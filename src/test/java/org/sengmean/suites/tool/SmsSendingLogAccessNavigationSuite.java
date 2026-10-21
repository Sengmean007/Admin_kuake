package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Access &amp; navigation</b>: the {@code SMS-ADM-NAV-*} rows, and nothing else.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogAccessNavigationSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Access & navigation")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$AccessNavigation")
public class SmsSendingLogAccessNavigationSuite {
}
