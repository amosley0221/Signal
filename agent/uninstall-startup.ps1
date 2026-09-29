<#
  Stops and removes the "Signal Agent" scheduled task.
  Usage: powershell -ExecutionPolicy Bypass -File .\uninstall-startup.ps1
#>
$ErrorActionPreference = "Stop"
$TaskName = "Signal Agent"
$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($task) {
  Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
  Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
  Write-Host "Removed scheduled task '$TaskName'."
} else {
  Write-Host "Scheduled task '$TaskName' is not installed."
}
