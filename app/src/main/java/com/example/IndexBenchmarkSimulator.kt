package com.example

import android.content.Context
import android.util.JsonReader
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

object IndexBenchmarkSimulator {
    private val indexDataTreeMap = java.util.concurrent.ConcurrentSkipListMap<Long, Map<String, Double>>()
    private val quantLiquidTreeMap = java.util.concurrent.ConcurrentSkipListMap<Long, Double>()
    private val benchmarkNavCache = ConcurrentHashMap<String, TreeMap<Long, Double>>()
    @Volatile
    private var isIndexLoaded = false

    val benchmarkFunds = mapOf(
        "Quant Liquid Plan - Direct (G)" to "quant_liquid_navs.json",
        "Quant Overnight Fund - Direct (G)" to "quant_overnight_fund_direct_plan_growth_option.json",
        "Quant Multi Asset Allocation Fund - Direct (G)" to "quant_multi_asset_allocation_fund_direct_plan_growth_option.json",
        "Quant Flexi Cap Fund - Direct (G)" to "quant_flexi_cap_fund_direct_plan_growth_option.json",
        "Quant Small Cap Fund - Direct (G)" to "quant_small_cap_fund_direct_plan_growth_option.json",
        "Quant Momentum Fund - Direct (G)" to "quant_momentum_fund_direct_plan_growth_option.json",
        "Quant Business Cycle Fund - Direct (G)" to "quant_business_cycle_fund_direct_plan_growth_option.json",
        "Quant Aggressive Hybrid Fund - Direct (G)" to "quant_aggressive_hybrid_fund_direct_plan_growth_option.json",
        "Quant Dynamic Asset Allocation Fund - Direct (G)" to "quant_dynamic_asset_allocation_fund_direct_plan_growth_option.json",
        "Quant Multi Cap Fund - Direct (G)" to "quant_multi_cap_fund_direct_plan_growth_option.json",
        "Quant BFSI Fund - Direct (G)" to "quant_bfsi_fund_direct_plan_growth_option.json",
        "Quant Infrastructure Fund - Direct (G)" to "quant_infrastructure_fund_direct_plan_growth_option.json",
        "Quant Healthcare Fund - Direct (G)" to "quant_healthcare_fund_direct_plan_growth_option.json",
        "Quant Mid Cap Fund - Direct (G)" to "quant_mid_cap_fund_direct_plan_growth_option.json",
        "Quant Quantamental Fund - Direct (G)" to "quant_quantamental_fund_direct_plan_growth_option.json",
        "Quant Value Fund - Direct (G)" to "quant_value_fund_direct_plan_growth_option.json",
        "Quant ESG Integration Strategy Fund - Direct (G)" to "quant_esg_integration_strategy_fund_direct_plan_growth_option.json",
        "Quant Arbitrage Fund - Direct (G)" to "quant_arbitrage_fund_direct_plan_growth_option.json",
        "Quant Equity Savings Fund - Direct (G)" to "quant_equity_savings_fund_direct_plan_growth_option.json",
        "Quant Income Plus Arbitrage Active FoF - Direct (G)" to "quant_income_plus_arbitrage_active_fof_direct_plan_growth_option.json"
    )

