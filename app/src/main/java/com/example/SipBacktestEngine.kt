package com.example

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object SipBacktestEngine {

    fun parseDateAnyLocal(dateStr: String): Date? {
        val formats = listOf("dd-MMM-yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                val d = sdf.parse(dateStr)
                if (d != null) return d
            } catch (e: Exception) {}
        }
        return null
    }

    fun calculateXIRR(cashFlows: List<Pair<Long, Double>>): Double {
        if (cashFlows.size < 2) return 0.0
        val sorted = cashFlows.sortedBy { it.first }
        val t0 = sorted.first().first

        // Newton-Raphson method
        var rate = 0.1 // initial guess 10%
        for (iter in 0 until 100) {
            var fValue = 0.0
            var fDerivative = 0.0

            for (cf in sorted) {
                val dt = (cf.first - t0).toDouble() / (365.25 * 24.0 * 60.0 * 60.0 * 1000.0)
                val base = 1.0 + rate
                if (base <= 0.0) continue
                val factor = base.pow(dt)
                fValue += cf.second / factor
                fDerivative -= (dt * cf.second) / base.pow(dt + 1.0)
            }

            if (abs(fValue) < 1e-5) return rate
            if (abs(fDerivative) < 1e-9) break

            val nextRate = rate - (fValue / fDerivative)
            if (nextRate <= -0.99) {
                rate = (rate - 0.99) / 2.0
            } else {
                rate = nextRate
            }
        }
        return rate
    }

    fun runBacktestSimulation(sorted: List<Pair<HistoricalNav, Long>>): List<BacktestResult> {
        val n = sorted.size
        val startIdx = 0

        val monthlyBase = 10000.0
        val baseInstallment = monthlyBase / 4.0 // ₹2,500 per weekly interval
        val dailyAmount = monthlyBase / 20.0     // ₹500 per trading day
        val weeklyAmount = monthlyBase / 4.0     // ₹2,500 per week
        val monthlyAmount = monthlyBase          // ₹10,000 per month
        val lumpsumAmount = monthlyBase * 2.0    // ₹20,000 on dip

        val weeklyLowestIndices = mutableSetOf<Int>()
        var wStart = 0
        while (wStart < n) {
            val wEnd = minOf(n, wStart + 5)
            var minIdx = wStart
            var minVal = sorted[wStart].first.navValue
            for (idx in wStart until wEnd) {
                if (sorted[idx].first.navValue < minVal) {
                    minVal = sorted[idx].first.navValue
                    minIdx = idx
                }
            }
            weeklyLowestIndices.add(minIdx)
            wStart += 5
        }

        val monthlyLowestIndices = mutableSetOf<Int>()
        var mStart = 0
        while (mStart < n) {
            val mEnd = minOf(n, mStart + 20)
            var minIdx = mStart
            var minVal = sorted[mStart].first.navValue
            for (idx in mStart until mEnd) {
                if (sorted[idx].first.navValue < minVal) {
                    minVal = sorted[idx].first.navValue
                    minIdx = idx
                }
            }
            monthlyLowestIndices.add(minIdx)
            mStart += 20
        }

        var smartUnits = 0.0
        var smartInvested = 0.0
        var smartInstallmentCount = 0
        val smartCashFlows = mutableListOf<Pair<Long, Double>>()
        val smartGrowth = mutableListOf<Pair<Long, Double>>()
        var smartPeakValue = 0.0
        var smartMaxDrawdown = 0.0

        var weeklyDipUnits = 0.0
        var weeklyDipInvested = 0.0
        var weeklyDipInstallmentCount = 0
        val weeklyDipCashFlows = mutableListOf<Pair<Long, Double>>()
        val weeklyDipGrowth = mutableListOf<Pair<Long, Double>>()
        var weeklyDipPeakValue = 0.0
        var weeklyDipMaxDrawdown = 0.0

        var monthlyDipUnits = 0.0
        var monthlyDipInvested = 0.0
        var monthlyDipInstallmentCount = 0
        val monthlyDipCashFlows = mutableListOf<Pair<Long, Double>>()
        val monthlyDipGrowth = mutableListOf<Pair<Long, Double>>()
        var monthlyDipPeakValue = 0.0
        var monthlyDipMaxDrawdown = 0.0

        var dailyUnits = 0.0
        var dailyInvested = 0.0
        var dailyInstallmentCount = 0
        val dailyCashFlows = mutableListOf<Pair<Long, Double>>()
        val dailyGrowth = mutableListOf<Pair<Long, Double>>()
        var dailyPeakValue = 0.0
        var dailyMaxDrawdown = 0.0

        var weeklyUnits = 0.0
        var weeklyInvested = 0.0
        var weeklyInstallmentCount = 0
        val weeklyCashFlows = mutableListOf<Pair<Long, Double>>()
        val weeklyGrowth = mutableListOf<Pair<Long, Double>>()
        var weeklyPeakValue = 0.0
        var weeklyMaxDrawdown = 0.0

        var monthlyUnits = 0.0
        var monthlyInvested = 0.0
        var monthlyInstallmentCount = 0
        val monthlyCashFlows = mutableListOf<Pair<Long, Double>>()
        val monthlyGrowth = mutableListOf<Pair<Long, Double>>()
        var monthlyPeakValue = 0.0
        var monthlyMaxDrawdown = 0.0

        var lumpsumUnits = 0.0
        var lumpsumInvested = 0.0
        var lumpsumInstallmentCount = 0
        val lumpsumCashFlows = mutableListOf<Pair<Long, Double>>()
        val lumpsumGrowth = mutableListOf<Pair<Long, Double>>()
        var lumpsumPeakValue = 0.0
        var lumpsumMaxDrawdown = 0.0
        var lastLumpsumIdx = -100
        var lastLumpsumTier = 4

        for (i in startIdx until n) {
            val currentRec = sorted[i].first
            val currentNav = currentRec.navValue
            val currentTimestamp = sorted[i].second

            var runningMax = 0.0
            for (k in 0..i) {
                if (sorted[k].first.navValue > runningMax) runningMax = sorted[k].first.navValue
            }
            val athPullbackPct = if (runningMax > 0.0) ((currentNav - runningMax) / runningMax) * 100.0 else 0.0

            val sma20 = if (i >= 19) {
                (0 until 20).map { sorted[i - it].first.navValue }.average()
            } else {
                (0..i).map { sorted[it].first.navValue }.average()
            }
            val sma50 = if (i >= 49) {
                (0 until 50).map { sorted[i - it].first.navValue }.average()
            } else sma20
            val sma200 = if (i >= 199) {
                (0 until 200).map { sorted[i - it].first.navValue }.average()
            } else sma50

            val rsi14 = if (i >= 14) {
                var gains = 0.0
                var losses = 0.0
                for (r in 1..14) {
                    val diff = sorted[i - 14 + r].first.navValue - sorted[i - 14 + r - 1].first.navValue
                    if (diff > 0.0) gains += diff else losses += -diff
                }
                val avgGain = gains / 14.0
                val avgLoss = losses / 14.0
                if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + (avgGain / avgLoss)))
            } else 50.0

            val vol20 = if (i >= 20) {
                val returns = (1..20).map {
                    (sorted[i - 20 + it].first.navValue - sorted[i - 20 + it - 1].first.navValue) / sorted[i - 20 + it - 1].first.navValue
                }
                val mean = returns.average()
                val variance = returns.map { (it - mean).pow(2) }.average()
                sqrt(variance) * sqrt(252.0) * 100.0
            } else 15.0

            val sma20GapPct = ((currentNav - sma20) / sma20) * 100.0

            if (i % 5 == 0) {
                var multiplier = 1.0
                if (athPullbackPct <= -15.0) multiplier += 1.0
                else if (athPullbackPct <= -10.0) multiplier += 0.75
                else if (athPullbackPct <= -5.0) multiplier += 0.5
                else if (athPullbackPct <= -2.5) multiplier += 0.25

                if (sma20GapPct <= -5.0) multiplier += 0.75
                else if (sma20GapPct <= -2.5) multiplier += 0.5
                else if (sma20GapPct <= -1.0) multiplier += 0.25
                else if (sma20GapPct >= 5.0) multiplier -= 0.5
                else if (sma20GapPct >= 2.5) multiplier -= 0.25

                if (rsi14 <= 30.0) multiplier += 0.5
                else if (rsi14 <= 40.0) multiplier += 0.25
                else if (rsi14 >= 70.0) multiplier -= 0.25

                if (currentNav < sma50 && currentNav < sma200) multiplier += 0.5
                if (vol20 > 25.0 && sma20GapPct < 0.0) multiplier += 0.25

                val finalMult = multiplier.coerceIn(0.5, 2.5)
                val currentInstallment = baseInstallment * finalMult
                val unitsBought = currentInstallment / currentNav

                smartUnits += unitsBought
                smartInvested += currentInstallment
                smartInstallmentCount++
                smartCashFlows.add(Pair(currentTimestamp, -currentInstallment))
            }

            if (weeklyLowestIndices.contains(i)) {
                val unitsBought = weeklyAmount / currentNav
                weeklyDipUnits += unitsBought
                weeklyDipInvested += weeklyAmount
                weeklyDipInstallmentCount++
                weeklyDipCashFlows.add(Pair(currentTimestamp, -weeklyAmount))
            }

            if (monthlyLowestIndices.contains(i)) {
                val unitsBought = monthlyAmount / currentNav
                monthlyDipUnits += unitsBought
                monthlyDipInvested += monthlyAmount
                monthlyDipInstallmentCount++
                monthlyDipCashFlows.add(Pair(currentTimestamp, -monthlyAmount))
            }

            val dailyUnitsBought = dailyAmount / currentNav
            dailyUnits += dailyUnitsBought
            dailyInvested += dailyAmount
            dailyInstallmentCount++
            dailyCashFlows.add(Pair(currentTimestamp, -dailyAmount))

            if (i % 5 == 0) {
                val weeklyUnitsBought = weeklyAmount / currentNav
                weeklyUnits += weeklyUnitsBought
                weeklyInvested += weeklyAmount
                weeklyInstallmentCount++
                weeklyCashFlows.add(Pair(currentTimestamp, -weeklyAmount))
            }

            if (i % 20 == 0) {
                val monthlyUnitsBought = monthlyAmount / currentNav
                monthlyUnits += monthlyUnitsBought
                monthlyInvested += monthlyAmount
                monthlyInstallmentCount++
                monthlyCashFlows.add(Pair(currentTimestamp, -monthlyAmount))
            }

            val tier = when {
                athPullbackPct <= -15.0 || (sma20GapPct <= -7.0 && rsi14 <= 30.0) -> 1
                athPullbackPct <= -10.0 || (sma20GapPct <= -5.0 && rsi14 <= 35.0) -> 2
                athPullbackPct <= -5.0 || (sma20GapPct <= -2.5 && rsi14 <= 40.0) -> 3
                else -> 4
            }
            val isDeepestSubtier = tier < lastLumpsumTier
            val cooldownPassed = (i - lastLumpsumIdx) >= 15

            if (tier <= 3 && (cooldownPassed || isDeepestSubtier)) {
                val tranche = when (tier) {
                    1 -> lumpsumAmount * 1.5
                    2 -> lumpsumAmount * 1.0
                    else -> lumpsumAmount * 0.5
                }
                val unitsBought = tranche / currentNav
                lumpsumUnits += unitsBought
                lumpsumInvested += tranche
                lumpsumInstallmentCount++
                lumpsumCashFlows.add(Pair(currentTimestamp, -tranche))
                lastLumpsumIdx = i
                lastLumpsumTier = tier
            }

            val smartVal = smartUnits * currentNav
            if (smartVal > smartPeakValue) smartPeakValue = smartVal
            val smartDD = if (smartPeakValue > 0.0) ((smartVal - smartPeakValue) / smartPeakValue) * 100.0 else 0.0
            if (smartDD < smartMaxDrawdown) smartMaxDrawdown = smartDD
            smartGrowth.add(Pair(currentTimestamp, smartVal))

            val weeklyDipVal = weeklyDipUnits * currentNav
            if (weeklyDipVal > weeklyDipPeakValue) weeklyDipPeakValue = weeklyDipVal
            val weeklyDipDD = if (weeklyDipPeakValue > 0.0) ((weeklyDipVal - weeklyDipPeakValue) / weeklyDipPeakValue) * 100.0 else 0.0
            if (weeklyDipDD < weeklyDipMaxDrawdown) weeklyDipMaxDrawdown = weeklyDipDD
            weeklyDipGrowth.add(Pair(currentTimestamp, weeklyDipVal))

            val monthlyDipVal = monthlyDipUnits * currentNav
            if (monthlyDipVal > monthlyDipPeakValue) monthlyDipPeakValue = monthlyDipVal
            val monthlyDipDD = if (monthlyDipPeakValue > 0.0) ((monthlyDipVal - monthlyDipPeakValue) / monthlyDipPeakValue) * 100.0 else 0.0
            if (monthlyDipDD < monthlyDipMaxDrawdown) monthlyDipMaxDrawdown = monthlyDipDD
            monthlyDipGrowth.add(Pair(currentTimestamp, monthlyDipVal))

            val dailyVal = dailyUnits * currentNav
            if (dailyVal > dailyPeakValue) dailyPeakValue = dailyVal
            val dailyDD = if (dailyPeakValue > 0.0) ((dailyVal - dailyPeakValue) / dailyPeakValue) * 100.0 else 0.0
            if (dailyDD < dailyMaxDrawdown) dailyMaxDrawdown = dailyDD
            dailyGrowth.add(Pair(currentTimestamp, dailyVal))

            val weeklyVal = weeklyUnits * currentNav
            if (weeklyVal > weeklyPeakValue) weeklyPeakValue = weeklyVal
            val weeklyDD = if (weeklyPeakValue > 0.0) ((weeklyVal - weeklyPeakValue) / weeklyPeakValue) * 100.0 else 0.0
            if (weeklyDD < weeklyMaxDrawdown) weeklyMaxDrawdown = weeklyDD
            weeklyGrowth.add(Pair(currentTimestamp, weeklyVal))

            val monthlyVal = monthlyUnits * currentNav
            if (monthlyVal > monthlyPeakValue) monthlyPeakValue = monthlyVal
            val monthlyDD = if (monthlyPeakValue > 0.0) ((monthlyVal - monthlyPeakValue) / monthlyPeakValue) * 100.0 else 0.0
            if (monthlyDD < monthlyMaxDrawdown) monthlyMaxDrawdown = monthlyDD
            monthlyGrowth.add(Pair(currentTimestamp, monthlyVal))

            val lumpsumVal = lumpsumUnits * currentNav
            if (lumpsumVal > lumpsumPeakValue) lumpsumPeakValue = lumpsumVal
            val lumpsumDD = if (lumpsumPeakValue > 0.0) ((lumpsumVal - lumpsumPeakValue) / lumpsumPeakValue) * 100.0 else 0.0
            if (lumpsumDD < lumpsumMaxDrawdown) lumpsumMaxDrawdown = lumpsumDD
            lumpsumGrowth.add(Pair(currentTimestamp, lumpsumVal))
        }

        val finalTimestamp = sorted.last().second
        val finalNav = sorted.last().first.navValue
        val computedResults = mutableListOf<BacktestResult>()

        val smartFinalVal = smartUnits * finalNav
        if (smartInvested > 0.0) {
            val flows = smartCashFlows.toMutableList()
            flows.add(Pair(finalTimestamp, smartFinalVal))
            val rawXirr = calculateXIRR(flows) * 100.0
            val safeXirr = if (rawXirr.isNaN() || rawXirr.isInfinite()) 0.0 else rawXirr
            computedResults.add(
                BacktestResult(
                    strategyName = "Smart Tactical SIP 🧠",
                    totalInvested = smartInvested,
                    currentValue = smartFinalVal,
                    absoluteReturnPct = ((smartFinalVal - smartInvested) / smartInvested) * 100.0,
                    xirrPct = safeXirr,
                    peakDrawdownPct = smartMaxDrawdown,
                    growthTimeline = smartGrowth,
                    installmentCount = smartInstallmentCount,
                    formulaDetail = "$smartInstallmentCount wks (₹2.5k base × dip booster)"
                )
            )
        }

        val weeklyDipFinalVal = weeklyDipUnits * finalNav
        if (weeklyDipInvested > 0.0) {
            val flows = weeklyDipCashFlows.toMutableList()
            flows.add(Pair(finalTimestamp, weeklyDipFinalVal))
            val rawXirr = calculateXIRR(flows) * 100.0
            val safeXirr = if (rawXirr.isNaN() || rawXirr.isInfinite()) 0.0 else rawXirr
            computedResults.add(
                BacktestResult(
                    strategyName = "Weekly Dip SIP (Lowest NAV/Wk) 📉🎯",
                    totalInvested = weeklyDipInvested,
                    currentValue = weeklyDipFinalVal,
                    absoluteReturnPct = ((weeklyDipFinalVal - weeklyDipInvested) / weeklyDipInvested) * 100.0,
                    xirrPct = safeXirr,
                    peakDrawdownPct = weeklyDipMaxDrawdown,
                    growthTimeline = weeklyDipGrowth,
                    installmentCount = weeklyDipInstallmentCount,
                    formulaDetail = "$weeklyDipInstallmentCount wks (lowest NAV of week) × ₹2,500"
                )
            )
        }

        val monthlyDipFinalVal = monthlyDipUnits * finalNav
        if (monthlyDipInvested > 0.0) {
            val flows = monthlyDipCashFlows.toMutableList()
            flows.add(Pair(finalTimestamp, monthlyDipFinalVal))
            val rawXirr = calculateXIRR(flows) * 100.0
            val safeXirr = if (rawXirr.isNaN() || rawXirr.isInfinite()) 0.0 else rawXirr
            computedResults.add(
                BacktestResult(
                    strategyName = "Monthly Dip SIP (Lowest NAV/Mo) 📉🎯",
                    totalInvested = monthlyDipInvested,
                    currentValue = monthlyDipFinalVal,
                    absoluteReturnPct = ((monthlyDipFinalVal - monthlyDipInvested) / monthlyDipInvested) * 100.0,
                    xirrPct = safeXirr,
                    peakDrawdownPct = monthlyDipMaxDrawdown,
                    growthTimeline = monthlyDipGrowth,
                    installmentCount = monthlyDipInstallmentCount,
                    formulaDetail = "$monthlyDipInstallmentCount mos (lowest NAV of month) × ₹10,000"
                )
            )
        }

        val dailyFinalVal = dailyUnits * finalNav
        if (dailyInvested > 0.0) {
            val flows = dailyCashFlows.toMutableList()
            flows.add(Pair(finalTimestamp, dailyFinalVal))
            val rawXirr = calculateXIRR(flows) * 100.0
            val safeXirr = if (rawXirr.isNaN() || rawXirr.isInfinite()) 0.0 else rawXirr
            computedResults.add(
                BacktestResult(
                    strategyName = "Blind Daily SIP 📅",
                    totalInvested = dailyInvested,
                    currentValue = dailyFinalVal,
                    absoluteReturnPct = ((dailyFinalVal - dailyInvested) / dailyInvested) * 100.0,
                    xirrPct = safeXirr,
                    peakDrawdownPct = dailyMaxDrawdown,
                    growthTimeline = dailyGrowth,
                    installmentCount = dailyInstallmentCount,
                    formulaDetail = "$dailyInstallmentCount days × ₹500/day"
                )
            )
        }

        val weeklyFinalVal = weeklyUnits * finalNav
        if (weeklyInvested > 0.0) {
            val flows = weeklyCashFlows.toMutableList()
            flows.add(Pair(finalTimestamp, weeklyFinalVal))
            val rawXirr = calculateXIRR(flows) * 100.0
            val safeXirr = if (rawXirr.isNaN() || rawXirr.isInfinite()) 0.0 else rawXirr
            computedResults.add(
                BacktestResult(
                    strategyName = "Blind Weekly SIP (Fixed Day) 📅",
                    totalInvested = weeklyInvested,
                    currentValue = weeklyFinalVal,
                    absoluteReturnPct = ((weeklyFinalVal - weeklyInvested) / weeklyInvested) * 100.0,
                    xirrPct = safeXirr,
                    peakDrawdownPct = weeklyMaxDrawdown,
                    growthTimeline = weeklyGrowth,
                    installmentCount = weeklyInstallmentCount,
                    formulaDetail = "$weeklyInstallmentCount wks (fixed 5th day) × ₹2,500"
                )
            )
        }

        val monthlyFinalVal = monthlyUnits * finalNav
        if (monthlyInvested > 0.0) {
            val flows = monthlyCashFlows.toMutableList()
            flows.add(Pair(finalTimestamp, monthlyFinalVal))
            val rawXirr = calculateXIRR(flows) * 100.0
            val safeXirr = if (rawXirr.isNaN() || rawXirr.isInfinite()) 0.0 else rawXirr
            computedResults.add(
                BacktestResult(
                    strategyName = "Blind Monthly SIP (Fixed Day) 📅",
                    totalInvested = monthlyInvested,
                    currentValue = monthlyFinalVal,
                    absoluteReturnPct = ((monthlyFinalVal - monthlyInvested) / monthlyInvested) * 100.0,
                    xirrPct = safeXirr,
                    peakDrawdownPct = monthlyMaxDrawdown,
                    growthTimeline = monthlyGrowth,
                    installmentCount = monthlyInstallmentCount,
                    formulaDetail = "$monthlyInstallmentCount mos (fixed 20th day) × ₹10,000"
                )
            )
        }

        val lumpsumFinalVal = lumpsumUnits * finalNav
        if (lumpsumInvested > 0.0) {
            val flows = lumpsumCashFlows.toMutableList()
            flows.add(Pair(finalTimestamp, lumpsumFinalVal))
            val rawXirr = calculateXIRR(flows) * 100.0
            val safeXirr = if (rawXirr.isNaN() || rawXirr.isInfinite()) 0.0 else rawXirr
            computedResults.add(
                BacktestResult(
                    strategyName = "Tactical Lumpsum on Dips 💰",
                    totalInvested = lumpsumInvested,
                    currentValue = lumpsumFinalVal,
                    absoluteReturnPct = ((lumpsumFinalVal - lumpsumInvested) / lumpsumInvested) * 100.0,
                    xirrPct = safeXirr,
                    peakDrawdownPct = lumpsumMaxDrawdown,
                    growthTimeline = lumpsumGrowth,
                    installmentCount = lumpsumInstallmentCount,
                    formulaDetail = "$lumpsumInstallmentCount dip buys × ₹20,000"
                )
            )
        }

        return computedResults
    }

    fun buildFundSummaryRow(
        schemeName: String,
        totalNavs: Int,
        results: List<BacktestResult>,
        sorted: List<Pair<HistoricalNav, Long>>? = null
    ): FundSummaryRow {
        val isMidSmallCap = schemeName.contains("Ex-Top 100", ignoreCase = true) ||
                schemeName.contains("Mid", ignoreCase = true) ||
                schemeName.contains("Small", ignoreCase = true) ||
                schemeName.contains("Micro", ignoreCase = true)
        val isAssetAllocator = schemeName.contains("Asset Allocator", ignoreCase = true) ||
                schemeName.contains("Multi Asset", ignoreCase = true) ||
                schemeName.contains("Balanced", ignoreCase = true) ||
                schemeName.contains("Hybrid", ignoreCase = true)

        val categoryLabel = when {
            isMidSmallCap -> "Mid/Small Cap"
            isAssetAllocator -> "Multi-Asset Hybrid"
            else -> "Large/Flexi Cap"
        }

        val rankedSips = results.filter { !it.strategyName.contains("Lumpsum") }.sortedByDescending { it.xirrPct }
        val best = rankedSips.firstOrNull() ?: results.maxByOrNull { it.xirrPct }
        val runnerUp = if (rankedSips.size > 1) rankedSips[1] else null
        val baseline = results.find { it.strategyName.contains("Blind Monthly") }
            ?: results.find { it.strategyName.contains("Blind Weekly") }
            ?: rankedSips.lastOrNull()

        val preferredXirr = best?.xirrPct ?: 0.0
        val baselineXirr = baseline?.xirrPct ?: 0.0
        val alphaMargin = preferredXirr - baselineXirr

        val cleanBestName = (best?.strategyName ?: "Systematic SIP").replace(Regex("[📉🎯🧠📅💰]"), "").trim()
        val cleanRunnerUpName = (runnerUp?.strategyName ?: "Fixed Weekly").replace(Regex("[📉🎯🧠📅💰]"), "").trim()
        val cleanBaselineName = (baseline?.strategyName ?: "Blind Monthly").replace(Regex("[📉🎯🧠📅💰]"), "").trim()

        val keyAdvantage = when {
            cleanBestName.contains("Monthly Dip", ignoreCase = true) -> "Buys monthly trough at lowest NAV (+${String.format(Locale.US, "%.2f%%", alphaMargin)} Alpha)"
            cleanBestName.contains("Smart Tactical", ignoreCase = true) -> "Scales 2.0x–3.0x on pullbacks (+${String.format(Locale.US, "%.2f%%", alphaMargin)} Alpha)"
            cleanBestName.contains("Weekly Dip", ignoreCase = true) -> "Captures intra-week dips (+${String.format(Locale.US, "%.2f%%", alphaMargin)} Alpha)"
            else -> "Outperforms rigid calendar DCA (+${String.format(Locale.US, "%.2f%%", alphaMargin)} Alpha)"
        }

        val predictiveAnalysis = if (sorted != null && sorted.isNotEmpty()) {
            SipPredictiveTimingEngine.analyze(
                schemeName = schemeName,
                category = categoryLabel,
                preferredMethod = cleanBestName,
                sorted = sorted
            )
        } else null

        return FundSummaryRow(
            schemeName = schemeName,
            category = categoryLabel,
            preferredMethod = cleanBestName,
            preferredXirr = preferredXirr,
            preferredWealth = best?.currentValue ?: 0.0,
            baselineMethod = cleanBaselineName,
            baselineXirr = baselineXirr,
            alphaMargin = alphaMargin,
            runnerUpMethod = cleanRunnerUpName,
            runnerUpXirr = runnerUp?.xirrPct ?: 0.0,
            totalNavCount = totalNavs,
            keyAdvantage = keyAdvantage,
            predictiveAnalysis = predictiveAnalysis
        )
    }
}
