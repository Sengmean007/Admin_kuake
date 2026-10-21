package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Summary cards</b>: the {@code SMS-ADM-SUM-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogSummaryCardsSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Summary cards")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$SummaryCards")
public class SmsSendingLogSummaryCardsSuite {
}
