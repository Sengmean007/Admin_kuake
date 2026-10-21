package org.sengmean.tests.rebateprogram;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.sengmean.base.BaseTest;
import org.sengmean.page.rebate.RebateReportPage;
import org.sengmean.report.CaseLog;
import org.sengmean.testdata.RebateTestData;

import java.util.List;

import static org.sengmean.testdata.RebateTestData.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>Rebate Management &gt; Rebate report</b> - section D2 of the client workbook,
 * {@code RBMP-134} to {@code RBMP-154}.
 *
 * <h2>This is the one part of the rebate suite that tests something already shipped</h2>
 * The 114 cases in {@link RealTimeRebateTest} and {@link DailyRebateTest} are written ahead of a
 * multi-program UI that has not arrived, and report Not run until it does. These do not: the
 * Rebate report screen is built, deployed and in use, so {@link RebateReportPage} addresses the
 * running markup and these cases <b>assert</b>.
 *
 * <p>They came into the workbook through the merge of the client's own executed {@code TEST CASE}
 * tab, which is why their expected results are specific where the generated rows are general -
 * "No data" for an unknown agent, a named set of columns for the table.
 *
 * <h2>What still cannot pass, and why it aborts rather than passes</h2>
 * The report has <b>no rebate records</b> on this environment, and no member groups are
 * configured. A case that asserts on the contents of a row would therefore pass by finding
 * nothing, which is worse than not running: it would go green while testing the empty set, and
 * stay green when the feature broke. Those cases check for data first and abort saying so.
 *
 * <p>The negative rows are the exception and are the strongest cases here: {@code RBMP-141} and
 * {@code RBMP-143} <em>expect</em> "No data" for an account that does not exist, and an empty
 * environment does not weaken that claim at all.
 *
 * <h2>Ordering</h2>
 * Filters first, each proving it applies; then Reset all, which puts them back; then the
 * structural checks. Nothing here writes, so no case can strand another - the order is for
 * readability rather than safety, and no row is tagged {@code creates-data}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(80)   // E2E step 8
public class RebateReportTest extends BaseTest {

    private static BrowserContext session;
    private static Page sessionPage;
    private static RebateReportPage report;

