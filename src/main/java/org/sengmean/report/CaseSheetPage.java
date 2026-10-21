package org.sengmean.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the imported client test case workbook into a local page with a <b>Run</b> link per row.
 *
 * <h2>What this is for</h2>
 * The workbook is the client's index of what the product must do; this project automates part of
 * it. Reading one and running the other meant keeping a spreadsheet and an IDE open and knowing
 * which Java method answered which row. This page puts the two side by side: every row of every
 * tab, in the client's own words, with a link on the rows that have a test behind them.
 *
 * <h2>Where the workbook comes from</h2>
 * {@code src/test/resources/testdata/client_testcase.xlsx} is an export of the client's Google
 * Sheet, checked in so the project can be read and run without an internet connection or an
 * account on someone else's Drive. It is a <b>copy</b>, and the sheet is the original: re-export
 * it when the client changes their cases. Nothing here writes back to it.
 *
 * <h2>How a row becomes a link</h2>
 * The workbook writes an id as {@code TC_LOGIN_001}; the suites write the same case as
 * {@code LOGIN-001} in a {@code @DisplayName}. {@link CaseSheetLayout#suiteId(String)} converts between them and
 * {@link CaseIndex} decides whether anything actually carries the result. A row that resolves gets
 * a link; a row that does not gets the reason instead of a link.
 *
 * <p><b>A row with no link is a row with no test behind it, and that is information.</b> It would
 * be easy to link every row and let the run report "Tests run: 0" - and that is exactly the
 * failure this project has been bitten by twice, because a run that selects nothing reports
 * success. An unresolved row says so on the page instead.
 *
 * <h2>What the link does</h2>
 * {@code fpr://LOGIN-001}, which the handler registered by {@code tools/install-url-handler.ps1}
 * turns into {@code ./mvnw test -Dcase=LOGIN-001} in a console window. A browser will not run a
 * command for a page, and it will not link a custom scheme from Google Sheets either - but it
 * will hand one to a registered handler from a local file, which is what this page is.
 */
public final class CaseSheetPage {

    /**
     * The imported workbook and the page built from it.
     *
     * Relative to the working directory and overridable by system property, which is the same
     * shape {@link HtmlReportGenerator#testSrc()} and its siblings use: Maven runs from the
     * repository root, and the property is what lets a run from somewhere else say so.
     */
    public static Path workbook() {
        return Path.of(System.getProperty("report.workbook",
                "src/test/resources/testdata/client_testcase.xlsx"));
    }

    /** Whether the page shows only the tabs the client coloured green. */
    static boolean greenOnly() {
        return Boolean.parseBoolean(System.getProperty("report.cases.greenOnly", "true"));
    }

    static Path outFile() {
        return Path.of(System.getProperty("report.cases.out", "docs/test-cases/index.html"));
    }

    /** One stylesheet for the index and every feature page, so the set cannot drift apart. */
    private static final String STYLE = """
            <!doctype html><meta charset="utf-8">
            <title>Client test cases</title>
            <style>
             :root{--bg:#fbfbfd;--fg:#1d2027;--muted:#6b7280;--line:#e3e5ea;--card:#fff;
                   --link:#2f6feb;--warn:#9a6b00;--target:#fff3cd}
             @media (prefers-color-scheme:dark){:root{--bg:#12141a;--fg:#e6e8ee;--muted:#98a0b0;
                   --line:#272b35;--card:#1a1d25;--link:#7aa7ff;--warn:#d6a634;--target:#33302099}}
             *{box-sizing:border-box}
             body{margin:0;background:var(--bg);color:var(--fg);
                  font:14px/1.55 system-ui,-apple-system,Segoe UI,sans-serif}
             header{padding:2rem 2rem 1rem;border-bottom:1px solid var(--line)}
             h1{margin:0 0 .3rem;font-size:1.35rem}
             .sub{color:var(--muted)}
             main{padding:1.5rem 2rem 4rem}
             h2{font-size:1rem;margin:2rem 0 .6rem;padding-bottom:.35rem;
                border-bottom:1px solid var(--line)}
             .wrap{overflow-x:auto;background:var(--card);border:1px solid var(--line);
                   border-radius:8px}
             table{border-collapse:collapse;width:100%;min-width:52rem}
             th,td{text-align:left;padding:.5rem .7rem;border-bottom:1px solid var(--line);
                   vertical-align:top}
             th{font-size:.72rem;text-transform:uppercase;letter-spacing:.04em;
                color:var(--muted);font-weight:600}
             tr:last-child td{border-bottom:0}
             td.id{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;white-space:nowrap}
             /* The row a RUN link from the results workbook has jumped to. Without this the
                browser scrolls to the right place and the reader still has to work out which of
                the rows on screen was meant. scroll-margin keeps it clear of the sticky header. */
             tr:target{scroll-margin-top:4rem}
             tr:target td{background:var(--target,#fff6d5)}
             tr:target td.id{font-weight:700}
             td.run{white-space:nowrap}
             a.run{display:inline-block;padding:.15rem .6rem;border:1px solid var(--link);
                   border-radius:5px;color:var(--link);text-decoration:none;font-weight:600}
             a.run:hover{background:var(--link);color:#fff}
             .none{color:var(--muted)}
             .warn{color:var(--warn)}
             code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
             .note{max-width:60rem;color:var(--muted);margin:.8rem 0 0}
             .back{display:inline-block;margin:0 0 .2rem;color:var(--link);text-decoration:none}
             .back:hover{text-decoration:underline}
             .cards{display:grid;gap:.8rem;
                    grid-template-columns:repeat(auto-fill,minmax(15rem,1fr))}
             .card{display:block;padding:1rem 1.1rem;background:var(--card);border-radius:9px;
                   border:1px solid var(--line);text-decoration:none;color:inherit}
             .card:hover{border-color:var(--link)}
             .card .name{font-weight:600;margin-bottom:.2rem}
             .card .count{color:var(--muted);font-size:.85rem}
            </style>
            """;

    /**
     * A column of the page: what to call it, and the header spellings that fill it.
     *
     * The workbook uses two vocabularies - four green tabs say "Important Level" and four say
     * "RISK LEVEL" for the same thing - so a column is a list of spellings, first match wins.
     */
    private record Column(String label, List<String> headers) {
    }

    /** Columns worth showing, in the page's order. */
    private static final List<Column> COLUMNS = List.of(
            new Column("Test case id", List.of("TEST CASE ID")),
            new Column("Scenario", List.of("TEST SCENARIO")),
            new Column("Test case", List.of("TEST CASE")),
            new Column("Pre-condition", List.of("PRE-CONDITION", "PRECONDITION", "PRE CONDITION")),
            new Column("Test steps", List.of("TEST STEPS", "TEST STEP", "STEPS")),
            new Column("Test data", List.of("TEST DATA")),
            new Column("Expected result", List.of("EXPECTED RESULTS", "EXPECTED RESULT")),
            new Column("Importance", List.of("Important Level", "RISK LEVEL")));

    private CaseSheetPage() {
    }

    /** One row of one tab, as the page needs it. */
    private record CaseRow(String sheetId, String suiteId, List<String> cells,
                           CaseIndex.TestCase automated, String whyNot) {
    }

    public static void main(String[] args) throws IOException {
        generate();
    }

    /**
     * Rewrites the page from the imported workbook.
     *
     * Quiet when the workbook is absent rather than throwing: this runs at the end of every test
     * run, and a missing copy of the client's spreadsheet is not a reason to fail a build that
     * has already done its work.
     */
    public static void generate() throws IOException {
        Path workbook = workbook();
        if (!Files.exists(workbook)) {
            System.out.println("No client workbook at " + workbook + " - test case page not written.");
            return;
        }

        Map<String, List<CaseRow>> tabs = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        Map<String, CaseIndex.TestCase> index = CaseIndex.byId();

        try (InputStream in = Files.newInputStream(workbook);
             Workbook book = new XSSFWorkbook(in)) {
            FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
            for (int s = 0; s < book.getNumberOfSheets(); s++) {
                Sheet sheet = book.getSheetAt(s);
                // The client marks what is in scope by colouring the sheet tab green, so that is
                // what the page shows: eight tabs rather than thirty-six, and no scrolling past
                // a thousand rows nobody is being asked to automate. -Dreport.cases.greenOnly=false
                // renders the whole workbook when the wider view is wanted.
                if (greenOnly() && !CaseSheetLayout.markedGreen(sheet)) {
                    continue;
                }
                List<CaseRow> rows = read(sheet, index, evaluator);
                if (rows == null) {
                    skipped.add(sheet.getSheetName());
                } else if (!rows.isEmpty()) {
                    tabs.put(sheet.getSheetName(), rows);
                }
            }
        }

        Path out = outFile();
        Files.createDirectories(out.getParent());

        // One page per feature, plus an index over them. A single page of every green tab was
        // seven hundred rows of scrolling to reach Register, and the whole point of the split is
        // that a feature is a thing you open on its own.
        for (Map.Entry<String, List<CaseRow>> tab : tabs.entrySet()) {
            Path page = out.getParent()
                    .resolve(CaseSheetSplitter.slug(tab.getKey()) + ".html");
            Map<String, List<CaseRow>> one = new LinkedHashMap<>();
            one.put(tab.getKey(), tab.getValue());
            try (OutputStream stream = Files.newOutputStream(page)) {
                stream.write(render(one, List.of()).getBytes(StandardCharsets.UTF_8));
            }
        }

        try (OutputStream stream = Files.newOutputStream(out)) {
            stream.write(renderIndex(tabs, skipped).getBytes(StandardCharsets.UTF_8));
        }

        long linked = tabs.values().stream().flatMap(List::stream)
                .filter(row -> row.automated != null).count();
        long total = tabs.values().stream().mapToLong(List::size).sum();
        System.out.println("Wrote " + out.getParent() + " (" + tabs.size() + " feature pages, "
                + linked + " of " + total + " rows runnable)");
    }

    /** The rows of one tab, or null when the tab carries no recognisable header. */
    private static List<CaseRow> read(Sheet sheet, Map<String, CaseIndex.TestCase> index,
                                      FormulaEvaluator evaluator) {
        CaseSheetLayout.Table table = CaseSheetLayout.of(sheet, evaluator);
        if (!table.found()) {
            return null;
        }
        int idColumn = table.idColumn();

        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Column wanted : COLUMNS) {
            int column = table.column(wanted.headers().toArray(new String[0]));
            if (column >= 0) {
                columns.put(wanted.label(), column);
            }
        }
        columns.putIfAbsent(COLUMNS.get(0).label(), idColumn);

        List<CaseRow> rows = new ArrayList<>();
        for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String sheetId = text(row.getCell(idColumn), evaluator).trim();
            if (sheetId.isEmpty()) {
                continue;
            }

            List<String> cells = new ArrayList<>();
            for (Column wanted : COLUMNS) {
                Integer column = columns.get(wanted.label());
                cells.add(column == null ? "" : text(row.getCell(column), evaluator).trim());
            }

            String suiteId = CaseSheetLayout.suiteId(sheetId);
            CaseIndex.TestCase automated = index.get(suiteId);
            String whyNot = automated == null ? "not automated" : null;
            if (automated != null && automated.createsData()) {
                whyNot = "creates data";
            }
            rows.add(new CaseRow(sheetId, suiteId, cells, automated, whyNot));
        }
        return rows;
    }

    /** Delegates to the shared layout, so the page and the writer read the workbook alike. */
    private static String text(Cell cell, FormulaEvaluator evaluator) {
        return CaseSheetLayout.text(cell, evaluator);
    }


    /**
     * The index: one card per feature, saying how much of it is automated and linking to its page.
     *
     * <p>The counts are the reason this is worth a page of its own rather than a list of links -
     * "12 of 158" beside a feature name is the coverage conversation in one line, and it is
     * derived from the sources on every build rather than maintained by anyone.
     */
    private static String renderIndex(Map<String, List<CaseRow>> tabs, List<String> skipped) {
        StringBuilder html = new StringBuilder();
        long total = tabs.values().stream().mapToLong(List::size).sum();
        long linked = tabs.values().stream().flatMap(List::stream)
                .filter(row -> row.automated != null).count();

        html.append(STYLE)
                .append("<header><h1>Client test cases</h1><div class=\"sub\">")
                .append(tabs.size()).append(" features &middot; ").append(linked).append(" of ")
                .append(total).append(" rows have a test behind them &middot; generated ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("</div>")
                .append("""
                        <p class="note">Only the tabs the client coloured green are here - those are
                        the features in scope. Open one to run its cases: clicking <b>Run</b> executes
                        <code>./mvnw test -Dcase=&lt;id&gt;</code> in a console window, which needs the
                        handler registered once per machine with
                        <code>powershell -ExecutionPolicy Bypass -File tools\\install-url-handler.ps1</code>.</p>
                        </header><main><div class="cards">
                        """);

        for (Map.Entry<String, List<CaseRow>> tab : tabs.entrySet()) {
            long featureLinked = tab.getValue().stream().filter(row -> row.automated != null).count();
            html.append("<a class=\"card\" href=\"")
                    .append(escape(CaseSheetSplitter.slug(tab.getKey()))).append(".html\">")
                    .append("<div class=\"name\">").append(escape(tab.getKey())).append("</div>")
                    .append("<div class=\"count\">").append(featureLinked).append(" of ")
                    .append(tab.getValue().size()).append(" runnable</div>")
                    .append("</a>\n");
        }
        html.append("</div>");

        if (!skipped.isEmpty()) {
            html.append("<p class=\"note\">Green tabs with no <code>TEST CASE ID</code> header, so "
                    + "no rows were read: ").append(escape(String.join(", ", skipped))).append("</p>");
        }
        return html.append("</main>").toString();
    }

    private static String render(Map<String, List<CaseRow>> tabs, List<String> skipped) {
        StringBuilder html = new StringBuilder();
        long total = tabs.values().stream().mapToLong(List::size).sum();
        long linked = tabs.values().stream().flatMap(List::stream)
                .filter(row -> row.automated != null).count();

        html.append(STYLE);

        html.append("<header><a class=\"back\" href=\"index.html\">&larr; all features</a>"
                        + "<h1>" + escape(tabs.keySet().iterator().next()) + "</h1>"
                        + "<div class=\"sub\">")
                .append(linked).append(" of ").append(total)
                .append(" rows have a test behind them &middot; generated ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("</div>")
                .append("""
                        <p class="note">Clicking <b>Run</b> executes <code>./mvnw test -Dcase=&lt;id&gt;</code>
                        in a console window. It needs the handler registered once per machine:
                        <code>powershell -ExecutionPolicy Bypass -File tools\\install-url-handler.ps1</code>.
                        Rows with no link have no automated case - they are the client's, not the
                        project's, and a link that ran nothing would report success.</p>
                        </header><main>
                        """);

        for (Map.Entry<String, List<CaseRow>> tab : tabs.entrySet()) {
            html.append("<h2>").append(escape(tab.getKey())).append("</h2><div class=\"wrap\"><table><tr>");
            for (Column column : COLUMNS) {
                html.append("<th>").append(escape(column.label())).append("</th>");
            }
            html.append("<th>Run</th></tr>");

            for (CaseRow row : tab.getValue()) {
                // Anchored on the suite id, so the RUN link in the results workbook -
                // <base>/<feature>.html#SIGNUP-901 - lands on the case rather than at the top of a
                // page of two hundred rows. See CaseSheetResults.setRunLink.
                html.append("<tr id=\"").append(escape(row.suiteId)).append("\">");
                for (int i = 0; i < row.cells.size(); i++) {
                    html.append(i == 0 ? "<td class=\"id\">" : "<td>")
                            .append(escape(row.cells.get(i)))
                            .append("</td>");
                }
                html.append("<td class=\"run\">");
                if (row.automated == null) {
                    html.append("<span class=\"none\">not automated</span>");
                } else if (row.automated.createsData()) {
                    // Shown, but labelled. These register accounts, commit deposits and stake
                    // balance; the label is the difference between clicking one on purpose and
                    // clicking it because it was next in the column.
                    html.append("<a class=\"run\" href=\"fpr://").append(escape(row.suiteId))
                            .append("\">Run</a> <span class=\"warn\" title=\"registers accounts, "
                                    + "commits deposits or stakes balance\">creates data</span>");
                } else {
                    html.append("<a class=\"run\" href=\"fpr://").append(escape(row.suiteId))
                            .append("\">Run</a>");
                }
                // One row per line, so the page can be grepped and diffed like anything else.
                html.append("</td></tr>\n");
            }
            html.append("</table></div>");
        }

        if (!skipped.isEmpty()) {
            html.append("<p class=\"note\">Tabs with no <code>TEST CASE ID</code> header, so no "
                    + "rows were read: ").append(escape(String.join(", ", skipped))).append("</p>");
        }
        return html.append("</main>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("\n", "<br>");
    }
}
