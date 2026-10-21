# CLOSED - NOT FILED: Si had OC-GAM-018 fixed in the test to match the system (2026-09-29)

| Field | Value |
|---|---|
| **Project** | GD |
| **Issue type** | Bug |
| **Summary** | [ISSUE/UI] Other Category manage games filters: fail - Search + Provider + Selected combination could not be verified (OC-GAM-018) |
| **Test case ID** | OC-GAM-018 |
| **Environment** | Test - https://admin-test.1083.city/product/other-category-games?id=384 (currency HKD), operator qa0001 |
| **Found by** | Admin_weibo automation - OtherCategoryManageGamesSuite, run 2026-09-29 14:41 (GMT+7) |

## Description
The case checks that the Game search, **Provider** filter and **Selection** filter work together, so that every
game shown matches all three. It stopped during setup: after the Provider filter was set to **JILI**, there
was no unselected game left to tick.

## Steps performed
1. Open **Manage games** on "QA OC Popular Games".
2. Set **Provider** to JILI. `GET /api/account/game?paginate=20&page=1&game_business_id=158&currency=HKD` returns 200.
3. Look for an unselected game to tick. There is none.

## Expected result
Provider JILI lists at least one game that can be ticked, and the three filters then narrow the list together.

## Actual result
Provider JILI lists exactly **one game, "JILI", which is already selected** (with a banner). That is correct
data, not a fault: the Provider filter did its job.

## Attachment
- `oc-gam-018-jili-only-game-selected.png` - Provider = JILI, one game, already selected

## Recommendation
Don't file this ticket. The failure comes from the test's setup, which picked a provider with nothing left
to tick. The fix belongs in the test: tick an unselected game first, then filter by that game's own provider.
