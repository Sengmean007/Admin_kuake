package org.sengmean.report;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.sengmean.config.ConfigDataProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Emails the result of a run when it finishes, if it was asked to.
 *
 * <h2>Off by default, deliberately</h2>
 * {@code mail.enabled} is false in {@code Config/config.properties}. A suite that mails somebody
 * on every local run is a suite whose mail gets filtered into a folder nobody opens, and the local
 * runs are the ones nobody needs told about - the report is already on disk and the person who
 * ran it is sitting in front of it. Switch it on for CI, or for a run whose result is going to
 * someone:
 *
 * <pre>./mvnw test -Dmail.enabled=true</pre>
 *
 * <h2>Where the password comes from</h2>
 * Never from a committed file. {@link ConfigDataProvider} looks in {@code -Dmail.password}, then
 * the {@code MAIL_PASSWORD} environment variable, then {@code Config/secrets.properties}, which is
 * not in git. A Gmail account password will not work; it has to be an app password, and
 * {@code secrets.properties.example} says where to get one.
 *
 * <h2>It never fails a run</h2>
 * Everything here is caught and reported as a line on the console. A report is a by-product, and a
 * mail server that is down or an app password that has expired must not be mistaken for a failing
 * suite - which is exactly what would happen if this threw out of
 * {@code testPlanExecutionFinished}.
 */
public final class ReportMailer {

    /** How many of the newest report pages to attach before it becomes a nuisance. */
    private static final int MAX_ATTACHMENTS = 12;

    private ReportMailer() {
    }

    /**
     * Sends the run's result, or does nothing at all.
     *
     * @param reportDir   where {@link HtmlReportGenerator} just wrote the suite pages
     * @param tally       "38 passed, 2 failed, 1 skipped"
     * @param environment the conditions the run happened under, for the body
     */
    public static void send(Path reportDir, String tally, Map<String, String> environment) {
        if (!ConfigDataProvider.flag("mail.enabled", false)) {
            return;
        }

        String from = ConfigDataProvider.get("mail.from");
        String to = ConfigDataProvider.get("mail.to");
        String password = ConfigDataProvider.get("mail.password");

        if (from.isEmpty() || to.isEmpty() || password.isEmpty()) {
            // Saying which one is missing matters more here than it looks: the symptom of any of
            // the three being absent is silence, and silence is also what a working, disabled
            // mailer produces.
            System.out.println("Report email is enabled but not configured - need mail.from ("
                    + (from.isEmpty() ? "missing" : "set") + "), mail.to ("
                    + (to.isEmpty() ? "missing" : "set") + ") and mail.password ("
                    + (password.isEmpty() ? "missing" : "set")
                    + "). See Config/secrets.properties.example. Nothing was sent.");
            return;
        }

        try {
            Session session = session(from, password);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
            message.setSubject(subject(tally, environment));

            MimeMultipart body = new MimeMultipart();
            MimeBodyPart text = new MimeBodyPart();
            text.setText(body(tally, environment), "UTF-8");
            body.addBodyPart(text);

            for (Path attachment : attachments(reportDir)) {
                MimeBodyPart part = new MimeBodyPart();
                part.setDataHandler(new DataHandler(new FileDataSource(attachment.toFile())));
                part.setFileName(attachment.getFileName().toString());
                body.addBodyPart(part);
            }

            message.setContent(body);
            Transport.send(message);
            System.out.println("Report emailed to " + to + " - " + tally);

        } catch (Exception couldNotSend) {
            System.out.println("Could not email the report: " + couldNotSend);
        }
    }

    /**
     * An SMTPS session on the configured host.
     *
     * <p>Port 465 with {@code smtp.ssl.enable} rather than 587 with STARTTLS, because the former
     * is TLS from the first byte and there is no plaintext negotiation to be stripped. Both are
     * configurable; only the default is opinionated.
     */
    private static Session session(String from, String password) {
        Properties properties = new Properties();
        String port = ConfigDataProvider.get("mail.smtp.port", "465");
        properties.put("mail.smtp.host", ConfigDataProvider.get("mail.smtp.host", "smtp.gmail.com"));
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", ConfigDataProvider.get("mail.smtp.auth", "true"));
        properties.put("mail.smtp.ssl.enable", String.valueOf("465".equals(port)));
        properties.put("mail.smtp.starttls.enable", String.valueOf(!"465".equals(port)));

        return Session.getInstance(properties, new jakarta.mail.Authenticator() {
            @Override
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                // Gmail shows an app password in four groups of four. Stripping the spaces here
                // means it can be pasted either way, which is the difference between this working
                // first time and an authentication failure nobody can explain.
                return new jakarta.mail.PasswordAuthentication(from, password.replace(" ", ""));
            }
        });
    }

    /** Result first, so it reads in a notification list without being opened. */
    private static String subject(String tally, Map<String, String> environment) {
        String outcome = tally.contains(" 0 failed") ? "PASSED" : "FAILED";
        return "Admin weibo - " + outcome + " - " + tally
                + " - " + environment.getOrDefault("base.url", "");
    }

    private static String body(String tally, Map<String, String> environment) {
        StringBuilder out = new StringBuilder();
        out.append("Admin_weibo automated run\n\n");
        out.append(tally).append("\n\n");
        out.append("Conditions of the run\n");
        for (Map.Entry<String, String> setting : environment.entrySet()) {
            out.append("  ").append(setting.getKey()).append(" = ").append(setting.getValue()).append("\n");
        }
        out.append("\nThe suite pages are attached. The full report, with the screenshots and the\n");
        out.append("step trail under each case, is docs/reports/index.html in the repository - the\n");
        out.append("attachments are the same pages without their images.\n");
        return out.toString();
    }

    /**
     * The report pages worth attaching: the index and the newest per-suite pages.
     *
     * <p>Capped, and newest first, because the reports directory accumulates a page per suite and
     * a full run produces ten of them. Attaching every one of a large directory is how a report
     * mail ends up rejected for size by the receiving server, which fails silently from here.
     */
    private static List<Path> attachments(Path reportDir) {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(reportDir)) {
            return found;
        }
        try (Stream<Path> files = Files.list(reportDir)) {
            found = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".html"))
                    .sorted(Comparator.comparing((Path file) -> lastModified(file)).reversed())
                    .limit(MAX_ATTACHMENTS)
                    .toList();
        } catch (Exception cannotList) {
            System.out.println("Could not list the reports to attach: " + cannotList.getMessage());
        }
        return found;
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception unreadable) {
            return 0L;
        }
    }
}
