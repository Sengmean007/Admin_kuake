package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Non-functional: data &amp; reliability</b>: the {@code SMS-NFR-REL-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogNfrDataReliabilitySuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - NFR: data & reliability")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$NfrDataReliability")
public class SmsSendingLogNfrDataReliabilitySuite {
}
