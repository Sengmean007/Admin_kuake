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
import org.sengmean.page.webcontent.ShareLinkFormPage;
import org.sengmean.page.webcontent.ShareLinkListPage;
import org.sengmean.report.CaseLog;
import org.sengmean.testdata.ShareLinkTestData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * <b>Seed data for Web content management &gt; Share link</b>: ten share links created in one
 * shot, <em>under every currency the header offers</em>, and kept; and the row that deletes them
 * all again.
 *
 * <pre>
 *   ./mvnw test -Dsuite=ShareLinkSeedSuite                          every currency: QA SEED SL 01..10 each (skips any already there)
 *   ./mvnw test -Dsuite=ShareLinkSeedSuite -Dseed.currency=HKD      one currency only
 *   ./mvnw test -Dsuite=ShareLinkSeedCleanupSuite                   delete every QA SEED SL share link, in every currency
 *   ./mvnw test -Dsuite=ShareLinkSeedCleanupSuite -Dseed.currency=HKD   delete it in one currency only
 * </pre>
 *
 * <h2>Why every currency, not just {@code test.currency}</h2>
 * Si asked for this seed to work "with all currencies existing" - the same request behind
 * {@code MemberBenefitsSeedData}'s own per-currency loop, and for the same reason: this back
 * office keeps records per currency (see {@code AdminShellPage#useCurrency}'s own Rebate
 * example), so a seed written for one currency only leaves every other currency's pagination row
 * exactly as starved as before. {@link AdminShellPage#currencyValues()} is read fresh each run
 * rather than hard-coded.
 *
 * <h2>{@code -Dseed.currency} narrows it to one currency</h2>
 * A separate property from {@code test.currency} - see {@code MemberBenefitsSeedData}'s own note
 * on why the two must not be conflated. {@link #SEED_CURRENCY} is blank by default (every
 * currency); set it to seed or delete in just one. An unoffered value fails immediately, naming
 * what the header does offer.
 *
 * <h2>Why keep them</h2>
 * {@code ShareLinkTest}'s own pagination row ({@code SL-ADM-LST-012}) needs more share links on
 * the list than the default page size, the same reason {@code OtherCategorySeedData} and
 * {@code MemberBenefitsSeedData} exist. Counting what is already listed before creating anything
 * is what makes a second run of the seed suite a no-op, per currency.
 *
 * <h2>Written ahead of the screen, like {@code ShareLinkTest} itself - shipped 2026-10-26</h2>
 * Not deployed as of 2026-10-21 (see {@code ShareLinkTest}'s own Javadoc); confirmed live
 * 2026-10-26 at {@code cms/share-link}. This class still probes for the screen the same way and
 * aborts, naming that, on an environment where it is missing again - nothing is created or
 * deleted while it is.
 *
 * <h2>Why they survive the suite</h2>
 * They are named {@value org.sengmean.testdata.ShareLinkTestData#SEED_PREFIX}NN, not
 * {@value org.sengmean.testdata.ShareLinkTestData#PREFIX} - {@code ShareLinkTest} never searches
 * for or deletes that prefix. Their Display Orders are 301-310
 * ({@link org.sengmean.testdata.ShareLinkTestData#SEED_ORDER_BASE}), clear of the 0 / 1 / 5 / 9 /
 * duplicate values the suite's own order rows exercise. Each is saved once, named identically
 * across the three required languages, Active, with the shared {@code CARD_IMAGE} fixture.
 *
 * <h2>One currency's trouble does not stop the others</h2>
 * {@link #seedShareLinks()} and {@link #deleteSeed()} both finish the full currency list before
 * asserting anything, collecting per-currency failures into one message.
 *
 * <h2>Why this class does not end in "Test"</h2>
 * A plain {@code ./mvnw test} runs every {@code *Test} class, and would seed and delete share
 * links in every currency on every run for nothing. This one runs only through its two suites.
 * Both rows are project-only, so they take the 900 block of the workbook's {@code SL-} prefix
 * ({@code SL-CRT-901}, {@code SL-DEL-901}) - the same numbering {@code OC-CRT-902} /
 * {@code OC-DEL-901}, {@code NOT-CRT-901} / {@code NOT-DEL-901} and {@code MB-CRT-901} /
 * {@code MB-DEL-901} use for their own seeds.
 */
@DisplayName("Share Link - seed data")
public class ShareLinkSeedData extends BaseTest {

    /** Restricts a run to one currency - blank (the default) means every currency the header offers. */
    private static final String SEED_CURRENCY = org.sengmean.config.ConfigDataProvider.get("seed.currency", "");

    /**
     * URL link is required per language the same as Title and Text content - confirmed live
     * 2026-10-26, {@code ShareLinkTest}'s own Javadoc has the full list of what that pass measured.
     * {@link #seedShareLinks()} originally never set this at all: the form silently refused every
     * save (inline "URL link (繁體/简体/English) is required.", no dialog), and because no dialog
     * ever appeared, {@code form.save()} timed out and returned "" - which the loop below reads as
     * success. Nothing was ever actually created; the only reason this was not already seen failing
     * is that the screen did not exist yet when this class was written.
     */
    private static final String SEED_URL = "https://example.com/qa-sl-seed";

    private static BrowserContext session;
    private static AdminShellPage shell;
    private static ShareLinkListPage list;
    private static boolean screenPresent;

    @BeforeAll
    static void signInAndProbe() {
        session = newContext();
        Page sessionPage = session.newPage();
        signIn(sessionPage);
        shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        list = new ShareLinkListPage(sessionPage);

        try {
            list.open();
            screenPresent = list.isShowing();
        } catch (RuntimeException screenNeverArrived) {
            screenPresent = false;
        }
        CaseLog.data("Share Link screen deployed", String.valueOf(screenPresent));
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

    private static void requireScreen() {
        if (!screenPresent) {
            abort("Web content management > Share link is not deployed on " + BASE_URL + " yet - nothing to seed or delete.");
        }
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

    /** Every share link currently listed (in whichever currency the header is set to) whose title starts with {@code SEED_PREFIX}. */
    private static List<String> seedsListed() {
        list.open();
        list.searchTitle(ShareLinkTestData.SEED_PREFIX.trim());
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < list.rowCount(); i++) {
            titles.add(list.row(i).getOrDefault("Title", ""));
        }
        return titles;
    }

    @Test
    @Tag("creates-data")
    @DisplayName("SL-CRT-901: Seed 10 Share Links per currency, kept for the data-heavy rows")
    void seedShareLinks() {
        requireScreen();
        Map<String, List<String>> missingByCurrency = new LinkedHashMap<>();
        Map<String, List<String>> refusedByCurrency = new LinkedHashMap<>();

        for (String currencyCode : currenciesToSeed()) {
            shell.useCurrency(currencyCode);

            List<String> present = seedsListed();
            List<String> created = new ArrayList<>();
            List<String> refused = new ArrayList<>();

            for (int n = 1; n <= ShareLinkTestData.SEED_COUNT; n++) {
                String name = ShareLinkTestData.seedName(n);
                if (present.contains(name)) {
                    continue;
                }
                list.open();
                ShareLinkFormPage form = list.clickAdd();
                for (String lang : ShareLinkFormPage.REQUIRED_LANGUAGES) {
                    form.switchTab(lang);
                    form.setTitle(name);
                    form.setText("Seed data for the pagination rows.");
                    form.setUrl(SEED_URL);
                    form.uploadImage(ShareLinkTestData.CARD_IMAGE);
                }
                form.setOrder(String.valueOf(ShareLinkTestData.SEED_ORDER_BASE + n));
                form.setActive(true);
                String said = form.save();
                if (said.isEmpty()) {
                    created.add(name);
                } else {
                    refused.add(name + " (" + said + ")");
                }
            }
            CaseLog.data("Seed share links under " + currencyCode, created.size() + " created, " + present.size() + " already there"
                    + (refused.isEmpty() ? "" : ", refused: " + String.join("; ", refused)));
            if (!refused.isEmpty()) {
                refusedByCurrency.put(currencyCode, refused);
            }

            List<String> listed = seedsListed();
            List<String> missing = new ArrayList<>();
            for (int n = 1; n <= ShareLinkTestData.SEED_COUNT; n++) {
                if (!listed.contains(ShareLinkTestData.seedName(n))) {
                    missing.add(ShareLinkTestData.seedName(n));
                }
            }
            if (!missing.isEmpty()) {
                missingByCurrency.put(currencyCode, missing);
            }
        }

        assertTrue(missingByCurrency.isEmpty(), "Every seed share link should be listed in every currency; missing " + missingByCurrency
                + (refusedByCurrency.isEmpty() ? "" : " - the form refused " + refusedByCurrency));
    }

    @Test
    @Tag("creates-data")
    @DisplayName("SL-DEL-901: Delete every seeded Share Link, in every currency")
    void deleteSeed() {
        requireScreen();
        Map<String, List<String>> remainingByCurrency = new LinkedHashMap<>();
        int deleted = 0;

        for (String currencyCode : currenciesToSeed()) {
            shell.useCurrency(currencyCode);
            int deletedHere = 0;
            while (true) {
                list.open();
                list.searchTitle(ShareLinkTestData.SEED_PREFIX.trim());
                if (list.rowCount() == 0) {
                    break;
                }
                list.clickDelete(0);
                if (list.isDeleteDialogVisible()) {
                    list.confirmDelete();
                }
                deletedHere++;
            }
            CaseLog.data("Seed share links deleted under " + currencyCode, String.valueOf(deletedHere));
            deleted += deletedHere;

            List<String> remaining = seedsListed();
            if (!remaining.isEmpty()) {
                remainingByCurrency.put(currencyCode, remaining);
            }
        }

        CaseLog.data("Seed share links deleted in total", String.valueOf(deleted));
        assertTrue(remainingByCurrency.isEmpty(), "No seed share link should be left in any currency; still listed: " + remainingByCurrency);
    }
}
