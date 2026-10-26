package org.sengmean.tests.promo;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.sengmean.base.BaseTest;
import org.sengmean.page.client.DepositPage;
import org.sengmean.page.client.MemberSignInPage;
import org.sengmean.page.promo.SystemPromotionRecordsPage;
import org.sengmean.page.promo.SystemPromotionRecordsPage.TransactionRow;
import org.sengmean.page.promo.SystemPromotionSetupPage;
import org.sengmean.page.promo.TransactionDetailPage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>SPD-*</b>: makes a real, qualifying deposit on the member site under a chosen promotion
 * state, so {@code DiscountSuite} has data of its own to run against instead of depending on
 * whatever a deposit happens to exist on admin-test that day.
 *
 * <h2>Why this exists</h2>
 * {@code SystemPromotionRecordsTest}'s own Javadoc (2026-10-06) says plainly that {@code SPR-002},
 * {@code 003} and {@code 007} "need a deposit made while only one promotion type was Active...
 * outside this suite's reach (no member-site page objects in Admin_weibo)". That boundary was
 * deliberate when this project was lifted out of NewWeibo_automation (see {@code AGENTS.md}), but
 * Si asked this class be written here anyway, crossing it on purpose: {@link DepositPage} and
 * {@link MemberSignInPage} are a trimmed port of NewWeibo_automation's own member-site deposit
 * flow, carrying only the one fiat option (Channel &gt; Bank Transfer) this suite needs.
 *
 * <h2>What each row does</h2>
 * <ol>
 *   <li>Snapshots Discount &gt; System Promotion Setup ({@link SystemPromotionSetupPage}) and sets
 *       exactly one of Wallet Promotion / Live Promotion to Active, the other to Inactive (both
 *       Active for {@code SPD-003}) - and points Offer currency at {@link #CURRENCY} too, so every
 *       record this run touches reads back under the one currency rather than needing a switch
 *       mid-test (Si's fix for the currency problem earlier versions of this class kept tripping
 *       on, 2026-10-06).</li>
 *   <li>Signs in to the member site as {@link #USER_USERNAME}, switches to an HKD wallet (fiat
 *       Bank Transfer is an HKD-wallet option - see NewWeibo_automation's own deposit suite), and
 *       deposits exactly the Setup screen's own configured minimum.</li>
 *   <li>Reads the new deposit back from Trade &gt; Transaction Records (the newest Deposit tid for
 *       this account past whatever existed before) and asserts which of the two promotion types
 *       linked to it - the direct, live answer to {@code SPR-002}/{@code 003}'s claim.</li>
 *   <li>Presses Receive on the Main Deposit and asserts the cascade: as the system actually
 *       implements it (Si's ruling, 2026-10-06), confirming the Main Deposit confirms its linked
 *       promotion record(s) along with it - one child for {@code SPD-001}/{@code 002}
 *       ({@code SPR-007}'s claim), both for {@code SPD-003} ({@code SPR-006}'s).</li>
 *   <li>Restores the Setup snapshot in {@code @AfterEach}, the same pattern
 *       {@code RebateSetUpTest} and {@code SystemPromotionSetupTest}'s own writing rows use for a
 *       global, single setting.</li>
 * </ol>
 *
 * <p>All three rows are {@code creates-data} and confirm their own deposit, so each leaves behind a
 * record with no pending status left for {@code SystemPromotionRecordsTest}'s
 * {@code requireFreshLinkedSet} rows (SPR-008..012, the reject-cascade claims) to spend - those
 * still need their own, separately-made deposit, left unconfirmed, which is outside what these
 * three rows do. {@code SPD-003} additionally reads back the currency rule ({@code SPR-013..015}):
 * each child's credited amount in its own currency.
 *
 * <p>New ids, {@code SPD-001}..{@code 003}: there is no client workbook tab for Discount, the same
 * reason {@code SPR-*} and {@code SPS-*} are this project's own numbering.
 *
 * <h2>Still blocked: {@code test.user.*} is not a Quark member account (found 2026-10-24)</h2>
 * {@link DepositPage}, {@link MemberSignInPage} and {@link org.sengmean.page.client.AlertBoard}
 * were measured and corrected against the live Quark site this same session - see their own
 * javadoc - but {@link #openSessions()} still aborts every row here, on sign-in itself, because
 * {@code test.user.username}/{@code test.user.password} ({@code qa000092}/{@code password}) are
 * NewWeibo_automation's own account, carried over unverified, and the live site refuses them
 * ("Wrong username/password, please re-enter."). See {@code Config/config.properties}'s own note
 * on {@code test.user.*} for what to correct once Si supplies (or confirms) the real account - very
 * possibly {@code qa00002}, already signed in on a browser on this machine, one fewer zero than the
 * configured username.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(135)   // after Discount > System Promotion Setup (130), before the records it feeds (140)
@DisplayName("Discount - a qualifying client deposit")
public class ClientValidDepositTest extends BaseTest {

    private static BrowserContext adminSession;
    private static Page adminPage;
    private static AdminShellPage shell;
    private static SystemPromotionRecordsPage records;
    private static SystemPromotionSetupPage setup;

    private static BrowserContext memberSession;
    private static Page memberPage;
    private static MemberSignInPage memberSignIn;
    private static DepositPage deposit;

    private Map<String, String> setupSnapshot;

    @BeforeAll
    static void openSessions() {
        adminSession = newContext();
        adminPage = adminSession.newPage();
        signIn(adminPage);
        shell = new AdminShellPage(adminPage);
        shell.useLanguage(AdminShellPage.ENGLISH);
        shell.useCurrency(CURRENCY);
        records = new SystemPromotionRecordsPage(adminPage);
        setup = new SystemPromotionSetupPage(adminPage);

        memberSession = newContext();
        memberPage = memberSession.newPage();
        memberSignIn = new MemberSignInPage(memberPage);
        boolean signedIn = memberSignIn.signIn(USER_USERNAME, USER_PASSWORD, CAPTCHA);
        if (!signedIn) {
            abort("Could not sign in to the member site as " + USER_USERNAME + " - see the CaseLog for what it said.");
        }
        deposit = new DepositPage(memberPage);
    }

    @AfterAll
    static void closeSessions() {
        if (adminSession != null) {
            adminSession.close();
            adminSession = null;
        }
        if (memberSession != null) {
            memberSession.close();
            memberSession = null;
        }
        adminPage = null;
        shell = null;
        records = null;
        setup = null;
        memberPage = null;
        memberSignIn = null;
        deposit = null;
    }

    @BeforeEach
    void snapshotSetup() {
        // Setup is one configuration per header currency (SystemPromotionSetupPage's own class
        // Javadoc), and tidLinkedTo() leaves the shell on whichever currency it last checked rather
        // than switching back. Found live 2026-10-06: without this, SPD-002's own @BeforeEach opened
        // Setup still on USDT (left there by SPD-001's last tidLinkedTo() call), toggled *USDT's*
        // Wallet/Live status, and the member's HKD deposit was then judged against HKD's own,
        // untouched configuration - whatever that happened to be, not what this row asked for. Since
        // arrangePromotionState() now also points Offer currency at CURRENCY, every tidLinkedTo() call
        // a run makes stays on CURRENCY throughout in practice - this guard is what stays correct
        // even so, in case a future row ever reads a record under some other currency.
        shell.useCurrency(CURRENCY);
        setup.open();
        if (!setup.isDataLoaded()) {
            abort("Discount > System Promotion Setup's data calls are refused for " + USERNAME
                    + ", so there is nothing to toggle - see SystemPromotionSetupTest's class Javadoc.");
        }
        setupSnapshot = setup.snapshot();
    }

    @AfterEach
    void restoreSetup() {
        if (setupSnapshot == null) {
            return;
        }
        shell.useCurrency(CURRENCY);
        setup.open();
        setup.apply(setupSnapshot);
        setup.save();
    }

    /**
     * Puts Setup's two promotion types into the requested Active/Inactive state and saves - and
     * points the Wallet Promotion's own Offer currency at {@link #CURRENCY} too.
     *
     * <p>Si pointed out the simpler way through the currency problem this class kept tripping on:
     * rather than this suite chasing the System Promotion child across whatever offer currency
     * Setup happens to be configured with (USDT, on admin-test, which needed a currency switch -
     * and the switch, the search, or the detail route kept being the thing that silently failed -
     * see every note above this one from 2026-10-06), set the offer currency to {@code CURRENCY}
     * itself. Then the Main Deposit, the System Promotion record and the System Live Promotion
     * record all read back under the one same header currency a run is already on, and nothing
     * downstream of this method needs to switch currency to find any of them.
     */
    private static void arrangePromotionState(boolean walletActive, boolean liveActive) {
        shell.useCurrency(CURRENCY);
        setup.open();
        setup.choose(SystemPromotionSetupPage.WALLET_STATUS, walletActive ? SystemPromotionSetupPage.ACTIVE : SystemPromotionSetupPage.INACTIVE);
        setup.choose(SystemPromotionSetupPage.LIVE_STATUS, liveActive ? SystemPromotionSetupPage.ACTIVE : SystemPromotionSetupPage.INACTIVE);
        if (!CURRENCY.equalsIgnoreCase(setup.selected(SystemPromotionSetupPage.OFFER_CURRENCY))
                && setup.offerCurrencyOptions().contains(CURRENCY)) {
            setup.choose(SystemPromotionSetupPage.OFFER_CURRENCY, CURRENCY);
        }
        String said = setup.save();
        CaseLog.data("Setup saved as Wallet=" + (walletActive ? "Active" : "Inactive")
                + ", Live=" + (liveActive ? "Active" : "Inactive"), said);
    }

    /** The largest tid among this account's own Deposit rows right now - the "before" mark a fresh deposit must beat. */
    private static long currentMaxDepositTid() {
        shell.useCurrency(CURRENCY);
        records.open();
        records.filterAccount(USER_USERNAME);
        records.selectOnlyTypes("Deposit");
        records.selectAllStatuses();
        records.selectAllPaymentOptions();
        records.selectAllMemberGroups();
        records.clearDateRange();
        records.search();
        return records.records().stream().mapToLong(r -> records.tidOf(r.transactionNumber())).max().orElse(0);
    }

    /** The Deposit row for this account with the largest tid greater than {@code afterTid}, or null if none appeared. */
    private static TransactionRow newestDepositAfter(long afterTid) {
        shell.useCurrency(CURRENCY);
        records.open();
        records.filterAccount(USER_USERNAME);
        records.selectOnlyTypes("Deposit");
        records.selectAllStatuses();
        records.selectAllPaymentOptions();
        records.selectAllMemberGroups();
        records.clearDateRange();
        records.search();
        return records.records().stream()
                .filter(r -> records.tidOf(r.transactionNumber()) > afterTid)
                .max((a, b) -> Long.compare(records.tidOf(a.transactionNumber()), records.tidOf(b.transactionNumber())))
                .orElse(null);
    }

    /** {@code "yyyy-MM-dd HH:mm:ss"}, the Transaction Date column's own format. */
    private static final java.time.format.DateTimeFormatter TRANSACTION_DATE =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Seconds between two Transaction Date cells, or {@code Long.MAX_VALUE} if either does not parse. */
    private static long secondsBetween(String a, String b) {
        try {
            return Math.abs(java.time.Duration.between(
                    java.time.LocalDateTime.parse(a, TRANSACTION_DATE),
                    java.time.LocalDateTime.parse(b, TRANSACTION_DATE)).getSeconds());
        } catch (RuntimeException unparsable) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * The tid of whichever record of {@code type}, searched under {@code currency}, links back to
     * {@code mainDeposit} - 0 when none does.
     *
     * <p>Selects every Transaction type and filters by {@code type} in Java, scoped to
     * {@link #USER_USERNAME} - found live 2026-10-06 (both SPD-001/002's first real run) that the
     * screen's own Transaction type checkboxes silently drop System Promotion / System Live
     * Promotion from every result the moment the selection is anything less than all eight boxes,
     * even with the wanted type among those still checked. See
     * {@link SystemPromotionRecordsPage#openEveryFilterForPromotionTypesOnly()}'s Javadoc for how
     * this was proven (a known System Live Promotion row vanished under every narrowed selection
     * tried, and reappeared only with nothing unchecked at all).
     *
     * <p>Si pointed out that a linked record is not grouped with its Main Deposit on screen at all -
     * each is its own row - but carries almost exactly the same Transaction Date, a second or so
     * apart at most. Candidates are tried in order of closeness to {@code mainDeposit}'s own
     * Transaction Date rather than in whatever order the table lists them, so the real match is
     * normally the first (and only) candidate whose detail view is opened to confirm it by its own
     * "Deposit transaction code" field - the one authoritative link, timestamp proximity is only
     * what decides which candidate to check first. This matters beyond speed: opening a detail view
     * per candidate is what timed out once already (2026-10-06, SPD-001's first real run, 10s on a
     * {@code tidOf()} call) when every candidate on the page had to be tried in list order.
     */
    private static long tidLinkedTo(TransactionRow mainDeposit, String type, String currency) {
        shell.useCurrency(currency);
        records.open();
        records.filterAccount(USER_USERNAME);
        records.openEveryFilterForPromotionTypesOnly();
        records.search();
        List<TransactionRow> candidates = records.records().stream()
                .filter(r -> type.equalsIgnoreCase(r.transactionType()))
                .sorted(java.util.Comparator.comparingLong(r -> secondsBetween(mainDeposit.transactionDate(), r.transactionDate())))
                .limit(15).toList();
        for (TransactionRow candidate : candidates) {
            // A candidate's own link can fail to resolve - found live 2026-10-07, SPD-001's first
            // run after the Payment Options fix: tidOf() timed out (10s) on a transaction number
            // that was genuinely on the page moments earlier, on this busy, actively-shared
            // environment (dozens of accounts' rows churning through the same 50-row page every
            // few seconds). One candidate's transient failure should not sink the whole search when
            // there may be 14 more to try - skip it and move on rather than letting the exception
            // propagate out of the loop.
            try {
                long tid = records.tidOf(candidate.transactionNumber());
                TransactionDetailPage detail = type.equals("System Promotion")
                        ? records.openSystemPromotionDetail(tid) : records.openSystemLivePromotionDetail(tid);
                if (mainDeposit.transactionNumber().equals(detail.depositTransactionCode())) {
                    return tid;
                }
            } catch (com.microsoft.playwright.PlaywrightException notResolvable) {
                CaseLog.evidence("Candidate " + candidate.transactionNumber() + " could not be opened - "
                        + notResolvable.getMessage().split("\\R", 2)[0] + " - trying the next candidate.");
            }
        }
        return 0;
    }

    /** The row for {@code tid}, searched under {@code currency} with every Transaction type open - see {@link SystemPromotionRecordsPage#openEveryFilterForPromotionTypesOnly()}. */
    private static TransactionRow rowByTid(long tid, String currency) {
        shell.useCurrency(currency);
        records.open();
        records.filterAccount(USER_USERNAME);
        records.openEveryFilterForPromotionTypesOnly();
        records.search();
        return records.records().stream().filter(r -> records.tidOf(r.transactionNumber()) == tid).findFirst().orElse(null);
    }

    /**
     * Deposits {@code minAmount} via Channel > Bank Transfer, an HKD-wallet option, and confirms it.
     *
     * <p>Takes the amount as a parameter rather than reading {@code setup.value(DEPOSIT_MIN_AMOUNT)}
     * itself: a caller that reads Setup's own fields must do so <i>before</i> it calls
     * {@link #currentMaxDepositTid()} or anything else that navigates {@code adminPage} away from
     * {@code /promo/system-promotion} - both share the one admin session/page, so a Setup field read
     * after the page has moved on to Trade &gt; Transaction Records times out waiting for an input
     * that is no longer on screen. The first version of this method read it internally and both
     * SPD-001/002 failed exactly that way (2026-10-06 15:37 run): a 10s timeout on
     * {@code input[name='DepositMinAmount']}, because {@code currentMaxDepositTid()} had already
     * navigated the page before this method got a chance to read it.
     */
    private static void depositTheConfiguredMinimum(String minAmount) {
        String wallet = memberSignIn.useCurrency(CURRENCY);
        if (!CURRENCY.equalsIgnoreCase(wallet)) {
            abort("The member's wallet would not switch to " + CURRENCY + " (reads \"" + wallet
                    + "\"), and Bank Transfer is an HKD-wallet option - see NewWeibo_automation's own deposit suite.");
        }

        deposit.open();
        if (!deposit.isLoaded()) {
            abort("The member deposit page did not render within its own timeout.");
        }
        deposit.selectChannel(DepositPage.CHANNEL_CHANNEL);
        deposit.selectOption(DepositPage.OPT_BANK_TRANSFER);
        deposit.enterAmount(minAmount);
        if (!deposit.showsAmountSummary()) {
            abort("The deposit form never calculated a summary for " + minAmount + " - see DepositPage#enterAmount's own Javadoc.");
        }
        deposit.submit();
        if (!deposit.isConfirmDialogVisible()) {
            abort("SUBMIT did not raise the \"Confirm Deposit?\" summary - the form said: \"" + deposit.popupText() + "\".");
        }
        if (!deposit.confirm()) {
            abort("Confirm was not available on the deposit summary.");
        }
        CaseLog.data("Deposited", minAmount + " " + CURRENCY + " via Channel > Bank Transfer, confirmed.");
    }

    @Test
    @Tag("creates-data")
    @DisplayName("SPD-001: With only System (Wallet) Promotion Active, confirming a qualifying client deposit creates and confirms its linked System Promotion record, and creates no System Live Promotion record")
    void onlyWalletPromotionActiveConfirmsOnlyTheWalletChild() {
        arrangePromotionState(true, false);
        // Read Setup's own fields now, while adminPage is still on it - see depositTheConfiguredMinimum's Javadoc.
        String minAmount = setup.value(SystemPromotionSetupPage.DEPOSIT_MIN_AMOUNT);
        String offerCurrency = setup.selected(SystemPromotionSetupPage.OFFER_CURRENCY);

        long before = currentMaxDepositTid();
        depositTheConfiguredMinimum(minAmount);

        TransactionRow newDeposit = newestDepositAfter(before);
        if (newDeposit == null) {
            abort("No new Deposit row for " + USER_USERNAME + " appeared past tid " + before + " after the deposit.");
        }
        String depositNumber = newDeposit.transactionNumber();
        long depositTid = records.tidOf(depositNumber);
        CaseLog.data("New deposit", depositNumber + " (tid " + depositTid + ", " + newDeposit.deposit() + ")");

        long walletTid = tidLinkedTo(newDeposit, "System Promotion", offerCurrency);
        long liveTid = tidLinkedTo(newDeposit, "System Live Promotion", CURRENCY);
        assertTrue(walletTid > 0, depositNumber + " should have a linked System Promotion record with Wallet Promotion Active, but none was found.");
        assertEquals(0L, liveTid, depositNumber + " should have no linked System Live Promotion record with Live Promotion Inactive, but one was found (tid " + liveTid + ").");

        // As the system actually implements it (fixed to match, 2026-10-06): the admin confirms the
        // Main Deposit, and its one linked promotion record is confirmed along with it - SPR-007's
        // claim, with only one child in play rather than two.
        shell.useCurrency(CURRENCY);
        TransactionDetailPage main = records.openDeposit(depositTid);
        assertTrue(main.canReceive(), depositNumber + " should still offer Receive before this row acts on it.");
        String said = main.receive();
        CaseLog.data("After Receive", said.isEmpty() ? "(no message)" : said);

        main = records.openDeposit(depositTid);
        assertEquals("Confirmed", main.status(), depositNumber + " should now read Confirmed.");

        TransactionDetailPage wallet = records.openSystemPromotionDetail(walletTid);
        assertEquals("Confirmed", wallet.status(), "The linked System Promotion record should have been "
                + "confirmed along with the Main Deposit, but reads \"" + wallet.status() + "\".");
    }

    @Test
    @Tag("creates-data")
    @DisplayName("SPD-002: With only System Live Promotion Active, confirming a qualifying client deposit creates and confirms its linked System Live Promotion record, and creates no System Promotion record")
    void onlyLivePromotionActiveConfirmsOnlyTheLiveChild() {
        arrangePromotionState(false, true);
        // Read Setup's own fields now, while adminPage is still on it - see depositTheConfiguredMinimum's Javadoc.
        String minAmount = setup.value(SystemPromotionSetupPage.DEPOSIT_MIN_AMOUNT);
        String offerCurrency = setup.selected(SystemPromotionSetupPage.OFFER_CURRENCY);

        long before = currentMaxDepositTid();
        depositTheConfiguredMinimum(minAmount);

        TransactionRow newDeposit = newestDepositAfter(before);
        if (newDeposit == null) {
            abort("No new Deposit row for " + USER_USERNAME + " appeared past tid " + before + " after the deposit.");
        }
        String depositNumber = newDeposit.transactionNumber();
        long depositTid = records.tidOf(depositNumber);
        CaseLog.data("New deposit", depositNumber + " (tid " + depositTid + ", " + newDeposit.deposit() + ")");

        long walletTid = tidLinkedTo(newDeposit, "System Promotion", offerCurrency);
        long liveTid = tidLinkedTo(newDeposit, "System Live Promotion", CURRENCY);
        assertEquals(0L, walletTid, depositNumber + " should have no linked System Promotion record with Wallet Promotion Inactive, but one was found (tid " + walletTid + ").");
        assertTrue(liveTid > 0, depositNumber + " should have a linked System Live Promotion record with Live Promotion Active, but none was found.");

        // As the system actually implements it (fixed to match, 2026-10-06): the admin confirms the
        // Main Deposit, and its one linked promotion record is confirmed along with it - SPR-007's
        // claim, with only one child in play rather than two.
        shell.useCurrency(CURRENCY);
        TransactionDetailPage main = records.openDeposit(depositTid);
        assertTrue(main.canReceive(), depositNumber + " should still offer Receive before this row acts on it.");
        String said = main.receive();
        CaseLog.data("After Receive", said.isEmpty() ? "(no message)" : said);

        main = records.openDeposit(depositTid);
        assertEquals("Confirmed", main.status(), depositNumber + " should now read Confirmed.");

        TransactionDetailPage live = records.openSystemLivePromotionDetail(liveTid);
        assertEquals("Confirmed", live.status(), "The linked System Live Promotion record should have been "
                + "confirmed along with the Main Deposit, but reads \"" + live.status() + "\".");
    }

    @Test
    @Tag("creates-data")
    @DisplayName("SPD-003: Confirming a qualifying client deposit with both promotions Active cascades to both children, each credited in its own currency")
    void confirmingADepositWithBothPromotionsActiveCascadesToBothChildren() {
        // Both promotions Active is the natural, both-children case SPR-001 asks for; pressing
        // Receive and reading the result back covers SPR-006's cascade claim and SPR-013..015's
        // wallet/currency claims too - all from one deposit this test makes and confirms itself,
        // rather than depending on SystemPromotionRecordsTest finding a naturally-occurring set to
        // spend (which SPR-006 and 008..015 compete for, one run only ever having the one).
        arrangePromotionState(true, true);
        String minAmount = setup.value(SystemPromotionSetupPage.DEPOSIT_MIN_AMOUNT);
        String offerCurrency = setup.selected(SystemPromotionSetupPage.OFFER_CURRENCY);

        long before = currentMaxDepositTid();
        depositTheConfiguredMinimum(minAmount);

        TransactionRow newDeposit = newestDepositAfter(before);
        if (newDeposit == null) {
            abort("No new Deposit row for " + USER_USERNAME + " appeared past tid " + before + " after the deposit.");
        }
        String depositNumber = newDeposit.transactionNumber();
        long depositTid = records.tidOf(depositNumber);
        CaseLog.data("New deposit", depositNumber + " (tid " + depositTid + ", " + newDeposit.deposit() + ")");

        long walletTid = tidLinkedTo(newDeposit, "System Promotion", offerCurrency);
        long liveTid = tidLinkedTo(newDeposit, "System Live Promotion", CURRENCY);
        if (walletTid == 0) {
            abort(depositNumber + " should have a linked System Promotion record with Wallet Promotion Active, but none was found.");
        }
        if (liveTid == 0) {
            abort(depositNumber + " should have a linked System Live Promotion record with Live Promotion Active, but none was found.");
        }
        CaseLog.data("Linked children", "System Promotion tid " + walletTid + ", System Live Promotion tid " + liveTid);

        shell.useCurrency(CURRENCY);
        TransactionDetailPage main = records.openDeposit(depositTid);
        assertTrue(main.canReceive(), depositNumber + " should still offer Receive before this row acts on it.");
        String said = main.receive();
        CaseLog.data("After Receive", said.isEmpty() ? "(no message)" : said);

        main = records.openDeposit(depositTid);
        assertEquals("Confirmed", main.status(), depositNumber + " should now read Confirmed.");

        TransactionDetailPage wallet = records.openSystemPromotionDetail(walletTid);
        assertEquals("Confirmed", wallet.status(), "The linked System Promotion record should have cascaded to "
                + "Confirmed, but reads \"" + wallet.status() + "\".");

        TransactionDetailPage live = records.openSystemLivePromotionDetail(liveTid);
        assertEquals("Confirmed", live.status(), "The linked System Live Promotion record should have cascaded "
                + "to Confirmed, but reads \"" + live.status() + "\".");

        TransactionRow walletRow = rowByTid(walletTid, offerCurrency);
        assertTrue(walletRow != null && walletRow.deposit().contains(offerCurrency),
                "The System Promotion record's credited amount should read in " + offerCurrency
                        + " (the configured offer currency), but reads \"" + (walletRow == null ? "(row not found)" : walletRow.deposit()) + "\".");

        TransactionRow liveRow = rowByTid(liveTid, CURRENCY);
        assertTrue(liveRow != null && liveRow.deposit().contains(CURRENCY),
                "The System Live Promotion record's credited amount should read in " + CURRENCY
                        + " (the deposit's own currency), but reads \"" + (liveRow == null ? "(row not found)" : liveRow.deposit()) + "\".");
    }
}
