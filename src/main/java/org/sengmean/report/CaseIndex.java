package org.sengmean.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every automated case, keyed by the id the client test case sheet uses.
 *
 * <h2>What it is for</h2>
 * The sheet's own column A holds {@code TC_HOME_002}; the suites name the same case
 * {@code HOME-002} in a {@code @DisplayName}; and Surefire needs
 * {@code HomePageTest$HeaderFurniture#logoIsTopLeft}. This is the one place that maps
 * between the three, so that clicking an id in the sheet can run exactly that case - see
 * {@code org.sengmean.api.TestRunController}.
 *
 * <h2>Why it reuses the report generator's parser</h2>
 * {@link HtmlReportGenerator#readSpecs(Path)} already reads the test sources for the ids,
 * display names, tags and {@code @Disabled} reasons, because the HTML report needs all of
 * it. Writing a second parser here would mean two things that both have to keep agreeing
 * about how a {@code @DisplayName} is written - and the first symptom of them disagreeing
 * would be a link in a client's spreadsheet that runs the wrong test.
 *
 * <p>The index is read from source on every call rather than cached: the endpoint is used a
 * handful of times a day, parsing fourteen files costs milliseconds, and a cache would mean
 * a case renamed in the IDE keeps running the old method until the app is restarted.
 */
public final class CaseIndex {

    /**
     * One case, as the three systems that care about it each name it.
     *
     * @param id       the sheet's id with the wrapper stripped, e.g. {@code HOME-002}
     * @param suite    the top-level test class, e.g. {@code HomePageTest}
     * @param group    the enclosing {@code @Nested} class, or "" for a top-level case
     * @param method   the test method
     * @param expected the display name minus the id, i.e. what the case proves
     * @param tags     the JUnit tags, which is how a caller sees {@code creates-data}
     * @param selector what Surefire wants for {@code -Dtest=}
     */
    public record TestCase(String id, String suite, String group, String method,
                           String expected, List<String> tags, String selector) {

        /** True when running this case has side effects that outlive the run. */
        public boolean createsData() {
            return tags.contains("creates-data");
        }

        /** True when the case asserts behaviour the application does not have yet. */
        public boolean knownDefect() {
            return tags.contains("known-defect");
        }
    }

    private CaseIndex() {
    }

    /** Every case that carries an id, keyed by it. Later duplicates do not displace earlier. */
    public static Map<String, TestCase> byId() throws IOException {
        List<HtmlReportGenerator.SuiteReport> suites =
                HtmlReportGenerator.readSpecs(HtmlReportGenerator.testSrc());
        Map<String, TestCase> index = new LinkedHashMap<>();

        // Pass one: the ids cases own outright, from their @DisplayName.
        //
        // A display name may carry more than one where one workbook row is answered by several -
        // "W-1 / WDR-03" - so every id it names resolves to the same case rather than only the
        // first.
        for (HtmlReportGenerator.SuiteReport suite : suites) {
            for (HtmlReportGenerator.Spec spec : suite.specs) {
                for (String id : spec.id.split("/")) {
                    String key = id.trim();
                    if (!key.isEmpty()) {
                        index.putIfAbsent(key, toCase(key, spec));
                    }
                }
            }
        }

        // Pass two: the rows named in an "Also covers workbook rows:" comment above a case.
        //
        // These resolve to the same test, which is what puts a Run button on those rows in the
        // client test case page. They are kept out of the display name on purpose: one sweep can
        // answer fourteen workbook rows, and listing fourteen ids in a @DisplayName turns the
        // report's Id column back into a list and breaks the display-name ordering every suite
        // runs on. See HtmlReportGenerator.Spec#alsoCovers.
        //
        // <b>A whole second pass rather than a second loop inside the first</b>, and the reason is
        // the only thing that makes this safe: within one pass the specs are visited in file
        // order, so a case early in the tree could claim, as an "also covers", an id that a case
        // later in the tree owns in its own @DisplayName - and putIfAbsent would then keep the
        // wrong one. Ownership is decided completely before any of these are offered, so a
        // misplaced comment can never take a row off the case whose display name names it.
        for (HtmlReportGenerator.SuiteReport suite : suites) {
            for (HtmlReportGenerator.Spec spec : suite.specs) {
                for (String covered : spec.alsoCovers) {
                    String key = covered.trim();
                    if (!key.isEmpty()) {
                        index.putIfAbsent(key, toCase(key, spec));
                    }
                }
            }
        }

        return index;
    }

    /** The case for an id, or null when nothing automates it. */
    public static TestCase find(String id) throws IOException {
        return byId().get(id == null ? "" : id.trim().toUpperCase());
    }

    private static TestCase toCase(String id, HtmlReportGenerator.Spec spec) {
        // Surefire addresses a @Nested case as Outer$Nested#method. Getting this wrong does
        // not fail loudly - it selects nothing and the run reports success with no tests.
        String selector = spec.group.isEmpty()
                ? spec.suite + "#" + spec.method
                : spec.suite + "$" + spec.group + "#" + spec.method;
        return new TestCase(id, spec.suite, spec.group, spec.method, spec.expected,
                List.copyOf(spec.tags), selector);
    }
}
