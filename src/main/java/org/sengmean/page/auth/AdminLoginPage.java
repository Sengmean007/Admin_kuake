package org.sengmean.page.auth;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

/**
 * Page Object for the back office sign-in form at {@code /login}.
 *
 * <h2>This is not the member site's login, and nothing about it carries over</h2>
 * {@code NewWeibo_automation}'s {@code LoginPage} drives a modal that opens from a header
 * button on the landing page, over a Next.js site. The back office is a <b>Nuxt</b>
 * application that serves the form as its own page: there is no modal, no header, and no
 * control to click before the fields exist. {@link #open()} navigates straight to
 * {@code /login} and the inputs are there.
 *
 * <h2>Locators</h2>
 * The three inputs carry no {@code id}, and their classes are all Bootstrap's
 * {@code form-control}, so neither is usable. They do carry stable {@code name} attributes -
 * {@code Username}, {@code Password} and {@code VeriCode} - which is what this class
 * addresses them by. <b>The names are capitalised and {@code VeriCode} is not spelled the way
 * the placeholder reads</b> ("Verification code"), so they are worth reading off the page
 * rather than guessing.
 *
 * <p>There is <b>no {@code form} element</b> on the page - the framework submits through the
 * button's own handler - so a locator anchored on one matches nothing, and pressing Enter in a
 * field is not a reliable substitute for clicking the Log in button.
 *
 * <h2>The verification code</h2>
 * The form shows a CAPTCHA image with a refresh control beside it, and the field is
 * <b>validated first</b>: submitting the form empty answers "Wrong verification code, please
 * fill in again" rather than anything about the missing username. The test environment
 * accepts a fixed bypass value whatever the image shows -
 * {@link org.sengmean.base.BaseTest#CAPTCHA}, {@code 1111} - so a suite never has to read it.
 *
 * <h2>Messages arrive as a jQuery UI dialog, not as a banner</h2>
 * Whatever the site has to say about a sign-in comes back as a {@code div.ui-dialog} titled
 * "Prompt message", with the text in {@code .ui-dialog-content} and a single <b>OK</b> button.
 * It is modal and it does not clear itself, so anything that raises one and then keeps
 * interacting with the page has to dismiss it - see {@link #dismissPrompt()}. This is a
 * different component from the member site's alert board that {@link BasePage} knows about.
 *
 * <h2>One session per operator: a second sign-in evicts the first</h2>
 * Signing in as an account that is already signed in elsewhere does not open a second session -
 * it <b>ends the first one</b>. The evicted browser gets a black modal reading "You've been
 * logged out! … or your account is being accessed from multiple devices", and every page it
 * had open is finished. Observed 2026-09-16, when a suite run signed out a browser tab that was
 * sitting on the Control Panel as the same operator.
 *
 * <p>Two consequences worth knowing before they cost a run. <b>Suites as the same operator
 * cannot run concurrently</b> - two forks signing in as {@link org.sengmean.base.BaseTest#USERNAME}
 * will take turns evicting each other, and the failures land wherever each happened to be
 * rather than on the sign-in. And <b>a run evicts a person</b>: a tester watching the back
 * office on that account is signed out by the next unattended run. Give a parallel run, or a
 * person who needs to stay signed in, their own account through
 * {@link org.sengmean.base.BaseTest#ALT_USERNAME}.
 *
 * <h2>Three wrong passwords lock the account</h2>
 * Observed on 2026-09-16: the fourth rejected attempt stops saying "Wrong username/password,
 * please re-enter" and says "Wrong password for more than 3 times, please contact Customer
 * Service". Any case that deliberately submits a wrong password must therefore use
 * {@link org.sengmean.base.BaseTest#ALT_USERNAME} and be tagged {@code creates-data} - running
 * it against the shared operator account locks every other suite out of the back office, and
 * unlocking is somebody else's manual action.
 */
public class AdminLoginPage extends BasePage {

    /** Where the back office serves the sign-in form, and where it sends a signed-out visitor. */
    public static final String PATH = "login";

    /**
     * The jQuery UI dialog the site raises for every message, successful or not.
     *
     * <p>Scoped to {@code div.ui-dialog} rather than to a bare {@code [role=dialog]}: the widget
     * leaves its own chrome in the DOM, and the surrounding {@code .ui-widget-overlay} matches a
     * dialog-ish selector without carrying any of the text.
     */
    private static final String PROMPT_DIALOG = "div.ui-dialog[role='dialog']";

