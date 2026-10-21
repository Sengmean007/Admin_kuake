package org.sengmean.page.rebate;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.sengmean.base.BasePage;

import java.util.List;

/**
 * The <b>Create</b> and <b>Modify</b> rebate program form.
 *
 * <h2>This is a view, not a page - it has no URL of its own</h2>
 * Create and Modify replace the program list <em>in place</em>: the address bar still reads
 * {@code /rebate/set-up/} the whole time, and nothing navigates. That is why
 * {@code RBMP-119}'s "deep-link to the Create/Modify URL" has no target, and it is why this
 * class takes the same {@link Page} the list is on rather than opening anything.
 *
 * <h2>Everything below was read off the deployed form on 2026-09-18</h2>
 * Until that date the form had not been built and the two tab page objects simply aborted any
 * case that needed it. It is there now, and it turned out to carry rather more than the
 * workbook describes:
 *
 * <ul>
 *   <li><b>Program name</b> - the form's only placeholder-bearing text input;</li>
 *   <li><b>Status</b> - a native select, Enable {@code 1} / Disable {@code 0}, defaulting to
 *       <b>Disable</b>, which is what {@code RBMP-017} and {@code RBMP-072} predicted;</li>
 *   <li><b>Agent group</b> and <b>Member group</b> - a pair of transfer lists, each preceded by
 *       a <b>Select all / Optional</b> radio pair, and each with four move buttons;</li>
 *   <li>a <b>game rate table</b> of 20 rows - No. / Game name / Game code / Percentage% - every
 *       one pre-populated at {@code 0.00}, which is {@code RBMP-043} / {@code RBMP-098}'s
 *       "all game categories are pre-populated automatically on Create";</li>
 *   <li><b>Cancel</b>, <b>Submit</b>, and a <b>Return »</b> control above them.</li>
 * </ul>
 *
 * <h2>Scoping, and why it is not optional here</h2>
 * The list and this form share the screen, the {@code .box-body} wrapper, the
 * {@code .rebate-info-banner} and even the {@code table.custom_table} class - so a selector
 * that is not scoped to one of them will silently address the other. {@link #FORM_ROOT} picks
 * this view out by its transfer lists, which the list view has none of, exactly as
 * {@link BaseRebateProgramPage#LIST_ROOT} picks the list out by its Create button.
 *
 * <p><b>Cancel and Submit are the exception</b>: they sit in a footer <em>outside</em> the
 * box-body, so they are addressed at page level and told apart from the list's Create button by
 * it carrying {@code .btn-sm} and them not.
 *
 * <h2>The transfer lists are empty on this environment</h2>
 * Both <b>Option</b> columns offer zero entries, because no Agent groups and no Member groups
 * are configured - see {@code RebateTestData.SEEDING_REQUIRED}. The locators are still correct
 * and the counts they return are still meaningful ("nothing to select" is a finding a case can
 * assert); what cannot be exercised yet is moving an entry across. The <b>Select all</b> radio
 * is checked by default for both groups, so a program may well be creatable without touching
 * them at all - which is the first thing to try when the create cases are switched on.
 */
public class RebateProgramFormPage extends BasePage {

    /**
     * The Create/Modify view's own box, and the scope for everything inside it.
     *
     * <p>Identified by its transfer lists. Counted on 2026-09-18: <b>none</b> on either list
     * tab, one while the form is open.
     */
    public static final String FORM_ROOT = "div.box-body:has(select.select-from):visible";

    /** Which transfer list is which, in the order the form draws them. */
    public static final int AGENT_GROUP = 0;
    public static final int MEMBER_GROUP = 1;

    /** The game rate table's columns, left to right. */
    private static final int GAME_NAME_COLUMN = 1;
    private static final int GAME_CODE_COLUMN = 2;

    private static final double DEFAULT_TIMEOUT_MS = 10_000;
    private static final double SUBMIT_SETTLE_MS = 2000;

    private final Locator formRoot;
    private final Locator programNameInput;
    private final Locator statusSelect;
    private final Locator gameTable;
    private final Locator cancelButton;
    private final Locator submitButton;
    private final Locator returnButton;

    public RebateProgramFormPage(Page page) {
        super(page);
        this.formRoot = page.locator(FORM_ROOT);

        // The rate inputs carry no placeholder, so "the text input that has one" is the name
        // field and nothing else - twenty-one inputs, exactly one of them matched.
        this.programNameInput = formRoot.locator("input[type='text'].form-control[placeholder]");
        this.statusSelect = formRoot.locator("select.form-control");
        this.gameTable = formRoot.locator("table.custom_table");

        // Outside the box-body, so page-level. :not(.btn-sm) is what keeps Submit from matching
        // the list's "+ Create Real-time rebate", which is also a .btn-primary.
        this.cancelButton = page.locator("button.btn-default:visible");
        this.submitButton = page.locator("button.btn-primary:not(.btn-sm):visible");
        this.returnButton = page.locator("button.rebate-return-btn");
    }

