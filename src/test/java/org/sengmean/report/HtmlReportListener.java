package org.sengmean.report;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.testcase.CaseRunnerGenerator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Regenerates the HTML test case reports at the end of every test run.
 *
 * Registered through {@code META-INF/services}, so the JUnit Platform picks it up wherever the
 * suites are launched from - {@code mvn test}, a single class from the IDE, one method from a
 * run configuration. Nothing has to be remembered and nothing has to be run afterwards, which
 * was the point: a report you have to ask for is a report that goes stale.
 *
 * <h2>Why not a Maven phase</h2>
 * Binding {@code exec:java@html-report} to the {@code test} phase looks equivalent and is not.
 * Surefire fails the build the moment a case goes red, and Maven then abandons the rest of the
 * phase - so the report would be written on green runs and skipped on exactly the runs worth
 * reading. This listener lives inside the test JVM and runs on {@code testPlanExecutionFinished}
 * whatever the outcome. {@code mvn exec:java@html-report} still works for rebuilding by hand.
 *
 * <h2>Why not read the Surefire XML</h2>
 * Surefire never clears its results directory; it overwrites the files for the classes it ran
 * and leaves the others in place. A report built from that directory after running one package
 * silently blends those fresh results with whatever the previous full run left behind, and
 * stamps the mixture with a single time. This listener only ever sees the run it is part of, so
 * a suite that did not run this time is reported <i>Not run</i> rather than reported from
 * yesterday.
 *
 * <h2>Container failures</h2>
 * A class whose {@code @BeforeAll} throws never reaches its methods, so JUnit reports the
 * failure against the class rather than any case. Those are applied to every case this run
 * selected under the class that produced no result of its own - otherwise the run that matters
 * most, the one where a suite could not start at all, is the one the report calls "Not run".
 */
public class HtmlReportListener implements TestExecutionListener {

    /** Keyed as {@code TopLevelClass#method}, matching what the generator expects. */
    private final Map<String, HtmlReportGenerator.Outcome> outcomes = new ConcurrentHashMap<>();

    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();

    /**
     * Kept because a skip is announced against the container, not the cases inside it. A class
     * carrying {@code @Disabled} produces exactly one {@code executionSkipped} call for the
     * class, and its methods are never mentioned again - so without the plan to walk, seven
     * deliberately disabled cases would be reported "Not run", which reads as an oversight
     * rather than as the decision it was.
     */
    private volatile TestPlan plan;

