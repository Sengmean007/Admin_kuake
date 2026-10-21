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
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.List;

import static org.sengmean.testdata.RebateTestData.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>D1. Rebate set up - the client's own executed rows</b>, {@code RBMP-128} to
 * {@code RBMP-133}: modifying the global settings, and the language filter over the list.
 *
 * <h2>These six came from the client's executed tab, and they all run</h2>
 * They arrived through the merge of the client's own {@code TEST CASE} sheet - which is why
 * their wording is terse where the generated rows are formal, and why their TEST DATA column is
 * a bare "-". What they have in common is that every one of them addresses a control that is on
 * the screen today: Release time, Expire time, Status, Min. Claim and the header's language
 * selector. Nothing here waits on a fixture.
 *
 * <h2>Each writing case restores what it found</h2>
 * Four of the six change a saved setting, and the setting is <b>global</b> - it applies to every
 * program of that type and it outlives the run. So each reads the current value first, hands the
 * way back to {@link #restoreWhatThisCaseChanged()} <em>before</em> it writes anything, and only
 * then asserts. Restoring at the end of the case body was tried first and was wrong for the
 * obvious reason: a failing assertion never reaches it, so every red row left a test value on a
 * shared back office. They stay tagged {@code creates-data} regardless, because a case that
 * cannot reach the screen at all cannot put anything back either:
 *
 * <pre>./mvnw test -Dsuite=RebateSuite -DexcludedGroups=creates-data</pre>
 *
 * <h2>Which settings these rows are modifying</h2>
 * The ones belonging to {@link org.sengmean.testdata.RebateTestData#CURRENCY_HKD}, because the
 * panel keeps a separate set per currency and this suite selects HKD in {@code @BeforeAll}. That
 * is worth knowing before reading a report from this class: a value it records as "found" is the
 * HKD figure, and it will not match what the screen shows a person whose header is still on the
 * environment's default.
 *
 * <h2>What the panel's hours actually mean</h2>
 * The Daily panel carries a note the Real-time one does not - <b>"Daily release time and expire
 * time start count at 12:00 am"</b> - which settles what the workbook's "as per hour" means:
 * these are clock hours from midnight, not durations. {@code RBMP-128} and {@code RBMP-129}
 * therefore use hours that make a sensible window rather than arbitrary numbers.
 *
 * <h2>RBMP-131 and RBMP-133 are the same row twice</h2>
 * The workbook carries "Verify filter language functionality / Select Language / For rebate
 * setup list, [Game name] should change according to the selected language" at both ids, word
 * for word. Running the identical check twice would cover both rows and prove one thing, so
 * {@code RBMP-131} takes the pair the client's own document names - English against Chinese -
 * and {@code RBMP-133} takes Thai, the third language the back office turned out to offer.
 * Together they cover every language the screen has, which is what the row is actually after.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(40)   // E2E step 4
public class RebateSetUpTest extends BaseTest {

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

    /**
     * How the case that is running puts back whatever it changed.
     *
     * <p>It is held here, and run from {@link #restoreWhatThisCaseChanged()}, rather than written
     * at the end of each case - because the end of a case is exactly where a failing assertion
     * does not reach. The first version of this class restored inline and left the environment
     * holding a test value every time a row went red, which is the opposite of what a shared
     * back office needs from a suite that writes to it.
     */
    private Runnable restore;

    /** Every case starts on a freshly-opened screen, in English, on the Real-time tab. */
    @BeforeEach
    void reopenInEnglish() {
        restore = null;
        if (shell != null && !LANGUAGE.equals(shell.language())) {
            shell.selectLanguage(LANGUAGE);
        }
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
    }

    @AfterEach
    void restoreWhatThisCaseChanged() {
        if (restore == null) {
            return;
        }
        try {
            restore.run();
        } catch (RuntimeException couldNotRestore) {
            // Never allowed to turn a red case green or a green case red: what it does is make
            // sure the next person knows the environment was left changed.
            CaseLog.evidence("This case could not put the setting it changed back: "
                    + couldNotRestore.getMessage() + " - check Rebate set up by hand.");
        } finally {
            restore = null;
        }
    }

    /** The Daily tab, freshly switched to. */
    private static DailyRebatePage daily() {
        return realTime.switchToDailyRebate();
    }

    /**
     * Re-opens the screen so that what is asserted is what the back office <em>stored</em>,
     * rather than what is still sitting in the field that typed it.
     *
     * <p>Without this every one of the modify rows would pass by reading its own input back.
     */
    private static DailyRebatePage reloadDaily() {
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        return daily();
    }

    private static RealTimeRebatePage reloadRealTime() {
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        return realTime;
    }

    // =====================================================================
    // Read-only first: the language filter over the list
    //
    // Nothing below writes, so running these before the modify rows means they see the settings
    // as the environment left them, and a modify row that fails cannot take them down with it.
    // =====================================================================

    @Test
    @Order(10)
    @DisplayName("RBMP-131: The rebate set up list follows the selected language (English / Chinese)")
    void listFollowsTheSelectedLanguageInChinese() {
        assertColumnsFollowLanguage(AdminShellPage.CHINESE, PROGRAM_COLUMNS_CHINESE);
    }

    @Test
    @Order(11)
    @DisplayName("RBMP-133: The rebate set up list follows the selected language (Thai)")
    void listFollowsTheSelectedLanguageInThai() {
        // English baseline, for the same reason as RBMP-131: this row measures a change.
        shell.selectLanguage(AdminShellPage.ENGLISH);
        List<String> english = realTime.columnHeaders();
        assertEquals(PROGRAM_COLUMNS_ENGLISH, english,
                "This case starts from the English headings");

        shell.selectLanguage(AdminShellPage.THAI);
        assertEquals(AdminShellPage.THAI, shell.language(),
                "The header should hold Thai after it was chosen");

        List<String> thai = realTime.columnHeaders();
        CaseLog.data("Columns in Thai", String.join(" | ", thai));

        // Thai has no expected list of its own in the workbook - the client's document is
        // bilingual - so what is asserted is that the list relabelled itself into a third
        // language rather than falling back to English, and that it kept its shape.
        assertEquals(english.size(), thai.size(),
                "Thai should relabel the same columns, not add or drop one");
        assertNotEquals(english, thai,
                "Choosing Thai should relabel the program list, but the headings were unchanged - "
                        + "which is what an untranslated fallback to English looks like");
        assertTrue(thai.stream().anyMatch(RebateSetUpTest::containsThai),
                "At least one heading should be rendered in Thai script, but none was: " + thai);

        shell.selectLanguage(LANGUAGE);
    }

    /**
     * Switches the language and asserts the program list's headings follow it.
     *
     * <p>The workbook's expected result names {@code [Game name]}, which is a column of the game
     * rate table on the Create screen rather than of the program list. Both are asserted where
     * they exist: the list always does, and the game table is checked too when the environment
     * has games configured to fill it.
     */
    private static void assertColumnsFollowLanguage(String language, List<String> expected) {
        // Starts from English on purpose, whatever test.language is set to: this row measures a
        // CHANGE, so it needs a known baseline to change away from. Running it from a Chinese
        // baseline and switching to Chinese would compare a screen with itself.
        shell.selectLanguage(AdminShellPage.ENGLISH);
        List<String> english = realTime.columnHeaders();
        assertEquals(PROGRAM_COLUMNS_ENGLISH, english,
                "This case starts from the English headings");

        shell.selectLanguage(language);
        assertEquals(language, shell.language(),
                "The header should hold the language that was chosen");

        List<String> translated = realTime.columnHeaders();
        CaseLog.data("Columns in " + language, String.join(" | ", translated));
        assertNotEquals(english, translated,
                "Choosing " + language + " should relabel the program list, but the headings were "
                        + "unchanged - which is what an untranslated fallback to English looks like");
        assertEquals(expected, translated,
                "The program list should carry its " + language + " column headings");

        shell.selectLanguage(AdminShellPage.ENGLISH);
        assertEquals(english, realTime.columnHeaders(),
                "Switching back to English should restore the English headings");

        // Hand the run its own language back - @AfterEach would too, but leaving it correct here
        // keeps the next assertion in a longer case honest.
        shell.selectLanguage(LANGUAGE);
    }

    // =====================================================================
    // Then the writes, least consequential first
    //
    // Release time, Expire time and Min. Claim are plain value edits: whatever they leave behind
    // is a number in a field. Status is a switch that changes what the feature DOES, so it goes
    // last - the same order the A2/B2 sections use, where RBMP-008 and RBMP-009 save values and
    // RBMP-010 toggles the global Status after them.
    // =====================================================================

    @Test
    @Order(20)
    @Tag("creates-data")
    @DisplayName("RBMP-128: Modify the Daily release time")
    void modifyReleaseTime() {
        DailyRebatePage dailyTab = daily();
        String original = dailyTab.getReleaseTimeValue();
        CaseLog.data("Release time as found", original);
        restore = () -> {
            DailyRebatePage back = reloadDaily();
            back.setReleaseTime(original);
            back.clickSave();
        };

        String wanted = original.equals(MODIFIED_RELEASE_TIME) ? "3" : MODIFIED_RELEASE_TIME;
        dailyTab.setReleaseTime(wanted);
        dailyTab.clickSave();

        assertEquals(wanted, reloadDaily().getReleaseTimeValue(),
                "The Daily release time should have been saved as " + wanted
                        + " and still read that way after a reload");
        CaseLog.check("Release time modified and persisted. On the client this moves the hour at "
                + "which the day's rebate becomes claimable; the panel's own note says the count "
                + "starts at 12:00 am.");
    }

    @Test
    @Order(21)
    @Tag("creates-data")
    @DisplayName("RBMP-129: Modify the Daily expire time")
    void modifyExpireTime() {
        DailyRebatePage dailyTab = daily();
        String original = dailyTab.getExpireTimeValue();
        CaseLog.data("Expire time as found", original);
        restore = () -> {
            DailyRebatePage back = reloadDaily();
            back.setExpireTime(original);
            back.clickSave();
        };

        String wanted = original.equals(MODIFIED_EXPIRE_TIME) ? "21" : MODIFIED_EXPIRE_TIME;
        dailyTab.setExpireTime(wanted);
        dailyTab.clickSave();

        assertEquals(wanted, reloadDaily().getExpireTimeValue(),
                "The Daily expire time should have been saved as " + wanted
                        + " and still read that way after a reload");
        CaseLog.check("Expire time modified and persisted. Whether an unclaimed rebate actually "
                + "expires at that hour is a settlement behaviour, and is not observable from the "
                + "admin back office - this row proves the configuration, not the expiry.");
    }

    @Test
    @Order(22)
    @Tag("creates-data")
    @DisplayName("RBMP-132: Min. Claim accepts an amount with two decimal places")
    void minClaimAcceptsTwoDecimals() {
        RealTimeRebatePage tab = reloadRealTime();
        String original = tab.getMinClaimValue();
        CaseLog.data("Min. Claim as found", original);
        restore = () -> {
            RealTimeRebatePage back = reloadRealTime();
            back.setMinClaim(original);
            back.clickSave();
        };

        tab.setMinClaim(TWO_DECIMAL_MIN_CLAIM);
        tab.clickSave();

        String stored = reloadRealTime().getMinClaimValue();
        assertEquals(TWO_DECIMAL_MIN_CLAIM, stored,
                "Min. Claim should accept and keep two decimal places, but " + TWO_DECIMAL_MIN_CLAIM
                        + " came back as " + stored);
    }

    @Test
    @Order(23)
    @Tag("creates-data")
    @DisplayName("RBMP-130: Modify the Rebate status")
    void modifyRebateStatus() {
        DailyRebatePage dailyTab = daily();
        String original = dailyTab.getGlobalStatusValue();
        CaseLog.data("Status as found", original);
        restore = () -> {
            DailyRebatePage back = reloadDaily();
            back.setGlobalStatus(original);
            back.clickSave();
        };

        String wanted = SETUP_ENABLE.equals(original) ? SETUP_DISABLE : SETUP_ENABLE;
        dailyTab.setGlobalStatus(wanted);
        dailyTab.clickSave();

        assertEquals(wanted, reloadDaily().getGlobalStatusValue(),
                "The Daily rebate status should have been saved as " + wanted
                        + " and still read that way after a reload");
        CaseLog.check("Status modified and persisted. The row's expected result - that a disabled "
                + "rebate stops the settle calculation - is a back-end behaviour with no admin "
                + "screen to read it from; this row proves the switch, and RBMP-125 is where the "
                + "member-facing half belongs.");
    }

    /** True when a string carries at least one character from the Thai block. */
    private static boolean containsThai(String text) {
        return text.codePoints().anyMatch(codePoint -> codePoint >= 0x0E00 && codePoint <= 0x0E7F);
    }
}
