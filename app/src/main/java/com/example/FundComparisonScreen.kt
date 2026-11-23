package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class FundCompareData(
    val schemeName: String,
    val records: List<NavRecord>,
    val color: Color,
    val category: String = "Fund"
)

data class SelectableCompareItem(
    val id: String,
    val name: String,
    val category: String, // "Index", "Portfolio", "Quant Direct"
    val isIndex: Boolean = false,
    val color: Color
)

fun getCompareColor(schemeName: String, fallbackIdx: Int = 0): Color {
    return when {
        schemeName.contains("Nifty 50 TRI", ignoreCase = true) || schemeName.equals("Nifty 50", ignoreCase = true) -> Color(0xFFFFB300) // Amber Gold
        schemeName.contains("Midcap 150", ignoreCase = true) -> Color(0xFFFF7043) // Coral
        schemeName.contains("Smallcap 250", ignoreCase = true) -> Color(0xFF26C6DA) // Cyan
        schemeName.contains("500", ignoreCase = true) -> Color(0xFFAB47BC) // Purple
        schemeName.contains("Multi Asset", ignoreCase = true) -> Color(0xFF9C27B0)
        schemeName.contains("Small Cap", ignoreCase = true) -> Color(0xFF00E676)
        schemeName.contains("Flexi Cap", ignoreCase = true) -> Color(0xFF64B5F6)
        schemeName.contains("Active Asset", ignoreCase = true) -> Color(0xFF29B6F6)
        schemeName.contains("Liquid", ignoreCase = true) -> Color(0xFF81C784)
        schemeName.contains("Momentum", ignoreCase = true) -> Color(0xFFFF4081)
        schemeName.contains("Infrastructure", ignoreCase = true) -> Color(0xFFFFAB40)
        schemeName.contains("Healthcare", ignoreCase = true) -> Color(0xFF00B0FF)
        schemeName.contains("BFSI", ignoreCase = true) -> Color(0xFF7C4DFF)
        schemeName.contains("Value", ignoreCase = true) -> Color(0xFFAEEA00)
        else -> {
            val palette = listOf(
                Color(0xFF64B5F6), Color(0xFFFF9800), Color(0xFFE91E63), 
                Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFF00BCD4),
                Color(0xFFFF5252), Color(0xFF00E676), Color(0xFFFFD600),
                Color(0xFFB388FF), Color(0xFF00B0FF), Color(0xFFFF6E40),
                Color(0xFFA7FFEB), Color(0xFFEA80FC), Color(0xFF26A69A),
                Color(0xFFD4E157)
            )
            palette[Math.abs(schemeName.hashCode() + fallbackIdx) % palette.size]
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundComparisonScreen(
    baseSchemeName: String,
    allSchemes: List<String>,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Standard benchmark indices
    val benchmarkIndices = remember {
        listOf(
            "Nifty 50 TRI",
            "Nifty Midcap 150 TRI",
            "Nifty Smallcap 250 TRI",
            "Nifty 500 TRI"
        )
    }

    // Benchmark Quant Mutual Funds
    val quantBenchmarkFunds = remember {
        IndexBenchmarkSimulator.benchmarkFunds.keys.toList()
    }

    // Build master list of all selectable items
    val allSelectableItems = remember(allSchemes) {
        val list = mutableListOf<SelectableCompareItem>()
        // 1. Indices
        benchmarkIndices.forEach { idxName ->
            list.add(SelectableCompareItem(idxName, idxName, "Index", isIndex = true, color = getCompareColor(idxName)))
        }
        // 2. Portfolio Funds
        allSchemes.forEach { sch ->
            if (list.none { it.id == sch }) {
                list.add(SelectableCompareItem(sch, sch, "Portfolio", isIndex = false, color = getCompareColor(sch)))
            }
        }
        // 3. Quant Direct Benchmark Funds
        quantBenchmarkFunds.forEach { qFund ->
            if (list.none { it.id == qFund }) {
                list.add(SelectableCompareItem(qFund, qFund, "Quant Direct", isIndex = false, color = getCompareColor(qFund)))
            }
        }
        list
    }

    var selectedSchemes by remember { mutableStateOf(listOf(baseSchemeName)) }
    var showBenchmarkSelector by remember { mutableStateOf(false) }
    var fundsData by remember { mutableStateOf<List<FundCompareData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var timeFilter by remember { mutableStateOf("Compare") }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customValue by remember { mutableStateOf("5") }
    var customUnit by remember { mutableStateOf("Years") }

    val allDatesSet = mutableSetOf<String>()
    fundsData.forEach { fd -> allDatesSet.addAll(fd.records.map { it.dateStr }) }
    val sortedDates = remember(fundsData) {
        allDatesSet.toList().sortedWith { d1, d2 ->
            val date1 = parseDateAny(d1)
            val date2 = parseDateAny(d2)
            if (date1 != null && date2 != null) date1.compareTo(date2) else d1.compareTo(d2)
        }
    }

    val filteredDates = remember(sortedDates, timeFilter, customStartDate, customEndDate, customValue, customUnit) {
        if (sortedDates.isEmpty()) return@remember emptyList()
        if (timeFilter == "Compare" || timeFilter == "ALL") return@remember sortedDates
        if (timeFilter == "Calendar") {
            val startMillis = customStartDate ?: 0L
            val endMillis = customEndDate ?: Long.MAX_VALUE
            return@remember sortedDates.filter {
                val dTime = parseDateAny(it)?.time ?: 0L
                dTime in startMillis..endMillis
            }
        }
        val end = sortedDates.last()
        val endDate = parseDateAny(end) ?: return@remember sortedDates
        val cal = Calendar.getInstance().apply { time = endDate }
        when (timeFilter) {
            "1M" -> cal.add(Calendar.MONTH, -1)
            "3M" -> cal.add(Calendar.MONTH, -3)
            "6M" -> cal.add(Calendar.MONTH, -6)
            "1Y" -> cal.add(Calendar.YEAR, -1)
            "3Y" -> cal.add(Calendar.YEAR, -3)
            "Custom" -> {
                val amt = customValue.toIntOrNull() ?: 1
                when (customUnit) {
                    "Days" -> cal.add(Calendar.DAY_OF_YEAR, -amt)
                    "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, -amt)
                    "Months" -> cal.add(Calendar.MONTH, -amt)
                    "Years" -> cal.add(Calendar.YEAR, -amt)
                }
            }
        }
        val cutoff = cal.time
        sortedDates.filter {
            val d = parseDateAny(it)
            d != null && !d.before(cutoff)
        }
    }

    // Fetch data whenever selectedSchemes changes
    LaunchedEffect(selectedSchemes) {
        if (selectedSchemes.isEmpty()) {
            fundsData = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val newFundsData = mutableListOf<FundCompareData>()
            
            selectedSchemes.forEachIndexed { index, scheme ->
                val color = getCompareColor(scheme, index)
                val isIndex = benchmarkIndices.any { it.equals(scheme, ignoreCase = true) }
                val isQuantFund = quantBenchmarkFunds.any { it.equals(scheme, ignoreCase = true) }

                val dbRecords = db.fundDao().getHistoricalNavs(scheme).map { NavRecord(it.dateStr, it.navValue) }
                val records: List<NavRecord> = IndexBenchmarkSimulator.getFullHistoricalNavs(context, scheme, dbRecords).ifEmpty {
                    val cached = NavCache.getRecords(scheme)
                    if (cached.isNotEmpty()) cached else dbRecords
                }
                if (records.isNotEmpty()) {
                    NavCache.addAll(scheme, records)
                }

                val category = when {
                    isIndex -> "Index"
                    allSchemes.contains(scheme) -> "Portfolio"
                    else -> "Benchmark"
                }

                newFundsData.add(
                    FundCompareData(
                        schemeName = scheme,
                        records = records.sortedBy { parseDateAny(it.dateStr)?.time ?: 0 },
                        color = color,
                        category = category
                    )
                )
            }
            fundsData = newFundsData
        }
        isLoading = false
    }

    var selectedIndexA by remember { mutableStateOf<Int?>(null) }
    var selectedIndexB by remember { mutableStateOf<Int?>(null) }
    var currentProgressA by remember { mutableStateOf(0f) }
    var currentProgressB by remember { mutableStateOf(0f) }

    fun getYoungestInceptionIndex(funds: List<FundCompareData>, allDates: List<String>): Int {
        if (funds.isEmpty() || allDates.isEmpty()) return 0
        val inceptions = funds.mapNotNull { fd ->
            fd.records.minByOrNull { parseDateAny(it.dateStr)?.time ?: Long.MAX_VALUE }?.let { rec ->
                parseDateAny(rec.dateStr)?.time
            }
        }
        val youngestTime = inceptions.maxOrNull() ?: return 0
        var closestIdx = 0
        var minDiff = Long.MAX_VALUE
        allDates.forEachIndexed { idx, dStr ->
            val t = parseDateAny(dStr)?.time ?: 0L
            if (t > 0L) {
                val diff = kotlin.math.abs(t - youngestTime)
                if (diff < minDiff) {
                    minDiff = diff
                    closestIdx = idx
                }
            }
        }
        return closestIdx
    }

    LaunchedEffect(fundsData, filteredDates.size) {
        if (filteredDates.isNotEmpty()) {
            val youngestIdx = getYoungestInceptionIndex(fundsData, filteredDates)
            selectedIndexA = youngestIdx
            selectedIndexB = filteredDates.lastIndex
            currentProgressA = youngestIdx.toFloat() / maxOf(1, filteredDates.lastIndex)
            currentProgressB = 1f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Compare Funds & Benchmarks", fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (selectedSchemes.isNotEmpty()) "${selectedSchemes.size} active comparison(s)" else "No funds selected",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Clear / Remove All Button
                    if (selectedSchemes.isNotEmpty()) {
                        IconButton(
                            onClick = { selectedSchemes = emptyList() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.LayersClear,
                                contentDescription = "Remove All",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            )
                        }
                    }
                    // Add Fund / Benchmark Button
                    IconButton(
                        onClick = { showBenchmarkSelector = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Addchart,
                            contentDescription = "Add Funds & Benchmarks",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Selected Funds & Benchmarks Horizontal Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "+ Add" Quick Action Chip
                Surface(
                    onClick = { showBenchmarkSelector = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Add Benchmark",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Remove All Quick Chip (if >= 2 items)
                if (selectedSchemes.size >= 2) {
                    Surface(
                        onClick = { selectedSchemes = emptyList() },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear All",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Clear All (${selectedSchemes.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Active Items Chips
                fundsData.forEach { fd ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = fd.color.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, fd.color.copy(alpha = 0.55f)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(fd.color, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            val cleanLabel = fd.schemeName
                                .replace(" - Direct (G)", "")
                                .replace("Direct Plan Growth Option", "")
                                .trim()
                            Text(
                                text = if (cleanLabel.length > 20) cleanLabel.take(18) + "…" else cleanLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = fd.color
                            )
                            Spacer(Modifier.width(4.dp))
                            // One-tap chip removal
                            IconButton(
                                onClick = {
                                    selectedSchemes = selectedSchemes.filter { it != fd.schemeName }
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(13.dp),
                                    tint = fd.color
                                )
                            }
                        }
                    }
                }
            }

            // Timeframes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeChips = listOf("Compare", "1M", "3M", "6M", "1Y", "3Y", "ALL", "Custom")
                timeChips.forEach { label ->
                    val isActive = timeFilter == label
                    Surface(
                        onClick = { timeFilter = label },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val isCalendarActive = timeFilter == "Calendar"
                Surface(
                    onClick = { timeFilter = "Calendar" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCalendarActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = if (isCalendarActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(34.dp)
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

            if (timeFilter == "Custom") {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Relative Past Range:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var isDropdownExpanded by remember { mutableStateOf(false) }
                            androidx.compose.foundation.text.BasicTextField(
                                value = customValue,
                                onValueChange = { customValue = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier.width(44.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)).padding(6.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Box {
                                Row(
                                    modifier = Modifier.clickable { isDropdownExpanded = true }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(customUnit, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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

            if (timeFilter == "Calendar" || timeFilter == "Compare") {
                fun parseDateStringToMillis(dateStr: String): Long {
                    val formats = listOf("dd-MMM-yyyy", "yyyy-MM-dd", "dd-MMM''yy", "dd-MMM-yy")
                    for (fStr in formats) {
                        try {
                            val fmt = SimpleDateFormat(fStr, Locale.US)
                            fmt.isLenient = false
                            val d = fmt.parse(dateStr)
                            if (d != null) return d.time
                        } catch (e: Exception) {}
                    }
                    return System.currentTimeMillis()
                }

                if (showStartDatePicker) {
                    val initialDate = if (timeFilter == "Compare") {
                        val minIdx = minOf(selectedIndexA ?: 0, selectedIndexB ?: 0).coerceIn(0, maxOf(0, filteredDates.lastIndex))
                        val dateStr = filteredDates.getOrNull(minIdx) ?: ""
                        parseDateStringToMillis(dateStr)
                    } else {
                        customStartDate ?: System.currentTimeMillis()
                    }
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
                    DatePickerDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val selectedMillis = datePickerState.selectedDateMillis
                                if (selectedMillis != null) {
                                    if (timeFilter == "Compare") {
                                        var closestIdx = 0
                                        var minDiff = Long.MAX_VALUE
                                        filteredDates.forEachIndexed { idx, dStr ->
                                            val ptTime = parseDateStringToMillis(dStr)
                                            if (ptTime > 0L) {
                                                val diff = kotlin.math.abs(ptTime - selectedMillis)
                                                if (diff < minDiff) {
                                                    minDiff = diff
                                                    closestIdx = idx
                                                }
                                            }
                                        }
                                        selectedIndexA = closestIdx
                                        currentProgressA = closestIdx.toFloat() / maxOf(1, filteredDates.lastIndex)
                                    } else {
                                        customStartDate = selectedMillis
                                    }
                                }
                                showStartDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    if (timeFilter == "Compare") {
                                        val youngestIdx = getYoungestInceptionIndex(fundsData, filteredDates)
                                        selectedIndexA = youngestIdx
                                        currentProgressA = youngestIdx.toFloat() / maxOf(1, filteredDates.lastIndex)
                                    } else {
                                        customStartDate = null
                                    }
                                    showStartDatePicker = false
                                }) { Text("Inception") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
                            }
                        }
                    ) {
                        DatePicker(
                            state = datePickerState,
                            showModeToggle = true
                        )
                    }
                }

                if (showEndDatePicker) {
                    val initialDate = if (timeFilter == "Compare") {
                        val maxIdx = maxOf(selectedIndexA ?: 0, selectedIndexB ?: 0).coerceIn(0, maxOf(0, filteredDates.lastIndex))
                        val dateStr = filteredDates.getOrNull(maxIdx) ?: ""
                        parseDateStringToMillis(dateStr)
                    } else {
                        customEndDate ?: System.currentTimeMillis()
                    }
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
                    DatePickerDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val selectedMillis = datePickerState.selectedDateMillis
                                if (selectedMillis != null) {
                                    if (timeFilter == "Compare") {
                                        var closestIdx = 0
                                        var minDiff = Long.MAX_VALUE
                                        filteredDates.forEachIndexed { idx, dStr ->
                                            val ptTime = parseDateStringToMillis(dStr)
                                            if (ptTime > 0L) {
                                                val diff = kotlin.math.abs(ptTime - selectedMillis)
                                                if (diff < minDiff) {
                                                    minDiff = diff
                                                    closestIdx = idx
                                                }
                                            }
                                        }
                                        selectedIndexB = closestIdx
                                        currentProgressB = closestIdx.toFloat() / maxOf(1, filteredDates.lastIndex)
                                    } else {
                                        customEndDate = selectedMillis
                                    }
                                }
                                showEndDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    if (timeFilter == "Compare") {
                                        selectedIndexB = filteredDates.lastIndex
                                        currentProgressB = 1f
                                    } else {
                                        customEndDate = null
                                    }
                                    showEndDatePicker = false
                                }) { Text("Latest") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
                            }
                        }
                    ) {
                        DatePicker(
                            state = datePickerState,
                            showModeToggle = true
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("FROM DATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(6.dp))
                                val displayStart = if (timeFilter == "Compare") {
                                    val idx = minOf(selectedIndexA ?: 0, selectedIndexB ?: 0).coerceIn(0, maxOf(0, filteredDates.lastIndex))
                                    val dStr = filteredDates.getOrNull(idx) ?: ""
                                    val dfOut = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                                    parseDateAny(dStr)?.let { dfOut.format(it) } ?: "Select Start"
                                } else {
                                    if (customStartDate != null) {
                                        val df = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                                        df.format(Date(customStartDate!!))
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
                    Box(modifier = Modifier.weight(0.08f).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                    Card(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("TO DATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(6.dp))
                                val displayEnd = if (timeFilter == "Compare") {
                                    val idx = maxOf(selectedIndexA ?: 0, selectedIndexB ?: 0).coerceIn(0, maxOf(0, filteredDates.lastIndex))
                                    val dStr = filteredDates.getOrNull(idx) ?: ""
                                    val dfOut = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                                    parseDateAny(dStr)?.let { dfOut.format(it) } ?: "Select End"
                                } else {
                                    if (customEndDate != null) {
                                        val df = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                                        df.format(Date(customEndDate!!))
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

            if (selectedSchemes.isEmpty()) {
                // Empty state when all items have been removed
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Addchart,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No Funds or Benchmarks Selected",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Add indices (e.g. Nifty 50 TRI) or portfolio funds to compare point-to-point returns.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showBenchmarkSelector = true },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Funds & Benchmarks")
                        }
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredDates.isNotEmpty()) {
                // Return Section
                val isDualMode = timeFilter == "Compare"
                val idxA = if (isDualMode) {
                    selectedIndexA?.coerceIn(0, maxOf(0, filteredDates.lastIndex)) ?: 0
                } else 0
                val idxB = if (isDualMode) {
                    selectedIndexB?.coerceIn(0, maxOf(0, filteredDates.lastIndex)) ?: maxOf(0, filteredDates.lastIndex)
                } else {
                    maxOf(0, filteredDates.lastIndex)
                }

                val sIdx = minOf(idxA, idxB)
                val eIdx = maxOf(idxA, idxB)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Compare Returns", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val dfOut = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                            val d1 = parseDateAny(filteredDates[sIdx])?.let { dfOut.format(it) } ?: filteredDates[sIdx]
                            val d2 = parseDateAny(filteredDates[eIdx])?.let { dfOut.format(it) } ?: filteredDates[eIdx]
                            Text("$d1 to $d2", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val tStart = parseDateAny(filteredDates[sIdx])?.time ?: 0L
                        val tEnd = parseDateAny(filteredDates[eIdx])?.time ?: 0L

                        fundsData.forEach { fd ->
                            val navStart = findLatestNavBeforeRecords(fd.records, tStart)
                            val navEnd = findLatestNavBeforeRecords(fd.records, tEnd)

                            if (navStart != null && navEnd != null && navStart > 0) {
                                val ret = ((navEnd - navStart) / navStart) * 100
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(modifier = Modifier.size(8.dp).background(fd.color, CircleShape))
                                        Spacer(Modifier.width(8.dp))
                                        val cleanLabel = fd.schemeName
                                            .replace(" - Direct (G)", "")
                                            .replace("Direct Plan Growth Option", "")
                                            .trim()
                                        Text(
                                            cleanLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (fd.category == "Index") {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = fd.color.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "INDEX",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = fd.color,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = String.format(Locale.US, "%+.2f%%", ret),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (ret >= 0) Color(0xFF00FF66) else Color(0xFFFF5252)
                                    )
                                }
                            }
                        }
                    }
                }

                // Chart Container
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    MultiFundChart(
                        dates = filteredDates,
                        fundsData = fundsData,
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
                        isDark = isDarkTheme,
                        isDualMarkerMode = timeFilter == "Compare"
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet for Comprehensive Selection (Add/Remove/Remove All)
    if (showBenchmarkSelector) {
        FundBenchmarkSelectionSheet(
            isOpen = showBenchmarkSelector,
            onDismiss = { showBenchmarkSelector = false },
            allItems = allSelectableItems,
            selectedItemIds = selectedSchemes.toSet(),
            onToggleItem = { id ->
                selectedSchemes = if (selectedSchemes.contains(id)) {
                    selectedSchemes - id
                } else {
                    selectedSchemes + id
                }
            },
            onSelectAll = {
                selectedSchemes = allSelectableItems.map { it.id }
            },
            onClearAll = {
                selectedSchemes = emptyList()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundBenchmarkSelectionSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    allItems: List<SelectableCompareItem>,
    selectedItemIds: Set<String>,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    if (!isOpen) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTab by remember { mutableStateOf("All") }

    val categories = listOf("All", "Indices", "Portfolio", "Quant Direct", "Selected")

    val filteredItems = remember(searchQuery, selectedCategoryTab, allItems, selectedItemIds) {
        allItems.filter { item ->
            val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            val matchesTab = when (selectedCategoryTab) {
                "All" -> true
                "Indices" -> item.category == "Index"
                "Portfolio" -> item.category == "Portfolio"
                "Quant Direct" -> item.category == "Quant Direct"
                "Selected" -> selectedItemIds.contains(item.id)
                else -> true
            }
            matchesSearch && matchesTab
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Add / Remove Benchmarks & Funds",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${selectedItemIds.size} of ${allItems.size} selected for comparison",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedItemIds.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search funds or indices…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            )

            Spacer(Modifier.height(12.dp))

            // Category Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { tab ->
                    val isSelected = selectedCategoryTab == tab
                    val count = when (tab) {
                        "All" -> allItems.size
                        "Indices" -> allItems.count { it.category == "Index" }
                        "Portfolio" -> allItems.count { it.category == "Portfolio" }
                        "Quant Direct" -> allItems.count { it.category == "Quant Direct" }
                        "Selected" -> selectedItemIds.size
                        else -> 0
                    }
                    Surface(
                        onClick = { selectedCategoryTab = tab },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = "$tab ($count)",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filteredItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No items found matching criteria", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(filteredItems, key = { it.id }) { item ->
                    val isChecked = selectedItemIds.contains(item.id)
                    Surface(
                        onClick = { onToggleItem(item.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isChecked) item.color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(
                            1.dp,
                            if (isChecked) item.color.copy(alpha = 0.6f) else Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(item.color, CircleShape)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = when (item.category) {
                                                "Index" -> Color(0xFFFFB300).copy(alpha = 0.18f)
                                                "Portfolio" -> Color(0xFF00E676).copy(alpha = 0.18f)
                                                else -> Color(0xFF64B5F6).copy(alpha = 0.18f)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = item.category.uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (item.category) {
                                                    "Index" -> Color(0xFFFFB300)
                                                    "Portfolio" -> Color(0xFF00C853)
                                                    else -> Color(0xFF1E88E5)
                                                },
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    val cleanName = item.name
                                        .replace(" - Direct (G)", "")
                                        .replace("Direct Plan Growth Option", "")
                                        .trim()
                                    Text(
                                        text = cleanName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleItem(item.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = item.color,
                                    checkmarkColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MultiFundChart(
    dates: List<String>,
    fundsData: List<FundCompareData>,
    selectedIndexA: Int?,
    selectedIndexB: Int?,
    currentProgressA: Float,
    currentProgressB: Float,
    onUpdateCompareIndices: (Int?, Int?, Float, Float) -> Unit,
    isDark: Boolean,
    isDualMarkerMode: Boolean
) {
    if (dates.isEmpty()) return

    val currentDates by rememberUpdatedState(dates)
    val currentSelectedIndexA by rememberUpdatedState(selectedIndexA)
    val currentSelectedIndexB by rememberUpdatedState(selectedIndexB)
    val currentProgressAState by rememberUpdatedState(currentProgressA)
    val currentProgressBState by rememberUpdatedState(currentProgressB)
    val currentOnUpdateCompareIndices by rememberUpdatedState(onUpdateCompareIndices)

    var activeDragMarker by remember { mutableStateOf<String?>(null) }

    val cachedChartData = remember(dates, fundsData, selectedIndexA, selectedIndexB, isDualMarkerMode) {
        if (dates.isEmpty() || fundsData.isEmpty()) return@remember null

        val sIdx = if (isDualMarkerMode && selectedIndexA != null && selectedIndexB != null) {
            minOf(selectedIndexA, selectedIndexB).coerceIn(0, dates.lastIndex)
        } else {
            0
        }
        val eIdx = if (isDualMarkerMode && selectedIndexA != null && selectedIndexB != null) {
            maxOf(selectedIndexA, selectedIndexB).coerceIn(0, dates.lastIndex)
        } else {
            dates.lastIndex
        }

        val activeDates = if (isDualMarkerMode && sIdx < eIdx) {
            dates.subList(sIdx, eIdx + 1)
        } else {
            dates
        }

        val dateTimes = activeDates.map { parseDateAny(it)?.time ?: 0L }
        val startTime = dateTimes.getOrNull(0) ?: 0L
        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE
        val fundsNormData = fundsData.map { fd ->
            val recordPairs = fd.records.map {
                (parseDateAny(it.dateStr)?.time ?: 0L) to it.nav
            }.sortedBy { it.first }
            val startNav = if (recordPairs.isNotEmpty()) {
                findLatestNavBeforePairs(recordPairs, startTime) ?: recordPairs.first().second
            } else {
                1.0
            }
            val normValues = dateTimes.map { dateTime ->
                val nav = if (recordPairs.isNotEmpty()) {
                    findLatestNavBeforePairs(recordPairs, dateTime) ?: startNav
                } else {
                    startNav
                }
                (nav / startNav) * 100.0
            }
            val localMin = normValues.minOrNull()?.toFloat() ?: 100f
            val localMax = normValues.maxOrNull()?.toFloat() ?: 100f
            if (localMin < minVal) minVal = localMin
            if (localMax > maxVal) maxVal = localMax
            Pair(normValues, fd.color)
        }
        Triple(Pair(fundsNormData, activeDates), minVal, maxVal)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .pointerInput(isDualMarkerMode) {
                if (!isDualMarkerMode) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val w = size.width.toFloat()
                        val datesList = currentDates
                        if (datesList.isEmpty()) return@detectTapGestures
                        val progress = (offset.x / w).coerceIn(0f, 1f)
                        val tappedIdx = Math.round(progress * (datesList.size - 1)).toInt().coerceIn(0, datesList.lastIndex)

                        val distA = Math.abs(progress - currentProgressAState)
                        val distB = Math.abs(progress - currentProgressBState)
                        if (currentSelectedIndexA == null) {
                            currentOnUpdateCompareIndices(tappedIdx, currentSelectedIndexB, progress, currentProgressBState)
                        } else if (currentSelectedIndexB == null) {
                            currentOnUpdateCompareIndices(currentSelectedIndexA, tappedIdx, currentProgressAState, progress)
                        } else {
                            if (distA < distB) {
                                currentOnUpdateCompareIndices(tappedIdx, currentSelectedIndexB, progress, currentProgressBState)
                            } else {
                                currentOnUpdateCompareIndices(currentSelectedIndexA, tappedIdx, currentProgressAState, progress)
                            }
                        }
                    }
                )
            }
            .pointerInput(isDualMarkerMode) {
                if (!isDualMarkerMode) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val datesList = currentDates
                        if (datesList.isEmpty()) return@detectHorizontalDragGestures
                        val progress = (offset.x / w).coerceIn(0f, 1f)

                        val distA = Math.abs(progress - currentProgressAState)
                        val distB = Math.abs(progress - currentProgressBState)
                        activeDragMarker = if (distA < distB) "A" else "B"

                        val newIdx = Math.round(progress * (datesList.size - 1)).toInt().coerceIn(0, datesList.lastIndex)
                        if (activeDragMarker == "A") {
                            currentOnUpdateCompareIndices(newIdx, currentSelectedIndexB, progress, currentProgressBState)
                        } else {
                            currentOnUpdateCompareIndices(currentSelectedIndexA, newIdx, currentProgressAState, progress)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val datesList = currentDates
                        if (datesList.isEmpty()) return@detectHorizontalDragGestures
                        val progress = (change.position.x / w).coerceIn(0f, 1f)
                        val newIdx = Math.round(progress * (datesList.size - 1)).toInt().coerceIn(0, datesList.lastIndex)

                        if (activeDragMarker == "A") {
                            currentOnUpdateCompareIndices(newIdx, currentSelectedIndexB, progress, currentProgressBState)
                        } else if (activeDragMarker == "B") {
                            currentOnUpdateCompareIndices(currentSelectedIndexA, newIdx, currentProgressAState, progress)
                        }
                    },
                    onDragEnd = { activeDragMarker = null },
                    onDragCancel = { activeDragMarker = null }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val usableH = h - 50f

        if (cachedChartData == null) return@Canvas
        val (pairData, minVal, maxVal) = cachedChartData
        val (fundsNormData, activeDates) = pairData
        val activeTotal = activeDates.size

        val normalizedPaths = mutableListOf<Pair<Path, Color>>()

        val range = maxVal - minVal
        val pad = if (range == 0f) 1f else range * 0.1f
        val renderMin = minVal - pad
        val renderMax = maxVal + pad
        val renderRange = renderMax - renderMin

        fun getY(value: Double): Float = usableH - (((value.toFloat() - renderMin) / renderRange) * usableH)

        fundsNormData.forEach { (normValues, color) ->
            val path = Path()
            var started = false
            normValues.forEachIndexed { i, v ->
                val px = getX(i, w, activeTotal)
                val py = getY(v)
                if (!started) { path.moveTo(px, py); started = true }
                else path.lineTo(px, py)
            }
            normalizedPaths.add(Pair(path, color))
        }

        clipRect {
            normalizedPaths.forEach { (path, color) ->
                drawPath(path, color, style = Stroke(width = 4f, join = StrokeJoin.Round, cap = StrokeCap.Round))
            }
        }

        if (isDualMarkerMode) {
            val xa = 0f
            val xb = w
            val needleColorA = Color(0xFF00FF66) // Vibrant Neon Green
            val needleColorB = Color(0xFFFFEA00) // Electric Yellow

            drawLine(needleColorA, Offset(xa, 0f), Offset(xa, usableH), strokeWidth = 5f)
            val baseNormA = fundsNormData.firstOrNull()?.first?.getOrNull(0)
            if (baseNormA != null) {
                val ya = getY(baseNormA)
                drawCircle(needleColorA, radius = 10f, center = Offset(xa, ya))
                drawCircle(Color.Black, radius = 4f, center = Offset(xa, ya))
            }

            drawLine(needleColorB, Offset(xb, 0f), Offset(xb, usableH), strokeWidth = 5f)
            val baseNormB = fundsNormData.firstOrNull()?.first?.getOrNull(activeTotal - 1)
            if (baseNormB != null) {
                val yb = getY(baseNormB)
                drawCircle(needleColorB, radius = 10f, center = Offset(xb, yb))
                drawCircle(Color.Black, radius = 4f, center = Offset(xb, yb))
            }
        }
    }
}

private fun getX(index: Int, w: Float, total: Int): Float {
    if (total <= 1) return 0f
    return (index.toFloat() / (total - 1)) * w
}

private val localSdf1 = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
}
private val localSdf2 = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
}
private val localSdf3 = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("dd-MMM''yy", Locale.US)
}
private val localSdf4 = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("dd-MMM-yy", Locale.US)
}

private fun parseDateAny(dateStr: String): Date? {
    val millis = IndexBenchmarkSimulator.parseAnyDateToMillis(dateStr)
    return if (millis != null) Date(millis) else null
}

private fun findLatestNavBeforePairs(recordPairs: List<Pair<Long, Double>>, targetTime: Long): Double? {
    if (recordPairs.isEmpty()) return null
    var low = 0
    var high = recordPairs.lastIndex
    var ans: Double? = null
    while (low <= high) {
        val mid = (low + high) ushr 1
        val midTime = recordPairs[mid].first
        if (midTime <= targetTime) {
            ans = recordPairs[mid].second
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return ans
}

private fun findLatestNavBeforeRecords(records: List<NavRecord>, targetTime: Long): Double? {
    if (records.isEmpty()) return null
    var low = 0
    var high = records.lastIndex
    var ans: Double? = null
    while (low <= high) {
        val mid = (low + high) ushr 1
        val midTime = parseDateAny(records[mid].dateStr)?.time ?: 0L
        if (midTime <= targetTime) {
            ans = records[mid].nav
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return ans
}
