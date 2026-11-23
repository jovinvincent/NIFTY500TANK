package com.example

import com.example.depthtank.data.MarketRegime
import com.example.depthtank.data.NseMarketCalendar
import com.example.depthtank.data.StockTank
import com.example.depthtank.data.TankCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class TankMathTest {

    @Test
    fun stockScoreAndOverflowFollow50PercentValveRule() {
        val flowingStock = StockTank(
            symbol = "INFY",
            name = "Infosys Ltd.",
            sector = "IT",
            currentPrice = 175.0,
            low52W = 100.0,
            high52W = 200.0,
            dailyChangePct = 1.2
        )
        assertEquals(75.0, flowingStock.score, 0.001)
        assertEquals(25.0, flowingStock.overflowVolume, 0.001)

        val weakStock = StockTank(
            symbol = "TATASTEEL",
            name = "Tata Steel Ltd.",
            sector = "Metals",
            currentPrice = 120.0,
            low52W = 100.0,
            high52W = 200.0,
            dailyChangePct = -0.8
        )
        assertEquals(20.0, weakStock.score, 0.001)
        assertEquals(0.0, weakStock.overflowVolume, 0.001)

        // Two stocks: overflow sum = 25 + 0 = 25; max possible = 2 * 50 = 100 -> Main Tank = 25%
        val mainTank = TankCalculator.computeMainTankLevel(listOf(flowingStock, weakStock))
        assertEquals(25.0, mainTank, 0.001)
    }

    @Test
    fun marketRegimeClassificationMatchesPlaybookBands() {
        assertEquals(MarketRegime.CAPITULATION, TankCalculator.classifyRegime(6.2))
        assertEquals(MarketRegime.ACCUMULATION, TankCalculator.classifyRegime(28.5))
        assertEquals(MarketRegime.NEUTRAL, TankCalculator.classifyRegime(48.0))
        assertEquals(MarketRegime.BULLISH_FLOW, TankCalculator.classifyRegime(64.1))
        assertEquals(MarketRegime.OVERHEATED, TankCalculator.classifyRegime(81.5))
    }

    @Test
    fun nseMarketCalendarSkipsHolidaysWeekendsAndPreOpenHours() {
        // Sunday 15-Nov-2026 resolves to Friday 13-Nov-2026
        val sundayIst = ZonedDateTime.of(2026, 11, 15, 14, 44, 0, 0, NseMarketCalendar.IST_ZONE)
        assertEquals(LocalDate.of(2026, 11, 13), NseMarketCalendar.latestMarketSessionDate(sundayIst))

        // NSE holiday 10-Nov-2026 (Diwali Balipratipada) & 15-Jan-2026 resolve to prior trading day
        assertFalse(NseMarketCalendar.isTradingDay(LocalDate.of(2026, 11, 10)))
        assertFalse(NseMarketCalendar.isTradingDay(LocalDate.of(2026, 1, 15)))
        assertTrue(NseMarketCalendar.isTradingDay(LocalDate.of(2026, 11, 13)))

        val holidayIst = ZonedDateTime.of(2026, 11, 10, 13, 0, 0, 0, NseMarketCalendar.IST_ZONE)
        assertEquals(LocalDate.of(2026, 11, 9), NseMarketCalendar.latestMarketSessionDate(holidayIst))

        // Pre-open before 09:15 IST on 11-Nov-2026 resolves past holiday 10-Nov to 09-Nov-2026
        val preOpenIst = ZonedDateTime.of(2026, 11, 11, 8, 45, 0, 0, NseMarketCalendar.IST_ZONE)
        assertEquals(LocalDate.of(2026, 11, 9), NseMarketCalendar.latestMarketSessionDate(preOpenIst))
        assertFalse(NseMarketCalendar.isMarketOpen(preOpenIst))

        val liveSessionIst = ZonedDateTime.of(2026, 11, 16, 11, 15, 0, 0, NseMarketCalendar.IST_ZONE)
        assertTrue(NseMarketCalendar.isMarketOpen(liveSessionIst))
        assertFalse(NseMarketCalendar.isPostCloseSettlementWindow(liveSessionIst))

        val postCloseIst = ZonedDateTime.of(2026, 11, 16, 16, 0, 0, 0, NseMarketCalendar.IST_ZONE)
        assertFalse(NseMarketCalendar.isMarketOpen(postCloseIst))
        assertTrue(NseMarketCalendar.isPostCloseSettlementWindow(postCloseIst))
        assertFalse(NseMarketCalendar.isMarketOpen(holidayIst))
        assertFalse(NseMarketCalendar.isMarketOpen(sundayIst))
    }

    @Test
    fun dateFormattingMatchesDdMmmYyyyConvention() {
        assertEquals("15-Nov-2026", NseMarketCalendar.formatDdMmmYyyy("2026-11-15"))
        assertEquals("13-Nov-2026", NseMarketCalendar.formatDdMmmYyyy("2026-11-13"))
        assertEquals("01-Apr-2016", NseMarketCalendar.formatDdMmmYyyy("2016-04-01"))
    }

    @Test
    fun extremesAnalyzerClustersMacroEpisodesAndFiltersDailyLog() {
        val samplePoints = listOf(
            com.example.depthtank.data.TankHistoryPoint("2020-03-20", 8.5, -2.1, 80, 500, 2, 33.0),
            com.example.depthtank.data.TankHistoryPoint("2020-03-23", 5.84, -2.66, 75, 500, 1, 31.0),
            com.example.depthtank.data.TankHistoryPoint("2020-03-24", 9.2, 3.36, 85, 500, 4, 34.0),
            com.example.depthtank.data.TankHistoryPoint("2020-06-15", 45.0, 1.2, 220, 500, 40, 50.0), // normal
            com.example.depthtank.data.TankHistoryPoint("2024-09-24", 80.5, 0.4, 490, 500, 320, 88.0),
            com.example.depthtank.data.TankHistoryPoint("2024-09-25", 81.19, 0.69, 494, 500, 352, 89.8),
            com.example.depthtank.data.TankHistoryPoint("2026-11-13", 22.48, 0.19, 228, 500, 69, 48.59) // normal
        )

        val summary = com.example.depthtank.data.ExtremesAnalyzer.summarize(samplePoints)
        assertEquals(3, summary.totalCapitulationDays)
        assertEquals(2, summary.totalEuphoriaDays)
        assertEquals(5, summary.totalExtremeDays)
        assertFalse(summary.isCurrentlyExtreme)

        val episodes = com.example.depthtank.data.ExtremesAnalyzer.buildEpisodes(samplePoints)
        assertEquals(2, episodes.size)

        val newestEpisode = episodes.first()
        assertEquals(MarketRegime.OVERHEATED, newestEpisode.type)
        assertEquals(81.19, newestEpisode.extremeLevel, 0.001)

        val olderEpisode = episodes.last()
        assertEquals(MarketRegime.CAPITULATION, olderEpisode.type)
        assertEquals(5.84, olderEpisode.extremeLevel, 0.001)

        val capDaily = com.example.depthtank.data.ExtremesAnalyzer.filterDailyLog(
            samplePoints,
            com.example.depthtank.data.ExtremeTypeFilter.CAPITULATION,
            "All",
            ""
        )
        assertEquals(3, capDaily.size)

        val year2024Daily = com.example.depthtank.data.ExtremesAnalyzer.filterDailyLog(
            samplePoints,
            com.example.depthtank.data.ExtremeTypeFilter.ALL,
            "2024",
            ""
        )
        assertEquals(2, year2024Daily.size)
    }

    @Test
    fun scoreBinsHistogramMatchesExactConstituentOverflowAcrossAllHolePositions() {
        val constituents = (0 until 100).map { idx ->
            val scorePrice = 100.0 + idx.toDouble() + 0.5 // scores 0.5, 1.5, ..., 99.5
            StockTank(
                symbol = "STK$idx",
                name = "Stock $idx",
                sector = "Industrials",
                currentPrice = scorePrice,
                low52W = 100.0,
                high52W = 200.0,
                dailyChangePct = 0.0,
                rollingHistoryDays = 252
            )
        }
        val bins = TankCalculator.buildScoreBins(constituents)
        assertEquals(100, bins.sum())

        for (hole in listOf(0.0, 15.0, 25.0, 50.0, 75.0, 85.0, 100.0)) {
            val exactLevel = TankCalculator.computeMainTankLevel(constituents, hole)
            val binLevel = TankCalculator.computeTankLevelFromBins(bins, hole)
            assertEquals("Tank level mismatch at hole=$hole", exactLevel, binLevel, 0.25)

            val exactFlowing = constituents.count { it.isFlowingAt(hole) }
            val binFlowing = TankCalculator.computeFlowingCountFromBins(bins, hole)
            assertEquals("Flowing count mismatch at hole=$hole", exactFlowing, binFlowing)
        }
    }
}
