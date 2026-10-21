package org.sengmean.testdata;

/**
 * The fixtures and literal values the rebate workbook's cases call for, in one place.
 *
 * <h2>Why these are not just typed into the cases</h2>
 * Every value here comes from the <b>TEST DATA</b> column of
 * {@code src/test/resources/testdata/client_testcase.xlsx}, and a case that invents its own
 * instead is quietly testing something the client did not ask for. Gathering them also makes the
 * one question worth asking before the UI ships answerable at a glance: <b>which of these exist
 * on the test environment, and which still have to be seeded?</b> See
 * {@link #SEEDING_REQUIRED}.
 *
 * <h2>Programs are named by their role, not numbered</h2>
 * The workbook writes "Sample 1" and "Sample 2" for the list cases and then says things like "An
 * Inactive test program" or "Any existing test program" for the delete cases, which reads as
 * though the same two would do. They will not. Cases run in id order - see
 * {@code junit-platform.properties} - so {@code RBMP-005} deletes its program before
 * {@code RBMP-030} asks for one, and a suite that shares one fixture between them fails on the
 * second for reasons that have nothing to do with the second. Each destructive case therefore
 * gets a program of its own, and the non-destructive ones share the two the workbook names.
 */
public final class RebateTestData {

    private RebateTestData() {
    }

    // -----------------------------------------------------------------
    // Programs
    // -----------------------------------------------------------------

    /**
     * Listed, <b>Active</b>, with a non-zero rate and non-default Agent/User groups.
     *
     * <p>Carries four rows' worth of requirements between them: the workbook's "Sample 1" for the
     * list and search cases, "any existing program with non-default groups and rates" for Modify,
     * and "an Active test program with a non-zero rate" for the delete-an-Active-program case.
     * <b>Nothing may delete it</b> - {@code RBMP-031} attempts to and asserts it survives.
     */
    public static final String PROGRAM_ACTIVE = "Sample 1";
    public static final String PROGRAM_ACTIVE_1 = "Sample 1";
    public static final String PROGRAM_ACTIVE_2 = "Sample 2";
    public static final String PROGRAM_ACTIVE_3 = "Sample 3";

    /**
     * Listed and <b>Inactive</b>, so that the status filter has something to exclude.
     *
     * <p>The workbook's "Sample 2", and the other half of its "mixed-status program set".
     * Non-destructive cases only.
     */
    public static final String PROGRAM_INACTIVE = "Sample 2";
    public static final String PROGRAM_INACTIVE_1 = "Sample 2-1";
    public static final String PROGRAM_INACTIVE_2 = "Sample 2-2";
    public static final String PROGRAM_INACTIVE_3 = "Sample 2-3";

    /**
     * A program that exists only to be deleted by {@code RBMP-005} / {@code RBMP-060}.
     *
     * <p>Re-seeded between runs, because the case that uses it destroys it. The workbook calls
     * this "any existing test program"; it is separate from {@link #PROGRAM_INACTIVE} so that
     * deleting it cannot strand a later case.
     */
    public static final String PROGRAM_DISPOSABLE = "QA Delete Me";

    /**
     * An <b>Inactive</b> program that exists only to be deleted by {@code RBMP-030} /
     * {@code RBMP-085}, which are specifically about deleting one that is not Active.
     */
    public static final String PROGRAM_DISPOSABLE_INACTIVE = "QA Delete Me Inactive";

    /** A name no program has, for the create-with-a-new-name rows. */
    public static final String PROGRAM_NEW_REAL_TIME = "New Real-time Rebate";
    public static final String PROGRAM_NEW_REAL_TIME_1 = "New Real-time Rebate 1";
    public static final String PROGRAM_NEW_REAL_TIME_2 = "New Real-time Rebate 2";
    public static final String PROGRAM_NEW_REAL_TIME_3 = "New Real-time Rebate 3";

    /** As {@link #PROGRAM_NEW_REAL_TIME}, on the Daily tab. */
    public static final String PROGRAM_NEW_DAILY = "New Daily Rebate";

    // -----------------------------------------------------------------
    // Global settings  -  RBMP-008, 009, 063, 064
    // -----------------------------------------------------------------

    /** {@code RBMP-008}: "Min Claim = 10; Expire Time = 24 (hours)." */
    public static final String VALID_MIN_CLAIM = "10";

