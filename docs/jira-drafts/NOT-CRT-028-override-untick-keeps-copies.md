# FILED as GD-137 on 2026-09-30 - https://jira.initcapp.com/browse/GD-137

| Field | Value |
|---|---|
| **Project** | GD |
| **Issue type** | Bug |
| **Summary** | [ISSUE/UI] Notification create: unticking "Override above contents to: All languages" keeps the copied text on every language tab, so Save is accepted instead of showing the languages-required validation |
| **Environment** | Test - https://admin-test.1083.city/tools/notification-form (currency HKD), operator qa0001 |
| **Priority** | Medium |

## Description
On **Tool > Notification > Add Notification**, ticking **Override above contents to: All languages** copies the 繁體
Title and Content into the other eleven language tabs. Unticking the same checkbox does not undo that: the copies
stay on every tab, so the form is in the state the operator chose to leave - "do not override" - while still
carrying the override's text. Save then goes through with "Successfully added", where the operator expects the
"Title and Content are required for all languages" validation the form shows when the other languages are blank.

## Steps to reproduce
1. Sign in to the back office as qa0001 (HKD) and open **Tool > Notification**, then **+ Add notification**.
2. On the 繁體 tab fill Title "QA NOT Override Unticked", Content and Link.
3. Tick **Override above contents to: All languages**, then untick it again.
4. Open the English or 简体 tab: the 繁體 Title and Content are still there.
5. Under **Agent Group** and **Member group** choose Optional and move agents001 / Default 00 into Selected.
6. Click **Save Notification** and confirm the prompt.

## Expected result
Unticking Override restores the other language tabs to what they held before it was ticked (blank), and Save is
refused with the languages-required validation message; nothing is added to the Notification list.

## Actual result
- After the untick, English and 简体 still hold the 繁體 Title "QA NOT Override Unticked" and its Content.
- The page says **"Successfully added"** and returns to the list, where the rule is listed as ID 277.
- Because the form's Schedule defaults to Once with no Timer, the rule was also sent immediately on save and set
  itself Inactive (measured 2026-09-30 17:39).

## Impact
The checkbox reads as a mode ("override the other languages" on / off) but acts as a one-shot copy. An operator
who ticks it by mistake and unticks it cannot get the blank tabs back without clearing eleven tabs by hand, and
will not be warned by validation - the rule saves and, with the default Schedule, sends at once with untranslated
text on every language.

## Attachment
- notification-override-untick-keeps-copies-saved.png - the list after Save: "QA NOT Override Unticked" listed as
  ID 277, Once / Immediate, Inactive after its send.

## Automation
- `untickingOverrideLeavesLanguagesRequired` (NOT-CRT-028) in `NotificationTest` asserts the refusal and stays red
  until the untick clears the copies. NOT-I18N-004 (Override copies verbatim) is unaffected.
