package com.example.depthtank.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.round

enum class ExtremeTypeFilter(val label: String, val thresholdDesc: String) {
    ALL("All Extremes", "Capitulation (<15%) & Euphoria (≥75%)"),
    CAPITULATION("Capitulation (<15%)", "Deep value washouts & asymmetric buy zones"),
    EUPHORIA("Euphoria (≥75%)", "Overheated breath & euphoric distribution tops")
}

enum class ExtremesViewMode(val label: String) {
    EPISODES("Macro Episodes"),
    DAILY_LOG("Daily Session Log")
}

data class MacroExtremeEpisode(
    val id: String,
    val title: String,
    val type: MarketRegime, // CAPITULATION or OVERHEATED
    val startDate: String,
    val endDate: String,
    val tradingDaysCount: Int,
    val extremeLevel: Double,
    val averageLevel: Double,
    val startLevel: Double,
    val endLevel: Double,
    val avgFlowingCount: Int,
    val maxFlowingCount: Int,
    val minFlowingCount: Int,
    val activeStocksCount: Int,
    val contextNote: String,
    val points: List<TankHistoryPoint>
)

data class ExtremesSummary(
    val totalCapitulationDays: Int,
    val totalEuphoriaDays: Int,
    val totalExtremeDays: Int,
    val totalTradingDays: Int,
    val extremeDaysSharePct: Double,
    val latestExtremePoint: TankHistoryPoint?,
    val isCurrentlyExtreme: Boolean
)

object ExtremesAnalyzer {

    fun summarize(history: List<TankHistoryPoint>): ExtremesSummary {
        val activeHistory = history.filterNot { it.isIncompleteColdStart }
        val totalDays = activeHistory.size
        val capDays = activeHistory.count { it.tankLevel < 15.0 }
        val euphDays = activeHistory.count { it.tankLevel >= 75.0 }
        val totalExtremes = capDays + euphDays
        val share = if (totalDays > 0) round((totalExtremes.toDouble() / totalDays.toDouble()) * 1000.0) / 10.0 else 0.0
        val latest = activeHistory.lastOrNull { it.tankLevel < 15.0 || it.tankLevel >= 75.0 }
        val isCurrent = activeHistory.lastOrNull()?.let { it.tankLevel < 15.0 || it.tankLevel >= 75.0 } ?: false

        return ExtremesSummary(
            totalCapitulationDays = capDays,
            totalEuphoriaDays = euphDays,
            totalExtremeDays = totalExtremes,
            totalTradingDays = totalDays,
            extremeDaysSharePct = share,
            latestExtremePoint = latest,
            isCurrentlyExtreme = isCurrent
        )
    }

    fun buildEpisodes(history: List<TankHistoryPoint>, maxGapDays: Int = 4): List<MacroExtremeEpisode> {
        val episodes = mutableListOf<MacroExtremeEpisode>()
        var currentType: MarketRegime? = null
        var currentPoints = mutableListOf<TankHistoryPoint>()
        var gap = 0
        var lastExtremeDate: LocalDate? = null

        for (pt in history) {
            if (pt.isIncompleteColdStart) continue
            val ptType = when {
                pt.tankLevel < 15.0 -> MarketRegime.CAPITULATION
                pt.tankLevel >= 75.0 -> MarketRegime.OVERHEATED
                else -> null
            }
            val ptDate = try { LocalDate.parse(pt.date) } catch (_: Exception) { null }

            if (currentType == null) {
                if (ptType != null) {
                    currentType = ptType
                    currentPoints = mutableListOf(pt)
                    lastExtremeDate = ptDate
                    gap = 0
                }
            } else {
                val calendarDays = if (ptDate != null && lastExtremeDate != null) {
                    ChronoUnit.DAYS.between(lastExtremeDate, ptDate)
                } else 0L

                if (ptType == currentType && calendarDays <= 8) {
                    currentPoints.add(pt)
                    lastExtremeDate = ptDate
                    gap = 0
                } else if (ptType != null && ptType != currentType) {
                    episodes.add(createEpisode(currentType, currentPoints))
                    currentType = ptType
                    currentPoints = mutableListOf(pt)
                    lastExtremeDate = ptDate
                    gap = 0
                } else {
                    gap++
                    if (gap > maxGapDays || calendarDays > 10) {
                        episodes.add(createEpisode(currentType, currentPoints))
                        if (ptType != null) {
                            currentType = ptType
                            currentPoints = mutableListOf(pt)
                            lastExtremeDate = ptDate
                        } else {
                            currentType = null
                            currentPoints = mutableListOf()
                            lastExtremeDate = null
                        }
                        gap = 0
                    }
                }
            }
        }
        if (currentType != null && currentPoints.isNotEmpty()) {
            episodes.add(createEpisode(currentType, currentPoints))
        }

        // Return reverse chronological so newest macro events appear at top
        return episodes.reversed()
    }

