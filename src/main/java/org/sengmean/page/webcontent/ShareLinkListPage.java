package org.sengmean.page.webcontent;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Web content management &gt; Share link</b> - the list screen.
 *
 * <h2>Confirmed live 2026-10-26 at {@code cms/share-link}</h2>
 * Not deployed as of 2026-10-21 (Web content management then offered only Member Benefits and Game
 * page banner); live now, at the path the Figma spec note implied. Every locator was re-measured
 * against the real screen on 2026-10-26 - see {@code ShareLinkTest}'s Javadoc for what that pass
 * changed. The original spec note Si gave ("Back office &gt; Web content management &gt; Share
 * link", Platform project / "version 3.20.0" page) still states:
 * <ul>
 *   <li>Breadcrumb "Home Page &gt; Web Content Management System &gt; Share Link"; a header "Share
 *       Link" with a <b>+ Add share link</b> button.</li>
 *   <li>Controls: "Show [50] Entry", "Status: [All]", "Language: [...]" (the note says 繁體 is the
 *       confirmed default - the mock itself showing English is called out as a design mistake,
 *       {@code SL-ADM-LST-005}), "Title: [Search title]" with a Search button, and a refresh icon.</li>
 *   <li>Columns: <b>No., Card image, Title, Text content, URL link, Order, Status, Last updated,
 *       Action</b> (Modify, Delete). Sorted by Order ascending (smallest first).</li>
 *   <li>Delete opens a confirm dialog: "Delete share link", a {@code ✕}, the record's name and
 *       "Active/Inactive · Order n", a removal warning, Cancel / Delete - the same shape
 *       {@code OtherCategoryListPage}'s delete confirmation already uses on this project.</li>
 * </ul>
 */
public class ShareLinkListPage extends BasePage {

    private final Locator addButton;
    private final Locator showEntriesSelect;
    private final Locator statusFilter;
    private final Locator languageFilter;
    private final Locator titleSearch;
    private final Locator searchButton;
    private final Locator refreshIcon;
    private final Locator headerCells;
    private final Locator rows;
    private final Locator footer;
    private final Locator deleteDialog;

    public ShareLinkListPage(Page page) {
        super(page);
        // Confirmed live 2026-10-26: "+ Add share link" is <a class="btn btn-primary">, not a
        // <button> - AriaRole.BUTTON matched nothing and made isShowing()/clickAdd() fail outright.
        this.addButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Add share link").setExact(false));
        // Scoped by the option set each select actually carries, not DOM position or "English"/
        // "Active" text - the header's own Interface Language select (id="lang") sits before these
        // in the DOM and shares both those words, so page.locator("select").first() and a hasText
        // ("English") filter both silently resolved to the header selector instead of this screen's
        // own controls. 20/200 and 繁體 are each unique to their own select.
        this.showEntriesSelect = page.locator("select").filter(new Locator.FilterOptions().setHasText("20").setHasText("200"));
        this.statusFilter = page.locator("select").filter(new Locator.FilterOptions().setHasText("Active"));
        this.languageFilter = page.locator("select").filter(new Locator.FilterOptions().setHasText("繁體"));
        this.titleSearch = page.locator("input[placeholder*='itle' i]");
        this.searchButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search").setExact(true));
        this.refreshIcon = page.locator("[class*='refresh' i], [title*='refresh' i]").first();
        this.headerCells = page.locator("table thead th");
        this.rows = page.locator("table tbody tr");
        this.footer = page.locator(":text('Showing')").first();
        // Confirmed live 2026-10-26: the ancestor::div[1] of the "Delete share link" heading is only
        // share-link-modal-header - it does not contain the Cancel/Delete buttons, so confirmDelete()
        // and cancelDelete() threw "no element found". .share-link-modal is the whole dialog.
        this.deleteDialog = page.locator(".share-link-modal");
    }

    // ---------------------------------------------------------------
    // Getting there
    // ---------------------------------------------------------------

    public void open() {
        CaseLog.step("Opening Web content management > Share link.");
        open(AdminShellPage.SHARE_LINK_HREF.replaceFirst("^/", ""));
        awaitReady();
    }

    public boolean isShowing() {
        return addButton.count() > 0 && (rows.count() > 0 || page.getByText("No data").count() > 0);
    }

    /** Raw count behind {@link #isShowing()}'s first signal - temporary diagnostic, see {@code ShareLinkTest}. */
    public int addButtonCountForDiagnostics() {
        return addButton.count();
    }

    // ---------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------

    public String languageFilterValue() {
        return languageFilter.count() == 0 ? "" : languageFilter.first().inputValue();
    }

    public void filterLanguage(String label) {
        CaseLog.step("Setting the Language filter to \"" + label + "\".");
        languageFilter.first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
        page.waitForTimeout(500);
    }

    public void filterStatus(String label) {
        CaseLog.step("Setting the Status filter to \"" + label + "\".");
        statusFilter.first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
        page.waitForTimeout(500);
    }

    public void searchTitle(String text) {
        CaseLog.step("Searching Share Link titles for \"" + text + "\".");
        titleSearch.fill(text);
        searchButton.click();
        page.waitForTimeout(500);
    }

    public void setShowEntries(int n) {
        showEntriesSelect.first().selectOption(String.valueOf(n));
        page.waitForTimeout(500);
    }

    public void clickRefresh() {
        CaseLog.step("Clicking the refresh icon.");
        refreshIcon.click();
        page.waitForTimeout(500);
    }

    public String footerText() {
        return footer.count() == 0 ? "" : footer.first().innerText().trim();
    }

    // ---------------------------------------------------------------
    // The table
    // ---------------------------------------------------------------

    public List<String> headers() {
        return headerCells.allInnerTexts().stream().map(String::trim).toList();
    }

    public int rowCount() {
        return rows.count();
    }

    public Map<String, String> row(int index) {
        List<String> heads = headers();
        Map<String, String> result = new LinkedHashMap<>();
        if (index >= rows.count()) {
            return result;
        }
        Locator cells = rows.nth(index).locator("td");
        int count = Math.min(heads.size(), cells.count());
        for (int i = 0; i < count; i++) {
            result.put(heads.get(i), cells.nth(i).innerText().trim());
        }
        return result;
    }

    /** True when the list's rows read in ascending Order - SL-ADM-LST-003. */
    public boolean isSortedByOrderAscending() {
        int column = headers().indexOf("Order");
        if (column < 0) {
            return false;
        }
        int previous = Integer.MIN_VALUE;
        for (int i = 0; i < rows.count(); i++) {
            String text = row(i).getOrDefault("Order", "").replaceAll("[^0-9-]", "");
            if (text.isBlank()) {
                continue;
            }
            int value = Integer.parseInt(text);
            if (value < previous) {
                return false;
            }
            previous = value;
        }
        return true;
    }

    public Locator urlLinkOf(int index) {
        return rows.nth(index).locator("a[href^='http']");
    }

    // ---------------------------------------------------------------
    // Add / Modify
    // ---------------------------------------------------------------

    public ShareLinkFormPage clickAdd() {
        CaseLog.step("Clicking + Add share link.");
        addButton.click();
        awaitReady();
        ShareLinkFormPage form = new ShareLinkFormPage(page);
        form.awaitOpen();
        return form;
    }

    public ShareLinkFormPage clickModify(int index) {
        CaseLog.step("Clicking Modify on row " + index + ".");
        // Confirmed live 2026-10-26: "Modify" is <a class="btn btn-primary btn-xs">, Delete is a
        // real <button> - the two are not the same role here, unlike elsewhere in this project.
        rows.nth(index).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Modify")).click();
        awaitReady();
        ShareLinkFormPage form = new ShareLinkFormPage(page);
        form.awaitOpen();
        return form;
    }

    // ---------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------

    public void clickDelete(int index) {
        CaseLog.step("Clicking Delete on row " + index + ".");
        rows.nth(index).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete")).click();
    }

    public boolean isDeleteDialogVisible() {
        return deleteDialog.count() > 0 && deleteDialog.first().isVisible();
    }

    public String deleteDialogText() {
        return isDeleteDialogVisible() ? deleteDialog.first().innerText().replaceAll("\\s+", " ").trim() : "";
    }

    public void confirmDelete() {
        CaseLog.step("Confirming delete.");
        deleteDialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete").setExact(true)).last().click();
        page.waitForTimeout(500);
    }

    public void cancelDelete() {
        deleteDialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Cancel").setExact(true)).click();
    }

    public void closeDeleteDialogViaIcon() {
        deleteDialog.locator("[aria-label='Close'], button:has-text('✕'), button:has-text('×')").first().click();
    }
}
