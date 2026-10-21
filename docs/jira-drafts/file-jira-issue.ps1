# Files one reviewed draft as a Bug in Jira project GD, with attachments. The description is a file of Jira
# wiki markup. Reads the Personal Access Token from -TokenFile (sent as Bearer). GD's Bug screen has no
# Priority field (sending one is a 400); Reporter is no longer on it either (2026-10-05), so only the token is checked.
#   powershell -ExecutionPolicy Bypass -File file-jira-issue.ps1 -Summary "..." -DescriptionFile x.jira.txt `
#     -Labels a,b -Attachments p1,p2 -TokenFile C:/path/token.txt
param(
    [Parameter(Mandatory)] [string]$Summary,
    [Parameter(Mandatory)] [string]$DescriptionFile,
    [string[]]$Labels = @(),
    [string[]]$Attachments = @(),
    [string]$TokenFile = "$env:USERPROFILE\.jira-token",
    [string]$Project = 'GD'
)
$ErrorActionPreference = 'Stop'
$jira = 'https://jira.initcapp.com'
$token = (Get-Content -Raw $TokenFile).Trim()
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/json' }
$me = Invoke-RestMethod -Uri "$jira/rest/api/2/myself" -Headers $headers

$fields = @{
    project     = @{ key = $Project }
    issuetype   = @{ name = 'Bug' }
    summary     = $Summary
    # A plain read: Get-Content -Raw adds PS properties that ConvertTo-Json sends as an object, which Jira refuses.
    description = [System.IO.File]::ReadAllText((Resolve-Path $DescriptionFile), [System.Text.Encoding]::UTF8)
    labels      = $Labels
}
$bytes = [System.Text.Encoding]::UTF8.GetBytes((@{ fields = $fields } | ConvertTo-Json -Depth 6))
try {
    $issue = Invoke-RestMethod -Method Post -Uri "$jira/rest/api/2/issue" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes
} catch {
    $resp = $_.Exception.Response
    if ($resp) { $reader = New-Object System.IO.StreamReader($resp.GetResponseStream()); Write-Host "Jira refused the issue: $($reader.ReadToEnd())" }
    throw
}
Write-Host "Created $($issue.key): $jira/browse/$($issue.key)"

foreach ($path in $Attachments) {
    $out = & curl.exe -s -o NUL -w '%{http_code}' -X POST "$jira/rest/api/2/issue/$($issue.key)/attachments" `
        -H "Authorization: Bearer $token" -H 'X-Atlassian-Token: no-check' -F "file=@$path"
    Write-Host "Attached $(Split-Path -Leaf $path) -> HTTP $out"
}
