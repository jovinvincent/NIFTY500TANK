package com.example

import android.content.Context
import android.util.JsonReader
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

object BuiltInNavLoader {
    const val LATEST_BUILTIN_NAV_DATE = "31-Oct-2026"
    const val LATEST_BUILTIN_NAV_DATE_ISO = "2026-10-31"

    private fun isoToDisplayDate(iso: String): String {
        return try {
            val y = iso.substring(0, 4)
            val m = iso.substring(5, 7)
            val d = iso.substring(8, 10)
            val monthStr = when (m) {
                "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
                "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
                "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
                else -> m
            }
            "$d-$monthStr-$y"
        } catch (e: Exception) {
            iso
        }
    }

    suspend fun preloadIfNeeded(context: Context, repository: FundRepository, force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val existing = repository.getAllHistoricalNavsDirect()
            val expectedFunds = listOf(
                "Equity Long-Short Fund",
                "Equity Ex-Top 100 Long-Short Fund",
                "Sector Rotation Long-Short Fund",
                "Active Asset Allocator Long-Short Fund",
                "Hybrid Long-Short Fund",
                "Nifty 50 TRI",
                "Quant Liquid Plan - Direct (G)",
                "Quant Income Plus Arbitrage Active FoF - Direct (G)"
            )
            val existingFunds = existing.map { it.schemeName }.toSet()
            val isMissingAnyFund = expectedFunds.any { !existingFunds.contains(it) }
            val hasLatestBuiltin = existing.any { 
                it.dateStr == LATEST_BUILTIN_NAV_DATE || 
                it.dateStr == "30-Oct-2026" || 
                it.dateStr == "2026-10-31" || 
                it.dateStr == "2026-10-30" || 
                it.dateStr == "01-Nov-2026" || 
                it.dateStr == "2026-11-01" 
            }

            // If empty, missing any standard fund, missing latest baseline date, or force reload
            if (existing.size < 5000 || isMissingAnyFund || !hasLatestBuiltin || force) {
                val records = loadFromAssets(context)
                if (records.isNotEmpty()) {
                    // Chunk into batches of 1000 for SQLite safety
                    records.chunked(1000).forEach { chunk ->
                        repository.insertHistoricalNavs(chunk)
                    }
                    Log.d("BuiltInNavLoader", "Preloaded ${records.size} historical NAV records from assets into database.")
                    val updated = repository.getAllHistoricalNavsDirect()
                    PortfolioDataCache.updateHistoricalNavs(updated)
                }
            } else {
                // Ensure cache is populated
                PortfolioDataCache.updateHistoricalNavs(existing)
            }
        } catch (e: Exception) {
            Log.e("BuiltInNavLoader", "Error preloading built-in historical NAVs", e)
        }
    }

    fun loadFromAssets(context: Context): List<HistoricalNav> {
        val list = ArrayList<HistoricalNav>(50000)

        // 1. Portfolio Long-Short Funds from historical_navs.json
        try {
            context.assets.open("historical_navs.json").use { inputStream ->
                JsonReader(inputStream.bufferedReader()).use { reader ->
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var scheme = ""
                        var date = ""
                        var nav = 0.0
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "scheme" -> scheme = reader.nextString()
                                "date" -> date = reader.nextString()
                                "nav" -> nav = reader.nextDouble()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (scheme.isNotEmpty() && date.isNotEmpty() && nav > 0.0) {
                            list.add(HistoricalNav(scheme, date, nav))
                        }
                    }
                    reader.endArray()
                }
            }
        } catch (e: Exception) {
            Log.e("BuiltInNavLoader", "Failed reading historical_navs.json", e)
        }

        // 2. Portfolio era Benchmark Indices from index_historical.json (2024 to 2026)
        try {
            context.assets.open("index_historical.json").use { inputStream ->
                JsonReader(inputStream.bufferedReader()).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (key.length == 10 && key[4] == '-' && key[7] == '-' && key >= "2024-01-01") {
                            val displayDate = isoToDisplayDate(key)
                            var n50 = 0.0
                            var n500 = 0.0
                            var nMid = 0.0
                            var nSmall = 0.0
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
                            if (n50 > 0.0) list.add(HistoricalNav("Nifty 50 TRI", displayDate, n50))
                            if (n500 > 0.0) list.add(HistoricalNav("Nifty 500 TRI", displayDate, n500))
                            if (nMid > 0.0) list.add(HistoricalNav("Nifty Midcap 150 TRI", displayDate, nMid))
                            if (nSmall > 0.0) list.add(HistoricalNav("Nifty Smallcap 250 TRI", displayDate, nSmall))
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
        } catch (e: Exception) {
            Log.e("BuiltInNavLoader", "Failed reading index_historical.json", e)
        }

        // 3. Benchmark & Quant Funds from quant_*.json (2022 to 2026)
        for ((schemeName, fileName) in IndexBenchmarkSimulator.benchmarkFunds) {
            try {
                context.assets.open(fileName).use { inputStream ->
                    JsonReader(inputStream.bufferedReader()).use { reader ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            if (key.length == 10 && key[4] == '-' && key[7] == '-' && key >= "2022-08-01") {
                                val displayDate = isoToDisplayDate(key)
                                val nav = reader.nextDouble()
                                if (nav > 0.0) {
                                    list.add(HistoricalNav(schemeName, displayDate, nav))
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
            } catch (e: Exception) {
                Log.e("BuiltInNavLoader", "Failed reading $fileName for $schemeName", e)
            }
        }

        return list
    }

    fun getLatestNavDate(records: List<HistoricalNav>): String {
        if (records.isEmpty()) return LATEST_BUILTIN_NAV_DATE
        val sdf1 = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdf2 = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        var maxTime = 0L
        var latestStr = LATEST_BUILTIN_NAV_DATE
        for (r in records) {
            val t = try {
                sdf2.parse(r.dateStr)?.time ?: sdf1.parse(r.dateStr)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
            if (t > maxTime) {
                maxTime = t
                latestStr = r.dateStr
            }
        }
        return latestStr
    }

    suspend fun recomputeFundsFromHistory(repository: FundRepository) = withContext(Dispatchers.IO) {
        try {
            val historicals = repository.getAllHistoricalNavsDirect()
            if (historicals.isEmpty()) return@withContext
            val grouped = historicals.groupBy { it.schemeName }
            val currentFunds = repository.getFundsDirectSync().associateBy { it.schemeName }
            val updatedFunds = ArrayList<FundData>()

            val expectedFunds = setOf(
                "Equity Long-Short Fund",
                "Equity Ex-Top 100 Long-Short Fund",
                "Sector Rotation Long-Short Fund",
                "Active Asset Allocator Long-Short Fund",
                "Hybrid Long-Short Fund",
                "Quant Income Plus Arbitrage Active FoF - Direct (G)"
            )
            for ((schemeName, records) in grouped) {
                if (records.isEmpty() || !expectedFunds.contains(schemeName)) continue
                val sorted = records.sortedBy { IndexBenchmarkSimulator.parseAnyDateToMillis(it.dateStr) ?: 0L }
                val latest = sorted.last()
                val prev = if (sorted.size > 1) sorted[sorted.size - 2] else null

                val existingFund = currentFunds[schemeName] ?: FundData(
                    schemeName = schemeName,
                    rawSchemeName = schemeName,
                    navValue = latest.navValue,
                    pctChange = 0.0,
                    pctChangeString = "0.0%",
                    navDate = latest.dateStr,
                    option = "Direct",
                    fetchedAtTimestamp = System.currentTimeMillis()
                )

                val pct = if (prev != null && prev.navValue > 0.0) {
                    ((latest.navValue - prev.navValue) / prev.navValue) * 100.0
                } else {
                    existingFund.pctChange
                }
                val pctStr = String.format(Locale.US, "%.2f%%", pct)

                val highestRecord = sorted.maxByOrNull { it.navValue }
                val highNav = highestRecord?.navValue ?: latest.navValue
                val highDate = highestRecord?.dateStr ?: latest.dateStr

                updatedFunds.add(
                    existingFund.copy(
                        navValue = latest.navValue,
                        navDate = latest.dateStr,
                        previousDayNav = prev?.navValue ?: existingFund.previousDayNav,
                        pctChange = pct,
                        pctChangeString = pctStr,
                        globalHighestNav = highNav,
                        globalHighestNavDate = highDate
                    )
                )
            }

            if (updatedFunds.isNotEmpty()) {
                repository.saveFunds(updatedFunds)
            }
        } catch (e: Exception) {
            Log.e("BuiltInNavLoader", "Failed recomputing funds from history", e)
        }
    }
}
