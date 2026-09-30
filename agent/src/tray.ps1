# Signal Agent tray icon (Windows). Written to <dataDir>\tray.ps1 and started by src/tray.js:
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File tray.ps1 -Port 8765 -AgentPid 1234 -Name "PC"
# Uses only System.Windows.Forms + System.Drawing; the icon is drawn at runtime. Keep this file ASCII.
param(
  [int]$Port = 8765,
  [int]$AgentPid = 0,
  [string]$Name = 'this PC'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()
# From here on an error in a click/timer handler must never bring up a WinForms error dialog.
$ErrorActionPreference = 'SilentlyContinue'

$script:Base = 'http://127.0.0.1:' + $Port
$script:PageUrl = 'http://localhost:' + $Port + '/'
$script:Headers = @{ 'X-Signal-Admin' = '1' }
$script:Seen = @{}
$script:BalloonAction = ''
$script:UpdateSeen = ''
$script:Quitting = $false

function Get-TrayState {
  try {
    return Invoke-RestMethod -Uri ($script:Base + '/admin/tray-state') -Headers $script:Headers -TimeoutSec 3 -UseBasicParsing
  } catch {
    return $null
  }
}

function Invoke-AgentPost([string]$Path, [string]$Body) {
  try {
    return Invoke-RestMethod -Method Post -Uri ($script:Base + $Path) -Headers $script:Headers -ContentType 'application/json' -Body $Body -TimeoutSec 3 -UseBasicParsing
  } catch {
    return $null
  }
}

function Start-Update {
  $r = Invoke-AgentPost '/admin/update' '{}'
  $script:BalloonAction = ''
  if ($r -ne $null -and [bool]$r.ok) {
    $script:Notify.ShowBalloonTip(6000, 'Signal Agent', 'Updating ' + [char]0x2014 + ' Signal Agent will restart in a moment', [System.Windows.Forms.ToolTipIcon]::Info)
  } else {
    $script:Notify.ShowBalloonTip(6000, 'Signal Agent', 'The update could not start. Open Signal Agent for details.', [System.Windows.Forms.ToolTipIcon]::Warning)
  }
}

function Open-Page {
  try { Start-Process $script:PageUrl } catch { }
}

function Set-Tip([string]$PcName) {
  $tip = 'Signal Agent ' + [char]0x2014 + ' ' + $PcName
  if ($tip.Length -gt 63) { $tip = $tip.Substring(0, 63) }
  $script:Notify.Text = $tip
}

# ---- icon: amber square with four dark waveform bars (matches the app icon) ----
function New-SignalIcon {
  $bmp = New-Object System.Drawing.Bitmap 32, 32
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.Color]::Transparent)
  $amber = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 0xF2, 0xA9, 0x3B))
  $dark = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 0x1A, 0x13, 0x00))
  $g.FillRectangle($amber, 0, 0, 32, 32)
  $heights = @(12, 22, 16, 8)
  for ($i = 0; $i -lt 4; $i++) {
    $h = $heights[$i]
    $x = 4 + $i * 7
    $y = [int]((32 - $h) / 2)
    $g.FillRectangle($dark, $x, $y, 4, $h)
  }
  $g.Dispose()
  $amber.Dispose()
  $dark.Dispose()
  return [System.Drawing.Icon]::FromHandle($bmp.GetHicon())
}

$script:Notify = New-Object System.Windows.Forms.NotifyIcon
$script:Notify.Icon = New-SignalIcon
Set-Tip $Name

