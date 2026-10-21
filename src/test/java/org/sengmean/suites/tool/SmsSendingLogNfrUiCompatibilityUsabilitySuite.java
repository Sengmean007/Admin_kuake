package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Non-functional: UI, compatibility &amp; usability</b>: the
 * {@code SMS-NFR-UI-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogNfrUiCompatibilityUsabilitySuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - NFR: UI, compatibility & usability")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$NfrUiCompatibilityUsability")
public class SmsSendingLogNfrUiCompatibilityUsabilitySuite {
}
