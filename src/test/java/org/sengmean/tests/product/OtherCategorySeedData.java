package org.sengmean.tests.product;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sengmean.base.BaseTest;
import org.sengmean.page.product.OtherCategoryFormPage;
import org.sengmean.page.product.OtherCategoryListPage;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sengmean.testdata.OtherCategoryTestData.*;

/**
 * <b>Seed data for Product &gt; Other Category</b>: twenty categories created in one shot and
 * <em>kept</em>, and the row that deletes them again.
 *
 * <pre>
 *   ./mvnw test -Dsuite=OtherCategorySeedSuite          create QA SEED 01..20 (skips any already there)
 *   ./mvnw test -Dsuite=OtherCategorySeedCleanupSuite   delete every QA SEED category
 * </pre>
 *
 * <h2>Why keep them</h2>
 * The paging rows ({@code OC-LST-005}, {@code OC-LST-006}) need more categories than the list's
 * smallest page, and used to create about sixteen each, every run, then delete them - some seven
 * minutes apiece. They count what is already listed before creating anything, so with the seed in
 * place they create nothing. The rows that need "at least two / three categories listed"
 * ({@code OC-LST-001}, {@code OC-LST-008}) are satisfied by it too.
 *
 * <h2>Why they survive the suite</h2>
 * They are named {@value org.sengmean.testdata.OtherCategoryTestData#SEED_PREFIX}NN, not "QA OC ...",
 * so {@code OtherCategoryTest}'s sweep leaves them alone. Their Display Orders are 201-220, clear of
 * the 90-up range {@code freeOrder()} hands the suite's own fixtures, and all distinct, so the
 * ordering rows never judge a tie among them. Each is named once for all languages, Active, with no
 * games.
 *
 * <h2>Why this class does not end in "Test"</h2>
 * A plain {@code ./mvnw test} runs every {@code *Test} class, and would seed and delete on every run.
 * This one runs only through its two suites. Both rows are project-only, so they take the 900 block
 * of the workbook's prefixes ({@code OC-CRT-902}, {@code OC-DEL-901}).
 */
@DisplayName("Other Category - seed data")
public class OtherCategorySeedData extends BaseTest {

    private static BrowserContext session;
    private static OtherCategoryListPage list;

    @BeforeAll
    static void signInOnce() {
        session = newContext();
        Page sessionPage = session.newPage();
        signIn(sessionPage);
        AdminShellPage shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        // The seed goes into the run's currency (test.currency, -Dtest.currency=USDT to change it),
        // the same one OtherCategoryTest switches to, so the paging rows see the seed they rely on.
        shell.useCurrency(CURRENCY);
        list = new OtherCategoryListPage(sessionPage);
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    /** The list in English, every row on one page. */
    private static void openList() {
        list.open();
        list.filterLanguage(TAB_ENGLISH);
        List<Integer> sizes = list.pageSizeOptions();
        if (!sizes.isEmpty()) {
            list.setPageSize(sizes.get(sizes.size() - 1));
        }
    }

    private static List<String> seedsListed() {
        return list.categoryNames().stream().filter(name -> name.startsWith(SEED_PREFIX)).toList();
    }

    @Test
    @Tag("creates-data")
    @DisplayName("OC-CRT-902: Seed 20 Other Categories in one shot, kept for the data-heavy rows")
    void seedCategories() {
        openList();
        Set<String> present = new HashSet<>(seedsListed());
        List<String> created = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (int n = 1; n <= SEED_COUNT; n++) {
            String name = seedName(n);
            if (present.contains(name)) {
                continue;
            }
            OtherCategoryFormPage form = list.clickCreate();
            form.setNameForAllLanguages(name);
            form.uploadIcon(ICON_PNG);
            form.setDisplayOrder(String.valueOf(SEED_ORDER_BASE + n));
            form.clickSave();
            if (form.isOpen()) {
                refused.add(name + " (" + form.messages() + ")");
            } else {
                created.add(name);
            }
            openList();
        }
        CaseLog.data("Seed categories", created.size() + " created, " + present.size() + " already there"
                + (refused.isEmpty() ? "" : ", refused: " + String.join("; ", refused)));

        openList();
        List<String> listed = seedsListed();
        List<String> missing = new ArrayList<>();
        for (int n = 1; n <= SEED_COUNT; n++) {
            if (!listed.contains(seedName(n))) {
                missing.add(seedName(n));
            }
        }
        assertTrue(missing.isEmpty(), "Every seed category should be listed; missing " + missing
                + (refused.isEmpty() ? "" : " - the form refused " + refused));
    }

    @Test
    @Tag("creates-data")
    @DisplayName("OC-DEL-901: Delete every seeded Other Category")
    void deleteSeed() {
        openList();
        List<String> seeds = seedsListed();
        for (String name : seeds) {
            list.deleteIfListed(name);
            openList();
        }
        CaseLog.data("Seed categories deleted", String.valueOf(seeds.size()));
        openList();
        assertTrue(seedsListed().isEmpty(), "No seed category should be left; still listed: " + seedsListed());
    }
}
