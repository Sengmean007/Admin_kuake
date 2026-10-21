package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Permission &amp; phone masking</b>: the {@code SMS-ADM-PRM-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogPermissionAndPhoneMaskingSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Permission & phone masking")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$PermissionAndPhoneMasking")
public class SmsSendingLogPermissionAndPhoneMaskingSuite {
}
