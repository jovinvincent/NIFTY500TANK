package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.max

data class SmaConfig(val fastPeriod: Int, val slowPeriod: Int, val levelLabel: String)
data class CrossEvent(val type: String, val date: String, val sessionsAgo: Int)

fun calculateSMA(records: List<NavRecord>, period: Int, index: Int): Double? {
    if (index < period - 1 || index >= records.size || period <= 0) return null
    var sum = 0.0
    var k = (index - period) + 1
    while (k <= index) {
        sum += records[k].nav
        k++
    }
    return sum / period
}

fun computeAnalyticsForScheme(records: List<NavRecord>): SmaConfig {
    val n = records.size
    return when {
        n >= 50 -> SmaConfig(20, 50, "Adjusted Trend (20/50 SMA)")
        n >= 15 -> SmaConfig(5, 15, "Micro Trend")
        n >= 5 -> SmaConfig(maxOf(n / 2, 1), n - 1, "Early Trend")
        else -> SmaConfig(1, n, "Inception Tracking")
    }
}

fun findLatestCross(records: List<NavRecord>, fastPeriod: Int, slowPeriod: Int): CrossEvent? {
    for (i in records.size - 1 downTo 1) {
        val fCurr = calculateSMA(records, fastPeriod, i) ?: continue
        val sCurr = calculateSMA(records, slowPeriod, i) ?: continue
        val fPrev = calculateSMA(records, fastPeriod, i - 1) ?: continue
        val sPrev = calculateSMA(records, slowPeriod, i - 1) ?: continue

        if (fCurr > sCurr && fPrev <= sPrev) {
            return CrossEvent("GOLDEN_CROSS", records[i].dateStr, records.size - 1 - i)
        }
        if (fCurr < sCurr && fPrev >= sPrev) {
            return CrossEvent("DEATH_CROSS", records[i].dateStr, records.size - 1 - i)
        }
    }
    return null
}

data class TimelineChartPoint(
    val dateStr: String,
    val nav: Double,
    val fastSma: Double?,
    val slowSma: Double?,
    val trend: Double,
    val trendScore: Double,
    val sma200: Double? = null
)