    /** {@code RBMP-008}: the Expire Time saved alongside {@link #VALID_MIN_CLAIM}. */
    public static final String VALID_EXPIRE_TIME = "24";

    /** {@code RBMP-009}: the two rejected Min Claim values, "-5, \"abc\"". */
    public static final String NEGATIVE_MIN_CLAIM = "-5";

    /** {@code RBMP-009}: the non-numeric half of the same row. */
    public static final String NON_NUMERIC_MIN_CLAIM = "abc";

    /**
     * {@code RBMP-063}: "Release Time = 20:00; Expire Time = 18:00" - deliberately inverted.
     *
     * <h2>These are clock hours, not durations</h2>
     * The client confirmed (2026-09-16) that Expire Time is whatever the admin sets it to and that
     * the {@code /hour} suffix on the field means an <b>exact clock hour</b>. So 18 is 18:00, not
     * "eighteen hours from release", and the reason 20 with 18 is invalid is that the window would
     * close two hours before it opened. The workbook's Real-time row writes the same field as
     * "24 (hours)", which reads like a duration; it is the same clock, with 24 meaning end of day.
     */
    public static final String LATE_RELEASE_TIME = "20";

    /** {@code RBMP-063}: the Expire Time that {@link #LATE_RELEASE_TIME} must not exceed. */
    public static final String EARLY_EXPIRE_TIME = "18";

    /** {@code RBMP-064}: "Release Time = 0; Expire Time = 24" - the full-day window from 00:00. */
    public static final String MIDNIGHT_RELEASE_TIME = "0";

    /** {@code RBMP-064}: the far end of that window. */
    public static final String FULL_DAY_EXPIRE_TIME = "24";

    /**
     * {@code RBMP-113}: an Expire Time distinct from {@link #VALID_EXPIRE_TIME}, so that saving
     * one type's settings and reading the other's proves independence rather than coincidence.
     */
    public static final String CROSS_TYPE_EXPIRE_TIME = "6";

    // -----------------------------------------------------------------
    // Status values - and the screens do NOT agree on the words
    // -----------------------------------------------------------------

    /**
     * The setup and modify screens spell the status <b>Enable / Disable</b>; the program list
     * displays the same state as <b>Active / Inactive</b> (confirmed by the client, 2026-09-16).
     *
     * <p>This is worth spelling out rather than smoothing over, because the obvious mistake is to
     * pick one vocabulary and use it everywhere. A case that sets "Inactive" on the settings panel
     * is choosing an option that is not there, and one that filters the list for "Disable" is
     * doing the same thing from the other end - and both fail as a missing element rather than as
     * anything that names the real problem.
     */
    public static final String SETUP_ENABLE = "Enable";

    /** The setup screens' word for off. See {@link #SETUP_ENABLE}. */
    public static final String SETUP_DISABLE = "Disable";

    /** What the <b>list</b> shows for an enabled program. */
    public static final String LIST_ACTIVE = "Active";

    /** What the <b>list</b> shows for a disabled one. */
    public static final String LIST_INACTIVE = "Inactive";

    /** The list's status filter option that excludes nothing. */
    public static final String FILTER_ALL = "All";

    // -----------------------------------------------------------------
    // Group targeting  -  A6 / B6
    // -----------------------------------------------------------------

    /**
     * The Agent group {@code RBMP-011} targets, named by the client on 2026-09-18.
     *
     * <h2>Why the suite cannot see it yet</h2>
     * The Create form's Agent picker is populated from
     * {@code GET /api/account/agent?no_paginate=true&status=1}, and for the operator this suite
     * signs in as that call answers <b>{@code {"status":-10,"message":"Permission denied"}}</b> -
     * measured 2026-09-18. The Member group picker gets the same answer from
     * {@code /api/account/member_group}. So both Option panes render empty, and they do so
     * because of an <em>account permission</em>, not because no groups exist. Anyone looking at
     * the same screen with a more privileged account sees the groups perfectly well.
     *
     * <p>That distinction matters for reading the report: the group-targeting rows are not
     * waiting on data to be created, they are waiting on {@code test.username} to be granted
     * read access to agents and member groups. Grant it and they run as written.
     */
    public static final String AGENT_GROUP_8001 = "agent-8001";

