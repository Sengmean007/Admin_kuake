package org.sengmean.page.member;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

/**
 * The <b>Ranking Settings</b> tab of Membership and Referral &gt; Rankings.
 *
 * <h2>Read from the Figma design (frame {@code admin-rankings-settings})</h2>
 * Two sections:
 * <ul>
 *   <li><b>Client Display Settings</b> - "Homepage widget entries" (locked, "3 rows"), "Full
 *       rankings page entries" (locked, "50 rows"), "Show \"My Ranking\" row" (locked, On), "Mask
 *       usernames" (locked, On), and "Amount display on client": a radio choice of <b>Exact
 *       amount</b> / <b>Rounded</b> (default) / <b>Partial mask</b>, with a live "Client preview"
 *       box of three rows beside it.</li>
 *   <li><b>Data Rules</b> ("How rankings are calculated. Applies to both tabs.") - "Data period"
 *       (locked, "All-time"), "Update mode" (locked, "Scheduled" - Referral daily at 00:00,
 *       Withdrawal hourly), "Referral amount counts" / "Withdrawal amount counts" (read-only
 *       description text, not a control), "Accounts with $0" (locked, "Show \"Not ranked yet\""),
 *       "Tie-breaker" (locked, "Earliest to reach the amount ranks higher" - this answers the
 *       sheet's OQ-12 in general, though not the specific reversal-then-regain case
 *       {@code RANK-DAT-008} asks about).</li>
 * </ul>
 * Eight fields carry a lock icon and are described as fixed/read-only in the design: Homepage
 * widget entries, Full rankings page entries, Show "My Ranking" row, Mask usernames, Data period,
 * Update mode, Accounts with $0, Tie-breaker - {@link #LOCKED_FIELDS}. Only Amount display on
 * client is editable, with Cancel / Save changes.
 */
public class RankingSettingsPage extends BasePage {

    public static final String EXACT = "Exact amount";
    public static final String ROUNDED = "Rounded";
    public static final String PARTIAL_MASK = "Partial mask";

    /** The field labels the design marks locked/fixed - see the class Javadoc. */
    public static final java.util.List<String> LOCKED_FIELDS = java.util.List.of(
            "Homepage widget entries", "Full rankings page entries", "Show \"My Ranking\" row",
            "Mask usernames", "Data period", "Update mode", "Accounts with $0", "Tie-breaker");

    private final Locator saveButton;
    private final Locator cancelButton;
    private final Locator previewBox;

    public RankingSettingsPage(Page page) {
        super(page);
        this.saveButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save changes"));
        this.cancelButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel").setExact(true));
        this.previewBox = page.getByText("Client preview", new Page.GetByTextOptions().setExact(false))
                .locator("xpath=ancestor::*[self::div][1]");
    }

    public boolean isShowing() {
        return saveButton.count() > 0;
    }

    /**
     * True when the field at this label carries a lock icon or a disabled control - false when the
     * field could not be found at all, which the caller should treat as "not found", not "unlocked".
     */
    public boolean isLocked(String label) {
        Locator field = fieldRow(label);
        if (field.count() == 0) {
            return false;
        }
        Locator disabledControl = field.first().locator("[disabled], [aria-disabled='true']");
        boolean lockIcon = field.first().locator("svg, i, [class*='lock' i]").count() > 0;
        return disabledControl.count() > 0 || lockIcon;
    }

    /** The free-text description under a field's label, e.g. "Referral amount counts". */
    public String descriptionOf(String label) {
        Locator field = fieldRow(label);
        return field.count() == 0 ? "" : field.first().innerText().replaceAll("\\s+", " ").trim();
    }

    private Locator fieldRow(String label) {
        return page.getByText(label, new Page.GetByTextOptions().setExact(false)).first()
                .locator("xpath=ancestor::*[self::div][1]");
    }

    /** The currently-selected Amount display option - {@link #EXACT}, {@link #ROUNDED} or {@link #PARTIAL_MASK}. */
    public String amountDisplayOption() {
        for (String option : java.util.List.of(EXACT, ROUNDED, PARTIAL_MASK)) {
            Locator radio = page.locator("label, div").filter(new Locator.FilterOptions().setHasText(option))
                    .locator("input[type=radio]");
            if (radio.count() > 0 && radio.first().isChecked()) {
                return option;
            }
        }
        return "";
    }

    public void selectAmountDisplay(String option) {
        CaseLog.step("Selecting Amount display = \"" + option + "\".");
        page.getByText(option, new Page.GetByTextOptions().setExact(true)).first().click();
        page.waitForTimeout(300);
    }

    public String previewText() {
        return previewBox.count() == 0 ? "" : previewBox.first().innerText().replaceAll("\\s+", " ").trim();
    }

    /** Clicks Save changes and returns what the page said, OK pressed - "" when nothing appeared. */
    public String save() {
        CaseLog.step("Clicking Save changes.");
        saveButton.click();
        return PromptMessage.readAndClose(page, 4000);
    }

    public void cancel() {
        CaseLog.step("Clicking Cancel.");
        cancelButton.click();
    }
}
