package org.sengmean.suites.promo;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.sengmean.tests.promo.SystemPromotionSetupTest;

/**
 * <b>Discount - System Promotion Setup</b>: every {@code SPS-*} row.
 *
 * <pre>./mvnw test -Dsuite=SystemPromotionSetupSuite</pre>
 *
 * <p>Signs in as the operator, probes the screen once, and runs the eight sections of
 * {@link SystemPromotionSetupTest}. Every writing row reads the setting first and puts it back,
 * because the setup is global per currency; run with {@code -DexcludedGroups=creates-data} for the
 * read-only rows alone.
 */
@Suite
@SuiteDisplayName("Discount - System Promotion Setup")
@SelectClasses(SystemPromotionSetupTest.class)
public class SystemPromotionSetupSuite {
}
