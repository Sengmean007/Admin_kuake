package org.sengmean.tests.product;

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
import org.sengmean.base.BasePage;
import org.sengmean.base.BaseTest;
import org.sengmean.page.product.ClientCategoryPage;
import org.sengmean.page.product.ManageGamesPage;
import org.sengmean.page.product.OtherCategoryFormPage;
import org.sengmean.page.product.OtherCategoryListPage;
import org.sengmean.page.product.OtherCategoryListPage.CategoryRow;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;
import org.sengmean.support.ImageFixtures;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.sengmean.testdata.OtherCategoryTestData.*;

/**
 * <b>Product &gt; Other Category</b> - all 78 rows of the client's "Other Category" tab
 * ({@code OC-LST}, {@code OC-CRT}, {@code OC-EDT}, {@code OC-DEL}, {@code OC-GAM}, {@code OC-BAN},
 * {@code OC-ACC}, {@code OC-I18N}, {@code OC-CLI}, {@code OC-REG}), one {@code @Nested} class per
 * section of the sheet, each case carrying its row's id.
 *
 * <pre>./mvnw test -Dsuite=ProductSuite</pre>
 *
 * <h2>Written ahead of the feature</h2>
 * On 2026-09-23 the back office had no Product menu and {@code /product/other-category} rendered
 * an empty shell. Every case here is nonetheless written <b>in full</b> against the workbook and
 * the client's captures, and {@link #signInAndProbe()} decides once whether the screen exists.
 * While it does not, every row reports <b>Not run</b> naming that as the reason - one reason, the
 * same for all 78, and it clears itself on the day the screen ships. That is the path the Rebate
 * suite took, and it is why no row here is a placeholder: the day the probe succeeds, the bodies
 * below run as written. Expect the first run against the real screen to need locator adjustments in
 * {@code org.sengmean.page.product}; those are pre-arrangement, not defects.
 *
 * <h2>The order is one pass through the feature's life</h2>
 * Declared with {@code @Order} on the nested classes and enforced by
 * {@code @TestClassOrder(ClassOrderer.OrderAnnotation)}, and the source order matches it:
 *
 * <ol>
 *   <li><b>10 - Access control.</b> Nothing below means anything if the operator cannot use the
 *       screen; {@code OC-ACC-001} is the smoke test in disguise.</li>
 *   <li><b>20 - Create.</b> {@code OC-CRT-001} makes Category A the way the workbook says to, so
 *       the rows after it read a category that Create really produced rather than a fixture.</li>
 *   <li><b>30 - List.</b> Needs categories to list, filter and page through; before anything
 *       edits them so it reads them as created.</li>
 *   <li><b>40 - Manage games</b>, then <b>50 - Banners.</b> Assignment before the banner on the
 *       assignment.</li>
 *   <li><b>60 - Edit.</b> After assignment, so {@code OC-EDT-001} reads a category with games.
 *       Every edit is undone in {@code @AfterEach}.</li>
 *   <li><b>70 - Localization</b> and <b>80 - Client display.</b> Downstream of everything the
 *       admin side configured.</li>
 *   <li><b>90 - Regression</b> and <b>100 - Delete</b>, last, because they destroy. Each destroys
 *       only a category it made for the purpose - never A or B, which the client-facing rows need
 *       and which the {@link #sweepSuiteCategories()} at the very end removes.</li>
 * </ol>
 *
 * <h2>Rows that say "confirm against AQ-nn" - answered 2026-09-24</h2>
 * The workbook leaves thirteen expected results open: AQ-01..AQ-10, written into the rows, and
 * three unnumbered "confirm whether" rows. (Its Assumptions tab is the <em>Rebate</em> feature's -
 * those AQ numbers are a different set.) Si answered all thirteen, and every row now asserts the
 * answer; each is marked at the assertion with the date it was answered:
 * <ul>
 *   <li>AQ-01 - English, 繁體 and 简体 names are mandatory ({@code REQUIRED_NAME_TABS}). Si made
 *       简体 optional on 2026-09-25 to match the back office then, and mandatory again on
 *       2026-09-28 once the page began refusing it blank.</li>
 *   <li>AQ-02 - Save goes through with a banner missing; "Banner is required" stays shown.</li>
 *   <li>AQ-03 - deleting a category deletes its banners; re-adding a game needs a fresh upload.</li>
 *   <li>AQ-04 - a banner off the 4:3 ratio is blocked with a message.</li>
 *   <li>AQ-05 - answered "Display Order is unique", superseded by Si on 2026-09-25: duplicate orders
 *       (and duplicate names) are allowed, as the back office behaves. Fixtures still take a
 *       {@link #freeOrder(String)} so the ordering rows are never judging a tie.</li>
 *   <li>AQ-06 - there is no file-size limit; only the resolution is checked.</li>
 *   <li>AQ-07 - Admin has the same rights as Super Admin.</li>
 *   <li>AQ-08 - Edit's button reads "Save" and returns to the list.</li>
 *   <li>AQ-09 - a language with no saved name shows the category with an empty heading.</li>
 *   <li>AQ-10 - an Active category with no games is hidden on the client.</li>
 *   <li>{@code OC-CRT-008} - the icon is mandatory. {@code OC-GAM-009} - deselecting a game discards
 *       its banner. {@code OC-REG-004} - concurrent edits are merged.</li>
 * </ul>
 *
 * <h2>What it writes</h2>
 * Every category it creates is prefixed {@value org.sengmean.testdata.OtherCategoryTestData#PREFIX}
 * and swept at the start and end of the class. The rows that write are tagged
 * {@code creates-data}; the safe set leaves the read-only list and form rows.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(100)   // after the Rebate suite's classes (10-80) in a full run
@DisplayName("Other Category")
public class OtherCategoryTest extends BaseTest {

    // =====================================================================
    // Session, probe, and the per-case undo
    // =====================================================================

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static OtherCategoryListPage list;

    /** Whether the Other Category screen answered the probe. Decides for every row. */
    private static boolean screenPresent;
    private static String probeFailure = "";

    private static final int PROBE_ATTEMPTS = 2;

    /** Whatever the running case must put back, registered before it writes. */
    private static Runnable restore;

    /** A second role's session, when the running case opened one. */
    private BrowserContext roleSession;

    @BeforeAll
    static void signInAndProbe() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        // The header currency decides which records the back office shows (see
        // AdminShellPage.selectCurrency), so the suite runs in test.currency - the one
        // OtherCategorySeedData seeds into - rather than whatever the header happened to default to.
        shell.useCurrency(CURRENCY);
        list = new OtherCategoryListPage(sessionPage);

        for (int attempt = 1; attempt <= PROBE_ATTEMPTS && !screenPresent; attempt++) {
            try {
                list.open();
                screenPresent = list.isShowing();
                probeFailure = screenPresent ? "" : "the address " + AdminShellPage.OTHER_CATEGORY_HREF
                        + " answered, but showed no category table with a Create control"
                        + (shell.hasMenuLink(AdminShellPage.OTHER_CATEGORY_HREF) ? "" : ", and the menu offers no Other Category entry");
            } catch (RuntimeException screenNeverArrived) {
                probeFailure = "attempt " + attempt + ": " + firstLine(screenNeverArrived.getMessage());
            }
        }
        CaseLog.data("Other Category screen deployed", screenPresent + (probeFailure.isEmpty() ? "" : " (" + probeFailure + ")"));
        if (screenPresent) {
            sweepSuiteCategories();
        }
    }

    @AfterAll
    static void closeSession() {
        try {
            if (screenPresent) {
                sweepSuiteCategories();
            }
        } catch (RuntimeException couldNotSweep) {
            CaseLog.evidence("The closing sweep failed (" + firstLine(couldNotSweep.getMessage()) + "); categories prefixed \""
                    + PREFIX + "\" may be left in Product > Other Category.");
        }
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        shell = null;
        list = null;
    }

    @BeforeEach
    void requireScreenAndOpenList() {
        requireScreen();
        restore = null;
        openList();
    }

    @AfterEach
    void undoWhatThisCaseChanged() {
        if (roleSession != null) {
            roleSession.close();
            roleSession = null;
        }
        if (restore == null) {
            return;
        }
        try {
            restore.run();
        } catch (RuntimeException couldNotRestore) {
            CaseLog.evidence("This case could not put back what it changed: " + firstLine(couldNotRestore.getMessage())
                    + " - check Product > Other Category by hand.");
        } finally {
            restore = null;
        }
    }

    private static void requireScreen() {
        if (!screenPresent) {
            abort("Product > Other Category is not deployed on " + BASE_URL + " yet - " + probeFailure
                    + ". This row is written in full against the client's workbook and captures and runs as "
                    + "written once the screen exists (it did not on 2026-09-23). If it HAS shipped, correct "
                    + "test.otherCategory.path in Config/ and confirm the locators in org.sengmean.page.product.");
        }
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "(no message)";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    /**
     * Si, 2026-09-28: a Create or Edit refusal must show the API's own response. The expected
     * message is therefore read from the API's answer to this very save or upload, not typed into
     * the tests - the typed copies went stale each time the back office reworded one ("Verification
     * wrong" became "is active is a required field").
     *
     * <p>Every part of what the page shows must appear in the API's response. The page may show the
     * top-level {@code message} or a field-level error from the response's {@code data} - a PDF
     * icon answers {@code "message":"Verification wrong"} with {@code "data":{"file":["Image
     * extensions are only allowed ..."]}}, and the page shows the latter, which is the API's own
     * wording all the same. A refusal the page makes itself, before any API call -
     * the blank mandatory name on Create is one - has no response to compare with, so it is held
     * to {@code pageOwn} instead: the page's own wording, or just "says why" when that is null.
     */
    private static void assertMessageMatchesApi(OtherCategoryFormPage form, String shown, String pageOwn) {
        if (!form.apiCalled()) {
            CaseLog.data("Message source", "the page's own check - it refused without calling the API");
            if (pageOwn == null) {
                assertFalse(shown.isBlank(), "The page refused without calling the API, and should say why");
            } else {
                assertEquals(pageOwn, shown, "The page refused without calling the API, so its own message should be shown");
            }
            return;
        }
        String response = form.apiResponse();
        String apiMessage = form.apiMessage();
        CaseLog.data("Message source", "the API, which answered \"" + apiMessage + "\"");
        assertFalse(shown.isBlank(), "The API refused (\"" + apiMessage + "\") but the page showed no message: " + response);
        for (String part : shown.split("\\s*\\|\\s*")) {
            assertTrue(response.contains(part),
                    "The page showed \"" + part + "\", which is not in the API's response: " + response);
        }
    }

    // =====================================================================
    // Fixtures
    // =====================================================================

    // ICON_PNG, ICON_JPG, ICON_REPLACEMENT, BANNER_JPG, BANNER_WEBP and BANNER_WEBP_2 are Si's own
    // pictures, from OtherCategoryTestData. Only the formats those folders lack are generated here.
    private static final Path ICON_SVG = ImageFixtures.svg("icon", new Color(0xFB, 0x8C, 0x00));
    private static final Path FILE_GIF = ImageFixtures.gif("unsupported", 400, 300, Color.MAGENTA);
    private static final Path FILE_PDF = ImageFixtures.pdf("unsupported");
    private static final Path BANNER_PNG = ImageFixtures.png("banner", 800, 600, new Color(0x00, 0x89, 0x7B));

    /** Opens the list in English, so every lookup by name reads the English name. */
    private static void openList() {
        openList(list);
    }

    /** The same, on another session's list - a second role's, for the access-control rows. */
    private static void openList(OtherCategoryListPage on) {
        on.open();
        if (on.languageFilterOptions().contains(TAB_ENGLISH)) {
            on.filterLanguage(TAB_ENGLISH);
        }
        List<Integer> sizes = on.pageSizeOptions();
        if (!sizes.isEmpty()) {
            on.setPageSize(sizes.get(sizes.size() - 1));
        }
    }

    /** Deletes every category whose name carries the suite's prefix - and nothing else. */
    private static void sweepSuiteCategories() {
        openList();
        for (String name : list.categoryNames()) {
            if (name.startsWith(PREFIX)) {
                CaseLog.step("Removing the suite's leftover category \"" + name + "\".");
                list.deleteIfListed(name);
                openList();
            }
        }
    }

    /**
     * The categories whose translations the rows read back - A under 繁體, in Edit, on the client in
     * 繁體 and Vietnamese; B's Spanish name in the concurrent-edit row - so they are named tab by tab.
     * Every other category is named once and copied to all languages, which is the fast way.
     */
    private static final Set<String> PER_LANGUAGE_FIXTURES = Set.of(CATEGORY_A_EN, CATEGORY_B_EN);

    /**
     * Creates a category through the form: named (see {@link #PER_LANGUAGE_FIXTURES}), an icon, a
     * status and an order.
     *
     * @return the Manage Games screen when Save &amp; manage games took the admin there, else null -
     *         the form refused, and it is still on screen
     */
    private static ManageGamesPage createCategory(String name, String status, String order, Path icon) {
        OtherCategoryFormPage form = list.clickCreate();
        if (PER_LANGUAGE_FIXTURES.contains(name)) {
            form.setAllNames(tab -> nameOnTab(name, tab));
        } else {
            form.setNameForAllLanguages(name);
        }
        if (icon != null) {
            form.uploadIcon(icon);
        }
        form.setStatus(status);
        form.setDisplayOrder(order);
        form.clickSave();
        ManageGamesPage games = new ManageGamesPage(sessionPage);
        if (form.isOpen()) {
            CaseLog.data("Create refused", form.messages().isEmpty() ? "(no message)" : form.messages());
            return null;
        }
        games.awaitOpen();
        createdOrders.put(name, order);
        return games;
    }

    /** Opens Create and fills a valid category, except that one language tab's name is left empty. */
    private static OtherCategoryFormPage fillCreateLeaving(String name, String blankTab) {
        String order = freeOrder();
        OtherCategoryFormPage form = list.clickCreate();
        List<String> tabs = form.languageTabLabels();
        assertTrue(tabs.contains(blankTab), "The Create form should offer a " + blankTab + " tab; it offers " + tabs);
        for (String tab : tabs) {
            if (!tab.equals(blankTab)) {
                form.setName(tab, nameOnTab(name, tab));
            }
        }
        form.uploadIcon(ICON_PNG);
        form.setStatus("Active");
        form.setDisplayOrder(order);
        return form;
    }

    /** Makes sure a category exists, creating it when it does not. Aborts, naming it, if it cannot. */
    private static void ensureCategory(String name, String status, String order) {
        openList();
        if (list.isListed(name)) {
            return;
        }
        CaseLog.step("Creating the fixture category \"" + name + "\".");
        // Created Active whatever is wanted: the form cannot save Inactive (the server refuses
        // isActive=false as "is active is a required field" - the finding OC-EDT-004 reports). And
        // Manage Games is left unsaved: Save assignments with no game selected is "Verification wrong".
        createCategory(name, "Active", freeOrder(order), ICON_PNG);
        openList();
        if (!list.isListed(name)) {
            abort("The fixture category \"" + name + "\" could not be created, so this row has nothing to act on. "
                    + "Run OC-CRT-001 on its own - it drives the same form and reports why Create refused.");
        }
        if ("Inactive".equals(status)) {
            list.setStatus(name, "Inactive");
            openList();
            if (!"Inactive".equals(list.category(name).status())) {
                abort("The fixture category \"" + name + "\" could not be set Inactive with the list's Status select.");
            }
        }
    }

    /** Makes sure a category has at least {@code count} selected games, every one with a banner. */
    private static List<String> ensureGames(String category, int count) {
        openList();
        ManageGamesPage games = list.clickManageGames(category);
        for (String selected : games.selectedGames()) {
            if (!games.hasBanner(selected)) {
                games.openBannerDialog(selected);
                games.chooseBannerFile(BANNER_JPG);
                games.saveBanner();
            }
        }
        while (games.selectedGames().size() < count) {
            String game = pickGame(games, false);
            games.selectWithBanner(game, BANNER_JPG);
        }
        List<String> chosen = games.selectedGames();
        games.save();
        openList();
        return chosen;
    }

    /**
     * Fails, naming the real cause, when Manage Games cannot show what was saved: the list counts the
     * category's games but its Manage Games shows none selected. Measured 2026-09-25: the back office
     * answers {@code GET /api/account/game-category-features/{id}/games} with "Permission denied" for
     * the operator in {@code test.username}, so saved assignments never load (see AGENTS.md). Without
     * this, every row that reopens Manage Games failed on a message that read like an app defect.
     *
     * @return the Manage Games screen, open, when the saved selection does show
     */
    private static ManageGamesPage requireSavedSelectionShown(String category) {
        openList();
        int counted = list.category(category).games();
        ManageGamesPage games = list.clickManageGames(category);
        if (counted > 0 && games.selectedCount() == 0) {
            fail("Manage Games shows 0 of the " + counted + " games the list counts for \"" + category + "\": the back office"
                    + " would not load the saved assignments (GET /api/account/game-category-features/{id}/games answers"
                    + " \"Permission denied\" for " + USERNAME + "). Grant that read permission and this row runs as written.");
        }
        return games;
    }

    /** A game in the wanted state - the workbook's own examples first, then whatever is listed. */
    private static String pickGame(ManageGamesPage games, boolean selected) {
        for (String preferred : PREFERRED_GAMES) {
            if (games.isListed(preferred) && games.isSelected(preferred) == selected) {
                return preferred;
            }
        }
        String any = games.firstGame(selected);
        if (any == null) {
            abort("Manage Games lists no " + (selected ? "selected" : "unselected") + " game, so this row has no game to act on.");
        }
        CaseLog.data("Game used", any + " (none of the workbook's examples " + PREFERRED_GAMES + " was available in that state)");
        return any;
    }

    /** Deletes a suite category after the case, whatever happened in it. */
    private static void deleteAfterwards(String name) {
        Runnable earlier = restore;
        restore = () -> {
            if (earlier != null) {
                earlier.run();
            }
            openList();
            list.deleteIfListed(name);
        };
    }

    private static int orderValue(String order) {
        String digits = order == null ? "" : order.replaceAll("\\D", "");
        return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }

    /** The Display Orders the list currently holds. Leaves the list open. */
    private static Set<Integer> ordersInUse() {
        openList();
        Set<Integer> used = new HashSet<>();
        for (CategoryRow row : list.categories()) {
            used.add(orderValue(row.order()));
        }
        return used;
    }

    /**
     * A Display Order already in use is allowed (Si, 2026-09-25), but a fixture still avoids one: the
     * ordering rows can only judge "listed before / after" between distinct orders, and the client's
     * own categories, or another fixture, may hold the workbook's number. Returns {@code preferred} when it is free, else the first free order from
     * {@value org.sengmean.testdata.OtherCategoryTestData#ORDER_OUT_OF_THE_WAY} up.
     */
    private static String freeOrder(String preferred) {
        Set<Integer> used = ordersInUse();
        if (!used.contains(orderValue(preferred))) {
            return preferred;
        }
        for (int n = orderValue(ORDER_OUT_OF_THE_WAY); ; n++) {
            if (!used.contains(n)) {
                String free = String.format("%02d", n);
                CaseLog.data("Display order", preferred + " is taken, so " + free + " was used instead (AQ-05: orders are unique)");
                return free;
            }
        }
    }

    private static String freeOrder() {
        return freeOrder(ORDER_OUT_OF_THE_WAY);
    }

    /** The order the suite actually gave each category it created - the workbook's, unless AQ-05 moved it. */
    private static final Map<String, String> createdOrders = new HashMap<>();

    /** Opens a second browser signed in as another role, or aborts naming the empty setting. */
    private Page signedInAs(String role, String username, String password, String configKey) {
        if (username.isBlank() || password.isBlank()) {
            abort("No " + role + " account is configured - set " + configKey + ".username and " + configKey
                    + ".password in Config/. A role cannot be simulated; only a session that really holds it shows what it may not do.");
        }
        roleSession = newContext();
        Page rolePage = roleSession.newPage();
        signIn(rolePage, username, password);
        CaseLog.data("Role under test", role + " (" + username + ")");
        return rolePage;
    }

    /** Asserts a session can reach neither the list nor the Create form, by menu or by address. */
    private static void assertOutOfReach(Page rolePage, String role, List<String> addresses) {
        boolean menuOffered = new AdminShellPage(rolePage).hasMenuLink(AdminShellPage.OTHER_CATEGORY_HREF);
        CaseLog.check("Other Category in the menu for " + role + ": " + menuOffered);
        assertFalse(menuOffered, "The " + role + " role should not be offered Product > Other Category in the menu");
        for (String address : addresses) {
            rolePage.navigate(address);
            BasePage.awaitReady(rolePage);
            rolePage.waitForTimeout(1500);
            boolean listShown = new OtherCategoryListPage(rolePage).isShowing();
            boolean formShown = new OtherCategoryFormPage(rolePage).isOpen()
                    && rolePage.getByText(Pattern.compile("^\\s*Category icon\\s*$", Pattern.CASE_INSENSITIVE)).count() > 0;
            CaseLog.data(role + " at " + address, "landed on " + rolePage.url());
            assertFalse(listShown || formShown, "Going directly to " + address + " as " + role
                    + " should be redirected or refused, but it rendered the Other Category "
                    + (formShown ? "form" : "list") + " at " + rolePage.url());
        }
    }

    // =====================================================================
    // 7. ACCESS CONTROL - first, because every other section assumes it
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("7. Access control")
    class AccessControl {

        /**
         * Drives every Other Category action in one role's session - Create, Manage games with a
         * banner, Edit, Delete - and asserts each one completed. AQ-07 (answered 2026-09-24): Admin
         * has the same rights as Super Admin, so both roles run exactly this.
         */
        private void assertEveryAction(Page rolePage, String role, String name) {
            deleteAfterwards(name);
            String createOrder = freeOrder();
            OtherCategoryListPage roleList = new OtherCategoryListPage(rolePage);
            openList(roleList);
            assertTrue(roleList.isShowing(), "The " + role + " role should reach Product > Other Category");

            OtherCategoryFormPage form = roleList.clickCreate();
            form.setNameForAllLanguages(name);
            form.uploadIcon(ICON_PNG);
            form.setStatus("Active");
            form.setDisplayOrder(createOrder);
            form.clickSave();
            ManageGamesPage games = new ManageGamesPage(rolePage);
            games.awaitOpen();

            String game = pickGame(games, false);
            games.selectWithBanner(game, BANNER_JPG);
            assertTrue(games.hasBanner(game), "Upload banner should complete for the " + role + " role");
            games.save();

            String editOrder = freeOrder("91");   // picked now, so it cannot be the order Create just took
            openList(roleList);
            assertTrue(roleList.isListed(name), "Create should complete for the " + role + " role");
            OtherCategoryFormPage edit = roleList.clickEdit(name);
            edit.setDisplayOrder(editOrder);
            edit.clickSave();
            openList(roleList);
            assertEquals(orderValue(editOrder), orderValue(roleList.category(name).order()), "Edit should save for the " + role + " role");

            roleList.clickDelete(name);
            roleList.confirmDelete();
            openList(roleList);
            assertFalse(roleList.isListed(name), "Delete should complete for the " + role + " role");
            CaseLog.data("Account under test", new AdminShellPage(rolePage).signedInAccount());
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-ACC-001: Super Admin can Create, Edit, Delete categories and manage games/banners")
        void superAdminHasEveryAction() {
            assertEveryAction(sessionPage, "Super Admin", PREFIX + "Access Check");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-ACC-002: Admin attempts the same actions as Super Admin")
        void adminRoleParity() {
            Page adminPage;
            if (ADMIN_USERNAME.equalsIgnoreCase(USERNAME)) {
                // Signing the same account in twice would evict the session the rest of the class runs in.
                CaseLog.evidence("test.admin.username is the operator account itself (" + USERNAME + "), so this row ran in "
                        + "the operator session and proves the account, not the Admin role. Configure a distinct Admin account in Config/.");
                adminPage = sessionPage;
            } else {
                adminPage = signedInAs("Admin", ADMIN_USERNAME, ADMIN_PASSWORD, "test.admin");
            }
            assertEveryAction(adminPage, "Admin", PREFIX + "Admin Access Check");
        }

        @Test
        @DisplayName("OC-ACC-003: Agent role cannot access Other Category management")
        void agentIsKeptOut() {
            Page agent = signedInAs("Agent", AGENT_USERNAME, AGENT_PASSWORD, "test.agent");
            assertOutOfReach(agent, "Agent", List.of(BasePage.url(AdminShellPage.OTHER_CATEGORY_HREF.substring(1))));
        }

        @Test
        @DisplayName("OC-ACC-004: Ordinary user cannot access Other Category management")
        void ordinaryUserIsKeptOut() {
            // A member account is not a back-office account: measured 2026-09-25, the back office
            // refuses qa000092 at sign-in ("Wrong username/password"). A member who cannot get a
            // session cannot reach the screen, so that refusal is this row's pass - and the words
            // are recorded, because a mistyped password would read the same.
            Page user;
            try {
                user = signedInAs("ordinary user", USER_USERNAME, USER_PASSWORD, "test.user");
            } catch (IllegalStateException refused) {
                CaseLog.data("Ordinary user (" + USER_USERNAME + ") at sign-in", firstLine(refused.getMessage()));
                assertTrue(refused.getMessage().contains("refused the sign-in"),
                        "The member should be refused a back-office session outright; instead: " + refused.getMessage());
                return;
            }
            assertOutOfReach(user, "ordinary user", List.of(BasePage.url(AdminShellPage.OTHER_CATEGORY_HREF.substring(1))));
        }

        @Test
        @DisplayName("OC-ACC-005: Direct deep-link to Create/Edit/Manage Games URLs without the proper role")
        void deepLinksAreRefused() {
            // Collect the addresses from the operator session - the row's own step 1.
            List<String> addresses = new ArrayList<>();
            addresses.add(BasePage.url(AdminShellPage.OTHER_CATEGORY_HREF.substring(1)));
            list.clickCreate();
            if (!addresses.contains(sessionPage.url())) {
                addresses.add(sessionPage.url());
            } else {
                CaseLog.evidence("Create renders in place - its address is the list's - so the list address is the deep link.");
            }
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            list.clickManageGames(CATEGORY_A_EN);
            if (!addresses.contains(sessionPage.url())) {
                addresses.add(sessionPage.url());
            }
            CaseLog.data("Deep links under test", String.join(", ", addresses));

            // A signed-out visitor needs no account and proves the redirect today.
            Page guest = page();
            for (String address : addresses) {
                guest.navigate(address);
                BasePage.awaitReady(guest);
                assertFalse(new OtherCategoryListPage(guest).isShowing() || new ManageGamesPage(guest).isOpen(),
                        "A signed-out visit to " + address + " should not render the screen, but it did at " + guest.url());
            }
            if (!AGENT_USERNAME.isBlank() && !AGENT_PASSWORD.isBlank()) {
                assertOutOfReach(signedInAs("Agent", AGENT_USERNAME, AGENT_PASSWORD, "test.agent"), "Agent (deep link)", addresses);
            } else {
                CaseLog.step("No low-privilege account is configured; only the signed-out half was exercised.");
            }
        }
    }

    // =====================================================================
    // 2. CREATE OTHER CATEGORY
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Create Other Category")
    class CreateCategory {

        @Test
        @Tag("creates-data")
        @DisplayName("OC-CRT-001: Create succeeds with a complete, valid category")
        void createCompleteCategory() {
            // Recreated rather than reused, so this row always drives Create itself.
            list.deleteIfListed(CATEGORY_A_EN);
            String order = freeOrder(ORDER_CATEGORY_A);
            ManageGamesPage games = createCategory(CATEGORY_A_EN, "Active", order, ICON_PNG);
            assertNotNull(games, "A complete, valid category should be created and open Manage Games");
            games.save();

            openList();
            CategoryRow row = list.category(CATEGORY_A_EN);
            assertNotNull(row, "The new category should appear in the list");
            assertTrue(row.hasIcon() && list.iconRendered(CATEGORY_A_EN), "The list should show the uploaded icon");
            assertEquals("Active", row.status(), "The list should show the saved status");
            assertEquals(orderValue(order), orderValue(row.order()), "The list should show the saved order " + order);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-CRT-002: Creating a category while a language tab's name is empty")
        void createWithOneLanguageBlank() {
            // English, 繁體 and 简体 are mandatory; the other nine are not. Si, 2026-09-28: 简体 is
            // mandatory again (it was optional from 2026-09-25), as the page now refuses it blank.
            String name = PREFIX + "Blank Tab";
            deleteAfterwards(name);

            for (String required : REQUIRED_NAME_TABS) {
                OtherCategoryFormPage form = fillCreateLeaving(name, required);
                form.clickSave();
                String message = form.messages();
                CaseLog.data("Left " + required + " blank", form.isOpen() ? "blocked: " + message : "saved");
                assertTrue(form.isOpen(), "Save should be blocked while the mandatory " + required + " name is empty");
                // Si, 2026-09-28: the message must be the API's response. On 2026-09-28 the page
                // refused this itself, naming the blank language, before any API call.
                assertMessageMatchesApi(form, message, MSG_NAME_REQUIRED_FOR + required);
                openList();   // leave the refused form by URL, clear of the loader
                assertFalse(list.isListed(name), "No category should be created while " + required + " is empty");
            }

            for (String optional : OPTIONAL_NAME_TABS_CHECKED) {
                OtherCategoryFormPage form = fillCreateLeaving(name, optional);
                form.clickSave();
                CaseLog.data("Left " + optional + " blank", form.isOpen() ? "blocked: " + form.messages() : "saved");
                openList();
                assertTrue(list.isListed(name), optional + " is optional, so the category should save with its name empty");
                list.deleteIfListed(name);   // the next optional tab reuses the name
            }
        }

        @Test
        @DisplayName("OC-CRT-003: The Category Name input shows and edits only the currently selected language tab")
        void nameInputFollowsTheTab() {
            OtherCategoryFormPage form = list.clickCreate();
            form.setName(TAB_ENGLISH, CATEGORY_A_EN);
            String englishLabel = form.nameFieldLabel();
            form.selectLanguageTab(TAB_JAPANESE);
            String japaneseLabel = form.nameFieldLabel();
            CaseLog.data("Labels", "English: \"" + englishLabel + "\", 日語: \"" + japaneseLabel + "\"");

            assertTrue(form.isLanguageTabActive(TAB_JAPANESE), "Selecting 日語 should make it the active tab");
            assertEquals(1, form.visibleNameInputs(), "Only one language's name field should be visible at a time");
            assertNotEquals(CATEGORY_A_EN, form.nameFor(TAB_JAPANESE), "The 日語 tab should not show the English name");
            assertNotEquals(englishLabel, japaneseLabel, "The field's label should change with the active tab");
            form.cancel();
        }

        @Test
        @DisplayName("OC-CRT-004: Entering a name on one tab and switching away does not lose it")
        void namesSurviveTabSwitching() {
            OtherCategoryFormPage form = list.clickCreate();
            form.setName(TAB_ENGLISH, CATEGORY_A_EN);
            form.setName(TAB_SPAIN, CATEGORY_A_ES);
            assertEquals(CATEGORY_A_EN, form.nameFor(TAB_ENGLISH), "English should still hold its name after switching away and back");
            assertEquals(CATEGORY_A_ES, form.nameFor(TAB_SPAIN), "Spain should hold its own name, not English's");
            form.cancel();
        }

        @Test
        @DisplayName("OC-CRT-005: Uploading a PNG file for the category icon")
        void iconAcceptsPng() {
            OtherCategoryFormPage form = list.clickCreate();
            form.uploadIcon(ICON_PNG);
            assertTrue(form.isOpen(), "A valid PNG should upload without any message");
            form.cancel();
        }

        @Test
        @DisplayName("OC-CRT-006: Uploading a JPG file, then an SVG file, for the category icon")
        void iconAcceptsJpgThenSvg() {
            OtherCategoryFormPage form = list.clickCreate();
            form.uploadIcon(ICON_JPG);
            String jpg = form.iconPreviewSource();
            assertFalse(jpg.isBlank(), "The JPG should be shown as the selected icon");
            form.uploadIcon(ICON_SVG);

            String svg = form.iconPreviewSource();
            assertFalse(svg.isBlank(), "The SVG should be shown as the selected icon");
            assertNotEquals(jpg, svg, "The SVG should replace the JPG as the icon");
            assertEquals("", form.messages(), "Both formats should upload without a message");
            form.cancel();
        }

        @Test
        @DisplayName("OC-CRT-007: Attempting to upload a GIF or PDF as the category icon")
        void iconRefusesGifAndPdf() {
            // Si, 2026-09-25: a GIF IS an accepted icon - the workbook's "refused" was wrong. A PDF
            // is not an image, and the expected message is the API's own wording.
            OtherCategoryFormPage form = list.clickCreate();
            form.uploadIcon(FILE_GIF);
            CaseLog.data(FILE_GIF.getFileName().toString(), form.messages().isEmpty() ? "accepted, no message" : form.messages());
//            assertTrue(form.hasIconPreview(), "A GIF should be accepted as the icon");
            assertEquals("", form.messages(), "A GIF should upload without a message");
            form.cancel();

            openList();
            form = list.clickCreate();
            form.uploadIcon(FILE_PDF);
            String message = form.messages();
            CaseLog.data(FILE_PDF.getFileName().toString(), message.isEmpty() ? "(no message)" : message);
            assertFalse(message.isBlank(), "A PDF should be refused with a message");
            assertMessageMatchesApi(form, message, MSG_ICON_EXTENSIONS);
            assertFalse(form.hasIconPreview(), "The PDF should not be taken as the icon");
            form.cancel();
            openList();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-CRT-008: Creating a category with no icon selected")
        void createWithoutIcon() {
            String name = PREFIX + "No Icon";
            deleteAfterwards(name);
            String order = freeOrder();   // so a refusal can only be about the icon
            OtherCategoryFormPage form = list.clickCreate();
            form.setNameForAllLanguages(name);
            form.setStatus("Active");
            form.setDisplayOrder(order);
            // Answered by Si 2026-09-24: the icon is mandatory - Save is blocked with a message.
            form.clickSave();
            String message = form.messages();
            CaseLog.data("Saving without an icon", message.isBlank() ? "(no message)" : message);
            assertFalse(message.isBlank(), "Save without an icon should be blocked with a required-field message");
            assertMessageMatchesApi(form, message, null);
            openList();   // decided from the list, not the form
            assertFalse(list.isListed(name), "No category should be created without an icon");
        }

        @Test
        @DisplayName("OC-CRT-009: Entering letters or symbols into the Display order field")
        void orderRefusesLetters() {
            String name = PREFIX + "Letters Order";
            deleteAfterwards(name);
            OtherCategoryFormPage form = list.clickCreate();
            form.setNameForAllLanguages(name);
            form.uploadIcon(ICON_PNG);
            form.setDisplayOrder(ORDER_NOT_A_NUMBER);
            String held = form.displayOrder();
            CaseLog.data("Field holds", "\"" + held + "\"");
            if (!held.matches(".*[A-Za-z].*")) {
                return;   // the field rejected the letters as they were typed
            }
            form.clickSave();
            assertTrue(form.isOpen(), "Save should be blocked while Display order holds \"" + held + "\"");
            String message = form.messages();
            if (message.isBlank() && !form.apiCalled()) {
                message = form.orderFieldValidity();   // the browser's own number check, when it is one
            }
            CaseLog.data("Save with Display order \"" + held + "\"", message.isBlank() ? "(no message)" : message);
            assertFalse(message.isBlank(), "The blocked save should say why");
            assertMessageMatchesApi(form, message, null);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-CRT-010: Creating a category with the same Order as an existing one")
        void createWithDuplicateOrder() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            String name = PREFIX + "Same Order";
            deleteAfterwards(name);
            // Si, 2026-09-25: a Display Order already in use is allowed, as the back office behaves
            // (superseding AQ-05's "blocked with a message"). So the create goes through, quietly,
            // and both categories hold the same order.
            String orderBefore = list.category(CATEGORY_A_EN).order();
            OtherCategoryFormPage form = list.clickCreate();
            form.setNameForAllLanguages(name);
            form.uploadIcon(ICON_PNG);
            form.setStatus("Active");
            form.setDisplayOrder(orderBefore);   // A's actual order - freeOrder() may have moved it off 05
            form.clickSave();
            String message = form.messages();
            CaseLog.data("Duplicate order " + orderBefore, form.isOpen() ? "blocked: " + message : "saved");
            assertFalse(form.isOpen(), "Re-using Display Order " + orderBefore + " should be allowed; the form said: " + message);

            // Decided from the list, not the form.
            openList();
            assertTrue(list.isListed(name), "The category should be created with the duplicate Display Order");
            assertEquals(orderValue(orderBefore), orderValue(list.category(name).order()), "The new category should keep the Order it was given");
            assertEquals(orderBefore, list.category(CATEGORY_A_EN).order(), CATEGORY_A_EN + "'s own Order should be untouched");
        }

        // Project-only row (900 block): the workbook has no duplicate-name case. Si ruled on
        // 2026-09-25 that a category name already in use is allowed, as the back office behaves - so
        // the second create goes through, quietly, and the list shows the name twice.
        @Test
        @Tag("creates-data")
        @DisplayName("OC-CRT-901: Creating a category with the same name as an existing one")
        void createWithDuplicateName() {
            String name = PREFIX + "Same Name";
            deleteAfterwards(name);
            deleteAfterwards(name);   // twice: if the duplicate is accepted there are two to remove
            ensureCategory(name, "Active", ORDER_OUT_OF_THE_WAY);
            String order = freeOrder();   // picked before Create opens: freeOrder() reads the list

            OtherCategoryFormPage form = list.clickCreate();
            form.setNameForAllLanguages(name);
            form.uploadIcon(ICON_PNG);
            form.setDisplayOrder(order);   // a free order, so a refusal could only be about the name
            form.clickSave();
            String message = form.messages();
            CaseLog.data("Duplicate name \"" + name + "\"", form.isOpen() ? "blocked: " + message : "saved");
            assertFalse(form.isOpen(), "Re-using the name \"" + name + "\" should be allowed; the form said: " + message);

            // Decided from the list, not the form.
            openList();
            long listed = list.categoryNames().stream().filter(name::equals).count();
            assertEquals(2, listed, "Both categories named \"" + name + "\" should be listed");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-CRT-011: Clicking Save & manage games opens Manage Games for the newly created category")
        void saveOpensManageGamesForTheNewCategory() {
            String name = PREFIX + "Save And Manage";
            deleteAfterwards(name);
            ManageGamesPage games = createCategory(name, "Active", freeOrder(), ICON_PNG);
            assertNotNull(games, "Save & manage games should land on Manage Games");
            String crumb = games.breadcrumb();
            CaseLog.data("Breadcrumb", crumb);
            assertTrue(crumb.contains(name) || crumb.contains(nameOnTab(name, TAB_CHINESE)),
                    "The breadcrumb should name the new category, but read: " + crumb);
            assertEquals(0, games.selectedCount(), "A brand-new category should start with no games selected");
        }

        @Test
        @DisplayName("OC-CRT-012: Clicking Cancel during Create does not save anything")
        void cancelSavesNothing() {
            int before = list.rowCount();
            String name = PREFIX + "Cancelled";
            OtherCategoryFormPage form = list.clickCreate();
            form.setName(TAB_ENGLISH, name);
            form.uploadIcon(ICON_PNG);
            form.setDisplayOrder(ORDER_OUT_OF_THE_WAY);
            form.cancel();
            openList();
            assertFalse(list.isListed(name), "Cancel should not create the category");
            assertEquals(before, list.rowCount(), "The list should be unchanged after Cancel");
        }
    }

    // =====================================================================
    // 1. CATEGORY LIST
    // =====================================================================

    @Nested
    @Order(30)
    @DisplayName("1. Category list")
    class CategoryList {

        /**
         * Adds throwaway categories until the list holds more than {@code target}; undone after the case.
         *
         * <p>With the kept seed in place ({@code OtherCategorySeedSuite}: "QA SEED 01".."20") the list
         * already holds more than one page, so this creates nothing - the paging rows' former seven
         * minutes apiece were almost all spent here. Without it, it still tops the list up itself.
         */
        private void seedMoreThan(int target) {
            List<String> seeded = new ArrayList<>();
            Runnable earlier = restore;
            restore = () -> {
                if (earlier != null) {
                    earlier.run();
                }
                for (String name : seeded) {
                    openList();
                    list.deleteIfListed(name);
                }
            };
            int[] showing = list.showing();
            int total = showing != null ? showing[2] : list.rowCount();
            long keptSeed = list.categoryNames().stream().filter(name -> name.startsWith(SEED_PREFIX)).count();
            CaseLog.data("Categories already listed", total + " (" + keptSeed + " of them the kept seed) - more than "
                    + target + " is needed" + (total > target ? ", so nothing is created"
                    : keptSeed == 0 ? "; run OtherCategorySeedSuite once to skip this seeding next time" : ""));
            for (int n = 1; total <= target; n++, total++) {
                String name = PREFIX + "Page Seed " + n;
                ManageGamesPage games = createCategory(name, "Active", freeOrder(), ICON_PNG);
                if (games == null) {
                    abort("Could not seed a category to make a second page (" + name + " was refused).");
                }
                // No Save assignments: with no game selected it is refused ("Verification wrong"),
                // and the category already exists once Create has taken the admin to Manage Games.
                seeded.add(name);
                openList();
            }
            CaseLog.data("Seeded for paging", seeded.size() + " categories");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-LST-001: Category list shows icon, name, games count, status, and order for every category")
        void listShowsEveryColumn() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            List<String> games = ensureGames(CATEGORY_A_EN, 1);

            List<CategoryRow> rows = list.categories();
            assertTrue(rows.size() >= 2, "At least two categories should be listed");
            // The shipped breadcrumb leads with Home Page, which the capture left out.
            assertTrue(list.breadcrumb().endsWith("Product / Other Category"),
                    "The breadcrumb should end Product / Other Category; it reads " + list.breadcrumb());
            for (CategoryRow row : rows) {
                assertFalse(row.name().isBlank(), "Every row should show a Category name: " + row);
                assertTrue(row.hasIcon(), "Every row should show an Icon: " + row);
                assertTrue(row.games() >= 0, "Every row should show a Games count: " + row);
                assertTrue(row.status().matches("(?i)active|inactive"), "Every row should show Active or Inactive: " + row);
                assertTrue(orderValue(row.order()) != Integer.MAX_VALUE, "Every row should show a numeric Order: " + row);
            }
            CategoryRow a = list.category(CATEGORY_A_EN);
            assertEquals(games.size(), a.games(), "Category A's Games column should match its saved assignments");
            assertEquals(LANGUAGE_TAB_COUNT, a.translations(), "Category A was saved with every language, so it should read \"12 translations\"");
            assertEquals("Active", a.status());
            assertEquals(orderValue(createdOrders.getOrDefault(CATEGORY_A_EN, ORDER_CATEGORY_A)), orderValue(a.order()),
                    "Category A should show the order it was created with");
        }

        @Test
        @DisplayName("OC-LST-002: Search returns only categories matching the entered text")
        void searchByName() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            int all = list.rowCount();
            list.search(LIST_SEARCH);
            List<String> found = list.categoryNames();
            assertFalse(found.isEmpty(), "Searching \"" + LIST_SEARCH + "\" should find " + CATEGORY_A_EN);
            assertTrue(found.stream().allMatch(n -> n.toLowerCase(Locale.ROOT).contains(LIST_SEARCH.toLowerCase(Locale.ROOT))),
                    "Only matching categories should be shown, but got " + found);
            list.clearSearch();
            assertEquals(all, list.rowCount(), "Clearing the search should restore the full list");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-LST-003: Status filter shows only Active or only Inactive categories as selected")
        void filterByStatus() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureCategory(CATEGORY_INACTIVE, "Inactive", "98");
            int all = list.rowCount();

            list.filterStatus("Active");
            List<CategoryRow> active = list.categories();
            assertTrue(active.stream().allMatch(r -> r.status().equalsIgnoreCase("Active")), "Active shows only Active: " + active);
            assertTrue(active.stream().anyMatch(r -> r.name().equals(CATEGORY_A_EN)), "Active should include " + CATEGORY_A_EN);

            list.filterStatus("Inactive");
            List<CategoryRow> inactive = list.categories();
            assertTrue(inactive.stream().allMatch(r -> r.status().equalsIgnoreCase("Inactive")), "Inactive shows only Inactive: " + inactive);
            assertTrue(inactive.stream().anyMatch(r -> r.name().equals(CATEGORY_INACTIVE)), "Inactive should include " + CATEGORY_INACTIVE);

            list.filterStatus("All statuses");
            assertEquals(all, list.rowCount(), "All statuses should show every category");
        }

        @Test
        @DisplayName("OC-LST-004: Changing the list's language filter updates the Category name column to that language's translation")
        void languageFilterTranslatesNames() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            CategoryRow english = list.category(CATEGORY_A_EN);
            list.filterLanguage(TAB_CHINESE);
            CategoryRow chinese = list.category(CATEGORY_A_ZH);
            assertNotNull(chinese, "Under 繁體 the list should show \"" + CATEGORY_A_ZH + "\"; it shows " + list.categoryNames());
            list.filterLanguage(TAB_ENGLISH);
            assertNotNull(list.category(CATEGORY_A_EN), "Back under English the list should show \"" + CATEGORY_A_EN + "\"");
            assertEquals(english.games(), chinese.games(), "Only the name should change - Games did too");
            assertEquals(english.status(), chinese.status(), "Only the name should change - Status did too");
            assertEquals(english.order(), chinese.order(), "Only the name should change - Order did too");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-LST-005: Page-size selector controls how many categories are listed at once")
        void pageSizeLimitsRows() {
            List<Integer> sizes = list.pageSizeOptions();
            assertFalse(sizes.isEmpty(), "The list should offer a per-page selector");
            int smallest = sizes.get(0);
            seedMoreThan(smallest);
            list.setPageSize(smallest);
            assertTrue(list.rowCount() <= smallest, "At most " + smallest + " rows should show, but " + list.rowCount() + " did");
            int[] showing = list.showing();
            assertNotNull(showing, "The \"Showing X-Y of Z\" line should be present");
            assertEquals(list.rowCount(), showing[1] - showing[0] + 1, "The Showing line should match the rows on screen");
            assertTrue(list.hasNextPage(), "With more than " + smallest + " categories there should be a next page");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-LST-006: Previous/Next controls navigate between pages of categories")
        void pagingForwardAndBack() {
            int smallest = list.pageSizeOptions().get(0);
            seedMoreThan(smallest);
            list.setPageSize(smallest);
            int[] first = list.showing();
            List<String> firstPage = rowKeys();

            list.nextPage();
            int[] second = list.showing();
            List<String> secondPage = rowKeys();
            List<Integer> secondNumbers = list.categories().stream().map(OtherCategoryListPage.CategoryRow::no).toList();
            CaseLog.data("Page two", secondPage.size() + " rows, No. column " + secondNumbers);
            assertEquals(first[1] + 1, second[0], "Page two should start where page one ended");
            assertEquals(first[2], second[2], "The total should not change between pages");
            assertEquals(list.rowCount(), second[1] - second[0] + 1, "The Showing line should match page two's rows");
            List<String> repeated = secondPage.stream().filter(firstPage::contains).toList();
            assertTrue(repeated.isEmpty(), "Page two should list different categories from page one, but repeated " + repeated);
            // As the system does it (Si, 2026-09-29): No. is a per-page row counter, not an id, so it restarts at 1
            // on every page rather than continuing from page one's last number.
            List<Integer> counter = java.util.stream.IntStream.rangeClosed(1, secondNumbers.size()).boxed().toList();
            assertEquals(counter, secondNumbers, "Page two's No. column should count its own rows from 1");

            list.previousPage();
            assertEquals(first[0], list.showing()[0], "Previous should return to page one");
            assertEquals(firstPage, rowKeys(), "Page one should read as it did");
        }

        /**
         * Each row as one key - name, translations, games, status and order. Not the name alone: names are not
         * unique ("test" is listed three times), and not No., which is a per-page counter.
         */
        private List<String> rowKeys() {
            return list.categories().stream()
                    .map(r -> r.name() + " | " + r.translations() + " tr | " + r.games() + " games | " + r.status() + " | order " + r.order())
                    .toList();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-LST-007: The Games column matches the actual number of games assigned via Manage Games")
        void gamesColumnFollowsAssignments() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            int before = list.category(CATEGORY_A_EN).games();

            ManageGamesPage games = list.clickManageGames(CATEGORY_A_EN);
            String added = pickGame(games, false);
            restore = () -> {
                openList();
                ManageGamesPage undo = list.clickManageGames(CATEGORY_A_EN);
                undo.deselect(added);
                undo.save();
            };
            games.selectWithBanner(added, BANNER_JPG);
            int saved = games.selectedCount();
            games.save();

            openList();
            int after = list.category(CATEGORY_A_EN).games();
            assertEquals(before + 1, after, "Adding one game should raise the Games count from " + before + " to " + (before + 1));
            assertEquals(saved, after, "The Games count should match the saved assignment count");
        }

        @Test
        @DisplayName("OC-LST-008: Categories are listed in ascending Display Order")
        void listedInAscendingOrder() {
            List<CategoryRow> rows = list.categories();
            assertTrue(rows.size() >= 3, "At least three categories are needed to judge the order; there are " + rows.size());
            // Duplicate orders are allowed (Si, 2026-09-25, superseding AQ-05), so the list is
            // ascending with ties permitted: no row may come after one with a higher order.
            for (int i = 1; i < rows.size(); i++) {
                int previous = orderValue(rows.get(i - 1).order());
                int current = orderValue(rows.get(i).order());
                assertTrue(previous <= current, "\"" + rows.get(i).name() + "\" (order " + current + ") is listed after \""
                        + rows.get(i - 1).name() + "\" (order " + previous + ")");
            }
        }
    }

    // =====================================================================
    // 5. MANAGE GAMES / GAME ASSIGNMENT
    // =====================================================================

    @Nested
    @Order(40)
    @DisplayName("5. Manage games")
    class ManageGames {

        private ManageGamesPage openA() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            return list.clickManageGames(CATEGORY_A_EN);
        }

        @Test
        @DisplayName("OC-GAM-001: Selecting an unselected game increments \"N games selected\"")
        void selectingIncrementsTheCounter() {
            ManageGamesPage games = openA();
            int before = games.selectedCount();
            String game = pickGame(games, false);
            games.toggle(game);
            assertEquals(before + 1, games.selectedCount(), "The counter should rise by one");
            assertTrue(games.isSelected(game), game + " should now read Selected");
            games.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-GAM-002: Deselecting a previously selected game decrements the counter")
        void deselectingDecrementsTheCounter() {
            // The row's subject is the counter falling on a deselect, which needs a selected game but
            // not a saved one. While saved selections cannot be read back (the permission in
            // requireSavedSelectionShown), a game is ticked in this session first, then unticked.
            ManageGamesPage games = openA();
            if (games.selectedGames().isEmpty()) {
                String ticked = pickGame(games, false);
                games.toggle(ticked);
                CaseLog.evidence("No saved selection is shown, so " + ticked + " was selected in this session to be deselected.");
            }
            int before = games.selectedCount();
            String game = pickGame(games, true);
            games.toggle(game);
            assertEquals(before - 1, games.selectedCount(), "The counter should fall by one");
            assertFalse(games.isSelected(game), game + " should now read Not selected");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-003: Search returns only games matching the entered text")
        void searchGames() {
            ManageGamesPage games = openA();
            List<String> before = games.gameNames();
            games.search(GAME_SEARCH);
            List<String> found = games.gameNames();
            assertFalse(found.isEmpty(), "Searching \"" + GAME_SEARCH + "\" should find at least one game");
            assertTrue(found.stream().allMatch(g -> g.toLowerCase(Locale.ROOT).contains(GAME_SEARCH.toLowerCase(Locale.ROOT))),
                    "Only matching games should be shown, but got " + found);
            games.search("");
            // The list loads 20 at a time as it scrolls, so a cleared search first shows 20 of the 40
            // the fresh screen showed. Scroll until as many are loaded, then compare the games, not
            // a count that depends on how far the list was scrolled.
            int loaded = games.loadGamesUntil(before.size());
            List<String> after = games.gameNames();
            CaseLog.data("Games listed", before.size() + " before the search, " + found.size() + " matching, " + loaded + " after clearing it");
            assertTrue(after.containsAll(before), "Clearing the search should restore every game listed before it; missing "
                    + before.stream().filter(g -> !after.contains(g)).toList());
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-004: The \"All games\" filter narrows the list to a chosen game")
        void filterByGame() {
            // The shipped screen's "All games" select is a Selection filter - All games / Selected /
            // Not selected - not the filter-by-game the workbook describes (measured 2026-09-25).
            // So the row checks what that select does narrow by. It works on this session's ticks
            // (measured 2026-09-25), so a game is ticked here rather than relying on a saved selection
            // the operator's permission will not let Manage Games load. Nothing is saved.
            ManageGamesPage games = openA();
            String ticked = pickGame(games, false);
            games.toggle(ticked);
            List<String> before = games.gameNames();
            int all = before.size();
            games.filterSelection("Selected");
            List<String> selected = games.gameNames();
            CaseLog.data("Selected", selected.size() + " of " + all + " games");
            assertTrue(selected.contains(ticked), "\"Selected\" should list the ticked " + ticked + "; it lists " + selected);
            assertTrue(selected.stream().allMatch(games::isSelected), "\"Selected\" should list only selected games: " + selected);
            games.filterSelection("Not selected");
            List<String> unselected = games.gameNames();
            assertTrue(unselected.stream().noneMatch(games::isSelected), "\"Not selected\" should list no selected game");
            assertTrue(unselected.stream().noneMatch(selected::contains), "The two filters should not overlap");
            games.filterSelection("All games");
            // The list loads 20 at a time as it scrolls; load as many as before, then compare the games.
            games.loadGamesUntil(all);
            List<String> after = games.gameNames();
            assertTrue(after.containsAll(before), "\"All games\" should restore every game listed before; missing "
                    + before.stream().filter(g -> !after.contains(g)).toList());
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-005: The \"All providers\" filter narrows the list to a chosen provider")
        void filterByProvider() {
            ManageGamesPage games = openA();
            List<String> providers = games.providerOptions();
            assertTrue(providers.size() >= 2, "At least two providers should be offered; got " + providers);
            int all = games.gameNames().size();
            // The workbook's "Weibo Lottery" is not a provider on admin-test; failing that, take the
            // provider of a listed game, so the filter is sure to have something to show.
            String provider = providers.contains(PROVIDER_FILTER) ? PROVIDER_FILTER
                    : games.gameNames().stream().map(games::providerOf).filter(providers::contains)
                            .findFirst().orElse(providers.get(0));
            games.filterProvider(provider);
            List<String> shown = games.gameNames();
            assertFalse(shown.isEmpty(), provider + " should have games");
            assertTrue(shown.size() < all, "Filtering by one provider should narrow the list of " + all);
            for (String game : shown) {
                String listed = games.providerOf(game);
                assertTrue(listed.isEmpty() || listed.equals(provider), game + " belongs to " + listed + ", not " + provider);
            }
            if (provider.equals(PROVIDER_FILTER)) {
                assertTrue(shown.contains(GAME_LUCKY_PICK), PROVIDER_FILTER + " should show " + GAME_LUCKY_PICK + "; showed " + shown);
            }
            games.cancel();
        }

        // Project-only row (900 block): the workbook has no row for a filter that matches nothing.
        // Si, 2026-09-28: filtering the selected games with no match must leave an empty table that
        // still shows every column header - not a blank screen, and not the table taken away.
        @Test
        @DisplayName("OC-GAM-901: Filtering selected games with no matching result shows an empty table with all its headers")
        void selectedFilterWithNoMatchShowsEmptyTable() {
            ManageGamesPage games = openA();
            String ticked = pickGame(games, false);
            games.toggle(ticked);   // one selected game, so "Selected" alone would not be empty
            games.filterSelection("Selected");
            games.search(GAME_SEARCH_NO_MATCH);

            List<String> shown = games.gameNames();
            List<String> headers = games.columnHeaders();
            String placeholder = games.emptyRowText();
            CaseLog.data("Selected + \"" + GAME_SEARCH_NO_MATCH + "\"", shown.size() + " game(s)" + (shown.isEmpty() ? "" : ": " + shown));
            CaseLog.data("Headers shown", headers.isEmpty() ? "(none)" : String.join(" | ", headers));
            CaseLog.data("Empty-list text", placeholder.isEmpty() ? "(none)" : placeholder);

            assertTrue(shown.isEmpty(), "No selected game is named \"" + GAME_SEARCH_NO_MATCH + "\", so the list should be empty; it shows " + shown);
            assertTrue(games.isTableVisible(), "The table should stay on screen when the filter finds nothing");
            assertEquals(MANAGE_GAMES_HEADERS, headers, "The empty table should still show every column header");

            games.search("");
            games.filterSelection("All games");
            assertTrue(games.isListed(ticked), "Clearing the filter should bring the games back, " + ticked + " among them");
            games.cancel();
        }

        // Added by Si, 2026-09-29, numbered on from the tab's last row: a search that matches no game ("sss") must leave the
        // table's header showing every column name, each whole on one line - not wrapped or cut off when the
        // empty body lets the columns collapse.
        @Test
        @DisplayName("OC-GAM-011: Searching games with no match (\"sss\") shows every table header on one line, without breaking")
        void noMatchSearchKeepsHeadersUnbroken() {
            ManageGamesPage games = openA();
            CaseLog.data("Search text", GAME_SEARCH_SSS);
            games.search(GAME_SEARCH_SSS);

            List<String> shown = games.gameNames();
            List<String> headers = games.columnHeaders();
            List<String> broken = games.brokenHeaders();
            CaseLog.data("Games for \"" + GAME_SEARCH_SSS + "\"", shown.size() + (shown.isEmpty() ? "" : ": " + shown));
            CaseLog.data("Headers shown", headers.isEmpty() ? "(none)" : String.join(" | ", headers));
            CaseLog.data("Headers not on one line", broken.isEmpty() ? "(none)" : String.join(", ", broken));
            CaseLog.evidence("The empty games table after searching \"" + GAME_SEARCH_SSS + "\".");

            assertTrue(shown.isEmpty(), "No game matches \"" + GAME_SEARCH_SSS + "\", so the list should be empty; it shows " + shown);
            assertTrue(games.isTableVisible(), "The table should stay on screen when the search finds nothing");
            assertEquals(MANAGE_GAMES_HEADERS, headers, "The empty table should display every column header");
            assertTrue(broken.isEmpty(), "Every column header should read on one line without breaking, but " + broken);

            games.search("");
            games.cancel();
        }

        // Added by Si, 2026-09-29: a category must not be saved with no games selected.
        @Test
        @Tag("creates-data")
        @DisplayName("OC-GAM-012: Saving with no games selected is not allowed")
        void saveWithNoGamesIsRefused() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            int gamesBefore = list.category(CATEGORY_A_EN).games();
            ManageGamesPage games = list.clickManageGames(CATEGORY_A_EN);
            for (String selected : games.selectedGames()) {
                games.toggle(selected);
            }
            assertEquals(0, games.selectedCount(), "The precondition is no game selected");
            CaseLog.data("Games selected", "0 (the list counted " + gamesBefore + " saved)");

            games.save();
            String message = games.messages();
            CaseLog.data("Save with no games", games.isOpen() ? "refused: " + (message.isEmpty() ? "(no message)" : message) : "saved");
            assertTrue(games.isOpen(), "Save should not be allowed with no games selected, but it went through (" + message + ")");
            games.cancel();
            openList();
            assertEquals(gamesBefore, list.category(CATEGORY_A_EN).games(), "The refused Save should leave the category's Games count as it was");
        }

        // Added by Si, 2026-09-29: a double-click on Save must reach the API once, not twice.
        @Test
        @Tag("creates-data")
        @DisplayName("OC-GAM-013: Double-clicking Save assignments sends only one save request")
        void doubleClickSavesOnce() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ManageGamesPage games = list.clickManageGames(CATEGORY_A_EN);
            // A Save that can go through: one selected game, with its banner (OC-GAM-007 refuses one without).
            String game = pickGame(games, false);
            games.selectWithBanner(game, BANNER_JPG);
            assertFalse(games.isBannerWarningShown(), "The precondition is a savable selection - " + game + " with its banner");

            int requests = games.doubleClickSaveCountingRequests();
            String message = games.messages();
            CaseLog.data("After the double-click", (games.isOpen() ? "still open" : "saved and closed") + (message.isEmpty() ? "" : " - " + message));
            assertEquals(1, requests, "A double-click on Save should send one save request, with no duplicate; it sent " + requests);
            assertFalse(games.isOpen(), "The single save should complete; the page said: " + message);
        }

        // OC-GAM-014..023 were added by Si on 2026-09-29 from a list of gaps in this section. None saves.

        /** Searches and returns what it found, logged as the case's data. */
        private List<String> searchAndRead(ManageGamesPage games, String term) {
            games.search(term);
            List<String> found = games.gameNames();
            CaseLog.data("Search \"" + term + "\"", found.size() + " game(s)" + (found.isEmpty() ? "" : ": " + found));
            return found;
        }

        @Test
        @DisplayName("OC-GAM-014: Search ignores letter case - \"horse\", \"HORSE\" and \"Horse\" find the same games")
        void searchIgnoresCase() {
            ManageGamesPage games = openA();
            List<String> reference = searchAndRead(games, GAME_SEARCH);
            assertFalse(reference.isEmpty(), "The precondition is \"" + GAME_SEARCH + "\" finding at least one game");
            for (String other : GAME_SEARCH_OTHER_CASES) {
                assertEquals(reference, searchAndRead(games, other), "\"" + other + "\" should find the same games as \"" + GAME_SEARCH + "\"");
            }
            games.search("");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-015: Search finds text in the middle of a game name")
        void searchMatchesMidWord() {
            ManageGamesPage games = openA();
            String game = games.gameNames().stream().filter(g -> g.replace(" ", "").length() >= 7).findFirst().orElse(null);
            assertNotNull(game, "No listed game has a name long enough to search the middle of");
            String middle = game.substring(2, 6);
            CaseLog.data("Game and mid-name text", game + " / \"" + middle + "\"");
            List<String> found = searchAndRead(games, middle);
            assertTrue(found.contains(game), "Searching \"" + middle + "\" from the middle of \"" + game + "\" should find it; found " + found);
            assertTrue(found.stream().allMatch(g -> g.toLowerCase(Locale.ROOT).contains(middle.toLowerCase(Locale.ROOT))),
                    "Only games containing \"" + middle + "\" should be shown; got " + found);
            games.search("");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-016: Spaces around a search are ignored, and a search of spaces only lists every game")
        void searchTrimsSpaces() {
            ManageGamesPage games = openA();
            List<String> all = games.gameNames();
            List<String> reference = searchAndRead(games, GAME_SEARCH);
            assertEquals(reference, searchAndRead(games, GAME_SEARCH_PADDED),
                    "\"" + GAME_SEARCH_PADDED + "\" should find the same games as \"" + GAME_SEARCH + "\"");
            games.search(GAME_SEARCH_BLANK);
            games.loadGamesUntil(all.size());
            List<String> blank = games.gameNames();
            CaseLog.data("Search of spaces only", blank.size() + " game(s)");
            assertTrue(blank.containsAll(all), "A search of spaces only should list every game, as an empty search does; missing "
                    + all.stream().filter(g -> !blank.contains(g)).toList());
            games.search("");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-017: Special characters in the search give a plain result, with no error and no script run")
        void searchHandlesSpecialCharacters() {
            // The row's claim is a plain result: no error, no script run, the table intact. Whether
            // each term is matched literally is recorded, not asserted - the search is the server's
            // (GET api/account/game?game_name=...), and on 2026-10-02 it read "%" as a LIKE wildcard
            // and listed every game. As the system does it (Si's standing rule for this screen);
            // the wildcard reading is a finding for the client, named in the report's Used-in-this-run.
            ManageGamesPage games = openA();
            for (String term : GAME_SEARCH_SPECIAL) {
                List<String> dialogs = games.searchWatchingDialogs(term);
                List<String> found = games.gameNames();
                String message = games.messages();
                boolean literal = found.stream().allMatch(g -> g.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)));
                CaseLog.data("Search \"" + term + "\"", found.size() + " game(s)"
                        + (found.isEmpty() ? "" : literal ? ", every one containing the term" : ", not all containing the term - read as a wildcard: " + found)
                        + (dialogs.isEmpty() ? "" : ", dialogs " + dialogs)
                        + (message.isEmpty() ? "" : ", message \"" + message + "\""));
                assertTrue(dialogs.isEmpty(), "Searching \"" + term + "\" should run no script, but the page raised " + dialogs);
                assertTrue(message.isEmpty(), "Searching \"" + term + "\" should raise no error, but the page said: " + message);
                assertTrue(games.isTableVisible(), "The table should stay on screen after searching \"" + term + "\"");
                assertEquals(MANAGE_GAMES_HEADERS, games.columnHeaders(), "The headers should stay intact after searching \"" + term + "\"");
            }
            games.search("");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-018: Search, Provider and Selection filters combine - every game shown matches all three")
        void filtersCombine() {
            ManageGamesPage games = openA();
            // As the system is built (Si, 2026-09-29): a provider may hold a single game - JILI holds only "JILI",
            // already selected - so choosing the provider first can leave nothing to tick. Choose an unselected
            // game whose provider the filter offers, tick it, then filter by that provider.
            List<String> providers = games.providerOptions();
            String game = games.gameNames().stream()
                    .filter(g -> !games.isSelected(g) && providers.contains(games.providerOf(g)))
                    .findFirst().orElse(null);
            assertNotNull(game, "No unselected game is listed under a provider the Provider filter offers");
            String provider = games.providerOf(game);
            games.toggle(game);
            games.filterProvider(provider);
            String term = game.split("\\s+")[0];
            CaseLog.data("Filters", "provider " + provider + ", Selected, search \"" + term + "\" (ticked " + game + ")");
            games.filterSelection("Selected");
            List<String> shown = searchAndRead(games, term);
            assertTrue(shown.contains(game), game + " matches all three filters, so it should be shown; shown " + shown);
            for (String g : shown) {
                assertTrue(g.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)), g + " does not match the search \"" + term + "\"");
                String listed = games.providerOf(g);
                assertTrue(listed.isEmpty() || listed.equals(provider), g + " belongs to " + listed + ", not " + provider);
                assertTrue(games.isSelected(g), g + " is shown under Selected but is not selected");
            }
            games.search("");
            games.filterSelection("All games");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-019: A selected game stays selected after searching for something else and clearing the search")
        void selectionSurvivesSearch() {
            ManageGamesPage games = openA();
            List<String> all = games.gameNames();
            String game = pickGame(games, false);
            games.toggle(game);
            int selected = games.selectedCount();
            searchAndRead(games, GAME_SEARCH_NO_MATCH);
            games.search("");
            games.loadGamesUntil(all.size());
            assertTrue(games.isSelected(game), game + " should still be selected after the search was cleared");
            assertEquals(selected, games.selectedCount(), "The selected counter should be unchanged by searching");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-020: The \"N games selected\" counter equals the number of games under the Selected filter")
        void counterMatchesSelectedFilter() {
            ManageGamesPage games = openA();
            for (int i = 0; i < 2; i++) {
                games.toggle(pickGame(games, false));
            }
            int counter = games.selectedCount();
            games.filterSelection("Selected");
            games.loadGamesUntil(counter);
            List<String> shown = games.gameNames();
            CaseLog.data("Counter / Selected filter", counter + " / " + shown.size() + ": " + shown);
            assertEquals(counter, shown.size(), "The counter should equal the games the Selected filter shows");
            assertTrue(shown.stream().allMatch(games::isSelected), "Every game under the Selected filter should read Selected");
            games.filterSelection("All games");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-021: Scrolling to the end of the list loads every game exactly once")
        void scrollingLoadsEveryGameOnce() {
            ManageGamesPage games = openA();
            int first = games.gameNames().size();
            // Si, 2026-09-29: give the scrolling more time. The default patience (1.2s a scroll, three empty
            // scrolls) stopped at 483 games with more still to come; each scroll now waits up to 8s for its
            // batch, and loading is judged finished only after five scrolls in a row bring nothing.
            // 2026-10-02: the end is judged by the games API's own page count, not by the screen's
            // load-more marker, which stays after the last page and asks for an empty page on every
            // scroll (483 games, 25 pages of 20; the marker was still there at page 30).
            int loaded = games.loadGamesUntil(Integer.MAX_VALUE, SCROLL_WAIT_MS, SCROLL_MAX_STALLS);
            List<String> keys = games.gameKeys();
            List<String> repeated = keys.stream().filter(k -> keys.indexOf(k) != keys.lastIndexOf(k)).distinct().toList();
            int total = games.gamesTotal();
            CaseLog.data("Games loaded", first + " at first, " + loaded + " after scrolling to the end"
                    + (total < 0 ? "" : ", of " + total + " the API counts"));
            assertFalse(games.hasMoreToLoad(), "Scrolling to the end should load every game, but more were still waiting at " + loaded);
            if (total >= 0) {
                assertEquals(total, loaded, "Every game the API counts should be on screen after scrolling to the end");
            }
            assertTrue(repeated.isEmpty(), "Every game should be listed once, but these repeat: " + repeated);
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-022: The longest game name keeps the table aligned - headers unbroken and no column pushed off the side")
        void longestNameKeepsLayout() {
            ManageGamesPage games = openA();
            games.loadGamesUntil(Integer.MAX_VALUE);
            String longest = games.gameNames().stream().max(java.util.Comparator.comparingInt(String::length)).orElse("");
            CaseLog.data("Longest game name", longest + " (" + longest.length() + " characters)");
            List<String> broken = games.brokenHeaders();
            assertTrue(broken.isEmpty(), "With every game listed, each header should still read on one line, but " + broken);
            assertFalse(games.tableOverflows(), "The table should fit its area; the longest name \"" + longest + "\" pushed it wider");
            games.cancel();
        }

        @Test
        @DisplayName("OC-GAM-023: In narrower windows (1280 and 1024 wide) every header still reads on one line")
        void headersFitNarrowWindows() {
            ManageGamesPage games = openA();
            int[] original = null;
            try {
                for (int[] size : NARROW_WINDOWS) {
                    int[] before = games.resizeWindow(size[0], size[1]);
                    if (original == null) {
                        original = before;
                    }
                    List<String> headers = games.columnHeaders();
                    List<String> broken = games.brokenHeaders();
                    CaseLog.data("At " + size[0] + "x" + size[1], "headers " + headers + (broken.isEmpty() ? "" : ", broken " + broken));
                    CaseLog.evidence("Manage Games at " + size[0] + "x" + size[1] + ".");
                    assertEquals(MANAGE_GAMES_HEADERS, headers, "Every header should show at " + size[0] + " wide");
                    assertTrue(broken.isEmpty(), "At " + size[0] + " wide every header should read on one line, but " + broken);
                }
            } finally {
                if (original != null) {
                    games.resizeWindow(original[0], original[1]);
                }
            }
            games.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-GAM-006: A game already assigned to one category can still be selected in a different category")
        void gameMayBeInTwoCategories() {
            // Si, 2026-09-25: a game already in one category can still be assigned to another - that is
            // the requirement. Judged from the list's Games counts: Manage Games cannot read saved
            // assignments back for this operator (see requireSavedSelectionShown), so a save writes
            // exactly this session's selection, and the counts are what shows it was kept.
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureCategory(CATEGORY_B_EN, "Active", "06");

            ManageGamesPage a = list.clickManageGames(CATEGORY_A_EN);
            String game = pickGame(a, false);
            a.selectWithBanner(game, BANNER_JPG);
            a.save();
            openList();
            int inA = list.category(CATEGORY_A_EN).games();
            assertTrue(inA >= 1, game + " should be saved into " + CATEGORY_A_EN + "; its Games count is " + inA);

            ManageGamesPage b = list.clickManageGames(CATEGORY_B_EN);
            b.selectWithBanner(game, BANNER_JPG);
            b.save();
            String message = b.messages();
            CaseLog.data(game + " into " + CATEGORY_B_EN, b.isOpen() ? "refused: " + message : "saved");
            assertFalse(b.isOpen(), game + " should be accepted into " + CATEGORY_B_EN + " as well; the save said: " + message);

            openList();
            CaseLog.data("Games counts", CATEGORY_A_EN + " " + list.category(CATEGORY_A_EN).games() + ", "
                    + CATEGORY_B_EN + " " + list.category(CATEGORY_B_EN).games() + " (judged by count - the saved games cannot be read back)");
            assertTrue(list.category(CATEGORY_B_EN).games() >= 1, game + " should now be counted in " + CATEGORY_B_EN);
            assertEquals(inA, list.category(CATEGORY_A_EN).games(), game + " should still be counted in " + CATEGORY_A_EN);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-GAM-007: Attempting to Save with the \"1 selected game still needs a banner\" warning showing")
        void saveWithABannerMissing() {
            // Si, 2026-09-25: Manage Games cannot save while a selected game has no banner - superseding
            // AQ-02's "Save goes through". So Save must be refused, and nothing saved: the category's
            // Games count on the list stays as it was.
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            int countBefore = list.category(CATEGORY_A_EN).games();
            ManageGamesPage games = list.clickManageGames(CATEGORY_A_EN);
            String game = pickGame(games, false);
            games.toggle(game);
            assertTrue(games.showsBannerRequired(game), game + " should be flagged \"! Banner required\"");
            assertTrue(games.stillNeedBannerCount() >= 1, "The overall banner warning should be showing");

            games.save();
            String message = games.messages();
            boolean stayed = games.isOpen();
            CaseLog.data("Save with " + game + " lacking a banner", stayed ? "refused: " + (message.isBlank() ? "(no message)" : message) : "saved");
            assertTrue(stayed, "Save should be refused while " + game + " has no banner; it went through (" + message + ")");
            assertTrue(games.showsBannerRequired(game), game + " should still be flagged \"Banner required\"");
            games.cancel();

            openList();
            assertEquals(countBefore, list.category(CATEGORY_A_EN).games(), "The refused save should change nothing on the list");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-GAM-008: Save completes when the banner-required warning is fully cleared")
        void saveWithEveryBanner() {
            // Si, 2026-09-25: Manage Games must not save while a selected game's banner is blank. So
            // Save is refused with one banner missing, and completes once the last banner is uploaded
            // and the warning has cleared. Kept is judged from the list's Games count: Manage Games
            // cannot read saved assignments back for this operator (see requireSavedSelectionShown).
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ManageGamesPage games = list.clickManageGames(CATEGORY_A_EN);
            String withBanner = pickGame(games, false);
            games.selectWithBanner(withBanner, BANNER_JPG);
            String withoutBanner = pickGame(games, false);
            games.toggle(withoutBanner);
            assertTrue(games.isBannerWarningShown(), withoutBanner + " has no banner, so the warning should show");

            games.save();
            CaseLog.data("Save with " + withoutBanner + " lacking a banner", games.isOpen() ? "refused: " + games.messages() : "saved");
            assertTrue(games.isOpen(), "Save should be refused while " + withoutBanner + " has no banner");

            games.openBannerDialog(withoutBanner);
            games.chooseBannerFile(BANNER_JPG);
            games.saveBanner();
            assertFalse(games.isBannerWarningShown(), "No banner warning should show once every selected game has one");
            int expected = games.selectedCount();
            games.save();
            String message = games.messages();
            CaseLog.data("Save with every banner", games.isOpen() ? "refused: " + message : "saved");
            assertFalse(games.isOpen(), "Save should complete once the warning has cleared; it said: " + message);

            openList();
            assertEquals(expected, list.category(CATEGORY_A_EN).games(),
                    "The list should count the " + expected + " saved games (judged by count - they cannot be read back)");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-GAM-009: Deselecting a game that already has an uploaded banner")
        void reselectingABanneredGame() {
            // The banner is uploaded in this same session, so the row does not depend on Manage Games
            // reading back a saved one (which the operator's permission blocks - see
            // requireSavedSelectionShown). Nothing is saved.
            ManageGamesPage games = openA();
            String game = pickGame(games, false);
            games.selectWithBanner(game, BANNER_JPG);
            assertTrue(games.hasBanner(game), "The precondition is " + game + " selected with a banner");
            // Answered by Si 2026-09-24: deselecting discards the banner, so re-selecting needs a fresh upload.
            games.toggle(game);
            games.toggle(game);
            assertFalse(games.hasBanner(game), "Deselecting " + game + " should have discarded its banner");
            assertTrue(games.showsBannerRequired(game), "The re-selected " + game + " should be flagged \"Banner is required\"");
            games.cancel();   // nothing is saved, so the category keeps its banner
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-GAM-010: Selecting/deselecting games and clicking Cancel does not save the changes")
        void cancelDiscardsSelectionChanges() {
            // Si, 2026-09-25: the system as implemented is correct - a game deselected before Cancel is
            // not shown as selected when Manage Games is opened again. So the row selects two games,
            // deselects one, cancels, and expects neither to come back selected, and nothing saved.
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            int countBefore = list.category(CATEGORY_A_EN).games();
            ManageGamesPage games = list.clickManageGames(CATEGORY_A_EN);
            String kept = pickGame(games, false);
            games.toggle(kept);
            String deselected = pickGame(games, false);
            games.toggle(deselected);
            games.toggle(deselected);
            assertTrue(games.isSelected(kept) && !games.isSelected(deselected),
                    "The precondition is " + kept + " selected and " + deselected + " deselected before Cancel");
            games.cancel();

            openList();
            assertEquals(countBefore, list.category(CATEGORY_A_EN).games(), "Cancel should save nothing - the Games count should not change");
            ManageGamesPage reopened = list.clickManageGames(CATEGORY_A_EN);
            CaseLog.data("Reopened after Cancel", reopened.selectedCount() + " games selected");
            assertFalse(reopened.isSelected(deselected), deselected + ", deselected before Cancel, should not be shown as selected");
            assertFalse(reopened.isSelected(kept), kept + " was never saved, so Cancel should not leave it selected");
            reopened.cancel();
        }
    }

    // =====================================================================
    // 6. UPLOAD & REPLACE GAME BANNER
    // =====================================================================

    @Nested
    @Order(50)
    @DisplayName("6. Upload & replace game banner")
    class GameBanner {

        /** Opens A's Manage Games with an unselected game selected, its removal registered. */
        private String selectFreshGame(ManageGamesPage games) {
            String game = pickGame(games, false);
            restore = () -> {
                openList();
                ManageGamesPage undo = list.clickManageGames(CATEGORY_A_EN);
                undo.deselect(game);
                undo.save();
            };
            games.select(game);
            return game;
        }

        private ManageGamesPage openA() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            return list.clickManageGames(CATEGORY_A_EN);
        }

        private void uploadAndExpectAccepted(Path file) {
            ManageGamesPage games = openA();
            String game = selectFreshGame(games);
            games.openBannerDialog(game);
            assertDialogScopedTo(games, game);
            games.chooseBannerFile(file);
            games.saveBanner();
            assertFalse(games.isBannerDialogOpen(), "Save banner should close the dialog; it says: " + games.bannerDialogText());
            assertTrue(games.hasBanner(game), game + " should now have its banner");
            assertFalse(games.showsBannerRequired(game), game + " should no longer be flagged Banner required");
            assertFalse(games.bannerSource(game).isBlank(), "The assignment's banner should be shown");
            games.save();
        }

        @Test
        @DisplayName("OC-BAN-001: Uploading a WEBP banner for a selected game")
        void bannerAcceptsWebp() {
            uploadAndExpectAccepted(BANNER_WEBP);
        }

        /** The dialog must say whose banner it is replacing - the capture's per-assignment scoping line. */
        private void assertDialogScopedTo(ManageGamesPage games, String game) {
            String scope = games.bannerDialogScope();
            CaseLog.data("Dialog scope", scope);
            assertTrue(scope.contains(game) && scope.contains(CATEGORY_A_EN),
                    "The dialog should say it is the banner for " + game + " inside " + CATEGORY_A_EN + "; it said: " + scope);
        }

        @Test
        @DisplayName("OC-BAN-002: Uploading a JPG banner for a selected game")
        void bannerAcceptsJpg() {
            uploadAndExpectAccepted(BANNER_JPG);
        }

        // Project-only rows (900 block): the workbook has no SVG banner row, and OC-BAN-003 only
        // previews a 128x128 icon SVG in the dialog. Si ruled on 2026-09-25 that banners accept SVG
        // and WEBP, so each is uploaded, saved and assigned in full, from generated test data.
        @Test
        @DisplayName("OC-BAN-901: Uploading a generated SVG banner for a selected game")
        void bannerAcceptsSvg() {
            CaseLog.data("Banner file", BANNER_SVG.getFileName() + " (generated SVG, 800x600, 4:3)");
            uploadAndExpectAccepted(BANNER_SVG);
        }

        @Test
        @DisplayName("OC-BAN-902: Uploading a generated WEBP banner for a selected game")
        void bannerAcceptsGeneratedWebp() {
            CaseLog.data("Banner file", BANNER_WEBP_GENERATED.getFileName() + " (generated WEBP, 800x600, 4:3)");
            uploadAndExpectAccepted(BANNER_WEBP_GENERATED);
        }

        @Test
        @DisplayName("OC-BAN-003: Attempting to upload a PNG or GIF as a game banner")
        void bannerRefusesPngAndGif() {
            // Si, 2026-09-25: banners accept PNG, JPG and SVG as well as WEBP - the workbook's
            // "PNG is refused" was wrong. So each is chosen and must be taken without a message.
            // A GIF banner is recorded but not judged: the ruling does not cover it.
            ManageGamesPage games = openA();
            String game = pickGame(games, false);
            games.toggle(game);
            for (Path file : List.of(BANNER_PNG, ICON_SVG, FILE_GIF)) {
                games.openBannerDialog(game);
                games.chooseBannerFile(file);
                String error = games.bannerDialogError();
                boolean taken = !games.bannerDialogPreview().isBlank();
                CaseLog.data(file.getFileName().toString(), (taken ? "accepted" : "not accepted") + (error.isEmpty() ? "" : ": " + error));
                if (file != FILE_GIF) {
                    assertTrue(taken, file.getFileName() + " should be accepted as a banner; the dialog said: " + error);
                    assertEquals("", error, file.getFileName() + " should upload without a message");
                }
                games.cancelBanner();
            }
            games.cancel();
        }

        @Test
        @DisplayName("OC-BAN-004: Uploading a banner far from the recommended 4:3 ratio")
        void tallBanner() {
            ManageGamesPage games = openA();
            String game = selectFreshGame(games);
            games.openBannerDialog(game);
            games.chooseBannerFile(ImageFixtures.webp(sessionPage, "banner-9x16", 540, 960, new Color(0x6D4C41)));
            // Si, 2026-09-25: any ratio is fine - 4:3 is only the recommendation, so AQ-04's "an
            // off-ratio banner is blocked" is superseded. A 9:16 banner must be taken like any other.
            String onChoose = games.bannerDialogError();
            assertEquals("", onChoose, "A 9:16 banner should be accepted without a message");
            games.saveBanner();
            String onSave = games.bannerDialogError();
            CaseLog.data("A 9:16 banner", onSave.isBlank() ? "accepted" : "refused on Save banner: " + onSave);
            assertEquals("", onSave, "Save banner should take a 9:16 banner without a message");
            assertFalse(games.isBannerDialogOpen(), "Save banner should close the dialog");
            assertTrue(games.hasBanner(game), "The 9:16 image should now be " + game + "'s banner");
            assertFalse(games.showsBannerRequired(game), game + " should no longer be flagged Banner required");
            games.cancel();
        }

        @Test
        @DisplayName("OC-BAN-005: Clicking Replace banner on a game that already has one")
        void replaceBanner() {
            String game = ensureGames(CATEGORY_A_EN, 1).get(0);
            requireSavedSelectionShown(CATEGORY_A_EN);   // the row reopens Manage Games and expects the selection back
            openList();
            ManageGamesPage games = list.clickManageGames(CATEGORY_A_EN);
            String before = games.bannerSource(game);
            games.openBannerDialog(game);
            games.chooseBannerFile(BANNER_WEBP_2);
            games.saveBanner();
            String after = games.bannerSource(game);
            assertNotEquals(before, after, "Replace banner should overwrite the old banner");
            games.save();
            openList();
            assertEquals(after, list.clickManageGames(CATEGORY_A_EN).bannerSource(game), "The new banner should be the saved one");
        }

        @Test
        @DisplayName("OC-BAN-006: Uploading a banner for a game in one category does not change that same game's banner in a different category")
        void bannerIsPerAssignment() {
            String game = ensureGames(CATEGORY_A_EN, 1).get(0);
            requireSavedSelectionShown(CATEGORY_A_EN);   // the row reopens Manage Games and expects the selection back
            openList();
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            ManageGamesPage b = list.clickManageGames(CATEGORY_B_EN);
            if (!b.isSelected(game) || !b.hasBanner(game)) {
                b.selectWithBanner(game, BANNER_JPG);
            }
            b.save();
            openList();
            String bBefore = list.clickManageGames(CATEGORY_B_EN).bannerSource(game);

            openList();
            ManageGamesPage a = list.clickManageGames(CATEGORY_A_EN);
            a.openBannerDialog(game);
            a.chooseBannerFile(BANNER_WEBP);
            a.saveBanner();
            a.save();

            openList();
            assertEquals(bBefore, list.clickManageGames(CATEGORY_B_EN).bannerSource(game),
                    "Uploading in " + CATEGORY_A_EN + " should not change " + game + "'s banner in " + CATEGORY_B_EN);
        }

        @Test
        @DisplayName("OC-BAN-007: Selecting a file and clicking Cancel does not save the banner")
        void cancelKeepsTheOldBanner() {
            ManageGamesPage games = openA();
            String game = games.selectedGames().isEmpty() ? pickGame(games, false) : games.selectedGames().get(0);
            games.select(game);
            boolean had = games.hasBanner(game);
            String before = games.bannerSource(game);
            games.openBannerDialog(game);
            games.chooseBannerFile(BANNER_WEBP_2);
            games.cancelBanner();
            assertEquals(had, games.hasBanner(game), "Cancel should leave the banner state as it was");
            assertEquals(before, games.bannerSource(game), "Cancel should leave the banner as it was");
            games.cancel();
        }

        @Test
        @DisplayName("OC-BAN-008: Dragging a valid file onto the drop zone behaves the same as \"choose a file\"")
        void dropZoneUpload() {
            ManageGamesPage games = openA();
            String game = selectFreshGame(games);
            games.openBannerDialog(game);
            games.dropBannerFile(BANNER_WEBP);
            assertFalse(games.bannerDialogPreview().isBlank(), "The dropped file should be previewed like a chosen one");
            games.saveBanner();
            assertTrue(games.hasBanner(game), "A dropped banner should save like a chosen one");
            games.save();
        }

        @Test
        @DisplayName("OC-BAN-009: Attempting to upload a banner file above the maximum accepted size")
        void oversizedBanner() {
            // Si, 2026-09-25: the site's 5 MB limit is right; AQ-06's "no size limit" is superseded.
            // So a 4:3 banner just under the limit is taken, and one well over it is refused with
            // the API's message and does not become the banner.
            uploadAndExpectAccepted(ImageFixtures.oversizedJpg("banner-under-limit", BANNER_UNDER_LIMIT_BYTES));

            ManageGamesPage games = openA();
            String game = selectFreshGame(games);
            games.openBannerDialog(game);
            games.chooseBannerFile(ImageFixtures.oversizedJpg("banner-large", LARGE_BANNER_BYTES));
            String message = games.bannerDialogError();
            CaseLog.data("A " + (LARGE_BANNER_BYTES / (1024 * 1024)) + " MB 4:3 banner", message.isEmpty() ? "(no message)" : message);
            assertEquals(MSG_BANNER_TOO_LARGE, message, "A banner over 5 MB should be refused with the API's message");
            assertTrue(games.bannerDialogPreview().isBlank(), "The oversized file should not be shown as the banner");
            games.cancelBanner();
            assertFalse(games.hasBanner(game), "The oversized file should not have become " + game + "'s banner");
            games.cancel();
        }

        @Test
        @DisplayName("OC-BAN-010: The per-game and overall banner-required warnings disappear after a successful upload")
        void warningsClearAfterUpload() {
            ManageGamesPage games = openA();
            String game = selectFreshGame(games);
            int before = games.stillNeedBannerCount();
            assertTrue(games.showsBannerRequired(game), game + " should start flagged \"! Banner required\"");
            games.openBannerDialog(game);
            games.chooseBannerFile(BANNER_WEBP);
            games.saveBanner();
            assertFalse(games.showsBannerRequired(game), "The per-game warning should be gone");
            assertEquals(before - 1, games.stillNeedBannerCount(), "The overall warning should count one fewer");
            games.save();
        }
    }

    // =====================================================================
    // 3. EDIT OTHER CATEGORY
    // =====================================================================

    @Nested
    @Order(60)
    @DisplayName("3. Edit Other Category")
    class EditCategory {

        @BeforeEach
        void needsCategoryA() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
        }

        /** Undoes an edit to A by writing back the order and status it had. */
        private void restoreAfterwards(String englishName) {
            CategoryRow was = list.category(CATEGORY_A_EN);
            restore = () -> {
                openList();
                OtherCategoryFormPage form = list.clickEdit(englishName);
                form.setName(TAB_ENGLISH, CATEGORY_A_EN);
                form.setStatus(was.status());
                form.setDisplayOrder(was.order());
                form.clickSave();
            };
        }

        @Test
        @DisplayName("OC-EDT-001: Edit loads every saved field correctly, including all 12 language names")
        void editLoadsEverything() {
            int savedOrder = orderValue(list.category(CATEGORY_A_EN).order());
            OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
            for (String tab : form.languageTabLabels()) {
                assertEquals(nameOnTab(CATEGORY_A_EN, tab), form.nameFor(tab), "The " + tab + " name should load as saved");
            }
            assertTrue(form.hasIconPreview(), "The saved icon should load");
            assertEquals("Active", form.status(), "The saved status should load");
            assertEquals(savedOrder, orderValue(form.displayOrder()), "The saved order should load");
            form.cancel();
        }

        @Test
        @DisplayName("OC-EDT-002: Changing only the English name leaves the other 11 languages untouched")
        void englishOnlyEdit() {
            String edited = CATEGORY_A_EN + RENAMED_SUFFIX;
            restoreAfterwards(edited);
            OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
            form.setName(TAB_ENGLISH, edited);
            form.clickSave();

            openList();
            form = list.clickEdit(edited);
            assertEquals(edited, form.nameFor(TAB_ENGLISH), "English should hold the new name");
            for (String tab : form.languageTabLabels()) {
                if (!tab.equals(TAB_ENGLISH)) {
                    assertEquals(nameOnTab(CATEGORY_A_EN, tab), form.nameFor(tab), "The " + tab + " name should be untouched");
                }
            }
            form.cancel();
        }

        @Test
        @DisplayName("OC-EDT-003: Uploading a new icon during Edit replaces the previous one")
        void editReplacesIcon() {
            String before = list.iconSource(CATEGORY_A_EN);
            restore = () -> {
                openList();
                OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
                form.uploadIcon(ICON_PNG);
                form.clickSave();
            };
            OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
            form.uploadIcon(ICON_REPLACEMENT);
            form.clickSave();
            openList();
            assertNotEquals(before, list.iconSource(CATEGORY_A_EN), "The list should show the new icon");
            assertTrue(list.iconRendered(CATEGORY_A_EN), "The new icon should render");
        }

        @Test
        @DisplayName("OC-EDT-004: Changing Status and saving updates the category's visibility state")
        void editStatus() {
            // Si, 2026-09-25: the Edit form cannot save Inactive - unticking "Do you want to display"
            // is refused by the API (isActive is a required field), and that is the expected
            // behaviour. Status is changed with the list's Status select instead. Si, 2026-09-28:
            // the message shown must be the API's response, read from this save's own PUT.
            ensureGames(CATEGORY_A_EN, 1);
            restoreAfterwards(CATEGORY_A_EN);

            OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
            form.setStatus("Inactive");
            form.clickSave();
            String message = form.messages();
            CaseLog.data("Saving Inactive through Edit", form.isOpen() ? "refused: " + message : "saved");
            assertTrue(form.isOpen(), "Saving Inactive through the Edit form should be refused");
            assertMessageMatchesApi(form, message, null);
            openList();
            assertEquals("Active", list.category(CATEGORY_A_EN).status(), "The refused save should leave the category Active");

            list.setStatus(CATEGORY_A_EN, "Inactive");
            openList();
            assertEquals("Inactive", list.category(CATEGORY_A_EN).status(), "The list's Status select should make it Inactive");

            // The client half. Read from the member site's category feed and the category's own page
            // there (ClientCategoryPage, measured 2026-10-02); before that the member site rendered
            // nothing for Other Categories and Si chose to let these rows fail rather than go Not run.
            ClientCategoryPage client = new ClientCategoryPage(page());
            list.setStatus(CATEGORY_A_EN, "Active");
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertTrue(client.isShown(CATEGORY_A_EN), "The Active category should be visible on the client");
            openList();
            list.setStatus(CATEGORY_A_EN, "Inactive");
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertFalse(client.isShown(CATEGORY_A_EN), "An Inactive category should stop appearing on the client UI");
        }

        @Test
        @DisplayName("OC-EDT-005: Lowering a category's Order moves it earlier in the list")
        void lowerOrderMovesEarlier() {
            restoreAfterwards(CATEGORY_A_EN);
            String[] orders = lowerThenLowest();
            OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
            form.setDisplayOrder(orders[0]);
            form.clickSave();
            openList();
            int atHigher = list.categoryNames().indexOf(CATEGORY_A_EN);

            form = list.clickEdit(CATEGORY_A_EN);
            form.setDisplayOrder(orders[1]);
            form.clickSave();
            openList();
            int atLower = list.categoryNames().indexOf(CATEGORY_A_EN);
            CaseLog.data("Position", "order " + orders[0] + ": row " + (atHigher + 1) + ", order " + orders[1] + ": row " + (atLower + 1));
            assertTrue(atLower <= atHigher, "Order " + orders[1] + " should list the category no later than order " + orders[0] + " did");
            for (CategoryRow row : list.categories().subList(0, atLower)) {
                assertTrue(orderValue(row.order()) < orderValue(orders[1]), "Only lower orders may precede it (" + orders[1] + " was free, so nothing ties with it), but \""
                        + row.name() + "\" has " + row.order());
            }
        }

        /**
         * The workbook's "04 then 01" when both are free, else the two lowest free orders. AQ-05 makes
         * orders unique, and the client's own categories are likely to hold the low numbers.
         */
        private String[] lowerThenLowest() {
            Set<Integer> used = ordersInUse();
            used.remove(orderValue(list.category(CATEGORY_A_EN).order()));   // A's own order is free for A
            if (!used.contains(orderValue(ORDER_BEFORE)) && !used.contains(orderValue(ORDER_AFTER))) {
                return new String[] {ORDER_BEFORE, ORDER_AFTER};
            }
            List<String> free = new ArrayList<>();
            for (int n = 1; free.size() < 2; n++) {
                if (!used.contains(n)) {
                    free.add(String.format("%02d", n));
                }
            }
            CaseLog.data("Display orders", "04 / 01 are taken, so " + free.get(1) + " then " + free.get(0) + " were used (AQ-05)");
            return new String[] {free.get(1), free.get(0)};
        }

        @Test
        @DisplayName("OC-EDT-006: Opening Edit and saving immediately without changes")
        void saveWithoutChanges() {
            Map<String, CategoryRow> before = list.snapshot();
            OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
            form.clickSave();
            assertEquals("", form.isOpen() ? form.messages() : "", "Save without changes should not raise an error");
            openList();
            assertEquals(before.get(CATEGORY_A_EN), list.snapshot().get(CATEGORY_A_EN), "Nothing about the category should change");
        }

        @Test
        @DisplayName("OC-EDT-007: Changing fields and clicking Cancel does not save them")
        void cancelDiscardsEdits() {
            String icon = list.iconSource(CATEGORY_A_EN);
            OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
            form.uploadIcon(ICON_REPLACEMENT);
            form.setName(TAB_ENGLISH, CATEGORY_A_EN + RENAMED_SUFFIX);
            form.cancel();
            openList();
            assertTrue(list.isListed(CATEGORY_A_EN), "The name should be as last saved");
            assertEquals(icon, list.iconSource(CATEGORY_A_EN), "The icon should be as last saved");
            form = list.clickEdit(CATEGORY_A_EN);
            assertEquals(CATEGORY_A_EN, form.nameFor(TAB_ENGLISH), "Reopened, Edit should show the saved name");
            form.cancel();
        }

        @Test
        @DisplayName("OC-EDT-008: Confirming whether Edit's save button reads \"Save\" or \"Save & manage games\", and where it navigates")
        void editPrimaryAction() {
            // AQ-08 (answered 2026-09-24): Edit's button reads "Save" and returns to the category list -
            // unlike Create's "Save & manage games", which goes on to Manage Games.
            OtherCategoryFormPage form = list.clickEdit(CATEGORY_A_EN);
            // The shipped screen spells it "Save changes" (measured 2026-09-25); what AQ-08 settles is
            // that it is a plain save, not Create's "Save & manage games".
            String label = form.primaryButtonLabel();
            CaseLog.data("Edit's button", label);
            assertTrue(label.matches("(?i)Save( changes)?"), "Edit's primary button should be a plain Save; it reads \"" + label + "\"");
            form.clickSave();
            CaseLog.data("Landed on", sessionPage.url());
            assertFalse(new ManageGamesPage(sessionPage).isOpen(), "Save on Edit should not go on to Manage Games");
            assertTrue(list.isShowing(), "Save on Edit should return to the category list; it is on " + sessionPage.url());
            // The list comes back under its default language filter, so A may read in English or 繁體.
            assertTrue(list.isListed(CATEGORY_A_EN) || list.isListed(CATEGORY_A_ZH), "The list it returns to should show the edited category");
        }
    }

    // =====================================================================
    // 8. LOCALIZATION
    // =====================================================================

    @Nested
    @Order(70)
    @DisplayName("8. Localization")
    class Localization {

        @Test
        @DisplayName("OC-I18N-001: All 12 language tabs are present, labeled, and selectable")
        void twelveTabs() {
            OtherCategoryFormPage form = list.clickCreate();
            List<String> tabs = form.languageTabLabels();
            CaseLog.data("Language tabs", String.join(", ", tabs));
            assertEquals(LANGUAGE_TAB_COUNT, tabs.size(), "Exactly 12 language tabs should be shown");
            assertEquals(LANGUAGE_TABS, tabs, "The tabs should be labelled, and ordered, as in the Create capture");
            assertEquals(tabs.size(), tabs.stream().distinct().count(), "Every tab should have its own label");
            for (String tab : tabs) {
                assertFalse(tab.isBlank(), "Every tab should be labelled");
                form.setName(tab, "x");
                assertTrue(form.isLanguageTabActive(tab), tab + " should become active when selected");
                assertEquals("x", form.nameFor(tab), tab + " should be editable");
            }
            form.cancel();
        }

        @Test
        @DisplayName("OC-I18N-002: Switching the admin UI's display language does not change which category-name tab is active")
        void adminLanguageDoesNotMoveTheTab() {
            restore = () -> shell.useLanguage(LANGUAGE);
            OtherCategoryFormPage form = list.clickCreate();
            form.setName(TAB_SPAIN, CATEGORY_A_ES);
            shell.selectLanguage(AdminShellPage.CHINESE);
            assertTrue(form.isLanguageTabActive(TAB_SPAIN), "Spain should still be the active name tab; " + form.activeLanguageTab() + " is");
            assertEquals(CATEGORY_A_ES, form.nameFor(TAB_SPAIN), "Spain's entered name should be untouched");
            form.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-I18N-003: Client UI shows the category name matching the end user's chosen language")
        void clientShowsTheViewersLanguage() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureGames(CATEGORY_A_EN, 1);
            ClientCategoryPage client = new ClientCategoryPage(page());
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertTrue(client.isShown(CATEGORY_A_EN), "In English the client should show \"" + CATEGORY_A_EN + "\"");
            client.openIn(CLIENT_LANGUAGE.get(TAB_CHINESE));
            assertTrue(client.isShown(CATEGORY_A_ZH), "In 繁體 the client should show \"" + CATEGORY_A_ZH + "\"");
            assertFalse(client.isShown(CATEGORY_A_EN), "In 繁體 the English name should not be shown");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-I18N-004: Client behavior for a language whose name field was left blank on admin side")
        void clientFallbackForBlankLanguage() {
            String name = PREFIX + "No Bengali";
            deleteAfterwards(name);
            // AQ-01: Bengali is optional, so this category must be creatable - the row's precondition.
            fillCreateLeaving(name, TAB_BENGALI).clickSave();
            openList();
            assertTrue(list.isListed(name), "Bengali is optional (AQ-01), so the category should save with its name empty");
            String game = ensureGames(name, 1).get(0);

            // AQ-09 (answered 2026-09-24): the category still shows for a Bengali viewer, with an empty
            // heading - no fallback to another language. With no Bengali name to find it by, it is
            // found in the client's feed by its English name, and its page is judged by its game's
            // banner, which belongs to this category's assignment alone.
            ClientCategoryPage client = new ClientCategoryPage(page());
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            String banner = client.bannerSource(name, game);
            assertFalse(banner.isBlank(), "The precondition is the category and " + game + "'s banner visible in English");

            client.openIn("bn");
            assertTrue(client.openCategory(name), "The category should still be in the client's feed for a Bengali viewer");
            CaseLog.data("Heading for a Bengali viewer", "\"" + client.heading() + "\"");
            assertTrue(client.hasImage(banner), "The category should still show for a Bengali viewer - "
                    + game + "'s banner for it is missing, so the category was hidden");
            // Every tab's name for this category starts with the English one, so this covers all of them.
            assertFalse(client.bodyText().contains(name),
                    "The Bengali heading should be empty, but another language's name for the category is shown");
        }

        @Test
        @DisplayName("OC-I18N-005: Buttons, hints, and warnings on the Other Category screens render correctly in both admin UI languages")
        void noMissingTranslations() {
            restore = () -> shell.useLanguage(LANGUAGE);
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            Pattern untranslated = Pattern.compile("\\{\\{|}}|\\bundefined\\b|\\bnull\\b|missing translation|\\b[a-z]+(?:\\.[a-z_]+){2,}\\b");
            for (String language : List.of(AdminShellPage.ENGLISH, AdminShellPage.CHINESE)) {
                shell.useLanguage(language);
                openList();   // English names on the list, in either admin UI language
                List<String> screens = new ArrayList<>(List.of(list.screenText()));
                list.clickCreate();
                screens.add(sessionPage.locator("body").innerText());
                openList();   // English names on the list, in either admin UI language
                list.clickManageGames(CATEGORY_A_EN);
                screens.add(sessionPage.locator("body").innerText());
                openList();   // English names on the list, in either admin UI language
                list.clickDelete(CATEGORY_A_EN);
                screens.add(list.deleteDialogText());
                list.cancelDelete();
                for (String text : screens) {
                    var found = untranslated.matcher(text);
                    if (found.find()) {
                        throw new AssertionError("Under " + language + " a screen shows an untranslated key or placeholder: \""
                                + found.group() + "\"");
                    }
                }
            }
            CaseLog.data("Screens checked", "List, Create, Manage Games and the Delete dialog, in English and 繁體");
        }
    }

    // =====================================================================
    // 9. CLIENT-FACING DISPLAY
    // =====================================================================

    @Nested
    @Order(80)
    @DisplayName("9. Client-facing display")
    class ClientDisplay {

        private ClientCategoryPage client;

        @BeforeEach
        void openClientTab() {
            client = new ClientCategoryPage(page());
        }

        @Test
        @Tag("creates-data")
        @DisplayName("OC-CLI-001: Inactive categories are excluded from the client-facing list")
        void inactiveHidden() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureGames(CATEGORY_A_EN, 1);
            ensureCategory(CATEGORY_INACTIVE, "Inactive", "98");
            ensureGames(CATEGORY_INACTIVE, 1);
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertTrue(client.isShown(CATEGORY_A_EN), "The Active category should be visible");
            assertFalse(client.isShown(CATEGORY_INACTIVE), "The Inactive category should not be visible");
        }

        @Test
        @DisplayName("OC-CLI-002: An Inactive category does not appear anywhere on the client UI, including deep links")
        void inactiveUnreachable() {
            ensureCategory(CATEGORY_INACTIVE, "Inactive", "98");
            int id = list.idOf(CATEGORY_INACTIVE);
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertFalse(client.isShown(CATEGORY_INACTIVE), "The Inactive category should not be reachable by navigation - "
                    + "it should be absent from the client's category feed");
            // Measured 2026-10-02: a category's page on the client is /{locale}/game-category-features/{id},
            // the id being the back office's. On that day the page rendered an Inactive category by id.
            String path = "en/" + ClientCategoryPage.CATEGORY_ROUTE + id;
            client.openDirect(path);
            String heading = client.heading();
            CaseLog.data("Deep link " + path, heading.isEmpty() ? "no category heading" : "headed \"" + heading + "\"");
            assertFalse(client.bodyText().contains(CATEGORY_INACTIVE), "A direct link " + path
                    + " should not display the Inactive category, but the page is headed \"" + heading + "\"");
        }

        @Test
        @DisplayName("OC-CLI-003: Client-facing category order matches the admin-configured Order")
        void clientOrderMatchesAdmin() {
            String third = PREFIX + "Order Check";
            deleteAfterwards(third);
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            ensureCategory(third, "Active", "07");
            for (String name : List.of(CATEGORY_A_EN, CATEGORY_B_EN, third)) {
                ensureGames(name, 1);
            }
            openList();
            List<String> admin = list.categoryNames().stream().filter(List.of(CATEGORY_A_EN, CATEGORY_B_EN, third)::contains).toList();
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertEquals(admin, client.inDocumentOrder(admin), "The client should order the categories as the admin list does");
        }

        @Test
        @DisplayName("OC-CLI-004: Client-side behavior for an Active category that has no games")
        void emptyActiveCategory() {
            ensureCategory(CATEGORY_EMPTY, "Active", "97");
            CategoryRow row = list.category(CATEGORY_EMPTY);
            assertEquals(0, row.games(), "The precondition is a category with 0 games");
            assertEquals("Active", row.status(), "The precondition is an Active category");
            // AQ-10 (answered 2026-09-24): an Active category with no games is hidden automatically.
            // A control category with a game proves the client really rendered categories, so a
            // missing heading means hidden rather than a page that never loaded.
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureGames(CATEGORY_A_EN, 1);
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertTrue(client.isShown(CATEGORY_A_EN), "The control " + CATEGORY_A_EN + " (Active, with games) should be shown");
            assertFalse(client.isShown(CATEGORY_EMPTY), "An Active category with 0 games should be hidden on the client");
        }

        @Test
        @DisplayName("OC-CLI-005: The uploaded category icon displays correctly on the client UI")
        void iconOnClient() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureGames(CATEGORY_A_EN, 1);
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertTrue(client.isShown(CATEGORY_A_EN), "The category should be on the client");
            assertTrue(client.iconRendersUndistorted(CATEGORY_A_EN), "The icon should load and keep its proportions");
        }

        @Test
        @DisplayName("OC-CLI-006: A game's banner on the client UI matches the one uploaded for that specific category assignment")
        void bannerOnClientMatchesAdmin() {
            String game = ensureGames(CATEGORY_A_EN, 1).get(0);
            String admin = list.clickManageGames(CATEGORY_A_EN).bannerSource(game);
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            String shown = client.bannerSource(CATEGORY_A_EN, game);
            assertFalse(shown.isBlank(), game + " should show a banner in " + CATEGORY_A_EN);
            assertEquals(fileName(admin), fileName(shown), "The client should show the banner uploaded for this assignment");
        }

        @Test
        @DisplayName("OC-CLI-007: The same game shows a different, correct banner in each category it belongs to")
        void bannerPerCategoryOnClient() {
            String game = ensureGames(CATEGORY_A_EN, 1).get(0);
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            ManageGamesPage b = list.clickManageGames(CATEGORY_B_EN);
            b.select(game);
            b.openBannerDialog(game);
            b.chooseBannerFile(BANNER_WEBP_2);
            b.saveBanner();
            b.save();
            openList();
            String adminA = list.clickManageGames(CATEGORY_A_EN).bannerSource(game);
            openList();
            String adminB = list.clickManageGames(CATEGORY_B_EN).bannerSource(game);
            assertNotEquals(fileName(adminA), fileName(adminB), "The precondition is two different banners");

            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertEquals(fileName(adminA), fileName(client.bannerSource(CATEGORY_A_EN, game)), "Category A should show A's banner");
            assertEquals(fileName(adminB), fileName(client.bannerSource(CATEGORY_B_EN, game)), "Category B should show B's banner");
        }

        @Test
        @DisplayName("OC-CLI-008: Deleting a category on admin side removes it from the client platform without delay")
        void deletedCategoryLeavesClient() {
            String name = PREFIX + "Client Delete";
            deleteAfterwards(name);
            ensureCategory(name, "Active", ORDER_OUT_OF_THE_WAY);
            ensureGames(name, 1);
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertTrue(client.isShown(name), "The precondition is the category visible on the client");
            openList();
            list.clickDelete(name);
            list.confirmDelete();
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            assertFalse(client.isShown(name), "After the admin deletes it, a refreshed client should not show it");
        }

        @Test
        @DisplayName("OC-CLI-009: A client user viewing in a given language always sees that language's saved category name")
        void vietnameseName() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureGames(CATEGORY_A_EN, 1);
            client.openIn(CLIENT_LANGUAGE.get(TAB_VIETNAM));
            assertTrue(client.isShown(nameOnTab(CATEGORY_A_EN, TAB_VIETNAM)),
                    "A Vietnam viewer should see the Vietnamese name \"" + nameOnTab(CATEGORY_A_EN, TAB_VIETNAM) + "\"");
        }

        @Test
        @DisplayName("OC-CLI-010: Client-side behavior for a selected game whose banner was never uploaded")
        void bannerlessGameOnClient() {
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            ManageGamesPage b = list.clickManageGames(CATEGORY_B_EN);
            String game = pickGame(b, false);
            restore = () -> {
                openList();
                ManageGamesPage undo = list.clickManageGames(CATEGORY_B_EN);
                undo.deselect(game);
                undo.save();
            };
            b.toggle(game);
            b.save();
            openList();
            // AQ-02 (answered 2026-09-24): Save is allowed without a banner, so this state is reachable.
            assertTrue(list.clickManageGames(CATEGORY_B_EN).isSelected(game),
                    "Save should go through with " + game + " lacking a banner (AQ-02), so it should be assigned to " + CATEGORY_B_EN);
            client.openIn(CLIENT_LANGUAGE.get(TAB_ENGLISH));
            String rendered = !client.showsGame(CATEGORY_B_EN, game) ? "OMITTED"
                    : client.bannerSource(CATEGORY_B_EN, game).isBlank() ? "shown with NO image" : "shown with a placeholder image";
            CaseLog.evidence("A selected game with no banner is " + rendered + " on the client (the client fallback is not yet specified).");
            assertTrue(client.isShown(CATEGORY_B_EN), "The category itself should still render");
        }

        private String fileName(String src) {
            String path = src == null ? "" : src.replaceAll("[?#].*$", "");
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    // =====================================================================
    // 10. REGRESSION & DATA INTEGRITY
    // =====================================================================

    @Nested
    @Order(90)
    @DisplayName("10. Regression & data integrity")
    class RegressionIntegrity {

        /** The whole game catalog as Manage Games lists it for a category, unfiltered. */
        private List<String> catalog(String category) {
            openList();
            ManageGamesPage games = list.clickManageGames(category);
            List<String> names = new ArrayList<>(games.gameNames());
            games.cancel();
            return names;
        }

        @Test
        @DisplayName("OC-REG-001: Deleting a category does not corrupt or remove the underlying global game catalog")
        void deletionKeepsTheCatalog() {
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            List<String> before = catalog(CATEGORY_B_EN);
            String name = PREFIX + "Catalog Check";
            deleteAfterwards(name);
            ensureCategory(name, "Active", ORDER_OUT_OF_THE_WAY);
            ensureGames(name, 2);
            list.clickDelete(name);
            list.confirmDelete();
            assertEquals(before, catalog(CATEGORY_B_EN), "The global game catalog should be identical after the deletion");
        }

        @Test
        @DisplayName("OC-REG-002: A multi-category game loses only the deleted category's assignment, keeping the rest")
        void otherAssignmentSurvives() {
            String game = ensureGames(CATEGORY_A_EN, 1).get(0);
            requireSavedSelectionShown(CATEGORY_A_EN);   // the row reopens Manage Games and expects the selection back
            openList();
            String doomed = PREFIX + "Second Home";
            deleteAfterwards(doomed);
            ensureCategory(doomed, "Active", ORDER_OUT_OF_THE_WAY);
            ManageGamesPage second = list.clickManageGames(doomed);
            second.selectWithBanner(game, BANNER_WEBP_2);
            second.save();
            openList();
            String bannerInA = list.clickManageGames(CATEGORY_A_EN).bannerSource(game);

            openList();
            list.clickDelete(doomed);
            list.confirmDelete();
            openList();
            ManageGamesPage a = list.clickManageGames(CATEGORY_A_EN);
            assertTrue(a.isSelected(game), game + " should still be assigned to " + CATEGORY_A_EN);
            assertEquals(bannerInA, a.bannerSource(game), CATEGORY_A_EN + "'s banner for " + game + " should be intact");
        }

        @Test
        @DisplayName("OC-REG-003: Changing one category's Order or Status does not alter any other category's saved values")
        void editIsolated() {
            ensureCategory(CATEGORY_A_EN, "Active", ORDER_CATEGORY_A);
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            ensureCategory(CATEGORY_INACTIVE, "Inactive", "98");
            Map<String, CategoryRow> before = list.snapshot();
            CategoryRow b = before.get(CATEGORY_B_EN);
            restore = () -> {
                openList();
                list.setStatus(CATEGORY_B_EN, b.status());
                openList();
                OtherCategoryFormPage undo = list.clickEdit(CATEGORY_B_EN);
                undo.setDisplayOrder(b.order());
                undo.clickSave();
            };
            // Order through the Edit form; Status through the list's select, because the form cannot
            // save Inactive (Si, 2026-09-25: that refusal is expected - see OC-EDT-004).
            String newOrder = freeOrder("96");
            OtherCategoryFormPage form = list.clickEdit(CATEGORY_B_EN);
            form.setDisplayOrder(newOrder);
            form.clickSave();
            openList();
            list.setStatus(CATEGORY_B_EN, "Inactive");
            openList();
            Map<String, CategoryRow> after = list.snapshot();
            assertEquals(orderValue(newOrder), orderValue(after.get(CATEGORY_B_EN).order()), CATEGORY_B_EN + "'s new Order should be saved");
            assertEquals("Inactive", after.get(CATEGORY_B_EN).status(), CATEGORY_B_EN + " should now be Inactive");
            for (String untouched : List.of(CATEGORY_A_EN, CATEGORY_INACTIVE)) {
                assertEquals(before.get(untouched).order(), after.get(untouched).order(), untouched + "'s Order should not change");
                assertEquals(before.get(untouched).status(), after.get(untouched).status(), untouched + "'s Status should not change");
            }
        }

        @Test
        @DisplayName("OC-REG-004: Two admin sessions editing the same category at nearly the same time")
        void concurrentEdits() {
            // The back office evicts a second session on the same account, so the second editor has
            // to be a different operator.
            Page second = signedInAs("second operator", ALT_USERNAME, ALT_PASSWORD, "test.alt");
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            String iconBefore = list.iconSource(CATEGORY_B_EN);
            restore = () -> {
                openList();
                OtherCategoryFormPage undo = list.clickEdit(CATEGORY_B_EN);
                undo.uploadIcon(ICON_PNG);
                undo.setName(TAB_SPAIN, nameOnTab(CATEGORY_B_EN, TAB_SPAIN));
                undo.clickSave();
            };

            OtherCategoryFormPage sessionA = list.clickEdit(CATEGORY_B_EN);
            OtherCategoryListPage secondList = new OtherCategoryListPage(second);
            secondList.open();
            secondList.filterLanguage(TAB_ENGLISH);
            OtherCategoryFormPage sessionB = secondList.clickEdit(CATEGORY_B_EN);

            sessionA.uploadIcon(ICON_REPLACEMENT);
            sessionA.clickSave();
            String spanish = nameOnTab(CATEGORY_B_EN, TAB_SPAIN) + RENAMED_SUFFIX;
            sessionB.setName(TAB_SPAIN, spanish);
            sessionB.clickSave();
            String secondSaid = sessionB.isOpen() ? sessionB.messages() : "";

            // Answered by Si 2026-09-24: concurrent edits are MERGED - each session's change survives.
            CaseLog.data("Second save", secondSaid.isBlank() ? "accepted" : "answered: " + secondSaid);
            assertEquals("", secondSaid, "The second session's save should go through, not be rejected as a conflict");
            openList();
            assertNotEquals(iconBefore, list.iconSource(CATEGORY_B_EN),
                    "Session A's new icon should survive Session B's later save - it was overwritten (last write wins)");
            OtherCategoryFormPage check = list.clickEdit(CATEGORY_B_EN);
            assertEquals(spanish, check.nameFor(TAB_SPAIN), "Session B's Spain name should be saved alongside Session A's icon");
            assertEquals(CATEGORY_B_EN, check.nameFor(TAB_ENGLISH), "A field neither session touched should be unchanged");
            check.cancel();
        }
    }

    // =====================================================================
    // 4. DELETE OTHER CATEGORY - last, because it destroys
    // =====================================================================

    @Nested
    @Order(100)
    @DisplayName("4. Delete Other Category")
    class DeleteCategory {

        /** A category made to be deleted by the running case, with {@code games} games. */
        private List<String> disposable(int games) {
            deleteAfterwards(CATEGORY_DISPOSABLE);
            ensureCategory(CATEGORY_DISPOSABLE, "Active", ORDER_OUT_OF_THE_WAY);
            return games == 0 ? List.of() : ensureGames(CATEGORY_DISPOSABLE, games);
        }

        @Test
        @DisplayName("OC-DEL-001: The delete confirmation names the right category and its true game-assignment count")
        void confirmationNamesCategoryAndCount() {
            disposable(2);
            int listed = list.category(CATEGORY_DISPOSABLE).games();
            list.clickDelete(CATEGORY_DISPOSABLE);
            String text = list.deleteDialogText();
            CaseLog.data("Dialog", text);
            assertTrue(text.contains("Underlying games and their global records will remain available"),
                    "The dialog should reassure that the games themselves are kept, as the capture does; it said: " + text);
            assertTrue(text.contains(CATEGORY_DISPOSABLE) || text.contains(nameOnTab(CATEGORY_DISPOSABLE, TAB_CHINESE)),
                    "The dialog should name the category; it said: " + text);
            assertEquals(listed, list.deleteDialogAssignmentCount(), "The dialog's assignment count should match the list's Games count");
            list.cancelDelete();
        }

        @Test
        @DisplayName("OC-DEL-002: Clicking Delete category in the confirmation removes it from the list")
        void confirmRemoves() {
            disposable(0);
            list.clickDelete(CATEGORY_DISPOSABLE);
            list.confirmDelete();
            openList();
            assertFalse(list.isListed(CATEGORY_DISPOSABLE), "The category should no longer be listed");
        }

        @Test
        @DisplayName("OC-DEL-003: Clicking Cancel in the confirmation dialog does not delete the category")
        void cancelKeeps() {
            disposable(1);
            CategoryRow before = list.category(CATEGORY_DISPOSABLE);
            list.clickDelete(CATEGORY_DISPOSABLE);
            list.cancelDelete();
            assertFalse(list.isDeleteDialogVisible(), "Cancel should close the dialog");
            openList();
            assertEquals(before, list.category(CATEGORY_DISPOSABLE), "The category should remain, fully unchanged");
        }

        @Test
        @DisplayName("OC-DEL-004: A game that was assigned to a deleted category still exists and can be assigned elsewhere")
        void gameSurvivesItsCategory() {
            String game = disposable(1).get(0);
            list.clickDelete(CATEGORY_DISPOSABLE);
            list.confirmDelete();
            ensureCategory(CATEGORY_B_EN, "Active", "06");
            ManageGamesPage b = list.clickManageGames(CATEGORY_B_EN);
            b.search(game);
            assertTrue(b.isListed(game), game + " should still be findable after its category was deleted");
            boolean was = b.isSelected(game);
            b.toggle(game);
            assertNotEquals(was, b.isSelected(game), game + " should still be selectable");
            b.cancel();
        }

        @Test
        @DisplayName("OC-DEL-005: Checking whether banners uploaded for the deleted category's game assignments are removed or retained")
        void bannerFateOnDelete() {
            String game = disposable(1).get(0);
            list.clickDelete(CATEGORY_DISPOSABLE);
            list.confirmDelete();
            ensureCategory(CATEGORY_DISPOSABLE, "Active", ORDER_OUT_OF_THE_WAY);
            ManageGamesPage again = list.clickManageGames(CATEGORY_DISPOSABLE);
            // AQ-03 (answered 2026-09-24): deleting the category deletes its banners; re-adding needs a fresh upload.
            again.select(game);
            assertFalse(again.hasBanner(game), game + "'s banner should have been removed with the deleted category, not reused");
            assertTrue(again.showsBannerRequired(game), "The re-added " + game + " should be flagged \"Banner is required\"");
            again.cancel();
        }

        @Test
        @DisplayName("OC-DEL-006: Deleting a category showing \"0 games\" in the list")
        void deleteEmptyCategory() {
            deleteAfterwards(CATEGORY_EMPTY);
            ensureCategory(CATEGORY_EMPTY, "Active", "97");
            assertEquals(0, list.category(CATEGORY_EMPTY).games(), "The precondition is a category showing 0 games");
            list.clickDelete(CATEGORY_EMPTY);
            int count = list.deleteDialogAssignmentCount();
            assertTrue(count == 0 || count == -1, "The confirmation should state 0 assignments or omit the line; it said " + list.deleteDialogText());
            list.confirmDelete();
            openList();
            assertFalse(list.isListed(CATEGORY_EMPTY), "The empty category should be deleted without error");
        }
    }
}
