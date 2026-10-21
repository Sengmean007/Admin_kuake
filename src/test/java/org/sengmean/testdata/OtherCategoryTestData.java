package org.sengmean.testdata;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The fixtures and literal values the Other Category workbook's cases call for.
 *
 * <h2>Where they come from</h2>
 * The <b>TEST DATA</b> column of the "Other Category" tab in
 * {@code src/test/resources/testdata/client_testcase.xlsx}. Where a value is changed, the reason
 * is given beside it - there is one reason, and it applies to every category name.
 *
 * <h2>Every category the suite creates is prefixed {@value #PREFIX}</h2>
 * The workbook's own examples - "Popular Games", "Live Casino", "New Releases" - are the names of
 * categories the client expects to <em>already exist</em>. A suite that created a second
 * "Popular Games" would collide with the real one, and one that deleted "New Releases" would
 * remove the client's data. So the names the suite writes carry the prefix, and the prefix is also
 * what {@code OtherCategoryTest} sweeps at the start and end of a run to remove its own leftovers
 * - and only its own.
 */
public final class OtherCategoryTestData {

    private OtherCategoryTestData() {
    }

    /** Marks a category as the suite's own. Never used by the client's data. */
    public static final String PREFIX = "QA OC ";

    // -----------------------------------------------------------------
    // Languages
    // -----------------------------------------------------------------

    /**
     * The language tab the workbook uses as the base language, and the admin list's default filter.
     * All twelve labels are as the Create capture spells them ({@code docs/mockups/other-category}).
     */
    public static final String TAB_CHINESE = "繁體";
    public static final String TAB_SIMPLIFIED = "简体";
    public static final String TAB_ENGLISH = "English";
    public static final String TAB_JAPANESE = "日語";
    public static final String TAB_THAI = "ภาษาไทย";
    public static final String TAB_VIETNAM = "Vietnam";
    public static final String TAB_SPAIN = "Spain";
    public static final String TAB_MALAYSIA = "Malaysia";
    public static final String TAB_INDONESIA = "Indonesia";
    public static final String TAB_FILIPINO = "Filipino";
    public static final String TAB_BENGALI = "Bengali";
    public static final String TAB_PORTUGUESE = "Portuguese";

    /** The twelve tabs in the capture's order - what {@code OC-I18N-001} expects to find. */
    public static final List<String> LANGUAGE_TABS = List.of(
            TAB_CHINESE, TAB_SIMPLIFIED, TAB_ENGLISH, TAB_JAPANESE, TAB_THAI, TAB_VIETNAM,
            TAB_SPAIN, TAB_MALAYSIA, TAB_INDONESIA, TAB_FILIPINO, TAB_BENGALI, TAB_PORTUGUESE);

    /** How many language tabs the Create form must offer ({@code OC-I18N-001}). */
    public static final int LANGUAGE_TAB_COUNT = 12;

    /**
     * The mandatory names: Save is blocked while any is empty. AQ-01 (2026-09-24) listed these
     * three; Si briefly made 简体 optional on 2026-09-25 to match the back office then, and on
     * 2026-09-28 - the page now refusing a blank 简体 - confirmed 简体 is mandatory again.
     */
    public static final List<String> REQUIRED_NAME_TABS = List.of(TAB_ENGLISH, TAB_CHINESE, TAB_SIMPLIFIED);

    /** Optional names {@code OC-CRT-002} leaves blank to show Save still goes through. */
    public static final List<String> OPTIONAL_NAME_TABS_CHECKED = List.of(TAB_BENGALI);

    /**
     * The member-site path segment for a language, for the client-facing rows. The member site
     * addresses languages as the first path segment - {@code /en}, {@code /zh-TW} - which is how
     * NewWeibo_automation reaches them.
     */
    public static final Map<String, String> CLIENT_LANGUAGE = Map.of(
            TAB_ENGLISH, "en",
            TAB_CHINESE, "zh-TW",
            TAB_VIETNAM, "vi");

    // -----------------------------------------------------------------
    // The suite's own categories
    // -----------------------------------------------------------------

    /**
     * The workbook's "Popular Games" / "熱門遊戲" ({@code OC-CRT-001}), and "Category A" in every row
     * that needs two categories sharing a game. Created by Create, never deleted until the sweep.
     */
    public static final String CATEGORY_A_EN = PREFIX + "Popular Games";
    public static final String CATEGORY_A_ZH = PREFIX + "熱門遊戲";
    public static final String CATEGORY_A_ES = PREFIX + "Juegos populares";

    /** "Category B": a second category holding the same game with a different banner. */
    public static final String CATEGORY_B_EN = PREFIX + "Live Casino";
    public static final String CATEGORY_B_ZH = PREFIX + "真人娛樂";

    /** A category that exists to be deleted by the delete rows, each of which destroys it. */
    public static final String CATEGORY_DISPOSABLE = PREFIX + "Delete Me";

    /** The workbook's "New Releases, 0 games" for {@code OC-DEL-006} and {@code OC-CLI-004}. */
    public static final String CATEGORY_EMPTY = PREFIX + "New Releases";

    /** An Inactive category, the "Inactive test category" of the client-facing rows. */
    public static final String CATEGORY_INACTIVE = PREFIX + "Hidden";

    // -----------------------------------------------------------------
    // Seed data - kept between runs
    // -----------------------------------------------------------------

    /**
     * The prefix of the categories {@code OtherCategorySeedData} creates and keeps. Deliberately NOT
     * {@link #PREFIX}: the suite's start-and-end sweep removes everything under that one, and the
     * point of the seed is to still be there next run, so the data-heavy rows need not build it.
     */
    public static final String SEED_PREFIX = "QA SEED ";

    /** How many seed categories there are - one more page than the list's smallest page size needs. */
    public static final int SEED_COUNT = 20;

    /** Seed n has Display Order 200 + n: distinct, and far above where {@code freeOrder()} looks (90 up). */
    public static final int SEED_ORDER_BASE = 200;

    /** "QA SEED 01" .. "QA SEED 20". */
    public static String seedName(int n) {
        return SEED_PREFIX + String.format("%02d", n);
    }

    /** Written on one tab and one tab only, for the rows about names staying on their own tab. */
    public static final String RENAMED_SUFFIX = " (edited)";

    /**
     * The name a category gets on a tab the workbook does not give a value for: the English name
     * and the tab, so a name shown under the wrong language is recognisable at a glance.
     */
    public static String nameOnTab(String englishName, String tab) {
        if (TAB_ENGLISH.equals(tab)) {
            return englishName;
        }
        if (TAB_CHINESE.equals(tab) && CATEGORY_A_EN.equals(englishName)) {
            return CATEGORY_A_ZH;
        }
        if (TAB_CHINESE.equals(tab) && CATEGORY_B_EN.equals(englishName)) {
            return CATEGORY_B_ZH;
        }
        if (TAB_SPAIN.equals(tab) && CATEGORY_A_EN.equals(englishName)) {
            return CATEGORY_A_ES;
        }
        return englishName + " [" + tab + "]";
    }

    // -----------------------------------------------------------------
    // Display order
    // -----------------------------------------------------------------

    /** {@code OC-CRT-001}: "order = 05". */
    public static final String ORDER_CATEGORY_A = "05";

    /** {@code OC-EDT-005}: "Change Order from 04 to 01". */
    public static final String ORDER_BEFORE = "04";
    public static final String ORDER_AFTER = "01";

    /** {@code OC-CRT-009}: "Display order = abc". */
    public static final String ORDER_NOT_A_NUMBER = "abc";

    /**
     * Where the suite starts looking for a free order for categories whose position does not
     * matter. Orders are unique (AQ-05), so {@code OtherCategoryTest.freeOrder} takes the first
     * unused value from here up rather than this value itself.
     */
    public static final String ORDER_OUT_OF_THE_WAY = "90";

    // -----------------------------------------------------------------
    // Searches and filters
    // -----------------------------------------------------------------

    /** {@code OC-LST-002}: search text matching "Popular". */
    public static final String LIST_SEARCH = "Popular";

    /** {@code OC-GAM-003}: search text matching "Horse". */
    public static final String GAME_SEARCH = "Horse";

    /** {@code OC-GAM-901}: a game name no provider uses, so the filtered list can only be empty. */
    public static final String GAME_SEARCH_NO_MATCH = "QA No Such Game 9Z7";

    /** {@code OC-GAM-011}: Si's search text for a no-match result on the unfiltered games list. */
    public static final String GAME_SEARCH_SSS = "sss";

    /** {@code OC-GAM-014}: {@link #GAME_SEARCH} in other cases, which must find the same games. */
    public static final List<String> GAME_SEARCH_OTHER_CASES = List.of("horse", "HORSE");

    /** {@code OC-GAM-016}: {@link #GAME_SEARCH} padded with spaces, and a search of spaces only. */
    public static final String GAME_SEARCH_PADDED = "  Horse  ";
    public static final String GAME_SEARCH_BLANK = "   ";

    /** {@code OC-GAM-017}: searches no game name contains, each a classic way to break a search. */
    public static final List<String> GAME_SEARCH_SPECIAL = List.of("%", "'", "<script>alert(1)</script>", "😀");

    /** {@code OC-GAM-021}: how long each scroll waits for its batch of games, and how many empty scrolls end loading. */
    public static final long SCROLL_WAIT_MS = 8_000;
    public static final int SCROLL_MAX_STALLS = 5;

    /** {@code OC-GAM-023}: narrower windows the Manage Games header must still fit on one line in. */
    public static final List<int[]> NARROW_WINDOWS = List.of(new int[]{1280, 800}, new int[]{1024, 768});

    /** The Manage Games table's headers, in order (measured 2026-09-25) - kept when a filter finds nothing. */
    public static final List<String> MANAGE_GAMES_HEADERS = List.of("Selected", "Game", "Provider", "Game banner", "Action");

    /** {@code OC-GAM-005}: filter = "Weibo Lottery", which should show e.g. "Lucky Pick 3". */
    public static final String PROVIDER_FILTER = "Weibo Lottery";

    // -----------------------------------------------------------------
    // Games
    // -----------------------------------------------------------------

    /**
     * The games the workbook names, in the order a row should try them. A row that needs "a game"
     * takes the first one the screen lists and falls back to whatever it offers, recording which.
     */
    public static final String GAME_HORSE_RACING = "Horse Racing";
    public static final String GAME_LUCKY_PICK = "Lucky Pick 3";
    public static final String GAME_LIVE_SPORTS = "Live Sports";
    public static final String GAME_BOXING = "Boxing Yes / No";

    public static final List<String> PREFERRED_GAMES =
            List.of(GAME_HORSE_RACING, GAME_LUCKY_PICK, GAME_LIVE_SPORTS, GAME_BOXING);

    // -----------------------------------------------------------------
    // Files
    // -----------------------------------------------------------------

    /**
     * {@code OC-BAN-009}'s large 4:3 banner, well over the site's 5 MB upload limit. Si ruled on
     * 2026-09-25 that the limit is right, superseding AQ-06's "no size limit".
     */
    public static final int LARGE_BANNER_BYTES = 15 * 1024 * 1024;

    /** A 4:3 banner comfortably under the 5 MB limit, however "5MB" is counted - so it must be taken. */
    public static final int BANNER_UNDER_LIMIT_BYTES = (int) (4.5 * 1024 * 1024);

    // -----------------------------------------------------------------
    // The API's messages - Si, 2026-09-25: the expected message is the API's own wording
    // -----------------------------------------------------------------

    // Si, 2026-09-28: Create and Edit refusals are checked against the API's own response to that
    // save, read at run time (OtherCategoryTest.assertMessageMatchesApi), so no copy of the API's
    // wording is kept here for them. The two below are what the PAGE says when it refuses before
    // calling the API at all, which leaves no response to compare with.

    /** The page's own refusal of a blank mandatory name, followed by the tab's name: "...for: English". */
    public static final String MSG_NAME_REQUIRED_FOR = "Category name is required for: ";

    /** An icon or banner file that is not an image. */
    public static final String MSG_ICON_EXTENSIONS = "Image extensions are only allowed jpg、jpeg、png、gif、svg、webp";

    /** An image over the upload limit. */
    public static final String MSG_BANNER_TOO_LARGE = "Image upload failed, the size exceeds 5MB";

    // -----------------------------------------------------------------
    // Upload images - Si's own pictures
    // -----------------------------------------------------------------
    //
    // Prepared 2026-09-25 from Si's local Pictures folder (iCon\ and the loose files beside it) and
    // kept under src/test/resources/testdata/images/other-category, so a run does not depend on one
    // machine's OneDrive. Several originals carried the wrong extension - tick.png is an AVIF,
    // plan.png a JPEG, oip.png and phone.png WEBPs - and an upload control may judge by either the
    // name or the bytes, so each copy is named for what it really is. The banners were centre-cropped
    // to an exact 4:3, because AQ-04 blocks anything else; the uncropped originals are kept as
    // off-ratio files. The formats neither folder had (SVG, GIF, PDF, a PNG banner, 9:16, 15 MB)
    // are still generated by ImageFixtures.

    /** A real PNG, 512x512 ({@code avarta.png}). */
    public static final Path ICON_PNG = image("icon/avatar.png");

    /** A JPEG, 310x382 ({@code images.jpg}). */
    public static final Path ICON_JPG = image("icon/images.jpg");

    /** A second, different icon for the replace rows - a JPEG, 360x360 ({@code plan.png} in the folder). */
    public static final Path ICON_REPLACEMENT = image("icon/plan.jpg");

    /** An AVIF, 360x360 ({@code tick.png} in the folder) - a format the icon upload does not list. Not used by a row yet. */
    public static final Path ICON_AVIF = image("icon/tick.avif");

    /** A WEBP banner, 472x354 - {@code oip.png} cropped to 4:3. */
    public static final Path BANNER_WEBP = image("banner/oip-4x3.webp");

    /** A second WEBP banner for the replace rows, 744x558 - {@code phone.png} cropped to 4:3. */
    public static final Path BANNER_WEBP_2 = image("banner/phone-4x3.webp");

    /** A JPG banner, 736x552 - {@code dog.jpg} cropped to 4:3. */
    public static final Path BANNER_JPG = image("banner/dog-4x3.jpg");

    /**
     * A generated SVG banner, 800x600 (4:3) - {@code OC-BAN-901}. Generated 2026-09-28 rather than
     * taken from Si's pictures, which hold no SVG; it is sized as a banner, unlike the 128x128
     * {@code ImageFixtures} icon SVG that {@code OC-BAN-003} only previews.
     */
    public static final Path BANNER_SVG = image("banner/generated-4x3.svg");

    /**
     * A generated WEBP banner, 800x600 (4:3), encoded by Chromium's canvas - {@code OC-BAN-902}.
     * Committed rather than encoded per run, so the row does not depend on the browser's encoder.
     */
    public static final Path BANNER_WEBP_GENERATED = image("banner/generated-4x3.webp");

    /** {@code dog.jpg} as it is, 736x1104 (2:3) - an off-ratio JPG. Not used by a row yet. */
    public static final Path BANNER_OFF_RATIO_JPG = image("banner/dog-2x3.jpg");

    /** {@code phone.png} as it is, 750x1000 (3:4) - an off-ratio WEBP. Not used by a row yet. */
    public static final Path BANNER_OFF_RATIO_WEBP = image("banner/phone-3x4.webp");

    /** A file under {@code testdata/images/other-category/} on the test classpath, as a path Playwright can upload. */
    private static Path image(String relative) {
        String resource = "testdata/images/other-category/" + relative;
        URL url = OtherCategoryTestData.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Missing upload image " + resource + " under src/test/resources");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve " + url, e);
        }
    }
}
