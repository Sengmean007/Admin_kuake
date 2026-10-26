package org.sengmean.page.client;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

/**
 * The member deposit page, {@code /en/member/deposit/} - a trimmed port of NewWeibo_automation's
 * {@code page.payment.DepositPage}, carrying only what {@code ClientValidDepositTest} needs to
 * fund a qualifying deposit for Admin_weibo's Discount &gt; System Promotion feature (2026-10-06):
 * the Channel &gt; Bank Transfer option, since it is the one fiat option NewWeibo_automation's own
 * deposit suite already confirms in-app without a gateway hand-off. See that project's DepositPage
 * for the other four channels, the invalid-amount cases and the preset-amount controls - none of
 * which this project's own suite needs.
 *
 * <h2>Measured against the live Quark site (2026-10-24)</h2>
 * {@code "Channel"} is one of eight Deposit Channel tabs, and under it "Bank Transfer" is spelled
 * exactly that way among eleven options - both matched the ported constants as written. What did
 * not: see {@link #summaryReady} on the amount summary's own flakiness, and {@link AlertBoard}'s
 * own javadoc - the "Confirm Deposit?" dialog this page's {@link #submit()}/{@link #confirm()} read
 * does not use the CSS-module shape that class was ported with.
 *
 * <p>Call {@link MemberSignInPage#useCurrency} to an HKD wallet <i>before</i> opening this page:
 * the wallet decides which Deposit Channel options even render, and Bank Transfer is a fiat-wallet
 * option.
 */
public class DepositPage extends BasePage {

    public static final String PATH = MemberSignInPage.CLIENT_URL + "/en/member/deposit/";

    public static final String CHANNEL_CHANNEL = "Channel";
    public static final String OPT_BANK_TRANSFER = "Bank Transfer";

    private final Locator channelRow;
    private final Locator optionList;
    private final Locator amountInput;
    private final Locator submitButton;
    private final Locator totalGamePointsValue;
    private final Locator summaryReady;
    private final AlertBoard board;

    public DepositPage(Page page) {
        super(page);
        this.channelRow = page.getByText("Deposit Channel");
        this.optionList = page.getByText("Deposit Options");
        this.amountInput = page.locator("input[type='number']").first();
        this.submitButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("SUBMIT"));
        this.totalGamePointsValue = page.locator(
                "xpath=//span[contains(text(),'Total game points')]/following-sibling::span");
        // Found live 2026-10-24: once an amount is typed, "Deposit turnover req." and "System
        // promotion" render inline straight away, but "Total game points" itself sometimes only
        // appears once the "Show more" link is opened (a modal) - the row the form shows inline
        // first is not consistently the same one run to run. "Deposit turnover req." was inline
        // every time tried, so it is the readiness signal; totalGamePointsValue stays the one
        // actually reported, read after readiness is confirmed.
        this.summaryReady = page.getByText("Deposit turnover req", new Page.GetByTextOptions().setExact(false));
        this.board = new AlertBoard(page);
    }

    public void open() {
        page.navigate(PATH, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        page.waitForTimeout(2000);
        dismissWelcomeBanner();
        closeChatWidget();
    }

    /**
     * The Chatwoot chat widget, closed through its own SDK if available, hidden otherwise - ported
     * from {@code ClientCategoryHomePage}'s own copy. Found live 2026-10-07: {@code
     * ClientValidDepositTest}'s own abort trail ("the deposit form never calculated a summary")
     * showed the widget's own message-polling calls firing repeatedly right up to the moment of
     * abort, the same pattern already documented for the HK client UI - the panel opens itself a
     * few seconds after the page loads and covers whatever is underneath it, including this form's
     * channel/option buttons and amount field.
     */
    public void closeChatWidget() {
        Object state = page.evaluate("""
                () => {
                  const holder = document.querySelector('#cw-widget-holder');
                  if (!holder || holder.classList.contains('woot--hide')) return 'closed';
                  try { if (window.$chatwoot && window.$chatwoot.toggle) { window.$chatwoot.toggle('close'); return 'toggled'; } } catch (e) {}
                  holder.classList.add('woot--hide'); holder.style.display = 'none'; return 'hidden';
                }""");
        if (!"closed".equals(state)) {
            CaseLog.step("The chat widget's panel had opened over the page - " + ("toggled".equals(state) ? "closed it." : "hid it."));
            page.waitForTimeout(800);
        }
    }

    /** True once the Channel row, the Options list and SUBMIT have all rendered. */
    public boolean isLoaded() {
        try {
            channelRow.first().waitFor(new Locator.WaitForOptions().setTimeout(45_000));
            optionList.first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
            return submitButton.isVisible();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void selectChannel(String label) {
        closeChatWidget();
        page.getByText(label, new Page.GetByTextOptions().setExact(true)).first().click();
        page.waitForTimeout(1500);
    }

    public void selectOption(String label) {
        page.getByText(label, new Page.GetByTextOptions().setExact(false)).last().click();
        page.waitForTimeout(1500);
    }

    /**
     * Types an amount into the free-entry field and waits for the summary to register it - the
     * signal the app has taken the amount into its own state. See NewWeibo_automation's
     * {@code DepositPage#enterAmount} for why a submit sent before that renders is a false
     * below-minimum refusal rather than a loud failure.
     */
    public void enterAmount(String amount) {
        amountInput.fill("");
        amountInput.fill(amount);
        page.waitForTimeout(1500);
        if (waitForAmountSummary(4_000)) {
            return;
        }
        amountInput.fill("");
        amountInput.fill(amount);
        page.waitForTimeout(1500);
        waitForAmountSummary(4_000);
    }

    private boolean waitForAmountSummary(int timeoutMillis) {
        for (int waited = 0; waited < timeoutMillis; waited += 500) {
            if (summaryReady.count() > 0) {
                return true;
            }
            page.waitForTimeout(500);
        }
        return summaryReady.count() > 0;
    }

    /**
     * "Total game points" itself, read via its own label - "" if the form has not rendered it yet.
     * Not the readiness signal ({@link #showsAmountSummary()} is, via "Deposit turnover req.", which
     * was inline every time measured) - see this class's own javadoc for why.
     */
    public String totalGamePointsValueText() {
        return totalGamePointsValue.count() > 0 ? totalGamePointsValue.first().innerText().trim() : "";
    }

    public boolean showsAmountSummary() {
        return summaryReady.count() > 0;
    }

    /** Presses SUBMIT, which opens a confirmation popup rather than committing anything by itself. */
    public void submit() {
        closeChatWidget();
        try {
            submitButton.click();
        } catch (PlaywrightException e) {
            submitButton.first().evaluate("b => b.click()");
        }
        page.waitForTimeout(2500);
    }

    public String popupText() {
        return board.innerText();
    }

    public boolean isConfirmDialogVisible() {
        return popupText().contains("Confirm Deposit?");
    }

    /**
     * Presses Confirm, committing a real deposit request order against the member's wallet - the
     * same one-way action NewWeibo_automation's {@code DepositPage#confirm()} is. Reserved for
     * {@code creates-data} tests.
     */
    public boolean confirm() {
        if (!isConfirmDialogVisible() || !board.hasConfirm()) {
            return false;
        }
        CaseLog.step("Pressing Confirm on the deposit summary.");
        board.confirm();
        page.waitForTimeout(6000);
        return true;
    }

    public void cancelConfirmation() {
        if (board.cancel()) {
            page.waitForTimeout(500);
        }
    }
}
