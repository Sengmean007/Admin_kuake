package org.sengmean.page.tool;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Tool &gt; Notification</b> - the Notification tab's list of rules.
 *
 * <h2>What the capture shows</h2>
 * {@code docs/mockups/notification/notification-list.png}:
 * <pre>
 *   Notification                                       Home Page &gt; Tool &gt; Notification
 *   [Notification] [Record History]
 *   [+ Add notification]
 *   Show [50] entries   Status: [Active v]   Type: [--- All --- v]   Language: [繁體 v]
 *   ID     Title                     Type    Agent Group  Member Group  Schedule  Timer      Status    Created At        Actions
 *   10025  Daily mission promotion…  System  agents001    Default沒有優惠  Daily     12 AM      Active    2026-01-24 10:00  [pencil][trash][↻]
 *   10024  VIP upgrade notification… System  agent8001    老客戶有優惠群組   Once      Immediate  Inactive  2026-01-23 15:30
 *   10022  Maintenance window he…    System  All          All           Once      03 AM      Inactive  2026-01-20 18:00
 *   Showing 1 to 5 of 5 entries                          [First] [Prev] [1] [2] [3] [Next] [Last]
 * </pre>
 * The three row actions are icons: Edit (pencil), Delete (red trash) and <b>Send Now</b> (circular
 * arrow - AQ-03, answered 2026-09-26). A Once row's Timer reads <b>Immediate</b>.
 *
 * <h2>Measured against the shipped screen (2026-09-28)</h2>
 * Written ahead of the screen on 2026-09-26, then checked against it once it shipped:
 * <ul>
 *   <li>The list is at {@code /tools/notification}; its two tabs are links to {@code ?tab=notification}
 *       and {@code ?tab=history}.</li>
 *   <li><b>Add notification</b> is {@code a.notif-add-btn}, linking to {@code /tools/notification-form}.</li>
 *   <li>The filter bar is the site's own, not DataTables': each filter is a {@code .notif-filter-group}
 *       holding a {@code span.notif-filter-label} ("Show", "Status:", "Type:", "Language:") and its
 *       select. A second, hidden "Show [] Entry" select sits before it and must not be used.</li>
 *   <li>The table is {@code table.custom_table}; an empty list is one {@code td[colspan]} reading "No data".</li>
 *   <li>The row actions are {@code .notif-actions}: Edit is a pencil link to
 *       {@code notification-form?id=N}, Delete a red {@code .notif-action-btn--danger} button, and Send
 *       Now a button titled "Send Now".</li>
 *   <li>The pager is DataTables' First / Previous / Next / Last, and its info line reads "Common 1 Unit，
 *       Show page 1 Page", as on Other Category - there is no "Showing X to Y of Z".</li>
 *   <li>Delete asks "Confirm deletion?" (Confirm / Cancel), then says "Successfully deleted".</li>
 * </ul>
 */
public class NotificationListPage extends BasePage {

    public static final String HEADING = "Notification";

