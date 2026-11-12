package com.laneguide.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import com.laneguide.app.engine.AppState
import com.laneguide.app.engine.UiState
import com.laneguide.app.model.VehicleProfile
import com.laneguide.app.pleos.VinReader
import kotlin.math.roundToInt

/** 내비 옆 창에 띄우는 메인 화면: 구간 요약, 차로 그림, 최근 경고, (정차 중) 설정 */
class MainActivity : Activity() {

    private lateinit var store: ProfileStore
    private lateinit var restriction: DrivingRestrictionWatcher

    private lateinit var laneSummary: TextView
    private lateinit var speedText: TextView
    private lateinit var laneBoard: LaneBoardView
    private lateinit var alertText: TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = ProfileStore(this)

        laneSummary = findViewById(R.id.laneSummary)
        speedText = findViewById(R.id.speedText)
        laneBoard = findViewById(R.id.laneBoard)
        alertText = findViewById(R.id.alertText)
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

        restriction = DrivingRestrictionWatcher(this) { locked -> runOnUiThread { setSettingsLocked(locked) } }
        restriction.start()

        startServiceIfPermitted()
    }

    override fun onStart() {
        super.onStart()
        AppState.observe(observer)
    }

    override fun onStop() {
        AppState.remove(observer)
        super.onStop()
    }

    override fun onDestroy() {
        restriction.stop()
        super.onDestroy()
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

    private fun setupSettings() {
        pendingProfile = store.profile
        pendingHeightDm = (store.heightM * 10).roundToInt()
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

    /** 설정 칸을 펼치거나(true) 프로필 한 줄로 접는다(false). */
    private fun showSettings(open: Boolean) {
        settingsOpen = open
        settingsPanel.visibility = if (open) View.VISIBLE else View.GONE
        profileBar.visibility = if (open) View.GONE else View.VISIBLE
        profileSummary.text = String.format("%s · 높이 %.1f m", store.profile.label, store.heightM)
    }

    private fun refreshSettings() {
        heightValue.text = String.format("%.1f m", pendingHeightDm / 10.0)
        profileButtons.forEach { (profile, button) -> button.alpha = if (profile == pendingProfile) 1f else 0.5f }
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

    private fun render(state: UiState) {
        laneSummary.text = state.laneSummary
        speedText.text = if (state.gpsReady) "${state.highwayName ?: "일반 도로"} · ${state.speedKmh} km/h" else "위치를 기다리는 중"
        laneBoard.setLanes(state.lanes)
        alertText.text = state.lastAlert ?: "아직 안내가 없습니다"
    }

    // ---------- 권한 ----------

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
        list += com.laneguide.app.pleos.PleosVehicleSignals.PERMISSIONS
        list += VinReader.PERMISSIONS
        return list.toTypedArray()
    }

    private fun startServiceIfPermitted() {
        val missing = requiredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { startAlertService(); recommendFromVin() } else requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
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
        const val MIN_HEIGHT_DM = 10 // 1.0 m
        const val MAX_HEIGHT_DM = 45 // 4.5 m
    }
}
