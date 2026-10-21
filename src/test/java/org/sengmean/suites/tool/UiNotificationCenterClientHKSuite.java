package org.sengmean.suites.tool;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.sengmean.tests.tool.UiNotificationCenterClientHK;

/**
 * <b>Notification - Message Center on the client (HK, new UI)</b>: the member site's Message Center
 * as a visitor and as the testing member, and the same screen on a PC, a Mac, an iPad, an iPhone
 * and an Android phone.
 *
 * <pre>./mvnw test -Dsuite=UiNotificationCenterClientHKSuite</pre>
 *
 * <p>Independent of the back office: nothing here signs in to admin-test or sends a notification;
 * the rows validate what the member's list draws, an empty list included. The Mac and iOS rows run
 * in WebKit ({@code npx playwright install webkit}).
 */
@Suite
@SuiteDisplayName("Notification - Message Center on the client (HK, new UI)")
@SelectClasses(UiNotificationCenterClientHK.class)
public class UiNotificationCenterClientHKSuite {
}
