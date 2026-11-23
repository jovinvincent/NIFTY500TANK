package com.example.depthtank.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.round

import kotlin.math.abs

data class CycleDetectionConfig(
    val startIsoDate: String = "2016-04-01",
    val minSwingProminencePct: Double = 25.0,
    val minPeakThresholdPct: Double = 55.0,
    val maxBottomCeilingPct: Double = 38.0,
    val recoveryFloorPct: Double = 55.0,
    val recoveryRetracementRatio: Double = 0.65
)

data class MarketCycleTurningPoint(
    val cycleIndex: Int,
    val exactPeakDate: String,
    val peakMainTankPct: Double,
    val peakEuphoriaPct: Double,
    val exactBottomDate: String,
    val bottomMainTankPct: Double,
    val bottomCapitulationPct: Double,
    val drawdownPctPoints: Double,
    val daysPeakToBottom: Int,
    val calendarDaysPeakToBottom: Int,
    val recoveryDate: String?,
    val recoveryDurationDays: Int?,
    val calendarDaysBottomToRecovery: Int?,
    val recoveryLevelPct: Double,
    val recoveryFromBottomPctPoints: Double,
    val recoveryRetracementPct: Double,
    val isRecovered: Boolean
)

/**
 * Strictly causal real-time signal derived using only observations up to index t (0..t).
 * Completely isolated from retrospective ZigZag turning-point detection to prevent look-ahead bias.
 */
data class CausalLiveSignal(
    val sessionDate: String,
    val fourPillars: FourPillarSnapshot,
    val trailingHighDate: String,
    val trailingHighTankPct: Double,
    val drawdownFromTrailingHighPctPoints: Double,
    val tradingDaysSinceTrailingHigh: Int,
    val isLookAheadBiasFree: Boolean = true
)

private data class RawPivot(
    val index: Int,
    val isPeak: Boolean
)

/**
 * Quantitative turning-point engine.
 *
 * Retrospective cycle detection operates strictly on RAW unsmoothed daily observations
 * (never moving averages or smoothed splines) so exact calendar dates, trough depth, and
 * peak euphoria readings remain faithful to actual market breadth.
 */
object CycleTurningPointEngine {

    fun configForHoleThreshold(
        holeThreshold: Double,
        startIsoDate: String = "2016-04-01"
    ): CycleDetectionConfig {
        val h = holeThreshold.coerceIn(0.0, 100.0)
        if (abs(h - 50.0) < 0.5) return CycleDetectionConfig(startIsoDate = startIsoDate)
        return if (h > 50.0) {
            val t = ((h - 50.0) / 50.0).coerceIn(0.0, 1.0)
            CycleDetectionConfig(
                startIsoDate = startIsoDate,
                minSwingProminencePct = (25.0 - 12.0 * t).coerceIn(10.0, 25.0),
                minPeakThresholdPct = (55.0 - 40.0 * t).coerceIn(12.0, 55.0),
                maxBottomCeilingPct = (38.0 - 32.0 * t).coerceIn(5.0, 38.0),
                recoveryFloorPct = (55.0 - 40.0 * t).coerceIn(12.0, 55.0)
            )
        } else {
            val t = ((50.0 - h) / 50.0).coerceIn(0.0, 1.0)
            CycleDetectionConfig(
                startIsoDate = startIsoDate,
                minSwingProminencePct = (25.0 - 3.0 * t).coerceIn(20.0, 25.0),
                minPeakThresholdPct = (55.0 + 14.0 * t).coerceIn(55.0, 72.0),
                maxBottomCeilingPct = (38.0 + 16.0 * t).coerceIn(38.0, 56.0),
                recoveryFloorPct = (55.0 + 14.0 * t).coerceIn(55.0, 72.0)
            )
        }
    }

    fun detectCyclesForHole(
        simulatedHistory: List<TankHistoryPoint>,
        holeThreshold: Double,
        startIsoDate: String = "2016-04-01"
    ): List<MarketCycleTurningPoint> {
        val h = holeThreshold.coerceIn(0.0, 100.0)
        if (h >= 98.0) return emptyList()
        return detectCyclesRetrospective(
            rawHistory = simulatedHistory,
            config = configForHoleThreshold(h, startIsoDate = startIsoDate)
        )
    }