    /** The Member group {@code RBMP-011} targets. See {@link #AGENT_GROUP_8001}. */
    public static final String MEMBER_GROUP_DEFAULT_00 = "Default 00";

    /** {@code RBMP-015} / {@code RBMP-070}: "Pairing = Agent B + User Group B". */
    public static final String AGENT_GROUP_B = "Agent B";
    public static final String AGENT_GROUP_002 = "agents002";

    /** The User Group half of that pairing. */
    public static final String USER_GROUP_B = "User Group B";
    public static final String USER_GROUP_TEST = "test";

    /** {@code RBMP-042} / {@code RBMP-097}: the "Default" User Group option. */
    public static final String USER_GROUP_DEFAULT = "Default";

    /**
     * The other thing the program search matches besides the name.
     *
     * <p>The client confirmed (2026-09-16) that the box searches the <b>program name</b> and the
     * <b>pair target</b> - the Agent plus member group - and that the list's status filter covers
     * Active and Inactive. So {@code RBMP-002} and {@code RBMP-057} are two searches rather than
     * one: a program is expected back when its <em>group</em> is typed, not only its own name.
     */
    public static final String SEARCH_BY_TARGET = AGENT_GROUP_B;

    // -----------------------------------------------------------------
    // Game-category rates  -  A7 / B7
    // -----------------------------------------------------------------

    /**
     * {@code RBMP-011}: "LIVE CASINO rate = 0.3%".
     *
     * <h2>The workbook's categories are not the screen's rows</h2>
     * The workbook writes the rate table in terms of game <b>categories</b> - LIVE CASINO, SLOT -
     * and the shipped form lists twenty individual <b>games</b>: AWC68, AWC RCB988, Gaming Soft
     * BBIN CH, BOLE GAMING, 3SING, DREAM GAMING ch and so on, each with its own code. There is no
     * row called "LIVE CASINO" to set a rate on, so a case that addressed the table by the
     * workbook's word would match nothing.
     *
     * <p>The category names are kept because they are what the client's rows say, and
     * {@link #GAME_CODE_LIVE_CASINO} carries the game the suite actually uses in their place. The
     * mapping from category to games is a question for the client - the codes are opaque enough
     * ({@code GS_DG}, {@code GS_S6}) that guessing which are live-casino products would be
     * inventing coverage rather than writing it.
     */
    public static final String CATEGORY_LIVE_CASINO = "LIVE CASINO";

    /**
     * A real row of the deployed rate table, used wherever a case needs to set <em>a</em> rate.
     *
     * <p>{@code GS_DG} is "DREAM GAMING ch", which is also the product
     * {@link #REPORT_PRODUCT} names on the Rebate report - so a rate set here and a record read
     * there are about the same game. Addressed by code rather than by name because the name
     * follows the interface language and the code does not.
     */
    public static final String GAME_CODE_LIVE_CASINO = "GS_DG";

    /** A second real row, for the cases that change one rate and check another is untouched. */
    public static final String GAME_CODE_SLOT = "GS_S6";

    /** {@code RBMP-065}: "SLOT rate = 1.2%", and {@code RBMP-026}'s "SLOT 1.2% -> 1.5%". */
    public static final String CATEGORY_SLOT = "SLOT";

    public static final String RATE_LIVE_CASINO = "0.3";
    public static final String RATE_SLOT = "1.2";
    public static final String RATE_SLOT_CHANGED = "1.5";

    /** {@code RBMP-044} / {@code RBMP-099}: "One category = 0%". */
    public static final String RATE_ZERO = "0";

    /** {@code RBMP-045} / {@code RBMP-100}: "-1%". */
    public static final String RATE_NEGATIVE = "-1";

    /** {@code RBMP-046} / {@code RBMP-101}: the non-numeric rate. */
    public static final String RATE_NON_NUMERIC = "abc";

    /** {@code RBMP-048} / {@code RBMP-103}: "100%, 150%" - the boundary and the one past it. */
    public static final String RATE_AT_MAXIMUM = "100";

    /** The value above the ceiling from the same row. */
    public static final String RATE_ABOVE_MAXIMUM = "150";

    /** {@code RBMP-049} / {@code RBMP-104}: "0.375%" - three decimal places. */
    public static final String RATE_HIGH_PRECISION = "0.375";

