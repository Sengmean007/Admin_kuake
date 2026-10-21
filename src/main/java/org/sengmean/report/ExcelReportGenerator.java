package org.sengmean.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Turns the Surefire XML results into a formatted Excel workbook.
 * Run after the suite:
 *   mvn test
 *   mvn exec:java
 * Output defaults to target/E2E-Test-Report.xlsx and can be redirected with -Dreport.out=...
 */
public class ExcelReportGenerator {

    /** One executed test case. */
    record TestResult(String suite, String name, String status, double seconds, String details) {}

    private static final String PASSED = "PASSED";
    private static final String FAILED = "FAILED";
    private static final String ERROR = "ERROR";
    private static final String SKIPPED = "SKIPPED";

    public static void main(String[] args) throws Exception {
        Path reportsDir = Path.of(System.getProperty("report.in", "target/surefire-reports"));
        Path output = Path.of(System.getProperty("report.out", "target/E2E-Test-Report.xlsx"));

        if (!Files.isDirectory(reportsDir)) {
            throw new IllegalStateException("No Surefire results at " + reportsDir.toAbsolutePath()
                    + ". Run 'mvn test' first.");
        }

        List<TestResult> results = readResults(reportsDir);
        if (results.isEmpty()) {
            throw new IllegalStateException("No test cases found in " + reportsDir.toAbsolutePath());
        }

        write(results, output, CaseDetails.load());
        System.out.println("Report written to " + output.toAbsolutePath() + " (" + results.size() + " tests)");
    }

    // ---------- reading Surefire XML ----------

    /** Reads all Surefire XML files in the given directory and returns a list of test results. */
    private static List<TestResult> readResults(Path reportsDir) throws Exception {
        List<TestResult> results = new ArrayList<>();
        try (Stream<Path> files = Files.list(reportsDir)) {
            List<Path> xml = files
                    .filter(p -> p.getFileName().toString().startsWith("TEST-"))
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
            for (Path file : xml) {
                results.addAll(readSuite(file));
            }
        }
        // By package area first (auth, product, rebateprogram, tool), as the HTML index and the Extent
        // dashboard group them, then by suite and case.
        results.sort(Comparator.comparing((TestResult r) -> areaOf(r.suite())).thenComparing(TestResult::suite)
                .thenComparing(TestResult::name));
        return results;
    }

