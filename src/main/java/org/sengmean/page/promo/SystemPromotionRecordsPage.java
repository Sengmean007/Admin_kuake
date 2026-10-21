package org.sengmean.page.promo;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.sengmean.base.BasePage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Trade &gt; Transaction Records</b> ({@code /ticketing/ticketing-history}): the screen a
 * qualifying deposit's Main Deposit, System Promotion and System Live Promotion records are
 * meant to show, measured live on admin-test on 2026-10-06 (as {@code qa0001}).
 *
 * <h2>How this was found</h2>
 * {@code SystemPromotionRecordsTest} had reported, on 2026-10-06, that no such screen could be
 * found anywhere in the operator's menu or by guessing addresses. It was there all along, just
 * not in the side navigation (the same way {@code Discount} is hidden for this operator): the
 * Control Panel's "Today total deposit amount" tile carries a "More details" link to it, and its
 * own breadcrumb reads "Home Page &gt; Trade &gt; Transaction Records".
 *
 * <h2>What the screen holds</h2>
 * A search panel on the left/right and a results table below, both measured live:
 * <pre>
 *   Transaction Records                                 Home Page &gt; Trade &gt; Transaction Records
 *   (o) Transaction date  ( ) Confirm date   [date range picker]            [Clear]
 *   Transaction code [from] to [to]      Transaction type   [8 checkboxes]  Record type [2 checkboxes]
 *   Amount condition [from] to [to]      Status              [5 checkboxes]  Member group [6 checkboxes]
 *   Account [text]                        Agent code [text]                 Payment Options [N checkboxes, by currency]
 *   Shift search [select]  Confirm party [text]
 *   [Search] [Reset all]   Show memo column
 *   Show [50] Entry
 *   No. | Transaction Date | Transaction Number | Username | Group | Agent code | Transaction Type
 *     | Payment Options | Details | Status | Expenditure | Deposit | Confirmer | Ignore Alert
 * </pre>
 *
 * <h2>The eight Transaction type checkboxes, and why the count matters</h2>
 * {@code Deposit, Withdraw, Promotion, Mission Promotion, Live Promotion, Replenishment Order,
 * System Promotion, System Live Promotion} - confirmed from the live DOM's own ids
 * ({@code chk_tt_Transaction type_0} .. {@code _7}) and from the search API's own
 * {@code where[type][]} values, read off the network call: {@code 1, 2, 5, 10, 11, 6, 12, 13} in
 * that order. <b>System Promotion (12) and System Live Promotion (13) are their own transaction
 * types, distinct from the older ad-hoc "Promotion" (5) and "Live Promotion" (11)</b> - which is
 * the client's GD-112 requirement ("use transaction type `System live promotion`") showing up as
 * real, shipped taxonomy, not just wording in a ticket. {@link #TYPE_LABELS} is the checkbox
 * order; {@link #checkbox(String, String)} finds one by it.
 *
 * <h2>Every other checkbox group follows the same id pattern</h2>
 * {@code chk_tt_<Group label>_<index>}, zero-based in the order the screen draws the group's
 * options - Status (New Request, Processing, Confirmed, Rejected, Init order), Record type
 * (Deposit, Expenditure), Member group (whatever {@code /api/account/member_group} serves - six
 * on admin-test, a total the API itself reports rather than a page the picker is hiding more of
 * behind) and Payment Options (changes with the header currency - HKD offers fourteen+ including
 * {@code UMPay(HKD)} and {@code Bank Transfer}, USDT a different list headed by
 * {@code USDT-TRC20(UPAY)}). {@link #checkbox(String, int)} and {@link #checkbox(String, String)}
 * read any of them by that id.
 *
 * <h2>Update, same day: deposits exist now, but Confirm / Reject is its own permission wall</h2>
 * The paragraph above described an empty environment. Hours later the same day it was not: 301
 * HKD deposit requests from {@code qa000092} appeared (289 {@code New Request}, 12 auto-{@code
 * Rejected} by "system"), every one at or above the 100 HKD minimum {@link SystemPromotionSetupPage}
 * now shows configured and Active (Wallet Promotion 30%, Live Promotion 10% - that screen's own
 * "Permission denied" / "cannot query here" blocker is resolved as of this same session). And yet a
 * search narrowed to only the two promotion types ({@link #openEveryFilterForPromotionTypesOnly()})
 * still answers "No data" - not one System Promotion or System Live Promotion record exists, at any
 * status, for any of the 301 deposits. Two readings are possible from the deposit side alone: either
 * the records are raised only on <em>confirming</em> the Main Deposit (none of the 301 have been),
 * or something else is still missing - Confirm itself is what would tell them apart, and it cannot
 * be reached to find out:
 * <ul>
 *   <li><b>Every Transaction Number link - on this screen and on Trade &gt; Instant Transaction, the
 *       pending queue the same {@code /ticketing/deposit?tid=N} route serves - answers the back
 *       office's own "No permission" prompt.</b> Confirmed by clicking it for real, not only by
 *       {@link #refusedDetailCall}, which asks the same address's API directly and gets the same
 *       refusal. This is a different, narrower permission than the Setup screen's, granted
 *       separately and still missing: listing transactions is allowed, opening one - the only place
 *       Confirm / Reject live - is not.</li>
 *   <li>So nothing here is a locator for a row's Confirm / Reject action, a Details link's target,
 *       or how a child record is read back against its Main Deposit parent. Those still have to be
 *       measured against the live markup of {@code /ticketing/deposit?tid=N}, which cannot happen
 *       before this permission is granted - there being nothing to measure against while every open
 *       attempt is refused before the page draws anything.</li>
 * </ul>
 * See {@code SystemPromotionRecordsTest}'s class Javadoc for the full, dated account and what each
 * {@code SPR-*} row can and cannot assert because of it.
 *
 * <h2>Update, 2026-10-06: a hard-coded column class broke every case in the class</h2>
 * Si's run of {@code DiscountSuite} the same day had all eighteen {@code SPR-*} cases report Error,
 * "the suite failed before this case ran" - {@code @BeforeAll}'s own {@code openEveryFilter()}
 * timed out 10s into {@link #selectAllPaymentOptions()}. {@link #groupLink} had scoped each group's
 * "Select all" link to {@code div.col-md-3}, measured only against Transaction type's own column;
 * Payment Options' far longer checkbox list sits in a column of a different width, so that locator
 * matched nothing. Fixed to find the link from the group's own first checkbox instead (present for
 * every group under the same {@code chk_tt_<group>_0} id regardless of column width) rather than a
 * Bootstrap class - verified live against all five groups (Transaction type, Status, Record type,
 * Member group, Payment Options), each resolving to the same {@code div.input-group-sm} wrapper.
 */
public class SystemPromotionRecordsPage extends BasePage {

    /** The screen's address, relative to the base URL. */
    public static final String PATH = AdminShellPage.TRANSACTION_RECORDS_HREF.replaceFirst("^/", "");

    /** The Transaction type checkboxes, left to right / top to bottom, as the screen draws them. */
    public static final List<String> TYPE_LABELS = List.of("Deposit", "Withdraw", "Promotion",
            "Mission Promotion", "Live Promotion", "Replenishment Order", "System Promotion", "System Live Promotion");

    /** The Status checkboxes, in screen order. */
    public static final List<String> STATUS_LABELS = List.of("New Request", "Processing", "Confirmed", "Rejected", "Init order");

    /** The Record type checkboxes: Deposit credits the wallet, Expenditure debits it. */
    public static final List<String> RECORD_TYPE_LABELS = List.of("Deposit", "Expenditure");

    private static final String HEADING = "h1";
    private static final String TABLE = "table.custom_table";
    private static final String SEARCH_BUTTON = "input[type='button'][value='Search']";
    private static final String RESET_BUTTON = "input[type='button'][value='Reset all']";
    private static final String CLEAR_DATE_BUTTON = "button:has-text('Clear')";
    private static final String TRANSACTION_DATE_RADIO = "input[type='radio'][value='transaction']";
    private static final String CONFIRM_DATE_RADIO = "input[type='radio'][value='confirm']";

    private final Locator headerCells;
    private final Locator rows;

    public SystemPromotionRecordsPage(Page page) {
        super(page);
        this.headerCells = page.locator(TABLE + " thead th");
        this.rows = page.locator(TABLE + " tbody tr").filter(new Locator.FilterOptions().setHas(page.locator("td")));
    }

    // ---------------------------------------------------------------
    // Opening and probing
    // ---------------------------------------------------------------

    /** Opens the screen by address - it carries no side-navigation entry for this operator. */
    public void open() {
        CaseLog.step("Opening Trade > Transaction Records at /" + PATH);
        open(PATH);
        awaitReady();
        try {
            page.locator(SEARCH_BUTTON).waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (RuntimeException notDrawn) {
            CaseLog.evidence("The Transaction Records search form was not drawn within 15s.");
        }
        page.waitForTimeout(1_000);
    }

    /** True while the screen is on - its heading and the search form's Search button. */
    public boolean isShowing() {
        return heading().toLowerCase().contains("transaction records") && page.locator(SEARCH_BUTTON).count() > 0;
    }

    public String heading() {
        Locator h = page.locator(HEADING).filter(new Locator.FilterOptions().setVisible(true));
        return h.count() == 0 ? "" : h.first().innerText().trim();
    }

    public String breadcrumb() {
        Locator crumbs = page.locator(".content-header .breadcrumb, .breadcrumb").filter(new Locator.FilterOptions().setVisible(true));
        return crumbs.count() == 0 ? "" : crumbs.first().innerText().replaceAll("\\s+", " ").trim();
    }

    // ---------------------------------------------------------------
    // The checkbox groups - Transaction type, Status, Record type, Member group, Payment Options
    // ---------------------------------------------------------------

    /**
     * A group's checkbox by its zero-based position, e.g. {@code checkbox("Transaction type", 6)}
     * for System Promotion. The id pattern ({@code chk_tt_<group>_<index>}) is read straight off
     * the live DOM; the space in the group name is part of the id and is matched by attribute
     * selector rather than escaped, which CSS's {@code \ } escaping of spaces does not need here.
     */
    public Locator checkbox(String group, int index) {
        return page.locator("[id='chk_tt_" + group + "_" + index + "']");
    }

    /** A Transaction type / Status / Record type checkbox by its label, from the lists above. */
    public Locator checkbox(String group, String label) {
        List<String> labels = switch (group) {
            case "Transaction type" -> TYPE_LABELS;
            case "Status" -> STATUS_LABELS;
            case "Record type" -> RECORD_TYPE_LABELS;
            default -> throw new IllegalArgumentException(
                    "checkbox(group, label) only knows the fixed groups (Transaction type, Status, Record "
                            + "type); Member group and Payment Options are read from the environment - use "
                            + "checkbox(group, index), or look the index up with memberGroupLabels() / paymentOptionLabels().");
        };
        int index = labels.indexOf(label);
        if (index < 0) {
            throw new IllegalArgumentException("\"" + label + "\" is not one of " + group + "'s checkboxes: " + labels);
        }
        return checkbox(group, index);
    }

    public void check(String group, String label) {
        checkbox(group, label).setChecked(true);
    }

    public void uncheck(String group, String label) {
        checkbox(group, label).setChecked(false);
    }

    /** Only these Transaction type labels end up checked; every other type is unchecked. */
    public void selectOnlyTypes(String... labels) {
        List<String> wanted = List.of(labels);
        for (String label : TYPE_LABELS) {
            checkbox("Transaction type", label).setChecked(wanted.contains(label));
        }
    }

    public void selectAllTypes() {
        groupLink("Transaction type", "Select all").click();
    }

    public void selectAllStatuses() {
        groupLink("Status", "Select all").click();
    }

    public void selectAllPaymentOptions() {
        groupLink("Payment Options", "Select all").click();
    }

    public void selectAllMemberGroups() {
        groupLink("Member group", "Select all").click();
    }

    /**
     * The "Select all" / "Unselect all" link above a checkbox group, found from the group's own
     * first checkbox rather than from a Bootstrap column class.
     *
     * <h2>A hard-coded {@code col-md-3} broke every {@code SystemPromotionRecordsTest} case (2026-10-06)</h2>
     * The first version of this method scoped to {@code div.col-md-3}, measured against Transaction
     * type's own column - whose first child holds, in order, the group's {@code label}, a
     * {@code span} with both links and a {@code div} of the checkboxes. That shape does not hold for
     * every group: {@code @BeforeAll}'s own {@link #selectAllPaymentOptions()} timed out after 10s
     * with "waiting for ... Payment Options ... Select all" - Payment Options' far longer checkbox
     * list sits in a column of its own width, not {@code col-md-3}, so the old locator matched
     * nothing and every case in the class reported Error before it could run ("the suite failed
     * before this case ran"). This version instead walks up from the group's own first checkbox
     * (by its {@code chk_tt_<group>_0} id, present for every group regardless of column width) to
     * the closest ancestor that also contains a matching link - the same relationship (link and
     * checkboxes are sibling children of one wrapper) without depending on which column class wraps
     * it.
     */
    private Locator groupLink(String group, String which) {
        return checkbox(group, 0).locator("xpath=ancestor::*[.//a[normalize-space()='" + which + "']][1]")
                .locator("a").filter(new Locator.FilterOptions().setHasText(exactly(which, false))).first();
    }

    /** The Member group checkboxes' own labels, as the environment currently offers them (six on admin-test). */
    public List<String> memberGroupLabels() {
        return groupLabels("Member group");
    }

    /** The Payment Options checkboxes' own labels, which change with the header currency. */
    public List<String> paymentOptionLabels() {
        return groupLabels("Payment Options");
    }

    private List<String> groupLabels(String group) {
        List<String> out = new ArrayList<>();
        for (int i = 0; ; i++) {
            Locator box = checkbox(group, i);
            if (box.count() == 0) {
                break;
            }
            Locator label = box.locator("xpath=ancestor::label[1]");
            out.add(label.count() > 0 ? label.innerText().trim() : "");
        }
        return out;
    }

    // ---------------------------------------------------------------
    // The other filters
    // ---------------------------------------------------------------

    /** The text input beside a {@code div.input-group-sm}'s label - Account, Agent code, Confirm party. */
    private Locator textField(String label) {
        return page.locator("div.input-group-sm:has(label:text-is('" + label + "')) input").first();
    }

    public void filterAccount(String username) {
        textField("Account").fill(username);
    }

    public void filterAgentCode(String code) {
        textField("Agent code").fill(code);
    }

    public void filterConfirmParty(String confirmer) {
        textField("Confirm party").fill(confirmer);
    }

    /** Both ends of the Transaction code range, from-to. Pass "" to leave one end open. */
    public void filterTransactionCode(String from, String to) {
        Locator group = page.locator("div.input-group-sm:has(label:text-is('Transaction code')) input");
        if (!from.isEmpty()) {
            group.nth(0).fill(from);
        }
        if (!to.isEmpty()) {
            group.nth(1).fill(to);
        }
    }

    /** Switches the duration radio between Transaction date (default) and Confirm date. */
    public void useConfirmDate() {
        page.locator(CONFIRM_DATE_RADIO).check();
    }

    public void useTransactionDate() {
        page.locator(TRANSACTION_DATE_RADIO).check();
    }

    /** Clears the date-range field, which is this screen's way of asking for every date. */
    public void clearDateRange() {
        CaseLog.step("Clearing the transaction date range.");
        page.locator(CLEAR_DATE_BUTTON).first().click();
    }

    // ---------------------------------------------------------------
    // Search / Reset
    // ---------------------------------------------------------------

    public void search() {
        CaseLog.step("Pressing Search on Transaction Records.");
        page.locator(SEARCH_BUTTON).click();
        page.waitForTimeout(1_500);
        awaitStableRows();
    }

    /**
     * Waits until two reads of the result row count, 400ms apart, agree - the same race
     * {@code NotificationListPage.turnPage()} already found on this project's DataTables-based
     * lists, met again here (2026-10-06): {@code tidOf()} timed out looking for a transaction
     * number ({@code NWE1137915}) that was genuinely on the page, and on investigation it was
     * still repainting when the fixed 1,500ms settle wait above elapsed - a real risk on this
     * screen's much bigger result sets (every Transaction type open, scoped to one account's
     * whole history, not the narrow two-type searches the original wait was tuned against) on a
     * busy, actively-shared test environment. A fixed-but-short additional wait is deliberately
     * not used instead: "No data" is a genuine, common answer on this screen (an empty result is
     * not itself a sign of an unsettled table), so this polls for the count to stop changing
     * rather than for it to become non-zero.
     */
    private void awaitStableRows() {
        int previous = -1;
        for (int i = 0; i < 6; i++) {
            int current = rows.count();
            if (current == previous) {
                return;
            }
            previous = current;
            page.waitForTimeout(400);
        }
    }

    public void resetAll() {
        CaseLog.step("Pressing Reset all on Transaction Records.");
        page.locator(RESET_BUTTON).click();
        page.waitForTimeout(800);
    }

    /**
     * Every filter opened up at once: every Transaction type, every Status, both Record types,
     * every Member group and every Payment Option, with no date bound - the broadest a search on
     * this screen can be. What {@link #isEmpty()} is checked against before a row is believed to
     * be genuinely absent rather than filtered out.
     */
    public void openEveryFilter() {
        selectAllTypes();
        selectAllStatuses();
        selectAllPaymentOptions();
        selectAllMemberGroups();
        clearDateRange();
    }

    /**
     * Every Transaction type, Status and Member group opened wide, Payment Options left at its own
     * untouched default, no date bound. Call this and then filter {@link #records()} in Java by
     * {@link TransactionRow#transactionType()} to find System Promotion / System Live Promotion
     * rows specifically - never by narrowing the Transaction type checkboxes to them (they still
     * show up fine under a narrowed type selection; see the corrected finding below).
     *
     * <h2>The real cause (found live 2026-10-07, after a day of chasing the wrong one): clicking
     * Payment Options' own "Select all" link is what excludes these two types</h2>
     * The 2026-10-06 version of this Javadoc blamed the Transaction type checkboxes - narrowing to
     * just the two promotion types, or leaving even one of the other six unchecked, seemed to make
     * System Promotion / System Live Promotion rows vanish. That was a real observation but the
     * wrong culprit: every test that showed the "vanishing" also called {@link #selectAllPaymentOptions()}
     * as part of the same filter-opening sequence, and every one that did not (a fresh page load,
     * touched nowhere) showed them fine. Isolated live by re-building the filter one click at a
     * time against a known-good set of rows: Transaction type's own "Select all" alone - fine;
     * adding Status's "Select all" - still fine; adding Payment Options' "Select all" - every
     * System Promotion / System Live Promotion row vanished immediately, Deposit/Withdraw rows
     * unaffected. Both promotion types carry no Payment Option at all (the column is blank for
     * them), so clicking "Select all" there evidently submits an explicit list of payment channel
     * codes that a record with none can never match - where the page's own untouched default,
     * whatever it actually sends, does not exclude them. This method therefore no longer touches
     * Payment Options in either direction; its own default state is exactly what makes these two
     * types visible at all.
     */
    public void openEveryFilterForPromotionTypesOnly() {
        selectAllTypes();
        selectAllStatuses();
        selectAllMemberGroups();
        clearDateRange();
    }

    /**
     * Asks the deposit detail API directly - {@code /api/account/ticketing/new-ticket/order/<id>},
     * the address behind a Transaction Number link - and says whether it refuses, e.g.
     * {@code "Permission denied"}, or {@code ""} when it answers.
     *
     * <h2>History: refused earlier the same day, granted by the time this was re-checked</h2>
     * First measured 2026-10-06 against an empty environment: every Transaction Number link, on
     * this screen and on Trade &gt; Instant Transaction, answered the back office's own "No
     * permission" prompt, confirmed by UI click and by this call alike, with any id - the check ran
     * before an id was even looked up. Hours later, asked to check again once real records existed,
     * the same links opened real detail views ({@link TransactionDetailPage}) and this call answered
     * 200 for both a real id and an arbitrary one. Nothing in the suite changed that permission; the
     * environment did. Kept as the cheap live probe {@code SystemPromotionRecordsTest} still runs,
     * since it is what tells a future "No permission" morning from a working one without needing a
     * real transaction number in hand first.
     */
    public String refusedDetailCall(String currency, long id) {
        try {
            String body = page.request().get(url("api/account/ticketing/new-ticket/order/" + id + "?currency=" + currency)).text();
            if (body.contains("\"status\":200")) {
                return "";
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
            return m.find() ? m.group(1) : body.substring(0, Math.min(80, body.length()));
        } catch (RuntimeException couldNotAsk) {
            return couldNotAsk.getMessage().split("\\R", 2)[0];
        }
    }

    // ---------------------------------------------------------------
    // A row's detail view - the Main Deposit's Receive / Reject, and its two children's Reject
    // ---------------------------------------------------------------

    /**
     * The numeric id a Transaction Number link addresses its detail route by - read from the link's
     * own {@code href} (e.g. {@code tid=37845}), not guessed from the transaction number's digits:
     * measured 2026-10-06, {@code NWE1137845} carries {@code tid=37845} and {@code NWE1137843}
     * carries {@code tid=37843} - the leading {@code 11} is not part of the id.
     */
    public long tidOf(String transactionNumber) {
        Locator link = page.locator("a").filter(new Locator.FilterOptions().setHasText(exactly(transactionNumber, false))).first();
        String href = String.valueOf(link.getAttribute("href"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("tid=(\\d+)").matcher(href);
        if (!m.find()) {
            throw new IllegalStateException("No tid in \"" + transactionNumber + "\"'s link href (\"" + href + "\").");
        }
        return Long.parseLong(m.group(1));
    }

    /** Opens the Main Deposit detail view - Receive and Reject - by its tid. */
    public TransactionDetailPage openDeposit(long tid) {
        return openDetailRoute("ticketing/deposit?tid=" + tid);
    }

    /**
     * Opens the System Promotion (Wallet) detail view - Reject only - by its tid. Read under the
     * header currency the promotion's own target/offer currency is (USDT on admin-test, measured
     * 2026-10-06); the record does not show while the header is on the deposit's own HKD.
     */
    public TransactionDetailPage openSystemPromotionDetail(long tid) {
        return openDetailRoute("ticketing/system-promo?tid=" + tid);
    }

    /** Opens the System Live Promotion detail view - Reject only - by its tid. */
    public TransactionDetailPage openSystemLivePromotionDetail(long tid) {
        return openDetailRoute("ticketing/deposit-promo?tid=" + tid);
    }

    /**
     * Opens whichever of the three detail routes {@code row}'s own Transaction Type calls for.
     * Matched case-insensitively - measured 2026-10-06, the List's Transaction Type column reads
     * "System promotion" (lower-case p) for a Wallet record and "System Live Promotion" (title
     * case) for a Live one, an inconsistency in the shipped product worth keeping rather than
     * normalising away, so this dispatch does not depend on which casing a given screen used.
     */
    public TransactionDetailPage openDetail(TransactionRow row) {
        long tid = tidOf(row.transactionNumber());
        String type = row.transactionType().toLowerCase(java.util.Locale.ROOT);
        if (type.equals("deposit")) {
            return openDeposit(tid);
        } else if (type.equals("system promotion")) {
            return openSystemPromotionDetail(tid);
        } else if (type.equals("system live promotion")) {
            return openSystemLivePromotionDetail(tid);
        }
        throw new IllegalArgumentException("No known detail route for Transaction Type \"" + row.transactionType() + "\".");
    }

    private TransactionDetailPage openDetailRoute(String path) {
        CaseLog.step("Opening the transaction detail at /" + path + ".");
        page.navigate(url(path));
        awaitReady();
        TransactionDetailPage detail = new TransactionDetailPage(page);
        try {
            page.locator("h1").filter(new Locator.FilterOptions().setVisible(true)).first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        } catch (RuntimeException notDrawn) {
            CaseLog.evidence("The transaction detail view was not headed within 10s.");
        }
        return detail;
    }

    // ---------------------------------------------------------------
    // Reading the table
    // ---------------------------------------------------------------

    /** One row, in the table's own column order. */
    public record TransactionRow(String no, String transactionDate, String transactionNumber, String username,
                                  String group, String agentCode, String transactionType, String paymentOptions,
                                  String details, String status, String expenditure, String deposit, String confirmer) {
    }

    public List<TransactionRow> records() {
        List<TransactionRow> out = new ArrayList<>();
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() < 5) {
                continue;   // the empty list's single "No data" cell
            }
            out.add(new TransactionRow(
                    cell(cells, 0), cell(cells, 1), cell(cells, 2), cell(cells, 3), cell(cells, 4),
                    cell(cells, 5), cell(cells, 6), cell(cells, 7), cell(cells, 8), cell(cells, 9),
                    cell(cells, 10), cell(cells, 11), cell(cells, 12)));
        }
        return out;
    }

    public boolean isEmpty() {
        return records().isEmpty();
    }

    /** The records sharing a Transaction Number - how a Main Deposit and its linked promotion rows would be found. */
    public List<TransactionRow> byTransactionNumber(String transactionNumber) {
        return records().stream().filter(r -> r.transactionNumber().equals(transactionNumber)).toList();
    }

    public List<String> columnHeaders() {
        return headerCells.allInnerTexts().stream().map(t -> t.replaceAll("\\s+", " ").trim()).toList();
    }

    private static String cell(Locator cells, int index) {
        return index < 0 || index >= cells.count() ? "" : cells.nth(index).innerText().replaceAll("\\s+", " ").trim();
    }
}
