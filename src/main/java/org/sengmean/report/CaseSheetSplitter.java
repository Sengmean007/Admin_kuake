package org.sengmean.report;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits the imported client workbook into one file per feature.
 *
 * <h2>Why split it</h2>
 * The client keeps everything in one spreadsheet of forty-three tabs, which is theirs to organise
 * as they like, but it is a poor shape for a repository: one file that every feature touches, a
 * megabyte of binary whose diff says nothing about which feature changed, and no way to look at
 * Login's cases without loading Deposit's. Split, the test data mirrors how the suites and page
 * objects are already laid out - by product area - and a re-import that only changed Register
 * shows up as a change to Register.
 *
 * <h2>Only the green tabs</h2>
 * The client marks what is in scope by colouring the sheet tab green, so those are the features
 * that get a file. See {@link CaseSheetLayout#markedGreen}.
 *
 * <h2>How a feature file is made</h2>
 * By opening the import and <b>removing every other sheet</b>, rather than copying rows into a new
 * workbook. Copying rows means recreating styles, merges, column widths and data validations by
 * hand, and getting any of it wrong makes a file that no longer looks like what the client sent.
 * Removing sheets keeps the client's own formatting exactly and lets POI drop what nothing
 * references any more.
 *
 * <p>Run it after re-exporting the sheet:
 * <pre>  mvn exec:java@split-cases</pre>
 */
public final class CaseSheetSplitter {

    /** Where the per-feature files are written. */
    static Path featureDir() {
        return Path.of(System.getProperty("report.features.dir",
                "src/test/resources/testdata/features"));
    }

    private CaseSheetSplitter() {
    }

    public static void main(String[] args) throws IOException {
        split();
    }

    /**
     * The per-feature files, splitting first only when they are missing or older than the import.
     *
     * <p>Splitting takes a few seconds - the workbook is opened once per feature - and the import
     * changes only when someone re-exports the client's sheet, so doing it at the end of every
     * test run would be a few seconds spent on nothing almost every time. Checking the timestamp
     * means a re-import still needs exactly one command, and nobody has to remember to run the
     * split before the results.
     */
    public static List<Path> splitIfStale() throws IOException {
        Path source = CaseSheetPage.workbook();
        if (!Files.exists(source)) {
            System.out.println("No client workbook at " + source + " - nothing to split.");
            return List.of();
        }
        Path dir = featureDir();
        List<Path> existing = existing(dir);
        if (!existing.isEmpty()) {
            long importedAt = Files.getLastModifiedTime(source).toMillis();
            boolean stale = false;
            for (Path file : existing) {
                if (Files.getLastModifiedTime(file).toMillis() < importedAt) {
                    stale = true;
                    break;
                }
            }
            if (!stale) {
                return existing;
            }
            System.out.println("The client workbook is newer than its feature files - resplitting.");
        }
        return split();
    }

    /** The feature files already on disk, sorted, or empty when there are none. */
    static List<Path> existing(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".xlsx"))
                    .sorted()
                    .toList();
        }
    }

    /** One workbook per green tab. Returns the files written, in workbook order. */
    public static List<Path> split() throws IOException {
        Path source = CaseSheetPage.workbook();
        if (!Files.exists(source)) {
            System.out.println("No client workbook at " + source + " - nothing to split.");
            return List.of();
        }

        List<String> features = new ArrayList<>();
        try (InputStream in = Files.newInputStream(source);
             Workbook book = new XSSFWorkbook(in)) {
            for (int s = 0; s < book.getNumberOfSheets(); s++) {
                Sheet sheet = book.getSheetAt(s);
                if (CaseSheetLayout.markedGreen(sheet)) {
                    features.add(sheet.getSheetName());
                }
            }
        }

        Path dir = featureDir();
        Files.createDirectories(dir);
        List<Path> written = new ArrayList<>();

        for (String feature : features) {
            // Reopened per feature: removing sheets mutates the workbook, so one pass could not
            // produce the second file from what the first left behind.
            try (InputStream in = Files.newInputStream(source);
                 Workbook book = new XSSFWorkbook(in)) {
                for (int s = book.getNumberOfSheets() - 1; s >= 0; s--) {
                    if (!feature.equals(book.getSheetName(s))) {
                        book.removeSheetAt(s);
                    }
                }
                // The one remaining sheet has to be the active one, or Excel opens the file on a
                // sheet index that no longer exists and reports it as damaged.
                book.setActiveSheet(0);

                Path out = dir.resolve(fileName(feature));
                try (OutputStream stream = Files.newOutputStream(out)) {
                    book.write(stream);
                }
                written.add(out);
                System.out.println("  " + out + "  (" + Files.size(out) / 1024 + " KB)");
            }
        }

        System.out.println("Split " + source + " into " + written.size() + " feature files in " + dir);
        return written;
    }

    /** {@code WithdrawalMainbalance} becomes {@code withdrawal-mainbalance.xlsx}. */
    static String fileName(String feature) {
        return slug(feature) + ".xlsx";
    }

    /**
     * A file-safe, lower-case name for a tab.
     *
     * The tab names carry spaces, trailing spaces and at least one slash, none of which belong in
     * a path. Runs of anything that is not a letter or digit collapse to a single dash.
     */
    static String slug(String feature) {
        String slug = feature.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return slug.replaceAll("^-+|-+$", "");
    }
}
