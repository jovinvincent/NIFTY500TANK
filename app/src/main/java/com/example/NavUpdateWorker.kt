package com.example

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class NavUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        fun initialize(context: Context) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
            val now = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 19)
            cal.set(Calendar.MINUTE, 30)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = cal.timeInMillis - now

            val workRequest = OneTimeWorkRequestBuilder<NavUpdateWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    5, TimeUnit.MINUTES
                )
                .addTag("NavUpdateWorker")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("DailyNavUpdate", ExistingWorkPolicy.KEEP, workRequest)
        }

        fun scheduleNext(context: Context) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
            val now = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 19)
            cal.set(Calendar.MINUTE, 30)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = cal.timeInMillis - now

            val workRequest = OneTimeWorkRequestBuilder<NavUpdateWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    5, TimeUnit.MINUTES
                )
                .addTag("NavUpdateWorker")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("DailyNavUpdate", ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun scheduleRetry(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<NavUpdateWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .addTag("NavUpdateWorker")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("DailyNavUpdate", ExistingWorkPolicy.REPLACE, workRequest)
        }
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NavUpdateWorker", "Starting background NAV array fetch...")
                
                val repository = FundRepository(AppDatabase.getDatabase(appContext).fundDao())
                val oldFunds = try { repository.allFunds.first() } catch (e: Exception) { emptyList() }
                val oldDate = oldFunds.firstOrNull()?.navDate ?: ""
                
                val fetchedFunds = fetchFundsFromWeb()
                
                if (fetchedFunds.isEmpty()) {
                    Log.d("NavUpdateWorker", "No funds fetched. Checking time limit...")
                    if (isPastTimeLimit()) {
                        scheduleNext(appContext)
                    } else {
                        scheduleRetry(appContext)
                    }
                    return@withContext Result.success()
                }
                
                val combinedList = fetchedFunds.toMutableList()
                val fetchedSchemeNames = fetchedFunds.map { it.schemeName }.toSet()
                oldFunds.forEach { oldFund ->
                    if (!fetchedSchemeNames.contains(oldFund.schemeName)) {
                        combinedList.add(oldFund)
                    }
                }
                
                val combinedSchemeNames = combinedList.map { it.schemeName }.toSet()
                val targetFunds = listOf(
                    "qsif Equity Long-Short Fund - Direct - Growth",
                    "qsif Equity Ex-Top 100 Long-Short Fund - Direct - Growth",
                    "qsif Sector Rotation Long-Short Fund - Growth Option - Direct Plan",
                    "qsif Active Asset Allocator Long-Short Fund - Growth Option - Direct Plan",
                    "qsif Hybrid Long-Short Fund - Direct - Growth",
                    "quant Income Plus Arbitrage Active FoF - Direct (G)"
                )
                
                targetFunds.forEach { fullRawName ->
                    val cleanedName = cleanSchemeName(fullRawName)
                    if (!combinedSchemeNames.contains(cleanedName)) {
                        val allHistNavs = repository.getAllHistoricalNavsDirect()
                        val myHist = allHistNavs.filter { it.schemeName == cleanedName }
                        if (myHist.isNotEmpty()) {
                            val sdf = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.US)
                            val latestHist = myHist.maxByOrNull {
                                try {
                                    sdf.parse(it.dateStr)?.time ?: 0L
                                } catch(e: Exception) { 0L }
                            }
                            if (latestHist != null) {
                                val fallbackFund = FundData(
                                    schemeName = cleanedName,
                                    rawSchemeName = fullRawName,
                                    navValue = latestHist.navValue,
                                    pctChange = 0.0,
                                    pctChangeString = "0.00",
                                    navDate = latestHist.dateStr,
                                    option = "Growth",
                                    fetchedAtTimestamp = System.currentTimeMillis(),
                                    previousDayNav = latestHist.navValue
                                )
                                combinedList.add(fallbackFund)
                            }
                        }
                    }
                }

                val sortedFundsToSave = combinedList.sortedBy { getSchemeOrderIndex(it.schemeName) }
                
                val newDate = fetchedFunds.firstOrNull()?.navDate ?: ""
                
                repository.saveFunds(sortedFundsToSave)
                
                val historicalNavs = fetchedFunds.map { fund ->
                    HistoricalNav(
                        schemeName = fund.schemeName,
                        dateStr = fund.navDate,
                        navValue = fund.navValue
                    )
                }
                repository.insertHistoricalNavs(historicalNavs)
                
                Log.d("NavUpdateWorker", "Successfully saved funds to DB.")
                
                // If it successfully fetched but the date didn't change, we might want to keep retrying 
                // until it actually updates to a new date.
                // The requirement is: "stop when the NAV date is updated to the live date"
                val todayDf = SimpleDateFormat("dd-MMM-yyyy", Locale.US).apply { 
                    timeZone = TimeZone.getTimeZone("Asia/Kolkata") 
                }
                val todayStr = todayDf.format(Date())
                
                // Perform daily auto backup only once per day
                try {
                    val syncPrefs = appContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    val lastBackupDate = syncPrefs.getString("last_backup_date", "") ?: ""
                    if (lastBackupDate != todayStr) {
                        val jsonString = generateBackupJsonStringDirect(appContext, repository)
                        if (jsonString != null) {
                            saveBackupAndRotateDirect(appContext, jsonString)
                            Log.d("NavUpdateWorker", "Daily auto backup completed successfully.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NavUpdateWorker", "Daily auto backup failed", e)
                }

                // If newDate is NOT equal to oldDate, it updated.
                // Or if newDate IS today's date, it's definitely the live date.
                if (newDate != oldDate || newDate == todayStr) {
                    Log.d("NavUpdateWorker", "NAV date updated to $newDate. Fetching historical delta...")
                    try {
                        NavScraper.fetchHistoricalNavs(repository)
                    } catch (e: Exception) {
                        Log.e("NavUpdateWorker", "Failed to fetch historical delta", e)
                    }
                    Log.d("NavUpdateWorker", "Finished updating historical navs. Stop retrying.")
                    
                    try {
                        BenchmarkSyncManager.syncAllBenchmarks(appContext, force = true)
                    } catch (e: Exception) {
                        Log.e("NavUpdateWorker", "Failed syncing benchmarks", e)
                    }

                    scheduleNext(appContext)
                    return@withContext Result.success() // Data updated, stop!
                } else {
                    // Check if we are past 11:30 PM, if so stop retrying
                    if (isPastTimeLimit()) {
                        Log.d("NavUpdateWorker", "Past 11:30 PM, stopping retries for today.")
                        scheduleNext(appContext)
                        return@withContext Result.success()
                    }
                    
                    Log.d("NavUpdateWorker", "NAV date ($newDate) same as old ($oldDate) and not today. Retrying in 5 mins...")
                    scheduleRetry(appContext)
                    return@withContext Result.success()
                }

            } catch (e: Exception) {
                Log.e("NavUpdateWorker", "Error in sync", e)

                if (isPastTimeLimit()) {
                    scheduleNext(appContext)
                } else {
                    scheduleRetry(appContext)
                }
                return@withContext Result.success()
            }
        }
    }

    private fun isPastTimeLimit(): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        return (cal.get(Calendar.HOUR_OF_DAY) == 23 && cal.get(Calendar.MINUTE) >= 30) || (cal.get(Calendar.HOUR_OF_DAY) < 19)
    }
    
    private fun fetchFundsFromWeb(): List<FundData> {
        val fetchedAt = System.currentTimeMillis()
        val urlsToTry = listOf(
            "https://www.qsif.com/NAV/latestnav",
            "https://qsif.com/NAV/latestnav",
            "http://www.qsif.com/NAV/latestnav",
            "http://qsif.com/NAV/latestnav"
        )
        
        var lastException: Exception? = null
        var responseString = ""
        var successfulUrlStr = ""
        
        for (targetUrl in urlsToTry) {
            try {
                 val client = OkHttpClient.Builder()
                    .cookieJar(MemoryCookieJar())
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.isNotEmpty()) {
                        responseString = bodyStr
                        successfulUrlStr = targetUrl
                        break
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        
        if (responseString.isEmpty()) {
            throw lastException ?: Exception("Connection timeout.")
        }
        
        var currentDoc = Jsoup.parse(responseString)
        val allRawRows = mutableListOf<ParsedRow>()
        allRawRows.addAll(parseTable(currentDoc))

        // Fetch latest NAV for Quant Income Plus Arbitrage Active FoF from https://www.quantmutual.com/NAV/latestnav
        try {
            val quantClient = OkHttpClient.Builder()
                .cookieJar(MemoryCookieJar())
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val quantRequest = Request.Builder()
                .url("https://www.quantmutual.com/NAV/latestnav")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val quantResponse = quantClient.newCall(quantRequest).execute()
            if (quantResponse.isSuccessful) {
                val quantBody = quantResponse.body?.string() ?: ""
                if (quantBody.isNotEmpty()) {
                    val quantDoc = Jsoup.parse(quantBody)
                    allRawRows.addAll(parseTable(quantDoc))
                }
            }
        } catch (e: Exception) {
            Log.e("NavUpdateWorker", "Failed fetching quantmutual latestnav", e)
        }
        
        var currentPage = 1
        while (currentPage < 20) {
            val links = currentDoc.select("#ctl00_ContentPlaceHolder1_nav_sch a").takeIf { it.isNotEmpty() } ?: currentDoc.select("a[href*=__doPostBack]")
            var nextPageTarget: String? = null
            var nextPageArgument: String = ""
            
            val targetPageStr = (currentPage + 1).toString()
            for (link in links) {
                val text = link.text().trim()
                val href = link.attr("href")
                if (text == targetPageStr) {
                    val pgRegex = Regex("""__doPostBack\(\s*'([^']*)'\s*(?:,\s*'([^']*)')?\s*\)""")
                    val match = pgRegex.find(href)
                    if (match != null) {
                        nextPageTarget = match.groupValues[1]
                        nextPageArgument = match.groups[2]?.value ?: ""
                        break
                    }
                }
            }
            
            if (nextPageTarget == null) break
            
            val form = currentDoc.getElementById("aspnetForm") ?: currentDoc.select("form").first()
            val params = mutableMapOf<String, String>()
            if (form != null) {
                val inputs = form.select("input")
                for (input in inputs) {
                    val name = input.attr("name")
                    val valStr = input.attr("value")
                    if (name.isNotEmpty() && input.attr("type") != "submit") {
                        params[name] = valStr
                    }
                }
            }
            
            params["__EVENTTARGET"] = nextPageTarget
            params["__EVENTARGUMENT"] = nextPageArgument
            
            val formBodyBuilder = FormBody.Builder()
            for ((key, value) in params) {
                formBodyBuilder.add(key, value)
            }
            
            val postRequest = Request.Builder()
                .url(successfulUrlStr)
                .post(formBodyBuilder.build())
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", successfulUrlStr)
                .build()
            
            val clientPost = OkHttpClient.Builder()
                .cookieJar(MemoryCookieJar())
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val nextPageHtml = clientPost.newCall(postRequest).execute().body?.string() ?: ""
            if (nextPageHtml.isEmpty()) break
            
            currentDoc = Jsoup.parse(nextPageHtml)
            val pageRows = parseTable(currentDoc)
            if (pageRows.isEmpty()) break
            
            allRawRows.addAll(pageRows)
            currentPage++
        }
        
        val filtered = allRawRows.filter { row ->
            val s = row.scheme.lowercase(Locale.US)
            val opt = row.option.trim().lowercase(Locale.US)
            s.contains("direct") && opt == "growth"
        }.map { row ->
            val cleanedName = cleanSchemeName(row.scheme)
            val navVal = row.nav.toDoubleOrNull() ?: 0.0
            val pctChgVal = row.pctChange.replace("+", "").replace("%", "").trim().toDoubleOrNull() ?: 0.0
            
            FundData(
                schemeName = cleanedName,
                rawSchemeName = row.scheme,
                navValue = navVal,
                pctChange = pctChgVal,
                pctChangeString = row.pctChange,
                navDate = row.date,
                option = row.option,
                fetchedAtTimestamp = fetchedAt
            )
        }.distinctBy { it.schemeName }
        
        return filtered.sortedBy { getSchemeOrderIndex(it.schemeName) }
    }

    private fun parseTable(doc: Document): List<ParsedRow> {
        val results = mutableListOf<ParsedRow>()
        val table = doc.getElementById("notice") ?: return results
        val rows = table.getElementsByTag("tr")
        for (row in rows) {
            val cols = row.getElementsByTag("td")
            if (cols.size < 5) continue
            val cells = cols.map { it.text().trim() }
            if (cells[0].equals("Date", ignoreCase = true)) continue
            
            results.add(
                ParsedRow(
                    date = cells[0],
                    scheme = cells[1],
                    option = cells[2],
                    nav = cells[3],
                    pctChange = cells[4]
                )
            )
        }
        return results
    }

    private fun cleanSchemeName(name: String): String {
        val lower = name.lowercase(Locale.US)
        if (lower.contains("income plus") || lower.contains("arbitrage active fof") || lower.contains("arbitrage active")) {
            return "Quant Income Plus Arbitrage Active FoF - Direct (G)"
        }
        return name.replace("qsif ", "", ignoreCase = true)
            .replace(" - direct plan - growth option", "", ignoreCase = true)
            .replace(" - growth option - direct plan", "", ignoreCase = true)
            .replace(" - direct - growth", "", ignoreCase = true)
            .replace("- direct plan", "", ignoreCase = true)
            .replace("growth option", "", ignoreCase = true)
            .replace("direct", "", ignoreCase = true)
            .trim(' ', '-')
    }

    private fun getSchemeOrderIndex(schemeName: String): Int {
        val name = schemeName.lowercase(Locale.US)
        return when {
            name.contains("income plus") || name.contains("arbitrage active fof") || name.contains("arbitrage active") -> 5
            name.contains("equity ex-top") || name.contains("ex-top") -> 1
            name.contains("equity long-short") || (name.contains("equity") && !name.contains("quant")) -> 0
            name.contains("sector rotation") -> 2
            name.contains("active asset allocator") || name.contains("active asset") -> 3
            name.contains("hybrid") -> 4
            else -> 100
        }
    }
}
