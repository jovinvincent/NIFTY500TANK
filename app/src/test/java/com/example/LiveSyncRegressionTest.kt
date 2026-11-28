package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.depthtank.data.DataQuality
import com.example.depthtank.data.DatasetIntegrityMetadata
import com.example.depthtank.data.LiveMarketTankDataSource
import com.example.depthtank.data.NseMarketCalendar
import com.example.depthtank.data.RemoteStocksResult
import com.example.depthtank.data.RemoteTankDataSource
import com.example.depthtank.data.StockTank
import com.example.depthtank.data.TankHistoryPoint
import com.example.depthtank.data.TankRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveSyncRegressionTest {

    @Test
    fun tradingViewSymbolSanitizationHandlesAmpersandAndHyphenSymbols() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ds = LiveMarketTankDataSource(ctx)
        val t0 = 1795589400L // 2026-11-25 14:40 IST
        val sampleJson = """
            {
              "data": [
                {
                  "s": "NSE:M_M",
                  "d": ["M_M", "Mahindra & Mahindra", 2950.0, 2.1, 3200.0, 2200.0, "Auto", $t0, 2980.0, 3200.0, 18.0, 7.0]
                },
                {
                  "s": "NSE:J_KBANK",
                  "d": ["J_KBANK", "Jammu & Kashmir Bank", 108.5, 1.4, 130.0, 88.0, "Banking", $t0, 110.0, 130.0, 12.0, 4.0]
                },
                {
                  "s": "NSE:BAJAJ_AUTO",
                  "d": ["BAJAJ_AUTO", "Bajaj Auto", 9400.0, -0.5, 10200.0, 7800.0, "Auto", $t0, 9500.0, 10200.0, 11.0, 3.0]
                }
              ]
            }
        """.trimIndent()
        val symbols = setOf("M&M", "J&KBANK", "BAJAJ-AUTO")
        val (stocks, sessionDate, _) = ds.parseTradingViewMultiDayForTest(sampleJson, symbols)
        assertEquals("2026-11-25", sessionDate)
        assertEquals(setOf("M&M", "J&KBANK", "BAJAJ-AUTO"), stocks.map { it.symbol }.toSet())
    }

    @Test
    fun nseEquityStockIndicesParserExtractsZeroDelayLiveQuotesAndSessionDate() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ds = LiveMarketTankDataSource(ctx)
        val nseJson = """
            {
              "name": "NIFTY 500",
              "timestamp": "25-Nov-2026 09:20:00",
              "metadata": {
                "indexName": "NIFTY 500",
                "timeVal": "25-Nov-2026 09:20:00"
              },
              "data": [
                {
                  "priority": 1,
                  "symbol": "NIFTY 500",
                  "lastPrice": 23450.25,
                  "previousClose": 23300.0,
                  "pChange": 0.64
                },
                {
                  "priority": 0,
                  "symbol": "RELIANCE",
                  "lastPrice": 1412.50,
                  "previousClose": 1390.00,
                  "pChange": 1.62,
                  "dayHigh": 1418.00,
                  "yearHigh": 1608.00,
                  "yearLow": 1114.00
                },
                {
                  "priority": 0,
                  "symbol": "M&M",
                  "lastPrice": 2965.00,
                  "previousClose": 2920.00,
                  "dayHigh": 2975.00,
                  "yearHigh": 3220.00,
                  "yearLow": 2180.00
                }
              ]
            }
        """.trimIndent()

        val outcome = ds.parseNseEquityStockIndicesPayload(nseJson, setOf("RELIANCE", "M&M"))
        assertNotNull(outcome)
        assertEquals("2026-11-25", outcome!!.sessionDate)
        assertEquals(2, outcome.quotesBySymbol.size)
        val rel = outcome.quotesBySymbol["RELIANCE"]!!
        assertEquals(1412.50, rel.lastPrice, 0.001)
        assertEquals(1.62, rel.dailyChangePct, 0.01)
        assertEquals(1418.00, rel.dayHigh ?: 0.0, 0.001)
        val mm = outcome.quotesBySymbol["M&M"]!!
        assertEquals(2965.0, mm.lastPrice, 0.001)
        assertEquals(1.54, mm.dailyChangePct, 0.01)
    }

    @Test
    fun yahooSparkParserHandlesEarlyMorningIntradayTimestampAndPreviousClose() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ds = LiveMarketTankDataSource(ctx)
        // 2026-11-23 15:30 IST = 1795428000, 2026-11-25 09:20 IST = 1795578600
        val tNov23 = 1795428000L
        val tNov25 = 1795578600L
        val yahooJson = """
            {
              "RELIANCE.NS": {
                "timestamp": [$tNov23, $tNov25],
                "close": [1390.0, 1411.5],
                "chartPreviousClose": 1380.0,
                "previousClose": 1390.0
              },
              "M%26M.NS": {
                "timestamp": [$tNov23, $tNov25],
                "close": [2920.0, 2960.0],
                "chartPreviousClose": 2900.0
              }
            }
        """.trimIndent()

        val parsed = ds.parseYahooSparkResponse(
            yahooJson,
            mapOf("RELIANCE.NS" to "RELIANCE", "M%26M.NS" to "M&M")
        )
        assertEquals(2, parsed.size)
        val relBars = parsed["RELIANCE"]!!.bars
        assertEquals("2026-11-23", relBars[0].first)
        assertEquals(1390.0, relBars[0].second, 0.001)
        assertEquals("2026-11-25", relBars[1].first)
        assertEquals(1411.5, relBars[1].second, 0.001)
        assertEquals(listOf("2026-11-23" to 2920.0, "2026-11-25" to 2960.0), parsed["M&M"]!!.bars)
    }

    @Test
    fun repositorySerializesConcurrentRefreshAndNeverMislabelsOldSessionAsLive() = runBlocking {
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

        val concurrentCounter = AtomicInteger(0)
        val maxConcurrentSeen = AtomicInteger(0)

        // Simulate upstream returning Nov 23 snapshot
        val staleSessionRemote = object : RemoteTankDataSource {
            override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> {
                val active = concurrentCounter.incrementAndGet()
                maxConcurrentSeen.updateAndGet { prev -> kotlin.math.max(prev, active) }
                delay(40L)
                concurrentCounter.decrementAndGet()
                return Result.success(
                    RemoteStocksResult(
                        stocks = baseStocks,
                        sourceName = "Test Delayed Feed",
                        sessionDate = "2026-11-23",
                        integrity = DatasetIntegrityMetadata(
                            dataQuality = DataQuality.VERIFIED_EOD,
                            constituentCoverage = 1.0,
                            isIllustrative = false,
                            lastVerifiedTradingDate = "2026-11-23"
                        )
                    )
                )
            }

            override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> =
                Result.failure(IllegalStateException("unused"))
        }

        val repo = TankRepository(ctx, staleSessionRemote)
        repo.ensureInitialized()

        coroutineScope {
            val a = async { repo.refreshLiveMarketData(silent = false) }
            val b = async { repo.refreshLiveMarketData(silent = true) }
            awaitAll(a, b)
        }

        assertEquals(
            "Concurrent refreshLiveMarketData calls must be serialized cleanly",
            1,
            maxConcurrentSeen.get()
        )
        val status = repo.pipelineStatus.value
        assertEquals("2026-11-23", status.lastTradeDate)
        val expectedSessionNow = NseMarketCalendar.latestMarketSessionDate(
            ZonedDateTime.now(NseMarketCalendar.IST_ZONE)
        ).toString()
        if (expectedSessionNow > "2026-11-23") {
            assertFalse(
                "Stale 2026-11-23 session must never be labeled as LIVE when expected session is $expectedSessionNow (was '${status.lastSyncedDisplay}')",
                status.lastSyncedDisplay.startsWith("LIVE:")
            )
        }
        assertTrue(
            "History must remain strictly chronological",
            repo.history.value.zipWithNext().all { (p, n) -> p.date < n.date }
        )
    }
}
