package org.sengmean.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The trail of what a case actually did, recorded while it runs and read back by
 * {@link HtmlReportGenerator} into the detail panel under each row.
 *
 * <h2>Why the report needed this</h2>
 * A row used to say "Pass" and nothing else - the Actual result column read "The application
 * behaved as expected" for every green case in every suite, which is a restatement of the
 * status rather than evidence for it. A failed row was barely better: a truncated exception
 * message and one stack frame, with no account of how the browser got there. Neither answers
 * the question a QA report exists to answer - <i>what was done, and what did the application
 * do about it</i> - so a reader had to open the code, or take the colour on trust.
 *
 * <h2>Recorded without touching the suites</h2>
 * Nothing here has to be called from a test for the trail to exist. {@code BaseTest} attaches
 * Playwright listeners to every context it opens, so navigations, console errors, uncaught page
 * errors and the site's own API traffic are logged as they happen, for all ten suites at once.
 * {@link #step(String)} is there for a case that wants to narrate something the browser events
 * cannot show - which account it signed in as, which amount it typed - and page objects under
 * {@code org.sengmean.page} can call it too, which is why this class lives in {@code src/main}
 * and deliberately has no Playwright import.
 *
 * <h2>Written to disk, not held in memory</h2>
 * Each case's entries land in {@code target/case-logs/<Suite>.<method>.log}, matching how
 * {@code ScreenshotOnFinish} files its images, and the generator joins them back to rows on the
 * same {@code Suite#method} key. Going through the filesystem is what lets
 * {@code mvn exec:java@html-report} - the by-hand rebuild that reads Surefire XML and never sees
 * a live JVM - render the same detail as the listener does. A trail kept only in the listener's
 * memory would have made the two routes produce visibly different reports.
 *
 * <h2>Bounded, and never fatal</h2>
 * A case that triggers a redirect loop, or a page that logs an error on every animation frame,
 * would otherwise write a log larger than the report. Recording stops at {@code MAX_ENTRIES}
 * and says so. Every method here swallows its own failures: a report is a by-product of a run,
 * and a step that could not be recorded must never fail the case it was describing.
 */
public final class CaseLog {

    /** One recorded moment. {@code millis} is measured from the start of the case. */
    public record Entry(long millis, String kind, String text) {}

    /** Something the test itself narrated. */
    public static final String STEP = "STEP";
    /** A page load or a new tab. */
    public static final String NAV = "NAV";
    /** A request the site made of its own back end, with the status it came back with. */
    public static final String API = "API";
    /** A request that came back 4xx/5xx, or did not come back at all. */
    public static final String NETWORK = "NETWORK";
    /** A console error or an uncaught error on the page. */
    public static final String CONSOLE = "CONSOLE";
    /** A screenshot was taken. */
    public static final String EVIDENCE = "EVIDENCE";
    /** Something the case asserted or read back. */
    public static final String CHECK = "CHECK";
    /** A value the run used - the account, the currency, the amount. */
    public static final String DATA = "DATA";

    /** Past this, a case is not describing itself any more; it is flooding the report. */
    private static final int MAX_ENTRIES = 400;

    /**
     * How much of the budget the browser's own traffic may spend. The remainder is reserved for
     * the case's own STEP, CHECK, DATA and EVIDENCE lines, which are never dropped for chatter.
     */
    private static final int MAX_AUTOMATIC_ENTRIES = 300;

    private static final ThreadLocal<Recorder> CURRENT = new ThreadLocal<>();

    private CaseLog() {
    }

    /** {@code target/case-logs}, overridable with {@code -Dreport.logs=...}. */
    public static Path dir() {
        return Path.of(System.getProperty("report.logs", "target/case-logs"));
    }

    /**
     * Opens a trail for the case about to run.
     *
     * @param caseName the file-name stem {@code ScreenshotOnFinish} uses, {@code Suite.method},
     *                 so the log and the screenshots of one case sit under the same name.
     */
    public static void start(String caseName) {
        CURRENT.set(new Recorder(caseName));
    }

    /** True while a case is being recorded, for a caller that wants to skip building a message. */
    public static boolean recording() {
        return CURRENT.get() != null;
    }

    public static void step(String text) {
        add(STEP, text);
    }

    public static void nav(String text) {
        add(NAV, text);
    }

    public static void api(String text) {
        add(API, text);
    }

    public static void network(String text) {
        add(NETWORK, text);
    }

    public static void console(String text) {
        add(CONSOLE, text);
    }

    public static void evidence(String text) {
        add(EVIDENCE, text);
    }

    public static void check(String text) {
        add(CHECK, text);
    }

    /** A named value the case worked with, e.g. {@code data("currency", "HKD")}. */
    public static void data(String name, String value) {
        add(DATA, name + " = " + value);
    }

    public static void add(String kind, String text) {
        Recorder recorder = CURRENT.get();
        if (recorder == null || text == null || text.isBlank()) {
            return;
        }
        recorder.add(kind, text);
    }

    /**
     * Writes the case's trail out and closes it.
     *
     * Called from {@code ScreenshotOnFinish.afterTestExecution}, which JUnit runs for a pass, a
     * failure and an abort alike - so no case ends without its log, including the ones a reader
     * most wants to read.
     */
    public static void finish() {
        Recorder recorder = CURRENT.get();
        CURRENT.remove();
        if (recorder == null || recorder.entries.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(dir());
            List<String> lines = new ArrayList<>(recorder.entries.size());
            for (Entry entry : recorder.entries) {
                // Tab-separated, one line per entry, because the reader is the report generator
                // and a dependency-free format cannot go stale. A tab or a newline inside the
                // text would break the shape, so both are folded to a space.
                lines.add(entry.millis() + "\t" + entry.kind() + "\t" + oneLine(entry.text()));
            }
            Files.write(dir().resolve(recorder.name + ".log"), lines, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            System.out.println("Could not write the step log for " + recorder.name + ": " + e);
        }
    }

    /** Drops the current trail without writing it. */
    public static void clear() {
        CURRENT.remove();
    }

    /** Reads back the current case's trail, for anything that wants it in the same JVM. */
    public static List<Entry> entries() {
        Recorder recorder = CURRENT.get();
        return recorder == null ? List.of() : List.copyOf(recorder.entries);
    }

    private static String oneLine(String text) {
        return text.replace('\t', ' ').replaceAll("\\R", " ").trim();
    }

    /**
     * The trail of one case.
     *
     * Held per thread because JUnit can be told to run cases in parallel, and a shared list
     * would file one case's steps under another's name - the same reason
     * {@code ScreenshotOnFinish} keeps its current-case name in a {@link ThreadLocal}.
     */
    private static final class Recorder {
        private final String name;
        private final long startedAt = System.nanoTime();
        private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
        private boolean capped;

        Recorder(String name) {
            this.name = name;
        }

        /**
         * Records an entry, capping the browser's own chatter <b>without</b> ever crowding out
         * what the test deliberately said.
         *
         * <h2>Why the cap cannot be a single number</h2>
         * It was, and a long case lost exactly the part worth reading. The walk over all 52 games
         * wrote 328 API lines, 34 console lines and 17 network lines - and its per-game results
         * stopped after the third game, because the site's background traffic had spent the whole
         * 400-entry budget. The report then showed a case that transferred into 21 games with
         * evidence for 3 of them.
         *
         * <p>A {@code STEP}, {@code CHECK}, {@code DATA} or {@code EVIDENCE} line exists because a
         * test author wrote it, so it is always more valuable than the three-hundredth automatic
         * API line. Those get their own allowance and are never dropped in favour of chatter; the
         * automatic kinds share what is left.
         */
        void add(String kind, String text) {
            boolean deliberate = STEP.equals(kind) || CHECK.equals(kind)
                    || DATA.equals(kind) || EVIDENCE.equals(kind);
            int ceiling = deliberate ? MAX_ENTRIES : MAX_AUTOMATIC_ENTRIES;
            int used = 0;
            synchronized (entries) {
                for (Entry entry : entries) {
                    boolean entryDeliberate = STEP.equals(entry.kind()) || CHECK.equals(entry.kind())
                            || DATA.equals(entry.kind()) || EVIDENCE.equals(entry.kind());
                    if (entryDeliberate == deliberate) {
                        used++;
                    }
                }
            }
            if (used >= ceiling) {
                if (!deliberate && !capped) {
                    capped = true;
                    entries.add(new Entry(elapsed(), STEP,
                            "The browser's own traffic stopped being recorded after "
                                    + MAX_AUTOMATIC_ENTRIES + " entries; the case's own steps continue."));
                }
                return;
            }
            entries.add(new Entry(elapsed(), kind, text));
        }

        long elapsed() {
            return (System.nanoTime() - startedAt) / 1_000_000L;
        }
    }
}
