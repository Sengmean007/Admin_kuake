package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Notification - end to end</b>: one pass through the Notification process, from Create to Delete.
 *
 * <pre>./mvnw test -Dsuite=NotificationE2ESuite</pre>
 *
 * <p>Si, 2026-09-30: the whole lifecycle in one run, <b>without</b> the three suites that are not part of
 * the process itself - {@code NotificationAccessControlSuite} (who may use the screen), and
 * {@code NotificationSeedSuite} / {@code NotificationSeedCleanupSuite} (the kept paging data, which
 * a run must never seed or delete by accident; see the README's seed section).
 *
 * <p>The sections run in the {@code @Order} {@code NotificationTest} gives them, which is the order
 * the feature's life goes in and the one every later section relies on:
 * Create (20) - List (30) - Target groups (40) - Edit (50) - Send Now (60) - Record History list (70)
 * and detail (80) - Localization (90) - Client Message Inbox (100) - Regression (110) - Delete (120).
 * The outer class's sign-in, probe and "QA NOT " sweep run once around the whole pass.
 *
 * <p>Reporting: each section keeps its own page ({@code NotificationCreate-test-report.html} and so on),
 * so this run refreshes all eleven of them at once, exactly as running the eleven section suites in turn
 * would - there is no twelfth page to keep in step.
 */
@Suite
@SuiteDisplayName("Notification - end to end")
@SelectClasses(names = {
        "org.sengmean.tests.tool.NotificationTest$CreateNotification",
        "org.sengmean.tests.tool.NotificationTest$NotificationList",
        "org.sengmean.tests.tool.NotificationTest$TargetGroups",
        "org.sengmean.tests.tool.NotificationTest$EditNotification",
        "org.sengmean.tests.tool.NotificationTest$SendNow",
        "org.sengmean.tests.tool.NotificationTest$RecordHistoryList",
        "org.sengmean.tests.tool.NotificationTest$RecordHistoryDetail",
        "org.sengmean.tests.tool.NotificationTest$Localization",
        "org.sengmean.tests.tool.NotificationTest$ClientInbox",
        "org.sengmean.tests.tool.NotificationTest$Regression",
        "org.sengmean.tests.tool.NotificationTest$DeleteNotification"
})
public class NotificationE2ESuite {
}
