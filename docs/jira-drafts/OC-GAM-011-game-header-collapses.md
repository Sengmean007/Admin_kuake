# DRAFT - not yet filed in Jira

| Field | Value |
|---|---|
| **Project** | GD |
| **Issue type** | Bug |
| **Summary** | [ISSUE/UI] Other Category manage games: fail - "Game" column header collapses when a search has no match (OC-GAM-011) |
| **Test case ID** | OC-GAM-011 |
| **Environment** | Test - https://admin-test.1083.city/product/other-category-games?id=384 (currency HKD), operator qa0001 |
| **Found by** | Admin_weibo automation - OtherCategoryManageGamesSuite, run 2026-09-29 14:41 (GMT+7) |
| **Labels** | other-category, manage-games, ui, automation |

## Description
On **Product > Other Category > Manage games**, searching for a game that doesn't exist makes the table
header break. With the body showing only "No data", the **Game** column collapses to zero width. Its
header disappears, and the header bar covers only part of the table's width, while "No data" spans all
of it.

## Steps to reproduce
1. Sign in to https://admin-test.1083.city/ as qa0001, with currency HKD.
2. Open **Product > Other Category** and click **Manage games** on any category (e.g. "QA OC Popular Games").
3. In **Game**, type `sss` and press Enter.

## Expected result
The empty table still shows every column header - **Selected | Game | Provider | Game banner | Action** -
each on one line across the full table width, with "No data" below.

## Actual result
- The header shows **Selected | Provider | Game banner | Action**. The **Game** header is squeezed to nothing (automation measured "Game: cut off").
- The header bar ends partway across, while the "No data" row spans the full width.
- The search request itself is fine: `GET /api/account/game?paginate=20&page=1&game_name=sss&currency=HKD` returns 200 with no games.

## Attachment
- `oc-gam-011-game-header-collapsed.png` - the table after searching "sss"

## Note
OC-GAM-901 (Selected filter + no match) passes, and OC-GAM-023 (1280 and 1024 wide windows, with games
listed) passes. So the collapse happens when the list is empty and the columns lose the content that
sets their widths.
