package com.example

import kotlinx.coroutines.flow.Flow

class FundRepository(private val fundDao: FundDao) {
    val allFunds: Flow<List<FundData>> = fundDao.getAllFunds()
    val allInvestments: Flow<List<FundInvestment>> = fundDao.getAllInvestments()

    suspend fun deleteFundsNotIn(allowedSchemes: List<String>) {
        fundDao.deleteFundsNotIn(allowedSchemes)
    }

    suspend fun saveFunds(funds: List<FundData>) {
        val allowedSchemes = listOf(
            "Equity Long-Short Fund",
            "Equity Ex-Top 100 Long-Short Fund",
            "Sector Rotation Long-Short Fund",
            "Active Asset Allocator Long-Short Fund",
            "Hybrid Long-Short Fund",
            "Quant Income Plus Arbitrage Active FoF - Direct (G)"
        )
        val filteredInput = funds.filter { allowedSchemes.contains(it.schemeName) }
        val oldFunds = fundDao.getFundsDirect().associateBy { it.schemeName }
        val mergedFunds = filteredInput.map { newFund ->
            val cached = oldFunds[newFund.schemeName]
            val exactHistPrev = NavCache.getRecords(newFund.schemeName).let { records ->
                val sdf1 = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.US)
                val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val currTime = try {
                    sdf1.parse(newFund.navDate)?.time ?: sdf2.parse(newFund.navDate)?.time ?: 0L
                } catch (e: Exception) { 0L }
                if (currTime > 0L) {
                    records.filter { rec ->
                        val t = try {
                            sdf1.parse(rec.dateStr)?.time ?: sdf2.parse(rec.dateStr)?.time ?: 0L
                        } catch (e: Exception) { 0L }
                        t in 1 until currTime
                    }.maxByOrNull { rec ->
                        try {
                            sdf1.parse(rec.dateStr)?.time ?: sdf2.parse(rec.dateStr)?.time ?: 0L
                        } catch (e: Exception) { 0L }
                    }?.nav
                } else null
            }

            if (cached != null) {
                var updated = newFund
                if (exactHistPrev != null && exactHistPrev > 0.0) {
                    updated = updated.copy(previousDayNav = exactHistPrev)
                } else if (newFund.pctChange != 0.0) {
                    val prevNavCalc = newFund.navValue / (1.0 + (newFund.pctChange / 100.0))
                    updated = updated.copy(previousDayNav = prevNavCalc)
                } else if (newFund.navValue != cached.navValue) {
                    updated = updated.copy(previousDayNav = cached.navValue)
                } else {
                    updated = updated.copy(previousDayNav = cached.previousDayNav)
                }
                var highNav = cached.globalHighestNav
                var highDate = cached.globalHighestNavDate
                
                if (highNav != null && newFund.navValue > highNav) {
                    highNav = newFund.navValue
                    highDate = newFund.navDate
                } else if (highNav == null) {
                    highNav = newFund.navValue
                    highDate = newFund.navDate
                }

                updated.copy(
                    globalHighestNav = highNav,
                    globalHighestNavDate = highDate
                )
            } else {
                if (exactHistPrev != null && exactHistPrev > 0.0) {
                    newFund.copy(previousDayNav = exactHistPrev)
                } else if (newFund.pctChange != 0.0) {
                    newFund.copy(previousDayNav = newFund.navValue / (1.0 + (newFund.pctChange / 100.0)))
                } else {
                    newFund
                }
            }
        }
        
        // Removed old HighestNav logic
        
        fundDao.insertFunds(mergedFunds)
        fundDao.deleteFundsNotIn(allowedSchemes)
    
}

    suspend fun updateFundHighest(schemeName: String, nav: Double, date: String) {
        fundDao.updateFundHighest(schemeName, nav, date)
    
}

    suspend fun saveInvestment(schemeName: String, invested: Double, units: Double, highestNav: Double, highestNavDate: String) {
        if (invested <= 0.0 || units <= 0.0) {
            fundDao.deleteInvestment(schemeName)
        
} else {
            val existing = fundDao.getInvestmentByScheme(schemeName)
            if (existing != null) {
                fundDao.insertInvestment(existing.copy(
                    investedAmount = invested, 
                    unitsHeld = units,
                    highestNav = highestNav,
                    highestNavDate = highestNavDate
                ))
            
} else {
                fundDao.insertInvestment(FundInvestment(schemeName, invested, units, highestNav, highestNavDate))
            
}
        
}
    
}

    suspend fun updateHighestNavOnly(schemeName: String, nav: Double, date: String) {
        val existing = fundDao.getInvestmentByScheme(schemeName)
        if (existing != null) {
            if (nav > existing.highestNav) {
                fundDao.insertInvestment(existing.copy(
                    highestNav = nav,
                    highestNavDate = date
                ))
            
}
        
} else {
            // Can't update highest NAV if they haven't invested, but maybe we should store it anyway?
            // "if fetch fails: do nothing, existing highestNav stays unchanged"
            fundDao.insertInvestment(FundInvestment(schemeName, 0.0, 0.0, nav, date))
        
}
    
}

    val allHistoricalNavs: Flow<List<HistoricalNav>> = fundDao.getAllHistoricalNavs()

    fun getAllHistoricalNavsDirectSync(): List<HistoricalNav> {
        return fundDao.getAllHistoricalNavsDirectSync()
    }

    suspend fun getHistoricalNavs(schemeName: String): List<HistoricalNav> {
        return fundDao.getHistoricalNavs(schemeName)
    }

    suspend fun getAllHistoricalNavsDirect(): List<HistoricalNav> {
        return fundDao.getAllHistoricalNavsDirect()
    }

    suspend fun insertHistoricalNavs(navs: List<HistoricalNav>) {
        fundDao.insertHistoricalNavs(navs)
    
}
    
    suspend fun deleteAllHistoricalNavs() {
        fundDao.deleteAllHistoricalNavs()
    }

    suspend fun deleteHistoricalNavsNotIn(allowedSchemes: List<String>) {
        fundDao.deleteHistoricalNavsNotIn(allowedSchemes)
    }

    suspend fun getInvestmentBySchemeDirect(schemeName: String): FundInvestment? {
        return fundDao.getInvestmentByScheme(schemeName)
    }

    suspend fun deleteAllCasTransactions() { fundDao.deleteAllTransactions() }

    suspend fun insertCasTransactions(txns: List<CasTransactionEntity>) {
        fundDao.insertTransactions(txns)
    }

    suspend fun deleteTransactionById(id: Int) {
        fundDao.deleteTransactionById(id)
    }

    suspend fun getTransactionsForSchemeDirect(schemeName: String): List<CasTransactionEntity> {
        return fundDao.getTransactionsForSchemeDirect(schemeName)
    }

    fun getCasTransactions(schemeName: String) = fundDao.getTransactionsForScheme(schemeName)

    fun getAllCasTransactions() = fundDao.getAllTransactions()

    fun getAllCasTransactionsDirectSync() = fundDao.getAllTransactionsDirectSync()

    fun getFundsDirectSync() = fundDao.getFundsDirectSync()

    fun getAllInvestmentsDirectSync() = fundDao.getAllInvestmentsDirectSync()

    suspend fun getAllCasTransactionsDirect() = fundDao.getAllTransactionsDirect()
}
