package org.sengmean.base;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.page.auth.AdminLoginPage;
import org.sengmean.report.CaseLog;

/**
 * Author: Seng mean
 * Date: 2024-06-17
 * BaseTest class to set up and tear down Playwright browser instances for tests.
 * Lifecycle: Playwright and the Browser are created once per test class in {@link #launchBrowser()},
 * and each test gets its own {@link BrowserContext} and {@link Page}. The browser must exist before
 * any subclass {@code @BeforeAll} runs - classes such as DepositPageTest call {@link #newContext()}
 * from their own {@code @BeforeAll} to open a long-lived member session, and JUnit runs every
 * {@code @BeforeAll} before the first {@code @BeforeEach}.
 */
@ExtendWith(ScreenshotOnFinish.class)
public class BaseTest {

    /**
     * The environment under test, taken from {@link BasePage} rather than declared again.
     *
     * <p>It used to be a second {@code System.getProperty("base.url", "https://…")} with the
     * default spelled out a second time, which is one edit away from a suite whose pages and
     * whose assertions disagree about which environment they are on - and the symptom of that
     * is a case that navigates correctly and then fails on a URL comparison. There is one
     * declaration now, in the page-object base class that actually does the navigating.
     */
    public static final String BASE_URL = BasePage.BASE_URL;
    public static final String USERNAME = setting("test.username", "qa0001");
    public static final String PASSWORD = setting("test.password", "password123@");
    public static final String CAPTCHA = setting("test.captcha", "1111");

    /**
     * A second operator account, for the cases the shared one cannot serve.
     *
     * <p>Two kinds tend to need one, and for opposite reasons: a case that deliberately spends
     * an account's wrong-password attempts and must not spend the shared one's, and a case that
     * needs a role with <em>fewer</em> permissions than the usual operator so that a control is
     * genuinely absent rather than merely unused. Empty until Admin_weibo has such a case; set
     * {@code test.alt.username} / {@code test.alt.password} in {@code Config/} when it does.
     */
    public static final String ALT_USERNAME = setting("test.alt.username", "");
    public static final String ALT_PASSWORD = setting("test.alt.password", "");

    /**
     * The three other roles the workbook's Access Control section needs, and which none of the
     * accounts above can stand in for.
     *
     * <h2>Why a role cannot be faked</h2>
     * {@code RBMP-116} to {@code RBMP-119} are about what a role is <em>not</em> allowed to do:
     * an Agent must not find Rebate management in the menu, an ordinary user must not reach
     * {@code /rebate/set-up} by typing it. There is no way to ask the back office to pretend -
     * the only honest test is a session that really holds that role, which means a real account
     * per role. Until each is configured its case aborts saying which key is empty, rather than
     * passing on the strength of a control that was never there to be found.
     *
     * <p>Set {@code test.admin.username} / {@code test.admin.password} and the matching
     * {@code test.agent.*} and {@code test.user.*} keys in {@code Config/secrets.properties}
     * (or as environment variables in CI) when the accounts exist.
     */
    public static final String ADMIN_USERNAME = setting("test.admin.username", "");
    public static final String ADMIN_PASSWORD = setting("test.admin.password", "");

    /** An Agent-role account. See {@link #ADMIN_USERNAME}. */
    public static final String AGENT_USERNAME = setting("test.agent.username", "");
    public static final String AGENT_PASSWORD = setting("test.agent.password", "");

    /** An ordinary member account. See {@link #ADMIN_USERNAME}. */
    public static final String USER_USERNAME = setting("test.user.username", "");
    public static final String USER_PASSWORD = setting("test.user.password", "");

