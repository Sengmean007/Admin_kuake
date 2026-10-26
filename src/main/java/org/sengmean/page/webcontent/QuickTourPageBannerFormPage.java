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

/**
 * <b>Configure Page Banner</b>, under Web content management &gt; Quick Tour Banner - one fixed
 * record per currency (no list of its own), read from the client workbook
 * ({@code docs/test-cases/quick-tour.html}, rows {@code QT-A-PB_*}).
 *
 * <h2>Measured against the live screen (2026-10-24)</h2>
 * Shaped after {@code MemberBenefitsFormPage}'s own Banners block (a fixed pair of upload slots
 * per language, an Override section), but with no "Add banner" - the sheet describes exactly one
 * PC + one Mobile slot per language, not an open-ended list. Two guesses from the sheet did not
 * survive contact with the real markup: the {@code h1} reads only <b>"Configure Page Banner"</b>
 * (the "Quick Tour Banner · " half lives in the breadcrumb, a separate element), and "Return to
 * list" is actually a link reading <b>"Return »"</b>.
 *
 * <h2>Shape of the screen, per the sheet ({@code QT-A-PB_P01})</h2>
 * <ul>
 *   <li>Header reads "Configure Page Banner"; twelve language tabs; <b>PC horizontal banner</b>
 *       and <b>Mobile banner</b> upload slots per tab; an Override section; <b>Save changes</b> /
 *       <b>Return »</b>. <b>No Title, Order or Status field</b> - unlike every other Web content
 *       management form this project already drives.</li>
 *   <li>繁體, 简体, English are required (missing either slot on any of the three blocks the
 *       save with a named inline message, e.g. {@code QT-A-PB_N02}: "mobile images zh-TW is
 *       required."). Every other language tab is optional.</li>
 *   <li>Override section is 繁體-tab-only ({@code QT-A-PB_N06}), copies both PC and Mobile
 *       immediately into a ticked language ({@code QT-A-PB_P04}), and a once-ticked-then-unticked
 *       language keeps the copy but stops tracking further 繁體 changes ({@code QT-A-PB_P05}).</li>
 *   <li>Deleting a required slot's saved image blocks Save outright, with the previously saved
 *       image still live on the client ({@code QT-A-PB_N05}) - unlike a blank slot on Add, which
 *       the client has simply never seen.</li>
 *   <li>Only image files are accepted - a non-image (e.g. {@code .pdf}) raises "Prompt message
 *       Image extensions are only allowed jpg、jpeg、png、gif、svg、webp" ({@code QT-A-PB_N07}).</li>
 * </ul>
 */
public class QuickTourPageBannerFormPage extends BasePage {

    /** The twelve language tabs, the same list and order {@code NotificationFormPage} already established. */
    public static final List<String> LANGUAGE_TABS = NotificationFormPage.LANGUAGE_TABS;

    public static final List<String> REQUIRED_LANGUAGES = List.of("繁體", "简体", "English");

    private final Locator heading;
    private final Locator pcBannerInput;
    private final Locator mobileBannerInput;
    private final Locator allLanguagesOverride;
    private final Locator saveButton;
    private final Locator returnButton;

    public QuickTourPageBannerFormPage(Page page) {
        super(page);
        this.heading = page.locator("h1").first();
        this.pcBannerInput = page.locator("input[type=file]").nth(0);
        this.mobileBannerInput = page.locator("input[type=file]").nth(1);
        this.allLanguagesOverride = page.getByText("All languages", new Page.GetByTextOptions().setExact(true))
                .locator("xpath=preceding-sibling::input[@type='checkbox'][1] | following-sibling::input[@type='checkbox'][1]");
        this.saveButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save changes"));
        this.returnButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Return »"));
    }

    public void awaitOpen() {
        pcBannerInput.waitFor(new Locator.WaitForOptions().setTimeout(20_000));
        page.waitForTimeout(400);
    }

    public boolean isOpen() {
        return pcBannerInput.count() > 0 && saveButton.count() > 0;
    }

    public String headingText() {
        return heading.count() == 0 ? "" : heading.first().innerText().trim();
    }

    /** True when the screen offers no Title, Order or Status field at all - QT-A-PB_P01. */
    public boolean hasNoTitleOrderOrStatusFields() {
        return page.locator("input[name*='title' i]").count() == 0
                && page.locator("input[type=number]").count() == 0
                && page.locator("select").filter(new Locator.FilterOptions().setHasText("Active")).count() == 0;
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
    // PC horizontal banner / Mobile banner
    // ---------------------------------------------------------------

    public void uploadPcBanner(Path file) {
        PromptMessage.clearStray(page);
        CaseLog.step("Uploading PC horizontal banner " + file.getFileName() + ".");
        pcBannerInput.setInputFiles(file);
        page.waitForTimeout(500);
    }

    public void uploadMobileBanner(Path file) {
        PromptMessage.clearStray(page);
        CaseLog.step("Uploading Mobile banner " + file.getFileName() + ".");
        mobileBannerInput.setInputFiles(file);
        page.waitForTimeout(500);
    }

    public boolean isPcBannerUploaded() {
        return isSlotUploaded(pcBannerInput);
    }

    public boolean isMobileBannerUploaded() {
        return isSlotUploaded(mobileBannerInput);
    }

    private boolean isSlotUploaded(Locator input) {
        Locator box = input.locator("xpath=ancestor::div[1]");
        return box.locator("img[src^='blob:'], img[src^='data:']").count() > 0;
    }

    /**
     * Removes the saved image from a slot, leaving it empty - QT-A-PB_N05 (delete the saved 繁體
     * PC banner, then try to save). Mirrors {@code MemberBenefitsFormPage#removeSlot}'s own
     * "Delete image" label, not yet confirmed here.
     */
    public void removeSlot(Locator input) {
        PromptMessage.clearStray(page);
        CaseLog.step("Removing the uploaded image from a page banner slot.");
        Locator box = input.locator("xpath=ancestor::div[2]");
        box.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete image").setExact(false)).first().click();
        page.waitForTimeout(400);
    }

    public Locator pcBannerField() {
        return pcBannerInput;
    }

    public Locator mobileBannerField() {
        return mobileBannerInput;
    }

    /** The inline validation text naming a missing required slot, e.g. "mobile images zh-TW is required." - "" if none. */
    public String inlineValidationText() {
        Locator text = page.getByText(java.util.regex.Pattern.compile("images? .* is required", java.util.regex.Pattern.CASE_INSENSITIVE));
        return text.count() == 0 ? "" : text.first().innerText().trim();
    }

    // ---------------------------------------------------------------
    // Override above contents to: - 繁體 tab only, QT-A-PB_N06
    // ---------------------------------------------------------------

    public boolean overrideSectionShown() {
        return page.getByText("Override above contents to", new Page.GetByTextOptions().setExact(false)).count() > 0;
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

    // ---------------------------------------------------------------
    // Save / Return
    // ---------------------------------------------------------------

    public Locator saveButton() {
        return saveButton;
    }

    /**
     * Clicks Save changes and returns what the message after it said, OK pressed - "" if nothing
     * appeared. See {@code QuickTourContentTabFormPage#save} - same {@link PromptMessage#confirmAndRead}
     * shape, not yet confirmed this screen raises the same two-button prompt.
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
