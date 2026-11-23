package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

enum class FvCalculationMode(val label: String, val description: String) {
    EXPECTED_XIRR("Expected XIRR", "Each fund compounded at the lower of its Expected XIRR or Current XIRR"),
    ONGOING_FUND_XIRR("Ongoing Fund XIRR", "Each fund compounded at its own actual ongoing XIRR"),
    PORTFOLIO_XIRR("Portfolio XIRR", "All funds compounded at aggregate portfolio XIRR")
}

object FundFutureValueCalculator {
    fun calculateFundOngoingXirr(
        item: FundUiItem,
        allTxns: List<CasTransactionEntity>,
        xirrBaseCal: Calendar,
        parseDate: (String) -> Date?
    ): Double {
        val txns = ReturnsCalculations.filterTxnsForScheme(allTxns, item.schemeName)
        return if (txns.isNotEmpty()) {
            val navDate = parseDate(item.fund.navDate) ?: Date()
            val calcXirr = ReturnsCalculations.calculateXirr(txns, item.fund.navValue, item.unitsHeld, item.investedAmount, navDate)
            if (calcXirr == 0.0) {
                calculatePortfolioXirr(item.investedAmount, item.currentValue, item.fund.navDate, xirrBaseCal)
            } else {
                calcXirr
            }
        } else {
            calculatePortfolioXirr(item.investedAmount, item.currentValue, item.fund.navDate, xirrBaseCal)
        }
    }

    fun calculateFundExpectedXirr(
        schemeName: String,
        ongoingXirr: Double
    ): Double {
        val expectedFromModel = calculateExpectedXirrForScheme(schemeName)
        return if (expectedFromModel != 0.0 && !expectedFromModel.isNaN() && !expectedFromModel.isInfinite()) {
            expectedFromModel
        } else {
            if (ongoingXirr.isNaN() || ongoingXirr.isInfinite() || ongoingXirr <= 0.0) 15.0 else ongoingXirr
        }
    }

    fun getConservativeRate(expectedXirr: Double, ongoingXirr: Double, portfolioXirr: Double): Double {
        val validExpected = if (expectedXirr.isNaN() || expectedXirr.isInfinite()) 15.0 else expectedXirr
        val validOngoing = if (ongoingXirr.isNaN() || ongoingXirr.isInfinite()) validExpected else ongoingXirr
        val validPortfolio = if (portfolioXirr.isNaN() || portfolioXirr.isInfinite()) validExpected else portfolioXirr
        return minOf(validExpected, validOngoing, validPortfolio)
    }

    fun calculateFundFutureValue(
        currentValue: Double,
        ratePercent: Double,
        years: Double
    ): Double {
        if (years <= 0.0 || currentValue <= 0.0) return currentValue
        val r = ratePercent / 100.0
        if (r <= -1.0) return 0.0
        val factor = Math.pow(1.0 + r, years)
        return if (factor.isInfinite() || factor.isNaN()) Double.MAX_VALUE else currentValue * factor
    }

    private val fvSumCache = java.util.concurrent.ConcurrentHashMap<String, Double>()

    fun calculatePortfolioSumFutureValue(
        investedItems: List<FundUiItem>,
        allTxns: List<CasTransactionEntity>,
        xirrBaseCal: Calendar,
        portfolioXirr: Double,
        years: Double,
        parseDate: (String) -> Date?
    ): Double {
        if (investedItems.isEmpty()) return 0.0
        val cacheKey = "${investedItems.size}_${allTxns.size}_${portfolioXirr.toInt()}_${years.toInt()}_${investedItems.sumOf { it.currentValue }.toLong()}"
        val cached = fvSumCache[cacheKey]
        if (cached != null) return cached

        val sum = investedItems.sumOf { item ->
            val ongoing = calculateFundOngoingXirr(item, allTxns, xirrBaseCal, parseDate)
            val expected = calculateFundExpectedXirr(item.schemeName, ongoing)
            val effectiveRate = getConservativeRate(expected, ongoing, portfolioXirr)
            calculateFundFutureValue(item.currentValue, effectiveRate, years)
        }
        fvSumCache[cacheKey] = sum
        return sum
    }
}