    private fun parseIsoDateFast(key: String): Long? {
        if (key.length != 10 || key[4] != '-' || key[7] != '-') return null
        val y = (key[0] - '0') * 1000 + (key[1] - '0') * 100 + (key[2] - '0') * 10 + (key[3] - '0')
        val m = (key[5] - '0') * 10 + (key[6] - '0') - 1
        val d = (key[8] - '0') * 10 + (key[9] - '0')
        if (y < 1990 || m !in 0..11 || d !in 1..31) return null
        val cal = Calendar.getInstance()
        cal.set(y, m, d, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private val parsedDateCache = ConcurrentHashMap<String, Long>()

    fun parseAnyDateToMillis(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        val trimmed = dateStr.trim()
        val cached = parsedDateCache[trimmed]
        if (cached != null) return cached

        parseIsoDateFast(trimmed)?.let {
            parsedDateCache[trimmed] = it
            return it
        }

        val patterns = arrayOf(
            "dd-MMM-yyyy",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy/MM/dd",
            "MMM dd, yyyy"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(trimmed)
                if (date != null) {
                    val ms = date.time
                    parsedDateCache[trimmed] = ms
                    return ms
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun loadIndexDataIfNeeded(context: Context) {
        if (isIndexLoaded && quantLiquidTreeMap.isNotEmpty()) return
        synchronized(this) {
            if (isIndexLoaded && quantLiquidTreeMap.isNotEmpty()) return
            try {
                context.assets.open("index_historical.json").use { inputStream ->
                    JsonReader(inputStream.bufferedReader()).use { reader ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            if (key.length == 10 && key[4] == '-' && key[7] == '-') {
                                val time = parseIsoDateFast(key)
                                var n50 = 25000.0
                                var n500 = 23000.0
                                var nMid = 21000.0
                                var nSmall = 17000.0
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "nifty50" -> n50 = reader.nextDouble()
                                        "nifty500" -> n500 = reader.nextDouble()
                                        "niftyMidcap150" -> nMid = reader.nextDouble()
                                        "niftySmallcap250" -> nSmall = reader.nextDouble()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (time != null) {
                                    indexDataTreeMap[time] = mapOf(
                                        "nifty50" to n50,
                                        "nifty500" to n500,
                                        "niftyMidcap150" to nMid,
                                        "niftySmallcap250" to nSmall
                                    )
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
                isIndexLoaded = true
                Log.d("IndexBenchmarkSimulator", "Loaded index historical data with ${indexDataTreeMap.size} entries")
            } catch (e: Exception) {
                Log.e("IndexBenchmarkSimulator", "Error loading index data", e)
            }

            // Load persistent live overlay for indices if available
            try {
                val overlayFile = java.io.File(context.filesDir, "index_live_overlay.json")
                if (overlayFile.exists()) {
                    val overlayStr = overlayFile.readText()
                    val json = org.json.JSONObject(overlayStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val time = parseIsoDateFast(key)
                        if (time != null) {
                            val dayObj = json.getJSONObject(key)
                            val existing = indexDataTreeMap[time]?.toMutableMap() ?: mutableMapOf()
                            if (dayObj.has("nifty50")) existing["nifty50"] = dayObj.getDouble("nifty50")
                            if (dayObj.has("nifty500")) existing["nifty500"] = dayObj.getDouble("nifty500")
                            if (dayObj.has("niftyMidcap150")) existing["niftyMidcap150"] = dayObj.getDouble("niftyMidcap150")
                            if (dayObj.has("niftySmallcap250")) existing["niftySmallcap250"] = dayObj.getDouble("niftySmallcap250")
                            indexDataTreeMap[time] = existing
                        }
                    }
                    Log.d("IndexBenchmarkSimulator", "Merged live index overlay. Total entries: ${indexDataTreeMap.size}")
                }
            } catch (e: Exception) {
                Log.e("IndexBenchmarkSimulator", "Error loading live index overlay", e)
            }

            try {
                context.assets.open("quant_liquid_navs.json").use { inputStream ->
                    JsonReader(inputStream.bufferedReader()).use { reader ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            if (key.length == 10 && key[4] == '-' && key[7] == '-') {
                                val time = parseIsoDateFast(key)
                                val nav = reader.nextDouble()
                                if (time != null && nav > 0.0) {
                                    quantLiquidTreeMap[time] = nav
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
                Log.d("IndexBenchmarkSimulator", "Loaded Quant Liquid NAV data with ${quantLiquidTreeMap.size} entries")
            } catch (e: Exception) {
                Log.e("IndexBenchmarkSimulator", "Error loading Quant Liquid NAV data", e)
            }
        }
    }

    fun updateIndexValue(indexKey: String, dateMillis: Long, value: Double) {
        if (value > 0.0) {
            val norm = normalizeToMidnight(dateMillis)
            val existing = indexDataTreeMap[norm]?.toMutableMap() ?: mutableMapOf(
                "nifty50" to 25000.0,
                "nifty500" to 23000.0,
                "niftyMidcap150" to 21000.0,
                "niftySmallcap250" to 17000.0
            )
            existing[indexKey] = value
            indexDataTreeMap[norm] = existing
            clearCache()
        }
    }

    fun updateQuantLiquidNav(dateMillis: Long, nav: Double) {
        if (nav > 0.0) {
            val norm = normalizeToMidnight(dateMillis)
            quantLiquidTreeMap[norm] = nav
            clearCache()
        }
    }

    fun updateBenchmarkNav(schemeName: String, dateMillis: Long, nav: Double) {
        if (nav > 0.0) {
            val norm = normalizeToMidnight(dateMillis)
            val map = benchmarkNavCache.getOrPut(schemeName) { TreeMap() }
            map[norm] = nav
            clearCache()
        }
    }

    fun getLatestBenchmarkDate(context: Context): String {
        loadIndexDataIfNeeded(context)
        var maxTime = indexDataTreeMap.lastKey() ?: 0L
        for ((_, tree) in benchmarkNavCache) {
            val lastKey = tree.lastKey() ?: 0L
            if (lastKey > maxTime) maxTime = lastKey
        }
        if (maxTime == 0L) return "Live Daily"
        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        return sdf.format(Date(maxTime))
    }

    fun getQuantLiquidNavForDate(timestamp: Long): Double {
        if (quantLiquidTreeMap.isEmpty()) {
            return 45.5428
        }
        val normTime = normalizeToMidnight(timestamp)
        val exact = quantLiquidTreeMap[normTime]
        if (exact != null && exact > 0.0) {
            return exact
        }
        val ceiling = quantLiquidTreeMap.ceilingEntry(normTime)
        if (ceiling != null && ceiling.value > 0.0) {
            return ceiling.value
        }
        val floor = quantLiquidTreeMap.floorEntry(normTime)
        if (floor != null && floor.value > 0.0) {
            return floor.value
        }
        return quantLiquidTreeMap.lastEntry()?.value ?: 45.5428
    }

    private fun normalizeToMidnight(timeMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun loadNavDataFromFile(context: Context, schemeName: String, fileName: String): TreeMap<Long, Double> {
        val cached = benchmarkNavCache[schemeName]
        if (cached != null && cached.isNotEmpty()) {
            return cached
        }
        val navTreeMap = TreeMap<Long, Double>()
        try {
            context.assets.open(fileName).use { inputStream ->
                JsonReader(inputStream.bufferedReader()).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (key.length == 10 && key[4] == '-' && key[7] == '-') {
                            val time = parseIsoDateFast(key)
                            val nav = reader.nextDouble()
                            if (time != null && nav > 0.0) {
                                navTreeMap[time] = nav
                            }
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
        } catch (e: Exception) {
            Log.e("IndexBenchmarkSimulator", "Error streaming NAV data from $fileName", e)
        }

        // Overlay persistent live updates if any
        try {
            val overlayFile = java.io.File(context.filesDir, "quant_benchmarks_live_overlay.json")
            if (overlayFile.exists()) {
                val overlayJson = org.json.JSONObject(overlayFile.readText())
                if (overlayJson.has(schemeName)) {
                    val schemeObj = overlayJson.getJSONObject(schemeName)
                    val baseLastNav = navTreeMap.lastEntry()?.value
                    val keys = schemeObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val time = parseIsoDateFast(k)
                        if (time != null) {
                            val v = schemeObj.optDouble(k, 0.0)
                            // Strict validation: Reject IDCW/corrupted NAVs (must be positive and within 0.7x to 1.5x of base NAV)
                            val isValid = if (baseLastNav != null && baseLastNav > 0.0) {
                                v in (baseLastNav * 0.70)..(baseLastNav * 1.50)
                            } else {
                                v > 5.0 && v < 15000.0
                            }
                            if (isValid) {
                                navTreeMap[time] = v
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IndexBenchmarkSimulator", "Error merging live fund overlay for $schemeName", e)
        }

        benchmarkNavCache[schemeName] = navTreeMap
        return navTreeMap
    }

    private fun calculateSimpleCagr(invested: Double, currentValue: Double, startMillis: Long, endMillis: Long): Double {
        if (invested <= 0.0 || currentValue <= 0.0 || endMillis <= startMillis) return 0.0
        val years = (endMillis - startMillis).toDouble() / (365.25 * 24 * 60 * 60 * 1000)
        if (years <= 0.0) return 0.0
        return try {
            val cagr = (Math.pow(currentValue / invested, 1.0 / years) - 1.0) * 100.0
            if (cagr.isNaN() || cagr.isInfinite()) 0.0 else cagr.coerceIn(-90.0, 500.0)
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun preloadAllBenchmarkData(context: Context) = withContext(Dispatchers.IO) {
        loadIndexDataIfNeeded(context)
        for ((bName, bFile) in benchmarkFunds) {
            loadNavDataFromFile(context, bName, bFile)
        }
    }

    private fun getNavForDate(navMap: TreeMap<Long, Double>, timestamp: Long): Double {
        if (navMap.isEmpty()) return 45.5428
        val normTime = normalizeToMidnight(timestamp)
        val exact = navMap[normTime]
        if (exact != null && exact > 0.0) return exact
        val ceiling = navMap.ceilingEntry(normTime)
        if (ceiling != null && ceiling.value > 0.0) return ceiling.value
        val floor = navMap.floorEntry(normTime)
        if (floor != null && floor.value > 0.0) return floor.value
        return navMap.lastEntry()?.value ?: 45.5428
    }


    fun getIndexValueForDate(indexName: String, timestamp: Long): Double {
        if (indexDataTreeMap.isEmpty()) {
            return when (indexName) {
                "nifty50" -> 25000.0
                "nifty500" -> 23000.0
                "niftyMidcap150" -> 21000.0
                "niftySmallcap250" -> 17000.0
                else -> 100.0
            }
        }
        val normTime = normalizeToMidnight(timestamp)
        // 1. Exact match on trading day
        val exact = indexDataTreeMap[normTime]
        if (exact != null && exact.containsKey(indexName)) {
            return exact[indexName] ?: 100.0
        }
        // 2. If weekend or holiday, order executes on next available trading day (SEBI/NSE rule)
        val ceiling = indexDataTreeMap.ceilingEntry(normTime)
        if (ceiling != null && ceiling.value.containsKey(indexName)) {
            return ceiling.value[indexName] ?: 100.0
        }
        // 3. If after latest date, use most recent recorded trading day (floor)
        val floor = indexDataTreeMap.floorEntry(normTime)
        if (floor != null && floor.value.containsKey(indexName)) {
            return floor.value[indexName] ?: 100.0
        }
        return indexDataTreeMap.firstEntry()?.value?.get(indexName) ?: 100.0
    }

    fun getMasterIndexTimeline(context: Context?): List<Pair<Long, Map<String, Double>>> {
        if (context != null) loadIndexDataIfNeeded(context)
        return indexDataTreeMap.map { it.key to it.value }
    }

    fun getIndexRecords(context: Context, benchmarkName: String): List<NavRecord> {
        loadIndexDataIfNeeded(context)
        val key = when {
            benchmarkName.contains("500", ignoreCase = true) -> "nifty500"
            benchmarkName.contains("Midcap", ignoreCase = true) -> "niftyMidcap150"
            benchmarkName.contains("Smallcap", ignoreCase = true) -> "niftySmallcap250"
            else -> "nifty50"
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val list = mutableListOf<NavRecord>()
        for ((time, map) in indexDataTreeMap) {
            val v = map[key]
            if (v != null && v > 0.0) {
                list.add(NavRecord(sdf.format(Date(time)), v))
            }
        }
        return list
    }

    fun getBenchmarkFundRecords(context: Context, schemeName: String): List<NavRecord> {
        val matchedEntry = benchmarkFunds.entries.find { (k, _) ->
            k.equals(schemeName, ignoreCase = true)
        } ?: benchmarkFunds.entries.find { (k, _) ->
            val hasIncomePlus1 = k.contains("income plus", ignoreCase = true)
            val hasIncomePlus2 = schemeName.contains("income plus", ignoreCase = true)
            hasIncomePlus1 == hasIncomePlus2 && (
                k.contains(schemeName, ignoreCase = true) ||
                schemeName.contains(k, ignoreCase = true) ||
                (schemeName.contains("mid", ignoreCase = true) && schemeName.contains("cap", ignoreCase = true) && k.contains("mid", ignoreCase = true))
            )
        }
        val targetScheme = matchedEntry?.key ?: schemeName
        val fileName = matchedEntry?.value ?: benchmarkFunds[schemeName]
        val tree = if (fileName != null) {
            loadNavDataFromFile(context, targetScheme, fileName)
        } else {
            benchmarkNavCache[schemeName] ?: TreeMap()
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val list = mutableListOf<NavRecord>()
        for ((time, nav) in tree) {
            if (nav > 0.0) {
                list.add(NavRecord(sdf.format(Date(time)), nav))
            }
        }
        return list
    }

    fun getFullHistoricalNavs(context: Context, scheme: String, fallbackRecords: List<NavRecord> = emptyList()): List<NavRecord> {
        try {
            val isIndex = scheme.contains("Nifty", ignoreCase = true) || scheme.contains("TRI", ignoreCase = true)
            if (isIndex) {
                val indexRecords = getIndexRecords(context, scheme)
                if (indexRecords.isNotEmpty()) return indexRecords
            }

            val fundRecords = getBenchmarkFundRecords(context, scheme)
            if (fundRecords.isNotEmpty()) return fundRecords
        } catch (e: Exception) {
            Log.e("IndexBenchmarkSimulator", "Error in getFullHistoricalNavs for $scheme", e)
        }

        return fallbackRecords
    }

    data class BenchmarkSimulationResult(
        val name: String,
        val simulatedXirr: Double,
        val alpha: Double,
        val simulatedCurrentValue: Double,
        val totalInvested: Double
    )

    data class PortfolioCashflowEvent(
        val timestamp: Long,
        val isInvestment: Boolean, // true = purchase/inflow, false = redemption/withdrawal/outflow
        val amount: Double
    )

    data class BenchmarkFifoLot(
        val timestamp: Long,
        var units: Double,
        val costPerUnit: Double
    )

    private var cachedSimulationKey: String? = null
    private var cachedSimulationResults: List<BenchmarkSimulationResult>? = null

    fun getCachedSimulationResults(): List<BenchmarkSimulationResult>? = cachedSimulationResults

    fun clearCache() {
        cachedSimulationKey = null
        cachedSimulationResults = null
    }

    private fun extractPortfolioCashflows(
        investedItems: List<FundUiItem>,
        allTxns: List<CasTransactionEntity>,
        startCal: Calendar
    ): List<PortfolioCashflowEvent> {
        val events = mutableListOf<PortfolioCashflowEvent>()

        if (allTxns.isNotEmpty()) {
            val relevantTxns = if (investedItems.isNotEmpty()) {
                allTxns.filter { txn ->
                    investedItems.any { item -> ReturnsCalculations.isSameScheme(txn.schemeName, item.schemeName) }
                }
            } else {
                allTxns
            }
            val txnsToUse = if (relevantTxns.isNotEmpty()) relevantTxns else allTxns

            for (txn in txnsToUse) {
                val rawTime = parseAnyDateToMillis(txn.date) ?: continue
                val t = normalizeToMidnight(rawTime)

                val isBuy = txn.type.contains("Buy", ignoreCase = true) ||
                            txn.type.contains("Purchase", ignoreCase = true) ||
                            txn.type.contains("SIP", ignoreCase = true) ||
                            txn.type.contains("Switch In", ignoreCase = true) ||
                            (txn.units > 0 && txn.amount > 0 && !txn.type.contains("Sell", ignoreCase = true) && !txn.type.contains("Redemption", ignoreCase = true) && !txn.type.contains("Switch Out", ignoreCase = true))

                val isSell = txn.type.contains("Sell", ignoreCase = true) ||
                             txn.type.contains("Redemption", ignoreCase = true) ||
                             txn.type.contains("Switch Out", ignoreCase = true) ||
                             txn.units < 0 || txn.amount < 0

                val rawAmount = kotlin.math.abs(txn.amount)
                val amount = if (rawAmount > 0.0) rawAmount else kotlin.math.abs(txn.units * txn.nav)

                if (isBuy && amount > 0.0) {
                    events.add(PortfolioCashflowEvent(t, true, amount))
                } else if (isSell && amount > 0.0) {
                    events.add(PortfolioCashflowEvent(t, false, amount))
                }
            }
        }

        if (events.isEmpty()) {
            val tStart = normalizeToMidnight(startCal.timeInMillis)
            for (item in investedItems) {
                if (item.investedAmount > 0) {
                    events.add(PortfolioCashflowEvent(tStart, true, item.investedAmount))
                }
            }
        }

        return events.sortedBy { it.timestamp }
    }

    fun runBenchmarkSimulation(
        context: Context,
        investedItems: List<FundUiItem>,
        allTxns: List<CasTransactionEntity>,
        portfolioXirr: Double,
        startCal: Calendar
    ): List<BenchmarkSimulationResult> {
        val cacheKey = "${investedItems.size}_${allTxns.size}_${portfolioXirr.toInt()}_${investedItems.sumOf { it.currentValue }.toLong()}"
        if (cachedSimulationKey == cacheKey && cachedSimulationResults != null) {
            return cachedSimulationResults!!
        }

        loadIndexDataIfNeeded(context)

        val defaultValuationDate = ReturnsCalculations.getValuationDate().time
        val latestValuationDate = investedItems.mapNotNull {
            parseAnyDateToMillis(it.fund.navDate)
        }.maxOrNull() ?: defaultValuationDate

        // Extract the portfolio's active FIFO investment lots (the exact basis of the portfolio's XIRR and Invested Capital)
        val activePortfolioLots = mutableListOf<ReturnsCalculations.Lot>()
        if (investedItems.isNotEmpty() && allTxns.isNotEmpty()) {
            for (item in investedItems) {
                val fundTxns = ReturnsCalculations.filterTxnsForScheme(allTxns, item.schemeName)
                val lots = ReturnsCalculations.runFifo(fundTxns)
                if (lots.isNotEmpty()) {
                    activePortfolioLots.addAll(lots)
                } else if (item.investedAmount > 0) {
                    val t = parseAnyDateToMillis(item.fund.navDate) ?: startCal.timeInMillis
                    activePortfolioLots.add(ReturnsCalculations.Lot(item.unitsHeld.coerceAtLeast(1.0), item.investedAmount / item.unitsHeld.coerceAtLeast(1.0), Date(t)))
                }
            }
        } else if (investedItems.isNotEmpty()) {
            val tStart = startCal.timeInMillis
            for (item in investedItems) {
                if (item.investedAmount > 0) {
                    activePortfolioLots.add(ReturnsCalculations.Lot(item.unitsHeld.coerceAtLeast(1.0), item.investedAmount / item.unitsHeld.coerceAtLeast(1.0), Date(tStart)))
                }
            }
        }

        val totalInvestedCapital = activePortfolioLots.sumOf { it.units * it.costPerUnit }

        fun simulateActiveLotsForBenchmark(
            name: String,
            getPrice: (Long) -> Double
        ): BenchmarkSimulationResult {
            var totalBenchmarkUnits = 0.0
            val xirrCashflows = mutableListOf<Pair<Long, Double>>()

            for (lot in activePortfolioLots) {
                val lotTime = normalizeToMidnight(lot.date.time)
                val lotCost = lot.units * lot.costPerUnit
                if (lotCost <= 0.0) continue

                val priceOnDate = getPrice(lotTime)
                if (priceOnDate > 0.0) {
                    val unitsBought = lotCost / priceOnDate
                    totalBenchmarkUnits += unitsBought
                    xirrCashflows.add(lotTime to -lotCost)
                }
            }

            val finalPrice = getPrice(latestValuationDate)
            val finalSimulatedValue = totalBenchmarkUnits * finalPrice

            if (finalSimulatedValue > 0.0) {
                xirrCashflows.add(latestValuationDate to finalSimulatedValue)
            }

            val simulatedXirr = if (xirrCashflows.size >= 2 && xirrCashflows.any { it.second < 0 } && xirrCashflows.any { it.second > 0 }) {
                val rawXirr = ReturnsCalculations.trueXirr(xirrCashflows)
                if (rawXirr.isNaN() || rawXirr.isInfinite() || rawXirr < -90.0 || rawXirr > 500.0) {
                    calculateSimpleCagr(totalInvestedCapital, finalSimulatedValue, xirrCashflows.first().first, latestValuationDate)
                } else {
                    rawXirr
                }
            } else {
                0.0
            }

            val alpha = portfolioXirr - simulatedXirr

            return BenchmarkSimulationResult(
                name = name,
                simulatedXirr = simulatedXirr,
                alpha = alpha,
                simulatedCurrentValue = finalSimulatedValue,
                totalInvested = totalInvestedCapital
            )
        }

        val indexKeys = listOf("nifty50", "niftyMidcap150", "niftySmallcap250", "nifty500")
        val indexDisplayNames = mapOf(
            "nifty50" to "Nifty 50 TRI",
            "nifty500" to "Nifty 500 TRI",
            "niftyMidcap150" to "Nifty Midcap 150 TRI",
            "niftySmallcap250" to "Nifty Smallcap 250 TRI"
        )

        val indexResults = mutableListOf<BenchmarkSimulationResult>()
        for (idxKey in indexKeys) {
            val res = simulateActiveLotsForBenchmark(
                name = indexDisplayNames[idxKey] ?: idxKey,
                getPrice = { timeMs -> getIndexValueForDate(idxKey, timeMs) }
            )
            indexResults.add(res)
        }

        val quantResults = mutableListOf<BenchmarkSimulationResult>()
        for ((bName, bFile) in benchmarkFunds) {
            val navMap = loadNavDataFromFile(context, bName, bFile)
            val res = simulateActiveLotsForBenchmark(
                name = bName,
                getPrice = { timeMs -> getNavForDate(navMap, timeMs) }
            )
            quantResults.add(res)
        }

        val sortedQuantResults = quantResults.sortedBy { it.alpha }
        val finalResults = indexResults + sortedQuantResults
        cachedSimulationKey = cacheKey
        cachedSimulationResults = finalResults
        return finalResults
    }

    private fun simulateBenchmarkCashflows(
        name: String,
        portfolioEvents: List<PortfolioCashflowEvent>,
        latestValuationDate: Long,
        portfolioXirr: Double,
        navMap: TreeMap<Long, Double>? = null,
        getPrice: (Long) -> Double
    ): BenchmarkSimulationResult {
        val lots = mutableListOf<BenchmarkFifoLot>()
        val xirrCashflows = mutableListOf<Pair<Long, Double>>()
        var totalPurchases = 0.0
        var totalRedemptions = 0.0
        val firstNavDate = navMap?.firstKey()

        for (event in portfolioEvents) {
            val effectiveTimestamp = if (firstNavDate != null && event.timestamp < firstNavDate) firstNavDate else event.timestamp
            val price = getPrice(effectiveTimestamp)
            if (price <= 0.0) continue

            if (event.isInvestment) {
                val unitsBought = event.amount / price
                lots.add(BenchmarkFifoLot(effectiveTimestamp, unitsBought, price))
                xirrCashflows.add(effectiveTimestamp to -event.amount)
                totalPurchases += event.amount
            } else {
                // Withdrawal: exact FIFO lot deduction
                var targetWithdrawAmount = event.amount
                var unitsNeeded = targetWithdrawAmount / price
                var actualUnitsRedeemed = 0.0

                val remainingLots = mutableListOf<BenchmarkFifoLot>()
                for (lot in lots) {
                    if (unitsNeeded <= 1e-9) {
                        remainingLots.add(lot)
                        continue
                    }
                    val take = kotlin.math.min(lot.units, unitsNeeded)
                    val left = lot.units - take
                    unitsNeeded -= take
                    actualUnitsRedeemed += take
                    if (left > 1e-9) {
                        remainingLots.add(BenchmarkFifoLot(lot.timestamp, left, lot.costPerUnit))
                    }
                }
                lots.clear()
                lots.addAll(remainingLots)

                val actualCashWithdrawn = actualUnitsRedeemed * price
                xirrCashflows.add(effectiveTimestamp to actualCashWithdrawn)
                totalRedemptions += actualCashWithdrawn
            }
        }

        val finalPrice = getPrice(latestValuationDate)
        val remainingUnits = lots.sumOf { it.units }
        val finalSimulatedValue = remainingUnits * finalPrice

        val activeLotCashflows = mutableListOf<Pair<Long, Double>>()
        for (lot in lots) {
            val lotOutflow = -(lot.units * lot.costPerUnit)
            if (lotOutflow < 0) {
                activeLotCashflows.add(lot.timestamp to lotOutflow)
            }
        }
        if (finalSimulatedValue > 0.0) {
            activeLotCashflows.add(latestValuationDate to finalSimulatedValue)
        }

        val netInvested = lots.sumOf { it.units * it.costPerUnit }

        val simulatedXirr = if (activeLotCashflows.size >= 2 && activeLotCashflows.any { it.second < 0 } && activeLotCashflows.any { it.second > 0 }) {
            val rawXirr = ReturnsCalculations.trueXirr(activeLotCashflows)
            if (rawXirr.isNaN() || rawXirr.isInfinite() || rawXirr < -90.0 || rawXirr > 500.0) {
                calculateSimpleCagr(netInvested, finalSimulatedValue, activeLotCashflows.first().first, latestValuationDate)
            } else {
                rawXirr
            }
        } else {
            0.0
        }

        val alpha = portfolioXirr - simulatedXirr

        return BenchmarkSimulationResult(
            name = name,
            simulatedXirr = simulatedXirr,
            alpha = alpha,
            simulatedCurrentValue = finalSimulatedValue,
            totalInvested = netInvested
        )
    }

    fun computeBenchmarkTimelines(
        context: Context,
        benchmarkNames: Set<String>,
        timestamps: List<Long>,
        investedItems: List<FundUiItem>,
        allTxns: List<CasTransactionEntity>,
        startCal: Calendar,
        customNavMaps: Map<String, TreeMap<Long, Double>> = emptyMap()
    ): Map<String, List<Double>> {
        if (benchmarkNames.isEmpty() || timestamps.isEmpty()) return emptyMap()

        loadIndexDataIfNeeded(context)

        val portfolioEvents = extractPortfolioCashflows(investedItems, allTxns, startCal)
        val results = mutableMapOf<String, List<Double>>()

        for (name in benchmarkNames) {
            val isIndex = name.contains("Nifty 50 TRI", ignoreCase = true) ||
                          name.contains("Nifty 500", ignoreCase = true) ||
                          name.contains("Nifty Midcap 150", ignoreCase = true) ||
                          name.contains("Nifty Smallcap 250", ignoreCase = true)

            val idxKey = when {
                name.contains("Nifty 50 TRI", ignoreCase = true) -> "nifty50"
                name.contains("Nifty 500", ignoreCase = true) -> "nifty500"
                name.contains("Nifty Midcap 150", ignoreCase = true) -> "niftyMidcap150"
                name.contains("Nifty Smallcap 250", ignoreCase = true) -> "niftySmallcap250"
                else -> null
            }

            val quantFile = benchmarkFunds[name] ?: benchmarkFunds.entries.firstOrNull {
                val hasIncomePlus1 = name.contains("income plus", ignoreCase = true)
                val hasIncomePlus2 = it.key.contains("income plus", ignoreCase = true)
                hasIncomePlus1 == hasIncomePlus2 && (
                    name.contains(it.key.substringBefore(" -"), ignoreCase = true) ||
                    it.key.contains(name.substringBefore(" -"), ignoreCase = true)
                )
            }?.value

            val navMap = if (!isIndex && quantFile != null) {
                loadNavDataFromFile(context, name, quantFile)
            } else null

            val customMap = customNavMaps[name] ?: customNavMaps.entries.firstOrNull {
                it.key.contains(name, ignoreCase = true) || name.contains(it.key, ignoreCase = true)
            }?.value

            fun getPriceAt(timeMs: Long): Double {
                return if (idxKey != null) {
                    getIndexValueForDate(idxKey, timeMs)
                } else if (navMap != null) {
                    getNavForDate(navMap, timeMs)
                } else if (customMap != null && customMap.isNotEmpty()) {
                    customMap.floorEntry(timeMs)?.value ?: customMap.ceilingEntry(timeMs)?.value ?: 100.0
                } else {
                    100.0
                }
            }

            // Chronological FIFO accumulation matching each timestamp
            val timelineValues = ArrayList<Double>(timestamps.size)
            val simLots = mutableListOf<BenchmarkFifoLot>()
            var eventIndex = 0

            for (t in timestamps) {
                // Apply all portfolio investment/withdrawal events that happened on or before t
                while (eventIndex < portfolioEvents.size && portfolioEvents[eventIndex].timestamp <= t) {
                    val event = portfolioEvents[eventIndex]
                    val priceOnDate = getPriceAt(event.timestamp)
                    if (priceOnDate > 0.0) {
                        if (event.isInvestment) {
                            val unitsBought = event.amount / priceOnDate
                            simLots.add(BenchmarkFifoLot(event.timestamp, unitsBought, priceOnDate))
                        } else {
                            // Exact FIFO redemption of benchmark lots
                            var unitsNeeded = event.amount / priceOnDate
                            val nextLots = mutableListOf<BenchmarkFifoLot>()
                            for (lot in simLots) {
                                if (unitsNeeded <= 1e-9) {
                                    nextLots.add(lot)
                                    continue
                                }
                                val take = kotlin.math.min(lot.units, unitsNeeded)
                                val left = lot.units - take
                                unitsNeeded -= take
                                if (left > 1e-9) {
                                    nextLots.add(BenchmarkFifoLot(lot.timestamp, left, lot.costPerUnit))
                                }
                            }
                            simLots.clear()
                            simLots.addAll(nextLots)
                        }
                    }
                    eventIndex++
                }

                // Compute exact hypothetical benchmark portfolio market valuation at timestamp t
                val priceAtT = getPriceAt(t)
                val remainingUnitsAtT = simLots.sumOf { it.units }
                val dayBenchmarkVal = remainingUnitsAtT * priceAtT
                timelineValues.add(dayBenchmarkVal)
            }

            results[name] = timelineValues
        }

        return results
    }
}
