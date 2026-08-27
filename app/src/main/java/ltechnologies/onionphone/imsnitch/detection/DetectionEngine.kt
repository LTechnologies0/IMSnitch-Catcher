package ltechnologies.onionphone.imsnitch.detection

/**
 * Heuristic IMSI-catcher / rogue BTS detector.
 *
 * Stock Android apps cannot read modem cipher suites or baseband IMSI-request
 * events (those live behind privileged / vendor APIs). This engine therefore
 * scores *behavioral* signals from [TelephonyManager] CellInfo + ServiceState:
 * downgrades, isolated cells, anomalous RSRP, operator spoof, TAC jumps, etc.
 *
 * Pure Kotlin — unit-tested without Android framework.
 */
class DetectionEngine(
    private val config: Config = Config(),
) {
    data class Config(
        val strongSignalDbmThreshold: Int = -70,
        val weakNeighborDbmCeiling: Int = -100,
        val maxPlausibleTacDelta: Int = 64,
        val rapidHopWindowMs: Long = 120_000L,
        val rapidHopDistinctCells: Int = 4,
        val downgradeMemoryMs: Long = 30 * 60_000L,
        val alertScoreThreshold: Int = 60,
    )

    private data class HistoryEntry(
        val timestampMs: Long,
        val key: CellIdentityKey?,
        val generation: RatGeneration,
        val lacOrTac: Int?,
    )

    private val history = ArrayDeque<HistoryEntry>()
    private var lastTrustedGeneration: RatGeneration = RatGeneration.UNKNOWN
    private var lastTrustedGenerationAtMs: Long = 0L
    private var lastTrustedOperator: Pair<String, String>? = null
    private val knownRogue = mutableSetOf<CellIdentityKey>()

    fun addKnownRogue(key: CellIdentityKey) {
        knownRogue += key
    }

    fun clearHistory() {
        history.clear()
        lastTrustedGeneration = RatGeneration.UNKNOWN
        lastTrustedGenerationAtMs = 0L
        lastTrustedOperator = null
    }

    fun evaluate(snapshot: CellEnvironmentSnapshot): DetectionResult {
        val findings = mutableListOf<ThreatFinding>()
        val serving = snapshot.serving
        val ctx = snapshot.context

        if (ctx.airplaneMode) {
            return DetectionResult(snapshot, emptyList(), 0)
        }

        serving?.key?.let { key ->
            if (key in knownRogue) {
                findings += ThreatFinding(
                    type = ThreatType.KNOWN_ROGUE_CELL,
                    severity = ThreatSeverity.CRITICAL,
                    title = "Known rogue cell",
                    detail = "Serving cell matches a known-bad identity: ${formatKey(key)}",
                    score = 100,
                    cell = key,
                )
            }
        }

        detectDowngrade(snapshot, findings)
        detectIsolated(snapshot, findings)
        detectStrongNew(snapshot, findings)
        detectOperatorSpoof(snapshot, findings)
        detectTacJump(snapshot, findings)
        detectRapidHop(snapshot, findings)
        detectLimitedService(snapshot, findings)

        updateMemory(snapshot)

        val score = findings.sumOf { it.score }.coerceAtMost(100)
        return DetectionResult(snapshot, findings.sortedByDescending { it.score }, score)
    }

    private fun detectDowngrade(
        snapshot: CellEnvironmentSnapshot,
        out: MutableList<ThreatFinding>,
    ) {
        val servingGen = effectiveGeneration(snapshot)
        if (servingGen.rank < RatGeneration.G4.rank && servingGen != RatGeneration.UNKNOWN) {
            val recentHi =
                lastTrustedGeneration.rank >= RatGeneration.G4.rank &&
                    snapshot.timestampMs - lastTrustedGenerationAtMs <= config.downgradeMemoryMs
            if (recentHi) {
                out += ThreatFinding(
                    type = ThreatType.DOWNGRADE_ATTACK,
                    severity = if (servingGen == RatGeneration.G2) {
                        ThreatSeverity.CRITICAL
                    } else {
                        ThreatSeverity.HIGH
                    },
                    title = "Possible RAT downgrade",
                    detail = "Device moved from ${lastTrustedGeneration.name} to ${servingGen.name}. " +
                        "Fake base stations often force 2G/3G to disable encryption.",
                    score = if (servingGen == RatGeneration.G2) 90 else 70,
                    cell = snapshot.serving?.key,
                )
            } else if (servingGen == RatGeneration.G2) {
                out += ThreatFinding(
                    type = ThreatType.DOWNGRADE_ATTACK,
                    severity = ThreatSeverity.MEDIUM,
                    title = "Connected on 2G",
                    detail = "2G offers weak/no encryption. Prefer disabling 2G in system settings when possible.",
                    score = 40,
                    cell = snapshot.serving?.key,
                )
            }
        }
    }

    private fun detectIsolated(
        snapshot: CellEnvironmentSnapshot,
        out: MutableList<ThreatFinding>,
    ) {
        val serving = snapshot.serving ?: return
        if (snapshot.neighbors.isEmpty() && serving.registered) {
            val gen = serving.key.generation
            // LTE/NR with zero neighbors is more suspicious than rural 2G.
            val severity = when {
                gen.rank >= RatGeneration.G4.rank -> ThreatSeverity.HIGH
                else -> ThreatSeverity.MEDIUM
            }
            out += ThreatFinding(
                type = ThreatType.ISOLATED_CELL,
                severity = severity,
                title = "Isolated serving cell",
                detail = "No neighbor cells reported. Many IMSI-catchers advertise a lone cell.",
                score = if (gen.rank >= RatGeneration.G4.rank) 55 else 35,
                cell = serving.key,
            )
        }
    }

    private fun detectStrongNew(
        snapshot: CellEnvironmentSnapshot,
        out: MutableList<ThreatFinding>,
    ) {
        val serving = snapshot.serving ?: return
        val dbm = serving.dbm ?: return
        val key = serving.key
        val seenBefore = history.any { it.key == key }
        if (seenBefore) return
        if (dbm < config.strongSignalDbmThreshold) return

        val neighborsWeak = snapshot.neighbors.all { n ->
            val nd = n.dbm
            nd == null || nd <= config.weakNeighborDbmCeiling
        }
        if (snapshot.neighbors.isNotEmpty() && !neighborsWeak) return

        out += ThreatFinding(
            type = ThreatType.STRONG_NEW_CELL,
            severity = ThreatSeverity.HIGH,
            title = "Unusually strong new cell",
            detail = "New serving cell ${formatKey(key)} at ${dbm} dBm — " +
                "portable Stingrays often overpower legitimate towers.",
            score = 65,
            cell = key,
        )
    }

    private fun detectOperatorSpoof(
        snapshot: CellEnvironmentSnapshot,
        out: MutableList<ThreatFinding>,
    ) {
        val serving = snapshot.serving ?: return
        val mcc = serving.key.mcc ?: return
        val mnc = serving.key.mnc ?: return
        val ctx = snapshot.context

        val simMcc = ctx.simMcc
        val simMnc = ctx.simMnc
        if (!simMcc.isNullOrBlank() && !simMnc.isNullOrBlank()) {
            if (mcc != simMcc || !mncCompatible(mnc, simMnc)) {
                // Roaming is legitimate; only flag when networkOperator also disagrees
                // or we had a recent trusted home operator.
                val netMcc = ctx.networkOperatorMcc
                val netMnc = ctx.networkOperatorMnc
                val networkAgrees = netMcc == mcc && netMnc != null && mncCompatible(mnc, netMnc)
                val trusted = lastTrustedOperator
                val breaksTrusted = trusted != null &&
                    (trusted.first != mcc || !mncCompatible(mnc, trusted.second))
                if (!networkAgrees || breaksTrusted) {
                    out += ThreatFinding(
                        type = ThreatType.OPERATOR_SPOOF,
                        severity = ThreatSeverity.HIGH,
                        title = "Operator identity mismatch",
                        detail = "Cell advertises $mcc-$mnc but SIM expects $simMcc-$simMnc" +
                            (if (netMcc != null) " (network op $netMcc-$netMnc)" else "") + ".",
                        score = 60,
                        cell = serving.key,
                    )
                }
            }
        }
    }

    private fun detectTacJump(
        snapshot: CellEnvironmentSnapshot,
        out: MutableList<ThreatFinding>,
    ) {
        val serving = snapshot.serving ?: return
        val tac = serving.key.lacOrTac ?: return
        val prev = history.lastOrNull { it.lacOrTac != null } ?: return
        val prevTac = prev.lacOrTac ?: return
        val delta = kotlin.math.abs(tac - prevTac)
        val dt = snapshot.timestampMs - prev.timestampMs
        if (dt in 1..60_000 && delta > config.maxPlausibleTacDelta) {
            out += ThreatFinding(
                type = ThreatType.TAC_LAC_JUMP,
                severity = ThreatSeverity.MEDIUM,
                title = "Abrupt TAC/LAC change",
                detail = "Tracking area jumped by $delta within ${dt / 1000}s ($prevTac → $tac).",
                score = 45,
                cell = serving.key,
            )
        }
    }

    private fun detectRapidHop(
        snapshot: CellEnvironmentSnapshot,
        out: MutableList<ThreatFinding>,
    ) {
        val cutoff = snapshot.timestampMs - config.rapidHopWindowMs
        val recentKeys = history
            .filter { it.timestampMs >= cutoff && it.key != null }
            .mapNotNull { it.key }
            .toMutableSet()
        snapshot.serving?.key?.let { recentKeys += it }
        if (recentKeys.size >= config.rapidHopDistinctCells) {
            out += ThreatFinding(
                type = ThreatType.RAPID_CELL_HOP,
                severity = ThreatSeverity.MEDIUM,
                title = "Rapid cell hopping",
                detail = "${recentKeys.size} distinct serving cells in " +
                    "${config.rapidHopWindowMs / 1000}s — possible active measurement / interception.",
                score = 40,
                cell = snapshot.serving?.key,
            )
        }
    }

    private fun detectLimitedService(
        snapshot: CellEnvironmentSnapshot,
        out: MutableList<ThreatFinding>,
    ) {
        if (!snapshot.context.emergencyOnly) return
        val dbm = snapshot.serving?.dbm
        if (dbm != null && dbm >= config.strongSignalDbmThreshold) {
            out += ThreatFinding(
                type = ThreatType.LIMITED_SERVICE,
                severity = ThreatSeverity.HIGH,
                title = "Limited service on strong cell",
                detail = "Modem reports emergency/limited service despite ${dbm} dBm — " +
                    "can indicate a trap cell rejecting normal attach.",
                score = 70,
                cell = snapshot.serving?.key,
            )
        }
    }

    private fun updateMemory(snapshot: CellEnvironmentSnapshot) {
        val serving = snapshot.serving
        val gen = effectiveGeneration(snapshot)
        history.addLast(
            HistoryEntry(
                timestampMs = snapshot.timestampMs,
                key = serving?.key,
                generation = gen,
                lacOrTac = serving?.key?.lacOrTac,
            ),
        )
        while (history.size > 128) history.removeFirst()
        // Prune older than 2h
        val pruneBefore = snapshot.timestampMs - 2 * 60 * 60_000L
        while (history.isNotEmpty() && history.first().timestampMs < pruneBefore) {
            history.removeFirst()
        }

        if (gen.rank >= RatGeneration.G4.rank) {
            lastTrustedGeneration = gen
            lastTrustedGenerationAtMs = snapshot.timestampMs
        }
        val mcc = serving?.key?.mcc
        val mnc = serving?.key?.mnc
        val simOk = snapshot.context.simMcc == null ||
            (mcc == snapshot.context.simMcc &&
                mnc != null &&
                snapshot.context.simMnc != null &&
                mncCompatible(mnc, snapshot.context.simMnc!!))
        if (gen.rank >= RatGeneration.G4.rank && mcc != null && mnc != null && simOk) {
            lastTrustedOperator = mcc to mnc
        }
    }

    private fun effectiveGeneration(snapshot: CellEnvironmentSnapshot): RatGeneration {
        val fromServing = snapshot.serving?.key?.generation ?: RatGeneration.UNKNOWN
        if (fromServing != RatGeneration.UNKNOWN) return fromServing
        val data = snapshot.context.dataRat
        if (data != RatGeneration.UNKNOWN) return data
        return snapshot.context.voiceRat
    }

    private fun mncCompatible(a: String, b: String): Boolean {
        val na = a.trimStart('0').ifEmpty { "0" }
        val nb = b.trimStart('0').ifEmpty { "0" }
        return na == nb || a == b
    }

    private fun formatKey(key: CellIdentityKey): String =
        buildString {
            append(key.generation.name)
            append(' ')
            append(key.mcc ?: "?")
            append('-')
            append(key.mnc ?: "?")
            append(" TAC/LAC=")
            append(key.lacOrTac ?: -1)
            append(" CI=")
            append(key.cellId ?: -1L)
        }
}
