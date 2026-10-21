<#
    A small local bridge so a cell in the client test case sheet can run a case.

    Google Sheets will not make a clickable link out of a custom scheme - a
    =HYPERLINK("awb://LOGIN-001", ...) renders as plain text, not a link - so the sheet cannot
    reach the awb:// handler directly. It will happily link an http:// URL, and http://127.0.0.1
    resolves on the machine of whoever clicks it. This listens there and starts the same
    run-case.ps1 the awb:// handler uses, in its own console window so the run is visible.

        powershell -ExecutionPolicy Bypass -File tools\run-server.ps1

    Leave it running while you work from the sheet. It serves one route:

        /run?token=<token>&case=LOGIN-001[&suite=AuthSuite]

    Why the token:

    Any web page you happen to have open can issue a GET to 127.0.0.1 - through an image tag or a
    window.open - and the browser will send it without asking. Binding to the loopback address
    stops other machines, not other tabs. The token is the part a random page cannot guess. It is
    generated once into %LOCALAPPDATA%\Admin_weibo\run-token, outside the repository so it is
    never committed, and stays put across restarts so the links in the sheet keep working. Delete
    that file to rotate it, then re-run tools\write-sheet-links.ps1 to print fresh formulas.

    The token is a nuisance guard, not the only one: the case id is validated against a strict
    pattern here and again in run-case.ps1, and nothing from the URL is ever handed to a shell.
#>
param([int]$Port = 8766)

$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'run-case.ps1'

$tokenDir = Join-Path $env:LOCALAPPDATA 'Admin_weibo'
$tokenFile = Join-Path $tokenDir 'run-token'
if (-not (Test-Path $tokenFile)) {
    New-Item -ItemType Directory -Path $tokenDir -Force | Out-Null
    $bytes = New-Object byte[] 16
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    (($bytes | ForEach-Object { $_.ToString('x2') }) -join '') | Set-Content -Path $tokenFile -Encoding ascii
}
$token = (Get-Content $tokenFile -Raw).Trim()

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()

Write-Host ''
Write-Host "  Admin weibo case runner listening on http://127.0.0.1:$Port" -ForegroundColor Green
Write-Host "  token: $token" -ForegroundColor DarkGray
Write-Host '  Ctrl+C to stop.' -ForegroundColor DarkGray
Write-Host ''

function Send-Html($context, [int]$status, [string]$title, [string]$body, [string]$colour) {
    $html = @"
<!doctype html><meta charset="utf-8"><title>$title</title>
<style>
 body{font:15px/1.6 system-ui,sans-serif;margin:0;display:grid;place-items:center;height:100vh;
      background:#12141a;color:#e6e8ee}
 .card{max-width:34rem;padding:2rem 2.25rem;background:#1b1e26;border-radius:12px;
       border-top:3px solid $colour}
 h1{font-size:1.1rem;margin:0 0 .5rem;color:$colour}
 p{margin:.4rem 0;color:#aab0c0}
 code{background:#12141a;padding:.15em .4em;border-radius:4px;color:#e6e8ee}
</style>
<div class="card"><h1>$title</h1>$body</div>
"@
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($html)
    $context.Response.StatusCode = $status
    $context.Response.ContentType = 'text/html; charset=utf-8'
    $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $context.Response.Close()
}

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request
        $query = $request.QueryString

        if ($request.Url.AbsolutePath -ne '/run') {
            Send-Html $context 404 'Not found' '<p>Only <code>/run</code> is served here.</p>' '#e2554f'
            continue
        }
        if ($query['token'] -ne $token) {
            Write-Host "  refused: bad or missing token from $($request.UrlReferrer)" -ForegroundColor Yellow
            Send-Html $context 403 'Refused' '<p>Wrong token. Re-run <code>tools\write-sheet-links.ps1</code> for fresh formulas.</p>' '#e2554f'
            continue
        }

        $ids = $query['case']
        $suite = $query['suite']
        if ($ids -notmatch '^[A-Za-z0-9_,\-]+$') {
            Send-Html $context 400 'Refused' '<p>That is not a case id.</p>' '#e2554f'
            continue
        }
        if ($suite -and $suite -notmatch '^[A-Za-z0-9_*\-]+$') {
            Send-Html $context 400 'Refused' '<p>That is not a suite name.</p>' '#e2554f'
            continue
        }

        $uri = "awb://$ids"
        if ($suite) { $uri += "?suite=$suite" }

        Write-Host "  running $ids" -ForegroundColor Cyan
        Start-Process -FilePath 'powershell.exe' `
            -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runner, $uri)

        Send-Html $context 200 "Running $ids" @"
<p>Started in a new console window - watch it there for the result.</p>
<p>A first run compiles the project, so give it a minute.</p>
<p>The report is rewritten at the end of the run:<br><code>docs\reports\index.html</code></p>
<p style="margin-top:1.2rem;color:#6b7280">You can close this tab.</p>
"@ '#3fb27f'
    }
} finally {
    $listener.Stop()
}
