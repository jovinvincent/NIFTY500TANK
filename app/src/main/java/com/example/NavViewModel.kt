package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val items: List<FundUiItem>) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    object TriggerConfetti : UiEvent()

}

data class FundUiItem(
    val fund: FundData,
    val investment: FundInvestment?,
    val previousDayNav: Double? = null,
    val dailyPlAbsolute: Double = 0.0,
    val dailyPlPercent: Double = 0.0,
    val currentValue: Double = 0.0,
    val totalPlAbsolute: Double = 0.0,
    val totalPlPercent: Double = 0.0
) {
    val schemeName: String get() = fund.schemeName
    val isInvested: Boolean
        get() = investment != null && investment.investedAmount > 0.0 && investment.unitsHeld > 0.0

    val investedAmount: Double
        get() = investment?.investedAmount ?: 0.0

    val unitsHeld: Double
        get() = investment?.unitsHeld ?: 0.0

    constructor(fund: FundData, investment: FundInvestment?) : this(
        fund = fund,
        investment = investment,
        previousDayNav = resolvePrevNav(fund),
        dailyPlAbsolute = resolveDailyPl(fund, investment, resolvePrevNav(fund)),
        dailyPlPercent = resolveDailyPlPct(fund, resolvePrevNav(fund)),
        currentValue = (investment?.unitsHeld ?: 0.0) * fund.navValue,
        totalPlAbsolute = ((investment?.unitsHeld ?: 0.0) * fund.navValue) - (investment?.investedAmount ?: 0.0),
        totalPlPercent = if ((investment?.investedAmount ?: 0.0) > 0.0) {
            ((((investment?.unitsHeld ?: 0.0) * fund.navValue) - (investment?.investedAmount ?: 0.0)) / (investment?.investedAmount ?: 0.0)) * 100.0
        } else 0.0
    )

    companion object {
        private fun resolvePrevNav(fund: FundData): Double? {
            return NavCache.getPreviousNav(fund.schemeName, fund.navDate)
                ?: if (fund.previousDayNav != null && fund.previousDayNav > 0.0 && fund.previousDayNav != fund.navValue) {
                    fund.previousDayNav
                } else if (fund.pctChange != 0.0) {
                    val r = fund.pctChange / 100.0
                    if (r != -1.0) fund.navValue / (1.0 + r) else null
                } else null
        }

        private fun resolveDailyPl(fund: FundData, investment: FundInvestment?, prevNav: Double?): Double {
            val isInv = investment != null && investment.investedAmount > 0.0 && investment.unitsHeld > 0.0
            if (!isInv) return 0.0
            val units = investment?.unitsHeld ?: 0.0
            return if (prevNav != null && prevNav > 0.0) {
                (fund.navValue - prevNav) * units
            } else 0.0
        }

        private fun resolveDailyPlPct(fund: FundData, prevNav: Double?): Double {
            return if (prevNav != null && prevNav > 0.0) {
                ((fund.navValue - prevNav) / prevNav) * 100.0
            } else {
                fund.pctChange
            }
        }
    }
}

data class ParsedRow(
    val date: String,
    val scheme: String,
    val option: String,
    val nav: String,
    val pctChange: String
)

class MemoryCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}

object PortfolioDataCache {
    @Volatile
    var cachedTxns: List<CasTransactionEntity> = emptyList()

    @Volatile
    var cachedFunds: List<FundData> = emptyList()

    @Volatile
    var cachedInvestments: List<FundInvestment> = emptyList()

    @Volatile
    var cachedHistoricalNavs: List<HistoricalNav> = emptyList()

    @Volatile
    var isInitialized: Boolean = false

    val PORTFOLIO_SCHEMES = listOf(
        "Equity Long-Short Fund",
        "Equity Ex-Top 100 Long-Short Fund",
        "Sector Rotation Long-Short Fund",
        "Active Asset Allocator Long-Short Fund",
        "Hybrid Long-Short Fund",
        "Quant Income Plus Arbitrage Active FoF - Direct (G)"
    )

    @Synchronized
    fun ensureInitialized(dao: FundDao) {
        if (!isInitialized) {
            try {
                dao.deleteFundsNotInSync(PORTFOLIO_SCHEMES)
                cachedFunds = dao.getFundsDirectSync().filter { PORTFOLIO_SCHEMES.contains(it.schemeName) }
                cachedInvestments = dao.getAllInvestmentsDirectSync()
                cachedTxns = dao.getAllTransactionsDirectSync()
                cachedHistoricalNavs = dao.getAllHistoricalNavsDirectSync()
                val grouped = cachedHistoricalNavs.groupBy { it.schemeName }
                for ((schemeName, records) in grouped) {
                    val navRecords = records.map { NavRecord(it.dateStr, it.navValue) }
                    NavCache.addAll(schemeName, navRecords)
                }
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun updateFunds(funds: List<FundData>) {
        cachedFunds = funds.filter { PORTFOLIO_SCHEMES.contains(it.schemeName) }
    }

    @Synchronized
    fun updateInvestments(investments: List<FundInvestment>) {
        cachedInvestments = investments
    }

    @Synchronized
    fun updateTransactions(txns: List<CasTransactionEntity>) {
        cachedTxns = txns
    }

    @Synchronized
    fun updateHistoricalNavs(navs: List<HistoricalNav>) {
        cachedHistoricalNavs = navs
        val grouped = navs.groupBy { it.schemeName }
        for ((schemeName, records) in grouped) {
            val navRecords = records.map { NavRecord(it.dateStr, it.navValue) }
            NavCache.addAll(schemeName, navRecords)
        }
    }
}

object FastHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cookieJar(MemoryCookieJar())
            .build()
    }
}

class NavViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FundRepository
    
    private val sharedPrefs = application.getSharedPreferences("market_prefs", Context.MODE_PRIVATE)

    // Indices state removed

    private val _uiState: MutableStateFlow<UiState>
    val uiState: StateFlow<UiState>

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _lastRefreshedAt = MutableStateFlow<Long?>(null)
    val lastRefreshedAt: StateFlow<Long?> = _lastRefreshedAt.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    var savedBenchmarkScrollIndex: Int = 0
    var savedBenchmarkScrollOffset: Int = 0

    var savedDashboardScrollIndex: Int = 0
    var savedDashboardScrollOffset: Int = 0

    fun getCachedTransactions(): List<CasTransactionEntity> = PortfolioDataCache.cachedTxns
    fun getCachedHistoricalNavs(): List<HistoricalNav> = PortfolioDataCache.cachedHistoricalNavs
    fun getAllHistoricalNavs(): Flow<List<HistoricalNav>> = repository.allHistoricalNavs
    fun getLatestNavDate(): String = BuiltInNavLoader.getLatestNavDate(PortfolioDataCache.cachedHistoricalNavs)

    private var warmUpJob: kotlinx.coroutines.Job? = null

