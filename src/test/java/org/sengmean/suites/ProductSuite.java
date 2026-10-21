package org.sengmean.suites;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.sengmean.tests.product.OtherCategoryTest;

/**
 * <b>Product</b>: Product &gt; Other Category.
 *
 * <pre>./mvnw test -Dsuite=ProductSuite</pre>
 *
 * <p>All 78 rows of the client's "Other Category" tab, in {@link OtherCategoryTest} - one nested
 * class per section of the sheet, run as one pass through the feature's life. The order and the
 * reasons for it are on that class.
 *
 * <p><b>Not deployed yet.</b> On 2026-09-23 the back office had no Product menu, so every row
 * reports Not run naming that one reason. The rows are written in full and run as written once
 * the screen exists.
 *
 * <p>Most rows create, edit or delete categories and are tagged {@code creates-data}; every
 * category the suite makes is prefixed "QA OC " and swept at the start and end of the run. The
 * safe set keeps the read-only list and form rows:
 *
 * <pre>./mvnw test -Dsuite=ProductSuite -DexcludedGroups=known-defect,creates-data</pre>
 */
@Suite
@SuiteDisplayName("Product")
@SelectClasses({
        OtherCategoryTest.class
})
public class ProductSuite {
}
