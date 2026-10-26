package org.sengmean.suites.webcontent;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Member Benefits - delete seed data</b>: deletes every "QA SEED MB" benefit
 * ({@code MB-DEL-901}) that {@code MemberBenefitsSeedSuite} created.
 *
 * <pre>./mvnw test -Dsuite=MemberBenefitsSeedCleanupSuite</pre>
 */
@Suite
@SuiteDisplayName("Member Benefits - delete seed data")
@SelectMethod("org.sengmean.tests.webcontent.MemberBenefitsSeedData#deleteSeed")
public class MemberBenefitsSeedCleanupSuite {
}