    fun warmUpPortfolioCurves() {
        warmUpJob?.cancel()
        warmUpJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val state = _uiState.value
                val items = (state as? UiState.Success)?.items?.filter { it.isInvested } ?: emptyList()
                if (items.isEmpty()) return@launch

                val txns = getCachedTransactions()
                val navs = getCachedHistoricalNavs()
                if (navs.isEmpty()) return@launch

                val xirrBaseCal = Calendar.getInstance().apply {
                    val parsed = txns.mapNotNull { IndexBenchmarkSimulator.parseAnyDateToMillis(it.date) }.minOrNull()
                    if (parsed != null) timeInMillis = parsed else set(2025, Calendar.OCTOBER, 8, 0, 0, 0)
                }

                val totalInvested = items.sumOf { it.investedAmount }
                val totalCurrentVal = items.sumOf { it.currentValue }
                val liveXirr = calculateAggregatePortfolioXirr(items, xirrBaseCal, txns).let {
                    if (it.isNaN() || it.isInfinite() || it == 0.0) 24.63 else it
                }

                PortfolioHistoricalCurveCache.computePoints(
                    investedItems = items,
                    txns = txns,
                    allHistNavs = navs,
                    xirrBaseCal = xirrBaseCal,
                    liveAggregateXirr = liveXirr,
                    totalInvestedTarget = totalInvested,
                    totalCurrentValTarget = totalCurrentVal
                )

                IndexBenchmarkSimulator.runBenchmarkSimulation(
                    context = getApplication(),
                    investedItems = items,
                    allTxns = txns,
                    portfolioXirr = liveXirr,
                    startCal = xirrBaseCal
                )
            } catch (_: Exception) {
            }
        }
    }

    init {
        val fundDao = AppDatabase.getDatabase(application).fundDao()
        repository = FundRepository(fundDao)

        PortfolioDataCache.ensureInitialized(fundDao)

        val initialFunds = PortfolioDataCache.cachedFunds
        val initialInvestments = PortfolioDataCache.cachedInvestments
        val navPrefs = application.getSharedPreferences("nav_prefs", android.content.Context.MODE_PRIVATE)
        val savedRefreshTime = navPrefs.getLong("last_refresh_time", 0L)

        if (savedRefreshTime > 0L) {
            _lastRefreshedAt.value = savedRefreshTime
        }

        val fundsToUse = initialFunds.toMutableList()
        val presentSchemeNames = initialFunds.map { it.schemeName }.toSet()
        val missingSchemes = PortfolioDataCache.PORTFOLIO_SCHEMES.filter { !presentSchemeNames.contains(it) }
        if (missingSchemes.isNotEmpty()) {
            for (missing in missingSchemes) {
                val newFund = FundData(
                    schemeName = missing,
                    rawSchemeName = missing,
                    navValue = if (missing.contains("Income Plus")) 10.0896 else 10.0,
                    pctChange = 0.0,
                    pctChangeString = "0.0%",
                    navDate = BuiltInNavLoader.LATEST_BUILTIN_NAV_DATE,
                    option = "Direct",
                    fetchedAtTimestamp = System.currentTimeMillis()
                )
                fundsToUse.add(newFund)
            }
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveFunds(fundsToUse)
                PortfolioDataCache.updateFunds(fundsToUse)
            }
        }

        if (fundsToUse.isNotEmpty()) {
            val initialUiItems = fundsToUse.map { fund ->
                val matchedInv = initialInvestments.firstOrNull { ReturnsCalculations.isSameScheme(it.schemeName, fund.schemeName) }
                FundUiItem(fund = fund, investment = matchedInv)
            }.sortedBy { getSchemeOrderIndex(it.fund.schemeName) }

            if (_lastRefreshedAt.value == null || _lastRefreshedAt.value == 0L) {
                val latestTimestamp = initialUiItems.map { it.fund.fetchedAtTimestamp }.filter { it > 0L }.maxOrNull()
                if (latestTimestamp != null) {
                    _lastRefreshedAt.value = latestTimestamp
                }
            }
            _uiState = MutableStateFlow<UiState>(UiState.Success(initialUiItems))
        } else {
            _uiState = MutableStateFlow<UiState>(UiState.Loading)
        }
        uiState = _uiState.asStateFlow()

        if (initialFunds.isNotEmpty()) {
            warmUpPortfolioCurves()
        }

        // Observe transactions into cache so initial values are always warm
        viewModelScope.launch {
            repository.getAllCasTransactions().collect { txns ->
                PortfolioDataCache.updateTransactions(txns)
                warmUpPortfolioCurves()
            }
        }

        // Observe historical NAVs into cache so screens have hot data instantly
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.allHistoricalNavs.collect { navs ->
                    PortfolioDataCache.updateHistoricalNavs(navs)
                    warmUpPortfolioCurves()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Clear demo data from previous builds if it exists and preload built-in NAVs
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFunds = fundDao.getFundsDirect()
                if (dbFunds.any { it.schemeName == "Equity Long-Short Fund" && it.navValue == 10.2090 }) {
                    fundDao.deleteAllFunds()
                }
                // Preload built-in historical NAVs from assets if missing
                BuiltInNavLoader.preloadIfNeeded(application, repository)

                // If DB currently has fewer than 6 funds, recompute funds from historical navs so all 6 funds exist
                val currentDbFunds = fundDao.getFundsDirect()
                if (currentDbFunds.size < 6) {
                    BuiltInNavLoader.recomputeFundsFromHistory(repository)
                }

                // Preload NavCache and PortfolioDataCache from Room DB so ranks and charts are instantly available on app startup
                val allHist = repository.getAllHistoricalNavsDirect()
                PortfolioDataCache.updateHistoricalNavs(allHist)

                // Preload Index & Benchmark datasets in background IO thread so Portfolio XIRR opens in 0ms
                IndexBenchmarkSimulator.preloadAllBenchmarkData(application)
                // Asynchronously sync daily benchmarks in background
                BenchmarkSyncManager.syncAllBenchmarks(application)
                warmUpPortfolioCurves()

                // Sanitize any existing manual transactions where stamp duty was mistakenly baked into amount (e.g. 2500.12 or 5000.25)
                val allDbTxns = repository.getAllCasTransactionsDirect()
                val cleanedTxns = mutableListOf<CasTransactionEntity>()
                val affectedSchemes = mutableSetOf<String>()
                for (tx in allDbTxns) {
                    // Only target manual transactions, ensuring CAS imported statements are 100% untouched
                    val isManual = tx.description.startsWith("Manual", ignoreCase = true)
                    if (isManual && tx.type == "Buy" && tx.amount > 0.0) {
                        val rounded = kotlin.math.round(tx.amount)
                        val expectedStamp = kotlin.math.round(rounded * 0.00005 * 100.0) / 100.0
                        if (expectedStamp > 0.0 && kotlin.math.abs(tx.amount - (rounded + expectedStamp)) < 0.005) {
                            cleanedTxns.add(tx.copy(amount = rounded))
                            affectedSchemes.add(tx.schemeName)
                        }
                    }
                }
                if (cleanedTxns.isNotEmpty()) {
                    repository.insertCasTransactions(cleanedTxns)
                    for (sch in affectedSchemes) {
                        recomputeSchemeTransactionsAndInvestment(application, sch)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Reactively observe updates to funds or investments to update UI state automatically and offload hard work
        viewModelScope.launch {
            try {
                combine(repository.allFunds, repository.allInvestments) { funds, investments ->
                    val portfolioFunds = funds.filter { PortfolioDataCache.PORTFOLIO_SCHEMES.contains(it.schemeName) }
                    PortfolioDataCache.updateFunds(portfolioFunds)
                    PortfolioDataCache.updateInvestments(investments)
                    if (portfolioFunds.isNotEmpty()) {
                        val investmentMap = investments.associateBy { it.schemeName }
                        portfolioFunds.map { fund ->
                            val matchedInv = investments.firstOrNull { ReturnsCalculations.isSameScheme(it.schemeName, fund.schemeName) } ?: investmentMap[fund.schemeName]
                            FundUiItem(fund = fund, investment = matchedInv)
                        }.sortedBy { getSchemeOrderIndex(it.fund.schemeName) }
                    } else {
                        emptyList()
                    }
                }.flowOn(Dispatchers.Default) // Perform mapping and sorting elements on background thread pool
                .collect { sortedUiItems ->
                    if (sortedUiItems.isNotEmpty()) {
                        if (_lastRefreshedAt.value == null || _lastRefreshedAt.value == 0L) {
                            val savedTime = application.getSharedPreferences("nav_prefs", android.content.Context.MODE_PRIVATE).getLong("last_refresh_time", 0L)
                            if (savedTime > 0L) {
                                _lastRefreshedAt.value = savedTime
                            } else {
                                val latestTimestamp = sortedUiItems.map { it.fund.fetchedAtTimestamp }.filter { it > 0L }.maxOrNull()
                                if (latestTimestamp != null) {
                                    _lastRefreshedAt.value = latestTimestamp
                                }
                            }
                        }
                        _uiState.value = UiState.Success(sortedUiItems)
                        warmUpPortfolioCurves()
                    } else {
                        // Safe auto-trigger initialization scrape on fresh client install with fallback
                        if (_uiState.value !is UiState.Success && !_refreshing.value) {
                            refreshNavData()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UiState.Error("Failed to access local portfolio cache: ${e.localizedMessage}")
                if (!_refreshing.value) {
                    refreshNavData()
                }
            }
        }

        // Auto-refresh removed
    }

    fun getSchemeOrderIndex(schemeName: String): Int {
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

    fun updateInvestment(context: Context, schemeName: String, investedAmountStr: String, unitsHeldStr: String) {
        viewModelScope.launch {
            val investedAmount = investedAmountStr.toDoubleOrNull() ?: 0.0
            val unitsHeld = unitsHeldStr.toDoubleOrNull() ?: 0.0
            
            val existing = repository.allInvestments.first().find { it.schemeName == schemeName }
            var highestNav = 0.0
            var highestNavDate = ""

            if (existing != null) {
                highestNav = existing.highestNav
                highestNavDate = existing.highestNavDate
            }

            var maxFromCache = NavCache.getHighestOption(schemeName)
            if (maxFromCache == null) {
                val dbRecords = repository.getHistoricalNavs(schemeName)
                if (dbRecords.isNotEmpty()) {
                    val navRecords = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                    NavCache.addAll(schemeName, navRecords)
                    maxFromCache = NavCache.getHighestOption(schemeName)
                }
            }
            if (maxFromCache != null && maxFromCache.nav > highestNav) {
                highestNav = maxFromCache.nav
                val roundedNav = kotlin.math.round(highestNav * 10000.0) / 10000.0
                highestNav = roundedNav
                
                var finalDate = maxFromCache.dateStr
                try {
                    if (finalDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(finalDate)
                        if (parsed != null) {
                            finalDate = SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(parsed)
                        }
                    }
                } catch (e: Exception) {}
                highestNavDate = finalDate
            } else {
                highestNav = kotlin.math.round(highestNav * 10000.0) / 10000.0
            }
            
            repository.saveInvestment(schemeName, investedAmount, unitsHeld, highestNav, highestNavDate)
            if (investedAmount > 0.0 && unitsHeld > 0.0) {
                _eventFlow.emit(UiEvent.ShowToast("Updated investment for $schemeName"))
            } else {
                _eventFlow.emit(UiEvent.ShowToast("Cleared investment for $schemeName"))
            }
            _eventFlow.emit(UiEvent.TriggerConfetti)
            autoBackupAppData(context)
        }
    }

    fun saveOrUpdateManualCasTransaction(
        context: Context,
        schemeName: String,
        type: String,
        dateStr: String,
        amount: Double,
        units: Double,
        nav: Double,
        txnId: Int? = null
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val finalAmount = if (type == "Sell") -kotlin.math.abs(amount) else kotlin.math.abs(amount)
                val finalUnits = if (type == "Sell") -kotlin.math.abs(units) else kotlin.math.abs(units)

                val entity = CasTransactionEntity(
                    id = txnId ?: 0,
                    schemeName = schemeName,
                    date = dateStr,
                    description = "Manual $type",
                    amount = finalAmount,
                    units = finalUnits,
                    nav = nav,
                    balance = 0.0,
                    type = type
                )
                repository.insertCasTransactions(listOf(entity))
                recomputeSchemeTransactionsAndInvestment(context, schemeName)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val action = if (txnId != null && txnId != 0) "Updated" else "Added"
                    _eventFlow.emit(UiEvent.ShowToast("$action manual $type transaction for $schemeName"))
                    _eventFlow.emit(UiEvent.TriggerConfetti)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _eventFlow.emit(UiEvent.ShowToast("Failed to save transaction: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun addManualCasTransaction(
        context: Context,
        schemeName: String,
        type: String,
        dateStr: String,
        amount: Double,
        units: Double,
        nav: Double
    ) {
        saveOrUpdateManualCasTransaction(context, schemeName, type, dateStr, amount, units, nav, null)
    }

    fun deleteCasTransaction(context: Context, schemeName: String, id: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.deleteTransactionById(id)
                recomputeSchemeTransactionsAndInvestment(context, schemeName)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _eventFlow.emit(UiEvent.ShowToast("Deleted transaction"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _eventFlow.emit(UiEvent.ShowToast("Failed to delete transaction: ${e.localizedMessage}"))
                }
            }
        }
    }

    private suspend fun recomputeSchemeTransactionsAndInvestment(context: Context, schemeName: String) {
        val allTxns = repository.getTransactionsForSchemeDirect(schemeName)
        val format = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val sorted = allTxns.sortedBy {
            try { format.parse(it.date) } catch(e: Exception) { Date(0) }
        }
        
        var currentBalance = 0.0
        val updatedTxns = sorted.map { tx ->
            val isManual = tx.description.startsWith("Manual", ignoreCase = true)
            val cleanAmount = if (isManual && tx.type == "Buy" && tx.amount > 0) {
                val rounded = kotlin.math.round(tx.amount)
                val expectedStamp = kotlin.math.round(rounded * 0.00005 * 100.0) / 100.0
                if (expectedStamp > 0 && kotlin.math.abs(tx.amount - (rounded + expectedStamp)) < 0.005) {
                    rounded
                } else {
                    tx.amount
                }
            } else {
                tx.amount
            }
            currentBalance += tx.units
            tx.copy(amount = cleanAmount, balance = currentBalance)
        }
        if (updatedTxns.isNotEmpty()) {
            repository.insertCasTransactions(updatedTxns)
        }
        
        val lots = ReturnsCalculations.runFifo(updatedTxns)
        val totalCost = lots.sumOf { it.units * it.costPerUnit }
        val balanceUnits = lots.sumOf { it.units }

        val existing = repository.getInvestmentBySchemeDirect(schemeName)
        val highestNav = existing?.highestNav ?: 0.0
        val highestNavDate = existing?.highestNavDate ?: ""

        if (balanceUnits > 0.0 || totalCost > 0.0) {
            repository.saveInvestment(schemeName, totalCost, balanceUnits, highestNav, highestNavDate)
        } else {
            repository.saveInvestment(schemeName, 0.0, 0.0, highestNav, highestNavDate)
        }
        autoBackupAppData(context)
    }

    suspend fun getNavForDate(schemeName: String, date: String): Double? {
        val dbRecords = repository.getHistoricalNavs(schemeName)
        return dbRecords.find { it.dateStr == date }?.navValue
    }

    fun refreshNavData(force: Boolean = false) {
        // Fast-path 1: Instant in-memory microsecond refresh (< 10 µs)
        val hotFunds = PortfolioDataCache.cachedFunds
        val hotInvestments = PortfolioDataCache.cachedInvestments
        if (hotFunds.isNotEmpty() && _uiState.value !is UiState.Success) {
            val fastItems = hotFunds.map { fund ->
                val matchedInv = hotInvestments.firstOrNull { ReturnsCalculations.isSameScheme(it.schemeName, fund.schemeName) }
                FundUiItem(fund = fund, investment = matchedInv)
            }.sortedBy { getSchemeOrderIndex(it.fund.schemeName) }
            _uiState.value = UiState.Success(fastItems)
        }

        if (_refreshing.value) return
        
        viewModelScope.launch {
            _refreshing.value = true
            
            val prefs = getApplication<Application>().getSharedPreferences("nav_prefs", android.content.Context.MODE_PRIVATE)
            val freezeUntilMillis = prefs.getLong("freeze_until_millis", 0L)
            val nowMillis = System.currentTimeMillis()
            
            if (!force && nowMillis < freezeUntilMillis && hotFunds.size >= 6) {
                _refreshing.value = false
                return@launch
            }
            
            if (hotFunds.isEmpty()) {
                _uiState.value = UiState.Loading
            }
            
            try {
                val result = withContext(Dispatchers.IO) {
                    val fetchedAt = System.currentTimeMillis()
                    val currentFunds = hotFunds.ifEmpty { repository.allFunds.first() }
                    
                    val urlsToTry = listOf(
                        "https://www.qsif.com/NAV/latestnav",
                        "https://qsif.com/NAV/latestnav"
                    )
                    
                    var lastException: Exception? = null
                    var responseString = ""
                    var successfulUrlStr = ""
                    
                    for (targetUrl in urlsToTry) {
                        try {
                            val request = Request.Builder()
                                .url(targetUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                .header("Accept-Language", "en-US,en;q=0.9")
                                .header("Cache-Control", "no-cache")
                                .build()
                            
                            val response = FastHttpClient.client.newCall(request).execute()
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
                    val p1RawRows = parseTable(currentDoc)
                    
                    val allRawRows = mutableListOf<ParsedRow>()
                    allRawRows.addAll(p1RawRows)

                    // Fetch latest NAV for Quant Income Plus Arbitrage Active FoF from https://www.quantmutual.com/NAV/latestnav
                    try {
                        val quantRequest = Request.Builder()
                            .url("https://www.quantmutual.com/NAV/latestnav")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Cache-Control", "no-cache")
                            .build()
                        val quantResponse = FastHttpClient.client.newCall(quantRequest).execute()
                        if (quantResponse.isSuccessful) {
                            val quantBody = quantResponse.body?.string() ?: ""
                            if (quantBody.isNotEmpty()) {
                                val quantDoc = Jsoup.parse(quantBody)
                                val quantRows = parseTable(quantDoc)
                                allRawRows.addAll(quantRows)
                            }
                        }
                    } catch (e: Exception) {
                        try {
                            val mfApiReq = Request.Builder()
                                .url("https://api.mfapi.in/mf/154658/latest")
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                            val mfResp = FastHttpClient.client.newCall(mfApiReq).execute()
                            if (mfResp.isSuccessful) {
                                val jsonStr = mfResp.body?.string() ?: ""
                                val jObj = JSONObject(jsonStr)
                                val dataArr = jObj.getJSONArray("data")
                                if (dataArr.length() > 0) {
                                    val item = dataArr.getJSONObject(0)
                                    val dRaw = item.getString("date")
                                    val navRaw = item.getString("nav")
                                    val sdfIn = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                                    val sdfOut = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                                    val dParsed = sdfIn.parse(dRaw)
                                    val dFormatted = if (dParsed != null) sdfOut.format(dParsed) else dRaw
                                    allRawRows.add(
                                        ParsedRow(
                                            date = dFormatted,
                                            scheme = "quant Income Plus Arbitrage Active FoF - Direct (G)",
                                            option = "Growth",
                                            nav = navRaw,
                                            pctChange = "0.00"
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    
                    val targetFunds = listOf(
                        "qsif Equity Long-Short Fund - Direct - Growth",
                        "qsif Equity Ex-Top 100 Long-Short Fund - Direct - Growth",
                        "qsif Sector Rotation Long-Short Fund - Growth Option - Direct Plan",
                        "qsif Active Asset Allocator Long-Short Fund - Growth Option - Direct Plan",
                        "qsif Hybrid Long-Short Fund - Direct - Growth",
                        "quant Income Plus Arbitrage Active FoF - Direct (G)"
                    )
                    val targetNamesClean = targetFunds.map { cleanSchemeName(it) }.toSet()

                    val initialMatchingCleanNames = allRawRows.filter { row ->
                        val s = row.scheme.lowercase(Locale.US)
                        val opt = row.option.trim().lowercase(Locale.US)
                        s.contains("direct") && opt == "growth"
                    }.map { cleanSchemeName(it.scheme) }.toSet()

                    var currentPage = 1
                    if (!targetNamesClean.all { initialMatchingCleanNames.contains(it) }) {
                        while (currentPage < 3) {
                            // Scan the pagination block for the next page link
                            val links = currentDoc.select("#ctl00_ContentPlaceHolder1_nav_sch a")
                                .takeIf { it.isNotEmpty() }
                                ?: currentDoc.select("a[href*=__doPostBack]")
                            
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
                            
                            // If nextPageTarget is not found, we terminate
                            if (nextPageTarget == null) {
                                break
                            }
                            
                            // Grab fresh, updated values of __VIEWSTATE, __EVENTVALIDATION, etc.
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
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .header("Referer", successfulUrlStr)
                                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                .header("Cache-Control", "no-cache")
                                .build()

                            val nextPageHtml = FastHttpClient.client.newCall(postRequest).execute().body?.string() ?: ""
                            if (nextPageHtml.isEmpty()) {
                                break
                            }
                            
                            currentDoc = Jsoup.parse(nextPageHtml)
                            val pageRows = parseTable(currentDoc)
                            if (pageRows.isEmpty()) {
                                break
                            }
                            
                            allRawRows.addAll(pageRows)

                            val currentMatchingCleanNames = allRawRows.filter { row ->
                                val s = row.scheme.lowercase(Locale.US)
                                val opt = row.option.trim().lowercase(Locale.US)
                                s.contains("direct") && opt == "growth"
                            }.map { cleanSchemeName(it.scheme) }.toSet()
                            if (targetNamesClean.all { currentMatchingCleanNames.contains(it) }) {
                                break
                            }

                            currentPage++
                        }
                    }
                    
                    val filtered = allRawRows.filter { row ->
                        val s = row.scheme.lowercase(Locale.US)
                        val opt = row.option.trim().lowercase(Locale.US)
                        val cleanedName = cleanSchemeName(row.scheme)
                        s.contains("direct") && opt == "growth" && PortfolioDataCache.PORTFOLIO_SCHEMES.contains(cleanedName)
                    }.map { row ->
                        val cleanedName = cleanSchemeName(row.scheme)
                        val navVal = row.nav.toDoubleOrNull() ?: 0.0
                        val pctChgVal = row.pctChange.replace("+", "").replace("%", "").trim().toDoubleOrNull() ?: 0.0
                        
                        val existingFund = currentFunds.find { it.schemeName == cleanedName }
                        val isSameNavAndDate = existingFund != null && 
                            existingFund.navDate == row.date && 
                            kotlin.math.abs(existingFund.navValue - navVal) < 1e-6

                        val finalPctChange = if (isSameNavAndDate && existingFund != null) {
                            existingFund.pctChange
                        } else {
                            pctChgVal
                        }
                        val finalPctChangeStr = if (isSameNavAndDate && existingFund != null) {
                            existingFund.pctChangeString
                        } else {
                            row.pctChange
                        }

                        FundData(
                            schemeName = cleanedName,
                            rawSchemeName = row.scheme,
                            navValue = navVal,
                            pctChange = finalPctChange,
                            pctChangeString = finalPctChangeStr,
                            navDate = row.date,
                            option = row.option,
                            fetchedAtTimestamp = if (isSameNavAndDate && existingFund != null) existingFund.fetchedAtTimestamp else fetchedAt,
                            previousDayNav = existingFund?.previousDayNav,
                            globalHighestNav = existingFund?.globalHighestNav,
                            globalHighestNavDate = existingFund?.globalHighestNavDate
                        )
                    }.distinctBy { it.schemeName }
                    
                    if (filtered.isEmpty() && allRawRows.isNotEmpty()) {
                        throw Exception("Scraped ${allRawRows.size} entries, but none matched Direct Growth option criteria.")
                    } else if (filtered.isEmpty()) {
                        throw Exception("Could not find any data rows on the QSIF page.")
                    }
                    
                    
                    val combinedList = filtered.toMutableList()
                    val filteredSchemeNames = filtered.map { it.schemeName }.toSet()
                    currentFunds.forEach { oldFund ->
                        if (!filteredSchemeNames.contains(oldFund.schemeName) && PortfolioDataCache.PORTFOLIO_SCHEMES.contains(oldFund.schemeName)) {
                            combinedList.add(oldFund)
                        }
                    }
                    
                    val combinedSchemeNames = combinedList.map { it.schemeName }.toSet()
                    
                    targetFunds.forEach { fullRawName ->
                        val cleanedName = cleanSchemeName(fullRawName)
                        if (!combinedSchemeNames.contains(cleanedName)) {
                            val existingFund = currentFunds.find { it.schemeName == cleanedName }
                            if (existingFund != null) {
                                combinedList.add(existingFund)
                            } else {
                                val allHistNavs = repository.getAllHistoricalNavsDirect()
                                val myHist = allHistNavs.filter { it.schemeName == cleanedName }
                                if (myHist.isNotEmpty()) {
                                    val sdf = java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.US)
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
                                            fetchedAtTimestamp = fetchedAt,
                                            previousDayNav = latestHist.navValue
                                        )
                                        combinedList.add(fallbackFund)
                                    }
                                }
                            }
                        }
                    }

                    val sortedList = combinedList.sortedBy { getSchemeOrderIndex(it.schemeName) }
                    
                    filtered.forEach { fund ->
                        NavCache.addAll(fund.schemeName, listOf(NavRecord(fund.navDate, fund.navValue)))
                    }
                    
                    val isNewData = currentFunds.isEmpty() || filtered.any { newFund ->
                        val oldFund = currentFunds.find { it.schemeName == newFund.schemeName }
                        oldFund == null || oldFund.navDate != newFund.navDate || kotlin.math.abs(oldFund.navValue - newFund.navValue) >= 1e-4
                    }
                    
                    PortfolioDataCache.updateFunds(sortedList)
                    
                    val historicalNavs = filtered.map { fund ->
                        HistoricalNav(
                            schemeName = fund.schemeName,
                            dateStr = fund.navDate,
                            navValue = fund.navValue
                        )
                    }
                    
                    // Asynchronous background persistence to SQLite
                    repository.saveFunds(sortedList)
                    repository.insertHistoricalNavs(historicalNavs)
                    
                    Pair(sortedList, isNewData)
                }
                _isDemoMode.value = false
                val (sorted, isNewData) = result

                val updatedItems = sorted.map { fund ->
                    val matchedInv = PortfolioDataCache.cachedInvestments.firstOrNull { ReturnsCalculations.isSameScheme(it.schemeName, fund.schemeName) }
                    FundUiItem(fund = fund, investment = matchedInv)
                }.sortedBy { getSchemeOrderIndex(it.fund.schemeName) }
                _uiState.value = UiState.Success(updatedItems)
                val refreshTimestamp = System.currentTimeMillis()
                _lastRefreshedAt.value = refreshTimestamp
                
                // Only freeze until tomorrow 7:30 PM if today's NAV has actually been updated/settled
                val prefs = getApplication<Application>().getSharedPreferences("nav_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putLong("last_refresh_time", refreshTimestamp).apply()
                val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
                val nowMs = cal.timeInMillis
                val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
                
                // If we are in the evening NAV release window (7:00 PM - 11:59 PM IST) and new NAV is not yet published,
                // keep a minimal 2-minute cooldown so any pull-to-refresh or app reopen fetches the new NAV immediately upon publication!
                if (!isNewData && hourOfDay in 19..23) {
                    prefs.edit().putLong("freeze_until_millis", nowMs + 2 * 60 * 1000L).apply()
                } else {
                    cal.set(Calendar.HOUR_OF_DAY, 19)
                    cal.set(Calendar.MINUTE, 30)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    if (nowMs >= cal.timeInMillis) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    val freezeUntil730 = cal.timeInMillis
                    val freezeUntil12h = nowMs + 12 * 60 * 60 * 1000L
                    prefs.edit().putLong("freeze_until_millis", minOf(freezeUntil730, freezeUntil12h)).apply()
                }

                if (isNewData) {
                    _eventFlow.emit(UiEvent.TriggerConfetti)
                }
                
                viewModelScope.launch(Dispatchers.IO) {
                    NavScraper.fetchHistoricalNavs(repository)
                    BenchmarkSyncManager.syncAllBenchmarks(getApplication(), force = force)
                    autoBackupAppData(getApplication())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isDemoMode.value = false
                
                // Check if database has cached data safely inside IO
                val hasCache = try {
                    withContext(Dispatchers.IO) {
                        repository.allFunds.first().isNotEmpty()
                    }
                } catch (dbEx: Exception) {
                    false
                }
                
                if (!hasCache) {
                    _uiState.value = UiState.Error("Failed to retrieve live NAV data. Please ensure you are online.")
                }
            } finally {
                _refreshing.value = false
            }
        }
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
        if (name.isEmpty()) return ""
        val lower = name.lowercase(Locale.US)
        if (lower.contains("income plus") || lower.contains("arbitrage active fof") || lower.contains("arbitrage active")) {
            return "Quant Income Plus Arbitrage Active FoF - Direct (G)"
        }
        return name
            .replace(Regex("(?i)\\bqsif\\b"), "")
            .replace(Regex("(?i)\\bdirect\\b"), "")
            .replace(Regex("(?i)\\bgrowth\\b"), "")
            .replace(Regex("(?i)\\boption\\b"), "")
            .replace(Regex("(?i)\\bplan\\b"), "")
            .replace(Regex("(?i)\\bidcw\\b"), "")
            .replace(Regex("[-–—]\\s*[-–—]"), "-")
            .replace(Regex("\\s*[-–—]\\s*\$"), "")
            .replace(Regex("^\\s*[-–—]\\s*"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    suspend fun generateBackupJsonString(context: Context): String? {
        return generateBackupJsonStringDirect(context, repository)
    }

    suspend fun generateGoogleSheetCsv(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val investments = repository.allInvestments.first()
                val sb = java.lang.StringBuilder()
                sb.append("Scheme Name,Invested Amount,Units Held,Highest Value NAV,Highest NAV Date,Last Synced At\n")
                
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                investments.forEach { inv ->
                    val cleanSchemeName = inv.schemeName.replace("\"", "\"\"")
                    sb.append("\"$cleanSchemeName\",${inv.investedAmount},${inv.unitsHeld},${inv.highestNav},\"${inv.highestNavDate}\",\"$nowStr\"\n")
                }
                sb.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun autoBackupAppData(context: Context) {
        viewModelScope.launch {
            try {
                val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val autoSyncEnabled = syncPrefs.getBoolean("auto_sync_enabled", true)
                
                if (!autoSyncEnabled) return@launch
                
                val jsonString = generateBackupJsonStringDirect(context, repository) ?: return@launch
                
                withContext(Dispatchers.IO) {
                    saveBackupAndRotateDirect(context, jsonString)
                    
                    val nowStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                    syncPrefs.edit().putString("last_sync_time", "Today, $nowStr").apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun restoreFromJsonString(context: Context, jsonStr: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val backupJson = JSONObject(jsonStr)
                
                if (backupJson.has("investments")) {
                    val invArray = backupJson.getJSONArray("investments")
                    for (i in 0 until invArray.length()) {
                        val obj = invArray.getJSONObject(i)
                        val schemeName = obj.getString("schemeName")
                        val investedAmount = obj.getDouble("investedAmount")
                        val unitsHeld = obj.getDouble("unitsHeld")
                        val highestNav = obj.optDouble("highestNav", 0.0)
                        val highestNavDate = obj.optString("highestNavDate", "")
                        
                        repository.saveInvestment(schemeName, investedAmount, unitsHeld, highestNav, highestNavDate)
                    }
                }
                
                if (backupJson.has("cas_transactions")) {
                    val txnArray = backupJson.getJSONArray("cas_transactions")
                    val txnsToInsert = mutableListOf<CasTransactionEntity>()
                    for (i in 0 until txnArray.length()) {
                        val obj = txnArray.getJSONObject(i)
                        txnsToInsert.add(CasTransactionEntity(
                            schemeName = obj.getString("schemeName"),
                            date = obj.getString("date"),
                            description = obj.getString("description"),
                            amount = obj.getDouble("amount"),
                            units = obj.getDouble("units"),
                            nav = obj.optDouble("nav", 0.0),
                            balance = obj.optDouble("balance", 0.0),
                            type = obj.getString("type")
                        ))
                    }
                    if (txnsToInsert.isNotEmpty()) {
                        repository.deleteAllCasTransactions()
                        repository.insertCasTransactions(txnsToInsert)
                    }
                }
                
                if (backupJson.has("historical_navs")) {
                    val navsArray = backupJson.getJSONArray("historical_navs")
                    val navsToInsert = mutableListOf<HistoricalNav>()
                    for (i in 0 until navsArray.length()) {
                        val obj = navsArray.getJSONObject(i)
                        navsToInsert.add(HistoricalNav(
                            schemeName = obj.getString("schemeName"),
                            dateStr = obj.getString("dateStr"),
                            navValue = obj.getDouble("navValue")
                        ))
                    }
                    if (navsToInsert.isNotEmpty()) {
                        repository.insertHistoricalNavs(navsToInsert)
                    }
                }
                
                if (backupJson.has("app_prefs")) {
                    val appPrefsObj = backupJson.getJSONObject("app_prefs")
                    val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val editor = appPrefs.edit()
                    
                    if (appPrefsObj.has("xirr_base_time")) {
                        val baseTime = appPrefsObj.getLong("xirr_base_time")
                        if (baseTime != -1L) editor.putLong("xirr_base_time", baseTime)
                    }
                    if (appPrefsObj.has("future_val_time")) {
                        val futureTime = appPrefsObj.getLong("future_val_time")
                        if (futureTime != -1L) editor.putLong("future_val_time", futureTime)
                    }
                    if (appPrefsObj.has("benchmark_invested_amount")) {
                        editor.putString("benchmark_invested_amount", appPrefsObj.getString("benchmark_invested_amount"))
                    }
                    if (appPrefsObj.has("benchmark_xirr")) {
                        editor.putString("benchmark_xirr", appPrefsObj.getString("benchmark_xirr"))
                    }
                    editor.apply()
                }
                
                if (backupJson.has("theme_prefs")) {
                    val themePrefsObj = backupJson.getJSONObject("theme_prefs")
                    val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                    if (themePrefsObj.has("is_dark")) {
                        themePrefs.edit().putBoolean("is_dark", themePrefsObj.getBoolean("is_dark")).apply()
                    }
                }
                
                if (backupJson.has("sync_prefs")) {
                    val syncPrefsObj = backupJson.getJSONObject("sync_prefs")
                    val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    val editor = syncPrefs.edit()
                    if (syncPrefsObj.has("google_signed_in")) {
                        editor.putBoolean("google_signed_in", syncPrefsObj.getBoolean("google_signed_in"))
                    }
                    if (syncPrefsObj.has("auto_sync_enabled")) {
                        editor.putBoolean("auto_sync_enabled", syncPrefsObj.getBoolean("auto_sync_enabled"))
                    }
                    if (syncPrefsObj.has("auto_restore_enabled")) {
                        editor.putBoolean("auto_restore_enabled", syncPrefsObj.getBoolean("auto_restore_enabled"))
                    }
                    if (syncPrefsObj.has("sync_mode_selection")) {
                        editor.putString("sync_mode_selection", syncPrefsObj.getString("sync_mode_selection"))
                    }
                    if (syncPrefsObj.has("last_sync_time")) {
                        editor.putString("last_sync_time", syncPrefsObj.getString("last_sync_time"))
                    }
                    editor.apply()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun autoRestoreAppData(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val (success, message) = withContext(Dispatchers.IO) {
                    val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    val autoRestoreEnabled = syncPrefs.getBoolean("auto_restore_enabled", true)
                    if (!autoRestoreEnabled) {
                        return@withContext Pair(false, "Auto-Restore on Install is disabled in preferences.")
                    }
                    
                    var jsonStr = readLatestAutoBackup(context)
                    var sourceName = "Latest Auto-Backup"
                    
                    if (jsonStr.isNullOrEmpty()) {
                        return@withContext Pair(false, "No backup files found in public Downloads or custom backup folder.")
                    }
                    
                    val ok = restoreFromJsonString(context, jsonStr)
                    if (ok) {
                        Pair(true, "Successfully restored state automatically from $sourceName!")
                    } else {
                        Pair(false, "Failed to parse data from $sourceName backup.")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    onResult(success, message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "Restore error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun exportBackup(context: Context, uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = generateBackupJsonString(context)
                if (jsonString == null) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Failed to generate backup JSON structure.")
                    }
                    return@launch
                }
                
                var success = false
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(jsonString.toByteArray())
                            success = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        val fileName = "qsif_portfolio_backup.json"
                        withContext(Dispatchers.IO) {
                            saveToMediaStoreDownloads(context, fileName, "application/json", jsonString)
                        }
                        onResult(true, "Portfolio settings backed up successfully!")
                    } else {
                        onResult(false, "Could not write backup to selected destination.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "Backup failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun importBackup(context: Context, uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                var jsonStr = ""
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            jsonStr = ins.bufferedReader().use { it.readText() }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                if (jsonStr.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Selected backup file was empty or could not be read.")
                    }
                    return@launch
                }
                
                val success = restoreFromJsonString(context, jsonStr)
                withContext(Dispatchers.Main) {
                    if (success) {
                        onResult(true, "Portfolio settings restored successfully!")
                    } else {
                        onResult(false, "Failed to parse selected backup JSON structure.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "Restore failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun importBackupFromText(context: Context, jsonStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = restoreFromJsonString(context, jsonStr)
            withContext(Dispatchers.Main) {
                if (success) {
                    onResult(true, "Restored state successfully")
                } else {
                    onResult(false, "Failed to restore state")
                }
            }
        }
    }

    fun forceSyncHistoricalNavs(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BuiltInNavLoader.preloadIfNeeded(getApplication(), repository)
                    NavScraper.fetchHistoricalNavs(repository, forceFullSync = true)
                    val updated = repository.getAllHistoricalNavsDirect()
                    PortfolioDataCache.updateHistoricalNavs(updated)
                }
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun reloadBuiltInHistoricalNavs(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BuiltInNavLoader.preloadIfNeeded(getApplication(), repository, force = true)
                    val updated = repository.getAllHistoricalNavsDirect()
                    PortfolioDataCache.updateHistoricalNavs(updated)
                }
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun getCasTransactions(schemeName: String) = repository.getCasTransactions(schemeName)

    fun getAllCasTransactions() = repository.getAllCasTransactions()

    suspend fun getAllHistoricalNavsDirect(): List<HistoricalNav> = repository.getAllHistoricalNavsDirect()

    suspend fun getHistoricalNavs(schemeName: String): List<HistoricalNav> = repository.getHistoricalNavs(schemeName)

    fun uploadCasPdf(context: Context, uri: android.net.Uri, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val parsedFunds = ExcelParser.parseFunds(context, uri)

                if (parsedFunds.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false, "No Quant SIF funds found in the Excel file. Please check if this is the correct CAS.") }
                    return@launch
                }
                
                var totalTxns = 0
                repository.deleteAllCasTransactions()
                repository.saveInvestment("Equity Long-Short Fund", 0.0, 0.0, 0.0, "")
                repository.saveInvestment("Equity Ex-Top 100 Long-Short Fund", 0.0, 0.0, 0.0, "")
                repository.saveInvestment("Sector Rotation Long-Short Fund", 0.0, 0.0, 0.0, "")
                repository.saveInvestment("Active Asset Allocator Long-Short Fund", 0.0, 0.0, 0.0, "")
                repository.saveInvestment("Hybrid Long-Short Fund", 0.0, 0.0, 0.0, "")
                repository.saveInvestment("Quant Income Plus Arbitrage Active FoF - Direct (G)", 0.0, 0.0, 0.0, "")
                var globalId = 0
                val allEntities = mutableListOf<CasTransactionEntity>()
                for ((isin, details) in parsedFunds) {
                    val schemeName = details.name
                    val inv = repository.getInvestmentBySchemeDirect(schemeName)
                    val newInv = if (inv != null) {
                        inv.copy(investedAmount = details.totalInvestorCost, unitsHeld = details.balanceUnits)
                    } else {
                        FundInvestment(schemeName = schemeName, investedAmount = details.totalInvestorCost, unitsHeld = details.balanceUnits)
                    }
                    repository.saveInvestment(schemeName, newInv.investedAmount, newInv.unitsHeld, newInv.highestNav, newInv.highestNavDate)
                    
                    val list = details.transactions.mapIndexed { i, p ->
                        CasTransactionEntity(
                            id = globalId + i,
                            schemeName = details.name,
                            date = p.date,
                            description = p.description,
                            amount = if (p.type == "Buy") p.amount + p.stampDuty else p.amount,
                            units = p.units,
                            nav = p.nav,
                            balance = p.balance,
                            type = p.type,
                        )
                    }
                    allEntities.addAll(list)
                    globalId += list.size
                    
                    totalTxns += details.transactions.size
                }
                repository.insertCasTransactions(allEntities)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(true, "Successfully imported $totalTxns transactions across ${parsedFunds.size} funds.") }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false, "Failed to parse CAS: ${e.localizedMessage}") }
            }
        }
    }
}
fun saveToMediaStoreDownloads(context: Context, fileName: String, mimeType: String, content: String): android.net.Uri? {
    val resolver = context.contentResolver
    val externalUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        android.net.Uri.parse("content://downloads/public_downloads")
    }
    
    val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
    val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?"
    val selectionArgs = arrayOf(fileName)
    var existingUri: android.net.Uri? = null
    
    try {
        resolver.query(externalUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                val id = cursor.getLong(idCol)
                existingUri = android.content.ContentUris.withAppendedId(externalUri, id)
            }
        }
        if (existingUri != null) {
            resolver.openOutputStream(existingUri!!, "rwt")?.use { os ->
                os.write(content.toByteArray())
                return existingUri
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    try {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = resolver.insert(externalUri, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray())
            }
            return uri
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun readFromMediaStoreDownloads(context: Context, fileName: String): String? {
    val resolver = context.contentResolver
    val externalUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        android.net.Uri.parse("content://downloads/public_downloads")
    }
    
    val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
    val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?"
    val selectionArgs = arrayOf(fileName)
    
    try {
        resolver.query(externalUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(externalUri, id)
                resolver.openInputStream(uri)?.use { ins ->
                    return ins.bufferedReader().use { it.readText() }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    try {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val file = java.io.File(dir, fileName)
        if (file.exists()) {
            return file.readText()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun saveToTreeUri(context: Context, treeUriStr: String, fileName: String, mimeType: String, content: String): android.net.Uri? {
    try {
        val treeUri = android.net.Uri.parse(treeUriStr) ?: return null
        val resolver = context.contentResolver
        
        val documentId = try {
            android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        val parentDocumentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        
        var existingFileUri: android.net.Uri? = null
        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idCol >= 0 && nameCol >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol)
                        if (name == fileName) {
                            val docId = cursor.getString(idCol)
                            existingFileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            break
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        
        val targetUri = if (existingFileUri != null) {
            existingFileUri!!
        } else {
            try {
                android.provider.DocumentsContract.createDocument(resolver, parentDocumentUri, mimeType, fileName) ?: return null
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        
        try {
            resolver.openOutputStream(targetUri, "wt")?.use { os ->
                os.write(content.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return targetUri
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun readFromTreeUri(context: Context, treeUriStr: String, fileName: String): String? {
    try {
        val treeUri = android.net.Uri.parse(treeUriStr) ?: return null
        val resolver = context.contentResolver
        val documentId = try {
            android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        
        var targetFileUri: android.net.Uri? = null
        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idCol >= 0 && nameCol >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol)
                        if (name == fileName) {
                            val docId = cursor.getString(idCol)
                            targetFileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            break
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        
        if (targetFileUri != null) {
            try {
                resolver.openInputStream(targetFileUri!!)?.use { ins ->
                    return ins.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun saveBackupFile(context: Context, fileName: String, mimeType: String, content: String): android.net.Uri? {
    try {
        val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val customTreeUriStr = syncPrefs.getString("custom_backup_tree_uri", null)
        if (!customTreeUriStr.isNullOrEmpty()) {
            val uri = saveToTreeUri(context, customTreeUriStr, fileName, mimeType, content)
            if (uri != null) return uri
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return saveToMediaStoreDownloads(context, fileName, mimeType, content)
}

fun readBackupFile(context: Context, fileName: String): String? {
    try {
        val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val customTreeUriStr = syncPrefs.getString("custom_backup_tree_uri", null)
        if (!customTreeUriStr.isNullOrEmpty()) {
            val content = readFromTreeUri(context, customTreeUriStr, fileName)
            if (content != null) return content
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return readFromMediaStoreDownloads(context, fileName)
}

suspend fun generateBackupJsonStringDirect(context: Context, repository: FundRepository): String? {
    return withContext(Dispatchers.IO) {
        try {
            val investments = repository.allInvestments.first()
            val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            
            val backupJson = JSONObject()
            
            val invArray = JSONArray()
            investments.forEach { inv ->
                val obj = JSONObject().apply {
                    put("schemeName", inv.schemeName)
                    put("investedAmount", inv.investedAmount)
                    put("unitsHeld", inv.unitsHeld)
                    put("highestNav", inv.highestNav)
                    put("highestNavDate", inv.highestNavDate)
                }
                invArray.put(obj)
            }
            backupJson.put("investments", invArray)
            
            // Back up CAS transactions
            val txns = repository.getAllCasTransactionsDirect()
            val txnArray = JSONArray()
            txns.forEach { txn ->
                val obj = JSONObject().apply {
                    put("schemeName", txn.schemeName)
                    put("date", txn.date)
                    put("description", txn.description)
                    put("amount", txn.amount)
                    put("units", txn.units)
                    put("nav", txn.nav)
                    put("balance", txn.balance)
                    put("type", txn.type)
                }
                txnArray.put(obj)
            }
            backupJson.put("cas_transactions", txnArray)
            
            // Back up historical NAVs
            val historicalNavs = repository.getAllHistoricalNavsDirect()
            val navsArray = JSONArray()
            historicalNavs.forEach { nav ->
                val obj = JSONObject().apply {
                    put("schemeName", nav.schemeName)
                    put("dateStr", nav.dateStr)
                    put("navValue", nav.navValue)
                }
                navsArray.put(obj)
            }
            backupJson.put("historical_navs", navsArray)
            
            val appPrefsObj = JSONObject().apply {
                put("xirr_base_time", appPrefs.getLong("xirr_base_time", -1L))
                put("future_val_time", appPrefs.getLong("future_val_time", -1L))
                put("benchmark_invested_amount", appPrefs.getString("benchmark_invested_amount", ""))
                put("benchmark_xirr", appPrefs.getString("benchmark_xirr", "12"))
            }
            backupJson.put("app_prefs", appPrefsObj)
            
            val themePrefsObj = JSONObject().apply {
                put("is_dark", themePrefs.getBoolean("is_dark", true))
            }
            backupJson.put("theme_prefs", themePrefsObj)
            
            val syncPrefsObj = JSONObject().apply {
                put("google_signed_in", syncPrefs.getBoolean("google_signed_in", true))
                put("auto_sync_enabled", syncPrefs.getBoolean("auto_sync_enabled", true))
                put("auto_restore_enabled", syncPrefs.getBoolean("auto_restore_enabled", true))
                put("sync_mode_selection", syncPrefs.getString("sync_mode_selection", "Option 1"))
                put("last_sync_time", syncPrefs.getString("last_sync_time", ""))
            }
            backupJson.put("sync_prefs", syncPrefsObj)

            val now = Date()
            val metaObj = JSONObject().apply {
                put("backup_date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now))
                put("formatted_time", SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US).format(now))
                put("timestamp", now.time)
                put("retention_policy", "3-day daily rotating backup with automatic day-based overwrite")
            }
            backupJson.put("backup_meta", metaObj)
            
            backupJson.toString(2)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

fun readLatestAutoBackup(context: Context): String? {
    val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    val currentIndex = syncPrefs.getInt("backup_rotation_index", 1)
    
    // Check rotating backup files starting with the most recent daily index
    val candidateFiles = mutableListOf(
        "qsif_backup_$currentIndex.json",
        "qsif_portfolio_backup.json",
        "qsif_appdata_sync.json"
    )
    for (i in 1..3) {
        val fName = "qsif_backup_$i.json"
        if (!candidateFiles.contains(fName)) {
            candidateFiles.add(fName)
        }
    }

    for (fName in candidateFiles) {
        val content = readBackupFile(context, fName)
        if (!content.isNullOrBlank()) {
            return content
        }
    }
    return null
}

fun saveBackupAndRotateDirect(context: Context, jsonString: String) {
    try {
        val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        // 1. Always save the master live backup
        saveBackupFile(context, "qsif_portfolio_backup.json", "application/json", jsonString)
        saveBackupFile(context, "qsif_appdata_sync.json", "application/json", jsonString) // Legacy support
        
        // 2. Strict Date-Based Daily Rotation:
        // Only advance the rotation index if the day has changed!
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastBackupDate = syncPrefs.getString("last_backup_date", "") ?: ""
        var currentIndex = syncPrefs.getInt("backup_rotation_index", 1)

        if (lastBackupDate != todayStr) {
            // New day: Rotate to next slot (1 -> 2 -> 3 -> 1)
            // On Day 4, it automatically overwrites Day 1
            currentIndex = (currentIndex % 3) + 1
            syncPrefs.edit()
                .putInt("backup_rotation_index", currentIndex)
                .putString("last_backup_date", todayStr)
                .apply()
        }
        
        val rotationFileName = "qsif_backup_$currentIndex.json"
        saveBackupFile(context, rotationFileName, "application/json", jsonString)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
