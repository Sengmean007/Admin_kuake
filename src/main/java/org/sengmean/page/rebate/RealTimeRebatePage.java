package org.sengmean.page.rebate;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object for the Real-time tab of the UNCS Rebate Program Setup screen
 * — Min Claim / Expire Time / Status global settings, the Real-time program
 * list, and the entry point into Create Real-time Rebate Program.
 *
 * Cross-checked against RBMP-001..RBMP-010, the cross-tab checks at RBMP-112
 * and RBMP-113, and the client-executed rows RBMP-130 and RBMP-132, in the
 * Rebate Program test case workbook. (IDs reflect the Real-time-then-Daily
 * restructure of that workbook — see its Read Me tab.)
 *
 * <p>Locators read off the running back office on 2026-09-18 — see
 * {@link BaseRebateProgramPage} for what the screen turned out to be, and for what the
 * mockup-era placeholders had got wrong. This tab's own settings panel is the one with the
 * {@code RealTime}-prefixed field names, which makes Min. Claim the most stably addressable
 * control on the whole screen.
 *
 * Shared list/global-settings mechanics (Expire Time, Status, Save,
 * search/filter, the table, pagination) live in {@link BaseRebateProgramPage}.
 */
public class RealTimeRebatePage extends BaseRebateProgramPage {

    /** This tab's half of the settings row. See {@link BaseRebateProgramPage}. */
    static final String SETTINGS_BOX = "#divSearchCriteriaRealTime";

    /**
     * The only two named form controls on the screen.
     *
     * <p>Worth using while they last: everything else on this tab has to be found by position or
     * by visibility, because the markup carries no other ids, names or test hooks.
     */
    private static final String CSS_MIN_CLAIM_INPUT = "input[name='RealTimeMinClaim']";

    /**
     * The Create control, scoped to the list view.
     *
     * <p>Both tabs. Create buttons are in the DOM at once and only the active tab.s is showing,
     * and the Create/Modify form has none at all - which is what makes {@code .btn-sm} the
     * discriminator {@link BaseRebateProgramPage#LIST_ROOT} itself is built on.
     */
    private static final String CSS_CREATE_BUTTON = "button.btn-primary:not(.btn-xs)";

    private final Locator minClaimInput;
    private final Locator createButton;

    public RealTimeRebatePage(Page page) {
        super(page, SETTINGS_BOX);
        this.minClaimInput = settingsBox.locator(CSS_MIN_CLAIM_INPUT);
        this.createButton = listRoot.locator(CSS_CREATE_BUTTON);
    }

    public void navigateTo(String url) {
        navigateAndWaitForTitle(url);
        realTimeTab.click();
        waitUntilActive();
    }

    @Override
    protected void waitUntilActive() {
        waitUntilPopulated(minClaimInput);
    }

    public String getMinClaimValue() {
        awaitSettingsLoaded();
        return minClaimInput.inputValue();
    }

    public void setMinClaim(String value) {
        awaitSettingsLoaded();
        minClaimInput.fill(value);
    }

    /**
     * Whatever is in the Min. Claim box right now, <b>without</b> waiting for it to be populated.
     *
     * <p>{@link #getMinClaimValue()} waits for a non-blank value, which is right everywhere
     * except here: {@code RBMP-009} types letters into a decimal-only field and the correct
     * outcome is an <em>empty</em> box, so waiting for one to fill would time out on the screen
     * behaving properly.
     */
    public String minClaimAsTyped() {
        return minClaimInput.inputValue();
    }

    /** Opens the Create form, and hands back the page object for it. */
    public RebateProgramFormPage clickCreateRealTimeRebate() {
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

    /** Crosses into the Daily tab context, returning its Page Object for continued fluent interaction. */
    public DailyRebatePage switchToDailyRebate() {
        waitForVisible(dailyTab);
        dailyTab.click();
        DailyRebatePage dailyPage = new DailyRebatePage(page);
        dailyPage.waitUntilActive();
        return dailyPage;
    }
}