    // ---------------------------------------------------------------
    // Presence
    // ---------------------------------------------------------------

    /** True while the Create/Modify form is the view on screen rather than the list. */
    public boolean isOpen() {
        return formRoot.count() > 0;
    }

    /**
     * Waits until the form is on screen <b>and its rate table has arrived</b>.
     *
     * <h2>The table is about half a second behind the form</h2>
     * Measured on 2026-09-18 by opening Create and polling every 500ms: at 0ms the table holds
     * its single "No data" row, and by 500ms it holds all twenty games. The column headings are
     * there the whole time, so a check on those cannot tell the two apart.
     *
     * <p>That gap is not cosmetic. A case that opened the form and immediately called
     * {@link #setRate(String, String)} was addressing a row that did not exist yet - the fill
     * quietly went nowhere, the program was submitted with no rate set, and the back office
     * refused it <em>without a message</em>. The symptom was "the suite cannot create a Daily
     * program"; the cause was reading the form 500ms too early, and it is the same mistake the
     * settings panels and the Modify form each produced in their own way.
     *
     * <p>Waiting on a rate <em>input</em> rather than on a row count is deliberate: the empty
     * table has a row too, and only a populated one has fields in it.
     */
    public void waitUntilOpen() {
        formRoot.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(DEFAULT_TIMEOUT_MS));
        page.waitForCondition(
                () -> formRoot.locator("table.custom_table tbody input.form-control").count() > 0,
                new Page.WaitForConditionOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    }

    /**
     * Waits until the form has been <b>filled in</b>, which is what Modify needs and Create does
     * not.
     *
     * <h2>Modify opens empty and populates about half a second later</h2>
     * Measured on 2026-09-18 by opening Modify on a known program and polling every 500ms: at
     * 0ms the name field is empty and Status reads Disable (its Create default); by 500ms the
     * name is the program's and Status is the program's. So a case that read the form the
     * instant it opened would report {@code RBMP-004}'s "Modify opens the correct program
     * pre-filled" as a failure - with an empty name and the wrong status - when the only thing
     * wrong was the moment it looked.
     *
     * <p>Not used when opening <b>Create</b>, where an empty name field is the correct state and
     * waiting for one to fill would simply time out.
     */
    public void waitUntilPopulated() {
        waitUntilOpen();
        page.waitForCondition(() -> !programNameInput.inputValue().isBlank(),
                new Page.WaitForConditionOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    }

    public void waitUntilClosed() {
        formRoot.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(DEFAULT_TIMEOUT_MS));
    }

    /**
     * The section labels the form draws, in order.
     *
     * <p>Useful to the localization rows, which need something on this view to compare across
     * languages: "Program details", "Program name", "Program status &amp; eligibility",
     * "Status", "Agent group", "Select all", "Optional", "Option", "Selected", "Member group".
     */
    public List<String> labels() {
        return formRoot.locator("label").allInnerTexts()
                .stream().map(String::trim).filter(text -> !text.isEmpty()).toList();
    }

    // ---------------------------------------------------------------
    // Program name and status
    // ---------------------------------------------------------------

    public String programName() {
        return programNameInput.inputValue();
    }

    public void setProgramName(String name) {
        programNameInput.fill(name);
    }

    /** The hint the empty name field shows - "New Real-time Rebate" or the Daily equivalent. */
    public String programNamePlaceholder() {
        return String.valueOf(programNameInput.getAttribute("placeholder"));
    }

    /** "Enable" or "Disable", rather than the {@code 1} / {@code 0} the option stores. */
    public String status() {
        return BaseRebateProgramPage.STATUS_VALUE_ENABLE.equals(statusSelect.inputValue())
                ? "Enable" : "Disable";
    }

    public void setStatus(String status) {
        statusSelect.selectOption(BaseRebateProgramPage.statusValue(status));
    }

    // ---------------------------------------------------------------
    // Agent group / Member group transfer lists
    // ---------------------------------------------------------------

    /** The "Option" column of one group's transfer list - what is available to be chosen. */
    public Locator availableOptions(int group) {
        return formRoot.locator("select.select-from").nth(group);
    }

    /** The "Selected" column of one group's transfer list - what this program targets. */
    public Locator selectedOptions(int group) {
        return formRoot.locator("select.select-to").nth(group);
    }

