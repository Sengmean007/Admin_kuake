package org.sengmean.page.rebate;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Page Object for <b>Rebate Management &gt; Rebate report</b>, at {@code /rebate/report}.
 *
 * <h2>This one models the screen that is actually deployed</h2>
 * The rest of {@code org.sengmean.page.rebate} addresses the Figma mockup of the multi-config
 * Rebate Program Setup screen, which has not shipped. This class is the opposite: every locator
 * below was read off the running back office on 2026-09-16, because the Rebate report screen is
 * built and in use. It is what lets {@code RebateReportTest} assert rather than wait.
 *
 * <h2>Everything is addressed by its label</h2>
 * Not one control on this screen has an {@code id} or a {@code name} - the filters are all bare
 * {@code .form-control}, and two of them share a class with the search box. What each one does
 * have is a unique visible label, so every locator here is
 * {@code //label[text()='...']/following::input-or-select[1]}. That is also why the labels are
 * spelled exactly as the screen spells them, "Agent code" and "Member group" included: a label
 * that does not match returns nothing rather than the wrong control.
 *
 * <p><b>Type and Status share one container</b>, as do their two labels, so neither can be found
 * by looking inside a form group - the group holds both. Walking forward from the label is what
 * separates them.
 *
 * <h2>Search and Reset all are inputs, not buttons</h2>
 * {@code <input type="button" value="Search">}, like Save on the setup screen and Log Out in the
 * header. A {@code //button[...'search']} locator matches <b>nothing</b> here. They are addressed
 * by role and accessible name instead, which for an input is its {@code value}.
 */
public class RebateReportPage extends BasePage {

    /** Where the back office serves the rebate report. */
    public static final String PATH = "rebate/report";

    /** The filter controls, each found by walking forward from its own label. */
    private static final String TIME_LIMIT = "//label[normalize-space()='Time limit']/following::input[1]";
    private static final String PRODUCT = "//label[normalize-space()='Product']/following::select[1]";
    private static final String TYPE = "//label[normalize-space()='Type']/following::select[1]";
    private static final String STATUS = "//label[normalize-space()='Status']/following::select[1]";
    private static final String AGENT_CODE = "//label[normalize-space()='Agent code']/following::input[1]";
    private static final String USERNAME = "//label[normalize-space()='Username']/following::input[1]";

    /** The member-group picker: a list of checkboxes with its own Select all / Unselect all. */
    private static final String MEMBER_GROUP_LIST = ".trx-type-list";

    /**
     * The table's own language selector, beside "Show N Entry".
     *
     * <p>Not the {@code #lang} control in the page header, which changes the whole back office.
     * The row the client's case is about - "[Game name] should change according to the selected
     * language" - is this one, which relabels the report's own contents.
     */
    private static final String TABLE_LANGUAGE = "//label[contains(normalize-space(),'Show')]/following::select[last()]";

    private final Locator timeLimit;
    private final Locator product;
    private final Locator type;
    private final Locator status;
    private final Locator agentCode;
    private final Locator username;
    private final Locator memberGroupList;

    public RebateReportPage(Page page) {
        super(page);
        this.timeLimit = page.locator(TIME_LIMIT);
        this.product = page.locator(PRODUCT);
        this.type = page.locator(TYPE);
        this.status = page.locator(STATUS);
        this.agentCode = page.locator(AGENT_CODE);
        this.username = page.locator(USERNAME);
        this.memberGroupList = page.locator(MEMBER_GROUP_LIST).first();
    }

    /** Opens the report and waits until its filters are there to be used. */
    public void open() {
        open(PATH);
        awaitReady();
        product.waitFor();
    }

    /** The screen's own heading, for a case that wants to say it arrived. */
    public String heading() {
        Locator h1 = page.locator("h1").first();
        h1.waitFor();
        return String.valueOf(h1.innerText()).replaceAll("\\s+", " ").trim();
    }

    // -----------------------------------------------------------------
    // Filters
    // -----------------------------------------------------------------

    /** The date range the report is showing, as the field spells it. */
    public String timeRange() {
        return timeLimit.inputValue();
    }

    /** Empties the date range through the field's own <b>Clear</b> control. */
    public void clearTimeRange() {
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Clear")).first().click();
    }

    /**
     * Writes a date range into the field directly, for when <b>Clear</b> will not take.
     *
     * <h2>Why this is not just a fill()</h2>
     * The field is a datepicker's display input: it is populated by the widget rather than typed
     * into, and {@link Locator#fill} either bounces off it or updates the DOM without telling the
     * Vue model, so the search goes on using the range the model still holds. Assigning the value
     * and dispatching {@code input} and {@code change} is what the widget itself does, so the
     * model follows.
     *
     * <p>It exists because {@link #clearTimeRange()} is unreliable: measured 2026-09-22, three
     * clicks 500ms apart left the range sitting at {@code 2026-09-22 00:00:00 - 2026-09-22
     * 23:59:59} and the report answered for that day only. A row that needs the whole ledger
     * cannot be left at the mercy of it.
     *
     * @param range in the field's own format, {@code yyyy-MM-dd HH:mm:ss - yyyy-MM-dd HH:mm:ss}
     */
    public void setTimeRange(String range) {
        timeLimit.evaluate("(el, value) => {"
                + " el.value = value;"
                + " el.dispatchEvent(new Event('input', { bubbles: true }));"
                + " el.dispatchEvent(new Event('change', { bubbles: true }));"
                + "}", range);
        page.waitForTimeout(SETTLE_MS);
    }

    /**
     * A range wide enough to be the whole ledger, in the field's own format.
     *
     * <p>Deliberately absurd at both ends rather than "the last year": a row that reads the whole
     * ledger should not quietly start missing records because the data got older than the window
     * somebody picked.
     */
    public static final String EVERY_DATE = "2000-01-01 00:00:00 - 2099-12-31 23:59:59";

    /** Chooses a game from <b>Product</b> by its visible name. */
    public void selectProduct(String label) {
        product.selectOption(new SelectOption().setLabel(label));
        CaseLog.step("Product = " + label);
    }

    /** Chooses a rebate <b>Type</b>: All, Daily rebate or Real-time rebate. */
    public void selectType(String label) {
        type.selectOption(new SelectOption().setLabel(label));
        CaseLog.step("Type = " + label);
    }

    /** Chooses a <b>Status</b>: All, Unsettle, Settled, Expired or Cancel. */
    public void selectStatus(String label) {
        status.selectOption(new SelectOption().setLabel(label));
        CaseLog.step("Status = " + label);
    }

    /** The visible text of whichever option a filter currently shows. */
    public String selectedProduct() {
        return selectedLabel(product);
    }

    public String selectedType() {
        return selectedLabel(type);
    }

    public String selectedStatus() {
        return selectedLabel(status);
    }

    private static String selectedLabel(Locator select) {
        Object label = select.evaluate(
                "el => el.options[el.selectedIndex] ? el.options[el.selectedIndex].text : ''");
        return String.valueOf(label).trim();
    }

    /** Every option a filter offers, so a case can assert the set rather than one value. */
    public List<String> typeOptions() {
        return optionsOf(type);
    }

    public List<String> statusOptions() {
        return optionsOf(status);
    }

    @SuppressWarnings("unchecked")
    private static List<String> optionsOf(Locator select) {
        Object raw = select.evaluate("el => Array.from(el.options).map(o => o.text.trim())");
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public void setAgentCode(String value) {
        agentCode.fill(value);
    }

    public String agentCode() {
        return agentCode.inputValue();
    }

    public void setUsername(String value) {
        username.fill(value);
    }

    public String username() {
        return username.inputValue();
    }

    /** How many member groups the picker offers. Zero when none are configured. */
    public int memberGroupCount() {
        return memberGroupList.locator("input[type=checkbox]").count();
    }

    /** How many member groups are currently ticked. */
    public int selectedMemberGroupCount() {
        return memberGroupList.locator("input[type=checkbox]:checked").count();
    }

    /** The picker's own <b>Select all</b> / <b>Unselect all</b> links. */
    public void selectAllMemberGroups() {
        page.getByText("Select all", new Page.GetByTextOptions().setExact(true)).first().click();
    }

    public void unselectAllMemberGroups() {
        page.getByText("Unselect all", new Page.GetByTextOptions().setExact(true)).first().click();
    }

    /** Relabels the report's contents through the table's own language selector. */
    public void selectTableLanguage(String label) {
        page.locator(TABLE_LANGUAGE).first().selectOption(new SelectOption().setLabel(label));
        CaseLog.step("Report language = " + label);
    }

    // -----------------------------------------------------------------
    // Running the report
    // -----------------------------------------------------------------

    /** Presses <b>Search</b> and waits for the table to settle. */
    public void search() {
        CaseLog.step("Searching the rebate report.");
        namedButton("Search").click();
        page.waitForTimeout(SETTLE_MS);
    }

    /** Presses <b>Reset all</b>, which puts every filter back to its default. */
    public void resetAll() {
        CaseLog.step("Resetting every filter.");
        namedButton("Reset all").click();
        page.waitForTimeout(SETTLE_MS);
    }

    /**
     * A control addressed by role and accessible name.
     *
     * <p>Both of this screen's actions are {@code input type="button"}, whose accessible name is
     * its {@code value} - so they answer to a role lookup while matching no {@code //button}
     * locator at all.
     */
    private Locator namedButton(String name) {
        return page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(name)).first();
    }

    /** How long the table is given to redraw after a search. */
    private static final double SETTLE_MS = 1200;

    // -----------------------------------------------------------------
    // Results
    // -----------------------------------------------------------------

    /** The result table's column headings, left to right. */
    public List<String> columnHeaders() {
        List<String> headers = new ArrayList<>();
        Locator cells = page.locator("table th");
        for (int i = 0; i < cells.count(); i++) {
            headers.add(String.valueOf(cells.nth(i).innerText()).replaceAll("\\s+", " ").trim());
        }
        return headers;
    }

    /**
     * True when the report came back with at least one member row.
     *
     * <p>An empty report still renders one table row - the "No data" placeholder - so counting
     * {@code tbody tr} answers the wrong question. This asks whether the table is saying it has
     * nothing, which is what the client's negative rows assert and what the positive ones need.
     */
    public boolean hasData() {
        return !noDataShown();
    }

    /** True when the table is showing its "No data" placeholder. */
    public boolean noDataShown() {
        Locator body = page.locator("table tbody");
        if (body.count() == 0) {
            return true;
        }
        String text = String.valueOf(body.first().innerText()).replaceAll("\\s+", " ").trim();
        return text.isEmpty() || text.equalsIgnoreCase("No data");
    }

    /** How many member rows the report is showing, not counting the "No data" placeholder. */
    public int rowCount() {
        return noDataShown() ? 0 : page.locator("table tbody tr").count();
    }

    /**
     * Every value under one column heading, top to bottom, for the rows currently shown.
     *
     * <p>Addressed by the heading's own position rather than by a column index, because the
     * report's columns are relabelled by its table-language selector and a hard-coded index would
     * quietly read a different column after {@link #selectTableLanguage(String)}. An unknown
     * heading comes back empty rather than throwing, so a case can ask whether a column exists at
     * all - which is what {@code RBMP-055} needs to say about the program and rate columns the
     * workbook expects and the report does not have.
     *
     * @param header the heading exactly as {@link #columnHeaders()} reports it
     */
    public List<String> columnValues(String header) {
        int column = columnHeaders().indexOf(header);
        if (column < 0 || noDataShown()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Locator rows = page.locator("table tbody tr");
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("td");
            values.add(cells.count() <= column ? ""
                    : String.valueOf(cells.nth(column).innerText())
                            .replaceAll("\\s+", " ").trim());
        }
        return values;
    }

    /**
     * One row as its cells read, left to right.
     *
     * <p>What a case comparing two searches needs: a whole row is the unit that must not change,
     * and comparing them cell by cell says which column moved when one does.
     */
    public List<String> rowValues(int row) {
        if (noDataShown()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Locator cells = page.locator("table tbody tr").nth(row).locator("td");
        for (int i = 0; i < cells.count(); i++) {
            values.add(String.valueOf(cells.nth(i).innerText()).replaceAll("\\s+", " ").trim());
        }
        return values;
    }

    /** Every row of the report as it currently reads, which is what a "did anything change" row compares. */
    public List<List<String>> allRows() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount(); i++) {
            rows.add(rowValues(i));
        }
        return rows;
    }
}
