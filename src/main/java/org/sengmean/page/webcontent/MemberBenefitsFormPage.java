package org.sengmean.page.webcontent;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.page.tool.NotificationFormPage;
import org.sengmean.report.CaseLog;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * <b>Add Member Benefit</b> and its Edit twin - one page, measured live on admin-test 2026-10-22
 * at {@code /cms/member-benefit-form} ({@code ?id=N} for Edit).
 *
 * <h2>Shape of the screen</h2>
 * Three boxes, all sharing the same {@code box box-default} class, told apart by their own
 * heading text rather than a distinct class:
 * <ul>
 *   <li><b>Content details ({@code <language>})</b> - the per-language <b>Member benefit
 *       name</b> input and an <b>Override above contents to:</b> row of checkboxes (an
 *       {@code All languages} one plus one per other language), copying the current tab's name
 *       into the ticked ones. Unlike Share Link there is no per-language image or link here -
 *       the icon is shared, and a banner's link and images are themselves per-language, inside
 *       the Banners panel.</li>
 *   <li><b>General settings</b> - shared across every language: <b>Benefit icon</b> (one file,
 *       no per-language variant), <b>Status</b> (Active/Inactive, default Active) and
 *       <b>Display order</b> (the benefit's own order, separate from a banner's own order
 *       below).</li>
 *   <li><b>Banners ({@code <language>})</b> - one block per banner, added with <b>Add banner</b>
 *       (the header button, and a second one inside the empty-state message - both do the same
 *       thing, which is why {@link #addBanner()} takes the first match). Each block carries
 *       <b>Small banner / Mobile pop-up / PC pop-up</b> upload slots in that order, a
 *       <b>Link</b> field (plain text, shared across every language tab - not per-language) and
 *       its own <b>Display order</b>. A banner heading carries a status label -
 *       {@code label label-warning} reading "Inactive - upload all 3 images" until every slot
 *       has an image; measured live 2026-10-22 by uploading all three, it then simply reads
 *       <b>"Active"</b> - not "All 3 uploads complete" as the workbook's own wording for
 *       {@code MB-ADM-ADD-P023} suggested. <b>Delete</b> on a banner removes it with no
 *       confirmation (measured: {@link PromptMessage} is not raised for this one action, unlike
 *       the list's own Delete).</li>
 * </ul>
 *
 * <h2>Validation, and a successful save, both go through the jQuery UI {@link PromptMessage} dialog</h2>
 * Every negative row in the client's workbook ({@code MB-ADM-ADD-N*}, {@code MB-ADM-EDT-N*})
 * describes a refusal as "Prompt message ..." - confirmed live: Delete on the list raises
 * exactly the {@code .ui-dialog} component {@link PromptMessage} already drives for Other
 * Category and Notification ("This action removes the member benefit and all of its banners.",
 * Confirm / Cancel). There is no inline per-field red text or tab error marker here the way
 * Share Link's spec describes - every refusal this project has record of arrives as one of
 * these dialogs.
 *
 * <p><b>A valid save is not a straight POST either</b> - measured live 2026-10-22: clicking
 * <b>Save member benefit</b> first raises a two-button "Confirm to add?" {@link PromptMessage},
 * and only pressing its <b>Confirm</b> actually writes anything. {@link #save()} presses it via
 * {@link PromptMessage#confirmAndRead}; an earlier version of this method called
 * {@link PromptMessage#readAndClose} instead, which only matches a single-button dialog and so
 * silently left every successful save unconfirmed - every row depending on one created nothing
 * and then failed its own "listed" assertion.
 *
 * <h2>Two more things measured live that the workbook's own wording did not quite match</h2>
 * A complete banner's badge reads plainly <b>"Active"</b>, not "All 3 uploads complete" as
 * {@code MB-ADM-ADD-P023}'s wording suggested. An uploaded slot offers <b>Replace image</b> and
 * <b>Delete image</b>, not "Remove" - {@link #removeSlot} drives the real label.
 */
public class MemberBenefitsFormPage extends BasePage {

    /** The twelve language tabs, in the order measured live - same list and order Notification's form uses. */
    public static final List<String> LANGUAGE_TABS = NotificationFormPage.LANGUAGE_TABS;

    public static final List<String> REQUIRED_LANGUAGES = List.of("繁體", "简体", "English");

    /** Index of a banner's own upload slots, in DOM/visual order (confirmed live 2026-10-22). */
    public static final int SMALL_BANNER = 0;
    public static final int MOBILE_POPUP = 1;
    public static final int PC_POPUP = 2;

    private final Locator heading;
    private final Locator returnLink;
    private final Locator saveButton;
    private final Locator contentDetailsBox;
    private final Locator generalSettingsBox;
    private final Locator bannersBox;
    private final Locator nameInput;
    private final Locator addBannerButton;
    private final Locator bannerBoxes;

    public MemberBenefitsFormPage(Page page) {
        super(page);
        this.heading = page.locator("h1").first();
        this.returnLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Return »"));
        this.saveButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save member benefit"));
        this.contentDetailsBox = page.locator(".box").filter(new Locator.FilterOptions().setHasText("Content details"));
        this.generalSettingsBox = page.locator(".box").filter(new Locator.FilterOptions().setHasText("General settings"));
        this.bannersBox = page.locator(".box").filter(new Locator.FilterOptions().setHasText("Banners"));
        this.nameInput = page.locator("input[placeholder*='enefit name' i]");
        this.addBannerButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add banner").setExact(false));
        this.bannerBoxes = page.locator(".banner-box");
    }

    public void awaitOpen() {
        nameInput.waitFor(new Locator.WaitForOptions().setTimeout(20_000));
        page.waitForTimeout(400);
    }

    public boolean isOpen() {
        return nameInput.count() > 0 && saveButton.count() > 0;
    }

    public String headingText() {
        return heading.count() == 0 ? "" : heading.first().innerText().trim();
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
    // Content details - the per-language name
    // ---------------------------------------------------------------

    public void setName(String value) {
        nameInput.fill(value);
    }

    public String nameValue() {
        return nameInput.inputValue();
    }

    /** The "X / 100" (or similar) counter text under the name field - "" if the screen does not show one. */
    public String nameCounterText() {
        Locator counter = contentDetailsBox.getByText(java.util.regex.Pattern.compile("\\d+\\s*/\\s*\\d+"));
        return counter.count() == 0 ? "" : counter.first().innerText().trim();
    }

    // ---------------------------------------------------------------
    // Override above contents to:
    // ---------------------------------------------------------------

    private Locator overrideAllCheckbox() {
        return page.locator("label.copy-lang-all-item input[type=checkbox]");
    }

    private Locator overrideCheckbox(String language) {
        return page.locator("label.copy-lang-item:not(.copy-lang-all-item)")
                .filter(new Locator.FilterOptions().setHasText(language))
                .locator("input[type=checkbox]");
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
        if (overrideAllCheckbox().count() > 0 && !overrideAllCheckbox().first().isChecked()) {
            overrideAllCheckbox().first().click();
        }
    }

    public void untickOverrideAll() {
        if (overrideAllCheckbox().count() > 0 && overrideAllCheckbox().first().isChecked()) {
            overrideAllCheckbox().first().click();
        }
    }

    public boolean isOverrideAllTicked() {
        return overrideAllCheckbox().count() > 0 && overrideAllCheckbox().first().isChecked();
    }

    public boolean isOverrideTicked(String language) {
        Locator box = overrideCheckbox(language);
        return box.count() > 0 && box.first().isChecked();
    }

    /** True when {@code language}'s own tab offers no override checkbox for itself - it is excluded from its own list. */
    public boolean overrideOffersItself(String language) {
        return overrideCheckbox(language).count() > 0;
    }

    // ---------------------------------------------------------------
    // General settings - icon, status, the benefit's own display order
    // ---------------------------------------------------------------

    private Locator iconInput() {
        return generalSettingsBox.locator("input[type=file]");
    }

    public void uploadIcon(Path file) {
        PromptMessage.clearStray(page);
        CaseLog.step("Uploading benefit icon " + file.getFileName() + ".");
        iconInput().setInputFiles(file);
        page.waitForTimeout(500);
    }

    public boolean iconPreviewShown() {
        return generalSettingsBox.locator("img[src^='blob:'], img[src^='data:'], img[alt*='preview' i]").count() > 0;
    }

    private Locator statusSelect() {
        return generalSettingsBox.locator("select");
    }

    public boolean isActive() {
        return "1".equals(statusSelect().inputValue());
    }

    public void setActive(boolean value) {
        statusSelect().selectOption(new SelectOption().setLabel(value ? "Active" : "Inactive"));
    }

    private Locator displayOrderInput() {
        return generalSettingsBox.locator("input[type=number]");
    }

    public void setDisplayOrder(String value) {
        displayOrderInput().fill(value);
    }

    public String displayOrderValue() {
        return displayOrderInput().inputValue();
    }

    // ---------------------------------------------------------------
    // Banners
    // ---------------------------------------------------------------

    public int bannerCount() {
        return bannerBoxes.count();
    }

    /** Clicks Add banner (the header button, or the empty-state one - both open the same new banner) and returns its index. */
    public int addBanner() {
        PromptMessage.clearStray(page);
        CaseLog.step("Clicking Add banner.");
        int before = bannerCount();
        addBannerButton.first().click();
        page.waitForTimeout(400);
        return before;
    }

    public void removeBanner(int index) {
        PromptMessage.clearStray(page);
        CaseLog.step("Clicking Delete on banner " + (index + 1) + " (no confirmation expected).");
        bannerBoxes.nth(index).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete")).click();
        page.waitForTimeout(400);
    }

    /** The banner's own status label, e.g. "Inactive - upload all 3 images" or, once complete, "Active". */
    public String bannerBadgeText(int index) {
        Locator badge = bannerBoxes.nth(index).locator(".label");
        return badge.count() == 0 ? "" : badge.first().innerText().trim();
    }

    /** True once the banner's badge no longer reads the "Inactive" / "upload" wording - all 3 images are in. */
    public boolean isBannerComplete(int index) {
        String badge = bannerBadgeText(index).toLowerCase(Locale.ROOT);
        return !badge.isEmpty() && !badge.contains("inactive") && !badge.contains("upload");
    }

    private Locator slotInput(int bannerIndex, int slot) {
        return bannerBoxes.nth(bannerIndex).locator("input[type=file]").nth(slot);
    }

    public void uploadSlot(int bannerIndex, int slot, Path file) {
        PromptMessage.clearStray(page);
        CaseLog.step("Uploading " + slotName(slot) + " " + file.getFileName() + " to banner " + (bannerIndex + 1) + ".");
        slotInput(bannerIndex, slot).setInputFiles(file);
        page.waitForTimeout(500);
    }

    public void uploadSmallBanner(int bannerIndex, Path file) {
        uploadSlot(bannerIndex, SMALL_BANNER, file);
    }

    public void uploadMobilePopup(int bannerIndex, Path file) {
        uploadSlot(bannerIndex, MOBILE_POPUP, file);
    }

    public void uploadPcPopup(int bannerIndex, Path file) {
        uploadSlot(bannerIndex, PC_POPUP, file);
    }

    /** True once a slot's upload area shows a preview rather than the "Drop file here" placeholder. */
    public boolean isSlotUploaded(int bannerIndex, int slot) {
        Locator box = slotInput(bannerIndex, slot).locator("xpath=ancestor::div[1]");
        return box.locator("img[src^='blob:'], img[src^='data:']").count() > 0
                || !box.getByText("Drop file here").isVisible();
    }

    /**
     * Removes the single uploaded image from one slot, leaving the slot empty again - the sheet's
     * "Click Remove on small/mobile/PC image" rows. Measured live 2026-10-22: once a slot holds an
     * image it offers <b>Replace image</b> and <b>Delete image</b>, not "Remove" as the workbook's
     * own wording suggested.
     */
    public void removeSlot(int bannerIndex, int slot) {
        PromptMessage.clearStray(page);
        CaseLog.step("Removing " + slotName(slot) + " from banner " + (bannerIndex + 1) + ".");
        Locator box = slotInput(bannerIndex, slot).locator("xpath=ancestor::div[2]");
        box.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete image").setExact(false)).first().click();
        page.waitForTimeout(400);
    }

    private static String slotName(int slot) {
        return switch (slot) {
            case SMALL_BANNER -> "the small banner";
            case MOBILE_POPUP -> "the mobile pop-up";
            case PC_POPUP -> "the PC pop-up";
            default -> "slot " + slot;
        };
    }

    public Locator bannerLinkField(int bannerIndex) {
        return bannerBoxes.nth(bannerIndex).locator("input[type=text]");
    }

    public void setBannerLink(int bannerIndex, String url) {
        bannerLinkField(bannerIndex).fill(url);
    }

    public String bannerLinkValue(int bannerIndex) {
        return bannerLinkField(bannerIndex).inputValue();
    }

    public Locator bannerOrderField(int bannerIndex) {
        return bannerBoxes.nth(bannerIndex).locator("input[type=number]");
    }

    public void setBannerOrder(int bannerIndex, String value) {
        bannerOrderField(bannerIndex).fill(value);
    }

    public String bannerOrderValue(int bannerIndex) {
        return bannerOrderField(bannerIndex).inputValue();
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
     * Clicks Save member benefit, presses <b>Confirm</b> on the "Confirm to add?" / "Confirm to
     * save?" prompt the screen asks first, and returns what the message after it said, OK pressed
     * - "" if nothing appeared.
     *
     * <h2>Measured live 2026-10-22, and the gap this closed</h2>
     * A valid save does not go straight through: it raises a two-button {@link PromptMessage}
     * ("Confirm to add?", Confirm / Cancel) before anything is written, the same
     * {@code $confirm("confirm_to_add")} step {@link PromptMessage#confirmAndRead} already exists
     * for on Other Category. The first version of this method called
     * {@link PromptMessage#readAndClose} instead, which only matches a single-button dialog - it
     * saw no match against the two-button Confirm prompt, waited out its timeout doing nothing,
     * and returned "" while the prompt sat open and unconfirmed. Every row that depended on a
     * successful save actually created nothing and then failed its own "benefit should be listed"
     * assertion. A refusal (one button, straight to the validation message) still works the same
     * way either method - {@code confirmAndRead} reads that directly when no Confirm step comes
     * first.
     */
    public String save() {
        PromptMessage.clearStray(page);
        CaseLog.step("Clicking Save member benefit.");
        saveButton.click();
        return PromptMessage.confirmAndRead(page, 4000);
    }

    public void clickReturn() {
        CaseLog.step("Clicking Return ».");
        returnLink.click();
        page.waitForTimeout(500);
    }
}
