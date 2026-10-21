package org.sengmean.page.member;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Membership and Referral &gt; Rankings</b> - the Referral Rankings and Withdrawal Rankings
 * tabs. Both tabs share one layout, so this page object works for whichever is open, reading
 * columns by their own header text rather than assuming "Commission" or "Withdraw".
 *
 * <h2>Not deployed yet - this is read from the Figma design, not measured</h2>
 * Measured live on admin-test 2026-10-21: "Membership and Referrals" offers no Rankings entry at
 * all. Every locator here is read from the mockup instead (Platform project, "version 3.20.0"
 * page, frames {@code admin-rankings-referral-list} / {@code admin-rankings-withdrawal-list}),
 * which Si gave as the reference for this feature. As measured there:
 * <ul>
 *   <li>Breadcrumb "Home Page &gt; Membership and Referral &gt; Rankings"; three tabs <b>Referral
 *       Rankings</b>, <b>Withdrawal Rankings</b>, <b>Ranking Settings</b>.</li>
 *   <li>A status line reading, e.g., "Updates once daily at 00:00 (UTC+7) · Data period: All-time
 *       · Ranked accounts: 12,486 · Last updated: 2026-10-14 00:00:00 · Next update: 2026-10-15
 *       00:00:00" (Withdrawal reads "Updates every 1 hour" and its own Last/Next updated values).</li>
 *   <li>A toggle "Display Referral Ranking to client" / "Display Withdrawal Ranking to client".</li>
 *   <li>Controls: "Show [50] entries", a "Search username / member ID" box, "Export CSV".</li>
 *   <li>Columns, read off the mock rather than the sheet's AD-03 paraphrase, which the mock does not
 *       quite match - it names Member ID and leaves out Adjustment/Final: <b>Rank, Username, Total
 *       Commission (or Total Withdraw), Adjustment, Final Commission (or Final Withdrawal),
 *       Referrals (or Withdrawals), Last Updated, History</b> (a clock icon; no per-row edit
 *       control anywhere - AD-05).</li>
 *   <li>A footer "Showing 1 to 10 of 12,486 entries" and First/Prev/page numbers/Next/Last paging.</li>
 * </ul>
 * No currency selector is drawn on either list frame. Rebate set up's screens are per-currency the
 * same way and reuse the header's own selector ({@link AdminShellPage#selectCurrency}) rather than
 * carrying one of their own, so that is assumed here too until the real screen says otherwise.
 */
public class RankingListPage extends BasePage {

    public static final String TAB_REFERRAL = "Referral Rankings";
    public static final String TAB_WITHDRAWAL = "Withdrawal Rankings";
    public static final String TAB_SETTINGS = "Ranking Settings";

    private final Locator statusBar;
    private final Locator displayToggle;
    private final Locator showEntriesSelect;
    private final Locator searchBox;
    private final Locator exportButton;
    private final Locator headerCells;
    private final Locator rows;
    private final Locator footer;

    public RankingListPage(Page page) {
        super(page);
        this.statusBar = page.locator(":text('Ranked accounts')").first()
                .locator("xpath=ancestor::*[self::div or self::section][1]");
        this.displayToggle = page.locator(":text('Display'):text('Ranking to client')").first()
                .locator("xpath=following-sibling::*[1]");
        this.showEntriesSelect = page.locator("select").first();
        this.searchBox = page.locator("input[placeholder='Search username / member ID'], input[placeholder*='username' i]");
        this.exportButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export CSV"));
        this.headerCells = page.locator("table thead th");
        this.rows = page.locator("table tbody tr");
        this.footer = page.locator(":text('Showing')").first();
    }

    // ---------------------------------------------------------------
    // Getting there
    // ---------------------------------------------------------------

    public void open() {
        CaseLog.step("Opening Membership and Referral > Rankings.");
        open(AdminShellPage.RANKINGS_HREF.replaceFirst("^/", ""));
        awaitReady();
    }

