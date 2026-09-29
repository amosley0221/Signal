<#
  Registers a Scheduled Task "Signal Agent" that starts the agent at logon, hidden (no console
  window), and restarts it if it exits with an error.

  Run from an ELEVATED PowerShell in the agent folder:
    powershell -ExecutionPolicy Bypass -File .\install-startup.ps1

  Options:
    -ConfigPath C:\path\to\signal-agent.config.json   use a config file outside the agent folder
    -Interactive   run in your desktop session instead (needed if your media lives on mapped
                   network drives / NAS shares that need your credentials). node is then started
                   through a hidden PowerShell window.
#>
param(
  [string]$ConfigPath = "",
  [switch]$Interactive
)

$ErrorActionPreference = "Stop"
$TaskName = "Signal Agent"
$AgentDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Entry = Join-Path $AgentDir "src\index.js"

$Node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $Node) { throw "node.exe not found on PATH. Install Node.js LTS from https://nodejs.org/ first." }
if (-not (Test-Path (Join-Path $AgentDir "node_modules"))) { throw "Run 'npm install' in $AgentDir first." }

$NodeArgs = "`"$Entry`""
if ($ConfigPath -ne "") { $NodeArgs += " --config `"$((Resolve-Path $ConfigPath).Path)`"" }

$User = "$env:USERDOMAIN\$env:USERNAME"
if ($Interactive) {
  # PowerShell stays alive while node runs and passes node's exit code on, so restart-on-failure still works.
  $Cmd = "& `"$Node`" $NodeArgs; exit `$LASTEXITCODE"
  $Action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -Command `"$($Cmd.Replace('"','\"'))`"" `
    -WorkingDirectory $AgentDir
  $Principal = New-ScheduledTaskPrincipal -UserId $User -LogonType Interactive -RunLevel Limited
} else {
  # S4U: runs as you, without a window, whether or not a desktop session is visible.
  $Action = New-ScheduledTaskAction -Execute $Node -Argument $NodeArgs -WorkingDirectory $AgentDir
  $Principal = New-ScheduledTaskPrincipal -UserId $User -LogonType S4U -RunLevel Limited
}
$Trigger = New-ScheduledTaskTrigger -AtLogOn -User $User
$Settings = New-ScheduledTaskSettingsSet `
  -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
  -ExecutionTimeLimit ([TimeSpan]::Zero) `
  -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
  -MultipleInstances IgnoreNew

if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
  Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
  Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}
Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger -Settings $Settings -Principal $Principal `
  -Description "Serves media folders to the Signal Player Android app (admin page: http://localhost:8765/)." | Out-Null

Start-ScheduledTask -TaskName $TaskName
Write-Host "Registered and started scheduled task '$TaskName'."
Write-Host "  node: $Node $NodeArgs"
Write-Host "Open http://localhost:8765/ to approve your phone. Log file: $AgentDir\data\agent.log"
