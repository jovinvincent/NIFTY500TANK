package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Data representation of a single fund's session contribution on a specific date.
 */
data class FundSessionData(
    val dateStr: String,
    val timeMillis: Long,
    val amount: Double,
    val percent: Double,
    val nav: Double,
    val prevNav: Double,
    val units: Double
)

/**
 * Rigorous quantitative analytics metrics computed for an individual mutual fund.
 */
data class FundDailyPnlAnalytics(
    val fundItem: FundUiItem,
    val schemeName: String,
    val category: String,
    val color: Color,
    val totalSessions: Int,
    val upDays: Int,
    val downDays: Int,
    val neutralDays: Int,
    val activeTradingDays: Int,
    val winRatePct: Double,
    val totalPnlAmount: Double,
    val meanDailyPnlAmount: Double,
    val meanDailyPnlPercent: Double,
    val medianDailyPnlAmount: Double,
    val medianDailyPnlPercent: Double,
    val avgWinAmount: Double,
    val avgWinPercent: Double,
    val avgLossAmount: Double,
    val avgLossPercent: Double,
    val dailyExpectancyAmount: Double,
    val dailyExpectancyPercent: Double,
    val expectancyRatio: Double,
    val profitFactor: Double,
    val dailyVolatilityPct: Double,
    val annualizedVolatilityPct: Double,
    val bestDayAmount: Double,
    val bestDayPercent: Double,
    val bestDayDate: String,
    val worstDayAmount: Double,
    val worstDayPercent: Double,
    val worstDayDate: String,
    val currentValue: Double,
    val investedAmount: Double,
    val unitsHeld: Double,
    val meanDailyPnlPerUnit: Double,
    val meanDailyPnlPerRupee: Double,
    val medianDailyPnlPerUnit: Double,
    val medianDailyPnlPerRupee: Double
)

enum class FundSortField {
    EXPECTANCY_AMOUNT,
    EXPECTANCY_PCT,
    MEAN_AMOUNT,
    MEAN_PU,
    MEAN_PCT,
    MEDIAN_AMOUNT,
    MEDIAN_PU,
    WIN_RATE,
    VOLATILITY,
    ANNUAL_VOLATILITY,
    PROFIT_FACTOR,
    TOTAL_PNL,
    NAME
}

private val SCHEME_PALETTE = listOf(
    Color(0xFF29B6F6), // Light Blue
    Color(0xFFAB47BC), // Purple
    Color(0xFF66BB6A), // Green
    Color(0xFFFFA726), // Orange
    Color(0xFFFF7043), // Coral
    Color(0xFF26A69A), // Teal
    Color(0xFFEC407A), // Pink
    Color(0xFF8E24AA), // Deep Purple
    Color(0xFF00ACC1), // Cyan
    Color(0xFFFFB300)  // Amber
)

