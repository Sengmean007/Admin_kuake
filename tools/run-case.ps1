<#
    Runs one or more test cases by client case id, launched from an awb:// link.

    A Google Sheets cell cannot run a Maven command; a browser can only hand a URL to whatever
    program is registered for its scheme. So the sheet holds =HYPERLINK("awb://LGN-002", ...)
    and this script is what Windows starts when that link is clicked. Register it once with
    tools/install-url-handler.ps1.

    URL shapes:
        awb://LGN-002                      one case
        awb://LGN-002,LGN-003              several
        awb://LGN-002?suite=AuthSuite      narrow the scan first, for a faster run

    Any web page can invoke a registered scheme, so the id is matched against a strict pattern
    and never reaches a shell: it is validated here and then passed to mvnw.cmd as a single
    argument. Anything that is not a bare list of case ids is refused.
#>
param([Parameter(Mandatory = $true)][string]$Uri)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot

function Fail($message) {
    Write-Host ''
    Write-Host "  $message" -ForegroundColor Red
    Write-Host ''
    Write-Host '  Press Enter to close.'
    [void][System.Console]::ReadLine()
    exit 1
}

# Windows hands the scheme over with a trailing slash on some shapes; both are accepted.
$match = [regex]::Match($Uri, '^awb://(?<ids>[A-Za-z0-9_,\-]+)/?(?:\?suite=(?<suite>[A-Za-z0-9_*\-]+))?/?$')
if (-not $match.Success) {
    Fail "Not a case link this handler will run: $Uri"
}

$ids = $match.Groups['ids'].Value
$suite = $match.Groups['suite'].Value

$mvnwArgs = @('test', "-Dcase=$ids")
if ($suite) { $mvnwArgs += "-Dsuite=$suite" }

Write-Host ''
Write-Host "  Admin_weibo - running $ids" -ForegroundColor Cyan
if ($suite) { Write-Host "  scanning suite $suite" -ForegroundColor DarkGray }
Write-Host "  $repo" -ForegroundColor DarkGray
Write-Host ''

Push-Location $repo
try {
    & "$repo\mvnw.cmd" @mvnwArgs
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Host ''
if ($code -eq 0) {
    Write-Host '  PASSED' -ForegroundColor Green
} else {
    Write-Host "  FAILED (exit $code)" -ForegroundColor Red
}
Write-Host '  Report: docs\reports\index.html' -ForegroundColor DarkGray
Write-Host ''
Write-Host '  Press Enter to close.'
[void][System.Console]::ReadLine()
exit $code
