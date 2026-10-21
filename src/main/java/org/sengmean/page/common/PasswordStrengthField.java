package org.sengmean.page.common;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Pattern;

/**
 * The password / confirm-password widget the client's "Add new Member &amp; Reset password" sheet
 * (V.3.20.0, imported 2026-10-21) describes for both <b>Member Management &gt; Member List &gt; Add
 * member</b> and <b>a member profile's Reset Password tab</b> - a masked field with a show/hide
 * icon, a live red-✕/green-✓ rule checklist, a Weak/Medium/Good strength label, and a submit
 * control that only enables once the password is Good and Confirm matches it.
 *
 * <h2>Measured live on admin-test, 2026-10-21: none of this exists yet</h2>
 * Both screens are real and reachable - {@code /member/add-member} and a member profile's "Reset
 * Password" tab - and their two password inputs are plain {@code <input type="password">} fields
 * with no show/hide icon, no helper text, no rule checklist and no strength label; the submit
 * button carries no {@code disabled} attribute in any state that was tried. Every one of these is
 * covered by an open GD ticket the sheet itself cites against the row that needs it (GD-190
 * through GD-199), the same shape as {@code NOT-CRT-028}/GD-137: the row is written against what
 * the ticket asks for and stays red until it ships, which is what {@code known-defect} is for.
 *
 * <p>So every lookup here is written defensively - it checks {@link Locator#count()} before
 * reading an attribute or clicking, rather than letting Playwright's default wait run out against
 * an element that is not coming. That keeps a known-defect row's failure fast and its message
 * exact, instead of a 6s timeout per missing control.
 *
 * <h2>Read from the Figma design, not guessed</h2>
 * Si gave the mockup (Platform project, "version 3.20.0" page, frames {@code BO / Member / Add
 * Member - 1..5} and {@code BO / Member / Reset Password - 1..5}, one frame per state: Default,
 * Typing-Weak, Typing-Medium, Good-mismatched, Ready). Both forms share one component, so every
 * constant below is the exact copy measured off those frames, not a paraphrase:
 * <ul>
 *   <li>The collapsed helper under an untouched field: {@link #HELPER_TEXT}.</li>
 *   <li>Once typing starts, a box headed {@link #CHECKLIST_HEADER} replaces it, listing the five
 *       {@link Rule} items verbatim, each with a red ✕ or green ✓.</li>
 *   <li>A strength pill sits inside the field, right side: {@link #STRENGTH_WEAK},
 *       {@link #STRENGTH_MEDIUM} (shown in the Figma example on an 8-character password missing
 *       only the number rule - note the design's own open question #4 flags that example as off by
 *       one character), or {@link #STRENGTH_GOOD} once all five rules pass.</li>
 *   <li>A mismatched Confirm password gets a red border and {@link #MISMATCH_ERROR} underneath -
 *       final wording is the design's own open question #3, so treat it as provisional.</li>
 *   <li>Submit stays disabled until Password is Good <em>and</em> Confirm matches exactly, per the
 *       design note "Add member is disabled until all 5 rules are met and the passwords match" -
 *       and the same note adds "Validate again on the server - the disabled button is not a
 *       security control", which is why the BO-API rows exist at all even with no endpoint yet to
 *       call.</li>
 * </ul>
 * The design leaves five questions open (its own "OPEN QUESTIONS" note): exact Medium/Good
 * thresholds, whether special characters are allowed or raise strength, the final mismatch
 * wording, its own Medium example being miscoloured, and whether a password containing the
 * username or phone number is blocked. None of that changes what is asserted here - it is cited
 * on the rows it touches.
 */
public final class PasswordStrengthField {

    /** The collapsed helper shown under an empty, untouched field. */
    public static final String HELPER_TEXT =
            "8-20 characters, with at least 1 uppercase letter, 1 lowercase letter and 1 number. No spaces.";

    /** The header of the live checklist box that replaces {@link #HELPER_TEXT} once typing starts. */
    public static final String CHECKLIST_HEADER = "Password must contain:";

    public static final String STRENGTH_WEAK = "Weak";
    public static final String STRENGTH_MEDIUM = "Medium";
    public static final String STRENGTH_GOOD = "Good";

    /** Shown under Confirm password on a mismatch - provisional, see the class Javadoc's open question #3. */
    public static final String MISMATCH_ERROR = "Passwords do not match.";

    /** The five rules the sheet and the Figma checklist both describe, with the checklist's exact wording. */
    public enum Rule {
        LENGTH("8-20 characters"),
        UPPERCASE("At least 1 uppercase letter (A-Z)"),
        LOWERCASE("At least 1 lowercase letter (a-z)"),
        NUMBER("At least 1 number (0-9)"),
        NO_SPACES("No spaces");

        /** The checklist item's exact wording, measured off the Figma frames. */
        public final String text;

        Rule(String text) {
            this.text = text;
        }
    }

    private final Page page;
    private final Locator passwordInput;
    private final Locator confirmInput;

    public PasswordStrengthField(Page page, Locator passwordInput, Locator confirmInput) {
        this.page = page;
        this.passwordInput = passwordInput;
        this.confirmInput = confirmInput;
    }

