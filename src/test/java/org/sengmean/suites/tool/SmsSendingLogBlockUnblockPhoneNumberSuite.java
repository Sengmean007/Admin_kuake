package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>SMS Sending Log - Block / Unblock phone number</b>: the {@code SMS-ADM-BLK-*} rows. The one
 * section that writes; each row restores what it changed in {@code @AfterEach}.
 *
 * <pre>./mvnw test -Dsuite=SmsSendingLogBlockUnblockPhoneNumberSuite</pre>
 */
@Suite
@SuiteDisplayName("SMS Sending Log - Block / Unblock phone number")
@SelectClasses(names = "org.sengmean.tests.tool.SmsSendingLogTest$BlockUnblockPhoneNumber")
public class SmsSendingLogBlockUnblockPhoneNumberSuite {
}
