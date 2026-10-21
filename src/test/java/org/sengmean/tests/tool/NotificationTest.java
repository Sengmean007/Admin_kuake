package org.sengmean.tests.tool;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.sengmean.base.BasePage;
import org.sengmean.base.BaseTest;
import org.sengmean.page.client.MemberSignInPage;
import org.sengmean.page.client.MessageInboxPage;
import org.sengmean.page.member.MemberGroupPage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.page.tool.NotificationFormPage;
import org.sengmean.page.tool.NotificationListPage;
import org.sengmean.page.tool.NotificationListPage.NotificationRow;
import org.sengmean.page.tool.RecordHistoryPage;
import org.sengmean.page.tool.RecordHistoryPage.HistoryRow;
import org.sengmean.report.CaseLog;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.sengmean.page.tool.NotificationFormPage.AGENT_GROUP;
import static org.sengmean.page.tool.NotificationFormPage.MEMBER_GROUP;
import static org.sengmean.testdata.NotificationTestData.*;

/**
 * <b>Tool &gt; Notification</b> - the 102 rows of the client's "Notification" tab ({@code NOT-LST},
 * {@code NOT-CRT}, {@code NOT-EDT}, {@code NOT-DEL}, {@code NOT-ACT}, {@code NOT-GRP}, {@code NOT-RHL},
 * {@code NOT-RHD}, {@code NOT-I18N}, {@code NOT-CLI}, {@code NOT-ACC}, {@code NOT-REG}), one {@code @Nested}
 * class per section of the sheet, each case carrying its row's id exactly as the sheet writes it - plus,
 * since 2026-09-29/30 and 2026-10-01, 46 project-added rows numbered on from each tab's last id ({@code NOT-CRT-027} ..
 * {@code NOT-CRT-038}, {@code NOT-LST-010} .. {@code 015}, and so on; see AGENTS.md), which have no
 * workbook row yet. 148 rows in all.
 *
 * <pre>./mvnw test -Dsuite=ToolSuite</pre>
 *
 * <p>{@code -Dsuite=NotificationE2ESuite} runs the process end to end - every section below except
 * Access control - with the seed suites left out; each section also has a suite of its own in
 * {@code org.sengmean.suites.tool}.
 *
 * <h2>Written ahead of the feature, measured since</h2>
 * On 2026-09-26 the back office had no Tool menu and {@code /tool/notification} rendered an empty
 * shell; the member site had no Message Center. Every case was written in full against the workbook
 * (v1.3) and the client's six captures ({@code docs/mockups/notification}). The screen shipped at
 * {@code /tools/notification} on 2026-09-28 and the page objects have been measured against it since;
 * {@link #signInAndProbe()} still decides once whether the screen exists, and while it does not every
 * row reports <b>Not run</b> naming that as the reason.
 *
 * <h2>The order is one pass through the feature's life</h2>
 * <ol>
 *   <li><b>10 - Access control.</b> Nothing below means anything if the operator cannot use the screen.</li>
 *   <li><b>20 - Create.</b> {@code NOT-CRT-001} makes the Daily rule the way the workbook says to, so
 *       every later row reads a rule Create really produced.</li>
 *   <li><b>30 - List</b>, then <b>40 - Target groups.</b> Reading what Create made, then the pickers.</li>
 *   <li><b>50 - Edit.</b> Every edit is undone in {@code @AfterEach}.</li>
 *   <li><b>60 - Send Now.</b> It writes the Record History entries the next two sections read.</li>
 *   <li><b>70 - Record History list</b> and <b>80 - detail.</b></li>
 *   <li><b>90 - Localization</b> and <b>100 - Client Message Inbox.</b> Downstream of the admin side.</li>
 *   <li><b>110 - Regression</b> and <b>120 - Delete</b>, last, because they destroy - and each destroys
 *       only a rule it made for the purpose. The {@link #sweepSuiteNotifications()} at the very end
 *       removes the rest.</li>
 * </ol>
 *
 * <h2>The workbook's open questions - answered 2026-09-26</h2>
 * All twenty AQ items were resolved in the workbook itself (v1.3), and the rows assert the answers:
 * AQ-01 this screen manages System (Non-Event Trigger) rules only; AQ-02 English, 简体 and 繁體 are the
 * required languages (superseded for {@code NOT-CRT-007} on 2026-10-01 - see the Feature Report below); AQ-03 the
 * circular arrow is Send Now; AQ-04 Delete asks first; AQ-06 Record History
 * keeps the as-sent targets; AQ-09 the next send uses the latest content; AQ-10 a deleted target group never halts a
 * schedule; AQ-11 completed and rejected transactions both notify; AQ-13 Override copies, it does not
 * translate; AQ-14 Admin has Super Admin's rights; AQ-15 overlapping targets are allowed; AQ-16 Inactive is
 * not sent; AQ-17 Week day is a required single select and Timer is whole hours; AQ-18 only Once auto-ends;
 * AQ-19 the list shows "Weekly (Sunday)"; AQ-20 any group combination saves. <b>AQ-08 is half open</b>
 * (whether Failed shows a reason or a retry), and six rows still read "confirm whether" -
 * {@code NOT-DEL-004}, {@code NOT-GRP-003}, {@code NOT-RHD-005}, {@code NOT-RHD-006}, {@code NOT-I18N-005}
 * and {@code NOT-REG-007}: those record what the back office does and assert only what any answer must
 * satisfy, until the expected result is decided.
 *
 * <h2>The System Notification Feature Report (received 2026-09-28)</h2>
 * The client's requirement document, applied on top of the answers above. Its language rule (4.1, every
 * language required) is what the site enforces ("Title and Content are required for all languages") and, since
 * Si's 2026-10-01 ruling, what {@code NOT-CRT-007} expects - reversing the 2026-09-28 "three languages" ruling under
 * which it stayed red and GD-103 was filed. {@code NOT-CRT-006}/{@code 008} hold under either rule. From the rest of it:
 * <ul>
 *   <li>Link is optional and any link format is accepted (4.2) - superseding AQ-05's "well-formed URL"
 *       ({@code NOT-CRT-004}, {@code NOT-CRT-005}).</li>
 *   <li>Once without a Timer ("—") is sent immediately on activation; Once with a Timer is sent once at
 *       it; Daily and Weekly recur (5) - {@code NOT-CRT-014}.</li>
 *   <li>Every schedule runs on Hong Kong Time, UTC+8 (5): every "now", "today" and "next hour" the suite
 *       works out is taken in {@code NotificationTestData.SCHEDULE_ZONE}, not the machine's zone - except
 *       the <b>Timer</b> a row sets, which since 2026-09-30 is the machine's current hour plus one
 *       ({@code TIMER_ZONE}): Si's ruling, because the scheduler runs every hour and that is its next run.</li>
 *   <li>Select All removes the restriction for its side; Select All on both reaches all eligible users
 *       (6) - {@code NOT-GRP-003} is no longer a "confirm whether" row. Saving with both Selected
 *       columns empty must be <b>refused</b> ({@code NOT-CRT-022}): AQ-07 stands - Si reversed the 2026-09-28
 *       "expect the acceptance" ruling on 2026-10-01, so the row is red until the validation ships (GD-136).</li>
 *   <li>An Inactive notification is never delivered (4.3, 5) - {@code NOT-CRT-023}, {@code NOT-CLI-011}.</li>
 * </ul>
 *
 * <h2>What it writes</h2>
 * Every rule it creates is titled with {@value org.sengmean.testdata.NotificationTestData#PREFIX} and
 * swept at the start and end of the class. The rows that write are tagged {@code creates-data}.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(110)   // after Other Category (100) in a full run
@DisplayName("Notification")
public class NotificationTest extends BaseTest {

    // =====================================================================
    // Session, probe, and the per-case undo
    // =====================================================================

    private static BrowserContext session;
    private static Page sessionPage;
    private static AdminShellPage shell;
    private static NotificationListPage list;

    /** Whether the Notification screen answered the probe. Decides for every row. */
    private static boolean screenPresent;
    private static String probeFailure = "";

    private static final int PROBE_ATTEMPTS = 2;

    /** Whatever the running case must put back, registered before it writes. */
    private static Runnable restore;

    /** A second role's, or a member's, session, when the running case opened one. */
    private BrowserContext extraSession;

    /** The member site session behind the last {@link #memberInbox()}, for a row that switches the member's wallet. */
    private MemberSignInPage memberSite;

    @BeforeAll
    static void signInAndProbe() {
        session = newContext();
        sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        // The header currency decides which records the back office shows (see
        // AdminShellPage.selectCurrency), so the suite runs in test.currency - the one
        // NotificationSeedData seeds into - rather than whatever the header happened to default to.
        shell.useCurrency(CURRENCY);
        list = new NotificationListPage(sessionPage);

        for (int attempt = 1; attempt <= PROBE_ATTEMPTS && !screenPresent; attempt++) {
            try {
                list.open();
                screenPresent = list.isShowing();
                probeFailure = screenPresent ? "" : "the address " + AdminShellPage.NOTIFICATION_HREF
                        + " answered, but showed no Notification table with an Add notification control"
                        + (shell.hasMenuLink(AdminShellPage.NOTIFICATION_HREF) ? "" : ", and the menu offers no Tool > Notification entry");
            } catch (RuntimeException screenNeverArrived) {
                probeFailure = "attempt " + attempt + ": " + firstLine(screenNeverArrived.getMessage());
            }
        }
        CaseLog.data("Notification screen deployed", screenPresent + (probeFailure.isEmpty() ? "" : " (" + probeFailure + ")"));
        if (screenPresent) {
            sweepSuiteNotifications();
        }
    }

    @AfterAll
    static void closeSession() {
        try {
            if (screenPresent) {
                sweepSuiteNotifications();
            }
        } catch (RuntimeException couldNotSweep) {
            CaseLog.evidence("The closing sweep failed (" + firstLine(couldNotSweep.getMessage()) + "); notifications titled \""
                    + PREFIX + "...\" may be left in Tool > Notification.");
        }
        if (session != null) {
            session.close();
            session = null;
        }
        sessionPage = null;
        shell = null;
        list = null;
    }

    @BeforeEach
    void requireScreenAndOpenList() {
        requireScreen();
        restore = null;
        openList();
    }

    @AfterEach
    void undoWhatThisCaseChanged() {
        if (extraSession != null) {
            extraSession.close();
            extraSession = null;
        }
        if (restore == null) {
            return;
        }
        try {
            restore.run();
        } catch (RuntimeException couldNotRestore) {
            CaseLog.evidence("This case could not put back what it changed: " + firstLine(couldNotRestore.getMessage())
                    + " - check Tool > Notification by hand.");
        } finally {
            restore = null;
        }
    }

    private static void requireScreen() {
        if (!screenPresent) {
            abort("Tool > Notification is not deployed on " + BASE_URL + " yet - " + probeFailure
                    + ". This row is written in full against the client's workbook and captures and runs as written once the "
                    + "screen exists (it did not on 2026-09-26). If it HAS shipped, correct test.notification.path in Config/ "
                    + "and confirm the locators in org.sengmean.page.tool.");
        }
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "(no message)";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    // =====================================================================
    // Fixtures
    // =====================================================================

    /**
     * Opens the Notification tab showing every rule: the capture's list defaults its Status filter to
     * Active, which would hide every Inactive rule a row looks for. Largest page size, 繁體 titles.
     */
    private static void openList() {
        openList(list);
    }

    private static void openList(NotificationListPage on) {
        on.open();
        for (String option : on.statusFilterOptions()) {
            if (option.toLowerCase().contains("all")) {
                on.filterStatus(option);
                break;
            }
        }
        List<Integer> sizes = on.pageSizeOptions();
        if (!sizes.isEmpty()) {
            on.setPageSize(sizes.get(sizes.size() - 1));
        }
    }

    /** Deletes every notification whose title carries the suite's prefix - and nothing else. */
    private static void sweepSuiteNotifications() {
        openList();
        for (String title : list.titles()) {
            if (title.startsWith(PREFIX)) {
                CaseLog.step("Removing the suite's leftover notification \"" + title + "\".");
                list.deleteIfListed(title);
                openList();
            }
        }
    }

    /** What a rule is made of. Defaults are the workbook's NOT-CRT-001: Daily, 12 AM, Active, one group each. */
    private static final class Draft {
        String title;
        String content = CONTENT;
        String link = LINK;
        String schedule = SCHEDULE_DAILY;
        String weekDay = SUNDAY;
        String timer = TIMER_MIDNIGHT;
        String status = STATUS_ACTIVE;
        List<String> agents = List.of(TARGET_AGENT_GROUP);
        List<String> members = List.of(TARGET_MEMBER_GROUP);
        boolean broadcast;

        Draft(String title) {
            this.title = title;
        }

        Draft schedule(String schedule, String timer) {
            this.schedule = schedule;
            this.timer = timer;
            return this;
        }

        Draft status(String status) {
            this.status = status;
            return this;
        }

        Draft broadcast() {
            this.broadcast = true;
            return this;
        }

        Draft agents(List<String> agents) {
            this.agents = agents;
            return this;
        }

        Draft members(List<String> members) {
            this.members = members;
            return this;
        }
    }

    /**
     * Opens Add notification and fills a draft: title, content and link on 繁體, Override to All languages
     * (which also satisfies AQ-02's English and 简体), the settings, the targets. Does not save.
     */
    private static NotificationFormPage fill(Draft d) {
        NotificationFormPage form = list.clickAdd();
        form.fillLanguage(TAB_CHINESE, d.title, d.content, d.link);
        form.overrideToAll();
        form.setType(TYPE_SYSTEM);
        form.setSchedule(d.schedule);
        if (SCHEDULE_WEEKLY.equals(d.schedule)) {
            form.setWeekDay(d.weekDay);
        }
        // Si, 2026-09-28: a Once rule takes a Timer too - an hour sends it once at that hour, "—" sends
        // it immediately on activation. A Once draft with no Timer is the immediate kind.
        if (d.timer != null) {
            form.setTimer(d.timer);
        } else if (SCHEDULE_ONCE.equals(d.schedule)) {
            form.setTimer(TIMER_SEND_IMMEDIATELY);
        }
        form.setStatus(d.status);
        if (d.broadcast) {
            form.chooseSelectAll(AGENT_GROUP);
            form.chooseSelectAll(MEMBER_GROUP);
        } else {
            form.target(AGENT_GROUP, d.agents);
            form.target(MEMBER_GROUP, d.members);
        }
        return form;
    }

    /** Creates a rule and returns whether it was listed afterwards. */
    private static boolean create(Draft d) {
        NotificationFormPage form = fill(d);
        form.clickSave();
        openList();
        boolean listed = list.isListed(d.title);
        CaseLog.data("Created \"" + d.title + "\"", listed ? "listed" : "not listed - " + form.lastMessage());
        return listed;
    }

    /** Makes sure a rule exists, creating it when it does not. Aborts, naming it, if it cannot. */
    private static void ensure(Draft d) {
        openList();
        if (list.isListed(d.title)) {
            return;
        }
        CaseLog.step("Creating the fixture notification \"" + d.title + "\".");
        if (!create(d)) {
            abort("The fixture notification \"" + d.title + "\" could not be created, so this row has nothing to act on. "
                    + "Run NOT-CRT-001 on its own - it drives the same form and reports why Save refused.");
        }
    }

    /** The Daily rule NOT-CRT-001 makes - the one most rows read. */
    private static Draft daily() {
        return new Draft(DAILY_TITLE);
    }

    private static Draft weekly() {
        Draft d = new Draft(WEEKEND_TITLE).schedule(SCHEDULE_WEEKLY, TIMER_8PM);
        d.weekDay = SUNDAY;
        return d;
    }

    private static Draft broadcast() {
        return new Draft(BROADCAST_TITLE).broadcast();
    }

    /**
     * The rule Send Now pushes. Si, 2026-09-30: Send Now test data targets Select all on both Agent Group and
     * Member Group, so what it pushes reaches the configured member whatever groups it is in - the Daily rule
     * stays targeted at the configured groups, because the list, history and outsider rows read those names.
     */
    private static Draft sendNow() {
        return new Draft(SEND_NOW_TITLE).broadcast();
    }

    /** Deletes a rule after the case, whatever happened in it. */
    private static void deleteAfterwards(String title) {
        Runnable earlier = restore;
        restore = () -> {
            if (earlier != null) {
                earlier.run();
            }
            openList();
            list.deleteIfListed(title);
        };
    }

    /** Asserts Save was refused: the form is still up and nothing new is listed under the title. */
    private static void assertRefused(NotificationFormPage form, String title, String why) {
        String said = form.messages();
        CaseLog.data("Refusal", said.isEmpty() ? "(no message)" : said);
        assertTrue(form.isOpen(), "Save should be blocked " + why + ", but the form closed (" + said + ")");
        openList();
        assertFalse(list.isListed(title), "No notification should be created " + why);
    }

    /** The opposite of {@link #assertRefused}: Save went through, the form closed, and the rule is listed. */
    private static void assertSaved(NotificationFormPage form, String title, String how) {
        String said = form.lastMessage();
        CaseLog.data("Save " + how, said.isEmpty() ? "(no message)" : said);
        assertFalse(form.isOpen(), "Save should go through " + how + ", but the form stayed open (" + form.messages() + ")");
        openList();
        assertTrue(list.isListed(title), "The notification saved " + how + " should be listed");
    }

    /** How the list's Timer column shows a Weekly rule: "Sunday/08 PM" (measured 2026-09-28). */
    private static String weeklyTimer(String weekDay, String hour) {
        return weekDay + "/" + hour;
    }

    /** Sends a rule now and returns its newest Record History entry, or fails. */
    private static HistoryRow sendNowAndRead(String title) {
        openList();
        String said = list.clickSendNow(title);
        // Measured 2026-09-28: Send Now answers "Permission denied" for the configured operator, and
        // then nothing is logged - say so, rather than fail on the missing entry that follows.
        assertFalse(said.contains("Permission denied"), "Send Now on \"" + title + "\" was refused with \"Permission denied\" for "
                + "the configured operator, so nothing was sent - grant this account the Send Now permission (Jira GD-126) and this row runs as written");
        RecordHistoryPage history = list.openRecordHistory();
        List<HistoryRow> entries = history.entriesFor(title);
        assertFalse(entries.isEmpty(), "Send Now on \"" + title + "\" should log a Record History entry");
        return entries.get(0);
    }

    /** Opens Record History showing today - today in Hong Kong Time, where the suite's own sends are dated. */
    private static RecordHistoryPage historyToday() {
        openList();
        RecordHistoryPage history = list.openRecordHistory();
        String today = LocalDate.now(SCHEDULE_ZONE).toString();
        history.filterDates(today, today);
        return history;
    }

    /** "2 (ID 192 at 2026-09-30 16:33, ID 167 at 2026-09-30 15:04)" - a count a reader can check against the screenshot. */
    private static String describe(List<HistoryRow> entries) {
        if (entries.isEmpty()) {
            return "0";
        }
        return entries.size() + " (" + entries.stream().map(e -> "ID " + e.id() + " at " + e.releasedAt()).collect(Collectors.joining(", ")) + ")";
    }

    /** The hour after now in Hong Kong Time - the clock the Timer runs on - as the Timer spells it: "03 PM". */
    private static String nextHourLabel() {
        // Si, 2026-09-30: the scheduler runs every hour, so the Timer is the machine's current hour plus one.
        return hourLabel(LocalDateTime.now(TIMER_ZONE).plusHours(1).getHour());
    }

    /** The hour before now on the machine's clock, as the Timer spells it - an hour already past today (NOT-CRT-038). */
    private static String pastHourLabel() {
        return hourLabel(LocalDateTime.now(TIMER_ZONE).minusHours(1).getHour());
    }

    private static String hourLabel(int hour) {
        int twelve = hour % 12 == 0 ? 12 : hour % 12;
        return String.format("%02d %s", twelve, hour < 12 ? "AM" : "PM");
    }

    /** True when the text carries at least one CJK character - what a 中文 message must have (NOT-I18N-007). */
    private static boolean hasCjk(String text) {
        return text != null && text.matches("(?s).*[\\u4e00-\\u9fff].*");
    }

    /**
     * Waits until the next whole hour has passed, for the rows only a real firing can decide. Off unless
     * {@code test.notification.waitForSchedule} is true, so an ordinary run never sits for an hour.
     */
    private static void waitPastNextHour() {
        // Both ends on the machine's clock, so the wait matches the Timer nextHourLabel() set (Si, 2026-09-30).
        LocalDateTime firing = LocalDateTime.now(TIMER_ZONE).plusHours(1).withMinute(0).withSecond(0).withNano(0);
        long minutes = Duration.between(LocalDateTime.now(TIMER_ZONE), firing).toMinutes() + 2;
        if (!WAIT_FOR_SCHEDULE) {
            abort("This row can only be decided by letting a schedule fire, which here means waiting " + minutes
                    + " minutes for " + firing.toLocalTime() + ". Run with -Dtest.notification.waitForSchedule=true to let it wait.");
        }
        if (minutes > MAX_SCHEDULE_WAIT_MINUTES) {
            abort("The next firing is " + minutes + " minutes away, beyond test.notification.maxScheduleWaitMinutes ("
                    + MAX_SCHEDULE_WAIT_MINUTES + ").");
        }
        CaseLog.step("Waiting " + minutes + " minutes for the " + firing.toLocalTime() + " firing.");
        sessionPage.waitForTimeout(minutes * 60_000.0);
    }

    /** Opens a second admin browser signed in as another role, or aborts naming the empty setting. */
    private Page signedInAs(String role, String username, String password, String configKey) {
        if (username.isBlank() || password.isBlank()) {
            abort("No " + role + " account is configured - set " + configKey + ".username and " + configKey + ".password in Config/.");
        }
        extraSession = newContext();
        Page rolePage = extraSession.newPage();
        signIn(rolePage, username, password);
        CaseLog.data("Role under test", role + " (" + username + ")");
        return rolePage;
    }

    /** A member's Message Center, signed in on the member site, or aborts naming the account. */
    private MessageInboxPage memberInbox(String username, String password, String configKey) {
        if (username.isBlank() || password.isBlank()) {
            abort("No member account is configured for this row - set " + configKey + ".username and " + configKey + ".password in Config/.");
        }
        extraSession = newContext();
        Page member = extraSession.newPage();
        MemberSignInPage site = new MemberSignInPage(member);
        memberSite = site;
        if (!site.signIn(username, password, CAPTCHA)) {
            abort("The member site refused " + username + " at sign-in, so there is no Message Center to read.");
        }
        // Si, 2026-09-30: the back office keeps notifications per currency and the suite creates them under
        // test.currency, so the member reads the inbox on that wallet - NOT-CLI-013 proves the page follows.
        site.useCurrency(CURRENCY);
        MessageInboxPage inbox = new MessageInboxPage(member);
        inbox.open();
        if (!inbox.isShowing()) {
            abort("The member site shows no Message Center for " + username + " at " + inbox.currentUrl() + ". "
                    + "Check client.messages.path in Config/ (the temporary page is en/inbox-test/) and org.sengmean.page.client.MessageInboxPage.");
        }
        return inbox;
    }

    /** The member every targeted rule reaches: test.user.* in Config/. */
    private MessageInboxPage memberInbox() {
        return memberInbox(USER_USERNAME, USER_PASSWORD, "test.user");
    }

    /** Asserts a session can reach neither the list nor Add notification, by menu or by address. */
    private static void assertOutOfReach(Page rolePage, String role, List<String> addresses) {
        boolean menuOffered = new AdminShellPage(rolePage).hasMenuLink(AdminShellPage.NOTIFICATION_HREF);
        CaseLog.check("Tool > Notification in the menu for " + role + ": " + menuOffered);
        assertFalse(menuOffered, "The " + role + " role should not be offered Tool > Notification in the menu");
        for (String address : addresses) {
            rolePage.navigate(address);
            BasePage.awaitReady(rolePage);
            rolePage.waitForTimeout(1500);
            boolean shown = new NotificationListPage(rolePage).isShowing() || new NotificationFormPage(rolePage).isOpen();
            CaseLog.data(role + " at " + address, "landed on " + rolePage.url());
            assertFalse(shown, "Going directly to " + address + " as " + role + " should be redirected or refused, but it rendered the screen at " + rolePage.url());
        }
    }

    // =====================================================================
    // 11. ACCESS CONTROL - first, because every other section assumes it
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("11. Access control")
    class AccessControl {

        /** Create, Edit, Delete and Record History in one role's session, each asserted to complete. */
        private void assertEveryAction(Page rolePage, String role, String title) {
            deleteAfterwards(title);
            NotificationListPage roleList = new NotificationListPage(rolePage);
            openList(roleList);
            assertTrue(roleList.isShowing(), "The " + role + " role should reach Tool > Notification");

            NotificationFormPage form = roleList.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            form.overrideToAll();
            form.setSchedule(SCHEDULE_DAILY);
            form.setTimer(TIMER_MIDNIGHT);
            form.setStatus(STATUS_INACTIVE);   // Inactive: an access check must not message real members
            form.target(AGENT_GROUP, TARGET_AGENT_GROUP);
            form.target(MEMBER_GROUP, TARGET_MEMBER_GROUP);
            form.clickSave();
            openList(roleList);
            assertTrue(roleList.isListed(title), "Create should complete for the " + role + " role (" + form.lastMessage() + ")");

            NotificationFormPage edit = roleList.clickEdit(title);
            edit.setTimer(TIMER_9AM);
            edit.clickSave();
            openList(roleList);
            assertEquals(TIMER_9AM, roleList.notification(title).timer(), "Edit should save for the " + role + " role");

            RecordHistoryPage history = roleList.openRecordHistory();
            assertTrue(history.isShowing(), "Record History should open for the " + role + " role");

            openList(roleList);
            roleList.clickDelete(title);
            roleList.confirmDelete();
            openList(roleList);
            assertFalse(roleList.isListed(title), "Delete should complete for the " + role + " role");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-ACC-001: Super Admin can Create, Edit, Delete notifications and view Record History")
        void superAdminHasEveryAction() {
            assertEveryAction(sessionPage, "Super Admin", PREFIX + "Access Check");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-ACC-002: Admin has full parity with Super Admin for Notification management")
        void adminRoleParity() {
            // AQ-14 (answered 2026-09-26): Admin has the same rights, so it runs exactly the same actions.
            Page adminPage;
            if (ADMIN_USERNAME.equalsIgnoreCase(USERNAME)) {
                CaseLog.evidence("test.admin.username is the operator account itself (" + USERNAME + "), so this row ran in the operator "
                        + "session and proves the account, not the Admin role. Configure a distinct Admin account in Config/.");
                adminPage = sessionPage;
            } else {
                adminPage = signedInAs("Admin", ADMIN_USERNAME, ADMIN_PASSWORD, "test.admin");
            }
            assertEveryAction(adminPage, "Admin", PREFIX + "Admin Access Check");
        }

        @Test
        @DisplayName("NOT-ACC-003: Agent role cannot access Notification management")
        void agentIsKeptOut() {
            Page agent = signedInAs("Agent", AGENT_USERNAME, AGENT_PASSWORD, "test.agent");
            assertOutOfReach(agent, "Agent", List.of(BasePage.url(AdminShellPage.NOTIFICATION_HREF.substring(1))));
        }

        @Test
        @DisplayName("NOT-ACC-004: A client-side member account cannot access admin Notification management")
        void memberIsKeptOut() {
            // A member account is not a back-office account: the back office refuses qa000092 at sign-in
            // (measured 2026-09-25 for Other Category). That refusal is this row's pass - the member never
            // gets a session that could reach the screen - and the words are recorded.
            Page user;
            try {
                user = signedInAs("ordinary member", USER_USERNAME, USER_PASSWORD, "test.user");
            } catch (IllegalStateException refused) {
                CaseLog.data("Member (" + USER_USERNAME + ") at back-office sign-in", firstLine(refused.getMessage()));
                assertTrue(refused.getMessage().contains("refused the sign-in"),
                        "The member should be refused a back-office session outright; instead: " + refused.getMessage());
                return;
            }
            assertOutOfReach(user, "ordinary member", List.of(BasePage.url(AdminShellPage.NOTIFICATION_HREF.substring(1))));
        }

        @Test
        @DisplayName("NOT-ACC-005: Direct deep-link to Add/Edit Notification URLs without the proper role")
        void deepLinksAreRefused() {
            List<String> addresses = new ArrayList<>();
            addresses.add(BasePage.url(AdminShellPage.NOTIFICATION_HREF.substring(1)));
            list.clickAdd();
            if (!addresses.contains(sessionPage.url())) {
                addresses.add(sessionPage.url());
            }
            CaseLog.data("Deep links under test", String.join(", ", addresses));

            Page guest = page();
            for (String address : addresses) {
                guest.navigate(address);
                BasePage.awaitReady(guest);
                assertFalse(new NotificationListPage(guest).isShowing() || new NotificationFormPage(guest).isOpen(),
                        "A signed-out visit to " + address + " should not render the screen, but it did at " + guest.url());
            }
            assertOutOfReach(signedInAs("Agent", AGENT_USERNAME, AGENT_PASSWORD, "test.agent"), "Agent (deep link)", addresses);
        }
    }

    // =====================================================================
    // 2. CREATE NOTIFICATION
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Create notification")
    class CreateNotification {

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-001: Create succeeds with Title, Content, and Link filled, a valid Timer, and at least one target group")
        void createDailyNotification() {
            list.deleteIfListed(DAILY_TITLE);   // recreated, so this row always drives Create itself
            openList();
            assertTrue(create(daily()), "A complete Daily notification should be created and listed");
            NotificationRow row = list.notification(DAILY_TITLE);
            assertEquals(SCHEDULE_DAILY, row.schedule(), "The list should show the saved Schedule");
            assertEquals(TIMER_MIDNIGHT, row.timer(), "The list should show the saved Timer");
            assertEquals(TYPE_SYSTEM, row.type(), "The list should show Type System");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-002: Leaving Title empty on the active language tab blocks Save")
        void titleIsRequired() {
            String title = PREFIX + "No Title";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, "", CONTENT, LINK);
            form.overrideToAll();
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertTrue(form.isOpen(), "Save should be blocked while Title is empty (" + form.messages() + ")");
            assertFalse(form.messages().isBlank(), "A required-field message should be shown for Title");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-003: Leaving Content empty on the active language tab blocks Save")
        void contentIsRequired() {
            String title = PREFIX + "No Content";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, "", LINK);
            form.overrideToAll();
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertRefused(form, title, "while Content is empty");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-004: Leaving Link empty on the active language tab still saves - Link is optional")
        void linkIsOptional() {
            // Si, 2026-09-28: expect what the system does now. The shipped form saves with Link empty
            // ("Successfully added", measured 2026-09-28), so Link is optional - the workbook's "blocks
            // Save" is superseded. Saved Inactive: an Active Once rule with Timer "—" is sent at once.
            String title = PREFIX + "No Link";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, "");
            form.overrideToAll();
            form.setStatus(STATUS_INACTIVE);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertSaved(form, title, "with Link empty");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-005: Entering a malformed URL into Link is saved as entered - Link is not validated")
        void malformedLinkIsAccepted() {
            // Si, 2026-09-28: expect what the system does now. AQ-05 (2026-09-26) said Link is
            // validated as a well-formed URL, but the shipped form saves "not a url" ("Successfully
            // added", measured 2026-09-28) and keeps it as typed. Saved Inactive, as NOT-CRT-004 is.
            String title = PREFIX + "Bad Link";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, INVALID_LINK);
            form.overrideToAll();
            form.setStatus(STATUS_INACTIVE);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertSaved(form, title, "with Link = \"" + INVALID_LINK + "\"");

            NotificationFormPage edit = list.clickEdit(title);
            String saved = edit.link(TAB_CHINESE);
            CaseLog.data("Link as saved", "\"" + saved + "\"");
            assertEquals(INVALID_LINK, saved, "The Link should be kept exactly as it was entered");
            edit.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-006: Saving with only a non-mandatory language filled (e.g. 日語), leaving the required English, 简体, and 繁體 tabs blank")
        void aNonMandatoryLanguageAloneIsNotEnough() {
            String title = PREFIX + "Japanese Only";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_JAPANESE, title, CONTENT, LINK);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertRefused(form, title, "with only 日語 filled (AQ-02: English, 简体 and 繁體 are required)");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-007: Filling only English, 简体 and 繁體 is refused with \"Title and Content are required for all languages\" - every language needs content or Override")
        void theThreeLanguagesAloneAreRefused() {
            // Si, 2026-10-01: expect what the system does - the Feature Report's 4.1, every language required. This reverses
            // the 2026-09-28 ruling that English, 简体 and 繁體 alone are mandatory, under which the row stayed red and GD-103 was
            // filed: the site has answered "Title and Content are required for all languages" on every run since 2026-09-28
            // (last 2026-09-30 17:33). NOT-CRT-006 and NOT-CRT-008 hold under either rule, and fill() satisfies this one with
            // Override to All languages.
            String title = PREFIX + "Three Languages";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            for (String language : REQUIRED_LANGUAGES) {
                form.fillLanguage(language, title, CONTENT, LINK);
            }
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            String said = form.messages();
            assertRefused(form, title, "with only English, 简体 and 繁體 filled - the site requires Title and Content in every language");
            assertTrue(said.contains(ALL_LANGUAGES_REQUIRED),
                    "The refusal should say \"" + ALL_LANGUAGES_REQUIRED + "\", but the form said \"" + said + "\"");
        }


        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-008: Filling only two of the three required languages (English, 简体) while leaving the third (繁體) blank still blocks Save")
        void everyRequiredLanguageIsRequired() {
            String title = PREFIX + "Two Languages";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_ENGLISH, title, CONTENT, LINK);
            form.fillLanguage(TAB_SIMPLIFIED, title, CONTENT, LINK);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertRefused(form, title, "while 繁體 is blank");
        }

        @Test
        @DisplayName("NOT-CRT-009: Entering content on one tab and switching away does not lose it")
        void switchingTabsKeepsContent() {
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_ENGLISH, "English title", "English content", LINK);
            form.fillLanguage(TAB_SPAIN, "Título en español", "Contenido en español", LINK);
            assertEquals("English title", form.title(TAB_ENGLISH), "The English Title should survive switching to Spain and back");
            assertEquals("English content", form.content(TAB_ENGLISH), "The English Content should survive switching to Spain and back");
            assertEquals("Contenido en español", form.content(TAB_SPAIN), "Spain should keep its own content");
            form.cancel();
        }

        @Test
        @DisplayName("NOT-CRT-010: Checking one specific language under Override copies the active tab's Title/Content to that language")
        void overrideToOneLanguage() {
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, DAILY_TITLE, CONTENT, LINK);
            form.overrideTo(TAB_ENGLISH);
            assertEquals(DAILY_TITLE, form.title(TAB_ENGLISH), "English should now show the 繁體 Title");
            assertEquals(CONTENT, form.content(TAB_ENGLISH), "English should now show the 繁體 Content");
            form.cancel();
        }

        @Test
        @DisplayName("NOT-CRT-011: Checking \"All languages\" propagates the active tab's content to all 12 tabs in one action")
        void overrideToAllLanguages() {
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, DAILY_TITLE, CONTENT, LINK);
            form.overrideToAll();
            for (String language : List.of(TAB_ENGLISH, TAB_SPAIN, TAB_BENGALI)) {
                assertEquals(DAILY_TITLE, form.title(language), language + " should show the 繁體 Title after Override to All languages");
                assertEquals(CONTENT, form.content(language), language + " should show the 繁體 Content after Override to All languages");
            }
            form.cancel();
        }

        @Test
        @DisplayName("NOT-CRT-012: Editing one overridden language's content afterward does not revert or affect the others")
        void customisingAfterOverride() {
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, DAILY_TITLE, CONTENT, LINK);
            form.overrideToAll();
            form.setContent(TAB_SPAIN, "Contenido personalizado");
            assertEquals("Contenido personalizado", form.content(TAB_SPAIN), "Spain should show its customized content");
            assertEquals(CONTENT, form.content(TAB_ENGLISH), "English should keep the overridden content, unaffected by Spain's edit");
            form.cancel();
        }

        @Test
        @DisplayName("NOT-CRT-013: The copied content appears on the target language tab(s) right away, not only after saving")
        void overrideIsImmediate() {
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, DAILY_TITLE, CONTENT, LINK);
            form.overrideTo(TAB_ENGLISH);
            // No Save: the copy must already be on the tab.
            assertEquals(CONTENT, form.content(TAB_ENGLISH), "Override should copy into English before Save is clicked");
            form.cancel();
        }

        // Si, 2026-09-28 - the requirement, superseding the workbook's "clears or disables the Timer":
        // a Once notification has two forms. (1) Once with a Timer hour is sent once, at that hour.
        // (2) Once with Timer "—" is sent immediately, whenever its Status is made Active. So under
        // Once the Timer stays available and offers "—" beside the 24 hours.
        @Test
        @DisplayName("NOT-CRT-014: Schedule Once keeps the Timer - \"—\" sends immediately on activation, an hour sends once at that hour")
        void onceDisablesTimer() {
            NotificationFormPage form = list.clickAdd();
            form.setSchedule(SCHEDULE_ONCE);
            List<String> options = form.timerOptions();
            CaseLog.data("Timer under Once", "\"" + form.timer() + "\", enabled=" + form.isTimerEnabled() + ", offers " + options);
            assertTrue(form.isTimerEnabled(), "Under Once the Timer should stay available, to choose between \"—\" and an hour");
            assertTrue(options.contains(TIMER_SEND_IMMEDIATELY), "Under Once the Timer should offer \"—\" (send immediately on activation); it offers " + options);
            assertTrue(options.contains(TIMER_8PM), "Under Once the Timer should offer the hours (send once at that hour); it offers " + options);

            // (1) Once at a set hour.
            form.setTimer(TIMER_8PM);
            assertEquals(SCHEDULE_ONCE, form.schedule(), "Choosing an hour should keep the Schedule at Once");
            assertEquals(TIMER_8PM, form.timer(), "Once should take a Timer hour - it is sent once at " + TIMER_8PM);

            // (2) Once, immediately.
            form.setTimer(TIMER_SEND_IMMEDIATELY);
            assertEquals(SCHEDULE_ONCE, form.schedule(), "Choosing \"—\" should keep the Schedule at Once");
            assertEquals(TIMER_SEND_IMMEDIATELY, form.timer(), "Once should take \"—\" - it is sent immediately on activation");
            form.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-015: Timer accepts a time-of-day value when a recurring Schedule is selected")
        void recurringSchedulesTakeATimer() {
            deleteAfterwards(DAILY_TITLE + " T");
            deleteAfterwards(WEEKEND_TITLE + " T");
            Draft dailyAt = new Draft(DAILY_TITLE + " T").schedule(SCHEDULE_DAILY, TIMER_MIDNIGHT);
            Draft weeklyAt = new Draft(WEEKEND_TITLE + " T").schedule(SCHEDULE_WEEKLY, TIMER_8PM);
            assertTrue(create(dailyAt), "A Daily notification with Timer 12 AM should save");
            assertEquals(TIMER_MIDNIGHT, list.notification(dailyAt.title).timer(), "The Daily Timer should be saved as 12 AM");
            assertTrue(create(weeklyAt), "A Weekly notification with Timer 08 PM should save");
            // Si, 2026-09-28: as the list shows it now - a Weekly row's Timer column reads
            // "<Week day>/<hour>" ("Sunday/08 PM", measured 2026-09-28), not the hour alone.
            assertEquals(weeklyTimer(weeklyAt.weekDay, TIMER_8PM), list.notification(weeklyAt.title).timer(),
                    "The Weekly Timer should be listed as its week day and hour");
        }

        @Test
        @DisplayName("NOT-CRT-016: Selecting Weekly adds a Week day selector alongside Timer")
        void weeklyRevealsWeekDay() {
            NotificationFormPage form = list.clickAdd();
            form.setSchedule(SCHEDULE_DAILY);
            assertFalse(form.isWeekDayShown(), "Week day should not be shown for Daily");
            form.setSchedule(SCHEDULE_WEEKLY);
            assertTrue(form.isWeekDayShown(), "Selecting Weekly should reveal a Week day select");
            assertTrue(form.isTimerEnabled(), "Timer should stay available alongside Week day");
            form.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-017: Attempting to save a Weekly notification without selecting a Week day")
        void weeklyNeedsAWeekDay() {
            // AQ-17 (answered 2026-09-26): Week day is required, and it is a single select over the 7 days.
            String title = PREFIX + "Weekly No Day";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            form.overrideToAll();
            form.setSchedule(SCHEDULE_WEEKLY);
            assertFalse(form.isWeekDayMultiSelect(), "Week day should be a single select, not a multi-select");
            List<String> days = form.options("Week day");
            CaseLog.data("Week day options", days.toString());
            assertTrue(days.containsAll(WEEK_DAYS), "Week day should offer all seven days; it offers " + days);
            if (!form.weekDay().isBlank() && WEEK_DAYS.contains(form.weekDay())) {
                CaseLog.evidence("Week day defaulted to " + form.weekDay() + ", so it cannot be left unselected - the form cannot be saved without one.");
                form.cancel();
                return;
            }
            form.setTimer(TIMER_8PM);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertRefused(form, title, "for a Weekly notification with no Week day");
        }

        @Test
        @DisplayName("NOT-CRT-018: Timer is confirmed to use hour-level granularity (e.g. 12 AM, 1 AM ... 11 PM), with no minute-level precision available")
        void timerIsWholeHours() {
            NotificationFormPage form = list.clickAdd();
            form.setSchedule(SCHEDULE_DAILY);
            // The shipped select opens with a "—" placeholder (an em dash, measured 2026-09-28), which is
            // not an hour: any option that is only dashes or blank is skipped before counting.
            List<String> options = form.timerOptions().stream()
                    .filter(o -> !o.replaceAll("[\\s\\-–—]", "").isEmpty()).toList();
            CaseLog.data("Timer options", options.toString());
            assertEquals(TIMER_OPTION_COUNT, options.size(), "Timer should offer the 24 whole hours; it offers " + options);
            for (String option : options) {
                assertTrue(option.matches("\\d{2} (AM|PM)"), "Every Timer option should be a whole hour like \"08 PM\"; found \"" + option + "\"");
            }
            form.cancel();
        }

        @Test
        @DisplayName("NOT-CRT-019: Switching Schedule from Weekly to Once or Daily removes the Week day field")
        void leavingWeeklyHidesWeekDay() {
            NotificationFormPage form = list.clickAdd();
            form.setSchedule(SCHEDULE_WEEKLY);
            form.setWeekDay(SUNDAY);
            form.setSchedule(SCHEDULE_DAILY);
            assertFalse(form.isWeekDayShown(), "Week day should disappear (or disable) when Schedule changes to Daily");
            form.cancel();
        }

        @Test
        @DisplayName("NOT-CRT-020: Clicking Select all on Agent Group moves every available Agent")
        void selectAllAgents() {
            NotificationFormPage form = list.clickAdd();
            form.chooseOptional(AGENT_GROUP);
            List<String> offered = form.availableNames(AGENT_GROUP);
            form.chooseSelectAll(AGENT_GROUP);
            List<String> selected = form.selectedNames(AGENT_GROUP);
            CaseLog.data("Agent Groups", "offered " + offered.size() + ", selected after Select all " + selected.size());
            assertTrue(form.isSelectAll(AGENT_GROUP), "Select all should be chosen");
            assertTrue(selected.containsAll(offered), "Every available Agent Group should move into Selected; missing "
                    + offered.stream().filter(o -> !selected.contains(o)).toList());
            // "Individual items can still be removed afterwards": measured 2026-09-28, switching the
            // radio back to Optional empties the Selected column, so the removal is proven in Optional
            // mode with the » control - every Agent Group moved across, then one moved back with ‹.
            if (!selected.isEmpty()) {
                form.moveAll(AGENT_GROUP);
                List<String> movedAll = form.selectedNames(AGENT_GROUP);
                CaseLog.data("After » in Optional", movedAll.size() + " Agent Group(s) selected");
                assertTrue(movedAll.containsAll(offered), "» should move every Agent Group into Selected");
                String removed = movedAll.get(0);
                form.removeOne(AGENT_GROUP, removed);
                assertFalse(form.selectedNames(AGENT_GROUP).contains(removed), "An individual item should still be removable afterwards");
            }
            form.cancel();
        }

        @Test
        @DisplayName("NOT-CRT-021: Using the single-arrow and clear-all controls on Member Group")
        void moveOneAndClearAllMembers() {
            NotificationFormPage form = list.clickAdd();
            form.chooseOptional(MEMBER_GROUP);
            form.removeAll(MEMBER_GROUP);
            List<String> offered = form.availableNames(MEMBER_GROUP);
            assertFalse(offered.isEmpty(), "The Member Group picker should offer at least one group");
            form.target(MEMBER_GROUP, offered.get(0));
            assertTrue(form.selectedNames(MEMBER_GROUP).contains(offered.get(0)), "The single arrow should move " + offered.get(0) + " into Selected");
            form.removeAll(MEMBER_GROUP);
            assertTrue(form.selectedNames(MEMBER_GROUP).isEmpty(), "Clear-all («) should empty the Selected column");
            form.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-022: Saving with both Selected columns empty is refused - at least one Agent Group and one Member Group are required")
        void savesWithNoTargetsIsRefused() {
            // Si, 2026-10-01: Save must be refused when both Selected columns are empty - the workbook's AQ-07 answer
            // (2026-09-26, at least one Agent Group and one Member Group are mandatory) is the expected result again.
            // This reverses the 2026-09-28 ruling to expect the shipped acceptance ("Successfully added", measured
            // that day), so the row is red until the validation is added - the both-empty side of GD-136, whose
            // one-side-empty case is NOT-GRP-012. The draft is Inactive so that, while the site still accepts it, a
            // rule with no targets can never fire before deleteAfterwards removes it.
            String title = PREFIX + "No Targets";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            form.overrideToAll();
            form.setSchedule(SCHEDULE_DAILY);
            form.setTimer(TIMER_MIDNIGHT);
            form.setStatus(STATUS_INACTIVE);
            form.chooseOptional(AGENT_GROUP);
            form.removeAll(AGENT_GROUP);
            form.chooseOptional(MEMBER_GROUP);
            form.removeAll(MEMBER_GROUP);
            assertTrue(form.selectedNames(AGENT_GROUP).isEmpty() && form.selectedNames(MEMBER_GROUP).isEmpty(),
                    "Both Selected columns should be empty before Save");
            CaseLog.data("Targets", "Agent Group Optional with nothing selected; Member Group Optional with nothing selected");
            form.clickSave();
            assertRefused(form, title, "with no Agent Group and no Member Group selected (AQ-07: at least one of each is mandatory)");
        }


        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-023: An Inactive notification is not sent, even once its schedule/Timer would otherwise fire")
        void inactiveIsNotSent() {
            // AQ-16 (answered 2026-09-26): Inactive notifications are not sent.
            Draft d = new Draft(VIP_TITLE).schedule(SCHEDULE_DAILY, nextHourLabel()).status(STATUS_INACTIVE);
            deleteAfterwards(d.title);
            list.deleteIfListed(d.title);
            assertTrue(create(d), "An Inactive notification should save");
            waitPastNextHour();
            assertTrue(historyToday().entriesFor(d.title).isEmpty(), "No Record History entry should exist for the Inactive notification");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-024: A Once notification's Status changes from Active to Inactive by itself once its single send completes")
        void onceEndsInactive() {
            String title = PREFIX + "Once Lifecycle";
            deleteAfterwards(title);
            assertTrue(create(new Draft(title).schedule(SCHEDULE_ONCE, null)), "A Once, Active notification should save");
            String status = "";
            for (int i = 0; i < 12 && !STATUS_INACTIVE.equals(status); i++) {
                sessionPage.waitForTimeout(10_000);
                openList();
                status = list.notification(title).status();
            }
            CaseLog.data("Status after firing", status);
            assertEquals(STATUS_INACTIVE, status, "The Once notification should turn Inactive by itself after it fires (waited 2 minutes)");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-025: Daily and Weekly notifications have no end-date/occurrence-limit field and never auto-transition to Inactive")
        void recurringNeverEnds() {
            // AQ-18 (answered 2026-09-26): only Once auto-ends; there is no end-date field.
            NotificationFormPage form = list.clickAdd();
            form.setSchedule(SCHEDULE_DAILY);
            String text = form.screenText().toLowerCase();
            assertFalse(text.contains("end date") || text.contains("occurrence") || text.contains("expire"),
                    "The Daily form should offer no end-date or occurrence-limit field");
            form.cancel();
            ensure(daily());
            assertEquals(STATUS_ACTIVE, list.notification(DAILY_TITLE).status(), "A Daily notification should stay Active");
        }

        @Test
        @DisplayName("NOT-CRT-026: Clicking Cancel during Add Notification does not save anything")
        void cancelDiscards() {
            String title = PREFIX + "Cancelled";
            int before = list.rowCount();
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            form.setSchedule(SCHEDULE_DAILY);
            form.cancel();
            openList();
            assertFalse(list.isListed(title), "Cancel should create nothing");
            assertEquals(before, list.rowCount(), "The list should be unchanged after Cancel");
        }

        // Added by Si, 2026-09-29: NOT-CRT-024 covers a Once rule with no Timer, which sends on activation. This
        // row covers the other kind - a Once rule with a Timer stays Active until its hour, is sent when the
        // Timer is reached, and then sets its own Status to Inactive.
        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-027: A Once notification with a Timer is sent when the Timer is reached and then sets itself Inactive")
        void onceAtTimerEndsInactive() {
            String title = PREFIX + "Once At Timer";
            String timer = nextHourLabel();
            deleteAfterwards(title);
            assertTrue(create(new Draft(title).schedule(SCHEDULE_ONCE, timer)), "A Once, Active notification with Timer " + timer + " should save");

            NotificationRow before = list.notification(title);
            CaseLog.data("Before the Timer", "Status " + before.status() + ", Schedule " + before.schedule() + ", Timer " + before.timer());
            assertEquals(STATUS_ACTIVE, before.status(), "Before its Timer the Once notification should still be Active");
            assertTrue(historyToday().entriesFor(title).isEmpty(), "Nothing should be sent before the Timer (" + timer + ") is reached");

            waitPastNextHour();

            List<HistoryRow> sent = historyToday().entriesFor(title);
            CaseLog.data("Record History after the Timer", sent.toString());
            assertEquals(1, sent.size(), "The Once notification should be sent exactly once when its Timer is reached");
            assertEquals(SENT, sent.get(0).status(), "The send at the Timer should be logged as Sent");

            String status = "";
            for (int i = 0; i < 12 && !STATUS_INACTIVE.equals(status); i++) {
                openList();
                status = list.notification(title).status();
                if (!STATUS_INACTIVE.equals(status)) {
                    sessionPage.waitForTimeout(10_000);
                }
            }
            CaseLog.data("Status after the send", status);
            assertEquals(STATUS_INACTIVE, status, "Once its send at the Timer completed, the notification should set itself Inactive (waited 2 minutes)");
        }

        // Added by Si, 2026-09-30: Override to All languages ticked and then unticked again must leave the other
        // languages as they were - blank - so Save is refused with the languages-required validation message,
        // exactly as if Override had never been ticked.
        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-028: Ticking Override to All languages and unticking it again leaves the other languages blank, so Save shows the languages-required validation message")
        void untickingOverrideLeavesLanguagesRequired() {
            String title = PREFIX + "Override Unticked";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            form.overrideToAll();
            assertTrue(form.isOverrideTicked("All languages"), "Override to All languages should be ticked");
            form.overrideAllOff();
            assertFalse(form.isOverrideTicked("All languages"), "Override to All languages should be unticked again");
            CaseLog.data("English after unticking", "title \"" + form.title(TAB_ENGLISH) + "\", content \"" + form.content(TAB_ENGLISH) + "\"");
            CaseLog.data("简体 after unticking", "title \"" + form.title(TAB_SIMPLIFIED) + "\", content \"" + form.content(TAB_SIMPLIFIED) + "\"");
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            String said = form.messages();
            assertRefused(form, title, "after Override to All languages was ticked and unticked again, leaving the other languages blank");
            assertTrue(said.toLowerCase(Locale.ROOT).contains("language") && said.toLowerCase(Locale.ROOT).contains("required"),
                    "The refusal should be the languages-required validation message, but the form said \"" + said + "\"");
        }

        // NOT-CRT-029 .. NOT-CRT-038: added 2026-09-30 from Si's review of what the Create section did not yet cover,
        // numbered on from NOT-CRT-028. None has a workbook row yet.

        @Test
        @DisplayName("NOT-CRT-029: Add Notification opens with Type System, Schedule Once, Timer — and Status Active - defaults on which a rule is sent the moment it is saved")
        void formDefaults() {
            // What the shipped form opens with (measured 2026-09-30; NOT-CRT-028 saved on these defaults and was sent at once).
            NotificationFormPage form = list.clickAdd();
            String defaults = "Type " + form.type() + ", Schedule " + form.schedule() + ", Timer " + form.timer() + ", Status " + form.status()
                    + ", Agent Group " + (form.isSelectAll(AGENT_GROUP) ? "Select all" : "Optional")
                    + ", Member group " + (form.isSelectAll(MEMBER_GROUP) ? "Select all" : "Optional");
            CaseLog.data("Defaults on open", defaults);
            assertEquals(TYPE_SYSTEM, form.type(), "Type should default to System");
            assertEquals(SCHEDULE_ONCE, form.schedule(), "Schedule should default to Once");
            assertEquals(TIMER_SEND_IMMEDIATELY, form.timer(), "Timer should default to — (send immediately on activation)");
            assertEquals(STATUS_ACTIVE, form.status(), "Status should default to Active");
            CaseLog.evidence("On these defaults a rule is sent to its targets the moment Save is confirmed; the form says so in the confirmation it asks (NOT-CRT-030).");
            form.cancel();
        }

        @Test
        @DisplayName("NOT-CRT-030: Declining the \"save and send now?\" confirmation of a Once rule with no Timer saves nothing and keeps the form open")
        void decliningTheSendNowPromptSavesNothing() {
            String title = PREFIX + "Declined Prompt";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            form.overrideToAll();
            form.setSchedule(SCHEDULE_ONCE);
            form.setTimer(TIMER_SEND_IMMEDIATELY);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            String asked = form.clickSaveAndDecline();
            assertFalse(asked.isEmpty(), "Save on a Once rule with no Timer should ask before it sends");
            assertTrue(form.isOpen(), "Declining the confirmation should leave the form open with its entries");
            assertEquals(title, form.title(TAB_CHINESE), "The entered Title should still be on the form");
            openList();
            assertFalse(list.isListed(title), "Nothing should be saved after the confirmation was declined");
            assertTrue(historyToday().entriesFor(title).isEmpty(), "Nothing should be sent after the confirmation was declined");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-031: A Title or Content of spaces only is refused like an empty one")
        void whitespaceOnlyIsRefused() {
            String title = PREFIX + "Spaces Only";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, "   ", LINK);
            form.overrideToAll();
            form.setStatus(STATUS_INACTIVE);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            String said = form.messages();
            CaseLog.data("Save with Content of spaces only", said.isEmpty() ? "(no message)" : said);
            assertTrue(form.isOpen(), "Save should be refused when Content is spaces only; the form closed (" + said + ")");
            assertFalse(said.isBlank(), "The refusal should say Content is required");

            form.setContent(TAB_CHINESE, CONTENT);
            form.setTitle(TAB_CHINESE, "   ");
            form.overrideToAll();
            form.clickSave();
            said = form.messages();
            CaseLog.data("Save with Title of spaces only", said.isEmpty() ? "(no message)" : said);
            if (!form.isOpen()) {
                CaseLog.evidence("A rule titled with spaces only was saved (Inactive) - it carries no \"" + PREFIX + "\" prefix, so remove it by hand.");
            }
            assertTrue(form.isOpen(), "Save should be refused when Title is spaces only; the form closed (" + said + ")");
            assertFalse(said.isBlank(), "The refusal should say Title is required");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-033: HTML and script tags typed into Title and Content save, are shown without the tags, and never run")
        void markupIsNotRendered() {
            // Si, 2026-10-01: expect what the system does. The 2026-09-30 17:33 run saved the markup ("Successfully added",
            // rule 288) and the list showed "QA NOT Tagged title" - the <b> tags gone, not kept as literal text - so the row
            // no longer expects the tags as plain text (a "<b>" in the list would now be the finding). What it still must
            // never see is the script running on the list or the form. Whether the tags are stripped on save or only on
            // display is read from the rule the API serves the Edit form, recorded, and allowed either way.
            String title = PREFIX + "<b>Tagged</b> title";
            String shownTitle = PREFIX + "Tagged title";   // measured 2026-09-30: the list's cell text once the tags are gone
            String content = "<script>document.title='pwned'</script><i>italic</i> " + CONTENT;
            deleteAfterwards(shownTitle);
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, content, LINK);
            form.overrideToAll();
            form.setSchedule(SCHEDULE_DAILY);
            form.setTimer(TIMER_MIDNIGHT);
            form.setStatus(STATUS_INACTIVE);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertSaved(form, shownTitle, "with markup in Title and Content");
            NotificationRow row = list.notification(shownTitle);
            CaseLog.data("Listed Title", "\"" + row.title() + "\"");
            assertEquals(shownTitle, row.title(), "The list should show the Title without its tags, as the system does");
            assertFalse(list.isListed(title), "The list should not show the tags as literal text");
            assertFalse("pwned".equals(sessionPage.title()), "The script in the Content must not have run on the list");
            NotificationFormPage edit = list.clickEdit(shownTitle);
            String servedTitle = edit.servedTitle(TAB_CHINESE);
            String servedContent = edit.servedContent(TAB_CHINESE);
            boolean strippedOnSave = shownTitle.equals(servedTitle);
            CaseLog.data("Served by the API", "Title \"" + servedTitle + "\", Content \"" + servedContent + "\"");
            CaseLog.data("Where the tags go", strippedOnSave ? "stripped on save - the stored Title has none"
                    : title.equals(servedTitle) ? "stored as typed, hidden on display" : "stored as something else (see Served by the API)");
            assertTrue(strippedOnSave || title.equals(servedTitle),
                    "The stored Title should be the typed one or the typed one without its tags, not \"" + servedTitle + "\"");
            assertFalse("pwned".equals(sessionPage.title()), "The script in the Content must not have run on the form");
            edit.cancel();
        }


        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-034: Emoji and mixed-script text in Title and Content survive Save and reload unchanged")
        void unicodeSurvives() {
            String title = PREFIX + "Emoji 🎉 中文 ไทย বাংলা";
            String content = "🎁 " + CONTENT + " — 中文 · ไทย · বাংলা · ✓";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, content, LINK);
            form.overrideToAll();
            form.setSchedule(SCHEDULE_DAILY);
            form.setTimer(TIMER_MIDNIGHT);
            form.setStatus(STATUS_INACTIVE);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            assertSaved(form, title, "with emoji and mixed-script text");
            assertNotNull(list.notification(title), "The list should show the Title with every character intact");
            NotificationFormPage edit = list.clickEdit(title);
            assertEquals(title, edit.servedTitle(TAB_CHINESE), "The Title should be stored with every character intact");
            assertEquals(content, edit.servedContent(TAB_CHINESE), "The Content should be stored with every character intact");
            edit.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-035: Ticking Override to one language and unticking it again leaves that language blank, so Save shows the languages-required validation message")
        void untickingOneLanguageOverrideLeavesItRequired() {
            // The single-language checkbox's version of NOT-CRT-028. 简体 stays overridden so that English alone decides the save.
            String title = PREFIX + "Override English Unticked";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            form.overrideTo(TAB_SIMPLIFIED);
            form.overrideTo(TAB_ENGLISH);
            assertTrue(form.isOverrideTicked(TAB_ENGLISH), "Override to English should be ticked");
            form.overrideOff(TAB_ENGLISH);
            assertFalse(form.isOverrideTicked(TAB_ENGLISH), "Override to English should be unticked again");
            CaseLog.data("English after unticking", "title \"" + form.title(TAB_ENGLISH) + "\", content \"" + form.content(TAB_ENGLISH) + "\"");
            CaseLog.data("简体 (still overridden)", "title \"" + form.title(TAB_SIMPLIFIED) + "\", content \"" + form.content(TAB_SIMPLIFIED) + "\"");
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            String said = form.messages();
            assertRefused(form, title, "after Override to English was ticked and unticked again, leaving English blank");
            assertTrue(said.toLowerCase(Locale.ROOT).contains("language") && said.toLowerCase(Locale.ROOT).contains("required"),
                    "The refusal should be the languages-required validation message, but the form said \"" + said + "\"");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-036: Override ticked while the 繁體 tab is still blank - the other languages either follow the text typed afterwards or stay blank and block Save, never blank-and-saved")
        void overrideFromABlankSource() {
            // A "confirm whether" row: is Override a one-shot copy (NOT-CRT-013) or a mode that keeps following 繁體?
            String title = PREFIX + "Override Blank Source";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.overrideToAll();
            CaseLog.data("English right after Override from a blank 繁體", "title \"" + form.title(TAB_ENGLISH) + "\", content \"" + form.content(TAB_ENGLISH) + "\"");
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            String englishTitle = form.title(TAB_ENGLISH);
            String englishContent = form.content(TAB_ENGLISH);
            CaseLog.data("English after 繁體 was filled", "title \"" + englishTitle + "\", content \"" + englishContent + "\"");
            form.setStatus(STATUS_INACTIVE);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            boolean saved = !form.isOpen();
            String said = saved ? form.lastMessage() : form.messages();
            CaseLog.data("Override from a blank source (open question)", (saved ? "saved - " : "refused - ") + said
                    + (englishTitle.isBlank() ? "; the other languages stayed blank" : "; the other languages followed the text typed afterwards"));
            if (saved) {
                openList();
                assertTrue(list.isListed(title), "A save that went through should list the rule");
                assertTrue(!englishTitle.isBlank() && !englishContent.isBlank(),
                        "A rule saved after Override from a blank 繁體 must carry the text on the other languages, not blanks");
            } else {
                assertTrue(englishTitle.isBlank() || englishContent.isBlank(),
                        "Save was refused although the other languages held the text: " + said);
                assertFalse(said.isBlank(), "A refusal should say the languages are required");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-037: A second rule with the identical Title is either listed beside the first or refused with a message, never silently dropped")
        void duplicateTitle() {
            String title = PREFIX + "Duplicate Title";
            deleteAfterwards(title);
            deleteAfterwards(title);   // both copies, when both save
            ensure(new Draft(title).status(STATUS_INACTIVE));
            NotificationFormPage form = fill(new Draft(title).status(STATUS_INACTIVE));
            form.clickSave();
            String said = form.lastMessage();
            openList();
            long listed = list.notifications().stream().filter(r -> r.title().equals(title)).count();
            CaseLog.data("Second save under the same Title", (said.isEmpty() ? "(no message)" : said) + "; " + listed + " rule(s) titled \"" + title + "\" listed");
            assertTrue(listed == 2 || (listed == 1 && !said.isBlank()),
                    "Either both rules are listed or the second is refused with a message; " + listed + " listed and the page said \"" + said + "\"");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CRT-038: A Once rule whose Timer hour is already past today is not sent immediately - it stays Active for that hour tomorrow")
        void onceAtAPastHourWaits() {
            String title = PREFIX + "Once Past Hour";
            String timer = pastHourLabel();
            deleteAfterwards(title);
            assertTrue(create(new Draft(title).schedule(SCHEDULE_ONCE, timer)), "A Once, Active notification with Timer " + timer + " should save");
            CaseLog.step("Waiting two minutes to see whether the past hour triggers a send.");
            sessionPage.waitForTimeout(120_000);
            openList();
            NotificationRow row = list.notification(title);
            List<HistoryRow> sent = historyToday().entriesFor(title);
            CaseLog.data("Two minutes after saving with Timer " + timer, "Status " + row.status() + ", Timer " + row.timer() + ", " + sent.size() + " Record History entries");
            assertTrue(sent.isEmpty(), "A Once rule whose Timer hour (" + timer + ") has passed today should not be sent immediately");
            assertEquals(STATUS_ACTIVE, row.status(), "It should stay Active, waiting for " + timer + " tomorrow");
            assertEquals(timer, row.timer(), "The list should keep the Timer it was saved with");
        }
    }

    // =====================================================================
    // 1. NOTIFICATION LIST
    // =====================================================================

    @Nested
    @Order(30)
    @DisplayName("1. Notification list")
    class NotificationList {

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-LST-001: List shows ID, Title, Type, Agent Group, Member Group, Schedule, Timer, Status, and Created At for every notification")
        void listShowsEveryColumn() {
            ensure(daily());
            ensure(new Draft(VIP_TITLE).schedule(SCHEDULE_ONCE, null).status(STATUS_INACTIVE));
            List<String> headers = list.columnHeaders();
            CaseLog.data("Columns", headers.toString());
            // Si, 2026-09-28: headers match regardless of case - the site heads "Member group".
            // An extra column (the site's Actions) is fine; only the nine listed must be there.
            List<String> lowered = headers.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
            assertTrue(LIST_COLUMNS.stream().allMatch(c -> lowered.contains(c.toLowerCase(Locale.ROOT))),"The list should head " + LIST_COLUMNS + "; it heads " + headers);
            NotificationRow row = list.notification(DAILY_TITLE);
            assertFalse(row.id().isBlank(), "Every row should carry an ID");
            assertEquals(TYPE_SYSTEM, row.type());
            assertTrue(row.agentGroup().contains(TARGET_AGENT_GROUP), "Agent Group should show the saved target");
            assertTrue(row.memberGroup().contains(TARGET_MEMBER_GROUP), "Member Group should show the saved target");
            assertEquals(SCHEDULE_DAILY, row.schedule());
            assertEquals(TIMER_MIDNIGHT, row.timer());
            assertEquals(STATUS_ACTIVE, row.status());
            assertTrue(row.createdAt().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}.*"), "Created At should read like 2026-01-24 10:00, not " + row.createdAt());
            assertEquals(TIMER_IMMEDIATE, list.notification(VIP_TITLE).timer(), "A Once row's Timer should read Immediate, as in the capture");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-LST-002: Status filter shows only Active or only Inactive notifications as selected")
        void statusFilter() {
            ensure(daily());
            ensure(new Draft(VIP_TITLE).schedule(SCHEDULE_ONCE, null).status(STATUS_INACTIVE));
            for (String status : List.of(STATUS_ACTIVE, STATUS_INACTIVE)) {
                list.filterStatus(status);
                List<NotificationRow> shown = list.notifications();
                assertFalse(shown.isEmpty(), "The " + status + " filter should show the suite's " + status + " notification");
                for (NotificationRow row : shown) {
                    assertEquals(status, row.status(), "The " + status + " filter should show only " + status + " rows; \"" + row.title() + "\" is " + row.status());
                }
            }
        }

        @Test
        @DisplayName("NOT-LST-003: The Type filter only ever shows System, since Deposit/Withdraw don't have admin-created rules under this screen")
        void typeFilterIsSystemOnly() {
            // AQ-01 (answered 2026-09-26): this screen manages System (Non-Event Trigger) rules only.
            List<String> options = list.typeFilterOptions();
            CaseLog.data("Type filter options", options.toString());
            list.filterType(TYPE_SYSTEM);
            for (NotificationRow row : list.notifications()) {
                assertEquals(TYPE_SYSTEM, row.type(), "Filtered to System, \"" + row.title() + "\" shows Type " + row.type());
            }
            for (String other : List.of("Deposit", "Withdraw")) {
                if (options.contains(other)) {
                    list.filterType(other);
                    assertEquals(0, list.rowCount(), "Filtering by " + other + " should show no rules on this screen");
                }
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-LST-004: Changing the list's language filter updates the displayed Title to that language's translation")
        void languageFilterSwitchesTitle() {
            String title = PREFIX + "Language Preview";
            String english = PREFIX + "Language Preview EN";
            deleteAfterwards(title);
            NotificationFormPage form = fill(new Draft(title));
            form.setTitle(TAB_ENGLISH, english);   // after Override, so English differs from 繁體
            form.clickSave();
            openList();
            list.filterLanguage(TAB_CHINESE);
            NotificationRow chinese = list.notification(title);
            assertNotNull(chinese, "Under 繁體 the list should show the 繁體 title");
            list.filterLanguage(TAB_ENGLISH);
            NotificationRow inEnglish = list.notification(english);
            assertNotNull(inEnglish, "Under English the same row should show its English title " + english);
            assertEquals(chinese.id(), inEnglish.id(), "It should be the same notification");
            assertEquals(chinese.schedule(), inEnglish.schedule(), "No other column should change with the language");
        }

        @Test
        @DisplayName("NOT-LST-005: Page-size selector controls how many notifications are listed at once")
        void pageSize() {
            List<Integer> sizes = list.pageSizeOptions();
            assertFalse(sizes.isEmpty(), "The list should offer a page-size selector");
            list.setPageSize(sizes.get(0));
            int[] showing = list.showing();
            CaseLog.data("Page size " + sizes.get(0), showing == null ? "no entry count" : showing[0] + ".." + showing[1] + " of " + showing[2]);
            assertTrue(list.rowCount() <= sizes.get(0), "No more than " + sizes.get(0) + " rows should show");
        }

        /**
         * Si, 2026-09-30: the paging rows show {@value NotificationTestData#LIST_PAGE_SIZE} entries per page, so the
         * 50 seeds make three pages. Aborts, naming what the selector offers, when 20 is not among them, and when
         * the list has only one page at that size.
         */
        private void showTwentyPerPage(String controls) {
            List<Integer> sizes = list.pageSizeOptions();
            if (!sizes.contains(LIST_PAGE_SIZE)) {
                abort("The page-size selector offers " + sizes + ", not " + LIST_PAGE_SIZE + ", so this row cannot show " + LIST_PAGE_SIZE + " per page.");
            }
            list.setPageSize(LIST_PAGE_SIZE);
            if (!list.hasNextPage()) {
                abort("Only one page of notifications at " + LIST_PAGE_SIZE + " per page, so " + controls + " have nowhere to go. "
                        + "The row needs more than " + LIST_PAGE_SIZE + " notifications on the list - run "
                        + "./mvnw test -Dsuite=NotificationSeedSuite once to seed 50 Inactive ones.");
            }
        }

        @Test
        @DisplayName("NOT-LST-006: Previous/Next controls navigate between pages of notifications")
        void pager() {
            showTwentyPerPage("Next and Prev");
            List<String> first = list.titles();
            assertTrue(first.size() <= LIST_PAGE_SIZE, "Page 1 should show no more than " + LIST_PAGE_SIZE + " rows");
            list.nextPage();
            int[] second = list.showing();
            assertFalse(first.equals(list.titles()), "Next should show a different page of notifications");
            assertNotNull(second, "The entry count should stay shown on page 2");
            assertEquals(LIST_PAGE_SIZE + 1, second[0], "Page 2 should start at entry " + (LIST_PAGE_SIZE + 1));
            list.previousPage();
            assertEquals(first, list.titles(), "Prev should come back to the first page");
        }

        @Test
        @DisplayName("NOT-LST-010: The First control returns to the first page of notifications from a later page")
        void firstPageControl() {
            // Si, 2026-09-30. Numbered on from NOT-LST-009, as Si asked - no workbook row yet. With 20 per page, go two pages in (or as far as the
            // list allows) and come back with First, not Prev - so it must land on page 1 in one click.
            showTwentyPerPage("First");
            assertTrue(list.hasPagerControl("First"), "The pager should offer a First control");
            List<String> first = list.titles();
            list.nextPage();
            if (list.hasNextPage()) {
                list.nextPage();
            }
            int[] away = list.showing();
            assertNotNull(away, "The entry count should stay shown after moving away from page 1");
            assertTrue(away[0] > 1, "The list should be on a later page before First is clicked; it shows " + away[0] + ".." + away[1]);
            CaseLog.data("Before First", away[0] + ".." + away[1] + " of " + away[2]);
            list.firstPage();
            int[] back = list.showing();
            assertNotNull(back, "The entry count should be shown on the first page");
            assertEquals(1, back[0], "First should land on page 1, starting at entry 1");
            assertEquals(first, list.titles(), "First should show the same rows page 1 showed");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-LST-007: Clicking the edit icon on a row opens that exact notification pre-filled")
        void editOpensThatNotification() {
            Draft d = weekly();
            ensure(d);
            NotificationFormPage form = list.clickEdit(d.title);
            assertEquals(d.title, form.title(TAB_CHINESE), "Edit should open with that notification's Title");
            assertEquals(CONTENT, form.content(TAB_CHINESE), "Edit should open with its Content");
            assertEquals(SCHEDULE_WEEKLY, form.schedule(), "Edit should open with its Schedule");
            assertEquals(SUNDAY, form.weekDay(), "Edit should open with its Week day");
            assertEquals(TIMER_8PM, form.timer(), "Edit should open with its Timer");
            form.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-LST-008: A broadcast notification's Agent Group and Member Group columns both read \"All\"")
        void broadcastReadsAll() {
            ensure(broadcast());
            NotificationRow row = list.notification(BROADCAST_TITLE);
            assertEquals(ALL, row.agentGroup(), "A broadcast's Agent Group column should read All");
            assertEquals(ALL, row.memberGroup(), "A broadcast's Member Group column should read All");
            assertFalse(ALL.equals(list.notification(ensuredDaily()).agentGroup()), "A targeted notification should name its groups, not All");
        }

        private String ensuredDaily() {
            ensure(daily());
            return DAILY_TITLE;
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-LST-009: The list shows the configured Week day alongside \"Weekly\", e.g. \"Weekly (Sunday)\"")
        void weeklyShowsItsDay() {
            // Si, 2026-09-28, superseding AQ-19's "Weekly (Sunday)": as the list shows it now - the
            // Schedule column reads "Weekly" and the week day is carried in the Timer column.
            Draft weekly = weekly();
            ensure(weekly);
            NotificationRow row = list.notification(WEEKEND_TITLE);
            assertEquals(SCHEDULE_WEEKLY, row.schedule(), "The Schedule column should read Weekly");
            assertEquals(weeklyTimer(weekly.weekDay, TIMER_8PM), row.timer(), "The Timer column should carry the week day: Sunday/08 PM");
        }

        // NOT-LST-011 .. NOT-LST-015: added 2026-09-30 from Si's review, numbered on from NOT-LST-010. No workbook rows yet.

        @Test
        @DisplayName("NOT-LST-011: The Last control jumps to the final page of notifications")
        void lastPageControl() {
            showTwentyPerPage("Last");
            assertTrue(list.hasPagerControl("Last"), "The pager should offer a Last control");
            list.lastPage();
            int[] last = list.showing();
            assertNotNull(last, "The entry count should be shown on the last page");
            CaseLog.data("After Last", last[0] + ".." + last[1] + " of " + last[2]);
            assertEquals(last[2], last[1], "Last should show the final entries, ending at the total");
            assertFalse(list.hasNextPage(), "There should be no page after Last");
        }

        @Test
        @DisplayName("NOT-LST-012: A filter that matches nothing shows an empty list with a zero count, not stale rows")
        void emptyState() {
            List<String> options = list.typeFilterOptions();
            String other = options.stream().filter(o -> !o.equalsIgnoreCase(TYPE_SYSTEM) && !o.toLowerCase(Locale.ROOT).contains("all")).findFirst().orElse(null);
            if (other == null) {
                abort("The Type filter offers only " + options + ", so there is no filter on this screen that matches nothing.");
            }
            int before = list.rowCount();
            list.filterType(other);
            int[] showing = list.showing();
            String placeholder = list.screenText().lines().map(String::trim)
                    .filter(l -> l.toLowerCase(Locale.ROOT).matches(".*(no data|no matching|no record|nothing|沒有|没有|无|無).*")).findFirst().orElse("");
            CaseLog.data("Filtered to " + other, list.rowCount() + " rows (" + before + " before), count " + (showing == null ? "not shown" : showing[2])
                    + ", placeholder \"" + placeholder + "\"");
            assertEquals(0, list.rowCount(), "Filtering by " + other + " should leave no rows");
            assertTrue(showing == null || showing[2] == 0, "The entry count should read 0 when nothing matches");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-LST-013: Status, Type and Language filters combine, and every row shown satisfies all three")
        void filtersCombine() {
            ensure(daily());
            ensure(new Draft(VIP_TITLE).schedule(SCHEDULE_ONCE, null).status(STATUS_INACTIVE));
            list.filterStatus(STATUS_INACTIVE);
            list.filterType(TYPE_SYSTEM);
            list.filterLanguage(TAB_ENGLISH);
            List<NotificationRow> rows = list.notifications();
            int[] showing = list.showing();
            CaseLog.data("Inactive + System + English", rows.size() + " rows, count " + (showing == null ? "not shown" : showing[2]));
            assertFalse(rows.isEmpty(), "The combined filters should still show the Inactive System rule " + VIP_TITLE);
            for (NotificationRow row : rows) {
                assertEquals(STATUS_INACTIVE, row.status(), "\"" + row.title() + "\" should be Inactive");
                assertEquals(TYPE_SYSTEM, row.type(), "\"" + row.title() + "\" should be System");
            }
            assertTrue(rows.stream().anyMatch(r -> r.title().equals(VIP_TITLE)), "The Inactive rule should be listed under its English title (a copy of 繁體)");
            assertFalse(rows.stream().anyMatch(r -> r.title().equals(DAILY_TITLE)), "The Active Daily rule should be filtered out");
            assertTrue(showing == null || showing[2] >= rows.size(), "The count should cover the rows shown");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-LST-014: Returning from Edit with Cancel keeps the list's Status and Language filters")
        void cancelFromEditKeepsFilters() {
            ensure(new Draft(VIP_TITLE).schedule(SCHEDULE_ONCE, null).status(STATUS_INACTIVE));
            list.filterStatus(STATUS_INACTIVE);
            list.filterLanguage(TAB_ENGLISH);
            NotificationFormPage edit = list.clickEdit(VIP_TITLE);
            edit.cancel();
            String status = list.statusFilter();
            String language = list.languageFilter();
            CaseLog.data("Filters after Cancel", "Status \"" + status + "\", Language \"" + language + "\"");
            assertEquals(STATUS_INACTIVE, status, "The Status filter should still be applied after Cancel");
            assertEquals(TAB_ENGLISH, language, "The Language filter should still be applied after Cancel");
        }

        @Test
        @DisplayName("NOT-LST-015: Notifications are listed newest first, by ID")
        void newestFirst() {
            List<NotificationRow> rows = list.notifications();
            if (rows.size() < 2) {
                abort("The list holds " + rows.size() + " rule(s); an order needs at least two.");
            }
            List<Integer> ids = rows.stream().map(r -> r.id().replaceAll("\\D", "")).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
            CaseLog.data("IDs top to bottom", ids.size() > 8 ? ids.subList(0, 8) + " ..." : ids.toString());
            for (int i = 1; i < ids.size(); i++) {
                assertTrue(ids.get(i - 1) > ids.get(i), "Row " + (i + 1) + " (ID " + ids.get(i) + ") should come after ID " + ids.get(i - 1) + " - newest first");
            }
        }
    }

    // =====================================================================
    // 6. TARGET DELIVERY GROUPS
    // =====================================================================

    @Nested
    @Order(40)
    @DisplayName("6. Target delivery groups")
    class TargetGroups {

        @Test
        @DisplayName("NOT-GRP-001: Newly created Agent Groups elsewhere appear here without a workaround")
        void newAgentGroupAppears() {
            abort("Needs an Agent Group created in Agent management during the run, and this suite does not create groups: an Agent "
                    + "Group is shared configuration other features target (Rebate pairs on them). Create one by hand, then open Add "
                    + "Notification in the same session and check it is offered.");
        }

        @Test
        @DisplayName("NOT-GRP-002: Newly created Member Groups elsewhere appear here without a workaround")
        void newMemberGroupAppears() {
            abort("Needs a Member Group created in Member Management during the run, for the same reason as NOT-GRP-001.");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-GRP-003: Confirming how a notification is configured to reach every Agent/Member Group")
        void broadcastMechanism() {
            // Decided by the System Notification Feature Report (section 6; 2026-09-28): Select All removes
            // the restriction for its side, and Select All on both sides reaches all eligible users. The
            // mechanism is recorded; the saved rule must read All.
            deleteAfterwards(BROADCAST_TITLE);
            list.deleteIfListed(BROADCAST_TITLE);
            NotificationFormPage form = fill(broadcast());
            boolean allIsAnOption = form.availableNames(AGENT_GROUP).contains(ALL) || form.selectedNames(AGENT_GROUP).contains(ALL);
            CaseLog.data("Broadcast mechanism", allIsAnOption ? "an explicit \"All\" entry" : "the Select all radio");
            form.clickSave();
            openList();
            assertTrue(list.isListed(BROADCAST_TITLE), "A broadcast notification should save (" + form.lastMessage() + ")");
            assertEquals(ALL, list.notification(BROADCAST_TITLE).agentGroup(), "Whatever the mechanism, the saved rule should target All");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-GRP-004: Two notifications targeting the identical Agent+Member Group pairing both save without any conflict error")
        void overlappingTargetsAreAllowed() {
            // AQ-15 (answered 2026-09-26): targeting is additive, not exclusive like Rebate.
            ensure(daily());
            String second = PREFIX + "Same Pairing";
            deleteAfterwards(second);
            assertTrue(create(new Draft(second)), "A second notification on the same pairing should save without a conflict");
            assertTrue(list.isListed(DAILY_TITLE), "The first notification should still be there");
        }

        @Test
        @DisplayName("NOT-GRP-005: Select all moves every available Agent Group into Selected")
        void selectAllAgentGroups() {
            assertSelectAllMovesEverything(AGENT_GROUP, "Agent Group");
        }

        @Test
        @DisplayName("NOT-GRP-006: Select all moves every available Member Group into Selected")
        void selectAllMemberGroups() {
            assertSelectAllMovesEverything(MEMBER_GROUP, "Member Group");
        }

        private void assertSelectAllMovesEverything(int group, String name) {
            NotificationFormPage form = list.clickAdd();
            form.chooseOptional(group);
            List<String> everything = new ArrayList<>(form.availableNames(group));
            everything.addAll(form.selectedNames(group));
            form.chooseSelectAll(group);
            List<String> selected = form.selectedNames(group);
            assertTrue(selected.containsAll(everything), "Select all should put every " + name + " in Selected; missing "
                    + everything.stream().filter(e -> !selected.contains(e)).toList());
            form.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-GRP-007: The single left-arrow moves one item back to Available")
        void removeOneGroup() {
            ensure(new Draft(PREFIX + "Two Members").members(twoMemberGroups()));
            deleteAfterwards(PREFIX + "Two Members");
            NotificationFormPage form = list.clickEdit(PREFIX + "Two Members");
            List<String> before = form.selectedNames(MEMBER_GROUP);
            assertTrue(before.size() >= 2, "The rule should open with two Member Groups selected; it has " + before);
            form.chooseOptional(MEMBER_GROUP);
            form.removeOne(MEMBER_GROUP, before.get(0));
            assertTrue(form.availableNames(MEMBER_GROUP).contains(before.get(0)), before.get(0) + " should be back in Available");
            assertTrue(form.selectedNames(MEMBER_GROUP).contains(before.get(1)), "The other selection should be unaffected");
            form.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-GRP-008: The double left arrow («) empties the entire Selected column at once")
        void clearAllAgentGroups() {
            String title = PREFIX + "Three Agents";
            deleteAfterwards(title);
            ensure(new Draft(title).agents(someGroups(AGENT_GROUP, 3)));
            NotificationFormPage form = list.clickEdit(title);
            assertTrue(form.selectedNames(AGENT_GROUP).size() >= 2, "The rule should open with several Agent Groups selected");
            form.chooseOptional(AGENT_GROUP);
            form.removeAll(AGENT_GROUP);
            assertTrue(form.selectedNames(AGENT_GROUP).isEmpty(), "« should return every selected Agent Group in one action");
            form.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-GRP-009: Selecting a single Agent Group alongside several Member Groups saves successfully (1-to-many)")
        void oneAgentManyMembers() {
            String title = PREFIX + "One To Many";
            deleteAfterwards(title);
            assertTrue(create(new Draft(title).members(someGroups(MEMBER_GROUP, 3))), "1 Agent Group + 3 Member Groups should save");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-GRP-010: Selecting several Agent Groups alongside one Member Group saves successfully (many-to-1)")
        void manyAgentsOneMember() {
            String title = PREFIX + "Many To One";
            deleteAfterwards(title);
            assertTrue(create(new Draft(title).agents(someGroups(AGENT_GROUP, 3))), "3 Agent Groups + 1 Member Group should save");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-GRP-011: A true many-to-many combination (multiple Agent Groups and multiple Member Groups at once) saves successfully")
        void manyToMany() {
            // AQ-20 (answered 2026-09-26): any combination saves.
            String title = PREFIX + "Many To Many";
            deleteAfterwards(title);
            assertTrue(create(new Draft(title).agents(someGroups(AGENT_GROUP, 2)).members(someGroups(MEMBER_GROUP, 2))),
                    "2 Agent Groups + 2 Member Groups should save");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-GRP-012: Optional chosen on Agent Group with nothing moved to Selected is refused at Save")
        void optionalAgentGroupWithNothingSelectedIsRefused() {
            // Si, 2026-09-30. Numbered on from NOT-GRP-011, as Si asked - no workbook row yet. Every required field filled and the Member Group targeted as usual,
            // but the Agent Group left on Optional with an empty Selected column: Save must be refused. The draft
            // is Inactive so that, should the site accept it after all, a rule with no agents can never fire
            // before deleteAfterwards removes it - which is also how the row reports that acceptance.
            String title = PREFIX + "Optional No Agent";
            deleteAfterwards(title);
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, CONTENT, LINK);
            form.overrideToAll();
            form.setType(TYPE_SYSTEM);
            form.setSchedule(SCHEDULE_DAILY);
            form.setTimer(TIMER_MIDNIGHT);
            form.setStatus(STATUS_INACTIVE);
            form.chooseOptional(AGENT_GROUP);
            form.removeAll(AGENT_GROUP);
            assertTrue(form.selectedNames(AGENT_GROUP).isEmpty(), "The Agent Group's Selected column should be empty before Save");
            CaseLog.data("Agent Group", "Optional, nothing selected");
            form.target(MEMBER_GROUP, TARGET_MEMBER_GROUP);
            form.clickSave();
            assertRefused(form, title, "when Optional is chosen on Agent Group with nothing moved to Selected");
        }
    }

    /** The configured group first, then whatever else the picker offers, up to {@code count}. */
    private static List<String> someGroups(int group, int count) {
        String configured = group == AGENT_GROUP ? TARGET_AGENT_GROUP : TARGET_MEMBER_GROUP;
        openList();   // the caller may be on Record History, where there is no Add button (NOT-RHD-003 timed out there, 2026-09-30)
        NotificationFormPage form = list.clickAdd();
        form.chooseOptional(group);
        List<String> offered = new ArrayList<>(form.availableNames(group));
        offered.addAll(form.selectedNames(group));
        form.cancel();
        openList();
        List<String> chosen = new ArrayList<>(List.of(configured));
        for (String name : offered) {
            if (chosen.size() >= count) {
                break;
            }
            if (!chosen.contains(name)) {
                chosen.add(name);
            }
        }
        if (chosen.size() < count) {
            abort("The picker offers only " + offered.size() + " groups; this row needs " + count + ".");
        }
        CaseLog.data("Groups used", chosen.toString());
        return chosen;
    }

    private static List<String> twoMemberGroups() {
        return someGroups(MEMBER_GROUP, 2);
    }

    // =====================================================================
    // 3. EDIT NOTIFICATION
    // =====================================================================

    @Nested
    @Order(50)
    @DisplayName("3. Edit notification")
    class EditNotification {

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-001: Edit loads every saved field correctly, including all 12 language tabs")
        void editIsPrefilled() {
            String title = PREFIX + "Prefill Check";
            deleteAfterwards(title);
            NotificationFormPage form = fill(new Draft(title));
            Map<String, String> perLanguage = new HashMap<>();
            for (String language : LANGUAGE_TABS) {
                String content = CONTENT + " [" + language + "]";
                form.setContent(language, content);
                perLanguage.put(language, content);
            }
            form.clickSave();
            openList();
            NotificationFormPage edit = list.clickEdit(title);
            for (String language : LANGUAGE_TABS) {
                assertEquals(perLanguage.get(language), edit.content(language), "Edit should load the " + language + " content as saved");
            }
            assertEquals(TYPE_SYSTEM, edit.type());
            assertEquals(SCHEDULE_DAILY, edit.schedule());
            assertEquals(TIMER_MIDNIGHT, edit.timer());
            assertEquals(STATUS_ACTIVE, edit.status());
            assertTrue(edit.selectedNames(AGENT_GROUP).contains(TARGET_AGENT_GROUP), "Edit should load the Agent Group target");
            assertTrue(edit.selectedNames(MEMBER_GROUP).contains(TARGET_MEMBER_GROUP), "Edit should load the Member Group target");
            edit.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-002: Changing only the English content leaves the other 11 languages untouched")
        void editOneLanguage() {
            String title = PREFIX + "Edit English";
            deleteAfterwards(title);
            ensure(new Draft(title));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setContent(TAB_ENGLISH, "Updated English content");
            edit.clickSave();
            openList();
            NotificationFormPage again = list.clickEdit(title);
            // Decided from the rule the site serves the reopened form (the GET clickEdit captures), which is what the
            // save persisted per language. The form itself rendered the English tab blank on 2026-09-30 while that
            // body carried "Updated English content" - the load defect NOT-EDT-001 tracks - so what each tab shows is
            // recorded beside the served value rather than asserted here.
            assertFalse(again.servedRule().isEmpty(), "The reopened form should have loaded the rule from the API");
            List<String> blankTabs = new ArrayList<>();
            for (String language : LANGUAGE_TABS) {
                String served = again.servedContent(language);
                String shown = again.content(language);
                if (!shown.equals(served)) {
                    blankTabs.add(language + " shows \"" + shown + "\"");
                }
                String expected = language.equals(TAB_ENGLISH) ? "Updated English content" : CONTENT;
                assertEquals(expected, served, language.equals(TAB_ENGLISH) ? "English should carry the edit" : language + " should be untouched by the English edit");
            }
            CaseLog.data("Tabs rendering other than the served content", blankTabs.isEmpty() ? "none - every tab shows what was saved" : String.join("; ", blankTabs) + " (NOT-EDT-001's form-load defect)");
            again.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-003: Editing a one-time notification into a recurring one")
        void onceToDaily() {
            String title = PREFIX + "Once To Daily";
            deleteAfterwards(title);
            ensure(new Draft(title).schedule(SCHEDULE_ONCE, null).status(STATUS_INACTIVE));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setSchedule(SCHEDULE_DAILY);
            edit.setTimer(TIMER_9AM);
            edit.clickSave();
            openList();
            NotificationRow row = list.notification(title);
            assertEquals(SCHEDULE_DAILY, row.schedule(), "The Schedule should now be Daily");
            assertEquals(TIMER_9AM, row.timer(), "The Timer should now be 09 AM");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-004: Setting an Active recurring notification to Inactive stops it from firing")
        void inactiveStopsFiring() {
            // AQ-16 (answered 2026-09-26).
            String title = PREFIX + "Deactivated";
            deleteAfterwards(title);
            ensure(new Draft(title).schedule(SCHEDULE_DAILY, nextHourLabel()));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setStatus(STATUS_INACTIVE);
            edit.clickSave();
            openList();
            assertEquals(STATUS_INACTIVE, list.notification(title).status(), "The notification should now be Inactive");
            waitPastNextHour();
            assertTrue(historyToday().entriesFor(title).isEmpty(), "No Record History entry should appear after it was set Inactive");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-005: Changing a notification's Agent/Member Group targets does not retroactively alter already-sent Record History entries")
        void historyKeepsAsSentTargets() {
            // AQ-06 (answered 2026-09-26): Record History keeps the targets as they were at send time.
            String title = PREFIX + "Retarget";
            deleteAfterwards(title);
            ensure(new Draft(title));
            HistoryRow sent = sendNowAndRead(title);
            openList();
            List<String> others = someGroups(AGENT_GROUP, 2);
            NotificationFormPage edit = list.clickEdit(title);
            edit.chooseOptional(AGENT_GROUP);
            edit.removeAll(AGENT_GROUP);
            edit.target(AGENT_GROUP, others.get(1));
            edit.clickSave();
            HistoryRow after = historyToday().entries().stream().filter(e -> e.id().equals(sent.id())).findFirst().orElse(null);
            assertNotNull(after, "The earlier Record History entry " + sent.id() + " should still be listed");
            assertEquals(sent.agentGroup(), after.agentGroup(), "The earlier entry should keep its as-sent Agent Group");
            assertEquals(sent.memberGroup(), after.memberGroup(), "The earlier entry should keep its as-sent Member Group");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-006: Changing fields and clicking Cancel does not save them")
        void cancelKeepsTheSavedState() {
            String title = PREFIX + "Cancel Edit";
            deleteAfterwards(title);
            ensure(new Draft(title));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setContent(TAB_CHINESE, "Should not be saved");
            edit.chooseOptional(MEMBER_GROUP);
            edit.removeAll(MEMBER_GROUP);
            edit.cancel();
            openList();
            NotificationFormPage again = list.clickEdit(title);
            assertEquals(CONTENT, again.content(TAB_CHINESE), "The cancelled content change should be discarded");
            assertFalse(again.selectedNames(MEMBER_GROUP).isEmpty(), "The cancelled target change should be discarded");
            again.cancel();
        }

        // NOT-EDT-007 .. NOT-EDT-013: added 2026-09-30 from Si's review, numbered on from NOT-EDT-006. No workbook rows yet.

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-007: Clearing the required Title on Edit is refused with the same validation as Create")
        void clearingTitleOnEditIsRefused() {
            String title = PREFIX + "Edit Clear Title";
            deleteAfterwards(title);
            ensure(new Draft(title).status(STATUS_INACTIVE));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setTitle(TAB_CHINESE, "");
            edit.clickSave();
            String said = edit.messages();
            CaseLog.data("Save with the 繁體 Title cleared", said.isEmpty() ? "(no message)" : said);
            assertTrue(edit.isOpen(), "Save should be refused when the Title is cleared on Edit; the form closed (" + said + ")");
            assertFalse(said.isBlank(), "The refusal should say the Title is required");
            edit.cancel();
            openList();
            assertNotNull(list.notification(title), "The rule should keep the Title it had");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-008: Editing the 繁體 Title updates the list row at once, with the same ID and Created At")
        void renamingKeepsIdentity() {
            String title = PREFIX + "Rename Me";
            String renamed = PREFIX + "Renamed";
            deleteAfterwards(title);
            deleteAfterwards(renamed);
            ensure(new Draft(title).status(STATUS_INACTIVE));
            NotificationRow before = list.notification(title);
            NotificationFormPage edit = list.clickEdit(title);
            edit.setTitle(TAB_CHINESE, renamed);
            edit.clickSave();
            openList();
            assertFalse(list.isListed(title), "The old Title should no longer be listed");
            NotificationRow after = list.notification(renamed);
            assertNotNull(after, "The rule should be listed under its new Title");
            assertEquals(before.id(), after.id(), "The ID should be unchanged by the rename");
            assertEquals(before.createdAt(), after.createdAt(), "Created At should be unchanged by the rename");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-009: Changing Daily to Weekly on Edit shows Week day and saves it - the list reads the day and hour")
        void dailyToWeekly() {
            String title = PREFIX + "Daily To Weekly";
            deleteAfterwards(title);
            ensure(new Draft(title).status(STATUS_INACTIVE));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setSchedule(SCHEDULE_WEEKLY);
            assertTrue(edit.isWeekDayShown(), "Choosing Weekly on Edit should reveal Week day");
            edit.setWeekDay(SUNDAY);
            edit.setTimer(TIMER_8PM);
            edit.clickSave();
            openList();
            NotificationRow row = list.notification(title);
            assertEquals(SCHEDULE_WEEKLY, row.schedule(), "The Schedule should now be Weekly");
            assertEquals(weeklyTimer(SUNDAY, TIMER_8PM), row.timer(), "The Timer column should carry the week day and hour");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-010: Setting an Inactive Daily notification back to Active resumes firing at its Timer")
        void reactivatingResumesFiring() {
            String title = PREFIX + "Reactivated Daily";
            deleteAfterwards(title);
            ensure(new Draft(title).schedule(SCHEDULE_DAILY, nextHourLabel()).status(STATUS_INACTIVE));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setStatus(STATUS_ACTIVE);
            edit.clickSave();
            openList();
            assertEquals(STATUS_ACTIVE, list.notification(title).status(), "The notification should now be Active");
            waitPastNextHour();
            List<HistoryRow> sent = historyToday().entriesFor(title);
            CaseLog.data("Record History after the Timer", sent.toString());
            assertEquals(1, sent.size(), "The re-activated Daily rule should fire once at its Timer");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-011: Re-activating a Once rule that has already sent either sends it once more and ends Inactive again, or leaves it unsent - never sends it repeatedly")
        void reactivatingASentOnce() {
            // A "confirm whether" row: what Active means for a Once rule whose single send is done.
            String title = PREFIX + "Once Again";
            deleteAfterwards(title);
            ensure(new Draft(title).schedule(SCHEDULE_ONCE, null));   // sent on save, then sets itself Inactive
            String status = "";
            for (int i = 0; i < 12 && !STATUS_INACTIVE.equals(status); i++) {
                sessionPage.waitForTimeout(10_000);
                openList();
                status = list.notification(title).status();
            }
            if (!STATUS_INACTIVE.equals(status)) {
                abort("The Once rule did not set itself Inactive within two minutes of its send (NOT-CRT-024), so there is no sent Once rule to re-activate.");
            }
            int sentBefore = historyToday().entriesFor(title).size();
            openList();
            NotificationFormPage edit = list.clickEdit(title);
            edit.setStatus(STATUS_ACTIVE);
            edit.clickSave();
            int sentAfter = sentBefore;
            for (int i = 0; i < 12; i++) {
                sessionPage.waitForTimeout(10_000);
                openList();
                status = list.notification(title).status();
                sentAfter = historyToday().entriesFor(title).size();
                if (sentAfter > sentBefore && STATUS_INACTIVE.equals(status)) {
                    break;
                }
            }
            CaseLog.data("Re-activating a sent Once rule (open question)", "entries " + sentBefore + " -> " + sentAfter + ", Status now " + status);
            assertTrue(sentAfter == sentBefore || (sentAfter == sentBefore + 1 && STATUS_INACTIVE.equals(status)),
                    "Either it is sent once more and ends Inactive again, or it is not sent again; instead entries went " + sentBefore + " -> " + sentAfter + " and Status is " + status);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-012: Switching both targets from Optional to Select all on Edit saves as All / All")
        void widenToAll() {
            String title = PREFIX + "Widen To All";
            deleteAfterwards(title);
            ensure(new Draft(title).status(STATUS_INACTIVE));
            NotificationFormPage edit = list.clickEdit(title);
            edit.chooseSelectAll(AGENT_GROUP);
            edit.chooseSelectAll(MEMBER_GROUP);
            edit.clickSave();
            openList();
            NotificationRow row = list.notification(title);
            assertEquals(ALL, row.agentGroup(), "The Agent Group column should now read All");
            assertEquals(ALL, row.memberGroup(), "The Member group column should now read All");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-EDT-013: Removing the Link on Edit saves, and the next delivery carries no link")
        void removingTheLink() {
            String title = PREFIX + "Drop Link";
            deleteAfterwards(title);
            ensure(new Draft(title));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setLink(TAB_CHINESE, "");
            edit.clickSave();
            openList();
            NotificationFormPage again = list.clickEdit(title);
            assertTrue(again.link(TAB_CHINESE).isBlank(), "The Link should be saved empty");
            again.cancel();
            HistoryRow sent = sendNowAndRead(title);
            RecordHistoryPage history = historyToday();
            history.openDetail(sent.id());
            String link = history.detail("Link");
            CaseLog.data("Link on the delivery after removal", link.isEmpty() ? "(none)" : link);
            assertTrue(link.isBlank(), "The delivery after the Link was removed should carry no link");
        }
    }

    // =====================================================================
    // 5. SEND NOW (ROW ACTION)
    // =====================================================================

    @Nested
    @Order(60)
    @DisplayName("5. Send Now (row action)")
    class SendNow {

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-ACT-001: The circular-arrow icon is a Send button that pushes the notification immediately")
        void sendNowPushes() {
            // AQ-03 (answered 2026-09-26): the third icon is Send Now.
            ensure(sendNow());
            int before = historyToday().entriesFor(SEND_NOW_TITLE).size();
            HistoryRow entry = sendNowAndRead(SEND_NOW_TITLE);
            CaseLog.data("Entry logged", entry.id() + " at " + entry.releasedAt() + " (" + entry.status() + ")");
            assertTrue(historyToday().entriesFor(SEND_NOW_TITLE).size() > before, "Send Now should push the notification immediately and log it");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-ACT-002: Clicking Send Now logs a fresh Record History entry")
        void sendNowLogsAFreshEntry() {
            ensure(sendNow());
            Set<String> before = new HashSet<>(historyToday().entriesFor(SEND_NOW_TITLE).stream().map(HistoryRow::id).toList());
            HistoryRow entry = sendNowAndRead(SEND_NOW_TITLE);
            assertFalse(before.contains(entry.id()), "Send Now should create a new entry, not reuse " + entry.id());
            assertEquals(SENT, entry.status(), "The fresh entry should read Sent");
            assertEquals(LocalDate.now(SCHEDULE_ZONE).toString(), entry.releasedDate(), "Released At should be today in Hong Kong Time");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-ACT-003: Send Now does not alter the rule's own Schedule or Timer")
        void sendNowLeavesTheSchedule() {
            ensure(sendNow());
            NotificationRow before = list.notification(SEND_NOW_TITLE);
            sendNowAndRead(SEND_NOW_TITLE);
            openList();
            NotificationRow after = list.notification(SEND_NOW_TITLE);
            assertEquals(before.schedule(), after.schedule(), "Send Now should leave the Schedule as it was");
            assertEquals(before.timer(), after.timer(), "Send Now should leave the Timer as it was");
            assertEquals(before.status(), after.status(), "Send Now should leave the Status as it was");
        }

        // NOT-ACT-004 .. NOT-ACT-005: added 2026-09-30 from Si's review, numbered on from NOT-ACT-003. No workbook rows yet.

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-ACT-004: Send Now on an Inactive rule either sends and logs it or refuses with a message - never does nothing silently - and leaves the Status Inactive")
        void sendNowOnInactive() {
            // A "confirm whether" row. The 2026-09-30 inbox showed rules saved Inactive delivered to a member, so
            // whether Send Now bypasses Status needs a definite answer.
            String title = PREFIX + "Send Now Inactive";
            deleteAfterwards(title);
            ensure(new Draft(title).status(STATUS_INACTIVE));
            Set<String> before = historyToday().entriesFor(title).stream().map(HistoryRow::id).collect(Collectors.toSet());
            openList();
            String said = list.clickSendNow(title);
            List<HistoryRow> added = historyToday().entriesFor(title).stream().filter(e -> !before.contains(e.id())).toList();
            openList();
            String status = list.notification(title).status();
            CaseLog.data("Send Now on an Inactive rule (open question)", "the page said \"" + said + "\"; " + added.size() + " new Record History entries; Status now " + status);
            assertTrue(!added.isEmpty() || !said.isBlank(), "Send Now on an Inactive rule should either send and log it or refuse with a message; it did neither");
            assertEquals(STATUS_INACTIVE, status, "Send Now should not change the rule's Status");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-ACT-005: Two Send Now clicks in a row log two separate Record History entries")
        void sendNowTwice() {
            ensure(sendNow());
            Set<String> before = historyToday().entriesFor(SEND_NOW_TITLE).stream().map(HistoryRow::id).collect(Collectors.toSet());
            HistoryRow first = sendNowAndRead(SEND_NOW_TITLE);
            HistoryRow second = sendNowAndRead(SEND_NOW_TITLE);
            List<HistoryRow> added = historyToday().entriesFor(SEND_NOW_TITLE).stream().filter(e -> !before.contains(e.id())).toList();
            CaseLog.data("Entries added by two Send Now clicks", added.stream().map(e -> e.id() + " at " + e.releasedAt()).collect(Collectors.joining(", ")));
            assertFalse(first.id().equals(second.id()), "The second Send Now should log its own entry, not reuse " + first.id());
            assertEquals(2, added.size(), "Two Send Now clicks should add exactly two entries");
        }
    }

    // =====================================================================
    // 7. RECORD HISTORY LIST
    // =====================================================================

    @Nested
    @Order(70)
    @DisplayName("7. Record History list")
    class RecordHistoryList {

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHL-001: Record History lists ID, Notification Title, Type, Agent Group, Member Group, Released At, and Status for every send")
        void historyShowsEveryColumn() {
            ensure(daily());
            HistoryRow entry = sendNowAndRead(DAILY_TITLE);
            RecordHistoryPage history = historyToday();
            List<String> headers = history.columnHeaders();
            // Si, 2026-09-30: headers match regardless of case, as NOT-LST-001 does - the site heads "Member group".
            List<String> lowered = headers.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
            assertTrue(HISTORY_COLUMNS.stream().allMatch(c -> lowered.contains(c.toLowerCase(Locale.ROOT))), "Record History should head " + HISTORY_COLUMNS + "; it heads " + headers);
            assertFalse(entry.id().isBlank(), "The entry should carry an ID");
            assertEquals(TYPE_SYSTEM, entry.type());
            assertTrue(entry.agentGroup().contains(TARGET_AGENT_GROUP), "The entry should show the Agent Group it was sent to");
            assertTrue(entry.memberGroup().contains(TARGET_MEMBER_GROUP), "The entry should show the Member Group it was sent to");
            assertTrue(entry.releasedAt().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}.*"), "Released At should read like 2026-01-24 12:00");
            assertTrue(entry.actions().contains("View"), "The entry should offer a View action");
            assertTrue(List.of(SENT, PENDING, FAILED).contains(entry.status()), "Status should be Sent, Pending or Failed, not " + entry.status());
        }

        @Test
        @DisplayName("NOT-RHL-002: Status filter (All/Sent/Pending/Failed) narrows the history list")
        void statusFilter() {
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            for (String status : List.of(SENT, PENDING, FAILED)) {
                history.filterStatus(status);
                for (HistoryRow row : history.entries()) {
                    assertEquals(status, row.status(), "Filtered to " + status + ", entry " + row.id() + " reads " + row.status());
                }
                CaseLog.data(status + " entries", String.valueOf(history.entries().size()));
            }
        }

        @Test
        @DisplayName("NOT-RHL-003: From/To date filters narrow the history to that window")
        void dateFilter() {
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            List<HistoryRow> all = history.entries();
            if (all.isEmpty()) {
                abort("Record History is empty, so there is no window to narrow to.");
            }
            String day = all.get(0).releasedDate();
            history.filterDates(day, day);
            List<HistoryRow> window = history.entries();
            assertFalse(window.isEmpty(), "A window around " + day + " should keep that day's entries");
            for (HistoryRow row : window) {
                assertEquals(day, row.releasedDate(), "Only entries released on " + day + " should show; " + row.id() + " is " + row.releasedAt());
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHL-004: Changing the language filter updates the displayed Notification Title")
        void languageFilter() {
            String title = PREFIX + "History Language";
            String english = title + " EN";
            deleteAfterwards(title);
            NotificationFormPage form = fill(new Draft(title));
            form.setTitle(TAB_ENGLISH, english);
            form.clickSave();
            HistoryRow entry = sendNowAndRead(title);
            RecordHistoryPage history = historyToday();
            history.filterLanguage(TAB_ENGLISH);
            HistoryRow inEnglish = history.entries().stream().filter(e -> e.id().equals(entry.id())).findFirst().orElse(null);
            assertNotNull(inEnglish, "Entry " + entry.id() + " should still be listed under English");
            assertEquals(english, inEnglish.title(), "Under English the entry should show its English title");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHL-005: Over multiple days, a recurring notification logs a separate entry each time it fires")
        void eachFiringIsItsOwnEntry() {
            String title = PREFIX + "Hourly Firing";
            deleteAfterwards(title);
            ensure(new Draft(title).schedule(SCHEDULE_DAILY, nextHourLabel()));
            HistoryRow first = sendNowAndRead(title);
            waitPastNextHour();
            List<HistoryRow> entries = historyToday().entriesFor(title);
            assertTrue(entries.size() >= 2, "The scheduled firing should add its own entry alongside the earlier one; found " + entries.size());
            assertTrue(entries.stream().anyMatch(e -> e.id().equals(first.id())), "The earlier entry should not be replaced");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHL-006: A one-time notification never generates a second history entry")
        void onceLogsExactlyOnce() {
            String title = PREFIX + "Once Only";
            deleteAfterwards(title);
            // Record History outlives the rule: deleting the rule after the case leaves its entry behind, so every
            // earlier run of this row today counts under the same title (the 16:33 run read 3, its screenshot showing
            // the 15:04 and 16:33 sends among them). The row therefore judges only the entries its own rule adds.
            // A leftover rule under the title has already sent, and ensure() would keep it rather than make a fresh
            // one - so it goes first.
            openList();
            list.deleteIfListed(title);
            Set<String> before = historyToday().entriesFor(title).stream().map(HistoryRow::id).collect(Collectors.toSet());
            ensure(new Draft(title).schedule(SCHEDULE_ONCE, null));
            sessionPage.waitForTimeout(60_000);
            List<HistoryRow> first = historyToday().entriesFor(title).stream().filter(e -> !before.contains(e.id())).toList();
            sessionPage.waitForTimeout(120_000);
            List<HistoryRow> later = historyToday().entriesFor(title).stream().filter(e -> !before.contains(e.id())).toList();
            CaseLog.data("Entries from this run's rule", "after 1 minute " + describe(first) + ", after 3 minutes " + describe(later)
                    + (before.isEmpty() ? "" : "; left out " + before.size() + " earlier entries under the same title from previous runs: " + before));
            assertEquals(1, later.size(), "A Once notification should have exactly one Record History entry");
            assertEquals(first.size(), later.size(), "No further entry should appear with time");
        }

        @Test
        @DisplayName("NOT-RHL-007: Failed status is reserved for delivery-layer failures such as lost connection, not content or validation issues")
        void failedEntries() {
            // AQ-08 half answered: Failed is delivery-layer only. Still open: a reason or retry on the detail view.
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            history.filterStatus(FAILED);
            List<HistoryRow> failed = history.entries();
            if (failed.isEmpty()) {
                abort("No Failed entry exists to inspect. A Failed send needs a delivery-layer failure (AQ-08), which the suite cannot cause.");
            }
            history.openDetail(failed.get(0).id());
            assertEquals(FAILED, history.detail("Status"), "The detail view should show the Failed status");
            CaseLog.data("Failure reason or retry offered (AQ-08, still open)", history.failureInformation().isEmpty() ? "none shown" : history.failureInformation());
        }

        // NOT-RHL-008 .. NOT-RHL-012: added 2026-09-30 from Si's review, numbered on from NOT-RHL-007. No workbook rows yet.

        @Test
        @DisplayName("NOT-RHL-008: Record History's page size and Prev/Next pager work as the Notification tab's do")
        void historyPager() {
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            List<Integer> sizes = history.pageSizeOptions();
            assertFalse(sizes.isEmpty(), "Record History should offer a page-size selector");
            history.setPageSize(sizes.get(0));
            List<HistoryRow> first = history.entries();
            int[] showing = history.showing();
            CaseLog.data("Page size " + sizes.get(0), first.size() + " rows, " + (showing == null ? "no entry count" : showing[0] + ".." + showing[1] + " of " + showing[2]));
            assertTrue(first.size() <= sizes.get(0), "No more than " + sizes.get(0) + " entries should show per page");
            if (!history.hasNextPage()) {
                abort("Record History has only one page at " + sizes.get(0) + " per page, so the pager has nowhere to go.");
            }
            history.nextPage();
            List<HistoryRow> second = history.entries();
            int[] onSecond = history.showing();
            assertFalse(first.equals(second), "Next should show a different page of entries");
            assertTrue(onSecond == null || onSecond[0] == sizes.get(0) + 1, "Page 2 should start at entry " + (sizes.get(0) + 1));
            history.previousPage();
            assertEquals(first.stream().map(HistoryRow::id).toList(), history.entries().stream().map(HistoryRow::id).toList(), "Prev should come back to the first page");
        }

        @Test
        @DisplayName("NOT-RHL-009: A From date later than the To date shows nothing or is corrected, never a wrong window")
        void invertedDateRange() {
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            String today = LocalDate.now(SCHEDULE_ZONE).toString();
            String lastWeek = LocalDate.now(SCHEDULE_ZONE).minusDays(7).toString();
            history.filterDates(today, lastWeek);
            List<HistoryRow> shown = history.entries();
            String from = history.fromDate();
            String to = history.toDate();
            CaseLog.data("From " + today + " To " + lastWeek, shown.size() + " entries; the fields now read From " + from + " To " + to);
            if (shown.isEmpty()) {
                return;   // nothing matches an impossible window - one acceptable answer
            }
            assertTrue(from.compareTo(to) <= 0, "Entries are shown although From is still later than To - the window is wrong");
            for (HistoryRow row : shown) {
                assertTrue(row.releasedDate().compareTo(from) >= 0 && row.releasedDate().compareTo(to) <= 0,
                        "Entry " + row.id() + " (" + row.releasedAt() + ") is outside the corrected window " + from + " .. " + to);
            }
        }

        @Test
        @DisplayName("NOT-RHL-010: Clearing the From/To dates restores the whole history")
        void clearingDates() {
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            int whole = history.entries().size();
            String today = LocalDate.now(SCHEDULE_ZONE).toString();
            history.filterDates(today, today);
            int inWindow = history.entries().size();
            history.filterDates("", "");
            int after = history.entries().size();
            CaseLog.data("Entries", "whole " + whole + ", today " + inWindow + ", after clearing " + after + "; fields read From \"" + history.fromDate() + "\" To \"" + history.toDate() + "\"");
            assertTrue(history.fromDate().isEmpty() && history.toDate().isEmpty(), "Both date fields should be empty after clearing");
            assertEquals(whole, after, "Clearing the dates should show the whole history again");
            assertTrue(after >= inWindow, "The whole history should hold at least today's entries");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHL-011: A broadcast send's Record History row reads All / All")
        void broadcastHistoryReadsAll() {
            ensure(broadcast());
            HistoryRow sent = sendNowAndRead(BROADCAST_TITLE);
            CaseLog.data("Entry " + sent.id(), "Agent Group \"" + sent.agentGroup() + "\", Member Group \"" + sent.memberGroup() + "\"");
            assertEquals(ALL, sent.agentGroup(), "A broadcast's history row should read All for Agent Group");
            assertEquals(ALL, sent.memberGroup(), "A broadcast's history row should read All for Member Group");
        }

        @Test
        @DisplayName("NOT-RHL-012: Record History is listed newest first, by Released At and ID")
        void historyNewestFirst() {
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            List<HistoryRow> rows = history.entries();
            if (rows.size() < 2) {
                abort("Record History holds " + rows.size() + " entries; an order needs at least two.");
            }
            for (int i = 1; i < rows.size(); i++) {
                HistoryRow above = rows.get(i - 1);
                HistoryRow below = rows.get(i);
                assertTrue(above.releasedAt().compareTo(below.releasedAt()) >= 0,
                        "Entry " + below.id() + " (" + below.releasedAt() + ") is newer than the entry above it, " + above.id() + " (" + above.releasedAt() + ")");
                if (above.id().matches("\\d+") && below.id().matches("\\d+")) {
                    assertTrue(Integer.parseInt(above.id()) > Integer.parseInt(below.id()), "ID " + below.id() + " should come after ID " + above.id());
                }
            }
        }
    }

    // =====================================================================
    // 8. RECORD HISTORY DETAIL VIEW
    // =====================================================================

    @Nested
    @Order(80)
    @DisplayName("8. Record History detail view")
    class RecordHistoryDetail {

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHD-001: The detail view's Title/Content/Link match what was actually delivered, for every language tab")
        void detailShowsWhatWasSent() {
            ensure(daily());
            HistoryRow entry = sendNowAndRead(DAILY_TITLE);
            RecordHistoryPage history = historyToday();
            history.openDetail(entry.id());
            for (String language : List.of(TAB_CHINESE, TAB_ENGLISH, TAB_SPAIN)) {
                String[] sent = history.sentContent(language);
                assertEquals(DAILY_TITLE, sent[0], language + ": the detail's Title should be what was sent");
                assertEquals(CONTENT, sent[1], language + ": the detail's Content should be what was sent");
                assertEquals(LINK, sent[2], language + ": the detail's Link should be what was sent");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHD-002: The detail view's Released At is identical to the value shown in the Record History list")
        void releasedAtMatches() {
            ensure(daily());
            HistoryRow entry = sendNowAndRead(DAILY_TITLE);
            RecordHistoryPage history = historyToday();
            history.openDetail(entry.id());
            assertEquals(entry.releasedAt(), history.detail("Released At"), "Released At should match the list exactly");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHD-003: Agent/Member Group tags in the detail view represent who the notification was actually sent to")
        void detailTargetsAreASnapshot() {
            // AQ-06 (answered 2026-09-26): an as-sent snapshot, not a live mirror of the rule.
            String title = PREFIX + "Snapshot";
            deleteAfterwards(title);
            ensure(new Draft(title));
            HistoryRow entry = sendNowAndRead(title);
            List<String> others = someGroups(AGENT_GROUP, 2);
            NotificationFormPage edit = list.clickEdit(title);
            edit.chooseOptional(AGENT_GROUP);
            edit.removeAll(AGENT_GROUP);
            edit.target(AGENT_GROUP, others.get(1));
            edit.clickSave();
            RecordHistoryPage history = historyToday();
            history.openDetail(entry.id());
            List<String> agents = history.targetTags("Agent Group");
            CaseLog.data("Detail Agent Group tags", agents.toString());
            assertTrue(agents.contains(TARGET_AGENT_GROUP), "The detail should still show the as-sent Agent Group");
            assertFalse(agents.contains(others.get(1)), "The detail should not follow the rule's later target " + others.get(1));
        }

        @Test
        @DisplayName("NOT-RHD-004: Returning from a detail view preserves the Record History list's filter state")
        void backToListKeepsFilters() {
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            List<HistoryRow> all = history.entries();
            if (all.isEmpty()) {
                abort("Record History is empty, so there is no entry to open.");
            }
            String day = all.get(0).releasedDate();
            String status = all.get(0).status();
            history.filterStatus(status);
            history.filterDates(day, day);
            history.openDetail(history.entries().get(0).id());
            history.backToList();
            assertEquals(status, history.statusFilter(), "The Status filter should still be applied");
            assertEquals(day, history.fromDate(), "From should still be applied");
            assertEquals(day, history.toDate(), "To should still be applied");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHD-005: Confirming what the detail view's Schedule and Timer fields represent for a single send instance")
        void recordLevelScheduleAndTimer() {
            // Still a "confirm whether" row. The capture shows Schedule "Once" and Timer "—" on a record of a Daily rule.
            ensure(daily());
            HistoryRow entry = sendNowAndRead(DAILY_TITLE);
            RecordHistoryPage history = historyToday();
            history.openDetail(entry.id());
            String schedule = history.detail("Schedule");
            String timer = history.detail("Timer");
            CaseLog.data("Record-level Schedule / Timer (open question)", schedule + " / " + timer);
            assertFalse(schedule.isBlank(), "The detail view should show a Schedule for the record");
        }

        @Test
        @DisplayName("NOT-RHD-006: Confirming what is shown before a send has resolved")
        void pendingDetail() {
            // Still a "confirm whether" row: what Released At and Status show for a Pending entry.
            openList();
            RecordHistoryPage history = list.openRecordHistory();
            history.filterStatus(PENDING);
            List<HistoryRow> pending = history.entries();
            if (pending.isEmpty()) {
                abort("No Pending entry exists to open; a send only stays Pending while delivery is unresolved.");
            }
            history.openDetail(pending.get(0).id());
            CaseLog.data("Pending detail (open question)", "Released At \"" + history.detail("Released At") + "\", Status \"" + history.detail("Status") + "\"");
            assertEquals(PENDING, history.detail("Status"), "The detail view should show the Pending status");
        }

        // NOT-RHD-007 .. NOT-RHD-008: added 2026-09-30 from Si's review, numbered on from NOT-RHD-006. No workbook rows yet.

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHD-007: The details of a send still open after its rule has been deleted")
        void detailAfterRuleDeleted() {
            String title = PREFIX + "Detail After Delete";
            deleteAfterwards(title);
            ensure(new Draft(title));
            HistoryRow sent = sendNowAndRead(title);
            openList();
            list.clickDelete(title);
            list.confirmDelete();
            RecordHistoryPage history = historyToday();
            boolean kept = history.entries().stream().anyMatch(e -> e.id().equals(sent.id()));
            if (!kept) {
                CaseLog.data("Entry " + sent.id() + " after the rule was deleted", "removed with the rule (NOT-DEL-004 records the same), so there is no detail to open");
                return;
            }
            history.openDetail(sent.id());
            assertTrue(history.isDetailShowing(), "The details of entry " + sent.id() + " should open although its rule is gone");
            String[] sentText = history.sentContent(TAB_CHINESE);
            assertEquals(title, sentText[0], "The detail should still show the Title as sent");
            assertEquals(CONTENT, sentText[1], "The detail should still show the Content as sent");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-RHD-008: The details' Link is the link the member's message opens")
        void detailLinkMatchesTheMembersLink() {
            ensure(sendNow());
            HistoryRow sent = sendNowAndRead(SEND_NOW_TITLE);
            RecordHistoryPage history = historyToday();
            history.openDetail(sent.id());
            String detailLink = history.detail("Link");
            CaseLog.data("Link on the details view", detailLink);
            assertEquals(LINK, detailLink, "The details should show the rule's Link");
            MessageInboxPage inbox = memberInbox();
            assertTrue(inbox.awaitDelivery(SEND_NOW_TITLE), "The send should reach the member within client.messages.deliveryWaitSeconds");
            inbox.openMessage(SEND_NOW_TITLE);
            String followed = inbox.followLink();
            assertEquals(detailLink, followed, "The member's message should open the same link the details show");
        }
    }

    // =====================================================================
    // 9. LOCALIZATION
    // =====================================================================

    @Nested
    @Order(90)
    @DisplayName("9. Localization")
    class Localization {

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-I18N-001: All 12 language tabs are present, labeled, and selectable everywhere content is shown")
        void twelveTabsEverywhere() {
            NotificationFormPage form = list.clickAdd();
            List<String> tabs = form.languageTabLabels();
            assertEquals(LANGUAGE_TABS, tabs, "Add Notification should offer the twelve tabs in order");
            for (String tab : tabs) {
                form.selectLanguageTab(tab);
                assertEquals(tab, form.activeLanguageTab(), tab + " should be selectable");
            }
            form.cancel();
            ensure(daily());
            HistoryRow entry = sendNowAndRead(DAILY_TITLE);
            RecordHistoryPage history = historyToday();
            history.openDetail(entry.id());
            // Si, 2026-09-30: what the system shows - the details view draws the same twelve tabs the form does
            // (measured on the shipped screen); they are read from ul.notif-lang-tabs, as on the form.
            List<String> detailTabs = history.detailLanguageTabs();
            CaseLog.data("Record Details tabs", detailTabs.toString());
            assertEquals(tabs, detailTabs, "Record Details should offer the same twelve tabs as the form");
        }

        @Test
        @DisplayName("NOT-I18N-002: Switching the admin UI's display language does not change which content-language tab is active")
        void uiLanguageIsIndependent() {
            NotificationFormPage form = list.clickAdd();
            form.setContent(TAB_SPAIN, "Contenido en español");
            String before = shell.language();
            try {
                shell.selectLanguage(AdminShellPage.CHINESE);
                // Si, 2026-09-30: the switch re-renders the whole screen, and reading the form after it timed out.
                // When the form survives, the claim is asserted as written; when the re-render discards the
                // unsaved form, that is recorded and the independence is shown on a fresh form under 中文 - the
                // content tabs are the same twelve, and Spain selects.
                if (form.isOpen()) {
                    assertEquals(TAB_SPAIN, form.activeLanguageTab(), "The Spain content tab should stay selected");
                    assertEquals("Contenido en español", form.content(TAB_SPAIN), "Spain's entered text should be untouched");
                } else {
                    CaseLog.data("Add Notification after the language switch",
                            "not on screen - the switch re-rendered the screen and discarded the unsaved form");
                    list.open();
                    form = list.clickAdd();
                    assertEquals(LANGUAGE_TABS, form.languageTabLabels(), "The content-language tabs should be the same twelve under the 中文 interface");
                    form.selectLanguageTab(TAB_SPAIN);
                    assertEquals(TAB_SPAIN, form.activeLanguageTab(), "Spain should be selectable under the 中文 interface");
                }
                form.cancel();
            } finally {
                shell.selectLanguage(before);
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-I18N-003: A member sees a notification's Title/Content in their own app language, when each language was manually customized after Override")
        void memberSeesTheirLanguage() {
            // Si, 2026-09-30: the member's app language is Message Center's own 语言 select - the row chooses
            // English and 繁體 there. Written as the workbook asks: 繁體 is the original text, English is
            // customized after Override, and the same message is read under each.
            String title = PREFIX + "Own Language";
            String englishTitle = title + " (English)";
            String englishContent = "English content customized after Override. " + CONTENT;
            deleteAfterwards(title);
            openList();
            list.deleteIfListed(title);
            NotificationFormPage form = fill(new Draft(title).broadcast());
            form.fillLanguage(TAB_ENGLISH, englishTitle, englishContent, null);
            form.clickSave();
            openList();
            if (!list.isListed(title)) {
                abort("The fixture \"" + title + "\" could not be created (" + form.lastMessage() + "), so there is nothing to read on the member site.");
            }
            NotificationFormPage edit = list.clickEdit(title);
            String savedEnglish = edit.servedContent(TAB_ENGLISH);
            edit.cancel();
            CaseLog.data("English content as saved", savedEnglish);
            assertEquals(englishContent, savedEnglish, "The English text customized after Override should be what was saved - "
                    + "otherwise Override overwrote it and the member cannot be shown a per-language text");
            sendNowAndRead(title);
            MessageInboxPage inbox = memberInbox();
            showInboxIn(inbox, "en");
            assertTrue(inbox.awaitDelivery(englishTitle), "Under English the member should receive the message with its English title \""
                    + englishTitle + "\" within client.messages.deliveryWaitSeconds of Send Now");
            inbox.openMessage(englishTitle);
            String underEnglish = inbox.detailText();
            inbox.back();
            CaseLog.data("Read under English", underEnglish);
            assertTrue(underEnglish.contains(englishContent), "Under English the member should read the customized English content");
            showInboxIn(inbox, "zh-TW");
            assertTrue(inbox.isListed(title), "Under 繁體 the same message should carry its 繁體 title \"" + title + "\"");
            inbox.openMessage(title);
            String underChinese = inbox.detailText();
            inbox.back();
            CaseLog.data("Read under 繁體", underChinese);
            assertTrue(underChinese.contains(CONTENT), "Under 繁體 the member should read the original 繁體 content");
            assertFalse(underChinese.contains(englishContent), "Under 繁體 the member should not be shown the English text");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-I18N-004: A notification created via \"Override to All languages\" without manual per-language edits shows the same untranslated text regardless of the viewer's app language")
        void overrideCopiesDoNotTranslate() {
            // AQ-13 (answered 2026-09-26): Override copies verbatim; the admin side proves every tab holds the same text.
            String title = PREFIX + "Override Only";
            deleteAfterwards(title);
            ensure(new Draft(title));
            NotificationFormPage edit = list.clickEdit(title);
            for (String language : LANGUAGE_TABS) {
                assertEquals(CONTENT, edit.content(language), language + " should hold the untranslated 繁體 text");
            }
            edit.cancel();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-I18N-005: Client behavior for a language whose Title/Content was left blank on admin side")
        void blankLanguageFallback() {
            // Still a "confirm whether" row (blank, base-language fallback, or omitted). Si, 2026-09-30: the member's
            // language is Message Center's 语言 select, so the row saves with Bengali blank, reads the message under
            // Bengali, and records which answer the system gives. What any answer must satisfy: the admin side says
            // why when it refuses, and the member is never shown an empty message.
            String title = PREFIX + "Bengali Blank";
            deleteAfterwards(title);
            openList();
            list.deleteIfListed(title);
            NotificationFormPage form = fill(new Draft(title).broadcast());
            form.setTitle(TAB_BENGALI, "");
            form.setContent(TAB_BENGALI, "");
            form.clickSave();
            openList();
            if (!list.isListed(title)) {
                String said = form.lastMessage();
                CaseLog.data("Save with Bengali blank", "refused - " + (said.isEmpty() ? "(no message)" : said));
                CaseLog.data("Client behaviour for a blank language", "not reachable: the admin side does not save a rule with a language left blank");
                assertFalse(said.isBlank(), "A save refused for the blank Bengali language should say why");
                return;
            }
            CaseLog.data("Save with Bengali blank", "accepted");
            NotificationFormPage edit = list.clickEdit(title);
            CaseLog.data("Bengali as saved", "title \"" + edit.servedTitle(TAB_BENGALI) + "\", content \"" + edit.servedContent(TAB_BENGALI) + "\"");
            edit.cancel();
            sendNowAndRead(title);
            MessageInboxPage inbox = memberInbox();
            showInboxIn(inbox, "en");
            assertTrue(inbox.awaitDelivery(title), "The message should reach the member within client.messages.deliveryWaitSeconds of Send Now");
            MessageInboxPage.Message underEnglish = inbox.message(title);
            assertNotNull(underEnglish, "The delivered message should be readable under English");
            showInboxIn(inbox, "bn");
            MessageInboxPage.Message underBengali = inbox.messageById(underEnglish.id());
            if (underBengali == null) {
                CaseLog.data("Client behaviour for a blank language", "omitted - message #" + underEnglish.id() + " is not listed under Bengali");
                return;
            }
            String shown = "title \"" + underBengali.title() + "\", preview \"" + underBengali.preview() + "\"";
            boolean fallsBack = underBengali.title().equals(title);
            CaseLog.data("Client behaviour for a blank language", (fallsBack ? "base-language (繁體) fallback - " : "listed - ") + shown);
            assertFalse(underBengali.title().isBlank() && underBengali.preview().isBlank(),
                    "A language left blank on the admin side must not reach the member as an empty message - it should fall back to another language or be omitted");
        }

        /** Chooses the member's language on Message Center's 语言 select, or aborts naming what it does offer. */
        private void showInboxIn(MessageInboxPage inbox, String language) {
            if (!inbox.selectLanguage(language)) {
                abort("Message Center's 语言 select does not offer " + language + " - it offers " + inbox.languageOptions()
                        + " - so the member cannot be shown that language.");
            }
        }

        @Test
        @DisplayName("NOT-I18N-006: Buttons, hints, and field labels on the Notification screens render correctly in both admin UI languages")
        void noUntranslatedKeys() {
            String before = shell.language();
            try {
                for (String language : List.of(AdminShellPage.ENGLISH, AdminShellPage.CHINESE)) {
                    shell.selectLanguage(language);
                    // Si, 2026-09-30: reached by address and by class, not through openList(), which drives the
                    // Status and Show filters by their English labels - under 中文 those labels read differently
                    // and every lookup timed out. The Record History tab is found by its ?tab=history link.
                    list.open();
                    assertNoKeys(list.screenText(), "the list in " + language);
                    NotificationFormPage form = list.clickAdd();
                    assertNoKeys(form.screenText(), "Add Notification in " + language);
                    form.cancel();
                    list.open();
                    assertNoKeys(list.openRecordHistory().columnHeaders().toString(), "Record History in " + language);
                }
            } finally {
                shell.selectLanguage(before);
                openList();
            }
        }

        private void assertNoKeys(String text, String where) {
            List<String> keys = new ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b[a-z]+(?:[._][a-z0-9]+){2,}\\b|\\{\\{[^}]+}}|undefined|null").matcher(text);
            while (m.find()) {
                if (!m.group().contains("lumen.app") && !m.group().startsWith("system.")) {
                    keys.add(m.group());
                }
            }
            assertTrue(keys.isEmpty(), "No missing translation keys should show on " + where + "; found " + keys);
        }

        // NOT-I18N-007: added 2026-09-30 from Si's review, numbered on from NOT-I18N-006. No workbook row yet.

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-I18N-007: Validation and confirmation messages are translated under the 中文 interface")
        void messagesAreTranslated() {
            ensure(daily());
            String before = shell.language();
            try {
                shell.selectLanguage(AdminShellPage.CHINESE);
                // Reached by address and class, as NOT-I18N-006 does: openList() drives filters by their English labels.
                list.open();
                NotificationFormPage form = list.clickAdd();
                form.clickSave();   // an empty form: the required-fields refusal
                String refusal = form.messages();
                CaseLog.data("Validation under 中文", refusal.isEmpty() ? "(no message)" : refusal);
                form.cancel();
                list.open();
                String deleteAsk = "";
                if (list.isListed(DAILY_TITLE)) {
                    list.clickDelete(DAILY_TITLE);
                    deleteAsk = list.deleteDialogText();
                    list.cancelDelete();
                }
                CaseLog.data("Delete confirmation under 中文", deleteAsk.isEmpty() ? "(none shown)" : deleteAsk);
                assertFalse(refusal.isBlank(), "Saving an empty form should be refused with a message under 中文 too");
                assertTrue(hasCjk(refusal), "The validation message should be in 中文 under the 中文 interface, not \"" + refusal + "\"");
                if (!deleteAsk.isEmpty()) {
                    assertTrue(hasCjk(deleteAsk), "The delete confirmation should be in 中文 under the 中文 interface, not \"" + deleteAsk + "\"");
                }
            } finally {
                shell.selectLanguage(before);
                openList();
            }
        }
    }

    // =====================================================================
    // 10. CLIENT MESSAGE INBOX
    // =====================================================================

    @Nested
    @Order(100)
    @DisplayName("10. Client Message Inbox")
    class ClientInbox {

        /** Pushes the Send Now rule - Select all on both groups - to the member now. */
        private void deliverSendNow() {
            ensure(sendNow());
            sendNowAndRead(SEND_NOW_TITLE);
        }

        /** Pushes the Daily rule - targeted at the configured groups only - for the row that needs a non-member. */
        private void deliverDaily() {
            ensure(daily());
            sendNowAndRead(DAILY_TITLE);
        }

        /** The member's Message Center once {@code title} has been delivered to it - delivery is queued, so this waits. */
        private MessageInboxPage inboxHolding(String title) {
            MessageInboxPage inbox = memberInbox();
            assertTrue(inbox.awaitDelivery(title), "\"" + title + "\" should reach the member's Message Center within "
                    + "client.messages.deliveryWaitSeconds of Send Now reporting Sent");
            return inbox;
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-001: Message Center shows every message actually targeting this member")
        void inboxListsTargetedMessages() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            assertTrue(inbox.isListed(SEND_NOW_TITLE), "The member's Message Center should list \"" + SEND_NOW_TITLE + "\"");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-002: Each tab shows only messages of its own type")
        void tabsFilterByType() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            assertEquals(List.of(MessageInboxPage.TAB_ALL, MessageInboxPage.TAB_SYSTEM, MessageInboxPage.TAB_DEPOSIT, MessageInboxPage.TAB_WITHDRAW),
                    inbox.tabs(), "Message Center should offer All, System, Deposit and Withdraw");
            inbox.selectTab(MessageInboxPage.TAB_SYSTEM);
            assertTrue(inbox.isListed(SEND_NOW_TITLE), "The System tab should list the System notification");
            for (String other : List.of(MessageInboxPage.TAB_DEPOSIT, MessageInboxPage.TAB_WITHDRAW)) {
                inbox.selectTab(other);
                assertFalse(inbox.isListed(SEND_NOW_TITLE), "The " + other + " tab should not list a System notification");
            }
            inbox.selectTab(MessageInboxPage.TAB_ALL);
            assertTrue(inbox.isListed(SEND_NOW_TITLE), "All should list every type together");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-003: Unread messages show a distinct indicator that read messages do not have")
        void unreadIndicator() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            assertTrue(inbox.isUnread(SEND_NOW_TITLE), "A just-delivered message should show the unread dot");
            inbox.openMessage(SEND_NOW_TITLE);
            inbox.back();
            assertFalse(inbox.isUnread(SEND_NOW_TITLE), "A read message should not show the unread dot");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-004: Opening an unread message clears its unread indicator")
        void openingMarksRead() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            assertTrue(inbox.isUnread(SEND_NOW_TITLE), "The message should start unread");
            inbox.openMessage(SEND_NOW_TITLE);
            inbox.back();
            assertFalse(inbox.isUnread(SEND_NOW_TITLE), "Opening the message should clear its unread dot");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-005: Tapping Mark All as Read updates every unread message in one action")
        void markAllAsRead() {
            deliverSendNow();
            ensure(broadcast());
            sendNowAndRead(BROADCAST_TITLE);
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            inbox.markAllAsRead();
            for (MessageInboxPage.Message message : inbox.messages()) {
                assertFalse(message.unread(), "\"" + message.title() + "\" should be read after Mark All as Read");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-006: Opening a System notification shows its Title and Content as plain text")
        void systemDetailIsPlainText() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            inbox.openMessage(SEND_NOW_TITLE);
            String text = inbox.detailText();
            assertTrue(text.contains(SEND_NOW_TITLE), "The detail should show the Title");
            assertTrue(text.contains(CONTENT), "The detail should show the admin-configured Content");
            assertTrue(inbox.detailFields().isEmpty(), "A System message should carry no structured transaction fields");
        }

        @Test
        @DisplayName("NOT-CLI-007: Opening a Deposit notification shows Amount, Method, Transaction ID, Destination Wallet, and Status")
        void depositDetailFields() {
            MessageInboxPage inbox = memberInbox();
            inbox.selectTab(MessageInboxPage.TAB_DEPOSIT);
            List<MessageInboxPage.Message> deposits = inbox.messages();
            if (deposits.isEmpty()) {
                abort("The member has no Deposit message. It needs a completed deposit for " + USER_USERNAME
                        + ", which is a real transaction the suite does not make.");
            }
            inbox.openMessage(deposits.get(0).title());
            Map<String, String> fields = inbox.detailFields();
            CaseLog.data("Deposit detail", fields.toString());
            for (String field : DEPOSIT_DETAIL_FIELDS) {
                assertTrue(fields.containsKey(field) && !fields.get(field).isBlank(), "The Deposit detail should show " + field);
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-008: A message's configured Link opens correctly when tapped")
        void linkNavigates() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            inbox.openMessage(SEND_NOW_TITLE);
            String followed = inbox.followLink();
            CaseLog.data("Link followed", followed);
            assertTrue(followed.equals(LINK) || inbox.currentUrl().startsWith(LINK), "Tapping through should reach " + LINK);
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-009: A member outside a notification's Agent/Member Group does not see it")
        void outsiderDoesNotSeeIt() {
            deliverDaily();
            MessageInboxPage inbox = memberInbox(OUTSIDER_USERNAME, OUTSIDER_PASSWORD, "test.notification.outsider");
            // Delivery is queued, so give it the full window before deciding nothing came.
            assertFalse(inbox.awaitDelivery(DAILY_TITLE), "A member outside the targeted groups should not receive \"" + DAILY_TITLE + "\"");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-010: A notification targeting All reaches members regardless of their specific group")
        void broadcastReachesEveryone() {
            ensure(broadcast());
            sendNowAndRead(BROADCAST_TITLE);
            MessageInboxPage inbox = inboxHolding(BROADCAST_TITLE);
            assertTrue(inbox.isListed(BROADCAST_TITLE), "The broadcast should appear for the member");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-011: An Inactive notification produces no client-visible message")
        void inactiveNeverArrives() {
            // AQ-16 (answered 2026-09-26). A rule created Inactive, and never sent, must not arrive.
            String title = PREFIX + "Inactive To Member";
            deleteAfterwards(title);
            ensure(new Draft(title).schedule(SCHEDULE_ONCE, null).status(STATUS_INACTIVE));
            sessionPage.waitForTimeout(30_000);
            MessageInboxPage inbox = memberInbox();
            assertFalse(inbox.isListed(title), "An Inactive notification should never reach the member's inbox");
        }

        @Test
        @DisplayName("NOT-CLI-012: Deposit and Withdraw notifications fire on both completion and rejection outcomes")
        void transactionsNotifyOnBothOutcomes() {
            abort("AQ-11 (answered 2026-09-26): completed and rejected deposits and withdrawals all notify. Deciding it needs four real "
                    + "transactions for " + USER_USERNAME + " - a deposit and a withdrawal, each approved and rejected in the back office - "
                    + "which this suite does not make. Read Message Center after each.");
        }

        @Test
        @DisplayName("NOT-CLI-013: Message Center reads the member in the same currency the suite set the notifications up under")
        void inboxCurrencyMatchesSetup() {
            // Si, 2026-09-30, numbered on from NOT-CLI-012 - no workbook row yet. The back office keeps its notifications per header currency and the
            // suite creates them under test.currency (HKD), so a member reading the inbox on another wallet would see
            // none of them - the 11:48 run's inbox read "currency: USDT". memberInbox() puts the member on test.currency
            // first; this row proves the Message Center follows the wallet.
            MessageInboxPage inbox = memberInbox();
            String shown = inbox.currency();
            CaseLog.data("Message Center currency", shown.isEmpty() ? "not shown" : shown);
            assertEquals(CURRENCY.toUpperCase(Locale.ROOT), shown, "Message Center should read the member in " + CURRENCY
                    + ", the currency the suite set the notifications up under (test.currency)");
        }

        // NOT-CLI-014 .. NOT-CLI-020 (018 excepted): added 2026-09-30 from Si's review, numbered on from NOT-CLI-013.
        // NOT-CLI-018 (a Withdraw message's detail fields) needs a real withdrawal, like NOT-CLI-012, and is not written.

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-014: Messages are listed newest first, and Load more appends without repeating any")
        void newestFirstWithoutDuplicates() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            List<MessageInboxPage.Message> messages = inbox.messages();
            if (messages.size() < 2) {
                abort("The member has " + messages.size() + " message(s); an order needs at least two.");
            }
            Set<String> ids = new HashSet<>();
            for (MessageInboxPage.Message message : messages) {
                assertTrue(message.id().isEmpty() || ids.add(message.id()), "Message #" + message.id() + " is listed twice");
            }
            for (int i = 1; i < messages.size(); i++) {
                assertTrue(messages.get(i - 1).when().compareTo(messages.get(i).when()) >= 0,
                        "\"" + messages.get(i).title() + "\" (" + messages.get(i).when() + ") is newer than the message above it (" + messages.get(i - 1).when() + ")");
            }
            assertEquals(SEND_NOW_TITLE, messages.get(0).title(), "The message just delivered should be at the top");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-015: The header's unread indicator stays lit while any message is unread and clears once every one is read")
        void headerIndicatorFollowsEveryMessage() {
            deliverSendNow();
            ensure(broadcast());
            sendNowAndRead(BROADCAST_TITLE);
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            assertTrue(inbox.awaitDelivery(BROADCAST_TITLE), "\"" + BROADCAST_TITLE + "\" should reach the member too");
            assertTrue(inbox.hasUnreadIndicator(), "The header should show the unread indicator with two unread messages");
            inbox.openMessage(SEND_NOW_TITLE);
            inbox.back();
            assertTrue(inbox.hasUnreadIndicator(), "The header should stay lit while \"" + BROADCAST_TITLE + "\" is still unread");
            inbox.markAllAsRead();
            inbox.refresh();
            assertFalse(inbox.hasUnreadIndicator(), "The header indicator should clear once every message is read");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-016: A message's read state survives a refresh and a fresh sign-in")
        void readStatePersists() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            inbox.openMessage(SEND_NOW_TITLE);
            inbox.back();
            assertFalse(inbox.isUnread(SEND_NOW_TITLE), "The message should read as read once opened");
            inbox.refresh();
            assertFalse(inbox.isUnread(SEND_NOW_TITLE), "The read state should survive a refresh");
            extraSession.close();
            extraSession = null;
            MessageInboxPage again = memberInbox();
            assertTrue(again.isListed(SEND_NOW_TITLE), "The message should still be listed after a fresh sign-in");
            assertFalse(again.isUnread(SEND_NOW_TITLE), "The read state should survive a fresh sign-in");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-017: Switching the member to another wallet hides the notifications set up under the suite's currency")
        void otherWalletHidesTheseMessages() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            String other = CURRENCY.equalsIgnoreCase("HKD") ? "USDT" : "HKD";   // the shared QA account's other wallet
            String now = memberSite.useCurrency(other);
            if (!other.equalsIgnoreCase(now)) {
                abort("The member has no " + other + " wallet to switch to (it stayed on " + now + "), so the other wallet's Message Center cannot be read.");
            }
            inbox.open();
            String shown = inbox.currency();
            CaseLog.data("Message Center currency after the switch", shown.isEmpty() ? "not shown" : shown);
            assertEquals(other, shown, "Message Center should follow the member onto the " + other + " wallet");
            assertFalse(inbox.isListed(SEND_NOW_TITLE), "A notification set up under " + CURRENCY + " should not show on the " + other + " wallet");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-019: A delivered message keeps the Content as sent after the rule is edited")
        void deliveredContentIsFrozen() {
            String title = PREFIX + "Edited After Delivery";
            String changed = "Changed after delivery";
            deleteAfterwards(title);
            ensure(new Draft(title).broadcast());
            sendNowAndRead(title);
            MessageInboxPage inbox = inboxHolding(title);
            inbox.openMessage(title);
            String before = inbox.detailText();
            inbox.back();
            assertTrue(before.contains(CONTENT), "The delivered message should show the Content as sent");
            openList();
            NotificationFormPage edit = list.clickEdit(title);
            edit.setContent(TAB_ENGLISH, changed);   // the member reads the inbox in English
            edit.clickSave();
            inbox.refresh();
            inbox.openMessage(title);
            String after = inbox.detailText();
            inbox.back();
            CaseLog.data("Delivered message after the rule was edited", after);
            assertTrue(after.contains(CONTENT), "The delivered message should keep the Content it was sent with");
            assertFalse(after.contains(changed), "The delivered message should not follow the rule's later edit");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-CLI-020: Mark All as Read with nothing unread is a harmless no-op")
        void markAllAsReadTwice() {
            deliverSendNow();
            MessageInboxPage inbox = inboxHolding(SEND_NOW_TITLE);
            inbox.markAllAsRead();
            assertFalse(inbox.hasUnreadIndicator(), "Nothing should be unread after Mark All as Read");
            inbox.markAllAsRead();
            assertFalse(inbox.hasUnreadIndicator(), "A second Mark All as Read should leave everything read");
            assertTrue(inbox.isListed(SEND_NOW_TITLE), "Mark All as Read should remove nothing");
            for (MessageInboxPage.Message message : inbox.messages()) {
                assertFalse(message.unread(), "\"" + message.title() + "\" should still be read");
            }
        }
    }

    // =====================================================================
    // 12. REGRESSION & SCHEDULING INTEGRITY
    // =====================================================================

    @Nested
    @Order(110)
    @DisplayName("12. Regression & scheduling integrity")
    class Regression {

        /** Each Daily rule's Timer, as the list shows it ("12 AM"), filled by {@link #dailySends()}. */
        private final Map<String, String> dailyTimers = new HashMap<>();

        /** Every Daily rule's Sent entries over the last 30 days, grouped by title - Send Now pushes included. */
        private Map<String, List<HistoryRow>> dailySends() {
            openList();
            Set<String> daily = new HashSet<>();
            for (NotificationRow row : list.notifications()) {
                if (SCHEDULE_DAILY.equals(row.schedule())) {
                    daily.add(row.title());
                    dailyTimers.put(row.title(), row.timer());
                }
            }
            RecordHistoryPage history = list.openRecordHistory();
            history.filterDates(LocalDate.now(SCHEDULE_ZONE).minusDays(30).toString(), LocalDate.now(SCHEDULE_ZONE).toString());
            history.filterStatus(SENT);
            Map<String, List<HistoryRow>> byTitle = new HashMap<>();
            for (HistoryRow row : history.entries()) {
                if (daily.contains(row.title())) {
                    byTitle.computeIfAbsent(row.title(), k -> new ArrayList<>()).add(row);
                }
            }
            return byTitle;
        }

        /** "12 AM" -> 0, "03 PM" -> 15, "12 PM" -> 12; -1 for anything else (a Weekly "Sunday/08 PM", a blank). */
        private static int timerHour(String label) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d{1,2})\\s*(AM|PM)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(label == null ? "" : label);
            if (!m.find()) {
                return -1;
            }
            int hour = Integer.parseInt(m.group(1)) % 12;
            return m.group(2).equalsIgnoreCase("PM") ? hour + 12 : hour;
        }

        /** The hour of a Released At like "2026-09-30 10:45", or -1. */
        private static int releasedHour(HistoryRow row) {
            String at = row.releasedAt();
            return at.length() >= 13 && at.substring(11, 13).matches("\\d{2}") ? Integer.parseInt(at.substring(11, 13)) : -1;
        }

        /**
         * Si, 2026-09-30: only the entries at the rule's own Timer hour are the scheduler's. Send Now pushes
         * land at whatever hour the operator clicked - the suite's own runs put four in one morning hour - and
         * are not double sends, so they are left out.
         */
        private Map<String, List<HistoryRow>> scheduledSends(Map<String, List<HistoryRow>> sends) {
            Map<String, List<HistoryRow>> scheduled = new HashMap<>();
            int leftOut = 0;
            for (Map.Entry<String, List<HistoryRow>> rule : sends.entrySet()) {
                int hour = timerHour(dailyTimers.get(rule.getKey()));
                List<HistoryRow> atTimer = new ArrayList<>();
                for (HistoryRow row : rule.getValue()) {
                    // Released At is shown in Hong Kong Time; the Timer was set on the machine's clock (Si, 2026-09-30).
                    if (hour >= 0 && releasedHour(row) == timerHourInScheduleZone(hour)) {
                        atTimer.add(row);
                    } else {
                        leftOut++;
                    }
                }
                if (!atTimer.isEmpty()) {
                    scheduled.put(rule.getKey(), atTimer);
                }
            }
            CaseLog.data("Entries outside their rule's Timer hour (Send Now pushes), left out", String.valueOf(leftOut));
            return scheduled;
        }

        @Test
        @DisplayName("NOT-REG-001: A recurring notification's actual send time stays consistent across multiple days")
        void noDrift() {
            // Decided from history rather than by waiting three days: every Daily rule that has fired on three or
            // more days is checked for the same hour each time.
            Map<String, List<HistoryRow>> sends = scheduledSends(dailySends());   // Si, 2026-09-30: the Timer hour only, as NOT-REG-002
            int checked = 0;
            for (Map.Entry<String, List<HistoryRow>> rule : sends.entrySet()) {
                Set<String> days = new HashSet<>();
                Set<String> hours = new HashSet<>();
                for (HistoryRow row : rule.getValue()) {
                    days.add(row.releasedDate());
                    hours.add(row.releasedAt().length() >= 13 ? row.releasedAt().substring(11, 13) : "");
                }
                if (days.size() < 3) {
                    continue;
                }
                checked++;
                CaseLog.data(rule.getKey(), days.size() + " days, hours " + hours);
                assertEquals(1, hours.size(), "\"" + rule.getKey() + "\" should fire at the same hour every day; it fired at " + hours);
            }
            if (checked == 0) {
                abort("No Daily notification has fired on three or more days in the last 30, so drift cannot be judged yet.");
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-REG-002: No double-send occurs from a scheduler re-run or retry")
        void noDoubleSend() {
            // Decided from history: a scheduled Daily window must hold one entry. Only entries at the rule's own
            // Timer hour are the scheduler's (scheduledSends); Send Now pushes at other hours are not counted.
            Map<String, List<HistoryRow>> sends = scheduledSends(dailySends());
            if (sends.isEmpty()) {
                // Measured 2026-09-29: HKD Record History held 4 entries in 30 days, none from a Daily rule - the
                // suite's own Daily rules are deleted before their Timer comes round. So make one fire: a Daily rule
                // at the next hour, then exactly one Sent entry in that hour's window.
                String title = PREFIX + "Double Send";
                deleteAfterwards(title);
                String timer = nextHourLabel();
                ensure(new Draft(title).schedule(SCHEDULE_DAILY, timer));
                waitPastNextHour();
                int hour = timerHourInScheduleZone(timerHour(timer));
                List<HistoryRow> fired = historyToday().entriesFor(title).stream().filter(row -> releasedHour(row) == hour).toList();
                CaseLog.data("Entries for \"" + title + "\" in its " + timer + " window today", fired.toString());
                assertEquals(1, fired.size(), "One scheduled firing should log exactly one Record History entry, not " + fired.size());
                return;
            }
            for (Map.Entry<String, List<HistoryRow>> rule : sends.entrySet()) {
                Map<String, Integer> perWindow = new HashMap<>();
                for (HistoryRow row : rule.getValue()) {
                    String window = row.releasedAt().length() >= 13 ? row.releasedAt().substring(0, 13) : row.releasedAt();
                    perWindow.merge(window, 1, Integer::sum);
                }
                CaseLog.data("\"" + rule.getKey() + "\" at its " + dailyTimers.get(rule.getKey()) + " Timer", perWindow.toString());
                perWindow.forEach((window, count) -> assertEquals(1, (int) count,
                        "\"" + rule.getKey() + "\" was sent " + count + " times in the " + window + ":00 window"));
            }
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-REG-003: The very next scheduled send uses the most recently saved Content, with no propagation delay")
        void nextSendUsesLatestContent() {
            // AQ-09 (answered 2026-09-26).
            String title = PREFIX + "Content Change";
            deleteAfterwards(title);
            ensure(new Draft(title).schedule(SCHEDULE_DAILY, nextHourLabel()));
            NotificationFormPage edit = list.clickEdit(title);
            edit.setContent(TAB_CHINESE, "Updated before the firing");
            edit.clickSave();
            waitPastNextHour();
            RecordHistoryPage history = historyToday();
            List<HistoryRow> entries = history.entriesFor(title);
            assertFalse(entries.isEmpty(), "The scheduled firing should have logged an entry");
            history.openDetail(entries.get(0).id());
            assertEquals("Updated before the firing", history.sentContent(TAB_CHINESE)[1], "The firing should carry the latest saved Content");
        }

        /**
         * A Member Group the suite creates for itself, to be deleted under a rule. Agent Groups cannot be used: the
         * operator has no Agent management, and an agent is a member qualified in place, not something to throw away.
         * Member Management &gt; Member group can add and delete (2026-09-29), so the group side carries AQ-10.
         */
        private MemberGroupPage throwawayGroup(String name) {
            MemberGroupPage groups = new MemberGroupPage(sessionPage);
            Runnable earlier = restore;
            restore = () -> {
                if (earlier != null) {
                    earlier.run();
                }
                groups.deleteIfListed(name);
            };
            groups.ensure(name);
            assertTrue(groups.isListed(name), "The throwaway Member Group \"" + name + "\" should be created");
            return groups;
        }

        /** Deletes the throwaway group out from under the rule, then shows the list again. */
        private void deleteGroupUnderRule(MemberGroupPage groups, String group, String title) {
            openList();
            assertTrue(list.notification(title).memberGroup().contains(group),
                    "The precondition is \"" + title + "\" targeting \"" + group + "\"");
            groups.open();
            String said = groups.delete(group);
            assertFalse(groups.isListed(group), "Deleting a Member Group a rule targets should go through (AQ-10); the page said: " + said);
            openList();
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-REG-004: Deleting one of several targeted groups: the notification still fires, skipping only the deleted group")
        void deletedGroupIsSkipped() {
            // AQ-10 (answered 2026-09-26): the rule keeps firing to its remaining groups.
            String group = PREFIX + "REG Group A";
            String title = PREFIX + "Deleted Group";
            deleteAfterwards(title);
            MemberGroupPage groups = throwawayGroup(group);
            ensure(new Draft(title).members(List.of(TARGET_MEMBER_GROUP, group)));
            deleteGroupUnderRule(groups, group, title);
            assertTrue(list.isListed(title), "The rule should survive the deletion of one of its groups");
            NotificationRow row = list.notification(title);
            CaseLog.data("Rule after the group was deleted", "Member Group \"" + row.memberGroup() + "\", Status " + row.status());
            assertEquals(STATUS_ACTIVE, row.status(), "The rule should stay Active");
            HistoryRow sent = sendNowAndRead(title);
            CaseLog.data("Sent to", "Agent Group \"" + sent.agentGroup() + "\", Member Group \"" + sent.memberGroup() + "\"");
            assertTrue(sent.memberGroup().contains(TARGET_MEMBER_GROUP), "The send should still reach the remaining group " + TARGET_MEMBER_GROUP);
            assertFalse(sent.memberGroup().contains(group), "The send should skip the deleted group \"" + group + "\"");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-REG-005: Deleting a notification's sole targeted Agent/Member Group: the schedule still executes rather than being blocked or silently cancelled")
        void soleGroupDeleted() {
            // AQ-10 (answered 2026-09-26): the schedule still executes, with no valid target.
            String group = PREFIX + "REG Group B";
            String title = PREFIX + "Sole Group";
            deleteAfterwards(title);
            MemberGroupPage groups = throwawayGroup(group);
            ensure(new Draft(title).members(List.of(group)));
            deleteGroupUnderRule(groups, group, title);
            assertTrue(list.isListed(title), "The rule should not be silently cancelled when its only Member Group is deleted");
            NotificationRow row = list.notification(title);
            CaseLog.data("Rule after its only group was deleted", "Member Group \"" + row.memberGroup() + "\", Status " + row.status()
                    + ", Schedule " + row.schedule() + " " + row.timer());
            assertEquals(STATUS_ACTIVE, row.status(), "The rule should stay Active rather than be blocked");
            assertEquals(SCHEDULE_DAILY, row.schedule(), "The rule's schedule should be kept");
            HistoryRow sent = sendNowAndRead(title);
            CaseLog.data("Executed with", "Member Group \"" + sent.memberGroup() + "\", status " + sent.status());
        }

        @Test
        @DisplayName("NOT-REG-006: Confirming no migration or coexistence testing is required for the old customer-service-based mechanism")
        void legacyIsOutOfScope() {
            // AQ-12 (answered 2026-09-26): a scoping decision recorded by the workbook, not a behaviour. The row passes by recording it.
            CaseLog.check("The legacy customer-service notification mechanism is out of scope (AQ-12); no migration test is needed.");
        }

        @Test
        @DisplayName("NOT-REG-007: Two admin sessions editing the same notification at nearly the same time")
        void concurrentEdits() {
            if (ALT_USERNAME.isBlank() || ALT_PASSWORD.isBlank()) {
                abort("Needs a second operator account (test.alt.username / test.alt.password in Config/): signing the same account in "
                        + "twice evicts the first session. Still a \"confirm whether\" row - overwrite, merge, or conflict.");
            }
            String title = PREFIX + "Concurrent";
            deleteAfterwards(title);
            ensure(new Draft(title));
            Page second = signedInAs("second operator", ALT_USERNAME, ALT_PASSWORD, "test.alt");
            NotificationListPage otherList = new NotificationListPage(second);
            openList(otherList);
            NotificationFormPage sessionB = otherList.clickEdit(title);
            NotificationFormPage sessionA = list.clickEdit(title);
            sessionA.setContent(TAB_CHINESE, "Session A content");
            sessionA.clickSave();
            sessionB.chooseOptional(MEMBER_GROUP);
            sessionB.removeAll(MEMBER_GROUP);
            sessionB.target(MEMBER_GROUP, someGroups(MEMBER_GROUP, 2).get(1));
            sessionB.clickSave();
            openList();
            NotificationFormPage result = list.clickEdit(title);
            String content = result.content(TAB_CHINESE);
            List<String> members = result.selectedNames(MEMBER_GROUP);
            CaseLog.data("After both saves (open question)", "content \"" + content + "\", Member Groups " + members
                    + ", session B said \"" + sessionB.lastMessage() + "\"");
            assertTrue(content.equals("Session A content") || content.equals(CONTENT), "The saved content should be one of the two versions, never a mix");
            result.cancel();
        }

        // NOT-REG-010: added 2026-09-30 from Si's review, numbered on from NOT-REG-007. NOT-REG-008 (a Weekly rule fires only on
        // its Week day) and NOT-REG-009 (Timer hours under another interface language and machine clock) need days of observation
        // and are not written.

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-REG-010: Created At agrees with the header clock and with Hong Kong Time, the clock the schedules run on")
        void createdAtFollowsTheHeaderClock() {
            String title = PREFIX + "Clock Check";
            deleteAfterwards(title);
            list.deleteIfListed(title);
            openList();
            assertTrue(create(new Draft(title).status(STATUS_INACTIVE)), "The Inactive rule should save");
            String createdAt = list.notification(title).createdAt();
            String clock = shell.headerClock();
            LocalDateTime hongKong = LocalDateTime.now(SCHEDULE_ZONE);
            CaseLog.data("Created At / header clock / Hong Kong Time", createdAt + " / " + (clock.isEmpty() ? "(not shown)" : clock) + " / " + hongKong.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            LocalDateTime created = LocalDateTime.parse(createdAt.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            assertTrue(Math.abs(Duration.between(created, hongKong).toMinutes()) <= 5, "Created At should be Hong Kong Time; it is " + createdAt + " while Hong Kong reads " + hongKong);
            if (!clock.isEmpty()) {
                LocalDateTime header = LocalDateTime.parse(clock, DateTimeFormatter.ofPattern("yyyy-MMMM-d HH:mm", Locale.ENGLISH));
                assertTrue(Math.abs(Duration.between(created, header).toMinutes()) <= 5, "Created At should agree with the header clock; it is " + createdAt + " while the header reads " + clock);
            }
        }

        /** The Week day the scheduler will see at its next run: the next whole hour on the machine's clock, read in Hong Kong Time. */
        private static String firingWeekDay() {
            return java.time.ZonedDateTime.now(TIMER_ZONE).plusHours(1).withZoneSameInstant(SCHEDULE_ZONE)
                    .getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        }

        // NOT-REG-011: added 2026-10-01 from Si's question - a Weekly rule has fired today, then the operator moves its Timer
        // to a later hour the same day, and the scheduler sends it again at that hour. Si's reading: Weekly means "fire every
        // week at the saved day and hour", not "at most once a week", so the second send is correct - the admin changed the
        // definition and the scheduler ran it. What would be a defect is the save itself re-sending, or two entries in the
        // moved hour's window. Only the Timer is changed here; NOT-REG-003 covers a Content edit between firings.
        @Test
        @Tag("creates-data")
        @DisplayName("NOT-REG-011: Moving a Weekly rule's Timer to a later hour after it has already fired today sends it again at the new hour, exactly once")
        void weeklyMovedLaterTheSameDayFiresAgain() {
            if (!WAIT_FOR_SCHEDULE) {
                abort("This row needs two scheduler runs - one at the Weekly rule's first Timer, one at the hour it is moved to - so it "
                        + "waits up to two hours. Run with -Dtest.notification.waitForSchedule=true to let it wait.");
            }
            String title = PREFIX + "Weekly Moved Later";
            deleteAfterwards(title);
            // Record History outlives the rule (NOT-RHL-006): a leftover rule has already fired under a Timer this run did not
            // set, and earlier runs today left entries under the same title - so the rule is remade and only new entries count.
            openList();
            list.deleteIfListed(title);
            Set<String> before = historyToday().entriesFor(title).stream().map(HistoryRow::id).collect(Collectors.toSet());

            String firstTimer = nextHourLabel();
            String weekDay = firingWeekDay();
            Draft d = new Draft(title).schedule(SCHEDULE_WEEKLY, firstTimer);
            d.weekDay = weekDay;
            assertTrue(create(d), "A Weekly, Active rule for " + weekDay + " at " + firstTimer + " should save");
            assertEquals(weeklyTimer(weekDay, firstTimer), list.notification(title).timer(), "The list should show the Weekly rule's day and first hour");

            waitPastNextHour();
            List<HistoryRow> afterFirst = newEntries(title, before);
            CaseLog.data("Record History after the first Timer (" + firstTimer + ")", describe(afterFirst));
            assertEquals(1, afterFirst.size(), "The Weekly rule should fire once at its " + firstTimer + " Timer before it is moved");

            String secondTimer = nextHourLabel();
            if (!weekDay.equals(firingWeekDay())) {
                abort("The next scheduler run falls on " + firingWeekDay() + " in Hong Kong Time, not " + weekDay + ", so the moved hour "
                        + "would no longer be the same day as the first send. Run this row earlier in the day.");
            }
            openList();
            NotificationFormPage edit = list.clickEdit(title);
            edit.setTimer(secondTimer);
            edit.clickSave();
            openList();
            NotificationRow moved = list.notification(title);
            CaseLog.data("After moving the Timer to " + secondTimer, "Schedule " + moved.schedule() + ", Timer " + moved.timer() + ", Status " + moved.status());
            assertEquals(weeklyTimer(weekDay, secondTimer), moved.timer(), "The list should show the moved Timer");
            assertEquals(STATUS_ACTIVE, moved.status(), "Moving the Timer should leave the rule Active");
            CaseLog.step("Waiting one minute to see whether the save itself re-sends.");
            sessionPage.waitForTimeout(60_000);
            assertEquals(1, newEntries(title, before).size(), "Saving the moved Timer must not send anything by itself - the next send belongs to the " + secondTimer + " run");

            waitPastNextHour();
            List<HistoryRow> afterSecond = newEntries(title, before);
            CaseLog.data("Record History after the moved Timer (" + secondTimer + ")", describe(afterSecond));
            assertEquals(2, afterSecond.size(), "Moving the Timer to " + secondTimer + " should make the rule fire again that hour: two sends today, not " + afterSecond.size());
            int hour = timerHourInScheduleZone(timerHour(secondTimer));
            List<HistoryRow> atMovedHour = afterSecond.stream().filter(e -> releasedHour(e) == hour).toList();
            assertEquals(1, atMovedHour.size(), "Exactly one entry should be logged in the moved " + secondTimer + " window, not " + atMovedHour.size());
            assertEquals(SENT, atMovedHour.get(0).status(), "The send at the moved Timer should be logged as Sent");
            openList();
            assertEquals(STATUS_ACTIVE, list.notification(title).status(), "A Weekly rule stays Active after firing (NOT-CRT-025)");
        }

        /** Today's Record History entries for a title that this run's rule added - those not in {@code before}. */
        private static List<HistoryRow> newEntries(String title, Set<String> before) {
            return historyToday().entriesFor(title).stream().filter(e -> !before.contains(e.id())).toList();
        }
    }

    // =====================================================================
    // 4. DELETE NOTIFICATION - last, because it destroys
    // =====================================================================

    @Nested
    @Order(120)
    @DisplayName("4. Delete notification")
    class DeleteNotification {

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-DEL-001: Clicking the delete icon prompts for confirmation before removal")
        void deleteAsksFirst() {
            // AQ-04 (answered 2026-09-26): the platform's own confirmation.
            String title = PREFIX + "Delete Me";
            deleteAfterwards(title);
            ensure(new Draft(title));
            list.clickDelete(title);
            assertTrue(list.isDeleteDialogVisible(), "Clicking Delete should raise a confirmation first");
            CaseLog.data("Confirmation", list.deleteDialogText());
            list.cancelDelete();
            openList();
            assertTrue(list.isListed(title), "Nothing should be removed until the confirmation is answered");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-DEL-002: Confirming deletion removes the notification from the list")
        void confirmRemoves() {
            String title = PREFIX + "Delete Confirmed";
            deleteAfterwards(title);
            ensure(new Draft(title));
            list.clickDelete(title);
            list.confirmDelete();
            openList();
            assertFalse(list.isListed(title), "The notification should no longer be listed");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-DEL-003: Cancelling the confirmation leaves the notification untouched")
        void cancelKeeps() {
            String title = PREFIX + "Delete Cancelled";
            deleteAfterwards(title);
            ensure(new Draft(title));
            NotificationRow before = list.notification(title);
            list.clickDelete(title);
            list.cancelDelete();
            openList();
            assertEquals(before, list.notification(title), "The notification should remain, fully unchanged");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-DEL-004: Deleting a notification rule that already has sent history")
        void historyAfterDelete() {
            // Still a "confirm whether" row: does the audit trail survive the rule? Either answer must be whole.
            String title = PREFIX + "Delete With History";
            deleteAfterwards(title);
            ensure(new Draft(title));
            HistoryRow sent = sendNowAndRead(title);
            openList();
            list.clickDelete(title);
            list.confirmDelete();
            List<HistoryRow> after = historyToday().entriesFor(title);
            boolean kept = after.stream().anyMatch(e -> e.id().equals(sent.id()));
            CaseLog.data("Record History after the rule was deleted (open question)", kept ? "kept as an audit trail" : "removed with the rule");
            assertTrue(kept || after.isEmpty(), "History should be either kept or removed with the rule, not partly");
        }

        // NOT-DEL-005 .. NOT-DEL-006: added 2026-09-30 from Si's review, numbered on from NOT-DEL-004. No workbook rows yet.

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-DEL-005: Deleting an Active Daily rule stops its future firings")
        void deleteStopsFiring() {
            String title = PREFIX + "Delete Before Firing";
            deleteAfterwards(title);
            ensure(new Draft(title).schedule(SCHEDULE_DAILY, nextHourLabel()));
            list.clickDelete(title);
            list.confirmDelete();
            openList();
            assertFalse(list.isListed(title), "The rule should be gone before its Timer");
            waitPastNextHour();
            assertTrue(historyToday().entriesFor(title).isEmpty(), "No Record History entry should appear for a rule deleted before its Timer");
        }

        @Test
        @Tag("creates-data")
        @DisplayName("NOT-DEL-006: A message already delivered stays in the member's inbox after its rule is deleted")
        void deliveredMessageOutlivesTheRule() {
            String title = PREFIX + "Delete After Delivery";
            deleteAfterwards(title);
            ensure(new Draft(title).broadcast());
            sendNowAndRead(title);
            MessageInboxPage inbox = memberInbox();
            assertTrue(inbox.awaitDelivery(title), "\"" + title + "\" should reach the member within client.messages.deliveryWaitSeconds");
            openList();
            list.clickDelete(title);
            list.confirmDelete();
            openList();
            assertFalse(list.isListed(title), "The rule should be deleted");
            inbox.refresh();
            assertTrue(inbox.isListed(title), "The delivered message should still be in the member's inbox after the rule is deleted");
        }
    }
}
