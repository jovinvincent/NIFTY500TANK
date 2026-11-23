package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class ProjectionPoint(
    val dateStr: String,
    val date: Date,
    val yearOffset: Double,
    val projectedValue: Double,
    val gainAmount: Double,
    val gainPercent: Double,
    val investedValue: Double? = null,
    val actualValue: Double? = null
)

fun parseDateFlexible(dateStr: String): Date? {
    val formats = listOf(
        SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
        SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    )
    for (fmt in formats) {
        try {
            val d = fmt.parse(dateStr)
            if (d != null) return d
        } catch (e: Exception) {}
    }
    return null
}

fun formatInrCurrency(amount: Double): String {
    val isNeg = amount < 0
    val absVal = abs(amount)
    val fmt = String.format(Locale.US, "%.2f", absVal)
    val parts = fmt.split(".")
    var intPart = parts[0]
    val decPart = if (parts.size > 1) "." + parts[1] else ""

    if (intPart.length > 3) {
        val last3 = intPart.substring(intPart.length - 3)
        val rest = intPart.substring(0, intPart.length - 3)
        val sb = StringBuilder()
        for (i in rest.indices) {
            if (i > 0 && (rest.length - i) % 2 == 0) {
                sb.append(",")
            }
            sb.append(rest[i])
        }
        intPart = sb.toString() + "," + last3
    }
    return (if (isNeg) "-₹" else "₹") + intPart + decPart
}

