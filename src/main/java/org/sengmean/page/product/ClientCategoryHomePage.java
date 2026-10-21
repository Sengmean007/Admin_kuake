package org.sengmean.page.product;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import org.sengmean.report.CaseLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The <b>new UI</b> of the client platform's home page, where Other Categories are rendered as
 * sections - measured on {@code https://test-v2.138hk.vip} on 2026-10-02 as a visitor.
 *
 * <h2>What the screen shows</h2>
 * <pre>
 *   [logo]                                   [Login] [Register] [Activate] [flag v] [≡]
 *   [home] WinWin City | WeLive | ... (game navigation, scrolls with an arrow)
 *   [hero carousel]
 *   [marquee]
 *   ┌───────────────────────────────────────────────────────────────┐
 *   │ (icon) Category NAME                            More Games > │
 *   │ ‹ [card][card][card][card][card][card][card][card][card] ›    │
 *   └───────────────────────────────────────────────────────────────┘
 *   ... one such panel per category the feed serves, in feed order ...
 * </pre>
 * A panel is {@code div.rounded-lg}: a header row with the 20px icon, the name in a {@code span}
 * and a "More Games &gt;" {@code button} ("更多遊戲 &gt;" under 繁體) that pushes
 * {@code /{locale}/game-category-features/{id}}; then a row of {@code button.snap-start} cards,
 * each one {@code img} with the game's name only in its {@code alt}, 140px wide under the
 * {@code sm} breakpoint (640px) and 160px above it. A row wider than its panel gets "Scroll left" /
 * "Scroll right" arrow buttons, hidden under {@code sm}.
 *
 * <h2>What the site does around the sections</h2>
 * <ul>
 *   <li>A fresh visitor is met by "Welcome to Quark Dynasty - Choose your preferred interface to
 *       get started." with a Classic Interface card ("Choose Classic") and a New Interface card
 *       ("Choose New"); the choice is kept in {@code localStorage} as {@code ui-version}.
 *       {@link #openNewUi} answers it with New Interface, the one that renders the sections. The
 *       picker's copy changed from an earlier "No UI version - Please choose UI version. [Old UI]
 *       [New UI]" wording (found 2026-10-08, after it silently stopped matching and every signed-in
 *       case timed out on the header's Login hidden behind it).</li>
 *   <li>A visitor who opens a game gets a "Please login first" prompt with a Close button, not the
 *       Login dialog. {@link #asksToLogIn()} reads either.</li>
 *   <li>On an iPhone the site raises an "Add to Home Screen" panel over the page;
 *       {@link #dismissOverlays()} closes it.</li>
 *   <li>The language list opens from a flag button in the header on desktop and from a globe on
 *       phones; its rows are {@code HeaderTop_language-name} spans ("Traditional Chinese│繁體").</li>
 * </ul>
 */
public class ClientCategoryHomePage extends ClientCategoryPage {

    /** One category panel on the home page, its parts, and its cards. */
    private static final String SECTION = "div.rounded-lg";
    private static final String SECTION_NAME = "span";
    private static final String CARD = "button.snap-start";
    /** The panel's one button that is neither a card nor an arrow: More Games, in whatever language. */
    private static final String MORE_GAMES = "button:not(.snap-start):not([aria-label])";
    private static final String SCROLL_LEFT = "Scroll left";
    private static final String SCROLL_RIGHT = "Scroll right";

    /** The version picker, and the header's sign-in control and its dialog. */
    private static final String UI_VERSION_HEADING = "Choose your preferred interface";
    private static final Pattern LOG_IN = Pattern.compile("^\\s*log\\s*in\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLEASE_LOG_IN = Pattern.compile("please\\s+log\\s*in", Pattern.CASE_INSENSITIVE);

    /** The New UI's language control - a flag button on desktop, a globe on phones - and its rows. */
    private static final String LANGUAGE_BUTTON_DESKTOP = "button.rounded-md.border:has(svg)";
    private static final String LANGUAGE_BUTTON_PHONE = "button:has(svg[data-icon='globe'])";
    private static final String LANGUAGE_NAME = "[class*='HeaderTop_language-name']";
    private static final long LANGUAGE_SETTLE_MS = 3_500;

    /** A card as the page draws it: its image, its size, its left edge (for column counting) and whether it is within its row. */
    public record Card(String alt, String src, boolean loaded, int width, int height, int left, boolean inView) {
    }

    /** A category panel as the home page draws it. */
    public record Section(String name, String iconSrc, boolean iconLoaded, boolean moreGames, boolean leftArrow,
                          boolean rightArrow, int rowScrollWidth, int rowClientWidth, List<Card> cards) {
        /** True when the row has more cards than fit and so scrolls sideways. */
        public boolean overflows() {
            return rowScrollWidth > rowClientWidth + 1;
        }
    }

    public ClientCategoryHomePage(Page page) {
        super(page);
    }

    public ClientCategoryHomePage(Page page, String clientUrl) {
        super(page, clientUrl);
    }

    // ---------------------------------------------------------------
    // Opening the home page in the new UI
    // ---------------------------------------------------------------

    /**
     * Opens the home page in a language and, when the site asks which UI to use, chooses the New UI.
     * Closes whatever the site lays over the page, then waits for the category sections.
     *
     * <p>The welcome banner is cleared before the UI-version picker is answered too: both are
     * raised on a fresh load, and the banner's z-index sits above the picker's "Choose New" just as
     * it sits above the header's Login.
     */
    public void openNewUi(String language) {
        openIn(language);
        dismissWelcomeBanners(page, false);
        chooseNewUiIfAsked();
        dismissOverlays();
        awaitSections();
    }

    /**
     * Answers "Choose your preferred interface" with New Interface when the picker is up; the
     * choice persists in localStorage.
     *
     * <h2>It re-asks on every navigation unless told not to</h2>
     * Measured 2026-10-08: answering the picker once is not enough - it came back after a plain
     * {@code /messages} navigation that had nothing to do with a fresh picker, and again after that.
     * The picker carries its own "Don't ask again today" checkbox, which nothing here was ticking;
     * {@code ui-version} in localStorage decides which interface renders, but whatever gate decides
     * whether to show the asker again looks like it is not that value, since choosing New Interface
     * repeatedly did not stop it being asked again on the very next navigation. Ticking the checkbox
     * before answering is what the box is there for, and the retry loop stays as a second line of
     * defence rather than the only one, bounded the same way the rest of this class bounds a retry
     * against something that will not yield.
     */
    public void chooseNewUiIfAsked() {
        for (int attempt = 1; attempt <= 3 && isUiVersionPickerUp(); attempt++) {
            CaseLog.step("The site asked which UI to use - choosing New Interface" + (attempt > 1 ? " (retry " + attempt + ")." : "."));
            Locator dontAskAgain = page.getByRole(AriaRole.CHECKBOX).filter(new Locator.FilterOptions().setVisible(true));
            if (dontAskAgain.count() > 0 && !dontAskAgain.first().isChecked()) {
                CaseLog.step("Ticking \"Don't ask again today\" so the picker does not come back on the next navigation.");
                dontAskAgain.first().check();
            }
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Choose New").setExact(true)).first().click();
            awaitReady();
            page.waitForTimeout(3_000);
            if (isUiVersionPickerUp()) {
                CaseLog.evidence("The site landed back on the interface picker after choosing New Interface.");
            }
        }
    }

    /** True while the "Choose your preferred interface" picker is up. */
    public boolean isUiVersionPickerUp() {
        Locator picker = page.getByText(UI_VERSION_HEADING);
        return picker.count() > 0 && picker.first().isVisible();
    }

    /** The UI version the site keeps for this visitor ("new", "old"), or empty when none was chosen. */
    public String uiVersion() {
        Object raw = page.evaluate("() => { try { return localStorage.getItem('ui-version') || ''; } catch (e) { return ''; } }");
        return raw == null ? "" : String.valueOf(raw);
    }

    /**
     * Closes what the site lays over the page, so the next click reaches what it is aimed at:
     * <ul>
     *   <li>the <b>welcome banner</b> promo ({@link #WELCOME_BANNER}) - closed first, since its
     *       near-maximum z-index wins the hit test over everything else, including the header
     *       (found 2026-10-08: its absence here, unlike in {@code NewWeibo_automation}, was why
     *       every signed-in case timed out on the header's Login);</li>
     *   <li>the <b>Daily Mission</b> panel a member is met with after signing in - its reward is
     *       claimed first, as Si asked (2026-10-02), then the panel is closed by its ×;</li>
     *   <li>the <b>chat widget</b> (Chatwoot), whose panel opens by itself a few seconds after a
     *       page loads and covers the right of a PC screen and the whole of a phone's;</li>
     *   <li>the <b>"Add to Home Screen"</b> panel the site raises on an iPhone.</li>
     * </ul>
     * Harmless when nothing is up. Each step is recorded in the trail.
     */
    public void dismissOverlays() {
        dismissWelcomeBanners(page, false);
        claimDailyMission();
        closeChatWidget();
        closeAddToHomeScreen();
        closeCoveringPanel();
    }

    /**
     * Any other panel laid over the whole page - a fixed element the size of the window with a ×
     * and nothing else readable. The "Add to Home Screen" panel on an iPhone is one: its words are
     * in an image, so {@link #closeAddToHomeScreen()} could not see it by text, and the Login
     * click behind its backdrop was refused (2026-10-05). The sign-in dialog and the Daily Mission
     * panel are left alone - they have handlers of their own. Closed by its ×, else hidden, and
     * recorded either way.
     */
    public void closeCoveringPanel() {
        if (isLoginDialogOpen() || isDailyMissionUp()) {
            return;
        }
        for (int attempt = 0; attempt < 2 && isCoveringPanelUp(); attempt++) {
            CaseLog.step("A panel covers the whole page - closing it by its ×.");
            closeTopmost("the panel over the page");
        }
        if (isCoveringPanelUp()) {
            evaluateSettled(COVERING_PANEL + ".forEach(e => e.style.display = 'none')", null);
            CaseLog.evidence("The panel over the page offered no close control that worked; it was hidden so the row could go on.");
            page.waitForTimeout(400);
        }
    }

    /** Fixed, window-sized, visible, and not the chat widget nor the site's own page frame. */
    private static final String COVERING_PANEL = "[...document.querySelectorAll('div, section')].filter(e => { const s = getComputedStyle(e); const r = e.getBoundingClientRect();"
            + " return s.position === 'fixed' && r.width >= window.innerWidth - 2 && r.height >= window.innerHeight - 2 && s.display !== 'none' && s.visibility !== 'hidden' && +s.opacity > 0 && s.pointerEvents !== 'none' && !/hide-modal/.test(String(e.className))"
            + " && !e.closest('#cw-widget-holder, #cw-bubble-holder') && (s.backgroundColor !== 'rgba(0, 0, 0, 0)' || s.backdropFilter !== 'none' || /bg-black|backdrop/.test(String(e.className))); })";

    public boolean isCoveringPanelUp() {
        Object raw = evaluateSettled("() => " + COVERING_PANEL + ".length > 0", null);
        return Boolean.TRUE.equals(raw);
    }

    /**
     * The Daily Mission panel: "Complete N days of 5 days", a Claim reward button and an ×. Claims
     * when the button is offered, then closes. Hides the panel as a last resort, saying so.
     */
    public void claimDailyMission() {
        if (!isDailyMissionUp()) {
            return;
        }
        Locator claim = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("claim", Pattern.CASE_INSENSITIVE)))
                .filter(new Locator.FilterOptions().setVisible(true));
        if (claim.count() > 0 && claim.first().isEnabled()) {
            CaseLog.step("The Daily Mission panel is up - claiming the reward.");
            claim.first().click(new Locator.ClickOptions().setForce(true));
            page.waitForTimeout(2_000);
            String said = promptText();
            if (!said.isEmpty() && !said.toLowerCase().contains("daily mission")) {
                CaseLog.data("Daily Mission claim", said);
                closeTopmost("the claim's message");
            }
        } else {
            CaseLog.step("The Daily Mission panel is up with nothing to claim.");
        }
        for (int attempt = 0; attempt < 2 && isDailyMissionUp(); attempt++) {
            closeTopmost("the Daily Mission panel");
        }
        if (isDailyMissionUp()) {
            hidePanel("days of 5 days");
            CaseLog.evidence("The Daily Mission panel offered no close control that worked; it was hidden so the row could go on.");
        }
    }

    /**
     * True while the Daily Mission panel is up. Its "DAILY MISSION" title is an image, so the panel
     * is known by the words it does render: "Complete N days of 5 days" and its Claim reward button
     * (measured 2026-10-05 - looking for "daily mission" in the text never saw it, and the panel
     * stayed over every member row).
     */
    public boolean isDailyMissionUp() {
        return isUp("daily mission") || isUp("days of 5 days") || isUp("claim reward");
    }

    /**
     * Runs a script on the page, once more if the New UI reloads underneath it. Choosing the New UI
     * and signing in each reload the page twice a couple of seconds apart, and a read taken in the
     * gap dies with "Execution context was destroyed" - which errored {@code NOT-CLI-022} on
     * 2026-10-05 before the member had even signed in.
     */
    protected Object evaluateSettled(String script, Object arg) {
        try {
            return page.evaluate(script, arg);
        } catch (PlaywrightException destroyed) {
            if (destroyed.getMessage() == null || !destroyed.getMessage().contains("Execution context was destroyed")) {
                throw destroyed;
            }
            awaitReady();
            page.waitForTimeout(800);
            return page.evaluate(script, arg);
        }
    }

    /**
     * Clicks a control the site's overlays keep covering. The chat widget's panel opens by itself
     * one to two seconds after every page load - over the whole screen on a phone, over the sign-in
     * dialog on an iPad - and a click that lands on its iframe is refused as "intercepts pointer
     * events" until it times out. The widget is closed right before the click, and once more if
     * the click is still intercepted; a third try forces the click.
     */
    protected void clickPast(Locator control, String what) {
        for (int attempt = 1; ; attempt++) {
            closeChatWidget();
            try {
                control.click(new Locator.ClickOptions().setTimeout(attempt < 3 ? 4_000 : 8_000).setForce(attempt >= 3));
                return;
            } catch (PlaywrightException covered) {
                String why = covered.getMessage() == null ? "" : covered.getMessage();
                if (attempt >= 3 || !(why.contains("intercepts pointer events") || why.contains("Timeout"))) {
                    throw covered;
                }
                CaseLog.step("Something covered " + what + " (" + (why.contains("intercepts") ? "an overlay intercepted the click" : "it was not clickable in time") + ") - clearing the overlays and trying again.");
                dismissOverlays();
            }
        }
    }

    /** The Chatwoot panel, closed through the widget's own SDK; hidden if the SDK will not. */
    public void closeChatWidget() {
        Object state = evaluateSettled("""
                () => {
                  const holder = document.querySelector('#cw-widget-holder');
                  if (!holder || holder.classList.contains('woot--hide')) return 'closed';
                  try { if (window.$chatwoot && window.$chatwoot.toggle) { window.$chatwoot.toggle('close'); return 'toggled'; } } catch (e) {}
                  holder.classList.add('woot--hide'); holder.style.display = 'none'; return 'hidden';
                }""", null);
        if (!"closed".equals(state)) {
            CaseLog.step("The chat widget's panel had opened over the page - " + ("toggled".equals(state) ? "closed it." : "hid it."));
            page.waitForTimeout(800);
        }
    }

    /** The "Add to Home Screen" panel on an iPhone: its × control, else Escape. */
    private void closeAddToHomeScreen() {
        if (!isUp("add to home screen")) {
            return;
        }
        CaseLog.step("Closing the site's \"Add to Home Screen\" panel.");
        closeTopmost("the \"Add to Home Screen\" panel");
        if (isUp("add to home screen")) {
            hidePanel("add to home screen");
        }
    }

    /** True when a visible element carries this text (case-insensitive). */
    private boolean isUp(String text) {
        Object raw = evaluateSettled("t => [...document.querySelectorAll('div, section, h1, h2, h3, span, p')]"
                + ".some(e => e.offsetParent !== null && e.children.length < 40 && (e.innerText || '').toLowerCase().includes(t))", text);
        return Boolean.TRUE.equals(raw);
    }

    /**
     * Clicks the close control of whatever panel is on top: an element whose text is ×/✕, or an
     * aria-label or class naming close, nearest the top-right of the smallest visible panel that
     * has one; Escape when none is found.
     */
    private void closeTopmost(String what) {
        Object done = evaluateSettled("""
                () => {
                  const isClose = e => {
                    const t = (e.innerText || e.textContent || '').trim();
                    const cls = e.className && e.className.baseVal !== undefined ? e.className.baseVal : String(e.className || '');
                    return /^[×✕✖x]$/i.test(t) || /close/i.test(e.getAttribute('aria-label') || '') || /close/i.test(cls) || /close/i.test(e.getAttribute('alt') || '');
                  };
                  const closes = [...document.querySelectorAll('button, span, div, svg, img, a')].filter(e => e.offsetParent !== null || e.tagName === 'svg').filter(isClose)
                    .filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && r.width < 80 && r.height < 80; })
                    .filter(e => !e.closest('#cw-widget-holder, #cw-bubble-holder, header, nav'));
                  if (!closes.length) return 'none';
                  // The topmost: highest stacking - approximated by the last in document order inside a fixed/absolute ancestor.
                  const pick = closes.reverse().find(e => { let n = e; while (n && n !== document.body) { const p = getComputedStyle(n).position; if (p === 'fixed' || p === 'absolute') return true; n = n.parentElement; } return false; }) || closes[0];
                  pick.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true}));
                  return 'clicked ' + pick.tagName.toLowerCase();
                }""", null);
        if ("none".equals(done)) {
            CaseLog.step("No close control found for " + what + " - pressing Escape.");
            page.keyboard().press("Escape");
        }
        page.waitForTimeout(900);
    }

    /** Hides the smallest visible panel carrying this text - the last resort, used only after closing failed. */
    private void hidePanel(String text) {
        evaluateSettled("t => { const all = [...document.querySelectorAll('div, section')].filter(e => e.offsetParent !== null && (e.innerText || '').toLowerCase().includes(t));"
                + " if (!all.length) return; const panel = all.sort((a, b) => a.innerText.length - b.innerText.length)[0];"
                + " let n = panel; while (n && n !== document.body) { const p = getComputedStyle(n).position; if (p === 'fixed') break; n = n.parentElement; }"
                + " (n && n !== document.body ? n : panel).style.display = 'none'; }", text);
        page.waitForTimeout(500);
    }

    private void awaitSections() {
        try {
            page.locator(SECTION).first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (PlaywrightException none) {
            CaseLog.evidence("No category section was drawn on the home page within 15s.");
        }
        page.waitForTimeout(1_500);
    }

    /**
     * The New UI's language list: the flag button (desktop) or the globe (phone) opens it, and a
     * row is chosen by its English label. Replaces the old UI's globe-and-title rows.
     */
    @Override
    protected void selectLanguage(Language wanted) {
        CaseLog.step("Choosing " + wanted.label() + " from the client's language list.");
        for (int attempt = 1; attempt <= 2; attempt++) {
            Locator rows = page.locator(LANGUAGE_NAME).filter(new Locator.FilterOptions().setVisible(true));
            if (rows.count() == 0) {
                Locator opener = page.locator(LANGUAGE_BUTTON_DESKTOP).filter(new Locator.FilterOptions().setVisible(true));
                if (opener.count() == 0) {
                    opener = page.locator(LANGUAGE_BUTTON_PHONE).filter(new Locator.FilterOptions().setVisible(true));
                }
                if (opener.count() == 0) {
                    CaseLog.evidence("No language control is visible in the header.");
                    return;
                }
                opener.first().click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(1_200);
            }
            try {
                page.locator(LANGUAGE_NAME).filter(new Locator.FilterOptions().setHasText(wanted.label()))
                        .filter(new Locator.FilterOptions().setVisible(true)).first()
                        .click(new Locator.ClickOptions().setTimeout(10_000));
                page.waitForTimeout(LANGUAGE_SETTLE_MS);
                awaitReady();
                if (wanted.locale().equals(localeFromUrl())) {
                    return;
                }
            } catch (PlaywrightException rowNotClickable) {
                if (attempt == 2) {
                    throw rowNotClickable;
                }
                page.waitForTimeout(1_000);
            }
        }
        CaseLog.evidence("The client stayed on /" + localeFromUrl() + " after " + wanted.label() + " was chosen.");
    }

    // ---------------------------------------------------------------
    // Reading the sections
    // ---------------------------------------------------------------

    /** Every category panel on the home page, top to bottom, with its cards as drawn right now. */
    @SuppressWarnings("unchecked")
    public List<Section> sections() {
        Object raw = page.evaluate("""
                () => [...document.querySelectorAll('div.rounded-lg')].filter(s => s.querySelector('button.snap-start') || s.querySelector('span')).map(sec => {
                  const name = sec.querySelector('span');
                  const icon = sec.querySelector('img.w-5');
                  const btns = [...sec.querySelectorAll('button')];
                  const more = btns.some(b => !b.classList.contains('snap-start') && !b.getAttribute('aria-label') && b.innerText.trim());
                  const arrow = l => btns.some(b => (b.getAttribute('aria-label') || '') === l && b.offsetParent !== null);
                  const cards = [...sec.querySelectorAll('button.snap-start')];
                  const row = cards.length ? cards[0].parentElement : null;
                  const rr = row ? row.getBoundingClientRect() : { left: 0, right: window.innerWidth };
                  return {
                    name: name ? name.innerText.trim() : '',
                    iconSrc: icon ? (icon.getAttribute('src') || '') : '',
                    iconLoaded: !!(icon && icon.complete && icon.naturalWidth > 0),
                    moreGames: more,
                    leftArrow: arrow('Scroll left'), rightArrow: arrow('Scroll right'),
                    rowScrollWidth: row ? row.scrollWidth : 0, rowClientWidth: row ? row.clientWidth : 0,
                    cards: cards.map(c => { const i = c.querySelector('img'); const r = c.getBoundingClientRect();
                      return { alt: i ? i.alt : '', src: i ? (i.getAttribute('src') || '') : '',
                               loaded: !!(i && i.complete && i.naturalWidth > 0),
                               width: Math.round(r.width), height: Math.round(r.height), left: Math.round(r.left),
                               inView: r.left >= rr.left - 1 && r.right <= rr.right + 1 }; })
                  };
                })""");
        List<Section> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> s = (Map<String, Object>) o;
                List<Card> cards = new ArrayList<>();
                for (Object c : (List<Object>) s.get("cards")) {
                    cards.add(card((Map<String, Object>) c));
                }
                out.add(new Section(str(s.get("name")), str(s.get("iconSrc")), bool(s.get("iconLoaded")), bool(s.get("moreGames")),
                        bool(s.get("leftArrow")), bool(s.get("rightArrow")), num(s.get("rowScrollWidth")), num(s.get("rowClientWidth")), cards));
            }
        }
        return out;
    }

    /** The panel headed with this name, or null. */
    public Section section(String name) {
        return sections().stream().filter(s -> s.name().equalsIgnoreCase(name.trim())).findFirst().orElse(null);
    }

    private Locator sectionLocator(String name) {
        return page.locator(SECTION).filter(new Locator.FilterOptions()
                .setHas(page.locator(SECTION_NAME).filter(new Locator.FilterOptions().setHasText(exactly(name, true))))).first();
    }

    /** Clicks "More Games" on a panel (in whatever language it is labelled) and waits for the category page. */
    public void clickMoreGames(String name) {
        CaseLog.step("Clicking More Games on \"" + name + "\".");
        dismissOverlays();   // the site reloads itself now and then on WebKit, and the iPhone panel comes back with it
        Locator more = sectionLocator(name).locator(MORE_GAMES).first();
        clickOrForce(more, "More Games on \"" + name + "\"");
        try {
            page.waitForURL(Pattern.compile(".*" + Pattern.quote(CATEGORY_ROUTE) + "\\d+.*"), new Page.WaitForURLOptions().setTimeout(15_000));
        } catch (PlaywrightException stayed) {
            CaseLog.evidence("More Games did not open a category page within 15s; still on " + page.url());
        }
        awaitReady();
        dismissOverlays();
        page.waitForTimeout(1_500);
    }

    /** Presses a panel's scroll arrow once; false when it is not on screen. */
    public boolean pressArrow(String name, boolean right) {
        Locator arrow = sectionLocator(name).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(right ? SCROLL_RIGHT : SCROLL_LEFT).setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true));
        if (arrow.count() == 0) {
            return false;
        }
        CaseLog.step("Pressing the " + (right ? "right" : "left") + " arrow on \"" + name + "\".");
        dismissOverlays();
        clickOrForce(arrow.first(), (right ? "right" : "left") + " arrow on \"" + name + "\"");
        page.waitForTimeout(900);
        return true;
    }

    /**
     * A plain click, and when something the site lays over the control (the chat widget, a
     * banner) keeps Playwright from delivering it within 5s, a forced click - recorded, since a
     * control a real pointer cannot reach is worth knowing about.
     */
    private void clickOrForce(Locator control, String what) {
        try {
            control.click(new Locator.ClickOptions().setTimeout(5_000));
        } catch (PlaywrightException notReached) {
            String why = notReached.getMessage() == null ? "" : notReached.getMessage().split("\\R", 2)[0];
            CaseLog.evidence("A plain click on " + what + " was not delivered within 5s (" + why + "); clicking it by force.");
            control.click(new Locator.ClickOptions().setForce(true).setTimeout(5_000));
        }
    }

    /** Scrolls a panel's card row sideways by its own width, the way a finger would; returns the new scrollLeft. */
    public int swipeRow(String name) {
        CaseLog.step("Scrolling the cards of \"" + name + "\" sideways.");
        Object left = sectionLocator(name).locator(CARD).first().evaluate(
                "c => { const row = c.parentElement; row.scrollBy({left: row.clientWidth, behavior: 'instant'}); return row.scrollLeft; }");
        page.waitForTimeout(600);
        return left instanceof Number n ? n.intValue() : 0;
    }

    /** Clicks a panel's n-th card (0-based). */
    public void clickCard(String name, int index) {
        CaseLog.step("Clicking card " + (index + 1) + " of \"" + name + "\".");
        dismissOverlays();
        clickOrForce(sectionLocator(name).locator(CARD).nth(index), "card " + (index + 1) + " of \"" + name + "\"");
        page.waitForTimeout(2_500);
    }

    /** Clicks the n-th card (0-based) of the open category page. */
    public void clickCategoryPageCard(int index) {
        CaseLog.step("Clicking card " + (index + 1) + " on the category page.");
        dismissOverlays();
        clickOrForce(page.locator(SECTION + " button").filter(new Locator.FilterOptions().setHas(page.locator("img"))).nth(index),
                "card " + (index + 1) + " on the category page");
        page.waitForTimeout(2_500);
    }

    // ---------------------------------------------------------------
    // Launching a game
    // ---------------------------------------------------------------

    /** What opening a game did. The first three are a launch; the rest are the site declining. */
    public enum LaunchOutcome { NEW_WINDOW, NAVIGATED, EMBEDDED, MAINTENANCE, LOGIN_ASKED, BLOCKED, NOTHING;
        public boolean launched() {
            return this == NEW_WINDOW || this == NAVIGATED || this == EMBEDDED;
        }
    }

    /** The outcome of opening a game, and where it went or what the site said. */
    public record Launch(LaunchOutcome outcome, String detail) {
        @Override
        public String toString() {
            return outcome + (detail.isEmpty() ? "" : " - " + detail);
        }
    }

    private static final Pattern MAINTENANCE = Pattern.compile("maintenance|維護|维护", Pattern.CASE_INSENSITIVE);
    private static final long LAUNCH_WAIT_MS = 10_000;

    /** Opens a game from a home-page section's card, by the game's name (the card's alt). */
    public Launch launchFromSection(String section, String game) {
        Locator card = sectionLocator(section).locator(CARD).filter(new Locator.FilterOptions()
                .setHas(page.locator("img[alt='" + game.replace("'", "\\'") + "']"))).first();
        if (card.count() == 0) {
            return new Launch(LaunchOutcome.NOTHING, "no card for " + game + " in \"" + section + "\"");
        }
        CaseLog.step("Opening " + game + " from \"" + section + "\".");
        return launch(card);
    }

    /** Opens a game from the open category page, by the image the card draws (the page names no game). */
    public Launch launchFromCategoryPage(String src, String game) {
        Locator card = page.locator(SECTION + " button").filter(new Locator.FilterOptions()
                .setHas(page.locator("img[src='" + src + "']"))).first();
        if (card.count() == 0) {
            return new Launch(LaunchOutcome.NOTHING, "no card drawing " + src + " for " + game);
        }
        CaseLog.step("Opening " + game + " from the category page.");
        return launch(card);
    }

    /**
     * Clicks a game card and waits up to ten seconds for what the site does: a new window, a
     * navigation away, an embedded game frame, or a prompt. Leaves any new window open and the
     * page wherever it went - the caller decides what to put back, since it knows where it was.
     */
    private Launch launch(Locator card) {
        dismissOverlays();
        String before = page.url();
        int pagesBefore = page.context().pages().size();
        clickOrForce(card, "the game card");
        long deadline = System.currentTimeMillis() + LAUNCH_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            page.waitForTimeout(500);
            List<Page> pages = page.context().pages();
            if (pages.size() > pagesBefore) {
                Page opened = pages.get(pages.size() - 1);
                try {
                    opened.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                            new Page.WaitForLoadStateOptions().setTimeout(8_000));
                } catch (PlaywrightException stillLoading) {
                    // The address is what matters; the game's own assets may take longer.
                }
                return new Launch(LaunchOutcome.NEW_WINDOW, opened.url());
            }
            if (!page.url().equals(before)) {
                return new Launch(LaunchOutcome.NAVIGATED, page.url());
            }
            String ask = asksToLogIn();
            if (!ask.isEmpty()) {
                return new Launch(LaunchOutcome.LOGIN_ASKED, ask);
            }
            String said = promptText();
            if (!said.isEmpty()) {
                return new Launch(MAINTENANCE.matcher(said).find() ? LaunchOutcome.MAINTENANCE : LaunchOutcome.BLOCKED, said);
            }
            Locator frame = page.locator("iframe[src]:not([src='about:blank'])").filter(new Locator.FilterOptions().setVisible(true));
            if (frame.count() > 0 && isGameFrame(frame.first().getAttribute("src"))) {
                return new Launch(LaunchOutcome.EMBEDDED, String.valueOf(frame.first().getAttribute("src")));
            }
        }
        return new Launch(LaunchOutcome.NOTHING, "still on " + page.url() + " with no prompt after " + (LAUNCH_WAIT_MS / 1000) + "s");
    }

    /** Frames the page always carries - the chat widget, analytics - are not a game. */
    private static boolean isGameFrame(String src) {
        return src != null && !src.isBlank() && !src.contains("exchat") && !src.contains("chatwoot")
                && !src.contains("google") && !src.contains("doubleclick");
    }

    /** The words of a visible prompt, dialog or toast the site raised - empty when none is up. */
    public String promptText() {
        Object raw = evaluateSettled("""
                () => {
                  const boxes = [...document.querySelectorAll('[role=dialog], [role=alertdialog], [class*=modal], [class*=Modal], [class*=toast], [class*=Toast], [class*=swal], [class*=prompt]')]
                    .filter(e => e.offsetParent !== null && e.innerText && e.innerText.trim());
                  if (!boxes.length) return '';
                  const inner = boxes.sort((a, b) => a.innerText.length - b.innerText.length)[0];
                  return inner.innerText.replace(/\\s+/g, ' ').trim().slice(0, 200);
                }""", null);
        String text = raw == null ? "" : String.valueOf(raw);
        // The sign-in dialog is read by asksToLogIn; the game navigation and header are not prompts.
        return text.contains("Username") || text.contains("Verification Code") || text.length() > 180 ? "" : text;
    }

    /** Closes every window but this page's, as a game walk does between games. */
    public void closeOtherWindows() {
        for (Page other : new ArrayList<>(page.context().pages())) {
            if (other != page) {
                try {
                    other.close();
                } catch (PlaywrightException alreadyGone) {
                    // Nothing to close.
                }
            }
        }
    }

    /** Whatever badge, label or overlay a card carries besides its image - empty when it is the image alone. */
    public String cardExtras(String name, String alt) {
        Locator card = sectionLocator(name).locator(CARD).filter(new Locator.FilterOptions()
                .setHas(page.locator("img[alt='" + alt.replace("'", "\\'") + "']"))).first();
        if (card.count() == 0) {
            return "";
        }
        Object raw = card.evaluate("c => { const text = c.innerText.trim(); const extra = [...c.querySelectorAll('*')]"
                + ".filter(e => e.tagName !== 'IMG' && e.children.length === 0 && !e.querySelector('img')).map(e => e.className).join(' ');"
                + " return (text ? 'text \"' + text + '\" ' : '') + (extra ? 'elements ' + extra : ''); }");
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    // ---------------------------------------------------------------
    // Layout checks shared by the device rows
    // ---------------------------------------------------------------

    /** How far the document is wider than the window - 0 when nothing runs off the side. */
    public int horizontalOverflow() {
        Object raw = page.evaluate("() => Math.max(0, document.documentElement.scrollWidth - window.innerWidth)");
        return raw instanceof Number n ? n.intValue() : 0;
    }

    /** The window's inner width, which decides the site's breakpoints. */
    public int viewportWidth() {
        Object raw = page.evaluate("() => window.innerWidth");
        return raw instanceof Number n ? n.intValue() : 0;
    }

    /** True when a panel's header - icon, name and More Games - sits on one line and inside the window. */
    public boolean headerOnOneLine(String name) {
        Object raw = sectionLocator(name).evaluate("sec => { const n = sec.querySelector('span');"
                + " const m = [...sec.querySelectorAll('button')].find(b => !b.classList.contains('snap-start') && !b.getAttribute('aria-label') && b.innerText.trim());"
                + " if (!n || !m) return false; const a = n.getBoundingClientRect(), b = m.getBoundingClientRect();"
                + " const line = parseFloat(getComputedStyle(n).lineHeight) || 24;"
                + " return a.height <= line * 1.5 && Math.abs(a.top - b.top) < line && b.right <= window.innerWidth + 1; }");
        return Boolean.TRUE.equals(raw);
    }

    /** Every card on the open category page: its image, size and left edge, for column counting. */
    @SuppressWarnings("unchecked")
    public List<Card> categoryPageCards() {
        Object raw = page.evaluate("""
                () => [...document.querySelectorAll('div.rounded-lg button')].filter(b => b.querySelector('img')).map(c => {
                  const i = c.querySelector('img'); const r = c.getBoundingClientRect();
                  return { alt: i.alt, src: i.getAttribute('src') || '', loaded: !!(i.complete && i.naturalWidth > 0),
                           width: Math.round(r.width), height: Math.round(r.height), left: Math.round(r.left),
                           inView: r.left >= -1 && r.right <= window.innerWidth + 1 }; })""");
        List<Card> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object c : list) {
                out.add(card((Map<String, Object>) c));
            }
        }
        return out;
    }

    /** How many columns the open category page's grid has - the distinct left edges of its cards. */
    public int categoryPageColumns() {
        return (int) categoryPageCards().stream().map(Card::left).distinct().count();
    }

    // ---------------------------------------------------------------
    // Visitor and member
    // ---------------------------------------------------------------

    /** True while the sign-in dialog (Username / Password / Verification Code) is on screen. */
    public boolean isLoginDialogOpen() {
        return page.getByPlaceholder("Username", new Page.GetByPlaceholderOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true)).count() > 0;
    }

    /**
     * How the site is asking the visitor to sign in, if it is: "Login dialog", or the words of its
     * prompt ("Please login first"), or empty when it is not asking.
     */
    public String asksToLogIn() {
        if (isLoginDialogOpen()) {
            return "Login dialog";
        }
        Locator prompt = page.getByText(PLEASE_LOG_IN).filter(new Locator.FilterOptions().setVisible(true));
        return prompt.count() > 0 ? "prompt \"" + prompt.first().innerText().trim() + "\"" : "";
    }

    /** Closes whichever sign-in ask is up: the prompt's Close button, else Escape. */
    public void dismissLoginAsk() {
        Locator close = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^\\s*close\\s*$", Pattern.CASE_INSENSITIVE)))
                .filter(new Locator.FilterOptions().setVisible(true));
        if (close.count() > 0 && !isLoginDialogOpen()) {
            close.first().click();
        } else {
            page.keyboard().press("Escape");
        }
        page.waitForTimeout(600);
    }

    /** True while the header still offers Login - the visitor is not signed in. */
    public boolean isVisitor() {
        return page.getByText(LOG_IN).filter(new Locator.FilterOptions().setVisible(true)).count() > 0;
    }

    /**
     * Signs a member in through the New UI header's Login dialog; false, with the site's words
     * logged, when it refuses. The test site accepts {@code 1111} as the verification code.
     */
    public boolean signIn(String username, String password, String captcha) {
        CaseLog.step("Signing in to the client platform as " + username + ".");
        if (!isLoginDialogOpen()) {
            dismissOverlays();
            clickPast(page.getByText(LOG_IN).filter(new Locator.FilterOptions().setVisible(true)).first(), "the header's Login");
            page.waitForTimeout(1_200);
        }
        Locator username_ = field("Username");
        username_.fill(username);
        field("Password").fill(password);
        field("Verification Code").fill(captcha);
        // The dialog's own LOG IN, not the header's Login: the nearest ancestor of the Username field that holds one.
        Locator dialog = username_.locator("xpath=ancestor::*[.//button[contains(translate(normalize-space(string(.)), 'LOGIN ', 'login'), 'login')]][1]");
        Locator submit = dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(LOG_IN))
                .filter(new Locator.FilterOptions().setVisible(true));
        if (submit.count() == 0) {
            submit = page.locator("button[type='submit'], button[class*='submit']").filter(new Locator.FilterOptions().setVisible(true));
        }
        // The chat widget opens over the dialog while the fields are being filled (iPad, 2026-10-05).
        clickPast(submit.first(), "the dialog's LOG IN");
        page.waitForTimeout(4_000);
        // A member is met with the Daily Mission panel: claim and close it before anything else (Si, 2026-10-02).
        dismissOverlays();
        boolean signedIn = !isLoginDialogOpen() && !isVisitor();
        CaseLog.data("Member signed in", signedIn + (signedIn ? "" : " - the site said: " + visibleAlertText()));
        return signedIn;
    }

    /** Signs the member out through the header's Log out control; true when the header offers Login again. */
    public boolean signOut() {
        CaseLog.step("Signing out of the client platform.");
        dismissOverlays();
        Locator logout = page.getByText(Pattern.compile("^\\s*log\\s*out\\s*$", Pattern.CASE_INSENSITIVE))
                .filter(new Locator.FilterOptions().setVisible(true));
        if (logout.count() == 0) {
            CaseLog.evidence("No Log out control is visible in the header.");
            return false;
        }
        logout.first().click();
        page.waitForTimeout(3_000);
        String said = promptText();
        if (!said.isEmpty()) {
            // A confirmation ("Are you sure?") or a farewell: take the affirmative, else close it.
            Locator yes = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^(ok|yes|confirm|log\\s*out)$", Pattern.CASE_INSENSITIVE)))
                    .filter(new Locator.FilterOptions().setVisible(true));
            if (yes.count() > 0) {
                yes.first().click();
                page.waitForTimeout(2_500);
            } else {
                dismissLoginAsk();
            }
        }
        awaitReady();
        page.waitForTimeout(1_000);
        boolean out = isVisitor();
        CaseLog.data("Member signed out", String.valueOf(out));
        return out;
    }

    private Locator field(String placeholder) {
        Locator input = page.getByPlaceholder(placeholder, new Page.GetByPlaceholderOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true)).first();
        input.waitFor(new Locator.WaitForOptions().setTimeout(8_000));
        return input;
    }

    /** Opens an address under the client's origin and waits as the home page does. */
    public void openPath(String path) {
        page.navigate(clientUrl() + "/" + path.replaceFirst("^/", ""),
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        awaitReady();
        chooseNewUiIfAsked();
        dismissOverlays();
        page.waitForTimeout(2_000);
    }

    private static Card card(Map<String, Object> m) {
        return new Card(str(m.get("alt")), str(m.get("src")), bool(m.get("loaded")), num(m.get("width")),
                num(m.get("height")), num(m.get("left")), bool(m.get("inView")));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static boolean bool(Object o) {
        return Boolean.TRUE.equals(o);
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
