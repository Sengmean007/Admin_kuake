package org.sengmean.suites.product;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Other Category - delete seed data</b>: deletes every "QA SEED" category
 * ({@code OC-DEL-901}) that {@code OtherCategorySeedSuite} created.
 *
 * <pre>./mvnw test -Dsuite=OtherCategorySeedCleanupSuite</pre>
 */
@Suite
@SuiteDisplayName("Other Category - delete seed data")
@SelectMethod("org.sengmean.tests.product.OtherCategorySeedData#deleteSeed")
public class OtherCategorySeedCleanupSuite {
}
