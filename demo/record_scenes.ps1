# Contest demo video recorder. ASCII only on purpose (Windows PowerShell 5.1 reads BOM-less files as ANSI).
# Usage: powershell -ExecutionPolicy Bypass -File demo\record_scenes.ps1 -Scene 1   (1..9)
param([Parameter(Mandatory = $true)][int]$Scene, [switch]$NoRecord)
. (Join-Path $PSScriptRoot "scene_common.ps1")

$VideoDir = Join-Path $PSScriptRoot "..\video"
New-Item -ItemType Directory -Force $VideoDir | Out-Null
$VideoDir = (Resolve-Path $VideoDir).Path
$Names = @{
    1 = "01_first_run"; 2 = "02_split_highway"; 3 = "03_split_full_split"; 4 = "04_five_lanes"; 5 = "05_cruise"
    6 = "06_left_signal"; 7 = "07_underpass_2.0m"; 8 = "08_underpass_3.0m"; 9 = "09_themes"
}
$Secs = @{ 1 = 15; 2 = 20; 3 = 20; 4 = 15; 5 = 10; 6 = 10; 7 = 15; 8 = 15; 9 = 10 }

# touch positions (px on the 2560x1440 emulator screen)
$FullDemoHighway = @(2090, 699)   # unused (full layout button moves with the alert box), kept for reference
$SplitDemoHighway = @(1939, 1111)
$SplitDemoUnder = @(2336, 1111)
$FullSave = @(2221, 1220)

function Tap($xy) { Invoke-Adb shell input tap $xy[0] $xy[1] }

