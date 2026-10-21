package org.sengmean.tests.tool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.sengmean.base.BaseTest;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.page.client.MessageCenterPage;
import org.sengmean.page.client.MessageCenterPage.Message;
import org.sengmean.report.CaseLog;
import org.sengmean.support.Device;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>The Message Center (Notification Center) on the client platform's new UI</b>, on the HK brand
 * ({@code client.hk.url}) - what a visitor and the testing member see of the notifications the
 * back office sends, and whether the screen holds up on every device.
 *
 * <h2>What the new UI shows (measured 2026-10-02)</h2>
 * See {@link MessageCenterPage}: {@code /{locale}/messages} with a heading, a Mark All as Read
 * control enabled only while something is unread, four tabs (All, System, Deposit, Withdraw), rows
 * grouped under Today / Yesterday with a red dot while unread, "No messages yet" when empty and
 * "All messages shown" at the end; a message's own page at {@code /messages/{id}} headed
 * "Message Details" under a Back control. The Message Center is for signed-in members only (Si's
 * ruling, 2026-10-02): a visitor must be asked to log in or sent away. On that day the site showed
 * a visitor the empty shell instead, which {@code NOT-CLI-021} and {@code NOT-CLI-033}/{@code 034}
 * report as a finding.
 *
 * <h2>The messages are whatever the member has</h2>
 * As with the Other Category rows, nothing here expects a message to exist. The rows read what the
 * list draws and validate it: a member with no message validates the empty state and passes; one
 * with messages validates each row, the tabs' counts, opening a message and Mark All as Read.
 *
 * <h2>Devices and ids</h2>
 * The compatibility rows run the same walk as the signed-in member on {@link Device}'s five
 * profiles, WebKit for the Mac and iOS ones. The ids are project-added to the workbook's
 * Notification tab, numbered on from its last client row ({@code NOT-CLI-020}), and listed in
 * {@code added-cases/notification.tsv}.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(120)
@DisplayName("Notification - Message Center on the client (HK, new UI)")
public class UiNotificationCenterClientHK extends BaseTest {

    static final String HK_URL = ConfigDataProvider.get("client.hk.url", "https://test-v2.138hk.vip").replaceAll("/+$", "");
    static final String HK_USERNAME = ConfigDataProvider.get("client.hk.username", "");
    static final String HK_PASSWORD = ConfigDataProvider.get("client.hk.password", "");
    static final String HK_CAPTCHA = ConfigDataProvider.get("client.hk.captcha", "1111");
    /** A second HK-brand account, for the cross-member access row (NOT-CLI-035). */
    static final String HK_OUTSIDER_USERNAME = ConfigDataProvider.get("client.hk.outsider.username", "");
    static final String HK_OUTSIDER_PASSWORD = ConfigDataProvider.get("client.hk.outsider.password", "");

    static final String ENGLISH = "en";
    static final List<String> TABS = List.of("All", "System", "Deposit", "Withdraw");

    private static final Path SHOTS = Path.of(System.getProperty("report.shots", "target/case-screenshots"));

    private static Browser webkit;
    private BrowserContext deviceContext;

    /** The PC rows' Message Center, on the per-test guest page. */
    private MessageCenterPage center;

    @BeforeEach
    void openCenter() {
        center = new MessageCenterPage(page(), HK_URL);
    }

    @AfterEach
    void closeDevice() {
        if (deviceContext != null) {
            deviceContext.close();
            deviceContext = null;
        }
    }

    @AfterAll
    static void closeWebkit() {
        if (webkit != null) {
            webkit.close();
            webkit = null;
        }
    }

    // =====================================================================
    // Shared steps
    // =====================================================================

    /** Signs the testing member in on the home page in the New UI, or aborts naming what is missing. */
    private static void signInAsMember(MessageCenterPage on) {
        if (HK_USERNAME.isBlank() || HK_PASSWORD.isBlank()) {
            abort("No member account is configured for the HK client: set client.hk.username / client.hk.password in Config/.");
        }
        on.shell().openNewUi(ENGLISH);
        if (!on.shell().signIn(HK_USERNAME, HK_PASSWORD, HK_CAPTCHA)) {
            abort("The testing member " + HK_USERNAME + " could not sign in, so the Message Center cannot be read as a member.");
        }
    }