    /**
     * The language every signed-in suite puts the back office into before its first case.
     *
     * <h2>Why the run has a language at all</h2>
     * The header's language selector is per-account and <b>sticky</b>: whatever the last person
     * or the last run left it on is what the next run gets. That is a silent source of failures
     * for any case that reads visible text - a suite that passed yesterday in English fails today
     * in Chinese, and the report says "expected [Program name, ...] but was [方案名稱, ...]",
     * which looks like a defect and is not one. Pinning it makes a run reproducible.
     *
     * <p>It is also the cheap way to get a whole second pass over the product: the same 154 cases
     * run again in another language is a real localization sweep, and it costs a command-line
     * flag rather than a second suite.
     *
     * <pre>./mvnw test -Dsuite=RebateSuite -Dtest.language=zh-TW</pre>
     *
     * <p>The value is the option value the site's own selector carries - {@code en},
     * {@code zh-TW}, {@code th} - not the word it displays. See {@code test.language} in
     * {@code Config/config.properties}.
     */
    public static final String LANGUAGE = setting("test.language", "en");

    /**
     * The currency every signed-in suite puts the back office into before its first case.
     *
     * <h2>It selects the data, it does not merely filter the view</h2>
     * The obvious reading of a currency selector in a header is that it changes which records are
     * shown. On this back office it changes which records <b>exist</b> as far as the screen is
     * concerned: Rebate set up keeps a separate global settings panel and a separate program list
     * per currency. Measured 2026-09-18, Real-time Min. Claim read {@code 0.05} under USDT and
     * {@code 19} under HKD, with different programs listed under each.
     *
     * <p>So this is the single most consequential setting in the file. Pointed at the wrong
     * currency a run reports empty tabs on a populated environment, and writes its changes into a
     * configuration nobody is looking at - which is exactly how a test value once sat unnoticed
     * in the HKD settings while the USDT ones looked perfectly normal.
     *
     * <pre>./mvnw test -Dsuite=RebateSuite -Dtest.currency=USDT</pre>
     */
    public static final String CURRENCY = setting("test.currency", "HKD");

    /**
     * The Agent group and Member group a created program is targeted at.
     *
     * <p>Names as the Create form's pickers display them, from {@code test.group.*} in
     * {@code Config/config.properties} - so pointing the suite at different groups is a config
     * change rather than a code one. Used when the pickers offer them; see
     * {@code RealTimeRebateTest.targetNamedGroupsIfPossible} for what happens when they do not.
     */
    public static final String AGENT_GROUP_NAME = setting("test.group.agent.name", "agent-8001");
    public static final String MEMBER_GROUP_NAME = setting("test.group.member.name", "Default 00");

    /**
     * The Agent and Member group ids the suite pairs its seeded fixture with.
     *
     * <p>Numeric ids, not names - see {@code test.pairing.*} in {@code Config/config.properties}
     * for where they came from and why the suite needs them at all. In short: the Create form
     * cannot express a specific pairing for this operator, so a fixture that has to coexist with
     * programs already on the tab is seeded through the API instead.
     */
    public static final int PAIRING_AGENT_ID = (int) ConfigDataProvider.number("test.pairing.agent.id", 31);
    public static final int PAIRING_GROUP_ID = (int) ConfigDataProvider.number("test.pairing.group.id", 12);

    /** A second, different pairing, for the rows that need two programs that do not collide. */
    public static final int ALT_PAIRING_AGENT_ID = (int) ConfigDataProvider.number("test.pairing.alt.agent.id", 27);
    public static final int ALT_PAIRING_GROUP_ID = (int) ConfigDataProvider.number("test.pairing.alt.group.id", 9);

    /** How long a fresh sign-in is given to settle before the first case runs. */
    private static final double LOGIN_SETTLE_MS = ConfigDataProvider.number("test.login.settle", 3000);

    /**
     * Set headless=false in {@code Config/config.properties} - or {@code -Dheadless=false} for one
     * run - to watch the run, and {@code -DslowMo=1000} to slow it down for debugging.
     */
    public static final boolean HEADLESS = ConfigDataProvider.flag("headless", false);
    public static final double SLOW_MO = ConfigDataProvider.number("slowMo", 0);

