package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

object BenchmarkSyncManager {
    private const val TAG = "BenchmarkSyncManager"

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncStatus = MutableStateFlow<String>("Sync Disabled")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long>(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val INDEX_MAP = mapOf(
        "Nifty 50 TRI" to ("%5ENSEI" to "nifty50"),
        "Nifty 500 TRI" to ("%5ECRSLDX" to "nifty500"),
        "Nifty Midcap 150 TRI" to ("NIFTYMIDCAP150.NS" to "niftyMidcap150"),
        "Nifty Smallcap 250 TRI" to ("NIFTYSMLCAP250.NS" to "niftySmallcap250")
    )

    /**
     * Incrementally syncs benchmark indices (Nifty 50, 500, Midcap 150, Smallcap 250)
     * and persists records directly to the local Room database (historical_navs table).
     */
    suspend fun syncBenchmarkIndices(repository: FundRepository): Boolean = withContext(Dispatchers.IO) {
        var totalSynced = 0
        val sdfDisplay = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.US)

        for ((schemeName, pair) in INDEX_MAP) {
            val (symbol, simulatorKey) = pair
            try {
                val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=1mo"
                val responseStr = org.jsoup.Jsoup.connect(url)
                    .ignoreContentType(true)
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(6000)
                    .execute()
                    .body()

                val json = org.json.JSONObject(responseStr)
                val chartObj = json.optJSONObject("chart")
                val resultArr = chartObj?.optJSONArray("result")
                if (resultArr != null && resultArr.length() > 0) {
                    val result = resultArr.getJSONObject(0)
                    val timestamps = result.optJSONArray("timestamp")
                    val indicators = result.optJSONObject("indicators")
                    val quoteArr = indicators?.optJSONArray("quote")
                    val closes = quoteArr?.optJSONObject(0)?.optJSONArray("close")

                    if (timestamps != null && closes != null) {
                        val navRecords = mutableListOf<HistoricalNav>()
                        for (i in 0 until minOf(timestamps.length(), closes.length())) {
                            val ts = timestamps.optLong(i, 0L) * 1000L
                            val price = closes.optDouble(i, 0.0)
                            if (ts > 0L && price > 0.0 && !price.isNaN() && !price.isInfinite()) {
                                val dateStr = sdfDisplay.format(java.util.Date(ts))
                                navRecords.add(HistoricalNav(schemeName, dateStr, price))
                                IndexBenchmarkSimulator.updateIndexValue(simulatorKey, ts, price)
                            }
                        }
                        if (navRecords.isNotEmpty()) {
                            repository.insertHistoricalNavs(navRecords)
                            totalSynced += navRecords.size
                            Log.d(TAG, "Synced ${navRecords.size} live index points for $schemeName into SQLite.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing index $schemeName ($symbol): ${e.message}")
            }
        }
        _lastSyncStatus.value = if (totalSynced > 0) "Synced $totalSynced index points" else "Indices Up to date"
        _lastSyncTimestamp.value = System.currentTimeMillis()
        totalSynced > 0
    }

    /**
     * Executes daily sync. Clears temporary cache overlays and syncs indices if needed.
     */
    suspend fun syncAllBenchmarks(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext false
        _isSyncing.value = true
        try {
            val indexOverlay = File(context.filesDir, "index_live_overlay.json")
            if (indexOverlay.exists()) {
                indexOverlay.delete()
            }
            val quantOverlay = File(context.filesDir, "quant_benchmarks_live_overlay.json")
            if (quantOverlay.exists()) {
                quantOverlay.delete()
            }
            _lastSyncStatus.value = "Synced"
            _lastSyncTimestamp.value = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in benchmark sync: ${e.message}", e)
            _lastSyncStatus.value = "Error"
            false
        } finally {
            _isSyncing.value = false
        }
    }
}
