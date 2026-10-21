package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Search condition &amp; quick filter</b>: the {@code SMS-ADM-SRC-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogSearchConditionAndQuickFilterSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Search condition & quick filter")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$SearchConditionAndQuickFilter")
public class SmsSendingLogSearchConditionAndQuickFilterSuite {
}
