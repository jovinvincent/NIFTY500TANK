package com.example

import android.content.Context
import android.util.JsonReader
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Encapsulates the complete quantitative volatility profile of India VIX,
 * including absolute level, 5-day / 20-day rate of change (ΔVIX), and acceleration (Δ²VIX).
 */
data class IndiaVixDynamics(
    val currentVix: Double,
    val delta5d: Double,
    val deltaPct5d: Double,
    val delta20d: Double,
    val deltaPct20d: Double,
    val acceleration: Double, // Change in 5-day slope (Δ5d_recent - Δ5d_prev)
    val trendLabel: String,
    val regimeLabel: String,
    val percentileRank: Double,
    val categoryBeta: Double,
    val directionalSignal: String // "Accelerating Fear 🔺", "Cresting / Cooling 🟢", "Volatility Expansion ⚠️", "Calm Baseline 🟢"
)

/**
 * Institutional India VIX (NSE Volatility Index) Manager.
 * Blends actual, authentic historical daily India VIX values from the National Stock Exchange (NSE)
 * directly into the Risk-Reward Engine, Historical Timeline, and Market Mood Index (MMI).
 */
object IndiaVixManager {

    private const val TAG = "IndiaVixManager"
    private val vixTreeMap = java.util.concurrent.ConcurrentSkipListMap<Long, Double>()
    private val isLoaded = AtomicBoolean(false)
    private val isFetchScheduled = AtomicBoolean(false)

    @Volatile
    private var sortedKeys = LongArray(0)
    @Volatile
    private var sortedVals = DoubleArray(0)

    private fun rebuildFastArrays() {
        val entries = vixTreeMap.entries.toList()
        val kArr = LongArray(entries.size)
        val vArr = DoubleArray(entries.size)
        for (i in entries.indices) {
            kArr[i] = entries[i].key
            vArr[i] = entries[i].value
        }
        sortedKeys = kArr
        sortedVals = vArr
    }

    @Volatile
    private var lastFetchTime = 0L

    private fun parseIsoDate(key: String): Long? {
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

    private fun normalizeToMidnight(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Loads historical India VIX from bundled asset file and merges local live updates.
     */
    fun loadVixDataIfNeeded(context: Context?) {
        if (context == null || isLoaded.get()) return
        synchronized(this) {
            if (isLoaded.get()) return

            // 1. Load historical India VIX from assets (1200+ trading days of real NSE records)
            try {
                context.assets.open("india_vix_historical.json").use { inputStream ->
                    JsonReader(inputStream.bufferedReader()).use { reader ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val dateKey = reader.nextName()
                            val vixVal = reader.nextDouble()
                            val time = parseIsoDate(dateKey)
                            if (time != null && vixVal > 0.0) {
                                vixTreeMap[normalizeToMidnight(time)] = vixVal
                            }
                        }
                        reader.endObject()
                    }
                }
                Log.d(TAG, "Loaded ${vixTreeMap.size} historical India VIX points from assets")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading historical India VIX from assets", e)
            }

            // 2. Load live persistent overlay from disk if available
            try {
                val overlayFile = File(context.filesDir, "india_vix_live_overlay.json")
                val text = if (overlayFile.exists()) overlayFile.readText() else ""
                if (text.isNotBlank()) {
                    val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val time = parseIsoDate(k)
                        if (time != null) {
                            val v = json.optDouble(k, 0.0)
                            if (v in 5.0..90.0) {
                                vixTreeMap[normalizeToMidnight(time)] = v
                            }
                        }
                    }
                    Log.d(TAG, "Merged live overlay. Total India VIX entries: ${vixTreeMap.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading live overlay for India VIX", e)
            }

            rebuildFastArrays()
            isLoaded.set(true)
        }
    }

    /**
     * Asynchronously fetches live India VIX closing values from official feeds.
     */
    fun triggerLiveIndiaVixFetch(context: Context?) {
        if (context == null) return
        loadVixDataIfNeeded(context)

        val now = System.currentTimeMillis()
        if (now - lastFetchTime < 1800_000L && isFetchScheduled.get()) {
            return // Skip if fetched within last 30 minutes
        }
        isFetchScheduled.set(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                fetchFromYahooFinance(context)
            } catch (e: Exception) {
                Log.w(TAG, "Live India VIX fetch error: ${e.message}")
            }
        }
    }

