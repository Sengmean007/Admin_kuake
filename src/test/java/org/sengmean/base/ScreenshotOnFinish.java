package org.sengmean.base;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.sengmean.report.CaseLog;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Captures the final state of every open page at the end of each case - <b>passed or failed</b> -
 * so the HTML report carries a picture of what the browser was showing when the case finished.
 *
 * <h2>Passes are photographed too, and that is the point</h2>
 * This used to fire only on failure, which left the report's Evidence column empty for every
 * green case. A reader reviewing a QA report wants to see what a passing case actually looked
 * like, not only take its word for it - and a screenshot is the difference between "the suite
 * says the language switched" and seeing the translated page. Every executed case now leaves one.
 *
 * <h2>Skipped cases are not photographed</h2>
 * A case skipped by a failed assumption ends with an exception in hand just as a failure does, so
 * the two have to be told apart explicitly. A skip never ran, so a picture beside it would be of
 * whatever the browser happened to be showing - misleading in exactly the place a reader is
 * trying to work out why a row is empty.
 *
 * <h2>Hooked on afterTestExecution, not @AfterEach</h2>
 * JUnit runs this callback before any {@code @AfterEach}, which is the last moment at which
 * {@link BaseTest#tearDown()} has not yet closed the context out from under us.
 *
 * <p>It walks the browser's contexts instead of reading one Page field because the suites do not
 * agree on where their page lives - the guest suites use BaseTest's per-test page, while the
 * signed-in suites keep a long-lived session page of their own, and the casino suite opens the
 * game in a second tab. Every open page is worth having.
 *
 * <h2>Exactly one image per case</h2>
 * The page captured is the newest one open when the case ends - see {@link #pageWorthCapturing()}.
 * A case that opened a tab is photographed on that tab; a case that did not is photographed on
 * the page it worked on.
 *
 * <p>Files land in {@code target/case-screenshots} as {@code <TopLevelSuite>.<method>.png};
 * {@code org.sengmean.report.HtmlReportGenerator} matches them back to cases by that name. The
 * top-level class is used even for a {@code @Nested} case, for the same reason the report does:
 * Surefire names nested classes {@code Outer$Inner}, and one flat naming scheme keeps the two
 * sides in step.
 */
public class ScreenshotOnFinish
        implements AfterTestExecutionCallback, org.junit.jupiter.api.extension.BeforeTestExecutionCallback {

    private static final Path OUTPUT =
            Path.of(System.getProperty("report.shots", "target/case-screenshots"));

    /**
     * The file-name stem of the case now running, and how many mid-case captures it has taken.
     *
     * <p>These exist so a test can photograph a moment that has passed by the time the case ends
     * - the deposit form with an amount typed into it, say, which is gone the instant SUBMIT is
     * pressed. {@link #captureNow(Page)} needs to know which case it is filing under, and only
     * the extension knows that, so it is recorded here as each case starts.
     *
     * <p>Held per thread because JUnit can run cases in parallel; a shared field would file one
     * case's picture under another's name.
     */
    private static final ThreadLocal<String> CURRENT_CASE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> STEP_COUNT = ThreadLocal.withInitial(() -> 0);

    /** When the case now running started, so its verdict line can carry how long it took. */
    private static final ThreadLocal<Long> STARTED_AT = ThreadLocal.withInitial(System::nanoTime);

    /**
     * Whether each case's verdict is printed to the console as it finishes.
     *
     * <p>On by default. The report and the step trail are both written only once a whole class has
     * finished, so a run used to be a silent terminal for as long as it took - twenty-one minutes
     * for {@code DepositPageTest}'s sixty-seven cases - with no way to tell a suite that was
     * working from one that had stalled, and no sight of a failure until the end. Turn it off with
     * {@code -Dtest.liveResults=false} when the console output itself is in the way, such as a CI
     * job that only wants the report.
     */
    private static final boolean LIVE_RESULTS =
            !"false".equalsIgnoreCase(System.getProperty("test.liveResults", "true"));

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        String base = fileNameBase(context);
        CURRENT_CASE.set(base);
        STEP_COUNT.set(0);
        STARTED_AT.set(System.nanoTime());
        // The step trail is bracketed here rather than in @BeforeEach/@AfterEach because this
        // callback pair is the tightest bracket JUnit offers around the case body: a @BeforeEach
        // that navigates would otherwise be recorded against the previous case, and the trail
        // would end before the teardown that closes the browser.
        CaseLog.start(base);
        CaseLog.step("Case started: " + context.getDisplayName());
    }

    /**
     * Photographs a page mid-case, in addition to the one taken when the case ends.
     *
     * <p>Use it for a state the reader needs to see and the final screenshot cannot show - the
     * chosen payment option with its amount filled in, before SUBMIT replaces it with a dialog
     * and then a gateway. The report groups every image whose name shares the case's stem, so
     * these land in the same Evidence cell as the closing capture.
     *
     * <p>Named {@code <case>-1.png}, {@code -2} and so on, which sorts <em>before</em> the
     * closing {@code <case>.png} - a hyphen precedes a full stop - so the cell reads in the order
     * the case went through, provided the copier lists files in sorted order.
     *
     * <p>Never throws. A picture that could not be taken must not fail the case it documents.
     */
    public static void captureNow(Page page) {
        captureNow(page, null);
    }

    /**
     * Photographs a page mid-case and says in the trail what the picture is of.
     *
     * <p>The label matters once a case takes more than a handful: a walk over 52 games produces 52
     * images, and "Captured screenshot 37" tells a reader nothing about which game they are looking
     * at. Passing "PASS - Evoplay en" puts the answer beside the image in the trail, in the same
     * order the Evidence cell renders them.
     *
     * <p><b>The step number is zero-padded to three digits</b>, and that is not cosmetic. The
     * report copies screenshots in <em>lexicographic</em> filename order, so an unpadded
     * {@code -10} sorts before {@code -2} and a case with more than nine captures renders its
     * evidence out of sequence - the reader would see game 10 in position 2. Padding keeps the
     * numeric and the alphabetical orders the same. The report's grouping strips a trailing
     * {@code -\d+} either way, so padded names still land in the right case.
     */
    public static void captureNow(Page page, String label) {
        String base = CURRENT_CASE.get();
        if (base == null || page == null || page.isClosed()) {
            return;
        }
        try {
            int step = STEP_COUNT.get() + 1;
            STEP_COUNT.set(step);
            Files.createDirectories(OUTPUT);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(OUTPUT.resolve(String.format("%s-%03d.png", base, step)))
                    .setFullPage(false));
            CaseLog.evidence(label == null || label.isBlank()
                    ? "Captured screenshot " + step + " on " + page.url()
                    : "Screenshot " + step + ": " + label);
        } catch (Exception e) {
            System.out.println("Could not capture a step for " + base + ": " + e.getMessage());
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        // Wrapped so the step trail is written on every path out of here. The screenshot rules
        // below return early for a skip and for a closed browser, and both of those are cases a
        // reader still wants the trail for - a skip especially, since the trail is the only
        // thing that can show what the case had already done before it gave up.
        try {
            capture(context);
        } finally {
            CaseLog.finish();
            announce(context);
        }
    }

    /**
     * Prints one line per case to the console the moment it finishes.
     *
     * <h2>Why the report is not enough</h2>
     * Both the HTML report and the step trail are written after a whole class has run, so watching
     * a suite meant watching a still terminal - twenty-one minutes of it for the sixty-seven
     * deposit cases - unable to tell work from a stall, and unable to see a failure until the run
     * was over. This is the running commentary: which case, how it went, how long it took, and for
     * anything that did not pass, the first line of why.
     *
     * <p>It reads the verdict from JUnit rather than from anything the test says, so no case can
     * report itself green by forgetting to print. The four outcomes are distinguished because they
     * mean different things to a reader: a <b>SKIP</b> is a case that decided it could not run and
     * is normal on these suites, a <b>FAIL</b> is an assertion that was checked and was false, and
     * an <b>ERROR</b> is the case falling over before it got that far - most often a timeout, which
     * usually says more about the environment than about the feature.
     *
     * <p>Printed in a {@code finally} alongside {@code CaseLog.finish()} so that a case ending any
     * way at all still reports, and wrapped so a broken message can never turn a passing case red.
     */
    private void announce(ExtensionContext context) {
        if (!LIVE_RESULTS) {
            return;
        }
        try {
            Throwable failure = context.getExecutionException().orElse(null);
            String verdict;
            if (failure == null) {
                verdict = "PASS";
            } else if (failure instanceof org.opentest4j.TestAbortedException) {
                verdict = "SKIP";
            } else if (failure instanceof AssertionError) {
                verdict = "FAIL";
            } else {
                verdict = "ERROR";
            }
            double seconds = (System.nanoTime() - STARTED_AT.get()) / 1_000_000_000.0;
            System.out.printf("  %-5s %6.1fs  %s%s%n",
                    verdict, seconds, context.getDisplayName(), because(failure));
            System.out.flush();
        } catch (RuntimeException neverFailACaseOverALogLine) {
            System.out.println("  Could not print the verdict for " + context.getDisplayName()
                    + ": " + neverFailACaseOverALogLine);
        }
    }

    /**
     * The first line of why a case did not pass, short enough to sit on the end of its line.
     *
     * <p>The first line only, and capped: {@code assertAll} reports every failure it collected and
     * a Playwright timeout carries a call log and a stack, either of which would bury the forty
     * lines around it. The whole message is in the report and the trail, which is where a reader
     * goes once a line has told them which case to look at.
     */
    private static String because(Throwable failure) {
        if (failure == null) {
            return "";
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "  -> " + failure.getClass().getSimpleName();
        }
        String firstLine = message.strip().split("\\R", 2)[0].strip();
        if (firstLine.length() > 140) {
            firstLine = firstLine.substring(0, 140) + "…";
        }
        return "  -> " + firstLine;
    }

    private void capture(ExtensionContext context) {
        // Skipped cases only. A present exception no longer means "failed", because this now
        // fires for passes as well, so the abort check is the whole test rather than a guard on
        // top of one.
        if (context.getExecutionException()
                .filter(org.opentest4j.TestAbortedException.class::isInstance)
                .isPresent()) {
            CaseLog.step("Case aborted by a failed assumption; no closing screenshot is taken.");
            return;
        }
        if (BaseTest.browser == null || !BaseTest.browser.isConnected()) {
            return;
        }

        String base = fileNameBase(context);
        try {
            Page subject = pageWorthCapturing();
            if (subject == null) {
                return;
            }
            // The address the case finished on, recorded before the picture is taken so it
            // survives a screenshot that fails. It is the single most useful line in a trail:
            // a redirect to the landing page explains half the failures on the cashier suites
            // without anyone opening a stack trace.
            CaseLog.step("Case ended on " + subject.url());
            Files.createDirectories(OUTPUT);
            Path file = OUTPUT.resolve(base + ".png");
            subject.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(false));
            CaseLog.evidence("Captured the closing screenshot.");
        } catch (Exception e) {
            // A screenshot is evidence about a case, never a failure of its own: a page that
            // navigated away, closed itself or is mid-load must not turn a green case red or one
            // red case into two.
            System.out.println("Could not capture a screenshot for " + base + ": " + e.getMessage());
        }
    }

    /**
     * The one page worth photographing: the newest tab if the case opened one, otherwise the
     * page it worked on. Null when there is nothing worth a picture.
     *
     * <h2>One image per case, and it is the interesting one</h2>
     * Every open page used to be captured, which gave a case that opened a tab two images - the
     * page it started from and the tab it ended on - and the reader had to guess which mattered.
     * The newest page is the one the case arrived at: the payment gateway a confirmed deposit
     * hands off to, the game the casino suite opens in a second tab, the alternate-account page
     * the required-field cases work on. Where nothing new was opened, the newest page simply is
     * the page the case used, so the same rule covers both without a special case.
     *
     * <p>Pages are taken in creation order - {@code browser.contexts()} and {@code ctx.pages()}
     * both preserve it - so the last non-blank one is the most recently opened.
     */
    private static Page pageWorthCapturing() {
        Page newest = null;
        for (BrowserContext ctx : BaseTest.browser.contexts()) {
            for (Page page : ctx.pages()) {
                if (!page.isClosed() && !isBlank(page)) {
                    newest = page;
                }
            }
        }
        return newest;
    }

    /**
     * True for a page that has never been navigated, so nothing is learned by photographing it.
     *
     * <p>{@link BaseTest#setUp()} opens a fresh context and a blank page for <b>every</b> test,
     * including the signed-in suites that do their work on a long-lived member page of their own.
     * Walking the contexts therefore found two pages per cashier case and filed two images - the
     * blank one first, taking the un-suffixed name, so the Evidence column led with a white
     * rectangle and the real screenshot sat behind it as {@code -1}. Every one of those blanks
     * was the same 8,511 bytes, which is how they were spotted.
     *
     * <p>Skipping them means the useful page is the one that gets photographed, and a case whose
     * only page is blank now files nothing rather than something worthless.
     */
    private static boolean isBlank(Page page) {
        try {
            String url = page.url();
            return url == null || url.isBlank() || "about:blank".equals(url);
        } catch (RuntimeException e) {
            // A page being torn down cannot be read, and cannot usefully be photographed either.
            return true;
        }
    }

    /** {@code org.sengmean.tests.payment.DepositPageTest$ValidDeposits#foo} becomes {@code DepositPageTest.foo}. */
    private static String fileNameBase(ExtensionContext context) {
        String className = context.getTestClass().map(Class::getName).orElse("UnknownClass");
        String simple = className.substring(className.lastIndexOf('.') + 1);
        int nested = simple.indexOf('$');
        if (nested >= 0) {
            simple = simple.substring(0, nested);
        }
        String method = context.getTestMethod().map(m -> m.getName()).orElse("unknownTest");
        return (simple + "." + method).replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
