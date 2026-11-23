package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.min

/**
 * Decorated transaction with calculated FIFO cost deduction and cashflow parameters.
 */
data class DecoratedMonthlyTxn(
    val txn: CasTransactionEntity,
    val parsedDate: Date?,
    val monthKey: String,          // "2026-09"
    val monthLabel: String,        // "Sep 2026"
    val schemeName: String,
    val isBuy: Boolean,
    val isSell: Boolean,
    val investmentAmount: Double,  // Inflow purchase cost
    val fifoCostDeducted: Double,  // FIFO cost basis deducted on redemption
    val redemptionProceeds: Double,// Cash proceeds on redemption (units * nav or signed amount)
    val realizedGain: Double,      // redemptionProceeds - fifoCostDeducted
    val units: Double,
    val nav: Double
)

/**
 * Monthly contribution for an individual mutual fund scheme.
 */
data class MonthlyFundContribution(
    val schemeName: String,
    val monthKey: String,
    val monthLabel: String,
    val investmentAmount: Double,
    val fifoCostDeducted: Double,
    val redemptionProceeds: Double,
    val netAmount: Double,         // investmentAmount - fifoCostDeducted
    val transactions: List<DecoratedMonthlyTxn>
)

/**
 * Data item representing monthly cash flow aggregation with FIFO accounting.
 */
data class MonthlyCashFlowItem(
    val monthKey: String,          // "2026-09"
    val monthLabel: String,        // "Sep 2026"
    val year: Int,
    val monthIndex: Int,           // 0..11
    val investmentAmount: Double,  // Total purchases/inflows for the month
    val redemptionAmount: Double,  // Total FIFO cost deductions for the month
    val netAmount: Double,         // investmentAmount - redemptionAmount (FIFO Net)
    val redemptionProceeds: Double,// Total realized proceeds on redemption
    val buyCount: Int,
    val sellCount: Int,
    val fundContributions: List<MonthlyFundContribution> = emptyList()
)

/**
 * Helper object to compute FIFO-accurate monthly cash flows and fund contributions.
 */
object MonthlyCashflowCalculator {

