package org.sengmean.suites;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.sengmean.tests.rebateprogram.DailyRebateTest;
import org.sengmean.tests.rebateprogram.RealTimeRebateTest;
import org.sengmean.tests.rebateprogram.RebateAccessControlTest;
import org.sengmean.tests.rebateprogram.RebateLocalizationTest;
import org.sengmean.tests.rebateprogram.RebateMigrationTest;
import org.sengmean.tests.rebateprogram.RebateReportTest;
import org.sengmean.tests.rebateprogram.RebateSetUpTest;

/**
 * <b>Rebate management</b>: the Real-time and Daily tabs of Rebate set up.
 *
 * <pre>./mvnw test -Dsuite=RebateSuite</pre>
 *
 * <p>All 154 rows of the client's "Rebate Program (Multi-Config)" workbook, split by the sheet's
 * own sections - A1 to A8 and C1 in {@link RealTimeRebateTest}, B1 to B8 in
 * {@link DailyRebateTest}, C2 in {@link RebateAccessControlTest}, C3 in
 * {@link RebateLocalizationTest}, C4 in {@link RebateMigrationTest}, D1 in
 * {@link RebateSetUpTest} and D2 in {@link RebateReportTest}.
 *
 * <p><b>The multi-program feature has shipped.</b> When the first two classes were written,
 * {@code /rebate/set-up} was still the earlier single-program design and all 114 of their rows
 * reported Not run. Measured on 2026-09-18 the screen now serves the program list, both Create
 * controls, the search box, the status filter and the page-size control, so their guard no
 * longer fires and those rows attempt their work for the first time. The environment is still
 * bare - no programs, no Agent or User groups, no rebate records - so the rows that need a
 * fixture continue to abort naming it, which is the distinction worth keeping: a row that
 * cannot run says why, and a row that can, runs.
 *
 * <p>The {@code creates-data} tags here are written for the feature as specified rather than for
 * what runs today - a create, a delete or a settings save all outlive the run once they can
 * happen at all. They cost nothing while the rows abort, and they are already correct on the day
 * the feature arrives, which is the safer order to do it in:
 *
 * <pre>./mvnw test -Dsuite=RebateSuite -DexcludedGroups=known-defect,creates-data</pre>
 *
 * <h2>The order the classes run in, and why it is this one</h2>
 * A run of this suite is meant to read as <b>one pass through the feature's life</b>: get in,
 * check you are allowed in, look at the environment before touching it, configure it, build
 * programs on it, use them, and finally read the report that describes what happened. The order
 * is declared with {@code @Order} on each class - {@code junit-platform.properties} selects
 * {@code ClassOrderer$OrderAnnotation} - and the list in {@code @SelectClasses} below is
 * <b>not</b> what decides it, which is worth knowing before anyone reorders that list expecting
 * an effect.
 *
 * <ol>
 *   <li><b>10 - {@code AdminLoginTest}</b> (ADMLOGIN). Nothing downstream means anything if the
 *       back office will not let the operator in, and a failure here explains every failure
 *       after it. It is the only class that deliberately does <em>not</em> reuse a session.</li>
 *   <li><b>20 - {@link RebateAccessControlTest}</b> (C2). Having a session is not the same as
 *       being allowed to use this feature. {@code RBMP-115} is the suite's smoke test in
 *       disguise: menu entry, both tabs, Create on each, search, status filter, settings
 *       readable. If that is red, every section below it is red for one reason.</li>
 *   <li><b>30 - {@link RebateMigrationTest}</b> (C4). <b>The one placement that is load-bearing
 *       rather than tidy.</b> These rows ask what the multi-program upgrade left behind - is the
 *       pre-existing configuration on the list, were its assignments duplicated. That is only
 *       answerable while the list still holds what the upgrade put there. Run it after
 *       {@link RealTimeRebateTest} and {@code RBMP-124} finds "New Real-time Rebate", a program
 *       created by the suite itself thirty cases earlier, and reports a migration that never
 *       happened. It must observe before anything creates or deletes.</li>
 *   <li><b>40 - {@link RebateSetUpTest}</b> (D1). The global settings, read and written. Before
 *       the program sections because those read the settings "as found" in their own first case
 *       ({@code RBMP-007}, {@code RBMP-062}), and because this class puts every value back -
 *       whereas {@code RBMP-008} and {@code RBMP-010} later on do not. Going first is what lets
 *       it see pristine values rather than another section's leavings.</li>
 *   <li><b>50 - {@link RealTimeRebateTest}</b> (A1-A8, C1). The lifecycle proper, and itself an
 *       end-to-end pass: create, target, rate, list, modify, settings, cross-type, delete. Its
 *       own method bands are documented on the class. Real-time before Daily because
 *       {@code RBMP-114} - "the same pairing is allowed on a Daily program" - starts here and
 *       establishes the Real-time pairing the Daily side is permitted to reuse.</li>
 *   <li><b>60 - {@link DailyRebateTest}</b> (B1-B8). The same lifecycle on the other tab.</li>
 *   <li><b>70 - {@link RebateLocalizationTest}</b> (C3). After the creators on purpose:
 *       {@code RBMP-122} needs an existing program whose pairing a second submission can collide
 *       with, and there is no such program until {@link RealTimeRebateTest} has made one. The
 *       three read-only rows would run anywhere; this one would abort forever if the class ran
 *       first.</li>
 *   <li><b>80 - {@link RebateReportTest}</b> (D2). Last, because the report is downstream of
 *       everything else - it describes rebates that programs configured above went on to
 *       credit. Nothing settles inside a single run, so today this is a claim about direction
 *       rather than about data, but it is the right direction and it costs nothing to hold.</li>
 * </ol>
 *
 * <h2>What the order does not currently protect against</h2>
 * Two things, both worth fixing before the sequence can be relied on:
 *
 * <p><b>The classes are not all on the same currency.</b> Rebate set up stores a separate set of
 * global settings per currency - see
 * {@link org.sengmean.page.shell.AdminShellPage#selectCurrency(String)} - and the four newest
 * classes select HKD in {@code @BeforeAll} while {@link RealTimeRebateTest} and
 * {@link DailyRebateTest} use whatever the environment defaults to. So step 4 and step 5 are
 * presently writing to different records and cannot interfere, which sounds like safety and is
 * really just the ordering not being tested. Once every class selects the same currency the
 * sequence above starts to matter for real.
 *
 * <p><b>Steps 5 and 6 do not put the global settings back.</b> {@code RBMP-008} saves Min Claim
 * and Expire Time, {@code RBMP-010} and {@code RBMP-063} change more, and none of them restores
 * what it found - so a second run of the suite does not start where the first one did. The D1
 * rows at step 4 show the shape of the fix: register the undo before writing, and run it from
 * {@code @AfterEach}.
 */
@Suite
@SuiteDisplayName("Rebate management")
@SelectClasses({
        RebateAccessControlTest.class,
        RebateMigrationTest.class,
        RebateSetUpTest.class,
        RealTimeRebateTest.class,
        DailyRebateTest.class,
        RebateLocalizationTest.class,
        RebateReportTest.class
})
public class RebateSuite {
}
