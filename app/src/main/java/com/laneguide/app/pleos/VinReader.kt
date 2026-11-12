package com.laneguide.app.pleos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import com.laneguide.app.model.VehicleProfile

/**
 * 차대번호(VIN)를 읽어 차종 프로필을 "추천"한다. 최종 결정은 운전자가 저장 버튼으로 한 번 확인한다.
 * 권한: pleos.car.permission.CAR_INFO (+ PRIVATE_CAR_INFO)
 */
class VinReader(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())

    /** 결과는 메인 스레드에서 전달. 실패하면 null. */
    fun read(onResult: (String?) -> Unit) {
        Log.d(TAG, "차대번호 읽기 시작")
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle 서비스 연결 실패", e)
            onResult(null); return
        }
        attempt(retriesLeft = 2, onResult = onResult)
    }

    private fun attempt(retriesLeft: Int, onResult: (String?) -> Unit) {
        try {
            vehicle.getCarInfo().getVINInfo(
                { vin -> Log.d(TAG, "VIN 응답 길이=${vin?.length} 앞3자리=${vin?.take(3)}"); main.post { finish(vin, onResult) } },
                { e ->
                    Log.w(TAG, "VIN 읽기 실패 (남은 재시도 $retriesLeft)", e)
                    if (retriesLeft > 0) main.postDelayed({ attempt(retriesLeft - 1, onResult) }, 1500)
                    else main.post { finish(null, onResult) }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "VIN 요청 실패", e)
            finish(null, onResult)
        }
    }

    private fun finish(vin: String?, onResult: (String?) -> Unit) {
        try { vehicle.release() } catch (_: Exception) {}
        // 정식 VIN은 영문·숫자 17자리. 에뮬레이터는 "invalid" 같은 값을 준다.
        onResult(vin?.trim()?.trim('"')?.uppercase()?.takeIf { VIN_PATTERN.matches(it) })
    }

    companion object {
        private const val TAG = "VinReader"
        private val VIN_PATTERN = Regex("[A-HJ-NPR-Z0-9]{17}")

        /** 에뮬레이터처럼 차대번호가 없을 때, 개발용 빌드에서만 쓰는 시연용 값 (소형 화물 코드 KNC) */
        const val DEMO_VIN = "KNCDEMO0000000001"

        val PERMISSIONS = listOf(
            "pleos.car.permission.CAR_INFO",
            "pleos.car.permission.PRIVATE_CAR_INFO",
        )

        /**
         * 국내 현대·기아 차대번호 앞 3자리(제조사·차종 구분)로 프로필을 추천한다.
         * 모르는 코드면 null → 추천하지 않고 운전자가 직접 고른다.
         */
        fun recommend(vin: String): VehicleProfile? = when (vin.take(3)) {
            "KMH", "KNA", "KND", "KMJ" -> VehicleProfile.PASSENGER   // 승용·SUV·승합
            "KMF", "KMC", "KNC" -> VehicleProfile.LIGHT_TRUCK        // 소형 화물
            "KME" -> VehicleProfile.HEAVY_TRUCK                      // 중대형 화물
            else -> null
        }
    }
}