    // -----------------------------------------------------------------
    // Volume
    // -----------------------------------------------------------------

    // -----------------------------------------------------------------
    // Rebate report  -  D2 / RBMP-134..154
    //
    // Unlike everything above, these address a screen that is deployed. The values are the
    // client TEST DATA column's own, read off the running report on 2026-09-16.
    // -----------------------------------------------------------------

    /** The report's own heading, and the breadcrumb a case can assert it arrived at. */
    public static final String REPORT_HEADING = "Rebate report";

    /** {@code RBMP-137}: "Dream Gaming" in the sheet; the live Product list spells it this way. */
    public static final String REPORT_PRODUCT = "DREAM GAMING ch";

    /** {@code RBMP-138} / {@code RBMP-145}: the Type filter's default. */
    public static final String TYPE_ALL = "All";

    /** {@code RBMP-146}: the Daily half of the Type filter. */
    public static final String TYPE_DAILY = "Daily rebate";

    /** {@code RBMP-147}: the Real-time half. */
    public static final String TYPE_REAL_TIME = "Real-time rebate";

    /** {@code RBMP-139}: the sheet asks for Settled; the live filter also offers Unsettle, Expired, Cancel. */
    public static final String REPORT_STATUS_SETTLED = "Settled";

    /** The Status filter's default. */
    public static final String REPORT_STATUS_ALL = "All";

    /** {@code RBMP-140}: an agent account the sheet expects to find records for. */
    public static final String AGENT_CODE = "agtest";

    /** {@code RBMP-141}: an agent account that does not exist, and must return No data. */
    public static final String AGENT_CODE_INVALID = "agtest31231";

    /** {@code RBMP-142}: a member the sheet expects to find records for. */
    public static final String REPORT_USERNAME = "thu0108";

    /** {@code RBMP-143}: a member that does not exist, and must return No data. */
    public static final String REPORT_USERNAME_INVALID = "dsaa123fff";

    /** {@code RBMP-153}: the report relabels its own contents in this language. */
    public static final String REPORT_LANGUAGE = "English";

    /** {@code RBMP-134} / {@code RBMP-154}: the result table's columns, left to right. */
    public static final java.util.List<String> REPORT_COLUMNS = java.util.List.of(
            "No", "Username", "Member group", "Agent code",
            "Record amount", "Total valid bet", "Total rebate amount");

    /**
     * The program list's default page size.
     *
     * <p>The client said 10 on 2026-09-16, before the screen shipped. The screen that shipped
     * offers <b>20 / 50 / 100 / 150 / 200</b> and opens on 20 - measured 2026-09-18 - and 10 is
     * not among the options at all. The deployed behaviour wins: a suite that asserts the
     * client's pre-release number fails on a list that is working correctly.
     */
    public static final int PAGE_SIZE = 20;

    /** Every page size the list offers, in the order it offers them. */
    public static final java.util.List<String> PAGE_SIZE_OPTIONS =
            java.util.List.of("20", "50", "100", "150", "200");

    /**
     * {@code RBMP-006} / {@code RBMP-061}: "more than one page's worth of programs on one tab".
     *
     * <p>Half a page past {@link #PAGE_SIZE} rather than exactly one more, so that the second page
     * has several rows on it. A list sitting on exactly 11 programs gives a second page of one,
     * which passes a pagination test while proving almost nothing about it.
     */
    public static final int PROGRAMS_FOR_PAGINATION = PAGE_SIZE + 5;

    // -----------------------------------------------------------------
    // Localization  -  C3 / RBMP-120..123, and the language rows RBMP-131 / RBMP-133
    //
    // Read off the running back office on 2026-09-18 by switching the header's own #lang
    // selector, which is the control the workbook means by "Language = English / Chinese".
    // -----------------------------------------------------------------

    // -----------------------------------------------------------------
    // Looking the expectations up by language
    //
    // The suite can be run in any language the back office offers - see BaseTest.LANGUAGE and
    // test.language in Config/. Everything a case asserts about VISIBLE TEXT therefore has to be
    // chosen by language rather than written as a constant, which is what the lookups below are
    // for.
    //
    // They cover English and Chinese, because those are the two whose wording has actually been
    // read off the running screen. THAI IS DELIBERATELY ABSENT: the back office offers it, the
    // client's document does not translate anything into it, and nobody has confirmed what it
    // should say - so there is nothing to assert and inventing it would be worse than admitting
    // that. hasExpectationsFor() lets a case ask, and fall back to a structural check.
    // -----------------------------------------------------------------

