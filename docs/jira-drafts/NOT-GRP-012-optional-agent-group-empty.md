# FILED as GD-136 on 2026-09-30 - https://jira.initcapp.com/browse/GD-136

| Field | Value |
|---|---|
| **Project** | GD |
| **Issue type** | Bug |
| **Summary** | [ISSUE/API] Notification create accepts an empty Agent Group under "Optional" - the rule is saved with no agent target instead of being refused |
| **Environment** | Test - https://admin-test.1083.city/tools/notification-form (currency HKD), operator qa0001 |
| **Priority** | High |

## Description
On **Tool > Notification > Add Notification**, choosing **Optional** for the Agent Group and then saving without
moving any agent group into the **Selected** column is accepted. The rule is created with no agent target at all.
Under "Optional" the operator has said the notification is for specific agent groups; an empty Selected column
means none were chosen, and Save should refuse it with a validation message the same way it refuses a missing
Title or Content.

## Steps to reproduce
1. Sign in to the back office as qa0001 (HKD) and open **Tool > Notification**, then **+ Add notification**.
2. On the 繁體 tab fill Title "QA NOT Optional No Agent", Content and Link, and tick **Override above contents to: All languages**.
3. Set Type **System**, Schedule **Daily**, Timer **12 AM**, Status **Inactive**.
4. Under **Agent Group** choose **Optional** and leave the Selected column empty (click « if anything is there).
5. Under **Member group** choose **Optional** and move **Default 00** into Selected.
6. Click **Save Notification** and confirm "Confirm to add?".

## Expected result
Save is refused. The form stays open and shows which target is missing (for example "Please select at least one
Agent Group"), and nothing is added to the Notification list.

## Actual result
The page says **"Successfully added"** and returns to the list, where the new rule is listed with an empty Agent
Group. (The same acceptance was measured on 2026-09-28 with both Selected columns empty - NOT-CRT-022.)

## Impact
A rule with no agent target is saved as if it were valid. If it is Active, whether it reaches anyone depends on
how the sender treats an empty agent list, which the operator cannot see from the form. The workbook's AQ-07
answer says at least one Agent Group and one Member Group are mandatory.

## Attachment
- To be taken from the first NotificationTargetGroupsSuite run of NOT-GRP-012: the form before Save (Agent
  Group on Optional, Selected empty) and the list afterwards showing the saved rule.

## Automation
- `optionalAgentGroupWithNothingSelectedIsRefused` in `NotificationTest` asserts the refusal and will
  stay red until the validation is added. NOT-CRT-022 (`savesWithNoTargetsIsRefused`) asserts the refusal for the
  both-empty case too, since Si reversed its 2026-09-28 "expect the acceptance" ruling on 2026-10-01.
