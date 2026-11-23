
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlin.math.max

private fun parseDateAnyLocal(dateStr: String): Date? {
    val formats = listOf("dd-MMM-yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
    for (f in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(f, java.util.Locale.US)
            val d = sdf.parse(dateStr)
            if (d != null) return d
        } catch (e: Exception) {}
    }
    return null
}

data class SipGraphPoint(
    val dateLong: Long,
    val dateStr: String,
    val absoluteReturn: Double,
    val annualizedReturn: Double
)

data class SipFundLine(
    val name: String,
    val color: Color,
    val points: List<SipGraphPoint>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SipAnalyzerScreen(
    baseSchemeName: String,
    allSchemes: List<String>,
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    var selectedSchemes by remember { mutableStateOf(listOf(baseSchemeName)) }
    var selectedFrequencies by remember { mutableStateOf(setOf("Daily", "Weekly", "Monthly")) }
    
    var isLoading by remember { mutableStateOf(false) }
    var fundLines by remember { mutableStateOf<List<SipFundLine>>(emptyList()) }
    
    var sipStartDate by remember { mutableStateOf<Date?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var minAvailableDate by remember { mutableStateOf<Long>(0L) }
    var maxAvailableDate by remember { mutableStateOf<Long>(0L) }
    
    var returnType by remember { mutableStateOf("Annualized") }
    
    val colors = listOf(
        Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
        Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFFF8A65), Color(0xFFA1887F)
    )
    
    val scope = rememberCoroutineScope()
    
    fun reloadData(schemes: List<String>, freqs: Set<String>, startDateOpt: Date?) {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.Default) {
                val newFundLines = mutableListOf<SipFundLine>()
                var colorIdx = 0
                
                val baseScheme = schemes.firstOrNull() ?: ""
                val baseRecords = NavCache.getRecords(baseScheme)
                
                if (baseRecords.isNotEmpty()) {
                    val baseMapped = baseRecords.mapNotNull { 
                        val d = parseDateAnyLocal(it.dateStr)
                        if (d != null) Pair(it, d.time) else null
                    }.sortedBy { it.second }
                    
                    if (baseMapped.isNotEmpty()) {
                        val minD = baseMapped.first().second
                        val maxD = baseMapped.last().second
                        minAvailableDate = minD
                        maxAvailableDate = maxD
                        
                        if (sipStartDate == null) {
                            sipStartDate = Date(minD)
                        }
                        
                        val activeStartDate = startDateOpt ?: sipStartDate!!
                        val activeStartLong = activeStartDate.time
                        
                        val sharedDates = baseMapped.filter { it.second >= activeStartLong }.map { it.second }
                        
                        val allFrequencies = listOf(
                            Pair("Daily", 1),
                            Pair("Weekly", 7),
                            Pair("Biweekly", 14),
                            Pair("Monthly", 30),
                            Pair("Quarterly", 91)
                        )
                        val activeFrequencies = allFrequencies.filter { freqs.contains(it.first) }
                        
                        for (scheme in schemes) {
                            var records = NavCache.getRecords(scheme)
                            if (records.isEmpty()) {
                                val db = AppDatabase.getDatabase(context)
                                val dbRecords = db.fundDao().getHistoricalNavs(scheme)
                                if (dbRecords.isNotEmpty()) {
                                    records = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                                    NavCache.addAll(scheme, records)
                                }
                            }
                            if (records.isEmpty()) continue
                            
                            val mapped = records.mapNotNull { rec -> 
                                val d = parseDateAnyLocal(rec.dateStr)
                                if (d != null) Pair(rec, d.time) else null
                            }.sortedBy { it.second }
                            
                            if (mapped.isEmpty()) continue
                            
                            for ((freqName, stepDays) in activeFrequencies) {
                                val points = mutableListOf<SipGraphPoint>()
                                
                                val allCashFlows = mutableListOf<Pair<Long, Double>>()
                                val allInvestments = mutableListOf<Pair<Long, Double>>() // time, units
                                var currentSipTime = activeStartLong
                                
                                var ptr = 0
                                while (currentSipTime <= maxAvailableDate) {
                                    while (ptr < mapped.size - 1 && mapped[ptr + 1].second <= currentSipTime) {
                                        ptr++
                                    }
                                    
                                    val navPoint = if (stepDays == 1) {
                                        mapped[ptr]
                                    } else {
                                        val windowEnd = currentSipTime + stepDays * 24L * 60 * 60 * 1000L - 1
                                        var windowPtr = ptr
                                        var maxNavPoint = mapped[ptr]
                                        while (windowPtr < mapped.size && mapped[windowPtr].second <= windowEnd) {
                                            if (mapped[windowPtr].first.nav > maxNavPoint.first.nav) {
                                                maxNavPoint = mapped[windowPtr]
                                            }
                                            windowPtr++
                                        }
                                        maxNavPoint
                                    }
                                    
                                    allCashFlows.add(Pair(navPoint.second, -1000.0))
                                    allInvestments.add(Pair(navPoint.second, 1000.0 / navPoint.first.nav))
                                    
                                    currentSipTime += stepDays * 24L * 60 * 60 * 1000L
                                }
                                
                                var invIndex = 0
                                var runningTotalUnits = 0.0
                                var runningTotalInvested = 0.0
                                
                                for (evalTime in sharedDates) {
                                    yield() // Keep UI responsive during heavy calculation
                                    
                                    while (invIndex < allInvestments.size && allInvestments[invIndex].first <= evalTime) {
                                        runningTotalUnits += allInvestments[invIndex].second
                                        runningTotalInvested += 1000.0
                                        invIndex++
                                    }
                                    
                                    if (invIndex > 0) {
                                        val totalUnits = runningTotalUnits
                                        val totalInvested = runningTotalInvested
                                        
                                        val evalNavPoint = mapped.findLast { it.second <= evalTime } ?: mapped.first()
                                        val finalValue = totalUnits * evalNavPoint.first.nav
                                        val absoluteReturn = ((finalValue - totalInvested) / totalInvested) * 100.0
                                        
                                        val validCashFlows = allCashFlows.filter { it.first <= evalTime }.toMutableList()
                                        validCashFlows.add(Pair(evalNavPoint.second, finalValue))
                                        
                                        val daysElapsed = (evalTime - activeStartLong) / (24L * 60 * 60 * 1000L)
                                        val rawXirr = if (daysElapsed >= 30 && validCashFlows.size > 1 && validCashFlows.last().second > 0) calculateXIRR(validCashFlows) * 100.0 else 0.0
                                        
                                        val safeAbs = if (absoluteReturn.isNaN() || absoluteReturn.isInfinite()) 0.0 else absoluteReturn
                                        val safeXirr = if (rawXirr.isNaN() || rawXirr.isInfinite()) 0.0 else rawXirr
                                        
                                        points.add(SipGraphPoint(evalTime, evalNavPoint.first.dateStr, safeAbs, safeXirr))
                                    }
                                }
                                
                                val label = if (schemes.size > 1) "$scheme ($freqName)" else freqName
                                newFundLines.add(SipFundLine(label, colors[colorIdx % colors.size], points))
                                colorIdx++
                            }
                        }
                    }
                }
                fundLines = newFundLines
            }
            isLoading = false
        }
    }
    
    LaunchedEffect(selectedSchemes, selectedFrequencies, sipStartDate) {
        if (!isLoading) {
            reloadData(selectedSchemes, selectedFrequencies, sipStartDate)
        }
    }
    
    var selectedIndexA by remember { mutableStateOf<Int?>(null) }
    
    val allDates = remember(fundLines) {
        fundLines.flatMap { it.points }.map { it.dateLong }.distinct().sorted()
    }
    
    if (showDatePicker && sipStartDate != null) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = sipStartDate?.time ?: minAvailableDate,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis in minAvailableDate..maxAvailableDate
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
            androidx.compose.material3.DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = null,
                headline = null,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SIP Analyzer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(baseSchemeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Add Fund")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("All Funds") },
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
            // Main Content Area
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxWidth()
            ) {
                // Row 1: Date & Return Type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showDatePicker = true }.padding(4.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        val df = SimpleDateFormat("dd MMM yy", Locale.US)
                        Text(
                            text = sipStartDate?.let { df.format(it) } ?: "Start Date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Type: ", fontSize = 12.sp)
                        listOf("Annualized", "Absolute").forEach { type ->
                            FilterChip(
                                selected = returnType == type,
                                onClick = { returnType = type },
                                label = { Text(if(type=="Annualized") "Ann." else "Abs.", fontSize = 11.sp) },
                                modifier = Modifier.padding(start = 4.dp).height(28.dp)
                            )
                        }
                    }
                }
                
                // Row 2: Frequencies Dropdown
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    var freqExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { freqExpanded = true }, modifier = Modifier.height(36.dp)) {
                            Text("Frequencies", fontSize = 12.sp)
                            Icon(Icons.Default.KeyboardArrowDown, null)
                        }
                        DropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                            val allFreqs = listOf("Daily", "Weekly", "Biweekly", "Monthly", "Quarterly")
                            allFreqs.forEach { f ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = selectedFrequencies.contains(f),
                                                onCheckedChange = { checked ->
                                                    val newSet = selectedFrequencies.toMutableSet()
                                                    if (checked) newSet.add(f) else newSet.remove(f)
                                                    if (newSet.isNotEmpty()) selectedFrequencies = newSet
                                                }
                                            )
                                            Text(f)
                                        }
                                    },
                                    onClick = {
                                        val newSet = selectedFrequencies.toMutableSet()
                                        if (newSet.contains(f)) newSet.remove(f) else newSet.add(f)
                                        if (newSet.isNotEmpty()) selectedFrequencies = newSet
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
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
                        SipGraphTradingView(
                            fundLines = fundLines,
                            allDates = allDates,
                            isDarkTheme = isDarkTheme,
                            selectedIndex = selectedIndexA,
                            onIndexChange = { selectedIndexA = it },
                            returnType = returnType
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
                if (selectedIndexA != null && allDates.isNotEmpty() && selectedIndexA!! in allDates.indices) {
                    val dateLong = allDates[selectedIndexA!!]
                    val dfOut = java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                    val dateStr = dfOut.format(Date(dateLong))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Selected Date", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("$returnType Return", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            count = fundLines.size,
                            key = { fundLines[it].name },
                            contentType = { "sip_fund_stat" }
                        ) { i ->
                            val fd = fundLines[i]
                            val pt = fd.points.find { it.dateLong == dateLong }
                            
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(fd.color, RoundedCornerShape(4.dp)))
                                    Spacer(Modifier.width(6.dp))
                                    Text(fd.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(2.dp))
                                if (pt != null) {
                                    val ret = if (returnType == "Annualized") pt.annualizedReturn else pt.absoluteReturn
                                    Text(
                                        text = String.format(Locale.US, "%.2f%%", ret),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ret >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                } else {
                                    Text("No Data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun SipGraphTradingView(
    fundLines: List<SipFundLine>,
    allDates: List<Long>,
    isDarkTheme: Boolean,
    selectedIndex: Int?,
    onIndexChange: (Int?) -> Unit,
    returnType: String
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
    
    var scaleX by remember(fundLines, returnType) { mutableStateOf(defaultScaleX) }
    var offsetX by remember(fundLines, returnType) { mutableStateOf(Float.NEGATIVE_INFINITY) } 
    
    val density = androidx.compose.ui.platform.LocalDensity.current
    val yAxisWidth = with(density) { 56.dp.toPx() }
    val xAxisHeight = with(density) { 28.dp.toPx() }
    
    Box(modifier = Modifier.fillMaxSize()) {
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
            
            // Dynamic min/max value scaling
            val visibleMinDate = minDate + (((-offsetX) / scaleX) / chartWidth * dateRange).toLong()
            val visibleMaxDate = minDate + (((chartWidth - offsetX) / scaleX) / chartWidth * dateRange).toLong()
            
            val visiblePoints = fundLines.flatMap { it.points }.filter { it.dateLong in visibleMinDate..visibleMaxDate }
            
            val rawMinVal = visiblePoints.minOfOrNull { if (returnType == "Annualized") it.annualizedReturn else it.absoluteReturn } ?: 0.0
            val rawMaxVal = visiblePoints.maxOfOrNull { if (returnType == "Annualized") it.annualizedReturn else it.absoluteReturn } ?: 10.0
            
            val padding = (rawMaxVal - rawMinVal) * 0.1
            val minVal = rawMinVal - padding
            val maxVal = rawMaxVal + padding
            
            var range = maxVal - minVal
            if (range <= 0.0) range = 1.0
            
            clipRect(0f, 0f, chartWidth, chartHeight) {
                // Background grid lines
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
                
                // Zero line
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
                
                // Draw paths
                for (fd in fundLines) {
                    val path = Path()
                    var first = true
                    for (pt in fd.points) {
                        val fraction = (pt.dateLong - minDate).toFloat() / dateRange
                        val rawX = fraction * chartWidth
                        val scaledX = rawX * scaleX + offsetX
                        
                        val ret = if (returnType == "Annualized") pt.annualizedReturn else pt.absoluteReturn
                        val scaledY = chartHeight - ((ret - minVal) / range * chartHeight).toFloat()
                        
                        if (first) {
                            path.moveTo(scaledX, scaledY)
                            first = false
                        } else {
                            path.lineTo(scaledX, scaledY)
                        }
                    }
                    drawPath(
                        path = path,
                        color = fd.color,
                        style = Stroke(width = 2.5f)
                    )
                }
                
                // Draw cursor line
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
                                val ret = if (returnType == "Annualized") pt.annualizedReturn else pt.absoluteReturn
                                val scaledY = chartHeight - ((ret - minVal) / range * chartHeight).toFloat()
                                
                                drawCircle(color = fd.color, radius = 6f, center = Offset(scaledX, scaledY))
                                drawCircle(color = Color.White, radius = 3f, center = Offset(scaledX, scaledY))
                            }
                        }
                    }
                }
            }
            
            // Draw Y-axis (right side)
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
                    val txtLayout = textMeasurer.measure("${v.toInt()}%", style = textStyle)
                    drawText(textLayoutResult = txtLayout, topLeft = Offset(chartWidth + 4f, scaledY - txtLayout.size.height / 2f))
                }
            }
            
            // Draw X-axis (bottom)
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
        
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 55.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Fit All", fontSize = 10.sp)
                }
            }
            
            TextButton(
                onClick = {
                    scaleX = defaultScaleX
                    offsetX = Float.NEGATIVE_INFINITY
                    if (allDates.isNotEmpty()) {
                        onIndexChange(allDates.size - 1)
                    } else {
                        onIndexChange(null)
                    }
                },
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Last 3M", fontSize = 10.sp)
            }
        }
    }
}
