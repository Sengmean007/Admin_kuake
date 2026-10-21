package org.sengmean.base;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.report.CaseLog;

/**
 * Shared behaviour for all page objects: holds the Playwright {@link Page},
 * resolves the environment base URL, and offers small navigation helpers.
 *
 * <h2>This class is also what loads the configuration</h2>
 * The two fields below are the first settings any run reads, because every page object extends
 * this class and {@link org.sengmean.base.BaseTest} takes its own {@code BASE_URL} from here. So
 * touching {@link ConfigDataProvider} at this point is what pulls {@code Config/config.properties}
 * into the system properties, early enough that everything else in the project - the currency
 * switcher, the settle timeouts, the report generators' output paths, the environment panel of
 * the report - can go on reading {@code System.getProperty} and get the configured answer without
 * being rewritten. Do not remove the {@code ConfigDataProvider} calls in favour of plain
 * {@code System.getProperty} here: it would still work for these two fields, and it would quietly
 * stop the other thirty settings from ever being loaded.
 */
public class BasePage {

    /**
     * Overridable with -Dbase.url=... so the suite can point at another environment, and set for
     * the usual case in {@code Config/config.properties}.
     *     * Keep this an origin with no path and no query string. {@link #open(String)} appends
     * the path directly, so a base of ".../en?currency=HKD" produces
     * ".../en?currency=HKD/en/member/deposit/" - the path lands inside the query string and
     * every navigation silently goes to the wrong page. Use {@link #BASE_QUERY} for query
     * parameters instead.
     */
    public static final String BASE_URL = ConfigDataProvider.get("base.url", "https://test-v2.1083.city");

    /**
     * Query string appended to every navigation, without the leading "?".
     *     * This is the supported way to pass something like {@code currency=HKD}, which was
     * previously being pasted into {@link #BASE_URL} where it swallowed the path.
     *     * It defaults to empty, and deliberately so. Enabling {@code currency=HKD} by default
     * was tried and reverted: the parameter makes the site navigate again immediately
     * after load, which tears down the login modal mid-click and took LoginTest from 4/4
     * to 1 error and 3 failures. Opt in per run with -Dbase.query=currency=HKD once that
     * redirect is understood.
     */
    public static final String BASE_QUERY = ConfigDataProvider.get("base.query", "");

    protected Page page ;

    public BasePage() {
        super();
        // Default constructor
    }
    public BasePage(Page page) {
        this.page = page;
    }