    public int availableCount(int group) {
        return availableOptions(group).locator("option").count();
    }

    public int selectedCount(int group) {
        return selectedOptions(group).locator("option").count();
    }

    public List<String> availableNames(int group) {
        return availableOptions(group).locator("option").allInnerTexts()
                .stream().map(String::trim).toList();
    }

    public List<String> selectedNames(int group) {
        return selectedOptions(group).locator("option").allInnerTexts()
                .stream().map(String::trim).toList();
    }

    /**
     * The four move controls beside a transfer list.
     *
     * <p>They carry no text at all - each is an icon button - so they are addressed by the class
     * that names what they do, which is the most stable handle on this form and happens to be
     * the clearest too: {@code select-all-add} is {@code >>}, {@code select-add} is {@code >},
     * {@code select-remove} is {@code <} and {@code select-all-remove} is {@code <<}.
     */
    public void moveAll(int group) {
        formRoot.locator("button.select-all-add").nth(group).click();
    }

    public void moveSelected(int group) {
        formRoot.locator("button.select-add").nth(group).click();
    }

    public void removeSelected(int group) {
        formRoot.locator("button.select-remove").nth(group).click();
    }

    public void removeAll(int group) {
        formRoot.locator("button.select-all-remove").nth(group).click();
    }

    /**
     * Targets exactly one named group: switches off "Select all", picks the entry and moves it.
     *
     * <p>The three steps always go together - a program is not targeted at a group until the
     * entry is on the <b>Selected</b> side, and leaving the radio on "Select all" ignores the
     * selection entirely. Doing them in one call is also what makes the failure legible: if the
     * name is not on offer, the caller finds out here rather than at a later assertion about
     * something else.
     *
     * @throws IllegalStateException when the group is not among the options, which on this
     *                               environment means the picker came back empty - see
     *                               {@code RebateTestData.AGENT_GROUP_8001} for why
     */
    public void targetOnly(int group, String name) {
        String which = group == AGENT_GROUP ? "Agent" : "Member";
        chooseOptional(group);
        page.waitForTimeout(SELECTION_SETTLE_MS);

        // Two different failures, and telling them apart is the whole value of this method.
        if (!isTransferEnabled(group)) {
            throw new IllegalStateException("The " + which + " group's transfer controls are still "
                    + "disabled after choosing Optional, so nothing can be moved. The panes and "
                    + "all four buttons carry disabled=\"disabled\" while Select all is chosen - "
                    + "so either the radio did not register, or the form is not offering the "
                    + "Optional mode at all.");
        }

        List<String> offered = availableNames(group);
        if (!offered.contains(name)) {
            throw new IllegalStateException("\"" + name + "\" is not on offer in the " + which
                    + " group picker, which is enabled but holds " + offered.size()
                    + " entries: " + offered + ". An enabled-but-empty picker means the list "
                    + "never arrived - on this environment /api/account/"
                    + (group == AGENT_GROUP ? "agent" : "member_group")
                    + " answers \"Permission denied\" for the operator the suite signs in as.");
        }
        chooseAvailable(group, name);
        moveSelected(group);
        page.waitForTimeout(SELECTION_SETTLE_MS);
    }

    /** How long the transfer list is given to redraw after a radio or a move. */
    private static final double SELECTION_SETTLE_MS = 800;

    /** Highlights one entry in a group's Option column, ready for {@link #moveSelected(int)}. */
    public void chooseAvailable(int group, String name) {
        availableOptions(group).selectOption(new com.microsoft.playwright.options.SelectOption()
                .setLabel(name));
    }

    /** Highlights one entry in a group's Selected column, ready for {@link #removeSelected(int)}. */
    public void chooseSelected(int group, String name) {
        selectedOptions(group).selectOption(new com.microsoft.playwright.options.SelectOption()
                .setLabel(name));
    }

    /**
     * Whether a group is set to "Select all" rather than "Optional".
     *
     * <p>The radios are the only controls on this form with a {@code name}, and it is a generated
     * one - {@code ag_group_select_30} for the Agent group and {@code ag_group_select_31} for the
     * Member group. Generated names are exactly the kind that change, so they are matched by
     * prefix and picked by position rather than quoted in full.
     */
    public boolean isSelectAll(int group) {
        return groupRadio(group, 0).isChecked();
    }

    public void chooseSelectAll(int group) {
        groupRadio(group, 0).check();
    }

