package org.sengmean.page.client;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import org.sengmean.base.BasePage;
import org.sengmean.config.ConfigDataProvider;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The member site's <b>Message Center</b> - where a Tool &gt; Notification rule, and the automatic
 * Deposit and Withdraw messages, reach a member.
 *
 * <h2>The temporary integration page</h2>
 * The designed Message Center ({@code docs/mockups/notification/message-inbox.png}) has not shipped. Until
 * it does, the member site serves a developer integration page at {@code /en/inbox-test/}
 * ({@code client.messages.path}), headed "Message Center (联调)", reachable only by address once signed in.
 * It reads the same back end the real screen will - {@code GET /api/member/message-center},
 * {@code /message-center/{id}}, {@code /message-center/unread} and {@code POST /message-center/read-all} -
 * so what it shows is what was delivered. Its layout, as rendered (inline styles only, no classes):
 * <pre>
 *   Message Center (联调) ●                              [Mark All as Read]   &lt;- ● red when anything is unread
 *   member_id / channel_id / currency, agent_id [ ] group_id [ ] 语言 [zh-TW v] [刷新]
 *   [All] [System] [Deposit] [Withdraw]
 *   ┌ <b>Title</b>                          2026-09-29 14:32:00   ● ┐   &lt;- ● red = unread
 *   │ content, one line, ellipsised                              │
 *   └ #123 · System                                              ┘
 *   [加载更多]  or  - All messages shown -
 *   推送 / 接口日志 [清空]   (the page's own push/API log)
 * </pre>
 * A card opens a modal: the title as {@code h3}, the send time, then either a table (Deposit/Withdraw:
 * Amount, Method, Transaction ID, Destination/Source Wallet, Transaction Status) or the content as plain
 * text, a {@code link:} anchor when the rule set one, and a 关闭 (Close) button.
 *
 * <p>Two things differ from the design and matter to a case: titles render in the <b>语言</b> select's
 * language, which starts at zh-TW, so {@link #open()} switches it to {@code client.messages.language}; and
 * the list pages 20 at a time, so a lookup loads every page first.
 */
public class MessageInboxPage extends BasePage {

    public static final String TAB_ALL = "All";
    public static final String TAB_SYSTEM = "System";
    public static final String TAB_DEPOSIT = "Deposit";
    public static final String TAB_WITHDRAW = "Withdraw";

    private static final String PATH = ConfigDataProvider.get("client.messages.path", "en/inbox-test/").replaceAll("^/+", "");
    private static final String LANGUAGE = ConfigDataProvider.get("client.messages.language", "en");
    /** How long {@link #awaitDelivery} keeps refreshing for a pushed message - delivery is queued (see Config/). */
    private static final int DELIVERY_WAIT_SECONDS = Integer.parseInt(ConfigDataProvider.get("client.messages.deliveryWaitSeconds", "150"));
    private static final String REFRESH = "刷新";

    /** The red used for both unread dots - the header's and each card's. */
    private static final String UNREAD_RED = "rgb(239, 68, 68)";
    /** The page's "member_id: N channel_id: N currency: HKD" line. */
    private static final Pattern INBOX_CURRENCY = Pattern.compile("currency:\\s*([A-Za-z]{3,4})");
    private static final String LOAD_MORE = "加载更多";
    private static final String CLOSE = "关闭";

    public MessageInboxPage(Page page) {
        super(page);
    }

    /** Opens Message Center at {@code client.messages.path} and shows it in {@code client.messages.language}. */
    public void open() {
        CaseLog.step("Opening the member site's Message Center at /" + PATH + ".");
        page.navigate(MemberSignInPage.CLIENT_URL + "/" + PATH, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        try {
            heading().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (RuntimeException notThere) {
            CaseLog.data("Message Center", "not shown at " + page.url());
            return;
        }
        awaitList();
        selectLanguage(LANGUAGE);
    }

    private Locator heading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("^\\s*Message Center")));
    }

    public boolean isShowing() {
        return heading().count() > 0 && heading().first().isVisible();
    }

    /** The currency the page reads the member in - its "currency:" line - or "" when it shows none. */
    public String currency() {
        Matcher m = INBOX_CURRENCY.matcher(page.locator("body").innerText());
        return m.find() ? m.group(1).toUpperCase() : "";
    }

    /**
     * The page's <b>语言</b> select - the member's app language, as far as this page has one (Si, 2026-09-30:
     * the Localization rows choose the member's language here). Found by its 语言 label; failing that, any
     * select offering {@code language}.
     */
    private Locator languageSelect(String language) {
        Locator labelled = page.locator("xpath=//*[contains(normalize-space(text()),'语言')]/following::select[1]");
        Locator offering = page.locator("select").filter(new Locator.FilterOptions().setHas(page.locator("option[value='" + language + "']")));
        return labelled.or(offering).first();
    }

    /** The language codes the 语言 select offers, e.g. zh-TW, en, bn. */
    public List<String> languageOptions() {
        Locator select = languageSelect(LANGUAGE);
        if (select.count() == 0) {
            return List.of();
        }
        Object raw = select.locator("option").evaluateAll("os => os.map(o => o.value)");
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /**
     * Switches the page's 语言 select, which decides which language each title and content renders in.
     * Returns false, switching nothing, when the select is missing or does not offer {@code language}.
     */
    public boolean selectLanguage(String language) {
        Locator select = languageSelect(language);
        if (select.count() == 0 || select.locator("option[value='" + language + "']").count() == 0) {
            CaseLog.data("Message Center 语言", language + " is not offered; the select offers " + languageOptions());
            return false;
        }
        CaseLog.step("Showing Message Center in " + language + " (the 语言 select).");
        select.selectOption(language);
        page.waitForTimeout(500);
        awaitList();
        return true;
    }

    /** Waits for the list to settle: Loading... gone, and either the end marker or Load more shown. */
    private void awaitList() {
        page.waitForTimeout(800);
        for (int i = 0; i < 30; i++) {
            boolean loading = page.getByText("Loading...", new Page.GetByTextOptions().setExact(true)).count() > 0;
            boolean settled = page.getByText("- All messages shown -").count() > 0 || loadMore().count() > 0;
            if (!loading && settled) {
                return;
            }
            page.waitForTimeout(500);
        }
    }

    private Locator loadMore() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOAD_MORE).setExact(true));
    }

    /** Pages through 加载更多 until the whole tab is listed. */
    private void loadAll() {
        for (int i = 0; i < 20 && loadMore().count() > 0; i++) {
            loadMore().first().click();
            awaitList();
        }
    }

    /** The four tabs, as shown. */
    public List<String> tabs() {
        List<String> shown = new ArrayList<>();
        for (String tab : List.of(TAB_ALL, TAB_SYSTEM, TAB_DEPOSIT, TAB_WITHDRAW)) {
            if (tab(tab).count() > 0) {
                shown.add(tab);
            }
        }
        return shown;
    }

    private Locator tab(String label) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(label).setExact(true));
    }

    public void selectTab(String label) {
        CaseLog.step("Selecting the " + label + " tab in Message Center.");
        tab(label).first().click();
        awaitList();
    }

    /** True when the header's red dot is lit - the page's "something is unread" signal. */
    public boolean hasUnreadIndicator() {
        Locator dot = heading().first().locator("span");
        return dot.count() > 0 && UNREAD_RED.equals(dot.first().evaluate("d => getComputedStyle(d).backgroundColor"));
    }

    // ---------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------

    /**
     * One message card, as listed. {@code when} is the send time, {@code YYYY-MM-DD HH:mm:ss}; {@code id} is
     * the card's own "#123" number, which stays the same whatever language the 语言 select renders it in.
     */
    public record Message(String id, String title, String preview, String when, boolean unread) {
    }

    /** The card titled {@code title} on the current tab, or null. */
    public Message message(String title) {
        return messages().stream().filter(m -> m.title().equals(title)).findFirst().orElse(null);
    }

    /** The card numbered {@code id} on the current tab, whatever its title reads now, or null. */
    public Message messageById(String id) {
        return messages().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }

    /** Every card: the clickable panels, each led by a bold title. */
    private Locator cards() {
        return page.locator("xpath=//div[contains(@style,'cursor: pointer')][.//b]");
    }

    /** The card whose bold title is exactly {@code title}. */
    private Locator card(String title) {
        return cards().filter(new Locator.FilterOptions().setHas(page.locator("b").getByText(title, new Locator.GetByTextOptions().setExact(true))));
    }

    /**
     * Waits for a just-pushed message to arrive, refreshing the list every few seconds for up to
     * {@code client.messages.deliveryWaitSeconds}. Returns whether it arrived. The site queues delivery -
     * measured 2026-09-30, a firing at 11:00:00 reached the inbox at 11:00:41 - so a single read taken
     * seconds after Send Now decides nothing. Stays on the All tab; select a tab afterwards.
     */
    public boolean awaitDelivery(String title) {
        CaseLog.step("Waiting up to " + DELIVERY_WAIT_SECONDS + "s for \"" + title + "\" to be delivered.");
        long deadline = System.currentTimeMillis() + DELIVERY_WAIT_SECONDS * 1000L;
        int waited = 0;
        while (true) {
            loadAll();
            Locator c = card(title);
            if (c.count() > 0 && c.first().isVisible()) {
                CaseLog.data("\"" + title + "\" delivered", "after about " + waited + "s");
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                CaseLog.data("\"" + title + "\" delivered", "not within " + DELIVERY_WAIT_SECONDS + "s");
                return false;
            }
            page.waitForTimeout(5_000);
            waited += 5;
            refresh();
        }
    }

    /** Re-reads the list: the page's own 刷新 button when it is there, a reload (re-selecting the language) otherwise. */
    public void refresh() {
        Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(REFRESH).setExact(true));
        if (button.count() > 0) {
            button.first().click();
        } else {
            page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            awaitReady();
            selectLanguage(LANGUAGE);
        }
        awaitList();
    }

    public boolean isListed(String title) {
        loadAll();
        Locator c = card(title);
        boolean listed = c.count() > 0 && c.first().isVisible();
        CaseLog.check("\"" + title + "\" listed in Message Center: " + listed);
        return listed;
    }

    /** Every card on the current tab, read in document order. */
    public List<Message> messages() {
        loadAll();
        Object raw = page.evaluate("() => [...document.querySelectorAll('div')].filter(c => (c.getAttribute('style') || '').includes('cursor: pointer') && c.querySelector('b'))"
                + ".map(c => { const body = c.firstElementChild; const rows = body ? body.children : [];"
                + " const title = c.querySelector('b').innerText.trim();"
                + " const when = rows[0] && rows[0].querySelector('span') ? rows[0].querySelector('span').innerText.trim() : '';"
                + " const preview = rows[1] ? rows[1].innerText.trim() : '';"
                + " const dot = [...c.children].some(s => s.tagName === 'SPAN' && getComputedStyle(s).backgroundColor === '" + UNREAD_RED + "');"
                + " const id = ((c.innerText.match(/#(\\d+)/) || [])[1]) || '';"
                + " return [id, title, preview, when, dot]; })");
        List<Message> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof List<?> f && f.size() == 5) {
                    out.add(new Message(String.valueOf(f.get(0)), String.valueOf(f.get(1)), String.valueOf(f.get(2)), String.valueOf(f.get(3)), Boolean.TRUE.equals(f.get(4))));
                }
            }
        }
        CaseLog.data("Messages listed", String.valueOf(out.size()));
        return out;
    }

    /** True when the message's card carries its red unread dot - a direct child span, beside the text. */
    public boolean isUnread(String title) {
        loadAll();
        Locator c = card(title);
        if (c.count() == 0) {
            return false;
        }
        Object red = c.first().evaluate("c => [...c.children].some(s => s.tagName === 'SPAN' && getComputedStyle(s).backgroundColor === '" + UNREAD_RED + "')");
        return Boolean.TRUE.equals(red);
    }

    public void markAllAsRead() {
        CaseLog.step("Tapping Mark All as Read.");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mark All as Read").setExact(true)).first().click();
        page.waitForTimeout(1500);
    }

    /** Opens a message's detail modal. */
    public void openMessage(String title) {
        CaseLog.step("Opening the message \"" + title + "\".");
        loadAll();
        card(title).first().click();
        modal().locator("h3").first().waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        page.waitForTimeout(500);
    }

    /** The detail modal: the panel inside the fixed overlay. */
    private Locator modal() {
        return page.locator("xpath=//div[contains(@style,'position: fixed')][.//h3]/div").first();
    }

    /** Closes the detail modal, back to the list. */
    public void back() {
        Locator close = modal().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CLOSE).setExact(true));
        if (close.count() > 0) {
            close.first().click();
        } else {
            page.keyboard().press("Escape");
        }
        page.waitForTimeout(800);
    }

    // ---------------------------------------------------------------
    // Message detail
    // ---------------------------------------------------------------

    /** The open modal's title, send time, body and link, as text. */
    public String detailText() {
        return modal().innerText().replaceAll("[ \\t]+", " ").trim();
    }

    /**
     * A Deposit/Withdraw detail's structured rows - "Deposit Amount" -&gt; "HKD 10000" and so on. Empty for
     * a System message, whose content is plain text instead of a table.
     */
    public Map<String, String> detailFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        Locator rows = modal().locator("table tr");
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() >= 2) {
                fields.put(cells.nth(0).innerText().trim(), cells.nth(1).innerText().trim());
            }
        }
        return fields;
    }

    /**
     * Follows the modal's {@code link:} anchor and returns its href, or "" when the message has none. The
     * anchor opens a new tab; that tab is read for the log and closed, leaving this page on the modal.
     */
    public String followLink() {
        CaseLog.step("Tapping through the message's link.");
        Locator link = modal().locator("a[href]");
        if (link.count() == 0) {
            CaseLog.data("Message link", "none shown");
            return "";
        }
        String href = link.first().getAttribute("href");
        try {
            Page opened = page.context().waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(10_000), () -> link.first().click());
            awaitReady(opened);
            CaseLog.data("Link opened", opened.url());
            opened.close();
        } catch (RuntimeException noNewTab) {
            page.waitForTimeout(2500);
        }
        return href;
    }
}