    /**
     * Navigates to a path relative to the base URL, e.g. "/en/member/record/".
     *
     * <h2>Why it does not wait for {@code load}</h2>
     * Playwright's default is {@code waitUntil: "load"}, which waits for <b>every subresource on
     * the page</b> - third-party ones included. This site never satisfies that quickly: measured on
     * 2026-09-12, the landing page's own document and API calls were finished at <b>5.4s</b>, but
     * {@code static.cloudflareinsights.com/beacon.min.js} did not arrive until <b>6.8s</b> and the
     * {@code test.exchat.co} chat widget was still fetching at <b>10.3s</b>. Against the context's
     * navigation timeout that is a coin toss on every single navigation, and it is what failed
     * {@code HOME-0029} with {@code Timeout 6000ms exceeded … navigating to ".../en/", waiting
     * until "load"} - a case about Traditional Chinese that never got as far as the language menu.
     * Nothing was wrong with the page or with the case: it was waiting on an analytics beacon.
     *
     * <p>{@code domcontentloaded} is the event that actually means "the document is there", and it
     * is what every page object in this project builds on anyway - each does its own explicit
     * waiting for the elements it needs. So the navigation no longer holds a case hostage to a
     * third party that has nothing to do with what is being tested.
     */
    protected void open(String path) {
        page.navigate(url(path),
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    /**
     * Where this site puts every message it shows - a dialog over a dimmed page, not a toast.
     *
     * <p>Shared here rather than per page object because it is one site-wide component: the same
     * dark box with a single <b>Close</b> button carries "The point transfer fail" on the wallet
     * page and "Message sent" on the activation modal.
     */
    protected static final String ALERT_BOARD =
            "[class*='AlertBoard'], [class*='Toast'], [role='alert']";

    /**
     * Presses <b>Close</b> on the message the page is showing, and reports whether it is now clear.
     *
     * <h2>These dialogs do not clear themselves, and they block everything behind them</h2>
     * Learned twice on this project, in two unrelated suites, both times as a mystery timeout:
     * <ul>
     *   <li>the wallet page's "The point transfer fail" left the "To" box uncovered-looking but
     *       unclickable, and a walk over every game ended after one game;</li>
     *   <li>the activation modal's "Message sent" sat over stage two, so the OTP could not be
     *       typed and {@code AC-03} and {@code AC-04} both died on the code entry.</li>
     * </ul>
     * Any flow that raises one of these and then keeps interacting with the page has to dismiss it.
     *
     * <p><b>Close is addressed inside the board</b>, never by page-wide text: the support chat
     * widget parked in the bottom-right corner carries a "Close" of its own, and a page-wide match
     * dismisses the chat bubble while the dialog that is actually in the way stays put.
     *
     * @return true when nothing is covering the page - either no message was showing, or it was
     *         closed. False means one is still up and the caller should reload rather than click
     *         into it.
     */
    public boolean dismissMessage(double timeoutMs) {
        Locator close = alertCloseButton();
        if (close == null) {
            return true;
        }
        CaseLog.step("Closing the dialog the page raised: " + visibleAlertText());
        close.click();
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (alertCloseButton() == null) {
                return true;
            }
            page.waitForTimeout(200);
        }
        CaseLog.step("The dialog would not close within " + (long) timeoutMs + "ms.");
        return false;
    }

    /** The Close button of a visible message board, or null when none is showing. */
    private Locator alertCloseButton() {
        Locator alerts = page.locator(ALERT_BOARD);
        for (int i = 0; i < alerts.count(); i++) {
            Locator alert = alerts.nth(i);
            if (!alert.isVisible()) {
                continue;
            }
            Locator close = alert.getByText("Close", new Locator.GetByTextOptions().setExact(true));
            if (close.count() > 0 && close.first().isVisible()) {
                return close.first();
            }
        }
        return null;
    }

    /** What a visible message board is saying, for the trail. Empty when none is showing. */
    protected String visibleAlertText() {
        Locator alerts = page.locator(ALERT_BOARD);
        for (int i = 0; i < alerts.count(); i++) {
            Locator alert = alerts.nth(i);
            if (alert.isVisible()) {
                String text = String.valueOf(alert.innerText()).replaceAll("\\s+", " ").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    /**
     * The promotional <b>welcome banner</b> the client platform raises over the whole page - the
     * same component on both the member site ({@code test-v2.1083.city}, see
     * {@code NewWeibo_automation}'s {@code BasePage}) and this HK client ({@code test-v2.138hk.vip}):
     * identical {@code z-welcomeBannerModal} class and {@code z-index: 2147483002} (measured
     * 2026-10-08). It was never ported here, which is why every {@code UiNotificationCenterClientHK}
     * case that needed to sign in timed out on the header's Login - the banner sat on top of it and
     * won every hit test, so Playwright waited out the full budget for a control it could never
     * reach.
     *
     * <h2>It is not a banner, it is a full-screen click trap</h2>
     * It renders as a picture in the middle of the screen with a pink X above it, which reads as
     * something you can ignore. It is not: the element is {@code position:fixed; top:0; left:0;
     * width:100%; height:100%} at that near-maximum z-index, so while it is up it is the hit-test
     * winner for every point on the page - including controls that look sharp and clickable in a
     * screenshot. A click does not bounce with a useful message: Playwright waits for the control to
     * become clickable, the overlay never yields, and the case dies on a timeout naming the control
     * it wanted rather than the banner sitting on top of it.
     *
     * <p>Its close button wraps {@code <img alt="Ads close">}; {@link #WELCOME_BANNER_CLOSE}
     * matches on that, because the button's own classes are utility soup and the banner image itself
     * (which opens the promotion) would match several of them. A JavaScript {@code element.click()}
     * does not dismiss it - it wants a trusted event, so it has to be a real Playwright click. The
     * site can stack more than one, so dismissal closes them in a loop rather than once.
     */
    protected static final String WELCOME_BANNER = "div[class*='z-welcomeBannerModal']";

    /** The welcome banner's close control; see {@link #WELCOME_BANNER}. */
    protected static final String WELCOME_BANNER_CLOSE =
            "div[class*='z-welcomeBannerModal'] button:has(img[alt='Ads close'])";

    /** True while the promotional welcome banner is covering the page. */
    public boolean isWelcomeBannerOpen() {
        return isWelcomeBannerOpen(page);
    }

    /** As {@link #isWelcomeBannerOpen()}, for a page this object does not own. */
    public static boolean isWelcomeBannerOpen(Page target) {
        try {
            Locator banner = target.locator(WELCOME_BANNER);
            return banner.count() > 0 && banner.first().isVisible();
        } catch (PlaywrightException navigating) {
            return false;
        }
    }

    /**
     * Closes the promotional welcome banner if it is up, and reports whether the page is now clear.
     * True when nothing is covering the page - either no banner was up, or it has gone.
     */
    public boolean dismissWelcomeBanner() {
        return dismissWelcomeBanners(page, true);
    }

    /** As {@link #dismissWelcomeBanner()}, for a page this object does not own. */
    public static boolean dismissWelcomeBanners(Page target, boolean verbose) {
        // Closed in a loop, not once: the site raises these per promotion, so a load can bring up
        // more than one stacked over each other, and dismissing the top one only reveals the next.
        // Bounded rather than "until none", so a close that silently does nothing cannot spin here
        // forever.
        int found = welcomeBannerCount(target);
        if (found == 0) {
            if (verbose) {
                CaseLog.step("Welcome banners: none on the page.");
            }
            return true;
        }
        CaseLog.step("Welcome banners: " + found + " found; closing " + (found == 1 ? "it." : "them all."));

        int closed = 0;
        for (int attempt = 0; attempt < MAX_WELCOME_BANNERS && isWelcomeBannerOpen(target); attempt++) {
            try {
                target.locator(WELCOME_BANNER_CLOSE).first().click(new Locator.ClickOptions().setTimeout(5_000));
                // Gone means "no longer covering the page", not specifically "removed from the DOM" -
                // polling on visibility accepts either outcome, which is the one the caller cares about.
                if (!awaitBannerGone(target)) {
                    throw new PlaywrightException("The banner was clicked but is still covering the page after "
                            + (long) (BANNER_GONE_TIMEOUT_MS / 1000) + "s.");
                }
                closed++;
            } catch (PlaywrightException stillThere) {
                CaseLog.step("Welcome banner " + (closed + 1) + " would not close after " + closed + " of " + found
                        + ": " + stillThere.getMessage());
                return false;
            }
        }
        return !isWelcomeBannerOpen(target);
    }

    /** How many welcome banners are on the page right now. */
    public static int welcomeBannerCount(Page target) {
        try {
            return target.locator(WELCOME_BANNER).count();
        } catch (PlaywrightException navigating) {
            return 0;
        }
    }

    /** Polls until no welcome banner is covering the page, or the budget runs out. */
    private static boolean awaitBannerGone(Page target) {
        long deadline = System.currentTimeMillis() + (long) BANNER_GONE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!isWelcomeBannerOpen(target)) {
                return true;
            }
            target.waitForTimeout(250);
        }
        return false;
    }

    /** How long a clicked banner is given to stop covering the page. Override with -Dtest.welcomeBanners.goneTimeout=<ms>. */
    private static final double BANNER_GONE_TIMEOUT_MS =
            Double.parseDouble(ConfigDataProvider.get("test.welcomeBanners.goneTimeout", "6000"));

    /** How many stacked welcome banners are cleared before giving up. Override with -Dtest.welcomeBanners.max=<count>. */
    private static final int MAX_WELCOME_BANNERS =
            Math.max(1, Integer.parseInt(ConfigDataProvider.get("test.welcomeBanners.max", "5")));

    /**
     * Waits until the document is ready, and then gives {@code load} a brief chance without
     * letting it fail the case.
     *
     * <h2>The replacement for a bare {@code page.waitForLoadState()}</h2>
     * That call defaults to {@code load} and carries exactly the trap described in
     * {@link #open(String)}: fifteen page objects were calling it after navigating, so any of them
     * could fail on the chat widget being slow. Waiting for {@code domcontentloaded} is the part
     * that matters and it is honoured strictly. The {@code load} that follows is <b>best effort</b>
     * - it is genuinely useful when it arrives, because late-loading images and fonts settle the
     * layout, but a case must never be red because a beacon was not ready.
     */
    protected void awaitReady() {
        awaitReady(page);
    }

    /** As {@link #awaitReady()}, for a page this object does not own - a popup or a game tab. */
    public static void awaitReady(Page target) {
        target.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            target.waitForLoadState(LoadState.LOAD,
                    new Page.WaitForLoadStateOptions().setTimeout(SETTLE_AFTER_READY_MS));
        } catch (PlaywrightException stillLoading) {
            // Expected on this site, and not a problem: the document is ready and the case's own
            // waits take it from here. Recorded rather than swallowed silently, because a page that
            // never reaches `load` is worth seeing in the trail when a case does go wrong later.
            CaseLog.step("The page was ready but had not finished loading subresources within "
                    + (SETTLE_AFTER_READY_MS / 1000) + "s; carrying on.");
        }
    }

    /**
     * How long {@link #awaitReady()} lets {@code load} run over before carrying on regardless.
     *
     * <p>Override with {@code -Dtest.settleAfterReady=<milliseconds>}.
     */
    private static final long SETTLE_AFTER_READY_MS =
            Long.parseLong(ConfigDataProvider.get("test.settleAfterReady", "5000"));

    /**
     * The absolute URL for a path, with {@link #BASE_QUERY} appended as a real query
     * string rather than pasted into the middle of the address.
     */
    public static String url(String path) {
        String absolute = BASE_URL + path;
        if (BASE_QUERY.isEmpty()) {
            return absolute;
        }
        return absolute + (absolute.contains("?") ? "&" : "?") + BASE_QUERY;
    }

    /**
     * A header button addressed by its exact visible label, e.g. "Register".
     *     * The header renders each auth control twice - once for desktop and once for a mobile
     * layout that stays hidden on a desktop viewport. Matching on the label alone therefore
     * yields two elements, and clicking the hidden one waits out the full timeout before
     * failing; that is exactly how the original LoginPage locator broke every login test.
     * Filtering on visibility picks the control a user would actually press.
     *     * Matching is by accessible name with setExact, not by a hasText pattern. A Java
     * Pattern is handed to the browser as a JavaScript regex, and JavaScript has no
     * \Q...\E quoting - so a Pattern.quote'd label silently matches nothing and every
     * click waits out the full 60s timeout. An exact name also keeps "Register" from
     * matching the "How to Register" link.
     */
    protected Locator visibleButton(String label) {
        return page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(label).setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true))
                .first();
    }

    public String currentUrl() {
        return page.url();
    }

    /**
     * A pattern matching exactly {@code text}, give or take surrounding whitespace, that survives
     * being handed to the browser.
     *
     * <p>Use this - never {@code Pattern.quote} - to build a locator pattern from a literal. The
     * same trap as {@link #visibleButton}: Playwright turns a Java {@code Pattern} into a
     * JavaScript regex, JavaScript has no {@code \Q...\E}, and a quoted pattern therefore matches
     * nothing at all. It cost the Other Category page objects every row action and every language
     * tab before a mock of the captures showed it (2026-09-23). This escapes each metacharacter
     * individually, which both regex dialects read the same way.
     */
    protected static java.util.regex.Pattern exactly(String text, boolean ignoreCase) {
        String escaped = text.replaceAll("[\\\\^$.|?*+()\\[\\]{}/-]", "\\\\$0");
        return java.util.regex.Pattern.compile("^\\s*" + escaped + "\\s*$",
                ignoreCase ? java.util.regex.Pattern.CASE_INSENSITIVE : 0);
    }
}