    /** True once a Rankings tab bar and either a table or an empty-state message is on the page. */
    public boolean isShowing() {
        return tab(TAB_REFERRAL).count() > 0 && (rows.count() > 0 || page.getByText("No data").count() > 0);
    }

    private Locator tab(String name) {
        return page.getByText(name, new Page.GetByTextOptions().setExact(true)).filter(new Locator.FilterOptions().setVisible(true));
    }

    public void openReferralTab() {
        clickTab(TAB_REFERRAL);
    }

    public void openWithdrawalTab() {
        clickTab(TAB_WITHDRAWAL);
    }

    public RankingSettingsPage openSettingsTab() {
        clickTab(TAB_SETTINGS);
        return new RankingSettingsPage(page);
    }

    private void clickTab(String name) {
        CaseLog.step("Opening the \"" + name + "\" tab.");
        tab(name).first().click();
        awaitReady();
        page.waitForTimeout(500);
    }

    // ---------------------------------------------------------------
    // Status bar
    // ---------------------------------------------------------------

    public String statusBarText() {
        return statusBar.count() == 0 ? "" : statusBar.first().innerText().replaceAll("\\s+", " ").trim();
    }

    /** One field out of the status line, by the label it follows (e.g. "Last updated", "Next update", "Ranked accounts"). */
    public String statusField(String label) {
        Matcher m = Pattern.compile(Pattern.quote(label) + "\\s*[:：]?\\s*([^·]+)").matcher(statusBarText());
        return m.find() ? m.group(1).trim() : "";
    }

    public boolean isDisplayToggleOn() {
        if (displayToggle.count() == 0) {
            return false;
        }
        String cls = String.valueOf(displayToggle.first().getAttribute("class")).toLowerCase(java.util.Locale.ROOT);
        return cls.contains("on") || cls.contains("checked") || cls.contains("active")
                || "true".equals(displayToggle.first().getAttribute("aria-checked"));
    }

    public void setDisplayToggle(boolean on) {
        if (isDisplayToggleOn() != on) {
            CaseLog.step("Setting the client display toggle " + (on ? "On" : "Off") + ".");
            displayToggle.first().click();
            page.waitForTimeout(500);
        }
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

    /** A data row as header-&gt;cell, 0-based. */
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

    /** True when a row carries no control beyond the History icon - AD-05 / RANK-DAT-013. */
    public boolean hasOnlyHistoryControl(int index) {
        if (index >= rows.count()) {
            return false;
        }
        Locator actionable = rows.nth(index).locator("button, a[href], input, select");
        return actionable.count() <= 1;
    }

    public void search(String text) {
        CaseLog.step("Searching Rankings for \"" + text + "\".");
        searchBox.first().fill(text);
        page.waitForTimeout(800);
    }

    public void clearSearch() {
        search("");
    }

    public void setShowEntries(int n) {
        if (showEntriesSelect.count() == 0) {
            return;
        }
        showEntriesSelect.first().selectOption(String.valueOf(n));
        page.waitForTimeout(500);
    }

    public String footerText() {
        return footer.count() == 0 ? "" : footer.first().innerText().trim();
    }

    /** Clicks Export CSV and returns the downloaded file's suggested name - "" if nothing downloaded within 10s. */
    public String exportCsv() {
        CaseLog.step("Clicking Export CSV.");
        try {
            Download download = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(10_000), exportButton::click);
            String name = download.suggestedFilename();
            CaseLog.data("Export CSV downloaded", name);
            return name;
        } catch (com.microsoft.playwright.PlaywrightException noDownload) {
            CaseLog.evidence("Export CSV did not trigger a download within 10s: " + noDownload.getMessage());
            return "";
        }
    }

    /** Opens the History modal for a row, by its 0-based index. */
    public RankingHistoryPage openHistory(int index) {
        CaseLog.step("Opening History for row " + index + ".");
        rows.nth(index).locator("button, a, [role=button]").last().click();
        RankingHistoryPage history = new RankingHistoryPage(page);
        page.waitForTimeout(500);
        return history;
    }
}
