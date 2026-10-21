package org.sengmean.testdata;

import java.util.List;

/**
 * What the System Promotion Setup rows expect of the screen - the labels and messages measured in
 * the back office's translation bundle on 2026-10-05, and the values the rows write.
 */
public final class SystemPromotionTestData {
    private SystemPromotionTestData() {
    }

    /** The English heading and the breadcrumb's last two steps. */
    public static final String HEADING_ENGLISH = "System Promotion Setup";
    public static final String MENU_DISCOUNT = "Discount";

    /**
     * The headings the language rows expect ({@code system_promotion} / section keys of the
     * bundle). Corrected 2026-10-06 against the live screen - the Live Promotion heading reads
     * "直播優惠" / "โปรโมชั่นไลฟ์สตรีม", not "直播幣優惠" / "โปรโมชั่นเหรียญไลฟ์สตรีม"; the extra
     * 幣/เหรียญ ("coin") was never on the shipped screen and failed SPS-007/008 looking for it.
     */
    public static final List<String> SECTIONS_CHINESE = List.of("通用設定", "錢包優惠", "直播優惠", "代理群組", "會員群組");
    public static final List<String> SECTIONS_THAI = List.of("การตั้งค่าโดยรวม", "โปรโมชั่นกระเป๋าเงิน", "โปรโมชั่นไลฟ์สตรีม");

    /** Offer currency choices the Wallet section offers. */
    public static final List<String> OFFER_CURRENCIES = List.of("USDT", "TRX", "THB", "HKD", "USD");

    // The validation wording of the bundle (English).
    public static final String PERCENTAGE_REQUIRED = "Percentage is required";
    public static final String PERCENTAGE_RANGE = "Percentage cannot be greater than 100 or lower than 0";
    public static final String PERCENTAGE_NOT_NUMBER = "Percentage should be type of number";
    public static final String MAX_AMOUNT_ABOVE_ZERO = "Maximum discount amount must be greater than 0";
    public static final String LIVE_PERCENTAGE_ABOVE_ZERO = "Live promotion percentage must be greater than 0";
    public static final String LIVE_MAX_AMOUNT_ABOVE_ZERO = "Live promotion maximum discount amount must be greater than 0";
    public static final String AGENT_GROUP_REQUIRED = "Please select at least one agent group";
    public static final String MEMBER_GROUP_REQUIRED = "Please select at least one member group";
    /** The server's refusal when Status is Active and the Time Range excludes now (bundle key promo_update_failed). */
    public static final String TIME_RANGE_EXCLUDES_NOW = "time range";

    // Values the writing rows use; each row restores what it found.
    public static final String DEPOSIT_MIN_AMOUNT = "123";
    public static final String DEPOSIT_TURNOVER_MULTIPLE = "3";
    public static final String WALLET_PERCENTAGE = "7.5";
    public static final String WALLET_MAX_AMOUNT = "250";
    public static final String WALLET_TURNOVER_MULTIPLE = "2";
    public static final String LIVE_PERCENTAGE = "4";
    public static final String LIVE_MAX_AMOUNT = "150";
    public static final String LIVE_TURNOVER_MULTIPLE = "5";
    /** A range wholly in the past - Status Active with it must be refused. */
    public static final String PAST_RANGE = "2020-01-01 00:00:00 - 2020-01-02 00:00:00";
}
