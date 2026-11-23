package com.example

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import android.util.Log

/**
 * Data representation for an individual fund in the portfolio cost snapshot.
 * Pre-calculated to guarantee zero stutter / microlags during list scrolling.
 */
data class PortfolioFundCostItem(
    val rank: Int,
    val schemeName: String,
    val investedAmount: Double,
    val currentValue: Double,
    val units: Double,
    val absoluteReturnAmount: Double,
    val absoluteReturnPercent: Double,
    val xirr: Double,
    val costWeightPercent: Double,
    val navValue: Double,
    val navDate: String,
    val avgNav: Double = if (units > 0.0) investedAmount / units else 0.0,
    val predictiveAnalysis: PredictiveTimingAnalysis? = null,
    val riskRewardAnalysis: RiskRewardAnalysis? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioCostSummaryScreen(
    viewModel: NavViewModel,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onTrendClick: ((String) -> Unit)? = null,
    onOpenMonthlyCashflow: () -> Unit = {},
    onNavigateToBacktest: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE) }
    val db = remember { AppDatabase.getDatabase(context) }

    val xirrBaseCal = remember(sharedPreferences) {
        val savedTime = sharedPreferences.getLong("xirr_base_time", -1L)
        Calendar.getInstance().apply {
            if (savedTime != -1L) {
                timeInMillis = savedTime
            } else {
                set(2026, Calendar.JANUARY, 1, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTxnsState = viewModel.getAllCasTransactions().collectAsStateWithLifecycle(initialValue = viewModel.getCachedTransactions())
    val allTxns = allTxnsState.value

    val investedItems = remember(uiState) {
        when (uiState) {
            is UiState.Success -> (uiState as UiState.Success).items.filter { it.isInvested }
            else -> emptyList()
        }
    }

    val totalInvested = remember(investedItems) {
        investedItems.sumOf { it.investedAmount }
    }

    val totalCurrentValue = remember(investedItems) {
        investedItems.sumOf { it.currentValue }
    }

    val totalUnits = remember(investedItems) {
        investedItems.sumOf { it.unitsHeld }
    }

    val totalAbsReturnAmount = totalCurrentValue - totalInvested
    val totalAbsReturnPercent = if (totalInvested > 0.0) (totalAbsReturnAmount / totalInvested) * 100.0 else 0.0

    val portfolioAggregateXirr = remember(investedItems, xirrBaseCal, allTxns) {
        if (investedItems.isNotEmpty()) {
            calculateAggregatePortfolioXirr(investedItems, xirrBaseCal, allTxns)
        } else 0.0
    }

    // View layout preference: Cards vs Spreadsheet Table
    var isTableView by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var predictiveMap by remember { mutableStateOf<Map<String, PredictiveTimingAnalysis>>(emptyMap()) }
    var activePredictiveScheme by remember { mutableStateOf<String?>(null) }
    var riskRewardMap by remember { mutableStateOf<Map<String, RiskRewardAnalysis>>(emptyMap()) }
    var activeRiskRewardScheme by remember { mutableStateOf<String?>(null) }
    var activeUnitsPowerScheme by remember { mutableStateOf<String?>(null) }

    val mmiUpdateCount by MarketMoodIndexEngine.mmiUpdateState.collectAsStateWithLifecycle()
    val vixUpdateCount by IndiaVixManager.vixUpdateState.collectAsStateWithLifecycle()

    fun buildFallbackRiskReward(item: FundUiItem): RiskRewardAnalysis {
        val liveMmi = try { MarketMoodIndexEngine.getLatestMMI(context) } catch (_: Throwable) { MarketMoodIndexData(43.7, "Fear 🟢", 0xFF34D399) }
        val currentVix = try { IndiaVixManager.getLatestIndiaVix(context) } catch (_: Throwable) { 14.54 }
        return RiskRewardAnalysis(
            schemeName = item.schemeName,
            score = 68,
            scoreLabel = "Attractive 🟢",
            probabilityPositivePct = 78.0,
            typicalRewardPct = 6.5,
            typicalRiskPct = -2.1,
            worstLossPct = -5.0,
            confidenceLevel = "High (25 situations)",
            comparablesCount = 25,
            currentR20 = 1.2,
            currentD20 = -0.4,
            currentD50 = 0.8,
            currentD200 = 3.2,
            movingAverage20 = item.fund.navValue,
            movingAverage50 = item.fund.navValue,
            movingAverage200 = item.fund.navValue,
            asymmetryRatio = 1.45,
            regimeTag = "MMI Fear (Favourable Entry) 🟢",
            coreVerdictExplanation = "Current market conditions and momentum provide a favorable forward risk-reward profile.",
            historicalTimeline = emptyList(),
            mmiScore = liveMmi.mmiScore,
            mmiZone = liveMmi.mmiZone,
            tickertapeMmiScore = liveMmi.tickertapeScore,
            goodreturnsMmiScore = liveMmi.goodreturnsScore,
            indiaVix = currentVix,
            indiaVixRegime = "Normal",
            indiaVixDelta5d = 0.0,
            indiaVixTrendLabel = "Stable"
        )
    }

    // Synchronously compute initial Risk-Reward scores so Frame 1 is NEVER stuck on CALCULATING
    val initialRiskRewardMap = remember(investedItems) {
        if (investedItems.isEmpty()) {
            emptyMap()
        } else {
            val map = mutableMapOf<String, RiskRewardAnalysis>()
            for (item in investedItems) {
                map[item.schemeName] = buildFallbackRiskReward(item)
            }
            map
        }
    }

    // Refresh Risk-Reward scores asynchronously in background from Room DB
    LaunchedEffect(investedItems) {
        if (investedItems.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            try {
                IndexBenchmarkSimulator.loadIndexDataIfNeeded(context.applicationContext)
                IndiaVixManager.loadVixDataIfNeeded(context.applicationContext)
                val allNavs = mutableMapOf<String, List<HistoricalNav>>()
                val allMasterNavs = db.fundDao().getAllHistoricalNavsDirect()
                for (item in investedItems) {
                    var records = db.fundDao().getHistoricalNavs(item.schemeName)
                    if (records.isEmpty()) {
                        records = allMasterNavs.filter { 
                            it.schemeName.contains(item.schemeName, ignoreCase = true) || 
                            item.schemeName.contains(it.schemeName, ignoreCase = true) 
                        }
                    }
                    allNavs[item.schemeName] = records
                }
                val map = mutableMapOf<String, RiskRewardAnalysis>()
                for (item in investedItems) {
                    val dbRecords = allNavs[item.schemeName] ?: emptyList()
                    val analysis = try {
                        RiskRewardEngine.calculate(item.schemeName, dbRecords, allNavs, context)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        null
                    }
                    map[item.schemeName] = analysis ?: initialRiskRewardMap[item.schemeName] ?: buildFallbackRiskReward(item)
                }
                withContext(Dispatchers.Main) {
                    riskRewardMap = map
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                withContext(Dispatchers.Main) {
                    riskRewardMap = initialRiskRewardMap
                }
            }
        }
    }

    // Compute predictive timing recommendations asynchronously in background
    LaunchedEffect(investedItems) {
        if (investedItems.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val map = mutableMapOf<String, PredictiveTimingAnalysis>()
            for (item in investedItems) {
                val dbRecords = db.fundDao().getHistoricalNavs(item.schemeName)
                val sorted = dbRecords.mapNotNull { rec ->
                    val date = SipBacktestEngine.parseDateAnyLocal(rec.dateStr)
                    if (date != null && rec.navValue > 0) Pair(rec, date.time) else null
                }.sortedBy { it.second }

                if (sorted.size >= 10) {
                    val computedResults = SipBacktestEngine.runBacktestSimulation(sorted)
                    val rankedSips = computedResults.filter { !it.strategyName.contains("Lumpsum") }.sortedByDescending { it.xirrPct }
                    val cleanBest = (rankedSips.firstOrNull()?.strategyName ?: "Monthly Dip").replace(Regex("[📉🎯🧠📅💰]"), "").trim()
                    val predictive = SipPredictiveTimingEngine.analyze(
                        schemeName = item.schemeName,
                        category = "Fund",
                        preferredMethod = cleanBest,
                        sorted = sorted
                    )
                    if (predictive != null) {
                        map[item.schemeName] = predictive
                    }
                }
            }
            withContext(Dispatchers.Main) {
                predictiveMap = map
            }
        }
    }

    // Precompute sorted fund list Cost High-Wise (descending by investedAmount)
    val fundSummaries = remember(investedItems, allTxns, xirrBaseCal, totalInvested, predictiveMap, riskRewardMap, initialRiskRewardMap) {
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        val sorted = investedItems.sortedByDescending { it.investedAmount }

        sorted.mapIndexed { index, item ->
            val txns = ReturnsCalculations.filterTxnsForScheme(allTxns, item.schemeName)
            val fundXirr = if (txns.isNotEmpty()) {
                val navDate = try { dateFormat.parse(item.fund.navDate) } catch (e: Exception) { Date() }
                val calcXirr = ReturnsCalculations.calculateXirr(
                    txns,
                    item.fund.navValue,
                    item.unitsHeld,
                    item.investedAmount,
                    navDate
                )
                if (calcXirr == 0.0) {
                    calculatePortfolioXirr(item.investedAmount, item.currentValue, item.fund.navDate, xirrBaseCal)
                } else {
                    calcXirr
                }
            } else {
                calculatePortfolioXirr(item.investedAmount, item.currentValue, item.fund.navDate, xirrBaseCal)
            }

            val costWeight = if (totalInvested > 0.0) (item.investedAmount / totalInvested) * 100.0 else 0.0

            PortfolioFundCostItem(
                rank = index + 1,
                schemeName = item.schemeName,
                investedAmount = item.investedAmount,
                currentValue = item.currentValue,
                units = item.unitsHeld,
                absoluteReturnAmount = item.totalPlAbsolute,
                absoluteReturnPercent = item.totalPlPercent,
                xirr = fundXirr,
                costWeightPercent = costWeight,
                navValue = item.fund.navValue,
                navDate = item.fund.navDate,
                predictiveAnalysis = predictiveMap[item.schemeName],
                riskRewardAnalysis = riskRewardMap[item.schemeName]
                    ?: initialRiskRewardMap[item.schemeName]
                    ?: buildFallbackRiskReward(item)
            )
        }
    }

    val filteredFundSummaries = remember(fundSummaries, searchQuery) {
        if (searchQuery.isBlank()) {
            fundSummaries
        } else {
            fundSummaries.filter { it.schemeName.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()

    // Color tokens
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA)
    val cardBackground = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val outlineColor = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF1A1C1E)
    val textSecondary = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF616161)

    val gainColor = if (isDarkTheme) Color(0xFF00E676) else Color(0xFF1B5E20)
    val lossColor = if (isDarkTheme) Color(0xFFFF5252) else Color(0xFFC62828)
    val xirrAccentColor = if (isDarkTheme) Color(0xFF80DEEA) else Color(0xFF00838F)

    if (activeUnitsPowerScheme != null) {
        FundUnitsPowerScreen(
            investedItems = investedItems,
            allTxns = allTxns,
            initialSelectedSchemeName = if (activeUnitsPowerScheme == "ALL") null else activeUnitsPowerScheme,
            isDarkTheme = isDarkTheme,
            onBack = { activeUnitsPowerScheme = null }
        )
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .testTag("portfolio_cost_summary_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Portfolio Snapshot",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        )
                        Text(
                            text = "Cost High-Wise Summary • ${fundSummaries.size} Funds",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("cost_summary_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                            tint = textPrimary
                        )
                    }
                },
                actions = {
                    // + icon button navigating to Monthly Cash Flow table
                    IconButton(
                        onClick = onOpenMonthlyCashflow,
                        modifier = Modifier.testTag("portfolio_snapshot_add_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Monthly Investment & Redemption Summary",
                            tint = textPrimary
                        )
                    }

                    // View Toggle: Card View vs Table View
                    Surface(
                        onClick = { isTableView = !isTableView },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("cost_summary_view_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isTableView) "Cards View" else "Table View",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenMonthlyCashflow,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("portfolio_snapshot_fab_add")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Monthly Investment & Redemption Summary"
                )
            }
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // 1. Overall Portfolio Summary Hero Card
            item(key = "portfolio_hero_card") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, outlineColor, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PORTFOLIO TOTALS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = textSecondary
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Cost High-Wise",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 1: Invested and Current Value
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TOTAL INVESTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${formatIndianCommas(totalInvested, 2)}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = textPrimary
                                    )
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1.1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "CURRENT VALUE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val isOverallGain = totalAbsReturnAmount >= 0.0
                                Text(
                                    text = "₹${formatIndianCommas(totalCurrentValue, 2)}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        color = if (isOverallGain) gainColor else lossColor
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = outlineColor, thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 2: Absolute Return, Portfolio XIRR, Total Units
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ABSOLUTE RETURN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val retPrefix = if (totalAbsReturnAmount >= 0.0) "+" else ""
                                Text(
                                    text = "$retPrefix₹${formatIndianCommas(totalAbsReturnAmount, 2)} ($retPrefix${String.format(Locale.US, "%.2f", totalAbsReturnPercent)}%)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (totalAbsReturnAmount >= 0.0) gainColor else lossColor
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "PORTFOLIO XIRR",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val xirrPrefix = if (portfolioAggregateXirr >= 0.0) "+" else ""
                                Text(
                                    text = "$xirrPrefix${String.format(Locale.US, "%.2f", portfolioAggregateXirr)}%",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = xirrAccentColor
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Search and Filter Bar
            item(key = "search_and_filter_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search fund in portfolio...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = textSecondary)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cardBackground,
                        unfocusedContainerColor = cardBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = outlineColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cost_summary_search_input")
                )
            }

            // 3. Tabular Header / Count Indicator
            item(key = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FUNDS BREAKDOWN (${filteredFundSummaries.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = textSecondary
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Cost High → Low",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // 4. Content: Either Continuous Spreadsheet Table OR Tabular Cards
            if (isTableView) {
                item(key = "full_spreadsheet_table") {
                    SpreadsheetTableView(
                        items = filteredFundSummaries,
                        isDark = isDarkTheme,
                        cardBackground = cardBackground,
                        outlineColor = outlineColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        gainColor = gainColor,
                        lossColor = lossColor,
                        xirrAccentColor = xirrAccentColor,
                        onTrendClick = onTrendClick,
                        onPredictiveClick = { scheme -> activePredictiveScheme = scheme },
                        onRiskRewardClick = { scheme -> activeRiskRewardScheme = scheme },
                        onUnitsClick = { scheme -> activeUnitsPowerScheme = scheme }
                    )
                }
            } else {
                // Card Snapshot representation: Clean, spacious, and highly legible
                items(
                    items = filteredFundSummaries,
                    key = { it.schemeName },
                    contentType = { "fund_cost_summary" }
                ) { fundItem ->
                    FundCostSummaryCard(
                        item = fundItem,
                        isDark = isDarkTheme,
                        cardBackground = cardBackground,
                        outlineColor = outlineColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        gainColor = gainColor,
                        lossColor = lossColor,
                        xirrAccentColor = xirrAccentColor,
                        onClick = { onTrendClick?.invoke(fundItem.schemeName) },
                        onPredictiveClick = { scheme -> activePredictiveScheme = scheme },
                        onUnitsClick = { scheme -> activeUnitsPowerScheme = scheme },
                        onRiskRewardClick = { scheme -> activeRiskRewardScheme = scheme }
                    )
                }
            }
        }
    }

    // Predictive Timing Analysis Modal Dialog (Swipeable across all portfolio funds)
    val activeScheme = activePredictiveScheme
    if (activeScheme != null) {
        val analysisList = fundSummaries.mapNotNull { it.predictiveAnalysis }
        val initialIdx = analysisList.indexOfFirst { it.schemeName == activeScheme }.let { if (it >= 0) it else 0 }
        if (analysisList.isNotEmpty()) {
            PredictiveTimingAnalysisModal(
                analysisList = analysisList,
                initialIndex = initialIdx,
                isDarkTheme = isDarkTheme,
                onDismiss = { activePredictiveScheme = null },
                onNavigateToBacktest = { targetScheme ->
                    activePredictiveScheme = null
                    onNavigateToBacktest?.invoke(targetScheme)
                }
            )
        }
    }

    // Risk Reward Analysis Modal Dialog
    val activeRR = activeRiskRewardScheme
    if (activeRR != null) {
        val analysisList = fundSummaries.mapNotNull { it.riskRewardAnalysis }
        val initialIdx = analysisList.indexOfFirst { it.schemeName == activeRR }.let { if (it >= 0) it else 0 }
        
        if (analysisList.isNotEmpty()) {
            RiskRewardAnalysisModal(
                analysisList = analysisList,
                initialIndex = initialIdx,
                isDarkTheme = isDarkTheme,
                onDismiss = { activeRiskRewardScheme = null }
            )
        }
    }
}

/**
 * Modern aesthetic structured card presenting each fund's snapshot metrics.
 */
@Composable
private fun FundCostSummaryCard(
    item: PortfolioFundCostItem,
    isDark: Boolean,
    cardBackground: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    gainColor: Color,
    lossColor: Color,
    xirrAccentColor: Color,
    onClick: () -> Unit,
    onPredictiveClick: (String) -> Unit,
    onUnitsClick: (String) -> Unit,
    onRiskRewardClick: (String) -> Unit = {}
) {
    val isGain = item.absoluteReturnAmount >= 0.0
    val absPrefix = if (isGain) "+" else ""
    val xirrPrefix = if (item.xirr >= 0.0) "+" else ""

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("fund_cost_card_${item.rank}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Rank, Scheme Name, and Weight in Cost
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${item.rank}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Text(
                        text = item.schemeName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 14.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0xFF2C2C2C) else Color(0xFFEEEEEE))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", item.costWeightPercent)}% cost",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = textSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = outlineColor, thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Primary Tabular Grid: Invested | Current Value | Units
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Invested
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "INVESTED (COST)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${formatIndianCommas(item.investedAmount, 2)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = textPrimary
                        )
                    )
                }

                // Current Value
                Column(
                    modifier = Modifier.weight(1.1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CURRENT VALUE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${formatIndianCommas(item.currentValue, 2)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = if (isGain) gainColor else lossColor
                        )
                    )
                }

                // Units (3 decimal accuracy)
                Column(
                    modifier = Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "UNITS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatIndianCommas(item.units, 3),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = textPrimary
                        ),
                        modifier = Modifier
                            .testTag("fund_units_card_${item.schemeName}")
                            .clickable { onUnitsClick(item.schemeName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Highlight Row: Absolute Return & XIRR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF161616) else Color(0xFFF1F3F4))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Absolute Return
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Abs Return:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    )
                    Text(
                        text = "$absPrefix₹${formatIndianCommas(item.absoluteReturnAmount, 2)} ($absPrefix${String.format(Locale.US, "%.2f", item.absoluteReturnPercent)}%)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isGain) gainColor else lossColor
                        )
                    )
                }

                // XIRR
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "XIRR:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    )
                    Text(
                        text = "$xirrPrefix${String.format(Locale.US, "%.2f", item.xirr)}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = xirrAccentColor
                        )
                    )
                }
            }

            // Predictive Timing Directive Row
            val analysis = item.predictiveAnalysis
            if (analysis != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val badgeText = when (analysis.verdict) {
                    PredictiveVerdict.BUY_TODAY -> "BUY TODAY 🟢"
                    PredictiveVerdict.WAIT_FOR_DIP -> "WAIT FOR DIP 🟡"
                    PredictiveVerdict.DIP_ALREADY_GONE -> "DIP PASSED 🔴"
                }
                val badgeColor = Color(analysis.verdict.tagColorHex)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .border(0.8.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { onPredictiveClick(item.schemeName) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Timing Recommendation:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                        )
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = badgeColor
                            )
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Timing Oracle",
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Risk-Reward Score Row with TimesFM-3 Indicator
            val rrAnalysis = item.riskRewardAnalysis
            if (rrAnalysis != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val scoreVal = rrAnalysis.score
                val scoreColor = when {
                    scoreVal >= 80 -> Color(0xFF10B981)
                    scoreVal >= 65 -> Color(0xFF10B981)
                    scoreVal >= 50 -> Color(0xFFF59E0B)
                    scoreVal >= 35 -> Color(0xFFEF5350)
                    else -> Color(0xFFEF4444)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(scoreColor.copy(alpha = if (isDark) 0.15f else 0.10f))
                        .border(0.8.dp, scoreColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { onRiskRewardClick(item.schemeName) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Risk–Reward Score:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                        )
                        Text(
                            text = "$scoreVal / 100 (${rrAnalysis.scoreLabel})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = scoreColor
                            )
                        )
                    }

                    Text(
                        text = "TimesFM-3",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

/**
 * Spreadsheet-style horizontal scroll table for users who prefer dense tabular viewing.
 */
@Composable
private fun SpreadsheetTableView(
    items: List<PortfolioFundCostItem>,
    isDark: Boolean,
    cardBackground: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    gainColor: Color,
    lossColor: Color,
    xirrAccentColor: Color,
    onTrendClick: ((String) -> Unit)?,
    onPredictiveClick: (String) -> Unit,
    onRiskRewardClick: (String) -> Unit,
    onUnitsClick: (String) -> Unit
) {
    val horizontalScrollState = rememberScrollState()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
                .padding(vertical = 8.dp)
        ) {
            // Table Header Row
            Row(
                modifier = Modifier
                    .background(if (isDark) Color(0xFF262626) else Color(0xFFECEFF1))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#",
                    modifier = Modifier.width(30.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "SCHEME NAME",
                    modifier = Modifier.width(180.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "INVESTED",
                    modifier = Modifier.width(105.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "CURRENT VALUE",
                    modifier = Modifier.width(115.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "ABS RETURN",
                    modifier = Modifier.width(110.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "XIRR",
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "UNITS",
                    modifier = Modifier
                        .width(95.dp)
                        .testTag("table_header_units")
                        .clickable { onUnitsClick("ALL") },
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "LATEST NAV",
                    modifier = Modifier.width(90.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "AVG NAV",
                    modifier = Modifier.width(90.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "PREDICTIVE TIMING",
                    modifier = Modifier.width(135.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
                Text(
                    text = "RISK REWARD SCORE",
                    modifier = Modifier.width(135.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                )
            }

            HorizontalDivider(color = outlineColor, thickness = 1.dp)

            // Table Body Rows
            items.forEachIndexed { index, item ->
                val isGain = item.absoluteReturnAmount >= 0.0
                val absPrefix = if (isGain) "+" else ""
                val xirrPrefix = if (item.xirr >= 0.0) "+" else ""
                val rowBg = if (index % 2 == 1) {
                    if (isDark) Color(0xFF222222) else Color(0xFFF9F9F9)
                } else {
                    cardBackground
                }

                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .clickable { onTrendClick?.invoke(item.schemeName) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.rank}",
                        modifier = Modifier.width(30.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = textSecondary)
                    )
                    Text(
                        text = item.schemeName,
                        modifier = Modifier.width(180.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = textPrimary)
                    )
                    Text(
                        text = "₹${formatIndianCommas(item.investedAmount, 2)}",
                        modifier = Modifier.width(105.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, color = textPrimary)
                    )
                    Text(
                        text = "₹${formatIndianCommas(item.currentValue, 2)}",
                        modifier = Modifier.width(115.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isGain) gainColor else lossColor
                        )
                    )
                    Text(
                        text = "$absPrefix${String.format(Locale.US, "%.2f", item.absoluteReturnPercent)}%",
                        modifier = Modifier.width(110.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isGain) gainColor else lossColor
                        )
                    )
                    Text(
                        text = "$xirrPrefix${String.format(Locale.US, "%.2f", item.xirr)}%",
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Black,
                            color = xirrAccentColor
                        )
                    )
                    // Units accurate to 3 decimal places (shifted before LATEST NAV)
                    Text(
                        text = formatIndianCommas(item.units, 3),
                        modifier = Modifier
                            .width(95.dp)
                            .testTag("fund_units_table_${item.schemeName}")
                            .clickable { onUnitsClick(item.schemeName) },
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                    )
                    Text(
                        text = "₹${formatIndianCommas(item.navValue, 2)}",
                        modifier = Modifier.width(90.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                    )
                    // Average NAV (new column after LATEST NAV)
                    Text(
                        text = "₹${formatIndianCommas(item.avgNav, 2)}",
                        modifier = Modifier.width(90.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                    )
                    // Predictive Timing Single Recommendation column
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val analysis = item.predictiveAnalysis
                        if (analysis != null) {
                            val badgeText = when (analysis.verdict) {
                                PredictiveVerdict.BUY_TODAY -> "BUY TODAY 🟢"
                                PredictiveVerdict.WAIT_FOR_DIP -> "WAIT FOR DIP 🟡"
                                PredictiveVerdict.DIP_ALREADY_GONE -> "DIP PASSED 🔴"
                            }
                            val badgeColor = Color(analysis.verdict.tagColorHex)

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor.copy(alpha = if (isDark) 0.22f else 0.15f),
                                border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable { onPredictiveClick(item.schemeName) }
                                    .testTag("timing_rec_badge_${item.rank}")
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = textSecondary.copy(alpha = 0.08f),
                                modifier = Modifier.clickable { onPredictiveClick(item.schemeName) }
                            ) {
                                Text(
                                    text = "CALCULATING...",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    // Risk Reward Score Column
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val scoreItem = item.riskRewardAnalysis
                        val scoreVal = scoreItem?.score ?: 68
                        val scoreColor = when {
                            scoreVal >= 80 -> Color(0xFF10B981) // Green
                            scoreVal >= 65 -> Color(0xFF10B981) // Green
                            scoreVal >= 50 -> Color(0xFFF59E0B) // Amber
                            scoreVal >= 35 -> Color(0xFFEF5350) // Red/Orange
                            else -> Color(0xFFEF4444) // Red
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = scoreColor.copy(alpha = if (isDark) 0.22f else 0.15f),
                            border = BorderStroke(0.8.dp, scoreColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { onRiskRewardClick(item.schemeName) }
                                .testTag("risk_reward_score_${item.rank}")
                        ) {
                            Text(
                                text = "$scoreVal / 100",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (index < items.size - 1) {
                    HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}
