package org.sengmean.page.rebate;


import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;

/**
 * Shared base for the two Rebate Program Setup tab contexts —
 * {@link RealTimeRebatePage} and {@link DailyRebatePage}. Both tabs live on
 * the same underlying screen and share the Expire Time field, the global
 * Status control, Save, search/filter, the program list table, and the
 * page-size control. Each subclass adds only what's specific to its own tab:
 * Min Claim vs. Release Time, and its own Create button.
 *
 * <h2>Every locator below was read off the deployed screen on 2026-09-18</h2>
 * This class was first written against a Figma mockup, because the multi-program screen had
 * not been built. It has since shipped, and the placeholder XPaths have been replaced
 * wholesale. The mistakes the mockup版 made are recorded here because each is easy to make
 * again:
 *
 * <ul>
 *   <li>the heading is <b>"Rebate set up"</b>, not "Rebate program setup", so every navigation
 *       that waited for the title waited out its timeout;</li>
 *   <li>the tab strip matched by text also matched the <b>"+ Create Real-time rebate"</b>
 *       button, which contains the tab's own words - two elements, and strict mode refuses;</li>
 *   <li>the label is <b>"Min. Claim"</b>, with a full stop, so a needle of "min claim" missed;</li>
 *   <li>Save and Search are {@code <input type="button">}, not {@code <button>};</li>
 *   <li>the status filter is a <b>native {@code <select>}</b>, not a custom dropdown of
 *       {@code <li>} options, so clicking it open and clicking an option matched nothing;</li>
 *   <li>there is no "Showing X-Y of Z" summary and no Previous/Next buttons. The list offers a
 *       <b>page-size</b> select (20/50/100/150/200) and renders its paging controls only once
 *       there is more than a page of data.</li>
 * </ul>
 *
 * <h2>Three views share one screen, and telling them apart is the whole problem</h2>
 * {@code /rebate/set-up/} renders the Real-time half and the Daily half <b>both at once</b>,
 * hiding one with {@code display: none}, and it swaps the list for the Create/Modify form
 * <b>in place</b> without changing the address. So at any moment the DOM may hold two Expire
 * time fields, two Status selects, two Save buttons, two search boxes and two tables - and
 * after Create is opened, a third table and twenty-one more text inputs.
 *
 * <p>Naive selectors therefore do not merely match too much, they match the <b>wrong screen</b>.
 * {@code input[placeholder]:visible} is the list's search box on the list, and the <em>program
 * name</em> field on the Create form. {@code table.custom_table} is the program list on one and
 * the game-rate table on the other. Both views even carry the same {@code .rebate-info-banner}.
 *
 * <p>Everything here is consequently scoped to one of two roots, each chosen because it exists
 * on exactly one of the views:
 *
 * <ul>
 *   <li>{@link #LIST_ROOT} - the box holding the list, identified by its Create button
 *       ({@code .btn-sm}), which the form does not have;</li>
 *   <li>{@code FORM_ROOT} in {@link RebateProgramFormPage} - the box holding Create/Modify,
 *       identified by its transfer lists ({@code select.select-from}), which the list does not
 *       have.</li>
 * </ul>
 *
 * <p>Both are structural rather than textual, which matters because the localization cases put
 * this screen into Chinese and Thai while these locators still have to resolve.
 *
 * Note on waits: Playwright has no WebDriverWait (that's a Selenium class).
 * waitForVisible()/waitForHidden() are Playwright's own explicit-wait
 * mechanism (Locator#waitFor()), used in its place.
 */
public abstract class BaseRebateProgramPage extends org.sengmean.base.BasePage {

    protected static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    protected static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    /** Reusable case-insensitive text-match fragment for XPath 1.0 (no lower-case()/matches() support). */
    protected static final String TR = "translate(normalize-space(text()), '" + UPPER + "', '" + LOWER + "')";
    protected static final String TR_DOT = "translate(normalize-space(.), '" + UPPER + "', '" + LOWER + "')";
    protected static final double DEFAULT_TIMEOUT_MS = 10_000;

    /** The screen's own heading. Its <em>text</em> is language-dependent; its position is not. */
    private static final String CSS_PAGE_TITLE = "h1";

    /**
     * The tab strip, addressed by position rather than by its labels.
     *
     * <p>Position is the only language-independent handle on it, and it is also the only one that
     * does not collide with the Create button - see the class javadoc.
     */
    private static final String CSS_TAB_STRIP = ".nav-tabs li";
    private static final int REAL_TIME_TAB_INDEX = 0;
    private static final int DAILY_TAB_INDEX = 1;