    val vixUpdateState = kotlinx.coroutines.flow.MutableStateFlow(0)

    private fun fetchFromYahooFinance(context: Context) {
        val endpoints = listOf(
            "https://query2.finance.yahoo.com/v8/finance/chart/%5EINDIAVIX?interval=1d&range=3mo",
            "https://query1.finance.yahoo.com/v8/finance/chart/%5EINDIAVIX?interval=1d&range=3mo"
        )

        for (urlStr in endpoints) {
            try {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "application/json")
                }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(responseText)
                    val chart = root.optJSONObject("chart")
                    val resultArr = chart?.optJSONArray("result")
                    if (resultArr != null && resultArr.length() > 0) {
                        val item = resultArr.getJSONObject(0)
                        val timestamps = item.optJSONArray("timestamp")
                        val indicators = item.optJSONObject("indicators")
                        val quoteArr = indicators?.optJSONArray("quote")
                        val quote = quoteArr?.optJSONObject(0)
                        val closes = quote?.optJSONArray("close")

                        if (timestamps != null && closes != null) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val newPoints = mutableMapOf<String, Double>()

                            for (idx in 0 until timestamps.length()) {
                                val epochSec = timestamps.optLong(idx, 0L)
                                val closeVal = closes.optDouble(idx, -1.0)
                                if (epochSec > 0 && closeVal > 0.0 && !closeVal.isNaN()) {
                                    val timeMs = epochSec * 1000L
                                    val norm = normalizeToMidnight(timeMs)
                                    val rounded = (closeVal * 100.0).toInt() / 100.0
                                    vixTreeMap[norm] = rounded
                                    newPoints[sdf.format(Date(norm))] = rounded
                                }
                            }

                            if (newPoints.isNotEmpty()) {
                                rebuildFastArrays()
                                lastFetchTime = System.currentTimeMillis()
                                vixUpdateState.value += 1
                                Log.d(TAG, "Updated India VIX with ${newPoints.size} recent points from Yahoo ($urlStr). Latest: ${vixTreeMap.lastEntry()?.value}")

                                // Persist live overlay
                                try {
                                    val overlayFile = File(context.filesDir, "india_vix_live_overlay.json")
                                    val text = if (overlayFile.exists()) overlayFile.readText() else ""
                                    val existingJson = if (text.isNotBlank()) {
                                        try { JSONObject(text) } catch (e: Exception) { JSONObject() }
                                    } else {
                                        JSONObject()
                                    }
                                    for ((k, v) in newPoints) {
                                        existingJson.put(k, v)
                                    }
                                    overlayFile.writeText(existingJson.toString())
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error saving live overlay for India VIX", e)
                                }
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Yahoo VIX fetch error for $urlStr: ${e.message}")
            }
        }
    }

    /**
     * Retrieves the authentic India VIX for any calendar date.
     * Uses trading day lookup (exact, ceiling, or floor).
     */
    fun getIndiaVixForDate(timestamp: Long, context: Context? = null): Double {
        if (context != null) loadVixDataIfNeeded(context)
        if (vixTreeMap.isEmpty()) return 14.54 // Fallback to current benchmark default if not loaded

        val norm = normalizeToMidnight(timestamp)
        vixTreeMap[norm]?.let { return it }

        // Find closest date within trading week
        val ceiling = vixTreeMap.ceilingEntry(norm)
        val floor = vixTreeMap.floorEntry(norm)

        return when {
            ceiling != null && floor != null -> {
                if (abs(ceiling.key - norm) <= abs(floor.key - norm)) ceiling.value else floor.value
            }
            ceiling != null -> ceiling.value
            floor != null -> floor.value
            else -> vixTreeMap.lastEntry()?.value ?: 14.54
        }
    }

    /**
     * Retrieves the latest current India VIX value.
     */
    fun getLatestIndiaVix(context: Context? = null): Double {
        if (context != null) loadVixDataIfNeeded(context)
        return vixTreeMap.lastEntry()?.value ?: 14.54
    }