    private val dateParsers = listOf(
        SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
        SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
        SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
        SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH),
        SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
    )

    fun parseDate(str: String): Date? {
        val trimmed = str.trim()
        for (p in dateParsers) {
            try {
                p.isLenient = false
                val res = p.parse(trimmed)
                if (res != null) return res
            } catch (_: Exception) {}
        }
        return null
    }

    fun canonicalSchemeName(scheme: String): String {
        val l = scheme.lowercase(Locale.ENGLISH).trim()
        return when {
            l.contains("income plus") || l.contains("arbitrage active fof") || l.contains("arbitrage active") -> "Quant Income Plus Arbitrage Active FoF - Direct (G)"
            l.contains("ex-top") || l.contains("ex top") -> "Equity Ex-Top 100 Long-Short Fund"
            l.contains("active asset") || l.contains("allocator") -> "Active Asset Allocator Long-Short Fund"
            l.contains("sector rotation") -> "Sector Rotation Long-Short Fund"
            l.contains("hybrid") -> "Hybrid Long-Short Fund"
            l.contains("equity long-short") || l.contains("equity long short") -> "Equity Long-Short Fund"
            else -> scheme.trim()
        }
    }

    private data class FifoLot(val units: Double, val costPerUnit: Double, val date: Date)

    /**
     * Runs scheme-by-scheme FIFO matching to accurately compute purchase inflows and
     * FIFO cost deductions for redemptions, ensuring Overall Net strictly matches Total Invested.
     */
    fun computeMonthlyCashFlow(
        txns: List<CasTransactionEntity>,
        investedItems: List<FundUiItem>
    ): List<MonthlyCashFlowItem> {
        val allDecoratedTxns = mutableListOf<DecoratedMonthlyTxn>()

        if (txns.isNotEmpty()) {
            // Group transactions by canonical scheme name
            val groupedByScheme = txns.groupBy { canonicalSchemeName(it.schemeName) }

            for ((schemeName, schemeTxns) in groupedByScheme) {
                // Sort chronologically by transaction date
                val sortedTxns = schemeTxns.sortedBy {
                    parseDate(it.date)?.time ?: 0L
                }

                val activeLots = mutableListOf<FifoLot>()

                for (t in sortedTxns) {
                    val d = parseDate(t.date) ?: Date(0)
                    val cal = Calendar.getInstance().apply { time = d }
                    val yr = cal.get(Calendar.YEAR)
                    val mo = cal.get(Calendar.MONTH)
                    val monthKey = String.format(Locale.ENGLISH, "%04d-%02d", yr, mo + 1)
                    val monthLabel = SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(d)

                    val typeLower = t.type.lowercase().trim()
                    val descLower = t.description.lowercase().trim()

                    val isSell = typeLower == "sell" ||
                            typeLower.contains("redemption") ||
                            typeLower.contains("withdrawal") ||
                            typeLower.contains("switch out") ||
                            typeLower.contains("shift out") ||
                            descLower.contains("redemption") ||
                            descLower.contains("switch out") ||
                            descLower.contains("shift out") ||
                            t.units < 0 ||
                            t.amount < 0

                    val isBuy = !isSell && (
                            typeLower == "buy" ||
                                    typeLower.contains("purchase") ||
                                    typeLower.contains("sip") ||
                                    typeLower.contains("allotment") ||
                                    typeLower.contains("switch in") ||
                                    typeLower.contains("shift in") ||
                                    descLower.contains("purchase") ||
                                    descLower.contains("sip") ||
                                    (t.units > 0 && t.amount > 0)
                            )

                    if (isBuy && t.units > 0) {
                        val grossAmount = if (abs(t.amount) > 0) abs(t.amount) else abs(t.units) * t.nav
                        val units = abs(t.units)
                        val cpu = if (units > 0) grossAmount / units else t.nav
                        activeLots.add(FifoLot(units, cpu, d))

                        allDecoratedTxns.add(
                            DecoratedMonthlyTxn(
                                txn = t,
                                parsedDate = d,
                                monthKey = monthKey,
                                monthLabel = monthLabel,
                                schemeName = schemeName,
                                isBuy = true,
                                isSell = false,
                                investmentAmount = grossAmount,
                                fifoCostDeducted = 0.0,
                                redemptionProceeds = 0.0,
                                realizedGain = 0.0,
                                units = units,
                                nav = t.nav
                            )
                        )
                    } else if (isSell) {
                        val unitsToSell = abs(t.units)
                        val redemptionProceeds = if (abs(t.amount) > 0) abs(t.amount) else unitsToSell * t.nav

                        // Accurate FIFO matching against active unexhausted purchase lots
                        var remainingToConsume = unitsToSell
                        var sellCostBasis = 0.0
                        val remainingLots = mutableListOf<FifoLot>()

                        for (lot in activeLots) {
                            if (remainingToConsume <= 0) {
                                remainingLots.add(lot)
                                continue
                            }
                            val take = min(lot.units, remainingToConsume)
                            sellCostBasis += take * lot.costPerUnit
                            remainingToConsume -= take

                            if (lot.units - take > 1e-9) {
                                remainingLots.add(FifoLot(lot.units - take, lot.costPerUnit, lot.date))
                            }
                        }
                        activeLots.clear()
                        activeLots.addAll(remainingLots)

                        // Fallback if sell occurred prior to imported purchase history
                        if (sellCostBasis <= 0.0 && unitsToSell > 0) {
                            sellCostBasis = if (t.nav > 0) unitsToSell * t.nav else redemptionProceeds
                        }

                        val realizedGain = redemptionProceeds - sellCostBasis

                        allDecoratedTxns.add(
                            DecoratedMonthlyTxn(
                                txn = t,
                                parsedDate = d,
                                monthKey = monthKey,
                                monthLabel = monthLabel,
                                schemeName = schemeName,
                                isBuy = false,
                                isSell = true,
                                investmentAmount = 0.0,
                                fifoCostDeducted = sellCostBasis,
                                redemptionProceeds = redemptionProceeds,
                                realizedGain = realizedGain,
                                units = unitsToSell,
                                nav = t.nav
                            )
                        )
                    } else {
                        // Informational or zero-unit line
                        allDecoratedTxns.add(
                            DecoratedMonthlyTxn(
                                txn = t,
                                parsedDate = d,
                                monthKey = monthKey,
                                monthLabel = monthLabel,
                                schemeName = schemeName,
                                isBuy = false,
                                isSell = false,
                                investmentAmount = 0.0,
                                fifoCostDeducted = 0.0,
                                redemptionProceeds = 0.0,
                                realizedGain = 0.0,
                                units = abs(t.units),
                                nav = t.nav
                            )
                        )
                    }
                }
            }
        } else if (investedItems.isNotEmpty()) {
            // Fallback synthesis from manually entered funds when CAS is not imported
            for (item in investedItems) {
                val d = parseDate(item.fund.navDate) ?: Date()
                val cal = Calendar.getInstance().apply { time = d }
                val yr = cal.get(Calendar.YEAR)
                val mo = cal.get(Calendar.MONTH)
                val monthKey = String.format(Locale.ENGLISH, "%04d-%02d", yr, mo + 1)
                val monthLabel = SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(d)
                val schemeName = canonicalSchemeName(item.fund.schemeName)

                allDecoratedTxns.add(
                    DecoratedMonthlyTxn(
                        txn = CasTransactionEntity(
                            id = item.schemeName.hashCode(),
                            schemeName = schemeName,
                            date = item.fund.navDate,
                            description = "Manual Initial Investment",
                            amount = item.investedAmount,
                            units = item.unitsHeld,
                            nav = item.fund.navValue,
                            balance = item.unitsHeld,
                            type = "Buy"
                        ),
                        parsedDate = d,
                        monthKey = monthKey,
                        monthLabel = monthLabel,
                        schemeName = schemeName,
                        isBuy = true,
                        isSell = false,
                        investmentAmount = item.investedAmount,
                        fifoCostDeducted = 0.0,
                        redemptionProceeds = 0.0,
                        realizedGain = 0.0,
                        units = item.unitsHeld,
                        nav = item.fund.navValue
                    )
                )
            }
        }

        // Group decorated transactions by monthKey
        val groupedByMonth = allDecoratedTxns.groupBy { it.monthKey }

        return groupedByMonth.map { (monthKey, monthTxns) ->
            val firstTxn = monthTxns.first()
            val d = firstTxn.parsedDate ?: Date(0)
            val cal = Calendar.getInstance().apply { time = d }
            val yr = cal.get(Calendar.YEAR)
            val mo = cal.get(Calendar.MONTH)
            val monthLabel = firstTxn.monthLabel

            val totalInvestment = monthTxns.filter { it.isBuy }.sumOf { it.investmentAmount }
            val totalFifoRedemption = monthTxns.filter { it.isSell }.sumOf { it.fifoCostDeducted }
            val totalProceeds = monthTxns.filter { it.isSell }.sumOf { it.redemptionProceeds }
            val buyCount = monthTxns.count { it.isBuy }
            val sellCount = monthTxns.count { it.isSell }

            // Group transactions by fund within this month
            val fundContributions = monthTxns.groupBy { it.schemeName }.map { (schemeName, fTxns) ->
                val fInv = fTxns.filter { it.isBuy }.sumOf { it.investmentAmount }
                val fRed = fTxns.filter { it.isSell }.sumOf { it.fifoCostDeducted }
                val fProc = fTxns.filter { it.isSell }.sumOf { it.redemptionProceeds }
                MonthlyFundContribution(
                    schemeName = schemeName,
                    monthKey = monthKey,
                    monthLabel = monthLabel,
                    investmentAmount = fInv,
                    fifoCostDeducted = fRed,
                    redemptionProceeds = fProc,
                    netAmount = fInv - fRed,
                    transactions = fTxns.sortedByDescending { it.parsedDate?.time ?: 0L }
                )
            }.sortedByDescending { it.investmentAmount + it.fifoCostDeducted }

            MonthlyCashFlowItem(
                monthKey = monthKey,
                monthLabel = monthLabel,
                year = yr,
                monthIndex = mo,
                investmentAmount = totalInvestment,
                redemptionAmount = totalFifoRedemption,
                netAmount = totalInvestment - totalFifoRedemption,
                redemptionProceeds = totalProceeds,
                buyCount = buyCount,
                sellCount = sellCount,
                fundContributions = fundContributions
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioMonthlyCashflowScreen(
    viewModel: NavViewModel,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onMonthClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTxnsState = viewModel.getAllCasTransactions().collectAsStateWithLifecycle(
        initialValue = viewModel.getCachedTransactions()
    )
    val txns = allTxnsState.value

    val investedItems = remember(uiState) {
        when (uiState) {
            is UiState.Success -> (uiState as UiState.Success).items.filter { it.isInvested }
            else -> emptyList()
        }
    }

    // High-contrast color palette
    val backgroundColor = if (isDarkTheme) Color(0xFF0F1115) else Color(0xFFF8F9FA)
    val cardBackground = if (isDarkTheme) Color(0xFF181B20) else Color.White
    val zebraBackground = if (isDarkTheme) Color(0xFF13161A) else Color(0xFFF4F6F8)
    val headerBackground = if (isDarkTheme) Color(0xFF21252C) else Color(0xFFE9ECEF)
    val overallRowBackground = if (isDarkTheme) Color(0xFF1E2633) else Color(0xFFE3F2FD)

    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF1A1C1E)
    val textSecondary = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF616161)
    val textMuted = if (isDarkTheme) Color(0xFF757575) else Color(0xFF8D99AE)

    val investmentColor = if (isDarkTheme) Color(0xFF00E676) else Color(0xFF1B5E20)
    val redemptionColor = if (isDarkTheme) Color(0xFFFF5252) else Color(0xFFC62828)
    val netPositiveColor = if (isDarkTheme) Color(0xFF00E676) else Color(0xFF1B5E20)
    val netNegativeColor = if (isDarkTheme) Color(0xFFFF5252) else Color(0xFFC62828)
    val borderColor = if (isDarkTheme) Color(0xFF262B33) else Color(0xFFDEE2E6)

    // Sort order: true = Newest first, false = Oldest first
    var isNewestFirst by remember { mutableStateOf(true) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }

    // Parse and aggregate transactions month-by-month with accurate FIFO lot accounting
    val monthlyData = remember(txns, investedItems) {
        MonthlyCashflowCalculator.computeMonthlyCashFlow(txns, investedItems)
    }

    // Available distinct years
    val availableYears = remember(monthlyData) {
        monthlyData.map { it.year }.distinct().sortedDescending()
    }

    // Filter by year if selected
    val filteredMonths = remember(monthlyData, selectedYear, isNewestFirst) {
        val list = if (selectedYear != null) {
            monthlyData.filter { it.year == selectedYear }
        } else {
            monthlyData
        }
        if (isNewestFirst) {
            list.sortedByDescending { it.monthKey }
        } else {
            list.sortedBy { it.monthKey }
        }
    }

    // Overall Totals (FIFO Net strictly matches Total Invested on Home Screen)
    val overallInvestment = remember(filteredMonths) { filteredMonths.sumOf { it.investmentAmount } }
    val overallRedemption = remember(filteredMonths) { filteredMonths.sumOf { it.redemptionAmount } }
    val overallNet = remember(overallInvestment, overallRedemption) { overallInvestment - overallRedemption }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .testTag("portfolio_monthly_cashflow_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Monthly Cash Flow",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        )
                        Text(
                            text = "FIFO Investment, Redemption & Net Breakdown",
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
                        modifier = Modifier.testTag("monthly_cashflow_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Portfolio Snapshot",
                            tint = textPrimary
                        )
                    }
                },
                actions = {
                    // Sort order toggle button
                    Surface(
                        onClick = { isNewestFirst = !isNewestFirst },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("monthly_cashflow_sort_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isNewestFirst) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isNewestFirst) "Newest First" else "Oldest First",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isNewestFirst) "Newest" else "Oldest",
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
        containerColor = backgroundColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Year filter selector if multiple years exist
            if (availableYears.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedYear == null,
                            onClick = { selectedYear = null },
                            label = { Text("All Years (${availableYears.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("year_filter_all")
                        )
                        availableYears.forEach { yr ->
                            FilterChip(
                                selected = selectedYear == yr,
                                onClick = { selectedYear = yr },
                                label = { Text(yr.toString()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("year_filter_$yr")
                            )
                        }
                    }
                }
            }

            // Executive Summary Metric Strip
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Total Investment
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "TOTAL INVESTMENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "₹${formatIndianCommas(overallInvestment, if (overallInvestment % 1.0 > 0.001) 2 else 0)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = investmentColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(borderColor)
                        )

                        // Total Redemption Deduction (FIFO Cost)
                        Column(
                            modifier = Modifier
                                .weight(1.05f)
                                .padding(horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TOTAL REDEMPTION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (overallRedemption > 0) {
                                    "-₹${formatIndianCommas(overallRedemption, if (overallRedemption % 1.0 > 0.001) 2 else 0)}"
                                } else "₹0",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (overallRedemption > 0) redemptionColor else textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(borderColor)
                        )

                        // Overall Net (Matches Home Screen Total Invested)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "OVERALL NET",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val netPrefix = if (overallNet > 0) "+" else if (overallNet < 0) "-" else ""
                            Text(
                                text = "$netPrefix₹${formatIndianCommas(abs(overallNet), if (overallNet % 1.0 > 0.001) 2 else 0)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (overallNet >= 0) netPositiveColor else netNegativeColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // The Clean, Professional Table
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Table Column Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(headerBackground)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Month",
                                modifier = Modifier.weight(1.15f),
                                textAlign = TextAlign.Start,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 11.5.sp
                                )
                            )
                            Text(
                                text = "Investment",
                                modifier = Modifier.weight(1.15f),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 11.5.sp
                                )
                            )
                            Text(
                                text = "Redemption",
                                modifier = Modifier.weight(1.15f),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 11.5.sp
                                )
                            )
                            Text(
                                text = "Net",
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 11.5.sp
                                )
                            )
                            // Space for navigation arrow alignment
                            Spacer(modifier = Modifier.width(18.dp))
                        }

                        // Divider line under header
                        HorizontalDivider(thickness = 1.dp, color = borderColor)

                        if (filteredMonths.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No investment or redemption transactions found",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = textSecondary,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        } else {
                            // Monthly rows (clickable to view fund breakdown)
                            filteredMonths.forEachIndexed { index, item ->
                                val rowBg = if (index % 2 == 1) zebraBackground else cardBackground
                                val netSign = if (item.netAmount > 0) "+" else if (item.netAmount < 0) "-" else ""

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .clickable { onMonthClick(item.monthKey) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("monthly_row_${item.monthKey}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Month
                                    Text(
                                        text = item.monthLabel,
                                        modifier = Modifier.weight(1.15f),
                                        textAlign = TextAlign.Start,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Investment
                                    val invDec = item.investmentAmount % 1.0 > 0.001
                                    Text(
                                        text = if (item.investmentAmount > 0) {
                                            "₹${formatIndianCommas(item.investmentAmount, if (invDec) 2 else 0)}"
                                        } else "—",
                                        modifier = Modifier.weight(1.15f),
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.investmentAmount > 0) textPrimary else textMuted,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Redemption Deduction (FIFO Cost)
                                    val redDec = item.redemptionAmount % 1.0 > 0.001
                                    Text(
                                        text = if (item.redemptionAmount > 0) {
                                            "-₹${formatIndianCommas(item.redemptionAmount, if (redDec) 2 else 0)}"
                                        } else "—",
                                        modifier = Modifier.weight(1.15f),
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.redemptionAmount > 0) redemptionColor else textMuted,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Net (FIFO Net)
                                    val netDec = item.netAmount % 1.0 > 0.001
                                    Text(
                                        text = "$netSign₹${formatIndianCommas(abs(item.netAmount), if (netDec) 2 else 0)}",
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.netAmount > 0) netPositiveColor else if (item.netAmount < 0) netNegativeColor else textMuted,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Chevron Icon for drill-down affordance
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "View ${item.monthLabel} transactions",
                                        tint = textSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .padding(start = 6.dp)
                                            .size(12.dp)
                                    )
                                }

                                if (index < filteredMonths.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = borderColor.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Distinct Highlighted OVERALL Row
                            HorizontalDivider(thickness = 1.5.dp, color = borderColor)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(overallRowBackground)
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // OVERALL label
                                Text(
                                    text = "OVERALL",
                                    modifier = Modifier.weight(1.15f),
                                    textAlign = TextAlign.Start,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp
                                    )
                                )

                                // Overall Investment
                                val ovInvDec = overallInvestment % 1.0 > 0.001
                                Text(
                                    text = "₹${formatIndianCommas(overallInvestment, if (ovInvDec) 2 else 0)}",
                                    modifier = Modifier.weight(1.15f),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = investmentColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Overall Redemption (FIFO Cost Deduction)
                                val ovRedDec = overallRedemption % 1.0 > 0.001
                                Text(
                                    text = if (overallRedemption > 0) {
                                        "-₹${formatIndianCommas(overallRedemption, if (ovRedDec) 2 else 0)}"
                                    } else "₹0",
                                    modifier = Modifier.weight(1.15f),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (overallRedemption > 0) redemptionColor else textMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Overall Net (Matches Home Screen Total Invested)
                                val ovNetSign = if (overallNet > 0) "+" else if (overallNet < 0) "-" else ""
                                val ovNetDec = overallNet % 1.0 > 0.001
                                Text(
                                    text = "$ovNetSign₹${formatIndianCommas(abs(overallNet), if (ovNetDec) 2 else 0)}",
                                    modifier = Modifier.weight(1.2f),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (overallNet >= 0) netPositiveColor else netNegativeColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.width(18.dp))
                            }
                        }
                    }
                }
            }

            // Bottom spacing for comfortable padding
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
