# 지정차로 안내 (LaneGuide)

## 처음 써 보기 (3단계, Pleos Connect 에뮬레이터)
1. **앱 실행** — Android Studio에서 Run(▶), 또는 `powershell -ExecutionPolicy Bypass -File demo\build_install.ps1`. 처음에는 권한 창이 뜹니다. 가상 콘솔 기어가 **D(주행)이면 권한 창이 막히니 P(주차)에서** 실행해 허용하세요(차량 속도 권한 포함). 안 되면 `adb shell pm grant --user 10 com.laneguide.app android.car.permission.CAR_SPEED`. 처음에는 설정 화면이 열리니 프로필·높이·**화면 테마**(블랙/화이트/블루/퍼플)를 고르고 **저장**을 누르세요.
2. **[시연 주행] 누르기** — 화면의 `시연 주행: 고속도로` 또는 `시연 주행: 굴다리`. **정차 중(차량 속도 0)에만** 켜집니다. 굴다리는 프로필 소형 화물, 차 높이 **2.8m 이상**으로 저장해야 경고가 나옵니다.
3. **가상 콘솔에서 ACCEL(엑셀) 밟기** — `adb forward tcp:8088 tcp:8088` 후 브라우저로 http://127.0.0.1:8088/ 를 열고 기어를 D로 둔 뒤 ACCEL을 누르면, GPS가 아니라 **차량 속도만큼 경로를 따라 달리며** 안내가 나옵니다. 콘솔 창은 화면에 보이는 상태로 두세요(가려지면 속도가 안 올라갑니다). 경로 끝에 닿으면 자동으로 끝나고, 중간에는 파란 줄의 **[종료]** 로 실제 GPS로 돌아옵니다.

## 시연 주행이란
화면 맨 위 파란 줄에 **"시연 주행 중 (가상 위치)"** 가 항상 보입니다. 이 동안 위치는 GPS가 아니라 `app/src/main/assets`의 GPX 경로를 (차량 속도 × 시간)만큼 따라 간 가상 위치이고, 속도 표시도 차량 속도입니다. 차량 속도는 Pleos Vehicle SDK에 없어서 안드로이드 `CarPropertyManager`(PERF_VEHICLE_SPEED, 권한 CAR_SPEED)로 읽습니다.

차종에 맞춰 고속도로 지정차로와 낮은 지하차도·굴다리를 내비 옆 화면과 음성으로 미리 알려 주는 Pleos Connect 앱입니다.
화면은 내비를 왼쪽, 이 앱을 오른쪽에 두는 배치를 전제로 합니다.
## 동작하는 것
- 고속도로 구간에 들어서면 차로 그림(빨강 진입 금지 / 노랑 추월 시만 / 초록 주행)과 음성 안내
- 화면은 [DESIGN.md](DESIGN.md) 명세대로 원근 도로 차로판을 그리고, 테마 4종(블랙 기본 / 화이트 / 블루 HUD / 퍼플 HUD)을 설정에서 고릅니다. 차로 색의 뜻(금지=빨강 ✕, 추월 시만=노랑 꺾인 화살표, 주행=파랑 ↑)은 모든 테마에서 같습니다.
- 진행 방향 앞쪽에 내 차보다 낮은 시설물이 있으면 경고 (차 높이 + 0.1m 여유)
- 차종 프로필(승용 / 소형 화물 / 중대형 화물)과 차 높이 저장. 저장하면 한 줄로 접힘
- 주행 중(CarUxRestrictions NO_SETUP)에는 설정 잠금
- **Pleos NaviHelper SDK**: 높이 제한 경고 뒤에 **[우회 경로로 변경]** 버튼 표시. 운전자가 직접 누를 때만 우회 지점을 내비 경유지로 요청합니다(자동 변경 없음). 우회 지점 좌표는 후보이며 검증 전입니다.
- **Pleos TextToSpeech SDK** (ON_DEVICE)로 음성 안내 (`speech/Speaker.kt`). 오디오 포커스를 받아 다른 소리를 잠시 줄이고, 안내가 겹치면 차례로 말함
- **Pleos Vehicle SDK**
  - 차대번호(VIN) 앞 3자리로 프로필 추천 → 운전자가 저장으로 한 번 확인 (`pleos/VinReader.kt`)
  - 크루즈가 작동(ACTIVATED)하는 순간 현재 구간 차로 안내 (`pleos/PleosVehicleSignals.kt`)
  - 왼쪽 방향지시등 → 화면에 "추월 후 우측 지정차로로 복귀하세요"

