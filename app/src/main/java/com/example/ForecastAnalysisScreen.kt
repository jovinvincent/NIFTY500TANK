package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

data class ForecastGraphPoint(
    val dateLong: Long,
    val dateStr: String,
    val consistencyScore: Double,
    val expectedXirr: Double,
    val smoothedConsistencyScore: Double = consistencyScore,
    val smoothedExpectedXirr: Double = expectedXirr
)

data class ForecastFundLine(
    val name: String,
    val color: Color,
    val points: List<ForecastGraphPoint>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastAnalysisScreen(
    baseSchemeName: String,
    allSchemes: List<String>,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onSmartRecommendationClick: () -> Unit = {}
) {
    var selectedSchemes by remember { mutableStateOf(listOf(baseSchemeName)) }
    var metricType by remember { mutableStateOf("Expected XIRR") } // "Expected XIRR" or "Consistency Score"
    
    var isLoading by remember { mutableStateOf(false) }
    var fundLines by remember { mutableStateOf<List<ForecastFundLine>>(emptyList()) }
    
    val colors = listOf(
        Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
        Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFFF8A65), Color(0xFFA1887F)
    )
    
    val scope = rememberCoroutineScope()
    
    val context = LocalContext.current
    fun reloadData(schemes: List<String>) {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.Default) {
                val newFundLines = mutableListOf<ForecastFundLine>()
                var colorIdx = 0
                
                val firstScheme = schemes.firstOrNull() ?: ""
                var baseRecords = NavCache.getRecords(firstScheme)
                if (baseRecords.isEmpty() && firstScheme.isNotEmpty()) {
                    val db = AppDatabase.getDatabase(context)
                    val dbRecords = db.fundDao().getHistoricalNavs(firstScheme)
                    if (dbRecords.isNotEmpty()) {
                        baseRecords = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                        NavCache.addAll(firstScheme, baseRecords)
                    }
                }
                if (baseRecords.isNotEmpty()) {
                    val baseMapped = baseRecords.mapNotNull { 
                        try {
                            val time = parseTimelineDate(it.dateStr)
                            if (time > 0L) Pair(it, time) else null
                        } catch (e: Exception) { null }
                    }.distinctBy { it.second }.sortedBy { it.second }
                    
                    if (baseMapped.isNotEmpty()) {
                        val minD = baseMapped.first().second
                        val maxD = baseMapped.last().second
                        
                        val totalDays = ((maxD - minD) / (24L * 60 * 60 * 1000L)).coerceAtLeast(1)
                        // Target around 120 points (matching ForecastAndConsistencyComponent exactly) to guarantee
                        // 100% mathematical consistency while completely eliminating load delay and lag.
                        val stepDays = (totalDays / 120).coerceAtLeast(1)
                        val stepMs = stepDays * 24L * 60 * 60 * 1000L
                        
                        val sharedDates = mutableListOf<Long>()
                        var t = minD
                        while (t <= maxD) {
                            sharedDates.add(t)
                            t += stepMs
                        }
                        if (sharedDates.isEmpty() || sharedDates.last() != maxD) {
                            sharedDates.add(maxD)
                        }
                        
                        for (scheme in schemes) {
                            val records = NavCache.getRecords(scheme)
                            if (records.isEmpty()) continue
                            
                            val mapped = records.mapNotNull { rec -> 
                                try {
                                    val time = parseTimelineDate(rec.dateStr)
                                    if (time > 0L) Pair(rec, time) else null
                                } catch (e: Exception) { null }
                            }.distinctBy { it.second }.sortedBy { it.second }
                            
                            if (mapped.isEmpty()) continue

                            // Use the full unsampled timeline to guarantee 100% mathematical consistency
                            // with the main screen's forecast metrics.
                            val mappedFiltered = mapped
                            
                            // No need for computeTrendAndScore here, as ForecastGraphPoint only uses the raw NAVs
                            // through calculateConsistencyAndForecastMapped.
                            
                            val points = mutableListOf<ForecastGraphPoint>()
                            var k = 0
                            val historyList = mutableListOf<Pair<TimelineChartPoint, Long>>()
                            for (evalTime in sharedDates) {
                                yield()
                                // Rolling linear optimization: accumulate records up to evalTime
                                while (k < mappedFiltered.size && mappedFiltered[k].second <= evalTime) {
                                    val rec = mappedFiltered[k].first
                                    val time = mappedFiltered[k].second
                                    val pt = TimelineChartPoint(rec.dateStr, rec.nav, null, null, 0.0, 0.0)
                                    historyList.add(Pair(pt, time))
                                    k++
                                }
                                if (historyList.size >= 10) {
                                    val (score, xirr) = calculateConsistencyAndForecastMapped(historyList)
                                    val evalPoint = historyList.last().first
                                    points.add(ForecastGraphPoint(evalTime, evalPoint.dateStr, score, xirr))
                                }
                            }
                            
                            // Adaptive Exponential Moving Average smoothing based on history size
                            val historySize = points.size
                            val period = when {
                                historySize >= 200 -> 200
                                historySize >= 100 -> 100
                                historySize >= 50 -> 50
                                historySize >= 20 -> 20
                                historySize >= 15 -> 15
                                historySize >= 7 -> 7
                                historySize >= 5 -> historySize - 1
                                else -> 1
                            }
                            val alpha = 2.0 / (period.toDouble() + 1.0)
                            val smoothedPoints = mutableListOf<ForecastGraphPoint>()
                            var emaScore = 0.0
                            var emaXirr = 0.0
                            for (i in points.indices) {
                                val pt = points[i]
                                if (i == 0) {
                                    emaScore = pt.consistencyScore
                                    emaXirr = pt.expectedXirr
                                } else {
                                    emaScore = alpha * pt.consistencyScore + (1.0 - alpha) * emaScore
                                    emaXirr = alpha * pt.expectedXirr + (1.0 - alpha) * emaXirr
                                }
                                smoothedPoints.add(
                                    ForecastGraphPoint(
                                        dateLong = pt.dateLong,
                                        dateStr = pt.dateStr,
                                        consistencyScore = pt.consistencyScore,
                                        expectedXirr = pt.expectedXirr,
                                        smoothedConsistencyScore = emaScore,
                                        smoothedExpectedXirr = emaXirr
                                    )
                                )
                            }
                            
                            newFundLines.add(ForecastFundLine(scheme, colors[colorIdx % colors.size], smoothedPoints))
                            colorIdx++
                        }
                    }
                }
                fundLines = newFundLines
            }
            isLoading = false
        }
    }
    
    LaunchedEffect(selectedSchemes) {
        if (!isLoading) {
            reloadData(selectedSchemes)
        }
    }
    
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    val allDates = remember(fundLines) {
        fundLines.flatMap { it.points }.map { it.dateLong }.distinct().sorted()
    }
    
    LaunchedEffect(allDates) {
        if (allDates.isNotEmpty()) {
            if (selectedIndex == null || selectedIndex!! >= allDates.size) {
                selectedIndex = allDates.size - 1
            }
        } else {
            selectedIndex = null
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Forecast Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(baseSchemeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSmartRecommendationClick) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF00E676).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "R",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Compare Funds")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Compare All") },
                                onClick = {
                                    selectedSchemes = allSchemes
                                    expanded = false
                                }
                            )
                            HorizontalDivider()
                            allSchemes.forEach { scheme ->
                                DropdownMenuItem(
                                    text = { Text(scheme) },
                                    onClick = {
                                        if (!selectedSchemes.contains(scheme)) {
                                            selectedSchemes = selectedSchemes + scheme
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (selectedSchemes.size > 1) {
                        TextButton(onClick = { selectedSchemes = listOf(baseSchemeName) }) {
                            Text("Clear")
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                listOf("Expected XIRR", "Consistency Score").forEach { type ->
                    FilterChip(
                        selected = metricType == type,
                        onClick = { metricType = type },
                        label = { Text(type, fontSize = 12.sp) },
                        modifier = Modifier.padding(horizontal = 4.dp).height(28.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (fundLines.isEmpty()) {
                        Text("No data available", modifier = Modifier.align(Alignment.Center))
                    } else {
                        ForecastGraphTradingView(
                            fundLines = fundLines,
                            allDates = allDates,
                            isDarkTheme = isDarkTheme,
                            selectedIndex = selectedIndex,
                            onIndexChange = { selectedIndex = it },
                            metricType = metricType
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                if (selectedIndex != null && allDates.isNotEmpty() && selectedIndex!! in allDates.indices) {
                    val dateLong = allDates[selectedIndex!!]
                    val dfOut = java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                    val dateStr = dfOut.format(Date(dateLong))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Selected Date", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(metricType, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            count = fundLines.size,
                            key = { fundLines[it].name },
                            contentType = { "forecast_fund_item" }
                        ) { i ->
                            val fd = fundLines[i]
                            val pt = fd.points.find { it.dateLong == dateLong }
                            
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).background(fd.color, RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.width(8.dp))
                                        Text(fd.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    if (pt != null) {
                                        val actualXirr = pt.expectedXirr
                                        val trendXirr = pt.smoothedExpectedXirr
                                        val actualScore = pt.consistencyScore
                                        val trendScore = pt.smoothedConsistencyScore
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "SMOOTHED TREND",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "DAILY ACTUAL",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                        
                                        Spacer(Modifier.height(6.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("XIRR: ", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = String.format(Locale.US, "%.2f%%", trendXirr),
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (trendXirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                                                )
                                            }
                                            Text(
                                                text = String.format(Locale.US, "%.2f%%", actualXirr),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = (if (actualXirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)).copy(alpha = 0.7f)
                                            )
                                        }
                                        
                                        Spacer(Modifier.height(6.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Score: ", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = String.format(Locale.US, "%.2f", trendScore),
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = getConsistencyColor(trendScore)
                                                )
                                            }
                                            Text(
                                                text = String.format(Locale.US, "%.2f", actualScore),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = getConsistencyColor(actualScore).copy(alpha = 0.7f)
                                            )
                                        }
                                    } else {
                                        Text("No Data Available", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Touch & drag on the chart to view details.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ForecastGraphTradingView(
    fundLines: List<ForecastFundLine>,
    allDates: List<Long>,
    isDarkTheme: Boolean,
    selectedIndex: Int?,
    onIndexChange: (Int?) -> Unit,
    metricType: String
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
        fontSize = 10.sp
    )
    
    val minDate = allDates.firstOrNull() ?: 0L
    val maxDate = allDates.lastOrNull() ?: 0L
    val dateRange = max(1L, maxDate - minDate)
    
    val threeMonthsMs = 90L * 24 * 60 * 60 * 1000
    val defaultScaleX = if (dateRange > threeMonthsMs) {
        dateRange.toFloat() / threeMonthsMs
    } else {
        1f
    }
    
    var scaleX by remember(fundLines, metricType) { mutableStateOf(defaultScaleX) }
    var offsetX by remember(fundLines, metricType) { mutableStateOf(Float.NEGATIVE_INFINITY) } 
    
    val density = androidx.compose.ui.platform.LocalDensity.current
    val yAxisWidth = with(density) { 60.dp.toPx() }
    val xAxisHeight = with(density) { 28.dp.toPx() }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Controls & Legend Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 2.dp)
                            .background(if (isDarkTheme) Color.LightGray.copy(alpha = 0.5f) else Color.DarkGray.copy(alpha = 0.5f))
                    )
                    Text(
                        text = "Daily Actual",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 3.5.dp)
                            .background(if (isDarkTheme) Color.White else Color.Black)
                    )
                    Text(
                        text = "Smoothed Trend",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                }
            }
            
            // Timeframe buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (scaleX > 1f || offsetX < 0f) {
                    TextButton(
                        onClick = {
                            scaleX = 1f
                            offsetX = 0f
                            if (allDates.isNotEmpty()) {
                                onIndexChange(allDates.size - 1)
                            } else {
                                onIndexChange(null)
                            }
                        },
                        modifier = Modifier.height(26.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Fit All", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Button(
                    onClick = {
                        scaleX = defaultScaleX
                        offsetX = Float.NEGATIVE_INFINITY
                        if (allDates.isNotEmpty()) {
                            onIndexChange(allDates.size - 1)
                        } else {
                            onIndexChange(null)
                        }
                    },
                    modifier = Modifier.height(26.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Last 3M", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        
        // Canvas Graph Box
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(allDates, scaleX, offsetX) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pointer = event.changes.firstOrNull()
                                if (pointer != null && event.changes.any { it.pressed }) {
                                    val chartWidth = size.width - yAxisWidth
                                    val localX = (pointer.position.x - offsetX) / scaleX
                                    val fraction = (localX / chartWidth).coerceIn(0f, 1f)
                                    val targetDate = minDate + (fraction * dateRange).toLong()
                                    
                                    if (allDates.isNotEmpty()) {
                                        val closestIndex = allDates.withIndex().minByOrNull { Math.abs(it.value - targetDate) }?.index
                                        onIndexChange(closestIndex)
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(allDates) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScaleX = scaleX
                            scaleX = (scaleX * zoom).coerceIn(1f, 100f)
                            
                            val pivotX = centroid.x - offsetX
                            offsetX -= pivotX * (scaleX / oldScaleX - 1)
                            offsetX += pan.x
                        }
                    }
            ) {
                val chartWidth = size.width - yAxisWidth
                val chartHeight = size.height - xAxisHeight
                
                val maxOffsetX = 0f
                val minOffsetX = -chartWidth * (scaleX - 1)
                if (offsetX == Float.NEGATIVE_INFINITY || offsetX < minOffsetX) offsetX = minOffsetX
                if (offsetX > maxOffsetX) offsetX = maxOffsetX
                
                val visibleMinDate = minDate + (((-offsetX) / scaleX) / chartWidth * dateRange).toLong()
                val visibleMaxDate = minDate + (((chartWidth - offsetX) / scaleX) / chartWidth * dateRange).toLong()
                
                val visiblePoints = fundLines.flatMap { it.points }.filter { it.dateLong in visibleMinDate..visibleMaxDate }
                
                // Range is calculated on BOTH actual values and smoothed trend values to avoid visual clipping
                val visibleMinVal = visiblePoints.minOfOrNull { pt ->
                    val act = if (metricType == "Expected XIRR") pt.expectedXirr else pt.consistencyScore
                    val trnd = if (metricType == "Expected XIRR") pt.smoothedExpectedXirr else pt.smoothedConsistencyScore
                    minOf(act, trnd)
                } ?: 0.0
                
                val visibleMaxVal = visiblePoints.maxOfOrNull { pt ->
                    val act = if (metricType == "Expected XIRR") pt.expectedXirr else pt.consistencyScore
                    val trnd = if (metricType == "Expected XIRR") pt.smoothedExpectedXirr else pt.smoothedConsistencyScore
                    maxOf(act, trnd)
                } ?: 100.0
                
                val padding = (visibleMaxVal - visibleMinVal) * 0.12
                val minVal = visibleMinVal - padding
                val maxVal = visibleMaxVal + padding
                
                var range = maxVal - minVal
                if (range <= 0.0) range = 1.0
                
                clipRect(0f, 0f, chartWidth, chartHeight) {
                    val ySteps = 5
                    for (i in 0..ySteps) {
                        val v = minVal + (range * i / ySteps)
                        val scaledY = chartHeight - ((v - minVal) / range * chartHeight).toFloat()
                        if (scaledY in 0f..chartHeight) {
                            drawLine(
                                color = if (isDarkTheme) Color.DarkGray.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.5f),
                                start = Offset(0f, scaledY),
                                end = Offset(chartWidth, scaledY),
                                strokeWidth = 1f
                            )
                        }
                    }
                    
                    if (minVal < 0 && maxVal > 0) {
                        val scaledY = chartHeight - ((0 - minVal) / range * chartHeight).toFloat()
                        if (scaledY in 0f..chartHeight) {
                            drawLine(
                                color = if (isDarkTheme) Color.Gray else Color.DarkGray,
                                start = Offset(0f, scaledY),
                                end = Offset(chartWidth, scaledY),
                                strokeWidth = 2f
                            )
                        }
                    }
                    
                    for (fd in fundLines) {
                        if (fd.points.isEmpty()) continue
                        
                        // 1. Draw actual daily curve (thinner, semi-transparent for noise visual representation)
                        val actualPath = Path()
                        var actualFirst = true
                        for (pt in fd.points) {
                            val fraction = (pt.dateLong - minDate).toFloat() / dateRange
                            val rawX = fraction * chartWidth
                            val scaledX = rawX * scaleX + offsetX
                            
                            val ret = if (metricType == "Expected XIRR") pt.expectedXirr else pt.consistencyScore
                            val scaledY = chartHeight - ((ret - minVal) / range * chartHeight).toFloat()
                            
                            if (actualFirst) {
                                actualPath.moveTo(scaledX, scaledY)
                                actualFirst = false
                            } else {
                                actualPath.lineTo(scaledX, scaledY)
                            }
                        }
                        
                        drawPath(
                            path = actualPath,
                            color = fd.color.copy(alpha = 0.35f),
                            style = Stroke(
                                width = 1.5f,
                                join = StrokeJoin.Round,
                                cap = StrokeCap.Round
                            )
                        )
                        
                        // 2. Draw smoothed trend curve (thicker, solid, premium visual look)
                        val trendPath = Path()
                        var trendFirst = true
                        for (pt in fd.points) {
                            val fraction = (pt.dateLong - minDate).toFloat() / dateRange
                            val rawX = fraction * chartWidth
                            val scaledX = rawX * scaleX + offsetX
                            
                            val ret = if (metricType == "Expected XIRR") pt.smoothedExpectedXirr else pt.smoothedConsistencyScore
                            val scaledY = chartHeight - ((ret - minVal) / range * chartHeight).toFloat()
                            
                            if (trendFirst) {
                                trendPath.moveTo(scaledX, scaledY)
                                trendFirst = false
                            } else {
                                trendPath.lineTo(scaledX, scaledY)
                            }
                        }
                        
                        // 3. Area gradient fill under the trend curve
                        val fillPath = Path().apply {
                            addPath(trendPath)
                            
                            val lastPt = fd.points.last()
                            val lastFraction = (lastPt.dateLong - minDate).toFloat() / dateRange
                            val lastScaledX = (lastFraction * chartWidth) * scaleX + offsetX
                            lineTo(lastScaledX, chartHeight)
                            
                            val firstPt = fd.points.first()
                            val firstFraction = (firstPt.dateLong - minDate).toFloat() / dateRange
                            val firstScaledX = (firstFraction * chartWidth) * scaleX + offsetX
                            lineTo(firstScaledX, chartHeight)
                            
                            close()
                        }
                        
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(fd.color.copy(alpha = 0.18f), Color.Transparent),
                                startY = 0f,
                                endY = chartHeight
                            )
                        )
                        
                        drawPath(
                            path = trendPath,
                            color = fd.color,
                            style = Stroke(
                                width = 3.5f,
                                join = StrokeJoin.Round,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                    
                    if (selectedIndex != null && allDates.isNotEmpty() && selectedIndex in allDates.indices) {
                        val selDate = allDates[selectedIndex]
                        val fraction = (selDate - minDate).toFloat() / dateRange
                        val rawX = fraction * chartWidth
                        val scaledX = rawX * scaleX + offsetX
                        
                        if (scaledX in 0f..chartWidth) {
                            drawLine(
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                                start = Offset(scaledX, 0f),
                                end = Offset(scaledX, chartHeight),
                                strokeWidth = 2f
                            )
                            
                            for (fd in fundLines) {
                                val pt = fd.points.find { it.dateLong == selDate }
                                if (pt != null) {
                                    // Light node for daily actual value
                                    val actualRet = if (metricType == "Expected XIRR") pt.expectedXirr else pt.consistencyScore
                                    val actualScaledY = chartHeight - ((actualRet - minVal) / range * chartHeight).toFloat()
                                    drawCircle(color = fd.color.copy(alpha = 0.4f), radius = 4f, center = Offset(scaledX, actualScaledY))
                                    
                                    // High contrast node with white core for trend value
                                    val trendRet = if (metricType == "Expected XIRR") pt.smoothedExpectedXirr else pt.smoothedConsistencyScore
                                    val trendScaledY = chartHeight - ((trendRet - minVal) / range * chartHeight).toFloat()
                                    drawCircle(color = fd.color, radius = 7f, center = Offset(scaledX, trendScaledY))
                                    drawCircle(color = Color.White, radius = 3.5f, center = Offset(scaledX, trendScaledY))
                                }
                            }
                        }
                    }
                }
                
                drawRect(
                    color = if (isDarkTheme) Color(0xFF15191C) else Color(0xFFF4F6F8),
                    topLeft = Offset(chartWidth, 0f),
                    size = Size(yAxisWidth, chartHeight)
                )
                drawLine(
                    color = if (isDarkTheme) Color.DarkGray else Color.LightGray,
                    start = Offset(chartWidth, 0f),
                    end = Offset(chartWidth, chartHeight),
                    strokeWidth = 1f
                )
                
                val ySteps = 5
                for (i in 0..ySteps) {
                    val v = minVal + (range * i / ySteps)
                    val scaledY = chartHeight - ((v - minVal) / range * chartHeight).toFloat()
                    if (scaledY in 0f..chartHeight) {
                        val unit = if (metricType == "Expected XIRR") "%" else ""
                        val txtLayout = textMeasurer.measure("${v.toInt()}$unit", style = textStyle)
                        drawText(textLayoutResult = txtLayout, topLeft = Offset(chartWidth + 6f, scaledY - txtLayout.size.height / 2f))
                    }
                }
                
                drawRect(
                    color = if (isDarkTheme) Color(0xFF15191C) else Color(0xFFF4F6F8),
                    topLeft = Offset(0f, chartHeight),
                    size = Size(chartWidth, xAxisHeight)
                )
                drawLine(
                    color = if (isDarkTheme) Color.DarkGray else Color.LightGray,
                    start = Offset(0f, chartHeight),
                    end = Offset(chartWidth, chartHeight),
                    strokeWidth = 1f
                )
                
                if (dateRange > 0) {
                    val numXLabels = 4
                    for (i in 0..numXLabels) {
                        val fraction = i.toFloat() / numXLabels
                        val screenX = fraction * chartWidth
                        
                        val rawXView = (screenX - offsetX) / scaleX
                        val viewFraction = rawXView / chartWidth
                        val viewDateLong = minDate + (viewFraction * dateRange).toLong()
                        
                        if (viewDateLong in minDate..maxDate) {
                            val df = java.text.SimpleDateFormat("MMM-yy", Locale.US)
                            val dateStr = df.format(Date(viewDateLong))
                            val txtLayout = textMeasurer.measure(dateStr, style = textStyle)
                            val drawX = screenX.coerceIn(0f, chartWidth - txtLayout.size.width)
                            drawText(textLayoutResult = txtLayout, topLeft = Offset(drawX, chartHeight + 6f))
                        }
                    }
                }
            }
        }
    }
}

