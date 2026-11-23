package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.depthtank.data.FlowFilter
import com.example.depthtank.data.MarketRegime
import com.example.depthtank.data.SectorTankSummary
import com.example.depthtank.data.StockTank
import com.example.depthtank.data.SyncPipelineStatus
import com.example.depthtank.data.TankHistoryPoint
import com.example.depthtank.data.TankUiState
import com.example.depthtank.data.TimeframeOption
import com.example.depthtank.ui.DepthTankRootContent
import com.example.ui.theme.NiftyDepthTankTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun depth_tank_dashboard_screenshot() {
        val sampleStocks = listOf(
            StockTank("HDFCBANK", "HDFC Bank Ltd.", "Banking", 1795.5, 1363.55, 1825.0, 0.92),
            StockTank("TCS", "Tata Consultancy Services", "IT", 4385.75, 3591.5, 4592.25, 0.72),
            StockTank("TATAMOTORS", "Tata Motors Ltd.", "Auto", 848.4, 762.5, 1179.0, -1.18)
        )
        val sampleHistory = listOf(
            TankHistoryPoint("2016-04-01", 36.57, 0.0, 230, 296, 54, 65.6),
            TankHistoryPoint("2020-03-23", 5.84, -3.2, 85, 500, 2, 31.2),
            TankHistoryPoint("2024-09-26", 81.19, 0.65, 494, 500, 352, 89.8),
            TankHistoryPoint("2026-11-13", 64.07, 1.38, 396, 500, 212, 77.91)
        )

        val state = TankUiState(
            isLoading = false,
            currentTankLevel = 64.07,
            dailyDelta = 1.38,
            regime = MarketRegime.BULLISH_FLOW,
            flowingCount = 396,
            highPressureCount = 212,
            belowHoleCount = 104,
            activeStocksCount = 500,
            avgStockScore = 77.91,
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
                trackedStocksCount = 500,
                isLiveHydrated = true
            )
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

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