    /**
     * Retrieves historical India VIX time series for the given period (1M, 3M, 6M, 1Y, 2Y, 5Y, ALL from 01-04-2016).
     */
    fun getHistoricalSeries(period: String, context: Context?): List<Pair<Long, Double>> {
        if (context != null) loadVixDataIfNeeded(context)
        val now = System.currentTimeMillis()
        val days = when (period.uppercase()) {
            "1M" -> 30L
            "3M" -> 90L
            "6M" -> 180L
            "1Y" -> 365L
            "2Y" -> 730L
            "5Y" -> 1825L
            "ALL" -> 4500L // Since 01-04-2016
            else -> 90L
        }
        val cutoff = if (period.uppercase() == "ALL") {
            parseIsoDate("2016-04-01") ?: (now - (days * 86400000L))
        } else {
            now - (days * 86400000L)
        }
        val points = mutableListOf<Pair<Long, Double>>()
        val sub = vixTreeMap.tailMap(cutoff, true)
        for ((time, value) in sub) {
            if (value > 0.0) {
                points.add(time to value)
            }
        }
        val latest = getLatestIndiaVix(context)
        if (points.isEmpty()) {
            val step = max(1L, days / 30L)
            var d = days
            while (d >= 0) {
                points.add((now - d * 86400000L) to latest)
                d -= step
            }
        } else if (abs(points.last().first - now) > 86400000L) {
            points.add(now to latest)
        }
        return points
    }

    /**
     * Calculates the rolling percentile rank (0 to 100) of India VIX over the past 250 trading days (1 year).
     */
    fun getRollingPercentile(timestamp: Long, context: Context? = null): Double {
        if (context != null) loadVixDataIfNeeded(context)
        val keys = sortedKeys
        val vals = sortedVals
        if (vals.size < 20) return 40.0

        val currentVix = getIndiaVixForDate(timestamp, context)
        val norm = normalizeToMidnight(timestamp)
        val bs = keys.binarySearch(norm)
        val endIdx = if (bs >= 0) bs else (-bs - 2)
        if (endIdx < 0) return 40.0

        val startIdx = max(0, endIdx - 249)
        val count = endIdx - startIdx + 1
        if (count <= 0) return 40.0

        var countLower = 0
        for (i in startIdx..endIdx) {
            if (vals[i] < currentVix) countLower++
        }
        return (countLower.toDouble() / count) * 100.0
    }

    /**
     * Human-interpretable volatility regime label based on official NSE India VIX thresholds.
     */
    fun getRegimeLabel(vix: Double): String {
        return when {
            vix < 12.0 -> "Extreme Complacency (<12) 🟡"
            vix < 15.5 -> "Controlled / Normal (12-15.5) 🟢"
            vix < 19.0 -> "Elevated Volatility (15.5-19) 🟠"
            vix < 24.0 -> "High Fear / Uncertainty (19-24) 🔴"
            else -> "Market Panic / Capitulation (>24) 🟣"
        }
    }

    /**
     * Category-Aware Sensitivity Beta (β_vix) to India VIX.
     * Pure equity funds (especially SMID and small-caps) have the highest sensitivity,
     * while hybrid and debt funds buffer equity volatility.
     */
    fun getCategorySensitivityBeta(categoryProfile: String): Double {
        val s = categoryProfile.lowercase()
        return when {
            s.contains("smid") || s.contains("small") -> 1.35 // Pure SMID/Small Cap: High Beta to market fear
            s.contains("mid") || s.contains("sector") || s.contains("thematic") -> 1.20 // Mid/Thematic equity
            s.contains("large") || s.contains("flexi") || s.contains("multi-cap") || s.contains("broad equity") -> 1.00 // Standard broad equity
            s.contains("hybrid") || s.contains("multi-asset") || s.contains("asset allocator") -> 0.50 // Buffered by debt / hedges
            s.contains("debt") || s.contains("capital preservation") || s.contains("liquid") -> 0.15 // Minimal equity transmission
            else -> 1.00
        }
    }

