package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Advance / Decline (A/D) Breadth Metrics for a specific index segment.
 */
data class IndexAdvanceDecline(
    val indexKey: String,
    val displayName: String,
    val advances: Int,
    val declines: Int,
    val unchanged: Int,
    val totalConstituents: Int,
    val ratio: Double,           // Advances / Declines
    val breadthPercent: Double   // Advances / (Advances + Declines) * 100%
) {
    val isBullish: Boolean get() = advances >= declines
    val sentimentLabel: String get() = when {
        breadthPercent >= 75.0 -> "Strong Bullish 🟢"
        breadthPercent >= 60.0 -> "Bullish Bias 🟢"
        breadthPercent >= 45.0 -> "Balanced / Mixed ⚪"
        breadthPercent >= 30.0 -> "Bearish Pressure 🔴"
        else -> "Severe Selling 🔴"
    }
}

/**
 * Complete Advance / Decline Record for all 8 requested market segments on a single trading day.
 */
data class DailyAdvanceDeclineRecord(
    val dateStr: String,
    val timestamp: Long,
    val bseSensex: IndexAdvanceDecline,
    val nifty50: IndexAdvanceDecline,
    val niftyNext50: IndexAdvanceDecline,
    val niftyMidcap150: IndexAdvanceDecline,
    val niftySmallcap250: IndexAdvanceDecline,
    val nifty500: IndexAdvanceDecline,
    val nifty1000: IndexAdvanceDecline,
    val allNseEquity: IndexAdvanceDecline
) {
    fun getAllSegments(): List<IndexAdvanceDecline> = listOf(
        bseSensex,
        nifty50,
        niftyNext50,
        niftyMidcap150,
        niftySmallcap250,
        nifty500,
        nifty1000,
        allNseEquity
    )

    fun getSegment(key: String): IndexAdvanceDecline = when (key) {
        "bseSensex" -> bseSensex
        "nifty50" -> nifty50
        "niftyNext50" -> niftyNext50
        "niftyMidcap150" -> niftyMidcap150
        "niftySmallcap250" -> niftySmallcap250
        "nifty500" -> nifty500
        "nifty1000" -> nifty1000
        "allNseEquity" -> allNseEquity
        else -> nifty50
    }
}

/**
 * Container for parsed index constituent lists loaded from assets.
 */
internal data class IndexConstituentUniverse(
    val bseSensex: List<String>,
    val nifty50: List<String>,
    val niftyNext50: List<String>,
    val niftyMidcap150: List<String>,
    val niftySmallcap250: List<String>,
    val nifty500: List<String>,
    val nifty1000: List<String>
)

/**
 * Internal holder for NSE allIndices parsed breadth counts + exchange trade date.
 */
internal data class NseAllIndicesBreadth(
    val tradeDateStr: String?,
    val nifty50: Triple<Int, Int, Int>? = null,
    val niftyNext50: Triple<Int, Int, Int>? = null,
    val niftyMidcap150: Triple<Int, Int, Int>? = null,
    val niftySmallcap250: Triple<Int, Int, Int>? = null,
    val nifty500: Triple<Int, Int, Int>? = null
)

/**
 * Advance / Decline Ratio Manager.
 * Loads authentic historical constituent breadth records from 01-04-2016 to latest date from bundled assets
 * and supports live background updates via Official NSE allIndices + TradingView India Scanner
 * using official constituent lists for all 8 benchmarks.
 */
object AdvanceDeclineManager {

    private const val TAG = "AdvanceDeclineManager"
    internal const val CACHE_VERSION = 3
    private const val LIVE_CACHE_FILE = "advance_decline_live_cache.json"
    private val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    private val isLoaded = AtomicBoolean(false)
    private val recordsTimeline = ConcurrentSkipListMap<Long, DailyAdvanceDeclineRecord>()
    private var lastFetchTime = 0L
    private val isFetching = AtomicBoolean(false)

    @Volatile
    private var constituentUniverse: IndexConstituentUniverse? = null

    val adUpdateState = MutableStateFlow(0)

