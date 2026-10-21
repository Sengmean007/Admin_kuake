package org.sengmean.page.product;

import org.sengmean.page.common.PromptMessage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import org.sengmean.base.BasePage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Product &gt; Other Category</b>: the category list, its filters, its pager and the
 * <b>Delete Other Category?</b> confirmation.
 *
 * <h2>Built from the mockup, before the screen exists</h2>
 * Modelled on the client's captures - {@code docs/mockups/other-category/} - because on 2026-09-23
 * the back office had no Product menu yet. What the capture shows, top to bottom:
 *
 * <pre>
 *   Other Category                                               [Add category]
 *   Manage multilingual categories, category icons, and game-specific banners.
 *   Product / Other Category
 *   [Search category] [All statuses v] [繁體 v] [50 per page v]
 *   No. | Icon | Category name        | Games   | Status     | Order | Actions
 *   1   | 🔥   | Popular Games        | 8 games | ● Active   | 01    | [Edit] [Manage games] [Delete]
 *       |      | 12 translations      |         |            |       |
 *   Showing 1-4 of 4 categories                           [Previous] [1] [Next]
 * </pre>
 *
 * <p>Three things in that picture shape the code below:
 * <ul>
 *   <li><b>The name cell has two lines</b> - the name, then "12 translations". A lookup that
 *       compared the whole cell with a name would never match, so the name is the cell's first
 *       line and the translation count is read separately.</li>
 *   <li><b>The status carries a bullet</b> - "● Active", "○ Inactive" - which is stripped.</li>
 *   <li><b>The list's language filter defaults to 繁體</b>, so the Category name column shows the
 *       Traditional Chinese name until the filter is changed. Callers that look categories up by
 *       their English name must switch the filter first.</li>
 * </ul>
 *
 * <p>Locators are text- and role-based on the capture's wording. Markup is unknown until the
 * screen ships; a locator that misses it then is an adjustment, not a defect.
 *
 * <h2>Measured against the shipped screen, 2026-09-25</h2>
 * The real list differs from the capture in ways that each broke a whole run:
 * <ul>
 *   <li><b>Two selects offer "English".</b> The admin header's site-language select
 *       ({@code #lang}, labels padded with {@code &nbsp;}) comes first in the page, so a filter found
 *       by "has an English option" was the header's and every run died in the opening sweep. The
 *       list's own filter is the one inside {@code .lang-selector-wrapper}.</li>
 *   <li><b>The status filter reads Select / Enable / Disable</b>, and filters on <b>Search</b>, not on
 *       change. {@link #filterStatus} still takes the capture's words and translates them.</li>
 *   <li><b>The per-page selector is "Show [50] Entry"</b> - bare numbers, no "per page".</li>
 *   <li><b>The Status column is an inline select</b>, so its text is both options; the selected one
 *       is read instead.</li>
 *   <li><b>The row action is Modify</b>, not Edit; the pager is DataTables' First / Previous / Next /
 *       Last anchors with no {@code href} (so no link role); and in place of "Showing X-Y of Z" the
 *       footer reads "Common24 Unit， Show page 2 Page， Common 3 Page".</li>
 *   <li><b>Delete confirms in the site's jQuery UI "Prompt message" dialog</b>, with Confirm and
 *       Cancel - the same dialog every validation message on this screen uses.</li>
 * </ul>
 */
public class OtherCategoryListPage extends BasePage {

    /** The capture's heading. */
    public static final String HEADING = "Other Category";

    /** The footer "Common24 Unit， Show page 2 Page， Common 3 Page": the total, then the page shown. */
    private static final Pattern PAGE_INFO =
            Pattern.compile("Common\\s*(\\d+)\\s*Unit.*?Show page\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** The capture's status-filter words, as the shipped select spells them. */
    private static final Map<String, String> STATUS_FILTER = Map.of(
            "All statuses", "Select", "Active", "Active", "Inactive", "Inactive");

    /** "8 games", "0 games". */
    private static final Pattern GAMES = Pattern.compile("(\\d+)\\s*games?", Pattern.CASE_INSENSITIVE);

    /** "12 translations", the name cell's second line. */
    private static final Pattern TRANSLATIONS = Pattern.compile("(\\d+)\\s*translations?", Pattern.CASE_INSENSITIVE);

    /** "8 game assignments will be removed." */
    private static final Pattern ASSIGNMENTS =
            Pattern.compile("(\\d+)\\s+game\\s+assignments?", Pattern.CASE_INSENSITIVE);

    private final Locator rows;
    private final Locator headerCells;
    private final Locator searchBox;
    private final Locator deleteDialog;

    public OtherCategoryListPage(Page page) {
        super(page);
        this.rows = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHas(page.locator("td")));
        this.headerCells = page.locator("table thead th");
        this.searchBox = page.getByPlaceholder("Search category");
        // By its summary block, not its wording, which the admin UI language translates.
        this.deleteDialog = page.locator(".ui-dialog")
                .filter(new Locator.FilterOptions().setHas(page.locator(".delete-category-summary")))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    // ---------------------------------------------------------------
    // Getting there
    // ---------------------------------------------------------------

    /**
     * Opens the list: through the side navigation's Product &gt; Other Category entry when it is
     * there, by {@code test.otherCategory.path} otherwise. The menu is preferred because the
     * address is the one part of this screen the capture does not show.
     */
    public void open() {
        CaseLog.step("Opening Product > Other Category.");
        Locator menu = page.locator("a[href$='" + AdminShellPage.OTHER_CATEGORY_HREF + "']")
                .or(page.locator("a").filter(new Locator.FilterOptions()
                        .setHasText(Pattern.compile("^\\s*Other Category\\s*$", Pattern.CASE_INSENSITIVE))))
                .first();
        String href = menu.count() > 0 ? menu.getAttribute("href") : null;
        if (href != null && !href.isBlank() && !href.startsWith("javascript") && !href.equals("#")) {
            page.navigate(href.startsWith("http") ? href : url(href.replaceFirst("^/", "")));
        } else {
            open(AdminShellPage.OTHER_CATEGORY_HREF.replaceFirst("^/", ""));
        }
        awaitReady();
        try {
            addCategoryButton().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
            page.locator("table thead th").first().waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        } catch (RuntimeException notThere) {
            // isShowing() answers the question; the guard in the test decides what it means.
        }
        page.waitForTimeout(600);
    }

    /** True when the screen is the Other Category list: its heading, Add category, and the table. */
    public boolean isShowing() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING).setExact(true)).count() > 0
                && addCategoryButton().count() > 0
                && columnIndex("category") >= 0;
    }

    /** "Home Page / Product / Other Category" - the crumbs joined, since the separators are CSS-drawn. */
    public String breadcrumb() {
        return crumbs(page);
    }

    /** A {@code ul.breadcrumb}'s items joined with " / ". Shared with the form and Manage Games. */
    static String crumbs(Page page) {
        Object raw = page.locator("ul.breadcrumb li").evaluateAll(
                "lis => lis.map(li => li.innerText.replace(/\\s+/g, ' ').trim()).filter(t => t)");
        return raw instanceof List<?> items
                ? String.join(" / ", items.stream().map(String::valueOf).toList())
                : "";
    }

    // ---------------------------------------------------------------
    // Reading the list
    // ---------------------------------------------------------------

    /**
     * One row as the capture draws it.
     *
     * @param translations the "12 translations" count under the name, or -1 when absent
     * @param status       "Active" or "Inactive", with the capture's bullet stripped
     */
    public record CategoryRow(int no, String name, int translations, boolean hasIcon, int games,
                              String status, String order) {
    }

    /** Every row on the rendered page, in display order. */
    public List<CategoryRow> categories() {
        List<CategoryRow> result = new ArrayList<>();
        int no = columnIndex("no");
        int icon = columnIndex("icon");
        int name = columnIndex("category");
        int games = columnIndex("games");
        int status = columnIndex("status");
        int order = columnIndex("order");
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() <= Math.max(name, 0)) {
                continue;   // the empty-list placeholder row
            }
            String nameCell = rawText(cells, name);
            result.add(new CategoryRow(
                    number(Pattern.compile("(\\d+)"), cellText(cells, no)),
                    firstLine(nameCell),
                    number(TRANSLATIONS, nameCell),
                    icon >= 0 && cells.nth(icon).locator("img, svg").count() > 0
                            || icon >= 0 && !cellText(cells, icon).isBlank(),
                    number(GAMES, cellText(cells, games)),
                    statusText(cells, status),
                    cellText(cells, order).replaceAll("[^0-9]", "")));
        }
        return result;
    }

    /** The names of every listed category, in display order. */
    public List<String> categoryNames() {
        return categories().stream().map(CategoryRow::name).toList();
    }

    /** The listed row for a category, or null when it is not on the rendered page. */
    public CategoryRow category(String name) {
        return categories().stream().filter(row -> row.name().equals(name)).findFirst().orElse(null);
    }

    public boolean isListed(String name) {
        return category(name) != null;
    }

    public int rowCount() {
        return categories().size();
    }

    /**
     * The {@code src} of a listed category's icon, or its text when the icon is an emoji or a
     * glyph - the capture's icons (🔥, 🎰, 🏆, ✨) could be either.
     */
    public String iconSource(String name) {
        int icon = columnIndex("icon");
        if (icon < 0) {
            return "";
        }
        Locator cell = rowFor(name).locator("td").nth(icon);
        Locator image = cell.locator("img").first();
        return image.count() > 0 ? String.valueOf(image.getAttribute("src")) : cell.innerText().trim();
    }

    /** True when a listed category's icon has actually loaded, rather than being a broken image. */
    public boolean iconRendered(String name) {
        int icon = columnIndex("icon");
        if (icon < 0) {
            return false;
        }
        Locator cell = rowFor(name).locator("td").nth(icon);
        Locator image = cell.locator("img").first();
        if (image.count() == 0) {
            return cell.locator("svg").count() > 0 || !cell.innerText().isBlank();
        }
        return Boolean.TRUE.equals(image.evaluate("img => img.complete && img.naturalWidth > 0"));
    }

    /**
     * The rows the pager says are shown, as {from, to, total} - what the capture's "Showing X-Y of Z"
     * line gave. The shipped footer states only the total and the page number, so from and to are
     * worked out from those and the selected page size. Null when the footer is not shown.
     */
    public int[] showing() {
        Locator info = page.locator(".dataTables_info").first();
        if (info.count() == 0) {
            return null;
        }
        Matcher m = PAGE_INFO.matcher(info.innerText());
        if (!m.find()) {
            return null;
        }
        int total = Integer.parseInt(m.group(1));
        int pageNo = Integer.parseInt(m.group(2));
        String selected = String.valueOf(pageSizeSelect().evaluate("s => s.value"));
        int size = selected.matches("\\d+") ? Integer.parseInt(selected) : Math.max(total, 1);
        int from = total == 0 ? 0 : (pageNo - 1) * size + 1;
        return new int[]{from, Math.min(pageNo * size, total), total};
    }

    /**
     * Column index by header text, or -1: matched on the header's start, so "Category name"
     * answers "category" and "Order ↕" answers "order".
     */
    private int columnIndex(String startsWith) {
        for (int i = 0; i < headerCells.count(); i++) {
            String text = headerCells.nth(i).innerText().trim().toLowerCase(Locale.ROOT);
            if (text.startsWith(startsWith)) {
                return i;
            }
        }
        return -1;
    }

    /** The row whose name cell's first line is {@code name}. */
    private Locator rowFor(String name) {
        int column = Math.max(columnIndex("category"), 0);
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() > column && firstLine(rawText(cells, column)).equals(name)) {
                return rows.nth(i);
            }
        }
        return rows.filter(new Locator.FilterOptions().setHasText(name)).first();
    }

    private static String rawText(Locator cells, int index) {
        return index < 0 || index >= cells.count() ? "" : cells.nth(index).innerText().trim();
    }

    /** The shipped Status cell is an inline select, whose text would be both options; read the chosen one. */
    private static String statusText(Locator cells, int index) {
        if (index < 0 || index >= cells.count()) {
            return "";
        }
        Locator select = cells.nth(index).locator("select");
        if (select.count() > 0) {
            return String.valueOf(select.first().evaluate("s => s.options[s.selectedIndex]?.text?.trim() ?? ''"));
        }
        return cellText(cells, index).replaceAll("^[^A-Za-z\\p{L}]+", "").trim();
    }

    private static String cellText(Locator cells, int index) {
        return rawText(cells, index).replaceAll("\\s+", " ").trim();
    }

    private static String firstLine(String text) {
        String line = text.split("\\R", 2)[0].trim();
        // Rendered on one line, the name is still followed by its translation count.
        return line.replaceAll("\\s*\\d+\\s+translations?\\s*$", "").trim();
    }

    private static int number(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // ---------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------

    /** Types into Category name and presses Search - the search box sits in a form Enter would submit. */
    public void search(String text) {
        CaseLog.step("Searching the category list for \"" + text + "\".");
        searchBox.fill(text);
        clickSearch();
    }

    public void clearSearch() {
        search("");
    }

    private void clickSearch() {
        page.locator("#divSearchCriteria input[type='button'][value='Search']").click();
        page.waitForTimeout(1200);
    }

    /** "All statuses", "Active" or "Inactive" - the capture's words, set as Select / Enable / Disable. */
    public void filterStatus(String option) {
        CaseLog.step("Setting the status filter to " + option + ".");
        selectByLabel(page.locator("#frmSearchCriteria select"), STATUS_FILTER.getOrDefault(option, option));
        clickSearch();
    }

    /**
     * The list's language filter, which decides which translation the Category name column shows.
     * Its options are the Create form's twelve tab labels; it defaults to 繁體.
     */
    public void filterLanguage(String option) {
        CaseLog.step("Setting the list's language filter to " + option + ".");
        selectByLabel(languageFilter(), option);
    }

    public List<String> languageFilterOptions() {
        return optionTexts(languageFilter());
    }

    /** Not "the select with an English option": the admin header's site-language select has one too. */
    private Locator languageFilter() {
        return page.locator(".lang-selector-wrapper select").first();
    }

    /** The per-page selector's sizes, smallest first - "50 per page" reads as 50. */
    public List<Integer> pageSizeOptions() {
        List<Integer> sizes = new ArrayList<>();
        for (String option : optionTexts(pageSizeSelect())) {
            String digits = option.replaceAll("\\D", "");
            if (!digits.isEmpty()) {
                sizes.add(Integer.parseInt(digits));
            }
        }
        sizes.sort(Integer::compareTo);
        return sizes;
    }

    public void setPageSize(int size) {
        CaseLog.step("Setting the list to " + size + " per page.");
        for (String option : optionTexts(pageSizeSelect())) {
            if (option.replaceAll("\\D", "").equals(String.valueOf(size))) {
                selectByLabel(pageSizeSelect(), option);
                return;
            }
        }
        throw new IllegalArgumentException("The per-page selector offers no " + size + "; it offers " + optionTexts(pageSizeSelect()));
    }

    /** True when Next is enabled. The capture draws it as "Next" and as "Next →". */
    public boolean hasNextPage() {
        Locator next = pagerControl("Next");
        return next.count() > 0 && next.isEnabled()
                && !String.valueOf(next.getAttribute("class")).contains("disabled")
                && !"true".equals(next.getAttribute("aria-disabled"));
    }

    public void nextPage() {
        CaseLog.step("Going to the next page of categories.");
        turnPage("Next");
    }

    public void previousPage() {
        CaseLog.step("Going to the previous page of categories.");
        turnPage("Previous");
    }

    /**
     * Clicks a pager control and waits for the table to be redrawn. A fixed one-second wait was not enough
     * (2026-09-29): the table repaints in place, so a read taken early mixed rows of both pages - page one
     * came back with page two's "QA SEED 15, 16" at its top. So wait for the pager's page number and the rows
     * to change, then for two reads in a row to agree.
     */
    private void turnPage(String label) {
        String infoBefore = pageInfo();
        String rowsBefore = rowsSignature();
        pagerControl(label).click();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && (pageInfo().equals(infoBefore) || rowsSignature().equals(rowsBefore))) {
            page.waitForTimeout(200);
        }
        String previous = "";
        for (int i = 0; i < 20; i++) {
            String now = rowsSignature();
            if (now.equals(previous)) {
                break;
            }
            previous = now;
            page.waitForTimeout(300);
        }
    }

    private String pageInfo() {
        Locator info = page.locator(".dataTables_info").first();
        return info.count() == 0 ? "" : info.innerText();
    }

    private String rowsSignature() {
        return String.join("\n", rows.allInnerTexts());
    }

    /** DataTables' pager anchors carry no href, so they have no link role; they are found by class. */
    private Locator pagerControl(String label) {
        return page.locator(".dataTables_paginate a.paginate_button." + label.toLowerCase(Locale.ROOT)).first();
    }

    /** "Show [50] Entry". */
    private Locator pageSizeSelect() {
        // The select inside the table header's label - not by its "Show", which 中文 translates. (The
        // Language filter beside it sits in a div, not a label.)
        return page.locator(".custom_table_wrapper label select").first();
    }

    private List<String> optionTexts(Locator select) {
        if (select.count() == 0) {
            return List.of();
        }
        Object raw = select.locator("option").evaluateAll("os => os.map(o => o.textContent.trim())");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /** By the trimmed option text: Playwright's label match is exact, and these options are padded. */
    private void selectByLabel(Locator select, String label) {
        Object value = select.evaluate("(s, l) => [...s.options].find(o => o.textContent.trim() === l)?.value ?? null", label);
        if (value == null) {
            throw new IllegalArgumentException("No option \"" + label + "\"; the select offers " + optionTexts(select));
        }
        select.selectOption(String.valueOf(value));
        page.waitForTimeout(1000);
    }

    // ---------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------

    /**
     * Add category, found by where it goes rather than by its label: the admin UI can be switched to
     * 中文 ({@code OC-I18N-002}, {@code -005}), and then every label on the screen is translated.
     */
    private Locator addCategoryButton() {
        return page.locator("section.content a[href$='add-other-category']").first();
    }

    /** Clicks Add category, which opens Create Other Category. */
    public OtherCategoryFormPage clickCreate() {
        CaseLog.step("Clicking Add category.");
        PromptMessage.clearStray(page);
        addCategoryButton().click();
        OtherCategoryFormPage form = new OtherCategoryFormPage(page);
        form.awaitOpen();
        return form;
    }

    /** The capture's Edit; the shipped screen labels it Modify. */
    public OtherCategoryFormPage clickEdit(String name) {
        CaseLog.step("Clicking Modify on \"" + name + "\".");
        PromptMessage.clearStray(page);
        rowAction(name, "Modify").click();
        OtherCategoryFormPage form = new OtherCategoryFormPage(page);
        form.awaitOpen();
        return form;
    }

    /**
     * Clicks Manage games. If the screen does not arrive - seen once on 2026-09-25, when it loaded
     * and then fell back to the list - the link's own address is opened instead, which is the
     * same screen reached without the click.
     */
    public ManageGamesPage clickManageGames(String name) {
        CaseLog.step("Clicking Manage games on \"" + name + "\".");
        PromptMessage.clearStray(page);
        Locator link = rowAction(name, "Manage games");
        String href = link.getAttribute("href");
        link.click();
        ManageGamesPage games = new ManageGamesPage(page);
        try {
            games.awaitOpen();
        } catch (RuntimeException didNotArrive) {
            if (href == null || href.isBlank()) {
                throw didNotArrive;
            }
            CaseLog.evidence("Manage games did not open from the click (now on " + page.url() + "); opening " + href + " directly.");
            PromptMessage.clearStray(page);
            page.navigate(url(href.replaceFirst("^/", "")));
            games.awaitOpen();
        }
        return games;
    }

    /**
     * The category's id, read from its Manage games link ({@code other-category-games?id=418}),
     * or -1. The client platform addresses a category's page by this id.
     */
    public int idOf(String name) {
        String href = rowAction(name, "Manage games").getAttribute("href");
        java.util.regex.Matcher id = java.util.regex.Pattern.compile("[?&]id=(\\d+)").matcher(href == null ? "" : href);
        return id.find() ? Integer.parseInt(id.group(1)) : -1;
    }

    public void clickDelete(String name) {
        CaseLog.step("Clicking Delete on \"" + name + "\".");
        PromptMessage.clearStray(page);
        rowAction(name, "Delete").click();
        deleteDialog.first().waitFor();
    }

    /**
     * Sets a row's Status with the list's inline select - "Active" or "Inactive" - and presses Confirm
     * on "Confirm to modify?". Returns what the page said after (OK pressed).
     *
     * <p>Measured 2026-09-25: this is the one way to make a category Inactive. The Create and Edit
     * forms send {@code isActive: false}, which the server refuses as "is active is a required
     * field"; the inline select calls {@code PUT .../set-active} instead, which works.
     */
    public String setStatus(String name, String status) {
        CaseLog.step("Setting \"" + name + "\" to " + status + " with the list's Status select.");
        Locator select = rowFor(name).locator("td select").first();
        selectByLabel(select, status);
        return PromptMessage.confirmAndRead(page, 2500);
    }

    /**
     * A row's action, by what it does rather than by its label, so it holds in either admin UI
     * language: Modify and Manage games are links to their screens, Delete is the red button.
     */
    private Locator rowAction(String name, String label) {
        Locator row = rowFor(name);
        String css = switch (label) {
            case "Modify" -> "a[href*='update-other-category']";
            case "Manage games" -> "a[href*='other-category-games']";
            case "Delete" -> "button.btn-danger";
            default -> throw new IllegalArgumentException("No row action \"" + label + "\"");
        };
        return row.locator(css).first();
    }

    // ---------------------------------------------------------------
    // Delete Other Category?
    // ---------------------------------------------------------------

    public boolean isDeleteDialogVisible() {
        return deleteDialog.count() > 0;
    }

    /**
     * The whole dialog: "Delete Other Category? / This action removes the category and its game
     * assignments. / Popular Games / 8 game assignments will be removed. / Underlying games and
     * their global records will remain available."
     */
    public String deleteDialogText() {
        return isDeleteDialogVisible() ? deleteDialog.first().innerText().replaceAll("\\s+", " ").trim() : "";
    }

    /** The number in "N game assignments will be removed.", or -1 when the line is absent. */
    public int deleteDialogAssignmentCount() {
        return number(ASSIGNMENTS, deleteDialogText());
    }

    /** The capture's "Delete category"; the shipped dialog's button is Confirm. */
    public void confirmDelete() {
        CaseLog.step("Clicking Confirm in the delete confirmation.");
        deleteDialog.first().locator(".ui-dialog-buttonset button").first().click();   // Confirm, then Cancel
        page.waitForTimeout(1500);
    }

    public void cancelDelete() {
        CaseLog.step("Clicking Cancel in the delete confirmation.");
        deleteDialog.first().locator(".ui-dialog-buttonset button").last().click();
        page.waitForTimeout(800);
    }

    /** Deletes a category if it is listed; true when it was there to delete. */
    public boolean deleteIfListed(String name) {
        if (!isListed(name)) {
            return false;
        }
        clickDelete(name);
        confirmDelete();
        open();
        return true;
    }

    /** Every row keyed by name, for the rows that compare the list before and after an action. */
    public Map<String, CategoryRow> snapshot() {
        Map<String, CategoryRow> byName = new LinkedHashMap<>();
        for (CategoryRow row : categories()) {
            byName.put(row.name(), row);
        }
        return byName;
    }

    /** Every visible text on the screen, for the localization row that looks for untranslated keys. */
    public String screenText() {
        return page.locator("body").innerText();
    }
}
