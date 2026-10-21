package org.sengmean.tests.product;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.sengmean.base.BaseTest;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.page.product.ClientCategoryHomePage;
import org.sengmean.page.product.ClientCategoryHomePage.Card;
import org.sengmean.page.product.ClientCategoryHomePage.Launch;
import org.sengmean.page.product.ClientCategoryHomePage.LaunchOutcome;
import org.sengmean.page.product.ClientCategoryHomePage.Section;
import org.sengmean.page.product.ClientCategoryPage;
import org.sengmean.page.product.ClientCategoryPage.Category;
import org.sengmean.page.product.ClientCategoryPage.Game;
import org.sengmean.report.CaseLog;
import org.sengmean.support.Device;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>Other Category on the client platform's new UI</b>, on the HK brand ({@code client.hk.url},
 * {@code https://test-v2.138hk.vip}) - what a visitor and a member see of the categories the back
 * office configures, and whether it holds up on every device.
 *
 * <h2>Why a class of its own</h2>
 * {@code OtherCategoryTest}'s client rows ({@code OC-CLI-001}..{@code 010}) prove that what the
 * operator saved on admin-test reaches that brand's member site, so they need the back office's
 * fixtures and run inside its session. These rows ask a different question - how the shipped UI
 * presents the categories - against a site whose back office this project has no account for, so
 * they take the client's own category feed as the truth of what it was given and judge what it
 * draws against that. Nothing here signs in to a back office.
 *
 * <h2>What the new UI shows (measured 2026-10-02)</h2>
 * See {@link ClientCategoryHomePage}: one panel per category on the home page, in feed order, with
 * icon, name, "More Games &gt;" and a row of banner cards (no caption, no maintenance mark); a
 * category page at {@code /{locale}/game-category-features/{id}} headed with the name and a grid of
 * the same cards; a version picker a fresh visitor must answer with New UI first.
 *
 * <h2>Devices</h2>
 * The compatibility rows run the same walk on five profiles ({@link Device}): a Windows PC and an
 * Android phone in Chromium, a Mac, an iPad and an iPhone in WebKit, since Safari is WebKit and a
 * Chromium window pretending to be an iPhone tests the wrong engine. WebKit must be installed
 * ({@code npx playwright install webkit}); a row that cannot launch it reports Not run saying so.
 *
 * <h2>The categories are whatever is set up at run time</h2>
 * Other Categories are configured in that brand's back office and change from day to day - two,
 * one, none (Si, 2026-10-02). No row names a category, counts them, or expects the first one to
 * exist: each reads the client's feed as it runs ({@code servedCategories}) and validates the page
 * against that list, so a run with no category asserts that no section is drawn and passes
 * ({@code noneServed}), and a run with ten validates ten.
 *
 * <h2>Ids</h2>
 * The rows are project-added to the workbook's Other Category tab, numbered on from its last client
 * row ({@code OC-CLI-010}) as Si's rule has it, and listed in
 * {@code src/test/resources/testdata/added-cases/other-category.tsv}.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(110)
@DisplayName("Other Category - client UI (HK, new UI)")
public class UiOtherCategoryClientHK extends BaseTest {

    /** The HK brand's member site and its testing member, from {@code client.hk.*} in {@code Config/}. */
    static final String HK_URL = ConfigDataProvider.get("client.hk.url", "https://test-v2.138hk.vip").replaceAll("/+$", "");
    static final String HK_USERNAME = ConfigDataProvider.get("client.hk.username", "");
    static final String HK_PASSWORD = ConfigDataProvider.get("client.hk.password", "");
    static final String HK_CAPTCHA = ConfigDataProvider.get("client.hk.captcha", "1111");

    /** The language the rows open the client in, and the one the localization row switches to. */
    static final String ENGLISH = "en";
    static final String TRADITIONAL_CHINESE = "zh-TW";

    /** Where the device rows keep the screenshot of each device's home page, beside the closing captures. */
    private static final Path SHOTS = Path.of(System.getProperty("report.shots", "target/case-screenshots"));

    /** How many arrow presses a row may need to bring its last card into view. */
    private static final int MAX_ARROW_PRESSES = 12;

    /** Launched the first time a Safari row runs; closed with the class. */
    private static Browser webkit;

    /** The device row's own context, closed after the row. */
    private BrowserContext deviceContext;

    /** The PC rows' home page, on the per-test guest page (Chromium, 1920x1080). */
    private ClientCategoryHomePage home;

    @BeforeEach
    void openHome() {
        home = new ClientCategoryHomePage(page(), HK_URL);
    }

    @AfterEach
    void closeDevice() {
        if (deviceContext != null) {
            deviceContext.close();
            deviceContext = null;
        }
    }

    @AfterAll
    static void closeWebkit() {
        if (webkit != null) {
            webkit.close();
            webkit = null;
        }
    }

    // =====================================================================
    // Shared steps
    // =====================================================================

    /**
     * The categories the client serves right now, in its order - read fresh for every row, since
     * Other Categories are whatever the back office holds at run time (Si, 2026-10-02): two one
     * day, one the next, none the day after. Nothing in this class names a category or counts on
     * one existing; every row validates what the page draws against this list, an empty one
     * included.
     */
    private static List<Category> servedCategories(ClientCategoryPage client) {
        List<Category> feed = client.feed();
        CaseLog.data("Categories the client serves", feed.isEmpty() ? "none right now"
                : feed.stream().map(c -> c.nameIn(ENGLISH) + " (" + c.games().size() + " games)").toList().toString());
        return feed;
    }

    /**
     * When the client serves no category, the right display is no section at all - asserted, and
     * the row is done. True when that was the case, so the caller returns.
     */
    private static boolean noneServed(ClientCategoryHomePage ui, List<Category> feed, String where) {
        if (!feed.isEmpty()) {
            return false;
        }
        List<Section> drawn = ui.sections();
        assertTrue(drawn.isEmpty(), "No category is served right now, so " + where + " should draw no section, but it draws " + drawn.stream().map(Section::name).toList());
        CaseLog.data(where, "no category is served right now, and none is drawn - as it should be; there is nothing more to check until one is set up");
        return true;
    }

    /** The first section that has cards to open, or null when none has. */
    private static Section firstWithCards(List<Section> sections) {
        return sections.stream().filter(s -> !s.cards().isEmpty()).findFirst().orElse(null);
    }

    /** The feed's category behind a section, matched on its English name. */
    private static Category served(List<Category> feed, Section section) {
        return feed.stream().filter(c -> c.nameIn(ENGLISH).equalsIgnoreCase(section.name())).findFirst().orElse(null);
    }

    /** Presses the right arrow until the row's last card is in view; false when it never arrives. */
    private static boolean revealWholeRow(ClientCategoryHomePage ui, String name) {
        for (int presses = 0; presses < MAX_ARROW_PRESSES; presses++) {
            Section now = ui.section(name);
            List<Card> cards = now == null ? List.of() : now.cards();
            if (!cards.isEmpty() && cards.get(cards.size() - 1).inView()) {
                CaseLog.data("Arrow presses to reach the last card of \"" + name + "\"", String.valueOf(presses));
                return true;
            }
            if (!ui.pressArrow(name, true)) {
                return false;
            }
        }
        return false;
    }

    /**
     * The cards drawn for a category are its served games - the same banners, however ordered.
     * Measured 2026-10-02: the category page lists the same ten games as the home row but in a
     * different order (the page's own games call sorts them differently). The set is asserted; the
     * order is recorded, since the home row follows the feed and the page does not, which is a
     * finding for the client rather than a failure of either screen.
     */
    private static void assertSameGames(Category category, List<Card> cards, String where) {
        List<String> served = category.games().stream().map(Game::shownImage).toList();
        List<String> drawn = cards.stream().map(Card::src).toList();
        assertEquals(served.stream().sorted().toList(), drawn.stream().sorted().toList(),
                where + " should draw every served game's banner (or catalogue image) of \"" + category.nameIn(ENGLISH) + "\", no more and no fewer");
        CaseLog.data(where + " order", served.equals(drawn) ? "the feed's order, as the home row"
                : "NOT the feed's order - the home row and the category page list the same games differently");
        if (!served.equals(drawn)) {
            CaseLog.evidence(where + " lists \"" + category.nameIn(ENGLISH) + "\"'s games in a different order from the home row.");
        }
    }

    /** A category page's columns are the cards' distinct left edges; what the grid should give at this width. */
    private static int expectedColumns(int viewportWidth, int cards) {
        // grid-cols-2 below the lg breakpoint (1024px); auto-fill of 280px-minimum columns above it.
        return viewportWidth < 1024 ? Math.min(2, cards) : Math.min(3, cards);
    }

    // =====================================================================
    // 1. HOME PAGE SECTIONS AND CATEGORY PAGE - PC, as a visitor
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("1. Home page sections (PC)")
    class HomeSections {

        @Test
        @DisplayName("OC-CLI-011: The new UI home page lists every category the client is served, in order, each with icon, name and More Games")
        void homeListsEveryServedCategory() {
            home.openNewUi(ENGLISH);
            List<Category> feed = servedCategories(home);
            assertEquals("new", home.uiVersion(), "The New UI should be the version in force");
            if (noneServed(home, feed, "the home page")) {
                return;
            }
            List<Section> sections = home.sections();
            assertEquals(feed.stream().map(c -> c.nameIn(ENGLISH)).toList(), sections.stream().map(Section::name).toList(),
                    "The home page should show one section per served category, in the feed's order");
            for (Section section : sections) {
                Category category = served(feed, section);
                assertEquals(category.icon(), section.iconSrc(), "\"" + section.name() + "\" should show the icon it is served");
                assertTrue(section.iconLoaded(), "\"" + section.name() + "\"'s icon should load");
                assertTrue(section.moreGames(), "\"" + section.name() + "\" should offer More Games");
            }
        }

        @Test
        @DisplayName("OC-CLI-012: Every card in a section draws the banner served for that game's assignment, one card per served game")
        void cardsDrawTheServedBanners() {
            home.openNewUi(ENGLISH);
            List<Category> feed = servedCategories(home);
            if (noneServed(home, feed, "the home page")) {
                return;
            }
            for (Section section : home.sections()) {
                Category category = served(feed, section);
                assertTrue(category != null, "\"" + section.name() + "\" is drawn but the client serves no such category");
                List<String> servedImages = category.games().stream().map(Game::shownImage).toList();
                List<String> drawn = section.cards().stream().map(Card::src).toList();
                CaseLog.data("\"" + section.name() + "\"", section.cards().size() + " card(s) for " + category.games().size() + " served game(s)");
                assertEquals(servedImages, drawn, "\"" + section.name() + "\" should draw each served game's banner (or catalogue image), in order");
                assertTrue(section.cards().stream().allMatch(Card::loaded), "Every card image in \"" + section.name() + "\" should load");
                assertTrue(section.cards().stream().allMatch(c -> c.width() == 160),
                        "Cards should be 160px wide on a PC; \"" + section.name() + "\" has " + section.cards().stream().map(Card::width).distinct().toList());
            }
        }

        @Test
        @DisplayName("OC-CLI-013: A row with more cards than fit scrolls by its arrows until the last card is in view; a row that fits has none")
        void overflowRowScrollsByArrows() {
            home.openNewUi(ENGLISH);
            List<Category> feed = servedCategories(home);
            if (noneServed(home, feed, "the home page")) {
                return;
            }
            List<Section> sections = home.sections();
            Section overflowing = sections.stream().filter(Section::overflows).findFirst().orElse(null);
            for (Section section : sections) {
                if (!section.overflows()) {
                    assertFalse(section.leftArrow() || section.rightArrow(),
                            "\"" + section.name() + "\" fits on screen, so it should show no scroll arrows");
                }
            }
            if (overflowing == null) {
                CaseLog.data("Rows that overflow at 1920px", "none - every served category fits in one row (about nine cards), and none shows arrows, as it should");
                return;
            }
            assertTrue(overflowing.rightArrow(), "\"" + overflowing.name() + "\" overflows, so it should show a right arrow");
            assertFalse(overflowing.cards().get(overflowing.cards().size() - 1).inView(),
                    "The precondition is a last card out of view in \"" + overflowing.name() + "\"");
            assertTrue(revealWholeRow(home, overflowing.name()),
                    "Pressing the right arrow should bring the last card of \"" + overflowing.name() + "\" into view");
            assertTrue(home.pressArrow(overflowing.name(), false), "A left arrow should be offered once the row has scrolled");
        }

        @Test
        @DisplayName("OC-CLI-014: More Games opens the category's own page, headed with its name and listing every served game")
        void moreGamesOpensTheCategoryPage() {
            home.openNewUi(ENGLISH);
            List<Category> feed = servedCategories(home);
            if (noneServed(home, feed, "the home page")) {
                return;
            }
            Section first = home.sections().get(0);
            Category category = served(feed, first);
            assertTrue(category != null, "\"" + first.name() + "\" is drawn but the client serves no such category");
            home.clickMoreGames(first.name());
            assertTrue(page().url().contains(ClientCategoryPage.CATEGORY_ROUTE + category.id()),
                    "More Games should open /" + ClientCategoryPage.CATEGORY_ROUTE + category.id() + ", but the address is " + page().url());
            assertEquals(first.name(), home.heading(), "The category page should be headed with the category's name");
            List<Card> cards = home.categoryPageCards();
            assertSameGames(category, cards, "The category page");
            assertTrue(cards.stream().allMatch(Card::loaded), "Every card image on the category page should load");
        }

        @Test
        @DisplayName("OC-CLI-015: A game under maintenance is distinguished on its card (confirm whether)")
        void maintenanceGameIsDistinguished() {
            home.openNewUi(ENGLISH);
            List<Category> feed = servedCategories(home);
            if (noneServed(home, feed, "the home page")) {
                return;
            }
            Category category = null;
            Game game = null;
            for (Category c : feed) {
                game = c.games().stream().filter(Game::maintenance).findFirst().orElse(null);
                if (game != null) {
                    category = c;
                    break;
                }
            }
            if (game == null) {
                CaseLog.data("Games under maintenance", "none is served right now, so no card has a maintenance state to show; nothing more to check until one is put into maintenance");
                return;
            }
            String name = game.names().getOrDefault(ENGLISH, "");
            Section section = home.section(category.nameIn(ENGLISH));
            assertTrue(section != null && section.cards().stream().anyMatch(c -> c.alt().equalsIgnoreCase(name)),
                    name + " is under maintenance and should still be listed in \"" + category.nameIn(ENGLISH) + "\"");
            String extras = home.cardExtras(category.nameIn(ENGLISH), name);
            // Confirm whether (2026-10-02): the card carried nothing but the image - no badge, no dimming.
            // Recorded, not asserted, until the client says what a maintenance card should show.
            CaseLog.data("Maintenance mark on " + name, extras.isEmpty() ? "none - the card is the banner alone" : extras);
            CaseLog.evidence(extras.isEmpty()
                    ? "A visitor cannot tell that " + name + " is under maintenance from its card; the feed says it is."
                    : "The maintenance card carries: " + extras);
        }

        @Test
        @DisplayName("OC-CLI-016: A visitor who opens a game card is asked to log in, and no game opens")
        void visitorIsAskedToLogIn() {
            home.openNewUi(ENGLISH);
            List<Category> feed = servedCategories(home);
            if (noneServed(home, feed, "the home page")) {
                return;
            }
            Section first = firstWithCards(home.sections());
            if (first == null) {
                CaseLog.data("Cards to open", "no served category has a game right now, so there is no card for a visitor to open");
                return;
            }
            int pagesBefore = context().pages().size();
            home.clickCard(first.name(), 0);
            // As the system does it (2026-10-02): a "Please login first" prompt with Close, not the Login dialog.
            String asked = home.asksToLogIn();
            CaseLog.data("How the visitor is asked to log in", asked.isEmpty() ? "not asked" : asked);
            assertFalse(asked.isEmpty(), "A visitor opening a game should be asked to log in (the Login dialog or a \"Please login first\" prompt)");
            assertEquals(pagesBefore, context().pages().size(), "No game window should open for a visitor");
            home.dismissLoginAsk();
        }

        @Test
        @DisplayName("OC-CLI-017: Under Traditional Chinese each section is headed with the category's 繁體 name")
        void sectionsFollowTheViewersLanguage() {
            home.openNewUi(ENGLISH);
            List<Category> feed = servedCategories(home);
            home.openNewUi(TRADITIONAL_CHINESE);
            assertEquals("hk", home.localeFromUrl(), "The client should be on its Traditional Chinese locale");
            if (noneServed(home, feed, "the home page under 繁體")) {
                return;
            }
            List<String> expected = feed.stream().map(c -> c.nameIn(TRADITIONAL_CHINESE)).toList();
            List<String> shown = home.sections().stream().map(Section::name).toList();
            CaseLog.data("Section names under 繁體", shown.toString());
            assertEquals(expected, shown, "Each section should be headed with the name saved for 繁體");
        }

        @Test
        @DisplayName("OC-CLI-018: A category page for an id the client does not serve shows \"No Games\" rather than an error")
        void unknownCategoryShowsNoGames() {
            home.openNewUi(ENGLISH);
            home.openPath(ENGLISH + "/" + ClientCategoryPage.CATEGORY_ROUTE + "999999");
            String body = home.bodyText();
            CaseLog.data("Heading for an unknown id", "\"" + home.heading() + "\"");
            assertTrue(body.contains("No Games"), "An unknown category id should show \"No Games\"");
            assertTrue(home.heading().isBlank(), "An unknown category id should carry no category name");
            assertFalse(body.toLowerCase().contains("error") || body.contains("500") && body.toLowerCase().contains("server"),
                    "An unknown category id should not surface an error");
        }

        @Test
        @DisplayName("OC-CLI-019: A signed-in member who opens a game card is not asked to log in again (the game launches)")
        void memberOpensAGame() {
            if (HK_USERNAME.isBlank() || HK_PASSWORD.isBlank()) {
                abort("No member account is configured for the HK client: set client.hk.username / client.hk.password in Config/.");
            }
            home.openNewUi(ENGLISH);
            List<Category> feed = servedCategories(home);
            assertTrue(home.signIn(HK_USERNAME, HK_PASSWORD, HK_CAPTCHA), "The testing member should be able to sign in");
            home.openNewUi(ENGLISH);
            if (noneServed(home, feed, "the home page for a member")) {
                return;
            }
            Section first = firstWithCards(home.sections());
            if (first == null) {
                CaseLog.data("Cards to open", "no served category has a game right now, so there is no card for the member to open");
                return;
            }
            String before = page().url();
            int pagesBefore = context().pages().size();
            home.clickCard(first.name(), 0);
            page().waitForTimeout(3_000);
            String opened = context().pages().size() > pagesBefore ? "a new window (" + context().pages().get(context().pages().size() - 1).url() + ")"
                    : !page().url().equals(before) ? "this window, now at " + page().url()
                    : "nothing visible - still at " + page().url();
            CaseLog.data("Opening " + first.cards().get(0).alt() + " as a member", opened);
            String asked = home.asksToLogIn();
            assertTrue(asked.isEmpty(), "A signed-in member should not be asked to log in again, but the site showed the " + asked);
        }

        @Test
        @DisplayName("OC-CLI-030: A signed-in member can open every game listed in every section on the home page")
        void memberOpensEveryGameFromTheSections() {
            signInAsMember();
            List<Category> feed = servedCategories(home);
            if (noneServed(home, feed, "the home page for a member")) {
                return;
            }
            List<String> failures = new ArrayList<>();
            int opened = 0;
            for (Category category : feed) {
                String section = category.nameIn(ENGLISH);
                for (Game game : category.games()) {
                    String name = game.names().getOrDefault(ENGLISH, "");
                    home.openNewUi(ENGLISH);
                    Launch launch = home.launchFromSection(section, name);
                    judge(launch, game, name + " in \"" + section + "\"", failures);
                    opened += launch.outcome().launched() ? 1 : 0;
                    home.closeOtherWindows();
                }
            }
            CaseLog.data("Games opened from the sections", opened + " of " + feed.stream().mapToInt(c -> c.games().size()).sum());
            assertTrue(failures.isEmpty(), "Every game a member opens from a section should launch (or say it is under maintenance), but: " + failures);
        }

        @Test
        @DisplayName("OC-CLI-031: A signed-in member can open every game on every category's own page")
        void memberOpensEveryGameFromTheCategoryPages() {
            signInAsMember();
            List<Category> feed = servedCategories(home);
            if (noneServed(home, feed, "the home page for a member")) {
                return;
            }
            List<String> failures = new ArrayList<>();
            int opened = 0;
            for (Category category : feed) {
                String section = category.nameIn(ENGLISH);
                for (Game game : category.games()) {
                    String name = game.names().getOrDefault(ENGLISH, "");
                    home.open(category);
                    Launch launch = home.launchFromCategoryPage(game.shownImage(), name);
                    judge(launch, game, name + " on \"" + section + "\"'s page", failures);
                    opened += launch.outcome().launched() ? 1 : 0;
                    home.closeOtherWindows();
                }
            }
            CaseLog.data("Games opened from the category pages", opened + " of " + feed.stream().mapToInt(c -> c.games().size()).sum());
            assertTrue(failures.isEmpty(), "Every game a member opens from a category page should launch (or say it is under maintenance), but: " + failures);
        }

        /** Signs the testing member in on the home page, or aborts naming what is missing. */
        private void signInAsMember() {
            if (HK_USERNAME.isBlank() || HK_PASSWORD.isBlank()) {
                abort("No member account is configured for the HK client: set client.hk.username / client.hk.password in Config/.");
            }
            home.openNewUi(ENGLISH);
            if (!home.signIn(HK_USERNAME, HK_PASSWORD, HK_CAPTCHA)) {
                abort("The testing member " + HK_USERNAME + " could not sign in, so no game can be opened as a member.");
            }
        }

        /**
         * The condition each game is held to: a game the feed flags as under maintenance may decline
         * with a maintenance notice (a launch is accepted too, and noted); any other game must launch -
         * a new window, a navigation or an embedded frame - and must never be asked to log in.
         */
        private void judge(Launch launch, Game game, String what, List<String> failures) {
            String condition = game.maintenance() ? "under maintenance" : "available";
            CaseLog.data(what + " (" + condition + ")", launch.toString());
            if (launch.outcome() == LaunchOutcome.LOGIN_ASKED) {
                failures.add(what + ": asked to log in although signed in");
            } else if (game.maintenance()) {
                if (launch.outcome().launched()) {
                    CaseLog.evidence(what + " is flagged under maintenance in the feed, yet it launched.");
                } else if (launch.outcome() != LaunchOutcome.MAINTENANCE) {
                    CaseLog.evidence(what + " is under maintenance; the site answered with " + launch + " rather than a maintenance notice.");
                }
            } else if (!launch.outcome().launched()) {
                failures.add(what + ": " + launch);
            }
        }
    }

    // =====================================================================
    // 2. RESPONSIVE AND COMPATIBILITY - one row per device
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Responsive and compatibility")
    class Devices {

        @Test
        @DisplayName("OC-CLI-020: The sections and category page render and work on a PC (Windows, Chrome, 1920x1080)")
        void onPc() {
            deviceWalk(Device.DESKTOP_PC);
        }

        @Test
        @DisplayName("OC-CLI-021: The sections and category page render and work on a Mac (Safari/WebKit, 1440x900)")
        void onMac() {
            deviceWalk(Device.DESKTOP_MAC);
        }

        @Test
        @DisplayName("OC-CLI-022: The sections and category page render and work on an iPad (Safari/WebKit, 820x1180, touch)")
        void onIpad() {
            deviceWalk(Device.IPAD);
        }

        @Test
        @DisplayName("OC-CLI-023: The sections and category page render and work on an iPhone (Safari/WebKit, 390x844, touch)")
        void onIphone() {
            deviceWalk(Device.IPHONE);
        }

        @Test
        @DisplayName("OC-CLI-024: The sections and category page render and work on an Android phone (Chrome, 412x915, touch)")
        void onAndroid() {
            deviceWalk(Device.ANDROID);
        }

        @Test
        @DisplayName("OC-CLI-025: A signed-in member sees the sections and opens a game on a PC (Windows, Chrome, 1920x1080)")
        void memberOnPc() {
            memberWalk(Device.DESKTOP_PC);
        }

        @Test
        @DisplayName("OC-CLI-026: A signed-in member sees the sections and opens a game on a Mac (Safari/WebKit, 1440x900)")
        void memberOnMac() {
            memberWalk(Device.DESKTOP_MAC);
        }

        @Test
        @DisplayName("OC-CLI-027: A signed-in member sees the sections and opens a game on an iPad (Safari/WebKit, 820x1180, touch)")
        void memberOnIpad() {
            memberWalk(Device.IPAD);
        }

        @Test
        @DisplayName("OC-CLI-028: A signed-in member sees the sections and opens a game on an iPhone (Safari/WebKit, 390x844, touch)")
        void memberOnIphone() {
            memberWalk(Device.IPHONE);
        }

        @Test
        @DisplayName("OC-CLI-029: A signed-in member sees the sections and opens a game on an Android phone (Chrome, 412x915, touch)")
        void memberOnAndroid() {
            memberWalk(Device.ANDROID);
        }
    }

    /**
     * The member's side of the device walk: sign in as the testing member on the device, see the
     * same sections a visitor sees, open the first category's page, and tap a game - which must
     * not ask the member to log in again. What the tap opens is recorded (a new window, a
     * navigation, or nothing visible), since the launch itself belongs to the game provider.
     */
    private void memberWalk(Device device) {
        if (HK_USERNAME.isBlank() || HK_PASSWORD.isBlank()) {
            abort("No member account is configured for the HK client: set client.hk.username / client.hk.password in Config/.");
        }
        Page on = openOn(device);
        ClientCategoryHomePage ui = new ClientCategoryHomePage(on, HK_URL);
        try {
            memberSteps(on, ui, device);
        } catch (Throwable failed) {
            capture(on, device, "member-failed");
            throw failed;
        }
    }

    private void memberSteps(Page on, ClientCategoryHomePage ui, Device device) {
        ui.openNewUi(ENGLISH);
        List<Category> feed = servedCategories(ui);
        int width = ui.viewportWidth();
        CaseLog.data("Device", device + " in " + device.engine + ", window " + width + "px wide");
        assertTrue(ui.signIn(HK_USERNAME, HK_PASSWORD, HK_CAPTCHA), "The testing member should be able to sign in on " + device.label);
        assertFalse(ui.isVisitor(), "The header should no longer offer Login once signed in on " + device.label);
        capture(on, device, "member-home");

        ui.openNewUi(ENGLISH);
        assertFalse(ui.isVisitor(), "The member should still be signed in after the home page reloads on " + device.label);
        if (noneServed(ui, feed, "the home page for a member on " + device.label)) {
            return;
        }
        List<Section> sections = ui.sections();
        assertEquals(feed.stream().map(c -> c.nameIn(ENGLISH)).toList(), sections.stream().map(Section::name).toList(),
                "A member should see every served category as a section on " + device.label);
        assertEquals(0, ui.horizontalOverflow(), "Nothing should run off the side of the screen for a member on " + device.label);
        for (Section section : sections) {
            Category category = served(feed, section);
            assertTrue(ui.headerOnOneLine(section.name()), "\"" + section.name() + "\"'s header should sit on one line for a member on " + device.label);
            assertEquals(category.games().size(), section.cards().size(), "\"" + section.name() + "\" should draw one card per served game for a member on " + device.label);
            if (!section.cards().isEmpty()) {
                assertTrue(section.cards().get(0).loaded(), "The first card of \"" + section.name() + "\" should load for a member on " + device.label);
            }
        }

        Section first = firstWithCards(sections);
        if (first == null) {
            CaseLog.data("Cards to open on " + device.label, "no served category has a game right now, so there is no card for the member to open");
            return;
        }
        Category category = served(feed, first);
        ui.clickMoreGames(first.name());
        assertTrue(on.url().contains(ClientCategoryPage.CATEGORY_ROUTE + category.id()),
                "More Games should open the category page for a member on " + device.label + "; the address is " + on.url());
        assertEquals(first.name(), ui.heading(), "The category page should be headed with the name for a member on " + device.label);
        List<Card> cards = ui.categoryPageCards();
        assertSameGames(category, cards, "The category page for a member on " + device.label);
        capture(on, device, "member-category");

        String before = on.url();
        int pagesBefore = deviceContext.pages().size();
        ui.clickCategoryPageCard(0);
        on.waitForTimeout(3_000);
        String opened = deviceContext.pages().size() > pagesBefore
                ? "a new window (" + deviceContext.pages().get(deviceContext.pages().size() - 1).url() + ")"
                : !on.url().equals(before) ? "this window, now at " + on.url()
                : "nothing visible - still at " + on.url();
        CaseLog.data("Opening " + cards.get(0).alt() + " as a member on " + device.label, opened);
        String asked = ui.asksToLogIn();
        assertTrue(asked.isEmpty(), "A signed-in member should not be asked to log in again on " + device.label + ", but the site showed the " + asked);
        capture(on, device, "member-game");
    }

    /**
     * The same walk on every device: the home page in the New UI, every section drawn whole and in
     * place, its overflow row reachable by arrow or by swipe, the first category's page in the
     * grid the width calls for, and a visitor's tap on a game answered with the Login dialog.
     */
    private void deviceWalk(Device device) {
        Page on = openOn(device);
        ClientCategoryHomePage ui = new ClientCategoryHomePage(on, HK_URL);
        try {
            walk(on, ui, device);
        } catch (Throwable failed) {
            // The closing capture is of the guest page, not the device's; keep the device's screen as it failed.
            capture(on, device, "failed");
            throw failed;
        }
    }

    private void walk(Page on, ClientCategoryHomePage ui, Device device) {
        ui.openNewUi(ENGLISH);
        List<Category> feed = servedCategories(ui);
        int width = ui.viewportWidth();
        CaseLog.data("Device", device + " in " + device.engine + ", window " + width + "px wide");
        assertEquals("new", ui.uiVersion(), "The New UI should be in force on " + device.label);
        if (noneServed(ui, feed, "the home page on " + device.label)) {
            capture(on, device, "home");
            return;
        }

        List<Section> sections = ui.sections();
        assertEquals(feed.stream().map(c -> c.nameIn(ENGLISH)).toList(), sections.stream().map(Section::name).toList(),
                "Every served category should be a section on " + device.label);
        assertEquals(0, ui.horizontalOverflow(), "Nothing should run off the side of the screen on " + device.label);
        int cardWidth = width < 640 ? 140 : 160;
        Section overflowing = null;
        for (Section section : sections) {
            Category category = served(feed, section);
            assertTrue(ui.headerOnOneLine(section.name()),
                    "\"" + section.name() + "\"'s icon, name and More Games should sit on one line within the screen on " + device.label);
            assertTrue(section.iconLoaded(), "\"" + section.name() + "\"'s icon should load on " + device.label);
            assertEquals(category.games().size(), section.cards().size(), "\"" + section.name() + "\" should draw one card per served game on " + device.label);
            if (section.cards().isEmpty()) {
                continue;
            }
            Card first = section.cards().get(0);
            assertEquals(cardWidth, first.width(), "Cards should be " + cardWidth + "px wide at " + width + "px on " + device.label);
            assertTrue(first.loaded(), "The first card of \"" + section.name() + "\" should load on " + device.label);
            assertTrue(first.inView(), "The first card of \"" + section.name() + "\" should be on screen on " + device.label);
            if (device.touch) {
                assertTrue(first.width() >= 44 && first.height() >= 44, "A card should be a comfortable tap target on " + device.label);
            }
            if (section.overflows() && overflowing == null) {
                overflowing = section;
            }
        }
        if (overflowing != null) {
            if (width < 640) {
                assertFalse(overflowing.leftArrow() || overflowing.rightArrow(),
                        "Phones scroll the row by touch, so \"" + overflowing.name() + "\" should show no arrows on " + device.label);
                int scrolled = ui.swipeRow(overflowing.name());
                assertTrue(scrolled > 0, "The row of \"" + overflowing.name() + "\" should scroll sideways on " + device.label);
            } else {
                assertTrue(overflowing.rightArrow(), "\"" + overflowing.name() + "\" overflows, so it should show a right arrow on " + device.label);
                assertTrue(revealWholeRow(ui, overflowing.name()),
                        "The arrows should bring the last card of \"" + overflowing.name() + "\" into view on " + device.label);
            }
        } else {
            CaseLog.evidence("No row overflowed at " + width + "px, so sideways scrolling was not exercised on " + device.label + ".");
        }
        capture(on, device, "home");

        Section first = firstWithCards(sections);
        if (first == null) {
            CaseLog.data("Cards to open on " + device.label, "no served category has a game right now, so the category page and a visitor's tap have nothing to show");
            return;
        }
        Category category = served(feed, first);
        ui.clickMoreGames(first.name());
        assertTrue(on.url().contains(ClientCategoryPage.CATEGORY_ROUTE + category.id()),
                "More Games should open the category page on " + device.label + "; the address is " + on.url());
        assertEquals(first.name(), ui.heading(), "The category page should be headed with the name on " + device.label);
        List<Card> cards = ui.categoryPageCards();
        assertSameGames(category, cards, "The category page on " + device.label);
        assertTrue(cards.stream().allMatch(Card::loaded), "Every card image on the category page should load on " + device.label);
        assertTrue(cards.stream().allMatch(Card::inView), "Every card should fit within the screen's width on " + device.label);
        assertEquals(0, ui.horizontalOverflow(), "The category page should not run off the side on " + device.label);
        int columns = ui.categoryPageColumns();
        CaseLog.data("Category page on " + device.label, cards.size() + " card(s) in " + columns + " column(s)");
        int expected = expectedColumns(width, cards.size());
        assertTrue(width < 1024 ? columns == expected : columns >= expected,
                "At " + width + "px the grid should have " + (width < 1024 ? "" : "at least ") + expected + " column(s), not " + columns + " on " + device.label);
        capture(on, device, "category");

        // Measured 2026-10-02: a visitor's tap on a category page card does nothing - no prompt, no
        // navigation - while the same card on the home row answers "Please login first". Recorded
        // as a finding; the login ask is then asserted where the site does give it, on the home row.
        int pagesBefore = deviceContext.pages().size();
        String before = on.url();
        ui.clickCategoryPageCard(0);
        String onCategoryPage = ui.asksToLogIn();
        CaseLog.data("A visitor's tap on the category page on " + device.label,
                !onCategoryPage.isEmpty() ? onCategoryPage : !on.url().equals(before) ? "went to " + on.url() : "nothing - no prompt, no navigation");
        if (onCategoryPage.isEmpty() && on.url().equals(before)) {
            CaseLog.evidence("The category page's cards do nothing for a visitor on " + device.label + "; the home row's cards ask to log in.");
        }
        assertEquals(pagesBefore, deviceContext.pages().size(), "No game window should open for a visitor on " + device.label);
        ui.dismissLoginAsk();

        ui.openNewUi(ENGLISH);
        ui.clickCard(first.name(), 0);
        String asked = ui.asksToLogIn();
        CaseLog.data("How the visitor is asked to log in on " + device.label, asked.isEmpty() ? "not asked" : asked);
        assertFalse(asked.isEmpty(), "A visitor tapping a game on the home row should be asked to log in (the Login dialog or a \"Please login first\" prompt) on " + device.label);
        assertEquals(pagesBefore, deviceContext.pages().size(), "No game window should open for a visitor on " + device.label);
        capture(on, device, "login");
    }

    /** A page on the device's engine, in its viewport, density, touch and user agent. */
    private Page openOn(Device device) {
        Browser on = device.engine == Device.Engine.WEBKIT ? webkitOrAbort() : browser;
        deviceContext = newContext(on, device.contextOptions());
        return deviceContext.newPage();
    }

    /** WebKit, launched once; a Safari row reports Not run naming the install when it cannot. */
    private static Browser webkitOrAbort() {
        if (webkit == null) {
            try {
                webkit = playwright.webkit().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS).setSlowMo(SLOW_MO));
            } catch (RuntimeException notInstalled) {
                String why = notInstalled.getMessage() == null ? "" : notInstalled.getMessage().split("\\R", 2)[0];
                abort("The Safari rows run in WebKit, which could not be launched (" + why + "). Install it with "
                        + "`npx playwright install webkit` (or ./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI "
                        + "-Dexec.args=\"install webkit\") and rerun.");
            }
        }
        return webkit;
    }

    /** Keeps a screenshot of the device's screen beside the report's closing captures, and says where. */
    private static void capture(Page on, Device device, String what) {
        try {
            Path file = SHOTS.resolve("UiOtherCategoryClientHK." + device.name().toLowerCase() + "." + what + ".png");
            java.nio.file.Files.createDirectories(SHOTS);
            on.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(false));
            CaseLog.evidence(device.label + " - " + what + ": " + file);
        } catch (Exception couldNotCapture) {
            CaseLog.evidence("No screenshot of " + what + " on " + device.label + ": " + couldNotCapture.getMessage());
        }
    }
}