    /**
     * Switches a group onto "Optional" and waits for the transfer controls to come alive.
     *
     * <h2>In "Select all" mode the whole widget is disabled</h2>
     * Both {@code <select>} panes and all four move buttons carry {@code disabled="disabled"}
     * while Select all is chosen - read off the markup on 2026-09-18. So choosing Optional is not
     * a formality: until it registers, every other call here addresses a control that cannot be
     * used, and {@code selectOption} on a disabled list fails with a timeout that says nothing
     * about the radio.
     */
    public void chooseOptional(int group) {
        groupRadio(group, 1).check();
        try {
            page.waitForCondition(() -> isTransferEnabled(group),
                    new Page.WaitForConditionOptions().setTimeout(SELECTION_SETTLE_MS * 5));
        } catch (RuntimeException stillDisabled) {
            // Left to the caller to interpret: targetOnly() turns it into a legible message, and
            // a case that only wanted to see the radio move does not need this to throw.
        }
    }

    /** Whether a group's panes and move buttons are usable, i.e. it is in "Optional" mode. */
    public boolean isTransferEnabled(int group) {
        return availableOptions(group).isEnabled();
    }

    /**
     * The radio pair for a group, found by position rather than by name.
     *
     * <p>The names are generated and <b>they move</b>: the Agent group's pair was
     * {@code ag_group_select_30} on one render and {@code ag_group_select_31} on another, with
     * the Member group's shifting alongside it. Matching the prefix and counting - Agent first,
     * Member second, two radios each - survives that; quoting a name would not.
     */
    private Locator groupRadio(int group, int which) {
        return formRoot.locator("input[type='radio'][name^='ag_group_select']").nth(group * 2 + which);
    }

    // ---------------------------------------------------------------
    // Game-category rebate rates
    // ---------------------------------------------------------------

    /** The rate table's column headings, in whatever language the header is set to. */
    public List<String> gameColumnHeaders() {
        return gameTable.locator("th").allInnerTexts()
                .stream().map(String::trim).filter(text -> !text.isEmpty()).toList();
    }

    /** How many game categories the form is offering a rate for - 20 on this environment. */
    public int gameCount() {
        return gameTable.locator("tbody tr").count();
    }

    /** Every game's name, top to bottom. These follow the interface language. */
    public List<String> gameNames() {
        return columnText(GAME_NAME_COLUMN);
    }

    /** Every game's code - {@code GS_S6}, {@code GS_DG} - which does <b>not</b> follow language. */
    public List<String> gameCodes() {
        return columnText(GAME_CODE_COLUMN);
    }

    private List<String> columnText(int column) {
        return gameTable.locator("tbody tr td:nth-child(" + (column + 1) + ")")
                .allInnerTexts().stream().map(String::trim).toList();
    }

    /**
     * Whether this form is currently offering a rate row for a game.
     *
     * <h2>Create and Modify do not show the same games</h2>
     * The back office has <b>63</b> games and the rate table shows one page of <b>20</b>, so a
     * code that is on one view may be absent from the other. Measured on 2026-09-18 with the same
     * program: Create listed {@code GS_S6, GS_CY, GS_GB, GS_BL, GS_ST, GS_DG, ...} and Modify
     * listed {@code GS_PR, --, GS_PM, --, --, GS_DG, ...} - a different set in a different order,
     * and several rows with <b>no code at all</b>.
     *
     * <p>So a case that hard-codes a second game and expects to find it on both screens will time
     * out on one of them, which is exactly how {@code RBMP-025}, {@code RBMP-026} and
     * {@code RBMP-027} first failed. Address a game by code only after checking it is here, or
     * address a row by position with {@link #setRateAt(int, String)}.
     */
    public boolean hasGame(String gameCode) {
        return gameCodes().contains(gameCode);
    }

    /** The code on the n-th rate row, top to bottom. {@code --} where the game has none. */
    public String gameCodeAt(int row) {
        return gameCodes().get(row);
    }

    /** The rate on the n-th row, addressed by position rather than by a code that may be absent. */
    public String rateAt(int row) {
        return gameTable.locator("tbody input.form-control").nth(row).inputValue();
    }

    public void setRateAt(int row, String percentage) {
        gameTable.locator("tbody input.form-control").nth(row).fill(percentage);
    }

    /**
     * The position of some game other than the one named - a row a case can safely disturb, or
     * check was left alone.
     *
     * @return a row index, or {@code -1} when the form has only the named game
     */
    public int rowOtherThan(String gameCode) {
        List<String> codes = gameCodes();
        for (int row = 0; row < codes.size(); row++) {
            if (!codes.get(row).equals(gameCode)) {
                return row;
            }
        }
        return -1;
    }

