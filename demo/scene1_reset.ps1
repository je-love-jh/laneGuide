# 장면 1: 처음 설치한 것처럼 — 저장된 프로필·높이·차대번호 확인 기록을 지우고 앱을 연다.
# 차대번호 추천 상자가 다시 뜬다. 앱 권한은 그대로 둔다(앱 데이터 전체 삭제를 하지 않음).
# 사용 예) powershell -ExecutionPolicy Bypass -File demo\scene1_reset.ps1
. (Join-Path $PSScriptRoot "scene_common.ps1")

Set-Location-Emu $ParkLat $ParkLon
# 정차(P) 상태여야 저장 버튼을 누를 수 있다
Set-Gear "Park"
Invoke-Adb shell am force-stop $Pkg
# 개발용 빌드에서만 되는 run-as 로 설정 파일(profile.xml)만 지운다.
# 자동차용 안드로이드는 앱이 운전자 계정(보통 사용자 10)에서 돌아가므로 현재 사용자를 지정해야 한다.
$user = (& $Adb shell am get-current-user 2>$null | Out-String).Trim()
Invoke-Adb shell run-as $Pkg --user $user rm -f shared_prefs/profile.xml
Invoke-Adb shell am start -n "$Pkg/.ui.MainActivity"
