package org.sengmean.page.shell;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.sengmean.base.BasePage;
import org.sengmean.report.CaseLog;

/**
 * The chrome the back office draws around every screen: the header's language and currency
 * selectors, and the side navigation.
 *
 * <h2>Why this is not part of a rebate page object</h2>
 * Nothing here belongs to Rebate set up. The same header and the same menu are on the Control
 * Panel, on Rebate report and on Daily mission setting, and the cases that need them are asking
 * about the <em>shell</em> rather than about the screen inside it - "does this role see a Rebate
 * management menu at all" ({@code RBMP-117}, {@code RBMP-118}) and "does the whole page relabel
 * itself when the language changes" ({@code RBMP-120}, {@code RBMP-121}, {@code RBMP-131},
 * {@code RBMP-133}). Putting them on {@code RealTimeRebatePage} would mean a localization case
 * on some future screen either duplicating them or reaching into the rebate package for them.
 *
 * <h2>Locators, read off the running back office on 2026-09-18</h2>
 * Both selectors are real {@code <select>} elements with stable ids - {@code #lang} and
 * {@code #currency} - which is unusually kind of this site and worth using while it lasts. The
 * language <b>values</b> are what matter and they are not the labels: the option reading
 * "Chinese" carries {@code zh-TW}, not {@code zh}. Selecting by label was tried first and
 * Playwright answered "did not find some options", so everything here selects by value.
 *
 * <p>The side navigation links are addressed by {@code href} rather than by their text, because
 * the text is exactly what the localization cases change.
 */
public class AdminShellPage extends BasePage {

    /** The header language selector. Its option values are {@code en}, {@code zh-TW}, {@code th}. */
    public static final String ENGLISH = "en";

    /** See {@link #ENGLISH}. The workbook writes this as "Chinese"; the option value is Taiwan Chinese. */
    public static final String CHINESE = "zh-TW";

    /** The third language the back office offers, which the workbook does not mention. */
    public static final String THAI = "th";

    /** Where Rebate set up lives, and what a menu entry for it points at. */
    public static final String REBATE_SET_UP_HREF = "/rebate/set-up";

    /** Where Rebate report lives. */
    public static final String REBATE_REPORT_HREF = "/rebate/report";

    /**
     * Where Product &gt; Other Category is expected to live. Not yet confirmed - the feature was not
     * deployed on 2026-09-23 - so this is {@code test.otherCategory.path} from {@code Config/}.
     */
    public static final String OTHER_CATEGORY_HREF =
            "/" + org.sengmean.config.ConfigDataProvider.get("test.otherCategory.path",
                    "product/other-category");

    /**
     * Where Tool &gt; Notification lives: {@code test.notification.path} from {@code Config/}. Measured
     * 2026-09-28 on the shipped screen, the menu entry links to {@code /tools/notification} - plural,
     * not the {@code /tool/notification} the captures suggested.
     */
    public static final String NOTIFICATION_HREF =
            "/" + org.sengmean.config.ConfigDataProvider.get("test.notification.path", "tools/notification");

    /**
     * Where Discount > System Promotion Setup lives: {@code test.systemPromotion.path} from
     * {@code Config/}. Measured 2026-10-05 from the Nuxt route table ({@code /promo/system-promotion});
     * the Discount menu entry that would link to it is hidden for an operator without the permission.
     */
    public static final String SYSTEM_PROMOTION_HREF =
            "/" + org.sengmean.config.ConfigDataProvider.get("test.systemPromotion.path", "promo/system-promotion");

    /**
     * Where Trade &gt; Transaction Records lives: {@code test.systemPromotionRecords.path} from
     * {@code Config/}. Found 2026-10-06 from the Control Panel's "Today total deposit amount" tile
     * ("More details"), not from the side navigation - {@code Trade} is not one of its entries for
     * {@code qa0001}, the same way {@code Discount} is hidden. The screen is where a qualifying
     * deposit's Main Deposit, System Promotion and System Live Promotion records are meant to show
     * (GD-106/108/111-117); see {@code SystemPromotionRecordsTest}.
     */
    public static final String TRANSACTION_RECORDS_HREF =
            "/" + org.sengmean.config.ConfigDataProvider.get("test.systemPromotionRecords.path", "ticketing/ticketing-history");

