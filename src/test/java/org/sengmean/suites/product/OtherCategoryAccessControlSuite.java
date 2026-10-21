package org.sengmean.suites.product;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Other Category - Access control</b>: the {@code OC-ACC-*} rows of the client's "Other Category" tab, and nothing else.
 *
 * <pre>./mvnw test -Dsuite=OtherCategoryAccessControlSuite</pre>
 *
 * <p>Runs the {@code OtherCategoryTest.AccessControl} section on its own, for when only that part of the
 * feature has changed; {@code ProductSuite} still runs all ten sections as one pass. The section is
 * selected by name because it is a {@code @Nested} class the suites package cannot see. The outer
 * class's sign-in, probe and "QA OC " sweep still run around it, and every row creates the fixtures
 * it needs, so a section does not depend on another having run first.
 */
@Suite
@SuiteDisplayName("Other Category - Access control")
@SelectClasses(names = "org.sengmean.tests.product.OtherCategoryTest$AccessControl")
public class OtherCategoryAccessControlSuite {
}
