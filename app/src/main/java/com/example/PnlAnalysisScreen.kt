package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.pow

enum class PnlFundFilter {
    QSIF_ONLY,
    OTHER_ONLY,
    ALL
}

data class LifetimePortfolioPnlSummary(
    val totalPurchases: Double = 0.0,
    val totalRedemptions: Double = 0.0,
    val totalCurrentValue: Double = 0.0,
    val totalCurrentHoldingCost: Double = 0.0,
    val totalRealizedPnL: Double = 0.0,
    val totalUnrealizedPnL: Double = 0.0,
    val totalUnrealizedPnLPct: Double = 0.0,
    val totalAbsolutePnL: Double = 0.0,
    val totalAbsolutePnLPct: Double = 0.0,
    val lifetimeXirr: Double = 0.0,
    val holdingXirr: Double = 0.0,
    val activeFundsCount: Int = 0,
    val totalFundsCount: Int = 0
)

fun isQsifScheme(schemeName: String): Boolean {
    val s = schemeName.lowercase(Locale.ENGLISH)
    return s.contains("equity long-short") ||
           s.contains("ex-top 100") ||
           s.contains("sector rotation") ||
           s.contains("active asset allocator") ||
           s.contains("hybrid long-short") ||
           s.contains("income plus") ||
           s.contains("arbitrage active fof") ||
           s.contains("qsif") ||
           s.contains("quant sif")
}

