# Edits video\01_..09_*.webm into video\demo_full.mp4 (caption + fade per scene, 1080p, h264/aac). ASCII only.
# Usage: powershell -ExecutionPolicy Bypass -File demo\edit_video.ps1
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$video = Join-Path $root "video"
$ff = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter ffmpeg.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (-not $ff) { $ff = (Get-Command ffmpeg -ErrorAction Stop).Source }
$fp = Join-Path (Split-Path $ff) "ffprobe.exe"

$work = Join-Path $video "edit"
New-Item -ItemType Directory -Force $work | Out-Null
Set-Location $work   # relative paths in the filter (drawtext textfile) avoid colon escaping


# captions.txt: one line per scene, "title|one-line description"
$caps = @(Get-Content -Encoding UTF8 (Join-Path $PSScriptRoot "captions.txt") | Where-Object { $_.Trim() })
$files = Get-ChildItem $video -Filter "0*.webm" | Sort-Object Name
$utf8 = New-Object Text.UTF8Encoding $false
$bold = "fontfile='C\:/Windows/Fonts/malgunbd.ttf'"
$reg = "fontfile='C\:/Windows/Fonts/malgun.ttf'"
$enc = @("-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-r", "30", "-video_track_timescale", "15360",
         "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-ac", "2")
$CARD = 3.0
$list = @()
$start = 0.0
$rows = @()
for ($i = 0; $i -lt $files.Count; $i++) {
    $n = $i + 1
    $parts = $caps[$i] -split '\|', 2
    $title = $parts[0].Trim(); $desc = if ($parts.Count -gt 1) { $parts[1].Trim() } else { "" }
    [IO.File]::WriteAllText((Join-Path $work "t$n.txt"), $title, $utf8)
    [IO.File]::WriteAllText((Join-Path $work "d$n.txt"), $desc, $utf8)
    [IO.File]::WriteAllText((Join-Path $work "n$n.txt"), ("{0:D2} / {1:D2}" -f $n, $files.Count), $utf8)

    # 1) title card before the scene: number, title, one-line description (purple theme colors), silent audio
    $cardFile = "c$n.mp4"
    $cvf = "drawtext=${bold}:textfile=n$n.txt:fontcolor=0xB58CFF:fontsize=40:x=(w-tw)/2:y=h/2-190," +
           "drawtext=${bold}:textfile=t$n.txt:fontcolor=white:fontsize=84:x=(w-tw)/2:y=h/2-110," +
           "drawbox=x=(iw-160)/2:y=ih/2+10:w=160:h=4:color=0xB58CFF:t=fill," +
           "drawtext=${reg}:textfile=d$n.txt:fontcolor=0xD9CCF5:fontsize=40:x=(w-tw)/2:y=h/2+50," +
           "fade=t=in:st=0:d=0.4,fade=t=out:st=$($CARD - 0.4):d=0.4"
    & $ff -y -v error -f lavfi -i "color=c=0x110922:s=1920x1080:r=30:d=$CARD" -f lavfi -i "anullsrc=r=48000:cl=stereo" -vf $cvf @enc -t $CARD $cardFile
    if ($LASTEXITCODE -ne 0) { throw "card failed on scene $n" }

    # 2) the scene itself, with the title as a large caption at the bottom center for the first seconds
    $src = $files[$i].FullName
    $dur = [double](& $fp -v error -show_entries format=duration -of csv=p=0 $src)
    # scene 1: the first ~3.5 s is the navi screen before our app opens -> cut it
    $trim = 0.0; if ($i -eq 0) { $trim = 3.5 }
    $dur = $dur - $trim
    $out = "s$n.mp4"
    $fadeOut = [Math]::Max(0, $dur - 0.4)
    $vf = "scale=1920:1080:flags=lanczos,fps=30," +
          "drawtext=${bold}:textfile=t$n.txt:fontcolor=white:fontsize=60:box=1:boxcolor=0x110922@0.82:boxborderw=26:x=(w-tw)/2:y=h-th-90:enable='between(t,0.3,5.5)'," +
          "fade=t=in:st=0:d=0.4,fade=t=out:st=${fadeOut}:d=0.4"
    # the emulator recorder puts a burst of noise into the first ~2.2 s of every recording: mute it
    # scenes 2-8: no real sound can start before 3.3 s (speech starts at 3.5 s or later); scenes 1 and 9: only the first 2.3 s
    $mute = if ($i -ge 1 -and $i -le 7) { 3.3 } else { 2.3 }
    # aresample=async: the emulator audio track has gaps (no packets while silent); fill them with silence by timestamp,
    # otherwise the gaps collapse and every sound moves earlier (that put speech into the wrong scenes)
    $af = "aresample=async=1:first_pts=0,apad,volume=enable='lt(t,$mute)':volume=0,afade=t=in:st=${mute}:d=0.2,afade=t=out:st=$([Math]::Max(0, $dur - 0.4)):d=0.4"
    & $ff -y -v error -ss $trim -i $src -vf $vf -af $af @enc -t $dur $out
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed on scene $n" }

    $list += "file '$cardFile'"
    $list += "file '$out'"
    $rows += [pscustomobject]@{ Scene = $files[$i].Name; Length = [Math]::Round($dur, 1); Start = $start + $CARD }
    $start += $CARD + $dur
}
Set-Content -Encoding ASCII (Join-Path $work "list.txt") $list
& $ff -y -v error -f concat -safe 0 -i list.txt -c copy (Join-Path $video "demo_full.mp4")
if ($LASTEXITCODE -ne 0) { throw "concat failed" }
$total = [double](& $fp -v error -show_entries format=duration -of csv=p=0 (Join-Path $video "demo_full.mp4"))
"(each scene is preceded by a {0}s title card; 'start' = when the scene itself begins)" -f $CARD
$rows | ForEach-Object { "{0,-28} {1,5}s  start {2}" -f $_.Scene, $_.Length, ([TimeSpan]::FromSeconds($_.Start).ToString("mm\:ss\.f")) }
"TOTAL {0:N1}s" -f $total