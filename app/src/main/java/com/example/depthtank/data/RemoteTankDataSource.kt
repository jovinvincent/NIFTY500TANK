package com.example.depthtank.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

data class RemoteStocksResult(
    val stocks: List<StockTank>,
    val sourceName: String,
    val sessionDate: String? = null,
    val integrity: DatasetIntegrityMetadata = DatasetIntegrityMetadata(),
    val historicalStocksByDate: Map<String, List<StockTank>> = emptyMap(),
    val bseSymbols: Set<String> = emptySet()
)

data class RemoteMacroSnapshot(
    val vixByDate: Map<String, Double> = emptyMap(),
    val mmiByDate: Map<String, Double> = emptyMap(),
    val adPctByDateAndUniverse: Map<String, Map<TankIndexUniverse, Double>> = emptyMap()
)

data class RemoteHistoryPayload(
    val points: List<TankHistoryPoint>,
    val integrity: DatasetIntegrityMetadata
)

interface RemoteTankDataSource {
    suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult>
    suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>>
    suspend fun fetchLiveMacroSnapshot(): Result<RemoteMacroSnapshot> =
        Result.failure(IllegalStateException("Live macro feed unavailable"))
    suspend fun fetchGapDayStocks(
        missingDates: Set<String>,
        referenceStocks: List<StockTank>,
        bseSymbols: Set<String> = emptySet()
    ): Map<String, List<StockTank>> = emptyMap()
    suspend fun fetchRemoteHistoryWithMetadata(endpointUrl: String?): Result<RemoteHistoryPayload> {
        return fetchRemoteHistoryOverride(endpointUrl).map { pts ->
            val latestDate = pts.lastOrNull()?.date ?: "2026-11-13"
            val activeCount = pts.lastOrNull()?.activeCount ?: 0
            RemoteHistoryPayload(
                points = pts,
                integrity = DatasetIntegrityMetadata(
                    dataQuality = DataQuality.ILLUSTRATIVE_FALLBACK,
                    constituentCoverage = (activeCount.toDouble() / DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS).coerceIn(0.0, 1.0),
                    isIllustrative = true,
                    lastVerifiedTradingDate = latestDate
                )
            )
        }
    }
}

