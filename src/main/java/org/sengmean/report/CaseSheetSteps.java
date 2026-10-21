package org.sengmean.report;

import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The client workbook's own account of how each case is carried out - its PRE-CONDITION, TEST
 * STEPS and TEST DATA - keyed by the id the suites use.
 *
 * <h2>Why the report reads the workbook for this</h2>
 * The run's step trail says what the automation did, event by event, including every API call the
 * page made. That is the evidence, but it is not a test case: a reader checking the report against
 * the client's sheet wants the numbered steps and the data the sheet specifies, in the sheet's own
 * words. Those already exist in the workbook, so the report quotes them rather than restating them
 * in the test sources, where they would drift from the sheet the first time either was edited.
 *
 * <p>Every tab with a table is read, not only the green ones: a case whose tab is not green still
 * has steps worth showing, and the join is on the id, which is unambiguous either way.
 */
public final class CaseSheetSteps {

    /** One workbook row's description of a case. Blank strings, never null. */
    public record Entry(String precondition, String steps, String data) {

        public boolean isEmpty() {
            return precondition.isBlank() && steps.isBlank() && data.isBlank();
        }
    }

    /**
     * What every report shows for a case: its steps and data, and where they came from.
     *
     * @param precondition the workbook's PRE-CONDITION, blank when it has none
     * @param steps        the workbook's steps, or the run's STEP lines when the workbook has none
     * @param stepsFromRun true when {@code steps} came from the run rather than the workbook
     * @param data         the workbook's TEST DATA
     * @param usedData     the DATA lines the run recorded - the values it actually fed in
     */
    public record Described(String precondition, List<String> steps, boolean stepsFromRun,
                            List<String> data, List<String> usedData) {

        public String stepsText() {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < steps.size(); i++) {
                out.append(i == 0 ? "" : "\n").append(i + 1).append(". ").append(steps.get(i));
            }
            return out.toString();
        }

        public String dataText() {
            StringBuilder out = new StringBuilder(String.join("\n", data));
            if (!usedData.isEmpty()) {
                out.append(out.isEmpty() ? "" : "\n").append("Used in this run:");
                usedData.forEach(line -> out.append("\n- ").append(line));
            }
            return out.toString();
        }
    }

    private CaseSheetSteps() {
    }

    /**
     * The one place that decides what a case's Test steps and Test data are, so the HTML report,
     * the Extent dashboard and the Excel report cannot describe the same case three ways.
     *
     * <p>A case with no workbook row - the project-only 900 block - falls back to the STEP lines
     * of its own run trail, so the steps are never silently blank for a case that did run.
     */
    public static Described describe(Entry planned, List<CaseLog.Entry> trail) {
        List<String> steps = planned == null ? List.of() : lines(planned.steps());
        boolean fromRun = false;
        if (steps.isEmpty()) {
            steps = trailLines(trail, CaseLog.STEP);
            fromRun = !steps.isEmpty();
        }
        List<String> data = planned == null ? List.of() : lines(planned.data());
        String precondition = planned == null ? "" : planned.precondition();
        return new Described(precondition, steps, fromRun, data, trailLines(trail, CaseLog.DATA));
    }

    /** The trail's lines of one kind, in order, each once - a value logged on every retry is shown once. */
    static List<String> trailLines(List<CaseLog.Entry> trail, String kind) {
        if (trail == null) {
            return List.of();
        }
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (CaseLog.Entry entry : trail) {
            if (kind.equals(entry.kind()) && !entry.text().startsWith("Case started")) {
                lines.add(entry.text().trim());
            }
        }
        return new ArrayList<>(lines);
    }

    /**
     * The id a case carries at the start of its display name, "OC-LST-003 - ..." to "OC-LST-003",
     * read with the report's own {@link HtmlReportGenerator#CASE_ID} so it matches the Id column.
     */
    public static String idOf(String displayName) {
        var match = HtmlReportGenerator.CASE_ID.matcher(displayName == null ? "" : displayName.trim());
        return match.find() ? match.group(1).replaceAll("\\s*/\\s*", " / ") : "";
    }

    /**
     * Reads every row that carries an id, keyed by {@link CaseSheetLayout#suiteId}.
     *
     * <p>A workbook that is missing or unreadable gives an empty map and a console line rather
     * than an exception: the report is written at the end of a run, and a report without the
     * sheet's steps is a great deal better than no report.
     */
    public static Map<String, Entry> read(Path workbook) {
        Map<String, Entry> byId = new HashMap<>();
        if (!Files.isRegularFile(workbook)) {
            System.out.println("No client workbook at " + workbook.toAbsolutePath()
                    + " - the report's Test steps and Test data columns will show the run's own trail.");
            return byId;
        }
        try (Workbook book = WorkbookFactory.create(workbook.toFile(), null, true)) {
            FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
            for (Sheet sheet : book) {
                CaseSheetLayout.Table table = CaseSheetLayout.of(sheet, evaluator);
                if (!table.found()) {
                    continue;
                }
                int pre = table.column("PRE-CONDITION", "PRECONDITION", "PRE CONDITION");
                int steps = table.column("TEST STEPS", "TEST STEP", "STEPS");
                int data = table.column("TEST DATA");
                if (steps < 0 && data < 0) {
                    continue;
                }
                for (int r = table.headerRow() + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    String id = CaseSheetLayout.text(row.getCell(table.idColumn()), evaluator).trim();
                    if (id.isEmpty()) {
                        continue;
                    }
                    Entry entry = new Entry(cell(row, pre, evaluator), cell(row, steps, evaluator),
                            cell(row, data, evaluator));
                    if (!entry.isEmpty()) {
                        byId.putIfAbsent(CaseSheetLayout.suiteId(id), entry);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Could not read test steps from " + workbook + ": " + e.getMessage());
        }
        return byId;
    }

    /**
     * A multi-line cell as its items, with the sheet's own "1." / "2)" numbering taken off so the
     * report can number them itself. In a numbered cell an unnumbered line continues the step
     * above it, so a step the client wrapped onto a second line is not counted as two.
     */
    public static List<String> lines(String cell) {
        List<String> out = new ArrayList<>();
        if (cell == null) {
            return out;
        }
        boolean numbered = NUMBERED.matcher(cell.trim()).lookingAt();
        for (String line : cell.split("\\r?\\n")) {
            String text = line.trim();
            if (text.isEmpty()) {
                continue;
            }
            var number = NUMBERED.matcher(text);
            if (number.lookingAt()) {
                out.add(text.substring(number.end()).trim());
            } else if (numbered && !out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + " " + text);
            } else {
                out.add(text);
            }
        }
        return out;
    }

    private static final Pattern NUMBERED = Pattern.compile("\\d+[.)](?:\\s+|$)");

    private static String cell(Row row, int column, FormulaEvaluator evaluator) {
        return column < 0 ? "" : CaseSheetLayout.text(row.getCell(column), evaluator).trim();
    }
}