    private fun normalizeToMidnight(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    internal fun parseIsoDate(key: String): Long? {
        if (key.length != 10 || key[4] != '-' || key[7] != '-') return null
        val y = (key[0] - '0') * 1000 + (key[1] - '0') * 100 + (key[2] - '0') * 10 + (key[3] - '0')
        val m = (key[5] - '0') * 10 + (key[6] - '0') - 1
        val d = (key[8] - '0') * 10 + (key[9] - '0')
        if (y < 2000 || m !in 0..11 || d !in 1..31) return null
        val cal = Calendar.getInstance()
        cal.set(y, m, d, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    internal fun isWeekendIso(dateStr: String): Boolean {
        if (dateStr.length != 10 || dateStr[4] != '-' || dateStr[7] != '-') return true
        val t = parseIsoDate(dateStr) ?: return true
        val cal = Calendar.getInstance()
        cal.timeInMillis = t
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    /**
     * Resolves the most recent valid Mon-Fri trading calendar date in Asia/Kolkata.
     * Never returns Saturday or Sunday.
     */
    internal fun mostRecentWeekdayIsoIst(nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(istTimeZone)
        cal.timeInMillis = nowMillis
        // Before 09:00 IST on a weekday, the live session for today hasn't opened yet -> use previous weekday
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dowInitial = cal.get(Calendar.DAY_OF_WEEK)
        if (dowInitial !in listOf(Calendar.SATURDAY, Calendar.SUNDAY) && hour < 9) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        ) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = istTimeZone }
        return sdf.format(cal.time)
    }

    /**
     * Parses NSE timestamp formats such as "13-Nov-2026 15:30:00" or "2026-11-13" into "yyyy-MM-dd",
     * snapping weekends to Friday if needed.
     */
    internal fun parseNseTradeDateIso(rawTimestamp: String?): String? {
        if (rawTimestamp.isNullOrBlank()) return null
        val trimmed = rawTimestamp.trim()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = istTimeZone }
        val candidates = listOf(
            "dd-MMM-yyyy HH:mm:ss",
            "dd-MMM-yyyy",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy",
            "yyyy-MM-dd"
        )
        for (pattern in candidates) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = istTimeZone
                    isLenient = false
                }
                val parsedDate = parser.parse(trimmed)
                if (parsedDate != null) {
                    val cal = Calendar.getInstance(istTimeZone).apply { time = parsedDate }
                    while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                        cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                    ) {
                        cal.add(Calendar.DAY_OF_MONTH, -1)
                    }
                    return isoFormat.format(cal.time)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    internal fun buildSegment(
        key: String,
        name: String,
        adv: Int,
        dec: Int,
        unc: Int
    ): IndexAdvanceDecline {
        val safeA = max(0, adv)
        val safeD = max(0, dec)
        val safeU = max(0, unc)
        val tot = safeA + safeD + safeU
        val ratio = safeA.toDouble() / max(1, safeD)
        val pct = (safeA.toDouble() / max(1, safeA + safeD)) * 100.0
        return IndexAdvanceDecline(
            indexKey = key,
            displayName = name,
            advances = safeA,
            declines = safeD,
            unchanged = safeU,
            totalConstituents = tot,
            ratio = ratio,
            breadthPercent = pct
        )
    }

    private fun parseSegment(obj: JSONObject, key: String, name: String, defaultTot: Int): IndexAdvanceDecline {
        val segObj = obj.optJSONObject(key)
        val a = max(0, segObj?.optInt("adv", defaultTot / 2) ?: (defaultTot / 2))
        val d = max(0, segObj?.optInt("dec", defaultTot - defaultTot / 2) ?: (defaultTot - defaultTot / 2))
        val u = max(0, segObj?.optInt("unc", 0) ?: 0)
        val actualSum = a + d + u
        val rawTot = segObj?.optInt("total", actualSum) ?: actualSum
        val tot = if (rawTot > 0) rawTot else actualSum
        val ratio = segObj?.optDouble("ratio", Double.NaN)?.takeIf { !it.isNaN() }
            ?: (a.toDouble() / max(1, d))
        val pct = segObj?.optDouble("pct", Double.NaN)?.takeIf { !it.isNaN() }
            ?: ((a.toDouble() / max(1, a + d)) * 100.0)
        return IndexAdvanceDecline(
            indexKey = key,
            displayName = name,
            advances = a,
            declines = d,
            unchanged = u,
            totalConstituents = tot,
            ratio = ratio,
            breadthPercent = pct
        )
    }

    internal fun isValidSegment(seg: IndexAdvanceDecline, minTotal: Int, maxTotal: Int): Boolean {
        if (seg.advances < 0 || seg.declines < 0 || seg.unchanged < 0) return false
        val sum = seg.advances + seg.declines + seg.unchanged
        if (sum !in minTotal..maxTotal) return false
        if (seg.totalConstituents != sum) return false
        return true
    }

    internal fun isValidRecord(record: DailyAdvanceDeclineRecord): Boolean {
        if (isWeekendIso(record.dateStr)) return false
        val isPre2020 = record.dateStr < "2020-01-01"
        return if (isPre2020) {
            // Historical 2016-2019 Bhavcopy breadth using current constituent universe excludes post-2019 IPOs
            isValidSegment(record.bseSensex, 25, 33) &&
                isValidSegment(record.nifty50, 40, 53) &&
                isValidSegment(record.niftyNext50, 30, 53) &&
                isValidSegment(record.niftyMidcap150, 85, 156) &&
                isValidSegment(record.niftySmallcap250, 105, 256) &&
                isValidSegment(record.nifty500, 270, 515) &&
                isValidSegment(record.nifty1000, 440, 1020) &&
                isValidSegment(record.allNseEquity, 1200, 5000)
        } else {
            isValidSegment(record.bseSensex, 27, 33) &&
                isValidSegment(record.nifty50, 47, 53) &&
                isValidSegment(record.niftyNext50, 47, 53) &&
                isValidSegment(record.niftyMidcap150, 140, 156) &&
                isValidSegment(record.niftySmallcap250, 235, 256) &&
                isValidSegment(record.nifty500, 485, 515) &&
                isValidSegment(record.nifty1000, 950, 1020) &&
                isValidSegment(record.allNseEquity, 1200, 5000)
        }
    }

    private fun parseRecordFromJsonObject(dateKey: String, time: Long, obj: JSONObject): DailyAdvanceDeclineRecord {
        return DailyAdvanceDeclineRecord(
            dateStr = dateKey,
            timestamp = normalizeToMidnight(time),
            bseSensex = parseSegment(obj, "bseSensex", "BSE Sensex", 30),
            nifty50 = parseSegment(obj, "nifty50", "Nifty 50", 50),
            niftyNext50 = parseSegment(obj, "niftyNext50", "Nifty Next 50", 50),
            niftyMidcap150 = parseSegment(obj, "niftyMidcap150", "Nifty Midcap 150", 150),
            niftySmallcap250 = parseSegment(obj, "niftySmallcap250", "Nifty Smallcap 250", 250),
            nifty500 = parseSegment(obj, "nifty500", "Nifty 500", 500),
            nifty1000 = parseSegment(obj, "nifty1000", "Nifty 1000", 1000),
            allNseEquity = parseSegment(obj, "allNseEquity", "All NSE Equity", 2927)
        )
    }

    private fun loadConstituentsIfNeeded(context: Context): IndexConstituentUniverse? {
        constituentUniverse?.let { return it }
        return try {
            context.assets.open("index_constituents.json").use { stream ->
                val obj = JSONObject(InputStreamReader(stream).readText())
                fun readList(key: String): List<String> {
                    val arr = obj.optJSONArray(key) ?: return emptyList()
                    val list = ArrayList<String>(arr.length())
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i, "").trim().uppercase(Locale.US)
                        if (s.isNotEmpty()) list.add(s)
                    }
                    return list
                }
                val parsed = IndexConstituentUniverse(
                    bseSensex = readList("bseSensex"),
                    nifty50 = readList("nifty50"),
                    niftyNext50 = readList("niftyNext50"),
                    niftyMidcap150 = readList("niftyMidcap150"),
                    niftySmallcap250 = readList("niftySmallcap250"),
                    nifty500 = readList("nifty500"),
                    nifty1000 = readList("nifty1000")
                )
                constituentUniverse = parsed
                parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load index_constituents.json: ${e.message}")
            null
        }
    }

