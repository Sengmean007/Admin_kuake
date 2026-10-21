package org.sengmean.testcase;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sengmean.report.CaseSheetLayout;
import org.sengmean.report.CaseSheetPage;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deletes the <b>TP</b> rows from the imported client test case workbook, in place.
 *
 * <h2>What it removes, and why it is a tool rather than a one-off edit</h2>
 * Every row whose id begins {@code TC_TP_} - {@code TC_TP_DEPO}, {@code TC_TP_WITHDRA},
 * {@code TC_TP_TRANSFER} and their siblings on the tabs this project does not report on. Si asked
 * for them gone on 2026-09-11; they are out of scope for this suite and their presence made the
 * cashier tabs read as three times the work they are.
 *
 * <h2>Read this before re-exporting the client's sheet</h2>
 * {@code src/test/resources/testdata/client_testcase.xlsx} is a <b>copy</b> of the client's Google
 * Sheet, and the sheet is the original. The TP rows are still in the original and always will be,
 * so <b>a fresh export brings all 244 of them straight back</b>, silently and without any error to
 * notice. That is the known cost of deleting rows rather than filtering them when the workbook is
 * read, and it is why this exists as a committed, repeatable tool rather than as a spreadsheet edit
 * somebody did once:
 *
 * <pre>
 * ./mvnw exec:java@trim-cases     right after re-exporting, before anything else
 * ./mvnw exec:java@split-cases    then the usual resplit
 * </pre>
 *
 * <p>It is safe to run twice. A workbook with no TP rows left is reported as such and rewritten
 * unchanged, so there is no harm in running it whenever you are unsure.
 *
 * <h2>How rows are deleted</h2>
 * Bottom-up, one contiguous block at a time: remove the block, then shift everything below it up by
 * its height. Bottom-up because every index below a deletion moves, so working downwards would
 * have each block delete rows it had already moved - the classic way this goes quietly wrong and
 * takes the wrong rows out.
 *
 * <p>Merged regions that overlap a deleted block are dropped first. POI will otherwise carry a
 * merge across the shift into rows it was never meant to cover, and the symptom is a workbook Excel
 * calls repairable rather than an exception here.
 *
 * <h2>The ids are frozen to literals before anything is deleted, and this is the important part</h2>
 * Four tabs - Deposit, WithdrawalMainbalance, Manual Transfer and Game Launch - do not type their
 * ids. They generate them from the row number, in the shape {@code ="TC_HKD_DEPO_00"&ROW()-8}. So
 * deleting a row above one of them <b>changes its id</b>, and the first version of this tool did
 * exactly that: it removed the right 244 rows and silently renumbered <b>337 of the survivors</b>,
 * turning {@code TC_TRX_TRANSFER_0016} into {@code TC_TRX_TRANSFER_00-11}. Nothing failed. The
 * workbook opened cleanly. Every Run link on three green tabs simply pointed at a different case
 * than the row it sat on.
 *
 * <p>So every formula id on a tab about to be trimmed is replaced by its current value first, while
 * the row numbers still mean what the formula assumed. After that the ids are text and no amount of
 * shifting can move them. This does change the workbook in a second way beyond deleting rows - the
 * ids stop being formulas - and that is the intended outcome rather than a side effect: a case id
 * is meant to be a stable name for a case, not a function of where the case currently sits.
 */
public final class CaseSheetTrimmer {

    /** The prefix that marks a row as out of scope. Upper-cased before comparison. */
    private static final String PREFIX = "TC_TP_";

    private static final DataFormatter FORMATTER = new DataFormatter();

    private CaseSheetTrimmer() {
    }

    public static void main(String[] args) throws Exception {
        Path workbook = args.length > 0 ? Path.of(args[0]) : CaseSheetPage.workbook();
        trim(workbook);
    }

    /**
     * Removes every {@value #PREFIX} row from {@code source}, rewriting it in place.
     *
     * @return how many rows were removed across every tab
     */
    public static int trim(Path source) throws Exception {
        if (!Files.isRegularFile(source)) {
            System.out.println("No client workbook at " + source + " - nothing to trim.");
            return 0;
        }

        Workbook wb;
        try (InputStream in = Files.newInputStream(source)) {
            wb = new XSSFWorkbook(in);
        }

        int removed = 0;
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

        for (int index = 0; index < wb.getNumberOfSheets(); index++) {
            Sheet sheet = wb.getSheetAt(index);
            CaseSheetLayout.Table table = CaseSheetLayout.of(sheet, evaluator);
            if (!table.found()) {
                continue;
            }

            List<Integer> rows = outOfScopeRows(sheet, table, evaluator);
            if (rows.isEmpty()) {
                continue;
            }

            // Before a single row moves. See the class comment: on four tabs the id is computed
            // from ROW(), so deleting above one rewrites it.
            int frozen = freezeIds(sheet, table, evaluator);

            int before = rows.size();
            deleteRows(sheet, rows);
            removed += before;

            // The group heading the removed rows sat under, once nothing is left under it.
            int heading = removeEmptyHeading(sheet, table, evaluator);
            removed += heading;

            System.out.printf("  %-34s removed %3d %s rows%s%s%n",
                    sheet.getSheetName(), before, PREFIX,
                    heading > 0 ? " + its empty TP heading" : "",
                    frozen > 0 ? " (froze " + frozen + " formula ids first)" : "");
        }

        if (removed == 0) {
            System.out.println("No " + PREFIX + " rows left in " + source + " - nothing to do.");
            wb.close();
            return 0;
        }

        // Formulas are re-evaluated by Excel on open rather than here. The export carries
        // Google Sheets artifacts - __xludf.DUMMYFUNCTION wrappers, and a DATEDIF that POI cannot
        // evaluate at all - so asking POI to recalculate would fail on tabs that have nothing to
        // do with the rows being removed.
        wb.setForceFormulaRecalculation(true);

        try (OutputStream out = Files.newOutputStream(source)) {
            wb.write(out);
        }
        wb.close();

        System.out.println("Removed " + removed + " " + PREFIX + " rows from " + source);
        System.out.println("Now run ./mvnw exec:java@split-cases to rebuild the per-feature workbooks.");
        return removed;
    }