    /**
     * Where Membership and Referral &gt; Rankings is expected to live: {@code test.rankings.path}
     * from {@code Config/}. Not yet confirmed - measured live 2026-10-21, "Membership and
     * Referrals" offers no Rankings entry at all (only Membership and Rewards, Others Referral
     * Setting, Referral Setting, Referral Relationship, Referral Bonus) - so this is a guess from
     * the sibling menu entries' own addressing style (e.g. {@code member/other-referral-setting}),
     * not a measured path. See {@code RankingListPage}.
     */
    public static final String RANKINGS_HREF =
            "/" + org.sengmean.config.ConfigDataProvider.get("test.rankings.path", "member/rankings");

    /**
     * Where Web content management &gt; Member Benefits lives. Measured live on admin-test
     * 2026-10-22: the menu entry links to {@code /cms/member-benefits}, the only Web content
     * management entry (besides Game page banner) deployed at that date. See
     * {@code MemberBenefitsListPage}.
     */
    public static final String MEMBER_BENEFITS_HREF = "/cms/member-benefits";

    /**
     * Where Web content management &gt; Share link lives: {@code test.shareLink.path} from
     * {@code Config/}. Confirmed live 2026-10-26 - the menu now carries a "Share link" entry
     * linking to exactly the guessed default below, {@code /cms/share-link}. See
     * {@code ShareLinkListPage}.
     */
    public static final String SHARE_LINK_HREF =
            "/" + org.sengmean.config.ConfigDataProvider.get("test.shareLink.path", "cms/share-link");

    /**
     * Where Tool &gt; SMS Sending Log is expected to live: {@code test.smsSendingLog.path} from
     * {@code Config/}. Not yet confirmed - measured live 2026-10-21, Tool offers only Notification;
     * no SMS Sending Log entry. The default below follows Notification's own {@code /tools/<name>}
     * addressing ({@code /tools/notification}), which is a grounded guess, not a measured path.
     * See {@code SmsSendingLogListPage}.
     */
    public static final String SMS_SENDING_LOG_HREF =
            "/" + org.sengmean.config.ConfigDataProvider.get("test.smsSendingLog.path", "tools/sms-sending-log");

    /**
     * Where Web content management &gt; Quick Tour Banner lives: {@code test.quickTour.path} from
     * {@code Config/}. The guessed path was right - confirmed live 2026-10-24, where Web content
     * management now also offers Welcome Banner, Banner management, Promotion Banner and Fast
     * login banner beside the Member Benefits and Game page banner seen on 2026-10-21/22. See
     * {@code QuickTourBannerListPage}'s own javadoc for what else was measured that day.
     */
    public static final String QUICK_TOUR_HREF =
            "/" + org.sengmean.config.ConfigDataProvider.get("test.quickTour.path", "cms/quick-tour-banner");


    private final Locator languageSelect;
    private final Locator currencySelect;
    private final Locator accountName;

    public AdminShellPage(Page page) {
        super(page);
        this.languageSelect = page.locator("#lang");
        this.currencySelect = page.locator("#currency");
        this.accountName = page.locator(".user-menu .hidden-xs");
    }

    // ---------------------------------------------------------------
    // Language
    // ---------------------------------------------------------------

    /** The language the header is currently set to, as one of {@link #ENGLISH} and friends. */
    public String language() {
        return languageSelect.inputValue();
    }

    /**
     * Switches the interface language and waits until the page has finished relabelling itself.
     *
     * <p>The switch re-renders in place rather than navigating - the URL is unchanged - so there
     * is no load event to wait for. What can be waited on is the heading changing away from what
     * it said before, which is the observable the localization cases are about anyway.
     */
    public void selectLanguage(String value) {
        String before = headingText();
        CaseLog.step("Switching the interface language to " + value + ".");
        languageSelect.selectOption(value);
        try {
            page.waitForCondition(() -> !headingText().equals(before) || language().equals(value),
                    new Page.WaitForConditionOptions().setTimeout(20_000));
        } catch (RuntimeException reRendering) {
            // A switch that reloads the page takes the polled elements with it; the settle below covers it.
        }
        awaitReady();
        page.waitForTimeout(SETTLE_AFTER_LANGUAGE_MS);
        CaseLog.data("Language", language() + " (heading now \"" + headingText() + "\")");
    }

