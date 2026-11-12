# 디버그 빌드를 만들어 에뮬레이터에 설치한다 (Android Studio 없이 터미널에서).
# 사용 예) powershell -ExecutionPolicy Bypass -File demo\build_install.ps1
# Gradle 8.7 은 Java 17~21 이 필요하다. Android Studio 내장 JBR 이 25 라면 JDK 21 을 따로 쓴다.
$jdk21 = Join-Path $env:USERPROFILE ".jdks\jbr-21.0.11"
if (Test-Path $jdk21) { $env:JAVA_HOME = $jdk21 }
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not (Test-Path ".\gradlew.bat")) {
    # 처음 한 번: 캐시에 있는 Gradle 8.7 로 gradlew(래퍼)를 만든다
    $gradle = Get-ChildItem (Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-8.7-bin") -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
    & $gradle wrapper --gradle-version 8.7 --console=plain
}
& .\gradlew.bat installDebug --console=plain
Write-Host "BUILD_EXIT=$LASTEXITCODE"
