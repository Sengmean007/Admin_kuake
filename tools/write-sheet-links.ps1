<#
    Prints the =HYPERLINK formulas to paste into the RUN column of the client test case sheet.

        powershell -ExecutionPolicy Bypass -File tools\write-sheet-links.ps1 `
            -Cases LOGIN-001,LOGIN-002,LOGIN-003 -Suite AuthSuite

    The formulas carry the current token, so run this again after rotating it (deleting
    %LOCALAPPDATA%\Admin_weibo\run-token) and paste the fresh column over the old one.

    Why the sheet cannot just hold awb:// links: Google Sheets only turns a handful of schemes
    into clickable links, and a custom one is not among them - the cell renders as plain text.
    An http://127.0.0.1 URL links normally and resolves on the machine of whoever clicks it,
    which is the machine that should run the case.

    -Suite is optional and only narrows which classes Surefire scans, so the run starts sooner.
    Cases with no automated counterpart should simply be left out; a link to an id that no
    display name carries runs nothing and reports "Tests run: 0", which reads like a pass.
#>
param(
    [Parameter(Mandatory = $true)][string[]]$Cases,
    [string]$Suite,
    [string]$Label = 'Run',
    [int]$Port = 8766,
    [switch]$Together
)

# Launched with -File, PowerShell hands every argument over as a plain string, so
# -Cases A,B,C arrives as one element rather than three. Splitting here means the same
# command line behaves the same whether this is dot-sourced or run as a file.
$Cases = $Cases -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ }

# One link per case is what the sheet wants - a RUN cell beside each row. -Together instead
# prints a single link that runs the whole set, for a "run every automated case" cell.
if ($Together) { $Cases = @($Cases -join ',') }

$tokenFile = Join-Path $env:LOCALAPPDATA 'Admin_weibo\run-token'
if (-not (Test-Path $tokenFile)) {
    throw "No token yet. Start tools\run-server.ps1 once; it generates $tokenFile."
}
$token = (Get-Content $tokenFile -Raw).Trim()

foreach ($case in $Cases) {
    $url = "http://127.0.0.1:$Port/run?token=$token&case=$case"
    if ($Suite) { $url += "&suite=$Suite" }
    Write-Output ('=HYPERLINK("{0}","{1}")' -f $url, $Label)
}
