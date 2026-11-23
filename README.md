# 지정차로 안내 (LaneGuide)

**차종에 맞는 고속도로 지정차로를 크루즈·방향지시등 신호에 맞춰 알려주는 Pleos Connect 앱**

화면은 내비를 왼쪽, 이 앱을 오른쪽에 두는 분할 배치를 전제로 하고, 전체 화면에서는 가로 배치로 자동 전환됩니다.

## 다운로드
- **APK (v0.1.0)**: [GitHub Release v0.1.0](https://github.com/je-love-jh/laneGuide/releases/tag/v0.1.0) → `LaneGuide-v0.1.0-debug.apk`
- 개발용(debug) 빌드입니다. 에뮬레이터에서는 차대번호가 `invalid` 로 나오기 때문에, 개발용 빌드에서만 시연용 차대번호(KNC…, 소형 화물)로 프로필을 추천합니다.

## 테스트 환경
| 항목 | 값 |
|---|---|
| 에뮬레이터 | **Pleos Connect v2.1.0** (Android Automotive OS, Android 14 / **API 34**) |
| 앱 최소/대상 | minSdk 29 / targetSdk·compileSdk 34 |
| Java | **JDK 17~21** (확인한 것은 JDK 21. Android Studio 내장 JDK 가 25 라면 Gradle 8.7 과 맞지 않아 JDK 21 을 따로 지정) |
| 빌드 도구 | Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin 1.9.24 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1 (Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/`) |
| 운영체제 | Windows 11 (PowerShell 스크립트 사용) |

## 실행 방법

### 1) 설치
**APK로 설치 (빌드 없이)** — 에뮬레이터를 켠 뒤:
```
adb install -r -g LaneGuide-v0.1.0-debug.apk
```
`-g` 는 앱이 요구하는 권한(위치, 차량 속도 등)을 설치하면서 미리 허용합니다.

**소스에서 빌드** — Android Studio 에서 이 폴더를 열고 Gradle Sync 후 에뮬레이터를 고르고 **Run(▶)**, 또는 터미널에서:
```
powershell -ExecutionPolicy Bypass -File demo\build_install.ps1
```
(`gradlew installDebug` 로 빌드해 에뮬레이터에 설치합니다.)

### 2) 처음 실행
- 처음에는 **설정 화면**이 열리고, 차대번호로 추천한 프로필(소형 화물)이 골라져 있습니다. 차 높이·**화면 테마**(블랙 / 화이트 / 블루 / 퍼플)를 고르고 **저장**을 누르세요.
- 권한 창이 뜨면 허용합니다. 가상 콘솔 기어가 **D(주행)이면 권한 창이 막히니 P(주차)에서** 실행하세요. 안 되면 `adb shell pm grant --user 10 com.laneguide.app android.car.permission.CAR_SPEED`.
- 주행 중(기어 D)에는 설정이 잠깁니다.

### 3) 시연 주행
1. 화면의 **[시연 주행: 고속도로]** 또는 **[시연 주행: 굴다리]** 를 누릅니다. **정차 중(차량 속도 0)에만** 켜집니다.
2. 가상 콘솔을 엽니다: `adb forward tcp:8088 tcp:8088` 후 브라우저로 http://127.0.0.1:8088/ → Emulation Controls.
3. 기어를 D로 두고 **ACCEL(엑셀)** 을 누르면, GPS 대신 **차량 속도만큼 실제 도로 경로(GPX)를 따라 달리며** 안내가 나옵니다. 콘솔 창은 화면에 보이는 상태로 두세요(가려지면 속도가 오르지 않습니다).
4. 경로 끝에 닿으면 자동으로 끝나고, 중간에는 파란 줄의 **[종료]** 로 실제 GPS로 돌아옵니다.

굴다리 경고를 보려면 프로필 소형 화물, 차 높이 **2.8m 이상**(예: 짐 실은 3.0m)으로 저장하세요. 2.7m 이하는 지나갈 수 있어 경고 없이 정보만 보입니다.

### 4) 크루즈·방향지시등 (가상 콘솔)
| 콘솔 | 앱 동작 |
|---|---|
| ADAS > Cruise Control State > Ready → **Full autonomous** | 크루즈 작동 감지 → 지금 구간 지정차로 음성 안내 + 화면 표시 |
| Convenience > Turn Signal State > **Blink left** | 화면에 "추월 후 우측 지정차로로 복귀하세요" |

Longitudinal·Lateral 은 대부분 크루즈 작동(ACTIVATED)으로 오지 않아 안내가 나오지 않습니다. 시연할 때는 Ready → Full autonomous 로 누릅니다.

## 동작하는 것
- **고속도로 지정차로**: 구간에 들어서면 원근 차로판(빨강 ✕ 진입 금지 / 노랑 꺾인 화살표 추월 시만 / **파랑 ↑ 주행**)과 음성 안내. 차로 수가 바뀌면(편도 4 → 5차로) 다시 안내합니다.
- **크루즈 작동 순간** 현재 구간 지정차로 안내, **왼쪽 방향지시등**에 우측 지정차로 복귀 안내 (Pleos Vehicle SDK)
- **낮은 굴다리·지하차도**: 진행 방향 앞쪽에 내 차(높이 + 0.1m 여유)보다 낮은 시설물이 있으면 약 250m 앞에서 **큰 경고 카드 + 화면 가장자리 붉은 빛 + 음성**. 지나갈 수 있는 높이면 소리 없이 정보 카드만 보입니다. ([DESIGN_WARN.md](DESIGN_WARN.md))
- **차대번호(VIN) 앞 3자리로 프로필 추천** → 운전자가 저장으로 한 번 확인
- 차종 프로필(승용 / 소형 화물 / 중대형 화물)과 차 높이 저장, 주행 중(CarUxRestrictions NO_SETUP) 설정 잠금
- 화면 테마 4종(블랙 기본 / 화이트 / 블루 HUD / 퍼플 HUD). 차로 색의 뜻은 모든 테마에서 같습니다. ([DESIGN.md](DESIGN.md))
- 창 크기에 따라 **세로(분할 화면) / 가로(전체 화면) 배치 자동 전환**, 전환해도 안내 상태 유지 ([DESIGN_WIDE.md](DESIGN_WIDE.md))
- **Pleos TextToSpeech SDK** (ON_DEVICE, ko-KR) 음성 안내. 오디오 포커스를 받아 다른 소리를 잠시 줄이고, 안내가 겹치면 차례로 말합니다.
- **차량 속도**: Pleos Vehicle SDK 에 없어서 안드로이드 `CarPropertyManager`(PERF_VEHICLE_SPEED, 권한 CAR_SPEED)로 읽습니다.

### 아직 안 되는 것 / 제한
- **[우회 경로로 변경] 버튼은 지금 숨겨져 있습니다.** Pleos NaviHelper SDK 로 운전자가 누를 때만 우회 지점을 내비 경유지로 요청하는 코드는 들어 있지만(자동 경로 변경 없음), 데이터의 우회 지점이 실제로 굴다리를 피하는지 검증되지 않아 `MainActivity.SHOW_DETOUR = false` 로 꺼 두었습니다.
- **화물차 통행제한 구역 경고**는 판단 코드(`engine/AlertEngine.kt`)만 있고 데이터는 비어 있습니다(`zones: []`). 실제 공공데이터를 넣을 예정입니다.
- 도로 데이터는 시연 구간(경부고속도로 용인 구간, 죽전통로 굴다리)만 있습니다.
- 실차에서는 시험하지 못했습니다(에뮬레이터에서만 확인).

## 시연 경로 (실제 도로, OpenStreetMap 기준)
| 파일 | 내용 | 맞출 설정 |
|---|---|---|
| `app/src/main/assets/demo_drive.gpx` | 경부고속도로 부산방향 용인 구간 6.6km, 편도 4 → 5 → 4차로 | 소형 화물 |
| `app/src/main/assets/demo_underpass.gpx` | 용인 죽전 **죽전통로 굴다리(높이 제한 2.8m)** 접근 452m | 소형 화물, 높이 **2.8m 이상** |

## 스크립트 (`demo/`, PowerShell)
`powershell -ExecutionPolicy Bypass -File demo\<파일>` 로 실행합니다.

| 파일 | 하는 일 |
|---|---|
| `build_install.ps1` | 디버그 빌드 후 에뮬레이터에 설치 |
| `play_gpx.ps1 -Gpx demo_underpass.gpx -Height 3.0` | 앱을 해당 프로필·높이로 다시 시작하고 GPX 를 1초 간격 `adb emu geo fix` 로 보낸 뒤 앱 로그 출력 |
| `scene1_reset.ps1` ~ `scene5_cruise.ps1` | 시연 장면별 실행 (첫 실행 초기화, 고속도로, 굴다리 2.0m / 3.0m, 크루즈·방향지시등) |
| `record_scenes.ps1 -Scene 1..9` | 시연 영상 장면을 자동으로 실행하며 에뮬레이터 화면·소리 녹화 (`-NoRecord` 면 실행만) |
| `edit_video.ps1` | 녹화본을 장면 설명 화면·자막과 함께 이어 붙여 `video/demo_full.mp4` 로 편집 (ffmpeg 필요) |
| `theme_compare.ps1` | 같은 주행 화면을 테마 4종으로 차례로 띄움 (화면 비교용) |

## 데이터
`app/src/main/assets/golden_dataset.json` (네트워크 없이 동작)
- 고속도로: 경부고속도로 부산방향 용인 구간, 차로 수는 OSM 기준
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
  speech/   음성 안내 (Pleos TextToSpeech)
  car/      차량 속도, 주행 중 제한 감시
  demo/     시연 주행 경로
  navi/     Pleos NaviHelper (경유지 요청)
  pleos/    Pleos Vehicle SDK (크루즈·방향지시등, 차대번호)
  ui/       메인 화면, 차로판, 경고 카드, 테마
demo/       시연·녹화·빌드 스크립트 (시연 GPX 는 app/src/main/assets)
```

지도 데이터 © OpenStreetMap contributors (ODbL).

