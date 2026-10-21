package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Non-functional: security</b>: the {@code SMS-NFR-SEC-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogNfrSecuritySuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - NFR: security")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$NfrSecurity")
public class SmsSendingLogNfrSecuritySuite {
}
