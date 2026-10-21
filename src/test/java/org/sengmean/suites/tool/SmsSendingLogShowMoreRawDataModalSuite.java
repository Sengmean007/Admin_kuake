package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Show more (raw data) modal</b>: the {@code SMS-ADM-RAW-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogShowMoreRawDataModalSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Show more (raw data) modal")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$ShowMoreRawDataModal")
public class SmsSendingLogShowMoreRawDataModalSuite {
}