    fun detectCyclesRetrospective(
        rawHistory: List<TankHistoryPoint>,
        config: CycleDetectionConfig = CycleDetectionConfig()
    ): List<MarketCycleTurningPoint> {
        val scoped = rawHistory
            .filter { it.date >= config.startIsoDate && !it.isIncompleteColdStart }
            .sortedBy { it.date }
        if (scoped.size < 3) return emptyList()

        val prominence = config.minSwingProminencePct.coerceAtLeast(5.0)
        val maxBottomCeiling = config.maxBottomCeilingPct.coerceIn(2.0, 85.0)
        val pivots = findRawZigZagPivots(scoped, prominence, maxBottomCeiling)
        if (pivots.size < 2) return emptyList()

        val cycles = mutableListOf<MarketCycleTurningPoint>()
        var i = 0
        var cycleNumber = 1
        while (i + 1 < pivots.size) {
            val first = pivots[i]
            val second = pivots[i + 1]
            if (first.isPeak && !second.isPeak) {
                val peakPt = scoped[first.index]
                val bottomPt = scoped[second.index]
                val drawdown = roundTwo(peakPt.tankLevel - bottomPt.tankLevel)

                if (peakPt.tankLevel >= config.minPeakThresholdPct &&
                    bottomPt.tankLevel <= maxBottomCeiling &&
                    drawdown >= prominence * 0.6
                ) {
                    val nextPeakIdx = if (i + 2 < pivots.size && pivots[i + 2].isPeak) {
                        pivots[i + 2].index
                    } else {
                        scoped.lastIndex
                    }

                    val targetLevel = max(
                        config.recoveryFloorPct,
                        bottomPt.tankLevel + config.recoveryRetracementRatio * drawdown
                    ).coerceAtMost(peakPt.tankLevel)

                    var recoveryIdx: Int? = null
                    var maxPostBottomLevel = bottomPt.tankLevel
                    for (scanIdx in (second.index + 1)..nextPeakIdx) {
                        val lvl = scoped[scanIdx].tankLevel
                        if (lvl > maxPostBottomLevel) {
                            maxPostBottomLevel = lvl
                        }
                        if (recoveryIdx == null && lvl >= targetLevel) {
                            recoveryIdx = scanIdx
                        }
                    }

                    val isRecovered = recoveryIdx != null
                    val evalLevel = if (recoveryIdx != null) scoped[recoveryIdx].tankLevel else maxPostBottomLevel
                    val gainFromBottom = roundTwo(evalLevel - bottomPt.tankLevel)
                    val retracementPct = if (drawdown > 0.001) {
                        roundTwo(((evalLevel - bottomPt.tankLevel) / drawdown) * 100.0)
                    } else {
                        0.0
                    }

                    cycles.add(
                        MarketCycleTurningPoint(
                            cycleIndex = cycleNumber++,
                            exactPeakDate = peakPt.date,
                            peakMainTankPct = roundTwo(peakPt.tankLevel),
                            peakEuphoriaPct = roundTwo(peakPt.euphoriaPressurePct),
                            exactBottomDate = bottomPt.date,
                            bottomMainTankPct = roundTwo(bottomPt.tankLevel),
                            bottomCapitulationPct = roundTwo(bottomPt.capitulationDepthPct),
                            drawdownPctPoints = drawdown,
                            daysPeakToBottom = second.index - first.index,
                            calendarDaysPeakToBottom = safeCalendarDaysBetween(peakPt.date, bottomPt.date),
                            recoveryDate = recoveryIdx?.let { scoped[it].date },
                            recoveryDurationDays = recoveryIdx?.let { it - second.index },
                            calendarDaysBottomToRecovery = recoveryIdx?.let {
                                safeCalendarDaysBetween(bottomPt.date, scoped[it].date)
                            },
                            recoveryLevelPct = roundTwo(evalLevel),
                            recoveryFromBottomPctPoints = gainFromBottom,
                            recoveryRetracementPct = retracementPct,
                            isRecovered = isRecovered
                        )
                    )
                }
                i += 2
            } else {
                i += 1
            }
        }
        return cycles
    }

