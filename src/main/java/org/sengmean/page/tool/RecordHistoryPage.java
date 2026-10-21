package org.sengmean.page.tool;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * <b>Tool &gt; Notification &gt; Record History</b> - one row per actual send - and the record's
 * <b>Notification Record Details</b> view.
 *
 * <h2>What the captures show</h2>
 * {@code docs/mockups/notification/notification-record-history.png}:
 * <pre>
 *   [Notification] [Record History]
 *   Show [50] entries  Status: [--- All --- v]  From: [2026-01-01]  To: [2026-01-24]  Language: [繁體 v]
 *   ID    Notification Title                  Type    Agent Group  Member Group  Released At       Status   Actions
 *   5081  Daily mission promotion reminder    System  agents001    Default沒有優惠  2026-01-24 12:00  Sent     [eye]
 *   5078  New login from unknown device alert System  agents003    Default沒有優惠  2026-01-23 11:45  Pending  [eye]
 *   5077  Maintenance window heads-up notice  System  All          All           2026-01-20 18:00  Failed   [eye]
 * </pre>
 * {@code notification-record-details.png} - reached by the eye icon:
 * <pre>
 *   [← Back to List]                                   Home Page &gt; Tool &gt; Notification &gt; View
 *   Notification Record Details
 *   ID 5081   Type System   Schedule Once   Timer —   Status Sent
 *   [繁體] [简体] [English] ... [Portuguese]
 *   Title / Content / Link / Released At
 *   Target Delivery Groups   Agent Group [agent80301] [agent14002] ...   Member Group [不限組別] ...
 * </pre>
 *
 * <h2>Measured against the shipped screen (2026-09-28)</h2>
 * The tab is {@code /tools/notification?tab=history}. Its filter bar and table are the Notification
 * tab's ({@code .notif-filter-group}, {@code table.custom_table}); From and To are native
 * {@code input type=date}; Status offers --- All --- / Sent / Pending / Failed; the columns are ID,
 * Notification Title, Type, Agent Group, Member group, Released At, Status, Actions. The list was
 * empty on that date, so the row's eye action and the <b>Record Details</b> view are still as the
 * capture drew them and remain to be confirmed once a notification has actually been sent.
 */
public class RecordHistoryPage extends BasePage {

    private final Locator rows;
    private final Locator headerCells;

    public RecordHistoryPage(Page page) {
        super(page);
        // Scoped to the table headed "Released At": the Notification tab's rules table shares the class and can sit
        // in the DOM beside this one, and its rule row for the same title counted as a second history entry
        // (NOT-RHL-006 read 2 for a Once rule that had sent once, 2026-09-30).
        // The visible one, preferring the one headed "Released At" - so it is also found under the 中文 interface,
        // where that header reads differently (NOT-I18N-006).
        Locator visible = page.locator("table.custom_table:visible");
        Locator byHeader = visible.filter(new Locator.FilterOptions().setHas(page.locator("thead th").filter(new Locator.FilterOptions().setHasText(Pattern.compile("Released", Pattern.CASE_INSENSITIVE)))));
        Locator historyTable = byHeader.or(visible).first();
        this.rows = historyTable.locator("tbody tr").filter(new Locator.FilterOptions().setHas(page.locator("td")));
        this.headerCells = historyTable.locator("thead th");
    }

    /** Waits for the history table, told from the rules table by its Released At column. */
    public void awaitOpen() {
        try {
            page.waitForCondition(() -> columnIndex("released") >= 0 || (page.url().contains("tab=history") && headerCells.count() > 0),
                    new Page.WaitForConditionOptions().setTimeout(15_000));
        } catch (RuntimeException notYet) {
            // isShowing() reports it.
        }
        page.waitForTimeout(600);
    }

    public boolean isShowing() {
        return (columnIndex("released") >= 0 && columnIndex("notification title") >= 0) || (page.url().contains("tab=history") && headerCells.count() > 0);
    }

    // ---------------------------------------------------------------
    // The list
    // ---------------------------------------------------------------