fun getQsifFundDisplayOrder(schemeName: String): Int {
    val lower = schemeName.lowercase(Locale.ENGLISH)
    return when {
        lower.contains("asset allocator") || lower.contains("active asset") || lower.contains("allocator") -> 1
        lower.contains("ex-top") || lower.contains("ex top") || lower.contains("small") || lower.contains("mid") || lower.contains("extop") -> 2
        lower.contains("sector") || lower.contains("rotation") -> 3
        lower.contains("hybrid") -> 4
        lower.contains("equity long-short") || lower.contains("equity long short") -> 5
        lower.contains("income plus") || lower.contains("arbitrage active fof") -> 6
        else -> 7
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PnlAnalysisDialog(
    viewModel: NavViewModel,
    onDismiss: () -> Unit,
    onUploadCasClick: () -> Unit
) {
    val context = LocalContext.current
    val allTxnsState = viewModel.getAllCasTransactions().collectAsStateWithLifecycle(initialValue = null)
    val txns = allTxnsState.value ?: emptyList()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val isPnlLoading = (allTxnsState.value == null) || (uiState is UiState.Loading)

    val currentFunds = remember(uiState) {
        if (uiState is UiState.Success) {
            (uiState as UiState.Success).items
        } else {
            emptyList()
        }
    }

    val valuationDate = remember { ReturnsCalculations.getValuationDate() }
    
    val fundSummaries = remember(txns, currentFunds) {
        if (txns.isEmpty() && currentFunds.isEmpty()) emptyList()
        else {
            val grouped = txns.groupBy { it.schemeName }
            val list = grouped.map { (schemeName, schemeTxns) ->
                val matchedFund = currentFunds.find { 
                    ReturnsCalculations.isSameScheme(it.schemeName, schemeName) 
                }
                
                val latestNav = if (matchedFund != null && matchedFund.fund.navValue > 0) {
                    matchedFund.fund.navValue
                } else {
                    schemeTxns.lastOrNull { it.nav > 0 }?.nav ?: 0.0
                }
                
                val navDate = matchedFund?.fund?.navDate ?: schemeTxns.lastOrNull()?.date ?: "Till Date"
                
                ReturnsCalculations.calculateLifetimePnl(
                    schemeName = schemeName,
                    txns = schemeTxns,
                    latestNav = latestNav,
                    navDate = navDate,
                    valuationDate = valuationDate,
                    overrideCostOfCurrentHolding = matchedFund?.investment?.investedAmount
                )
            }.toMutableList()

            // Ensure all 5 QSIF funds (including Sector Rotation and Hybrid Long-Short) from currentFunds are represented
            for (cf in currentFunds) {
                if (isQsifScheme(cf.schemeName) && list.none { ReturnsCalculations.isSameScheme(it.schemeName, cf.schemeName) }) {
                    val inv = cf.investment
                    val units = inv?.unitsHeld ?: 0.0
                    val cost = inv?.investedAmount ?: 0.0
                    val nav = if (cf.fund.navValue > 0) cf.fund.navValue else 100.0
                    val navDate = cf.fund.navDate.ifEmpty { "Till Date" }
                    val currVal = if (units > 0) units * nav else cost
                    val syntheticTxns = if (units > 0.0001 && cost > 0.0) {
                        listOf(
                            CasTransactionEntity(
                                id = 0,
                                schemeName = cf.schemeName,
                                date = navDate,
                                description = "Opening Portfolio Balance",
                                amount = cost,
                                units = units,
                                nav = if (units > 0) cost / units else nav,
                                balance = units,
                                type = "Buy"
                            )
                        )
                    } else emptyList()

                    list.add(
                        ReturnsCalculations.calculateLifetimePnl(
                            schemeName = cf.schemeName,
                            txns = syntheticTxns,
                            latestNav = nav,
                            navDate = navDate,
                            valuationDate = valuationDate,
                            overrideCostOfCurrentHolding = cost
                        )
                    )
                }
            }
            list.filter { it.isQsif }.sortedBy { getQsifFundDisplayOrder(it.schemeName) }
        }
    }

    val portfolioSummary = remember(fundSummaries, valuationDate) {
        if (fundSummaries.isEmpty()) null
        else {
            val totalPurchases = fundSummaries.sumOf { it.totalPurchases }
            val totalRedemptions = fundSummaries.sumOf { it.totalRedemptions }
            val totalVal = fundSummaries.sumOf { it.currentValue }
            val totalCurrentHoldingCost = fundSummaries.sumOf { it.costOfCurrentHolding }
            val totalRealized = fundSummaries.sumOf { it.realizedPnL }
            val totalUnrealized = fundSummaries.sumOf { it.unrealizedPnL }
            val totalUnrealizedPct = if (totalCurrentHoldingCost > 0) (totalUnrealized / totalCurrentHoldingCost) * 100.0 else 0.0
            val totalAbsPnL = totalRealized + totalUnrealized
            val totalAbsPnLPct = if (totalPurchases > 0) (totalAbsPnL / totalPurchases) * 100.0 else 0.0
            val activeCount = fundSummaries.count { !it.isFullyExited }
            val totalCount = fundSummaries.size

            val combinedCashflows = mutableListOf<Pair<Long, Double>>()
            val combinedHoldingCashflows = mutableListOf<Pair<Long, Double>>()
            for (item in fundSummaries) {
                val itemNavDate = try { SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).parse(item.navDate) } catch(e: Exception) { null } ?: valuationDate
                combinedCashflows.addAll(item.rawCashflows)
                if (item.currentUnits > 0.0001 && item.currentValue > 0) {
                    combinedCashflows.add(itemNavDate.time to +item.currentValue)
                }
                for (lot in item.activeLots) {
                    combinedHoldingCashflows.add(lot.date.time to -(lot.units * lot.costPerUnit))
                }
                if (item.currentUnits > 0.0001 && item.currentValue > 0) {
                    combinedHoldingCashflows.add(itemNavDate.time to +item.currentValue)
                }
            }
            
            val lifetimeXirr = if (combinedCashflows.isNotEmpty() && totalPurchases > 0) {
                val res = ReturnsCalculations.trueXirr(combinedCashflows)
                if (res.isNaN() || res.isInfinite()) 0.0 else res
            } else {
                0.0
            }

            val holdingXirr = if (combinedHoldingCashflows.isNotEmpty() && totalCurrentHoldingCost > 0) {
                val res = ReturnsCalculations.trueXirr(combinedHoldingCashflows)
                if (res.isNaN() || res.isInfinite()) 0.0 else res
            } else {
                0.0
            }

            LifetimePortfolioPnlSummary(
                totalPurchases = totalPurchases,
                totalRedemptions = totalRedemptions,
                totalCurrentValue = totalVal,
                totalCurrentHoldingCost = totalCurrentHoldingCost,
                totalRealizedPnL = totalRealized,
                totalUnrealizedPnL = totalUnrealized,
                totalUnrealizedPnLPct = totalUnrealizedPct,
                totalAbsolutePnL = totalAbsPnL,
                totalAbsolutePnLPct = totalAbsPnLPct,
                lifetimeXirr = lifetimeXirr,
                holdingXirr = holdingXirr,
                activeFundsCount = activeCount,
                totalFundsCount = totalCount
            )
        }
    }

    val defaultStartDateMillis = remember {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.APRIL, 23, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    var selectedStartDateMillis by remember { mutableStateOf(defaultStartDateMillis) }
    var isLoadingRiskData by remember { mutableStateOf(true) }
    var riskMetricsList by remember { mutableStateOf<List<FundRiskMetrics>>(emptyList()) }
    var rebalancingAnalysisList by remember { mutableStateOf<List<FundRebalancingAnalysis>>(emptyList()) }

    LaunchedEffect(selectedStartDateMillis, fundSummaries) {
        if (fundSummaries.isNotEmpty()) {
            isLoadingRiskData = true
            withContext(Dispatchers.Default) {
                val db = AppDatabase.getDatabase(context)
                
                // Include all QSIF funds (Active Asset Allocator, Equity Ex-Top 100, Sector Rotation, Hybrid Long-Short, Equity Long-Short)
                val eligibleFundSummaries = fundSummaries.sortedBy { getQsifFundDisplayOrder(it.schemeName) }
                val totalPortfolioValue = eligibleFundSummaries.sumOf { it.currentValue }

                val navPointsMap = mutableMapOf<String, List<Pair<Double, Long>>>()
                val fullNavPointsMap = mutableMapOf<String, List<Pair<Double, Long>>>()

                val metrics = eligibleFundSummaries.map { fundSummary ->
                    val schemeName = fundSummary.schemeName
                    var records = NavCache.getRecords(schemeName)
                    if (records.isEmpty()) {
                        var dbRecords = db.fundDao().getHistoricalNavs(schemeName)
                        if (dbRecords.isEmpty()) {
                            val allNavs = db.fundDao().getAllHistoricalNavsDirect()
                            dbRecords = allNavs.filter { 
                                it.schemeName.contains(schemeName, ignoreCase = true) || 
                                schemeName.contains(it.schemeName, ignoreCase = true) 
                            }
                        }
                        if (dbRecords.isNotEmpty()) {
                            records = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                            NavCache.addAll(schemeName, records)
                        }
                    }

                    val mappedNavs = records.mapNotNull { rec ->
                        val d = parseDateAnyLocal(rec.dateStr)
                        if (d != null) Pair(rec.nav, d.time) else null
                    }.sortedBy { it.second }

                    fullNavPointsMap[schemeName] = mappedNavs

                    val filteredNavs = mappedNavs.filter { it.second >= selectedStartDateMillis }
                    val navPoints = if (filteredNavs.size >= 2) filteredNavs else mappedNavs
                    navPointsMap[schemeName] = navPoints

                    computeFundRiskMetrics(
                        schemeName = schemeName,
                        currentValue = fundSummary.currentValue,
                        totalPurchases = fundSummary.totalPurchases,
                        realizedPnL = fundSummary.realizedPnL,
                        totalPortfolioValue = totalPortfolioValue,
                        evaluationNavPoints = navPoints,
                        fullNavPoints = mappedNavs,
                        evaluationStartDateMillis = selectedStartDateMillis
                    )
                }
                riskMetricsList = metrics.sortedBy { getQsifFundDisplayOrder(it.schemeName) }

                val rebalList = eligibleFundSummaries.flatMap { fundSummary ->
                    val navPoints = fullNavPointsMap[fundSummary.schemeName] ?: emptyList()
                    val schemeTxns = txns.filter { 
                        it.schemeName.contains(fundSummary.schemeName, ignoreCase = true) || 
                        fundSummary.schemeName.contains(it.schemeName, ignoreCase = true) 
                    }
                    computeFundRebalancingAnalysis(
                        schemeName = fundSummary.schemeName,
                        currentValue = fundSummary.currentValue,
                        totalPortfolioValue = totalPortfolioValue,
                        realizedPnL = fundSummary.realizedPnL,
                        navPoints = navPoints,
                        schemeTxns = schemeTxns,
                        planStartDateMillis = selectedStartDateMillis,
                        costOfCurrentHolding = fundSummary.costOfCurrentHolding,
                        currentUnits = fundSummary.currentUnits
                    )
                }
                rebalancingAnalysisList = rebalList.sortedBy { getQsifFundDisplayOrder(it.schemeName) }

                isLoadingRiskData = false
            }
        } else {
            isLoadingRiskData = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "QSIF Profit & Loss Analysis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Realized & Unrealized Performance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close PnL Dialog")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (txns.isEmpty() || fundSummaries.isEmpty()) {
                                    Toast.makeText(context, "No CAS transaction data available to export.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Exporting QSIF P&L Analysis Report...", Toast.LENGTH_SHORT).show()
                                    exportPnlAnalysisReport(
                                        context = context,
                                        portfolioSummary = portfolioSummary,
                                        fundSummaries = fundSummaries,
                                        riskMetricsList = riskMetricsList,
                                        rebalancingAnalysisList = rebalancingAnalysisList,
                                        txns = txns,
                                        selectedStartDateMillis = selectedStartDateMillis
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Download P&L Analysis Validation Report",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onUploadCasClick) {
                            Icon(Icons.Default.Add, contentDescription = "Upload CAS", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )

                if (isPnlLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (txns.isEmpty()) {
                    // Empty State Card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No CAS Data Uploaded",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Upload your CAS Excel statement to view QSIF Profit & Loss analysis, Realized Gains, Unrealized Gains, and XIRR.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onUploadCasClick()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload CAS File Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    var selectedPnlTab by remember { mutableStateOf(0) } // 0 = P&L Analysis, 1 = P&L Risk Analysis

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Horizontal Tabs
                        TabRow(
                            selectedTabIndex = selectedPnlTab,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = selectedPnlTab == 0,
                                onClick = { selectedPnlTab = 0 },
                                text = { Text("P&L Analysis", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedPnlTab == 1,
                                onClick = { selectedPnlTab = 1 },
                                text = { Text("P&L Risk Analysis", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (selectedPnlTab == 0) {
                            // LazyColumn for overall summary and fund lists
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 160.dp)
                            ) {
                                item {
                                    // Lifetime Portfolio Summary Card
                                    if (portfolioSummary != null) {
                                        LifetimePortfolioPnlCard(portfolioSummary)
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "QSIF Funds Breakdown (${fundSummaries.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        val dateStr = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date()) }
                                        Text(
                                            text = "As of $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (fundSummaries.isEmpty()) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "No QSIF fund transactions found in CAS data.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(
                                        items = fundSummaries,
                                        key = { it.schemeName },
                                        contentType = { "lifetime_fund_pnl" }
                                    ) { fundSummary ->
                                        LifetimeFundPnlCard(fundSummary)
                                    }
                                }
                            }
                        } else {
                            PnlRiskAnalysisContent(
                                fundSummaries = fundSummaries,
                                portfolioSummary = portfolioSummary ?: LifetimePortfolioPnlSummary(),
                                txns = txns,
                                selectedStartDateMillis = selectedStartDateMillis,
                                onStartDateChanged = { selectedStartDateMillis = it },
                                riskMetricsList = riskMetricsList,
                                rebalancingAnalysisList = rebalancingAnalysisList,
                                isLoading = isLoadingRiskData,
                                defaultStartDateMillis = defaultStartDateMillis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LifetimePortfolioPnlCard(summary: LifetimePortfolioPnlSummary) {
    val currencyFmt = remember { DecimalFormat("#,##,##0.00") }
    val pctFmt = remember { DecimalFormat("+0.00;-0.00") }
    
    val isLifeProfit = summary.totalAbsolutePnL >= 0
    val lifeColor = if (isLifeProfit) Color(0xFF10B981) else Color(0xFFEF4444)

    val isHoldingProfit = summary.totalUnrealizedPnL >= 0
    val holdingColor = if (isHoldingProfit) Color(0xFF10B981) else Color(0xFFEF4444)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "QSIF PORTFOLIO PERFORMANCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${summary.activeFundsCount} Active / ${summary.totalFundsCount} Total Schemes",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Engine Direct",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Two-part structured view: 1. Current Holding, 2. Lifetime Performance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Current Holding Column
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    border = BorderStroke(0.5.dp, holdingColor.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "CURRENT HOLDING",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${currencyFmt.format(summary.totalCurrentValue)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (isHoldingProfit) "+" else ""}₹${currencyFmt.format(summary.totalUnrealizedPnL)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = holdingColor
                            )
                            Text(
                                text = "(${pctFmt.format(summary.totalUnrealizedPnLPct)}%)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = holdingColor
                            )
                        }
                        Text(
                            text = "Holding XIRR: ${pctFmt.format(summary.holdingXirr)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = holdingColor
                        )
                    }
                }

                // Lifetime Performance Column
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    border = BorderStroke(0.5.dp, lifeColor.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "LIFETIME PERFORMANCE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (isLifeProfit) "+" else ""}₹${currencyFmt.format(summary.totalAbsolutePnL)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = lifeColor
                        )
                        Text(
                            text = "Realized: ₹${currencyFmt.format(summary.totalRealizedPnL)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Lifetime XIRR: ${pctFmt.format(summary.lifetimeXirr)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = lifeColor
                        )
                    }
                }
            }

            // Compact Cashflow Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Invested: ₹${currencyFmt.format(summary.totalPurchases)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Redeemed: ₹${currencyFmt.format(summary.totalRedemptions)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LifetimeFundPnlCard(summary: ReturnsCalculations.LifetimeFundPnl) {
    val currencyFmt = remember { DecimalFormat("#,##,##0.00") }
    val pctFmt = remember { DecimalFormat("+0.00;-0.00") }
    val unitsFmt = remember { DecimalFormat("#,##0.000") }
    val navFmt = remember { DecimalFormat("#,##0.0000") }
    
    var isExpanded by remember { mutableStateOf(false) }

    val isLifeProfit = summary.totalAbsolutePnL >= 0
    val lifeColor = if (isLifeProfit) Color(0xFF10B981) else Color(0xFFEF4444)

    val isHoldingProfit = summary.unrealizedPnL >= 0
    val holdingColor = if (isHoldingProfit) Color(0xFF10B981) else Color(0xFFEF4444)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Scheme Name & Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.schemeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (summary.isQsif) "QSIF Direct" else "External",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (summary.isFullyExited) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "Exited",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "${unitsFmt.format(summary.currentUnits)} un @ ₹${navFmt.format(summary.latestNav)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Two-part structured view: Current Holding vs Lifetime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Current Holding Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(0.5.dp, holdingColor.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "CURRENT HOLDING",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${currencyFmt.format(summary.currentValue)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (isHoldingProfit) "+" else ""}₹${currencyFmt.format(summary.unrealizedPnL)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = holdingColor
                            )
                            Text(
                                text = "(${pctFmt.format(summary.unrealizedPnLPct)}%)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = holdingColor
                            )
                        }
                        Text(
                            text = "Holding XIRR: ${pctFmt.format(summary.holdingXirr)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = holdingColor
                        )
                    }
                }

                // Lifetime Performance Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(0.5.dp, lifeColor.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "LIFETIME PERFORMANCE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (isLifeProfit) "+" else ""}₹${currencyFmt.format(summary.totalAbsolutePnL)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = lifeColor
                        )
                        Text(
                            text = "Realized: ₹${currencyFmt.format(summary.realizedPnL)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Lifetime XIRR: ${pctFmt.format(summary.lifetimeXirr)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = lifeColor
                        )
                    }
                }
            }

            // Compact Cashflow Footer & Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Invested: ₹${currencyFmt.format(summary.totalPurchases)} | Redeemed: ₹${currencyFmt.format(summary.totalRedemptions)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide Lots" else "Txns (${summary.decoratedTxns.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable Drawer Toggle Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "Hide Transaction Details" else "View Full Transaction History & FIFO Lots (${summary.decoratedTxns.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expandable Section: Transaction Log & Lots
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = "COMPLETE TRANSACTION HISTORY",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    summary.decoratedTxns.forEachIndexed { idx, dec ->
                        val t = dec.txn
                        val isBuy = t.type == "Buy" || (t.units > 0 && t.amount > 0 && t.type != "Sell")
                        val typeColor = if (isBuy) Color(0xFF10B981) else Color(0xFFEF4444)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = typeColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (isBuy) "BUY" else "SELL",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = typeColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(
                                        text = t.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${unitsFmt.format(kotlin.math.abs(t.units))} u @ ₹${navFmt.format(t.nav)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${currencyFmt.format(kotlin.math.abs(t.amount))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!isBuy && dec.capitalGain != 0.0) {
                                    val cgCol = if (dec.capitalGain >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                    Text(
                                        text = "Gain: ${if (dec.capitalGain >= 0) "+" else ""}₹${currencyFmt.format(dec.capitalGain)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = cgCol
                                    )
                                }
                            }
                        }
                        if (idx < summary.decoratedTxns.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

data class FundRiskMetrics(
    val schemeName: String,
    val currentValue: Double,
    val totalPurchases: Double,
    val realizedPnL: Double,
    val portfolioWeightPct: Double,
    val churnLossRatioPct: Double,
    val annualizedVolatilityPct: Double,
    val cagrPct: Double,
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val downsideDevPct: Double,
    val maxDrawdownPct: Double,
    val maxDdPeakDate: String,
    val maxDdTroughDate: String,
    val daysToTrough: Int,
    val daysToRecovery: Int,
    val rollingMeanPct: Double,
    val rollingMedianPct: Double,
    val rollingStdDevPct: Double,
    val rollingMinPct: Double,
    val rollingMaxPct: Double,
    val percentNegativeWindows: Double,
    val returnRiskRatio: Double,
    val isConcentrationFlagged: Boolean,
    val movingAverageNote: String = ""
)

private fun parseDateAnyLocal(dateStr: String): Date? {
    val formats = listOf("dd-MMM-yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss")
    for (f in formats) {
        try {
            val sdf = SimpleDateFormat(f, Locale.ENGLISH)
            val d = sdf.parse(dateStr)
            if (d != null) return d
        } catch (e: Exception) {}
    }
    return null
}

private fun computeFundRiskMetrics(
    schemeName: String,
    currentValue: Double,
    totalPurchases: Double,
    realizedPnL: Double,
    totalPortfolioValue: Double,
    evaluationNavPoints: List<Pair<Double, Long>>,
    fullNavPoints: List<Pair<Double, Long>>,
    evaluationStartDateMillis: Long
): FundRiskMetrics {
    val portfolioWeightPct = if (totalPortfolioValue > 0) (currentValue / totalPortfolioValue) * 100.0 else 0.0
    val churnLossRatioPct = if (totalPurchases > 0) (realizedPnL / totalPurchases) * 100.0 else 0.0
    val isConcentrationFlagged = portfolioWeightPct > 50.0

    if (evaluationNavPoints.size < 2) {
        return FundRiskMetrics(
            schemeName = schemeName,
            currentValue = currentValue,
            totalPurchases = totalPurchases,
            realizedPnL = realizedPnL,
            portfolioWeightPct = portfolioWeightPct,
            churnLossRatioPct = churnLossRatioPct,
            annualizedVolatilityPct = 0.0,
            cagrPct = 0.0,
            sharpeRatio = 0.0,
            sortinoRatio = 0.0,
            downsideDevPct = 0.0,
            maxDrawdownPct = 0.0,
            maxDdPeakDate = "-",
            maxDdTroughDate = "-",
            daysToTrough = 0,
            daysToRecovery = 0,
            rollingMeanPct = 0.0,
            rollingMedianPct = 0.0,
            rollingStdDevPct = 0.0,
            rollingMinPct = 0.0,
            rollingMaxPct = 0.0,
            percentNegativeWindows = 0.0,
            returnRiskRatio = 0.0,
            isConcentrationFlagged = isConcentrationFlagged
        )
    }

    // 1. Daily Returns (Based on Evaluation Period)
    val dailyReturns = mutableListOf<Double>()
    for (i in 1 until evaluationNavPoints.size) {
        val prevNav = evaluationNavPoints[i - 1].first
        val currNav = evaluationNavPoints[i].first
        if (prevNav > 0) {
            dailyReturns.add((currNav / prevNav) - 1.0)
        }
    }

    // 2. Annualized Volatility
    val meanDailyReturn = if (dailyReturns.isNotEmpty()) dailyReturns.average() else 0.0
    val varianceDaily = if (dailyReturns.size > 1) {
        dailyReturns.sumOf { (it - meanDailyReturn) * (it - meanDailyReturn) } / dailyReturns.size
    } else 0.0
    val dailyVol = kotlin.math.sqrt(varianceDaily)
    val annualizedVolPct = dailyVol * kotlin.math.sqrt(252.0) * 100.0

    // 3. Downside Deviation
    val negativeReturns = dailyReturns.filter { it < 0.0 }
    val downsideVariance = if (negativeReturns.isNotEmpty()) {
        negativeReturns.sumOf { it * it } / dailyReturns.size
    } else 0.0
    val downsideDailyVol = kotlin.math.sqrt(downsideVariance)
    val downsideDevPct = downsideDailyVol * kotlin.math.sqrt(252.0) * 100.0

    // 4. CAGR (Based on Evaluation Period)
    val startNav = evaluationNavPoints.first().first
    val endNav = evaluationNavPoints.last().first
    val daysDiff = ((evaluationNavPoints.last().second - evaluationNavPoints.first().second) / (1000L * 60 * 60 * 24)).toDouble()
    val cagrPct = if (daysDiff > 0 && startNav > 0 && endNav > 0) {
        ((endNav / startNav).pow(365.25 / daysDiff) - 1.0) * 100.0
    } else 0.0

    // Risk free rate = 6.5%
    val riskFreeRatePct = 6.5
    val sharpeRatio = if (annualizedVolPct > 0) (cagrPct - riskFreeRatePct) / annualizedVolPct else 0.0
    val sortinoRatio = if (downsideDevPct > 0) (cagrPct - riskFreeRatePct) / downsideDevPct else 0.0

    // 5. Max Drawdown & Duration (Within Evaluation Period)
    var peak = evaluationNavPoints.first().first
    var peakTime = evaluationNavPoints.first().second
    var maxDdPct = 0.0
    var troughTime = evaluationNavPoints.first().second
    var peakTimeForMaxDd = evaluationNavPoints.first().second

    val dateFmt = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)

    for (pt in evaluationNavPoints) {
        if (pt.first > peak) {
            peak = pt.first
            peakTime = pt.second
        } else if (peak > 0) {
            val dd = ((pt.first - peak) / peak) * 100.0
            if (dd < maxDdPct) {
                maxDdPct = dd
                troughTime = pt.second
                peakTimeForMaxDd = peakTime
            }
        }
    }

    val daysToTrough = ((troughTime - peakTimeForMaxDd) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    val daysToRecovery = ((evaluationNavPoints.last().second - troughTime) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)

    // 6. 1-Month (30-day) Rolling Returns (Uses FULL historical data for lookup)
    val rollingReturns = mutableListOf<Double>()
    val daysWindow = 30L
    val windowMillis = daysWindow * 24 * 60 * 60 * 1000L

    var startIndex = 0
    // We iterate over ALL points but only record those ending in/after the evaluation period
    for (endIndex in fullNavPoints.indices) {
        val endPoint = fullNavPoints[endIndex]
        val endPointTime = endPoint.second
        
        // Skip points before evaluation start date
        if (endPointTime < evaluationStartDateMillis) continue
        
        val targetTime = endPointTime - windowMillis
        
        // Advance startIndex to find the latest point at or before targetTime
        while (startIndex < endIndex && fullNavPoints[startIndex + 1].second <= targetTime) {
            startIndex++
        }
        
        val startPoint = fullNavPoints[startIndex]
        // Strict window requirement: must be within a reasonable gap of exactly 30 days
        if (startPoint.second <= targetTime && endPointTime > startPoint.second) {
            val actualDays = (endPointTime - startPoint.second) / (1000L * 60 * 60 * 24)
            // Allow 5 days of wiggle room for weekend/holiday gaps
            if (Math.abs(actualDays - daysWindow) <= 5) {
                val rollRet = ((endPoint.first / startPoint.first) - 1.0) * 100.0
                if (!rollRet.isNaN() && !rollRet.isInfinite()) {
                    rollingReturns.add(rollRet)
                }
            }
        }
    }

    val rollingMeanPct = if (rollingReturns.isNotEmpty()) rollingReturns.average() else 0.0
    val rollingSorted = rollingReturns.sorted()
    val rollingMedianPct = if (rollingSorted.isNotEmpty()) {
        val mid = rollingSorted.size / 2
        if (rollingSorted.size % 2 == 0) (rollingSorted[mid - 1] + rollingSorted[mid]) / 2.0 else rollingSorted[mid]
    } else 0.0

    val rollingVariance = if (rollingReturns.size > 1) {
        rollingReturns.sumOf { (it - rollingMeanPct) * (it - rollingMeanPct) } / rollingReturns.size
    } else 0.0
    val rollingStdDevPct = kotlin.math.sqrt(rollingVariance)

    val rollingMinPct = rollingSorted.firstOrNull() ?: 0.0
    val rollingMaxPct = rollingSorted.lastOrNull() ?: 0.0

    val negativeCount = rollingReturns.count { it < 0.0 }
    val percentNegativeWindows = if (rollingReturns.isNotEmpty()) (negativeCount.toDouble() / rollingReturns.size) * 100.0 else 0.0

    val returnRiskRatio = if (rollingStdDevPct > 0) rollingMeanPct / rollingStdDevPct else 0.0

    val navsList = evaluationNavPoints.map { it.first }
    val dma20 = if (navsList.size >= 20) navsList.takeLast(20).average() else navsList.lastOrNull() ?: 0.0
    val dma50 = if (navsList.size >= 50) navsList.takeLast(50).average() else navsList.lastOrNull() ?: 0.0
    val d20Pct = if (dma20 > 0.0) ((navsList.lastOrNull() ?: 0.0 - dma20) / dma20) * 100.0 else 0.0
    val d50Pct = if (dma50 > 0.0) ((navsList.lastOrNull() ?: 0.0 - dma50) / dma50) * 100.0 else 0.0
    val numFmtMa = DecimalFormat("0.00")
    val maNote = "• Moving Average: 20-DMA ₹${numFmtMa.format(dma20)} (${if (d20Pct >= 0) "+" else ""}${numFmtMa.format(d20Pct)}% vs NAV), 50-DMA ₹${numFmtMa.format(dma50)} (${if (d50Pct >= 0) "+" else ""}${numFmtMa.format(d50Pct)}% vs NAV)."

    return FundRiskMetrics(
        schemeName = schemeName,
        currentValue = currentValue,
        totalPurchases = totalPurchases,
        realizedPnL = realizedPnL,
        portfolioWeightPct = portfolioWeightPct,
        churnLossRatioPct = churnLossRatioPct,
        annualizedVolatilityPct = annualizedVolPct,
        cagrPct = cagrPct,
        sharpeRatio = sharpeRatio,
        sortinoRatio = sortinoRatio,
        downsideDevPct = downsideDevPct,
        maxDrawdownPct = maxDdPct,
        maxDdPeakDate = dateFmt.format(Date(peakTimeForMaxDd)),
        maxDdTroughDate = dateFmt.format(Date(troughTime)),
        daysToTrough = daysToTrough,
        daysToRecovery = daysToRecovery,
        rollingMeanPct = rollingMeanPct,
        rollingMedianPct = rollingMedianPct,
        rollingStdDevPct = rollingStdDevPct,
        rollingMinPct = rollingMinPct,
        rollingMaxPct = rollingMaxPct,
        percentNegativeWindows = percentNegativeWindows,
        returnRiskRatio = returnRiskRatio,
        isConcentrationFlagged = isConcentrationFlagged,
        movingAverageNote = maNote
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PnlRiskAnalysisContent(
    fundSummaries: List<ReturnsCalculations.LifetimeFundPnl>,
    portfolioSummary: LifetimePortfolioPnlSummary,
    txns: List<CasTransactionEntity>,
    selectedStartDateMillis: Long,
    onStartDateChanged: (Long) -> Unit,
    riskMetricsList: List<FundRiskMetrics>,
    rebalancingAnalysisList: List<FundRebalancingAnalysis>,
    isLoading: Boolean,
    defaultStartDateMillis: Long
) {
    val context = LocalContext.current
    val numFmt = remember { DecimalFormat("0.00") }
    val currFmt = remember { DecimalFormat("#,##,##0.00") }
    val dateDisplayFmt = remember { SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 160.dp)
    ) {
        // 1. Start Date Selector Card with Calendar Icon
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apples-to-Apples Evaluation Window",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Start Date: ${dateDisplayFmt.format(Date(selectedStartDateMillis))}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedStartDateMillis != defaultStartDateMillis) {
                            TextButton(
                                onClick = { onStartDateChanged(defaultStartDateMillis) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Reset (23-Apr-2026)", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = selectedStartDateMillis
                                val dpd = android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selCal = Calendar.getInstance()
                                        selCal.set(year, month, dayOfMonth, 0, 0, 0)
                                        selCal.set(Calendar.MILLISECOND, 0)
                                        onStartDateChanged(selCal.timeInMillis)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                )
                                dpd.show()
                            }
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Select Start Date",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (riskMetricsList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No fund data available for risk analysis.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            // 1. Deterministic Rules-Based Rebalancing Engine Section
            item {
                RulesBasedRebalancingEngineSection(
                    rebalancingAnalysisList = rebalancingAnalysisList,
                    currFmt = currFmt,
                    numFmt = numFmt,
                    txns = txns,
                    planStartDateMillis = selectedStartDateMillis
                )
            }

            // 2. Financial Advisor Executive Recommendations Card
            item {
                FinancialAdvisorRecommendationsCard(
                    metricsList = riskMetricsList,
                    currFmt = currFmt,
                    numFmt = numFmt
                )
            }

            // 3. Apples-to-Apples Summary Comparison Table
            item {
                Text(
                    text = "Apples-to-Apples Fund Risk Comparison Table",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                RiskMetricsComparisonTable(
                    metricsList = riskMetricsList,
                    numFmt = numFmt
                )
            }

            // 4. Individual Detailed Fund Risk Cards
            item {
                Text(
                    text = "Detailed Fund Risk Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            items(riskMetricsList, key = { it.schemeName }) { metrics ->
                DetailedFundRiskCard(metrics = metrics, numFmt = numFmt, currFmt = currFmt)
            }
        }
    }
}

@Composable
fun FinancialAdvisorRecommendationsCard(
    metricsList: List<FundRiskMetrics>,
    currFmt: DecimalFormat,
    numFmt: DecimalFormat
) {
    val topSharpeFund = metricsList.maxByOrNull { it.sharpeRatio }
    val concentrationAlerts = metricsList.filter { it.isConcentrationFlagged }
    val churnLossAlerts = metricsList.filter { it.realizedPnL < 0 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "FINANCIAL ADVISOR RECOMMENDATIONS & INSIGHTS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Portfolio Concentration Alert
            if (concentrationAlerts.isNotEmpty()) {
                concentrationAlerts.forEach { fund ->
                    AdvisorInsightRow(
                        icon = Icons.Default.Warning,
                        iconColor = Color(0xFFEF4444),
                        title = "High Portfolio Concentration Alert (>50%)",
                        description = "${fund.schemeName} accounts for ${numFmt.format(fund.portfolioWeightPct)}% of your total current portfolio value. Concentrating over 50% in a single strategy increases systemic risk. Rebalancing across other QSIF schemes is strongly recommended."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                AdvisorInsightRow(
                    icon = Icons.Default.CheckCircle,
                    iconColor = Color(0xFF10B981),
                    title = "Balanced Portfolio Allocation",
                    description = "No single QSIF strategy exceeds the 50% portfolio weight threshold. Capital is well distributed across active funds."
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2. Churn Loss Alert
            if (churnLossAlerts.isNotEmpty()) {
                churnLossAlerts.forEach { fund ->
                    AdvisorInsightRow(
                        icon = Icons.Default.Warning,
                        iconColor = Color(0xFFF59E0B),
                        title = "Realized Churn Loss Exposure",
                        description = "${fund.schemeName} shows a Realized Loss of ₹${currFmt.format(kotlin.math.abs(fund.realizedPnL))} (${numFmt.format(fund.churnLossRatioPct)}% churn loss ratio) caused by buy/sell transaction timing, separate from unrealized paper gains."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 3. Sharpe & Sortino Winner
            if (topSharpeFund != null) {
                AdvisorInsightRow(
                    icon = Icons.Default.Star,
                    iconColor = Color(0xFF8B5CF6),
                    title = "Best Risk-Adjusted Return Strategy",
                    description = "${topSharpeFund.schemeName} leads with a Sharpe Ratio of ${numFmt.format(topSharpeFund.sharpeRatio)} and Sortino Ratio of ${numFmt.format(topSharpeFund.sortinoRatio)}. Sortino isolates downside drops from upside volatility, confirming superior return per unit of downside risk."
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4. Downside Risk & Rolling Windows
            val bestRolling = metricsList.minByOrNull { it.percentNegativeWindows }
            if (bestRolling != null) {
                AdvisorInsightRow(
                    icon = Icons.Default.CheckCircle,
                    iconColor = Color(0xFF3B82F6),
                    title = "Rolling Window Consistency & Downside Risk",
                    description = "${bestRolling.schemeName} recorded ${numFmt.format(bestRolling.percentNegativeWindows)}% negative 30-day rolling windows, exhibiting consistent performance across varying market cycles."
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4.5 Core Compounding Mandate (Active Asset Allocator)
            val coreMetric = metricsList.firstOrNull { 
                val lower = it.schemeName.lowercase(Locale.ENGLISH)
                lower.contains("asset allocator") || lower.contains("active asset") || lower.contains("allocator")
            }
            if (coreMetric != null) {
                AdvisorInsightRow(
                    icon = Icons.Default.ThumbUp,
                    iconColor = Color(0xFF10B981),
                    title = "Core Compounding Anchor (Active Asset Allocator)",
                    description = "${coreMetric.schemeName} serves as your foundational compounding base (Portfolio Weight: ${numFmt.format(coreMetric.portfolioWeightPct)}% vs Target: 45-60%). Profit-booking sell triggers remain strictly suppressed while underweight to ensure uninterrupted compounding. ${coreMetric.movingAverageNote}"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 5. Satellite Crash Accumulation Plan
            val exTopMetric = metricsList.firstOrNull { 
                val lower = it.schemeName.lowercase(Locale.ENGLISH)
                lower.contains("ex-top") || lower.contains("ex top") || lower.contains("extop") || lower.contains("small") || lower.contains("mid")
            }
            if (exTopMetric != null) {
                AdvisorInsightRow(
                    icon = Icons.Default.ShoppingCart,
                    iconColor = Color(0xFF10B981),
                    title = "Satellite Crash Accumulation Strategy (Equity Ex-Top 100)",
                    description = "Equity Ex-Top 100 is designated for automated reserve cash deployment during market crashes (Tier-1 ≥5%: ₹30,000 | Tier-2 ≥8%: ₹30,000 | Tier-3 ≥12%: ₹40,000). Currently in a rally — profit-booking proceeds will be earmarked for future crash buying."
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 6. Tactical Sector Rotation Strategy Insight
            val sectorMetric = metricsList.firstOrNull { 
                it.schemeName.contains("sector", ignoreCase = true) 
            }
            if (sectorMetric != null) {
                AdvisorInsightRow(
                    icon = Icons.Default.Star,
                    iconColor = Color(0xFFF59E0B),
                    title = "Tactical Sector Rotation Strategy",
                    description = "${sectorMetric.schemeName} provides dynamic cyclical momentum alpha via concentrated sector positioning (Ann. Vol: ${numFmt.format(sectorMetric.annualizedVolatilityPct)}%). Portfolio weight is ${numFmt.format(sectorMetric.portfolioWeightPct)}% (Target Range: 8-15%)."
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 7. Hybrid Long-Short Downside Cushion Insight
            val hybridMetric = metricsList.firstOrNull { 
                it.schemeName.contains("hybrid", ignoreCase = true) 
            }
            if (hybridMetric != null) {
                AdvisorInsightRow(
                    icon = Icons.Default.CheckCircle,
                    iconColor = Color(0xFF3B82F6),
                    title = "Hybrid Long-Short Defensive Cushion",
                    description = "${hybridMetric.schemeName} dampens portfolio drawdown via equity and debt hedging (Max Drawdown: ${numFmt.format(hybridMetric.maxDrawdownPct)}%, Churn Ratio: ${numFmt.format(hybridMetric.churnLossRatioPct)}%). Portfolio weight is ${numFmt.format(hybridMetric.portfolioWeightPct)}% (Target Range: 8-15%)."
                )
            }
        }
    }
}

@Composable
private fun AdvisorInsightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RiskMetricsComparisonTable(
    metricsList: List<FundRiskMetrics>,
    numFmt: DecimalFormat
) {
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(8.dp)
        ) {
            // Table Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell("Scheme Name", width = 140.dp, isHeader = true)
                TableCell("Weight", width = 65.dp, isHeader = true)
                TableCell("Sharpe", width = 60.dp, isHeader = true)
                TableCell("Sortino", width = 60.dp, isHeader = true)
                TableCell("Ann. Vol", width = 70.dp, isHeader = true)
                TableCell("Max DD", width = 65.dp, isHeader = true)
                TableCell("% Neg Roll", width = 75.dp, isHeader = true)
                TableCell("Churn Ratio", width = 75.dp, isHeader = true)
                TableCell("Ret/Risk", width = 65.dp, isHeader = true)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            metricsList.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell(item.schemeName, width = 140.dp, isHeader = false, fontWeight = FontWeight.Bold)
                    
                    val wColor = if (item.isConcentrationFlagged) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                    TableCell("${numFmt.format(item.portfolioWeightPct)}%", width = 65.dp, color = wColor, fontWeight = if (item.isConcentrationFlagged) FontWeight.Bold else FontWeight.Normal)
                    
                    val sColor = if (item.sharpeRatio > 1.0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                    TableCell(numFmt.format(item.sharpeRatio), width = 60.dp, color = sColor, fontWeight = FontWeight.SemiBold)
                    
                    val sorColor = if (item.sortinoRatio > 1.5) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                    TableCell(numFmt.format(item.sortinoRatio), width = 60.dp, color = sorColor, fontWeight = FontWeight.SemiBold)
                    
                    TableCell("${numFmt.format(item.annualizedVolatilityPct)}%", width = 70.dp)
                    
                    val ddColor = if (item.maxDrawdownPct < -15.0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                    TableCell("${numFmt.format(item.maxDrawdownPct)}%", width = 65.dp, color = ddColor)
                    
                    val negColor = if (item.percentNegativeWindows == 0.0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                    TableCell("${numFmt.format(item.percentNegativeWindows)}%", width = 75.dp, color = negColor)
                    
                    val churnColor = if (item.realizedPnL < 0) Color(0xFFEF4444) else Color(0xFF10B981)
                    TableCell("${numFmt.format(item.churnLossRatioPct)}%", width = 75.dp, color = churnColor)
                    
                    TableCell(numFmt.format(item.returnRiskRatio), width = 65.dp)
                }

                if (idx < metricsList.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(horizontal = 2.dp),
        style = if (isHeader) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                else MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        color = if (color != Color.Unspecified) color else if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun DetailedFundRiskCard(
    metrics: FundRiskMetrics,
    numFmt: DecimalFormat,
    currFmt: DecimalFormat
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (metrics.isConcentrationFlagged) Color(0xFFEF4444).copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Scheme Name & Weight Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metrics.schemeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Holding Val: ₹${currFmt.format(metrics.currentValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (metrics.isConcentrationFlagged) Color(0xFFEF4444).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Weight: ${numFmt.format(metrics.portfolioWeightPct)}%${if (metrics.isConcentrationFlagged) " 🚨 (>50%)" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (metrics.isConcentrationFlagged) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("Sharpe", numFmt.format(metrics.sharpeRatio), isPositive = metrics.sharpeRatio > 1.0)
                MetricItem("Sortino", numFmt.format(metrics.sortinoRatio), isPositive = metrics.sortinoRatio > 1.5)
                MetricItem("Ann. Vol", "${numFmt.format(metrics.annualizedVolatilityPct)}%")
                MetricItem("Max DD", "${numFmt.format(metrics.maxDrawdownPct)}%", isNegative = metrics.maxDrawdownPct < -10.0)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("Return/Risk", numFmt.format(metrics.returnRiskRatio))
                MetricItem("30d Neg Win", "${numFmt.format(metrics.percentNegativeWindows)}%", isPositive = metrics.percentNegativeWindows == 0.0)
                MetricItem("Churn Ratio", "${numFmt.format(metrics.churnLossRatioPct)}%", isNegative = metrics.realizedPnL < 0)
                MetricItem("Downside Dev", "${numFmt.format(metrics.downsideDevPct)}%")
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expand / Collapse Details Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "Hide Full Drawdown & Rolling Stats" else "View Drawdown & 30-Day Rolling Details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MAX DRAWDOWN DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Peak Date:", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
                        Text(metrics.maxDdPeakDate, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Trough Date:", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
                        Text(metrics.maxDdTroughDate, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Days to Trough / Recovery:", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
                        Text("${metrics.daysToTrough} days / ${metrics.daysToRecovery} days", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Text(
                        text = "30-DAY ROLLING RETURN STATS",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Mean / Median:", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
                        Text("${numFmt.format(metrics.rollingMeanPct)}% / ${numFmt.format(metrics.rollingMedianPct)}%", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("StdDev (Volatility):", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
                        Text("${numFmt.format(metrics.rollingStdDevPct)}%", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Min / Max Range:", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
                        Text("${numFmt.format(metrics.rollingMinPct)}% to ${numFmt.format(metrics.rollingMaxPct)}%", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    isPositive: Boolean = false,
    isNegative: Boolean = false
) {
    val valColor = when {
        isPositive -> Color(0xFF10B981)
        isNegative -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valColor
        )
    }
}

// -----------------------------------------------------------------------------
// REBALANCING ENGINE DATA MODELS & PLAIN-LANGUAGE SPECIFICATION
// -----------------------------------------------------------------------------

enum class EngineTriggerStatus(
    val title: String,
    val containerColor: Color,
    val contentColor: Color
) {
    NO_ACTION("NO ACTION", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF047857)),
    SELL("SELL", Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFB91C1C)),
    BUY("BUY", Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF6D28D9)),
    WAITING("WAITING TO SELL", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFB45309))
}

data class FundRebalancingAnalysis(
    val schemeName: String,
    val categoryName: String,
    val currentValue: Double,
    val totalPortfolioValue: Double,
    val currentWeightPct: Double,
    val targetMinWeightPct: Double,
    val targetMaxWeightPct: Double,
    val targetMidPct: Double,
    val rupeeGap: Double,
    val gapAmount: Double,
    val trimAmountRupees: Double,
    val currentNav: Double,
    val peak6MNav: Double,
    val drawdownFromPeakPct: Double,
    val isInActiveDrawdown: Boolean,
    val status: EngineTriggerStatus,
    val cardHeader: String,
    val statusText: String,
    val actionHeadlineText: String,
    val plainReasonExplanation: String?,
    val rupeeGapSummaryText: String,
    val targetCaptionText: String,
    val sellCount90Days: Int = 0,
    val isChurnBlocked: Boolean = false,
    val nextActionSummarySnippet: String? = null
)

private fun formatRupees(amount: Double): String {
    val fmt = DecimalFormat("#,##,##0")
    return "₹" + fmt.format(kotlin.math.abs(amount).toLong())
}

private fun computeFundRebalancingAnalysis(
    schemeName: String,
    currentValue: Double,
    totalPortfolioValue: Double,
    realizedPnL: Double,
    navPoints: List<Pair<Double, Long>>,
    schemeTxns: List<CasTransactionEntity> = emptyList(),
    planStartDateMillis: Long = 0L,
    costOfCurrentHolding: Double = 0.0,
    currentUnits: Double = 0.0
): List<FundRebalancingAnalysis> {
    val currentWeightPct = if (totalPortfolioValue > 0) (currentValue / totalPortfolioValue) * 100.0 else 0.0
    val numFmt = DecimalFormat("0.00")
    val schemeLower = schemeName.lowercase(Locale.ENGLISH)

    val isAssetAllocator = schemeLower.contains("asset allocator") || schemeLower.contains("active asset") || schemeLower.contains("allocator")
    val isExTop100 = schemeLower.contains("ex-top") || schemeLower.contains("ex top") || schemeLower.contains("small") || schemeLower.contains("mid") || schemeLower.contains("extop") || schemeLower.contains("ex-100")
    val isSectorRotation = schemeLower.contains("sector") || schemeLower.contains("rotation")
    val isHybrid = schemeLower.contains("hybrid")
    val isIncomePlus = schemeLower.contains("income plus") || schemeLower.contains("arbitrage active fof") || schemeLower.contains("arbitrage active")

    // Reconciled User Avg Cost NAV derived directly from unredeemed FIFO holding cost basis
    val userAvgCostNav = if (currentUnits > 0.0001) {
        costOfCurrentHolding / currentUnits
    } else {
        val txUnits = schemeTxns.filter { it.type.lowercase().contains("buy") }.sumOf { it.units } - 
                      schemeTxns.filter { it.type.lowercase().contains("sell") || it.type.lowercase().contains("redeem") }.sumOf { kotlin.math.abs(it.units) }
        val txCost = schemeTxns.filter { it.type.lowercase().contains("buy") }.sumOf { kotlin.math.abs(it.amount) }
        if (txUnits > 0.0001) txCost / txUnits else 0.0
    }

    // STRICT SAFEGUARD:
    // Distinguish new rebalancing installment executions from historical CAS redemptions.
    val isHistoricalImport = schemeTxns.isNotEmpty() && schemeTxns.none { txn ->
        val desc = txn.description.lowercase(Locale.ENGLISH)
        desc.contains("rebal") || desc.contains("installment") || desc.contains("staged") || desc.contains("part") || desc.contains("trim")
    }

    val rebalancingSells = if (isHistoricalImport) {
        emptyList()
    } else {
        schemeTxns.filter { txn ->
            val isSell = txn.type.lowercase().contains("sell") || txn.type.lowercase().contains("redeem")
            val desc = txn.description.lowercase(Locale.ENGLISH)
            val isExplicitlyTagged = desc.contains("rebal") || desc.contains("installment") || desc.contains("staged") || desc.contains("part") || desc.contains("trim")
            val txnTime = parseDateAnyLocal(txn.date)?.time ?: 0L
            isSell && (isExplicitlyTagged || (planStartDateMillis > 0L && txnTime > planStartDateMillis))
        }
    }
    val completedSells = rebalancingSells.size
    val totalPriorSells = rebalancingSells.sumOf { kotlin.math.abs(it.amount) }
    val originalCurrentValue = currentValue + totalPriorSells

    val categoryName = when {
        isAssetAllocator -> "Active Asset Allocator (Core Compounding)"
        isExTop100 -> "Equity Ex-Top 100 (Satellite High-Vol)"
        isSectorRotation -> "Sector Rotation (Tactical Momentum / Cyclical Alpha)"
        isHybrid -> "Hybrid Long-Short (Balanced / Downside Cushion)"
        isIncomePlus -> "Income Plus Arbitrage Active FoF (Arbitrage Alpha / Yield Plus)"
        else -> "Equity Long-Short (Flexi-Cap / Legacy Diversifier)"
    }

    val (targetMin, targetMax) = when {
        isAssetAllocator -> Pair(45.0, 60.0)
        isExTop100 -> Pair(15.0, 25.0)
        isSectorRotation -> Pair(8.0, 15.0)
        isHybrid -> Pair(8.0, 15.0)
        isIncomePlus -> Pair(5.0, 15.0)
        else -> Pair(8.0, 15.0)
    }

    val targetMid = when {
        isAssetAllocator -> 50.0
        isExTop100 -> 20.0
        isSectorRotation -> 10.0
        isHybrid -> 10.0
        isIncomePlus -> 10.0
        else -> 10.0
    }

    val currentNav = navPoints.lastOrNull()?.first ?: 0.0

    // Shared Cycle Detection Engine
    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    val timelinePoints = navPoints.map { (navVal, timeMillis) ->
        TimelineChartPoint(
            dateStr = sdfDate.format(Date(timeMillis)),
            nav = navVal,
            fastSma = null,
            slowSma = null,
            trend = 0.0,
            trendScore = 0.0
        )
    }

    val currentOpenRally = getOpenRallyCycle(timelinePoints)
    val fundExtensionPct = currentOpenRally?.maxRisePct ?: 0.0
    val rallyDays = currentOpenRally?.totalDays?.toInt() ?: 0
    val rallyTroughNav = currentOpenRally?.troughNav ?: currentNav

    val currentOpenDrawdown = getOpenDrawdownCycle(timelinePoints)
    val drawdownFromPeakPct = if (currentOpenDrawdown != null) -currentOpenDrawdown.maxFallPct else 0.0
    val drawdownDays = currentOpenDrawdown?.totalDays?.toInt() ?: 0
    val drawdownPeakNav = currentOpenDrawdown?.peakNav ?: currentNav
    val isInActiveDrawdown = currentOpenDrawdown != null && drawdownFromPeakPct < -3.0

    val sellCount90Days = if (realizedPnL < 0) 3 else 0
    val isChurnBlocked = sellCount90Days > 2

    val overTargetAmount = if (currentWeightPct > targetMax) currentValue - (targetMax / 100.0 * totalPortfolioValue) else 0.0
    val underTargetAmount = if (currentWeightPct < targetMin) (targetMin / 100.0 * totalPortfolioValue) - currentValue else 0.0

    val rupeeGapSummaryText = when {
        overTargetAmount > 0 -> "You're ${formatRupees(overTargetAmount)} over target — consider trimming"
        underTargetAmount > 0 -> "You can add up to ${formatRupees(underTargetAmount)} more to reach target"
        else -> "Currently within target range"
    }

    val targetCaptionText = when {
        overTargetAmount > 0 -> "${formatRupees(overTargetAmount)} too much in this fund"
        underTargetAmount > 0 -> "${formatRupees(underTargetAmount)} below target"
        else -> "On target"
    }

    val results = mutableListOf<FundRebalancingAnalysis>()

    fun createCard(
        status: EngineTriggerStatus,
        cardHeader: String,
        statusText: String,
        actionHeadlineText: String,
        plainReasonExplanation: String?,
        nextActionSummarySnippet: String?,
        trimAmountRupees: Double
    ): FundRebalancingAnalysis {
        return FundRebalancingAnalysis(
            schemeName = schemeName,
            categoryName = categoryName,
            currentValue = currentValue,
            totalPortfolioValue = totalPortfolioValue,
            currentWeightPct = currentWeightPct,
            targetMinWeightPct = targetMin,
            targetMaxWeightPct = targetMax,
            targetMidPct = targetMid,
            rupeeGap = (targetMin / 100.0 * totalPortfolioValue) - currentValue,
            gapAmount = if (overTargetAmount > 0) overTargetAmount else underTargetAmount,
            trimAmountRupees = trimAmountRupees,
            currentNav = currentNav,
            peak6MNav = drawdownPeakNav,
            drawdownFromPeakPct = drawdownFromPeakPct,
            isInActiveDrawdown = isInActiveDrawdown,
            status = status,
            cardHeader = cardHeader,
            statusText = statusText,
            actionHeadlineText = actionHeadlineText,
            plainReasonExplanation = plainReasonExplanation,
            rupeeGapSummaryText = rupeeGapSummaryText,
            targetCaptionText = targetCaptionText,
            sellCount90Days = sellCount90Days,
            isChurnBlocked = isChurnBlocked,
            nextActionSummarySnippet = nextActionSummarySnippet
        )
    }

    if (isAssetAllocator) {
        val navsList = navPoints.map { it.first }
        val dma20 = if (navsList.size >= 20) navsList.takeLast(20).average() else currentNav
        val dma50 = if (navsList.size >= 50) navsList.takeLast(50).average() else currentNav
        val d20Pct = if (dma20 > 0.0) ((currentNav - dma20) / dma20) * 100.0 else 0.0
        val d50Pct = if (dma50 > 0.0) ((currentNav - dma50) / dma50) * 100.0 else 0.0
        val maNote = "\n• Moving Average Trend Status: 20-DMA at ₹${numFmt.format(dma20)} (${if (d20Pct >= 0) "+" else ""}${numFmt.format(d20Pct)}% vs NAV), 50-DMA at ₹${numFmt.format(dma50)} (${if (d50Pct >= 0) "+" else ""}${numFmt.format(d50Pct)}% vs NAV). Core compounding anchored on moving average support."

        val isProfitBookingTrigger = fundExtensionPct >= 25.0 && rallyDays > 60

        val stepA = originalCurrentValue - (0.45 * totalPortfolioValue)
        val stepB = originalCurrentValue * 0.30
        val initialTotalReduction = kotlin.math.max(0.0, kotlin.math.min(stepA, stepB))
        val fixedPerPartSize = if (initialTotalReduction > 0) initialTotalReduction / 4.0 else 0.0

        val isUnderweight = currentWeightPct < targetMin

        if (isUnderweight) {
            // RULE: Profit-booking sell triggers are strictly SUPPRESSED on underweight core funds to preserve core compounding
            val distToProfitBook = kotlin.math.max(0.0, 25.0 - fundExtensionPct)
            results.add(createCard(
                status = EngineTriggerStatus.NO_ACTION,
                cardHeader = "Below Target — Accumulate Base",
                statusText = "ACCUMULATE",
                actionHeadlineText = "Below target weight (${numFmt.format(currentWeightPct)}% vs target 45-60%) — accumulate core position from rebalancing proceeds",
                plainReasonExplanation = "This core compounding fund is ${numFmt.format(currentWeightPct)}% of your portfolio (${formatRupees(underTargetAmount)} below target range 45-60%).\n• Core Compounding Mandate: Profit-booking sell triggers are strictly suppressed on Active Asset Allocator while underweight to prioritize building your core compounding base. Rebalancing proceeds from overweight funds will be allocated here to build this position.\n• Fund Extension Status: +${numFmt.format(fundExtensionPct)}% over $rallyDays days (Rally low: ₹${numFmt.format(rallyTroughNav)}). Profit-booking sell triggers remain suppressed until the fund reaches its target allocation.$maNote",
                nextActionSummarySnippet = null,
                trimAmountRupees = 0.0
            ))
        } else if (currentWeightPct > targetMax) {
            // Overweight core fund -> 4-Part Staged Plan
            if (completedSells < 4) {
                val partNum = completedSells + 1
                val trimAmt = fixedPerPartSize
                if (isInActiveDrawdown) {
                    results.add(createCard(
                        status = EngineTriggerStatus.WAITING,
                        cardHeader = "Too Much In This Fund (Part $partNum of 4)",
                        statusText = "WAITING TO SELL",
                        actionHeadlineText = "Wait until drawdown closes before selling — fund is currently down",
                        plainReasonExplanation = "We're not selling right now because the fund is currently down (${numFmt.format(kotlin.math.abs(drawdownFromPeakPct))}% below open cycle peak) — selling during a dip locks in a loss. We'll wait until it recovers.$maNote",
                        nextActionSummarySnippet = "[Concentration Trim — Part $partNum of 4] Sell ${formatRupees(fixedPerPartSize)} from $schemeName (once drawdown recovers)",
                        trimAmountRupees = trimAmt
                    ))
                } else {
                    results.add(createCard(
                        status = EngineTriggerStatus.SELL,
                        cardHeader = "Too Much In This Fund (Part $partNum of 4)",
                        statusText = "SELL — PART $partNum OF 4",
                        actionHeadlineText = "Part $partNum of 4: Sell ${formatRupees(fixedPerPartSize)} now",
                        plainReasonExplanation = "This fund is oversized in your portfolio (${numFmt.format(currentWeightPct)}% vs target 45-60%). Executing installment $partNum of 4 (~${formatRupees(fixedPerPartSize)} fixed per part, total reduction ~${formatRupees(initialTotalReduction)}). (${4 - partNum} parts remaining after this.) $maNote",
                        nextActionSummarySnippet = "[Concentration Trim — Part $partNum of 4]\nSell ${formatRupees(fixedPerPartSize)} from $schemeName now.\n(${4 - partNum} more installments planned, ~${formatRupees(fixedPerPartSize)} each — locked per-part size ensures plan completes in 4 parts.)",
                        trimAmountRupees = trimAmt
                    ))
                }
            } else {
                results.add(createCard(
                    status = EngineTriggerStatus.SELL,
                    cardHeader = "Too Much In This Fund (Overweight)",
                    statusText = "SELL — OVERWEIGHT",
                    actionHeadlineText = "Trim ${formatRupees(overTargetAmount)} to reach target range (45-60%)",
                    plainReasonExplanation = "This fund is ${numFmt.format(currentWeightPct)}% of your portfolio (Target: 45-60%). Although 4 rebalancing installments were previously logged, the fund weight remains above target. Recommend trimming ${formatRupees(overTargetAmount)}.$maNote",
                    nextActionSummarySnippet = "[Concentration Trim] Sell ${formatRupees(overTargetAmount)} from $schemeName to align with target range 45-60%",
                    trimAmountRupees = overTargetAmount
                ))
            }

            if (isProfitBookingTrigger) {
                val trimAmt = currentValue * 0.20
                results.add(createCard(
                    status = EngineTriggerStatus.SELL,
                    cardHeader = "Profit Booking — Data-Triggered (Fund Extension)",
                    statusText = "SELL",
                    actionHeadlineText = "Sell ${formatRupees(trimAmt)} from this fund (20% of holding)",
                    plainReasonExplanation = "PRIMARY TRIGGER MET: Active Asset Allocator has extended +${numFmt.format(fundExtensionPct)}% from its open cycle low (₹${numFmt.format(rallyTroughNav)}) over $rallyDays days, meeting the +25.0% profit-booking extension threshold. Recommended profit-book: ${formatRupees(trimAmt)} (20% of holding).$maNote",
                    nextActionSummarySnippet = "[Profit Booking] Sell ${formatRupees(trimAmt)} from Active Asset Allocator Long-Short Fund (Fund extended +${numFmt.format(fundExtensionPct)}% over $rallyDays days)",
                    trimAmountRupees = trimAmt
                ))
            }
        } else {
            // On target
            if (isProfitBookingTrigger) {
                val trimAmt = currentValue * 0.20
                results.add(createCard(
                    status = EngineTriggerStatus.SELL,
                    cardHeader = "Profit Booking — Data-Triggered (Fund Extension)",
                    statusText = "SELL",
                    actionHeadlineText = "Sell ${formatRupees(trimAmt)} from this fund (20% of holding)",
                    plainReasonExplanation = "PRIMARY TRIGGER MET: Active Asset Allocator has extended +${numFmt.format(fundExtensionPct)}% from its open cycle low (₹${numFmt.format(rallyTroughNav)}) over $rallyDays days, meeting the +25.0% profit-booking extension threshold. Recommended profit-book: ${formatRupees(trimAmt)} (20% of holding).$maNote",
                    nextActionSummarySnippet = "[Profit Booking] Sell ${formatRupees(trimAmt)} from Active Asset Allocator Long-Short Fund (Fund extended +${numFmt.format(fundExtensionPct)}% over $rallyDays days)",
                    trimAmountRupees = trimAmt
                ))
            } else {
                val distToProfitBook = kotlin.math.max(0.0, 25.0 - fundExtensionPct)
                results.add(createCard(
                    status = EngineTriggerStatus.NO_ACTION,
                    cardHeader = "On Target — Keep Investing",
                    statusText = "NO ACTION",
                    actionHeadlineText = "No action needed — continue your SIP",
                    plainReasonExplanation = "This core fund is ${numFmt.format(currentWeightPct)}% of your portfolio (Target: ${targetMin.toInt()}-${targetMax.toInt()}%). Currently on target.\n• Profit-booking status: ${numFmt.format(distToProfitBook)}% away from +25% fund extension threshold (Current Fund Extension: +${numFmt.format(fundExtensionPct)}% over $rallyDays days)$maNote",
                    nextActionSummarySnippet = null,
                    trimAmountRupees = 0.0
                ))
            }
        }
    } else if (isExTop100) {
        val personalGainPct = if (userAvgCostNav > 0) ((currentNav - userAvgCostNav) / userAvgCostNav) * 100.0 else 0.0

        val isPrimaryFundExtensionTrigger = fundExtensionPct >= 25.0 && rallyDays > 60
        val isSecondaryPersonalGainSignal = personalGainPct >= 25.0 && !isPrimaryFundExtensionTrigger

        // Compute peak NAV & peak drawdown across full timeline & active cycle
        val maxNavInHistory = navPoints.maxOfOrNull { it.first } ?: currentNav
        val directDrawdownPct = if (maxNavInHistory > 0) ((currentNav - maxNavInHistory) / maxNavInHistory) * 100.0 else 0.0
        val cycleDrawdownPct = if (currentOpenDrawdown != null) -currentOpenDrawdown.maxFallPct else 0.0
        val effectiveDrawdownPct = kotlin.math.min(directDrawdownPct, cycleDrawdownPct)
        val effectivePeakNav = if (directDrawdownPct <= cycleDrawdownPct && maxNavInHistory > 0) maxNavInHistory else drawdownPeakNav
        val absDrawdown = kotlin.math.abs(effectiveDrawdownPct)

        // Card 1: Crash Accumulation Strategy Card (Always generated)
        if (effectiveDrawdownPct <= -5.0) {
            val buyAmount = if (absDrawdown >= 12.0) 40000.0 else if (absDrawdown >= 8.0) 30000.0 else 30000.0
            val tierText = if (absDrawdown >= 12.0) "Tier-3" else if (absDrawdown >= 8.0) "Tier-2" else "Tier-1"
            results.add(createCard(
                status = EngineTriggerStatus.BUY,
                cardHeader = "Crash Accumulation / Buying Opportunity — Data-Triggered ($tierText Dip)",
                statusText = "BUY — DIP",
                actionHeadlineText = "Deploy ${formatRupees(buyAmount)} into Equity Ex-Top 100 for crash accumulation ($tierText Dip)",
                plainReasonExplanation = "This satellite high-volatility fund ($schemeName) is down ${numFmt.format(absDrawdown)}% from its peak NAV (₹${numFmt.format(effectivePeakNav)}) — meeting your $tierText crash accumulation dip-buy threshold (≥ 5% drop). Deploying reserve cash into this fund.\n• Portfolio Holding Cost Basis: Average Cost NAV = ₹${numFmt.format(userAvgCostNav)}, Personal Gain = +${numFmt.format(personalGainPct)}%.",
                nextActionSummarySnippet = "[Crash Accumulation] Add ${formatRupees(buyAmount)} to $schemeName from reserve cash (down ${numFmt.format(absDrawdown)}% from peak — $tierText dip)",
                trimAmountRupees = buyAmount
            ))
        } else {
            val distToDipBuy = kotlin.math.max(0.0, 5.0 - absDrawdown)
            val distToProfitBook = kotlin.math.max(0.0, 25.0 - fundExtensionPct)
            val secondarySignalStr = if (isSecondaryPersonalGainSignal) {
                "\n• Secondary Context: Personal gain threshold (+25.0%) reached (+${numFmt.format(personalGainPct)}% based on reconciled Avg Cost ₹${numFmt.format(userAvgCostNav)}), but primary fund-level cycle extension (+${numFmt.format(fundExtensionPct)}%) is below trigger threshold. Hold / continue SIP."
            } else ""
            val personalGainStr = if (userAvgCostNav > 0) " | Personal gain: +${numFmt.format(personalGainPct)}% (Avg Cost: ₹${numFmt.format(userAvgCostNav)})" else ""
            val weightStatusStr = if (underTargetAmount > 0) {
                "This satellite high-volatility fund is ${numFmt.format(currentWeightPct)}% of your portfolio (${formatRupees(underTargetAmount)} below target range ${targetMin.toInt()}-${targetMax.toInt()}%). Reserve cash pool is set aside here for crash accumulation / dip-buying when drawdown ≥ 5%."
            } else {
                "This satellite fund is ${numFmt.format(currentWeightPct)}% of your portfolio (Target: ${targetMin.toInt()}-${targetMax.toInt()}%). Currently on target."
            }

            results.add(createCard(
                status = EngineTriggerStatus.NO_ACTION,
                cardHeader = if (underTargetAmount > 0) "Below Target — Earmarked For Crash Accumulation" else "On Target — Keep Investing",
                statusText = "NO ACTION",
                actionHeadlineText = if (underTargetAmount > 0) "Below target range — reserve cash earmarked for crash accumulation on dip" else "No action needed — continue your SIP",
                plainReasonExplanation = "$weightStatusStr$secondarySignalStr\n• Crash Accumulation Dip Status: ${numFmt.format(distToDipBuy)}% away from -5% drawdown trigger (Current Dip: ${numFmt.format(absDrawdown)}%, Peak NAV: ₹${numFmt.format(effectivePeakNav)})\n• Primary Profit-booking status: ${numFmt.format(distToProfitBook)}% away from +25% fund extension threshold (Current Fund Extension: +${numFmt.format(fundExtensionPct)}% over $rallyDays days$personalGainStr)",
                nextActionSummarySnippet = null,
                trimAmountRupees = 0.0
            ))
        }

        // Card 2: Profit Booking Card (Coexists separately if trigger met)
        if (isPrimaryFundExtensionTrigger) {
            val trimAmt = currentValue * 0.20
            val personalContextStr = if (userAvgCostNav > 0) " (Supporting Personal Context: personal gain is +${numFmt.format(personalGainPct)}%, Avg Cost: ₹${numFmt.format(userAvgCostNav)})" else ""
            results.add(createCard(
                status = EngineTriggerStatus.SELL,
                cardHeader = "Profit Booking — Data-Triggered (Fund Extension)",
                statusText = "SELL",
                actionHeadlineText = "Sell ${formatRupees(trimAmt)} from this fund (20% of holding)",
                plainReasonExplanation = "PRIMARY TRIGGER MET: The fund itself has extended +${numFmt.format(fundExtensionPct)}% from its open cycle low (₹${numFmt.format(rallyTroughNav)}) over $rallyDays days, exceeding the +25.0% fund extension threshold$personalContextStr. Recommended profit-book: ${formatRupees(trimAmt)} (20% of holding).\n• Crash Accumulation Strategy (Equity Ex-Top 100): Trimming ${formatRupees(trimAmt)} builds your reserve cash pool. These funds are earmarked to deploy back into Equity Ex-Top 100 on future market drops: Tier-1 (≥5% dip: ₹30k), Tier-2 (≥8% dip: ₹30k), Tier-3 (≥12% dip: ₹40k). Current dip status: ${numFmt.format(absDrawdown)}% (no active crash dip; currently in a +${numFmt.format(fundExtensionPct)}% rally).",
                nextActionSummarySnippet = "[Profit Booking] Sell ${formatRupees(trimAmt)} from $schemeName (Fund extended +${numFmt.format(fundExtensionPct)}% over $rallyDays days)",
                trimAmountRupees = trimAmt
            ))
        }
    } else if (isSectorRotation) {
        val isProfitBookingTrigger = fundExtensionPct >= 25.0 && rallyDays > 60
        val isOverweight = currentWeightPct > targetMax
        val isUnderweight = currentWeightPct < targetMin

        if (isOverweight) {
            val trimAmt = overTargetAmount
            if (isInActiveDrawdown) {
                results.add(createCard(
                    status = EngineTriggerStatus.WAITING,
                    cardHeader = "Sector Rotation — Overweight (Waiting on Drawdown)",
                    statusText = "WAITING TO SELL",
                    actionHeadlineText = "Wait until sector drawdown closes before trimming position",
                    plainReasonExplanation = "Sector Rotation Long-Short Fund is above target (${numFmt.format(currentWeightPct)}% vs target 8-15%). However, the fund is currently in an active sector dip (${numFmt.format(kotlin.math.abs(drawdownFromPeakPct))}% below peak NAV). Defer trimming until recovery to lock in optimal profit.",
                    nextActionSummarySnippet = "[Sector Rebalance] Trim ${formatRupees(trimAmt)} from Sector Rotation Long-Short Fund (once drawdown recovers)",
                    trimAmountRupees = trimAmt
                ))
            } else {
                results.add(createCard(
                    status = EngineTriggerStatus.SELL,
                    cardHeader = "Sector Rotation — Overweight",
                    statusText = "SELL — OVERWEIGHT",
                    actionHeadlineText = "Trim ${formatRupees(trimAmt)} to align with target range (8-15%)",
                    plainReasonExplanation = "Sector Rotation Long-Short Fund represents ${numFmt.format(currentWeightPct)}% of your portfolio, exceeding target allocation 8-15%. Reallocate ${formatRupees(trimAmt)} towards core compounding to lock in cyclical sector gains.",
                    nextActionSummarySnippet = "[Sector Rebalance] Sell ${formatRupees(trimAmt)} from Sector Rotation Long-Short Fund to align with target 8-15%",
                    trimAmountRupees = trimAmt
                ))
            }
        }

        if (isProfitBookingTrigger) {
            val trimAmt = currentValue * 0.20
            results.add(createCard(
                status = EngineTriggerStatus.SELL,
                cardHeader = "Profit Booking — Data-Triggered (Sector Extension)",
                statusText = "SELL",
                actionHeadlineText = "Sell ${formatRupees(trimAmt)} from Sector Rotation (20% of holding)",
                plainReasonExplanation = "PRIMARY TRIGGER MET: Sector Rotation Long-Short Fund has extended +${numFmt.format(fundExtensionPct)}% from its open cycle low (₹${numFmt.format(rallyTroughNav)}) over $rallyDays days, meeting the +25.0% profit-booking threshold. Harvest ${formatRupees(trimAmt)} (20% of holding) to reinvest in core compounding base.",
                nextActionSummarySnippet = "[Profit Booking] Sell ${formatRupees(trimAmt)} from Sector Rotation Long-Short Fund (extended +${numFmt.format(fundExtensionPct)}% over $rallyDays days)",
                trimAmountRupees = trimAmt
            ))
        }

        if (results.isEmpty()) {
            val distToProfitBook = kotlin.math.max(0.0, 25.0 - fundExtensionPct)
            val distToDipBuy = kotlin.math.max(0.0, 5.0 - kotlin.math.abs(drawdownFromPeakPct))
            results.add(createCard(
                status = EngineTriggerStatus.NO_ACTION,
                cardHeader = if (isUnderweight) "Below Target — Accumulate Tactical Position" else "On Target — Keep Investing",
                statusText = "NO ACTION",
                actionHeadlineText = if (isUnderweight) "Below target range (8-15%) — accumulate tactical sector alpha" else "No action needed — continue your SIP",
                plainReasonExplanation = if (isUnderweight) {
                    "Sector Rotation Long-Short Fund is ${numFmt.format(currentWeightPct)}% of your portfolio (${formatRupees(underTargetAmount)} below target 8-15%). Accumulate this position systematically during sector consolidations to capture cyclical upside.\n• Profit-booking status: ${numFmt.format(distToProfitBook)}% away from +25% extension threshold\n• Dip status: ${numFmt.format(distToDipBuy)}% away from -5% drawdown threshold"
                } else {
                    "Sector Rotation Long-Short Fund is ${numFmt.format(currentWeightPct)}% of your portfolio (Target: 8-15%). Currently on target.\n• Profit-booking status: ${numFmt.format(distToProfitBook)}% away from +25% extension threshold (Current Extension: +${numFmt.format(fundExtensionPct)}% over $rallyDays days)\n• Dip status: ${numFmt.format(distToDipBuy)}% away from -5% drawdown threshold"
                },
                nextActionSummarySnippet = null,
                trimAmountRupees = 0.0
            ))
        }
    } else if (isHybrid) {
        val isProfitBookingTrigger = fundExtensionPct >= 20.0 && rallyDays > 60
        val isOverweight = currentWeightPct > targetMax
        val isUnderweight = currentWeightPct < targetMin

        if (isOverweight) {
            val trimAmt = overTargetAmount
            if (isInActiveDrawdown) {
                results.add(createCard(
                    status = EngineTriggerStatus.WAITING,
                    cardHeader = "Hybrid Cushion — Overweight (Waiting on Recovery)",
                    statusText = "WAITING TO SELL",
                    actionHeadlineText = "Wait until drawdown closes before rebalancing defensive holding",
                    plainReasonExplanation = "Hybrid Long-Short Fund is ${numFmt.format(currentWeightPct)}% vs target 8-15%. Waiting for current mild drawdown (${numFmt.format(kotlin.math.abs(drawdownFromPeakPct))}%) to recover before reallocating to equity compounding.",
                    nextActionSummarySnippet = "[Defensive Trim] Rebalance ${formatRupees(trimAmt)} from Hybrid Long-Short Fund (once drawdown recovers)",
                    trimAmountRupees = trimAmt
                ))
            } else {
                results.add(createCard(
                    status = EngineTriggerStatus.SELL,
                    cardHeader = "Hybrid Cushion — Overweight",
                    statusText = "SELL — OVERWEIGHT",
                    actionHeadlineText = "Trim ${formatRupees(trimAmt)} to align with target range (8-15%)",
                    plainReasonExplanation = "Hybrid Long-Short Fund is ${numFmt.format(currentWeightPct)}% of your portfolio, exceeding target range 8-15%. Trim ${formatRupees(trimAmt)} to redeploy into core wealth generation.",
                    nextActionSummarySnippet = "[Defensive Trim] Sell ${formatRupees(trimAmt)} from Hybrid Long-Short Fund to align with target 8-15%",
                    trimAmountRupees = trimAmt
                ))
            }
        }

        if (isProfitBookingTrigger) {
            val trimAmt = currentValue * 0.20
            results.add(createCard(
                status = EngineTriggerStatus.SELL,
                cardHeader = "Profit Booking — Data-Triggered (Hybrid Extension)",
                statusText = "SELL",
                actionHeadlineText = "Sell ${formatRupees(trimAmt)} from Hybrid Long-Short (20% of holding)",
                plainReasonExplanation = "PRIMARY TRIGGER MET: Hybrid Long-Short Fund has extended +${numFmt.format(fundExtensionPct)}% from open cycle low over $rallyDays days, meeting the +20.0% defensive profit-booking threshold. Harvest ${formatRupees(trimAmt)} into cash/core reserves.",
                nextActionSummarySnippet = "[Profit Booking] Sell ${formatRupees(trimAmt)} from Hybrid Long-Short Fund (extended +${numFmt.format(fundExtensionPct)}% over $rallyDays days)",
                trimAmountRupees = trimAmt
            ))
        }

        if (results.isEmpty()) {
            val distToProfitBook = kotlin.math.max(0.0, 20.0 - fundExtensionPct)
            val distToDipBuy = kotlin.math.max(0.0, 4.0 - kotlin.math.abs(drawdownFromPeakPct))
            results.add(createCard(
                status = EngineTriggerStatus.NO_ACTION,
                cardHeader = if (isUnderweight) "Below Target — Accumulate Defensive Cushion" else "On Target — Keep Investing",
                statusText = "NO ACTION",
                actionHeadlineText = if (isUnderweight) "Below target range (8-15%) — accumulate balanced defensive cushion" else "No action needed — continue your SIP",
                plainReasonExplanation = if (isUnderweight) {
                    "Hybrid Long-Short Fund is ${numFmt.format(currentWeightPct)}% of your portfolio (${formatRupees(underTargetAmount)} below target 8-15%). Accumulate this position to maintain your desired defensive cushion.\n• Profit-booking status: ${numFmt.format(distToProfitBook)}% away from +20% extension threshold\n• Dip status: ${numFmt.format(distToDipBuy)}% away from -4% drawdown threshold"
                } else {
                    "Hybrid Long-Short Fund is ${numFmt.format(currentWeightPct)}% of your portfolio (Target: 8-15%). Currently on target providing balanced downside protection.\n• Profit-booking status: ${numFmt.format(distToProfitBook)}% away from +20% extension threshold (Current Extension: +${numFmt.format(fundExtensionPct)}% over $rallyDays days)\n• Dip status: ${numFmt.format(distToDipBuy)}% away from -4% drawdown threshold"
                },
                nextActionSummarySnippet = null,
                trimAmountRupees = 0.0
            ))
        }
    } else {
        // Equity Long-Short
        val isProfitBookingTrigger = fundExtensionPct >= 25.0 && rallyDays > 60

        val stepA = originalCurrentValue - (0.45 * totalPortfolioValue)
        val stepB = originalCurrentValue * 0.30
        val initialTotalReduction = kotlin.math.max(0.0, kotlin.math.min(stepA, stepB))
        val fixedPerPartSize = if (initialTotalReduction > 0) initialTotalReduction / 4.0 else 0.0

        val isOverweight = currentWeightPct > targetMax

        // Card 1: 4-Part Staged Concentration Reduction Plan (ALWAYS generated as a separate card for overweight fund)
        if (isOverweight) {
            if (completedSells < 4) {
                val partNum = completedSells + 1
                val trimAmt = fixedPerPartSize
                if (isInActiveDrawdown) {
                    results.add(createCard(
                        status = EngineTriggerStatus.WAITING,
                        cardHeader = "Reducing Risk — Not a Profit Call (Part $partNum of 4)",
                        statusText = "WAITING TO SELL",
                        actionHeadlineText = "Wait until drawdown closes before selling — fund is currently down",
                        plainReasonExplanation = "This fund is oversized in your portfolio (${numFmt.format(currentWeightPct)}% vs target 8-15%). We're not selling right now because the fund is currently down (${numFmt.format(kotlin.math.abs(drawdownFromPeakPct))}% below open cycle peak) — selling during a dip locks in a loss. We'll wait until it recovers.",
                        nextActionSummarySnippet = "[Concentration Reduction — Part $partNum of 4] Sell ${formatRupees(fixedPerPartSize)} from Equity Long-Short (once drawdown recovers)",
                        trimAmountRupees = trimAmt
                    ))
                } else {
                    results.add(createCard(
                        status = EngineTriggerStatus.SELL,
                        cardHeader = "Reducing Risk — Not a Profit Call (Part $partNum of 4)",
                        statusText = "SELL — PART $partNum OF 4",
                        actionHeadlineText = "Part $partNum of 4: Sell ${formatRupees(fixedPerPartSize)} now",
                        plainReasonExplanation = "This fund is oversized in your portfolio (${numFmt.format(currentWeightPct)}% vs target 8-15%). Executing installment $partNum of 4 (~${formatRupees(fixedPerPartSize)} fixed per part, total reduction ~${formatRupees(initialTotalReduction)}). (${4 - partNum} parts remaining after this.)",
                        nextActionSummarySnippet = "[Concentration Reduction — Part $partNum of 4]\nSell ${formatRupees(fixedPerPartSize)} from Equity Long-Short Fund now.\n(${4 - partNum} more installments planned, ~${formatRupees(fixedPerPartSize)} each — locked per-part size ensures plan completes in 4 parts.)",
                        trimAmountRupees = trimAmt
                    ))
                }
            } else {
                results.add(createCard(
                    status = EngineTriggerStatus.SELL,
                    cardHeader = "Reducing Risk — Overweight",
                    statusText = "SELL — OVERWEIGHT",
                    actionHeadlineText = "Trim ${formatRupees(overTargetAmount)} to reach target range (8-15%)",
                    plainReasonExplanation = "This fund is ${numFmt.format(currentWeightPct)}% of your portfolio (Target: 8-15%). Although 4 rebalancing installments were previously logged, the fund weight remains above target. Recommend trimming ${formatRupees(overTargetAmount)}.",
                    nextActionSummarySnippet = "[Concentration Reduction] Sell ${formatRupees(overTargetAmount)} from Equity Long-Short Fund to align with target 8-15%",
                    trimAmountRupees = overTargetAmount
                ))
            }
        }

        // Card 2: Profit Booking Card (Coexists separately if trigger met)
        if (isProfitBookingTrigger) {
            val trimAmt = currentValue * 0.20
            val overweightNoteStr = if (isOverweight) " Note: This tactical profit-booking trim of ${formatRupees(trimAmt)} operates alongside the 4-part concentration reduction plan (currently ${numFmt.format(currentWeightPct)}% vs target 8-15%)." else ""
            results.add(createCard(
                status = EngineTriggerStatus.SELL,
                cardHeader = "Profit Booking — Data-Triggered (Fund Extension)",
                statusText = "SELL",
                actionHeadlineText = "Sell ${formatRupees(trimAmt)} from this fund (20% of holding)",
                plainReasonExplanation = "PRIMARY TRIGGER MET: Equity Long-Short has extended +${numFmt.format(fundExtensionPct)}% from its open cycle low (₹${numFmt.format(rallyTroughNav)}) over $rallyDays days, meeting the +25.0% profit-booking extension threshold. Recommended profit-book: ${formatRupees(trimAmt)} (20% of holding).$overweightNoteStr",
                nextActionSummarySnippet = "[Profit Booking] Sell ${formatRupees(trimAmt)} from Equity Long-Short Fund (Fund extended +${numFmt.format(fundExtensionPct)}% over $rallyDays days)",
                trimAmountRupees = trimAmt
            ))
        }

        if (results.isEmpty()) {
            val distToProfitBook = kotlin.math.max(0.0, 25.0 - fundExtensionPct)
            val distToDipBuy = kotlin.math.max(0.0, 5.0 - kotlin.math.abs(drawdownFromPeakPct))
            results.add(createCard(
                status = EngineTriggerStatus.NO_ACTION,
                cardHeader = "On Target — Keep Investing",
                statusText = "NO ACTION",
                actionHeadlineText = "No action needed — continue your SIP",
                plainReasonExplanation = if (completedSells >= 4) {
                    "All 4 rebalancing installments successfully completed. Fund is now on target (${numFmt.format(currentWeightPct)}%).\n• Profit-booking status: ${numFmt.format(distToProfitBook)}% away from +25% fund extension threshold (Current Fund Extension: +${numFmt.format(fundExtensionPct)}% over $rallyDays days)\n• Dip status: ${numFmt.format(distToDipBuy)}% away from -5% drawdown threshold (current dip: ${numFmt.format(kotlin.math.abs(drawdownFromPeakPct))}%)"
                } else {
                    "This fund is ${numFmt.format(currentWeightPct)}% of your portfolio (Target: ${targetMin.toInt()}-${targetMax.toInt()}%). Currently on target.\n• Profit-booking status: ${numFmt.format(distToProfitBook)}% away from +25% fund extension threshold (Current Fund Extension: +${numFmt.format(fundExtensionPct)}% over $rallyDays days)\n• Dip status: ${numFmt.format(distToDipBuy)}% away from -5% drawdown threshold (current dip: ${numFmt.format(kotlin.math.abs(drawdownFromPeakPct))}%)"
                },
                nextActionSummarySnippet = null,
                trimAmountRupees = 0.0
            ))
        }
    }

    return results
}

@Composable
fun RulesBasedRebalancingEngineSection(
    rebalancingAnalysisList: List<FundRebalancingAnalysis>,
    currFmt: DecimalFormat,
    numFmt: DecimalFormat,
    txns: List<CasTransactionEntity>,
    planStartDateMillis: Long = 0L
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top-of-screen Summary Card ("YOUR NEXT ACTIONS")
        TopNextActionCard(rebalancingAnalysisList = rebalancingAnalysisList)

        // 2. Your Long-Term Target Mix Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Long-Term Target Mix",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Age 41 Glide Path",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                rebalancingAnalysisList.distinctBy { it.schemeName }.forEach { item ->
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.schemeName}: ${numFmt.format(item.currentWeightPct)}% (Target: ${item.targetMinWeightPct.toInt()}-${item.targetMaxWeightPct.toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "→ \"${item.targetCaptionText}\"",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (item.currentWeightPct > item.targetMaxWeightPct) Color(0xFFEF4444)
                                    else if (item.currentWeightPct < item.targetMinWeightPct) Color(0xFFF59E0B)
                                    else Color(0xFF10B981),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val progress = (item.currentWeightPct / 100.0).coerceIn(0.0, 1.0).toFloat()
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (item.currentWeightPct > item.targetMaxWeightPct) Color(0xFFEF4444)
                                    else if (item.currentWeightPct < item.targetMinWeightPct) Color(0xFFF59E0B)
                                    else Color(0xFF10B981),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // 2.5 Reserve Cash Pool & Plan Tracking Card
        val stagedSellItems = rebalancingAnalysisList.filter { 
            it.cardHeader.contains("Part") || it.statusText.contains("PART")
        }
        val isHistoricalImport = txns.isNotEmpty() && txns.none { txn ->
            val desc = txn.description.lowercase(Locale.ENGLISH)
            desc.contains("rebal") || desc.contains("installment") || desc.contains("staged") || desc.contains("part") || desc.contains("trim")
        }

        val rebalancingSells = if (isHistoricalImport) {
            emptyList()
        } else {
            txns.filter { txn ->
                val isSell = txn.type.lowercase().contains("sell") || txn.type.lowercase().contains("redeem")
                val desc = txn.description.lowercase(Locale.ENGLISH)
                val isExplicitlyTagged = desc.contains("rebal") || desc.contains("installment") || desc.contains("staged") || desc.contains("part") || desc.contains("trim")
                val txnTime = parseDateAnyLocal(txn.date)?.time ?: 0L
                isSell && (isExplicitlyTagged || (planStartDateMillis > 0L && txnTime > planStartDateMillis))
            }
        }
        val totalReserveCashAvailable = rebalancingSells.sumOf { kotlin.math.abs(it.amount) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reserve Cash Pool & Installment Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Reserve Pool: ${formatRupees(totalReserveCashAvailable)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (stagedSellItems.isNotEmpty()) {
                    stagedSellItems.distinctBy { it.schemeName }.forEach { item ->
                        val schemeTxns = rebalancingSells.filter { txn ->
                            txn.schemeName.contains(item.schemeName, ignoreCase = true) || 
                            item.schemeName.contains(txn.schemeName, ignoreCase = true)
                        }
                        val completedSells = schemeTxns.size

                        Text(
                            text = "Staged Plan: ${item.schemeName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val partAmt = formatRupees(item.trimAmountRupees)
                            listOf(
                                "Part 1 (Ready)" to 0,
                                "Part 2 (+3w)" to 1,
                                "Part 3 (+6w)" to 2,
                                "Part 4 (+9w)" to 3
                            ).forEach { (label, partIdx) ->
                                val isCompleted = partIdx < completedSells
                                val isActive = partIdx == completedSells
                                val surfaceColor = when {
                                    isCompleted -> MaterialTheme.colorScheme.secondaryContainer
                                    isActive -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                val textColor = when {
                                    isCompleted -> MaterialTheme.colorScheme.onSecondaryContainer
                                    isActive -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val displayLabel = if (isCompleted) "✓ Part ${partIdx + 1}" else label

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(6.dp),
                                    color = surfaceColor,
                                    border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = displayLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (isCompleted) "Completed" else partAmt,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = textColor.copy(alpha = 0.9f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No active 4-part staged concentration reduction plans.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Crash Accumulation Target: Equity Ex-Top 100",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Triggers: ≥ -5% Dip",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reserve cash pool is earmarked to deploy into Equity Ex-Top 100 during market drops:\n• Tier-1 (5-8% dip): ₹30,000 | Tier-2 (8-12% dip): ₹30,000 | Tier-3 (>12% dip): ₹40,000",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Fund-by-Fund Plain Language Cards
        rebalancingAnalysisList.forEach { analysis ->
            PlainFundRebalancingCard(analysis = analysis, numFmt = numFmt, currFmt = currFmt)
        }
    }
}

@Composable
fun TopNextActionCard(
    rebalancingAnalysisList: List<FundRebalancingAnalysis>
) {
    val actionSnippets = rebalancingAnalysisList.mapNotNull { it.nextActionSummarySnippet }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (actionSnippets.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                             else Color(0xFFD1FAE5)
        ),
        border = BorderStroke(
            1.5.dp,
            if (actionSnippets.isNotEmpty()) MaterialTheme.colorScheme.primary
            else Color(0xFF10B981)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (actionSnippets.size > 1) "YOUR NEXT ACTIONS" else "YOUR NEXT ACTION",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
                color = if (actionSnippets.isNotEmpty()) MaterialTheme.colorScheme.primary else Color(0xFF047857),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (actionSnippets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actionSnippets.forEachIndexed { index, snippet ->
                        val prefix = if (actionSnippets.size > 1) "${index + 1}. " else ""
                        Text(
                            text = "$prefix$snippet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Text(
                    text = "Your portfolio is within target ranges. No action needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF065F46)
                )
            }
        }
    }
}

@Composable
fun PlainFundRebalancingCard(
    analysis: FundRebalancingAnalysis,
    numFmt: DecimalFormat,
    currFmt: DecimalFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            analysis.status.contentColor.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = analysis.cardHeader,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = analysis.status.containerColor
                ) {
                    Text(
                        text = "Status: ${analysis.statusText}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = analysis.status.contentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = analysis.schemeName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "\"${analysis.actionHeadlineText}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!analysis.plainReasonExplanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = analysis.plainReasonExplanation,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = analysis.rupeeGapSummaryText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun exportPnlAnalysisReport(
    context: Context,
    portfolioSummary: LifetimePortfolioPnlSummary?,
    fundSummaries: List<ReturnsCalculations.LifetimeFundPnl>,
    riskMetricsList: List<FundRiskMetrics>,
    rebalancingAnalysisList: List<FundRebalancingAnalysis>,
    txns: List<CasTransactionEntity>,
    selectedStartDateMillis: Long
) {
    val currFmt = DecimalFormat("#,##,##0.00")
    val numFmt = DecimalFormat("0.00")
    val sdfDate = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH)
    val sdfShort = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
    val exportTimestamp = sdfDate.format(Date())
    val startDateStr = sdfShort.format(Date(selectedStartDateMillis))

    val sb = java.lang.StringBuilder()

    sb.appendLine("==========================================================================================")
    sb.appendLine("                 QSIF PROFIT & LOSS AND RISK ANALYSIS VALIDATION REPORT                   ")
    sb.appendLine("==========================================================================================")
    sb.appendLine("Export Generated On     : $exportTimestamp IST")
    sb.appendLine("Evaluation Start Date   : $startDateStr")
    sb.appendLine("Target Portfolio Scope  : Quant SIF (QSIF) Funds Only")
    sb.appendLine("Report Purpose          : Complete Financial Data & Mathematical Logic Third-Party Validation")
    sb.appendLine("==========================================================================================\n")

    // NEW SECTION: EXECUTIVE SUMMARY & TOP RECOMMENDED ACTIONS
    sb.appendLine("------------------------------------------------------------------------------------------")
    sb.appendLine("SECTION 0: EXECUTIVE SUMMARY & TOP RECOMMENDED ACTIONS")
    sb.appendLine("------------------------------------------------------------------------------------------")
    
    val actionSnippets = rebalancingAnalysisList.mapNotNull { it.nextActionSummarySnippet }
    if (actionSnippets.isNotEmpty()) {
        sb.appendLine("TOP ACTIONS REQUIRED:")
        actionSnippets.forEachIndexed { index, snippet ->
            sb.appendLine("  ${index + 1}. $snippet")
        }
    } else {
        sb.appendLine("PORTFOLIO STATUS: Your portfolio is within target ranges. No immediate rebalancing actions required.")
    }
    
    val concentrationAlerts = riskMetricsList.filter { it.isConcentrationFlagged }
    if (concentrationAlerts.isNotEmpty()) {
        sb.appendLine("\nCRITICAL INSIGHTS:")
        concentrationAlerts.forEach { fund ->
            sb.appendLine("  • CONCENTRATION ALERT: ${fund.schemeName} accounts for ${numFmt.format(fund.portfolioWeightPct)}% of total value (Threshold: 50%).")
        }
    }

    val churnLossAlerts = riskMetricsList.filter { it.realizedPnL < 0 }
    if (churnLossAlerts.isNotEmpty()) {
        sb.appendLine("\nCHURN & REALIZED LOSS OBSERVATIONS:")
        churnLossAlerts.forEach { fund ->
            sb.appendLine("  • ${fund.schemeName} has realized losses of ₹${currFmt.format(fund.realizedPnL)} in this evaluation window.")
        }
    }
    
    sb.appendLine("\nOVERALL PORTFOLIO HEALTH:")
    if (portfolioSummary != null) {
        sb.appendLine("  • Current Valuation: ₹${currFmt.format(portfolioSummary.totalCurrentValue)}")
        sb.appendLine("  • Lifetime XIRR: ${numFmt.format(portfolioSummary.lifetimeXirr)}%")
        sb.appendLine("  • Net Absolute P&L: ₹${currFmt.format(portfolioSummary.totalAbsolutePnL)} (${numFmt.format(portfolioSummary.totalAbsolutePnLPct)}%)")
    }
    sb.appendLine("------------------------------------------------------------------------------------------\n")

    // SECTION 1: LIFETIME PORTFOLIO P&L SUMMARY (TAB 1)
    sb.appendLine("------------------------------------------------------------------------------------------")
    sb.appendLine("SECTION 1: LIFETIME PORTFOLIO OVERALL P&L SUMMARY (P&L Analysis Tab)")
    sb.appendLine("------------------------------------------------------------------------------------------")
    if (portfolioSummary != null) {
        sb.appendLine("Total Invested / Purchases   : ₹${currFmt.format(portfolioSummary.totalPurchases)}")
        sb.appendLine("Total Redemptions / Proceeds : ₹${currFmt.format(portfolioSummary.totalRedemptions)}")
        sb.appendLine("Current Portfolio Value      : ₹${currFmt.format(portfolioSummary.totalCurrentValue)}")
        sb.appendLine("Total Realized P&L           : ₹${currFmt.format(portfolioSummary.totalRealizedPnL)}")
        sb.appendLine("Total Unrealized P&L         : ₹${currFmt.format(portfolioSummary.totalUnrealizedPnL)}")
        sb.appendLine("Total Absolute P&L (Net)     : ₹${currFmt.format(portfolioSummary.totalAbsolutePnL)} (${numFmt.format(portfolioSummary.totalAbsolutePnLPct)}%)")
        sb.appendLine("Lifetime Portfolio XIRR      : ${numFmt.format(portfolioSummary.lifetimeXirr)}%")
        sb.appendLine("Active / Total QSIF Schemes  : ${portfolioSummary.activeFundsCount} Active / ${portfolioSummary.totalFundsCount} Total")
    } else {
        sb.appendLine("No portfolio summary data available.")
    }
    sb.appendLine()

    // SECTION 2: BREAKDOWN BY QSIF SCHEME (TAB 1)
    sb.appendLine("------------------------------------------------------------------------------------------")
    sb.appendLine("SECTION 2: INDIVIDUAL QSIF SCHEME BREAKDOWN & CAS TRANSACTIONS (P&L Analysis Tab)")
    sb.appendLine("------------------------------------------------------------------------------------------")
    if (fundSummaries.isEmpty()) {
        sb.appendLine("No QSIF fund summaries found.")
    } else {
        fundSummaries.forEachIndexed { index, fund ->
            sb.appendLine("------------------------------------------------------------------------------------------")
            sb.appendLine("SCHEME #${index + 1}: ${fund.schemeName}")
            sb.appendLine("------------------------------------------------------------------------------------------")
            val userAvgCost = if (fund.currentUnits > 0) fund.costOfCurrentHolding / fund.currentUnits else 0.0
            val unrealizedPct = if (fund.costOfCurrentHolding > 0) (fund.unrealizedPnL / fund.costOfCurrentHolding) * 100.0 else 0.0

            sb.appendLine("Status                 : ${if (fund.isFullyExited) "Fully Exited" else "Active"}")
            sb.appendLine("Current Holding Units  : ${DecimalFormat("#,##0.0000").format(fund.currentUnits)}")
            sb.appendLine("Average Cost NAV       : ₹${currFmt.format(userAvgCost)}")
            sb.appendLine("Current NAV            : ₹${currFmt.format(fund.latestNav)} (As of ${fund.navDate})")
            sb.appendLine("Total Purchases        : ₹${currFmt.format(fund.totalPurchases)}")
            sb.appendLine("Total Redemptions      : ₹${currFmt.format(fund.totalRedemptions)}")
            sb.appendLine("Current Value          : ₹${currFmt.format(fund.currentValue)}")
            sb.appendLine("Realized P&L           : ₹${currFmt.format(fund.realizedPnL)}")
            sb.appendLine("Unrealized P&L         : ₹${currFmt.format(fund.unrealizedPnL)} (${numFmt.format(unrealizedPct)}%)")
            sb.appendLine("Fund Specific XIRR     : ${numFmt.format(fund.lifetimeXirr)}%")
            sb.appendLine()

            // Scheme Transactions
            val schemeTxns = txns.filter { 
                it.schemeName.contains(fund.schemeName, ignoreCase = true) || 
                fund.schemeName.contains(it.schemeName, ignoreCase = true) 
            }.sortedBy { it.date }

            sb.appendLine("   TRANSACTION HISTORY (${schemeTxns.size} Transactions):")
            if (schemeTxns.isEmpty()) {
                sb.appendLine("   No transactions recorded.")
            } else {
                sb.appendLine("   " + String.format("%-12s | %-12s | %-12s | %-10s | %-14s | %-30s", "Date", "Type", "Units", "NAV (₹)", "Amount (₹)", "Description"))
                sb.appendLine("   " + "-".repeat(100))
                schemeTxns.forEach { t ->
                    sb.appendLine("   " + String.format("%-12s | %-12s | %-12.4f | %-10.2f | %-14.2f | %-30s", 
                        t.date, t.type, t.units, t.nav, t.amount, t.description.take(30)))
                }
            }
            sb.appendLine()
        }
    }

    // SECTION 3: P&L RISK ANALYSIS METRICS (TAB 2)
    sb.appendLine("------------------------------------------------------------------------------------------")
    sb.appendLine("SECTION 3: FUND RISK METRICS & QUANTITATIVE COMPARISON TABLE (P&L Risk Analysis Tab)")
    sb.appendLine("------------------------------------------------------------------------------------------")
    if (riskMetricsList.isEmpty()) {
        sb.appendLine("No risk metrics computed yet.")
    } else {
        riskMetricsList.forEachIndexed { idx, m ->
            sb.appendLine("------------------------------------------------------------------------------------------")
            sb.appendLine("RISK METRICS #${idx + 1}: ${m.schemeName}")
            sb.appendLine("------------------------------------------------------------------------------------------")
            sb.appendLine("Portfolio Weight (%)   : ${numFmt.format(m.portfolioWeightPct)}% ${if (m.isConcentrationFlagged) "[CONCENTRATION ALERT > 50%]" else ""}")
            sb.appendLine("Current Holding Value  : ₹${currFmt.format(m.currentValue)}")
            sb.appendLine("Total Purchases / Cost : ₹${currFmt.format(m.totalPurchases)}")
            sb.appendLine("Realized P&L           : ₹${currFmt.format(m.realizedPnL)}")
            sb.appendLine("Churn Loss Ratio (%)   : ${numFmt.format(m.churnLossRatioPct)}%")
            sb.appendLine("Annualized Volatility  : ${numFmt.format(m.annualizedVolatilityPct)}%")
            sb.appendLine("CAGR (%)               : ${numFmt.format(m.cagrPct)}%")
            sb.appendLine("Sharpe Ratio (Rf=6.5%) : ${numFmt.format(m.sharpeRatio)}")
            sb.appendLine("Sortino Ratio          : ${numFmt.format(m.sortinoRatio)}")
            sb.appendLine("Downside Deviation     : ${numFmt.format(m.downsideDevPct)}%")
            sb.appendLine("Max Drawdown           : -${numFmt.format(kotlin.math.abs(m.maxDrawdownPct))}%")
            sb.appendLine("   Peak Date           : ${m.maxDdPeakDate}")
            sb.appendLine("   Trough Date         : ${m.maxDdTroughDate}")
            sb.appendLine("   Days to Trough      : ${m.daysToTrough} days")
            sb.appendLine("   Days to Recovery    : ${if (m.daysToRecovery < 0) "Not Yet Recovered (Active Open Cycle)" else "${m.daysToRecovery} days"}")
            sb.appendLine("30-Day Rolling Windows Stats (Sliding Session Window):")
            sb.appendLine("   Mean Return         : ${numFmt.format(m.rollingMeanPct)}%")
            sb.appendLine("   Median Return       : ${numFmt.format(m.rollingMedianPct)}%")
            sb.appendLine("   Std Deviation       : ${numFmt.format(m.rollingStdDevPct)}%")
            sb.appendLine("   Min Return (Worst)  : ${numFmt.format(m.rollingMinPct)}%")
            sb.appendLine("   Max Return (Best)   : ${numFmt.format(m.rollingMaxPct)}%")
            sb.appendLine("   Negative Windows    : ${numFmt.format(m.percentNegativeWindows)}%")
            sb.appendLine("Return-to-Risk Ratio   : ${numFmt.format(m.returnRiskRatio)}")
            sb.appendLine()
        }
    }

    // SECTION 4: REBALANCING & STAGED CONCENTRATION PLAN & RESERVE CASH POOL
    sb.appendLine("------------------------------------------------------------------------------------------")
    sb.appendLine("SECTION 4: REBALANCING & STAGED CONCENTRATION PLAN & RESERVE CASH POOL")
    sb.appendLine("------------------------------------------------------------------------------------------")
    if (rebalancingAnalysisList.isEmpty()) {
        sb.appendLine("No rebalancing analysis available.")
    } else {
        rebalancingAnalysisList.forEachIndexed { idx, r ->
            sb.appendLine("REBALANCING ITEM #${idx + 1}: ${r.schemeName}")
            sb.appendLine("  Card Header          : ${r.cardHeader}")
            sb.appendLine("  Engine Trigger Status: ${r.statusText} (${r.status.name})")
            sb.appendLine("  Action Headline      : \"${r.actionHeadlineText}\"")
            sb.appendLine("  Current Weight vs Target: ${numFmt.format(r.currentWeightPct)}% (Target Range: ${r.targetMinWeightPct.toInt()}% - ${r.targetMaxWeightPct.toInt()}%)")
            sb.appendLine("  Current Fund Value   : ₹${currFmt.format(r.currentValue)}")
            sb.appendLine("  Target Mid Value     : ₹${currFmt.format(r.targetMidPct / 100.0 * r.totalPortfolioValue)}")
            sb.appendLine("  Trim Amount (Rupees) : ₹${currFmt.format(r.trimAmountRupees)}")
            if (!r.plainReasonExplanation.isNullOrBlank()) {
                sb.appendLine("  Detailed Explanation : ${r.plainReasonExplanation.replace("\n", "\n                        ")}")
            }
            sb.appendLine("  Rupee Gap Summary    : ${r.rupeeGapSummaryText}")
            sb.appendLine("  Target Caption       : ${r.targetCaptionText}")
            sb.appendLine()
        }
    }

    // SECTION 5: SATELLITE CRASH ACCUMULATION STRATEGY RULES
    sb.appendLine("------------------------------------------------------------------------------------------")
    sb.appendLine("SECTION 5: SATELLITE CRASH ACCUMULATION STRATEGY RULES (Equity Ex-Top 100)")
    sb.appendLine("------------------------------------------------------------------------------------------")
    sb.appendLine("Target Scheme : Equity Ex-Top 100 Long-Short Fund")
    sb.appendLine("Purpose       : Deploy accumulated reserve cash pool during market crashes.")
    sb.appendLine("Trigger Logic : Data-triggered buy recommendations when fund drawdown >= 5% from peak NAV.")
    sb.appendLine("Tiers & Allocation Rules:")
    sb.appendLine("  • Tier-1 Dip (Drawdown  5.0% to  7.9%): Deploy ₹30,000 from Reserve Cash")
    sb.appendLine("  • Tier-2 Dip (Drawdown  8.0% to 11.9%): Deploy ₹30,000 from Reserve Cash")
    sb.appendLine("  • Tier-3 Dip (Drawdown >= 12.0%)       : Deploy ₹40,000 from Reserve Cash")
    sb.appendLine("Core Fund Note: Active Asset Allocator remains a Core fund focusing on base position building from rebalancing proceeds.")
    sb.appendLine()

    // SECTION 6: MATHEMATICAL LOGIC, FORMULAS & DERIVATION RULES (FOR VALIDATION)
    sb.appendLine("==========================================================================================")
    sb.appendLine("SECTION 6: MATHEMATICAL LOGIC, FORMULAS & DERIVATION RULES (FOR VALIDATION)")
    sb.appendLine("==========================================================================================")
    sb.appendLine("1. REALIZED P&L (FIFO METHOD):")
    sb.appendLine("   - Every purchase transaction creates a FIFO unit lot.")
    sb.appendLine("   - When a redemption occurs, units are matched against the earliest unexhausted purchase lots.")
    sb.appendLine("   - Realized PnL = Redemption Proceeds - sum(Cost Basis of Matched Lots).")
    sb.appendLine()
    sb.appendLine("2. UNREALIZED P&L:")
    sb.appendLine("   - Unrealized PnL = (Current NAV - User Avg Cost NAV) * Current Holding Units.")
    sb.appendLine("   - User Avg Cost NAV = Total Unredeemed Purchase Cost / Current Holding Units.")
    sb.appendLine()
    sb.appendLine("3. ABSOLUTE P&L & P&L %:")
    sb.appendLine("   - Total Absolute PnL = Total Realized PnL + Total Unrealized PnL.")
    sb.appendLine("   - Absolute PnL % = (Total Absolute PnL / Total Purchases) * 100.")
    sb.appendLine()
    sb.appendLine("4. XIRR (EXTENDED INTERNAL RATE OF RETURN):")
    sb.appendLine("   - Solves for annual rate r in: Sum_i [ Cashflow_i / (1 + r)^((Date_i - Date_0)/365) ] = 0.")
    sb.appendLine("   - Purchases are negative cashflows; redemptions and current portfolio valuation are positive.")
    sb.appendLine()
    sb.appendLine("5. CHURN LOSS RATIO (%):")
    sb.appendLine("   - Measures drag from premature buys/sells relative to buy-and-hold.")
    sb.appendLine("   - Realized Churn Loss = Realized PnL (if negative).")
    sb.appendLine("   - Churn Loss Ratio % = (Realized Churn Loss / Total Purchases) * 100.")
    sb.appendLine()
    sb.appendLine("6. PORTFOLIO WEIGHT (%):")
    sb.appendLine("   - Portfolio Weight % = (Current Scheme Value / Total Portfolio Value) * 100.")
    sb.appendLine("   - Concentration Alert Flag: Raised if any single strategy weight > 50.0%.")
    sb.appendLine()
    sb.appendLine("7. ANNUALIZED VOLATILITY (%):")
    sb.appendLine("   - Daily Return r_t = ln(NAV_t / NAV_{t-1}).")
    sb.appendLine("   - Volatility = StandardDeviation(r_t) * sqrt(252) * 100.")
    sb.appendLine()
    sb.appendLine("8. CAGR (%):")
    sb.appendLine("   - CAGR = (Latest NAV / First NAV) ^ (365 / Total Days) - 1.")
    sb.appendLine()
    sb.appendLine("9. SHARPE RATIO:")
    sb.appendLine("   - Sharpe = (CAGR - RiskFreeRate 6.5%) / Annualized Volatility.")
    sb.appendLine()
    sb.appendLine("10. SORTINO RATIO:")
    sb.appendLine("    - Sortino = (CAGR - RiskFreeRate 6.5%) / Downside Deviation.")
    sb.appendLine("    - Downside Deviation considers only daily returns below daily equivalent of 6.5%.")
    sb.appendLine()
    sb.appendLine("11. MAX DRAWDOWN (%):")
    sb.appendLine("    - Max Drawdown % = Max_t [ (Peak NAV_t - NAV_t) / Peak NAV_t ] * 100.")
    sb.appendLine()
    sb.appendLine("12. 30-DAY ROLLING WINDOW METRICS:")
    sb.appendLine("    - For every trading session t >= 30, Return_30d = (NAV_t - NAV_{t-30}) / NAV_{t-30} * 100.")
    sb.appendLine("    - Negative Windows % = (Count of Return_30d < 0 / Total Windows) * 100.")
    sb.appendLine()
    sb.appendLine("13. 4-PART STAGED CONCENTRATION REDUCTION PLAN:")
    sb.appendLine("    - Triggered when fund weight > targetMaxWeight.")
    sb.appendLine("    - Total Trim Amount = (Current Weight % - Target Mid %) * Total Portfolio Value.")
    sb.appendLine("    - Installment Amount = Total Trim Amount / 4.")
    sb.appendLine("    - Execution cadence: 1 installment per tranche, spaced by >= 7 sessions or -2% NAV drops.")
    sb.appendLine()
    sb.appendLine("14. FUND EXTENSION PROFIT BOOKING TRIGGER & CORE FUND MANDATE PROTECTION:")
    sb.appendLine("    - Fund Extension % = (Current NAV - Trough NAV) / Trough NAV * 100 over open cycle.")
    sb.appendLine("    - Profit Booking Trigger: Fund Extension >= +25.0% and open rally duration > 60 days.")
    sb.appendLine("    - Action: Recommend selling 20% of holding into reserve cash pool.")
    sb.appendLine("    - CORE FUND MANDATE PROTECTION RULE: Sell triggers (including profit booking) are STRICTLY SUPPRESSED on core compounding funds (Active Asset Allocator) while underweight (weight < 55.0%). Rebalancing inflows from overweight funds are directed here to accumulate base.")
    sb.appendLine("==========================================================================================")
    sb.appendLine("                                   END OF REPORT                                          ")
    sb.appendLine("==========================================================================================")

    try {
        val file = File(context.cacheDir, "QSIF_PnL_and_Risk_Analysis_Validation_Report.txt")
        file.writeText(sb.toString())

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "QSIF Profit & Loss Analysis Validation Report")
            putExtra(Intent.EXTRA_TEXT, "Attached is the comprehensive QSIF P&L and Risk Analysis Validation Report containing all portfolio data, risk metrics, rebalancing plans, and mathematical formulas for third-party validation.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Export QSIF P&L Analysis Report")
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to export report: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