    /**
     * How long the re-render is given to finish after the heading has changed.
     *
     * <p>The heading is the first thing to change and the table headers are among the last, so a
     * case that read them the instant the heading moved would occasionally read the old ones.
     */
    private static final double SETTLE_AFTER_LANGUAGE_MS = 3000;

    /**
     * Puts the back office into the language a run is configured for, once.
     *
     * <h2>Idempotent, and checked</h2>
     * The selector is sticky per account, so a suite cannot assume it starts anywhere in
     * particular - but it also must not switch when it is already there, because every switch
     * costs a re-render. This sets it only when it differs.
     *
     * <p>An unknown value fails immediately and says what <em>is</em> offered, rather than
     * letting {@code selectOption} time out. The commonest way to get this wrong is
     * {@code "zh"}: the site's Chinese option carries <b>{@code zh-TW}</b>, and a plain
     * {@code zh} matches nothing.
     *
     * @throws IllegalArgumentException when the site does not offer that language
     */
    public void useLanguage(String value) {
        java.util.List<String> offered = languageValues();
        if (!offered.contains(value)) {
            throw new IllegalArgumentException("The back office does not offer the language \""
                    + value + "\". It offers " + offered + ". Set test.language in Config/ to one "
                    + "of those - they are the selector's own option values, not the words it "
                    + "displays, so Chinese is \"zh-TW\" rather than \"zh\".");
        }
        if (!value.equals(language())) {
            selectLanguage(value);
        }
        CaseLog.data("Run language", language());
    }

    /** The language options the header offers, by value. */
    public java.util.List<String> languageValues() {
        return optionValues("#lang option");
    }

    // ---------------------------------------------------------------
    // Currency
    // ---------------------------------------------------------------

    /** The currency the header filter is set to. */
    public String currency() {
        return currencySelect.inputValue();
    }

    /**
     * Sets the header currency.
     *
     * <h2>It is not a display filter, and treating it as one costs data</h2>
     * The obvious reading is that this only changes which records the screens below report on -
     * the member site's equivalent switches a wallet, and the back office holds no balance, so a
     * harmless filter is the natural guess. It is wrong. <b>Rebate set up stores a separate set
     * of global settings per currency</b>, and this selector chooses which set the panel is
     * editing. Measured on 2026-09-18, the Real-time panel held:
     *
     * <pre>
     *   USDT  Min. Claim 0.05   Expire 8
     *   HKD   Min. Claim 19     Expire 23
     *   THB   Min. Claim 1      Expire 2
     *   USD   Min. Claim 1      Expire 21
     * </pre>
     *
     * <p>So a suite that switches currency and then saves has written to a different tenant's
     * configuration than the one it read before switching, and a case that reads a value, changes
     * currency and reads again is looking at two different records rather than at one record
     * twice. Every case that writes must read, change and restore <b>within one currency</b>.
     * This was found the hard way: a run that saved while the panel was still loading left a test
     * value in the HKD settings while the USDT ones - the only ones anybody was looking at - read
     * perfectly normal.
     */
    /**
     * Picks the header currency.
     *
     * <h2>The fixed settle wait is not always enough, and a silent miss here poisons everything after it</h2>
     * Found live 2026-10-06, investigating why {@code SystemPromotionRecordsTest}'s own promotion-record
     * probe kept reading false for an account that demonstrably has dozens of them under HKD: this
     * operator's account defaults to <b>USDT</b> on every fresh sign-in (confirmed by logging in fresh
     * and reading the header before touching anything), so every HKD-scoped run depends on this switch
     * actually taking. The fixed {@link #SETTLE_AFTER_LANGUAGE_MS} wait is the same shape of bug already
     * found and fixed three times this same day elsewhere on this project (a confirm prompt, a search's
     * table repaint, the member site's own currency switch) - a flat sleep that is usually enough and
     * occasionally is not, with no way for the caller to tell the difference from a request the site
     * genuinely refused. This now polls {@link #currency()} for up to five more seconds past the fixed
     * wait, and logs plainly if the switch still did not take, rather than reporting whatever is
     * actually selected as if it were always the one asked for.
     */
    public void selectCurrency(String code) {
        if (code.equals(currency())) {
            return;
        }
        CaseLog.step("Setting the header currency filter to " + code + ".");
        currencySelect.selectOption(code);
        page.waitForTimeout(SETTLE_AFTER_LANGUAGE_MS);
        for (int round = 0; round < 5 && !code.equals(currency()); round++) {
            page.waitForTimeout(1_000);
        }
        String now = currency();
        if (!code.equals(now)) {
            CaseLog.evidence("The header currency did not switch to " + code + " - it still reads \"" + now + "\".");
        }
        CaseLog.data("Currency", now);
    }