    /** The pager's info line: "Common1 Unit， Show page 1 Page， Common 1 Page" - total, then page number. */
    private static final Pattern PAGE_INFO =
            Pattern.compile("Common\\s*(\\d+)\\s*Unit.*?Show page\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final Locator rows;
    private final Locator headerCells;

    public NotificationListPage(Page page) {
        super(page);
        this.rows = page.locator("table.custom_table tbody tr").filter(new Locator.FilterOptions().setHas(page.locator("td")));
        this.headerCells = page.locator("table.custom_table thead th");
    }

    // ---------------------------------------------------------------
    // Getting there
    // ---------------------------------------------------------------

    /**
     * Opens the Notification tab: through Tool &gt; Notification in the side navigation when the
     * menu offers it, by {@code test.notification.path} otherwise.
     */
    public void open() {
        CaseLog.step("Opening Tool > Notification.");
        Locator menu = page.locator("a[href$='" + AdminShellPage.NOTIFICATION_HREF + "']").first();
        String href = menu.count() > 0 ? menu.getAttribute("href") : null;
        if (href != null && !href.isBlank() && !href.startsWith("javascript") && !href.equals("#")) {
            page.navigate(href.startsWith("http") ? href : url(href.replaceFirst("^/", "")));
        } else {
            open(AdminShellPage.NOTIFICATION_HREF.replaceFirst("^/", ""));
        }
        awaitReady();
        try {
            addButton().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
            headerCells.first().waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        } catch (RuntimeException notThere) {
            // isShowing() answers the question; the probe in the test decides what it means.
        }
        page.waitForTimeout(600);
    }

    /** True when the screen is the Notification list: its heading, Add notification, and the table. */
    public boolean isShowing() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING).setExact(true)).count() > 0
                && addButton().count() > 0
                && columnIndex("title") >= 0;
    }

    /** "Home Page / Tool / Notification". */
    public String breadcrumb() {
        Locator crumbs = page.locator(".breadcrumb li, .breadcrumb a, .breadcrumb span");
        List<String> parts = new ArrayList<>();
        for (String text : crumbs.allInnerTexts()) {
            String t = text.replace(">", "").trim();
            if (!t.isEmpty() && !parts.contains(t)) {
                parts.add(t);
            }
        }
        return String.join(" / ", parts);
    }

    /** The two tabs across the top: "Notification" and "Record History". */
    public List<String> tabLabels() {
        return page.locator("ul.nav-tabs a.tab-link").allInnerTexts().stream()
                .map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    /** Switches to the Record History tab. */
    public RecordHistoryPage openRecordHistory() {
        CaseLog.step("Switching to the Record History tab.");
        PromptMessage.clearStray(page);
        // By its ?tab=history link first, so it is found under the 中文 interface too (NOT-I18N-006); by its
        // English label when the link carries no query.
        page.locator("ul.nav-tabs a.tab-link[href*='tab=history']").or(tab("Record History")).first().click();
        RecordHistoryPage history = new RecordHistoryPage(page);
        history.awaitOpen();
        return history;
    }

    /** The tab links: {@code ?tab=notification} and {@code ?tab=history}. */
    private Locator tab(String label) {
        return page.locator("ul.nav-tabs a.tab-link")
                .filter(new Locator.FilterOptions().setHasText(exactly(label, true))).first();
    }

    // ---------------------------------------------------------------
    // Reading the table
    // ---------------------------------------------------------------

    /** One row as the table shows it. Agent/Member Group read "All" for a broadcast. */
    public record NotificationRow(String id, String title, String type, String agentGroup, String memberGroup,
                                  String schedule, String timer, String status, String createdAt) {
    }

    public List<NotificationRow> notifications() {
        int id = columnIndex("id");
        int title = columnIndex("title");
        int type = columnIndex("type");
        int agent = columnIndex("agent");
        int member = columnIndex("member");
        int schedule = columnIndex("schedule");
        int timer = columnIndex("timer");
        int status = columnIndex("status");
        int created = columnIndex("created");
        List<NotificationRow> out = new ArrayList<>();
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() < 3) {
                continue;   // the empty list's single "No data" cell
            }
            out.add(new NotificationRow(cell(cells, id), fullTitle(cells, title), cell(cells, type), cell(cells, agent),
                    cell(cells, member), cell(cells, schedule), cell(cells, timer), cell(cells, status), cell(cells, created)));
        }
        return out;
    }

    public List<String> titles() {
        return notifications().stream().map(NotificationRow::title).toList();
    }

    /** The row with this title, or null. Titles the capture truncates are read from their tooltip. */
    public NotificationRow notification(String title) {
        for (NotificationRow row : notifications()) {
            if (row.title().equals(title)) {
                return row;
            }
        }
        return null;
    }

    public boolean isListed(String title) {
        return notification(title) != null;
    }

    public int rowCount() {
        return notifications().size();
    }

    /** The column headers left to right, for NOT-LST-001. */
    public List<String> columnHeaders() {
        return headerCells.allInnerTexts().stream().map(t -> t.replaceAll("\\s+", " ").trim()).toList();
    }

    /**
     * {from, to, total}, as the capture's "Showing 1 to 5 of 5 entries" gave them. The shipped info
     * line states only the total and the page number ("Common 5 Unit， Show page 1 Page"), so from and
     * to are worked out from those and the selected page size. Null when the line is not shown.
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

    /** Column index by the start of its header text, or -1. */
    private int columnIndex(String startsWith) {
        for (int i = 0; i < headerCells.count(); i++) {
            if (headerCells.nth(i).innerText().trim().toLowerCase(Locale.ROOT).startsWith(startsWith)) {
                return i;
            }
        }
        return -1;
    }

    private static String cell(Locator cells, int index) {
        return index < 0 || index >= cells.count() ? "" : cells.nth(index).innerText().replaceAll("\\s+", " ").trim();
    }

    /** The capture truncates long titles ("Daily mission promotion…"); the full one is in the title attribute when it is. */
    private static String fullTitle(Locator cells, int index) {
        if (index < 0 || index >= cells.count()) {
            return "";
        }
        Locator cell = cells.nth(index);
        Object full = cell.evaluate("c => (c.querySelector('[title]')?.getAttribute('title') || c.getAttribute('title') || c.innerText || '').trim()");
        return String.valueOf(full).replaceAll("\\s+", " ").trim();
    }

    /** The row whose title is {@code title}. */
    private Locator rowFor(String title) {
        int column = Math.max(columnIndex("title"), 0);
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() > column && fullTitle(cells, column).equals(title)) {
                return rows.nth(i);
            }
        }
        return rows.filter(new Locator.FilterOptions().setHasText(title)).first();
    }

    // ---------------------------------------------------------------
    // Filters, page size, pager
    // ---------------------------------------------------------------

    /**
     * The filter select in the {@code .notif-filter-group} whose label reads {@code label} - "Show",
     * "Status:", "Type:", "Language:". Scoped to the group, so the hidden "Show [] Entry" select before
     * the bar is never picked up.
     */
    private Locator filter(String label) {
        return page.locator(".notif-filter-group").filter(new Locator.FilterOptions().setHas(
                        page.locator("span.notif-filter-label").filter(new Locator.FilterOptions()
                                .setHasText(Pattern.compile("^\\s*" + label + "\\s*:?\\s*$", Pattern.CASE_INSENSITIVE)))))
                .locator("select").first();
    }

    /** "Active" or "Inactive" (or the "All" option). */
    public void filterStatus(String option) {
        CaseLog.step("Setting the Status filter to " + option + ".");
        selectByText(filter("Status"), option);
    }

    /** "--- All ---", "System", and whatever else the Type filter offers. */
    public void filterType(String option) {
        CaseLog.step("Setting the Type filter to " + option + ".");
        selectByText(filter("Type"), option);
    }

    public List<String> typeFilterOptions() {
        return optionTexts(filter("Type"));
    }

    public List<String> statusFilterOptions() {
        return optionTexts(filter("Status"));
    }

    /** The option the Status filter is set to, as shown. */
    public String statusFilter() {
        return selectedText(filter("Status"));
    }

    public String typeFilter() {
        return selectedText(filter("Type"));
    }

    public String languageFilter() {
        return selectedText(filter("Language"));
    }

    private static String selectedText(Locator select) {
        if (select.count() == 0) {
            return "";
        }
        return String.valueOf(select.evaluate("s => s.options[s.selectedIndex]?.textContent?.trim() ?? ''"));
    }

    /** The content language the Title column shows - one of the twelve tab labels. Defaults to 繁體. */
    public void filterLanguage(String option) {
        CaseLog.step("Setting the list's Language filter to " + option + ".");
        selectByText(filter("Language"), option);
    }

    public List<String> languageFilterOptions() {
        return optionTexts(filter("Language"));
    }

    /** The "Show [50] entries" sizes, smallest first. */
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
        CaseLog.step("Showing " + size + " entries per page.");
        for (String option : optionTexts(pageSizeSelect())) {
            if (option.replaceAll("\\D", "").equals(String.valueOf(size))) {
                selectByText(pageSizeSelect(), option);
                return;
            }
        }
        throw new IllegalArgumentException("The page-size selector offers no " + size + "; it offers " + optionTexts(pageSizeSelect()));
    }

    private Locator pageSizeSelect() {
        return filter("Show");
    }

    /**
     * True when there is a page after this one. Decided from the info line (rows shown so far against
     * the total), not from Next's class: measured 2026-09-28, Next never carries {@code disabled} - it
     * read enabled on a list of one row on one page.
     */
    public boolean hasNextPage() {
        Locator next = pager("Next");
        if (next.count() == 0 || !next.isVisible()) {
            return false;
        }
        int[] shown = showing();
        if (shown != null) {
            return shown[1] < shown[2];
        }
        return !String.valueOf(next.getAttribute("class")).contains("disabled")
                && !"true".equals(next.getAttribute("aria-disabled"));
    }

    public void nextPage() {
        turnPage("Next");
    }

    public void previousPage() {
        turnPage("Prev");
    }

    /** The pager's First control, back to page 1 from wherever the list is. */
    public void firstPage() {
        turnPage("First");
    }

    public void lastPage() {
        turnPage("Last");
    }

    /**
     * Clicks a pager control and waits for the table to finish repainting. A fixed second was not enough:
     * NOT-LST-006 read page 1 after Prev while the rows were still being replaced and saw page 2's first row
     * at the top of an otherwise correct page 1 (2026-09-30, the screenshot taken a moment later showed the
     * page right). So this waits for the info line to move off the page it was on, then for two reads of the
     * titles 400ms apart to agree.
     */
    private void turnPage(String label) {
        CaseLog.step("Clicking " + label + ".");
        int[] before = showing();
        pager(label).click();
        for (int i = 0; i < 20 && before != null; i++) {
            int[] now = showing();
            if (now != null && now[0] != before[0]) {
                break;
            }
            page.waitForTimeout(250);
        }
        awaitTableSettled();
    }

    /** Waits until two reads of the titles 400ms apart agree - the table has stopped repainting. */
    private void awaitTableSettled() {
        List<String> previous = titles();
        for (int i = 0; i < 12; i++) {
            page.waitForTimeout(400);
            List<String> now = titles();
            if (now.equals(previous)) {
                return;
            }
            previous = now;
        }
    }

    /** True when the pager shows the named control - First, Prev, Next or Last. */
    public boolean hasPagerControl(String label) {
        Locator control = pager(label);
        return control.count() > 0 && control.isVisible();
    }

    /**
     * DataTables' pager anchors - First / Previous / Next / Last - by class: they carry no href, so no
     * link role. The capture's "Prev" is the {@code previous} anchor.
     */
    private Locator pager(String label) {
        String css = label.equals("Prev") ? "previous" : label.toLowerCase(Locale.ROOT);
        return page.locator(".dataTables_paginate a.paginate_button." + css).first();
    }

    private List<String> optionTexts(Locator select) {
        if (select.count() == 0) {
            return List.of();
        }
        Object raw = select.locator("option").evaluateAll("os => os.map(o => o.textContent.trim())");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /** By trimmed option text, since these options are padded and Playwright's label match is exact. */
    private void selectByText(Locator select, String text) {
        Object value = select.evaluate("(s, l) => [...s.options].find(o => o.textContent.trim() === l)?.value ?? null", text);
        if (value == null) {
            throw new IllegalArgumentException("No option \"" + text + "\"; the select offers " + optionTexts(select));
        }
        select.selectOption(String.valueOf(value));
        page.waitForTimeout(1200);
    }

    // ---------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------

    /**
     * "+ Add notification": {@code a.notif-add-btn} in the toolbar, linking to the form without an id -
     * found by class, not its wording, which 中文 translates. (A row's Edit links to the same form
     * with {@code ?id=N}, so the address alone is not enough.)
     */
    private Locator addButton() {
        return page.locator("section.content a.notif-add-btn").first();
    }

    public NotificationFormPage clickAdd() {
        CaseLog.step("Clicking + Add notification.");
        PromptMessage.clearStray(page);
        addButton().click();
        NotificationFormPage form = new NotificationFormPage(page);
        form.awaitOpen();
        return form;
    }

    public NotificationFormPage clickEdit(String title) {
        CaseLog.step("Clicking Edit (pencil) on \"" + title + "\".");
        PromptMessage.clearStray(page);
        // The rule as the API hands it to the form, kept as evidence: NOT-EDT-001 found the 简体
        // tab opening empty (2026-09-28), and only this body tells a lost save from a lost load.
        List<Response> answered = new ArrayList<>();
        Consumer<Response> listener = response -> {
            if ("GET".equals(response.request().method()) && RULE_BY_ID.matcher(response.url()).find()) {
                answered.add(response);
            }
        };
        page.onResponse(listener);
        NotificationFormPage form;
        try {
            rowAction(title, 0).click();
            form = new NotificationFormPage(page);
            form.awaitOpen();
            for (int waited = 0; answered.isEmpty() && waited < 3000; waited += 250) {
                page.waitForTimeout(250);   // the form can show before its GET has answered
            }
        } finally {
            page.offResponse(listener);
        }
        for (Response response : answered) {
            String body;
            try {
                body = response.text();
            } catch (RuntimeException unreadable) {
                body = "(unreadable)";
            }
            CaseLog.data("API GET " + response.url().replaceFirst("^https?://[^/]+/", "").replaceFirst("\\?.*$", "")
                    + " answered", body);
            form.servedRule(body);   // NOT-EDT-002 decides from this when the form renders a tab blank
        }
        return form;
    }

    /** {@code api/account/inbox-notification-rule/6} - the one rule the Edit form loads. */
    private static final Pattern RULE_BY_ID = Pattern.compile("/inbox-notification-rule/\\d+(\\?|$)");

    /** Clicks Delete (trash). AQ-04: a confirmation dialog follows, driven by the methods below. */
    public void clickDelete(String title) {
        CaseLog.step("Clicking Delete (trash) on \"" + title + "\".");
        PromptMessage.clearStray(page);
        rowAction(title, 1).click();
        page.waitForTimeout(800);
    }

    /**
     * Clicks Send Now (the circular arrow). AQ-03: it pushes the notification immediately. Whatever
     * the back office then asks or says is answered and returned, so a row can record it.
     */
    public String clickSendNow(String title) {
        CaseLog.step("Clicking Send Now (circular arrow) on \"" + title + "\".");
        PromptMessage.clearStray(page);
        rowAction(title, 2).click();
        String said = PromptMessage.confirmAndRead(page, 4000);
        CaseLog.data("After Send Now", said.isEmpty() ? "(no message)" : said);
        return said;
    }

    /**
     * The three icons in the row's {@code .notif-actions}, left to right: Edit (a pencil link to
     * {@code notification-form?id=N}), Delete (the red {@code --danger} button) and Send Now (the
     * button titled "Send Now", a {@code fa-sync-alt} arrow). By what each is, not by position.
     */
    private Locator rowAction(String title, int which) {
        Locator actions = rowFor(title).locator(".notif-actions");
        String css = switch (which) {
            case 0 -> "a.notif-action-btn[href*='notification-form']";
            case 1 -> "button.notif-action-btn--danger";
            default -> "button.notif-action-btn[title='Send Now'], button.notif-action-btn:has(i.fa-sync-alt)";
        };
        return actions.locator(css).first();
    }

    // ---------------------------------------------------------------
    // The delete confirmation (AQ-04: the platform's own)
    // ---------------------------------------------------------------

    /** The jQuery UI "Prompt message" dialog asking "Confirm deletion?" - Confirm, then Cancel. */
    private Locator confirmDialog() {
        return page.locator(".ui-dialog").filter(new Locator.FilterOptions().setVisible(true))
                .filter(new Locator.FilterOptions().setHas(page.locator(".ui-dialog-buttonset button + button")))
                .first();
    }

    public boolean isDeleteDialogVisible() {
        return confirmDialog().count() > 0;
    }

    public String deleteDialogText() {
        return isDeleteDialogVisible() ? confirmDialog().innerText().replaceAll("\\s+", " ").trim() : "";
    }

    /** Confirm on "Confirm deletion?" - the first of its two buttons - then OK on "Successfully deleted". */
    public void confirmDelete() {
        CaseLog.step("Confirming the deletion.");
        confirmDialog().locator(".ui-dialog-buttonset button").first().click();
        page.waitForTimeout(1500);
        PromptMessage.readAndClose(page, 1500);
    }

    public void cancelDelete() {
        CaseLog.step("Cancelling the deletion.");
        confirmDialog().locator(".ui-dialog-buttonset button").last().click();
        page.waitForTimeout(800);
    }

    /** Deletes a notification if it is listed; true when it was there. */
    public boolean deleteIfListed(String title) {
        if (!isListed(title)) {
            return false;
        }
        clickDelete(title);
        confirmDelete();
        open();
        return true;
    }

    /** Every row keyed by title, for the rows that compare before and after. */
    public Map<String, NotificationRow> snapshot() {
        Map<String, NotificationRow> byTitle = new LinkedHashMap<>();
        for (NotificationRow row : notifications()) {
            byTitle.put(row.title(), row);
        }
        return byTitle;
    }

    /** Every visible text on the screen, for the localization row that looks for untranslated keys. */
    public String screenText() {
        return page.locator("body").innerText();
    }
}