    fun loadDataIfNeeded(context: Context) {
        if (isLoaded.get() && recordsTimeline.isNotEmpty()) return
        synchronized(this) {
            if (isLoaded.get() && recordsTimeline.isNotEmpty()) return
            try {
                // 1. Load authentic constituent historical base from assets (01-04-2016 onwards)
                context.assets.open("advance_decline_historical.json").use { inputStream ->
                    val text = InputStreamReader(inputStream).readText()
                    val root = JSONObject(text)
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val dateKey = keys.next()
                        if (isWeekendIso(dateKey)) continue
                        val time = parseIsoDate(dateKey) ?: continue
                        val obj = root.getJSONObject(dateKey)
                        val record = parseRecordFromJsonObject(dateKey, time, obj)
                        if (isValidRecord(record)) {
                            recordsTimeline[normalizeToMidnight(time)] = record
                        }
                    }
                }
                Log.d(TAG, "Loaded ${recordsTimeline.size} Advance/Decline records from assets (01-04-2016 to present)")

                // 2. Overlay live persisted disk cache ONLY if schema version >= CACHE_VERSION & passes strict validation
                try {
                    val cacheFile = File(context.filesDir, LIVE_CACHE_FILE)
                    if (cacheFile.exists()) {
                        val cachedJson = JSONObject(cacheFile.readText())
                        val version = cachedJson.optInt("version", 0)
                        val dStr = cachedJson.optString("date", "")
                        val t = parseIsoDate(dStr)
                        val latestAssetDate = recordsTimeline.lastEntry()?.value?.dateStr ?: ""
                        if (version >= CACHE_VERSION && t != null && !isWeekendIso(dStr) && dStr >= latestAssetDate) {
                            val cachedRecord = parseRecordFromJsonObject(dStr, t, cachedJson)
                            if (isValidRecord(cachedRecord)) {
                                recordsTimeline[normalizeToMidnight(t)] = cachedRecord
                                Log.d(
                                    TAG,
                                    "Loaded valid live cached A/D record for $dStr: Nifty50 = ${cachedRecord.nifty50.advances}/${cachedRecord.nifty50.declines}/${cachedRecord.nifty50.unchanged}"
                                )
                            } else {
                                cacheFile.delete()
                                Log.w(TAG, "Purged corrupted live A/D cache record for $dStr")
                            }
                        } else {
                            cacheFile.delete()
                            Log.w(TAG, "Purged stale/legacy A/D cache (version=$version, date=$dStr)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error loading live A/D cache: ${e.message}")
                    try { File(context.filesDir, LIVE_CACHE_FILE).delete() } catch (_: Exception) {}
                }

                isLoaded.set(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Advance/Decline history", e)
            }
        }
    }

    /**
     * Triggers asynchronous live fetching of the latest Advance / Decline market breadth
     * across all 8 market indices.
     */
    fun triggerLiveAdvanceDeclineFetch(context: Context?) {
        if (context == null) return
        loadDataIfNeeded(context)
        loadConstituentsIfNeeded(context)

        val now = System.currentTimeMillis()
        if (now - lastFetchTime < 30_000L && isFetching.get()) {
            return
        }
        isFetching.set(true)
        lastFetchTime = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                fetchLiveAdvanceDeclineFromOfficialFeeds(context)
            } catch (e: Exception) {
                Log.w(TAG, "Live A/D fetch error: ${e.message}")
            } finally {
                isFetching.set(false)
            }
        }
    }

