package org.sengmean.suites;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.sengmean.tests.tool.NotificationTest;
import org.sengmean.tests.tool.SmsSendingLogTest;

/**
 * <b>Tool</b>: Tool &gt; Notification, Tool &gt; SMS Sending Log.
 *
 * <pre>./mvnw test -Dsuite=ToolSuite</pre>
 *
 * <p>All 102 rows of the client's "Notification" tab, in {@link NotificationTest}, and all 129 rows of
 * the client's "SMS Sent logs" tab, in {@link SmsSendingLogTest} - one nested class per section of each
 * sheet, run as one pass through its own feature's life. The order and the reasons for it are on each
 * class. Each section also has a suite of its own in {@code org.sengmean.suites.tool}.
 *
 * <p><b>Not deployed yet.</b> On 2026-09-26 the back office had no Tool menu, and as of 2026-10-21 Tool
 * offers only Notification - no SMS Sending Log entry - so every row of both classes reports Not run
 * naming that one reason. The rows are written in full and run as written once each screen exists.
 *
 * <p>The safe set keeps the rows that only read and never save:
 *
 * <pre>./mvnw test -Dsuite=ToolSuite -DexcludedGroups=known-defect,creates-data</pre>
 */
@Suite
@SuiteDisplayName("Tool")
@SelectClasses({
        NotificationTest.class,
        SmsSendingLogTest.class
})
public class ToolSuite {
}
