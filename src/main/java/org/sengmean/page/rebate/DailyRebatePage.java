package org.sengmean.page.rebate;


import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object for the Daily tab of the UNCS Rebate Program Setup screen —
 * Release Time / Expire Time / Status global settings, the Daily program
 * list, and the entry point into Create Daily Rebate Program.
 *
 * Cross-checked against RBMP-056..RBMP-064 and the client-executed rows
 * RBMP-128, RBMP-129 and RBMP-133 in the Rebate Program test case workbook.
 * (IDs reflect the Real-time-then-Daily restructure of that workbook — see
 * its Read Me tab.)
 *
 * <h2>This tab's fields have no names, so they are found by position</h2>
 * The Real-time panel's inputs carry {@code RealTimeMinClaim} and {@code RealTimeExpireTime};
 * the Daily panel's carry nothing at all - no id, no name, no test hook, and a class
 * ({@code form-control search-input}) it shares with its neighbour. What it does have is a
 * fixed three-column layout inside {@code #divSearchCriteria}: Release time, Expire time,
 * Status, left to right. That order is what {@link #releaseTimeInput} and the shared Expire
 * time locator address, and it is deliberately not the label text, because the localization
 * cases switch the screen into Chinese and Thai while these locators still have to resolve.
 *
 * <p>The panel also carries a warning the Real-time one does not, and it is the answer to what
 * the workbook's hours mean: <b>"Daily release time and expire time start count at 12:00 am"</b>.
 *
 * Shared list/global-settings mechanics (Expire Time, Status, Save,
 * search/filter, the table, pagination) live in {@link BaseRebateProgramPage}.
 */
public class DailyRebatePage extends BaseRebateProgramPage {

    /** This tab's half of the settings row. See {@link BaseRebateProgramPage}. */
    static final String SETTINGS_BOX = "#divSearchCriteria";

    /** Release time is the first of the panel's three columns. */
    private static final int RELEASE_TIME_COLUMN = 0;

    /** See {@link RealTimeRebatePage} - both tabs' Create buttons differ only in which is showing. */
    private static final String CSS_CREATE_BUTTON = "button.btn-primary:not(.btn-xs)";

    private final Locator releaseTimeInput;
    private final Locator createButton;

    public DailyRebatePage(Page page) {
        super(page, SETTINGS_BOX);
        this.releaseTimeInput = settingsColumn(RELEASE_TIME_COLUMN).locator("input");
        this.createButton = listRoot.locator(CSS_CREATE_BUTTON);
    }

    public void navigateTo(String url) {
        navigateAndWaitForTitle(url);
        dailyTab.click();
        waitUntilActive();
    }

    @Override
    protected void waitUntilActive() {
        waitUntilPopulated(releaseTimeInput);
    }

    public String getReleaseTimeValue() {
        awaitSettingsLoaded();
        return releaseTimeInput.inputValue();
    }

    public void setReleaseTime(String value) {
        awaitSettingsLoaded();
        releaseTimeInput.fill(value);
    }

    /** Opens the Create form, and hands back the page object for it. */
    public RebateProgramFormPage clickCreateDailyRebate() {
        waitForVisible(createButton);
        createButton.click();
        RebateProgramFormPage form = new RebateProgramFormPage(page);
        form.waitUntilOpen();
        return form;
    }

    /** True when this tab offers a Create control - the multi-program screen's own entry point. */
    public boolean isCreateAvailable() {
        return createButton.count() > 0;
    }

    /** Crosses into the Real-time tab context, returning its Page Object for continued fluent interaction. */
    public RealTimeRebatePage switchToRealTimeRebate() {
        waitForVisible(realTimeTab);
        realTimeTab.click();
        RealTimeRebatePage realTimePage = new RealTimeRebatePage(page);
        realTimePage.waitUntilActive();
        return realTimePage;
    }
}