    /**
     * Strictly parses official NSE `/api/allIndices` JSON payload.
     * Uses exact normalized index name matching (`==`) and strict constituent sum ranges so
     * "NIFTY 500" or thematic indices can never contaminate "NIFTY 50".
     */
    internal fun parseNseAllIndicesJson(jsonText: String): NseAllIndicesBreadth? {
        return try {
            val root = JSONObject(jsonText)
            val tradeDate = parseNseTradeDateIso(root.optString("timestamp", ""))
            val dataArr = root.optJSONArray("data") ?: return null

            var nifty50: Triple<Int, Int, Int>? = null
            var niftyNext50: Triple<Int, Int, Int>? = null
            var niftyMidcap150: Triple<Int, Int, Int>? = null
            var niftySmallcap250: Triple<Int, Int, Int>? = null
            var nifty500: Triple<Int, Int, Int>? = null

            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i) ?: continue
                val cleanIdx = item.optString("index", "")
                    .trim()
                    .uppercase(Locale.US)
                    .replace(Regex("\\s+"), " ")
                fun parseCount(field: String, defaultVal: Int): Int {
                    if (!item.has(field) || item.isNull(field)) return defaultVal
                    val s = item.optString(field, "").trim()
                    s.toIntOrNull()?.let { return it }
                    s.toDoubleOrNull()?.let { if (!it.isNaN()) return it.toInt() }
                    return item.optInt(field, defaultVal)
                }
                val adv = parseCount("advances", -1)
                val dec = parseCount("declines", -1)
                val unc = parseCount("unchanged", 0)
                if (adv < 0 || dec < 0 || unc < 0) continue

                val sum = adv + dec + unc
                when (cleanIdx) {
                    "NIFTY 50" -> if (sum in 47..53) nifty50 = Triple(adv, dec, unc)
                    "NIFTY NEXT 50" -> if (sum in 47..53) niftyNext50 = Triple(adv, dec, unc)
                    "NIFTY MIDCAP 150" -> if (sum in 140..156) niftyMidcap150 = Triple(adv, dec, unc)
                    "NIFTY SMALLCAP 250" -> if (sum in 235..256) niftySmallcap250 = Triple(adv, dec, unc)
                    "NIFTY 500" -> if (sum in 485..515) nifty500 = Triple(adv, dec, unc)
                }
            }

