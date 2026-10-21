package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Non-functional: performance</b>: the {@code SMS-NFR-PRF-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogNfrPerformanceSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - NFR: performance")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$NfrPerformance")
public class SmsSendingLogNfrPerformanceSuite {
}