    /**
     * Computes the complete quantitative rate of change (ΔVIX) and acceleration dynamics for a given date.
     */
    fun getDynamics(timestamp: Long, categoryProfile: String = "Diversified Equity", context: Context? = null): IndiaVixDynamics {
        if (context != null) loadVixDataIfNeeded(context)
        val norm = normalizeToMidnight(timestamp)
        val currentVix = getIndiaVixForDate(timestamp, context)
        val regime = getRegimeLabel(currentVix)
        val beta = getCategorySensitivityBeta(categoryProfile)
        val pctile = getRollingPercentile(timestamp, context)

        val keys = sortedKeys
        val vals = sortedVals
        if (vals.size < 6) {
            return IndiaVixDynamics(
                currentVix = currentVix,
                delta5d = 0.0,
                deltaPct5d = 0.0,
                delta20d = 0.0,
                deltaPct20d = 0.0,
                acceleration = 0.0,
                trendLabel = "Stable / Range-Bound 🟡",
                regimeLabel = regime,
                percentileRank = pctile,
                categoryBeta = beta,
                directionalSignal = "Calm Baseline 🟢"
            )
        }

        val bs = keys.binarySearch(norm)
        val endIdx = if (bs >= 0) bs else (-bs - 2)
        val n = endIdx + 1
        val vNow = if (n > 0) vals[endIdx] else currentVix
        val v5d = if (n >= 6) vals[endIdx - 5] else vNow
        val v10d = if (n >= 11) vals[endIdx - 10] else v5d
        val v20d = if (n >= 21) vals[endIdx - 20] else v5d

        val d5d = vNow - v5d
        val dPct5d = if (v5d > 0.0) (d5d / v5d) * 100.0 else 0.0
        val dPrev5d = v5d - v10d
        val accel = d5d - dPrev5d
        val d20d = vNow - v20d
        val dPct20d = if (v20d > 0.0) (d20d / v20d) * 100.0 else 0.0

        val (trend, signal) = when {
            // High VIX regimes (>19)
            vNow >= 19.0 -> when {
                d5d < -0.8 -> "Cresting / Panic Subsidizing 🟢" to "Cresting / Cooling 🟢"
                d5d > 1.8 && accel > 0.3 -> "Spiking / Fear Escalating 🔺" to "Accelerating Fear 🔺"
                d5d > 0.0 -> "Elevated / Simmering Fear 🟠" to "Elevated Volatility 🟠"
                else -> "Elevated Volatility Plateau 🟡" to "Plateauing 🟡"
            }
            // Moderate VIX regimes (15.5 to 18.9)
            vNow >= 15.5 -> when {
                d5d > 1.2 && accel > 0.4 -> "Surging Into Elevated Fear ↗️" to "Accelerating Fear 🔺"
                d5d < -1.0 -> "Volatility Cooling Down 🟢" to "Cresting / Cooling 🟢"
                d5d > 0.4 -> "Drifting Upward ↗️" to "Volatility Expansion ⚠️"
                else -> "Moderate Fluctuations 🟡" to "Calm Baseline 🟢"
            }
            // Controlled VIX regimes (12.0 to 15.4)
            vNow >= 12.0 -> when {
                d5d > 1.2 -> "Awakening from Trough ⚠️" to "Volatility Expansion ⚠️"
                d5d < -0.8 -> "Subdued & Controlled 🟢" to "Calm Baseline 🟢"
                else -> "Normal Range-Bound 🟢" to "Calm Baseline 🟢"
            }
            // Extreme Complacency (<12.0)
            else -> when {
                d5d > 0.5 -> "Exiting Deep Trough ⚠️" to "Volatility Expansion ⚠️"
                else -> "Deep Complacency (<12) 🟡" to "Complacency Low 🟡"
            }
        }

        return IndiaVixDynamics(
            currentVix = vNow,
            delta5d = (d5d * 100.0).roundToInt() / 100.0,
            deltaPct5d = (dPct5d * 10.0).roundToInt() / 10.0,
            delta20d = (d20d * 100.0).roundToInt() / 100.0,
            deltaPct20d = (dPct20d * 10.0).roundToInt() / 10.0,
            acceleration = (accel * 100.0).roundToInt() / 100.0,
            trendLabel = trend,
            regimeLabel = regime,
            percentileRank = pctile,
            categoryBeta = beta,
            directionalSignal = signal
        )
    }
}