data class FundFutureValueProjection(
    val fundItem: FundUiItem,
    val rank: Int,
    val units: Double,
    val nav: Double,
    val investedAmount: Double,
    val currentValue: Double,
    val totalPlAbsolute: Double,
    val totalPlPercent: Double,
    val ongoingXirr: Double,
    val expectedXirr: Double,
    val effectiveRatePercent: Double,
    val futureValue: Double,
    val netWealthGain: Double,
    val wealthMultiplier: Double,
    val shareOfFuturePortfolioPercent: Double,
    val fundColor: Color
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FundFutureValueScreen(
    viewModel: NavViewModel,
    isDarkTheme: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTxnsState = viewModel.getAllCasTransactions().collectAsStateWithLifecycle(initialValue = viewModel.getCachedTransactions())
    val allTxns = allTxnsState.value

    var futureValCal by remember {
        val savedTime = sharedPreferences.getLong("future_val_time", -1L)
        val initialCal = Calendar.getInstance().apply {
            if (savedTime != -1L) {
                timeInMillis = savedTime
            } else {
                set(2030, Calendar.JANUARY, 1, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        mutableStateOf(initialCal)
    }

    var xirrBaseCal by remember {
        val savedTime = sharedPreferences.getLong("xirr_base_time", -1L)
        val initialCal = Calendar.getInstance().apply {
            if (savedTime != -1L) {
                timeInMillis = savedTime
            } else {
                set(2026, Calendar.JANUARY, 1, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        mutableStateOf(initialCal)
    }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var calculationMode by remember { mutableStateOf(FvCalculationMode.EXPECTED_XIRR) }

    val dateFormat = remember { SimpleDateFormat("dd-MMM-yyyy", Locale.US) }
    val targetDateStr = remember(futureValCal) { dateFormat.format(futureValCal.time) }

    // Normalize both current date (from) and target date (to) to midnight to ensure exact integer calendar days
    val todayCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val fromDateStr = remember(todayCal) { dateFormat.format(todayCal.time) }

    val targetCal = remember(futureValCal) {
        Calendar.getInstance().apply {
            timeInMillis = futureValCal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    // Exact calendar days between From (today) and To (target date)
    val daysDiff = remember(todayCal, targetCal) {
        ((targetCal.timeInMillis - todayCal.timeInMillis) / (1000L * 60 * 60 * 24)).coerceAtLeast(0L).toDouble()
    }
    // Standard Indian mutual fund ACT/365 financial annualization convention
    val yearsHorizon = remember(daysDiff) {
        (daysDiff / 365.0).coerceAtLeast(0.0)
    }

    val investedItems = remember(uiState) {
        when (uiState) {
            is UiState.Success -> (uiState as UiState.Success).items.filter { it.isInvested }
            else -> emptyList()
        }
    }

    val ranksMap = remember(uiState) {
        when (uiState) {
            is UiState.Success -> com.example.FundRankCache.getOrComputeRanks((uiState as UiState.Success).items)
            else -> emptyMap()
        }
    }

    val portfolioAggregateXirr = remember(investedItems, xirrBaseCal, allTxns) {
        if (investedItems.isNotEmpty()) {
            calculateAggregatePortfolioXirr(investedItems, xirrBaseCal, allTxns)
        } else 0.0
    }

    // Color palette for distinctive funds
    val fundColors = remember {
        listOf(
            Color(0xFF2196F3), // Blue
            Color(0xFF00E676), // Neon Green
            Color(0xFFFFB300), // Amber
            Color(0xFFAB47BC), // Purple
            Color(0xFF00B0FF), // Light Blue
            Color(0xFFFF7043), // Deep Orange
            Color(0xFF26A69A), // Teal
            Color(0xFFEC407A)  // Pink
        )
    }

    // Compute fund future values based on mode
    val fundProjections = remember(investedItems, allTxns, yearsHorizon, calculationMode, portfolioAggregateXirr, ranksMap) {
        val rawProjections = investedItems.mapIndexed { index, item ->
            val rank = ranksMap[item.schemeName] ?: (index + 1)
            val fundOngoingXirr = FundFutureValueCalculator.calculateFundOngoingXirr(
                item = item,
                allTxns = allTxns,
                xirrBaseCal = xirrBaseCal,
                parseDate = { dateStr -> try { dateFormat.parse(dateStr) } catch(e: Exception) { null } }
            )

            // Expected XIRR of each fund from the Forecast & Consistency card area
            val validExpectedXirr = FundFutureValueCalculator.calculateFundExpectedXirr(
                schemeName = item.schemeName,
                ongoingXirr = fundOngoingXirr
            )

            val effectiveRate = when (calculationMode) {
                FvCalculationMode.EXPECTED_XIRR -> {
                    FundFutureValueCalculator.getConservativeRate(validExpectedXirr, fundOngoingXirr, portfolioAggregateXirr)
                }
                FvCalculationMode.ONGOING_FUND_XIRR -> {
                    if (fundOngoingXirr.isNaN() || fundOngoingXirr.isInfinite()) 15.0 else fundOngoingXirr
                }
                FvCalculationMode.PORTFOLIO_XIRR -> {
                    if (portfolioAggregateXirr.isNaN() || portfolioAggregateXirr.isInfinite()) 15.0 else portfolioAggregateXirr
                }
            }

            val fv = FundFutureValueCalculator.calculateFundFutureValue(
                currentValue = item.currentValue,
                ratePercent = effectiveRate,
                years = yearsHorizon
            )

            val netGain = fv - item.currentValue
            val multiplier = if (item.currentValue > 0) fv / item.currentValue else 1.0

            FundFutureValueProjection(
                fundItem = item,
                rank = rank,
                units = item.unitsHeld,
                nav = item.fund.navValue,
                investedAmount = item.investedAmount,
                currentValue = item.currentValue,
                totalPlAbsolute = item.totalPlAbsolute,
                totalPlPercent = item.totalPlPercent,
                ongoingXirr = fundOngoingXirr,
                expectedXirr = validExpectedXirr,
                effectiveRatePercent = effectiveRate,
                futureValue = fv,
                netWealthGain = netGain,
                wealthMultiplier = multiplier,
                shareOfFuturePortfolioPercent = 0.0, // calculated next
                fundColor = fundColors[index % fundColors.size]
            )
        }.sortedBy { it.rank }

        val totalFv = rawProjections.sumOf { it.futureValue }
        rawProjections.map { proj ->
            val share = if (totalFv > 0) (proj.futureValue / totalFv) * 100.0 else 0.0
            proj.copy(shareOfFuturePortfolioPercent = share)
        }
    }

    val totalInvested = remember(fundProjections) { fundProjections.sumOf { it.investedAmount } }
    val totalCurrentValue = remember(fundProjections) { fundProjections.sumOf { it.currentValue } }
    val totalFutureValue = remember(fundProjections) { fundProjections.sumOf { it.futureValue } }
    val totalWealthGain = totalFutureValue - totalCurrentValue
    val totalMultiplier = if (totalCurrentValue > 0) totalFutureValue / totalCurrentValue else 1.0

    // Date Picker Dialog
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = futureValCal.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = millis
                        }
                        val cal = Calendar.getInstance().apply {
                            set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH),
                                12, 0, 0
                            )
                            set(Calendar.MILLISECOND, 0)
                        }
                        futureValCal = cal
                        sharedPreferences.edit().putLong("future_val_time", cal.timeInMillis).apply()
                    }
                    showDatePickerDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Future Value Projections",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = "Fund-wise compounding breakdown",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard"
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showDatePickerDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Target Date",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = targetDateStr,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = if (isDarkTheme) Color.Black else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Horizon Quick Chips
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROJECTION HORIZON",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f Years (%,d Days)", yearsHorizon, daysDiff.toLong()),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "From: $fromDateStr (Today)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        )
                        Text(
                            text = "To: $targetDateStr",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val horizons = listOf(
                            "2049 (Target)" to {
                                Calendar.getInstance().apply {
                                    set(2049, Calendar.DECEMBER, 31, 12, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                            },
                            "5Y" to {
                                Calendar.getInstance().apply {
                                    timeInMillis = todayCal.timeInMillis
                                    add(Calendar.YEAR, 5)
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                            },
                            "10Y" to {
                                Calendar.getInstance().apply {
                                    timeInMillis = todayCal.timeInMillis
                                    add(Calendar.YEAR, 10)
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                            },
                            "15Y" to {
                                Calendar.getInstance().apply {
                                    timeInMillis = todayCal.timeInMillis
                                    add(Calendar.YEAR, 15)
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                            },
                            "20Y" to {
                                Calendar.getInstance().apply {
                                    timeInMillis = todayCal.timeInMillis
                                    add(Calendar.YEAR, 20)
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                            }
                        )

                        horizons.forEach { (label, createCal) ->
                            val target = createCal()
                            val isSelected = target.get(Calendar.YEAR) == futureValCal.get(Calendar.YEAR) &&
                                (label != "2049 (Target)" || (futureValCal.get(Calendar.MONTH) == Calendar.DECEMBER && futureValCal.get(Calendar.DAY_OF_MONTH) == 31))
                            SuggestionChip(
                                onClick = {
                                    val newCal = createCal()
                                    futureValCal = newCal
                                    sharedPreferences.edit().putLong("future_val_time", newCal.timeInMillis).apply()
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                border = if (isSelected) SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Calculation Mode Selector
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "COMPOUNDING ENGINE MODE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FvCalculationMode.values().forEach { mode ->
                                val isCurrent = calculationMode == mode
                                val containerColor = if (isCurrent) {
                                    if (isDarkTheme) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)
                                } else {
                                    Color.Transparent
                                }
                                val borderColor = if (isCurrent) Color(0xFF2196F3) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                val textColor = if (isCurrent) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(containerColor)
                                        .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                                        .clickable { calculationMode = mode }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 10.5.sp,
                                            color = textColor
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = calculationMode.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }

            // Portfolio Overview Grand Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2196F3).copy(alpha = 0.5f), Color(0xFF00E676).copy(alpha = 0.5f))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AGGREGATE PORTFOLIO FUTURE VALUE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF00E676).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1fx Growth", totalMultiplier),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
                                    )
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))

                        // Large Future Value Text
                        Text(
                            text = formatIndianCurrency(totalFutureValue),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 23.sp,
                                color = if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
                            )
                        )

                        val crFormat = formatCrOrLakh(totalFutureValue)
                        if (crFormat.isNotEmpty()) {
                            Text(
                                text = "Equivalent: $crFormat",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // 3-Column Metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TOTAL INVESTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatIndianCurrency(totalInvested),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "CURRENT VALUE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatIndianCurrency(totalCurrentValue),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "NET WEALTH GAIN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "+" + formatIndianCurrency(totalWealthGain),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Portfolio Future Wealth Distribution Bar
            if (fundProjections.isNotEmpty() && totalFutureValue > 0) {
                item {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "PROJECTED CORPUS COMPOSITION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Stacked distribution bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            ) {
                                fundProjections.forEach { proj ->
                                    val weight = (proj.shareOfFuturePortfolioPercent / 100.0).toFloat().coerceAtLeast(0.01f)
                                    Box(
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .background(proj.fundColor)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Legend chips
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                fundProjections.forEach { proj ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(proj.fundColor)
                                        )
                                        Text(
                                            text = "${proj.fundItem.fund.schemeName.take(18)}... (%.1f%%)".format(Locale.US, proj.shareOfFuturePortfolioPercent),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 9.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INDIVIDUAL SCHEME BREAKDOWN",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "${fundProjections.size} Funds",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Individual Fund Cards
            itemsIndexed(fundProjections) { _, proj ->
                FundFutureValueCard(
                    proj = proj,
                    yearsHorizon = yearsHorizon,
                    targetDateStr = targetDateStr,
                    calculationMode = calculationMode,
                    isDarkTheme = isDarkTheme
                )
            }

            // Explanatory Methodology Card
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Methodology",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Compounding Methodology",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Future Value = Current Value × (1 + r)^t, where Current Value is calculated from actual CAS purchased units × latest live NAV. In Expected XIRR mode, each fund compounds using the lower of its Expected XIRR or current ongoing XIRR (min(Expected, Current)) to provide a grounded, conservative projection. Ongoing XIRR is calculated from all historical cash flows.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun FundFutureValueCard(
    proj: FundFutureValueProjection,
    yearsHorizon: Double,
    targetDateStr: String,
    calculationMode: FvCalculationMode,
    isDarkTheme: Boolean
) {
    val isPlPositive = proj.totalPlAbsolute >= 0.0
    val plColor = if (isPlPositive) {
        if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
    } else {
        if (isDarkTheme) Color(0xFFFF3333) else Color(0xFFB71C1C)
    }
    val plSign = if (isPlPositive) "+" else ""

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = proj.fundColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Rank, Scheme Name, Dynamic Rate Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank Badge
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(proj.fundColor.copy(alpha = 0.2f))
                        .border(1.dp, proj.fundColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${proj.rank}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = proj.fundColor
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Scheme Name & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = proj.fundItem.fund.schemeName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val ongoingSubtitle = if (calculationMode == FvCalculationMode.EXPECTED_XIRR) {
                        " • Exp: ${if (proj.expectedXirr >= 0.0) "+" else ""}${String.format(Locale.US, "%.1f%%", proj.expectedXirr)} | Cur: ${if (proj.ongoingXirr >= 0.0) "+" else ""}${String.format(Locale.US, "%.1f%%", proj.ongoingXirr)}"
                    } else ""
                    Text(
                        text = "NAV: ₹${proj.nav} • Units: ${String.format(Locale.US, "%,.3f", proj.units)}$ongoingSubtitle",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Dynamic Rate Pill
                val (rateLabel, rateVal) = when (calculationMode) {
                    FvCalculationMode.EXPECTED_XIRR -> Pair("Min(Exp, Cur)", proj.effectiveRatePercent)
                    FvCalculationMode.ONGOING_FUND_XIRR -> Pair("XIRR", proj.ongoingXirr)
                    FvCalculationMode.PORTFOLIO_XIRR -> Pair("Port XIRR", proj.effectiveRatePercent)
                }
                val rateSign = if (rateVal >= 0.0) "+" else ""
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDarkTheme) Color(0xFF00363A) else Color(0xFFE0F7FA))
                        .border(1.dp, Color(0xFF00ACC1).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%s: %s%.2f%%", rateLabel, rateSign, rateVal),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFF80DEEA) else Color(0xFF00838F)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Current Holdings Section (2 columns)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL INVESTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatIndianCurrency(proj.investedAmount),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CURRENT VALUE & P&L",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatIndianCurrency(proj.currentValue),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "(%s%.2f%%)", plSign, proj.totalPlPercent),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = plColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Highlighted Future Value Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDarkTheme) Color(0xFF002715) else Color(0xFFE8F5E9)
                    )
                    .border(
                        1.dp,
                        if (isDarkTheme) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFF2E7D32).copy(alpha = 0.3f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TrendingUpIcon(
                                modifier = Modifier.size(14.dp),
                                color = if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
                            )
                            Text(
                                text = "PROJECTED FUTURE VALUE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp,
                                    color = if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
                                )
                            )
                        }

                        // Growth Multiplier Badge
                        Text(
                            text = String.format(Locale.US, "%.1fx Value", proj.wealthMultiplier),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                color = if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Prominent Future Value Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = formatIndianCurrency(proj.futureValue),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 17.5.sp,
                                color = if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF1B5E20)
                            )
                        )

                        val crFormat = formatCrOrLakh(proj.futureValue)
                        if (crFormat.isNotEmpty()) {
                            Text(
                                text = crFormat,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress / Contribution Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val rateModeTag = when (calculationMode) {
                            FvCalculationMode.EXPECTED_XIRR -> " (Exp XIRR)"
                            FvCalculationMode.ONGOING_FUND_XIRR -> " (Ongoing XIRR)"
                            FvCalculationMode.PORTFOLIO_XIRR -> " (Port XIRR)"
                        }
                        Text(
                            text = String.format(Locale.US, "Compounded at %.2f%% p.a.%s over %.1f yrs", proj.effectiveRatePercent, rateModeTag, yearsHorizon),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f%% of portfolio", proj.shareOfFuturePortfolioPercent),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                color = proj.fundColor
                            )
                        )
                    }
                }
            }
        }
    }
}

fun formatIndianCurrency(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "₹0.00"
    if (value > 1e14) {
        val cr = value / 10_000_000.0
        return String.format(Locale.US, "₹%,.2f Cr", cr)
    }
    return "₹" + formatIndianCommas(value, 2)
}

fun formatCrOrLakh(value: Double): String {
    if (value.isNaN() || value.isInfinite() || value <= 0.0) return ""
    return when {
        value >= 100_000_000_000.0 -> String.format(Locale.US, "₹%,.0f Cr", value / 10_000_000.0)
        value >= 10_000_000.0 -> String.format(Locale.US, "₹%.2f Cr", value / 10_000_000.0)
        value >= 100_000.0 -> String.format(Locale.US, "₹%.2f Lakhs", value / 100_000.0)
        else -> ""
    }
}
