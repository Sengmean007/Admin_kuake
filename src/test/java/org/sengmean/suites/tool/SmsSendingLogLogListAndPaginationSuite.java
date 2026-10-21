package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Log list &amp; pagination</b>: the {@code SMS-ADM-LST-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogLogListAndPaginationSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Log list & pagination")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$LogListAndPagination")
public class SmsSendingLogLogListAndPaginationSuite {
}
