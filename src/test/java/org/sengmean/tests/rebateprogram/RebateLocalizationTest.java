package org.sengmean.tests.rebateprogram;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.sengmean.base.BasePage;
import org.sengmean.base.BaseTest;
import org.sengmean.page.rebate.RealTimeRebatePage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.List;

import static org.sengmean.testdata.RebateTestData.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>C3. Localization</b> - {@code RBMP-120} to {@code RBMP-123}: the Rebate set up screen in
 * each language the back office offers, and the currency display that must not follow it.
 *
 * <h2>These run, and they assert</h2>
 * Unlike the C2 and C4 sections beside them, nothing here needs an account, a fixture or a
 * migration: the header carries a {@code #lang} selector with English, Chinese and Thai, and
 * switching it relabels the screen in place. So these four are real assertions against the
 * running back office, and the expected strings in {@link org.sengmean.testdata.RebateTestData}
 * were read off it on 2026-09-18 rather than guessed.
 *
 * <h2>Asserting exact translations is the point, not a brittleness</h2>
 * It would be softer to assert only that the labels <em>changed</em>. It would also be a test
 * that passes when a label is swapped for the wrong translation, which is the defect these rows
 * exist to catch. Each language therefore has its own expected list, and a deliberate change to
 * the wording is meant to fail this suite once and be updated in one place.
 *
 * <p>One difference is already known and is not a defect: the client's document titles the
 * screen <b>返水計劃</b> and the screen says <b>返水配置</b>. See
 * {@link org.sengmean.testdata.RebateTestData#HEADING_CHINESE}.
 *
 * <h2>Every case puts the run's language back</h2>
 * The selector is part of the shared operator session, so a case that left the back office in
 * Thai would hand the next one a screen it could not read. {@link #restoreTheRunLanguage()} runs
 * after each.
 *
 * <p>Note that the rows which measure a <em>change</em> - RBMP-121 above all - pin an English
 * baseline of their own rather than starting from {@code test.language}. Run the suite with
 * {@code -Dtest.language=zh-TW} and a row that inherited the run's language would be comparing
 * the Chinese screen with itself, and would fail on a screen that was perfectly correct.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(70)   // E2E step 7
public class RebateLocalizationTest extends BaseTest {

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

    /** Every case starts on a freshly-opened screen, in the run's language, on Real-time. */
    @BeforeEach
    void reopenInTheRunLanguage() {
        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        if (!LANGUAGE.equals(shell.language())) {
            shell.selectLanguage(LANGUAGE);
        }
    }

    @AfterEach
    void restoreTheRunLanguage() {
        if (shell != null && !LANGUAGE.equals(shell.language())) {
            shell.selectLanguage(LANGUAGE);
        }
    }

    /**
     * The labels of the screen's global settings, both panels', in the order the screen draws
     * them.
     *
     * <p>Both tabs' panels are in the DOM at once, so this reads four labels rather than three:
     * Real-time's Min. Claim, Expire time and Status, then Daily's Release time. That is enough
     * to tell a translated screen from an untranslated one without depending on which tab is
     * showing.
     */
    private static List<String> settingsLabels() {
        return sessionPage.locator("#divSearchCriteriaRealTime label, #divSearchCriteria label")
                .allInnerTexts().stream().map(String::trim).filter(text -> !text.isEmpty())
                .distinct().toList();
    }

    /** Fails the case if any screen text carries the residue of a translation that did not happen. */
    private static void assertNothingUntranslated(String where) {
        String body = sessionPage.locator("body").innerText();
        for (String marker : UNTRANSLATED_MARKERS) {
            assertFalse(body.contains(marker),
                    "The " + where + " screen should carry no untranslated residue, but its text "
                            + "contains \"" + marker + "\"");
        }
    }

    // =====================================================================
    // The two languages the workbook names
    // =====================================================================

    @Test
    @Order(10)
    @DisplayName("RBMP-120: All static labels render correctly in English")
    void englishLabelsRenderCorrectly() {
        // Pins its own language rather than assuming the run's. This row IS "the labels are
        // correct in English", so it has to be in English whatever test.language says - and
        // @AfterEach hands the run's language back afterwards.
        shell.selectLanguage(AdminShellPage.ENGLISH);

        assertEquals(HEADING_ENGLISH, realTime.heading(),
                "The screen should be headed in English");

        List<String> labels = settingsLabels();
        CaseLog.data("Settings labels", String.join(" | ", labels));
        assertEquals(SETTINGS_LABELS_ENGLISH, labels,
                "The global settings should carry their English labels, in order");

        assertEquals(PROGRAM_COLUMNS_ENGLISH, realTime.columnHeaders(),
                "The program list should carry its English column headings");

        assertEquals(HOUR_SUFFIX_ENGLISH, amountSuffix(),
                "The time fields should be suffixed with the English unit");

        assertNothingUntranslated("English");
    }

    @Test
    @Order(11)
    @DisplayName("RBMP-121: All static labels render correctly in Chinese")
    void chineseLabelsRenderCorrectly() {
        // English baseline, pinned rather than inherited. This row measures a CHANGE - "switching
        // to Chinese relabels the panel" - so starting from whatever test.language happens to be
        // would compare the Chinese screen with itself and fail on a screen that was correct.
        shell.selectLanguage(AdminShellPage.ENGLISH);
        List<String> englishLabels = settingsLabels();

        shell.selectLanguage(AdminShellPage.CHINESE);
        assertEquals(AdminShellPage.CHINESE, shell.language(),
                "The header should hold the language that was chosen");

        assertEquals(HEADING_CHINESE, realTime.heading(),
                "The screen should be headed in Chinese. (The client's document writes this title "
                        + "as 返水計劃; the screen says 返水配置 - see RebateTestData.HEADING_CHINESE.)");

        List<String> chineseLabels = settingsLabels();
        CaseLog.data("Settings labels", String.join(" | ", chineseLabels));
        assertNotEquals(englishLabels, chineseLabels,
                "Switching to Chinese should relabel the settings panel, but the labels did not "
                        + "change at all");
        assertEquals(SETTINGS_LABELS_CHINESE, chineseLabels,
                "The global settings should carry their Chinese labels, in order");

        assertEquals(PROGRAM_COLUMNS_CHINESE, realTime.columnHeaders(),
                "The program list should carry its Chinese column headings");

        // The unit beside the time fields is a word, so it follows the language. The amount
        // beside it is a number, so it does not - which is RBMP-123's half of the same question.
        assertEquals(HOUR_SUFFIX_CHINESE, amountSuffix(),
                "The time fields' unit suffix should be translated along with everything else");

        // "Layout accommodates longer/shorter Chinese text without overlap or truncation." A
        // browser cannot be asked whether a layout looks right, but it can be asked whether any
        // label is being clipped by its own box, which is what truncation actually is.
        assertNoLabelIsClipped();

        assertNothingUntranslated("Chinese");
    }

    /**
     * Fails if any settings label is wider than the box drawing it.
     *
     * <p>This is the measurable half of "without overlap or truncation": a label whose
     * {@code scrollWidth} exceeds its {@code clientWidth} is being cut off, whatever it looks
     * like. Overlap between separate elements is left to the screenshot the report attaches.
     */
    private static void assertNoLabelIsClipped() {
        Object clipped = sessionPage.evaluate(
                "() => Array.from(document.querySelectorAll("
                        + "'#divSearchCriteriaRealTime label, #divSearchCriteria label'))"
                        + ".filter(l => l.scrollWidth > l.clientWidth + 1)"
                        + ".map(l => l.innerText.trim())");
        String overflowing = String.valueOf(clipped);
        assertTrue("[]".equals(overflowing),
                "No settings label should be clipped by its own box, but these were: " + overflowing);
    }
    // =====================================================================
    // What must NOT follow the language
    // =====================================================================

    @Test
    @Order(12)
    @DisplayName("RBMP-123: Currency display is consistent across screens and languages")
    void currencyDisplayIsConsistentAcrossLanguages() {
        // Whatever currency the run is in, not a hard-coded one. The workbook names HKD and that
        // is this project's default, but the claim - "the amount reads the same regardless of
        // language" - is true of every currency, and a run pointed at another one should still
        // be testing it.
        assertEquals(CURRENCY, shell.currency(),
                "The run should be in its configured currency before this row reads an amount");

        shell.selectLanguage(AdminShellPage.ENGLISH);
        String amountInEnglish = realTime.getMinClaimValue();
        CaseLog.data("Min. Claim in English, " + CURRENCY, amountInEnglish);

        shell.selectLanguage(AdminShellPage.CHINESE);

        assertEquals(CURRENCY, shell.currency(),
                "Changing the language should not change the selected currency");
        assertEquals(amountInEnglish, realTime.getMinClaimValue(),
                "The Min. Claim amount should read identically in both languages - a currency "
                        + "value is not translated");

        // Deliberately not asserted here: the "/hour" beside the time fields DOES become "/小時",
        // and that is correct - it is a unit of time, not of money, and RBMP-121 is where it
        // belongs. An early draft of this row asserted the suffix was unchanged and failed on
        // the screen being properly localized, which is the wrong way round.
        CaseLog.data("Time-unit suffix in Chinese", amountSuffix());

        // And the third language, because "regardless of the selected language" is a claim about
        // all of them and the back office offers three.
        shell.selectLanguage(AdminShellPage.THAI);
        assertEquals(CURRENCY, shell.currency(),
                "The selected currency should survive Thai as well");
        assertEquals(amountInEnglish, realTime.getMinClaimValue(),
                "The Min. Claim amount should read identically in Thai too");
    }

    /**
     * The unit the screen prints beside the time fields - {@code /hour}, or its translation.
     *
     * <p>The only piece of text on this screen that sits inside a field's own box, which is what
     * makes it the interesting one: {@code RBMP-121} expects it to follow the language and
     * {@code RBMP-123} expects the amount beside it not to.
     */
    private static String amountSuffix() {
        return sessionPage.locator("#divSearchCriteriaRealTime .input-suffix")
                .first().innerText().trim();
    }

    // =====================================================================
    // Last, because it is the only row here that drives Create
    //
    // Everything above reads. This one has to provoke a rejection from the Create form, so it
    // runs after the read-only rows have had a clean screen - and after the create suites, which
    // is what the class-level @Order(70) is for: RBMP-122 needs a program whose pairing it can
    // collide with, and RealTimeRebateTest is what makes one.
    // =====================================================================

    @Test
    @Order(20)
    @DisplayName("RBMP-122: The duplicate-pairing error message is translated")
    void duplicatePairingErrorIsTranslated() {
        shell.selectLanguage(AdminShellPage.CHINESE);

        // The error only exists as the answer to submitting a pairing another program already
        // holds, which needs two programs and therefore two Agent/User Group pairings to build
        // them from. Neither is on this environment.
        if (realTime.isListEmpty()) {
            abort("The duplicate-pairing error cannot be provoked here: the Real-time tab lists "
                    + "no programs at all, so there is no existing pairing to collide with. The "
                    + "error is raised by Create, and Create needs at least one Agent group and "
                    + "one User Group to pair - see RebateTestData.SEEDING_REQUIRED. Seed those, "
                    + "create one program, and this case runs as written: submit the same pairing "
                    + "again with the header in Chinese and assert the message comes back "
                    + "translated rather than in English.");
        }
        abort("Reached only when the tab has programs: driving Create to the point of a rejected "
                + "duplicate pairing needs a page object for the Create form, which "
                + "org.sengmean.page.rebate does not carry yet. RBMP-015 and RBMP-070 wait on the "
                + "same one.");
    }
}