    /**
     * A setting, from wherever it was configured, falling back when it is missing <em>or blank</em>.
     *
     * <h2>Why not {@code System.getProperty} directly</h2>
     * Two reasons, and the first one used to break every suite at once. Surefire plants
     * {@code ${slowMo}} from the POM into the forked JVM, and an undefined POM property arrives as
     * an empty string rather than as an absent property - so {@code getProperty("slowMo", "0")}
     * returns "" and {@code Double.parseDouble} throws inside a static initializer, which errors
     * every case in every suite before a browser is ever launched. {@link ConfigDataProvider}
     * treats blank as unset everywhere for that reason.
     *
     * <p>The second is that these values now live in {@code Config/config.properties}, with the
     * command line and the environment able to override them, and this is the one place the
     * suites read them through.
     */
    private static String setting(String key, String fallback) {
        return ConfigDataProvider.get(key, fallback);
    }

    protected static Playwright playwright;
    protected static Browser browser;

    /**
     * The per-test guest context and page, created on first use rather than before every test.
     *
     * <h2>Why lazily</h2>
     * These used to be opened in {@link #setUp()} for <b>every</b> test in every suite. A
     * signed-in suite does its work on a long-lived session page of its own and touches this
     * one only in a guest-redirect case, so almost every test was opening a browser context
     * and a page it never used. In a headed run that is a blank window sitting there for the
     * whole suite; unattended it is a context and a page per test for nothing.
     *
     * <p>Created on demand through {@link #page()} and {@link #context()}, so a test that never
     * asks for a guest page never opens one, and {@link #tearDown()} has nothing to close.
     */
    private BrowserContext context;
    private Page page;

    /**
     * How long any single action - a click, a fill, waiting for an element - may take.
     *
     * <p>Kept short on purpose: see {@link #newContext()}. Override with
     * {@code -Dtest.actionTimeout=<milliseconds>}.
     */
    private static final double ACTION_TIMEOUT_MS =
            Double.parseDouble(ConfigDataProvider.get("test.actionTimeout", "6000"));

    /**
     * How long a navigation may take, which is a different question from how long a click may take.
     *
     * <p>Override with {@code -Dtest.navigationTimeout=<milliseconds>}.
     */
    private static final double NAVIGATION_TIMEOUT_MS =
            Double.parseDouble(ConfigDataProvider.get("test.navigationTimeout", "30000"));

    public BaseTest() {
        super();
    }

    /**
     * Creates an isolated, unauthenticated context.
     * Safe to call from a subclass {@code @BeforeAll}, because the browser is launched in
     * this class's {@code @BeforeAll}, which JUnit runs first.
     */
    public static BrowserContext newContext() {
        if (browser == null) {
            throw new IllegalStateException(
                    "Browser not launched. Ensure the test class extends BaseTest so @BeforeAll runs.");
        }
        return newContext(browser, new Browser.NewContextOptions().setViewportSize(1920, 1080));
    }

    /**
     * As {@link #newContext()}, on a given browser with given options - how a device row opens a
     * phone-sized, touch, WebKit context rather than the 1920x1080 Chromium one. The same two
     * timeouts and the same step-trail instrumentation apply.
     */
    public static BrowserContext newContext(Browser on, Browser.NewContextOptions options) {
        if (on == null) {
            throw new IllegalStateException(
                    "Browser not launched. Ensure the test class extends BaseTest so @BeforeAll runs.");
        }
        BrowserContext ctx = on.newContext(options);
        // Two budgets, because they are two different questions.
        //
        // Actions keep the short one. Six seconds is deliberately impatient: a control that is not
        // clickable within six seconds is a finding, and waiting thirty for it only makes a red
        // suite slow as well as red.
        //
        // Navigation cannot live on that budget, and sharing it was a bug. A full page load on this
        // site involves third parties it does not control - measured 2026-09-12, the landing page's
        // own document and API calls were done at 5.4s while the Cloudflare beacon arrived at 6.8s
        // and the chat widget was still fetching at 10.3s - so six seconds made every navigation a
        // coin toss and duly failed HOME-0029 on the beacon rather than on anything about the case.
        // BasePage.open() no longer waits for `load` at all, which is the real fix; this is the
        // second half of it, so that a genuinely slow response has room to answer.
        ctx.setDefaultTimeout(ACTION_TIMEOUT_MS);
        ctx.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
        instrument(ctx);
        return ctx;
    }

