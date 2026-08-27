package ltechnologies.onionphone.imsnitch.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import ltechnologies.onionphone.imsnitch.detection.CellEnvironmentSnapshot
import ltechnologies.onionphone.imsnitch.detection.CellInfoMapper
import ltechnologies.onionphone.imsnitch.detection.NetworkContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Reads live cellular environment via public Telephony APIs.
 *
 * Primary surfaces:
 * - [TelephonyManager.getAllCellInfo] / [TelephonyManager.requestCellInfoUpdate] (API 29+)
 * - [TelephonyManager.getServiceState]
 * - [TelephonyManager.getDataNetworkType] / voice network type
 * - [SubscriptionManager] for SIM MCC/MNC
 */
class CellularReader(private val context: Context) {

    private val telephony: TelephonyManager?
        get() = context.getSystemService(TelephonyManager::class.java)

    fun hasRequiredPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val phone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        return (fine || coarse) && phone
    }

    @SuppressLint("MissingPermission")
    suspend fun readSnapshot(): CellEnvironmentSnapshot? {
        val tm = telephony ?: return null
        if (!hasRequiredPermissions()) return null

        val cells = requestCells(tm)
        val ctx = buildContext(tm)
        return CellInfoMapper.fromCellInfoList(cells, ctx)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCells(tm: TelephonyManager): List<CellInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return suspendCoroutine { cont ->
                try {
                    tm.requestCellInfoUpdate(
                        context.mainExecutor,
                        object : TelephonyManager.CellInfoCallback() {
                            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                cont.resume(cellInfo.toList())
                            }

                            override fun onError(errorCode: Int, detail: Throwable?) {
                                @Suppress("DEPRECATION")
                                cont.resume(tm.allCellInfo.orEmpty())
                            }
                        },
                    )
                } catch (_: SecurityException) {
                    cont.resume(emptyList())
                } catch (_: Exception) {
                    @Suppress("DEPRECATION")
                    cont.resume(tm.allCellInfo.orEmpty())
                }
            }
        }
        @Suppress("DEPRECATION")
        return tm.allCellInfo.orEmpty()
    }

    @SuppressLint("MissingPermission")
    private fun buildContext(tm: TelephonyManager): NetworkContext {
        val (simMcc, simMnc) = CellInfoMapper.parseOperator(tm.simOperator)
        val (netMcc, netMnc) = CellInfoMapper.parseOperator(tm.networkOperator)

        @Suppress("DEPRECATION")
        val dataType = try {
            tm.dataNetworkType
        } catch (_: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        val voiceType = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.voiceNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
        } catch (_: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        val serviceState = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tm.serviceState else null
        } catch (_: SecurityException) {
            null
        }

        return NetworkContext(
            simMcc = simMcc,
            simMnc = simMnc,
            networkOperatorMcc = netMcc,
            networkOperatorMnc = netMnc,
            voiceRat = CellInfoMapper.mapNetworkType(voiceType),
            dataRat = CellInfoMapper.mapNetworkType(dataType),
            emergencyOnly = CellInfoMapper.isEmergencyOnly(serviceState),
            airplaneMode = isAirplaneModeOn(),
        )
    }

    fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) != 0

    fun activeSubscriptionCount(): Int {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return 0
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_PHONE_STATE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return 0
                }
                sm.activeSubscriptionInfoCount
            } else {
                0
            }
        } catch (_: SecurityException) {
            0
        }
    }
}