    /** The rate box on the row for a game, found by its name or by its code. */
    public Locator rateInput(String gameNameOrCode) {
        return gameTable.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(gameNameOrCode))
                .locator("input.form-control");
    }

    public String rate(String gameNameOrCode) {
        return rateInput(gameNameOrCode).first().inputValue();
    }

    public void setRate(String gameNameOrCode, String percentage) {
        rateInput(gameNameOrCode).first().fill(percentage);
    }

    /** Every rate on the form, in row order - the quickest way to assert "all pre-populated". */
    public List<String> allRates() {
        Object raw = gameTable.locator("tbody input.form-control")
                .evaluateAll("inputs => inputs.map(i => i.value)");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    // ---------------------------------------------------------------
    // Leaving the form
    // ---------------------------------------------------------------

    /**
     * Presses Submit, reads what the back office said, and <b>clears the prompt</b>.
     *
     * <h2>Leaving the prompt up poisons everything after it</h2>
     * Every submission - accepted or refused - raises the modal prompt, and it does not close
     * itself. The first version of this method left it there, so a case whose assertion failed
     * walked away with a modal overlay across the screen and <b>every case after it timed out</b>
     * in {@code @BeforeEach}, unable to click anything. One refused submission turned into
     * thirty-five errors, none of which had anything wrong with them.
     *
     * <p>The message is captured before the prompt is dismissed, because it is the only record of
     * why a submission was refused - see {@link #validationMessageText()}.
     */
    public void submit() {
        submitButton.first().click();
        page.waitForTimeout(SUBMIT_SETTLE_MS);

        Locator prompt = validationMessage();
        if (prompt.count() > 0 && prompt.first().isVisible()) {
            Locator content = prompt.locator(".ui-dialog-content");
            lastMessage = content.count() == 0 ? ""
                    : content.first().innerText().replaceAll("\\s+", " ").trim();
            dismissMessage();
        } else {
            lastMessage = "";
        }
    }

    /** What the back office said about the last {@link #submit()}. */
    private String lastMessage = "";

    public void cancel() {
        cancelButton.first().click();
        page.waitForTimeout(SUBMIT_SETTLE_MS);
    }

    /** The "Return »" control above the form, which goes back to the list without submitting. */
    public void returnToList() {
        returnButton.first().click();
        page.waitForTimeout(SUBMIT_SETTLE_MS);
    }

    /**
     * What the back office says about a submission, accepted or refused.
     *
     * <p>The same jQuery UI prompt the rest of the application uses - see
     * {@link BaseRebateProgramPage#confirmDialog()}. It is not an inline field-level message:
     * nothing with {@code .help-block}, {@code .text-danger} or {@code [role=alert]} appears on
     * this form at all.
     */
    public Locator validationMessage() {
        return page.locator("div.ui-dialog[role='dialog']");
    }

    public boolean isValidationMessageVisible() {
        Locator message = validationMessage();
        return message.count() > 0 && message.first().isVisible();
    }

    public String validationMessageText() {
        Locator content = validationMessage().locator(".ui-dialog-content");
        return content.count() == 0 ? "" : content.first().innerText().replaceAll("\\s+", " ").trim();
    }

    /** Dismisses whatever prompt a submission raised, so the next action is not blocked by it. */
    /** Clears any prompt that is up, so the next action is not clicking through an overlay. */
    public void dismissMessage() {
        for (int attempt = 0; attempt < 3; attempt++) {
            Locator buttons = validationMessage().locator(".ui-dialog-buttonset button");
            if (buttons.count() == 0 || !buttons.first().isVisible()) {
                return;
            }
            buttons.first().click();
            page.waitForTimeout(SUBMIT_SETTLE_MS);
        }
    }

    /**
     * Whether the last {@link #submit()} was accepted.
     *
     * <h2>How the form answers, measured on 2026-09-18</h2>
     * A create was driven end to end - one program made and then deleted again - and the form
     * says yes or no by <b>whether it closes</b>:
     *
     * <ul>
     *   <li><b>Accepted</b>: the form closes and the list comes back with the new row on it. With
     *       both groups left on their default <b>Select all</b>, the row's Target Agents reads
     *       "All Agents" and its User Groups "All Groups" - so a program <em>can</em> be created
     *       on an environment with no groups configured, which is worth knowing before anyone
     *       waits on seeding to switch the create cases on.</li>
     *   <li><b>Refused</b>: the form <b>stays open</b> with everything still filled in. Submitting
     *       a second program with the same All Agents / All Groups pairing did exactly this,
     *       which is {@code RBMP-015}'s duplicate-pairing rule enforcing itself.</li>
     * </ul>
     */
    public boolean wasAccepted() {
        return !isOpen();
    }
}
