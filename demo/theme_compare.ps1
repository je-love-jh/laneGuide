# 테마 비교용 정지 화면: 고속도로 4차로 · 소형 화물 · 90 km/h · 같은 안내 문구를 네 테마로 차례로 띄운다.
# 저장된 테마는 바꾸지 않는다(미리보기만). 개발용 빌드에서만 된다.
# 사용 예) powershell -ExecutionPolicy Bypass -File demo\theme_compare.ps1            (엔터 칠 때마다 다음 테마)
#         powershell -ExecutionPolicy Bypass -File demo\theme_compare.ps1 -Theme BLUE (한 테마만)
param([ValidateSet("BLACK", "WHITE", "BLUE", "PURPLE", "ALL")][string]$Theme = "ALL")
. (Join-Path $PSScriptRoot "scene_common.ps1")

$list = if ($Theme -eq "ALL") { @("BLACK", "WHITE", "BLUE", "PURPLE") } else { @($Theme) }
foreach ($t in $list) {
    Invoke-Adb shell am broadcast -a com.laneguide.app.DEBUG_UI -p $Pkg --es compare $t
    Write-Host "  지금 화면: $t  (스크린샷을 찍으세요)" -ForegroundColor Cyan
    if ($list.Count -gt 1) { Read-Host "  다음 테마로 넘어가려면 Enter" | Out-Null }
}
if ($list.Count -gt 1) {
    # 미리보기 해제: 실제 상태로 되돌린다
    Invoke-Adb shell am broadcast -a com.laneguide.app.DEBUG_UI -p $Pkg --es clear 1
    Write-Host "  미리보기를 해제했습니다." -ForegroundColor DarkGray
}
