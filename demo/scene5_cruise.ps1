# 장면 5: 고속도로 주행 중 크루즈 작동 → 차로 안내, 왼쪽 방향지시등 → 복귀 안내
# 주행은 스크립트가 하고, 가상 콘솔 버튼은 사람이 누른다. 누를 때가 되면 화면에 안내가 뜬다.
# 사용 예) powershell -ExecutionPolicy Bypass -File demo\scene5_cruise.ps1
. (Join-Path $PSScriptRoot "scene_common.ps1")

Restart-App -VehicleProfile "LIGHT_TRUCK"

Clear-Host
Write-Host ""
Write-Host "  장면 5 · 크루즈 / 방향지시등" -ForegroundColor Cyan
Write-Host ""
Write-Host "  가상 콘솔: http://127.0.0.1:8088/  →  Emulation Controls"
Write-Host "  주행 시작 전에 맞춰 둘 것:"
Write-Host "    - ADAS > Cruise Control State      : Ready"
Write-Host "    - Convenience > Turn Signal State  : Off"
Write-Host ""
Write-Host "  누를 순서 (때가 되면 노란 글씨로 다시 알려 줍니다)"
Write-Host "    1. Cruise Control State > Full autonomous   → 차로 안내 음성"
Write-Host "    2. Turn Signal State > Blink left           → '추월 후 우측 지정차로로 복귀하세요'"
Write-Host "    3. Turn Signal State > Off"
Write-Host "    4. Cruise Control State > Ready             (다음 촬영 준비)"
Write-Host ""
Write-Host "  ※ Longitudinal / Lateral 은 크루즈 작동으로 오지 않을 때가 많아 쓰지 않습니다."
Write-Host ""
Read-Host "  콘솔을 Ready / Off 로 맞췄으면 Enter" | Out-Null

Show-Countdown "장면 5 · 크루즈 / 방향지시등"
# 지점 번호 = 출발 후 초. 0초에 '편도 4차로' 안내가 나오고 약 10초 걸린다.
$cues = @{
    15 = "  ▶ 지금: Cruise Control State > Full autonomous"
    35 = "  ▶ 지금: Turn Signal State > Blink left"
    45 = "  ▶ 지금: Turn Signal State > Off"
    55 = "  ▶ 지금: Cruise Control State > Ready  (이후는 그대로 두면 됩니다)"
}
Invoke-Drive "demo_drive.gpx" $cues
