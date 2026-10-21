package org.sengmean.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Writes a run's outcomes back into a copy of the client test case workbook.
 *
 * <h2>Into a copy, never the import</h2>
 * {@code src/test/resources/testdata/client_testcase.xlsx} is the input and stays untouched. The
 * results go to {@code docs/reports/client_testcase-results.xlsx}, which is the file to send back.
 * Writing into the import instead would put a megabyte of unreviewable binary diff into every
 * commit after every run, and would leave the project with no clean copy of what the client
 * actually asked for.
 *
 * <h2>Only the green tabs</h2>
 * The client marks the features that are in scope by colouring the <b>sheet tab</b> green - eight
 * of the forty-three. The other tabs are read and rendered by {@link CaseSheetPage} but nothing is
 * written into them, because a blank result column there is the honest state: no suite covers them.
 *
 * <h2>Only rows that ran, and only rows a test answers</h2>
 * Two filters, and both matter:
 * <ul>
 *   <li>A row whose id resolves to no case in {@link CaseIndex} is left alone. The index is parsed
 *       from the test sources, so it is the project's own answer to "does this case exist", and it
 *       is what keeps the workbook in step with the suites rather than with a list maintained
 *       beside them.</li>
 *   <li>A row whose case did not run <b>this time</b> is left alone rather than blanked. A run of
 *       one case must not turn the other hundred rows into "Not run" - that is a report of this
 *       run, not a statement about the product, and overwriting yesterday's real result with it
 *       would lose the only record of it.</li>
 * </ul>
 *
 * <p>Each written cell carries the id and the timestamp of the run that produced it, so a reader
 * can tell an automated result from a tester's own note without opening anything else.
 */
public final class CaseSheetResults {

    /** Where the per-feature results workbooks are written. */
    static Path outDir() {
        return Path.of(System.getProperty("report.results.dir", "docs/reports/cases"));
    }

    /** {@code Login} becomes {@code docs/reports/cases/login-results.xlsx}. */
    static Path outFile(String feature) {
        return outDir().resolve(CaseSheetSplitter.slug(feature) + "-results.xlsx");
    }

    /**
     * The workbook column the pass or fail goes in, spelled both ways across the green tabs:
     * four head it {@code STATUS(PASS/FAIL)} and four just {@code STATUS}. Looking for only the
     * first left half the features with results that were computed and never written.
     */
    private static final String[] STATUS_HEADERS = {"STATUS(PASS/FAIL)", "STATUS"};

    /** The workbook column the observed behaviour goes in. */
    private static final String ACTUAL_HEADER = "ACTUAL RESULT";

    /**
     * What {@link #describe} stamps on a cell this project wrote.
     *
     * <p>It is what tells our result from the client's own wording in the same column, which is
     * the difference that lets a results file be rebuilt from a grown source without either
     * losing what runs have recorded or overwriting what the client wrote.
     */
    private static final String RECORDED_MARKER = "[automated ";

    /**
     * The column of Run links this adds, on the right of whatever the tab already has.
     *
     * <p>So the results file is not only something to read: each row it covers links straight to
     * that case's own row on its feature page. See {@link #setRunLink} for where the link points
     * and why it is no longer the {@code fpr://} scheme it began as.
     */
    private static final String RUN_HEADER = "RUN";

    private CaseSheetResults() {
    }

    /**
     * Rebuilds the results workbook with no outcomes, which writes the Run links and leaves every
     * recorded result alone. This is the "I have just re-imported the sheet" entry point.
     */
    public static void main(String[] args) throws IOException {
        write(Map.of());
    }

    /**
     * Writes the outcomes of one run into the results copy.
     *
     * @param outcomes keyed {@code TopLevelClass#method}, exactly as
     *                 the listener collects them
     */
    public static void write(Map<String, HtmlReportGenerator.Outcome> outcomes) throws IOException {
        // The per-feature source files, cut from the client's single export. Split on demand so
        // that a re-import needs one command rather than two remembered in the right order.
        List<Path> features = CaseSheetSplitter.splitIfStale();
        if (features.isEmpty()) {
            return;
        }
        Files.createDirectories(outDir());

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        Map<String, CaseIndex.TestCase> index = CaseIndex.byId();
        List<String> written = new ArrayList<>();
        // Sorted and de-duplicated: a case can answer several ids, and the summary should say
        // each one once.
        TreeSet<String> ranButUnmapped = new TreeSet<>();
        for (Map.Entry<String, CaseIndex.TestCase> entry : index.entrySet()) {
            if (outcomes.containsKey(key(entry.getValue()))) {
                ranButUnmapped.add(entry.getKey());
            }
        }

        for (Path feature : features) {
            writeFeature(feature, outcomes, index, stamp, written, ranButUnmapped);
        }

        if (!ranButUnmapped.isEmpty()) {
            // Named rather than counted, because each one is a case the project ran and the
            // client's workbook has nowhere to record - either a project-only id like LGN-002,
            // or a case whose display name has drifted from the row it answers.
            System.out.println("  ran with no row in a green tab: "
                    + String.join(", ", ranButUnmapped));
        }
    }