fun detectFundCategory(schemeName: String): String {
    val s = schemeName.lowercase(Locale.ROOT)
    return when {
        s.contains("small cap") -> "Small Cap Equity"
        s.contains("mid cap") || s.contains("midcap") -> "Mid Cap Equity"
        s.contains("large & mid") || s.contains("large and mid") -> "Large & Mid Cap"
        s.contains("large cap") || s.contains("bluechip") || s.contains("top 100") -> "Large Cap Equity"
        s.contains("flexi cap") || s.contains("flexicap") -> "Flexi Cap Equity"
        s.contains("multi cap") || s.contains("multicap") -> "Multi Cap Equity"
        s.contains("focused") -> "Focused Equity"
        s.contains("elss") || s.contains("tax") -> "ELSS Tax Saver"
        s.contains("hybrid") || s.contains("balanced advantage") || s.contains("dynamic asset") -> "Dynamic / Hybrid"
        s.contains("asset allocator") || s.contains("multi asset") -> "Multi Asset Allocator"
        s.contains("arbitrage") -> "Arbitrage Fund"
        s.contains("debt") || s.contains("liquid") || s.contains("money market") || s.contains("overnight") -> "Debt / Liquid"
        s.contains("gold") || s.contains("silver") || s.contains("commodity") -> "Commodities"
        s.contains("index") || s.contains("nifty") || s.contains("sensex") -> "Passive Index"
        s.contains("quant") -> "Quant Active"
        else -> "Equity Growth"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundwiseQuantitativeAnalyticsScreen(
    investedItems: List<FundUiItem>,
    allDailyPoints: List<DailyPnlPoint>,
    selectedRange: String,
    onRangeChanged: (String) -> Unit,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit
) {
    // Intercept hardware / gesture back
    BackHandler(enabled = true) {
        onBack()
    }

    // Color definitions
    val greenColor = if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
    val redColor = if (isDarkTheme) Color(0xFFFF3333) else Color(0xFFB71C1C)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    // View & Filter states
    var viewMode by remember { mutableStateOf("TABLE") } // "TABLE" or "CARDS"
    var isGraphicalView by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var sortField by remember { mutableStateOf(FundSortField.EXPECTANCY_AMOUNT) }
    var sortAscending by remember { mutableStateOf(false) }
    var selectedFundDetail by remember { mutableStateOf<FundDailyPnlAnalytics?>(null) }

    // Filter points by chosen time horizon
    val filteredPoints = remember(allDailyPoints, selectedRange) {
        if (allDailyPoints.isEmpty()) return@remember emptyList<DailyPnlPoint>()
        val latestTime = allDailyPoints.last().timeMillis
        val cutoff = when (selectedRange) {
            "1M" -> latestTime - 30L * 24 * 3600 * 1000
            "3M" -> latestTime - 90L * 24 * 3600 * 1000
            "6M" -> latestTime - 180L * 24 * 3600 * 1000
            "1Y" -> latestTime - 365L * 24 * 3600 * 1000
            else -> 0L
        }
        val res = allDailyPoints.filter { it.timeMillis >= cutoff }
        if (res.isNotEmpty()) res else allDailyPoints
    }

    // Compute Fund-wise Quantitative Metrics
    val fundAnalyticsList = remember(investedItems, filteredPoints) {
        if (investedItems.isEmpty() || filteredPoints.isEmpty()) return@remember emptyList<FundDailyPnlAnalytics>()

        investedItems.mapIndexed { idx, item ->
            val schemeColor = SCHEME_PALETTE[idx % SCHEME_PALETTE.size]

            // Extract session points where this fund held units
            val fundPoints = filteredPoints.mapNotNull { pt ->
                pt.schemeBreakdown.find { it.schemeName == item.schemeName }?.let { contrib ->
                    FundSessionData(
                        dateStr = pt.dateStr,
                        timeMillis = pt.timeMillis,
                        amount = contrib.amount,
                        percent = contrib.percent,
                        nav = contrib.nav,
                        prevNav = contrib.prevNav,
                        units = contrib.units
                    )
                }
            }

            val category = detectFundCategory(item.schemeName)

            if (fundPoints.isEmpty()) {
                FundDailyPnlAnalytics(
                    fundItem = item,
                    schemeName = item.schemeName,
                    category = category,
                    color = schemeColor,
                    totalSessions = 0,
                    upDays = 0,
                    downDays = 0,
                    neutralDays = 0,
                    activeTradingDays = 0,
                    winRatePct = 0.0,
                    totalPnlAmount = 0.0,
                    meanDailyPnlAmount = 0.0,
                    meanDailyPnlPercent = 0.0,
                    medianDailyPnlAmount = 0.0,
                    medianDailyPnlPercent = 0.0,
                    avgWinAmount = 0.0,
                    avgWinPercent = 0.0,
                    avgLossAmount = 0.0,
                    avgLossPercent = 0.0,
                    dailyExpectancyAmount = 0.0,
                    dailyExpectancyPercent = 0.0,
                    expectancyRatio = 0.0,
                    profitFactor = 1.0,
                    dailyVolatilityPct = 0.0,
                    annualizedVolatilityPct = 0.0,
                    bestDayAmount = 0.0,
                    bestDayPercent = 0.0,
                    bestDayDate = "-",
                    worstDayAmount = 0.0,
                    worstDayPercent = 0.0,
                    worstDayDate = "-",
                    currentValue = item.currentValue,
                    investedAmount = item.investedAmount,
                    unitsHeld = item.unitsHeld,
                    meanDailyPnlPerUnit = 0.0,
                    meanDailyPnlPerRupee = 0.0,
                    medianDailyPnlPerUnit = 0.0,
                    medianDailyPnlPerRupee = 0.0
                )
            } else {
                val totalSessions = fundPoints.size
                val upDaysList = fundPoints.filter { it.amount > 0.0001 }
                val downDaysList = fundPoints.filter { it.amount < -0.0001 }
                val neutralDaysList = fundPoints.filter { abs(it.amount) <= 0.0001 }

                val upDays = upDaysList.size
                val downDays = downDaysList.size
                val neutralDays = neutralDaysList.size
                val activeTradingDays = upDays + downDays

                val winRatePct = if (activeTradingDays > 0) {
                    (upDays.toDouble() / activeTradingDays.toDouble()) * 100.0
                } else if (totalSessions > 0) {
                    (upDays.toDouble() / totalSessions.toDouble()) * 100.0
                } else 0.0

                val totalPnlAmount = fundPoints.sumOf { it.amount }
                val meanDailyPnlAmount = fundPoints.map { it.amount }.average()
                val meanDailyPnlPercent = fundPoints.map { it.percent }.average()

                // Authentic median calculated across active trading sessions
                val pointsForMedian = if (activeTradingDays > 0) {
                    fundPoints.filter { abs(it.amount) > 0.0001 }
                } else {
                    fundPoints
                }

                val sortedAmounts = pointsForMedian.map { it.amount }.sorted()
                val medianDailyPnlAmount = if (sortedAmounts.isNotEmpty()) {
                    val mid = sortedAmounts.size / 2
                    if (sortedAmounts.size % 2 == 1) {
                        sortedAmounts[mid]
                    } else {
                        (sortedAmounts[mid - 1] + sortedAmounts[mid]) / 2.0
                    }
                } else 0.0

                val sortedPcts = pointsForMedian.map { it.percent }.sorted()
                val medianDailyPnlPercent = if (sortedPcts.isNotEmpty()) {
                    val mid = sortedPcts.size / 2
                    if (sortedPcts.size % 2 == 1) {
                        sortedPcts[mid]
                    } else {
                        (sortedPcts[mid - 1] + sortedPcts[mid]) / 2.0
                    }
                } else 0.0

                val avgWinAmount = if (upDays > 0) upDaysList.map { it.amount }.average() else 0.0
                val avgWinPercent = if (upDays > 0) upDaysList.map { it.percent }.average() else 0.0

                val avgLossAmount = if (downDays > 0) downDaysList.map { it.amount }.average() else 0.0
                val avgLossPercent = if (downDays > 0) downDaysList.map { it.percent }.average() else 0.0

                val winProb = if (activeTradingDays > 0) upDays.toDouble() / activeTradingDays.toDouble() else 0.0
                val lossProb = if (activeTradingDays > 0) downDays.toDouble() / activeTradingDays.toDouble() else 0.0

                val dailyExpectancyAmount = (winProb * avgWinAmount) + (lossProb * avgLossAmount)
                val dailyExpectancyPercent = (winProb * avgWinPercent) + (lossProb * avgLossPercent)

                val expectancyRatio = if (abs(avgLossAmount) > 0.0) {
                    ((winProb * avgWinAmount) - (lossProb * abs(avgLossAmount))) / abs(avgLossAmount)
                } else 0.0

                val sumGains = upDaysList.sumOf { it.amount }
                val sumLosses = abs(downDaysList.sumOf { it.amount })
                val profitFactor = if (sumLosses > 0.0) {
                    sumGains / sumLosses
                } else if (sumGains > 0.0) {
                    9.99
                } else {
                    1.0
                }

                val variance = if (totalSessions > 1) {
                    fundPoints.map { (it.percent - meanDailyPnlPercent).let { d -> d * d } }.sum() / (totalSessions - 1)
                } else 0.0

                val dailyVolatilityPct = sqrt(variance)
                val annualizedVolatilityPct = dailyVolatilityPct * sqrt(252.0)

                val bestDay = fundPoints.maxByOrNull { it.amount }
                val worstDay = fundPoints.minByOrNull { it.amount }

                val unitsHeld = item.unitsHeld
                val meanDailyPnlPerUnit = if (unitsHeld > 0.0) meanDailyPnlAmount / unitsHeld else 0.0
                val meanDailyPnlPerRupee = if (item.investedAmount > 0.0) {
                    meanDailyPnlAmount / item.investedAmount
                } else if (item.currentValue > 0.0) {
                    meanDailyPnlAmount / item.currentValue
                } else {
                    meanDailyPnlPercent / 100.0
                }

                val medianDailyPnlPerUnit = if (unitsHeld > 0.0) medianDailyPnlAmount / unitsHeld else 0.0
                val medianDailyPnlPerRupee = if (item.investedAmount > 0.0) {
                    medianDailyPnlAmount / item.investedAmount
                } else if (item.currentValue > 0.0) {
                    medianDailyPnlAmount / item.currentValue
                } else {
                    medianDailyPnlPercent / 100.0
                }

                FundDailyPnlAnalytics(
                    fundItem = item,
                    schemeName = item.schemeName,
                    category = category,
                    color = schemeColor,
                    totalSessions = totalSessions,
                    upDays = upDays,
                    downDays = downDays,
                    neutralDays = neutralDays,
                    activeTradingDays = activeTradingDays,
                    winRatePct = winRatePct,
                    totalPnlAmount = totalPnlAmount,
                    meanDailyPnlAmount = meanDailyPnlAmount,
                    meanDailyPnlPercent = meanDailyPnlPercent,
                    medianDailyPnlAmount = medianDailyPnlAmount,
                    medianDailyPnlPercent = medianDailyPnlPercent,
                    avgWinAmount = avgWinAmount,
                    avgWinPercent = avgWinPercent,
                    avgLossAmount = avgLossAmount,
                    avgLossPercent = avgLossPercent,
                    dailyExpectancyAmount = dailyExpectancyAmount,
                    dailyExpectancyPercent = dailyExpectancyPercent,
                    expectancyRatio = expectancyRatio,
                    profitFactor = profitFactor,
                    dailyVolatilityPct = dailyVolatilityPct,
                    annualizedVolatilityPct = annualizedVolatilityPct,
                    bestDayAmount = bestDay?.amount ?: 0.0,
                    bestDayPercent = bestDay?.percent ?: 0.0,
                    bestDayDate = bestDay?.dateStr ?: "-",
                    worstDayAmount = worstDay?.amount ?: 0.0,
                    worstDayPercent = worstDay?.percent ?: 0.0,
                    worstDayDate = worstDay?.dateStr ?: "-",
                    currentValue = item.currentValue,
                    investedAmount = item.investedAmount,
                    unitsHeld = unitsHeld,
                    meanDailyPnlPerUnit = meanDailyPnlPerUnit,
                    meanDailyPnlPerRupee = meanDailyPnlPerRupee,
                    medianDailyPnlPerUnit = medianDailyPnlPerUnit,
                    medianDailyPnlPerRupee = medianDailyPnlPerRupee
                )
            }
        }
    }

    // Filter by search query
    val searchedList = remember(fundAnalyticsList, searchQuery) {
        if (searchQuery.isBlank()) {
            fundAnalyticsList
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            fundAnalyticsList.filter {
                it.schemeName.lowercase(Locale.ROOT).contains(q) ||
                it.category.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    // Sort list according to chosen column & direction
    val sortedList = remember(searchedList, sortField, sortAscending) {
        val comparator = when (sortField) {
            FundSortField.EXPECTANCY_AMOUNT -> compareBy<FundDailyPnlAnalytics> { it.dailyExpectancyAmount }
            FundSortField.EXPECTANCY_PCT -> compareBy { it.dailyExpectancyPercent }
            FundSortField.MEAN_AMOUNT -> compareBy { it.meanDailyPnlAmount }
            FundSortField.MEAN_PU -> compareBy { it.meanDailyPnlPerUnit }
            FundSortField.MEAN_PCT -> compareBy { it.meanDailyPnlPercent }
            FundSortField.MEDIAN_AMOUNT -> compareBy { it.medianDailyPnlAmount }
            FundSortField.MEDIAN_PU -> compareBy { it.medianDailyPnlPerUnit }
            FundSortField.WIN_RATE -> compareBy { it.winRatePct }
            FundSortField.VOLATILITY -> compareBy { it.dailyVolatilityPct }
            FundSortField.ANNUAL_VOLATILITY -> compareBy { it.annualizedVolatilityPct }
            FundSortField.PROFIT_FACTOR -> compareBy { it.profitFactor }
            FundSortField.TOTAL_PNL -> compareBy { it.totalPnlAmount }
            FundSortField.NAME -> compareBy { it.schemeName.lowercase(Locale.ROOT) }
        }
        if (sortAscending) searchedList.sortedWith(comparator) else searchedList.sortedWith(comparator.reversed())
    }

    // Executive best-in-class highlights
    val bestEvFund = remember(fundAnalyticsList) { fundAnalyticsList.maxByOrNull { it.dailyExpectancyAmount } }
    val bestWinRateFund = remember(fundAnalyticsList) { fundAnalyticsList.maxByOrNull { it.winRatePct } }
    val lowestVolFund = remember(fundAnalyticsList) {
        fundAnalyticsList.filter { it.dailyVolatilityPct > 0.0 }.minByOrNull { it.dailyVolatilityPct }
    }
    val bestPfFund = remember(fundAnalyticsList) { fundAnalyticsList.maxByOrNull { it.profitFactor } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Fund-wise Return Analytics",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Quantitative Comparison • $selectedRange (${filteredPoints.size} sessions)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = onSurfaceVariantColor,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("fundwise_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Historical Analytics"
                        )
                    }
                },
                actions = {
                    // Toggle Graphical vs Data view
                    IconButton(
                        onClick = { isGraphicalView = !isGraphicalView },
                        modifier = Modifier.testTag("graphical_view_toggle")
                    ) {
                        Icon(
                            imageVector = if (isGraphicalView) Icons.Default.List else Icons.Default.BarChart,
                            contentDescription = "Toggle Graphical View",
                            tint = primaryColor
                        )
                    }
                    // Toggle Table vs Cards view
                    IconButton(
                        onClick = { viewMode = if (viewMode == "TABLE") "CARDS" else "TABLE" },
                        modifier = Modifier.testTag("fundwise_view_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (viewMode == "TABLE") Icons.Default.ViewAgenda else Icons.Default.TableChart,
                            contentDescription = "Toggle View Mode (${if (viewMode == "TABLE") "Cards" else "Table"})",
                            tint = primaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor,
                    titleContentColor = onSurfaceColor
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))

                // Time Range Horizon Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTORICAL HORIZON",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceVariantColor,
                            letterSpacing = 0.5.sp,
                            fontSize = 10.5.sp
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("1M", "3M", "6M", "1Y", "ALL").forEach { rangeKey ->
                            val isSelected = (selectedRange == rangeKey)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) primaryColor else surfaceColor,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) primaryColor else outlineVariantColor
                                ),
                                modifier = Modifier
                                    .testTag("fundwise_range_$rangeKey")
                                    .clickable { onRangeChanged(rangeKey) }
                            ) {
                                Text(
                                    text = rangeKey,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else onSurfaceColor
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Executive Highlights Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (bestEvFund != null) {
                        ExecutiveHighlightChip(
                            title = "TOP EXPECTANCY",
                            fundName = bestEvFund.schemeName,
                            badge = String.format(Locale.US, "+₹%s/day", formatIndianCommas(bestEvFund.dailyExpectancyAmount, 0)),
                            subBadge = String.format(Locale.US, "+%.2f%% edge", bestEvFund.dailyExpectancyPercent),
                            accentColor = greenColor,
                            onClick = { selectedFundDetail = bestEvFund }
                        )
                    }

                    if (bestWinRateFund != null) {
                        ExecutiveHighlightChip(
                            title = "HIGHEST WIN RATE",
                            fundName = bestWinRateFund.schemeName,
                            badge = String.format(Locale.US, "%.1f%%", bestWinRateFund.winRatePct),
                            subBadge = "${bestWinRateFund.upDays}W / ${bestWinRateFund.downDays}L",
                            accentColor = primaryColor,
                            onClick = { selectedFundDetail = bestWinRateFund }
                        )
                    }

                    if (lowestVolFund != null) {
                        ExecutiveHighlightChip(
                            title = "LOWEST VOLATILITY",
                            fundName = lowestVolFund.schemeName,
                            badge = String.format(Locale.US, "±%.2f%%", lowestVolFund.dailyVolatilityPct),
                            subBadge = String.format(Locale.US, "%.1f%% ann.", lowestVolFund.annualizedVolatilityPct),
                            accentColor = Color(0xFF00ACC1),
                            onClick = { selectedFundDetail = lowestVolFund }
                        )
                    }

                    if (bestPfFund != null) {
                        ExecutiveHighlightChip(
                            title = "TOP PROFIT FACTOR",
                            fundName = bestPfFund.schemeName,
                            badge = String.format(Locale.US, "%.2fx", bestPfFund.profitFactor),
                            subBadge = "Gross W/L",
                            accentColor = Color(0xFFAB47BC),
                            onClick = { selectedFundDetail = bestPfFund }
                        )
                    }
                }
            }

            // Search Bar & Sort Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fundwise_search_input"),
                        placeholder = {
                            Text(
                                "Search fund name or category...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = onSurfaceVariantColor,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = onSurfaceVariantColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = outlineVariantColor
                        )
                    )

                    // Sort Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SORT BY:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = onSurfaceVariantColor,
                                fontSize = 10.sp
                            )
                        )

                        // Direction Toggle Button
                        Surface(
                            onClick = { sortAscending = !sortAscending },
                            shape = RoundedCornerShape(8.dp),
                            color = primaryColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                            modifier = Modifier.testTag("fundwise_sort_direction_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = if (sortAscending) "Ascending" else "Descending",
                                    tint = primaryColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (sortAscending) "Asc" else "Desc",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = primaryColor
                                    )
                                )
                            }
                        }

                        listOf(
                            FundSortField.EXPECTANCY_AMOUNT to "EV (₹)",
                            FundSortField.WIN_RATE to "Win Rate",
                            FundSortField.MEAN_AMOUNT to "Mean P&L",
                            FundSortField.MEAN_PU to "Mean PU",
                            FundSortField.MEDIAN_AMOUNT to "Median",
                            FundSortField.MEDIAN_PU to "Median PU",
                            FundSortField.VOLATILITY to "Daily Vol (σ)",
                            FundSortField.ANNUAL_VOLATILITY to "Annual Vol",
                            FundSortField.PROFIT_FACTOR to "Profit Factor",
                            FundSortField.TOTAL_PNL to "Total P&L",
                            FundSortField.NAME to "Fund Name"
                        ).forEach { (field, label) ->
                            val isSelected = (sortField == field)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) primaryColor else surfaceColor,
                                border = BorderStroke(1.dp, if (isSelected) primaryColor else outlineVariantColor),
                                modifier = Modifier
                                    .testTag("fundwise_sort_${field.name}")
                                    .clickable {
                                        if (sortField == field) {
                                            sortAscending = !sortAscending
                                        } else {
                                            sortField = field
                                            sortAscending = (field == FundSortField.VOLATILITY || field == FundSortField.ANNUAL_VOLATILITY || field == FundSortField.NAME)
                                        }
                                    }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.5.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else onSurfaceColor
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Main Content: Table View vs Card View
            if (isGraphicalView) {
                item {
                    GraphicalAnalyticsView(sortedList, greenColor, redColor, primaryColor)
                }
            } else {
                if (viewMode == "TABLE") {
                    item {
                        FundwiseComparisonTable(
                            analyticsList = sortedList,
                            sortField = sortField,
                            sortAscending = sortAscending,
                            onSortClick = { field ->
                                if (sortField == field) {
                                    sortAscending = !sortAscending
                                } else {
                                    sortField = field
                                    sortAscending = (field == FundSortField.VOLATILITY || field == FundSortField.ANNUAL_VOLATILITY || field == FundSortField.NAME)
                                }
                            },
                            onFundClick = { fund -> selectedFundDetail = fund },
                            greenColor = greenColor,
                            redColor = redColor,
                            surfaceColor = surfaceColor,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = onSurfaceVariantColor,
                            outlineVariantColor = outlineVariantColor
                        )
                    }
                } else {
                    // Card View
                    items(sortedList) { fundAnalytics ->
                        FundwiseAnalyticsCard(
                            fund = fundAnalytics,
                            greenColor = greenColor,
                            redColor = redColor,
                            surfaceColor = surfaceColor,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = onSurfaceVariantColor,
                            outlineVariantColor = outlineVariantColor,
                            onClick = { selectedFundDetail = fundAnalytics }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Detail Dialog on Fund Click
    selectedFundDetail?.let { detail ->
        FundDetailModal(
            fund = detail,
            onDismiss = { selectedFundDetail = null },
            greenColor = greenColor,
            redColor = redColor,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            onSurfaceVariantColor = onSurfaceVariantColor
        )
    }
}

/**
 * Top executive comparison chip displaying the winner in key categories.
 */
@Composable
private fun ExecutiveHighlightChip(
    title: String,
    fundName: String,
    badge: String,
    subBadge: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = accentColor,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = fundName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = accentColor
                    )
                )
                Text(
                    text = subBadge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/**
 * Horizontal-scrollable, responsive quantitative comparison table with fixed headers and aligned columns.
 */
@Composable
private fun FundwiseComparisonTable(
    analyticsList: List<FundDailyPnlAnalytics>,
    sortField: FundSortField,
    sortAscending: Boolean,
    onSortClick: (FundSortField) -> Unit,
    onFundClick: (FundDailyPnlAnalytics) -> Unit,
    greenColor: Color,
    redColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    outlineVariantColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, outlineVariantColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Table Header Title Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "QUANTITATIVE PERFORMANCE MATRIX",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
                Text(
                    text = "Tap header to sort • PU = Per Unit (₹/u & per ₹1K)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        color = onSurfaceVariantColor
                    )
                )
            }

            HorizontalDivider(color = outlineVariantColor)

            // Scrollable table container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell(
                            text = "MUTUAL FUND",
                            width = 175.dp,
                            field = FundSortField.NAME,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "DAILY EV",
                            width = 110.dp,
                            field = FundSortField.EXPECTANCY_AMOUNT,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "MEAN RETURN",
                            width = 105.dp,
                            field = FundSortField.MEAN_AMOUNT,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "MEAN PU",
                            width = 115.dp,
                            field = FundSortField.MEAN_PU,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "MEDIAN",
                            width = 100.dp,
                            field = FundSortField.MEDIAN_AMOUNT,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "MEDIAN PU",
                            width = 115.dp,
                            field = FundSortField.MEDIAN_PU,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "DAILY VOL (σ)",
                            width = 100.dp,
                            field = FundSortField.VOLATILITY,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "ANNUAL VOL",
                            width = 95.dp,
                            field = FundSortField.ANNUAL_VOLATILITY,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "WIN RATE",
                            width = 115.dp,
                            field = FundSortField.WIN_RATE,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "PROFIT FACTOR",
                            width = 95.dp,
                            field = FundSortField.PROFIT_FACTOR,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "TOTAL P&L",
                            width = 110.dp,
                            field = FundSortField.TOTAL_PNL,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                        TableHeaderCell(
                            text = "BEST / WORST",
                            width = 110.dp,
                            field = null,
                            currentSort = sortField,
                            ascending = sortAscending,
                            onClick = onSortClick
                        )
                    }

                    HorizontalDivider(color = outlineVariantColor)

                    // Data Rows
                    analyticsList.forEachIndexed { index, fund ->
                        val isAlternate = (index % 2 == 1)
                        val rowBg = if (isAlternate) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f) else Color.Transparent

                        Row(
                            modifier = Modifier
                                .background(rowBg)
                                .clickable { onFundClick(fund) }
                                .padding(vertical = 9.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Fund Column
                            Row(
                                modifier = Modifier.width(175.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(fund.color)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fund.schemeName,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = fund.category,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            color = onSurfaceVariantColor
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // 2. Daily EV (Amount & Edge %)
                            val isEvUp = fund.dailyExpectancyAmount >= 0.0
                            val evCol = if (isEvUp) greenColor else redColor
                            val evPfx = if (isEvUp) "+" else ""
                            Column(modifier = Modifier.width(110.dp)) {
                                Text(
                                    text = String.format(Locale.US, "$evPfx₹%s", formatIndianCommas(fund.dailyExpectancyAmount, 1)),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = evCol
                                    )
                                )
                                Text(
                                    text = String.format(Locale.US, "$evPfx%.3f%% edge", fund.dailyExpectancyPercent),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }

                            // 3. Mean Return
                            val isMeanUp = fund.meanDailyPnlAmount >= 0.0
                            val meanCol = if (isMeanUp) greenColor else redColor
                            val meanPfx = if (isMeanUp) "+" else ""
                            Column(modifier = Modifier.width(105.dp)) {
                                Text(
                                    text = String.format(Locale.US, "$meanPfx₹%s", formatIndianCommas(fund.meanDailyPnlAmount, 1)),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = meanCol
                                    )
                                )
                                Text(
                                    text = String.format(Locale.US, "$meanPfx%.2f%%", fund.meanDailyPnlPercent),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }

                            // 4. Mean PU Return (Per Unit / ₹1 Basis)
                            val isMeanPuUp = fund.meanDailyPnlPerUnit >= 0.0
                            val meanPuCol = if (isMeanPuUp) greenColor else redColor
                            val meanPuPfx = if (isMeanPuUp) "+" else ""
                            Column(modifier = Modifier.width(115.dp)) {
                                val puUnitStr = if (fund.unitsHeld > 0.0) {
                                    String.format(Locale.US, "$meanPuPfx₹%.3f/u", fund.meanDailyPnlPerUnit)
                                } else {
                                    String.format(Locale.US, "$meanPuPfx₹%.2f/1K", fund.meanDailyPnlPerRupee * 1000.0)
                                }
                                Text(
                                    text = puUnitStr,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = meanPuCol
                                    )
                                )
                                val puRupeeStr = String.format(Locale.US, "$meanPuPfx₹%.2f/1K", fund.meanDailyPnlPerRupee * 1000.0)
                                Text(
                                    text = puRupeeStr,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }

                            // 5. Median Return
                            val isMedUp = fund.medianDailyPnlAmount >= 0.0
                            val medCol = if (isMedUp) greenColor else redColor
                            val medPfx = if (isMedUp) "+" else ""
                            Column(modifier = Modifier.width(100.dp)) {
                                Text(
                                    text = String.format(Locale.US, "$medPfx₹%s", formatIndianCommas(fund.medianDailyPnlAmount, 0)),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = medCol
                                    )
                                )
                                Text(
                                    text = String.format(Locale.US, "$medPfx%.2f%%", fund.medianDailyPnlPercent),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }

                            // 6. Median PU Return (Per Unit / ₹1 Basis)
                            val isMedPuUp = fund.medianDailyPnlPerUnit >= 0.0
                            val medPuCol = if (isMedPuUp) greenColor else redColor
                            val medPuPfx = if (isMedPuUp) "+" else ""
                            Column(modifier = Modifier.width(115.dp)) {
                                val medUnitStr = if (fund.unitsHeld > 0.0) {
                                    String.format(Locale.US, "$medPuPfx₹%.3f/u", fund.medianDailyPnlPerUnit)
                                } else {
                                    String.format(Locale.US, "$medPuPfx₹%.2f/1K", fund.medianDailyPnlPerRupee * 1000.0)
                                }
                                Text(
                                    text = medUnitStr,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = medPuCol
                                    )
                                )
                                val medRupeeStr = String.format(Locale.US, "$medPuPfx₹%.2f/1K", fund.medianDailyPnlPerRupee * 1000.0)
                                Text(
                                    text = medRupeeStr,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }

                            // 5. Daily Volatility (σ)
                            Text(
                                text = String.format(Locale.US, "±%.2f%%", fund.dailyVolatilityPct),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.width(100.dp)
                            )

                            // 6. Annualized Volatility
                            Text(
                                text = String.format(Locale.US, "%.1f%%", fund.annualizedVolatilityPct),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (fund.annualizedVolatilityPct > 15.0) redColor else onSurfaceColor
                                ),
                                modifier = Modifier.width(95.dp)
                            )

                            // 7. Win Rate
                            Column(modifier = Modifier.width(115.dp)) {
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", fund.winRatePct),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (fund.winRatePct >= 50.0) greenColor else redColor
                                    )
                                )
                                Text(
                                    text = "${fund.upDays}W / ${fund.downDays}L",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }

                            // 8. Profit Factor
                            Text(
                                text = String.format(Locale.US, "%.2fx", fund.profitFactor),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (fund.profitFactor >= 1.2) greenColor else if (fund.profitFactor >= 1.0) onSurfaceColor else redColor
                                ),
                                modifier = Modifier.width(95.dp)
                            )

                            // 9. Total P&L
                            val isTotUp = fund.totalPnlAmount >= 0.0
                            val totCol = if (isTotUp) greenColor else redColor
                            val totPfx = if (isTotUp) "+" else ""
                            Column(modifier = Modifier.width(110.dp)) {
                                Text(
                                    text = String.format(Locale.US, "$totPfx₹%s", formatIndianCommas(fund.totalPnlAmount, 0)),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = totCol
                                    )
                                )
                                val totPct = if (fund.investedAmount > 0.0) (fund.totalPnlAmount / fund.investedAmount) * 100.0 else 0.0
                                Text(
                                    text = String.format(Locale.US, "$totPfx%.2f%%", totPct),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }

                            // 10. Best / Worst Day
                            Column(modifier = Modifier.width(110.dp)) {
                                Text(
                                    text = String.format(Locale.US, "+%.2f%%", fund.bestDayPercent),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = greenColor
                                    )
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f%%", fund.worstDayPercent),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = redColor
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = outlineVariantColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

/**
 * Clickable header cell supporting ascending / descending sort indicator.
 */
@Composable
private fun TableHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    field: FundSortField?,
    currentSort: FundSortField,
    ascending: Boolean,
    onClick: (FundSortField) -> Unit
) {
    val isCurrent = (field != null && field == currentSort)
    Row(
        modifier = Modifier
            .width(width)
            .clickable(enabled = field != null) { field?.let { onClick(it) } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                fontSize = 10.sp,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isCurrent) {
            Icon(
                imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

/**
 * Modern M3 card displaying an executive quantitative dashboard for an individual fund.
 */
@Composable
private fun FundwiseAnalyticsCard(
    fund: FundDailyPnlAnalytics,
    greenColor: Color,
    redColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    outlineVariantColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, outlineVariantColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Fund Name, Category, and Assigned Color Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(fund.color)
                    )
                    Column {
                        Text(
                            text = fund.schemeName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = fund.category,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${fund.totalSessions} sessions",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = onSurfaceVariantColor
                                )
                            )
                        }
                    }
                }

                // Current Valuation & Total Gain
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${formatIndianCommas(fund.currentValue, 0)}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    )
                    val isTotUp = fund.totalPnlAmount >= 0.0
                    val totCol = if (isTotUp) greenColor else redColor
                    val totPfx = if (isTotUp) "+" else ""
                    Text(
                        text = "$totPfx₹${formatIndianCommas(fund.totalPnlAmount, 0)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = totCol,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }

            HorizontalDivider(color = outlineVariantColor)

            // 4-Tile Executive Grid: EV, Mean/Median, Volatility, Win Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tile 1: EV
                val isEvUp = fund.dailyExpectancyAmount >= 0.0
                val evCol = if (isEvUp) greenColor else redColor
                val evPfx = if (isEvUp) "+" else ""
                SubMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "DAILY EXPECTATION (EV)",
                    primaryText = String.format(Locale.US, "$evPfx₹%s", formatIndianCommas(fund.dailyExpectancyAmount, 1)),
                    primaryColor = evCol,
                    subText = String.format(Locale.US, "Edge: $evPfx%.3f%% / session", fund.dailyExpectancyPercent),
                    surfaceColor = surfaceColor,
                    borderColor = evCol.copy(alpha = 0.25f)
                )

                // Tile 2: Mean & Median
                val isMeanUp = fund.meanDailyPnlAmount >= 0.0
                val meanCol = if (isMeanUp) greenColor else redColor
                val meanPfx = if (isMeanUp) "+" else ""
                SubMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "MEAN DAILY RETURN",
                    primaryText = String.format(Locale.US, "$meanPfx₹%s", formatIndianCommas(fund.meanDailyPnlAmount, 1)),
                    primaryColor = meanCol,
                    subText = String.format(
                        Locale.US,
                        "PU: %s₹%.3f/u • Med: %s₹%s",
                        if (fund.meanDailyPnlPerUnit >= 0) "+" else "",
                        fund.meanDailyPnlPerUnit,
                        if (fund.medianDailyPnlAmount >= 0) "+" else "",
                        formatIndianCommas(fund.medianDailyPnlAmount, 0)
                    ),
                    surfaceColor = surfaceColor,
                    borderColor = outlineVariantColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tile 3: Daily Volatility (σ)
                SubMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "DAILY VARIATION (σ)",
                    primaryText = String.format(Locale.US, "±%.2f%%", fund.dailyVolatilityPct),
                    primaryColor = onSurfaceColor,
                    subText = String.format(Locale.US, "Annualized Vol: %.1f%%", fund.annualizedVolatilityPct),
                    surfaceColor = surfaceColor,
                    borderColor = outlineVariantColor
                )

                // Tile 4: Win Rate & Profit Factor
                SubMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "WIN RATE & PROFIT FACTOR",
                    primaryText = String.format(Locale.US, "%.1f%% (%dW/%dL)", fund.winRatePct, fund.upDays, fund.downDays),
                    primaryColor = if (fund.winRatePct >= 50.0) greenColor else redColor,
                    subText = String.format(Locale.US, "Profit Factor: %.2fx", fund.profitFactor),
                    surfaceColor = surfaceColor,
                    borderColor = if (fund.profitFactor >= 1.2) greenColor.copy(alpha = 0.25f) else outlineVariantColor
                )
            }
        }
    }
}

