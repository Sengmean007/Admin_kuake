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
 * <b>Web content management &gt; Quick Tour Banner</b> - the list screen: one row per content
 * tab ({@code QT-A-LIST_*}), plus the badge and button that lead to Configure Page Banner
 * ({@code QT-A-PB_*}).
 *
 * <h2>Measured against the live screen (2026-10-24)</h2>
 * Built the same shape as the sibling list screens, {@code MemberBenefitsListPage} and
 * {@code ShareLinkListPage}, from a best-effort reading of the sheet
 * ({@code docs/test-cases/quick-tour.html}). Unlike the two form page objects below it
 * ({@code QuickTourContentTabFormPage}, {@code QuickTourPageBannerFormPage}), every locator here -
 * the Add content tab and Configure page banner links, the row table, the "Page banner ready"
 * badge, the Show-entries select and the footer - matched the real markup on first measurement, so
 * nothing needed correcting on this class.
 *
 * <h2>What the sheet says this screen shows</h2>
 * <ul>
 *   <li>A <b>badge area</b> reading whether a page banner is configured for the current currency
 *       - {@code QT-A-LIST_N02} expects it to <em>not</em> say "Page banner ready" when none was
 *       ever set up; {@code QT-A-PB_P02}/{@code P03} expect it to read ready with a
 *       "<i>N</i>/12 languages" count once PC and Mobile are both uploaded for at least the three
 *       required languages.</li>
 *   <li>A <b>Configure page banner</b> button/link leading to {@code QuickTourPageBannerFormPage}.</li>
 *   <li>An <b>Add content tab</b> button leading to {@code QuickTourContentTabFormPage}, and a
 *       <b>Modify</b> action per row leading to the same form pre-filled.</li>
 *   <li>Pagination with a <b>Show</b> entries selector ({@code QT-A-LIST_P15} exercises 10 vs
 *       50), the same shape {@code MemberBenefitsListPage} and {@code ShareLinkListPage} already
 *       drive.</li>
 *   <li>Each row's own <b>Languages</b> count, e.g. "3/12" ({@code QT-A-OVR_P10}) - read the same
 *       way {@code MemberBenefitsListPage#translationsLabel} reads "N translations" under Benefit
 *       name, assumed rather than measured here.</li>
 * </ul>
 */
public class QuickTourBannerListPage extends BasePage {

    private final Locator addButton;
    private final Locator configurePageBannerButton;
    private final Locator showEntriesSelect;
    private final Locator headerCells;
    private final Locator rows;
    private final Locator badge;
    private final Locator footer;

    public QuickTourBannerListPage(Page page) {
        super(page);
        this.addButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Add content tab").setExact(false));
        this.configurePageBannerButton = page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Configure page banner").setExact(false));
        this.showEntriesSelect = page.locator("select").filter(new Locator.FilterOptions().setHasText("20").setHasText("200"));
        this.headerCells = page.locator("table thead th");
        this.rows = page.locator("table tbody tr");
        this.badge = page.getByText("Page banner", new Page.GetByTextOptions().setExact(false)).first();
        this.footer = page.getByText("Show page", new Page.GetByTextOptions().setExact(false)).first();
    }

    // ---------------------------------------------------------------
    // Getting there
    // ---------------------------------------------------------------

    public void open() {
        CaseLog.step("Opening Web content management > Quick Tour Banner.");
        open(AdminShellPage.QUICK_TOUR_HREF.replaceFirst("^/", ""));
        awaitReady();
    }

    public boolean isShowing() {
        return addButton.count() > 0 && (rows.count() > 0 || page.getByText("No data").count() > 0);
    }

    // ---------------------------------------------------------------
    // Page banner badge
    // ---------------------------------------------------------------

    /** The badge area's own text, as-is - "" if the screen shows none. Not yet confirmed; see class javadoc. */
    public String pageBannerBadgeText() {
        return badge.count() == 0 ? "" : badge.first().innerText().replaceAll("\\s+", " ").trim();
    }

    public boolean pageBannerReady() {
        return pageBannerBadgeText().toLowerCase(java.util.Locale.ROOT).contains("ready");
    }

    public QuickTourPageBannerFormPage clickConfigurePageBanner() {
        CaseLog.step("Clicking Configure page banner.");
        configurePageBannerButton.click();
        awaitReady();
        QuickTourPageBannerFormPage form = new QuickTourPageBannerFormPage(page);
        form.awaitOpen();
        return form;
    }

    // ---------------------------------------------------------------
    // Filters / paging
    // ---------------------------------------------------------------

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

    /** The row's own "N/12" languages count - QT-A-OVR_P10. Assumed location; not yet confirmed. */
    public String languagesCountOf(int index) {
        Locator cell = rows.nth(index).getByText(java.util.regex.Pattern.compile("\\d+\\s*/\\s*12"));
        return cell.count() == 0 ? "" : cell.first().innerText().trim();
    }

    // ---------------------------------------------------------------
    // Add / Modify
    // ---------------------------------------------------------------

    public QuickTourContentTabFormPage clickAdd() {
        CaseLog.step("Clicking Add content tab.");
        addButton.click();
        awaitReady();
        QuickTourContentTabFormPage form = new QuickTourContentTabFormPage(page);
        form.awaitOpen();
        return form;
    }

    public QuickTourContentTabFormPage clickModify(int index) {
        CaseLog.step("Clicking Modify on row " + index + ".");
        rows.nth(index).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Modify")).click();
        awaitReady();
        QuickTourContentTabFormPage form = new QuickTourContentTabFormPage(page);
        form.awaitOpen();
        return form;
    }
}
