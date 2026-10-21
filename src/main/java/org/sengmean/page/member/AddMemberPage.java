package org.sengmean.page.member;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.common.PasswordStrengthField;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

/**
 * <b>Member Management &gt; Member List &gt; Add member</b> ({@code /member/add-member}) - the
 * screen the sheet's {@code BO-ADD-FRM}, {@code BO-ADD-PWD}, {@code BO-ADD-CHK}, {@code BO-ADD-BTN}
 * and {@code BO-ADD-AFT} sections cover.
 *
 * <h2>Measured live on admin-test, 2026-10-21</h2>
 * A plain page, not a modal, reached either by the Add member button on Member List or directly at
 * this path. Six fields in this order: Username, Full name, Password, Confirm password, Telephone
 * Number (a "Country code" control defaulting to <b>HK +852</b> beside a Phone number box) and
 * Email, then one submit button labelled <b>Add member</b>. The password fields are native
 * {@code <input type="password">} - masked by default, with no show/hide icon, no helper text, no
 * rule checklist and no strength label, and the submit button carries no {@code disabled} attribute
 * in any state tried. See {@link PasswordStrengthField}'s class Javadoc for what that means for the
 * rows that assert the enhanced behaviour the sheet describes (GD-190 through GD-197).
 */
public class AddMemberPage extends BasePage {

    public static final String PATH = "member/add-member";

    private final Locator username;
    private final Locator fullName;
    private final Locator password;
    private final Locator confirmPassword;
    private final Locator countryCode;
    private final Locator phoneNumber;
    private final Locator email;
    private final Locator submit;

    public final PasswordStrengthField strength;

    public AddMemberPage(Page page) {
        super(page);
        this.username = page.locator("input[placeholder='Username']");
        this.fullName = page.locator("input[placeholder='Full name']");
        this.password = page.locator("input[placeholder='Password']");
        this.confirmPassword = page.locator("input[placeholder='Confirm password']");
        this.countryCode = page.locator("input[placeholder='Country code'], *:has(> input[placeholder='Country code'])").first();
        this.phoneNumber = page.locator("input[placeholder='Phone number']");
        this.email = page.locator("input[placeholder='Email']");
        this.submit = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add member").setExact(true));
        this.strength = new PasswordStrengthField(page, password, confirmPassword);
    }

    public void open() {
        CaseLog.step("Opening Member Management > Member List > Add member directly at /" + PATH + ".");
        open("/" + PATH);
        awaitReady();
        username.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
    }

    public boolean isShowing() {
        return username.count() > 0 && fullName.count() > 0 && password.count() > 0
                && confirmPassword.count() > 0 && phoneNumber.count() > 0 && email.count() > 0
                && submit.count() > 0;
    }

    public Locator usernameField() {
        return username;
    }

    public void fillUsername(String value) {
        username.fill(value);
    }

    public void fillFullName(String value) {
        fullName.fill(value);
    }

    public void fillPhoneNumber(String value) {
        phoneNumber.fill(value);
    }

    public void fillEmail(String value) {
        email.fill(value);
    }

    /** The country code the Telephone Number field currently shows, e.g. "+852". */
    public String countryCode() {
        return countryCode.count() == 0 ? "" : countryCode.first().innerText().trim();
    }

    /**
     * Opens the country code control and picks the option whose text contains {@code optionText}.
     *
     * <p>Measured live 2026-10-21: clicking the control opens a scrollable list of unlabelled
     * buttons, each reading like "+66  Thailand (ไทย)" - name, native name and dial code together,
     * Hong Kong pinned first as the current selection. Matching on the country name ("Thailand")
     * is reliable; the dial code alone ("+66") also appears but is not the whole of any option's
     * text, so pass the name.
     */
    public void selectCountryCode(String optionText) {
        CaseLog.step("Selecting country code containing \"" + optionText + "\".");
        countryCode.first().click();
        Locator option = page.getByText(optionText, new Page.GetByTextOptions().setExact(false))
                .filter(new Locator.FilterOptions().setVisible(true));
        if (option.count() > 0) {
            option.first().click();
        } else {
            CaseLog.evidence("No visible option containing \"" + optionText + "\" was found after opening "
                    + "the country code control; the dropdown's real markup needs measuring.");
        }
    }

    public boolean isSubmitDisabled() {
        return PasswordStrengthField.isDisabled(submit);
    }

    public Locator submitButton() {
        return submit;
    }

    /** Clicks Add member and returns what the page said, OK pressed - "" when nothing appeared. */
    public String submitAndRead() {
        CaseLog.step("Clicking Add member.");
        submit.click();
        return PromptMessage.readAndClose(page, 4000);
    }
}
