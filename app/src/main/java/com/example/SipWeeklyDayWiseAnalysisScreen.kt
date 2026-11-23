package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
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
fun SipWeeklyDayWiseAnalysisScreen(
    baseSchemeName: String,
    allSchemes: List<String>,
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    var points by remember { mutableStateOf<List<TimelineChartPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var sipStartDate by remember { mutableStateOf<Date?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

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
                        Text("SIP Weekly Day-wise Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(baseSchemeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (points.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data available")
                }
            } else {
                // Header (Date picker)
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

                Spacer(modifier = Modifier.height(24.dp))

                // The Table
                // The Table
                WeeklyAnalysisTable(points, sipStartDate)
                
                // Add Recommendation
                SipDayRecommendationComponent(points, sipStartDate)
            }
        }
    }
}

@Composable
fun WeeklyAnalysisTable(points: List<TimelineChartPoint>, startDate: Date?) {
    if (startDate == null) return

    val daysOfWeek = listOf(
        Pair("Monday", Calendar.MONDAY),
        Pair("Tuesday", Calendar.TUESDAY),
        Pair("Wednesday", Calendar.WEDNESDAY),
        Pair("Thursday", Calendar.THURSDAY),
        Pair("Friday", Calendar.FRIDAY)
    )

    var results by remember { mutableStateOf<List<WeeklyAnalysisResult>>(emptyList()) }
    
    LaunchedEffect(points, startDate) {
        withContext(Dispatchers.Default) {
            val startMillis = startDate.time
            // Parse dates ONCE
            val mappedPoints = points.map { Pair(it, parseTimelineDate(it.dateStr)) }
            val validPoints = mappedPoints.filter { it.second >= startMillis }
            
            if (validPoints.isEmpty()) {
                results = emptyList()
                return@withContext
            }

            val latestNav = validPoints.last().first.nav
            val latestDate = validPoints.last().second

            val cal = Calendar.getInstance()

            val calcResults = daysOfWeek.map { (name, dayConst) ->
                var totalInvested = 0.0
                var totalUnits = 0.0
                val cashflows = mutableListOf<Pair<Long, Double>>()

                for (pt in validPoints) {
                    val pDate = pt.second
                    cal.timeInMillis = pDate
                    if (cal.get(Calendar.DAY_OF_WEEK) == dayConst) {
                        val amount = 1000.0
                        totalInvested += amount
                        totalUnits += amount / pt.first.nav
                        cashflows.add(Pair(pDate, -amount))
                    }
                }

                if (totalInvested > 0) {
                    val currentValue = totalUnits * latestNav
                    val absReturn = ((currentValue - totalInvested) / totalInvested) * 100.0
                    cashflows.add(Pair(latestDate, currentValue))
                    val xirr = calculateXIRR(cashflows) * 100.0

                    WeeklyAnalysisResult(name, absReturn, xirr)
                } else {
                    WeeklyAnalysisResult(name, 0.0, 0.0, hasData = false)
                }
            }
            results = calcResults
        }
    }
    
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                    text = "Abs. Return",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
                Text(
                    text = "XIRR",
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
                        
                        Text(
                            text = String.format(Locale.US, "%.2f%%", res.absReturn),
                            fontSize = 14.sp,
                            color = absColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f%%", res.xirr),
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

data class WeeklyAnalysisResult(
    val dayName: String,
    val absReturn: Double,
    val xirr: Double,
    val hasData: Boolean = true
)


@Composable
fun SipDayRecommendationComponent(points: List<TimelineChartPoint>, startDate: Date?) {
    if (startDate == null || points.isEmpty()) return

    val daysOfWeek = listOf(
        Pair("Monday", Calendar.MONDAY),
        Pair("Tuesday", Calendar.TUESDAY),
        Pair("Wednesday", Calendar.WEDNESDAY),
        Pair("Thursday", Calendar.THURSDAY),
        Pair("Friday", Calendar.FRIDAY)
    )

    var results by remember { mutableStateOf<List<WeeklyAnalysisResult>>(emptyList()) }
    
    LaunchedEffect(points, startDate) {
        withContext(Dispatchers.Default) {
            val startMillis = startDate.time
            // Parse dates ONCE
            val mappedPoints = points.map { Pair(it, parseTimelineDate(it.dateStr)) }
            val validPoints = mappedPoints.filter { it.second >= startMillis }
            
            if (validPoints.isEmpty()) {
                results = emptyList()
                return@withContext
            }

            val latestNav = validPoints.last().first.nav
            val latestDate = validPoints.last().second

            val cal = Calendar.getInstance()

            val calcResults = daysOfWeek.map { (name, dayConst) ->
                var totalInvested = 0.0
                var totalUnits = 0.0
                val cashflows = mutableListOf<Pair<Long, Double>>()

                for (pt in validPoints) {
                    val pDate = pt.second
                    cal.timeInMillis = pDate
                    if (cal.get(Calendar.DAY_OF_WEEK) == dayConst) {
                        val amount = 1000.0
                        totalInvested += amount
                        totalUnits += amount / pt.first.nav
                        cashflows.add(Pair(pDate, -amount))
                    }
                }

                if (totalInvested > 0) {
                    val currentValue = totalUnits * latestNav
                    val absReturn = ((currentValue - totalInvested) / totalInvested) * 100.0
                    cashflows.add(Pair(latestDate, currentValue))
                    val xirr = calculateXIRR(cashflows) * 100.0

                    WeeklyAnalysisResult(name, absReturn, xirr)
                } else {
                    WeeklyAnalysisResult(name, 0.0, 0.0, hasData = false)
                }
            }
            results = calcResults
        }
    }
    
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val bestHistorical = results.filter { it.hasData }.maxByOrNull { it.xirr }
    
    val recommendation = remember(points, results) {
        computeIntelligentRecommendation(points, results)
    }

    if (bestHistorical == null || recommendation.dayName.isEmpty()) return

    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(bestHistorical.dayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                val absColor = if (bestHistorical.absReturn >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                Text(String.format(Locale.US, "Abs: %.2f%%", bestHistorical.absReturn), fontSize = 13.sp, color = absColor, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                val xirrColor = if (bestHistorical.xirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                Text(String.format(Locale.US, "XIRR: %.2f%%", bestHistorical.xirr), fontSize = 13.sp, color = xirrColor, fontWeight = FontWeight.Medium)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            
            // 2. Recommended for this week
            Text("Recommended SIP Day for This Week", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(recommendation.fullDateStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Score: ${recommendation.confidence}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(recommendation.explanation, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}

data class RecommendationData(
    val dayName: String,
    val fullDateStr: String,
    val confidence: Int,
    val explanation: String,
    val suggestLumpsum: Boolean
)

fun computeIntelligentRecommendation(points: List<TimelineChartPoint>, results: List<WeeklyAnalysisResult>): RecommendationData {
    if (points.isEmpty() || results.isEmpty()) return RecommendationData("", "", 0, "", false)
    
    val cal = Calendar.getInstance()
    
    val maxXirr = results.maxOfOrNull { it.xirr } ?: 1.0
    val minXirr = results.minOfOrNull { it.xirr } ?: 0.0
    val rangeXirr = (maxXirr - minXirr).coerceAtLeast(0.001)
    
    val recentPoints = points.takeLast(60)
    val recentWeeks = recentPoints.groupBy { 
        cal.timeInMillis = parseTimelineDate(it.dateStr)
        cal.get(Calendar.WEEK_OF_YEAR) + cal.get(Calendar.YEAR) * 100
    }
    
    val dipsByDay = mutableMapOf<String, Int>()
    var totalWeeks = 0
    recentWeeks.forEach { (_, pts) ->
        if (pts.size >= 3) {
            val minNav = pts.minByOrNull { it.nav }?.nav ?: return@forEach
            val maxNav = pts.maxByOrNull { it.nav }?.nav ?: return@forEach
            if ((maxNav - minNav) / maxNav > 0.005) { 
                val minPt = pts.minByOrNull { it.nav }!!
                cal.timeInMillis = parseTimelineDate(minPt.dateStr)
                val dayName = when(cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Monday"
                    Calendar.TUESDAY -> "Tuesday"
                    Calendar.WEDNESDAY -> "Wednesday"
                    Calendar.THURSDAY -> "Thursday"
                    Calendar.FRIDAY -> "Friday"
                    else -> ""
                }
                if (dayName.isNotEmpty()) {
                    dipsByDay[dayName] = (dipsByDay[dayName] ?: 0) + 1
                }
            }
            totalWeeks++
        }
    }
    
    val last10 = points.takeLast(10)
    val currentTrend = if (last10.size == 10) (last10.last().nav - last10.first().nav) / last10.first().nav else 0.0
    val suggestLumpsum = currentTrend < -0.02
    
    var bestDay = ""
    var maxScore = -1.0
    var bestConfidence = 0
    var explanation = ""
    
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
    for (day in daysOfWeek) {
        val res = results.find { it.dayName == day } ?: continue
        if (!res.hasData) continue
        
        val histScore = ((res.xirr - minXirr) / rangeXirr) * 50.0 
        val dipScore = ((dipsByDay[day] ?: 0).toDouble() / totalWeeks.coerceAtLeast(1)) * 50.0 
        
        val rawScore = histScore + dipScore
        val finalScore = (rawScore * 0.7 + 25.0).coerceIn(45.0, 98.0).toInt()
        
        if (finalScore > maxScore) {
            maxScore = finalScore.toDouble()
            bestConfidence = finalScore
            bestDay = day
            
            val dipFactor = (dipsByDay[day] ?: 0)
            if (dipFactor > totalWeeks / 4) {
                explanation = "Analyzed long-term XIRR merged with frequent recent mid-week dips."
            } else if (histScore > 40) {
                explanation = "Driven by strong historical outperformance overriding recent volatility."
            } else {
                explanation = "Optimal risk-reward balance based on recent market momentum."
            }
        }
    }
    
    if (suggestLumpsum) {
        explanation += " Favorable valuation drop detected; partial lumpsum recommended."
        bestConfidence = (bestConfidence + 4).coerceAtMost(99)
    }
    
    val targetDayConst = when(bestDay) {
        "Monday" -> Calendar.MONDAY
        "Tuesday" -> Calendar.TUESDAY
        "Wednesday" -> Calendar.WEDNESDAY
        "Thursday" -> Calendar.THURSDAY
        "Friday" -> Calendar.FRIDAY
        else -> Calendar.MONDAY
    }
    
    cal.timeInMillis = System.currentTimeMillis()
    var addedDays = 0
    while(cal.get(Calendar.DAY_OF_WEEK) != targetDayConst || addedDays == 0) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
        addedDays++
    }
    val nextDateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(cal.time)
    val fullDate = "$bestDay, $nextDateStr"
    
    return RecommendationData(bestDay, fullDate, bestConfidence, explanation, suggestLumpsum)
}