    /**
     * The list view's own box, and the scope every list control is found inside.
     *
     * <p>Defined as <b>the box that holds a table and is not the Create/Modify form</b>. The form
     * is the one with the group transfer lists, so excluding {@code select.select-from} is what
     * separates them. Counted on 2026-09-18: one match on the Real-time tab, one on the Daily
     * tab, none while the form is open.
     *
     * <h2>It used to be anchored on the Create button's styling, and that broke</h2>
     * The first version was {@code div.box-body:has(button.btn-sm)}, because the Create control
     * carried {@code btn btn-primary btn-sm}. The site later dropped {@code btn-sm} from it - the
     * button is now plain {@code btn btn-primary} - and the anchor stopped matching anything.
     *
     * <p>The damage was out of all proportion to the change, because <b>nothing failed</b>: every
     * list read simply returned "empty". {@code getProgramCount()} answered 0 on a tab that was
     * showing a program, the suite concluded there was nothing there, and the real message - "the
     * screen is not where the page object thinks it is" - never appeared. Hence two things here:
     * an anchor made of structure rather than of a size class, and {@link #requireListShowing()},
     * so that a list which cannot be found says so instead of pretending to be empty.
     */
    public static final String LIST_ROOT =
            "div.box-body:has(.custom_table_wrapper):not(:has(select.select-from)):visible";

    /** Within a settings panel, the three fields sit in three columns, in this order. */
    protected static final String CSS_SETTINGS_COLUMN = ".col-md-2";
    protected static final int EXPIRE_TIME_COLUMN = 1;

    /**
     * What the Status controls store for each word the workbook uses.
     *
     * <p>The options read "Enable" and "Disable" but carry {@code 1} and {@code 0}, and their
     * labels arrive padded with the markup's own whitespace - selecting by label was tried and
     * Playwright answered "did not find some options". Selecting by value avoids the question,
     * and survives the screen being switched into Chinese or Thai.
     */
    static final String STATUS_VALUE_ENABLE = "1";
    static final String STATUS_VALUE_DISABLE = "0";

    /** The list's status filter adds a third option, whose value is the empty string. */
    private static final String STATUS_FILTER_VALUE_ALL = "";

    /**
     * The Action cell's two controls, told apart by colour rather than by wording.
     *
     * <p>Read off a real row on 2026-09-18 (by creating one program and deleting it again):
     * {@code <button class="btn btn-primary btn-xs">Modify</button>} and
     * {@code <button class="btn btn-danger btn-xs">Delete</button>}. Matching on the class is
     * deliberate - {@code .btn-danger} is the only destructive control on the screen, and unlike
     * the words "Modify" and "Delete" it survives the localization cases switching the interface
     * into Chinese or Thai.
     */
    private static final String CSS_MODIFY_BUTTON = "button.btn-primary.btn-xs";
    private static final String CSS_DELETE_BUTTON = "button.btn-danger.btn-xs";

    /**
     * The status cell is a live {@code <select>}, not a word.
     *
     * <p>A surprise worth recording: the list does not print "Active"/"Inactive" for a program,
     * it renders an Enable/Disable dropdown in the row that changes the program's status where it
     * stands. So the status is read with {@code inputValue()} and not from the cell's text, and
     * {@code RBMP-023}'s "toggle a program's Status" is a list action rather than a trip through
     * Modify - see {@link #setProgramStatus(String, String)}.
     */
    private static final String CSS_ROW_STATUS_SELECT = "select";

    /**
     * %s = the program name; matches rows whose <b>name cell</b> is exactly that.
     *
     * <h2>A program name is not unique, and assuming it was cost a run</h2>
     * This used to be a {@code contains()} over the whole row, which matched far too much: a
     * program called "Sample 1" also matched "Sample 10", and the Target Agents and User Groups
     * cells were in scope too, so a name that happened to look like a group matched on that.
     *
     * <p>Anchoring on the first cell fixes the over-matching but not the deeper point, which is
     * that <b>two programs may share a name</b>. Measured 2026-09-18 on the HKD Real-time tab:
     * two separate programs both called {@code TEST}, told apart only by their pairings - which
     * is legitimate, and is exactly what {@code RBMP-020} is about. So callers take
     * {@link #programRow(String)} as "the first program with this name" and can ask
     * {@link #programRowCount(String)} when the distinction matters.
     */
    private static final String XP_PROGRAM_ROW_TEMPLATE = ".//tr[td[1][normalize-space(.)='%s']]";

    /**
     * How the list says it is empty: one row spanning every column.
     *
     * <p>{@code colspan="9999"} is the marker, and it is language-independent, which the word
     * "No data" is not - the Chinese screen says 無資料.
     */
    private static final String CSS_NO_DATA_CELL = "td[colspan]";

