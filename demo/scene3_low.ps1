# 장면 3: 차 높이 2.0m 로 죽전통로 굴다리(2.8m) 통과 — 경고가 나오지 않아야 정상 (약 1분)
# 사용 예) powershell -ExecutionPolicy Bypass -File demo\scene3_low.ps1
. (Join-Path $PSScriptRoot "scene_common.ps1")

Restart-App -VehicleProfile "LIGHT_TRUCK" -Height 2.0
Show-Countdown "장면 3 · 굴다리 2.8m, 내 차 높이 2.0m"
Invoke-Drive "demo_underpass.gpx"
