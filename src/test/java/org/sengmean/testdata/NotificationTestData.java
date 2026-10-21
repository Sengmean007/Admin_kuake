package org.sengmean.testdata;

import org.sengmean.config.ConfigDataProvider;

import java.util.List;

/**
 * The fixtures and literal values the Notification workbook's cases call for.
 *
 * <h2>Where they come from</h2>
 * The TEST DATA column of the "Notification" tab in {@code src/test/resources/testdata/client_testcase.xlsx}
 * (imported from the client's {@code Weibo_Notification_TestCases.xlsx}, v1.3), and the captures in
 * {@code docs/mockups/notification}. The workbook's example titles are the client's own; the suite
 * writes them with {@value #PREFIX} in front, so it never collides with, or deletes, a real rule -
 * and the prefix is what {@code NotificationTest} sweeps at the start and end of a run.
 */
public final class NotificationTestData {

    private NotificationTestData() {
    }

    /** Marks a notification as the suite's own. */
    public static final String PREFIX = "QA NOT ";

    // -----------------------------------------------------------------
    // Seed data - kept between runs, for the paging rows (NotificationSeedData)
    // -----------------------------------------------------------------

    /**
     * Marks a seeded notification. Deliberately NOT starting with {@value #PREFIX}, so
     * {@code NotificationTest}'s start-and-end sweep leaves the seed in place.
     */
    public static final String SEED_PREFIX = "QA SEED NOT ";

    /**
     * How many notifications the seed keeps: 50 - more than two pages at the list's smallest page
     * size (20), so {@code NOT-LST-006} has a page 2 and a page 3 to move between.
     */
    public static final int SEED_COUNT = 50;

    /** "QA SEED NOT 01" .. "QA SEED NOT 50". */
    public static String seedTitle(int n) {
        return String.format("%s%02d", SEED_PREFIX, n);
    }

    /** What every seeded notification says - it is never sent, since the seed is saved Inactive. */
    public static final String SEED_CONTENT = "Automation seed data for the Notification paging tests - never sent (Inactive).";

    /** The seed's Timer: a quiet hour, though an Inactive rule never fires at all. */
    public static final String TIMER_3AM = "03 AM";

    // -----------------------------------------------------------------
    // Languages - the twelve content tabs, as the Add Notification capture spells them
    // -----------------------------------------------------------------

    public static final String TAB_CHINESE = "繁體";
    public static final String TAB_SIMPLIFIED = "简体";
    public static final String TAB_ENGLISH = "English";
    public static final String TAB_JAPANESE = "日語";
    public static final String TAB_SPAIN = "Spain";
    public static final String TAB_BENGALI = "Bengali";

    public static final List<String> LANGUAGE_TABS = List.of(
            "繁體", "简体", "English", "日語", "ภาษาไทย", "Vietnam",
            "Spain", "Malaysia", "Indonesia", "Filipino", "Bengali", "Portuguese");

    public static final int LANGUAGE_TAB_COUNT = 12;

    /**
     * AQ-02 (answered 2026-09-26, corrected): the three languages the workbook made mandatory. Since 2026-10-01 the suite
     * expects the site's own rule instead - every language, {@link #ALL_LANGUAGES_REQUIRED} - so {@code NOT-CRT-007} fills
     * these three and expects the refusal; {@code fill()} satisfies the rule with Override to All languages.
     */
    public static final List<String> REQUIRED_LANGUAGES = List.of(TAB_ENGLISH, TAB_SIMPLIFIED, TAB_CHINESE);

    /** The validation message Save answers while any language lacks Title or Content (measured 2026-09-28..30; NOT-CRT-007). */
    public static final String ALL_LANGUAGES_REQUIRED = "Title and Content are required for all languages";

    // -----------------------------------------------------------------
    // Content - the workbook's own examples
    // -----------------------------------------------------------------

    public static final String DAILY_TITLE = PREFIX + "Daily mission promotion reminder";
    public static final String VIP_TITLE = PREFIX + "VIP upgrade notification alert";
    public static final String WEEKEND_TITLE = PREFIX + "Weekend deposit bonus reminder";
    public static final String BROADCAST_TITLE = PREFIX + "Maintenance window heads-up notice";
    /**
     * The rule Send Now pushes - the NOT-ACT-* rows and the Message Center rows that read what arrived.
     * Si, 2026-09-30: Send Now test data targets <b>Select all</b> on both Agent Group and Member Group, so the
     * push reaches the configured member whatever groups it belongs to. Its columns therefore read All.
     */
    public static final String SEND_NOW_TITLE = PREFIX + "Send now to everyone";
    public static final String LOGIN_ALERT_TITLE = PREFIX + "New login from unknown device alert";

    public static final String RELEASED_AT = "2026-09-28 08:00:00";
    /** The Record Details capture's Content for "Daily mission promotion reminder". */
    public static final String CONTENT = "Complete your daily missions to earn bonus rewards! Check your mission list now and start earning.";

    public static final String LINK = "https://system.lumen.app/member/promotions/daily";

    /** NOT-CRT-005. */
    public static final String INVALID_LINK = "not a url";

    // -----------------------------------------------------------------
    // Settings - the form's own words
    // -----------------------------------------------------------------

    public static final String TYPE_SYSTEM = "System";
    public static final String SCHEDULE_ONCE = "Once";
    public static final String SCHEDULE_DAILY = "Daily";
    public static final String SCHEDULE_WEEKLY = "Weekly";
    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_INACTIVE = "Inactive";

