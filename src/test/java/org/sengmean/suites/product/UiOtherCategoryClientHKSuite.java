package org.sengmean.suites.product;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.sengmean.tests.product.UiOtherCategoryClientHK;

/**
 * <b>Other Category - client UI (HK, new UI)</b>: the home-page sections and category pages of the
 * client platform's new UI on the HK brand, as a visitor and as the testing member, on a PC, a Mac,
 * an iPad, an iPhone and an Android phone.
 *
 * <pre>./mvnw test -Dsuite=UiOtherCategoryClientHKSuite</pre>
 *
 * <p>Independent of the back office: nothing here signs in to admin-test, and the rows judge what the
 * client draws against what its own category feed serves. The Mac and iOS rows run in WebKit, which
 * must be installed ({@code npx playwright install webkit}); they report Not run naming that when it
 * is not.
 */
@Suite
@SuiteDisplayName("Other Category - client UI (HK, new UI)")
@SelectClasses(UiOtherCategoryClientHK.class)
public class UiOtherCategoryClientHKSuite {
}