# ---- menu ----
$menu = New-Object System.Windows.Forms.ContextMenuStrip
$openItem = New-Object System.Windows.Forms.ToolStripMenuItem 'Open Signal Agent'
$openItem.Font = New-Object System.Drawing.Font($openItem.Font, [System.Drawing.FontStyle]::Bold)
$openItem.add_Click({ Open-Page })
$startItem = New-Object System.Windows.Forms.ToolStripMenuItem 'Start with Windows'
$startItem.CheckOnClick = $false
$startItem.add_Click({
  $want = -not $script:StartItem.Checked
  $body = '{"enabled":' + $(if ($want) { 'true' } else { 'false' }) + '}'
  $r = Invoke-AgentPost '/admin/startup' $body
  if ($r -ne $null) { $script:StartItem.Checked = [bool]$r.enabled }
})
$script:StartItem = $startItem
$updateItem = New-Object System.Windows.Forms.ToolStripMenuItem 'Update Signal Agent'
$updateItem.Visible = $false
$updateItem.add_Click({ Start-Update })
$script:UpdateItem = $updateItem
$quitItem = New-Object System.Windows.Forms.ToolStripMenuItem 'Quit Signal Agent'
$quitItem.add_Click({
  $script:Quitting = $true
  [void](Invoke-AgentPost '/admin/quit' '{}')
  Stop-Tray
})
[void]$menu.Items.Add($openItem)
[void]$menu.Items.Add($startItem)
[void]$menu.Items.Add($updateItem)
[void]$menu.Items.Add((New-Object System.Windows.Forms.ToolStripSeparator))
[void]$menu.Items.Add($quitItem)
$menu.add_Opening({
  $s = Get-TrayState
  if ($s -ne $null) {
    $script:StartItem.Visible = [bool]$s.startupSupported
    $script:StartItem.Checked = [bool]$s.startup
    $u = $s.update
    $script:UpdateItem.Visible = ($u -ne $null -and [bool]$u.available)
    if ($script:UpdateItem.Visible) {
      if ([bool]$u.downloading) { $script:UpdateItem.Text = 'Updating...'; $script:UpdateItem.Enabled = $false }
      else { $script:UpdateItem.Text = 'Update to ' + [string]$u.latest; $script:UpdateItem.Enabled = $true }
    }
  }
})
$script:Notify.ContextMenuStrip = $menu
$script:Notify.add_DoubleClick({ Open-Page })
$script:Notify.add_BalloonTipClicked({
  if ($script:BalloonAction -eq 'page') { Open-Page }
  elseif ($script:BalloonAction -eq 'update') { Start-Update }
})
$script:Notify.Visible = $true

# ---- first-run hint (once per PC, marker next to this script) ----
$marker = Join-Path $PSScriptRoot 'tray-welcomed'
if (-not (Test-Path -LiteralPath $marker)) {
  $script:BalloonAction = ''
  $script:Notify.ShowBalloonTip(8000, 'Signal Agent', 'Signal Agent is running ' + [char]0x2014 + ' it lives here in the tray', [System.Windows.Forms.ToolTipIcon]::Info)
  try { Set-Content -LiteralPath $marker -Value 'shown' } catch { }
}

$script:Ctx = New-Object System.Windows.Forms.ApplicationContext

function Stop-Tray {
  try { $script:Timer.Stop() } catch { }
  try { $script:Notify.Visible = $false; $script:Notify.Dispose() } catch { }
  try { $script:Ctx.ExitThread() } catch { }
}

# ---- every 5 s: is the agent still alive? any new pair requests? ----
$script:Timer = New-Object System.Windows.Forms.Timer
$script:Timer.Interval = 5000
$script:Timer.add_Tick({
  if ($script:Quitting) { return }
  if ($AgentPid -gt 0 -and -not (Get-Process -Id $AgentPid -ErrorAction SilentlyContinue)) {
    Stop-Tray
    return
  }
  $s = Get-TrayState
  if ($s -eq $null) { return }
  if ($s.name) { Set-Tip ([string]$s.name) }
  $new = $null
  foreach ($r in @($s.requests)) {
    if ($r -ne $null -and -not $script:Seen.ContainsKey([string]$r.id)) {
      $script:Seen[[string]$r.id] = $true
      $new = $r
    }
  }
  $u = $s.update
  if ($u -ne $null -and [bool]$u.available -and -not [bool]$u.downloading -and [string]$u.latest -ne $script:UpdateSeen) {
    $script:UpdateSeen = [string]$u.latest
    $script:BalloonAction = 'update'
    $script:Notify.ShowBalloonTip(10000, 'Signal Agent update', 'Version ' + [string]$u.latest + ' is available ' + [char]0x2014 + ' click to install', [System.Windows.Forms.ToolTipIcon]::Info)
  }
  if ($new -ne $null) {
    $script:BalloonAction = 'page'
    $who = [string]$new.deviceName
    if (-not $who) { $who = 'A phone' }
    $script:Notify.ShowBalloonTip(10000, 'Phone wants to pair', $who + ' wants to pair ' + [char]0x2014 + ' click to approve', [System.Windows.Forms.ToolTipIcon]::Info)
  }
})
$script:Timer.Start()

try {
  [System.Windows.Forms.Application]::Run($script:Ctx)
} finally {
  try { $script:Notify.Visible = $false; $script:Notify.Dispose() } catch { }
}
