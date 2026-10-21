package org.sengmean.page.tool;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

import java.util.regex.Pattern;

/**
 * The <b>Show more</b> ("raw data") modal a list row's Show more action opens - read from the
 * Figma frame {@code BO / SMS Sending Log / Show more - raw data (modal)}.
 *
 * <h2>What the mockup shows</h2>
 * Header "Show more · SMS record #85144", a <b>RAW DATA (AS STORED)</b> block showing the exact
 * stored string (e.g. {@code {"phone":"55753454","phoneCode":"852","message":6062,"lang":"zh_t","source":6}}),
 * a <b>READABLE</b> table mapping it - Country code &lt;- phoneCode, Phone number &lt;- phone,
 * Message (code) &lt;- message, Language &lt;- lang, Source &lt;- source - a <b>Copy raw data</b>
 * button, and <b>Close</b>.
 */
public class SmsRawDataModalPage extends BasePage {

    private final Locator title;
    private final Locator rawDataBlock;
    private final Locator copyButton;
    private final Locator closeButton;

    public SmsRawDataModalPage(Page page) {
        super(page);
        this.title = page.getByText(Pattern.compile("Show more", Pattern.CASE_INSENSITIVE)).first();
        this.rawDataBlock = page.getByText(Pattern.compile("RAW DATA", Pattern.CASE_INSENSITIVE))
                .locator("xpath=following-sibling::*[1]");
        this.copyButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Copy raw data"));
        this.closeButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close").setExact(true));
    }

    public void awaitOpen() {
        rawDataBlock.first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        page.waitForTimeout(400);
    }

    public boolean isShowing() {
        return rawDataBlock.count() > 0;
    }

    public String rawDataText() {
        return rawDataBlock.count() == 0 ? "" : rawDataBlock.first().innerText().trim();
    }

    /** The READABLE table's value for a label, e.g. "Country code", "Phone number", "Message (code)", "Language", "Source". */
    public String readable(String label) {
        Locator row = page.getByText(label, new Page.GetByTextOptions().setExact(false)).first();
        if (row.count() == 0) {
            return "";
        }
        // Confirmed against the Figma frame "BO / SMS Sending Log / Show more - raw data (modal)"
        // (node 40013545:813) on 2026-10-21: each row is three siblings - the caption, the raw
        // field name (e.g. "phoneCode"), then the value (e.g. "+852") - so the value is the LAST
        // sibling, not the first as a prior guess here took it (which would have read the field
        // name instead of the value).
        Locator value = row.locator("xpath=following-sibling::*[last()]");
        return value.count() == 0 ? "" : value.first().innerText().trim();
    }

    public void clickCopyRawData() {
        CaseLog.step("Clicking Copy raw data.");
        copyButton.click();
    }

    public void close() {
        closeButton.click();
    }
}