**화물차 통행제한 구역 경고**는 판단 코드(`engine/AlertEngine.kt`)만 있고 데이터는 아직 비어 있습니다(`zones: []`). 실제 공공데이터를 넣을 예정입니다.

## 실행 방법

### Android Studio
1. 이 `LaneGuide` 폴더를 열고 Gradle Sync가 끝날 때까지 기다립니다.
2. Device Manager에서 **Pleos Connect v2.1.0** 에뮬레이터(AAOS, API 34)를 실행합니다.
3. 실행 대상으로 에뮬레이터를 고르고 **Run(▶)** 을 누릅니다. 권한 창이 나오면 허용합니다.
4. 프로필과 높이를 맞추고 **저장**을 누릅니다.

### 터미널
```
powershell -ExecutionPolicy Bypass -File demo\build_install.ps1
```
`gradlew installDebug` 로 빌드해 에뮬레이터에 설치합니다. Gradle 8.7은 Java 17~21이 필요합니다(Android Studio 내장 JDK가 25라면 JDK 21을 따로 지정).

에뮬레이터는 차대번호가 `invalid` 로 나와서, 개발용 빌드에서만 시연용 차대번호(KNC…, 소형 화물)로 대신 추천합니다.

## 시연 경로 (실제 도로, OpenStreetMap 기준)
| 파일 | 내용 | 맞출 설정 |
|---|---|---|
| `app/src/main/assets/demo_drive.gpx` | 경부고속도로 부산방향 용인 구간 6.6km, 편도 4 → 5 → 4차로 (시속 90km, 약 4분 반) | 소형 화물 |
| `app/src/main/assets/demo_underpass.gpx` | 용인 죽전 **죽전통로 굴다리(높이 제한 2.8m)** 접근 (시속 30km, 약 1분) | 소형 화물, 높이 **2.8m 이상** (예: 짐 실은 3.0m) |

높이를 2.7m 이하로 두면 경고가 나오지 않는 것이 정상입니다(내 차가 지나갈 수 있음).

### 자동 재생 스크립트
```
powershell -ExecutionPolicy Bypass -File demo\play_gpx.ps1 -Gpx demo_underpass.gpx -Height 3.0
```
앱을 해당 프로필·높이로 다시 시작하고, GPX를 1초 간격 `adb emu geo fix` 로 보낸 뒤 앱 로그를 보여 줍니다.
앱은 5초마다 `상태:` 로그(위치, 속도, 방향, 가장 가까운 시설물까지 거리)를 남깁니다.

에뮬레이터 `geo fix` 는 속도 0·방향 0(북쪽)을 채워 보내기 때문에, 앱은 방향을 이동 경로로 계산하고 시속 250km를 넘는 위치 점프는 무시합니다.

### 시연 영상용 장면 스크립트
`powershell -ExecutionPolicy Bypass -File demo\<파일>` 로 실행합니다. 로그는 출력하지 않습니다.

