package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.depthtank.data.DataQuality
import com.example.depthtank.data.DatasetIntegrityMetadata
import com.example.depthtank.data.DeadlyComboReport
import com.example.depthtank.data.FlowFilter
import com.example.depthtank.data.FourPillarSnapshot
import com.example.depthtank.data.InstitutionalRegime
import com.example.depthtank.data.InvestorActionDirective
import com.example.depthtank.data.InvestorActionStage
import com.example.depthtank.data.KineticTelemetry
import com.example.depthtank.data.MarketCycleTurningPoint
import com.example.depthtank.data.MarketPathProjection
import com.example.depthtank.data.MarketRegime
import com.example.depthtank.data.MultiHoleReading
import com.example.depthtank.data.SectorTankSummary
import com.example.depthtank.data.StockTank
import com.example.depthtank.data.SyncPipelineStatus
import com.example.depthtank.data.TankHistoryPoint
import com.example.depthtank.data.TankIndexUniverse
import com.example.depthtank.data.TankUiState
import com.example.depthtank.data.TimeframeOption
import com.example.depthtank.ui.CycleReportScreen
import com.example.depthtank.ui.DepthTankRootContent
import com.example.ui.theme.NiftyDepthTankTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class Phase2UiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildSampleState(
        mainTankPct: Double = 78.4,
        euphoriaPct: Double = 64.2,
        breakoutPct: Double = 9.6,
        breakoutDelta20: Double = 1.8,
        regime: InstitutionalRegime = InstitutionalRegime.ELEVATED_EUPHORIA,
        hiddenDivergence: Boolean = false
    ): TankUiState {
        val sampleStocks = listOf(
            StockTank(
                symbol = "HDFCBANK",
                name = "HDFC Bank Ltd.",
                sector = "Banking",
                currentPrice = 1795.5,
                low52W = 1363.55,
                high52W = 1825.0,
                dailyChangePct = 0.92,
                todayHigh = 1830.0,
                prior52WHigh = 1820.0
            ),
            StockTank(
                symbol = "TCS",
                name = "Tata Consultancy Services",
                sector = "IT",
                currentPrice = 4385.75,
                low52W = 3591.5,
                high52W = 4592.25,
                dailyChangePct = 0.72
            ),
            StockTank(
                symbol = "TATAMOTORS",
                name = "Tata Motors Ltd.",
                sector = "Auto",
                currentPrice = 770.0,
                low52W = 762.5,
                high52W = 1179.0,
                dailyChangePct = -1.18
            )
        )
        val sampleHistory = listOf(
            TankHistoryPoint("2016-04-01", 36.57, 0.0, 230, 296, 54, 65.6, 18.2, 12, 4.1, 0.0, 28, 9.5, 0),
            TankHistoryPoint("2020-03-23", 5.84, -3.2, 85, 500, 2, 31.2, 0.4, 1, 0.2, -5.0, 260, 52.0, 0),
            TankHistoryPoint("2024-09-26", 81.19, 0.65, 494, 500, 352, 89.8, 70.4, 58, 11.6, 2.1, 3, 0.6, 14),
            TankHistoryPoint("2026-11-13", mainTankPct, 0.42, 462, 500, 321, 86.1, euphoriaPct, 48, breakoutPct, breakoutDelta20, 6, 1.2, 8)
        )
        val sampleCycle = MarketCycleTurningPoint(
            cycleIndex = 1,
            exactPeakDate = "2024-09-26",
            peakMainTankPct = 81.19,
            peakEuphoriaPct = 70.4,
            exactBottomDate = "2020-03-23",
            bottomMainTankPct = 5.84,
            bottomCapitulationPct = 52.0,
            drawdownPctPoints = 75.35,
            daysPeakToBottom = 42,
            calendarDaysPeakToBottom = 60,
            recoveryDate = "2020-11-09",
            recoveryDurationDays = 158,
            calendarDaysBottomToRecovery = 231,
            recoveryLevelPct = 61.5,
            recoveryFromBottomPctPoints = 55.66,
            recoveryRetracementPct = 73.8,
            isRecovered = true
        )

        return TankUiState(
            isLoading = false,
            currentTankLevel = mainTankPct,
            dailyDelta = 0.42,
            regime = MarketRegime.OVERHEATED,
            flowingCount = 462,
            highPressureCount = 321,
            belowHoleCount = 38,
            activeStocksCount = 500,
            avgStockScore = 86.1,
            fullHistory = sampleHistory,
            filteredHistory = sampleHistory,
            selectedTimeframe = TimeframeOption.MAX,
            allStocks = sampleStocks,
            filteredStocks = sampleStocks,
            availableSectors = listOf("All Sectors", "Banking", "IT", "Auto"),
            selectedFlowFilter = FlowFilter.ALL,
            sectorSummaries = listOf(
                SectorTankSummary("Banking", 8, 7, 81.2, 249.6, 38.5),
                SectorTankSummary("IT", 7, 7, 88.4, 268.8, 41.2)
            ),
            pipelineStatus = SyncPipelineStatus(
                lastSyncedDisplay = "Auto-Synced: Today 4:15 PM IST",
                lastTradeDate = "2026-11-13",
                trackedStocksCount = 500,
                isLiveHydrated = true
            ),
            fourPillarSnapshot = FourPillarSnapshot(
                sessionDate = "2026-11-13",
                mainTankLevelPct = mainTankPct,
                mainTankDailyDeltaPct = 0.42,
                isOverheatedThresholdBreached = mainTankPct > 75.0,
                plateauDays = 8,
                euphoriaPressurePct = euphoriaPct,
                euphoriaPressureCount = 321,
                breakoutPulsePct = breakoutPct,
                breakoutPulseCount = 48,
                breakoutPulseDelta20 = breakoutDelta20,
                capitulationDepthPct = 1.2,
                capitulationCount = 6,
                activeEligibleStocksCount = 500,
                excludedInsufficientHistoryCount = 3,
                regime = regime,
                hasHiddenBearishDivergence = hiddenDivergence
            ),
            institutionalRegime = regime,
            datasetIntegrity = DatasetIntegrityMetadata(
                dataQuality = DataQuality.VERIFIED_EOD,
                constituentCoverage = 1.0,
                isIllustrative = false,
                lastVerifiedTradingDate = "2026-11-13",
                excludedInsufficientHistoryCount = 3
            ),
            historyIntegrity = DatasetIntegrityMetadata(
                dataQuality = DataQuality.ILLUSTRATIVE_FALLBACK,
                constituentCoverage = 0.994,
                isIllustrative = true,
                lastVerifiedTradingDate = "2026-11-13"
            ),
            detectedCycles = listOf(sampleCycle),
            cycleReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
                universe = TankIndexUniverse.NIFTY_500,
                history = sampleHistory,
                universeHistories = emptyMap(),
                stocks = sampleStocks
            )
        )
    }

    @Test
    fun dashboard_rendersFourPillars_andSuppressesWarningWithoutDivergence() {
        val state = buildSampleState(
            mainTankPct = 79.2,
            euphoriaPct = 64.2,
            breakoutDelta20 = 2.1,
            regime = InstitutionalRegime.ELEVATED_EUPHORIA,
            hiddenDivergence = false
        )

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = state,
                    onSelectTimeframe = {},
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Nifty 500 Depth Tank").assertIsDisplayed()
        assert(composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty())
        assert(TankUiState().selectedTimeframe == TimeframeOption.Y1)
        composeTestRule.onNodeWithTag("dashboard_regime_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("euphoria_vertical_gauge").assertIsDisplayed()

        // Must NOT trigger deterioration warning purely because Main Tank > 75%
        assert(composeTestRule.onAllNodesWithTag("breadth_deterioration_banner").fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("four_pillar_grid"))
        composeTestRule.onNodeWithTag("four_pillar_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pillar_card_main_tank").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pillar_card_euphoria").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pillar_card_breakout_pulse").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pillar_card_capitulation").assertIsDisplayed()
    }

    @Test
    fun dashboard_showsBreadthDeteriorationBanner_whenCausalDivergenceTriggers() {
        val state = buildSampleState(
            mainTankPct = 78.5,
            euphoriaPct = 76.0,
            breakoutDelta20 = -4.2,
            regime = InstitutionalRegime.MOMENTUM_EXHAUSTION,
            hiddenDivergence = true
        )

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = state,
                    onSelectTimeframe = {},
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("breadth_deterioration_banner"))
        composeTestRule.onNodeWithTag("breadth_deterioration_banner").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("chart_ema_smooth_toggle"))
        composeTestRule.onNodeWithTag("series_toggle_euphoria").performClick()
        composeTestRule.onNodeWithTag("chart_ema_smooth_toggle").performClick()
        composeTestRule.onNodeWithTag("chart_ema_smooth_toggle").assertTextContains("EMA Smooth: ON")

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("historical_cycles_toggle_button"))
        composeTestRule.onNodeWithTag("historical_cycles_toggle_button").performClick()

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("cycle_entry_1"))
        composeTestRule.onNodeWithTag("cycle_entry_1").assertIsDisplayed()
    }

    @Test
    fun navigationTabs_switchCleanlyAndDisplay52WeekTerminology() {
        val state = buildSampleState()

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = state,
                    onSelectTimeframe = {},
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("nav_tab_tanks").performClick()
        composeTestRule.onNodeWithTag("stock_tanks_explorer_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stock_tank_card_HDFCBANK").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_analytics").performClick()
        composeTestRule.onNodeWithTag("analytics_sync_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("breadth_distribution_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("analytics_sync_screen")
            .performScrollToNode(hasTestTag("breakout_pulse_attribution_card"))
        composeTestRule.onNodeWithTag("breakout_pulse_attribution_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_guide").performClick()
        composeTestRule.onNodeWithTag("philosophy_guide_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Philosophy & 4-Pillar Guide").assertIsDisplayed()
    }

    @Test
    fun holePositionPresets_dynamicallyUpdateChartAndExplorerLabels() {
        val stateAt75 = buildSampleState(mainTankPct = 75.25).copy(
            holeThreshold = 75f,
            flowingCount = 310,
            belowHoleCount = 190
        )

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = stateAt75,
                    onSelectTimeframe = {},
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("historical_depth_chart_card"))
        composeTestRule.onNodeWithText("Hole: 75%").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_tanks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("flow_filter_chip_flowing")
            .assertTextContains("Flowing (>75%)", substring = true)
    }

    @Test
    fun universeDropdown_listsAllIndicesAndUpdatesAllFiveTabs() {
        val base = buildSampleState()
        val selectedState = androidx.compose.runtime.mutableStateOf(base)

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = selectedState.value,
                    onSelectUniverse = { picked ->
                        selectedState.value = selectedState.value.copy(
                            selectedUniverse = picked,
                            activeStocksCount = picked.targetCount,
                            flowingCount = picked.targetCount / 2
                        )
                    },
                    onSelectTimeframe = {},
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }

        // Default is Nifty 500 Depth Tank
        composeTestRule.onNodeWithTag("universe_dropdown_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nifty 500 Depth Tank").assertIsDisplayed()

        // Open dropdown & verify all 5 index universes appear
        composeTestRule.onNodeWithTag("universe_dropdown_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("universe_option_nifty_50").assertIsDisplayed()
        composeTestRule.onNodeWithTag("universe_option_next_50").assertIsDisplayed()
        composeTestRule.onNodeWithTag("universe_option_midcap_150").assertIsDisplayed()
        composeTestRule.onNodeWithTag("universe_option_smallcap_250").assertIsDisplayed()

        // Select Smallcap 250
        composeTestRule.onNodeWithTag("universe_option_smallcap_250").performClick()
        composeTestRule.waitForIdle()

        // 1. Dashboard reflects Smallcap 250 (250 Base)
        composeTestRule.onNodeWithText("Smallcap 250 Depth Tank").assertIsDisplayed()
        composeTestRule.onNodeWithText("125 / 250 pouring", substring = true).assertIsDisplayed()

        // 2. Extremes reflects Smallcap 250 (250 Base)
        composeTestRule.onNodeWithTag("nav_tab_extremes").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Smallcap 250 (250 Base)", substring = true).assertIsDisplayed()

        // 3. Tanks tab & screen reflect 250 base
        composeTestRule.onNodeWithTag("nav_tab_tanks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Smallcap 250 Mini Tanks").assertIsDisplayed()

        // 4. Analytics reflects Smallcap 250
        composeTestRule.onNodeWithTag("nav_tab_analytics").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Smallcap 250 (250 Base)", substring = true).assertIsDisplayed()

        // 5. Guide reflects Smallcap 250
        composeTestRule.onNodeWithTag("nav_tab_guide").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Smallcap 250 (250 Base)", substring = true).assertIsDisplayed()

        // 6. Report reflects Smallcap 250, Investor Action & Tax Playbook, Trajectory Forecast, and 9-Factor Checklists
        composeTestRule.onNodeWithTag("nav_tab_report").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cycle_report_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("executive_compass_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("cycle_report_screen")
            .performScrollToNode(hasTestTag("investor_action_playbook_card"))
        composeTestRule.onNodeWithTag("investor_action_playbook_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("equity_tax_shield_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("cycle_report_screen")
            .performScrollToNode(hasTestTag("trajectory_path_forecast_card"))
        composeTestRule.onNodeWithTag("trajectory_path_forecast_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("cycle_report_screen")
            .performScrollToNode(hasTestTag("deadly_peak_checklist_card"))
        composeTestRule.onNodeWithTag("deadly_peak_checklist_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("cycle_report_screen")
            .performScrollToNode(hasTestTag("deadly_bottom_checklist_card"))
        composeTestRule.onNodeWithTag("deadly_bottom_checklist_card").assertIsDisplayed()
    }

    @Test
    fun historicalChart_displaysExtendedTimeframesAggregationAndProvenanceBanner() {
        val sampleWith1995 = buildSampleState().let { base ->
            val hist = listOf(
                TankHistoryPoint(
                    date = "1995-01-02",
                    tankLevel = 0.0,
                    dailyDelta = 0.0,
                    flowingCount = 0,
                    activeCount = 0,
                    highPressureCount = 0,
                    avgScore = 0.0,
                    provenance = TankHistoryPoint.PROVENANCE_INCOMPLETE_COLD_START
                ),
                TankHistoryPoint(
                    date = "1996-06-14",
                    tankLevel = 44.2,
                    dailyDelta = 0.8,
                    flowingCount = 240,
                    activeCount = 500,
                    highPressureCount = 65,
                    avgScore = 54.0,
                    provenance = TankHistoryPoint.PROVENANCE_RECONSTRUCTED_HISTORICAL
                )
            ) + base.fullHistory
            base.copy(
                fullHistory = hist,
                filteredHistory = hist,
                selectedTimeframe = TimeframeOption.MAX,
                selectedAggregation = com.example.depthtank.data.ChartAggregation.WEEKLY,
                archiveHydrationState = com.example.depthtank.data.ArchiveHydrationState.READY
            )
        }

        var pickedTf: TimeframeOption? = null
        var pickedAgg: com.example.depthtank.data.ChartAggregation? = null

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = sampleWith1995,
                    onSelectTimeframe = { pickedTf = it },
                    onSelectAggregation = { pickedAgg = it },
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("historical_depth_chart_card"))
        composeTestRule.onNodeWithTag("timeframe_chip_y10").performScrollTo().performClick()
        assert(pickedTf == TimeframeOption.Y10)
        composeTestRule.onNodeWithTag("timeframe_chip_y20").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("timeframe_chip_max").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("aggregation_chip_daily").assertIsDisplayed()
        composeTestRule.onNodeWithTag("aggregation_chip_weekly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("aggregation_chip_monthly").performClick()
        assert(pickedAgg == com.example.depthtank.data.ChartAggregation.MONTHLY)

        composeTestRule.onNodeWithTag("provenance_notice_banner").assertIsDisplayed()
    }

    @Test
    fun cycleReport_displaysPartialFactorBadgeAndHonestNaForPreInceptionDates() {
        val pre2008History = listOf(
            TankHistoryPoint("1999-12-01", 62.0, 0.5, 340, 500, 120, 72.0, 24.0, 20, 4.0, 0.5, 10, 2.0, 0),
            TankHistoryPoint("2000-02-15", 79.4, 1.1, 440, 500, 280, 86.0, 56.0, 45, 9.0, 2.0, 4, 0.8, 12)
        )
        val partialReport = com.example.depthtank.data.DeadlyComboEngine.generateReport(
            universe = TankIndexUniverse.NIFTY_500,
            history = pre2008History,
            universeHistories = mapOf(TankIndexUniverse.NIFTY_500 to pre2008History),
            stocks = emptyList(),
            macroOverlays = emptyMap()
        )
        val state = buildSampleState().copy(
            fullHistory = pre2008History,
            filteredHistory = pre2008History,
            cycleReport = partialReport
        )

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = state,
                    onSelectTimeframe = {},
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("nav_tab_report").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("partial_factor_coverage_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("partial_factor_coverage_badge")
            .assertTextContains("7/9 Active", substring = true)

        composeTestRule.onNodeWithTag("cycle_report_screen")
            .performScrollToNode(hasTestTag("trajectory_path_forecast_card"))
        composeTestRule.onNodeWithText("Pre-2008 N/A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pre-2016 N/A").assertIsDisplayed()
    }

    @Test
    fun historicalChart_displaysValuationPeToggleHoleTileAndStockMiniTankPe() {
        val sampleWithPe = buildSampleState().let { base ->
            val peStocks = base.allStocks.map { s ->
                if (s.symbol == "HDFCBANK") {
                    s.copy(peTtm = 19.4, peLow52W = 15.0, peHigh52W = 23.0, epsTtm = 92.5)
                } else {
                    s
                }
            }
            val peHist = base.fullHistory.map { pt ->
                pt.copy(peTankLevel = 42.6, medianPe = 31.8, peActiveCount = 465)
            }
            base.copy(
                allStocks = peStocks,
                filteredStocks = peStocks,
                fullHistory = peHist,
                filteredHistory = peHist,
                currentPeTankLevel = 42.6,
                currentMedianPe = 31.8,
                peActiveStocksCount = 465
            )
        }

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = sampleWithPe,
                    onSelectTimeframe = {},
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("hero_tank_valuation_pe_badge").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("historical_depth_chart_card"))
        composeTestRule.onNodeWithTag("series_toggle_valuation_pe").assertIsDisplayed()
        // Toggle OFF and back ON without errors
        composeTestRule.onNodeWithTag("series_toggle_valuation_pe").performClick()
        composeTestRule.onNodeWithTag("series_toggle_valuation_pe").performClick()

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("hole_position_slider_card"))
        composeTestRule.onNodeWithTag("hole_valuation_pe_tile").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_tanks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stock_tank_card_HDFCBANK").assertIsDisplayed()
        composeTestRule.onNodeWithText("P/E 19.4× • V:55", substring = true).assertIsDisplayed()
    }

    @Test
    fun cycleReport_rendersIntegratedValuationPeTelemetryAndPreserves9Factors() {
        val sampleReport = DeadlyComboReport(
            sessionDate = "2026-11-20",
            universe = TankIndexUniverse.NIFTY_500,
            currentReading = MultiHoleReading(hole15 = 48.0, hole25 = 37.5, hole50 = 22.5, hole75 = 10.4),
            kinetics = KineticTelemetry(
                indiaVix = 13.8,
                vixSurgeRatio15d = 1.05,
                mmiValue = 68.4,
                mmiMinusTankGap = 45.9,
                adPctDaily = 61.2,
                adPct3dAvg = 58.4,
                velocity10d = 12.6,
                momentumAccel3d = 0.8,
                peTankLevel = 14.6,
                peHole25 = 22.9,
                peHole75 = 8.1,
                medianPe = 33.5,
                indexPe = 22.5,
                indexPb = 3.37,
                indexDivYield = 1.19,
                medianPeLongTermMedian = 22.9,
                medianPeP25 = 19.0,
                medianPeP75 = 27.6,
                medianPeHistoricalPercentile = 46,
                indexPeConsolidatedMedian = 23.9,
                indexPeConsolidatedPercentile = 24,
                isOfficialIndexPeBenchmark = true,
                priceMinusPeGap = 7.9,
                valuationRegime = "Deep Valuation Compression",
                absolutePeRegime = "Attractive Value",
                dualValuationVerdict = "Aligned Fair-Value Pullback (14.6% • 22.5×, 46th %ile (20Y) / 24th (Cons.))",
                peActiveCount = 474,
                peCoveragePct = 94.8
            ),
            investorDirective = InvestorActionDirective(
                activeStage = InvestorActionStage.AGGRESSIVE_SIP_STEP_UP,
                headlineDirective = "INCREASE SIP AGGRESSIVELY (+25%–50%) & STAGGER LUMP SUM IN 3 TRANCHES ON DIPS",
                executiveRationale = "Aligned Value Setup: Val P/E 14.6% and Official Index P/E 22.5× (46th %ile 20Y / 24th Cons.) agree on Attractive Value pricing."
            ),
            pathProjection = MarketPathProjection(
                crashRiskHeadline = "NO — Systemic Crash Risk Is LOW (~10%–12%)",
                crashRiskExplanation = "Aligned Val P/E 14.6% and Index P/E 22.5× provide genuine valuation support.",
                straightEuphoriaHeadline = "NO — Expect Intermediate Shakeouts First",
                straightEuphoriaExplanation = "Waiting for Val P/E re-rating >30%.",
                expectedPathSummary = "Main Tank 22.5%, Val P/E 14.6% (Index P/E 22.5×, 46th %ile)."
            ),
            forwardTrajectoryForecast = "Main Tank 22.5%, Val P/E 14.6% (Index P/E 22.5×, 46th %ile).",
            peakAvailableFactors = 9,
            peakTotalFactors = 9,
            bottomAvailableFactors = 9,
            bottomTotalFactors = 9
        )
        val state = TankUiState(
            selectedUniverse = TankIndexUniverse.NIFTY_500,
            cycleReport = sampleReport
        )

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                CycleReportScreen(uiState = state)
            }
        }

        composeTestRule.onNodeWithTag("executive_compass_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("compass_dual_valuation_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("14.6% • 22.5×", substring = false).assertIsDisplayed()

        composeTestRule.onNodeWithTag("cycle_report_screen")
            .performScrollToNode(hasTestTag("investor_action_playbook_card"))
        composeTestRule.onNodeWithTag("playbook_dual_valuation_pill").assertIsDisplayed()

        composeTestRule.onNodeWithTag("cycle_report_screen")
            .performScrollToNode(hasTestTag("trajectory_path_forecast_card"))
        composeTestRule.onNodeWithTag("report_valuation_pe_strip").assertIsDisplayed()
        composeTestRule.onNode(
            hasText("14.6%", substring = false) and hasAnyAncestor(hasTestTag("report_valuation_pe_strip"))
        ).assertIsDisplayed()
        composeTestRule.onNode(
            hasText("22.5×", substring = false) and hasAnyAncestor(hasTestTag("report_valuation_pe_strip"))
        ).assertIsDisplayed()
    }

    @Test
    fun dashboard_displaysDailyDualValuationRecommendationCard() {
        val sampleReport = DeadlyComboReport(
            sessionDate = "2026-11-20",
            universe = TankIndexUniverse.NIFTY_500,
            currentReading = MultiHoleReading(hole15 = 48.0, hole25 = 37.5, hole50 = 22.5, hole75 = 10.4),
            kinetics = KineticTelemetry(
                peTankLevel = 14.6,
                medianPe = 33.5,
                indexPe = 22.5,
                indexPb = 3.37,
                indexDivYield = 1.19,
                medianPeLongTermMedian = 22.9,
                medianPeHistoricalPercentile = 46,
                indexPeConsolidatedMedian = 23.9,
                indexPeConsolidatedPercentile = 24,
                isOfficialIndexPeBenchmark = true,
                valuationRegime = "Deep Valuation Compression",
                absolutePeRegime = "Attractive Value",
                dualValuationVerdict = "Aligned Fair-Value Pullback (14.6% • 22.5×)",
                peActiveCount = 474,
                peCoveragePct = 94.8
            ),
            investorDirective = InvestorActionDirective(
                activeStage = InvestorActionStage.AGGRESSIVE_SIP_STEP_UP,
                headlineDirective = "INCREASE SIP AGGRESSIVELY (+25%–50%) & STAGGER LUMP SUM IN 3 TRANCHES",
                executiveRationale = "12M Valuation P/E compressed at 14.6%, Official Index P/E 22.5× (46th %ile)."
            )
        )
        val state = buildSampleState().copy(
            cycleReport = sampleReport,
            currentPeTankLevel = 14.6,
            currentMedianPe = 33.5,
            currentIndexPe = 22.5,
            currentIndexPb = 3.37,
            currentIndexDivYield = 1.19,
            peActiveStocksCount = 474
        )

        composeTestRule.setContent {
            NiftyDepthTankTheme {
                DepthTankRootContent(
                    uiState = state,
                    onSelectTimeframe = {},
                    onSearchQueryChange = {},
                    onSelectSector = {},
                    onSelectFlowFilter = {},
                    onOpenStockDetail = {},
                    onDismissStockDetail = {},
                    onOpenInfoModal = {},
                    onDismissInfoModal = {},
                    onForceRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_daily_recommendation_card"))
        composeTestRule.onNodeWithTag("dashboard_daily_recommendation_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("INCREASE SIP AGGRESSIVELY", substring = true).assertIsDisplayed()
        composeTestRule.onNode(
            hasText("22.5× Idx", substring = false) and hasAnyAncestor(hasTestTag("dashboard_daily_recommendation_card"))
        ).assertIsDisplayed()
    }
}