    private final Locator usernameInput;
    private final Locator passwordInput;
    private final Locator captchaInput;
    private final Locator loginButton;
    private final Locator promptDialog;

    public AdminLoginPage(Page page) {
        super(page);
        this.usernameInput = page.locator("input[name='Username']");
        this.passwordInput = page.locator("input[name='Password']");
        this.captchaInput = page.locator("input[name='VeriCode']");
        this.loginButton = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Log in"));
        this.promptDialog = page.locator(PROMPT_DIALOG);
    }

    /**
     * Opens {@code /login} and waits until the form is actually ready to be filled.
     *
     * <h2>Waiting for the inputs is not enough, and this cost a run to find</h2>
     * The three fields render before the verification code has been issued, so a suite that
     * waits for the username box and then fills all three submits against a session the server
     * has no captcha for. The answer is <b>"Wrong verification code, please fill in again"</b> -
     * for the bypass value, which is otherwise always accepted - and it is intermittent, because
     * whether it happens depends on how much of the round trip the fill beat. Observed
     * 2026-09-16 as one failure in three runs of the same green case.
     *
     * <p>{@link #CAPTCHA_IMAGE} is what settles it. The image is not fetched from an endpoint:
     * it arrives as an inline {@code data:image/...} URI, and the element is in the DOM before
     * then with an <b>empty {@code src}</b>. So an image that carries a {@code data:} URI is the
     * server having issued a code for this session, which is exactly the precondition the form
     * has and does not announce.
     */
    public void open() {
        open(PATH);
        awaitReady();
        usernameInput.waitFor();
        page.locator(CAPTCHA_IMAGE).waitFor();
    }

    /**
     * The verification code image, once it carries a code.
     *
     * <p>The {@code data:image/} prefix is load-bearing rather than decoration - see
     * {@link #open()}. The element is addressed through the form group that holds the
     * {@code VeriCode} input because it has no class, no id and no alt text of its own, and the
     * page's other images are the browser-recommendation logos.
     */
    private static final String CAPTCHA_IMAGE =
            "div.form-group:has(input[name='VeriCode']) img[src^='data:image/']";

    /**
     * Fills the three fields and presses <b>Log in</b>, without waiting for the outcome.
     *
     * <p>Kept separate from {@link #signIn(String, String, String)} so a case can submit and
     * then assert on whatever came back, rather than on having arrived somewhere.
     */
    public void submit(String username, String password, String captcha) {
        CaseLog.data("Account", username);
        usernameInput.fill(username);
        passwordInput.fill(password);
        captchaInput.fill(captcha);
        CaseLog.step("Submitting the back office sign-in form.");
        loginButton.click();
    }

    /**
     * Signs in and waits until the back office has actually loaded behind the form.
     *
     * <p>Waiting on the header's account name rather than on a URL: the site answers a
     * successful sign-in by replacing the page at the <b>root path</b>, so a case that waits
     * for a path to change is waiting for something that has already happened, and one that
     * waits for {@code /login} to disappear can be satisfied by a redirect that has not signed
     * anyone in. The account name in the header is the site's own statement that there is a
     * session.
     */
    public void signIn(String username, String password, String captcha) {
        open();
        submit(username, password, captcha);
        awaitSignInOutcome();
        CaseLog.check("Signed in: the header names " + signedInAccount() + ".");
    }