| 순서 | 파일 | 하는 일 |
|---|---|---|
| 1 | `scene1_reset.ps1` | 저장된 프로필·높이·차대번호 확인 기록만 지우고(권한 유지) 기어를 P로 → 차대번호 추천 상자가 뜸. **저장**을 누름 |
| 2 | `scene2_highway.ps1` | 기어 D, 3초 카운트다운 후 고속도로 4 → 5 → 4차로 주행 (약 4분 반) |
| 3 | `scene3_low.ps1` | 높이 2.0m, 카운트다운 후 굴다리 통과 → 경고 없음 (약 1분) |
| 4 | `scene4_high.ps1` | 높이 3.0m, 카운트다운 후 굴다리 접근 → 약 250m 전 경고 (약 1분) |
| 5 | `scene5_cruise.ps1` | 고속도로 주행을 시작하고, 가상 콘솔에서 누를 버튼을 때맞춰 화면에 안내 |

주행 장면은 위치와 함께 **차량 자체 속도**도 가상 콘솔로 보내서, 왼쪽 계기판 속도가 주행에 맞춰 변합니다(끝나면 0으로 되돌림).
기어도 가상 콘솔(`adb forward tcp:8088 tcp:8088`)로 바꿉니다. 시작 전에 위치를 고속도로·굴다리와 먼 곳으로 옮겨, 이전 장면의 위치 때문에 안내가 나오지 않게 합니다.

### 확인된 결과 (2026-11-12, 에뮬레이터 로그)
| 시험 | 결과 |
|---|---|
| 굴다리, 높이 3.0m | 시설물 약 245m 앞에서 경고 1회 |
| 굴다리, 높이 2.0m | 경고 없음 |
| 고속도로 전체 | 4 → 5 → 4차로 바뀔 때마다 안내 |
| 크루즈 Full autonomous | 차로 안내 |
| 왼쪽 방향지시등 | 복귀 안내 화면 표시 |

## Pleos 가상 콘솔로 차량 신호 바꾸기
PowerShell (`%LOCALAPPDATA%\Android\Sdk\platform-tools`):
```
.\adb forward tcp:8088 tcp:8088
```
브라우저로 http://127.0.0.1:8088/ → Emulation Controls

| 콘솔 | 앱이 받는 값 |
|---|---|
| ADAS > Cruise Control State > Ready | ENABLED |
| ADAS > Cruise Control State > **Full autonomous** | **ACTIVATED** (크루즈 안내가 나옴) |
| ADAS > Cruise Control State > Not ready | 값 없음(null) |
| Convenience > Turn Signal State > Blink left / Flash left | LEFT (복귀 안내) |
| Convenience > Turn Signal State > Blink right / Off | RIGHT / OFF |

Longitudinal·Lateral 은 대부분 ENABLED 로 와서 크루즈 안내가 나오지 않습니다. 시연할 때는 Ready → Full autonomous 로 누릅니다.

로그 확인: `.\adb logcat -d -s AlertService PleosVehicleSignals VinReader`

## 데이터
`app/src/main/assets/golden_dataset.json` (네트워크 없이 동작)
- 고속도로: 경부고속도로 부산방향 용인 구간, 차로 수는 OSM 기준 (로드뷰 재확인 전)
- 굴다리: 죽전통로 굴다리 2.8m (OSM way 1195151859, maxheight 기준)
- 통행제한 구역: 비어 있음
- 지정차로 규칙(`rules/LaneRules.kt`)은 도로교통법 시행규칙 [별표 9]를 바탕으로 만들었습니다.

## 폴더 구조
```
app/src/main/java/com/laneguide/app/
  model/    데이터 모양 (차종, 차로 상태, 구간, 시설물, 통행제한 구역)
  geo/      거리·방위각 계산
  rules/    차종별 차로 규칙
  data/     데이터 읽기, 프로필 저장
  engine/   경고 판단 엔진, 백그라운드 서비스, 화면 상태
  speech/   음성 안내 (오디오 포커스, 앞 무음)
  car/      주행 중 제한 감시
  ui/       메인 화면, 차로 그림
  pleos/    Pleos Vehicle SDK (차량 신호, 차대번호)
demo/       장면·재생·빌드 스크립트 (시연 GPX 는 app/src/main/assets)
```

지도 데이터 © OpenStreetMap contributors (ODbL).

