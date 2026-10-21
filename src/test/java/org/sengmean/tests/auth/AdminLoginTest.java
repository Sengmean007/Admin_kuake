package org.sengmean.tests.auth;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.sengmean.base.BaseTest;
import org.sengmean.page.auth.AdminLoginPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The back office sign-in form at {@code /login}.
 *
 * <h2>Valid sign-in only, deliberately</h2>
 * This class carries the one case that matters most and costs nothing: the configured operator
 * account signs in and reaches the Control Panel. The rejection cases the form also has - an
 * unknown account, a wrong password, an empty form, a wrong verification code - are <b>not</b>
 * here, and adding them is not simply a matter of writing more methods:
 *
 * <p>The site locks an account after <b>three</b> rejected passwords, answering the fourth
 * attempt with "Wrong password for more than 3 times, please contact Customer Service"
 * (observed 2026-09-16). A wrong-password case run against {@link BaseTest#USERNAME} therefore
 * locks the account every other suite in this project signs in with, and unlocking it is a
 * Customer Service action rather than anything a run can undo. Such a case needs
 * {@link BaseTest#ALT_USERNAME} - which is empty until someone provisions a second operator -
 * and the {@code creates-data} tag. Do not add one against the shared account.
 *
 * <h2>The id</h2>
 * {@code ADMLOGIN-901} is in the <b>900 block</b>, which is this project's marker for a case
 * with no row in the client's workbook. There is no admin workbook checked in yet - see the
 * README - so every case here is the project's own for now. When the admin sheet lands and it
 * has a valid-login row, this case takes that row's id and the 900 number is retired to a
 * {@code // Formerly:} comment rather than kept alongside it.
 *
 * <h2>Guest pages, not a shared session</h2>
 * Each case gets its own unauthenticated context, because the thing under test <em>is</em>
 * the transition from signed-out to signed-in - a suite-wide session established in a
 * {@code @BeforeAll} would have done the work before the first assertion ran. The signed-in
 * suites that come later should do the opposite and go through {@link BaseTest#signIn(Page)}
 * once.
 */
@Order(10)   // E2E step 1 - see RebateSuite for the whole cycle
public class AdminLoginTest extends BaseTest {

    private BrowserContext session;
    private Page sessionPage;
    private AdminLoginPage loginPage;

    @BeforeEach
    void openGuestPage() {
        session = newContext();
        sessionPage = session.newPage();
        loginPage = new AdminLoginPage(sessionPage);
    }

    @AfterEach
    void closeGuestPage() {
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        loginPage = null;
    }

    @Test
    @DisplayName("ADMLOGIN-001: a valid operator account signs in and reaches the Control Panel")
    void validCredentialsSignTheOperatorIn() {
        loginPage.signIn(USERNAME, PASSWORD, CAPTCHA);

        // Three separate claims, because each can be true while another is false: the session
        // exists, it belongs to the account the run configured, and the back office - not the
        // login page under another name - is what came back.
        assertTrue(loginPage.isSignedIn(),
                "Expected the header to name a signed-in operator after valid credentials, "
                        + "but the site answered: " + loginPage.promptText());
        assertEquals(USERNAME, loginPage.signedInAccount(),
                "Signed in, but as a different account than the one configured");
        assertEquals("Control Panel", loginPage.landingHeading(),
                "Expected the Control Panel to be the landing page after signing in");
    }
}