    /** When the run began, so the report can say how long the whole thing took. */
    private static long runStartedAt = System.nanoTime();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.plan = testPlan;
        runStartedAt = System.nanoTime();
        // Clear last run's screenshots before this one starts writing its own. Without this a
        // case that failed yesterday and passes today keeps yesterday's picture in its Evidence
        // column, which is worse than having no picture at all.
        clearDirectory(HtmlReportGenerator.shotsDir());
        // And its step trails, for exactly the same reason: a detail panel describing yesterday's
        // navigation under today's green row is a lie the reader has no way to catch.
        clearDirectory(CaseLog.dir());
    }

    @Override
    public void executionStarted(TestIdentifier identifier) {
        key(identifier).ifPresent(key -> startedAt.put(key, System.nanoTime()));
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        Optional<String> key = key(identifier);
        if (key.isEmpty()) {
            // Not a method: a class or the engine. Only its failure is worth keeping, and only
            // so the cases it prevented from running can say why. Given to the cases this run
            // selected under it rather than to the whole class, so a section run on its own whose
            // sign-in failed does not rewrite the other sections' report pages as errors. A case
            // that already has a result - an @AfterAll failure comes after its cases - keeps it.
            TestPlan current = plan;
            if (result.getStatus() == TestExecutionResult.Status.FAILED && current != null) {
                HtmlReportGenerator.Outcome failure = HtmlReportGenerator.containerFailure(
                        describe(result.getThrowable().orElse(null)));
                current.getDescendants(identifier).forEach(child ->
                        key(child).ifPresent(childKey -> outcomes.putIfAbsent(childKey, failure)));
            }
            return;
        }
        double seconds = elapsed(key.get());
        switch (result.getStatus()) {
            case SUCCESSFUL -> outcomes.put(key.get(),
                    new HtmlReportGenerator.Outcome(HtmlReportGenerator.PASS, "", "", seconds));
            case ABORTED -> outcomes.put(key.get(),
                    new HtmlReportGenerator.Outcome(HtmlReportGenerator.SKIPPED,
                            message(result.getThrowable().orElse(null)),
                            describe(result.getThrowable().orElse(null)), seconds));
            case FAILED -> outcomes.put(key.get(),
                    new HtmlReportGenerator.Outcome(status(result.getThrowable().orElse(null)),
                            message(result.getThrowable().orElse(null)),
                            describe(result.getThrowable().orElse(null)), seconds));
            default -> { }
        }
    }

    @Override
    public void executionSkipped(TestIdentifier identifier, String reason) {
        Optional<String> key = key(identifier);
        if (key.isPresent()) {
            outcomes.put(key.get(),
                    new HtmlReportGenerator.Outcome(HtmlReportGenerator.SKIPPED, reason, "", 0));
            return;
        }
        // A skipped container: pass the reason down to every case it holds, so a @Disabled
        // class reports seven skipped cases rather than seven that nobody asked about.
        TestPlan current = plan;
        if (current == null) {
            return;
        }
        current.getDescendants(identifier).forEach(child -> key(child).ifPresent(childKey ->
                outcomes.put(childKey,
                        new HtmlReportGenerator.Outcome(HtmlReportGenerator.SKIPPED, reason, "", 0))));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            HtmlReportGenerator.generate(
                    HtmlReportGenerator.testSrc(),
                    HtmlReportGenerator.shotsDir(),
                    HtmlReportGenerator.outDir(),
                    new HashMap<>(outcomes),
                    Map.of(),
                    environment(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        } catch (Exception e) {
            // A report is a by-product of the run. Failing to write it must never be mistaken
            // for a failing suite, so this is reported and swallowed.
            System.out.println("Could not write the HTML test case reports: " + e);
        }

        try {
            // The client test case page is rebuilt here for the same reason the reports are: a
            // page you have to remember to regenerate is a page that is wrong by the time anyone
            // reads it. It is cheap - one workbook read - and its Run links depend on which ids
            // the sources carry, so a case added or renamed today must not leave yesterday's
            // links behind.
            CaseSheetPage.generate();
        } catch (Exception e) {
            System.out.println("Could not write the client test case page: " + e);
        }

        try {
            // The client's own workbook, with this run's results in it. Same outcomes the HTML
            // report is built from, so the two can never disagree about what happened, and the
            // same reason for being written here: a results file somebody has to remember to
            // produce is one that gets sent stale or not at all.
            CaseSheetResults.write(new HashMap<>(outcomes));
        } catch (Exception e) {
            System.out.println("Could not write the client results workbook: " + e);
        }

        try {
            // The per-case launchers under TestCase/run, for the same reason again: they are
            // generated from the ids the sources carry, so a case renamed or added today would
            // otherwise leave yesterday's .cmd sitting there pointing at a method that no longer
            // exists - and the only person who would find out is whoever double-clicked it.
            // Writing them here costs one parse of the test sources, which has already happened
            // twice above.
            if (ConfigDataProvider.flag("testcase.run.enabled", true)) {
                CaseRunnerGenerator.generate();
            }
        } catch (Exception e) {
            System.out.println("Could not write the per-case launchers: " + e);
        }

        try {
            // And the email, last, because it attaches what the three steps above just wrote.
            // Off unless mail.enabled says otherwise, so an ordinary local run mails nobody.
            ReportMailer.send(HtmlReportGenerator.outDir(), tally(), environment());
        } catch (Exception e) {
            System.out.println("Could not email the report: " + e);
        }
    }

    /** "38 passed, 2 failed, 1 skipped" - what the run came to, for a subject line. */
    private String tally() {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        for (HtmlReportGenerator.Outcome outcome : outcomes.values()) {
            if (HtmlReportGenerator.PASS.equals(outcome.status())) {
                passed++;
            } else if (HtmlReportGenerator.SKIPPED.equals(outcome.status())) {
                skipped++;
            } else {
                failed++;
            }
        }
        return passed + " passed, " + failed + " failed, " + skipped + " skipped";
    }

    /** {@code org.sengmean.tests.payment.DepositPageTest$ValidDeposits} plus a method name. */
    private static Optional<String> key(TestIdentifier identifier) {
        return identifier.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast)
                .map(source -> topLevel(source.getClassName()) + "#" + source.getMethodName());
    }

    /** "org.sengmean.tests.payment.DepositPageTest$ValidDeposits" becomes "DepositPageTest". */
    private static String topLevel(String className) {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        int nested = simple.indexOf('$');
        return nested < 0 ? simple : simple.substring(0, nested);
    }

    /** An assertion that did not hold is a failure; anything else is an error. */
    private static String status(Throwable throwable) {
        if (throwable == null) {
            return HtmlReportGenerator.FAIL;
        }
        String type = throwable.getClass().getName();
        boolean assertion = throwable instanceof AssertionError
                || type.startsWith("org.opentest4j.")
                || type.startsWith("com.microsoft.playwright.assertions.");
        return assertion ? HtmlReportGenerator.FAIL : HtmlReportGenerator.ERROR;
    }

    private static String message(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter out = new StringWriter();
        throwable.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    private double elapsed(String key) {
        Long started = startedAt.remove(key);
        return started == null ? 0 : (System.nanoTime() - started) / 1_000_000_000.0;
    }

    /**
     * The conditions this run happened under, for the report's environment panel.
     *
     * <p>Three of these used to be reported - the target, the account and whether the browser was
     * headless - which is not enough to reproduce a run or to explain one. The wallet decides
     * which controls the cashier pages even render, the browser and its version decide how the
     * site behaves, and the machine and JDK are the first thing anyone asks about a result that
     * only appears on one person's laptop. A QA report is supposed to be repeatable by someone
     * who was not there, and that takes the whole list.
     *
     * <p>Read from the JVM rather than hard-coded, so a run overridden with
     * {@code -Dbase.url=...} reports where it actually went instead of where it usually goes.
     */
    static Map<String, String> environment() {
        Map<String, String> environment = new LinkedHashMap<>();
        for (String key : new String[]{"base.url", "base.query", "test.username",
                "test.alt.username", "headless", "slowMo"}) {
            String value = System.getProperty(key);
            if (value != null && !value.isBlank()) {
                environment.put(key, value);
            }
        }
        environment.put("java.version", System.getProperty("java.version", "unknown"));
        environment.put("os.name", System.getProperty("os.name", "unknown")
                + " " + System.getProperty("os.version", ""));
        environment.put("run.duration", String.format("%.1fs",
                (System.nanoTime() - runStartedAt) / 1_000_000_000.0));
        return environment;
    }

    private static void clearDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // A file another process is holding open is not worth failing a run over.
                }
            });
        } catch (Exception e) {
            System.out.println("Could not clear " + directory + ": " + e.getMessage());
        }
    }
}
