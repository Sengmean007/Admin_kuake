package org.sengmean.page.tool;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

import java.util.regex.Pattern;

/**
 * The <b>Block phone number</b> modal - read from the Figma frame
 * {@code BO / SMS Sending Log / Block number (modal)}.
 *
 * <h2>What the mockup shows</h2>
 * Header "Block phone number", a ✕; a status line "+855 10****522 · Record #85143 · Limit
 * reached"; a warning: "This number will be blocked permanently. It can't request SMS, Voice or
 * 'Send SMS to us' codes until an admin unblocks it."; <b>Reason*</b> (a required dropdown, e.g.
 * "Suspected SMS abuse"), <b>Note</b> (optional, with a max length); an audit banner: "Recorded in
 * Account Operation Log as 'Blocked by {admin}' with the time and reason."; <b>Cancel</b> /
 * <b>Confirm block</b> (red).
 */
public class SmsBlockModalPage extends BasePage {

    private final Locator title;
    private final Locator statusLine;
    private final Locator reasonSelect;
    private final Locator noteInput;
    private final Locator cancelButton;
    private final Locator confirmButton;
    private final Locator closeIcon;

    public SmsBlockModalPage(Page page) {
        super(page);
        this.title = page.getByText("Block phone number", new Page.GetByTextOptions().setExact(false)).first();
        this.statusLine = page.getByText(Pattern.compile("Record #\\d+"));
        this.reasonSelect = page.locator("select").first();
        this.noteInput = page.locator("textarea, input[placeholder*='note' i]").first();
        this.cancelButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel").setExact(true));
        this.confirmButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Confirm block"));
        this.closeIcon = page.locator("[aria-label='Close'], button:has-text('✕'), button:has-text('×')").first();
    }

    public void awaitOpen() {
        title.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        page.waitForTimeout(400);
    }

    public boolean isShowing() {
        return title.count() > 0;
    }

    public String statusLineText() {
        return statusLine.count() == 0 ? "" : statusLine.first().innerText().trim();
    }

    public java.util.List<String> reasonOptions() {
        if (reasonSelect.count() == 0) {
            return java.util.List.of();
        }
        Object raw = reasonSelect.evaluate("s => [...s.options].map(o => o.textContent.trim())");
        return raw instanceof java.util.List<?> list ? list.stream().map(String::valueOf).toList() : java.util.List.of();
    }

    public void selectReason(String reason) {
        reasonSelect.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(reason));
    }

    public void setNote(String note) {
        noteInput.fill(note);
    }

    public String noteValue() {
        return noteInput.count() == 0 ? "" : noteInput.inputValue();
    }

    public void cancel() {
        CaseLog.step("Clicking Cancel on the Block modal.");
        cancelButton.click();
    }

    public void closeViaIcon() {
        closeIcon.click();
    }

    /** Clicks Confirm block and returns what the page said, OK pressed - "" when nothing appeared. */
    public String confirmBlock() {
        CaseLog.step("Clicking Confirm block.");
        confirmButton.click();
        return PromptMessage.readAndClose(page, 4000);
    }

    public boolean isConfirmDisabled() {
        return "true".equals(confirmButton.getAttribute("disabled")) || confirmButton.isDisabled();
    }

    public String fieldError(String caption) {
        Locator label = page.getByText(caption, new Page.GetByTextOptions().setExact(false)).first();
        if (label.count() == 0) {
            return "";
        }
        Locator error = label.locator("xpath=following::*[contains(@class,'error') or contains(@class,'invalid')][1]");
        return error.count() == 0 ? "" : error.first().innerText().trim();
    }
}
