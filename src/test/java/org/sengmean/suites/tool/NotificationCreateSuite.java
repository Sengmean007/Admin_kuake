package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Notification - Create notification</b>: the {@code NOT-CRT-*} rows of the client's "Notification" tab, and nothing else.
 *
 * <pre>./mvnw test -Dsuite=NotificationCreateSuite</pre>
 *
 * <p>Runs the {@code NotificationTest.CreateNotification} section on its own and reports it on a page of its own;
 * {@code ToolSuite} still runs all twelve sections as one pass. The outer class's sign-in, probe and
 * "QA NOT " sweep still run around it, and every row creates the fixtures it needs.
 */
@Suite
@SuiteDisplayName("Notification - Create notification")
@SelectClasses(names = "org.sengmean.tests.tool.NotificationTest$CreateNotification")
public class NotificationCreateSuite {
}