    /** Links and results for one feature, written to its own workbook. */
    private static void writeFeature(Path feature,
                                     Map<String, HtmlReportGenerator.Outcome> outcomes,
                                     Map<String, CaseIndex.TestCase> index,
                                     String stamp,
                                     List<String> written,
                                     TreeSet<String> ranButUnmapped) throws IOException {
        String name = featureName(feature);
        Path out = outFile(name);

        // Built on this feature's previous results file when there is one, so running one case
        // adds to what earlier runs recorded rather than starting from a workbook with no
        // results in it. Only when there is none does it start from the freshly split source.
        //
        // Unless the source has grown. Rows added to client_testcase.xlsx reach the split feature
        // file but could never reach a results copy that already existed, because that copy was
        // the base and nothing ever re-read the source: 164 rows added on 2026-09-12 produced
        // "1 Run links" on a deposit tab of 58 new cases, and the new rows were simply absent.
        // When the source carries ids the copy does not, the copy is rebuilt from the source and
        // the results recorded so far are carried across by id.
        Path previous = Files.exists(out) ? out : null;
        Path base = previous;
        Map<String, String[]> carried = Map.of();
        if (previous != null && sourceHasNewRows(feature, previous)) {
            carried = recordedResults(previous);
            base = feature;
            System.out.println("  " + name + ": the source has rows this results file does not; "
                    + "rebuilding from it and carrying " + carried.size() + " recorded result(s).");
        }
        if (base == null) {
            base = feature;
        }

        int linked = 0;
        int recorded = 0;
        try (InputStream in = Files.newInputStream(base);
             Workbook book = new XSSFWorkbook(in)) {
            FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
            CreationHelper helper = book.getCreationHelper();
            CellStyle linkStyle = linkStyle(book);

            for (int s = 0; s < book.getNumberOfSheets(); s++) {
                Sheet sheet = book.getSheetAt(s);
                CaseSheetLayout.Table table = CaseSheetLayout.of(sheet, evaluator);
                if (!table.found()) {
                    continue;
                }
                int statusColumn = table.column(STATUS_HEADERS);
                int actualColumn = table.column(ACTUAL_HEADER);
                int runColumn = runColumn(sheet, table, linkStyle);

                for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    String sheetId =
                            CaseSheetLayout.text(row.getCell(table.idColumn()), evaluator).trim();
                    if (sheetId.isEmpty()) {
                        continue;
                    }
                    String suiteId = CaseSheetLayout.suiteId(sheetId);
                    CaseIndex.TestCase automated = index.get(suiteId);
                    if (automated == null) {
                        continue;
                    }

                    // The link goes on every row a test answers, whether or not it ran this
                    // time. It describes what can be run, not what was.
                    setRunLink(row, runColumn, name, suiteId, helper, linkStyle);
                    linked++;

                    // Put back what earlier runs had recorded, when this file is being rebuilt
                    // from a source that grew. Done before this run's own outcome so that a case
                    // which ran again overwrites it rather than the other way round.
                    String[] earlier = carried.get(sheetId);
                    if (earlier != null) {
                        if (statusColumn >= 0 && !earlier[0].isBlank()) {
                            set(row, statusColumn, earlier[0]);
                        }
                        if (actualColumn >= 0) {
                            set(row, actualColumn, earlier[1]);
                        }
                    }

                    HtmlReportGenerator.Outcome outcome = outcomes.get(key(automated));
                    if (outcome == null) {
                        continue;
                    }
                    if (statusColumn >= 0) {
                        set(row, statusColumn, outcome.status());
                    }
                    if (actualColumn >= 0) {
                        set(row, actualColumn, describe(suiteId, outcome, stamp));
                    }
                    written.add(sheet.getSheetName() + " " + sheetId + " -> " + outcome.status());
                    ranButUnmapped.remove(suiteId);
                    recorded++;
                }
            }

            try (OutputStream stream = Files.newOutputStream(out)) {
                book.write(stream);
            }
        }
        System.out.println("  " + out + "  (" + linked + " Run links, " + recorded
                + " result" + (recorded == 1 ? "" : "s") + " this run)");
    }

    /**
     * True when the split source has anything the existing results copy has not.
     *
     * <p>Ids <b>and</b> column headings, because the copy goes stale in two different ways and
     * checking only the first left it half right: adding the {@code Automation} column to the
     * workbook changed no id at all, so the results files were rebuilt from their own previous
     * copies and the deposit, withdrawal and manual-transfer ones came out still missing the
     * column that had just been added to the source.
     */
    private static boolean sourceHasNewRows(Path feature, Path previous) throws IOException {
        Set<String> beforeIds = idsIn(previous);
        for (String id : idsIn(feature)) {
            if (!beforeIds.contains(id)) {
                return true;
            }
        }
        Set<String> beforeHeaders = headersIn(previous);
        for (String header : headersIn(feature)) {
            if (!beforeHeaders.contains(header)) {
                return true;
            }
        }
        return false;
    }

    /** Every column heading in a workbook, tab-qualified so two tabs cannot mask each other. */
    private static Set<String> headersIn(Path book) throws IOException {
        Set<String> headers = new HashSet<>();
        try (InputStream in = Files.newInputStream(book);
             Workbook wb = new XSSFWorkbook(in)) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                CaseSheetLayout.Table table = CaseSheetLayout.of(sheet, evaluator);
                if (!table.found()) {
                    continue;
                }
                Row header = sheet.getRow(table.headerRow());
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    String text = CaseSheetLayout.text(header.getCell(c), evaluator);
                    if (text != null && !text.isBlank()) {
                        headers.add(sheet.getSheetName() + "|"
                                + text.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
        return headers;
    }

    /** Every {@code TC_...} id in a workbook, whichever tab it is on. */
    private static Set<String> idsIn(Path book) throws IOException {
        Set<String> ids = new HashSet<>();
        try (InputStream in = Files.newInputStream(book);
             Workbook wb = new XSSFWorkbook(in)) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                CaseSheetLayout.Table table = CaseSheetLayout.of(sheet, evaluator);
                if (!table.found()) {
                    continue;
                }
                for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    String id = CaseSheetLayout.text(row.getCell(table.idColumn()), evaluator);
                    if (id != null && !id.isBlank()) {
                        ids.add(id.trim());
                    }
                }
            }
        }
        return ids;
    }

    /**
     * The status and actual result the previous copy recorded, by sheet id.
     *
     * <p>Only rows this project has written are carried: a row still holding the client's own
     * wording is left to come fresh from the source, so re-importing the workbook is what updates
     * it rather than a stale copy overwriting it. {@link #describe} is what marks ours.
     */
    private static Map<String, String[]> recordedResults(Path previous) throws IOException {
        Map<String, String[]> byId = new HashMap<>();
        try (InputStream in = Files.newInputStream(previous);
             Workbook wb = new XSSFWorkbook(in)) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                CaseSheetLayout.Table table = CaseSheetLayout.of(sheet, evaluator);
                if (!table.found()) {
                    continue;
                }
                int statusColumn = table.column(STATUS_HEADERS);
                int actualColumn = table.column(ACTUAL_HEADER);
                for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    String id = CaseSheetLayout.text(row.getCell(table.idColumn()), evaluator);
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    String actual = actualColumn < 0 ? ""
                            : CaseSheetLayout.text(row.getCell(actualColumn), evaluator);
                    if (actual == null || !actual.contains(RECORDED_MARKER)) {
                        continue;
                    }
                    String status = statusColumn < 0 ? ""
                            : CaseSheetLayout.text(row.getCell(statusColumn), evaluator);
                    byId.put(id.trim(), new String[]{status == null ? "" : status, actual});
                }
            }
        }
        return byId;
    }

    /** {@code features/login.xlsx} becomes {@code login}. */
    private static String featureName(Path feature) {
        String file = feature.getFileName().toString();
        int dot = file.lastIndexOf('.');
        return dot < 0 ? file : file.substring(0, dot);
    }

    /** How {@link HtmlReportListener} keys an outcome: the top-level class and the method. */
    private static String key(CaseIndex.TestCase found) {
        return found.suite() + "#" + found.method();
    }

    /**
     * What goes in ACTUAL RESULT.
     *
     * A pass says so and nothing more; the interesting case is a failure, where the assertion
     * message is what a reader needs and is exactly what a screenshot cannot give them.
     */
    private static String describe(String suiteId, HtmlReportGenerator.Outcome outcome,
                                   String stamp) {
        StringBuilder text = new StringBuilder();
        if (HtmlReportGenerator.PASS.equals(outcome.status())) {
            text.append("As expected.");
        } else if (!outcome.message().isBlank()) {
            text.append(outcome.message().trim());
        } else {
            text.append(outcome.status());
        }
        return text.append("\n[automated ").append(suiteId).append(", ").append(stamp).append(']')
                .toString();
    }

    /**
     * The column the Run links go in, creating and heading it when the tab has none yet.
     *
     * <p>Looked up by header first, so a second run reuses the column it added rather than
     * marching one further right every time. Otherwise it is placed after the last header the tab
     * carries, which is why the header row is measured rather than a fixed letter assumed - these
     * tabs do not agree on how wide they are.
     */
    private static int runColumn(Sheet sheet, CaseSheetLayout.Table table, CellStyle linkStyle) {
        int existing = table.column(RUN_HEADER);
        if (existing >= 0) {
            return existing;
        }
        Row header = sheet.getRow(table.headerRow());
        int column = header.getLastCellNum() < 0 ? 0 : header.getLastCellNum();
        Cell cell = header.createCell(column, CellType.STRING);
        cell.setCellValue(RUN_HEADER);
        cell.setCellStyle(linkStyle);
        sheet.setColumnWidth(column, 2600);
        return column;
    }

    /**
     * Where the case pages are served from, without a trailing slash.
     *
     * <p>The default is IntelliJ's built-in web server, which serves the project at
     * {@code http://localhost:63342/<project>/...} while the IDE is open. Override with
     * {@code -Dtestcases.baseUrl=...} to point at wherever the pages are actually published -
     * a shared server, or a {@code file://} path for someone without the IDE running.
     */
    private static final String CASES_BASE_URL = System.getProperty("testcases.baseUrl",
            "http://localhost:63342/Admin_weibo/docs/test-cases");

    /**
     * Puts a clickable link to the case's own row on its feature page.
     *
     * <h2>Why not {@code fpr://} any more</h2>
     * The link used to be {@code fpr://<id>}, a custom scheme that a registered handler turned
     * into a test run. That only works on a machine where {@code tools/install-url-handler.ps1}
     * has been run, and it does nothing at all for a reader who just wants to see what the case
     * covers. An ordinary {@code http://} link to the feature page works everywhere, and the
     * {@code #id} fragment lands on the row rather than the top of a page of two hundred.
     *
     * <p>The page still carries its own Run control, so nothing is lost: the workbook link opens
     * the case, and the case is one click from being run.
     */
    private static void setRunLink(Row row, int column, String feature, String suiteId,
                                   CreationHelper helper, CellStyle linkStyle) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column, CellType.STRING);
        }
        cell.setCellValue("Run " + suiteId);
        Hyperlink link = helper.createHyperlink(HyperlinkType.URL);
        link.setAddress(CASES_BASE_URL + "/" + feature + ".html#" + suiteId);
        cell.setHyperlink(link);
        cell.setCellStyle(linkStyle);
    }

    /** Blue and underlined, so a link looks like one. Built once per workbook. */
    private static CellStyle linkStyle(Workbook book) {
        Font font = book.createFont();
        font.setUnderline(Font.U_SINGLE);
        font.setColor(IndexedColors.BLUE.getIndex());
        CellStyle style = book.createCellStyle();
        style.setFont(font);
        return style;
    }

    /**
     * Writes a string, keeping whatever style and width the client gave the cell.
     *
     * <h2>Formula cells have to be emptied first</h2>
     * Several tabs compute the ACTUAL RESULT column rather than typing it - Manual Transfer's is a
     * formula on every row. Calling {@code setCellValue} on a formula cell does <b>not</b> replace
     * the formula: POI keeps the cell a FORMULA and treats the string as its cached result, so the
     * file still carries the formula and Excel recomputes it the moment it opens. The write is
     * lost, silently and only on the tabs that happen to compute that column - which is exactly
     * how it was found, with a row that recorded its STATUS and left its ACTUAL RESULT empty.
     *
     * <p>Blanking first drops the formula and makes it an ordinary string cell. That is the point
     * of the column here: an actual result is what was observed, not something derived from what
     * was expected. Only the results copy is touched; the import keeps its formulas.
     */
    private static void set(Row row, int column, String value) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column, CellType.STRING);
        } else if (cell.getCellType() == CellType.FORMULA) {
            cell.setBlank();
        }
        cell.setCellValue(value);
    }
}