data class ModelResults(
    val fastPeriod: Int,
    val slowPeriod: Int,
    val trendCurve: DoubleArray,
    val scoreCurve: DoubleArray,
    val pointsRaw: List<TimelineChartPoint>,
    val cross: CrossEvent?,
    val smaConfig: SmaConfig
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovingAverageTrendVisualizer(
    schemeName: String,
    unitsHeld: Double = 0.0,
    investedAmount: Double = 0.0,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit,
    onCompareFunds: () -> Unit = {},
    onRollingReturnsGraphClick: () -> Unit = {},
    onForecastGraphClick: () -> Unit = {},
    onRankScoreClick: () -> Unit = {},
    onSipAnalyzerClick: () -> Unit = {},
    onWeeklyAnalyzerClick: () -> Unit = {},
    onRollingWeeklyAnalyzerClick: () -> Unit = {},
    onRollingDailyAnalyzerClick: () -> Unit = {},
    onDailySipAnalysisClick: () -> Unit = {},
    onSmartRecommendationClick: () -> Unit = {},
    onOpenDailySummary: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    targetScrollIndex: Int? = null,
    onScrollIndexConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var rawRecords by remember(schemeName) { mutableStateOf(NavCache.getRecords(schemeName)) }
    var isReady by remember(schemeName) { mutableStateOf(rawRecords.isNotEmpty()) }

    LaunchedEffect(targetScrollIndex, isReady) {
        if (targetScrollIndex != null && isReady) {
            listState.scrollToItem(targetScrollIndex)
            onScrollIndexConsumed()
        }
    }

    LaunchedEffect(schemeName) {
        if (rawRecords.isEmpty()) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val dbRecords = db.fundDao().getHistoricalNavs(schemeName).map { NavRecord(it.dateStr, it.navValue) }
                val records = IndexBenchmarkSimulator.getFullHistoricalNavs(context, schemeName, dbRecords)
                if (records.isNotEmpty()) {
                    NavCache.addAll(schemeName, records)
                }
            }
            rawRecords = NavCache.getRecords(schemeName)
            isReady = rawRecords.isNotEmpty()
        } else {
            isReady = true
        }
    }

    if (!isReady) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Loading...", maxLines = 1)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val parsedRecords = remember(schemeName, rawRecords) {
        rawRecords.map { rec ->
            val time = try {
                SimpleDateFormat("dd-MMM-yyyy", Locale.US).parse(rec.dateStr)?.time ?: 0L
            } catch (e: Exception) {
                try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(rec.dateStr)?.time ?: 0L
                } catch (e2: Exception) {
                    0L
                }
            }
            Pair(rec, time)
        }.distinctBy { it.second }.sortedBy { it.second }
    }

    val records = remember(parsedRecords) {
        parsedRecords.map { it.first }
    }

    val modelCalculation = remember(records) {
        val smaConfig = computeAnalyticsForScheme(records)
        val fastPeriod = smaConfig.fastPeriod
        val slowPeriod = smaConfig.slowPeriod

        val N = records.size
        if (N < 2) {
            val trend = DoubleArray(N) { records[it].nav }
            val score = DoubleArray(N) { 0.0 }
            val pts = records.mapIndexed { idx, rec ->
                TimelineChartPoint(rec.dateStr, rec.nav, calculateSMA(records, fastPeriod, idx), calculateSMA(records, slowPeriod, idx), trend[idx], score[idx], calculateSMA(records, 200, idx))
            }
            return@remember ModelResults(fastPeriod, slowPeriod, trend, score, pts, null, smaConfig)
        }

        val x = DoubleArray(N) { it.toDouble() }
        val y = DoubleArray(N) { records[it].nav }

        val span = 0.3
        val k = (N * span).toInt().coerceIn(2, N)
        val halfK = k / 2
        val baseTrend = DoubleArray(N)

        for (i in 0 until N) {
            val jStart = (i - halfK).coerceAtLeast(0)
            val jEnd = (i + halfK).coerceAtMost(N - 1)
            val dMax = (maxOf(i - jStart, jEnd - i).toDouble()).coerceAtLeast(1.0)

            var sumW = 0.0; var sumWt = 0.0; var sumWy = 0.0
            for (j in jStart..jEnd) {
                val d = kotlin.math.abs(j - i).toDouble()
                val u = d / dMax
                if (u < 1.0) {
                    val oneMinusU3 = 1.0 - u * u * u
                    val w = oneMinusU3 * oneMinusU3 * oneMinusU3
                    sumW += w
                    sumWt += w * x[j]
                    sumWy += w * y[j]
                }
            }
            if (sumW > 0) {
                val meanT = sumWt / sumW
                val meanY = sumWy / sumW
                var num = 0.0; var den = 0.0
                for (j in jStart..jEnd) {
                    val d = kotlin.math.abs(j - i).toDouble()
                    val u = d / dMax
                    if (u < 1.0) {
                        val oneMinusU3 = 1.0 - u * u * u
                        val w = oneMinusU3 * oneMinusU3 * oneMinusU3
                        val diffT = x[j] - meanT
                        num += w * diffT * (y[j] - meanY)
                        den += w * diffT * diffT
                    }
                }
                val b = if (den > 0) num / den else 0.0
                val a = meanY - b * meanT
                baseTrend[i] = a + b * x[i]
            } else baseTrend[i] = y[i]
        }

        val trendCurve = DoubleArray(N)
        var currentErrorBias = 0.0
        val learningRate = 0.05

        for (i in 0 until N) {
            val predicted = baseTrend[i] + currentErrorBias
            trendCurve[i] = predicted
            val error = y[i] - predicted
            currentErrorBias += learningRate * error
        }

        val trendSlope = DoubleArray(N)
        var smoothedSlope = 0.0
        var maxAbsSlope = 0.0

        for (i in 0 until N) {
            val instantaneousSlope = when (i) {
                0 -> if (N > 1) trendCurve[1] - trendCurve[0] else 0.0
                N - 1 -> if (N > 1) trendCurve[N - 1] - trendCurve[N - 2] else 0.0
                else -> (trendCurve[i + 1] - trendCurve[i - 1]) / 2.0
            }
            smoothedSlope = 0.85 * smoothedSlope + 0.15 * instantaneousSlope
            trendSlope[i] = smoothedSlope
            maxAbsSlope = kotlin.math.max(maxAbsSlope, kotlin.math.abs(smoothedSlope))
        }
        if (maxAbsSlope == 0.0) maxAbsSlope = 1.0

        val scoreCurve = DoubleArray(N)
        for (i in 0 until N) {
            val normalizedSlope = trendSlope[i] / maxAbsSlope
            val smoothSineScore = kotlin.math.sin(kotlin.math.abs(normalizedSlope) * kotlin.math.PI / 2) * 100.0
            scoreCurve[i] = if (normalizedSlope >= 0) +smoothSineScore else -smoothSineScore
        }

        val pointsRaw = records.mapIndexed { idx, rec ->
            val fs = calculateSMA(records, fastPeriod, idx)
            val ss = calculateSMA(records, slowPeriod, idx)
            val s200 = calculateSMA(records, 200, idx)
            TimelineChartPoint(rec.dateStr, rec.nav, fs, ss, trendCurve[idx], scoreCurve[idx], s200)
        }

        val cross = findLatestCross(records, fastPeriod, slowPeriod) ?: run {
            val lastIdx = records.size - 1
            val fLast = calculateSMA(records, fastPeriod, lastIdx) ?: 0.0
            val sLast = calculateSMA(records, slowPeriod, lastIdx) ?: 0.0
            if (fLast >= sLast) {
                CrossEvent("BULLISH_STATE", records.lastOrNull()?.dateStr ?: "Recent", 0)
            } else {
                CrossEvent("BEARISH_STATE", records.lastOrNull()?.dateStr ?: "Recent", 0)
            }
        }

        ModelResults(
            fastPeriod = fastPeriod,
            slowPeriod = slowPeriod,
            trendCurve = trendCurve,
            scoreCurve = scoreCurve,
            pointsRaw = pointsRaw,
            cross = cross,
            smaConfig = smaConfig
        )
    }

    val fastPeriod = modelCalculation.fastPeriod
    val slowPeriod = modelCalculation.slowPeriod
    val trendCurve = modelCalculation.trendCurve
    val scoreCurve = modelCalculation.scoreCurve
    val pointsRaw = modelCalculation.pointsRaw
    val cross = modelCalculation.cross
    val smaConfig = modelCalculation.smaConfig

    // Timeframe filters & modes
    var timeFilter by remember { mutableStateOf<String?>("ALL") }
    var isDualMarkerMode by remember { mutableStateOf(false) }

    var customUnit by remember { mutableStateOf("Months") }
    var customValue by remember { mutableStateOf("5") }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedIndexA by remember { mutableStateOf<Int?>(null) }
    var selectedIndexB by remember { mutableStateOf<Int?>(null) }
    var currentProgressA by remember { mutableStateOf(0f) }
    var currentProgressB by remember { mutableStateOf(0f) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val boundsAndPoints = remember(
        timeFilter, customUnit, customValue, customStartDate, customEndDate, parsedRecords, pointsRaw, fastPeriod, slowPeriod, trendCurve, scoreCurve, records
    ) {
        var cutoffTime = parsedRecords.firstOrNull()?.second ?: 0L
        var maxTime = parsedRecords.lastOrNull()?.second ?: System.currentTimeMillis()

        if (timeFilter != null && timeFilter != "ALL") {
            val now = maxTime
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            when (timeFilter) {
                "1M" -> cal.add(Calendar.MONTH, -1)
                "3M" -> cal.add(Calendar.MONTH, -3)
                "6M" -> cal.add(Calendar.MONTH, -6)
                "1Y" -> cal.add(Calendar.YEAR, -1)
                "3Y" -> cal.add(Calendar.YEAR, -3)
                "Custom" -> {
                    val v = customValue.toIntOrNull() ?: 1
                    when (customUnit) {
                        "Days" -> cal.add(Calendar.DAY_OF_YEAR, -v)
                        "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, -v)
                        "Months" -> cal.add(Calendar.MONTH, -v)
                        "Years" -> cal.add(Calendar.YEAR, -v)
                    }
                }
                "Calendar" -> {
                    if (customStartDate != null) {
                        cutoffTime = customStartDate!!
                    }
                    if (customEndDate != null) {
                        maxTime = customEndDate!!
                    }
                    cal.timeInMillis = cutoffTime
                }
            }
            if (timeFilter != "Calendar") {
                cutoffTime = cal.timeInMillis
            }
        }

        val visiblePoints = if (cutoffTime <= (parsedRecords.firstOrNull()?.second ?: 0L) && maxTime >= (parsedRecords.lastOrNull()?.second ?: 0L)) pointsRaw else {
            val filtered = parsedRecords.filter { it.second in cutoffTime..maxTime }.map { it.first }
            val startIndex = parsedRecords.indexOfFirst { it.second >= cutoffTime }.coerceAtLeast(0)
            filtered.mapIndexed { i, rec ->
                val idx = startIndex + i
                val safeIdx = idx.coerceIn(0, records.lastIndex)
                val fs = calculateSMA(records, fastPeriod, safeIdx)
                val ss = calculateSMA(records, slowPeriod, safeIdx)
                val s200 = calculateSMA(records, 200, safeIdx)
                TimelineChartPoint(rec.dateStr, rec.nav, fs, ss, trendCurve[safeIdx], scoreCurve[safeIdx], s200)
            }
        }

        val points = if (visiblePoints.size < 2) pointsRaw else visiblePoints
        Triple(cutoffTime, maxTime, points)
    }

    val cutoffTime = boundsAndPoints.first
    val maxTime = boundsAndPoints.second
    val points = boundsAndPoints.third

    val avgNAV = if (investedAmount > 0.0 && unitsHeld > 0.0) investedAmount / unitsHeld else null

    val isDark = isDarkTheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            schemeName,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp)
                        )
                        Text(
                            "Moving Average Trend Visualizer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenDailySummary,
                        modifier = Modifier.testTag("trend_daily_summary_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Daily Recommendation Summary",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                cross?.let { evt ->
                    val cardBg = if (isDark) Color(0xFF0F0F12) else Color(0xFFF9FAFC)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                                    )
                                ),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            ) {
                            val isBullish = evt.type == "GOLDEN_CROSS" || evt.type == "BULLISH_STATE"
                            Icon(
                                if (isBullish) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                when (evt.type) {
                                    "GOLDEN_CROSS" -> "Golden Cross"
                                    "BULLISH_STATE" -> "Bullish Trend Alignment"
                                    "DEATH_CROSS" -> "Death Cross"
                                    else -> "Bearish Trend Alignment"
                                },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "System: ${smaConfig.levelLabel}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            val descriptionText = if (evt.type == "GOLDEN_CROSS" || evt.type == "DEATH_CROSS") {
                                val word = if (evt.type == "GOLDEN_CROSS") "Bullish breakout!" else "Bearish breakdown!"
                                val act = if (evt.type == "GOLDEN_CROSS") "crossed above" else "crossed below"
                                val conc = if (evt.type == "GOLDEN_CROSS") "strong bullish trend reversal and long-term upward momentum." else "strong bearish trend reversal and long-term downward momentum."
                                "$word A ${if (evt.type == "GOLDEN_CROSS") "Golden" else "Death"} Cross occurred on ${evt.date} (${evt.sessionsAgo} sessions ago) when the $fastPeriod-day SMA $act the $slowPeriod-day SMA. This indicates a $conc"
                            } else {
                                val stateName = if (evt.type == "BULLISH_STATE") "Bullish Trend Alignment" else "Bearish Trend Alignment"
                                val direction = if (evt.type == "BULLISH_STATE") "above" else "below"
                                val impact = if (evt.type == "BULLISH_STATE") "sustained upward trend support and positive momentum." else "downside pressure and defensive trend structure."
                                "Active $stateName: The ${fastPeriod}-day SMA is trading $direction the ${slowPeriod}-day SMA across current history, indicating $impact"
                            }
                            Text(
                                descriptionText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        }
                    }
                }

                // Top Details Overlay
                if (isDualMarkerMode) {
                    val safeA = selectedIndexA?.coerceIn(0, maxOf(0, points.lastIndex))
                    val safeB = selectedIndexB?.coerceIn(0, maxOf(0, points.lastIndex))

                    if (safeA != null && safeB != null && points.isNotEmpty()) {
                        val startIdx = minOf(safeA, safeB)
                        val endIdx = maxOf(safeA, safeB)
                        CompareDetailRow(
                            pointStart = points[startIdx],
                            pointEnd = points[endIdx],
                            unitsHeld = unitsHeld,
                            isDark = isDark,
                            onStartClick = { showStartDatePicker = true },
                            onEndClick = { showEndDatePicker = true }
                        )
                    } else {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(
                                    0xFF1E1E1E
                                ) else Color(0xFFF5F5F5)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(262.dp)
                                .padding(bottom = 16.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "Select two points on the chart to compare",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    val effectiveIdx = if (selectedIndex != null && selectedIndex!! in points.indices) selectedIndex!! else if (points.isNotEmpty()) points.lastIndex else null
                    if (effectiveIdx != null) {
                        TooltipDetailRow(
                            point = points[effectiveIdx],
                            isDark = isDark,
                            fastPeriod = fastPeriod,
                            slowPeriod = slowPeriod,
                            onClear = { selectedIndex = null }
                        )
                    }
                }

                // Chart Container
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp, bottom = 16.dp)
                    ) { // inside padding
                        InteractiveTimelineChart(
                            points = points,
                            avgNAV = avgNAV,
                            crossEvent = cross,
                            fastPeriod = fastPeriod,
                            slowPeriod = slowPeriod,
                            isDark = isDark,
                            isDualMarkerMode = isDualMarkerMode,
                            selectedIndex = selectedIndex,
                            onSelectIndex = { idx -> selectedIndex = idx },
                            selectedIndexA = selectedIndexA,
                            selectedIndexB = selectedIndexB,
                            currentProgressA = currentProgressA,
                            currentProgressB = currentProgressB,
                            onUpdateCompareIndices = { a, b, pa, pb ->
                                selectedIndexA = a
                                selectedIndexB = b
                                currentProgressA = pa
                                currentProgressB = pb
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            item {
                // Timeframe Segmented Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Fixed Single/Compare Modes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modeChips = listOf("Single", "Compare")
                    modeChips.forEach { label ->
                        val isActive = (label == "Compare" && isDualMarkerMode) || (label == "Single" && !isDualMarkerMode)
                        Surface(
                            onClick = {
                                isDualMarkerMode = (label == "Compare")
                                if (isDualMarkerMode) {
                                    if (points.isNotEmpty()) {
                                        val ptA = points.size / 4
                                        val ptB = (points.size * 3) / 4
                                        selectedIndexA = ptA
                                        selectedIndexB = ptB
                                        currentProgressA = ptA.toFloat() / maxOf(1, points.lastIndex)
                                        currentProgressB = ptB.toFloat() / maxOf(1, points.lastIndex)
                                    }
                                } else {
                                    selectedIndex = null
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isActive) Color(0xFFF57C00) else Color.Transparent, // Orange for mode
                            border = if (isActive) null else androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    if (isDualMarkerMode) {
                        IconButton(
                            onClick = { onCompareFunds() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFF57C00).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Funds to Compare",
                                tint = Color(0xFFF57C00),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // Scrollable Timeframes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeChips = listOf("1M", "3M", "6M", "1Y", "3Y", "ALL", "Custom")
                    timeChips.forEach { label ->
                        val isActive = timeFilter == label
                        Surface(
                            onClick = {
                                timeFilter = label
                                selectedIndex = null
                                if (isDualMarkerMode && points.isNotEmpty()) {
                                    val ptA = points.size / 4
                                    val ptB = (points.size * 3) / 4
                                    selectedIndexA = ptA
                                    selectedIndexB = ptB
                                    currentProgressA = ptA.toFloat() / maxOf(1, points.lastIndex)
                                    currentProgressB = ptB.toFloat() / maxOf(1, points.lastIndex)
                                } else {
                                    selectedIndexA = null
                                    selectedIndexB = null
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = if (isActive) null else androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    val isCalendarActive = timeFilter == "Calendar"
                    Surface(
                        onClick = {
                            timeFilter = "Calendar"
                            selectedIndex = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCalendarActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = if (isCalendarActive) null else androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = "Calendar",
                                tint = if (isCalendarActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            if (timeFilter == "Custom") {
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Relative Past Range:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var isDropdownExpanded by remember { mutableStateOf(false) }
                            androidx.compose.foundation.text.BasicTextField(
                                value = customValue,
                                onValueChange = { customValue = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier.width(48.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)).padding(8.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Box {
                                Row(
                                    modifier = Modifier.clickable { isDropdownExpanded = true }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(customUnit, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = isDropdownExpanded, onDismissRequest = { isDropdownExpanded = false }) {
                                    listOf("Days", "Weeks", "Months", "Years").forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit) },
                                            onClick = {
                                                customUnit = unit
                                                isDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (timeFilter == "Calendar") {
                fun parseDateStringToMillis(dateStr: String): Long {
                    val formats = listOf("dd-MMM-yyyy", "yyyy-MM-dd", "dd-MMM''yy", "dd-MMM-yy")
                    for (fStr in formats) {
                        try {
                            val fmt = java.text.SimpleDateFormat(fStr, Locale.US)
                            val d = fmt.parse(dateStr)
                            if (d != null) return d.time
                        } catch (e: Exception) {}
                    }
                    return System.currentTimeMillis()
                }

                if (showStartDatePicker) {
                    val initialDate = if (isDualMarkerMode) {
                        val minIdx = minOf(selectedIndexA ?: 0, selectedIndexB ?: 0).coerceIn(0, maxOf(0, points.lastIndex))
                        val dateStr = points.getOrNull(minIdx)?.dateStr ?: ""
                        parseDateStringToMillis(dateStr)
                    } else {
                        customStartDate ?: System.currentTimeMillis()
                    }
                    val datePickerState = androidx.compose.material3.rememberDatePickerState(
                        initialSelectedDateMillis = initialDate
                    )
                    androidx.compose.material3.DatePickerDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                val selectedMillis = datePickerState.selectedDateMillis
                                if (selectedMillis != null) {
                                    if (isDualMarkerMode) {
                                        var closestIdx = 0
                                        var minDiff = Long.MAX_VALUE
                                        points.forEachIndexed { idx, pt ->
                                            val ptTime = parseDateStringToMillis(pt.dateStr)
                                            if (ptTime > 0L) {
                                                val diff = kotlin.math.abs(ptTime - selectedMillis)
                                                if (diff < minDiff) {
                                                    minDiff = diff
                                                    closestIdx = idx
                                                }
                                            }
                                        }
                                        selectedIndexA = closestIdx
                                        currentProgressA = closestIdx.toFloat() / maxOf(1, points.lastIndex)
                                    } else {
                                        customStartDate = selectedMillis
                                    }
                                }
                                showStartDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        androidx.compose.material3.DatePicker(state = datePickerState)
                    }
                }
                
                if (showEndDatePicker) {
                    val initialDate = if (isDualMarkerMode) {
                        val maxIdx = maxOf(selectedIndexA ?: 0, selectedIndexB ?: 0).coerceIn(0, maxOf(0, points.lastIndex))
                        val dateStr = points.getOrNull(maxIdx)?.dateStr ?: ""
                        parseDateStringToMillis(dateStr)
                    } else {
                        customEndDate ?: System.currentTimeMillis()
                    }
                    val datePickerState = androidx.compose.material3.rememberDatePickerState(
                        initialSelectedDateMillis = initialDate
                    )
                    androidx.compose.material3.DatePickerDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                val selectedMillis = datePickerState.selectedDateMillis
                                if (selectedMillis != null) {
                                    if (isDualMarkerMode) {
                                        var closestIdx = 0
                                        var minDiff = Long.MAX_VALUE
                                        points.forEachIndexed { idx, pt ->
                                            val ptTime = parseDateStringToMillis(pt.dateStr)
                                            if (ptTime > 0L) {
                                                val diff = kotlin.math.abs(ptTime - selectedMillis)
                                                if (diff < minDiff) {
                                                    minDiff = diff
                                                    closestIdx = idx
                                                }
                                            }
                                        }
                                        selectedIndexB = closestIdx
                                        currentProgressB = closestIdx.toFloat() / maxOf(1, points.lastIndex)
                                    } else {
                                        customEndDate = selectedMillis
                                    }
                                }
                                showEndDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        androidx.compose.material3.DatePicker(state = datePickerState)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("FROM DATE", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DateRange, contentDescription=null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                val displayStart = if (isDualMarkerMode) {
                                    val idx = minOf(selectedIndexA ?: 0, selectedIndexB ?: 0).coerceIn(0, maxOf(0, points.lastIndex))
                                    points.getOrNull(idx)?.dateStr ?: "Select Start"
                                } else {
                                    if (customStartDate != null) {
                                        val df = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        df.format(java.util.Date(customStartDate!!))
                                    } else {
                                        "Select Start"
                                    }
                                }
                                Text(
                                    text = displayStart,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(0.1f).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Card(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("TO DATE", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DateRange, contentDescription=null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                val displayEnd = if (isDualMarkerMode) {
                                    val idx = maxOf(selectedIndexA ?: 0, selectedIndexB ?: 0).coerceIn(0, maxOf(0, points.lastIndex))
                                    points.getOrNull(idx)?.dateStr ?: "Select End"
                                } else {
                                    if (customEndDate != null) {
                                        val df = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        df.format(java.util.Date(customEndDate!!))
                                    } else {
                                        "Select End"
                                    }
                                }
                                Text(
                                    text = displayEnd,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            GaugeComponent(points, isDark)
            ActionableStructuralDirectivesComponent(points, cross?.type ?: "", isDark)
            Spacer(modifier = Modifier.height(16.dp))
            DrawdownCyclesComponent(points, isDark)
            }
            item {
            RallyCyclesComponent(points, isDark)
            }
            item {
            WeekdayDistributionComponent(points, isDark)
            }
            item {
            VolatilityGridComponent(points, isDark)
            }
            item {
            SipReturnsComponent(
                points,
                isDark,
                onGraphClick = onSipAnalyzerClick,
                onWeeklyAnalyzerClick = onWeeklyAnalyzerClick,
                onRollingWeeklyAnalyzerClick = onRollingWeeklyAnalyzerClick,
                onRollingDailyAnalyzerClick = onRollingDailyAnalyzerClick,
                onDailySipAnalysisClick = onDailySipAnalysisClick
            )
            }
            item {
            TrailingReturnsComponent(pointsRaw, isDark)
            }
            item {
            RollingReturnsComponent(points, isDark, onGraphClick = onRollingReturnsGraphClick)
            }
            item {
            ForecastAndConsistencyComponent(
                points = pointsRaw,
                isDark = isDark,
                onGraphClick = onForecastGraphClick,
                onRankScoreClick = onRankScoreClick,
                onSmartRecommendationClick = onSmartRecommendationClick
            )
            }
        }
    }
}

@Composable
fun InteractiveTimelineChart(
    points: List<TimelineChartPoint>,
    avgNAV: Double?,
    crossEvent: CrossEvent?,
    fastPeriod: Int,
    slowPeriod: Int,
    isDark: Boolean,
    isDualMarkerMode: Boolean,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    selectedIndexA: Int?,
    selectedIndexB: Int?,
    currentProgressA: Float,
    currentProgressB: Float,
    onUpdateCompareIndices: (Int?, Int?, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val navColor = Color(0xFF64B5F6) // Light Blue
    val fastSmaColor = Color(0xFF4CAF50) // Green
    val slowSmaColor = Color(0xFFF44336) // Red
    val trendColor = Color(0xFFFF4081) // Brighter Pink for score

    val currentPoints by androidx.compose.runtime.rememberUpdatedState(points)
    val currentIsDualMarkerMode by androidx.compose.runtime.rememberUpdatedState(isDualMarkerMode)
    val currentSelectedIndexA by androidx.compose.runtime.rememberUpdatedState(selectedIndexA)
    val currentSelectedIndexB by androidx.compose.runtime.rememberUpdatedState(selectedIndexB)
    val currentProgressAState by androidx.compose.runtime.rememberUpdatedState(currentProgressA)
    val currentProgressBState by androidx.compose.runtime.rememberUpdatedState(currentProgressB)
    val currentOnSelectIndex by androidx.compose.runtime.rememberUpdatedState(onSelectIndex)
    val currentOnUpdateCompareIndices by androidx.compose.runtime.rememberUpdatedState(onUpdateCompareIndices)

    var activeDragMarker by remember { mutableStateOf<String?>(null) }
    
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (!currentIsDualMarkerMode) {
                            currentOnSelectIndex(null)
                        }
                    },
                    onTap = { offset ->
                        val pts = currentPoints
                        if (pts.isEmpty()) return@detectTapGestures
                        val usableW = size.width - 80f
                        val progress = ((offset.x - 40f) / usableW).coerceIn(0f, 1f)
                        val idx = ((pts.size - 1) * progress)
                            .roundToInt()
                            .coerceIn(0, pts.lastIndex)
                            
                        if (currentIsDualMarkerMode) {
                            if (currentSelectedIndexA == null) {
                                currentOnUpdateCompareIndices(idx, currentSelectedIndexB, progress, currentProgressBState)
                            } else if (currentSelectedIndexB == null) {
                                currentOnUpdateCompareIndices(currentSelectedIndexA, idx, currentProgressAState, progress)
                            } else {
                                val distA = abs(progress - currentProgressAState)
                                val distB = abs(progress - currentProgressBState)
                                if (distA < distB) {
                                    currentOnUpdateCompareIndices(idx, currentSelectedIndexB, progress, currentProgressBState)
                                } else {
                                    currentOnUpdateCompareIndices(currentSelectedIndexA, idx, currentProgressAState, progress)
                                }
                            }
                        } else {
                            currentOnSelectIndex(idx)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val pts = currentPoints
                        if (pts.isEmpty()) return@detectHorizontalDragGestures
                        val usableW = size.width - 80f
                        val progress = ((offset.x - 40f) / usableW).coerceIn(0f, 1f)
                        if (currentIsDualMarkerMode) {
                            val distA = abs(progress - currentProgressAState)
                            val distB = abs(progress - currentProgressBState)
                            activeDragMarker = if (distA < distB) "A" else "B"
                            
                            val idx = ((pts.size - 1) * progress).roundToInt().coerceIn(0, pts.lastIndex)
                            if (activeDragMarker == "A") {
                                currentOnUpdateCompareIndices(idx, currentSelectedIndexB, progress, currentProgressBState)
                            } else {
                                currentOnUpdateCompareIndices(currentSelectedIndexA, idx, currentProgressAState, progress)
                            }
                        } else {
                            activeDragMarker = "single"
                            val idx = ((pts.size - 1) * progress).roundToInt().coerceIn(0, pts.lastIndex)
                            currentOnSelectIndex(idx)
                        }
                    },
                    onDragEnd = { activeDragMarker = null },
                    onDragCancel = { activeDragMarker = null },
                    onHorizontalDrag = { change, dragAmount -> 
                        val dx = abs(change.position.x - change.previousPosition.x)
                        val dy = abs(change.position.y - change.previousPosition.y)
                        if (dx > dy) {
                            change.consume()
                        }
                        val pts = currentPoints
                        if (pts.isEmpty()) return@detectHorizontalDragGestures
                        val usableW = size.width - 80f
                        val progress = ((change.position.x - 40f) / usableW).coerceIn(0f, 1f)
                        val idx = ((pts.size - 1) * progress).roundToInt().coerceIn(0, pts.lastIndex)
                        if (currentIsDualMarkerMode) {
                            if (activeDragMarker == "A") {
                                currentOnUpdateCompareIndices(idx, currentSelectedIndexB, progress, currentProgressBState)
                            } else if (activeDragMarker == "B") {
                                currentOnUpdateCompareIndices(currentSelectedIndexA, idx, currentProgressAState, progress)
                            }
                        } else {
                            currentOnSelectIndex(idx)
                        }
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val paddingX = 40f
        val paddingYBottom = 60f // Space for x-axis labels
        val chartHeight = h - paddingYBottom
        val usableW = w - 2 * paddingX
        val usableH = chartHeight - 75f // 75f top margin for labels

        val maxNav = points.maxOf { maxOf(it.nav, it.fastSma ?: it.nav, it.slowSma ?: it.nav, it.sma200 ?: it.nav, it.trend) }
        val minNav = points.minOf { minOf(it.nav, it.fastSma ?: it.nav, it.slowSma ?: it.nav, it.sma200 ?: it.nav, it.trend) }
        val diff = if (maxNav > minNav) maxNav - minNav else 1e-6
        val padY = diff * 0.1
        val yMax = maxNav + padY
        val yMin = minNav - padY
        val yRnge = yMax - yMin

        fun getY(value: Double) = 75f + usableH - (((value - yMin) / yRnge) * usableH).toFloat()
        fun getX(index: Int) =
            paddingX + (index.toFloat() / (points.size - 1).coerceAtLeast(1)) * usableW

        val gridColor =
            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
        val gridStroke =
            Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))

        // Draw 4 horizontal grid lines
        for (i in 0..4) {
            val yLines = 75f + (usableH / 4) * i
            drawLine(
                gridColor,
                Offset(paddingX, yLines),
                Offset(w - paddingX, yLines),
                strokeWidth = 1f,
                pathEffect = gridStroke.pathEffect
            )
        }

        // Avg NAV Line
        if (avgNAV != null) {
            val yAvg = getY(avgNAV)
            drawLine(
                color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f),
                start = Offset(paddingX, yAvg),
                end = Offset(w - paddingX, yAvg),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )

            // Avg NAV Pill
            val lastPt = points.last()
            val pdNav = ((lastPt.nav - avgNAV) / avgNAV) * 100.0
            val pdFast =
                if (lastPt.fastSma != null) ((lastPt.fastSma - avgNAV) / avgNAV) * 100.0 else 0.0
            val pdSlow =
                if (lastPt.slowSma != null) ((lastPt.slowSma - avgNAV) / avgNAV) * 100.0 else 0.0

            val pillStr = buildAnnotatedString {
                val labelCol = if (isDark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
                withStyle(SpanStyle(color = labelCol)) {
                    append(String.format(Locale.US, "Avg NAV %.2f (", avgNAV))
                }
                fun c(v: Double) =
                    if (v >= 0) (if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)) else (if (isDark) Color(
                        0xFFFF5252
                    ) else Color(0xFFD32F2F))

                fun f(v: Double) = String.format(Locale.US, "%+.2f%%", v)
                withStyle(SpanStyle(color = c(pdNav))) { append(f(pdNav)) }
                withStyle(SpanStyle(color = labelCol)) { append(", ") }
                withStyle(SpanStyle(color = c(pdFast))) { append(f(pdFast)) }
                withStyle(SpanStyle(color = labelCol)) { append(", ") }
                withStyle(SpanStyle(color = c(pdSlow))) { append(f(pdSlow)) }
                withStyle(SpanStyle(color = labelCol)) { append(")") }
            }

            // Draw pill bg
            val txtSize = textMeasurer.measure(pillStr)
            drawRoundRect(
                color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFEEEEEE),
                topLeft = Offset(paddingX, 10f),
                size = androidx.compose.ui.geometry.Size(
                    txtSize.size.width + 20f,
                    txtSize.size.height + 10f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
            )
            drawRoundRect(
                color = if (isDark) Color(0xFF333333) else Color(0xFFCCCCCC),
                topLeft = Offset(paddingX, 10f),
                size = androidx.compose.ui.geometry.Size(
                    txtSize.size.width + 20f,
                    txtSize.size.height + 10f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                style = Stroke(1f)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = pillStr,
                topLeft = Offset(paddingX + 10f, 15f)
            )
        }

        val pathNav = Path()
        val pathFast = Path()
        val pathSlow = Path()
        val pathSma200 = Path()
        val pathTrend = Path()

        var fastStarted = false
        var slowStarted = false
        var sma200Started = false
        var trendStarted = false

        for (i in points.indices) {
            val pt = points[i]
            val x = getX(i)

            if (i == 0) pathNav.moveTo(x, getY(pt.nav))
            else pathNav.lineTo(x, getY(pt.nav))

            if (pt.fastSma != null) {
                val yFast = getY(pt.fastSma)
                if (!fastStarted) {
                    pathFast.moveTo(x, yFast)
                    fastStarted = true
                } else {
                    pathFast.lineTo(x, yFast)
                }
            }

            if (pt.slowSma != null) {
                val ySlow = getY(pt.slowSma)
                if (!slowStarted) {
                    pathSlow.moveTo(x, ySlow)
                    slowStarted = true
                } else {
                    pathSlow.lineTo(x, ySlow)
                }
            }

            if (pt.sma200 != null) {
                val ySma200 = getY(pt.sma200)
                if (!sma200Started) {
                    pathSma200.moveTo(x, ySma200)
                    sma200Started = true
                } else {
                    pathSma200.lineTo(x, ySma200)
                }
            }
            
            val yTrend = getY(pt.trend)
            if (!trendStarted) {
                pathTrend.moveTo(x, yTrend)
                trendStarted = true
            } else {
                pathTrend.lineTo(x, yTrend)
            }
        }

        // Draw selection fill area if in dual mode
        if (isDualMarkerMode && selectedIndexA != null && selectedIndexB != null) {
            val i1 = minOf(selectedIndexA, selectedIndexB)
            val i2 = maxOf(selectedIndexA, selectedIndexB)
            val x1 = getX(i1)
            val x2 = getX(i2)
            drawRect(
                color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f),
                topLeft = Offset(x1, 75f),
                size = androidx.compose.ui.geometry.Size(x2 - x1, usableH)
            )
        }

        clipRect {
            drawPath(
                pathTrend,
                color = trendColor,
                style = Stroke(width = 3.6f, join = StrokeJoin.Round, cap = StrokeCap.Round)
            )
            if (sma200Started) {
                drawPath(
                    pathSma200,
                    color = Color(0xFFFFF176), // Light Yellow
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }
            drawPath(
                pathSlow,
                color = slowSmaColor,
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                )
            )
            drawPath(
                pathFast,
                color = fastSmaColor,
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
            drawPath(
                pathNav,
                color = navColor,
                style = Stroke(width = 4f, join = StrokeJoin.Round, cap = StrokeCap.Round)
            )
        }

        // X-axis Date Labels
        if (points.size >= 3) {
            val labelCol = if (isDark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
            val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val sdfOut = SimpleDateFormat("dd-MMM''yy", Locale.US)

            val indices = listOf(0, points.size / 2, points.lastIndex)
            for (idx in indices) {
                val pt = points[idx]
                val dateFmt = try {
                    val d = sdfIn.parse(pt.dateStr)
                    if (d != null) sdfOut.format(d) else pt.dateStr
                } catch (e: Exception) {
                    try {
                        val d2 = SimpleDateFormat("dd-MMM-yyyy", Locale.US).parse(pt.dateStr)
                        if (d2 != null) sdfOut.format(d2) else pt.dateStr
                    } catch (e2: Exception) {
                        pt.dateStr
                    }
                }
                val txtStr = buildAnnotatedString {
                    withStyle(SpanStyle(color = labelCol, fontSize = 11.sp)) {
                        append(dateFmt)
                    }
                }
                val txt = textMeasurer.measure(txtStr)
                val alignX = when (idx) {
                    0 -> paddingX
                    points.lastIndex -> w - paddingX - txt.size.width
                    else -> getX(idx) - (txt.size.width / 2f)
                }
                drawText(
                    textMeasurer = textMeasurer,
                    text = txtStr,
                    topLeft = Offset(alignX, chartHeight + 12f)
                )
            }
        }

        // Golden/Death cross dot
        if (crossEvent != null) {
            val idx = points.size - 1 - crossEvent.sessionsAgo
            if (idx in points.indices) {
                val pt = points[idx]
                val cy = getY(pt.fastSma ?: pt.nav)
                val cx = getX(idx)
                val cColor =
                    if (crossEvent.type == "GOLDEN_CROSS" || crossEvent.type == "BULLISH_STATE") Color(0xFF4CAF50) else Color(0xFFF44336)
                drawCircle(color = cColor, radius = 8f, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 4f, center = Offset(cx, cy))
            }
        }

        if (isDualMarkerMode && points.isNotEmpty()) {
            val safeA = selectedIndexA?.coerceIn(0, points.lastIndex)
            val safeB = selectedIndexB?.coerceIn(0, points.lastIndex)
            
            val startIdx = if (safeA != null && safeB != null) minOf(
                safeA,
                safeB
            ) else safeA ?: safeB
            val endIdx = if (safeA != null && safeB != null) maxOf(
                safeA,
                safeB
            ) else null

            // 1. Pink shaded rect between markers
            if (startIdx != null && endIdx != null) {
                val x1 = getX(startIdx)
                val x2 = getX(endIdx)
                drawRect(
                    color = Color(0xFFE91E63).copy(alpha = 0.08f),
                    topLeft = Offset(x1, 75f),
                    size = androidx.compose.ui.geometry.Size(x2 - x1, chartHeight - 75f)
                )
            } else if (startIdx != null) {
                val x1 = getX(startIdx)
                drawRect(
                    color = Color(0xFF1565C0).copy(alpha = 0.04f),
                    topLeft = Offset(x1, 75f),
                    size = androidx.compose.ui.geometry.Size(w - paddingX - x1, chartHeight - 75f)
                )
            }

            // 2. Marker A vertical line + dot (always green)
            if (startIdx != null) {
                val x = getX(startIdx)
                drawLine(
                    Color(0xFF4CAF50),
                    Offset(x, 75f),
                    Offset(x, chartHeight),
                    strokeWidth = 3f
                )
                val yNav = getY(points[startIdx].nav)
                drawCircle(Color(0xFF4CAF50), radius = 8f, center = Offset(x, yNav))
                drawCircle(Color.White, radius = 3f, center = Offset(x, yNav))
            }

            // 3. Marker B vertical line + dot (always pink)
            if (endIdx != null) {
                val x = getX(endIdx)
                drawLine(
                    Color(0xFFE91E63),
                    Offset(x, 75f),
                    Offset(x, chartHeight),
                    strokeWidth = 3f
                )
                val yNav = getY(points[endIdx].nav)
                drawCircle(Color(0xFFE91E63), radius = 8f, center = Offset(x, yNav))
                drawCircle(Color.White, radius = 3f, center = Offset(x, yNav))
            }
        } else {
            // Touch Cursor (Single Mode)
            val effectiveIdx = if (selectedIndex != null && selectedIndex in points.indices) selectedIndex else points.lastIndex
            if (effectiveIdx >= 0 && effectiveIdx < points.size) {
                val ix = effectiveIdx
                val pt = points[ix]
                val cx = getX(ix)

                drawRect(
                    color = Color(0xFF1565C0).copy(alpha = 0.04f),
                    topLeft = Offset(cx, 75f),
                    size = androidx.compose.ui.geometry.Size(w - paddingX - cx, chartHeight - 75f)
                )

                drawLine(
                    color = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
                    start = Offset(cx, 65f),
                    end = Offset(cx, chartHeight + 10f),
                    strokeWidth = 2f
                )

                // Trend dot
                val cTrendY = getY(pt.trend)
                drawCircle(color = trendColor, radius = 5f, center = Offset(cx, cTrendY))

                // 200 SMA
                if (pt.sma200 != null) drawCircle(
                    color = Color(0xFFFFF176),
                    radius = 5f,
                    center = Offset(cx, getY(pt.sma200))
                )
                // Slow
                if (pt.slowSma != null) drawCircle(
                    color = slowSmaColor,
                    radius = 5f,
                    center = Offset(cx, getY(pt.slowSma))
                )
                // Fast
                if (pt.fastSma != null) drawCircle(
                    color = fastSmaColor,
                    radius = 5f,
                    center = Offset(cx, getY(pt.fastSma))
                )
                // Nav (double dot)
                drawCircle(color = navColor, radius = 7f, center = Offset(cx, getY(pt.nav)))
                drawCircle(color = Color.White, radius = 2.5f, center = Offset(cx, getY(pt.nav)))
            }
        }
    }
}

@Composable
fun TooltipDetailRow(
    point: TimelineChartPoint,
    isDark: Boolean,
    fastPeriod: Int,
    slowPeriod: Int,
    onClear: () -> Unit
) {
    val navColor = Color(0xFF64B5F6) // Light Blue
    val fastSmaColor = Color(0xFF4CAF50) // Green
    val slowSmaColor = Color(0xFFF44336) // Red
    val trendColor = Color(0xFFFF4081) // Brighter Pink for score

    val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sdfOut = SimpleDateFormat("dd MMMM yyyy", Locale.US)
    val dStr = try {
        val d = sdfIn.parse(point.dateStr)
        if (d != null) sdfOut.format(d) else point.dateStr
    } catch (e: Exception) {
        try {
            val d2 = SimpleDateFormat("dd-MMM-yyyy", Locale.US).parse(point.dateStr)
            if (d2 != null) sdfOut.format(d2) else point.dateStr
        } catch (e2: Exception) {
            point.dateStr
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E1E1E) else Color(
                0xFFF5F5F5
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    dStr,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Clear selection",
                    color = MaterialTheme.colorScheme.primary, // Orange text
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onClear() }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell("NAV", navColor, String.format(Locale.US, "₹%.4f", point.nav))
                MetricCell(
                    "${fastPeriod}-d SMA",
                    fastSmaColor,
                    if (point.fastSma != null) String.format(Locale.US, "₹%.4f", point.fastSma) else "-"
                )
                MetricCell(
                    "${slowPeriod}-d SMA",
                    slowSmaColor,
                    if (point.slowSma != null) String.format(Locale.US, "₹%.4f", point.slowSma) else "-"
                )
                MetricCell(
                    "Trend Score",
                    trendColor,
                    String.format(Locale.US, "%.1f", point.trendScore),
                    valueColor = if (point.trendScore >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            val (bulletCol, txt) = if (point.fastSma != null && point.slowSma != null) {
                val diffRatio = ((point.fastSma - point.slowSma) / point.slowSma) * 100.0
                val leading = diffRatio >= 0.0
                Pair(if (leading) Color(0xFF4CAF50) else Color(0xFFF44336),
                    "Fast SMA is ${if (leading) "leading" else "trailing"} Slow SMA by ${String.format(Locale.US, "%+.2f%%", diffRatio)}")
            } else {
                Pair(Color.Gray, "Insufficient historical data for trend signal calculation")
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = 0.15f
                    )
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(modifier = Modifier.size(8.dp)) { drawCircle(bulletCol) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(txt, color = bulletCol, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun CompareDetailRow(
    pointStart: TimelineChartPoint,
    pointEnd: TimelineChartPoint,
    unitsHeld: Double,
    isDark: Boolean,
    onStartClick: (() -> Unit)? = null,
    onEndClick: (() -> Unit)? = null
) {
    val startNav = pointStart.nav
    val endNav = pointEnd.nav
    val absChange = endNav - startNav
    val pctChange = (absChange / startNav) * 100.0

    val df2 = java.text.DecimalFormat("0.00")
    val df4 = java.text.DecimalFormat("0.0000")

    val growthValue = unitsHeld * absChange

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(bottom = 16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Historical Period Comparison", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = onStartClick != null) { onStartClick?.invoke() }
                        .padding(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("STARTING POINT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (onStartClick != null) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Change Start Date", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        }
                    }
                    Text("(${pointStart.dateStr})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("₹${df4.format(startNav)}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = onEndClick != null) { onEndClick?.invoke() }
                        .padding(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (onEndClick != null) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Change End Date", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        }
                        Text("ENDING POINT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("(${pointEnd.dateStr})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("₹${df4.format(endNav)}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f)))
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Absolute Valuation Change", color = MaterialTheme.colorScheme.onSurface)
                Text(
                    (if (absChange >= 0) "₹" else "-₹") + df4.format(abs(absChange)),
                    color = if (absChange >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Period Return Percentage", color = MaterialTheme.colorScheme.onSurface)
                Text(
                    (if (pctChange >= 0) "+" else "") + df2.format(pctChange) + "%",
                    color = if (pctChange >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("YOUR INVESTMENT ANALYSIS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Units Held", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(df4.format(unitsHeld), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Est. Period Growth Value", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        (if (growthValue >= 0) "+₹" else "-₹") + df2.format(abs(growthValue)),
                        color = if (growthValue >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCell(label: String, color: Color, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(6.dp)) { drawCircle(color) }
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Text(
            value,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

enum class StanceType { ACCUMULATE, STEADY, CASH_RESERVE }
data class StrategicStance(val type: StanceType, val text: String)

fun calculateStance(diffSlowSmaPct: Double, signal: String): StrategicStance {
    if (diffSlowSmaPct <= -5.0)
        return StrategicStance(
            StanceType.ACCUMULATE,
            "CRUCIAL BUYING WINDOW: The fund NAV sits heavily discounted below long-term " +
            "averages. Allocate any sidelined idle balances or execute lump-sum buying " +
            "immediately."
        )
    if (diffSlowSmaPct >= 12.0 && (signal == "Death Cross" || signal == "Bearish Alignment"))
        return StrategicStance(
            StanceType.CASH_RESERVE,
            "WARNING STRATEGIC CONSOLIDATION: Valuation indicators are extremely " +
            "overextended alongside negative SMA crossovers. Pause all new allotments, " +
            "accumulate cash reserves, or rebalance gains."
        )
    if (diffSlowSmaPct >= 12.0)
        return StrategicStance(
            StanceType.CASH_RESERVE,
            "VALUATION BUFFER WARNING: The fund sits highly overextended above historic " +
            "fair boundaries. Restrain fresh lump-sum entries, continuing with automated " +
            "SIP layouts until bounds recede."
        )
    return StrategicStance(
        StanceType.STEADY,
        "STABLE SYSTEMATIC OUTLINE: Compounding structures are healthy and trading " +
        "securely near stable benchmarks. Broad trend alignment is favorable; continue " +
        "standard SIP buying comfortably."
    )
}

fun getGaugeColor(diffPct: Double, isDark: Boolean): Long = when {
    diffPct >=  12.0  -> if (isDark) 0xFFB00020L else 0xFFD32F2FL
    diffPct >=   5.0  -> if (isDark) 0xFFFFB300L else 0xFFFFC107L
    diffPct >=  -5.0  -> if (isDark) 0xFFFFFFFFL else 0xFFE0E0E0L
    else              -> if (isDark) 0xFFFF8A80L else 0xFFFFAB91L
}

fun overextended(diffPct: Double): Pair<String, String> = when {
    diffPct >=  12.0  -> "Highly Overextended"     to "Current NAV is trading ${String.format(Locale.US, "%.1f", diffPct)}% above its slow-moving average. This is historically overbought territory — consider partial profit booking or systemizing SIPs rather than lumping in here."
    diffPct >=   5.0  -> "Healthy Momentum"        to "Current NAV is trading ${String.format(Locale.US, "%.1f", diffPct)}% above its slow trendline, showcasing a stable and active growth structure with solid buying support."
    diffPct >=  -5.0  -> "Fairly Valued Base"      to "Current NAV is closely aligned with its slow trendline (difference of ${String.format(Locale.US, "%.1f", abs(diffPct))}%). This represents a highly sustainable accumulation zone."
    else              -> "Discounted Oversold"     to "Current NAV is trading ${String.format(Locale.US, "%.1f", abs(diffPct))}% below its slow-moving average. An outstanding margin-of-safety buy window is open!"
}

@Composable
fun GaugeComponent(points: List<TimelineChartPoint>, isDark: Boolean) {
    if (points.isEmpty()) return
    val lastPt = points.last()
    val diffPct = if (lastPt.slowSma != null && lastPt.slowSma > 0) ((lastPt.nav - lastPt.slowSma) / lastPt.slowSma) * 100.0 else 0.0

    val (titleTxt, descTxt) = overextended(diffPct)
    val colorLong = getGaugeColor(diffPct, isDark)
    val needleColor = Color(colorLong)
    val titleColor = needleColor

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Momentum Alignment Gauge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Indicates whether price momentum is overbought, fair value, or a high-discount accumulation safety zone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.padding(bottom = 16.dp, top = 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Canvas(modifier = Modifier.size(240.dp, 120.dp)) {
                        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        
                        val rect = androidx.compose.ui.geometry.Rect(
                            left = 8.dp.toPx(),
                            top = 8.dp.toPx(),
                            right = size.width - 8.dp.toPx(),
                            bottom = size.height * 2 - 8.dp.toPx()
                        )
                        
                        val gradient = Brush.sweepGradient(
                            0.5f to Color(0xFFD32F2F),   // Red
                            0.7f to Color(0xFFFFC107),   // Yellow
                            0.9f to Color(0xFF388E3C),   // Green
                            1.0f to Color(0xFF7B1FA2),   // Purple
                            center = Offset(size.width / 2, size.height)
                        )
                        
                        drawArc(
                            brush = gradient,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = stroke,
                            topLeft = Offset(rect.left, rect.top),
                            size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                        )

                        val positionFraction = ((diffPct + 10.0) / 25.0).coerceIn(0.0, 1.0)
                        val needleAngleRad = Math.toRadians(180.0 + positionFraction * 180.0)
                        
                        val cx = size.width / 2
                        val cy = size.height
                        val radius = size.width / 2 - 8.dp.toPx()
                        
                        val pointerX = (cos(needleAngleRad) * (radius - 20.dp.toPx())) + cx
                        val pointerY = (sin(needleAngleRad) * (radius - 20.dp.toPx())) + cy

                        drawLine(
                            color = needleColor,
                            start = Offset(cx, cy),
                            end = Offset(pointerX.toFloat(), pointerY.toFloat()),
                            strokeWidth = 4.dp.toPx(), // Thinner needle
                            cap = StrokeCap.Round
                        )
                        drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(cx, cy))
                    }
                }

                Text(
                    "Momentum: $titleTxt",
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    descTxt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ActionableStructuralDirectivesComponent(points: List<TimelineChartPoint>, signal: String, isDark: Boolean) {
    if (points.isEmpty()) return
    val lastPt = points.last()
    val diffPct = if (lastPt.slowSma != null && lastPt.slowSma > 0) ((lastPt.nav - lastPt.slowSma) / lastPt.slowSma) * 100.0 else 0.0

    val stance = calculateStance(diffPct, signal)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Actionable Structural Directives", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(12.dp))
            val split = stance.text.split(":")
            val boldPart = if (split.size > 1) split[0] + ":" else ""
            val restPart = if (split.size > 1) split.subList(1, split.size).joinToString(":").trim() else stance.text

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(boldPart)
                    }
                    if (boldPart.isNotEmpty()) append(" ")
                    append(restPart)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            StanceHorizontalProgress(stance.type, isDark)
        }
    }
}

@Composable
fun StanceHorizontalProgress(activeStance: StanceType, isDark: Boolean) {
    val labels = listOf("Accumulate/Buy", "Hold/Steady", "Defensive Cash")
    val colors = listOf(
        Color(if (isDark) 0xFFFFB300 else 0xFFFFC107),
        Color(if (isDark) 0xFFFFFFFF else 0xFFE0E0E0),
        Color(if (isDark) 0xFFFF8A80 else 0xFFFFAB91),
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        for (i in 0..2) {
            val isActive = (i == 0 && activeStance == StanceType.ACCUMULATE) ||
                           (i == 1 && activeStance == StanceType.STEADY) ||
                           (i == 2 && activeStance == StanceType.CASH_RESERVE)
            
            val bgColor = if (isActive) colors[i] else Color(if (isDark) 0xFF333333 else 0xFFE0E0E0).copy(alpha = 0.5f)
            val textColor = if (isActive) {
                if (i == 1) Color.Black else if (isDark) Color.Black else Color.White
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bgColor,
                modifier = Modifier.weight(1f).height(32.dp).padding(end = if (i < 2) 4.dp else 0.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = labels[i],
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun ActionChip(label: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UnderstandingVisualsComponent() {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text("Understanding the Trend Visuals", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LegendItem(Color(0xFF64B5F6), "Asset Price Line (NAV)", "The actual, current net asset value of the fund. Fluctuates with day-to-day market events.")
        LegendItem(Color(0xFF4CAF50), "20-Day SMA (Fast)", "Short-term trend indicator. Reacts quickly to recent price changes. Useful for spotting early momentum shifts.")
        LegendItem(Color(0xFFF44336), "50-Day SMA (Slow)", "Long-term trend indicator. Filters out daily noise to reveal the broader, underlying market direction.")
        LegendItem(Color(0xFFFFF176), "200-Day SMA", "Displayed in light yellow on the graph for informational reference when 200+ data points are available.")
        LegendItem(Color(0xFFFF4081), "Trend Score", "The percentage difference between the current NAV and the 50-day SMA. Helps identify overbought or oversold extremes.")
    }
}

@Composable
fun LegendItem(color: Color, title: String, desc: String) {
    Row(modifier = Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Canvas(modifier = Modifier.padding(top = 6.dp).size(8.dp)) { drawCircle(color) }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class DailyMove(
    val changePct: Double,
    val dateStr: String
)

data class DrawdownCycle(
    val maxFallPct: Double,
    val peakDateStr: String,
    val troughDateStr: String,
    val peakNav: Double,
    val troughNav: Double,
    val fallDays: Long,
    val recoveryDateStr: String?,
    val recoveryDays: Long?,
    val totalDays: Long,
    val isRecovered: Boolean,
    val top3Losses: List<DailyMove>
)

private fun parseDate(dateStr: String): java.util.Date? {
    return try {
        java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.US).parse(dateStr)
    } catch (e: Exception) {
        try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateStr)
        } catch (e2: Exception) {
            null
        }
    }
}

private fun getDaysDiff(startStr: String, endStr: String): Long {
    val start = parseDate(startStr) ?: return 1L
    val end = parseDate(endStr) ?: return 1L
    val diffMs = end.time - start.time
    return java.lang.Math.max(1L, java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMs))
}

private fun formatDateStr(dateStr: String): String {
    val date = parseDate(dateStr) ?: return dateStr
    return java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(date)
}

private fun getCycleTop3Moves(
    data: List<TimelineChartPoint>,
    startIdx: Int,
    endIdx: Int,
    isGain: Boolean
): List<DailyMove> {
    val moves = mutableListOf<DailyMove>()
    for (i in startIdx + 1..endIdx) {
        if (i < data.size) {
            val prevNav = data[i-1].nav
            val currNav = data[i].nav
            if (prevNav > 0.0) {
                val pctChange = (currNav - prevNav) / prevNav * 100.0
                moves.add(DailyMove(pctChange, data[i].dateStr))
            }
        }
    }
    return if (isGain) {
        moves.sortedByDescending { it.changePct }.take(3)
    } else {
        moves.sortedBy { it.changePct }.take(3)
    }
}

fun findDrawdownCycles(
    data: List<TimelineChartPoint>,
    minFallPct: Double = 0.0,
    topN: Int = 3
): List<DrawdownCycle> {
    if (data.size < 3) return emptyList()

    var peakIdx = 0
    var peakNav = data[0].nav
    var troughIdx: Int? = null
    var troughNav: Double? = null
    var inCycle = false
    val cycles = mutableListOf<DrawdownCycle>()

    for (i in 1 until data.size) {
        val nav = data[i].nav

        if (!inCycle) {
            if (nav > peakNav) {
                peakNav = nav
                peakIdx = i
            } else if ((peakNav - nav) / peakNav * 100.0 >= minFallPct) {
                inCycle = true
                troughNav = nav
                troughIdx = i
            }
        } else { // we are in a cycle
            if (troughNav == null || nav < troughNav) {
                troughNav = nav
                troughIdx = i
            }
            if (nav >= peakNav) {
                val peakDate = data[peakIdx].dateStr
                val troughDate = data[troughIdx!!].dateStr
                val recoveryDate = data[i].dateStr

                val fallDays = getDaysDiff(peakDate, troughDate)
                val recoveryDays = getDaysDiff(troughDate, recoveryDate)
                val totalDays = getDaysDiff(peakDate, recoveryDate)

                val top3Losses = getCycleTop3Moves(data, peakIdx, i, isGain = false)

                cycles.add(
                    DrawdownCycle(
                        maxFallPct = (peakNav - troughNav) / peakNav * 100.0,
                        peakDateStr = peakDate,
                        troughDateStr = troughDate,
                        peakNav = peakNav,
                        troughNav = troughNav,
                        fallDays = fallDays,
                        recoveryDateStr = recoveryDate,
                        recoveryDays = recoveryDays,
                        totalDays = totalDays,
                        isRecovered = true,
                        top3Losses = top3Losses
                    )
                )

                peakIdx = i
                peakNav = nav
                troughIdx = null
                troughNav = null
                inCycle = false
            }
        }
    }

    if (inCycle && troughIdx != null) {
        val peakDate = data[peakIdx].dateStr
        val troughDate = data[troughIdx!!].dateStr
        val lastDate = data.last().dateStr

        val fallDays = getDaysDiff(peakDate, troughDate)
        val totalDays = getDaysDiff(peakDate, lastDate)

        val top3Losses = getCycleTop3Moves(data, peakIdx, data.size - 1, isGain = false)

        cycles.add(
            DrawdownCycle(
                maxFallPct = (peakNav - troughNav!!) / peakNav * 100.0,
                peakDateStr = peakDate,
                troughDateStr = troughDate,
                peakNav = peakNav,
                troughNav = troughNav,
                fallDays = fallDays,
                recoveryDateStr = null,
                recoveryDays = null,
                totalDays = totalDays,
                isRecovered = false,
                top3Losses = top3Losses
            )
        )
    }

    cycles.sortByDescending { it.maxFallPct }
    return cycles.take(topN)
}

data class RallyCycle(
    val maxRisePct: Double,
    val troughDateStr: String,
    val peakDateStr: String,
    val troughNav: Double,
    val peakNav: Double,
    val riseDays: Long,
    val retracementDateStr: String?,
    val retracementDays: Long?,
    val totalDays: Long,
    val isRetraced: Boolean,
    val top3Gains: List<DailyMove>
)

fun findRallyCycles(
    data: List<TimelineChartPoint>,
    minRisePct: Double = 0.0,
    topN: Int = 3
): List<RallyCycle> {
    if (data.size < 3) return emptyList()

    var troughIdx = 0
    var troughNav = data[0].nav
    var peakIdx: Int? = null
    var peakNav: Double? = null
    var inCycle = false
    val cycles = mutableListOf<RallyCycle>()

    for (i in 1 until data.size) {
        val nav = data[i].nav

        if (!inCycle) {
            if (nav < troughNav) {
                troughNav = nav
                troughIdx = i
            } else if ((nav - troughNav) / troughNav * 100.0 >= minRisePct) {
                inCycle = true
                peakNav = nav
                peakIdx = i
            }
        } else {
            if (peakNav == null || nav > peakNav) {
                peakNav = nav
                peakIdx = i
            }
            if (nav <= troughNav) {
                val troughDate = data[troughIdx].dateStr
                val peakDate = data[peakIdx!!].dateStr
                val retracementDate = data[i].dateStr

                val riseDays = getDaysDiff(troughDate, peakDate)
                val retracementDays = getDaysDiff(peakDate, retracementDate)
                val totalDays = getDaysDiff(troughDate, retracementDate)

                val top3Gains = getCycleTop3Moves(data, troughIdx, i, isGain = true)

                cycles.add(
                    RallyCycle(
                        maxRisePct = (peakNav - troughNav) / troughNav * 100.0,
                        troughDateStr = troughDate,
                        peakDateStr = peakDate,
                        troughNav = troughNav,
                        peakNav = peakNav,
                        riseDays = riseDays,
                        retracementDateStr = retracementDate,
                        retracementDays = retracementDays,
                        totalDays = totalDays,
                        isRetraced = true,
                        top3Gains = top3Gains
                    )
                )

                troughIdx = i
                troughNav = nav
                peakIdx = null
                peakNav = null
                inCycle = false
            }
        }
    }

    if (inCycle && peakIdx != null) {
        val troughDate = data[troughIdx].dateStr
        val peakDate = data[peakIdx!!].dateStr
        val lastDate = data.last().dateStr

        val riseDays = getDaysDiff(troughDate, peakDate)
        val totalDays = getDaysDiff(troughDate, lastDate)

        val top3Gains = getCycleTop3Moves(data, troughIdx, data.size - 1, isGain = true)

        cycles.add(
            RallyCycle(
                maxRisePct = (peakNav!! - troughNav) / troughNav * 100.0,
                troughDateStr = troughDate,
                peakDateStr = peakDate,
                troughNav = troughNav,
                peakNav = peakNav,
                riseDays = riseDays,
                retracementDateStr = null,
                retracementDays = null,
                totalDays = totalDays,
                isRetraced = false,
                top3Gains = top3Gains
            )
        )
    }

    cycles.sortByDescending { it.maxRisePct }
    return cycles.take(topN)
}

fun getOpenRallyCycle(points: List<TimelineChartPoint>): RallyCycle? {
    val cycles = findRallyCycles(points, minRisePct = 0.0, topN = Int.MAX_VALUE)
    return cycles.find { !it.isRetraced }
}

fun getOpenDrawdownCycle(points: List<TimelineChartPoint>): DrawdownCycle? {
    val cycles = findDrawdownCycles(points, minFallPct = 0.0, topN = Int.MAX_VALUE)
    return cycles.find { !it.isRecovered }
}

@Composable
fun MutedChip(text: String) {
    Box(
        modifier = Modifier
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TrendingDownIcon(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val arrowPath = Path().apply {
            moveTo(w * 0.15f, h * 0.3f)
            lineTo(w * 0.45f, h * 0.55f)
            lineTo(w * 0.65f, h * 0.4f)
            lineTo(w * 0.85f, h * 0.7f)
        }
        drawPath(
            path = arrowPath,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val headPath = Path().apply {
            moveTo(w * 0.6f, h * 0.7f)
            lineTo(w * 0.85f, h * 0.7f)
            lineTo(w * 0.85f, h * 0.45f)
        }
        drawPath(
            path = headPath,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ShieldIcon(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.2f)
            quadraticTo(w * 0.5f, h * 0.15f, w * 0.8f, h * 0.2f)
            lineTo(w * 0.8f, h * 0.55f)
            quadraticTo(w * 0.8f, h * 0.85f, w * 0.5f, h * 0.95f)
            quadraticTo(w * 0.2f, h * 0.85f, w * 0.2f, h * 0.55f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val checkPath = Path().apply {
            moveTo(w * 0.38f, h * 0.5f)
            lineTo(w * 0.47f, h * 0.6f)
            lineTo(w * 0.65f, h * 0.4f)
        }
        drawPath(
            path = checkPath,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun OldDrawdownCyclesComponent(points: List<TimelineChartPoint>, isDark: Boolean) {
    val cycles = remember(points) {
        findDrawdownCycles(points, minFallPct = 0.0, topN = 3)
    }

    val topOverallLosses = remember(points) {
        val overallMoves = mutableListOf<DailyMove>()
        for (i in 1 until points.size) {
            val prevNav = points[i-1].nav
            val currNav = points[i].nav
            if (prevNav > 0.0) {
                val pctChange = (currNav - prevNav) / prevNav * 100.0
                overallMoves.add(DailyMove(pctChange, points[i].dateStr))
            }
        }
        overallMoves.sortedBy { it.changePct }.take(3)
    }

    // Find the latest chronological cycle for "This Cycle" comparison
    val currentCycleLosses = remember(points) {
        val allChronological = findDrawdownCycles(points, minFallPct = 0.0, topN = Int.MAX_VALUE).sortedBy { it.peakDateStr }
        allChronological.lastOrNull()?.top3Losses ?: emptyList()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF3A2418) else Color(0xFFFFEAD2)),
                        contentAlignment = Alignment.Center
                    ) {
                        TrendingDownIcon(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFFFF7043)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Drawdown Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top 3 Single-Day Losses Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isDark) Color(0xFF262626) else Color(0xFFEEEEEE),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "Top 3 Single-Day Losses",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) Color(0xFFFFCC80) else Color(0xFFE65100)
                )
                Spacer(modifier = Modifier.height(8.dp))

                for (i in 0 until 3) {
                    val overall = topOverallLosses.getOrNull(i)
                    val cycleLoss = currentCycleLosses.getOrNull(i)

                    val overallText = overall?.let {
                        String.format(java.util.Locale.US, "%.2f%% (%s)", it.changePct, formatDateStr(it.dateStr))
                    } ?: "N/A"

                    val cycleText = cycleLoss?.let {
                        String.format(java.util.Locale.US, "%.2f%% (%s)", it.changePct, formatDateStr(it.dateStr))
                    } ?: "N/A"

                    Text(
                        text = "Top Loss #${i + 1}: $overallText — Overall Dataset | $cycleText — This Cycle",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    if (i < 2) Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Largest Historical Drawdown Cycles",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (cycles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShieldIcon(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No drawdowns detected in this period.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                cycles.forEachIndexed { index, cycle ->
                    var expanded by remember { mutableStateOf(index == 0) } // First one expanded by default

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2C) else Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { expanded = !expanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bigNumberColor = when {
                                    cycle.maxFallPct >= 20.0 -> Color(0xFFE57373)
                                    cycle.maxFallPct >= 10.0 -> Color(0xFFFFD54F)
                                    else -> Color(0xFFB9F6CA)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MutedChip(text = "#${index + 1}")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = String.format(java.util.Locale.US, "-%.1f%%", cycle.maxFallPct),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = bigNumberColor
                                    )
                                }

                                androidx.compose.material.icons.Icons.Default.let { icons ->
                                    Icon(
                                        imageVector = if (expanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AnimatedVisibility(visible = expanded) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    Text(
                                        text = "Fell ${String.format(java.util.Locale.US, "%.1f", cycle.maxFallPct)}% from ${formatDateStr(cycle.peakDateStr)} to ${formatDateStr(cycle.troughDateStr)} over ${cycle.fallDays} days.",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = if (isDark) Color.LightGray else Color.DarkGray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (cycle.isRecovered) {
                                        val recDays = cycle.recoveryDays ?: 0L
                                        Text(
                                            text = "Recovered to peak by ${formatDateStr(cycle.recoveryDateStr ?: "")} in $recDays days. Total cycle: ${cycle.totalDays} days.",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = if (isDark) Color.LightGray else Color.DarkGray
                                        )
                                    } else {
                                        Text(
                                            text = "Hasn't recovered yet — ${cycle.totalDays} days from peak.",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = Color(0xFFFFD54F)
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
}

@Composable
fun TrendingUpIcon(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val arrowPath = Path().apply {
            moveTo(w * 0.15f, h * 0.7f)
            lineTo(w * 0.45f, h * 0.45f)
            lineTo(w * 0.65f, h * 0.6f)
            lineTo(w * 0.85f, h * 0.3f)
        }
        drawPath(
            path = arrowPath,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val headPath = Path().apply {
            moveTo(w * 0.6f, h * 0.3f)
            lineTo(w * 0.85f, h * 0.3f)
            lineTo(w * 0.85f, h * 0.55f)
        }
        drawPath(
            path = headPath,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun OldRallyCyclesComponent(points: List<TimelineChartPoint>, isDark: Boolean) {
    val cycles = remember(points) {
        findRallyCycles(points, minRisePct = 0.0, topN = 3)
    }

    val topOverallGains = remember(points) {
        val overallMoves = mutableListOf<DailyMove>()
        for (i in 1 until points.size) {
            val prevNav = points[i-1].nav
            val currNav = points[i].nav
            if (prevNav > 0.0) {
                val pctChange = (currNav - prevNav) / prevNav * 100.0
                overallMoves.add(DailyMove(pctChange, points[i].dateStr))
            }
        }
        overallMoves.sortedByDescending { it.changePct }.take(3)
    }

    val currentCycleGains = remember(points) {
        val allChronological = findRallyCycles(points, minRisePct = 0.0, topN = Int.MAX_VALUE).sortedBy { it.troughDateStr }
        allChronological.lastOrNull()?.top3Gains ?: emptyList()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1B3B2B) else Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        TrendingUpIcon(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Rally / Bull Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top 3 Single-Day Gains Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isDark) Color(0xFF262626) else Color(0xFFEEEEEE),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "Top 3 Single-Day Gains",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(8.dp))

                for (i in 0 until 3) {
                    val overall = topOverallGains.getOrNull(i)
                    val cycleGain = currentCycleGains.getOrNull(i)

                    val overallText = overall?.let {
                        String.format(java.util.Locale.US, "+%.2f%% (%s)", it.changePct, formatDateStr(it.dateStr))
                    } ?: "N/A"

                    val cycleText = cycleGain?.let {
                        String.format(java.util.Locale.US, "+%.2f%% (%s)", it.changePct, formatDateStr(it.dateStr))
                    } ?: "N/A"

                    Text(
                        text = "Top Gain #${i + 1}: $overallText — Overall Dataset | $cycleText — This Cycle",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    if (i < 2) Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Largest Historical Rally Cycles",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (cycles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShieldIcon(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No major rallies detected in this period.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                cycles.forEachIndexed { index, cycle ->
                    var expanded by remember { mutableStateOf(index == 0) }

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2C) else Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { expanded = !expanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bigNumberColor = when {
                                    cycle.maxRisePct >= 50.0 -> Color(0xFF66BB6A)
                                    cycle.maxRisePct >= 20.0 -> Color(0xFF81C784)
                                    else -> Color(0xFFA5D6A7)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MutedChip(text = "#${index + 1}")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = String.format(java.util.Locale.US, "+%.1f%%", cycle.maxRisePct),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = bigNumberColor
                                    )
                                }

                                androidx.compose.material.icons.Icons.Default.let { icons ->
                                    Icon(
                                        imageVector = if (expanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AnimatedVisibility(visible = expanded) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    Text(
                                        text = "Rose ${String.format(java.util.Locale.US, "%.1f", cycle.maxRisePct)}% from ${formatDateStr(cycle.troughDateStr)} to ${formatDateStr(cycle.peakDateStr)} over ${cycle.riseDays} days.",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = if (isDark) Color.LightGray else Color.DarkGray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (cycle.isRetraced) {
                                        val retDays = cycle.retracementDays ?: 0L
                                        Text(
                                            text = "Retraced back to starting level by ${formatDateStr(cycle.retracementDateStr ?: "")} in $retDays days. Total cycle: ${cycle.totalDays} days.",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = if (isDark) Color.LightGray else Color.DarkGray
                                        )
                                    } else {
                                        Text(
                                            text = "Ongoing expansion — holding levels for ${cycle.totalDays} days from trough.",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = Color(0xFF81C784)
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
}

@Composable
fun VolatilityGridComponent(points: List<TimelineChartPoint>, isDark: Boolean) {
    val (avgUp, avgDown, upPct, downPct) = remember(points) {
        val pctChanges = mutableListOf<Double>()
        for (i in 1 until points.size) {
            val prev = points[i - 1].nav
            val curr = points[i].nav
            if (prev > 0.0) {
                pctChanges.add((curr - prev) / prev * 100.0)
            }
        }

        val pos = pctChanges.filter { it > 0 }.sorted()
        val neg = pctChanges.filter { it < 0 }.sorted()

        fun getMiddle80Avg(list: List<Double>): Double {
            if (list.isEmpty()) return 0.0
            val dropCount = (list.size * 0.1).toInt()
            val remaining = list.drop(dropCount).dropLast(dropCount)
            return if (remaining.isNotEmpty()) remaining.average() else 0.0
        }

        val upAvg = getMiddle80Avg(pos)
        val downAvg = getMiddle80Avg(neg)
        val total = pctChanges.size
        val upPctVal = if (total > 0) (pos.size.toDouble() / total) * 100.0 else 0.0
        val downPctVal = if (total > 0) (neg.size.toDouble() / total) * 100.0 else 0.0
        
        listOf(upAvg, downAvg, upPctVal, downPctVal)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Card (Green)
        VolatilityCard(
            title = "Typical Up Swing",
            value = "+${String.format(java.util.Locale.US, "%.2f", avgUp)}%",
            frequency = "${String.format(java.util.Locale.US, "%.1f", upPct)}% of days",
            color = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32),
            isDark = isDark,
            modifier = Modifier.weight(1f)
        )

        // Right Card (Red)
        VolatilityCard(
            title = "Typical Down Swing",
            value = "-${String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(avgDown))}%",
            frequency = "${String.format(java.util.Locale.US, "%.1f", downPct)}% of days",
            color = if (isDark) Color(0xFFF44336) else Color(0xFFD32F2F),
            isDark = isDark,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun VolatilityCard(title: String, value: String, frequency: String, color: Color, isDark: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF15191C) else Color(0xFFF4F6F8)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C3238) else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 20.dp, horizontal = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = frequency,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


private val sdf1 = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue() = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.US)
}
private val sdf2 = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
}

private val timelineDateCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

fun parseTimelineDate(dateStr: String): Long {
    val cached = timelineDateCache[dateStr]
    if (cached != null) return cached
    val parsed = try {
        sdf1.get()?.parse(dateStr)?.time ?: 0L
    } catch (e: Exception) {
        try {
            sdf2.get()?.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    if (parsed > 0L) {
        timelineDateCache[dateStr] = parsed
    }
    return parsed
}


@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun SipReturnsComponent(
    points: List<TimelineChartPoint>,
    isDark: Boolean,
    onGraphClick: () -> Unit = {},
    onWeeklyAnalyzerClick: () -> Unit = {},
    onRollingWeeklyAnalyzerClick: () -> Unit = {},
    onRollingDailyAnalyzerClick: () -> Unit = {},
    onDailySipAnalysisClick: () -> Unit = {}
) {
    if (points.isEmpty()) return
    
    var sipStartDate by remember { mutableStateOf(java.util.Date(parseTimelineDate(points.first().dateStr))) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = sipStartDate.time,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= parseTimelineDate(points.first().dateStr) && utcTimeMillis <= parseTimelineDate(points.last().dateStr)
                }
            }
        )
        
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        sipStartDate = java.util.Date(it)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
    
    data class SipResult(val frequency: String, val absoluteReturn: Double, val xirr: Double)
    
    val sipResults = remember(points, sipStartDate) {
            val mappedPoints = points.map { Pair(it, parseTimelineDate(it.dateStr)) }
            val frequencies = listOf(
                Pair("Daily", 1),
                Pair("Weekly", 7),
                Pair("Biweekly", 14),
                Pair("Monthly", 30),
                Pair("Quarterly", 91)
            )
            
            val lastPoint = mappedPoints.last()
            val lastDate = lastPoint.second
            
            val results = frequencies.map { (name, days) ->
                var totalUnits = 0.0
                var totalInvested = 0.0
                val cashFlows = mutableListOf<Pair<Long, Double>>() // time, amount

                if (name == "Daily") {
                    // Daily SIP only on market/trading days (points with actual NAV)
                    val eligiblePoints = mappedPoints.filter { it.second >= sipStartDate.time }
                    for (rec in eligiblePoints) {
                        val nav = rec.first.nav
                        if (nav > 0.0) {
                            val investedAmount = 1000.0
                            totalUnits += investedAmount / nav
                            totalInvested += investedAmount
                            cashFlows.add(Pair(rec.second, -investedAmount))
                        }
                    }
                } else {
                    var currentSipTime = sipStartDate.time
                    var ptr = 0
                    while (currentSipTime <= lastDate) {
                        // Advance ptr
                        while (ptr < mappedPoints.size - 1 && mappedPoints[ptr + 1].second <= currentSipTime) {
                            ptr++
                        }
                        
                        val windowEnd = currentSipTime + days * 24L * 60 * 60 * 1000L - 1
                        var windowPtr = ptr
                        var maxNavPoint = mappedPoints[ptr]
                        while (windowPtr < mappedPoints.size && mappedPoints[windowPtr].second <= windowEnd) {
                            if (mappedPoints[windowPtr].first.nav > maxNavPoint.first.nav) {
                                maxNavPoint = mappedPoints[windowPtr]
                            }
                            windowPtr++
                        }
                        val navPoint = maxNavPoint
                        
                        val investedAmount = 1000.0
                        val unitsBought = investedAmount / navPoint.first.nav
                        totalUnits += unitsBought
                        totalInvested += investedAmount
                        cashFlows.add(Pair(navPoint.second, -investedAmount))
                        
                        currentSipTime += days * 24L * 60 * 60 * 1000L
                    }
                }
                
                if (totalInvested > 0) {
                    val finalValue = totalUnits * lastPoint.first.nav
                    val absoluteReturn = ((finalValue - totalInvested) / totalInvested) * 100.0
                    
                    cashFlows.add(Pair(lastPoint.second, finalValue))
                    
                    val xirr = calculateXIRR(cashFlows) * 100.0
                    
                    SipResult(name, absoluteReturn, xirr)
                } else {
                    SipResult(name, 0.0, 0.0)
                }
            }
            results
    }
    
    val dateFormatter = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF15191C) else Color(0xFFF4F6F8)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C3238) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SIP returns (%)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = onWeeklyAnalyzerClick,
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("W", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                        Surface(
                            onClick = onRollingWeeklyAnalyzerClick,
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("RW", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                        }
                        Surface(
                            onClick = onRollingDailyAnalyzerClick,
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("RD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                        }
                        Surface(
                            onClick = onGraphClick,
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Default.Add,
                                    contentDescription = "SIP Analyzer",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showDatePicker = true }.padding(4.dp)
                ) {
                    Text(
                        text = dateFormatter.format(sipStartDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.DateRange,
                        contentDescription = "Select start date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Period", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.5f))
                Text("Absolute", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("Annualised", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = if (isDark) Color(0xFF2C3238) else Color(0xFFE2E8F0))
            Spacer(modifier = Modifier.height(8.dp))
            
            if (sipResults.isEmpty()) {
                Text("Not enough data.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sipResults.forEach { data ->
                    val isDaily = data.frequency.equals("Daily", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .then(
                                if (isDaily) {
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onDailySipAnalysisClick() }
                                } else Modifier
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1.5f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = data.frequency,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isDaily) FontWeight.Bold else FontWeight.Normal,
                                    textDecoration = if (isDaily) androidx.compose.ui.text.style.TextDecoration.Underline else null
                                ),
                                color = if (isDaily) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isDaily) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "ANALYZE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", data.absoluteReturn),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", data.xirr),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

fun calculateNPV(rate: Double, cashFlows: List<Pair<Long, Double>>, t0: Long): Double {
    var f = 0.0
    for ((t, c) in cashFlows) {
        val days = (t - t0) / (1000.0 * 60 * 60 * 24)
        val years = days / 365.25
        f += c / Math.pow(1.0 + rate, years)
    }
    return f
}

fun calculateXIRR(cashFlows: List<Pair<Long, Double>>, guess: Double = 0.1): Double {
    if (cashFlows.isEmpty()) return 0.0
    val t0 = cashFlows[0].first
    
    // 1. Newton-Raphson
    var rate = guess
    val maxIter = 100
    val tol = 1e-6
    
    for (i in 0 until maxIter) {
        var f = 0.0
        var df = 0.0
        
        for ((t, c) in cashFlows) {
            val days = (t - t0) / (1000.0 * 60 * 60 * 24)
            val years = days / 365.25
            f += c / Math.pow(1.0 + rate, years)
            df -= years * c / Math.pow(1.0 + rate, years + 1.0)
        }
        
        if (Math.abs(df) < 1e-10) break
        val newRate = rate - f / df
        if (Math.abs(newRate - rate) < tol) {
            return newRate
        }
        rate = newRate
    }
    
    // 2. Fallback: Bisection
    var low = -0.99
    var high = 10.0
    for (i in 0 until 100) {
        val mid = (low + high) / 2
        val npv = calculateNPV(mid, cashFlows, t0)
        if (Math.abs(npv) < tol) return mid
        
        // Determine which side of the root the mid is on
        val npvLow = calculateNPV(low, cashFlows, t0)
        if (npv * npvLow < 0) high = mid else low = mid
    }
    
    return Double.NaN
}

@Composable
fun TrailingReturnsComponent(points: List<TimelineChartPoint>, isDark: Boolean) {
    if (points.isEmpty()) return
    
    data class TrailingData(val periodName: String, val absolute: Double?, val annualised: Double?)
    
    val trailingDataList = remember(points) {
            val mappedPoints = points.map { Pair(it, parseTimelineDate(it.dateStr)) }
            val lastPoint = mappedPoints.last()
            val lastDate = lastPoint.second
            val lastNav = lastPoint.first.nav
            
            val periods = listOf(
                Pair("One week", 7),
                Pair("One month", 30),
                Pair("Three months", 91),
                Pair("Six months", 182),
                Pair("One year", 365),
                Pair("Two years", 730),
                Pair("Three years", 1095),
                Pair("Five years", 1825),
                Pair("Seven years", 2555),
                Pair("Ten years", 3650)
            )
            
            val results = periods.map { (name, days) ->
                val targetTime = lastDate - days * 24L * 60 * 60 * 1000L
                val startPoint = mappedPoints.findLast { it.second <= targetTime }
                
                if (startPoint != null && lastDate > startPoint.second) {
                    val absRet = ((lastNav - startPoint.first.nav) / startPoint.first.nav) * 100.0
                    val actualDays = (lastDate - startPoint.second) / (1000L * 60 * 60 * 24)
                    val annRet = if (actualDays > 0) (Math.pow(lastNav / startPoint.first.nav, 365.25 / actualDays) - 1.0) * 100.0 else 0.0
                    TrailingData(name, absRet, annRet)
                } else {
                    TrailingData(name, null, null)
                }
            }.toMutableList()
            
            // Since inception calculation (from the very first available NAV date)
            val inceptionPoint = mappedPoints.firstOrNull()
            if (inceptionPoint != null && lastDate > inceptionPoint.second && inceptionPoint.first.nav > 0.0) {
                val absRet = ((lastNav - inceptionPoint.first.nav) / inceptionPoint.first.nav) * 100.0
                val actualDays = (lastDate - inceptionPoint.second) / (1000L * 60 * 60 * 24)
                val annRet = if (actualDays > 0) (Math.pow(lastNav / inceptionPoint.first.nav, 365.25 / actualDays) - 1.0) * 100.0 else 0.0
                results.add(TrailingData("Since inception", absRet, annRet))
            } else {
                results.add(TrailingData("Since inception", null, null))
            }
            
            results
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF15191C) else Color(0xFFF4F6F8)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C3238) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = "Trailing returns (%)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Period", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.5f))
                Text("Absolute", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("Annualised", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = if (isDark) Color(0xFF2C3238) else Color(0xFFE2E8F0))
            Spacer(modifier = Modifier.height(8.dp))
            
            if (trailingDataList.isEmpty()) {
                Text("Not enough data.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                trailingDataList.forEach { data ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(data.periodName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.5f))
                        Text(
                            text = data.absolute?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "-",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = data.annualised?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "-",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RollingReturnsComponent(points: List<TimelineChartPoint>, isDark: Boolean, onGraphClick: () -> Unit = {}) {
    if (points.isEmpty()) return
    
    data class RollingStats(
        val periodName: String,
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
    
    val rollingStatsList = remember(points) {
            val mappedPoints = points.map { Pair(it, parseTimelineDate(it.dateStr)) }
            
            val periods = listOf(
                Pair("1 Month", 30),
                Pair("3 Months", 91),
                Pair("6 Months", 182),
                Pair("1 Year", 365),
                Pair("2 Years", 730),
                Pair("3 Years", 1095),
                Pair("5 Years", 1825),
                Pair("7 Years", 2555),
                Pair("10 Years", 3650)
            )
            
            val results = periods.mapNotNull { (name, days) ->
                val returnsList = mutableListOf<Double>()
                var startIndex = 0
                val targetMillis = days * 24L * 60 * 60 * 1000L
                for (i in mappedPoints.indices) {
                    val endPoint = mappedPoints[i]
                    val endPointTime = endPoint.second
                    val targetTime = endPointTime - targetMillis
                    
                    while (startIndex < i && mappedPoints[startIndex + 1].second <= targetTime) {
                        startIndex++
                    }
                    
                    val startPoint = mappedPoints[startIndex]
                    if (startPoint.second <= targetTime && endPointTime > startPoint.second) {
                        val actualDays = (endPointTime - startPoint.second) / (1000L * 60 * 60 * 24)
                        if (actualDays > 0) {
                            val ret = if (days < 360) {
                                (endPoint.first.nav / startPoint.first.nav - 1.0) * 100.0
                            } else {
                                (Math.pow(endPoint.first.nav / startPoint.first.nav, 365.25 / actualDays) - 1.0) * 100.0
                            }
                            returnsList.add(ret)
                        }
                    }
                }
                
                if (returnsList.isNotEmpty()) {
                    returnsList.sort()
                    val mean = returnsList.average()
                    val stdDev = Math.sqrt(returnsList.map { Math.pow(it - mean, 2.0) }.average())
                    val min = returnsList.first()
                    val max = returnsList.last()
                    val median = if (returnsList.size % 2 == 0) {
                        (returnsList[returnsList.size / 2 - 1] + returnsList[returnsList.size / 2]) / 2.0
                    } else {
                        returnsList[returnsList.size / 2]
                    }
                    
                    val total = returnsList.size.toDouble()
                    RollingStats(
                        periodName = name,
                        mean = mean,
                        stdDev = stdDev,
                        min = min,
                        median = median,
                        max = max,
                        below0 = returnsList.count { it < 0 } / total * 100.0,
                        from0To8 = returnsList.count { it in 0.0..8.0 } / total * 100.0,
                        from8To12 = returnsList.count { it > 8.0 && it <= 12.0 } / total * 100.0,
                        from12To16 = returnsList.count { it > 12.0 && it <= 16.0 } / total * 100.0,
                        from16To20 = returnsList.count { it > 16.0 && it <= 20.0 } / total * 100.0,
                        above20 = returnsList.count { it > 20.0 } / total * 100.0
                    )
                } else {
                    null
                }
            }
            results
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF15191C) else Color(0xFFF4F6F8)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C3238) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Rolling returns (%)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onGraphClick) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Rolling Return Graph", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            if (rollingStatsList.isEmpty()) {
                Text(
                    text = "Not enough data for rolling returns.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val scrollState = rememberScrollState()
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                    Column(modifier = Modifier.width(120.dp)) {
                        Text("", style = MaterialTheme.typography.labelMedium, modifier = Modifier.height(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf("Mean", "Std Dev", "Minimum", "Median", "Maximum", "", "Times below 0", "Times 0 to 8", "Times 8 to 12", "Times 12 to 16", "Times 16 to 20", "Times above 20").forEach { label ->
                            if (label.isEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            } else {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                    
                    rollingStatsList.forEach { stats ->
                        Column(modifier = Modifier.width(80.dp), horizontalAlignment = Alignment.End) {
                            Text(stats.periodName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.height(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(stats.mean, stats.stdDev, stats.min, stats.median, stats.max).forEach { value ->
                                Text(String.format(java.util.Locale.US, "%.2f", value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.height(24.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(stats.below0, stats.from0To8, stats.from8To12, stats.from12To16, stats.from16To20, stats.above20).forEach { value ->
                                Text(String.format(java.util.Locale.US, "%.2f", value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
