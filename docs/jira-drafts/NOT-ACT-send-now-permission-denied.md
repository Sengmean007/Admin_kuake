# DRAFT - not yet filed in Jira

| Field | Value |
|---|---|
| **Project** | GD (https://jira.initcapp.com/secure/RapidBoard.jspa?projectKey=GD&rapidView=7) |
| **Issue type** | Bug |
| **Summary** | [ISSUE/API] Notification send now: fail - API returns "Permission denied" for qa0001 (NOT-ACT-001 / NOT-ACT-002 / NOT-ACT-003) |
| **Test case IDs** | NOT-ACT-001, NOT-ACT-002, NOT-ACT-003 |
| **Priority** | High (blocks all Send Now cases) |
| **Component** | Admin back office - Tool > Notification |
| **Environment** | Test - https://admin-test.1083.city/tools/notification/ (currency HKD) |
| **Affects account** | Operator `qa0001` |
| **Labels** | notification, send-now, permission, automation |
| **Found by** | Admin_weibo automation - `NotificationSendNowSuite`, run 2026-09-29 11:09 (GMT+7) |
| **Assignee** | _<to fill>_ |

---

## Description

In Tool > Notification, clicking **Send Now** (the circular-arrow icon) on a notification rule and
confirming the prompt is refused with **"Permission denied"**. Nothing is sent: no message reaches the
member and no Record History entry is written.

The same operator can create, list and edit notification rules, so only the Send Now action is
refused. The API returns HTTP **200** but its body is a refusal, and the page shows it as "Permission
denied".

## Steps to reproduce

1. Sign in to https://admin-test.1083.city/ as **qa0001**, with currency HKD.
2. Open **Tool > Notification**.
3. Click **+ Add notification** and create a rule:
   - Title/Content/Link filled in (Override: All languages)
   - Type **System**, Schedule **Daily**, Timer **12 AM**, Status **Active**
   - Agent Group **agents001**, Member Group **Default 00**
   - Save and Confirm. The page shows "Successfully added".
4. In the list, find the new rule, or any existing one. The refusal happens on any rule, e.g. seed rule
   **#124 "QA SEED NOT 50"**.
5. Click **Send Now** (circular arrow) on that row.
6. Confirm "Are you sure you want to send this notification now?"

## Expected result (one per test case)

| ID | Expected |
|---|---|
| NOT-ACT-001 | The circular-arrow icon sends the notification right away to the members in agents001 / Default 00 |
| NOT-ACT-002 | Clicking Send Now logs a new entry in **Record History** |
| NOT-ACT-003 | Send Now leaves the rule's own Schedule (Daily) and Timer (12 AM) unchanged |

## Actual result

- The page shows a Prompt message **"Permission denied"** (see capture 02).
- Network: the API returns HTTP 200, but the body is a refusal:
  ```
  POST https://admin-test.1083.city/api/account/inbox-notification-rule/124/send-now?currency=HKD
  HTTP 200
  {"status":-10,"message":"Permission denied","timestamp":1790655872,"data":{}}
  ```
  The same response came back for rule #137 in the automated run at 11:09. It was reproduced by hand
  at 2026-09-29 11:24 on seed rule #124 "QA SEED NOT 50", which shows it doesn't depend on the rule.
- Nothing is sent. Record History gets no new entry, and the member's Message Center
  (`https://test-v2.1083.city/en/inbox-test/`, member qa000092) stays empty.

## Test case results (run 2026-09-29 11:09)

| ID | Case | Result | Failed at |
|---|---|---|---|
| NOT-ACT-001 | The circular-arrow icon is a Send button that pushes the notification immediately | Fail | Send Now refused - "Permission denied" |
| NOT-ACT-002 | Clicking Send Now logs a fresh Record History entry | Fail | Send Now refused - "Permission denied" |
| NOT-ACT-003 | Send Now does not alter the rule's own Schedule or Timer | Fail | Send Now refused - "Permission denied" |

All three stop at the same step, so none of their own checks - delivery, Record History, Schedule/Timer
kept - could be tested. In each run the rule was created and listed correctly first.

## Suspected cause / requested action

The operator role for `qa0001` doesn't seem to have the permission behind
`inbox-notification-rule/{id}/send-now`. **Please grant `qa0001` the Send Now permission**, or confirm
which role is supposed to hold it so the test account can be moved to that role.

If Send Now is *meant* to be restricted for this role, please confirm. The test cases would then be
changed to expect the refusal.

## Attachments (captures of the failure - in `docs/jira-drafts/attachments/`)

| File | Shows |
|---|---|
| `01-send-now-confirm.png` | After clicking Send Now on #124: "Are you sure you want to send this notification now?" with Confirm / Cancel |
| `02-send-now-permission-denied.png` | After Confirm: the Prompt message **"Permission denied"** |
| `03-send-now-api-response.txt` | The `send-now` request and its `{"status":-10,"message":"Permission denied"}` response |

Also available: the full automation report, `docs/reports/NotificationSendNow-test-report.html`, with each
case's step log. Its own screenshots were taken after the message was closed, so they only show the list.

## Notes

- The same refusal also blocks cases outside this ticket, which should pass once this is fixed:
  Client Message Inbox NOT-CLI-001 to 006 and 008 to 010 (no message is ever delivered), and probably
  NOT-EDT-005.
- Related, same kind of problem: `GET /api/account/game-category-features/{id}/games` also returns
  "Permission denied" for `qa0001` (Product > Other Category > Manage Games). This could be filed
  separately.
- Rule #137 from the automated run was deleted by the suite's own cleanup afterwards. That's why the
  reproduction used seed rule #124.