@Composable
private fun SubMetricTile(
    modifier: Modifier = Modifier,
    title: String,
    primaryText: String,
    primaryColor: Color,
    subText: String,
    surfaceColor: Color,
    borderColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.3.sp
                )
            )
            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = primaryColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Deep-dive dialog showing exhaustive statistics for an individual fund.
 */
@Composable
private fun FundDetailModal(
    fund: FundDailyPnlAnalytics,
    onDismiss: () -> Unit,
    greenColor: Color,
    redColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Column {
                Text(
                    text = fund.schemeName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${fund.category} • Quantitative Performance Card",
                    style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(
                    label = "Mathematical Expectancy (EV)",
                    value = String.format(Locale.US, "%s₹%s / session", if (fund.dailyExpectancyAmount >= 0) "+" else "", formatIndianCommas(fund.dailyExpectancyAmount, 2)),
                    valueColor = if (fund.dailyExpectancyAmount >= 0) greenColor else redColor
                )
                DetailRow(
                    label = "Expectancy Edge %",
                    value = String.format(Locale.US, "%s%.3f%% / session", if (fund.dailyExpectancyPercent >= 0) "+" else "", fund.dailyExpectancyPercent),
                    valueColor = if (fund.dailyExpectancyPercent >= 0) greenColor else redColor
                )
                DetailRow(
                    label = "Van Tharp Expectancy (R)",
                    value = String.format(Locale.US, "%s%.2f R", if (fund.expectancyRatio >= 0) "+" else "", fund.expectancyRatio),
                    valueColor = if (fund.expectancyRatio >= 0) greenColor else redColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                DetailRow(
                    label = "Mean Daily Return",
                    value = String.format(Locale.US, "%s₹%s (%.2f%%)", if (fund.meanDailyPnlAmount >= 0) "+" else "", formatIndianCommas(fund.meanDailyPnlAmount, 2), fund.meanDailyPnlPercent),
                    valueColor = if (fund.meanDailyPnlAmount >= 0) greenColor else redColor
                )
                DetailRow(
                    label = "Mean PU Return (Per Unit)",
                    value = String.format(Locale.US, "%s₹%.4f / unit (%s₹%.2f per ₹1K)", if (fund.meanDailyPnlPerUnit >= 0) "+" else "", fund.meanDailyPnlPerUnit, if (fund.meanDailyPnlPerRupee >= 0) "+" else "", fund.meanDailyPnlPerRupee * 1000.0),
                    valueColor = if (fund.meanDailyPnlPerUnit >= 0) greenColor else redColor
                )
                DetailRow(
                    label = "Median Daily Return",
                    value = String.format(Locale.US, "%s₹%s (%.2f%%)", if (fund.medianDailyPnlAmount >= 0) "+" else "", formatIndianCommas(fund.medianDailyPnlAmount, 2), fund.medianDailyPnlPercent),
                    valueColor = if (fund.medianDailyPnlAmount >= 0) greenColor else redColor
                )
                DetailRow(
                    label = "Median PU Return (Per Unit)",
                    value = String.format(Locale.US, "%s₹%.4f / unit (%s₹%.2f per ₹1K)", if (fund.medianDailyPnlPerUnit >= 0) "+" else "", fund.medianDailyPnlPerUnit, if (fund.medianDailyPnlPerRupee >= 0) "+" else "", fund.medianDailyPnlPerRupee * 1000.0),
                    valueColor = if (fund.medianDailyPnlPerUnit >= 0) greenColor else redColor
                )
                DetailRow(
                    label = "Units Held & Capital Base",
                    value = String.format(Locale.US, "%,.3f units • ₹%s", fund.unitsHeld, formatIndianCommas(fund.investedAmount, 0)),
                    valueColor = onSurfaceColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                DetailRow(
                    label = "Daily Volatility (σ)",
                    value = String.format(Locale.US, "±%.2f%%", fund.dailyVolatilityPct),
                    valueColor = onSurfaceColor
                )
                DetailRow(
                    label = "Annualized Volatility (σ × √252)",
                    value = String.format(Locale.US, "%.2f%%", fund.annualizedVolatilityPct),
                    valueColor = onSurfaceColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                DetailRow(
                    label = "Win Rate (Active Sessions)",
                    value = String.format(Locale.US, "%.1f%% (%dW / %dL / %dFlat)", fund.winRatePct, fund.upDays, fund.downDays, fund.neutralDays),
                    valueColor = if (fund.winRatePct >= 50.0) greenColor else redColor
                )
                DetailRow(
                    label = "Profit Factor",
                    value = String.format(Locale.US, "%.2fx", fund.profitFactor),
                    valueColor = if (fund.profitFactor >= 1.2) greenColor else onSurfaceColor
                )
                DetailRow(
                    label = "Average Win Amount",
                    value = String.format(Locale.US, "+₹%s (+%.2f%%)", formatIndianCommas(fund.avgWinAmount, 2), fund.avgWinPercent),
                    valueColor = greenColor
                )
                DetailRow(
                    label = "Average Loss Amount",
                    value = String.format(Locale.US, "-₹%s (%.2f%%)", formatIndianCommas(abs(fund.avgLossAmount), 2), fund.avgLossPercent),
                    valueColor = redColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                DetailRow(
                    label = "Best Single Day",
                    value = String.format(Locale.US, "+₹%s (+%.2f%%) on %s", formatIndianCommas(fund.bestDayAmount, 0), fund.bestDayPercent, fund.bestDayDate),
                    valueColor = greenColor
                )
                DetailRow(
                    label = "Worst Single Day",
                    value = String.format(Locale.US, "-₹%s (%.2f%%) on %s", formatIndianCommas(abs(fund.worstDayAmount), 0), fund.worstDayPercent, fund.worstDayDate),
                    valueColor = redColor
                )
            }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                color = valueColor
            )
        )
    }

}

