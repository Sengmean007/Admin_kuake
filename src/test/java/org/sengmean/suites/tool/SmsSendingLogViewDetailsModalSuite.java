package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - View details modal</b>: the {@code SMS-ADM-VDT-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogViewDetailsModalSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - View details modal")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$ViewDetailsModal")
public class SmsSendingLogViewDetailsModalSuite {
}
