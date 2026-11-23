package com.example

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

private fun parseDateAnyLocal(dateStr: String): Date? {
    val millis = IndexBenchmarkSimulator.parseAnyDateToMillis(dateStr)
    return if (millis != null) Date(millis) else null
}

data class RollingReturnPoint(
    val endDate: Long,
    val dateStr: String,
    val annualizedReturn: Double
)

data class FundRollingData(
    val schemeName: String,
    val points: List<RollingReturnPoint>,
    val color: Color,
    val mean: Double,
    val stdDev: Double,
    val min: Double,
    val median: Double,
    val max: Double,
    val below0: Double,
    val from0To8: Double,
    val from8To12: Double,
    val from12To16: Double,
    val from16To20: Double,
    val above20: Double
)

data class DayOfWeekStat(
    val dayOfWeek: Int,
    val dayName: String,
    val avgReturn: Double,
    val minReturn: Double,
    val maxReturn: Double,
    val count: Int
)

fun calculateDayOfWeekStats(points: List<RollingReturnPoint>): List<DayOfWeekStat> {
    if (points.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    val dayMap = mutableMapOf<Int, MutableList<Double>>()
    
    for (pt in points) {
        cal.timeInMillis = pt.endDate
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        dayMap.getOrPut(dow) { mutableListOf() }.add(pt.annualizedReturn)
    }
    
    val dayNames = mapOf(
        Calendar.MONDAY to "Monday",
        Calendar.TUESDAY to "Tuesday",
        Calendar.WEDNESDAY to "Wednesday",
        Calendar.THURSDAY to "Thursday",
        Calendar.FRIDAY to "Friday",
        Calendar.SATURDAY to "Saturday",
        Calendar.SUNDAY to "Sunday"
    )
    
    val daysOrder = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )
    
    return daysOrder.mapNotNull { dow ->
        val returns = dayMap[dow]
        if (returns != null && returns.isNotEmpty()) {
            DayOfWeekStat(
                dayOfWeek = dow,
                dayName = dayNames[dow] ?: "Day $dow",
                avgReturn = returns.average(),
                minReturn = returns.minOrNull() ?: 0.0,
                maxReturn = returns.maxOrNull() ?: 0.0,
                count = returns.size
            )
        } else null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollingReturnVisualizerScreen(
    baseSchemeName: String,
    allSchemes: List<String>,
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var selectedSchemes by remember { mutableStateOf(listOf(baseSchemeName)) }
    var isLoading by remember { mutableStateOf(false) }
    var fundsData by remember { mutableStateOf<List<FundRollingData>>(emptyList()) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var showFundSelectorDialog by remember { mutableStateOf(false) }
    
    var periodNumber by remember { mutableStateOf(1) }
    var periodUnit by remember { mutableStateOf("Months") } // "Weeks", "Months", "Years"
    
    val benchmarkIndices = remember {
        listOf(
            "Nifty 50 TRI",
            "Nifty Midcap 150 TRI",
            "Nifty Smallcap 250 TRI",
            "Nifty 500 TRI"
        )
    }
    val quantBenchmarkFunds = remember {
        IndexBenchmarkSimulator.benchmarkFunds.keys.toList()
    }
    
    val colors = listOf(
        Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
        Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFFF8A65), Color(0xFFA1887F),
        Color(0xFF26A69A), Color(0xFF7E57C2), Color(0xFFEC407A), Color(0xFF29B6F6),
        Color(0xFFFF7043), Color(0xFF9CCC65), Color(0xFFAB47BC), Color(0xFF26C6DA)
    )

    LaunchedEffect(selectedSchemes, periodNumber, periodUnit, startDateMillis) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val newFundsData = mutableListOf<FundRollingData>()
                
                val days = when (periodUnit) {
                    "Weeks" -> periodNumber * 7L
                    "Months" -> periodNumber * 30L
                    else -> periodNumber * 365L
                }
                
                selectedSchemes.forEachIndexed { index, scheme ->
                    val dbRecords = db.fundDao().getHistoricalNavs(scheme).map { NavRecord(it.dateStr, it.navValue) }
                    val records: List<NavRecord> = IndexBenchmarkSimulator.getFullHistoricalNavs(context, scheme, dbRecords).ifEmpty {
                        val cached = NavCache.getRecords(scheme)
                        if (cached.isNotEmpty()) cached else dbRecords
                    }
                    if (records.isNotEmpty()) {
                        NavCache.addAll(scheme, records)
                    }
                    val sortedRecords = records.sortedBy { parseDateAnyLocal(it.dateStr)?.time ?: 0 }
                    val mappedPoints = sortedRecords.map { Pair(it, parseDateAnyLocal(it.dateStr)?.time ?: 0L) }
                    
                    val returnsList = mutableListOf<RollingReturnPoint>()
                    val rawReturns = mutableListOf<Double>()
                    
                    // Use a sliding window / two pointers for O(N) performance
                    var startIndex = 0
                    for (endIndex in mappedPoints.indices) {
                        val endPoint = mappedPoints[endIndex]
                        val endPointTime = endPoint.second
                        if (endPointTime <= 0L) continue
                        
                        val targetTime = endPointTime - days * 24L * 60 * 60 * 1000L
                        
                        // Advance startIndex until it's just before or exactly at targetTime
                        while (startIndex < endIndex && mappedPoints[startIndex + 1].second <= targetTime) {
                            startIndex++
                        }
                        
                        val startPoint = mappedPoints[startIndex]
                        if (startPoint.second <= targetTime && endPointTime > startPoint.second) {
                            // If startDateMillis is specified, ensure the rolling period end date is on or after startDateMillis
                            if (startDateMillis != null && endPointTime < startDateMillis!!) continue

                            val actualDays = (endPointTime - startPoint.second) / (1000L * 60 * 60 * 24)
                            val maxAllowedGap = maxOf(15L, days / 10)
                            if (Math.abs(actualDays - days) <= maxAllowedGap) {
                                val ret = if (days < 360L) {
                                    (endPoint.first.nav / startPoint.first.nav - 1.0) * 100.0
                                } else {
                                    (Math.pow(endPoint.first.nav / startPoint.first.nav, 365.25 / actualDays) - 1.0) * 100.0
                                }
                                if (!ret.isNaN() && !ret.isInfinite()) {
                                    returnsList.add(RollingReturnPoint(endPointTime, endPoint.first.dateStr, ret))
                                    rawReturns.add(ret)
                                }
                            }
                        }
                    }
                    
                    if (rawReturns.isNotEmpty()) {
                        val sortedRaw = rawReturns.sorted()
                        val mean = sortedRaw.average()
                        val stdDev = Math.sqrt(sortedRaw.map { Math.pow(it - mean, 2.0) }.average())
                        val min = sortedRaw.first()
                        val max = sortedRaw.last()
                        val median = if (sortedRaw.size % 2 == 0) {
                            (sortedRaw[sortedRaw.size / 2 - 1] + sortedRaw[sortedRaw.size / 2]) / 2.0
                        } else {
                            sortedRaw[sortedRaw.size / 2]
                        }
                        val total = sortedRaw.size.toDouble()
                        
                        newFundsData.add(
                            FundRollingData(
                                schemeName = scheme,
                                points = returnsList,
                                color = colors[index % colors.size],
                                mean = mean,
                                stdDev = stdDev,
                                min = min,
                                median = median,
                                max = max,
                                below0 = sortedRaw.count { it < 0 } / total * 100.0,
                                from0To8 = sortedRaw.count { it in 0.0..8.0 } / total * 100.0,
                                from8To12 = sortedRaw.count { it > 8.0 && it <= 12.0 } / total * 100.0,
                                from12To16 = sortedRaw.count { it > 12.0 && it <= 16.0 } / total * 100.0,
                                from16To20 = sortedRaw.count { it > 16.0 && it <= 20.0 } / total * 100.0,
                                above20 = sortedRaw.count { it > 20.0 } / total * 100.0
                            )
                        )
                    }
                }
                fundsData = newFundsData
            } catch (e: Exception) {
                android.util.Log.e("RollingReturnVisualizer", "Error computing rolling returns", e)
            }
        }
        isLoading = false
    }

    var selectedIndexA by remember { mutableStateOf<Int?>(null) }
    
    val allDates = remember(fundsData) {
        fundsData.flatMap { it.points }.map { it.endDate }.distinct().sorted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rolling Returns Visualizer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(baseSchemeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Start Date")
                    }
                    IconButton(onClick = { showFundSelectorDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Funds & Benchmarks")
                    }
                    if (selectedSchemes.size > 1) {
                        IconButton(onClick = { selectedSchemes = listOf(baseSchemeName) }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Remove All Added Funds", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Graph Area
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxWidth()
            ) {
                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Period: ", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    
                    // Period Number
                    var numExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { numExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(periodNumber.toString(), fontSize = 14.sp)
                                Icon(Icons.Default.KeyboardArrowDown, "", modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(expanded = numExpanded, onDismissRequest = { numExpanded = false }) {
                            (1..30).forEach { num ->
                                DropdownMenuItem(
                                    text = { Text(num.toString()) },
                                    onClick = { periodNumber = num; numExpanded = false }
                                )
                            }
                        }
                    }
                    
                    // Period Unit
                    var unitExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { unitExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(periodUnit, fontSize = 14.sp)
                                Icon(Icons.Default.KeyboardArrowDown, "", modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                            listOf("Weeks", "Months", "Years").forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit) },
                                    onClick = { periodUnit = unit; unitExpanded = false }
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.width(8.dp))
                    
                    Surface(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Start Date", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(4.dp))
                            if (startDateMillis != null) {
                                val df = java.text.SimpleDateFormat("dd-MMM-yy", Locale.US)
                                Text(df.format(java.util.Date(startDateMillis!!)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            } else {
                                Text("Start Date", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    if (startDateMillis != null) {
                        IconButton(onClick = { startDateMillis = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Date", modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Surface(
                        onClick = { showFundSelectorDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add Funds & Benchmarks", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Funds / Benchmarks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }

                    if (selectedSchemes.size > 1) {
                        Surface(
                            onClick = { selectedSchemes = listOf(baseSchemeName) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove All", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(4.dp))
                                Text("Remove All (${selectedSchemes.size - 1})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    
                    if (selectedSchemes.size > 1) {
                        Text(
                            text = "Comparing ${fundsData.size} of ${selectedSchemes.size} Funds",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp).align(Alignment.CenterVertically)
                        )
                    }
                    
                    Spacer(Modifier.width(8.dp))
                    
                    fundsData.forEach { fd ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = fd.color.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, fd.color.copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Box(modifier = Modifier.size(8.dp).background(fd.color, RoundedCornerShape(4.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(fd.schemeName.take(15) + "...", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fd.color)
                                if (selectedSchemes.size > 1) {
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(onClick = { selectedSchemes = selectedSchemes.filter { it != fd.schemeName } }, modifier = Modifier.size(16.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp), tint = fd.color)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (fundsData.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No sufficient data for selected period.")
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        RollingGraphView(
                            fundsData = fundsData,
                            allDates = allDates,
                            isDarkTheme = isDarkTheme,
                            selectedIndex = selectedIndexA,
                            onIndexChange = { selectedIndexA = it }
                        )
                    }
                }
            }
            
            // Bottom Stats Area
            Column(
                modifier = Modifier
                    .weight(if (periodUnit == "Weeks" && periodNumber == 1) 0.45f else 0.35f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                val returnType = if (periodUnit == "Weeks" || (periodUnit == "Months" && periodNumber < 12)) "Absolute Returns" else "Annualized Returns (CAGR)"
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Stats Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(returnType, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selectedIndexA != null && allDates.isNotEmpty() && selectedIndexA!! in allDates.indices) {
                        val dateLong = allDates[selectedIndexA!!]
                        val dfOut = java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                        Text(dfOut.format(Date(dateLong)), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(
                        count = fundsData.size,
                        key = { fundsData[it].schemeName },
                        contentType = { "fund_rolling_stats" }
                    ) { i ->
                        val fd = fundsData[i]
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(fd.color, RoundedCornerShape(4.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(fd.schemeName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(4.dp))
                            
                            val statsStr = """
                                Mean: ${String.format(Locale.US, "%.2f", fd.mean)}%
                                Median: ${String.format(Locale.US, "%.2f", fd.median)}%
                                StdDev: ${String.format(Locale.US, "%.2f", fd.stdDev)}
                                Min: ${String.format(Locale.US, "%.2f", fd.min)}%
                                Max: ${String.format(Locale.US, "%.2f", fd.max)}%
                                
                                % Times Below 0: ${String.format(Locale.US, "%.1f", fd.below0)}%
                                0-8: ${String.format(Locale.US, "%.1f", fd.from0To8)}%
                                8-12: ${String.format(Locale.US, "%.1f", fd.from8To12)}%
                                12-16: ${String.format(Locale.US, "%.1f", fd.from12To16)}%
                                16-20: ${String.format(Locale.US, "%.1f", fd.from16To20)}%
                                20+: ${String.format(Locale.US, "%.1f", fd.above20)}%
                            """.trimIndent()
                            
                            Text(statsStr, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (periodUnit == "Weeks" && periodNumber == 1) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                "📅 Weekly SIP Pattern Analysis (1-Week Mode)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "1-week rolling returns grouped by day of the week to identify optimal Weekly SIP execution day.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(
                            count = fundsData.size,
                            key = { "weekly_pattern_" + fundsData[it].schemeName },
                            contentType = { "weekly_pattern" }
                        ) { i ->
                            val fd = fundsData[i]
                            val dayStats = remember(fd.points) { calculateDayOfWeekStats(fd.points) }
                            val lowestAvgDay = dayStats.minByOrNull { it.avgReturn }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, fd.color.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).background(fd.color, RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.width(6.dp))
                                        Text(fd.schemeName, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    if (lowestAvgDay != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "Lowest Return Day: ",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        lowestAvgDay.dayName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        "(${String.format(Locale.US, "%.2f", lowestAvgDay.avgReturn)}% avg)",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 11.sp,
                                                        color = if (lowestAvgDay.avgReturn >= 0) Color(0xFF4CAF50) else Color(0xFFEF4444)
                                                    )
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "💡 Weekly SIP Insight: Buying on ${lowestAvgDay.dayName} historically gave the lowest average 1-week return, meaning lower NAV entry prices for maximum unit accumulation.",
                                                    fontSize = 10.sp,
                                                    lineHeight = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Table Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Day", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                                        Text("Avg Return", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                        Text("Min Return", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                        Text("Max Return", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                        Text("Weeks", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(0.8f))
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    // Table Rows
                                    dayStats.forEach { stat ->
                                        val isLowest = stat.dayOfWeek == lowestAvgDay?.dayOfWeek
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isLowest) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else Color.Transparent,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(vertical = 4.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isLowest) "⭐ ${stat.dayName}" else stat.dayName,
                                                fontWeight = if (isLowest) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 10.sp,
                                                color = if (isLowest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1.2f)
                                            )
                                            Text(
                                                text = "${String.format(Locale.US, "%.2f", stat.avgReturn)}%",
                                                fontWeight = if (isLowest) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 10.sp,
                                                color = if (stat.avgReturn >= 0) Color(0xFF4CAF50) else Color(0xFFEF4444),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${String.format(Locale.US, "%.2f", stat.minReturn)}%",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${String.format(Locale.US, "%.2f", stat.maxReturn)}%",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${stat.count}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(0.8f)
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
        
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = startDateMillis ?: run {
                    val earliestPoint = fundsData.flatMap { it.points }.minOfOrNull { it.endDate }
                    earliestPoint ?: System.currentTimeMillis()
                }
            )
            
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        startDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            startDateMillis = null
                            showDatePicker = false
                        }) { Text("Reset to Inception") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    showModeToggle = true,
                    title = {
                        Text("Select Rolling Start Date", modifier = Modifier.padding(start = 24.dp, top = 16.dp), fontWeight = FontWeight.Bold)
                    },
                    headline = {
                        Text("Choose start date from 2013 onwards", modifier = Modifier.padding(start = 24.dp, bottom = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (showFundSelectorDialog) {
            AlertDialog(
                onDismissRequest = { showFundSelectorDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Select Funds & Benchmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${selectedSchemes.size} selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showFundSelectorDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                },
                text = {
                    var searchQuery by remember { mutableStateOf("") }
                    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: Indices, 2: Portfolio, 3: Benchmarks

                    val allAvailable = remember(allSchemes, benchmarkIndices, quantBenchmarkFunds) {
                        val list = mutableListOf<Triple<String, String, String>>() // Name, Type, Searchable
                        benchmarkIndices.forEach { list.add(Triple(it, "INDEX", "Nifty Index Benchmark TRI")) }
                        allSchemes.forEach { list.add(Triple(it, "PORTFOLIO", "Portfolio Fund QSIF")) }
                        quantBenchmarkFunds.forEach { list.add(Triple(it, "BENCHMARK", "Quant Mutual Fund Direct")) }
                        list
                    }

                    val filteredList = remember(searchQuery, selectedTab, allAvailable, selectedSchemes) {
                        allAvailable.filter { (name, type, extra) ->
                            val matchesTab = when (selectedTab) {
                                1 -> type == "INDEX"
                                2 -> type == "BENCHMARK"
                                3 -> type == "PORTFOLIO"
                                4 -> selectedSchemes.contains(name)
                                else -> true
                            }
                            val matchesQuery = searchQuery.isBlank() || 
                                name.contains(searchQuery, ignoreCase = true) ||
                                extra.contains(searchQuery, ignoreCase = true)
                            matchesTab && matchesQuery
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Quick Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val newSet = (selectedSchemes + benchmarkIndices).distinct()
                                    selectedSchemes = newSet
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("+ Indices", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val newSet = (selectedSchemes + quantBenchmarkFunds).distinct()
                                    selectedSchemes = newSet
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("+ Benchmarks", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val newSet = (selectedSchemes + allSchemes).distinct()
                                    selectedSchemes = newSet
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("+ Portfolio", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedSchemes = listOf(baseSchemeName)
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Search field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search funds or indices...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            textStyle = TextStyle(fontSize = 13.sp),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        )

                        // Category Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val tabs = listOf("All", "Indices (${benchmarkIndices.size})", "Benchmarks (${quantBenchmarkFunds.size})", "Portfolio (${allSchemes.size})", "Selected (${selectedSchemes.size})")
                            tabs.forEachIndexed { idx, label ->
                                FilterChip(
                                    selected = selectedTab == idx,
                                    onClick = { selectedTab = idx },
                                    label = { Text(label, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }

                        // Items list
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredList) { (schemeName, type, _) ->
                                val isSelected = selectedSchemes.contains(schemeName)
                                val (badgeColor, badgeTextColor) = when (type) {
                                    "INDEX" -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                    "PORTFOLIO" -> Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    else -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                }

                                Surface(
                                    onClick = {
                                        if (isSelected) {
                                            if (selectedSchemes.size > 1) {
                                                selectedSchemes = selectedSchemes.filter { it != schemeName }
                                            }
                                        } else {
                                            selectedSchemes = selectedSchemes + schemeName
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    selectedSchemes = (selectedSchemes + schemeName).distinct()
                                                } else {
                                                    if (selectedSchemes.size > 1) {
                                                        selectedSchemes = selectedSchemes.filter { it != schemeName }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = schemeName,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(badgeColor, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = type,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeTextColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showFundSelectorDialog = false },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Apply (${selectedSchemes.size} Selected)")
                    }
                }
            )
        }
    }
}

@Composable
fun RollingGraphView(
    fundsData: List<FundRollingData>,
    allDates: List<Long>,
    isDarkTheme: Boolean,
    selectedIndex: Int?,
    onIndexChange: (Int?) -> Unit
) {
    if (allDates.isEmpty()) return
    
    val minVal = fundsData.flatMap { it.points }.minOfOrNull { it.annualizedReturn }?.coerceAtMost(0.0) ?: 0.0
    val maxVal = fundsData.flatMap { it.points }.maxOfOrNull { it.annualizedReturn }?.coerceAtLeast(25.0) ?: 25.0
    val range = maxVal - minVal
    
    val zoneColors = listOf(
        Pair(0.0..8.0, Color(0xFFE57373).copy(alpha = 0.1f)),
        Pair(8.0..12.0, Color(0xFFFFB74D).copy(alpha = 0.1f)),
        Pair(12.0..16.0, Color(0xFFFFF176).copy(alpha = 0.1f)),
        Pair(16.0..20.0, Color(0xFF81C784).copy(alpha = 0.1f)),
        Pair(20.0..1000.0, Color(0xFF4DB6AC).copy(alpha = 0.1f)),
        Pair(-1000.0..0.0, Color(0xFF9E9E9E).copy(alpha = 0.1f))
    )

    val textMeasurer = rememberTextMeasurer()
    val labelColor = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
    val textStyle = TextStyle(color = labelColor, fontSize = 10.sp)

    Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 24.dp, start = 8.dp, end = 32.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onIndexChange(null) },
                        onDragCancel = { onIndexChange(null) },
                        onHorizontalDrag = { change, _ ->
                            if (allDates.isNotEmpty()) {
                                val x = change.position.x
                                val fraction = (x / size.width).coerceIn(0f, 1f)
                                val index = (fraction * (allDates.size - 1)).toInt()
                                onIndexChange(index)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (allDates.isNotEmpty()) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val index = (fraction * (allDates.size - 1)).toInt()
                                onIndexChange(index)
                                tryAwaitRelease()
                                onIndexChange(null)
                            }
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            
            // Draw Zones
            zoneColors.forEach { (valRange, color) ->
                val startVal = valRange.start.coerceIn(minVal, maxVal)
                val endVal = valRange.endInclusive.coerceIn(minVal, maxVal)
                
                if (startVal < endVal) {
                    val bottomY = height - ((startVal - minVal) / range * height).toFloat()
                    val topY = height - ((endVal - minVal) / range * height).toFloat()
                    val drawHeight = bottomY - topY
                    if (drawHeight > 0) {
                        drawRect(
                            color = color,
                            topLeft = Offset(0f, topY),
                            size = Size(width, drawHeight)
                        )
                    }
                }
            }

            // Zero line
            val zeroY = height - ((0.0 - minVal) / range * height).toFloat()
            if (zeroY in 0f..height) {
                drawLine(
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f),
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 2f
                )
            }
            
            // Draw grid lines and Y-axis labels
            val yAxisLabels = listOf(0.0, 8.0, 12.0, 16.0, 20.0)
            for (i in yAxisLabels) {
                val y = height - ((i - minVal) / range * height).toFloat()
                if (y in 0f..height) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    
                    // Draw Y-axis text at the right edge
                    val textLayout = textMeasurer.measure(
                        text = "${i.toInt()}%",
                        style = textStyle
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(width + 4f, y - 6f)
                    )
                }
            }
            
            // Draw X-axis labels (Years)
            val cal = Calendar.getInstance()
            val yearMap = mutableMapOf<Int, Int>() // Year -> Index
            allDates.forEachIndexed { index, time ->
                cal.timeInMillis = time
                val year = cal.get(Calendar.YEAR)
                if (!yearMap.containsKey(year)) {
                    yearMap[year] = index
                }
            }
            
            yearMap.forEach { (year, index) ->
                val x = (index.toFloat() / maxOf(1, allDates.size - 1)) * width
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
                val textLayout = textMeasurer.measure(
                    text = year.toString(),
                    style = textStyle
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(x + 2f, height + 4f)
                )
            }

            clipRect {
                fundsData.forEach { fd ->
                    val path = Path()
                    var isFirst = true
                    
                    fd.points.forEach { pt ->
                        val dateIdx = allDates.binarySearch(pt.endDate)
                        if (dateIdx >= 0) {
                            val x = (dateIdx.toFloat() / maxOf(1, allDates.size - 1)) * width
                            val y = height - ((pt.annualizedReturn - minVal) / range * height).toFloat()
                            
                            if (isFirst) {
                                path.moveTo(x, y)
                                isFirst = false
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                    }
                    
                    if (!isFirst) {
                        drawPath(
                            path = path,
                            color = fd.color,
                            style = Stroke(width = 3f, join = StrokeJoin.Round, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            
            if (selectedIndex != null && selectedIndex in allDates.indices) {
                val x = (selectedIndex.toFloat() / maxOf(1, allDates.size - 1)) * width
                drawLine(
                    color = Color.Gray,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                
                // Draw circles for each fund
                fundsData.forEach { fd ->
                    val pt = fd.points.find { it.endDate == allDates[selectedIndex] }
                    if (pt != null) {
                        val y = height - ((pt.annualizedReturn - minVal) / range * height).toFloat()
                        drawCircle(color = fd.color, radius = 6f, center = Offset(x, y))
                        drawCircle(color = Color.White, radius = 3f, center = Offset(x, y))
                        
                        // Draw Tooltip text
                        val txtStr = String.format(Locale.US, "%.1f%%", pt.annualizedReturn)
                        val txt = textMeasurer.measure(
                            text = txtStr,
                            style = TextStyle(color = if (isDarkTheme) Color.White else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                        val txtBgWidth = txt.size.width + 12f
                        val txtBgHeight = txt.size.height + 8f
                        val bgRectTopLeft = Offset(x + 10f, y - txtBgHeight / 2f)
                        
                        // Prevent drawing outside right edge
                        val finalTopLeft = if (bgRectTopLeft.x + txtBgWidth > width) {
                            Offset(x - 10f - txtBgWidth, y - txtBgHeight / 2f)
                        } else {
                            bgRectTopLeft
                        }
                        
                        drawRoundRect(
                            color = fd.color.copy(alpha = 0.8f),
                            topLeft = finalTopLeft,
                            size = Size(txtBgWidth, txtBgHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                        drawText(
                            textLayoutResult = txt,
                            topLeft = Offset(finalTopLeft.x + 6f, finalTopLeft.y + 4f)
                        )
                    }
                }
            }
        }
    }
}