    /** Whether the verified wording for a language is recorded here. True for en and zh-TW. */
    public static boolean hasExpectationsFor(String language) {
        return ENGLISH.equals(language) || CHINESE.equals(language);
    }

    /** The option values the header's language selector carries, so the data can name them. */
    private static final String ENGLISH = "en";
    private static final String CHINESE = "zh-TW";

    /** The Rebate set up screen's heading, in the language asked for. */
    public static String heading(String language) {
        return CHINESE.equals(language) ? HEADING_CHINESE : HEADING_ENGLISH;
    }

    /** The global settings labels, in the language asked for. */
    public static java.util.List<String> settingsLabels(String language) {
        return CHINESE.equals(language) ? SETTINGS_LABELS_CHINESE : SETTINGS_LABELS_ENGLISH;
    }

    /** The program list's column headings, in the language asked for. */
    public static java.util.List<String> programColumns(String language) {
        return CHINESE.equals(language) ? PROGRAM_COLUMNS_CHINESE : PROGRAM_COLUMNS_ENGLISH;
    }

    /** The game rate table's column headings, in the language asked for. */
    public static java.util.List<String> gameColumns(String language) {
        return CHINESE.equals(language) ? GAME_COLUMNS_CHINESE : GAME_COLUMNS_ENGLISH;
    }

    /** The unit printed beside the time fields, in the language asked for. */
    public static String hourSuffix(String language) {
        return CHINESE.equals(language) ? HOUR_SUFFIX_CHINESE : HOUR_SUFFIX_ENGLISH;
    }

    /** The Rebate set up screen's heading in English. The workbook's rows call it "Rebate set up". */
    public static final String HEADING_ENGLISH = "Rebate set up";

    /**
     * The same heading in Chinese, as the screen actually renders it.
     *
     * <p>The workbook writes the Chinese title as <b>返水計劃</b> ("rebate plan"), quoting the
     * design document. The shipped screen says <b>返水配置</b> ("rebate configuration"). Both are
     * reasonable translations of the same thing and the difference is the document's rather than
     * a defect, so {@code RBMP-121} asserts what the screen says and this constant records the
     * discrepancy in one place instead of it being rediscovered per case.
     */
    public static final String HEADING_CHINESE = "返水配置";

    /** {@code RBMP-120}: the global settings labels, in English, both tabs' worth. */
    public static final java.util.List<String> SETTINGS_LABELS_ENGLISH = java.util.List.of(
            "Min. Claim", "Expire time", "Status", "Release time");

    /** {@code RBMP-121}: the same four labels in Chinese, in the same order. */
    public static final java.util.List<String> SETTINGS_LABELS_CHINESE = java.util.List.of(
            "最低領取金額", "有效時間", "狀態", "發放時間");

    /** {@code RBMP-120}: the program list's columns, left to right, in English. */
    public static final java.util.List<String> PROGRAM_COLUMNS_ENGLISH = java.util.List.of(
            "Program name", "Target Agents", "User Groups", "Status", "Last update", "Action");

    /** {@code RBMP-121}: the same columns in Chinese. */
    public static final java.util.List<String> PROGRAM_COLUMNS_CHINESE = java.util.List.of(
            "方案名稱", "目標代理", "會員群組", "狀態", "最後更新時間", "操作");

    /**
     * The unit the time fields print beside themselves, in English and in Chinese.
     *
     * <p>Worth a constant because it is the one place where translating and not translating are
     * both plausible and only one is right. {@code /hour} is a unit of <b>time</b>, so it
     * follows the language and {@code RBMP-121} asserts that it does. A currency amount is not,
     * so {@code RBMP-123} asserts the opposite about Min. Claim. Getting these the wrong way
     * round produces a suite that fails on the screen being correct.
     */
    public static final String HOUR_SUFFIX_ENGLISH = "/hour";

    /** See {@link #HOUR_SUFFIX_ENGLISH}. */
    public static final String HOUR_SUFFIX_CHINESE = "/小時";