# find a button by its text on screen (uiautomator) and tap its center. Returns $false if not found.
function Find-Text([string]$Text) {
    Invoke-Adb shell uiautomator dump /sdcard/ui.xml
    $tmp = Join-Path $env:TEMP "laneguide_ui.xml"
    Invoke-Adb pull /sdcard/ui.xml $tmp
    $xml = Get-Content -Raw -Encoding UTF8 $tmp
    $m = [regex]::Match($xml, 'text="' + [regex]::Escape($Text) + '"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $null }
    $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
    $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
    return @($x, $y)
}

# tap the demo button at $xy (found beforehand with Find-Text, so there is no delay inside the recording)
# and make sure the demo really started (log line); retry up to 3 times
function Start-Demo($xy) {
    if (-not $xy) { Write-Host "DEMO BUTTON NOT FOUND"; return }
    for ($n = 0; $n -lt 3; $n++) {
        Tap $xy
        if (Wait-Log "시연 주행 시작" 3) { return }
    }
    Write-Host "DEMO DID NOT START"
}
# Window state from real pixels: look at a point in the left and one in the right half of the app area.
#   full   : app is dark on both
#   split  : navi (white map) on the left, app (dark) on the right  <- wanted split layout
#   splitL : app (dark) on the left, navi on the right
Add-Type -AssemblyName System.Drawing
function Get-Mode {
    Invoke-Adb shell screencap -p /sdcard/mode.png
    $tmp = Join-Path $env:TEMP "laneguide_mode.png"
    Invoke-Adb pull /sdcard/mode.png $tmp
    $bmp = [System.Drawing.Bitmap]::FromFile($tmp)
    try {
        $l = $bmp.GetPixel(1000, 700); $r = $bmp.GetPixel(2200, 700)
        $ld = (($l.R + $l.G + $l.B) / 3) -lt 110
        $rd = (($r.R + $r.G + $r.B) / 3) -lt 110
    } finally { $bmp.Dispose() }
    if ($ld -and $rd) { return "full" }
    if ((-not $ld) -and $rd) { return "split" }
    if ($ld -and (-not $rd)) { return "splitL" }
    return "unknown"
}
function Set-Mode([string]$Want) {
    $now = Get-Mode
    Write-Host "window mode: $now -> $Want"
    if ($now -eq $Want -or $now -eq "unknown") { return }
    if ($Want -eq "split") {
        if ($now -eq "full") {
            Invoke-Adb shell input swipe 2547 720 1700 720 600
            Start-Sleep -Seconds 9
        }
        # tapping the handle swaps the panes: navi on the left, our app on the right
        Invoke-Adb shell input tap 1705 720
        Start-Sleep -Seconds 7
    } else {
        if ($now -eq "split") { Invoke-Adb shell input swipe 1705 720 800 720 600 }
        else { Invoke-Adb shell input swipe 1705 720 2547 720 600 }
        Start-Sleep -Seconds 9
    }
}
$sw = $null
function Start-Rec([string]$Name, [int]$Limit) {
    $script:OutFile = Join-Path $VideoDir "$Name.webm"
    if (-not $NoRecord) {
        if (Test-Path $script:OutFile) { Remove-Item $script:OutFile -Force }
        Invoke-Adb emu screenrecord start --time-limit ($Limit + 5) $script:OutFile
        Start-Sleep -Milliseconds 1200
    }
    $script:sw = [Diagnostics.Stopwatch]::StartNew()
}
function Wait-Until([double]$Sec) {
    $ms = $Sec * 1000 - $script:sw.ElapsedMilliseconds
    if ($ms -gt 0) { Start-Sleep -Milliseconds $ms }
}
function Stop-Rec {
    $script:Elapsed = [Math]::Round($script:sw.Elapsed.TotalSeconds, 1)
    if (-not $NoRecord) {
        Invoke-Adb emu screenrecord stop
        Start-Sleep -Seconds 3
    }
}
# hold a vehicle speed (km/h) with one command per second until second $Until of the recording
function Hold-Speed([int]$Kph, [double]$Until) {
    while ($script:sw.Elapsed.TotalSeconds -lt $Until) {
        Set-VehicleSpeed $Kph
        $next = [Math]::Floor($script:sw.Elapsed.TotalSeconds) + 1
        Wait-Until ([Math]::Min($next, $Until))
    }
}
# stream GPX points [From..To] as GPS, 1 per second; speed follows the distance between points
function Drive-Range([string]$Gpx, [int]$From, [int]$To, [scriptblock]$Cue = $null) {
    $pts = @(Get-GpxPoints $Gpx)
    $inv = [Globalization.CultureInfo]::InvariantCulture
    $t0 = [Diagnostics.Stopwatch]::StartNew()
    $prev = $null
    for ($i = $From; $i -le $To; $i++) {
        $p = $pts[$i]
        if ($Cue) { & $Cue ($i - $From) }
        Set-Location-Emu $p.lat $p.lon
        $lat = [double]::Parse($p.lat, $inv); $lon = [double]::Parse($p.lon, $inv)
        $kph = 90   # first point of a range: the GPX is driven at 90 km/h (a 0 here shows as a speed dip)
        if ($prev) { $kph = [int][Math]::Round((Get-DistanceM $prev[0] $prev[1] $lat $lon) * 3.6) }
        Set-VehicleSpeed $kph
        $prev = @($lat, $lon)
        $wait = ($i - $From + 1) * 1000 - $t0.ElapsedMilliseconds
        if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
    }
}
function Put-Console([string]$PathAndQuery) {
    try { Send-Console $PathAndQuery } catch { Write-Host "console call failed: $PathAndQuery" }
}

$name = $Names[$Scene]; $secs = $Secs[$Scene]
Invoke-Adb forward tcp:8088 tcp:8088
Put-Console "/vehicle/driving-mode?ad_mode_state=ready"
Put-Console "/vehicle/turn-signal?mode=Off"

switch ($Scene) {
    1 {
        Set-Mode "full"
        Set-Gear "Park"
        Invoke-Adb shell am force-stop $Pkg
        # keep only the theme (purple); everything else is first-run state
        $tmp = Join-Path $env:TEMP "laneguide_p.xml"
        [IO.File]::WriteAllText($tmp, "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>`n<map>`n    <string name=`"theme`">PURPLE</string>`n</map>`n", (New-Object Text.UTF8Encoding $false))
        Invoke-Adb push $tmp /data/local/tmp/p.xml
        Invoke-Adb shell chmod 644 /data/local/tmp/p.xml
        Invoke-Adb shell "run-as $Pkg --user 10 cp /data/local/tmp/p.xml shared_prefs/profile.xml"
        Set-Location-Emu $ParkLat $ParkLon
        Start-Rec $name $secs
        Invoke-Adb shell am start -n "$Pkg/.ui.MainActivity"
        Wait-Until 8
        Tap $FullSave
        Wait-Until $secs
        Stop-Rec
    }
    { $_ -in 2, 3 } {
        Restart-App -VehicleProfile "LIGHT_TRUCK"
        Set-Mode "split"
        $btn = Find-Text "시연 주행: 고속도로"
        Start-Rec $name $secs
        Wait-Until 1
        Start-Demo $btn
        Wait-Until 2
        if ($Scene -eq 2) {
            Hold-Speed 80 $secs
        } else {
            Hold-Speed 80 3
            Invoke-Adb shell input swipe 1705 720 800 720 600    # split -> full
            Hold-Speed 80 9
            Invoke-Adb shell input swipe 2547 720 1700 720 600   # full -> split
            Hold-Speed 80 13
            Invoke-Adb shell input tap 1705 720                  # swap panes (navi left, app right)
            Hold-Speed 80 $secs
        }
        Stop-Rec
        Set-VehicleSpeed 0
    }
    4 {
        Restart-App -VehicleProfile "LIGHT_TRUCK"
        Set-Mode "split"
        Drive-Range "demo_drive.gpx" 100 119
        Wait-SpeechDone
        Start-Rec $name $secs
        Drive-Range "demo_drive.gpx" 120 (120 + $secs - 1)
        Stop-Rec
        Set-VehicleSpeed 0
    }
    5 {
        Restart-App -VehicleProfile "LIGHT_TRUCK"
        Set-Mode "split"
        Drive-Range "demo_drive.gpx" 0 19
        Wait-SpeechDone
        Start-Rec $name $secs
        Drive-Range "demo_drive.gpx" 20 (20 + $secs - 1) { param($k) if ($k -eq 2) { Put-Console "/vehicle/driving-mode?ad_mode_state=full_autonomous" } }
        Stop-Rec
        Put-Console "/vehicle/driving-mode?ad_mode_state=ready"
        Set-VehicleSpeed 0
    }
    6 {
        Restart-App -VehicleProfile "LIGHT_TRUCK"
        Set-Mode "split"
        Put-Console "/vehicle/driving-mode?ad_mode_state=full_autonomous"
        Drive-Range "demo_drive.gpx" 28 47
        Wait-SpeechDone
        Start-Rec $name $secs
        Drive-Range "demo_drive.gpx" 48 (48 + $secs - 1) { param($k) if ($k -eq 1) { Put-Console "/vehicle/turn-signal?mode=BlinkLeft" }; if ($k -eq 7) { Put-Console "/vehicle/turn-signal?mode=Off" } }
        Stop-Rec
        Put-Console "/vehicle/turn-signal?mode=Off"
        Put-Console "/vehicle/driving-mode?ad_mode_state=ready"
        Set-VehicleSpeed 0
    }
    { $_ -in 7, 8 } {
        $h = 2.0; if ($Scene -eq 8) { $h = 3.0 }
        Restart-App -VehicleProfile "LIGHT_TRUCK" -Height $h
        Set-Mode "split"
        $btn = Find-Text "시연 주행: 굴다리"
        Start-Rec $name $secs
        Wait-Until 1
        Start-Demo $btn
        Wait-Until 2
        Hold-Speed 80 $secs
        Stop-Rec
        Set-VehicleSpeed 0
    }
    9 {
        Restart-App -VehicleProfile "LIGHT_TRUCK" -Height 1.7
        Set-Gear "Park"
        Start-Sleep -Seconds 2
        Start-Rec $name $secs
        Wait-Until 0.5; Invoke-Adb shell input tap 2395 1203      # change (open settings)
        Wait-Until 2.5; Invoke-Adb shell input tap 2026 622       # black
        Wait-Until 4;   Invoke-Adb shell input tap 2156 622       # white
        Wait-Until 5.5; Invoke-Adb shell input tap 2285 622       # blue
        Wait-Until 7;   Invoke-Adb shell input tap 2415 622       # purple
        Wait-Until 8.5; Invoke-Adb shell input tap 2221 1220      # save
        Wait-Until $secs
        Stop-Rec
    }
}
$file = Join-Path $VideoDir "$name.webm"
$len = 0; if (Test-Path $file) { $len = (Get-Item $file).Length }
Write-Host ("RESULT scene={0} file={1} bytes={2} elapsed={3}s" -f $Scene, "$name.webm", $len, $script:Elapsed)
