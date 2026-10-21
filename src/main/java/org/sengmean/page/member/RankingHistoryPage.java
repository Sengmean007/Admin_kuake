package org.sengmean.page.member;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The read-only <b>Ranking History</b> modal a list row's History icon opens.
 *
 * <h2>Read from the Figma design (frame {@code admin-rankings-referral-history-modal})</h2>
 * <ul>
 *   <li>Title "Ranking History - &lt;username&gt;"; a subtitle line "Member ID M100231 · Referral
 *       Ranking · Daily snapshots · 00:00 (UTC+7)" (the Withdrawal modal reads "Hourly snapshots").
 *       Note this UTC+7 does not agree with Ranking Settings' own "00:00 (UTC+8)" wording - a
 *       genuine inconsistency in the mock worth flagging, not a locator question.</li>
 *   <li>Four summary tiles: <b>Current rank</b>, <b>Total Commission</b> (or Total Withdraw),
 *       <b>Referrals</b> (or Withdrawals), <b>Best rank</b>.</li>
 *   <li>A <b>Period</b> dropdown (the mock shows "Last 7 days"; the sheet's RANK-ADM-HIS-006 also
 *       expects Today and Last 30 days).</li>
 *   <li>A table: <b>Snapshot Time, Rank, Rank Change, Final Commission</b> (or Final Withdrawal),
 *       <b>Amount Change, Referrals</b> (or Withdrawals) - newest first. Rank Change reads "▲1" /
 *       "▼1" / "—"; Amount Change a signed amount. A footer note: "Rank Change and Amount Change
 *       compare each snapshot with the previous one. Data is read-only."</li>
 *   <li>A footer "Showing 1 to 7 of 365 snapshots", Prev/page numbers/Next, "Export CSV" and
 *       "Close", plus a "✕" in the header.</li>
 * </ul>
 */
public class RankingHistoryPage extends BasePage {

    private final Locator dialog;
    private final Locator title;
    private final Locator periodSelect;
    private final Locator headerCells;
    private final Locator rows;
    private final Locator exportButton;
    private final Locator closeButton;
    private final Locator closeIcon;

    public RankingHistoryPage(Page page) {
        super(page);
        this.dialog = page.getByText("Ranking History", new Page.GetByTextOptions().setExact(false))
                .locator("xpath=ancestor::*[self::div][1]");
        this.title = page.getByText("Ranking History -", new Page.GetByTextOptions().setExact(false));
        this.periodSelect = page.locator("select").first();
        this.headerCells = page.locator("table thead th");
        this.rows = page.locator("table tbody tr");
        this.exportButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export CSV"));
        this.closeButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close").setExact(true));
        this.closeIcon = page.locator("[aria-label='Close'], button:has-text('✕'), button:has-text('×')").first();
    }

    public boolean isShowing() {
        return title.count() > 0;
    }

    public String titleText() {
        return title.count() == 0 ? "" : title.first().innerText().trim();
    }

    /** A summary tile's value, by the label above it (e.g. "Current rank", "Best rank"). */
    public String summaryTile(String label) {
        Locator byLabel = page.getByText(label, new Page.GetByTextOptions().setExact(true));
        if (byLabel.count() == 0) {
            return "";
        }
        Locator value = byLabel.first().locator("xpath=following-sibling::*[1]");
        return value.count() == 0 ? "" : value.first().innerText().trim();
    }

    public void selectPeriod(String label) {
        CaseLog.step("Setting the History period to \"" + label + "\".");
        periodSelect.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
        page.waitForTimeout(500);
    }

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

    public String exportCsv() {
        CaseLog.step("Clicking Export CSV in the History modal.");
        try {
            Download download = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(10_000), exportButton::click);
            return download.suggestedFilename();
        } catch (com.microsoft.playwright.PlaywrightException noDownload) {
            CaseLog.evidence("History's Export CSV did not trigger a download within 10s: " + noDownload.getMessage());
            return "";
        }
    }

    public void closeViaIcon() {
        closeIcon.click();
    }

    public void closeViaButton() {
        closeButton.click();
    }
}
