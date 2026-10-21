package org.sengmean.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sengmean.report.CaseIndex;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs one test case on this machine, addressed by the id the client test case sheet uses.
 *
 * <h2>Why this exists</h2>
 * The Home page tab of the client sheet carries a link per row. A link cannot reach into
 * IntelliJ and press Run - JetBrains exposes no such endpoint - so the link points here
 * instead: {@code http://localhost:8080/api/run/HOME-002} resolves the id through
 * {@link CaseIndex}, runs exactly that case with the Maven wrapper, and answers with the
 * result. The sheet stays the index of what is covered; this makes a row actionable.
 *
 * <h2>It runs headed on purpose</h2>
 * A run started by hand from a spreadsheet is one somebody is watching, so the browser is
 * shown rather than hidden. That is the opposite of the CI default and it is deliberate:
 * the value of clicking a row is seeing the case drive the site. Override per call with
 * {@code ?headless=true}.
 *
 * <h2>What it will not do</h2>
 * <ul>
 *   <li><b>It only runs ids it can resolve.</b> The selector handed to Maven is built from
 *       the test sources, never from the request, so the path parameter cannot smuggle
 *       arguments into the command line - an unknown id is a 404 page, not a shell.</li>
 *   <li><b>It refuses {@code creates-data} cases unless asked twice.</b> Those register real
 *       accounts, send real SMS, commit deposits and stake real balance. One click from a
 *       spreadsheet is too easy a way to spend money, so they need
 *       {@code ?allowCreatesData=true} on top of being clicked.</li>
 *   <li><b>It is one run at a time.</b> Two Playwright suites against the same shared QA
 *       account interfere - the currency switch alone is enough - so a second request while
 *       one is running is told to wait rather than starting a race.</li>
 * </ul>
 *
 * <p>The whole app sits behind HTTP Basic (see {@code ApiDocsSecurityConfig}), so the first
 * click of the day asks for the docs credentials and the browser caches them after that.
 * That guard is worth keeping on an endpoint that starts processes.
 */
@RestController
@RequestMapping("/api/run")
@Tag(name = "Run a case", description = "Run one test case by its client-sheet id")
public class TestRunController {

    /** How long a single case may take before it is killed and reported as timed out. */
    private static final Duration LIMIT = Duration.ofMinutes(10);

    /** Surefire's own summary line, which is the only thing worth parsing out of a build. */
    private static final Pattern TALLY = Pattern.compile(
            "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");

    /** One at a time - see the class comment on why two concurrent runs is not a race worth having. */
    private static final Object LOCK = new Object();
    private static volatile String running;

    @GetMapping(value = "/{caseId}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Run the case with this id and return the result as a page")
    // Every binding annotation names its parameter explicitly, and must keep doing so. This
    // project imports Spring Boot's BOM rather than taking spring-boot-starter-parent, and
    // the parent is what normally adds -parameters to the compiler. Without it Spring cannot
    // read a parameter's name by reflection, and an unnamed @PathVariable fails at request
    // time - not at startup - with "Name for argument of type [java.lang.String] not
    // specified", which reads like a routing problem rather than a build setting.
    public String run(@PathVariable("caseId") String caseId,
                      @RequestParam(name = "headless", defaultValue = "false") boolean headless,
                      @RequestParam(name = "allowCreatesData", defaultValue = "false")
                      boolean allowCreatesData) {
        // Everything is caught and rendered. The reader of this page came from a link in a
        // spreadsheet, and Spring's default for an uncaught exception is a JSON blob with a
        // status and no message - which tells them nothing about what to do next.
        try {
            return attempt(caseId, headless, allowCreatesData);
        } catch (Exception | LinkageError problem) {
            StringBuilder trace = new StringBuilder();
            for (StackTraceElement frame : problem.getStackTrace()) {
                if (frame.getClassName().startsWith("org.sengmean")) {
                    trace.append(frame).append('\n');
                }
            }
            return page("Error", caseId,
                    "<p>The run could not be started: <code>"
                            + escape(problem.getClass().getSimpleName()) + ": "
                            + escape(String.valueOf(problem.getMessage())) + "</code></p>"
                            + "<p>Working directory: <code>"
                            + escape(System.getProperty("user.dir")) + "</code></p>"
                            + "<pre>" + escape(trace.toString()) + "</pre>");
        }
    }

    private String attempt(String caseId, boolean headless, boolean allowCreatesData)
            throws IOException {

        CaseIndex.TestCase found = CaseIndex.find(caseId);
        if (found == null) {
            return page("Not automated", caseId,
                    "<p>No test case carries the id <code>" + escape(caseId) + "</code>.</p>"
                            + "<p>The sheet lists every case the client asked for; this project "
                            + "automates the High risk ones. A row with no link is a row with no "
                            + "test behind it yet.</p>");
        }
        if (found.createsData() && !allowCreatesData) {
            return page("Refused - this case spends something", caseId,
                    "<p><code>" + escape(found.id()) + "</code> is tagged "
                            + "<code>creates-data</code>: running it has effects that outlive "
                            + "the run - a registered account, a real SMS, a committed deposit, "
                            + "a staked bet - and none of them are undone by closing the "
                            + "browser.</p><p>If that is what you want, run it deliberately:<br>"
                            + "<a href=\"?allowCreatesData=true\">run "
                            + escape(found.id()) + " anyway</a></p>");
        }

        String busy = running;
        if (busy != null) {
            return page("Busy", caseId,
                    "<p>A run of <code>" + escape(busy) + "</code> is already going.</p>"
                            + "<p>Two suites against the same QA account interfere with each "
                            + "other, so this one was not started. Try again when that has "
                            + "finished.</p>");
        }

        synchronized (LOCK) {
            if (running != null) {
                return page("Busy", caseId, "<p>Another run started first. Try again shortly.</p>");
            }
            running = found.id();
        }
        try {
            return execute(found, headless);
        } finally {
            running = null;
        }
    }

    private String execute(CaseIndex.TestCase found, boolean headless) {
        List<String> command = command(found, headless);
        long startedAt = System.currentTimeMillis();
        List<String> output = new ArrayList<>();
        int exit;
        boolean timedOut = false;
        try {
            Process process = new ProcessBuilder(command)
                    .directory(new File(System.getProperty("user.dir")))
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }
            timedOut = !process.waitFor(LIMIT.toMinutes(), TimeUnit.MINUTES);
            if (timedOut) {
                process.destroyForcibly();
                exit = -1;
            } else {
                exit = process.exitValue();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return page("Could not start the run", found.id(),
                    "<p>The Maven wrapper would not start: <code>" + escape(String.valueOf(
                            e.getMessage())) + "</code></p><p>Command: <code>"
                            + escape(String.join(" ", command)) + "</code></p>");
        }

        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
        String verdict = timedOut
                ? "TIMED OUT"
                : verdictFrom(output, exit);
        return page(verdict, found.id(), summary(found, command, seconds, output, verdict));
    }

    /**
     * The command line, which is where a mistake is expensive and silent.
     *
     * <p>{@code -DfailIfNoSpecifiedTests=false} is deliberately absent: if the selector ever
     * stops matching - a renamed method, a case moved between {@code @Nested} groups - the
     * build should fail loudly rather than report a green run of nothing.
     */
    private List<String> command(CaseIndex.TestCase found, boolean headless) {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("win");
        // The wrapper is named absolutely, not as "mvnw.cmd" with the working directory set.
        // A bare name is resolved by cmd.exe against its own idea of the current directory
        // and the PATH, and it came back "'mvnw.cmd' is not recognized as an internal or
        // external command" even with directory() pointing at the project root. An absolute
        // path cannot be resolved against the wrong thing.
        File root = new File(System.getProperty("user.dir"));
        File wrapper = new File(root, windows ? "mvnw.cmd" : "mvnw");
        List<String> command = new ArrayList<>();
        if (windows) {
            command.add("cmd.exe");
            command.add("/c");
            command.add(wrapper.isFile() ? wrapper.getAbsolutePath() : "mvn");
        } else {
            command.add(wrapper.isFile() ? wrapper.getAbsolutePath() : "mvn");
        }
        command.add("test");
        command.add("-Dtest=" + found.selector());
        command.add("-Dheadless=" + headless);
        // known-defect stays excluded even here: those cases are red by design, and a red
        // page for a case that is behaving exactly as recorded would teach the reader to
        // ignore the colour. A creates-data case has already been allowed explicitly above.
        command.add("-DexcludedGroups=known-defect");
        return command;
    }

    private String verdictFrom(List<String> output, int exit) {
        for (String line : output) {
            Matcher tally = TALLY.matcher(line);
            if (tally.find() && !line.contains("Time elapsed")) {
                int run = Integer.parseInt(tally.group(1));
                int failures = Integer.parseInt(tally.group(2));
                int errors = Integer.parseInt(tally.group(3));
                int skipped = Integer.parseInt(tally.group(4));
                if (run == 0) {
                    return "NO TESTS RAN";
                }
                if (failures > 0 || errors > 0) {
                    return "FAIL";
                }
                return skipped > 0 && skipped == run ? "SKIPPED" : "PASS";
            }
        }
        return exit == 0 ? "PASS" : "FAIL";
    }

    private String summary(CaseIndex.TestCase found, List<String> command, double seconds,
                           List<String> output, String verdict) {
        StringBuilder html = new StringBuilder();
        html.append("<dl>")
                .append("<dt>Case</dt><dd><code>").append(escape(found.id())).append("</code></dd>")
                .append("<dt>What it proves</dt><dd>").append(escape(found.expected())).append("</dd>")
                .append("<dt>Test</dt><dd><code>").append(escape(found.selector())).append("</code></dd>")
                .append("<dt>Took</dt><dd>").append(String.format(Locale.ROOT, "%.1fs", seconds))
                .append("</dd>")
                .append("<dt>Command</dt><dd><code>").append(escape(String.join(" ", command)))
                .append("</code></dd>")
                // Shown always, not only on the error path: when a run fails for an
                // environmental reason, "which directory did this run in" is the first
                // question, and it is what caught the wrapper not being found.
                .append("<dt>Directory</dt><dd><code>")
                .append(escape(System.getProperty("user.dir"))).append("</code></dd></dl>");
        if (!"PASS".equals(verdict)) {
            html.append("<p>The lines Maven printed that mention this case:</p><pre>");
            for (String line : output) {
                if (line.contains("Tests run:") || line.contains("ERROR")
                        || line.contains(found.suite())) {
                    html.append(escape(line)).append('\n');
                }
            }
            html.append("</pre>");
        }
        html.append("<details><summary>Full output</summary><pre>");
        // The tail rather than the whole build: Maven's download chatter on a cold start can
        // run to thousands of lines and pushes the part worth reading off the page.
        List<String> tail = output.size() > 400
                ? output.subList(output.size() - 400, output.size())
                : output;
        for (String line : tail) {
            html.append(escape(line)).append('\n');
        }
        html.append("</pre></details>");
        html.append("<p><a href=\"\">Run it again</a></p>");
        return html.toString();
    }

    private String page(String verdict, String caseId, String body) {
        String colour = switch (verdict) {
            case "PASS" -> "#137a43";
            case "FAIL", "TIMED OUT", "NO TESTS RAN" -> "#b02525";
            default -> "#9a6a00";
        };
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + escape(caseId) + " - " + escape(verdict) + "</title><style>"
                + "body{margin:0;padding:32px 20px;background:#f4f6fa;color:#1a2230;"
                + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
                + "line-height:1.5}.wrap{max-width:900px;margin:0 auto}"
                + "h1{margin:0 0 4px;font-size:22px}.v{display:inline-block;padding:4px 12px;"
                + "border-radius:20px;font-weight:700;color:#fff;background:" + colour + "}"
                + "dl{display:grid;grid-template-columns:150px 1fr;gap:6px 14px;font-size:14px}"
                + "dt{color:#5b6676;font-weight:600}dd{margin:0;word-break:break-word}"
                + "pre{background:#fff;border:1px solid #e2e7ef;border-radius:8px;padding:12px;"
                + "overflow-x:auto;font-size:12px;max-height:420px}"
                + "code{background:#eef1f6;padding:1px 5px;border-radius:4px;font-size:12.5px}"
                + "a{color:#1f6a9c}</style></head><body><div class=\"wrap\">"
                + "<h1>" + escape(caseId) + " <span class=\"v\">" + escape(verdict) + "</span></h1>"
                + body + "</div></body></html>";
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
