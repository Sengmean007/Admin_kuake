package org.sengmean.page.webcontent;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Add Share Link</b> and its Modify twin - one page, read from the client's workbook and the
 * Figma spec note ("Back office &gt; Web content management &gt; Share link").
 *
 * <h2>What the spec note says</h2>
 * <pre>
 *   Add Share Link                                  Home Page &gt; Web Content Management System &gt; Share Link &gt; Add Share Link
 *   [繁體*][简体*][English*][日語][ภาษาไทย][Vietnam][Spain][Malaysia][Indonesia][Filipino][Bengali][Portuguese]
 *   Title *        [                              ]  0/20
 *   Text content * [                              ]  0/60
 *   Card image *   [upload / preview, 16:9, 704x396 recommended, JPG/PNG/WEBP, max 2MB]
 *   URL link *     [https://                       ]
 *   Override above contents to:  [ ] All languages  [ ] 简体 [ ] English [ ] 日語 ... (not the current tab)
 *   Order [0]   Active [ ]
 *   [Add share link] / [Save]        Return »
 * </pre>
 * Required languages are 繁體, 简体, English (marked {@code *}); the other nine are optional - empty
 * means not shown to members of that language. Missing required content turns that tab red with a
 * "!" and raises one error box on Save. "Override above contents to:" copies the <em>current</em>
 * tab's Title / Text content / Card image / URL link into every ticked language, replacing what is
 * there; it excludes the current tab itself. Order and Active are shared across every language;
 * Active is unticked by default.
 */
public class ShareLinkFormPage extends BasePage {

    /** The twelve language tabs, reusing the same list and order Notification's form already established. */
    public static final List<String> LANGUAGE_TABS = org.sengmean.page.tool.NotificationFormPage.LANGUAGE_TABS;

    public static final List<String> REQUIRED_LANGUAGES = List.of("繁體", "简体", "English");

    private final Locator titleInput;
    private final Locator textInput;
    private final Locator urlInput;
    private final Locator orderInput;
    private final Locator activeCheckbox;
    private final Locator imageInput;
    private final Locator deleteImageButton;
    private final Locator allLanguagesOverride;
    private final Locator addOrSaveButton;
    private final Locator returnButton;

    public ShareLinkFormPage(Page page) {
        super(page);
        this.titleInput = page.locator("input[placeholder*='itle' i], input[name*='title' i]").first();
        this.textInput = page.locator("textarea").first();
        this.urlInput = page.locator("input[placeholder*='https' i], input[name*='url' i]").first();
        this.orderInput = page.locator("input[type=number], input[name*='order' i]").first();
        this.activeCheckbox = page.getByText("Active", new Page.GetByTextOptions().setExact(true)).first()
                .locator("xpath=preceding-sibling::input[@type='checkbox'][1] | following-sibling::input[@type='checkbox'][1]");
        this.imageInput = page.locator("input[type=file]");
        this.deleteImageButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Delete image"));
        this.allLanguagesOverride = page.getByText("All languages", new Page.GetByTextOptions().setExact(true))
                .locator("xpath=preceding-sibling::input[@type='checkbox'][1] | following-sibling::input[@type='checkbox'][1]");
        this.addOrSaveButton = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("^(Add share link|Save)$")));
        this.returnButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Return »"));
    }

    public void awaitOpen() {
        titleInput.waitFor(new Locator.WaitForOptions().setTimeout(20_000));
        page.waitForTimeout(400);
    }

    public boolean isOpen() {
        return titleInput.count() > 0 && addOrSaveButton.count() > 0;
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

    /** True when the tab carries the red "!" the spec note describes for missing required content. */
    public boolean tabHasErrorMarker(String language) {
        Locator marker = tab(language).first().locator("xpath=following-sibling::*[1] | ..").getByText("!", new Locator.GetByTextOptions().setExact(true));
        return marker.count() > 0;
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

    /** The "X / 20" counter text under Title - "" if not found. */
    public String titleCounterText() {
        return counterNear(titleInput);
    }

    public void setText(String value) {
        textInput.fill(value);
    }

    public String textValue() {
        return textInput.inputValue();
    }

    /** The "X / 60" counter text under Text content - "" if not found. */
    public String textCounterText() {
        return counterNear(textInput);
    }

    private String counterNear(Locator field) {
        Locator counter = field.locator("xpath=following-sibling::*").getByText(Pattern.compile("\\d+\\s*/\\s*\\d+"));
        return counter.count() == 0 ? "" : counter.first().innerText().trim();
    }

    public void setUrl(String value) {
        urlInput.fill(value);
    }

    public String urlValue() {
        return urlInput.inputValue();
    }

    public void uploadImage(Path file) {
        CaseLog.step("Uploading " + file.getFileName() + ".");
        imageInput.setInputFiles(file);
        page.waitForTimeout(500);
    }

    public boolean imagePreviewShown() {
        return page.locator("img[src^='blob:'], img[src^='data:'], img[alt*='preview' i]").count() > 0;
    }

    public void deleteImage() {
        deleteImageButton.click();
    }

    /** The error text shown under a field, by the field's own locator - "" if none. */
    public String fieldError(Locator field) {
        Locator error = field.locator("xpath=following-sibling::*").filter(new Locator.FilterOptions().setHasText("⚠"));
        return error.count() == 0 ? "" : error.first().innerText().trim();
    }

    public Locator titleField() {
        return titleInput;
    }

    public Locator textField() {
        return textInput;
    }

    public Locator urlField() {
        return urlInput;
    }

    public Locator orderField() {
        return orderInput;
    }

    // ---------------------------------------------------------------
    // Shared fields
    // ---------------------------------------------------------------

    public void setOrder(String value) {
        orderInput.fill(value);
    }

    public String orderValue() {
        return orderInput.inputValue();
    }

    public boolean isActive() {
        return activeCheckbox.count() > 0 && activeCheckbox.first().isChecked();
    }

    public void setActive(boolean value) {
        if (isActive() != value) {
            activeCheckbox.first().click();
        }
    }

    // ---------------------------------------------------------------
    // Override above contents to:
    // ---------------------------------------------------------------

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

    public boolean isOverrideTicked(String language) {
        Locator box = overrideCheckbox(language);
        return box.count() > 0 && box.first().isChecked();
    }

    /** The helper text naming the current tab as the copy source, e.g. "will get the 繁體 content on save." */
    public String overrideHelperText() {
        Locator helper = page.getByText(Pattern.compile("will get the .* content on save", Pattern.CASE_INSENSITIVE));
        return helper.count() == 0 ? "" : helper.first().innerText().trim();
    }

    // ---------------------------------------------------------------
    // Save / Return
    // ---------------------------------------------------------------

    public Locator saveButton() {
        return addOrSaveButton;
    }

    public boolean isSaveDisabled() {
        return "true".equals(addOrSaveButton.getAttribute("disabled")) || addOrSaveButton.isDisabled();
    }

    /** Clicks Add share link / Save and returns the toast/prompt text, OK pressed - "" if nothing appeared. */
    public String save() {
        CaseLog.step("Clicking " + addOrSaveButton.first().innerText() + ".");
        addOrSaveButton.click();
        return PromptMessage.readAndClose(page, 4000);
    }

    /** The error box text shown above the save button on a failed save - "" if none. */
    public String errorBoxText() {
        Locator box = page.getByText("Cannot save", new Page.GetByTextOptions().setExact(false));
        return box.count() == 0 ? "" : box.first().innerText().trim();
    }

    public void clickReturn() {
        CaseLog.step("Clicking Return ».");
        returnButton.click();
        page.waitForTimeout(500);
    }

    /** "Created ... · Last updated ..." shown on Modify - "" on Add. */
    public String createdAndUpdatedText() {
        Locator text = page.getByText(Pattern.compile("Created.*Last updated", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
        return text.count() == 0 ? "" : text.first().innerText().replaceAll("\\s+", " ").trim();
    }
}