    /** Reads a single Surefire XML file and returns the test results it contains. */
    private static List<TestResult> readSuite(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(file.toFile());

        String suite = doc.getDocumentElement().getAttribute("name");
        List<TestResult> results = new ArrayList<>();

        NodeList cases = doc.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element testCase = (Element) cases.item(i);
            String name = testCase.getAttribute("name");
            double seconds = parseSeconds(testCase.getAttribute("time"));

            String status = PASSED;
            String details = "";
            Element failure = firstChild(testCase, "failure");
            Element error = firstChild(testCase, "error");
            Element skipped = firstChild(testCase, "skipped");

            if (failure != null) {
                status = FAILED;
                details = summarise(failure);
            } else if (error != null) {
                status = ERROR;
                details = summarise(error);
            } else if (skipped != null) {
                status = SKIPPED;
                details = skipped.getAttribute("message");
            }
            results.add(new TestResult(suite, name, status, seconds, details));
        }
        return results;
    }

    /** Returns the first child element with the given tag, or null if none. */
    private static Element firstChild(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getParentNode() == parent) {
                return (Element) node;
            }
        }
        return null;
    }

    /** Keeps the message and the first stack frame; full traces belong in the Surefire output. */
    private static String summarise(Element failure) {
        String message = failure.getAttribute("message");
        if (message == null || message.isBlank()) {
            String text = failure.getTextContent();
            message = text == null ? "" : text.lines().findFirst().orElse("");
        }
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 500 ? message.substring(0, 500) + "..." : message;
    }

    /** Parses a time string in seconds, e.g. "0.123" or "1,234.567". Returns 0 on error. */
    private static double parseSeconds(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (RuntimeException e) {
            return 0d;
        }
    }

    // ---------- writing the workbook ----------

    /** Writes the test results to an Excel workbook at the given path. */
    private static void write(List<TestResult> results, Path output, CaseDetails details) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            writeSummarySheet(workbook, styles, results);
            writeResultsSheet(workbook, styles, results, details);

            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileOutputStream out = new FileOutputStream(output.toFile())) {
                workbook.write(out);
            }
        }
    }

    /** Writes the summary sheet with overall metrics and environment info. */
    private static void writeSummarySheet(Workbook workbook, Styles styles, List<TestResult> results) {
        Sheet sheet = workbook.createSheet("Summary");
        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 12000);

        int row = 0;
        Cell title = sheet.createRow(row++).createCell(0);
        title.setCellValue("End-to-End Test Report");
        title.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        row++;

        row = keyValue(sheet, styles, row, "Project", "Admin_weibo (Playwright + JUnit 5)");
        row = keyValue(sheet, styles, row, "Environment",
                System.getProperty("base.url", "not recorded"));
        row = keyValue(sheet, styles, row, "Feature under test", "Weibo Empire admin back office");
        row = keyValue(sheet, styles, row, "Test account", System.getProperty("test.username", "not recorded"));
        row = keyValue(sheet, styles, row, "Browser", "Chromium (headless)");
        row = keyValue(sheet, styles, row, "Executed",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        row++;

        long total = results.size();
        long passed = results.stream().filter(r -> r.status().equals(PASSED)).count();
        long failed = results.stream().filter(r -> r.status().equals(FAILED)).count();
        long errored = results.stream().filter(r -> r.status().equals(ERROR)).count();
        long skipped = results.stream().filter(r -> r.status().equals(SKIPPED)).count();
        double duration = results.stream().mapToDouble(TestResult::seconds).sum();

        Row header = sheet.createRow(row++);
        cell(header, 0, "Metric", styles.header);
        cell(header, 1, "Value", styles.header);

        row = keyValue(sheet, styles, row, "Total tests", String.valueOf(total));
        row = keyValue(sheet, styles, row, "Passed", String.valueOf(passed));
        row = keyValue(sheet, styles, row, "Failed", String.valueOf(failed));
        row = keyValue(sheet, styles, row, "Errors", String.valueOf(errored));
        row = keyValue(sheet, styles, row, "Skipped", String.valueOf(skipped));
        row = keyValue(sheet, styles, row, "Pass rate",
                total == 0 ? "n/a" : String.format("%.1f%%", 100.0 * passed / total));
        keyValue(sheet, styles, row, "Total duration", String.format("%.1f s", duration));
    }

    /** Writes a key-value pair to the given row and returns the next row index. */
    private static int keyValue(Sheet sheet, Styles styles, int rowIndex, String key, String value) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, key, styles.label);
        cell(row, 1, value, styles.text);
        return rowIndex + 1;
    }

    /** Writes the detailed test results sheet with one row per test case. */
    private static void writeResultsSheet(Workbook workbook, Styles styles, List<TestResult> results,
                                          CaseDetails details) {
        Sheet sheet = workbook.createSheet("Test Results");
        String[] headers = {"#", "Package", "Test Suite", "Case ID", "Test Case", "Pre-condition", "Test Steps",
                "Test Data", "Status", "Duration (s)", "Details"};
        int[] widths = {1500, 4200, 7000, 3800, 11000, 12000, 16000, 12000, 3200, 3600, 20000};

        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            cell(header, i, headers[i], styles.header);
            sheet.setColumnWidth(i, widths[i]);
        }

        int rowIndex = 1;
        for (TestResult result : results) {
            Row row = sheet.createRow(rowIndex);
            cell(row, 0, String.valueOf(rowIndex), styles.text);
            cell(row, 1, HtmlReportGenerator.areaTitle(areaOf(result.suite())), styles.text);
            cell(row, 2, shortSuite(result.suite()), styles.text);
            String key = CaseDetails.key(result.suite(), result.name());
            CaseSheetSteps.Described described = details.describe(key);
            cell(row, 3, details.id(key), styles.text);
            cell(row, 4, result.name(), styles.text);
            cell(row, 5, described.precondition(), styles.wrapped);
            cell(row, 6, described.stepsText(), styles.wrapped);
            cell(row, 7, described.dataText(), styles.wrapped);
            cell(row, 8, result.status(), styles.forStatus(result.status()));

            Cell durationCell = row.createCell(9);
            durationCell.setCellValue(Math.round(result.seconds() * 10) / 10.0);
            durationCell.setCellStyle(styles.number);

            cell(row, 10, result.details(), styles.wrapped);
            rowIndex++;
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, headers.length - 1));
    }

    /**
     * What the Case ID, Pre-condition, Test Steps and Test Data columns are read from: the case ids declared in
     * the test sources, the client workbook, and the run's step trails - the same three sources the
     * HTML report joins, through the same {@link CaseSheetSteps#describe}.
     *
     * <p>Surefire names a case by its method and its class ({@code Outer$Inner}), so everything is
     * keyed {@code Outer#method}, which is the key the trails and the HTML report already use.
     */
    record CaseDetails(Map<String, String> ids, Map<String, List<CaseLog.Entry>> logs,
                       Map<String, CaseSheetSteps.Entry> sheet) {

        static CaseDetails load() {
            Map<String, String> ids = new HashMap<>();
            try {
                for (HtmlReportGenerator.SuiteReport suite
                        : HtmlReportGenerator.readSpecs(HtmlReportGenerator.testSrc())) {
                    suite.specs.forEach(spec -> ids.put(spec.key(), spec.id));
                }
            } catch (IOException | RuntimeException e) {
                System.out.println("Could not read the case ids from the test sources: " + e.getMessage());
            }
            return new CaseDetails(ids, HtmlReportGenerator.readStepLogs(CaseLog.dir()),
                    CaseSheetSteps.read(CaseSheetPage.workbook()));
        }

        static String key(String suite, String method) {
            String simple = shortSuite(suite);
            int nested = simple.indexOf('$');
            return (nested >= 0 ? simple.substring(0, nested) : simple) + "#" + method;
        }

        String id(String key) {
            return ids.getOrDefault(key, "");
        }

        CaseSheetSteps.Described describe(String key) {
            String id = id(key);
            return CaseSheetSteps.describe(id.isEmpty() ? null : sheet.get(id), logs.get(key));
        }
    }

    /** The package area of a Surefire suite name: "org.sengmean.tests.product.OtherCategoryTest\" to "product". */
    static String areaOf(String suite) {
        int dot = suite.lastIndexOf('.');
        return HtmlReportGenerator.area(dot < 0 ? "" : suite.substring(0, dot));
    }

    /** Returns the last segment of a suite name, e.g. "org.sengmean.tests.record.RecordPageTest" -> "RecordPageTest". */
    private static String shortSuite(String suite) {
        int dot = suite.lastIndexOf('.');
        return dot >= 0 ? suite.substring(dot + 1) : suite;
    }

    /** Creates a cell in the given row and column with the specified value and style. */
    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    /** Cell styles used across the workbook. */
    private static class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle label;
        final CellStyle text;
        final CellStyle wrapped;
        final CellStyle number;
        final CellStyle passed;
        final CellStyle failed;
        final CellStyle error;
        final CellStyle skipped;

        Styles(Workbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            Font labelFont = workbook.createFont();
            labelFont.setBold(true);

            title = workbook.createCellStyle();
            title.setFont(titleFont);

            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.LEFT);
            border(header);

            label = workbook.createCellStyle();
            label.setFont(labelFont);
            border(label);

            text = workbook.createCellStyle();
            text.setVerticalAlignment(VerticalAlignment.TOP);
            border(text);

            wrapped = workbook.createCellStyle();
            wrapped.setWrapText(true);
            wrapped.setVerticalAlignment(VerticalAlignment.TOP);
            border(wrapped);

            number = workbook.createCellStyle();
            number.setAlignment(HorizontalAlignment.RIGHT);
            border(number);

            passed = statusStyle(workbook, IndexedColors.BRIGHT_GREEN, IndexedColors.WHITE);
            failed = statusStyle(workbook, IndexedColors.RED, IndexedColors.WHITE);
            error = statusStyle(workbook, IndexedColors.ORANGE, IndexedColors.WHITE);
            skipped = statusStyle(workbook, IndexedColors.GREY_40_PERCENT, IndexedColors.WHITE);
        }

        /** Creates a cell style for a test status with the given fill and font colors. */
        private static CellStyle statusStyle(Workbook workbook, IndexedColors fill, IndexedColors fontColor) {
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(fontColor.getIndex());
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(fill.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            border(style);
            return style;
        }

        /** Applies a thin border to all sides of the given cell style. */
        private static void border(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }

        /** Returns the cell style corresponding to the given test status. */
        CellStyle forStatus(String status) {
            return switch (status) {
                case PASSED -> passed;
                case FAILED -> failed;
                case ERROR -> error;
                case SKIPPED -> skipped;
                default -> text;
            };
        }
    }
}
