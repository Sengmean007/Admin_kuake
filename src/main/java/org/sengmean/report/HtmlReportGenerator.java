package org.sengmean.report;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes one HTML test-case report per suite, in the house style of
 * {@code docs/weibo_register_test_report.html}: ID, test case, expected result, actual
 * result, status, remark, and the screenshot of the page as each case left it.
 *
 * Run it after a suite:
 * <pre>
 *   mvn test -Dheadless=true -DexcludedGroups=creates-data
 *   mvn exec:java@html-report
 * </pre>
 *
 * <h2>Where each column comes from</h2>
 * The Surefire XML knows a method name, a duration and a stack trace; it does not know what
 * the case was supposed to prove, and it does not carry the {@code @DisplayName}. So the
 * report is assembled from two sources joined on the method name:
 * <ul>
 *   <li><b>the test sources</b> under {@code src/test/java/tests} give the case id, the
 *       expected result, the {@code @Tag}s and any {@code @Disabled} reason. Reading the
 *       source rather than a hand-kept spreadsheet is what stops the report drifting away
 *       from the suite - a case renamed in code is renamed in the report on the next run;</li>
 *   <li><b>the Surefire XML</b> gives what actually happened and how long it took;</li>
 *   <li><b>{@code target/case-screenshots}</b>, written by {@code org.sengmean.base.ScreenshotOnFinish},
 *       gives the picture of the page as each case left it, passed or failed.</li>
 * </ul>
 *
 * Cases present in the source but absent from the XML are reported as <i>Not run</i> rather
 * than quietly dropped, because a case excluded by {@code -DexcludedGroups} is exactly the
 * kind of gap a reader of a QA report needs to see.
 *
 * <h2>Naming</h2>
 * Surefire names a {@code @Nested} class {@code Outer$Inner}, so results are keyed on the
 * top-level class and the method name. The nested classes still survive as section headings
 * inside the suite's page.
 */
public class HtmlReportGenerator {

    // ---------- model ----------

    /** One case as the source declares it. */
    static final class Spec {
        final String suite;          // top-level test class, simple name
        final String group;          // enclosing @Nested class, "" for a top-level case
        final String groupTitle;     // that class's @DisplayName
        final String method;
        final String id;             // "DEP-02", "W-1 / WDR-03", or "" when the name carries none
        final String expected;       // the @DisplayName, minus the id prefix
        final List<String> tags;
        final String disabledReason; // null unless @Disabled

        /**
         * The other workbook rows this case answers for, from an
         * {@code // Also covers workbook rows: A, B, C} comment directly above the case.
         *
         * <h2>Why these are held apart from {@link #id}</h2>
         * {@code id} is the case's <b>key</b> - one id, never reused, shown in the report's Id
         * column and used to name the case everywhere else. A sweep such as
         * {@code DEPINVALID-09}, which walks every deposit option and asserts none accepts an
         * amount below its own minimum, genuinely answers <b>fourteen</b> workbook rows. Putting
         * all fourteen in the {@code @DisplayName} would turn the Id column back into a list -
         * the exact thing that was undone on 2026-09-03 - and would wreck the display-name
         * ordering the whole project runs on.
         *
         * <p>So they live in a comment, and this field is what makes that comment mean something:
         * {@link CaseIndex} registers every id here as resolving to this case, which is what puts
         * a <b>Run</b> button on those rows in the client test case page. Before 2026-09-11 the
         * comment was read by people only - the generator skipped it like any other {@code //}
         * line - so a row it named still showed as having no test behind it.
         */
        final List<String> alsoCovers;

        Spec(String suite, String group, String groupTitle, String method,
             String id, String expected, List<String> tags, String disabledReason,
             List<String> alsoCovers) {
            this.suite = suite;
            this.group = group;
            this.groupTitle = groupTitle;
            this.method = method;
            this.id = id;
            this.expected = expected;
            this.tags = tags;
            this.disabledReason = disabledReason;
            this.alsoCovers = alsoCovers;
        }

        String key() {
            return suite + "#" + method;
        }
    }

    /** What the run did with a case. */
    record Outcome(String status, String message, String detail, double seconds) {}

    static final String PASS = "Pass";
    static final String FAIL = "Fail";
    static final String ERROR = "Error";
    static final String SKIPPED = "Skipped";
    static final String NOT_RUN = "Not run";

    /** A suite's page, ready to be written. */
    static final class SuiteReport {
        final String simpleName;
        final String packageName;
        String title = "";
        String classDisabledReason;
        final List<Spec> specs = new ArrayList<>();

        SuiteReport(String simpleName, String packageName) {
            this.simpleName = simpleName;
            this.packageName = packageName;
        }

        String fqn() {
            return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        }

        String fileName() {
            return simpleName.replaceAll("(?i)(test|suite)$", "") + "-test-report.html";
        }
    }

    // ---------- entry points ----------

    /** The default locations, so the CLI and {@code HtmlReportListener} cannot drift apart. */
    static Path testSrc() {
        return Path.of(System.getProperty("report.src", "src/test/java/org/sengmean/tests"));
    }

    /** The {@code @Suite} classes, read for the sections that get a report page of their own. */
    static Path suitesSrc(Path testSrc) {
        String configured = System.getProperty("report.suites");
        return configured == null || configured.isBlank()
                ? testSrc.resolveSibling("suites")
                : Path.of(configured);
    }

    static Path shotsDir() {
        return Path.of(System.getProperty("report.shots", "target/case-screenshots"));
    }

    static Path outDir() {
        return Path.of(System.getProperty("report.out.dir", "docs/reports"));
    }

