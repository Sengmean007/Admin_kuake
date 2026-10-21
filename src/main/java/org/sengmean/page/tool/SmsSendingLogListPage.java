package org.sengmean.page.tool;

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
 * <b>Tool &gt; SMS Sending Log</b> - the list screen, read from the client's workbook and the
 * Figma mockup Si gave (Platform project, "version <b>5.1.0</b>" page, frame
 * {@code BO / Tool / SMS Sending Log - admin without Show more permission} and its "Version A"
 * twin).
 *
 * <h2>Not deployed yet</h2>
 * Measured live on admin-test 2026-10-21: Tool offers only Notification - no SMS Sending Log.
 * Every locator here is read from the mockup, the same caveat {@code RankingListPage} and
 * {@code ShareLinkListPage} carry for their own screens.
 *
 * <h2>What the mockup shows</h2>
 * <ul>
 *   <li>A "Search condition" panel: <b>Time limit</b> (a date-time range, defaulting to today
 *       00:00-23:59), <b>Phone number</b>, <b>Username</b>, <b>IP address</b>
 *       ("e.g. 103.25.61.12"), <b>Domain</b> ("e.g. 1083.city or ag022.1083.city"), <b>Method</b>
 *       (All / SMS / Voice / Reverse SMS), <b>Purpose</b> (All / Registration / Forgot password /
 *       Change phone), <b>Delivery status</b>, <b>Verification result</b>; <b>Search</b> /
 *       <b>Reset all</b> buttons; quick-filter chips <b>All</b> / <b>Verified, not registered
 *       (n)</b> / <b>Limit reached (n)</b> / <b>Failed delivery (n)</b>; <b>Export</b> top-right.</li>
 *   <li>Five summary cards: <b>Codes sent</b> (with an SMS/Voice/Reverse SMS breakdown),
 *       <b>Verified</b> (with a % of codes sent), <b>Failed delivery</b>, <b>Limit reached</b>
 *       (resets 00:00 HKT), <b>Verified, not registered</b>.</li>
 *   <li>"Show [50] Entry" with a refresh icon, and a note: "Daily limit: 3 codes per number (SMS +
 *       Voice), resets 00:00 HKT. Reverse SMS has no limit. Phone shown full or masked by admin
 *       permission."</li>
 *   <li>Table columns: <b>No., Time, Phone, Method, Purpose, Delivery, Verification result, Codes
 *       today, IP / Domain, Username, Actions</b> (View, Show more, Block/Unblock - Show more
 *       hidden for an admin without that permission).</li>
 * </ul>
 */
public class SmsSendingLogListPage extends BasePage {

    private final Locator timeLimitInput;
    private final Locator phoneInput;
    private final Locator usernameInput;
    private final Locator ipInput;
    private final Locator domainInput;
    private final Locator searchButton;
    private final Locator resetAllButton;
    private final Locator exportButton;
    private final Locator refreshIcon;
    private final Locator showEntriesSelect;
    private final Locator headerCells;
    private final Locator rows;
    private final Locator footer;
    private final Locator nextPageButton;
    private final Locator previousPageButton;

    public SmsSendingLogListPage(Page page) {
        super(page);
        // Confirmed against the Figma frame "BO / Tool / SMS Sending Log (upgraded)" (node
        // 40013545:236) on 2026-10-21: "Time limit" is ONE input showing the whole range as text
        // ("2026-10-05 00:00 ~ 2026-10-06 23:59"), not two separate date fields - a prior guess
        // here took the next two <input> elements after the label, which would actually have
        // resolved to Phone number's input as the "second" one since there is only one here.
        this.timeLimitInput = page.getByText("Time limit", new Page.GetByTextOptions().setExact(false)).first()
                .locator("xpath=following::input[1]");
        this.phoneInput = page.locator("input[placeholder*='phone' i]").first();
        this.usernameInput = page.locator("input[placeholder*='username' i]").first();
        this.ipInput = page.locator("input[placeholder*='103.25' i], input[placeholder*='ip' i]").first();
        this.domainInput = page.locator("input[placeholder*='1083.city' i], input[placeholder*='domain' i]").first();
        this.searchButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search").setExact(true));
        this.resetAllButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset all"));
        this.exportButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export"));
        this.refreshIcon = page.locator("[class*='refresh' i], [title*='refresh' i]").first();
        this.showEntriesSelect = page.locator("select").first();
        this.headerCells = page.locator("table thead th");
        this.rows = page.locator("table tbody tr");
        this.footer = page.locator(":text('Showing')").first();
        this.nextPageButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Next"))
                .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Next")));
        this.previousPageButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Previous"))
                .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Previous")));
    }

