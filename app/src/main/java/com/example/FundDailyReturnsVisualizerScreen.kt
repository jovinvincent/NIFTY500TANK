package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class DailyReturnDataPoint(
    val dateStr: String,
    val timeMillis: Long,
    val nav: Double,
    val absoluteReturnPct: Double,
    val xirrPct: Double,
    val investedCost: Double,
    val currentValue: Double,
    val units: Double
)

data class FundHistoricalSeries(
    val schemeName: String,
    val color: Color,
    val points: List<DailyReturnDataPoint>,
    val startNav: Double,
    val latestNav: Double,
    val latestAbsReturn: Double,
    val latestXirr: Double,
    val minAbsReturn: Double,
    val maxAbsReturn: Double,
    val minXirr: Double,
    val maxXirr: Double,
    val avgAbsReturn: Double,
    val avgXirr: Double,
    val isInvested: Boolean
)

private fun parseDateAny(dateStr: String): Date? {
    val formats = listOf("dd-MMM-yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy")
    for (f in formats) {
        try {
            val sdf = SimpleDateFormat(f, Locale.US)
            val d = sdf.parse(dateStr)
            if (d != null) return d
        } catch (e: Exception) {}
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundDailyReturnsVisualizerScreen(
    baseSchemeName: String,
    allSchemes: List<String>,
    initialTab: Int = 1, // Default = 1 (Annualized XIRR %)
    viewModel: NavViewModel,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit
) {
    // Default to XIRR (tab 1)
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    
    // Comparison Mode: HOLDING (Investor Cash Flows) vs APPLE_TO_APPLE (Date-to-Date Normalized)
    var comparisonMode by remember { mutableStateOf("HOLDING") }
    
    // Time filter presets
    var selectedTimeFilter by remember { mutableStateOf("ALL") } // 1M, 3M, 6M, 1Y, 3Y, 5Y, ALL, CUSTOM
    var customStartDateMillis by remember { mutableStateOf<Long?>(null) }
    var customEndDateMillis by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Color palette for funds
    val palette = remember {
        listOf(
            Color(0xFF00E5FF), // Vibrant Cyan (Base Fund)
            Color(0xFFFFB300), // Amber
            Color(0xFFE040FB), // Magenta
            Color(0xFF00E676), // Emerald Green
            Color(0xFFFF5252), // Coral Red
            Color(0xFFFF9100), // Vivid Orange
            Color(0xFF7C4DFF), // Deep Purple
            Color(0xFF40C4FF), // Sky Blue
            Color(0xFFFF4081), // Pink Accent
            Color(0xFFAEEA00)  // Lime Accent
        )
    }

    var selectedSchemes by remember(baseSchemeName) {
        mutableStateOf(listOf(baseSchemeName))
    }
    var showAddFundDialog by remember { mutableStateOf(false) }

    val allTxnsState = viewModel.getAllCasTransactions().collectAsStateWithLifecycle(initialValue = emptyList())
    val allTxns = allTxnsState.value ?: emptyList()

    var fundsSeries by remember { mutableStateOf<List<FundHistoricalSeries>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Scrubber state for interactive crosshair inspection
    var hoveredDateIndex by remember { mutableStateOf<Int?>(null) }
    var touchProgress by remember { mutableFloatStateOf(-1f) }

    // Fund index indicator for swiping
    val currentFundIndex = remember(baseSchemeName, allSchemes) {
        val idx = allSchemes.indexOf(baseSchemeName)
        if (idx >= 0) idx + 1 else 1
    }

    // Background Computation of Daily Return Series (with Apple-to-Apple Date-to-Date support)
    LaunchedEffect(selectedSchemes, allTxns, comparisonMode, selectedTimeFilter, customStartDateMillis, customEndDateMillis) {
        isLoading = true
        withContext(Dispatchers.Default) {
            val computedList = mutableListOf<FundHistoricalSeries>()

            // 1. Gather all raw records for all selected schemes
            val schemeRecordsMap = selectedSchemes.associateWith { scheme ->
                val records = NavCache.getRecords(scheme)
                records.mapNotNull { rec ->
                    parseDateAny(rec.dateStr)?.let { parsedDate ->
                        Triple(rec.dateStr, parsedDate, rec.nav)
                    }
                }.sortedBy { it.second.time }
            }

            // 2. Determine the active Date Range boundary based on filter
            val allTimestamps = schemeRecordsMap.values.flatten().map { it.second.time }.sorted()
            val globalEarliest = allTimestamps.firstOrNull() ?: 0L
            val globalLatest = allTimestamps.lastOrNull() ?: System.currentTimeMillis()

            val (filterStartMillis, filterEndMillis) = when (selectedTimeFilter) {
                "1M" -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = globalLatest; add(Calendar.MONTH, -1) }
                    cal.timeInMillis to globalLatest
                }
                "3M" -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = globalLatest; add(Calendar.MONTH, -3) }
                    cal.timeInMillis to globalLatest
                }
                "6M" -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = globalLatest; add(Calendar.MONTH, -6) }
                    cal.timeInMillis to globalLatest
                }
                "1Y" -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = globalLatest; add(Calendar.YEAR, -1) }
                    cal.timeInMillis to globalLatest
                }
                "3Y" -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = globalLatest; add(Calendar.YEAR, -3) }
                    cal.timeInMillis to globalLatest
                }
                "5Y" -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = globalLatest; add(Calendar.YEAR, -5) }
                    cal.timeInMillis to globalLatest
                }
                "CUSTOM" -> {
                    val s = customStartDateMillis ?: globalEarliest
                    val e = customEndDateMillis ?: globalLatest
                    s to e
                }
                else -> globalEarliest to globalLatest
            }

            // 3. Compute series per fund
            selectedSchemes.forEachIndexed { index, schemeName ->
                val color = palette.getOrElse(index) { palette[index % palette.size] }
                val dateRecords = schemeRecordsMap[schemeName] ?: emptyList()
                val txns = ReturnsCalculations.filterTxnsForScheme(allTxns, schemeName)
                val isInvested = txns.isNotEmpty()

                val points = mutableListOf<DailyReturnDataPoint>()

                if (dateRecords.isNotEmpty()) {
                    if (comparisonMode == "APPLE_TO_APPLE" || !isInvested) {
                        // Apple-to-Apple Date-to-Date Normalized Mode
                        val recordsInRange = dateRecords.filter { it.second.time in filterStartMillis..filterEndMillis }
                        val activeRecords = if (recordsInRange.isNotEmpty()) recordsInRange else {
                            dateRecords.filter { it.second.time >= filterStartMillis }.ifEmpty { dateRecords }
                        }

                        if (activeRecords.isNotEmpty()) {
                            val baseNav = activeRecords.first().third
                            val baseTime = activeRecords.first().second.time

                            for (triple in activeRecords) {
                                val dateStr = triple.first
                                val dateObj = triple.second
                                val navVal = triple.third

                                val absPct = if (baseNav > 0.0) {
                                    ((navVal - baseNav) / baseNav) * 100.0
                                } else 0.0

                                val days = max(1.0, (dateObj.time - baseTime) / (1000.0 * 60 * 60 * 24))
                                val xirrPct = if (days >= 14 && baseNav > 0.0 && navVal > 0.0) {
                                    val ratio = navVal / baseNav
                                    val cagr = (ratio.pow(365.0 / days) - 1.0) * 100.0
                                    cagr.coerceIn(-99.9, 500.0)
                                } else {
                                    absPct
                                }

                                points.add(
                                    DailyReturnDataPoint(
                                        dateStr = dateStr,
                                        timeMillis = dateObj.time,
                                        nav = navVal,
                                        absoluteReturnPct = absPct,
                                        xirrPct = xirrPct,
                                        investedCost = 0.0,
                                        currentValue = 0.0,
                                        units = 0.0
                                    )
                                )
                            }
                        }
                    } else {
                        // Holding Mode with Real CAS transactions
                        val format = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                        val sortedTxns = txns.mapNotNull { txn ->
                            try {
                                val d = format.parse(txn.date) ?: parseDateAny(txn.date)
                                if (d != null) txn to d else null
                            } catch (e: Exception) {
                                null
                            }
                        }.sortedBy { it.second.time }

                        val firstTxnTime = sortedTxns.firstOrNull()?.second?.time ?: 0L
                        val holdingNavRecords = dateRecords.filter { 
                            it.second.time >= firstTxnTime && it.second.time in filterStartMillis..filterEndMillis 
                        }
                        val activeRecords = if (holdingNavRecords.isNotEmpty()) holdingNavRecords else dateRecords

                        for (triple in activeRecords) {
                            val dateStr = triple.first
                            val dateObj = triple.second
                            val navVal = triple.third

                            val txnsUpToDate = sortedTxns.filter { it.second.time <= dateObj.time }.map { it.first }

                            if (txnsUpToDate.isNotEmpty()) {
                                val lots = ReturnsCalculations.runFifo(txnsUpToDate)
                                val units = lots.sumOf { it.units }
                                val cost = lots.sumOf { it.units * it.costPerUnit }
                                val currentVal = units * navVal

                                val absPct = if (cost > 0.0) {
                                    ((currentVal - cost) / cost) * 100.0
                                } else 0.0

                                val xirrPct = ReturnsCalculations.calculateXirr(
                                    txns = txnsUpToDate,
                                    latestNav = navVal,
                                    balanceUnits = units,
                                    totalCost = cost,
                                    valuationDate = dateObj
                                )

                                points.add(
                                    DailyReturnDataPoint(
                                        dateStr = dateStr,
                                        timeMillis = dateObj.time,
                                        nav = navVal,
                                        absoluteReturnPct = absPct,
                                        xirrPct = xirrPct,
                                        investedCost = cost,
                                        currentValue = currentVal,
                                        units = units
                                    )
                                )
                            }
                        }
                    }
                }

                if (points.isNotEmpty()) {
                    val startNavVal = points.first().nav
                    val latestPt = points.last()
                    val absVals = points.map { it.absoluteReturnPct }
                    val xirrVals = points.map { it.xirrPct }

                    computedList.add(
                        FundHistoricalSeries(
                            schemeName = schemeName,
                            color = color,
                            points = points,
                            startNav = startNavVal,
                            latestNav = latestPt.nav,
                            latestAbsReturn = latestPt.absoluteReturnPct,
                            latestXirr = latestPt.xirrPct,
                            minAbsReturn = absVals.minOrNull() ?: 0.0,
                            maxAbsReturn = absVals.maxOrNull() ?: 0.0,
                            minXirr = xirrVals.minOrNull() ?: 0.0,
                            maxXirr = xirrVals.maxOrNull() ?: 0.0,
                            avgAbsReturn = if (absVals.isNotEmpty()) absVals.average() else 0.0,
                            avgXirr = if (xirrVals.isNotEmpty()) xirrVals.average() else 0.0,
                            isInvested = isInvested
                        )
                    )
                }
            }

            fundsSeries = computedList
            isLoading = false
        }
    }

    // Unified sorted date list across all active funds
    val allDatesList = remember(fundsSeries) {
        val set = mutableSetOf<String>()
        fundsSeries.forEach { s -> set.addAll(s.points.map { it.dateStr }) }
        set.toList().sortedWith { a, b ->
            val da = parseDateAny(a)?.time ?: 0L
            val db = parseDateAny(b)?.time ?: 0L
            da.compareTo(db)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Historical Returns & XIRR",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.5.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$currentFundIndex/${allSchemes.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = baseSchemeName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("returns_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Quick Action: Add All Funds
                    if (selectedSchemes.size < allSchemes.size) {
                        IconButton(
                            onClick = { selectedSchemes = allSchemes },
                            modifier = Modifier.testTag("add_all_funds_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Add All Funds",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Quick Action: Reset / Clear Added Funds
                    if (selectedSchemes.size > 1) {
                        IconButton(
                            onClick = { selectedSchemes = listOf(baseSchemeName) },
                            modifier = Modifier.testTag("clear_added_funds_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Clear Added Funds",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Add Fund Comparison Modal
                    IconButton(
                        onClick = { showAddFundDialog = true },
                        modifier = Modifier.testTag("add_compare_fund_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Compare with other funds",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Swipe Hint Pill
            if (allSchemes.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Swipe left / right to change base fund",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        )
                    }
                }
            }

            // 1. Dual Tab Selector: Annualized XIRR (%) vs Absolute Return (%)
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            if (selectedTab in tabPositions.indices) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = {}
                    ) {
                        // Tab 0: Absolute Return
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Absolute Return (%)",
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        )

                        // Tab 1: Annualized XIRR (DEFAULT)
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Annualized XIRR (%)",
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // 2. Calculation Methodology Switch: Apple-to-Apple Date-to-Date vs Portfolio Holding
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF131822) else Color(0xFFF1F5F9)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (comparisonMode == "APPLE_TO_APPLE") "Mode: Apple-to-Apple Date-to-Date" else "Mode: Portfolio Holding Cash-Flows",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = if (comparisonMode == "APPLE_TO_APPLE") 
                                    "Starts all funds at 0% baseline on common start date for fair comparison" 
                                else "Calculates money-weighted returns from investor purchase lots",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        // Switch Button
                        OutlinedButton(
                            onClick = {
                                comparisonMode = if (comparisonMode == "APPLE_TO_APPLE") "HOLDING" else "APPLE_TO_APPLE"
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (comparisonMode == "APPLE_TO_APPLE") "Holding Mode" else "Apple-to-Apple",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // 3. Active Comparison Funds Bar (with Add All & Clear Added)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE FUNDS (${selectedSchemes.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Add All quick action
                            if (selectedSchemes.size < allSchemes.size) {
                                Text(
                                    text = "+ Add All (${allSchemes.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .clickable { selectedSchemes = allSchemes }
                                        .padding(2.dp)
                                )
                            }

                            // Clear Added quick action
                            if (selectedSchemes.size > 1) {
                                Text(
                                    text = "Clear Added",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .clickable { selectedSchemes = listOf(baseSchemeName) }
                                        .padding(2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedSchemes.forEachIndexed { index, scheme ->
                            val color = palette.getOrElse(index) { palette[index % palette.size] }
                            val isBase = scheme == baseSchemeName

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = color.copy(alpha = if (isDarkTheme) 0.22f else 0.12f),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBase) "${scheme.take(20)} (Base)" else scheme.take(22),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = if (isDarkTheme) Color.White else Color.Black
                                        ),
                                        maxLines = 1
                                    )
                                    if (!isBase) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove $scheme",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    selectedSchemes = selectedSchemes.filter { it != scheme }
                                                },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Add Fund Button Pill
                        if (selectedSchemes.size < allSchemes.size) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable { showAddFundDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Fund",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Add Fund",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Time Filter Quick Chips & Date-to-Date Range Selection
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filters = listOf("1M", "3M", "6M", "1Y", "3Y", "5Y", "ALL", "CUSTOM")
                        filters.forEach { filter ->
                            val isSelected = selectedTimeFilter == filter
                            SuggestionChip(
                                onClick = {
                                    selectedTimeFilter = filter
                                    if (filter == "CUSTOM") {
                                        showStartDatePicker = true
                                    }
                                },
                                label = {
                                    Text(
                                        text = filter,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 10.5.sp
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

                    // Date-to-Date Range Bar (Interactive Start & End Date Pickers)
                    if (allDatesList.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showStartDatePicker = true }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Select Start Date",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "From: ${allDatesList.first()}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showEndDatePicker = true }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Select End Date",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "To: ${allDatesList.last()} (${allDatesList.size} Days)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Interactive Scrubber Inspection HUD / Live Stats Banner
            item {
                val activeDateStr = if (hoveredDateIndex != null && hoveredDateIndex in allDatesList.indices) {
                    allDatesList[hoveredDateIndex!!]
                } else {
                    allDatesList.lastOrNull() ?: ""
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF141922) else Color(0xFFF1F5F9)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hoveredDateIndex != null) "INSPECTING DATE: $activeDateStr" else "LATEST VALUATION: $activeDateStr",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            if (hoveredDateIndex != null) {
                                Text(
                                    text = "Tap to release",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Per-fund Return Snapshot on Inspected Date
                        fundsSeries.forEach { series ->
                            val pt = if (hoveredDateIndex != null && hoveredDateIndex in allDatesList.indices) {
                                val d = allDatesList[hoveredDateIndex!!]
                                series.points.find { it.dateStr == d } ?: series.points.lastOrNull()
                            } else {
                                series.points.lastOrNull()
                            }

                            if (pt != null) {
                                val metricValue = if (selectedTab == 0) pt.absoluteReturnPct else pt.xirrPct
                                val metricColor = if (metricValue >= 0) {
                                    if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF00B050)
                                } else {
                                    if (isDarkTheme) Color(0xFFFF3333) else Color(0xFFE53935)
                                }
                                val prefix = if (metricValue >= 0) "+" else ""

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(series.color)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = series.schemeName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "NAV: ₹${formatIndianCommas(pt.nav, 4)}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = String.format(Locale.US, "$prefix%.2f%%", metricValue),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = metricColor,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. High-Aesthetic Interactive Graphic Canvas
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF0E131C) else Color(0xFFFFFFFF)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    } else if (allDatesList.isEmpty() || fundsSeries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No historical return records found",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    } else {
                        DailyReturnsGraphCanvas(
                            filteredDates = allDatesList,
                            fundsSeries = fundsSeries,
                            selectedTab = selectedTab,
                            isDarkTheme = isDarkTheme,
                            hoveredIndex = hoveredDateIndex,
                            touchProgress = touchProgress,
                            onHoverChange = { idx, progress ->
                                hoveredDateIndex = idx
                                touchProgress = progress
                            }
                        )
                    }
                }
            }

            // 7. Comprehensive Statistics & Analytics Card
            item {
                val primarySeries = fundsSeries.firstOrNull { it.schemeName == baseSchemeName } ?: fundsSeries.firstOrNull()
                if (primarySeries != null) {
                    val isAbs = selectedTab == 0
                    val minVal = if (isAbs) primarySeries.minAbsReturn else primarySeries.minXirr
                    val maxVal = if (isAbs) primarySeries.maxAbsReturn else primarySeries.maxXirr
                    val avgVal = if (isAbs) primarySeries.avgAbsReturn else primarySeries.avgXirr
                    val latestVal = if (isAbs) primarySeries.latestAbsReturn else primarySeries.latestXirr

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAbs) "ABSOLUTE RETURN INSIGHTS" else "XIRR PERFORMANCE INSIGHTS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = "${primarySeries.points.size} Daily Points",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatMetricBox(
                                    title = "LATEST RETURN",
                                    value = String.format(Locale.US, "%s%.2f%%", if (latestVal >= 0) "+" else "", latestVal),
                                    isPositive = latestVal >= 0,
                                    isDark = isDarkTheme,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                StatMetricBox(
                                    title = "PEAK (MAX)",
                                    value = String.format(Locale.US, "%s%.2f%%", if (maxVal >= 0) "+" else "", maxVal),
                                    isPositive = maxVal >= 0,
                                    isDark = isDarkTheme,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                StatMetricBox(
                                    title = "LOWEST (MIN)",
                                    value = String.format(Locale.US, "%s%.2f%%", if (minVal >= 0) "+" else "", minVal),
                                    isPositive = minVal >= 0,
                                    isDark = isDarkTheme,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                StatMetricBox(
                                    title = "AVERAGE",
                                    value = String.format(Locale.US, "%s%.2f%%", if (avgVal >= 0) "+" else "", avgVal),
                                    isPositive = avgVal >= 0,
                                    isDark = isDarkTheme,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 8. Daily Historical Ledger Table (Expandable)
            item {
                val primarySeries = fundsSeries.firstOrNull { it.schemeName == baseSchemeName } ?: fundsSeries.firstOrNull()
                if (primarySeries != null && primarySeries.points.isNotEmpty()) {
                    var isTableExpanded by remember { mutableStateOf(false) }
                    val displayList = if (isTableExpanded) primarySeries.points.reversed() else primarySeries.points.reversed().take(8)

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DAILY HISTORICAL LEDGER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = if (isTableExpanded) "Show Less" else "View All (${primarySeries.points.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .clickable { isTableExpanded = !isTableExpanded }
                                        .padding(4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Date", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1.2f))
                                Text("NAV (₹)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1f))
                                Text("Abs Ret %", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1.1f))
                                Text("XIRR %", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1.1f))
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            displayList.forEachIndexed { index, pt ->
                                val absColor = if (pt.absoluteReturnPct >= 0) {
                                    if (isDarkTheme) Color(0xFF00FF66) else Color(0xFF00B050)
                                } else {
                                    if (isDarkTheme) Color(0xFFFF3333) else Color(0xFFE53935)
                                }
                                val xirrColor = if (pt.xirrPct >= 0) {
                                    if (isDarkTheme) Color(0xFF80DEEA) else Color(0xFF00838F)
                                } else {
                                    if (isDarkTheme) Color(0xFFFF8A80) else Color(0xFFC62828)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pt.dateStr,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.weight(1.2f)
                                    )
                                    Text(
                                        text = formatIndianCommas(pt.nav, 4),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.End
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%s%.2f%%", if (pt.absoluteReturnPct >= 0) "+" else "", pt.absoluteReturnPct),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.End,
                                            color = absColor
                                        ),
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%s%.2f%%", if (pt.xirrPct >= 0) "+" else "", pt.xirrPct),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.End,
                                            color = xirrColor
                                        ),
                                        modifier = Modifier.weight(1.1f)
                                    )
                                }

                                if (index < displayList.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Add Comparison Fund Modal Dialog
    if (showAddFundDialog) {
        val availableFunds = allSchemes.filter { it !in selectedSchemes }

        Dialog(onDismissRequest = { showAddFundDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Fund for Comparison",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { showAddFundDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Bulk Actions Row inside dialog
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (availableFunds.isNotEmpty()) {
                            Button(
                                onClick = {
                                    selectedSchemes = allSchemes
                                    showAddFundDialog = false
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add All (${availableFunds.size})", fontSize = 11.5.sp)
                            }
                        }

                        if (selectedSchemes.size > 1) {
                            OutlinedButton(
                                onClick = {
                                    selectedSchemes = listOf(baseSchemeName)
                                    showAddFundDialog = false
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Reset to Base", fontSize = 11.5.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (availableFunds.isEmpty()) {
                        Text(
                            text = "All portfolio funds are already added.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                count = availableFunds.size,
                                key = { idx -> availableFunds[idx] }
                            ) { idx ->
                                val scheme = availableFunds[idx]
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSchemes = selectedSchemes + scheme
                                            showAddFundDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = scheme,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Material 3 Date Picker Dialogs for Custom Date-to-Date Comparison
    if (showStartDatePicker) {
        val initialDate = customStartDateMillis ?: (allDatesList.firstOrNull()?.let { parseDateAny(it)?.time } ?: System.currentTimeMillis())
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)

        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val sel = datePickerState.selectedDateMillis
                    if (sel != null) {
                        customStartDateMillis = sel
                        selectedTimeFilter = "CUSTOM"
                    }
                    showStartDatePicker = false
                }) {
                    Text("Select Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val initialDate = customEndDateMillis ?: (allDatesList.lastOrNull()?.let { parseDateAny(it)?.time } ?: System.currentTimeMillis())
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)

        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val sel = datePickerState.selectedDateMillis
                    if (sel != null) {
                        customEndDateMillis = sel
                        selectedTimeFilter = "CUSTOM"
                    }
                    showEndDatePicker = false
                }) {
                    Text("Select End")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun StatMetricBox(
    title: String,
    value: String,
    isPositive: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val color = if (isPositive) {
                if (isDark) Color(0xFF00FF66) else Color(0xFF00B050)
            } else {
                if (isDark) Color(0xFFFF3333) else Color(0xFFE53935)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    fontFamily = FontFamily.SansSerif
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
fun DailyReturnsGraphCanvas(
    filteredDates: List<String>,
    fundsSeries: List<FundHistoricalSeries>,
    selectedTab: Int, // 0 = Abs %, 1 = XIRR %
    isDarkTheme: Boolean,
    hoveredIndex: Int?,
    touchProgress: Float,
    onHoverChange: (Int?, Float) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val isAbs = selectedTab == 0

    // Compute min and max value bounds across all funds for aligned Y-axis
    val (minVal, maxVal) = remember(fundsSeries, filteredDates, selectedTab) {
        var minV = Double.MAX_VALUE
        var maxV = -Double.MAX_VALUE

        val dateSet = filteredDates.toSet()

        fundsSeries.forEach { series ->
            series.points.filter { it.dateStr in dateSet }.forEach { pt ->
                val v = if (isAbs) pt.absoluteReturnPct else pt.xirrPct
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
        }

        if (minV == Double.MAX_VALUE || maxV == -Double.MAX_VALUE) {
            Pair(-10.0, 50.0)
        } else {
            // Add aesthetic padding on top and bottom
            val range = max(5.0, maxV - minV)
            val padMin = min(0.0, minV - (range * 0.1))
            val padMax = maxV + (range * 0.12)
            Pair(padMin, padMax)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(filteredDates) {
                detectTapGestures(
                    onPress = { offset ->
                        val paddingLeft = 45.dp.toPx()
                        val paddingRight = 15.dp.toPx()
                        val chartW = size.width - paddingLeft - paddingRight
                        if (chartW > 0 && offset.x in paddingLeft..(size.width - paddingRight)) {
                            val ratio = ((offset.x - paddingLeft) / chartW).coerceIn(0f, 1f)
                            val idx = (ratio * (filteredDates.size - 1)).toInt().coerceIn(0, filteredDates.lastIndex)
                            onHoverChange(idx, ratio)
                        } else {
                            onHoverChange(null, -1f)
                        }
                    },
                    onTap = {
                        // Release or toggle inspection
                    }
                )
            }
            .pointerInput(filteredDates) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val paddingLeft = 45.dp.toPx()
                        val paddingRight = 15.dp.toPx()
                        val chartW = size.width - paddingLeft - paddingRight
                        if (chartW > 0) {
                            val ratio = ((offset.x - paddingLeft) / chartW).coerceIn(0f, 1f)
                            val idx = (ratio * (filteredDates.size - 1)).toInt().coerceIn(0, filteredDates.lastIndex)
                            onHoverChange(idx, ratio)
                        }
                    },
                    onDragEnd = {},
                    onDragCancel = {},
                    onHorizontalDrag = { change, _ ->
                        val paddingLeft = 45.dp.toPx()
                        val paddingRight = 15.dp.toPx()
                        val chartW = size.width - paddingLeft - paddingRight
                        if (chartW > 0) {
                            val ratio = ((change.position.x - paddingLeft) / chartW).coerceIn(0f, 1f)
                            val idx = (ratio * (filteredDates.size - 1)).toInt().coerceIn(0, filteredDates.lastIndex)
                            onHoverChange(idx, ratio)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingLeft = 48.dp.toPx()
            val paddingRight = 16.dp.toPx()
            val paddingTop = 22.dp.toPx()
            val paddingBottom = 30.dp.toPx()

            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            if (chartWidth <= 0 || chartHeight <= 0 || filteredDates.isEmpty()) return@Canvas

            val valRange = if (maxVal - minVal > 1e-4) maxVal - minVal else 1.0

            // Helper to compute Canvas coordinate Y from return percentage
            fun getY(value: Double): Float {
                val ratio = (value - minVal) / valRange
                return (paddingTop + chartHeight * (1.0 - ratio)).toFloat().coerceIn(paddingTop, paddingTop + chartHeight)
            }

            // Helper to compute Canvas coordinate X from date index
            fun getX(index: Int): Float {
                if (filteredDates.size <= 1) return paddingLeft
                val ratio = index.toFloat() / (filteredDates.size - 1)
                return paddingLeft + chartWidth * ratio
            }

            // 1. Draw Horizontal Gridlines & Y-Axis Labels (5 Steps)
            val gridSteps = 5
            val labelStyle = TextStyle(
                color = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF616161),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )

            for (i in 0..gridSteps) {
                val fraction = i.toFloat() / gridSteps
                val gridVal = minVal + fraction * valRange
                val yPos = (paddingTop + chartHeight * (1f - fraction))

                // Gridline
                drawLine(
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.07f),
                    start = Offset(paddingLeft, yPos),
                    end = Offset(size.width - paddingRight, yPos),
                    strokeWidth = 1.dp.toPx()
                )

                // Label
                val labelText = String.format(Locale.US, "%s%.0f%%", if (gridVal >= 0) "+" else "", gridVal)
                val textLayout = textMeasurer.measure(labelText, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = labelText,
                    style = labelStyle,
                    topLeft = Offset(paddingLeft - textLayout.size.width - 6.dp.toPx(), yPos - (textLayout.size.height / 2))
                )
            }

            // 2. Prominent Zero Line (0.00%)
            if (minVal <= 0.0 && maxVal >= 0.0) {
                val zeroY = getY(0.0)
                drawLine(
                    color = if (isDarkTheme) Color(0xFF80CBC4).copy(alpha = 0.4f) else Color(0xFF00897B).copy(alpha = 0.4f),
                    start = Offset(paddingLeft, zeroY),
                    end = Offset(size.width - paddingRight, zeroY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // 3. Draw Curves for Each Active Fund
            val dateMap = filteredDates.mapIndexed { idx, d -> d to idx }.toMap()

            fundsSeries.forEachIndexed { fundIdx, series ->
                val pointsInView = series.points.mapNotNull { pt ->
                    dateMap[pt.dateStr]?.let { idx ->
                        idx to (if (isAbs) pt.absoluteReturnPct else pt.xirrPct)
                    }
                }.sortedBy { it.first }

                if (pointsInView.size >= 2) {
                    val linePath = Path()
                    val areaPath = Path()

                    val firstPt = pointsInView.first()
                    val startX = getX(firstPt.first)
                    val startY = getY(firstPt.second)

                    linePath.moveTo(startX, startY)
                    areaPath.moveTo(startX, paddingTop + chartHeight)
                    areaPath.lineTo(startX, startY)

                    for (k in 1 until pointsInView.size) {
                        val prev = pointsInView[k - 1]
                        val curr = pointsInView[k]

                        val x0 = getX(prev.first)
                        val y0 = getY(prev.second)
                        val x1 = getX(curr.first)
                        val y1 = getY(curr.second)

                        // Smooth cubic bezier curve
                        val cx = (x0 + x1) / 2f
                        linePath.cubicTo(cx, y0, cx, y1, x1, y1)
                        areaPath.cubicTo(cx, y0, cx, y1, x1, y1)
                    }

                    val lastPt = pointsInView.last()
                    val endX = getX(lastPt.first)
                    areaPath.lineTo(endX, paddingTop + chartHeight)
                    areaPath.close()

                    // Only draw soft gradient area fill for the primary (first) base fund
                    if (fundIdx == 0) {
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    series.color.copy(alpha = if (isDarkTheme) 0.35f else 0.22f),
                                    series.color.copy(alpha = 0.0f)
                                ),
                                startY = paddingTop,
                                endY = paddingTop + chartHeight
                            )
                        )
                    }

                    // Stroke line
                    drawPath(
                        path = linePath,
                        color = series.color,
                        style = Stroke(
                            width = if (fundIdx == 0) 3.dp.toPx() else 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw end dot
                    val lastX = getX(lastPt.first)
                    val lastY = getY(lastPt.second)
                    drawCircle(
                        color = series.color,
                        radius = 4.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = if (isDarkTheme) Color.Black else Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }

            // 4. Draw X-Axis Date Labels (4-5 labels along bottom)
            val dateSteps = minOf(4, filteredDates.size - 1)
            if (dateSteps > 0) {
                for (s in 0..dateSteps) {
                    val idx = (s.toFloat() / dateSteps * (filteredDates.size - 1)).toInt().coerceIn(0, filteredDates.lastIndex)
                    val xPos = getX(idx)
                    val dStr = filteredDates[idx]

                    val label = try {
                        val parsed = parseDateAny(dStr)
                        if (parsed != null) SimpleDateFormat("dd-MMM", Locale.US).format(parsed) else dStr
                    } catch (e: Exception) {
                        dStr
                    }

                    val textLayout = textMeasurer.measure(label, labelStyle)
                    val leftAlignX = (xPos - (textLayout.size.width / 2)).coerceIn(
                        paddingLeft,
                        size.width - paddingRight - textLayout.size.width
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        style = labelStyle,
                        topLeft = Offset(leftAlignX, size.height - paddingBottom + 8.dp.toPx())
                    )
                }
            }

            // 5. Crosshair Scrubber Line & Highlight Dots
            if (hoveredIndex != null && hoveredIndex in filteredDates.indices) {
                val hoverX = getX(hoveredIndex)
                val hoverDateStr = filteredDates[hoveredIndex]

                // Vertical Crosshair Line
                drawLine(
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
                    start = Offset(hoverX, paddingTop),
                    end = Offset(hoverX, paddingTop + chartHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )

                // Highlight Dots for each fund on inspected date
                fundsSeries.forEach { series ->
                    val pt = series.points.find { it.dateStr == hoverDateStr }
                    if (pt != null) {
                        val v = if (isAbs) pt.absoluteReturnPct else pt.xirrPct
                        val ptY = getY(v)

                        drawCircle(
                            color = series.color.copy(alpha = 0.4f),
                            radius = 7.dp.toPx(),
                            center = Offset(hoverX, ptY)
                        )
                        drawCircle(
                            color = series.color,
                            radius = 4.5.dp.toPx(),
                            center = Offset(hoverX, ptY)
                        )
                        drawCircle(
                            color = if (isDarkTheme) Color.Black else Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(hoverX, ptY)
                        )
                    }
                }
            }
        }
    }
}