class LiveMarketTankDataSource(
    private val context: Context
) : RemoteTankDataSource {

    companion object {
        private const val TAG = "LiveMarketTankDataSource"
        private const val TRADINGVIEW_SCANNER_URL = "https://scanner.tradingview.com/india/scan"
        private const val TICKERTAPE_MMI_NOW_URL = "https://api.tickertape.in/mmi/now"
        private const val TICKERTAPE_MMI_PAGE_URL = "https://www.tickertape.in/market-mood-index"
        private const val YAHOO_VIX_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%5EINDIAVIX?range=1mo&interval=1d"
        private const val YAHOO_SPARK_PRIMARY_URL = "https://query1.finance.yahoo.com/v8/finance/spark"
        private const val YAHOO_SPARK_SECONDARY_URL = "https://query2.finance.yahoo.com/v8/finance/spark"
        private const val NSE_HOME_URL = "https://www.nseindia.com/market-data/live-equity-market"
        private const val NSE_ALL_INDICES_URL = "https://www.nseindia.com/api/allIndices"
        private const val NSE_NIFTY500_QUOTES_URL = "https://www.nseindia.com/api/equity-stockIndices?index=NIFTY%20500"
        private const val NSE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private val KNOWN_BSE_REITS = setOf("BAGMANE", "BIRET", "EMBASSY")
        private val NSE_TIMESTAMP_FORMATS = listOf(
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US),
            DateTimeFormatter.ISO_LOCAL_DATE
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val nseCookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    @Volatile
    private var lastNseWarmupMs: Long = 0L

    private val nseClient: OkHttpClient by lazy {
        client.newBuilder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val list = nseCookieStore.getOrPut(url.host) { mutableListOf() }
                    synchronized(list) {
                        val now = System.currentTimeMillis()
                        list.removeAll { existing ->
                            existing.expiresAt < now || cookies.any { it.name == existing.name && it.path == existing.path }
                        }
                        list.addAll(cookies)
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val now = System.currentTimeMillis()
                    val matched = ArrayList<Cookie>()
                    for (cookies in nseCookieStore.values) {
                        synchronized(cookies) {
                            cookies.removeAll { it.expiresAt < now }
                            for (c in cookies) {
                                if (c.matches(url)) matched.add(c)
                            }
                        }
                    }
                    return matched
                }
            })
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private data class ScannerQueryOutcome(
        val stocks: List<StockTank>,
        val sessionDate: String?,
        val historicalStocksByDate: Map<String, List<StockTank>> = emptyMap()
    )

    internal data class LiveQuoteOverlay(
        val lastPrice: Double,
        val dailyChangePct: Double,
        val barDate: String,
        val dayHigh: Double? = null,
        val yearHigh: Double? = null,
        val yearLow: Double? = null
    )

    internal data class LiveOverlayOutcome(
        val sourceLabel: String,
        val sessionDate: String,
        val quotesBySymbol: Map<String, LiveQuoteOverlay>
    )

    internal data class YahooSparkSeries(
        val bars: List<Pair<String, Double>>,
        val previousClose: Double?
    )

    private fun toTradingViewTickerSymbol(symbol: String): String =
        symbol.replace("-", "_").replace("&", "_")

    override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> = withContext(Dispatchers.IO) {
        try {
            val nifty500Universe = loadNifty500Symbols()
            if (nifty500Universe.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Nifty 500 constituent list unavailable."))
            }
            val symbolSet = nifty500Universe.toHashSet()
            val nowIst = ZonedDateTime.now(IST_ZONE)
            val expectedLatestSession = NseMarketCalendar.latestMarketSessionDate(nowIst).toString()
            val isRealtimeWindow = NseMarketCalendar.isMarketOpen(nowIst) ||
                NseMarketCalendar.isPostCloseSettlementWindow(nowIst)

            val (tvOutcome, nseLiveOverlay) = coroutineScope {
                val tvDeferred = async {
                    runCatching {
                        val nseTickers = nifty500Universe.map { "NSE:${toTradingViewTickerSymbol(it)}" }
                        executeScannerQuery(nseTickers, symbolSet)
                    }.getOrNull()
                }
                val nseLiveDeferred = async {
                    if (isRealtimeWindow) {
                        runCatching { fetchNseLiveEquityOverlay(symbolSet, expectedLatestSession) }.getOrNull()
                    } else {
                        null
                    }
                }
                tvDeferred.await() to nseLiveDeferred.await()
            }

            val combined = LinkedHashMap<String, StockTank>(512)
            if (tvOutcome != null) {
                for (stock in tvOutcome.stocks) {
                    combined[stock.symbol] = stock
                }
            }
            val historicalMerged = LinkedHashMap<String, LinkedHashMap<String, StockTank>>()
            if (tvOutcome != null) {
                for ((dt, list) in tvOutcome.historicalStocksByDate) {
                    val map = historicalMerged.getOrPut(dt) { LinkedHashMap(512) }
                    for (st in list) map[st.symbol] = st
                }
            }

            // Fallback to BSE exchange for Nifty 500 REITs / constituents not indexed under NSE: (e.g. BAGMANE, BIRET, EMBASSY)
            val missingAfterNseTv = nifty500Universe.filter { it !in combined }
            val bseSymbolsFound = LinkedHashSet<String>(KNOWN_BSE_REITS)
            var tvSessionDate = tvOutcome?.sessionDate
            if (tvOutcome != null && missingAfterNseTv.isNotEmpty() && missingAfterNseTv.size < 120) {
                val bseTickers = missingAfterNseTv.map { "BSE:${toTradingViewTickerSymbol(it)}" }
                val bseOutcome = runCatching { executeScannerQuery(bseTickers, symbolSet) }.getOrNull()
                if (bseOutcome != null) {
                    for (stock in bseOutcome.stocks) {
                        if (stock.symbol !in combined) {
                            combined[stock.symbol] = stock
                            bseSymbolsFound.add(stock.symbol)
                        }
                    }
                    for ((dt, list) in bseOutcome.historicalStocksByDate) {
                        val map = historicalMerged.getOrPut(dt) { LinkedHashMap(16) }
                        for (st in list) {
                            if (st.symbol !in map) map[st.symbol] = st
                        }
                    }
                    if (tvSessionDate == null) {
                        tvSessionDate = bseOutcome.sessionDate
                    }
                }
            }

            val tvEligibleCount = TankCalculator.filterEligibleConstituents(combined.values.toList()).size
            val tvIsBehindExpected = tvSessionDate == null || tvSessionDate < expectedLatestSession
            val needsZeroDelayOverlay = isRealtimeWindow || tvIsBehindExpected || tvEligibleCount < 450

            var activeOverlay: LiveOverlayOutcome? = nseLiveOverlay
                ?.takeIf { it.quotesBySymbol.size >= 450 && (tvSessionDate == null || it.sessionDate >= tvSessionDate) }

            if (needsZeroDelayOverlay && activeOverlay == null) {
                // If after-hours and TradingView lagged or failed, also try NSE before falling back to Yahoo Spark
                if (!isRealtimeWindow) {
                    activeOverlay = runCatching { fetchNseLiveEquityOverlay(symbolSet, expectedLatestSession) }
                        .getOrNull()
                        ?.takeIf { it.quotesBySymbol.size >= 450 && (tvSessionDate == null || it.sessionDate >= tvSessionDate) }
                }
                if (activeOverlay == null) {
                    activeOverlay = runCatching {
                        fetchYahooLiveOverlay(
                            symbols = nifty500Universe,
                            bseSymbols = bseSymbolsFound,
                            expectedLatestSession = expectedLatestSession
                        )
                    }.getOrNull()?.takeIf {
                        it.quotesBySymbol.size >= 450 && (tvSessionDate == null || it.sessionDate >= tvSessionDate)
                    }
                }
            }

            // If TradingView failed or returned incomplete constituents, hydrate base metadata/ranges from local reference
            if (tvEligibleCount < 450) {
                val refStocks = loadReferenceStocksFallback()
                for (ref in refStocks) {
                    if (ref.symbol in symbolSet && ref.symbol !in combined) {
                        combined[ref.symbol] = ref
                    }
                }
            }

            var resolvedSessionDate = tvSessionDate
            var sourceDisplayPrefix = "NSE/BSE Live Constituent Feed"

            if (activeOverlay != null && (tvSessionDate == null || activeOverlay.sessionDate >= tvSessionDate)) {
                val overlaySession = activeOverlay.sessionDate
                // Preserve TradingView's prior trading session snapshot into history before upgrading to today's live session
                if (tvSessionDate != null &&
                    tvSessionDate < overlaySession &&
                    NseMarketCalendar.isValidTradingIsoDate(tvSessionDate) &&
                    tvEligibleCount >= 450 &&
                    tvSessionDate !in historicalMerged
                ) {
                    val tvDayMap = LinkedHashMap<String, StockTank>(combined.size)
                    for ((sym, st) in combined) tvDayMap[sym] = st
                    historicalMerged[tvSessionDate] = tvDayMap
                }

                for ((sym, quote) in activeOverlay.quotesBySymbol) {
                    val base = combined[sym] ?: continue
                    // Only apply quotes that are at least as fresh as TradingView's bar
                    if (tvSessionDate != null && quote.barDate < tvSessionDate && overlaySession == tvSessionDate) {
                        continue
                    }
                    val newPrice = quote.lastPrice
                    val isNewSessionFromBase = tvSessionDate != null && overlaySession > tvSessionDate
                    val tradedInTargetSession = quote.barDate == overlaySession
                    val newChg = if (tradedInTargetSession) quote.dailyChangePct else 0.0
                    val newTodayHigh = when {
                        tradedInTargetSession && quote.dayHigh != null && quote.dayHigh > 0.0 -> max(quote.dayHigh, newPrice)
                        isNewSessionFromBase -> newPrice
                        else -> max(base.todayHigh, newPrice)
                    }
                    val newLow52 = if (quote.yearLow != null && quote.yearHigh != null && quote.yearLow > 0.0 && quote.yearHigh > quote.yearLow) {
                        min(quote.yearLow, newPrice)
                    } else {
                        min(base.low52W, newPrice)
                    }
                    val newHigh52 = if (quote.yearLow != null && quote.yearHigh != null && quote.yearLow > 0.0 && quote.yearHigh > quote.yearLow) {
                        max(quote.yearHigh, newTodayHigh)
                    } else {
                        max(base.high52W, newTodayHigh)
                    }
                    val newPrior52 = if (isNewSessionFromBase) base.high52W else base.prior52WHigh
                    val updatedPe = base.epsTtm
                        ?.takeIf { it.isFinite() && it > 0.0001 }
                        ?.let { eps -> (newPrice / eps).takeIf { it.isFinite() && it > 0.0 && it < 5000.0 } }
                        ?: base.peTtm
                    val updatedPeLow = if (updatedPe != null && base.peLow52W != null) min(base.peLow52W, updatedPe) else base.peLow52W
                    val updatedPeHigh = if (updatedPe != null && base.peHigh52W != null) max(base.peHigh52W, updatedPe) else base.peHigh52W

                    combined[sym] = base.copy(
                        currentPrice = newPrice,
                        low52W = newLow52,
                        high52W = newHigh52,
                        dailyChangePct = newChg,
                        todayHigh = newTodayHigh,
                        prior52WHigh = newPrior52,
                        peTtm = updatedPe,
                        peLow52W = updatedPeLow,
                        peHigh52W = updatedPeHigh
                    )
                }
                resolvedSessionDate = overlaySession
                sourceDisplayPrefix = activeOverlay.sourceLabel
            }

            val parsed = combined.values.toList()
            val eligible = TankCalculator.filterEligibleConstituents(parsed)
            if (eligible.size < 450) {
                return@withContext Result.failure(
                    IllegalStateException("Insufficient eligible 52-Week constituent coverage (${eligible.size}/500)")
                )
            }
            val excludedCount = (parsed.size - eligible.size).coerceAtLeast(0)
            val coverage = round((eligible.size.toDouble() / DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS) * 10000.0) / 10000.0
            val integrity = DatasetIntegrityMetadata(
                dataQuality = DataQuality.VERIFIED_EOD,
                constituentCoverage = coverage.coerceIn(0.0, 1.0),
                isIllustrative = false,
                lastVerifiedTradingDate = resolvedSessionDate ?: expectedLatestSession,
                excludedInsufficientHistoryCount = excludedCount
            )
            val eligibleHistorical = historicalMerged.mapValues { (_, map) ->
                TankCalculator.filterEligibleConstituents(map.values.toList()).sortedByDescending { it.score }
            }.filterValues { it.size >= 450 }

            Result.success(
                RemoteStocksResult(
                    stocks = eligible.sortedByDescending { it.score },
                    sourceName = "$sourceDisplayPrefix (${eligible.size}/500)",
                    sessionDate = resolvedSessionDate,
                    integrity = integrity,
                    historicalStocksByDate = eligibleHistorical,
                    bseSymbols = bseSymbolsFound
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Live scanner refresh skipped/offline: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun fetchLiveMacroSnapshot(): Result<RemoteMacroSnapshot> = withContext(Dispatchers.IO) {
        coroutineScope {
            val tvVixDeferred = async { runCatching { fetchTradingViewVix() }.getOrDefault(emptyMap()) }
            val yahooVixDeferred = async { runCatching { fetchYahooVixHistory() }.getOrDefault(emptyMap()) }
            val mmiNowDeferred = async { runCatching { fetchTickertapeMmiNow() }.getOrNull() }
            val mmiDailyDeferred = async { runCatching { fetchTickertapeDailyMmiPage() }.getOrDefault(emptyMap()) }
            val nseIndicesDeferred = async { runCatching { fetchNseIndicesMacro() }.getOrNull() }

            val tvVix = tvVixDeferred.await()
            val yahooVix = yahooVixDeferred.await()
            val mmiNow = mmiNowDeferred.await()
            val mmiPageDaily = mmiDailyDeferred.await()
            val nseMacro = nseIndicesDeferred.await()

            val mergedVix = LinkedHashMap<String, Double>()
            // Precedence: Yahoo 1mo history -> NSE live fallback -> Tickertape VIX -> TradingView NSE:INDIAVIX (freshest)
            mergedVix.putAll(yahooVix)
            nseMacro?.first?.let { (dt, v) ->
                if (v > 0.0 && dt !in mergedVix) mergedVix[dt] = v
            }
            mmiNow?.second?.forEach { (dt, v) ->
                if (v > 0.0) mergedVix[dt] = v
            }
            mergedVix.putAll(tvVix)

            val mergedMmi = LinkedHashMap<String, Double>()
            // Precedence: Tickertape page periodicDailyMMI -> Tickertape live /mmi/now
            mergedMmi.putAll(mmiPageDaily)
            mmiNow?.first?.forEach { (dt, v) ->
                if (v >= 0.0) mergedMmi[dt] = v
            }

            val mergedAd = LinkedHashMap<String, Map<TankIndexUniverse, Double>>()
            nseMacro?.second?.let { (dt, uMap) ->
                if (uMap.isNotEmpty()) mergedAd[dt] = uMap
            }

            if (mergedVix.isEmpty() && mergedMmi.isEmpty() && mergedAd.isEmpty()) {
                Result.failure(IllegalStateException("All live macro endpoints unavailable"))
            } else {
                Result.success(
                    RemoteMacroSnapshot(
                        vixByDate = mergedVix,
                        mmiByDate = mergedMmi,
                        adPctByDateAndUniverse = mergedAd
                    )
                )
            }
        }
    }

    override suspend fun fetchGapDayStocks(
        missingDates: Set<String>,
        referenceStocks: List<StockTank>,
        bseSymbols: Set<String>
    ): Map<String, List<StockTank>> = withContext(Dispatchers.IO) {
        if (missingDates.isEmpty() || referenceStocks.isEmpty()) return@withContext emptyMap()
        val refBySymbol = referenceStocks.associateBy { it.symbol }
        val symbols = refBySymbol.keys.toList()
        val chunks = symbols.chunked(20)
        val semaphore = Semaphore(6)

        val perSymbolSeries = LinkedHashMap<String, YahooSparkSeries>(symbols.size)
        val chunkResults = coroutineScope {
            chunks.map { chunk ->
                async {
                    semaphore.withPermit {
                        runCatching { fetchYahooSparkSeriesBatch(chunk, bseSymbols, range = "1mo") }.getOrDefault(emptyMap())
                    }
                }
            }.awaitAll()
        }
        for (batch in chunkResults) {
            perSymbolSeries.putAll(batch)
        }
        if (perSymbolSeries.isEmpty()) return@withContext emptyMap()

        val result = LinkedHashMap<String, List<Pair<String, Double>>>(symbols.size)
        for ((sym, sData) in perSymbolSeries) {
            result[sym] = sData.bars
        }

        val dayOut = LinkedHashMap<String, List<StockTank>>(missingDates.size)
        for (targetDate in missingDates.sorted()) {
            val dayStocks = ArrayList<StockTank>(refBySymbol.size)
            for ((sym, ref) in refBySymbol) {
                val series = result[sym] ?: continue
                val idx = series.indexOfLast { it.first == targetDate }
                if (idx < 0) continue
                val closeVal = series[idx].second
                if (closeVal <= 0.0 || closeVal.isNaN()) continue
                val prevClose = when {
                    idx > 0 -> series[idx - 1].second
                    else -> perSymbolSeries[sym]?.previousClose ?: closeVal
                }
                val chgPct = if (prevClose > 0.0) {
                    round(((closeVal - prevClose) / prevClose) * 10000.0) / 100.0
                } else {
                    0.0
                }
                val effHigh52 = max(ref.high52W, closeVal)
                val effLow52 = min(ref.low52W, closeVal)
                val gapPe: Double? = ref.epsTtm
                    ?.takeIf { it.isFinite() && it > 0.0001 }
                    ?.let { eps -> (closeVal / eps).takeIf { it.isFinite() && it > 0.0 && it < 5000.0 } }
                    ?: ref.peTtm
                val effPeLow52 = if (gapPe != null && ref.peLow52W != null) min(ref.peLow52W, gapPe) else ref.peLow52W
                val effPeHigh52 = if (gapPe != null && ref.peHigh52W != null) max(ref.peHigh52W, gapPe) else ref.peHigh52W
                dayStocks.add(
                    ref.copy(
                        currentPrice = closeVal,
                        low52W = effLow52,
                        high52W = effHigh52,
                        dailyChangePct = chgPct,
                        todayHigh = closeVal,
                        prior52WHigh = null,
                        peTtm = gapPe,
                        peLow52W = effPeLow52,
                        peHigh52W = effPeHigh52
                    )
                )
            }
            if (dayStocks.size >= 450) {
                dayOut[targetDate] = dayStocks.sortedByDescending { it.score }
            }
        }
        dayOut
    }

    private suspend fun fetchYahooLiveOverlay(
        symbols: List<String>,
        bseSymbols: Set<String>,
        expectedLatestSession: String
    ): LiveOverlayOutcome? = coroutineScope {
        if (symbols.isEmpty()) return@coroutineScope null
        val chunks = symbols.chunked(25)
        val semaphore = Semaphore(8)
        val perSymbolSeries = LinkedHashMap<String, YahooSparkSeries>(symbols.size)
        val chunkResults = chunks.map { chunk ->
            async {
                semaphore.withPermit {
                    runCatching { fetchYahooSparkSeriesBatch(chunk, bseSymbols, range = "5d") }.getOrDefault(emptyMap())
                }
            }
        }.awaitAll()
        for (batch in chunkResults) {
            perSymbolSeries.putAll(batch)
        }
        if (perSymbolSeries.size < 400) return@coroutineScope null

        val dateCounts = HashMap<String, Int>(8)
        val validSeries = LinkedHashMap<String, YahooSparkSeries>(perSymbolSeries.size)
        for ((sym, sData) in perSymbolSeries) {
            val filteredBars = sData.bars.filter { (dt, _) ->
                dt <= expectedLatestSession && NseMarketCalendar.isValidTradingIsoDate(dt)
            }
            if (filteredBars.isEmpty()) continue
            val lastDt = filteredBars.last().first
            dateCounts[lastDt] = (dateCounts[lastDt] ?: 0) + 1
            validSeries[sym] = sData.copy(bars = filteredBars)
        }
        if (dateCounts.isEmpty()) return@coroutineScope null

        val expectedCount = dateCounts[expectedLatestSession] ?: 0
        val resolvedSession = if (expectedCount >= 200) {
            expectedLatestSession
        } else {
            dateCounts.maxByOrNull { it.value }?.key ?: return@coroutineScope null
        }

        val quotes = LinkedHashMap<String, LiveQuoteOverlay>(validSeries.size)
        for ((sym, sData) in validSeries) {
            val bars = sData.bars
            val lastBar = bars.last()
            val closeVal = lastBar.second
            if (closeVal <= 0.0 || !closeVal.isFinite()) continue
            val prevClose = if (lastBar.first == resolvedSession) {
                if (bars.size >= 2) bars[bars.size - 2].second else (sData.previousClose ?: closeVal)
            } else {
                closeVal
            }
            val chgPct = if (lastBar.first == resolvedSession && prevClose > 0.0) {
                round(((closeVal - prevClose) / prevClose) * 10000.0) / 100.0
            } else {
                0.0
            }
            quotes[sym] = LiveQuoteOverlay(
                lastPrice = closeVal,
                dailyChangePct = chgPct,
                barDate = lastBar.first
            )
        }
        if (quotes.size < 450) null
        else LiveOverlayOutcome(
            sourceLabel = "Yahoo/TV Real-Time Live",
            sessionDate = resolvedSession,
            quotesBySymbol = quotes
        )
    }

    private fun fetchYahooSparkSeriesBatch(
        symbols: List<String>,
        bseSymbols: Set<String>,
        range: String
    ): Map<String, YahooSparkSeries> {
        val yahooToAppSym = LinkedHashMap<String, String>(symbols.size)
        val encodedTickers = symbols.map { sym ->
            val suffix = if (sym in bseSymbols) ".BO" else ".NS"
            val yf = "$sym$suffix"
            yahooToAppSym[yf] = sym
            URLEncoder.encode(yf, "UTF-8")
        }
        val query = "symbols=${encodedTickers.joinToString(",")}&range=$range&interval=1d"
        for (baseUrl in listOf(YAHOO_SPARK_PRIMARY_URL, YAHOO_SPARK_SECONDARY_URL)) {
            val req = Request.Builder()
                .url("$baseUrl?$query")
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()
            val parsed = runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyMap()
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use emptyMap()
                    parseYahooSparkResponse(body, yahooToAppSym)
                }
            }.getOrDefault(emptyMap())
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyMap()
    }

    internal fun parseYahooSparkResponse(
        body: String,
        yahooToAppSym: Map<String, String> = emptyMap()
    ): Map<String, YahooSparkSeries> {
        val root = JSONObject(body)
        val out = LinkedHashMap<String, YahooSparkSeries>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val yfKey = keys.next()
            val appSym = yahooToAppSym[yfKey]
                ?: yfKey.removeSuffix(".NS").removeSuffix(".BO").uppercase(Locale.US)
            val node = root.optJSONObject(yfKey) ?: continue
            val tsArr = node.optJSONArray("timestamp") ?: continue
            val closeArr = node.optJSONArray("close") ?: continue
            val len = min(tsArr.length(), closeArr.length())
            val dedupByDate = LinkedHashMap<String, Double>(len)
            for (i in 0 until len) {
                val ts = tsArr.optLong(i, 0L)
                val c = closeArr.optDouble(i, Double.NaN)
                if (ts > 1_600_000_000L && c.isFinite() && c > 0.0) {
                    val dt = Instant.ofEpochSecond(ts).atZone(IST_ZONE).toLocalDate().toString()
                    dedupByDate[dt] = c
                }
            }
            val prevClose = sequenceOf("previousClose", "chartPreviousClose")
                .map { key -> node.optDouble(key, Double.NaN) }
                .firstOrNull { it.isFinite() && it > 0.0 }
            if (dedupByDate.isNotEmpty()) {
                out[appSym] = YahooSparkSeries(
                    bars = dedupByDate.entries.map { it.key to it.value }.sortedBy { it.first },
                    previousClose = prevClose
                )
            }
        }
        return out
    }

    private fun fetchTradingViewVix(): Map<String, Double> {
        val payload = JSONObject().apply {
            put(
                "symbols",
                JSONObject().apply {
                    put("tickers", JSONArray().put("NSE:INDIAVIX"))
                    put("query", JSONObject().apply { put("types", JSONArray()) })
                }
            )
            put(
                "columns",
                JSONArray().apply {
                    put("time"); put("close")
                    put("time[1]"); put("close[1]")
                    put("time[2]"); put("close[2]")
                }
            )
        }
        val req = Request.Builder()
            .url(TRADINGVIEW_SCANNER_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) Nifty500DepthTank")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyMap()
            val body = resp.body?.string().orEmpty()
            val data = JSONObject(body).optJSONArray("data") ?: return emptyMap()
            val row = data.optJSONObject(0)?.optJSONArray("d") ?: return emptyMap()
            val out = LinkedHashMap<String, Double>()
            for (pairIdx in listOf(0, 2, 4)) {
                if (pairIdx + 1 >= row.length()) continue
                val ts = row.optLong(pairIdx, 0L)
                val v = row.optDouble(pairIdx + 1, Double.NaN)
                if (ts > 1_600_000_000L && !v.isNaN() && v > 0.0) {
                    val dt = Instant.ofEpochSecond(ts).atZone(IST_ZONE).toLocalDate().toString()
                    out[dt] = round(v * 100.0) / 100.0
                }
            }
            return out
        }
    }

    private fun fetchYahooVixHistory(): Map<String, Double> {
        val req = Request.Builder()
            .url(YAHOO_VIX_CHART_URL)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyMap()
            val root = JSONObject(resp.body?.string().orEmpty())
            val res = root.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0) ?: return emptyMap()
            val tsArr = res.optJSONArray("timestamp") ?: return emptyMap()
            val closeArr = res.optJSONObject("indicators")
                ?.optJSONArray("quote")
                ?.optJSONObject(0)
                ?.optJSONArray("close") ?: return emptyMap()
            val out = LinkedHashMap<String, Double>()
            val n = min(tsArr.length(), closeArr.length())
            for (i in 0 until n) {
                val ts = tsArr.optLong(i, 0L)
                val v = closeArr.optDouble(i, Double.NaN)
                if (ts > 1_600_000_000L && !v.isNaN() && v > 0.0) {
                    val dt = Instant.ofEpochSecond(ts).atZone(IST_ZONE).toLocalDate().toString()
                    out[dt] = round(v * 100.0) / 100.0
                }
            }
            val meta = res.optJSONObject("meta")
            val regTime = meta?.optLong("regularMarketTime", 0L) ?: 0L
            val regPrice = meta?.optDouble("regularMarketPrice", Double.NaN) ?: Double.NaN
            if (regTime > 1_600_000_000L && regPrice.isFinite() && regPrice > 0.0) {
                val dt = Instant.ofEpochSecond(regTime).atZone(IST_ZONE).toLocalDate().toString()
                out[dt] = round(regPrice * 100.0) / 100.0
            }
            return out
        }
    }

    private fun fetchTickertapeMmiNow(): Pair<Map<String, Double>, Map<String, Double>>? {
        val req = Request.Builder()
            .url(TICKERTAPE_MMI_NOW_URL)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val root = JSONObject(resp.body?.string().orEmpty())
            if (!root.optBoolean("success", false)) return null
            val data = root.optJSONObject("data") ?: return null
            val mmiMap = LinkedHashMap<String, Double>()
            val vixMap = LinkedHashMap<String, Double>()

            fun captureNode(node: JSONObject?) {
                if (node == null) return
                val rawDate = node.optString("date", "")
                val dt = parseTickertapeIsoToDate(rawDate) ?: return
                val mmiVal = node.optDouble("indicator", Double.NaN)
                if (!mmiVal.isNaN() && mmiVal in 0.0..100.0) {
                    mmiMap[dt] = round(mmiVal * 100.0) / 100.0
                }
                val vixRaw = node.optDouble("vix", Double.NaN)
                if (!vixRaw.isNaN() && abs(vixRaw) > 1.0) {
                    vixMap[dt] = round(abs(vixRaw) * 100.0) / 100.0
                }
            }

            captureNode(data.optJSONObject("lastWeek"))
            captureNode(data.optJSONObject("lastDay"))
            captureNode(data)
            val currentVal = data.optDouble("currentValue", Double.NaN)
            val curDt = parseTickertapeIsoToDate(data.optString("date", ""))
            if (curDt != null && !currentVal.isNaN() && currentVal in 0.0..100.0) {
                mmiMap[curDt] = round(currentVal * 100.0) / 100.0
            }
            return mmiMap to vixMap
        }
    }

    private fun fetchTickertapeDailyMmiPage(): Map<String, Double> {
        val req = Request.Builder()
            .url(TICKERTAPE_MMI_PAGE_URL)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyMap()
            val html = resp.body?.string().orEmpty()
            val marker = "__NEXT_DATA__"
            val idx = html.indexOf(marker)
            if (idx < 0) return emptyMap()
            val startTagEnd = html.indexOf('>', idx)
            if (startTagEnd < 0) return emptyMap()
            val endTag = html.indexOf("</script>", startTagEnd)
            if (endTag < 0) return emptyMap()
            val jsonStr = html.substring(startTagEnd + 1, endTag).trim()
            val nextData = JSONObject(jsonStr)
            val queries = nextData.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("dehydratedState")
                ?.optJSONArray("queries") ?: return emptyMap()
            val out = LinkedHashMap<String, Double>()
            for (i in 0 until queries.length()) {
                val dataObj = queries.optJSONObject(i)
                    ?.optJSONObject("state")
                    ?.optJSONObject("data") ?: continue
                val arr = dataObj.optJSONArray("periodicDailyMMI") ?: continue
                for (j in 0 until arr.length()) {
                    val item = arr.optJSONObject(j) ?: continue
                    val dt = parseTickertapeIsoToDate(item.optString("date", "")) ?: continue
                    val valMmi = item.optDouble("indicator", Double.NaN)
                    if (!valMmi.isNaN() && valMmi in 0.0..100.0) {
                        out[dt] = round(valMmi * 100.0) / 100.0
                    }
                }
            }
            return out
        }
    }

    private fun ensureNseCookies(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && nseCookieStore.isNotEmpty() && (now - lastNseWarmupMs) < 300_000L) return
        val warmupReq = Request.Builder()
            .url(NSE_HOME_URL)
            .get()
            .header("User-Agent", NSE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        runCatching {
            nseClient.newCall(warmupReq).execute().use { it.body?.close() }
            lastNseWarmupMs = System.currentTimeMillis()
        }
    }

    private fun executeNseJsonGet(url: String): String? {
        ensureNseCookies(force = false)
        for (attempt in 0..1) {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", NSE_USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", NSE_HOME_URL)
                .header("Cache-Control", "no-cache")
                .build()
            val (code, body) = runCatching {
                nseClient.newCall(req).execute().use { resp ->
                    resp.code to (if (resp.isSuccessful) resp.body?.string() else null)
                }
            }.getOrDefault(0 to null)
            if (code == 200 && !body.isNullOrBlank()) return body
            if (attempt == 0 && (code == 401 || code == 403 || code == 0)) {
                ensureNseCookies(force = true)
            } else {
                break
            }
        }
        return null
    }

    private fun fetchNseLiveEquityOverlay(
        allowedSymbols: Set<String>,
        expectedLatestSession: String
    ): LiveOverlayOutcome? {
        val jsonText = executeNseJsonGet(NSE_NIFTY500_QUOTES_URL) ?: return null
        val parsed = parseNseEquityStockIndicesPayload(jsonText, allowedSymbols) ?: return null
        if (parsed.sessionDate > expectedLatestSession) return null
        return parsed
    }

    internal fun parseNseEquityStockIndicesPayload(
        jsonText: String,
        allowedSymbols: Set<String>
    ): LiveOverlayOutcome? {
        val root = JSONObject(jsonText)
        val metadata = root.optJSONObject("metadata")
        val rawTs = metadata?.optString("timeVal", "")
            ?.ifBlank { root.optString("timestamp", "") }
            ?: root.optString("timestamp", "")
        val sessionDate = parseNseDateTimeToIsoDate(rawTs)
            ?.takeIf { NseMarketCalendar.isValidTradingIsoDate(it) }
            ?: return null
        val dataArr = root.optJSONArray("data") ?: return null
        val quotes = LinkedHashMap<String, LiveQuoteOverlay>(512)
        for (i in 0 until dataArr.length()) {
            val row = dataArr.optJSONObject(i) ?: continue
            val sym = row.optString("symbol", "").trim().uppercase(Locale.US)
            if (sym.isEmpty() || sym == "NIFTY 500" || row.optInt("priority", 0) == 1) continue
            if (sym !in allowedSymbols) continue
            val lastPrice = row.optDouble("lastPrice", Double.NaN).let {
                if (it.isFinite() && it > 0.0) it else row.optDouble("close", Double.NaN)
            }
            if (!lastPrice.isFinite() || lastPrice <= 0.0) continue
            val prevClose = row.optDouble("previousClose", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            val rawPChg = row.optDouble("pChange", Double.NaN)
            val chgPct = when {
                rawPChg.isFinite() -> round(rawPChg * 100.0) / 100.0
                prevClose != null -> round(((lastPrice - prevClose) / prevClose) * 10000.0) / 100.0
                else -> 0.0
            }
            val dayHigh = row.optDouble("dayHigh", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            val yearHigh = row.optDouble("yearHigh", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            val yearLow = row.optDouble("yearLow", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            quotes[sym] = LiveQuoteOverlay(
                lastPrice = lastPrice,
                dailyChangePct = chgPct,
                barDate = sessionDate,
                dayHigh = dayHigh,
                yearHigh = yearHigh,
                yearLow = yearLow
            )
        }
        if (quotes.isEmpty()) return null
        return LiveOverlayOutcome(
            sourceLabel = "NSE Official Live",
            sessionDate = sessionDate,
            quotesBySymbol = quotes
        )
    }

    private fun parseNseDateTimeToIsoDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        for (fmt in NSE_TIMESTAMP_FORMATS) {
            try {
                val ld = LocalDate.parse(cleaned, fmt)
                return ld.toString()
            } catch (_: Exception) {
            }
        }
        val firstToken = cleaned.substringBefore(' ')
        for (fmt in NSE_TIMESTAMP_FORMATS) {
            try {
                return LocalDate.parse(firstToken, fmt).toString()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun fetchNseIndicesMacro(): Pair<Pair<String, Double>?, Pair<String, Map<TankIndexUniverse, Double>>?>? {
        val body = executeNseJsonGet(NSE_ALL_INDICES_URL) ?: return null
        val root = JSONObject(body)
        val dataArr = root.optJSONArray("data") ?: return null
        val sessionDt = parseNseDateTimeToIsoDate(root.optString("timestamp", ""))
            ?.takeIf { NseMarketCalendar.isValidTradingIsoDate(it) }
            ?: NseMarketCalendar.latestMarketSessionDate(ZonedDateTime.now(IST_ZONE)).toString()
        var vixPair: Pair<String, Double>? = null
        val adMap = LinkedHashMap<TankIndexUniverse, Double>()
        val indexToUniverse = mapOf(
            "NIFTY 500" to TankIndexUniverse.NIFTY_500,
            "NIFTY 50" to TankIndexUniverse.NIFTY_50,
            "NIFTY NEXT 50" to TankIndexUniverse.NEXT_50,
            "NIFTY MIDCAP 150" to TankIndexUniverse.MIDCAP_150,
            "NIFTY SMALLCAP 250" to TankIndexUniverse.SMALLCAP_250
        )
        for (i in 0 until dataArr.length()) {
            val row = dataArr.optJSONObject(i) ?: continue
            val idxName = row.optString("index", "").uppercase(Locale.US)
            if (idxName == "INDIA VIX") {
                val last = row.optDouble("last", Double.NaN)
                if (!last.isNaN() && last > 0.0) {
                    vixPair = sessionDt to (round(last * 100.0) / 100.0)
                }
            } else {
                val u = indexToUniverse[idxName] ?: continue
                val adv = row.optDouble("advances", Double.NaN)
                val dec = row.optDouble("declines", Double.NaN)
                if (!adv.isNaN() && !dec.isNaN() && (adv + dec) > 0.0) {
                    adMap[u] = round((adv / (adv + dec)) * 10000.0) / 100.0
                }
            }
        }
        return vixPair to (if (adMap.isNotEmpty()) sessionDt to adMap else null)
    }

    private fun parseTickertapeIsoToDate(raw: String): String? {
        if (raw.length < 10) return null
        return try {
            if (raw.endsWith("T00:00:00.000Z") || raw.endsWith("T00:00:00Z")) {
                raw.substring(0, 10)
            } else {
                Instant.parse(raw).atZone(IST_ZONE).toLocalDate().toString()
            }
        } catch (_: Exception) {
            val sub = raw.take(10)
            runCatching { LocalDate.parse(sub).toString() }.getOrNull()
        }
    }

    private fun executeScannerQuery(
        tickers: List<String>,
        symbolSet: Set<String>
    ): ScannerQueryOutcome {
        val tickersArray = JSONArray().apply {
            for (t in tickers) put(t)
        }
        val payload = JSONObject().apply {
            put(
                "symbols",
                JSONObject().apply {
                    put("tickers", tickersArray)
                    put("query", JSONObject().apply { put("types", JSONArray()) })
                }
            )
            put(
                "columns",
                JSONArray().apply {
                    put("name")                    // 0
                    put("description")             // 1
                    put("close")                   // 2
                    put("change")                  // 3
                    put("price_52_week_high")      // 4
                    put("price_52_week_low")       // 5
                    put("sector")                  // 6
                    put("time")                    // 7
                    put("high")                    // 8
                    put("price_52_week_high[1]")   // 9
                    put("Perf.Y")                  // 10
                    put("Perf.6M")                 // 11
                    put("time[1]")                 // 12
                    put("close[1]")                // 13
                    put("change[1]")               // 14
                    put("high[1]")                 // 15
                    put("price_52_week_high[1]")   // 16
                    put("price_52_week_low[1]")    // 17
                    put("time[2]")                 // 18
                    put("close[2]")                // 19
                    put("high[2]")                 // 20
                    put("price_52_week_high[2]")   // 21
                    put("price_52_week_low[2]")    // 22
                    put("price_earnings_ttm")      // 23
                    put("earnings_per_share_diluted_ttm") // 24
                }
            )
        }

        val request = Request.Builder()
            .url(TRADINGVIEW_SCANNER_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) Nifty500DepthTank")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Market feed HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseTradingViewPayloadFull(body, symbolSet)
        }
    }

    override suspend fun fetchRemoteHistoryOverride(endpointUrl: String?): Result<List<TankHistoryPoint>> {
        return fetchRemoteHistoryWithMetadata(endpointUrl).map { it.points }
    }

    override suspend fun fetchRemoteHistoryWithMetadata(endpointUrl: String?): Result<RemoteHistoryPayload> =
        withContext(Dispatchers.IO) {
            if (!endpointUrl.isNullOrBlank()) {
                try {
                    val req = Request.Builder().url(endpointUrl).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = resp.body?.string().orEmpty()
                            val payload = parseStructuredHistoryJson(json)
                            if (payload.points.isNotEmpty() && payload.integrity.isSchemaValid()) {
                                return@withContext Result.success(payload)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Remote history REST fetch failed: ${e.message}")
                }
            }

            // Optional Firestore collection hydration when Firebase is provisioned in the project
            if (isFirebaseConfigured()) {
                try {
                    val db = getFirestoreInstance()
                    if (db != null) {
                        val snapshot = db.collection("nifty500_tank_history")
                            .orderBy("date")
                            .get()
                            .await()
                        val list = snapshot.documents.mapNotNull { doc ->
                            val date = doc.getString("date") ?: return@mapNotNull null
                            val tankLevel = doc.getDouble("tankLevel") ?: return@mapNotNull null
                            val activeCount = (doc.getLong("activeCount") ?: 500L).toInt()
                            val highPress = (doc.getLong("highPressureCount") ?: 0L).toInt()
                            val rawBins = (doc.get("bins") as? List<*>)
                                ?.mapNotNull { (it as? Number)?.toInt() }
                                ?.takeIf { it.size == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT }
                                ?: emptyList()
                            TankHistoryPoint(
                                date = date,
                                tankLevel = tankLevel,
                                dailyDelta = doc.getDouble("dailyDelta") ?: 0.0,
                                flowingCount = (doc.getLong("flowingCount") ?: 0L).toInt(),
                                activeCount = activeCount,
                                highPressureCount = highPress,
                                avgScore = doc.getDouble("avgScore") ?: 50.0,
                                euphoriaPressurePct = doc.getDouble("euphoriaPressurePct")
                                    ?: if (activeCount > 0) round((highPress.toDouble() / activeCount) * 10000.0) / 100.0 else 0.0,
                                breakoutCount = (doc.getLong("breakoutCount") ?: 0L).toInt(),
                                breakoutPulsePct = doc.getDouble("breakoutPulsePct") ?: 0.0,
                                breakoutPulseDelta20 = doc.getDouble("breakoutPulseDelta20") ?: 0.0,
                                capitulationCount = (doc.getLong("capitulationCount") ?: 0L).toInt(),
                                capitulationDepthPct = doc.getDouble("capitulationDepthPct") ?: 0.0,
                                plateauDays = (doc.getLong("plateauDays") ?: 0L).toInt(),
                                scoreBins = rawBins
                            )
                        }
                        if (list.isNotEmpty()) {
                            val latest = list.last()
                            val integrity = DatasetIntegrityMetadata(
                                dataQuality = DataQuality.VERIFIED_EOD,
                                constituentCoverage = (latest.activeCount.toDouble() / DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS).coerceIn(0.0, 1.0),
                                isIllustrative = false,
                                lastVerifiedTradingDate = latest.date
                            )
                            return@withContext Result.success(RemoteHistoryPayload(list, integrity))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore sync unavailable: ${e.message}")
                }
            }

            Result.failure(IllegalStateException("Using bundled historical vault"))
        }

    internal fun parseTradingViewPayload(
        jsonText: String,
        nifty500Symbols: Set<String>
    ): List<StockTank> = parseTradingViewPayloadWithSession(jsonText, nifty500Symbols).first

    internal fun parseTradingViewPayloadWithSession(
        jsonText: String,
        nifty500Symbols: Set<String>
    ): Pair<List<StockTank>, String?> {
        val full = parseTradingViewPayloadFull(jsonText, nifty500Symbols)
        return full.stocks to full.sessionDate
    }

    internal fun parseTradingViewMultiDayForTest(
        jsonText: String,
        nifty500Symbols: Set<String>
    ): Triple<List<StockTank>, String?, Map<String, List<StockTank>>> {
        val full = parseTradingViewPayloadFull(jsonText, nifty500Symbols)
        return Triple(full.stocks, full.sessionDate, full.historicalStocksByDate)
    }

    private fun parseTradingViewPayloadFull(
        jsonText: String,
        nifty500Symbols: Set<String>
    ): ScannerQueryOutcome {
        val root = JSONObject(jsonText)
        val dataArr = root.optJSONArray("data") ?: return ScannerQueryOutcome(emptyList(), null)
        val sanitizedToCanonical = HashMap<String, String>(nifty500Symbols.size * 2)
        for (sym in nifty500Symbols) {
            val upper = sym.trim().uppercase(Locale.US)
            sanitizedToCanonical[upper] = upper
            sanitizedToCanonical[toTradingViewTickerSymbol(upper)] = upper
        }
        val results = ArrayList<StockTank>(500)
        val seen = HashSet<String>(512)
        val dateCounts = HashMap<String, Int>()
        val hist1List = ArrayList<Pair<String, StockTank>>(500)
        val hist1DateCounts = HashMap<String, Int>()
        val hist2List = ArrayList<Pair<String, StockTank>>(500)
        val hist2DateCounts = HashMap<String, Int>()

        for (i in 0 until dataArr.length()) {
            val item = dataArr.optJSONObject(i) ?: continue
            val d = item.optJSONArray("d") ?: continue
            if (d.length() < 7) continue

            val rawSym = d.optString(0, "").trim().uppercase(Locale.US)
            if (rawSym.isEmpty()) continue
            val matchedSym = sanitizedToCanonical[rawSym]
                ?: sanitizedToCanonical[rawSym.replace("_", "-")]
                ?: continue
            if (!seen.add(matchedSym)) continue

            val name = d.optString(1, matchedSym).ifBlank { matchedSym }
            val price = d.optDouble(2, Double.NaN)
            val chg = d.optDouble(3, 0.0)
            val high52 = d.optDouble(4, Double.NaN)
            val low52 = d.optDouble(5, Double.NaN)
            val rawSector = d.optString(6, "")
            val sector = normalizeSector(matchedSym, rawSector)
            if (d.length() >= 8) {
                val barEpochSec = d.optLong(7, 0L)
                if (barEpochSec > 1_600_000_000L) {
                    val sessionIso = Instant.ofEpochSecond(barEpochSec).atZone(IST_ZONE).toLocalDate().toString()
                    dateCounts[sessionIso] = (dateCounts[sessionIso] ?: 0) + 1
                }
            }

            val todayHighRaw = if (d.length() >= 9) d.optDouble(8, price) else price
            val todayHigh = if (todayHighRaw.isNaN() || todayHighRaw <= 0.0) price else todayHighRaw

            val prior52High: Double? = if (d.length() >= 10 && !d.isNull(9)) {
                val rawPrior = d.optDouble(9, Double.NaN)
                if (!rawPrior.isNaN() && rawPrior > 0.0) rawPrior else null
            } else {
                null
            }

            val rollingDays = when {
                d.length() <= 10 -> TankCalculator.FULL_52W_TRADING_DAYS // Legacy 7-8 column test payload
                !d.isNull(10) && !d.optDouble(10, Double.NaN).isNaN() -> TankCalculator.FULL_52W_TRADING_DAYS
                d.length() >= 12 && !d.isNull(11) && !d.optDouble(11, Double.NaN).isNaN() -> 130
                else -> 60
            }

            if (price.isNaN() || high52.isNaN() || low52.isNaN() || high52 <= low52 || price <= 0.0) {
                continue
            }

            val rawPeTtm: Double? = if (d.length() >= 24 && !d.isNull(23)) {
                d.optDouble(23, Double.NaN).takeIf { it.isFinite() && it > 0.0 && it < 5000.0 }
            } else {
                null
            }
            val rawEpsTtm: Double? = if (d.length() >= 25 && !d.isNull(24)) {
                d.optDouble(24, Double.NaN).takeIf { it.isFinite() && abs(it) > 1e-6 }
            } else {
                null
            }
            val resolvedEpsTtm: Double? = rawEpsTtm ?: if (rawPeTtm != null && rawPeTtm > 0.0) {
                (price / rawPeTtm).takeIf { it.isFinite() && it > 0.0 }
            } else {
                null
            }
            val resolvedPeTtm: Double? = rawPeTtm ?: if (resolvedEpsTtm != null && resolvedEpsTtm > 0.0) {
                (price / resolvedEpsTtm).takeIf { it.isFinite() && it > 0.0 && it < 5000.0 }
            } else {
                null
            }

            results.add(
                StockTank(
                    symbol = matchedSym,
                    name = name,
                    sector = sector,
                    currentPrice = price,
                    low52W = low52,
                    high52W = high52,
                    dailyChangePct = if (chg.isNaN()) 0.0 else chg,
                    todayHigh = todayHigh,
                    prior52WHigh = prior52High,
                    rollingHistoryDays = rollingDays,
                    peTtm = resolvedPeTtm,
                    epsTtm = resolvedEpsTtm
                )
            )

            // Parse [1] (prior trading session)
            if (d.length() >= 18) {
                val epoch1 = d.optLong(12, 0L)
                val close1 = d.optDouble(13, Double.NaN)
                val chg1 = d.optDouble(14, Double.NaN)
                val high1 = d.optDouble(15, close1)
                val h52_1 = d.optDouble(16, high52)
                val l52_1 = d.optDouble(17, low52)
                val prior52_2: Double? = if (d.length() >= 22 && !d.isNull(21)) {
                    d.optDouble(21, Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
                } else null
                if (epoch1 > 1_600_000_000L && !close1.isNaN() && close1 > 0.0 &&
                    !h52_1.isNaN() && !l52_1.isNaN() && h52_1 > l52_1 && l52_1 > 0.0
                ) {
                    val pe1 = resolvedEpsTtm
                        ?.takeIf { it > 0.0 }
                        ?.let { eps -> (close1 / eps).takeIf { it.isFinite() && it > 0.0 && it < 5000.0 } }
                    val iso1 = Instant.ofEpochSecond(epoch1).atZone(IST_ZONE).toLocalDate().toString()
                    hist1DateCounts[iso1] = (hist1DateCounts[iso1] ?: 0) + 1
                    hist1List.add(
                        iso1 to StockTank(
                            symbol = matchedSym,
                            name = name,
                            sector = sector,
                            currentPrice = close1,
                            low52W = l52_1,
                            high52W = h52_1,
                            dailyChangePct = if (chg1.isNaN()) 0.0 else chg1,
                            todayHigh = if (high1.isNaN() || high1 <= 0.0) close1 else high1,
                            prior52WHigh = prior52_2,
                            rollingHistoryDays = rollingDays,
                            peTtm = pe1,
                            epsTtm = resolvedEpsTtm
                        )
                    )
                }
            }

            // Parse [2] (2 trading sessions ago)
            if (d.length() >= 23) {
                val epoch2 = d.optLong(18, 0L)
                val close2 = d.optDouble(19, Double.NaN)
                val high2 = d.optDouble(20, close2)
                val h52_2 = d.optDouble(21, high52)
                val l52_2 = d.optDouble(22, low52)
                if (epoch2 > 1_600_000_000L && !close2.isNaN() && close2 > 0.0 &&
                    !h52_2.isNaN() && !l52_2.isNaN() && h52_2 > l52_2 && l52_2 > 0.0
                ) {
                    val pe2 = resolvedEpsTtm
                        ?.takeIf { it > 0.0 }
                        ?.let { eps -> (close2 / eps).takeIf { it.isFinite() && it > 0.0 && it < 5000.0 } }
                    val iso2 = Instant.ofEpochSecond(epoch2).atZone(IST_ZONE).toLocalDate().toString()
                    hist2DateCounts[iso2] = (hist2DateCounts[iso2] ?: 0) + 1
                    hist2List.add(
                        iso2 to StockTank(
                            symbol = matchedSym,
                            name = name,
                            sector = sector,
                            currentPrice = close2,
                            low52W = l52_2,
                            high52W = h52_2,
                            dailyChangePct = 0.0,
                            todayHigh = if (high2.isNaN() || high2 <= 0.0) close2 else high2,
                            prior52WHigh = null,
                            rollingHistoryDays = rollingDays,
                            peTtm = pe2,
                            epsTtm = resolvedEpsTtm
                        )
                    )
                }
            }
        }

        val dominantSessionDate = dateCounts.maxByOrNull { it.value }?.key
        val histMap = LinkedHashMap<String, List<StockTank>>()
        val iso1Dom = hist1DateCounts.maxByOrNull { it.value }?.key
        if (iso1Dom != null && iso1Dom != dominantSessionDate) {
            histMap[iso1Dom] = hist1List.filter { it.first == iso1Dom }.map { it.second }
        }
        val iso2Dom = hist2DateCounts.maxByOrNull { it.value }?.key
        if (iso2Dom != null && iso2Dom != dominantSessionDate && iso2Dom != iso1Dom) {
            histMap[iso2Dom] = hist2List.filter { it.first == iso2Dom }.map { it.second }
        }

        return ScannerQueryOutcome(
            stocks = results,
            sessionDate = dominantSessionDate,
            historicalStocksByDate = histMap
        )
    }

    private fun loadNifty500Symbols(): List<String> {
        return try {
            val raw = context.assets.open("index_constituents.json").bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            val arr = json.optJSONArray("nifty500") ?: return emptyList()
            List(arr.length()) { idx -> arr.optString(idx).trim().uppercase(Locale.US) }
                .filter { it.isNotEmpty() && !it.startsWith("DUMMY") && it != "HEGRAPHITE" }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading index_constituents.json", e)
            emptyList()
        }
    }

    private fun loadReferenceStocksFallback(): List<StockTank> {
        return try {
            val raw = context.assets.open("nifty500_stocks_seed.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(raw)
            val list = ArrayList<StockTank>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val symbol = obj.optString("symbol", "").trim().uppercase(Locale.US)
                if (symbol.isEmpty()) continue
                val price = obj.optDouble("price", 0.0)
                val low52W = obj.optDouble("low52W", 0.0)
                val high52W = obj.optDouble("high52W", 0.0)
                if (price <= 0.0 || low52W <= 0.0 || high52W <= low52W) continue
                list.add(
                    StockTank(
                        symbol = symbol,
                        name = obj.optString("name", symbol),
                        sector = obj.optString("sector", "Consumer & Tech"),
                        currentPrice = price,
                        low52W = low52W,
                        high52W = high52W,
                        dailyChangePct = obj.optDouble("chg", 0.0),
                        todayHigh = obj.optDouble("todayHigh", price),
                        rollingHistoryDays = obj.optInt("rollingHistoryDays", TankCalculator.FULL_52W_TRADING_DAYS)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Reference stocks fallback unavailable: ${e.message}")
            emptyList()
        }
    }

    private fun normalizeSector(symbol: String, rawSector: String): String {
        val s = rawSector.lowercase(Locale.US)
        return when {
            s.contains("finance") || s.contains("bank") -> "Banking"
            s.contains("technology") && (symbol in setOf("TCS", "INFY", "WIPRO", "HCLTECH", "TECHM", "LTM", "PERSISTENT", "COFORGE", "MPHASIS", "LTTS", "KPITTECH")) -> "IT"
            s.contains("electronic technology") || s.contains("technology services") -> "IT"
            s.contains("motor") || s.contains("auto") || symbol in setOf("M&M", "MARUTI", "TATAMOTORS", "BAJAJ-AUTO", "EICHERMOT", "TVSMOTOR", "HEROMOTOCO") -> "Auto"
            s.contains("health") || s.contains("pharma") || s.contains("biotech") -> "Pharma"
            s.contains("energy") || s.contains("utilities") || s.contains("oil") -> "Energy"
            s.contains("consumer non-durables") || s.contains("food") || s.contains("beverage") || s.contains("tobacco") -> "FMCG"
            s.contains("non-energy minerals") || s.contains("steel") || s.contains("metal") || s.contains("cement") -> "Metals"
            s.contains("producer") || s.contains("industrial") || s.contains("aerospace") || s.contains("real estate") || s.contains("construction") -> "Infra & Defence"
            else -> "Consumer & Tech"
        }
    }

    private fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun getFirestoreInstance(): FirebaseFirestore? {
        return try {
            val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
            if (resId != 0) {
                val dbId = context.getString(resId)
                if (dbId.isNotBlank()) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance()
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseStructuredHistoryJson(jsonText: String): RemoteHistoryPayload {
        val trimmed = jsonText.trim()
        if (trimmed.startsWith("[")) {
            val points = parseHistoryJsonArray(trimmed)
            val latest = points.lastOrNull()
            return RemoteHistoryPayload(
                points = points,
                integrity = DatasetIntegrityMetadata(
                    dataQuality = DataQuality.ILLUSTRATIVE_FALLBACK,
                    constituentCoverage = ((latest?.activeCount ?: 0).toDouble() / DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS).coerceIn(0.0, 1.0),
                    isIllustrative = true,
                    lastVerifiedTradingDate = latest?.date ?: "2026-11-13"
                )
            )
        }

        val root = JSONObject(trimmed)
        val arr = root.optJSONArray("history") ?: root.optJSONArray("series") ?: JSONArray()
        val points = parseHistoryArray(arr)
        val latest = points.lastOrNull()
        val dataQuality = DataQuality.fromWire(root.optString("dataQuality", ""))
        val isIllustrative = if (root.has("isIllustrative")) {
            root.optBoolean("isIllustrative", true)
        } else {
            dataQuality != DataQuality.VERIFIED_EOD
        }
        val coverage = root.optDouble(
            "constituentCoverage",
            ((latest?.activeCount ?: 0).toDouble() / DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS)
        ).coerceIn(0.0, 1.0)
        val lastVerified = root.optString("lastVerifiedTradingDate", latest?.date ?: "2026-11-13")
        val modelVer = root.optString("modelVersion", DatasetIntegrityMetadata.MODEL_VERSION)
        val calcVer = root.optString("calculationVersion", DatasetIntegrityMetadata.CALCULATION_VERSION)

        return RemoteHistoryPayload(
            points = points,
            integrity = DatasetIntegrityMetadata(
                dataQuality = dataQuality,
                constituentCoverage = coverage,
                isIllustrative = isIllustrative,
                lastVerifiedTradingDate = lastVerified,
                modelVersion = modelVer,
                calculationVersion = calcVer
            )
        )
    }

    internal fun parseHistoryJsonArray(jsonText: String): List<TankHistoryPoint> {
        val trimmed = jsonText.trim()
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val arr = root.optJSONArray("history") ?: root.optJSONArray("series") ?: JSONArray()
            return parseHistoryArray(arr)
        }
        return parseHistoryArray(JSONArray(trimmed))
    }

    internal fun parseHistoryArray(
        arr: JSONArray,
        validateCalendar: Boolean = true
    ): List<TankHistoryPoint> {
        val out = ArrayList<TankHistoryPoint>(arr.length())
        var runningPlateau = 0
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val date = obj.optString("date", "")
            if (date.length != 10) continue
            if (validateCalendar && !NseMarketCalendar.isValidTradingIsoDate(date)) continue
            val tankLevel = obj.optDouble("tankLevel", 0.0)
            val activeCount = obj.optInt("activeCount", 500)
            val highPress = obj.optInt("highPressureCount", 0)
            runningPlateau = if (obj.has("plateauDays")) {
                obj.optInt("plateauDays", 0)
            } else {
                if (tankLevel > TankCalculator.OVERHEATED_TANK_THRESHOLD_PCT) runningPlateau + 1 else 0
            }
            val eupPct = if (obj.has("euphoriaPressurePct")) {
                obj.optDouble("euphoriaPressurePct", 0.0)
            } else {
                if (activeCount > 0) round((highPress.toDouble() / activeCount) * 10000.0) / 100.0 else 0.0
            }

            val binsArr = obj.optJSONArray("bins")
            val parsedBins = if (binsArr != null && binsArr.length() == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) {
                List(TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) { bIdx -> binsArr.optInt(bIdx, 0).coerceAtLeast(0) }
            } else {
                emptyList()
            }
            val provenance = obj.optString("provenance", "").ifBlank { null }
            val peTankLevel = if (obj.has("peTankLevel") && !obj.isNull("peTankLevel")) {
                obj.optDouble("peTankLevel", Double.NaN).takeIf { it.isFinite() && it in 0.0..100.0 }
            } else null
            val medianPe = if (obj.has("medianPe") && !obj.isNull("medianPe")) {
                obj.optDouble("medianPe", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            } else null
            val peActiveCount = obj.optInt("peActiveCount", 0).coerceAtLeast(0)
            val peBinsArr = obj.optJSONArray("peBins")
            val parsedPeBins = if (peBinsArr != null && peBinsArr.length() == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) {
                List(TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) { bIdx -> peBinsArr.optInt(bIdx, 0).coerceAtLeast(0) }
            } else {
                emptyList()
            }

            out.add(
                TankHistoryPoint(
                    date = date,
                    tankLevel = tankLevel,
                    dailyDelta = obj.optDouble("dailyDelta", 0.0),
                    flowingCount = obj.optInt("flowingCount", 0),
                    activeCount = activeCount,
                    highPressureCount = highPress,
                    avgScore = obj.optDouble("avgScore", 50.0),
                    euphoriaPressurePct = eupPct,
                    breakoutCount = obj.optInt("breakoutCount", 0),
                    breakoutPulsePct = obj.optDouble("breakoutPulsePct", 0.0),
                    breakoutPulseDelta20 = obj.optDouble("breakoutPulseDelta20", 0.0),
                    capitulationCount = obj.optInt("capitulationCount", 0),
                    capitulationDepthPct = obj.optDouble("capitulationDepthPct", 0.0),
                    plateauDays = runningPlateau,
                    scoreBins = parsedBins,
                    provenance = provenance,
                    peTankLevel = peTankLevel,
                    medianPe = medianPe,
                    peActiveCount = peActiveCount,
                    peScoreBins = parsedPeBins
                )
            )
        }
        return out
    }
}
