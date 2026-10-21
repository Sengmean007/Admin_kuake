package org.sengmean.page.promo;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.SelectOption;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Discount &gt; System Promotion Setup</b> ({@code /promo/system-promotion}): the single System
 * Promotion configuration of the back office, one per header currency. First measured on admin-test
 * 2026-10-05 while its data calls were permission-blocked (every section stacked, single column,
 * under the defaults that state draws); re-measured 2026-10-06 once the permission was granted and
 * real data loads - the diagram and the save behaviour below are the live, loaded shape, not the
 * blocked one.
 *
 * <h2>What the screen holds</h2>
 * <pre>
 *   System Promotion Setup                       (h1; breadcrumb Home Page > Discount > System Promotion Setup)
 *   Overall settings
 *     Type [select PromotionType]  Status [PromotionStatus: Active/Inactive]
 *     Time Range (when reached, status turns on/off automatically) [text]  [Clear]
 *     Payment Channel [select PaymentChannel]
 *     Required deposit min. amount [DepositMinAmount]  Deposit transaction turnover multiple [DepositTurnoverMultiply]
 *     Deposit transaction require rebate turnover [DepositRebateTurnover: Enabled/Disable]
 *   Wallet Promotion                              |  Live Promotion                      (a left/right pair, one row -
 *     Status [WalletStatus]                       |    Status [LiveStatus]                GD-111: above the groups,
 *     Percentage [PromotionPercentages]            |    Percentage [LivePromotionPercentages]  no currency field)
 *     Maximum discount amount [PromotionMaxAmount] |    Maximum discount amount [LivePromotionMaxAmount]
 *     Offer currency [OfferCurrency]               |    Multiple turnover [LivePromotionTurnoverMultiply]
 *     Promotion turnover multiple                  |    Promotion require rebate turnover [LiveRebateTurnover]
 *     Promotion require rebate turnover [PromotionRebateTurnover]
 *   Agent group (left)                             |  Member group (right)                 (another left/right pair)
 *     (o) Select all  ( ) Optional   Option [select-from] >> > < << Selected [select-to]  - both sides
 *   [Save]
 * </pre>
 * The two pairs are read apart by column as well as by row - {@link #sectionTop} alone reads Wallet
 * and Live (and Agent and Member) at the same height; {@link #sectionLeft} is what tells left from
 * right.
 *
 * <p>The form is read and written through {@code /api/account/system-promo} (its Type list through
 * {@code /system-promo/type}, the channels through {@code /get_payment_channel}); when those answer
 * "Permission denied" the Type list is empty, every value is a default and Save is disabled - which
 * is how {@code qa0001} found it on 2026-10-05. {@link #isDataLoaded()} tells the two states apart.
 *
 * <h2>Saving is a two-step conversation once data loads, and the old code never answered the first step</h2>
 * Measured live 2026-10-06: Save on an <b>accepted</b> value first raises the back office's own
 * two-button prompt - "...Confirm the modification?" - and the write ({@code POST
 * /api/account/system-promo}, confirmed 200) only fires once <b>Confirm</b> is pressed; nothing
 * then follows it, so a successful save ends <i>silently</i>, unlike the Rebate screen's own
 * "Successfully modified". A <b>refused</b> value (blank or out-of-range Wallet percentage, both
 * measured) skips that prompt entirely and answers at once with a single-OK validation message.
 * {@link #save()} presses Confirm when the two-button prompt appears and otherwise reads a
 * single-OK one directly ({@link PromptMessage#confirmAndRead}) - see its own Javadoc for how the
 * earlier version here, which only ever watched for the single-OK shape, left every accepted save's
 * prompt unanswered until a 12s wait ran out and returned empty exactly as a disabled Save does.
 */
public class SystemPromotionSetupPage extends BasePage {

    /** The screen's address, relative to the base URL. */
    public static final String PATH = AdminShellPage.SYSTEM_PROMOTION_HREF.replaceFirst("^/", "");

    public static final String ACTIVE = "Active";
    public static final String INACTIVE = "Inactive";
    public static final String ENABLED = "Enabled";
    public static final String DISABLE = "Disable";

    /** The picker groups, in the order the screen draws them. */
    public static final int AGENT_GROUP = 0;
    public static final int MEMBER_GROUP = 1;

    // Field names, as the shipped form carries them.
    public static final String TYPE = "PromotionType";
    public static final String STATUS = "PromotionStatus";
    public static final String PAYMENT_CHANNEL = "PaymentChannel";
    public static final String DEPOSIT_MIN_AMOUNT = "DepositMinAmount";
    public static final String DEPOSIT_TURNOVER_MULTIPLE = "DepositTurnoverMultiply";
    public static final String DEPOSIT_REBATE_TURNOVER = "DepositRebateTurnover";
    public static final String WALLET_STATUS = "WalletStatus";
    public static final String WALLET_PERCENTAGE = "PromotionPercentages";
    public static final String WALLET_MAX_AMOUNT = "PromotionMaxAmount";
    public static final String OFFER_CURRENCY = "OfferCurrency";
    public static final String WALLET_TURNOVER_MULTIPLE = "PromotionTurnoverMultiply";
    public static final String WALLET_REBATE_TURNOVER = "PromotionRebateTurnover";
    public static final String LIVE_STATUS = "LiveStatus";
    public static final String LIVE_PERCENTAGE = "LivePromotionPercentages";
    public static final String LIVE_MAX_AMOUNT = "LivePromotionMaxAmount";
    public static final String LIVE_TURNOVER_MULTIPLE = "LivePromotionTurnoverMultiply";
    public static final String LIVE_REBATE_TURNOVER = "LiveRebateTurnover";

    /** The text inputs, in screen order - what a snapshot records and restores. */
    public static final List<String> TEXT_FIELDS = List.of(DEPOSIT_MIN_AMOUNT, DEPOSIT_TURNOVER_MULTIPLE,
            WALLET_PERCENTAGE, WALLET_MAX_AMOUNT, WALLET_TURNOVER_MULTIPLE,
            LIVE_PERCENTAGE, LIVE_MAX_AMOUNT, LIVE_TURNOVER_MULTIPLE);
    /** The single selects, in screen order. */
    public static final List<String> SELECT_FIELDS = List.of(TYPE, STATUS, PAYMENT_CHANNEL, DEPOSIT_REBATE_TURNOVER,
            WALLET_STATUS, OFFER_CURRENCY, WALLET_REBATE_TURNOVER, LIVE_STATUS, LIVE_REBATE_TURNOVER);

    /** The section titles, in the order GD-111 asks for them. */
    public static final List<String> SECTION_TITLES = List.of("Overall settings", "Wallet Promotion", "Live Promotion", "Agent group", "Member group");

    private static final String HEADING = "h1";
    private static final String SAVE = "button.btn-primary:has-text('Save')";
    private static final String TIME_RANGE_GROUP = ".form-group:has(label:has-text('Time Range'))";
    private static final String RADIO_NAMES[] = {"ag_group_selection", "mb_group_selection"};
    private static final long SAVE_PROMPT_MS = 12_000;
    private static final long LOAD_SETTLE_MS = 2_500;

    public SystemPromotionSetupPage(Page page) {
        super(page);
    }

    // ---------------------------------------------------------------
    // Opening and probing
    // ---------------------------------------------------------------

    /** Opens the screen by address and lets its data calls settle. */
    public void open() {
        CaseLog.step("Opening Discount > System Promotion Setup at /" + PATH);
        page.navigate(url(PATH), new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        try {
            page.locator("select[name='" + TYPE + "']").waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (PlaywrightException notDrawn) {
            CaseLog.evidence("The System Promotion form was not drawn within 15s; the page shows: " + bodyText(160));
        }
        page.waitForTimeout(LOAD_SETTLE_MS);
    }

    /** True while the screen is on - its heading and its form. */
    public boolean isShowing() {
        return heading().toLowerCase().contains("system promotion") && page.locator("select[name='" + TYPE + "']").count() > 0;
    }

    /** The screen's h1 - "System Promotion Setup" in English. */
    public String heading() {
        Locator h = page.locator(HEADING).filter(new Locator.FilterOptions().setVisible(true));
        return h.count() == 0 ? "" : h.first().innerText().trim();
    }

    /** The breadcrumb under the heading, e.g. "Home Page > Discount > System Promotion Setup". */
    public String breadcrumb() {
        Locator crumbs = page.locator(".content-header .breadcrumb, .breadcrumb").filter(new Locator.FilterOptions().setVisible(true));
        return crumbs.count() == 0 ? "" : crumbs.first().innerText().replaceAll("\\s+", " ").trim();
    }

    /**
     * True when the screen's own data arrived: the Type list has options. With the setup, type and
     * channel calls refused ("Permission denied") the list stays empty and Save is disabled.
     */
    public boolean isDataLoaded() {
        return typeOptions().size() > 0;
    }

    /**
     * Asks the screen's three data calls directly and says which refuse, e.g.
     * {@code "/api/account/system-promo: Permission denied; /api/account/system-promo/type: Permission denied"},
     * or empty when all answer.
     */
    public String refusedDataCalls(String currency) {
        List<String> refused = new ArrayList<>();
        for (String api : new String[] {"api/account/system-promo", "api/account/system-promo/type", "api/account/get_payment_channel"}) {
            try {
                String body = page.request().get(url(api + "?currency=" + currency)).text();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                String message = m.find() ? m.group(1) : "";
                if (!body.contains("\"status\":200")) {
                    refused.add("/" + api + ": " + (message.isEmpty() ? body.substring(0, Math.min(80, body.length())) : message));
                }
            } catch (RuntimeException couldNotAsk) {
                refused.add("/" + api + ": " + couldNotAsk.getMessage().split("\\R", 2)[0]);
            }
        }
        return String.join("; ", refused);
    }

    public boolean isSaveEnabled() {
        Locator save = page.locator(SAVE).filter(new Locator.FilterOptions().setVisible(true));
        return save.count() > 0 && save.first().isEnabled();
    }

    /** The section titles as drawn, in document order, among the five the screen is meant to show. */
    public List<String> sectionTitles() {
        List<String> out = new ArrayList<>();
        for (String title : SECTION_TITLES) {
            if (page.getByText(exactly(title, false)).filter(new Locator.FilterOptions().setVisible(true)).count() > 0) {
                out.add(title);
            }
        }
        return out;
    }

    /** The vertical position of a section title, for asserting the order GD-111 asks for; -1 when absent. */
    public int sectionTop(String title) {
        Locator t = page.getByText(exactly(title, false)).filter(new Locator.FilterOptions().setVisible(true));
        if (t.count() == 0) {
            return -1;
        }
        Object raw = t.first().evaluate("e => Math.round(e.getBoundingClientRect().top + window.scrollY)");
        return raw instanceof Number n ? n.intValue() : -1;
    }

    /**
     * The horizontal position of a section title - what tells Wallet Promotion from Live Promotion,
     * and Agent group from Member group, apart when {@link #sectionTop} reads the same row for both.
     * Measured live 2026-10-06: with real data loaded the form draws Wallet Promotion / Live
     * Promotion as a left/right pair on one row, and Agent group / Member group the same way below
     * them - not each on its own row, which is what the permission-blocked defaults this page was
     * first measured against (2026-10-05) had looked like with every section stacked. -1 when absent.
     */
    public int sectionLeft(String title) {
        Locator t = page.getByText(exactly(title, false)).filter(new Locator.FilterOptions().setVisible(true));
        if (t.count() == 0) {
            return -1;
        }
        Object raw = t.first().evaluate("e => Math.round(e.getBoundingClientRect().left + window.scrollX)");
        return raw instanceof Number n ? n.intValue() : -1;
    }

    /** Every visible label on the form, in order - what the language rows compare. */
    public List<String> labels() {
        Locator labels = page.locator(".content label").filter(new Locator.FilterOptions().setVisible(true));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < labels.count(); i++) {
            String text = labels.nth(i).innerText().replaceAll("\\s+", " ").trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------

    private Locator input(String name) {
        return page.locator("input[name='" + name + "']").first();
    }

    private Locator select(String name) {
        return page.locator("select[name='" + name + "']").first();
    }

    /** A text field's value. */
    public String value(String name) {
        return input(name).inputValue();
    }

    /** Types into a text field, firing the events the form listens to. */
    public void setValue(String name, String text) {
        CaseLog.step("Setting " + name + " to \"" + text + "\".");
        Locator field = input(name);
        field.fill(text);
        field.dispatchEvent("input");
        field.dispatchEvent("change");
    }

    /** The selected option's label of a single select ("Active", "Enabled", "USDT", a Type name). */
    public String selected(String name) {
        Object raw = select(name).evaluate("s => s.selectedOptions[0] ? s.selectedOptions[0].text.trim() : ''");
        return raw == null ? "" : String.valueOf(raw);
    }

    /** The labels a single select offers. */
    public List<String> options(String name) {
        Object raw = select(name).evaluate("s => [...s.options].map(o => o.text.trim())");
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(o -> out.add(String.valueOf(o)));
        }
        return out;
    }

    /** Chooses a single select's option by its label. */
    public void choose(String name, String label) {
        CaseLog.step("Choosing \"" + label + "\" for " + name + ".");
        select(name).selectOption(new SelectOption().setLabel(label));
        select(name).dispatchEvent("change");
    }

    public List<String> typeOptions() {
        return options(TYPE);
    }

    public List<String> paymentChannelOptions() {
        return options(PAYMENT_CHANNEL);
    }

    public List<String> offerCurrencyOptions() {
        return options(OFFER_CURRENCY);
    }

    // The Time Range picker: a text field with a Clear button beside it.

    public String timeRange() {
        return page.locator(TIME_RANGE_GROUP + " input").first().inputValue();
    }

    /** Writes a range into the picker's field - "YYYY-MM-DD HH:mm:ss - YYYY-MM-DD HH:mm:ss" - and fires its events. */
    public void setTimeRange(String range) {
        CaseLog.step("Setting the Time Range to \"" + range + "\".");
        Locator field = page.locator(TIME_RANGE_GROUP + " input").first();
        field.fill(range);
        field.dispatchEvent("input");
        field.dispatchEvent("change");
        page.keyboard().press("Escape");
    }

    public void clearTimeRange() {
        CaseLog.step("Pressing the Time Range's Clear.");
        page.locator(TIME_RANGE_GROUP + " button:has-text('Clear'), button.btn-xs:has-text('Clear')").first().click();
        page.waitForTimeout(400);
    }

    /** True while the Live section's Multiple turnover field is drawn (GD-111: shown when turnover is enabled). */
    public boolean isLiveTurnoverMultipleShown() {
        return input(LIVE_TURNOVER_MULTIPLE).count() > 0 && input(LIVE_TURNOVER_MULTIPLE).isVisible();
    }

    // ---------------------------------------------------------------
    // The Agent group / Member group pickers
    // ---------------------------------------------------------------

    private Locator groupRadio(int group, int which) {
        return page.locator("input[name='" + RADIO_NAMES[group] + "']").nth(which);
    }

    public boolean isSelectAll(int group) {
        return groupRadio(group, 0).isChecked();
    }

    public void chooseSelectAll(int group) {
        CaseLog.step("Choosing Select all for the " + groupName(group) + ".");
        groupRadio(group, 0).check(new Locator.CheckOptions().setForce(true));
        page.waitForTimeout(400);
    }

    public void chooseOptional(int group) {
        CaseLog.step("Choosing Optional for the " + groupName(group) + ".");
        groupRadio(group, 1).check(new Locator.CheckOptions().setForce(true));
        page.waitForTimeout(400);
    }

    private Locator availableSelect(int group) {
        return page.locator("select.select-from").nth(group);
    }

    private Locator selectedSelect(int group) {
        return page.locator("select.select-to").nth(group);
    }

    @SuppressWarnings("unchecked")
    private static List<String> names(Locator select) {
        Object raw = select.evaluate("s => [...s.options].map(o => o.text.trim())");
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }

    public List<String> availableNames(int group) {
        return names(availableSelect(group));
    }

    public List<String> selectedNames(int group) {
        return names(selectedSelect(group));
    }

    /** True while the picker's transfer buttons can be pressed - the Optional state. */
    public boolean isPickerEnabled(int group) {
        Locator add = page.locator("button.select-add").nth(group);
        return add.count() > 0 && add.isEnabled();
    }

    public void chooseAvailable(int group, String name) {
        availableSelect(group).selectOption(new SelectOption().setLabel(name));
    }

    public void chooseSelected(int group, String name) {
        selectedSelect(group).selectOption(new SelectOption().setLabel(name));
    }

    public void moveSelected(int group) {
        page.locator("button.select-add").nth(group).click();
        page.waitForTimeout(300);
    }

    public void removeSelected(int group) {
        page.locator("button.select-remove").nth(group).click();
        page.waitForTimeout(300);
    }

    public void moveAll(int group) {
        page.locator("button.select-all-add").nth(group).click();
        page.waitForTimeout(300);
    }

    public void removeAll(int group) {
        page.locator("button.select-all-remove").nth(group).click();
        page.waitForTimeout(300);
    }

    /** Puts a group on Optional with exactly these names in Selected. */
    public void targetOnly(int group, List<String> wanted) {
        chooseOptional(group);
        if (!selectedNames(group).isEmpty()) {
            removeAll(group);
        }
        for (String name : wanted) {
            chooseAvailable(group, name);
            moveSelected(group);
        }
    }

    public static String groupName(int group) {
        return group == AGENT_GROUP ? "Agent group" : "Member group";
    }

    // ---------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------

    /**
     * Presses Save and returns what the screen said: the prompt's words ("Successfully modified",
     * or a refusal), else the inline validation labels joined, else a literal note that the save
     * went through silently, else empty when Save itself was disabled.
     *
     * <h2>A valid save is a two-step conversation; an invalid one is not</h2>
     * Measured live 2026-10-06, pressing Save on a value the form accepts raises the back office's
     * own two-button "Prompt message" first - "...Confirm the modification?" - and only <b>after</b>
     * Confirm is pressed does the {@code POST /api/account/system-promo} actually fire (confirmed
     * 200 over the network). Nothing then follows it: unlike the Rebate screen's save, there is no
     * "Successfully modified" dialog afterwards at all - it saves silently. A value the form
     * <b>refuses</b> (blank or out-of-range Wallet percentage, both measured the same day) skips
     * the confirm step entirely and answers straight away with a single-OK validation prompt.
     * {@link PromptMessage#confirmAndRead} already presses Confirm when the two-button prompt shows
     * and otherwise reads a single-OK one directly, so one call covers both paths - the earlier
     * version here used {@link PromptMessage#readAndClose}, which looks only for the single-OK
     * shape and so left every valid save's confirm prompt unanswered until its own 12s wait ran
     * out, returning empty exactly as a disabled Save does. That is the bug {@code SPS-010},
     * {@code 011}, {@code 014} and {@code 015} were failing on (2026-10-06 09:52 run) - not a
     * refused save, an unanswered one.
     */
    public String save() {
        CaseLog.step("Pressing Save.");
        PromptMessage.clearStray(page);
        Locator save = page.locator(SAVE).first();
        if (!save.isEnabled()) {
            CaseLog.evidence("Save is disabled, so nothing was submitted.");
            return "";
        }
        save.click();
        String prompt = PromptMessage.confirmAndRead(page, SAVE_PROMPT_MS);
        String inline = inlineValidation();
        String said = !prompt.isEmpty() ? prompt : !inline.isEmpty() ? inline
                : "Saved successfully (this screen shows no confirmation message after Confirm).";
        CaseLog.data("Save said", said.isEmpty() ? "(nothing)" : said);
        page.waitForTimeout(800);
        return said;
    }

    /** The jQuery-validate messages drawn beside the fields right now, joined with " | ". */
    public String inlineValidation() {
        Locator errors = page.locator("label.error, .error:not(input):not(select), .help-block, .invalid-feedback, .text-danger")
                .filter(new Locator.FilterOptions().setVisible(true));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < errors.count(); i++) {
            String text = errors.nth(i).innerText().replaceAll("\\s+", " ").trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return String.join(" | ", out);
    }

    /** True for the back office's success wording, in any of its three languages. */
    public static boolean wasAccepted(String said) {
        String s = said == null ? "" : said.toLowerCase();
        return s.contains("success") || s.contains("成功") || s.contains("สำเร็จ");
    }

    // ---------------------------------------------------------------
    // Snapshots, so a writing row can put the global setting back
    // ---------------------------------------------------------------

    /** Every field and picker as the screen shows it now. */
    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : SELECT_FIELDS) {
            out.put(name, selected(name));
        }
        for (String name : TEXT_FIELDS) {
            out.put(name, value(name));
        }
        out.put("timeRange", timeRange());
        for (int group = 0; group < 2; group++) {
            out.put("group" + group + ".selectAll", String.valueOf(isSelectAll(group)));
            out.put("group" + group + ".selected", String.join("\u0001", selectedNames(group)));
        }
        return out;
    }

    /** Re-applies a snapshot to the form (without saving). Options a select no longer offers are skipped and recorded. */
    public void apply(Map<String, String> snapshot) {
        for (String name : SELECT_FIELDS) {
            String label = snapshot.getOrDefault(name, "");
            if (label.isEmpty() || label.equals(selected(name))) {
                continue;
            }
            if (options(name).contains(label)) {
                choose(name, label);
            } else {
                CaseLog.evidence(name + " no longer offers \"" + label + "\", so it was left as is.");
            }
        }
        for (String name : TEXT_FIELDS) {
            String text = snapshot.getOrDefault(name, "");
            if (!text.equals(value(name))) {
                setValue(name, text);
            }
        }
        String range = snapshot.getOrDefault("timeRange", "");
        if (!range.equals(timeRange())) {
            if (range.isEmpty()) {
                clearTimeRange();
            } else {
                setTimeRange(range);
            }
        }
        for (int group = 0; group < 2; group++) {
            boolean selectAll = Boolean.parseBoolean(snapshot.getOrDefault("group" + group + ".selectAll", "true"));
            String joined = snapshot.getOrDefault("group" + group + ".selected", "");
            List<String> wanted = joined.isEmpty() ? List.of() : List.of(joined.split("\u0001"));
            if (selectAll) {
                if (!isSelectAll(group)) {
                    chooseSelectAll(group);
                }
            } else {
                targetOnly(group, wanted);
            }
        }
    }

    private String bodyText(int max) {
        String text = page.locator("body").innerText().replaceAll("\\s+", " ").trim();
        return text.substring(0, Math.min(max, text.length()));
    }
}
