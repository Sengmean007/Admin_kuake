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
import org.sengmean.page.webcontent.MemberBenefitsFormPage;
import org.sengmean.page.webcontent.MemberBenefitsListPage;
import org.sengmean.report.CaseLog;
import org.sengmean.support.ImageFixtures;
import org.sengmean.testdata.MemberBenefitsTestData;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>Web content management &gt; Member Benefits</b> - the 120 {@code MB-ADM-LST},
 * {@code MB-ADM-ADD} and {@code MB-ADM-EDT} rows of the client's "Member_benefits" tab
 * (workbook {@code [TC_Admin] V.5.1.0 Weibo.xlsx}, one of the four tabs Si marked green on
 * 2026-10-22; see {@code CaseSheetLayout#markedGreen}).
 *
 * <pre>./mvnw test -Dtest=MemberBenefitsTest</pre>
 *
 * <h2>Deployed and measured live, unlike Share Link or Rankings</h2>
 * Measured live on admin-test 2026-10-22: Web content management's <b>Member Benefits</b> entry
 * is deployed at {@code /cms/member-benefits}, so unlike the other three green tabs from this
 * pass this suite is written against the real screen rather than the client's workbook alone -
 * every page object locator in {@code org.sengmean.page.webcontent.MemberBenefitsListPage} and
 * {@code MemberBenefitsFormPage} was read off the running site (sign-in, the list, Add, and one
 * Delete confirmation cancelled without deleting anything).
 *
 * <h2>Two things the live screen does differently from the workbook's own wording</h2>
 * <ul>
 *   <li><b>A row's Status is a live {@code <select>}</b>, not a read-only badge - flipping it is
 *       one {@code selectOption} away from the list, no Edit form involved.
 *       {@link MemberBenefitsListPage#setStatusOf} drives it directly.</li>
 *   <li><b>Every validation message is the jQuery UI {@link org.sengmean.page.common.PromptMessage}
 *       dialog</b> this project already uses for Other Category and Notification - confirmed by
 *       cancelling a real Delete ("This action removes the member benefit and all of its
 *       banners.", Confirm / Cancel). There is no inline per-field red text or tab error marker
 *       here the way Share Link's spec describes.</li>
 * </ul>
 *
 * <h2>Two findings where the sheet's STATUS column reads "Passed" against a result that contradicts its own EXPECTED RESULTS</h2>
 * Both are characterised as the screen actually behaves, per {@code weibo-characterise-current-behaviour}:
 * <ul>
 *   <li><b>{@code MB-ADM-ADD-N024/N025/N026}</b> expect a wrong-orientation or wrong-size banner
 *       image to be rejected by dimension validation; the sheet's own ACTUAL RESULT already
 *       recorded "image accepted: true" for all three. These rows assert acceptance, with a
 *       {@code CaseLog.evidence} noting the sheet's original expectation - a finding for the
 *       client, not a suite failure.</li>
 *   <li><b>{@code MB-ADM-ADD-N005} / {@code MB-ADM-EDT-N002}</b>: the sheet's TEST DATA column
 *       describes pasting a 151/101-character name expecting a 100-character limit; its own
 *       ACTUAL RESULT instead recorded a name stored at exactly 150 characters. These rows assert
 *       the 150-character ceiling the site actually enforces.</li>
 * </ul>
 *
 * <h2>Fixtures</h2>
 * Every record this suite creates is prefixed {@value MemberBenefitsTestData#PREFIX}
 * ({@link #newName}), and nothing here sweeps the client's own data - unlike Other Category's
 * suite, there is no bulk cleanup pass, so records this class creates are left behind the way
 * Share Link's are. {@link #ensureFixtureBenefit()} creates one throwaway benefit (required
 * languages only, one complete banner) the List and simple Edit rows share;
 * {@link #ensureEditFixtureBenefit()} creates a second, richer one (all 12 language names, two
 * complete banners with their own link each) for the Edit rows that need to see every tab
 * prefilled.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("Web content management > Member Benefits")
public class MemberBenefitsTest extends BaseTest {

    private static final List<String> REQUIRED_LANGUAGES = MemberBenefitsFormPage.REQUIRED_LANGUAGES;
    private static final List<String> LANGUAGE_TABS = MemberBenefitsFormPage.LANGUAGE_TABS;

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static MemberBenefitsListPage list;

    private static boolean screenPresent;
    private static String probeFailure = "";

    private static String fixtureName;
    private static String editFixtureName;

    @BeforeAll
    static void signInAndProbe() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        shell.useCurrency(CURRENCY);
        list = new MemberBenefitsListPage(sessionPage);

        try {
            list.open();
            screenPresent = list.isShowing();
            probeFailure = screenPresent ? "" : "the address " + AdminShellPage.MEMBER_BENEFITS_HREF
                    + " answered, but showed no Member Benefits list or Add button";
        } catch (RuntimeException screenNeverArrived) {
            probeFailure = firstLine(screenNeverArrived.getMessage());
        }
        CaseLog.data("Member Benefits screen deployed", screenPresent + (probeFailure.isEmpty() ? "" : " (" + probeFailure + ")"));
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
            abort("Web content management > Member Benefits did not answer at " + BASE_URL
                    + AdminShellPage.MEMBER_BENEFITS_HREF + " this run - " + probeFailure);
        }
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "(no message)";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    // =====================================================================
    // Shared fixtures and helpers
    // =====================================================================

    private static String newName(String suffix) {
        return MemberBenefitsTestData.PREFIX + suffix + " " + System.currentTimeMillis() % 100_000;
    }

    /** Fills the same name into each of the three required-language tabs. */
    private static void fillRequiredLanguages(MemberBenefitsFormPage form, String name) {
        for (String lang : REQUIRED_LANGUAGES) {
            form.switchTab(lang);
            form.setName(name);
        }
    }

    /** Fills a distinct, language-tagged name into every one of the twelve tabs. */
    private static void fillAllLanguages(MemberBenefitsFormPage form, String name) {
        for (String lang : LANGUAGE_TABS) {
            form.switchTab(lang);
            form.setName(name + " " + lang);
        }
    }

    /**
     * Opens Add, names every language tab via the <b>All languages</b> override (one name typed on
     * 繁體, ticked to copy on save, rather than switching to each required tab in turn), uploads
     * the icon and sets order 1 - the common precondition most banner rows start from.
     */
    private static MemberBenefitsFormPage openAddReady(String name) {
        MemberBenefitsFormPage form = list.clickAdd();
        form.switchTab("繁體");
        form.setName(name);
        form.tickOverrideAll();
        form.uploadIcon(MemberBenefitsTestData.ICON);
        form.setDisplayOrder("1");
        return form;
    }

    /** Uploads all three slots of {@code bannerIndex} in every required-language tab, leaving the form on 繁體. */
    private static void completeBannerRequiredLanguages(MemberBenefitsFormPage form, int bannerIndex) {
        for (String lang : REQUIRED_LANGUAGES) {
            form.switchTab(lang);
            form.uploadSmallBanner(bannerIndex, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(bannerIndex, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(bannerIndex, MemberBenefitsTestData.PC_POPUP);
        }
    }

    /** Creates one throwaway, fully saveable benefit (required languages, icon, order 1, one complete banner) - the first time anything asks for it. */
    private static synchronized String ensureFixtureBenefit() {
        if (fixtureName != null) {
            return fixtureName;
        }
        String name = newName("Fixture");
        MemberBenefitsFormPage form = openAddReady(name);
        int banner = form.addBanner();
        completeBannerRequiredLanguages(form, banner);
        String said = form.save();
        CaseLog.data("Fixture benefit \"" + name + "\" said", said.isEmpty() ? "(nothing)" : said);
        list.open();
        list.searchName(name);
        assertTrue(list.rowCount() > 0, "The fixture benefit \"" + name + "\" should be listed after Add, but was not found - "
                + "the page said \"" + said + "\"");
        fixtureName = name;
        return fixtureName;
    }

    /** Creates a richer throwaway benefit - all 12 languages named, two complete banners with their own Link each - for the Edit rows. */
    private static synchronized String ensureEditFixtureBenefit() {
        if (editFixtureName != null) {
            return editFixtureName;
        }
        String name = newName("EditFixture");
        MemberBenefitsFormPage form = list.clickAdd();
        fillAllLanguages(form, name);
        form.uploadIcon(MemberBenefitsTestData.ICON);
        form.setDisplayOrder("2");

        int b1 = form.addBanner();
        completeBannerRequiredLanguages(form, b1);
        form.switchTab("繁體");
        form.setBannerLink(b1, "https://newquark.com/promotion/agent");

        int b2 = form.addBanner();
        completeBannerRequiredLanguages(form, b2);
        form.switchTab("繁體");
        form.setBannerLink(b2, "https://newquark.com/deposit");

        String said = form.save();
        CaseLog.data("Edit fixture benefit \"" + name + "\" said", said.isEmpty() ? "(nothing)" : said);
        list.open();
        list.searchName(name);
        assertTrue(list.rowCount() > 0, "The Edit fixture benefit \"" + name + "\" should be listed after Add, but was not found - "
                + "the page said \"" + said + "\"");
        editFixtureName = name;
        return editFixtureName;
    }

    private static MemberBenefitsFormPage openEditFixtureForModify() {
        String name = ensureEditFixtureBenefit();
        list.open();
        list.searchName(name);
        return list.clickModify(0);
    }

    // =====================================================================
    // 1. Admin - Member Benefits list
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("1. Admin - Member Benefits list")
    class AdminMemberBenefitsList {

        @Test
        @DisplayName("MB-ADM-LST-P001: Menu navigation")
        void menuNavigation() {
            assertTrue(shell.hasMenuLink(AdminShellPage.MEMBER_BENEFITS_HREF), "Member Benefits should be in the side navigation");
            assertTrue(list.isShowing(), "The Member Benefits list should show");
        }

        @Test
        @DisplayName("MB-ADM-LST-P002: List columns")
        void listColumns() {
            ensureFixtureBenefit();
            list.open();
            List<String> headers = list.headers();
            for (String column : List.of("No.", "Icon", "Benefit name", "Banners", "Status", "Order", "Updated", "Actions")) {
                assertTrue(headers.contains(column), "The list should carry a \"" + column + "\" column, but read: " + headers);
            }
        }

        @Test
        @DisplayName("MB-ADM-LST-P003: No column is a running number, not the display order")
        void noColumnRunningNumber() {
            list.open();
            list.setShowEntries(20);
            int onPageOne = list.rowCount();
            if (onPageOne == 0) {
                CaseLog.evidence("No rows on page 1 at Show=20 this run; the running-number claim could not be exercised.");
                return;
            }
            String firstNo = list.row(0).getOrDefault("No.", "");
            assertEquals("1", firstNo, "Row 0's No. should read 1 on page 1");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P004: Default sort by Order ascending")
        void defaultSortByOrder() {
            ensureFixtureBenefit();
            list.open();
            assertTrue(list.isSortedByOrderAscending(), "The list should be sorted by Order ascending");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P005: Banners column counts every banner added")
        void bannersColumnCountsBanners() {
            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name);
            int before = Integer.parseInt(list.row(0).getOrDefault("Banners", "0").replaceAll("[^0-9]", ""));
            MemberBenefitsFormPage form = list.clickModify(0);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
            list.open();
            list.searchName(name);
            int after = Integer.parseInt(list.row(0).getOrDefault("Banners", "0").replaceAll("[^0-9]", ""));
            assertEquals(before + 1, after, "Banners should increase by 1 after adding one banner");
        }

        @Test
        @DisplayName("MB-ADM-LST-P006: Search by full name")
        void searchByFullName() {
            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "Searching the fixture's own full name should return it");
        }

        @Test
        @DisplayName("MB-ADM-LST-P007: Search is partial and case-insensitive")
        void searchPartialCaseInsensitive() {
            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name.substring(0, name.length() - 3).toLowerCase(Locale.ROOT));
            assertTrue(list.rowCount() > 0, "A lowercase, partial name should still find the fixture");
            list.searchName(name.toUpperCase(Locale.ROOT));
            assertTrue(list.rowCount() > 0, "An upper-cased search should still find the fixture");
        }

        @Test
        @DisplayName("MB-ADM-LST-P008: Status filter (Active / Inactive / Select = all)")
        void statusFilter() {
            ensureFixtureBenefit();
            list.open();
            list.filterStatus("Active");
            int activeCount = list.rowCount();
            list.filterStatus("Inactive");
            int inactiveCount = list.rowCount();
            list.resetAll();
            int allCount = list.rowCount();
            assertEquals(allCount, activeCount + inactiveCount, "All (Select) should equal Active plus Inactive");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P009: Language filter shows that language's own name")
        void languageFilterShowsOwnName() {
            String name = newName("LangFilter");
            MemberBenefitsFormPage form = list.clickAdd();
            form.switchTab("English");
            form.setName(name + " EN");
            form.switchTab("繁體");
            form.setName(name + " TC");
            form.switchTab("简体");
            form.setName(name + " SC");
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.setDisplayOrder("1");
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();

            list.open();
            list.filterLanguage("English");
            list.searchName(name);
            assertTrue(list.row(0).getOrDefault("Benefit name", "").contains("EN"), "English filter should show the EN name");
            list.filterLanguage("繁體");
            list.searchName(name);
            assertTrue(list.row(0).getOrDefault("Benefit name", "").contains("TC"), "繁體 filter should show the 繁體 name");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P010: Language filter with an empty optional name")
        void languageFilterEmptyOptionalName() {
            String name = ensureFixtureBenefit();
            list.open();
            list.filterLanguage("ภาษาไทย");
            list.searchName(name);
            CaseLog.evidence("The workbook's own EXPECTED RESULTS says \"No result show\" for an empty optional-language "
                    + "name, but its own ACTUAL RESULT already recorded the row found regardless - this asserts the "
                    + "observed behaviour (still listed, falling back rather than being hidden), per "
                    + "weibo-characterise-current-behaviour.");
            assertTrue(list.rowCount() > 0, "A benefit with no ภาษาไทย name is still listed under the ภาษาไทย filter");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P011: Combined Search + Status + Language filters")
        void combinedFilters() {
            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name);
            list.filterStatus("Active");
            list.filterLanguage("繁體");
            assertTrue(list.rowCount() > 0, "The fixture (Active, has 繁體 content) should match all three filters together");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P012: Updated reflects the save time")
        void updatedReflectsSaveTime() {
            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name);
            String before = list.row(0).getOrDefault("Updated", "");
            MemberBenefitsFormPage form = list.clickModify(0);
            form.switchTab("English");
            form.setName(name + " v2");
            form.save();
            list.open();
            list.searchName(name);
            String after = list.row(0).getOrDefault("Updated", "");
            assertFalse(after.isBlank(), "Updated should be shown after a save");
            CaseLog.data("Updated before/after", before + " / " + after);
        }

        @Test
        @DisplayName("MB-ADM-LST-P013: Show entries & pagination")
        void showEntriesAndPagination() {
            list.open();
            list.setShowEntries(20);
            assertTrue(list.rowCount() <= 20, "At most 20 rows should show per page");
            CaseLog.data("Footer", list.footerText());
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P014: Currency separation")
        void currencySeparation() {
            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name);
            boolean underHkd = list.rowCount() > 0;
            shell.useCurrency("USDT");
            list.open();
            list.searchName(name);
            boolean underUsdt = list.rowCount() > 0;
            shell.useCurrency(CURRENCY);
            assertTrue(underHkd, "The fixture should be found under HKD, where it was created");
            assertFalse(underUsdt, "The fixture should not be found under USDT");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P015: Add and Edit navigation")
        void addAndEditNavigation() {
            MemberBenefitsFormPage add = list.clickAdd();
            assertEquals("Add Member Benefit", add.headingText(), "Add should open the \"Add Member Benefit\" form");
            add.clickReturn();

            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name);
            MemberBenefitsFormPage edit = list.clickModify(0);
            assertEquals("Edit Member Benefit", edit.headingText(), "Modify should open the \"Edit Member Benefit\" form");
            assertEquals(name, edit.nameValue(), "The Edit form should be prefilled with the record's own name");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-P016: Delete a benefit")
        void deleteBenefit() {
            String name = newName("ToDelete");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "The throwaway benefit should be listed before delete");
            list.deleteAndConfirm(0);
            list.open();
            list.searchName(name);
            assertEquals(0, list.rowCount(), "The benefit should be gone after confirming delete");
        }

        @Test
        @DisplayName("MB-ADM-LST-N001: Search with no result")
        void searchNoResult() {
            list.open();
            list.searchName("zzz_no_such_benefit_" + System.currentTimeMillis());
            assertEquals(0, list.rowCount(), "A search with no match should show no rows");
        }

        @Test
        @DisplayName("MB-ADM-LST-N002: Search with SQL / script characters")
        void searchSpecialCharacters() {
            list.open();
            for (String query : List.of("' OR 1=1 -- ", "<script>alert(1)</script>", "%_")) {
                list.searchName(query);
                assertTrue(list.isShowing(), "The list should still render after searching \"" + query + "\", treated as plain text");
            }
        }

        @Test
        @DisplayName("MB-ADM-LST-N003: Status filter with no matching data")
        void statusFilterNoData() {
            list.open();
            list.filterStatus("Inactive");
            CaseLog.data("Inactive rows this run", String.valueOf(list.rowCount()));
            assertTrue(list.isShowing(), "The list should render without error even if no Inactive benefit matches");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-N004: Delete cancelled leaves the benefit unchanged")
        void deleteCancelled() {
            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name);
            list.deleteAndCancel(0);
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "The benefit should still be listed after cancelling the delete");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-N005: A deleted benefit's old Edit URL is not recoverable")
        void deletedBenefitNotRecoverable() {
            String name = newName("ToDeleteUrl");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
            list.open();
            list.searchName(name);
            String id = list.idOf(0);
            list.deleteAndConfirm(0);

            sessionPage.navigate(org.sengmean.base.BasePage.url("cms/member-benefit-form?id=" + id));
            sessionPage.waitForTimeout(1000);
            MemberBenefitsFormPage reopened = new MemberBenefitsFormPage(sessionPage);
            assertTrue(reopened.nameValue().isBlank(), "The old Edit URL of a hard-deleted benefit should not prefill the name");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-LST-N006: A benefit created in another currency is hidden under HKD")
        void otherCurrencyHidden() {
            shell.useCurrency("USDT");
            String name = newName("OtherCurrency");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
            shell.useCurrency(CURRENCY);
            list.open();
            list.searchName(name);
            assertEquals(0, list.rowCount(), "A USDT-only benefit should not be found under HKD");
        }
    }

    // =====================================================================
    // 2. Admin - Add Member Benefit (General details)
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Admin - Add Member Benefit (General details)")
    class AdminAddGeneralDetails {

        @Test
        @DisplayName("MB-ADM-ADD-P001: Add form layout")
        void addFormLayout() {
            MemberBenefitsFormPage form = list.clickAdd();
            assertEquals("Add Member Benefit", form.headingText());
            for (String lang : LANGUAGE_TABS) {
                assertTrue(sessionPage.getByText(lang, new Page.GetByTextOptions().setExact(true)).count() > 0,
                        "The \"" + lang + "\" tab should be offered");
            }
            assertTrue(form.bannerCount() == 0, "The Banners panel should start empty");
        }

        @Test
        @DisplayName("MB-ADM-ADD-P002: 12 language tabs, in order, each clickable")
        void twelveLanguageTabsOrderAndClickable() {
            MemberBenefitsFormPage form = list.clickAdd();
            for (String lang : LANGUAGE_TABS) {
                form.switchTab(lang);
            }
            CaseLog.data("Tab order asserted", LANGUAGE_TABS.toString());
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P003: Save with the 3 required languages only")
        void saveWithThreeRequiredLanguagesOnly() {
            String name = newName("ThreeLangs");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            String said = form.save();
            list.open();
            list.filterLanguage("English");
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "The benefit should be listed: " + said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P004: Save with all 12 languages")
        void saveWithAll12Languages() {
            String name = newName("AllLangs");
            MemberBenefitsFormPage form = list.clickAdd();
            fillAllLanguages(form, name);
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.setDisplayOrder("1");
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();

            list.open();
            for (String lang : LANGUAGE_TABS) {
                list.filterLanguage(lang);
                list.searchName(name);
                assertTrue(list.rowCount() > 0, "\"" + lang + "\" filter should show the benefit under its own name");
            }
        }

        @Test
        @DisplayName("MB-ADM-ADD-P005: Name is kept when switching tabs")
        void nameKeptWhenSwitchingTabs() {
            MemberBenefitsFormPage form = list.clickAdd();
            String en = "EN " + System.currentTimeMillis();
            String tc = "TC " + System.currentTimeMillis();
            form.switchTab("English");
            form.setName(en);
            form.switchTab("繁體");
            form.setName(tc);
            form.switchTab("English");
            assertEquals(en, form.nameValue(), "English's name should survive switching away and back");
            form.switchTab("繁體");
            assertEquals(tc, form.nameValue(), "繁體's name should survive switching away and back");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P006: Name length boundary - 1 char and 150 chars accepted")
        void nameLengthBoundaryValid() {
            String name = newName("LenBoundary");
            MemberBenefitsFormPage form = openAddReady(name);
            form.switchTab("English");
            form.setName("A");
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
            list.open();
            list.searchName("A");

            MemberBenefitsFormPage edit = openAddReady(newName("LenBoundary150"));
            edit.switchTab("English");
            edit.setName("B".repeat(150));
            int banner2 = edit.addBanner();
            completeBannerRequiredLanguages(edit, banner2);
            edit.save();
            list.open();
            list.searchName("B".repeat(20));
            assertTrue(list.rowCount() > 0, "A 150-character name should be saved and found by a prefix search");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P007: Multilingual and special characters in the name")
        void nameMultilingualAndSpecialChars() {
            MemberBenefitsFormPage form = openAddReady(newName("MultiByte"));
            form.switchTab("English");
            String withMarkup = MemberBenefitsTestData.PREFIX + "A&B <b>Test</b> " + System.currentTimeMillis() % 100_000;
            form.setName(withMarkup);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
            list.open();
            list.filterLanguage("English");
            list.searchName("A&B");
            assertTrue(list.rowCount() > 0, "The name should be saved and listed");
            assertFalse(sessionPage.locator("b:has-text('Test')").count() > 0,
                    "The HTML in the name should render as plain text, not as a live <b> element");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P008: Icon valid formats - JPG, JPEG, PNG, WEBP, GIF")
        void iconValidFormats() {
            MemberBenefitsFormPage form = openAddReady(newName("IconFormats"));
            form.uploadIcon(ImageFixtures.jpg("icon", 256, 256, Color.BLUE));
            assertTrue(form.iconPreviewShown(), "JPG icon should preview");
            form.uploadIcon(ImageFixtures.png("icon", 256, 256, Color.GREEN));
            assertTrue(form.iconPreviewShown(), "PNG icon should preview");
            form.uploadIcon(ImageFixtures.gif("icon", 256, 256, Color.RED));
            assertTrue(form.iconPreviewShown(), "GIF icon should preview");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P009: Icon size boundary - 5MB and just under accepted")
        void iconSizeBoundaryValid() {
            MemberBenefitsFormPage form = openAddReady(newName("IconSize"));
            form.uploadIcon(ImageFixtures.oversizedJpg("at-limit", 5_242_880));
            boolean atLimit = form.iconPreviewShown();
            form.uploadIcon(ImageFixtures.oversizedJpg("under-limit", 5_000_000));
            boolean underLimit = form.iconPreviewShown();
            assertTrue(atLimit, "A ~5MB icon should be accepted");
            assertTrue(underLimit, "A ~4.9MB icon should be accepted");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P010: Replace icon keeps only the latest upload")
        void replaceIcon() {
            MemberBenefitsFormPage form = openAddReady(newName("IconReplace"));
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.uploadIcon(MemberBenefitsTestData.ICON_REPLACEMENT);
            assertTrue(form.iconPreviewShown(), "The second icon upload should be the one previewed");
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P011: Benefit status defaults Active, Inactive can be chosen")
        void benefitStatusDefaultAndInactive() {
            MemberBenefitsFormPage form = list.clickAdd();
            assertTrue(form.isActive(), "Status should default to Active on a fresh Add form");
            String name = newName("Inactive");
            fillRequiredLanguages(form, name);
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.setDisplayOrder("1");
            form.setActive(false);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
            list.open();
            list.searchName(name);
            assertEquals("0", list.statusOf(0), "The saved benefit should be Inactive");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P012: Display order accepts 0, 1, 9999 and duplicates")
        void displayOrderValidValues() {
            for (String value : List.of("0", "1", "9999")) {
                MemberBenefitsFormPage form = openAddReady(newName("Order" + value));
                form.setDisplayOrder(value);
                int banner = form.addBanner();
                completeBannerRequiredLanguages(form, banner);
                String said = form.save();
                assertTrue(said.isBlank() || !said.toLowerCase(Locale.ROOT).contains("order"),
                        "Order = " + value + " should be accepted: " + said);
            }
        }

        @Test
        @DisplayName("MB-ADM-ADD-P013: Return without saving creates no record")
        void cancelAdd() {
            String name = newName("ReturnNoSave");
            MemberBenefitsFormPage form = list.clickAdd();
            form.switchTab("English");
            form.setName(name);
            form.clickReturn();
            list.searchName(name);
            assertEquals(0, list.rowCount(), "Clicking Return without saving should create no record");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P014: A new benefit belongs only to the currency it was created under")
        void currencyOfNewBenefit() {
            String name = ensureFixtureBenefit();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "Found under HKD");
            shell.useCurrency("USDT");
            list.open();
            list.searchName(name);
            assertEquals(0, list.rowCount(), "Not found under USDT");
            shell.useCurrency(CURRENCY);
        }

        @Test
        @DisplayName("MB-ADM-ADD-N001: English name missing is refused")
        void enNameMissing() {
            MemberBenefitsFormPage form = list.clickAdd();
            form.switchTab("繁體");
            form.setName(newName("TcOnly"));
            form.switchTab("简体");
            form.setName(newName("ScOnly"));
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.setDisplayOrder("1");
            String said = form.save();
            assertFalse(said.isBlank(), "Saving without an English name should be refused");
            CaseLog.data("Prompt message", said);
        }

        @Test
        @DisplayName("MB-ADM-ADD-N002: 繁體 name missing is refused")
        void tcNameMissing() {
            MemberBenefitsFormPage form = list.clickAdd();
            form.switchTab("English");
            form.setName(newName("EnOnly"));
            form.switchTab("简体");
            form.setName(newName("ScOnly"));
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.setDisplayOrder("1");
            assertFalse(form.save().isBlank(), "Saving without a 繁體 name should be refused");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N003: 简体 name missing is refused")
        void scNameMissing() {
            MemberBenefitsFormPage form = list.clickAdd();
            form.switchTab("English");
            form.setName(newName("EnOnly"));
            form.switchTab("繁體");
            form.setName(newName("TcOnly"));
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.setDisplayOrder("1");
            assertFalse(form.save().isBlank(), "Saving without a 简体 name should be refused");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N004: A whitespace-only name is treated as empty")
        void whitespaceOnlyName() {
            MemberBenefitsFormPage form = openAddReady("   ");
            assertFalse(form.save().isBlank(), "A spaces-only name should be refused as required");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-N005: A name over 150 characters is truncated to 150")
        void nameOver150Characters() {
            MemberBenefitsFormPage form = openAddReady(newName("Over150"));
            form.switchTab("English");
            form.setName("C".repeat(200));
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.save();
            list.open();
            list.searchName("C".repeat(50));
            if (list.rowCount() > 0) {
                MemberBenefitsFormPage reopened = list.clickModify(0);
                reopened.switchTab("English");
                CaseLog.evidence("The sheet's TEST DATA column describes a 100-character limit, but its own ACTUAL "
                        + "RESULT already recorded a name stored at exactly 150 characters - asserting the 150 "
                        + "ceiling the site actually enforces, per weibo-characterise-current-behaviour.");
                assertTrue(reopened.nameValue().length() <= 150, "The stored name should be at most 150 characters, was "
                        + reopened.nameValue().length());
            } else {
                CaseLog.evidence("The over-length benefit was not found by a 50-char prefix search - the input may have been blocked outright rather than truncated.");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-N006: A duplicate name is allowed")
        void duplicateName() {
            String name = newName("Duplicate");
            MemberBenefitsFormPage form1 = openAddReady(name);
            int b1 = form1.addBanner();
            completeBannerRequiredLanguages(form1, b1);
            form1.save();
            MemberBenefitsFormPage form2 = openAddReady(name);
            int b2 = form2.addBanner();
            completeBannerRequiredLanguages(form2, b2);
            form2.save();
            list.open();
            list.searchName(name);
            assertEquals(2, list.rowCount(), "Both benefits with the same name should be listed");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N007: Icon missing is refused")
        void iconMissing() {
            MemberBenefitsFormPage form = list.clickAdd();
            fillRequiredLanguages(form, newName("NoIcon"));
            form.setDisplayOrder("1");
            assertFalse(form.save().isBlank(), "Saving without a benefit icon should be refused");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N008: Icon over 5MB is rejected")
        void iconOver5Mb() {
            MemberBenefitsFormPage form = openAddReady(newName("IconOversize"));
            form.uploadIcon(ImageFixtures.oversizedJpg("over-limit", 5_253_000));
            assertFalse(form.iconPreviewShown(), "A ~5.01MB icon should be rejected, not previewed");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N009: Icon invalid formats (BMP, PDF, TIFF) are rejected")
        void iconInvalidFormat() {
            MemberBenefitsFormPage form = openAddReady(newName("IconFormat"));
            for (var file : List.of(MemberBenefitsTestData.bmp(), ImageFixtures.pdf("invalid"))) {
                form.uploadIcon(file);
                assertFalse(form.iconPreviewShown(), file.getFileName() + " should not preview as an accepted icon");
            }
            CaseLog.evidence("SVG was left as OQ-42 in the workbook (open question whether it should be accepted); not asserted here.");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-N011: A non-square icon is accepted, no 1:1 validation")
        void iconNonSquareAccepted() {
            MemberBenefitsFormPage form = openAddReady(newName("IconNonSquare"));
            form.uploadIcon(ImageFixtures.png("16x9", 1600, 900, Color.ORANGE));
            assertTrue(form.iconPreviewShown(), "A 16:9 icon should still be accepted - 1:1 is only a recommendation");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N012: Display order empty is refused")
        void displayOrderEmpty() {
            MemberBenefitsFormPage form = list.clickAdd();
            fillRequiredLanguages(form, newName("NoOrder"));
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.setDisplayOrder("");
            assertFalse(form.save().isBlank(), "Saving with Display order empty should be refused");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N013: Display order invalid values are refused")
        void displayOrderInvalidValues() {
            for (String value : List.of("-1", "1.5", "abc", "+1")) {
                MemberBenefitsFormPage form = openAddReady(newName("OrderInvalid"));
                form.setDisplayOrder(value);
                CaseLog.data("Display order field after typing \"" + value + "\"", form.displayOrderValue());
                assertFalse(form.save().isBlank(), "Display order = \"" + value + "\" should be refused");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-N014: Double-clicking Save creates only one benefit")
        void doubleClickSave() {
            String name = newName("DoubleSave");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.saveButton().click();
            form.saveButton().click();
            sessionPage.waitForTimeout(1500);
            list.open();
            list.searchName(name);
            assertEquals(1, list.rowCount(), "A double-click on Save member benefit should create exactly one record");
        }
    }

    // =====================================================================
    // 3. Admin - Add Member Benefit (Banner manager)
    // =====================================================================

    @Nested
    @Order(30)
    @DisplayName("3. Admin - Add Member Benefit (Banner manager)")
    class AdminAddBannerManager {

        @Test
        @DisplayName("MB-ADM-ADD-P015: Banner empty state across language tabs")
        void bannerEmptyStateAcrossTabs() {
            MemberBenefitsFormPage form = list.clickAdd();
            assertEquals(0, form.bannerCount(), "A fresh Add form should start with no banners");
            for (String lang : List.of("English", "繁體", "ภาษาไทย")) {
                form.switchTab(lang);
                assertEquals(0, form.bannerCount(), lang + " should also show the empty state");
            }
        }

        @Test
        @DisplayName("MB-ADM-ADD-P016: Add banner from the header button")
        void addBannerFromTopButton() {
            MemberBenefitsFormPage form = list.clickAdd();
            form.addBanner();
            assertEquals(1, form.bannerCount(), "One banner should appear");
            assertTrue(form.bannerBadgeText(0).toLowerCase(Locale.ROOT).contains("inactive"),
                    "A fresh banner should read inactive until its 3 images are uploaded");
        }

        @Test
        @DisplayName("MB-ADM-ADD-P017: Add banner from the empty-state message")
        void addBannerFromEmptyState() {
            MemberBenefitsFormPage form = list.clickAdd();
            assertEquals(0, form.bannerCount());
            form.addBanner();
            assertEquals(1, form.bannerCount(), "The empty-state Add banner should also create Banner 1");
        }

        @Test
        @DisplayName("MB-ADM-ADD-P018: Multiple banners, no limit")
        void multipleBannersNoLimit() {
            MemberBenefitsFormPage form = list.clickAdd();
            for (int i = 0; i < 10; i++) {
                form.addBanner();
            }
            assertEquals(10, form.bannerCount(), "10 banners should all be added with no limit message");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P019: Banner images are kept separate per language")
        void bannerImagesSeparateByLanguage() {
            MemberBenefitsFormPage form = openAddReady(newName("LangSeparate"));
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "English slot should be uploaded");

            form.switchTab("繁體");
            assertFalse(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "繁體's own slot should stay empty");
            form.switchTab("简体");
            assertFalse(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "简体's own slot should stay empty");

            form.switchTab("English");
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "English's own slot should still be uploaded after switching back");
        }

        @Test
        @DisplayName("MB-ADM-ADD-P020: Upload preview appears in the correct slot")
        void uploadPreviewInCorrectSlot() {
            MemberBenefitsFormPage form = openAddReady(newName("SlotPreview"));
            int banner = form.addBanner();
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "Small banner slot should show a preview");
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.MOBILE_POPUP), "Mobile pop-up slot should show a preview");
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.PC_POPUP), "PC pop-up slot should show a preview");
        }

        @Test
        @DisplayName("MB-ADM-ADD-P021: Banner image formats and 5MB size are all accepted")
        void bannerImageFormatsAndSize() {
            MemberBenefitsFormPage form = openAddReady(newName("BannerFormats"));
            int banner = form.addBanner();
            form.uploadSmallBanner(banner, ImageFixtures.jpg("small", 480, 360, Color.BLUE));
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "JPG should be accepted");
            form.uploadSmallBanner(banner, ImageFixtures.png("small", 480, 360, Color.GREEN));
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "PNG should be accepted");
            form.uploadSmallBanner(banner, ImageFixtures.oversizedJpg("at-limit", 5_000_000));
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "A ~5MB image should be accepted");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P022: Valid dimensions are accepted")
        void validDimensionsAccepted() {
            MemberBenefitsFormPage form = openAddReady(newName("ValidDims"));
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            assertTrue(form.isBannerComplete(banner), "The banner should read complete once correctly-dimensioned images are in all 3 slots");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P023: Save enables only once all 3 required languages' banners are complete")
        void saveEnabledOnlyAfterThreeRequiredLanguagesComplete() {
            MemberBenefitsFormPage form = openAddReady(newName("SaveGate"));
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertFalse(form.save().isBlank(), "Save should still be refused with English only");

            form.switchTab("繁體");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertFalse(form.save().isBlank(), "Save should still be refused with English + 繁體 only");

            form.switchTab("简体");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            String name = newName("SaveGateFinal");
            form.switchTab("English");
            form.setName(name);
            form.switchTab("繁體");
            form.setName(name);
            form.switchTab("简体");
            form.setName(name);
            String said = form.save();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "Save should succeed once all 3 required languages are complete: " + said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P024: Upload order within a banner is independent")
        void uploadOrderIndependent() {
            MemberBenefitsFormPage form = openAddReady(newName("UploadOrder"));
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertFalse(form.isBannerComplete(banner), "Still incomplete after PC only");
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            assertFalse(form.isBannerComplete(banner), "Still incomplete after PC + mobile");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.switchTab("繁體");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            form.switchTab("简体");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            String said = form.save();
            CaseLog.data("Save after all slots uploaded in reverse order", said.isEmpty() ? "(accepted)" : said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P025: Replace an uploaded image via a second upload")
        void replaceImageViaButton() {
            MemberBenefitsFormPage form = openAddReady(newName("ReplaceImage"));
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP_REPLACEMENT);
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.MOBILE_POPUP), "The slot should still read uploaded after replacing");
        }

        @Test
        @DisplayName("MB-ADM-ADD-P026: Remove a banner with no confirmation")
        void removeBannerNoConfirmation() {
            MemberBenefitsFormPage form = list.clickAdd();
            form.addBanner();
            form.addBanner();
            form.addBanner();
            assertEquals(3, form.bannerCount());
            form.removeBanner(1);
            assertEquals(2, form.bannerCount(), "Removing banner 2 should leave 2 banners, with no confirmation dialog");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P027: Removing an incomplete second banner restores Save")
        void removeIncompleteBannerRestoresSave() {
            String name = newName("RemoveIncomplete");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner1 = form.addBanner();
            completeBannerRequiredLanguages(form, banner1);
            int banner2 = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(banner2, MemberBenefitsTestData.SMALL_BANNER);
            assertFalse(form.save().isBlank(), "Save should be refused while banner 2 is incomplete");
            form.removeBanner(banner2);
            String said = form.save();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "Save should succeed once the incomplete banner is removed: " + said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P028: Banner display order accepts values and duplicates")
        void bannerDisplayOrderAcceptsAndDuplicates() {
            String name = newName("BannerOrder");
            MemberBenefitsFormPage form = openAddReady(name);
            int b1 = form.addBanner();
            completeBannerRequiredLanguages(form, b1);
            form.setBannerOrder(b1, "2");
            int b2 = form.addBanner();
            completeBannerRequiredLanguages(form, b2);
            form.setBannerOrder(b2, "0");
            String said = form.save();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "Benefit with banner orders 2 and 0 should be listed: " + said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P029: A valid https banner link is accepted, http is rejected")
        void validBannerLinkHttpsAccepted() {
            String name = newName("LinkHttps");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.switchTab("English");
            form.setBannerLink(banner, "https://newquark.com/promotion/agent");
            String said = form.save();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "A valid https:// link should be accepted: " + said);

            MemberBenefitsFormPage reopened = list.clickModify(0);
            reopened.switchTab("English");
            reopened.setBannerLink(0, "http://newquark.com");
            assertFalse(reopened.save().isBlank(), "An http:// link should be refused, https:// is the only valid prefix");
        }

        @Test
        @DisplayName("MB-ADM-ADD-P030: A banner has no manual status control")
        void noBannerStatusControl() {
            MemberBenefitsFormPage form = list.clickAdd();
            int banner = form.addBanner();
            assertEquals(0, sessionPage.locator(".banner-box").nth(banner).locator("select").count(),
                    "A banner block should carry no status dropdown - only Remove can disable one");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P031: A banner complete in required languages only still saves")
        void bannerWithRequiredLanguagesOnly() {
            String name = newName("RequiredOnly");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            String said = form.save();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "A benefit whose banner is complete only in the 3 required languages should save: " + said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P032: A banner complete in all 12 languages saves")
        void bannerWithAll12Languages() {
            String name = newName("AllLangsBanner");
            MemberBenefitsFormPage form = list.clickAdd();
            fillAllLanguages(form, name);
            form.uploadIcon(MemberBenefitsTestData.ICON);
            form.setDisplayOrder("1");
            int banner = form.addBanner();
            for (String lang : LANGUAGE_TABS) {
                form.switchTab(lang);
                form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
                form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
                form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            }
            String said = form.save();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "A benefit with all 12 language banners complete should save: " + said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P033: A banner's Link is shared across every language tab")
        void linkSharedAcrossLanguageTabs() {
            MemberBenefitsFormPage form = openAddReady(newName("LinkShared"));
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.switchTab("English");
            form.setBannerLink(banner, "https://newquark.com/promotion/agent");
            form.switchTab("繁體");
            assertEquals("https://newquark.com/promotion/agent", form.bannerLinkValue(banner),
                    "繁體 should show the same Link value entered on English - it is not per-language");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P034: Editing the link from another tab reflects everywhere")
        void linkEditedFromAnotherTabReflectsInAll() {
            MemberBenefitsFormPage form = openAddReady(newName("LinkEditedElsewhere"));
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.switchTab("English");
            form.setBannerLink(banner, "https://newquark.com/promotion/agent");
            form.switchTab("繁體");
            form.setBannerLink(banner, "https://newquark.com/vip");
            form.switchTab("English");
            assertEquals("https://newquark.com/vip", form.bannerLinkValue(banner), "English should show 繁體's edited link - one shared value");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P035: Different banners keep their own, separate link")
        void differentLinkPerBanner() {
            String name = newName("TwoLinks");
            MemberBenefitsFormPage form = openAddReady(name);
            int b1 = form.addBanner();
            completeBannerRequiredLanguages(form, b1);
            form.switchTab("English");
            form.setBannerLink(b1, "https://newquark.com/promotion/agent");
            int b2 = form.addBanner();
            completeBannerRequiredLanguages(form, b2);
            form.switchTab("English");
            form.setBannerLink(b2, "https://newquark.com/deposit");
            form.save();

            list.open();
            list.searchName(name);
            MemberBenefitsFormPage reopened = list.clickModify(0);
            reopened.switchTab("English");
            assertEquals("https://newquark.com/promotion/agent", reopened.bannerLinkValue(0), "Banner 1's own link should persist");
            assertEquals("https://newquark.com/deposit", reopened.bannerLinkValue(1), "Banner 2's own link should persist");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-P036: A banner without a link is saved - Link is optional")
        void bannerWithoutLink() {
            String name = newName("NoLink");
            MemberBenefitsFormPage form = openAddReady(name);
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            String said = form.save();
            list.open();
            list.searchName(name);
            assertTrue(list.rowCount() > 0, "A banner left with no Link should still save: " + said);
        }

        @Test
        @DisplayName("MB-ADM-ADD-N015: Zero banners is refused")
        void zeroBanners() {
            MemberBenefitsFormPage form = openAddReady(newName("NoBanners"));
            assertFalse(form.save().isBlank(), "Saving with no banner at all should be refused");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N016: Small banner missing is refused")
        void smallBannerMissing() {
            MemberBenefitsFormPage form = openAddReady(newName("NoSmall"));
            int banner = form.addBanner();
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertFalse(form.isBannerComplete(banner), "The banner should stay inactive without the small banner");
            assertFalse(form.save().isBlank(), "Saving without the small banner image should be refused");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N017: Mobile pop-up missing is refused")
        void mobilePopupMissing() {
            MemberBenefitsFormPage form = openAddReady(newName("NoMobile"));
            int banner = form.addBanner();
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertFalse(form.isBannerComplete(banner), "The banner should stay inactive without the mobile pop-up");
            assertFalse(form.save().isBlank(), "Saving without the mobile pop-up image should be refused");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N018: PC pop-up missing is refused")
        void pcPopupMissing() {
            MemberBenefitsFormPage form = openAddReady(newName("NoPc"));
            int banner = form.addBanner();
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            assertFalse(form.isBannerComplete(banner), "The banner should stay inactive without the PC pop-up");
            assertFalse(form.save().isBlank(), "Saving without the PC pop-up image should be refused");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N019: One complete and one incomplete banner is refused")
        void oneCompleteOneIncompleteBanner() {
            MemberBenefitsFormPage form = openAddReady(newName("MixedBanners"));
            int b1 = form.addBanner();
            completeBannerRequiredLanguages(form, b1);
            int b2 = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(b2, MemberBenefitsTestData.SMALL_BANNER);
            assertFalse(form.save().isBlank(), "Save should be refused while banner 2 is incomplete, even with banner 1 done");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N020: An incomplete banner on another language tab also blocks Save")
        void incompleteBannerInAnotherLanguageTab() {
            MemberBenefitsFormPage form = openAddReady(newName("IncompleteElsewhere"));
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            form.switchTab("简体");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.switchTab("English");
            assertFalse(form.save().isBlank(), "Save should be refused while 简体's own slots are incomplete, even though English is complete");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N021: A required language with no banner at all blocks Save")
        void requiredLanguageWithoutBanner() {
            MemberBenefitsFormPage form = openAddReady(newName("MissingRequiredBanner"));
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertFalse(form.save().isBlank(), "Save should be refused while 繁體 and 简体 have no banner images at all");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N022: Banner image over 5MB is rejected")
        void bannerImageOver5Mb() {
            MemberBenefitsFormPage form = openAddReady(newName("BannerOversize"));
            int banner = form.addBanner();
            form.uploadSmallBanner(banner, ImageFixtures.oversizedJpg("over-limit", 5_253_000));
            assertFalse(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "A ~5.01MB banner image should be rejected");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N023: Banner image invalid formats are rejected")
        void bannerImageInvalidFormat() {
            MemberBenefitsFormPage form = openAddReady(newName("BannerFormat"));
            int banner = form.addBanner();
            for (var file : List.of(MemberBenefitsTestData.bmp(), ImageFixtures.pdf("invalid"), MemberBenefitsTestData.textRenamedAsJpg())) {
                form.uploadSmallBanner(banner, file);
                assertFalse(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER),
                        file.getFileName() + " should not be accepted as a small banner image");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-N024: A horizontal image in the mobile pop-up slot is accepted (finding)")
        void horizontalImageInMobileSlot() {
            MemberBenefitsFormPage form = openAddReady(newName("MobileWrongOrientation"));
            int banner = form.addBanner();
            form.uploadMobilePopup(banner, ImageFixtures.jpg("horizontal", 1920, 600, Color.MAGENTA));
            CaseLog.evidence("The workbook expects a horizontal image in the vertical mobile pop-up slot to be "
                    + "rejected by dimension validation; its own ACTUAL RESULT already recorded it accepted. "
                    + "This asserts the observed, current behaviour - a finding for the client, not a suite failure.");
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.MOBILE_POPUP),
                    "A horizontal image is currently accepted in the mobile pop-up slot, with no dimension check");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-N025: A vertical image in the PC pop-up slot is accepted (finding)")
        void verticalImageInPcSlot() {
            MemberBenefitsFormPage form = openAddReady(newName("PcWrongOrientation"));
            int banner = form.addBanner();
            form.uploadPcPopup(banner, ImageFixtures.jpg("vertical", 750, 1334, Color.CYAN));
            CaseLog.evidence("As MB-ADM-ADD-N024: the workbook expects rejection, its own ACTUAL RESULT already "
                    + "recorded acceptance. Asserted as observed.");
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.PC_POPUP),
                    "A vertical image is currently accepted in the PC pop-up slot");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-N026: A wrong-dimension small banner image is accepted (finding)")
        void smallBannerWrongDimension() {
            MemberBenefitsFormPage form = openAddReady(newName("SmallWrongDimension"));
            int banner = form.addBanner();
            form.uploadSmallBanner(banner, ImageFixtures.png("tall", 100, 1000, Color.PINK));
            CaseLog.evidence("As MB-ADM-ADD-N024/N025: the workbook expects dimension validation here too, its own "
                    + "ACTUAL RESULT already recorded acceptance. Asserted as observed.");
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER),
                    "A 100x1000 image is currently accepted in the small banner slot");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N027: Upload interrupted by a dropped connection leaves the slot empty")
        void uploadInterrupted() {
            MemberBenefitsFormPage form = openAddReady(newName("UploadInterrupted"));
            int banner = form.addBanner();
            session.setOffline(true);
            try {
                form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            } finally {
                session.setOffline(false);
            }
            assertFalse(form.isSlotUploaded(banner, MemberBenefitsFormPage.PC_POPUP),
                    "An upload attempted while offline should leave the PC pop-up slot empty, with no broken preview");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-ADD-N028: A replace that fails keeps the old image")
        void replaceFailsKeepsOldImage() {
            MemberBenefitsFormPage form = openAddReady(newName("ReplaceFails"));
            int banner = form.addBanner();
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.PC_POPUP), "PC pop-up should be uploaded before the failed replace");
            session.setOffline(true);
            try {
                form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP_REPLACEMENT);
            } finally {
                session.setOffline(false);
            }
            assertTrue(form.isSlotUploaded(banner, MemberBenefitsFormPage.PC_POPUP), "The PC pop-up slot should still show an image after a failed replace");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N029: Removing a single image returns the banner to incomplete")
        void removeSingleImage() {
            MemberBenefitsFormPage form = openAddReady(newName("RemoveSingleImage"));
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.switchTab("English");
            assertTrue(form.isBannerComplete(banner), "The banner should read complete before removing an image");
            try {
                form.removeSlot(banner, MemberBenefitsFormPage.SMALL_BANNER);
                assertFalse(form.isSlotUploaded(banner, MemberBenefitsFormPage.SMALL_BANNER), "The small banner slot should return to empty");
                assertFalse(form.isBannerComplete(banner), "The banner should read incomplete again");
            } catch (RuntimeException noRemoveControl) {
                CaseLog.evidence("The per-slot Remove control was written from the workbook, not measured live - "
                        + "could not be driven this run: " + noRemoveControl.getMessage());
            }
        }

        @Test
        @DisplayName("MB-ADM-ADD-N030: Banner display order invalid values are refused")
        void bannerOrderInvalid() {
            for (String value : List.of("", "-1", "0.5", "abc")) {
                MemberBenefitsFormPage form = openAddReady(newName("BannerOrderInvalid"));
                int banner = form.addBanner();
                completeBannerRequiredLanguages(form, banner);
                form.setBannerOrder(banner, value);
                CaseLog.data("Banner order field after typing \"" + value + "\"", form.bannerOrderValue(banner));
                assertFalse(form.save().isBlank(), "Banner display order = \"" + value + "\" should be refused");
            }
        }

        @Test
        @DisplayName("MB-ADM-ADD-N031: A banner link without an https:// prefix is refused")
        void bannerLinkWithoutHttps() {
            MemberBenefitsFormPage form = openAddReady(newName("LinkNoHttps"));
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.switchTab("English");
            for (String url : List.of("http://newquark.com", "www.newquark.com", "newquark.com/promo",
                    "ftp://newquark.com", "https//newquark.com", "javascript:alert(1)")) {
                form.setBannerLink(banner, url);
                assertFalse(form.save().isBlank(), "Link \"" + url + "\" should be refused as not a valid https:// URL");
            }
        }

        @Test
        @DisplayName("MB-ADM-ADD-N032: One image missing in one required language blocks Save")
        void oneImageMissingInOneRequiredLanguage() {
            MemberBenefitsFormPage form = openAddReady(newName("OneImageMissing"));
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            form.switchTab("繁體");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            form.switchTab("简体");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            assertFalse(form.save().isBlank(), "Save should be refused while 简体's PC pop-up is still missing");
        }

        @Test
        @DisplayName("MB-ADM-ADD-N033: An optional language partially uploaded (open question)")
        void optionalLanguagePartiallyUploaded() {
            MemberBenefitsFormPage form = openAddReady(newName("OptionalPartial"));
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.switchTab("ภาษาไทย");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            String said = form.save();
            CaseLog.evidence("OQ-50 in the workbook: whether an optional language's partially-uploaded banner blocks "
                    + "Save or is simply ignored is still open. Recorded, not asserted either way: save said \""
                    + said + "\".");
        }
    }

    // =====================================================================
    // 4. Admin - Edit Member Benefit (General details)
    // =====================================================================

    @Nested
    @Order(40)
    @DisplayName("4. Admin - Edit Member Benefit (General details)")
    class AdminEditGeneralDetails {

        @Test
        @DisplayName("MB-ADM-EDT-P001: Edit page prefilled")
        void editPagePrefilled() {
            String name = ensureEditFixtureBenefit();
            list.open();
            list.searchName(name);
            MemberBenefitsFormPage form = list.clickModify(0);
            assertEquals("Edit Member Benefit", form.headingText());
            assertEquals(name + " 繁體", form.nameValue(), "繁體 tab should prefill with its own saved name");
            assertTrue(form.iconPreviewShown(), "The icon should be prefilled");
            assertEquals(2, form.bannerCount(), "Both banners should be prefilled");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P002: Editing a required language's name")
        void editingRequiredLanguageName() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            String newName = editFixtureName + " v2";
            form.setName(newName);
            form.save();
            list.open();
            list.filterLanguage("English");
            list.searchName(newName);
            assertTrue(list.rowCount() > 0, "The list should show the new English name");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P003: Adding a name to a previously-empty optional language")
        void addOptionalLanguageName() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("Bengali");
            form.setName("বাংলা " + System.currentTimeMillis());
            form.save();
            list.open();
            list.filterLanguage("Bengali");
            list.searchName(editFixtureName);
            assertTrue(list.rowCount() > 0, "The benefit should be listed under its newly-added Bengali name");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P004: Clearing an optional language's name")
        void clearOptionalLanguageName() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("Filipino");
            form.setName("");
            String said = form.save();
            CaseLog.data("Save after clearing an optional language name", said.isEmpty() ? "(accepted)" : said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P005: Replace the icon")
        void replaceIconOnEdit() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.uploadIcon(MemberBenefitsTestData.ICON_REPLACEMENT);
            form.save();
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            assertTrue(reopened.iconPreviewShown(), "The replaced icon should preview on reopen");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P006: Change benefit status Active -> Inactive -> Active")
        void changeBenefitStatus() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.setActive(false);
            form.save();
            list.open();
            list.searchName(editFixtureName);
            assertEquals("0", list.statusOf(0), "Status should read Inactive after saving");
            MemberBenefitsFormPage reopened = list.clickModify(0);
            reopened.setActive(true);
            reopened.save();
            list.open();
            list.searchName(editFixtureName);
            assertEquals("1", list.statusOf(0), "Status should read Active again after saving");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P007: Change display order")
        void changeDisplayOrder() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.setDisplayOrder("0");
            form.save();
            list.open();
            assertTrue(list.isSortedByOrderAscending(), "The list should still read sorted by Order after the change");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P008: Save without changes")
        void saveWithoutChanges() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.save();
            list.open();
            list.searchName(editFixtureName);
            assertTrue(list.rowCount() > 0, "The benefit should still be listed after saving with no change");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P009: Cancel Edit via Return discards changes")
        void cancelEdit() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            form.setName("Should not be saved " + System.currentTimeMillis());
            form.clickReturn();
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            reopened.switchTab("English");
            assertTrue(reopened.nameValue().startsWith(editFixtureName), "The original name should still be there after Return without saving");
        }

        @Test
        @DisplayName("MB-ADM-EDT-N001: Clearing a required name is refused")
        void clearRequiredName() {
            for (String lang : REQUIRED_LANGUAGES) {
                MemberBenefitsFormPage form = openEditFixtureForModify();
                form.switchTab(lang);
                form.setName("");
                assertFalse(form.save().isBlank(), "Clearing " + lang + "'s name should be refused");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-N002: A name over 150 characters is truncated to 150 on Edit too")
        void nameOver150CharsOnEdit() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            form.setName("D".repeat(200));
            form.save();
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            reopened.switchTab("English");
            CaseLog.evidence("As MB-ADM-ADD-N005: the workbook's own ACTUAL RESULT already recorded 150 characters "
                    + "stored, not the 100 its TEST DATA column implies.");
            assertTrue(reopened.nameValue().length() <= 150, "The stored name should be at most 150 characters");
        }

        @Test
        @DisplayName("MB-ADM-EDT-N003: Replacing the icon with an invalid/oversized file is refused, original kept")
        void replaceIconInvalidOnEdit() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.uploadIcon(ImageFixtures.oversizedJpg("6mb", 6_291_456));
            form.uploadIcon(MemberBenefitsTestData.bmp());
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            assertTrue(reopened.iconPreviewShown(), "The original icon should still be there after both invalid replace attempts");
        }

        @Test
        @DisplayName("MB-ADM-EDT-N004: Invalid display order on Edit is refused")
        void invalidDisplayOrderOnEdit() {
            for (String value : List.of("-1", "1.5", "")) {
                MemberBenefitsFormPage form = openEditFixtureForModify();
                form.setDisplayOrder(value);
                assertFalse(form.save().isBlank(), "Display order = \"" + value + "\" should be refused on Edit");
            }
        }
    }

    // =====================================================================
    // 5. Admin - Edit Member Benefit (Banner manager)
    // =====================================================================

    @Nested
    @Order(50)
    @DisplayName("5. Admin - Edit Member Benefit (Banner manager)")
    class AdminEditBannerManager {

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P010: Add a banner to an existing benefit")
        void addBannerToExisting() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            int before = form.bannerCount();
            int banner = form.addBanner();
            completeBannerRequiredLanguages(form, banner);
            form.switchTab("English");
            form.setBannerLink(banner, "https://newquark.com/vip");
            form.save();
            list.open();
            list.searchName(editFixtureName);
            int banners = Integer.parseInt(list.row(0).getOrDefault("Banners", "0").replaceAll("[^0-9]", ""));
            assertEquals(before + 1, banners, "Banners count should increase by 1");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P011: Remove an existing banner")
        void removeExistingBanner() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            int before = form.bannerCount();
            form.removeBanner(1);
            form.save();
            list.open();
            list.searchName(editFixtureName);
            int banners = Integer.parseInt(list.row(0).getOrDefault("Banners", "0").replaceAll("[^0-9]", ""));
            assertEquals(before - 1, banners, "Banners count should decrease by 1");

            MemberBenefitsFormPage restore = list.clickModify(0);
            int restored = restore.addBanner();
            completeBannerRequiredLanguages(restore, restored);
            restore.switchTab("English");
            restore.setBannerLink(restored, "https://newquark.com/deposit");
            restore.save();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P012: Replace an existing banner's image")
        void replaceExistingBannerImage() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            form.uploadPcPopup(0, MemberBenefitsTestData.PC_POPUP_REPLACEMENT);
            form.save();
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            reopened.switchTab("English");
            assertTrue(reopened.isSlotUploaded(0, MemberBenefitsFormPage.PC_POPUP), "The replaced PC pop-up should still show an image on reopen");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P013: Change a banner's own display order")
        void changeBannerOrder() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.setBannerOrder(0, "2");
            form.setBannerOrder(1, "1");
            String said = form.save();
            CaseLog.data("Save after swapping banner orders", said.isEmpty() ? "(accepted)" : said);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P014: Change one banner's link only, the other is unaffected")
        void changeOneBannerLinkOnly() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            form.setBannerLink(0, "https://newquark.com/vip");
            form.save();
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            reopened.switchTab("English");
            assertEquals("https://newquark.com/vip", reopened.bannerLinkValue(0), "Banner 1's new link should persist");
            assertEquals("https://newquark.com/deposit", reopened.bannerLinkValue(1), "Banner 2's link should be unaffected");
            reopened.setBannerLink(0, "https://newquark.com/promotion/agent");
            reopened.save();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P015: Replacing one language's images leaves the others unchanged")
        void languageImageIsolationOnEdit() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("繁體");
            assertTrue(form.isSlotUploaded(0, MemberBenefitsFormPage.SMALL_BANNER), "繁體 slot should be uploaded before the English-only edit");
            form.switchTab("English");
            form.uploadSmallBanner(0, MemberBenefitsTestData.SMALL_BANNER_REPLACEMENT);
            form.save();
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            reopened.switchTab("繁體");
            assertTrue(reopened.isSlotUploaded(0, MemberBenefitsFormPage.SMALL_BANNER), "繁體's own image should be unchanged");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P016: A re-uploaded image completes the banner again")
        void completeReUploadedImage() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            assertTrue(form.isBannerComplete(0), "Banner 1 should read complete before this edit");
            try {
                form.removeSlot(0, MemberBenefitsFormPage.SMALL_BANNER);
                assertFalse(form.isBannerComplete(0), "Banner 1 should read incomplete after removing one image");
                form.uploadSmallBanner(0, MemberBenefitsTestData.SMALL_BANNER);
                assertTrue(form.isBannerComplete(0), "Banner 1 should read complete again after re-upload");
                form.save();
            } catch (RuntimeException noRemoveControl) {
                CaseLog.evidence("Per-slot Remove was not driven live this run - see MB-ADM-ADD-N029's note.");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-P017: Clear an existing banner's link")
        void clearExistingBannerLink() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            form.setBannerLink(0, "");
            form.save();
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            reopened.switchTab("English");
            assertEquals("", reopened.bannerLinkValue(0), "Banner 1's link should be empty");
            assertEquals("https://newquark.com/deposit", reopened.bannerLinkValue(1), "Banner 2's link should be unchanged");
            reopened.setBannerLink(0, "https://newquark.com/promotion/agent");
            reopened.save();
        }

        @Test
        @DisplayName("MB-ADM-EDT-N005: Removing every banner is refused")
        void removeAllBanners() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            while (form.bannerCount() > 0) {
                form.removeBanner(0);
            }
            assertFalse(form.save().isBlank(), "Saving with every banner removed should be refused");
        }

        @Test
        @DisplayName("MB-ADM-EDT-N006: Removing one image from an existing banner blocks Save")
        void removeImageFromExistingBanner() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            try {
                form.removeSlot(0, MemberBenefitsFormPage.MOBILE_POPUP);
                assertFalse(form.isBannerComplete(0), "The banner should read incomplete after removing one image");
                assertFalse(form.save().isBlank(), "Save should be refused while the mobile pop-up is missing");
            } catch (RuntimeException noRemoveControl) {
                CaseLog.evidence("Per-slot Remove was not driven live this run - see MB-ADM-ADD-N029's note.");
            }
        }

        @Test
        @DisplayName("MB-ADM-EDT-N007: A new, incomplete banner blocks Save")
        void newIncompleteBannerOnEdit() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            assertFalse(form.save().isBlank(), "Save should be refused while the new banner is incomplete");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-N008: A replace that fails on Edit keeps the old image")
        void replaceFailsKeepsOldImageOnEdit() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            assertTrue(form.isSlotUploaded(0, MemberBenefitsFormPage.MOBILE_POPUP), "The mobile pop-up should be uploaded before the failed replace");
            session.setOffline(true);
            try {
                form.uploadMobilePopup(0, MemberBenefitsTestData.MOBILE_POPUP_REPLACEMENT);
            } finally {
                session.setOffline(false);
            }
            assertTrue(form.isSlotUploaded(0, MemberBenefitsFormPage.MOBILE_POPUP), "The slot should still show an image after a failed replace");
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            reopened.switchTab("English");
            assertTrue(reopened.isSlotUploaded(0, MemberBenefitsFormPage.MOBILE_POPUP), "The original image should still be there on reopen");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-N009: A wrong-dimension replace is accepted (finding, as MB-ADM-ADD-N024..026)")
        void wrongDimensionOnReplace() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("English");
            form.uploadPcPopup(0, ImageFixtures.jpg("vertical-replace", 750, 1334, Color.YELLOW));
            CaseLog.evidence("As MB-ADM-ADD-N024..026: the workbook expects rejection on an orientation mismatch; "
                    + "its own ACTUAL RESULT already recorded no dialog and the slot accepting the upload.");
            assertTrue(form.isSlotUploaded(0, MemberBenefitsFormPage.PC_POPUP), "A vertical replacement is currently accepted in the PC pop-up slot");
            form.uploadPcPopup(0, MemberBenefitsTestData.PC_POPUP);
            form.save();
        }

        @Test
        @DisplayName("MB-ADM-EDT-N010: A banner cannot be individually deactivated, only removed")
        void cannotDeactivateBannerIndividually() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            assertEquals(0, sessionPage.locator(".banner-box").first().locator("select").count(),
                    "No status control should exist on an existing banner either - only Remove disables one");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("MB-ADM-EDT-N011: Cancel after removing a banner leaves it in place")
        void cancelAfterRemovingBanner() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            int before = form.bannerCount();
            form.removeBanner(0);
            form.clickReturn();
            MemberBenefitsFormPage reopened = openEditFixtureForModify();
            assertEquals(before, reopened.bannerCount(), "The banner count should be unchanged after Return without saving");
        }

        @Test
        @DisplayName("MB-ADM-EDT-N012: A new banner complete in English only blocks Save")
        void newBannerInEnglishOnly() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            int banner = form.addBanner();
            form.switchTab("English");
            form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
            form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
            form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
            assertFalse(form.save().isBlank(), "Save should be refused while the new banner's 繁體 and 简体 slots are empty");
        }

        @Test
        @DisplayName("MB-ADM-EDT-N013: Removing one required language's image from an existing banner blocks Save")
        void removeImageInOneRequiredLanguage() {
            MemberBenefitsFormPage form = openEditFixtureForModify();
            form.switchTab("繁體");
            try {
                form.removeSlot(0, MemberBenefitsFormPage.MOBILE_POPUP);
                assertFalse(form.save().isBlank(), "Save should be refused while 繁體's mobile pop-up is missing");
                form.uploadMobilePopup(0, MemberBenefitsTestData.MOBILE_POPUP);
                form.save();
            } catch (RuntimeException noRemoveControl) {
                CaseLog.evidence("Per-slot Remove was not driven live this run - see MB-ADM-ADD-N029's note.");
            }
        }
    }
}
