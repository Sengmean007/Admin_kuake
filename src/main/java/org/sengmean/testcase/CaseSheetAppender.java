package org.sengmean.testcase;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellCopyPolicy;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sengmean.report.CaseSheetLayout;
import org.sengmean.report.CaseSheetPage;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adds the project's own test-case rows to a client workbook, each at the end of its tab's section.
 *
 * <h2>Why a tool, and not a spreadsheet edit</h2>
 * Cases Si asks for, or that are added while fixing a feature, take the next id after the tab's last real
 * row ({@code NOT-CRT-028} after {@code NOT-CRT-027}) and exist first in the Java suite. The client's
 * workbook is a copy of the client's own sheet, so every re-export loses whatever was typed into the copy -
 * the same reason {@link CaseSheetTrimmer} exists. The rows therefore live in a committed TSV
 * ({@code src/test/resources/testdata/added-cases/<feature>.tsv}) and this tool writes them into any
 * workbook with that tab, repeatably: the project's {@code client_testcase.xlsx}, and the copy Si keeps.
 *
 * <pre>
 * ./mvnw exec:java@append-cases                                    the project workbook
 * ./mvnw exec:java@append-cases -Dexec.args="C:/path/other.xlsx"   another copy of the same tab
 * ./mvnw exec:java@split-cases                                     then the usual resplit
 * ./mvnw exec:java@case-page                                       and the docs/test-cases page
 * </pre>
 *
 * <p>Safe to run twice: a row whose id the tab already carries is left alone and reported - unless the run
 * carries {@code -Dcases.update=true}, when such a row's text cells are rewritten from the TSV where they
 * differ (added 2026-10-01, when {@code NOT-CRT-033}'s expected result changed after it was in the workbook).
 * The TSV is the source of truth for a project-added row either way; the workbook row only ever follows it.
 *
 * <h2>The TSV</h2>
 * Tab-separated, one case per line, UTF-8, a header line first. Columns, matching the tab's headers:
 * {@code SHEET, TEST CASE ID, TEST SCENARIO, TEST CASE, PRE-CONDITION, TEST STEPS, TEST DATA,
 * EXPECTED RESULTS, POST CONDITION, Important Level, VERSION, LAST UPDATE, CASE TYPE}. A literal
 * {@code \n} in a value becomes a line break, as the client writes numbered steps.
 *
 * <h2>Where a row lands</h2>
 * After the last existing row whose id shares the new row's prefix ({@code NOT-CRT-}), so a section keeps
 * its order and its heading; rows are inserted in id order, so {@code NOT-CRT-029} lands after {@code 028}
 * and {@code 030} after {@code 029}. The row above lends every cell its style, so the new row looks like
 * the tab's own. The LAST UPDATE value is written as a date when the row above holds one.
 */
public final class CaseSheetAppender {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private static final String[] TEXT_COLUMNS = {
            "TEST SCENARIO", "TEST CASE", "PRE-CONDITION", "TEST STEPS", "TEST DATA", "EXPECTED RESULTS",
            "POST CONDITION", "Important Level", "VERSION", "CASE TYPE"};

    /** {@code -Dcases.update=true}: rewrite rows the workbook already carries from their TSV line. */
    private static final boolean UPDATE = Boolean.getBoolean("cases.update");

    private CaseSheetAppender() {
    }

    public static void main(String[] args) throws Exception {
        Path workbook = args.length > 0 ? Path.of(args[0]) : CaseSheetPage.workbook();
        Path rows = args.length > 1 ? Path.of(args[1])
                : Path.of(System.getProperty("cases.added", "src/test/resources/testdata/added-cases/notification.tsv"));
        append(workbook, rows);
    }

    /** One row of the TSV: the tab it belongs on, its id, and its values by header. */
    public record Added(String sheet, String id, Map<String, String> values) {
    }

    /**
     * Writes every row of {@code tsv} that the workbook does not yet carry, in place.
     *
     * @return how many rows were added
     */
    public static int append(Path workbook, Path tsv) throws Exception {
        if (!Files.isRegularFile(workbook)) {
            System.out.println("No workbook at " + workbook + " - nothing to add to.");
            return 0;
        }
        List<Added> added = read(tsv);
        if (added.isEmpty()) {
            System.out.println("No rows in " + tsv + " - nothing to add.");
            return 0;
        }

        Workbook wb;
        try (InputStream in = Files.newInputStream(workbook)) {
            wb = new XSSFWorkbook(in);
        }
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

        int written = 0;
        int kept = 0;
        for (Added row : added) {
            String prefix = row.id().substring(0, row.id().lastIndexOf('-') + 1).toUpperCase(Locale.ROOT);
            Sheet sheet = tabFor(wb, row.sheet(), prefix, evaluator);
            if (sheet == null) {
                System.out.println("  " + row.id() + ": no tab named \"" + row.sheet() + "\" and none carrying " + prefix + " ids - skipped");
                continue;
            }
            CaseSheetLayout.Table table = CaseSheetLayout.of(sheet, evaluator);
            Map<String, Integer> ids = idsOn(sheet, table, evaluator);
            if (ids.containsKey(row.id().toUpperCase(Locale.ROOT))) {
                int at = ids.get(row.id().toUpperCase(Locale.ROOT));
                if (UPDATE && refresh((XSSFSheet) sheet, at, table, row, evaluator)) {
                    written++;
                    System.out.printf("  %-14s updated on \"%s\" at row %d%n", row.id(), sheet.getSheetName(), at + 1);
                } else {
                    kept++;
                }
                continue;
            }
            int anchor = -1;
            for (Map.Entry<String, Integer> entry : ids.entrySet()) {
                if (entry.getKey().startsWith(prefix) && entry.getValue() > anchor) {
                    anchor = entry.getValue();
                }
            }
            if (anchor < 0) {
                System.out.println("  " + row.id() + ": tab \"" + row.sheet() + "\" has no " + prefix + " row to add after - skipped");
                continue;
            }
            insertAfter((XSSFSheet) sheet, anchor, table, row);
            written++;
            System.out.printf("  %-14s added on \"%s\" at row %d%n", row.id(), sheet.getSheetName(), anchor + 2);
        }

        if (written == 0) {
            System.out.println("Every row of " + tsv + " is already in " + workbook + " (" + kept + " kept"
                    + (UPDATE ? ", none differing from its TSV line" : "") + ") - nothing to do.");
            wb.close();
            return 0;
        }
        // As CaseSheetTrimmer: Excel recalculates on open; the export carries formulas POI cannot evaluate.
        wb.setForceFormulaRecalculation(true);
        try (OutputStream out = Files.newOutputStream(workbook)) {
            wb.write(out);
        }
        wb.close();
        System.out.println((UPDATE ? "Added or updated " : "Added ") + written + " rows in " + workbook + (kept > 0 ? " (" + kept + " already there)" : ""));
        return written;
    }

    /**
     * Rewrites an existing row's text cells and LAST UPDATE from its TSV line, where they differ - the
     * {@code -Dcases.update=true} path. Styles are untouched. Returns whether anything was changed.
     */
    private static boolean refresh(XSSFSheet sheet, int at, CaseSheetLayout.Table table, Added row, FormulaEvaluator evaluator) {
        XSSFRow existing = sheet.getRow(at);
        if (existing == null) {
            return false;
        }
        boolean changed = false;
        for (String header : TEXT_COLUMNS) {
            int column = table.column(header);
            if (column >= 0 && row.values().containsKey(header)
                    && !row.values().get(header).equals(text(existing.getCell(column), evaluator))) {
                set(existing, column, row.values().get(header));
                changed = true;
            }
        }
        if (changed) {
            setLastUpdate(existing, table, row);
        }
        return changed;
    }

    /**
     * The tab the row belongs on: the one named in the TSV when the workbook has it, else the first tab whose
     * id column carries the row's prefix. The client's own feature workbook keeps the same rows on a tab named
     * "Test Cases", while the project's import names the tab after the feature.
     */
    private static Sheet tabFor(Workbook wb, String named, String prefix, FormulaEvaluator evaluator) {
        Sheet sheet = wb.getSheet(named);
        if (sheet != null && CaseSheetLayout.of(sheet, evaluator).found()) {
            return sheet;
        }
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet candidate = wb.getSheetAt(i);
            CaseSheetLayout.Table table = CaseSheetLayout.of(candidate, evaluator);
            if (table.found() && idsOn(candidate, table, evaluator).keySet().stream().anyMatch(id -> id.startsWith(prefix))) {
                return candidate;
            }
        }
        return null;
    }

    /** Every id on the tab, upper-cased, with its 0-based row index. */
    private static Map<String, Integer> idsOn(Sheet sheet, CaseSheetLayout.Table table, FormulaEvaluator evaluator) {
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String id = text(row.getCell(table.idColumn()), evaluator);
            if (!id.isEmpty()) {
                ids.put(id.toUpperCase(Locale.ROOT), r);
            }
        }
        return ids;
    }

    /** Makes room below {@code anchor}, copies its styles, and fills the new row. */
    private static void insertAfter(XSSFSheet sheet, int anchor, CaseSheetLayout.Table table, Added row) {
        int target = anchor + 1;
        if (target <= sheet.getLastRowNum()) {
            sheet.shiftRows(target, sheet.getLastRowNum(), 1, true, false);
        }
        XSSFRow source = sheet.getRow(anchor);
        XSSFRow created = sheet.createRow(target);
        CellCopyPolicy styleOnly = new CellCopyPolicy.Builder().cellValue(false).cellStyle(true).cellFormula(false)
                .copyHyperlink(false).mergedRegions(false).rowHeight(true).build();
        created.copyRowFrom(source, styleOnly);

        set(created, table.idColumn(), row.id());
        for (String header : TEXT_COLUMNS) {
            int column = table.column(header);
            if (column >= 0 && row.values().containsKey(header)) {
                set(created, column, row.values().get(header));
            }
        }
        setLastUpdate(created, table, row);
    }

    /** LAST UPDATE from the TSV line (today when it has none), as a date where it parses as one. */
    private static void setLastUpdate(XSSFRow target, CaseSheetLayout.Table table, Added row) {
        int updated = table.column("LAST UPDATE");
        if (updated < 0) {
            return;
        }
        String value = row.values().getOrDefault("LAST UPDATE", LocalDate.now().toString());
        Cell cell = target.getCell(updated) != null ? target.getCell(updated) : target.createCell(updated);
        try {
            cell.setCellValue(LocalDateTime.of(LocalDate.parse(value), java.time.LocalTime.MIDNIGHT));
        } catch (RuntimeException notADate) {
            cell.setCellValue(value);
        }
    }

    private static void set(Row row, int column, String value) {
        Cell cell = row.getCell(column) != null ? row.getCell(column) : row.createCell(column);
        cell.setCellValue(value);
    }

    private static String text(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        try {
            return FORMATTER.formatCellValue(cell, evaluator).trim();
        } catch (Exception cannotEvaluate) {
            return FORMATTER.formatCellValue(cell).trim();
        }
    }

    /** The TSV as rows; a literal backslash-n in a value is a line break. */
    static List<Added> read(Path tsv) throws Exception {
        List<Added> rows = new ArrayList<>();
        if (!Files.isRegularFile(tsv)) {
            System.out.println("No added-cases file at " + tsv);
            return rows;
        }
        List<String> lines = Files.readAllLines(tsv, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return rows;
        }
        String[] headers = lines.get(0).split("\t", -1);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("\t", -1);
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < headers.length && c < cells.length; c++) {
                values.put(headers[c].trim(), cells[c].replace("\\n", "\n").trim());
            }
            String sheet = values.getOrDefault("SHEET", "");
            String id = values.getOrDefault("TEST CASE ID", "");
            if (sheet.isEmpty() || id.isEmpty()) {
                System.out.println("  line " + (i + 1) + " of " + tsv.getFileName() + " has no SHEET or TEST CASE ID - skipped");
                continue;
            }
            rows.add(new Added(sheet, id, values));
        }
        rows.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        return rows;
    }
}