    protected final Locator pageTitle;
    protected final Locator realTimeTab;
    protected final Locator dailyTab;

    /** This tab's global settings panel, and the scope every settings control is found within. */
    protected final Locator settingsBox;

    /** The list view's box. See {@link #LIST_ROOT}. */
    protected final Locator listRoot;

    private final Locator expireTimeInput;
    private final Locator globalStatusDropdown;
    private final Locator saveButton;

    private final Locator searchInput;
    private final Locator searchButton;
    private final Locator statusFilterDropdown;
    private final Locator pageSizeSelect;
    private final Locator refreshControl;
    private final Locator infoBanner;
    private final Locator programTable;

    /**
     * @param settingsBoxSelector the id of this tab's global settings panel. Passed in rather
     *                            than read from an overridable method, so that nothing here
     *                            depends on a subclass being constructed first.
     */
    protected BaseRebateProgramPage(Page page, String settingsBoxSelector) {
        // super(page) rather than the no-arg constructor: BasePage's own helpers - awaitReady(),
        // visibleAlertText() - read its `page` field, and this class used to leave it null while
        // shadowing it with a field of its own, so any of them would have thrown.
        super(page);

        this.pageTitle = page.locator(CSS_PAGE_TITLE);
        this.realTimeTab = page.locator(CSS_TAB_STRIP).nth(REAL_TIME_TAB_INDEX);
        this.dailyTab = page.locator(CSS_TAB_STRIP).nth(DAILY_TAB_INDEX);

        this.settingsBox = page.locator(settingsBoxSelector);
        this.expireTimeInput = settingsColumn(EXPIRE_TIME_COLUMN).locator("input");
        this.globalStatusDropdown = settingsBox.locator("select");
        this.saveButton = settingsBox.locator("input.btn-primary[type='button']");

        this.listRoot = page.locator(LIST_ROOT);
        this.searchInput = listRoot.locator("input[type='text'].form-control");
        this.searchButton = listRoot.locator("input[type='button'].btn-primary");
        this.statusFilterDropdown = listRoot.locator("select.form-control");
        this.pageSizeSelect = listRoot.locator(".custom_table_wrapper > div select");
        this.refreshControl = listRoot.locator("i.fa-sync-alt");
        this.infoBanner = listRoot.locator(".rebate-info-banner");
        this.programTable = listRoot.locator("table.custom_table");
    }

    /** The n-th field column of this tab's settings panel, counting from the left. */
    protected Locator settingsColumn(int index) {
        return settingsBox.locator(CSS_SETTINGS_COLUMN).nth(index);
    }

    /** Each subclass waits for the field that only appears once its own tab's content is showing. */
    protected abstract void waitUntilActive();

