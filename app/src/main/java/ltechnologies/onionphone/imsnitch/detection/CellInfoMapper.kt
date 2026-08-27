package ltechnologies.onionphone.imsnitch.detection

import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.ServiceState
import android.telephony.TelephonyManager

/**
 * Maps Android Telephony framework objects into engine-friendly snapshots.
 */
object CellInfoMapper {

    fun mapNetworkType(networkType: Int): RatGeneration = when (networkType) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM,
        -> RatGeneration.G2

        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        -> RatGeneration.G3

        TelephonyManager.NETWORK_TYPE_LTE,
        TelephonyManager.NETWORK_TYPE_IWLAN,
        -> RatGeneration.G4

        TelephonyManager.NETWORK_TYPE_NR -> RatGeneration.G5

        else -> RatGeneration.UNKNOWN
    }

    fun fromCellInfoList(
        cells: List<CellInfo>,
        context: NetworkContext,
        timestampMs: Long = System.currentTimeMillis(),
    ): CellEnvironmentSnapshot {
        val observed = cells.mapNotNull { mapCell(it) }
        val serving = observed.firstOrNull { it.registered }
            ?: observed.maxByOrNull { it.dbm ?: Int.MIN_VALUE }
        val neighbors = observed.filter { it !== serving }
        return CellEnvironmentSnapshot(
            timestampMs = timestampMs,
            serving = serving,
            neighbors = neighbors,
            context = context,
        )
    }

    fun mapCell(info: CellInfo): ObservedCell? = when (info) {
        is CellInfoGsm -> {
            val id = info.cellIdentity
            ObservedCell(
                key = CellIdentityKey(
                    generation = RatGeneration.G2,
                    mcc = id.mccString,
                    mnc = id.mncString,
                    lacOrTac = id.lac.takeUnless { it == CellInfo.UNAVAILABLE },
                    cellId = id.cid.toLong().takeUnless { id.cid == CellInfo.UNAVAILABLE },
                ),
                registered = info.isRegistered,
                dbm = info.cellSignalStrength.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                asu = info.cellSignalStrength.asuLevel.takeUnless { it == CellInfo.UNAVAILABLE },
                level = info.cellSignalStrength.level,
                timingAdvance = info.cellSignalStrength.timingAdvance
                    .takeUnless { it == CellInfo.UNAVAILABLE },
                bandHint = "GSM ARFCN=${id.arfcn}",
            )
        }

        is CellInfoWcdma -> {
            val id = info.cellIdentity
            ObservedCell(
                key = CellIdentityKey(
                    generation = RatGeneration.G3,
                    mcc = id.mccString,
                    mnc = id.mncString,
                    lacOrTac = id.lac.takeUnless { it == CellInfo.UNAVAILABLE },
                    cellId = id.cid.toLong().takeUnless { id.cid == CellInfo.UNAVAILABLE },
                    pci = id.psc.takeUnless { it == CellInfo.UNAVAILABLE },
                ),
                registered = info.isRegistered,
                dbm = info.cellSignalStrength.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                asu = info.cellSignalStrength.asuLevel.takeUnless { it == CellInfo.UNAVAILABLE },
                level = info.cellSignalStrength.level,
                bandHint = "WCDMA UARFCN=${id.uarfcn}",
            )
        }

        is CellInfoTdscdma -> {
            val id = info.cellIdentity
            ObservedCell(
                key = CellIdentityKey(
                    generation = RatGeneration.G3,
                    mcc = id.mccString,
                    mnc = id.mncString,
                    lacOrTac = id.lac.takeUnless { it == CellInfo.UNAVAILABLE },
                    cellId = id.cid.toLong().takeUnless { id.cid == CellInfo.UNAVAILABLE },
                ),
                registered = info.isRegistered,
                dbm = info.cellSignalStrength.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                level = info.cellSignalStrength.level,
                bandHint = "TD-SCDMA",
            )
        }

        is CellInfoLte -> {
            val id = info.cellIdentity
            ObservedCell(
                key = CellIdentityKey(
                    generation = RatGeneration.G4,
                    mcc = id.mccString,
                    mnc = id.mncString,
                    lacOrTac = id.tac.takeUnless { it == CellInfo.UNAVAILABLE },
                    cellId = id.ci.toLong().takeUnless { id.ci == CellInfo.UNAVAILABLE },
                    pci = id.pci.takeUnless { it == CellInfo.UNAVAILABLE },
                ),
                registered = info.isRegistered,
                dbm = info.cellSignalStrength.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                asu = info.cellSignalStrength.asuLevel.takeUnless { it == CellInfo.UNAVAILABLE },
                level = info.cellSignalStrength.level,
                timingAdvance = info.cellSignalStrength.timingAdvance
                    .takeUnless { it == CellInfo.UNAVAILABLE },
                bandHint = "LTE EARFCN=${id.earfcn} band=${id.bandwidth}",
            )
        }

        is CellInfoNr -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            val id = info.cellIdentity as CellIdentityNr
            val ss = info.cellSignalStrength
            ObservedCell(
                key = CellIdentityKey(
                    generation = RatGeneration.G5,
                    mcc = id.mccString,
                    mnc = id.mncString,
                    lacOrTac = id.tac.takeUnless { it == CellInfo.UNAVAILABLE },
                    cellId = id.nci.takeUnless { it == CellInfo.UNAVAILABLE_LONG },
                    pci = id.pci.takeUnless { it == CellInfo.UNAVAILABLE },
                ),
                registered = info.isRegistered,
                dbm = ss.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                asu = ss.asuLevel.takeUnless { it == CellInfo.UNAVAILABLE },
                level = ss.level,
                bandHint = "NR NARFCN=${id.nrarfcn}",
            )
        }

        else -> null
    }

    fun parseOperator(operatorNumeric: String?): Pair<String?, String?> {
        if (operatorNumeric.isNullOrBlank() || operatorNumeric.length < 5) return null to null
        val mcc = operatorNumeric.substring(0, 3)
        val mnc = operatorNumeric.substring(3)
        return mcc to mnc
    }

    fun isEmergencyOnly(serviceState: ServiceState?): Boolean {
        if (serviceState == null) return false
        return serviceState.state == ServiceState.STATE_EMERGENCY_ONLY ||
            serviceState.state == ServiceState.STATE_OUT_OF_SERVICE
    }
}
