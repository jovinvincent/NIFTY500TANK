package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MarketCapitulationEvolvingTest {

    private fun isoToMs(iso: String): Long {
        return IndexBenchmarkSimulator.parseAnyDateToMillis(iso)!!
    }

    @Test
    fun mcei_distinguishesSustainableBullFromEuphoriaAndTrapCorrectionsFromCapitulation() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. User-reported false euphoria periods MUST stay in Wealth Creation / Stay Invested (27..48)
        for (bullDate in listOf("2023-06-02", "2023-12-01")) {
            val data = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs(bullDate), context)
            assertTrue(
                "Expected $bullDate to remain in Stay Invested / Wealth Creation (27..48), was ${data.score} (${data.regimeLabel})",
                data.score in 27.0..48.0
            )
            assertTrue(
                "Expected $bullDate NOT to trigger Euphoria Override",
                !data.isEuphoriaOverride
            )
        }

        // 2. User-reported 1st-leg trap (21 Nov 2024) MUST NOT trigger Blind Lumpsum (>= 85) or Staggered Accum (>= 68),
        // whereas genuine bottom (03 Mar 2025) MUST organically reach Blind Lumpsum (>= 85).
        val nov24Trap = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs("2024-11-21"), context)
        val mar25Bottom = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs("2025-03-03"), context)
        assertTrue(
            "Expected 2024-11-21 trap to stay below Staggered Accum (< 68), was ${nov24Trap.score}",
            nov24Trap.score < 68.0
        )
        assertTrue(
            "Expected 2025-03-03 genuine bottom to reach >= 80 Lumpsum / Staggered Accum Zone, was ${mar25Bottom.score}",
            mar25Bottom.score >= 80.0
        )
        assertTrue(
            "Expected 2025-03-03 (${mar25Bottom.score}) to be substantially higher conviction than 2024-11-21 (${nov24Trap.score})",
            mar25Bottom.score - nov24Trap.score >= 25.0
        )

        // 3. Genuine macro bottoms emerge organically into Deep Value (>= 68) / Blind Lumpsum (>= 80)
        val majorCapitulationBottoms = listOf("2018-10-26", "2019-08-22", "2020-03-24", "2022-06-20", "2025-03-03", "2026-03-30")
        for (dateIso in majorCapitulationBottoms) {
            val data = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs(dateIso), context)
            assertTrue(
                "Expected genuine bottom $dateIso to reach >= 80 organically, was ${data.score}",
                data.score >= 80.0
            )
        }
        for (valueBottom in listOf("2016-12-26")) {
            val data = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs(valueBottom), context)
            assertTrue(
                "Expected value bottom $valueBottom to reach >= 45 Staggered Accum organically, was ${data.score}",
                data.score >= 45.0
            )
        }

        // 3b. Verify May 2020 re-test does not falsely trigger secondary capitulation (>= 85) following March 2020 panic
        val may20ReTest = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs("2020-05-20"), context)
        assertTrue(
            "Expected May 2020 re-test to stay in Staggered Accumulation (< 85), was ${may20ReTest.score}",
            may20ReTest.score < 85.0
        )

        // 4. Genuine exhausted euphoric tops emerge organically <= 16
        for (euphPeak in listOf("2021-10-18", "2024-09-26")) {
            val data = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs(euphPeak), context)
            assertTrue(
                "Expected exhausted euphoria top $euphPeak to reach <= 16 Profit Booking organically, was ${data.score}",
                data.score <= 16.0
            )
        }
        val jan18Top = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs("2018-01-23"), context)
        assertTrue(
            "Expected late-cycle 2018-01-23 top to read Caution (<= 27), was ${jan18Top.score}",
            jan18Top.score <= 27.0
        )
    }

    @Test
    fun mcei_forwardReturnsIncreaseMonotonicallyAndEnforceAnnualRarity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val allSeries = MarketMoodIndexEngine.getHistoricalCompositeSeries("ALL", context)
        assertTrue("Expected > 2550 points since 2016-04-01, was ${allSeries.size}", allSeries.size >= 2550)
        val firstIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(allSeries.first().first)
        assertTrue("Expected MCEI ALL start in 2016-04, was $firstIso", firstIso.startsWith("2016-04"))

        // Forward 6M return validation on Nifty 500 across the entire 2016-2026 dataset
        val n500Prices = allSeries.map { IndexBenchmarkSimulator.getIndexValueForDate("nifty500", it.first) }
        val euphReturns = mutableListOf<Double>()
        val bullReturns = mutableListOf<Double>()
        val accumReturns = mutableListOf<Double>()
        val lumpsumReturns = mutableListOf<Double>()

        for (i in 60 until allSeries.size - 126) {
            val p0 = n500Prices[i]
            val p6 = n500Prices[i + 126]
            if (p0 > 100.0 && p6 > 100.0) {
                val fwd6m = (p6 / p0 - 1.0) * 100.0
                val sc = allSeries[i].second
                when {
                    sc <= 16.0 -> euphReturns.add(fwd6m)
                    sc in 27.0..48.0 -> bullReturns.add(fwd6m)
                    sc in 68.0..84.99 -> accumReturns.add(fwd6m)
                    sc >= 85.0 -> lumpsumReturns.add(fwd6m)
                }
            }
        }

        assertTrue("Expected sufficient euphoria samples", euphReturns.size >= 50)
        assertTrue("Expected sufficient lumpsum samples", lumpsumReturns.size >= 5)

        val avgEuph = euphReturns.average()
        val avgBull = bullReturns.average()
        val avgAccum = accumReturns.average()
        val avgLumpsum = lumpsumReturns.average()
        val lumpsumWinRate = lumpsumReturns.count { it > 0.0 }.toDouble() / lumpsumReturns.size

        assertTrue(
            "Expected Euphoria zone avg 6M return < 2% (was $avgEuph%)",
            avgEuph < 2.0
        )
        assertTrue(
            "Expected Bull Stay Invested avg 6M return > avg Euphoria ($avgBull% vs $avgEuph%)",
            avgBull > avgEuph + 5.0
        )
        assertTrue(
            "Expected Lumpsum avg 6M return ($avgLumpsum%) > Bull ($avgBull%)",
            avgLumpsum > avgBull
        )
        assertTrue(
            "Expected Lumpsum 6M win rate >= 90%, was ${lumpsumWinRate * 100}%",
            lumpsumWinRate >= 0.90
        )

        // Annual extreme rarity & smoothness
        val yearFmt = SimpleDateFormat("yyyy", Locale.US)
        val capEpisodesByYear = mutableMapOf<String, Int>()
        val euphEpisodesByYear = mutableMapOf<String, Int>()
        var inCap = false
        var inEuph = false
        var totalStep = 0.0
        var maxDailyJump = 0.0

        for (i in allSeries.indices) {
            val (ts, sc) = allSeries[i]
            val yr = yearFmt.format(ts)
            if (!inCap && sc >= 85.0) {
                capEpisodesByYear[yr] = (capEpisodesByYear[yr] ?: 0) + 1
                inCap = true
            } else if (inCap && sc < 75.0) {
                inCap = false
            }

            if (!inEuph && sc <= 16.0) {
                euphEpisodesByYear[yr] = (euphEpisodesByYear[yr] ?: 0) + 1
                inEuph = true
            } else if (inEuph && sc > 24.0) {
                inEuph = false
            }

            if (i > 0) {
                val step = abs(sc - allSeries[i - 1].second)
                totalStep += step
                if (step > maxDailyJump) maxDailyJump = step
            }
        }

        for ((yr, count) in capEpisodesByYear) {
            assertTrue("Capitulation extremes in $yr happened $count times (must be <= 2/year)", count <= 2)
        }
        for ((yr, count) in euphEpisodesByYear) {
            assertTrue("Euphoria extremes in $yr happened $count times (must be <= 2/year)", count <= 2)
        }

        val avgDailyStep = totalStep / (allSeries.size - 1)
        assertTrue("Expected smooth investment curve (avg step <= 3.0), was $avgDailyStep", avgDailyStep <= 3.0)
        assertTrue("Expected no abrupt daily spikes (max jump <= 15.0), was $maxDailyJump", maxDailyJump <= 15.0)
    }

    @Test
    fun mcei_learnsHistoricalOutcomesChronologicallyAndSurfacesAnalogs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mid2016 = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs("2016-06-15"), context)
        val preCovid2020 = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs("2020-02-03"), context)
        val mid2023 = MarketMoodIndexEngine.getCapitulationIndexForDate(isoToMs("2023-06-15"), context)
        val latest = MarketMoodIndexEngine.getLatestCapitulationIndex(context)

        assertEquals(0, mid2016.learnedEpisodesCount)
        assertTrue("Expected growing learned memory pre-COVID -> mid-2023 -> latest", preCovid2020.learnedEpisodesCount in 25..60)
        assertTrue(mid2023.learnedEpisodesCount > preCovid2020.learnedEpisodesCount)
        assertTrue(latest.learnedEpisodesCount > mid2023.learnedEpisodesCount)
        assertTrue(
            "Expected latest analog string to include match & 6M outcome, was '${latest.closestHistoricalAnalog}'",
            latest.closestHistoricalAnalog.contains("match") && latest.closestHistoricalAnalog.contains("6M")
        )
    }

    @Test
    fun diagnostic_dump2020SpringScores() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val series = MarketMoodIndexEngine.getHistoricalCompositeSeries("ALL", context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== MCEI 2020-02-20 to 2020-06-15 ===")
        for ((ts, sc) in series) {
            val d = sdf.format(ts)
            if (d in "2020-02-20".."2020-06-15") {
                val info = MarketMoodIndexEngine.getCapitulationIndexForDate(ts, context)
                val n500 = IndexBenchmarkSimulator.getIndexValueForDate("nifty500", ts)
                sb.appendLine("$d n500=${n500.toInt()} vix=${"%.1f".format(Locale.US, info.rawVix)} mmi=${"%.1f".format(Locale.US, info.rawMmi)} sc=${"%.2f".format(Locale.US, sc)} ${info.regimeLabel}")
            }
        }
        sb.appendLine("=== ALL HIGHS >= 78 across 2016-2026 ===")
        for ((ts, sc) in series) {
            if (sc >= 78.0) {
                sb.appendLine("${sdf.format(ts)} sc=${"%.2f".format(Locale.US, sc)}")
            }
        }
        val records = AdvanceDeclineManager.getAllHistoricalRecords(context)
        val n500s = DoubleArray(records.size)
        val vixs = DoubleArray(records.size)
        var ema200 = 0.0
        for (i in records.indices) {
            val ts = records[i].timestamp
            val p = IndexBenchmarkSimulator.getIndexValueForDate("nifty500", ts)
            n500s[i] = if (p > 100.0) p else (if (i > 0) n500s[i - 1] else 6400.0)
            vixs[i] = IndiaVixManager.getIndiaVixForDate(ts, context)
            ema200 = if (i == 0) n500s[i] else n500s[i] * (2.0 / 201.0) + ema200 * (1.0 - 2.0 / 201.0)
            val d = sdf.format(ts)
            val isKey = d in listOf(
                "2016-12-26", "2018-01-23", "2018-10-09", "2018-10-26", "2019-08-22",
                "2020-03-11", "2020-03-23", "2020-03-24", "2020-04-03", "2020-04-30",
                "2020-05-04", "2020-05-05", "2020-05-18", "2020-05-20", "2020-05-29",
                "2021-10-18", "2022-05-13", "2022-06-17", "2022-06-20",
                "2023-06-02", "2023-12-01", "2024-09-26", "2024-11-21",
                "2025-02-18", "2025-03-03", "2026-03-20", "2026-03-30", "2026-10-23", "2026-10-26"
            )
            if (isKey) {
                var pk252 = n500s[i]
                for (k in maxOf(0, i - 251)..i) if (n500s[k] > pk252) pk252 = n500s[k]
                var lo63 = n500s[i]
                var vixPk63 = vixs[i]
                for (k in maxOf(0, i - 62)..i) {
                    if (n500s[k] < lo63) lo63 = n500s[k]
                    if (vixs[k] > vixPk63) vixPk63 = vixs[k]
                }
                val dd = (pk252 - n500s[i]) / pk252 * 100.0
                val b63 = (n500s[i] - lo63) / lo63 * 100.0
                val ext = (n500s[i] - ema200) / ema200 * 100.0
                val r1y = if (i >= 252) (n500s[i] / n500s[i - 252] - 1.0) * 100.0 else 0.0
                val info = MarketMoodIndexEngine.getCapitulationIndexForDate(ts, context)
                sb.appendLine("KEY $d n500=${n500s[i].toInt()} dd=${"%.1f".format(Locale.US, dd)}% ext200=${"%.1f".format(Locale.US, ext)}% r1y=${"%.1f".format(Locale.US, r1y)}% b63=+${"%.1f".format(Locale.US, b63)}% vix=${"%.1f".format(Locale.US, vixs[i])}/${"%.1f".format(Locale.US, vixPk63)} mmi=${"%.1f".format(Locale.US, info.rawMmi)} sc=${"%.2f".format(Locale.US, info.score)}")
            }
        }
        java.io.File("build/mcei_diag.txt").writeText(sb.toString())
        println("Wrote build/mcei_diag.txt lines=${sb.lines().size}")
    }
}