    // ---------------------------------------------------------------
    // Explicit-wait helpers (Playwright's answer to WebDriverWait —
    // see class javadoc)
    // ---------------------------------------------------------------
    protected void waitForVisible(Locator locator) {
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(DEFAULT_TIMEOUT_MS));
    }

    protected void waitForHidden(Locator locator) {
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(DEFAULT_TIMEOUT_MS));
    }

    /**
     * Waits until a settings field is not merely on the screen but <b>filled in</b>.
     *
     * <h2>Why visible is not enough, and what it cost</h2>
     * The settings panels are rendered before they are populated: the inputs exist and are
     * visible with empty values, and the saved figures arrive about a second later. Measured on
     * 2026-09-18 by polling both panels every second - the Real-time inputs are absent at 0ms and
     * hold {@code 0.05 / 8 / 1} by 1000ms; the Daily ones are present-but-empty at 0ms and hold
     * {@code 1 / 23 / 1} by 1000ms.
     *
     * <p>Waiting only for visibility therefore reads a blank field, and that is not a harmless
     * misread. A case that read blank, typed its own value and pressed Save was writing over a
     * panel it had never seen, and a case that read blank and asserted got "expected 2 but was
     * &lt;&gt;" - which points at the save, when the fault was in the read. Both happened before
     * this wait existed.
     */
    protected void waitUntilPopulated(Locator field) {
        waitForVisible(field);
        page.waitForCondition(() -> !field.inputValue().isBlank(),
                new Page.WaitForConditionOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    }

    /**
     * Opens the screen and waits until its tab strip is there to be clicked.
     *
     * <p>It used to wait for the heading's <em>text</em>, which was both wrong (the screen says
     * "Rebate set up") and unusable by the localization cases, which meet the heading in Chinese
     * and in Thai. The tab strip is the same two elements in every language.
     */
    protected void navigateAndWaitForTitle(String url) {
        page.navigate(url);
        awaitReady();
        waitForVisible(page.locator(CSS_TAB_STRIP).first());
    }

    /** The screen's heading, in whatever language the header is set to. */
    public String heading() {
        waitForVisible(pageTitle.first());
        return pageTitle.first().innerText().trim();
    }

    /** True while the list is the view on screen, rather than the Create/Modify form. */
    public boolean isListShowing() {
        return listRoot.count() > 0;
    }

    /**
     * Fails plainly when the list is not on screen, rather than letting a reader be told it is
     * empty.
     *
     * <p>Every count and every lookup goes through this. "The program list has nothing in it" and
     * "the page object cannot find the program list" are completely different findings, and for
     * one afternoon they were indistinguishable: a styling change to the Create button moved
     * {@link #LIST_ROOT} off the screen and the suite reported empty tabs on an environment with
     * programs on both of them.
     */
    protected void requireListShowing() {
        if (listRoot.count() == 0) {
            throw new IllegalStateException(
                    "The program list is not on screen, so it cannot be read. Either the Create/"
                            + "Modify form is still open, or the markup moved and LIST_ROOT ("
                            + LIST_ROOT + ") no longer matches it. This is deliberately an error "
                            + "rather than an empty list: the two mean very different things.");
        }
    }

    /** The banner above the list: "Every program requires at least one Target Agent...". */
    public String infoBannerText() {
        waitForVisible(infoBanner);
        return infoBanner.innerText().trim();
    }

    // ---------------------------------------------------------------
    // Shared: Expire Time / global Status / Save / validation errors
    // ---------------------------------------------------------------

    /**
     * Waits until this panel has been filled in from the server.
     *
     * <p>Every read and every write below goes through it, because the panel's Status select is
     * the one control whose unpopulated state cannot be told from a real one - an empty text
     * field is visibly empty, but a select that has not loaded yet simply reads {@code 0}, which
     * is also what Disable reads. So the select is never trusted on its own: the panel is
     * considered loaded when its <em>first</em> field has a value, and by then all three do.
     */
    protected void awaitSettingsLoaded() {
        waitUntilPopulated(settingsColumn(0).locator("input"));
    }

    public String getExpireTimeValue() {
        awaitSettingsLoaded();
        return expireTimeInput.inputValue();
    }

    public void setExpireTime(String value) {
        awaitSettingsLoaded();
        expireTimeInput.fill(value);
    }

    /** The word the Status select is showing - "Enable" or "Disable" - rather than its stored 1/0. */
    public String getGlobalStatusValue() {
        awaitSettingsLoaded();
        return STATUS_VALUE_ENABLE.equals(globalStatusDropdown.inputValue()) ? "Enable" : "Disable";
    }

    /** Sets the panel's Status by the workbook's word, translating it to the option's own value. */
    public void setGlobalStatus(String status) {
        awaitSettingsLoaded();
        globalStatusDropdown.selectOption(statusValue(status));
    }

    /** Maps the several words the workbook uses for "off" onto the option value that means it. */
    static String statusValue(String status) {
        return status.equalsIgnoreCase("Disable") || status.equalsIgnoreCase("Inactive")
                ? STATUS_VALUE_DISABLE
                : STATUS_VALUE_ENABLE;
    }

    /**
     * Presses this panel's Save, reads what the screen says about it, and clears the prompt.
     *
     * <h2>Saving is a two-step conversation, and missing that cost most of a suite</h2>
     * Save does not just save: it raises the back office's modal prompt - "Successfully
     * modified", one OK button - and <b>leaves it up</b>. Nothing used to dismiss it, so from the
     * first settings case onward there was an invisible overlay across the screen, and every case
     * after it failed on a timeout while trying to click something behind it. That is what
     * {@code RBMP-008}, {@code RBMP-003}, {@code RBMP-004}, {@code RBMP-005} and the three delete
     * rows were all actually dying of; none of them had anything wrong with it.
     *
     * <p>The message is captured before the prompt is dismissed, because it is the only thing
     * that says whether the save was taken - see {@link #saveWasAccepted()}.
     *
     * <p>Waits for the panel to be loaded first, so that a Save can never write a screenful of
     * blanks over settings the case has not read yet - see {@link #waitUntilPopulated(Locator)}.
     */
    public void clickSave() {
        awaitSettingsLoaded();
        waitForVisible(saveButton);
        saveButton.click();
        page.waitForTimeout(SAVE_SETTLE_MS);

        lastSaveMessage = isConfirmDialogVisible() ? confirmDialogMessage() : "";
        answerPrompt("OK");
    }

    /** What the screen said about the last {@link #clickSave()}. Empty when it said nothing. */
    private String lastSaveMessage = "";

    public String lastSaveMessage() {
        return lastSaveMessage;
    }

    /**
     * Whether the last save was taken.
     *
     * <p>Judged on the prompt's own words. A successful save says "Successfully modified"; a
     * refused one says something else, and an empty message means the screen said nothing at all,
     * which is not a success either.
     */
    public boolean saveWasAccepted() {
        return lastSaveMessage.toLowerCase(java.util.Locale.ROOT).contains("success");
    }

    /**
     * How long Save is given to reach the server before a case navigates away from it.
     *
     * <p>The button fires an XHR and the panel does not visibly change, so there is no state to
     * wait on. Reloading the screen a moment too early cancels the request in flight, and the
     * case then fails on reading back the old value - which looks exactly like the save having
     * been rejected.
     */
    private static final double SAVE_SETTLE_MS = 1500;

    /**
     * Whether the last save was <b>refused</b>.
     *
     * <h2>There is no inline validation on this screen</h2>
     * It was modelled as one - a {@code [role=alert]} or {@code .help-block} beside the field -
     * and there is nothing of the sort: counted zero after saving a deliberately invalid value on
     * 2026-09-18. The screen answers every save the same way, through the modal prompt, and the
     * only difference between accepted and refused is the words in it. So this reads the message
     * {@link #clickSave()} captured rather than looking for an element.
     */
    public boolean isSettingsErrorVisible() {
        return !lastSaveMessage.isEmpty() && !saveWasAccepted();
    }

    /**
     * What the screen said about the last save, refused or not. Empty when it said nothing.
     *
     * <p><b>Never waits.</b> It used to, and that was its own bug: Java builds an assertion's
     * message eagerly, so {@code assertFalse(isSettingsErrorVisible(), "... " + getSettingsErrorMessage())}
     * called this on <em>every</em> run of the case, including the passing ones - where it then
     * waited ten seconds for an element that was never going to exist and failed the case with a
     * timeout. A getter used inside an assertion message must be cheap and must not throw.
     */
    public String getSettingsErrorMessage() {
        return lastSaveMessage;
    }

    // ---------------------------------------------------------------
    // Shared: search / filter / page size
    // ---------------------------------------------------------------

    /**
     * Types into the list's search box and presses the Search button beside it.
     *
     * <p>It used to press Enter instead. There is a real <b>Search</b> button - an
     * {@code <input type="button">} in the column next to the box - and nothing promises that
     * the field submits on Enter, so a search that appeared to do nothing would have been
     * indistinguishable from a filter that matched nothing.
     */
    public void searchProgram(String text) {
        waitForVisible(searchInput);
        searchInput.fill(text);
        clickSearch();
    }

    public void clickSearch() {
        waitForVisible(searchButton);
        searchButton.click();
        page.waitForTimeout(LIST_SETTLE_MS);
    }

    /** How long the list is given to redraw after a search, a filter or a page-size change. */
    private static final double LIST_SETTLE_MS = 1200;

    public String getSearchValue() {
        waitForVisible(searchInput);
        return searchInput.inputValue();
    }

    /** Empties the search box and searches again, which is how the list is restored in full. */
    public void clearSearch() {
        waitForVisible(searchInput);
        searchInput.fill("");
        clickSearch();
    }

    /** True when this tab offers a search box - the control only the multi-program screen has. */
    public boolean isSearchAvailable() {
        return searchInput.count() > 0;
    }

    /**
     * Sets the list's status filter.
     *
     * <p>A native {@code <select>}, chosen by option value: {@code ""} for All statuses,
     * {@code "1"} for Enable and {@code "0"} for Disable. The previous version clicked the
     * control open and then clicked an {@code <li>}, which is what a custom dropdown needs and
     * this is not; it matched nothing and waited out the timeout.
     */
    public void filterByStatus(String status) {
        waitForVisible(statusFilterDropdown);
        statusFilterDropdown.selectOption(filterValue(status));
        clickSearch();
    }

    private static String filterValue(String status) {
        if (status == null || status.isBlank()
                || status.equalsIgnoreCase("All") || status.equalsIgnoreCase("All statuses")) {
            return STATUS_FILTER_VALUE_ALL;
        }
        return statusValue(status);
    }

    /** What the status filter is currently set to, as the option's own value. */
    public String selectedStatusFilter() {
        waitForVisible(statusFilterDropdown);
        return statusFilterDropdown.inputValue();
    }

    /** True when this tab offers the list's status filter. */
    public boolean isStatusFilterAvailable() {
        return statusFilterDropdown.count() > 0;
    }

    /**
     * How many rows the list shows per page.
     *
     * <p>This is what the screen offers in place of the "Showing X-Y of Z" summary the mockup
     * had: a {@code Show [20|50|100|150|200] Entry} select above the table. The paging controls
     * themselves are rendered only when there is more than one page of data, so they cannot be
     * modelled until the environment has enough programs to produce one - see
     * {@link #paginationControls()}.
     */
    public String pageSize() {
        waitForVisible(pageSizeSelect);
        return pageSizeSelect.inputValue();
    }

    public void setPageSize(String rowsPerPage) {
        waitForVisible(pageSizeSelect);
        pageSizeSelect.selectOption(rowsPerPage);
        page.waitForTimeout(LIST_SETTLE_MS);
    }

    /** The page-size options the list offers, by value. */
    public List<String> pageSizeOptions() {
        Object raw = pageSizeSelect.locator("option").evaluateAll("options => options.map(o => o.value)");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /**
     * The list's paging controls, which exist only when there is more than one page.
     *
     * <p>Returned as a locator rather than as a click helper on purpose: with an empty list on
     * every environment seen so far, <b>nothing here has been observed</b>, and inventing method
     * names for buttons nobody has seen is how this class acquired its Previous/Next XPaths in
     * the first place. A case that reaches this point should assert on the count and then the
     * markup can be modelled from what it found.
     */
    public Locator paginationControls() {
        return listRoot.locator(".custom_table_wrapper .pagination, .custom_table_wrapper .paginate_button");
    }

    /** Re-fetches the list without reloading the page. */
    public void refreshList() {
        waitForVisible(refreshControl);
        refreshControl.click();
        page.waitForTimeout(LIST_SETTLE_MS);
    }

    // ---------------------------------------------------------------
    // Shared: program list table
    // ---------------------------------------------------------------

    /** The list's table. Scoped to {@link #LIST_ROOT}, so it is never the game-rate table. */
    protected Locator programTable() {
        return programTable;
    }

    /**
     * Waits until the program list has finished arriving.
     *
     * <h2>The list is fetched separately from the screen, and reading it early lies</h2>
     * The table renders its "No data" row first and is filled in when its own request comes back.
     * <b>An empty list and a not-yet-loaded list look exactly the same</b>, so there is no state
     * to wait for the way {@link #waitUntilPopulated(Locator)} waits for a value - which is why
     * this settles the DOM instead: it polls the table body until it stops changing.
     *
     * <p>Reading too early was not a cosmetic problem. {@code getProgramCount()} answered 0 while
     * the server was returning a program, so the suite concluded the tab was empty, tried to
     * create its fixture, and was refused - the back office answering, correctly,
     * <i>"All Agents and All Groups already exist in \"New Daily Rebate\""</i>. The failure
     * surfaced as "the suite cannot create a Daily program" and the cause was a list read about a
     * second too soon. It also meant the clean-up could not see what it was supposed to remove,
     * so a stray program survived several runs.
     */
    protected void awaitListLoaded() {
        Locator body = programTable.locator("tbody");
        if (body.count() == 0) {
            return;
        }
        String previous = null;
        long deadline = System.currentTimeMillis() + (long) DEFAULT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            String current;
            try {
                current = body.first().innerHTML();
            } catch (RuntimeException stillRendering) {
                page.waitForTimeout(LIST_POLL_MS);
                continue;
            }
            if (current.equals(previous)) {
                return;
            }
            previous = current;
            page.waitForTimeout(LIST_POLL_MS);
        }
    }

    /** How long between two looks at the table while waiting for it to settle. */
    private static final double LIST_POLL_MS = 500;

    /**
     * The list row naming a program.
     *
     * <p>The {@code xpath=} prefix is load-bearing. Playwright sniffs a selector as XPath when it
     * starts with {@code //} or {@code ..}, and this one starts with {@code .//} - which is not
     * on that list, so it was parsed as CSS and threw {@code Unexpected token "/" while parsing
     * css selector}. The engine has to be named explicitly for a relative XPath.
     */
    protected Locator programRow(String programName) {
        return allProgramRows(programName).first();
    }

    /** Every row carrying this name - more than one when two programs share it. */
    protected Locator allProgramRows(String programName) {
        return programTable.locator("tbody")
                .locator("xpath=" + String.format(XP_PROGRAM_ROW_TEMPLATE, programName));
    }

    /**
     * How many programs are listed under a name.
     *
     * <p>Usually one, occasionally more - names are not unique, only pairings are. A case that
     * acts on "the program called X" is really acting on the first of them, and this is how it
     * can tell whether that mattered.
     */
    public int programRowCount(String programName) {
        awaitListLoaded();
        return isListEmpty() ? 0 : allProgramRows(programName).count();
    }

    public boolean isProgramListed(String programName) {
        requireListShowing();
        awaitListLoaded();
        return !isListEmpty() && allProgramRows(programName).count() > 0;
    }

    /** How many programs this tab is listing. An empty list shows one "No data" row, not none. */
    public int getProgramCount() {
        requireListShowing();
        awaitListLoaded();
        return isListEmpty() ? 0 : programTable.locator("tbody tr").count();
    }

    /**
     * True when this tab has no programs at all.
     *
     * <p>Decided on the empty row's {@code colspan}, not on the words in it. The screen writes
     * "No data" in English and 無資料 in Chinese, and a localization case that switched the
     * language would otherwise make an empty list look populated.
     */
    public boolean isListEmpty() {
        Locator body = programTable.locator("tbody");
        if (body.count() == 0) {
            return true;
        }
        return body.locator(CSS_NO_DATA_CELL).count() > 0 || body.locator("tr").count() == 0;
    }

    /** What the list says when it has nothing to show - "No data", or its translation. */
    public String emptyMessage() {
        Locator cell = programTable.locator("tbody " + CSS_NO_DATA_CELL);
        return cell.count() == 0 ? "" : cell.first().innerText().trim();
    }

    /** The list's column headings, left to right, in whatever language the header is set to. */
    public List<String> columnHeaders() {
        return programTable.locator("th").allInnerTexts()
                .stream().map(String::trim).filter(text -> !text.isEmpty()).toList();
    }

    /** "Enable" or "Disable" - read from the row's own select, not from the cell's text. */
    public String getProgramStatus(String programName) {
        Locator row = programRow(programName);
        waitForVisible(row);
        return STATUS_VALUE_ENABLE.equals(rowStatusSelect(row).inputValue()) ? "Enable" : "Disable";
    }

    /**
     * Switches a program between Enable and Disable from the list, without opening Modify.
     *
     * <h2>Choosing the option is only half of it</h2>
     * There is no Save beside the select, which makes it look like the choice is the whole
     * action. It is not: the screen raises its prompt asking <b>"Confirm to modify?"</b>, and a
     * change left unanswered is <b>discarded</b>. Measured on 2026-09-18 - select Disable, and
     * the row reads Disable; reload without answering the prompt, and it is Enable again. A case
     * that only called {@code selectOption} would therefore assert successfully against the
     * screen and then find nothing had been saved, which is the most expensive kind of green.
     */
    public void setProgramStatus(String programName, String status) {
        Locator row = programRow(programName);
        waitForVisible(row);
        rowStatusSelect(row).selectOption(statusValue(status));
        page.waitForTimeout(LIST_SETTLE_MS);
        answerPrompt("Confirm");
    }

    private Locator rowStatusSelect(Locator row) {
        return row.locator(CSS_ROW_STATUS_SELECT).first();
    }

    /** What the row names as the program's targets: "All Agents" when it targets every one. */
    public String getProgramTargetAgents(String programName) {
        return cellText(programName, 1);
    }

    /** As {@link #getProgramTargetAgents(String)}, for the User Groups column. */
    public String getProgramUserGroups(String programName) {
        return cellText(programName, 2);
    }

    /** The row's Last update stamp, e.g. {@code 2026-09-18 11:50:59}. */
    public String getProgramLastUpdate(String programName) {
        return cellText(programName, 4);
    }

    private String cellText(String programName, int column) {
        Locator row = programRow(programName);
        waitForVisible(row);
        return row.locator("td").nth(column).innerText().trim();
    }

    /** Clicks Modify on a program's row, and returns the form that opens. */
    public RebateProgramFormPage clickModify(String programName) {
        Locator row = programRow(programName);
        waitForVisible(row);
        row.locator(CSS_MODIFY_BUTTON).first().click();
        RebateProgramFormPage form = new RebateProgramFormPage(page);
        // Populated rather than merely open: Modify draws its fields empty and fills them about
        // half a second later, exactly as the settings panels do. Reading at the moment it opens
        // gets a blank name and the wrong status - see RebateProgramFormPage.waitUntilPopulated.
        form.waitUntilPopulated();
        return form;
    }

    /** Clicks Delete on a program's row and waits for the confirmation to come up. */
    public void clickDelete(String programName) {
        Locator row = programRow(programName);
        waitForVisible(row);
        row.locator(CSS_DELETE_BUTTON).first().click();
        waitForVisible(confirmDialog().first());
    }

    /**
     * The prompt the back office raises for a confirmation or a result.
     *
     * <h2>It is the sign-in form's dialog, reused</h2>
     * Measured on 2026-09-18 by deleting a program: the confirmation is a <b>jQuery UI
     * dialog</b> - {@code div.ui-dialog[role=dialog]} titled "Prompt message", the question in
     * {@code .ui-dialog-content} and the answers as bare {@code <button>} elements inside
     * {@code .ui-dialog-buttonset}. That is the same widget {@code AdminLoginPage} documents for
     * sign-in messages, so the back office has <b>one</b> dialog component and knowing its shape
     * once is enough for the whole application.
     *
     * <p>Nothing of the sort is in the DOM until it is asked for: with the list merely on screen,
     * {@code [role=dialog]}, {@code .modal}, {@code .swal2-container}, {@code .el-dialog} and
     * anything whose class contains "modal" or "dialog" all match zero elements.
     *
     * <p><b>A delete raises the prompt twice.</b> First "Confirm deletion?" with Confirm and
     * Cancel, then "Successfully deleted" with a single OK - and the second one is modal too, so
     * a case that walks away after confirming leaves it blocking the screen for whatever runs
     * next. {@link #confirmDeletion()} dismisses both.
     */
    public Locator confirmDialog() {
        return page.locator("div.ui-dialog[role='dialog']");
    }

    public boolean isConfirmDialogVisible() {
        Locator dialog = confirmDialog();
        return dialog.count() > 0 && dialog.first().isVisible();
    }

    /** What the prompt is currently asking or reporting. */
    public String confirmDialogMessage() {
        Locator content = confirmDialog().locator(".ui-dialog-content");
        return content.count() == 0 ? "" : content.first().innerText().replaceAll("\\s+", " ").trim();
    }

    /** The answers the prompt is offering, e.g. {@code [Confirm, Cancel]} or {@code [OK]}. */
    public List<String> confirmDialogButtons() {
        return confirmDialog().locator(".ui-dialog-buttonset button").allInnerTexts()
                .stream().map(String::trim).filter(text -> !text.isEmpty()).toList();
    }

    /**
     * Answers "Confirm deletion?" and then dismisses the "Successfully deleted" prompt behind it.
     *
     * <p>Both halves matter. The second prompt is raised by the same widget and is just as modal,
     * so leaving it up means the next case's first click lands on an overlay instead of on the
     * screen.
     */
    public void confirmDeletion() {
        answerPrompt("Confirm");
    }

    /**
     * Answers the prompt with the named button, then clears the outcome prompt behind it.
     *
     * <p>Every write on this screen is two prompts deep: the question ("Confirm deletion?",
     * "Confirm to modify?") and then the result ("Successfully deleted"), each raised by the same
     * modal widget. Answering only the first leaves the second sitting over the page, and the
     * next case's first click lands on the overlay. Both are dealt with here, and doing nothing
     * when there is no prompt is deliberate - it makes this safe to call unconditionally after
     * any action that may or may not ask.
     */
    private void answerPrompt(String answer) {
        if (!isConfirmDialogVisible()) {
            return;
        }
        clickDialogButton(answer);
        page.waitForTimeout(LIST_SETTLE_MS);
        if (isConfirmDialogVisible()) {
            clickDialogButton("OK");
            page.waitForTimeout(LIST_SETTLE_MS);
        }
        if (confirmDialog().count() > 0 && confirmDialog().first().isVisible()) {
            waitForHidden(confirmDialog().first());
        }
    }

    /** Backs out of the confirmation, leaving the program where it was. */
    public void cancelDeletion() {
        clickDialogButton("Cancel");
        waitForHidden(confirmDialog().first());
    }

    /**
     * Presses one of the prompt's buttons by its label.
     *
     * <p>The buttons carry no class, no id and no type beyond {@code button} - the buttonset is
     * the only handle on them - so the label is all there is to choose by. Falls back to the
     * first button when the label is not found, which is what makes a single-answer prompt like
     * "Successfully deleted" safe to dismiss without knowing whether it says OK or 確定.
     */
    private void clickDialogButton(String label) {
        Locator buttons = confirmDialog().locator(".ui-dialog-buttonset button");
        waitForVisible(buttons.first());
        Locator match = buttons.filter(new Locator.FilterOptions().setHasText(label));
        (match.count() > 0 ? match.first() : buttons.first()).click();
    }
}
