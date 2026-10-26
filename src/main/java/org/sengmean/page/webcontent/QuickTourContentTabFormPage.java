package org.sengmean.page.webcontent;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.page.tool.NotificationFormPage;
import org.sengmean.report.CaseLog;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <b>Add content tab</b> and its Modify twin, under Web content management &gt; Quick Tour
 * Banner - one page, read from the client workbook ({@code docs/test-cases/quick-tour.html},
 * rows {@code QT-A-FORM_*} and {@code QT-A-OVR_*}).
 *
 * <h2>Measured against the live screen (2026-10-24)</h2>
 * Shaped after {@code ShareLinkFormPage} (one Title per language, an Override section, Order)
 * with a second per-language image slot added for Phone, since the sheet names two images -
 * Desktop and Phone - per language rather than Share Link's one. The guessed locators did not
 * survive contact with the real markup: the Title input carries no {@code name} or
 * {@code placeholder} (found by its {@code label.control-label} instead), the submit button
 * reads <b>"Add content tab"</b> on Add and only <b>"Save changes"</b> on Modify (matched by
 * {@code type=submit} rather than its name), "Return to list" is actually a link reading
 * <b>"Return »"</b>, and each image slot's size hint sits in the field's {@code ancestor::div[1]},
 * not a following sibling of the {@code input}.
 *
 * <h2>Shape of the screen, per the sheet</h2>
 * <ul>
 *   <li>Twelve language tabs, same set {@code NotificationFormPage} and {@code ShareLinkFormPage}
 *       already use. Required: 繁體, 简体, English ({@code QT-A-FORM_N14} treats every other
 *       language as optional, title-only accepted).</li>
 *   <li>Per language: a <b>Title</b> field, a <b>Desktop image</b> slot (hint text: recommended
 *       2788 x 796 px) and a <b>Phone image</b> slot (hint text: recommended 750 x 264 px) -
 *       {@code QT-A-FORM_P04}.</li>
 *   <li>An <b>Order</b> field, shared across languages, which the sheet says must refuse letters,
 *       negative numbers and blank ({@code QT-A-FORM_N12}) - read as "the field does not allow
 *       the keystroke" rather than "save then show a message", since the row's own wording is
 *       "The field is not allow to input".</li>
 *   <li>An <b>Override above contents to:</b> section, 繁體 tab only ({@code QT-A-OVR_N01}),
 *       copying Title, Desktop image and Phone image together into every ticked language
 *       ({@code QT-A-OVR_P10}-{@code P12}). The ticks themselves do not persist past a save and
 *       reopen - only the copied content does ({@code QT-A-OVR_P11}).</li>
 *   <li><b>Save changes</b> / <b>Return to list</b> - named after the sibling screens'
 *       {@code Save changes} wording rather than Share Link's own "Add share link" / "Save",
 *       since the sheet's own steps say "Save changes" throughout.</li>
 * </ul>
 */
public class QuickTourContentTabFormPage extends BasePage {

    /** The twelve language tabs, the same list and order {@code NotificationFormPage} already established. */
    public static final List<String> LANGUAGE_TABS = NotificationFormPage.LANGUAGE_TABS;

    public static final List<String> REQUIRED_LANGUAGES = List.of("繁體", "简体", "English");

    private final Locator titleInput;
    private final Locator desktopImageInput;
    private final Locator phoneImageInput;
    private final Locator orderInput;
    private final Locator allLanguagesOverride;
    private final Locator saveButton;
    private final Locator returnButton;

    public QuickTourContentTabFormPage(Page page) {
        super(page);
        this.titleInput = page.locator("xpath=//label[starts-with(normalize-space(.),'Title')]/following::input[1]");
        this.desktopImageInput = page.locator("input[type=file]").nth(0);
        this.phoneImageInput = page.locator("input[type=file]").nth(1);
        this.orderInput = page.locator("input[type=number], input[name*='order' i]").first();
        this.allLanguagesOverride = page.getByText("All languages", new Page.GetByTextOptions().setExact(true))
                .locator("xpath=preceding-sibling::input[@type='checkbox'][1] | following-sibling::input[@type='checkbox'][1]");
        // The submit button reads "Add content tab" on Add and "Save changes" on Modify - match by
        // type, not name, since exactly one submit button is on the form either way.
        this.saveButton = page.locator("button[type=submit]");
        this.returnButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Return »"));
    }

    public void awaitOpen() {
        titleInput.waitFor(new Locator.WaitForOptions().setTimeout(20_000));
        page.waitForTimeout(400);
    }

    public boolean isOpen() {
        return titleInput.count() > 0 && saveButton.count() > 0;
    }

    // ---------------------------------------------------------------
    // Language tabs
    // ---------------------------------------------------------------

    private Locator tab(String language) {
        return page.getByText(language, new Page.GetByTextOptions().setExact(true)).filter(new Locator.FilterOptions().setVisible(true));
    }

