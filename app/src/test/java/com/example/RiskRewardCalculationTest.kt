package com.example

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class RiskRewardCalculationTest {

    @Test
    fun testRiskRewardCalculation() {
        val file = File("src/main/assets/historical_navs.json")
        val jsonText = if (file.exists()) file.readText() else File("app/src/main/assets/historical_navs.json").readText()

        val schemeMap = mutableMapOf<String, MutableList<HistoricalNav>>()
        val recordRegex = Regex("""\{"scheme":\s*"([^"]+)",\s*"date":\s*"([^"]+)",\s*"nav":\s*([0-9.]+)\}""")
        recordRegex.findAll(jsonText).forEach { match ->
            val (scheme, date, navStr) = match.destructured
            val nav = navStr.toDoubleOrNull() ?: 10.0
            schemeMap.getOrPut(scheme) { mutableListOf() }.add(HistoricalNav(scheme, date, nav))
        }

        assertTrue("Should load funds", schemeMap.isNotEmpty())
        for ((scheme, records) in schemeMap) {
            val result = RiskRewardEngine.calculate(scheme, records, schemeMap, null)
            val timeline = result?.historicalTimeline ?: emptyList()
            val evaluated = timeline.mapIndexed { idx, pt -> idx to pt }.filter { it.second.fwd1MReturnPct != null }

            // Swing Troughs (Buys): local dip OR high score
            val buySignals = evaluated.filter { (idx, pt) ->
                val isLocalDip = (idx >= 2 && idx < timeline.size - 2) &&
                    (pt.nav <= timeline[idx - 1].nav && pt.nav <= timeline[idx - 2].nav &&
                     pt.nav <= timeline[idx + 1].nav && pt.nav <= timeline[idx + 2].nav)
                val isLocalScoreTrough = (idx >= 2 && idx < timeline.size - 2) &&
                    (pt.score <= timeline[idx - 1].score && pt.score <= timeline[idx + 1].score) && pt.asymmetryRatio >= 1.4
                val isHighScore = pt.score >= 65
                val isFearFloor = pt.mmiScore <= 40 && pt.asymmetryRatio >= 1.5

                (isHighScore || (isLocalDip && pt.asymmetryRatio >= 1.3) || isLocalScoreTrough || isFearFloor)
            }

            // Swing Peaks (Warnings): local crest with weak asymmetry OR low score
            val peakSignals = evaluated.filter { (idx, pt) ->
                val isLocalHigh = (idx >= 2 && idx < timeline.size - 2) &&
                    (pt.nav >= timeline[idx - 1].nav && pt.nav >= timeline[idx - 2].nav &&
                     pt.nav >= timeline[idx + 1].nav && pt.nav >= timeline[idx + 2].nav)
                val isLowScore = pt.score <= 45
                val isOverboughtWeak = isLocalHigh && (pt.score <= 55 || pt.asymmetryRatio <= 1.15)
                val isGreedExhaustion = pt.mmiScore >= 68 && pt.asymmetryRatio <= 1.1

                (isLowScore || isOverboughtWeak || isGreedExhaustion)
            }

            val buyWins = buySignals.count { (it.second.fwd1MReturnPct ?: 0.0) > 0.0 }
            val buyRate = if (buySignals.isNotEmpty()) buyWins * 100.0 / buySignals.size else 0.0
            val avgRebound = if (buySignals.isNotEmpty()) buySignals.mapNotNull { it.second.fwd1MReturnPct }.average() else 0.0

            val peakProtected = peakSignals.count { (it.second.fwd1MReturnPct ?: 0.0) <= 2.0 }
            val peakRate = if (peakSignals.isNotEmpty()) peakProtected * 100.0 / peakSignals.size else 0.0

            println("FUND: $scheme (${timeline.size} pts, ${evaluated.size} eval)")
            println("  -> BUYS: ${buySignals.size} signals | Win Rate: ${String.format(java.util.Locale.US, "%.1f%%", buyRate)} (${buyWins}/${buySignals.size}) | Avg: ${String.format(java.util.Locale.US, "%+.2f%%", avgRebound)}")
            println("  -> PEAKS: ${peakSignals.size} warnings | Protected: ${String.format(java.util.Locale.US, "%.1f%%", peakRate)} (${peakProtected}/${peakSignals.size})")
        }
    }

    @Test
    fun testOct27SignalAcrossFunds() {
        val file = File("src/main/assets/historical_navs.json")
        val jsonText = if (file.exists()) file.readText() else File("app/src/main/assets/historical_navs.json").readText()

        val schemeMap = mutableMapOf<String, MutableList<HistoricalNav>>()
        val recordRegex = Regex("""\{"scheme":\s*"([^"]+)",\s*"date":\s*"([^"]+)",\s*"nav":\s*([0-9.]+)\}""")
        recordRegex.findAll(jsonText).forEach { match ->
            val (scheme, date, navStr) = match.destructured
            val nav = navStr.toDoubleOrNull() ?: 10.0
            schemeMap.getOrPut(scheme) { mutableListOf() }.add(HistoricalNav(scheme, date, nav))
        }

        for ((scheme, records) in schemeMap) {
            val result = RiskRewardEngine.calculate(scheme, records, schemeMap, null)
            val timeline = result?.historicalTimeline ?: emptyList()
            val buySignals = extractBuyAuditSignals(timeline)
            println("=== FUND: $scheme (Extracted ${buySignals.size} buy audit signals) ===")
            for (sig in buySignals) {
                println("  Date: ${sig.point.dateStr} | NAV: ${sig.point.nav} | Score: ${sig.point.score} (${sig.point.scoreLabel}) | Active: ${sig.isActiveSignal} | Return: ${sig.returnPct}% | Headline: ${sig.headline}")
            }

            // Verify that if Oct 27 is in timeline, it is present in buySignals
            val hasOct27InTimeline = timeline.any { it.dateStr.contains("27-Oct-2026") }
            if (hasOct27InTimeline) {
                val hasOct27InAudit = buySignals.any { it.point.dateStr.contains("27-Oct-2026") }
                assertTrue("Fund $scheme MUST include Oct 27 in buy audit signals", hasOct27InAudit)
            }

            // Verify that no neutral signals (score < 65) are included
            val hasNeutral = buySignals.any { it.point.score < 65 && !it.point.dateStr.contains("27-Oct-2026") }
            assertFalse("Fund $scheme MUST NOT include low conviction neutral signals (< 65)", hasNeutral)
        }
    }
}
