package com.example

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

/**
 * Data model representing a Daily SIP simulated from a specific start date till latest date.
 */
data class DailySipInceptionPoint(
    val startDateStr: String,
    val startTimestamp: Long,
    val installments: Int,
    val totalInvested: Double,
    val currentValue: Double,
    val absoluteReturnPct: Double,
    val entryNavReturnPct: Double, // Point-to-point NAV growth: (lastNav - entryNav) / entryNav * 100
    val xirrPct: Double,
    val entryNav: Double,
    val sma20: Double?,
    val sma50: Double?,
    val sma20DiffPct: Double?, // positive = above SMA20, negative = dip below SMA20
    val isDipEntry: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySipInceptionAnalysisScreen(
    baseSchemeName: String,
    allSchemes: List<String> = emptyList(),
    isDarkTheme: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var rawRecords by remember(baseSchemeName) { mutableStateOf(NavCache.getRecords(baseSchemeName)) }
    var isLoading by remember(baseSchemeName) { mutableStateOf(rawRecords.isEmpty()) }

    // Load records from Room if cache was cold
    LaunchedEffect(baseSchemeName) {
        if (rawRecords.isEmpty()) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val dbRecords = db.fundDao().getHistoricalNavs(baseSchemeName)
                if (dbRecords.isNotEmpty()) {
                    val navList = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                    NavCache.addAll(baseSchemeName, navList)
                    rawRecords = navList
                }
            }
            isLoading = false
        }
    }

    // Parse chronologically (oldest to newest)
    val parsedRecords = remember(rawRecords) {
        val formats = listOf(
            SimpleDateFormat("dd-MMM-yyyy", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US)
        )
        rawRecords.mapNotNull { rec ->
            val time = formats.firstNotNullOfOrNull { fmt ->
                try { fmt.parse(rec.dateStr)?.time } catch (e: Exception) { null }
            }
            if (time != null) Pair(rec, time) else null
        }.distinctBy { it.second }.sortedBy { it.second }
    }

    // Compute simulation points
    var simulationPoints by remember { mutableStateOf<List<DailySipInceptionPoint>>(emptyList()) }
    var inverseSipPoints by remember { mutableStateOf<List<InverseSipPoint>>(emptyList()) }
    var isCalculating by remember { mutableStateOf(false) }

    val inceptionTime = remember(parsedRecords) { parsedRecords.firstOrNull()?.second ?: 0L }
    var inverseSipStartTime by remember(inceptionTime) { mutableLongStateOf(inceptionTime) }

    LaunchedEffect(parsedRecords, inverseSipStartTime) {
        if (parsedRecords.size < 5) {
            simulationPoints = emptyList()
            inverseSipPoints = emptyList()
            return@LaunchedEffect
        }
        isCalculating = true
        withContext(Dispatchers.Default) {
            val dailySip = computeDailySipInceptionSeries(parsedRecords)
            val inverseSip = computeInverseSipSeries(parsedRecords, inverseSipStartTime)
            simulationPoints = dailySip
            inverseSipPoints = inverseSip
        }
        isCalculating = false
    }

    // UI state
    var selectedTab by remember { mutableIntStateOf(0) } // 0: XIRR (%), 1: Absolute Return (%), 2: Inverse SIP XIRR, 3: Consistency & Range
    var showDatePicker by remember { mutableStateOf(false) }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = inverseSipStartTime
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        inverseSipStartTime = it
                    }
                    showDatePicker = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    val sharedPreferences = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var rawBenchmarkXirr by remember {
        mutableStateOf(sharedPreferences.getString("benchmark_xirr", "12") ?: "12")
    }
    val benchmarkRate by remember(rawBenchmarkXirr) {
        derivedStateOf { rawBenchmarkXirr.toDoubleOrNull() ?: 12.0 }
    }

    LaunchedEffect(Unit) {
        val saved = sharedPreferences.getString("benchmark_xirr", "12") ?: "12"
        if (saved != rawBenchmarkXirr) {
            rawBenchmarkXirr = saved
        }
    }

    var selectedTimeFilter by remember { mutableStateOf("ALL") } // 1M, 3M, 6M, ALL
    var tableSearchQuery by remember { mutableStateOf("") }
    var tableFilterDipOnly by remember { mutableStateOf(false) }
    var tableSortOrder by remember { mutableStateOf("DATE_DESC") } // DATE_DESC, DATE_ASC, XIRR_DESC, ABS_DESC
    var isTableExpanded by remember { mutableStateOf(false) }

    val cardBg = if (isDarkTheme) Color(0xFF15191C) else Color(0xFFFFFFFF)
    val screenBg = if (isDarkTheme) Color(0xFF0D1117) else Color(0xFFF8FAFC)
    val borderColor = if (isDarkTheme) Color(0xFF2C3238) else Color(0xFFE2E8F0)
    val emeraldColor = Color(0xFF00E676)
    val cyanColor = Color(0xFF00E5FF)
    val amberColor = Color(0xFFFFB300)
    val redColor = Color(0xFFFF5252)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = baseSchemeName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Daily SIP Inception Simulator",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenBg,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = screenBg
    ) { paddingValues ->
        if (isLoading || isCalculating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Simulating Inception-to-Date Daily SIPs...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        if (simulationPoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Insufficient historical NAV data to simulate daily SIPs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        // Filter points for chart based on selectedTimeFilter
        val activeEligiblePoints = remember(simulationPoints, inverseSipPoints, selectedTab) {
            if (selectedTab == 2) {
                val startNav = inverseSipPoints.firstOrNull()?.endNav ?: 1.0
                inverseSipPoints.map { pt ->
                    val navGrowth = if (startNav > 0.0) ((pt.endNav - startNav) / startNav) * 100.0 else 0.0
                    DailySipInceptionPoint(
                        startDateStr = pt.endDateStr,
                        startTimestamp = pt.endTimestamp,
                        installments = pt.installments,
                        totalInvested = pt.totalInvested,
                        currentValue = pt.currentValue,
                        absoluteReturnPct = pt.absoluteReturnPct,
                        entryNavReturnPct = navGrowth,
                        xirrPct = pt.xirrPct,
                        entryNav = pt.endNav,
                        sma20 = null,
                        sma50 = null,
                        sma20DiffPct = null,
                        isDipEntry = false
                    )
                }
            } else {
                if (simulationPoints.size > 2) simulationPoints.filter { it.installments > 1 } else simulationPoints
            }
        }
        
        val nowMillis = remember(activeEligiblePoints) { activeEligiblePoints.lastOrNull()?.startTimestamp ?: System.currentTimeMillis() }
        val filteredPoints = remember(activeEligiblePoints, selectedTimeFilter) {
            when (selectedTimeFilter) {
                "1M" -> {
                    val cutoff = nowMillis - (30L * 24 * 60 * 60 * 1000L)
                    activeEligiblePoints.filter { it.startTimestamp >= cutoff }
                }
                "3M" -> {
                    val cutoff = nowMillis - (90L * 24 * 60 * 60 * 1000L)
                    activeEligiblePoints.filter { it.startTimestamp >= cutoff }
                }
                "6M" -> {
                    val cutoff = nowMillis - (180L * 24 * 60 * 60 * 1000L)
                    activeEligiblePoints.filter { it.startTimestamp >= cutoff }
                }
                else -> activeEligiblePoints
            }.ifEmpty { activeEligiblePoints }
        }

        // Filtered & Sorted Table Items
        val tableItems = remember(activeEligiblePoints, tableSearchQuery, tableFilterDipOnly, tableSortOrder, selectedTab) {
            var list = activeEligiblePoints
            if (tableFilterDipOnly && selectedTab != 2) {
                list = list.filter { it.isDipEntry }
            }
            if (tableSearchQuery.isNotBlank()) {
                val q = tableSearchQuery.trim().lowercase()
                list = list.filter { it.startDateStr.lowercase().contains(q) }
            }
            when (tableSortOrder) {
                "DATE_ASC" -> list.sortedBy { it.startTimestamp }
                "XIRR_DESC" -> list.sortedByDescending { it.xirrPct }
                "ABS_DESC" -> list.sortedByDescending { it.absoluteReturnPct }
                else -> list.sortedByDescending { it.startTimestamp } // DATE_DESC
            }
        }

        // Summary Statistics
        val bestXirrPoint = remember(simulationPoints) { 
            simulationPoints.filter { it.installments > 1 && it.xirrPct <= 250.0 }.maxByOrNull { it.xirrPct } 
                ?: simulationPoints.filter { it.installments > 1 }.maxByOrNull { it.xirrPct }
                ?: simulationPoints.maxByOrNull { it.xirrPct }
        }
        val bestAbsPoint = remember(simulationPoints) { simulationPoints.maxByOrNull { it.absoluteReturnPct } }
        
        // Inverse SIP Statistics
        val bestInverseXirrPoint = remember(inverseSipPoints) {
            inverseSipPoints.filter { it.installments > 1 && it.xirrPct <= 250.0 }.maxByOrNull { it.xirrPct }
                ?: inverseSipPoints.filter { it.installments > 1 }.maxByOrNull { it.xirrPct }
                ?: inverseSipPoints.maxByOrNull { it.xirrPct }
        }
        val latestInversePoint = inverseSipPoints.lastOrNull()

        // Average Statistics
        val avgXirr = remember(simulationPoints) {
            val eligiblePoints = simulationPoints.filter { it.installments > 1 }
            val normalPoints = eligiblePoints.filter { it.xirrPct <= 250.0 }
            if (normalPoints.isNotEmpty()) {
                normalPoints.map { it.xirrPct }.average()
            } else if (eligiblePoints.isNotEmpty()) {
                eligiblePoints.map { minOf(it.xirrPct, 250.0) }.average()
            } else {
                0.0
            }
        }
        val avgAbs = remember(simulationPoints) { 
            val eligible = simulationPoints.filter { it.installments > 1 }
            if (eligible.isNotEmpty()) eligible.map { it.absoluteReturnPct }.average() else 0.0 
        }
        val avgInverseXirr = remember(inverseSipPoints) {
            val eligible = inverseSipPoints.filter { it.installments > 1 }
            val normal = eligible.filter { it.xirrPct <= 150.0 }
            if (normal.isNotEmpty()) normal.map { it.xirrPct }.average()
            else if (eligible.isNotEmpty()) eligible.map { minOf(it.xirrPct, 150.0) }.average()
            else 0.0
        }
        
        val firstInceptionPoint = simulationPoints.firstOrNull()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Concept Explainer Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    val title = if (selectedTab == 2) "Inverse SIP Analysis" else "Daily SIP Inception Behavior"
                                    val subtitle = if (selectedTab == 2) {
                                        "Started on: ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(inverseSipStartTime))}"
                                    } else {
                                        "Returns as if a daily SIP started on each day till today"
                                    }
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (selectedTab == 2) {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Change Start Date",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Primary Tabs
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = cardBg,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 8.dp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Annualised XIRR", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("SIP Absolute", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Inverse SIP XIRR", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (selectedTab == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Consistency & Range", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    )
                }
            }

            if (selectedTab in 0..2) {

            // 3. Key Stat Metric Cards
            item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Peak/Best Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = when (selectedTab) {
                                0 -> "Peak XIRR Timing"
                                1 -> "Peak SIP Return"
                                else -> "Peak Inverse XIRR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        val peakVal = when (selectedTab) {
                            0 -> bestXirrPoint?.xirrPct
                            1 -> bestAbsPoint?.absoluteReturnPct
                            else -> bestInverseXirrPoint?.xirrPct
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f%%", peakVal ?: 0.0),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = emeraldColor
                        )
                        Spacer(Modifier.height(2.dp))
                        val peakDate = when (selectedTab) {
                            0 -> bestXirrPoint?.startDateStr
                            1 -> bestAbsPoint?.startDateStr
                            else -> bestInverseXirrPoint?.endDateStr
                        }
                        Text(
                            text = if (selectedTab == 2) "Peak: ${peakDate ?: "-"}" else "Started: ${peakDate ?: "-"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Average Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = when (selectedTab) {
                                0 -> "Average XIRR"
                                1 -> "Avg SIP Return"
                                else -> "Avg Inverse XIRR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        val avgVal = when (selectedTab) {
                            0 -> avgXirr
                            1 -> avgAbs
                            else -> avgInverseXirr
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f%%", avgVal),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = cyanColor
                        )
                        Spacer(Modifier.height(2.dp))
                        val totalPoints = if (selectedTab == 2) inverseSipPoints.size else simulationPoints.size
                        Text(
                            text = "$totalPoints Points",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Day-1 / Latest Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (selectedTab == 2) "Latest XIRR" else "Since Inception",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        val lastVal = when (selectedTab) {
                            0 -> firstInceptionPoint?.xirrPct
                            1 -> firstInceptionPoint?.absoluteReturnPct
                            else -> latestInversePoint?.xirrPct
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f%%", lastVal ?: 0.0),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = amberColor
                        )
                        Spacer(Modifier.height(2.dp))
                        val installs = if (selectedTab == 2) latestInversePoint?.installments else firstInceptionPoint?.installments
                        Text(
                            text = "${installs ?: 0} Installs",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

            // 4. Interactive Dual-Panel Chart with Moving Average Overlay
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> "Daily SIP XIRR Trajectory"
                                        1 -> "Daily SIP Absolute Return Trajectory"
                                        2 -> "Inverse SIP Progressive XIRR Trajectory"
                                        else -> "Entry NAV Point-to-Point Growth"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (selectedTab == 2) {
                                        "Touch & drag across timeline to inspect progressive XIRR as installments accumulate"
                                    } else {
                                        "Touch & drag across timeline to inspect any starting date"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Time filter chips
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("1M", "3M", "ALL").forEach { tf ->
                                    val isSelected = selectedTimeFilter == tf
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { selectedTimeFilter = tf }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = tf,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Interactive canvas chart
                        DailySipInteractiveChart(
                            points = filteredPoints,
                            isDark = isDarkTheme,
                            selectedTab = selectedTab
                        )
                    }
                }
            }

            // 5. Moving Average & Quantitative Factor Analytics (Relevant for Daily SIP Inception Entry Timing)
            if (selectedTab in 0..1) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Moving Average Timing Impact (20-DMA & 50-DMA)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Performance comparison for Daily SIPs initiated during short-term (20-DMA) and medium-term (50-DMA) market pullbacks:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        // 20-DMA Analysis
                        val dip20Points = remember(simulationPoints) { simulationPoints.filter { it.sma20 != null && it.entryNav < it.sma20 } }
                        val nonDip20Points = remember(simulationPoints) { simulationPoints.filter { it.sma20 != null && it.entryNav >= it.sma20 } }
                        val avgDip20Val = remember(dip20Points, selectedTab) {
                            if (dip20Points.isNotEmpty()) {
                                val eligible = dip20Points.filter { it.installments > 1 }
                                if (selectedTab == 0) {
                                    val normal = eligible.filter { it.xirrPct <= 250.0 }
                                    if (normal.isNotEmpty()) normal.map { it.xirrPct }.average()
                                    else if (eligible.isNotEmpty()) eligible.map { minOf(it.xirrPct, 250.0) }.average()
                                    else 0.0
                                } else {
                                    if (eligible.isNotEmpty()) eligible.map { it.absoluteReturnPct }.average() else 0.0
                                }
                            } else 0.0
                        }
                        val avgPeak20Val = remember(nonDip20Points, selectedTab) {
                            if (nonDip20Points.isNotEmpty()) {
                                val eligible = nonDip20Points.filter { it.installments > 1 }
                                if (selectedTab == 0) {
                                    val normal = eligible.filter { it.xirrPct <= 250.0 }
                                    if (normal.isNotEmpty()) normal.map { it.xirrPct }.average()
                                    else if (eligible.isNotEmpty()) eligible.map { minOf(it.xirrPct, 250.0) }.average()
                                    else 0.0
                                } else {
                                    if (eligible.isNotEmpty()) eligible.map { it.absoluteReturnPct }.average() else 0.0
                                }
                            } else 0.0
                        }

                        // 50-DMA Analysis
                        val dip50Points = remember(simulationPoints) { simulationPoints.filter { it.sma50 != null && it.entryNav < it.sma50 } }
                        val nonDip50Points = remember(simulationPoints) { simulationPoints.filter { it.sma50 != null && it.entryNav >= it.sma50 } }
                        val avgDip50Val = remember(dip50Points, selectedTab) {
                            if (dip50Points.isNotEmpty()) {
                                val eligible = dip50Points.filter { it.installments > 1 }
                                if (selectedTab == 0) {
                                    val normal = eligible.filter { it.xirrPct <= 250.0 }
                                    if (normal.isNotEmpty()) normal.map { it.xirrPct }.average()
                                    else if (eligible.isNotEmpty()) eligible.map { minOf(it.xirrPct, 250.0) }.average()
                                    else 0.0
                                } else {
                                    if (eligible.isNotEmpty()) eligible.map { it.absoluteReturnPct }.average() else 0.0
                                }
                            } else 0.0
                        }
                        val avgPeak50Val = remember(nonDip50Points, selectedTab) {
                            if (nonDip50Points.isNotEmpty()) {
                                val eligible = nonDip50Points.filter { it.installments > 1 }
                                if (selectedTab == 0) {
                                    val normal = eligible.filter { it.xirrPct <= 250.0 }
                                    if (normal.isNotEmpty()) normal.map { it.xirrPct }.average()
                                    else if (eligible.isNotEmpty()) eligible.map { minOf(it.xirrPct, 250.0) }.average()
                                    else 0.0
                                } else {
                                    if (eligible.isNotEmpty()) eligible.map { it.absoluteReturnPct }.average() else 0.0
                                }
                            } else 0.0
                        }

                        val unitLabel = if (selectedTab == 0) "XIRR" else "Return"

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 20-DMA Card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkTheme) Color(0xFF0E231B) else Color(0xFFE8F5E9)
                                ),
                                border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "20-DMA Dip Entries",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = emeraldColor
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.2f%% %s", avgDip20Val, unitLabel),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = emeraldColor
                                    )
                                    Text(
                                        text = "${dip20Points.size} Dips vs ${nonDip20Points.size} Rallies",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 50-DMA Card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkTheme) Color(0xFF13222E) else Color(0xFFE0F7FA)
                                ),
                                border = BorderStroke(1.dp, cyanColor.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "50-DMA Dip Entries",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = cyanColor
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.2f%% %s", avgDip50Val, unitLabel),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = cyanColor
                                    )
                                    Text(
                                        text = "${dip50Points.size} Dips vs ${nonDip50Points.size} Rallies",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        val alpha20 = avgDip20Val - avgPeak20Val
                        val alpha50 = avgDip50Val - avgPeak50Val
                        val bestAlpha = if (alpha50 > alpha20) alpha50 else alpha20
                        val bestDmaLabel = if (alpha50 > alpha20) "50-DMA" else "20-DMA"

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (bestAlpha > 0) emeraldColor.copy(alpha = 0.1f) else amberColor.copy(alpha = 0.1f)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (bestAlpha > 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = if (bestAlpha > 0) emeraldColor else amberColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (bestAlpha > 0) {
                                    String.format(Locale.US, "Dip Premium: +%.2f%% extra %s gained by starting daily SIP during %s pullbacks.", bestAlpha, unitLabel, bestDmaLabel)
                                } else {
                                    "Secular Momentum: Continuous SIP compounding outweighed short-term moving average fluctuations."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (bestAlpha > 0) emeraldColor else amberColor
                            )
                        }
                    }
                }
            }
            }

            // 6. Detailed Date-by-Date Data Table Header & Controls
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedTab == 2) "Inverse SIP Schedule (${tableItems.size} dates)" else "Daily Inception Schedule (${tableItems.size} dates)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // Sort toggle button
                        TextButton(
                            onClick = {
                                tableSortOrder = when (tableSortOrder) {
                                    "DATE_DESC" -> "XIRR_DESC"
                                    "XIRR_DESC" -> "ABS_DESC"
                                    "ABS_DESC" -> "DATE_ASC"
                                    else -> "DATE_DESC"
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val sortLabel = when (tableSortOrder) {
                                "DATE_DESC" -> "Newest First"
                                "DATE_ASC" -> "Oldest First"
                                "XIRR_DESC" -> "Highest XIRR"
                                "ABS_DESC" -> "Highest Return"
                                else -> "Sort"
                            }
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(sortLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Search and Filter Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tableSearchQuery,
                            onValueChange = { tableSearchQuery = it },
                            placeholder = { Text("Search date...", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = cardBg,
                                unfocusedContainerColor = cardBg,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = borderColor
                            )
                        )

                        // Dip Filter Chip (only relevant for Inception mode)
                        if (selectedTab != 2) {
                            FilterChip(
                                selected = tableFilterDipOnly,
                                onClick = { tableFilterDipOnly = !tableFilterDipOnly },
                                label = { Text("Dips Only", fontSize = 12.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // 7. Table Column Headers
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E2429) else Color(0xFFEEF2F6)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedTab == 2) "Valuation Date" else "Start Date",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = if (selectedTab == 2) "NAV Value" else "Entry NAV / SMA",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.3f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Absolute",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "XIRR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.1f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // 8. Table Items (Preview 8 items or full list)
            val displayedTableItems = if (isTableExpanded) tableItems else tableItems.take(8)
            itemsIndexed(displayedTableItems, key = { _, item -> item.startTimestamp }) { _, item ->
                DailySipRowCard(
                    point = item,
                    isDark = isDarkTheme,
                    borderColor = borderColor,
                    cardBg = cardBg,
                    selectedTab = selectedTab
                )
            }

            if (tableItems.size > 8) {
                item {
                    OutlinedButton(
                        onClick = { isTableExpanded = !isTableExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Icon(
                            imageVector = if (isTableExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isTableExpanded) "Collapse Schedule (Showing Top 8 of ${tableItems.size})" else "Show All ${tableItems.size} Dates in Schedule ▼",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            } else {
                // Tab 3: Consistency & Range Analysis Tab
                // 1. Benchmark Hurdle Settings Card (Synchronized with Benchmark Area)
                item {
                    DailySipBenchmarkSettingsCard(
                        rawBenchmarkXirr = rawBenchmarkXirr,
                        benchmarkRate = benchmarkRate,
                        onBenchmarkChange = { newVal: String ->
                            rawBenchmarkXirr = newVal
                            sharedPreferences.edit().putString("benchmark_xirr", newVal).apply()
                        },
                        isDark = isDarkTheme,
                        cardBg = cardBg,
                        borderColor = borderColor
                    )
                }

                // 2. Comprehensive Return Range Distribution & Consistency Component
                item {
                    DailySipConsistencyDistributionComponent(
                        points = simulationPoints,
                        benchmarkRate = benchmarkRate,
                        isDark = isDarkTheme,
                        cardBg = cardBg,
                        borderColor = borderColor
                    )
                }

                // 3. Horizon & Market Phase Benchmark Resilience Breakdown
                item {
                    DailySipRegimeResilienceCard(
                        points = simulationPoints,
                        benchmarkRate = benchmarkRate,
                        isDark = isDarkTheme,
                        cardBg = cardBg,
                        borderColor = borderColor
                    )
                }
            }

            item {
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

/**
 * Interactive Chart with Top Return Curve and Bottom NAV / SMA synchronization.
 */
@Composable
fun DailySipInteractiveChart(
    points: List<DailySipInceptionPoint>,
    isDark: Boolean,
    selectedTab: Int
) {
    // Exclude the single-day last SIP (1 installment, 0% return) that creates an ugly dip to zero
    val chartPoints = remember(points) {
        if (points.size > 2) points.filter { it.installments > 1 } else points
    }
    if (chartPoints.size < 2) return

    var touchFraction by remember { mutableStateOf<Float?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val yLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
    )

    val minReturn = remember(chartPoints, selectedTab) {
        val values = chartPoints.map { pt ->
            when (selectedTab) {
                0, 2 -> pt.xirrPct
                1 -> pt.absoluteReturnPct
                else -> pt.entryNavReturnPct
            }
        }.filter { !it.isNaN() && !it.isInfinite() }
        if (values.isEmpty()) 0.0
        else {
            val minVal = values.minOrNull() ?: 0.0
            if (minVal < 0) minVal * 1.12 - 2.0 else minVal * 0.88 - 2.0
        }
    }
    val maxReturn = remember(chartPoints, selectedTab, minReturn) {
        val values = chartPoints.map { pt ->
            when (selectedTab) {
                0, 2 -> pt.xirrPct
                1 -> pt.absoluteReturnPct
                else -> pt.entryNavReturnPct
            }
        }.filter { !it.isNaN() && !it.isInfinite() }
        if (values.isEmpty()) 20.0
        else {
            val maxVal = values.maxOrNull() ?: 20.0
            val padded = if (maxVal > 0) maxVal * 1.12 + 2.0 else maxVal * 0.88 + 2.0
            padded.coerceAtLeast(minReturn + 5.0)
        }
    }

    val activeIndex = remember(touchFraction, chartPoints) {
        touchFraction?.let { tf ->
            val idx = (tf * (chartPoints.size - 1)).toInt().coerceIn(0, chartPoints.size - 1)
            idx
        }
    }
    val activePoint = activeIndex?.let { chartPoints[it] }

    val primaryLineColor = when (selectedTab) {
        0 -> Color(0xFF00E676)
        1 -> Color(0xFF00E5FF)
        2 -> Color(0xFFFFB300)
        else -> Color(0xFFFFB300)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Floating inspection tooltip if touched
        if (activePoint != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E2429) else Color(0xFFEEF2F6)
                ),
                border = BorderStroke(1.dp, primaryLineColor.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (selectedTab == 2) "Valuation: ${activePoint.startDateStr}" else "Start: ${activePoint.startDateStr}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${activePoint.installments} Installments • ₹${String.format(Locale.US, "%,.0f", activePoint.totalInvested)} Invested",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = when (selectedTab) {
                                    0 -> String.format(Locale.US, "XIRR: %.2f%%", activePoint.xirrPct)
                                    1 -> String.format(Locale.US, "SIP Return: %.2f%%", activePoint.absoluteReturnPct)
                                    2 -> String.format(Locale.US, "Inverse XIRR: %.2f%%", activePoint.xirrPct)
                                    else -> String.format(Locale.US, "NAV Growth: %.2f%%", activePoint.entryNavReturnPct)
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = primaryLineColor
                            )
                            Text(
                                text = "Valuation: ₹${String.format(Locale.US, "%,.0f", activePoint.currentValue)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (selectedTab == 2) {
                            "Valuation NAV: ₹${String.format(Locale.US, "%.4f", activePoint.entryNav)} • Abs Return: ${String.format(Locale.US, "%s%.2f%%", if (activePoint.absoluteReturnPct >= 0) "+" else "", activePoint.absoluteReturnPct)}"
                        } else {
                            "Entry NAV: ₹${String.format(Locale.US, "%.4f", activePoint.entryNav)}" +
                                    " • NAV Growth: ${String.format(Locale.US, "%s%.2f%%", if (activePoint.entryNavReturnPct >= 0) "+" else "", activePoint.entryNavReturnPct)}" +
                                    (if (activePoint.sma20 != null) " • 20D: ₹${String.format(Locale.US, "%.2f", activePoint.sma20)}" else "") +
                                    (if (activePoint.sma50 != null) " • 50D: ₹${String.format(Locale.US, "%.2f", activePoint.sma50)}" else "")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Canvas Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .pointerInput(chartPoints) {
                    detectTapGestures(
                        onPress = { offset ->
                            val padRight = 44.dp.toPx()
                            val chartW = (size.width - padRight).coerceAtLeast(1f)
                            touchFraction = (offset.x / chartW).coerceIn(0f, 1f)
                        },
                        onTap = { offset ->
                            val padRight = 44.dp.toPx()
                            val chartW = (size.width - padRight).coerceAtLeast(1f)
                            touchFraction = (offset.x / chartW).coerceIn(0f, 1f)
                        }
                    )
                }
                .pointerInput(chartPoints) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val padRight = 44.dp.toPx()
                            val chartW = (size.width - padRight).coerceAtLeast(1f)
                            touchFraction = (offset.x / chartW).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            // Keep last touched point visible for easy reading
                        },
                        onDragCancel = {},
                        onDrag = { change, _ ->
                            val dx = abs(change.position.x - change.previousPosition.x)
                            val dy = abs(change.position.y - change.previousPosition.y)
                            if (dx > dy) {
                                change.consume()
                            }
                            val padRight = 44.dp.toPx()
                            val chartW = (size.width - padRight).coerceAtLeast(1f)
                            touchFraction = (change.position.x / chartW).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val padBottom = 24.dp.toPx()
                val padRight = 44.dp.toPx()
                val chartW = w - padRight
                val chartH = h - padBottom

                val rangeReturn = (maxReturn - minReturn).coerceAtLeast(0.001)

                // Draw Grid Lines & Y-axis scale labels
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = (chartH / gridLines) * i
                    drawLine(
                        color = if (isDark) Color(0xFF2C3238).copy(alpha = 0.5f) else Color(0xFFE2E8F0),
                        start = Offset(0f, y),
                        end = Offset(chartW, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    val returnVal = maxReturn - ((maxReturn - minReturn) * (i.toDouble() / gridLines.toDouble()))
                    val labelStr = String.format(Locale.US, "%s%.0f%%", if (returnVal > 0) "+" else "", returnVal)
                    val layoutRes = textMeasurer.measure(labelStr, style = yLabelStyle)
                    val labelY = (y - layoutRes.size.height / 2f).coerceIn(0f, chartH - layoutRes.size.height)
                    drawText(textLayoutResult = layoutRes, topLeft = Offset(chartW + 6.dp.toPx(), labelY))
                }

                // Zero Reference Baseline (if 0.0 is between minReturn and maxReturn)
                if (minReturn < 0.0 && maxReturn > 0.0) {
                    val zeroNormY = ((0.0 - minReturn) / rangeReturn).toFloat().coerceIn(0f, 1f)
                    val zeroY = chartH - (zeroNormY * chartH)
                    drawLine(
                        color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f),
                        start = Offset(0f, zeroY),
                        end = Offset(chartW, zeroY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )
                    val zeroLabelRes = textMeasurer.measure("0%", style = yLabelStyle.copy(fontWeight = FontWeight.Bold, color = if (isDark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.75f)))
                    drawText(textLayoutResult = zeroLabelRes, topLeft = Offset(chartW + 6.dp.toPx(), (zeroY - zeroLabelRes.size.height / 2f).coerceIn(0f, chartH - zeroLabelRes.size.height)))
                }

                // Build Return Path
                val returnPath = Path()
                val returnAreaPath = Path()

                val n = chartPoints.size
                for (i in 0 until n) {
                    val pt = chartPoints[i]
                    val x = (i.toFloat() / (n - 1).toFloat()) * chartW
                    val valY = when (selectedTab) {
                        0, 2 -> pt.xirrPct
                        1 -> pt.absoluteReturnPct
                        else -> pt.entryNavReturnPct
                    }
                    val normY = ((valY - minReturn) / rangeReturn).toFloat().coerceIn(0f, 1f)
                    val y = chartH - (normY * chartH)

                    if (i == 0) {
                        returnPath.moveTo(x, y)
                        returnAreaPath.moveTo(x, chartH)
                        returnAreaPath.lineTo(x, y)
                    } else {
                        returnPath.lineTo(x, y)
                        returnAreaPath.lineTo(x, y)
                    }
                }
                returnAreaPath.lineTo(chartW, chartH)
                returnAreaPath.close()

                // Draw Area Gradient
                drawPath(
                    path = returnAreaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryLineColor.copy(alpha = 0.35f), primaryLineColor.copy(alpha = 0.0f)),
                        startY = 0f,
                        endY = chartH
                    )
                )
                // Draw Return Stroke
                drawPath(
                    path = returnPath,
                    color = primaryLineColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Draw Touch Cursor Line & Indicator Dot
                if (touchFraction != null && activeIndex != null) {
                    val activeX = (activeIndex.toFloat() / (n - 1).toFloat()) * chartW

                    // Vertical crosshair
                    drawLine(
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                        start = Offset(activeX, 0f),
                        end = Offset(activeX, chartH),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Point on curve
                    val activePt = chartPoints[activeIndex]
                    val valY = when (selectedTab) {
                        0, 2 -> activePt.xirrPct
                        1 -> activePt.absoluteReturnPct
                        else -> activePt.entryNavReturnPct
                    }
                    val normY = ((valY - minReturn) / rangeReturn).toFloat().coerceIn(0f, 1f)
                    val activeY = chartH - (normY * chartH)

                    drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = Offset(activeX, activeY))
                    drawCircle(color = primaryLineColor, radius = 3.5.dp.toPx(), center = Offset(activeX, activeY))
                }
            }
        }

        // X-Axis Timeline Milestone Dates Row (4 evenly spaced dates spanning full chart width)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp, end = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelCount = 4
            val step = (chartPoints.size - 1) / (labelCount - 1)
            for (idx in 0 until labelCount) {
                val pIdx = (idx * step).coerceAtMost(chartPoints.size - 1)
                val labelText = chartPoints[pIdx].startDateStr
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Dedicated Chart Legend Row centered below timeline dates
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(primaryLineColor, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (selectedTab == 0) "Daily SIP XIRR" else if (selectedTab == 1) "SIP Abs Return" else if (selectedTab == 2) "Inverse SIP XIRR" else "NAV Growth",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Individual Row in the Daily SIP Schedule Table.
 */
@Composable
fun DailySipRowCard(
    point: DailySipInceptionPoint,
    isDark: Boolean,
    borderColor: Color,
    cardBg: Color,
    selectedTab: Int
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, if (point.isDipEntry) Color(0xFF00E676).copy(alpha = 0.3f) else borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: Date & Installments
                Column(modifier = Modifier.weight(1.3f)) {
                    Text(
                        text = point.startDateStr,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${point.installments} installments",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Column 2: Entry NAV & SMA Status
                Column(modifier = Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "₹%.4f", point.entryNav),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedTab != 2 && point.sma20DiffPct != null) {
                        val isBelow = point.sma20DiffPct < 0
                        val chipColor = if (isBelow) Color(0xFF00E676) else Color(0xFFFFB300)
                        Text(
                            text = String.format(Locale.US, "%s%.1f%% vs 20D", if (isBelow) "" else "+", point.sma20DiffPct),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = chipColor
                        )
                    }
                }

                // Column 3: Absolute Return % & NAV Growth
                Column(modifier = Modifier.weight(1.0f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, "%.2f%%", point.absoluteReturnPct),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (point.absoluteReturnPct >= 0) Color(0xFF00E676) else Color(0xFFFF5252),
                        textAlign = TextAlign.End
                    )
                    if (selectedTab != 2) {
                        Text(
                            text = String.format(Locale.US, "NAV: %s%.2f%%", if (point.entryNavReturnPct >= 0) "+" else "", point.entryNavReturnPct),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    } else {
                        Text(
                            text = "Growth",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Column 4: XIRR %
                Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f%%", point.xirrPct),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }

            // Expandable details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Invested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format(Locale.US, "%,.0f", point.totalInvested)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Current Value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format(Locale.US, "%,.0f", point.currentValue)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Net Wealth Gain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val gain = point.currentValue - point.totalInvested
                            Text(
                                "₹${String.format(Locale.US, "%,.0f", gain)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (gain >= 0) Color(0xFF00E676) else Color(0xFFFF5252)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "SIP Installments Return: %.2f%%", point.absoluteReturnPct),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "NAV Point-to-Point Growth: %s%.2f%%", if (point.entryNavReturnPct >= 0) "+" else "", point.entryNavReturnPct),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (point.sma20 != null || point.sma50 != null) {
                        Spacer(Modifier.height(6.dp))
                        val sma20Text = if (point.sma20 != null) "20-DMA: ₹${String.format(Locale.US, "%.2f", point.sma20)}" else ""
                        val sma50Text = if (point.sma50 != null) "50-DMA: ₹${String.format(Locale.US, "%.2f", point.sma50)}" else ""
                        val statusText = if (point.isDipEntry) "Favorable Pullback Entry" else "Rally Entry"
                        Text(
                            text = listOf(sma20Text, sma50Text, statusText).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Data model representing a Daily SIP simulated from a fixed start date up to each historical date.
 */
data class InverseSipPoint(
    val endDateStr: String,
    val endTimestamp: Long,
    val installments: Int,
    val totalInvested: Double,
    val currentValue: Double,
    val absoluteReturnPct: Double,
    val xirrPct: Double,
    val endNav: Double
)

/**
 * Computes the Inverse SIP series starting at a fixed date up to each date in recent history.
 */
fun computeInverseSipSeries(
    records: List<Pair<NavRecord, Long>>,
    fixedStartTime: Long
): List<InverseSipPoint> {
    if (records.size < 2) return emptyList()

    val result = mutableListOf<InverseSipPoint>()
    val startIndex = records.indexOfFirst { it.second >= fixedStartTime }.coerceAtLeast(0)
    
    // Iteratively build the SIP up to each point k >= startIndex
    var currentUnits = 0.0
    var currentInvested = 0.0
    val currentCashFlows = mutableListOf<Pair<Long, Double>>()

    for (k in startIndex until records.size) {
        val rec = records[k]
        val navK = rec.first.nav
        val timeK = rec.second
        
        if (navK > 0.0) {
            val investedAmount = 1000.0
            currentUnits += investedAmount / navK
            currentInvested += investedAmount
            currentCashFlows.add(Pair(timeK, -investedAmount))
        }

        if (currentInvested > 0.0) {
            val currentValue = currentUnits * navK
            val absReturnPct = ((currentValue - currentInvested) / currentInvested) * 100.0
            
            val tempFlows = currentCashFlows.toMutableList()
            tempFlows.add(Pair(timeK, currentValue))
            
            val installments = currentCashFlows.size
            val daysActive = if (currentCashFlows.isNotEmpty()) {
                (timeK - currentCashFlows.first().first) / (1000.0 * 60 * 60 * 24)
            } else 0.0
            
            val xirr = if (installments >= 5 && daysActive >= 10.0) {
                val res = calculateXIRR(tempFlows) * 100.0
                if (res.isNaN() || res.isInfinite() || res <= -90.0) {
                    val fallback = ReturnsCalculations.trueXirr(tempFlows)
                    if (fallback.isNaN() || fallback.isInfinite() || fallback <= -90.0) absReturnPct else fallback
                } else res.coerceIn(-90.0, 250.0)
            } else if (installments > 1 && daysActive > 0.5) {
                val annualized = absReturnPct * (365.0 / daysActive.coerceAtLeast(1.0))
                annualized.coerceIn(-50.0, 100.0)
            } else absReturnPct

            result.add(
                InverseSipPoint(
                    endDateStr = rec.first.dateStr,
                    endTimestamp = timeK,
                    installments = installments,
                    totalInvested = currentInvested,
                    currentValue = currentValue,
                    absoluteReturnPct = absReturnPct,
                    xirrPct = xirr,
                    endNav = navK
                )
            )
        }
    }

    return result
}

/**
 * Computes the Daily SIP series starting at each date from inception to recent.
 */
fun computeDailySipInceptionSeries(
    records: List<Pair<NavRecord, Long>>
): List<DailySipInceptionPoint> {
    if (records.size < 5) return emptyList()

    val n = records.size
    val lastPoint = records.last()
    val lastNav = lastPoint.first.nav
    val lastTime = lastPoint.second

    val rawNavList = records.map { it.first }
    val result = mutableListOf<DailySipInceptionPoint>()

    // Simulate daily SIP starting on each index i from 0 up to (n - 1), investing exclusively on market (trading) days
    for (i in 0 until n) {
        val startPoint = records[i]
        val startTime = startPoint.second
        val startDateStr = startPoint.first.dateStr

        var totalUnits = 0.0
        var totalInvested = 0.0
        val cashFlows = mutableListOf<Pair<Long, Double>>()

        for (k in i until n) {
            val rec = records[k]
            val navK = rec.first.nav
            if (navK > 0.0) {
                val investedAmount = 1000.0
                totalUnits += investedAmount / navK
                totalInvested += investedAmount
                cashFlows.add(Pair(rec.second, -investedAmount))
            }
        }

        val currentValue = totalUnits * lastNav
        val absReturnPct = if (totalInvested > 0.0) {
            ((currentValue - totalInvested) / totalInvested) * 100.0
        } else 0.0

        val entryNav = startPoint.first.nav
        val entryNavReturnPct = if (entryNav > 0.0) {
            ((lastNav - entryNav) / entryNav) * 100.0
        } else 0.0

        cashFlows.add(Pair(lastTime, currentValue))

        val installments = cashFlows.size - 1
        val daysActive = (lastTime - startTime) / (1000.0 * 60 * 60 * 24)
        val rawXirr = if (installments > 1 && daysActive > 0.5) {
            val res = calculateXIRR(cashFlows) * 100.0
            if (res.isNaN() || res.isInfinite() || res <= -99.0) {
                val fallback = ReturnsCalculations.trueXirr(cashFlows)
                if (fallback.isNaN() || fallback.isInfinite() || fallback <= -99.0) absReturnPct else fallback
            } else res
        } else absReturnPct

        // Moving Average on start date
        val sma20 = calculateSMA(rawNavList, 20, i)
        val sma50 = calculateSMA(rawNavList, 50, i)
        val sma20DiffPct = if (sma20 != null && sma20 > 0.0) {
            ((entryNav - sma20) / sma20) * 100.0
        } else null

        val isDip = (sma20DiffPct != null && sma20DiffPct < 0.0)

        result.add(
            DailySipInceptionPoint(
                startDateStr = startDateStr,
                startTimestamp = startTime,
                installments = installments,
                totalInvested = totalInvested,
                currentValue = currentValue,
                absoluteReturnPct = absReturnPct,
                entryNavReturnPct = entryNavReturnPct,
                xirrPct = rawXirr,
                entryNav = entryNav,
                sma20 = sma20,
                sma50 = sma50,
                sma20DiffPct = sma20DiffPct,
                isDipEntry = isDip
            )
        )
    }

    return result
}

/**
 * Excel-compliant Newton-Raphson & Bisection XIRR solver.
 */
fun calculatePrecisionXIRR(cashFlows: List<Pair<Long, Double>>, guess: Double = 0.1): Double {
    if (cashFlows.size < 2) return 0.0
    val t0 = cashFlows.minOf { it.first }
    val msPerDay = 86_400_000.0
    
    val dayMap = mutableMapOf<Long, Double>()
    for ((t, cf) in cashFlows) {
        val dayKey = ((t - t0) / msPerDay).toLong()
        dayMap[dayKey] = (dayMap[dayKey] ?: 0.0) + cf
    }
    
    val grouped = dayMap.toList().sortedBy { it.first }
    if (grouped.size < 2) return 0.0
    
    val maxDay = grouped.maxOf { it.first }
    if (maxDay == 0L) return 0.0

    // 1. Newton-Raphson Solver with 365.0 days/year standard
    var rate = guess
    val maxIter = 100
    val tol = 1e-7

    for (i in 0 until maxIter) {
        var f = 0.0
        var df = 0.0

        for ((day, cf) in grouped) {
            val years = day / 365.0
            val denom = Math.pow(1.0 + rate, years)
            if (denom != 0.0 && !denom.isNaN() && !denom.isInfinite()) {
                f += cf / denom
                df -= years * cf / (denom * (1.0 + rate))
            }
        }

        if (Math.abs(df) < 1e-11) break
        val step = f / df
        val newRate = rate - step
        if (newRate.isNaN() || newRate.isInfinite() || newRate <= -0.99) break
        if (Math.abs(newRate - rate) < tol) {
            return newRate * 100.0
        }
        rate = newRate
    }

    // 2. Bisection Solver Fallback
    var low = -0.99
    var high = 50.0 // up to 5000%
    var npvLow = 0.0
    var npvHigh = 0.0
    for ((day, cf) in grouped) {
        val years = day / 365.0
        npvLow += cf / Math.pow(1.0 + low, years)
        npvHigh += cf / Math.pow(1.0 + high, years)
    }
    
    if (npvLow * npvHigh <= 0) {
        for (i in 0 until 100) {
            val mid = (low + high) / 2.0
            var npvMid = 0.0
            for ((day, cf) in grouped) {
                val years = day / 365.0
                npvMid += cf / Math.pow(1.0 + mid, years)
            }
            if (Math.abs(npvMid) < 1e-6) return mid * 100.0
            if (npvMid * npvLow < 0) high = mid else low = mid
        }
        return ((low + high) / 2.0) * 100.0
    }

    // 3. Extended Range Bisection for super-short durations
    var altHigh = 500.0 // up to 50,000%
    var npvAltHigh = 0.0
    for ((day, cf) in grouped) {
        val years = day / 365.0
        npvAltHigh += cf / Math.pow(1.0 + altHigh, years)
    }
    if (npvLow * npvAltHigh <= 0) {
        var aLow = low
        var aHigh = altHigh
        for (i in 0 until 100) {
            val mid = (aLow + aHigh) / 2.0
            var npvMid = 0.0
            for ((day, cf) in grouped) {
                val years = day / 365.0
                npvMid += cf / Math.pow(1.0 + mid, years)
            }
            if (Math.abs(npvMid) < 1e-6) return mid * 100.0
            if (npvMid * npvLow < 0) aHigh = mid else aLow = mid
        }
        return ((aLow + aHigh) / 2.0) * 100.0
    }

    return 0.0
}

/**
 * Data models for SIP Return Distribution and Rolling Consistency analysis
 */
data class ReturnRangeBucket(
    val id: String,
    val label: String,
    val shortLabel: String,
    val tierName: String,
    val minVal: Double,
    val maxVal: Double,
    val color: Color,
    val count: Int,
    val percentage: Double,
    val sampleDates: List<String>,
    val avgReturn: Double
)

private data class BucketRawDef(
    val id: String,
    val label: String,
    val shortLabel: String,
    val tierName: String,
    val minVal: Double,
    val maxVal: Double,
    val color: Color
)

data class SipConsistencyStats(
    val totalDatesEvaluated: Int,
    val minReturn: Double,
    val maxReturn: Double,
    val medianReturn: Double,
    val avgReturn: Double,
    val winRatePct: Double,
    val benchmarkBeatPct: Double,
    val highAlphaPct: Double,
    val downsideRiskPct: Double,
    val consistencyGrade: String,
    val gradeColor: Color,
    val buckets: List<ReturnRangeBucket>,
    val effectiveBenchmarkRate: Double
)

fun computeSipConsistencyStats(
    points: List<DailySipInceptionPoint>,
    metric: String, // "XIRR" or "ABSOLUTE"
    benchmarkRate: Double = 12.0
): SipConsistencyStats? {
    if (points.isEmpty()) return null

    // For XIRR, exclude recent short-term start dates (<10 installments) and extreme annualization spikes (>150%)
    val validPoints = if (metric == "XIRR") {
        val filtered = points.filter { pt ->
            pt.installments >= 10 &&
            !pt.xirrPct.isNaN() &&
            !pt.xirrPct.isInfinite() &&
            pt.xirrPct in -90.0..150.0
        }
        if (filtered.size >= 5) filtered else {
            points.filter { pt ->
                pt.installments >= 3 &&
                !pt.xirrPct.isNaN() &&
                !pt.xirrPct.isInfinite() &&
                pt.xirrPct in -90.0..250.0
            }
        }
    } else {
        // Absolute return is valid for all multi-day SIPs
        points.filter { it.installments > 1 }
    }

    if (validPoints.isEmpty()) return null

    val total = validPoints.size
    val values = validPoints.map { if (metric == "XIRR") it.xirrPct else it.absoluteReturnPct }
    val sortedValues = values.sorted()

    val minReturn = sortedValues.first()
    val maxReturn = sortedValues.last()
    val avgReturn = values.average()
    val medianReturn = if (total % 2 == 1) sortedValues[total / 2] else (sortedValues[total / 2 - 1] + sortedValues[total / 2]) / 2.0

    val winRatePct = (values.count { it > 0.0 }.toDouble() / total) * 100.0
    val effectiveBenchmark = if (benchmarkRate > 0.0) benchmarkRate else 12.0
    val hurdleThreshold = if (metric == "XIRR") effectiveBenchmark else (effectiveBenchmark * 0.85).coerceAtLeast(6.0)
    val benchmarkBeatPct = (values.count { it >= hurdleThreshold }.toDouble() / total) * 100.0
    val highAlphaPct = (values.count { it >= (effectiveBenchmark + 3.0) }.toDouble() / total) * 100.0
    val downsideRiskPct = (values.count { it < 0.0 }.toDouble() / total) * 100.0

    val (grade, gradeColor) = when {
        winRatePct >= 99.0 && benchmarkBeatPct >= 70.0 -> Pair("Grade A+ • Exceptional", Color(0xFF00E676))
        winRatePct >= 90.0 && benchmarkBeatPct >= 45.0 -> Pair("Grade A • High Consistency", Color(0xFF00E5FF))
        winRatePct >= 80.0 -> Pair("Grade B • Moderate Consistency", Color(0xFFFFB300))
        else -> Pair("Grade C • Volatile / Cyclical", Color(0xFFFF5252))
    }

    val bHigh = (effectiveBenchmark + 5.0).coerceAtLeast(18.0)
    val bMid = effectiveBenchmark
    val bLow = (effectiveBenchmark - 4.0).coerceAtLeast(6.0)

    val bucketDefs = if (metric == "XIRR") {
        listOf(
            BucketRawDef("ALPHA", String.format(Locale.US, "> %.0f%% (High Alpha)", bHigh), String.format(Locale.US, "> %.0f%%", bHigh), "High Alpha", bHigh, Double.MAX_VALUE, Color(0xFF7C4DFF)),
            BucketRawDef("ABOVE", String.format(Locale.US, "%.0f%% – %.0f%% (Above Benchmark)", bMid, bHigh), String.format(Locale.US, "%.0f%%–%.0f%%", bMid, bHigh), "Above Bench", bMid, bHigh, Color(0xFF00E5FF)),
            BucketRawDef("NEAR", String.format(Locale.US, "%.0f%% – %.0f%% (Near Benchmark)", bLow, bMid), String.format(Locale.US, "%.0f%%–%.0f%%", bLow, bMid), "Near Bench", bLow, bMid, Color(0xFF00E676)),
            BucketRawDef("MODERATE", String.format(Locale.US, "0%% – %.0f%% (Moderate)", bLow), String.format(Locale.US, "0%%–%.0f%%", bLow), "Moderate", 0.0, bLow, Color(0xFFFFB300)),
            BucketRawDef("DIP", "< 0% (Capital Dip)", "< 0%", "Capital Dip", -Double.MAX_VALUE, 0.0, Color(0xFFFF5252))
        )
    } else {
        listOf(
            BucketRawDef("ALPHA", "> 20% (Substantial)", "> 20%", "Substantial", 20.0, Double.MAX_VALUE, Color(0xFF7C4DFF)),
            BucketRawDef("ABOVE", String.format(Locale.US, "%.0f%% – 20%%", effectiveBenchmark), String.format(Locale.US, "%.0f%%–20%%", effectiveBenchmark), "Above Bench", effectiveBenchmark, 20.0, Color(0xFF00E5FF)),
            BucketRawDef("NEAR", String.format(Locale.US, "5%% – %.0f%%", effectiveBenchmark), String.format(Locale.US, "5%%–%.0f%%", effectiveBenchmark), "Near Bench", 5.0, effectiveBenchmark, Color(0xFF00E676)),
            BucketRawDef("MODERATE", "0% – 5% (Marginal)", "0%–5%", "Marginal", 0.0, 5.0, Color(0xFFFFB300)),
            BucketRawDef("DIP", "< 0% (Capital Dip)", "< 0%", "Capital Dip", -Double.MAX_VALUE, 0.0, Color(0xFFFF5252))
        )
    }

    val buckets = bucketDefs.map { def ->
        val matchingPoints = validPoints.filter { pt ->
            val v = if (metric == "XIRR") pt.xirrPct else pt.absoluteReturnPct
            if (def.maxVal == Double.MAX_VALUE) v >= def.minVal
            else if (def.minVal == -Double.MAX_VALUE) v < def.maxVal
            else v >= def.minVal && v < def.maxVal
        }
        val count = matchingPoints.size
        val pct = (count.toDouble() / total) * 100.0
        val avgVal = if (matchingPoints.isNotEmpty()) {
            matchingPoints.map { if (metric == "XIRR") it.xirrPct else it.absoluteReturnPct }.average()
        } else 0.0

        ReturnRangeBucket(
            id = def.id,
            label = def.label,
            shortLabel = def.shortLabel,
            tierName = def.tierName,
            minVal = def.minVal,
            maxVal = def.maxVal,
            color = def.color,
            count = count,
            percentage = pct,
            sampleDates = matchingPoints.take(5).map { it.startDateStr },
            avgReturn = avgVal
        )
    }

    return SipConsistencyStats(
        totalDatesEvaluated = total,
        minReturn = minReturn,
        maxReturn = maxReturn,
        medianReturn = medianReturn,
        avgReturn = avgReturn,
        winRatePct = winRatePct,
        benchmarkBeatPct = benchmarkBeatPct,
        highAlphaPct = highAlphaPct,
        downsideRiskPct = downsideRiskPct,
        consistencyGrade = grade,
        gradeColor = gradeColor,
        buckets = buckets,
        effectiveBenchmarkRate = effectiveBenchmark
    )
}

/**
 * Return Range & Consistency Analysis Component.
 * Analyzes the percentage of daily SIP start dates producing returns in specific ranges,
 * ignoring recent illogical short-term annualized XIRR spikes (< 10 installments).
 */
@Composable
fun DailySipConsistencyDistributionComponent(
    points: List<DailySipInceptionPoint>,
    benchmarkRate: Double = 12.0,
    isDark: Boolean,
    cardBg: Color,
    borderColor: Color
) {
    var metric by remember { mutableStateOf("XIRR") } // "XIRR" or "ABSOLUTE"
    var expandedBucketId by remember { mutableStateOf<String?>(null) }

    val stats = remember(points, metric, benchmarkRate) {
        computeSipConsistencyStats(points, metric, benchmarkRate)
    }

    if (stats == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title and Metric Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SIP Return Range & Consistency",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Historical distribution across ${stats.totalDatesEvaluated} entry dates",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Grade Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(stats.gradeColor.copy(alpha = 0.15f))
                        .border(1.dp, stats.gradeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stats.consistencyGrade,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = stats.gradeColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Metric Toggle Tabs: XIRR vs Absolute Return
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0xFF1E2429) else Color(0xFFEEF2F6))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("XIRR" to "Annualised XIRR (%)", "ABSOLUTE" to "Absolute Gain (%)").forEach { (key, label) ->
                    val isSelected = metric == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { metric = key }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Method Explainer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (metric == "XIRR") {
                        "Simulates starting a daily SIP on each past market day. Start dates with <10 installments are excluded to eliminate short-term annualization distortion."
                    } else {
                        "Simulates cumulative net portfolio gain across all starting dates from inception up to today."
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            // 4 KPI Summary Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Win Rate Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF0E231B) else Color(0xFFE8F5E9)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Positive Days (Win)",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color(0xFF00E676)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f%%", stats.winRatePct),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00E676)
                        )
                        Text(
                            text = if (stats.downsideRiskPct == 0.0) "0% capital loss" else "${String.format(Locale.US, "%.1f%%", stats.downsideRiskPct)} loss risk",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Benchmark Hurdle Card
                val benchDisplayRate = stats.effectiveBenchmarkRate
                val targetLabel = if (metric == "XIRR") {
                    "≥ ${String.format(Locale.US, if (benchDisplayRate % 1.0 == 0.0) "%.0f%%" else "%.1f%%", benchDisplayRate)} Hurdle"
                } else {
                    "≥ 10% Return"
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF13222E) else Color(0xFFE0F7FA)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = targetLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f%%", stats.benchmarkBeatPct),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = "Beats benchmark",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Median Outcome Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF241D12) else Color(0xFFFFF8E1)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Median SIP Return",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color(0xFFFFB300)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.2f%%", stats.medianReturn),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFB300)
                        )
                        Text(
                            text = "50% dates did better",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Min to Max Spread Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E1729) else Color(0xFFEDE7F6)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Worst vs Best Start",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color(0xFF7C4DFF)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f%% – %.1f%%", stats.minReturn, stats.maxReturn),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF7C4DFF)
                        )
                        Text(
                            text = "Historical band",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Graphical Diagram 1: Proportional Spectrum Strip Chart
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF13181C) else Color(0xFFF8FAFC))
                    .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Distribution Spectrum",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${stats.totalDatesEvaluated} start dates (100%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Continuous Proportional Spectrum Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1E2429) else Color(0xFFE2E8F0)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    stats.buckets.filter { it.percentage > 0.0 }.forEach { bucket ->
                        Box(
                            modifier = Modifier
                                .weight(bucket.percentage.toFloat().coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(bucket.color)
                                .clickable {
                                    expandedBucketId = if (expandedBucketId == bucket.id) null else bucket.id
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bucket.percentage >= 15.0) {
                                Text(
                                    text = String.format(Locale.US, "%.0f%%", bucket.percentage),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    ),
                                    color = Color.Black.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Interactive Responsive FlowRow Legend
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stats.buckets.forEach { b ->
                        val isSelected = expandedBucketId == b.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) b.color.copy(alpha = 0.18f) else if (isDark) Color(0xFF1E2429) else Color(0xFFEDF2F7),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) b.color else Color.Transparent
                            ),
                            modifier = Modifier.clickable {
                                expandedBucketId = if (isSelected) null else b.id
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(b.color, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = b.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "(${b.tierName}):",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", b.percentage),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (b.percentage > 0) b.color else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Graphical Diagram 2: Horizontal Distribution Frequency Histogram (Bar Chart)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Return Range Breakdown",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tap tier to view details",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            val maxPct = stats.buckets.maxOfOrNull { it.percentage }?.coerceAtLeast(1.0) ?: 1.0

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.buckets.forEach { bucket ->
                    val isExpanded = expandedBucketId == bucket.id
                    val barFraction = (bucket.percentage / maxPct).toFloat().coerceIn(0f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = barFraction,
                        label = "barFraction_${bucket.id}"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                expandedBucketId = if (isExpanded) null else bucket.id
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpanded) {
                                bucket.color.copy(alpha = 0.12f)
                            } else {
                                if (isDark) Color(0xFF1B2025) else Color(0xFFF1F5F9)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isExpanded) bucket.color.copy(alpha = 0.6f) else borderColor.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Top Row: Category title + Range badge on left, Percentage & count on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(bucket.color, CircleShape)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = bucket.tierName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(bucket.color.copy(alpha = 0.18f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = bucket.shortLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = bucket.color
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", bucket.percentage),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "(${bucket.count} dates)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Full-width Visual Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isDark) Color(0xFF101417) else Color(0xFFE2E8F0))
                            ) {
                                if (animatedFraction > 0.005f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(animatedFraction)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(bucket.color.copy(alpha = 0.7f), bucket.color)
                                                )
                                            )
                                    )
                                }
                            }

                            // Expanded detail when tapped
                            AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    HorizontalDivider(color = bucket.color.copy(alpha = 0.3f))
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Bracket Average Return:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.2f%% %s", bucket.avgReturn, metric),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = bucket.color
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${bucket.count} of ${stats.totalDatesEvaluated} possible daily start dates achieved this bracket.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (bucket.sampleDates.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Sample start dates: ${bucket.sampleDates.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Executive Consistency Takeaway
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (stats.winRatePct >= 95.0) Color(0xFF00E676).copy(alpha = 0.1f)
                        else Color(0xFFFFB300).copy(alpha = 0.1f)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (stats.winRatePct >= 95.0) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (stats.winRatePct >= 95.0) Color(0xFF00E676) else Color(0xFFFFB300),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (stats.winRatePct == 100.0) {
                        "Unshakable Compounding: 100% of all possible Daily SIP start dates produced positive returns. ${String.format(Locale.US, "%.1f%%", stats.benchmarkBeatPct)} of starting dates beat your target ${String.format(Locale.US, "%.1f", stats.effectiveBenchmarkRate)}% benchmark hurdle, proving that daily rupee-cost averaging systematically smooths market cycles."
                    } else {
                        "Consistency Insight: Across all possible Daily SIP entry points, ${String.format(Locale.US, "%.1f%%", stats.winRatePct)} delivered positive returns and ${String.format(Locale.US, "%.1f%%", stats.benchmarkBeatPct)} beat the ${String.format(Locale.US, "%.1f", stats.effectiveBenchmarkRate)}% benchmark hurdle with a median return of ${String.format(Locale.US, "%.2f%%", stats.medianReturn)}."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (stats.winRatePct >= 95.0) Color(0xFF00E676) else Color(0xFFFFB300)
                )
            }
        }
    }
}

/**
 * Benchmark Hurdle Settings Card.
 * Allows user to dynamically inspect and change the target benchmark hurdle (persisted to SharedPreferences).
 */
@Composable
fun DailySipBenchmarkSettingsCard(
    rawBenchmarkXirr: String,
    benchmarkRate: Double,
    onBenchmarkChange: (String) -> Unit,
    isDark: Boolean,
    cardBg: Color,
    borderColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Target Benchmark Hurdle",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Synced with Benchmark Comparison Area",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Active: ${String.format(Locale.US, if (benchmarkRate % 1.0 == 0.0) "%.0f%%" else "%.1f%%", benchmarkRate)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = rawBenchmarkXirr,
                    onValueChange = onBenchmarkChange,
                    label = { Text("Bench XIRR %") },
                    trailingIcon = {
                        Text(
                            "%",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )

                // Quick preset hurdle chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("10", "12", "14", "15").forEach { preset ->
                        val isPresetActive = rawBenchmarkXirr.trim() == preset
                        FilterChip(
                            selected = isPresetActive,
                            onClick = { onBenchmarkChange(preset) },
                            label = {
                                Text(
                                    "$preset%",
                                    fontSize = 11.sp,
                                    fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Benchmark Resilience Across Market Regimes & Tenures Card.
 * Evaluates performance of Daily SIP across all historical dates, holding periods, and entry dip/peak conditions.
 */
@Composable
fun DailySipRegimeResilienceCard(
    points: List<DailySipInceptionPoint>,
    benchmarkRate: Double,
    isDark: Boolean,
    cardBg: Color,
    borderColor: Color
) {
    val validPoints = remember(points) {
        val filtered = points.filter { pt ->
            pt.installments >= 10 &&
            !pt.xirrPct.isNaN() &&
            !pt.xirrPct.isInfinite() &&
            pt.xirrPct in -90.0..150.0
        }
        if (filtered.size >= 5) filtered else points.filter { it.installments >= 3 }
    }

    if (validPoints.isEmpty()) return

    data class RegimeRow(
        val title: String,
        val subtitle: String,
        val count: Int,
        val avgXirr: Double,
        val winRatePct: Double,
        val beatBenchPct: Double,
        val tag: String,
        val tagColor: Color
    )

    val benchmarkTarget = if (benchmarkRate > 0.0) benchmarkRate else 12.0
    val now = points.lastOrNull()?.startTimestamp ?: System.currentTimeMillis()

    val rows = remember(validPoints, benchmarkTarget) {
        val threeYearsMillis = 3L * 365 * 24 * 60 * 60 * 1000L
        val oneYearMillis = 365L * 24 * 60 * 60 * 1000L

        fun eval(list: List<DailySipInceptionPoint>, name: String, sub: String): RegimeRow? {
            if (list.isEmpty()) return null
            val c = list.size
            val avgX = list.map { it.xirrPct }.average()
            val win = (list.count { it.xirrPct > 0.0 }.toDouble() / c) * 100.0
            val beat = (list.count { it.xirrPct >= benchmarkTarget }.toDouble() / c) * 100.0
            val (t, tc) = when {
                beat >= 90.0 -> "Dominant" to Color(0xFF00E676)
                beat >= 70.0 -> "Strong" to Color(0xFF00E5FF)
                beat >= 50.0 -> "Moderate" to Color(0xFFFFB300)
                else -> "Lagging" to Color(0xFFFF5252)
            }
            return RegimeRow(name, sub, c, avgX, win, beat, t, tc)
        }

        val allRow = eval(validPoints, "All Historical Start Dates", "Full inception timeline")
        val threePlus = eval(validPoints.filter { (now - it.startTimestamp) >= threeYearsMillis }, "Long-Term Holding (>3 Years)", "Multi-cycle compounding")
        val oneToThree = eval(validPoints.filter { val diff = now - it.startTimestamp; diff in oneYearMillis until threeYearsMillis }, "Medium-Term (1 to 3 Years)", "Cyclical accumulation")
        val dipStarts = eval(validPoints.filter { it.isDipEntry }, "Market Dip Entries", "Started when NAV < 50-DMA")
        val peakStarts = eval(validPoints.filter { !it.isDipEntry }, "Regular / Peak Entries", "Started when NAV ≥ 50-DMA")

        listOfNotNull(allRow, threePlus, oneToThree, dipStarts, peakStarts)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Benchmark Resilience Across Market Phases",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Holding period & entry conditions vs ${String.format(Locale.US, if (benchmarkTarget % 1.0 == 0.0) "%.0f%%" else "%.1f%%", benchmarkTarget)} target",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isDark) Color(0xFF1B2026) else Color(0xFFF1F5F9))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Regime / Phase",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = "Avg XIRR",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.9f),
                    textAlign = TextAlign.End
                )
                Text(
                    text = "Beats Bench",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.1f),
                    textAlign = TextAlign.End
                )
            }

            Spacer(Modifier.height(6.dp))

            rows.forEachIndexed { idx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${row.subtitle} (${row.count} dates)",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = String.format(Locale.US, "%.1f%%", row.avgXirr),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (row.avgXirr >= benchmarkTarget) Color(0xFF00E676) else Color(0xFFFFB300),
                        modifier = Modifier.weight(0.9f),
                        textAlign = TextAlign.End
                    )

                    Column(
                        modifier = Modifier.weight(1.1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f%%", row.beatBenchPct),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = row.tagColor,
                            textAlign = TextAlign.End
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(row.tagColor.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = row.tag,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = row.tagColor
                            )
                        }
                    }
                }
                if (idx < rows.lastIndex) {
                    HorizontalDivider(
                        color = borderColor.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
    }
}
