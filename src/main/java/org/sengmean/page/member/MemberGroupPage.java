package org.sengmean.page.member;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.sengmean.base.BasePage;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.page.common.PromptMessage;
import org.sengmean.report.CaseLog;

import java.util.List;

/**
 * <b>Member Management &gt; Member group</b> ({@code /member/member-group/}) - the groups a Tool &gt;
 * Notification rule targets on its Member Group side.
 *
 * <h2>As measured (2026-09-29)</h2>
 * A table of No. / Group name / Status / Operate. "Default 00" has only Modify; every other group has
 * Modify and Delete. <b>Add member group</b> opens a Bootstrap modal with one "Group name" field and
 * Cancel / Submit; Submit raises the jQuery UI prompt "Confirm to add?", and Delete its own confirmation.
 * The groups listed follow the header currency, like every other screen here.
 *
 * <p>Member groups are shared configuration. A case may only add and delete groups it names itself -
 * the Notification suite uses the {@code QA NOT } prefix - and must delete what it added.
 */
public class MemberGroupPage extends BasePage {

    private static final String PATH = ConfigDataProvider.get("test.memberGroup.path", "member/member-group/");

    public MemberGroupPage(Page page) {
        super(page);
    }

    public void open() {
        CaseLog.step("Opening Member Management > Member group.");
        page.navigate(url((BASE_URL.endsWith("/") ? "" : "/") + PATH), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        page.locator("table tbody tr").first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        page.waitForTimeout(800);
    }

    /** Every group name, as listed. */
    public List<String> names() {
        return page.locator("table tbody tr td:nth-child(2)").allInnerTexts().stream()
                .map(String::trim).filter(n -> !n.isEmpty()).toList();
    }

    public boolean isListed(String name) {
        return names().contains(name);
    }

    private Locator row(String name) {
        return page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHas(page.locator("td:nth-child(2)").getByText(name, new Locator.GetByTextOptions().setExact(true))))
                .first();
    }

    /** Adds a group and returns what the page said; the caller decides from {@link #isListed}. */
    public String add(String name) {
        CaseLog.step("Adding the member group \"" + name + "\".");
        PromptMessage.clearStray(page);
        page.getByText("Add member group", new Page.GetByTextOptions().setExact(true)).first().click();
        Locator field = page.getByPlaceholder("Group name").filter(new Locator.FilterOptions().setVisible(true)).first();
        field.waitFor(new Locator.WaitForOptions().setTimeout(8_000));
        field.fill(name);
        page.locator(".modal:visible").getByText("Submit", new Locator.GetByTextOptions().setExact(true)).first().click();
        String said = PromptMessage.confirmAndRead(page, 6000);
        CaseLog.data("After adding \"" + name + "\"", said.isEmpty() ? "(no message)" : said);
        open();
        return said;
    }

    /** Adds the group unless it is already listed. */
    public void ensure(String name) {
        open();
        if (!isListed(name)) {
            add(name);
        }
    }

    /** Deletes a group by its row's Delete button and returns what the page said. */
    public String delete(String name) {
        CaseLog.step("Deleting the member group \"" + name + "\".");
        PromptMessage.clearStray(page);
        row(name).getByText("Delete", new Locator.GetByTextOptions().setExact(true)).first().click();
        String said = PromptMessage.confirmAndRead(page, 6000);
        CaseLog.data("After deleting \"" + name + "\"", said.isEmpty() ? "(no message)" : said);
        open();
        return said;
    }

    /** Deletes the group if it is listed - for clean-up, where it may already be gone. */
    public void deleteIfListed(String name) {
        open();
        if (isListed(name)) {
            delete(name);
        }
    }
}
