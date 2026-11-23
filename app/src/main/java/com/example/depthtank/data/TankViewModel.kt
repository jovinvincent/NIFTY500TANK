package com.example.depthtank.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class TankUiState(
    val isLoading: Boolean = true,
    val currentTankLevel: Double = 0.0,
    val dailyDelta: Double = 0.0,
    val regime: MarketRegime = MarketRegime.NEUTRAL,
    val flowingCount: Int = 0,
    val highPressureCount: Int = 0,
    val belowHoleCount: Int = 0,
    val activeStocksCount: Int = 0,
    val avgStockScore: Double = 0.0,
    val fullHistory: List<TankHistoryPoint> = emptyList(),
    val filteredHistory: List<TankHistoryPoint> = emptyList(),
    val selectedTimeframe: TimeframeOption = TimeframeOption.Y1,
    val allStocks: List<StockTank> = emptyList(),
    val filteredStocks: List<StockTank> = emptyList(),
    val availableSectors: List<String> = listOf("All Sectors"),
    val searchQuery: String = "",
    val selectedSector: String = "All Sectors",
    val selectedFlowFilter: FlowFilter = FlowFilter.ALL,
    val sectorSummaries: List<SectorTankSummary> = emptyList(),
    val pipelineStatus: SyncPipelineStatus = SyncPipelineStatus(),
    val selectedStockForDetail: StockTank? = null,
    val isInfoModalVisible: Boolean = false,
    val holeThreshold: Float = 50f,
    val fourPillarSnapshot: FourPillarSnapshot = FourPillarSnapshot(),
    val institutionalRegime: InstitutionalRegime = InstitutionalRegime.ACCUMULATION,
    val datasetIntegrity: DatasetIntegrityMetadata = DatasetIntegrityMetadata(),
    val historyIntegrity: DatasetIntegrityMetadata = DatasetIntegrityMetadata(),
    val detectedCycles: List<MarketCycleTurningPoint> = emptyList(),
    val liveSignal: CausalLiveSignal? = null,
    val selectedUniverse: TankIndexUniverse = TankIndexUniverse.NIFTY_500,
    val cycleReport: DeadlyComboReport = DeadlyComboReport(),
    val selectedAggregation: ChartAggregation = ChartAggregation.DAILY,
    val archiveHydrationState: ArchiveHydrationState = ArchiveHydrationState.NOT_LOADED,
    val archiveSummary: ArchiveProvenanceSummary = ArchiveProvenanceSummary(),
    val currentPeTankLevel: Double? = null,
    val currentMedianPe: Double? = null,
    val currentIndexPe: Double? = null,
    val currentIndexPb: Double? = null,
    val currentIndexDivYield: Double? = null,
    val peActiveStocksCount: Int = 0
)

class TankViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TankRepository(application)

    private val selectedUniverse = MutableStateFlow(TankIndexUniverse.NIFTY_500)
    private val selectedTimeframe = MutableStateFlow(TimeframeOption.Y1)
    private val selectedAggregation = MutableStateFlow(ChartAggregation.DAILY)
    private val searchQuery = MutableStateFlow("")
    private val selectedSector = MutableStateFlow("All Sectors")
    private val selectedFlowFilter = MutableStateFlow(FlowFilter.ALL)
    private val selectedStock = MutableStateFlow<StockTank?>(null)
    private val showInfoModal = MutableStateFlow(false)
    private val holeThreshold = MutableStateFlow(50f)

    private var isForegroundActive: Boolean = true
    private var lastLiveRefreshMs: Long = 0L
    private var marketHoursPollJob: Job? = null

    private data class FilterState(
        val universe: TankIndexUniverse,
        val timeframe: TimeframeOption,
        val aggregation: ChartAggregation,
        val query: String,
        val sector: String,
        val flowFilter: FlowFilter,
        val detailStock: StockTank?,
        val infoVisible: Boolean,
        val holeThreshold: Float
    )

    private data class UniverseDataBundle(
        val constituents: Map<TankIndexUniverse, Set<String>>,
        val histories: Map<TankIndexUniverse, List<TankHistoryPoint>>,
        val macroOverlays: Map<String, MacroOverlayPoint>,
        val archiveHydrationState: ArchiveHydrationState,
        val archiveSummary: ArchiveProvenanceSummary
    )

    private data class EngineBundle(
        val fourPillars: FourPillarSnapshot,
        val liveIntegrity: DatasetIntegrityMetadata,
        val historyIntegrity: DatasetIntegrityMetadata,
        val cycles: List<MarketCycleTurningPoint>,
        val liveSignal: CausalLiveSignal?,
        val universeConstituents: Map<TankIndexUniverse, Set<String>>,
        val universeHistories: Map<TankIndexUniverse, List<TankHistoryPoint>>,
        val macroOverlays: Map<String, MacroOverlayPoint>,
        val archiveHydrationState: ArchiveHydrationState,
        val archiveSummary: ArchiveProvenanceSummary
    )

    private val filterFlow = combine(
        selectedUniverse,
        selectedTimeframe,
        selectedAggregation,
        searchQuery,
        selectedSector,
        selectedFlowFilter,
        selectedStock,
        showInfoModal,
        holeThreshold
    ) { values ->
        FilterState(
            universe = values[0] as TankIndexUniverse,
            timeframe = values[1] as TimeframeOption,
            aggregation = values[2] as ChartAggregation,
            query = values[3] as String,
            sector = values[4] as String,
            flowFilter = values[5] as FlowFilter,
            detailStock = values[6] as StockTank?,
            infoVisible = values[7] as Boolean,
            holeThreshold = values[8] as Float
        )
    }

    private val universeDataFlow = combine(
        repository.universeConstituents,
        repository.universeHistories,
        repository.macroOverlays,
        repository.archiveHydrationState,
        repository.archiveSummary
    ) { constituents, histories, overlays, archiveState, archiveSummary ->
        UniverseDataBundle(
            constituents = constituents,
            histories = histories,
            macroOverlays = overlays,
            archiveHydrationState = archiveState,
            archiveSummary = archiveSummary
        )
    }

    private val engineFlow = combine(
        repository.fourPillarSnapshot,
        repository.datasetIntegrity,
        repository.historyIntegrity,
        repository.detectedCycles,
        universeDataFlow
    ) { fourPillars, liveIntegrity, histIntegrity, cycles, universeData ->
        EngineBundle(
            fourPillars = fourPillars,
            liveIntegrity = liveIntegrity,
            historyIntegrity = histIntegrity,
            cycles = cycles,
            liveSignal = null,
            universeConstituents = universeData.constituents,
            universeHistories = universeData.histories,
            macroOverlays = universeData.macroOverlays,
            archiveHydrationState = universeData.archiveHydrationState,
            archiveSummary = universeData.archiveSummary
        )
    }

    val uiState: StateFlow<TankUiState> = combine(
        repository.stocks,
        repository.history,
        repository.pipelineStatus,
        filterFlow,
        engineFlow
    ) { stocks, history, pipeline, filters, engine ->
        val allEligibleStocks = TankCalculator.filterEligibleConstituents(stocks)
        val universe = filters.universe
        val eligibleStocks = if (universe == TankIndexUniverse.NIFTY_500) {
            allEligibleStocks
        } else {
            val allowedSymbols = engine.universeConstituents[universe].orEmpty()
            if (allowedSymbols.isNotEmpty()) {
                allEligibleStocks.filter { it.symbol in allowedSymbols }
            } else {
                emptyList()
            }
        }
        val universeHistory = engine.universeHistories[universe]?.takeIf { it.isNotEmpty() } ?: history

        val h = filters.holeThreshold.toDouble()
        val isDefaultHole = kotlin.math.abs(filters.holeThreshold - 50f) < 0.001f

        val simulatedHistory = if (isDefaultHole) {
            universeHistory
        } else {
            TankCalculator.simulateHistory(
                history = universeHistory,
                holeThreshold = h,
                liveStocks = eligibleStocks,
                liveSessionDate = pipeline.lastTradeDate,
                targetConstituents = universe.targetCount
            )
        }

        val latestPoint = simulatedHistory.lastOrNull()
        val computedTankLevel = if (eligibleStocks.isNotEmpty()) {
            TankCalculator.computeMainTankLevel(eligibleStocks, h)
        } else {
            latestPoint?.tankLevel ?: 0.0
        }

        val prevLevel = if (simulatedHistory.size >= 2) {
            simulatedHistory[simulatedHistory.size - 2].tankLevel
        } else {
            computedTankLevel
        }
        val dailyDelta = if (simulatedHistory.isNotEmpty()) {
            kotlin.math.round((computedTankLevel - prevLevel) * 100.0) / 100.0
        } else {
            latestPoint?.dailyDelta ?: 0.0
        }

        val flowingCount = eligibleStocks.count { it.score > h }
        val highPressureCount = eligibleStocks.count { it.score > 85.0 }
        val belowHoleCount = eligibleStocks.count { it.score <= h }
        val avgScore = if (eligibleStocks.isNotEmpty()) eligibleStocks.map { it.score }.average() else (latestPoint?.avgScore ?: 0.0)

        val eligiblePeStocks = TankCalculator.filterEligiblePeConstituents(eligibleStocks)
        val computedPeTankLevel = if (eligiblePeStocks.isNotEmpty()) {
            TankCalculator.computePeTankLevel(eligibleStocks, h)
        } else {
            latestPoint?.peTankLevel
        }
        val computedMedianPe = TankCalculator.computeMedianPe(eligibleStocks) ?: latestPoint?.medianPe
        val peActiveStocksCount = if (eligiblePeStocks.isNotEmpty()) eligiblePeStocks.size else (latestPoint?.peActiveCount ?: 0)
        val latestOfficialPePt = universeHistory.lastOrNull { it.hasOfficialIndexPe }
        val computedIndexPe = latestPoint?.indexPe ?: latestOfficialPePt?.indexPe
        val computedIndexPb = latestPoint?.indexPb ?: latestOfficialPePt?.indexPb
        val computedIndexDivYield = latestPoint?.indexDivYield ?: latestOfficialPePt?.indexDivYield

        val trimmedDailyHistory = if (filters.timeframe == TimeframeOption.MAX || simulatedHistory.size <= filters.timeframe.tradingDays) {
            simulatedHistory
        } else {
            simulatedHistory.takeLast(filters.timeframe.tradingDays)
        }
        val aggregatedHistory = TankHistoryAggregator.aggregate(trimmedDailyHistory, filters.aggregation)

        val sectors = listOf("All Sectors") + listOf(
            "Banking", "IT", "Auto", "Pharma", "Energy", "FMCG", "Metals", "Infra & Defence", "Consumer & Tech"
        ).filter { sec -> eligibleStocks.any { it.sector == sec } }

        val effectiveSector = if (filters.sector in sectors) filters.sector else "All Sectors"
        val cleanQuery = filters.query.trim()
        val filteredStocks = eligibleStocks.filter { stock ->
            val matchesQuery = cleanQuery.isEmpty() ||
                stock.symbol.contains(cleanQuery, ignoreCase = true) ||
                stock.name.contains(cleanQuery, ignoreCase = true)
            val matchesSector = effectiveSector == "All Sectors" || stock.sector == effectiveSector
            val matchesFlow = when (filters.flowFilter) {
                FlowFilter.ALL -> true
                FlowFilter.FLOWING -> stock.score > h
                FlowFilter.HIGH_PRESSURE -> stock.isHighPressure
                FlowFilter.BELOW_HOLE -> stock.score <= h
            }
            matchesQuery && matchesSector && matchesFlow
        }

        val cycleStartIso = if (simulatedHistory.firstOrNull()?.date?.let { it < TankRepository.PRE2016_BOUNDARY_DATE } == true) {
            "1995-01-01"
        } else {
            "2016-04-01"
        }
        val activeCycles = if (isDefaultHole) {
            if (universe == TankIndexUniverse.NIFTY_500) {
                engine.cycles
            } else {
                CycleTurningPointEngine.detectCyclesRetrospective(
                    simulatedHistory,
                    CycleDetectionConfig(startIsoDate = cycleStartIso)
                )
            }
        } else {
            CycleTurningPointEngine.detectCyclesForHole(simulatedHistory, h, startIsoDate = cycleStartIso)
        }

        val causalForUniverse = CycleTurningPointEngine.computeCausalSignalAt(
            historyPrefix = universeHistory,
            liveStocks = eligibleStocks
        )
        val baseFourPillars = causalForUniverse?.fourPillars
            ?: if (universe == TankIndexUniverse.NIFTY_500) {
                engine.fourPillars
            } else {
                TankCalculator.computeFourPillarSnapshot(eligibleStocks, universeHistory, latestPoint?.date ?: pipeline.lastTradeDate)
            }

        val overheatedLine = TankCalculator.overheatedThresholdForHole(h)
        val activeFourPillars = if (isDefaultHole) {
            baseFourPillars
        } else {
            val roundedMain = kotlin.math.round(computedTankLevel * 100.0) / 100.0
            val simPlateau = TankCalculator.computePlateauDays(simulatedHistory, overheatedLine)
            val simRegime = TankCalculator.classifyInstitutionalRegime(
                tankLevel = roundedMain,
                dailyDelta = dailyDelta,
                plateauDays = simPlateau,
                euphoriaPressurePct = baseFourPillars.euphoriaPressurePct,
                breakoutPulsePct = baseFourPillars.breakoutPulsePct,
                breakoutPulseDelta20 = baseFourPillars.breakoutPulseDelta20,
                capitulationDepthPct = baseFourPillars.capitulationDepthPct
            )
            baseFourPillars.copy(
                mainTankLevelPct = roundedMain,
                mainTankDailyDeltaPct = dailyDelta,
                isOverheatedThresholdBreached = roundedMain > overheatedLine,
                plateauDays = simPlateau,
                regime = simRegime,
                hasHiddenBearishDivergence = roundedMain >= overheatedLine &&
                    simPlateau >= 5 &&
                    baseFourPillars.breakoutPulseDelta20 <= -2.5
            )
        }

        val activeCountResolved = if (eligibleStocks.isNotEmpty()) eligibleStocks.size else (latestPoint?.activeCount ?: universe.targetCount)
        val universeCoverage = kotlin.math.round(
            (activeCountResolved.toDouble() / universe.targetCount.toDouble()).coerceIn(0.0, 1.0) * 10000.0
        ) / 10000.0
        val universeIntegrity = engine.liveIntegrity.copy(
            constituentCoverage = universeCoverage
        )
        val universePipeline = pipeline.copy(
            trackedStocksCount = activeCountResolved,
            integrity = universeIntegrity
        )

        val comboReport = DeadlyComboEngine.generateReport(
            universe = universe,
            history = universeHistory,
            universeHistories = engine.universeHistories,
            stocks = eligibleStocks,
            macroOverlays = engine.macroOverlays
        )

        TankUiState(
            isLoading = allEligibleStocks.isEmpty() && history.isEmpty(),
            currentTankLevel = computedTankLevel,
            dailyDelta = dailyDelta,
            regime = TankCalculator.classifyRegime(computedTankLevel),
            flowingCount = flowingCount,
            highPressureCount = highPressureCount,
            belowHoleCount = belowHoleCount,
            activeStocksCount = activeCountResolved,
            avgStockScore = avgScore,
            fullHistory = simulatedHistory,
            filteredHistory = aggregatedHistory,
            selectedTimeframe = filters.timeframe,
            allStocks = eligibleStocks,
            filteredStocks = filteredStocks,
            availableSectors = sectors,
            searchQuery = filters.query,
            selectedSector = effectiveSector,
            selectedFlowFilter = filters.flowFilter,
            sectorSummaries = TankCalculator.buildSectorSummaries(eligibleStocks, h),
            pipelineStatus = universePipeline,
            selectedStockForDetail = filters.detailStock?.takeIf { sel ->
                eligibleStocks.any { it.symbol == sel.symbol }
            },
            isInfoModalVisible = filters.infoVisible,
            holeThreshold = filters.holeThreshold,
            fourPillarSnapshot = activeFourPillars,
            institutionalRegime = activeFourPillars.regime,
            datasetIntegrity = universeIntegrity,
            historyIntegrity = engine.historyIntegrity,
            detectedCycles = activeCycles,
            liveSignal = causalForUniverse,
            selectedUniverse = universe,
            cycleReport = comboReport,
            selectedAggregation = filters.aggregation,
            archiveHydrationState = engine.archiveHydrationState,
            archiveSummary = engine.archiveSummary,
            currentPeTankLevel = computedPeTankLevel,
            currentMedianPe = computedMedianPe,
            currentIndexPe = computedIndexPe,
            currentIndexPb = computedIndexPb,
            currentIndexDivYield = computedIndexDivYield,
            peActiveStocksCount = peActiveStocksCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TankUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
            TankDailySyncWorker.scheduleNextWeekdayEodSync(getApplication())
            launch {
                delay(400L)
                repository.ensurePre2016Loaded()
            }
            repository.refreshLiveMarketData(silent = false).onSuccess {
                lastLiveRefreshMs = System.currentTimeMillis()
            }
            startMarketHoursPolling()
        }
    }

    fun setForegroundActive(active: Boolean) {
        isForegroundActive = active
        if (active) {
            startMarketHoursPolling()
            val nowMs = System.currentTimeMillis()
            val nowIst = ZonedDateTime.now(NseMarketCalendar.IST_ZONE)
            val expectedSession = NseMarketCalendar.latestMarketSessionDate(nowIst).toString()
            val currentSession = repository.pipelineStatus.value.lastTradeDate
            val hasLiveMacro = repository.macroOverlays.value[expectedSession]?.adPctByUniverse?.isNotEmpty() == true
            val isStale = (nowMs - lastLiveRefreshMs) >= LIVE_POLL_INTERVAL_MS
            val needsSync = NseMarketCalendar.isMarketOpen(nowIst) ||
                NseMarketCalendar.isPostCloseSettlementWindow(nowIst) ||
                currentSession < expectedSession ||
                !repository.pipelineStatus.value.isLiveHydrated ||
                !hasLiveMacro
            if (needsSync && isStale) {
                viewModelScope.launch {
                    repository.refreshLiveMarketData(silent = true).onSuccess {
                        lastLiveRefreshMs = System.currentTimeMillis()
                    }
                }
            }
        } else {
            marketHoursPollJob?.cancel()
            marketHoursPollJob = null
        }
    }

    private fun startMarketHoursPolling() {
        if (marketHoursPollJob?.isActive == true) return
        marketHoursPollJob = viewModelScope.launch {
            while (isActive) {
                delay(LIVE_POLL_INTERVAL_MS)
                if (!isForegroundActive) continue
                val nowIst = ZonedDateTime.now(NseMarketCalendar.IST_ZONE)
                val expectedSession = NseMarketCalendar.latestMarketSessionDate(nowIst).toString()
                val needsEodSync = NseMarketCalendar.isPostCloseSettlementWindow(nowIst) &&
                    (System.currentTimeMillis() - lastLiveRefreshMs) >= EOD_SETTLEMENT_POLL_MS
                val behindSession = repository.pipelineStatus.value.lastTradeDate < expectedSession
                val missingMacro = repository.macroOverlays.value[expectedSession]?.adPctByUniverse?.isNotEmpty() != true
                if (NseMarketCalendar.isMarketOpen(nowIst) || needsEodSync || behindSession || missingMacro) {
                    repository.refreshLiveMarketData(silent = true).onSuccess {
                        lastLiveRefreshMs = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    companion object {
        private const val LIVE_POLL_INTERVAL_MS = 30_000L
        private const val EOD_SETTLEMENT_POLL_MS = 180_000L
    }

    fun selectUniverse(universe: TankIndexUniverse) {
        if (selectedUniverse.value != universe) {
            selectedUniverse.value = universe
            selectedStock.value = null
            selectedSector.value = "All Sectors"
        }
    }

    fun selectTimeframe(option: TimeframeOption) {
        selectedTimeframe.value = option
        if (option.requiresPre2016Archive && repository.archiveHydrationState.value != ArchiveHydrationState.READY) {
            viewModelScope.launch {
                repository.ensurePre2016Loaded()
            }
        }
    }

    fun selectAggregation(mode: ChartAggregation) {
        if (selectedAggregation.value != mode) {
            selectedAggregation.value = mode
        }
    }

    fun retryPre2016Archive() {
        viewModelScope.launch {
            repository.ensurePre2016Loaded(forceRebuildCache = true)
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectSector(sector: String) {
        selectedSector.value = sector
    }

    fun selectFlowFilter(filter: FlowFilter) {
        selectedFlowFilter.value = filter
    }

    fun openStockDetail(stock: StockTank) {
        selectedStock.value = stock
    }

    fun closeStockDetail() {
        selectedStock.value = null
    }

    fun setInfoModalVisible(visible: Boolean) {
        showInfoModal.value = visible
    }

    fun updateHoleThreshold(threshold: Float) {
        val rounded = kotlin.math.round(threshold.coerceIn(0f, 100f))
        if (holeThreshold.value != rounded) {
            holeThreshold.value = rounded
        }
    }

    fun resetHoleThreshold() {
        holeThreshold.value = 50f
    }

    fun forceRefresh() {
        viewModelScope.launch {
            repository.refreshLiveMarketData(silent = false).onSuccess {
                lastLiveRefreshMs = System.currentTimeMillis()
            }
        }
    }
}
