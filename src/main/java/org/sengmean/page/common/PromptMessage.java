package org.sengmean.page.common;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.sengmean.report.CaseLog;

/**
 * The back office's jQuery UI <b>Prompt message</b> dialog, as the Product and Tool screens use it.
 *
 * <p>Measured 2026-09-25: every validation message on Other Category arrives here - "Please upload
 * a category icon." with a single <b>OK</b> - and so does the delete confirmation, with Confirm and
 * Cancel. It is modal, so a message left open swallows every click that follows. The page objects
 * therefore read it and close it straight after each action that can raise it, and keep the text
 * for the test to ask about.
 *
 * <p>The delete confirmation is left alone: it has no OK, and the list page objects drive it.
 *
 * <p>In {@code page.common} since 2026-09-26, when Tool &gt; Notification became the second area to
 * use it.
 */
public final class PromptMessage {

    private PromptMessage() {
    }

    /** The visible Prompt message dialogs with a single button (OK). By count, not wording: the admin UI can be in 中文. */
    private static Locator withOk(Page page) {
        return page.locator(".ui-dialog")
                .filter(new Locator.FilterOptions().setVisible(true))
                .filter(new Locator.FilterOptions().setHas(page.locator(".ui-dialog-buttonset button")))
                .filter(new Locator.FilterOptions().setHasNot(page.locator(".ui-dialog-buttonset button + button")));
    }

    /**
     * Closes a message left open by something earlier - so its overlay cannot swallow the next click -
     * and records what it said. Call before an action; empty when nothing was open.
     *
     * <p>Measured 2026-09-25: a failed upload's "Failed to modify" can arrive after its case has
     * moved on, and the {@code ui-widget-overlay} it raises then sits over the next case's form. Every
     * click there timed out "intercepted by ui-widget-overlay", in rows that had nothing to do with
     * the upload.
     */
    public static String clearStray(Page page) {
        String left = readAndClose(page, 0);
        if (!left.isEmpty()) {
            CaseLog.evidence("A message was still open from an earlier action and was closed: \"" + left + "\"");
        }
        return left;
    }

    /** True for the server's transient upload failure, which is worth one retry - not for a refusal. */
    public static boolean isTransientUploadFailure(String message) {
        return message != null && message.contains("Failed to modify");
    }

    /** The visible confirmation dialogs - two buttons, Confirm then Cancel - other than the delete confirmation. */
    private static Locator withConfirm(Page page) {
        return page.locator(".ui-dialog")
                .filter(new Locator.FilterOptions().setVisible(true))
                .filter(new Locator.FilterOptions().setHas(page.locator(".ui-dialog-buttonset button + button")))
                .filter(new Locator.FilterOptions().setHasNot(page.locator(".delete-category-summary")));
    }

    /**
     * For a save: presses Confirm on the "are you sure" the screen asks first, then returns what the
     * message after it said, OK pressed. Empty when nothing came within {@code timeoutMs}.
     *
     * <p>Measured 2026-09-25: Create's {@code save()} calls {@code $confirm("confirm_to_add")} before
     * it posts anything, and posts only on Confirm. A suite that pressed Save and waited saw no
     * request, no message and no error - every create looked refused while nothing had been sent.
     */
    public static String confirmAndRead(Page page, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (withConfirm(page).count() > 0) {
                Locator dialog = withConfirm(page).first();
                CaseLog.step("The page asked: \"" + dialog.locator(".ui-dialog-content").innerText().replaceAll("\\s+", " ").trim()
                        + "\" - pressing Confirm.");
                dialog.locator(".ui-dialog-buttonset button").first().click();   // Confirm, then Cancel
                return readAndClose(page, Math.max(timeoutMs, 4000));
            }
            if (withOk(page).count() > 0) {
                return readAndClose(page, 0);
            }
            page.waitForTimeout(150);
        }
        return "";
    }

    /**
     * For a save the case wants to abandon: waits for the "are you sure" confirmation and presses its
     * Cancel (the second button), then returns what the confirmation asked. Empty when none came within
     * {@code timeoutMs} - a message with only OK is read and closed as usual (NOT-CRT-030, 2026-09-30).
     */
    public static String declineAndRead(Page page, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (withConfirm(page).count() > 0) {
                Locator dialog = withConfirm(page).first();
                String asked = dialog.locator(".ui-dialog-content").innerText().replaceAll("\\s+", " ").trim();
                CaseLog.step("The page asked: \"" + asked + "\" - pressing Cancel.");
                dialog.locator(".ui-dialog-buttonset button").last().click();
                page.waitForTimeout(500);
                readAndClose(page, 0);
                return asked;
            }
            if (withOk(page).count() > 0) {
                readAndClose(page, 0);
                return "";
            }
            page.waitForTimeout(150);
        }
        return "";
    }

    /**
     * Waits up to {@code timeoutMs} for a message, then returns its text and presses OK.
     * Empty when none came.
     */
    public static String readAndClose(Page page, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (withOk(page).count() == 0 && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(150);
        }
        StringBuilder all = new StringBuilder();
        for (int closed = 0; closed < 5 && withOk(page).count() > 0; closed++) {
            Locator dialog = withOk(page).first();
            String text = dialog.locator(".ui-dialog-content").innerText().replaceAll("\\s+", " ").trim();
            if (!text.isEmpty()) {
                all.append(all.length() == 0 ? "" : " | ").append(text);
                CaseLog.step("The page said: \"" + text + "\" - pressing OK.");
            }
            dialog.locator(".ui-dialog-buttonset button").first().click();   // the only button
            page.waitForTimeout(300);
        }
        return all.toString();
    }
}