    public record HistoryRow(String id, String title, String type, String agentGroup, String memberGroup,
                             String releasedAt, String status, String actions) {

        /** "2026-01-24" out of "2026-01-24 12:00". */
        public String releasedDate() {
            return releasedAt.length() >= 10 ? releasedAt.substring(0, 10) : releasedAt;
        }
    }

    public List<HistoryRow> entries() {
        int id = columnIndex("id");
        int title = columnIndex("notification title");
        int type = columnIndex("type");
        int agent = columnIndex("agent");
        int member = columnIndex("member");
        int released = columnIndex("released");
        int status = columnIndex("status");
        int actions = columnIndex("actions");
        List<HistoryRow> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() < 3) {
                continue;
            }
            String entryId = cell(cells, id);
            if (!entryId.isEmpty() && !seen.add(entryId)) {
                continue;   // an entry rendered twice (a fixed-header clone, a responsive child row) is one send
            }
            out.add(new HistoryRow(entryId, cell(cells, title), cell(cells, type), cell(cells, agent),
                    cell(cells, member), cell(cells, released), cell(cells, status), actionsOf(cells, actions)));
        }
        return out;
    }

    /** The entries for one notification title, newest first as the list orders them. */
    public List<HistoryRow> entriesFor(String title) {
        return entries().stream().filter(e -> e.title().equals(title)).toList();
    }

    public List<String> columnHeaders() {
        return headerCells.allInnerTexts().stream().map(t -> t.replaceAll("\\s+", " ").trim()).toList();
    }

    /**
     * The Actions cell as words. The shipped screen draws the View action as an eye icon with no text
     * (measured 2026-09-30), so a cell holding a control reads "View"; any text the cell does carry is kept.
     */
    private static String actionsOf(Locator cells, int index) {
        if (index < 0 || index >= cells.count()) {
            return "";
        }
        String text = cell(cells, index);
        boolean hasControl = cells.nth(index).locator("a, button, i, svg, [class*=eye]").count() > 0;
        if (hasControl && !text.toLowerCase(Locale.ROOT).contains("view")) {
            return text.isEmpty() ? "View" : text + " View";
        }
        return text;
    }

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

    // ---------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------

    /**
     * The control in the {@code .notif-filter-group} labelled {@code label} - "Status:", "From:", "To:",
     * "Language:" - as on the Notification tab: a select, or for From / To an {@code input type=date}.
     */
    private Locator filter(String label, String tag) {
        return page.locator(".notif-filter-group").filter(new Locator.FilterOptions().setHas(
                        page.locator("span.notif-filter-label").filter(new Locator.FilterOptions()
                                .setHasText(Pattern.compile("^\\s*" + label + "\\s*:?\\s*$", Pattern.CASE_INSENSITIVE)))))
                .locator(tag).first();
    }

    /** "--- All ---", "Sent", "Pending" or "Failed". */
    public void filterStatus(String option) {
        CaseLog.step("Setting the Record History Status filter to " + option + ".");
        selectByText(filter("Status", "select"), option);
    }

    public List<String> statusFilterOptions() {
        return optionTexts(filter("Status", "select"));
    }

    public String statusFilter() {
        return String.valueOf(filter("Status", "select").evaluate("s => s.options[s.selectedIndex]?.textContent?.trim() ?? ''"));
    }

    /**
     * From / To in yyyy-MM-dd - what the shipped {@code input type=date} takes as its value. Set by
     * value and announced with input/change, as the Vue model needs; the bar has no Search button, so
     * the list follows the change.
     */
    public void filterDates(String from, String to) {
        CaseLog.step("Setting the Record History range to " + from + " .. " + to + ".");
        setDate(filter("From", "input"), from);
        setDate(filter("To", "input"), to);
        Locator search = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^\\s*Search\\s*$", Pattern.CASE_INSENSITIVE)));
        if (search.count() > 0) {
            search.first().click();
        }
        page.waitForTimeout(1500);
    }

    public String fromDate() {
        return filter("From", "input").inputValue();
    }

    public String toDate() {
        return filter("To", "input").inputValue();
    }

    private void setDate(Locator input, String value) {
        input.evaluate("(i, v) => { i.value = v; i.dispatchEvent(new Event('input', {bubbles: true}));"
                + " i.dispatchEvent(new Event('change', {bubbles: true})); }", value);
    }

    public void filterLanguage(String option) {
        CaseLog.step("Setting the Record History Language filter to " + option + ".");
        selectByText(filter("Language", "select"), option);
    }

    private List<String> optionTexts(Locator select) {
        if (select.count() == 0) {
            return List.of();
        }
        Object raw = select.locator("option").evaluateAll("os => os.map(o => o.textContent.trim())");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private void selectByText(Locator select, String text) {
        Object value = select.evaluate("(s, l) => [...s.options].find(o => o.textContent.trim() === l)?.value ?? null", text);
        if (value == null) {
            throw new IllegalArgumentException("No option \"" + text + "\"; the select offers " + optionTexts(select));
        }
        select.selectOption(String.valueOf(value));
        page.waitForTimeout(1200);
    }

    // ---------------------------------------------------------------
    // Page size and pager - the same DataTables controls as the Notification tab (NOT-RHL-008, 2026-09-30)
    // ---------------------------------------------------------------

    /** "Common 190 Unit， Show page 1 Page" - total, then page number, as on the Notification tab. */
    private static final Pattern PAGE_INFO =
            Pattern.compile("Common\\s*(\\d+)\\s*Unit.*?Show page\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private Locator pageSizeSelect() {
        return filter("Show", "select");
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
        CaseLog.step("Showing " + size + " history entries per page.");
        for (String option : optionTexts(pageSizeSelect())) {
            if (option.replaceAll("\\D", "").equals(String.valueOf(size))) {
                selectByText(pageSizeSelect(), option);
                return;
            }
        }
        throw new IllegalArgumentException("The history page-size selector offers no " + size + "; it offers " + optionTexts(pageSizeSelect()));
    }

    /** {from, to, total} worked out from the visible info line and the page size, or null when there is none. */
    public int[] showing() {
        Locator info = page.locator(".dataTables_info:visible").first();
        if (info.count() == 0) {
            return null;
        }
        java.util.regex.Matcher m = PAGE_INFO.matcher(info.innerText());
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

    private Locator pager(String label) {
        String css = label.equals("Prev") ? "previous" : label.toLowerCase(Locale.ROOT);
        return page.locator(".dataTables_paginate:visible a.paginate_button." + css).first();
    }

    public boolean hasPagerControl(String label) {
        Locator control = pager(label);
        return control.count() > 0 && control.isVisible();
    }

    /** Decided from the info line, as on the Notification tab - Next never carries "disabled" there. */
    public boolean hasNextPage() {
        if (!hasPagerControl("Next")) {
            return false;
        }
        int[] shown = showing();
        return shown != null ? shown[1] < shown[2] : !String.valueOf(pager("Next").getAttribute("class")).contains("disabled");
    }

    public void nextPage() {
        turnPage("Next");
    }

    public void previousPage() {
        turnPage("Prev");
    }

    /** Clicks a pager control and waits for the info line to move and two reads of the IDs to agree. */
    private void turnPage(String label) {
        CaseLog.step("Clicking " + label + " on Record History.");
        int[] before = showing();
        pager(label).click();
        for (int i = 0; i < 20 && before != null; i++) {
            int[] now = showing();
            if (now != null && now[0] != before[0]) {
                break;
            }
            page.waitForTimeout(250);
        }
        List<String> previous = ids();
        for (int i = 0; i < 12; i++) {
            page.waitForTimeout(400);
            List<String> now = ids();
            if (now.equals(previous)) {
                return;
            }
            previous = now;
        }
    }

    private List<String> ids() {
        return entries().stream().map(HistoryRow::id).toList();
    }

    // ---------------------------------------------------------------
    // Record details
    // ---------------------------------------------------------------

    /** Clicks the eye icon on the entry with this ID. */
    public void openDetail(String id) {
        CaseLog.step("Opening the details of Record History entry " + id + ".");
        PromptMessage.clearStray(page);
        Locator row = rows.filter(new Locator.FilterOptions().setHas(page.locator("td").filter(
                new Locator.FilterOptions().setHasText(exactly(id, false))))).first();
        row.locator("td").last().locator("a, button").first().click();
        page.getByText(Pattern.compile("Notification Record Details", Pattern.CASE_INSENSITIVE)).first()
                .waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        page.waitForTimeout(500);
    }

    public boolean isDetailShowing() {
        return page.getByText(Pattern.compile("Notification Record Details", Pattern.CASE_INSENSITIVE)).count() > 0;
    }

    /**
     * The value under a caption on the details view: "ID", "Type", "Schedule", "Timer", "Status", "Released At", "Title",
     * "Content", "Link". An empty field is drawn as a placeholder dash - NOT-EDT-013 read "-" for a Link that had been
     * removed (2026-10-01) and failed on it - so a value that is only a dash comes back as "", and a caller can ask
     * "is there no value" without knowing how the view draws one.
     */
    public String detail(String caption) {
        Locator label = page.locator("label, dt, .detail-label, small, div, span").filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile("^\\s*" + caption + "\\s*$", Pattern.CASE_INSENSITIVE))).last();
        if (label.count() == 0) {
            return "";
        }
        Object value = label.evaluate("l => { let n = l.nextElementSibling; while (n && !n.innerText.trim()) n = n.nextElementSibling;"
                + " return n ? n.innerText.trim() : ''; }");
        String text = String.valueOf(value).replaceAll("\\s+", " ").trim();
        return isPlaceholder(text) ? "" : text;
    }

    /** A lone "-", "–", "—" or "―": what the details view shows in place of an empty value. */
    private static boolean isPlaceholder(String text) {
        return text.matches("[-\\u2013\\u2014\\u2015]+");
    }

    /**
     * The details view's language tabs. Measured 2026-09-30: they are the form's {@code ul.notif-lang-tabs}, not
     * Bootstrap's {@code ul.nav-tabs} - the old locator matched nothing and NOT-RHD-001 / NOT-I18N-001 timed out on it.
     */
    private Locator languageTabs() {
        return page.locator("ul.notif-lang-tabs a, ul.nav-tabs a, [role='tab']");
    }

    /** Title, Content and Link as sent, on one language tab. */
    public String[] sentContent(String languageTab) {
        languageTabs().filter(new Locator.FilterOptions().setHasText(exactly(languageTab, false))).first().click();
        page.waitForTimeout(300);
        return new String[]{detail("Title"), detail("Content"), detail("Link")};
    }

    public List<String> detailLanguageTabs() {
        return languageTabs().allInnerTexts().stream().map(String::trim)
                .filter(t -> NotificationFormPage.LANGUAGE_TABS.contains(t)).toList();
    }

    /** The tags under "Agent Group" or "Member Group" in Target Delivery Groups. */
    public List<String> targetTags(String caption) {
        Locator label = page.getByText(exactly(caption, true)).last();
        if (label.count() == 0) {
            return List.of();
        }
        Object raw = label.evaluate("l => { let n = l.nextElementSibling; return n ? [...n.querySelectorAll('span, .badge, .tag, li')]"
                + ".map(t => t.innerText.trim()).filter(Boolean) : []; }");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).distinct().toList() : List.of();
    }

    /** Anything the details view shows about why a Failed entry failed, or offers to retry it. */
    public String failureInformation() {
        Locator reasons = page.getByText(Pattern.compile("reason|error|retry|resend|connection", Pattern.CASE_INSENSITIVE));
        return String.join(" | ", reasons.allInnerTexts()).replaceAll("\\s+", " ").trim();
    }

    public void backToList() {
        CaseLog.step("Clicking Back to List.");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Back to List", Pattern.CASE_INSENSITIVE)))
                .or(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("Back to List", Pattern.CASE_INSENSITIVE))))
                .first().click();
        awaitOpen();
    }
}
