package org.sengmean.page.product;

import org.sengmean.page.common.PromptMessage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * <b>Create Other Category</b> and its Edit twin.
 *
 * <h2>What the capture shows</h2>
 * <pre>
 *   Create Other Category
 *   Configure one category icon and localized names for all 12 supported languages.
 *   Product / Other Category / Create
 *
 *   Category Name
 *   Select a language tab to edit its category name. Only the active language is shown.
 *   [繁體] [简体] [English] [日語] [ภาษาไทย] [Vietnam] [Spain] [Malaysia] [Indonesia] [Filipino] [Bengali] [Portuguese]
 *     Category name (繁體)
 *     Shown on the client UI when 繁體 is selected.
 *     [熱門遊戲                                   ]
 *
 *   General settings
 *   +------------------------+   Status          [Active v]
 *   | Upload category icon   |   Display order
 *   | PNG, JPG or SVG · one  |   Lower numbers appear first.
 *   +------------------------+   [01]
 *                                               [Cancel] [Save &amp; manage games]
 * </pre>
 *
 * <h2>One input, twelve tabs</h2>
 * There is a single Category name field, relabelled for whichever tab is active - "Category name
 * (繁體)". So there is no "the English input" to address: every name method selects the tab
 * first, then reads or writes the one field. {@link #nameFor(String)} leaves the form on the tab
 * it read.
 *
 * <p>The tab strip is found by its labels, {@link #LANGUAGE_TABS}, rather than by a class name the
 * capture cannot show. If the real strip spells a language differently, correct the list here.
 *
 * <h2>Measured against the shipped screen, 2026-09-25</h2>
 * The spellings were right; nearly everything else about the markup was not:
 * <ul>
 *   <li>The tabs are Bootstrap {@code ul.nav-tabs} anchors, and no label is tied to its input, so
 *       neither the tab role nor {@code getByLabel} finds anything. The name field is the active
 *       pane's text input, and Display order is the form's {@code type=number} input.</li>
 *   <li><b>There is no Status select.</b> Status is a "Do you want to display" checkbox, ticked by
 *       default. {@link #setStatus} and {@link #status} keep the capture's Active / Inactive words
 *       and translate them to ticked / unticked.</li>
 *   <li><b>There is no Cancel.</b> The way out without saving is <b>Return »</b>.</li>
 *   <li>The icon uploads to storage the moment it is chosen, and the drop zone swaps to a preview
 *       with Replace image / Delete image. Edit's button reads <b>Save changes</b>.</li>
 *   <li>Validation arrives in the site's Prompt message dialog ({@link PromptMessage}), not inline.
 *       The hints under the fields are {@code .help-block}, so that class is no longer read as a
 *       message: it made every form look as if it were complaining.</li>
 * </ul>
 */
public class OtherCategoryFormPage extends BasePage {

    /** The twelve tabs, in the capture's order. */
    public static final List<String> LANGUAGE_TABS = List.of(
            "繁體", "简体", "English", "日語", "ภาษาไทย", "Vietnam",
            "Spain", "Malaysia", "Indonesia", "Filipino", "Bengali", "Portuguese");

    /** "Create Other Category"; Edit is expected to read "Edit Other Category". */
    private static final Pattern HEADING =
            Pattern.compile("^\\s*(Create|Edit)\\s+Other Category\\s*$", Pattern.CASE_INSENSITIVE);

    /** Where an inline message may appear, besides the Prompt message dialog. Not .help-block: those are the hints. */
    private static final String MESSAGES = ".invalid-feedback, .error-message, .text-danger, "
            + ".el-form-item__error, [role='alert'], " + ALERT_BOARD;

    /** What the Prompt message dialog said after the last action on this form, already closed. */
    private String lastPrompt = "";

    /**
     * The calls whose answer a save or an upload shows: the category's own POST/PUT, and the
     * storage upload an icon goes through. Manage Games' {@code /games} call is not the form's.
     */
    private static final Pattern FORM_API = Pattern.compile(
            "/api/account/(?:game-category-features(?:/\\d+)?|uploadImage)(?:\\?|$)");

    private static final Pattern JSON_MESSAGE =
            Pattern.compile("\"(?:message|msg)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** The JSON body the API answered the last save or upload with, escapes decoded; empty if none was called. */
    private String lastApiBody = "";

    /** The {@code message} field of {@link #lastApiBody}. */
    private String lastApiMessage = "";

    /** Whether the last save or upload reached the API at all, rather than being refused by the page. */
    private boolean apiCalled;

    public OtherCategoryFormPage(Page page) {
        super(page);
    }

    /**
     * Waits for the form by its name field, not its heading: switched to 中文 the admin UI heads it
     * "新增其他分類", which the English heading pattern cannot see.
     */
    public void awaitOpen() {
        nameInput().waitFor(new Locator.WaitForOptions().setTimeout(20_000));
        page.waitForTimeout(400);
    }

    /** On Create or Edit, in either admin UI language: the address, and the language tab strip. */
    public boolean isOpen() {
        return page.url().matches(".*/product/(add|update)-other-category.*")
                && page.locator("form ul.nav-tabs").count() > 0
                && page.locator("form ul.nav-tabs").first().isVisible();
    }

    private Locator heading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING)).first();
    }

    /** "Create Other Category" or "Edit Other Category". */
    public String headingText() {
        return heading().count() > 0 ? heading().innerText().trim() : "";
    }

    /** "Home Page / Product / Other Category / Create Other Category". */
    public String breadcrumb() {
        return OtherCategoryListPage.crumbs(page);
    }

    // ---------------------------------------------------------------
    // Language tabs and the name field
    // ---------------------------------------------------------------

    private Locator tab(String label) {
        return page.locator("ul.nav-tabs a")
                .filter(new Locator.FilterOptions().setHasText(exactly(label, false)))
                .filter(new Locator.FilterOptions().setVisible(true))
                .first();
    }

    /** Every language tab on screen, in the capture's order. A missing tab is simply left out. */
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

    /**
     * The active tab. Read from the field's own label - "Category name (繁體)" - which is what
     * the capture ties to the tab, and falls back to the tab's selected state.
     */
    public String activeLanguageTab() {
        String label = nameFieldLabel();
        for (String tab : LANGUAGE_TABS) {
            if (label.contains("(" + tab + ")")) {
                return tab;
            }
        }
        for (String tab : LANGUAGE_TABS) {
            Locator t = tab(tab);
            if (t.count() == 0) {
                continue;
            }
            String state = t.getAttribute("aria-selected") + " " + t.getAttribute("aria-pressed") + " " + t.getAttribute("class")
                    + " " + t.evaluate("a => a.parentElement?.className ?? ''");   // Bootstrap marks the li
            if (state.matches("(?s).*\\b(true|active|is-active|selected)\\b.*")) {
                return tab;
            }
        }
        return "";
    }

    public boolean isLanguageTabActive(String label) {
        return label.equals(activeLanguageTab());
    }

    /** The one Category name input, whatever its label currently names. */
    private Locator nameInput() {
        return page.locator(".tab-content .tab-pane.active input[type='text']")
                .filter(new Locator.FilterOptions().setVisible(true))
                .first();
    }

    /** How many name inputs are visible at once. The capture shows one; OC-CRT-003 checks it. */
    public int visibleNameInputs() {
        return page.locator(".tab-content input[type='text']")
                .filter(new Locator.FilterOptions().setVisible(true)).count();
    }

    /** "Category name (繁體)". */
    public String nameFieldLabel() {
        Locator label = page.locator("label").filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile("^\\s*Category name\\s*\\(", Pattern.CASE_INSENSITIVE))).first();
        return label.count() > 0 ? label.innerText().replaceAll("\\s+", " ").trim() : "";
    }

    /** "Shown on the client UI when 繁體 is selected." */
    public String nameFieldHint() {
        Locator hint = page.getByText(Pattern.compile("Shown on the client UI when .+ is selected")).first();
        return hint.count() > 0 ? hint.innerText().trim() : "";
    }

    public void setName(String languageTab, String name) {
        selectLanguageTab(languageTab);
        nameInput().fill(name);
    }

    public String nameFor(String languageTab) {
        selectLanguageTab(languageTab);
        return nameInput().inputValue();
    }

    /**
     * Names the category once, for every language: types {@code name} on the 繁體 tab and ticks
     * "Override above contents to: All languages", which copies the 繁體 name to the other eleven
     * when the form is saved (the form's {@code copyZhTwContentToSelectedLangs}). One fill instead of
     * twelve tab switches - for a category whose translations are not what the case is about.
     */
    public void setNameForAllLanguages(String name) {
        CaseLog.step("Naming the category \"" + name + "\" on 繁體 and ticking Override to: All languages.");
        setName(LANGUAGE_TABS.get(0), name);
        page.locator("label.copy-lang-all-item input[type='checkbox']").first().setChecked(true);
        page.waitForTimeout(200);
    }

    /** Fills every tab on screen, taking each value from {@code nameFor}. */
    public void setAllNames(Function<String, String> nameFor) {
        CaseLog.step("Filling the category name on every language tab.");
        for (String tab : languageTabLabels()) {
            setName(tab, nameFor.apply(tab));
        }
    }

    // ---------------------------------------------------------------
    // General settings: icon, status, order
    // ---------------------------------------------------------------

    /** The Category icon drop zone - the form has one. */
    private Locator iconBox() {
        return page.locator("form .image-dropzone").first();
    }

    /**
     * Chooses the icon file. The screen uploads it to storage at once, so this waits for the preview
     * to show the new file, or for the Prompt message that refuses it, before returning.
     */
    public void uploadIcon(Path file) {
        CaseLog.step("Uploading " + file.getFileName() + " as the category icon.");
        PromptMessage.clearStray(page);
        capturingApi(() -> attemptIconUpload(file));
        if (PromptMessage.isTransientUploadFailure(lastPrompt)) {
            // The storage upload itself failed (seen 2026-09-25 across a whole run, then fine minutes
            // later). That says nothing about the file, so it is tried once more; a refusal is not.
            CaseLog.evidence("The icon upload failed on the server (\"" + lastPrompt + "\"); retrying once.");
            capturingApi(() -> attemptIconUpload(file));
        }
        page.waitForTimeout(300);
    }

    /** One upload: waits up to 20s for the preview to change, or for the Prompt message that refuses it. */
    private void attemptIconUpload(Path file) {
        String before = iconPreviewSource();
        lastPrompt = "";
        iconBox().locator("input[type='file']").first().setInputFiles(file);
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            lastPrompt = PromptMessage.readAndClose(page, 0);
            String now = iconPreviewSource();
            if (!lastPrompt.isEmpty() || (!now.isBlank() && !now.equals(before))) {
                return;
            }
            page.waitForTimeout(300);
        }
    }

    /** The icon preview's {@code src}, or empty while the zone still reads "Drop file here". */
    public String iconPreviewSource() {
        Locator preview = iconBox().locator("img.image-dropzone-preview, img")
                .filter(new Locator.FilterOptions().setVisible(true)).first();
        return preview.count() > 0 ? String.valueOf(preview.getAttribute("src")) : "";
    }

    public boolean hasIconPreview() {
        return !iconPreviewSource().isBlank();
    }

    /** "Do you want to display" - the shipped screen's Status. Ticked is Active. */
    /** The form's one checkbox that is not an "Override above contents to" language - found without its label's wording. */
    private Locator displayCheckbox() {
        return page.locator("form .checkbox label:not(.copy-lang-item) input[type='checkbox']").first();
    }

    /** "Active" ticks "Do you want to display"; "Inactive" unticks it. */
    public void setStatus(String status) {
        CaseLog.step("Setting Status to " + status + " (\"Do you want to display\" "
                + ("Active".equalsIgnoreCase(status) ? "ticked" : "unticked") + ").");
        PromptMessage.clearStray(page);
        displayCheckbox().setChecked("Active".equalsIgnoreCase(status));
    }

    /** "Active" when "Do you want to display" is ticked, else "Inactive". */
    public String status() {
        return displayCheckbox().isChecked() ? "Active" : "Inactive";
    }

    private Locator orderInput() {
        return page.locator("form input[type='number']")
                .filter(new Locator.FilterOptions().setVisible(true)).first();
    }

    public void setDisplayOrder(String order) {
        CaseLog.step("Setting Display order to \"" + order + "\".");
        PromptMessage.clearStray(page);
        orderInput().fill("");
        orderInput().pressSequentially(order);
    }

    public String displayOrder() {
        return orderInput().inputValue();
    }

    // ---------------------------------------------------------------
    // Buttons
    // ---------------------------------------------------------------

    /** The form's submit: "Save &amp; manage games" on Create, "Save changes" on Edit. */
    private Locator primaryButton() {
        return page.locator("form button[type='submit']")
                .filter(new Locator.FilterOptions().setVisible(true)).first();
    }

    /** "Save &amp; manage games" on Create; "Save changes" on Edit, which returns to the list (AQ-08). */
    public String primaryButtonLabel() {
        return primaryButton().count() > 0 ? primaryButton().innerText().replaceAll("\\s+", " ").trim() : "";
    }

    /** Submits, then reads and closes any Prompt message, so a refusal is kept and does not block the page. */
    public void clickSave() {
        CaseLog.step("Clicking " + primaryButtonLabel() + ".");
        PromptMessage.clearStray(page);
        capturingApi(() -> {
            primaryButton().click();
            lastPrompt = PromptMessage.confirmAndRead(page, 2500);
        });
        if (isSuccess(lastPrompt)) {
            lastPrompt = "";   // the save went through; "successfully modified" is not a complaint
        }
        page.waitForTimeout(1500);
    }

    /**
     * Runs a save or an upload while listening for the form's own API call, and keeps what that
     * call answered - so a refusal is checked against the API's response rather than against a
     * copy of its wording typed into the tests, which went stale every time the back office
     * reworded a message (2026-09-28: "Verification wrong" became field-specific).
     *
     * <p>Edit's save raises "Confirm to modify?" first, and the PUT only goes out once that is
     * confirmed, so the listener spans the whole action rather than waiting on the click alone.
     */
    private void capturingApi(Runnable action) {
        List<Response> answered = new ArrayList<>();
        Consumer<Response> listener = response -> {
            if (!"GET".equals(response.request().method()) && FORM_API.matcher(response.url()).find()) {
                answered.add(response);
            }
        };
        page.onResponse(listener);
        try {
            action.run();
        } finally {
            page.offResponse(listener);
        }
        apiCalled = !answered.isEmpty();
        lastApiBody = "";
        lastApiMessage = "";
        for (Response response : answered) {
            String body;
            try {
                body = decodeJson(response.text());
            } catch (RuntimeException unreadable) {
                body = "";
            }
            var message = JSON_MESSAGE.matcher(body);
            lastApiBody = body;
            lastApiMessage = message.find() ? message.group(1) : "";
            CaseLog.data("API " + response.request().method() + " "
                    + response.url().replaceFirst("^https?://[^/]+/", "").replaceFirst("\\?.*$", "")
                    + " answered", body.isEmpty() ? "(no body)" : body);
        }
        if (!apiCalled) {
            CaseLog.data("API", "not called - the page answered this itself");
        }
    }

    /** JSON string escapes decoded, so a body compares with what the page shows: {@code 、} to 、. */
    private static String decodeJson(String json) {
        StringBuilder out = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c != '\\' || i + 1 >= json.length()) {
                out.append(c);
                continue;
            }
            char next = json.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'u' -> {
                    if (i + 4 < json.length()) {
                        out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                // \" stays escaped so JSON_MESSAGE can still find where a string ends.
                case '"' -> out.append("\\\"");
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    /** Whether the last save or upload reached the API, rather than being refused by the page first. */
    public boolean apiCalled() {
        return apiCalled;
    }

    /** The {@code message} the API answered the last save or upload with; empty when none was called. */
    public String apiMessage() {
        return lastApiMessage.replace("\\\"", "\"");
    }

    /** The API's whole answer to the last save or upload, JSON escapes decoded; empty when none was called. */
    public String apiResponse() {
        return lastApiBody.replace("\\\"", "\"");
    }

    /** The alert a successful save raises - "Successfully modified" - which is not a refusal. */
    private static boolean isSuccess(String message) {
        return message.toLowerCase().contains("success");
    }

    /** The capture's Cancel. The shipped form has none; <b>Return »</b> leaves it without saving. */
    public void cancel() {
        CaseLog.step("Clicking Return » (the form has no Cancel).");
        PromptMessage.clearStray(page);
        // The first button above the form - found by place, not its "Return »", which 中文 translates.
        page.locator("section.content > p > a.btn-primary").first().click();
        page.waitForTimeout(1000);
    }

    /**
     * Every validation or upload message, joined - empty when there is none: what the Prompt message
     * dialog said after the last save or upload, plus any inline message still on screen.
     */
    public String messages() {
        StringBuilder all = new StringBuilder(lastPrompt);
        String open = PromptMessage.readAndClose(page, 0);
        if (!open.isEmpty()) {
            lastPrompt = lastPrompt.isEmpty() ? open : lastPrompt + " | " + open;
            all.append(all.length() == 0 ? "" : " | ").append(open);
        }
        Locator found = page.locator(MESSAGES).filter(new Locator.FilterOptions().setVisible(true));
        for (int i = 0; i < found.count(); i++) {
            String text = found.nth(i).innerText().replaceAll("\\s+", " ").trim();
            if (!text.isEmpty()) {
                all.append(all.length() == 0 ? "" : " | ").append(text);
            }
        }
        return all.toString();
    }

    /** The browser's own constraint message for the order field, when it uses {@code type=number}. */
    public String orderFieldValidity() {
        return String.valueOf(orderInput().evaluate("i => i.validationMessage || ''"));
    }
}