    /**
     * Records a step, for a case that wants to narrate something the browser events cannot show.
     *
     * <p>The trail under each row in the HTML report is built without any suite calling this -
     * see {@link #instrument(BrowserContext)} - so this is for the facts only the test knows:
     * which account it signed in as, which amount it typed, what it decided to assert. Page
     * objects can call {@link CaseLog} directly for the same reason.
     */
    protected static void step(String text) {
        CaseLog.step(text);
    }

    /**
     * Attaches the listeners that build each case's step trail.
     *
     * <h2>Why it hangs off the context rather than the suites</h2>
     * Every context in the project is opened here - the guest suites through {@link #context()},
     * the signed-in suites by calling {@link #newContext()} from their own {@code @BeforeAll} -
     * so one hook covers every suite and nothing has to be remembered when the next one is
     * written. {@code onPage} then reaches pages the suite never asked for by name - a report
     * the back office opens in a second tab, a gateway a confirmation hands off to.
     *
     * <h2>What is recorded, and what is left out</h2>
     * Page loads, uncaught page errors, console <em>errors</em>, and the site's own API traffic
     * with the status each call came back with. That last one is what turns a green row into
     * evidence: "POST /api/deposit -> 200" is the application agreeing, in its own words, with
     * what the case asserted about the screen. Static assets are filtered out - a single page
     * load pulls in scores of images, stylesheets and scripts, and a trail buried under them is
     * no more readable than no trail at all.
     *
     * <h2>No dialog listener, deliberately</h2>
     * Playwright dismisses a dialog automatically only while nothing is listening for one.
     * Subscribing here to log dialogs would suppress that and leave the first {@code alert()}
     * on the site blocking the page until the case timed out - a report feature that breaks the
     * runs it reports on. The alert boards on this site are page elements rather than browser
     * dialogs anyway, so they show up in the screenshots regardless.
     */
    private static void instrument(BrowserContext ctx) {
        ctx.onPage(opened -> {
            CaseLog.nav("Tab opened: " + shortUrl(opened.url()));
            watch(opened);
        });
    }