    // ---------------------------------------------------------------
    // Getting there
    // ---------------------------------------------------------------

    public void open() {
        CaseLog.step("Opening Tool > SMS Sending Log.");
        open(AdminShellPage.SMS_SENDING_LOG_HREF.replaceFirst("^/", ""));
        awaitReady();
    }

    public boolean isShowing() {
        return searchButton.count() > 0 && (rows.count() > 0 || page.getByText("No data").count() > 0);
    }

    // ---------------------------------------------------------------
    // Search condition
    // ---------------------------------------------------------------

    /** The Time limit field's current text, e.g. "2026-10-05 00:00 ~ 2026-10-06 23:59". */
    public String timeLimitValue() {
        return timeLimitInput.count() == 0 ? "" : timeLimitInput.inputValue();
    }

    /**
     * Sets the Time limit range and searches. The field is one input that reads the whole range
     * as text ("yyyy-MM-dd HH:mm ~ yyyy-MM-dd HH:mm"); start/end are joined in that format. A real
     * picker may instead need a click-to-open calendar - confirm once the screen ships.
     */
    public void setTimeLimit(String start, String end) {
        CaseLog.step("Setting Time limit to \"" + start + "\" ~ \"" + end + "\".");
        timeLimitInput.fill(start + " ~ " + end);
        searchButton.click();
        page.waitForTimeout(800);
    }

    public void searchPhone(String value) {
        CaseLog.step("Searching SMS Sending Log by phone \"" + value + "\".");
        phoneInput.fill(value);
        searchButton.click();
        page.waitForTimeout(800);
    }

    /** Types into Phone number and presses Enter instead of clicking Search. */
    public void searchPhoneWithEnter(String value) {
        CaseLog.step("Typing \"" + value + "\" into Phone number and pressing Enter.");
        phoneInput.fill(value);
        phoneInput.press("Enter");
        page.waitForTimeout(800);
    }

    public void searchUsername(String value) {
        usernameInput.fill(value);
        searchButton.click();
        page.waitForTimeout(800);
    }

    public void searchIp(String value) {
        ipInput.fill(value);
        searchButton.click();
        page.waitForTimeout(800);
    }

    public void searchDomain(String value) {
        domainInput.fill(value);
        searchButton.click();
        page.waitForTimeout(800);
    }

    public void filterSelect(String label, String option) {
        Locator select = page.locator("label, div").filter(new Locator.FilterOptions().setHasText(label))
                .locator("select").first();
        if (select.count() == 0) {
            select = page.getByText(label, new Page.GetByTextOptions().setExact(false)).first()
                    .locator("xpath=following::select[1]");
        }
        Object value = select.evaluate("(s, l) => [...s.options].find(o => o.textContent.trim() === l)?.value ?? null", option);
        if (value == null) {
            throw new IllegalArgumentException("The " + label + " select offers no \"" + option + "\"");
        }
        select.selectOption(String.valueOf(value));
        page.waitForTimeout(500);
    }

    public void clickSearch() {
        searchButton.click();
        page.waitForTimeout(800);
    }

    public void resetAll() {
        CaseLog.step("Clicking Reset all.");
        resetAllButton.click();
        page.waitForTimeout(500);
    }

    public void clickQuickFilter(String label) {
        CaseLog.step("Clicking the quick filter \"" + label + "\".");
        page.getByText(Pattern.compile(Pattern.quote(label), Pattern.CASE_INSENSITIVE)).first().click();
        page.waitForTimeout(800);
    }

    public void clickRefresh() {
        refreshIcon.click();
        page.waitForTimeout(800);
    }

