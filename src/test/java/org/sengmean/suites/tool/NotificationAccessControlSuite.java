package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Notification - Access control</b>: the {@code NOT-ACC-*} rows of the client's "Notification" tab, and nothing else.
 *
 * <pre>./mvnw test -Dsuite=NotificationAccessControlSuite</pre>
 *
 * <p>Runs the {@code NotificationTest.AccessControl} section on its own and reports it on a page of its own;
 * {@code ToolSuite} still runs all twelve sections as one pass. The outer class's sign-in, probe and
 * "QA NOT " sweep still run around it, and every row creates the fixtures it needs.
 */
@Suite
@SuiteDisplayName("Notification - Access control")
@SelectClasses(names = "org.sengmean.tests.tool.NotificationTest$AccessControl")
public class NotificationAccessControlSuite {
}
