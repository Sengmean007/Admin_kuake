package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Client SMS limit</b>: the {@code SMS-CLI-LIM-*} rows. Every row currently
 * aborts - this project has no member-site page objects for requesting an SMS/Voice/Reverse SMS
 * code (see the class javadoc on {@code SmsSendingLogTest}).
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogClientSmsLimitSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Client SMS limit")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$ClientSmsLimit")
public class SmsSendingLogClientSmsLimitSuite {
}
