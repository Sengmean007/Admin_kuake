package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Notification - Target delivery groups</b>: the {@code NOT-GRP-*} rows of the client's "Notification" tab, and nothing else.
 *
 * <pre>./mvnw test -Dsuite=NotificationTargetGroupsSuite</pre>
 *
 * <p>Runs the {@code NotificationTest.TargetGroups} section on its own and reports it on a page of its own;
 * {@code ToolSuite} still runs all twelve sections as one pass. The outer class's sign-in, probe and
 * "QA NOT " sweep still run around it, and every row creates the fixtures it needs.
 */
@Suite
@SuiteDisplayName("Notification - Target delivery groups")
@SelectClasses(names = "org.sengmean.tests.tool.NotificationTest$TargetGroups")
public class NotificationTargetGroupsSuite {
}
