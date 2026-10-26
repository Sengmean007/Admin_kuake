package org.sengmean.page.webcontent;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Web content management &gt; Member Benefits</b> - the list screen.
 *
 * <h2>Measured live on admin-test 2026-10-22</h2>
 * Deployed at {@code /cms/member-benefits}, the only Web content management entry besides Game
 * page banner that was live that date (Share link, Welcome Banner, Banner management, Promotion
 * Banner, Fast login banner and Quick Tour Banner are all separate, already-existing screens on
 * the same menu, not this one).
 * <ul>
 *   <li>Columns: <b>No., Icon, Benefit name, Banners, Status, Order, Updated, Actions</b>.
 *       Benefit name carries a second line, "N translations". <b>Status is itself a live
 *       {@code <select>}</b> in the row (Active/Inactive) - not a read-only badge like Share
 *       Link's - so flipping it is one {@code selectOption} away, no form to open.</li>
 *   <li>Filters: <b>Benefit name</b> search box, a <b>Status</b> select (blank "Select" / Active
 *       / Inactive), a <b>Show</b> entries select (20/50/100/150/200) and a <b>Language</b>
 *       select of the same 12 languages the form's tabs use, defaulting to 繁體 - the same
 *       default Share Link's spec note describes.</li>
 *   <li>Actions: <b>Modify</b> is a plain link to {@code /cms/member-benefit-form?id=N}; the
 *       {@code id} is the record's own id, nothing to parse out of the row. <b>Delete</b> raises
 *       the jQuery UI {@link PromptMessage} confirmation - "This action removes the member
 *       benefit and all of its banners." with <b>Confirm</b> / <b>Cancel</b> - measured directly,
 *       the same component {@code PromptMessage} already drives for Other Category and
 *       Notification.</li>
 *   <li>Pager: First / Previous / page numbers / Next / Last, same as the other list screens.
 *       The footer line reads <b>"Common5 Unit， Show page 1 Page， Common 1 Page"</b> for five
 *       records on one page - an un-localized template string ({@code "Common{n} Unit"} /
 *       {@code "Show page {p} Page"} / {@code "Common{p} Page"}), not the "Showing x-y of N"
 *       wording the client's workbook describes. {@link #footerText()} returns it as-is; a case
 *       that reads it should not assume the workbook's phrasing.</li>
 * </ul>
 */
public class MemberBenefitsListPage extends BasePage {

    private final Locator addButton;
    private final Locator nameSearch;
    private final Locator statusFilter;
    private final Locator showEntriesSelect;
    private final Locator languageFilter;
    private final Locator searchButton;
    private final Locator resetButton;
    private final Locator headerCells;
    private final Locator rows;
    private final Locator footer;

    public MemberBenefitsListPage(Page page) {
        super(page);
        this.addButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Add member benefit").setExact(false));
        this.nameSearch = page.locator("input[placeholder*='enefit name' i]");
        this.statusFilter = page.locator("select").filter(new Locator.FilterOptions().setHasText("Active").setHasText("Inactive"))
                .filter(new Locator.FilterOptions().setHasNotText("繁體"));
        this.showEntriesSelect = page.locator("select").filter(new Locator.FilterOptions().setHasText("20").setHasText("200"));
        this.languageFilter = page.locator("select").filter(new Locator.FilterOptions().setHasText("繁體"));
        this.searchButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search").setExact(true));
        this.resetButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset all").setExact(true));
        this.headerCells = page.locator("table thead th");
        this.rows = page.locator("table tbody tr");
        this.footer = page.getByText("Show page", new Page.GetByTextOptions().setExact(false)).first();
    }

    // ---------------------------------------------------------------
    // Getting there
    // ---------------------------------------------------------------

    public void open() {
        CaseLog.step("Opening Web content management > Member Benefits.");
        open(AdminShellPage.MEMBER_BENEFITS_HREF.replaceFirst("^/", ""));
        awaitReady();
    }

    public boolean isShowing() {
        return addButton.count() > 0 && (rows.count() > 0 || page.getByText("No data").count() > 0);
    }

    // ---------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------

    public String languageFilterValue() {
        return languageFilter.count() == 0 ? "" : languageFilter.first().inputValue();
    }

    public void filterLanguage(String label) {
        CaseLog.step("Setting the Language filter to \"" + label + "\".");
        languageFilter.first().selectOption(new SelectOption().setLabel(label));
        page.waitForTimeout(500);
    }

