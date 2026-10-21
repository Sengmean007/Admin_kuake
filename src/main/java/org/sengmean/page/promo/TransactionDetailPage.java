package org.sengmean.page.promo;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

/**
 * The detail view behind a Transaction Number link on Trade &gt; Transaction Records (the same
 * routes Trade &gt; Instant Transaction's own links use) - measured live on admin-test 2026-10-06
 * against the three real records one 100 HKD deposit (NWE1137845) raised. Three distinct routes
 * share one near-identical form shape (bootstrap two-column label / value rows, the same
 * {@code .form-group} the rest of this project's "read a labelled field" helpers already use), so
 * one class reads all of them; what differs is which fields and which action button(s) are drawn:
 *
 * <pre>
 *   /ticketing/deposit?tid=N        "Transaction Details - Deposit"        Receive + Reject
 *   /ticketing/system-promo?tid=N   "Transaction Details - Offers"         Reject only
 *   /ticketing/deposit-promo?tid=N  "Transaction Details - Live Promotion" Reject only
 * </pre>
 *
 * <h2>Fields, as measured</h2>
 * The Main Deposit (NWE1137845, 100 HKD, ATM Machine) carries: Deposit transaction code, Deposit
 * transaction date, Deposit amount, Fund deposit method ("Deposit"), Status, Total approved
 * amounts, Multiple, Turnover requirement, Deposit Channel, Deposit Options, Remark.
 *
 * <p>The System Promotion record it raised (tid 37846, read under the header's USDT - the
 * configured offer currency, not the deposit's own HKD) carries: the same code / date / amount
 * (here {@code USDT 3.82}) / method ("System promotion") / Status, plus <b>Initial promotion
 * amount</b> ({@code HKD 30.00} - 30% of the deposit, in the deposit's own currency),
 * <b>Exchange rate</b> ({@code 0.1274616}), <b>Actual promotion amount</b> ({@code USDT 3.82} -
 * the converted figure), Multiple, Turnover requirement, and an "Offers set themes" note
 * ("系统自动优惠(存款单NWE1137845,赠送百分率 30.00%)"). This is SPR-016's claim made concrete: a
 * target/offer currency and its conversion live on this record and nowhere else.
 *
 * <p>The System Live Promotion record it raised (tid 37847, read under HKD - the deposit's own
 * currency) carries only: code / date / amount ({@code HKD 10.00} - 10% of the deposit, the
 * deposit's own currency, no conversion) / method ("System Live Promotion") / Status, Multiple,
 * Turnover requirement, and its own "Offers set themes" note ("系统直播币优惠(存款单NWE1137845,
 * 赠送百分率 10.00%)"). No Initial / Exchange rate / Actual amount fields exist here at all - the
 * other half of SPR-016.
 *
 * <h2>Confirmed live, not yet exercised</h2>
 * All three records were readable this way the moment they existed, each still {@code New
 * Request}: the Main Deposit draws Receive and Reject, both children draw Reject only - the
 * cascade rule in SPR-006..012, visible in which button each screen draws before a single confirm
 * or reject has been pressed anywhere. {@link #receive()} and {@link #reject()} are wired against
 * the real "Confirm to receive deposit?" prompt (the same {@link PromptMessage} component the rest
 * of the back office uses), but neither was pressed by this investigation - see
 * {@code SystemPromotionRecordsTest}'s class Javadoc for why pressing one is left to whoever runs
 * the suite rather than to the investigation that wrote it.
 */
public class TransactionDetailPage extends BasePage {

    private static final String RECEIVE = "button.btn-info";
    private static final String REJECT = "button.btn-danger";

    public TransactionDetailPage(Page page) {
        super(page);
    }

    /** "Transaction Details - Deposit" / "- Offers" / "- Live Promotion", whichever route this is. */
    public String heading() {
        Locator h = page.locator("h1").filter(new Locator.FilterOptions().setVisible(true));
        return h.count() == 0 ? "" : h.first().innerText().trim();
    }

    /** True once the id-keyed record has rendered - its heading, and a Status field to read. */
    public boolean isShowing() {
        return heading().toLowerCase().contains("transaction details") && hasField("Status");
    }

    /**
     * A labelled field's value, read whether it is a plain input/textarea or a styled plain value
     * (Status, drawn as a coloured block rather than an input) - {@code null} when this view does
     * not draw that label at all (e.g. "Exchange rate" on the Main Deposit or the Live Promotion
     * record).
     */
    public String field(String label) {
        Locator group = page.locator("div.form-group").filter(new Locator.FilterOptions()
                .setHas(page.locator("label").filter(new Locator.FilterOptions().setHasText(exactly(label, false)))));
        if (group.count() == 0) {
            return null;
        }
        Locator value = group.first().locator(".col-sm-8").first();
        Object raw = value.evaluate("e => { const i = e.querySelector('input, textarea'); return i ? i.value : e.textContent; }");
        return raw == null ? "" : String.valueOf(raw).replaceAll("\\s+", " ").trim();
    }

    public boolean hasField(String label) {
        return field(label) != null;
    }

    public String status() {
        return field("Status");
    }

    public String depositTransactionCode() {
        return field("Deposit transaction code");
    }

    public boolean canReceive() {
        return page.locator(RECEIVE).count() > 0;
    }

    public boolean canReject() {
        return page.locator(REJECT).count() > 0;
    }

    /**
     * Presses Receive - the Main Deposit record only, {@link #canReceive()} says whether this view
     * offers it - and answers the "Confirm to receive deposit?" prompt, returning whatever the back
     * office says afterwards.
     */
    public String receive() {
        CaseLog.step("Pressing Receive.");
        page.locator(RECEIVE).first().click();
        return PromptMessage.confirmAndRead(page, 8_000);
    }

    /** Presses Reject and answers its own confirmation, returning whatever the back office says afterwards. */
    public String reject() {
        CaseLog.step("Pressing Reject.");
        page.locator(REJECT).first().click();
        return PromptMessage.confirmAndRead(page, 8_000);
    }
}