    public void switchTab(String language) {
        tab(language).first().click();
        page.waitForTimeout(300);
    }

    // ---------------------------------------------------------------
    // Per-language fields
    // ---------------------------------------------------------------

    public void setTitle(String value) {
        titleInput.fill(value);
    }

    public String titleValue() {
        return titleInput.inputValue();
    }

    /** The recommended-size hint text under the Desktop image slot - QT-A-FORM_P04. */
    public String desktopHintText() {
        return hintNear(desktopImageInput);
    }

    /** The recommended-size hint text under the Phone image slot - QT-A-FORM_P04. */
    public String phoneHintText() {
        return hintNear(phoneImageInput);
    }

    private String hintNear(Locator field) {
        Locator hint = field.locator("xpath=ancestor::div[1]").getByText(Pattern.compile("\\d+\\s*x\\s*\\d+"));
        return hint.count() == 0 ? "" : hint.first().innerText().trim();
    }

    public void uploadDesktopImage(Path file) {
        PromptMessage.clearStray(page);
        CaseLog.step("Uploading Desktop image " + file.getFileName() + ".");
        desktopImageInput.setInputFiles(file);
        page.waitForTimeout(500);
    }

    public void uploadPhoneImage(Path file) {
        PromptMessage.clearStray(page);
        CaseLog.step("Uploading Phone image " + file.getFileName() + ".");
        phoneImageInput.setInputFiles(file);
        page.waitForTimeout(500);
    }

    /** The validation text shown for a missing/invalid image slot - "" if none. Not yet confirmed. */
    public String imageValidationText(Locator slot) {
        Locator error = slot.locator("xpath=following-sibling::*").filter(new Locator.FilterOptions().setHasText("required"));
        return error.count() == 0 ? "" : error.first().innerText().trim();
    }

    public Locator desktopImageField() {
        return desktopImageInput;
    }

    public Locator phoneImageField() {
        return phoneImageInput;
    }

    // ---------------------------------------------------------------
    // Order
    // ---------------------------------------------------------------

    public void setOrder(String value) {
        orderInput.fill(value);
    }

    public String orderValue() {
        return orderInput.inputValue();
    }

    public Locator orderField() {
        return orderInput;
    }

    // ---------------------------------------------------------------
    // Override above contents to: - 繁體 tab only, QT-A-OVR_N01
    // ---------------------------------------------------------------

    private Locator overrideSection() {
        return page.getByText("Override above contents to", new Page.GetByTextOptions().setExact(false));
    }

    /** True when the current tab shows an Override section at all - only 繁體 should. */
    public boolean overrideSectionShown() {
        return overrideSection().count() > 0;
    }

    private Locator overrideCheckbox(String language) {
        return page.getByText(language, new Page.GetByTextOptions().setExact(true))
                .locator("xpath=ancestor::label[1]").locator("input[type=checkbox]");
    }

    public void tickOverride(String language) {
        Locator box = overrideCheckbox(language);
        if (box.count() > 0 && !box.first().isChecked()) {
            box.first().click();
        }
    }

    public void untickOverride(String language) {
        Locator box = overrideCheckbox(language);
        if (box.count() > 0 && box.first().isChecked()) {
            box.first().click();
        }
    }

    public boolean isOverrideTicked(String language) {
        Locator box = overrideCheckbox(language);
        return box.count() > 0 && box.first().isChecked();
    }

    public void tickOverrideAll() {
        if (allLanguagesOverride.count() > 0 && !allLanguagesOverride.first().isChecked()) {
            allLanguagesOverride.first().click();
        }
    }

    public void untickOverrideAll() {
        if (allLanguagesOverride.count() > 0 && allLanguagesOverride.first().isChecked()) {
            allLanguagesOverride.first().click();
        }
    }

    public boolean isOverrideAllTicked() {
        return allLanguagesOverride.count() > 0 && allLanguagesOverride.first().isChecked();
    }

    // ---------------------------------------------------------------
    // Save / Return
    // ---------------------------------------------------------------

    public Locator saveButton() {
        return saveButton;
    }

    public boolean isSaveDisabled() {
        return "true".equals(saveButton.getAttribute("disabled")) || saveButton.isDisabled();
    }

    /**
     * Clicks Save changes and returns what the message after it said, OK pressed - "" if nothing
     * appeared. Presses <b>Confirm</b> first if a two-button prompt comes first, the same
     * {@link PromptMessage#confirmAndRead} shape Member Benefits' own save needed once measured
     * live - not yet confirmed whether this screen raises the same prompt, but
     * {@code confirmAndRead} is a safe superset of a plain single-button read either way.
     */
    public String save() {
        PromptMessage.clearStray(page);
        CaseLog.step("Clicking Save changes.");
        saveButton.click();
        return PromptMessage.confirmAndRead(page, 4000);
    }

    public void clickReturn() {
        CaseLog.step("Clicking Return to list.");
        returnButton.click();
        page.waitForTimeout(500);
    }
}