    /** What the Message Center's shell must always show, signed in or not, on any device. */
    private static void assertShell(MessageCenterPage on, String where) {
        assertTrue(on.isOpen(), "The Message Center should be open " + where + "; the page is " + on.heading());
        assertEquals(TABS, on.tabs(), "The Message Center should offer its four tabs " + where);
        assertEquals("All", on.activeTab(), "The Message Center should open on the All tab " + where);
        assertTrue(on.isMarkAllOffered(), "Mark All as Read should be offered " + where);
        assertTrue(on.isBackOffered(), "A Back control should be offered " + where);
    }

    /** The list as drawn, validated against itself: rows have a title and a time; the empty state when there are none. */
    private static List<Message> assertListConsistent(MessageCenterPage on, String where) {
        List<Message> rows = on.messages();
        if (rows.isEmpty()) {
            assertTrue(on.isEmpty(), "With no message row drawn, the list should say \"No messages yet\" " + where);
            CaseLog.data("Messages " + where, "none - the member has no message right now, and the list says so");
            return rows;
        }
        assertFalse(on.isEmpty(), "The list draws rows, so it should not say \"No messages yet\" " + where);
        for (Message row : rows) {
            assertFalse(row.title().isBlank(), "Every message row should carry a title " + where + ": " + row);
            assertFalse(row.time().isBlank(), "Every message row should carry a time " + where + ": " + row);
        }
        CaseLog.data("Messages " + where, rows.size() + " row(s), " + rows.stream().filter(Message::unread).count() + " unread; "
                + rows.stream().map(Message::title).limit(5).toList() + (rows.size() > 5 ? " ..." : ""));
        return rows;
    }

    // =====================================================================
    // 1. MESSAGE CENTER - PC
    // =====================================================================

    @Nested
    @Order(10)
    @DisplayName("1. Message Center (PC)")
    class Center {

        @Test
        @Tag("known-defect")
        @DisplayName("NOT-CLI-021: A visitor who opens the Message Center is kept out - asked to log in or sent away - and sees no message list")
        void visitorIsKeptOut() {
            // Si's ruling (2026-10-02): the Message Center is for signed-in members only. On that day the
            // site showed a visitor the empty shell instead, so this row is red until it is fixed.
            center.shell().openNewUi(ENGLISH);
            center.open();
            String keptOut = center.visitorKeptOut();
            CaseLog.data("A visitor opening /" + MessageCenterPage.ROUTE, keptOut.isEmpty() ? "shown the Message Center (" + center.heading() + ", tabs " + center.tabs() + ")" : keptOut);
            assertFalse(keptOut.isEmpty(), "A visitor should not reach the Message Center - they should be asked to log in or sent away - but the list is shown");
        }

        @Test
        @DisplayName("NOT-CLI-022: A signed-in member's Message Center lists their messages with title, time and an unread mark, and each tab shows no more than All")
        void memberSeesTheirMessages() {
            signInAsMember(center);
            center.open();   // by address; the header's control is NOT-CLI-030's subject
            assertShell(center, "for the member");
            int all = center.loadWholeList();
            List<Message> rows = assertListConsistent(center, "under All");
            assertEquals(rows.size(), all, "Scrolling to the end should leave every row drawn");
            if (!rows.isEmpty()) {
                assertTrue(center.isEndShown(), "The end of the list should say \"All messages shown\"");
                CaseLog.data("Day labels", center.dayLabels().toString());
            }
            for (String tab : TABS.subList(1, TABS.size())) {
                center.selectTab(tab);
                assertEquals(tab, center.activeTab(), "The " + tab + " tab should become the selected one");
                int under = center.loadWholeList();
                List<Message> filtered = assertListConsistent(center, "under " + tab);
                assertTrue(filtered.size() <= rows.size(), "The " + tab + " tab should list no more than All does, but lists " + under);
            }
            center.selectTab("All");
            assertEquals("All", center.activeTab(), "All should be selectable again");
        }

