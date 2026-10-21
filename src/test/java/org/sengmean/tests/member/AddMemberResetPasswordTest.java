package org.sengmean.tests.member;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.sengmean.base.BaseTest;
import org.sengmean.page.common.PasswordStrengthField;
import org.sengmean.page.common.PasswordStrengthField.Rule;
import org.sengmean.page.member.AddMemberPage;
import org.sengmean.page.member.MemberListPage;
import org.sengmean.page.member.ResetPasswordPage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>Member Management &gt; Member List &gt; Add member</b> and a member profile's <b>Reset
 * Password</b> tab - the 92 {@code BO-ADD-FRM}, {@code BO-ADD-PWD}, {@code BO-ADD-CHK},
 * {@code BO-ADD-BTN}, {@code BO-ADD-AFT}, {@code BO-RST} and {@code BO-API} rows of the client's
 * "Add new Member &amp; Reset password" tab (workbook {@code [TC_Admin] V.3.20.0 Weibo.xlsx}, the
 * tab Si marked green on 2026-10-21 for this pass - {@code FF00FF00}, the same convention
 * {@code CaseSheetLayout.markedGreen} reads for NewWeibo_automation).
 *
 * <pre>./mvnw test -Dtest=AddMemberResetPasswordTest</pre>
 *
 * <h2>Scope of this pass - one test class, not yet a suite</h2>
 * Si asked for one test class first rather than the full Create/Edit/Delete/Suite scaffold
 * Notification and Other Category eventually grew. No {@code @Suite} class selects this one yet;
 * run it by class or by {@code -Dcase=}. The client workbook is not touched either - the ids below
 * are taken verbatim from the sheet (the project's standing rule for a new feature's sheet), but
 * {@code client_testcase.xlsx} gaining its own green tab for this feature, with TEST DATA and
 * Automation columns filled in, is follow-up work, not part of this pass.
 *
 * <h2>Seven rows were left out of the 98 on the sheet's own count</h2>
 * The tab's row-count formula reads 98, but seven of those - a trailing "CLIENT - MULTI-CURRENCY"
 * section, ids {@code RANK-CLI-CUR-001}..{@code -007} - are about a client-side ranking currency
 * selector with no relation to adding a member or resetting a password, and use a different id
 * prefix than every other row on the tab. They read as a copy-paste leftover from the sibling
 * "User Rankings Setup" sheet rather than genuinely belonging here, so they are not in this class;
 * flag it to Si rather than assume.
 *
 * <h2>Written against the Figma design, which is ahead of the live screen</h2>
 * Si gave the mockup (Platform project, "version 3.20.0" page, frames {@code BO / Member / Add
 * Member} and {@code BO / Member / Reset Password}, states 1-5). See
 * {@link PasswordStrengthField}'s class Javadoc for the exact copy it specifies. Measured live on
 * {@code admin-test.1083.city} on 2026-10-21, and confirmed by Si the same day: both screens exist
 * and the plain parts of each form already work - the fields, native password masking, the country
 * code default, member creation itself - but <b>none of the enhanced password UX exists yet</b>: no
 * show/hide icon, no helper text, no live rule checklist, no strength label, and neither submit
 * button ever carries a {@code disabled} attribute. The sheet cites an open GD ticket against every
 * row that needs a piece of that UX (GD-190 through GD-199), so those rows are tagged
 * {@code known-defect} and asserted against what the ticket (and the Figma frame) specifies - they
 * stay red until it ships, the same shape as {@code NOT-CRT-028}/GD-137.
 *
 * <h2>What could not be written for real</h2>
 * <ul>
 *   <li><b>{@code BO-ADD-AFT-006}, {@code BO-RST-012}</b> - logging in as the member on the
 *       front-end needs member-site page objects, which this project does not carry (per
 *       {@code AGENTS.md}: "no page objects, no test classes... no client workbook" for the member
 *       site - that is {@code NewWeibo_automation}'s). Both abort naming that.</li>
 *   <li><b>{@code BO-ADD-FRM-014}..{@code -016}</b> - the sheet marks these "(see OQ-01/02/03)":
 *       Si has not ruled on what the required-field validation should do, and the live form shows
 *       no validation feedback at all today. Submitting a deliberately invalid form to find out
 *       risks leaving a malformed row in a shared environment for a question nobody has answered
 *       yet, so these abort naming the open question rather than guess at one answer.</li>
 *   <li><b>{@code BO-API-001}..{@code -014}</b> - the sheet's own design note says plainly "validate
 *       again on the server - the disabled button is not a security control", so these rows matter,
 *       but nothing in this pass captured the real endpoint, payload shape or an auth approach for
 *       either API, and this project has no HTTP-level harness yet to build one confidently against.
 *       Each aborts naming exactly that: capture the real {@code Add Member} / {@code Reset
 *       Password} request from the network tab and implement these for real in a follow-up.</li>
 * </ul>
 *
 * <h2>Fixture</h2>
 * {@code BO-ADD-AFT-001} creates one throwaway member ({@link #ensureFixtureMember()}, prefixed
 * {@value #PREFIX}) and every row after it that needs an existing member - the rest of
 * {@code BO-ADD-AFT} and all of {@code BO-RST} - reuses it rather than creating its own. The sheet's
 * own POST CONDITION column says as much against {@code BO-ADD-AFT-001}: "remove or reuse for later
 * cases." There is no delete control on Member Management to sweep it afterward, so it is left
 * behind on purpose, the same tradeoff {@code OtherCategorySeedData} makes for its own fixtures.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("Add new Member & Reset password")
public class AddMemberResetPasswordTest extends BaseTest {

    /** Every member this class creates carries this prefix, so one is identifiable among the rest. */
    private static final String PREFIX = "qaboadd";

    /** A password that is Good by every rule the sheet and the Figma checklist describe. */
    private static final String GOOD_PASSWORD = "Abcdefg1";

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static MemberListPage list;

    /** The one throwaway member {@link #ensureFixtureMember()} creates, reused by every row after BO-ADD-AFT-001. */
    private static String fixtureUsername;
    private static String fixtureMemberId;

    @BeforeAll
    static void signInAndOpenShell() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        shell.useCurrency(CURRENCY);
        list = new MemberListPage(sessionPage);
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        shell = null;
        list = null;
    }

    // =====================================================================
    // Shared fixture and helpers
    // =====================================================================

    /**
     * Creates the one member this whole class reuses, the first time anything asks for it -
     * so every row is independently runnable (by {@code -Dcase=}, in any order) rather than
     * depending on BO-ADD-AFT-001 having already run in this JVM.
     */
    private static synchronized String ensureFixtureMember() {
        if (fixtureUsername != null) {
            return fixtureUsername;
        }
        String username = PREFIX + System.currentTimeMillis() % 100_000_000L;
        AddMemberPage form = new AddMemberPage(sessionPage);
        form.open();
        form.fillUsername(username);
        form.fillFullName("QA Fixture");
        form.strength.typePassword(GOOD_PASSWORD);
        form.strength.typeConfirm(GOOD_PASSWORD);
        form.fillEmail(username + "@example.com");
        String said = form.submitAndRead();
        CaseLog.data("Add member fixture \"" + username + "\" said", said.isEmpty() ? "(nothing)" : said);

        list.open();
        list.searchByUsername(username);
        assertTrue(list.hasRow(username),
                "The fixture member \"" + username + "\" should be listed after Add member, but was not found - "
                        + "the page said \"" + said + "\"");
        fixtureUsername = username;
        fixtureMemberId = list.memberId(username);
        CaseLog.data("Fixture member ID", fixtureMemberId);
        return fixtureUsername;
    }

    private static AddMemberPage openAddMember() {
        AddMemberPage form = new AddMemberPage(sessionPage);
        form.open();
        return form;
    }

    /** Fills the password fields and reports the checklist's read of one rule, for a short assertion line. */
    private static void assertRule(AddMemberPage form, Rule rule, boolean expected, String password) {
        form.strength.typePassword(password);
        Boolean state = form.strength.ruleState(rule);
        assertEquals(expected, Boolean.TRUE.equals(state),
                "For password \"" + password + "\", the live checklist's \"" + rule.text + "\" item should read "
                        + expected + ", but it " + (state == null ? "does not exist yet (GD-192)" : "reads " + state));
    }

    // =====================================================================
    // 1. Add Member - form fields & show/hide
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("1. Add Member - form fields & show/hide")
    class FormFields {

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-001: Add Member form opens with all fields in default state")
        void defaultFormState() {
            list.open();
            AddMemberPage form = list.clickAddMember();
            assertTrue(form.isShowing(),
                    "Add member should show Username, Full name, Password, Confirm password, "
                            + "Telephone Number and Email fields");
            assertTrue(form.isSubmitDisabled(), "Add member should be disabled on a fresh, empty form (GD-190)");
        }

        @Test
        @DisplayName("BO-ADD-FRM-002: Password and Confirm password are masked by default")
        void maskedByDefault() {
            AddMemberPage form = openAddMember();
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            assertTrue(form.strength.passwordMasked(), "Password should be masked by default");
            assertTrue(form.strength.confirmMasked(), "Confirm password should be masked by default");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-003: Helper text shows the rule list when the field is empty")
        void helperTextOnEmptyField() {
            AddMemberPage form = openAddMember();
            assertTrue(form.strength.collapsedHelperShown(),
                    "An untouched Password field should show the helper text \"" + PasswordStrengthField.HELPER_TEXT + "\" (GD-192)");
            assertFalse(form.strength.checklistShown(), "The live checklist should stay hidden until typing starts");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-004: Show icon on Password reveals the typed value")
        void showIconOnPassword() {
            AddMemberPage form = openAddMember();
            form.strength.typePassword(GOOD_PASSWORD);
            boolean clicked = form.strength.clickShowOnPassword();
            assertTrue(clicked, "Password should have a show icon to click (GD-191)");
            assertFalse(form.strength.passwordMasked(), "Password should show plain text after clicking show");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-005: Hide icon on Password masks the value again")
        void hideIconOnPassword() {
            AddMemberPage form = openAddMember();
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.clickShowOnPassword();
            boolean clickedAgain = form.strength.clickShowOnPassword();
            assertTrue(clickedAgain, "Password should have a hide icon to click back (GD-191)");
            assertTrue(form.strength.passwordMasked(), "Password should be masked again after clicking hide");
            assertEquals(GOOD_PASSWORD, form.strength.passwordValue(), "The typed value should be unchanged by toggling");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-006: Show icon on Confirm password reveals its own value")
        void showIconOnConfirm() {
            AddMemberPage form = openAddMember();
            form.strength.typeConfirm(GOOD_PASSWORD);
            boolean clicked = form.strength.clickShowOnConfirm();
            assertTrue(clicked, "Confirm password should have a show icon to click (GD-191)");
            assertFalse(form.strength.confirmMasked(), "Confirm password should show plain text after clicking show");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-007: Password show icon does not affect Confirm password")
        void passwordShowDoesNotAffectConfirm() {
            AddMemberPage form = openAddMember();
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            form.strength.clickShowOnPassword();
            assertTrue(form.strength.confirmMasked(), "Confirm password should stay masked when only Password's icon is clicked (GD-191)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-008: Confirm password show icon does not affect Password")
        void confirmShowDoesNotAffectPassword() {
            AddMemberPage form = openAddMember();
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            form.strength.clickShowOnConfirm();
            assertTrue(form.strength.passwordMasked(), "Password should stay masked when only Confirm's icon is clicked (GD-191)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-009: Toggling visibility keeps the typed value and rule state")
        void togglingKeepsValueAndRules() {
            AddMemberPage form = openAddMember();
            form.strength.typePassword(GOOD_PASSWORD);
            boolean allGreenBefore = form.strength.allRulesGreen();
            for (int i = 0; i < 3; i++) {
                form.strength.clickShowOnPassword();
            }
            assertEquals(GOOD_PASSWORD, form.strength.passwordValue(), "The value should survive several toggles (GD-191)");
            assertEquals(allGreenBefore, form.strength.allRulesGreen(), "The checklist state should survive several toggles");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-FRM-010: Typing while visible continues to update the checklist")
        void typingWhileVisibleUpdatesChecklist() {
            AddMemberPage form = openAddMember();
            form.strength.typePassword("Abc");
            form.strength.clickShowOnPassword();
            form.strength.typePasswordKeyByKey(GOOD_PASSWORD);
            assertFalse(form.strength.passwordMasked(), "Password should still be visible while typing (GD-191)");
            assertTrue(form.strength.allRulesGreen(), "The checklist should keep updating while the field is visible (GD-191, GD-192)");
        }

        @Test
        @DisplayName("BO-ADD-FRM-011: Country code dropdown defaults to HK +852")
        void countryCodeDefaultsToHongKong() {
            AddMemberPage form = openAddMember();
            assertTrue(form.countryCode().contains("+852"), "Telephone Number should default to HK +852, but read \"" + form.countryCode() + "\"");
        }

        @Test
        @DisplayName("BO-ADD-FRM-012: Admin can change the country code")
        void adminCanChangeCountryCode() {
            AddMemberPage form = openAddMember();
            form.selectCountryCode("Thailand");
            assertTrue(form.countryCode().contains("+66"), "Country code should read +66 after selecting Thailand, but read \"" + form.countryCode() + "\"");
        }

        @Test
        @DisplayName("BO-ADD-FRM-013: No OTP / verification code is requested in Back Office")
        void noOtpRequested() {
            AddMemberPage form = openAddMember();
            form.fillPhoneNumber("91234567");
            assertEquals(0, sessionPage.getByText(java.util.regex.Pattern.compile("OTP|verification code", java.util.regex.Pattern.CASE_INSENSITIVE)).count(),
                    "No Send OTP / verification code control should be shown on the back-office Add member form (GD-197)");
        }

        @Test
        @DisplayName("BO-ADD-FRM-014: Username required rule (OQ-01)")
        void usernameRequiredRule() {
            abort("Si has not ruled on OQ-01 (what the Username-required validation should do), and the live "
                    + "form shows no validation feedback at all. Submitting a blank Username to see what happens "
                    + "risks leaving a malformed row in a shared environment for a question nobody has answered "
                    + "yet. Rule on OQ-01 and this row can assert the answer.");
        }

        @Test
        @DisplayName("BO-ADD-FRM-015: Full name required rule (OQ-02)")
        void fullNameRequiredRule() {
            abort("Si has not ruled on OQ-02 (what the Full name-required validation should do). Same reasoning "
                    + "as BO-ADD-FRM-014: this aborts rather than guess an answer by submitting.");
        }

        @Test
        @DisplayName("BO-ADD-FRM-016: Email required / format rule (OQ-03)")
        void emailFormatRule() {
            abort("Si has not ruled on OQ-03 (what the Email format validation should do). Same reasoning as "
                    + "BO-ADD-FRM-014: this aborts rather than guess an answer by submitting.");
        }
    }

    // =====================================================================
    // 2. Add Member - password rules
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Add Member - password rules")
    class PasswordRules {

        private AddMemberPage form;

        @BeforeEach
        void openForm() {
            form = openAddMember();
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-001: 7 characters fails the length rule")
        void sevenCharsFailsLength() {
            assertRule(form, Rule.LENGTH, false, "Abcdef1");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-002: 8 characters passes the length rule")
        void eightCharsPassesLength() {
            assertRule(form, Rule.LENGTH, true, "Abcdef12");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-003: 20 characters passes the length rule")
        void twentyCharsPassesLength() {
            assertRule(form, Rule.LENGTH, true, "Abcdefghijklmnopqr12");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-004: 21 characters fails the length rule (OQ-06)")
        void twentyOneCharsFailsLength() {
            assertRule(form, Rule.LENGTH, false, "Abcdefghijklmnopqrs12");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-005: Password without uppercase fails")
        void noUppercaseFails() {
            assertRule(form, Rule.UPPERCASE, false, "abcdefg1");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-006: One uppercase letter satisfies the rule")
        void oneUppercasePasses() {
            assertRule(form, Rule.UPPERCASE, true, GOOD_PASSWORD);
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-007: Password without lowercase fails")
        void noLowercaseFails() {
            assertRule(form, Rule.LOWERCASE, false, "ABCDEFG1");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-008: One lowercase letter satisfies the rule")
        void oneLowercasePasses() {
            assertRule(form, Rule.LOWERCASE, true, "ABCDEFg1");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-009: Password without a number fails")
        void noNumberFails() {
            assertRule(form, Rule.NUMBER, false, "Abcdefgh");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-010: One digit satisfies the number rule")
        void oneDigitPasses() {
            assertRule(form, Rule.NUMBER, true, GOOD_PASSWORD);
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-011: Leading space fails the no-spaces rule")
        void leadingSpaceFails() {
            assertRule(form, Rule.NO_SPACES, false, " Abcdefg1");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-012: Middle space fails the no-spaces rule")
        void middleSpaceFails() {
            assertRule(form, Rule.NO_SPACES, false, "Abcd efg1");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-013: Trailing space fails the no-spaces rule")
        void trailingSpaceFails() {
            assertRule(form, Rule.NO_SPACES, false, "Abcdefg1 ");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-014: Password with no spaces passes")
        void noSpacesPasses() {
            assertRule(form, Rule.NO_SPACES, true, GOOD_PASSWORD);
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-015: Password meeting all 5 rules shows every rule green and strength Good")
        void allRulesGreenIsGood() {
            form.strength.typePassword(GOOD_PASSWORD);
            assertTrue(form.strength.allRulesGreen(), "All 5 rules should read green for \"" + GOOD_PASSWORD + "\" (GD-192)");
            assertEquals(PasswordStrengthField.STRENGTH_GOOD, form.strength.strengthLabel(),
                    "The strength label should read Good (GD-193)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-PWD-016: Special characters are allowed when other rules are met (OQ-07)")
        void specialCharactersAllowed() {
            form.strength.typePassword("Abc@efg1");
            assertTrue(form.strength.allRulesGreen(),
                    "\"Abc@efg1\" meets all 5 stated rules, and the sheet's own open question #2 asks whether "
                            + "special characters should raise strength further - this only asserts they do not fail it (GD-192)");
        }

        @Test
        @DisplayName("BO-ADD-PWD-017: Rules match front-end registration")
        void rulesMatchFrontEndRegistration() {
            abort("This project carries no member-site (front-end registration) page objects - "
                    + "per AGENTS.md, that belongs to the sibling NewWeibo_automation project. Parity between "
                    + "the two needs a case written there against this same rule set, not here.");
        }
    }

    // =====================================================================
    // 3. Add Member - live checklist & strength label
    // =====================================================================

    @Nested
    @Order(30)
    @DisplayName("3. Add Member - live checklist & strength label")
    class LiveChecklist {

        private AddMemberPage form;

        @BeforeEach
        void openForm() {
            form = openAddMember();
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-001: Checklist appears when the admin starts typing")
        void checklistAppearsOnFirstCharacter() {
            form.strength.typePasswordKeyByKey("a");
            assertTrue(form.strength.checklistShown(), "The checklist should appear as soon as typing starts (GD-192)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-002: Checklist updates on every keystroke")
        void checklistUpdatesLive() {
            form.strength.typePasswordKeyByKey(GOOD_PASSWORD);
            assertTrue(form.strength.allRulesGreen(), "Typing the password key by key should leave every rule green by the end (GD-192)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-003: Checklist updates when characters are deleted")
        void checklistUpdatesOnDelete() {
            form.strength.typePasswordKeyByKey(GOOD_PASSWORD);
            assertEquals(Boolean.TRUE, form.strength.ruleState(Rule.NUMBER), "The number rule should read green before deleting the digit");
            form.strength.backspacePassword();
            assertEquals(Boolean.FALSE, form.strength.ruleState(Rule.NUMBER),
                    "Deleting the only digit should flip the number rule back to red immediately (GD-192)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-004: Clearing the field returns to the default state (OQ-08)")
        void clearingReturnsToDefault() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.clear();
            assertTrue(form.strength.collapsedHelperShown(), "Clearing the field should bring back the collapsed helper text (GD-192)");
            assertFalse(form.strength.checklistShown(), "The checklist should hide again once the field is empty");
            assertEquals("", form.strength.strengthLabel(), "The strength label should hide once the field is empty");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-005: Pasting a password updates the checklist")
        void pastingUpdatesChecklist() {
            // No OS clipboard is set up in this project's Playwright context; a programmatic value
            // change (fill) is used as the closest available proxy for "the value changed other
            // than by a keystroke", which is the part of this row GD-192 is actually about.
            form.strength.typePassword(GOOD_PASSWORD);
            assertTrue(form.strength.allRulesGreen(), "The checklist should reflect a programmatically-set value immediately (GD-192)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-006: Weak label for a short lowercase password")
        void weakLabelForShortPassword() {
            form.strength.typePassword("abc");
            assertEquals(PasswordStrengthField.STRENGTH_WEAK, form.strength.strengthLabel(), "\"abc\" should read Weak (GD-193)");
            assertTrue(form.isSubmitDisabled(), "Add member should stay disabled for a Weak password");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-007: Medium label when only the number rule is missing")
        void mediumLabelMissingNumber() {
            form.strength.typePassword("Abcdefgh");
            assertEquals(PasswordStrengthField.STRENGTH_MEDIUM, form.strength.strengthLabel(), "\"Abcdefgh\" should read Medium (GD-193)");
            assertTrue(form.isSubmitDisabled(), "Add member should stay disabled for a Medium password - only Good can submit");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-008: Good label when all 5 rules are met")
        void goodLabelAllRulesMet() {
            form.strength.typePassword(GOOD_PASSWORD);
            assertEquals(PasswordStrengthField.STRENGTH_GOOD, form.strength.strengthLabel(), "\"" + GOOD_PASSWORD + "\" should read Good (GD-193)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-009: Label moves Weak to Medium to Good while typing")
        void labelProgressesWhileTyping() {
            form.strength.typePassword("abc");
            assertEquals(PasswordStrengthField.STRENGTH_WEAK, form.strength.strengthLabel(), "\"abc\" should read Weak (GD-193)");
            form.strength.typePassword("Abcdefgh");
            assertEquals(PasswordStrengthField.STRENGTH_MEDIUM, form.strength.strengthLabel(), "\"Abcdefgh\" should read Medium (GD-193)");
            form.strength.typePassword(GOOD_PASSWORD);
            assertEquals(PasswordStrengthField.STRENGTH_GOOD, form.strength.strengthLabel(), "\"" + GOOD_PASSWORD + "\" should read Good (GD-193)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-010: Label moves down when a character is deleted (OQ-05)")
        void labelMovesDownOnDelete() {
            form.strength.typePassword(GOOD_PASSWORD);
            assertEquals(PasswordStrengthField.STRENGTH_GOOD, form.strength.strengthLabel());
            form.strength.backspacePassword();
            assertFalse(PasswordStrengthField.STRENGTH_GOOD.equals(form.strength.strengthLabel()),
                    "Deleting the digit should move the label off Good - the sheet's OQ-05 leaves open whether it lands on Medium or Weak (GD-193)");
            assertTrue(form.isSubmitDisabled(), "Add member should disable again once the label leaves Good");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-011: A Medium password never enables Add member")
        void mediumNeverEnablesSubmit() {
            form.strength.typePassword("Abcdefgh");
            form.strength.typeConfirm("Abcdefgh");
            assertTrue(form.isSubmitDisabled(), "Add member should stay disabled for a Medium password even though it matches Confirm (GD-193, GD-195)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-CHK-012: Strength label colour and position match the design")
        void labelColourAndPosition() {
            form.strength.typePassword("abc");
            String weak = form.strength.strengthLabel();
            form.strength.typePassword("Abcdefgh");
            String medium = form.strength.strengthLabel();
            form.strength.typePassword(GOOD_PASSWORD);
            String good = form.strength.strengthLabel();
            assertEquals(PasswordStrengthField.STRENGTH_WEAK, weak, "Weak label text (GD-193, colour/position per Figma Version C - not machine-checked here)");
            assertEquals(PasswordStrengthField.STRENGTH_MEDIUM, medium, "Medium label text (GD-193)");
            assertEquals(PasswordStrengthField.STRENGTH_GOOD, good, "Good label text (GD-193)");
        }
    }

    // =====================================================================
    // 4. Add Member - confirm password & button state
    // =====================================================================

    @Nested
    @Order(40)
    @DisplayName("4. Add Member - confirm password & Add member button state")
    class ConfirmAndButton {

        private AddMemberPage form;

        @BeforeEach
        void openForm() {
            form = openAddMember();
        }

        @Test
        @DisplayName("BO-ADD-BTN-001: Matching passwords show no error")
        void matchingPasswordsShowNoError() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            assertFalse(form.strength.mismatchErrorShown(), "Matching passwords should never show a mismatch error");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-002: Mismatched passwords show an error")
        void mismatchedPasswordsShowError() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm("Abcdefg2");
            assertTrue(form.strength.mismatchErrorShown(), "\"" + PasswordStrengthField.MISMATCH_ERROR + "\" should be shown on a mismatch (GD-194)");
            assertTrue(form.isSubmitDisabled(), "Add member should be disabled while passwords do not match");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-003: Match is case-sensitive")
        void matchIsCaseSensitive() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD.toLowerCase(java.util.Locale.ROOT));
            assertTrue(form.strength.mismatchErrorShown(), "A case-different Confirm password should be treated as a mismatch (GD-194)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-004: Changing Password after a match re-checks the match")
        void editingPasswordRechecksMatch() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            form.strength.typePassword(GOOD_PASSWORD + "2");
            assertTrue(form.strength.mismatchErrorShown(), "Editing Password after a match should re-raise the mismatch error (GD-194, GD-195)");
            assertTrue(form.isSubmitDisabled(), "Add member should disable again once the match breaks");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-005: Fixing Confirm password clears the error")
        void fixingConfirmClearsError() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm("Abcdefg2");
            form.strength.typeConfirm(GOOD_PASSWORD);
            assertFalse(form.strength.mismatchErrorShown(), "Correcting Confirm password should clear the mismatch error (GD-194)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-006: Trailing space in Confirm causes a mismatch")
        void trailingSpaceInConfirmMismatches() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD + " ");
            assertTrue(form.strength.mismatchErrorShown(), "A trailing space in Confirm should be treated as a mismatch (GD-194)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-007: Add member is disabled in the default (empty) state")
        void disabledInDefaultState() {
            assertTrue(form.isSubmitDisabled(), "Add member should be disabled on a fresh, empty form (GD-195)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-008: Add member is disabled in the Weak state")
        void disabledInWeakState() {
            form.strength.typePassword("abc");
            form.strength.typeConfirm("abc");
            assertTrue(form.isSubmitDisabled(), "Add member should be disabled for a Weak password (GD-195)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-009: Add member is disabled in the Medium state")
        void disabledInMediumState() {
            form.strength.typePassword("Abcdefgh");
            form.strength.typeConfirm("Abcdefgh");
            assertTrue(form.isSubmitDisabled(), "Add member should be disabled for a Medium password (GD-195)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-010: Add member is disabled when Good but not matching")
        void disabledWhenGoodButNotMatching() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD.toLowerCase(java.util.Locale.ROOT));
            assertTrue(form.isSubmitDisabled(), "Add member should be disabled while Good but not matching (GD-195)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-011: Add member enables only when Good and matching (Ready)")
        void enabledOnlyWhenReady() {
            assertTrue(form.isSubmitDisabled(), "Add member should start disabled");
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            assertFalse(form.isSubmitDisabled(), "Add member should become enabled once Good and matching (GD-195)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-012: Button disables again when the Ready state is broken")
        void disablesAgainWhenReadyBreaks() {
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            form.strength.backspacePassword();
            assertTrue(form.isSubmitDisabled(), "Add member should disable again once Ready is broken (GD-195)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-ADD-BTN-013: Enter key does not submit while disabled")
        void enterDoesNotSubmitWhileDisabled() {
            // Username, Full name, Phone and Email are left blank so that even an unexpected
            // submit cannot create an identifiable member - only the navigation is asserted.
            form.strength.typePassword("abc");
            form.strength.typeConfirm("abc");
            sessionPage.locator("input[placeholder='Confirm password']").press("Enter");
            sessionPage.waitForTimeout(1000);
            assertTrue(sessionPage.url().contains(AddMemberPage.PATH),
                    "Pressing Enter with a Weak password should not submit the form (GD-195), but the page navigated to " + sessionPage.url());
        }
    }

    // =====================================================================
    // 5. Add Member - after submit
    // =====================================================================

    @Nested
    @Order(50)
    @DisplayName("5. Add Member - after submit")
    class AfterSubmit {

        @Test
        @Tag("creates-data")
        @DisplayName("BO-ADD-AFT-001: A member is created with valid data")
        void memberIsCreated() {
            String username = ensureFixtureMember();
            assertTrue(list.hasRow(username), "The fixture member \"" + username + "\" should be created and listed");
        }

        @Test
        @DisplayName("BO-ADD-AFT-002: New member status is Active")
        void newMemberIsActive() {
            String username = ensureFixtureMember();
            list.open();
            list.searchByUsername(username);
            assertEquals("Active", list.statusOf(username), "The newly created member should be Active");
        }

        @Test
        @DisplayName("BO-ADD-AFT-003: Admin returns to Member List with the new member shown")
        void returnsToMemberListShowingNewMember() {
            String username = PREFIX + "r" + System.currentTimeMillis() % 100_000_000L;
            AddMemberPage form = openAddMember();
            form.fillUsername(username);
            form.fillFullName("QA Redirect Check");
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            form.fillEmail(username + "@example.com");
            form.submitAndRead();
            assertTrue(sessionPage.url().contains(MemberListPage.PATH),
                    "Add member should return to Member List, but the page is at " + sessionPage.url());
            list.searchByUsername(username);
            assertTrue(list.hasRow(username), "The new member should appear in Member List after the redirect");
        }

        @Test
        @DisplayName("BO-ADD-AFT-004: Admin is not logged in as the new member")
        void adminSessionUnchanged() {
            ensureFixtureMember();
            assertEquals(USERNAME, shell.signedInAccount(),
                    "The signed-in account should remain the operator (" + USERNAME + ") after creating a member, not switch to it");
        }

        @Test
        @DisplayName("BO-ADD-AFT-005: No OTP is sent to the new member's phone")
        void noOtpSentOnCreate() {
            String username = PREFIX + "o" + System.currentTimeMillis() % 100_000_000L;
            AddMemberPage form = openAddMember();
            form.fillUsername(username);
            form.fillFullName("QA OTP Check");
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            form.fillPhoneNumber("91234567");
            form.fillEmail(username + "@example.com");
            form.submitAndRead();
            CaseLog.evidence("There is no SMS log reachable from this project to confirm nothing was sent (GD-195, "
                    + "GD-197). This asserts the safe half: creating the member did not navigate the admin to any "
                    + "OTP/verification step.");
            assertFalse(sessionPage.url().toLowerCase(java.util.Locale.ROOT).contains("otp"),
                    "Creating a member should not route the admin through an OTP step");
        }

        @Test
        @DisplayName("BO-ADD-AFT-006: New member can log in with the admin-set password")
        void newMemberCanLogIn() {
            abort("Logging in as the new member on the front end needs member-site page objects. Per AGENTS.md, "
                    + "Admin_weibo carries none - that is NewWeibo_automation's. Confirm this from that project "
                    + "against the member created here, or once this project gains its own member-site login page.");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("BO-ADD-AFT-007: Double-click on Add member creates only one member")
        void doubleClickCreatesOnlyOne() {
            String username = PREFIX + "d" + System.currentTimeMillis() % 100_000_000L;
            AddMemberPage form = openAddMember();
            form.fillUsername(username);
            form.fillFullName("QA Double Click Check");
            form.strength.typePassword(GOOD_PASSWORD);
            form.strength.typeConfirm(GOOD_PASSWORD);
            form.fillEmail(username + "@example.com");
            form.submitButton().click();
            form.submitButton().click();
            sessionPage.waitForTimeout(1500);
            list.open();
            list.searchByUsername(username);
            assertEquals(1, list.rowCount(username),
                    "A double-click on Add member should create exactly one member, but " + list.rowCount(username) + " rows were found for \"" + username + "\"");
        }
    }

    // =====================================================================
    // 6. Admin Reset Password (member profile)
    // =====================================================================

    @Nested
    @Order(60)
    @DisplayName("6. Admin Reset Password (member profile)")
    class ResetPassword {

        private ResetPasswordPage reset;

        @BeforeEach
        void openResetPassword() {
            ensureFixtureMember();
            reset = new ResetPasswordPage(sessionPage);
            reset.openFor(fixtureMemberId);
        }

        @Test
        @DisplayName("BO-RST-001: New password and Confirm are masked by default")
        void maskedByDefault() {
            assertTrue(reset.isShowing(), "The Reset password form should be showing");
            reset.strength.typePassword(GOOD_PASSWORD);
            reset.strength.typeConfirm(GOOD_PASSWORD);
            assertTrue(reset.strength.passwordMasked(), "New password should be masked by default");
            assertTrue(reset.strength.confirmMasked(), "Confirm password should be masked by default");
        }

        @Test
        @Tag("known-defect")
        // Also covers workbook row: BO-RST-001 (helper text and Save-disabled halves; the masking
        // half is answered by maskedByDefault above, which is the row's real id).
        @DisplayName("BO-RST-901: Helper text is shown and Save is disabled by default (GD-198)")
        void helperShownAndSaveDisabledByDefault() {
            assertTrue(reset.strength.collapsedHelperShown(), "An untouched New password field should show the helper text (GD-198)");
            assertTrue(reset.isSaveDisabled(), "Reset all should be disabled on a fresh form (GD-198)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-002: Each show/hide icon toggles only its own field")
        void showHideTogglesOwnFieldOnly() {
            reset.strength.typePassword(GOOD_PASSWORD);
            reset.strength.typeConfirm(GOOD_PASSWORD);
            boolean clicked = reset.strength.clickShowOnPassword();
            assertTrue(clicked, "New password should have a show icon to click (GD-198)");
            assertFalse(reset.strength.passwordMasked(), "New password should be visible after clicking its icon");
            assertTrue(reset.strength.confirmMasked(), "Confirm password should stay masked when only New password's icon is clicked");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-003: Checklist and strength label update on every keystroke")
        void checklistUpdatesLive() {
            reset.strength.typePasswordKeyByKey(GOOD_PASSWORD);
            assertTrue(reset.strength.allRulesGreen(), "Every rule should read green after typing the password (GD-198)");
            assertEquals(PasswordStrengthField.STRENGTH_GOOD, reset.strength.strengthLabel(), "The strength label should read Good (GD-198)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-004: Length boundaries 7/8/20/21")
        void lengthBoundaries() {
            assertFalse(ruleState(reset, "Abcdef1"), "7 characters should fail the length rule (GD-198)");
            assertTrue(ruleState(reset, "Abcdef12"), "8 characters should pass the length rule (GD-198)");
            assertTrue(ruleState(reset, "Abcdefghijklmnopqr12"), "20 characters should pass the length rule (GD-198)");
            assertFalse(ruleState(reset, "Abcdefghijklmnopqrs12"), "21 characters should fail the length rule (GD-198)");
        }

        private boolean ruleState(ResetPasswordPage page, String password) {
            page.strength.typePassword(password);
            return Boolean.TRUE.equals(page.strength.ruleState(Rule.LENGTH));
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-005: Space at start, middle or end fails")
        void spacesFail() {
            reset.strength.typePassword(" Abcdefg1");
            assertEquals(Boolean.FALSE, reset.strength.ruleState(Rule.NO_SPACES), "A leading space should fail the no-spaces rule (GD-198)");
            reset.strength.typePassword("Abcd efg1");
            assertEquals(Boolean.FALSE, reset.strength.ruleState(Rule.NO_SPACES), "A middle space should fail the no-spaces rule (GD-198)");
            reset.strength.typePassword("Abcdefg1 ");
            assertEquals(Boolean.FALSE, reset.strength.ruleState(Rule.NO_SPACES), "A trailing space should fail the no-spaces rule (GD-198)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-006: Save disabled for Weak")
        void saveDisabledForWeak() {
            reset.strength.typePassword("abc");
            reset.strength.typeConfirm("abc");
            assertTrue(reset.isSaveDisabled(), "Save should be disabled for a Weak password (GD-198)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-007: Save disabled for Medium")
        void saveDisabledForMedium() {
            reset.strength.typePassword("Abcdefgh");
            reset.strength.typeConfirm("Abcdefgh");
            assertTrue(reset.isSaveDisabled(), "Save should be disabled for a Medium password (GD-198)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-008: Save disabled when Good but not matching")
        void saveDisabledWhenGoodButNotMatching() {
            reset.strength.typePassword(GOOD_PASSWORD);
            reset.strength.typeConfirm(GOOD_PASSWORD.toLowerCase(java.util.Locale.ROOT));
            assertTrue(reset.strength.mismatchErrorShown(), "A case-different Confirm should raise the mismatch error (GD-198)");
            assertTrue(reset.isSaveDisabled(), "Save should be disabled while Good but not matching (GD-198)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-009: Save enabled only when Good and matching")
        void saveEnabledOnlyWhenReady() {
            assertTrue(reset.isSaveDisabled(), "Save should start disabled");
            reset.strength.typePassword(GOOD_PASSWORD);
            reset.strength.typeConfirm(GOOD_PASSWORD);
            assertFalse(reset.isSaveDisabled(), "Save should become enabled once Good and matching (GD-198)");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-010: Editing New password after a match re-checks the match")
        void editingAfterMatchRechecks() {
            reset.strength.typePassword(GOOD_PASSWORD);
            reset.strength.typeConfirm(GOOD_PASSWORD);
            reset.strength.typePassword(GOOD_PASSWORD + "2");
            assertTrue(reset.strength.mismatchErrorShown(), "Editing New password after a match should re-raise the mismatch error (GD-198)");
            assertTrue(reset.isSaveDisabled(), "Save should disable again once the match breaks");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("BO-RST-011: Password updated, success message, admin stays on the member profile")
        void saveUpdatesPasswordAndStays() {
            reset.strength.typePassword(GOOD_PASSWORD);
            reset.strength.typeConfirm(GOOD_PASSWORD);
            String said = reset.saveAndRead();
            CaseLog.data("Reset all said", said.isEmpty() ? "(nothing)" : said);
            assertFalse(said.isBlank(), "Reset all should show a success message");
            assertTrue(sessionPage.url().contains("member-details") || sessionPage.url().contains(fixtureMemberId),
                    "The admin should stay on the member profile after Reset all, but the page is at " + sessionPage.url());
        }

        @Test
        @DisplayName("BO-RST-012: Member can log in with the new password only")
        void memberLogsInWithNewPasswordOnly() {
            abort("Logging in as the member on the front end needs member-site page objects, which this project "
                    + "does not carry (see BO-ADD-AFT-006). Confirm from NewWeibo_automation against the password "
                    + "BO-RST-011 just set.");
        }

        @Test
        @Tag("known-defect")
        @DisplayName("BO-RST-013: Reset form uses the same checklist/label/match components as Add Member")
        void reusesSameComponentsAsAddMember() {
            AddMemberPage addMember = openAddMember();
            addMember.strength.typePassword("abc");
            reset.strength.typePassword("abc");
            assertEquals(addMember.strength.strengthLabel(), reset.strength.strengthLabel(),
                    "Both forms should show the same strength label for the same password (GD-198)");
            assertEquals(addMember.strength.checklistShown(), reset.strength.checklistShown(),
                    "Both forms should show the same checklist presence for the same password (GD-198)");
        }
    }

    // =====================================================================
    // 7. Server-side validation (API level)
    // =====================================================================

    @Nested
    @Order(70)
    @DisplayName("7. Server-side validation (API level)")
    class ServerSideValidation {

        private void needsEndpoint(String which) {
            abort("This pass did not capture the real " + which + " request (endpoint, payload shape or an auth "
                    + "approach), and this project has no HTTP-level test harness yet to build one confidently "
                    + "against. The design's own note is explicit that server-side validation matters - 'validate "
                    + "again on the server, the disabled button is not a security control' - so this is worth "
                    + "doing: capture the real request from the network tab on a real Add member / Reset all "
                    + "submit and implement this row for real.");
        }

        @Test
        @DisplayName("BO-API-001: Valid request creates the member")
        void validRequestCreatesMember() {
            needsEndpoint("Add Member API");
        }

        @Test
        @DisplayName("BO-API-002: Weak password sent directly is refused")
        void weakPasswordRefused() {
            needsEndpoint("Add Member API");
        }

        @Test
        @DisplayName("BO-API-003: Medium password sent directly is refused")
        void mediumPasswordRefused() {
            needsEndpoint("Add Member API");
        }

        @Test
        @DisplayName("BO-API-004: Mismatched passwords are refused")
        void mismatchedPasswordsRefused() {
            needsEndpoint("Add Member API");
        }

        @Test
        @DisplayName("BO-API-005: Boundary lengths 7 and 21 are refused")
        void boundaryLengthsRefused() {
            needsEndpoint("Add Member API");
        }

        @Test
        @DisplayName("BO-API-006: Password with a space is refused")
        void passwordWithSpaceRefused() {
            needsEndpoint("Add Member API");
        }

        @Test
        @DisplayName("BO-API-007: Error lists every failed rule")
        void errorListsEveryFailedRule() {
            needsEndpoint("Add Member API");
        }

        @Test
        @DisplayName("BO-API-008: Password is never returned in any response")
        void passwordNeverReturned() {
            needsEndpoint("Add Member / member detail API");
        }

        @Test
        @DisplayName("BO-API-009: Password is stored hashed")
        void passwordStoredHashed() {
            abort("Reading the stored password value needs DB read access (dev/QA support), which this "
                    + "automation project has no access to and should not be given.");
        }

        @Test
        @DisplayName("BO-API-010: Valid reset request updates the password")
        void validResetRequestUpdatesPassword() {
            needsEndpoint("Reset Password API");
        }

        @Test
        @DisplayName("BO-API-011: Weak password sent directly to reset is refused")
        void weakResetPasswordRefused() {
            needsEndpoint("Reset Password API");
        }

        @Test
        @DisplayName("BO-API-012: Mismatched reset passwords are refused")
        void mismatchedResetPasswordsRefused() {
            needsEndpoint("Reset Password API");
        }

        @Test
        @DisplayName("BO-API-013: Password not returned in reset response")
        void passwordNotReturnedInResetResponse() {
            needsEndpoint("Reset Password API");
        }

        @Test
        @DisplayName("BO-API-014: Same validator as front-end registration")
        void sameValidatorAsFrontEndRegistration() {
            needsEndpoint("Add Member API, Reset Password API and the member-site registration API together");
        }
    }
}
