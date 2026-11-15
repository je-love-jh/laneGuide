# 장면 4: 짐을 실어 차 높이 3.0m 로 죽전통로 굴다리(2.8m) 접근 — 약 250m 전에 경고 (약 1분)
# 사용 예) powershell -ExecutionPolicy Bypass -File demo\scene4_high.ps1
. (Join-Path $PSScriptRoot "scene_common.ps1")

Restart-App -VehicleProfile "LIGHT_TRUCK" -Height 3.0
Show-Countdown "장면 4 · 굴다리 2.8m, 내 차 높이 3.0m"
Invoke-Drive "demo_underpass.gpx"