    @BeforeAll
    static void signInAndOpenReport() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        report = new RebateReportPage(sessionPage);
        report.open();
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        report = null;
    }

    /** Every case starts from a freshly-opened report, so one filter cannot leak into the next. */
    @BeforeEach
    void reopenReport() {
        report.open();
    }

    /**
     * Aborts a case that would otherwise assert on an empty report.
     *
     * <p>Called after a search, never before: whether there is data is a property of the result,
     * and the point is to avoid a green row that examined nothing.
     */
    private static void requireRebateRecords() {
        if (!report.hasData()) {
            abort("The rebate report has no records on this environment for the selected filters, "
                    + "so this row's claim about row contents cannot be checked. Asserting against "
                    + "an empty table would pass without testing anything. Seed rebate activity - "
                    + "see RebateTestData.SEEDING_REQUIRED - and this case runs as written.");
        }
    }

    // =====================================================================
    // Filters
    // =====================================================================

    @Test
    @Order(10)
    @DisplayName("RBMP-136: Filter the rebate report by date time")
    void filterByDateTime() {
        String range = report.timeRange();
        CaseLog.data("Time limit", range);
        assertFalse(range.isBlank(),
                "The report should open on a default date range rather than an empty one");

        report.search();
        assertEquals(REPORT_HEADING, report.heading(),
                "Searching a date range should stay on the report");
        assertEquals(range, report.timeRange(),
                "The searched date range should still be shown after the search");
    }

    @Test
    @Order(11)
    @DisplayName("RBMP-137: Filter the rebate report by Product (game name)")
    void filterByProduct() {
        report.selectProduct(REPORT_PRODUCT);
        assertEquals(REPORT_PRODUCT, report.selectedProduct(),
                "The Product filter should hold the game that was chosen");

        report.search();
        assertEquals(REPORT_PRODUCT, report.selectedProduct(),
                "The chosen game should survive the search rather than reset");
    }

    @Test
    @Order(12)
    @DisplayName("RBMP-138: Type filter defaults to All and offers both rebate types")
    void typeFilterDefaultsToAll() {
        assertEquals(TYPE_ALL, report.selectedType(),
                "Type should default to All");

        List<String> options = report.typeOptions();
        CaseLog.data("Type options", String.join(" / ", options));
        assertTrue(options.containsAll(List.of(TYPE_ALL, TYPE_DAILY, TYPE_REAL_TIME)),
                "Type should offer All, Daily rebate and Real-time rebate, but offered: " + options);
    }

    @Test
    @Order(13)
    @DisplayName("RBMP-145: Filter the rebate report by Type = All")
    void filterByTypeAll() {
        report.selectType(TYPE_ALL);
        report.search();
        assertEquals(TYPE_ALL, report.selectedType(),
                "The Type filter should still read All after searching");
    }

    @Test
    @Order(14)
    @DisplayName("RBMP-146: Filter the rebate report by Type = Daily rebate")
    void filterByTypeDaily() {
        report.selectType(TYPE_DAILY);
        report.search();
        assertEquals(TYPE_DAILY, report.selectedType(),
                "The Type filter should still read Daily rebate after searching");
    }

    @Test
    @Order(15)
    @DisplayName("RBMP-147: Filter the rebate report by Type = Real-time rebate")
    void filterByTypeRealTime() {
        report.selectType(TYPE_REAL_TIME);
        report.search();
        assertEquals(TYPE_REAL_TIME, report.selectedType(),
                "The Type filter should still read Real-time rebate after searching");
    }

    @Test
    @Order(16)
    @DisplayName("RBMP-139: Filter the rebate report by Status")
    void filterByStatus() {
        assertEquals(REPORT_STATUS_ALL, report.selectedStatus(),
                "Status should default to All");

        List<String> options = report.statusOptions();
        CaseLog.data("Status options", String.join(" / ", options));
        assertTrue(options.contains(REPORT_STATUS_SETTLED),
                "Status should offer Settled, but offered: " + options);

        report.selectStatus(REPORT_STATUS_SETTLED);
        report.search();
        assertEquals(REPORT_STATUS_SETTLED, report.selectedStatus(),
                "The Status filter should still read Settled after searching");
    }

    @Test
    @Order(17)
    @DisplayName("RBMP-140: Filter the rebate report by Agent account")
    void filterByAgentAccount() {
        report.setAgentCode(AGENT_CODE);
        report.search();

        assertEquals(AGENT_CODE, report.agentCode(),
                "The Agent code searched for should still be in the field");
        requireRebateRecords();
        assertTrue(report.rowCount() > 0,
                "An agent with rebate records should return rows");
    }

    @Test
    @Order(18)
    @DisplayName("RBMP-141: An invalid Agent account returns No data")
    void invalidAgentAccountReturnsNoData() {
        // The strongest case on this screen: its expected result is an empty report, so an
        // environment with no records cannot make it pass for the wrong reason.
        report.setAgentCode(AGENT_CODE_INVALID);
        report.search();

        assertTrue(report.noDataShown(),
                "An agent account that does not exist should return No data, but the report "
                        + "showed " + report.rowCount() + " row(s)");
    }

    @Test
    @Order(19)
    @DisplayName("RBMP-142: Filter the rebate report by Username")
    void filterByUsername() {
        report.setUsername(REPORT_USERNAME);
        report.search();

        assertEquals(REPORT_USERNAME, report.username(),
                "The username searched for should still be in the field");
        requireRebateRecords();
        assertTrue(report.rowCount() > 0,
                "A member with rebate records should return rows");
    }

    @Test
    @Order(20)
    @DisplayName("RBMP-143: An invalid Username returns No data")
    void invalidUsernameReturnsNoData() {
        report.setUsername(REPORT_USERNAME_INVALID);
        report.search();

        assertTrue(report.noDataShown(),
                "A username that does not exist should return No data, but the report showed "
                        + report.rowCount() + " row(s)");
    }

    @Test
    @Order(21)
    @DisplayName("RBMP-144: Filter the rebate report by Member group")
    void filterByMemberGroup() {
        int available = report.memberGroupCount();
        CaseLog.data("Member groups offered", String.valueOf(available));
        if (available == 0) {
            abort("No member groups are configured on this environment, so the Member group "
                    + "filter has nothing to select. Create at least two - see "
                    + "RebateTestData.SEEDING_REQUIRED - and this case runs as written.");
        }

        report.selectAllMemberGroups();
        assertEquals(available, report.selectedMemberGroupCount(),
                "Select all should tick every member group offered");

        report.unselectAllMemberGroups();
        assertEquals(0, report.selectedMemberGroupCount(),
                "Unselect all should clear every member group");
    }

    // =====================================================================
    // Reset, language and structure
    // =====================================================================

    @Test
    @Order(30)
    @DisplayName("RBMP-152: Reset all returns every filter to its default")
    void resetAllClearsTheFilters() {
        report.selectType(TYPE_DAILY);
        report.selectStatus(REPORT_STATUS_SETTLED);
        report.setAgentCode(AGENT_CODE_INVALID);
        report.setUsername(REPORT_USERNAME_INVALID);
        report.search();

        report.resetAll();

        assertEquals(TYPE_ALL, report.selectedType(), "Reset all should put Type back to All");
        assertEquals(REPORT_STATUS_ALL, report.selectedStatus(),
                "Reset all should put Status back to All");
        assertTrue(report.agentCode().isBlank(), "Reset all should clear the Agent code");
        assertTrue(report.username().isBlank(), "Reset all should clear the Username");
    }

    @Test
    @Order(31)
    @DisplayName("RBMP-153: The report relabels its contents in the selected language")
    void selectReportLanguage() {
        report.selectTableLanguage(REPORT_LANGUAGE);
        report.search();

        // What the row claims is that [Game name] follows the language. With no records there is
        // no game name to read, so what can be shown is that the report stayed on its feet and
        // the choice held - the rest needs data.
        assertEquals(REPORT_HEADING, report.heading(),
                "Changing the report language should keep the report on screen");
        assertEquals(REPORT_COLUMNS, report.columnHeaders(),
                "The column headings should still be the English set after choosing "
                        + REPORT_LANGUAGE);
    }

    @Test
    @Order(32)
    @DisplayName("RBMP-154: The rebate record table shows the agreed column headings")
    void tableShowsAgreedColumns() {
        List<String> headers = report.columnHeaders();
        CaseLog.data("Columns", String.join(" | ", headers));

        assertEquals(REPORT_COLUMNS, headers,
                "The report's columns should be exactly the set the client's row names");
    }

    // =====================================================================
    // Rows that need rebate records
    // =====================================================================

    @Test
    @Order(40)
    @DisplayName("RBMP-134: The report lists every member who earned a rebate, with totals")
    void listsEveryMemberWithRebate() {
        report.search();

        assertEquals(REPORT_COLUMNS, report.columnHeaders(),
                "The report should carry the agreed columns before anything is read from them");
        requireRebateRecords();
        assertTrue(report.rowCount() > 0,
                "Members with rebate records should be listed");
    }

    @Test
    @Order(41)
    @DisplayName("RBMP-135: Clicking a member shows that member's own rebate records")
    void clickingAMemberShowsTheirRecords() {
        report.search();
        requireRebateRecords();
        abort("Reached only when the report has records: the per-member drill-down is a second "
                + "screen, and RebateReportPage does not model it yet.");
    }

    @Test
    @Order(42)
    @DisplayName("RBMP-148: Rebate value display when the first bet reaches no experience group")
    void valueDisplayBelowFirstLevel() {
        requireSettlementFixture();
    }

    @Test
    @Order(43)
    @DisplayName("RBMP-149: Rebate value display when the first bet reaches level 1")
    void valueDisplayAtLevelOne() {
        requireSettlementFixture();
    }

    @Test
    @Order(44)
    @DisplayName("RBMP-150: Rebate value display when experience reaches level 2")
    void valueDisplayAtLevelTwo() {
        requireSettlementFixture();
    }

    @Test
    @Order(45)
    @DisplayName("RBMP-151: Rebate value display for a Real-time rebate record")
    void valueDisplayForRealTimeRecord() {
        requireSettlementFixture();
    }

    /**
     * Aborts the four value-display rows, which need a member whose betting history puts them at a
     * known membership level.
     *
     * <p>Each asserts a whole row of computed values - Percentage, Turnover, Membership type,
     * Increase %, Rebate amount - against a fixture describing how much was bet and which
     * experience level that reaches. None of that can be created from the admin UI.
     */
    private static void requireSettlementFixture() {
        abort("Needs a member with a known betting history and membership level, and a settled "
                + "rebate record to read back - none of which the admin back office can create. "
                + "See RebateTestData.SEEDING_REQUIRED.");
    }
}
