package org.sengmean.suites.product;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Other Category - seed data</b>: creates "QA SEED 01".."QA SEED 20" in one shot and keeps them
 * ({@code OC-CRT-902}). Any already there are skipped, so it is safe to run again.
 *
 * <pre>./mvnw test -Dsuite=OtherCategorySeedSuite</pre>
 *
 * <p>Run it once before the data-heavy suites - {@code OtherCategoryListSuite} above all, whose paging
 * rows then find enough categories already listed and create none of their own. Remove the seed with
 * {@code OtherCategorySeedCleanupSuite}.
 */
@Suite
@SuiteDisplayName("Other Category - seed data")
@SelectMethod("org.sengmean.tests.product.OtherCategorySeedData#seedCategories")
public class OtherCategorySeedSuite {
}
