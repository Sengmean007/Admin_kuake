# DRAFT - not yet filed in Jira

| Field | Value |
|---|---|
| **Project** | GD |
| **Issue type** | Bug |
| **Summary** | [ISSUE/UI][iPad] Home page: fixed bottom tab bar permanently covers the last content section, content unreachable by scrolling |
| **Environment** | Test - https://test-v2.138hk.vip (New UI), iPad viewport 820x1180, visitor and member qa00002 |
| **Found by** | Manual browser check, 2026-10-08 |
| **Labels** | client, ui, responsive, ipad |

## Description
On the home page, the fixed bottom tab bar (Home / Message / WeLive / Support / Me) permanently
covers the bottom of the page's last content section, and the page cannot be scrolled any further
to reveal it. `document.documentElement.scrollHeight` already equals the maximum scroll position
(`scrollY + innerHeight` = 1522 at this width), so this is not a timing issue - the covered area is
unreachable by any amount of scrolling. Reproduces both as a visitor and as a signed-in member
(qa00002).

## Steps to reproduce
1. Open https://test-v2.138hk.vip/en/ in a browser sized to 820x1180 (iPad).
2. Scroll to the bottom of the home page.
3. Compare the last section ("USDT Deposit Bonus") against the fixed bottom tab bar.

## Expected result
The page reserves bottom padding equal to the fixed tab bar's height, so every section - including
the lowest one's bottom edge - can be scrolled fully clear of the tab bar.

## Actual result
- The lower ~70-90px of the "USDT Deposit Bonus" promo banner (its graphics and the bottom of its
  text) sits underneath the tab bar, with no way to scroll it into view.
- Confirmed via JS: `scrollY` maxes at 493, `innerHeight` 1029, `scrollHeight` 1522 - scrollY + innerHeight
  already equals scrollHeight, so the page is genuinely at the bottom; the tab bar is simply drawn on
  top of the last ~72px of it (tab bar `top: 948.5` to `bottom: 1021` in viewport coordinates).

## Attachment
- `ipad-home-bottom-nav-overlap-full.jpg` - full page at max scroll
- `ipad-home-bottom-nav-overlap-closeup.png` - close-up on the overlap

## Note
The tab bar's own class (`lg:hidden fixed bottom-2 left-1/2 -translate-x-1/2 z-[1000]`) shows it is
meant for narrower, non-desktop widths, so this most likely affects every width below the `lg`
breakpoint (phone included), not iPad alone - not yet checked on phone widths this session.
