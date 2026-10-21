package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Notification - delete seed data</b>: deletes every "QA SEED NOT" notification
 * ({@code NOT-DEL-901}) that {@code NotificationSeedSuite} created.
 *
 * <pre>./mvnw test -Dsuite=NotificationSeedCleanupSuite</pre>
 */
@Suite
@SuiteDisplayName("Notification - delete seed data")
@SelectMethod("org.sengmean.tests.tool.NotificationSeedData#deleteSeed")
public class NotificationSeedCleanupSuite {
}