fun formatCompactInr(amount: Double): String {
    val absVal = abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        absVal >= 10_000_000 -> String.format(Locale.US, "%s₹%.2fCr", sign, absVal / 10_000_000.0)
        absVal >= 100_000 -> String.format(Locale.US, "%s₹%.2fL", sign, absVal / 100_000.0)
        absVal >= 1_000 -> String.format(Locale.US, "%s₹%.1fk", sign, absVal / 1000.0)
        else -> String.format(Locale.US, "%s₹%.0f", sign, absVal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkProjectionGraphDialog(
    xirrBaseCal: Calendar,
    futureValCal: Calendar,
    defaultInvestedAmount: Double,
    defaultXirr: Double,
    isDark: Boolean,
    allTxns: List<CasTransactionEntity> = emptyList(),
    investedItems: List<FundUiItem> = emptyList(),
    portfolioSummary: PortfolioSummaryData? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BenchmarkProjectionGraphContent(
                xirrBaseCal = xirrBaseCal,
                futureValCal = futureValCal,
                defaultInvestedAmount = defaultInvestedAmount,
                defaultXirr = defaultXirr,
                isDark = isDark,
                allTxns = allTxns,
                investedItems = investedItems,
                portfolioSummary = portfolioSummary,
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkProjectionGraphContent(
    xirrBaseCal: Calendar,
    futureValCal: Calendar,
    defaultInvestedAmount: Double,
    defaultXirr: Double,
    isDark: Boolean,
    allTxns: List<CasTransactionEntity> = emptyList(),
    investedItems: List<FundUiItem> = emptyList(),
    portfolioSummary: PortfolioSummaryData? = null,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd-MMM-yyyy", Locale.US) }

    var rawInvestedText by remember { mutableStateOf(String.format(Locale.US, "%.0f", defaultInvestedAmount)) }
    var rawXirrText by remember { mutableStateOf(String.format(Locale.US, "%.2f", defaultXirr)) }

    val currentInvested = rawInvestedText.toDoubleOrNull() ?: defaultInvestedAmount
    val currentXirr = rawXirrText.toDoubleOrNull() ?: defaultXirr

    val durationOptions = remember {
        listOf("-9m to +10d", "1 Year", "2 Years", "3 Years", "5 Years", "10 Years", "All Years")
    }
    var selectedDuration by remember { mutableStateOf("-9m to +10d") }
    var isDurationDropdownExpanded by remember { mutableStateOf(false) }

    val todayCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }
    val todayMs = remember(todayCal) { todayCal.timeInMillis }

    // Start and End Timestamps for selected window duration
    val (windowStartMs, windowEndMs) = remember(selectedDuration, xirrBaseCal, futureValCal, todayMs) {
        val calStart = Calendar.getInstance()
        val calEnd = Calendar.getInstance()
        val baseMs = xirrBaseCal.timeInMillis
        val (rawStartMs, rawEndMs) = when (selectedDuration) {
            "-9m to +10d" -> {
                calStart.add(Calendar.MONTH, -9)
                calEnd.add(Calendar.DAY_OF_YEAR, 10)
                Pair(calStart.timeInMillis, calEnd.timeInMillis)
            }
            "1 Year" -> {
                val endCal = Calendar.getInstance()
                endCal.timeInMillis = baseMs
                endCal.add(Calendar.YEAR, 1)
                Pair(baseMs, endCal.timeInMillis)
            }
            "2 Years" -> {
                val endCal = Calendar.getInstance()
                endCal.timeInMillis = baseMs
                endCal.add(Calendar.YEAR, 2)
                Pair(baseMs, endCal.timeInMillis)
            }
            "3 Years" -> {
                val endCal = Calendar.getInstance()
                endCal.timeInMillis = baseMs
                endCal.add(Calendar.YEAR, 3)
                Pair(baseMs, endCal.timeInMillis)
            }
            "5 Years" -> {
                val endCal = Calendar.getInstance()
                endCal.timeInMillis = baseMs
                endCal.add(Calendar.YEAR, 5)
                Pair(baseMs, endCal.timeInMillis)
            }
            "10 Years" -> {
                val endCal = Calendar.getInstance()
                endCal.timeInMillis = baseMs
                endCal.add(Calendar.YEAR, 10)
                Pair(baseMs, endCal.timeInMillis)
            }
            else -> Pair(baseMs, futureValCal.timeInMillis) // "All Years"
        }
        val clampedStart = maxOf(rawStartMs, baseMs)
        val clampedEnd = maxOf(rawEndMs, clampedStart + 86_400_000L)
        Pair(clampedStart, clampedEnd)
    }

    // Build or fetch authentic historical portfolio curve using scheme-separated FIFO
    val histPoints = remember(investedItems, allTxns, portfolioSummary) {
        val cached = PortfolioHistoricalCurveCache.cachedPoints
        if (!cached.isNullOrEmpty()) {
            cached
        } else {
            val totalInvestedTarget = portfolioSummary?.totalInvested ?: defaultInvestedAmount
            val totalCurrentValTarget = portfolioSummary?.totalCurrentValue ?: defaultInvestedAmount
            val liveXirr = portfolioSummary?.xirrVal ?: defaultXirr
            val histNavList = NavCache.cache.flatMap { (scheme, records) ->
                records.map { HistoricalNav(schemeName = scheme, dateStr = it.dateStr, navValue = it.nav) }
            }
            PortfolioHistoricalCurveCache.computePoints(
                investedItems = investedItems,
                txns = allTxns,
                allHistNavs = histNavList,
                xirrBaseCal = xirrBaseCal,
                liveAggregateXirr = liveXirr,
                totalInvestedTarget = totalInvestedTarget,
                totalCurrentValTarget = totalCurrentValTarget
            )
        }
    }

    val histTree = remember(histPoints) {
        val tree = java.util.TreeMap<Long, PortfolioHistoricalPoint>()
        for (pt in histPoints) {
            tree[pt.timeMillis] = pt
        }
        tree
    }

    // Data points calculation for all 3 curves over [windowStartMs, windowEndMs]
    val points = remember(currentInvested, currentXirr, windowStartMs, windowEndMs, xirrBaseCal, histTree, portfolioSummary, todayMs) {
        val totalCount = 120
        val baseMs = xirrBaseCal.timeInMillis
        val r = currentXirr / 100.0

        val totalInvestedTarget = portfolioSummary?.totalInvested ?: currentInvested
        val totalCurrentValTarget = portfolioSummary?.totalCurrentValue ?: currentInvested

        // Ensure window cleanly covers today if selected duration ends before today
        val effectiveEndMs = if (windowEndMs < todayMs) todayMs else windowEndMs
        val effectiveStartMs = minOf(windowStartMs, todayMs)

        // Generate timestamps guaranteeing an exact point for TODAY (todayMs)
        val sampleTimes = ArrayList<Long>(totalCount + 2)
        if (effectiveStartMs < todayMs && todayMs < effectiveEndMs) {
            val pastSpan = (todayMs - effectiveStartMs).toDouble()
            val totalSpan = (effectiveEndMs - effectiveStartMs).toDouble()
            val pastRatio = (pastSpan / totalSpan).coerceIn(0.1, 0.9)
            val pastCount = (totalCount * pastRatio).roundToInt().coerceIn(15, 105)
            val futureCount = totalCount - pastCount

            for (i in 0 until pastCount) {
                val frac = i.toDouble() / pastCount
                sampleTimes.add((effectiveStartMs + frac * pastSpan).toLong())
            }
            // Explicit exact point for TODAY
            sampleTimes.add(todayMs)
            val futureSpan = (effectiveEndMs - todayMs).toDouble()
            for (j in 1..futureCount) {
                val frac = j.toDouble() / futureCount
                sampleTimes.add((todayMs + frac * futureSpan).toLong())
            }
        } else if (todayMs <= effectiveStartMs) {
            sampleTimes.add(todayMs)
            val span = (effectiveEndMs - effectiveStartMs).toDouble()
            for (i in 0..totalCount) {
                val frac = i.toDouble() / totalCount
                sampleTimes.add((effectiveStartMs + frac * span).toLong())
            }
        } else {
            val span = (todayMs - effectiveStartMs).toDouble()
            for (i in 0 until totalCount) {
                val frac = i.toDouble() / totalCount
                sampleTimes.add((effectiveStartMs + frac * span).toLong())
            }
            sampleTimes.add(todayMs)
        }

        val todayIndex = sampleTimes.indexOfLast { it <= todayMs }.coerceAtLeast(0)
        val list = ArrayList<ProjectionPoint>(sampleTimes.size)

        for ((idx, dateMs) in sampleTimes.withIndex()) {
            val isPastOrToday = (idx <= todayIndex)
            val isToday = (idx == todayIndex)
            val date = Date(dateMs)
            val dateStr = dateFormat.format(date)

            val yearOffsetFromBase = (dateMs - baseMs) / (1000.0 * 60 * 60 * 24 * 365.25)
            val projValue = if (currentInvested > 0 && yearOffsetFromBase >= 0) {
                currentInvested * (1.0 + r).pow(yearOffsetFromBase)
            } else currentInvested
            val gain = projValue - currentInvested
            val gainPct = if (currentInvested > 0) (gain / currentInvested) * 100.0 else 0.0

            var investedVal: Double? = null
            var actualVal: Double? = null

            if (isPastOrToday) {
                if (isToday) {
                    investedVal = totalInvestedTarget
                    actualVal = totalCurrentValTarget
                } else if (histTree.isNotEmpty()) {
                    val floor = histTree.floorEntry(dateMs)
                    val ceil = histTree.ceilingEntry(dateMs)
                    if (floor != null && ceil != null && floor.key != ceil.key) {
                        val frac = (dateMs - floor.key).toDouble() / (ceil.key - floor.key)
                        investedVal = floor.value.investedCost + frac * (ceil.value.investedCost - floor.value.investedCost)
                        actualVal = floor.value.currentValue + frac * (ceil.value.currentValue - floor.value.currentValue)
                    } else if (floor != null) {
                        investedVal = floor.value.investedCost
                        actualVal = floor.value.currentValue
                    } else if (ceil != null) {
                        investedVal = ceil.value.investedCost
                        actualVal = ceil.value.currentValue
                    } else {
                        investedVal = totalInvestedTarget
                        actualVal = totalCurrentValTarget
                    }
                } else {
                    investedVal = totalInvestedTarget
                    actualVal = totalCurrentValTarget
                }
            }

            list.add(
                ProjectionPoint(
                    dateStr = dateStr,
                    date = date,
                    yearOffset = maxOf(0.0, yearOffsetFromBase),
                    projectedValue = projValue,
                    gainAmount = gain,
                    gainPercent = gainPct,
                    investedValue = investedVal,
                    actualValue = actualVal
                )
            )
        }
        list
    }

    // Default inspection index corresponds to the latest available date (today)
    val defaultSelectedIndex = remember(points) {
        val idx = points.indexOfLast { it.investedValue != null || it.actualValue != null }
        if (idx != -1) idx else points.lastIndex
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val displayIndex = selectedIndex ?: defaultSelectedIndex
    val activePoint = points.getOrElse(displayIndex.coerceIn(0, points.lastIndex)) { points.last() }

    val startWindowDateStr = remember(points) { points.firstOrNull()?.dateStr ?: "" }
    val endWindowDateStr = remember(points) { points.lastOrNull()?.dateStr ?: "" }

    val isModified = (currentInvested != defaultInvestedAmount) || (currentXirr != defaultXirr) || (selectedDuration != "-9m to +10d")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Benchmark Projection Graph",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Window: $startWindowDateStr → $endWindowDateStr",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close Graph")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            rawInvestedText = String.format(Locale.US, "%.0f", defaultInvestedAmount)
                            rawXirrText = String.format(Locale.US, "%.2f", defaultXirr)
                            selectedDuration = "-9m to +10d"
                            selectedIndex = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to Benchmark",
                            tint = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Interactive Canvas Graph with 3 Curves
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Graph Legends Header for 3 Curves
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Projected", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF818CF8), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Invested (Till Date)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF818CF8))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Actual NAV (Till Date)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                        }

                        if (selectedIndex != null) {
                            TextButton(
                                onClick = { selectedIndex = null },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Default", fontSize = 10.sp)
                            }
                        } else {
                            Text(
                                text = "Drag/Tap to inspect",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Projection Chart Canvas with 3 Curves
                    ProjectionChartCanvas(
                        points = points,
                        baseInvested = currentInvested,
                        selectedIndex = displayIndex,
                        onSelectIndex = { selectedIndex = it },
                        isDark = isDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
            }

            // Selected Point / Target Summary Card showing 3 Values
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrendingUpIcon(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedIndex == null) "LATEST DATE VALUES (TODAY)" else "INSPECT DATE VALUES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${activePoint.dateStr} (${String.format(Locale.US, "%.1f", activePoint.yearOffset)} yrs)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Projected Value
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Projected Target",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444)
                            )
                            Text(
                                text = formatInrCurrency(activePoint.projectedValue),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }

                        // 2. Invested Amount (Till Date)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Invested Amt",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF818CF8)
                            )
                            Text(
                                text = activePoint.investedValue?.let { formatInrCurrency(it) } ?: "N/A (Future)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activePoint.investedValue != null) Color(0xFF818CF8) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        // 3. Actual Value (Till Date) - RED COLOR
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Actual Value",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = activePoint.actualValue?.let { formatInrCurrency(it) } ?: "N/A (Future)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activePoint.actualValue != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Interactive Controls Row (Duration Dropdown + Inputs)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFFAFAFA)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Duration Dropdown
                        ExposedDropdownMenuBox(
                            expanded = isDurationDropdownExpanded,
                            onExpandedChange = { isDurationDropdownExpanded = !isDurationDropdownExpanded },
                            modifier = Modifier.weight(1.2f)
                        ) {
                            OutlinedTextField(
                                value = selectedDuration,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Window Duration", fontSize = 11.sp) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDurationDropdownExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor(),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            )
                            ExposedDropdownMenu(
                                expanded = isDurationDropdownExpanded,
                                onDismissRequest = { isDurationDropdownExpanded = false }
                            ) {
                                durationOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt, fontSize = 13.sp) },
                                        onClick = {
                                            selectedDuration = opt
                                            isDurationDropdownExpanded = false
                                            selectedIndex = null
                                        }
                                    )
                                }
                            }
                        }

                        // Investment Amt Input
                        OutlinedTextField(
                            value = rawInvestedText,
                            onValueChange = { rawInvestedText = it },
                            label = { Text("Invested Amt", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1.1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        )

                        // XIRR % Input
                        OutlinedTextField(
                            value = rawXirrText,
                            onValueChange = { rawXirrText = it },
                            label = { Text("XIRR %", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    if (isModified) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Modified parameters active",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(
                                onClick = {
                                    rawInvestedText = String.format(Locale.US, "%.0f", defaultInvestedAmount)
                                    rawXirrText = String.format(Locale.US, "%.2f", defaultXirr)
                                    selectedDuration = "-9m to +10d"
                                    selectedIndex = null
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Reset Values", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectionChartCanvas(
    points: List<ProjectionPoint>,
    baseInvested: Double,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val currentPoints by rememberUpdatedState(points)
    val currentOnSelectIndex by rememberUpdatedState(onSelectIndex)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val pts = currentPoints
                        if (pts.isEmpty()) return@detectTapGestures
                        val usableW = size.width - 90f
                        val progress = ((offset.x - 50f) / usableW).coerceIn(0f, 1f)
                        val tMin = pts.first().date.time
                        val tMax = pts.last().date.time
                        val targetTime = (tMin + progress * (tMax - tMin)).toLong()
                        val closestIdx = pts.indices.minByOrNull { abs(pts[it].date.time - targetTime) } ?: 0
                        currentOnSelectIndex(closestIdx)
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val pts = currentPoints
                        if (pts.isEmpty()) return@detectHorizontalDragGestures
                        val usableW = size.width - 90f
                        val progress = ((offset.x - 50f) / usableW).coerceIn(0f, 1f)
                        val tMin = pts.first().date.time
                        val tMax = pts.last().date.time
                        val targetTime = (tMin + progress * (tMax - tMin)).toLong()
                        val closestIdx = pts.indices.minByOrNull { abs(pts[it].date.time - targetTime) } ?: 0
                        currentOnSelectIndex(closestIdx)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val pts = currentPoints
                        if (pts.isEmpty()) return@detectHorizontalDragGestures
                        val usableW = size.width - 90f
                        val progress = ((change.position.x - 50f) / usableW).coerceIn(0f, 1f)
                        val tMin = pts.first().date.time
                        val tMax = pts.last().date.time
                        val targetTime = (tMin + progress * (tMax - tMin)).toLong()
                        val closestIdx = pts.indices.minByOrNull { abs(pts[it].date.time - targetTime) } ?: 0
                        currentOnSelectIndex(closestIdx)
                    }
                )
            }
    ) {
        if (points.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height

        val paddingLeft = 50f
        val paddingRight = 40f
        val paddingTop = 25f
        val paddingBottom = 120f

        val chartW = w - paddingLeft - paddingRight
        val chartH = h - paddingTop - paddingBottom

        val projectedMax = points.maxOf { it.projectedValue }
        val investedMax = points.mapNotNull { it.investedValue }.maxOrNull() ?: baseInvested
        val actualMax = points.mapNotNull { it.actualValue }.maxOrNull() ?: baseInvested

        val maxVal = maxOf(baseInvested * 1.05, projectedMax, investedMax, actualMax)

        val projectedMin = points.minOf { it.projectedValue }
        val investedMin = points.mapNotNull { it.investedValue }.minOrNull() ?: baseInvested
        val actualMin = points.mapNotNull { it.actualValue }.minOrNull() ?: baseInvested

        val minVal = minOf(baseInvested * 0.95, projectedMin, investedMin, actualMin)

        val valDiff = if (maxVal > minVal) maxVal - minVal else 1.0
        val padY = valDiff * 0.08
        val yMax = maxVal + padY
        val yMin = maxOf(0.0, minVal - padY)
        val yRange = if (yMax > yMin) yMax - yMin else 1.0

        val minTime = points.first().date.time
        val maxTime = points.last().date.time
        val timeSpan = (maxTime - minTime).toDouble().coerceAtLeast(1.0)

        fun getXByTime(dateMs: Long): Float {
            val frac = ((dateMs - minTime) / timeSpan).coerceIn(0.0, 1.0).toFloat()
            return paddingLeft + frac * chartW
        }

        fun getX(index: Int): Float {
            if (points.isEmpty()) return paddingLeft
            val pt = points[index.coerceIn(0, points.lastIndex)]
            return getXByTime(pt.date.time)
        }

        fun getY(value: Double): Float {
            val frac = ((value - yMin) / yRange).toFloat()
            return paddingTop + chartH * (1f - frac)
        }

        // Draw horizontal grid lines & Y-axis labels (5 steps)
        val gridColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
        val textCol = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

        val gridSteps = 4
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps
            val yPos = paddingTop + chartH * frac
            val gridVal = yMax - (frac * yRange)

            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, yPos),
                end = Offset(w - paddingRight, yPos),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )

            // Label on Y axis right
            val txtResult = textMeasurer.measure(
                text = formatCompactInr(gridVal),
                style = TextStyle(fontSize = 10.sp, color = textCol)
            )
            drawText(
                textLayoutResult = txtResult,
                topLeft = Offset(w - paddingRight + 6f, yPos - txtResult.size.height / 2f)
            )
        }

        // Base Investment Reference Line (dashed grey)
        val yBase = getY(baseInvested)
        drawLine(
            color = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f),
            start = Offset(paddingLeft, yBase),
            end = Offset(w - paddingRight, yBase),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )

        // Draw X-axis date labels (6 ticks spaced linearly by time)
        val tickCount = 5
        val shortDateFormat = SimpleDateFormat("MMM yy", Locale.US)

        for (t in 0..tickCount) {
            val frac = t.toFloat() / tickCount
            val tickTime = (minTime + frac * timeSpan).toLong()
            val xPos = paddingLeft + frac * chartW

            // Draw faint vertical grid line
            drawLine(
                color = gridColor,
                start = Offset(xPos, paddingTop),
                end = Offset(xPos, paddingTop + chartH),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )

            val labelText = shortDateFormat.format(Date(tickTime))
            val txtResult = textMeasurer.measure(
                text = labelText,
                style = TextStyle(fontSize = 10.sp, color = textCol, fontWeight = FontWeight.Medium)
            )
            drawText(
                textLayoutResult = txtResult,
                topLeft = Offset(xPos - txtResult.size.width / 2f, paddingTop + chartH + 10f)
            )
        }

        // 1. Curve 1: Projected Target Growth (Red - full projection to future date)
        val projLinePath = Path()
        val projFillPath = Path()

        for (i in points.indices) {
            val x = getX(i)
            val y = getY(points[i].projectedValue)

            if (i == 0) {
                projLinePath.moveTo(x, y)
                projFillPath.moveTo(x, paddingTop + chartH)
                projFillPath.lineTo(x, y)
            } else {
                projLinePath.lineTo(x, y)
                projFillPath.lineTo(x, y)
            }
        }
        projFillPath.lineTo(getX(points.lastIndex), paddingTop + chartH)
        projFillPath.close()

        val redGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFEF4444).copy(alpha = 0.25f),
                Color(0xFFEF4444).copy(alpha = 0.01f)
            ),
            startY = paddingTop,
            endY = paddingTop + chartH
        )
        drawPath(path = projFillPath, brush = redGradient)
        drawPath(
            path = projLinePath,
            color = Color(0xFFEF4444),
            style = Stroke(width = 3f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )

        // 2. Curve 2: Invested Amount (Indigo - till date)
        val investedIndices = points.indices.filter { points[it].investedValue != null }
        if (investedIndices.isNotEmpty()) {
            val investedLinePath = Path()
            for ((seqIdx, i) in investedIndices.withIndex()) {
                val x = getX(i)
                val y = getY(points[i].investedValue!!)
                if (seqIdx == 0) {
                    investedLinePath.moveTo(x, y)
                } else {
                    investedLinePath.lineTo(x, y)
                }
            }
            drawPath(
                path = investedLinePath,
                color = Color(0xFF818CF8),
                style = Stroke(width = 2.8f, join = StrokeJoin.Round, cap = StrokeCap.Round)
            )

            // Endpoint dot at today for Invested Amount
            val lastInvIdx = investedIndices.last()
            val invLastX = getX(lastInvIdx)
            val invLastY = getY(points[lastInvIdx].investedValue!!)
            drawCircle(color = Color(0xFF818CF8).copy(alpha = 0.3f), radius = 7f, center = Offset(invLastX, invLastY))
            drawCircle(color = Color(0xFF818CF8), radius = 4f, center = Offset(invLastX, invLastY))
        }

        // 3. Curve 3: Actual Value based on NAV (Green - till date)
        val actualIndices = points.indices.filter { points[it].actualValue != null }
        if (actualIndices.isNotEmpty()) {
            val actualLinePath = Path()
            val actualFillPath = Path()

            for ((seqIdx, i) in actualIndices.withIndex()) {
                val x = getX(i)
                val y = getY(points[i].actualValue!!)
                if (seqIdx == 0) {
                    actualLinePath.moveTo(x, y)
                    actualFillPath.moveTo(x, paddingTop + chartH)
                    actualFillPath.lineTo(x, y)
                } else {
                    actualLinePath.lineTo(x, y)
                    actualFillPath.lineTo(x, y)
                }
            }
            val lastActIdx = actualIndices.last()
            actualFillPath.lineTo(getX(lastActIdx), paddingTop + chartH)
            actualFillPath.close()

            val greenGradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF4CAF50).copy(alpha = 0.2f),
                    Color(0xFF4CAF50).copy(alpha = 0.01f)
                ),
                startY = paddingTop,
                endY = paddingTop + chartH
            )
            drawPath(path = actualFillPath, brush = greenGradient)
            drawPath(
                path = actualLinePath,
                color = Color(0xFF4CAF50),
                style = Stroke(width = 3.2f, join = StrokeJoin.Round, cap = StrokeCap.Round)
            )

            // Endpoint glowing dot at today for Actual Value
            val actLastX = getX(lastActIdx)
            val actLastY = getY(points[lastActIdx].actualValue!!)
            drawCircle(color = Color(0xFF4CAF50).copy(alpha = 0.4f), radius = 9f, center = Offset(actLastX, actLastY))
            drawCircle(color = Color.White, radius = 5f, center = Offset(actLastX, actLastY))
            drawCircle(color = Color(0xFF4CAF50), radius = 3.5f, center = Offset(actLastX, actLastY))
        }

        // Draw Interactive Crosshair & Inspection Nodes across active curves
        val activeIdx = selectedIndex.coerceIn(0, points.lastIndex)
        val activePt = points[activeIdx]

        val activeX = getX(activeIdx)
        val projY = getY(activePt.projectedValue)

        // Movable Vertical Crosshair
        drawLine(
            color = Color(0xFFFF9800),
            start = Offset(activeX, paddingTop),
            end = Offset(activeX, paddingTop + chartH),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )

        // Highlight Node on Projected Curve
        val projNodeColor = Color(0xFFEF4444)
        drawCircle(color = projNodeColor.copy(alpha = 0.3f), radius = 9f, center = Offset(activeX, projY))
        drawCircle(color = Color.White, radius = 5f, center = Offset(activeX, projY))
        drawCircle(color = projNodeColor, radius = 3.5f, center = Offset(activeX, projY))

        // Highlight Node on Invested Curve if active at this date
        if (activePt.investedValue != null) {
            val invY = getY(activePt.investedValue)
            val invNodeColor = Color(0xFF818CF8)
            drawCircle(color = invNodeColor.copy(alpha = 0.3f), radius = 9f, center = Offset(activeX, invY))
            drawCircle(color = Color.White, radius = 5f, center = Offset(activeX, invY))
            drawCircle(color = invNodeColor, radius = 3.5f, center = Offset(activeX, invY))
        }

        // Highlight Node on Actual NAV Curve if active at this date
        if (activePt.actualValue != null) {
            val actY = getY(activePt.actualValue)
            val actNodeColor = Color(0xFF4CAF50)
            drawCircle(color = actNodeColor.copy(alpha = 0.4f), radius = 10f, center = Offset(activeX, actY))
            drawCircle(color = Color.White, radius = 5.5f, center = Offset(activeX, actY))
            drawCircle(color = actNodeColor, radius = 4f, center = Offset(activeX, actY))
        }
    }
}