    /**
     * Replaces every formula id on this tab with the text it currently produces.
     *
     * <p>Only the id column, and only on a tab that is about to lose rows. Freezing a whole
     * workbook of formulas would rewrite tabs this tool has no business touching, and the export
     * carries formulas - {@code DATEDIF}, Google's {@code __xludf.DUMMYFUNCTION} - that POI cannot
     * evaluate at all, so it could not do so honestly even if it wanted to.
     *
     * @return how many cells were frozen
     */
    private static int freezeIds(Sheet sheet, CaseSheetLayout.Table table, FormulaEvaluator evaluator) {
        int frozen = 0;
        for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(table.idColumn());
            if (cell == null || cell.getCellType() != CellType.FORMULA) {
                continue;
            }
            String value;
            try {
                value = FORMATTER.formatCellValue(cell, evaluator).trim();
            } catch (Exception cannotEvaluate) {
                // The cached value Google wrote on export. Better than the formula text, and the
                // only answer available for a function POI does not implement.
                value = FORMATTER.formatCellValue(cell).trim();
            }
            // setBlank() first, and it is not optional. Calling setCellValue(String) on a cell
            // that still holds a formula only replaces POI's *cached result* - the formula stays,
            // Excel recomputes it on open, and the id moves anyway. That is exactly how the
            // second attempt at this tool failed: it reported freezing 263 ids and renumbered
            // every one of them regardless. Blanking drops the formula, and the assignment after
            // it lands on a plain text cell.
            cell.setBlank();
            cell.setCellValue(value);
            frozen++;
        }
        return frozen;
    }

    /**
     * Removes the tab's bare {@code TP} group heading, if it now heads nothing.
     *
     * <p>Several tabs separate their currency blocks with a one-word label row - {@code TP},
     * {@code HKD}, {@code THB} - carried in the id column. A {@code TP} label left behind after its
     * block has gone is visible residue of this trim rather than a case, so it goes too. Guarded on
     * no {@value #PREFIX} row remaining, which is what keeps it from touching a tab whose TP rows
     * are named some other way and are still there.
     *
     * @return 1 if a heading was removed, 0 otherwise
     */
    private static int removeEmptyHeading(Sheet sheet, CaseSheetLayout.Table table,
                                          FormulaEvaluator evaluator) {
        if (!outOfScopeRows(sheet, table, evaluator).isEmpty()) {
            return 0;
        }
        for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            if ("TP".equals(id(row.getCell(table.idColumn()), evaluator))) {
                removeBlock(sheet, r, r);
                return 1;
            }
        }
        return 0;
    }

    /** The 0-based indices of this tab's out-of-scope rows, in ascending order. */
    private static List<Integer> outOfScopeRows(Sheet sheet, CaseSheetLayout.Table table,
                                                FormulaEvaluator evaluator) {
        List<Integer> rows = new ArrayList<>();
        for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            if (id(row.getCell(table.idColumn()), evaluator).startsWith(PREFIX)) {
                rows.add(r);
            }
        }
        return rows;
    }

    /**
     * A cell as text, upper-cased, tolerating a formula POI cannot evaluate.
     *
     * <p>The export carries {@code DATEDIF} and Google's {@code __xludf.DUMMYFUNCTION} wrapper,
     * neither of which POI implements, and an id column on one tab is generated by formula. Falling
     * back to the cached value rather than throwing is what lets this walk every tab - the
     * alternative is a tool that dies on a tab with no TP rows on it at all.
     */
    private static String id(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        String text;
        try {
            text = FORMATTER.formatCellValue(cell, evaluator);
        } catch (Exception cannotEvaluate) {
            text = FORMATTER.formatCellValue(cell);
        }
        return text.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Removes the given rows and closes the gaps, working from the bottom of the sheet up.
     *
     * @param rows ascending row indices; consumed as contiguous blocks
     */
    private static void deleteRows(Sheet sheet, List<Integer> rows) {
        // Walk backwards, gathering each run of consecutive indices into one block so a run of
        // sixty-six rows costs one shift rather than sixty-six.
        int end = rows.size() - 1;
        while (end >= 0) {
            int start = end;
            while (start > 0 && rows.get(start - 1) == rows.get(start) - 1) {
                start--;
            }
            int first = rows.get(start);
            int last = rows.get(end);
            removeBlock(sheet, first, last);
            end = start - 1;
        }
    }

    private static void removeBlock(Sheet sheet, int first, int last) {
        dropMergesOver(sheet, first, last);

        for (int r = first; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                sheet.removeRow(row);
            }
        }

        int height = last - first + 1;
        if (last < sheet.getLastRowNum()) {
            sheet.shiftRows(last + 1, sheet.getLastRowNum(), -height);
        }
    }

    /**
     * Drops merged regions that overlap the rows about to go.
     *
     * <p>Backwards, because removing a region renumbers every one after it - the same reason the
     * rows themselves are deleted bottom-up.
     */
    private static void dropMergesOver(Sheet sheet, int first, int last) {
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() <= last && region.getLastRow() >= first) {
                sheet.removeMergedRegion(i);
            }
        }
    }
}
