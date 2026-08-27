package ltechnologies.onionphone.imsnitch.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineTest {

    private fun cell(
        gen: RatGeneration,
        mcc: String = "208",
        mnc: String = "01",
        lac: Int = 100,
        cid: Long = 1000,
        registered: Boolean = true,
        dbm: Int = -90,
    ) = ObservedCell(
        key = CellIdentityKey(gen, mcc, mnc, lac, cid),
        registered = registered,
        dbm = dbm,
    )

    private fun snap(
        serving: ObservedCell?,
        neighbors: List<ObservedCell> = emptyList(),
        ctx: NetworkContext = NetworkContext(simMcc = "208", simMnc = "01"),
        ts: Long = 1_000_000L,
    ) = CellEnvironmentSnapshot(ts, serving, neighbors, ctx)

    @Test
    fun downgradeFromLteToGsmIsCritical() {
        val engine = DetectionEngine()
        engine.evaluate(
            snap(
                serving = cell(RatGeneration.G4, cid = 1, dbm = -95),
                neighbors = listOf(cell(RatGeneration.G4, cid = 2, registered = false, dbm = -100)),
                ts = 1_000_000L,
            ),
        )
        val result = engine.evaluate(
            snap(
                serving = cell(RatGeneration.G2, cid = 99, lac = 999, dbm = -60),
                ts = 1_000_000L + 60_000L,
            ),
        )
        assertTrue(result.findings.any { it.type == ThreatType.DOWNGRADE_ATTACK })
        assertTrue(result.isAlert)
        assertTrue(result.aggregateScore >= 70)
    }

    @Test
    fun isolatedLteCellScoresHigh() {
        val engine = DetectionEngine()
        val result = engine.evaluate(snap(serving = cell(RatGeneration.G4)))
        assertTrue(result.findings.any { it.type == ThreatType.ISOLATED_CELL })
    }

    @Test
    fun strongBrandNewCellFlagged() {
        val engine = DetectionEngine()
        engine.evaluate(
            snap(
                serving = cell(RatGeneration.G4, cid = 1, dbm = -95),
                neighbors = listOf(cell(RatGeneration.G4, cid = 2, registered = false, dbm = -110)),
            ),
        )
        val result = engine.evaluate(
            snap(
                serving = cell(RatGeneration.G4, cid = 777, lac = 200, dbm = -55),
                neighbors = listOf(cell(RatGeneration.G4, cid = 2, registered = false, dbm = -110)),
                ts = 1_000_000L + 30_000L,
            ),
        )
        assertTrue(result.findings.any { it.type == ThreatType.STRONG_NEW_CELL })
    }

    @Test
    fun airplaneModeSkipsDetection() {
        val engine = DetectionEngine()
        val result = engine.evaluate(
            snap(
                serving = cell(RatGeneration.G2, dbm = -50),
                ctx = NetworkContext(airplaneMode = true),
            ),
        )
        assertTrue(result.findings.isEmpty())
        assertFalse(result.isAlert)
    }

    @Test
    fun knownRogueIsCritical() {
        val engine = DetectionEngine()
        val key = CellIdentityKey(RatGeneration.G4, "208", "01", 1, 42)
        engine.addKnownRogue(key)
        val result = engine.evaluate(
            snap(serving = cell(RatGeneration.G4, lac = 1, cid = 42)),
        )
        assertEquals(ThreatType.KNOWN_ROGUE_CELL, result.findings.first().type)
        assertEquals(ThreatSeverity.CRITICAL, result.findings.first().severity)
    }

    @Test
    fun limitedServiceOnStrongCell() {
        val engine = DetectionEngine()
        val result = engine.evaluate(
            snap(
                serving = cell(RatGeneration.G4, dbm = -60),
                neighbors = listOf(cell(RatGeneration.G4, cid = 2, registered = false)),
                ctx = NetworkContext(
                    simMcc = "208",
                    simMnc = "01",
                    emergencyOnly = true,
                ),
            ),
        )
        assertTrue(result.findings.any { it.type == ThreatType.LIMITED_SERVICE })
    }

    @Test
    fun mapNetworkTypes() {
        assertEquals(RatGeneration.G2, CellInfoMapper.mapNetworkType(16)) // GSM
        assertEquals(RatGeneration.G4, CellInfoMapper.mapNetworkType(13)) // LTE
        assertEquals(RatGeneration.G5, CellInfoMapper.mapNetworkType(20)) // NR
    }

    @Test
    fun parseOperator() {
        val (mcc, mnc) = CellInfoMapper.parseOperator("20801")
        assertEquals("208", mcc)
        assertEquals("01", mnc)
    }
}