        @Test
        @DisplayName("NOT-CLI-023: Opening a message shows its details under a Back control, Back returns to the list, and the message is no longer unread")
        void memberOpensAMessage() {
            signInAsMember(center);
            center.open();
            assertShell(center, "for the member");
            List<Message> rows = assertListConsistent(center, "under All");
            if (rows.isEmpty()) {
                return;   // the empty state was validated; there is no message to open until one is sent
            }
            int index = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).unread()) {
                    index = i;
                    break;
                }
            }
            Message opened = rows.get(index);
            center.openMessage(index);
            assertTrue(center.isMessageOpen(), "The message's own page should open, headed \"Message Details\"; the address is " + page().url());
            CaseLog.data("Opened message", "\"" + opened.title() + "\" - page title \"" + center.messageTitle() + "\", body " + center.messageBody().length() + " chars");
            assertTrue(center.messageTitle().contains(opened.title()) || opened.title().contains(center.messageTitle()),
                    "The message page should be headed with the row's title \"" + opened.title() + "\", not \"" + center.messageTitle() + "\"");
            assertFalse(center.messageBody().isBlank(), "The message page should show the message's content");
            center.back();
            assertTrue(center.isOpen(), "Back should return to the Message Center list; the address is " + page().url());
            List<Message> after = center.messages();
            assertTrue(after.size() > index, "The list should still hold the opened message after Back");
            if (opened.unread()) {
                assertFalse(after.get(index).unread(), "\"" + opened.title() + "\" was unread and should lose its mark once opened");
            } else {
                CaseLog.data("Unread mark", "the opened message was already read, so no mark could clear");
            }
        }

        @Test
        @DisplayName("NOT-CLI-024: Mark All as Read is enabled only while something is unread, and pressing it clears every unread mark")
        void memberMarksAllAsRead() {
            signInAsMember(center);
            center.open();
            assertShell(center, "for the member");
            center.loadWholeList();
            long unread = center.unreadCount();
            boolean enabled = center.isMarkAllEnabled();
            CaseLog.data("Before", unread + " unread, Mark All as Read " + (enabled ? "enabled" : "disabled"));
            assertEquals(unread > 0, enabled, "Mark All as Read should be enabled exactly when a message is unread (" + unread + " unread)");
            if (unread == 0) {
                return;   // nothing to mark; the disabled state was validated
            }
            center.markAllAsRead();
            center.loadWholeList();
            assertEquals(0, center.unreadCount(), "After Mark All as Read no row should carry the unread mark");
            assertFalse(center.isMarkAllEnabled(), "Mark All as Read should be disabled once nothing is unread");
        }

        @Test
        @DisplayName("NOT-CLI-030: The header's message control takes a signed-in member to the Message Center, and its badge agrees with the unread rows")
        void headerControlLeadsToTheCenter() {
            signInAsMember(center);
            String badge = center.headerBadge();
            boolean offered = center.isHeaderControlOffered();
            CaseLog.data("Header message control", offered ? "offered" + (badge.isEmpty() ? ", no badge" : ", badge " + badge) : "not offered");
            assertTrue(offered, "A signed-in member's header should offer a control leading to the Message Center");
            // Measured 2026-10-05: the envelope opens the Message Center as a popover in the header
            // (heading, Mark All as Read, the four tabs, the rows) rather than navigating to the list.
            // Either is "leading to the Message Center"; what it opened is recorded.
            String opened = center.openFromHeader();
            CaseLog.data("The header control opened", opened.isEmpty() ? "nothing - still on " + page().url() : opened);
            if (opened.equals("popover")) {
                assertTrue(center.headerPopoverHeading().toLowerCase().contains("message center"),
                        "The header's popover should be headed Message Center, not \"" + center.headerPopoverHeading() + "\"");
                assertEquals(TABS, center.headerPopoverTabs(), "The header's Message Center popover should offer the four tabs");
                center.closeHeaderPopover();
                center.open();
                assertShell(center, "for the member");
            } else {
                assertTrue(opened.equals("page"), "The header's control should open the Message Center - as the list or as its popover - but " + (opened.isEmpty() ? "did neither" : "it was opened by address instead"));
                assertShell(center, "from the header");
            }
            center.loadWholeList();
            long unread = center.unreadCount();
            if (badge.matches("\\d+")) {
                assertEquals(unread, Long.parseLong(badge), "The header badge should count the unread messages");
            } else if (badge.equals("dot")) {
                assertTrue(unread > 0, "The header shows an unread dot, so at least one message should be unread");
            } else {
                CaseLog.data("Header badge against the list", "no badge shown; " + unread + " unread in the list"
                        + (unread > 0 ? " - the header does not announce them" : ""));
            }
        }

        @Test
        @DisplayName("NOT-CLI-031: Every message belongs to exactly one of System, Deposit and Withdraw - the three tabs together list what All lists")
        void tabsPartitionTheMessages() {
            signInAsMember(center);
            center.open();
            assertShell(center, "for the member");
            int all = center.loadWholeList();
            List<Message> allRows = assertListConsistent(center, "under All");
            if (allRows.isEmpty()) {
                for (String tab : TABS.subList(1, TABS.size())) {
                    center.selectTab(tab);
                    assertTrue(center.isEmpty(), "With no message under All, the " + tab + " tab should say \"No messages yet\" too");
                }
                return;
            }
            int sum = 0;
            for (String tab : TABS.subList(1, TABS.size())) {
                center.selectTab(tab);
                int under = center.loadWholeList();
                List<Message> rows = assertListConsistent(center, "under " + tab);
                assertEquals(rows.size(), under, "Scrolling the " + tab + " tab to its end should leave every row drawn");
                for (Message row : rows) {
                    assertTrue(allRows.stream().anyMatch(a -> a.title().equals(row.title()) && a.time().equals(row.time())),
                            "\"" + row.title() + "\" under " + tab + " should also be listed under All");
                }
                sum += rows.size();
            }
            CaseLog.data("Rows under the three tabs together", sum + " against " + all + " under All");
            assertEquals(all, sum, "System, Deposit and Withdraw together should list exactly what All lists - each message in one tab");
        }

        @Test
        @DisplayName("NOT-CLI-032: Under Traditional Chinese the Message Center keeps its structure - four tabs, the same rows - with its labels translated")
        void centerFollowsTheViewersLanguage() {
            signInAsMember(center);
            center.open();
            assertShell(center, "in English");
            int english = center.loadWholeList();
            List<String> englishTabs = center.tabs();
            center.shell().openNewUi("zh-TW");
            assertEquals("hk", center.shell().localeFromUrl(), "The client should be on its Traditional Chinese locale");
            center.open();
            assertTrue(center.tabs().size() == 4, "The Message Center should still offer four tabs under 繁體; it offers " + center.tabs());
            CaseLog.data("Labels under 繁體", "heading \"" + center.heading() + "\", tabs " + center.tabs());
            assertFalse(center.heading().equalsIgnoreCase("Message Center") && center.tabs().equals(englishTabs),
                    "Under 繁體 the heading and tabs should be translated, but they still read " + center.heading() + " " + center.tabs());
            int chinese = center.loadWholeList();
            assertEquals(english, chinese, "The same messages should be listed under 繁體 as under English");
            center.shell().openNewUi(ENGLISH);
        }

        @Test
        @DisplayName("NOT-CLI-033: After signing out, the Message Center is no longer reachable - the former member is kept out like any visitor")
        void signingOutClosesTheCenter() {
            signInAsMember(center);
            center.open();
            assertShell(center, "for the member");
            center.shell().openNewUi(ENGLISH);
            assertTrue(center.shell().signOut(), "The member should be able to sign out from the header");
            center.open();
            String keptOut = center.visitorKeptOut();
            CaseLog.data("After signing out, /" + MessageCenterPage.ROUTE, keptOut.isEmpty() ? "still shows the Message Center" : keptOut);
            assertFalse(keptOut.isEmpty(), "After signing out, the Message Center should not be shown - the visitor should be asked to log in or sent away");
        }

        @Test
        @DisplayName("NOT-CLI-034: A message's own address opened without being signed in is refused, not shown")
        void deepLinkIsRefusedToAVisitor() {
            signInAsMember(center);
            center.open();
            assertShell(center, "for the member");
            List<Message> rows = assertListConsistent(center, "under All");
            if (rows.isEmpty()) {
                return;   // no message, so no address to deep-link; the empty state was validated
            }
            center.openMessage(0);
            assertTrue(center.isMessageOpen(), "The message's own page should open; the address is " + page().url());
            String address = page().url();
            center.shell().openNewUi(ENGLISH);
            assertTrue(center.shell().signOut(), "The member should be able to sign out from the header");
            center.openMessageByAddress(address);
            String keptOut = center.visitorKeptOut();
            CaseLog.data("The message's address as a visitor", keptOut.isEmpty() ? "shows the message \"" + center.messageTitle() + "\"" : keptOut);
            assertFalse(keptOut.isEmpty(), "A message's address should be refused to a visitor, but " + address + " shows the message");
        }

        @Test
        @DisplayName("NOT-CLI-035: A signed-in member cannot open another member's message by its own address")
        void anotherMembersMessageIsRefused() {
            // The client's own test matrix for this screen (TC-MI-EDG-004, read 2026-10-06): "As B,
            // open A's detail URL/ID" should answer "Access denied / not found; A's data not shown."
            // This is distinct from NOT-CLI-034, which is a visitor with no session at all; here the
            // second account IS signed in, just as the wrong member.
            if (HK_OUTSIDER_USERNAME.isBlank() || HK_OUTSIDER_PASSWORD.isBlank()) {
                abort("No second HK client account is configured to act as the outsider: set "
                        + "client.hk.outsider.username / client.hk.outsider.password in Config/.");
            }
            signInAsMember(center);
            center.open();
            assertShell(center, "for the member");
            List<Message> rows = assertListConsistent(center, "under All");
            if (rows.isEmpty()) {
                return;   // no message belongs to the member, so there is nothing an outsider could reach
            }
            center.openMessage(0);
            assertTrue(center.isMessageOpen(), "The message's own page should open; the address is " + page().url());
            String address = page().url();
            String ownersTitle = center.messageTitle();

            center.shell().openNewUi(ENGLISH);
            assertTrue(center.shell().signOut(), "The member should be able to sign out from the header");
            assertTrue(center.shell().signIn(HK_OUTSIDER_USERNAME, HK_OUTSIDER_PASSWORD, HK_CAPTCHA),
                    "The outsider " + HK_OUTSIDER_USERNAME + " could not sign in, so this row cannot check what they are shown.");
            center.openMessageByAddress(address);

            boolean showsOwnersMessage = center.isMessageOpen() && ownersTitle.equals(center.messageTitle());
            String outcome = !center.isMessageOpen() ? "the message page did not open; the address is " + page().url()
                    : showsOwnersMessage ? "shows the owner's message \"" + ownersTitle + "\""
                    : "shows something else (\"" + center.messageTitle() + "\")";
            CaseLog.data("The message's address as a different signed-in member", outcome);
            assertFalse(showsOwnersMessage, "A member's message should not be reachable by another signed-in member, but "
                    + address + " showed \"" + ownersTitle + "\" to " + HK_OUTSIDER_USERNAME);
        }
    }

    // =====================================================================
    // 2. RESPONSIVE AND COMPATIBILITY - one row per device, as the member
    // =====================================================================

    @Nested
    @Order(20)
    @DisplayName("2. Responsive and compatibility")
    class Devices {

        @Test
        @DisplayName("NOT-CLI-025: The Message Center renders and works for a member on a PC (Windows, Chrome, 1920x1080)")
        void onPc() {
            deviceWalk(Device.DESKTOP_PC);
        }

        @Test
        @DisplayName("NOT-CLI-026: The Message Center renders and works for a member on a Mac (Safari/WebKit, 1440x900)")
        void onMac() {
            deviceWalk(Device.DESKTOP_MAC);
        }

        @Test
        @DisplayName("NOT-CLI-027: The Message Center renders and works for a member on an iPad (Safari/WebKit, 820x1180, touch)")
        void onIpad() {
            deviceWalk(Device.IPAD);
        }

        @Test
        @DisplayName("NOT-CLI-028: The Message Center renders and works for a member on an iPhone (Safari/WebKit, 390x844, touch)")
        void onIphone() {
            deviceWalk(Device.IPHONE);
        }

        @Test
        @DisplayName("NOT-CLI-029: The Message Center renders and works for a member on an Android phone (Chrome, 412x915, touch)")
        void onAndroid() {
            deviceWalk(Device.ANDROID);
        }
    }

    private void deviceWalk(Device device) {
        Page on = openOn(device);
        MessageCenterPage ui = new MessageCenterPage(on, HK_URL);
        try {
            walk(on, ui, device);
        } catch (Throwable failed) {
            capture(on, device, "failed");
            throw failed;
        }
    }

    /**
     * The same walk on every device, as the member: the Message Center's shell in place and within
     * the screen, the tab row readable (scrolling sideways on a phone rather than cutting off), every
     * row fitting, comfortable tap targets on touch devices, a message opened and its page fitting,
     * Back returning to the list.
     */
    private void walk(Page on, MessageCenterPage ui, Device device) {
        signInAsMember(ui);
        ui.open();
        int width = ui.viewportWidth();
        CaseLog.data("Device", device + " in " + device.engine + ", window " + width + "px wide");
        assertShell(ui, "on " + device.label);
        assertEquals(0, ui.horizontalOverflow(), "Nothing should run off the side of the Message Center on " + device.label);
        assertTrue(ui.headerOnOneLine(), "The heading and Mark All as Read should share one line within the screen on " + device.label);
        CaseLog.data("Tab row on " + device.label, ui.tabsOverflow() ? "wider than its box - scrolls sideways" : "fits in one row");
        if (!ui.tabsOverflow()) {
            assertTrue(ui.tabsWithinScreen(), "Every tab should be within the screen on " + device.label);
        }
        if (device.touch) {
            int smallest = ui.smallestTapTarget();
            CaseLog.data("Smallest tap target on " + device.label, smallest + "px");
            assertTrue(smallest >= 36, "Tabs, Back and Mark All as Read should be comfortable tap targets (36px or more) on " + device.label + ", not " + smallest + "px");
        }
        List<Message> rows = assertListConsistent(ui, "on " + device.label);
        assertTrue(rows.stream().allMatch(Message::inView), "Every message row should fit within the screen's width on " + device.label);
        capture(on, device, "list");

        if (rows.isEmpty()) {
            return;
        }
        ui.openMessage(0);
        assertTrue(ui.isMessageOpen(), "A message's page should open on " + device.label + "; the address is " + on.url());
        assertEquals(0, ui.horizontalOverflow(), "The message page should not run off the side on " + device.label);
        assertTrue(ui.messageFitsScreen(), "The message's content should fit within the screen on " + device.label);
        capture(on, device, "message");
        ui.back();
        assertTrue(ui.isOpen(), "Back should return to the list on " + device.label + "; the address is " + on.url());
    }

    private Page openOn(Device device) {
        Browser on = device.engine == Device.Engine.WEBKIT ? webkitOrAbort() : browser;
        deviceContext = newContext(on, device.contextOptions());
        return deviceContext.newPage();
    }

    private static Browser webkitOrAbort() {
        if (webkit == null) {
            try {
                webkit = playwright.webkit().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS).setSlowMo(SLOW_MO));
            } catch (RuntimeException notInstalled) {
                String why = notInstalled.getMessage() == null ? "" : notInstalled.getMessage().split("\\R", 2)[0];
                abort("The Safari rows run in WebKit, which could not be launched (" + why + "). Install it with "
                        + "`npx playwright install webkit` and rerun.");
            }
        }
        return webkit;
    }

    private static void capture(Page on, Device device, String what) {
        try {
            Path file = SHOTS.resolve("UiNotificationCenterClientHK." + device.name().toLowerCase() + "." + what + ".png");
            java.nio.file.Files.createDirectories(SHOTS);
            on.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(false));
            CaseLog.evidence(device.label + " - " + what + ": " + file);
        } catch (Exception couldNotCapture) {
            CaseLog.evidence("No screenshot of " + what + " on " + device.label + ": " + couldNotCapture.getMessage());
        }
    }
}
