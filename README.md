# 지정차로 안내 (LaneGuide)

차종에 맞춰 고속도로 지정차로와 낮은 지하차도·굴다리를 내비 옆 화면과 음성으로 미리 알려 주는 Pleos Connect 앱입니다.
화면은 내비를 왼쪽, 이 앱을 오른쪽에 두는 배치를 전제로 합니다. 앱은 경로를 만들거나 바꾸지 않고 안내만 합니다.

## 동작하는 것
- 고속도로 구간에 들어서면 차로 그림(빨강 진입 금지 / 노랑 추월 시만 / 초록 주행)과 음성 안내
- 진행 방향 앞쪽에 내 차보다 낮은 시설물이 있으면 경고 (차 높이 + 0.1m 여유)
- 차종 프로필(승용 / 소형 화물 / 중대형 화물)과 차 높이 저장. 저장하면 한 줄로 접힘
- 주행 중(CarUxRestrictions NO_SETUP)에는 설정 잠금
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
| `demo/demo_drive.gpx` | 경부고속도로 부산방향 용인 구간 6.6km, 편도 4 → 5 → 4차로 (시속 90km, 약 4분 반) | 소형 화물 |
| `demo/demo_underpass.gpx` | 용인 죽전 **죽전통로 굴다리(높이 제한 2.8m)** 접근 (시속 30km, 약 1분) | 소형 화물, 높이 **2.8m 이상** (예: 짐 실은 3.0m) |

높이를 2.7m 이하로 두면 경고가 나오지 않는 것이 정상입니다(내 차가 지나갈 수 있음).

### 자동 재생 스크립트
```
powershell -ExecutionPolicy Bypass -File demo\play_gpx.ps1 -Gpx demo_underpass.gpx -Height 3.0
```
앱을 해당 프로필·높이로 다시 시작하고, GPX를 1초 간격 `adb emu geo fix` 로 보낸 뒤 앱 로그를 보여 줍니다.
앱은 5초마다 `상태:` 로그(위치, 속도, 방향, 가장 가까운 시설물까지 거리)를 남깁니다.

에뮬레이터 `geo fix` 는 속도 0·방향 0(북쪽)을 채워 보내기 때문에, 앱은 방향을 이동 경로로 계산하고 시속 250km를 넘는 위치 점프는 무시합니다.

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
demo/       시연 GPX, 재생·빌드 스크립트
```

지도 데이터 © OpenStreetMap contributors (ODbL).
