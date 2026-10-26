package org.sengmean.tests.webcontent;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sengmean.base.BaseTest;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.page.webcontent.MemberBenefitsFormPage;
import org.sengmean.page.webcontent.MemberBenefitsListPage;
import org.sengmean.report.CaseLog;
import org.sengmean.testdata.MemberBenefitsTestData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Seed data for Web content management &gt; Member Benefits</b>: ten benefits created in one
 * shot, <em>under every currency the header offers</em>, and kept; and the row that deletes them
 * all again.
 *
 * <pre>
 *   ./mvnw test -Dsuite=MemberBenefitsSeedSuite                          every currency: QA SEED MB 01..10 each (skips any already there)
 *   ./mvnw test -Dsuite=MemberBenefitsSeedSuite -Dseed.currency=HKD      one currency only
 *   ./mvnw test -Dsuite=MemberBenefitsSeedCleanupSuite                   delete every QA SEED MB benefit, in every currency
 *   ./mvnw test -Dsuite=MemberBenefitsSeedCleanupSuite -Dseed.currency=HKD   delete it in one currency only
 * </pre>
 *
 * <h2>Why every currency, not just {@code test.currency}</h2>
 * Si asked for this seed to work "with all currencies existing" - and Member Benefits keeps
 * records per currency exactly the way Rebate set up does
 * ({@code MB-ADM-LST-P014}/{@code N006} proved a benefit created under one currency is invisible
 * under another). A seed written for one currency only would leave every other currency's
 * pagination rows exactly as starved as before. {@link AdminShellPage#currencyValues()} is read
 * fresh each run rather than hard-coded, so a currency the header adds or removes later is
 * picked up without editing this class.
 *
 * <h2>{@code -Dseed.currency} narrows it to one currency</h2>
 * A separate property from {@code test.currency} - that one already means "the currency every
 * other suite works in", and conflating the two would make {@code -Dtest.currency=USDT} silently
 * change what a run of {@code MemberBenefitsTest} itself seeds, far from where anyone would
 * think to look. {@link #SEED_CURRENCY} is blank by default (every currency); set it to seed or
 * delete in just one, e.g. for a quick check or to fix up a single currency without touching the
 * others. An unoffered value fails immediately, naming what the header does offer, the same
 * guard {@link AdminShellPage#useCurrency} already applies.
 *
 * <h2>Why keep them</h2>
 * {@code MemberBenefitsTest}'s own pagination and "more than one page" rows
 * ({@code MB-ADM-LST-P003}, {@code MB-ADM-LST-P013}) need more benefits on the list than the
 * default page size, and the test site is otherwise close to empty of them. Counting what is
 * already listed before creating anything is what makes a second run of the seed suite a no-op,
 * per currency.
 *
 * <h2>Why they survive the suite</h2>
 * They are named {@value org.sengmean.testdata.MemberBenefitsTestData#SEED_PREFIX}NN, not
 * {@value org.sengmean.testdata.MemberBenefitsTestData#PREFIX} - {@code MemberBenefitsTest} never
 * searches for or deletes that prefix, the same separation {@code OtherCategorySeedData} and
 * {@code NotificationSeedData} already use. Their Display Orders are 301-310 (
 * {@link org.sengmean.testdata.MemberBenefitsTestData#SEED_ORDER_BASE}), clear of the 0 / 1 / 9999
 * / duplicate values the suite's own Display order rows exercise, and all distinct, so no list
 * row has to guess a tie-break order among them. Each is named once, on 繁體, then copied to
 * every other language tab with the <b>All languages</b> override rather than typed into each
 * tab in turn - Si's own ask - and saved Active, with one complete banner.
 *
 * <h2>One currency's trouble does not stop the others</h2>
 * {@link #seedBenefits()} and {@link #deleteSeed()} both finish the full currency list before
 * asserting anything, collecting per-currency failures into one message - so a problem seeding
 * USDT is reported alongside a clean HKD run rather than aborting it before HKD is even tried.
 *
 * <h2>Why this class does not end in "Test"</h2>
 * A plain {@code ./mvnw test} runs every {@code *Test} class, and would seed and delete benefits
 * in every currency on every run for nothing. This one runs only through its two suites. Both
 * rows are project-only, so they take the 900 block of the workbook's {@code MB-} prefix
 * ({@code MB-CRT-901}, {@code MB-DEL-901}) - the same numbering {@code OC-CRT-902} /
 * {@code OC-DEL-901} and {@code NOT-CRT-901} / {@code NOT-DEL-901} use for their own seeds.
 */
@DisplayName("Member Benefits - seed data")
public class MemberBenefitsSeedData extends BaseTest {

    /** Restricts a run to one currency - blank (the default) means every currency the header offers. */
    private static final String SEED_CURRENCY = org.sengmean.config.ConfigDataProvider.get("seed.currency", "");

    private static BrowserContext session;
    private static AdminShellPage shell;
    private static MemberBenefitsListPage list;

    @BeforeAll
    static void signInOnce() {
        session = newContext();
        Page sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        list = new MemberBenefitsListPage(sessionPage);
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        shell = null;
        list = null;
    }

    /** Every currency to seed or delete into this run - {@link #SEED_CURRENCY} alone, or every currency the header offers. */
    private static List<String> currenciesToSeed() {
        List<String> offered = shell.currencyValues();
        if (SEED_CURRENCY.isBlank()) {
            return offered;
        }
        if (!offered.contains(SEED_CURRENCY)) {
            throw new IllegalArgumentException("The back office does not offer the currency \"" + SEED_CURRENCY
                    + "\" (-Dseed.currency). It offers " + offered + ".");
        }
        return List.of(SEED_CURRENCY);
    }

    /** Every benefit currently listed (in whichever currency the header is set to) whose name starts with {@code SEED_PREFIX}. */
    private static List<String> seedsListed() {
        list.open();
        list.searchName(MemberBenefitsTestData.SEED_PREFIX.trim());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < list.rowCount(); i++) {
            names.add(list.row(i).getOrDefault("Benefit name", ""));
        }
        return names;
    }

    @Test
    @Tag("creates-data")
    @DisplayName("MB-CRT-901: Seed 10 Member Benefits per currency, kept for the data-heavy rows")
    void seedBenefits() {
        Map<String, List<String>> missingByCurrency = new LinkedHashMap<>();
        Map<String, List<String>> refusedByCurrency = new LinkedHashMap<>();

        for (String currencyCode : currenciesToSeed()) {
            shell.useCurrency(currencyCode);

            List<String> present = seedsListed();
            List<String> created = new ArrayList<>();
            List<String> refused = new ArrayList<>();

            for (int n = 1; n <= MemberBenefitsTestData.SEED_COUNT; n++) {
                String name = MemberBenefitsTestData.seedName(n);
                if (present.contains(name)) {
                    continue;
                }
                list.open();
                MemberBenefitsFormPage form = list.clickAdd();
                form.switchTab("繁體");
                form.setName(name);
                form.tickOverrideAll();
                form.uploadIcon(MemberBenefitsTestData.ICON);
                form.setDisplayOrder(String.valueOf(MemberBenefitsTestData.SEED_ORDER_BASE + n));
                form.setActive(true);
                int banner = form.addBanner();
                for (String lang : MemberBenefitsFormPage.REQUIRED_LANGUAGES) {
                    form.switchTab(lang);
                    form.uploadSmallBanner(banner, MemberBenefitsTestData.SMALL_BANNER);
                    form.uploadMobilePopup(banner, MemberBenefitsTestData.MOBILE_POPUP);
                    form.uploadPcPopup(banner, MemberBenefitsTestData.PC_POPUP);
                }
                String said = form.save();
                if (said.isEmpty()) {
                    created.add(name);
                } else {
                    refused.add(name + " (" + said + ")");
                }
            }
            CaseLog.data("Seed benefits under " + currencyCode, created.size() + " created, " + present.size() + " already there"
                    + (refused.isEmpty() ? "" : ", refused: " + String.join("; ", refused)));
            if (!refused.isEmpty()) {
                refusedByCurrency.put(currencyCode, refused);
            }

            List<String> listed = seedsListed();
            List<String> missing = new ArrayList<>();
            for (int n = 1; n <= MemberBenefitsTestData.SEED_COUNT; n++) {
                if (!listed.contains(MemberBenefitsTestData.seedName(n))) {
                    missing.add(MemberBenefitsTestData.seedName(n));
                }
            }
            if (!missing.isEmpty()) {
                missingByCurrency.put(currencyCode, missing);
            }
        }

        assertTrue(missingByCurrency.isEmpty(), "Every seed benefit should be listed in every currency; missing " + missingByCurrency
                + (refusedByCurrency.isEmpty() ? "" : " - the form refused " + refusedByCurrency));
    }

    @Test
    @Tag("creates-data")
    @DisplayName("MB-DEL-901: Delete every seeded Member Benefit, in every currency")
    void deleteSeed() {
        Map<String, List<String>> remainingByCurrency = new LinkedHashMap<>();
        int deleted = 0;

        for (String currencyCode : currenciesToSeed()) {
            shell.useCurrency(currencyCode);
            int deletedHere = 0;
            while (true) {
                list.open();
                list.searchName(MemberBenefitsTestData.SEED_PREFIX.trim());
                if (list.rowCount() == 0) {
                    break;
                }
                list.deleteAndConfirm(0);
                deletedHere++;
            }
            CaseLog.data("Seed benefits deleted under " + currencyCode, String.valueOf(deletedHere));
            deleted += deletedHere;

            List<String> remaining = seedsListed();
            if (!remaining.isEmpty()) {
                remainingByCurrency.put(currencyCode, remaining);
            }
        }

        CaseLog.data("Seed benefits deleted in total", String.valueOf(deleted));
        assertTrue(remainingByCurrency.isEmpty(), "No seed benefit should be left in any currency; still listed: " + remainingByCurrency);
    }
}
