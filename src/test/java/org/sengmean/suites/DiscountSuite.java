package org.sengmean.suites;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.sengmean.tests.promo.ClientValidDepositTest;
import org.sengmean.tests.promo.SystemPromotionRecordsTest;
import org.sengmean.tests.promo.SystemPromotionSetupTest;

/**
 * <b>Discount</b>: Discount &gt; System Promotion Setup, and the deposit-linked promotion records
 * that setup produces.
 *
 * <pre>./mvnw test -Dsuite=DiscountSuite</pre>
 *
 * <p>Every {@code SPS-*} row, in {@link SystemPromotionSetupTest} - this project's own rows,
 * since there is no client workbook tab for Discount yet. The screen renders for the operator
 * but its three data calls answer "Permission denied" (measured 2026-10-05), so the writing
 * section's rows abort naming that until the permission is granted; the class Javadoc has the
 * detail.
 *
 * <p>Every {@code SPR-*} row, in {@link SystemPromotionRecordsTest} - the deposit / System
 * Promotion / System Live Promotion records flow (confirm cascades, reject isolation, the
 * wallet and currency rules), written from Si's own description (2026-10-06) against the real
 * screen: Trade &gt; Transaction Records (not in the operator's menu, reached from the Control
 * Panel's own "Today total deposit amount" tile). The screen renders and searches for real
 * ({@code SPR-017}, {@code SPR-018}), but nothing exists yet to search for - the widest possible
 * search found no transaction record at all - so the other fourteen rows abort naming that until
 * a qualifying deposit exists to read; the class Javadoc has the detail.
 *
 * <p>Every {@code SPD-*} row, in {@link ClientValidDepositTest} - a real, qualifying deposit made
 * on the member site (2026-10-06), one promotion type Active at a time, so this suite has its own
 * data to run {@code SPR-002}/{@code 003} and the reject-cascade rows against instead of depending
 * on whatever happens to exist on admin-test that day. Crosses the "no member-site code in
 * Admin_weibo" boundary {@code AGENTS.md} otherwise documents - done on request, not by default.
 *
 * <p>The writing rows are tagged {@code creates-data} - each reads the global setting first and
 * puts it back in {@code @AfterEach}, the same pattern {@code RebateSetUpTest} uses for its own
 * global panel. The safe set keeps the read-only screen and localization rows:
 *
 * <pre>./mvnw test -Dsuite=DiscountSuite -DexcludedGroups=known-defect,creates-data</pre>
 */
@Suite
@SuiteDisplayName("Discount")
@SelectClasses({
        SystemPromotionSetupTest.class,
        ClientValidDepositTest.class,
        SystemPromotionRecordsTest.class
})
public class DiscountSuite {
}