    public void filterStatus(String label) {
        CaseLog.step("Setting the Status filter to \"" + label + "\".");
        statusFilter.first().selectOption(new SelectOption().setLabel(label));
        searchButton.click();
        page.waitForTimeout(500);
    }

    public void searchName(String text) {
        CaseLog.step("Searching Member Benefits names for \"" + text + "\".");
        nameSearch.fill(text);
        searchButton.click();
        page.waitForTimeout(500);
    }

    public void resetAll() {
        CaseLog.step("Clicking Reset all.");
        resetButton.click();
        page.waitForTimeout(500);
    }

    public void setShowEntries(int n) {
        showEntriesSelect.first().selectOption(String.valueOf(n));
        page.waitForTimeout(500);
    }

    public String footerText() {
        return footer.count() == 0 ? "" : footer.first().innerText().replaceAll("\\s+", " ").trim();
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

    /** The row's cells by header name. "Benefit name" is just the name - the "N translations" line is dropped. */
    public Map<String, String> row(int index) {
        List<String> heads = headers();
        Map<String, String> result = new LinkedHashMap<>();
        if (index >= rows.count()) {
            return result;
        }
        Locator cells = rows.nth(index).locator("td");
        int count = Math.min(heads.size(), cells.count());
        for (int i = 0; i < count; i++) {
            String text = cells.nth(i).innerText().trim();
            if ("Benefit name".equals(heads.get(i))) {
                text = text.split("\\R")[0].trim();
            }
            result.put(heads.get(i), text);
        }
        return result;
    }

    /** The "N translations" line under the row's Benefit name - "" if not shown. */
    public String translationsLabel(int index) {
        Locator cell = rows.nth(index).locator("td").nth(headers().indexOf("Benefit name"));
        String text = cell.innerText().trim();
        String[] lines = text.split("\\R");
        return lines.length > 1 ? lines[1].trim() : "";
    }

    /** True when the list's rows read in ascending Order - MB-ADM-LST-P004. */
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

    /** The row's own Status select - MB-ADM-LST, unlike Share Link, flips in place without opening the form. */
    public Locator statusSelectOf(int index) {
        return rows.nth(index).locator("select");
    }

    public String statusOf(int index) {
        Locator select = statusSelectOf(index);
        return select.count() == 0 ? "" : select.first().inputValue();
    }

    /** Flips the row's own Status select to "Active" or "Inactive" in place. */
    public void setStatusOf(int index, String label) {
        CaseLog.step("Setting row " + index + "'s Status to \"" + label + "\" from the list.");
        statusSelectOf(index).selectOption(new SelectOption().setLabel(label));
        page.waitForTimeout(500);
    }

    // ---------------------------------------------------------------
    // Add / Modify
    // ---------------------------------------------------------------

    public MemberBenefitsFormPage clickAdd() {
        CaseLog.step("Clicking Add member benefit.");
        addButton.click();
        awaitReady();
        MemberBenefitsFormPage form = new MemberBenefitsFormPage(page);
        form.awaitOpen();
        return form;
    }

    public MemberBenefitsFormPage clickModify(int index) {
        CaseLog.step("Clicking Modify on row " + index + ".");
        rows.nth(index).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Modify")).click();
        awaitReady();
        MemberBenefitsFormPage form = new MemberBenefitsFormPage(page);
        form.awaitOpen();
        return form;
    }

    /** The {@code id} query parameter of row {@code index}'s Modify link, read without navigating. */
    public String idOf(int index) {
        String href = rows.nth(index).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Modify")).getAttribute("href");
        if (href == null) {
            return "";
        }
        int at = href.indexOf("id=");
        return at < 0 ? "" : href.substring(at + 3);
    }

    // ---------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------

    public void clickDelete(int index) {
        CaseLog.step("Clicking Delete on row " + index + ".");
        rows.nth(index).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete")).click();
    }

    /** Clicks Delete on row {@code index} and presses Confirm on the Prompt message, OK pressed after. */
    public String deleteAndConfirm(int index) {
        clickDelete(index);
        return PromptMessage.confirmAndRead(page, 5000);
    }

    /** Clicks Delete on row {@code index} and presses Cancel on the Prompt message - nothing removed. */
    public String deleteAndCancel(int index) {
        clickDelete(index);
        return PromptMessage.declineAndRead(page, 5000);
    }
}
