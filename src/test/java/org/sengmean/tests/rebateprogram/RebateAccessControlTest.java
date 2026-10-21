package org.sengmean.tests.rebateprogram;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.sengmean.base.BasePage;
import org.sengmean.base.BaseTest;
import org.sengmean.page.auth.AdminLoginPage;
import org.sengmean.page.rebate.RealTimeRebatePage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>C2. Access Control</b> - {@code RBMP-115} to {@code RBMP-119}: who may reach Rebate
 * program management, and what happens to everyone else.
 *
 * <h2>Why these are their own class</h2>
 * Every other rebate case runs inside one long-lived operator session, opened once and reused.
 * These cannot: the thing under test <em>is</em> the session. Each role needs a context of its
 * own, signed in as a different account, and the back office <b>evicts a duplicate session</b>
 * (see {@link AdminLoginPage}) - so a case that borrowed the shared operator's account to check
 * a second role would sign the rest of the suite out mid-run. Each case here therefore opens
 * its own context and closes it again, and none of them touches the operator session.
 *
 * <h2>Three of the five wait on accounts that do not exist yet</h2>
 * A role cannot be simulated. "The Agent must not see Rebate management" is only answered by an
 * Agent session actually not seeing it; asserting against an account that has the menu tells
 * you nothing, and asserting against no session at all tells you about signing out. So
 * {@code RBMP-116}, {@code RBMP-117} and {@code RBMP-118} are <b>written in full</b> against
 * {@link BaseTest#ADMIN_USERNAME} and its neighbours, and abort naming the empty configuration
 * key until someone fills it in. They run as written on the day the accounts exist.
 *
 * <h2>What the deep-link row turned into, and why</h2>
 * {@code RBMP-119} asks for "the Create or Modify screen's URL". <b>There is no such URL.</b>
 * Measured on 2026-09-18: Create and Modify are views rendered in place by the Vue application,
 * and the address bar still reads {@code /rebate/set-up/} while the Create form is on screen.
 * So the only deep link that exists to defend is {@code /rebate/set-up} itself, and that is
 * what the case tests - as a signed-out visitor, which needs no extra account and proves the
 * redirect today, and again as a low-privilege role once one is configured.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(20)   // E2E step 2
public class RebateAccessControlTest extends BaseTest {

    /** Where the back office serves the rebate settings screen. */
    private static final String REBATE_SET_UP = "rebate/set-up";

    /** The operator session the Super Admin row runs in. The other rows open their own. */
    private static BrowserContext operatorSession;
    private static Page operatorPage;
    private static AdminShellPage operatorShell;
    private static RealTimeRebatePage realTime;

    /** Whatever context the case currently running opened for a second role, closed after it. */
    private BrowserContext roleSession;

    @BeforeAll
    static void signInAsOperator() {
        operatorSession = newContext();
        operatorPage = operatorSession.newPage();
        signIn(operatorPage);
        operatorShell = new AdminShellPage(operatorPage);
        operatorShell.useLanguage(LANGUAGE);
        operatorShell.useCurrency(CURRENCY);
        realTime = new RealTimeRebatePage(operatorPage);
    }

    @AfterAll
    static void closeOperatorSession() {
        if (operatorSession != null) {
            operatorSession.close();
            operatorSession = null;
        }
        operatorPage = null;
        operatorShell = null;
        realTime = null;
    }

    @AfterEach
    void closeRoleSession() {
        if (roleSession != null) {
            roleSession.close();
            roleSession = null;
        }
    }

    /**
     * Opens a second browser session signed in as another role, or aborts saying which key is
     * empty.
     *
     * <p>The abort names the configuration key rather than "an account is missing", because the
     * next person to read the report is the person who has to create it, and the key is the
     * thing they will search for.
     */
    private Page signedInAs(String role, String username, String password, String configKey) {
        if (username.isBlank() || password.isBlank()) {
            abort("No " + role + " account is configured, so this row cannot be answered. A role "
                    + "cannot be simulated: what it must NOT be able to do is only shown by a "
                    + "session that really holds it. Set " + configKey + ".username and "
                    + configKey + ".password in Config/ - see RebateTestData.SEEDING_REQUIRED - "
                    + "and this case runs as written.");
        }
        roleSession = newContext();
        Page rolePage = roleSession.newPage();
        signIn(rolePage, username, password);
        CaseLog.data("Role under test", role + " (" + username + ")");
        return rolePage;
    }

    /** Asserts that a session cannot reach Rebate set up, by either route the workbook names. */
    private void assertRebateAdminIsOutOfReach(Page rolePage, String role) {
        AdminShellPage shell = new AdminShellPage(rolePage);

        boolean menuOffered = shell.hasMenuLink(AdminShellPage.REBATE_SET_UP_HREF);
        CaseLog.check("Rebate set up in the menu for " + role + ": " + menuOffered);

        rolePage.navigate(BasePage.url(REBATE_SET_UP));
        BasePage.awaitReady(rolePage);
        String landedOn = rolePage.url();
        boolean formShown = rolePage.locator("#divSearchCriteriaRealTime").count() > 0;
        CaseLog.data("Direct navigation landed on", landedOn);

        assertFalse(menuOffered && formShown,
                "The " + role + " role should not be able to reach Rebate program admin setup, "
                        + "but the menu offered it and " + landedOn + " rendered the setup form");
        assertFalse(formShown,
                "Typing " + REBATE_SET_UP + " as " + role + " should be redirected or refused "
                        + "rather than rendering the settings panel, but the panel was there at "
                        + landedOn);
    }

    // =====================================================================
    // The role that is allowed everything
    // =====================================================================

    @Test
    @Order(10)
    @DisplayName("RBMP-115: Super Admin has full access to Rebate program management")
    void superAdminHasFullAccess() {
        // What "full access" means here is the list of controls the workbook's own rows go on to
        // use: the menu entry, both tabs, a Create on each, the search box, the status filter and
        // a Save on the settings panel. If any were missing, the section that needs it would fail
        // for a reason that had nothing to do with that section.
        assertTrue(operatorShell.hasMenuLink(AdminShellPage.REBATE_SET_UP_HREF),
                "Rebate set up should be in the side navigation for the operator account");

        realTime.navigateTo(BasePage.url(REBATE_SET_UP));
        assertTrue(realTime.isCreateAvailable(), "Create should be available on the Real-time tab");
        assertTrue(realTime.isSearchAvailable(), "The program search box should be available");
        assertTrue(realTime.isStatusFilterAvailable(), "The list's status filter should be available");
        assertFalse(realTime.getMinClaimValue().isBlank(),
                "The Real-time global settings should be readable, not blank");

        var daily = realTime.switchToDailyRebate();
        assertTrue(daily.isCreateAvailable(), "Create should be available on the Daily tab too");
        assertFalse(daily.getReleaseTimeValue().isBlank(),
                "The Daily global settings should be readable, not blank");

        // The row says "Super Admin". The back office does not name a role anywhere in the shell,
        // so what this case can honestly claim is that the account the suite runs as has every
        // action available - and it records which account that was, so the claim can be checked
        // against whoever administers the environment.
        CaseLog.data("Account under test", operatorShell.signedInAccount());
        CaseLog.check("Every Rebate management action is available to this account. The back "
                + "office does not display a role name, so the row is answered for the account "
                + "rather than for the word \"Super Admin\".");
    }

    // =====================================================================
    // The roles that are allowed less, or nothing
    // =====================================================================

    @Test
    @Order(20)
    @DisplayName("RBMP-116: Admin role's access level to Rebate program management")
    void adminRoleAccessLevel() {
        Page adminPage = signedInAs("Admin", ADMIN_USERNAME, ADMIN_PASSWORD, "test.admin");
        AdminShellPage shell = new AdminShellPage(adminPage);

        assertTrue(shell.hasMenuLink(AdminShellPage.REBATE_SET_UP_HREF),
                "The Admin role is expected to reach Rebate management at all (see AQ-10)");

        RealTimeRebatePage adminView = new RealTimeRebatePage(adminPage);
        adminView.navigateTo(BasePage.url(REBATE_SET_UP));

        // AQ-10 is open: the client has not said whether Admin is at parity with Super Admin or
        // holds a restricted subset. Until it is answered, the case records what this role can
        // actually do rather than asserting one answer and being wrong half the time.
        CaseLog.data("Create available to Admin", String.valueOf(adminView.isCreateAvailable()));
        CaseLog.data("Search available to Admin", String.valueOf(adminView.isSearchAvailable()));
        CaseLog.data("Status filter available to Admin",
                String.valueOf(adminView.isStatusFilterAvailable()));
        CaseLog.evidence("AQ-10 is unanswered: this row records the Admin role's actual access "
                + "level above. Turn it into an assertion once the client says whether Admin is "
                + "at parity with Super Admin or holds a restricted subset.");
    }

    @Test
    @Order(21)
    @DisplayName("RBMP-117: Agent role cannot access Rebate program admin setup")
    void agentRoleCannotAccessAdminSetup() {
        Page agentPage = signedInAs("Agent", AGENT_USERNAME, AGENT_PASSWORD, "test.agent");
        assertRebateAdminIsOutOfReach(agentPage, "Agent");
    }

    @Test
    @Order(22)
    @DisplayName("RBMP-118: Ordinary user cannot access Rebate program admin setup")
    void ordinaryUserCannotAccessAdminSetup() {
        // test.user was qatest, which is an Agent (Si, 2026-09-25), so this row used to re-run
        // RBMP-117. It is now a member account, which the back office refuses at sign-in - member
        // and back-office accounts are separate - and a member with no session is kept out.
        Page userPage;
        try {
            userPage = signedInAs("ordinary user", USER_USERNAME, USER_PASSWORD, "test.user");
        } catch (IllegalStateException refused) {
            CaseLog.data("Ordinary user (" + USER_USERNAME + ") at sign-in", refused.getMessage());
            assertTrue(refused.getMessage().contains("refused the sign-in"),
                    "The member should be refused a back-office session outright; instead: " + refused.getMessage());
            return;
        }
        assertRebateAdminIsOutOfReach(userPage, "ordinary user");
    }

    @Test
    @Order(30)
    @DisplayName("RBMP-119: Direct deep-link to Create/Modify URLs without the proper role")
    void deepLinkWithoutTheProperRole() {
        // The half that runs today, and needs no account at all: a visitor with no session.
        // "Unauthorized access is redirected or shown an access-denied state rather than the form
        // itself" is exactly what this measures.
        Page guest = page();
        guest.navigate(BasePage.url(REBATE_SET_UP));
        BasePage.awaitReady(guest);

        String landedOn = guest.url();
        CaseLog.data("Signed-out deep link landed on", landedOn);
        assertEquals(0, guest.locator("#divSearchCriteriaRealTime").count(),
                "A signed-out deep link to " + REBATE_SET_UP + " should not render the settings "
                        + "form, but it did at " + landedOn);
        assertTrue(landedOn.contains(AdminLoginPage.PATH),
                "A signed-out deep link should be redirected to " + AdminLoginPage.PATH
                        + ", but landed on " + landedOn);

        CaseLog.evidence("The row asks for the Create/Modify screen's own URL. There is not one: "
                + "measured 2026-09-18, Create and Modify are rendered in place and the address "
                + "stays /rebate/set-up/ throughout, so /rebate/set-up is the whole of what can "
                + "be deep-linked.");

        // And the half that needs a role, when one is configured. Deliberately last: the guest
        // assertions above are the part that must not be lost to an abort.
        if (!AGENT_USERNAME.isBlank() && !AGENT_PASSWORD.isBlank()) {
            Page agentPage = signedInAs("Agent", AGENT_USERNAME, AGENT_PASSWORD, "test.agent");
            assertRebateAdminIsOutOfReach(agentPage, "Agent (deep link)");
        } else {
            CaseLog.step("No low-privilege account is configured, so only the signed-out deep "
                    + "link was exercised. Set test.agent.* in Config/ to cover the role half.");
        }
    }
}