    /**
     * Reads back the step trails {@link CaseLog} wrote during the run, keyed {@code Suite#method}.
     *
     * <p>Joined on the same key as the screenshots and for the same reason: the trail, the
     * pictures and the result are three views of one case, and one naming scheme keeps them from
     * drifting apart. A case with no log - one that ran before this existed, or one whose log
     * could not be written - simply gets a detail panel without a trail, never a broken page.
     */
    static Map<String, List<CaseLog.Entry>> readStepLogs(Path logsDir) {
        Map<String, List<CaseLog.Entry>> byCase = new HashMap<>();
        if (!Files.isDirectory(logsDir)) {
            return byCase;
        }
        try (Stream<Path> files = Files.list(logsDir)) {
            for (Path log : files.filter(p -> p.getFileName().toString().endsWith(".log")).toList()) {
                String stem = log.getFileName().toString();
                stem = stem.substring(0, stem.length() - 4);     // Suite.method
                int dot = stem.indexOf('.');
                if (dot < 0) {
                    continue;
                }
                String key = stem.substring(0, dot) + "#" + stem.substring(dot + 1);
                List<CaseLog.Entry> entries = new ArrayList<>();
                for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
                    String[] parts = line.split("\t", 3);
                    if (parts.length < 3) {
                        continue;
                    }
                    try {
                        entries.add(new CaseLog.Entry(Long.parseLong(parts[0]), parts[1], parts[2]));
                    } catch (NumberFormatException ignored) {
                        // A line that is not a step is not worth failing a report over.
                    }
                }
                if (!entries.isEmpty()) {
                    byCase.put(key, entries);
                }
            }
        } catch (IOException e) {
            System.out.println("Could not read the step logs from " + logsDir + ": " + e.getMessage());
        }
        return byCase;
    }

    /**
     * Regenerates the reports from Surefire XML that is already on disk.
     *
     * This is the by-hand route, for rebuilding the pages without re-running anything.
     * The reports are normally written by {@code HtmlReportListener} as part of the run
     * itself, which is the only way they can be trusted to match the code that produced them.
     *
     * Beware that Surefire never clears its results directory: it overwrites the files for
     * the classes it ran and leaves the rest alone. So running one package and then rebuilding
     * from XML blends that package's fresh results with whatever the last full run left behind,
     * under a single timestamp. The listener has no such problem - it only ever sees one run.
     */
    public static void main(String[] args) throws Exception {
        Path resultsDir = Path.of(System.getProperty("report.in", "target/surefire-reports"));

        Map<String, Outcome> outcomes = new HashMap<>();
        Map<String, String> environment = new LinkedHashMap<>();
        String runStamp = "not recorded";
        if (Files.isDirectory(resultsDir)) {
            outcomes = readOutcomes(resultsDir);
            environment = readEnvironment(resultsDir);
            runStamp = readRunStamp(resultsDir);
        } else {
            System.out.println("No Surefire results at " + resultsDir.toAbsolutePath()
                    + " - every case will be reported as Not run.");
        }

        generate(testSrc(), shotsDir(), outDir(), outcomes, Map.of(), environment, runStamp);
    }

    /**
     * Writes one page per suite plus the index.
     *
     * Every case found in the sources gets a row whether or not this run produced a result
     * for it, so a case filtered out by {@code -DexcludedGroups} or left out by
     * {@code -Dtest=...} shows as <i>Not run</i> instead of vanishing. The header says how
     * many suites the run actually covered, which is what keeps a one-package run from
     * reading like a full one that mostly did nothing.
     *
     * @param suiteFailures a failure that stopped a whole class before its cases ran, keyed on
     *                      the class's simple name. Those cases produce no result of their own,
     *                      and calling them "Not run" would describe the worst kind of run - a
     *                      suite that could not start - as though nothing had been asked of it.
     */
    public static void generate(Path testSrc,
                                Path shotsDir,
                                Path outDir,
                                Map<String, Outcome> outcomes,
                                Map<String, String> suiteFailures,
                                Map<String, String> environment,
                                String runStamp) throws IOException {
        if (!Files.isDirectory(testSrc)) {
            throw new IllegalStateException("No test sources at " + testSrc.toAbsolutePath());
        }
        List<SuiteReport> suites = readSpecs(testSrc);
        if (suites.isEmpty()) {
            throw new IllegalStateException("No @Test methods found under " + testSrc.toAbsolutePath());
        }
        suites = isolateSections(suites, suitesSrc(testSrc));

        outcomes = new HashMap<>(outcomes);
        for (SuiteReport suite : suites) {
            for (Spec spec : suite.specs) {
                // Looked up on the case's own class rather than the page's name, because a page
                // isolated from a class is named after its @Suite and not after the class.
                String failure = suiteFailures.get(spec.suite);
                if (failure != null) {
                    outcomes.putIfAbsent(spec.key(), containerFailure(failure));
                }
            }
        }

        Files.createDirectories(outDir);
        Path shotsOut = outDir.resolve("screenshots");
        Files.createDirectories(shotsOut);
        Map<String, List<String>> shots = copyScreenshots(shotsDir, shotsOut, outcomes.keySet());
        Map<String, List<CaseLog.Entry>> logs = readStepLogs(CaseLog.dir());
        Map<String, CaseSheetSteps.Entry> sheet = CaseSheetSteps.read(CaseSheetPage.workbook());

        String scope = scope(suites, outcomes);

        // Only the suites this run actually executed get their pages rewritten. The rest keep the
        // page and the summary from the last run that did execute them, which is what makes
        // `mvn test -Dtest=LoginTest` followed by `-Dtest=RegisterPageTest` leave two real reports
        // rather than one report and ten blanked pages.
        Set<String> live = new LinkedHashSet<>();
        int kept = 0;
        for (SuiteReport suite : suites) {
            live.add(suite.fileName());
            Path file = outDir.resolve(suite.fileName());
            if (!executed(suite, outcomes) && Files.isRegularFile(file)) {
                kept++;
                continue;
            }
            String html = renderSuite(suite, outcomes, shots, logs, sheet, environment, runStamp, scope);
            Files.writeString(file, html, StandardCharsets.UTF_8);
            // A page written only because it did not exist yet (a suite never run since its page
            // was added) has not run, so it carries no run time; stamping it would read as a run.
            saveState(outDir, suite, summarise(suite, outcomes, executed(suite, outcomes) ? runStamp : ""));
            System.out.println("Wrote " + file + " (" + suite.specs.size() + " cases)");
        }
        if (kept > 0) {
            System.out.println("Kept " + kept + " suite page(s) from their own last run; "
                    + "re-run a suite to refresh its page.");
        }
        prune(outDir, live);

        Path indexFile = outDir.resolve("index.html");
        Files.writeString(indexFile, renderIndex(suites, outcomes, environment, runStamp, scope),
                StandardCharsets.UTF_8);
        System.out.println("Wrote " + indexFile);
    }

    /** The result given to a case whose class failed before the case could run. */
    static Outcome containerFailure(String failure) {
        return new Outcome(ERROR,
                "The suite failed before this case could run: " + firstLine(failure), failure, 0);
    }

    // ---------- isolated section pages ----------

    private static final Pattern SUITE_DISPLAY_NAME =
            Pattern.compile("@SuiteDisplayName\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern SELECT_NESTED =
            Pattern.compile("@SelectClasses\\s*\\(\\s*names\\s*=\\s*\"([\\w.]+)\\$(\\w+)\"\\s*\\)");
    private static final Pattern SELECT_METHOD =
            Pattern.compile("@SelectMethod\\s*\\(\\s*\"([\\w.]+)#(\\w+)\"\\s*\\)");
    /** Anchored to a declaration line, so the word "class" in the suite's Javadoc cannot match. */
    private static final Pattern SUITE_CLASS = Pattern.compile(
            "^\\s*(?:public\\s+)?(?:final\\s+)?class\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern SUITE_PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;",
            Pattern.MULTILINE);

    /**
     * Splits a test class into one page per {@code @Suite} that selects part of it.
     *
     * <p>A report page is written for each top-level class, and a page is rewritten whenever its
     * class produced any result. OtherCategoryTest holds all ten sections of the Other Category
     * tab, each with its own {@code -Dsuite=}, so running one section rewrote the one page with
     * that section's rows and marked the other 69 <i>Not run</i> - wiping the results the other
     * sections had from their own runs. The same happened to the seed and its cleanup.
     *
     * <p>So a suite that selects a single {@code @Nested} class ({@code @SelectClasses(names =
     * "...Outer$Inner")}) or a single method ({@code @SelectMethod("...Outer#method")}) gets a page
     * of its own, named and titled after the suite, with its own saved state. Running one section
     * rewrites that section's page and leaves the rest as their own last run left them. A full
     * {@code ProductSuite} run rewrites all of them.
     *
     * <p>A class is split only when those suites cover every one of its cases together. A case no
     * suite selects would otherwise have no page at all, so a partly covered class keeps its
     * single page and says why on the console.
     */
    static List<SuiteReport> isolateSections(List<SuiteReport> suites, Path suitesSrc) throws IOException {
        if (!Files.isDirectory(suitesSrc)) {
            return suites;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(suitesSrc)) {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        Map<String, SuiteReport> byClass = new HashMap<>();
        for (SuiteReport suite : suites) {
            byClass.put(suite.fqn(), suite);
        }
        Map<String, List<SuiteReport>> sections = new HashMap<>();
        for (Path file : files) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            var nested = SELECT_NESTED.matcher(source);
            var method = SELECT_METHOD.matcher(source);
            boolean byNested = nested.find();
            boolean byMethod = !byNested && method.find();
            var className = SUITE_CLASS.matcher(source);
            if (!(byNested || byMethod) || !className.find()) {
                continue;
            }
            var selector = byNested ? nested : method;
            SuiteReport owner = byClass.get(selector.group(1));
            if (owner == null) {
                continue;
            }
            var pkg = SUITE_PACKAGE.matcher(source);
            SuiteReport section = new SuiteReport(className.group(1), pkg.find() ? pkg.group(1) : "");
            var display = SUITE_DISPLAY_NAME.matcher(source);
            section.title = display.find() ? unescape(display.group(1)) : humanise(section.simpleName);
            section.classDisabledReason = owner.classDisabledReason;
            String selected = selector.group(2);
            for (Spec spec : owner.specs) {
                if (selected.equals(byNested ? spec.group : spec.method)) {
                    section.specs.add(spec);
                }
            }
            if (!section.specs.isEmpty()) {
                sections.computeIfAbsent(owner.simpleName, k -> new ArrayList<>()).add(section);
            }
        }

        List<SuiteReport> result = new ArrayList<>();
        for (SuiteReport suite : suites) {
            List<SuiteReport> parts = sections.get(suite.simpleName);
            if (parts == null) {
                result.add(suite);
                continue;
            }
            Set<String> covered = new HashSet<>();
            parts.forEach(part -> part.specs.forEach(spec -> covered.add(spec.key())));
            long uncovered = suite.specs.stream().filter(spec -> !covered.contains(spec.key())).count();
            if (uncovered > 0) {
                System.out.println("Kept " + suite.simpleName + " as one report page: " + uncovered
                        + " of its cases are in no section suite.");
                result.add(suite);
                continue;
            }
            // In the order the class declares its cases, which is the order the sections run in.
            parts.sort(Comparator.comparingInt(part -> suite.specs.indexOf(part.specs.get(0))));
            result.addAll(parts);
        }
        return result;
    }

    /**
     * What one suite's page showed the last time that suite actually ran.
     *
     * <p>Kept so a run of one suite does not blank the others. Before this existed, every run
     * rewrote all eleven pages from the test sources, and a suite that had not run this time was
     * rendered <i>Not run</i> - so running LoginTest on its own threw away the Register results
     * from ten minutes earlier. Each suite's counts and run time are now saved when its page is
     * written, and the index reads them back for the suites this run did not touch.
     *
     * <p>Each case's status and the first line of its message are kept as well, keyed
     * {@code Suite#method}, so a suite's card on the index can list its failed and passed cases
     * whether it ran this time or is being carried forward.
     */
    record SuiteState(int cases, int pass, int fail, int skip, int notRun, double seconds, String ranAt,
                      Map<String, String> statuses, Map<String, String> messages) {

        String statusOf(Spec spec) {
            return statuses.getOrDefault(spec.key(), NOT_RUN);
        }

        String messageOf(Spec spec) {
            return messages.getOrDefault(spec.key(), "");
        }
    }

    /** Where the per-suite summaries live, beside the pages they describe. */
    private static Path stateDir(Path outDir) {
        return outDir.resolve(".state");
    }

    private static void saveState(Path outDir, SuiteReport suite, SuiteState state) {
        try {
            Files.createDirectories(stateDir(outDir));
            Properties props = new Properties();
            props.setProperty("cases", String.valueOf(state.cases()));
            props.setProperty("pass", String.valueOf(state.pass()));
            props.setProperty("fail", String.valueOf(state.fail()));
            props.setProperty("skip", String.valueOf(state.skip()));
            props.setProperty("notRun", String.valueOf(state.notRun()));
            props.setProperty("seconds", String.valueOf(state.seconds()));
            props.setProperty("ranAt", state.ranAt() == null ? "" : state.ranAt());
            state.statuses().forEach((key, status) -> props.setProperty("status." + key, status));
            state.messages().forEach((key, message) -> props.setProperty("message." + key, message));
            try (var out = Files.newOutputStream(stateDir(outDir).resolve(suite.simpleName + ".properties"))) {
                props.store(out, "Last run of " + suite.fqn());
            }
        } catch (IOException | RuntimeException e) {
            // A summary that could not be saved costs the index one row's history, never the run.
            System.out.println("Could not save report state for " + suite.simpleName + ": " + e);
        }
    }

    private static SuiteState loadState(Path outDir, SuiteReport suite) {
        Path file = stateDir(outDir).resolve(suite.simpleName + ".properties");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (var in = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            Map<String, String> statuses = new HashMap<>();
            Map<String, String> messages = new HashMap<>();
            for (String name : props.stringPropertyNames()) {
                if (name.startsWith("status.")) {
                    statuses.put(name.substring("status.".length()), props.getProperty(name));
                } else if (name.startsWith("message.")) {
                    messages.put(name.substring("message.".length()), props.getProperty(name));
                }
            }
            return new SuiteState(
                    Integer.parseInt(props.getProperty("cases", "0")),
                    Integer.parseInt(props.getProperty("pass", "0")),
                    Integer.parseInt(props.getProperty("fail", "0")),
                    Integer.parseInt(props.getProperty("skip", "0")),
                    Integer.parseInt(props.getProperty("notRun", "0")),
                    Double.parseDouble(props.getProperty("seconds", "0")),
                    props.getProperty("ranAt", ""),
                    statuses, messages);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * One row of the index, for a suite this run executed or for one being carried forward.
     *
     * <p>A carried-forward row says <b>when</b> it last ran, because an index that mixed this
     * run's results with an earlier run's without saying so would be the same blend the listener
     * exists to prevent - just spread over time instead of over suites.
     */
    private static String indexRow(SuiteReport suite, SuiteState state, boolean ranThisTime) {
        StringBuilder row = new StringBuilder();
        row.append("      <tr>\n")
                .append("        <td class=\"tc\"><a href=\"").append(escape(suite.fileName()))
                .append("\">").append(escape(suite.title)).append("</a>")
                .append("<div class=\"id\">").append(escape(suite.fqn()));
        if (!ranThisTime && state.ranAt() != null && !state.ranAt().isBlank()) {
            row.append(" &middot; last run ").append(escape(state.ranAt()));
        }
        row.append("</div></td>\n")
                .append("        <td class=\"minibar\">")
                .append(miniBar(state.pass(), state.fail(), state.skip(), state.notRun()))
                .append("</td>\n")
                .append("        <td>").append(suite.specs.size()).append("</td>\n")
                .append("        <td>").append(state.pass()).append("</td>\n")
                .append("        <td>").append(state.fail()).append("</td>\n")
                .append("        <td>").append(state.skip()).append("</td>\n")
                .append("        <td>").append(state.notRun()).append("</td>\n")
                .append("        <td>").append(String.format(Locale.ROOT, "%.1fs", state.seconds()))
                .append("</td>\n")
                .append("        <td><span class=\"st ")
                .append(state.fail() > 0 ? "fail" : state.pass() > 0 ? "pass" : "info").append("\">")
                .append(state.fail() > 0 ? "Findings" : state.pass() > 0 ? "Green" : "Not run")
                .append(ranThisTime ? "" : " <span class=\"muted\">(earlier run)</span>")
                .append("</span></td>\n")
                .append("      </tr>\n");
        return row.toString();
    }

    /** Pass, fail (an error counts as a failure here, as in every tally), skip or not run. */
    private static String statusGroup(String status) {
        return switch (status) {
            case PASS -> "pass";
            case FAIL, ERROR -> "fail";
            case SKIPPED -> "skip";
            default -> "notrun";
        };
    }

    /** The icon beside every status colour, so no status is told by colour alone. */
    private static String icon(String css) {
        return switch (css) {
            case "pass" -> "&#10003;";
            case "fail" -> "&#10007;";
            case "skip" -> "&#8856;";
            default -> "&#9675;";
        };
    }

    private static String count(String css, int count, String label) {
        return "<span class=\"cnt " + css + (count == 0 ? " zero" : "") + "\"><i>" + icon(css) + "</i>"
                + count + " " + label + "</span>";
    }

    private static String tile(String css, int count, String label) {
        return "        <div class=\"tile " + css + "\"><b>"
                + (css.isEmpty() ? "" : "<i>" + icon(css) + "</i>") + count + "</b><span>" + label
                + "</span></div>\n";
    }

    private static String chartLegend(String note) {
        StringBuilder legend = new StringBuilder("<div class=\"chart-legend\">");
        for (String[] item : new String[][]{{"pass", "Passed"}, {"fail", "Failed"},
                {"skip", "Skipped"}, {"notrun", "Not run"}}) {
            legend.append("<span><i class=\"sw ").append(item[0]).append("\"></i>").append(item[1]).append("</span>");
        }
        if (!note.isEmpty()) {
            legend.append("<span class=\"muted\">").append(note).append("</span>");
        }
        return legend.append("</div>").toString();
    }

    /**
     * The suite's result as a stacked bar - the form for a part-to-whole of four statuses. Each
     * segment is labelled with its count where it is wide enough to hold one, carries a hover
     * tooltip, and is named in the legend beneath, so a status is never told by colour alone.
     */
    private static String chart(String title, int pass, int fail, int skip, int notRun) {
        int total = pass + fail + skip + notRun;
        if (total == 0) {
            return "";
        }
        return "      <div class=\"chart\" role=\"img\" aria-label=\"" + escape(title) + ": " + pass
                + " passed, " + fail + " failed, " + skip + " skipped, " + notRun + " not run\">\n"
                + "        <div class=\"stack big\">"
                + stackSegment(title, "pass", pass, total, "passed", true)
                + stackSegment(title, "fail", fail, total, "failed", true)
                + stackSegment(title, "skip", skip, total, "skipped", true)
                + stackSegment(title, "notrun", notRun, total, "not run", true)
                + "</div>\n        " + chartLegend("") + "\n      </div>\n";
    }

    /** One segment, sized by flex-grow so the 2px gaps between segments never overflow the bar. */
    private static String stackSegment(String title, String css, int count, int total, String label,
                                       boolean labelled) {
        if (count == 0) {
            return "";
        }
        double share = 100.0 * count / total;
        String tip = title + " - " + count + " " + label + String.format(Locale.ROOT, " (%.0f%%)", share);
        return "<span class=\"seg " + css + "\" style=\"flex-grow:" + count + "\" tabindex=\"0\" data-tip=\""
                + escape(tip) + "\">" + (labelled && share >= 6 ? "<em>" + count + "</em>" : "") + "</span>";
    }

    /** One status's cases, linked to their rows below; the failures start open, the rest closed. */
    private static String caseList(SuiteState state, String css, String label, List<Spec> specs, boolean open) {
        if (specs.isEmpty()) {
            return "";
        }
        StringBuilder list = new StringBuilder("      <details class=\"cl ").append(css).append("\"")
                .append(open ? " open" : "").append("><summary><i>").append(icon(css)).append("</i>")
                .append(label).append(" <span class=\"n\">(").append(specs.size()).append(")</span></summary>\n")
                .append("        <ol>\n");
        for (Spec spec : specs) {
            list.append("          <li><a href=\"#").append(anchor(spec)).append("\">")
                    .append(spec.id.isEmpty() ? escape(spec.method) : escape(spec.id)).append("</a> ")
                    .append(escape(spec.expected));
            if (spec.tags.contains("known-defect")) {
                list.append(" <span class=\"tag known\">known defect</span>");
            }
            String message = state.messageOf(spec);
            if ("fail".equals(css) && !message.isBlank()) {
                list.append("<div class=\"why\">").append(escape(message)).append("</div>");
            }
            list.append("</li>\n");
        }
        list.append("        </ol>\n      </details>\n");
        return list.toString();
    }

    /**
     * The suite page's summary behaviour: remember whether the reader left the header minimised,
     * and one shared tooltip for the chart's segments. Storage is wrapped because it can be
     * unavailable, and the page must work without it.
     */
    private static String summaryScript() {
        return """
                  <div id="tip" role="tooltip"></div>
                  <script>
                  (function(){
                    var sum=document.querySelector('details.rpt-sum');
                    var KEY='admin-weibo-summary-open';
                    if(sum){
                      try{var v=localStorage.getItem(KEY);if(v!==null){sum.open=v==='1';}}catch(e){}
                      sum.addEventListener('toggle',function(){try{localStorage.setItem(KEY,sum.open?'1':'0');}catch(e){}});
                    }
                    var tip=document.getElementById('tip');
                    function show(el,x,y){tip.textContent=el.getAttribute('data-tip');tip.style.display='block';
                      var w=tip.offsetWidth;tip.style.left=Math.max(8,Math.min(x+12,window.innerWidth-w-8))+'px';tip.style.top=(y+14)+'px';}
                    function hide(){tip.style.display='none';}
                    document.addEventListener('mousemove',function(e){var el=e.target.closest&&e.target.closest('[data-tip]');
                      if(el){show(el,e.clientX,e.clientY);}else{hide();}});
                    document.addEventListener('focusin',function(e){var el=e.target.closest&&e.target.closest('[data-tip]');
                      if(el){var r=el.getBoundingClientRect();show(el,r.left,r.bottom);}});
                    document.addEventListener('focusout',hide);
                  })();
                  </script>
                """;
    }

    /** This run's tally for one suite, in the shape the index and the state file both want. */
    private static SuiteState summarise(SuiteReport suite, Map<String, Outcome> outcomes, String ranAt) {
        int pass = 0;
        int fail = 0;
        int skip = 0;
        int notRun = 0;
        double seconds = 0;
        Map<String, String> statuses = new HashMap<>();
        Map<String, String> messages = new HashMap<>();
        for (Spec spec : suite.specs) {
            Outcome outcome = outcomes.get(spec.key());
            String status = status(outcome);
            switch (status) {
                case PASS -> pass++;
                case FAIL, ERROR -> fail++;
                case SKIPPED -> skip++;
                default -> notRun++;
            }
            if (outcome != null) {
                seconds += outcome.seconds();
                statuses.put(spec.key(), status);
                String message = firstLine(outcome.message());
                if (!message.isBlank()) {
                    messages.put(spec.key(), trim(message, 240));
                }
            }
        }
        return new SuiteState(suite.specs.size(), pass, fail, skip, notRun, seconds, ranAt,
                statuses, messages);
    }

    /** True when this run produced a result for at least one of the suite's cases. */
    private static boolean executed(SuiteReport suite, Map<String, Outcome> outcomes) {
        for (Spec spec : suite.specs) {
            if (outcomes.get(spec.key()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes report pages left behind by suites that no longer exist.
     *
     * <p>The suite list is read from the test <em>sources</em>, not from what ran, so every
     * suite that still exists gets a page written on every run whether or not it was executed -
     * that is what makes "Not run" possible. A {@code *-test-report.html} that was not written
     * this time therefore has no class behind it any more, and is a leftover.
     *
     * <p>Left unpruned they accumulate and are indistinguishable from live reports: the
     * throwaway probe classes used to diagnose the cashier pages left published pages for
     * {@code BlankTabProbe}, {@code WithdrawOptionProbe} and {@code PresetAmountProbe} long
     * after the classes were deleted, two of them committed. {@code index.html} is never a
     * candidate - it is written immediately after this runs.
     */
    private static void prune(Path outDir, Set<String> written) throws IOException {
        try (Stream<Path> files = Files.list(outDir)) {
            for (Path stale : files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("-test-report.html"))
                    .filter(p -> !written.contains(p.getFileName().toString()))
                    .toList()) {
                Files.delete(stale);
                System.out.println("Removed " + stale + " (no test class of that name any more)");
            }
        }
    }

    /** "all 9 suites" or "4 of 9 suites" - so a subset run is self-describing. */
    private static String scope(List<SuiteReport> suites, Map<String, Outcome> outcomes) {
        long covered = suites.stream()
                .filter(suite -> suite.specs.stream().anyMatch(spec -> outcomes.containsKey(spec.key())))
                .count();
        if (covered == suites.size()) {
            return "all " + suites.size() + " suites";
        }
        return covered + " of " + suites.size() + " suites";
    }

    // ---------- reading the test sources ----------

    /**
     * The comment that names the other workbook rows a case answers for.
     *
     * <p>Matched as a literal prefix rather than a regex because it is a convention rather than
     * syntax, and a reader adding one should be able to copy the phrase exactly. It must be the
     * whole start of the line - {@code // Also covers workbook rows: A, B, C} - directly above the
     * case's annotations. See {@link Spec#alsoCovers} for why these are not in the display name.
     */
    private static final String ALSO_COVERS = "// Also covers workbook rows:";

    private static final Pattern DISPLAY_NAME =
            Pattern.compile("@DisplayName\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern TAG =
            Pattern.compile("@Tag\\s*\\(\\s*\"([^\"]*)\"");
    private static final Pattern DISABLED =
            Pattern.compile("@Disabled\\s*(?:\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\")?");
    private static final Pattern CLASS_DECL = Pattern.compile("\\bclass\\s+(\\w+)");
    private static final Pattern METHOD_DECL = Pattern.compile("\\bvoid\\s+(\\w+)\\s*\\(\\s*\\)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    /** One case id: a prefix of one or more letter-led segments, a hyphen, and a number. */
    private static final String ID = "[A-Z][A-Z0-9]{0,23}(?:-[A-Z][A-Z0-9]{0,23})*-\\d+";

    /** "DEP-02: ...", "AC-04/AC-05: ...", "W-1 / WDR-03: ..." */
    /**
     * A case id at the head of a display name, e.g. "DEP-13: ..." or "RC-09/RC-10: ...".
     *
     * <b>The id may be separated from the title by a colon or by a spaced dash</b> - "DEP-02: ..."
     * and "DEP-001 - ..." are both read. Only the colon was accepted at first, so a display name
     * written with a dash had its id left unrecognised: the ID column showed an em-dash and the
     * id itself ended up inside the expected-result text, which is how it was noticed. A dash
     * must have whitespace on both sides, so an id like DEP-001 is not split at its own hyphen.
     *
     * The prefix length follows the test case sheet, because the ids are the sheet's own. A sheet
     * id is its row's {@code TC_&lt;FEATURE&gt;_&lt;number&gt;} with the wrapper stripped -
     * TC_HOME_001 becomes HOME-001, TC_FORGOTPASS_002 becomes FORGOTPASS-002 - so the length is
     * whatever the tab called itself, and the tabs do not agree at all. The original
     * five-character cap silently dropped the id from the longer ones, and twelve did the same to
     * the longer still: {@code HKDGAMELAUNCH} is thirteen and {@code HKDEVENTTRIGGER} fifteen, so
     * the twenty-five cases on the Game Launch and Automate Greeting tabs rendered with an em dash
     * in the Id column - present in the report, but with nothing to trace back to a sheet row.
     *
     * <p>Twenty-four now, which covers every prefix the workbook currently uses and leaves room
     * for {@code MEMBERSHIPPROGRAM} and {@code TURNOVERCOMPLETIONCAL} when those tabs are
     * automated. <b>This is not the same as loosening the pattern.</b> What stops a title like
     * "TOP 10 games - the lobby lists them" being misread as an id is the anchor, the digits and
     * the required {@code ": "} or {@code " - "} separator, and all three are unchanged; the cap
     * only decides which of the client's own prefixes the report is able to express. Lengthening
     * it is the fix - renaming an id to fit would mean deviating from the workbook, which is the
     * one thing these ids exist to avoid.
     *
     * <p><b>A prefix may itself be hyphenated.</b> The Other Category tab (2026-09-23) writes its
     * ids as {@code OC-LST-001} and {@code OC-I18N-003} - a module and a section before the
     * number - and the single-segment prefix left every one of its 78 cases without an id in the
     * report. Each extra segment must still begin with a letter, so a title such as
     * "TOP-10 games - ..." is read no differently than before: {@code 10} cannot start a segment,
     * and the number that ends the id is still followed by the required separator.
     */
    static final Pattern CASE_ID = Pattern.compile(
            "^(" + ID + "(?:\\s*[/,]\\s*" + ID + ")*)"
                    + "(?:\\s*:\\s*|\\s+[-–—]\\s+)");

    /** One entry on the class stack while a source file is being read. */
    private static final class Frame {
        final String name;
        final String displayName;
        final int bodyDepth;

        Frame(String name, String displayName, int bodyDepth) {
            this.name = name;
            this.displayName = displayName;
            this.bodyDepth = bodyDepth;
        }
    }

    /**
     * Parses the test sources into suites and cases. Package-private rather than private so
     * {@link CaseIndex} can reuse it: the run endpoint has to resolve a case id to the exact
     * class and method Surefire needs, and re-parsing the sources a second way would be two
     * parsers to keep agreeing about @DisplayName.
     */
    static List<SuiteReport> readSpecs(Path testSrc) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(testSrc)) {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        List<SuiteReport> suites = new ArrayList<>();
        for (Path file : files) {
            SuiteReport suite = readSuite(file);
            if (suite != null && !suite.specs.isEmpty()) {
                suites.add(suite);
            }
        }
        return suites;
    }

    /**
     * Reads one test class into a {@link SuiteReport}.
     *
     * This is a line scanner rather than a real Java parser, which is enough because it only
     * has to recognise three shapes: an annotation, a class header and a no-argument method
     * header. String literals are blanked before braces are counted, so an XPath or a JSON
     * fragment inside a locator cannot throw the nesting depth off.
     */
    private static SuiteReport readSuite(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String packageName = "";
        SuiteReport suite = null;

        Deque<Frame> stack = new ArrayDeque<>();
        List<String> pending = new ArrayList<>();
        /** Ids from an "Also covers workbook rows:" comment waiting for the case below them. */
        List<String> pendingCovers = new ArrayList<>();
        int depth = 0;
        boolean inBlockComment = false;
        /** Unclosed parentheses in an annotation that is still being read across lines. */
        int annotationParens = 0;

        for (String raw : lines) {
            String line = raw.trim();

            if (inBlockComment) {
                int end = line.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                line = line.substring(end + 2).trim();
                inBlockComment = false;
            }
            int start = line.indexOf("/*");
            if (start >= 0) {
                int end = line.indexOf("*/", start + 2);
                if (end < 0) {
                    inBlockComment = true;
                    line = line.substring(0, start).trim();
                } else {
                    line = (line.substring(0, start) + line.substring(end + 2)).trim();
                }
            }
            if (line.startsWith(ALSO_COVERS)) {
                // Collected before the comment skip below, and cleared wherever `pending` is, so
                // it belongs to the next case the same way its annotations do.
                for (String covered : line.substring(ALSO_COVERS.length()).split(",")) {
                    String trimmed = covered.trim().toUpperCase(Locale.ROOT);
                    if (!trimmed.isEmpty()) {
                        pendingCovers.add(trimmed);
                    }
                }
                continue;
            }
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }

            if (line.startsWith("package ")) {
                packageName = line.substring(8).replace(";", "").trim();
                continue;
            }
            if (line.startsWith("import ")) {
                continue;
            }

            // An annotation may wrap across lines, and its continuation lines are joined onto the
            // entry they belong to rather than falling through to the code branch below - which
            // clears everything collected so far. A wrapped @Disabled reason therefore used to
            // take the @Test and @DisplayName above it down with it, and the case did not appear
            // in the report as skipped: it did not appear at all, as though it had never been
            // written. Parentheses are counted after string literals are blanked, so a bracket
            // inside a reason cannot hold the annotation open.
            String forParens = STRING_LITERAL.matcher(line).replaceAll("\"\"");
            if (annotationParens > 0) {
                pending.set(pending.size() - 1, pending.get(pending.size() - 1) + " " + line);
                annotationParens += count(forParens, '(') - count(forParens, ')');
                continue;
            }
            if (line.startsWith("@")) {
                pending.add(line);
                annotationParens = count(forParens, '(') - count(forParens, ')');
                continue;
            }

            int before = depth;
            String forBraces = STRING_LITERAL.matcher(line).replaceAll("\"\"");
            int delta = count(forBraces, '{') - count(forBraces, '}');

            var classMatch = CLASS_DECL.matcher(line);
            var methodMatch = METHOD_DECL.matcher(line);

            if (classMatch.find()) {
                String name = classMatch.group(1);
                String display = firstGroup(DISPLAY_NAME, pending);
                if (stack.isEmpty()) {
                    suite = new SuiteReport(name, packageName);
                    suite.title = display == null ? humanise(name) : display;
                    suite.classDisabledReason = disabledReason(pending);
                }
                stack.push(new Frame(name, display == null ? humanise(name) : display, before + 1));
                pending.clear();
                pendingCovers.clear();
            } else if (methodMatch.find() && containsAnnotation(pending, "@Test")) {
                String method = methodMatch.group(1);
                String display = firstGroup(DISPLAY_NAME, pending);
                String text = display == null ? humanise(method) : display;
                String id = "";
                var idMatch = CASE_ID.matcher(text);
                if (idMatch.find()) {
                    id = idMatch.group(1).replaceAll("\\s*/\\s*", " / ");
                    text = text.substring(idMatch.end());
                }
                Frame enclosing = stack.peek();
                boolean nested = stack.size() > 1;
                if (suite != null) {
                    suite.specs.add(new Spec(
                            suite.simpleName,
                            nested && enclosing != null ? enclosing.name : "",
                            nested && enclosing != null ? enclosing.displayName : "",
                            method, id, text, allGroups(TAG, pending), disabledReason(pending), List.copyOf(pendingCovers)));
                }
                pending.clear();
                pendingCovers.clear();
            } else {
                pending.clear();
                pendingCovers.clear();
            }

            depth += delta;
            while (!stack.isEmpty() && depth < stack.peek().bodyDepth) {
                stack.pop();
            }
        }
        return suite;
    }

    private static boolean containsAnnotation(List<String> pending, String annotation) {
        for (String line : pending) {
            if (line.equals(annotation) || line.startsWith(annotation + "(")) {
                return true;
            }
        }
        return false;
    }

    private static String firstGroup(Pattern pattern, List<String> pending) {
        for (String line : pending) {
            var m = pattern.matcher(line);
            if (m.find() && m.group(1) != null) {
                return unescape(m.group(1));
            }
        }
        return null;
    }

    private static List<String> allGroups(Pattern pattern, List<String> pending) {
        List<String> found = new ArrayList<>();
        for (String line : pending) {
            var m = pattern.matcher(line);
            while (m.find()) {
                if (m.group(1) != null) {
                    found.add(unescape(m.group(1)));
                }
            }
        }
        return found;
    }

    /** "" when {@code @Disabled} carries no reason, {@code null} when there is no annotation. */
    private static String disabledReason(List<String> pending) {
        for (String line : pending) {
            if (!line.startsWith("@Disabled")) {
                continue;
            }
            var m = DISABLED.matcher(line);
            if (m.find()) {
                return m.group(1) == null ? "" : unescape(m.group(1));
            }
        }
        return null;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", " ");
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /** "amountBelowMinimumIsRejected" becomes "Amount below minimum is rejected". */
    private static String humanise(String camel) {
        String spaced = camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    // ---------- reading the Surefire results ----------

    private static Map<String, Outcome> readOutcomes(Path resultsDir) throws Exception {
        Map<String, Outcome> outcomes = new HashMap<>();
        for (Path xml : xmlFiles(resultsDir)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
            NodeList cases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element testcase = (Element) cases.item(i);
                String method = testcase.getAttribute("name");
                String className = testcase.getAttribute("classname");
                double seconds = parseSeconds(testcase.getAttribute("time"));

                String status = PASS;
                String message = "";
                String detail = "";
                Element failure = firstChild(testcase, "failure");
                Element error = firstChild(testcase, "error");
                Element skipped = firstChild(testcase, "skipped");
                if (failure != null) {
                    status = FAIL;
                    message = failure.getAttribute("message");
                    detail = failure.getTextContent();
                } else if (error != null) {
                    status = ERROR;
                    message = error.getAttribute("message");
                    detail = error.getTextContent();
                } else if (skipped != null) {
                    status = SKIPPED;
                    message = skipped.getAttribute("message");
                }
                outcomes.put(topLevel(className) + "#" + method,
                        new Outcome(status, message, detail, seconds));
            }
        }
        return outcomes;
    }

    /** The environment the run used, from the properties Surefire records in every XML file. */
    private static Map<String, String> readEnvironment(Path resultsDir) throws Exception {
        Map<String, String> wanted = new LinkedHashMap<>();
        List<Path> files = xmlFiles(resultsDir);
        if (files.isEmpty()) {
            return wanted;
        }
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(files.get(0).toFile());
        NodeList properties = doc.getElementsByTagName("property");
        // The same list the listener records, so a page rebuilt by hand from XML carries the same
        // environment panel as one written by the run itself. Surefire writes every system
        // property of the forked JVM into each file, so java.version and os.name are there too.
        Set<String> keys = Set.of("base.url", "base.query", "test.username", "test.alt.username",
                "headless", "slowMo", "java.version", "os.name");
        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);
            String name = property.getAttribute("name");
            if (keys.contains(name)) {
                wanted.put(name, property.getAttribute("value"));
            }
        }
        return wanted;
    }

    private static String readRunStamp(Path resultsDir) throws IOException {
        Instant latest = null;
        for (Path xml : xmlFiles(resultsDir)) {
            BasicFileAttributes attrs = Files.readAttributes(xml, BasicFileAttributes.class);
            Instant when = attrs.lastModifiedTime().toInstant();
            if (latest == null || when.isAfter(latest)) {
                latest = when;
            }
        }
        if (latest == null) {
            return "not recorded";
        }
        return LocalDateTime.ofInstant(latest, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static List<Path> xmlFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.list(dir)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(tag)) {
                return (Element) child;
            }
        }
        return null;
    }

    /** "org.sengmean.tests.payment.DepositPageTest$ValidDeposits" becomes "DepositPageTest". */
    private static String topLevel(String className) {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        int nested = simple.indexOf('$');
        return nested < 0 ? simple : simple.substring(0, nested);
    }

    private static double parseSeconds(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * The click-to-zoom overlay, and the script that drives it.
     *
     * <p>Evidence used to be a 190px thumbnail linking to the raw PNG in a new tab. Neither half
     * worked as a preview: at that size a 1920x1080 capture says only "a picture exists", and
     * opening the file in a tab loses the report and every bit of context around the row. The
     * thumbnails are bigger now, and clicking one opens it full size over the report, captioned
     * with what it shows.
     *
     * <p>Plain inline script and no dependencies on purpose. These pages are opened straight off
     * disk, and often committed and read on a machine that has never built the project, so
     * anything fetched from a network would leave a reader looking at a broken preview.
     */
    private static String lightbox() {
        return """
                <div id="lightbox" role="dialog" aria-modal="true" aria-label="Screenshot preview">
                  <img id="lightbox-img" alt="">
                  <div class="cap" id="lightbox-cap"></div>
                  <div class="hint">Click anywhere, or press Escape, to close</div>
                </div>
                <script>
                (function () {
                  var box = document.getElementById('lightbox');
                  var img = document.getElementById('lightbox-img');
                  var cap = document.getElementById('lightbox-cap');
                  function open(src, caption) {
                    img.setAttribute('src', src);
                    img.setAttribute('alt', caption || '');
                    cap.textContent = caption || '';
                    box.classList.add('open');
                  }
                  function close() {
                    box.classList.remove('open');
                    img.removeAttribute('src');
                  }
                  document.addEventListener('click', function (e) {
                    // Both the Evidence column's images and the ones shown inline against a trail
                    // line. The inline ones were left out when they were added, so they fell
                    // through to target="_blank" and every capture opened a new tab - fifty-two of
                    // them if a reader worked down the transfer walk.
                    var link = e.target.closest ? e.target.closest('a.shot, a.step-shot') : null;
                    if (link) {
                      // Only take over the plain click - a modified click still opens the file
                      // in a tab, which is what someone reaching for that expects.
                      if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
                      e.preventDefault();
                      var inner = link.querySelector('img');
                      open(link.getAttribute('href'), inner ? inner.getAttribute('alt') : '');
                    } else if (box.classList.contains('open')) {
                      close();
                    }
                  });
                  document.addEventListener('keydown', function (e) {
                    if (e.key === 'Escape') { close(); }
                  });
                })();
                </script>
                """;
    }

    /**
     * Opens the detail panels, filters by status and searches - all client-side, all inline.
     *
     * <p>Same rule as the lightbox: no dependency may be fetched. These pages are committed and
     * read on machines that have never built the project, and a report whose controls silently
     * stop working off a network is worse than a report with no controls.
     *
     * <p>A row and its detail panel are two {@code &lt;tr&gt;} elements rather than one nested
     * table, so that the seven columns line up across every group on the page. That is why the
     * script pairs them by sibling order and has to hide and show them together - a detail row
     * left behind by a filtered-out case would appear under someone else's.
     *
     * <p>Deep links keep working: a fragment id in the address opens that row's panel and scrolls
     * to it, which is what the findings list at the top of the page relies on.
     */
    private static String filterScript() {
        return """
                <script>
                (function () {
                  var rows = Array.prototype.slice.call(document.querySelectorAll('tr.case'));
                  var shown = document.getElementById('shown');
                  var search = document.getElementById('q');
                  var expandAll = document.getElementById('expand-all');
                  var filter = 'all';
                  var query = '';

                  function panelOf(row) {
                    var next = row.nextElementSibling;
                    return next && next.classList.contains('detail') ? next : null;
                  }

                  function matches(row) {
                    if (filter !== 'all' && row.getAttribute('data-status') !== filter) { return false; }
                    if (query === '') { return true; }
                    return (row.getAttribute('data-search') || '').indexOf(query) >= 0;
                  }

                  function apply() {
                    var visible = 0;
                    rows.forEach(function (row) {
                      var ok = matches(row);
                      if (ok) { visible++; }
                      row.classList.toggle('hide', !ok);
                      var panel = panelOf(row);
                      if (panel) { panel.classList.toggle('hide', !ok || !row.classList.contains('open')); }
                    });
                    // A group whose every case is filtered out is hidden with its heading, so the
                    // page does not end in a run of empty tables under numbered titles.
                    Array.prototype.forEach.call(document.querySelectorAll('section.grp'), function (grp) {
                      var any = grp.querySelector('tr.case:not(.hide)');
                      grp.classList.toggle('hide', !any);
                    });
                    if (shown) {
                      shown.textContent = visible + ' of ' + rows.length + ' cases';
                    }
                  }

                  function open(row, wanted) {
                    var panel = panelOf(row);
                    if (!panel) { return; }
                    var isOpen = wanted === undefined ? !row.classList.contains('open') : wanted;
                    row.classList.toggle('open', isOpen);
                    panel.classList.toggle('hide', !isOpen);
                    var button = row.querySelector('button.more');
                    if (button) { button.setAttribute('aria-expanded', isOpen ? 'true' : 'false'); }
                  }

                  document.addEventListener('click', function (e) {
                    var more = e.target.closest ? e.target.closest('button.more') : null;
                    if (more) {
                      open(more.closest('tr.case'));
                      return;
                    }
                    var flt = e.target.closest ? e.target.closest('button.flt[data-filter]') : null;
                    if (flt) {
                      filter = flt.getAttribute('data-filter');
                      Array.prototype.forEach.call(document.querySelectorAll('button.flt[data-filter]'),
                        function (b) { b.classList.toggle('on', b === flt); });
                      apply();
                    }
                  });

                  if (search) {
                    search.addEventListener('input', function () {
                      query = search.value.trim().toLowerCase();
                      apply();
                    });
                  }

                  if (expandAll) {
                    expandAll.addEventListener('click', function () {
                      var opening = expandAll.getAttribute('data-open') !== 'yes';
                      expandAll.setAttribute('data-open', opening ? 'yes' : 'no');
                      expandAll.textContent = opening ? 'Collapse all details' : 'Expand all details';
                      rows.forEach(function (row) { if (!row.classList.contains('hide')) { open(row, opening); } });
                    });
                  }

                  function openFromHash() {
                    if (!window.location.hash) { return; }
                    var target = document.getElementById(window.location.hash.slice(1));
                    if (!target) { return; }
                    // The hash may name either anchor a row carries: the internal
                    // case-<suite>-<method>, which the findings list links to, or the case
                    // id itself - "#HOME-002" - which is what the client test case sheet's
                    // per-row links use. The id sits on a <td>, so resolve upwards to the
                    // row rather than testing the target: without this the browser scrolls
                    // to the right place and leaves the details shut, which reads as a link
                    // that landed on the wrong thing.
                    var row = target.classList.contains('case')
                      ? target
                      : (target.closest ? target.closest('tr.case') : null);
                    if (row) {
                      open(row, true);
                      row.classList.add('flash');
                      row.scrollIntoView({ block: 'center' });
                    }
                  }
                  window.addEventListener('hashchange', openFromHash);
                  openFromHash();
                  apply();
                })();
                </script>
                """;
    }

    // ---------- screenshots ----------

    /**
     * Removes the published PNGs of the cases that produced a result in this run, and no others.
     *
     * <p>A screenshot is named {@code <Suite>.<method>[-nnn].png}, the case key
     * {@code Suite#method} with a dot for the hash. This used to clear by the suite part alone,
     * which is the top-level class - and every Other Category section is one class, so running
     * the Create section deleted the Edit section's pictures while the Edit page was being kept,
     * leaving it with broken images. A case's pictures now live exactly as long as its row does.
     */
    static void clearShotsOfCases(Path directory, Set<String> caseKeys) {
        if (caseKeys.isEmpty() || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.list(directory)) {
            for (Path file : walk.filter(p -> p.getFileName().toString().endsWith(".png")).toList()) {
                String name = file.getFileName().toString();
                String stem = name.substring(0, name.length() - 4).replaceAll("-\\d+$", "");
                if (caseKeys.contains(stem.replaceFirst("\\.", "#"))) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            System.out.println("Could not clear old screenshots from " + directory + ": " + e.getMessage());
        }
    }

    /**
     * Removes the PNGs already in the report's screenshot directory.
     *
     * Only PNGs, and only at the top level: the directory belongs to the report, but deleting
     * indiscriminately in a checked-in tree is how a generator eats something it did not create.
     * A failure to clear is reported and swallowed - a stale picture is worth less than a report,
     * so it must not stop one being written.
     */
    private static void clearPngs(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.list(directory)) {
            for (Path file : walk.filter(p -> p.getFileName().toString().endsWith(".png")).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            System.out.println("Could not clear old screenshots from " + directory + ": "
                    + e.getMessage());
        }
    }

    /** Copies the run's failure screenshots next to the reports, keyed by {@code Suite#method}. */
    private static Map<String, List<String>> copyScreenshots(Path from, Path to, Set<String> ranThisTime) throws IOException {
        Map<String, List<String>> byCase = new HashMap<>();
        // Clear the destination first, for the same reason the listener clears the source: a
        // report shows one run, never a blend. Copying into a directory that was never emptied
        // left images from earlier runs lying beside the current ones - 78 of the 203 here were
        // stale blanks from before the capture rules changed - and a reader has no way to tell
        // which run any given picture came from.
        // Only the cases that ran THIS time have their old images removed (see clearShotsOfCases),
        // so a page kept from an earlier run keeps its pictures.
        clearShotsOfCases(to, ranThisTime);
        if (!Files.isDirectory(from)) {
            return byCase;
        }
        try (Stream<Path> walk = Files.list(from)) {
            // Sorted, so a cell reads in the order the case went through. Mid-case captures are
            // named "<case>-1.png" and the closing one "<case>.png"; a hyphen sorts before a full
            // stop, so the step shots come first and the final state last. Files.list makes no
            // ordering promise of its own, which would leave the sequence to the filesystem.
            for (Path shot : walk.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList()) {
                String name = shot.getFileName().toString();
                String stem = name.substring(0, name.length() - 4);   // Suite.method or Suite.method-1
                int dot = stem.indexOf('.');
                if (dot < 0) {
                    continue;
                }
                String suite = stem.substring(0, dot);
                String method = stem.substring(dot + 1).replaceAll("-\\d+$", "");
                Files.copy(shot, to.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                byCase.computeIfAbsent(suite + "#" + method, k -> new ArrayList<>())
                        .add("screenshots/" + name);
            }
        }
        byCase.values().forEach(Collections::sort);
        return byCase;
    }

    // ---------- rendering ----------

    private static String renderSuite(SuiteReport suite,
                                      Map<String, Outcome> outcomes,
                                      Map<String, List<String>> shots,
                                      Map<String, List<CaseLog.Entry>> logs,
                                      Map<String, CaseSheetSteps.Entry> sheet,
                                      Map<String, String> environment,
                                      String runStamp,
                                      String scope) {
        int pass = 0;
        int fail = 0;
        int skip = 0;
        int notRun = 0;
        double total = 0;
        List<Spec> failed = new ArrayList<>();
        for (Spec spec : suite.specs) {
            Outcome outcome = outcomes.get(spec.key());
            switch (status(outcome)) {
                case PASS -> pass++;
                case FAIL, ERROR -> {
                    fail++;
                    failed.add(spec);
                }
                case SKIPPED -> skip++;
                default -> notRun++;
            }
            if (outcome != null) {
                total += outcome.seconds();
            }
        }

        StringBuilder html = new StringBuilder();
        html.append(head(suite.title + " - test case report"));
        html.append("<body>\n<div class=\"wrap\">\n");
        // The header is the suite's statistics summary, and it collapses to one line: the title,
        // the counts and the proportions bar stay visible when it is minimised; the run details,
        // the tiles, the chart and the cases grouped by status fold away beneath them.
        SuiteState state = summarise(suite, outcomes, runStamp);
        int ran = executed(pass, fail, skip);
        html.append("  <header class=\"rpt\">\n  <details class=\"rpt-sum\" open>\n")
                .append("    <summary>\n")
                .append("      <span class=\"rpt-line\"><span class=\"chev\" aria-hidden=\"true\"></span>")
                .append("<h1>").append(escape(suite.title)).append("</h1>")
                .append("<span class=\"rpt-toggle-hint\">click to minimise / expand</span></span>\n")
                .append("      <span class=\"rpt-counts\">")
                .append(count("pass", pass, "passed"))
                .append(count("fail", fail, "failed"))
                .append(count("skip", skip, "skipped"))
                .append(count("notrun", notRun, "not run"))
                .append("<span class=\"cnt\">").append(suite.specs.size()).append(" cases</span>")
                .append("</span>\n")
                .append(miniBar(pass, fail, skip, notRun).replace("bar mini", "bar"))
                .append("\n    </summary>\n");
        html.append("    <div class=\"sub\">Test case report &mdash; <code>")
                .append(escape(suite.fqn())).append("</code></div>\n");
        html.append(metaGrid(environment, runStamp, scope));
        html.append("    <h2 class=\"rpt-h\">Statistics</h2>\n    <div class=\"tiles\">\n")
                .append(tile("", suite.specs.size(), "cases"))
                .append(tile("pass", pass, "passed"))
                .append(tile("fail", fail, "failed"))
                .append(tile("skip", skip, "skipped"))
                .append(tile("notrun", notRun, "not run"))
                .append("        <div class=\"tile\"><b>")
                .append(ran == 0 ? "&ndash;" : String.format(Locale.ROOT, "%.0f%%", 100.0 * pass / ran))
                .append("</b><span>pass rate of the cases that ran</span></div>\n")
                .append("        <div class=\"tile\"><b>").append(String.format(Locale.ROOT, "%.1fs", total))
                .append("</b><span>browser time</span></div>\n")
                .append("    </div>\n");
        html.append(chart(suite.title, pass, fail, skip, notRun));
        html.append("    <h2 class=\"rpt-h\">Cases by result</h2>\n    <div class=\"case-lists\">\n");
        Map<String, List<Spec>> byStatus = new LinkedHashMap<>();
        for (String group : new String[]{"fail", "pass", "skip", "notrun"}) {
            byStatus.put(group, new ArrayList<>());
        }
        for (Spec spec : suite.specs) {
            byStatus.get(statusGroup(state.statusOf(spec))).add(spec);
        }
        html.append(caseList(state, "fail", "Failed", byStatus.get("fail"), true))
                .append(caseList(state, "pass", "Passed", byStatus.get("pass"), false))
                .append(caseList(state, "skip", "Skipped", byStatus.get("skip"), false))
                .append(caseList(state, "notrun", "Not run", byStatus.get("notrun"), false))
                .append("    </div>\n");
        html.append("  </details>\n  </header>\n\n");
        html.append("  <div class=\"nav\"><a href=\"index.html\">&larr; All suites</a></div>\n");

        if (suite.classDisabledReason != null) {
            html.append("  <div class=\"callout warn\"><h2>This suite is disabled</h2>")
                    .append(escape(suite.classDisabledReason.isEmpty()
                            ? "The class carries @Disabled, so none of its cases run."
                            : suite.classDisabledReason))
                    .append("</div>\n");
        }

        html.append(findings(failed, outcomes));

        // Sections follow the @Nested classes, in the order they appear in the source.
        LinkedHashMap<String, List<Spec>> groups = new LinkedHashMap<>();
        for (Spec spec : suite.specs) {
            groups.computeIfAbsent(spec.group, k -> new ArrayList<>()).add(spec);
        }
        html.append(toolbar(suite.specs.size()));
        int section = 0;
        for (var entry : groups.entrySet()) {
            section++;
            String title = entry.getKey().isEmpty()
                    ? (groups.size() == 1 ? "Test cases" : "Cases outside a group")
                    : entry.getValue().get(0).groupTitle;
            html.append("  <section class=\"grp\">\n");
            html.append("  <h2 class=\"section\">").append(section).append(". ")
                    .append(escape(title)).append("</h2>\n");
            html.append("  <div class=\"tbl-scroll\">\n  <table class=\"cases\">\n");
            html.append("    <thead><tr><th>ID</th><th>Test case</th><th>Pre-condition</th>")
                    .append("<th>Test steps</th>")
                    .append("<th>Test data</th><th>Expected result</th>")
                    .append("<th>Actual result</th><th>Status</th><th>Remark / cause</th>")
                    .append("<th>Evidence</th></tr></thead>\n    <tbody>\n");
            for (Spec spec : entry.getValue()) {
                html.append(row(spec, outcomes.get(spec.key()), shots.get(spec.key()),
                        logs.get(spec.key()), sheet.get(spec.id)));
            }
            html.append("    </tbody>\n  </table>\n  </div>\n  </section>\n");
        }

        html.append(legend());
        html.append("  <footer>Generated by <code>org.sengmean.report.HtmlReportGenerator</code> from the test "
                + "sources, the run's results, its step logs and its screenshots. Rewritten at the end "
                + "of every run by <code>HtmlReportListener</code>."
                + "</footer>\n");
        html.append(lightbox());
        html.append(filterScript());
        html.append(summaryScript());
        html.append("</div>\n</body>\n</html>\n");
        return html.toString();
    }

    /**
     * The conditions the run happened under.
     *
     * <p>It used to be three lines - target, account, headless - which is not enough for anyone
     * to repeat a run they did not watch. The wallet is here because the cashier pages render a
     * different set of controls per currency and it is the usual explanation for a suite full of
     * skips; the JDK and the machine because a result that only appears on one laptop is the
     * hardest kind to chase; the duration because a suite that took four times as long as usual
     * is a finding even when every case is green.
     */
    private static String metaGrid(Map<String, String> environment, String runStamp, String scope) {
        StringBuilder meta = new StringBuilder("    <div class=\"meta\">\n");
        meta.append(metaItem("Target", environment.getOrDefault("base.url", "not recorded")));
        meta.append(metaItem("Account", environment.getOrDefault("test.username", "not recorded")));
        meta.append(metaItem("Run", runStamp));
        if (environment.containsKey("run.duration")) {
            meta.append(metaItem("Elapsed", environment.get("run.duration")));
        }
        meta.append(metaItem("Scope", scope + " executed"));
        String headless = environment.get("headless");
        meta.append(metaItem("Browser", "Chromium via Playwright 1.58.0"
                + (headless == null ? "" : Boolean.parseBoolean(headless) ? " (headless)" : " (headed)")));
        meta.append(metaItem("Runner", "JUnit 5"
                + (environment.containsKey("java.version") ? " on Java " + environment.get("java.version") : "")));
        if (environment.containsKey("os.name")) {
            meta.append(metaItem("Machine", environment.get("os.name").trim()));
        }
        return meta.append("    </div>\n").toString();
    }

    private static String metaItem(String label, String value) {
        return "      <div><b>" + escape(label) + ":</b> " + escape(value) + "</div>\n";
    }

    /**
     * The pass/fail proportions as one bar.
     *
     * <p>A tally line of five numbers has to be read and compared before it means anything. The
     * bar is the same five numbers arranged so the shape of the run is legible before any of them
     * is read, which is what a summary is for. It carries the counts as tooltips rather than
     * replacing the tally: a bar alone cannot be quoted in a status update.
     */
    private static String bar(int pass, int fail, int skip, int notRun) {
        int total = pass + fail + skip + notRun;
        if (total == 0) {
            return "";
        }
        StringBuilder bar = new StringBuilder("    <div class=\"bar\" role=\"img\" aria-label=\"")
                .append(pass).append(" passed, ").append(fail).append(" failed, ")
                .append(skip).append(" skipped, ").append(notRun).append(" not run\">\n");
        bar.append(segment("pass", pass, total, "passed"));
        bar.append(segment("fail", fail, total, "failed"));
        bar.append(segment("skip", skip, total, "skipped"));
        bar.append(segment("notrun", notRun, total, "not run"));
        bar.append("    </div>\n");
        bar.append("    <div class=\"rate\">")
                .append(String.format(Locale.ROOT, "%.0f%% of the cases that ran passed",
                        executed(pass, fail, skip) == 0 ? 0.0 : 100.0 * pass / executed(pass, fail, skip)))
                .append("</div>\n");
        return bar.toString();
    }

    /**
     * The denominator for a pass rate: cases that produced a result.
     *
     * <p>Not-run cases are deliberately outside it. A safe run excludes every {@code creates-data}
     * case, so counting them as failures-in-waiting would report a suite that did exactly what it
     * was asked as though it had lost a third of its coverage - the number would say more about
     * the exclusions than about the application.
     */
    private static int executed(int pass, int fail, int skip) {
        return pass + fail + skip;
    }

    private static String segment(String css, int count, int total, String label) {
        if (count == 0) {
            return "";
        }
        return "      <span class=\"seg " + css + "\" style=\"width:"
                + String.format(Locale.ROOT, "%.4f", 100.0 * count / total) + "%\" title=\""
                + count + " " + label + "\"></span>\n";
    }

    /**
     * The red cases, listed once at the top with a link to each row.
     *
     * <p>Without this a reader looking for what went wrong scrolls the whole page hunting for red
     * badges, which on the larger suites means passing forty green rows to find three. The list
     * is the report answering its own first question. It is omitted entirely when nothing failed,
     * rather than rendered empty - a "Findings: none" heading is a thing to read and dismiss on
     * every green run.
     */
    private static String findings(List<Spec> failed, Map<String, Outcome> outcomes) {
        if (failed.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("  <div class=\"callout bad\">\n");
        out.append("    <h2>").append(failed.size())
                .append(failed.size() == 1 ? " case needs attention" : " cases need attention")
                .append("</h2>\n    <ol class=\"findings\">\n");
        for (Spec spec : failed) {
            Outcome outcome = outcomes.get(spec.key());
            boolean known = spec.tags.contains("known-defect");
            out.append("      <li><a href=\"#").append(anchor(spec)).append("\">")
                    .append(spec.id.isEmpty() ? escape(spec.method) : escape(spec.id))
                    .append("</a> ").append(escape(spec.expected));
            if (known) {
                out.append(" <span class=\"tag known\">known defect</span>");
            }
            String headline = headline(outcome);
            if (!headline.isEmpty()) {
                out.append("<div class=\"why\">").append(escape(trim(headline, 220))).append("</div>");
            }
            out.append("</li>\n");
        }
        out.append("    </ol>\n");
        out.append("    <p>A case tagged <code>known-defect</code> is red on purpose: it asserts the "
                + "behaviour the application <i>should</i> have and stays red until the finding is "
                + "fixed. The rest are this run's news.</p>\n");
        out.append("  </div>\n");
        return out.toString();
    }

    /**
     * The controls above the tables: filter by status, and search.
     *
     * <p>The suites run to fifty-odd cases, and the question a reader arrives with is almost
     * always narrower than "show me everything" - the three that failed, the case with a
     * particular id, everything that was skipped. Plain inline script and no dependencies, for
     * the same reason the lightbox has none: these pages are opened straight off disk and read on
     * machines that have never built the project.
     */
    private static String toolbar(int total) {
        return """
                  <div class="toolbar">
                    <div class="filters" role="group" aria-label="Filter cases by status">
                      <button type="button" class="flt on" data-filter="all">All</button>
                      <button type="button" class="flt" data-filter="Pass">Passed</button>
                      <button type="button" class="flt" data-filter="Fail">Failed</button>
                      <button type="button" class="flt" data-filter="Skipped">Skipped</button>
                      <button type="button" class="flt" data-filter="Not run">Not run</button>
                    </div>
                    <input type="search" id="q" placeholder="Search id, case or message" aria-label="Search cases">
                    <button type="button" class="flt" id="expand-all">Expand all details</button>
                    <span class="count" id="shown">__TOTAL__ of __TOTAL__ cases</span>
                  </div>
                """.replace("__TOTAL__", String.valueOf(total));
    }

    private static String row(Spec spec, Outcome outcome, List<String> shots,
                              List<CaseLog.Entry> steps, CaseSheetSteps.Entry planned) {
        String status = status(outcome);
        StringBuilder row = new StringBuilder();
        row.append("      <tr class=\"case\" id=\"").append(anchor(spec))
                .append("\" data-status=\"").append(escape(status))
                .append("\" data-search=\"").append(escape(searchText(spec, outcome))).append("\">\n");
        // The case id doubles as a fragment anchor, so a row can be linked to by the id a
        // reader already knows - "#HOME-002" - rather than by the method name behind it. That
        // is what the client test case sheet's per-row links are built on: its own column A
        // holds TC_HOME_002, and the link is the report URL plus the id with the wrapper
        // stripped. Linking on the method name instead would break the moment a method was
        // renamed, which is a refactor nobody would think to check a spreadsheet for.
        row.append("        <td class=\"id\"");
        if (!spec.id.isEmpty()) {
            row.append(" id=\"").append(escape(spec.id)).append("\"");
        }
        row.append(">")
                .append(spec.id.isEmpty() ? "&mdash;" : escape(spec.id)).append("</td>\n");
        row.append("        <td class=\"tc\"><button type=\"button\" class=\"more\" "
                        + "aria-expanded=\"false\">Details</button><code>")
                .append(escape(spec.method)).append("</code>")
                .append(tagBadges(spec)).append("</td>\n");
        CaseSheetSteps.Described described = CaseSheetSteps.describe(planned, steps);
        row.append("        <td class=\"tpre\">").append(preconditionHtml(described)).append("</td>\n");
        row.append("        <td class=\"tsteps\">").append(stepsHtml(described)).append("</td>\n");
        row.append("        <td class=\"tdata\">").append(dataHtml(described)).append("</td>\n");
        row.append("        <td>").append(escape(spec.expected)).append("</td>\n");
        row.append("        <td>").append(actual(outcome, status, steps)).append("</td>\n");
        row.append("        <td><span class=\"st ").append(cssStatus(status)).append("\">")
                .append(escape(status)).append("</span>");
        if (outcome != null && outcome.seconds() > 0) {
            row.append("<div class=\"secs\">")
                    .append(String.format(Locale.ROOT, "%.2fs", outcome.seconds())).append("</div>");
        }
        row.append("</td>\n");
        row.append("        <td>").append(remark(spec, outcome, status)).append("</td>\n");
        row.append("        <td>")
                .append(evidence(shots, status, caseLabel(spec), steps))
                .append("</td>\n");
        row.append("      </tr>\n");
        row.append(detailRow(spec, outcome, status, steps, shots, planned));
        return row.toString();
    }

    /** The Pre-condition cell: the workbook's PRE-CONDITION, in its own words. */
    static String preconditionHtml(CaseSheetSteps.Described described) {
        return described.precondition().isBlank()
                ? "<span class=\"muted\">&mdash;</span>"
                : "<div class=\"tc-pre\">" + escape(described.precondition()) + "</div>";
    }

    /**
     * The Test steps cell, from {@link CaseSheetSteps#describe}: the client workbook's numbered
     * TEST STEPS, or the run's own STEP lines - marked as such - for a case with no workbook row.
     * The full trail, API calls and all, stays in the Details panel.
     */
    static String stepsHtml(CaseSheetSteps.Described described) {
        if (described.steps().isEmpty()) {
            return "<span class=\"muted\">&mdash;</span>";
        }
        StringBuilder out = new StringBuilder("<ol class=\"tc-steps\">");
        for (String item : described.steps()) {
            out.append("<li>").append(escape(item)).append("</li>");
        }
        out.append("</ol>");
        if (described.stepsFromRun()) {
            out.append("<div class=\"src\">From the run's step trail</div>");
        }
        return out.toString();
    }

    /**
     * The Test data cell: the workbook's TEST DATA, then the values the run actually used - the
     * DATA lines of its trail, such as the account it signed in with. The two are kept apart
     * because they answer different questions: what the sheet asks for, and what this run fed in.
     */
    static String dataHtml(CaseSheetSteps.Described described) {
        if (described.data().isEmpty() && described.usedData().isEmpty()) {
            return "<span class=\"muted\">&mdash;</span>";
        }
        StringBuilder out = new StringBuilder();
        if (!described.data().isEmpty()) {
            out.append("<ul class=\"tc-data\">");
            for (String item : described.data()) {
                out.append("<li>").append(escape(item)).append("</li>");
            }
            out.append("</ul>");
        }
        if (!described.usedData().isEmpty()) {
            out.append("<div class=\"src\">Used in this run</div><ul class=\"tc-data used\">");
            for (String item : described.usedData()) {
                out.append("<li><code>").append(escape(item)).append("</code></li>");
            }
            out.append("</ul>");
        }
        return out.toString();
    }

    /** A stable, readable fragment id, so the findings list can link straight at a row. */
    private static String anchor(Spec spec) {
        return "case-" + spec.suite + "-" + spec.method;
    }

    /** Lower-cased haystack for the search box: id, method, title, tags and the failure message. */
    private static String searchText(Spec spec, Outcome outcome) {
        StringBuilder text = new StringBuilder(spec.id).append(' ')
                .append(spec.method).append(' ')
                .append(spec.expected).append(' ')
                .append(String.join(" ", spec.tags));
        if (outcome != null) {
            text.append(' ').append(blankTo(outcome.message(), ""));
        }
        return text.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * The panel under each row: how the case was carried out, and what came back.
     *
     * <h2>Why the table alone was not enough</h2>
     * Seven columns is already as wide as a report can usefully be, so everything that would
     * not fit was being cut to a sentence - a green case said "The application behaved as
     * expected", a red one showed 400 characters of an exception. Neither is checkable. This
     * panel is where the rest goes, folded away so the table still reads at a glance and the
     * reader who wants the working can have all of it.
     *
     * <h2>Passes get a panel too, and that is the point</h2>
     * A QA report that details its failures and asserts its passes is only half a report: a
     * green row is a claim, and a reader is entitled to the evidence behind it. The trail shows
     * where the browser went, what the site's own API answered, and what the case checked - so
     * "Pass" becomes something that can be audited rather than believed.
     */
    private static String detailRow(Spec spec, Outcome outcome, String status,
                                    List<CaseLog.Entry> steps, List<String> shots,
                                    CaseSheetSteps.Entry planned) {
        StringBuilder panel = new StringBuilder();
        panel.append("      <tr class=\"detail hide\" data-status=\"").append(escape(status))
                .append("\">\n        <td colspan=\"10\">\n");
        panel.append("          <div class=\"panels\">\n");
        panel.append(objectivePanel(spec, outcome, status, planned));
        panel.append(resultPanel(spec, outcome, status));
        panel.append("          </div>\n");
        panel.append(stepsPanel(steps, shots));
        panel.append("        </td>\n      </tr>\n");
        return panel.toString();
    }

    /** What the case was for, in full - the table cell shows the same text clipped to its column. */
    private static String objectivePanel(Spec spec, Outcome outcome, String status,
                                         CaseSheetSteps.Entry planned) {
        StringBuilder out = new StringBuilder("            <div class=\"panel\">\n");
        out.append("              <h3>The case</h3>\n              <dl>\n");
        out.append(field("Case id", spec.id.isEmpty() ? "not numbered" : spec.id));
        out.append(field("Method", spec.suite + "#" + spec.method));
        if (!spec.group.isEmpty()) {
            out.append(field("Group", spec.groupTitle + " (" + spec.group + ")"));
        }
        if (planned != null && !planned.precondition().isBlank()) {
            out.append(field("Pre-condition", planned.precondition()));
        }
        out.append(field("Expected result", spec.expected));
        out.append(field("Tags", spec.tags.isEmpty() ? "none" : String.join(", ", spec.tags)));
        if (spec.disabledReason != null) {
            out.append(field("Disabled because", blankTo(spec.disabledReason, "no reason given")));
        }
        out.append(field("Outcome", status
                + (outcome == null ? "" : String.format(Locale.ROOT, " in %.2fs", outcome.seconds()))));
        out.append("              </dl>\n            </div>\n");
        return out.toString();
    }

    /**
     * What came back: the diagnosis for a red case, the confirmation for a green one.
     *
     * <p>The failure half is broken out into named fields rather than left as a wall of stack
     * trace, because the four things a reader needs - which exception, what was expected against
     * what arrived, which locator, and which line of this project - are each one line, and are
     * each buried at a different depth of the trace. The trace itself is kept, folded, because
     * the parsing above is a convenience and must never be the only copy of the evidence.
     */
    private static String resultPanel(Spec spec, Outcome outcome, String status) {
        StringBuilder out = new StringBuilder("            <div class=\"panel\">\n");
        if (outcome == null) {
            out.append("              <h3>The result</h3>\n");
            out.append("              <p class=\"muted\">This case produced no result in this run, so "
                    + "there is nothing to report about it. It was either filtered out - normally by "
                    + "<code>-DexcludedGroups=creates-data</code> - or its suite was not part of the "
                    + "run at all.</p>\n            </div>\n");
            return out.toString();
        }
        if (PASS.equals(status)) {
            out.append("              <h3>The result</h3>\n              <dl>\n");
            out.append(field("Verdict", "Every assertion in the case held."));
            out.append(field("Verified", spec.expected));
            out.append(field("Duration", String.format(Locale.ROOT, "%.2f seconds", outcome.seconds())));
            out.append("              </dl>\n");
            out.append("              <p class=\"muted\">The steps below are the run's own record of "
                    + "how the case reached that verdict, and the Evidence column shows the page it "
                    + "finished on.</p>\n            </div>\n");
            return out.toString();
        }
        if (SKIPPED.equals(status)) {
            out.append("              <h3>Why it was skipped</h3>\n              <dl>\n");
            out.append(field("Reason", blankTo(outcome.message(),
                    "No reason was recorded, which normally means the class carries @Disabled.")));
            out.append("              </dl>\n");
            out.append("              <p class=\"muted\">A skip is a statement about the run, not about "
                    + "the application: an assumption the case needs did not hold, so it stopped rather "
                    + "than report a defect it could not have proved.</p>\n            </div>\n");
            return out.toString();
        }

        out.append("              <h3>The failure</h3>\n              <dl>\n");
        String type = errorType(outcome);
        if (type != null) {
            out.append(field("Exception", type));
        }
        out.append(field("Message", blankTo(outcome.message(), firstLine(outcome.detail()))));
        String[] comparison = expectedAndActual(outcome);
        if (comparison != null) {
            out.append(fieldRaw("Asserted", "<div class=\"cmp\"><div><span>expected</span><code>"
                    + escape(trim(comparison[0], 400)) + "</code></div>"
                    + "<div><span>actual</span><code>" + escape(trim(comparison[1], 400))
                    + "</code></div></div>"));
        }
        String locator = locatorOf(outcome);
        if (locator != null) {
            out.append(fieldRaw("Element", "<code>" + escape(trim(locator, 300)) + "</code>"));
        }
        List<String> frames = projectFrames(outcome.detail());
        if (!frames.isEmpty()) {
            StringBuilder where = new StringBuilder("<div class=\"frames\">");
            for (String frame : frames) {
                where.append("<code>").append(escape(frame)).append("</code>");
            }
            out.append(fieldRaw("Where in this project", where.append("</div>").toString()));
        }
        String reading = cause(outcome);
        if (reading != null) {
            out.append(field("Reading", reading));
        }
        if (spec.tags.contains("known-defect")) {
            out.append(field("Expected to be red", "This case is tagged known-defect: it asserts the "
                    + "behaviour the application should have, and stays red until the finding is fixed."));
        }
        out.append("              </dl>\n");
        if (outcome.detail() != null && !outcome.detail().isBlank()) {
            out.append("              <details class=\"trace\"><summary>Full stack trace</summary>")
                    .append("<pre>").append(escape(trim(outcome.detail(), 12000))).append("</pre></details>\n");
        }
        out.append("            </div>\n");
        return out.toString();
    }

    private static String field(String label, String value) {
        return fieldRaw(label, escape(value));
    }

    private static String fieldRaw(String label, String html) {
        return "                <dt>" + escape(label) + "</dt><dd>" + html + "</dd>\n";
    }

    /**
     * The run's own account of what the browser did, as the case did it.
     *
     * <p>Kinds are shown as badges rather than folded into the text because they are what makes
     * the trail scannable: the reader chasing a failure looks for the red <code>NETWORK</code>
     * and <code>CONSOLE</code> lines and reads outward from them, and the reader auditing a pass
     * looks for the <code>API</code> line where the site agreed with the assertion.
     */
    private static String stepsPanel(List<CaseLog.Entry> steps, List<String> shots) {
        if (steps == null || steps.isEmpty()) {
            return "          <div class=\"panel wide\"><h3>What the run did</h3>"
                    + "<p class=\"muted\">No step trail was recorded for this case. Trails are written "
                    + "while a case runs, so one is missing whenever the case did not run in the run "
                    + "this report describes.</p></div>\n";
        }
        StringBuilder out = new StringBuilder("          <div class=\"panel wide\">\n");
        out.append("            <h3>What the run did <span class=\"muted\">(")
                .append(steps.size()).append(" recorded events)</span></h3>\n");
        out.append("            <ol class=\"steps\">\n");

        // Each EVIDENCE line is the n-th capture of the case, in the order both were produced, so
        // the picture can be shown against the line that describes it rather than only in the
        // Evidence column.
        //
        // Deliberately only for a case that took MORE THAN ONE capture. Almost every case in the
        // project takes exactly one - the closing screenshot - and that one is already shown in the
        // Evidence column; repeating it inside the trail would change the look of all 236 rows to
        // solve a problem none of them has. The problem is specific to a case that photographs many
        // states, such as the walk over 52 games, where the Evidence column collapses into a fold
        // of near-identical thumbnails and nothing says which game each belongs to.
        boolean pairable = shots != null && shots.size() > 1
                && shotCaptions(steps, shots.size()).size() == shots.size();
        int shotIndex = 0;

        for (CaseLog.Entry entry : steps) {
            String inlineShot = "";
            if (pairable && CaseLog.EVIDENCE.equals(entry.kind()) && shotIndex < shots.size()) {
                String shot = shots.get(shotIndex++);
                inlineShot = "<a class=\"step-shot\" href=\"" + escape(shot)
                        + "\" target=\"_blank\" rel=\"noopener\"><img src=\"" + escape(shot)
                        + "\" alt=\"" + escape(trim(entry.text(), 200)) + "\" loading=\"lazy\"></a>";
            }
            // A line the case itself marked FAIL is highlighted, whatever kind it is. The walk over
            // 52 games writes one CHECK and one EVIDENCE line per game, and a reader scanning 104
            // near-identical lines for the handful that went wrong should not have to read every
            // one. NETWORK and CONSOLE already colour themselves; this does the same for a failure
            // the test declared rather than one the browser reported.
            String failed = trim(entry.text(), 500).stripLeading().startsWith("FAIL") ? " failed" : "";
            out.append("              <li class=\"k-").append(entry.kind().toLowerCase(Locale.ROOT))
                    .append(failed)
                    .append("\"><span class=\"at\">")
                    .append(String.format(Locale.ROOT, "%.2fs", entry.millis() / 1000.0))
                    .append("</span><span class=\"kind\">").append(escape(entry.kind()))
                    .append("</span><span class=\"what\">").append(escape(trim(entry.text(), 500)))
                    .append("</span>").append(inlineShot).append("</li>\n");
        }
        out.append("            </ol>\n          </div>\n");
        return out.toString();
    }

    /** {@code org.opentest4j.AssertionFailedError}, read off the head of the stack trace. */
    private static String errorType(Outcome outcome) {
        String first = firstLine(outcome.detail());
        if (first.isBlank()) {
            return null;
        }
        int colon = first.indexOf(':');
        String head = (colon < 0 ? first : first.substring(0, colon)).trim();
        // A stack trace opens with the exception's own name; anything else here is a message
        // that happened to be first, and guessing at it would put nonsense in a named field.
        return head.matches("[\\w.$]+(Exception|Error|Throwable)") ? head : null;
    }

    /**
     * The two halves of a failed comparison, pulled out of whatever wrote it.
     *
     * <p>Three notations turn up in this project's runs and they do not agree with one another:
     * JUnit's own {@code expected: <x> but was: <y>}, Playwright's assertion messages, which
     * print {@code Expected} and {@code Received} on their own lines, and the plain
     * {@code assertTrue} case, which produces no comparison at all. Reporting the first two as a
     * pair of named values is the difference between a reader seeing the discrepancy and a
     * reader parsing a sentence to find it.
     *
     * @return expected and actual, or null when the failure carried no comparison
     */
    private static String[] expectedAndActual(Outcome outcome) {
        String text = blankTo(outcome.message(), "") + "\n" + blankTo(outcome.detail(), "");
        var junit = Pattern.compile("expected:\\s*<(.*?)>\\s*but was:\\s*<(.*?)>", Pattern.DOTALL)
                .matcher(text);
        if (junit.find()) {
            return new String[]{junit.group(1), junit.group(2)};
        }
        var playwright = Pattern.compile(
                        "Expected(?:\\s+\\w+)?:\\s*(.*?)\\R\\s*(?:Received|Actual)(?:\\s+\\w+)?:\\s*(.*?)\\R",
                        Pattern.DOTALL)
                .matcher(text);
        if (playwright.find()) {
            return new String[]{playwright.group(1).trim(), playwright.group(2).trim()};
        }
        return null;
    }

    /**
     * The element the case was waiting on when it gave up.
     *
     * <p>The single most useful line in a Playwright timeout, and the one buried deepest: the
     * message names the locator it could not resolve, which says at once whether the control
     * moved, the page never arrived, or the selector was wrong - three findings that read
     * identically as "Timed out" without it.
     */
    private static String locatorOf(Outcome outcome) {
        String text = blankTo(outcome.message(), "") + "\n" + blankTo(outcome.detail(), "");
        var waiting = Pattern.compile("waiting for (locator\\(.*?\\)|get\\w+\\(.*?\\))").matcher(text);
        if (waiting.find()) {
            return waiting.group(1);
        }
        var call = Pattern.compile("(?:Locator|locator)\\((\"[^\"]{1,240}\"|'[^']{1,240}')\\)").matcher(text);
        if (call.find()) {
            return "locator(" + call.group(1) + ")";
        }
        return null;
    }

    /**
     * Every frame of the trace that belongs to this project, nearest first.
     *
     * <p>The old report showed one, which is enough to find the assertion and not enough to
     * explain it: a case that fails inside a page object needs the page object frame <em>and</em>
     * the line of the test that called it. Framework frames are left out - nobody debugging this
     * suite starts inside JUnit's reflection machinery - and the list stops at six so a deep
     * trace cannot swamp the panel that is meant to summarise it.
     */
    private static List<String> projectFrames(String detail) {
        List<String> frames = new ArrayList<>();
        if (detail == null) {
            return frames;
        }
        for (String line : detail.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at org.sengmean.")) {
                frames.add(trimmed.substring(3));
                if (frames.size() == 6) {
                    break;
                }
            }
        }
        return frames;
    }

    /** The one line worth quoting about a failure, for the findings list at the top of a page. */
    private static String headline(Outcome outcome) {
        if (outcome == null) {
            return "";
        }
        String message = blankTo(outcome.message(), firstLine(outcome.detail()));
        return firstLine(message);
    }

    private static String status(Outcome outcome) {
        return outcome == null ? NOT_RUN : outcome.status();
    }

    private static String cssStatus(String status) {
        return switch (status) {
            case PASS -> "pass";
            case FAIL, ERROR -> "fail";
            case SKIPPED -> "partial";
            default -> "info";
        };
    }

    /**
     * The Actual result cell.
     *
     * <p>A green case used to say only "The application behaved as expected", which restates the
     * Status column and is the one cell in the table with no information in it. It now says what
     * the run has to show for the claim - how many events were recorded, and where the browser
     * finished - so the reader can tell a case that drove the application from one that passed
     * without touching it. Both readings have happened here: several cases were once green while
     * asserting nothing at all.
     */
    private static String actual(Outcome outcome, String status, List<CaseLog.Entry> steps) {
        if (outcome == null) {
            return "<span class=\"muted\">Not executed in this run.</span>";
        }
        return switch (status) {
            case PASS -> "The application behaved as expected."
                    + (steps == null || steps.isEmpty() ? ""
                    : "<div class=\"secs\">" + steps.size() + " events recorded"
                    + (endedOn(steps) == null ? "" : ", ended on <code>"
                    + escape(trim(endedOn(steps), 90)) + "</code>") + "</div>");
            // A skip with no message used to read "see the remark" beside a remark that said
            // nothing - the cell pointed at an empty cell. It now states the fact directly.
            case SKIPPED -> escape(blankTo(outcome.message(),
                    "The case stopped before asserting anything, and recorded no reason."));
            default -> "<div class=\"why\">"
                    + escape(trim(blankTo(outcome.message(), firstLine(outcome.detail())), 400))
                    + "</div>";
        };
    }

    /** The last address the trail recorded, which is where the case left the browser. */
    private static String endedOn(List<CaseLog.Entry> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            CaseLog.Entry entry = steps.get(i);
            if (entry.text().startsWith("Case ended on ")) {
                return entry.text().substring("Case ended on ".length());
            }
        }
        return null;
    }

    private static String remark(Spec spec, Outcome outcome, String status) {
        List<String> notes = new ArrayList<>();
        if (spec.tags.contains("known-defect")) {
            notes.add("<b>Known defect.</b> This case asserts the behaviour the application "
                    + "<i>should</i> have, so it stays red until the finding is fixed. It is a "
                    + "regression net, not a health check.");
        }
        if (spec.tags.contains("creates-data")) {
            notes.add("<b>Creates data.</b> Side effects reach outside the browser &mdash; a real "
                    + "account, a real SMS, a filed deposit, a withdrawal hold or a staked balance "
                    + "&mdash; so it is kept out of unattended runs and must be run attended.");
        }
        if (spec.disabledReason != null) {
            notes.add("<b>Disabled:</b> " + escape(blankTo(spec.disabledReason, "no reason given")));
        }
        if (SKIPPED.equals(status) && spec.disabledReason == null) {
            // A skip is a statement about the run, not the application, and a reader who does not
            // know that reads a yellow badge as a soft failure. The distinction is worth its line
            // here because the suites skip deliberately and often - a wallet without the currency
            // a case needs, an activation phone number that was not supplied.
            notes.add("<b>Skipped.</b> An assumption the case depends on did not hold, so it "
                    + "stopped rather than report a defect it could not have proved. Open "
                    + "<i>Details</i> for how far it got.");
        }
        if (NOT_RUN.equals(status)) {
            notes.add(spec.tags.isEmpty()
                    ? "Not part of this run &mdash; the suite was not executed, or the case was filtered out."
                    : "Filtered out of this run by <code>-DexcludedGroups</code>.");
        }
        if (FAIL.equals(status) || ERROR.equals(status)) {
            String cause = cause(outcome);
            if (cause != null) {
                notes.add("<b>Cause:</b> " + escape(cause));
            }
            String where = assertionSite(outcome.detail());
            if (where != null) {
                notes.add("<code>" + escape(where) + "</code>");
            }
        }
        return notes.isEmpty() ? "<span class=\"muted\">&mdash;</span>" : String.join("<br>", notes);
    }

    /**
     * A short reading of why a case went red. These are the failure classes that actually recur
     * in this project's runs, and naming one saves a reader opening the stack trace only to find
     * out whether they are looking at a defect or at a slow environment.
     */
    private static String cause(Outcome outcome) {
        String all = outcome.message() + " " + outcome.detail();
        if (all.contains("TimeoutError") || all.contains("Timeout ") || all.contains("exceeded")) {
            return "Timed out waiting for an element or a navigation - either the control moved "
                    + "or the environment did not respond in time.";
        }
        if (all.contains("strict mode violation")) {
            return "The locator matched more than one element; Playwright's strict mode refuses "
                    + "an ambiguous match.";
        }
        if (all.contains("TestAbortedException") || all.contains("Assumption")) {
            return "An assumption did not hold, so the case stopped rather than reporting a defect "
                    + "it could not have proved.";
        }
        if (all.contains("AssertionFailedError") || all.contains("expected:")) {
            return "The application produced a different result from the one the case asserts.";
        }
        if (all.contains("NullPointerException")) {
            return "A page object returned nothing where the case expected a value.";
        }
        return null;
    }

    /** The first project frame in the stack trace, which is where a reader should start. */
    private static String assertionSite(String detail) {
        if (detail == null) {
            return null;
        }
        for (String line : detail.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at org.sengmean.tests.")
                    || trimmed.startsWith("at org.sengmean.page.")) {
                return trimmed.substring(3);
            }
        }
        return null;
    }

    /** "DEP-02 - one valid deposit is accepted on each of the five channels", for a caption. */
    private static String caseLabel(Spec spec) {
        String id = spec.id == null ? "" : spec.id.trim();
        String text = spec.expected == null ? "" : spec.expected.trim();
        if (id.isEmpty()) {
            return text;
        }
        return text.isEmpty() ? id : id + " - " + text;
    }

    /**
     * The captions for a case's screenshots, taken from its own EVIDENCE lines where they line up.
     *
     * <h2>Why the case name is not a good enough caption</h2>
     * A case that photographs one page can be captioned with its title. A case that photographs
     * <b>fifty-two</b> - one per game in the transfer walk - cannot: every image then reads
     * "HKDTRANSFER-0011: 1 from the main wallet into every game offered (7 of 52)", and a reader
     * looking at an enlarged capture has to count to the seventh line of the trail to learn which
     * game it is. The trail already holds the answer, because each capture logs an EVIDENCE line
     * naming the game and whether it passed.
     *
     * <p>Paired by position, which is safe because both sequences come from the same run in the
     * same order: {@code captureNow} writes its file and its EVIDENCE line together, and the
     * closing capture logs one too. If the counts ever disagree - an image that failed to write, a
     * log that was capped - this returns nothing and the caller falls back to the case name rather
     * than captioning pictures with the wrong labels.
     */
    private static List<String> shotCaptions(List<CaseLog.Entry> trail, int shotCount) {
        if (trail == null || shotCount == 0) {
            return List.of();
        }
        List<String> captions = new ArrayList<>();
        for (CaseLog.Entry entry : trail) {
            if (CaseLog.EVIDENCE.equals(entry.kind())) {
                captions.add(entry.text());
            }
        }
        return captions.size() == shotCount ? captions : List.of();
    }

    private static String evidence(List<String> shots, String status, String label,
                                   List<CaseLog.Entry> trail) {
        if (shots == null || shots.isEmpty()) {
            return NOT_RUN.equals(status) || PASS.equals(status) || SKIPPED.equals(status)
                    ? "<span class=\"muted\">&mdash;</span>"
                    : "<span class=\"muted\">No screenshot captured.</span>";
        }
        StringBuilder cell = new StringBuilder();
        // A row holding several captures is folded away by default, so one case with a handful
        // of tabs cannot push every other row off the screen. The summary says how many there
        // are, and the row stays one line until someone wants to look. A single capture is left
        // showing: hiding it behind a click would be worse than the problem.
        boolean many = shots.size() > 1;
        if (many) {
            cell.append("<details class=\"shots\"><summary>")
                    .append(shots.size()).append(" captures</summary>");
        }
        List<String> captions = shotCaptions(trail, shots.size());
        int index = 0;
        for (String shot : shots) {
            // The caption names the case rather than describing what a screenshot is. Every
            // image used to carry the same sentence, which told a reader looking at an enlarged
            // capture nothing about which row it came from. Where a case left more than one
            // page open - a gateway tab beside the deposit page - they are numbered.
            String position = "  (" + (++index) + " of " + shots.size() + ")";
            String caption = captions.isEmpty()
                    ? (shots.size() > 1 ? label + position : label)
                    : captions.get(index - 1) + position;
            cell.append("<a class=\"shot\" href=\"").append(escape(shot))
                    .append("\" target=\"_blank\" rel=\"noopener\"><img src=\"").append(escape(shot))
                    .append("\" alt=\"").append(escape(caption))
                    .append("\" loading=\"lazy\"></a>");
        }
        if (many) {
            cell.append("</details>");
        }
        return cell.toString();
    }

    private static String tagBadges(Spec spec) {
        StringBuilder badges = new StringBuilder();
        for (String tag : spec.tags) {
            badges.append("<div class=\"tag\">").append(escape(tag)).append("</div>");
        }
        return badges.toString();
    }

    private static String renderIndex(List<SuiteReport> suites,
                                      Map<String, Outcome> outcomes,
                                      Map<String, String> environment,
                                      String runStamp,
                                      String scope) {
        int allPass = 0;
        int allFail = 0;
        int allSkip = 0;
        int allNotRun = 0;
        int allCases = 0;
        double allSeconds = 0;
        // Rows and tallies per package area, alphabetically, so the index reads as one section per
        // area of the back office (auth, product, rebateprogram, tool) rather than one long list.
        Map<String, StringBuilder> rowsByArea = new java.util.TreeMap<>();
        Map<String, int[]> tallyByArea = new HashMap<>();      // suites, cases, pass, fail, skip, notRun
        Map<String, Double> secondsByArea = new HashMap<>();
        // The attention list is grouped by the same package areas, so a long run's findings read
        // area by area rather than as one mixed list.
        Map<String, StringBuilder> crossByArea = new java.util.TreeMap<>();
        int findings = 0;
        for (SuiteReport suite : suites) {
            // A suite this run did not execute keeps the tally from the run that did, so the index
            // agrees with the page it links to. Its findings are not re-listed in the cross-suite
            // roll-up: that section is built from this run's outcomes; the suite's own page still
            // lists them, under the date they are from.
            boolean ranThisTime = executed(suite, outcomes);
            SuiteState remembered = ranThisTime ? null : loadState(outDir(), suite);
            SuiteState state = remembered != null ? remembered
                    : summarise(suite, ranThisTime ? outcomes : Map.of(), ranThisTime ? runStamp : "");
            if (ranThisTime) {
                for (Spec spec : suite.specs) {
                    if (!"fail".equals(statusGroup(state.statusOf(spec)))) {
                        continue;
                    }
                    findings++;
                    // Linked through to the row on the suite's own page, so the roll-up is a way
                    // in rather than a second place the same detail has to be maintained.
                    StringBuilder crossSuite = crossByArea.computeIfAbsent(area(suite.packageName), k -> new StringBuilder());
                    crossSuite.append("      <li><a href=\"").append(escape(suite.fileName()))
                            .append("#").append(anchor(spec)).append("\">")
                            .append(spec.id.isEmpty() ? escape(spec.method) : escape(spec.id))
                            .append("</a> <span class=\"muted\">")
                            .append(escape(suite.title)).append("</span> &mdash; ")
                            .append(escape(spec.expected));
                    if (spec.tags.contains("known-defect")) {
                        crossSuite.append(" <span class=\"tag known\">known defect</span>");
                    }
                    crossSuite.append("</li>\n");
                }
            }
            allPass += state.pass();
            allFail += state.fail();
            allSkip += state.skip();
            allNotRun += state.notRun();
            allCases += suite.specs.size();
            allSeconds += state.seconds();
            String area = area(suite.packageName);
            rowsByArea.computeIfAbsent(area, k -> new StringBuilder()).append(indexRow(suite, state, ranThisTime));
            int[] tally = tallyByArea.computeIfAbsent(area, k -> new int[6]);
            tally[0]++;
            tally[1] += suite.specs.size();
            tally[2] += state.pass();
            tally[3] += state.fail();
            tally[4] += state.skip();
            tally[5] += state.notRun();
            secondsByArea.merge(area, state.seconds(), Double::sum);
        }

        StringBuilder html = new StringBuilder();
        html.append(head("Weibo Empire admin - test case reports"));
        html.append("<body>\n<div class=\"wrap\">\n");
        html.append("  <header class=\"rpt\">\n");
        html.append("    <h1>Weibo Empire admin &mdash; test case reports</h1>\n");
        html.append("    <div class=\"sub\">One report per suite, every case listed, every case's working "
                + "recorded</div>\n");
        html.append(metaGrid(environment, runStamp, scope));
        html.append("    <div class=\"tally\">").append(allCases).append(" cases &middot; ")
                .append(allPass).append(" passed &middot; ").append(allFail).append(" failed &middot; ")
                .append(allSkip).append(" skipped &middot; ").append(allNotRun).append(" not run &middot; ")
                .append(String.format(Locale.ROOT, "%.1fs of browser time", allSeconds))
                .append("</div>\n");
        html.append(bar(allPass, allFail, allSkip, allNotRun));
        html.append("  </header>\n\n");

        if (findings > 0) {
            html.append("  <div class=\"callout bad\">\n    <h2>")
                    .append(findings).append(findings == 1 ? " case needs attention"
                            : " cases need attention").append(" across the run</h2>\n")
                    .append(findingsByArea(crossByArea))
                    .append("    <p>Each links to its row on the suite's own page, where the failure is "
                            + "broken down and the run's record of what the browser did is kept.</p>\n")
                    .append("  </div>\n");
        }

        // One section per package area, each with its own tally and its suites beneath it.
        for (Map.Entry<String, StringBuilder> area : rowsByArea.entrySet()) {
            int[] t = tallyByArea.get(area.getKey());
            html.append("  <section class=\"pkg\" id=\"pkg-").append(escape(area.getKey())).append("\">\n");
            html.append("  <h2 class=\"section\">").append(escape(areaTitle(area.getKey())))
                    .append(" <span class=\"pkg-name\">").append(escape(area.getKey())).append("</span></h2>\n");
            html.append("  <div class=\"pkg-tally\">").append(miniBar(t[2], t[3], t[4], t[5])).append("<span>")
                    .append(t[0]).append(t[0] == 1 ? " suite" : " suites").append(" &middot; ")
                    .append(t[1]).append(" cases &middot; ").append(t[2]).append(" passed &middot; ")
                    .append(t[3]).append(" failed &middot; ").append(t[4]).append(" skipped &middot; ")
                    .append(t[5]).append(" not run &middot; ")
                    .append(String.format(Locale.ROOT, "%.1fs", secondsByArea.getOrDefault(area.getKey(), 0d)))
                    .append("</span></div>\n");
            html.append("  <div class=\"tbl-scroll\">\n  <table>\n");
            html.append("    <thead><tr><th>Suite</th><th>Result</th><th>Cases</th><th>Pass</th>")
                    .append("<th>Fail</th><th>Skip</th><th>Not run</th><th>Time</th><th>Verdict</th>")
                    .append("</tr></thead>\n");
            html.append("    <tbody>\n").append(area.getValue()).append("    </tbody>\n  </table>\n  </div>\n  </section>\n");
        }
        html.append(legend());
        html.append("  <footer>Generated by <code>org.sengmean.report.HtmlReportGenerator</code>, rewritten "
                + "at the end of every run by <code>HtmlReportListener</code>. "
                + "<code>mvn exec:java@html-report</code> rebuilds these pages by hand.</footer>\n");
        html.append(lightbox());
        html.append("</div>\n</body>\n</html>\n");
        return html.toString();
    }

    /** The attention list, one titled list per package area, each linking to the area's section below. */
    private static String findingsByArea(Map<String, StringBuilder> byArea) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, StringBuilder> area : byArea.entrySet()) {
            long count = area.getValue().toString().split("<li>", -1).length - 1;
            out.append("    <h3 class=\"pkg-findings\"><a href=\"#pkg-").append(escape(area.getKey())).append("\">")
                    .append(escape(areaTitle(area.getKey()))).append("</a> <span class=\"muted\">(")
                    .append(count).append(")</span></h3>\n")
                    .append("    <ol class=\"findings\">\n").append(area.getValue()).append("    </ol>\n");
        }
        return out.toString();
    }

    /**
     * The area of the back office a suite belongs to: the last segment of its package, so a class in
     * {@code org.sengmean.tests.product} and a section suite in {@code org.sengmean.suites.product}
     * both land under "product". A suite straight in {@code tests} or {@code suites} is "other".
     */
    static String area(String packageName) {
        String last = packageName == null ? "" : packageName.substring(packageName.lastIndexOf('.') + 1);
        return last.isBlank() || last.equals("tests") || last.equals("suites") ? "other" : last;
    }

    /** "rebateprogram" to "Rebate program", "auth" to "Auth" - the area's heading. */
    static String areaTitle(String area) {
        return switch (area) {
            case "rebateprogram" -> "Rebate program";
            case "auth" -> "Authentication";
            default -> area.isEmpty() ? area : Character.toUpperCase(area.charAt(0)) + area.substring(1);
        };
    }

    /** The same proportions as the header bar, sized for a table cell. */
    private static String miniBar(int pass, int fail, int skip, int notRun) {
        int total = pass + fail + skip + notRun;
        if (total == 0) {
            return "";
        }
        return "<span class=\"bar mini\">" + segment("pass", pass, total, "passed")
                + segment("fail", fail, total, "failed")
                + segment("skip", skip, total, "skipped")
                + segment("notrun", notRun, total, "not run") + "</span>";
    }

    private static String legend() {
        return """
                  <h2 class="section">How to read this report</h2>
                  <div class="callout">
                    <p><b>Status</b> is what the run did, not a verdict on the application. A
                    <span class="st fail">Fail</span> on a case badged <code>known-defect</code> is the
                    expected outcome: those cases assert the behaviour the application <i>should</i>
                    have and stay red until the finding is fixed.</p>
                    <p><b>Not run</b> means the case exists in the suite but produced no result, normally
                    because it was filtered out with <code>-DexcludedGroups=creates-data</code>, which
                    keeps cases with real-world side effects - a registered account, a real SMS, a filed
                    deposit, a withdrawal hold, a staked casino bet - out of an unattended run.</p>
                    <p><b>Evidence</b> is the screenshot of the page as the case left it - captured for every
                    case that ran, passed or failed, just before teardown closed the browser. A
                    skipped case never ran, so it has none. Click an image for the full size.</p>
                    <p><b>Details</b>, on every row, opens what would not fit in the table: the case as its
                    source declares it, the diagnosis of a failure - exception, expected against actual,
                    the element that could not be resolved, the lines of this project involved, and the
                    full stack trace - and, for every row alike, the run's own record of what the browser
                    did.</p>
                    <p><b>What the run did</b> is that record. It is captured automatically while the case
                    runs: <code>NAV</code> for a page load or a new tab, <code>API</code> for a call the
                    site made of its own back end with the status it came back with, <code>NETWORK</code>
                    for one that came back 4xx or 5xx, <code>CONSOLE</code> for a console or uncaught
                    error, <code>EVIDENCE</code> for a screenshot, and <code>STEP</code>,
                    <code>CHECK</code> and <code>DATA</code> for what the case itself narrated. A passing
                    row carries one too, deliberately: a green result is a claim, and this is the working
                    behind it.</p>
                  </div>
                """;
    }

    private static String head(String title) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>__TITLE__</title>
                <style>
                  :root{
                    --bg:#f4f6fa; --card:#ffffff; --ink:#1a2230; --muted:#5b6676;
                    --line:#e2e7ef; --head:#26314a; --headink:#ffffff;
                    --pass:#137a43; --pass-bg:#e6f5ec;
                    --fail:#b02525; --fail-bg:#fdecec;
                    --partial:#9a6a00; --partial-bg:#fdf3dd;
                    --info:#1f6a9c; --info-bg:#e6f1f9;
                    --accent:#c79a2f;                  }
                  *{box-sizing:border-box}
                  body{margin:0;background:var(--bg);color:var(--ink);
                    font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
                    line-height:1.5;padding:32px 18px}
                  .wrap{max-width:1320px;margin:0 auto}
                  header.rpt{background:var(--head);color:var(--headink);border-radius:14px;padding:26px 30px;
                    box-shadow:0 6px 20px rgba(20,30,50,.12)}
                  header.rpt h1{margin:0 0 6px;font-size:24px;letter-spacing:.3px}
                  header.rpt .sub{opacity:.85;font-size:14px}
                  header.rpt code{background:rgba(255,255,255,.14);color:inherit}
                  .meta{display:flex;flex-wrap:wrap;gap:10px 26px;margin-top:16px;font-size:13.5px}
                  .meta b{color:var(--accent)}
                  .tally{margin-top:14px;padding-top:12px;border-top:1px solid rgba(255,255,255,.18);
                    font-size:14px;font-weight:600}
                  /* The run at a glance. Proportional segments, so the shape of a result is
                     readable before any of the five numbers above it has been read. */
                  .bar{display:flex;height:10px;border-radius:999px;overflow:hidden;margin-top:12px;
                    background:rgba(255,255,255,.16)}
                  .bar .seg{display:block;height:100%}
                  .bar .seg.pass{background:#3ddc84}
                  .bar .seg.fail{background:#ff6b6b}
                  .bar .seg.skip{background:#ffcc66}
                  .bar .seg.notrun{background:#8fa3bf}
                  .rate{margin-top:8px;font-size:12.5px;opacity:.85}
                  /* The same bar in a table cell, where the ground is white rather than the
                     header's navy, so the empty track has to be redrawn to stay visible. */
                  .bar.mini{width:150px;height:8px;margin:0;background:var(--line)}
                  td.minibar{width:170px}
                  .toolbar{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin:22px 0 4px}
                  .toolbar .filters{display:flex;flex-wrap:wrap;gap:6px}
                  button.flt{font:inherit;font-size:12.5px;padding:6px 12px;border-radius:999px;
                    border:1px solid var(--line);background:var(--card);color:var(--muted);cursor:pointer}
                  button.flt:hover{color:var(--ink);border-color:var(--muted)}
                  button.flt.on{background:var(--head);border-color:var(--head);color:#fff}
                  .toolbar input[type=search]{font:inherit;font-size:13px;padding:7px 12px;flex:1 1 220px;
                    min-width:180px;border:1px solid var(--line);border-radius:999px;background:var(--card);
                    color:var(--ink)}
                  .toolbar .count{font-size:12.5px;color:var(--muted);margin-left:auto}
                  .hide{display:none !important}
                  .callout.bad{border-left-color:var(--fail)}
                  ol.findings{margin:0;padding-left:20px}
                  ol.findings li{margin-bottom:10px}
                  ol.findings a{color:var(--fail);font-family:ui-monospace,Menlo,Consolas,monospace;
                    font-weight:700;text-decoration:none}
                  ol.findings a:hover{text-decoration:underline}
                  .tag.known{background:var(--fail-bg);color:var(--fail)}
                  /* The detail panel under a row. It is a second <tr> rather than a nested table
                     so the seven columns stay aligned across every group on the page. */
                  button.more{font:inherit;font-size:11px;font-weight:600;padding:2px 8px;margin-right:7px;
                    border-radius:999px;border:1px solid var(--line);background:var(--card);
                    color:var(--muted);cursor:pointer;vertical-align:middle}
                  button.more:hover{color:var(--ink);border-color:var(--muted)}
                  button.more::before{content:"\\25B8 "}
                  tr.case.open button.more::before{content:"\\25BE "}
                  tr.case.open>td{background:#f2f6fd}
                  tr.case.flash>td{animation:flash 1.4s ease-out}
                  @keyframes flash{from{background:#fdf3dd}to{background:transparent}}
                  tr.detail>td{background:#f7f9fc;border-top:0;padding:0 13px 18px}
                  .panels{display:flex;flex-wrap:wrap;gap:14px}
                  .panel{flex:1 1 340px;background:var(--card);border:1px solid var(--line);
                    border-radius:10px;padding:14px 16px;min-width:0}
                  .panel.wide{flex-basis:100%;margin-top:14px}
                  .panel h3{margin:0 0 10px;font-size:13px;text-transform:uppercase;
                    letter-spacing:.6px;color:var(--muted)}
                  .panel dl{margin:0;display:grid;grid-template-columns:150px 1fr;gap:6px 14px;font-size:13px}
                  .panel dt{color:var(--muted);font-weight:600}
                  .panel dd{margin:0;word-break:break-word}
                  .panel p{font-size:13px;margin:10px 0 0}
                  .cmp{display:grid;gap:6px}
                  .cmp>div{display:flex;gap:8px;align-items:baseline}
                  .cmp span{font-size:10.5px;text-transform:uppercase;letter-spacing:.5px;
                    color:var(--muted);min-width:58px}
                  .cmp code{white-space:pre-wrap;word-break:break-word}
                  .cmp>div:first-child code{background:var(--pass-bg);color:var(--pass)}
                  .cmp>div:last-child code{background:var(--fail-bg);color:var(--fail)}
                  .frames{display:flex;flex-direction:column;gap:4px;align-items:flex-start}
                  details.trace{margin-top:12px;font-size:12.5px}
                  details.trace>summary{cursor:pointer;color:var(--muted)}
                  details.trace pre{margin:8px 0 0;padding:12px;background:#f4f6fa;border-radius:8px;
                    overflow-x:auto;font-size:11.5px;line-height:1.45;max-height:340px}
                  /* The step trail. Kinds are badges rather than prose because they are what makes
                     it scannable - red for the network and console lines a failure hangs off. */
                  ol.steps{margin:0;padding:0;list-style:none;font-size:12.5px;
                    font-family:ui-monospace,Menlo,Consolas,monospace}
                  ol.steps li{display:flex;gap:10px;padding:4px 0;border-top:1px solid var(--line);
                    align-items:baseline}
                  ol.steps li:first-child{border-top:0}
                  ol.steps .at{color:var(--muted);min-width:52px;text-align:right;font-size:11.5px}
                  ol.steps .kind{min-width:74px;font-size:10px;font-weight:700;letter-spacing:.5px;
                    padding:2px 7px;border-radius:4px;text-align:center;background:#eef1f6;color:var(--muted)}
                  ol.steps .what{flex:1;word-break:break-word;white-space:pre-wrap}
                  /* A capture shown against the trail line that describes it. Kept small so a
                     case with fifty-two of them still scrolls as a trail rather than a gallery,
                     and clickable so the full-size image is one click away. align-self:flex-start
                     stops a tall thumbnail stretching a short line's row. */
                  ol.steps .step-shot{flex:0 0 auto;align-self:flex-start;line-height:0;
                    border:1px solid var(--line);border-radius:4px;overflow:hidden}
                  ol.steps .step-shot img{display:block;width:180px;height:auto}
                  ol.steps li.k-evidence{align-items:flex-start}
                  @media (max-width:900px){ol.steps .step-shot img{width:120px}}
                  ol.steps li.k-step .kind{background:var(--info-bg);color:var(--info)}
                  ol.steps li.k-check .kind{background:var(--pass-bg);color:var(--pass)}
                  ol.steps li.k-data .kind{background:var(--pass-bg);color:var(--pass)}
                  ol.steps li.k-network .kind,ol.steps li.k-console .kind{background:var(--fail-bg);color:var(--fail)}
                  ol.steps li.k-network .what,ol.steps li.k-console .what{color:var(--fail)}
                  /* A line the case declared FAIL. Tinted across the whole row rather than only
                     the text, so it is findable while scrolling a trail of a hundred lines, and
                     given a left rule so it still reads as failed without relying on colour. */
                  ol.steps li.failed{background:var(--fail-bg);
                    box-shadow:inset 3px 0 0 var(--fail);padding-left:7px}
                  ol.steps li.failed .what{color:var(--fail);font-weight:600}
                  ol.steps li.failed .kind{background:var(--fail);color:#fff}
                  ol.steps li.k-evidence .kind{background:var(--partial-bg);color:var(--partial)}
                  .nav{margin:18px 0 0;font-size:13.5px}
                  .nav a{color:var(--info);text-decoration:none}
                  .nav a:hover{text-decoration:underline}
                  .callout{background:var(--card);border-left:5px solid var(--accent);border-radius:10px;
                    padding:16px 20px;margin:22px 0;box-shadow:0 2px 8px rgba(20,30,50,.06);font-size:14px}
                  .callout h2{margin:0 0 8px;font-size:16px}
                  .callout p{margin:0 0 10px}
                  .callout p:last-child{margin-bottom:0}
                  .callout.warn{border-left-color:var(--partial)}
                  h2.section{font-size:18px;margin:30px 0 12px;padding-bottom:6px;border-bottom:2px solid var(--line)}
                  /* The index's package sections: heading, the package name beside it, then its tally. */
                  h2.section .pkg-name{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12.5px;
                    font-weight:500;color:var(--muted);margin-left:8px}
                  .pkg-tally{display:flex;align-items:center;gap:12px;margin:-4px 0 12px;font-size:13px;color:var(--muted)}
                  .pkg-tally .bar.mini{width:160px;flex:0 0 160px}
                  h3.pkg-findings{font-size:14px;margin:14px 0 4px}
                  h3.pkg-findings a{color:inherit}
                  .tbl-scroll{overflow-x:auto;border-radius:12px;box-shadow:0 2px 10px rgba(20,30,50,.07)}
                  table{border-collapse:collapse;width:100%;background:var(--card);font-size:13.5px;min-width:1000px}
                  thead th{background:var(--head);color:var(--headink);text-align:left;padding:11px 13px;
                    font-weight:600;white-space:nowrap}
                  tbody td{padding:11px 13px;border-top:1px solid var(--line);vertical-align:top;
                    overflow-wrap:break-word}
                  /* Fixed column widths on the case tables, because automatic layout shares the
                     width out by content and one long cell then starves the rest: a remark
                     carrying a two-sentence reading of a failure took 792 of 1565 pixels and left
                     the Actual result column at 103, wrapping its message one word per line. It
                     also stops the full-width detail row from widening the whole table, which was
                     pushing the failure panel off the right of the page. */
                  table.cases{table-layout:fixed;min-width:1600px}
                  table.cases th:nth-child(1),table.cases td:nth-child(1){width:6%}
                  table.cases th:nth-child(2),table.cases td:nth-child(2){width:10%}
                  table.cases th:nth-child(3),table.cases td:nth-child(3){width:12%}
                  table.cases th:nth-child(4),table.cases td:nth-child(4){width:14%}
                  table.cases th:nth-child(5),table.cases td:nth-child(5){width:10%}
                  table.cases th:nth-child(6),table.cases td:nth-child(6){width:11%}
                  table.cases th:nth-child(7),table.cases td:nth-child(7){width:11%}
                  table.cases th:nth-child(8),table.cases td:nth-child(8){width:6%}
                  table.cases th:nth-child(9),table.cases td:nth-child(9){width:10%}
                  table.cases th:nth-child(10),table.cases td:nth-child(10){width:10%}
                  table.cases tr.detail>td{width:auto}
                  /* Pre-condition, Test steps and Test data: the client workbook's own wording, with the values
                     the run actually used listed under the sheet's. */
                  .tc-pre{font-size:12.5px;white-space:pre-wrap}
                  ol.tc-steps{margin:0;padding-left:18px;font-size:12.5px}
                  ol.tc-steps li{margin:0 0 3px}
                  ul.tc-data{margin:0;padding-left:16px;font-size:12.5px}
                  ul.tc-data li{margin:0 0 3px;overflow-wrap:anywhere}
                  ul.tc-data.used code{font-size:11.5px;white-space:normal}
                  td.tsteps .src,td.tdata .src{margin:6px 0 3px;font-size:10.5px;font-weight:700;
                    letter-spacing:.4px;text-transform:uppercase;color:var(--muted)}
                  /* The sheet ids run from HOME-001 to HKDWITHDRAWAL-012, and a fixed column
                     clips what it cannot fit: the longer ones were cut mid-word with no sign
                     that anything was missing. They wrap here rather than truncate. */
                  table.cases td.id{white-space:normal;overflow-wrap:anywhere}
                  tbody tr:nth-child(even){background:#fafbfd}
                  td.tc{font-weight:600}
                  td.tc a{color:var(--ink)}
                  td.id{font-family:ui-monospace,Menlo,Consolas,monospace;color:var(--muted);
                    font-size:12.5px;white-space:nowrap}
                  code{font-family:ui-monospace,Menlo,Consolas,monospace;
                    background:#eef1f6;padding:1px 5px;border-radius:4px;font-size:12.5px}
                  .why{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12px;color:var(--fail);
                    white-space:pre-wrap;word-break:break-word}
                  .muted{color:var(--muted)}
                  .secs{margin-top:6px;font-size:11.5px;color:var(--muted)}
                  .tag{display:inline-block;margin-top:6px;margin-right:4px;padding:1px 7px;border-radius:4px;
                    background:#eef1f6;color:var(--muted);font-size:11px;font-weight:600;
                    font-family:ui-monospace,Menlo,Consolas,monospace}
                  .st{display:inline-block;padding:3px 9px;border-radius:20px;font-size:12px;font-weight:700;
                    white-space:nowrap}
                  .st.pass{color:var(--pass);background:var(--pass-bg)}
                  .st.fail{color:var(--fail);background:var(--fail-bg)}
                  .st.partial{color:var(--partial);background:var(--partial-bg)}
                  .st.info{color:var(--info);background:var(--info-bg)}
                  details.shots{max-width:100%}
                  details.shots>summary{cursor:pointer;font-size:12.5px;color:var(--muted);
                    padding:5px 9px;border:1px solid var(--line);border-radius:999px;
                    display:inline-block;list-style:none;user-select:none}
                  details.shots>summary::-webkit-details-marker{display:none}
                  details.shots>summary::before{content:"\\25B8 ";color:var(--muted)}
                  details.shots[open]>summary::before{content:"\\25BE "}
                  details.shots>summary:hover{color:var(--ink);border-color:var(--muted)}
                  details.shots[open]>summary{margin-bottom:8px}
                  a.shot{display:inline-block;margin:0 6px 6px 0;position:relative;cursor:zoom-in}
                  a.shot img{width:100%;max-width:260px;border:1px solid var(--line);border-radius:8px;display:block;
                    box-shadow:0 2px 6px rgba(20,30,50,.10);transition:transform .12s,box-shadow .12s}
                  a.shot:hover img{transform:scale(1.03);box-shadow:0 6px 18px rgba(20,30,50,.22)}
                  a.shot::after{content:"\\2315";position:absolute;right:8px;bottom:8px;
                    background:rgba(15,25,45,.72);color:#fff;font-size:13px;line-height:1;
                    padding:5px 7px;border-radius:6px;opacity:0;transition:opacity .12s}
                  a.shot:hover::after{opacity:1}
                  /* Click-to-zoom overlay. A 260px thumbnail of a 1920x1080 capture is a
                     smudge - readable enough to say a picture exists, not enough to read the
                     page in it - so the full image opens over the report rather than in a
                     separate tab the reader then has to navigate back from. */
                  #lightbox{position:fixed;inset:0;background:rgba(10,16,28,.88);display:none;
                    align-items:center;justify-content:center;flex-direction:column;z-index:999;
                    padding:24px;cursor:zoom-out}
                  #lightbox.open{display:flex}
                  #lightbox img{max-width:96vw;max-height:86vh;border-radius:8px;
                    box-shadow:0 10px 40px rgba(0,0,0,.5);background:#fff}
                  #lightbox .cap{color:#e8eef8;font-size:13px;margin-top:12px;text-align:center;
                    max-width:80vw}
                  #lightbox .hint{color:#93a4bd;font-size:12px;margin-top:6px}
                  /* A suite page's header is its statistics summary, and collapses to its first
                     lines: title, counts and bar stay; run details, tiles, chart and the cases by
                     result fold away. Drawn on the header's navy, so the status colours are the
                     same bright steps as the header bar, each paired with an icon and a label. */
                  details.rpt-sum>summary{list-style:none;cursor:pointer;user-select:none}
                  details.rpt-sum>summary::-webkit-details-marker{display:none}
                  details.rpt-sum>summary:focus-visible{outline:2px solid #9cc9ff;outline-offset:6px;border-radius:6px}
                  .rpt-line{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
                  .rpt-line{flex-wrap:nowrap}
                  .rpt-line h1{margin:0;flex:1 1 auto;min-width:0}
                  details.rpt-sum>.sub{margin-top:14px}
                  header.rpt .sub code{overflow-wrap:anywhere}
                  .chev::before{content:"\\25B8";display:inline-block;opacity:.8;transition:transform .15s}
                  details[open]>summary .chev::before{transform:rotate(90deg)}
                  .rpt-toggle-hint{margin-left:auto;font-size:12px;opacity:.6}
                  .rpt-counts{display:flex;flex-wrap:wrap;gap:4px 18px;margin-top:10px;font-size:14px;
                    font-weight:600;font-variant-numeric:tabular-nums}
                  .cnt i,.tile i,details.cl>summary i{font-style:normal;margin-right:5px}
                  .cnt.pass i,.tile.pass i,details.cl.pass>summary i{color:#3ddc84}
                  .cnt.fail i,.tile.fail i,details.cl.fail>summary i{color:#ff6b6b}
                  .cnt.skip i,.tile.skip i,details.cl.skip>summary i{color:#ffcc66}
                  .cnt.notrun i,.tile.notrun i,details.cl.notrun>summary i{color:#b8c4d6}
                  .cnt.zero{opacity:.55}
                  .rpt-h{font-size:12.5px;text-transform:uppercase;letter-spacing:.7px;opacity:.75;
                    margin:20px 0 10px;padding-top:14px;border-top:1px solid rgba(255,255,255,.18)}
                  .tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(118px,1fr));gap:10px}
                  .tile{background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.14);
                    border-radius:10px;padding:10px 12px}
                  .tile b{display:block;font-size:22px;line-height:1.25;font-variant-numeric:tabular-nums}
                  .tile span{font-size:12px;opacity:.75}
                  .chart{margin:16px 0 0}
                  .stack{display:flex;gap:2px;height:28px}
                  .stack .seg{display:flex;align-items:center;justify-content:center;flex-basis:0;
                    min-width:4px;overflow:hidden}
                  .stack .seg:first-child{border-radius:4px 0 0 4px}
                  .stack .seg:last-child{border-radius:0 4px 4px 0}
                  .stack .seg:only-child{border-radius:4px}
                  .stack .seg.pass{background:#3ddc84}
                  .stack .seg.fail{background:#ff6b6b}
                  .stack .seg.skip{background:#ffcc66}
                  .stack .seg.notrun{background:#8fa3bf}
                  .stack .seg:focus-visible{outline:2px solid #fff;outline-offset:1px}
                  .stack em{font-style:normal;font-size:12px;font-weight:700;color:#14203a}
                  .chart-legend{display:flex;flex-wrap:wrap;gap:6px 16px;margin-top:10px;font-size:12.5px;opacity:.85}
                  .sw{display:inline-block;width:10px;height:10px;border-radius:3px;margin-right:6px;vertical-align:-1px}
                  .sw.pass{background:#3ddc84}
                  .sw.fail{background:#ff6b6b}
                  .sw.skip{background:#ffcc66}
                  .sw.notrun{background:#8fa3bf}
                  .case-lists{display:flex;flex-direction:column;gap:8px}
                  details.cl{border:1px solid rgba(255,255,255,.16);border-radius:10px;padding:0 14px;
                    background:rgba(255,255,255,.04)}
                  details.cl>summary{cursor:pointer;padding:9px 0;font-weight:600;font-size:13.5px}
                  details.cl .n{opacity:.7;font-weight:400}
                  details.cl ol{margin:0 0 12px;padding-left:22px;font-size:13px;max-height:280px;overflow:auto}
                  details.cl li{margin:5px 0}
                  details.cl li a{font-family:ui-monospace,Menlo,Consolas,monospace;font-weight:700;
                    text-decoration:none;color:#9cc9ff}
                  details.cl.fail li a{color:#ff9d9d}
                  details.cl li a:hover{text-decoration:underline}
                  header.rpt .why{color:#ffb3b3}
                  #tip{position:fixed;display:none;z-index:1000;background:#0e1524;color:#fff;font-size:12px;
                    padding:6px 9px;border-radius:6px;pointer-events:none;max-width:320px;
                    box-shadow:0 4px 14px rgba(0,0,0,.35)}
                  @media (max-width:640px){.rpt-toggle-hint{display:none}}
                  footer{margin-top:28px;font-size:12.5px;color:var(--muted);text-align:center}
                </style>
                </head>
                """.replace("__TITLE__", escape(title));
    }

    // ---------- small helpers ----------

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.strip().split("\\R", 2)[0];
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + " ...";
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
