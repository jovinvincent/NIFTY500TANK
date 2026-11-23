package com.example

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class RankScoreDeltaPoint(
    val dateLong: Long,
    val dateStr: String,
    val rawScore: Double,
    val deltaScore: Double, // Score(t) - Score(StartDate)
    val rank: Int = -1
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankScoreDeltaAnalysisScreen(
    baseSchemeName: String,
    allSchemes: List<String>,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedSchemes by remember { mutableStateOf(listOf(baseSchemeName)) }
    var rawSchemeData by remember { mutableStateOf<Map<String, List<RankScorePoint>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // UI state
    var showAddFundSheet by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var canvasWidth by remember { mutableStateOf(1f) }
    var fromDateStr by remember { mutableStateOf<String?>(null) }

    val bgCol = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA)
    val textCol = if (isDarkTheme) Color.White else Color(0xFF1E2228)
    val mutedCol = if (isDarkTheme) Color(0xFFA0AAB4) else Color(0xFF64748B)
    val lineColors = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981), // Green
        Color(0xFFF59E0B), // Orange
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4), // Cyan
        Color(0xFFEF4444), // Red
        Color(0xFFEAB308)  // Yellow
    )

    // Load data
    LaunchedEffect(selectedSchemes) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val newSchemeData = mutableMapOf<String, List<RankScorePoint>>()
            
            for (scheme in selectedSchemes) {
                if (rawSchemeData.containsKey(scheme)) {
                    newSchemeData[scheme] = rawSchemeData[scheme]!!
                    continue
                }
                
                var records = NavCache.getRecords(scheme)
                if (records.isEmpty()) {
                    val dbRecords = db.fundDao().getHistoricalNavs(scheme)
                    records = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                    if (records.isNotEmpty()) {
                        NavCache.addAll(scheme, records)
                    }
                }
                
                if (records.isNotEmpty()) {
                    val points = records.map { rec ->
                        TimelineChartPoint(rec.dateStr, rec.nav, null, null, 0.0, 0.0)
                    }
                    val mappedPoints = points.mapNotNull { pt ->
                        val dateLong = parseTimelineDate(pt.dateStr)
                        if (dateLong > 0) Pair(pt, dateLong) else null
                    }.sortedBy { it.second }
                    
                    if (mappedPoints.isNotEmpty()) {
                        val minD = mappedPoints.first().second
                        val maxD = mappedPoints.last().second
                        
                        val totalDays = ((maxD - minD) / (24L * 60 * 60 * 1000L)).coerceAtLeast(1)
                        val stepDays = (totalDays / 120).coerceAtLeast(1)
                        val stepMs = stepDays * 24L * 60 * 60 * 1000L
                        
                        val sharedDates = mutableListOf<Long>()
                        var t = minD
                        while (t <= maxD) {
                            sharedDates.add(t)
                            t += stepMs
                        }
                        if (sharedDates.isEmpty() || sharedDates.last() != maxD) sharedDates.add(maxD)
                        
                        val historyList = mutableListOf<Pair<TimelineChartPoint, Long>>()
                        var k = 0
                        
                        val dailyXirr = mutableListOf<Double>()
                        val dailyConsistency = mutableListOf<Double>()
                        
                        val schemePoints = mutableListOf<RankScorePoint>()
                        
                        for (evalTime in sharedDates) {
                            while (k < mappedPoints.size && mappedPoints[k].second <= evalTime) {
                                historyList.add(mappedPoints[k])
                                k++
                            }
                            if (historyList.size >= 10) {
                                val (score, xirr) = calculateConsistencyAndForecastMapped(historyList)
                                dailyConsistency.add(score)
                                dailyXirr.add(xirr)
                                
                                val historySize = dailyXirr.size
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
                                
                                var emaX = 0.0
                                var emaC = 0.0
                                for (i in dailyXirr.indices) {
                                    val x = dailyXirr[i]
                                    val c = dailyConsistency[i]
                                    if (i == 0) {
                                        emaX = x
                                        emaC = c
                                    } else {
                                        emaX = alpha * x + (1.0 - alpha) * emaX
                                        emaC = alpha * c + (1.0 - alpha) * emaC
                                    }
                                }
                                
                                val compScore = scaleCompositeScore(emaX, dailyXirr.last(), emaC, dailyConsistency.last())
                                val p = historyList.last().first
                                schemePoints.add(RankScorePoint(evalTime, p.dateStr, compScore))
                            }
                        }
                        newSchemeData[scheme] = schemePoints
                    }
                }
            }
            rawSchemeData = newSchemeData
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rank Score Delta Analysis", color = textCol) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = textCol)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        val cal = Calendar.getInstance()
                        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                        if (fromDateStr != null) {
                            try { sdf.parse(fromDateStr!!)?.let { cal.time = it } } catch (e: Exception) {}
                        }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                cal.set(y, m, d)
                                fromDateStr = sdf.format(cal.time)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Icon(Icons.Default.DateRange, "Change Start Date", tint = textCol)
                    }
                    if (selectedSchemes.size > 1) {
                        IconButton(onClick = { selectedSchemes = listOf(baseSchemeName) }) {
                            Icon(Icons.Default.Clear, "Clear Others", tint = textCol)
                        }
                    }
                    IconButton(onClick = { showAddFundSheet = true }) {
                        Icon(Icons.Default.Add, "Add Fund", tint = textCol)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgCol)
            )
        },
        containerColor = bgCol
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Determine global maximum date to compute default 2 months range if fromDateStr is null
                var globalMaxDate = 0L
                rawSchemeData.values.forEach { pts ->
                    pts.lastOrNull()?.let { if (it.dateLong > globalMaxDate) globalMaxDate = it.dateLong }
                }

                val effectiveFromDateStr = remember(globalMaxDate, fromDateStr) {
                    if (fromDateStr != null) {
                        fromDateStr!!
                    } else if (globalMaxDate > 0L) {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = globalMaxDate
                        cal.add(Calendar.MONTH, -2)
                        SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(cal.time)
                    } else ""
                }

                val fromDateLong = parseTimelineDate(effectiveFromDateStr)

                // Compute baseline score for each scheme at fromDateLong
                val deltaSchemeData = mutableMapOf<String, List<RankScoreDeltaPoint>>()
                
                selectedSchemes.forEach { scheme ->
                    val pts = rawSchemeData[scheme] ?: emptyList()
                    val filtered = pts.filter { it.dateLong >= fromDateLong }
                    if (filtered.isNotEmpty()) {
                        val baselineScore = filtered.first().score
                        val deltaPoints = filtered.map { pt ->
                            RankScoreDeltaPoint(
                                dateLong = pt.dateLong,
                                dateStr = pt.dateStr,
                                rawScore = pt.score,
                                deltaScore = pt.score - baselineScore
                            )
                        }
                        deltaSchemeData[scheme] = deltaPoints
                    }
                }

                // Collect all shared dates across filtered schemes
                val allDatesSet = mutableSetOf<Long>()
                deltaSchemeData.values.forEach { pts ->
                    pts.forEach { allDatesSet.add(it.dateLong) }
                }
                val sortedDates = allDatesSet.toList().sorted()

                val alignedDeltaData = mutableMapOf<String, MutableList<RankScoreDeltaPoint>>()
                deltaSchemeData.keys.forEach { alignedDeltaData[it] = mutableListOf() }

                for (d in sortedDates) {
                    val currentDayScores = mutableListOf<Pair<String, Double>>()
                    
                    for ((scheme, pts) in deltaSchemeData) {
                        val pt = pts.lastOrNull { it.dateLong <= d }
                        if (pt != null) {
                            currentDayScores.add(scheme to pt.rawScore)
                        }
                    }
                    
                    currentDayScores.sortByDescending { it.second }
                    val rankMap = currentDayScores.mapIndexed { idx, pair -> pair.first to (idx + 1) }.toMap()
                    
                    for ((scheme, pts) in deltaSchemeData) {
                        val pt = pts.lastOrNull { it.dateLong <= d }
                        if (pt != null) {
                            alignedDeltaData[scheme]!!.add(
                                pt.copy(rank = rankMap[scheme] ?: -1)
                            )
                        }
                    }
                }

                var minDate = Long.MAX_VALUE
                var maxDate = Long.MIN_VALUE
                var minDelta = Double.MAX_VALUE
                var maxDelta = Double.MIN_VALUE

                alignedDeltaData.values.forEach { pts ->
                    pts.forEach { pt ->
                        if (pt.dateLong < minDate) minDate = pt.dateLong
                        if (pt.dateLong > maxDate) maxDate = pt.dateLong
                        if (pt.deltaScore < minDelta) minDelta = pt.deltaScore
                        if (pt.deltaScore > maxDelta) maxDelta = pt.deltaScore
                    }
                }

                if (minDate > maxDate) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No data available in selected range", color = textCol)
                    }
                } else {
                    val dateRange = maxDate - minDate
                    // Always include 0.0 in range so baseline zero line is visible
                    val pMinVal = Math.min(minDelta, -1.0)
                    val pMaxVal = Math.max(maxDelta, 1.0)
                    val valRange = (pMaxVal - pMinVal).coerceAtLeast(0.1)

                    val pMin = pMinVal - valRange * 0.1
                    val pMax = pMaxVal + valRange * 0.1
                    val pRange = pMax - pMin

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Absolute Score Point Change",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textCol
                        )
                        Text(
                            text = "Baseline Start Date: $effectiveFromDateStr (Relative 0.0)",
                            style = MaterialTheme.typography.labelMedium,
                            color = mutedCol
                        )
                    }

                    // Graph
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> scrubPosition = offset.x },
                                onDrag = { change, _ -> scrubPosition = change.position.x },
                                onDragEnd = { scrubPosition = null },
                                onDragCancel = { scrubPosition = null }
                            )
                        }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            canvasWidth = w
                            val h = size.height

                            // Grid
                            val gridLines = 4
                            for (i in 0..gridLines) {
                                val y = h * (i.toFloat() / gridLines)
                                val v = pMax - (pRange * (i.toFloat() / gridLines))
                                drawLine(
                                    color = mutedCol.copy(alpha = 0.2f),
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    String.format(Locale.US, "%+.1f", v),
                                    0f, y - 5f,
                                    android.graphics.Paint().apply {
                                        color = android.graphics.Color.GRAY
                                        textSize = 10.sp.toPx()
                                    }
                                )
                            }

                            // Zero reference line
                            val zeroY = h - ((0.0 - pMin) / pRange * h).toFloat()
                            if (zeroY in 0f..h) {
                                drawLine(
                                    color = Color(0xFF22C55E).copy(alpha = 0.6f),
                                    start = Offset(0f, zeroY),
                                    end = Offset(w, zeroY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            }

                            // Fund lines
                            selectedSchemes.forEachIndexed { i, scheme ->
                                val pts = alignedDeltaData[scheme] ?: emptyList()
                                if (pts.isNotEmpty()) {
                                    val path = Path()
                                    var started = false
                                    pts.forEach { pt ->
                                        val x = if (dateRange == 0L) w / 2f else (pt.dateLong - minDate).toFloat() / dateRange * w
                                        val y = h - ((pt.deltaScore - pMin) / pRange * h).toFloat()
                                        if (!started) {
                                            path.moveTo(x, y)
                                            started = true
                                        } else {
                                            path.lineTo(x, y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = lineColors[i % lineColors.size],
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }

                            // Scrubber
                            if (scrubPosition != null) {
                                val sx = scrubPosition!!.coerceIn(0f, w)
                                drawLine(
                                    color = textCol.copy(alpha = 0.5f),
                                    start = Offset(sx, 0f),
                                    end = Offset(sx, h),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }

                    // Legend & Scrubber info below graph
                    val displayTime = if (scrubPosition != null) {
                        minDate + (scrubPosition!! / canvasWidth * dateRange).toLong()
                    } else {
                        maxDate
                    }

                    val displayDateStr = if (displayTime > 0) {
                        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(displayTime))
                    } else ""

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E2228) else Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Date: $displayDateStr", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = textCol)
                            Spacer(Modifier.height(8.dp))

                            val dayScores = mutableListOf<Triple<String, RankScoreDeltaPoint, Int>>()
                            selectedSchemes.forEachIndexed { idx, scheme ->
                                val pts = alignedDeltaData[scheme] ?: emptyList()
                                val pt = if (scrubPosition != null) {
                                    pts.minByOrNull { Math.abs(it.dateLong - displayTime) }
                                } else {
                                    pts.lastOrNull()
                                }
                                if (pt != null) {
                                    dayScores.add(Triple(scheme, pt, idx))
                                }
                            }

                            dayScores.sortBy { it.second.rank }

                            dayScores.forEach { (scheme, pt, colorIdx) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(12.dp).background(lineColors[colorIdx % lineColors.size], RoundedCornerShape(2.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        scheme.take(20) + if (scheme.length > 20) "..." else "", 
                                        color = textCol, 
                                        modifier = Modifier.weight(1f), 
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Rank: ${if (pt.rank > 0) pt.rank else "-"}", 
                                        color = mutedCol, 
                                        fontSize = 14.sp, 
                                        modifier = Modifier.width(60.dp)
                                    )
                                    val deltaFormatted = String.format(Locale.US, "%+.2f", pt.deltaScore)
                                    val deltaColor = if (pt.deltaScore > 0) Color(0xFF22C55E) else if (pt.deltaScore < 0) Color(0xFFEF4444) else textCol
                                    Text(
                                        deltaFormatted, 
                                        color = deltaColor, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddFundSheet) {
        ModalBottomSheet(onDismissRequest = { showAddFundSheet = false }, containerColor = bgCol) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Compare with Fund", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textCol)
                Spacer(Modifier.height(16.dp))
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Add All Funds", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.clickable {
                                selectedSchemes = allSchemes
                                showAddFundSheet = false
                            }
                        )
                        Divider()
                    }
                    items(allSchemes, key = { it }, contentType = { "scheme_option" }) { scheme ->
                        if (scheme !in selectedSchemes) {
                            ListItem(
                                headlineContent = { Text(scheme) },
                                modifier = Modifier.clickable {
                                    selectedSchemes = selectedSchemes + scheme
                                    showAddFundSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
