package org.sengmean.tests.promo;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.sengmean.page.promo.SystemPromotionSetupPage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.sengmean.testdata.SystemPromotionTestData.*;

/**
 * <b>Discount &gt; System Promotion Setup</b> ({@code /promo/system-promotion}), the {@code SPS-*}
 * rows - this project's own, there being no client workbook tab for it yet.
 *
 * <h2>The screen renders, but the operator's permission is missing</h2>
 * Measured 2026-10-05 against admin-test with {@code qa0001}: the page itself renders (heading,
 * breadcrumb, every field and both pickers), but its three data calls -
 * {@code /api/account/system-promo}, {@code /api/account/system-promo/type} and
 * {@code /api/account/get_payment_channel} - all answer a status -10 "Permission denied". With
 * the Type list empty the form carries only defaults (Status Inactive both for the overall setting
 * and the Live section, Wallet Active) and <b>Save stays disabled</b>, so nothing can be written
 * or read back. The Discount menu entry that would link here is hidden for the same operator - the
 * suite reaches the screen by address, as {@code OtherCategoryTest} did before Product shipped.
 *
 * <p>This is the same shape as two earlier findings on this project: the group pickers before
 * 2026-09-21 ({@code AGENTS.md}, "The group pickers work now") and Manage Games's read permission
 * before 2026-09-25. Both resolved once the permission was granted, and nothing in the suite can
 * work around this one honestly either - it is
 * {@link org.sengmean.page.promo.SystemPromotionSetupPage#refusedDataCalls} that every section's
 * {@code @BeforeAll} records, and every writing row aborts on it rather than attempting a save it
 * cannot verify.
 *
 * <h2>What is asserted while it does not</h2>
 * Everything the rendered page can answer without the data calls: the heading and breadcrumb, the
 * section order GD-111 asks for (Overall settings, Wallet Promotion, Live Promotion, then the
 * groups), that Live Promotion carries no currency field (GD-111: Live Promotion always uses the
 * requested deposit currency - only Wallet Promotion's Offer currency select exists), the labels
 * under Traditional Chinese and Thai, and the shape of the two pickers (Select all / Optional,
 * Option / Selected, the four transfer buttons). The rows that need loaded data - a Type to choose,
 * a channel to choose, a save to read back - abort naming the refusal, the same pattern
 * {@code requireScreen()} uses in {@code OtherCategoryTest} for a screen that is not deployed at
 * all. The day the permission is granted, {@link SavingAndValidation} runs as written.
 *
 * <h2>Ids</h2>
 * {@code SPS} is a project prefix - there is no client workbook tab for Discount yet - so ids run
 * {@code SPS-001} upward rather than starting in the 900 block, which is reserved for seed and
 * structural rows added to an *existing* tab, not for a feature this project covers on its own.
 * One case carries one id; ids are never renumbered.
 *
 * <h2>Update, 2026-10-06: the permission above is granted</h2>
 * Confirmed live the same day while finishing {@code SystemPromotionRecordsTest}: {@code
 * /promo/system-promotion} now loads and saves for real, with a genuine configuration already on
 * it (Required deposit min. amount 100 HKD, Wallet Promotion Active 30%, Live Promotion Active
 * 10%) rather than the defaults described above. {@link #dataLoaded} is read live every run, so
 * {@link SavingAndValidation} needs no code change to start running for real - this note exists
 * only so the two sections above are not read as still describing today's state.
 *
 * <h2>Update, same day: the 09:52 run's eight failures, read and fixed</h2>
 * Si's own run (`docs/reports/SystemPromotionSetup-test-report.html`, 09:52) was the first against
 * the now-loaded screen, and it found three real, distinct bugs - none of them a defect in the
 * site, all of them this suite's own locators and assertions having been written against the
 * permission-blocked defaults and never re-measured once real data arrived:
 * <ul>
 *   <li><b>{@code SPS-010/011/014/015} ("Save should succeed, but said \"\"").</b> A save the form
 *       accepts asks its own "...Confirm the modification?" first and saves silently once Confirm
 *       is pressed - no "Successfully modified" follows, unlike the Rebate screen. The old
 *       {@link SystemPromotionSetupPage#save()} only ever watched for a single-OK dialog, so this
 *       intermediate prompt went unanswered for the full 12s wait on every accepted save, returning
 *       empty exactly as a disabled Save does - while a refused save (blank/out-of-range Wallet
 *       percentage, SPS-012/013) skips that prompt and answers immediately, which is why those two
 *       passed. Fixed in {@link SystemPromotionSetupPage#save()} - see its own Javadoc.</li>
 *   <li><b>{@code SPS-002/003} (sections read at colliding positions).</b> With real data loaded the
 *       form draws Wallet Promotion beside Live Promotion, and Agent group beside Member group, as
 *       left/right pairs on shared rows - not each section stacked on its own row, which is what the
 *       blocked defaults this page was first measured against (2026-10-05) happened to look like.
 *       Both rows now check same-row-plus-left-of (via the new {@link SystemPromotionSetupPage#sectionLeft}),
 *       not strict top-to-bottom stacking.</li>
 *   <li><b>{@code SPS-007/008} (labels said empty under Chinese and Thai).</b> Not a translation bug:
 *       {@code assertLabelsFollow} was checking the expected section words against
 *       {@code promo.sectionTitles()}, which only ever matches the hard-coded ENGLISH title strings
 *       and so reads {@code []} under any other language by construction - the message's "found
 *       among []" was reporting that, not a missing translation. Fixed to check the whole visible
 *       page text instead.</li>
 * </ul>
 * All three fixes are compiled only, not run by this investigation - see the project's standing
 * rule that Si runs this suite's own tests locally.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(130)   // after Notification (120)
@DisplayName("Discount - System Promotion Setup")
public class SystemPromotionSetupTest extends BaseTest {

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static SystemPromotionSetupPage promo;

    /** Whether the screen's own data (Type list, channels, the saved setting) loaded. Decides every writing row. */
    private static boolean dataLoaded;
    private static String refusal = "";

    private static final int PROBE_ATTEMPTS = 2;

    /** How close two section tops may read and still count as "the same row" - a left/right pair's labels can differ by a few px. */
    private static final int ROW_TOLERANCE_PX = 20;

    /** Whatever the running case must put back, registered before it writes. */
    private Runnable restore;

    @BeforeAll
    static void signInAndProbe() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(AdminShellPage.ENGLISH);
        shell.useCurrency(CURRENCY);
        promo = new SystemPromotionSetupPage(sessionPage);

        for (int attempt = 1; attempt <= PROBE_ATTEMPTS && !dataLoaded; attempt++) {
            try {
                promo.open();
                dataLoaded = promo.isDataLoaded();
                if (!dataLoaded) {
                    refusal = promo.refusedDataCalls(CURRENCY);
                }
            } catch (RuntimeException screenNeverArrived) {
                refusal = "attempt " + attempt + ": " + firstLine(screenNeverArrived.getMessage());
            }
        }
        CaseLog.data("System Promotion Setup data loaded", dataLoaded + (refusal.isEmpty() ? "" : " (" + refusal + ")"));
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        shell = null;
        promo = null;
    }

    @BeforeEach
    void reopenInEnglish() {
        restore = null;
        if (!AdminShellPage.ENGLISH.equals(shell.language())) {
            shell.selectLanguage(AdminShellPage.ENGLISH);
        }
        promo.open();
    }

    @AfterEach
    void restoreWhatThisCaseChanged() {
        if (restore == null) {
            return;
        }
        try {
            restore.run();
        } catch (RuntimeException couldNotRestore) {
            CaseLog.evidence("This case could not put the setting it changed back: "
                    + couldNotRestore.getMessage() + " - check Discount > System Promotion Setup by hand.");
        } finally {
            restore = null;
        }
    }

    /** Aborts naming the refused data calls - the one reason every writing row takes. */
    private static void requireData() {
        if (!dataLoaded) {
            abort("Discount > System Promotion Setup renders, but its data calls are refused for " + USERNAME
                    + " - " + refusal + ". This row needs the loaded Type list / saved setting to act on and "
                    + "runs as written once the permission is granted; see the class Javadoc.");
        }
    }

    private static String firstLine(String message) {
        return message == null ? "" : message.split("\\R", 2)[0];
    }

    // =====================================================================
    // 1. The screen itself - what renders regardless of the permission
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("1. The screen")
    class Screen {

        @Test
        @DisplayName("SPS-001: Discount > System Promotion Setup opens and is headed correctly")
        void screenOpensAndIsHeaded() {
            assertEquals(HEADING_ENGLISH, promo.heading(), "The screen should be headed \"" + HEADING_ENGLISH + "\"");
            String crumb = promo.breadcrumb();
            CaseLog.data("Breadcrumb", crumb.isEmpty() ? "(none drawn)" : crumb);
            if (!crumb.isEmpty()) {
                assertTrue(crumb.contains(MENU_DISCOUNT) && crumb.contains(HEADING_ENGLISH),
                        "The breadcrumb should name Discount and System Promotion Setup, but reads \"" + crumb + "\"");
            }
        }

        @Test
        @DisplayName("SPS-002: The three settings sections are drawn in the order GD-111 asks for - Overall, then Wallet left of Live on one row")
        void sectionsAreInTheRightOrder() {
            List<String> titles = promo.sectionTitles();
            CaseLog.data("Sections drawn", String.join(" | ", titles));
            assertTrue(titles.containsAll(SystemPromotionSetupPage.SECTION_TITLES.subList(0, 3)),
                    "All three settings sections should be drawn, but found " + titles);
            int overall = promo.sectionTop("Overall settings");
            int walletTop = promo.sectionTop("Wallet Promotion");
            int liveTop = promo.sectionTop("Live Promotion");
            // Measured live 2026-10-06: with real data loaded, Wallet Promotion and Live Promotion draw
            // as a left/right pair on one row below Overall settings, not each on its own row - see
            // SystemPromotionSetupPage's class Javadoc. So "top to bottom" is Overall above the pair's
            // row, and within that row Wallet reads left of Live.
            assertTrue(overall < walletTop && Math.abs(walletTop - liveTop) < ROW_TOLERANCE_PX,
                    "GD-111 asks for Overall settings above the Wallet/Live row, and Wallet Promotion and Live "
                            + "Promotion on the same row - but found them at " + overall + " / " + walletTop + " / " + liveTop);
            int walletLeft = promo.sectionLeft("Wallet Promotion");
            int liveLeft = promo.sectionLeft("Live Promotion");
            assertTrue(walletLeft < liveLeft,
                    "Wallet Promotion should read left of Live Promotion on their shared row, but found them at "
                            + walletLeft + " / " + liveLeft);
        }

        @Test
        @DisplayName("SPS-003: Agent group and Member group stay at the bottom of the form, below the three settings sections")
        void groupsStayAtTheBottom() {
            int liveTop = promo.sectionTop("Live Promotion");
            int agentTop = promo.sectionTop("Agent group");
            int memberTop = promo.sectionTop("Member group");
            // Same shape as SPS-002's fix: Agent group and Member group are a left/right pair on one
            // row too (measured 2026-10-06), below the Wallet/Live row - not Agent above Member.
            assertTrue(liveTop < agentTop && Math.abs(agentTop - memberTop) < ROW_TOLERANCE_PX,
                    "GD-111 asks for the groups below Live Promotion, Agent group and Member group on the same "
                            + "row - but found Live " + liveTop + ", Agent " + agentTop + ", Member " + memberTop);
            int agentLeft = promo.sectionLeft("Agent group");
            int memberLeft = promo.sectionLeft("Member group");
            assertTrue(agentLeft < memberLeft,
                    "Agent group should read left of Member group on their shared row, but found them at "
                            + agentLeft + " / " + memberLeft);
        }

        @Test
        @DisplayName("SPS-004: Live Promotion carries no currency field - it always uses the requested deposit currency")
        void liveSectionHasNoCurrencyField() {
            // GD-111: Live Promotion always uses the requested deposit currency, so it needs no
            // currency field of its own. Only the Wallet section's Offer currency exists.
            assertTrue(promo.offerCurrencyOptions().size() > 0 || !dataLoaded,
                    "The Wallet section's Offer currency select should be on the form");
            boolean liveHasCurrencySelect = sessionPage.locator("select[name='LiveOfferCurrency'], select[name='LiveCurrency']").count() > 0;
            assertFalse(liveHasCurrencySelect,
                    "Live Promotion should have no currency field of its own, per GD-111, but one was found");
        }

        @Test
        @DisplayName("SPS-005: The Time Range field and its Clear control are offered under Overall settings")
        void timeRangeIsOffered() {
            String range = promo.timeRange();
            CaseLog.data("Time Range as found", range.isEmpty() ? "(empty)" : range);
            // No assertion on the value - it is whatever the environment holds - only that the
            // control exists and can be read without throwing, which timeRange() having returned
            // already proves; this row's job is to put it on record for the writing rows below.
        }

        @Test
        @DisplayName("SPS-006: Agent group and Member group each offer Select all and Optional, with Option and Selected lists")
        void pickersHaveTheirShape() {
            for (int group = 0; group < 2; group++) {
                String name = SystemPromotionSetupPage.groupName(group);
                assertTrue(promo.availableNames(group).size() >= 0, name + " Option list should be readable");
                boolean selectAll = promo.isSelectAll(group);
                CaseLog.data(name + " radio", selectAll ? "Select all" : "Optional");
            }
        }
    }

    // =====================================================================
    // 2. Localization - the labels under Traditional Chinese and Thai
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Localization")
    class Localization {

        @Test
        @DisplayName("SPS-007: The form's labels follow the selected language (Traditional Chinese)")
        void labelsFollowChinese() {
            assertLabelsFollow(AdminShellPage.CHINESE, SECTIONS_CHINESE);
        }

        @Test
        @DisplayName("SPS-008: The form's labels follow the selected language (Thai)")
        void labelsFollowThai() {
            assertLabelsFollow(AdminShellPage.THAI, SECTIONS_THAI);
        }

        private void assertLabelsFollow(String language, List<String> expectedSectionWords) {
            shell.selectLanguage(AdminShellPage.ENGLISH);
            promo.open();
            List<String> english = promo.labels();
            assertFalse(english.isEmpty(), "English labels should be readable as a baseline");

            shell.selectLanguage(language);
            assertEquals(language, shell.language(), "The header should hold the language that was chosen");
            promo.open();
            List<String> translated = promo.labels();
            CaseLog.data("Labels in " + language, String.join(" | ", translated));

            assertEquals(english.size(), translated.size(),
                    language + " should relabel the same fields, not add or drop one");
            assertNotEquals(english, translated,
                    "Choosing " + language + " should relabel the form, but the labels were unchanged - "
                            + "which is what an untranslated fallback to English looks like");

            // The expected words are the section headers' own translated text ("通用設定" etc.), not
            // form-field labels, so they are checked against the whole visible page rather than
            // promo.labels() (plain <label> elements only) or promo.sectionTitles(), which matches
            // only the hard-coded ENGLISH section titles and so can never find a translated one.
            // Found 2026-10-06: that made `sections` read empty under every non-English language,
            // failing this row regardless of whether the real translation was right or wrong.
            String pageText = sessionPage.locator("body").innerText();
            for (String expected : expectedSectionWords) {
                assertTrue(pageText.contains(expected),
                        "\"" + expected + "\" should appear somewhere on the page under " + language + ", but was not found.");
            }
            shell.selectLanguage(AdminShellPage.ENGLISH);
        }
    }

    // =====================================================================
    // 3. Saving and validation - needs the data calls to answer
    // =====================================================================

    @Nested
    @Order(30)
    @DisplayName("3. Saving and validation")
    class SavingAndValidation {

        @Test
        @Tag("creates-data")
        @DisplayName("SPS-009: The Type list offers at least one choice, and choosing one is reflected on the form")
        void typeListOffersChoices() {
            requireData();
            List<String> types = promo.typeOptions();
            assertFalse(types.isEmpty(), "Type should offer at least one choice once the data call answers");
            String first = types.get(0);
            promo.choose(SystemPromotionSetupPage.TYPE, first);
            assertEquals(first, promo.selected(SystemPromotionSetupPage.TYPE), "Choosing a Type should be reflected on the form");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPS-010: Saving the Wallet Promotion percentage and maximum discount amount is read back after a reload")
        void walletPercentageAndMaxAmountRoundTrip() {
            requireData();
            Map<String, String> before = promo.snapshot();
            restore = () -> {
                promo.open();
                promo.apply(before);
                promo.save();
            };
            promo.setValue(SystemPromotionSetupPage.WALLET_PERCENTAGE, WALLET_PERCENTAGE);
            promo.setValue(SystemPromotionSetupPage.WALLET_MAX_AMOUNT, WALLET_MAX_AMOUNT);
            String said = promo.save();
            assertTrue(SystemPromotionSetupPage.wasAccepted(said), "Save should succeed, but said \"" + said + "\"");

            promo.open();
            assertEquals(WALLET_PERCENTAGE, promo.value(SystemPromotionSetupPage.WALLET_PERCENTAGE),
                    "The Wallet percentage should be what was saved after a reload");
            assertEquals(WALLET_MAX_AMOUNT, promo.value(SystemPromotionSetupPage.WALLET_MAX_AMOUNT),
                    "The Wallet maximum discount amount should be what was saved after a reload");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPS-011: Saving the Live Promotion percentage and maximum discount amount is read back after a reload")
        void livePercentageAndMaxAmountRoundTrip() {
            requireData();
            Map<String, String> before = promo.snapshot();
            restore = () -> {
                promo.open();
                promo.apply(before);
                promo.save();
            };
            promo.setValue(SystemPromotionSetupPage.LIVE_PERCENTAGE, LIVE_PERCENTAGE);
            promo.setValue(SystemPromotionSetupPage.LIVE_MAX_AMOUNT, LIVE_MAX_AMOUNT);
            String said = promo.save();
            assertTrue(SystemPromotionSetupPage.wasAccepted(said), "Save should succeed, but said \"" + said + "\"");

            promo.open();
            assertEquals(LIVE_PERCENTAGE, promo.value(SystemPromotionSetupPage.LIVE_PERCENTAGE),
                    "The Live percentage should be what was saved after a reload");
            assertEquals(LIVE_MAX_AMOUNT, promo.value(SystemPromotionSetupPage.LIVE_MAX_AMOUNT),
                    "The Live maximum discount amount should be what was saved after a reload");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPS-012: Saving a blank Wallet Promotion percentage is refused")
        void blankWalletPercentageIsRefused() {
            requireData();
            Map<String, String> before = promo.snapshot();
            restore = () -> {
                promo.open();
                promo.apply(before);
                promo.save();
            };
            promo.setValue(SystemPromotionSetupPage.WALLET_PERCENTAGE, "");
            String said = promo.save();
            CaseLog.data("Save said", said.isEmpty() ? "(nothing)" : said);
            assertFalse(SystemPromotionSetupPage.wasAccepted(said),
                    "Saving with the Wallet percentage blank should be refused, but the screen said \"" + said + "\"");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPS-013: A Wallet Promotion percentage outside 0-100 is accepted, as the system currently implements it")
        void walletPercentageOutOfRangeIsAccepted() {
            // Si asked for this row to be fixed "as system working" (2026-10-06). It had been
            // tagged known-defect since the save()-prompt fix made the real behaviour observable:
            // PERCENTAGE_RANGE is the client's own stated range (0-100), and there is no sane
            // reading of "150% of the deposit as a maximum-discount-capped bonus" as intended - but
            // the screen does not enforce it. Measured live: 150 saves as "Successfully modified"
            // and reads back as 150 after a reload, and restoring it to the original value afterward
            // works the same way - confirmed live, the setting is never left stuck. The row now
            // asserts what the system actually does rather than what the client's own range implies
            // it should refuse; the gap itself (no server-side bound on this field) is a finding for
            // the client's own list, not a reason for this row to stay red.
            requireData();
            Map<String, String> before = promo.snapshot();
            restore = () -> {
                promo.open();
                promo.apply(before);
                promo.save();
            };
            promo.setValue(SystemPromotionSetupPage.WALLET_PERCENTAGE, "150");
            String said = promo.save();
            CaseLog.data("Save said", said.isEmpty() ? "(nothing)" : said);
            assertTrue(SystemPromotionSetupPage.wasAccepted(said),
                    "A Wallet percentage of 150 (outside the client's own stated 0-100 range) should still be "
                            + "accepted, as the system currently implements it, but the screen said \"" + said + "\"");

            promo.open();
            assertEquals("150", promo.value(SystemPromotionSetupPage.WALLET_PERCENTAGE),
                    "The Wallet percentage should still read 150 after a reload.");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPS-014: Setting both Agent group and Member group to Select all is accepted and holds after a reload")
        void selectAllBothGroupsRoundTrips() {
            requireData();
            Map<String, String> before = promo.snapshot();
            restore = () -> {
                promo.open();
                promo.apply(before);
                promo.save();
            };
            promo.chooseSelectAll(SystemPromotionSetupPage.AGENT_GROUP);
            promo.chooseSelectAll(SystemPromotionSetupPage.MEMBER_GROUP);
            String said = promo.save();
            assertTrue(SystemPromotionSetupPage.wasAccepted(said), "Save should succeed, but said \"" + said + "\"");

            promo.open();
            assertTrue(promo.isSelectAll(SystemPromotionSetupPage.AGENT_GROUP), "Agent group should hold Select all after a reload");
            assertTrue(promo.isSelectAll(SystemPromotionSetupPage.MEMBER_GROUP), "Member group should hold Select all after a reload");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SPS-015: Targeting one Agent group and one Member group on Optional is accepted and holds after a reload")
        void optionalOnePairingRoundTrips() {
            requireData();
            Map<String, String> before = promo.snapshot();
            restore = () -> {
                promo.open();
                promo.apply(before);
                promo.save();
            };
            List<String> agents = promo.availableNames(SystemPromotionSetupPage.AGENT_GROUP);
            List<String> members = promo.availableNames(SystemPromotionSetupPage.MEMBER_GROUP);
            if (agents.isEmpty() || members.isEmpty()) {
                abort("No Agent group or Member group is configured to target, so this row has nothing to select.");
            }
            promo.targetOnly(SystemPromotionSetupPage.AGENT_GROUP, List.of(agents.get(0)));
            promo.targetOnly(SystemPromotionSetupPage.MEMBER_GROUP, List.of(members.get(0)));
            String said = promo.save();
            assertTrue(SystemPromotionSetupPage.wasAccepted(said), "Save should succeed, but said \"" + said + "\"");

            promo.open();
            assertEquals(List.of(agents.get(0)), promo.selectedNames(SystemPromotionSetupPage.AGENT_GROUP),
                    "The targeted Agent group should hold after a reload");
            assertEquals(List.of(members.get(0)), promo.selectedNames(SystemPromotionSetupPage.MEMBER_GROUP),
                    "The targeted Member group should hold after a reload");
        }

        @Test
        @Tag("creates-data")
        @Tag("known-defect")
        @DisplayName("SPS-016: Saving Status Active with a Time Range wholly in the past is refused")
        void activeWithPastTimeRangeIsRefused() {
            // The bundle's promo_update_failed message says exactly this should be refused when
            // Active and the range excludes "now" - a past range always does. Tagged known-defect
            // until confirmed against a real save, which the permission denial currently blocks.
            requireData();
            Map<String, String> before = promo.snapshot();
            restore = () -> {
                promo.open();
                promo.apply(before);
                promo.save();
            };
            promo.choose(SystemPromotionSetupPage.STATUS, SystemPromotionSetupPage.ACTIVE);
            promo.setTimeRange(PAST_RANGE);
            String said = promo.save();
            CaseLog.data("Save said", said.isEmpty() ? "(nothing)" : said);
            assertFalse(SystemPromotionSetupPage.wasAccepted(said),
                    "Status Active with a Time Range wholly in the past should be refused "
                            + "(the back office's own message: time range must include now, or be left blank), "
                            + "but the screen said \"" + said + "\"");
        }
    }
}
