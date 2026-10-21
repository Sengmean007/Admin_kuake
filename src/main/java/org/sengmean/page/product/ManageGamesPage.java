package org.sengmean.page.product;

import org.sengmean.page.common.PromptMessage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Manage Games</b> for one category, and the <b>Upload Game Banner</b> dialog it opens.
 *
 * <h2>What the shipped screen shows (measured 2026-09-25)</h2>
 * <pre>
 *   Manage Games                               Home Page / Product / Other Category / test / Manage Games
 *   [Return »]  1 game selected
 *   Game [Search games]   Selection [All games v]   Provider [All providers v]
 *   ⓘ A game may appear in multiple Other Categories. Its banner is uploaded separately for each ...
 *   Selected         | Game  | Provider | Game banner       | Action
 *   [x] Selected     | JILI  | JILI     | ⚠ Banner required | [Upload banner]
 *   [ ] Not selected | ...   | ...      | —                 |
 *   ⚠ 1 selected game still needs a banner                     [Cancel] [Save assignments]
 * </pre>
 *
 * <p>Where the capture this was first written from differed, the screen wins:
 * <ul>
 *   <li><b>Selecting is a checkbox</b> in the Selected column, both ways. There is no "Select game"
 *       button; an unselected row's Action cell is empty.</li>
 *   <li><b>The "All games" select is a Selection filter</b> - All games / Selected / Not selected -
 *       not a filter by game.</li>
 *   <li><b>The banner dialog is a plain {@code .modal-content}</b> with no dialog role, so no dialog
 *       selector found it. It uploads a chosen file to storage straight away and swaps the drop zone
 *       to a preview. Its scope line is "JILI · Banner used only inside this category", and the one
 *       that names both sides is "This upload replaces only the test → JILI banner."</li>
 *   <li>Messages come through the site's Prompt message dialog - see {@link PromptMessage}.</li>
 * </ul>
 *
 * <h2>A banner belongs to an assignment, not to a game</h2>
 * The info line says it and the dialog repeats it. So {@link #bannerSource(String)} is always read
 * on <em>this</em> category's screen, and nothing here caches a banner by game name.
 */
public class ManageGamesPage extends BasePage {

    private static final Pattern SELECTED_COUNT =
            Pattern.compile("(\\d+)\\s+games?\\s+selected", Pattern.CASE_INSENSITIVE);

    private static final Pattern STILL_NEED =
            Pattern.compile("(\\d+)\\s+selected\\s+games?\\s+still\\s+needs?\\s+a\\s+banner", Pattern.CASE_INSENSITIVE);

    /** "Banner required" on the screen; "Banner is required" is the wording Si confirmed (AQ-02). */
    private static final Pattern BANNER_REQUIRED = Pattern.compile("Banner (is )?required", Pattern.CASE_INSENSITIVE);

    /** "This upload replaces only the test → JILI banner." - category, then game. */
    private static final Pattern SCOPE =
            Pattern.compile("replaces only the (.+?)\\s*(?:→|->)\\s*(.+?) banner", Pattern.CASE_INSENSITIVE);

    /** The dialog's fixed wording, which must not be mistaken for an error message. */
    private static final List<Pattern> DIALOG_BOILERPLATE = List.of(
            Pattern.compile("Upload Game Banner", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\S[^·•]*?[·•]\\s*Banner used only inside this category", Pattern.CASE_INSENSITIVE),
            Pattern.compile("This upload replaces only the .*? banner\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Drop file here|Choose a file|Replace image|Delete image", Pattern.CASE_INSENSITIVE),
            // The buttons. No word boundaries: inline buttons render as "CancelSave banner".
            Pattern.compile("Save banner|Cancel", Pattern.CASE_INSENSITIVE));

    /** Reads the whole table in one call: dozens of games, each looked up cell by cell, was minutes. */
    private static final String READ_ROWS = """
            table => {
              const heads = [...table.querySelectorAll('thead th')].map(th => th.innerText.trim().toLowerCase());
              const at = name => heads.findIndex(h => h === name);
              // By header in English; by position (the screen's fixed order) when the admin UI is in 中文.
              const or = (i, d) => i >= 0 ? i : d;
              const sel = or(at('selected'), 0), game = or(at('game'), 1), prov = or(at('provider'), 2), ban = or(at('game banner'), 3), act = or(at('action'), 4);
              // Not every tbody row is a game: the list loads as it scrolls, and a lone
              // <td colspan=5 class="games-sentinel"> row marks where the next batch goes.
              return [...table.querySelectorAll('tbody tr')].filter(tr => tr.querySelectorAll('td').length >= heads.length).map(tr => {
                const td = tr.querySelectorAll('td');
                const text = i => i >= 0 && td[i] ? td[i].innerText.replace(/\\s+/g, ' ').trim() : '';
                const box = sel >= 0 && td[sel] ? td[sel].querySelector('input[type=checkbox]') : null;
                const img = ban >= 0 && td[ban] ? td[ban].querySelector('img') : null;
                return { game: text(game), provider: text(prov),
                         selected: box ? box.checked : /^selected/i.test(text(sel)),
                         banner: text(ban), bannerSrc: img ? (img.getAttribute('src') || '') : '', action: text(act) };
              });
            }""";

    /** One row of the table, as {@link #READ_ROWS} reads it. */
    private record GameRow(String game, String provider, boolean selected, String banner, String bannerSrc, String action) {
    }

    private final Locator table;
    private final Locator bannerDialog;

    /** What the Prompt message dialog said after the last action on this screen, already closed. */
    private String lastPrompt = "";

    public ManageGamesPage(Page page) {
        super(page);
        // The screen has one table; found without its header wording, which the admin UI language translates.
        this.table = page.locator("section.content table").first();
        this.bannerDialog = page.locator(".modal-content")
                .filter(new Locator.FilterOptions().setHas(page.locator(".image-dropzone")))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    public void awaitOpen() {
        page.locator(".games-selected-count").first().waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        table.locator("tbody tr td").first().waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        page.waitForTimeout(800);
    }

    public boolean isOpen() {
        // By address and counter, not the "Manage Games" heading, which 中文 translates.
        return page.url().contains("/product/other-category-games")
                && page.locator(".games-selected-count").count() > 0 && page.locator(".games-selected-count").first().isVisible();
    }

    /** "Home Page / Product / Other Category / test / Manage Games". */
    public String breadcrumb() {
        return OtherCategoryListPage.crumbs(page);
    }

    // ---------------------------------------------------------------
    // Counters and warnings
    // ---------------------------------------------------------------

    /** N in "N games selected". */
    public int selectedCount() {
        Matcher m = SELECTED_COUNT.matcher(page.locator(".games-selected-count").first().innerText());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** N in "⚠ N selected game(s) still need a banner", or 0 when the warning is not shown. */
    public int stillNeedBannerCount() {
        Locator warning = page.locator(".banner-warning").filter(new Locator.FilterOptions().setVisible(true));
        if (warning.count() == 0) {
            return 0;
        }
        Matcher m = STILL_NEED.matcher(warning.first().innerText());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public boolean isBannerWarningShown() {
        return stillNeedBannerCount() > 0;
    }

    // ---------------------------------------------------------------
    // The table
    // ---------------------------------------------------------------

    private List<GameRow> rows() {
        if (table.count() == 0) {
            return List.of();
        }
        Object raw = table.evaluate(READ_ROWS);
        List<GameRow> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> r) {
                    result.add(new GameRow(String.valueOf(r.get("game")), String.valueOf(r.get("provider")),
                            Boolean.TRUE.equals(r.get("selected")), String.valueOf(r.get("banner")),
                            String.valueOf(r.get("bannerSrc")), String.valueOf(r.get("action"))));
                }
            }
        }
        return result;
    }

    private GameRow row(String game) {
        return rows().stream().filter(r -> r.game().equals(game)).findFirst().orElse(null);
    }

    /** The row whose Game cell is exactly {@code game} - not a row whose Provider happens to match. */
    private Locator rowFor(String game) {
        return table.locator("tbody tr").filter(new Locator.FilterOptions()
                .setHas(page.locator("td:nth-child(2)").filter(new Locator.FilterOptions().setHasText(exactly(game, false)))))
                .first();
    }

    /**
     * Scrolls the table's load-more marker ({@code .games-sentinel}) into view until at least
     * {@code minimum} games are loaded, or no more arrive. The list loads 20 at a time as it scrolls:
     * a fresh screen happens to show 40, but after a search is cleared it shows 20 until scrolled.
     *
     * @return how many games are loaded now
     */
    public int loadGamesUntil(int minimum) {
        return loadGamesUntil(minimum, 1200, 3);
    }

    /**
     * As {@link #loadGamesUntil(int)}, with the patience set by the caller: after each scroll, new rows are
     * waited for up to {@code waitPerScrollMs}, and loading counts as finished only after {@code maxStalls}
     * scrolls in a row bring none. Deep in the list each batch arrives more slowly - on 2026-09-29 the
     * default gave up at 483 games with the load-more marker still there - so a case that must reach the
     * end passes a longer wait.
     */
    public int loadGamesUntil(int minimum, long waitPerScrollMs, int maxStalls) {
        int loaded = gameNames().size();
        page.onResponse(gamesApiWatch);
        try {
            for (int stalled = 0; loaded < minimum && stalled < maxStalls; ) {
                Locator sentinel = page.locator(".games-sentinel").first();
                if (sentinel.count() == 0) {
                    break;
                }
                sentinel.scrollIntoViewIfNeeded();
                long deadline = System.currentTimeMillis() + waitPerScrollMs;
                int now = loaded;
                while (System.currentTimeMillis() < deadline) {
                    page.waitForTimeout(400);
                    now = table.locator("tbody tr").count();
                    if (now > loaded + 1) {
                        page.waitForTimeout(400);
                        break;
                    }
                    if (lastGamesPageReached()) {
                        break;   // the API has no more pages; the rows it sent are what there is
                    }
                }
                now = gameNames().size();
                boolean grew = now > loaded;
                stalled = grew ? 0 : stalled + 1;
                loaded = now;
                if (lastGamesPageReached() && !grew) {
                    break;   // the last page is on screen and this scroll brought nothing: the end
                }
            }
        } finally {
            page.offResponse(gamesApiWatch);
        }
        if (gamesTotal >= 0) {
            CaseLog.data("Games API", "page " + gamesCurrentPage + " of " + gamesLastPage + ", " + gamesTotal + " games in all");
        }
        return loaded;
    }

    /** The games list's own API call: {@code GET /api/account/game?paginate=20&page=N}. */
    private static final Pattern GAMES_API = Pattern.compile("/api/account/game\\?");

    /** What the games API said about its pages while the list was scrolled; -1 until a page has answered. */
    private int gamesCurrentPage = -1;
    private int gamesLastPage = -1;
    private int gamesTotal = -1;

    /**
     * Reads each page the games API answers while the list is scrolled. Measured 2026-10-02: the
     * API pages 20 at a time and answers the page after the last one empty, while the screen keeps
     * its load-more marker and asks for another empty page on every scroll - so the marker cannot
     * say whether anything is left; the API's own page count can.
     */
    private final java.util.function.Consumer<com.microsoft.playwright.Response> gamesApiWatch = response -> {
        if (!GAMES_API.matcher(response.url()).find()) {
            return;
        }
        String body;
        try {
            body = response.text();
        } catch (RuntimeException unreadable) {
            return;
        }
        Matcher current = Pattern.compile("\"current_page\"\\s*:\\s*\"?(\\d+)").matcher(body);
        Matcher last = Pattern.compile("\"last_page\"\\s*:\\s*\"?(\\d+)").matcher(body);
        Matcher total = Pattern.compile("\"total\"\\s*:\\s*\"?(\\d+)").matcher(body);
        if (current.find()) {
            gamesCurrentPage = Math.max(gamesCurrentPage, Integer.parseInt(current.group(1)));
        }
        if (last.find()) {
            gamesLastPage = Integer.parseInt(last.group(1));
        }
        if (total.find()) {
            gamesTotal = Integer.parseInt(total.group(1));
        }
    };

    /** True once the games API has answered its last page - every game it has is on screen or arriving. */
    private boolean lastGamesPageReached() {
        return gamesLastPage > 0 && gamesCurrentPage >= gamesLastPage;
    }

    /** How many games the games API counts in all, as it said while the list was scrolled; -1 when it never said. */
    public int gamesTotal() {
        return gamesTotal;
    }

    /** True when the games table itself is on screen - it may be, with no game rows in it. */
    public boolean isTableVisible() {
        return table.count() > 0 && table.isVisible();
    }

    /** The table's column headers, in order, as shown: Selected, Game, Provider, Game banner, Action. */
    public List<String> columnHeaders() {
        if (table.count() == 0) {
            return List.of();
        }
        Object raw = table.locator("thead th")
                .evaluateAll("ths => ths.filter(th => th.offsetParent !== null).map(th => th.innerText.replace(/\\s+/g, ' ').trim())");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /** Every loaded row as "game | provider" - the pair that identifies a game, since names can repeat across providers. */
    public List<String> gameKeys() {
        return rows().stream().map(r -> r.game() + " | " + r.provider()).toList();
    }

    /**
     * True while more games are waiting to be scrolled in. Decided by the games API's page count
     * once {@link #loadGamesUntil} has seen it - the screen keeps its load-more marker after the
     * last page (see {@code gamesApiWatch}) - and by the marker alone before then.
     */
    public boolean hasMoreToLoad() {
        if (lastGamesPageReached()) {
            return false;
        }
        return page.locator(".games-sentinel").count() > 0;
    }

    /**
     * True when the games table is wider than the area it sits in, so the columns run off the side - what a
     * long name that will not wrap, or a narrow window, does to a table whose layout gives way.
     */
    public boolean tableOverflows() {
        if (table.count() == 0) {
            return false;
        }
        Object raw = table.evaluate("t => t.getBoundingClientRect().width > (t.parentElement ? t.parentElement.clientWidth : t.clientWidth) + 1");
        return Boolean.TRUE.equals(raw);
    }

    /** Resizes the browser window's page area; returns the size it had before, as {width, height}. */
    public int[] resizeWindow(int width, int height) {
        CaseLog.step("Resizing the window to " + width + "x" + height + ".");
        com.microsoft.playwright.options.ViewportSize before = page.viewportSize();
        page.setViewportSize(width, height);
        page.waitForTimeout(800);
        return before == null ? new int[]{1920, 1080} : new int[]{before.width, before.height};
    }

    /**
     * Searches, and returns the message of every browser dialog (alert/confirm/prompt) the page raised while it
     * did - which a search for markup such as {@code <script>alert(1)</script>} must never cause.
     */
    public List<String> searchWatchingDialogs(String text) {
        List<String> raised = new java.util.ArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Dialog> watch = dialog -> {
            raised.add(dialog.type() + ": " + dialog.message());
            dialog.dismiss();
        };
        page.onDialog(watch);
        try {
            search(text);
            page.waitForTimeout(800);
        } finally {
            page.offDialog(watch);
        }
        return raised;
    }

    /**
     * Every visible header whose label does not sit on one unbroken line, with how it breaks - "Game
     * banner: 2 lines" or "Action: cut off". Empty when every header reads whole on a single line. Lines
     * are counted from the rendered text's own boxes, so a label that wraps inside a narrow column
     * (typical when the body is empty and the columns collapse) is caught even though innerText has no
     * line break in it.
     */
    public List<String> brokenHeaders() {
        if (table.count() == 0) {
            return List.of();
        }
        Object raw = table.locator("thead th").evaluateAll("""
                ths => ths.filter(th => th.offsetParent !== null).map(th => {
                  const label = th.innerText.replace(/\\s+/g, ' ').trim();
                  const range = document.createRange();
                  range.selectNodeContents(th);
                  const tops = new Set([...range.getClientRects()].filter(r => r.width > 0 && r.height > 0).map(r => Math.round(r.top)));
                  const cut = th.scrollWidth > th.clientWidth + 1;
                  return tops.size > 1 ? label + ': ' + tops.size + ' lines' : cut ? label + ': cut off' : '';
                }).filter(s => s)""");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /**
     * The text of any body row that is not a game - the "no data" line an empty list may show -
     * or empty when there is none. The scroll list's load-more sentinel is not counted.
     */
    public String emptyRowText() {
        if (table.count() == 0) {
            return "";
        }
        Object raw = table.evaluate("""
                table => {
                  const heads = table.querySelectorAll('thead th').length;
                  return [...table.querySelectorAll('tbody tr')]
                    .filter(tr => tr.querySelectorAll('td').length < heads && !tr.querySelector('.games-sentinel'))
                    .map(tr => tr.innerText.replace(/\\s+/g, ' ').trim()).filter(t => t).join(' | ');
                }""");
        return raw == null ? "" : String.valueOf(raw);
    }

    /** Every game on screen, in order - the Game column. */
    public List<String> gameNames() {
        return rows().stream().map(GameRow::game).toList();
    }

    /** The Provider column for a game. */
    public String providerOf(String game) {
        GameRow r = row(game);
        return r == null ? "" : r.provider();
    }

    public boolean isListed(String game) {
        return row(game) != null;
    }

    /** True when the row's Selected checkbox is ticked. */
    public boolean isSelected(String game) {
        GameRow r = row(game);
        return r != null && r.selected();
    }

    private void setSelected(String game, boolean selected) {
        PromptMessage.clearStray(page);
        rowFor(game).locator("td").first().locator("input[type='checkbox']").setChecked(selected);
        page.waitForTimeout(400);
    }

    /** Ticks an unselected row's checkbox. */
    public void select(String game) {
        if (isSelected(game)) {
            return;
        }
        CaseLog.step("Ticking \"" + game + "\" to select it.");
        setSelected(game, true);
    }

    /** Unticks a selected row's checkbox. */
    public void deselect(String game) {
        if (!isSelected(game)) {
            return;
        }
        CaseLog.step("Unticking \"" + game + "\" to deselect it.");
        setSelected(game, false);
    }

    /** Selects an unselected game or deselects a selected one. */
    public void toggle(String game) {
        if (isSelected(game)) {
            deselect(game);
        } else {
            select(game);
        }
    }

    /** The first listed game in the wanted state, or null. */
    public String firstGame(boolean selected) {
        return rows().stream().filter(r -> r.selected() == selected && !r.game().isBlank())
                .map(GameRow::game).findFirst().orElse(null);
    }

    public List<String> selectedGames() {
        return rows().stream().filter(GameRow::selected).map(GameRow::game).toList();
    }

    /** True when the Game banner column reads "⚠ Banner required". */
    public boolean showsBannerRequired(String game) {
        GameRow r = row(game);
        return r != null && BANNER_REQUIRED.matcher(r.banner()).find();
    }

    /** True when the assignment has a banner on file: an image in its Game banner cell, or Replace banner offered. */
    public boolean hasBanner(String game) {
        GameRow r = row(game);
        return r != null && (!r.bannerSrc().isBlank() || r.action().toLowerCase().contains("replace"));
    }

    /** The {@code src} of this assignment's banner in the Game banner column, or empty. */
    public String bannerSource(String game) {
        GameRow r = row(game);
        return r == null ? "" : r.bannerSrc();
    }

    private Locator action(String game, String label) {
        return rowFor(game).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(exactly(label, true))).first();
    }

    // ---------------------------------------------------------------
    // Search and filters
    // ---------------------------------------------------------------

    public void search(String text) {
        CaseLog.step("Searching games for \"" + text + "\".");
        Locator box = page.getByPlaceholder("Search games");
        box.fill(text);
        box.press("Enter");
        page.waitForTimeout(1200);
    }

    /** The Selection filter: "All games", "Selected" or "Not selected". */
    public void filterSelection(String option) {
        CaseLog.step("Setting the Selection filter to " + option + ".");
        selectByText(filterWithOption("All games"), option);
    }

    public void filterProvider(String provider) {
        CaseLog.step("Setting the provider filter to " + provider + ".");
        selectByText(filterWithOption("All providers"), provider);
    }

    public List<String> providerOptions() {
        Object raw = filterWithOption("All providers").locator("option").evaluateAll("os => os.map(o => o.textContent.trim())");
        return raw instanceof List<?> list
                ? list.stream().map(String::valueOf).filter(o -> !o.equals("All providers")).toList()
                : List.of();
    }

    private Locator filterWithOption(String optionText) {
        return page.locator("#divSearchCriteria select").filter(new Locator.FilterOptions()
                .setHas(page.locator("option", new Page.LocatorOptions().setHasText(optionText)))).first();
    }

    /** By trimmed option text: the options are padded, and Playwright's label match is exact. */
    private void selectByText(Locator select, String text) {
        Object value = select.evaluate("(s, t) => [...s.options].find(o => o.textContent.trim() === t)?.value ?? null", text);
        if (value == null) {
            throw new IllegalArgumentException("No option \"" + text + "\" in that filter");
        }
        select.selectOption(String.valueOf(value));
        page.waitForTimeout(1500);
    }

    // ---------------------------------------------------------------
    // Save / Cancel
    // ---------------------------------------------------------------

    /** Save assignments, then reads and closes whatever Prompt message it raised. */
    public void save() {
        CaseLog.step("Clicking Save assignments.");
        PromptMessage.clearStray(page);
        page.locator(".save-bar button").filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile("^\\s*Save(\\s+assignments)?\\s*$", Pattern.CASE_INSENSITIVE))).first().click();
        lastPrompt = PromptMessage.confirmAndRead(page, 2500);
        page.waitForTimeout(1200);
    }

    /**
     * Double-clicks Save assignments, confirms every prompt that follows - one per submission, if the page
     * lets a second one through - and returns how many save requests reached the API (the {@code PUT}/{@code
     * POST} to {@code game-category-features/{id}/games}). A page that guards against a double submit sends one.
     */
    public int doubleClickSaveCountingRequests() {
        CaseLog.step("Double-clicking Save assignments.");
        PromptMessage.clearStray(page);
        int[] sent = {0};
        java.util.function.Consumer<com.microsoft.playwright.Request> counter = request -> {
            if (request.url().matches(".*/game-category-features/[^/?]+/games.*")
                    && ("PUT".equals(request.method()) || "POST".equals(request.method()))) {
                sent[0]++;
                CaseLog.data("Save request " + sent[0], request.method() + " " + request.url());
            }
        };
        page.onRequest(counter);
        try {
            page.locator(".save-bar button").filter(new Locator.FilterOptions()
                    .setHasText(Pattern.compile("^\\s*Save(\\s+assignments)?\\s*$", Pattern.CASE_INSENSITIVE))).first().dblclick();
            StringBuilder said = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                String one = PromptMessage.confirmAndRead(page, 2500);
                if (one.isEmpty()) {
                    break;
                }
                said.append(said.length() == 0 ? "" : " | ").append(one);
            }
            lastPrompt = said.toString();
            page.waitForTimeout(2500);
        } finally {
            page.offRequest(counter);
        }
        CaseLog.data("Save requests sent", String.valueOf(sent[0]));
        return sent[0];
    }

    /** The save bar's Cancel, which leaves for the category list without saving. */
    public void cancel() {
        CaseLog.step("Clicking Cancel on Manage Games.");
        if (isBannerDialogOpen()) {
            cancelBanner();
        }
        page.locator(".save-bar a, .save-bar button").filter(new Locator.FilterOptions()
                .setHasText(exactly("Cancel", true))).first().click();
        page.waitForTimeout(1000);
    }

    /** Every message after an action: what the Prompt message dialog said, plus inline errors. */
    public String messages() {
        StringBuilder all = new StringBuilder(lastPrompt);
        String open = PromptMessage.readAndClose(page, 0);
        if (!open.isEmpty()) {
            all.append(all.length() == 0 ? "" : " | ").append(open);
        }
        Locator found = page.locator(".invalid-feedback, .text-danger, .el-message, [role='alert'], " + ALERT_BOARD)
                .filter(new Locator.FilterOptions().setVisible(true));
        for (int i = 0; i < found.count(); i++) {
            String text = found.nth(i).innerText().replaceAll("\\s+", " ").trim();
            if (!text.isEmpty()) {
                all.append(all.length() == 0 ? "" : " | ").append(text);
            }
        }
        return all.toString();
    }

    // ---------------------------------------------------------------
    // Upload Game Banner
    // ---------------------------------------------------------------

    /** Opens the dialog from a selected game's Upload banner or Replace banner. */
    public void openBannerDialog(String game) {
        CaseLog.step("Opening Upload Game Banner for \"" + game + "\".");
        PromptMessage.clearStray(page);
        lastPrompt = "";
        Locator upload = action(game, "Upload banner");
        (upload.count() > 0 ? upload : action(game, "Replace banner")).click();
        bannerDialog.first().waitFor();
    }

    public boolean isBannerDialogOpen() {
        return bannerDialog.count() > 0;
    }

    /** Picks a file through "Choose a file", and waits for the upload's preview or refusal. */
    public void chooseBannerFile(Path file) {
        CaseLog.step("Choosing " + file.getFileName() + " in Upload Game Banner.");
        String before = bannerDialogPreview();
        bannerDialog.first().locator("input[type='file']").setInputFiles(file);
        awaitUpload(before);
        if (PromptMessage.isTransientUploadFailure(lastPrompt)) {
            // The storage upload failed on the server, which says nothing about the file: one retry.
            CaseLog.evidence("The banner upload failed on the server (\"" + lastPrompt + "\"); retrying once.");
            bannerDialog.first().locator("input[type='file']").setInputFiles(file);
            awaitUpload(before);
        }
    }

    /**
     * Drops a file on the dialog's drop zone, the way dragging it from the desktop would: a
     * {@code DataTransfer} holding the file, dispatched as dragenter, dragover and drop on the zone.
     */
    public void dropBannerFile(Path file) {
        CaseLog.step("Dragging " + file.getFileName() + " onto the banner drop zone.");
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".webp") ? "image/webp" : name.endsWith(".png") ? "image/png" : "image/jpeg";
        String before = bannerDialogPreview();
        bannerDialog.first().locator(".image-dropzone").first().evaluate("(zone, f) => {"
                + " const bytes = Uint8Array.from(atob(f.data), c => c.charCodeAt(0));"
                + " const dt = new DataTransfer(); dt.items.add(new File([bytes], f.name, {type: f.type}));"
                + " for (const t of ['dragenter', 'dragover', 'drop'])"
                + "   zone.dispatchEvent(new DragEvent(t, {bubbles: true, cancelable: true, dataTransfer: dt}));"
                + "}", Map.of("data", base64, "name", name, "type", type));
        awaitUpload(before);
    }

    /** The dialog uploads to storage at once: wait for its preview to change, or for a refusal. */
    private void awaitUpload(String previewBefore) {
        lastPrompt = "";
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            lastPrompt = PromptMessage.readAndClose(page, 0);
            String now = bannerDialogPreview();
            if (!lastPrompt.isEmpty() || (!now.isBlank() && !now.equals(previewBefore))) {
                break;
            }
            page.waitForTimeout(300);
        }
        page.waitForTimeout(300);
    }

    public void saveBanner() {
        CaseLog.step("Clicking Save banner.");
        bannerDialog.first().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Save banner").setExact(true)).click();
        lastPrompt = PromptMessage.confirmAndRead(page, 1500);
        page.waitForTimeout(500);
    }

    public void cancelBanner() {
        CaseLog.step("Clicking Cancel in Upload Game Banner.");
        bannerDialog.first().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Cancel").setExact(true)).click();
        page.waitForTimeout(800);
    }

    /** Everything the dialog says, boilerplate included, or empty when it is closed. */
    public String bannerDialogText() {
        return isBannerDialogOpen() ? bannerDialog.first().innerText().replaceAll("\\s+", " ").trim() : "";
    }

    /**
     * What the dialog says <em>beyond</em> its fixed wording, plus what the Prompt message said
     * after the last choose or save - a format, ratio or size refusal.
     *
     * <p>The fixed wording is stripped because a check that the dialog "mentions WEBP" would
     * otherwise pass on an untouched dialog and prove nothing about a rejection.
     */
    public String bannerDialogError() {
        String text = bannerDialogText();
        for (Pattern fixed : DIALOG_BOILERPLATE) {
            text = fixed.matcher(text).replaceAll(" ");
        }
        text = text.replaceAll("\\s+", " ").trim();
        String prompt = lastPrompt.isEmpty() ? PromptMessage.readAndClose(page, 0) : lastPrompt;
        return (text + " " + prompt).trim();
    }

    /** "JILI -> test": the game and the category the dialog says this banner is for. */
    public String bannerDialogScope() {
        Matcher m = SCOPE.matcher(bannerDialogText());
        return m.find() ? m.group(2).trim() + " -> " + m.group(1).trim() : "";
    }

    /** The dialog's preview {@code src}, once a file is chosen. */
    public String bannerDialogPreview() {
        if (!isBannerDialogOpen()) {
            return "";
        }
        Locator image = bannerDialog.first().locator("img");
        return image.count() > 0 ? String.valueOf(image.first().getAttribute("src")) : "";
    }

    /** Selects the game, uploads a banner for it and saves the banner - the whole happy path. */
    public void selectWithBanner(String game, Path banner) {
        select(game);
        openBannerDialog(game);
        chooseBannerFile(banner);
        saveBanner();
    }
}
