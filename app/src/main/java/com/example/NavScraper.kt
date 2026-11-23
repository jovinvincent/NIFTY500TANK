package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import kotlin.math.round

data class NavRecord(val dateStr: String, val nav: Double)

object NavCache {
    val cache = mutableMapOf<String, MutableList<NavRecord>>()
    private val timeTreeMaps = mutableMapOf<String, java.util.TreeMap<Long, Double>>()
    private val highestOptionCache = mutableMapOf<String, NavRecord>()
    private val parsedDateCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun parseDateToMillis(dateStr: String): Long {
        if (dateStr.isEmpty()) return 0L
        return parsedDateCache.getOrPut(dateStr) {
            val sdf1 = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
            val sdf2 = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            try {
                sdf1.parse(dateStr)?.time ?: sdf2.parse(dateStr)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    @Synchronized
    fun addAll(schemeName: String, records: List<NavRecord>) {
        if (!cache.containsKey(schemeName)) {
            cache[schemeName] = mutableListOf()
        }
        val existing = cache[schemeName]!!
        
        // Overwrite by date to prevent duplicates and keep latest value
        val existingMap = existing.associateBy { it.dateStr }.toMutableMap()
        for (rec in records) {
            existingMap[rec.dateStr] = rec
        }
        
        existing.clear()
        existing.addAll(existingMap.values)

        // Build sorted timeline index for O(log N) lookups without string parsing on UI frames
        val tree = java.util.TreeMap<Long, Double>()
        for (rec in existing) {
            val t = parseDateToMillis(rec.dateStr)
            if (t > 0L) {
                tree[t] = rec.nav
            }
        }
        timeTreeMaps[schemeName] = tree

        // Precompute highest option
        val highest = existing.maxWithOrNull(Comparator { r1, r2 ->
            val navCmp = r1.nav.compareTo(r2.nav)
            if (navCmp != 0) return@Comparator navCmp
            val d1 = parseDateToMillis(r1.dateStr)
            val d2 = parseDateToMillis(r2.dateStr)
            d2.compareTo(d1)
        })
        if (highest != null) {
            highestOptionCache[schemeName] = highest
        }
    }

    @Synchronized
    fun getRecords(schemeName: String): List<NavRecord> {
        return cache[schemeName]?.toList() ?: emptyList()
    }

    @Synchronized
    fun getPreviousNav(schemeName: String, currDateStr: String): Double? {
        val tree = timeTreeMaps[schemeName] ?: return null
        val currTime = parseDateToMillis(currDateStr)
        if (currTime <= 0L) return null
        return tree.lowerEntry(currTime)?.value
    }

    @Synchronized
    fun getHighestOption(schemeName: String): NavRecord? {
        return highestOptionCache[schemeName] ?: cache[schemeName]?.maxByOrNull { it.nav }
    }
}

object NavScraper {
    
    suspend fun fetchHistoricalNavs(repository: FundRepository, forceFullSync: Boolean = false) = withContext(Dispatchers.IO) {
        val dateToFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val toDateStr = dateToFormat.format(java.util.Date())

        val targetFunds = listOf(
            "qsif Equity Long-Short Fund - Direct - Growth",
            "qsif Equity Ex-Top 100 Long-Short Fund - Direct - Growth",
            "qsif Sector Rotation Long-Short Fund - Growth Option - Direct Plan",
            "qsif Active Asset Allocator Long-Short Fund - Growth Option - Direct Plan",
            "qsif Hybrid Long-Short Fund - Direct - Growth",
            "quant Income Plus Arbitrage Active FoF - Direct (G)"
        )

        coroutineScope {
            targetFunds.map { fundNameFull ->
                async(Dispatchers.IO) {
                    val isIncomePlus = fundNameFull.contains("Income Plus", ignoreCase = true) || fundNameFull.contains("Arbitrage Active FoF", ignoreCase = true)
                    val schemeNameBase = if (isIncomePlus) {
                        "Quant Income Plus Arbitrage Active FoF - Direct (G)"
                    } else {
                        fundNameFull
                            .replace("(?i)\\bqsif\\b".toRegex(), "")
                            .replace("(?i)\\bdirect\\b".toRegex(), "")
                            .replace("(?i)\\bgrowth\\b".toRegex(), "")
                            .replace("(?i)\\boption\\b".toRegex(), "")
                            .replace("(?i)\\bplan\\b".toRegex(), "")
                            .replace(Regex("[-–—]\\s*[-–—]"), "-")
                            .replace(Regex("\\s*[-–—]\\s*$"), "")
                            .replace(Regex("^\\s*[-–—]\\s*"), "")
                            .replace(Regex("\\s{2,}"), " ")
                            .trim()
                    }

                    // Load from DB if we don't have it in memory
                    val dbRecords = repository.getHistoricalNavs(schemeNameBase)
                    var fromDateStr = BuiltInNavLoader.LATEST_BUILTIN_NAV_DATE_ISO
                    
                    if (dbRecords.isNotEmpty()) {
                        val navRecords = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                        NavCache.addAll(schemeNameBase, navRecords)
                        
                        val sdf1 = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val sdf2 = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                        val latestRecord = dbRecords.maxByOrNull {
                            try {
                                sdf2.parse(it.dateStr)?.time ?: sdf1.parse(it.dateStr)?.time ?: 0L
                            } catch(e: Exception) { 0L }
                        }
                        if (latestRecord != null) {
                            try {
                                val latestTime = sdf2.parse(latestRecord.dateStr)?.time ?: sdf1.parse(latestRecord.dateStr)?.time ?: 0L
                                if (latestTime > 0L) {
                                    val diffDays = (System.currentTimeMillis() - latestTime) / (1000L * 60 * 60 * 24)
                                    val cal = Calendar.getInstance()
                                    val day = cal.get(Calendar.DAY_OF_WEEK)
                                    val isWeekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY || day == Calendar.MONDAY
                                    val maxAllowedAgeDays = if (isWeekend) 4 else 2
                                    if (!forceFullSync && diffDays <= maxAllowedAgeDays) {
                                        Log.d("NavScraper", "Skipping web fetch for $schemeNameBase; local data is up to date ($diffDays days old).")
                                        return@async
                                    }
                                    val baselineTime = sdf1.parse(BuiltInNavLoader.LATEST_BUILTIN_NAV_DATE_ISO)?.time ?: 0L
                                    val fetchStartTime = maxOf(latestTime - (2L * 24 * 60 * 60 * 1000L), baselineTime)
                                    fromDateStr = dateToFormat.format(java.util.Date(fetchStartTime))
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }

                    val records = mutableListOf<NavRecord>()

                    if (isIncomePlus) {
                        try {
                            val mfUrl = "https://api.mfapi.in/mf/154658"
                            val mfReq = okhttp3.Request.Builder()
                                .url(mfUrl)
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                            val mfResp = FastHttpClient.client.newCall(mfReq).execute()
                            if (mfResp.isSuccessful) {
                                val jsonStr = mfResp.body?.string() ?: ""
                                val jObj = org.json.JSONObject(jsonStr)
                                val dataArr = jObj.getJSONArray("data")
                                val sdfIn = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                                val sdfOut = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                                for (i in 0 until dataArr.length()) {
                                    val row = dataArr.getJSONObject(i)
                                    val dRaw = row.getString("date")
                                    val navVal = row.getDouble("nav")
                                    val dParsed = sdfIn.parse(dRaw)
                                    val dFormatted = if (dParsed != null) sdfOut.format(dParsed) else dRaw
                                    records.add(NavRecord(dFormatted, navVal))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("NavScraper", "Error fetching historical NAV for Income Plus", e)
                        }
                    } else {

                    try {
                        val session = Jsoup.newSession()
                        val getResponse = session.newRequest()
                            .url("https://www.qsif.com/nav/historical_nav")
                            .method(org.jsoup.Connection.Method.GET)
                            .header("User-Agent", "Mozilla/5.0")
                            .timeout(5000)
                            .execute()

                        val doc = getResponse.parse()
                        val viewState = doc.select("input[name=__VIEWSTATE]").`val`() ?: ""
                        val viewStateGen = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`() ?: ""
                        val eventValidation = doc.select("input[name=__EVENTVALIDATION]").`val`() ?: ""

                        val postResponse = session.newRequest()
                            .url("https://www.qsif.com/nav/historical_nav")
                            .method(org.jsoup.Connection.Method.POST)
                            .header("User-Agent", "Mozilla/5.0")
                            .timeout(5000)
                            .data("ctl00_ToolkitScriptManager1_HiddenField", "")
                            .data("__EVENTTARGET", "ctl00\$ContentPlaceHolder1\$BtnGo")
                            .data("__EVENTARGUMENT", "")
                            .data("__VIEWSTATE", viewState)
                            .data("__VIEWSTATEGENERATOR", viewStateGen)
                            .data("__VIEWSTATEENCRYPTED", "")
                            .data("__EVENTVALIDATION", eventValidation)
                            .data("ctl00\$ContentPlaceHolder1\$FromIds", fromDateStr)
                            .data("ctl00\$ContentPlaceHolder1\$ToIds", toDateStr)
                            .data("ctl00\$ContentPlaceHolder1\$txtschname", fundNameFull)
                            .execute()

                        var postDoc = postResponse.parse()

                        var pageCount = 1
                        while (true) {
                            val rows = postDoc.select("#notice tbody tr")

                            for (row in rows) {
                                val cols = row.select("td")
                                if (cols.size >= 3) {
                                    val navDateRaw = cols[0].text().trim()
                                    val navValueStr = cols[2].text().trim()
                                    val navVal = navValueStr.toDoubleOrNull()
                                    if (navVal != null && navDateRaw.isNotEmpty()) {
                                        records.add(NavRecord(navDateRaw, navVal))
                                    }
                                }
                            }

                            val nextLink = postDoc.select("a.aselect:containsOwn(Next), a:containsOwn(Next)")
                            val nextHref = nextLink.attr("href")
                            if (nextHref.isNullOrEmpty() || !nextHref.startsWith("javascript:__doPostBack")) {
                                break
                            }

                            val match = Regex("__doPostBack\\('(.*?)','(.*?)'\\)").find(nextHref)
                            if (match == null) {
                                break
                            }

                            val nEventTarget = match.groupValues[1]
                            val nEventArgument = match.groupValues[2]

                            val nextState = postDoc.select("input[name=__VIEWSTATE]").`val`() ?: ""
                            val nextGen = postDoc.select("input[name=__VIEWSTATEGENERATOR]").`val`() ?: ""
                            val nextValState = postDoc.select("input[name=__EVENTVALIDATION]").`val`() ?: ""

                            val nextResponse = session.newRequest()
                                .url("https://www.qsif.com/nav/historical_nav")
                                .method(org.jsoup.Connection.Method.POST)
                                .header("User-Agent", "Mozilla/5.0")
                                .timeout(5000)
                                .data("ctl00_ToolkitScriptManager1_HiddenField", "")
                                .data("__EVENTTARGET", nEventTarget)
                                .data("__EVENTARGUMENT", nEventArgument)
                                .data("__VIEWSTATE", nextState)
                                .data("__VIEWSTATEGENERATOR", nextGen)
                                .data("__VIEWSTATEENCRYPTED", "")
                                .data("__EVENTVALIDATION", nextValState)
                                .data("ctl00\$ContentPlaceHolder1\$FromIds", fromDateStr)
                                .data("ctl00\$ContentPlaceHolder1\$ToIds", toDateStr)
                                .data("ctl00\$ContentPlaceHolder1\$txtschname", fundNameFull)
                                .execute()

                            postDoc = nextResponse.parse()
                            pageCount++
                        }
                        
                        Log.d("NavScraper", "Fetched $pageCount pages for $schemeNameBase, records: ${records.size}")

                        if (records.isNotEmpty()) {
                            val historicalNavs = records.map {
                                HistoricalNav(schemeNameBase, it.dateStr, it.nav)
                            }
                            repository.insertHistoricalNavs(historicalNavs)

                            NavCache.addAll(schemeNameBase, records)
                            updateHighestNavInDb(repository, schemeNameBase)
                        } else {
                            // Fallback sync: ensure current date is populated using latest existing NAV record
                            val existingNavs = repository.getHistoricalNavs(schemeNameBase)
                            if (existingNavs.isNotEmpty()) {
                                val latest = existingNavs.maxByOrNull { it.dateStr }
                                if (latest != null) {
                                    val now = java.util.Date()
                                    val todayStr = SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(now)
                                    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
                                    // Check if today already exists
                                    val hasToday = existingNavs.any { it.dateStr == todayStr || it.dateStr == todayIso }
                                    if (!hasToday) {
                                        val todayNav = HistoricalNav(schemeNameBase, todayStr, latest.navValue)
                                        repository.insertHistoricalNavs(listOf(todayNav))
                                        NavCache.addAll(schemeNameBase, listOf(NavRecord(todayStr, latest.navValue)))
                                        updateHighestNavInDb(repository, schemeNameBase)
                                    }
                                }
                            }
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback sync on exception
                        try {
                            val existingNavs = repository.getHistoricalNavs(schemeNameBase)
                            if (existingNavs.isNotEmpty()) {
                                val latest = existingNavs.maxByOrNull { it.dateStr }
                                if (latest != null) {
                                    val todayStr = SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(java.util.Date())
                                    val hasToday = existingNavs.any { it.dateStr == todayStr }
                                    if (!hasToday) {
                                        val todayNav = HistoricalNav(schemeNameBase, todayStr, latest.navValue)
                                        repository.insertHistoricalNavs(listOf(todayNav))
                                        NavCache.addAll(schemeNameBase, listOf(NavRecord(todayStr, latest.navValue)))
                                        updateHighestNavInDb(repository, schemeNameBase)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (isIncomePlus && records.isNotEmpty()) {
                    val historicalNavs = records.map {
                        HistoricalNav(schemeNameBase, it.dateStr, it.nav)
                    }
                    repository.insertHistoricalNavs(historicalNavs)
                    NavCache.addAll(schemeNameBase, records)
                    updateHighestNavInDb(repository, schemeNameBase)
                }
            }
            }.awaitAll()

            // 1. Sync Quant Liquid NAV into local SQL
            try {
                QuantLiquidNavExtractor.syncQuantLiquidNavs(repository)
            } catch (e: Exception) {
                Log.e("NavScraper", "Error syncing Quant Liquid NAV: ${e.message}")
            }

            // 2. Incremental sync for all Benchmark & Quant Funds into local SQL
            try {
                QuantBenchmarkNavExtractor.syncAllBenchmarkNavs(repository)
            } catch (e: Exception) {
                Log.e("NavScraper", "Error syncing Quant benchmark funds: ${e.message}")
            }

            // 3. Incremental sync for Benchmark Indices (Nifty 50, 500, Midcap 150, Smallcap 250) into local SQL
            try {
                BenchmarkSyncManager.syncBenchmarkIndices(repository)
            } catch (e: Exception) {
                Log.e("NavScraper", "Error syncing benchmark indices: ${e.message}")
            }
        }
    }
    
    suspend fun updateHighestNavInDb(repository: FundRepository, schemeName: String) {
        val highest = NavCache.getHighestOption(schemeName)
        if (highest != null) {
            val roundedNav = round(highest.nav * 10000.0) / 10000.0
            
            // Convert to dd-MMM-yyyy if it's in yyyy-MM-dd
            var finalDate = highest.dateStr
            try {
                if (finalDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(finalDate)
                    if (parsed != null) {
                        finalDate = SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(parsed)
                    }
                }
            } catch (e: Exception) {}

            repository.updateHighestNavOnly(schemeName, roundedNav, finalDate)
            repository.updateFundHighest(schemeName, roundedNav, finalDate)
        }
    }
}

object QuantBenchmarkNavExtractor {
    private const val QUANT_HISTORICAL_URL = "https://www.quantmutual.com/nav/historical_nav"

    val BENCHMARK_SCHEMES = listOf(
        "Quant Liquid Plan - Direct (G)",
        "Quant Overnight Fund - Direct (G)",
        "Quant Multi Asset Allocation Fund - Direct (G)",
        "Quant Flexi Cap Fund - Direct (G)",
        "Quant Small Cap Fund - Direct (G)",
        "Quant Momentum Fund - Direct (G)",
        "Quant Business Cycle Fund - Direct (G)",
        "Quant Aggressive Hybrid Fund - Direct (G)",
        "Quant Dynamic Asset Allocation Fund - Direct (G)",
        "Quant Multi Cap Fund - Direct (G)",
        "Quant BFSI Fund - Direct (G)",
        "Quant Infrastructure Fund - Direct (G)",
        "Quant Healthcare Fund - Direct (G)",
        "Quant Mid Cap Fund - Direct (G)",
        "Quant Quantamental Fund - Direct (G)",
        "Quant Value Fund - Direct (G)",
        "Quant ESG Integration Strategy Fund - Direct (G)",
        "Quant Arbitrage Fund - Direct (G)",
        "Quant Equity Savings Fund - Direct (G)",
        "Quant Income Plus Arbitrage Active FoF - Direct (G)"
    )

    private val MFAPI_SCHEME_CODES = mapOf(
        "Quant Income Plus Arbitrage Active FoF - Direct (G)" to 154658,
        "Quant Arbitrage Fund - Direct (G)" to 153098
    )

    suspend fun syncAllBenchmarkNavs(repository: FundRepository) = withContext(Dispatchers.IO) {
        val dateToFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = java.util.Date()
        val toDateStr = dateToFormat.format(today)

        try {
            val session = Jsoup.newSession()
            val getResponse = session.newRequest()
                .url(QUANT_HISTORICAL_URL)
                .method(org.jsoup.Connection.Method.GET)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(8000)
                .execute()

            val doc = getResponse.parse()
            var viewState = doc.select("input[name=__VIEWSTATE]").`val`() ?: ""
            var viewStateGen = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`() ?: ""
            var eventValidation = doc.select("input[name=__EVENTVALIDATION]").`val`() ?: ""

            for (scheme in BENCHMARK_SCHEMES) {
                try {
                    // Check latest NAV in DB for this scheme to do true incremental fetch
                    val dbRecords = repository.getHistoricalNavs(scheme)
                    var fromDateStr = BuiltInNavLoader.LATEST_BUILTIN_NAV_DATE
                    if (dbRecords.isNotEmpty()) {
                        val latest = dbRecords.maxByOrNull {
                            try {
                                dateToFormat.parse(it.dateStr)?.time ?: isoFormat.parse(it.dateStr)?.time ?: 0L
                            } catch (e: Exception) { 0L }
                        }
                        if (latest != null) {
                            try {
                                val t = dateToFormat.parse(latest.dateStr) ?: isoFormat.parse(latest.dateStr)
                                if (t != null) {
                                    val startT = t.time - (24L * 60 * 60 * 1000L)
                                    fromDateStr = dateToFormat.format(java.util.Date(startT))
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    if (viewState.isEmpty()) {
                        val freshDoc = session.newRequest()
                            .url(QUANT_HISTORICAL_URL)
                            .method(org.jsoup.Connection.Method.GET)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .timeout(8000)
                            .execute()
                            .parse()
                        viewState = freshDoc.select("input[name=__VIEWSTATE]").`val`() ?: ""
                        viewStateGen = freshDoc.select("input[name=__VIEWSTATEGENERATOR]").`val`() ?: ""
                        eventValidation = freshDoc.select("input[name=__EVENTVALIDATION]").`val`() ?: ""
                    }

                    val postResponse = session.newRequest()
                        .url(QUANT_HISTORICAL_URL)
                        .method(org.jsoup.Connection.Method.POST)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .timeout(8000)
                        .data("ctl00_ToolkitScriptManager1_HiddenField", "")
                        .data("__EVENTTARGET", "ctl00\$ContentPlaceHolder1\$BtnGo")
                        .data("__EVENTARGUMENT", "")
                        .data("__VIEWSTATE", viewState)
                        .data("__VIEWSTATEGENERATOR", viewStateGen)
                        .data("__VIEWSTATEENCRYPTED", "")
                        .data("__EVENTVALIDATION", eventValidation)
                        .data("ctl00\$ContentPlaceHolder1\$FromIds", fromDateStr)
                        .data("ctl00\$ContentPlaceHolder1\$ToIds", toDateStr)
                        .data("ctl00\$ContentPlaceHolder1\$txtschname", scheme)
                        .execute()

                    val postDoc = postResponse.parse()

                    val nextVs = postDoc.select("input[name=__VIEWSTATE]").`val`()
                    if (!nextVs.isNullOrEmpty()) viewState = nextVs
                    val nextVsg = postDoc.select("input[name=__VIEWSTATEGENERATOR]").`val`()
                    if (!nextVsg.isNullOrEmpty()) viewStateGen = nextVsg
                    val nextEv = postDoc.select("input[name=__EVENTVALIDATION]").`val`()
                    if (!nextEv.isNullOrEmpty()) eventValidation = nextEv

                    val rows = postDoc.select("#notice tbody tr")
                    val records = mutableListOf<HistoricalNav>()

                    for (row in rows) {
                        val cols = row.select("td")
                        if (cols.size >= 3) {
                            val sName = cols[1].text().trim()
                            val isGrowth = !sName.contains("IDCW", ignoreCase = true) &&
                                           !sName.contains("Payout", ignoreCase = true) &&
                                           !sName.contains("Reinvest", ignoreCase = true) &&
                                           !sName.contains("Sweep", ignoreCase = true) &&
                                           !sName.contains("Div", ignoreCase = true)
                            if (!isGrowth) continue

                            val dStr = cols[0].text().trim()
                            val navVal = cols[2].text().trim().toDoubleOrNull()
                            if (navVal != null && navVal > 0.0 && dStr.isNotEmpty()) {
                                records.add(HistoricalNav(scheme, dStr, navVal))
                                try {
                                    val parsedDate = dateToFormat.parse(dStr) ?: isoFormat.parse(dStr)
                                    if (parsedDate != null) {
                                        if (scheme.contains("Liquid", ignoreCase = true)) {
                                            IndexBenchmarkSimulator.updateQuantLiquidNav(parsedDate.time, navVal)
                                        }
                                        IndexBenchmarkSimulator.updateBenchmarkNav(scheme, parsedDate.time, navVal)
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }

                    if (records.isEmpty()) {
                        val mfapiCode = MFAPI_SCHEME_CODES[scheme]
                        if (mfapiCode != null) {
                            try {
                                val url = "https://api.mfapi.in/mf/$mfapiCode"
                                val jsonStr = Jsoup.connect(url).ignoreContentType(true).timeout(6000).execute().body()
                                val obj = org.json.JSONObject(jsonStr)
                                val dataArr = obj.optJSONArray("data")
                                if (dataArr != null) {
                                    val parserIn = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                                    val parserOut = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                                    for (i in 0 until minOf(dataArr.length(), 60)) {
                                        val item = dataArr.getJSONObject(i)
                                        val d = item.optString("date")
                                        val n = item.optDouble("nav", 0.0)
                                        if (d.isNotEmpty() && n > 0.0) {
                                            val pDate = try { parserIn.parse(d) } catch (e: Exception) { null }
                                            if (pDate != null) {
                                                records.add(HistoricalNav(scheme, parserOut.format(pDate), n))
                                                IndexBenchmarkSimulator.updateBenchmarkNav(scheme, pDate.time, n)
                                            }
                                        }
                                    }
                                }
                            } catch (ex: Exception) {
                                Log.w("QuantBenchmarkNavExtractor", "MFAPI fallback failed for $scheme: ${ex.message}")
                            }
                        }
                    }

                    if (records.isNotEmpty()) {
                        repository.insertHistoricalNavs(records)
                        Log.d("QuantBenchmarkNavExtractor", "Synced ${records.size} NAVs for $scheme into SQLite")
                    }
                } catch (e: Exception) {
                    Log.e("QuantBenchmarkNavExtractor", "Error syncing NAV for $scheme: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("QuantBenchmarkNavExtractor", "Failed initial GET to $QUANT_HISTORICAL_URL", e)
        }
    }
}

object QuantLiquidNavExtractor {
    private const val QUANT_HISTORICAL_URL = "https://www.quantmutual.com/nav/historical_nav"
    private const val MFAPI_SCHEME_URL = "https://api.mfapi.in/mf/120837"
    private const val MFAPI_LATEST_URL = "https://api.mfapi.in/mf/120837/latest"
    const val SCHEME_NAME_FULL = "Quant Liquid Plan - Direct (G)"

    /**
     * Ultra-fast (< 500ms) query for latest Quant Liquid NAV
     */
    suspend fun fetchLatestNavFast(): List<NavRecord> = withContext(Dispatchers.IO) {
        val records = mutableListOf<NavRecord>()
        try {
            val jsonStr = Jsoup.connect(MFAPI_LATEST_URL)
                .ignoreContentType(true)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(5000)
                .execute()
                .body()

            val json = org.json.JSONObject(jsonStr)
            val dataArr = json.optJSONArray("data")
            if (dataArr != null && dataArr.length() > 0) {
                val parserIn = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                val parserOut = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    val dateRaw = item.optString("date")
                    val navVal = item.optDouble("nav", 0.0)
                    if (dateRaw.isNotEmpty() && navVal > 0.0) {
                        val parsedDate = try { parserIn.parse(dateRaw) } catch (e: Exception) { null }
                        if (parsedDate != null) {
                            records.add(NavRecord(parserOut.format(parsedDate), navVal))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("QuantLiquidExtractor", "Fast latest NAV query note: ${e.message}")
        }
        records
    }

    /**
     * Extracts NAV directly for a specific required date without pagination
     * by submitting exact From/To dates to the ASP.NET form.
     */
    suspend fun extractNavForRequiredDate(dateStr: String): NavRecord? = withContext(Dispatchers.IO) {
        try {
            val session = Jsoup.newSession()
            val getResponse = session.newRequest()
                .url(QUANT_HISTORICAL_URL)
                .method(org.jsoup.Connection.Method.GET)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(8000)
                .execute()

            val doc = getResponse.parse()
            val viewState = doc.select("input[name=__VIEWSTATE]").`val`() ?: ""
            val viewStateGen = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`() ?: ""
            val eventValidation = doc.select("input[name=__EVENTVALIDATION]").`val`() ?: ""

            val postResponse = session.newRequest()
                .url(QUANT_HISTORICAL_URL)
                .method(org.jsoup.Connection.Method.POST)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(8000)
                .data("ctl00_ToolkitScriptManager1_HiddenField", "")
                .data("__EVENTTARGET", "ctl00\$ContentPlaceHolder1\$BtnGo")
                .data("__EVENTARGUMENT", "")
                .data("__VIEWSTATE", viewState)
                .data("__VIEWSTATEGENERATOR", viewStateGen)
                .data("__VIEWSTATEENCRYPTED", "")
                .data("__EVENTVALIDATION", eventValidation)
                .data("ctl00\$ContentPlaceHolder1\$FromIds", dateStr)
                .data("ctl00\$ContentPlaceHolder1\$ToIds", dateStr)
                .data("ctl00\$ContentPlaceHolder1\$txtschname", SCHEME_NAME_FULL)
                .execute()

            val postDoc = postResponse.parse()
            val rows = postDoc.select("#notice tbody tr")
            for (row in rows) {
                val cols = row.select("td")
                if (cols.size >= 3) {
                    val sName = cols[1].text().trim()
                    if (sName.contains("Liquid", ignoreCase = true) &&
                        sName.contains("Direct", ignoreCase = true) &&
                        (sName.contains("(G)", ignoreCase = true) || sName.contains("Growth", ignoreCase = true))
                    ) {
                        val d = cols[0].text().trim()
                        val navVal = cols[2].text().trim().toDoubleOrNull()
                        if (navVal != null && navVal > 0.0) {
                            return@withContext NavRecord(d, navVal)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("QuantLiquidExtractor", "Failed direct date query for $dateStr: ${e.message}")
        }
        null
    }

    /**
     * Extracts historical NAVs without pagination inserting required dates
     * via single JSON payload directly from official AMFI/MFAPI endpoint.
     */
    suspend fun extractHistoricalNavsWithoutPagination(
        fromDate: String = "01-Aug-2022",
        toDate: String = ""
    ): List<NavRecord> = withContext(Dispatchers.IO) {
        val records = mutableListOf<NavRecord>()
        val effectiveToDate = if (toDate.isNotBlank()) toDate else SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(java.util.Date())

        // 1. Fetch latest NAV immediately in under 500ms
        val fastLatest = fetchLatestNavFast()
        records.addAll(fastLatest)

        // 2. Fetch full historical series with generous timeout and fallback
        try {
            val jsonStr = Jsoup.connect(MFAPI_SCHEME_URL)
                .ignoreContentType(true)
                .maxBodySize(0)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(20000)
                .execute()
                .body()

            val json = org.json.JSONObject(jsonStr)
            val dataArr = json.optJSONArray("data")
            if (dataArr != null) {
                val parserIn = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                val parserOut = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                val sdfFrom = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                val fromTime = try { sdfFrom.parse(fromDate)?.time } catch (e: Exception) { null } ?: 0L
                val toTime = try { sdfFrom.parse(effectiveToDate)?.time } catch (e: Exception) { null } ?: Long.MAX_VALUE

                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    val dateRaw = item.optString("date")
                    val navVal = item.optDouble("nav", 0.0)
                    if (dateRaw.isNotEmpty() && navVal > 0.0) {
                        val parsedDate = try { parserIn.parse(dateRaw) } catch (e: Exception) { null }
                        if (parsedDate != null && parsedDate.time in fromTime..toTime) {
                            val formattedDate = parserOut.format(parsedDate)
                            if (records.none { it.dateStr == formattedDate }) {
                                records.add(NavRecord(formattedDate, navVal))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("QuantLiquidExtractor", "Full history query timed out (${e.message}), retaining latest available NAV records (${records.size})")
        }
        records
    }

    /**
     * Syncs Quant Liquid Plan - Direct (G) historical NAVs into Room database & Simulator.
     */
    suspend fun syncQuantLiquidNavs(repository: FundRepository) = withContext(Dispatchers.IO) {
        try {
            val dbRecords = repository.getHistoricalNavs(SCHEME_NAME_FULL)
            val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
            val latest = dbRecords.maxByOrNull {
                try { sdf.parse(it.dateStr)?.time ?: 0L } catch (e: Exception) { 0L }
            }
            val fromDate = latest?.dateStr ?: BuiltInNavLoader.LATEST_BUILTIN_NAV_DATE
            val toDate = sdf.format(java.util.Date())
            val records = extractHistoricalNavsWithoutPagination(fromDate = fromDate, toDate = toDate)
            if (records.isNotEmpty()) {
                val entities = records.map {
                    HistoricalNav(SCHEME_NAME_FULL, it.dateStr, it.nav)
                }
                repository.insertHistoricalNavs(entities)
                
                val parser = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                records.forEach { r ->
                    try {
                        val d = parser.parse(r.dateStr)
                        if (d != null) {
                            IndexBenchmarkSimulator.updateQuantLiquidNav(d.time, r.nav)
                        }
                    } catch (e: Exception) {}
                }
                Log.d("QuantLiquidExtractor", "Synced ${records.size} Quant Liquid NAVs into database.")
            }
        } catch (e: Exception) {
            Log.w("QuantLiquidExtractor", "Note on Quant Liquid NAV sync: ${e.message}")
        }
    }
}
