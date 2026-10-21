package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Notification - Notification list</b>: the {@code NOT-LST-*} rows of the client's "Notification" tab, and nothing else.
 *
 * <pre>./mvnw test -Dsuite=NotificationListSuite</pre>
 *
 * <p>Runs the {@code NotificationTest.NotificationList} section on its own and reports it on a page of its own;
 * {@code ToolSuite} still runs all twelve sections as one pass. The outer class's sign-in, probe and
 * "QA NOT " sweep still run around it, and every row creates the fixtures it needs.
 */
@Suite
@SuiteDisplayName("Notification - Notification list")
@SelectClasses(names = "org.sengmean.tests.tool.NotificationTest$NotificationList")
public class NotificationListSuite {
}
