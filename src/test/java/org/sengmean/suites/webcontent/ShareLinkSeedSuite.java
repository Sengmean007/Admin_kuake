package org.sengmean.suites.webcontent;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Share Link - seed data</b>: creates "QA SEED SL 01".."QA SEED SL 10" in one shot and keeps
 * them ({@code SL-CRT-901}). Any already there are skipped, so it is safe to run again. Aborts,
 * naming it, while the screen is not yet deployed - see {@code ShareLinkSeedData}.
 *
 * <pre>./mvnw test -Dsuite=ShareLinkSeedSuite</pre>
 *
 * <p>Run it once before {@code ShareLinkTest}'s data-heavy list row, whose pagination case then
 * finds enough share links already listed. Remove the seed with {@code ShareLinkSeedCleanupSuite}.
 */
@Suite
@SuiteDisplayName("Share Link - seed data")
@SelectMethod("org.sengmean.tests.webcontent.ShareLinkSeedData#seedShareLinks")
public class ShareLinkSeedSuite {
}
