package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollingSipWeeklyDayWiseAnalysisScreen(
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
                            "Rolling SIP Weekly",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Date Picker Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SIP Start Date", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sipStartDate?.let { dateFormatter.format(it) } ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select Start Date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Duration Selector
            Text("Rolling Period", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$sipDurationYears Year${if(sipDurationYears != 1) "s" else ""}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Years", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$sipDurationMonths Month${if(sipDurationMonths != 1) "s" else ""}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Months", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (points.isNotEmpty()) {
                RollingWeeklyAnalysisTable(points, sipDurationYears, sipDurationMonths, sipStartDate)
                RollingSipDayRecommendationComponent(points, sipDurationYears, sipDurationMonths, sipStartDate)
            } else {
                Text("No data available for $baseSchemeName")
            }
        }
    }
}

@Composable
fun RollingWeeklyAnalysisTable(points: List<TimelineChartPoint>, sipDurationYears: Int, sipDurationMonths: Int, startDate: Date?) {
    val daysOfWeek = listOf(
        Pair("Monday", Calendar.MONDAY),
        Pair("Tuesday", Calendar.TUESDAY),
        Pair("Wednesday", Calendar.WEDNESDAY),
        Pair("Thursday", Calendar.THURSDAY),
        Pair("Friday", Calendar.FRIDAY)
    )

    var results by remember { mutableStateOf<List<WeeklyAnalysisResult>>(emptyList()) }
    var isCalculating by remember { mutableStateOf(true) }

    LaunchedEffect(points, sipDurationYears, sipDurationMonths, startDate) {
        isCalculating = true
        withContext(Dispatchers.Default) {
            val startMillis = startDate?.time ?: 0L
            val mappedPoints = points.map { Pair(it, parseTimelineDate(it.dateStr)) }
            val validPoints = mappedPoints.filter { it.second >= startMillis }
            
            if (validPoints.isEmpty()) {
                results = emptyList()
                return@withContext
            }
            
            val maxHistoryDate = validPoints.last().second
            val blockDurationMs = (sipDurationYears * 365L + sipDurationMonths * 30L) * 24L * 60L * 60L * 1000L
            val cal = Calendar.getInstance()

            val calcResults = daysOfWeek.map { (name, dayConst) ->
                var sumAbsReturn = 0.0
                var sumXirr = 0.0
                var totalBlocks = 0

                val dayPoints = validPoints.filter { 
                    cal.timeInMillis = it.second
                    cal.get(Calendar.DAY_OF_WEEK) == dayConst
                }

                for (i in dayPoints.indices) {
                    val startPt = dayPoints[i]
                    val startDate = startPt.second
                    val endDateTarget = startDate + blockDurationMs

                    if (endDateTarget > maxHistoryDate) break // Cannot form full block

                    val blockPoints = validPoints.filter { it.second in startDate..endDateTarget }
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
                        
                        sumAbsReturn += absReturn
                        sumXirr += xirr
                        totalBlocks++
                    }
                }

                if (totalBlocks > 0) {
                    WeeklyAnalysisResult(name, sumAbsReturn / totalBlocks, sumXirr / totalBlocks)
                } else {
                    WeeklyAnalysisResult(name, 0.0, 0.0, hasData = false)
                }
            }
            results = calcResults
        }
        isCalculating = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Average Rolling Performance", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

            if (isCalculating) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Day",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Avg Abs Ret",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "Avg XIRR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                
                // Data Rows
                results.forEach { res ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = res.dayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (res.hasData) {
                            val absColor = if (res.absReturn >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            val xirrColor = if (res.xirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            
                            val absRetDisp = if (res.absReturn.isNaN()) "—" else String.format(Locale.US, "%.2f%%", res.absReturn)
                            val xirrDisp = if (res.xirr.isNaN()) "—" else String.format(Locale.US, "%.2f%%", res.xirr)

                            Text(
                                text = absRetDisp,
                                fontSize = 14.sp,
                                color = absColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = xirrDisp,
                                fontSize = 14.sp,
                                color = xirrColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        } else {
                            Text(
                                text = "-",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(2f),
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RollingSipDayRecommendationComponent(points: List<TimelineChartPoint>, sipDurationYears: Int, sipDurationMonths: Int, startDate: Date?) {
    val daysOfWeek = listOf(
        Pair("Monday", Calendar.MONDAY),
        Pair("Tuesday", Calendar.TUESDAY),
        Pair("Wednesday", Calendar.WEDNESDAY),
        Pair("Thursday", Calendar.THURSDAY),
        Pair("Friday", Calendar.FRIDAY)
    )

    var results by remember { mutableStateOf<List<WeeklyAnalysisResult>>(emptyList()) }
    var recommendation by remember { mutableStateOf<RecommendationData?>(null) }
    var isCalculating by remember { mutableStateOf(true) }

    LaunchedEffect(points, sipDurationYears, sipDurationMonths, startDate) {
        isCalculating = true
        withContext(Dispatchers.Default) {
            val startMillis = startDate?.time ?: 0L
            val mappedPoints = points.map { Pair(it, parseTimelineDate(it.dateStr)) }
            val validPoints = mappedPoints.filter { it.second >= startMillis }
            
            if (validPoints.isEmpty()) {
                results = emptyList()
                return@withContext
            }
            
            val maxHistoryDate = validPoints.last().second
            val blockDurationMs = (sipDurationYears * 365L + sipDurationMonths * 30L) * 24L * 60L * 60L * 1000L
            val cal = Calendar.getInstance()

            val calcResults = daysOfWeek.map { (name, dayConst) ->
                var sumAbsReturn = 0.0
                var sumXirr = 0.0
                var totalBlocks = 0

                val dayPoints = validPoints.filter { 
                    cal.timeInMillis = it.second
                    cal.get(Calendar.DAY_OF_WEEK) == dayConst
                }

                for (i in dayPoints.indices) {
                    val startPt = dayPoints[i]
                    val startDate = startPt.second
                    val endDateTarget = startDate + blockDurationMs

                    if (endDateTarget > maxHistoryDate) break

                    val blockPoints = validPoints.filter { it.second in startDate..endDateTarget }
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
                        
                        sumAbsReturn += absReturn
                        sumXirr += xirr
                        totalBlocks++
                    }
                }

                if (totalBlocks > 0) {
                    WeeklyAnalysisResult(name, sumAbsReturn / totalBlocks, sumXirr / totalBlocks)
                } else {
                    WeeklyAnalysisResult(name, 0.0, 0.0, hasData = false)
                }
            }
            results = calcResults
            recommendation = computeIntelligentRecommendation(points, calcResults)
        }
        isCalculating = false
    }

    if (!isCalculating && recommendation != null) {
        val rec = recommendation!!
        val bestHistorical = results.filter { it.hasData }.maxByOrNull { it.xirr }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "SIP Day Recommendation",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 1. Best Historical
                Text("Best Historical SIP Day", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                if (bestHistorical != null) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(bestHistorical.dayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        val absRetDisp = if (bestHistorical.absReturn.isNaN()) "—" else String.format(Locale.US, "Abs: %.2f%%", bestHistorical.absReturn)
                        val absColor = if (bestHistorical.absReturn >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        Text(absRetDisp, fontSize = 13.sp, color = absColor, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(8.dp))
                        val xirrColor = if (bestHistorical.xirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        val xirrDisp = if (bestHistorical.xirr.isNaN()) "—" else String.format(Locale.US, "XIRR: %.2f%%", bestHistorical.xirr)
                        Text(xirrDisp, fontSize = 13.sp, color = xirrColor, fontWeight = FontWeight.Medium)
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                // 2. Recommended for this week
                Text("Recommended SIP Day for This Week", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(rec.fullDateStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Score: ${rec.confidence}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(rec.explanation, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
        }
    }
}
