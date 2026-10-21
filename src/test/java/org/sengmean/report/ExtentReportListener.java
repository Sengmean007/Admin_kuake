package org.sengmean.report;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Writes the Extent Spark report at the end of every test run.
 *
 * <h2>What this adds, given the project already has an HTML report</h2>
 * {@link HtmlReportGenerator} produces the <i>client-facing</i> report: one page per suite, laid
 * out in the columns the test case workbook uses - Id, test case, expected result, actual result,
 * status, remark - because it exists to be read next to that workbook. It is deliberately shaped
 * like a QA deliverable rather than like a build artefact.
 *
 * <p>Extent answers the other question: what did <i>this run</i> do. One page for everything, with
 * the dashboard, the pass/fail chart, the category filters and the full-text search a standard
 * tool gives for free. The two are complements rather than alternatives, and they are built from
 * the same evidence - the same {@link CaseLog} trails, the same {@code ScreenshotOnFinish}
 * images, the same case ids - so a case cannot say one thing here and another there.
 *
 * <h2>Registered the same way, and for the same reason</h2>
 * Through {@code META-INF/services}, alongside {@code HtmlReportListener}, so the JUnit Platform
 * picks it up wherever a run is launched from - {@code ./mvnw test}, one suite, a single method
 * from the IDE. There is no separate command to remember and no Maven phase to bind: a report you
 * have to ask for is a report that goes stale, and binding to the {@code test} phase would skip
 * it on exactly the red runs worth reading, because Surefire abandons the phase on failure.
 *
 * <h2>Self-contained on disk</h2>
 * Both listeners fire on {@code testPlanExecutionFinished} and the platform does not order them,
 * so this one must not depend on anything {@code HtmlReportListener} writes. It copies its own
 * screenshots out of {@code target/case-screenshots} into its own directory and references them
 * relatively, which costs some duplicated PNGs and buys independence from listener ordering. It
 * only reads the step logs, which are written during the run by {@link CaseLog} and cleared at
 * the start of the next one.
 *
 * <p>Output goes to {@code docs/reports/extent/index.html}, overridable with
 * {@code -Dreport.extent.dir=...}. A sub-directory rather than a file among the suite pages, so
 * its screenshots have somewhere of their own to live and so {@code HtmlReportGenerator}'s prune
 * step - which deletes stray {@code *-test-report.html} files from the reports directory - has
 * nothing here to consider stale.
 */
public class ExtentReportListener implements TestExecutionListener {

    /** One case as this run saw it: everything the report needs about a row. */
    private record Case(String key,
                        String suite,
                        String group,
                        String displayName,
                        List<String> tags,
                        int sequence,
                        String status,
                        String message,
                        String detail,
                        double seconds) {

        Case withOutcome(String status, String message, String detail, double seconds) {
            return new Case(key, suite, group, displayName, tags, sequence,
                    status, message, detail, seconds);
        }
    }

    /** Keyed as {@code TopLevelClass#method}, the key the screenshots and step logs also use. */
    private final Map<String, Case> cases = new ConcurrentHashMap<>();

    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();

