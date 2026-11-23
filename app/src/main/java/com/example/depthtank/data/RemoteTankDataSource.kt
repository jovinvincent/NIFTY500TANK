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
import java.util.Locale
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
        private const val YAHOO_SPARK_BASE_URL = "https://query1.finance.yahoo.com/v8/finance/spark"
        private const val NSE_ALL_INDICES_URL = "https://www.nseindia.com/api/allIndices"
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

    private data class ScannerQueryOutcome(
        val stocks: List<StockTank>,
        val sessionDate: String?,
        val historicalStocksByDate: Map<String, List<StockTank>> = emptyMap()
    )

    override suspend fun fetchLiveNifty500Stocks(): Result<RemoteStocksResult> = withContext(Dispatchers.IO) {
        try {
            val nifty500Universe = loadNifty500Symbols()
            if (nifty500Universe.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Nifty 500 constituent list unavailable."))
            }
            val symbolSet = nifty500Universe.toHashSet()
            val nseTickers = nifty500Universe.map { "NSE:${it.replace("-", "_")}" }
            val nseOutcome = executeScannerQuery(nseTickers, symbolSet)

            val combined = LinkedHashMap<String, StockTank>(512)
            for (stock in nseOutcome.stocks) {
                combined[stock.symbol] = stock
            }
            val historicalMerged = LinkedHashMap<String, LinkedHashMap<String, StockTank>>()
            for ((dt, list) in nseOutcome.historicalStocksByDate) {
                val map = historicalMerged.getOrPut(dt) { LinkedHashMap(512) }
                for (st in list) map[st.symbol] = st
            }

            // Fallback to BSE exchange for Nifty 500 REITs / constituents not indexed under NSE: (e.g. BAGMANE, BIRET, EMBASSY)
            val missingSymbols = nifty500Universe.filter { it !in combined }
            val bseSymbolsFound = LinkedHashSet<String>()
            var resolvedSessionDate = nseOutcome.sessionDate
            if (missingSymbols.isNotEmpty()) {
                val bseTickers = missingSymbols.map { "BSE:${it.replace("-", "_")}" }
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
                    if (resolvedSessionDate == null) {
                        resolvedSessionDate = bseOutcome.sessionDate
                    }
                }
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
                lastVerifiedTradingDate = resolvedSessionDate ?: NseMarketCalendar.latestMarketSessionDate(
                    java.time.ZonedDateTime.now(NseMarketCalendar.IST_ZONE)
                ).toString(),
                excludedInsufficientHistoryCount = excludedCount
            )
            val eligibleHistorical = historicalMerged.mapValues { (_, map) ->
                TankCalculator.filterEligibleConstituents(map.values.toList()).sortedByDescending { it.score }
            }.filterValues { it.size >= 450 }

            Result.success(
                RemoteStocksResult(
                    stocks = eligible.sortedByDescending { it.score },
                    sourceName = "NSE/BSE Live Constituent Feed (${eligible.size}/500)",
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

        val perSymbolSeries = LinkedHashMap<String, List<Pair<String, Double>>>(symbols.size)
        val chunkResults = coroutineScope {
            chunks.map { chunk ->
                async {
                    semaphore.withPermit {
                        runCatching { fetchYahooSparkBatch(chunk, bseSymbols) }.getOrDefault(emptyMap())
                    }
                }
            }.awaitAll()
        }
        for (batch in chunkResults) {
            perSymbolSeries.putAll(batch)
        }
        if (perSymbolSeries.isEmpty()) return@withContext emptyMap()

        val result = LinkedHashMap<String, List<StockTank>>(missingDates.size)
        for (targetDate in missingDates.sorted()) {
            val dayStocks = ArrayList<StockTank>(refBySymbol.size)
            for ((sym, ref) in refBySymbol) {
                val series = perSymbolSeries[sym] ?: continue
                val idx = series.indexOfLast { it.first == targetDate }
                if (idx < 0) continue
                val closeVal = series[idx].second
                if (closeVal <= 0.0 || closeVal.isNaN()) continue
                val prevClose = if (idx > 0) series[idx - 1].second else closeVal
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
                result[targetDate] = dayStocks.sortedByDescending { it.score }
            }
        }
        result
    }

    private fun fetchYahooSparkBatch(
        symbols: List<String>,
        bseSymbols: Set<String>
    ): Map<String, List<Pair<String, Double>>> {
        val yahooToAppSym = LinkedHashMap<String, String>(symbols.size)
        val encodedTickers = symbols.map { sym ->
            val suffix = if (sym in bseSymbols) ".BO" else ".NS"
            val yf = "$sym$suffix"
            yahooToAppSym[yf] = sym
            URLEncoder.encode(yf, "UTF-8")
        }
        val url = "$YAHOO_SPARK_BASE_URL?symbols=${encodedTickers.joinToString(",")}&range=1mo&interval=1d"
        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyMap()
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return emptyMap()
            val root = JSONObject(body)
            val out = LinkedHashMap<String, List<Pair<String, Double>>>(symbols.size)
            val keys = root.keys()
            while (keys.hasNext()) {
                val yfKey = keys.next()
                val appSym = yahooToAppSym[yfKey] ?: yfKey.removeSuffix(".NS").removeSuffix(".BO")
                val node = root.optJSONObject(yfKey) ?: continue
                val tsArr = node.optJSONArray("timestamp") ?: continue
                val closeArr = node.optJSONArray("close") ?: continue
                val len = min(tsArr.length(), closeArr.length())
                val series = ArrayList<Pair<String, Double>>(len)
                for (i in 0 until len) {
                    val ts = tsArr.optLong(i, 0L)
                    val c = closeArr.optDouble(i, Double.NaN)
                    if (ts > 1_600_000_000L && !c.isNaN() && c > 0.0) {
                        val dt = Instant.ofEpochSecond(ts).atZone(IST_ZONE).toLocalDate().toString()
                        series.add(dt to c)
                    }
                }
                if (series.isNotEmpty()) {
                    out[appSym] = series
                }
            }
            return out
        }
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

    private fun fetchNseIndicesMacro(): Pair<Pair<String, Double>?, Pair<String, Map<TankIndexUniverse, Double>>?>? {
        val req = Request.Builder()
            .url(NSE_ALL_INDICES_URL)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val root = JSONObject(resp.body?.string().orEmpty())
            val dataArr = root.optJSONArray("data") ?: return null
            val sessionDt = NseMarketCalendar.latestMarketSessionDate(
                java.time.ZonedDateTime.now(IST_ZONE)
            ).toString()
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
            val normalizedSym = rawSym.replace("_", "-")
            val matchedSym = when {
                rawSym in nifty500Symbols -> rawSym
                normalizedSym in nifty500Symbols -> normalizedSym
                else -> continue
            }
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
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading index_constituents.json", e)
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
