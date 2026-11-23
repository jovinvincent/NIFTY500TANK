package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.depthtank.data.CycleDetectionConfig
import com.example.depthtank.data.CycleTurningPointEngine
import com.example.depthtank.data.DataQuality
import com.example.depthtank.data.DatasetIntegrityMetadata
import com.example.depthtank.data.InstitutionalRegime
import com.example.depthtank.data.RemoteStocksResult
import com.example.depthtank.data.RemoteTankDataSource
import com.example.depthtank.data.StockTank
import com.example.depthtank.data.TankCalculator
import com.example.depthtank.data.TankHistoryPoint
import com.example.depthtank.data.TankRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase1EngineTest {

    @Test
    fun missingDataRuleExcludesInsufficient52WeekHistoryAndInvalidBounds() {
        val eligibleStrong = StockTank(
            symbol = "ELIGIBLE1",
            name = "Eligible One",
            sector = "IT",
            currentPrice = 175.0,
            low52W = 100.0,
            high52W = 200.0,
            dailyChangePct = 1.0,
            todayHigh = 180.0,
            prior52WHigh = 179.0,
            rollingHistoryDays = 252
        )
        val eligibleWeak = StockTank(
            symbol = "ELIGIBLE2",
            name = "Eligible Two",
            sector = "Banking",
            currentPrice = 110.0,
            low52W = 100.0,
            high52W = 200.0,
            dailyChangePct = -1.0,
            todayHigh = 115.0,
            prior52WHigh = 200.0,
            rollingHistoryDays = 210
        )
        val recentIpo = StockTank(
            symbol = "NEWIPO",
            name = "Brand New IPO",
            sector = "Consumer & Tech",
            currentPrice = 199.0,
            low52W = 100.0,
            high52W = 200.0,
            dailyChangePct = 5.0,
            todayHigh = 202.0,
            prior52WHigh = 195.0,
            rollingHistoryDays = 90
        )
        val corruptedBounds = StockTank(
            symbol = "BROKEN",
            name = "Broken Bounds",
            sector = "Energy",
            currentPrice = 100.0,
            low52W = 100.0,
            high52W = 100.0,
            dailyChangePct = 0.0,
            rollingHistoryDays = 252
        )

        val all = listOf(eligibleStrong, eligibleWeak, recentIpo, corruptedBounds)
        val eligible = TankCalculator.filterEligibleConstituents(all)
        assertEquals(2, eligible.size)

        assertEquals(25.0, TankCalculator.computeMainTankLevel(all), 0.001)
        assertEquals(50.0, TankCalculator.computeBreakoutPulsePct(eligible), 0.001)
        assertEquals(50.0, TankCalculator.computeCapitulationDepthPct(eligible), 0.001)

        val snapshot = TankCalculator.computeFourPillarSnapshot(
            stocks = all,
            history = listOf(
                TankHistoryPoint("2026-11-12", 20.0, 0.0, 1, 2, 0, 40.0),
                TankHistoryPoint("2026-11-13", 25.0, 5.0, 1, 2, 0, 42.5)
            ),
            sessionDate = "2026-11-13"
        )
        assertEquals(2, snapshot.activeEligibleStocksCount)
        assertEquals(2, snapshot.excludedInsufficientHistoryCount)
        assertEquals(25.0, snapshot.mainTankLevelPct, 0.001)
    }

    @Test
    fun breakoutPulseRequiresStrictTodayHighAbovePrior52WeekHighAndTracksDeltaB20() {
        assertTrue(TankCalculator.isGenuine52WeekBreakout(todayHigh = 500.05, prior52WHigh = 500.0))
        assertFalse(TankCalculator.isGenuine52WeekBreakout(todayHigh = 500.0, prior52WHigh = 500.0))
        assertFalse(TankCalculator.isGenuine52WeekBreakout(todayHigh = 499.0, prior52WHigh = 500.0))
        assertFalse(TankCalculator.isGenuine52WeekBreakout(todayHigh = 500.0, prior52WHigh = null))

        val history = (0..24).map { idx ->
            val bp = if (idx <= 5) 12.0 else 4.5
            TankHistoryPoint(
                date = "2026-10-%02d".format(idx + 1),
                tankLevel = 78.0,
                dailyDelta = 0.0,
                flowingCount = 460,
                activeCount = 500,
                highPressureCount = 260,
                avgScore = 88.0,
                breakoutPulsePct = bp,
                plateauDays = idx + 1
            )
        }
        val delta20 = TankCalculator.computeBreakoutPulseDelta20(history, currentBreakoutPulsePct = 4.5)
        assertEquals(-7.5, delta20, 0.001)
        assertEquals(25, TankCalculator.computePlateauDays(history))
    }

    @Test
    fun institutionalRegimeClassifierCoversAllEightStatesAndAvoidsFalseTopWarnings() {
        val healthyOverheated = TankCalculator.classifyInstitutionalRegime(
            tankLevel = 78.5,
            dailyDelta = 0.6,
            plateauDays = 6,
            euphoriaPressurePct = 54.0,
            breakoutPulsePct = 11.2,
            breakoutPulseDelta20 = 2.4,
            capitulationDepthPct = 1.0
        )
        assertEquals(InstitutionalRegime.ELEVATED_EUPHORIA, healthyOverheated)

        val extreme = TankCalculator.classifyInstitutionalRegime(
            tankLevel = 83.2,
            dailyDelta = 0.3,
            plateauDays = 9,
            euphoriaPressurePct = 67.5,
            breakoutPulsePct = 14.0,
            breakoutPulseDelta20 = 1.1,
            capitulationDepthPct = 0.4
        )
        assertEquals(InstitutionalRegime.EXTREME_EUPHORIA, extreme)

        val exhaustion = TankCalculator.classifyInstitutionalRegime(
            tankLevel = 77.4,
            dailyDelta = -0.3,
            plateauDays = 14,
            euphoriaPressurePct = 52.0,
            breakoutPulsePct = 3.8,
            breakoutPulseDelta20 = -6.2,
            capitulationDepthPct = 2.0
        )
        assertEquals(InstitutionalRegime.MOMENTUM_EXHAUSTION, exhaustion)

        assertEquals(
            InstitutionalRegime.BREADTH_DETERIORATION,
            TankCalculator.classifyInstitutionalRegime(62.0, -1.2, 0, 31.0, 2.5, -4.1, 6.0)
        )
        assertEquals(
            InstitutionalRegime.HEALTHY_BULL_FLOW,
            TankCalculator.classifyInstitutionalRegime(64.0, 0.8, 0, 34.0, 8.5, 1.8, 3.0)
        )
        assertEquals(
            InstitutionalRegime.RECOVERY,
            TankCalculator.classifyInstitutionalRegime(31.0, 1.4, 0, 12.0, 4.2, 2.1, 12.0)
        )
        assertEquals(
            InstitutionalRegime.ACCUMULATION,
            TankCalculator.classifyInstitutionalRegime(22.6, -0.75, 0, 13.88, 4.43, 0.5, 10.87)
        )
        assertEquals(
            InstitutionalRegime.DEEP_CAPITULATION,
            TankCalculator.classifyInstitutionalRegime(8.4, -1.8, 0, 1.2, 0.0, -2.0, 58.0)
        )
    }

    @Test
    fun turningPointEngineOperatesOnRawDataAndCausalSignalHasZeroLookAheadBias() {
        val rawSeries = listOf(
            TankHistoryPoint("2020-01-01", 65.0, 0.0, 380, 500, 150, 74.0, euphoriaPressurePct = 30.0, capitulationDepthPct = 2.0),
            TankHistoryPoint("2020-01-17", 81.56, 1.2, 470, 500, 340, 88.0, euphoriaPressurePct = 68.0, capitulationDepthPct = 0.2),
            TankHistoryPoint("2020-02-20", 52.0, -2.1, 300, 500, 90, 62.0, euphoriaPressurePct = 18.0, capitulationDepthPct = 8.0),
            TankHistoryPoint("2020-03-24", 4.93, -3.5, 45, 500, 2, 24.0, euphoriaPressurePct = 0.4, capitulationDepthPct = 76.0),
            TankHistoryPoint("2020-06-15", 35.0, 1.1, 210, 500, 30, 52.0, euphoriaPressurePct = 6.0, capitulationDepthPct = 14.0),
            TankHistoryPoint("2020-10-12", 68.5, 1.5, 410, 500, 180, 78.0, euphoriaPressurePct = 36.0, capitulationDepthPct = 1.8)
        )

        val cycles = CycleTurningPointEngine.detectCyclesRetrospective(
            rawHistory = rawSeries,
            config = CycleDetectionConfig(minSwingProminencePct = 25.0)
        )
        assertEquals(1, cycles.size)
        val covidCycle = cycles.first()
        assertEquals("2020-01-17", covidCycle.exactPeakDate)
        assertEquals(81.56, covidCycle.peakMainTankPct, 0.001)
        assertEquals(68.0, covidCycle.peakEuphoriaPct, 0.001)
        assertEquals("2020-03-24", covidCycle.exactBottomDate)
        assertEquals(4.93, covidCycle.bottomMainTankPct, 0.001)
        assertEquals(76.0, covidCycle.bottomCapitulationPct, 0.001)
        assertEquals(2, covidCycle.daysPeakToBottom)
        assertTrue(covidCycle.isRecovered)
        assertEquals("2020-10-12", covidCycle.recoveryDate)
        assertEquals(2, covidCycle.recoveryDurationDays)
        assertEquals(63.57, covidCycle.recoveryFromBottomPctPoints, 0.01)

        val prefixAtFeb = rawSeries.take(3)
        val signalBeforeFutureKnown = CycleTurningPointEngine.computeCausalSignalAt(prefixAtFeb)
        assertNotNull(signalBeforeFutureKnown)
        assertEquals("2020-02-20", signalBeforeFutureKnown?.sessionDate)
        assertEquals("2020-01-17", signalBeforeFutureKnown?.trailingHighDate)
        assertEquals(29.56, signalBeforeFutureKnown?.drawdownFromTrailingHighPctPoints ?: 0.0, 0.01)

        val unrecoveredCycles = CycleTurningPointEngine.detectCyclesRetrospective(rawSeries.take(5))
        assertEquals(1, unrecoveredCycles.size)
        assertFalse(unrecoveredCycles.first().isRecovered)
        assertNull(unrecoveredCycles.first().recoveryDate)
    }

    @Test
    fun bundledHistoryJsonValidatesPhase1MetadataSchemaAndDynamicCycles() {
        val candidates = listOf(
            File("src/main/assets/nifty500_tank_history.json"),
            File("app/src/main/assets/nifty500_tank_history.json")
        )
        val assetFile = candidates.firstOrNull { it.exists() }
        assertNotNull("Expected nifty500_tank_history.json to exist in module assets", assetFile)

        val root = JSONObject(assetFile!!.readText())
        assertEquals(DataQuality.ILLUSTRATIVE_FALLBACK.wireValue, root.getString("dataQuality"))
        assertTrue(root.getBoolean("isIllustrative"))
        assertEquals(DatasetIntegrityMetadata.MODEL_VERSION, root.getString("modelVersion"))
        assertEquals(DatasetIntegrityMetadata.CALCULATION_VERSION, root.getString("calculationVersion"))
        assertEquals(1.0, root.getDouble("constituentCoverage"), 0.0001)
        assertEquals("2026-11-13", root.getString("lastVerifiedTradingDate"))

        val arr = root.getJSONArray("history")
        assertTrue(arr.length() >= 2600)
        val points = (0 until arr.length()).map { idx ->
            val obj = arr.getJSONObject(idx)
            val binsArr = obj.optJSONArray("bins")
            val parsedBins = if (binsArr != null && binsArr.length() == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) {
                IntArray(TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) { b -> binsArr.getInt(b) }.toList()
            } else {
                emptyList()
            }
            TankHistoryPoint(
                date = obj.getString("date"),
                tankLevel = obj.getDouble("tankLevel"),
                dailyDelta = obj.getDouble("dailyDelta"),
                flowingCount = obj.getInt("flowingCount"),
                activeCount = obj.getInt("activeCount"),
                highPressureCount = obj.getInt("highPressureCount"),
                avgScore = obj.getDouble("avgScore"),
                euphoriaPressurePct = obj.getDouble("euphoriaPressurePct"),
                breakoutCount = obj.getInt("breakoutCount"),
                breakoutPulsePct = obj.getDouble("breakoutPulsePct"),
                breakoutPulseDelta20 = obj.getDouble("breakoutPulseDelta20"),
                capitulationCount = obj.getInt("capitulationCount"),
                capitulationDepthPct = obj.getDouble("capitulationDepthPct"),
                plateauDays = obj.getInt("plateauDays"),
                scoreBins = parsedBins
            )
        }

        assertTrue(
            "Every historical session must track all 500 Nifty 500 constituents",
            points.all { it.activeCount == 500 && it.scoreBins.sum() == 500 }
        )

        val dynamicCycles = CycleTurningPointEngine.detectCyclesRetrospective(points)
        assertTrue("Expected dynamic macro cycles >= 8 across 2016–2026", dynamicCycles.size >= 8)
        assertTrue(dynamicCycles.any { it.exactBottomDate == "2020-03-24" && it.bottomMainTankPct < 6.0 })
        assertTrue("No false mid-bull bottoms above 38%", dynamicCycles.all { it.bottomMainTankPct <= 38.0 })

        // Verify the 2023-2024 bull run peaks in late September 2024 (not December 2023)
        val dec2023 = points.first { it.date == "2023-12-04" }
        val sep2024PeakCycle = dynamicCycles.firstOrNull { it.exactPeakDate.startsWith("2024-09") }
        assertNotNull("Expected secular bull peak in September 2024", sep2024PeakCycle)
        assertTrue(sep2024PeakCycle!!.peakMainTankPct > 80.0)
        assertTrue(sep2024PeakCycle.peakMainTankPct > dec2023.tankLevel + 20.0)
        assertEquals("2025-03-03", sep2024PeakCycle.exactBottomDate)

        // Verify dynamic Hole = 75% constituent-bin simulation peaks cleanly in September 2024
        val sim75 = TankCalculator.simulateHistory(points, holeThreshold = 75.0)
        val cycles75 = CycleTurningPointEngine.detectCyclesForHole(sim75, holeThreshold = 75.0)
        val sep2024At75 = cycles75.firstOrNull { it.exactPeakDate.startsWith("2024-09") }
        val dec2023At75 = sim75.first { it.date == "2023-12-04" }
        assertNotNull("Expected Hole=75% curve to showcase exact September 2024 euphoric top", sep2024At75)
        assertTrue(sep2024At75!!.peakMainTankPct > 70.0)
        assertTrue(sep2024At75.peakMainTankPct > dec2023At75.tankLevel + 30.0)

        // Verify dynamic Hole = 25% simulation captures sharp V-shaped capitulation bottoms
        val sim25 = TankCalculator.simulateHistory(points, holeThreshold = 25.0)
        val cycles25 = CycleTurningPointEngine.detectCyclesForHole(sim25, holeThreshold = 25.0)
        assertTrue(cycles25.any { it.exactBottomDate == "2020-03-24" && it.bottomMainTankPct < 5.0 })
        assertTrue(cycles25.any { it.exactBottomDate == "2025-03-03" && it.bottomMainTankPct < 6.0 })
        assertTrue(cycles25.any { it.exactBottomDate.startsWith("2026-10") })
    }

    @Test
    fun repositoryHydratesFourPillarsCyclesAndIntegrityMetadataFromBundledAssets() = runBlocking {
        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline test"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline test"))
        }
        val repo = TankRepository(ApplicationProvider.getApplicationContext(), offlineSource)
        repo.ensureInitialized()

        assertTrue(repo.stocks.value.size >= 490)
        assertTrue(repo.history.value.size >= 2600)
        assertTrue(repo.validateIntegrityMetadata())
        assertTrue(repo.historyIntegrity.value.isIllustrative)
        assertEquals(DataQuality.ILLUSTRATIVE_FALLBACK, repo.historyIntegrity.value.dataQuality)
        assertEquals(DatasetIntegrityMetadata.MODEL_VERSION, repo.historyIntegrity.value.modelVersion)
        assertEquals(DatasetIntegrityMetadata.CALCULATION_VERSION, repo.historyIntegrity.value.calculationVersion)

        val pillars = repo.fourPillarSnapshot.value
        assertEquals(500, pillars.activeEligibleStocksCount)
        assertEquals(22.48, pillars.mainTankLevelPct, 0.05)
        assertEquals(13.80, pillars.euphoriaPressurePct, 0.05)
        assertEquals(10.80, pillars.capitulationDepthPct, 0.05)
        assertEquals(InstitutionalRegime.ACCUMULATION, pillars.regime)
        assertTrue(repo.detectedCycles.value.size >= 8)
        assertNotNull(repo.liveSignal.value)
        assertTrue(repo.liveSignal.value!!.isLookAheadBiasFree)

        // Verify all 5 index universes (Nifty 500, Nifty 50, Next 50, Midcap 150, Smallcap 250)
        val constituents = repo.universeConstituents.value
        val n50 = constituents[com.example.depthtank.data.TankIndexUniverse.NIFTY_50].orEmpty()
        val nn50 = constituents[com.example.depthtank.data.TankIndexUniverse.NEXT_50].orEmpty()
        val m150 = constituents[com.example.depthtank.data.TankIndexUniverse.MIDCAP_150].orEmpty()
        val s250 = constituents[com.example.depthtank.data.TankIndexUniverse.SMALLCAP_250].orEmpty()
        val n500 = constituents[com.example.depthtank.data.TankIndexUniverse.NIFTY_500].orEmpty()

        assertEquals(50, n50.size)
        assertEquals(50, nn50.size)
        assertEquals(150, m150.size)
        assertEquals(250, s250.size)
        assertEquals(500, n500.size)
        assertTrue((n50 intersect nn50).isEmpty())
        assertTrue((m150 intersect s250).isEmpty())
        assertEquals(n500, n50 + nn50 + m150 + s250)

        val histories = repo.universeHistories.value
        for (u in com.example.depthtank.data.TankIndexUniverse.entries) {
            val h = histories[u].orEmpty()
            assertTrue("Expected full history for ${u.shortLabel}", h.size >= 2600)
            assertTrue(
                "All sessions for ${u.shortLabel} must have activeCount==${u.targetCount}",
                h.all { it.activeCount == u.targetCount && it.scoreBins.sum() == u.targetCount }
            )
            val sim75 = TankCalculator.simulateHistory(
                history = h,
                holeThreshold = 75.0,
                targetConstituents = u.targetCount
            )
            val cycles75 = CycleTurningPointEngine.detectCyclesForHole(sim75, holeThreshold = 75.0)
            assertTrue(
                "Expected 2024-09 peak in ${u.shortLabel} at Hole=75%",
                cycles75.any { it.exactPeakDate.startsWith("2024-09") }
            )
        }

        // Verify terminal Smallcap 250 calculation strictly uses 250 base
        val smallcapStocks = repo.stocks.value.filter { it.symbol in s250 }
        assertEquals(250, smallcapStocks.size)
        val scTankLevel = TankCalculator.computeMainTankLevel(smallcapStocks, 50.0)
        assertEquals(21.07, scTankLevel, 0.05)

        // Verify mergeTodayIntoHistory supports sub-index constituent counts (250 / 50)
        val mergedSmallcap = repo.mergeTodayIntoHistory(
            currentHistory = histories[com.example.depthtank.data.TankIndexUniverse.SMALLCAP_250].orEmpty(),
            stocks = smallcapStocks,
            tradeDate = "2026-11-16",
            targetConstituents = 250
        )
        assertEquals("2026-11-16", mergedSmallcap.last().date)
        assertEquals(250, mergedSmallcap.last().activeCount)
    }

    @Test
    fun deadlyComboEngineValidatesMultiHoleResonanceAndCurrentTrajectory() = runBlocking {
        val sampleBins = IntArray(20) { 10 }.toList() // 200 total stocks uniformly spread
        val reading = com.example.depthtank.data.DeadlyComboEngine.computeReadingFromBins(
            bins = sampleBins,
            breakoutPulsePct = 12.5,
            breakoutDelta20 = 2.0,
            flowingCount = 100
        )
        assertTrue(reading.hole25 > reading.hole50)
        assertTrue(reading.hole50 > reading.hole75)
        assertEquals(5.0, reading.band95To100Pct, 0.01)
        assertEquals(10.0, reading.band0To10Pct, 0.01)

        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline test"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline test"))
        }
        val repo = TankRepository(ApplicationProvider.getApplicationContext(), offlineSource)
        repo.ensureInitialized()

        assertTrue("Expected >= 2600 macro overlay dates", repo.macroOverlays.value.size >= 2600)
        val nov13Overlay = repo.macroOverlays.value["2026-11-13"]
        assertNotNull(nov13Overlay)
        assertEquals(15.5, nov13Overlay!!.indiaVix ?: Double.NaN, 0.05)
        assertEquals(79.37, nov13Overlay.mmiValue ?: Double.NaN, 0.05)

        val fullHist = repo.history.value
        val liveReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = fullHist,
            universeHistories = repo.universeHistories.value,
            stocks = repo.stocks.value,
            macroOverlays = repo.macroOverlays.value
        )
        assertEquals(9, liveReport.peakRulesChecklist.size)
        assertEquals(9, liveReport.bottomRulesChecklist.size)
        assertEquals(
            com.example.depthtank.data.InvestorActionStage.AGGRESSIVE_SIP_STEP_UP,
            liveReport.investorDirective.activeStage
        )
        assertTrue(liveReport.investorDirective.stcgGuidance.contains("20%"))
        assertTrue(liveReport.investorDirective.ltcgGuidance.contains("12.5%"))
        assertTrue((liveReport.kinetics.mmiMinusTankGap ?: Double.NaN) > 50.0)
        assertTrue(liveReport.pathProjection.crashRiskHeadline.startsWith("NO"))
        assertTrue(liveReport.pathProjection.straightEuphoriaHeadline.startsWith("NO"))

        // Verify Oct 3, 2024 triggers Critical Secular Peak Sniper (>=8/9) while Feb 21, 2024 stays non-critical
        val oct3Idx = fullHist.indexOfFirst { it.date == "2024-10-03" }
        assertTrue(oct3Idx > 0)
        val oct3SubHistories = repo.universeHistories.value.mapValues { (_, list) ->
            list.filter { it.date <= "2024-10-03" }
        }
        val oct3Report = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = fullHist.subList(0, oct3Idx + 1),
            universeHistories = oct3SubHistories,
            stocks = emptyList(),
            macroOverlays = repo.macroOverlays.value
        )
        assertEquals(com.example.depthtank.data.ComboDangerLevel.CRITICAL_EXTREME, oct3Report.peakDangerLevel)
        assertEquals(
            com.example.depthtank.data.InvestorActionStage.MASSIVE_PROFIT_BOOKING,
            oct3Report.investorDirective.activeStage
        )

        val feb21Idx = fullHist.indexOfFirst { it.date == "2024-02-21" }
        val feb21SubHistories = repo.universeHistories.value.mapValues { (_, list) ->
            list.filter { it.date <= "2024-02-21" }
        }
        val feb21Report = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = fullHist.subList(0, feb21Idx + 1),
            universeHistories = feb21SubHistories,
            stocks = emptyList(),
            macroOverlays = repo.macroOverlays.value
        )
        assertFalse(
            "Mid-bull Feb 2024 must NOT trigger Massive Profit Booking before Sept 2024 climax",
            feb21Report.investorDirective.activeStage == com.example.depthtank.data.InvestorActionStage.MASSIVE_PROFIT_BOOKING
        )
    }

    @Test
    fun liveMacroAndGapBackfillHydratesVixMmiAdAndPersistsAcrossRestarts() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val seedRepo = TankRepository(
            ctx,
            object : RemoteTankDataSource {
                override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                    Result.failure(IllegalStateException("offline"))

                override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                    Result.failure(IllegalStateException("offline"))
            }
        )
        seedRepo.ensureInitialized()
        val baseStocks = seedRepo.stocks.value
        assertEquals(500, baseStocks.size)

        // Build deterministic multi-day snapshots for 2026-11-16 .. 2026-11-19
        fun stocksWithProfile(levelTarget: Double, advCount: Int): List<StockTank> =
            baseStocks.mapIndexed { idx, s ->
                val range = (s.high52W - s.low52W).coerceAtLeast(10.0)
                val targetScore = (levelTarget + ((idx % 11) - 5) * 3.0).coerceIn(5.0, 95.0)
                val price = s.low52W + range * (targetScore / 100.0)
                val chg = if (idx < advCount) 1.5 else -1.2
                s.copy(currentPrice = price, todayHigh = price, dailyChangePct = chg)
            }

        val day16Stocks = stocksWithProfile(levelTarget = 60.0, advCount = 260)
        val day17StocksTvZeroChg = stocksWithProfile(levelTarget = 58.0, advCount = 240).map { it.copy(dailyChangePct = 0.0) }
        val day17StocksYahoo = stocksWithProfile(levelTarget = 58.0, advCount = 210) // 42.0% A/D
        val day18Stocks = stocksWithProfile(levelTarget = 55.0, advCount = 125) // 25.0% A/D
        val day19Stocks = stocksWithProfile(levelTarget = 66.0, advCount = 353) // 70.6% A/D

        val mockRemote = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> = Result.success(
                RemoteStocksResult(
                    stocks = day19Stocks,
                    sourceName = "Mock Live Feed",
                    sessionDate = "2026-11-19",
                    historicalStocksByDate = mapOf(
                        "2026-11-18" to day18Stocks,
                        "2026-11-17" to day17StocksTvZeroChg
                    )
                )
            )

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("unused"))

            override suspend fun fetchLiveMacroSnapshot(): Result<com.example.depthtank.data.RemoteMacroSnapshot> =
                Result.success(
                    com.example.depthtank.data.RemoteMacroSnapshot(
                        vixByDate = mapOf(
                            "2026-11-16" to 15.68,
                            "2026-11-17" to 15.52,
                            "2026-11-18" to 15.57,
                            "2026-11-19" to 16.11
                        ),
                        mmiByDate = mapOf(
                            "2026-11-16" to 76.34,
                            "2026-11-17" to 76.59,
                            "2026-11-18" to 72.99,
                            "2026-11-19" to 71.87
                        )
                    )
                )

            override suspend fun fetchGapDayStocks(
                missingDates: Set<String>,
                referenceStocks: List<StockTank>,
                bseSymbols: Set<String>
            ): Map<String, List<StockTank>> {
                val out = LinkedHashMap<String, List<StockTank>>()
                if ("2026-11-16" in missingDates) out["2026-11-16"] = day16Stocks
                if ("2026-11-17" in missingDates) out["2026-11-17"] = day17StocksYahoo
                return out
            }
        }

        // Pre-seed an out-of-order 2026-11-19 merge (simulating pre-fix cache without 16..18)
        val jumpHistory = seedRepo.mergeTodayIntoHistory(seedRepo.history.value, day19Stocks, "2026-11-19")
        assertEquals("2026-11-19", jumpHistory.last().date)
        val backfilled = seedRepo.mergeTodayIntoHistory(jumpHistory, day18Stocks, "2026-11-18")
        assertEquals("2026-11-18", backfilled[backfilled.size - 2].date)
        assertEquals("2026-11-19", backfilled.last().date)
        assertEquals(
            backfilled.last().tankLevel - backfilled[backfilled.size - 2].tankLevel,
            backfilled.last().dailyDelta,
            0.02
        )

        val liveRepo = TankRepository(ctx, mockRemote)
        val res = liveRepo.refreshLiveMarketData(silent = false)
        assertTrue(res.isSuccess)

        val tailDates = liveRepo.history.value.takeLast(5).map { it.date }
        assertEquals(listOf("2026-11-13", "2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19"), tailDates)
        assertEquals(16.11, liveRepo.macroOverlays.value["2026-11-19"]?.indiaVix ?: 0.0, 0.01)
        assertEquals(71.87, liveRepo.macroOverlays.value["2026-11-19"]?.mmiValue ?: 0.0, 0.01)
        assertEquals(
            70.6,
            liveRepo.macroOverlays.value["2026-11-19"]?.adPctFor(com.example.depthtank.data.TankIndexUniverse.NIFTY_500) ?: 0.0,
            0.05
        )
        assertEquals(
            42.0,
            liveRepo.macroOverlays.value["2026-11-17"]?.adPctFor(com.example.depthtank.data.TankIndexUniverse.NIFTY_500) ?: 0.0,
            0.05
        )

        val report = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = liveRepo.history.value,
            universeHistories = liveRepo.universeHistories.value,
            stocks = liveRepo.stocks.value,
            macroOverlays = liveRepo.macroOverlays.value
        )
        assertEquals(16.11, report.kinetics.indiaVix ?: Double.NaN, 0.01)
        assertEquals(71.87, report.kinetics.mmiValue ?: Double.NaN, 0.01)
        assertEquals(70.6, report.kinetics.adPctDaily, 0.05)
        assertEquals(TankCalculator.computeMainTankLevel(day19Stocks, 50.0), report.currentReading.hole50, 0.02)
        assertTrue(report.kinetics.momentumAccel3d > 0.3)
        assertFalse(
            "Positive 3D accel must not say decelerated",
            report.pathProjection.straightEuphoriaExplanation.contains("decelerated", ignoreCase = true)
        )
        assertFalse(
            "Strong 70.6% A/D must not say A/D cooled",
            report.pathProjection.straightEuphoriaExplanation.contains("A/D cooled", ignoreCase = true)
        )

        // Verify local vaults restore cleanly on a fresh offline TankRepository instance
        val restartedRepo = TankRepository(
            ctx,
            object : RemoteTankDataSource {
                override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                    Result.failure(IllegalStateException("offline"))

                override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                    Result.failure(IllegalStateException("offline"))
            }
        )
        restartedRepo.ensureInitialized()
        assertEquals(
            listOf("2026-11-13", "2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19"),
            restartedRepo.history.value.takeLast(5).map { it.date }
        )
        assertEquals(16.11, restartedRepo.macroOverlays.value["2026-11-19"]?.indiaVix ?: 0.0, 0.01)
        assertEquals(71.87, restartedRepo.macroOverlays.value["2026-11-19"]?.mmiValue ?: 0.0, 0.01)
    }

    @Test
    fun tradingViewMultiDayPayloadParsesCurrentAndHistoricalBars() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ds = com.example.depthtank.data.LiveMarketTankDataSource(ctx)
        // Epochs corresponding to 2026-11-19, 2026-11-18, 2026-11-17 IST
        val t0 = 1795074600L // 2026-11-19 14:40 IST
        val t1 = 1794973500L // 2026-11-18 09:15 IST
        val t2 = 1794887100L // 2026-11-17 09:15 IST
        val sampleJson = """
            {
              "data": [
                {
                  "s": "NSE:RELIANCE",
                  "d": ["RELIANCE", "Reliance Ind", 1400.0, 1.8, 1600.0, 1100.0, "Energy", $t0, 1410.0, 1600.0, 10.0, 5.0,
                        $t1, 1375.0, -0.9, 1390.0, 1600.0, 1100.0,
                        $t2, 1387.5, 1395.0, 1600.0, 1100.0]
                }
              ]
            }
        """.trimIndent()
        val (cur, session, hist) = ds.parseTradingViewMultiDayForTest(sampleJson, setOf("RELIANCE"))
        assertEquals("2026-11-19", session)
        assertEquals(1, cur.size)
        assertEquals(1.8, cur[0].dailyChangePct, 0.001)
        assertEquals(2, hist.size)
        assertEquals(1375.0, hist["2026-11-18"]?.firstOrNull()?.currentPrice ?: 0.0, 0.01)
        assertEquals(-0.9, hist["2026-11-18"]?.firstOrNull()?.dailyChangePct ?: 0.0, 0.01)
        assertEquals(1387.5, hist["2026-11-17"]?.firstOrNull()?.currentPrice ?: 0.0, 0.01)
    }

    @Test
    fun pre2016ArchiveLazyDecompressionSha256CacheAndCorruptRecovery() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cacheDir = File(ctx.filesDir, TankRepository.PRE2016_CACHE_DIR_NAME)
        if (cacheDir.exists()) cacheDir.deleteRecursively()

        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline"))
        }

        val repo1 = TankRepository(ctx, offlineSource)
        repo1.ensureInitialized()
        val modernBaselineN500 = repo1.history.value.toList()
        assertEquals("2016-04-01", modernBaselineN500.first().date)
        assertEquals(com.example.depthtank.data.ArchiveHydrationState.NOT_LOADED, repo1.archiveHydrationState.value)

        // First hydration decompresses .json.gz assets, validates SHA-256, and writes cache
        val res1 = repo1.ensurePre2016Loaded()
        assertTrue("Expected pre-2016 archive hydration to succeed: ${res1.exceptionOrNull()}", res1.isSuccess)
        assertEquals(com.example.depthtank.data.ArchiveHydrationState.READY, repo1.archiveHydrationState.value)
        assertTrue(repo1.archiveSummary.value.checksumVerified)
        assertFalse("First run must decompress from gzip assets", repo1.archiveSummary.value.loadedFromCache)
        assertEquals(5261, repo1.archiveSummary.value.pre2016SessionCount)

        // Verify seamless boundary join at 2016-03-31 -> 2016-04-01 with zero duplicates and zero mutation to 2016+
        val fullN500 = repo1.history.value
        assertEquals("1995-01-02", fullN500.first().date)
        assertEquals(modernBaselineN500.last().date, fullN500.last().date)
        assertEquals(5261 + modernBaselineN500.size, fullN500.size)
        val uniqueDates = fullN500.map { it.date }.toSet()
        assertEquals("Zero duplicate dates allowed across 1995–Present", fullN500.size, uniqueDates.size)
        for (i in 1 until fullN500.size) {
            assertTrue("History must be strictly chronological", fullN500[i - 1].date < fullN500[i].date)
        }
        val joinedModernSlice = fullN500.filter { it.date >= "2016-04-01" }
        assertEquals("2016–Present records must remain completely unmodified", modernBaselineN500, joinedModernSlice)

        // Verify all 5 index universes have complete 1995-01-02..Present histories
        for (u in com.example.depthtank.data.TankIndexUniverse.entries) {
            val h = repo1.universeHistories.value[u].orEmpty()
            assertEquals("Expected ${u.shortLabel} to start on 1995-01-02", "1995-01-02", h.first().date)
            assertEquals("Expected ${u.shortLabel} to have 5261 pre-2016 sessions", 5261, h.count { it.date < "2016-04-01" })
            assertEquals("Zero duplicate dates in ${u.shortLabel}", h.size, h.map { it.date }.toSet().size)
        }

        // Second repository instance must hit verified persistent cache directly
        val repo2 = TankRepository(ctx, offlineSource)
        repo2.ensureInitialized()
        val res2 = repo2.ensurePre2016Loaded()
        assertTrue(res2.isSuccess)
        assertTrue("Second launch must load from SHA-256 verified cache", repo2.archiveSummary.value.loadedFromCache)
        assertTrue(repo2.archiveSummary.value.checksumVerified)
        assertEquals(fullN500.size, repo2.history.value.size)

        // Corrupt a cached file and verify automatic SHA-256 mismatch detection + transparent gzip recovery
        val cachedTankFile = File(cacheDir, "pre2016_tank_history.json")
        assertTrue(cachedTankFile.exists())
        cachedTankFile.writeText("{corrupted_payload_for_test")

        val repo3 = TankRepository(ctx, offlineSource)
        repo3.ensureInitialized()
        val res3 = repo3.ensurePre2016Loaded()
        assertTrue("Corrupt cache must auto-recover from gzip asset", res3.isSuccess)
        assertFalse("Recovered file must be re-decompressed from gzip", repo3.archiveSummary.value.loadedFromCache)
        assertTrue(repo3.archiveSummary.value.checksumVerified)
        assertEquals(fullN500.size, repo3.history.value.size)
    }

    @Test
    fun allTimeframesAndChartAggregationVerifyEveryHorizonFrom1YToMax1995() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline"))
        }
        val repo = TankRepository(ctx, offlineSource)
        repo.ensureInitialized()
        assertTrue(repo.ensurePre2016Loaded().isSuccess)

        val fullDaily = repo.history.value
        assertTrue("Expected >7800 daily sessions from 1995 to Present", fullDaily.size >= 7800)

        // Explicitly verify EVERY timeframe: 1Y, 3Y, 5Y, 10Y, 20Y, MAX
        val expectedCounts = mapOf(
            com.example.depthtank.data.TimeframeOption.Y1 to 252,
            com.example.depthtank.data.TimeframeOption.Y3 to 756,
            com.example.depthtank.data.TimeframeOption.Y5 to 1260,
            com.example.depthtank.data.TimeframeOption.Y10 to 2520,
            com.example.depthtank.data.TimeframeOption.Y20 to 5040,
            com.example.depthtank.data.TimeframeOption.MAX to fullDaily.size
        )
        for ((tf, expectedLen) in expectedCounts) {
            val sliced = if (tf == com.example.depthtank.data.TimeframeOption.MAX || fullDaily.size <= tf.tradingDays) {
                fullDaily
            } else {
                fullDaily.takeLast(tf.tradingDays)
            }
            assertEquals("Timeframe ${tf.label} must slice exact expected bar count", expectedLen, sliced.size)
            assertTrue("Timeframe ${tf.label} must remain strictly ordered", sliced.first().date < sliced.last().date)
            val activePoints = sliced.filter { !it.isIncompleteColdStart }
            assertTrue("Timeframe ${tf.label} must contain valid active breadth records", activePoints.isNotEmpty())
            assertTrue(
                "Timeframe ${tf.label} active points must have valid 0..100 tankLevel",
                activePoints.all { it.tankLevel in 0.0..100.0 && it.activeCount > 0 }
            )
        }

        // Verify 20Y reaches into 2006 and MAX reaches Jan 2, 1995
        val slice20Y = fullDaily.takeLast(com.example.depthtank.data.TimeframeOption.Y20.tradingDays)
        assertTrue("20Y horizon must reach pre-2008 period", slice20Y.first().date < "2007-06-01")
        assertEquals("1995-01-02", fullDaily.first().date)

        // Verify Daily / Weekly / Monthly aggregation derived strictly from daily bars without mutating daily data
        val weekly = com.example.depthtank.data.TankHistoryAggregator.aggregate(
            fullDaily,
            com.example.depthtank.data.ChartAggregation.WEEKLY
        )
        val monthly = com.example.depthtank.data.TankHistoryAggregator.aggregate(
            fullDaily,
            com.example.depthtank.data.ChartAggregation.MONTHLY
        )
        assertTrue("Weekly bars must be ~1500..1750 across 31 years (got ${weekly.size})", weekly.size in 1400..1800)
        assertTrue("Monthly bars must be ~360..400 across 31 years (got ${monthly.size})", monthly.size in 350..420)
        assertEquals(fullDaily.last().date, weekly.last().date)
        assertEquals(fullDaily.last().date, monthly.last().date)
        assertEquals(fullDaily.last().tankLevel, weekly.last().tankLevel, 0.0001)
        assertEquals(fullDaily.last().tankLevel, monthly.last().tankLevel, 0.0001)

        // Verify detected cycles now span iconic pre-2016 secular market cycles (e.g. 2008 GFC)
        val cycles = repo.detectedCycles.value
        assertTrue("Expected multi-decade cycles >= 14 across 1995–Present (got ${cycles.size})", cycles.size >= 14)
        assertTrue(
            "Expected 2008 GFC washout cycle in 1995–Present detected cycles",
            cycles.any { it.exactBottomDate.startsWith("2008-10") || it.exactBottomDate.startsWith("2008-11") }
        )
    }

    @Test
    fun partialFactorSniperScoringHonorsPreInceptionVixAndMmiBoundaries() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline"))
        }
        val repo = TankRepository(ctx, offlineSource)
        repo.ensureInitialized()
        assertTrue(repo.ensurePre2016Loaded().isSuccess)

        val fullHist = repo.history.value
        val macros = repo.macroOverlays.value

        // 1. Pre-VIX era (e.g., 2000-02-15): both VIX and MMI must be null -> 7/9 active factors
        val dotComIdx = fullHist.indexOfLast { it.date <= "2000-02-15" }
        assertTrue(dotComIdx > 100)
        val dotComDate = fullHist[dotComIdx].date
        assertNull("Pre-2008 macro overlay must keep India VIX null", macros[dotComDate]?.indiaVix)
        assertNull("Pre-2016 macro overlay must keep MMI null", macros[dotComDate]?.mmiValue)

        val dotComSub = repo.universeHistories.value.mapValues { (_, list) -> list.filter { it.date <= dotComDate } }
        val dotComReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = fullHist.subList(0, dotComIdx + 1),
            universeHistories = dotComSub,
            stocks = emptyList(),
            macroOverlays = macros
        )
        assertNull(dotComReport.kinetics.indiaVix)
        assertNull(dotComReport.kinetics.mmiValue)
        assertNull(dotComReport.kinetics.mmiMinusTankGap)
        assertEquals(7, dotComReport.peakAvailableFactors)
        assertEquals(7, dotComReport.bottomAvailableFactors)
        assertTrue(dotComReport.isPartialFactorScore)
        assertEquals("7/9 Active • Normalized", dotComReport.factorCoverageLabel)
        assertEquals(2, dotComReport.peakRulesChecklist.count { !it.isDataAvailable })
        assertTrue(dotComReport.peakRulesChecklist.filter { !it.isDataAvailable }.all { it.currentValueFormatted.startsWith("N/A") })
        assertFalse("Trajectory forecast must never render raw 'null' string", dotComReport.forwardTrajectoryForecast.contains("null"))

        // 2. Post-VIX, Pre-MMI era (e.g., 2008-10-27 GFC capitulation): VIX genuine != null, MMI null -> 8/9 active factors
        val gfcIdx = fullHist.indexOfLast { it.date <= "2008-10-27" }
        assertTrue(gfcIdx > 100)
        val gfcDate = fullHist[gfcIdx].date
        assertNotNull("2008-10-27 must have genuine ^INDIAVIX", macros[gfcDate]?.indiaVix)
        assertTrue("GFC India VIX must be elevated (>40)", (macros[gfcDate]?.indiaVix ?: 0.0) > 40.0)
        assertNull("2008-10-27 must keep MMI null", macros[gfcDate]?.mmiValue)

        val gfcSub = repo.universeHistories.value.mapValues { (_, list) -> list.filter { it.date <= gfcDate } }
        val gfcReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = fullHist.subList(0, gfcIdx + 1),
            universeHistories = gfcSub,
            stocks = emptyList(),
            macroOverlays = macros
        )
        assertNotNull(gfcReport.kinetics.indiaVix)
        assertNull(gfcReport.kinetics.mmiValue)
        assertEquals(8, gfcReport.peakAvailableFactors)
        assertEquals(8, gfcReport.bottomAvailableFactors)
        assertTrue(gfcReport.isPartialFactorScore)
        assertEquals("8/9 Active • Normalized", gfcReport.factorCoverageLabel)
        assertFalse(gfcReport.forwardTrajectoryForecast.contains("null"))

        // 3. Full modern 2026 report: 9/9 active factors + historical ledger includes pre-2016 empirical episodes
        val modernReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = fullHist,
            universeHistories = repo.universeHistories.value,
            stocks = repo.stocks.value,
            macroOverlays = macros
        )
        assertEquals(9, modernReport.peakAvailableFactors)
        assertEquals(9, modernReport.bottomAvailableFactors)
        assertFalse(modernReport.isPartialFactorScore)
        assertTrue(
            "Empirical ledger must include pre-2016 secular peak episodes",
            modernReport.historicalPeakEpisodes.any { it.peakOrTroughDate < "2016-04-01" && it.activeFactors < 9 }
        )
        assertTrue(
            "Empirical ledger must include pre-2016 capitulation trough episodes",
            modernReport.historicalBottomEpisodes.any { it.peakOrTroughDate < "2016-04-01" && it.activeFactors < 9 }
        )
    }

    @Test
    fun valuationPeNormalizationHydratesAllUniversesAndRespondsToHoleShiftsAcrossTimeframes() = runBlocking {
        // 1. Exact 0-100 Valuation P/E normalization formula matches Price normalization
        val midPeStock = StockTank(
            symbol = "TESTPE",
            name = "Test PE Corp",
            sector = "IT",
            currentPrice = 150.0,
            low52W = 100.0,
            high52W = 200.0,
            dailyChangePct = 1.0,
            peTtm = 30.0,
            peLow52W = 20.0,
            peHigh52W = 40.0,
            epsTtm = 5.0
        )
        assertEquals(50.0, midPeStock.peScore ?: Double.NaN, 0.0001)
        assertEquals(25.0, midPeStock.peOverflowVolumeAt(25.0), 0.0001)
        assertEquals(0.0, midPeStock.peOverflowVolumeAt(50.0), 0.0001)
        assertTrue(midPeStock.isPeFlowingAt(25.0))
        assertFalse(midPeStock.isPeFlowingAt(50.0))

        val highPeStock = midPeStock.copy(symbol = "HIGHPE", peTtm = 38.0) // peScore = 90.0
        assertEquals(90.0, highPeStock.peScore ?: Double.NaN, 0.0001)
        val lossMakingStock = midPeStock.copy(symbol = "LOSSCO", peTtm = null, peLow52W = null, peHigh52W = null, epsTtm = -2.0)
        assertNull(lossMakingStock.peScore)
        assertFalse(lossMakingStock.hasValidPeWindow)

        val pair = listOf(midPeStock, highPeStock, lossMakingStock)
        assertEquals(2, TankCalculator.filterEligiblePeConstituents(pair).size)
        // At H=50: midPe contributes 0, highPe contributes 40 -> 40 / (2 * 50) * 100 = 40.0%
        assertEquals(40.0, TankCalculator.computePeTankLevel(pair, 50.0) ?: Double.NaN, 0.001)
        // At H=25: midPe contributes 25, highPe contributes 65 -> 90 / (2 * 75) * 100 = 60.0%
        assertEquals(60.0, TankCalculator.computePeTankLevel(pair, 25.0) ?: Double.NaN, 0.001)
        // At H=75: midPe contributes 0, highPe contributes 15 -> 15 / (2 * 25) * 100 = 30.0%
        assertEquals(30.0, TankCalculator.computePeTankLevel(pair, 75.0) ?: Double.NaN, 0.001)

        // 2. Hydrate bundled valuation_pe_history.json.gz into all 5 universes + pre-2016 archive
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline"))
        }
        val repo = TankRepository(ctx, offlineSource)
        repo.ensureInitialized()
        assertTrue(repo.ensurePre2016Loaded().isSuccess)

        val peEligibleStocks = TankCalculator.filterEligiblePeConstituents(repo.stocks.value)
        assertTrue(
            "Expected >= 450 Nifty 500 stocks hydrated with real 52W Valuation P/E ranges (got ${peEligibleStocks.size})",
            peEligibleStocks.size >= 450
        )
        assertTrue(
            "Every hydrated stock peScore must lie in 0..100",
            peEligibleStocks.all { (it.peScore ?: -1.0) in 0.0..100.0 }
        )

        // Verify all 5 index universes have real historical Valuation P/E and zero fabricated pre-2007 P/E
        for (u in com.example.depthtank.data.TankIndexUniverse.entries) {
            val uHist = repo.universeHistories.value[u].orEmpty()
            val pre2007 = uHist.filter { it.date < "2007-03-01" }
            assertTrue("Expected pre-2007 sessions for ${u.shortLabel}", pre2007.isNotEmpty())
            assertTrue(
                "Pre-Mar-2007 dates must NEVER fabricate Valuation P/E (${u.shortLabel})",
                pre2007.all { it.peTankLevel == null && it.peScoreBins.isEmpty() }
            )

            val post2008 = uHist.filter { it.date >= "2008-06-01" }
            val peCoverage = post2008.count { it.peTankLevel != null }.toDouble() / post2008.size.coerceAtLeast(1)
            assertTrue(
                "Expected >= 95% Valuation P/E coverage for ${u.shortLabel} from 2008..Present (got ${peCoverage * 100}%)",
                peCoverage >= 0.95
            )
            assertTrue(
                "All populated Valuation P/E tank levels for ${u.shortLabel} must lie in 0..100",
                post2008.mapNotNull { it.peTankLevel }.all { it in 0.0..100.0 }
            )
        }

        // 3. Dynamic Hole shifting monotonically shifts Valuation P/E Tank across ALL timeframes (1Y..MAX)
        val n500Full = repo.history.value
        val n500Stocks = repo.stocks.value
        val sim0 = TankCalculator.simulateHistory(n500Full, holeThreshold = 0.0, liveStocks = n500Stocks)
        val sim25 = TankCalculator.simulateHistory(n500Full, holeThreshold = 25.0, liveStocks = n500Stocks)
        val sim50 = TankCalculator.simulateHistory(n500Full, holeThreshold = 50.0, liveStocks = n500Stocks)
        val sim75 = TankCalculator.simulateHistory(n500Full, holeThreshold = 75.0, liveStocks = n500Stocks)
        val sim85 = TankCalculator.simulateHistory(n500Full, holeThreshold = 85.0, liveStocks = n500Stocks)

        // Pre-2007 stays null across every hole threshold
        assertTrue(sim25.filter { it.date < "2007-03-01" }.all { it.peTankLevel == null })
        assertTrue(sim75.filter { it.date < "2007-03-01" }.all { it.peTankLevel == null })

        // Verify monotonicity on iconic dates (Sep 2024 peak, Mar 2020 washout, latest live date)
        val checkDates = listOf("2008-10-24", "2014-09-05", "2020-03-24", "2024-09-20", n500Full.last().date)
        for (d in checkDates) {
            val idx = n500Full.indexOfLast { it.date <= d }
            val v0 = sim0[idx].peTankLevel!!
            val v25 = sim25[idx].peTankLevel!!
            val v50 = sim50[idx].peTankLevel!!
            val v75 = sim75[idx].peTankLevel!!
            val v85 = sim85[idx].peTankLevel!!
            assertTrue("Expected monotonic Val P/E at $d: $v0 >= $v25 >= $v50 >= $v75 >= $v85",
                v0 >= v25 - 0.05 && v25 >= v50 - 0.05 && v50 >= v75 - 0.05 && v75 >= v85 - 0.05)
        }
        // Confirm Hole=25 vs Hole=75 produces a genuinely distinct Valuation P/E curve on latest bar
        assertTrue(
            "Shifting hole from 25% to 75% must visibly shift Valuation P/E tank level",
            (sim25.last().peTankLevel!! - sim75.last().peTankLevel!!) > 5.0
        )

        // 4. Verify Weekly & Monthly aggregation preserves Valuation P/E fields cleanly
        val weekly = com.example.depthtank.data.TankHistoryAggregator.aggregate(
            sim50,
            com.example.depthtank.data.ChartAggregation.WEEKLY
        )
        val monthly = com.example.depthtank.data.TankHistoryAggregator.aggregate(
            sim50,
            com.example.depthtank.data.ChartAggregation.MONTHLY
        )
        assertNotNull(weekly.last().peTankLevel)
        assertNotNull(monthly.last().peTankLevel)
        assertEquals(sim50.last().peTankLevel!!, weekly.last().peTankLevel!!, 0.01)
        assertEquals(sim50.last().peTankLevel!!, monthly.last().peTankLevel!!, 0.01)
        assertNull("Pre-2007 weekly bars must remain null for Valuation P/E", weekly.first().peTankLevel)
    }

    @Test
    fun tradingView25ColumnPayloadParsesLiveValuationPeAndEpsAndHistoricalDerivedPe() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ds = com.example.depthtank.data.LiveMarketTankDataSource(ctx)
        val t0 = 1795074600L // 2026-11-19 14:40 IST
        val t1 = 1794973500L // 2026-11-18 09:15 IST
        val t2 = 1794887100L // 2026-11-17 09:15 IST
        val sampleJson = """
            {
              "data": [
                {
                  "s": "NSE:TCS",
                  "d": ["TCS", "Tata Consultancy Services", 4000.0, 2.0, 4600.0, 3400.0, "IT", $t0, 4020.0, 4600.0, 12.0, 6.0,
                        $t1, 3920.0, -0.5, 3950.0, 4600.0, 3400.0,
                        $t2, 3940.0, 3960.0, 4600.0, 3400.0,
                        25.0, 160.0]
                }
              ]
            }
        """.trimIndent()
        val (cur, session, hist) = ds.parseTradingViewMultiDayForTest(sampleJson, setOf("TCS"))
        assertEquals("2026-11-19", session)
        assertEquals(1, cur.size)
        assertEquals(25.0, cur[0].peTtm ?: Double.NaN, 0.001)
        assertEquals(160.0, cur[0].epsTtm ?: Double.NaN, 0.001)
        // Historical sessions derive P/E cleanly from close / epsTtm
        assertEquals(3920.0 / 160.0, hist["2026-11-18"]?.firstOrNull()?.peTtm ?: Double.NaN, 0.01)
        assertEquals(3940.0 / 160.0, hist["2026-11-17"]?.firstOrNull()?.peTtm ?: Double.NaN, 0.01)
    }

    @Test
    fun cycleReportIntegratesValuationPeIntoTelemetryAnalogsAndRecommendations() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline"))
        }
        val repo = TankRepository(ctx, offlineSource)
        repo.ensureInitialized()
        assertTrue(repo.ensurePre2016Loaded().isSuccess)

        val fullHist = repo.history.value
        val fullUniverses = repo.universeHistories.value
        val macroOverlays = repo.macroOverlays.value
        val stocks = repo.stocks.value

        // 1. Current live/seed report: keeps exact 9-factor checklists, populates real Valuation P/E,
        // computes empirical valuation analogs, and weaves Valuation P/E into Investor Action & Path Verdict
        val liveReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = fullHist,
            universeHistories = fullUniverses,
            stocks = stocks,
            macroOverlays = macroOverlays
        )
        assertEquals(9, liveReport.peakRulesChecklist.size)
        assertEquals(9, liveReport.bottomRulesChecklist.size)
        assertNotNull("Live report must include real Valuation P/E Tank level", liveReport.kinetics.peTankLevel)
        assertNotNull("Live report must include real Median P/E", liveReport.kinetics.medianPe)
        assertNotNull("Live report must include Price−P/E spread", liveReport.kinetics.priceMinusPeGap)
        assertTrue("Valuation P/E level must be in 0..100", liveReport.kinetics.peTankLevel!! in 0.0..100.0)
        assertTrue("Valuation analog summary must be populated from real 2007–2026 history", liveReport.kinetics.valuationAnalogSummary.contains("Historical Val P/E Analogs"))
        assertTrue("Investor directive rationale must incorporate Valuation P/E", liveReport.investorDirective.executiveRationale.contains("Val P/E"))
        assertTrue("Crash risk assessment must incorporate Valuation P/E", liveReport.pathProjection.crashRiskExplanation.contains("Val P/E"))
        assertTrue("Expected path summary must incorporate Valuation P/E", liveReport.pathProjection.expectedPathSummary.contains("Val P/E"))
        assertEquals(com.example.depthtank.data.InvestorActionStage.AGGRESSIVE_SIP_STEP_UP, liveReport.investorDirective.activeStage)

        // 2. Historical episodes carry verified Valuation P/E for 2007–2026 cycles and strictly null pre-Mar 2007
        val sep2024Peak = liveReport.historicalPeakEpisodes.first { it.episodeId == "PK_2024_09" }
        assertEquals(44.2, sep2024Peak.peTankLevel ?: Double.NaN, 0.01)
        assertEquals(47.0, sep2024Peak.medianPe ?: Double.NaN, 0.01)

        val covidBottom = liveReport.historicalBottomEpisodes.first { it.episodeId == "BOT_2020_03" }
        assertEquals(1.1, covidBottom.peTankLevel ?: Double.NaN, 0.01)
        assertEquals(12.9, covidBottom.medianPe ?: Double.NaN, 0.01)

        val gfcBottom = liveReport.historicalBottomEpisodes.first { it.episodeId == "BOT_2008_10" }
        assertNotNull("Oct 2008 GFC trough must dynamically populate genuine Valuation P/E", gfcBottom.peTankLevel)
        assertTrue("Oct 2008 GFC Val P/E should reflect extreme washout (<10%)", gfcBottom.peTankLevel!! < 10.0)

        val dotComPeak = liveReport.historicalPeakEpisodes.first { it.episodeId == "PK_2000_02" }
        assertNull("Pre-Mar-2007 Dot-Com peak must strictly keep stock peTankLevel = null", dotComPeak.peTankLevel)
        assertNull("Pre-Mar-2007 Dot-Com peak must strictly keep stock medianPe = null", dotComPeak.medianPe)
        assertNotNull("Dot-Com Feb 2000 peak must carry real Official Point-in-Time NSE Index P/E", dotComPeak.indexPe)
        assertTrue("Dot-Com breadth peak Official Index P/E must reflect elevated valuation (>22x, got ${dotComPeak.indexPe})", (dotComPeak.indexPe ?: 0.0) > 22.0)

        // 3. Pre-2007 (Dot-Com Feb 2000): stock Val P/E strictly null, Official NSE Index P/E genuine (>28x)
        val dotComHist = fullHist.filter { it.date <= "2000-02-15" }
        val dotComReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = dotComHist,
            universeHistories = mapOf(com.example.depthtank.data.TankIndexUniverse.NIFTY_500 to dotComHist),
            stocks = emptyList(),
            macroOverlays = macroOverlays.filterKeys { it <= "2000-02-15" }
        )
        assertNull(dotComReport.kinetics.peTankLevel)
        assertNull(dotComReport.kinetics.medianPe)
        assertNotNull("Feb 2000 report must carry genuine Official NSE Index P/E", dotComReport.kinetics.indexPe)
        assertTrue("Feb 2000 Official Index P/E must be > 28x (got ${dotComReport.kinetics.indexPe})", (dotComReport.kinetics.indexPe ?: 0.0) > 28.0)
        assertEquals("N/A", dotComReport.kinetics.valuationRegime)
        assertFalse(dotComReport.investorDirective.executiveRationale.contains("null", ignoreCase = true))
        assertFalse(dotComReport.pathProjection.expectedPathSummary.contains("null", ignoreCase = true))

        // 4. Pre-1999 date: both stock Val P/E and Official NSE Index P/E are strictly null / N/A
        val pre1999Hist = fullHist.filter { it.date <= "1997-06-16" }
        val pre1999Report = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = pre1999Hist,
            universeHistories = mapOf(com.example.depthtank.data.TankIndexUniverse.NIFTY_500 to pre1999Hist),
            stocks = emptyList(),
            macroOverlays = macroOverlays.filterKeys { it <= "1997-06-16" }
        )
        assertNull(pre1999Report.kinetics.peTankLevel)
        assertNull(pre1999Report.kinetics.medianPe)
        assertNull(pre1999Report.kinetics.indexPe)
        assertEquals("N/A", pre1999Report.kinetics.valuationRegime)
        assertEquals("N/A", pre1999Report.kinetics.absolutePeRegime)
    }

    @Test
    fun backfilledValuationPeEliminatesWeekdaySawtoothAcrossAllUniverses() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline"))
        }
        val repo = TankRepository(ctx, offlineSource)
        repo.ensureInitialized()
        assertTrue(repo.ensurePre2016Loaded().isSuccess)

        val n500 = repo.universeHistories.value[com.example.depthtank.data.TankIndexUniverse.NIFTY_500].orEmpty()
        val feb24 = n500.firstOrNull { it.date == "2026-02-24" }
        assertNotNull("Expected 2026-02-24 session in Nifty 500 history", feb24)
        assertTrue(
            "2026-02-24 must be fully backfilled (expected >= 445 stocks, got ${feb24?.peActiveCount})",
            (feb24?.peActiveCount ?: 0) >= 445
        )

        val gfcBar = n500.firstOrNull { it.date == "2008-10-24" }
        assertNotNull(gfcBar)
        assertTrue(
            "Oct 2008 GFC should carry expanded standalone-backfilled P/E coverage (>= 200, got ${gfcBar?.peActiveCount})",
            (gfcBar?.peActiveCount ?: 0) >= 200
        )

        val minFloorByUniverse = mapOf(
            com.example.depthtank.data.TankIndexUniverse.NIFTY_500 to 420,
            com.example.depthtank.data.TankIndexUniverse.NIFTY_50 to 46,
            com.example.depthtank.data.TankIndexUniverse.NEXT_50 to 42,
            com.example.depthtank.data.TankIndexUniverse.MIDCAP_150 to 125,
            com.example.depthtank.data.TankIndexUniverse.SMALLCAP_250 to 195
        )
        for ((universe, minFloor) in minFloorByUniverse) {
            val recentWindow = repo.universeHistories.value[universe].orEmpty()
                .filter { it.date in "2025-11-24".."2026-11-13" }
            assertTrue("Expected trading days in 2025-11..2026-11 for ${universe.shortLabel}", recentWindow.size >= 200)
            val lowest = recentWindow.minByOrNull { it.peActiveCount }!!
            assertTrue(
                "Zero weekday sawtooth allowed in ${universe.shortLabel}: lowest coverage on ${lowest.date} was ${lowest.peActiveCount} (floor $minFloor)",
                lowest.peActiveCount >= minFloor
            )
        }
    }

    @Test
    fun dualValuationIntegratesAbsoluteMedianPePercentileIntoRecommendations() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline"))
        }
        val repo = TankRepository(ctx, offlineSource)
        repo.ensureInitialized()
        assertTrue(repo.ensurePre2016Loaded().isSuccess)

        val fullHist = repo.history.value
        val fullUniverses = repo.universeHistories.value
        val macroOverlays = repo.macroOverlays.value
        val stocks = repo.stocks.value

        // 1. Live Nifty 500 report: Official Point-in-Time NSE Index P/E (~22.5x) resolves false 33.5x froth signal!
        val liveReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = fullHist,
            universeHistories = fullUniverses,
            stocks = stocks,
            macroOverlays = macroOverlays
        )
        val liveK = liveReport.kinetics
        assertTrue("Expected Official Point-in-Time NSE Index P/E benchmark", liveK.isOfficialIndexPeBenchmark)
        assertNotNull("Expected Official NSE Index P/E", liveK.indexPe)
        assertNotNull("Expected Official NSE Index P/B", liveK.indexPb)
        assertNotNull("Expected 20Y long-term Index P/E baseline", liveK.medianPeLongTermMedian)
        assertNotNull("Expected 20Y P25 Index P/E baseline", liveK.medianPeP25)
        assertNotNull("Expected 20Y P75 Index P/E baseline", liveK.medianPeP75)
        assertNotNull("Expected causal Official Index P/E 20Y percentile", liveK.medianPeHistoricalPercentile)
        assertNotNull("Expected post-2021 consolidated Index P/E percentile", liveK.indexPeConsolidatedPercentile)

        assertTrue("Nifty 500 Official Index P/E must be ~22.5x (got ${liveK.indexPe})", liveK.indexPe!! in 20.0..25.0)
        assertTrue("20Y Official Index P/E median for Nifty 500 must be ~22.9x (got ${liveK.medianPeLongTermMedian})",
            liveK.medianPeLongTermMedian!! in 20.0..25.5)
        assertTrue("Nifty 500 20Y Official Index P/E percentile must be reasonable (35..60th, got ${liveK.medianPeHistoricalPercentile})",
            liveK.medianPeHistoricalPercentile!! in 35..60)
        assertTrue("Nifty 500 post-2021 consolidated percentile must be attractive (<= 35th, got ${liveK.indexPeConsolidatedPercentile})",
            liveK.indexPeConsolidatedPercentile!! <= 35)
        assertTrue("Nifty 500 must classify as Attractive Value or Fair Value — NO false froth! (got ${liveK.absolutePeRegime})",
            liveK.absolutePeRegime == "Attractive Value" || liveK.absolutePeRegime == "Fair Value")
        assertTrue("Dual valuation verdict should reflect aligned fair-value pullback (got ${liveK.dualValuationVerdict})",
            liveK.dualValuationVerdict.contains("Fair-Value", ignoreCase = true) ||
                liveK.dualValuationVerdict.contains("Aligned", ignoreCase = true))
        assertTrue("Executive rationale should reference Official Index P/E and explain Equal-Wt Stock Median dispersion",
            liveReport.investorDirective.executiveRationale.contains("Index P/E") &&
                liveReport.investorDirective.executiveRationale.contains("Equal-Wt Stock Median"))

        // 2. Verify Nifty 50, Next 50, and Midcap 150 also resolve false froth under Official Point-in-Time NSE Index P/E
        for (u in listOf(
            com.example.depthtank.data.TankIndexUniverse.NIFTY_50,
            com.example.depthtank.data.TankIndexUniverse.NEXT_50,
            com.example.depthtank.data.TankIndexUniverse.MIDCAP_150
        )) {
            val uHist = fullUniverses[u].orEmpty()
            val uReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
                universe = u,
                history = uHist,
                universeHistories = fullUniverses,
                stocks = stocks,
                macroOverlays = macroOverlays
            )
            assertTrue("${u.shortLabel} must use Official NSE Index P/E benchmark", uReport.kinetics.isOfficialIndexPeBenchmark)
            assertTrue(
                "${u.shortLabel} must NOT produce false Expensive/Frothy signal (got ${uReport.kinetics.absolutePeRegime} at ${uReport.kinetics.indexPe}x)",
                uReport.kinetics.absolutePeRegime in setOf("Deep Value", "Attractive Value", "Fair Value")
            )
        }

        // 3. COVID Mar 2020 trough (P/B 2.09x, Index P/E 19.83x) & GFC Oct 2008 trough (Index P/E 9.29x): Deep Value!
        val covidHist = fullHist.filter { it.date <= "2020-03-24" }
        val covidReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = covidHist,
            universeHistories = mapOf(com.example.depthtank.data.TankIndexUniverse.NIFTY_500 to covidHist),
            stocks = emptyList(),
            macroOverlays = macroOverlays.filterKeys { it <= "2020-03-24" }
        )
        assertEquals("Deep Value", covidReport.kinetics.absolutePeRegime)
        assertTrue("COVID dual valuation verdict should recognize Generational Deep Value",
            covidReport.kinetics.dualValuationVerdict.contains("Deep Value", ignoreCase = true) ||
                covidReport.kinetics.dualValuationVerdict.contains("Generational", ignoreCase = true))

        val gfcHist = fullHist.filter { it.date <= "2008-10-27" }
        val gfcReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_500,
            history = gfcHist,
            universeHistories = mapOf(com.example.depthtank.data.TankIndexUniverse.NIFTY_500 to gfcHist),
            stocks = emptyList(),
            macroOverlays = macroOverlays.filterKeys { it <= "2008-10-27" }
        )
        assertEquals("Deep Value", gfcReport.kinetics.absolutePeRegime)
        assertTrue("GFC Oct 2008 Official Index P/E must be deep single/low-double digits (<12x, got ${gfcReport.kinetics.indexPe})",
            (gfcReport.kinetics.indexPe ?: 99.0) < 12.0)
    }

    @Test
    fun pointInTimeConstituentsUpgradeNifty50AndNext50HistoricalCoverage() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val offlineSource = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> =
                Result.failure(IllegalStateException("offline"))

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("offline"))
        }
        val repo = TankRepository(ctx, offlineSource)
        repo.ensureInitialized()
        assertTrue(repo.ensurePre2016Loaded().isSuccess)

        val n50 = repo.universeHistories.value[com.example.depthtank.data.TankIndexUniverse.NIFTY_50].orEmpty()
        val nn50 = repo.universeHistories.value[com.example.depthtank.data.TankIndexUniverse.NEXT_50].orEmpty()

        // Verify point-in-time constituent rebuild boosted 2008-2020 stock coverage for Nifty 50 & Next 50
        val n50_2008 = n50.first { it.date == "2008-10-24" }
        val n50_2015 = n50.first { it.date == "2015-06-26" }
        assertTrue("Point-in-time Nifty 50 Oct 2008 must cover >= 38/50 stocks (got ${n50_2008.peActiveCount})", n50_2008.peActiveCount >= 38)
        assertTrue("Point-in-time Nifty 50 Jun 2015 must cover >= 42/50 stocks (got ${n50_2015.peActiveCount})", n50_2015.peActiveCount >= 42)
        assertNotNull("Nifty 50 Oct 2008 must have Official NSE Index P/E", n50_2008.indexPe)

        val nn50_2008 = nn50.first { it.date == "2008-10-24" }
        val nn50_2015 = nn50.first { it.date == "2015-06-26" }
        val nn50_2018 = nn50.first { it.date == "2018-08-31" }
        assertTrue("Point-in-time Next 50 Oct 2008 must cover >= 29/50 stocks (got ${nn50_2008.peActiveCount})", nn50_2008.peActiveCount >= 29)
        assertTrue("Point-in-time Next 50 Jun 2015 must cover >= 42/50 stocks (got ${nn50_2015.peActiveCount})", nn50_2015.peActiveCount >= 42)
        assertTrue("Point-in-time Next 50 Aug 2018 must cover >= 42/50 stocks (got ${nn50_2018.peActiveCount})", nn50_2018.peActiveCount >= 42)
        assertNotNull("Next 50 Oct 2008 must have Official NSE Index P/E", nn50_2008.indexPe)

        // Confirm report flags point-in-time constituent P/E tank for Nifty 50 & Next 50
        val n50Report = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = com.example.depthtank.data.TankIndexUniverse.NIFTY_50,
            history = n50,
            universeHistories = repo.universeHistories.value,
            stocks = repo.stocks.value,
            macroOverlays = repo.macroOverlays.value
        )
        assertTrue("Nifty 50 report must flag isPointInTimeConstituentPeTank = true", n50Report.kinetics.isPointInTimeConstituentPeTank)
    }
}