    /** So cases can be reported in the order they ran rather than in hash order. */
    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * Kept because a skip is announced against the container, not the cases inside it: a class
     * carrying {@code @Disabled} produces one {@code executionSkipped} call and its methods are
     * never mentioned again, so without the plan to walk they would be missing from the report
     * entirely rather than listed as the deliberate skips they are.
     */
    private volatile TestPlan plan;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.plan = testPlan;
    }

    @Override
    public void executionStarted(TestIdentifier identifier) {
        key(identifier).ifPresent(key -> {
            startedAt.put(key, System.nanoTime());
            cases.putIfAbsent(key, describe(identifier, key));
        });
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        Optional<String> key = key(identifier);
        if (key.isEmpty()) {
            // A class or the engine. Only a failure is worth keeping, and only so the cases it
            // stopped from running can say why rather than being reported as absent. Charged to
            // the cases this run selected under it, never to the whole class: every case not in
            // this run keeps its own earlier result, and a section that could not sign in must
            // not overwrite the other sections with errors.
            TestPlan current = plan;
            if (result.getStatus() == TestExecutionResult.Status.FAILED && current != null) {
                String trace = stackTrace(result.getThrowable().orElse(null));
                current.getDescendants(identifier).forEach(child -> key(child).ifPresent(childKey -> {
                    Case existing = cases.get(childKey);
                    if (existing == null || existing.status().isBlank()) {
                        record(child, childKey, HtmlReportGenerator.ERROR,
                                "The suite failed before this case ran.", trace, 0);
                    }
                }));
            }
            return;
        }
        Throwable throwable = result.getThrowable().orElse(null);
        double seconds = elapsed(key.get());
        String status = switch (result.getStatus()) {
            case SUCCESSFUL -> HtmlReportGenerator.PASS;
            case ABORTED -> HtmlReportGenerator.SKIPPED;
            case FAILED -> failureKind(throwable);
        };
        record(identifier, key.get(), status, message(throwable), stackTrace(throwable), seconds);
    }

    @Override
    public void executionSkipped(TestIdentifier identifier, String reason) {
        Optional<String> key = key(identifier);
        if (key.isPresent()) {
            record(identifier, key.get(), HtmlReportGenerator.SKIPPED, reason, "", 0);
            return;
        }
        TestPlan current = plan;
        if (current == null) {
            return;
        }
        current.getDescendants(identifier).forEach(child -> key(child).ifPresent(childKey ->
                record(child, childKey, HtmlReportGenerator.SKIPPED, reason, "", 0)));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            write();
        } catch (Exception e) {
            // A report is a by-product of the run. Failing to write one must never be mistaken
            // for a failing suite, so this is reported and swallowed - the same contract
            // HtmlReportListener works to.
            System.out.println("Could not write the Extent report: " + e);
        }
    }

    // ---------- collecting ----------

    private void record(TestIdentifier identifier, String key,
                        String status, String message, String detail, double seconds) {
        cases.compute(key, (ignored, existing) -> {
            Case base = existing != null ? existing : describe(identifier, key);
            return base.withOutcome(status, message, detail, seconds);
        });
    }

    /**
     * The static facts about a case - which suite and group it belongs to, what it is called,
     * what it is tagged with. Taken from the test plan rather than parsed out of the sources,
     * because the plan is what actually ran.
     */
    private Case describe(TestIdentifier identifier, String key) {
        MethodSource source = (MethodSource) identifier.getSource().orElseThrow();
        String suite = topLevel(source.getClassName());
        String group = "";
        TestPlan current = plan;
        if (current != null) {
            // The enclosing @Nested class by its display name - "Invalid deposits" rather than
            // "InvalidDeposits" - which is what a group should be called in a report.
            group = current.getParent(identifier)
                    .filter(parent -> parent.getSource()
                            .filter(ClassSource.class::isInstance)
                            .map(ClassSource.class::cast)
                            .map(classSource -> classSource.getClassName().contains("$"))
                            .orElse(false))
                    .map(TestIdentifier::getDisplayName)
                    .orElse("");
        }
        List<String> tags = identifier.getTags().stream()
                .map(TestTag::getName)
                .sorted()
                .toList();
        return new Case(key, suite, group, identifier.getDisplayName(), tags,
                sequence.getAndIncrement(), "", "", "", 0);
    }

    // ---------- writing ----------

    /**
     * One case as the dashboard last saw it, kept between runs in {@code .state/cases.json}.
     *
     * <p>The dashboard used to show only the run that wrote it, so running the Create section
     * dropped the Edit section's results from it entirely. Every case now keeps its latest result
     * until a run executes it again. The step trail and the screenshot paths are stored with the
     * result because both are cleared at the start of every run - without them a case carried
     * forward from an earlier run would lose the working behind its result.
     */
    record Stored(String key, String suite, String group, String displayName, List<String> tags,
                  String status, String message, String detail, double seconds, String ranAt,
                  List<CaseLog.Entry> trail, List<String> shots) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private void write() throws Exception {
        Path outDir = Path.of(System.getProperty("report.extent.dir", "docs/reports/extent"));
        Files.createDirectories(outDir);
        Path shotsOut = outDir.resolve("screenshots");

        // Only a case that produced a result this time replaces what the dashboard had for it.
        Map<String, Case> ran = new HashMap<>();
        cases.forEach((key, one) -> {
            if (!one.status().isBlank()) {
                ran.put(key, one);
            }
        });
        HtmlReportGenerator.clearShotsOfCases(shotsOut, ran.keySet());
        Map<String, List<String>> shots = copyScreenshots(HtmlReportGenerator.shotsDir(), shotsOut);
        Map<String, List<CaseLog.Entry>> logs = HtmlReportGenerator.readStepLogs(CaseLog.dir());
        Map<String, CaseSheetSteps.Entry> sheet = CaseSheetSteps.read(CaseSheetPage.workbook());
        String ranAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        Path stateFile = outDir.resolve(".state").resolve("cases.json");
        Map<String, Stored> merged = loadStored(stateFile);
        for (Case one : ran.values()) {
            merged.put(one.key(), new Stored(one.key(), one.suite(), one.group(), one.displayName(),
                    one.tags(), one.status(), one.message(), one.detail(), one.seconds(), ranAt,
                    logs.getOrDefault(one.key(), List.of()), shots.getOrDefault(one.key(), List.of())));
        }
        // In this run's plan but never reported: Not run, unless an earlier run has a result.
        for (Case one : cases.values()) {
            if (one.status().isBlank()) {
                merged.putIfAbsent(one.key(), new Stored(one.key(), one.suite(), one.group(),
                        one.displayName(), one.tags(), "", "", "", 0, "", List.of(), List.of()));
            }
        }
        // Cases in the order the sources declare them, and none the sources no longer declare.
        Map<String, Integer> order = declaredOrder();
        if (!order.isEmpty()) {
            merged.keySet().retainAll(order.keySet());
        }
        saveStored(stateFile, merged);

        ExtentSparkReporter spark =
                new ExtentSparkReporter(outDir.resolve("index.html").toString());
        spark.config().setEncoding("utf-8");
        // Spark's stylesheet and scripts come from jsDelivr and bootstrapcdn by default, so the
        // page renders as unstyled text on a machine with no internet - and these reports are
        // committed under docs/ and opened by whoever is handed the repository. Offline mode
        // unpacks those assets beside the page instead. Overridable for a run that would rather
        // have the smaller directory.
        spark.config().setOfflineMode(
                Boolean.parseBoolean(System.getProperty("report.extent.offline", "true")));
        spark.config().setTheme(Theme.STANDARD);
        spark.config().setDocumentTitle("Admin weibo");
        spark.config().setReportName("Weibo Empire admin - Playwright run");
        spark.config().setTimeStampFormat("yyyy-MM-dd HH:mm:ss");

        ExtentReports extent = new ExtentReports();
        extent.attachReporter(spark);
        // The same environment panel the HTML report carries, from the one implementation, so a
        // run cannot be described two ways by two reports written seconds apart.
        HtmlReportListener.environment().forEach(extent::setSystemInfo);

        // Package areas, then suites, alphabetically, and cases in the order the sources declare
        // them. The top level is the package area (auth, product, rebateprogram, tool), as on the
        // HTML index: a flat list of every suite in one column was hard to find anything in.
        Map<String, String> areaOf = declaredAreas();
        Map<String, Map<String, List<Stored>>> byArea = new TreeMap<>();
        merged.values().forEach(one -> byArea
                .computeIfAbsent(areaOf.getOrDefault(one.suite(), "other"), ignored -> new TreeMap<>())
                .computeIfAbsent(one.suite(), ignored -> new ArrayList<>()).add(one));

        for (Map.Entry<String, Map<String, List<Stored>>> area : byArea.entrySet()) {
            String areaTitle = HtmlReportGenerator.areaTitle(area.getKey());
            List<Stored> inArea = area.getValue().values().stream().flatMap(List::stream).toList();
            ExtentTest areaNode = extent.createTest(areaTitle + lastRun(inArea));
            for (Map.Entry<String, List<Stored>> suite : area.getValue().entrySet()) {
                List<Stored> ordered = suite.getValue().stream()
                        .sorted(Comparator.comparingInt((Stored one) -> order.getOrDefault(one.key(), Integer.MAX_VALUE))
                                .thenComparing(Stored::displayName))
                        .toList();
                // Each node says when its cases last ran, because they no longer all come from one run.
                ExtentTest parent = areaNode.createNode(suite.getKey() + lastRun(ordered));
                Map<String, ExtentTest> groups = new LinkedHashMap<>();
                for (Stored one : ordered) {
                    // A @Nested class becomes an intermediate node, so "Invalid deposits" is a group
                    // in the report exactly as it is in the source.
                    ExtentTest owner = one.group().isBlank()
                            ? parent
                            : groups.computeIfAbsent(one.group(), group -> parent.createNode(group
                                    + lastRun(ordered.stream().filter(c -> group.equals(c.group())).toList())));
                    ExtentTest node = owner.createNode(one.displayName());
                    node.assignCategory(areaTitle);   // the filter panel can pull up one package too
                    write(node, one, sheet.get(CaseSheetSteps.idOf(one.displayName())));
                }
            }
        }
        extent.flush();
        System.out.println("Extent report written to "
                + outDir.resolve("index.html").toAbsolutePath());
    }

    private void write(ExtentTest node, Stored one, CaseSheetSteps.Entry planned) {
        // The case id and the tags become Extent categories, which is what drives the filter
        // panel: a reader can pull up every known-defect case, or every case of one feature,
        // without knowing how the suites are laid out.
        caseId(one.displayName()).ifPresent(node::assignCategory);
        one.tags().forEach(node::assignCategory);
        node.assignCategory(one.suite());
        testCase(node, CaseSheetSteps.describe(planned, one.trail()));

        String status = one.status();
        if (status.isBlank()) {
            node.log(Status.SKIP, "Not run.");
            return;
        }
        node.log(Status.INFO, "Last run " + escape(one.ranAt()) + ".");

        trail(node, one.trail());

        switch (status) {
            case HtmlReportGenerator.PASS ->
                    node.log(Status.PASS, String.format("Passed in %.1fs.", one.seconds()));
            case HtmlReportGenerator.SKIPPED ->
                    node.log(Status.SKIP,
                            one.message().isBlank() ? "Skipped." : escape(one.message()));
            // Extent has no Error status of its own, so the distinction the HTML report draws -
            // an assertion that did not hold, versus something that blew up on the way - is kept
            // in the message rather than lost.
            case HtmlReportGenerator.ERROR -> {
                node.log(Status.FAIL, "<b>Error</b> - " + escape(one.message()));
                node.log(Status.FAIL, "<pre>" + escape(one.detail()) + "</pre>");
            }
            default -> {
                node.log(Status.FAIL, escape(one.message()));
                node.log(Status.FAIL, "<pre>" + escape(one.detail()) + "</pre>");
            }
        }

        for (String shot : one.shots()) {
            try {
                node.addScreenCaptureFromPath(shot);
            } catch (Exception e) {
                // A picture that cannot be attached is evidence lost, never a run to fail.
                node.log(Status.INFO, "Could not attach " + escape(shot) + ".");
            }
        }
    }

    /**
     * The case's Pre-condition, Test steps and Test data, from the same {@link CaseSheetSteps#describe} the HTML
     * report uses, so the two reports cannot describe one case two ways. Logged before the status
     * check, so a case that has not run yet still shows what it will do.
     */
    private static void testCase(ExtentTest node, CaseSheetSteps.Described described) {
        if (described.precondition().isBlank() && described.steps().isEmpty()
                && described.data().isEmpty() && described.usedData().isEmpty()) {
            return;
        }
        node.log(Status.INFO, "<table class='table table-sm'><tbody>"
                + "<tr><th style='width:110px'>Pre-condition</th><td>"
                + HtmlReportGenerator.preconditionHtml(described) + "</td></tr>"
                + "<tr><th>Test steps</th><td>"
                + HtmlReportGenerator.stepsHtml(described) + "</td></tr>"
                + "<tr><th>Test data</th><td>" + HtmlReportGenerator.dataHtml(described) + "</td></tr>"
                + "</tbody></table>");
    }

    /**
     * The {@link CaseLog} trail, rendered as one block rather than one Extent log row per entry.
     * A busy case records a few hundred navigations and API calls, and four hundred rows would
     * bury the one line that says what actually failed.
     */
    private static void trail(ExtentTest node, List<CaseLog.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        StringBuilder table = new StringBuilder(
                "<table class='table table-sm'><thead><tr><th>t</th><th>kind</th>"
                        + "<th>what happened</th></tr></thead><tbody>");
        for (CaseLog.Entry entry : entries) {
            table.append("<tr><td>").append(String.format("%.1fs", entry.millis() / 1000.0))
                    .append("</td><td>").append(escape(entry.kind()))
                    .append("</td><td>").append(escape(entry.text()))
                    .append("</td></tr>");
        }
        table.append("</tbody></table>");
        node.log(Status.INFO, table.toString());
    }

    /**
     * Copies this run's screenshots next to the report, keyed by {@code Suite#method}.
     *
     * <p>Mid-case captures are named {@code <case>-1.png} and the closing one {@code <case>.png};
     * a hyphen sorts before a full stop, so sorting on the file name puts the step shots first
     * and the final state last, which is the order they were taken in.
     */
    private static Map<String, List<String>> copyScreenshots(Path from, Path to) {
        Map<String, List<String>> byCase = new HashMap<>();
        if (!Files.isDirectory(from)) {
            return byCase;
        }
        try {
            // Not emptied first: the cases this run executed have already had their old images
            // removed, and every other case's images still serve its carried-forward result.
            Files.createDirectories(to);
            try (Stream<Path> walk = Files.list(from)) {
                for (Path shot : walk
                        .filter(p -> p.getFileName().toString().endsWith(".png"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList()) {
                    String name = shot.getFileName().toString();
                    String stem = name.substring(0, name.length() - 4).replaceAll("-\\d+$", "");
                    int dot = stem.indexOf('.');
                    if (dot < 0) {
                        continue;
                    }
                    Files.copy(shot, to.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    byCase.computeIfAbsent(stem.substring(0, dot) + "#" + stem.substring(dot + 1),
                            ignored -> new ArrayList<>()).add("screenshots/" + name);
                }
            }
        } catch (Exception e) {
            System.out.println("Could not copy screenshots for the Extent report: " + e.getMessage());
        }
        return byCase;
    }

    // ---------- carried-forward results ----------

    /** What earlier runs recorded, keyed {@code Suite#method}; empty on the first run. */
    private static Map<String, Stored> loadStored(Path file) {
        Map<String, Stored> stored = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return stored;
        }
        try {
            List<Stored> list = JSON.readValue(file.toFile(), new TypeReference<List<Stored>>() { });
            list.forEach(one -> stored.put(one.key(), one));
        } catch (Exception e) {
            // An unreadable history costs the dashboard its earlier runs, never this one.
            System.out.println("Could not read " + file + ", starting the dashboard afresh: " + e.getMessage());
        }
        return stored;
    }

    private static void saveStored(Path file, Map<String, Stored> stored) {
        try {
            Files.createDirectories(file.getParent());
            List<Stored> list = stored.values().stream()
                    .sorted(Comparator.comparing(Stored::key))
                    .toList();
            JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), list);
        } catch (Exception e) {
            System.out.println("Could not save " + file + ": " + e.getMessage());
        }
    }

    /**
     * Each case's position in the test sources, from the same parser the HTML report uses. Empty
     * when the sources cannot be read, in which case nothing is dropped for not being declared.
     */
    private static Map<String, Integer> declaredOrder() {
        Map<String, Integer> order = new HashMap<>();
        try {
            for (HtmlReportGenerator.SuiteReport suite
                    : HtmlReportGenerator.readSpecs(HtmlReportGenerator.testSrc())) {
                for (HtmlReportGenerator.Spec spec : suite.specs) {
                    order.putIfAbsent(spec.key(), order.size());
                }
            }
        } catch (Exception e) {
            System.out.println("Could not read the test sources for the dashboard's order: " + e.getMessage());
        }
        return order;
    }

    /** Each test class's package area, keyed by its simple name - "OtherCategoryTest" to "product". */
    private static Map<String, String> declaredAreas() {
        Map<String, String> areas = new HashMap<>();
        try {
            for (HtmlReportGenerator.SuiteReport suite
                    : HtmlReportGenerator.readSpecs(HtmlReportGenerator.testSrc())) {
                areas.putIfAbsent(suite.simpleName, HtmlReportGenerator.area(suite.packageName));
            }
        } catch (Exception e) {
            System.out.println("Could not read the test sources for the dashboard's packages: " + e.getMessage());
        }
        return areas;
    }

    /** " - last run 2026-09-26 10:25", the latest among the cases given, or "" if none ran. */
    private static String lastRun(List<Stored> cases) {
        return cases.stream()
                .map(Stored::ranAt)
                .filter(ranAt -> ranAt != null && !ranAt.isBlank())
                .max(Comparator.naturalOrder())
                .map(ranAt -> " - last run " + ranAt)
                .orElse("");
    }

    // ---------- small helpers ----------

    /**
     * The case id at the head of a display name: {@code DEP-01} out of
     * {@code "DEP-01: the amount field advertises its range"}.
     *
     * <p>Uses {@link HtmlReportGenerator#CASE_ID} rather than a pattern of its own. A second copy
     * of that regex would be a second thing to keep in step with how display names are written,
     * and the first symptom of the two disagreeing would be an id that appears in one report and
     * not the other.
     */
    private static Optional<String> caseId(String displayName) {
        Matcher match = HtmlReportGenerator.CASE_ID.matcher(displayName);
        return match.find()
                ? Optional.of(match.group(1).replaceAll("\\s*/\\s*", " / "))
                : Optional.empty();
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
    private static String failureKind(Throwable throwable) {
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

    private static String stackTrace(Throwable throwable) {
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

    /** Escaped because a Playwright failure message routinely carries angle brackets. */
    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
