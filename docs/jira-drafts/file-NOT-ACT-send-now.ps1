# Files the reviewed draft NOT-ACT-send-now-permission-denied.md as one Bug in Jira project GD and
# attaches the three captures. Reads the token from %USERPROFILE%\.jira-token (a Jira Personal Access
# Token, sent as Bearer). Run it yourself:  powershell -ExecutionPolicy Bypass -File <this file>
param([string]$TokenFile = "$env:USERPROFILE\.jira-token")
$ErrorActionPreference = 'Stop'
$jira = 'https://jira.initcapp.com'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$token = (Get-Content -Raw $TokenFile).Trim()
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/json' }

$summary = '[ISSUE/API] Notification send now: fail - API returns "Permission denied" for qa0001 (NOT-ACT-001 / NOT-ACT-002 / NOT-ACT-003)'

$description = @'
h3. Description
In *Tool > Notification*, clicking *Send Now* (the circular-arrow icon) on a notification rule and confirming the prompt is refused with *"Permission denied"*. Nothing is sent: no message reaches the member and no Record History entry is written.

The same operator can create, list and edit notification rules, so only the Send Now action is refused. The API returns HTTP *200* but its body is a refusal, and the page shows it as "Permission denied".

*Environment:* Test - https://admin-test.1083.city/tools/notification/ (currency HKD), operator {{qa0001}}
*Found by:* Admin_weibo automation - NotificationSendNowSuite, run 2026-09-29 11:09 (GMT+7); reproduced by hand 11:24

h3. Steps to reproduce
# Sign in to https://admin-test.1083.city/ as *qa0001*, with currency HKD.
# Open *Tool > Notification*.
# Click *+ Add notification* and create a rule: Type *System*, Schedule *Daily*, Timer *12 AM*, Status *Active*, Agent Group *agents001*, Member Group *Default 00*. Save and Confirm. The page shows "Successfully added".
# In the list, find the new rule, or any existing one. The refusal happens on any rule, e.g. seed rule *#124 "QA SEED NOT 50"*.
# Click *Send Now* (circular arrow) on that row.
# Confirm "Are you sure you want to send this notification now?"

h3. Expected result
||ID||Expected||
|NOT-ACT-001|The circular-arrow icon sends the notification right away to the members in agents001 / Default 00|
|NOT-ACT-002|Clicking Send Now logs a new entry in *Record History*|
|NOT-ACT-003|Send Now leaves the rule's own Schedule (Daily) and Timer (12 AM) unchanged|

h3. Actual result
* The page shows a Prompt message *"Permission denied"* (attachment 02).
* The API returns HTTP 200, but the body is a refusal:
{code}
POST https://admin-test.1083.city/api/account/inbox-notification-rule/124/send-now?currency=HKD
HTTP 200
{"status":-10,"message":"Permission denied","timestamp":1790655872,"data":{}}
{code}
* The same response came back for rule #137 in the automated run. The manual reproduction on #124 shows it doesn't depend on the rule.
* Nothing is sent. Record History gets no new entry, and the member's Message Center (member qa000092) stays empty.

h3. Test case results (run 2026-09-29 11:09)
||ID||Case||Result||Failed at||
|NOT-ACT-001|The circular-arrow icon is a Send button that pushes the notification immediately|Fail|Send Now refused - "Permission denied"|
|NOT-ACT-002|Clicking Send Now logs a fresh Record History entry|Fail|Send Now refused - "Permission denied"|
|NOT-ACT-003|Send Now does not alter the rule's own Schedule or Timer|Fail|Send Now refused - "Permission denied"|

All three stop at the same step, so none of their own checks could be tested. In each run the rule was created and listed correctly first.

h3. Suspected cause / requested action
The operator role for {{qa0001}} doesn't seem to have the permission behind {{inbox-notification-rule/{id}/send-now}}. *Please grant qa0001 the Send Now permission*, or confirm which role is supposed to hold it so the test account can be moved to that role.
If Send Now is _meant_ to be restricted for this role, please confirm, and the test cases will be changed to expect the refusal.

h3. Attachments
* 01-send-now-confirm.png - the Send Now confirmation prompt
* 02-send-now-permission-denied.png - the "Permission denied" message after Confirm
* 03-send-now-api-response.txt - the send-now request and its response

h3. Notes
* The same refusal also blocks cases outside this ticket, which should pass once this is fixed: Client Message Inbox NOT-CLI-001 to 006 and 008 to 010, and probably NOT-EDT-005.
* Related, same kind of problem: GET /api/account/game-category-features/{id}/games also returns "Permission denied" for qa0001 (Product > Other Category > Manage Games). This could be filed separately.
'@

# GD's Bug screen has no Priority field (sending one is a 400) and requires Reporter - the token's owner.
$me = Invoke-RestMethod -Uri "$jira/rest/api/2/myself" -Headers $headers
$fields = @{
    project     = @{ key = 'GD' }
    issuetype   = @{ name = 'Bug' }
    summary     = $summary
    description = $description
    labels      = @('notification', 'send-now', 'permission', 'automation')
    reporter    = @{ name = $me.name }
}

$body = @{ fields = $fields } | ConvertTo-Json -Depth 6
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
try {
    $issue = Invoke-RestMethod -Method Post -Uri "$jira/rest/api/2/issue" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes
} catch {
    $resp = $_.Exception.Response
    if ($resp) { $reader = New-Object System.IO.StreamReader($resp.GetResponseStream()); Write-Host "Jira refused the issue: $($reader.ReadToEnd())" }
    throw
}
Write-Host "Created $($issue.key): $jira/browse/$($issue.key)"

foreach ($name in '01-send-now-confirm.png', '02-send-now-permission-denied.png', '03-send-now-api-response.txt') {
    $path = Join-Path $here "attachments\$name"
    $out = & curl.exe -s -o NUL -w '%{http_code}' -X POST "$jira/rest/api/2/issue/$($issue.key)/attachments" `
        -H "Authorization: Bearer $token" -H 'X-Atlassian-Token: no-check' -F "file=@$path"
    Write-Host "Attached $name -> HTTP $out"
}
