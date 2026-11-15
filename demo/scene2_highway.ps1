# 장면 2: 소형 화물로 경부고속도로 4 → 5 → 4차로 주행 (약 4분 반)
# 사용 예) powershell -ExecutionPolicy Bypass -File demo\scene2_highway.ps1
. (Join-Path $PSScriptRoot "scene_common.ps1")

Restart-App -VehicleProfile "LIGHT_TRUCK"
Show-Countdown "장면 2 · 고속도로 지정차로 (소형 화물)"
Invoke-Drive "demo_drive.gpx"
