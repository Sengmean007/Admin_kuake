package org.sengmean.testdata;

import java.util.List;

/**
 * The literal values the client's "SMS Sent logs" workbook tab calls for.
 *
 * <h2>Where they come from</h2>
 * The TEST DATA and EXPECTED RESULTS columns of the "SMS Sent logs" tab (version 3.20.0) inside
 * {@code [TC_Admin] V.5.1.0 Weibo.xlsx} - the bright-green ({@code FF00FF00}) tab, the project's own
 * marker for "automate this" - and the Figma mockup referenced in {@code SmsSendingLogListPage}'s
 * javadoc. Unlike {@code NotificationTestData}, this feature only reads, filters and blocks existing
 * SMS log rows; it creates nothing, so there is no suite prefix to sweep.
 */
public final class SmsSendingLogTestData {

    private SmsSendingLogTestData() {
    }

    // -----------------------------------------------------------------
    // Search condition filters
    // -----------------------------------------------------------------

    public static final List<String> METHOD_OPTIONS = List.of("All", "SMS", "Voice", "Reverse SMS");
    public static final List<String> PURPOSE_OPTIONS = List.of("All", "Registration", "Forgot password", "Change phone");
    public static final List<String> DELIVERY_OPTIONS = List.of("Delivered", "Failed", "No answer", "Received", "Waiting");
    public static final List<String> VERIFICATION_OPTIONS = List.of("Verified", "Wrong code", "Pending", "Limit reached", "Expired");

    public static final String QUICK_FILTER_ALL = "All";
    public static final String QUICK_FILTER_VERIFIED_NOT_REGISTERED = "Verified, not registered";
    public static final String QUICK_FILTER_LIMIT_REACHED = "Limit reached";
    public static final String QUICK_FILTER_FAILED_DELIVERY = "Failed delivery";

    /** The sheet's own sample phone number, with and without the country code. */
    public static final String SAMPLE_PHONE_FULL = "+855 77999318";
    public static final String SAMPLE_PHONE_LOCAL = "77999318";
    public static final String SAMPLE_PHONE_PARTIAL = "318";

    public static final String SAMPLE_USERNAME = "mytest8282";
    public static final String SAMPLE_USERNAME_MISSING = "notexist_999";
    public static final String SAMPLE_USERNAME_SPACED = "  mytest8282  ";

    public static final String SAMPLE_IP = "103.25.61.12";
    public static final String SAMPLE_IP_INVALID = "999.1.1";

    public static final String SAMPLE_DOMAIN_MAIN = "1083.city";
    public static final String SAMPLE_DOMAIN_AGENT_SUB = "ag022.1083.city";

    public static final List<String> SQL_XSS_PAYLOADS = List.of("' OR '1'='1", "<img src=x onerror=alert(1)>", "' OR 1=1 --");

    // -----------------------------------------------------------------
    // Table columns
    // -----------------------------------------------------------------

    public static final List<String> LIST_COLUMNS = List.of(
            "No.", "Time", "Phone", "Method", "Purpose", "Delivery", "Verification result",
            "Codes today", "IP / Domain", "Username", "Actions");

    // -----------------------------------------------------------------
    // Summary cards
    // -----------------------------------------------------------------

    public static final String CARD_CODES_SENT = "Codes sent";
    public static final String CARD_VERIFIED = "Verified";
    public static final String CARD_FAILED_DELIVERY = "Failed delivery";
    public static final String CARD_LIMIT_REACHED = "Limit reached";
    public static final String CARD_VERIFIED_NOT_REGISTERED = "Verified, not registered";

    // -----------------------------------------------------------------
    // Daily limit and code validity (the list screen's own footnote)
    // -----------------------------------------------------------------

    /** "Daily limit: 3 codes per number (SMS + Voice), resets 00:00 HKT. Reverse SMS has no limit." */
    public static final int DAILY_LIMIT_SMS_VOICE = 3;

    /** How long a requested code stays valid before Verification result reads Expired. */
    public static final int CODE_VALIDITY_SECONDS = 120;

    // -----------------------------------------------------------------
    // Block reasons
    // -----------------------------------------------------------------

    public static final String BLOCK_REASON_SUSPECTED_ABUSE = "Suspected SMS abuse";
}
