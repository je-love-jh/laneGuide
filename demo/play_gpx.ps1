param(
    [string]$Gpx = "demo_underpass.gpx",
    [string]$Profile = "LIGHT_TRUCK",
    [double]$Height = 3.0,
    [int]$IntervalMs = 1000
)
# 에뮬레이터에 GPX 경로를 1초 간격으로 보내고, 끝나면 앱 로그를 보여 준다.
# 사용 예) powershell -ExecutionPolicy Bypass -File demo\play_gpx.ps1 -Gpx demo_underpass.gpx -Height 3.0

$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$gpxPath = if (Test-Path $Gpx) { $Gpx } else { Join-Path $PSScriptRoot "..\app\src\main\assets\$Gpx" }

Write-Host "앱을 다시 시작합니다: $Profile, 높이 $Height m"
& $adb logcat -c
& $adb shell am force-stop com.laneguide.app
& $adb shell am start -n com.laneguide.app/.ui.MainActivity --es profile $Profile --ef height $Height | Out-Null
Start-Sleep -Seconds 6

[xml]$doc = Get-Content -Raw -Encoding UTF8 $gpxPath
$points = $doc.gpx.trk.trkseg.trkpt
Write-Host "주행 시작: $($points.Count)개 지점 ($gpxPath)"
$i = 0
foreach ($p in $points) {
    & $adb emu geo fix $p.lon $p.lat | Out-Null
    $i++
    if ($i % 20 -eq 0) { Write-Host "  $i / $($points.Count)" }
    Start-Sleep -Milliseconds $IntervalMs
}
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "===== 앱 로그 ====="
& $adb logcat -d -s AlertService:I VinReader:D PleosVehicleSignals:D