    // ---------------------------------------------------------------
    // Typing
    // ---------------------------------------------------------------

    /** Sets the password in one go - for rows that only care about the end state. */
    public void typePassword(String value) {
        passwordInput.fill(value);
    }

    /** Types the password one keystroke at a time - for rows that assert the checklist updates live. */
    public void typePasswordKeyByKey(String value) {
        passwordInput.fill("");
        passwordInput.pressSequentially(value);
    }

    /** Deletes the last character of whatever is in Password, keeping the rest. */
    public void backspacePassword() {
        passwordInput.press("Backspace");
    }

    public void typeConfirm(String value) {
        confirmInput.fill(value);
    }

    public void clear() {
        passwordInput.fill("");
        confirmInput.fill("");
    }

    public String passwordValue() {
        return passwordInput.inputValue();
    }

    public String confirmValue() {
        return confirmInput.inputValue();
    }

    // ---------------------------------------------------------------
    // Masking and the show/hide icon (GD-191 / GD-198)
    // ---------------------------------------------------------------

    public boolean passwordMasked() {
        return "password".equals(passwordInput.getAttribute("type"));
    }

    public boolean confirmMasked() {
        return "password".equals(confirmInput.getAttribute("type"));
    }

    /**
     * The show/hide icon beside a field, if the screen has one - not yet shipped on either form
     * (GD-191, GD-198). Looked for as a clickable sibling of the input inside its field wrapper.
     */
    private Locator showHideIcon(Locator input) {
        return input.locator("xpath=following-sibling::*[self::i or self::svg or self::button or self::span][1]");
    }

    /** True if a show/hide icon was found and clicked beside Password; false if none exists yet. */
    public boolean clickShowOnPassword() {
        return clickIcon(showHideIcon(passwordInput));
    }

    public boolean clickShowOnConfirm() {
        return clickIcon(showHideIcon(confirmInput));
    }

    private boolean clickIcon(Locator icon) {
        if (icon.count() == 0) {
            return false;
        }
        icon.first().click();
        return true;
    }

    // ---------------------------------------------------------------
    // Helper text and the live checklist (GD-192 / GD-198)
    // ---------------------------------------------------------------

    /** {@link #HELPER_TEXT}, shown under an empty, untouched field. */
    public boolean collapsedHelperShown() {
        return page.getByText(HELPER_TEXT, new Page.GetByTextOptions().setExact(true)).count() > 0;
    }

    /** {@link #CHECKLIST_HEADER}, shown once the admin starts typing. */
    public boolean checklistShown() {
        return page.getByText(CHECKLIST_HEADER, new Page.GetByTextOptions().setExact(true)).count() > 0;
    }

    /**
     * Whether a rule's checklist item reads as passed (true), failed (false), or was not found at
     * all (null - the checklist does not exist on the live screen yet).
     */
    public Boolean ruleState(Rule rule) {
        Locator item = ruleItem(rule);
        if (item.count() == 0) {
            return null;
        }
        Locator first = item.first();
        String cls = String.valueOf(first.getAttribute("class")).toLowerCase(java.util.Locale.ROOT);
        String text = String.valueOf(first.innerText());
        if (text.contains("✓") || cls.contains("pass") || cls.contains("valid") || cls.contains("green") || cls.contains("success")) {
            return true;
        }
        if (text.contains("✕") || text.contains("✗") || cls.contains("fail") || cls.contains("invalid")
                || cls.contains("red") || cls.contains("error")) {
            return false;
        }
        return null;
    }

    private Locator ruleItem(Rule rule) {
        return page.locator("li, .password-rule, .rule, .pwd-rule")
                .filter(new Locator.FilterOptions().setHasText(rule.text));
    }

    /** True only when every one of the five rules reads as passed - null/false otherwise. */
    public boolean allRulesGreen() {
        for (Rule rule : Rule.values()) {
            if (!Boolean.TRUE.equals(ruleState(rule))) {
                return false;
            }
        }
        return true;
    }

    /** {@link #STRENGTH_WEAK}/{@link #STRENGTH_MEDIUM}/{@link #STRENGTH_GOOD}, or "" when none is shown yet. */
    public String strengthLabel() {
        Locator label = page.getByText(Pattern.compile("^\\s*(" + STRENGTH_WEAK + "|" + STRENGTH_MEDIUM + "|" + STRENGTH_GOOD + ")\\s*$"));
        return label.count() > 0 ? label.first().innerText().trim() : "";
    }

    // ---------------------------------------------------------------
    // Confirm-password match and the submit control (GD-194 / GD-195 / GD-198)
    // ---------------------------------------------------------------

    /** {@link #MISMATCH_ERROR}, shown under Confirm password on a mismatch. */
    public boolean mismatchErrorShown() {
        return page.getByText(MISMATCH_ERROR, new Page.GetByTextOptions().setExact(true)).count() > 0;
    }

    /** True when the submit control carries a {@code disabled} attribute. */
    public static boolean isDisabled(Locator submit) {
        return submit.getAttribute("disabled") != null;
    }
}
