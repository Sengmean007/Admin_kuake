package org.sengmean.page.client;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.sengmean.base.BasePage;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.report.CaseLog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signs a member in on the <b>client platform</b> - the member site - for the rows that read what a
 * back-office feature delivered to a member (the Notification suite's Message Center rows).
 *
 * <h2>Ported, not invented</h2>
 * The steps are NewWeibo_automation's {@code LoginPage.signInOnVisibleForm}, which drives the member
 * site's form by its visible copy - "Log In", and the Username / Password / Verification Code
 * placeholders - because that is what survives both the desktop modal and the phone layout. The test
 * site accepts {@code 1111} as the verification code whatever the image shows ({@code test.captcha}).
 *
 * <p>The site raises its promotional welcome banner twice - once before sign-in and once after - and
 * either one swallows the clicks that follow, so {@link #dismissBanners()} runs at both points.
 */
public class MemberSignInPage extends BasePage {

    /** The member site's origin, {@code client.url} in {@code Config/}. */
    public static final String CLIENT_URL = ConfigDataProvider.get("client.url", "https://test-v2.1083.city")
            .replaceAll("/+$", "");

    /** Reads the account panel's "Main wallet &lt;CUR&gt; &lt;balance&gt;" line - textContent, since the panel is a collapsed dropdown. */
    private static final Pattern WALLET_CURRENCY = Pattern.compile("Main wallet\\s*([A-Z]{3,4})");
    /** Where the wallet switch is made: the landing page, signed in, with {@code ?currency=}. */
    private static final String SWITCH_PATH = "/en/";
    private static final double CURRENCY_SETTLE_MS = 3000;

    public MemberSignInPage(Page page) {
        super(page);
    }

    /** Opens the member site and signs in; returns false, with the site's words logged, when it refuses. */
    public boolean signIn(String username, String password, String captcha) {
        CaseLog.step("Signing in to the member site at " + CLIENT_URL + " as " + username + ".");
        page.navigate(CLIENT_URL + "/", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        page.waitForTimeout(2500);
        dismissBanners();
        page.getByText("Log In", new Page.GetByTextOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true)).first().click();
        field("Username").fill(username);
        field("Password").fill(password);
        field("Verification Code").fill(captcha);
        page.locator("button[class*='submit']").filter(new Locator.FilterOptions().setVisible(true)).first().click();
        page.waitForTimeout(3500);
        dismissBanners();
        boolean signedIn = page.getByText("Log In", new Page.GetByTextOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true)).count() == 0;
        CaseLog.data("Member signed in", signedIn + (signedIn ? "" : " - the site said: " + visibleAlertText()));
        return signedIn;
    }

    private Locator field(String placeholder) {
        Locator input = page.getByPlaceholder(placeholder, new Page.GetByPlaceholderOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true)).first();
        input.waitFor(new Locator.WaitForOptions().setTimeout(8_000));
        return input;
    }

    /**
     * The member's wallet currency, "HKD" or "USDT", or "" when the account panel is not on the page. Ported from
     * NewWeibo_automation's {@code CurrencySwitcher.currentCurrency}.
     */
    public String currentCurrency() {
        try {
            String text = page.locator("body").textContent();
            if (text == null) {
                return "";
            }
            Matcher m = WALLET_CURRENCY.matcher(text);
            return m.find() ? m.group(1) : "";
        } catch (RuntimeException navigating) {
            return "";
        }
    }

    /** How many extra 1s rounds to poll for the currency panel once the fixed settle wait has elapsed, when the site's own second navigation is still running past it. */
    private static final int CURRENCY_POLL_ROUNDS = 5;

    /**
     * Puts the signed-in member onto {@code currency} - the way NewWeibo_automation's {@code CurrencySwitcher}
     * does, by {@code ?currency=} on the landing page, which the site reads and then navigates again on its own.
     * The shared QA account drifts between HKD and USDT, and the back office keeps its notifications per
     * currency, so a member reading the inbox on the other wallet sees none of what the suite created.
     * Returns the currency in force afterwards, which is not necessarily the one asked for: an account with no
     * wallet in it stays where it was. Switches when the panel is unreadable rather than trusting a blank.
     *
     * <h2>The fixed settle wait does not always cover the site's own second navigation</h2>
     * Found live 2026-10-06 ({@code ClientValidDepositTest}'s own first real run): the site's second,
     * self-triggered navigation (dropping the now-read {@code ?currency=} from the URL) does not always
     * finish within {@link #CURRENCY_SETTLE_MS} of the first one loading - one run's trail showed it still
     * loading 3.6s after the first page settled, past the fixed wait here, so {@link #currentCurrency()}
     * read the panel mid-reload and came back empty. Rather than lengthen the fixed wait (which only
     * narrows the window, the same mistake already made and unmade elsewhere on this project for an
     * analogous "second navigation" race in {@code SystemPromotionRecordsPage#search()}), this now polls
     * {@link #currentCurrency()} for up to {@link #CURRENCY_POLL_ROUNDS} more seconds, re-dismissing the
     * banner each round since the second navigation can raise it again.
     */
    public String useCurrency(String currency) {
        String inForce = currentCurrency();
        if (currency.equalsIgnoreCase(inForce)) {
            CaseLog.data("Member wallet currency", inForce);
            return inForce;
        }
        CaseLog.step("Switching the member's wallet from " + (inForce.isEmpty() ? "an unreadable currency" : inForce) + " to " + currency + ".");
        try {
            page.navigate(CLIENT_URL + SWITCH_PATH + "?currency=" + currency, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            awaitReady();
            page.waitForTimeout(CURRENCY_SETTLE_MS);   // the site navigates again once it has read the parameter
            dismissBanners();
            for (int round = 0; round < CURRENCY_POLL_ROUNDS && currentCurrency().isEmpty(); round++) {
                page.waitForTimeout(1_000);
                dismissBanners();
            }
        } catch (RuntimeException didNotComplete) {
            CaseLog.data("Currency switch", "did not complete: " + didNotComplete.getMessage());
        }
        String now = currentCurrency();
        CaseLog.data("Member wallet currency", now.isEmpty() ? "not readable after the switch" : now);
        return now;
    }

    /** Closes the welcome banner if it is up: its close control, else Escape. */
    public void dismissBanners() {
        Locator close = page.locator("[class*='close'], [aria-label*='close' i]")
                .filter(new Locator.FilterOptions().setVisible(true));
        for (int i = 0; i < 3 && close.count() > 0; i++) {
            try {
                close.first().click(new Locator.ClickOptions().setTimeout(2000));
                page.waitForTimeout(500);
            } catch (RuntimeException notClickable) {
                page.keyboard().press("Escape");
                break;
            }
        }
    }
}