            if (nifty50 == null && niftyNext50 == null && niftyMidcap150 == null &&
                niftySmallcap250 == null && nifty500 == null
            ) {
                return null
            }
            NseAllIndicesBreadth(
                tradeDateStr = tradeDate,
                nifty50 = nifty50,
                niftyNext50 = niftyNext50,
                niftyMidcap150 = niftyMidcap150,
                niftySmallcap250 = niftySmallcap250,
                nifty500 = nifty500
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed parsing NSE allIndices: ${e.message}")
            null
        }
    }

    /**
     * Parses TradingView India Scanner JSON payload and computes authentic constituent-level A/D
     * for all 8 market benchmarks using the official constituent universe.
     */
    internal fun computeAllBenchmarksFromTradingViewJson(
        tvJsonText: String,
        universe: IndexConstituentUniverse
    ): Map<String, Triple<Int, Int, Int>>? {
        return try {
            val root = JSONObject(tvJsonText)
            val dataArr = root.optJSONArray("data") ?: return null
            if (dataArr.length() < 1200) return null

            val changesBySymbol = HashMap<String, Double>(dataArr.length() * 2)
            val orderedActive = ArrayList<Pair<String, Double>>(dataArr.length())

            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i) ?: continue
                val dArr = item.optJSONArray("d") ?: continue
                if (dArr.length() < 3) continue
                val name = dArr.optString(0, "").trim().uppercase(Locale.US)
                if (name.isEmpty() || dArr.isNull(2)) continue
                val chg = dArr.optDouble(2, Double.NaN)
                if (chg.isNaN()) continue
                changesBySymbol[name] = chg
                if (!name.endsWith("-RE") && !name.endsWith(".RR")) {
                    orderedActive.add(name to chg)
                }
            }

            if (orderedActive.size < 1200) return null

            fun countSymbols(symbols: List<String>, minMatchRatio: Double = 0.85): Triple<Int, Int, Int>? {
                if (symbols.isEmpty()) return null
                var adv = 0
                var dec = 0
                var unc = 0
                var found = 0
                for (sym in symbols) {
                    val tvKey = sym.replace("-", "_").replace("&", "_")
                    val chg = changesBySymbol[sym]
                        ?: changesBySymbol[tvKey]
                        ?: changesBySymbol["$sym.RR"]
                    if (chg != null) {
                        found++
                        when {
                            abs(chg) < 0.0005 -> unc++
                            chg > 0.0 -> adv++
                            else -> dec++
                        }
                    }
                }
                if (found < (symbols.size * minMatchRatio).toInt()) return null
                return Triple(adv, dec, unc)
            }

            val result = LinkedHashMap<String, Triple<Int, Int, Int>>()
            countSymbols(universe.bseSensex)?.let { result["bseSensex"] = it }
            countSymbols(universe.nifty50)?.let { result["nifty50"] = it }
            countSymbols(universe.niftyNext50)?.let { result["niftyNext50"] = it }
            countSymbols(universe.niftyMidcap150)?.let { result["niftyMidcap150"] = it }
            countSymbols(universe.niftySmallcap250)?.let { result["niftySmallcap250"] = it }
            countSymbols(universe.nifty500)?.let { result["nifty500"] = it }
            countSymbols(universe.nifty1000)?.let { result["nifty1000"] = it }

            // All NSE Equity from live active universe
            var allAdv = 0
            var allDec = 0
            var allUnc = 0
            for ((_, chg) in orderedActive) {
                when {
                    abs(chg) < 0.0005 -> allUnc++
                    chg > 0.0 -> allAdv++
                    else -> allDec++
                }
            }
            if (allAdv + allDec + allUnc >= 1200) {
                result["allNseEquity"] = Triple(allAdv, allDec, allUnc)
            }

            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            Log.d(TAG, "TradingView A/D parse error: ${e.message}")
            null
        }
    }

    private fun fetchNseAllIndices(): NseAllIndicesBreadth? {
        return try {
            val sessionConn = (URL("https://www.nseindia.com").openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }
            val cookies = sessionConn.headerFields["Set-Cookie"]
            val cookieHeader = cookies?.joinToString("; ") { it.substringBefore(";") } ?: ""
            sessionConn.disconnect()

            val apiConn = (URL("https://www.nseindia.com/api/allIndices").openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Referer", "https://www.nseindia.com/market-data/live-equity-market")
                if (cookieHeader.isNotEmpty()) setRequestProperty("Cookie", cookieHeader)
            }

            if (apiConn.responseCode == 200) {
                val jsonText = apiConn.inputStream.bufferedReader().use { it.readText() }
                parseNseAllIndicesJson(jsonText)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "NSE allIndices feed unavailable: ${e.message}")
            null
        }
    }

    private fun fetchTradingViewIndiaBenchmarks(universe: IndexConstituentUniverse): Map<String, Triple<Int, Int, Int>>? {
        return try {
            val payload = JSONObject().apply {
                put(
                    "filter",
                    JSONArray().apply {
                        put(JSONObject().put("left", "exchange").put("operation", "equal").put("right", "NSE"))
                        put(
                            JSONObject().put("left", "type").put("operation", "in_range")
                                .put("right", JSONArray().put("stock").put("dr"))
                        )
                    }
                )
                put("markets", JSONArray().put("india"))
                put("columns", JSONArray().put("name").put("close").put("change"))
                put("sort", JSONObject().put("sortBy", "market_cap_basic").put("sortOrder", "desc"))
                put("range", JSONArray().put(0).put(3500))
            }.toString()

            val conn = (URL("https://scanner.tradingview.com/india/scan").openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 9000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                computeAllBenchmarksFromTradingViewJson(text, universe)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "TradingView India scanner failed: ${e.message}")
            null
        }
    }

    private fun fetchLiveAdvanceDeclineFromOfficialFeeds(context: Context) {
        val universe = loadConstituentsIfNeeded(context)

        // Fetch both sources (NSE official allIndices when accessible on device + TradingView constituent scanner)
        val nseBreadth = fetchNseAllIndices()
        val tvBreadth = if (universe != null) fetchTradingViewIndiaBenchmarks(universe) else null

        if (nseBreadth == null && tvBreadth == null) {
            Log.d(TAG, "Both live A/D feeds unavailable; retaining verified historical record")
            return
        }

        val latestExisting = recordsTimeline.lastEntry()?.value
        fun existingTuple(seg: IndexAdvanceDecline?): Triple<Int, Int, Int>? =
            seg?.let { Triple(it.advances, it.declines, it.unchanged) }

        val sensexTuple = tvBreadth?.get("bseSensex") ?: existingTuple(latestExisting?.bseSensex) ?: return
        val nifty50Tuple = nseBreadth?.nifty50 ?: tvBreadth?.get("nifty50") ?: existingTuple(latestExisting?.nifty50) ?: return
        val next50Tuple = nseBreadth?.niftyNext50 ?: tvBreadth?.get("niftyNext50") ?: existingTuple(latestExisting?.niftyNext50) ?: return
        val midcapTuple = nseBreadth?.niftyMidcap150 ?: tvBreadth?.get("niftyMidcap150") ?: existingTuple(latestExisting?.niftyMidcap150) ?: return
        val smallcapTuple = nseBreadth?.niftySmallcap250 ?: tvBreadth?.get("niftySmallcap250") ?: existingTuple(latestExisting?.niftySmallcap250) ?: return
        val nifty500Tuple = nseBreadth?.nifty500 ?: tvBreadth?.get("nifty500") ?: existingTuple(latestExisting?.nifty500) ?: return
        val nifty1000Tuple = tvBreadth?.get("nifty1000") ?: existingTuple(latestExisting?.nifty1000) ?: return
        val allNseTuple = tvBreadth?.get("allNseEquity") ?: existingTuple(latestExisting?.allNseEquity) ?: return

        // Determine authentic trading date (never stamp a weekend date like Saturday/Sunday)
        val candidateDateStr = nseBreadth?.tradeDateStr ?: mostRecentWeekdayIsoIst()
        val isIdenticalToPreviousSession = latestExisting != null &&
            latestExisting.nifty50.advances == nifty50Tuple.first &&
            latestExisting.nifty50.declines == nifty50Tuple.second &&
            latestExisting.nifty50.unchanged == nifty50Tuple.third &&
            latestExisting.nifty500.advances == nifty500Tuple.first &&
            latestExisting.nifty500.declines == nifty500Tuple.second

        // If NSE didn't explicitly supply a newer session timestamp and breadth is identical to the last session,
        // keep that session's date rather than inventing a new point on a weekday market holiday.
        val tradeDateStr = if (nseBreadth?.tradeDateStr == null && isIdenticalToPreviousSession) {
            latestExisting.dateStr
        } else {
            candidateDateStr
        }

        if (isWeekendIso(tradeDateStr)) {
            Log.w(TAG, "Refusing to store weekend A/D record for $tradeDateStr")
            return
        }
        val tradeTime = parseIsoDate(tradeDateStr)?.let { normalizeToMidnight(it) } ?: return

        val updatedRecord = DailyAdvanceDeclineRecord(
            dateStr = tradeDateStr,
            timestamp = tradeTime,
            bseSensex = buildSegment("bseSensex", "BSE Sensex", sensexTuple.first, sensexTuple.second, sensexTuple.third),
            nifty50 = buildSegment("nifty50", "Nifty 50", nifty50Tuple.first, nifty50Tuple.second, nifty50Tuple.third),
            niftyNext50 = buildSegment("niftyNext50", "Nifty Next 50", next50Tuple.first, next50Tuple.second, next50Tuple.third),
            niftyMidcap150 = buildSegment("niftyMidcap150", "Nifty Midcap 150", midcapTuple.first, midcapTuple.second, midcapTuple.third),
            niftySmallcap250 = buildSegment("niftySmallcap250", "Nifty Smallcap 250", smallcapTuple.first, smallcapTuple.second, smallcapTuple.third),
            nifty500 = buildSegment("nifty500", "Nifty 500", nifty500Tuple.first, nifty500Tuple.second, nifty500Tuple.third),
            nifty1000 = buildSegment("nifty1000", "Nifty 1000", nifty1000Tuple.first, nifty1000Tuple.second, nifty1000Tuple.third),
            allNseEquity = buildSegment("allNseEquity", "All NSE Equity", allNseTuple.first, allNseTuple.second, allNseTuple.third)
        )

        if (!isValidRecord(updatedRecord)) {
            Log.w(TAG, "Discarding invalid live A/D record for $tradeDateStr")
            return
        }

        recordsTimeline[tradeTime] = updatedRecord

        // Persist to disk cache with version stamp
        try {
            val cacheObj = JSONObject().apply {
                put("version", CACHE_VERSION)
                put("date", tradeDateStr)
                fun putSeg(seg: IndexAdvanceDecline): JSONObject = JSONObject().apply {
                    put("adv", seg.advances)
                    put("dec", seg.declines)
                    put("unc", seg.unchanged)
                    put("total", seg.totalConstituents)
                    put("ratio", seg.ratio)
                    put("pct", seg.breadthPercent)
                }
                put("bseSensex", putSeg(updatedRecord.bseSensex))
                put("nifty50", putSeg(updatedRecord.nifty50))
                put("niftyNext50", putSeg(updatedRecord.niftyNext50))
                put("niftyMidcap150", putSeg(updatedRecord.niftyMidcap150))
                put("niftySmallcap250", putSeg(updatedRecord.niftySmallcap250))
                put("nifty500", putSeg(updatedRecord.nifty500))
                put("nifty1000", putSeg(updatedRecord.nifty1000))
                put("allNseEquity", putSeg(updatedRecord.allNseEquity))
            }
            File(context.filesDir, LIVE_CACHE_FILE).writeText(cacheObj.toString())
            Log.d(
                TAG,
                "Persisted live A/D cache for $tradeDateStr: Nifty 50 = ${updatedRecord.nifty50.advances}/${updatedRecord.nifty50.declines}/${updatedRecord.nifty50.unchanged}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving A/D disk cache", e)
        }

        adUpdateState.value++
    }

    fun getLatestRecord(context: Context): DailyAdvanceDeclineRecord {
        loadDataIfNeeded(context)
        return recordsTimeline.lastEntry()?.value ?: defaultRecord()
    }

    fun getRecordForTimestamp(timeMillis: Long, context: Context): DailyAdvanceDeclineRecord {
        loadDataIfNeeded(context)
        val norm = normalizeToMidnight(timeMillis)
        return recordsTimeline[norm]
            ?: recordsTimeline.floorEntry(norm)?.value
            ?: recordsTimeline.ceilingEntry(norm)?.value
            ?: recordsTimeline.lastEntry()?.value
            ?: defaultRecord()
    }

    fun getAllHistoricalRecords(context: Context): List<DailyAdvanceDeclineRecord> {
        loadDataIfNeeded(context)
        return recordsTimeline.values.toList()
    }

    /**
     * Retrieves historical A/D ratio or breadth series for a specific index segment
     * for any requested period: "1M", "3M", "6M", "1Y", "2Y", "5Y", "ALL" (from 01-04-2016).
     */
    fun getHistoricalSeries(
        indexKey: String,
        period: String,
        asBreadthPercent: Boolean,
        context: Context
    ): List<Pair<Long, Double>> {
        loadDataIfNeeded(context)
        val now = System.currentTimeMillis()
        val days = when (period.uppercase()) {
            "1M" -> 30L
            "3M" -> 90L
            "6M" -> 180L
            "1Y" -> 365L
            "2Y" -> 730L
            "5Y" -> 1825L
            "ALL" -> 4500L
            else -> 180L
        }
        val cutoff = if (period.uppercase() == "ALL") {
            parseIsoDate("2016-04-01") ?: (now - (days * 86400000L))
        } else {
            now - (days * 86400000L)
        }
        val sub = recordsTimeline.tailMap(cutoff, true)
        val points = mutableListOf<Pair<Long, Double>>()

        for ((time, record) in sub) {
            val seg = record.getSegment(indexKey)
            val v = if (asBreadthPercent) seg.breadthPercent else seg.ratio
            points.add(time to v)
        }

        if (points.isEmpty()) {
            val def = if (asBreadthPercent) 22.45 else 0.289
            points.add(now to def)
        }
        return points
    }

    private fun defaultRecord(): DailyAdvanceDeclineRecord {
        val tradeDate = "2026-11-13"
        val t = normalizeToMidnight(parseIsoDate(tradeDate) ?: System.currentTimeMillis())
        return DailyAdvanceDeclineRecord(
            dateStr = tradeDate,
            timestamp = t,
            bseSensex = buildSegment("bseSensex", "BSE Sensex", 5, 24, 1),
            nifty50 = buildSegment("nifty50", "Nifty 50", 11, 38, 1),
            niftyNext50 = buildSegment("niftyNext50", "Nifty Next 50", 9, 41, 0),
            niftyMidcap150 = buildSegment("niftyMidcap150", "Nifty Midcap 150", 47, 103, 0),
            niftySmallcap250 = buildSegment("niftySmallcap250", "Nifty Smallcap 250", 92, 158, 0),
            nifty500 = buildSegment("nifty500", "Nifty 500", 160, 339, 1),
            nifty1000 = buildSegment("nifty1000", "Nifty 1000", 359, 639, 2),
            allNseEquity = buildSegment("allNseEquity", "All NSE Equity", 1107, 1778, 42)
        )
    }
}
