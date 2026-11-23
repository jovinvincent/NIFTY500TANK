package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DailyRollingItem(
    val startDateStr: String,
    val endDateStr: String,
    val absReturn: Double,
    val xirr: Double,
    val invested: Double,
    val value: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollingSipDailyDayWiseAnalysisScreen(
    baseSchemeName: String,
    allSchemes: List<String>,
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    var points by remember { mutableStateOf<List<TimelineChartPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sipStartDate by remember { mutableStateOf<Date?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var sipDurationYears by remember { mutableStateOf(0) }
    var sipDurationMonths by remember { mutableStateOf(1) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val context = LocalContext.current

    var rollingItems by remember { mutableStateOf<List<DailyRollingItem>>(emptyList()) }
    var isCalculatingRolling by remember { mutableStateOf(true) }

    LaunchedEffect(baseSchemeName) {
        isLoading = true
        withContext(Dispatchers.IO) {
            var records = NavCache.getRecords(baseSchemeName)
            if (records.isEmpty()) {
                val db = AppDatabase.getDatabase(context)
                val dbRecords = db.fundDao().getHistoricalNavs(baseSchemeName)
                if (dbRecords.isNotEmpty()) {
                    records = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                    NavCache.addAll(baseSchemeName, records)
                }
            }
            points = if (records.isNotEmpty()) {
                records.map { TimelineChartPoint(it.dateStr, it.nav, null, null, 0.0, 0.0) }.sortedBy { parseTimelineDate(it.dateStr) }
            } else {
                emptyList()
            }
        }
        if (points.isNotEmpty() && sipStartDate == null) {
            sipStartDate = Date(parseTimelineDate(points.first().dateStr))
        }
        isLoading = false
    }

    LaunchedEffect(points, sipDurationYears, sipDurationMonths, sipStartDate) {
        if (points.isEmpty()) {
            rollingItems = emptyList()
            isCalculatingRolling = false
            return@LaunchedEffect
        }
        isCalculatingRolling = true
        withContext(Dispatchers.Default) {
            val startMillis = sipStartDate?.time ?: 0L
            val mappedPoints = points.map { Pair(it, parseTimelineDate(it.dateStr)) }
            val validPoints = mappedPoints.filter { it.second >= startMillis }
            
            if (validPoints.isEmpty()) {
                rollingItems = emptyList()
                return@withContext
            }
            
            val maxHistoryDate = validPoints.last().second
            val blockDurationMs = (sipDurationYears * 365L + sipDurationMonths * 30L) * 24L * 60L * 60L * 1000L

            val results = mutableListOf<DailyRollingItem>()

            for (i in validPoints.indices) {
                val startPt = validPoints[i]
                val startDateTs = startPt.second
                val endDateTarget = startDateTs + blockDurationMs

                if (endDateTarget > maxHistoryDate) break

                val blockPoints = validPoints.filter { it.second in startDateTs..endDateTarget }
                if (blockPoints.isEmpty()) continue

                var totalInvested = 0.0
                var totalUnits = 0.0
                val cashflows = mutableListOf<Pair<Long, Double>>()

                for (p in blockPoints) {
                    val amount = 1000.0
                    totalInvested += amount
                    totalUnits += amount / p.first.nav
                    cashflows.add(Pair(p.second, -amount))
                }

                if (totalInvested > 0) {
                    val endNavPt = validPoints.lastOrNull { it.second <= endDateTarget } ?: validPoints.last()
                    val currentValue = totalUnits * endNavPt.first.nav
                    val absReturn = ((currentValue - totalInvested) / totalInvested) * 100.0
                    cashflows.add(Pair(endDateTarget, currentValue))
                    
                    val xirr = calculateXIRR(cashflows) * 100.0
                    
                    results.add(
                        DailyRollingItem(
                            startDateStr = startPt.first.dateStr,
                            endDateStr = endNavPt.first.dateStr,
                            absReturn = absReturn,
                            xirr = xirr,
                            invested = totalInvested,
                            value = currentValue
                        )
                    )
                }
            }
            rollingItems = results
        }
        isCalculatingRolling = false
    }

    val reversedList = remember(rollingItems) { rollingItems.reversed() }

    if (showDatePicker && points.isNotEmpty()) {
        val minDate = parseTimelineDate(points.first().dateStr)
        val maxDate = parseTimelineDate(points.last().dateStr)

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = sipStartDate?.time ?: minDate,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis in minDate..maxDate
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        sipStartDate = Date(it)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
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
                            "Rolling SIP Daily (RD)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            baseSchemeName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Date Picker & Rolling Period Control Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("SIP Start Date", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sipStartDate?.let { dateFormatter.format(it) } ?: "",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Select Start Date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Rolling Period", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isYearsMenuExpanded by remember { mutableStateOf(false) }
                                var isMonthsMenuExpanded by remember { mutableStateOf(false) }

                                Box {
                                    Row(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                            .clickable { isYearsMenuExpanded = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$sipDurationYears Yr${if(sipDurationYears != 1) "s" else ""}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Years", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = isYearsMenuExpanded,
                                        onDismissRequest = { isYearsMenuExpanded = false }
                                    ) {
                                        for (y in 0..20) {
                                            DropdownMenuItem(
                                                text = { Text("$y Year${if(y != 1) "s" else ""}") },
                                                onClick = { 
                                                    sipDurationYears = y
                                                    if (sipDurationYears == 0 && sipDurationMonths == 0) sipDurationMonths = 1
                                                    isYearsMenuExpanded = false 
                                                }
                                            )
                                        }
                                    }
                                }

                                Box {
                                    Row(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                            .clickable { isMonthsMenuExpanded = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$sipDurationMonths Mo${if(sipDurationMonths != 1) "s" else ""}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Months", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = isMonthsMenuExpanded,
                                        onDismissRequest = { isMonthsMenuExpanded = false }
                                    ) {
                                        for (m in 0..11) {
                                            DropdownMenuItem(
                                                text = { Text("$m Month${if(m != 1) "s" else ""}") },
                                                onClick = { 
                                                    sipDurationMonths = m
                                                    if (sipDurationYears == 0 && sipDurationMonths == 0) sipDurationYears = 1
                                                    isMonthsMenuExpanded = false 
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading || isCalculatingRolling) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Calculating rolling windows...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (rollingItems.isEmpty()) {
                item {
                    Text("Not enough historical data for the selected duration.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val validXirrs = rollingItems.map { it.xirr }.filter { !it.isNaN() }
                val avgXirr = if (validXirrs.isNotEmpty()) validXirrs.average() else 0.0
                val maxXirr = if (validXirrs.isNotEmpty()) validXirrs.maxOrNull() ?: 0.0 else 0.0
                val minXirr = if (validXirrs.isNotEmpty()) validXirrs.minOrNull() ?: 0.0 else 0.0
                
                // Statistical Measures
                val variance = if (validXirrs.isNotEmpty()) validXirrs.map { (it - avgXirr).pow(2) }.average() else 0.0
                val stdDev = sqrt(variance)

                val sortedXirrs = validXirrs.sorted()
                val size = sortedXirrs.size
                val p50Val = if (size > 0) sortedXirrs[(size * 0.50).toInt().coerceIn(0, size - 1)] else 0.0
                val p75Val = if (size > 0) sortedXirrs[(size * 0.25).toInt().coerceIn(0, size - 1)] else 0.0
                val p80Val = if (size > 0) sortedXirrs[(size * 0.20).toInt().coerceIn(0, size - 1)] else 0.0
                val p90Val = if (size > 0) sortedXirrs[(size * 0.10).toInt().coerceIn(0, size - 1)] else 0.0
                val p95Val = if (size > 0) sortedXirrs[(size * 0.05).toInt().coerceIn(0, size - 1)] else 0.0

                // Threshold Counts
                val count50 = rollingItems.count { it.xirr >= 50.0 }
                val count75 = rollingItems.count { it.xirr >= 75.0 }
                val count80 = rollingItems.count { it.xirr >= 80.0 }
                val count90 = rollingItems.count { it.xirr >= 90.0 }
                val countAbove90 = rollingItems.count { it.xirr > 90.0 }

                // Top Overview Grid
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("Daily Rolling SIP Overview", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatCard("Avg XIRR", String.format(Locale.US, "%.2f%%", avgXirr), if (avgXirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336), Modifier.weight(1f))
                                StatCard("Std Dev", String.format(Locale.US, "%.2f%%", stdDev), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                StatCard("Median (50%)", String.format(Locale.US, "%.1f%%", p50Val), if (p50Val >= 0) Color(0xFF4CAF50) else Color(0xFFF44336), Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatCard("Windows Count", "${rollingItems.size}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                StatCard("Max XIRR", String.format(Locale.US, "%.1f%%", maxXirr), Color(0xFF4CAF50), Modifier.weight(1f))
                                StatCard("Min XIRR", String.format(Locale.US, "%.1f%%", minXirr), if (minXirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336), Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Interactive Rolling Return Professional Chart
                item {
                    RollingSipTimelineChart(items = rollingItems)
                }

                // Professional Statistical Analysis & Consistency Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Professional Rolling Return Statistical Analysis",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Statistical Summary Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Mean Average:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format(Locale.US, "%.2f%%", avgXirr), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (avgXirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336))
                                }
                                Column {
                                    Text("Volatility (Std Dev):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format(Locale.US, "%.2f%%", stdDev), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column {
                                    Text("Spread (Max - Min):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format(Locale.US, "%.1f%%", maxXirr - minXirr), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Probability & Threshold Distributions (Includes 50% above)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Min XIRR Delivered:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("• 50% of times (Median): ≥ ${String.format(Locale.US, "%.1f%%", p50Val)}", fontSize = 11.sp, color = if (p50Val >= 0) Color(0xFF4CAF50) else Color(0xFFF44336))
                                    Text("• 75% of times: ≥ ${String.format(Locale.US, "%.1f%%", p75Val)}", fontSize = 11.sp, color = if (p75Val >= 0) Color(0xFF4CAF50) else Color(0xFFF44336))
                                    Text("• 80% of times: ≥ ${String.format(Locale.US, "%.1f%%", p80Val)}", fontSize = 11.sp, color = if (p80Val >= 0) Color(0xFF4CAF50) else Color(0xFFF44336))
                                    Text("• 90% of times: ≥ ${String.format(Locale.US, "%.1f%%", p90Val)}", fontSize = 11.sp, color = if (p90Val >= 0) Color(0xFF2196F3) else Color(0xFFF44336))
                                    Text("• >90% of times: ≥ ${String.format(Locale.US, "%.1f%%", p95Val)}", fontSize = 11.sp, color = if (p95Val >= 0) Color(0xFF2196F3) else Color(0xFFF44336))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("High Return Windows:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("• ≥ 50% XIRR: $count50 times (${String.format(Locale.US, "%.1f%%", (count50.toDouble() / size) * 100)})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    Text("• ≥ 75% XIRR: $count75 times (${String.format(Locale.US, "%.1f%%", (count75.toDouble() / size) * 100)})", fontSize = 11.sp)
                                    Text("• ≥ 80% XIRR: $count80 times (${String.format(Locale.US, "%.1f%%", (count80.toDouble() / size) * 100)})", fontSize = 11.sp)
                                    Text("• ≥ 90% XIRR: $count90 times (${String.format(Locale.US, "%.1f%%", (count90.toDouble() / size) * 100)})", fontSize = 11.sp)
                                    Text("• > 90% XIRR: $countAbove90 times (${String.format(Locale.US, "%.1f%%", (countAbove90.toDouble() / size) * 100)})", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Rolling Windows History (${rollingItems.size} Windows)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Table Header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Start -> End", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                            Text("Invested", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                            Text("Abs Ret", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                            Text("XIRR", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }

                // Lazy list of all historical rolling items
                itemsIndexed(
                    items = reversedList,
                    key = { _, item -> item.startDateStr + "_" + item.endDateStr }
                ) { _, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(item.startDateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.endDateStr, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                text = String.format(Locale.US, "₹%.0f", item.invested),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(0.9f),
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val absColor = if (item.absReturn >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            Text(
                                text = String.format(Locale.US, "%.1f%%", item.absReturn),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(0.9f),
                                textAlign = TextAlign.End,
                                color = absColor
                            )
                            val xirrColor = if (item.xirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            Text(
                                text = String.format(Locale.US, "%.1f%%", item.xirr),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.9f),
                                textAlign = TextAlign.End,
                                color = xirrColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RollingSipTimelineChart(
    items: List<DailyRollingItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val xirrs = remember(items) { items.map { it.xirr }.filter { !it.isNaN() } }
    if (xirrs.isEmpty()) return

    val minVal = remember(xirrs) { xirrs.minOrNull() ?: 0.0 }
    val maxVal = remember(xirrs) { xirrs.maxOrNull() ?: 10.0 }
    val avgVal = remember(xirrs) { xirrs.average() }

    val primaryColor = MaterialTheme.colorScheme.primary
    val positiveColor = Color(0xFF4CAF50)
    val negativeColor = Color(0xFFF44336)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Rolling XIRR Return Timeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Touch or drag across the curve to inspect any period",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedIndex != null && selectedIndex!! in items.indices) {
                    val item = items[selectedIndex!!]
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f%% XIRR", item.xirr),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Inspection Tooltip Callout
            if (selectedIndex != null && selectedIndex!! in items.indices) {
                val selItem = items[selectedIndex!!]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selItem.startDateStr} → ${selItem.endDateStr}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("XIRR: ${String.format(Locale.US, "%.1f%%", selItem.xirr)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selItem.xirr >= 0) positiveColor else negativeColor)
                        Text("Abs: ${String.format(Locale.US, "%.1f%%", selItem.absReturn)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .pointerInput(items) {
                        detectTapGestures { offset ->
                            val usableW = size.width - 60f
                            val progress = ((offset.x - 30f) / usableW).coerceIn(0f, 1f)
                            val idx = ((items.size - 1) * progress).roundToInt().coerceIn(0, items.lastIndex)
                            selectedIndex = idx
                        }
                    }
                    .pointerInput(items) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val usableW = size.width - 60f
                                val progress = ((offset.x - 30f) / usableW).coerceIn(0f, 1f)
                                val idx = ((items.size - 1) * progress).roundToInt().coerceIn(0, items.lastIndex)
                                selectedIndex = idx
                            },
                            onHorizontalDrag = { change, _ ->
                                val dx = abs(change.position.x - change.previousPosition.x)
                                val dy = abs(change.position.y - change.previousPosition.y)
                                if (dx > dy) {
                                    change.consume()
                                }
                                val usableW = size.width - 60f
                                val progress = ((change.position.x - 30f) / usableW).coerceIn(0f, 1f)
                                val idx = ((items.size - 1) * progress).roundToInt().coerceIn(0, items.lastIndex)
                                selectedIndex = idx
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val padLeft = 30f
                    val padRight = 30f
                    val padTop = 15f
                    val padBottom = 25f

                    val usableW = w - padLeft - padRight
                    val usableH = h - padTop - padBottom

                    val valRange = if (maxVal > minVal) (maxVal - minVal) * 1.15 else 1.0
                    val yMin = minVal - (valRange * 0.05)
                    val yMax = yMin + valRange

                    fun getY(v: Double) = (padTop + usableH - (((v - yMin) / valRange) * usableH)).toFloat()
                    fun getX(idx: Int) = padLeft + (usableW * (idx.toFloat() / (items.size - 1).coerceAtLeast(1)))

                    // Grid Lines
                    val steps = 3
                    for (s in 0..steps) {
                        val v = yMin + (valRange * s / steps)
                        val y = getY(v)
                        drawLine(
                            color = gridColor,
                            start = Offset(padLeft, y),
                            end = Offset(w - padRight, y),
                            strokeWidth = 1f
                        )
                    }

                    // Zero Baseline
                    if (0.0 in yMin..yMax) {
                        val yZero = getY(0.0)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.4f),
                            start = Offset(padLeft, yZero),
                            end = Offset(w - padRight, yZero),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }

                    // Mean Line
                    val yAvg = getY(avgVal)
                    drawLine(
                        color = primaryColor.copy(alpha = 0.5f),
                        start = Offset(padLeft, yAvg),
                        end = Offset(w - padRight, yAvg),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )

                    // Path Construction
                    val path = Path()
                    val fillPath = Path()

                    items.forEachIndexed { i, item ->
                        val x = getX(i)
                        val y = getY(item.xirr)

                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, padTop + usableH)
                            fillPath.lineTo(x, y)
                        } else {
                            val prevX = getX(i - 1)
                            val prevY = getY(items[i - 1].xirr)
                            val cx1 = prevX + (x - prevX) / 2f
                            val cx2 = cx1
                            path.cubicTo(cx1, prevY, cx2, y, x, y)
                            fillPath.cubicTo(cx1, prevY, cx2, y, x, y)
                        }

                        if (i == items.lastIndex) {
                            fillPath.lineTo(x, padTop + usableH)
                            fillPath.close()
                        }
                    }

                    // Gradient Area Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.30f),
                                primaryColor.copy(alpha = 0.02f)
                            ),
                            startY = padTop,
                            endY = padTop + usableH
                        )
                    )

                    // Main Curve Stroke
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 2.5f)
                    )

                    // Timeline Date Labels
                    if (items.isNotEmpty()) {
                        val startLabel = items.first().startDateStr
                        val endLabel = items.last().endDateStr
                        drawText(
                            textMeasurer = textMeasurer,
                            text = startLabel,
                            topLeft = Offset(padLeft, h - 18f),
                            style = androidx.compose.ui.text.TextStyle(color = Color.Gray, fontSize = 10.sp)
                        )
                        val endLayout = textMeasurer.measure(endLabel)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = endLabel,
                            topLeft = Offset(w - padRight - endLayout.size.width, h - 18f),
                            style = androidx.compose.ui.text.TextStyle(color = Color.Gray, fontSize = 10.sp)
                        )
                    }

                    // Touch Inspection Marker
                    val selIdx = selectedIndex
                    if (selIdx != null && selIdx in items.indices) {
                        val cx = getX(selIdx)
                        val cy = getY(items[selIdx].xirr)

                        drawLine(
                            color = primaryColor.copy(alpha = 0.8f),
                            start = Offset(cx, padTop),
                            end = Offset(cx, padTop + usableH),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        drawLine(
                            color = primaryColor.copy(alpha = 0.8f),
                            start = Offset(padLeft, cy),
                            end = Offset(w - padRight, cy),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )

                        drawCircle(color = primaryColor, radius = 6f, center = Offset(cx, cy))
                        drawCircle(color = Color.White, radius = 3f, center = Offset(cx, cy))
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
