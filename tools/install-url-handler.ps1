<#
    Registers the awb:// scheme on this machine, so a link in the test case sheet can run a case.

    Written under HKCU, so it needs no administrator rights and applies to the signed-in user
    only. Run it once per machine that should be able to run cases from the sheet:

        powershell -ExecutionPolicy Bypass -File tools\install-url-handler.ps1

    Re-running it is safe; it overwrites the same keys, which is also how you point the scheme at
    the repository after moving it. Pass -Uninstall to remove the scheme again.

    Note what this does and does not give you. The link runs the case on the machine that clicked
    it, using that machine's checkout - it is a shortcut for the person holding the sheet, not a
    build server. On a machine without this handler the link simply does nothing.
#>
param([switch]$Uninstall)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$key = 'HKCU:\Software\Classes\awb'

if ($Uninstall) {
    if (Test-Path $key) {
        Remove-Item $key -Recurse -Force
        Write-Host 'awb:// removed.' -ForegroundColor Green
    } else {
        Write-Host 'awb:// was not registered.' -ForegroundColor DarkGray
    }
    return
}

$runner = Join-Path $PSScriptRoot 'run-case.ps1'
if (-not (Test-Path $runner)) { throw "Runner not found: $runner" }

$command = '"{0}" -NoProfile -ExecutionPolicy Bypass -File "{1}" "%1"' -f `
    (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'), $runner

New-Item -Path $key -Force | Out-Null
New-ItemProperty -Path $key -Name '(Default)' -Value 'URL:Admin weibo test case' -PropertyType String -Force | Out-Null
New-ItemProperty -Path $key -Name 'URL Protocol' -Value '' -PropertyType String -Force | Out-Null
New-Item -Path "$key\shell\open\command" -Force | Out-Null
New-ItemProperty -Path "$key\shell\open\command" -Name '(Default)' -Value $command -PropertyType String -Force | Out-Null

Write-Host ''
Write-Host '  awb:// registered for this user.' -ForegroundColor Green
Write-Host "  repository: $repo" -ForegroundColor DarkGray
Write-Host ''
Write-Host '  Try it:  start awb://LGN-002' -ForegroundColor Cyan
Write-Host '  In the sheet:  =HYPERLINK("awb://LGN-002", "Run")' -ForegroundColor Cyan
Write-Host ''
