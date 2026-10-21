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
import org.sengmean.base.BasePage;
import org.sengmean.base.BaseTest;
import org.sengmean.page.rebate.DailyRebatePage;
import org.sengmean.page.rebate.RealTimeRebatePage;
import org.sengmean.page.rebate.RebateReportPage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import static org.sengmean.testdata.RebateTestData.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>C4. Regression</b> - {@code RBMP-124} to {@code RBMP-127}: what the multi-program upgrade
 * was not allowed to break.
 *
 * <h2>The upgrade has happened, which changes what these rows can say</h2>
 * When the workbook was written the multi-program feature was unreleased and every row here was
 * a prediction. It shipped: measured on 2026-09-18, {@code /rebate/set-up} serves the program
 * list, both Create controls, the search box and the status filter. So these cases are no
 * longer asking "what will the migration do" but "what did it do", and they are written to
 * read the answer off the running environment.
 *
 * <h2>What the environment says, and why most of these still abort</h2>
 * The post-upgrade screen has <b>global settings that carried over</b> - Min. Claim, Expire
 * time, Release time and Status all hold values - and a <b>program list that is empty on both
 * tabs</b>. Those two facts together are the interesting part of this section, and
 * {@link #singleProgramDataMigrated()} records them rather than passing or failing on them:
 * whether an empty list is correct depends on whether this environment had a single-program
 * configuration to migrate, which is AQ-12 and is not a question a browser can answer.
 *
 * <p>The remaining three need evidence that is not on any admin screen - a member's credited
 * rebate, a report row from before the upgrade, a snapshot of the assignments as they were -
 * and each aborts naming what it needs, rather than asserting against the empty set and going
 * green for it.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(30)   // E2E step 3
public class RebateMigrationTest extends BaseTest {

    /** Where the back office serves the rebate settings screen. */
    private static final String REBATE_SET_UP = "rebate/set-up";

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static RealTimeRebatePage realTime;

    @BeforeAll
    static void signInAndOpenRebateSetUp() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        shell.useCurrency(CURRENCY);
        realTime = new RealTimeRebatePage(sessionPage);
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        shell = null;
        realTime = null;
    }

    @BeforeEach
    void reopenRealTimeTab() {
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
    }

    @Test
    @Order(10)
    @DisplayName("RBMP-124: Existing single-program rebate data migrates into the new multi-program list")
    void singleProgramDataMigrated() {
        // The global settings are the part of the old single-program design that had somewhere
        // obvious to land, so they are read first and recorded whatever the list says.
        String minClaim = realTime.getMinClaimValue();
        String realTimeExpire = realTime.getExpireTimeValue();
        String realTimeStatus = realTime.getGlobalStatusValue();
        int realTimePrograms = realTime.getProgramCount();

        DailyRebatePage daily = realTime.switchToDailyRebate();
        String releaseTime = daily.getReleaseTimeValue();
        String dailyExpire = daily.getExpireTimeValue();
        String dailyStatus = daily.getGlobalStatusValue();
        int dailyPrograms = daily.getProgramCount();

        CaseLog.data("Real-time global settings after the upgrade",
                "Min. Claim " + minClaim + ", Expire " + realTimeExpire + ", Status " + realTimeStatus);
        CaseLog.data("Daily global settings after the upgrade",
                "Release " + releaseTime + ", Expire " + dailyExpire + ", Status " + dailyStatus);
        CaseLog.data("Programs listed", "Real-time " + realTimePrograms + ", Daily " + dailyPrograms);

        assertFalse(minClaim.isBlank() && realTimeExpire.isBlank() && releaseTime.isBlank(),
                "The upgrade should not have emptied the global settings: every one of Min. Claim, "
                        + "Expire time and Release time came back blank, which would mean the "
                        + "pre-upgrade configuration was lost rather than migrated");

        if (realTimePrograms == 0 && dailyPrograms == 0) {
            abort("The pre-existing configuration's global settings survived the upgrade - "
                    + "Min. Claim " + minClaim + ", Real-time expire " + realTimeExpire
                    + ", Daily release " + releaseTime + ", Daily expire " + dailyExpire
                    + " - but NEITHER tab lists a program. Whether that is correct is AQ-12: if "
                    + "this environment held a single-program rebate configuration before the "
                    + "upgrade, it should now appear as an entry on its own tab with its original "
                    + "rates and targeting, and it does not. If it held none, there was nothing "
                    + "to migrate and the empty list is right. Confirm which with the client, and "
                    + "this case asserts on the migrated entry as written.");
        }

        abort("Reached only when a program is listed: checking that its rates and targeting came "
                + "across intact needs the pre-upgrade configuration to compare against, which is "
                + "not on any admin screen. Record it before the next environment is upgraded.");
    }

    @Test
    @Order(11)
    @DisplayName("RBMP-125: Member-facing rebate crediting is unaffected for a migrated program")
    void memberCreditingUnaffected() {
        requiresMemberActivity("whether a member still receives the rebate a migrated program "
                + "promises, and at the pre-upgrade amount");
    }

    @Test
    @Order(12)
    @DisplayName("RBMP-126: Historical rebate reports remain accurate after the multi-program change")
    void historicalReportsRemainAccurate() {
        // This one has a screen: the Rebate report is deployed, and a pre-upgrade record would be
        // visible on it. Whether there is one is the question.
        RebateReportPage report = new RebateReportPage(sessionPage);
        report.open();
        report.search();

        if (!report.hasData()) {
            abort("The rebate report holds no records at all on this environment, so there is "
                    + "nothing from before the upgrade whose attribution could have been damaged "
                    + "by it. Asserting against an empty report would pass while testing nothing. "
                    + "Seed rebate activity - see RebateTestData.SEEDING_REQUIRED - or run this "
                    + "against an environment that was upgraded with history on it.");
        }
        abort("Reached only when the report has records: telling a pre-upgrade record from a "
                + "post-upgrade one needs the upgrade date for this environment and the record's "
                + "own date, and the report's date filter is the way in. Supply the date and this "
                + "case asserts that every record before it is still attributed to a program.");
    }

    @Test
    @Order(13)
    @DisplayName("RBMP-127: Pre-existing Agent/User Group assignments are not duplicated or corrupted by the migration")
    void assignmentsNotDuplicatedByMigration() {
        // The one assertion that can be made from the list alone: whatever migrated, no two
        // programs of the same type may hold the same Agent/User Group pairing - that is the
        // rule RBMP-015 enforces on Create, and a migration that duplicated an assignment would
        // have broken it silently.
        int realTimePrograms = realTime.getProgramCount();
        DailyRebatePage daily = realTime.switchToDailyRebate();
        int dailyPrograms = daily.getProgramCount();
        CaseLog.data("Programs listed", "Real-time " + realTimePrograms + ", Daily " + dailyPrograms);

        if (realTimePrograms == 0 && dailyPrograms == 0) {
            abort("Neither tab lists a program, so there is no assignment to compare. This row "
                    + "needs the Agent/User Group assignments as they stood before the upgrade "
                    + "and as they stand now; only the second half is on screen, and it is empty. "
                    + "See RBMP-124, which records the same finding from the other end.");
        }
        abort("Reached only when programs are listed: a duplication check compares each program's "
                + "Target Agents and User Groups against every other of the same type, and the "
                + "pre-upgrade snapshot is what says whether anything was lost as well as "
                + "duplicated. Record that snapshot before the next upgrade.");
    }

    /**
     * Aborts a row whose evidence is a member's balance rather than an admin screen.
     *
     * <p>Kept separate from the aborts above because it is a different kind of missing: those
     * want data seeded on this environment, this wants a second application and a settlement run
     * that the back office cannot trigger.
     */
    private static void requiresMemberActivity(String whatIsBeingAsked) {
        abort("Not answerable through the admin back office: " + whatIsBeingAsked + ". It needs a "
                + "member account to bet, a settlement run to credit the rebate, and the member "
                + "site to read the balance back - none of which this suite drives, and none of "
                + "which the admin UI exposes. See RebateTestData.SEEDING_REQUIRED.");
    }
}
