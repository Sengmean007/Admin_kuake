package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Notification - seed data</b>: creates "QA SEED NOT 01".."QA SEED NOT 50" in one shot and keeps
 * them ({@code NOT-CRT-901}). Each is Inactive, so none is ever sent. Any already there are skipped,
 * so it is safe to run again.
 *
 * <pre>./mvnw test -Dsuite=NotificationSeedSuite</pre>
 *
 * <p>Run it once before {@code NotificationListSuite}, whose paging rows ({@code NOT-LST-005},
 * {@code NOT-LST-006}) need more notifications than one page holds. Remove the seed with
 * {@code NotificationSeedCleanupSuite}.
 */
@Suite
@SuiteDisplayName("Notification - seed data")
@SelectMethod("org.sengmean.tests.tool.NotificationSeedData#seedNotifications")
public class NotificationSeedSuite {
}
