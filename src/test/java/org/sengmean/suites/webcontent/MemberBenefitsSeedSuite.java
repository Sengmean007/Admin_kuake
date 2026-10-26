package org.sengmean.suites.webcontent;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <b>Member Benefits - seed data</b>: creates "QA SEED MB 01".."QA SEED MB 10" in one shot and
 * keeps them ({@code MB-CRT-901}). Any already there are skipped, so it is safe to run again.
 *
 * <pre>./mvnw test -Dsuite=MemberBenefitsSeedSuite</pre>
 *
 * <p>Run it once before {@code MemberBenefitsTest}'s data-heavy list rows, whose pagination cases
 * then find enough benefits already listed. Remove the seed with
 * {@code MemberBenefitsSeedCleanupSuite}.
 */
@Suite
@SuiteDisplayName("Member Benefits - seed data")
@SelectMethod("org.sengmean.tests.webcontent.MemberBenefitsSeedData#seedBenefits")
public class MemberBenefitsSeedSuite {
}
