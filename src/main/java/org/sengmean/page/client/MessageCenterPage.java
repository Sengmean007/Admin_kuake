package org.sengmean.page.client;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import org.sengmean.base.BasePage;
import org.sengmean.page.product.ClientCategoryHomePage;
import org.sengmean.page.product.ClientCategoryPage;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The <b>Message Center</b> (Notification Center) of the client platform's new UI, at
 * {@code /{locale}/messages} with a message's own page at {@code /{locale}/messages/{id}} -
 * measured on {@code https://test-v2.138hk.vip} on 2026-10-02.
 *
 * <h2>What the screen shows</h2>
 * <pre>
 *   [‹]  Message Center                               [Mark All as Read]
 *   [All] [System] [Deposit] [Withdraw]                 <- role=tablist, aria-selected
 *   Today
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │ (icon)  Title                               12:30   ●        │   <- ● only while unread
 *   │         preview, two lines at most                            │
 *   └──────────────────────────────────────────────────────────────┘
 *   Yesterday ...                                "All messages shown" at the end
 *                                                "No messages yet" when there are none
 * </pre>
 * The list loads more as it scrolls (an observer on its last row). Opening a message pushes
 * {@code /messages/{id}}: "Message Details" under a Back control, the message's icon, title and
 * body. "Mark All as Read" is disabled while nothing is unread. A visitor who is not signed in
 * sees the same shell, empty, with no login ask - recorded by the rows, not assumed.
 *
 * <h2>How it is driven</h2>
 * The New UI's shell - the version picker, sign-in, the overlays that cover the page - is
 * {@link ClientCategoryHomePage}'s, so this page composes one rather than repeating it.
 */
public class MessageCenterPage extends BasePage {

    /** The list and its message page, under the locale. */
    public static final String ROUTE = "messages";

    /**
     * The site's header is not a {@code <header>} element but a {@code div.Header_wrapper__…}, and for
     * a signed-in member it holds a <b>message popover</b> (the envelope button, {@code aria-label=
     * "Message Center"}) that renders a second copy of the whole list - heading, Mark All as Read,
     * the four tabs, the rows, "All messages shown". Every lookup on this page therefore excludes
     * the header's subtree, or a member would read eight tabs and the popover's buttons first.
     */
    private static final String OUTSIDE_HEADER = "[not(ancestor::*[contains(@class,'Header_')])]";
    private static final String TABLIST = "xpath=//*[@role='tablist']" + OUTSIDE_HEADER;
    private static final String TAB = TABLIST + "//button[@role='tab']";
    private static final Pattern MESSAGE_CENTER = Pattern.compile("message center", Pattern.CASE_INSENSITIVE);
    private static final Pattern MESSAGE_DETAILS = Pattern.compile("message details", Pattern.CASE_INSENSITIVE);
    private static final String EMPTY = "No messages yet";
    private static final String END_OF_LIST = "All messages shown";
    /**
     * One message row. Since the redesign seen on 2026-10-05 a row is a {@code button.rounded-[14px]}
     * in the list box that follows the tab row (title {@code span.truncate.font-bold}, time
     * {@code span.text-[11px]}, preview {@code span.line-clamp-2}, the unread dot {@code .bg-red-500});
     * before it, a {@code div.odd:bg-[#F6FAFF]}. Both are read, outside the header popover.
     */
    private static final String ITEM = "xpath=(//*[@role='tablist']" + OUTSIDE_HEADER + "/following-sibling::div//button[.//span[contains(@class,'truncate')]]"
            + " | //div[contains(@class,'odd:bg-')]" + OUTSIDE_HEADER + ")";
    /** The same rows for a script: the list box after the page's own tab row, else the old rows. */
    private static final String ITEMS_JS = "(() => { const tl = [...document.querySelectorAll('[role=tablist]')].find(t => !t.closest('[class*=\"Header_\"]'));"
            + " const box = tl && tl.nextElementSibling; const rows = box ? [...box.querySelectorAll('button')].filter(b => b.querySelector('[class*=\"truncate\"]')) : [];"
            + " return rows.length ? rows : [...document.querySelectorAll('div[class*=\"odd:bg-\"]')].filter(r => !r.closest('[class*=\"Header_\"]')); })()";
    private static final String UNREAD_DOT = ".bg-red-500";
    /** The back control: the round button holding a left chevron, on the list and on a message. */
    private static final String BACK = "xpath=//button" + OUTSIDE_HEADER + "[.//*[local-name()='svg'][@data-icon='chevron-left' or @data-icon='arrow-left']]";
    /** Mark All as Read, the page's own (the header popover has one too). */
    private static final String MARK_ALL_BUTTON = "xpath=//button" + OUTSIDE_HEADER
            + "[contains(translate(normalize-space(.),'MARKLSED','marklsed'),'mark all as read')]";
    /**
     * The header's control leading to the Message Center: the envelope button (shown from 1024px
     * up), a link to /messages, or the phone layout's bottom-bar "Message" entry.
     */
    private static final String HEADER_CONTROL = "button[aria-label='Message Center'], a[href*='/" + ROUTE + "'], "
            + "div[class*='basis-1/5']:has(img[src*='MobileStickyNav/message'])";
    /** The popover the envelope opens: a dialog inside the header. */
    private static final String HEADER_POPOVER = "[class*='Header_'] [role='dialog']";

    private final ClientCategoryHomePage shell;

    /** A message as the list draws it. */
    public record Message(String title, String preview, String time, boolean unread, int height, boolean inView) {
    }

    public MessageCenterPage(Page page, String clientUrl) {
        super(page);
        this.shell = new ClientCategoryHomePage(page, clientUrl);
    }

    /** The New UI shell this page drives: sign-in, overlays, language. */
    public ClientCategoryHomePage shell() {
        return shell;
    }

    // ---------------------------------------------------------------
    // Opening
    // ---------------------------------------------------------------

    /**
     * Opens the Message Center by address in the language the shell was last opened in.
     *
     * <p>Retried once: {@link ClientCategoryHomePage#openPath} already answers the UI-version
     * picker if it is up <i>when it navigates</i>, but the site can land back on that picker on its
     * own a few seconds later - measured 2026-10-08, after a sign-in, on a plain {@code /messages}
     * navigation that had nothing to do with a fresh picker. {@link #awaitOpen()}'s own timeout is
     * what catches that: the tabs never show up because there is no Message Center page to show
     * them on, only the picker. One retry of the whole open answers it again rather than leaving the
     * case to fail on tabs that were never going to appear.
     */
    public void open() {
        String locale = shell.language().locale();
        for (int attempt = 1; attempt <= 2; attempt++) {
            CaseLog.step("Opening the Message Center at /" + locale + "/" + ROUTE + (attempt > 1 ? " (retry, the site landed back on the interface picker)." : "."));
            shell.openPath(locale + "/" + ROUTE);
            awaitOpen();
            if (!shell.isUiVersionPickerUp()) {
                return;
            }
        }
    }

    /**
     * The header's control that leads to the Message Center: the envelope button labelled
     * "Message Center" beside Log out (measured 2026-10-05; it opens a popover, {@code
     * aria-haspopup="dialog"}), or a link to /messages should one appear.
     */
    private Locator headerControl() {
        return page.locator(HEADER_CONTROL).filter(new Locator.FilterOptions().setVisible(true)).first();
    }

    /** True when the header offers a control leading to the Message Center. */
    public boolean isHeaderControlOffered() {
        return headerControl().count() > 0;
    }

    /**
     * What the header's message control shows as a badge: a number, a plain dot ("dot"), or empty
     * when it carries nothing beyond its icon. The badge is drawn beside the envelope rather than
     * inside it, so the control's own box (its parent) is read.
     */
    public String headerBadge() {
        Locator control = headerControl();
        if (control.count() == 0) {
            return "";
        }
        Object raw = control.evaluate("c => { const box = c.parentElement || c; const text = (box.innerText || '').trim(); if (/\\d/.test(text)) return text.match(/\\d+/)[0];"
                + " const dot = [...box.querySelectorAll('*')].find(e => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e);"
                + " return r.width > 0 && r.width <= 14 && r.height <= 14 && /rgb\\((2[0-9][0-9]|1[89][0-9]), ?([0-9]{1,2}), ?([0-9]{1,2})\\)/.test(s.backgroundColor); });"
                + " return dot ? 'dot' : ''; }");
        return raw == null ? "" : String.valueOf(raw);
    }

    /**
     * Opens the Message Center from the header's own message control and says what that opened:
     * {@code "page"} when the control navigated to the list, {@code "popover"} when it opened the
     * header's Message Center popover in place (what the envelope does, measured 2026-10-05: a
     * dialog headed Message Center with Mark All as Read, the four tabs and the rows), {@code
     * "address"} when no control was offered and the list was opened by address, or empty when
     * the click did neither.
     */
    public String openFromHeader() {
        Locator control = headerControl();
        if (control.count() == 0) {
            CaseLog.data("Header message control", "none offered; opening by address");
            open();
            return "address";
        }
        CaseLog.step("Opening the Message Center from the header's message control.");
        shell.dismissOverlays();
        control.click();
        page.waitForTimeout(1_500);
        if (page.url().contains("/" + ROUTE)) {
            awaitOpen();
            return "page";
        }
        if (isHeaderPopoverOpen()) {
            waitForList();
            return "popover";
        }
        return "";
    }

    /** The header's Message Center popover, open. */
    private Locator headerPopover() {
        return page.locator(HEADER_POPOVER).filter(new Locator.FilterOptions().setVisible(true));
    }

    public boolean isHeaderPopoverOpen() {
        return headerPopover().count() > 0;
    }

    /** The popover's heading - its first line, "Message Center" in English. */
    public String headerPopoverHeading() {
        if (!isHeaderPopoverOpen()) {
            return "";
        }
        return headerPopover().first().innerText().trim().split("\\R", 2)[0].trim();
    }

    /** The popover's tabs, in order. */
    public List<String> headerPopoverTabs() {
        Locator tabs = headerPopover().locator("[role='tab']");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tabs.count(); i++) {
            out.add(tabs.nth(i).innerText().trim());
        }
        return out;
    }

    /** Closes the popover by its own Close control, else Escape. */
    public void closeHeaderPopover() {
        if (!isHeaderPopoverOpen()) {
            return;
        }
        CaseLog.step("Closing the header's Message Center popover.");
        Locator close = headerPopover().locator("button[aria-label='Close']");
        if (close.count() > 0) {
            close.first().click();
        } else {
            page.keyboard().press("Escape");
        }
        page.waitForTimeout(600);
    }

    /** Opens a message's own page by address, as a deep link would. */
    public void openMessageByAddress(String address) {
        CaseLog.step("Opening " + address + " directly.");
        page.navigate(address, new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        shell.chooseNewUiIfAsked();
        shell.dismissOverlays();
        page.waitForTimeout(2_500);
    }

    /**
     * How the site keeps a visitor out of the Message Center, if it does: "asked to log in" (the
     * dialog or its prompt), "sent to {address}" (a redirect away), or empty when the list is shown.
     */
    public String visitorKeptOut() {
        String ask = shell.asksToLogIn();
        if (!ask.isEmpty()) {
            return "asked to log in - " + ask;
        }
        if (!page.url().contains("/" + ROUTE)) {
            return "sent to " + page.url();
        }
        return isOpen() || isMessageOpen() ? "" : "sent to " + page.url();
    }

    private void awaitOpen() {
        try {
            page.locator(TABLIST).first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (PlaywrightException none) {
            CaseLog.evidence("The Message Center's tabs did not appear within 15s; the page shows: " + page.locator("body").innerText().replaceAll("\\s+", " ").trim().substring(0, 120));
        }
        waitForList();
    }

    /** Waits for the list to settle: the loading placeholders gone, then a moment for the rows. */
    private void waitForList() {
        try {
            page.locator(".animate-pulse").first().waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN).setTimeout(10_000));
        } catch (PlaywrightException stillLoading) {
            // The placeholders may never have been drawn; the rows are read as they are.
        }
        page.waitForTimeout(1_200);
    }

    /** True while the Message Center list (its heading and tabs) is on screen. */
    public boolean isOpen() {
        return page.locator(TABLIST).count() > 0 && page.locator(TABLIST).first().isVisible()
                && page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(MESSAGE_CENTER)).count() > 0;
    }

    /** The heading text - "Message Center" in English. */
    public String heading() {
        // The page's own visible h1: not the header popover's, nor one in a panel laid over the page
        // (the Daily Mission panel has an h1 of its own, "Complete N days of 5 days").
        Object raw = page.evaluate("() => { const h = [...document.querySelectorAll('h1')].find(e => e.offsetParent !== null && !e.closest('[class*=\"Header_\"]')"
                + " && !(() => { let n = e; while (n && n !== document.body) { if (getComputedStyle(n).position === 'fixed') return true; n = n.parentElement; } return false; })());"
                + " return h ? h.innerText.trim() : ''; }");
        return raw == null ? "" : String.valueOf(raw);
    }

    // ---------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------

    /** The tabs' labels, in order: All, System, Deposit, Withdraw. */
    public List<String> tabs() {
        Locator tabs = page.locator(TAB);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tabs.count(); i++) {
            out.add(tabs.nth(i).innerText().trim());
        }
        return out;
    }

    /** The label of the tab the list is on ({@code aria-selected}), or empty. */
    public String activeTab() {
        Locator on = page.locator(TAB + "[aria-selected='true']");
        return on.count() == 0 ? "" : on.first().innerText().trim();
    }

    public void selectTab(String label) {
        CaseLog.step("Opening the " + label + " tab.");
        shell.dismissOverlays();
        page.locator(TAB).filter(new Locator.FilterOptions().setHasText(exactly(label, true))).first().click();
        waitForList();
    }

    /** True when the tab row runs wider than its box, so it scrolls sideways (phones). */
    public boolean tabsOverflow() {
        Object raw = page.locator(TABLIST).first().evaluate("t => t.scrollWidth > t.clientWidth + 1");
        return Boolean.TRUE.equals(raw);
    }

    /** True when every tab's right edge is within the window - nothing cut off. */
    public boolean tabsWithinScreen() {
        Object raw = page.locator(TABLIST).first().evaluate("t => [...t.querySelectorAll('button')].every(b => b.getBoundingClientRect().right <= window.innerWidth + 1)");
        return Boolean.TRUE.equals(raw);
    }

    /** True when the heading and Mark All as Read sit on one line within the window. */
    public boolean headerOnOneLine() {
        Object raw = page.evaluate("() => { const outside = e => !e.closest('[class*=\"Header_\"]') && e.offsetParent !== null;"
                + " const h = [...document.querySelectorAll('h1')].find(e => outside(e) && !(() => { let n = e; while (n && n !== document.body) { if (getComputedStyle(n).position === 'fixed') return true; n = n.parentElement; } return false; })());"
                + " const m = [...document.querySelectorAll('button')].find(b => outside(b) && /mark all as read/i.test(b.innerText));"
                + " if (!h || !m) return false; const a = h.getBoundingClientRect(), b = m.getBoundingClientRect();"
                + " return Math.abs(a.top - b.top) < a.height && b.right <= window.innerWidth + 1 && a.left >= -1; }");
        return Boolean.TRUE.equals(raw);
    }

    // ---------------------------------------------------------------
    // Mark All as Read
    // ---------------------------------------------------------------

    private Locator markAll() {
        return page.locator(MARK_ALL_BUTTON).first();
    }

    public boolean isMarkAllOffered() {
        return markAll().count() > 0 && markAll().isVisible();
    }

    /** True while Mark All as Read can be pressed - the site enables it only while something is unread. */
    public boolean isMarkAllEnabled() {
        return isMarkAllOffered() && markAll().isEnabled();
    }

    public void markAllAsRead() {
        CaseLog.step("Pressing Mark All as Read.");
        shell.dismissOverlays();
        markAll().click();
        page.waitForTimeout(2_000);
        waitForList();
    }

    // ---------------------------------------------------------------
    // The list
    // ---------------------------------------------------------------

    /** True when the list says "No messages yet". */
    public boolean isEmpty() {
        return pageText(EMPTY).count() > 0;
    }

    /** True when the list says "All messages shown" - its end has been reached. */
    public boolean isEndShown() {
        return pageText(END_OF_LIST).count() > 0;
    }

    /** A visible element carrying exactly this text, outside the header popover. */
    private Locator pageText(String text) {
        return page.locator("xpath=//*" + OUTSIDE_HEADER + "[normalize-space(text())=" + xpathString(text) + "]")
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    private static String xpathString(String text) {
        return text.contains("'") ? "concat('" + text.replace("'", "',\"'\",'") + "')" : "'" + text + "'";
    }

    /** The day labels drawn over the rows ("Today", "Yesterday", a date). */
    public List<String> dayLabels() {
        Object raw = page.evaluate("() => [...document.querySelectorAll('span, div')].filter(e => e.children.length === 0 && e.offsetParent !== null && !e.closest('[class*=\"Header_\"]')"
                + " && /^(Today|Yesterday|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\/\\d{1,2}(\\/\\d{2,4})?)$/.test((e.innerText || '').trim())).map(e => e.innerText.trim())");
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(o -> out.add(String.valueOf(o)));
        }
        return out;
    }

    /** Every message row drawn right now, top to bottom. */
    @SuppressWarnings("unchecked")
    public List<Message> messages() {
        Object raw = page.evaluate("""
                () => """ + ITEMS_JS + """
                .filter(r => r.offsetParent !== null).map(r => {
                  const title = r.querySelector('[class*="truncate"][class*="font-bold"]');
                  const preview = r.querySelector('[class*="line-clamp"]');
                  const time = r.querySelector('[class*="text-[11px]"]');
                  const rect = r.getBoundingClientRect();
                  return { title: title ? title.innerText.trim() : '', preview: preview ? preview.innerText.trim() : '',
                           time: time ? time.innerText.trim() : '', unread: !!r.querySelector('.bg-red-500'),
                           height: Math.round(rect.height), inView: rect.left >= -1 && rect.right <= window.innerWidth + 1 };
                })""");
        List<Message> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                out.add(new Message(str(m.get("title")), str(m.get("preview")), str(m.get("time")), Boolean.TRUE.equals(m.get("unread")),
                        m.get("height") instanceof Number n ? n.intValue() : 0, Boolean.TRUE.equals(m.get("inView"))));
            }
        }
        return out;
    }

    /** How many rows are unread (carry the red dot). */
    public long unreadCount() {
        return messages().stream().filter(Message::unread).count();
    }

    /** Scrolls the list to its end, letting it load more, until "All messages shown" or nothing more arrives. */
    public int loadWholeList() {
        int rows = messages().size();
        for (int stalls = 0; stalls < 3 && !isEndShown(); ) {
            page.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
            page.waitForTimeout(1_500);
            int now = messages().size();
            stalls = now > rows ? 0 : stalls + 1;
            rows = now;
        }
        page.evaluate("() => window.scrollTo(0, 0)");
        page.waitForTimeout(400);
        return rows;
    }

    // ---------------------------------------------------------------
    // A message's own page
    // ---------------------------------------------------------------

    /** Opens the n-th row (0-based) and waits for its page. */
    public void openMessage(int index) {
        CaseLog.step("Opening message " + (index + 1) + ".");
        shell.dismissOverlays();
        page.locator(ITEM).nth(index).click();
        try {
            page.waitForURL(Pattern.compile(".*/" + ROUTE + "/[^/]+/?$"), new Page.WaitForURLOptions().setTimeout(15_000));
        } catch (PlaywrightException stayed) {
            CaseLog.evidence("Opening the message did not change the address within 15s; still on " + page.url());
        }
        awaitReady();
        page.waitForTimeout(1_500);
    }

    /** True while a message's own page ("Message Details") is on screen. */
    public boolean isMessageOpen() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(MESSAGE_DETAILS)).count() > 0
                && page.url().matches(".*/" + ROUTE + "/[^/]+/?$");
    }

    /** The opened message's title (the first bold line under the details heading) and body text. */
    public String messageTitle() {
        Locator t = page.locator("[class*='text-[17px]'][class*='font-bold'], [class*='text-[16px]'][class*='font-bold']").filter(new Locator.FilterOptions().setVisible(true));
        return t.count() == 0 ? "" : t.first().innerText().trim();
    }

    public String messageBody() {
        Locator b = page.locator("[class*='leading-relaxed']").filter(new Locator.FilterOptions().setVisible(true));
        return b.count() == 0 ? "" : b.first().innerText().trim();
    }

    /** True when the opened message's content box fits within the window. */
    public boolean messageFitsScreen() {
        Object raw = page.evaluate("() => { const b = document.querySelector('[class*=\"leading-relaxed\"]'); if (!b) return true;"
                + " const r = b.getBoundingClientRect(); return r.left >= -1 && r.right <= window.innerWidth + 1; }");
        return Boolean.TRUE.equals(raw);
    }

    /** Presses the Back control at the top of the page and waits for the list. */
    public void back() {
        CaseLog.step("Pressing Back.");
        shell.dismissOverlays();
        page.locator(BACK).filter(new Locator.FilterOptions().setVisible(true)).first().click();
        try {
            page.waitForURL(Pattern.compile(".*/" + ROUTE + "/?$"), new Page.WaitForURLOptions().setTimeout(15_000));
        } catch (PlaywrightException stayed) {
            CaseLog.evidence("Back did not return to the list within 15s; still on " + page.url());
        }
        waitForList();
    }

    /** True when the list's Back control is offered. */
    public boolean isBackOffered() {
        return page.locator(BACK).filter(new Locator.FilterOptions().setVisible(true)).count() > 0;
    }

    /** The smallest tap target among the controls a finger uses here: tabs, Back, Mark All, rows. */
    public int smallestTapTarget() {
        Object raw = page.evaluate("() => { const els = [...document.querySelectorAll('[role=tab], button')].filter(e => e.offsetParent !== null && !e.closest('header, nav, [class*=\"Header_\"], [class*=\"NavList_\"], #cw-widget-holder, #cw-bubble-holder'));"
                + " return els.length ? Math.min(...els.map(e => { const r = e.getBoundingClientRect(); return Math.round(Math.min(r.width, r.height)); })) : 0; }");
        return raw instanceof Number n ? n.intValue() : 0;
    }

    /** How far the document runs wider than the window - 0 when nothing is cut off. */
    public int horizontalOverflow() {
        return shell.horizontalOverflow();
    }

    public int viewportWidth() {
        return shell.viewportWidth();
    }

    public String clientUrl() {
        return shell.clientUrl();
    }

    /** The category feed's origin, for rows that need to know the site these messages belong to. */
    public ClientCategoryPage feedReader() {
        return shell;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
