package com.example

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.abs

const val TAB_PORTFOLIO_SUMMARY = "TAB_PORTFOLIO_SUMMARY"

data class FundSummaryRow(
    val schemeName: String,
    val category: String,
    val preferredMethod: String,
    val preferredXirr: Double,
    val preferredWealth: Double,
    val baselineMethod: String,
    val baselineXirr: Double,
    val alphaMargin: Double,
    val runnerUpMethod: String,
    val runnerUpXirr: Double,
    val totalNavCount: Int,
    val keyAdvantage: String,
    val predictiveAnalysis: PredictiveTimingAnalysis? = null
)

data class BacktestResult(
    val strategyName: String,
    val totalInvested: Double,
    val currentValue: Double,
    val absoluteReturnPct: Double,
    val xirrPct: Double,
    val peakDrawdownPct: Double,
    val growthTimeline: List<Pair<Long, Double>>, // Timestamp -> Value
    val installmentCount: Int = 0,
    val formulaDetail: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SipBacktestComparisonScreen(
    allFunds: List<FundUiItem>,
    isDarkTheme: Boolean,
    initialSchemeName: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSchemeName by remember(initialSchemeName) {
        mutableStateOf(
            if (initialSchemeName.isNotBlank() && allFunds.any { it.schemeName == initialSchemeName }) {
                initialSchemeName
            } else {
                TAB_PORTFOLIO_SUMMARY
            }
        )
    }
    var isLoading by remember { mutableStateOf(true) }
    var backtestResults by remember { mutableStateOf<List<BacktestResult>>(emptyList()) }
    var allFundsSummary by remember { mutableStateOf<List<FundSummaryRow>>(emptyList()) }
    var selectedChartFilter by remember { mutableStateOf("ALL") }
    var totalNavCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentFundPredictiveAnalysis by remember { mutableStateOf<PredictiveTimingAnalysis?>(null) }
    var showFundPredictiveModal by remember { mutableStateOf(false) }

    // Colors matching style guide
    val emeraldColor = Color(0xFF10B981)
    val sapphireColor = Color(0xFF3B82F6)
    val goldColor = Color(0xFFF59E0B)
    val redColor = Color(0xFFEF4444)
    val darkBg = Color(0xFF0D1117)
    val lightBg = Color(0xFFF8FAFC)

    val surfaceColor = if (isDarkTheme) Color(0xFF161B22) else Color(0xFFFFFFFF)
    val onSurfaceColor = if (isDarkTheme) Color(0xFFF0F6FC) else Color(0xFF1E293B)
    val borderColor = if (isDarkTheme) Color(0xFF30363D) else Color(0xFFE2E8F0)

    // Parse date helper
    fun parseDateAnyLocal(dateStr: String): Date? {
        val formats = listOf("yyyy-MM-dd", "dd-MMM-yyyy", "dd-MM-yyyy")
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                sdf.isLenient = false
                val d = sdf.parse(dateStr)
                if (d != null) return d
            } catch (e: Exception) {}
        }
        return null
    }

    // Run backtest calculation in coroutine
    LaunchedEffect(selectedSchemeName, allFunds) {
        isLoading = true
        errorMessage = null
        scope.launch(Dispatchers.Default) {
            try {
                val db = AppDatabase.getDatabase(context)

                if (selectedSchemeName == TAB_PORTFOLIO_SUMMARY) {
                    val summaryList = mutableListOf<FundSummaryRow>()
                    for (fund in allFunds) {
                        val dbRecords = db.fundDao().getHistoricalNavs(fund.schemeName)
                        val sorted = dbRecords.mapNotNull { rec ->
                            val date = SipBacktestEngine.parseDateAnyLocal(rec.dateStr)
                            if (date != null && rec.navValue > 0) Pair(rec, date.time) else null
                        }.sortedBy { it.second }

                        if (sorted.size >= 10) {
                            val results = SipBacktestEngine.runBacktestSimulation(sorted)
                            val row = SipBacktestEngine.buildFundSummaryRow(fund.schemeName, sorted.size, results, sorted)
                            summaryList.add(row)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        allFundsSummary = summaryList
                        isLoading = false
                    }
                } else {
                    val dbRecords = db.fundDao().getHistoricalNavs(selectedSchemeName)
                    if (dbRecords.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "No historical NAV records found in database for $selectedSchemeName."
                            isLoading = false
                        }
                        return@launch
                    }

                    val sorted = dbRecords.mapNotNull { rec ->
                        val date = SipBacktestEngine.parseDateAnyLocal(rec.dateStr)
                        if (date != null && rec.navValue > 0) Pair(rec, date.time) else null
                    }.sortedBy { it.second }

                    if (sorted.size < 30) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Insufficient data points (found ${sorted.size}, need at least 30) to perform backtest."
                            isLoading = false
                        }
                        return@launch
                    }

                    val computedResults = SipBacktestEngine.runBacktestSimulation(sorted)
                    val rankedSips = computedResults.filter { !it.strategyName.contains("Lumpsum") }.sortedByDescending { it.xirrPct }
                    val cleanBest = (rankedSips.firstOrNull()?.strategyName ?: "Monthly Dip").replace(Regex("[📉🎯🧠📅💰]"), "").trim()
                    val predictive = SipPredictiveTimingEngine.analyze(
                        schemeName = selectedSchemeName,
                        category = "Fund",
                        preferredMethod = cleanBest,
                        sorted = sorted
                    )

                    withContext(Dispatchers.Main) {
                        backtestResults = computedResults
                        totalNavCount = sorted.size
                        currentFundPredictiveAnalysis = predictive
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Error run backtest: ${e.message}"
                    isLoading = false
                }
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Historical Backtest Audit",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Smart vs Blind Daily/Weekly/Monthly SIP",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkTheme) darkBg else lightBg
                )
            )
        },
        containerColor = if (isDarkTheme) darkBg else lightBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Fund Selection LazyRow Tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. First Tab: Summary Matrix Table
                item {
                    val isSummarySelected = selectedSchemeName == TAB_PORTFOLIO_SUMMARY
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSummarySelected) sapphireColor else surfaceColor
                            )
                            .border(
                                1.dp,
                                if (isSummarySelected) sapphireColor else borderColor,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedSchemeName = TAB_PORTFOLIO_SUMMARY
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "📊 Summary Table",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isSummarySelected) Color.White else onSurfaceColor
                        )
                    }
                }

                // 2. Individual Fund Tabs
                items(allFunds) { fund ->
                    val isSelected = fund.schemeName == selectedSchemeName
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) sapphireColor else surfaceColor
                            )
                            .border(
                                1.dp,
                                if (isSelected) sapphireColor else borderColor,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedSchemeName = fund.schemeName
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = fund.schemeName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isSelected) Color.White else onSurfaceColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = sapphireColor)
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage!!,
                        color = redColor,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (selectedSchemeName == TAB_PORTFOLIO_SUMMARY) {
                Box(modifier = Modifier.weight(1f)) {
                    AllFundsSummaryTableView(
                        summaryList = allFundsSummary,
                        isDarkTheme = isDarkTheme,
                        onSelectFund = { scheme ->
                            selectedSchemeName = scheme
                        }
                    )
                }
            } else {
                val smartSipResult = backtestResults.find { it.strategyName.contains("Smart") }
                val blindWeeklyResult = backtestResults.find { it.strategyName.contains("Weekly") }
                val blindDailyResult = backtestResults.find { it.strategyName.contains("Daily") }
                val blindMonthlyResult = backtestResults.find { it.strategyName.contains("Monthly") }

                val smartSipXirr = smartSipResult?.xirrPct ?: 0.0
                val avgBlindXirr = listOfNotNull(
                    blindWeeklyResult?.xirrPct,
                    blindDailyResult?.xirrPct,
                    blindMonthlyResult?.xirrPct
                ).average()

                val xirrOutperformance = smartSipXirr - avgBlindXirr
                val isWorthy = xirrOutperformance > 0.5

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // 0. Predictive Timing Oracle Card
                    currentFundPredictiveAnalysis?.let { pa ->
                        item {
                            val verdictColor = when (pa.verdict) {
                                PredictiveVerdict.BUY_TODAY -> emeraldColor
                                PredictiveVerdict.WAIT_FOR_DIP -> goldColor
                                PredictiveVerdict.DIP_ALREADY_GONE -> redColor
                            }
                            val verdictIcon = when (pa.verdict) {
                                PredictiveVerdict.BUY_TODAY -> Icons.Default.CheckCircle
                                PredictiveVerdict.WAIT_FOR_DIP -> Icons.Default.DateRange
                                PredictiveVerdict.DIP_ALREADY_GONE -> Icons.Default.Close
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFundPredictiveModal = true }
                                    .testTag("fund_predictive_timing_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = verdictColor.copy(alpha = if (isDarkTheme) 0.12f else 0.08f)
                                ),
                                border = BorderStroke(1.5.dp, verdictColor.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(verdictColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = verdictIcon,
                                                    contentDescription = null,
                                                    tint = verdictColor,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "PREDICTIVE TIMING ORACLE",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = verdictColor,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Text(
                                                    text = pa.verdictHeadline,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = onSurfaceColor,
                                                    lineHeight = 20.sp
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = verdictColor.copy(alpha = 0.18f),
                                            border = BorderStroke(0.8.dp, verdictColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "${pa.confidenceScorePct}% CONFIDENCE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = verdictColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = verdictColor.copy(alpha = if (isDarkTheme) 0.2f else 0.12f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Directive: ${pa.actionDirective}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = onSurfaceColor,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }

                                    Text(
                                        text = pa.verdictSubtitle,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = onSurfaceColor.copy(alpha = 0.9f)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "NAV: ₹${String.format(Locale.US, "%.4f", pa.currentNav)} • Trough: ₹${String.format(Locale.US, "%.4f", pa.cycleTroughNav)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Open Full Oracle",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = sapphireColor
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = sapphireColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 1. Audit Worthiness Summary Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isWorthy) emeraldColor.copy(alpha = 0.12f) else goldColor.copy(alpha = 0.12f)
                            ),
                            border = BorderStroke(
                                1.5.dp,
                                if (isWorthy) emeraldColor.copy(alpha = 0.5f) else goldColor.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isWorthy) emeraldColor.copy(alpha = 0.2f) else goldColor.copy(alpha = 0.2f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isWorthy) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (isWorthy) emeraldColor else goldColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isWorthy) "THE EXERCISE IS 100% WORTHY! 🚀" else "THE EXERCISE IS REASONABLY WORTHY 📊",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = if (isWorthy) emeraldColor else goldColor
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    val formattedAlpha = String.format(Locale.US, "%.2f%%", xirrOutperformance)
                                    Text(
                                        text = if (isWorthy) {
                                            "Our custom Smart Tactical SIP out-performed standard blind averages by +$formattedAlpha net alpha annually, buying significantly more shares in deep pullbacks and saving cash at peaks."
                                        } else {
                                            "Due to standard market momentum, standard regular SIP closely tracks progress, but Smart SIP provides critical downside protection of ${String.format(Locale.US, "%.2f%%", (blindWeeklyResult?.peakDrawdownPct ?: 0.0) - (smartSipResult?.peakDrawdownPct ?: 0.0))}% lower drawdowns."
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            lineHeight = 16.sp
                                        ),
                                        color = onSurfaceColor
                                    )
                                }
                            }
                        }
                    }

                    // 2. Data Validation & Proportionality Breakdown Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = surfaceColor
                            ),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = sapphireColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "DATA VALIDATION & PROPORTIONALITY",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = sapphireColor
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = sapphireColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "$totalNavCount NAVs Found",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = sapphireColor
                                        )
                                    }
                                }

                                Text(
                                    text = "All strategies are evaluated over all $totalNavCount historical NAV trading days using a standardized ₹10,000/month baseline (20 trading days) and Multi-Variant Quantitative Modeling:",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = onSurfaceColor
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isDarkTheme) Color(0xFF0F141C) else Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "• Smart Tactical SIP (0.5×–2.5×): Multi-Variant Booster (52W/ATH Pullback, 20/50/200 DMA Ribbon, RSI-14 & 20D Volatility)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                        color = sapphireColor
                                    )
                                    Text(
                                        text = "• Multi-Tier Tactical Lumpsum: Multi-tier dip entry (₹30,000 Capitulation, ₹20,000 Correction, ₹10,000 Dip Entry) with cooldown logic",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                        color = redColor
                                    )
                                    Text(
                                        text = "• Weekly Dip SIP: ${(totalNavCount + 4) / 5} installments (lowest NAV of each 5-day week) × ₹2,500 = ₹${String.format(Locale.US, "%,d", ((totalNavCount + 4) / 5) * 2500)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF06B6D4)
                                    )
                                    Text(
                                        text = "• Monthly Dip SIP: ${(totalNavCount + 19) / 20} installments (lowest NAV of each 20-day month) × ₹10,000 = ₹${String.format(Locale.US, "%,d", ((totalNavCount + 19) / 20) * 10000)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFA855F7)
                                    )
                                    Text(
                                        text = "• Blind Daily SIP: $totalNavCount installments × ₹500/day = ₹${String.format(Locale.US, "%,d", totalNavCount * 500)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = emeraldColor
                                    )
                                    Text(
                                        text = "• Blind Weekly SIP: ${(totalNavCount + 4) / 5} installments (fixed 5th day) × ₹2,500/wk = ₹${String.format(Locale.US, "%,d", ((totalNavCount + 4) / 5) * 2500)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = goldColor
                                    )
                                    Text(
                                        text = "• Blind Monthly SIP: ${(totalNavCount + 19) / 20} installments (fixed 20th day) × ₹10,000/mo = ₹${String.format(Locale.US, "%,d", ((totalNavCount + 19) / 20) * 10000)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Head-to-Head Stats Cards / Table
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(surfaceColor, RoundedCornerShape(12.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Strategy Comparison Matrix",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = onSurfaceColor,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            backtestResults.forEach { res ->
                                val isSmart = res.strategyName.contains("Smart")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .background(
                                            if (isSmart) sapphireColor.copy(alpha = 0.1f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.3f)) {
                                        Text(
                                            text = res.strategyName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = onSurfaceColor
                                        )
                                        Text(
                                            text = "Invested: ₹${String.format(Locale.US, "%,.0f", res.totalInvested)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSmart) sapphireColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (res.formulaDetail.isNotBlank()) {
                                            Text(
                                                text = res.formulaDetail,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "XIRR: ${String.format(Locale.US, "%.2f%%", res.xirrPct)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black),
                                            color = if (res.xirrPct >= avgBlindXirr) emeraldColor else onSurfaceColor
                                        )
                                        Text(
                                            text = "Abs: ${String.format(Locale.US, "%.1f%%", res.absoluteReturnPct)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "Max Drawdown",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f%%", res.peakDrawdownPct)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (res.peakDrawdownPct > -10.0) emeraldColor else redColor
                                        )
                                    }
                                }
                                Divider(color = borderColor.copy(alpha = 0.5f))
                            }
                        }
                    }

                    // 3. Enhanced Wealth Growth Line Chart Visualizer with Anti-Overlap Isolation
                    item {
                        val sipResults = backtestResults.filter { !it.strategyName.contains("Lumpsum") }
                        val topPerformer = sipResults.maxByOrNull { it.xirrPct } ?: backtestResults.maxByOrNull { it.xirrPct }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(surfaceColor, RoundedCornerShape(14.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Historical Wealth Growth Comparison",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = onSurfaceColor
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Tracking accumulated portfolio corpus over entire timeline",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectedChartFilter != "ALL") {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = sapphireColor.copy(alpha = 0.15f),
                                        modifier = Modifier.clickable { selectedChartFilter = "ALL" }
                                    ) {
                                        Text(
                                            text = "Show All Lines",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = sapphireColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Interactive Anti-Overlap Strategy Selector Chips
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val filterOptions = listOf(
                                    "ALL" to "All Lines (7)",
                                    "WINNER" to "🏆 Winner Only",
                                    "Smart" to "Smart SIP",
                                    "Weekly Dip" to "Weekly Dip",
                                    "Monthly Dip" to "Monthly Dip",
                                    "Blind Weekly" to "Blind Weekly",
                                    "Blind Monthly" to "Blind Monthly",
                                    "Daily" to "Daily SIP"
                                )
                                items(filterOptions) { (filterKey, label) ->
                                    val isSelected = selectedChartFilter == filterKey
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) sapphireColor else surfaceColor,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) sapphireColor else borderColor.copy(alpha = 0.7f)
                                        ),
                                        modifier = Modifier.clickable {
                                            selectedChartFilter = if (isSelected && filterKey != "ALL") "ALL" else filterKey
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else onSurfaceColor,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // Active Focus Status Pill
                            if (selectedChartFilter != "ALL") {
                                val focusedStrategy = when (selectedChartFilter) {
                                    "WINNER" -> topPerformer
                                    else -> backtestResults.find { it.strategyName.contains(selectedChartFilter) }
                                }
                                if (focusedStrategy != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = emeraldColor.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Isolating: ${focusedStrategy.strategyName}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = emeraldColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "₹${String.format(Locale.US, "%,.0f", focusedStrategy.currentValue)} (XIRR ${String.format(Locale.US, "%.1f%%", focusedStrategy.xirrPct)})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = onSurfaceColor
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Draw Wealth Chart
                            val timelines = backtestResults.map { it.growthTimeline }
                            if (timelines.isNotEmpty() && timelines.first().isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val width = size.width
                                        val height = size.height

                                        val maxVal = timelines.flatMap { t -> t.map { it.second } }.maxOrNull() ?: 1.0
                                        val minVal = 0.0

                                        // Horizontal Grid lines
                                        drawLine(
                                            color = borderColor.copy(alpha = 0.3f),
                                            start = Offset(0f, height * 0.25f),
                                            end = Offset(width, height * 0.25f),
                                            strokeWidth = 1f
                                        )
                                        drawLine(
                                            color = borderColor.copy(alpha = 0.3f),
                                            start = Offset(0f, height * 0.5f),
                                            end = Offset(width, height * 0.5f),
                                            strokeWidth = 1f
                                        )
                                        drawLine(
                                            color = borderColor.copy(alpha = 0.3f),
                                            start = Offset(0f, height * 0.75f),
                                            end = Offset(width, height * 0.75f),
                                            strokeWidth = 1f
                                        )

                                        // Determine which lines to draw in background vs foreground
                                        backtestResults.forEach { res ->
                                            val tLine = res.growthTimeline
                                            if (tLine.size > 2) {
                                                val isSmart = res.strategyName.contains("Smart")
                                                val isWinner = res == topPerformer
                                                val isFocused = when (selectedChartFilter) {
                                                    "ALL" -> true
                                                    "WINNER" -> isWinner
                                                    else -> res.strategyName.contains(selectedChartFilter)
                                                }

                                                val strokeColor = when {
                                                    res.strategyName.contains("Smart") -> sapphireColor
                                                    res.strategyName.contains("Weekly Dip") -> Color(0xFF06B6D4)
                                                    res.strategyName.contains("Monthly Dip") -> Color(0xFFA855F7)
                                                    res.strategyName.contains("Lumpsum") -> redColor
                                                    res.strategyName.contains("Daily") -> emeraldColor
                                                    res.strategyName.contains("Blind Weekly") -> goldColor
                                                    res.strategyName.contains("Blind Monthly") -> Color(0xFF94A3B8)
                                                    else -> borderColor
                                                }

                                                val strokeAlpha = if (selectedChartFilter == "ALL") {
                                                    if (isWinner) 1.0f else 0.85f
                                                } else {
                                                    if (isFocused) 1.0f else 0.15f
                                                }

                                                val strokeWidth = if (selectedChartFilter == "ALL") {
                                                    if (isWinner) 4f else if (isSmart) 3.2f else 2.2f
                                                } else {
                                                    if (isFocused) 4.5f else 1.5f
                                                }

                                                val path = Path()
                                                val nPoints = tLine.size
                                                var lastX = 0f
                                                var lastY = 0f

                                                tLine.forEachIndexed { pIdx, pt ->
                                                    val x = (pIdx.toFloat() / (nPoints - 1)) * width
                                                    val y = height - (((pt.second - minVal) / (maxVal - minVal)).toFloat() * height)
                                                    if (pIdx == 0) {
                                                        path.moveTo(x, y)
                                                    } else {
                                                        path.lineTo(x, y)
                                                    }
                                                    if (pIdx == nPoints - 1) {
                                                        lastX = x
                                                        lastY = y
                                                    }
                                                }

                                                drawPath(
                                                    path = path,
                                                    color = strokeColor.copy(alpha = strokeAlpha),
                                                    style = Stroke(width = strokeWidth)
                                                )

                                                // Draw terminal point for focused line or winner
                                                if (isFocused || (selectedChartFilter == "ALL" && isWinner)) {
                                                    drawCircle(
                                                        color = strokeColor,
                                                        radius = if (isFocused) 6f else 4f,
                                                        center = Offset(lastX, lastY)
                                                    )
                                                    drawCircle(
                                                        color = Color.White,
                                                        radius = 2.5f,
                                                        center = Offset(lastX, lastY)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Interactive Chart Legend - Tapping any item isolates it
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                selectedChartFilter = if (selectedChartFilter == "Smart") "ALL" else "Smart"
                                            }
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(sapphireColor))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "Smart SIP",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedChartFilter == "Smart") sapphireColor else onSurfaceColor
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                selectedChartFilter = if (selectedChartFilter == "Weekly Dip") "ALL" else "Weekly Dip"
                                            }
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF06B6D4)))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "Weekly Dip",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedChartFilter == "Weekly Dip") Color(0xFF06B6D4) else onSurfaceColor
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                selectedChartFilter = if (selectedChartFilter == "Monthly Dip") "ALL" else "Monthly Dip"
                                            }
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFA855F7)))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "Monthly Dip",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedChartFilter == "Monthly Dip") Color(0xFFA855F7) else onSurfaceColor
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                selectedChartFilter = if (selectedChartFilter == "Lumpsum") "ALL" else "Lumpsum"
                                            }
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(redColor))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "Lumpsum Dip",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedChartFilter == "Lumpsum") redColor else redColor
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                selectedChartFilter = if (selectedChartFilter == "Daily") "ALL" else "Daily"
                                            }
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(emeraldColor))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "Daily SIP",
                                                fontSize = 10.sp,
                                                color = if (selectedChartFilter == "Daily") emeraldColor else onSurfaceColor
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                selectedChartFilter = if (selectedChartFilter == "Blind Weekly") "ALL" else "Blind Weekly"
                                            }
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(goldColor))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "Blind Weekly",
                                                fontSize = 10.sp,
                                                color = if (selectedChartFilter == "Blind Weekly") goldColor else onSurfaceColor
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                selectedChartFilter = if (selectedChartFilter == "Blind Monthly") "ALL" else "Blind Monthly"
                                            }
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF94A3B8)))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "Blind Monthly",
                                                fontSize = 10.sp,
                                                color = if (selectedChartFilter == "Blind Monthly") Color(0xFF94A3B8) else onSurfaceColor
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "💡 Tip: Tap any filter chip or legend label above to isolate its trajectory without overlap.",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 4. Best Performing SIP Method & Outperformance Margins Card
                    item {
                        val rankedSipList = backtestResults
                            .filter { !it.strategyName.contains("Lumpsum") }
                            .sortedByDescending { it.xirrPct }
                        val bestSipResult = rankedSipList.firstOrNull() ?: backtestResults.maxByOrNull { it.xirrPct }

                        if (bestSipResult != null) {
                            val competingStrategies = backtestResults.filter { it != bestSipResult }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkTheme) Color(0xFF141A24) else Color(0xFFFFFFFF)
                                ),
                                border = BorderStroke(
                                    1.5.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            goldColor.copy(alpha = 0.8f),
                                            emeraldColor.copy(alpha = 0.8f),
                                            sapphireColor.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Top Header Badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = goldColor.copy(alpha = 0.2f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("🏆", fontSize = 18.sp)
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "BEST PERFORMING SIP METHOD",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 0.8.sp
                                                    ),
                                                    color = goldColor
                                                )
                                                Text(
                                                    text = "Alpha margin audit against alternative SIP schedules",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = emeraldColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = emeraldColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "RANK #1",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = emeraldColor
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    // Winner Hero Box
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isDarkTheme) Color(0xFF0F1520) else Color(0xFFF8FAFC),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                1.dp,
                                                emeraldColor.copy(alpha = 0.35f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Text(
                                            text = bestSipResult.strategyName,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 17.sp
                                            ),
                                            color = onSurfaceColor
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "Best risk-adjusted compounder for $selectedSchemeName over entire backtest period",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        // 3 KPI Highlights
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "ANNUALIZED XIRR",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.5.sp
                                                    ),
                                                    color = emeraldColor
                                                )
                                                Text(
                                                    text = String.format(Locale.US, "%.2f%%", bestSipResult.xirrPct),
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 20.sp
                                                    ),
                                                    color = emeraldColor
                                                )
                                                Text(
                                                    text = "Abs: +${String.format(Locale.US, "%.1f%%", bestSipResult.absoluteReturnPct)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1.1f)) {
                                                Text(
                                                    text = "TOTAL WEALTH ACCUMULATED",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.5.sp
                                                    ),
                                                    color = sapphireColor
                                                )
                                                Text(
                                                    text = "₹${String.format(Locale.US, "%,.0f", bestSipResult.currentValue)}",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 17.sp
                                                    ),
                                                    color = onSurfaceColor
                                                )
                                                Text(
                                                    text = "Invested: ₹${String.format(Locale.US, "%,.0f", bestSipResult.totalInvested)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Column(
                                                modifier = Modifier.weight(0.9f),
                                                horizontalAlignment = Alignment.End
                                            ) {
                                                Text(
                                                    text = "MAX DRAWDOWN",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.5.sp
                                                    ),
                                                    color = if (bestSipResult.peakDrawdownPct > -10.0) emeraldColor else redColor
                                                )
                                                Text(
                                                    text = String.format(Locale.US, "%.1f%%", bestSipResult.peakDrawdownPct),
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 17.sp
                                                    ),
                                                    color = if (bestSipResult.peakDrawdownPct > -10.0) emeraldColor else redColor
                                                )
                                                Text(
                                                    text = "Capital Shield",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // Outperformance Margin Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "BEATING OTHER METHODS BY WHAT MARGIN",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = onSurfaceColor
                                        )
                                        Text(
                                            text = "${competingStrategies.size} Benchmarks",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = emeraldColor
                                            )
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Detailed comparative margin rows
                                    competingStrategies.forEach { other ->
                                        val xirrDelta = bestSipResult.xirrPct - other.xirrPct
                                        val wealthDelta = bestSipResult.currentValue - other.currentValue
                                        val wealthDeltaPct = if (other.currentValue > 0) ((bestSipResult.currentValue - other.currentValue) / other.currentValue) * 100.0 else 0.0
                                        val drawdownAdvantage = other.peakDrawdownPct - bestSipResult.peakDrawdownPct

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(
                                                    if (isDarkTheme) Color(0xFF19202B).copy(alpha = 0.7f) else Color(0xFFF1F5F9),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    borderColor.copy(alpha = 0.6f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1.2f)) {
                                                    Text(
                                                        text = "vs ${other.strategyName}",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = onSurfaceColor
                                                    )
                                                    Text(
                                                        text = "Actual: XIRR ${String.format(Locale.US, "%.2f%%", other.xirrPct)} • Wealth ₹${String.format(Locale.US, "%,.0f", other.currentValue)}",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (xirrDelta >= 0) emeraldColor.copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.15f),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (xirrDelta >= 0) emeraldColor.copy(alpha = 0.4f) else Color(0xFF64748B).copy(alpha = 0.4f)
                                                        )
                                                    ) {
                                                        Text(
                                                            text = if (xirrDelta >= 0) "▲ +${String.format(Locale.US, "%.2f%%", xirrDelta)} MARGIN" else "${String.format(Locale.US, "%.2f%%", xirrDelta)} MARGIN",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 11.sp
                                                            ),
                                                            color = if (xirrDelta >= 0) emeraldColor else onSurfaceColor,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = if (wealthDelta >= 0) "+₹${String.format(Locale.US, "%,.0f", wealthDelta)} (+${String.format(Locale.US, "%.1f%%", wealthDeltaPct)} more)" else "₹${String.format(Locale.US, "%,.0f", wealthDelta)}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        color = if (wealthDelta >= 0) emeraldColor else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Visual comparative progress bars
                                            Spacer(Modifier.height(8.dp))
                                            val maxValForBar = maxOf(bestSipResult.xirrPct, other.xirrPct, 1.0)
                                            val winnerFraction = (bestSipResult.xirrPct / maxValForBar).toFloat().coerceIn(0.05f, 1f)
                                            val competitorFraction = (other.xirrPct / maxValForBar).toFloat().coerceIn(0.05f, 1f)

                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Winner",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = emeraldColor,
                                                        modifier = Modifier.width(60.dp)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(6.dp)
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(borderColor.copy(alpha = 0.3f))
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(winnerFraction)
                                                                .fillMaxHeight()
                                                                .clip(RoundedCornerShape(3.dp))
                                                                .background(Brush.horizontalGradient(listOf(sapphireColor, emeraldColor)))
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Other SIP",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.width(60.dp)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(6.dp)
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(borderColor.copy(alpha = 0.3f))
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(competitorFraction)
                                                                .fillMaxHeight()
                                                                .clip(RoundedCornerShape(3.dp))
                                                                .background(Color(0xFF94A3B8))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    // Why It Wins Strategic Audit
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                emeraldColor.copy(alpha = 0.08f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .border(
                                                1.dp,
                                                emeraldColor.copy(alpha = 0.25f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = emeraldColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "WHY THIS METHOD WINS (ALPHA ENGINE)",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = emeraldColor
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "• Dynamic Dip Accumulation: Scales up capital up to 2.5× during market pullbacks and dips, securing significantly more mutual fund units at bargain NAVs.\n" +
                                                   "• Peak Capital Preservation: Avoids aggressive deployment when NAV extends far above moving averages, conserving cash reserve pool.\n" +
                                                   "• Compounding Velocity: A consistently lower average cost basis enables faster portfolio compounding upon recovery, sustaining a durable outperformance margin over fixed-calendar investing.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            ),
                                            color = onSurfaceColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Fund-Specific Investment Recommendation & Preferred Methods Playbook
                    item {
                        val rankedSipList = backtestResults
                            .filter { !it.strategyName.contains("Lumpsum") }
                            .sortedByDescending { it.xirrPct }
                        val bestSipResult = rankedSipList.firstOrNull() ?: backtestResults.maxByOrNull { it.xirrPct }
                        val secondBestSipResult = if (rankedSipList.size > 1) rankedSipList[1] else null
                        val worstSipResult = rankedSipList.lastOrNull()

                        val isMidSmallCap = selectedSchemeName.contains("Ex-Top 100", ignoreCase = true) ||
                                selectedSchemeName.contains("Mid", ignoreCase = true) ||
                                selectedSchemeName.contains("Small", ignoreCase = true) ||
                                selectedSchemeName.contains("Emerging", ignoreCase = true)

                        val isAssetAllocator = selectedSchemeName.contains("Asset Allocator", ignoreCase = true) ||
                                selectedSchemeName.contains("Multi Asset", ignoreCase = true) ||
                                selectedSchemeName.contains("Dynamic", ignoreCase = true) ||
                                selectedSchemeName.contains("Balanced", ignoreCase = true)

                        val fundCategoryTitle = when {
                            isMidSmallCap -> "High-Growth / Mid & Small Cap Equity"
                            isAssetAllocator -> "Dynamic Multi-Asset / Core Compounder"
                            else -> "Large-Cap & Core Broad Market Equity"
                        }

                        val fundVolatilityTag = when {
                            isMidSmallCap -> "High Volatility • Frequent Dip Alpha"
                            isAssetAllocator -> "Moderate Volatility • Low Drawdown"
                            else -> "Steady Trend • Moderate Pullbacks"
                        }

                        // Helper for clean strategy titles
                        fun cleanStrategyTitle(name: String?): String {
                            if (name == null) return "Systematic SIP"
                            return name.replace(Regex("[📉🎯🧠📅💰]"), "").trim()
                        }

                        // Strategy 1 details
                        val rank1Title = cleanStrategyTitle(bestSipResult?.strategyName)
                        val (rank1Plan, _) = when {
                            rank1Title.contains("Monthly Dip", ignoreCase = true) -> Pair(
                                "• Execution Plan: Deploy monthly capital (e.g. ₹10,000) on the lowest NAV trading day during each monthly 20-session cycle, or auto-trigger when NAV dips ≥ 2.0% intra-month.\n\n" +
                                "• Mechanical Advantage: Concentrates monthly capital at cyclical monthly troughs, securing the lowest average purchase NAV without requiring extra dynamic liquidity. Delivers maximum annualized compounding yield (XIRR).",
                                "Captures the intra-month volatility trough systematically."
                            )
                            rank1Title.contains("Smart Tactical", ignoreCase = true) -> Pair(
                                "• Execution Plan: Maintain a weekly base SIP + auto-trigger a 2.0× to 3.0× multiplier whenever NAV drops ≥ 2.5% below its 20-day moving average.\n\n" +
                                "• Mechanical Advantage: High-beta mid/small cap funds exhibit frequent sharp pullbacks that revert to the mean. Buying heavily on dips sharply reduces average cost per unit before cyclical rallies.",
                                "Maximizes total rupee wealth by scaling capital into drawdowns."
                            )
                            rank1Title.contains("Weekly Dip", ignoreCase = true) -> Pair(
                                "• Execution Plan: Invest weekly installments (e.g. ₹2,500) triggered on the lowest NAV session of each week.\n\n" +
                                "• Mechanical Advantage: Smooths short-term weekly fluctuations effectively, capturing intra-week dips before weekend reversals.",
                                "Captures weekly pullbacks automatically."
                            )
                            else -> Pair(
                                "• Execution Plan: Systematic regular accumulation with scheduled rebalancing and disciplined step-up increments.\n\n" +
                                "• Mechanical Advantage: Uninterrupted regular accumulation maximizes compound growth across long market cycles with zero market timing risk.",
                                "Steady compound growth."
                            )
                        }

                        // Strategy 2 details
                        val rank2Title = cleanStrategyTitle(secondBestSipResult?.strategyName)
                        val rank2Desc = when {
                            rank2Title.contains("Smart Tactical", ignoreCase = true) ->
                                "Deploys 2.0x–3.0x multipliers during deep dips to accumulate maximum total rupee wealth, ideal if you maintain liquid cash reserves."
                            rank2Title.contains("Weekly Dip", ignoreCase = true) ->
                                "Captures intra-week market troughs systematically, smoothing out weekly volatility with minimal tracking overhead."
                            rank2Title.contains("Daily", ignoreCase = true) ->
                                "Continuous micro-installments deployed every trading day for flawless mathematical rupee-cost averaging."
                            rank2Title.contains("Blind Weekly", ignoreCase = true) ->
                                "Fixed weekly calendar purchases on a set day of the week, offering 100% automation with zero manual tracking."
                            else ->
                                "Disciplined periodic investment providing reliable rupee-cost averaging across all market conditions."
                        }

                        // Discouraged strategy details
                        val worstTitle = cleanStrategyTitle(worstSipResult?.strategyName)
                        val worstDesc = when {
                            worstTitle.contains("Blind Monthly", ignoreCase = true) ->
                                "Rigid monthly SIPs on fixed salary dates risk purchasing near peak intra-month spikes, consistently trailing dip-timed strategies in cost efficiency."
                            worstTitle.contains("Daily", ignoreCase = true) ->
                                "Higher transaction frequency with negligible alpha margin over disciplined weekly schedules."
                            else ->
                                "Lacks adaptive dip-timing mechanisms, resulting in a higher average cost per unit compared to dip-optimized strategies."
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkTheme) Color(0xFF131B26) else Color(0xFFFFFFFF)
                            ),
                            border = BorderStroke(
                                1.5.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        sapphireColor.copy(alpha = 0.7f),
                                        emeraldColor.copy(alpha = 0.7f)
                                    )
                                )
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // 1. Header with Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = sapphireColor.copy(alpha = 0.18f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("🎯", fontSize = 18.sp)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "STRATEGY RECOMMENDATION",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    letterSpacing = 0.8.sp
                                                ),
                                                color = sapphireColor
                                            )
                                            Text(
                                                text = "Backtested Optimal Playbook",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = onSurfaceColor
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = emeraldColor.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "DATA-BACKED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = emeraldColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                // 2. Fund Character & Volatility Summary Box
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isDarkTheme) Color(0xFF192230) else Color(0xFFF1F5F9),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = selectedSchemeName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp
                                        ),
                                        color = onSurfaceColor
                                    )
                                    Spacer(Modifier.height(6.dp))

                                    // Attribute Pills in clean column
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = sapphireColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, sapphireColor.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = "📂 $fundCategoryTitle",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = sapphireColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = goldColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, goldColor.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = "⚡ $fundVolatilityTag",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = goldColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Section Title
                                Text(
                                    text = "PREFERRED INVESTMENT METHODS FOR THIS SCHEME",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = onSurfaceColor
                                )

                                Spacer(Modifier.height(10.dp))

                                // Tier 1: Top Recommended Strategy Card (Dynamically aligns with #1 Backtest Winner!)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isDarkTheme) Color(0xFF0F261D) else Color(0xFFECFDF5),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.5.dp,
                                            emeraldColor.copy(alpha = 0.8f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(14.dp)
                                ) {
                                    // Row 1: Badges
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = emeraldColor
                                        ) {
                                            Text(
                                                text = "★ #1 PREFERRED STRATEGY",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = emeraldColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "Rank #1 • ${String.format(java.util.Locale.US, "%.2f", bestSipResult?.xirrPct ?: 0.0)}% XIRR",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = emeraldColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Method Title
                                    Text(
                                        text = "$rank1Title 🏆",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp
                                        ),
                                        color = onSurfaceColor
                                    )

                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider(color = emeraldColor.copy(alpha = 0.3f), thickness = 1.dp)
                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = rank1Plan,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp
                                        ),
                                        color = onSurfaceColor
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                // Tier 2: Viable Alternative Card
                                if (secondBestSipResult != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isDarkTheme) Color(0xFF142033) else Color(0xFFEFF6FF),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                1.dp,
                                                sapphireColor.copy(alpha = 0.5f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = sapphireColor.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, sapphireColor.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "VIABLE ALTERNATIVE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = sapphireColor,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = sapphireColor.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, sapphireColor.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "Rank #2 • ${String.format(java.util.Locale.US, "%.2f", secondBestSipResult.xirrPct)}% XIRR",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = sapphireColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))

                                        Text(
                                            text = "$rank2Title 🥈",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = onSurfaceColor
                                        )

                                        Spacer(Modifier.height(4.dp))

                                        Text(
                                            text = rank2Desc,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))
                                }

                                // Tier 3: Discouraged Strategy Card
                                if (worstSipResult != null && worstSipResult != bestSipResult && worstSipResult != secondBestSipResult) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isDarkTheme) Color(0xFF261919) else Color(0xFFFEF2F2),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                1.dp,
                                                redColor.copy(alpha = 0.4f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = redColor.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, redColor.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "⚠️ DISCOURAGED STRATEGY",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = redColor,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = redColor.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, redColor.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "Lowest Rank • ${String.format(java.util.Locale.US, "%.2f", worstSipResult.xirrPct)}% XIRR",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = redColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))

                                        Text(
                                            text = "$worstTitle (Rigid Calendar)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = onSurfaceColor
                                        )

                                        Spacer(Modifier.height(4.dp))

                                        Text(
                                            text = worstDesc,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))
                                }

                                Spacer(Modifier.height(16.dp))

                                // Practical 70/30 Capital Allocation Playbook
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isDarkTheme) Color(0xFF192230) else Color(0xFFF1F5F9),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = sapphireColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "CAPITAL ALLOCATION: THE 70/30 RULE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = sapphireColor
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Clean 2-column or stacked cards with safe weights
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isDarkTheme) Color(0xFF121822) else Color(0xFFFFFFFF),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "70% CORE SIP",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = emeraldColor
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "Disciplined weekly base SIP for steady accumulation across all conditions.",
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isDarkTheme) Color(0xFF121822) else Color(0xFFFFFFFF),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "30% DIP TANK",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = goldColor
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "Parked in liquid funds, deployed as 2.0x–3.0x booster units on ≥ 2.5% dips.",
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Live Evolving Disclaimer Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkTheme) Color(0xFF161B22) else Color(0xFFF1F5F9)
                            ),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = emeraldColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "DAILY NAV ADAPTIVE ENGINE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = emeraldColor
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "This simulation updates live and automatically calibrates dynamically every single day as new official NAV records arrive. The step-up booster thresholds continuously self-improve and adapt automatically, preserving your capital in peaks and multiplying investments inside drawdowns.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFundPredictiveModal && currentFundPredictiveAnalysis != null) {
        val allAnalyses = allFundsSummary.mapNotNull { it.predictiveAnalysis }
        val targetIdx = allAnalyses.indexOfFirst { it.schemeName == selectedSchemeName }.takeIf { it >= 0 } ?: 0
        PredictiveTimingAnalysisModal(
            analysisList = if (allAnalyses.isNotEmpty()) allAnalyses else listOf(currentFundPredictiveAnalysis!!),
            initialIndex = targetIdx,
            isDarkTheme = isDarkTheme,
            onDismiss = { showFundPredictiveModal = false },
            onNavigateToBacktest = { scheme ->
                selectedSchemeName = scheme
                showFundPredictiveModal = false
            }
        )
    }
}
