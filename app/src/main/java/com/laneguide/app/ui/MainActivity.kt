package com.laneguide.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.laneguide.app.R
import com.laneguide.app.car.DrivingRestrictionWatcher
import com.laneguide.app.data.ProfileStore
import com.laneguide.app.engine.AlertService
import com.laneguide.app.demo.DemoRouteId
import com.laneguide.app.engine.AppState
import com.laneguide.app.engine.DemoCommand
import com.laneguide.app.engine.UiState
import com.laneguide.app.model.VehicleProfile
import com.laneguide.app.pleos.VinReader
import kotlin.math.roundToInt

/** 내비 옆 창에 띄우는 메인 화면: 구간 요약, 차로 그림, 최근 경고, (정차 중) 설정 */
class MainActivity : Activity() {

    private lateinit var store: ProfileStore
    private lateinit var restriction: DrivingRestrictionWatcher

    private lateinit var root: GridBackground
    private lateinit var stage: StageFrame
    private lateinit var mainContent: ViewGroup
    private lateinit var roadName: TextView
    private lateinit var laneTitle: TextView
    private lateinit var speedValue: TextView
    private lateinit var speedUnit: TextView
    private lateinit var laneSummary: TextView
    private lateinit var laneBoard: LaneBoardView
    private lateinit var heightCard: HeightCardView
    private lateinit var redVignette: RedVignetteView
    private lateinit var theme: UiTheme
    private val density get() = resources.displayMetrics.density
    private lateinit var alertText: TextView
    private lateinit var detourButton: Button
    private lateinit var demoBanner: ViewGroup
    private lateinit var demoBannerText: TextView
    private lateinit var demoHintText: TextView
    private lateinit var demoBar: ViewGroup
    private lateinit var demoNote: TextView
    private lateinit var btnDemoHighway: Button
    private lateinit var btnDemoUnderpass: Button
    private lateinit var btnDemoStop: Button
    private lateinit var lockText: TextView
    private lateinit var settingsPanel: ViewGroup
    private lateinit var heightValue: TextView
    private lateinit var vinHint: TextView
    private lateinit var profileBar: ViewGroup
    private lateinit var profileSummary: TextView
    private var settingsOpen = false
    private lateinit var profileButtons: Map<VehicleProfile, Button>

    // 저장 버튼을 누르기 전까지 고르고 있는 값
    private var pendingProfile = VehicleProfile.LIGHT_TRUCK
    private var pendingHeightDm = 20 // 0.1m 단위 (20 = 2.0m)
    private var readVin: String? = null

    private val observer: (UiState) -> Unit = { render(it) }

    /** 넓은 창(전체 화면)이라 가로 배치를 쓰는가. attachBaseContext 에서 창 크기로 정한다. */
    private var wide = false

