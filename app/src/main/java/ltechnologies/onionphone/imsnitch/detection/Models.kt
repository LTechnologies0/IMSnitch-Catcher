package ltechnologies.onionphone.imsnitch.detection

/**
 * Radio access technology generation buckets used for downgrade detection.
 */
enum class RatGeneration(val rank: Int) {
    UNKNOWN(0),
    G2(2),
    G3(3),
    G4(4),
    G5(5),
}

enum class ThreatSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class ThreatType {
    /** Serving cell is 2G/3G after recent LTE/NR — classic Stingray downgrade. */
    DOWNGRADE_ATTACK,

    /** Serving cell appears alone with no neighbors — common fake BTS footprint. */
    ISOLATED_CELL,

    /** Brand-new cell with abnormally strong signal. */
    STRONG_NEW_CELL,

    /** Serving MCC/MNC disagrees with SIM / last trusted operator. */
    OPERATOR_SPOOF,

    /** LAC/TAC jumps by an implausible amount in a short window. */
    TAC_LAC_JUMP,

    /** Serving cell identity churns unusually fast. */
    RAPID_CELL_HOP,

    /** Device reports emergency-only / limited service while a strong cell is present. */
    LIMITED_SERVICE,

    /** Explicitly known-bad cell identity (user/allowlist DB). */
    KNOWN_ROGUE_CELL,
}

data class CellIdentityKey(
    val generation: RatGeneration,
    val mcc: String?,
    val mnc: String?,
    val lacOrTac: Int?,
    val cellId: Long?,
    val pci: Int? = null,
)

data class ObservedCell(
    val key: CellIdentityKey,
    val registered: Boolean,
    val dbm: Int?,
    val asu: Int? = null,
    val level: Int? = null,
    val timingAdvance: Int? = null,
    val bandHint: String? = null,
)

data class NetworkContext(
    val simMcc: String? = null,
    val simMnc: String? = null,
    val networkOperatorMcc: String? = null,
    val networkOperatorMnc: String? = null,
    val voiceRat: RatGeneration = RatGeneration.UNKNOWN,
    val dataRat: RatGeneration = RatGeneration.UNKNOWN,
    val emergencyOnly: Boolean = false,
    val airplaneMode: Boolean = false,
)

data class CellEnvironmentSnapshot(
    val timestampMs: Long,
    val serving: ObservedCell?,
    val neighbors: List<ObservedCell>,
    val context: NetworkContext,
)

data class ThreatFinding(
    val type: ThreatType,
    val severity: ThreatSeverity,
    val title: String,
    val detail: String,
    val score: Int,
    val cell: CellIdentityKey? = null,
)

data class DetectionResult(
    val snapshot: CellEnvironmentSnapshot,
    val findings: List<ThreatFinding>,
    val aggregateScore: Int,
) {
    val isAlert: Boolean get() = findings.any {
        it.severity == ThreatSeverity.HIGH || it.severity == ThreatSeverity.CRITICAL
    }
}