    /** {@code RBMP-131} / {@code RBMP-133}: the game rate table's columns, in English. */
    public static final java.util.List<String> GAME_COLUMNS_ENGLISH = java.util.List.of(
            "No.", "Game name", "Game code", "Percentage%");

    /** The same, in Chinese. {@code No.} is not translated by the screen. */
    public static final java.util.List<String> GAME_COLUMNS_CHINESE = java.util.List.of(
            "No.", "遊戲名稱", "遊戲代號", "百分比%");

    /**
     * {@code RBMP-123}: the currency the row's pre-condition names, and the one every signed-in
     * suite in this project selects before it does anything else.
     *
     * <h2>Choosing it is not cosmetic: the settings are stored per currency</h2>
     * Rebate set up keeps a separate Min. Claim, Expire time, Release time and Status <b>for
     * each currency the header offers</b> - see
     * {@link org.sengmean.page.shell.AdminShellPage#selectCurrency(String)} for the four sets
     * measured on 2026-09-18. So this constant decides which configuration the whole suite reads
     * and writes, and a case that changed currency half way through would be comparing two
     * different records. Pinning every suite to one currency is what makes the modify rows
     * repeatable, and HKD is the one the client's own capture shows.
     */
    public static final String CURRENCY_HKD = "HKD";

    /** What an untranslated screen leaves behind: a raw key, a placeholder, or an empty label. */
    public static final java.util.List<String> UNTRANSLATED_MARKERS = java.util.List.of(
            "{{", "}}", "i18n", "undefined", "null.", "[object");

    // -----------------------------------------------------------------
    // Rebate set up - the client's own executed rows  -  D1 / RBMP-128..133
    //
    // The values below are this suite's, not the workbook's: the client's TEST DATA column for
    // these six rows is a literal "-". They are chosen to be valid, distinguishable from
    // whatever the environment happens to hold, and harmless - every case that writes one puts
    // the original back.
    // -----------------------------------------------------------------

    /** {@code RBMP-128}: a Daily release hour, counted from 12:00 am as the panel's own note says. */
    public static final String MODIFIED_RELEASE_TIME = "2";

    /** {@code RBMP-129}: a Daily expire hour, later than {@link #MODIFIED_RELEASE_TIME}. */
    public static final String MODIFIED_EXPIRE_TIME = "22";

    /** {@code RBMP-132}: "Enter Min. Claim with decimal amount", expecting two decimals to stick. */
    public static final String TWO_DECIMAL_MIN_CLAIM = "12.34";

    /**
     * What a person has to put on the test environment before these cases can pass.
     *
     * <p>Kept as a constant rather than a comment so that a case can quote it when it aborts, and
     * so the list has one home rather than being scattered across two test classes and a README.
     * Every line is a fixture the workbook's TEST DATA column asks for and that no automated step
     * in this project creates.
     */
    public static final String SEEDING_REQUIRED = String.join("\n",
            "The suite creates and deletes its own programs - see the E2E ordering on each test",
            "class - so the list below is only what a run cannot make for itself.",
            "",
            "Agent and User groups (the suite selects them, it does not create them):",
            "  - \"" + AGENT_GROUP_B + "\" and \"" + USER_GROUP_B + "\", to pair the created programs with",
            "  - at least two more of each, for the transfer-list and cross-product rows",
            "    (RBMP-034..042 / RBMP-089..097)",
            "Accounts:",
            "  - test.alt.username / test.alt.password in Config/ - still empty, and needed by any",
            "    case that spends wrong-password attempts",
            "  - Admin, Agent and ordinary-user accounts for C2 (RBMP-115..119): the cases are",
            "    written and waiting on test.admin.* / test.agent.* / test.user.* in Config/",
            "Settlement history:",
            "  - a program with settled and unsettled rebate entries, for RBMP-031/050/105 and the",
            "    A8/B8 rows - none of which is observable on the Rebate set up screen anyway",
            "",
            "\"" + PROGRAM_ACTIVE + "\" and \"" + PROGRAM_INACTIVE + "\" may already exist as the client's own test data,",
            "and the read-only list rows use them when they do; the lifecycle rows do not depend on",
            "them, because a suite that needs data it did not create cannot run twice in a row.");
}
