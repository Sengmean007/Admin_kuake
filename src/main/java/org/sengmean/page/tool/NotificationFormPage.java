package org.sengmean.page.tool;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Add Notification</b> and its Edit twin.
 *
 * <h2>What the captures show</h2>
 * {@code docs/mockups/notification/notification-form*.png} - one form, drawn in its Daily, Once and
 * Weekly states:
 * <pre>
 *   Notification                                       Home Page &gt; Tool &gt; Add Notification
 *   Notification Content
 *   [繁體] [简体] [English] [日語] [ภาษาไทย] [Vietnam] [Spain] [Malaysia] [Indonesia] [Filipino] [Bengali] [Portuguese]
 *   Title *      [Daily mission promotion reminder            ]
 *   Content *    [Enter notification content...               ]
 *   Override above contents to:
 *   [ ] All languages [ ] 简体 [ ] English [ ] 日本語 [ ] ภาษาไทย [ ] Vietnam [ ] Spain [ ] Malaysia ... [ ] Portuguese
 *   Link         [https://system.lumen.app/member/promotions/daily]
 *   Type [System v]  Schedule [Daily v]  (Week day [Sunday v])  Timer [12 AM v]  Status [Active v]
 *   Target Delivery Groups
 *     Agent Group   ( ) Select all (o) Optional      Member Group  ( ) Select all (o) Optional
 *     Option | » › ‹ « | Selected                    Option | » › ‹ « | Selected
 *   [Save Notification] [Cancel]
 * </pre>
 * Schedule = <b>Once</b> keeps Timer available: "—" sends immediately whenever the rule is made
 * Active, an hour sends it once at that hour (Si's requirement, 2026-09-28); <b>Weekly</b> adds a Week day select between
 * Schedule and Timer. The Override row sits between Content and Link, so it copies Title and
 * Content - not Link - and offers every language except 繁體, the one being copied from.
 *
 * <h2>Measured against the shipped screen (2026-09-28)</h2>
 * <ul>
 *   <li>Add is {@code /tools/notification-form}, Edit the same form with {@code ?id=N}; both are
 *       {@code form#frmNotification}, and both save with "Save Notification".</li>
 *   <li>The twelve language tabs are {@code ul.notif-lang-tabs} - not {@code ul.nav-tabs} - over ONE
 *       Title input and ONE Content textarea that switch with the tab (no tab panes).</li>
 *   <li>Each field is a {@code .notif-field} with a {@code label.control-label}: "Title *", "Content *",
 *       "Override above contents to:", "Link", "Type", "Schedule", "Week day" (Weekly only), "Timer",
 *       "Status". The Override row is {@code label.copy-lang-item} / {@code label.copy-lang-all-item},
 *       and writes 日語 as 日語, not the capture's 日本語.</li>
 *   <li>The group pickers: the radios are {@code ag_group_selection} / {@code mb_group_selection}
 *       ({@code value=all} / {@code selective}); the Option / Selected lists and the » › ‹ « buttons
 *       sit beside {@code #divAgentGroupSelect}, not inside it, so they are found by position.</li>
 *   <li>Save asks "Confirm to add?" (Confirm / Cancel) before posting, then says "Successfully added"
 *       and returns to the list. An empty save is refused with "Title and Content are required for all
 *       languages".</li>
 * </ul>
 */
public class NotificationFormPage extends BasePage {

    /** The twelve content tabs, in the capture's order. */
    public static final List<String> LANGUAGE_TABS = List.of(
            "繁體", "简体", "English", "日語", "ภาษาไทย", "Vietnam",
            "Spain", "Malaysia", "Indonesia", "Filipino", "Bengali", "Portuguese");

    /** Which transfer list is which, in the order the form draws them. */
    public static final int AGENT_GROUP = 0;
    public static final int MEMBER_GROUP = 1;

    /** What the Prompt message dialog said after the last Save, already closed. */
    private String lastPrompt = "";

    /** The rule as {@code GET api/account/inbox-notification-rule/N} served it to this Edit form, or "" for Add. */
    private String servedRule = "";

    /** The API's language codes, tab by tab: the {@code titles} / {@code contents} keys of a served rule. */
    private static final Map<String, String> LANGUAGE_CODES = Map.ofEntries(
            Map.entry("繁體", "zh-TW"), Map.entry("简体", "zh-CN"), Map.entry("English", "en"), Map.entry("日語", "ja"),
            Map.entry("ภาษาไทย", "th"), Map.entry("Vietnam", "vi"), Map.entry("Spain", "es"), Map.entry("Malaysia", "ms"),
            Map.entry("Indonesia", "id"), Map.entry("Filipino", "tl"), Map.entry("Bengali", "bn"), Map.entry("Portuguese", "pt"));

    public NotificationFormPage(Page page) {
        super(page);
    }

    // ---------------------------------------------------------------
    // Presence
    // ---------------------------------------------------------------

    /** Waits for the form by its Title field. */
    public void awaitOpen() {
        titleInput().waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        page.waitForTimeout(400);
    }

    /** True while the form is on screen: its language tabs and its Save Notification button. */
    public boolean isOpen() {
        return page.locator("#frmNotification ul.notif-lang-tabs").count() > 0 && saveButton().count() > 0 && saveButton().isVisible();
    }

    // ---------------------------------------------------------------
    // The rule as served - what the form was given to render
    // ---------------------------------------------------------------

    /** Set by {@link NotificationListPage#clickEdit}: the body of the GET the form loaded. */
    public void servedRule(String body) {
        this.servedRule = body == null ? "" : body;
    }

    public String servedRule() {
        return servedRule;
    }

    /** A language's Content as the API served it to this form, or "" when the body carries none. */
    public String servedContent(String languageTab) {
        return servedField("contents", languageTab);
    }

    /** A language's Title as the API served it to this form, or "" when the body carries none. */
    public String servedTitle(String languageTab) {
        return servedField("titles", languageTab);
    }


    private String servedField(String object, String languageTab) {
        String code = LANGUAGE_CODES.get(languageTab);
        if (code == null || servedRule.isEmpty()) {
            return "";
        }
        // "contents":{ ... } - the object's body, up to its closing brace (its values hold no braces).
        Matcher block = Pattern.compile("\"" + object + "\"\\s*:\\s*\\{([^}]*)\\}").matcher(servedRule);
        if (!block.find()) {
            return "";
        }
        // "en":"..." inside it, with any escaped quote kept in the value.
        Matcher value = Pattern.compile("\"" + Pattern.quote(code) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(block.group(1));
        return value.find() ? unescapeJson(value.group(1)) : "";
    }

    /** The JSON string escapes the API uses - an escaped slash, quote or backslash, n, t, r and uXXXX. */
    private static String unescapeJson(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char next = s.charAt(++i);
            switch (next) {
                case 'u' -> {
                    out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                default -> out.append(next);   // an escaped slash, quote or backslash
            }
        }
        return out.toString();
    }

    /** "Home Page / Tool / Add Notification". */
    public String breadcrumb() {
        return new NotificationListPage(page).breadcrumb();
    }

    // ---------------------------------------------------------------
    // Language tabs: Title, Content (per language), Link
    // ---------------------------------------------------------------

    private Locator tab(String label) {
        return page.locator("#frmNotification ul.notif-lang-tabs a")
                .filter(new Locator.FilterOptions().setHasText(exactly(label, false)))
                .filter(new Locator.FilterOptions().setVisible(true))
                .first();
    }

    /** Every language tab on screen, in the capture's order. */
    public List<String> languageTabLabels() {
        List<String> shown = new ArrayList<>();
        for (String label : LANGUAGE_TABS) {
            if (tab(label).count() > 0) {
                shown.add(label);
            }
        }
        return shown;
    }

    public void selectLanguageTab(String label) {
        PromptMessage.clearStray(page);
        tab(label).click();
        page.waitForTimeout(250);
    }

    /** The active content tab, read from Bootstrap's active marking. */
    public String activeLanguageTab() {
        for (String label : LANGUAGE_TABS) {
            Locator t = tab(label);
            if (t.count() == 0) {
                continue;
            }
            String state = t.getAttribute("aria-selected") + " " + t.getAttribute("class") + " "
                    + t.evaluate("a => a.parentElement?.className ?? ''");
            if (state.matches("(?s).*\\b(true|active|is-active|selected)\\b.*")) {
                return label;
            }
        }
        return "";
    }

    /**
     * How each field's label is spelled under the interface languages the header offers. The 2026-09-30
     * Localization run switched the back office to 中文 and every "Title" / "Content" lookup timed out
     * (NOT-I18N-002, NOT-I18N-006), because the labels translate with the interface.
     */
    private static final Map<String, String> LABEL_SPELLINGS = Map.of(
            "Title", "Title|標題|标题",
            "Content", "Content|內容|内容",
            "Link", "Link|連結|链接|鏈接|連接");

    /**
     * The {@code .notif-field} whose {@code label.control-label} reads {@code label} ("Title *" matches
     * "Title") in any interface language. The form has one Title and one Content that follow the active
     * language tab.
     */
    private Locator notifField(String label) {
        String spellings = LABEL_SPELLINGS.getOrDefault(label, label);
        return page.locator("#frmNotification .notif-field").filter(new Locator.FilterOptions().setHas(
                page.locator("label.control-label").filter(new Locator.FilterOptions()
                        .setHasText(Pattern.compile("^\\s*(?:" + spellings + ")\\s*\\*?\\s*$", Pattern.CASE_INSENSITIVE)))));
    }

    /**
     * The same three fields by their place in the form, for an interface language whose spelling is not
     * listed above: Title is the text input before the Content textarea, Link the first one after it.
     */
    private Locator fieldByPlace(String label, String tag) {
        String textInput = "input[@type='text' or not(@type)]";
        String textarea = "//form[@id='frmNotification']//*[contains(@class,'notif-field')]//textarea";
        return switch (label) {
            case "Title" -> page.locator("xpath=(" + textarea + ")[1]/preceding::" + textInput + "[1]");
            case "Link" -> page.locator("xpath=(" + textarea + ")[1]/following::" + textInput + "[1]");
            default -> page.locator("xpath=(" + textarea + ")[1]");
        };
    }

    /** The {@code tag} input inside the field labelled {@code label}: "Title *", "Content *", "Link". */
    private Locator field(String label, String tag) {
        return notifField(label).locator(tag).or(fieldByPlace(label, tag)).first();
    }

    private Locator titleInput() {
        return field("Title", "input");
    }

    private Locator contentInput() {
        return field("Content", "textarea");
    }

    private Locator linkInput() {
        return field("Link", "input");
    }

    public void setTitle(String languageTab, String title) {
        selectLanguageTab(languageTab);
        titleInput().fill(title);
    }

    public void setContent(String languageTab, String content) {
        selectLanguageTab(languageTab);
        contentInput().fill(content);
    }

    /**
     * Link. The capture draws it below the Override row, outside the language panes; if the real form
     * keeps one per language, it is set on the given tab.
     */
    public void setLink(String languageTab, String link) {
        selectLanguageTab(languageTab);
        linkInput().fill(link);
    }

    public String title(String languageTab) {
        selectLanguageTab(languageTab);
        return titleInput().inputValue();
    }

    public String content(String languageTab) {
        selectLanguageTab(languageTab);
        return contentInput().inputValue();
    }

    public String link(String languageTab) {
        selectLanguageTab(languageTab);
        return linkInput().inputValue();
    }

    /** "Enter notification content..." in the capture. */
    public String contentPlaceholder() {
        return String.valueOf(contentInput().getAttribute("placeholder"));
    }

    /** Fills Title, Content and Link on one tab. */
    public void fillLanguage(String languageTab, String title, String content, String link) {
        CaseLog.step("Filling Title, Content and Link on the " + languageTab + " tab.");
        setTitle(languageTab, title);
        setContent(languageTab, content);
        if (link != null) {
            setLink(languageTab, link);
        }
    }

    // ---------------------------------------------------------------
    // Override above contents to:
    // ---------------------------------------------------------------

    /** The tab the Override row copies from; the row is only rendered while this tab is active. */
    public static final String OVERRIDE_SOURCE_TAB = "繁體";

    /**
     * Measured 2026-09-28: "Override above contents to:" exists only while the 繁體 tab is active -
     * on any other tab the row is not rendered at all - so every Override action switches there first.
     */
    private void showOverrideRow() {
        if (!OVERRIDE_SOURCE_TAB.equals(activeLanguageTab())) {
            selectLanguageTab(OVERRIDE_SOURCE_TAB);
        }
    }

    /** One Override checkbox by its label; "All languages" is {@code label.copy-lang-all-item}. */
    private Locator overrideBox(String language) {
        showOverrideRow();
        if ("All languages".equalsIgnoreCase(language)) {
            return page.locator("#frmNotification label.copy-lang-all-item input[type='checkbox']").first();
        }
        return page.locator("#frmNotification label.copy-lang-item:not(.copy-lang-all-item)")
                .filter(new Locator.FilterOptions().setHasText(exactly(language, false)))
                .locator("input[type='checkbox']")
                .first();
    }

    /** The languages the Override row offers, by label - every one but the source, per the capture. */
    public List<String> overrideOptions() {
        showOverrideRow();
        Object raw = page.locator("label.copy-lang-item, label.copy-lang-all-item").evaluateAll(
                "ls => ls.map(l => l.textContent.trim()).filter(Boolean)");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /** Ticks "Override above contents to: {@code language}". */
    public void overrideTo(String language) {
        CaseLog.step("Ticking Override above contents to: " + language + ".");
        overrideBox(language).setChecked(true);
        page.waitForTimeout(300);
    }

    public void overrideToAll() {
        overrideTo("All languages");
    }

    /** Unticks "Override above contents to: {@code language}". */
    public void overrideOff(String language) {
        CaseLog.step("Unticking Override above contents to: " + language + ".");
        overrideBox(language).setChecked(false);
        page.waitForTimeout(300);
    }

    public void overrideAllOff() {
        overrideOff("All languages");
    }

    public boolean isOverrideTicked(String language) {
        return overrideBox(language).isChecked();
    }

    // ---------------------------------------------------------------
    // Type, Schedule, Week day, Timer, Status
    // ---------------------------------------------------------------

    /** The select in the settings row's field labelled "Type", "Schedule", "Week day", "Timer" or "Status". */
    private Locator setting(String label) {
        return notifField(label).locator("select").first();
    }

    private void choose(String label, String option) {
        CaseLog.step("Setting " + label + " to " + option + ".");
        Locator select = setting(label);
        Object value = select.evaluate("(s, l) => s.options ? ([...s.options].find(o => o.textContent.trim() === l)?.value ?? null) : null", option);
        if (value == null) {
            throw new IllegalArgumentException("The " + label + " select offers no \"" + option + "\"; it offers " + options(label));
        }
        select.selectOption(new SelectOption().setValue(String.valueOf(value)));
        page.waitForTimeout(400);
    }

    private String chosen(String label) {
        Locator select = setting(label);
        if (select.count() == 0) {
            return "";
        }
        Object text = select.evaluate("s => s.options ? (s.options[s.selectedIndex]?.textContent?.trim() ?? '') : (s.value || s.placeholder || '')");
        return String.valueOf(text).trim();
    }

    /** The options a setting's select offers, by their text. */
    public List<String> options(String label) {
        Locator select = setting(label);
        if (select.count() == 0) {
            return List.of();
        }
        Object raw = select.evaluate("s => s.options ? [...s.options].map(o => o.textContent.trim()) : []");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    public void setType(String type) {
        choose("Type", type);
    }

    public String type() {
        return chosen("Type");
    }

    /** "Once", "Daily" or "Weekly". */
    public void setSchedule(String schedule) {
        choose("Schedule", schedule);
    }

    public String schedule() {
        return chosen("Schedule");
    }

    /** True while the Week day select is on screen - only for Weekly, per the capture. */
    public boolean isWeekDayShown() {
        Locator weekDay = setting("Week day");
        return weekDay.count() > 0 && weekDay.isVisible() && weekDay.isEnabled();
    }

    public void setWeekDay(String day) {
        choose("Week day", day);
    }

    public String weekDay() {
        return chosen("Week day");
    }

    /** Whether the Week day select lets more than one day be chosen (AQ-17: it must not). */
    public boolean isWeekDayMultiSelect() {
        Locator weekDay = setting("Week day");
        return weekDay.count() > 0 && weekDay.getAttribute("multiple") != null;
    }

    /** "12 AM", "08 PM"... */
    public void setTimer(String timer) {
        choose("Timer", timer);
    }

    /** The Timer's shown value: "12 AM", or "—" while Schedule is Once. */
    public String timer() {
        return chosen("Timer");
    }

    public boolean isTimerEnabled() {
        Locator timer = setting("Timer");
        return timer.count() > 0 && timer.isEnabled() && timer.getAttribute("readonly") == null;
    }

    public List<String> timerOptions() {
        return options("Timer");
    }

    /** "Active" or "Inactive". */
    public void setStatus(String status) {
        choose("Status", status);
    }

    public String status() {
        return chosen("Status");
    }

    // ---------------------------------------------------------------
    // Target Delivery Groups: two transfer lists
    // ---------------------------------------------------------------

    /**
     * The half of Target Delivery Groups for one group: the {@code .col-md-6} holding the radios'
     * {@code #divAgentGroupSelect} / {@code #divMemberGroupSelect} and, beside it, the two lists.
     */
    private Locator groupPanel(int group) {
        String radios = group == AGENT_GROUP ? "#divAgentGroupSelect" : "#divMemberGroupSelect";
        return page.locator("#frmNotification .col-md-6").filter(new Locator.FilterOptions().setHas(page.locator(radios))).first();
    }

    private Locator available(int group) {
        return groupPanel(group).locator("select.select-from").first();
    }

    private Locator selected(int group) {
        return groupPanel(group).locator("select.select-to").first();
    }

    public List<String> availableNames(int group) {
        return available(group).locator("option").allInnerTexts().stream().map(String::trim).toList();
    }

    public List<String> selectedNames(int group) {
        return selected(group).locator("option").allInnerTexts().stream().map(String::trim).toList();
    }

    /** The radio pair before each list, by name and value: Select all ({@code all}), then Optional ({@code selective}). */
    private Locator groupRadio(int group, int which) {
        String name = group == AGENT_GROUP ? "ag_group_selection" : "mb_group_selection";
        return page.locator("input[type='radio'][name='" + name + "'][value='" + (which == 0 ? "all" : "selective") + "']").first();
    }

    /** "Select all" - NOT-CRT-020 / NOT-GRP-005 / -006. */
    public void chooseSelectAll(int group) {
        CaseLog.step("Choosing Select all for the " + label(group) + ".");
        groupRadio(group, 0).check();
        page.waitForTimeout(800);
    }

    public boolean isSelectAll(int group) {
        return groupRadio(group, 0).isChecked();
    }

    /** "Optional" - the transfer controls come alive (on Rebate they are disabled under Select all). */
    public void chooseOptional(int group) {
        groupRadio(group, 1).check();
        try {
            page.waitForCondition(() -> available(group).isEnabled(), new Page.WaitForConditionOptions().setTimeout(4000));
        } catch (RuntimeException stillDisabled) {
            // The caller's assertion will say what the widget did.
        }
    }

    /** Moves exactly these entries across, with the single arrow (›). */
    public void target(int group, List<String> names) {
        chooseOptional(group);
        for (String name : names) {
            List<String> offered = availableNames(group);
            if (!offered.contains(name)) {
                if (selectedNames(group).contains(name)) {
                    continue;
                }
                throw new IllegalStateException("\"" + name + "\" is not offered by the " + label(group)
                        + " picker, which offers " + offered.size() + " entries: " + offered);
            }
            CaseLog.step("Moving " + name + " into the " + label(group) + "'s Selected column.");
            available(group).selectOption(new SelectOption().setLabel(name));
            moveButton(group, "select-add", 1).click();
            page.waitForTimeout(400);
        }
    }

    public void target(int group, String name) {
        target(group, List.of(name));
    }

    /** » - every Option entry across. */
    public void moveAll(int group) {
        CaseLog.step("Clicking » on the " + label(group) + ".");
        chooseOptional(group);
        moveButton(group, "select-all-add", 0).click();
        page.waitForTimeout(500);
    }

    /** ‹ - one Selected entry back. */
    public void removeOne(int group, String name) {
        CaseLog.step("Moving " + name + " back out of the " + label(group) + "'s Selected column.");
        selected(group).selectOption(new SelectOption().setLabel(name));
        moveButton(group, "select-remove", 2).click();
        page.waitForTimeout(400);
    }

    /** « - the whole Selected column back. */
    public void removeAll(int group) {
        CaseLog.step("Clicking « on the " + label(group) + ".");
        moveButton(group, "select-all-remove", 3).click();
        page.waitForTimeout(500);
    }

    /** One of the group's four transfer buttons, by class: » select-all-add, › select-add, ‹ select-remove, « select-all-remove. */
    private Locator moveButton(int group, String css, int position) {
        return groupPanel(group).locator("ul.notif-transfer-controls button." + css).first();
    }

    private static String label(int group) {
        return group == AGENT_GROUP ? "Agent Group" : "Member Group";
    }

    // ---------------------------------------------------------------
    // Save / Cancel
    // ---------------------------------------------------------------

    /** "Save Notification" - the form's submit, on Add and Edit alike. */
    private Locator saveButton() {
        return page.locator("#frmNotification .notif-form-actions button[type='submit']").first();
    }

    /**
     * Clicks Save Notification, presses Confirm on "Confirm to add?", and reads what the page said
     * next - "Successfully added", or the refusal - closing it.
     *
     * <p>Measured 2026-09-28: the form posts only after that Confirm. Reading the first dialog and
     * pressing its first button is not the same thing: a refusal ("Title and Content are required
     * for all languages") has one OK button and no confirmation, which {@code confirmAndRead} handles.
     */
    public void clickSave() {
        CaseLog.step("Clicking Save Notification.");
        PromptMessage.clearStray(page);
        saveButton().click();
        lastPrompt = PromptMessage.confirmAndRead(page, 4000);
        page.waitForTimeout(1200);
        CaseLog.data("After Save", lastPrompt.isEmpty() ? "(no message)" : lastPrompt);
    }

    /**
     * Clicks Save Notification and presses Cancel on the confirmation the form asks first, so nothing is posted.
     * Returns what the confirmation asked, or "" when the form asked nothing (NOT-CRT-030).
     */
    public String clickSaveAndDecline() {
        CaseLog.step("Clicking Save Notification, to decline its confirmation.");
        PromptMessage.clearStray(page);
        saveButton().click();
        String asked = PromptMessage.declineAndRead(page, 4000);
        page.waitForTimeout(800);
        CaseLog.data("Confirmation declined", asked.isEmpty() ? "(none was asked)" : asked);
        return asked;
    }

    /** What the Prompt message dialog said after the last Save - the refusal's own words. */
    public String lastMessage() {
        return lastPrompt;
    }

    /** Every inline message besides the Prompt dialog - a native required-field bubble included. */
    public String messages() {
        StringBuilder all = new StringBuilder(lastPrompt);
        for (String inline : page.locator(".invalid-feedback, .error-message, .text-danger, .el-form-item__error, [role='alert']")
                .filter(new Locator.FilterOptions().setVisible(true)).allInnerTexts()) {
            if (!inline.isBlank()) {
                all.append(all.isEmpty() ? "" : " | ").append(inline.trim());
            }
        }
        Object invalid = page.evaluate("() => [...document.querySelectorAll('form :invalid')].map(e => e.validationMessage).filter(Boolean).join(' | ')");
        if (invalid != null && !String.valueOf(invalid).isBlank()) {
            all.append(all.isEmpty() ? "" : " | ").append(invalid);
        }
        return all.toString();
    }

    public void cancel() {
        CaseLog.step("Clicking Cancel.");
        PromptMessage.clearStray(page);
        // A link back to the list, beside Save - found by place, not its "Cancel", which 中文 translates.
        page.locator("#frmNotification .notif-form-actions a.btn-default").first().click();
        page.waitForTimeout(1000);
    }

    /** Every visible text on the form, for the localization row. */
    public String screenText() {
        return page.locator("body").innerText();
    }
}