    private static void watch(Page target) {
        target.onLoad(loaded -> CaseLog.nav("Loaded " + shortUrl(loaded.url())));
        target.onPageError(error -> CaseLog.console("Uncaught page error: " + oneLine(error)));
        target.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                CaseLog.console(oneLine(message.text()));
            }
        });
        target.onResponse(response -> {
            try {
                String url = response.url();
                if (!isWorthLogging(url)) {
                    return;
                }
                int status = response.status();
                String line = response.request().method() + " " + shortUrl(url) + " -> " + status;
                if (status >= 400) {
                    CaseLog.network(line);
                } else {
                    CaseLog.api(line);
                }
            } catch (RuntimeException e) {
                // A response read after its page went away is not worth a word in the report,
                // and must certainly not be worth a failed case.
            }
        });
    }

    /**
     * True for a call the site made of a back end, false for the furniture around it.
     *
     * One landing-page load fetches well over a hundred images, fonts, stylesheets and script
     * bundles. Logging those would bury the four calls that say what the application actually
     * did, and would hit {@code CaseLog}'s entry cap before the case had finished loading.
     */
    private static boolean isWorthLogging(String url) {
        if (url == null || url.isBlank() || url.startsWith("data:") || url.startsWith("blob:")) {
            return false;
        }
        String path = url.toLowerCase(java.util.Locale.ROOT);
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        for (String extension : ASSET_EXTENSIONS) {
            if (path.endsWith(extension)) {
                return false;
            }
        }
        return true;
    }

    private static final String[] ASSET_EXTENSIONS = {
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".ico", ".bmp", ".avif",
            ".css", ".js", ".mjs", ".map", ".woff", ".woff2", ".ttf", ".otf", ".eot",
            ".mp3", ".mp4", ".webm", ".ogg", ".wav"
    };

    /** The origin is the same on every line, so only the path and query earn the space. */
    private static String shortUrl(String url) {
        if (url == null || url.isBlank()) {
            return "(no address)";
        }
        String trimmed = url.startsWith(BASE_URL) ? url.substring(BASE_URL.length()) : url;
        if (trimmed.isEmpty()) {
            trimmed = "/";
        }
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160) + "...";
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\R", " ").trim();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "...";
    }

    /**
     * Puts a page into a signed-in back office session, as {@link #USERNAME}.
     *
     * <h2>One sign-in, in one place</h2>
     * Every signed-in suite goes through this rather than driving the login form itself, which
     * is the shape the framework came from - NewWeibo_automation carried the same helper - and
     * it is what lets a change to the sign-in flow be made once. What that version also did
     * does <b>not</b> carry over: it switched the wallet currency afterwards, because the
     * member site's cashier pages render a different set of controls per wallet. The back
     * office has no wallet to switch.
     *
     * <p>The account is recorded through {@link CaseLog} rather than merely used, because
     * "which account was this run on" explains more failed runs than any other single fact,
     * and a report that cannot answer it leaves the reader guessing.
     *
     * @param page a page to sign in on - usually one from a context opened by
     *             {@link #newContext()} in the suite's own {@code @BeforeAll}, so the session
     *             outlives a single test
     */
    public static void signIn(Page page) {
        signIn(page, USERNAME, PASSWORD);
    }

    /**
     * As {@link #signIn(Page)}, for a case that needs a particular account rather than the
     * shared operator - one of the role accounts in {@link #ADMIN_USERNAME} and its neighbours.
     *
     * <p>Every sign-in still goes through one place, which is the point of the helper. Note the
     * eviction rule in {@link AdminLoginPage}: signing in as an account that already has a
     * session ends that session, so a case that opens a second role must do it in a context of
     * its own and must not reuse the operator's account to do it.
     */
    public static void signIn(Page page, String username, String password) {
        CaseLog.step("Signing in to the back office at " + BASE_URL);
        new AdminLoginPage(page).signIn(username, password, CAPTCHA);
        settleAfterLogin(page);
    }

    /** How long a fresh sign-in is given to settle before the first case runs. */
    protected static void settleAfterLogin(Page page) {
        page.waitForTimeout(LOGIN_SETTLE_MS);
    }

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        // Printed because this is the setting testers get wrong most often, and a headless
        // run is indistinguishable from a headed one in the log. Surefire plants the value
        // into the forked JVM from the POM, so the default above is not what decides it -
        // see the headless property in pom.xml.
        System.out.println("Launching chromium: headless=" + HEADLESS + " slowMo=" + SLOW_MO);
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(HEADLESS).setSlowMo(SLOW_MO));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    /**
     * The per-test guest page, opened the first time a test asks for it.
     *
     * Use this wherever a case needs an unauthenticated browser - the guest suites use it for
     * everything, and the signed-in suites for their guest-redirect cases. A test that never
     * calls it costs nothing.
     */
    protected Page page() {
        if (page == null) {
            page = context().newPage();
        }
        return page;
    }

    /** The per-test guest context, opened the first time it or {@link #page()} is asked for. */
    protected BrowserContext context() {
        if (context == null) {
            context = newContext();
        }
        return context;
    }

    @BeforeEach
    void setUp() {
        // Deliberately empty. The guest context and page are opened by page() / context() on
        // first use; see the field comment for why they are no longer created here.
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
        page = null;
    }
}
