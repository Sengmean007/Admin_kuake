package org.sengmean.tests.tool;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sengmean.base.BaseTest;
import org.sengmean.page.shell.AdminShellPage;
import org.sengmean.page.tool.NotificationFormPage;
import org.sengmean.page.tool.NotificationListPage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sengmean.page.tool.NotificationFormPage.AGENT_GROUP;
import static org.sengmean.page.tool.NotificationFormPage.MEMBER_GROUP;
import static org.sengmean.testdata.NotificationTestData.*;

/**
 * <b>Seed data for Tool &gt; Notification</b>: fifty notifications created in one shot and
 * <em>kept</em>, and the row that deletes them again.
 *
 * <pre>
 *   ./mvnw test -Dsuite=NotificationSeedSuite          create QA SEED NOT 01..50 (skips any already there)
 *   ./mvnw test -Dsuite=NotificationSeedCleanupSuite   delete every QA SEED NOT notification
 *
 *   ./mvnw test -Dsuite=NotificationSeedSuite -Dtest.currency=USDT   seed another currency
 * </pre>
 * The seed goes into the run's currency - {@code test.currency} in {@code Config/}, HKD unless
 * overridden - because the back office keeps its records per header currency. Run the paging suite
 * (and the cleanup) with the same {@code -Dtest.currency} as the seed, or it will not see the seed.
 *
 * <h2>Why keep them</h2>
 * The paging rows ({@code NOT-LST-005}, {@code NOT-LST-006}) need more notifications than the list's
 * smallest page (20). {@code NOT-LST-006} aborts on a list with only one page, and the test site's list
 * is normally near empty. Fifty gives pages 1, 2 and 3 at 20 per page.
 *
 * <h2>Why they are safe</h2>
 * Every seed is saved <b>Inactive</b> - never delivered, under the System Notification Feature Report
 * (4.3, 5) - on a Daily schedule at 03 AM, targeted at the configured groups only
 * ({@code test.notification.agentGroup} / {@code memberGroup}) rather than Select all. So even a seed
 * someone activated by mistake would not reach every member.
 *
 * <h2>Why they survive the suite</h2>
 * They are titled {@value org.sengmean.testdata.NotificationTestData#SEED_PREFIX}NN, not
 * "{@value org.sengmean.testdata.NotificationTestData#PREFIX}...", so {@code NotificationTest}'s sweep
 * leaves them alone. Title and Content are written on 繁體 and copied to every language with Override,
 * so each is complete in all twelve.
 *
 * <h2>Why this class does not end in "Test"</h2>
 * A plain {@code ./mvnw test} runs every {@code *Test} class, and would seed and delete on every run.
 * This one runs only through its two suites. Both rows are project-only, so they take the 900 block
 * of the workbook's prefixes ({@code NOT-CRT-901}, {@code NOT-DEL-901}).
 */
@DisplayName("Notification - seed data")
public class NotificationSeedData extends BaseTest {

    private static BrowserContext session;
    private static NotificationListPage list;

    @BeforeAll
    static void signInOnce() {
        session = newContext();
        Page sessionPage = session.newPage();
        signIn(sessionPage);
        AdminShellPage shell = new AdminShellPage(sessionPage);
        shell.useLanguage(LANGUAGE);
        // The seed goes into the run's currency (test.currency, -Dtest.currency=USDT to change it),
        // the same one NotificationTest switches to, so the paging rows see the seed they rely on.
        shell.useCurrency(CURRENCY);
        list = new NotificationListPage(sessionPage);
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    /** The list, every status, as many rows per page as it allows - so a seed on page 2 is still seen. */
    private static void openList() {
        list.open();
        List<Integer> sizes = list.pageSizeOptions();
        if (!sizes.isEmpty()) {
            list.setPageSize(sizes.get(sizes.size() - 1));
        }
    }

    private static List<String> seedsListed() {
        return list.titles().stream().filter(title -> title.startsWith(SEED_PREFIX)).toList();
    }

    @Test
    @Tag("creates-data")
    @DisplayName("NOT-CRT-901: Seed 50 Inactive notifications in one shot, kept for the paging rows")
    void seedNotifications() {
        openList();
        Set<String> present = new HashSet<>(seedsListed());
        List<String> created = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (int n = 1; n <= SEED_COUNT; n++) {
            String title = seedTitle(n);
            if (present.contains(title)) {
                continue;
            }
            NotificationFormPage form = list.clickAdd();
            form.fillLanguage(TAB_CHINESE, title, SEED_CONTENT, null);
            form.overrideToAll();
            form.setType(TYPE_SYSTEM);
            form.setSchedule(SCHEDULE_DAILY);
            form.setTimer(TIMER_3AM);
            form.setStatus(STATUS_INACTIVE);
            form.target(AGENT_GROUP, List.of(TARGET_AGENT_GROUP));
            form.target(MEMBER_GROUP, List.of(TARGET_MEMBER_GROUP));
            form.clickSave();
            if (form.isOpen()) {
                refused.add(title + " (" + form.messages() + ")");
                form.cancel();
            } else {
                created.add(title);
            }
            openList();
        }
        CaseLog.data("Seed notifications", created.size() + " created, " + present.size() + " already there"
                + (refused.isEmpty() ? "" : ", refused: " + String.join("; ", refused)));

        openList();
        List<String> listed = seedsListed();
        List<String> missing = new ArrayList<>();
        for (int n = 1; n <= SEED_COUNT; n++) {
            if (!listed.contains(seedTitle(n))) {
                missing.add(seedTitle(n));
            }
        }
        assertTrue(missing.isEmpty(), "Every seed notification should be listed; missing " + missing
                + (refused.isEmpty() ? "" : " - the form refused " + refused));
        assertTrue(list.notifications().stream().filter(row -> row.title().startsWith(SEED_PREFIX))
                        .allMatch(row -> STATUS_INACTIVE.equals(row.status())),
                "Every seed notification should be Inactive, so none of them is ever sent");
    }

    @Test
    @Tag("creates-data")
    @DisplayName("NOT-DEL-901: Delete every seeded notification")
    void deleteSeed() {
        openList();
        List<String> seeds = seedsListed();
        for (String title : seeds) {
            list.deleteIfListed(title);
            openList();
        }
        CaseLog.data("Seed notifications deleted", String.valueOf(seeds.size()));
        openList();
        assertTrue(seedsListed().isEmpty(), "No seed notification should be left; still listed: " + seedsListed());
    }
}
