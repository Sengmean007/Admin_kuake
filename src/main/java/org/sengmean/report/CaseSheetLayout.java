package org.sengmean.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Where one tab of the client workbook keeps its table, and how to read a cell out of it.
 *
 * <h2>Why this is its own class</h2>
 * Two things read the workbook - {@link CaseSheetPage}, which renders it, and
 * {@link CaseSheetResults}, which writes a run's outcomes back into a copy of it. They must agree
 * about which row is the header, which column holds the id, and how a sheet id becomes a suite id,
 * or the writer puts a result on a different row than the page linked. That is the same reasoning
 * {@link CaseIndex} gives for reusing the report generator's parser rather than writing a second
 * one: the first symptom of two parsers disagreeing is a result recorded against the wrong case.
 *
 * <h2>What the tabs actually do</h2>
 * They do not agree on much. Some carry three banner rows before the table and some carry seven,
 * so the header is found by looking for {@value #ID_HEADER} rather than assumed. Several - Deposit,
 * WithdrawalMainbalance, Manual Transfer - indent the whole table by a column or two, so the header
 * is looked for anywhere in the row and its own column is taken as the id column. And the Home page
 * tab generates its ids with {@code ="TC_HOME_00"&ROW()-8} instead of typing them, so formulas have
 * to be evaluated or every id on that tab reads back as the formula text.
 */
public final class CaseSheetLayout {

    /** The header cell that marks the start of a tab's rows. */
    public static final String ID_HEADER = "TEST CASE ID";

    private static final DataFormatter FORMATTER = new DataFormatter();

    private CaseSheetLayout() {
    }

    /**
     * A tab's table: which row is the header, which column holds the id, and where the named
     * columns are. {@code headerRow} is -1 when the tab has no table at all.
     */
    public record Table(int headerRow, int idColumn, Map<String, Integer> columns) {

        public boolean found() {
            return headerRow >= 0;
        }

        /**
         * The column index for the first of these headers the tab carries, or -1.
         *
         * <p>Several are accepted because the workbook does not use one vocabulary: four green
         * tabs head the importance column <b>Important Level</b> and four head it <b>RISK
         * LEVEL</b>; four say <b>STATUS(PASS/FAIL)</b> and four say <b>STATUS</b>. Matching only
         * the first spelling of each silently produced an empty column for half the features,
         * which reads as "the client did not fill this in" rather than "this was not looked for".
         */
        public int column(String... headers) {
            for (String header : headers) {
                for (Map.Entry<String, Integer> entry : columns.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(header)) {
                        return entry.getValue();
                    }
                }
            }
            return -1;
        }
    }

    /**
     * True when the client has coloured this tab's <b>tab</b> green, which is how they mark the
     * features that are in scope for automation.
     *
     * <p>It is the tab colour, not any cell highlight - the workbook uses cell fills for pass and
     * fail inside the tables, and reading those instead would pick up something else entirely. As
     * exported today the eight green tabs are Home page, Register, Login, Forgot Password,
     * Activation Account, Deposit, WithdrawalMainbalance and Manual Transfer, which is exactly the
     * set this project has suites for.
     *
     * <p>The comparison is tolerant rather than an equality test against 00FF00: the client picks
     * the colour from a palette in Google Sheets, and a slightly different green chosen next month
     * should not quietly drop a whole feature out of scope.
     */
    public static boolean markedGreen(Sheet sheet) {
        if (!(sheet instanceof XSSFSheet xssf)) {
            return false;
        }
        XSSFColor colour = xssf.getTabColor();
        if (colour == null) {
            return false;
        }
        byte[] rgb = colour.getRGB();
        if (rgb == null || rgb.length < 3) {
            return false;
        }
        int red = rgb[0] & 0xFF;
        int green = rgb[1] & 0xFF;
        int blue = rgb[2] & 0xFF;
        return green > 128 && red < 128 && blue < 128;
    }

    /** Locates the table on a tab. Every header cell it carries is recorded, not just known ones. */
    public static Table of(Sheet sheet, FormulaEvaluator evaluator) {
        int headerRow = -1;
        int idColumn = -1;
        for (Row row : sheet) {
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (ID_HEADER.equalsIgnoreCase(text(cell, evaluator).trim())) {
                    headerRow = row.getRowNum();
                    idColumn = cell.getColumnIndex();
                    break;
                }
            }
            if (headerRow >= 0) {
                break;
            }
        }
        if (headerRow < 0) {
            return new Table(-1, -1, Map.of());
        }

        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Cell cell : sheet.getRow(headerRow)) {
            String name = text(cell, evaluator).trim();
            if (!name.isEmpty()) {
                columns.putIfAbsent(name, cell.getColumnIndex());
            }
        }
        return new Table(headerRow, idColumn, columns);
    }

    /**
     * A cell as text, with formulas evaluated.
     *
     * An evaluation that throws falls back to the unevaluated text rather than failing: this
     * workbook is someone else's and spans forty-three tabs, and one cross-sheet reference POI
     * cannot follow is not a reason to lose the other 6,900 rows.
     */
    public static String text(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        try {
            return FORMATTER.formatCellValue(cell, evaluator);
        } catch (RuntimeException cannotEvaluate) {
            // The fallback can throw too, and used to take the whole run with it: a cell left
            // pointing at a shared formula whose master has gone raises
            // "Master cell of a shared formula with sid=3 was not found" from
            // formatCellValue itself, outside the guard above. One unreadable cell then aborted
            // the results run for every remaining feature. A cell nothing can read is empty as
            // far as a reader is concerned, and that is a great deal better than losing the file.
            try {
                return FORMATTER.formatCellValue(cell);
            } catch (RuntimeException unreadable) {
                return "";
            }
        }
    }

    /**
     * The id the suites use, for the id the workbook uses: {@code TC_LOGIN_001} to
     * {@code LOGIN-001}, {@code TC_HKD_DEPO_0068} to {@code HKDDEPO-0068}.
     *
     * <p>The wrapper is only stripped when it is there, and the module's own underscores are
     * dropped because the suites do not keep them. Keeping them is what made every multi-word
     * module - the whole Deposit and Withdrawal side of the workbook - resolve to nothing while
     * looking merely un-automated.
     *
     * <p>The result is looked up rather than trusted: several suites use a prefix of their own
     * that matches no tab, and an id this cannot convert comes back unchanged and simply fails
     * to resolve, which the page then reports.
     */
    public static String suiteId(String sheetId) {
        String id = sheetId.trim().toUpperCase(Locale.ROOT);
        if (id.startsWith("TC_")) {
            id = id.substring(3);
        }
        int lastUnderscore = id.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            return id;
        }
        return id.substring(0, lastUnderscore).replace("_", "") + "-" + id.substring(lastUnderscore + 1);
    }
}
