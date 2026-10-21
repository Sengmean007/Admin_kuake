package org.sengmean.page.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import org.sengmean.base.BasePage;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How an Other Category looks on the <b>client platform</b> - the member site - rather than in the
 * back office. The {@code OC-CLI-*} rows and {@code OC-I18N-003}/{@code -004} are all about this
 * end: what the operator configured is only half the claim; the other half is what a member sees.
 *
 * <h2>What the member site ships (measured 2026-10-02)</h2>
 * Until then the rows read the landing page for a heading carrying the category's name, and found
 * nothing - the landing page does not list Other Categories. The site does carry the feature, in
 * two places:
 * <ul>
 *   <li><b>The category feed</b>, {@code GET /api/member/game-category-features?currency=..&page=1&paginate=..},
 *       open to a guest. It lists the Active categories that have games, in Display Order, each with
 *       its twelve names, its icon and its games (name, catalogue image, and the banner uploaded for
 *       this category's assignment). The home page's category sections are rendered from exactly
 *       this call, in this order - but only under the site's "new" UI version, which admin-test does
 *       not enable, so no page lists the categories on screen today. {@link #feed()} reads it.</li>
 *   <li><b>A category's own page</b>, {@code /{locale}/game-category-features/{id}}: a card headed
 *       with the category's name in the viewer's language and a grid of its games, each drawn as
 *       its banner (or its catalogue image when it has none) with no caption. Nothing on the site
 *       links to it yet; it is reached by address. {@link #open(Category)} opens it.</li>
 * </ul>
 * So "shown on the client" here means: listed in the feed under the name in the language in force,
 * and rendered under that name on its page. The feed is the client's own data, not the back
 * office's, so a category missing from it is missing for the member too.
 *
 * <h2>Language</h2>
 * The site is a Next.js {@code [locale]} route, and a locale typed into the address is replaced by
 * the one the site remembers - {@code /vi/...} came back as {@code /en/...}. A language is chosen
 * through the globe in the top bar, as NewWeibo_automation's HomePage does, and the site's locale
 * codes are not the feed's keys: Traditional Chinese is {@code hk} on the site and {@code zh-TW} in
 * the feed. {@link Language} carries both; {@link #openIn(String)} accepts either.
 */
public class ClientCategoryPage extends BasePage {

    /** The member site's origin, {@code client.url} in {@code Config/}. */
    public static final String CLIENT_URL = ConfigDataProvider.get("client.url", "https://test-v2.1083.city")
            .replaceAll("/+$", "");

    /** The client's category feed - the call its home-page sections are rendered from. */
    public static final String FEED_PATH = "/api/member/game-category-features";

    /** A category's page under the locale: {@code /en/game-category-features/418}. */
    public static final String CATEGORY_ROUTE = "game-category-features/";

    /** The feed is per currency; the suite's categories are created under {@code test.currency}. */
    private static final String CURRENCY = ConfigDataProvider.get("test.currency", "HKD");

    /** The globe that opens the language list, and one row of the open list ("Vietnamese│Tiếng Việt"). */
    private static final String GLOBE = "button:has(svg[data-icon='globe'])";
    private static final String LANGUAGE_ROW = "div[class*='HeaderTop_title'][class*='HeaderTop_desktop']";

    /** The category's name at the top of its card on its page. */
    private static final String HEADING = "div.text-lg.font-semibold";

    /** How long the site is given for the navigation it makes after a language is chosen. */
    private static final long LANGUAGE_SETTLE_MS = 3_500;

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * A language three ways: the key the feed and the back office use for its name, the English
     * label of its row in the client's language list, and the locale the site puts in the address
     * once it is chosen (read off the site on 2026-08-29 and again on 2026-10-02).
     */
    public enum Language {
        ENGLISH("en", "English", "en"),
        TRADITIONAL_CHINESE("zh-TW", "Traditional Chinese", "hk"),
        SIMPLIFIED_CHINESE("zh-CN", "Simplified Chinese", "cn"),
        JAPANESE("ja", "Japanese", "ja"),
        THAI("th", "Thai", "th"),
        VIETNAMESE("vi", "Vietnamese", "vi"),
        SPANISH("es", "Spanish", "es"),
        PORTUGUESE("pt", "Portuguese", "pt"),
        MALAY("ms", "Malay", "ms"),
        INDONESIAN("id", "Indonesian", "id"),
        FILIPINO("tl", "Filipino", "tl"),
        BENGALI("bn", "Bengali", "bn");

        private final String key;
        private final String label;
        private final String locale;

        Language(String key, String label, String locale) {
            this.key = key;
            this.label = label;
            this.locale = locale;
        }

        /** The feed's key for the name in this language, e.g. "zh-TW". */
        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        /** The path segment the site uses once this language is chosen, e.g. "hk". */
        public String locale() {
            return locale;
        }

        /** By feed key or site locale, either way; English when the code is unknown. */
        public static Language of(String code) {
            String wanted = code == null ? "" : code.trim();
            for (Language language : values()) {
                if (language.key.equalsIgnoreCase(wanted) || language.locale.equalsIgnoreCase(wanted)) {
                    return language;
                }
            }
            return ENGLISH;
        }
    }

    /** A game as the feed lists it inside a category; {@code maintenance} is the feed's {@code isMaintenance}. */
    public record Game(Map<String, String> names, String banner, String image, boolean maintenance) {
        /** True when this is the game's name in any language. */
        public boolean isNamed(String name) {
            return names.values().stream().anyMatch(n -> same(n, name));
        }

        /** What the client draws for the game: the banner uploaded for this assignment, else the catalogue image, else nothing. */
        public String shownImage() {
            return !banner.isBlank() ? banner : image;
        }
    }

    /** A category as the feed lists it, games included. */
    public record Category(int id, Map<String, String> names, String icon, int sort, List<Game> games) {
        /** The name in a language by its feed key ("en", "zh-TW", "bn"), or empty. */
        public String nameIn(String key) {
            return names.getOrDefault(key, "");
        }

        /** True when this is the category's name in any language. */
        public boolean isNamed(String name) {
            return names.values().stream().anyMatch(n -> same(n, name));
        }

        /** The game of this name, in any language, or null. */
        public Game game(String name) {
            return games.stream().filter(g -> g.isNamed(name)).findFirst().orElse(null);
        }
    }

    /** The language the client was last opened in - what {@link #isShown(String)} reads names in. */
    private Language language = Language.ENGLISH;

    /** The member site this instance reads - {@link #CLIENT_URL} unless another was given. */
    private final String clientUrl;

    public ClientCategoryPage(Page page) {
        this(page, CLIENT_URL);
    }

    /** For a second member site, such as the HK brand's new UI ({@code client.hk.url}). */
    public ClientCategoryPage(Page page, String clientUrl) {
        super(page);
        this.clientUrl = clientUrl.replaceAll("/+$", "");
    }

    /** The member site's origin this instance reads. */
    public String clientUrl() {
        return clientUrl;
    }

    // ---------------------------------------------------------------
    // Opening the client, in a language
    // ---------------------------------------------------------------

    /**
     * Opens the member site's landing page in a language ("en", "zh-TW", "vi", "bn"... by feed key
     * or site locale), choosing it from the language list when the site is not already on it.
     */
    public void openIn(String language) {
        Language wanted = Language.of(language);
        String address = clientUrl + "/" + wanted.locale() + "/";
        CaseLog.step("Opening the client platform at " + address);
        page.navigate(address, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        page.waitForTimeout(2_500);
        if (!wanted.locale().equals(localeFromUrl())) {
            selectLanguage(wanted);
        }
        this.language = wanted;
        CaseLog.data("Client language", wanted.label() + " (/" + localeFromUrl() + ")");
    }

    /** The language the client was last opened in. */
    public Language language() {
        return language;
    }

    /**
     * Chooses a language from the globe's list. The globe toggles, so the click is retried once
     * against a freshly opened list, as NewWeibo_automation's HomePage does.
     */
    protected void selectLanguage(Language wanted) {
        CaseLog.step("Choosing " + wanted.label() + " from the client's language list.");
        for (int attempt = 1; attempt <= 2; attempt++) {
            Locator rows = page.locator(LANGUAGE_ROW);
            if (rows.count() == 0 || !rows.first().isVisible()) {
                page.locator(GLOBE).first().click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(1_200);
            }
            try {
                page.locator(LANGUAGE_ROW).filter(new Locator.FilterOptions().setHasText(wanted.label())).first()
                        .click(new Locator.ClickOptions().setTimeout(10_000));
                page.waitForTimeout(LANGUAGE_SETTLE_MS);
                awaitReady();
                if (wanted.locale().equals(localeFromUrl())) {
                    return;
                }
            } catch (PlaywrightException rowNotClickable) {
                if (attempt == 2) {
                    throw rowNotClickable;
                }
                page.waitForTimeout(1_000);
            }
        }
        CaseLog.evidence("The client stayed on /" + localeFromUrl() + " after " + wanted.label() + " was chosen.");
    }

    /** The first path segment of the current address - the locale in force, e.g. "hk". */
    public String localeFromUrl() {
        String path = page.url().replaceFirst("^https?://[^/]+/", "");
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    // ---------------------------------------------------------------
    // The feed - what the client lists
    // ---------------------------------------------------------------

    /** The categories the client is served, in the order it lists them. Empty when the feed cannot be read. */
    public List<Category> feed() {
        String address = clientUrl + FEED_PATH + "?currency=" + CURRENCY + "&page=1&paginate=200";
        List<Category> out = new ArrayList<>();
        try {
            APIResponse response = page.request().get(address);
            JsonNode root = JSON.readTree(response.text());
            for (JsonNode c : root.path("data")) {
                List<Game> games = new ArrayList<>();
                for (JsonNode g : c.path("games")) {
                    games.add(new Game(names(g.path("game_name")), text(g, "banner_url"), text(g, "images_url"),
                            g.path("isMaintenance").asBoolean(false)));
                }
                out.add(new Category(c.path("id").asInt(), names(c.path("name")), text(c, "icon"), c.path("sort").asInt(), games));
            }
            if (!root.path("status").isMissingNode() && root.path("status").asInt() != 200) {
                CaseLog.evidence("The client's category feed answered " + root.path("status").asInt() + ": " + root.path("message").asText());
            }
        } catch (Exception unreadable) {
            CaseLog.evidence("The client's category feed at " + address + " could not be read: " + unreadable.getMessage());
        }
        return out;
    }

    /** The category the client lists under this name in the language in force, or null. */
    public Category listed(String name) {
        return feed().stream().filter(c -> same(c.nameIn(language.key()), name)).findFirst().orElse(null);
    }

    /** The category the client lists under this name in any language, or null - how one whose name is blank in the language in force is still found. */
    public Category listedInAnyLanguage(String name) {
        return feed().stream().filter(c -> c.isNamed(name)).findFirst().orElse(null);
    }

    /** The names, in the language in force, of every category the client lists. */
    public List<String> listedNames() {
        return feed().stream().map(c -> c.nameIn(language.key())).toList();
    }

    /** Where each of the named categories comes in the client's list, for order comparisons. */
    public List<String> inDocumentOrder(List<String> names) {
        List<String> ordered = new ArrayList<>();
        for (Category c : feed()) {
            String shown = c.nameIn(language.key());
            names.stream().filter(n -> same(n, shown)).findFirst().ifPresent(ordered::add);
        }
        return ordered;
    }

    // ---------------------------------------------------------------
    // A category's page - what the client renders
    // ---------------------------------------------------------------

    /** Opens the category's own page in the language in force. */
    public void open(Category category) {
        String address = clientUrl + "/" + language.locale() + "/" + CATEGORY_ROUTE + category.id();
        CaseLog.step("Opening the client's page for \"" + category.nameIn("en") + "\" at " + address);
        page.navigate(address, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        try {
            page.locator(HEADING).first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (PlaywrightException noHeading) {
            // The page draws "No Games" instead of a heading for an id it has nothing for.
        }
        page.waitForTimeout(1_500);
    }

    /** Opens the page of the category listed under this name in any language; false when the feed has none. */
    public boolean openCategory(String anyLanguageName) {
        Category category = listedInAnyLanguage(anyLanguageName);
        if (category == null) {
            CaseLog.data("\"" + anyLanguageName + "\" on the client", "not in the category feed under any language");
            return false;
        }
        open(category);
        return true;
    }

    /** The category name at the top of the open page, or empty. */
    public String heading() {
        Locator heading = page.locator(HEADING).first();
        return heading.count() == 0 ? "" : heading.innerText().trim();
    }

    /**
     * True when the client lists the category under this name in the language in force and
     * renders its page under that name.
     */
    public boolean isShown(String name) {
        Category category = listed(name);
        if (category == null) {
            CaseLog.data("\"" + name + "\" on the client", "not in the category feed under " + language.label()
                    + "; listed: " + listedNames());
            return false;
        }
        open(category);
        String heading = heading();
        boolean shown = same(heading, name);
        CaseLog.data("\"" + name + "\" on the client", shown ? "listed and rendered" : "listed, but its page is headed \"" + heading + "\"");
        return shown;
    }

    /**
     * True when the category's page draws the icon the client is served for it, loaded and not
     * squashed flat. Measured 2026-10-02: the page draws no icon at all - only the home page's
     * sections under the "new" UI do - so this reports false, naming the icon the feed carries.
     */
    public boolean iconRendersUndistorted(String name) {
        Category category = listed(name);
        if (category == null) {
            return false;
        }
        open(category);
        Locator icon = category.icon().isBlank() ? page.locator("img[src='']") : page.locator("img[src='" + category.icon() + "']").first();
        if (category.icon().isBlank() || icon.count() == 0) {
            CaseLog.evidence("The category's page draws no icon" + (category.icon().isBlank() ? ", and the feed carries none"
                    : "; the feed serves " + category.icon() + " but nothing on the page shows it"));
            return false;
        }
        Object ok = icon.evaluate("i => { if (!i.complete || i.naturalWidth === 0) return false;"
                + " const r = i.getBoundingClientRect(); const natural = i.naturalWidth / i.naturalHeight;"
                + " const shown = r.width / r.height; return r.width > 0 && Math.abs(natural - shown) / natural < 0.1; }");
        return Boolean.TRUE.equals(ok);
    }

    /**
     * The {@code src} the category's page draws for a game - the banner uploaded for this
     * assignment, or the catalogue image when there is none - or empty when the client does not
     * list the game there or does not draw it.
     */
    public String bannerSource(String category, String game) {
        Category listed = listedInAnyLanguage(category);
        if (listed == null) {
            CaseLog.data("\"" + category + "\" on the client", "not in the category feed");
            return "";
        }
        Game listedGame = listed.game(game);
        if (listedGame == null) {
            CaseLog.data(game + " in \"" + category + "\" on the client", "not among the category's games in the feed");
            return "";
        }
        String src = listedGame.shownImage();
        open(listed);
        boolean drawn = !src.isBlank() && hasImage(src);
        CaseLog.data(game + " in \"" + category + "\" on the client", drawn ? "drawn as " + src
                : src.isBlank() ? "served with no banner and no image" : "served " + src + ", but the page does not draw it");
        return drawn ? src : "";
    }

    /** True when the client lists the game under the category and its page draws a card for it. */
    public boolean showsGame(String category, String game) {
        Category listed = listedInAnyLanguage(category);
        Game listedGame = listed == null ? null : listed.game(game);
        if (listedGame == null) {
            CaseLog.data(game + " in \"" + category + "\" on the client", "not listed");
            return false;
        }
        open(listed);
        String src = listedGame.shownImage();
        if (!src.isBlank()) {
            return hasImage(src);
        }
        // Nothing to draw it with: the card is an <img> with no source, if the client draws one at all.
        return (Boolean) page.evaluate("() => [...document.images].some(i => !i.getAttribute('src'))");
    }

    /** Opens an address on the client platform, relative to its origin, and returns where it landed. */
    public String openDirect(String path) {
        page.navigate(clientUrl + "/" + path.replaceFirst("^/", ""),
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        page.waitForTimeout(2_500);
        return page.url();
    }

    /** True when an image with this {@code src} is on the open page. */
    public boolean hasImage(String src) {
        return (Boolean) page.evaluate("src => [...document.images].some(i => i.getAttribute('src') === src || i.src === src)", src);
    }

    public String bodyText() {
        return page.locator("body").innerText();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static Map<String, String> names(JsonNode node) {
        Map<String, String> names = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> names.put(e.getKey(), e.getValue().isNull() ? "" : e.getValue().asText()));
        return names;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private static String fold(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** The same name, give or take surrounding whitespace and letter case. */
    private static boolean same(String a, String b) {
        return !fold(a).isEmpty() && fold(a).equals(fold(b));
    }
}