    /**
     * Puts the back office into the currency a run is configured for, once.
     *
     * <p>The counterpart of {@link #useLanguage(String)}, and the more dangerous of the two to
     * get wrong: the language only changes what the screen says, while the currency changes
     * <b>which records it is showing at all</b> - see {@link #selectCurrency(String)}. Checked
     * against what the header actually offers, so a typo fails immediately rather than leaving
     * the run quietly pointed at the default.
     *
     * @throws IllegalArgumentException when the site does not offer that currency
     */
    public void useCurrency(String code) {
        java.util.List<String> offered = currencyValues();
        if (!offered.contains(code)) {
            throw new IllegalArgumentException("The back office does not offer the currency \""
                    + code + "\". It offers " + offered + ". Set test.currency in Config/ to one "
                    + "of those.");
        }
        selectCurrency(code);
        CaseLog.data("Run currency", currency());
    }

    /** The currencies the header offers, by value. */
    public java.util.List<String> currencyValues() {
        return optionValues("#currency option");
    }

    /**
     * The {@code value} attribute of every <b>selectable</b> option a selector offers, in the
     * order it offers them - a {@code disabled} option (a placeholder or group heading, not a
     * real choice) is left out.
     *
     * <h2>Why this matters</h2>
     * Measured live on admin-test.138hk.vip 2026-10-23: {@code #currency}'s first option is a
     * disabled "Fiat Currency" heading, value {@code ""}. Before this filter,
     * {@link #currencyValues()} returned it as if it were a real currency, and every caller that
     * iterates "every currency the header offers" - {@code MemberBenefitsSeedData},
     * {@code ShareLinkSeedData} - tried {@code selectOption("")} on it first. Playwright refuses a
     * disabled option outright ("option being selected is not enabled") and keeps retrying until
     * {@code test.actionTimeout} runs out, so every one of those runs failed on the very first
     * currency before anything else in the case had a chance to run.
     */
    private java.util.List<String> optionValues(String selector) {
        Object raw = page.locator(selector).evaluateAll("options => options.filter(o => !o.disabled).map(o => o.value)");
        return raw instanceof java.util.List<?> list
                ? list.stream().map(String::valueOf).toList()
                : java.util.List.of();
    }

    // ---------------------------------------------------------------
    // Navigation and identity
    // ---------------------------------------------------------------

    /** True when the side navigation offers the screen at {@code href} to whoever is signed in. */
    public boolean hasMenuLink(String href) {
        return page.locator("a[href='" + href + "']").count() > 0;
    }

    /** The account the header names, or empty when nobody is signed in. */
    public String signedInAccount() {
        return accountName.count() > 0 ? accountName.first().innerText().trim() : "";
    }

    /** True when the shell is showing a signed-in session rather than the sign-in form. */
    public boolean isSignedIn() {
        return !signedInAccount().isEmpty();
    }

    /** The screen's own {@code h1}, which is what the language switch is most visibly measured on. */
    /**
     * The header clock - "2026-September-30 17:39" beside the account name - or "" when it is not shown. The
     * back office draws it in Hong Kong Time, the clock every schedule runs on (NOT-REG-010).
     */
    public String headerClock() {
        String text = page.locator("body").innerText();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{4}-[A-Za-z]+-\\d{1,2} \\d{2}:\\d{2}").matcher(text == null ? "" : text);
        return m.find() ? m.group() : "";
    }

    public String headingText() {
        Locator heading = page.locator("h1");
        return heading.count() > 0 ? heading.first().innerText().trim() : "";
    }
}