    public static final String TIMER_MIDNIGHT = "12 AM";
    public static final String TIMER_8PM = "08 PM";
    public static final String TIMER_9AM = "09 AM";

    /** What the list's Timer column shows for a Once rule, per the capture. */
    public static final String TIMER_IMMEDIATE = "Immediate";

    /**
     * The Timer's "—" option. Si's requirement (2026-09-28) for a <b>Once</b> notification: with Timer
     * "—" it is sent immediately, whenever its Status is made Active; with a Timer hour it is sent
     * once, at that hour. So "—" is a real choice under Once, not a disabled field (NOT-CRT-014).
     */
    public static final String TIMER_SEND_IMMEDIATELY = "—";

    public static final String SUNDAY = "Sunday";

    /**
     * The clock every schedule runs on: Hong Kong Time, UTC+8, no daylight saving - the System
     * Notification Feature Report, section 5 (2026-09-28). The back office also shows Created At and
     * Released At in it. The test machine is not necessarily there (Si's is UTC+7), so every "now",
     * "today" and "next hour" the suite works out for a schedule is taken in this zone, never the
     * machine's own.
     */
    public static final java.time.ZoneId SCHEDULE_ZONE = java.time.ZoneId.of("Asia/Hong_Kong");

    /**
     * The clock a <b>Timer</b> is set from: the test machine's own. Si, 2026-09-30: the scheduler runs every
     * hour, so a new notification's Timer is the machine's current hour plus one - that is the next run.
     * Set from Hong Kong Time (one hour ahead of Si's machine) it landed a run later than intended. The
     * back office still displays Created At and Released At in {@link #SCHEDULE_ZONE}, so the two zones
     * are converted between where a Timer hour is compared with a Released At hour.
     */
    public static final java.time.ZoneId TIMER_ZONE = java.time.ZoneId.systemDefault();

    /** A Timer hour as set in {@link #TIMER_ZONE}, expressed in {@link #SCHEDULE_ZONE} - the hour Released At shows for it. */
    public static int timerHourInScheduleZone(int hour) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(TIMER_ZONE);
        int offsetHours = (SCHEDULE_ZONE.getRules().getOffset(now.toInstant()).getTotalSeconds()
                - TIMER_ZONE.getRules().getOffset(now.toInstant()).getTotalSeconds()) / 3600;
        return ((hour + offsetHours) % 24 + 24) % 24;
    }

    /** AQ-17: Week day is a single select over the seven days. */
    public static final List<String> WEEK_DAYS = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    /** AQ-17: whole hours only - "12 AM", "01 AM" ... "11 PM". */
    public static final int TIMER_OPTION_COUNT = 24;

    /** Si, 2026-09-30: the paging rows (NOT-LST-006, NOT-LST-010) show 20 entries per page - three pages of the 50 seeds. */
    public static final int LIST_PAGE_SIZE = 20;

    /** What a broadcast's Agent Group and Member Group columns read (NOT-LST-008). */
    public static final String ALL = "All";

    // -----------------------------------------------------------------
    // The tables' columns, as the captures head them
    // -----------------------------------------------------------------

    public static final List<String> LIST_COLUMNS = List.of(
            "ID", "Title", "Type", "Agent Group", "Member Group", "Schedule", "Timer", "Status", "Created At");

    public static final List<String> HISTORY_COLUMNS = List.of(
            "ID", "Notification Title", "Type", "Agent Group", "Member Group", "Released At", "Status","Actions");

    public static final String SENT = "Sent";
    public static final String PENDING = "Pending";
    public static final String FAILED = "Failed";

    // -----------------------------------------------------------------
    // Targets - Config/, because they must be groups the Message Center member belongs to
    // -----------------------------------------------------------------

    public static final String TARGET_AGENT_GROUP = ConfigDataProvider.get("test.notification.agentGroup", "agents001");
    public static final String TARGET_MEMBER_GROUP = ConfigDataProvider.get("test.notification.memberGroup", "Default 00");

    public static final String OUTSIDER_USERNAME = ConfigDataProvider.get("test.notification.outsider.username", "");
    public static final String OUTSIDER_PASSWORD = ConfigDataProvider.get("test.notification.outsider.password", "");

    public static final boolean WAIT_FOR_SCHEDULE = ConfigDataProvider.flag("test.notification.waitForSchedule", false);
    public static final int MAX_SCHEDULE_WAIT_MINUTES = (int) ConfigDataProvider.number("test.notification.maxScheduleWaitMinutes", 65);

    /**
     * Si, 2026-10-01: the developer has stopped the delivery process for this run, so a Send Now stays Pending until
     * they resume it (then Sent) or make it fail (then Failed). What NOT-RHD-006 and NOT-RHL-007 need to make their own
     * Pending and Failed entries rather than look for ones that have never existed on admin-test.
     */
    public static final boolean DELIVERY_STOPPED = ConfigDataProvider.flag("test.notification.deliveryStopped", false);
    /** How long those rows wait for the developer to resolve the stopped send. */
    public static final int RESOLVE_WAIT_MINUTES = (int) ConfigDataProvider.number("test.notification.resolveWaitMinutes", 10);

    // -----------------------------------------------------------------
    // The Deposit/Withdraw detail's fields (NOT-CLI-007)
    // -----------------------------------------------------------------

    public static final List<String> DEPOSIT_DETAIL_FIELDS = List.of(
            "Deposit Amount", "Deposit Method", "Transaction ID", "Destination Wallet", "Transaction Status");
}
