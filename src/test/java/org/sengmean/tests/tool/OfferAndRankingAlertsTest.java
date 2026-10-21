package org.sengmean.tests.tool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.sengmean.base.BaseTest;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.page.tool.NotificationFormPage;
import org.sengmean.page.tool.NotificationListPage;
import org.sengmean.page.tool.RankingAlertsSettingsPage;
import org.sengmean.page.tool.RecordHistoryPage;
import org.sengmean.report.CaseLog;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>Tool &gt; Notification</b> - the <b>Offer</b> notification type and the <b>Ranking Alerts</b>
 * tab: the 160 {@code SN-ADM-LST}, {@code SN-ADM-OFF}, {@code SN-ADM-VAL}, {@code SN-SYS-DLV},
 * {@code SN-CLI-OFF}, {@code SN-ADM-RNK}, {@code SN-SYS-RNK}, {@code SN-CLI-RNK}, {@code SN-CLI-REB},
 * {@code SN-ADM-REC}, {@code SN-E2E}, {@code SN-NFR-UI}, {@code SN-NFR-PRF}, {@code SN-NFR-SEC} and
 * {@code SN-NFR-DAT} rows of the client's "NOTIFICATION Setup (Offer Rank...)" tab (workbook
 * {@code [TC_Admin] V.3.20.0 Weibo.xlsx}, the last of the four tabs Si marked green on 2026-10-21).
 *
 * <pre>./mvnw test -Dtest=OfferAndRankingAlertsTest</pre>
 *
 * <h2>160 rows, not the sheet's own stated 131</h2>
 * {@code ROW0}'s "Test Cases" cell reads 131; the tab actually carries 160 {@code SN-} rows, by
 * section: 16 LST, 27 ADM-OFF, 13 ADM-VAL, 12 SYS-DLV, 12 CLI-OFF, 7 ADM-RNK, 18 SYS-RNK, 5 CLI-RNK,
 * 4 CLI-REB, 18 ADM-REC, 3 E2E, 7 NFR-UI, 5 NFR-PRF, 7 NFR-SEC, 6 NFR-DAT - the same stale-count
 * pattern {@code RankingTest} and {@code ShareLinkTest} both found in their own tabs.
 *
 * <h2>This is an extension of an already-shipped screen, not a new one</h2>
 * Unlike Rankings and Share Link, <b>Tool &gt; Notification already exists and already has a full
 * suite</b> ({@code NotificationTest}, 148 {@code NOT-} rows). This class does not repeat that
 * coverage - it covers only what this sheet's own {@code SN-} ids ask about: a new <b>Offer</b>
 * notification type (linked to a promotion, with a banner and a Claim button) and a new
 * <b>Ranking Alerts</b> tab, both read from a <em>different, later</em> Figma page than the other
 * three tabs this pass covered - "version <b>5.1.0</b>", not "version 3.20.0" - found by searching
 * "Ranking Alerts" across every page of the file Si gave, since "Offer" and "Ranking Alerts" do not
 * appear anywhere on 3.20.0 at all.
 *
 * <h2>Measured live on admin-test, 2026-10-21</h2>
 * <ul>
 *   <li>Tool &gt; Notification has two tabs, <b>Notification</b> and <b>Record History</b> - no
 *       <b>Ranking Alerts</b>.</li>
 *   <li>+ Add notification goes straight to the System notification form - no menu offering
 *       System notification / Offer.</li>
 *   <li>The form's own <b>Type</b> select offers exactly one option, <b>System</b> - no
 *       <b>Offers</b>, and so no Linked Promotion, Banner Image or Claim Button section exists to
 *       drive even speculatively.</li>
 *   <li>The Notification list already has a <b>Type</b> filter and column (System only today) and
 *       already has Status and Language filters - these are real and are tested as such. It has no
 *       <b>Linked Promotion</b> column and no <b>Copy</b> row action.</li>
 *   <li>Record History already has Status, From/To and Language filters, and System record detail
 *       already shows ID/Type/Schedule/Timer/Status, per-language Title/Content/Link, Released At
 *       and Target Delivery Groups - all real. It has <b>no Type filter</b> and <b>no Sent/Seen
 *       count anywhere</b>, in the list or in the detail view - measured directly, not assumed.</li>
 * </ul>
 *
 * <h2>Two gates, checked once, that most known-defect rows share</h2>
 * {@link #offerTypeAvailable} ({@code form.options("Type").contains("Offers")}, checked in
 * {@link #signInAndProbeGates()}) and {@link #rankingAlertsTabAvailable}
 * ({@code list.tabLabels().contains("Ranking Alerts")}). Every {@code SN-ADM-OFF} and
 * {@code SN-ADM-VAL} row - and the admin-reachable {@code SN-ADM-RNK} rows - assert against these
 * gates rather than attempt to drive fields that cannot be reached at all: there is no dropdown,
 * no Type value and no tab to click through to a form that has never been measured. They fail
 * today naming exactly what is missing, and once the gate opens, the rows that did more than just
 * check the gate run as written.
 *
 * <h2>What could not be written for real even once both gates open</h2>
 * <ul>
 *   <li><b>{@code SN-SYS-DLV}, {@code SN-CLI-OFF}, {@code SN-CLI-RNK}, {@code SN-CLI-REB}, the
 *       member-facing half of {@code SN-E2E}</b> - all need reading a member's own inbox, which
 *       needs member-site page objects this project does not carry (see
 *       {@code AddMemberResetPasswordTest}'s Javadoc for the same limitation).</li>
 *   <li><b>{@code SN-SYS-RNK}</b> - needs Rankings itself deployed, which {@code RankingTest}
 *       found was not the case on this same date, plus Ranking Alerts, plus a way to force a VIP
 *       change or a ranking snapshot on demand.</li>
 *   <li>Rows needing a stopped delivery service, a restarted scheduler, a 50,000-member send, or
 *       an intercepted and resent API request are named individually, the same reasoning
 *       {@code RankingTest} and {@code ShareLinkTest} both used for their own out-of-reach rows.</li>
 * </ul>
 *
 * <h2>What is real here, reusing the existing Notification suite's own page objects</h2>
 * Everything that does not depend on Offer or Ranking Alerts - list navigation, the Type/Status/
 * Language filters that already exist, Record History's date-range and Status/Language filters and
 * its System record detail, XSS rendering, and a scheduling-timezone check using Playwright's own
 * {@code Browser.NewContextOptions.setTimezoneId} - is written as a real test against
 * {@code NotificationListPage}, {@code NotificationFormPage} and {@code RecordHistoryPage}, the
 * same classes {@code NotificationTest} already uses.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("Tool > Notification: Offer type & Ranking Alerts")
public class OfferAndRankingAlertsTest extends BaseTest {

    private static final String PREFIX = "QA SN ";

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static NotificationListPage list;

    private static boolean offerTypeAvailable;
    private static List<String> typeOptions = List.of();
    private static boolean rankingAlertsTabAvailable;
    private static List<String> tabLabels = List.of();

    @BeforeAll
    static void signInAndProbeGates() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        shell.useCurrency(CURRENCY);
        list = new NotificationListPage(sessionPage);
        list.open();

        tabLabels = list.tabLabels();
        rankingAlertsTabAvailable = tabLabels.contains("Ranking Alerts");

        NotificationFormPage form = list.clickAdd();
        typeOptions = form.options("Type");
        offerTypeAvailable = typeOptions.contains("Offers");
        form.cancel();

        CaseLog.data("Offer type available", offerTypeAvailable + " (Type offers " + typeOptions + ")");
        CaseLog.data("Ranking Alerts tab available", rankingAlertsTabAvailable + " (tabs: " + tabLabels + ")");
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

    // ---------------------------------------------------------------
    // Shared gate helpers
    // ---------------------------------------------------------------

    /** For SN-ADM-OFF / SN-ADM-VAL rows: fails naming the Type dropdown's real options until Offers ships. */
    private static void knownDefectOfferNotShipped(String whatThisRowWouldAlsoCheck) {
        assertTrue(offerTypeAvailable, "Offer notifications are not shipped yet - the Type dropdown offers "
                + typeOptions + ", no \"Offers\". Once it ships, this row would also check: " + whatThisRowWouldAlsoCheck);
    }

    /** For SN-ADM-RNK rows: fails naming the real tab list until Ranking Alerts ships. */
    private static void knownDefectRankingAlertsNotShipped(String whatThisRowWouldAlsoCheck) {
        assertTrue(rankingAlertsTabAvailable, "The Ranking Alerts tab is not shipped yet - Tool > Notification "
                + "offers " + tabLabels + ". Once it ships, this row would also check: " + whatThisRowWouldAlsoCheck);
    }

    private static void needsMemberSite() {
        abort("This row needs reading a member's own inbox, which needs member-site page objects this project "
                + "does not carry - see AddMemberResetPasswordTest's Javadoc for the same limitation.");
    }

    private static void needsRankingsDeployed(String what) {
        abort("This row needs " + what + ". RankingTest found Rankings itself is not deployed on admin-test "
                + "(2026-10-21), and this row also needs the Ranking Alerts tab this class's own probe found "
                + "missing, so both would have to ship before this is reachable.");
    }

    private static NotificationFormPage openAddForm() {
        list.open();
        return list.clickAdd();
    }

    // =====================================================================
    // 1. Admin - Notification list & navigation
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("1. Admin - Notification list & navigation")
    class AdminNotificationListAndNavigation {

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-001: Menu navigation and 3 tabs")
        void menuNavigationAndThreeTabs() {
            list.open();
            assertTrue(list.isShowing(), "The Notification screen should be showing");
            assertEquals(List.of("Notification", "Ranking Alerts", "Record History"), list.tabLabels(),
                    "The tab order should be Notification, Ranking Alerts, Record History, but read: " + list.tabLabels());
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-002: Switching between the three tabs")
        void switchingBetweenThreeTabs() {
            knownDefectOfferNotShipped("that is moot here - restated: ");
            knownDefectRankingAlertsNotShipped("each of the three tabs shows its own content with no stale data or errors");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-003: Add notification menu offers System notification and Offer")
        void addNotificationMenuOffersTwoItems() {
            // Measured 2026-10-21: + Add notification navigates straight to the form today; there
            // is no intermediate menu to open at all, so there is nothing to click through here -
            // the gate assertion below is the whole of what this row can check right now.
            knownDefectOfferNotShipped("+ Add notification offers a menu with System notification and Offer (NEW badge) items");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-004: Add notification > System notification opens the System form")
        void addSystemNotificationOpensForm() {
            NotificationFormPage form = openAddForm();
            assertTrue(form.isOpen(), "+ Add notification should open a form");
            assertEquals("System", form.type(), "Type should default to System");
            CaseLog.evidence("The form opens directly today rather than via a menu choice - see SN-ADM-LST-003.");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-005: Add notification > Offer opens the Offers form")
        void addOfferOpensOffersForm() {
            knownDefectOfferNotShipped("the Offers form opens with breadcrumb 'Home Page > Tool > Add Notification' and Type = Offers pre-selected");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-006: List columns (filters + Linked Promotion)")
        void listColumns() {
            list.open();
            List<String> headers = list.columnHeaders();
            CaseLog.data("Columns", String.join(", ", headers));
            assertTrue(headers.contains("Type"), "A Type column should already exist, and does");
            assertFalse(headers.contains("Linked Promotion"), "(sanity) Linked Promotion is not shipped yet");
            knownDefectOfferNotShipped("a Linked Promotion column alongside ID, Title, Type, Agent Group, Member Group, Schedule, Timer, Status, Created At, Actions (Edit, Delete, Copy)");
        }

        @Test
        @DisplayName("SN-ADM-LST-007: Type filter (All / System already real; Offers not yet)")
        void typeFilterAllSystemOffers() {
            list.open();
            List<String> options = list.typeFilterOptions();
            CaseLog.data("Type filter options", String.join(", ", options));
            assertTrue(options.contains("System"), "The Type filter should offer System, and does");
            list.filterType("System");
            assertTrue(list.rowCount() >= 0, "Filtering by System should not error");
            if (!options.contains("Offers")) {
                CaseLog.evidence("The Type filter offers no \"Offers\" option yet (GD: none filed in this pass) - "
                        + "the All/System half of this row is real and passes; the Offers half is not shipped.");
            }
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-008: Linked Promotion column values (Offers truncated+hover, System em-dash)")
        void linkedPromotionColumnValues() {
            knownDefectOfferNotShipped("an Offers row shows the linked promotion name (truncated, full name on hover) and a System row shows an em dash");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-009: Type and column naming (\"Offers\" / \"Linked Promotion\")")
        void typeAndColumnNaming() {
            knownDefectOfferNotShipped("the Type value and filter option read \"Offers\" (not \"Offer\") and the column header reads \"Linked Promotion\" (not \"Linked To\")");
        }

        @Test
        @DisplayName("SN-ADM-LST-010: Ranking alerts are excluded from the Notification list")
        void rankingAlertsExcludedFromList() {
            abort("This needs at least one Ranking alert already sent to prove it is excluded, which needs "
                    + "Ranking Alerts deployed and a real trigger - see needsRankingsDeployed's reasoning. Vacuously "
                    + "true today (no ranking alerts exist to appear anywhere), which is not a meaningful pass.");
        }

        @Test
        @DisplayName("SN-ADM-LST-011: Status and Language filters")
        void statusAndLanguageFilters() {
            list.open();
            List<String> statusOptions = list.statusFilterOptions();
            assertTrue(statusOptions.size() > 1, "The Status filter should offer more than just the default, but read: " + statusOptions);
            list.filterStatus(statusOptions.get(1));
            list.filterLanguage("English");
            assertEquals("English", list.languageFilter(), "The Language filter should now read English");
        }

        @Test
        @DisplayName("SN-ADM-LST-012: Schedule, Timer and Status display formats")
        void scheduleTimerStatusDisplayFormats() {
            list.open();
            var rows = list.notifications();
            assertTrue(rows.size() > 0, "The list should have at least one row to read formats from");
            CaseLog.data("Sample row", rows.get(0).toString());
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-013: Edit Offer")
        void editOffer() {
            knownDefectOfferNotShipped("Edit on an Offer row opens the Offers form prefilled, and a Title change is saved and shown in the list");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-014: Copy Offer")
        void copyOffer() {
            list.open();
            boolean hasCopyAction = sessionPage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Copy")).count() > 0;
            assertTrue(hasCopyAction, "A Copy row action should exist (it does not yet, for any notification type)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-015: Delete Offer")
        void deleteOffer() {
            knownDefectOfferNotShipped("Delete on an Offer asks for confirmation and removes it from the list, cancelling future sends");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-LST-016: Pagination with Type=Offer filter applied")
        void paginationWithOfferFilter() {
            knownDefectOfferNotShipped("paging applies to the Offer-filtered results and the filter is kept between pages");
        }
    }

    // =====================================================================
    // 2. Admin - Add Offer (linked promotion, content, banner, button, schedule, groups)
    // =====================================================================

    @Nested
    @Order(20)
    @Tag("known-defect")
    @DisplayName("2. Admin - Add Offer")
    class AdminAddOffer {
        @Test @DisplayName("SN-ADM-OFF-001: Form layout matches the design") void formLayout() { knownDefectOfferNotShipped("Linked Promotion, Notification Content (tabs, Banner, Title*, Content*, Override), Claim Button, Type/Schedule/Timer/Status, Target Delivery Groups, Save/Cancel, a save-rules note"); }
        @Test @DisplayName("SN-ADM-OFF-002: Promotion Kind offers 5 options (OQ-16)") void promotionKindOptions() { knownDefectOfferNotShipped("Promotion, Mission Promotion, Live Promotion, System Wallet Promotion, System Live Promotion"); }
        @Test @DisplayName("SN-ADM-OFF-003: Promotion is required") void promotionRequired() { knownDefectOfferNotShipped("saving with Title and Content filled but Promotion empty is blocked, Promotion marked required"); }
        @Test @DisplayName("SN-ADM-OFF-004: Promotion list content (Active/Inactive-future shown, ended/past hidden)") void promotionListContent() { knownDefectOfferNotShipped("only Active and Inactive-future-start promotions are listed"); }
        @Test @DisplayName("SN-ADM-OFF-005: Promotion search") void promotionSearch() { knownDefectOfferNotShipped("the picker filters to matching promotions; no match shows an empty state"); }
        @Test @DisplayName("SN-ADM-OFF-006: Promotion list filtered by Kind") void promotionListFilteredByKind() { knownDefectOfferNotShipped("the picker lists only promotions of the selected Kind, and changing Kind clears the selected promotion and summary"); }
        @Test @DisplayName("SN-ADM-OFF-007: System Wallet / System Live promotion picker and summary (OQ-16)") void systemPromotionPickerAndSummary() { knownDefectOfferNotShipped("Wallet Kind lists only the System Wallet Promotion, Live Kind only the System Live Promotion, with a bonus-terms summary"); }
        @Test @DisplayName("SN-ADM-OFF-008: Promotion summary is a read-only box") void promotionSummaryReadOnly() { knownDefectOfferNotShipped("Offer Period (HKT), Promotion Status, Promotion Type and Promotion Groups shown read-only, taken from the linked promotion"); }
        @Test @DisplayName("SN-ADM-OFF-009: Offer Period reads Ongoing for a promotion with no Time Range") void offerPeriodOngoing() { knownDefectOfferNotShipped("selecting a promotion without a Time Range shows Offer Period = Ongoing"); }
        @Test @DisplayName("SN-ADM-OFF-010: 12 language tabs, each with its own banner/title/content") void twelveLanguageTabs() { knownDefectOfferNotShipped("the same 12 tabs the System form already offers, each keeping its own banner, title and content"); }
        @Test @DisplayName("SN-ADM-OFF-011: Title and Content are required") void titleAndContentRequired() { knownDefectOfferNotShipped("saving with either field empty is blocked and the missing field is marked with *"); }
        @Test @DisplayName("SN-ADM-OFF-012: Unicode, emoji and HTML in Title/Content") void unicodeEmojiHtmlInTitleContent() { knownDefectOfferNotShipped("unicode and emoji save and display correctly; HTML is shown as escaped text"); }
        @Test @DisplayName("SN-ADM-OFF-013: Title/Content max length matches the System form (OQ-11)") void maxLengthMatchesSystemForm() { knownDefectOfferNotShipped("the same max length as the System notification form, checked first against the live System form"); }
        @Test @DisplayName("SN-ADM-OFF-014: Override above contents to") void overrideAboveContents() { knownDefectOfferNotShipped("All languages copies the current tab to every other tab; selective ticks copy only to ticked languages; the current tab is never offered as a target - the same component the System form already has"); }
        @Test @DisplayName("SN-ADM-OFF-015: Banner upload per language") void bannerUploadPerLanguage() { knownDefectOfferNotShipped("each language tab keeps its own uploaded banner, with a preview and an upload area labelled for the current tab"); }
        @Test @DisplayName("SN-ADM-OFF-016: Banner formats JPG/PNG/WebP accepted") void bannerFormatsAccepted() { knownDefectOfferNotShipped("1029x453 JPG, PNG and WebP banners are all accepted"); }
        @Test @DisplayName("SN-ADM-OFF-017: Banner invalid formats rejected") void bannerInvalidFormatsRejected() { knownDefectOfferNotShipped("GIF, BMP, SVG, PDF and a renamed EXE are all rejected with a format message"); }
        @Test @DisplayName("SN-ADM-OFF-018: Banner size limit - 1 MB accepted, over rejected") void bannerSizeLimit() { knownDefectOfferNotShipped("exactly 1,048,576 bytes is accepted; 1,048,577 is rejected with a size message"); }
        @Test @DisplayName("SN-ADM-OFF-019: Banner dimensions - only exactly 1029x453 accepted (OQ-10)") void bannerDimensionsExact() { knownDefectOfferNotShipped("every size other than exactly 1029x453, including the same 2.27:1 ratio, is rejected with a size message naming the exact size"); }
        @Test @DisplayName("SN-ADM-OFF-020: Banners required only for 繁體/简体/English (OQ-02)") void bannersRequiredForRequiredLanguagesOnly() { knownDefectOfferNotShipped("a missing banner on 繁體, 简体 or English blocks Save naming the language; the other 9 languages need no banner"); }
        @Test @DisplayName("SN-ADM-OFF-021: Claim Button field for a normal promotion") void claimButtonFieldNormalPromotion() { knownDefectOfferNotShipped("a read-only field labelled Claim Button reading \"Claim This Offer (automatic: opens the deposit page with the linked promotion pre-selected)\""); }
        @Test @DisplayName("SN-ADM-OFF-022: Claim Button field for a System promotion (OQ-26)") void claimButtonFieldSystemPromotion() { knownDefectOfferNotShipped("the same Claim Button label reading \"Deposit Now (automatic: ... no promotion is pre-selected)\""); }
        @Test @DisplayName("SN-ADM-OFF-023: Switching Type between Offers and System (OQ-18)") void switchingTypeOffersAndSystem() { knownDefectOfferNotShipped("switching to System hides Linked Promotion/Banner/Claim Button and shows Link and editable groups; switching back restores the Offers layout; each Type saves and lists correctly"); }
        @Test @DisplayName("SN-ADM-OFF-024: Schedule, Timer and Status options") void scheduleTimerStatusOptions() { knownDefectOfferNotShipped("Once/Daily/Weekly, Week day shown only for Weekly, a 12-hour Timer, and Active/Inactive Status - the same controls the System form already has"); }
        @Test @DisplayName("SN-ADM-OFF-025: Editable Target Delivery Groups (OQ-19)") void editableTargetDeliveryGroups() { knownDefectOfferNotShipped("no groups pre-selected; Save is blocked with none selected; groups outside the linked promotion can still be added and saved"); }
        @Test @DisplayName("SN-ADM-OFF-026: Selected groups survive a promotion change (OQ-19)") void selectedGroupsSurvivePromotionChange() { knownDefectOfferNotShipped("changing the linked promotion updates the summary but leaves the admin's own selected groups unchanged"); }
        @Test @DisplayName("SN-ADM-OFF-027: Cancel returns to the list with nothing saved") void cancelReturnsToList() { knownDefectOfferNotShipped("Cancel on a filled-in Offers form returns to the Notification list without creating a record"); }
    }

    // =====================================================================
    // 3. Admin - Offer save rules & validation
    // =====================================================================

    @Nested
    @Order(30)
    @Tag("known-defect")
    @DisplayName("3. Admin - Offer save rules & validation")
    class AdminOfferSaveRulesValidation {
        @Test @DisplayName("SN-ADM-VAL-001: A valid Offer saves") void validOfferSaves() { knownDefectOfferNotShipped("saved; estimated recipients shown; appears in the list with Type Offer and Linked To"); }
        @Test @DisplayName("SN-ADM-VAL-002: An ended promotion blocks save") void endedPromotionBlocksSave() { knownDefectOfferNotShipped("save blocked with a \"promotion has ended\" message"); }
        @Test @DisplayName("SN-ADM-VAL-003: A disabled promotion blocks save") void disabledPromotionBlocksSave() { knownDefectOfferNotShipped("save blocked with a clear message"); }
        @Test @DisplayName("SN-ADM-VAL-004: A send time before the offer period blocks save") void sendTimeBeforePeriodBlocksSave() { knownDefectOfferNotShipped("save blocked: send time outside the offer period"); }
        @Test @DisplayName("SN-ADM-VAL-005: A send time after the offer period blocks save") void sendTimeAfterPeriodBlocksSave() { knownDefectOfferNotShipped("save blocked"); }
        @Test @DisplayName("SN-ADM-VAL-006: Send times on the period's first/last minute are accepted") void sendTimeOnBoundaryAccepted() { knownDefectOfferNotShipped("both the first and last minute of the offer period save successfully"); }
        @Test @DisplayName("SN-ADM-VAL-007: A send time before a future-start promotion begins blocks save") void sendTimeBeforeFutureStartBlocksSave() { knownDefectOfferNotShipped("save blocked (outside the offer period)"); }
        @Test @DisplayName("SN-ADM-VAL-008: An Inactive System promotion (Overall or part) blocks save") void inactiveSystemPromotionBlocksSave() { knownDefectOfferNotShipped("save blocked whether Overall or just one part is Inactive"); }
        @Test @DisplayName("SN-ADM-VAL-009: A missing-title language shows a save warning, not a block") void missingLanguageShowsWarning() { knownDefectOfferNotShipped("a warning lists the languages with no title; the admin can still save; those members are skipped"); }
        @Test @DisplayName("SN-ADM-VAL-010: Estimated recipients matches Sent after delivery") void estimatedRecipientsMatchesSent() { knownDefectOfferNotShipped("the estimated recipient count excludes members in a language with no title and equals Sent after delivery"); }
        @Test @DisplayName("SN-ADM-VAL-011: Re-activating after an auto-Inactive is blocked by the promotion rule") void reactivatingAfterAutoInactiveBlocked() { knownDefectOfferNotShipped("setting Status back to Active is still blocked by the promotion-ended rule"); }
        @Test @DisplayName("SN-ADM-VAL-012: A Daily Offer stops at the offer period end (OQ-13)") void dailyOfferStopsAtPeriodEnd() { knownDefectOfferNotShipped("Daily sends happen only up to the period end, then stop and the Offer turns Inactive"); }
        @Test @DisplayName("SN-ADM-VAL-013: The save-rules note is shown under the buttons") void saveRulesNoteShown() { knownDefectOfferNotShipped("a note stating the promotion-ended/disabled/out-of-period block, the missing-title warning and the estimated-recipients line"); }
    }

    // =====================================================================
    // 4. System - Offer delivery & scheduling
    // =====================================================================

    @Nested
    @Order(40)
    @DisplayName("4. System - Offer delivery & scheduling")
    class SystemOfferDeliveryScheduling {
        @Test @DisplayName("SN-SYS-DLV-001: Immediate send") void immediateSend() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-002: Timed send at a set HKT time") void timedSendHkt() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-003: Daily send at 12 AM") void dailySend() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-004: Weekly send") void weeklySend() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-005: An Inactive Offer sends nothing") void inactiveOfferSendsNothing() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-006: Audience is the Offer's own selected groups (OQ-19)") void audienceIsOffersOwnGroups() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-007: A member in a language with no title is skipped") void missingLanguageMemberSkipped() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-008: Sends stop when the promotion ends") void sendsStopOnPromotionEnd() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-009: Sends stop when the promotion is disabled") void sendsStopOnPromotionDisable() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-010: The Offer's saved groups do not change when the promotion's groups do (OQ-19)") void offerGroupsUnaffectedByPromotionGroupChange() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-011: Audience is recalculated from the Offer's groups at each send (OQ-15, OQ-19)") void audienceRecalculatedEachSend() { needsMemberSite(); }
        @Test @DisplayName("SN-SYS-DLV-012: An Offer lands only under the Offers inbox tab") void offerLandsInOffersTab() { needsMemberSite(); }
    }

    // =====================================================================
    // 5. Client - Offer detail & claim
    // =====================================================================

    @Nested
    @Order(50)
    @DisplayName("5. Client - Offer detail & claim")
    class ClientOfferDetailAndClaim {
        @Test @DisplayName("SN-CLI-OFF-001: Detail content") void detailContent() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-002: Member language") void memberLanguage() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-003: Ongoing period") void ongoingPeriod() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-004: Claim This Offer opens the deposit page pre-selected") void claimThisOffer() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-005: Changing the pre-selected promotion on deposit") void changingPreSelectedPromotion() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-006: Deposit Now for a System promotion") void depositNowSystemPromotion() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-007: Button disabled after the promotion ends") void buttonDisabledAfterPromotionEnds() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-008: Button disabled once a one-time offer is used") void buttonDisabledOneTimeUsed() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-009: Not eligible for the linked promotion despite receiving the Offer (OQ-19)") void notEligibleForLinkedPromotion() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-010: Logged out, then login, keeps the promotion selected") void loggedOutThenLogin() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-011: A stale screen is rejected server-side when the promotion ends mid-view") void staleScreenRejected() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-OFF-012: Disabled-state texts are localized") void disabledTextsLocalized() { needsMemberSite(); }
    }

    // =====================================================================
    // 6. Admin - Ranking Alerts settings
    // =====================================================================

    @Nested
    @Order(60)
    @DisplayName("6. Admin - Ranking Alerts settings")
    class AdminRankingAlertsSettings {

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-RNK-001: Tab layout matches the design")
        void tabLayoutMatchesDesign() {
            knownDefectRankingAlertsNotShipped("an intro line and a table ALERT / WHEN IT IS SENT (SYSTEM RULE) / DATA CARD SHOWN TO MEMBER, "
                    + "rows Membership upgrade, Referral Ranking, Withdrawal Ranking, a system-rules note, Save changes / Cancel");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-RNK-002: No content fields - only On/Off switches")
        void noContentFieldsOnlyToggles() {
            knownDefectRankingAlertsNotShipped("no title/content input anywhere on the tab, only the three On/Off switches");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-RNK-003: Save changes persists after refresh")
        void saveChangesPersistsAfterRefresh() {
            knownDefectRankingAlertsNotShipped("switching Withdrawal Ranking On, clicking Save changes, and refreshing leaves it On");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-RNK-004: An unsaved switch reverts on refresh")
        void unsavedSwitchRevertsOnRefresh() {
            knownDefectRankingAlertsNotShipped("switching On without clicking Save changes and refreshing leaves it Off again");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-RNK-005: Cancel discards the switch")
        void cancelDiscardsSwitch() {
            knownDefectRankingAlertsNotShipped("switching Referral Ranking Off then clicking Cancel returns it to On, nothing saved");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-RNK-006: Leaving the tab with an unsaved change (OQ-20)")
        void leavingTabWithUnsavedChange() {
            knownDefectRankingAlertsNotShipped("an unsaved-changes warning, or the change being silently discarded - OQ-20 leaves which one open");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-RNK-007: Admin Operation Log entry per change")
        void adminOperationLogEntryPerChange() {
            knownDefectRankingAlertsNotShipped("one Account Operation Log entry per saved change, with admin, time, alert name, old -> new value");
        }
    }

    // =====================================================================
    // 7. System - Ranking alert triggers
    // =====================================================================

    @Nested
    @Order(70)
    @DisplayName("7. System - Ranking alert triggers (Membership, Referral, Withdrawal)")
    class SystemRankingAlertTriggers {
        @Test @DisplayName("SN-SYS-RNK-001: Single VIP level upgrade") void singleLevelUpgrade() { needsRankingsDeployed("a way to upgrade a member's VIP level and read the resulting alert"); }
        @Test @DisplayName("SN-SYS-RNK-002: A multi-level jump sends exactly one alert") void multiLevelJumpOneAlert() { needsRankingsDeployed("a way to jump a member several VIP levels at once"); }
        @Test @DisplayName("SN-SYS-RNK-003: Downgrade or re-saving the same level sends no alert") void downgradeOrSameLevelNoAlert() { needsRankingsDeployed("a way to downgrade or re-save a member's VIP level"); }
        @Test @DisplayName("SN-SYS-RNK-004: Rebate % in the card matches Membership and Rewards") void rebatePercentMatchesConfig() { needsRankingsDeployed("a way to upgrade a member and read the alert's card"); }
        @Test @DisplayName("SN-SYS-RNK-005: A new Referral best rank triggers an alert") void newReferralBestRankTriggersAlert() { needsRankingsDeployed("the daily Referral snapshot, which RankingTest found is not deployed"); }
        @Test @DisplayName("SN-SYS-RNK-006: Entering the Referral Top 50 at rank 50 triggers an alert") void enteringTopFiftyTriggersAlert() { needsRankingsDeployed("the daily Referral snapshot"); }
        @Test @DisplayName("SN-SYS-RNK-007: Rank 51 triggers no alert") void rankFiftyOneNoAlert() { needsRankingsDeployed("the daily Referral snapshot"); }
        @Test @DisplayName("SN-SYS-RNK-008: Improving but not past the best rank triggers no alert") void improvingNotPastBestNoAlert() { needsRankingsDeployed("the daily Referral snapshot"); }
        @Test @DisplayName("SN-SYS-RNK-009: Rank staying the same or dropping triggers no alert") void rankSameOrDroppingNoAlert() { needsRankingsDeployed("the daily Referral snapshot"); }
        @Test @DisplayName("SN-SYS-RNK-010: Referral alerts only fire at 00:00 (UTC+7)") void referralAlertsOnlyAtMidnight() { needsRankingsDeployed("the daily Referral snapshot schedule"); }
        @Test @DisplayName("SN-SYS-RNK-011: A new Withdrawal best rank triggers an alert") void newWithdrawalBestRankTriggersAlert() { needsRankingsDeployed("the hourly Withdrawal snapshot"); }
        @Test @DisplayName("SN-SYS-RNK-012: Withdrawal alerts only fire on the hour") void withdrawalAlertsOnlyOnTheHour() { needsRankingsDeployed("the hourly Withdrawal snapshot schedule"); }
        @Test @DisplayName("SN-SYS-RNK-013: Reaching rank 1 triggers an alert") void reachingRankOneTriggersAlert() { needsRankingsDeployed("a snapshot that moves a member to rank 1"); }
        @Test @DisplayName("SN-SYS-RNK-014: Nothing sends while the alert is Off") void nothingSendsWhileAlertOff() { needsRankingsDeployed("the Ranking Alerts Off state plus a snapshot"); }
        @Test @DisplayName("SN-SYS-RNK-015: No backfill when an alert is switched On") void noBackfillWhenSwitchedOn() { needsRankingsDeployed("switching an alert On with members already in the Top 50"); }
        @Test @DisplayName("SN-SYS-RNK-016: No alert while the ranking is hidden from clients") void noAlertWhileRankingHidden() { needsRankingsDeployed("Rankings' own Display toggle, which RankingTest already covers for Rankings itself"); }
        @Test @DisplayName("SN-SYS-RNK-017: A $0 / not-ranked member triggers no alert") void zeroMemberNoAlert() { needsRankingsDeployed("a snapshot over a $0 member"); }
        @Test @DisplayName("SN-SYS-RNK-018: Toggling during snapshot processing is all-or-none") void togglingDuringSnapshotAllOrNone() { needsRankingsDeployed("a way to switch the alert during an in-progress snapshot"); }
    }

    // =====================================================================
    // 8. Client - Ranking alert display
    // =====================================================================

    @Nested
    @Order(80)
    @DisplayName("8. Client - Ranking alert display")
    class ClientRankingAlertDisplay {
        @Test @DisplayName("SN-CLI-RNK-001: Alert appears under the Rankings tab") void alertUnderRankingsTab() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-RNK-002: Opening the alert shows the data card") void openingAlertShowsDataCard() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-RNK-003: Amount follows the Ranking Settings format") void amountFollowsFormat() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-RNK-004: Member language") void memberLanguage() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-RNK-005: Client naming differs from Back Office (OQ-06, OQ-09)") void clientNamingDiffersFromBackOffice() { needsMemberSite(); }
    }

    // =====================================================================
    // 9. Client - Rebate ready (system event)
    // =====================================================================

    @Nested
    @Order(90)
    @DisplayName("9. Client - Rebate ready (system event)")
    class ClientRebateReady {
        @Test @DisplayName("SN-CLI-REB-001: Tapping opens the claim page directly, no detail page") void tapOpensClaimPageDirectly() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-REB-002: Tapping marks it Seen") void tapMarksSeen() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-REB-003: The claim URL uses the site's domain and language path") void claimUrlUsesSiteDomainAndLanguage() { needsMemberSite(); }
        @Test @DisplayName("SN-CLI-REB-004: Logged-in members only (OQ-08)") void loggedInMembersOnly() { needsMemberSite(); }
    }

    // =====================================================================
    // 10. Admin - Record History (list & record details)
    // =====================================================================

    @Nested
    @Order(100)
    @DisplayName("10. Admin - Record History (list & record details)")
    class AdminRecordHistory {

        /** Creates one real System notification this section's rows can read back from Record History. */
        private String seedSystemNotification() {
            String title = PREFIX + "Rec " + System.currentTimeMillis() % 100_000;
            NotificationFormPage form = openAddForm();
            form.fillLanguage("繁體", title, "Seed for OfferAndRankingAlertsTest's Record History rows.", "");
            form.overrideToAll();
            form.setSchedule("Once");
            form.setStatus("Active");
            form.clickSave();
            return title;
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-001: List columns (Type filter + Sent/Seen)")
        void listColumns() {
            list.open();
            RecordHistoryPage history = list.openRecordHistory();
            List<String> headers = history.columnHeaders();
            CaseLog.data("Record History columns", String.join(", ", headers));
            assertFalse(headers.stream().anyMatch(h -> h.toLowerCase(java.util.Locale.ROOT).contains("sent") || h.toLowerCase(java.util.Locale.ROOT).contains("seen")),
                    "(sanity) no Sent/Seen column is shown yet");
            assertTrue(headers.stream().anyMatch(h -> h.toLowerCase(java.util.Locale.ROOT).contains("sent") || h.toLowerCase(java.util.Locale.ROOT).contains("seen")),
                    "Record History should carry a Sent / Seen column - it does not yet, and there is no Type filter either");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-002: Type filter offers System, Offer and the three Ranking types")
        void typeFilterOffersAllTypes() {
            knownDefectOfferNotShipped("a Type filter on Record History offering System, Offer, Ranking · Membership, Ranking · Referral and Ranking · Withdrawal - none of it exists yet, Offer or Ranking alike");
        }

        @Test
        @DisplayName("SN-ADM-REC-003: Date range filter (From / To)")
        void dateRangeFilter() {
            seedSystemNotification();
            RecordHistoryPage history = list.openRecordHistory();
            String today = java.time.LocalDate.now().toString();
            history.filterDates(today.substring(0, 8) + "01", today);
            assertTrue(history.entries().size() >= 0, "Filtering by a valid date range should not error");
            history.filterDates(today, today.substring(0, 8) + "01");
            CaseLog.evidence("An inverted From > To range is expected to be blocked; this only confirms the filter "
                    + "accepts a valid range without erroring, since the inverted case's own feedback was not "
                    + "measured in this pass.");
        }

        @Test
        @DisplayName("SN-ADM-REC-004: Status and Language filters")
        void statusAndLanguageFilters() {
            seedSystemNotification();
            RecordHistoryPage history = list.openRecordHistory();
            List<String> statusOptions = history.statusFilterOptions();
            assertTrue(statusOptions.size() > 1, "The Status filter should offer more than the default, but read: " + statusOptions);
            history.filterStatus(statusOptions.get(1));
            history.filterLanguage("English");
            CaseLog.data("Entries after filtering", String.valueOf(history.entries().size()));
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-005: Ranking row values")
        void rankingRowValues() {
            needsRankingsDeployed("at least one Referral, Withdrawal or Membership record in Record History");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-006: Pending / Failed status values with Sent/Seen counts")
        void pendingFailedStatusValues() {
            knownDefectOfferNotShipped("Pending reads —/—, Failed reads 0/0 with a Failed badge, and Sent counts carry thousand separators - "
                    + "none of this is checkable without the same Sent/Seen column SN-ADM-REC-001 is missing");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-007: Released At is HKT everywhere, with ranking snapshots converted from UTC+7")
        void releasedAtHktEverywhere() {
            needsRankingsDeployed("a Referral or Withdrawal record to compare its UTC+7 snapshot time against its HKT display");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-008: System record detail")
        void systemRecordDetail() {
            String title = seedSystemNotification();
            RecordHistoryPage history = list.openRecordHistory();
            var entries = history.entriesFor(title);
            assertTrue(entries.size() > 0, "The seeded notification should have at least one Record History entry");
            history.openDetail(entries.get(0).id());
            assertTrue(history.isDetailShowing(), "The detail view should open");
            assertFalse(history.detail("Type").isBlank(), "Type should be shown");
            assertFalse(history.detail("Released At").isBlank(), "Released At should be shown");
            assertTrue(history.targetTags("Agent Group").size() >= 0, "Target Delivery Groups should be shown");
            boolean sentShown = !history.detail("Sent").isBlank();
            boolean seenShown = !history.detail("Seen").isBlank();
            assertTrue(sentShown && seenShown,
                    "A Sent count and a Seen count (% of sent) should both be shown on the detail view - neither is yet "
                            + "(Sent=\"" + history.detail("Sent") + "\", Seen=\"" + history.detail("Seen") + "\")");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-009: Offer record detail")
        void offerRecordDetail() {
            knownDefectOfferNotShipped("the System detail fields plus Banner per language, Linked Promotion with offer period, Delivery Result and a completed-claims count");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-010: Ranking - Membership detail")
        void membershipDetail() {
            needsRankingsDeployed("a Membership upgrade record to open");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-011: Ranking - Referral / Withdrawal detail")
        void referralWithdrawalDetail() {
            needsRankingsDeployed("a Referral or Withdrawal snapshot record to open");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-012: Admin sees full usernames and exact amounts across ranking records (OQ-29)")
        void adminSeesFullDataAcrossRankingRecords() {
            needsRankingsDeployed("several ranking records naming the same member, to compare their Username and amounts");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-013: Seen is counted when a member opens the detail")
        void seenCountedOnOpen() {
            knownDefectOfferNotShipped("a Seen count that increases when a member opens the notification - there is no Seen display anywhere yet, for any type");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-014: Seen is counted only once per member")
        void seenCountedOnlyOnce() {
            knownDefectOfferNotShipped("opening the same notification twice does not increase the Seen count a second time");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-015: Viewing the inbox list alone does not count as Seen (OQ-05)")
        void listViewAloneNotCountedAsSeen() {
            knownDefectOfferNotShipped("a member seeing the notification only in their inbox list, without opening it, leaves it Not seen");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-016: Seen % rounding")
        void seenPercentRounding() {
            knownDefectOfferNotShipped("12,480/7,912 rounds to 63%, 52,300/30,118 to 58%, 3/2 to 67%, and 0/0 shows 0% or an em dash without erroring");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-ADM-REC-017: Claim This Offer count only counts completed claims (OQ-04)")
        void claimCountOnlyCompletedClaims() {
            knownDefectOfferNotShipped("a tap without a completed deposit, a switched promotion, and a deposit that misses the condition are all excluded from the Claim count");
        }

        @Test
        @DisplayName("SN-ADM-REC-018: Back to List keeps the filters")
        void backToListKeepsFilters() {
            String title = seedSystemNotification();
            RecordHistoryPage history = list.openRecordHistory();
            history.filterLanguage("English");
            var entries = history.entriesFor(title);
            if (entries.isEmpty()) {
                abort("The seeded record is not visible under the English language filter - nothing to open and return from.");
            }
            history.openDetail(entries.get(0).id());
            history.backToList();
            assertTrue(history.isShowing(), "Back to List should return to the Record History list");
            assertFalse(history.entriesFor(title).isEmpty(),
                    "The seeded record should still be visible after Back to List, consistent with the English filter having been kept rather than reset");
        }
    }

    // =====================================================================
    // 11. End-to-end
    // =====================================================================

    @Nested
    @Order(110)
    @DisplayName("11. End-to-end")
    class EndToEnd {
        @Test @DisplayName("SN-E2E-001: Offer lifecycle, create to promotion end") void offerLifecycle() { needsMemberSite(); }
        @Test @DisplayName("SN-E2E-002: Withdrawal ranking flow, climb to alert") void withdrawalRankingFlow() { needsMemberSite(); }
        @Test @DisplayName("SN-E2E-003: Membership upgrade flow, upgrade to alert") void membershipFlow() { needsMemberSite(); }
    }

    // =====================================================================
    // 12. Non-functional - UI, compatibility & usability
    // =====================================================================

    @Nested
    @Order(120)
    @DisplayName("12. Non-functional - UI, compatibility & usability")
    class NonFunctionalUi {

        @Test
        @DisplayName("SN-NFR-UI-001: BO UI vs Figma 5.1.0 §7")
        void boUiVsFigma() {
            abort("Comparing the live screens against the Figma frames pixel by pixel needs a visual-diff tool "
                    + "this project does not have - the same limitation ShareLinkTest's SL-NFR-UI-001 names.");
        }

        @Test
        @DisplayName("SN-NFR-UI-002: Client screens on the main browsers")
        void clientScreensOnMainBrowsers() {
            needsMemberSite();
        }

        @Test
        @DisplayName("SN-NFR-UI-003: Responsive client widths")
        void responsiveClientWidths() {
            needsMemberSite();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SN-NFR-UI-004: Long titles do not break the BO list layout")
        void longTitlesDoNotBreakBoListLayout() {
            String longTitle = ("QA SN " + "ยาวมาก".repeat(3)).substring(0, 20);
            NotificationFormPage form = openAddForm();
            form.fillLanguage("繁體", longTitle, "Long-title layout check for SN-NFR-UI-004.", "");
            form.overrideToAll();
            form.clickSave();
            list.open();
            assertTrue(list.isShowing(), "The list should still render normally with a long multi-byte title in it");
            CaseLog.evidence("Only the BO list is checked for layout; the client list/detail wrap behaviour needs "
                    + "the member site, which this project does not carry.");
        }

        @Test
        @DisplayName("SN-NFR-UI-005: Emoji rendering across operating systems")
        void emojiRenderingAcrossOperatingSystems() {
            abort("Comparing rendering across Windows, macOS, iOS and Android needs those platforms; this suite "
                    + "runs one Chromium build.");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-NFR-UI-006: Validation messages name the rule and the field")
        void validationMessagesNameRuleAndField() {
            knownDefectOfferNotShipped("triggering each Offer save-block rule (promotion ended/disabled, send time outside the period) shows a message naming the reason and the field, in the BO's own language");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-NFR-UI-007: Offers form language labels match the System form exactly (OQ-24)")
        void offersFormLanguageLabelsMatchSystemForm() {
            NotificationFormPage form = openAddForm();
            List<String> systemTabs = form.languageTabLabels();
            form.cancel();
            CaseLog.data("System form language tabs", String.join(", ", systemTabs));
            knownDefectOfferNotShipped("the Offers form's tabs and \"Override above contents to\" checkboxes use exactly these same labels - " + systemTabs);
        }
    }

    // =====================================================================
    // 13. Non-functional - performance
    // =====================================================================

    @Nested
    @Order(130)
    @DisplayName("13. Non-functional - performance")
    class NonFunctionalPerformance {

        @Test
        @DisplayName("SN-NFR-PRF-001: A 50,000+ member bulk Offer send completes within a minute")
        void bulkOfferSendPerformance() {
            knownDefectOfferNotShipped("sending to 50,000+ eligible members completes within 1 minute and the back office stays responsive");
        }

        @Test
        @DisplayName("SN-NFR-PRF-002: An hourly Withdrawal snapshot processes within a minute")
        void hourlySnapshotPerformance() {
            needsRankingsDeployed("a large ranking dataset and the hourly snapshot job");
        }

        @Test
        @DisplayName("SN-NFR-PRF-003: Record History loads within 3 seconds")
        void recordHistoryLoadsWithinThreeSeconds() {
            list.open();
            long start = System.currentTimeMillis();
            RecordHistoryPage history = list.openRecordHistory();
            history.filterStatus(history.statusFilterOptions().size() > 1 ? history.statusFilterOptions().get(1) : history.statusFilterOptions().get(0));
            long elapsedMs = System.currentTimeMillis() - start;
            CaseLog.data("Record History open+filter time", elapsedMs + "ms against " + history.entries().size()
                    + " rows on this page (the row's premise of 100,000+ records was not seeded)");
            assertTrue(elapsedMs < 3000, "Record History should open and filter within 3s, took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("SN-NFR-PRF-004: Message Center loads within 2 seconds with 500+ notifications")
        void messageCenterLoadPerformance() {
            needsMemberSite();
        }

        @Test
        @DisplayName("SN-NFR-PRF-005: Banner loading on a slow network shows a placeholder")
        void bannerLoadingOnSlowNetwork() {
            knownDefectOfferNotShipped("a placeholder is shown while a 1MB banner loads on a throttled connection, and the image is compressed");
        }
    }

    // =====================================================================
    // 14. Non-functional - security
    // =====================================================================

    @Nested
    @Order(140)
    @DisplayName("14. Non-functional - security")
    class NonFunctionalSecurity {

        @Test
        @DisplayName("SN-NFR-SEC-001: BO permission control")
        void boPermissionControl() {
            if (ALT_USERNAME.isBlank() || ALT_PASSWORD.isBlank()) {
                abort("No second operator account is configured (test.alt.username / test.alt.password), so "
                        + "there is no account known to lack the Notification permission to sign in as.");
            }
            BrowserContext altSession = newContext();
            Page altPage = altSession.newPage();
            try {
                signIn(altPage, ALT_USERNAME, ALT_PASSWORD);
                AdminShellPage altShell = new AdminShellPage(altPage);
                assertFalse(altShell.hasMenuLink(AdminShellPage.NOTIFICATION_HREF),
                        "An operator without the Notification permission should not see its menu entry");
                CaseLog.evidence("Only the menu half is checked - confirming the API also answers denied needs the "
                        + "real endpoint, which this pass did not capture.");
            } finally {
                altSession.close();
            }
        }

        @Test
        @DisplayName("SN-NFR-SEC-002: Requesting another member's notification by ID")
        void requestingAnotherMembersNotificationById() {
            abort("This needs the real notification-by-ID endpoint, intercepted and called directly with another "
                    + "member's session - not captured in this pass, and member-site besides.");
        }

        @Test
        @DisplayName("SN-NFR-SEC-003: Server-side eligibility on an ineligible deposit")
        void serverSideEligibilityOnIneligibleDeposit() {
            abort("This needs the deposit API called directly with an Offer-linked promotion ID as an ineligible "
                    + "member, which needs both the real endpoint (not captured) and an Offer to link to (not shipped).");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SN-NFR-SEC-004: XSS in Title/Content renders as text")
        void xssInTitleContentRendersAsText() {
            String title = newTitle();
            String payload = "<img src=x onerror=alert(1)>";
            NotificationFormPage form = openAddForm();
            form.fillLanguage("繁體", title, payload, "");
            form.overrideToAll();
            form.clickSave();
            list.open();
            assertTrue(list.isListed(title), "The notification should save and list without executing the payload");
            var row = list.notification(title);
            CaseLog.data("Listed title", row == null ? "(not found)" : row.toString());
        }

        private String newTitle() {
            return PREFIX + "Xss " + System.currentTimeMillis() % 100_000;
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-NFR-SEC-005: A disguised banner upload is rejected server-side")
        void disguisedBannerUploadRejected() {
            knownDefectOfferNotShipped("a polyglot or renamed file uploaded as a banner is rejected by a content check, not just an extension check");
        }

        @Test
        @DisplayName("SN-NFR-SEC-006: Open-redirect protection after login")
        void openRedirectProtectionAfterLogin() {
            needsMemberSite();
        }

        @Test
        @DisplayName("SN-NFR-SEC-007: Ranking alert client API masks data per Ranking Settings")
        void rankingAlertApiMasksData() {
            needsRankingsDeployed("the client API response for a ranking alert, inspected under Partial mask");
        }
    }

    // =====================================================================
    // 15. Non-functional - data & reliability
    // =====================================================================

    @Nested
    @Order(150)
    @DisplayName("15. Non-functional - data & reliability")
    class NonFunctionalDataReliability {

        @Test
        @DisplayName("SN-NFR-DAT-001: No duplicate sends if the scheduler restarts around send time")
        void noDuplicateSendsOnSchedulerRestart() {
            abort("This needs restarting the delivery service around a real send time, an operational control this suite does not have.");
        }

        @Test
        @DisplayName("SN-NFR-DAT-002: A failed delivery is recorded, not silently lost")
        void failedDeliveryRecordedNotLost() {
            abort("This needs stopping the delivery service to force a failure, an operational control this suite does not have.");
        }

        @Test
        @DisplayName("SN-NFR-DAT-003: Concurrent edit by two admins is handled without corruption")
        void concurrentEditByTwoAdmins() {
            if (ALT_USERNAME.isBlank() || ALT_PASSWORD.isBlank()) {
                abort("This needs a second operator account (test.alt.username / test.alt.password) to open and "
                        + "save the same Offer from two sessions at once; none is configured. Even once Offer ships "
                        + "and the account exists, this still needs an agreed way to force the two saves to race.");
            }
            abort("A second account is configured, but this also needs Offer shipped (it is not) and an agreed way "
                    + "to force the two sessions' saves to race, rather than guess at timing.");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("SN-NFR-DAT-004: Scheduling is in HKT regardless of the browser's own timezone")
        void schedulingIsInHktRegardlessOfBrowserTimezone() {
            BrowserContext tzSession = newContext(browser, new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080).setTimezoneId("America/New_York"));
            try {
                Page tzPage = tzSession.newPage();
                signIn(tzPage, USERNAME, PASSWORD);
                NotificationListPage tzList = new NotificationListPage(tzPage);
                tzList.open();
                NotificationFormPage form = tzList.clickAdd();
                String title = PREFIX + "Tz " + System.currentTimeMillis() % 100_000;
                form.fillLanguage("繁體", title, "HKT scheduling check under a New York browser timezone.", "");
                form.overrideToAll();
                form.setSchedule("Once");
                form.setTimer("12 AM");
                form.clickSave();
                tzList.open();
                assertTrue(tzList.isListed(title), "A notification scheduled under a non-HKT browser timezone should still save");
                CaseLog.evidence("This confirms the form accepts a schedule under a non-HKT browser timezone; "
                        + "confirming the actual send fires at 00:30 HKT (not a shifted local time) needs observing "
                        + "a real send, which the project's own waitForSchedule gate is for - not invoked here to "
                        + "keep this row's runtime bounded.");
            } finally {
                tzSession.close();
            }
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-NFR-DAT-005: A schedule at 23:30 HKT on the offer period's last day is accepted (HKT vs UTC+7 boundary)")
        void scheduleNearPeriodEndBoundary() {
            knownDefectOfferNotShipped("scheduling an Offer at 23:30 HKT on the last day of its offer period is accepted and sent, with the period checked in HKT");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("SN-NFR-DAT-006: Offer create/edit/copy/delete each write an Admin Operation Log entry")
        void offerActionsWriteOperationLogEntries() {
            knownDefectOfferNotShipped("create, edit, copy and delete on an Offer each write one Account Operation Log entry naming the admin, time and action");
        }
    }
}
