package org.sengmean.page.member;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

import java.util.List;

/**
 * <b>Member Management &gt; Member List</b> ({@code /member/member-listing}) - the screen the
 * sheet's {@code BO-ADD-FRM-001} opens <b>Add member</b> from, and the one {@code BO-ADD-AFT-002}
 * and {@code -003} read a newly created member back from.
 *
 * <h2>Measured live on admin-test, 2026-10-21</h2>
 * A search panel (Username / Full name, Member ID, Status, ... with Search / Reset all), an
 * <b>Add member</b> button above it, and a table whose columns include Member ID, Member account,
 * Contact number, Email, Member group, Agent code, <b>Status</b>, Pause betting, Registration
 * details, Recent login detail, Main wallet balance and Agent. The account name is a link inside
 * the Member account cell; Status is a coloured dropdown in its own cell. The header row's own
 * wording is what the column readers below search by, so a column that moves keeps working.
 */
public class MemberListPage extends BasePage {

    public static final String PATH = "member/member-listing";

    private final Locator searchField;
    private final Locator searchButton;
    private final Locator addMemberButton;
    private final Locator headerCells;
    private final Locator rows;

    public MemberListPage(Page page) {
        super(page);
        this.searchField = page.locator("input[placeholder='Username / Full name']");
        this.searchButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search").setExact(true));
        this.addMemberButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add member").setExact(true));
        this.headerCells = page.locator("table thead th");
        this.rows = page.locator("table tbody tr");
    }

    public void open() {
        CaseLog.step("Opening Member Management > Member List.");
        open("/" + PATH);
        awaitReady();
        searchField.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
    }

    public boolean isShowing() {
        return searchField.count() > 0 && addMemberButton.count() > 0;
    }

    /** Clicks Add member and returns a page object for the form it opens. */
    public AddMemberPage clickAddMember() {
        CaseLog.step("Clicking Add member.");
        addMemberButton.click();
        awaitReady();
        AddMemberPage form = new AddMemberPage(page);
        form.usernameField().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        return form;
    }

    public void searchByUsername(String username) {
        CaseLog.step("Searching the member list for \"" + username + "\".");
        searchField.fill(username);
        searchButton.click();
        awaitReady();
        page.waitForTimeout(800);
    }

    /** True when the list currently shows a row for this member account. */
    public boolean hasRow(String username) {
        return row(username).count() > 0;
    }

    /** How many rows the list currently shows for this member account - BO-ADD-AFT-007 reads this to rule out a double create. */
    public int rowCount(String username) {
        return row(username).count();
    }

    /** The row's Member ID cell, as read by the "Member ID" header - "" when the member is not listed. */
    public String memberId(String username) {
        return cellOf(username, "Member ID");
    }

    /** The row's Status cell, as read by the "Status" header - "" when the member is not listed. */
    public String statusOf(String username) {
        return cellOf(username, "Status");
    }

    /** A row's cell under a given header, by that header's own wording - "" when the member or the header is not found. */
    private String cellOf(String username, String header) {
        Locator target = row(username);
        if (target.count() == 0) {
            return "";
        }
        int column = headerIndex(header);
        if (column < 0) {
            return "";
        }
        Locator cell = target.first().locator("td").nth(column);
        return cell.count() == 0 ? "" : cell.innerText().trim();
    }

    private Locator row(String username) {
        return rows.filter(new Locator.FilterOptions()
                .setHas(page.locator("a", new Page.LocatorOptions())
                        .getByText(username, new Locator.GetByTextOptions().setExact(true))));
    }

    /** The 0-based column index of the first header cell whose text equals (ignoring case) this label. */
    private int headerIndex(String label) {
        List<String> headers = headerCells.allInnerTexts();
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).trim().equalsIgnoreCase(label)) {
                return i;
            }
        }
        return -1;
    }
}