    fun filterDailyLog(
        history: List<TankHistoryPoint>,
        filter: ExtremeTypeFilter,
        selectedYear: String,
        searchQuery: String
    ): List<TankHistoryPoint> {
        val cleanQuery = searchQuery.trim().lowercase()

        return history.asReversed().filter { pt ->
            if (pt.isIncompleteColdStart) return@filter false
            val isCap = pt.tankLevel < 15.0
            val isEuph = pt.tankLevel >= 75.0
            val matchesType = when (filter) {
                ExtremeTypeFilter.ALL -> isCap || isEuph
                ExtremeTypeFilter.CAPITULATION -> isCap
                ExtremeTypeFilter.EUPHORIA -> isEuph
            }
            if (!matchesType) return@filter false

            val matchesYear = selectedYear == "All" || pt.date.startsWith(selectedYear)
            if (!matchesYear) return@filter false

            if (cleanQuery.isNotEmpty()) {
                val formatted = NseMarketCalendar.formatDdMmmYyyy(pt.date).lowercase()
                val dateMatches = pt.date.contains(cleanQuery) || formatted.contains(cleanQuery)
                val levelMatches = String.format("%.1f", pt.tankLevel).contains(cleanQuery)
                dateMatches || levelMatches
            } else {
                true
            }
        }
    }

    private fun createEpisode(
        type: MarketRegime,
        points: List<TankHistoryPoint>
    ): MacroExtremeEpisode {
        val start = points.first()
        val end = points.last()
        val isCap = type == MarketRegime.CAPITULATION

        val extremeLvl = if (isCap) {
            points.minOf { it.tankLevel }
        } else {
            points.maxOf { it.tankLevel }
        }
        val avgLvl = points.map { it.tankLevel }.average()
        val avgFlowing = points.map { it.flowingCount }.average().toInt()
        val maxFlowing = points.maxOf { it.flowingCount }
        val minFlowing = points.minOf { it.flowingCount }
        val activeCount = points.map { it.activeCount }.maxOrNull() ?: 500

        val startFormatted = NseMarketCalendar.formatDdMmmYyyy(start.date)
        val endFormatted = NseMarketCalendar.formatDdMmmYyyy(end.date)

        val title = generateEpisodeTitle(type, start.date, end.date, extremeLvl)
        val contextNote = generateContextNote(type, extremeLvl, points.size)

        return MacroExtremeEpisode(
            id = "${type.name}_${start.date}_${end.date}",
            title = title,
            type = type,
            startDate = start.date,
            endDate = end.date,
            tradingDaysCount = points.size,
            extremeLevel = round(extremeLvl * 100.0) / 100.0,
            averageLevel = round(avgLvl * 100.0) / 100.0,
            startLevel = start.tankLevel,
            endLevel = end.tankLevel,
            avgFlowingCount = avgFlowing,
            maxFlowingCount = maxFlowing,
            minFlowingCount = minFlowing,
            activeStocksCount = activeCount,
            contextNote = contextNote,
            points = points
        )
    }

