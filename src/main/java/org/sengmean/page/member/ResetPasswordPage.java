package org.sengmean.page.member;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PasswordStrengthField;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

/**
 * A member profile's <b>Reset Password</b> tab ({@code /member/member-details/{id}}, the "Reset
 * Password" tab) - the sheet's {@code BO-RST} section.
 *
 * <h2>Measured live on admin-test, 2026-10-21</h2>
 * Opened from a member's profile (Account Details is the tab it lands on first) by clicking the
 * "Reset Password" tab; the address then carries the tab's Chinese label URL-encoded
 * ({@code .../member-details/11518/%E9%87%8D%E7%BD%AE%E5%AF%86%E7%A2%BC}), which this page object
 * does not depend on - it navigates by clicking the tab rather than building that address. The panel
 * is titled "Reset password" with two fields, <b>New password</b> and <b>Confirm password</b>, both
 * native masked inputs with none of the sheet's enhanced UX yet (see {@link PasswordStrengthField}),
 * and one button, <b>Reset all</b>.
 */
public class ResetPasswordPage extends BasePage {

    private final Locator newPassword;
    private final Locator confirmPassword;
    private final Locator resetAll;

    public final PasswordStrengthField strength;

    public ResetPasswordPage(Page page) {
        super(page);
        this.newPassword = page.locator("input[placeholder='New password']");
        this.confirmPassword = page.locator("input[placeholder='Confirm password']");
        this.resetAll = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset all").setExact(true));
        this.strength = new PasswordStrengthField(page, newPassword, confirmPassword);
    }

    /** Opens member {@code id}'s profile and switches to its Reset Password tab. */
    public void openFor(String memberId) {
        CaseLog.step("Opening the Reset Password tab for member " + memberId + ".");
        open("/member/member-details/" + memberId);
        awaitReady();
        Locator tab = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Reset Password").setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true));
        if (tab.count() == 0) {
            tab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Reset Password").setExact(true));
        }
        tab.first().click();
        awaitReady();
        newPassword.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
    }

    public boolean isShowing() {
        return newPassword.count() > 0 && confirmPassword.count() > 0 && resetAll.count() > 0;
    }

    public boolean isSaveDisabled() {
        return PasswordStrengthField.isDisabled(resetAll);
    }

    public Locator saveButton() {
        return resetAll;
    }

    /** Clicks Reset all and returns what the page said, OK pressed - "" when nothing appeared. */
    public String saveAndRead() {
        CaseLog.step("Clicking Reset all.");
        resetAll.click();
        return PromptMessage.readAndClose(page, 4000);
    }
}