    /**
     * 창 크기로 배치를 고른다 (DESIGN_WIDE.md): 가로 ≥ 세로 × 1.3 이면 가로 배치(기준 1600×900), 아니면 세로 배치(기준 640×900).
     * 그리고 그 기준 크기가 창에 꽉 차는 배율(k)로 화면 밀도(dp·sp)를 키운다 → 레이아웃의 dp 1칸 = 기준 크기 1단위,
     * 그래서 글자·아이콘·여백이 모두 창 크기에 비례한다. 창 크기가 바뀌면 액티비티가 다시 만들어지면서 이 계산을 다시 한다.
     */
    override fun attachBaseContext(newBase: Context) {
        val cfg = android.content.res.Configuration(newBase.resources.configuration)
        val physicalScale = cfg.densityDpi / 160f
        val wDp = cfg.screenWidthDp
        val hDp = cfg.screenHeightDp
        val wPx = wDp * physicalScale
        val hPx = hDp * physicalScale
        if (wPx > 0f && hPx > 0f) {
            wide = wPx >= hPx * WIDE_RATIO
            val baseW = if (wide) WIDE_W else DESIGN_W
            val k = minOf(wPx / baseW, hPx / DESIGN_H).coerceIn(0.4f, 3f)
            android.util.Log.i(
                "UiScale",
                "실제 창 ${wDp}x${hDp}dp (기기 밀도 ${cfg.densityDpi}dpi) 가로/세로=${"%.2f".format(wPx / hPx)} " +
                    "→ ${if (wide) "가로" else "세로"} 배치 (기준 ${WIDE_RATIO} 이상이면 가로), 배율 ${"%.2f".format(k)}, " +
                    "화면 단위 ${(wPx / k).roundToInt()}x${(hPx / k).roundToInt()}",
            )
            cfg.densityDpi = (160f * k).roundToInt()
            cfg.fontScale = 1f
            cfg.screenWidthDp = (wPx / k).toInt()
            cfg.screenHeightDp = (hPx / k).toInt()
            super.attachBaseContext(newBase.createConfigurationContext(cfg))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    /** 창 크기가 바뀌어(분할↔전체) 액티비티가 다시 만들어질 때, 화면에 있던 설정 상태를 넘겨 준다. 구간·안내는 AppState 에 있어 그대로다. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("settingsOpen", settingsOpen)
        outState.putString("pendingProfile", pendingProfile.name)
        outState.putInt("pendingHeightDm", pendingHeightDm)
        outState.putString("pendingTheme", pendingThemeId)
        outState.putString("readVin", readVin)
        outState.putBoolean("vinHintVisible", vinHint.visibility == View.VISIBLE)
        outState.putCharSequence("vinHintText", vinHint.text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(if (wide) R.layout.activity_main_wide else R.layout.activity_main)
        store = ProfileStore(this)
        theme = UiTheme.byId(store.themeId)

        root = findViewById(R.id.root)
        stage = findViewById(R.id.stage)
        root.wide = wide
        stage.wide = wide
        mainContent = findViewById(R.id.mainContent)
        roadName = findViewById(R.id.roadName)
        laneTitle = findViewById(R.id.laneTitle)
        speedValue = findViewById(R.id.speedValue)
        speedUnit = findViewById(R.id.speedUnit)
        laneSummary = findViewById(R.id.laneSummary)
        laneBoard = findViewById(R.id.laneBoard)
        heightCard = findViewById(R.id.heightCard)
        heightCard.wide = wide
        redVignette = findViewById(R.id.redVignette)
        alertText = findViewById(R.id.alertText)
        alertBasePx = alertText.textSize
        // 창 크기가 바뀌어 상자 폭이 달라지면 글자 크기를 다시 맞춘다
        alertText.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
            val w = r - l
            if (w != alertFitWidth) { alertFitWidth = w; v.post { fitAlertText() } }
        }
        detourButton = findViewById(R.id.detourButton)
        demoBanner = findViewById(R.id.demoBanner)
        demoBannerText = findViewById(R.id.demoBannerText)
        demoHintText = findViewById(R.id.demoHintText)
        demoBar = findViewById(R.id.demoBar)
        demoNote = findViewById(R.id.demoNote)
        btnDemoHighway = findViewById(R.id.btnDemoHighway)
        btnDemoUnderpass = findViewById(R.id.btnDemoUnderpass)
        btnDemoStop = findViewById(R.id.btnDemoStop)
        btnDemoHighway.setOnClickListener { AppState.onDemoCommand?.invoke(DemoCommand.Start(DemoRouteId.HIGHWAY)) }
        btnDemoUnderpass.setOnClickListener { AppState.onDemoCommand?.invoke(DemoCommand.Start(DemoRouteId.UNDERPASS)) }
        // [종료]는 주행 중에도 항상 눌러서 실제 GPS로 돌아올 수 있어야 한다
        btnDemoStop.setOnClickListener { AppState.onDemoCommand?.invoke(DemoCommand.Stop) }
        lockText = findViewById(R.id.lockText)
        settingsPanel = findViewById(R.id.settingsPanel)
        heightValue = findViewById(R.id.heightValue)
        vinHint = findViewById(R.id.vinHint)
        profileBar = findViewById(R.id.profileBar)
        profileSummary = findViewById(R.id.profileSummary)
        profileButtons = mapOf(
            VehicleProfile.PASSENGER to findViewById(R.id.btnPassenger),
            VehicleProfile.LIGHT_TRUCK to findViewById(R.id.btnLightTruck),
            VehicleProfile.HEAVY_TRUCK to findViewById(R.id.btnHeavyTruck),
        )

        applyTestExtras()
        setupSettings()
        // 분할↔전체로 다시 만들어진 경우: 고르던 설정·열려 있던 설정 화면을 되살린다
        savedInstanceState?.let { b ->
            runCatching { VehicleProfile.valueOf(b.getString("pendingProfile") ?: "") }.getOrNull()?.let { pendingProfile = it }
            pendingHeightDm = b.getInt("pendingHeightDm", pendingHeightDm)
            pendingThemeId = b.getString("pendingTheme") ?: pendingThemeId
            readVin = b.getString("readVin")
            if (b.getBoolean("vinHintVisible")) {
                vinHint.visibility = View.VISIBLE
                vinHint.text = b.getCharSequence("vinHintText")
            }
            showSettings(b.getBoolean("settingsOpen"))
        }
        registerPreviewReceiver()
        applyTheme(UiTheme.byId(pendingThemeId))

        restriction = DrivingRestrictionWatcher(this) { locked -> runOnUiThread { setSettingsLocked(locked) } }
        restriction.start()

        startServiceIfPermitted(recommend = savedInstanceState == null)
    }

    override fun onStart() {
        super.onStart()
        AppState.observe(observer)
    }

    override fun onStop() {
        AppState.remove(observer)
        super.onStop()
    }

    /** 테마(배경·장식 색)를 화면 전체에 적용한다. 차로 색의 뜻은 테마와 상관없이 같다. */
    private fun applyTheme(newTheme: UiTheme) {
        theme = newTheme
        root.setTheme(theme)
        stage.setTheme(theme)
        laneBoard.setTheme(theme)
        heightCard.setTheme(theme)
        redVignette.setTheme(theme)

        // 눈금자는 세로 배치의 HUD 머리에만 있다 (가로 시안에는 없음)
        findViewById<HudRulerView?>(R.id.hudRuler)?.setTheme(theme)
        if (wide) {
            laneTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 84f)
            speedValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 76f)
        } else {
            // HUD 시안은 세로 배치 머리 글자가 조금 더 크다 (46 / 42)
            laneTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (theme.hud) 46f else 44f)
            speedValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (theme.hud) 42f else 40f)
        }
        roadName.setTextColor(theme.textSub)
        laneTitle.setTextColor(theme.text)
        speedValue.setTextColor(theme.text)
        speedUnit.setTextColor(theme.textSub)
        laneSummary.setTextColor(theme.summary)
        lastAlertKind = null // 안내 상자 색을 다시 칠하도록
        profileSummary.setTextColor(if (theme.hud) theme.textSub else theme.infoText)
        demoNote.setTextColor(theme.textSub)
        lockText.setTextColor(theme.textSub)

        // 일반 버튼
        listOf(
            findViewById<Button>(R.id.btnEdit), btnDemoHighway, btnDemoUnderpass,
            findViewById(R.id.btnHeightDown), findViewById(R.id.btnHeightUp),
        ).forEach { styleButton(it) }
        profileButtons.values.forEach { it.setTextColor(theme.text); it.stateListAnimator = null; it.isAllCaps = false }
        // 강조 버튼: 우회 제안·저장
        listOf(detourButton, findViewById<Button>(R.id.btnSave)).forEach {
            it.background = Panels.primaryButton(theme, density)
            it.setTextColor(Color.parseColor("#04121C"))
            it.stateListAnimator = null; it.isAllCaps = false
        }
        // 설정 화면 글자
        findViewById<TextView>(R.id.settingsTitle).setTextColor(theme.text)
        findViewById<TextView>(R.id.settingsSub).setTextColor(theme.textSub)
        findViewById<TextView>(R.id.profileLabel).setTextColor(theme.text)
        findViewById<TextView>(R.id.heightLabel).setTextColor(theme.text)
        heightValue.setTextColor(theme.text)
        refreshSettings()
        // 현재 상태로 다시 그린다
        render(AppState.current)
    }

    private fun styleButton(b: Button) {
        b.background = Panels.button(theme, density)
        b.setTextColor(theme.text)
        b.stateListAnimator = null
        b.isAllCaps = false
    }

    override fun onDestroy() {
        previewReceiver?.let { runCatching { unregisterReceiver(it) } }
        restriction.stop()
        super.onDestroy()
    }

    // ---------- 화면 확인용 미리보기 (개발용 빌드 전용) ----------

    /** 값이 있으면 실제 상태 대신 이 상태를 그린다. 화면 디자인 확인용이라 개발용 빌드에서만 쓴다. */
    private var preview: UiState? = null
    private var previewReceiver: android.content.BroadcastReceiver? = null

    /**
     * adb shell am broadcast -a com.laneguide.app.DEBUG_UI -p com.laneguide.app --es lanes 5 --es profile T --es speed 90 --es road 경부고속도로_부산방향 --es alert 문구_밑줄은_공백
     * 해제: --es clear 1
     */
    private fun registerPreviewReceiver() {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getStringExtra("clear") != null) { preview = null; render(AppState.current); return }
                // 테마 비교용: 항상 같은 장면(고속도로 4차로·소형 화물·90km/h·같은 문구)을 고른 테마로 띄운다. 저장값은 안 바꾼다.
                val compare = i.getStringExtra("compare")
                // 높이 경고/통과 미리보기: --es warn 250,2.8,3.0 (거리,제한,내 차) / --es pass 250,2.8,2.0
                val hsSpec = i.getStringExtra("warn") ?: i.getStringExtra("pass")
                val hsBlocked = i.getStringExtra("warn") != null
                val hs = hsSpec?.split(',')?.let { p ->
                    if (p.size < 3) null else com.laneguide.app.engine.HeightStatus(
                        facilityName = "죽전통로 굴다리",
                        limitM = p[1].toDoubleOrNull() ?: 2.8,
                        vehicleM = p[2].toDoubleOrNull() ?: 3.0,
                        distanceM = p[0].toIntOrNull() ?: 250,
                        blocked = hsBlocked,
                    )
                }
                val n = if (compare != null) 4 else i.getStringExtra("lanes")?.toIntOrNull() ?: if (hs != null) 0 else 4
                val truck = compare != null || i.getStringExtra("profile") != "P"
                val profile = if (truck) VehicleProfile.LIGHT_TRUCK else VehicleProfile.PASSENGER
                val road = i.getStringExtra("road")?.replace('_', ' ') ?: "경부고속도로 부산방향"
                val speed = if (compare != null) 90 else i.getStringExtra("speed")?.toIntOrNull() ?: 92
                val defaultAlert = when {
                    compare != null -> "화물차 지정차로는 3, 4차로입니다. 2차로는 추월차로입니다."
                    hs != null && hs.blocked -> "전방 약 ${(hs.distanceM / 50) * 50}미터에 높이 제한 ${"%.1f".format(hs.limitM)}미터 시설물이 있습니다."
                    else -> null
                }
                preview = UiState(
                    highwayName = if (n > 0) road else null,
                    laneSummary = if (n > 0) com.laneguide.app.rules.LaneRules.summary(n, profile) else "고속도로 구간이 아닙니다",
                    lanes = if (n > 0) com.laneguide.app.rules.LaneRules.laneStates(n, profile) else emptyList(),
                    lastAlert = i.getStringExtra("alert")?.replace('_', ' ') ?: defaultAlert,
                    speedKmh = speed,
                    gpsReady = true,
                    vehicleSpeedKmh = speed,
                    heightStatus = hs,
                )
                val themeId = compare ?: i.getStringExtra("theme")
                themeId?.let {
                    if (compare == null) { store.themeId = it; pendingThemeId = it }
                    applyTheme(UiTheme.byId(it))
                }
                // 미리보기는 기본이 주행 화면이다(설정이 열려 있으면 접는다). 설정을 보려면 --es settings 1
                showSettings(i.getStringExtra("settings") != null)
                render(AppState.current)
            }
        }
        val filter = android.content.IntentFilter("com.laneguide.app.DEBUG_UI")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, filter, Context.RECEIVER_EXPORTED) else registerReceiver(r, filter)
        previewReceiver = r
    }

    // ---------- 테스트 ----------

    /**
     * 개발용 빌드에서만: adb 로 프로필·높이를 넘겨 자동 테스트할 수 있게 한다.
     * 예) adb shell am start -n com.laneguide.app/.ui.MainActivity --es profile LIGHT_TRUCK --ef height 3.0
     */
    private fun applyTestExtras() {
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        intent?.getStringExtra("profile")?.let { name ->
            runCatching { VehicleProfile.valueOf(name) }.getOrNull()?.let { store.profile = it }
        }
        if (intent?.hasExtra("height") == true) {
            store.heightM = intent.getFloatExtra("height", store.heightM.toFloat()).toDouble().coerceIn(1.0, 4.5)
        }
        android.util.Log.i("AlertService", "설정: ${store.profile.label}, 높이 ${store.heightM}m")
    }

    // ---------- 설정 ----------

    // ---------- 화면 테마 고르기 ----------

    private var pendingThemeId = "BLACK"
    private val themeButtons = mutableMapOf<String, android.widget.LinearLayout>()
    private val themeSwatches = mutableMapOf<String, View>()

    /** 설정 화면에 테마 견본 버튼 4개를 만든다. 누르면 바로 화면에 미리 적용하고, [저장] 때 기기에 저장한다. */
    private fun buildThemeButtons() {
        val row = findViewById<android.widget.LinearLayout>(R.id.themeRow)
        UiTheme.ALL.forEachIndexed { i, t ->
            val box = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "화면 테마 ${t.label}"
                layoutParams = android.widget.LinearLayout.LayoutParams(0, (112 * density).toInt(), 1f).apply {
                    if (i > 0) marginStart = (12 * density).toInt()
                }
            }
            val swatch = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams((64 * density).toInt(), (36 * density).toInt())
            }
            val name = TextView(this).apply {
                text = t.label
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                setPadding(0, (10 * density).toInt(), 0, 0)
            }
            box.addView(swatch)
            box.addView(name)
            box.setOnClickListener {
                pendingThemeId = t.id
                applyTheme(t) // 눌러 보면 바로 화면에 반영
            }
            row.addView(box)
            themeButtons[t.id] = box
            themeSwatches[t.id] = swatch
        }
    }

    /** 견본 색(배경·테두리)과 선택 표시(굵은 accent 테두리)를 현재 테마에 맞춰 다시 칠한다. */
    private fun refreshThemeButtons() {
        UiTheme.ALL.forEach { t ->
            val selected = t.id == pendingThemeId
            themeButtons[t.id]?.background = Panels.button(theme, density, selected = selected, choice = true)
            (themeButtons[t.id]?.getChildAt(1) as? TextView)?.setTextColor(theme.text)
            themeButtons[t.id]?.isSelected = selected
            val swatchBorder = when (t.id) {
                "BLACK" -> Color.parseColor("#5B6675")
                "WHITE" -> Color.parseColor("#AEB7C2")
                "BLUE" -> Color.parseColor("#4CC9FF")
                else -> Color.parseColor("#B58CFF")
            }
            val swatchFill = when (t.id) {
                "BLUE" -> Color.parseColor("#0A1F44")
                "PURPLE" -> Color.parseColor("#22124A")
                else -> t.bg
            }
            themeSwatches[t.id]?.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(swatchFill)
                setStroke((2 * density).toInt().coerceAtLeast(1), swatchBorder)
                cornerRadius = 8f * density
            }
        }
        findViewById<TextView>(R.id.themeLabel).setTextColor(theme.text)
        findViewById<TextView>(R.id.themeNote).setTextColor(theme.textSub)
    }

    private fun setupSettings() {
        pendingProfile = store.profile
        pendingHeightDm = (store.heightM * 10).roundToInt()
        pendingThemeId = store.themeId
        buildThemeButtons()
        profileButtons.forEach { (profile, button) ->
            button.setOnClickListener { pendingProfile = profile; refreshSettings() }
        }
        findViewById<Button>(R.id.btnHeightDown).setOnClickListener {
            pendingHeightDm = (pendingHeightDm - 1).coerceAtLeast(MIN_HEIGHT_DM); refreshSettings()
        }
        findViewById<Button>(R.id.btnHeightUp).setOnClickListener {
            pendingHeightDm = (pendingHeightDm + 1).coerceAtMost(MAX_HEIGHT_DM); refreshSettings()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            store.profile = pendingProfile
            store.heightM = pendingHeightDm / 10.0
            store.themeId = pendingThemeId
            readVin?.let { store.confirmedVin = it }
            vinHint.visibility = View.GONE
            showSettings(false)
            Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnEdit).setOnClickListener { showSettings(true) }
        refreshSettings()
        // 이미 한 번 저장한 적이 있으면 접힌 상태로 시작
        showSettings(store.confirmedVin == null && !store.hasSaved)
    }

    /** 설정 화면을 열거나(true) 주행 화면으로 돌아간다(false). 주행 화면 맨 아래에는 프로필 한 줄과 [변경] 이 있다. */
    private fun showSettings(open: Boolean) {
        settingsOpen = open
        settingsPanel.visibility = if (open) View.VISIBLE else View.GONE
        mainContent.visibility = if (open) View.GONE else View.VISIBLE
        profileSummary.text = String.format("%s · 높이 %.1f m", store.profile.label, store.heightM)
    }

    private fun refreshSettings() {
        heightValue.text = String.format("%.1f m", pendingHeightDm / 10.0)
        profileButtons.forEach { (profile, button) ->
            button.background = Panels.button(theme, density, selected = profile == pendingProfile, choice = true)
        }
        refreshThemeButtons()
    }

    private fun setSettingsLocked(locked: Boolean) {
        lockText.visibility = if (locked) View.VISIBLE else View.GONE
        setEnabledRecursive(settingsPanel, !locked)
        setEnabledRecursive(profileBar, !locked)
    }

    private fun setEnabledRecursive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (i in 0 until view.childCount) setEnabledRecursive(view.getChildAt(i), enabled)
    }

    // ---------- 차대번호 추천 ----------

    /** 차대번호를 읽어, 아직 확인하지 않은 차라면 프로필을 추천해 미리 골라 둔다. */
    private fun recommendFromVin() {
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        VinReader(this).read { realVin ->
            // 개발용 빌드(에뮬레이터 시연)에서만, 차대번호가 없으면 시연용 값을 쓴다.
            val demo = realVin == null && debuggable
            val vin = realVin ?: if (demo) VinReader.DEMO_VIN else null
            android.util.Log.d("VinReader", "추천 처리: vin=${vin?.take(3)}… 시연용=$demo 확인된 차=${store.confirmedVin?.take(3)}")
            readVin = vin
            if (vin == null) {
                if (!settingsOpen) return@read // 이미 저장한 상태면 굳이 다시 펼치지 않는다
                vinHint.visibility = View.VISIBLE
                vinHint.text = "차대번호를 읽지 못했습니다. 프로필을 직접 골라 저장해 주세요."
                return@read
            }
            if (vin == store.confirmedVin) return@read
            showSettings(true) // 처음 보는 차: 추천을 확인하도록 설정을 펼친다
            val rec = VinReader.recommend(vin)
            vinHint.visibility = View.VISIBLE
            vinHint.text = if (rec != null) {
                pendingProfile = rec
                refreshSettings()
                (if (demo) "[시연용 차대번호] " else "") +
                    "차대번호 ${vin.take(3)}…로 보아 '${rec.label}' 프로필을 추천합니다.\n맞으면 저장을 눌러 주세요."
            } else {
                "차대번호($vin)로는 차종을 알 수 없습니다. 프로필을 골라 저장해 주세요."
            }
        }
    }

    // ---------- 화면 갱신 ----------

    private var lastAlertKind: AlertKind? = null
    private var alertBasePx = 0f
    private var alertFitWidth = -1

    /**
     * 안내 상자 글자: 한 문장이 상자 폭보다 조금 길면 글자를 살짝 줄여 한 줄에 넣는다 ("…입니 / 다." 로 끊기는 것 방지).
     * 많이 길면(78% 미만으로 줄여야 하면) 기본 크기로 두고 자연스럽게 줄바꿈한다. 폭은 창 크기에 맞춰 이미 비례해 있다.
     */
    private fun fitAlertText() {
        if (alertBasePx <= 0f) return
        val avail = alertText.width - alertText.paddingLeft - alertText.paddingRight
        if (avail <= 0) { alertText.post { fitAlertText() }; return }
        val paint = android.text.TextPaint(alertText.paint).apply { textSize = alertBasePx; letterSpacing = alertText.letterSpacing }
        val widest = alertText.text.toString().split('\n').maxOfOrNull { paint.measureText(it) } ?: 0f
        val scale = if (widest > avail) avail / widest else 1f
        // 4% 여유: 딱 맞게 줄이면 글자 끝 한 글자("다.")가 다음 줄로 넘어가는 경우가 있다
        val size = if (scale >= 0.78f) alertBasePx * scale * (if (scale < 1f) 0.96f else 1f) else alertBasePx
        alertText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size)
    }

    /** 안내 문구의 성격: 높이·통행 제한은 경고(빨강), 지정차로 안내·복귀 안내는 안내(노랑), 나머지는 평상시 */
    private fun alertKindOf(text: String): AlertKind = when {
        "높이 제한" in text || "통행제한" in text -> AlertKind.WARN
        "복귀" in text || "지정차로는" in text || "추월차로" in text -> AlertKind.TIP
        else -> AlertKind.INFO
    }

    /** "편도 4차로 · 화물차 3·4차로" → "화물차 지정차로 3·4차로" (규칙 문장은 그대로 두고 화면에서만 다듬는다) */
    private fun summaryOf(laneSummary: String, hasLanes: Boolean): String {
        if (!hasLanes) return ""
        val rest = laneSummary.substringAfter(" · ", laneSummary)
        return if (rest.startsWith("화물차 ")) "화물차 지정차로 " + rest.removePrefix("화물차 ") else rest
    }

    private fun render(incoming: UiState) {
        val state = preview ?: incoming
        val hasLanes = state.lanes.isNotEmpty()
        val demo = state.demo
        // 1) 머리: 도로 이름 / 편도 N차로 / 속도 (시연 주행 중에는 차량 속도)
        roadName.text = state.highwayName ?: if (state.gpsReady || demo != null) "고속도로 구간이 아닙니다" else "위치를 기다리는 중"
        laneTitle.text = if (hasLanes) "편도 ${state.lanes.size}차로" else "일반 도로"
        // 차량 속도(계기판과 같은 값)를 우선 쓴다. GPS 위치 간격으로 계산한 속도는 도착 간격이 들쭉날쭉해 90→160→60 처럼 튄다.
        val speed = state.vehicleSpeedKmh ?: state.speedKmh
        speedValue.text = if (state.gpsReady || demo != null) "$speed" else "–"
        // 2) 요약 한 줄
        laneSummary.text = summaryOf(state.laneSummary, hasLanes)
        // 3) 차로판
        laneBoard.setLanes(state.lanes)
        // 3-1) 높이 제한 시설물이 가까우면: 경고 카드(+붉은 가장자리 빛) 또는 통과 가능 정보 카드. 고속도로 위라면 차로판 위에 덮어 그린다.
        val hs = state.heightStatus
        heightCard.setStatus(hs)
        heightCard.visibility = if (hs != null) View.VISIBLE else View.GONE
        // 차로판 없는 곳(일반 도로)에서는 "고속도로를 주행하면…" 안내 글자가 카드 뒤에 비치지 않게 가린다
        laneBoard.visibility = if (hs != null && !hasLanes) View.INVISIBLE else View.VISIBLE
        redVignette.visibility = if (hs?.blocked == true) View.VISIBLE else View.GONE
        // 4) 안내 상자
        val alert = state.lastAlert ?: if (hasLanes) "지정차로를 지키며 주행 중입니다" else "아직 안내가 없습니다"
        val kind = alertKindOf(alert)
        // 문장이 둘 이상이면 문장마다 줄을 바꾼다
        val shown = alert.replace(". ", ".\n")
        if (alertText.text.toString() != shown) {
            alertText.text = shown
            fitAlertText()
        }
        if (kind != lastAlertKind) {
            lastAlertKind = kind
            alertText.background = Panels.alertBox(theme, kind, density)
            alertText.setTextColor(Panels.alertColors(theme, kind).third)
        }
        renderDemo(state)
        val offer = state.detourOffer
        // 우회 지점 데이터가 미검증이라 버튼은 숨긴다 (코드는 남겨 두고 SHOW_DETOUR 로 끈다)
        detourButton.visibility = if (offer != null && SHOW_DETOUR) View.VISIBLE else View.GONE
        detourButton.setOnClickListener { offer?.let { AppState.onDetourRequested?.invoke(it) } }
    }

    /**
     * 시연 주행 표시. 시연 중이면 파란 줄("시연 주행 중 (가상 위치)")을 항상 보여 주고, 속도도 차량 속도로 쓴다.
     * 시연이 아닐 때의 [시연 주행] 버튼은 정차 상태(차량 속도 0)일 때만 누를 수 있다.
     */
    private fun renderDemo(state: UiState) {
        val demo = state.demo
        demoBanner.visibility = if (demo != null) View.VISIBLE else View.GONE
        demoBar.visibility = if (demo == null) View.VISIBLE else View.GONE
        if (demo != null) {
            demoBannerText.text = "시연 주행 중 (가상 위치) · ${demo.routeLabel} ${demo.progressPercent}%"
            demoHintText.text = demo.hint ?: "속도는 차량 속도입니다"
            demoHintText.visibility = View.VISIBLE
            return
        }
        val kmh = state.vehicleSpeedKmh
        val canStart = kmh != null && kmh == 0
        btnDemoHighway.isEnabled = canStart
        btnDemoUnderpass.isEnabled = canStart
        demoNote.text = when {
            kmh == null -> "차량 속도를 읽을 수 없어 시연 주행을 쓸 수 없습니다 (차량 속도 권한을 허용해 주세요)"
            kmh > 0 -> "주행 중에는 시연 주행을 시작할 수 없습니다 (정차 후 눌러 주세요)"
            else -> "시작한 뒤 가상 콘솔에서 ACCEL(엑셀)을 밟으면 경로를 따라 달립니다"
        }
    }

    // ---------- 권한 ----------

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
        list += com.laneguide.app.pleos.PleosVehicleSignals.PERMISSIONS
        list += VinReader.PERMISSIONS
        list += com.laneguide.app.car.VehicleSpeedReader.PERMISSION
        return list.toTypedArray()
    }

    /** [recommend] false: 창 크기가 바뀌어 다시 만들어진 경우라, 차대번호 추천으로 고르던 설정을 덮어쓰지 않는다. */
    private fun startServiceIfPermitted(recommend: Boolean = true) {
        val missing = requiredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { startAlertService(); if (recommend) recommendFromVin() } else requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSIONS) return
        android.util.Log.d("VinReader", "권한 결과: " + permissions.zip(grantResults.toList()).joinToString { "${it.first.substringAfterLast('.')}=${it.second == 0}" })
        recommendFromVin()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startAlertService()
        } else {
            alertText.text = "위치 권한이 있어야 안내할 수 있습니다"
        }
    }

    private fun startAlertService() {
        startForegroundService(Intent(this, AlertService::class.java))
    }

    private companion object {
        const val REQ_PERMISSIONS = 1
        /** [우회 경로로 변경] 버튼을 보일까. 우회 지점이 미검증이라 끈다 (DESIGN_WARN.md) */
        const val SHOW_DETOUR = false
        const val DESIGN_W = 640f
        const val DESIGN_H = 900f
        /** 가로 배치 기준 폭(1600×900) */
        const val WIDE_W = 1600f
        /** 창 가로 ≥ 세로 × 이 값 이면 가로 배치 */
        const val WIDE_RATIO = 1.3f
        const val MIN_HEIGHT_DM = 10 // 1.0 m
        const val MAX_HEIGHT_DM = 45 // 4.5 m
    }
}