    private fun generateEpisodeTitle(
        type: MarketRegime,
        startDate: String,
        endDate: String,
        extremeLvl: Double
    ): String {
        val isCap = type == MarketRegime.CAPITULATION
        val startYear = startDate.substring(0, 4)
        val startMonth = startDate.substring(5, 7)

        return when {
            isCap && startYear == "2020" && (startMonth in listOf("03", "04")) ->
                "COVID-19 Panic Capitulation (Washout: ${extremeLvl}%)"
            isCap && startYear in listOf("2018", "2019") ->
                "NBFC / Smallcap Bear Washout (${startYear})"
            isCap && startYear == "2022" ->
                "Global Rate-Hike & War Reset (${extremeLvl}%)"
            isCap && startYear == "2025" ->
                "2025 Midcap Reset Capitulation (${extremeLvl}%)"
            isCap && startYear == "2026" && startMonth in listOf("10") ->
                "October 2026 Deep Value Trough (${extremeLvl}%)"
            isCap && startYear == "2016" && startMonth in listOf("01", "02") ->
                "2016 Global Oil & China Sell-Off (${extremeLvl}%)"
            isCap && startYear == "2013" ->
                "2013 Fed Taper Tantrum Washout (${extremeLvl}%)"
            isCap && startYear == "2011" ->
                "2011 Euro Crisis & RBI Rate-Hike Trough (${extremeLvl}%)"
            isCap && startYear in listOf("2008", "2009") ->
                "Global Financial Crisis Capitulation ($startYear • ${extremeLvl}%)"
            isCap && startYear == "2004" && startMonth == "05" ->
                "May 2004 Election Shock Washout (${extremeLvl}%)"
            isCap && startYear in listOf("2000", "2001") ->
                "Dot-Com & Ketan Parekh Bear Trough ($startYear • ${extremeLvl}%)"
            isCap && startYear in listOf("1997", "1998") ->
                "Asian Financial Crisis Washout ($startYear • ${extremeLvl}%)"
            isCap ->
                "Deep Capitulation Bottom ($startYear • ${extremeLvl}%)"
            !isCap && startYear == "2024" && (startMonth in listOf("08", "09", "10")) ->
                "2024 All-Time High Euphoric Peak (${extremeLvl}%)"
            !isCap && startYear == "2021" ->
                "Post-COVID Secular Bull Expansion (2021)"
            !isCap && startYear == "2017" ->
                "Post-Demonetization Secular Momentum (2017)"
            !isCap && startYear == "2026" && startMonth in listOf("01") ->
                "January 2026 New Year Breadth Peak (${extremeLvl}%)"
            !isCap && startYear == "2014" ->
                "2014 Secular Election Breakout Peak (${extremeLvl}%)"
            !isCap && startYear == "2010" ->
                "2010 Post-GFC Recovery Climax (${extremeLvl}%)"
            !isCap && startYear in listOf("2007", "2008") ->
                "2007–2008 Infra & Liquidity Secular Top (${extremeLvl}%)"
            !isCap && startYear in listOf("2003", "2004", "2005", "2006") ->
                "2003–2007 Secular Bull Expansion ($startYear • ${extremeLvl}%)"
            !isCap && startYear in listOf("1999", "2000") ->
                "Dot-Com / Y2K Tech Mania Peak ($startYear • ${extremeLvl}%)"
            !isCap ->
                "Euphoric Overheated Cycle ($startYear • ${extremeLvl}%)"
            else -> "Market Breadth Extreme Episode"
        }
    }

    private fun generateContextNote(
        type: MarketRegime,
        extremeLvl: Double,
        durationDays: Int
    ): String {
        return if (type == MarketRegime.CAPITULATION) {
            "Severe internal liquidation where over 85% of momentum capacity drained. Historically offered maximum multi-year asymmetric reward across systematic entry tranches."
        } else {
            "Euphoric breadth cluster ($durationDays trading days). Over 75% of capacity pinned near highs. Asymmetric downside risk; disciplined trailing stops and cash defense recommended."
        }
    }
}
