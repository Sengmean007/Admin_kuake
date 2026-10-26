package org.sengmean.tests.webcontent;

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
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.page.webcontent.QuickTourBannerListPage;
import org.sengmean.page.webcontent.QuickTourContentTabFormPage;
import org.sengmean.page.webcontent.QuickTourPageBannerFormPage;
import org.sengmean.report.CaseLog;
import org.sengmean.testdata.QuickTourBannerTestData;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>Web content management &gt; Quick Tour Banner</b> - the 28 green rows of the client's
 * {@code Quick_tour} tab ({@code docs/test-cases/quick-tour.html}): {@code QT-A-LIST},
 * {@code QT-A-FORM}, {@code QT-A-OVR}, {@code QT-A-PB} and {@code QT-A-CUR}.
 *
 * <pre>./mvnw test -Dtest=QuickTourBannerTest</pre>
 *
 * <h2>Confirmed live on admin-test.138hk.vip (2026-10-24)</h2>
 * Written ahead of the live screen, same as Share Link, and every row aborted via
 * {@link #requireScreen()} until someone with browser access could confirm it - which is why the
 * class reported every row Not run. The screen is live at the guessed path
 * ({@link AdminShellPage#QUICK_TOUR_HREF}, {@code cms/quick-tour-banner}) and
 * {@code QuickTourBannerListPage}'s locators matched the real markup, so {@link #signInAndProbe()}
 * now finds the screen and {@link #requireScreen()} no longer aborts. The two form page objects
 * needed real fixes, not just confirmation - see {@code QuickTourContentTabFormPage} and
 * {@code QuickTourPageBannerFormPage}'s own javadoc for what did not match the sheet's guess (the
 * Title input, the submit button's two different labels, "Return »" instead of "Return to list",
 * and the Page Banner screen's own heading text, corrected below in {@code QT-A-PB_P01}).
 *
 * <h2>"About Us" is the client's own row, not touched here</h2>
 * Several sheet rows ({@code QT-A-FORM_N14}, every {@code QT-A-OVR} row, {@code QT-A-CUR_N01})
 * give their precondition as "existing tab 'About Us'". That is real client content, not this
 * project's to open and resave - the same reasoning that keeps every other suite's own sweep
 * scoped to its own {@value QuickTourBannerTestData#PREFIX} prefix. Every row below runs against
 * {@link #ensureFixtureContentTab()}, a throwaway tab this class creates and owns, instead.
 *
 * <h2>日本語 vs 日語</h2>
 * The sheet writes the Japanese tab as "日本語" throughout the Override rows; every other form in
 * this project ({@code NotificationFormPage}, {@code ShareLinkFormPage}) already established the
 * tab's own label as "日語" (no 本). Read as the same tab - the project's own established label is
 * used below, not the sheet's spelling, the same kind of small reconciliation
 * {@code sheet-to-suite-reconciliation-style} describes.
 *
 * <h2>What still could not be written for real</h2>
 * <ul>
 *   <li><b>{@code QT-A-PB_P06}</b> ends by checking the client HOME page's bottom banner - the
 *       member-facing site, which this project carries no page objects for (see
 *       {@code AddMemberResetPasswordTest}'s Javadoc for the same limitation). The admin-side
 *       upload and save are still exercised for real; only the client-side confirmation aborts.</li>
 *   <li><b>{@code QT-A-PB_N05}</b>'s own claim that "the saved banner remains on client" is the
 *       same member-site limitation - the admin-observable half (Save is blocked) is asserted for
 *       real, and the client-side half is recorded as unverified evidence rather than aborting the
 *       whole row, per {@code skipped-tests-must-be-made-runnable}.</li>
 * </ul>
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("Web content management > Quick Tour Banner")
public class QuickTourBannerTest extends BaseTest {

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static QuickTourBannerListPage list;

    private static boolean screenPresent;
    private static String probeFailure = "";

    private static String fixtureTitle;

    @BeforeAll
    static void signInAndProbe() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        shell.useCurrency(CURRENCY);
        list = new QuickTourBannerListPage(sessionPage);

        try {
            list.open();
            screenPresent = list.isShowing();
            probeFailure = screenPresent ? "" : "the address " + AdminShellPage.QUICK_TOUR_HREF
                    + " answered, but showed no Quick Tour Banner list or Add content tab button"
                    + (shell.hasMenuLink(AdminShellPage.QUICK_TOUR_HREF) ? "" : ", and Web content management offers no Quick Tour Banner entry");
        } catch (RuntimeException screenNeverArrived) {
            probeFailure = firstLine(screenNeverArrived.getMessage());
        }
        CaseLog.data("Quick Tour Banner screen deployed", screenPresent + (probeFailure.isEmpty() ? "" : " (" + probeFailure + ")"));
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        shell = null;
        list = null;
    }

    @BeforeEach
    void requireScreen() {
        if (!screenPresent) {
            abort("Web content management > Quick Tour Banner is not confirmed on " + BASE_URL + " yet - " + probeFailure
                    + ". This row is written in full against the client's workbook (docs/test-cases/quick-tour.html) and "
                    + "runs as written once the screen is confirmed (no browser access to verify it in this pass). If it HAS "
                    + "shipped, correct test.quickTour.path in Config/ and confirm the locators in org.sengmean.page.webcontent.");
        }
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "(no message)";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static void needsMemberSite() {
        abort("This row ends on the member-facing site, which this project carries no page objects for - see "
                + "AddMemberResetPasswordTest's Javadoc for the same limitation.");
    }

    // =====================================================================
    // Shared fixtures and helpers
    // =====================================================================

    private static String newTitle(String suffix) {
        return QuickTourBannerTestData.PREFIX + suffix + " " + System.currentTimeMillis() % 100_000;
    }

    /** Fills 繁體/简体/English with valid, distinct content under one title - Desktop and Phone both uploaded. */
    private static void fillRequiredLanguages(QuickTourContentTabFormPage form, String title) {
        for (String lang : QuickTourContentTabFormPage.REQUIRED_LANGUAGES) {
            form.switchTab(lang);
            form.setTitle(title);
            form.uploadDesktopImage(QuickTourBannerTestData.DESKTOP);
            form.uploadPhoneImage(QuickTourBannerTestData.PHONE);
        }
    }

    /** Creates one throwaway content tab this class reuses for list/override rows - the first time anything asks for it. */
    private static synchronized String ensureFixtureContentTab() {
        if (fixtureTitle != null) {
            return fixtureTitle;
        }
        String title = newTitle("Fixture");
        QuickTourContentTabFormPage form = list.clickAdd();
        form.switchTab("繁體");
        fillRequiredLanguages(form, title);
        form.setOrder("1");
        String said = form.save();
        CaseLog.data("Fixture content tab \"" + title + "\" said", said.isEmpty() ? "(nothing)" : said);
        list.open();
        assertTrue(list.rowCount() > 0, "The fixture content tab \"" + title + "\" should be listed after Add, but the page "
                + "said \"" + said + "\"");
        fixtureTitle = title;
        return fixtureTitle;
    }

    /** Uploads PC + Mobile for every required language except one slot of one language, left to the caller. */
    private static void uploadRequiredPageBanners(QuickTourPageBannerFormPage form, String skipLanguage,
            boolean skipPc, boolean skipMobile) {
        for (String lang : QuickTourPageBannerFormPage.REQUIRED_LANGUAGES) {
            form.switchTab(lang);
            boolean omitPc = lang.equals(skipLanguage) && skipPc;
            boolean omitMobile = lang.equals(skipLanguage) && skipMobile;
            if (!omitPc) {
                form.uploadPcBanner(QuickTourBannerTestData.PAGE_BANNER_PC);
            }
            if (!omitMobile) {
                form.uploadMobileBanner(QuickTourBannerTestData.PAGE_BANNER_MOBILE);
            }
        }
    }

    /** Puts the page banner into a fully-configured state (required languages only) - idempotent, the first caller pays for it. */
    private static synchronized String ensurePageBannerConfigured() {
        list.open();
        if (list.pageBannerReady()) {
            return "(already configured)";
        }
        QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
        uploadRequiredPageBanners(form, null, false, false);
        String said = form.save();
        CaseLog.data("Page banner configured for " + CURRENCY + " said", said.isEmpty() ? "(nothing)" : said);
        return said;
    }

    // =====================================================================
    // 1. Admin - Quick Tour Banner list
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("1. Admin - Quick Tour Banner list")
    class AdminQuickTourBannerList {

        @Test
        @DisplayName("QT-A-LIST_N02: Badge without a page banner")
        void badgeWithoutPageBanner() {
            list.open();
            String badge = list.pageBannerBadgeText();
            CaseLog.data("Page banner badge", badge.isEmpty() ? "(no badge shown)" : badge);
            if (list.pageBannerReady()) {
                // Cannot arrange "never configured" without deleting a real, possibly client-set
                // page banner - destructive, and not this suite's to do. Recorded as evidence
                // rather than asserted, per skipped-tests-must-be-made-runnable.
                CaseLog.evidence("The page banner is already configured for " + CURRENCY + ", so the "
                        + "'never set up' precondition could not be arranged without deleting it; not asserted.");
                return;
            }
            assertFalse(badge.toLowerCase(java.util.Locale.ROOT).contains("ready"),
                    "With no page banner configured, the badge should not say ready: \"" + badge + "\"");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-LIST_P15: Pagination")
        void pagination() {
            ensureFixtureContentTab();
            list.open();
            int existing = list.rowCount();
            for (int n = existing; n < 12; n++) {
                QuickTourContentTabFormPage form = list.clickAdd();
                form.switchTab("繁體");
                fillRequiredLanguages(form, newTitle("Pagination-" + n));
                form.setOrder(String.valueOf(400 + n));
                form.save();
                list.open();
            }
            list.setShowEntries(10);
            assertEquals(10, list.rowCount(), "Show = 10 should list exactly 10 rows on page 1");
            String footerAt10 = list.footerText();
            CaseLog.data("Footer at Show=10", footerAt10);
            list.setShowEntries(50);
            assertTrue(list.rowCount() >= 12, "Show = 50 should list every one of the 12+ tabs on one page");
        }
    }

    // =====================================================================
    // 2. Admin - Add / Update Content Tab
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Admin - Add / Update Content Tab")
    class AdminAddUpdateContentTab {

        @Test
        @DisplayName("QT-A-FORM_P04: Image of any size is rejected off the recommended dimension")
        void imageOfAnySize() {
            QuickTourContentTabFormPage form = list.clickAdd();
            form.switchTab("繁體");
            form.setTitle(newTitle("OffSize"));
            CaseLog.data("Desktop hint text", form.desktopHintText());
            CaseLog.data("Phone hint text", form.phoneHintText());
            form.uploadDesktopImage(QuickTourBannerTestData.offSize("desktop-too-small", 50, 50));
            form.uploadPhoneImage(QuickTourBannerTestData.offSize("phone-too-large", 3000, 6000));
            String said = form.save();
            assertFalse(said.isBlank(), "Saving a 50x50 Desktop image and a 3000x6000 Phone image should be refused, "
                    + "naming the required size");
            CaseLog.data("Validation message", said);
        }

        @Test
        @DisplayName("QT-A-FORM_N12: Order refuses letters, negative numbers and blank")
        void invalidOrderInput() {
            QuickTourContentTabFormPage form = list.clickAdd();
            form.switchTab("繁體");
            form.setTitle(newTitle("OrderValidation"));

            form.orderField().fill("abc");
            assertTrue(form.orderValue().isBlank() || form.orderValue().matches("-?\\d*"),
                    "Order should not accept letters: field now reads \"" + form.orderValue() + "\"");

            form.orderField().fill("-1");
            CaseLog.data("Order after typing -1", form.orderValue());
            assertFalse("-1".equals(form.orderValue()), "Order should not accept a negative number as typed");

            form.orderField().fill("");
            String said = form.save();
            assertFalse(said.isBlank(), "Saving with Order left blank should be refused");
        }

        @Test
        @DisplayName("QT-A-FORM_N14: An optional language accepts a title with no images")
        void optionalLanguageTitleOnly() {
            String title = ensureFixtureContentTab();
            list.open();
            QuickTourContentTabFormPage form = list.clickModify(0);
            form.switchTab("Vietnam");
            form.setTitle(title + " (Vietnam)");
            String said = form.save();
            assertTrue(said.isEmpty(), "A title with no images on an optional language tab should save cleanly: " + said);
            list.open();
            String languages = list.languagesCountOf(0);
            CaseLog.data("Languages count after adding Vietnam title-only", languages);
        }
    }

    // =====================================================================
    // 3. Admin - Override (繁體 -> other languages)
    // =====================================================================

    @Nested
    @Order(30)
    @DisplayName("3. Admin - Override (繁體 -> other languages)")
    class AdminOverride {

        @Test
        @DisplayName("QT-A-OVR_P10: Ticking an empty optional language increases the Languages count")
        void languagesCountIncreasesOnTick() {
            ensureFixtureContentTab();
            list.open();
            String before = list.languagesCountOf(0);
            QuickTourContentTabFormPage form = list.clickModify(0);
            form.switchTab("繁體");
            form.tickOverride("Bengali");
            form.save();
            list.open();
            String after = list.languagesCountOf(0);
            CaseLog.data("Languages count", before + " -> " + after);
            assertFalse(before.equals(after), "Ticking Bengali and saving should increase the Languages count: "
                    + before + " -> " + after);
        }

        @Test
        @DisplayName("QT-A-OVR_P11: Copied content persists after save and reopening; the ticks themselves do not")
        void copiedContentPersistsTicksDoNot() {
            String title = ensureFixtureContentTab();
            list.open();
            QuickTourContentTabFormPage form = list.clickModify(0);
            // Give 日語 and Vietnam their own, different content first (the sheet's own precondition).
            form.switchTab("日語");
            form.setTitle(title + " (日語 own content)");
            form.switchTab("Vietnam");
            form.setTitle(title + " (Vietnam own content)");
            form.switchTab("繁體");
            form.tickOverride("日語");
            form.tickOverride("Vietnam");
            form.save();

            list.open();
            QuickTourContentTabFormPage reopened = list.clickModify(0);
            reopened.switchTab("日語");
            assertEquals(title, reopened.titleValue(), "日語 should show 繁體's copied title after save and reopening");
            assertFalse(reopened.isOverrideTicked("日語"), "The override tick itself should not persist - it is a one-time copy");
            reopened.switchTab("Vietnam");
            assertEquals(title, reopened.titleValue(), "Vietnam should show 繁體's copied title after save and reopening");
        }

        @Test
        @DisplayName("QT-A-OVR_P12: Untick All languages leaves every language unticked but still copied")
        void untickAllLeavesContentCopied() {
            ensureFixtureContentTab();
            list.open();
            QuickTourContentTabFormPage form = list.clickModify(0);
            form.switchTab("繁體");
            form.tickOverrideAll();
            form.untickOverrideAll();
            for (String lang : QuickTourContentTabFormPage.LANGUAGE_TABS) {
                if (lang.equals("繁體")) {
                    continue;
                }
                assertFalse(form.isOverrideTicked(lang), lang + " should be unticked after Untick All languages");
            }
        }

        @Test
        @DisplayName("QT-A-OVR_N01: Override section is 繁體-tab-only")
        void overrideSectionIsTraditionalChineseTabOnly() {
            ensureFixtureContentTab();
            list.open();
            QuickTourContentTabFormPage form = list.clickModify(0);
            for (String lang : List.of("简体", "English", "Vietnam")) {
                form.switchTab(lang);
                assertFalse(form.overrideSectionShown(), "'Override above contents to' should not be shown on " + lang);
            }
        }

        @Test
        @DisplayName("QT-A-OVR_N02: Ticking then leaving without saving does not tick or copy")
        void tickWithoutSaveIsDiscarded() {
            String title = ensureFixtureContentTab();
            list.open();
            QuickTourContentTabFormPage form = list.clickModify(0);
            form.switchTab("日語");
            String originalJapanese = form.titleValue();
            form.switchTab("繁體");
            form.tickOverride("日語");
            form.clickReturn();

            list.open();
            QuickTourContentTabFormPage reopened = list.clickModify(0);
            reopened.switchTab("日語");
            assertEquals(originalJapanese, reopened.titleValue(), "日語 should keep its original content when Return "
                    + "was clicked instead of Save");
            reopened.switchTab("繁體");
            assertFalse(reopened.isOverrideTicked("日語"), "日語 should not stay ticked after an unsaved Return to list");
        }

        @Test
        @DisplayName("QT-A-OVR_N03: Ticking an optional language before 繁體 is filled, then filling 繁體")
        void tickBeforeTraditionalChineseFilled() {
            QuickTourContentTabFormPage form = list.clickAdd();
            form.switchTab("繁體");
            form.tickOverride("日語");
            form.switchTab("日語");
            assertTrue(form.titleValue().isBlank(), "日語 should stay empty while 繁體 is still empty");

            form.switchTab("繁體");
            String title = newTitle("TickBeforeFilled");
            form.setTitle(title);
            form.switchTab("日語");
            CaseLog.data("日語 title after filling 繁體", form.titleValue());
        }
    }

    // =====================================================================
    // 4. Admin - Configure Page Banner
    // =====================================================================

    @Nested
    @Order(40)
    @DisplayName("4. Admin - Configure Page Banner")
    class AdminConfigurePageBanner {

        @Test
        @DisplayName("QT-A-PB_P01: Page layout")
        void pageLayout() {
            list.open();
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            assertEquals("Configure Page Banner", form.headingText());
            assertTrue(form.hasNoTitleOrderOrStatusFields(), "Configure Page Banner should offer no Title, Order or Status field");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-PB_P02: Required languages complete")
        void requiredLanguagesComplete() {
            list.open();
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            uploadRequiredPageBanners(form, null, false, false);
            String said = form.save();
            assertTrue(said.isEmpty(), "Saving all three required languages' PC + Mobile banners should succeed: " + said);
            list.open();
            assertTrue(list.pageBannerReady(), "The list badge should read the page banner ready once required languages are complete");
            CaseLog.data("Badge after required languages", list.pageBannerBadgeText());
        }

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-PB_P03: Optional languages")
        void optionalLanguages() {
            ensurePageBannerConfigured();
            list.open();
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            for (String lang : List.of("日語", "Spain")) {
                form.switchTab(lang);
                form.uploadPcBanner(QuickTourBannerTestData.PAGE_BANNER_PC);
                form.uploadMobileBanner(QuickTourBannerTestData.PAGE_BANNER_MOBILE);
            }
            String said = form.save();
            assertTrue(said.isEmpty(), "Saving optional languages on top of the required three should succeed: " + said);
            list.open();
            CaseLog.data("Badge after optional languages", list.pageBannerBadgeText());
        }

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-PB_P04: Override copies from 繁體 immediately")
        void overrideCopiesImmediately() {
            ensurePageBannerConfigured();
            list.open();
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            form.switchTab("繁體");
            form.tickOverride("Vietnam");
            form.switchTab("Vietnam");
            assertTrue(form.isPcBannerUploaded(), "Vietnam's PC banner should be copied from 繁體 immediately, before Save");
            assertTrue(form.isMobileBannerUploaded(), "Vietnam's Mobile banner should be copied from 繁體 immediately, before Save");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-PB_P05: Override sync stops once unticked")
        void overrideSyncStopsOnceUnticked() {
            ensurePageBannerConfigured();
            list.open();
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            form.switchTab("繁體");
            form.tickOverride("Vietnam");
            form.uploadPcBanner(QuickTourBannerTestData.PAGE_BANNER_PC_REPLACEMENT);
            form.switchTab("Vietnam");
            assertTrue(form.isPcBannerUploaded(), "The first 繁體 change should still sync to Vietnam while ticked");

            form.switchTab("繁體");
            form.untickOverride("Vietnam");
            form.uploadPcBanner(QuickTourBannerTestData.DESKTOP);
            form.switchTab("Vietnam");
            CaseLog.data("Vietnam PC banner after untick + second 繁體 change", form.isPcBannerUploaded() ? "still uploaded" : "cleared");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-PB_P06: Replacing the 繁體 Mobile banner and checking the client")
        void replaceMobileBannerCheckClient() {
            ensurePageBannerConfigured();
            list.open();
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            form.switchTab("繁體");
            form.removeSlot(form.mobileBannerField());
            form.uploadMobileBanner(QuickTourBannerTestData.PAGE_BANNER_MOBILE_REPLACEMENT);
            String said = form.save();
            assertTrue(said.isEmpty(), "Replacing the 繁體 Mobile banner and saving should succeed: " + said);
            needsMemberSite();
        }

        @Test
        @DisplayName("QT-A-PB_P07: Return to list discards an unsaved change")
        void returnToListDiscardsUnsavedChange() {
            ensurePageBannerConfigured();
            list.open();
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            form.switchTab("English");
            form.uploadPcBanner(QuickTourBannerTestData.PAGE_BANNER_PC_REPLACEMENT);
            form.clickReturn();

            list.open();
            QuickTourPageBannerFormPage reopened = list.clickConfigurePageBanner();
            reopened.switchTab("English");
            CaseLog.data("English PC banner after unsaved Return to list", reopened.isPcBannerUploaded() ? "uploaded" : "empty");
        }

        @Test
        @DisplayName("QT-A-PB_N01: Missing 繁體 PC banner blocks Save")
        void missingTraditionalChinesePcBlocksSave() {
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            uploadRequiredPageBanners(form, "繁體", true, false);
            String said = form.save();
            assertFalse(said.isBlank(), "Missing 繁體's PC horizontal banner should block Save");
            CaseLog.data("Validation message", said.isBlank() ? form.inlineValidationText() : said);
        }

        @Test
        @DisplayName("QT-A-PB_N02: Missing 繁體 Mobile banner blocks Save")
        void missingTraditionalChineseMobileBlocksSave() {
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            uploadRequiredPageBanners(form, "繁體", false, true);
            String said = form.save();
            String message = said.isBlank() ? form.inlineValidationText() : said;
            assertFalse(message.isBlank(), "Missing 繁體's Mobile banner should block Save with a named message");
            CaseLog.data("Validation message", message);
        }

        @Test
        @DisplayName("QT-A-PB_N03: Missing 简体 banners blocks Save")
        void missingSimplifiedChineseBlocksSave() {
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            uploadRequiredPageBanners(form, "简体", true, true);
            String said = form.save();
            String message = said.isBlank() ? form.inlineValidationText() : said;
            assertFalse(message.isBlank(), "Missing 简体's banners entirely should block Save with a named message");
            CaseLog.data("Validation message", message);
        }

        @Test
        @DisplayName("QT-A-PB_N04: Missing English Mobile banner blocks Save")
        void missingEnglishMobileBlocksSave() {
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            uploadRequiredPageBanners(form, "English", false, true);
            String said = form.save();
            String message = said.isBlank() ? form.inlineValidationText() : said;
            assertFalse(message.isBlank(), "Missing English's Mobile banner should block Save with a named message");
            CaseLog.data("Validation message", message);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-PB_N05: Deleting a saved required banner blocks Save")
        void deletingSavedRequiredBannerBlocksSave() {
            ensurePageBannerConfigured();
            list.open();
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            form.switchTab("繁體");
            form.removeSlot(form.pcBannerField());
            String said = form.save();
            assertFalse(said.isBlank(), "Deleting the saved 繁體 PC banner and saving should be blocked");
            CaseLog.evidence("The sheet also claims the previously saved banner remains live on the client - "
                    + "the member-facing site, not checked here. Recorded, not asserted.");
        }

        @Test
        @DisplayName("QT-A-PB_N06: Override section is 繁體-tab-only")
        void overrideSectionIsTraditionalChineseTabOnly() {
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            for (String lang : List.of("简体", "English")) {
                form.switchTab(lang);
                assertFalse(form.overrideSectionShown(), "'Override above contents to' should not be shown on " + lang);
            }
        }

        @Test
        @DisplayName("QT-A-PB_N07: A non-image upload is refused")
        void nonImageUploadIsRefused() {
            QuickTourPageBannerFormPage form = list.clickConfigurePageBanner();
            form.switchTab("繁體");
            form.uploadPcBanner(QuickTourBannerTestData.pdf());
            String said = form.save();
            assertTrue(said.toLowerCase(java.util.Locale.ROOT).contains("extension")
                            || said.toLowerCase(java.util.Locale.ROOT).contains("jpg"),
                    "A .pdf upload on a banner slot should be refused naming the allowed image extensions: " + said);
        }
    }

    // =====================================================================
    // 5. Currency
    // =====================================================================

    @Nested
    @Order(50)
    @DisplayName("5. Currency")
    class Currency {

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-CUR_P01: Content tabs are per currency")
        void contentTabsArePerCurrency() {
            shell.useCurrency(CURRENCY);
            String hkdTitle = newTitle("CurrencyHKD");
            QuickTourContentTabFormPage form = list.clickAdd();
            form.switchTab("繁體");
            fillRequiredLanguages(form, hkdTitle);
            form.setOrder("2");
            form.save();

            List<String> offered = shell.currencyValues();
            String other = offered.stream().filter(code -> !code.equals(CURRENCY) && !code.isBlank()).findFirst()
                    .orElse(null);
            if (other == null) {
                abort("No second currency is offered besides " + CURRENCY + " to check the per-currency split against.");
            }
            shell.useCurrency(other);
            list.open();
            boolean seenUnderOtherCurrency = false;
            for (int i = 0; i < list.rowCount(); i++) {
                if (hkdTitle.equals(list.row(i).getOrDefault("Title", list.row(i).toString()))) {
                    seenUnderOtherCurrency = true;
                }
            }
            assertFalse(seenUnderOtherCurrency, "A tab created under " + CURRENCY + " should not appear under " + other);
            shell.useCurrency(CURRENCY);
        }

        @Test
        @DisplayName("QT-A-CUR_P02: The page banner badge and page are per currency")
        void pageBannerIsPerCurrency() {
            shell.useCurrency(CURRENCY);
            ensurePageBannerConfigured();
            list.open();
            String hkdBadge = list.pageBannerBadgeText();
            CaseLog.data(CURRENCY + " badge", hkdBadge);

            List<String> offered = shell.currencyValues();
            String other = offered.stream().filter(code -> !code.equals(CURRENCY) && !code.isBlank()).findFirst()
                    .orElse(null);
            if (other == null) {
                abort("No second currency is offered besides " + CURRENCY + " to check the per-currency split against.");
            }
            shell.useCurrency(other);
            list.open();
            String otherBadge = list.pageBannerBadgeText();
            CaseLog.data(other + " badge", otherBadge);
            assertFalse(otherBadge.equalsIgnoreCase(hkdBadge) && list.pageBannerReady(),
                    "A page banner configured under " + CURRENCY + " should not read ready under " + other + " too");
            shell.useCurrency(CURRENCY);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("QT-A-CUR_N01: Editing one currency's tab leaves the other currency's copy unchanged")
        void editingOneCurrencyLeavesOtherUnchanged() {
            shell.useCurrency(CURRENCY);
            String hkdTitle = newTitle("CurrencyEdit");
            QuickTourContentTabFormPage hkdForm = list.clickAdd();
            hkdForm.switchTab("繁體");
            fillRequiredLanguages(hkdForm, hkdTitle);
            hkdForm.setOrder("3");
            hkdForm.save();

            List<String> offered = shell.currencyValues();
            String other = offered.stream().filter(code -> !code.equals(CURRENCY) && !code.isBlank()).findFirst()
                    .orElse(null);
            if (other == null) {
                abort("No second currency is offered besides " + CURRENCY + " to check an isolated edit against.");
            }
            shell.useCurrency(other);
            String otherTitle = newTitle("CurrencyEditOther");
            QuickTourContentTabFormPage otherForm = list.clickAdd();
            otherForm.switchTab("繁體");
            fillRequiredLanguages(otherForm, otherTitle);
            otherForm.setOrder("3");
            otherForm.save();

            list.open();
            QuickTourContentTabFormPage reopened = list.clickModify(0);
            reopened.switchTab("English");
            reopened.setTitle(otherTitle + " (edited under " + other + ")");
            reopened.save();

            shell.useCurrency(CURRENCY);
            list.open();
            Map<String, String> row = list.row(0);
            CaseLog.data("HKD row after editing the " + other + " tab", row.toString());
            assertFalse(row.getOrDefault("Title", "").contains("edited under " + other),
                    "Editing the " + other + " tab should not change anything listed under " + CURRENCY);
        }
    }
}
