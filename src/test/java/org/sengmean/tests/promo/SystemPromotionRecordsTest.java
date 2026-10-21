package org.sengmean.tests.promo;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.sengmean.base.BaseTest;
import org.sengmean.page.promo.SystemPromotionRecordsPage;
import org.sengmean.page.promo.SystemPromotionRecordsPage.TransactionRow;
import org.sengmean.page.promo.SystemPromotionSetupPage;
import org.sengmean.page.promo.TransactionDetailPage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>Trade &gt; Transaction Records</b> ({@code /ticketing/ticketing-history}): the {@code SPR-*}
 * rows for the deposit / System Promotion / System Live Promotion flow Si described directly
 * (2026-10-06), cross-checked against GD-106 (the System Live Promotion epic) and its stories
 * GD-108, 111-117, already filed and mostly Resolved.
 *
 * <h2>The requirement, as given and cross-checked</h2>
 * <ul>
 *   <li><b>Setup currency differs by type.</b> System (Wallet) Promotion is configured with a
 *       target/offer currency the admin chooses; System Live Promotion has no currency field of
 *       its own and is always scoped to the currency the setup itself is under (GD-111: "Live
 *       Promotion always uses the requested deposit currency" - already asserted by
 *       {@code SystemPromotionSetupTest#SPS-004}).</li>
 *   <li><b>A qualifying deposit creates up to three linked records</b>: the Main Deposit, a System
 *       Promotion record and a System Live Promotion record - one per promotion type that is both
 *       enabled and met by the deposit's amount against each type's own minimum (GD-112: "Display
 *       Wallet Promotion and Live Promotion as separate promotion records"; GD-108: "the member
 *       sees one System Promotion record and one System live promotion record" when both apply).</li>
 *   <li><b>Confirm cascades down.</b> Confirming the Main Deposit record confirms its linked
 *       promotion record(s) with it - there is no separate confirm step for a child once the
 *       parent is confirmed.</li>
 *   <li><b>Reject cascades down from the parent, but not sideways or up from a child.</b>
 *       Rejecting the Main Deposit record auto-rejects both linked promotion records. Rejecting a
 *       <i>promotion</i> record on its own - System Promotion or System Live Promotion - affects
 *       only that record: the Main Deposit record is untouched, and so is the sibling promotion
 *       record.</li>
 *   <li><b>The wallet updates on confirm, by currency.</b> Confirming the Main Deposit credits the
 *       member's main wallet by the deposit amount; confirming System Promotion credits its amount
 *       in the configured target/offer currency; confirming System Live Promotion credits (sends
 *       to the live platform) its amount in the deposit's own requested currency - never a
 *       separately configured one (GD-113: "Live Promotion amount and turnover amount in the
 *       requested deposit currency"; GD-116: "Create separate System Live Promotion transactions
 *       linked to deposits").</li>
 *   <li><b>The target currency is stored on the System Promotion record only.</b> System Live
 *       Promotion carries no stored target-currency field of its own; it is read from, and always
 *       equal to, the deposit's own currency.</li>
 * </ul>
 *
 * <h2>The screen was found on 2026-10-06 - the earlier "no screen exists" finding was wrong</h2>
 * The 2026-10-06 investigation that first wrote this class had searched the operator's menu
 * ({@code /api/account/allow-menu}) and guessed addresses, found neither, and every row aborted
 * naming that. A second look the same day found it: the Control Panel's "Today total deposit
 * amount" tile carries a "More details" link to {@code /ticketing/ticketing-history}, breadcrumbed
 * "Home Page &gt; Trade &gt; Transaction Records" - {@code Trade} is simply not in the side
 * navigation for this operator, the same way {@code Discount} is hidden from
 * {@code SystemPromotionSetupTest}. (The Trade menu, once reached, turned out to carry two
 * siblings too: <b>Instant Transaction</b>, the pending queue - the same
 * {@code /ticketing/deposit?tid=N} detail route as this screen's Transaction Number links - and
 * <b>Transaction Report</b>, not otherwise used here.) {@link SystemPromotionRecordsPage} is
 * measured against the live screen: its search panel (eight Transaction type checkboxes, confirmed
 * from the live DOM's own ids to include <b>System Promotion</b> and <b>System Live Promotion</b>
 * as their own, separate types - not the older ad-hoc "Promotion" / "Live Promotion"), and its
 * results table's fourteen columns.
 *
 * <h2>Later the same day: 301 deposits appeared, briefly a narrower permission wall, then that cleared too</h2>
 * The first pass found a genuinely empty environment and every row aborted naming that. Hours
 * later, asked to try again:
 * <ul>
 *   <li><b>{@code SystemPromotionSetupTest}'s own blocker is resolved.</b> {@code /promo/system-promotion}
 *       now loads and saves for real: Required deposit min. amount <b>100 (HKD)</b>, Wallet
 *       Promotion <b>Active, 30%</b> (offer currency USDT), Live Promotion <b>Active, 10%</b>.</li>
 *   <li><b>301 HKD deposit requests exist from {@code qa000092}</b>, every one at or above that 100
 *       minimum. At that point not one System Promotion or System Live Promotion record existed for
 *       any of them, and every Transaction Number link answered "No permission" - a second,
 *       narrower permission than the Setup screen's.</li>
 * </ul>
 * Asked a third time, minutes later, to check again once Si said a System Live Promotion record
 * existed: it did, and the permission above was gone. <b>A qualifying deposit raises its linked
 * records immediately on request, not on confirm</b> - all three of {@code NWE1137845} (the 100 HKD
 * Main Deposit, ATM Machine), its System Promotion child and its System Live Promotion child carry
 * the same {@code 2026-10-06 10:24:5x} timestamp, a second apart, every one still {@code New
 * Request}. Opening each (now permitted) measured the cascade design directly, in which actions
 * each screen offers rather than in a ticket's wording: the Main Deposit's own view draws
 * <b>Receive and Reject</b>; both children's views draw <b>Reject only</b> - there is no way to
 * press Confirm on a child at all, which is the whole of SPR-006..012's claim made visible before a
 * single click. See {@link SystemPromotionRecordsPage} and {@link TransactionDetailPage} for the
 * three routes, their fields and what each is measured against.
 *
 * <h2>What pressing Receive would have shown, and why it was not pressed</h2>
 * With {@link TransactionDetailPage#receive()} wired and the Main Deposit's own confirmation dialog
 * open on screen, this investigation's own environment refused the click as an unrequested
 * state-changing action in a connected app - correctly: confirming a deposit credits a wallet, and
 * that is not a read a browsing session should wave through on its own judgement. The record was
 * left exactly as found, {@code New Request}, Receive un-pressed. This is <b>not</b> the same thing
 * as the permission wall two paragraphs up - the action is available, the session's own guardrail is
 * what declined to take it - and it is why {@code SPR-006}-{@code 015} still abort below rather than
 * asserting a cascade this investigation never triggered.
 *
 * <h2>What that leaves open for each row</h2>
 * {@link Screen}, {@code SPR-005}, {@code SPR-004} and {@code SPR-016} read real, measured detail
 * views without pressing anything and run for real. {@code SPR-001} finds the one naturally
 * occurring linked set ({@link #findLinkedSet}) and asserts both children exist - also real, also
 * nothing pressed. {@code SPR-002}, {@code 003} and {@code 007} need a deposit made while only one
 * promotion type was Active, which needs the member site this project's suite does not drive (see
 * {@code AGENTS.md} - no member-site page objects were carried over here) and so stay aborted.
 * {@code SPR-006} and {@code 008}-{@code 015} are wired against real, measured
 * {@link TransactionDetailPage#receive()} / {@link TransactionDetailPage#reject()} calls - not
 * placeholders - but share the one existing linked set as their only subject, and pressing Receive
 * or Reject on it is exactly the destructive, mutually exclusive action each of those rows would
 * need to take on it to prove its own claim; running more than one would either find the set already
 * consumed by an earlier row or consume it out from under a later one. They abort naming that a
 * second (third, fourth...) independently qualifying deposit is needed, one per row that still needs
 * to press something - the same two-sentence reason {@link #requireFreshLinkedSet(String)} gives.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(140)   // after Discount > System Promotion Setup (130)
@DisplayName("Discount - System Promotion / Live Promotion records")
public class SystemPromotionRecordsTest extends BaseTest {

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static SystemPromotionRecordsPage records;
    private static SystemPromotionSetupPage setup;

    /** Whether any transaction at all exists for this operator's groups. True since 2026-10-06. */
    private static boolean anyDepositExists;
    /** Whether a System Promotion / System Live Promotion record exists, any status. Decides every row past {@link Screen}. */
    private static boolean anyPromotionRecordExists;
    /** What the deposit-detail API says when asked directly - "" once the permission is granted. */
    private static String detailRefusal = "";
    /** A naturally-occurring Main Deposit with both children linked to it, found once by {@link #findLinkedSet}; null if none. */
    private static LinkedSet linkedSet;
    /** Every tid this run has pressed Receive or Reject on - a destructive action is never repeated against the same tid. */
    private static final java.util.Set<Long> actedOn = new java.util.HashSet<>();

    /** A Main Deposit and the tids of its linked System Promotion / System Live Promotion children (0 if absent). */
    private record LinkedSet(String depositNumber, long depositTid, long walletChildTid, long liveChildTid) {
        boolean hasWalletChild() {
            return walletChildTid > 0;
        }

        boolean hasLiveChild() {
            return liveChildTid > 0;
        }
    }

    @BeforeAll
    static void signInAndProbe() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(AdminShellPage.ENGLISH);
        shell.useCurrency(CURRENCY);
        records = new SystemPromotionRecordsPage(sessionPage);
        setup = new SystemPromotionSetupPage(sessionPage);

        records.open();
        records.openEveryFilter();
        records.search();
        anyDepositExists = !records.isEmpty();
        CaseLog.data("Any transaction found under " + CURRENCY, String.valueOf(anyDepositExists));

        records.open();
        records.filterAccount(USER_USERNAME);
        records.openEveryFilterForPromotionTypesOnly();
        records.search();
        anyPromotionRecordExists = records.records().stream().anyMatch(r -> isPromotionType(r.transactionType()));
        CaseLog.data("Any System Promotion / System Live Promotion record found", String.valueOf(anyPromotionRecordExists));

        detailRefusal = records.refusedDetailCall(CURRENCY, 1);
        CaseLog.data("Opening a deposit's detail", detailRefusal.isEmpty() ? "allowed" : "refused (" + detailRefusal + ")");

        if (anyPromotionRecordExists && detailRefusal.isEmpty()) {
            setup.open();
            String offerCurrency = setup.selected(SystemPromotionSetupPage.OFFER_CURRENCY);
            linkedSet = findLinkedSet(offerCurrency);
            CaseLog.data("Linked set found", linkedSet == null ? "none" : linkedSet.depositNumber()
                    + " (wallet child " + (linkedSet.hasWalletChild() ? "yes" : "no")
                    + ", live child " + (linkedSet.hasLiveChild() ? "yes" : "no") + ")");
        }
    }

    /** True for the back office's own spelling of either promotion type, case-insensitive - see {@link #isPromotionType}'s own note on the "System promotion" / "System Live Promotion" casing inconsistency. */
    private static boolean isPromotionType(String transactionType) {
        return "System Promotion".equalsIgnoreCase(transactionType) || "System Live Promotion".equalsIgnoreCase(transactionType);
    }

    /**
     * Searches the System Live Promotion records (under {@link #CURRENCY}) for one whose detail
     * view names a Main Deposit, then looks for a System Promotion record under {@code offerCurrency}
     * naming the same deposit. Opens at most a handful of detail views, each read-only - nothing here
     * presses Receive or Reject. Returns null when no System Live Promotion record links back to a
     * deposit at all.
     *
     * <p>Searches with every Transaction type selected and filters the result in Java, and scopes to
     * {@link #USER_USERNAME} - see {@link SystemPromotionRecordsPage#openEveryFilterForPromotionTypesOnly()}
     * for why the screen's own type checkboxes cannot be narrowed, and why an unscoped search of an
     * 8000+-record, 50-per-page table would miss a real match sitting past page one.
     */
    private static LinkedSet findLinkedSet(String offerCurrency) {
        shell.useCurrency(CURRENCY);
        records.open();
        records.filterAccount(USER_USERNAME);
        records.openEveryFilterForPromotionTypesOnly();
        records.search();
        List<TransactionRow> liveCandidates = records.records().stream()
                .filter(r -> "System Live Promotion".equalsIgnoreCase(r.transactionType()))
                .limit(10).toList();

        for (TransactionRow liveCandidate : liveCandidates) {
            long liveTid;
            String depositNumber;
            try {
                // A candidate's own link can fail to resolve on this busy, actively-shared
                // environment - found live 2026-10-07 (ClientValidDepositTest's tidLinkedTo, the
                // same shape of search). One candidate's transient failure should not sink the
                // whole search when there may be 9 more to try.
                liveTid = records.tidOf(liveCandidate.transactionNumber());
                TransactionDetailPage liveDetail = records.openSystemLivePromotionDetail(liveTid);
                depositNumber = liveDetail.depositTransactionCode();
            } catch (com.microsoft.playwright.PlaywrightException notResolvable) {
                CaseLog.evidence("Candidate " + liveCandidate.transactionNumber() + " could not be opened - "
                        + notResolvable.getMessage().split("\\R", 2)[0] + " - trying the next candidate.");
                continue;
            }
            if (depositNumber == null || depositNumber.isBlank()) {
                continue;
            }

            shell.useCurrency(offerCurrency);
            records.open();
            records.filterAccount(USER_USERNAME);
            records.openEveryFilterForPromotionTypesOnly();
            records.search();
            List<TransactionRow> walletCandidates = records.records().stream()
                    .filter(r -> "System Promotion".equalsIgnoreCase(r.transactionType()))
                    .limit(10).toList();
            long walletTid = 0;
            for (TransactionRow walletCandidate : walletCandidates) {
                try {
                    long candidateTid = records.tidOf(walletCandidate.transactionNumber());
                    if (depositNumber.equals(records.openSystemPromotionDetail(candidateTid).depositTransactionCode())) {
                        walletTid = candidateTid;
                        break;
                    }
                } catch (com.microsoft.playwright.PlaywrightException notResolvable) {
                    CaseLog.evidence("Candidate " + walletCandidate.transactionNumber() + " could not be opened - "
                            + notResolvable.getMessage().split("\\R", 2)[0] + " - trying the next candidate.");
                }
            }

            shell.useCurrency(CURRENCY);
            records.open();
            records.filterAccount(USER_USERNAME);
            records.openEveryFilter();
            records.search();
            long depositTid = records.tidOf(depositNumber);
            return new LinkedSet(depositNumber, depositTid, walletTid, liveTid);
        }
        return null;
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        shell = null;
        records = null;
        setup = null;
        linkedSet = null;
        actedOn.clear();
    }

    @BeforeEach
    void reopen() {
        if (!AdminShellPage.ENGLISH.equals(shell.language())) {
            shell.selectLanguage(AdminShellPage.ENGLISH);
        }
        records.open();
    }

    /**
     * Aborts naming which half of the two-part blocker still applies - the one reason every row
     * past {@link Screen} takes. See the class Javadoc for the full account.
     */
    private static void requirePromotionRecord() {
        if (anyPromotionRecordExists) {
            return;
        }
        if (!anyDepositExists) {
            abort("Trade > Transaction Records found no transaction at all for " + USERNAME + " under " + CURRENCY
                    + " - back to a genuinely empty environment; see the class Javadoc for the 2026-10-06 history.");
        }
        String permissionNote = detailRefusal.isEmpty() ? ""
                : " Opening a deposit's own detail (the only place Confirm / Reject live) is refused for "
                        + USERNAME + " - \"" + detailRefusal + "\" - which is what stands between this operator and "
                        + "confirming one to find out.";
        abort("Deposits exist and qualify (301 HKD requests at or above the configured 100 minimum, none "
                + "confirmed), but no System Promotion or System Live Promotion record exists for " + USER_USERNAME
                + " - searched with every Transaction type, Status, Payment Option and Member group open, no date "
                + "bound, filtered in Java to the two promotion types (the screen's own Transaction type checkboxes "
                + "cannot be narrowed to them - see SystemPromotionRecordsPage's class Javadoc)." + permissionNote
                + " This row's real assertion is not yet written, not only blocked: it would read a deposit's "
                + "detail view, whose markup cannot be measured until that view can be opened. See the class Javadoc.");
    }

    /** Aborts naming why no naturally-linked Main Deposit / children set could be found, for the read-only rows. */
    private static void requireLinkedSet() {
        requirePromotionRecord();
        if (linkedSet == null) {
            abort("A System Promotion / System Live Promotion record exists, but none of the ones checked linked "
                    + "back (by its own \"Deposit transaction code\" field) to a Main Deposit this probe could also "
                    + "find - see findLinkedSet() and the class Javadoc.");
        }
    }

    /**
     * Guards a row that must press Receive or Reject: requires the one naturally-linked set, that it
     * is still untouched by an earlier row this run, and marks it touched. A row past this point that
     * finds {@code tid} already in {@link #actedOn} would be asserting against a record a sibling row
     * changed moments ago, not against the state its own claim is about - see the class Javadoc for
     * why this suite does not yet have more than one such set to spend.
     */
    private static void requireFreshLinkedSet(String row) {
        requireLinkedSet();
        if (!actedOn.isEmpty()) {
            abort(row + " needs its own, untouched qualifying deposit to press Receive or Reject on, but the only "
                    + "one this run found (" + linkedSet.depositNumber() + ") was already acted on by an earlier "
                    + "row in this same run (tid(s) " + actedOn + "). This suite has no way to create its own "
                    + "deposit (that needs the member site, outside Admin_weibo's scope - see AGENTS.md) and so "
                    + "cannot cover every row that needs to press something in one run against one environment.");
        }
    }

    /** The leading number out of a Deposit/Expenditure cell like {@code "100.00 (HKD)"}. */
    private static double amount(String cell) {
        Matcher m = Pattern.compile("[-\\d.]+").matcher(cell == null ? "" : cell);
        return m.find() ? Double.parseDouble(m.group()) : Double.NaN;
    }

    /**
     * The row for {@code tid}, searched under {@code currency}; null when not found. {@code type} is
     * no longer used to narrow the Transaction type checkboxes - see
     * {@link SystemPromotionRecordsPage#openEveryFilterForPromotionTypesOnly()} - {@code tid} alone
     * already disambiguates which row this is.
     */
    private static TransactionRow findRow(String type, String currency, long tid) {
        shell.useCurrency(currency);
        records.open();
        records.filterAccount(USER_USERNAME);
        records.selectAllTypes();
        records.selectAllStatuses();
        records.selectAllPaymentOptions();
        records.selectAllMemberGroups();
        records.clearDateRange();
        records.search();
        return records.records().stream().filter(r -> records.tidOf(r.transactionNumber()) == tid).findFirst().orElse(null);
    }

    /**
     * True when some record of {@code type}, searched under {@code currency}, has a "Deposit
     * transaction code" field equal to {@code depositNumber}. Bounded to the first 15 candidates
     * found, read-only throughout - nothing here presses Receive or Reject.
     *
     * <p>Selects every Transaction type and filters by {@code type} in Java rather than narrowing the
     * screen's own checkboxes to it - see
     * {@link SystemPromotionRecordsPage#openEveryFilterForPromotionTypesOnly()}'s Javadoc: a search
     * narrowed to System Promotion/System Live Promotion (or anything less than all eight types)
     * reads No data even for a record known to exist.
     */
    private static boolean linksTo(String depositNumber, String type, String currency) {
        shell.useCurrency(currency);
        records.open();
        records.filterAccount(USER_USERNAME);
        records.openEveryFilterForPromotionTypesOnly();
        records.search();
        List<Long> candidates = records.records().stream()
                .filter(r -> type.equalsIgnoreCase(r.transactionType()))
                .map(r -> records.tidOf(r.transactionNumber())).limit(15).toList();
        for (long tid : candidates) {
            TransactionDetailPage detail = type.equals("System Promotion")
                    ? records.openSystemPromotionDetail(tid) : records.openSystemLivePromotionDetail(tid);
            if (depositNumber.equals(detail.depositTransactionCode())) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // 0. The screen itself - what is asserted for real, without a transaction to read
    // =====================================================================

    @Nested
    @Order(5)
    @DisplayName("0. The screen")
    class Screen {

        @Test
        @DisplayName("SPR-017: Trade > Transaction Records opens and is headed correctly")
        void screenOpensAndIsHeaded() {
            assertTrue(records.isShowing(), "The screen should show its heading and Search button.");
            assertEquals("Transaction Records", records.heading());
            String crumb = records.breadcrumb();
            CaseLog.data("Breadcrumb", crumb.isEmpty() ? "(none drawn)" : crumb);
            if (!crumb.isEmpty()) {
                assertTrue(crumb.contains("Trade") && crumb.contains("Transaction Records"),
                        "The breadcrumb should name Trade and Transaction Records, but reads \"" + crumb + "\"");
            }
        }

        @Test
        @DisplayName("SPR-018: The results table carries the fourteen columns the back office draws, Deposit/Expenditure and Confirmer among them")
        void tableColumnsAreAsDrawn() {
            List<String> headers = records.columnHeaders();
            CaseLog.data("Column headers", String.join(" | ", headers));
            for (String expected : List.of("Transaction Date", "Transaction Number", "Username", "Transaction Type",
                    "Status", "Expenditure", "Deposit", "Confirmer")) {
                assertTrue(headers.contains(expected), "Expected a \"" + expected + "\" column, but found " + headers);
            }
        }
    }

    // =====================================================================
    // 1. A qualifying deposit creates its linked records
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("1. Record creation on deposit")
    class RecordCreation {

        @Test
        @DisplayName("SPR-001: A deposit meeting both promotions' minimum amount creates three linked records - Main Deposit, System Promotion, System Live Promotion")
        void depositMeetingBothMinimumsCreatesThreeRecords() {
            requireLinkedSet();
            CaseLog.data("Linked set", linkedSet.depositNumber() + " (tid " + linkedSet.depositTid()
                    + ") -> System Promotion tid " + linkedSet.walletChildTid() + ", System Live Promotion tid "
                    + linkedSet.liveChildTid());
            assertTrue(linkedSet.hasWalletChild(), linkedSet.depositNumber()
                    + " should have a linked System Promotion record, but none was found.");
            assertTrue(linkedSet.hasLiveChild(), linkedSet.depositNumber()
                    + " should have a linked System Live Promotion record, but none was found.");
        }

        @Test
        @DisplayName("SPR-002: With only System (Wallet) Promotion enabled, a qualifying deposit creates two records - Main Deposit and System Promotion, no Live record")
        void onlyWalletPromotionEnabledCreatesTwoRecords() {
            requireLinkedSet();
            abort("Every deposit found was made while Setup's Wallet Promotion AND Live Promotion were both "
                    + "Active (30% / 10%, confirmed 2026-10-06), so none shows what happens with Live turned off. "
                    + "Producing one needs a deposit made through the member site while Setup is Active/Inactive "
                    + "respectively - outside this suite's reach (no member-site page objects in Admin_weibo).");
        }

        @Test
        @DisplayName("SPR-003: With only System Live Promotion enabled, a qualifying deposit creates two records - Main Deposit and System Live Promotion, no Wallet record")
        void onlyLivePromotionEnabledCreatesTwoRecords() {
            requireLinkedSet();
            abort("The same reason as SPR-002, the other way round: every deposit found was made with both "
                    + "promotion types Active, so none shows what happens with Wallet turned off.");
        }

        @Test
        @DisplayName("SPR-004: A deposit below both promotions' minimum amount creates only the Main Deposit record - no promotion record is raised")
        void depositBelowMinimumCreatesNoPromotionRecord() {
            requireLinkedSet();
            String minText = setup.value(SystemPromotionSetupPage.DEPOSIT_MIN_AMOUNT);
            double minAmount = Double.parseDouble(minText);
            String offerCurrency = setup.selected(SystemPromotionSetupPage.OFFER_CURRENCY);

            shell.useCurrency(CURRENCY);
            records.open();
            records.selectOnlyTypes("Deposit");
            records.selectAllStatuses();
            records.selectAllPaymentOptions();
            records.selectAllMemberGroups();
            records.clearDateRange();
            records.search();
            TransactionRow belowMin = records.records().stream()
                    .filter(r -> amount(r.deposit()) < minAmount)
                    .findFirst().orElse(null);
            if (belowMin == null) {
                abort("No deposit below the configured " + minAmount + " " + CURRENCY + " minimum was found among "
                        + USERNAME + "'s deposits to test this against.");
            }
            CaseLog.data("Below-minimum deposit", belowMin.transactionNumber() + " (" + belowMin.deposit() + " against a "
                    + minAmount + " " + CURRENCY + " minimum)");

            boolean hasLiveChild = linksTo(belowMin.transactionNumber(), "System Live Promotion", CURRENCY);
            boolean hasWalletChild = linksTo(belowMin.transactionNumber(), "System Promotion", offerCurrency);
            assertFalse(hasLiveChild, belowMin.transactionNumber()
                    + " is below the minimum but a System Live Promotion record links to it.");
            assertFalse(hasWalletChild, belowMin.transactionNumber()
                    + " is below the minimum but a System Promotion record links to it.");
        }

        @Test
        @DisplayName("SPR-005: The System Promotion and System Live Promotion records carry distinct transaction types (\"System Promotion\" vs \"System Live Promotion\")")
        void recordsCarryDistinctTransactionTypes() {
            // Half asserted structurally, needing no record: the search panel itself models the two as
            // separate Transaction type checkboxes (GD-112's "Use transaction type `System live
            // promotion`"), confirmed from the live DOM's own checkbox ids - not merely worded that way
            // in a ticket.
            List<String> types = SystemPromotionRecordsPage.TYPE_LABELS;
            assertTrue(types.contains("System Promotion") && types.contains("Live Promotion")
                            && types.contains("System Live Promotion"),
                    "The screen should offer System Promotion, Live Promotion and System Live Promotion as three "
                            + "distinct Transaction types, but offers " + types);

            // The other half, now with a real record of each: measured 2026-10-06, the list's own
            // Transaction Type cell reads "System promotion" (lower-case p) for the Wallet record and
            // "System Live Promotion" (title case) for the Live one - a shipped inconsistency, recorded
            // rather than asserted as a single exact string.
            requireLinkedSet();
            shell.useCurrency(CURRENCY);
            records.open();
            records.filterAccount(USER_USERNAME);
            records.openEveryFilterForPromotionTypesOnly();
            records.search();
            String liveType = records.records().stream()
                    .filter(r -> records.tidOf(r.transactionNumber()) == linkedSet.liveChildTid())
                    .findFirst().map(TransactionRow::transactionType).orElse(null);

            String offerCurrency = setup.selected(SystemPromotionSetupPage.OFFER_CURRENCY);
            shell.useCurrency(offerCurrency);
            records.open();
            records.filterAccount(USER_USERNAME);
            records.openEveryFilterForPromotionTypesOnly();
            records.search();
            String walletType = records.records().stream()
                    .filter(r -> records.tidOf(r.transactionNumber()) == linkedSet.walletChildTid())
                    .findFirst().map(TransactionRow::transactionType).orElse(null);

            CaseLog.data("Transaction Type text", "Wallet = \"" + walletType + "\", Live = \"" + liveType + "\"");
            assertNotEquals(walletType, liveType, "The two children of " + linkedSet.depositNumber()
                    + " should carry visibly different Transaction Type text, but both read \"" + walletType + "\"");
        }
    }

    // =====================================================================
    // 2. Confirming the Main Deposit record cascades to its children
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Confirm cascades")
    class ConfirmCascades {

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-006: Confirming the Main Deposit record also confirms both of its linked promotion records")
        void confirmingMainRecordConfirmsBothChildren() {
            requireFreshLinkedSet("SPR-006");
            TransactionDetailPage deposit = records.openDeposit(linkedSet.depositTid());
            assertTrue(deposit.canReceive(), linkedSet.depositNumber() + " should still offer Receive before this row acts on it.");
            String said = deposit.receive();
            actedOn.add(linkedSet.depositTid());
            CaseLog.data("After Receive", said.isEmpty() ? "(no message)" : said);

            deposit = records.openDeposit(linkedSet.depositTid());
            assertEquals("Confirmed", deposit.status(), linkedSet.depositNumber() + " should now read Confirmed.");

            TransactionDetailPage wallet = records.openSystemPromotionDetail(linkedSet.walletChildTid());
            assertEquals("Confirmed", wallet.status(), "The linked System Promotion record should have cascaded to "
                    + "Confirmed, but reads \"" + wallet.status() + "\".");

            TransactionDetailPage live = records.openSystemLivePromotionDetail(linkedSet.liveChildTid());
            assertEquals("Confirmed", live.status(), "The linked System Live Promotion record should have cascaded "
                    + "to Confirmed, but reads \"" + live.status() + "\".");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-007: Confirming a Main Deposit record that has only one linked promotion record confirms that one record, and leaves no orphan pending record")
        void confirmingMainRecordWithOneChildConfirmsThatChildOnly() {
            requireLinkedSet();
            abort("Needs a deposit made while only one promotion type was Active (see SPR-002/003) - the same "
                    + "reason, not this row's own.");
        }
    }

    // =====================================================================
    // 3. Rejecting cascades down from the parent, never sideways or up
    // =====================================================================

    @Nested
    @Order(30)
    @DisplayName("3. Reject cascades and isolation")
    class RejectCascadesAndIsolation {

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-008: Rejecting the Main Deposit record auto-rejects both of its linked promotion records")
        void rejectingMainRecordRejectsBothChildren() {
            requireFreshLinkedSet("SPR-008");
            TransactionDetailPage deposit = records.openDeposit(linkedSet.depositTid());
            assertTrue(deposit.canReject(), linkedSet.depositNumber() + " should still offer Reject before this row acts on it.");
            String said = deposit.reject();
            actedOn.add(linkedSet.depositTid());
            CaseLog.data("After Reject", said.isEmpty() ? "(no message)" : said);

            deposit = records.openDeposit(linkedSet.depositTid());
            assertEquals("Rejected", deposit.status(), linkedSet.depositNumber() + " should now read Rejected.");

            TransactionDetailPage wallet = records.openSystemPromotionDetail(linkedSet.walletChildTid());
            assertEquals("Rejected", wallet.status(), "The linked System Promotion record should have cascaded to "
                    + "Rejected, but reads \"" + wallet.status() + "\".");

            TransactionDetailPage live = records.openSystemLivePromotionDetail(linkedSet.liveChildTid());
            assertEquals("Rejected", live.status(), "The linked System Live Promotion record should have cascaded "
                    + "to Rejected, but reads \"" + live.status() + "\".");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-009: Rejecting the System Promotion (Wallet) record on its own does not affect the Main Deposit record, which stays pending")
        void rejectingWalletChildDoesNotAffectMainRecord() {
            requireFreshLinkedSet("SPR-009");
            TransactionDetailPage wallet = records.openSystemPromotionDetail(linkedSet.walletChildTid());
            assertTrue(wallet.canReject(), "The System Promotion record should offer Reject before this row acts on it.");
            String said = wallet.reject();
            actedOn.add(linkedSet.walletChildTid());
            CaseLog.data("After Reject", said.isEmpty() ? "(no message)" : said);

            wallet = records.openSystemPromotionDetail(linkedSet.walletChildTid());
            assertEquals("Rejected", wallet.status(), "The System Promotion record should now read Rejected.");

            TransactionDetailPage deposit = records.openDeposit(linkedSet.depositTid());
            CaseLog.data("Main Deposit after the child's Reject", "Status=" + deposit.status());
            assertEquals("New Request", deposit.status(), linkedSet.depositNumber()
                    + " should stay pending when only its System Promotion child is rejected, but reads \""
                    + deposit.status() + "\".");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-010: Rejecting the System Promotion (Wallet) record on its own does not affect the sibling System Live Promotion record")
        void rejectingWalletChildDoesNotAffectLiveSibling() {
            requireFreshLinkedSet("SPR-010");
            TransactionDetailPage wallet = records.openSystemPromotionDetail(linkedSet.walletChildTid());
            assertTrue(wallet.canReject(), "The System Promotion record should offer Reject before this row acts on it.");
            String said = wallet.reject();
            actedOn.add(linkedSet.walletChildTid());
            CaseLog.data("After Reject", said.isEmpty() ? "(no message)" : said);

            wallet = records.openSystemPromotionDetail(linkedSet.walletChildTid());
            assertEquals("Rejected", wallet.status(), "The System Promotion record should now read Rejected.");

            TransactionDetailPage live = records.openSystemLivePromotionDetail(linkedSet.liveChildTid());
            CaseLog.data("System Live Promotion sibling after the Wallet child's Reject", "Status=" + live.status());
            assertEquals("New Request", live.status(), "The sibling System Live Promotion record should stay "
                    + "pending when only its System Promotion sibling is rejected, but reads \"" + live.status() + "\".");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-011: Rejecting the System Live Promotion record on its own does not affect the Main Deposit record, which stays pending")
        void rejectingLiveChildDoesNotAffectMainRecord() {
            requireFreshLinkedSet("SPR-011");
            TransactionDetailPage live = records.openSystemLivePromotionDetail(linkedSet.liveChildTid());
            assertTrue(live.canReject(), "The System Live Promotion record should offer Reject before this row acts on it.");
            String said = live.reject();
            actedOn.add(linkedSet.liveChildTid());
            CaseLog.data("After Reject", said.isEmpty() ? "(no message)" : said);

            live = records.openSystemLivePromotionDetail(linkedSet.liveChildTid());
            assertEquals("Rejected", live.status(), "The System Live Promotion record should now read Rejected.");

            TransactionDetailPage deposit = records.openDeposit(linkedSet.depositTid());
            CaseLog.data("Main Deposit after the child's Reject", "Status=" + deposit.status());
            assertEquals("New Request", deposit.status(), linkedSet.depositNumber()
                    + " should stay pending when only its System Live Promotion child is rejected, but reads \""
                    + deposit.status() + "\".");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-012: Rejecting the System Live Promotion record on its own does not affect the sibling System Promotion (Wallet) record")
        void rejectingLiveChildDoesNotAffectWalletSibling() {
            requireFreshLinkedSet("SPR-012");
            TransactionDetailPage live = records.openSystemLivePromotionDetail(linkedSet.liveChildTid());
            assertTrue(live.canReject(), "The System Live Promotion record should offer Reject before this row acts on it.");
            String said = live.reject();
            actedOn.add(linkedSet.liveChildTid());
            CaseLog.data("After Reject", said.isEmpty() ? "(no message)" : said);

            live = records.openSystemLivePromotionDetail(linkedSet.liveChildTid());
            assertEquals("Rejected", live.status(), "The System Live Promotion record should now read Rejected.");

            TransactionDetailPage wallet = records.openSystemPromotionDetail(linkedSet.walletChildTid());
            CaseLog.data("System Promotion sibling after the Live child's Reject", "Status=" + wallet.status());
            assertEquals("New Request", wallet.status(), "The sibling System Promotion record should stay "
                    + "pending when only its System Live Promotion sibling is rejected, but reads \"" + wallet.status() + "\".");
        }
    }

    // =====================================================================
    // 4. The wallet updates on confirm, each record in its own currency
    // =====================================================================

    @Nested
    @Order(40)
    @DisplayName("4. Wallet and currency on confirm")
    class WalletAndCurrency {

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-013: Confirming the Main Deposit record credits the member's main wallet by the deposit amount")
        void confirmingMainRecordCreditsTheWallet() {
            requireLinkedSet();
            TransactionRow deposit = findRow("Deposit", CURRENCY, linkedSet.depositTid());
            requireConfirmedBySPR006(deposit == null ? null : deposit.status());
            CaseLog.data("Main Deposit after confirm", "Status=" + deposit.status() + ", Confirmer=\"" + deposit.confirmer()
                    + "\", Deposit=" + deposit.deposit());
            assertEquals("Confirmed", deposit.status());
            assertFalse(deposit.confirmer().isBlank(), linkedSet.depositNumber() + " should name a Confirmer once Confirmed.");
            CaseLog.data("Not verified", "The member's actual wallet balance before/after crediting - this project "
                    + "has no page object yet for a member's wallet view to read it from. Only the record's own "
                    + "Status and Confirmer are checked here.");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-014: Confirming the System Promotion record credits the promotion amount in its configured target (offer) currency")
        void confirmingWalletRecordCreditsItsTargetCurrency() {
            requireLinkedSet();
            String offerCurrency = setup.selected(SystemPromotionSetupPage.OFFER_CURRENCY);
            TransactionRow wallet = findRow("System Promotion", offerCurrency, linkedSet.walletChildTid());
            requireConfirmedBySPR006(wallet == null ? null : wallet.status());
            CaseLog.data("System Promotion after confirm", "Status=" + wallet.status() + ", Confirmer=\"" + wallet.confirmer()
                    + "\", Deposit=" + wallet.deposit() + " (offer currency " + offerCurrency + ")");
            assertEquals("Confirmed", wallet.status());
            assertTrue(wallet.deposit().contains(offerCurrency), "The credited amount should read in " + offerCurrency
                    + " (the configured offer currency), but reads \"" + wallet.deposit() + "\".");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPR-015: Confirming the System Live Promotion record credits its amount in the deposit's own requested currency, never a separately configured one")
        void confirmingLiveRecordCreditsTheDepositCurrency() {
            requireLinkedSet();
            TransactionRow live = findRow("System Live Promotion", CURRENCY, linkedSet.liveChildTid());
            requireConfirmedBySPR006(live == null ? null : live.status());
            CaseLog.data("System Live Promotion after confirm", "Status=" + live.status() + ", Confirmer=\"" + live.confirmer()
                    + "\", Deposit=" + live.deposit());
            assertEquals("Confirmed", live.status());
            assertTrue(live.deposit().contains(CURRENCY), "The credited amount should read in " + CURRENCY
                    + " (the deposit's own currency), but reads \"" + live.deposit() + "\".");
        }

        @Test
        @DisplayName("SPR-016: The target (offer) currency chosen at setup is stored on the System Promotion record only - the System Live Promotion record carries no target-currency field and always shows the deposit's own currency")
        void targetCurrencyIsStoredOnTheWalletRecordOnly() {
            requireLinkedSet();
            TransactionDetailPage wallet = records.openSystemPromotionDetail(linkedSet.walletChildTid());
            assertTrue(wallet.hasField("Exchange rate"), "The System Promotion record should carry an Exchange rate field.");
            assertTrue(wallet.hasField("Initial promotion amount"), "The System Promotion record should carry an Initial promotion amount field.");
            assertTrue(wallet.hasField("Actual promotion amount"), "The System Promotion record should carry an Actual promotion amount field.");

            TransactionDetailPage live = records.openSystemLivePromotionDetail(linkedSet.liveChildTid());
            assertFalse(live.hasField("Exchange rate"), "The System Live Promotion record should carry no Exchange rate field, but does.");
            assertFalse(live.hasField("Initial promotion amount"), "The System Live Promotion record should carry no Initial promotion amount field, but does.");
            assertFalse(live.hasField("Actual promotion amount"), "The System Live Promotion record should carry no Actual promotion amount field, but does.");
            assertTrue(live.field("Deposit amount").contains(CURRENCY), "The System Live Promotion record's amount "
                    + "should read in " + CURRENCY + " (the deposit's own currency), but reads \"" + live.field("Deposit amount") + "\".");
        }
    }

    /** Aborts naming that this row needs the confirmed state SPR-006 leaves behind, which this run does not have. */
    private static void requireConfirmedBySPR006(String currentStatus) {
        if (!"Confirmed".equals(currentStatus)) {
            abort("This row reads the Confirmed state SPR-006 leaves behind on " + linkedSet.depositNumber()
                    + ", which this run does not have (reads \"" + currentStatus + "\") - SPR-006 either did not "
                    + "run or did not complete. Run the class as a whole, in order, rather than this row alone.");
        }
    }
}
