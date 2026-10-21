package org.sengmean.suites;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.sengmean.tests.auth.AdminLoginTest;

/**
 * <b>Authentication</b>: getting into the back office, and staying out of it when you should.
 *
 * <pre>./mvnw test -Dsuite=AuthSuite</pre>
 *
 * <p>Everything here is safe to run unattended today - a valid sign-in spends nothing and
 * changes nothing. That stops being true the moment a rejection case is added: the site locks
 * an account after three wrong passwords, so such a case must use the secondary operator
 * account and be tagged {@code creates-data}. See {@link AdminLoginTest} for why none is here
 * yet.
 */
@Suite
@SuiteDisplayName("Authentication")
@SelectClasses({
        AdminLoginTest.class
})
public class AuthSuite {
}
