# 시연 영상용 장면 스크립트(scene*.ps1)가 함께 쓰는 함수. 직접 실행하지 않는다.

$script:Adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$script:Pkg = "com.laneguide.app"
# 고속도로·굴다리 어디에도 걸리지 않는 대기 위치 (시작 전 위치를 여기로 옮겨, 이전 장면 위치로 안내가 나오지 않게 한다)
$script:ParkLat = "37.3200"
$script:ParkLon = "127.1300"

function Invoke-Adb {
    # adb 출력은 화면에 내지 않는다
    & $script:Adb @args 2>$null | Out-Null
}

# 가상 콘솔(8088)로 기어를 바꾼다: Park 면 설정 잠금이 풀리고, Drive 면 주행 중이라 설정이 잠긴다
# 콘솔은 열린 콘솔 페이지가 있어야 명령을 받는다(없으면 emulator_inactive). 그래서 페이지처럼 하트비트를 먼저 보낸다.
$script:Console = "http://127.0.0.1:8088"

function Send-Console([string]$PathAndQuery) {
    Invoke-WebRequest -UseBasicParsing -Method Post "$script:Console/browser/session?client=laneguide-script" -TimeoutSec 3 | Out-Null
    Invoke-WebRequest -UseBasicParsing -Method Put "$script:Console$PathAndQuery" -TimeoutSec 3 | Out-Null
}

function Set-Gear([string]$Gear) {
    $forwards = & $script:Adb forward --list 2>$null | Out-String
    if ($forwards -notmatch 'tcp:8088 tcp:8088') { Invoke-Adb forward tcp:8088 tcp:8088 }
    try {
        Send-Console "/vehicle/gear?gear=$Gear"
    } catch {
        Write-Host "  (가상 콘솔에 연결하지 못해 기어를 $Gear 로 바꾸지 못했습니다. 콘솔에서 직접 바꿔 주세요)" -ForegroundColor DarkYellow
    }
}

# 차량 자체 속도(왼쪽 계기판에 보이는 값)를 바꾼다. 실패해도 주행은 계속한다.
function Set-VehicleSpeed([int]$Kph) {
    try {
        Send-Console "/vehicle/controls?speed=$Kph&accelerator=0&brake=0&steering=0"
    } catch {
        if (-not $script:SpeedWarned) {
            $script:SpeedWarned = $true
            Write-Host "  (가상 콘솔에 연결하지 못해 계기판 속도는 바뀌지 않습니다)" -ForegroundColor DarkYellow
        }
    }
}

# 두 GPS 지점 사이 거리(m)
function Get-DistanceM([double]$Lat1, [double]$Lon1, [double]$Lat2, [double]$Lon2) {
    $r = [Math]::PI / 180
    $dLat = ($Lat2 - $Lat1) * $r
    $dLon = ($Lon2 - $Lon1) * $r
    $h = [Math]::Pow([Math]::Sin($dLat / 2), 2) + [Math]::Cos($Lat1 * $r) * [Math]::Cos($Lat2 * $r) * [Math]::Pow([Math]::Sin($dLon / 2), 2)
    return 2 * 6371000 * [Math]::Asin([Math]::Sqrt($h))
}

function Set-Location-Emu([string]$Lat, [string]$Lon) {
    Invoke-Adb emu geo fix $Lon $Lat
}

# wait until a logcat line matches the regex (max $TimeoutSec seconds). Returns $true if found.
function Wait-Log([string]$Pattern, [int]$TimeoutSec = 20) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $hit = & $script:Adb logcat -d 2>$null | Select-String -Pattern $Pattern -Quiet
        if ($hit) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

# wait until the TTS is not speaking (last Speaker line is "playback end" or there is none)
function Wait-SpeechDone([int]$TimeoutSec = 30) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $last = & $script:Adb logcat -d 2>$null | Select-String -Pattern "Speaker :" | Select-Object -Last 1
        if (-not $last -or $last.Line -match "재생 끝|재생 오류|준비 완료") { Start-Sleep -Seconds 6; return }   # the emulator audio output lags behind the "playback end" log by a few seconds
        Start-Sleep -Milliseconds 700
    }
}

function Restart-App([string]$VehicleProfile, [double]$Height = -1) {
    Set-Location-Emu $script:ParkLat $script:ParkLon
    Set-Gear "Drive"
    Set-VehicleSpeed 0
    Invoke-Adb shell am force-stop $script:Pkg
    $extras = @()
    if ($VehicleProfile) { $extras += @("--es", "profile", $VehicleProfile) }
    if ($Height -gt 0) { $extras += @("--ef", "height", "$Height") }
    Invoke-Adb logcat -c
    Invoke-Adb shell am start -n "$script:Pkg/.ui.MainActivity" @extras
    # app is ready when the speed reader and the TTS engine have reported (takes 4-10 s); the demo buttons stay disabled until then
    Wait-Log "VehicleSpeed: .*(차량 속도 현재값)" 40
    Wait-Log "음성 엔진 준비 완료" 20
    Start-Sleep -Seconds 3
    # 서비스가 첫 위치를 대기 위치로 받게 한 번 더 보낸다
    Set-Location-Emu $script:ParkLat $script:ParkLon
    Start-Sleep -Seconds 2
}

function Show-Countdown([string]$Title) {
    Clear-Host
    Write-Host ""
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host ""
    foreach ($n in 3, 2, 1) {
        Write-Host "        $n" -ForegroundColor Yellow
        Start-Sleep -Seconds 1
    }
    Write-Host "      출발!" -ForegroundColor Green
}

function Get-GpxPoints([string]$Name) {
    # GPX 는 앱과 같이 쓰도록 app/src/main/assets 에 둔다 (앱의 [시연 주행]도 같은 파일을 읽음)
    [xml]$doc = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot "..\app\src\main\assets\$Name")
    return $doc.gpx.trk.trkseg.trkpt
}

# GPX 를 1초 간격으로 보낸다. $Cues 는 @{ 지점번호 = "화면에 띄울 안내" } (없으면 생략)
# 위치와 함께 차량 자체 속도도 맞춰 보내서, 왼쪽 계기판 속도가 주행에 맞게 변한다. 끝나면 0 으로 되돌린다.
function Invoke-Drive([string]$Gpx, [hashtable]$Cues = @{}) {
    $points = Get-GpxPoints $Gpx
    $inv = [Globalization.CultureInfo]::InvariantCulture
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $prev = $null
    $i = 0
    try {
        foreach ($p in $points) {
            if ($Cues.ContainsKey($i)) {
                Write-Host ""
                Write-Host $Cues[$i] -ForegroundColor Yellow
            }
            Set-Location-Emu $p.lat $p.lon
            $lat = [double]::Parse($p.lat, $inv); $lon = [double]::Parse($p.lon, $inv)
            # 지점 간격이 1초이므로 이동 거리(m) x 3.6 = 시속(km/h)
            $kph = 0
            if ($prev) { $kph = [int][Math]::Round((Get-DistanceM $prev[0] $prev[1] $lat $lon) * 3.6) }
            Set-VehicleSpeed $kph
            $prev = @($lat, $lon)
            $i++
            # 명령 보내는 데 걸린 시간을 빼서 지점 간격을 1초로 맞춘다
            $wait = $i * 1000 - $watch.ElapsedMilliseconds
            if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
        }
    } finally {
        Set-VehicleSpeed 0
    }
}
