package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Export &amp; Account Operation Log</b>: the {@code SMS-ADM-EXP-*} rows.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogExportAndAccountOperationLogSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Export & Account Operation Log")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$ExportAndAccountOperationLog")
public class SmsSendingLogExportAndAccountOperationLogSuite {
}
