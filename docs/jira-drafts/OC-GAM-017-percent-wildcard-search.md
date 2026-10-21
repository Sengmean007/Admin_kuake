# DRAFT - not yet filed in Jira

| Field | Value |
|---|---|
| **Project** | GD |
| **Issue type** | Bug |
| **Summary** | [ISSUE/API] Other Category manage games search: fail - "%" is treated as a wildcard and lists every game (OC-GAM-017) |
| **Test case ID** | OC-GAM-017 |
| **Environment** | Test - https://admin-test.1083.city/product/other-category-games?id=384 (currency HKD), operator qa0001 |
| **Found by** | Admin_weibo automation - OtherCategoryManageGamesSuite, run 2026-09-29 14:41 (GMT+7) |
| **Labels** | other-category, manage-games, search, api, automation |

## Description
In **Manage games**, the Game search treats `%` as "match anything" instead of as a plain character.
Searching `%` lists **every game**, although no game name contains `%`. The page sends the character
correctly encoded (`game_name=%25`), so the server itself interprets it. That usually means the value
goes into a SQL `LIKE` pattern without its wildcard characters escaped, and `_` would likely behave the
same way (matching any single character).

## Steps to reproduce
1. Sign in to https://admin-test.1083.city/ as qa0001, with currency HKD.
2. Open **Product > Other Category** and click **Manage games** on any category.
3. In **Game**, type `%` and press Enter.

## Expected result
The search matches `%` as a literal character. No game name contains it, so the list is empty ("No data").

## Actual result
- The full, unfiltered list is shown (JILI, EVOPLAY game-en, WM Live Casino, Weibo Sport, WinWin City, Red Tiger, ... - 20 on the first page).
- API: `GET /api/account/game?paginate=20&page=1&game_name=%25&currency=HKD` returns **200 with every game**.

## Other characters checked in the same case (all correct)
| Search | Result |
|---|---|
| `'` | Plain result, no error |
| `<script>alert(1)</script>` | No script ran, no error |
| 😀 | Plain result, no error |

## Attachment
- `oc-gam-017-percent-lists-all-games.png` - "%" in the Game box and the unfiltered list

## Suggested fix
Escape `%`, `_` and `\` in `game_name` before building the `LIKE` pattern (or use the ORM's literal-contains helper).
