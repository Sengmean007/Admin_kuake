package org.sengmean.page.client;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/**
 * The member site's own modal - the "alert board" it raises for confirmations, refusals and
 * "Confirm to start the game?". Started as a direct, unchanged port of NewWeibo_automation's
 * {@code page.common.AlertBoard}, for {@link DepositPage}'s confirm step (2026-10-06).
 *
 * <h2>Corrected against the live Quark site (2026-10-24)</h2>
 * The ported locators target {@code AlertBoard_bg}/{@code AlertBoard_show} CSS-module classes -
 * real on this site (seen, hidden, on other mounted dialogs: {@code AlertBoard_modal-content},
 * {@code AlertBoard_deposit-activate-close}), but <b>not</b> on the Deposit confirm dialog
 * {@link DepositPage} actually raises. That one dialog is a different, plain Tailwind component:
 * one instance mounted at a time (not several, hidden ones, the way the ported javadoc below
 * still describes for whichever dialog does use the CSS-module shape), wrapped in a
 * {@code div[class*='z-alertBoard']}, its message in a sibling {@code div}, Cancel/Confirm as
 * plain {@code <button>}s with no distinguishing class - matched here by role and exact text, same
 * as before.
 *
 * <h2>Only the displayed board counts</h2>
 * The site keeps more than one alert board mounted at all times, and only the one on screen
 * carries {@code AlertBoard_show}. A page-wide locator also matches a hidden board and reads its
 * stale text back as the answer to the click just made.
 *
 * <p>Class names are matched by <b>prefix</b> rather than in full, because the CSS module hash
 * changes on every build.
 */
public class AlertBoard {

    private static final String SHOWN_BOARD = "div[class*='z-alertBoard']";
    private static final Pattern ACKNOWLEDGE = Pattern.compile("^(OK|Ok|Close|×)$");
    private static final String CONTENT = "[class*='whitespace-pre-line']";

    /** How long pressing Confirm may take - a deposit confirm posts the order and may hand off to a gateway. */
    private static final double CONFIRM_TIMEOUT_MS = 30_000;

    private final Page page;
    private final Locator shownBoard;
    private final Locator content;
    private final Locator confirmButton;
    private final Locator cancelButton;
    private final Locator closeButton;

    public AlertBoard(Page page) {
        this.page = page;
        this.shownBoard = page.locator(SHOWN_BOARD);
        this.content = shownBoard.locator(CONTENT);
        this.confirmButton = button("Confirm");
        this.cancelButton = button("Cancel");
        this.closeButton = shownBoard.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(ACKNOWLEDGE));
    }

    public boolean isShowing() {
        return shownBoard.count() > 0;
    }

    public String text() {
        if (shownBoard.count() == 0 || content.count() == 0) {
            return "";
        }
        String text = content.first().textContent();
        return text == null ? "" : text.trim();
    }

    public String innerText() {
        return content.count() > 0 ? content.first().innerText().trim() : "";
    }

    public boolean says(String fragment) {
        return text().contains(fragment);
    }

    public boolean hasConfirm() {
        return confirmButton.count() > 0;
    }

    public boolean hasCancel() {
        return cancelButton.count() > 0;
    }

    public void confirm() {
        confirmButton.first().click(new Locator.ClickOptions().setTimeout(CONFIRM_TIMEOUT_MS));
    }

    public boolean cancel() {
        if (cancelButton.count() == 0) {
            return false;
        }
        cancelButton.first().click();
        page.waitForTimeout(500);
        return true;
    }

    public void dismiss() {
        if (cancelButton.count() > 0) {
            cancelButton.first().click();
        } else if (closeButton.count() > 0) {
            closeButton.first().click();
        } else {
            page.keyboard().press("Escape");
        }
        page.waitForTimeout(500);
    }

    private Locator button(String label) {
        return shownBoard.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(label).setExact(true));
    }
}