    /**
     * Waits until the site has answered the sign-in, one way or the other.
     *
     * <h2>Why this is not just {@code signedInMarker().waitFor()}</h2>
     * Because a refused sign-in is not a slow one, and waiting on the header alone cannot tell
     * the difference. The site answers a rejection by raising the "Prompt message" dialog and
     * leaving the form exactly where it was, so the header never appears and the wait ends as
     * {@code TimeoutError: waiting for locator(".user-menu .hidden-xs") to be visible} - which
     * is true, and says nothing whatsoever about the cause. The site knew perfectly well what
     * was wrong and had written it on the screen.
     *
     * <p>So both outcomes are watched, and the dialog's own words are what comes out when it is
     * the dialog that arrives. A run that trips the three-attempt lock, or that meets a
     * verification code the environment did not accept, now says so in one line.
     */
    private void awaitSignInOutcome() {
        long deadline = System.currentTimeMillis() + (long) SIGN_IN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (isSignedIn()) {
                return;
            }
            if (isPromptVisible()) {
                throw new IllegalStateException(
                        "The back office refused the sign-in: \"" + promptText() + "\"");
            }
            page.waitForTimeout(POLL_INTERVAL_MS);
        }
        throw new IllegalStateException(
                "The back office neither signed the operator in nor said why, within "
                        + (long) (SIGN_IN_TIMEOUT_MS / 1000) + "s. Still on " + currentUrl());
    }

    /**
     * How long a sign-in is given to resolve, either way.
     *
     * <p>Longer than the context's six-second action budget on purpose. That budget is the right
     * one for a click on a control that is already on screen; this is a full round trip through
     * an authentication back end followed by the back office rendering itself, and it was
     * measured taking a little over six seconds on an unlucky run. Override with
     * {@code -Dtest.signIn.timeout=<milliseconds>}.
     */
    private static final double SIGN_IN_TIMEOUT_MS =
            Double.parseDouble(org.sengmean.config.ConfigDataProvider.get("test.signIn.timeout", "20000"));

    private static final double POLL_INTERVAL_MS = 250;

    /**
     * The header control that carries the signed-in operator's name, top right.
     *
     * <p>This is the marker for "there is a session": it is absent on {@code /login} and present
     * on every page of the back office.
     */
    private Locator signedInMarker() {
        return page.locator(".user-menu .hidden-xs").first();
    }

    /** True once the back office is showing, i.e. the header names an account. */
    public boolean isSignedIn() {
        return signedInMarker().count() > 0 && signedInMarker().isVisible();
    }

    /** The account the header says is signed in, or empty when none is. */
    public String signedInAccount() {
        if (!isSignedIn()) {
            return "";
        }
        return String.valueOf(signedInMarker().innerText()).trim();
    }

    /**
     * The heading of the page the back office lands on after a sign-in.
     *
     * <h2>Why not the document title</h2>
     * {@code page.title()} is the obvious way to ask this and it does not work here. The back
     * office is a Nuxt application that sets the title during hydration, some way after the
     * header - and therefore after {@link #signIn} has already returned - so reading it at that
     * moment yields an <b>empty string</b> rather than "Control Panel". That is not a race worth
     * waiting out: the {@code h1} is what a person actually sees, Playwright waits for it on its
     * own, and a case that asserts on it is asserting on the screen rather than on a tab label.
     */
    public String landingHeading() {
        Locator heading = page.locator("h1").first();
        heading.waitFor();
        return String.valueOf(heading.innerText()).replaceAll("\\s+", " ").trim();
    }

    /** True while a "Prompt message" dialog is up. */
    public boolean isPromptVisible() {
        return promptDialog.count() > 0 && promptDialog.first().isVisible();
    }

    /** What the "Prompt message" dialog is saying, or empty when none is showing. */
    public String promptText() {
        if (!isPromptVisible()) {
            return "";
        }
        return String.valueOf(promptDialog.first().locator(".ui-dialog-content").innerText())
                .replaceAll("\\s+", " ").trim();
    }

    /**
     * Presses <b>OK</b> on the message the site is showing, if there is one.
     *
     * <p>Safe to call when nothing is up, so a flow can clear the way without first asking.
     */
    public void dismissPrompt() {
        if (!isPromptVisible()) {
            return;
        }
        CaseLog.step("Closing the dialog the site raised: " + promptText());
        promptDialog.first()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("OK"))
                .click();
        promptDialog.first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
    }

    /**
     * Ends the session through the header's own <b>Log Out</b> control.
     *
     * <p>It lives in the account dropdown's footer and is an {@code input type="button"} rather
     * than a {@code button} element, so it is addressed by role and accessible name - which for
     * an input is its {@code value} - instead of by text. The dropdown has to be opened first;
     * the control is in the DOM while it is closed, but not clickable.
     */
    public void logOut() {
        page.locator(".user-menu > a.dropdown-toggle").click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log Out")).click();
        usernameInput.waitFor();
        CaseLog.step("Signed out; the back office returned the sign-in form.");
    }
}
