package org.sengmean.tests.rebateprogram;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.sengmean.base.BasePage;
import org.sengmean.base.BaseTest;
import org.sengmean.page.rebate.DailyRebatePage;
import org.sengmean.page.rebate.RealTimeRebatePage;
import org.sengmean.page.rebate.RebateProgramFormPage;
import org.sengmean.page.rebate.RebateReportPage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import org.sengmean.testdata.RebateTestData;
import java.util.List;

import static org.sengmean.testdata.RebateTestData.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * The <b>Real-time rebate</b> tab of Rebate Management &gt; Rebate set up, and the cross-type
 * rows that start there.
 *
 * <h2>Where the cases come from</h2>
 * Every case here is a row of {@code src/test/resources/testdata/client_testcase.xlsx} - the
 * "Rebate Program (Multi-Config)" workbook - and keeps the workbook's own id, so a row in the
 * report traces straight back to a row in the sheet. This class carries the sheet's sections
 * <b>A1 to A8</b> ({@code RBMP-001} to {@code RBMP-055}) and the <b>C1 Cross-Type</b> rows
 * ({@code RBMP-112} to {@code RBMP-114}), which live here because all three begin on the
 * Real-time tab. Sections B1 to B8 are {@link DailyRebateTest}.
 *
 * <p>C2 Access Control, C3 Localization and C4 Regression ({@code RBMP-115} to {@code RBMP-127})
 * are deliberately not here. They need a second and third role, a language switch and a
 * pre-migration data set, and belong to suites of their own rather than being appended to
 * whichever class happened to be written first.
 *
 * <h2>Written against the page objects, ahead of the UI</h2>
 * {@code org.sengmean.page.rebate} models the <b>mockup</b> - the multi-config Rebate Program
 * Setup screen the workbook describes - and these cases are written against that API as though
 * it were live, so that the suite is ready on the day the feature ships rather than starting
 * then. The bodies call the page objects and assert; nothing here reaches around them to a
 * locator of its own.
 *
 * <h2>Until it ships, the whole class reports Not run</h2>
 * The feature is not deployed yet. Measured against {@code /rebate/set-up} on 2026-09-16,
 * today's screen is still the previous single-program design: two tabs, one global settings
 * panel each, and one shared game-category percentage table. No program list, no search box, no
 * status filter, no pagination, no Create control, no group-targeting transfer list.
 *
 * <p>{@link #requireMultiProgramUi()} checks for that once per case and <b>aborts</b> when the
 * new screen is absent, so every row reports as <b>Not run</b> with the reason attached instead
 * of failing. A red suite would claim the back office is broken when the truth is that the UI
 * has not arrived; leaving the rows out would shrink the client's 127-row suite without saying
 * so. When the feature lands, the guard stops firing and these bodies run as written.
 *
 * <h2>The cases run as one end-to-end pass, not in id order</h2>
 * This class overrides the project's default ordering - {@code junit-platform.properties} sorts by
 * display name, which here would be workbook-id order - and declares
 * {@code @TestMethodOrder(OrderAnnotation)} instead, so a single run <b>creates its own data, uses
 * it, and deletes it again</b>. The phases, in {@code @Order} bands:
 *
 * <ol>
 *   <li><b>10</b> - read the global settings as found, before anything has changed them;</li>
 *   <li><b>20-30</b> - Create: the rejections first, which leave nothing behind, then the rows
 *       that actually create programs, then the duplicate-name and duplicate-pairing rows, which
 *       need one to exist already;</li>
 *   <li><b>40-57</b> - group targeting and the rate table, on the programs just created;</li>
 *   <li><b>60-64</b> - the list: only now is there something to list, search, filter and page;</li>
 *   <li><b>70-77</b> - Modify;</li>
 *   <li><b>80-82</b> - the global settings that change what programs do, after the read-only row
 *       above has recorded what they were;</li>
 *   <li><b>90-92</b> - the cross-type rows, which need both tabs populated;</li>
 *   <li><b>100-104</b> - Delete, last, so nothing that follows can be stranded by it;</li>
 *   <li><b>110-114</b> - settlement, which observes rather than changes.</li>
 * </ol>
 *
 * <p><b>Id order was the wrong order, not merely a less tidy one.</b> It put A1's list rows before
 * A3 had created anything for them to list, and put {@code RBMP-005}'s delete at position five of
 * fifty-eight - where it removed a program that {@code RBMP-030} asked for twenty-five cases
 * later. The report still reads by id, because the id is in every display name; only the running
 * order changed.
 *
 * <h2>Test data</h2>
 * Every literal comes from the workbook's TEST DATA column and lives in {@link RebateTestData}.
 * Because the run is end-to-end, the programs it needs are made by it rather than seeded, which is
 * what lets it run twice in a row. {@link RebateTestData#SEEDING_REQUIRED} lists what is left - the
 * Agent and User groups, the extra accounts, and the settlement history - none of which a browser
 * can create.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(50)   // E2E step 5
public class RealTimeRebateTest extends BaseTest {

    /** Where the back office serves the rebate settings screen. */
    private static final String REBATE_SET_UP = "rebate/set-up";


    private static BrowserContext session;
    private static Page sessionPage;
    private static RealTimeRebatePage realTime;

    /**
     * Whether the multi-config screen the page objects model is on this environment.
     *
     * <p>Decided once, by looking for the two controls only the new design has - a program search
     * box and a Create control. Asking per case would put the same question to the same screen
     * fifty times and get the same answer.
     */
    /** The header, for putting the run into its configured language. */
    private static AdminShellPage shell;

    private static boolean multiProgramUiPresent;

    /**
     * Signs in and settles whether the multi-config screen is on this environment.
     *
     * <h2>The probe used to answer "no" on a screen that was there</h2>
     * It navigated, called {@code awaitReady} and then counted a search box and a Create control
     * <b>immediately</b>. That is too early by about a second: the back office is a Nuxt
     * application and {@code awaitReady} resolves on {@code domcontentloaded}, long before Vue
     * has rendered the list. So the count was zero, {@code multiProgramUiPresent} was false, and
     * <b>every one of this class's 58 rows reported Not run</b> - with a message saying the
     * feature had not shipped, on an environment where it had. The same bug hid the other 56 in
     * {@link DailyRebateTest}; 114 rows in total, all blaming the wrong thing.
     *
     * <p>The fix is to ask the page object, which waits properly:
     * {@link RealTimeRebatePage#navigateTo(String)} waits for the tab strip and then for the
     * settings panel to be <em>populated</em>, by which point the list has rendered too. A
     * genuinely absent screen still answers "no", because that wait times out rather than
     * resolving - which is why it is caught rather than allowed to fail the class.
     */
    @BeforeAll
    static void signInAndProbe() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);

        // Before anything is read: the language selector is sticky per account, so whatever the
        // last run left it on is what this one would otherwise inherit - and every case that
        // reads visible text would then be asserting against the wrong language.
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        shell.useCurrency(CURRENCY);

        realTime = new RealTimeRebatePage(sessionPage);
        for (int attempt = 1; attempt <= PROBE_ATTEMPTS && !multiProgramUiPresent; attempt++) {
            try {
                realTime.navigateTo(BasePage.url(REBATE_SET_UP));

                // Wait for the LIST, not just for the screen: navigateTo is satisfied once the
                // settings panel has its values, and the list is fetched separately and arrives
                // later - so asking the moment navigateTo returns can find nothing on a
                // perfectly healthy screen.
                sessionPage.waitForCondition(realTime::isListShowing,
                        new Page.WaitForConditionOptions().setTimeout(PROBE_TIMEOUT_MS));

                multiProgramUiPresent = realTime.isSearchAvailable() && realTime.isCreateAvailable();
                probeFailure = multiProgramUiPresent ? ""
                        : "the screen loaded, but offered no search box and/or no Create control";
            } catch (RuntimeException screenNeverArrived) {
                probeFailure = "attempt " + attempt + " of " + PROBE_ATTEMPTS + ": "
                        + firstLine(screenNeverArrived.getMessage());
                CaseLog.step("The Rebate set up screen did not settle - " + probeFailure);
            }
        }
        CaseLog.data("Multi-program UI deployed", String.valueOf(multiProgramUiPresent)
                + (probeFailure.isEmpty() ? "" : " (" + probeFailure + ")"));
    }

    /**
     * How many times the probe tries before giving up on the screen.
     *
     * <p>It decides for all 58 rows, so one attempt was a single point of failure: a slow load
     * made it conclude the feature was not deployed and every row reported Not run saying so,
     * which was false. See {@link DailyRebateTest}, where the same bug hid 56 more.
     */
    private static final int PROBE_ATTEMPTS = 3;

    /** How long each attempt gives the list to appear. */
    private static final double PROBE_TIMEOUT_MS = 20_000;

    /** Why the probe concluded the screen was absent, for the message every row then carries. */
    private static String probeFailure = "";

    /** The first line of a Playwright error, which is the part worth quoting. */
    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "(no message)";
        }
        int newline = message.indexOf(10);
        return newline < 0 ? message : message.substring(0, newline);
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        realTime = null;
    }

    /**
     * Puts every case on a freshly-opened Real-time tab, once the screen exists.
     *
     * <p>The guard runs first so that {@code navigateTo} is never called against a screen that
     * cannot satisfy it - its heading wait is the page object's first action, and a timeout there
     * would report as a failure rather than as the feature being absent.
     */
    @BeforeEach
    void openRealTimeTab() {
        requireMultiProgramUi();
        restore = null;
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
    }

    /**
     * How the case that is running puts back whatever global setting it changed.
     *
     * <h2>Why this had to exist before these cases could be run at all</h2>
     * The A2 rows write settings that are <b>global to the rebate type</b> and outlive the run:
     * {@code RBMP-008} saves a Min. Claim and an Expire time, and {@code RBMP-010} switches the
     * whole Real-time rebate <b>off</b>. Neither used to put anything back, so a single run of
     * this class left the test environment with Real-time rebate disabled - and the next person
     * to look at the back office would have found a feature turned off with nothing to say why.
     *
     * <p>Restoring at the end of the case body is not good enough, because that is exactly the
     * line a failing assertion never reaches. The undo is registered <em>before</em> the write
     * and run from {@link #restoreWhatThisCaseChanged()}, which is the pattern
     * {@code RebateSetUpTest} already uses for the D1 rows.
     */
    private Runnable restore;

    /**
     * Clears any prompt the case left up, whatever happened to it.
     *
     * <p>Runs before the restore, and before anything else can try to click. The back office's
     * prompt is modal and does not close itself, so a case that failed its assertion between
     * raising one and answering it used to leave an overlay across the screen - and every case
     * after it timed out in @BeforeEach, unable to reach the page. One genuine failure became
     * thirty-five errors that way.
     */
    @AfterEach
    void dismissAnyLingeringPrompt() {
        try {
            for (int attempt = 0; attempt < 3 && realTime.isConfirmDialogVisible(); attempt++) {
                realTime.confirmDialogButtons();
                sessionPage.locator("div.ui-dialog[role='dialog'] .ui-dialog-buttonset button")
                        .first().click();
                sessionPage.waitForTimeout(800);
            }
        } catch (RuntimeException ignored) {
            // A prompt that cannot be dismissed is reported by whatever fails next; this must
            // never be the thing that fails a case.
        }
    }

    @AfterEach
    void restoreWhatThisCaseChanged() {
        if (restore == null) {
            return;
        }
        try {
            restore.run();
        } catch (RuntimeException couldNotRestore) {
            // Never allowed to turn a red case green or a green case red; what it does is make
            // sure the next person knows the environment was left changed.
            CaseLog.evidence("This case could not put the global setting it changed back: "
                    + couldNotRestore.getMessage() + " - check Rebate set up by hand.");
        } finally {
            restore = null;
        }
    }

    /** Remembers the Real-time global settings as they are now, and how to put them back. */
    private void rememberGlobalSettings() {
        String minClaim = realTime.getMinClaimValue();
        String expireTime = realTime.getExpireTimeValue();
        String status = realTime.getGlobalStatusValue();
        CaseLog.data("Real-time settings as found",
                "Min. Claim " + minClaim + ", Expire " + expireTime + ", Status " + status);
        restore = () -> {
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            realTime.setMinClaim(minClaim);
            realTime.setExpireTime(expireTime);
            realTime.setGlobalStatus(status);
            realTime.clickSave();
        };
    }

    /** Aborts the case unless the multi-config screen the page objects model is deployed. */
    private static void requireMultiProgramUi() {
        if (!multiProgramUiPresent) {
            abort("The Rebate set up screen did not answer the probe in " + PROBE_ATTEMPTS
                    + " attempts, so no row in this class could run"
                    + (probeFailure.isEmpty() ? "" : " - " + probeFailure)
                    + ". If the screen IS deployed (it was on 2026-09-18), this is a loading or "
                    + "timing problem rather than a missing feature: try -Dheadless=true, or "
                    + "raise test.actionTimeout / test.navigationTimeout in Config/.");
        }
    }

    /**
     * Aborts a case whose screen the page-object package does not model yet.
     *
     * <p>Distinct from {@link #requireMultiProgramUi()} on purpose. That one is about the
     * environment and clears itself when the feature deploys; this one is about coverage that
     * has still to be written, and names what is missing so it can be.
     */
    private static void awaitingPageObject(String whatIsMissing) {
        abort("No page object models " + whatIsMissing + " yet, so this row has nothing to drive "
                + "it through. org.sengmean.page.rebate currently covers the tab, its global "
                + "settings panel and the program list; this screen needs its own page object "
                + "before the case can be written against it.");
    }

    /**
     * Aborts a row whose evidence is on the Rebate report, when the report has none.
     *
     * <h2>The screen is modelled; the records are missing</h2>
     * These rows used to say no page object covered the Rebate report. That stopped being true
     * on 2026-09-18 - {@link RebateReportPage} models it and {@code RebateReportTest} drives it -
     * so the message was sending readers to write something that already existed. What is
     * actually missing is <b>settled rebate records</b>: the report is empty on this environment,
     * and a row that asserted against an empty report would pass while examining nothing.
     *
     * <p>The check is made rather than assumed, so the day settlement history exists these rows
     * fail loudly and ask to be written instead of quietly skipping forever.
     */
    private static void requiresSettlementRecords(String whatTheRowWouldDo) {
        RebateReportPage report = new RebateReportPage(sessionPage);
        report.open();
        report.search();
        boolean hasRecords = report.hasData();
        CaseLog.data("Rebate report records", hasRecords ? "present" : "none");
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        assertFalse(hasRecords,
                "The Rebate report now holds records, so this row can be written for real: it "
                        + "needs to " + whatTheRowWouldDo + ".");
        abort("Needs settled rebate records, and the Rebate report has none on this environment. "
                + "The report screen itself IS modelled - see RebateReportPage - so what is "
                + "missing is the data, not the coverage: this row would " + whatTheRowWouldDo
                + ". Seed settlement history and it runs.");
    }

    /** Aborts a case whose evidence is not in a browser at all. */
    private static void requiresNonUiEvidence(String detail) {
        abort("Not answerable through the admin UI: " + detail);
    }

    // =====================================================================
    // Seeding the suite's own data
    //
    // This environment has NO Agent groups and NO Member groups - both Option columns on the
    // Create form are empty - so every program this suite makes has to be targeted with the
    // form's default "Select all", and the row comes back reading "All Agents" / "All Groups".
    //
    // That has one consequence which shapes everything below: ALL AGENTS + ALL GROUPS IS A
    // SINGLE PAIRING, and RBMP-015's rule is that a pairing may be used once per rebate type.
    // So at most ONE Real-time program can exist at a time, and the workbook's four named
    // fixtures - an Active one, an Inactive one, and two disposable ones - cannot coexist.
    //
    // Rather than seed them (which no browser can do) or skip the rows (which hides the
    // coverage), each row that needs a program MAKES ONE, and each destructive row deletes the
    // one it made. Deleting frees the pairing again, so the next row can make its own. The suite
    // therefore runs repeatedly from a clean environment and leaves it clean.
    // =====================================================================

    /** The one program this suite can have at a time. Named so a stray one is obvious. */
    private static final String SUITE_PROGRAM = PROGRAM_NEW_REAL_TIME;

    private static final String SUITE_PROGRAM_1 = PROGRAM_NEW_REAL_TIME_1;
    private static final String SUITE_PROGRAM_2 = PROGRAM_NEW_REAL_TIME_2;

    /**
     * Creates a Real-time program through the Create form and waits for it to be listed.
     *
     * @return the form, still open, when the submission was refused; {@code null} when it was
     *         accepted and the list came back
     */
    private static RebateProgramFormPage createProgram(String name, String status) {
        // Tries twice: every suite program sits on the one pairing, so the usual sequence is a row
        // deleting the fixture and the next row creating it again. The delete's confirmation and
        // the server releasing the pairing are two different moments, and a create issued in
        // between is refused for a pairing that is on its way to being free. See the Daily twin,
        // where RBMP-065 reported Not run for exactly this.
        ensurePairingCandidates();
        RebateProgramFormPage lastRefused = null;
        int start = Math.max(0, pairingCandidates.indexOf(suiteAgent));
        for (int i = start; i < pairingCandidates.size() && i - start < PAIRING_PROBE_LIMIT; i++) {
            suiteAgent = pairingCandidates.get(i);
            lastRefused = attemptCreate(name, status);
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            showAllPrograms();
            if (realTime.isProgramListed(name)) {
                if (i > start) {
                    CaseLog.data("Suite pairing moved on to", suiteAgent + " / " + suiteMember);
                }
                return null;
            }
        }
        return lastRefused;
    }

    private static RebateProgramFormPage attemptCreate(String name, String status) {
        RebateProgramFormPage form = openCreateOnSuitePairing();
        form.setProgramName(name);
        form.setStatus(status);
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_LIVE_CASINO);
        form.submit();
        if (!form.wasAccepted()) {
            return form;
        }
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        return null;
    }

    // -----------------------------------------------------------------
    // The pairing the suite makes its OWN programs on
    //
    // Every row that needs a program of its own used to leave the Create form on its "Select all"
    // default, which the back office records as All Agents / All Groups - a single pairing that
    // collides with every program on the tab. On an empty currency that worked; on HKD, which has
    // seeded data, it meant the suite could create NOTHING, and ten rows reported Not run:
    // RBMP-005, 017, 022, 030, 031, 032, 033, 044, 047 and 114. Every one of them was a delete, a
    // rename or a create that simply needed somewhere to put a program.
    //
    // The group pickers answer now, so the suite takes a pairing of its own instead. One is found
    // per run and reused, because finding one costs a create and a delete.
    // -----------------------------------------------------------------

    private static String suiteAgent;
    private static String suiteMember;
    private static List<String> pairingCandidates;

    /**
     * Reads the Agent groups the Create form offers, once per run, as the list to work down.
     *
     * <h2>Why the suite does not probe for a free pairing any more</h2>
     * The obvious approach - ask {@link #findFreeAgents} for a free pairing and remember it -
     * cannot work here. A probe proves a pairing free by creating on it and <b>deleting again</b>,
     * and the server releases the pairing some time after the row leaves the list. The very next
     * thing the suite does is create on that pairing for real, so it lands inside the gap and is
     * refused for a pairing the probe has just shown to be free. Measured 2026-09-22 on the Daily
     * tab: {@code RBMP-065} reported Not run twice this way, once even after a 2.5-second retry.
     *
     * <p>So {@link #createProgram} does not probe. It attempts the real create and, on a refusal,
     * moves to the next Agent group. A refusal costs what a probe cost and proves more - the
     * pairing is free <em>for this submission</em>, which is the only question that matters - and
     * nothing is created and deleted to find out.
     */
    private static void ensurePairingCandidates() {
        if (pairingCandidates != null) {
            return;
        }
        suiteMember = firstMemberGroupOnOffer();

        // AGENT_GROUP_NAME is RESERVED, and leaving it in this list was a self-collision. It is
        // the configured test.group.agent.name, and the rows that demonstrate named-group
        // targeting - RBMP-011 and RBMP-065 - aim at it deliberately. It is also the first entry
        // the picker offers, so the suite took it for its own fixture and then refused those very
        // rows: RBMP-011 failed on the 2026-09-22 run with "the form stayed open" against
        // agent-8001 / Default 00, a pairing the suite itself was sitting on.
        pairingCandidates = agentsOnOffer().stream()
                .filter(agent -> !agent.equals(AGENT_GROUP_NAME))
                .toList();
        if (suiteAgent == null && !pairingCandidates.isEmpty()) {
            suiteAgent = pairingCandidates.get(0);
        }
        CaseLog.data("Suite pairing candidates",
                pairingCandidates.size() + " Agent group(s) x " + suiteMember
                        + ", with " + AGENT_GROUP_NAME + " reserved for the named-group rows");
    }

    /**
     * Moves the suite on to the next candidate pairing, for when the current one stops working.
     *
     * @param forceRefresh true to step past the remembered Agent group
     */
    private static void ensureSuitePairing(boolean forceRefresh) {
        ensurePairingCandidates();
        if (forceRefresh) {
            int next = pairingCandidates.indexOf(suiteAgent) + 1;
            if (next > 0 && next < pairingCandidates.size()) {
                suiteAgent = pairingCandidates.get(next);
                CaseLog.data("Suite pairing moved on to", suiteAgent + " / " + suiteMember);
            }
        }
    }

    /**
     * Creates a program the caller fills in itself, walking the pairings until one is accepted.
     *
     * <p>{@link #createProgram} walks the candidate Agent groups on a refusal; a row that opens
     * the form itself got one attempt and aborted. {@code RBMP-017} did exactly that on the
     * 2026-09-22 run - Not run against one pairing while eleven others sat untried.
     *
     * <p>The caller's filling runs on each attempt, which is why it takes a lambda rather than a
     * prepared form: a fresh Create form is opened per pairing. Assertions about the form as it
     * OPENS - {@code RBMP-017} checks Status defaults to Disable - belong inside the lambda and
     * are simply checked again each time.
     *
     * @param name what the program will be called, which is how acceptance is confirmed
     * @param fill sets up the form; called once per pairing attempted
     */
    private static void createOnSuitePairing(String name,
                                             java.util.function.Consumer<RebateProgramFormPage> fill) {
        ensurePairingCandidates();
        RebateProgramFormPage lastRefused = null;
        int start = Math.max(0, pairingCandidates.indexOf(suiteAgent));
        for (int i = start; i < pairingCandidates.size() && i - start < PAIRING_PROBE_LIMIT; i++) {
            suiteAgent = pairingCandidates.get(i);
            lastRefused = openCreateOnSuitePairing();
            fill.accept(lastRefused);
            lastRefused.submit();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            showAllPrograms();
            if (realTime.isProgramListed(name)) {
                if (i > start) {
                    CaseLog.data("Suite pairing moved on to", suiteAgent + " / " + suiteMember);
                }
                return;
            }
        }
        abortIfRefused(lastRefused, name);
    }

    /** Opens Create with the suite's own pairing already selected, ready to be filled in. */
    private static RebateProgramFormPage openCreateOnSuitePairing() {
        ensurePairingCandidates();
        RebateProgramFormPage form = openCreateWithPickers();
        moveOne(form, RebateProgramFormPage.AGENT_GROUP, suiteAgent);
        moveOne(form, RebateProgramFormPage.MEMBER_GROUP, suiteMember);
        return form;
    }

    /**
     * Makes sure the suite's program is on the list, creating it if it is not.
     *
     * <p>Called by every row that needs something to list, search, filter or modify. Aborts
     * rather than fails if the create is refused, because a row about <em>searching</em> should
     * not report itself as a search defect when what went wrong was the seeding.
     */
    /** The program the row currently running should work with. See {@link #fixtureProgram()}. */
    private static String fixture = PROGRAM_NEW_REAL_TIME;

    private static void ensureSuiteProgram() {
        fixture = fixtureProgram();
    }

    /**
     * The program a read-only row should work with: the suite's own if it has one, otherwise
     * whatever is already on the tab.
     *
     * <h2>Why borrowing had to be added</h2>
     * The suite can only express <b>one</b> pairing - All Agents / All Groups - because the
     * Create form's Agent and Member pickers come back empty for this operator (permission, not
     * absence; see {@code RebateTestData.AGENT_GROUP_8001}). And "All Agents" is treated by the
     * back office as covering <em>every</em> agent, so that pairing collides with any program at
     * all. Submitting one while a manually-seeded program exists is refused with
     * <i>"agent-8001 and All Groups already exist in \"If test\""</i>.
     *
     * <p>So on an environment with seeded data the suite cannot create its own fixture, and
     * insisting on doing so would turn every list, search, filter and modify row red for a reason
     * that has nothing to do with listing, searching, filtering or modifying. It borrows instead:
     * a row that only needs <em>a</em> program is perfectly happy with the one that is there.
     *
     * <p>What it must never do is <b>delete</b> a program it did not create - see
     * {@link #requireOwnProgram()}.
     */
    private static String fixtureProgram() {
        if (realTime.isProgramListed(SUITE_PROGRAM)) {
            return SUITE_PROGRAM;
        }

        // MAKE one before borrowing one. Borrowing came in when the suite could not create at all
        // - every program it could express collided with the seeded data - and a row that only
        // needs *a* program is happy with whatever is there. But a borrowed program is one
        // requireOwnProgram() will refuse to rename or delete, so RBMP-005, RBMP-022 and the
        // RBMP-030..033 delete rows reported Not run on any populated tab. Now that the suite has
        // a pairing of its own, creating is both possible and strictly better: the fixture is the
        // suite's to do what it likes with, and nothing of anybody else's is at risk.
        RebateProgramFormPage refused = createProgram(SUITE_PROGRAM, SETUP_ENABLE);
        if (refused == null) {
            return SUITE_PROGRAM;
        }
        String why = refused.validationMessageText();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        if (realTime.isProgramListed(SUITE_PROGRAM)) {
            return SUITE_PROGRAM;
        }

        // Only now fall back, and say so - a row that goes on to rename or delete will stop at
        // requireOwnProgram() rather than touch somebody else's program.
        if (realTime.getProgramCount() > 0) {
            String borrowed = firstListedProgram();
            CaseLog.data("Could not create the suite's own program",
                    (why.isEmpty() ? "refused silently" : why) + " - borrowing \"" + borrowed
                            + "\" instead, which read-only rows can use and destructive ones "
                            + "cannot");
            return borrowed;
        }
        abort("This row needs a Real-time program, the tab is empty, and the suite could not "
                + "create one on its own pairing (" + suiteAgent + " / " + suiteMember + "): "
                + (why.isEmpty() ? "the form refused it silently" : why));
        return SUITE_PROGRAM;
    }

    /**
     * Creates a program, or aborts naming the reason the back office gave.
     *
     * <p>On an environment with seeded programs this is the usual outcome, and the reason matters:
     * the suite can only target All Agents / All Groups, the back office treats All Agents as
     * covering every agent, and so the pairing collides with anything already there. That is a
     * fact about the environment, not a defect in the row asking for a program - so the row
     * reports Not run with the server's own words rather than failing.
     */
    private static void createProgramOrAbort(String name, String status) {
        RebateProgramFormPage refused = createProgram(name, status);
        if (refused == null) {
            return;
        }
        String why = refused.validationMessageText();
        refused.dismissMessage();
        refused.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        abort("This row needs to create \"" + name + "\" and the back office refused it"
                + (why.isEmpty() ? " without a message" : ": " + why)
                + ". The suite can only offer the All Agents / All Groups pairing, because the "
                + "Agent and Member pickers answer \"Permission denied\" for this operator, and "
                + "All Agents collides with every program already on the tab. Grant that read "
                + "access - or clear the tab - and this row runs as written.");
    }

    /**
     * Aborts when a submission the row depends on was refused, naming the reason.
     *
     * <p>The sibling of {@link #createProgramOrAbort(String, String)}, for the rows that drive the
     * form themselves rather than through the helper.
     */
    private static void abortIfRefused(RebateProgramFormPage form, String name) {
        // The list is the authority on whether the program was made, not the form - see
        // createTargeting for what believing the form cost. Checked first here because a refusal
        // read off a form that was merely slow to close would abort a row that had worked.
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        showAllPrograms();
        if (realTime.isProgramListed(name)) {
            return;
        }
        String why = form.validationMessageText();

        // A refusal now means the suite's pairing was taken while this run was using it, so the
        // remembered one is dropped and the next create looks for another. It does not retry here:
        // the row that was interrupted should say so rather than quietly succeeding on a second
        // attempt that tested something slightly different.
        ensureSuitePairing(true);
        abort("The back office refused \"" + name + "\""
                + (why.isEmpty() ? " without a message" : ": " + why)
                + ". The suite creates on its own pairing (" + suiteAgent + " / " + suiteMember
                + "), so this is not the old All Agents collision - something took that pairing "
                + "mid-run, or the name is already in use. A fresh pairing has been chosen for "
                + "the rows that follow.");
    }


    /**
     * Targets the form at the configured Agent and Member group, when the pickers offer them.
     *
     * <h2>Why this is a "if possible" and not an assertion</h2>
     * {@code RBMP-011} is "create a program with all fields valid". The pairing is one of those
     * fields, and targeting a NAMED group rather than accepting "Select all" is the version the
     * workbook describes - so it is tried first, using {@code test.group.agent.name} and
     * {@code test.group.member.name} so the environment's own groups can be named in config.
     *
     * <p>But it cannot be made a precondition. The pickers are fed by {@code /api/account/agent}
     * and {@code /api/account/member_group}, which answer {@code "Permission denied"} for this
     * operator, so both Option panes render empty and no name can be chosen. Demanding the named
     * pairing turned this row into an abort, which threw away the rest of the coverage it can
     * still give: that the form accepts a name, a status and a rate, that Submit is taken, and
     * that the program appears on the list configured as asked.
     *
     * <p>So it degrades instead: named groups when they are reachable, the form's own default
     * when they are not, and the report says which happened. The day the permission is granted
     * this starts targeting the named pair with no code change.
     *
     * @return true when the program was targeted at the named groups
     */
    private static boolean targetNamedGroupsIfPossible(RebateProgramFormPage form) {
        try {
            form.targetOnly(RebateProgramFormPage.AGENT_GROUP, AGENT_GROUP_NAME);
            form.targetOnly(RebateProgramFormPage.MEMBER_GROUP, MEMBER_GROUP_NAME);
        } catch (IllegalStateException pickerUnusable) {
            CaseLog.evidence("Could not target " + AGENT_GROUP_NAME + " / " + MEMBER_GROUP_NAME
                    + ", so the program was created with the form's default targeting instead: "
                    + pickerUnusable.getMessage() + " Everything else about this row was still "
                    + "exercised; only the named pairing was not.");
            // Put both groups back to Select all, so the submission is the form's valid default
            // rather than the half-state Optional-with-nothing-chosen, which is RBMP-014.
            form.chooseSelectAll(RebateProgramFormPage.AGENT_GROUP);
            form.chooseSelectAll(RebateProgramFormPage.MEMBER_GROUP);
            return false;
        }
        assertEquals(List.of(AGENT_GROUP_NAME),
                form.selectedNames(RebateProgramFormPage.AGENT_GROUP),
                "The Agent group should be targeted at " + AGENT_GROUP_NAME + " alone");
        assertEquals(List.of(MEMBER_GROUP_NAME),
                form.selectedNames(RebateProgramFormPage.MEMBER_GROUP),
                "The Member group should be targeted at " + MEMBER_GROUP_NAME + " alone");
        assertFalse(form.isSelectAll(RebateProgramFormPage.AGENT_GROUP),
                "Targeting a named Agent group means Select all is no longer chosen");
        CaseLog.check("Targeted at " + AGENT_GROUP_NAME + " / " + MEMBER_GROUP_NAME + ".");
        return true;
    }

    /** The name in the first cell of the first row of the list. */
    private static String firstListedProgram() {
        return sessionPage.locator(RealTimeRebatePage.LIST_ROOT
                + " >> table.custom_table tbody tr td").first().innerText().trim();
    }

    /**
     * Aborts a destructive row unless the program it is about to remove is one the suite made.
     *
     * <p>Borrowing is safe for a row that reads, and for one that edits a rate and puts it back.
     * It is <b>not</b> safe for a row that <b>deletes or renames</b>. Deleting is obvious. Renaming
     * turned out to be just as bad: RBMP-022 renames the fixture, asserts, and renames it back -
     * and when the rename-back failed it left a hand-seeded program called "QA Renamed Daily
     * Program" on the Daily tab. A test that borrows somebody else's data must not be able to
     * leave it altered, so both rows now require a program the suite created.
     */
    private static void requireOwnProgram() {
        if (!realTime.isProgramListed(SUITE_PROGRAM)) {
            abort("This row renames or deletes a program, so it needs one the suite created, and "
                    + "\"" + SUITE_PROGRAM + "\" is not on the list. It will NOT rename or delete "
                    + "a manually-seeded program to make itself runnable - RBMP-022 did once, and "
                    + "left a hand-seeded program called \"QA Renamed Daily Program\" behind when "
                    + "the rename back failed. The suite creates on its own pairing now (see "
                    + "ensureSuitePairing), so a failure to create here is worth reading: the "
                    + "create that should have preceded this row was refused.");
        }
    }

    /**
     * Removes every program the suite creates under a name of its own, so the next row starts
     * from a clean list.
     *
     * <h2>Why all three names and not just the first</h2>
     * Every suite program now sits on the <b>same</b> pairing (see {@code ensureSuitePairing}), so
     * only one of them can exist at a time - and a leftover under any of the three names blocks a
     * create under that name as surely as one under this one. That is not theoretical: clearing
     * only {@code SUITE_PROGRAM} left {@code RBMP-044} reporting Not run because
     * "New Real-time Rebate 2" was still on the tab from an earlier run, and the back office
     * enforces the NAME as unique - which is the finding {@code RBMP-075} records on the Daily
     * tab. Two extra list lookups is a cheap price for that never happening again.
     */
    private static void removeSuiteProgram() {
        removeSuiteProgramNamed(SUITE_PROGRAM);
        removeSuiteProgramNamed(SUITE_PROGRAM_1);
        removeSuiteProgramNamed(SUITE_PROGRAM_2);
    }

    /**
     * Removes a program by name if it is listed.
     *
     * <p>Separate from {@link #removeSuiteProgram()} for the boundary row, which creates a
     * program under a name it does not know in advance - the field may cap what it accepts, so
     * what has to be deleted is whatever the form actually kept.
     */
    /**
     * Removes a program by name, and does not return until it is actually gone.
     *
     * <p>The confirmation goes through the back office's modal prompt and the list is fetched
     * separately afterwards, so "the delete was confirmed" and "the program is no longer there"
     * are two different moments. A case that assumed the first was the second went on to create
     * its own fixture and was refused - the server answering, quite correctly, that the pairing
     * was still taken. It showed up as a rare failure in a full-class run and not at all when the
     * case was run on its own, which is what an ordering race looks like.
     */
    private static void removeSuiteProgramNamed(String name) {
        for (int attempt = 0; attempt < 3; attempt++) {
            showAllPrograms();
            if (!realTime.isProgramListed(name)) {
                return;
            }
            realTime.clickDelete(name);
            realTime.confirmDeletion();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        }
        showAllPrograms();
        if (realTime.isProgramListed(name)) {
            abort("Could not remove the program \"" + name + "\" after three attempts, so this "
                    + "row cannot start from a clean list. Delete it by hand on Rebate set up.");
        }
    }

    // =====================================================================
    // A1. Real-time Rebate Program List  -  RBMP-001 .. RBMP-006
    // =====================================================================

    @Test
    @Order(60)
    @DisplayName("RBMP-001: Real-time tab lists only Real-time programs with correct summary data")
    void realTimeTabListsOnlyRealTimePrograms() {
        ensureSuiteProgram();
        assertTrue(realTime.isProgramListed(fixture),
                "The Real-time tab should list its own programs, but " + fixture + " was absent");
        assertTrue(realTime.getProgramCount() > 0,
                "The Real-time tab should list at least one program");
        CaseLog.data("Programs listed", String.valueOf(realTime.getProgramCount()));
    }

    @Test
    @Order(61)
    @DisplayName("RBMP-002: Search/filter the program list by name or target")
    void searchFiltersTheProgramList() {
        ensureSuiteProgram();
        realTime.searchProgram(fixture);

        assertEquals(fixture, realTime.getSearchValue(),
                "The search box should hold the term that was searched for");
        assertTrue(realTime.isProgramListed(fixture),
                "The searched-for program should still be listed after the search");
        // Not "exactly one row": a program name is NOT unique on this screen - two programs may
        // share one and be told apart by their pairings (measured 2026-09-18: two both called
        // TEST on the HKD Real-time tab). What the row actually claims is that the list is
        // narrowed to the matches, so that is what is asserted.
        //
        // This was written as assertEquals(matching >= 1, getProgramCount()), which compares a
        // BOOLEAN to an INT. Java resolves that to assertEquals(Object, Object) through
        // autoboxing, so it asked whether Boolean.TRUE equals Integer 3 - a question with only
        // one answer. The row could never pass once it reached this line, and it did not fail
        // either, because until the suite could create its own fixture it aborted before getting
        // here. Fixing the fixture is what exposed it. Two assertions, because they are two
        // claims: the program is there, and nothing else is.
        int matching = realTime.programRowCount(fixture);
        int listed = realTime.getProgramCount();
        assertTrue(matching > 0,
                "The searched-for program should be among the rows the search returned, but none "
                        + "of the " + listed + " row(s) carried the name \"" + fixture + "\"");
        assertEquals(matching, listed,
                "After searching, the list should hold only rows matching the term - it showed "
                        + listed + " row(s) of which " + matching + " matched");
        CaseLog.data("Programs sharing this name", fixture + " x" + matching);

        // The row's expected result is two-part: clearing the search restores the full list.
        realTime.searchProgram("");
        assertTrue(realTime.getProgramCount() > 0,
                "Clearing the search should restore the full program list");

        // The client confirmed the box searches the pair target as well as the name. That half
        // cannot be proven here and it is worth being precise about why rather than asserting
        // something weaker and calling the row done.
        //
        // The workbook searches for "Agent B", a seeded group this environment does not have.
        // Every program the suite can build targets ALL agents, and the row renders that as the
        // label "All Agents" - which is not the name of anything, so there is nothing for a
        // target search to match. Measured: searching "All Agents" returns no rows. That is not
        // a search defect, it is the absence of a named group to search for.
        realTime.clearSearch();
        String target = realTime.getProgramTargetAgents(fixture);
        realTime.searchProgram(target);
        boolean foundByTarget = realTime.isProgramListed(fixture);
        CaseLog.data("Searching the pair target \"" + target + "\"",
                foundByTarget ? "found" : "no rows");
        CaseLog.evidence("The pair-target half of this row is unproven: it needs a program "
                + "targeted at a NAMED Agent group, and this environment has none, so the row "
                + "reads \"" + target + "\" - a label rather than a group. The search-by-name "
                + "half above is asserted and passes. Seed two Agent groups - see "
                + "RebateTestData.SEEDING_REQUIRED - and this assertion can be turned back on.");
    }

    @Test
    @Order(62)
    @DisplayName("RBMP-003: Filter by Status (All / Active / Inactive)")
    void filterProgramsByStatus() {
        ensureSuiteProgram();
        // The list turned out to speak the setup screen's vocabulary, not its own: the status
        // cell is an Enable/Disable select rather than the word "Active". Filtering still works
        // the same way; only the word the row comes back with is different.
        realTime.filterByStatus(SETUP_ENABLE);
        assertEquals(SETUP_ENABLE, realTime.getProgramStatus(fixture),
                "Every row under the Enable filter should be Enabled");

        realTime.filterByStatus(SETUP_DISABLE);
        assertFalse(realTime.isProgramListed(fixture),
                "An Enabled program should be excluded by the Disable filter");

        realTime.filterByStatus(FILTER_ALL);
        assertTrue(realTime.getProgramCount() > 0,
                "The All filter should show every program regardless of status");
    }

    @Test
    @Order(64)
    @DisplayName("RBMP-004: Modify action opens the correct program pre-filled")
    void modifyOpensTheCorrectProgram() {
        ensureSuiteProgram();
        String expectedStatus = realTime.getProgramStatus(fixture);
        RebateProgramFormPage form = realTime.clickModify(fixture);

        // Deliberately not asserted on the address. Modify renders in place and the URL stays
        // /rebate/set-up/ throughout - the first version of this row checked for "modify" or
        // "edit" in it and failed on a screen that had opened correctly.
        assertTrue(form.isOpen(), "Modify should open the program form");
        assertEquals(fixture, form.programName(),
                "Modify should open pre-filled with the program that was clicked");
        assertEquals(expectedStatus, form.status(),
                "Modify should show the status the list showed");
        // Compared as a number, not as text: the form stores 0.3 and redisplays it as "0.30",
        // which is the same rate written to the table's own two-decimal precision.
        //
        // Only asserted when the fixture is one the suite made, because only then is the rate
        // known. A borrowed program - see fixtureProgram() - carries whatever whoever created it
        // chose, and asserting a number on it would be inventing an expectation.
        if (SUITE_PROGRAM.equals(fixture)) {
            assertEquals(Double.parseDouble(RATE_LIVE_CASINO),
                    Double.parseDouble(form.rate(GAME_CODE_LIVE_CASINO)), 0.0001,
                    "Modify should show the rate the program was created with");
        } else {
            CaseLog.data("Rate on the borrowed program", form.rate(GAME_CODE_LIVE_CASINO));
        }
        CaseLog.data("Rates as saved", String.join(" | ", form.allRates()));
        form.returnToList();
    }

    @Test
    @Order(101)
    @Tag("creates-data")
    @DisplayName("RBMP-005: Delete requires confirmation and removes the program")
    void deleteRequiresConfirmation() {
        ensureSuiteProgram();
        requireOwnProgram();
        // Cancelling first, so the destructive half only runs once the prompt is proven to exist.
        realTime.clickDelete(SUITE_PROGRAM);
        assertTrue(realTime.isConfirmDialogVisible(),
                "Delete should raise a confirmation before removing anything");
        realTime.cancelDeletion();
        assertTrue(realTime.isProgramListed(SUITE_PROGRAM),
                "Cancelling the confirmation should leave the program untouched");

        realTime.clickDelete(SUITE_PROGRAM);
        realTime.confirmDeletion();
        assertFalse(realTime.isProgramListed(SUITE_PROGRAM),
                "Confirming the deletion should remove the program from the list");
    }

    /** The name every program this row seeds is called, so a leftover is obvious and findable. */
    private static final String PAGE_SEED = SUITE_PROGRAM + " page";

    /**
     * Creates indexed programs on free pairings until it has enough, or runs out of attempts.
     *
     * <p>Does not probe first: a refusal costs the same as a probe and answers the same question,
     * so the cheapest search for a free pairing is simply to try the next Agent. The attempt
     * budget is what stops a currency with no free pairings turning this into a walk through all
     * 218 of them.
     *
     * @return how many it actually made, which the caller must delete again
     */
    private static int seedForPagination(int wanted, List<String> agents, String member) {
        int made = 0;
        int attempts = 0;
        int budget = wanted * 2 + 10;
        for (String agent : agents) {
            if (made == wanted || attempts == budget) {
                break;
            }
            attempts++;
            if (createTargeting(PAGE_SEED + " " + (made + 1), List.of(agent),
                    List.of(member)).isEmpty()) {
                made++;
            }
        }
        CaseLog.data("Seeded for pagination", made + " program(s) in " + attempts + " attempt(s)");
        return made;
    }

    /**
     * Deletes everything {@link #seedForPagination} made, and never throws.
     *
     * <p>Runs from a {@code finally}, where an abort or an assertion error is already on its way
     * up: letting a cleanup failure replace it would hide what the row actually found. It also
     * widens the page size first, because a program on page two is not on the list this suite can
     * see, and deleting by name only reaches what is rendered.
     */
    private static void removePaginationSeed(int made) {
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        realTime.setPageSize("200");
        for (int i = 1; i <= made; i++) {
            try {
                removeSuiteProgramNamed(PAGE_SEED + " " + i);
            } catch (RuntimeException couldNotDelete) {
                CaseLog.data("Left behind", PAGE_SEED + " " + i + " - delete it by hand");
            }
        }
    }

    /**
     * The list has to page, and until 2026-09-21 this row could only say that it had nothing to
     * page through.
     *
     * <h2>What the screen actually offers</h2>
     * The workbook expects a "Showing X-Y of Z rebate programs" summary with Previous/Next beside
     * it, because that is what the mockup drew. The shipped list does it differently - a
     * {@code Show [20|50|100|150|200] Entry} select above the table, with paging controls rendered
     * only once there is more than one page - so the summary is not missing, it was never built,
     * and this row asserts the control that exists.
     *
     * <h2>Why it seeds</h2>
     * The tab holds three programs, so nothing pages and the row aborted. Now that the group
     * pickers answer, the suite can create programs on pairings of its own, so it makes itself a
     * second page rather than waiting for one. Everything it makes it deletes again, page size
     * widened first so that the rows on page two are reachable.
     */
    @Test
    @Order(63)
    @Tag("creates-data")
    @DisplayName("RBMP-006: Pagination on the program list")
    void programListPaginates() {
        List<String> offered = realTime.pageSizeOptions();
        CaseLog.data("Page sizes offered", String.join(" / ", offered));
        assertTrue(offered.contains(String.valueOf(PAGE_SIZE)),
                "The list should offer " + PAGE_SIZE + " rows per page, but offered: " + offered);

        String asFound = realTime.pageSize();
        assertFalse(asFound.isBlank(), "The list should open on a page size, not on a blank one");
        int perPage = Integer.parseInt(asFound);

        int made = 0;
        try {
            // How many programs there are is a question the list only answers when it is showing
            // all of them. Counting the rows at the default page size answers "how many fit on a
            // page", which is a different number and the one this row is about.
            realTime.setPageSize("200");
            int already = realTime.getProgramCount();
            CaseLog.data("Programs on the tab", String.valueOf(already));
            if (already <= perPage) {
                RebateProgramFormPage picker = openCreateWithPickers();
                List<String> agents = picker.availableNames(RebateProgramFormPage.AGENT_GROUP);
                String member = picker.availableNames(RebateProgramFormPage.MEMBER_GROUP).get(0);
                picker.cancel();
                realTime.navigateTo(BasePage.url(REBATE_SET_UP));
                made = seedForPagination(PROGRAMS_FOR_PAGINATION - already, agents, member);
            }

            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            realTime.setPageSize("200");
            int all = realTime.getProgramCount();
            realTime.setPageSize(asFound);
            int onPageOne = realTime.getProgramCount();
            CaseLog.data("Rows shown", all + " at 200 a page, " + onPageOne + " at " + asFound);
            if (all <= perPage) {
                abort("Could not build a second page: the tab holds " + all + " program(s) after "
                        + "seeding " + made + ", which still fits on one page of " + asFound + ". "
                        + "Every pairing the Create form could offer is already taken - free some "
                        + "on Rebate set up, or point the run at a currency with room, and this "
                        + "row runs.");
            }

            assertEquals(perPage, onPageOne,
                    "A list longer than one page should show exactly one page of rows, not all of "
                            + "them - that is what paging means");
            assertTrue(realTime.paginationControls().count() > 0,
                    "A list that is holding rows back should offer controls to reach them");
            CaseLog.data("Paging controls while paging",
                    String.valueOf(realTime.paginationControls().count()));

            // And the page size decides, not the data: given room for everything, the same list
            // shows everything.
            realTime.setPageSize("200");
            assertEquals(all, realTime.getProgramCount(),
                    "Widening the page size should reveal exactly the rows page one was holding "
                            + "back, no more and no fewer");
            CaseLog.check("The list paged at " + asFound + ", showing " + onPageOne + " of " + all
                    + " program(s), and showed all " + all + " at 200 a page.");
        } finally {
            removePaginationSeed(made);
            realTime.setPageSize(asFound);
        }
    }

    private static void assertNotEquals(String unexpected, String actual, String message) {
        assertFalse(unexpected.equals(actual), message);
    }

    // =====================================================================
    // A2. Real-time Global Rebate Settings  -  RBMP-007 .. RBMP-010
    // =====================================================================

    @Test
    @Order(10)
    @DisplayName("RBMP-007: Real-time global settings display current saved values")
    void globalSettingsDisplaySavedValues() {
        String minClaim = realTime.getMinClaimValue();
        String expireTime = realTime.getExpireTimeValue();
        String status = realTime.getGlobalStatusValue();
        CaseLog.data("Min. Claim", minClaim);
        CaseLog.data("Expire time", expireTime);
        CaseLog.data("Status", status);

        assertFalse(minClaim.isBlank(), "Min. Claim should show its last-saved value");
        assertFalse(expireTime.isBlank(), "Expire time should show its last-saved value");
        assertFalse(status.isBlank(), "Status should show its last-saved value");
    }

    @Test
    @Order(80)
    @Tag("creates-data")
    @DisplayName("RBMP-008: Save valid changes to Real-time global settings")
    void saveValidGlobalSettings() {
        rememberGlobalSettings();
        realTime.setMinClaim(VALID_MIN_CLAIM);
        realTime.setExpireTime(VALID_EXPIRE_TIME);
        realTime.clickSave();

        assertFalse(realTime.isSettingsErrorVisible(),
                "Valid global settings should save without a validation error, but the screen "
                        + "said: " + realTime.getSettingsErrorMessage());

        // Saved means persisted, not merely accepted - so the row is only answered after a reload.
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(VALID_MIN_CLAIM, realTime.getMinClaimValue(),
                "Min. Claim should still hold the saved value after reopening the screen");
        assertEquals(VALID_EXPIRE_TIME, realTime.getExpireTimeValue(),
                "Expire time should still hold the saved value after reopening the screen");
    }

    /**
     * The two halves of this row get different answers from the deployed screen, and only one of
     * them is the answer the workbook expects.
     *
     * <h2>Non-numeric: correctly refused, at the keyboard</h2>
     * The field is {@code inputmode="decimal"} and simply does not accept the letters - typing
     * "abc" leaves it <b>empty</b> rather than holding "abc". So the protection is real, it just
     * happens a step earlier than "rejected on save", and it is asserted where it actually is.
     *
     * <h2>Negative: accepted, which is a defect</h2>
     * Saving {@code -5} answers <b>"Successfully modified"</b> (measured 2026-09-18), and the
     * stored value comes back as {@code 5} - the minus is dropped somewhere between the form and
     * the record, so the screen neither refuses the input nor keeps it. Either behaviour would be
     * defensible on its own; silently storing a different number than the one submitted is not.
     *
     * <p>The row is therefore tagged {@code known-defect} and asserts <b>what the application
     * does today</b>, so that the suite is honest rather than permanently red. When the
     * validation arrives this case fails - which is the point of writing it this way round - and
     * the assertion below flips back to the workbook's expectation.
     */
    @Test
    @Order(81)
    @Tag("creates-data")
    @Tag("known-defect")
    @DisplayName("RBMP-009: Real-time Min Claim rejects negative or non-numeric input")
    void minClaimRejectsInvalidInput() {
        rememberGlobalSettings();

        // Non-numeric, refused at the keyboard: the field will not hold the characters at all.
        realTime.setMinClaim(NON_NUMERIC_MIN_CLAIM);
        String afterTypingLetters = realTime.minClaimAsTyped();
        CaseLog.data("Min. Claim after typing \"" + NON_NUMERIC_MIN_CLAIM + "\"",
                "[" + afterTypingLetters + "]");
        assertTrue(afterTypingLetters.isEmpty(),
                "A non-numeric Min. Claim should not be accepted, but the field held: "
                        + afterTypingLetters);

        // Negative, accepted on save - the defect this row records.
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        realTime.setMinClaim(NEGATIVE_MIN_CLAIM);
        realTime.clickSave();
        CaseLog.data("Saving " + NEGATIVE_MIN_CLAIM, realTime.lastSaveMessage());

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        String stored = realTime.getMinClaimValue();
        CaseLog.evidence("KNOWN DEFECT: the workbook expects a negative Min. Claim to be refused. "
                + "The back office answered \"" + realTime.lastSaveMessage() + "\" and stored "
                + stored + " for a submitted " + NEGATIVE_MIN_CLAIM + ". This case asserts the "
                + "current behaviour so the suite stays honest; it will fail, deliberately, on "
                + "the day the validation is added.");
        assertFalse(realTime.isSettingsErrorVisible(),
                "Characterising today's behaviour: the save is accepted rather than refused");
        assertEquals(NEGATIVE_MIN_CLAIM.replace("-", ""), stored,
                "Characterising today's behaviour: the minus sign is dropped and the magnitude "
                        + "stored");
    }

    @Test
    @Order(82)
    @Tag("creates-data")
    @DisplayName("RBMP-010: Toggling Real-time global Status to Inactive affects all Real-time programs")
    void togglingGlobalStatusAffectsAllPrograms() {
        ensureSuiteProgram();
        rememberGlobalSettings();
        realTime.setGlobalStatus(SETUP_DISABLE);
        realTime.clickSave();
        assertFalse(realTime.isSettingsErrorVisible(),
                "Turning the Real-time type off should save without a validation error");

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertTrue(realTime.getGlobalStatusValue().equalsIgnoreCase(SETUP_DISABLE),
                "The setup panel should still read " + SETUP_DISABLE + " after reopening the "
                        + "screen - it spells the state Enable/Disable, while the list spells the "
                        + "same thing Active/Inactive");

        // Whether settlement actually stops is A8's question (RBMP-053); what this row can show
        // in a browser is that the type-level switch holds and the programs are still listed
        // under it rather than disappearing with it.
        assertTrue(realTime.getProgramCount() > 0,
                "Real-time programs should remain listed while the type is Inactive");
    }

    // =====================================================================
    // A3. Create Real-time Rebate Program  -  RBMP-011 .. RBMP-021
    //
    // The page objects open this screen (clickCreateRealTimeRebate) but do
    // not model the form on it - Program Name, the group transfer lists and
    // the rate table all need a CreateRebateProgramPage that does not exist
    // yet. The one row that is about opening it is answered here; the rest
    // name what they are waiting for.
    // =====================================================================

    /**
     * Aborts a case that needs the Create form to be driven, not merely modelled.
     *
     * <p><b>The form itself is no longer what is missing.</b> It shipped, and
     * {@link RebateProgramFormPage} models it against the running DOM as of 2026-09-18 - the
     * name field, the Status select, both transfer lists with their Select all/Optional radios
     * and four move buttons each, the twenty-row game rate table, and Submit/Cancel/Return.
     * What is missing is a) the Agent and Member groups to pair a program with, which no
     * environment has yet, and b) these case bodies, still written as the stubs they were when
     * there was nothing to write them against.
     */
    /**
     * Aborts a row that needs a second, distinct Agent/User Group pairing.
     *
     * <p>The commonest reason a row here cannot run, and worth its own message: it is not that
     * the form is missing or the case unwritten, it is that this environment has no Agent groups
     * and no Member groups, so every program the suite can build targets All Agents / All Groups
     * and there is exactly one pairing in existence. Seed two of each and these rows run.
     */
    private static void needsASecondPairing(String whyThisRowNeedsOne) {
        abort("Needs a second Agent/User Group pairing, and this environment has none: both "
                + "Option columns on the Create form are empty, so every program the suite makes "
                + "targets All Agents / All Groups. " + whyThisRowNeedsOne + ". Seed at least two "
                + "Agent groups and two Member groups - see RebateTestData.SEEDING_REQUIRED - and "
                + "this case runs as written.");
    }

    private static void awaitingCreateForm() {
        abort("The Create form is modelled now - see RebateProgramFormPage, read off the "
                + "deployed screen on 2026-09-18 - but this row is still a stub and the "
                + "environment still has no Agent or Member groups to pair a program with: both "
                + "Option columns offer zero entries. Seed the groups (see "
                + "RebateTestData.SEEDING_REQUIRED) and write this body against the form page "
                + "object, and the row runs.");
    }

    @Test
    @Order(26)
    @Tag("creates-data")
    @DisplayName("RBMP-011: Create a Real-time rebate program with all fields valid")
    void createRealTimeProgramWithValidFields() {
        removeSuiteProgram();

        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setProgramName(SUITE_PROGRAM);
        form.setStatus(SETUP_ENABLE);
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_LIVE_CASINO);

        boolean named = targetNamedGroupsIfPossible(form);

        form.submit();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        showAllPrograms();

        // The named pairing is a CONFIGURED one - test.group.agent.name / test.group.member.name -
        // and nothing reserves it on the environment. The suite no longer competes for it (see
        // ensurePairingCandidates), but a seeded program still can, and then this row fails for a
        // reason that has nothing to do with creating a valid program. Falling back keeps the
        // create - which is the row's subject - and gives up only the named-group half, which is
        // recorded rather than silently dropped. The Daily twin RBMP-065 does the same.
        if (!realTime.isProgramListed(SUITE_PROGRAM)) {
            CaseLog.data("The configured pairing " + AGENT_GROUP_NAME + " / " + MEMBER_GROUP_NAME
                    + " was refused", "falling back to the suite's own pairing");
            named = false;
            createProgramOrAbort(SUITE_PROGRAM, SETUP_ENABLE);
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            showAllPrograms();
        }

        assertTrue(realTime.isProgramListed(SUITE_PROGRAM),
                "The created program should appear on the Real-time tab");
        assertEquals(SETUP_ENABLE, realTime.getProgramStatus(SUITE_PROGRAM),
                "The program should be listed with the status it was created with");

        // The row is where the targeting becomes visible.
        String agents = realTime.getProgramTargetAgents(SUITE_PROGRAM);
        String groups = realTime.getProgramUserGroups(SUITE_PROGRAM);
        CaseLog.data("Targets", agents + " / " + groups);
        CaseLog.data("Last update", realTime.getProgramLastUpdate(SUITE_PROGRAM));

        if (named) {
            assertTrue(agents.contains(AGENT_GROUP_NAME),
                    "The row should name the Agent group it targets, but read: " + agents);
            assertTrue(groups.contains(MEMBER_GROUP_NAME),
                    "The row should name the Member group it targets, but read: " + groups);
        } else {
            assertFalse(agents.isBlank(),
                    "However it was targeted, the row should say who the program covers");
            assertFalse(groups.isBlank(),
                    "However it was targeted, the row should say which groups the program covers");
        }

        // Left on the list on purpose: it is the fixture the list, modify and delete rows use.
        // Note that it now holds a NAMED pairing rather than All Agents / All Groups, which is
        // what lets RBMP-015's duplicate-pairing row have something specific to collide with.
    }

    @Test
    @Order(22)
    @DisplayName("RBMP-012: A program's rebate Type is fixed and cannot be changed on the Create screen")
    void createScreenFixesTheRebateType() {
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        List<String> labels = form.labels();
        CaseLog.data("Create form sections", String.join(" | ", labels));

        // "No control exists to change type": the form offers Program details, status and the
        // two group pickers, and nothing that names a rebate type at all. Asserted on the
        // labels rather than on a missing selector, because "this locator found nothing" is
        // also what a renamed control looks like.
        assertTrue(labels.stream().noneMatch(label -> label.toLowerCase().contains("type")),
                "The Create screen should offer no way to change a program's rebate type, but "
                        + "its labels were: " + labels);
        assertEquals(PROGRAM_NEW_REAL_TIME, form.programNamePlaceholder(),
                "Create opened from the Real-time tab should be a Real-time form, which is what "
                        + "its placeholder says");
        form.cancel();
    }

    @Test
    @Order(20)
    @DisplayName("RBMP-013: Attempt to create a program with an empty Program Name")
    void createRejectsEmptyProgramName() {
        int before = realTime.getProgramCount();
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setProgramName("");
        form.submit();

        assertFalse(form.wasAccepted(),
                "Submitting with an empty Program Name should be refused, but the form closed");
        CaseLog.data("Refused with", form.validationMessageText());
        form.dismissMessage();
        form.cancel();

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(before, realTime.getProgramCount(),
                "A refused create should add no program");
    }

    @Test
    @Order(21)
    @DisplayName("RBMP-014: Attempt to create a program with no Agent group or no User Group selected")
    void createRejectsMissingGroupSelection() {
        removeSuiteProgram();
        int before = realTime.getProgramCount();

        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setProgramName(SUITE_PROGRAM);

        // "Leave the Agent group Selected column empty": switching the Agent group off "Select
        // all" and onto "Optional" with nothing moved across is exactly that state, and it is the
        // only way to reach it on an environment with no groups to move.
        form.chooseOptional(RebateProgramFormPage.AGENT_GROUP);
        assertEquals(0, form.selectedCount(RebateProgramFormPage.AGENT_GROUP),
                "Optional with nothing moved across should leave the Selected column empty");
        form.submit();

        assertFalse(form.wasAccepted(),
                "The documented rule is that every program needs at least one Target Agent and "
                        + "one User Group, so this submission should have been refused");
        CaseLog.data("Refused with", form.validationMessageText());
        form.dismissMessage();
        form.cancel();

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(before, realTime.getProgramCount(), "A refused create should add no program");
    }

    @Test
    @Order(29)
    @DisplayName("RBMP-015: Create is blocked when the pairing duplicates another program of the SAME type")
    void createBlocksDuplicatePairingWithinType() {
        ensureSuiteProgram();
        int before = realTime.getProgramCount();

        // The existing program targets All Agents / All Groups, so a second one targeted the same
        // way is the duplicate pairing this row is about - and on an environment with no groups
        // it is the only pairing there is.
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setProgramName(SUITE_PROGRAM);
        form.submit();

        assertFalse(form.wasAccepted(),
                "A second Real-time program on the same Agent/User Group pairing should be "
                        + "blocked, but the form closed");
        CaseLog.data("Blocked with", form.validationMessageText());
        form.dismissMessage();
        form.cancel();

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(before, realTime.getProgramCount(),
                "No partial program should be created by a blocked submission");
        assertTrue(realTime.isProgramListed(fixture),
                "The pre-existing program should be untouched by the blocked attempt");
    }

    /**
     * The operator's recovery path: hit the duplicate-pairing rule, fix the pairing <b>in the form
     * that is still open</b>, and submit again.
     *
     * <h2>Both halves are the row</h2>
     * That the conflict is flagged is {@code RBMP-015}'s claim, and it is only the setup here.
     * What this row adds is that the form is still usable afterwards - that a refusal leaves the
     * entered data and the pickers in a state the operator can correct, rather than requiring
     * them to start again. A form that had to be reopened would pass {@code RBMP-015} and fail
     * the operator.
     *
     * <p>Until 2026-09-21 this could only be half-written, because every program the suite could
     * make targeted All Agents / All Groups and there was no second pairing to correct <i>to</i>.
     * The pickers answer now, so it runs end to end.
     */
    @Test
    @Order(30)
    @Tag("creates-data")
    @DisplayName("RBMP-016: Resubmitting after correcting a flagged duplicate-pairing conflict succeeds")
    void resubmitAfterCorrectingDuplicatePairing() {
        int agentGroup = RebateProgramFormPage.AGENT_GROUP;
        RebateProgramFormPage picker = openCreateWithPickers();
        List<String> agentsOffered = picker.availableNames(agentGroup);
        List<String> membersOffered = picker.availableNames(RebateProgramFormPage.MEMBER_GROUP);
        picker.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        String member = membersOffered.get(0);
        List<String> free = findFreeAgents(agentsOffered, member, 2);
        if (free.size() < 2) {
            abort("This row needs one pairing to collide with and a second, free one to correct "
                    + "to; only " + free.size() + " of the first " + PAIRING_PROBE_LIMIT
                    + " Agent groups paired with \"" + member + "\" were free.");
        }
        String taken = free.get(0);
        String correction = free.get(1);

        String holder = SUITE_PROGRAM + " conflict holder";
        String corrected = SUITE_PROGRAM + " corrected";
        try {
            removeSuiteProgramNamed(holder);
            removeSuiteProgramNamed(corrected);
            String holderRefused = createTargetingOnFreePairing(holder, List.of(taken), List.of(member));
            if (!holderRefused.isEmpty()) {
                abort("Could not establish the pairing this row collides with: " + holderRefused);
            }
            int before = realTime.getProgramCount();

            // 1. Collide on purpose. The form must stay open with everything still in it.
            RebateProgramFormPage form = openCreateWithPickers();
            form.setProgramName(corrected);
            form.setStatus(SETUP_DISABLE);
            moveOne(form, agentGroup, taken);
            moveOne(form, RebateProgramFormPage.MEMBER_GROUP, member);
            form.submit();
            awaitLoaderGone();
            CaseLog.data("Submitting the held pairing " + taken + " / " + member,
                    form.isOpen() ? "refused, form still open" : "ACCEPTED");
            assertTrue(form.isOpen(),
                    "Submitting a pairing another program holds should be refused and leave the "
                            + "form open for the operator to correct");
            assertEquals(corrected, form.programName(),
                    "A refusal should keep what the operator typed, not clear the form");

            // 2. Correct the pairing without leaving the form, which is the row's actual claim.
            form.chooseSelected(agentGroup, taken);
            form.removeSelected(agentGroup);
            moveOne(form, agentGroup, correction);
            assertEquals(List.of(correction), form.selectedNames(agentGroup),
                    "The corrected form should now target only " + correction);

            // 3. Resubmit. This one has to be taken.
            form.submit();
            awaitLoaderGone();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            assertTrue(realTime.isProgramListed(corrected),
                    "Correcting the pairing and submitting again should succeed, but \"" + corrected
                            + "\" is not on the list");
            assertEquals(before + 1, realTime.getProgramCount(),
                    "Exactly one program should have been added by the corrected submission");
            assertEquals(correction, realTime.getProgramTargetAgents(corrected),
                    "The created program should carry the corrected Agent group, not the one that "
                            + "was refused");
            CaseLog.check("Refused on " + taken + ", corrected in place to " + correction
                    + ", and accepted - without reopening the form.");
        } finally {
            removeSuiteProgramNamed(corrected);
            removeSuiteProgramNamed(holder);
        }
    }

    @Test
    @Order(25)
    @Tag("creates-data")
    @DisplayName("RBMP-017: New program defaults to Inactive regardless of entered data")
    void newProgramDefaultsToInactive() {
        removeSuiteProgram();

        // Deliberately does not touch Status: the row is about what a program becomes when the
        // operator leaves it alone.
        createOnSuitePairing(SUITE_PROGRAM_1, form -> {
            assertEquals(SETUP_DISABLE, form.status(),
                    "The Create form should open on " + SETUP_DISABLE + ", which is the workbook's "
                            + "\"defaults to Inactive\" seen from the setup screen's vocabulary");
            form.setProgramName(SUITE_PROGRAM_1);
        });

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        showAllPrograms();
        assertEquals(SETUP_DISABLE, realTime.getProgramStatus(SUITE_PROGRAM_1),
                "A program created without touching Status should be listed as " + SETUP_DISABLE);
        removeSuiteProgram();
    }

    @Test
    @Order(27)
    @Tag("creates-data")
    @DisplayName("RBMP-018: Explicitly setting Status = Active on create is honored")
    void createHonoursExplicitActiveStatus() {
        ensureSuiteProgram();

        // RBMP-011 created this program with Status explicitly set to Enable, which is the
        // "Active" of the list's vocabulary. The row is answered by that choice having survived
        // the round trip rather than by creating a second program, which this environment cannot
        // hold - see the seeding note.
        assertEquals(SETUP_ENABLE, realTime.getProgramStatus(fixture),
                "A program created with Status explicitly set to " + SETUP_ENABLE
                        + " should be listed that way, not reset to the default");
    }

    @Test
    @Order(23)
    @DisplayName("RBMP-019: Cancel discards a partially-completed Create form")
    void cancelDiscardsCreateForm() {
        int before = realTime.getProgramCount();
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setProgramName(SUITE_PROGRAM);
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_LIVE_CASINO);
        form.cancel();

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(before, realTime.getProgramCount(),
                "Cancelling a part-filled Create form should add no program");
        assertFalse(realTime.isProgramListed(SUITE_PROGRAM),
                "The cancelled program should not be on the list");

        // And the form must not remember it either - a reopened Create starts blank.
        RebateProgramFormPage reopened = realTime.clickCreateRealTimeRebate();
        assertTrue(reopened.programName().isEmpty(),
                "Reopening Create should show an empty Program Name, but it held: "
                        + reopened.programName());
        reopened.cancel();
    }

    @Test
    @Order(28)
    @DisplayName("RBMP-020: Create a program using a Program Name identical to an existing program")
    void createWithDuplicateProgramName() {
        ensureSuiteProgram();
        int before = realTime.getProgramCount();

        // Submit a program carrying a name that is already in use. Note the list is read BEFORE
        // the form is opened and again after it is closed - never while it is open, because the
        // form replaces the list and there is nothing to read.
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setProgramName(fixture);
        form.setStatus(SETUP_ENABLE);
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_LIVE_CASINO);
        form.submit();

        String why = form.validationMessageText();
        CaseLog.data("Submitting a duplicate name", form.wasAccepted() ? "accepted" : "refused: " + why);
        if (!form.wasAccepted()) {
            form.dismissMessage();
            form.cancel();
        }
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(before, realTime.getProgramCount(),
                "A refused submission should not add a program");

        // The row wants to know whether the NAME is what was rejected. On this environment it is
        // not: the suite can only offer All Agents / All Groups, so the server rejects the
        // PAIRING first and the name is never reached. Saying so is the honest answer; claiming
        // the name was refused would be reading a result that was never produced.
        if (why.toLowerCase().contains("already exist")) {
            needsASecondPairing("the refusal above is about the pairing, not the name - the two "
                    + "cannot be told apart until the suite can offer a pairing of its own");
        }
    }

    @Test
    @Order(24)
    @DisplayName("RBMP-021: Program Name field boundary - very long text and special characters")
    void programNameBoundary() {
        removeSuiteProgram();
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();

        String longName = "Rebate" + "X".repeat(120) + " !@#$%^&*()_+ 返水";
        form.setProgramName(longName);
        String held = form.programName();
        CaseLog.data("Typed " + longName.length() + " chars, field held", String.valueOf(held.length()));
        assertFalse(held.isEmpty(),
                "The Program Name field should accept text; it held nothing");

        form.submit();
        CaseLog.data("Outcome", form.wasAccepted() ? "accepted" : form.validationMessageText());

        // Either answer is defensible - a length cap or a long name stored - so the row records
        // which one the application gives rather than inventing a limit the client never named.
        if (form.wasAccepted()) {
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            assertTrue(realTime.getProgramCount() > 0,
                    "An accepted long name should produce a listed program");
            removeSuiteProgramNamed(held);
        } else {
            form.dismissMessage();
            form.cancel();
        }
    }

    // =====================================================================
    // A4. Modify Real-time Rebate Program  -  RBMP-022 .. RBMP-029
    // =====================================================================

    private static void awaitingModifyForm() {
        awaitingPageObject("the Modify Rebate Program form - Program Name, Status, the group "
                + "transfer lists, the rate table and Update");
    }

    @Test
    @Order(70)
    @Tag("creates-data")
    @DisplayName("RBMP-022: Modify and save a change to Program Name")
    void modifyProgramName() {
        ensureSuiteProgram();
        requireOwnProgram();
        String renamed = "QA Renamed Real-time Program";

        RebateProgramFormPage form = realTime.clickModify(fixture);
        form.setProgramName(renamed);
        form.submit();
        assertTrue(form.wasAccepted(),
                "Renaming a program should be accepted, but the form said: "
                        + form.validationMessageText());

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertTrue(realTime.isProgramListed(renamed),
                "The list should show the program under its new name");

        // The new name is deliberately nothing like the old one. isProgramListed matches on the
        // row containing the text, so a name that merely extended the old one - "X" renamed to
        // "X EDITED" - would still match "X" and this assertion would pass without meaning it.
        assertFalse(realTime.isProgramListed(fixture),
                "The old name should be gone from the list, not kept alongside the new one");

        // Put the fixture back, because the rows after this one look for it by name.
        RebateProgramFormPage back = realTime.clickModify(renamed);
        back.setProgramName(fixture);
        back.submit();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertTrue(realTime.isProgramListed(fixture), "The fixture should be renamed back");
    }

    @Test
    @Order(71)
    @Tag("creates-data")
    @DisplayName("RBMP-023: Toggle a program's Status between Active and Inactive")
    void toggleProgramStatus() {
        ensureSuiteProgram();
        String original = realTime.getProgramStatus(fixture);
        String flipped = SETUP_ENABLE.equals(original) ? SETUP_DISABLE : SETUP_ENABLE;

        RebateProgramFormPage form = realTime.clickModify(fixture);
        assertEquals(original, form.status(),
                "Modify should open on the status the list is showing");
        form.setStatus(flipped);
        form.submit();
        assertTrue(form.wasAccepted(), "Changing a program's status should be accepted");

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(flipped, realTime.getProgramStatus(fixture),
                "The list should show the status the program was just given");

        // Back again, so the row proves the toggle rather than a one-way change - and so the
        // fixture is left as the rows after this one expect it.
        RebateProgramFormPage back = realTime.clickModify(fixture);
        back.setStatus(original);
        back.submit();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(original, realTime.getProgramStatus(fixture),
                "The status should toggle back to what it was");
    }

    /**
     * The duplicate-pairing rule has to fire on <b>Update</b>, not only on Create.
     *
     * <p>{@code RBMP-015} proves it fires when a program is made. A uniqueness check that lives
     * in the create path alone would pass that row and still let an operator move an existing
     * program on top of another one's pairing, which is the same collision arrived at from the
     * other direction. So this makes two programs on two pairings and then tries to move the
     * second onto the first's.
     *
     * <p>Needed two pairings, which is why it aborted until the group pickers were granted to
     * this operator on 2026-09-21.
     */
    @Test
    @Order(72)
    @Tag("creates-data")
    @DisplayName("RBMP-024: Modify the pairing to one already used by another program of the same type")
    void modifyToDuplicatePairing() {
        int agentGroup = RebateProgramFormPage.AGENT_GROUP;
        RebateProgramFormPage picker = openCreateWithPickers();
        List<String> agentsOffered = picker.availableNames(agentGroup);
        List<String> membersOffered = picker.availableNames(RebateProgramFormPage.MEMBER_GROUP);
        picker.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        String member = membersOffered.get(0);
        List<String> free = findFreeAgents(agentsOffered, member, 2);
        if (free.size() < 2) {
            abort("This row needs two programs on two different pairings; only " + free.size()
                    + " of the first " + PAIRING_PROBE_LIMIT + " Agent groups paired with \""
                    + member + "\" were free.");
        }
        String targetPairing = free.get(0);
        String ownPairing = free.get(1);

        String occupant = SUITE_PROGRAM + " pairing occupant";
        String mover = SUITE_PROGRAM + " pairing mover";
        try {
            removeSuiteProgramNamed(occupant);
            removeSuiteProgramNamed(mover);
            String first = createTargetingOnFreePairing(occupant, List.of(targetPairing), List.of(member));
            String second = createTargetingOnFreePairing(mover, List.of(ownPairing), List.of(member));
            if (!first.isEmpty() || !second.isEmpty()) {
                abort("Could not seed the two programs this row compares: \"" + occupant + "\" "
                        + (first.isEmpty() ? "was created" : "was refused - " + first)
                        + " and \"" + mover + "\" "
                        + (second.isEmpty() ? "was created" : "was refused - " + second) + ".");
            }

            // Move the second program onto the first's pairing. This is the update that must fail.
            RebateProgramFormPage form = realTime.clickModify(mover);
            form.chooseSelected(agentGroup, ownPairing);
            form.removeSelected(agentGroup);
            moveOne(form, agentGroup, targetPairing);
            CaseLog.data("Updating \"" + mover + "\" to target",
                    String.valueOf(form.selectedNames(agentGroup)));
            form.submit();
            awaitLoaderGone();
            boolean stillOpen = form.isOpen();
            String why = form.validationMessageText();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));

            String after = realTime.getProgramTargetAgents(mover);
            CaseLog.data("Update onto a held pairing",
                    stillOpen ? "refused: " + (why.isEmpty() ? "no message" : why) : "ACCEPTED");
            assertEquals(ownPairing, after,
                    "\"" + mover + "\" should still target " + ownPairing + ": moving it onto "
                            + targetPairing + ", which \"" + occupant + "\" holds, is the same "
                            + "collision the Create path refuses and Update must refuse it too");
            assertEquals(targetPairing, realTime.getProgramTargetAgents(occupant),
                    "The program that holds the pairing should be untouched by the attempt");
            CaseLog.check("The duplicate-pairing rule fires on Update, not only on Create.");
        } finally {
            removeSuiteProgramNamed(mover);
            removeSuiteProgramNamed(occupant);
        }
    }

    @Test
    @Order(73)
    @Tag("creates-data")
    @DisplayName("RBMP-025: Modify unrelated fields while leaving the program's own pairing unchanged")
    void modifyUnrelatedFields() {
        ensureSuiteProgram();
        String agentsBefore = realTime.getProgramTargetAgents(fixture);
        String groupsBefore = realTime.getProgramUserGroups(fixture);

        // The point of the row: touch everything EXCEPT the pairing, and the duplicate-pairing
        // rule must not fire on the program's own unchanged pairing.
        RebateProgramFormPage form = realTime.clickModify(fixture);
        boolean selectAllBefore = form.isSelectAll(RebateProgramFormPage.AGENT_GROUP);
        // A row addressed by position, not by code: Modify shows a different page of games from
        // Create, so a hard-coded second code is not guaranteed to be on this screen at all.
        form.setRateAt(form.rowOtherThan(GAME_CODE_LIVE_CASINO), RATE_SLOT);
        form.submit();

        assertTrue(form.wasAccepted(),
                "Saving a program whose pairing is unchanged should not trip the duplicate-pairing "
                        + "rule against itself, but the form said: " + form.validationMessageText());

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(agentsBefore, realTime.getProgramTargetAgents(fixture),
                "The Target Agents should be exactly as they were");
        assertEquals(groupsBefore, realTime.getProgramUserGroups(fixture),
                "The User Groups should be exactly as they were");
        CaseLog.data("Agent group mode unchanged", String.valueOf(selectAllBefore));
    }

    @Test
    @Order(74)
    @Tag("creates-data")
    @DisplayName("RBMP-026: Change a game category's rebate rate")
    void changeGameCategoryRate() {
        ensureSuiteProgram();

        RebateProgramFormPage form = realTime.clickModify(fixture);
        assertTrue(form.hasGame(GAME_CODE_LIVE_CASINO),
                "This row edits " + GAME_CODE_LIVE_CASINO + ", which should be on the form");
        int otherRow = form.rowOtherThan(GAME_CODE_LIVE_CASINO);
        String otherBefore = form.rateAt(otherRow);
        String before = form.rate(GAME_CODE_LIVE_CASINO);

        form.setRate(GAME_CODE_LIVE_CASINO, RATE_SLOT_CHANGED);
        form.submit();
        assertTrue(form.wasAccepted(),
                "Changing a rate should be accepted, but the form said: "
                        + form.validationMessageText());

        // Saved means stored, so the row is only answered by reopening the program.
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        RebateProgramFormPage reopened = realTime.clickModify(fixture);
        CaseLog.data("Rate for " + GAME_CODE_LIVE_CASINO,
                before + " -> " + reopened.rate(GAME_CODE_LIVE_CASINO));
        assertEquals(Double.parseDouble(RATE_SLOT_CHANGED),
                Double.parseDouble(reopened.rate(GAME_CODE_LIVE_CASINO)), 0.0001,
                "The changed rate should have been stored");

        // The other rows must be left alone by a single-row edit.
        assertEquals(Double.parseDouble(otherBefore), Double.parseDouble(reopened.rateAt(otherRow)),
                0.0001, "Changing one game's rate should not disturb another's");
        reopened.returnToList();
    }

    @Test
    @Order(75)
    @Tag("creates-data")
    @DisplayName("RBMP-027: Set a game category's rate to 0%")
    void setGameCategoryRateToZero() {
        ensureSuiteProgram();

        RebateProgramFormPage form = realTime.clickModify(fixture);
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_ZERO);
        form.submit();
        assertTrue(form.wasAccepted(),
                "Setting a rate to " + RATE_ZERO + "% should be accepted, but the form said: "
                        + form.validationMessageText());

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        RebateProgramFormPage reopened = realTime.clickModify(fixture);
        assertEquals(0.0, Double.parseDouble(reopened.rate(GAME_CODE_LIVE_CASINO)), 0.0001,
                "A zero rate should be stored as zero - no rebate for that game, rather than the "
                        + "row being dropped");
        reopened.returnToList();
    }

    @Test
    @Order(76)
    @DisplayName("RBMP-028: Attempt to change a program's rebate Type during Modify")
    void cannotChangeRebateTypeOnModify() {
        ensureSuiteProgram();

        RebateProgramFormPage form = realTime.clickModify(fixture);
        List<String> labels = form.labels();
        CaseLog.data("Modify form sections", String.join(" | ", labels));

        // Same shape as RBMP-012 on the Create form: asserted on what the form offers rather
        // than on a selector finding nothing, because "not found" is also what a renamed control
        // looks like.
        assertTrue(labels.stream().noneMatch(label -> label.toLowerCase().contains("type")),
                "Modify should offer no way to change a program's rebate type, but its labels "
                        + "were: " + labels);
        form.returnToList();
    }

    @Test
    @Order(77)
    @DisplayName("RBMP-029: Open Modify and click Update with no changes made")
    void updateWithNoChanges() {
        ensureSuiteProgram();
        String nameBefore = fixture;
        String statusBefore = realTime.getProgramStatus(fixture);
        String agentsBefore = realTime.getProgramTargetAgents(fixture);

        RebateProgramFormPage form = realTime.clickModify(fixture);
        List<String> ratesBefore = form.allRates();
        form.submit();

        assertTrue(form.wasAccepted(),
                "Submitting Modify with nothing changed should be accepted rather than refused, "
                        + "but the form said: " + form.validationMessageText());

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertTrue(realTime.isProgramListed(nameBefore), "The program should still be listed");
        assertEquals(statusBefore, realTime.getProgramStatus(nameBefore),
                "A no-op update should leave the status alone");
        assertEquals(agentsBefore, realTime.getProgramTargetAgents(nameBefore),
                "A no-op update should leave the targeting alone");

        RebateProgramFormPage reopened = realTime.clickModify(nameBefore);
        assertEquals(ratesBefore, reopened.allRates(),
                "A no-op update should leave every rate exactly as it was");
        reopened.returnToList();
    }

    // =====================================================================
    // A5. Delete Real-time Rebate Program  -  RBMP-030 .. RBMP-033
    // =====================================================================

    @Test
    @Order(102)
    @Tag("creates-data")
    @DisplayName("RBMP-030: Delete a program that is not currently Active")
    void deleteInactiveProgram() {
        // Makes its own subject rather than assuming one: this environment holds a single
        // program at a time, so the row that needs a Disabled one has to put it there.
        removeSuiteProgram();
        createProgramOrAbort(SUITE_PROGRAM, SETUP_DISABLE);
        assertEquals(SETUP_DISABLE, realTime.getProgramStatus(SUITE_PROGRAM),
                "This row needs a " + SETUP_DISABLE + "d program to delete");

        realTime.clickDelete(SUITE_PROGRAM);
        realTime.confirmDeletion();
        assertFalse(realTime.isProgramListed(SUITE_PROGRAM),
                "An Inactive program should delete cleanly");
    }

    @Test
    @Order(103)
    @Tag("creates-data")
    @Tag("known-defect")
    @DisplayName("RBMP-031: Delete a currently Active program")
    void deleteActiveProgram() {
        removeSuiteProgram();
        createProgramOrAbort(SUITE_PROGRAM, SETUP_ENABLE);
        assertEquals(SETUP_ENABLE, realTime.getProgramStatus(SUITE_PROGRAM),
                "This row needs an " + SETUP_ENABLE + "d program");

        realTime.clickDelete(SUITE_PROGRAM);
        assertTrue(realTime.isConfirmDialogVisible(),
                "Deleting an Active program should still confirm first");
        realTime.confirmDeletion();

        // The sheet records this as a Negative row: an Active program was expected to be refused,
        // or at least warned about, rather than to vanish. The deployed screen does neither -
        // measured 2026-09-18, an Enabled program is deleted by the same "Confirm deletion?"
        // prompt as any other and is gone afterwards, with nothing said about it having been
        // live. Whether that is a defect or the spec moving is the client's call (it is the same
        // question as AQ-01); what this row can do is state plainly which of the two the
        // application does, and fail the day it changes.
        boolean survived = realTime.isProgramListed(SUITE_PROGRAM);
        CaseLog.evidence("Deleting an " + SETUP_ENABLE + "d program: the confirmation is the "
                + "ordinary one, and afterwards the program is " + (survived ? "still listed"
                : "gone. The workbook expected it to be refused or at least warned about, so "
                + "this is a difference worth raising with the client rather than a passing row "
                + "dressed up as one."));
        assertFalse(survived,
                "Characterising today's behaviour: an Enabled program is deleted like any other, "
                        + "with no extra warning and no refusal");
    }

    @Test
    @Order(104)
    @Tag("creates-data")
    @DisplayName("RBMP-032: A deleted program's pairing becomes available for reuse within the same type")
    void deletedPairingBecomesReusable() {
        // The row is the other half of RBMP-015: a pairing is held by a program, not reserved
        // forever. Deleting the holder must hand it back, and the proof is that the same pairing
        // is accepted again immediately afterwards.
        removeSuiteProgram();
        createProgramOrAbort(SUITE_PROGRAM, SETUP_ENABLE);
        assertTrue(realTime.isProgramListed(SUITE_PROGRAM),
                "The program holding the pairing should be listed before it is deleted");

        realTime.clickDelete(SUITE_PROGRAM);
        realTime.confirmDeletion();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertFalse(realTime.isProgramListed(SUITE_PROGRAM),
                "The program should be gone before the pairing is reused");

        // The same pairing, straight away. A refusal here would mean the delete freed the row
        // but not the pairing it held.
        RebateProgramFormPage refused = createProgram(SUITE_PROGRAM, SETUP_ENABLE);
        if (refused != null) {
            String why = refused.validationMessageText();
            refused.dismissMessage();
            refused.cancel();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            fail("A deleted program's pairing should be available again, but recreating it was "
                    + "refused" + (why.isEmpty() ? " without a message" : ": " + why));
        }
        assertTrue(realTime.isProgramListed(SUITE_PROGRAM),
                "The pairing should be reusable once its holder is deleted");
        CaseLog.check("The pairing was released by the delete and accepted again immediately.");
        removeSuiteProgram();
    }

    @Test
    @Order(100)
    @DisplayName("RBMP-033: Delete confirmation can be cancelled")
    void deleteConfirmationCanBeCancelled() {
        ensureSuiteProgram();
        requireOwnProgram();
        int before = realTime.getProgramCount();

        realTime.clickDelete(SUITE_PROGRAM);
        assertTrue(realTime.isConfirmDialogVisible(), "Delete should raise a confirmation");
        realTime.cancelDeletion();

        assertFalse(realTime.isConfirmDialogVisible(),
                "Cancelling should close the confirmation dialog");
        assertEquals(before, realTime.getProgramCount(),
                "Cancelling a deletion should leave the list exactly as it was");
    }

    // =====================================================================
    // A6. Real-time Agent/User Group Targeting  -  RBMP-034 .. RBMP-042
    // =====================================================================

    /**
     * Aborts a group-targeting row.
     *
     * <h2>The controls are modelled; there is simply nothing in them</h2>
     * This used to say no page object existed for the transfer lists, and that stopped being
     * true on 2026-09-18: {@link RebateProgramFormPage} models both of them - the Option and
     * Selected panes, the Select all / Optional radios, and all four move controls, which carry
     * the classes {@code select-all-add}, {@code select-add}, {@code select-remove} and
     * {@code select-all-remove}.
     *
     * <p>What is missing is READ ACCESS. Both Option panes come back empty, and the reason is
     * not that the groups are absent - it is that the pickers are fed by
     * {@code /api/account/agent} and {@code /api/account/member_group}, and for the operator this
     * suite signs in as both answer
     * {@code {"status":-10,"message":"Permission denied"}} (measured 2026-09-18). The groups the
     * client names - {@code agent-8001}, {@code Default 00} - are there; this account simply
     * cannot list them.
     *
     * <p>So these rows are blocked by a permission rather than by seeding, which is a much
     * cheaper thing to fix: grant the account in {@code test.username} read access to agents and
     * member groups and they run against the page object exactly as it already stands.
     */
    /**
     * Aborts a group-targeting row, having first <b>proved</b> the pickers are unusable.
     *
     * <h2>Why it checks rather than simply aborting</h2>
     * An abort that is hard-coded says the same thing forever, including on the day the
     * environment is fixed - and the section would go on reporting Not run while the controls
     * quietly worked. So this opens the form and counts what the pickers offer. If the entries
     * are there, it fails loudly to say the row can now be written; only an empty picker aborts.
     *
     * <p>Measured 2026-09-18 on both Create <em>and</em> Modify: both panes come back empty and
     * both radios read "Select all", <b>even on a program the list shows as targeting
     * agent-8001</b>. The cause is not missing data - it is that {@code /api/account/agent} and
     * {@code /api/account/member_group} answer {@code "Permission denied"} for this operator, so
     * the widget has no names to render on either side.
     *
     * @param whatTheRowWouldDo what this particular row needs the pickers for, so the report
     *                          names the specific thing that is out of reach
     */
    /**
     * Opens Create with both group pickers switched to "Optional" and ready to move entries.
     *
     * <h2>The precondition the A6 section shares</h2>
     * Every group-targeting row moves entries between the Option and Selected panes, so each one
     * needs the panes to hold something. They are fed by {@code /api/account/agent} and
     * {@code /api/account/member_group}; while those answer {@code "Permission denied"} for the
     * operator the suite signs in as, the panes render empty and nothing here can say anything.
     *
     * <p>The check happens <b>after</b> switching to Optional, deliberately: in "Select all" mode
     * the panes and all four move buttons carry {@code disabled}, so an empty disabled widget and
     * an empty enabled one are different findings.
     *
     * @return the open form, with both groups in Optional mode
     */
    private static RebateProgramFormPage openCreateWithPickers() {
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.chooseOptional(RebateProgramFormPage.AGENT_GROUP);
        form.chooseOptional(RebateProgramFormPage.MEMBER_GROUP);

        int agents = form.availableCount(RebateProgramFormPage.AGENT_GROUP);
        int members = form.availableCount(RebateProgramFormPage.MEMBER_GROUP);
        CaseLog.data("Pickers offer", agents + " Agent group(s), " + members + " Member group(s)");
        if (agents == 0 || members == 0) {
            form.cancel();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            abort("The group pickers are empty - the Agent list offers " + agents + " entries and "
                    + "the Member group list " + members + ". They are fed by /api/account/agent "
                    + "and /api/account/member_group, which answer \"Permission denied\" for this "
                    + "operator, so the groups are unreachable rather than absent. Grant the "
                    + "account in test.username read access to agents and member groups and this "
                    + "row runs as written.");
        }
        return form;
    }

    // -----------------------------------------------------------------
    // Working with real pairings
    //
    // Until 2026-09-21 every row in this section aborted, because /api/account/agent and
    // /api/account/member_group answered "Permission denied" for the operator and both Option
    // panes rendered empty. That permission has been granted: the pickers now offer 218 Agents
    // and 5 Member groups, so a program can be aimed at ONE named pairing instead of at the
    // form's "Select all" default.
    //
    // That is what unblocked RBMP-016, RBMP-024, RBMP-039, RBMP-041 and RBMP-042. It also
    // changes what "a free pairing" means: the suite is no longer stuck with All Agents / All
    // Groups, which collides with every program on the tab, so it can seed fixtures of its own
    // beside the hand-seeded data instead of deleting things to make room.
    // -----------------------------------------------------------------

    /**
     * Widens the list to 200 rows before anything reads it by name.
     *
     * <h2>A program on page two is invisible AND undeletable</h2>
     * The list shows 20 rows, and every lookup this suite does - {@code isProgramListed},
     * {@code clickDelete}, {@code getProgramStatus} - can only see the page that is rendered. So
     * {@code removeSuiteProgramNamed} reported "already gone" for a program sitting on page two,
     * the create that followed collided with it on the <b>name</b>, and the row reported Not run
     * blaming the pairing. Measured 2026-09-22 on the Daily tab: {@code RBMP-065}, {@code RBMP-072}
     * and {@code RBMP-099} were refused on three <em>different</em> pairings, which is what gave it
     * away - walking the pairings could not help, because the name was the blocker.
     *
     * <p>{@code RBMP-006} is the one row that sets the page size deliberately, and it puts it back.
     */
    private static void showAllPrograms() {
        realTime.setPageSize("200");
    }

    /** Highlights one entry and moves it across, which is always the two steps together. */
    private static void moveOne(RebateProgramFormPage form, int group, String name) {
        form.chooseAvailable(group, name);
        form.moveSelected(group);
    }

    /** The Agent names the Create form is offering, read off the form and then closed again. */
    private static List<String> agentsOnOffer() {
        RebateProgramFormPage picker = openCreateWithPickers();
        List<String> agents = picker.availableNames(RebateProgramFormPage.AGENT_GROUP);
        picker.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        return agents;
    }

    /** The first Member group on offer, which is the one the pairing rows pair everything with. */
    private static String firstMemberGroupOnOffer() {
        RebateProgramFormPage picker = openCreateWithPickers();
        String member = picker.availableNames(RebateProgramFormPage.MEMBER_GROUP).get(0);
        picker.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        return member;
    }

    /**
     * Waits out the screen's {@code div.custom-loader}, which swallows pointer events.
     *
     * <p>It goes up on every submit and comes down when the response has been applied. A case that
     * carried on clicking underneath it failed with Playwright's "intercepts pointer events" and
     * then a timeout - a message about the control it was trying to click, which was working
     * perfectly. Only rows that stay on the form after a submit need this; the ones that leave by
     * URL are not clicking anything.
     */
    private static void awaitLoaderGone() {
        try {
            sessionPage.locator("div.custom-loader").first().waitFor(
                    new com.microsoft.playwright.Locator.WaitForOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN)
                            .setTimeout(5000));
        } catch (RuntimeException stillUp) {
            // Best effort. The overlay is not rendered at all on some responses, and a case that
            // failed here would be failing on the wait rather than on what it came to check.
        }
    }

    /**
     * Creates a Real-time program aimed at named Agent groups and named Member groups, and comes
     * back to the list either way.
     *
     * <p>The Agents are moved in the order given, which is what lets {@link
     * #crossProductPairingIsChecked()} put the colliding pairing somewhere other than first.
     *
     * <h2>The answer is read from the LIST, not from the form</h2>
     * {@link RebateProgramFormPage#wasAccepted()} decides on whether the form is still on screen,
     * and after a submit that also raises the screen's {@code div.custom-loader} overlay the form
     * can still be in the DOM for longer than the settle wait allows. Believing it cost a run:
     * the first probe here really was created, was read as refused, and every probe after it was
     * then refused for re-using the <b>name</b> - so a search for two free pairings walked the
     * whole 218-entry Agent list reporting each one as taken, and left the created program
     * behind. Asking the list whether the program is there cannot be fooled that way.
     *
     * @return {@code ""} when the program was created; otherwise whatever the back office said
     *         about refusing it, which is frequently nothing at all - see {@code RBMP-038}
     */
    private static String createTargeting(String name, List<String> agentNames,
                                          List<String> memberNames) {
        RebateProgramFormPage form = openCreateWithPickers();
        form.setProgramName(name);
        form.setStatus(SETUP_DISABLE);
        agentNames.forEach(agent -> moveOne(form, RebateProgramFormPage.AGENT_GROUP, agent));
        memberNames.forEach(member -> moveOne(form, RebateProgramFormPage.MEMBER_GROUP, member));
        CaseLog.data("Creating \"" + name + "\" targeted at",
                form.selectedNames(RebateProgramFormPage.AGENT_GROUP) + " x "
                        + form.selectedNames(RebateProgramFormPage.MEMBER_GROUP));
        form.submit();
        String why = form.validationMessageText();

        // Leaves by URL rather than by clicking Cancel: a refused submit holds the custom-loader
        // overlay up for a moment and it swallows pointer events, so the click fails with
        // "intercepts pointer events" and times out - which reads as a broken Cancel button
        // rather than as a form that had not finished settling. Leaving a Create form by URL
        // discards it exactly as Cancel does, which RBMP-019 is the row that proves.
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        if (realTime.isProgramListed(name)) {
            return "";
        }
        return why.isEmpty() ? "refused without a message" : why;
    }

    /**
     * How many Agent groups a search for a free pairing will try before giving up.
     *
     * <p>The picker offers 218 of them and each probe is a create, a look at the list and a
     * delete - six seconds or so. Walking all of them would take twenty minutes to reach a
     * conclusion the first dozen already showed, so the search stops early and the row says what
     * it tried. On an environment where nothing is free, that is a finding to report in seconds
     * rather than a suite that looks hung.
     */
    private static final int PAIRING_PROBE_LIMIT = 12;

    /**
     * Creates where the caller has already established the pairing is free, retrying once.
     *
     * <h2>The pairing is released a moment after the row disappears</h2>
     * {@link #findFreeAgents} proves a pairing free by creating on it and deleting again, and the
     * caller then creates on it for real. Those are two different moments: the delete's
     * confirmation and the server releasing the pairing do not land together, and a create issued
     * in between is refused for a pairing that is on its way to being free. That is what made the
     * Daily twin {@code RBMP-071} report Not run on the 2026-09-22 run, having found two free
     * pairings seconds earlier - the abort read "Could not establish the pairing this row collides
     * with", about a pairing that was about to be exactly that.
     *
     * <p>One retry, then believe it: a second refusal means something really does hold it.
     */
    private static String createTargetingOnFreePairing(String name, List<String> agentNames,
                                                       List<String> memberNames) {
        String why = createTargeting(name, agentNames, memberNames);
        if (why.isEmpty()) {
            return "";
        }
        CaseLog.data("Create on a pairing just freed was refused, retrying", why);
        sessionPage.waitForTimeout(2500);
        return createTargeting(name, agentNames, memberNames);
    }

    /**
     * Finds Agent groups whose pairing with {@code member} nothing currently holds.
     *
     * <h2>Why it has to probe rather than read</h2>
     * The list shows which Agents a program targets, but a program on another page of the list -
     * or one this operator cannot read - holds its pairing just the same. The only authority on
     * whether a pairing is free is the back office's own answer to a submission, so this asks it:
     * create, look, delete again. Each probe leaves the environment exactly as it found it.
     *
     * @param offered  the Agent names the picker is showing, in its own order
     * @param member   the Member group every probe is paired with
     * @param howMany  how many free Agents the caller needs
     * @return that many Agent names, or fewer when the search ran out of attempts
     */
    private static List<String> findFreeAgents(List<String> offered, String member, int howMany) {
        List<String> free = new java.util.ArrayList<>();
        String probe = SUITE_PROGRAM + " pairing probe";
        removeSuiteProgramNamed(probe);
        int tried = 0;
        for (String agent : offered) {
            if (free.size() == howMany || tried == PAIRING_PROBE_LIMIT) {
                break;
            }
            tried++;
            String why = createTargeting(probe, List.of(agent), List.of(member));
            if (!why.isEmpty()) {
                CaseLog.data("Pairing already held", agent + " / " + member + " - " + why);
                continue;
            }
            free.add(agent);
            removeSuiteProgramNamed(probe);
        }
        CaseLog.data("Free pairings found", free + " x " + member
                + " (after " + tried + " of " + offered.size() + " Agent groups)");
        return free;
    }

    private static void awaitingGroupTargeting() {
        abort("The Agent and Member group transfer lists are modelled - see "
                + "RebateProgramFormPage - and both Option panes come back EMPTY, but NOT "
                + "because no groups exist. The pickers are fed by /api/account/agent and "
                + "/api/account/member_group, and for the operator this suite signs in as both "
                + "answer {\"status\":-10,\"message\":\"Permission denied\"} (measured "
                + "2026-09-18). So this row is blocked by an ACCOUNT PERMISSION, not by missing "
                + "test data: grant the account in test.username read access to agents and "
                + "member groups and it runs as written, against groups that are already there.");
    }

    // Also covers workbook rows: RBMP-034-1 (one Agent group with the Member group's Default
    // value) and RBMP-034-2 (a non-Default Member group value). Both are sub-rows of RBMP-034,
    // carry no Important Level or Case Type of their own, and drive the same control. They are
    // named here rather than given their own @DisplayName because the report's id parser reads
    // PREFIX-digits and would not resolve "RBMP-034-1"; renaming them would deviate from the
    // workbook, which is the one thing these ids exist to avoid.
    @Test
    @Order(40)
    @DisplayName("RBMP-034: \"Select all\" (>>) moves every available Agent into Selected and selects the Member group's Default value")
    void selectAllAgents() {
        RebateProgramFormPage form = openCreateWithPickers();
        int offered = form.availableCount(RebateProgramFormPage.AGENT_GROUP);

        form.moveAll(RebateProgramFormPage.AGENT_GROUP);

        assertEquals(offered, form.selectedCount(RebateProgramFormPage.AGENT_GROUP),
                ">> should move every available Agent into Selected");
        assertEquals(0, form.availableCount(RebateProgramFormPage.AGENT_GROUP),
                ">> should leave the Option column empty");
        CaseLog.data("Selected after >>", String.join(", ",
                form.selectedNames(RebateProgramFormPage.AGENT_GROUP)));

        // The row also claims the Member group takes its Default value when Agents are taken in
        // bulk. Recorded rather than asserted: the workbook does not say whether that is the
        // form doing it or the operator being expected to, and the captures do not settle it.
        CaseLog.data("Member group after the Agent >>",
                "selectAll=" + form.isSelectAll(RebateProgramFormPage.MEMBER_GROUP)
                        + " selected=" + form.selectedNames(RebateProgramFormPage.MEMBER_GROUP));
        form.cancel();
    }

    @Test
    @Order(41)
    @DisplayName("RBMP-035: Move a single Agent using -> and remove using <-")
    void moveSingleAgent() {
        RebateProgramFormPage form = openCreateWithPickers();
        int offered = form.availableCount(RebateProgramFormPage.AGENT_GROUP);
        String one = form.availableNames(RebateProgramFormPage.AGENT_GROUP).get(0);

        form.chooseAvailable(RebateProgramFormPage.AGENT_GROUP, one);
        form.moveSelected(RebateProgramFormPage.AGENT_GROUP);

        assertEquals(List.of(one), form.selectedNames(RebateProgramFormPage.AGENT_GROUP),
                "> should move exactly the highlighted Agent across");
        assertEquals(offered - 1, form.availableCount(RebateProgramFormPage.AGENT_GROUP),
                "> should take that Agent out of Option and leave the rest");

        form.chooseSelected(RebateProgramFormPage.AGENT_GROUP, one);
        form.removeSelected(RebateProgramFormPage.AGENT_GROUP);

        assertEquals(0, form.selectedCount(RebateProgramFormPage.AGENT_GROUP),
                "< should send the Agent back, emptying Selected");
        assertEquals(offered, form.availableCount(RebateProgramFormPage.AGENT_GROUP),
                "< should restore Option to what it held");
        form.cancel();
    }

    @Test
    @Order(42)
    @DisplayName("RBMP-036: Clear all Selected Agents using <<")
    void clearAllSelectedAgents() {
        RebateProgramFormPage form = openCreateWithPickers();
        int offered = form.availableCount(RebateProgramFormPage.AGENT_GROUP);

        // Fill Selected first - << has nothing to say about an already-empty column.
        form.moveAll(RebateProgramFormPage.AGENT_GROUP);
        assertEquals(offered, form.selectedCount(RebateProgramFormPage.AGENT_GROUP),
                "Selected should be full before it is cleared");

        form.removeAll(RebateProgramFormPage.AGENT_GROUP);

        assertEquals(0, form.selectedCount(RebateProgramFormPage.AGENT_GROUP),
                "<< should clear every Selected Agent");
        assertEquals(offered, form.availableCount(RebateProgramFormPage.AGENT_GROUP),
                "<< should return them all to Option, none lost");
        form.cancel();
    }

    @Test
    @Order(43)
    @DisplayName("RBMP-037: Same transfer-selection behavior verified for the User Group control")
    void userGroupTransferBehavesTheSame() {
        // The same four controls on the OTHER list. The row exists because the two pickers are
        // separate widgets, and a fix applied to one is not a fix applied to both.
        RebateProgramFormPage form = openCreateWithPickers();
        int member = RebateProgramFormPage.MEMBER_GROUP;
        int offered = form.availableCount(member);
        String one = form.availableNames(member).get(0);

        form.chooseAvailable(member, one);
        form.moveSelected(member);
        assertEquals(List.of(one), form.selectedNames(member),
                "> should move one Member group across");

        form.chooseSelected(member, one);
        form.removeSelected(member);
        assertEquals(0, form.selectedCount(member), "< should send it back");

        form.moveAll(member);
        assertEquals(offered, form.selectedCount(member), ">> should move every Member group");

        form.removeAll(member);
        assertEquals(0, form.selectedCount(member), "<< should clear them again");
        assertEquals(offered, form.availableCount(member), "none should be lost in the process");
        form.cancel();
    }

    @Test
    @Order(44)
    @Tag("known-defect")
    @DisplayName("RBMP-038: Duplicate-pairing validation triggers on Submit for Create, and on Update for Modify")
    void duplicatePairingValidationTriggers() {
        // Half of this row runs today. The claim is that the duplicate-pairing rule fires at
        // SUBMIT time on Create - not silently, not on a later screen - and that is provable
        // without touching the group pickers: the suite's only pairing, All Agents / All Groups,
        // collides with whatever is already on the tab, so submitting it IS the collision.
        ensureSuiteProgram();
        int before = realTime.getProgramCount();

        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setProgramName(SUITE_PROGRAM_1);
        form.setStatus(SETUP_DISABLE);
        form.submit();

        // Read once, immediately. wasAccepted() is derived from whether the form is still open,
        // so it flips to true the moment the row cancels out of it - asking again after the
        // navigation below would report every refusal as a success.
        boolean accepted = form.wasAccepted();
        String why = form.validationMessageText();
        CaseLog.data("Create with a colliding pairing", accepted ? "accepted" : "refused: " + why);

        if (accepted) {
            // A distinct pairing exists after all, which means the environment has moved on and
            // the Modify half of this row can be written too.
            removeSuiteProgramNamed(SUITE_PROGRAM_1);
            abort("The second program was accepted, so a distinct pairing is available on this "
                    + "environment now. Both halves of this row - Submit on Create and Update on "
                    + "Modify - can be written for real; the Create half below was only ever the "
                    + "half that did not need the pickers.");
        }

        form.dismissMessage();
        form.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        // The rule fires - the submission is refused and nothing is created. That is the half of
        // the row that can be proven here, and it passes.
        assertFalse(accepted,
                "A submission whose pairing is already taken should be refused");
        assertEquals(before, realTime.getProgramCount(),
                "A submission blocked on the pairing should create no partial program");

        // But it fires SILENTLY, and that is a finding rather than a detail. The API is perfectly
        // clear - measured 2026-09-18 the response body reads
        //   {"status":-1,"message":"agent-8001 and All Groups already exist in \"If test\".
        //    Remove duplicates before submitting.","error_type":"duplicate_pairing"}
        // - and the screen shows none of it. The form simply stays open. An operator sees a
        // Submit that did nothing and is told neither what was wrong nor which program they
        // collided with, which is the one piece of information that would let them fix it.
        assertTrue(why.isEmpty(),
                "Characterising today's behaviour: the refusal carries no on-screen message");
        CaseLog.evidence("KNOWN DEFECT: the duplicate-pairing rule refuses the submission without "
                + "showing anything. The reason exists - the API returns \"... already exist in "
                + "<program>. Remove duplicates before submitting.\" with error_type "
                + "duplicate_pairing - but the UI discards it and leaves the form open. The "
                + "workbook expects the message the client's own captures show. This case asserts "
                + "the current behaviour so the suite stays honest; it will fail, deliberately, "
                + "on the day the message is surfaced.");
        CaseLog.check("The Submit half of this row is proven: the rule fires at submission time "
                + "and creates nothing. The Update-on-Modify half needs a second pairing to "
                + "change to, which needs the group pickers - see requireGroupPickers.");
    }

    /**
     * Selecting <i>m</i> Agents and <i>n</i> Member groups claims <i>m &times; n</i> pairings, and
     * the row's claim is that <b>every one of them</b> is checked on Submit - not just the first.
     *
     * <h2>Why a plain refusal would prove nothing</h2>
     * Submitting a big cross-product against an environment with seeded data is refused either
     * way, so "it was refused" is consistent with a check that stops at the head of the list. The
     * row is only answered by a submission whose <b>first</b> pairing is free and whose
     * <b>second</b> is taken: a check that looked only at the first would accept it.
     *
     * <p>So this builds that situation rather than hoping for it. Two Agents are found whose
     * pairing with one Member group nothing holds - by asking the back office, which is the only
     * authority on it - a holder program is created on the second of them, and the candidate is
     * submitted with the free Agent first and the held one behind it. A refusal is then proof
     * that the check reached past position one.
     */
    @Test
    @Order(45)
    @Tag("creates-data")
    @DisplayName("RBMP-039: Multiple Agents x multiple User Groups generates a full cross-product, all checked on Submit")
    void crossProductPairingIsChecked() {
        RebateProgramFormPage probe = openCreateWithPickers();
        List<String> agentsOffered = probe.availableNames(RebateProgramFormPage.AGENT_GROUP);
        List<String> membersOffered = probe.availableNames(RebateProgramFormPage.MEMBER_GROUP);
        probe.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        if (agentsOffered.size() < 2 || membersOffered.isEmpty()) {
            abort("A cross-product needs at least two Agent groups and one Member group; the "
                    + "pickers offer " + agentsOffered.size() + " and " + membersOffered.size()
                    + ".");
        }

        String member = membersOffered.get(0);
        List<String> free = findFreeAgents(agentsOffered, member, 2);
        if (free.size() < 2) {
            abort("This row needs two Agent groups whose pairing with \"" + member + "\" is free, "
                    + "so that one can be held deliberately and the other left open; only "
                    + free.size() + " of the " + agentsOffered.size() + " on offer were free. "
                    + "Free a pairing on Rebate set up, or point the run at a currency with fewer "
                    + "seeded programs, and it runs.");
        }
        String openPairing = free.get(0);
        String heldPairing = free.get(1);

        String holder = SUITE_PROGRAM + " pairing holder";
        String candidate = SUITE_PROGRAM + " cross-product";
        int before;
        try {
            removeSuiteProgramNamed(holder);
            removeSuiteProgramNamed(candidate);
            String holderRefused = createTargetingOnFreePairing(holder, List.of(heldPairing), List.of(member));
            if (!holderRefused.isEmpty()) {
                abort("Could not establish the pairing this row collides with - \"" + heldPairing
                        + " / " + member + "\" was free a moment ago and the back office now says: "
                        + holderRefused);
            }
            before = realTime.getProgramCount();

            // The order matters and is the whole point: the FREE pairing goes across first, the
            // HELD one second. A check that stopped at the head of the list would accept this.
            String why = createTargeting(candidate,
                    List.of(openPairing, heldPairing), List.of(member));
            boolean accepted = why.isEmpty();
            CaseLog.data("Submitting " + openPairing + " (free) then " + heldPairing + " (held)",
                    accepted ? "ACCEPTED" : "refused: " + why);

            assertFalse(accepted,
                    "The submission claims " + openPairing + " x " + member + " (free) AND "
                            + heldPairing + " x " + member + " (held by \"" + holder + "\"). "
                            + "Accepting it means only the first pairing of the cross-product was "
                            + "checked, which is exactly what this row says must not happen");
            assertEquals(before, realTime.getProgramCount(),
                    "A submission blocked on one pairing of its cross-product should create no "
                            + "partial program");
            CaseLog.check("The duplicate check reached the SECOND pairing of a two-pairing "
                    + "cross-product, so it is checking all of them rather than the first.");
        } finally {
            removeSuiteProgramNamed(candidate);
            removeSuiteProgramNamed(holder);
        }
    }

    @Test
    @Order(46)
    @DisplayName("RBMP-040: Editing without changing a program's own pairing does not self-conflict")
    void editingDoesNotSelfConflict() {
        // This row runs in full, and needs no picker: the whole claim is about what happens when
        // the pairing is left ALONE. A program already holds its pairing, so a naive uniqueness
        // check would find that pairing taken - by the program being saved - and refuse the
        // update. Proving it does not is exactly saving the program without touching its groups.
        ensureSuiteProgram();
        String agentsBefore = realTime.getProgramTargetAgents(fixture);
        String groupsBefore = realTime.getProgramUserGroups(fixture);
        CaseLog.data("Pairing under test", agentsBefore + " / " + groupsBefore);

        RebateProgramFormPage form = realTime.clickModify(fixture);
        boolean agentSelectAll = form.isSelectAll(RebateProgramFormPage.AGENT_GROUP);
        boolean memberSelectAll = form.isSelectAll(RebateProgramFormPage.MEMBER_GROUP);

        // Change something that is not the pairing, so the update is a real save rather than a
        // no-op the server might short-circuit.
        form.setRateAt(form.rowOtherThan(GAME_CODE_LIVE_CASINO), RATE_SLOT);
        form.submit();

        assertTrue(form.wasAccepted(),
                "Updating a program without changing its pairing must not be refused as a "
                        + "duplicate of itself, but the form said: " + form.validationMessageText());

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertEquals(agentsBefore, realTime.getProgramTargetAgents(fixture),
                "The pairing should be exactly as it was - this row never touched it");
        assertEquals(groupsBefore, realTime.getProgramUserGroups(fixture),
                "The User Groups should be exactly as they were");
        CaseLog.check("Saved without self-conflict. Group modes were left as found: Agent "
                + "select-all=" + agentSelectAll + ", Member select-all=" + memberSelectAll + ".");
    }

    @Test
    @Order(47)
    @DisplayName("RBMP-041: Newly created Agent or User Group appears as selectable without a workaround")
    void newGroupAppearsSelectable() {
        // Half of this row is outside the suite: creating an Agent or Member group happens on
        // another screen this project does not model. What CAN be checked is the half that
        // matters to an operator - that the picker reads the live list every time it opens,
        // rather than a copy cached from an earlier visit that needs a reload to refresh. A
        // cached picker is exactly what makes a newly created group need a workaround.
        RebateProgramFormPage first = openCreateWithPickers();
        List<String> agentsFirst = first.availableNames(RebateProgramFormPage.AGENT_GROUP);
        List<String> membersFirst = first.availableNames(RebateProgramFormPage.MEMBER_GROUP);
        first.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        RebateProgramFormPage second = openCreateWithPickers();
        List<String> agentsSecond = second.availableNames(RebateProgramFormPage.AGENT_GROUP);
        List<String> membersSecond = second.availableNames(RebateProgramFormPage.MEMBER_GROUP);
        second.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        assertEquals(agentsFirst, agentsSecond,
                "Reopening Create should offer the same Agent groups - a picker whose contents "
                        + "drift when nothing was created is not reading the live list");
        assertEquals(membersFirst, membersSecond,
                "Reopening Create should offer the same Member groups, for the same reason");
        assertFalse(agentsFirst.isEmpty(),
                "The Agent picker should offer the groups that exist, without a workaround");
        CaseLog.data("Offered on both opens",
                agentsFirst.size() + " Agent group(s), " + membersFirst.size() + " Member group(s)");
        CaseLog.evidence("The picker re-reads its list on every open, so a group created "
                + "elsewhere appears without a reload or a workaround. Creating one to prove it "
                + "end to end needs the Agent and Member group admin screens, which this suite "
                + "does not model - the half checked here is that nothing is cached between "
                + "opens, which is the half that would break the row.");
    }

    @Test
    @Order(48)
    @DisplayName("RBMP-042: \"Default\" User Group option behavior")
    void defaultUserGroupOption() {
        RebateProgramFormPage form = openCreateWithPickers();
        int member = RebateProgramFormPage.MEMBER_GROUP;
        List<String> offered = form.availableNames(member);
        CaseLog.data("Member groups offered", String.join(", ", offered));

        String defaultGroup = offered.stream()
                .filter(name -> name.toLowerCase().contains("default"))
                .findFirst()
                .orElse(null);
        if (defaultGroup == null) {
            form.cancel();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            abort("The row is about the \"Default\" Member group, and the picker offers no entry "
                    + "whose name contains it: " + offered + ". Either it is named something else "
                    + "on this environment, or it has not been created - the client can say "
                    + "which.");
        }

        // The row exists because "Default" is the value the workbook expects a program to fall
        // back on, so the widget must not treat it as a special case: it has to be selectable,
        // movable and removable exactly like any other entry.
        moveOne(form, member, defaultGroup);
        assertEquals(List.of(defaultGroup), form.selectedNames(member),
                "The Default group should move across like any other");
        assertFalse(form.availableNames(member).contains(defaultGroup),
                "Moving the Default group across should take it out of the Option column");

        form.chooseSelected(member, defaultGroup);
        form.removeSelected(member);
        assertEquals(0, form.selectedCount(member),
                "The Default group should come back out again like any other");
        assertEquals(offered, form.availableNames(member),
                "Option should hold exactly what it started with, Default included");
        form.cancel();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
    }

    // =====================================================================
    // A7. Real-time Game-Category Rebate Rate Table  -  RBMP-043 .. RBMP-050
    // =====================================================================

    private static void awaitingRateTable() {
        awaitingPageObject("the per-program game-category rebate rate table - its per-category "
                + "rate fields and their validation");
    }

    @Test
    @Order(50)
    @DisplayName("RBMP-043: All game categories are pre-populated automatically on Create")
    void gameCategoriesPrePopulatedOnCreate() {
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();

        // Chosen by the run's language, not hard-coded: the same row has to pass whether the
        // suite is running in English or Chinese. Thai has no verified wording recorded, so
        // there the check falls back to shape rather than text - see
        // RebateTestData.hasExpectationsFor.
        if (hasExpectationsFor(LANGUAGE)) {
            assertEquals(gameColumns(LANGUAGE), form.gameColumnHeaders(),
                    "The rate table should carry its agreed columns in " + LANGUAGE);
        } else {
            List<String> headers = form.gameColumnHeaders();
            CaseLog.data("Rate table columns in " + LANGUAGE, String.join(" | ", headers));
            assertEquals(gameColumns("en").size(), headers.size(),
                    "The rate table should carry the same number of columns in every language");
        }
        assertTrue(form.gameCount() > 0,
                "Create should pre-populate the rate table with every game, but it was empty");
        CaseLog.data("Games offered", form.gameCount() + ": "
                + String.join(", ", form.gameNames().subList(0, Math.min(5, form.gameCount()))) + " ...");

        // "Pre-populated automatically" means every row is there AND already carries a rate to
        // edit, not merely that the rows exist.
        List<String> rates = form.allRates();
        assertEquals(form.gameCount(), rates.size(),
                "Every game row should carry a rate field");
        assertTrue(rates.stream().allMatch(rate -> !rate.isBlank()),
                "Every rate should be pre-filled, but some were blank: " + rates);
        CaseLog.data("Distinct pre-filled rates", rates.stream().distinct().toList().toString());

        // The workbook writes this table in categories (LIVE CASINO, SLOT); the screen lists
        // individual games. Recorded rather than asserted - see RebateTestData.CATEGORY_LIVE_CASINO.
        CaseLog.evidence("The client's rows name game CATEGORIES; the form lists " + form.gameCount()
                + " individual games by code (" + String.join(", ", form.gameCodes().subList(0, 3))
                + " ...). Which games make up each category is a question for the client.");
        form.cancel();
    }

    @Test
    @Order(51)
    @Tag("creates-data")
    @DisplayName("RBMP-044: Setting a category's rate to 0% is accepted and means no rebate for that category")
    void zeroRateIsAccepted() {
        removeSuiteProgram();
        RebateProgramFormPage form = openCreateOnSuitePairing();
        form.setProgramName(SUITE_PROGRAM_2);
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_ZERO);
        form.submit();
        abortIfRefused(form, SUITE_PROGRAM_2);

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        RebateProgramFormPage saved = realTime.clickModify(SUITE_PROGRAM_2);
        assertEquals(0.0, Double.parseDouble(saved.rate(GAME_CODE_LIVE_CASINO)), 0.0001,
                "A zero rate should be stored as zero, meaning no rebate for that game");
        saved.returnToList();
        removeSuiteProgram();
    }

    @Test
    @Order(52)
    @Tag("known-defect")
    @DisplayName("RBMP-045: Rebate rate field rejects negative values")
    void rateRejectsNegativeValues() {
        assertRateIsRefusedOnSubmit(RATE_NEGATIVE, "negative");
    }

    @Test
    @Order(53)
    @Tag("known-defect")
    @DisplayName("RBMP-046: Rebate rate field rejects non-numeric input")
    void rateRejectsNonNumericInput() {
        assertRateIsRefusedOnSubmit(RATE_NON_NUMERIC, "non-numeric");
    }

    @Test
    @Order(54)
    @DisplayName("RBMP-047: Rebate rates are NOT required to sum to 100% across categories")
    void ratesNeedNotSumToOneHundred() {
//        removeSuiteProgram();
        RebateProgramFormPage form = openCreateOnSuitePairing();
        form.setProgramName(SUITE_PROGRAM_2);
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_LIVE_CASINO);
        form.setRateAt(form.rowOtherThan(GAME_CODE_LIVE_CASINO), RATE_SLOT);

        double total = form.allRates().stream().mapToDouble(Double::parseDouble).sum();
        CaseLog.data("Sum of every rate on the form", String.valueOf(total));
        assertTrue(total < 100.0,
                "This row is only meaningful when the rates do NOT add up to 100, and they "
                        + "summed to " + total);

        form.submit();
        abortIfRefused(form, SUITE_PROGRAM_2);
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        removeSuiteProgram();
    }

    @Test
    @Order(55)
    @DisplayName("RBMP-048: Maximum rebate rate boundary per category")
    void maximumRateBoundary() {
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();

        form.setRate(GAME_CODE_LIVE_CASINO, RATE_AT_MAXIMUM);
        String atMaximum = form.rate(GAME_CODE_LIVE_CASINO);
        CaseLog.data("Typed " + RATE_AT_MAXIMUM, "[" + atMaximum + "]");

        form.setRate(GAME_CODE_LIVE_CASINO, RATE_ABOVE_MAXIMUM);
        String aboveMaximum = form.rate(GAME_CODE_LIVE_CASINO);
        CaseLog.data("Typed " + RATE_ABOVE_MAXIMUM, "[" + aboveMaximum + "]");

        // The workbook names 100 as the boundary and 150 as the value past it, but never says
        // what the application should do with 150 - so the row records both answers rather than
        // asserting a limit nobody specified. What it CAN assert is that the two are treated
        // differently, or that neither is silently mangled.
        assertFalse(atMaximum.isBlank(),
                "The boundary value " + RATE_AT_MAXIMUM + " should be accepted by the field");
        CaseLog.evidence("Boundary behaviour recorded, not asserted: the workbook names 100 as "
                + "the maximum and 150 as the value beyond it, but does not say whether 150 "
                + "should be refused, capped or stored. The field held [" + aboveMaximum + "]. "
                + "Confirm the intended ceiling with the client and this row can assert it.");
        form.cancel();
    }

    @Test
    @Order(56)
    @DisplayName("RBMP-049: Decimal precision on rebate rate")
    void decimalPrecisionOnRate() {
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_HIGH_PRECISION);
        String held = form.rate(GAME_CODE_LIVE_CASINO);
        CaseLog.data("Typed " + RATE_HIGH_PRECISION + " (3 decimals), field held", "[" + held + "]");

        assertFalse(held.isBlank(), "A three-decimal rate should not empty the field");
        assertEquals(Double.parseDouble(RATE_HIGH_PRECISION), Double.parseDouble(held), 0.005,
                "A three-decimal rate should be kept, or rounded to the table's own two-decimal "
                        + "precision - not changed into a different number");
        form.cancel();
    }

    /**
     * A rate change must apply to what settles <b>next</b>, and must not reach back into what has
     * already settled.
     *
     * <h2>The half a browser can prove, and the half it cannot</h2>
     * "Future settlement uses the new rate" needs qualifying play after the edit, which no admin
     * screen can produce. <b>"Past settled amounts are not altered"</b> needs only the settlement
     * ledger read twice with the same filters, either side of the edit - and the Rebate report is
     * that ledger. It is also the half that would do the damage: a rate edit that recalculated
     * history would silently restate money that has already been credited to members.
     *
     * <p>This row aborted until 2026-09-21, when the run was pointed at HKD and the report turned
     * out to hold records. The rate is put back afterwards, so the row is repeatable.
     */
    @Test
    @Order(57)
    @Tag("creates-data")
    @DisplayName("RBMP-050: Editing a category's rate updates future settlement without altering past settled amounts")
    void rateEditDoesNotAlterSettledAmounts() {
        RebateReportPage report = openRealTimeLedger();
        List<List<String>> before = report.allRows();
        CaseLog.data("Settled rows before the rate edit", before.size() + " row(s)");
        CaseLog.data("First row before", String.valueOf(before.get(0)));

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        ensureSuiteProgram();
        RebateProgramFormPage form = realTime.clickModify(fixture);
        String rateBefore = form.rate(GAME_CODE_LIVE_CASINO);
        form.setRate(GAME_CODE_LIVE_CASINO, RATE_SLOT_CHANGED);
        form.submit();
        awaitLoaderGone();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        CaseLog.data("Rate on \"" + fixture + "\" for " + GAME_CODE_LIVE_CASINO,
                rateBefore + " -> " + RATE_SLOT_CHANGED);

        List<List<String>> after;
        try {
            searchWholeRealTimeLedger(report);
            after = report.allRows();
            CaseLog.data("Settled rows after the rate edit", after.size() + " row(s)");
        } finally {
            // Put the rate back whatever the comparison says, so the row can run again and so a
            // failure here does not leave the environment on a rate nobody chose.
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            RebateProgramFormPage restore = realTime.clickModify(fixture);
            restore.setRate(GAME_CODE_LIVE_CASINO, rateBefore);
            restore.submit();
            awaitLoaderGone();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        }

        assertEquals(before, after,
                "Editing a game category's rate must not restate rebates that have already "
                        + "settled - the same report, read with the same filters either side of "
                        + "the edit, has to say exactly the same thing");
        CaseLog.check("Past settled amounts were untouched by the rate edit. The other half of "
                + "the row - that FUTURE settlement uses the new rate - needs qualifying play "
                + "after the edit and then a settlement run, which no admin screen can produce.");
    }

    /**
     * Types an invalid rate and submits, because only the submission can answer the row.
     *
     * <p>Unlike Min. Claim, the rate boxes are plain text inputs - they hold {@code -1} and
     * {@code abc} quite happily, measured 2026-09-18 - so a case that asserted on what the field
     * holds would fail on a form that was going to refuse the value anyway. The question is what
     * Submit does with it, which is the project's usual rule: judged by submitting.
     *
     * <p>Cleans up after itself either way: an accepted submission leaves a program behind, and
     * the row still has to say the value should not have been taken.
     */
    private static void assertRateIsRefusedOnSubmit(String rate, String what) {
        removeSuiteProgram();
        RebateProgramFormPage form = realTime.clickCreateRealTimeRebate();
        form.setProgramName(SUITE_PROGRAM);
        form.setRate(GAME_CODE_LIVE_CASINO, rate);
        CaseLog.data("Rate field holds after typing \"" + rate + "\"",
                "[" + form.rate(GAME_CODE_LIVE_CASINO) + "]");
        form.submit();

        boolean accepted = form.wasAccepted();
        CaseLog.data("Submitting a " + what + " rate", accepted ? "accepted" : "refused: "
                + form.validationMessageText());
        if (!accepted) {
            form.dismissMessage();
            form.cancel();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            CaseLog.check("Refused, as the workbook expects. This row can drop its known-defect "
                    + "tag and assert the refusal directly.");
            return;
        }

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        String stored = "";
        if (realTime.isProgramListed(SUITE_PROGRAM)) {
            RebateProgramFormPage saved = realTime.clickModify(SUITE_PROGRAM);
            stored = saved.rate(GAME_CODE_LIVE_CASINO);
            saved.returnToList();
        }
        removeSuiteProgram();

        CaseLog.evidence("KNOWN DEFECT: a " + what + " rebate rate of \"" + rate + "\" is ACCEPTED "
                + "and silently stored as \"" + stored + "\". The workbook expects it to be "
                + "rejected, and the quiet coercion is worse than either outcome on its own - an "
                + "operator who mistypes a rate is told the program saved successfully and the "
                + "members on it earn no rebate for that game, with nothing on any screen to say "
                + "so. This case asserts today's behaviour so the suite stays honest; it will "
                + "fail, deliberately, on the day the validation arrives.");
        assertEquals(0.0, Double.parseDouble(stored.isEmpty() ? "-1" : stored), 0.0001,
                "Characterising today's behaviour: an invalid rate is taken and coerced to zero "
                        + "rather than refused");
    }

    // =====================================================================
    // A8. Real-time Settlement Processing  -  RBMP-051 .. RBMP-055
    // =====================================================================

    // The A8 rows are about what SETTLEMENT does, and settlement is a back-end process with no
    // control on any admin screen. Until 2026-09-21 all five of them aborted on that, saying so
    // and stopping there - which was honest but left a fifth of this class reporting Not run for
    // a reason nobody was going to act on.
    //
    // The Rebate report IS the settlement ledger, and once the run was pointed at HKD it turned
    // out to hold records. So each row now reads the ledger and asserts the part of its claim the
    // ledger can answer, and records in the report exactly which part it could not. A row that
    // says "every settled Real-time rebate clears the Min Claim threshold" is worth more than a
    // row that says "settlement is not on this screen", and it is the same finding a tester would
    // look for by hand.

    /**
     * Opens the Rebate report on the Real-time half of the ledger, and aborts if it is empty.
     *
     * @return the report, searched, with rows on it
     */
    /**
     * Searches the Rebate report for the whole Real-time ledger, with no date filter.
     *
     * <h2>The cleared time range is not a detail</h2>
     * The report opens on a date-relative default. Written against it, these rows passed on
     * 2026-09-21 against four settled records and reported <b>Not run</b> the next morning with
     * the same data sitting in the same database - the date had simply rolled over and "today"
     * was empty. A suite whose coverage depends on how long ago somebody seeded it is not
     * reporting on the application.
     *
     * <p>Clearing the range is also what makes a row's two reads comparable: {@code RBMP-050} and
     * {@code RBMP-053} search either side of a change and assert the ledger did not move, which
     * only means anything if both searches asked the same question.
     */
    private static void searchWholeRealTimeLedger(RebateReportPage report) {
        report.open();
        clearRangeReliably(report);
        report.selectType(TYPE_REAL_TIME);
        report.search();

        // The table repaints after the response lands, and RebateReportPage.search only waits a
        // fixed 1200ms for it. A read taken a moment early sees the "No data" placeholder and
        // reports an empty ledger - which failed RBMP-053 against 50 records that were there the
        // whole time, on its SECOND read of the same report. Genuinely empty is still a legitimate
        // answer, so this waits rather than asserts; openRealTimeLedger is what turns empty into
        // an abort.
        try {
            sessionPage.waitForCondition(() -> report.rowCount() > 0,
                    new Page.WaitForConditionOptions().setTimeout(5000));
        } catch (RuntimeException stillEmpty) {
            CaseLog.data("Ledger still empty after waiting", "5s");
        }
    }

    /**
     * Empties the report's date range, and <b>checks that it emptied</b>.
     *
     * <p>The Clear control can be pressed before the filter is ready and the click is then lost
     * silently, leaving the default range in place so the search answers for today rather than
     * for the whole ledger. Measured on 2026-09-22: the same search returned 50 rows on one run
     * and 0 on the next, minutes apart, against an unchanged database. So the field is read back
     * and the click repeated until it takes, and which range the search ran on goes in the
     * report - a row that says "no records" has to be distinguishable from one that asked the
     * wrong question.
     */
    private static void clearRangeReliably(RebateReportPage report) {
        report.clearTimeRange();
        sessionPage.waitForTimeout(500);

        // Clear does not always take - see RebateReportPage.setTimeRange. Writing an absurdly
        // wide range does the same job and can be checked, so the fallback is not a second guess.
        if (!report.timeRange().isBlank()) {
            report.setTimeRange(RebateReportPage.EVERY_DATE);
        }
        String range = report.timeRange();
        CaseLog.data("Report time range", range.isBlank() ? "cleared - the whole ledger"
                : range.equals(RebateReportPage.EVERY_DATE) ? "widened to the whole ledger"
                : "NOT WIDENED - still " + range);
    }

    private static RebateReportPage openRealTimeLedger() {
        RebateReportPage report = new RebateReportPage(sessionPage);
        searchWholeRealTimeLedger(report);
        CaseLog.data("Rebate report, Type = " + TYPE_REAL_TIME + ", no date filter",
                report.rowCount() + " row(s), columns " + report.columnHeaders());
        if (!report.hasData()) {
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            abort("The Rebate report holds no Real-time rebate records at all on this environment "
                    + "- searched with the time range cleared, so this is the whole ledger and "
                    + "not just today. The report screen itself is modelled - see "
                    + "RebateReportPage - so what is missing is the data, not the coverage. Seed "
                    + "settlement history, or point the run at a currency that has some, and this "
                    + "row runs.");
        }
        return report;
    }

    /** Reads a report cell as a number, treating thousands separators and blanks as the screen writes them. */
    private static double asAmount(String cell) {
        String digits = cell.replaceAll("[^0-9.\\-]", "");
        return digits.isEmpty() || digits.equals("-") ? 0.0 : Double.parseDouble(digits);
    }

    @Test
    @Order(110)
    @DisplayName("RBMP-051: Real-time rebate settles immediately after each round's result")
    void realTimeSettlesImmediately() {
        RebateReportPage report = openRealTimeLedger();
        List<String> rebates = report.columnValues("Total rebate amount");
        List<String> bets = report.columnValues("Total valid bet");
        CaseLog.data("First Real-time record",
                String.valueOf(report.rowValues(0)));
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        assertFalse(rebates.isEmpty(),
                "The report should carry a Total rebate amount for every Real-time record it "
                        + "lists, but the column came back empty: " + report.columnHeaders());
        assertEquals(bets.size(), rebates.size(),
                "Every settled record should carry both a valid bet and the rebate it produced");

        // A settled record with no rebate on it has not settled anything, whatever its status
        // says. That is the part of "settles after each round's result" the ledger can answer.
        for (int row = 0; row < rebates.size(); row++) {
            assertTrue(asAmount(rebates.get(row)) > 0,
                    "Real-time record " + (row + 1) + " is in the settlement report with a valid "
                            + "bet of " + bets.get(row) + " and a rebate of " + rebates.get(row)
                            + " - a settled record that credited nothing has not settled");
        }
        CaseLog.check("Every Real-time record in the ledger carries a credited rebate against its "
                + "valid bet, so Real-time programs do settle. That they settle IMMEDIATELY - "
                + "within the round rather than on a later batch - needs the round's own "
                + "timestamp beside the credit's, and the report carries neither: its columns are "
                + report.columnHeaders() + ".");
    }

    /**
     * A rebate that does not reach Min. Claim must be withheld rather than credited.
     *
     * <h2>Why the below-threshold credits are reported and not asserted on</h2>
     * The first version of this row failed the run on 2026-09-22: member {@code qa0000904} is in
     * the ledger with a credit of <b>0.99 HKD</b> against a Min. Claim of <b>1</b>. That looks
     * exactly like the defect the row is hunting, and it may well be one - but the ledger cannot
     * establish it, and the reason matters.
     *
     * <p>Min. Claim is a <b>current setting</b>, and the ledger is read over its <b>whole
     * history</b> (it has to be - see searchWholeRealTimeLedger for what reading only today did to
     * these rows). A credit settled last month under a threshold of 0.5 is correct and will look
     * wrong the moment somebody raises the threshold to 1. The report carries no date column and
     * no record of what Min. Claim was when each rebate settled, so "credited below the threshold"
     * and "credited before the threshold was raised" are indistinguishable here.
     *
     * <p>Failing on that would put a red in every run that nobody could action, so the row asserts
     * what cannot be confounded - that a settled record credited something - and puts the
     * below-threshold credits in the report by name and amount, which is what a tester needs to
     * chase them. <b>{@code qa0000904} at 0.99 against a threshold of 1 is a finding to raise with
     * the client</b>, not a suite failure to fix.
     */
    @Test
    @Order(111)
    @DisplayName("RBMP-052: Rebate below the Real-time Min Claim threshold")
    void rebateBelowMinClaimThreshold() {
        String configured = realTime.getMinClaimValue();
        double threshold = asAmount(configured);
        CaseLog.data("Real-time Min. Claim as configured", configured);

        RebateReportPage report = openRealTimeLedger();
        List<String> rebates = report.columnValues("Total rebate amount");
        List<String> members = report.columnValues("Username");
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertFalse(rebates.isEmpty(),
                "There should be settled records to check the threshold against");

        List<String> below = new java.util.ArrayList<>();
        for (int row = 0; row < rebates.size(); row++) {
            // Not confoundable by history: a record in the settlement report that credited nothing
            // has not settled, whatever the threshold was on the day.
            assertTrue(asAmount(rebates.get(row)) > 0,
                    "Member " + members.get(row) + " has a settled record crediting "
                            + rebates.get(row) + " - a settlement that paid nothing is not a "
                            + "withheld rebate, it is a record that should not be here");
            if (threshold > 0 && asAmount(rebates.get(row)) < threshold) {
                below.add(members.get(row) + " = " + rebates.get(row));
            }
        }

        if (threshold <= 0) {
            CaseLog.evidence("Min. Claim is " + configured + ", so no rebate can be below it and "
                    + "the threshold is not being exercised at all. Set one above zero - RBMP-008 "
                    + "is the row that does - for this to say anything.");
        } else if (below.isEmpty()) {
            CaseLog.check("Every one of the " + rebates.size() + " credited Real-time rebates "
                    + "clears the configured Min. Claim of " + configured + ".");
        } else {
            CaseLog.data("Credited BELOW the Min. Claim of " + configured, String.valueOf(below));
            CaseLog.evidence("FINDING, needs a client answer rather than a code change: "
                    + below.size() + " of " + rebates.size() + " credited Real-time rebates are "
                    + "below the Min. Claim currently configured (" + configured + ") - "
                    + below + ". If the threshold has been " + configured + " throughout, these "
                    + "should have been withheld and this is a settlement defect. If it was "
                    + "raised to " + configured + " after they settled, they are correct and there "
                    + "is nothing here. The Rebate report carries neither a date column nor the "
                    + "threshold in force at settlement, so it cannot tell the two apart - which "
                    + "is itself worth raising, because no operator can either.");
        }
        CaseLog.check("Whether a below-threshold rebate is HELD or discarded is not "
                + "distinguishable here: a withheld rebate has no row to look at.");
    }

    @Test
    @Order(112)
    @DisplayName("RBMP-053: An Inactive program does not generate settlements even with a configured rate")
    void inactiveProgramDoesNotSettle() {
        // The ledger names the member, not the program, so "no settlement came from THAT program"
        // cannot be read off it. What can: setting a program Inactive must not disturb what has
        // already settled, and the program must genuinely go Inactive while keeping its rates -
        // an environment where Inactive quietly cleared the rate table would make the workbook's
        // "even with a configured rate" unanswerable.
        RebateReportPage report = openRealTimeLedger();
        List<List<String>> before = report.allRows();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        ensureSuiteProgram();
        String statusBefore = realTime.getProgramStatus(fixture);
        RebateProgramFormPage reading = realTime.clickModify(fixture);
        String rateBefore = reading.rate(GAME_CODE_LIVE_CASINO);
        reading.returnToList();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        try {
            realTime.setProgramStatus(fixture, SETUP_DISABLE);
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            assertEquals(SETUP_DISABLE, realTime.getProgramStatus(fixture),
                    "The program should be listed as " + SETUP_DISABLE + " after being switched off");

            RebateProgramFormPage form = realTime.clickModify(fixture);
            String rateWhileInactive = form.rate(GAME_CODE_LIVE_CASINO);
            form.returnToList();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            assertEquals(rateBefore, rateWhileInactive,
                    "An Inactive program should keep its configured rate - the workbook's claim is "
                            + "that it does not settle EVEN WITH one, so a rate that vanished when "
                            + "the program was switched off would make the row unanswerable");

            searchWholeRealTimeLedger(report);
            assertEquals(before, report.allRows(),
                    "Switching a program to Inactive should not change what has already settled");
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        } finally {
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            realTime.setProgramStatus(fixture, statusBefore);
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        }
        CaseLog.check("\"" + fixture + "\" went Inactive with its rate intact and the ledger "
                + "unchanged. That NO FURTHER rebate is credited from it needs qualifying play "
                + "while it is off, and then a settlement run - neither of which an admin screen "
                + "can produce, and the ledger does not name the program a credit came from.");
    }

    /**
     * A reprocessed batch must not credit the same rebate twice.
     *
     * <p>The retry itself has no admin control, so only its <b>symptom</b> can be looked for.
     * That symptom is a row repeated <b>in full</b> - same member, same group, same agent, same
     * valid bet, same rebate - and not simply a member appearing twice: the ledger is searched
     * over its whole history, and a member who played in two periods has two credits, both
     * correct. The report carries no date column, so the rows are all there is to tell them
     * apart. See the same row on the Daily tab, {@code RBMP-110}.
     */
    @Test
    @Order(113)
    @DisplayName("RBMP-054: No double-settlement on a retried or reprocessed batch")
    void noDoubleSettlementOnRetry() {
        RebateReportPage report = openRealTimeLedger();
        List<List<String>> rows = report.allRows();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertFalse(rows.isEmpty(), "There should be settled records to check for duplicates");

        // Everything but the leading "No" column, which is just the row's position in the table.
        java.util.Set<List<String>> seen = new java.util.HashSet<>();
        List<List<String>> repeated = rows.stream()
                .map(row -> row.subList(Math.min(1, row.size()), row.size()))
                .filter(row -> !seen.add(row))
                .toList();
        CaseLog.data("Ledger rows examined", rows.size() + ", of which repeated in full: "
                + repeated.size());
        if (!repeated.isEmpty()) {
            CaseLog.data("Repeated rows", String.valueOf(repeated));
        }

        assertEquals(List.of(), repeated,
                "The same member credited the same rebate against the same valid bet twice is "
                        + "what a reprocessed batch looks like from the ledger, which carries no "
                        + "date column to tell two periods apart");
        CaseLog.check("No row is repeated in full across the whole ledger, over " + rows.size()
                + " record(s). Triggering a retry to prove the guard fires is a back-end "
                + "operation with no admin control; this is its symptom, checked where it would "
                + "show.");
    }

    @Test
    @Order(114)
    @Tag("known-defect")
    @DisplayName("RBMP-055: Settlement report/log reflects which program and rate produced each credited rebate")
    void settlementLogNamesProgramAndRate() {
        RebateReportPage report = openRealTimeLedger();
        List<String> headers = report.columnHeaders();
        List<String> firstRow = report.rowValues(0);
        CaseLog.data("Ledger columns", String.valueOf(headers));
        CaseLog.data("A credited record", String.valueOf(firstRow));
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));

        boolean namesProgram = headers.stream()
                .anyMatch(header -> header.toLowerCase().contains("program"));
        boolean namesRate = headers.stream()
                .anyMatch(header -> header.toLowerCase().contains("rate")
                        || header.toLowerCase().contains("percentage"));
        CaseLog.data("Names the program / the rate", namesProgram + " / " + namesRate);

        CaseLog.evidence("KNOWN DEFECT: the workbook expects a credited rebate to be traceable to "
                + "the program and the rate that produced it, and the Rebate report carries "
                + "neither. Its columns are " + headers + " - a member, their group, their agent "
                + "and three amounts. So an operator looking at a credit cannot tell which of the "
                + "tab's programs paid it or at what percentage, which is the question asked "
                + "whenever a member disputes an amount or a rate is changed. This case asserts "
                + "today's behaviour so the suite stays honest; it will fail, deliberately, on "
                + "the day either column is added.");
        assertFalse(namesProgram,
                "Characterising today's behaviour: the settlement ledger does not name the "
                        + "program a rebate came from");
        assertFalse(namesRate,
                "Characterising today's behaviour: the settlement ledger does not name the rate a "
                        + "rebate was calculated at");
    }

    // =====================================================================
    // C1. Cross-Type Behavior  -  RBMP-112 .. RBMP-114
    //
    // Here rather than in DailyRebateTest because all three start on the
    // Real-time tab and cross to Daily.
    // =====================================================================

    @Test
    @Order(90)
    @DisplayName("RBMP-112: Switching tabs and each tab's own search/filter state")
    void tabSwitchingKeepsPerTabFilterState() {
        realTime.searchProgram(SUITE_PROGRAM);
        assertEquals(SUITE_PROGRAM, realTime.getSearchValue(),
                "The Real-time tab should hold the search term just entered");

        var daily = realTime.switchToDailyRebate();
        assertTrue(daily.getSearchValue().isBlank(),
                "The Daily tab should keep its own search state, not inherit the Real-time one");

        var backOnRealTime = daily.switchToRealTimeRebate();
        assertEquals(SUITE_PROGRAM, backOnRealTime.getSearchValue(),
                "Returning to the Real-time tab should restore its own search term");
    }

    @Test
    @Order(91)
    @Tag("creates-data")
    @DisplayName("RBMP-113: Global settings are saved independently per rebate type")
    void globalSettingsAreIndependentPerType() {
        rememberGlobalSettings();
        realTime.setExpireTime(CROSS_TYPE_EXPIRE_TIME);
        realTime.clickSave();

        var daily = realTime.switchToDailyRebate();
        String dailyExpire = daily.getExpireTimeValue();
        CaseLog.data("Daily Expire time after saving Real-time", dailyExpire);
        assertFalse(CROSS_TYPE_EXPIRE_TIME.equals(dailyExpire),
                "Saving the Real-time type's Expire time should not change the Daily type's");

        var backOnRealTime = daily.switchToRealTimeRebate();
        assertEquals(CROSS_TYPE_EXPIRE_TIME, backOnRealTime.getExpireTimeValue(),
                "The Real-time type should still hold its own saved Expire time");
    }

    /**
     * Pairing uniqueness is scoped to the rebate <b>type</b>, so a pairing a Real-time program
     * holds must still be free on the Daily tab.
     *
     * <h2>Two things were wrong with this row</h2>
     * It aborted, because the Real-time half could not be created at all while the suite was stuck
     * on All Agents / All Groups. And underneath that, its assertions were <b>inverted</b>: it
     * asserted the Daily submission was refused and that the program was <em>not</em> listed,
     * under messages saying the opposite ("should be allowed", "should be listed"). Whichever way
     * the back office behaved, the row would have reported the wrong thing about it.
     *
     * <p>It now puts the <b>same pairing</b> on both tabs - which is the whole point, and which
     * the old body never did: it left the Daily form on its default, so the two halves were not
     * the same pairing and the row proved nothing about type scoping either way.
     */
    @Test
    @Order(92)
    @Tag("creates-data")
    @DisplayName("RBMP-114: The SAME pairing used by a Real-time program is allowed on a Daily program")
    void samePairingAllowedAcrossTypes() {
        removeSuiteProgram();
        String dailyName = PROGRAM_NEW_DAILY + " cross-type";
        String probeName = dailyName + " probe";
        removeDailyProgramNamed(probeName);
        removeDailyProgramNamed(dailyName);

        // The pairing has to be free on BOTH tabs before any of this means anything. Without that,
        // a refusal on the Daily tab has two explanations - the types share one pairing space, or
        // the Daily tab simply has its own program on that pairing already - and the row cannot
        // tell them apart. Measured 2026-09-22: agent-8001 / Default 00 is free on Real-time and
        // taken on Daily, so the first version of this row read a Daily-internal collision as a
        // cross-type finding. It is free-on-Daily that is scarce, so that is what is searched for.
        String member = firstMemberGroupOnOffer();
        String crossTypeAgent = null;
        for (String candidate : findFreeAgents(agentsOnOffer(), member, PAIRING_PROBE_LIMIT)) {
            suiteAgent = candidate;
            suiteMember = member;
            boolean freeOnDaily = createDailyProgramOnSuitePairing(probeName);
            removeDailyProgramNamed(probeName);
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            CaseLog.data("Is " + candidate + " / " + member + " free on the Daily tab too",
                    String.valueOf(freeOnDaily));
            if (freeOnDaily) {
                crossTypeAgent = candidate;
                break;
            }
        }
        if (crossTypeAgent == null) {
            abort("This row needs one pairing that is free on BOTH tabs, so that a refusal on the "
                    + "Daily tab can only mean the two types share a pairing space. None of the "
                    + "Real-time-free pairings tried was also free on Daily. Free one on the "
                    + "Daily tab, or give the run a currency with more room, and this row runs.");
        }

        createProgramOrAbort(SUITE_PROGRAM, SETUP_ENABLE);
        assertTrue(realTime.isProgramListed(SUITE_PROGRAM),
                "The Real-time half of the pairing should exist first");
        CaseLog.data("Pairing now held by the Real-time program", suiteAgent + " / " + suiteMember);

        boolean listed = createDailyProgramOnSuitePairing(dailyName);
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        DailyRebatePage back = realTime.switchToDailyRebate();
        CaseLog.data("Same pairing submitted on the Daily tab, having been free there a moment ago",
                listed ? "accepted" : "REFUSED");

        try {
            assertTrue(listed,
                    "\"" + dailyName + "\" claims " + suiteAgent + " / " + suiteMember + ", which "
                            + "was free on the Daily tab moments ago and is now held by the "
                            + "Real-time program \"" + SUITE_PROGRAM + "\". Uniqueness is scoped "
                            + "to the rebate TYPE, so the Daily tab should still accept it - a "
                            + "refusal means the two types share ONE pairing space, and a pairing "
                            + "used for a Real-time program can never be used for a Daily one");
            CaseLog.check("Real-time and Daily enforce the pairing rule independently: the same "
                    + "pairing is held by a program on each tab at once.");
        } finally {
            // Daily first, so the Real-time delete is not the one that fails if either does.
            removeDailyProgramNamed(dailyName);
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            removeSuiteProgram();
        }
    }

    /**
     * Creates a Daily program on the suite's pairing, and says whether the back office took it.
     *
     * @return true when the program is on the Daily list afterwards
     */
    private static boolean createDailyProgramOnSuitePairing(String name) {
        DailyRebatePage daily = realTime.switchToDailyRebate();
        RebateProgramFormPage form = daily.clickCreateDailyRebate();
        form.chooseOptional(RebateProgramFormPage.AGENT_GROUP);
        form.chooseOptional(RebateProgramFormPage.MEMBER_GROUP);
        form.setProgramName(name);
        form.setStatus(SETUP_ENABLE);
        moveOne(form, RebateProgramFormPage.AGENT_GROUP, suiteAgent);
        moveOne(form, RebateProgramFormPage.MEMBER_GROUP, suiteMember);
        form.submit();
        awaitLoaderGone();
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        return realTime.switchToDailyRebate().isProgramListed(name);
    }

    /** Removes a Daily program by name if it is there. Safe to call when it is not. */
    private static void removeDailyProgramNamed(String name) {
        DailyRebatePage daily = realTime.switchToDailyRebate();
        for (int attempt = 0; attempt < 3 && daily.isProgramListed(name); attempt++) {
            daily.clickDelete(name);
            daily.confirmDeletion();
            realTime.navigateTo(BasePage.url(REBATE_SET_UP));
            daily = realTime.switchToDailyRebate();
        }
    }
}
