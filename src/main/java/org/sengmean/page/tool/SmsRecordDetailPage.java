package org.sengmean.page.tool;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The <b>View details</b> modal ("SMS record details · #ID") a list row's View action opens -
 * read from the Figma frame {@code BO / SMS Sending Log / View details (modal)}.
 *
 * <h2>What the mockup shows</h2>
 * <ul>
 *   <li>Header "SMS record details · #85145", a ✕.</li>
 *   <li><b>REQUEST</b>: Phone number, Method, Purpose, Requested at, IP address / Country (e.g.
 *       "103.25.61.40 · Cambodia (KH)"), Request domain (with an "Agent subdomain" tag when it is
 *       one).</li>
 *   <li><b>DELIVERY &amp; VERIFICATION</b>: Delivery status, Verification result, Codes today
 *       (e.g. "2 of 3 · resets 2026-10-07 00:00 (HKT) · Reverse SMS: no limit"), Code expires
 *       (requested + 120s), Username ("— (not registered yet)" when unregistered).</li>
 *   <li><b>HISTORY FOR THIS PHONE (TODAY, HKT)</b>: a time-ordered list of today's events for the
 *       phone.</li>
 *   <li>Footer: "Version A" (Show more permission) shows <b>Block number</b> / <b>Show more</b> /
 *       <b>Close</b>; "Version B" (no Show more permission) shows <b>Block number</b> /
 *       <b>Close</b> only, with an info banner missing the Show more sentence.</li>
 * </ul>
 */
public class SmsRecordDetailPage extends BasePage {

    private final Locator title;
    private final Locator closeButton;
    private final Locator closeIcon;
    private final Locator blockNumberButton;
    private final Locator showMoreButton;
    private final Locator historyRows;

    public SmsRecordDetailPage(Page page) {
        super(page);
        this.title = page.getByText(Pattern.compile("SMS record details", Pattern.CASE_INSENSITIVE));
        this.closeButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close").setExact(true));
        this.closeIcon = page.locator("[aria-label='Close'], button:has-text('✕'), button:has-text('×')").first();
        this.blockNumberButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Block number"));
        this.showMoreButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show more"));
        this.historyRows = page.getByText(Pattern.compile("HISTORY FOR THIS PHONE", Pattern.CASE_INSENSITIVE))
                .locator("xpath=ancestor::*[self::div][1]").locator("li, tr");
    }

    public void awaitOpen() {
        title.first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        page.waitForTimeout(400);
    }

    public boolean isShowing() {
        return title.count() > 0;
    }

    public String titleText() {
        return title.count() == 0 ? "" : title.first().innerText().trim();
    }

    /** The value under a caption, e.g. "Phone number", "Method", "Delivery status", "Codes today", "Username". */
    public String field(String caption) {
        Locator label = page.getByText(Pattern.compile("^\\s*" + Pattern.quote(caption) + "\\s*$", Pattern.CASE_INSENSITIVE));
        if (label.count() == 0) {
            return "";
        }
        Locator value = label.first().locator("xpath=following-sibling::*[1]");
        return value.count() == 0 ? "" : value.first().innerText().trim();
    }

    public int historyEntryCount() {
        return historyRows.count();
    }

    public boolean isShowMoreOffered() {
        return showMoreButton.count() > 0;
    }

    public boolean isBlockNumberOffered() {
        return blockNumberButton.count() > 0;
    }

    public SmsRawDataModalPage clickShowMore() {
        CaseLog.step("Clicking Show more from View details.");
        showMoreButton.click();
        SmsRawDataModalPage modal = new SmsRawDataModalPage(page);
        modal.awaitOpen();
        return modal;
    }

    public SmsBlockModalPage clickBlockNumber() {
        CaseLog.step("Clicking Block number from View details.");
        blockNumberButton.click();
        SmsBlockModalPage modal = new SmsBlockModalPage(page);
        modal.awaitOpen();
        return modal;
    }

    public void closeViaIcon() {
        closeIcon.click();
    }

    public void closeViaButton() {
        closeButton.click();
    }
}