    /**
     * Evaluates strictly causal live state at the terminal observation of [historyPrefix].
     * Zero look-ahead data beyond historyPrefix.last() is permitted or inspected.
     */
    fun computeCausalSignalAt(
        historyPrefix: List<TankHistoryPoint>,
        liveStocks: List<StockTank> = emptyList(),
        trailingLookbackSessions: Int = 252
    ): CausalLiveSignal? {
        if (historyPrefix.isEmpty()) return null
        val latest = historyPrefix.last()
        val pillars = TankCalculator.computeFourPillarSnapshot(
            stocks = liveStocks,
            history = historyPrefix,
            sessionDate = latest.date
        )

        val windowStart = (historyPrefix.size - trailingLookbackSessions).coerceAtLeast(0)
        var peakIdx = windowStart
        var peakVal = historyPrefix[windowStart].tankLevel
        for (idx in windowStart until historyPrefix.size) {
            if (historyPrefix[idx].tankLevel >= peakVal) {
                peakVal = historyPrefix[idx].tankLevel
                peakIdx = idx
            }
        }
        val currentLevel = pillars.mainTankLevelPct
        val drawdownFromHigh = roundTwo((peakVal - currentLevel).coerceAtLeast(0.0))

        return CausalLiveSignal(
            sessionDate = latest.date,
            fourPillars = pillars,
            trailingHighDate = historyPrefix[peakIdx].date,
            trailingHighTankPct = roundTwo(peakVal),
            drawdownFromTrailingHighPctPoints = drawdownFromHigh,
            tradingDaysSinceTrailingHigh = (historyPrefix.lastIndex - peakIdx).coerceAtLeast(0),
            isLookAheadBiasFree = true
        )
    }

    private fun findRawZigZagPivots(
        raw: List<TankHistoryPoint>,
        prominence: Double,
        maxBottomCeiling: Double
    ): List<RawPivot> {
        val pivots = mutableListOf<RawPivot>()
        var direction = 0 // 0 = undetermined, -1 = seeking trough, +1 = seeking peak
        var initMaxIdx = 0
        var initMinIdx = 0
        var candHighIdx = 0
        var candLowIdx = 0

        for (i in raw.indices) {
            val v = raw[i].tankLevel
            if (direction == 0) {
                if (v > raw[initMaxIdx].tankLevel) initMaxIdx = i
                if (v < raw[initMinIdx].tankLevel) initMinIdx = i

                if (raw[initMaxIdx].tankLevel - v >= prominence && v <= maxBottomCeiling && initMaxIdx < i) {
                    pivots.add(RawPivot(initMaxIdx, isPeak = true))
                    direction = -1
                    candLowIdx = i
                } else if (v - raw[initMinIdx].tankLevel >= prominence && raw[initMinIdx].tankLevel <= maxBottomCeiling && initMinIdx < i) {
                    pivots.add(RawPivot(initMinIdx, isPeak = false))
                    direction = 1
                    candHighIdx = i
                }
            } else if (direction == -1) {
                if (v < raw[candLowIdx].tankLevel) {
                    candLowIdx = i
                } else if (pivots.isNotEmpty() &&
                    pivots.last().isPeak &&
                    raw[candLowIdx].tankLevel > maxBottomCeiling &&
                    v > raw[pivots.last().index].tankLevel
                ) {
                    // Shallow mid-bull pullback remained above maxBottomCeiling and made a higher bull climax
                    pivots[pivots.lastIndex] = RawPivot(i, isPeak = true)
                    candLowIdx = i
                } else if (v - raw[candLowIdx].tankLevel >= prominence) {
                    if (raw[candLowIdx].tankLevel <= maxBottomCeiling) {
                        pivots.add(RawPivot(candLowIdx, isPeak = false))
                        direction = 1
                        candHighIdx = i
                    } else if (pivots.isNotEmpty() && pivots.last().isPeak && v > raw[pivots.last().index].tankLevel) {
                        pivots[pivots.lastIndex] = RawPivot(i, isPeak = true)
                        candLowIdx = i
                    }
                }
            } else {
                if (v > raw[candHighIdx].tankLevel) {
                    candHighIdx = i
                } else if (raw[candHighIdx].tankLevel - v >= prominence && v <= maxBottomCeiling) {
                    pivots.add(RawPivot(candHighIdx, isPeak = true))
                    direction = -1
                    candLowIdx = i
                }
            }
        }

        // Capture terminal unconfirmed swing trough if a genuine correction has reached <= maxBottomCeiling
        if (direction == -1 && pivots.isNotEmpty() && pivots.last().isPeak) {
            val lastPeakVal = raw[pivots.last().index].tankLevel
            val candLowVal = raw[candLowIdx].tankLevel
            if (candLowVal <= maxBottomCeiling && lastPeakVal - candLowVal >= prominence * 0.6) {
                pivots.add(RawPivot(candLowIdx, isPeak = false))
            }
        }
        return pivots
    }

    private fun safeCalendarDaysBetween(startIso: String, endIso: String): Int {
        return try {
            ChronoUnit.DAYS.between(LocalDate.parse(startIso), LocalDate.parse(endIso)).toInt().coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    private fun roundTwo(value: Double): Double = round(value * 100.0) / 100.0
}
