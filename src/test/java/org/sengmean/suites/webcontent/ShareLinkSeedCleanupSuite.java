package org.sengmean.suites.webcontent;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Share Link - delete seed data</b>: deletes every "QA SEED SL" share link
 * ({@code SL-DEL-901}) that {@code ShareLinkSeedSuite} created.
 *
 * <pre>./mvnw test -Dsuite=ShareLinkSeedCleanupSuite</pre>
 */
@Suite
@SuiteDisplayName("Share Link - delete seed data")
@SelectMethod("org.sengmean.tests.webcontent.ShareLinkSeedData#deleteSeed")
public class ShareLinkSeedCleanupSuite {
}