    public void setShowEntries(int n) {
        showEntriesSelect.first().selectOption(String.valueOf(n));
        page.waitForTimeout(500);
    }

    public String footerText() {
        return footer.count() == 0 ? "" : footer.first().innerText().trim();
    }

    public void clickPageNumber(int pageNumber) {
        CaseLog.step("Clicking page " + pageNumber + ".");
        page.getByText(String.valueOf(pageNumber), new Page.GetByTextOptions().setExact(true)).first().click();
        page.waitForTimeout(800);
    }

    public void clickNextPage() {
        CaseLog.step("Clicking Next.");
        nextPageButton.first().click();
        page.waitForTimeout(800);
    }

    public void clickPreviousPage() {
        CaseLog.step("Clicking Previous.");
        previousPageButton.first().click();
        page.waitForTimeout(800);
    }

    public boolean isNextPageDisabled() {
        Locator btn = nextPageButton.first();
        return btn.count() == 0 || "true".equals(btn.getAttribute("disabled")) || btn.isDisabled();
    }

    public boolean isPreviousPageDisabled() {
        Locator btn = previousPageButton.first();
        return btn.count() == 0 || "true".equals(btn.getAttribute("disabled")) || btn.isDisabled();
    }

    /** Clicks Export and returns the downloaded file's suggested name - "" if nothing downloaded within 10s. */
    public String exportCsv() {
        CaseLog.step("Clicking Export.");
        try {
            Download download = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(10_000), exportButton::click);
            return download.suggestedFilename();
        } catch (com.microsoft.playwright.PlaywrightException noDownload) {
            CaseLog.evidence("Export did not trigger a download within 10s: " + noDownload.getMessage());
            return "";
        }
    }

    // ---------------------------------------------------------------
    // Summary cards
    // ---------------------------------------------------------------

    /** A summary card's headline value, by its label ("Codes sent", "Verified", "Failed delivery", "Limit reached", "Verified, not registered"). */
    public String summaryCard(String label) {
        Locator card = page.getByText(label, new Page.GetByTextOptions().setExact(true)).first();
        if (card.count() == 0) {
            return "";
        }
        Locator value = card.locator("xpath=following-sibling::*[1]");
        return value.count() == 0 ? "" : value.first().innerText().trim();
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

    public boolean isSortedByTimeDescending() {
        int column = headers().indexOf("Time");
        if (column < 0) {
            return false;
        }
        String previous = null;
        for (int i = 0; i < rows.count(); i++) {
            String text = row(i).getOrDefault("Time", "");
            if (text.isBlank()) {
                continue;
            }
            if (previous != null && text.compareTo(previous) > 0) {
                return false;
            }
            previous = text;
        }
        return true;
    }

    public boolean hasAction(int index, String action) {
        return rows.nth(index).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(action)).count() > 0
                || rows.nth(index).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(action)).count() > 0;
    }

    public SmsRecordDetailPage clickView(int index) {
        CaseLog.step("Clicking View on row " + index + ".");
        rows.nth(index).getByText("View", new Locator.GetByTextOptions().setExact(true)).first().click();
        SmsRecordDetailPage detail = new SmsRecordDetailPage(page);
        detail.awaitOpen();
        return detail;
    }

    public SmsRawDataModalPage clickShowMore(int index) {
        CaseLog.step("Clicking Show more on row " + index + ".");
        rows.nth(index).getByText("Show more", new Locator.GetByTextOptions().setExact(true)).first().click();
        SmsRawDataModalPage modal = new SmsRawDataModalPage(page);
        modal.awaitOpen();
        return modal;
    }

    public SmsBlockModalPage clickBlock(int index) {
        CaseLog.step("Clicking Block on row " + index + ".");
        rows.nth(index).getByText(Pattern.compile("^Block$")).first().click();
        SmsBlockModalPage modal = new SmsBlockModalPage(page);
        modal.awaitOpen();
        return modal;
    }

    public void clickUnblock(int index) {
        CaseLog.step("Clicking Unblock on row " + index + ".");
        rows.nth(index).getByText("Unblock", new Locator.GetByTextOptions().setExact(true)).first().click();
        page.waitForTimeout(500);
    }
}
