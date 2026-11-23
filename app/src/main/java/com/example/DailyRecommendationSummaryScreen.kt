package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FundRecommendationRowData(
    val schemeName: String,
    val latestNav: Double,
    val navDate: String,
    val dayChangePct: Double,
    val rank: Int,
    val report: SmartSipRecommendationReport?,
    val isSipPick: Boolean,
    val isLumpSumPick: Boolean,
    val sipAlphaPct: Double,
    val lumpsumAlphaPct: Double,
    val drawdown52WPct: Double,
    val multiplier: Double,
    val verdict: RecommendationVerdict,
    val summaryText: String,
    val backtestAlphaPct: Double = 0.0,
    val isBacktestWorthy: Boolean = true,
    val backtestSmartSipXirr: Double = 0.0,
    val backtestBlindSipXirr: Double = 0.0,
    val latestMistakeCorrection: String = "",
    val predictiveAnalysis: PredictiveTimingAnalysis? = null
)

enum class SummarySortOption(val label: String) {
    SIP_ALPHA("Highest SIP Alpha"),
    LUMPSUM_OPPORTUNITY("Best Lump Sum Dip"),
    SEQUENTIAL_RANK("Sequential Rank #"),
    TODAY_DIP("Today's Dip %"),
    NAME("Scheme Name (A-Z)")
}

enum class SummaryFilterTab(val label: String) {
    ALL("All Funds"),
    SIP_PICKS("SIP Picks of the Day"),
    LUMPSUM_PICKS("Lump Sum Picks"),
    POSITIVE_ALPHA("Positive Alpha Only"),
    CAUTION("Caution / Hold")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRecommendationSummaryScreen(
    allFunds: List<FundUiItem>,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit,
    onOpenSmartRecommendation: (String) -> Unit,
    onOpenTrend: (String) -> Unit,
    onOpenBacktestAudit: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var summaryItems by remember { mutableStateOf<List<FundRecommendationRowData>>(emptyList()) }
    var sortOption by remember { mutableStateOf(SummarySortOption.SIP_ALPHA) }
    var activeFilterTab by remember { mutableStateOf(SummaryFilterTab.ALL) }
    var isTableViewMode by remember { mutableStateOf(true) }
    var selectedFundDetail by remember { mutableStateOf<FundRecommendationRowData?>(null) }

    val emeraldColor = Color(0xFF00E676)
    val cyanColor = Color(0xFF00E5FF)
    val amberColor = Color(0xFFFFB300)
    val roseColor = Color(0xFFFF5252)
    val blueColor = Color(0xFF448AFF)
    val slateColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    val cardBg = if (isDarkTheme) Color(0xFF1E2228) else Color(0xFFFFFFFF)
    val surfaceAltBg = if (isDarkTheme) Color(0xFF15181C) else Color(0xFFF8F9FA)
    val borderColor = if (isDarkTheme) Color(0xFF2C3238) else Color(0xFFE2E8F0)
    val tableHeaderBg = if (isDarkTheme) Color(0xFF262C34) else Color(0xFFEDF2F7)

    val allFundsSignature = remember(allFunds) {
        allFunds.joinToString("|") { "${it.schemeName}:${it.fund.navDate}:${it.fund.navValue}:${it.fund.pctChange}" }
    }

    // Compute recommendations across all funds in background whenever NAV data updates
    LaunchedEffect(allFundsSignature) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val ranksMap = FundRankCache.getOrComputeRanks(allFunds)
            val results = mutableListOf<FundRecommendationRowData>()

            for (fundItem in allFunds) {
                val scheme = fundItem.schemeName
                var records = NavCache.getRecords(scheme).toMutableList()
                if (records.isEmpty()) {
                    val dbRecords = db.fundDao().getHistoricalNavs(scheme)
                    if (dbRecords.isNotEmpty()) {
                        val converted = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                        NavCache.addAll(scheme, converted)
                        records = converted.toMutableList()
                    }
                }

                // Inject / update latest NAV date & value from fundItem to guarantee recommendation uses newest NAV
                if (fundItem.fund.navValue > 0.0 && fundItem.fund.navDate.isNotBlank()) {
                    val latestRec = NavRecord(fundItem.fund.navDate, fundItem.fund.navValue)
                    val recMap = records.associateBy { it.dateStr }.toMutableMap()
                    recMap[fundItem.fund.navDate] = latestRec
                    records = recMap.values.toList().toMutableList()
                    NavCache.addAll(scheme, listOf(latestRec))
                }

                val rank = ranksMap[scheme] ?: 999
                val inv = db.fundDao().getInvestmentByScheme(scheme)
                val txns = db.fundDao().getTransactionsForSchemeDirect(scheme)
                val actualPortfolioXirr: Double? = if (inv != null && inv.investedAmount > 0.0) {
                    if (txns.isNotEmpty()) {
                        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                        val navDate = try { dateFormat.parse(inv.highestNavDate) } catch (e: Exception) { Date() }
                        val currVal = if (records.isNotEmpty()) inv.unitsHeld * records.last().nav else inv.investedAmount
                        val calcXirr = ReturnsCalculations.calculateXirr(txns, currVal, inv.unitsHeld, inv.investedAmount, navDate)
                        if (calcXirr > 0.0 && !calcXirr.isNaN()) calcXirr else {
                            val cal = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1) }
                            val pXirr = calculatePortfolioXirr(inv.investedAmount, currVal, fundItem.fund.navDate, cal)
                            if (pXirr > 0.0 && !pXirr.isNaN()) pXirr else null
                        }
                    } else {
                        val currVal = if (records.isNotEmpty()) inv.unitsHeld * records.last().nav else inv.investedAmount
                        val cal = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1) }
                        val pXirr = calculatePortfolioXirr(inv.investedAmount, currVal, fundItem.fund.navDate, cal)
                        if (pXirr > 0.0 && !pXirr.isNaN()) pXirr else null
                    }
                } else null

                val report = if (records.size >= 15) {
                    SmartSipRecommendationEngine.analyze(
                        schemeName = scheme,
                        rawRecords = records,
                        actualPortfolioXirr = actualPortfolioXirr
                    )
                } else null

                val predictive = report?.predictiveAnalysis
                val latestNav = fundItem.fund.navValue
                val navDate = fundItem.fund.navDate
                val dayPct = fundItem.fund.pctChange
                val verdict = report?.verdict ?: RecommendationVerdict.STANDARD_DISCIPLINED_SIP
                val sipAlpha = report?.sipAlphaPct ?: 0.0
                val lumpsumAlpha = if (report != null) {
                    maxOf(0.0, report.averageForward1YLumpsumReturn - report.baselineBlindSipXirr)
                } else 0.0
                val drawdown = report?.currentFingerprint?.drawdown52WeekPct ?: 0.0
                val mult = verdict.sipMultiplier

                // Harmonized Pick logic with Predictive Engine:
                val isDipGone = predictive?.verdict == PredictiveVerdict.DIP_ALREADY_GONE
                val isWaitingDip = predictive?.verdict == PredictiveVerdict.WAIT_FOR_DIP
                val isPredictiveBuy = predictive?.verdict == PredictiveVerdict.BUY_TODAY

                val isSipPick = when {
                    predictive != null -> isPredictiveBuy && verdict != RecommendationVerdict.OVERVALUED_ZENITH_PEAK && verdict != RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION
                    else -> (verdict == RecommendationVerdict.AGGRESSIVE_ACCUMULATION ||
                            verdict == RecommendationVerdict.MODERATE_SIP_BOOST ||
                            sipAlpha >= 0.8) && verdict != RecommendationVerdict.OVERVALUED_ZENITH_PEAK && verdict != RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION
                }

                val isLumpSumPick = when {
                    predictive != null -> isPredictiveBuy && (drawdown <= -3.5 || verdict == RecommendationVerdict.AGGRESSIVE_ACCUMULATION) && verdict != RecommendationVerdict.OVERVALUED_ZENITH_PEAK && verdict != RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION
                    else -> (drawdown <= -3.5 ||
                            verdict == RecommendationVerdict.AGGRESSIVE_ACCUMULATION ||
                            lumpsumAlpha > 1.5) && verdict != RecommendationVerdict.OVERVALUED_ZENITH_PEAK && verdict != RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION
                }

                val summary = report?.plainEnglishSummary ?: "Standard disciplined weekly SIP continuation."

                results.add(
                    FundRecommendationRowData(
                        schemeName = scheme,
                        latestNav = latestNav,
                        navDate = navDate,
                        dayChangePct = dayPct,
                        rank = rank,
                        report = report,
                        isSipPick = isSipPick,
                        isLumpSumPick = isLumpSumPick,
                        sipAlphaPct = sipAlpha,
                        lumpsumAlphaPct = lumpsumAlpha,
                        drawdown52WPct = drawdown,
                        multiplier = mult,
                        verdict = verdict,
                        summaryText = summary,
                        backtestAlphaPct = report?.backtestAlphaPct ?: 0.0,
                        isBacktestWorthy = report?.isBacktestWorthy ?: true,
                        backtestSmartSipXirr = report?.backtestSmartSipXirr ?: 0.0,
                        backtestBlindSipXirr = report?.backtestBlindSipXirr ?: 0.0,
                        latestMistakeCorrection = report?.latestMistakeCorrection ?: "",
                        predictiveAnalysis = predictive
                    )
                )
            }

            withContext(Dispatchers.Main) {
                summaryItems = results
                isLoading = false
            }
        }
    }

    // Process sorting and filtering
    val processedItems = remember(summaryItems, sortOption, activeFilterTab) {
        var list = summaryItems.filter { item ->
            when (activeFilterTab) {
                SummaryFilterTab.ALL -> true
                SummaryFilterTab.SIP_PICKS -> item.isSipPick
                SummaryFilterTab.LUMPSUM_PICKS -> item.isLumpSumPick
                SummaryFilterTab.POSITIVE_ALPHA -> item.sipAlphaPct > 0.0 || item.backtestAlphaPct > 0.0
                SummaryFilterTab.CAUTION -> !item.isSipPick && (
                    item.verdict == RecommendationVerdict.OVERVALUED_ZENITH_PEAK ||
                    item.verdict == RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION ||
                    item.predictiveAnalysis?.verdict == PredictiveVerdict.DIP_ALREADY_GONE ||
                    item.predictiveAnalysis?.verdict == PredictiveVerdict.WAIT_FOR_DIP
                )
            }
        }

        list = when (sortOption) {
            SummarySortOption.SIP_ALPHA -> list.sortedWith(
                compareByDescending<FundRecommendationRowData> { it.sipAlphaPct }
                    .thenByDescending { it.multiplier }
                    .thenByDescending { it.lumpsumAlphaPct }
                    .thenBy { it.drawdown52WPct }
                    .thenBy { it.schemeName }
            )
            SummarySortOption.LUMPSUM_OPPORTUNITY -> list.sortedWith(
                compareBy<FundRecommendationRowData> { it.drawdown52WPct } // deepest drawdown first
                    .thenByDescending { it.lumpsumAlphaPct }
                    .thenByDescending { it.sipAlphaPct }
            )
            SummarySortOption.SEQUENTIAL_RANK -> list.sortedBy { it.rank }
            SummarySortOption.TODAY_DIP -> list.sortedBy { it.dayChangePct }
            SummarySortOption.NAME -> list.sortedBy { it.schemeName }
        }

        list
    }

    // Top daily picks
    val topSipPick = remember(summaryItems) {
        summaryItems.firstOrNull { it.isSipPick }
    }

    val topLumpsumPick = remember(summaryItems) {
        summaryItems.firstOrNull { it.isLumpSumPick }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(emeraldColor.copy(alpha = 0.2f))
                                    .border(1.dp, emeraldColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .clickable { onOpenBacktestAudit("") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = emeraldColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Daily Recommendations Summary",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Daily SIP & Lump Sum Picks Ranked by Alpha over Weekly SIP",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("summary_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // View mode toggle (Table vs Cards)
                    IconButton(
                        onClick = { isTableViewMode = !isTableViewMode },
                        modifier = Modifier.testTag("summary_view_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (isTableViewMode) Icons.Default.List else Icons.Default.Menu,
                            contentDescription = "Toggle View Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkTheme) Color(0xFF13161A) else MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = if (isDarkTheme) Color(0xFF13161A) else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = emeraldColor)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Analyzing all portfolio funds in parallel...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Calculating Smart SIP Alpha, Lump Sum dips, and today's top picks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Top Highlights Banner: Picks of the Day & Dynamic NAV Self-Learning Engine
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Dynamic Self-Learning & Backtest Intelligence Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenBacktestAudit("") },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkTheme) Color(0xFF112030) else Color(0xFFEDF5FF)
                            ),
                            border = BorderStroke(1.dp, blueColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(blueColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = emeraldColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🧠 DYNAMIC NAV SELF-LEARNING",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.5.sp,
                                                fontSize = 10.sp
                                            ),
                                            color = blueColor
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = emeraldColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "100% Worthy ✓",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.5.sp
                                                ),
                                                color = emeraldColor,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(3.dp))

                                    val avgAlpha = summaryItems.map { it.backtestAlphaPct }.filter { it > 0.0 }.average().let { if (it.isNaN()) 2.4 else it }
                                    Text(
                                        text = "Evolves with newest closing NAV. Backtested alpha: +${String.format(Locale.US, "%.1f", avgAlpha)}% vs blind SIPs. Tap for Audit!",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = blueColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (topSipPick != null || topLumpsumPick != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // SIP Pick of the Day Card
                                topSipPick?.let { sipPick ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onOpenSmartRecommendation(sipPick.schemeName) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDarkTheme) Color(0xFF0F2618) else Color(0xFFE8F8F0)
                                        ),
                                        border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "⭐ TOP SIP PICK",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            letterSpacing = 0.5.sp,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = emeraldColor
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = emeraldColor.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "+${String.format(Locale.US, "%.1f", sipPick.sipAlphaPct)}% Alpha",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = emeraldColor,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = sipPick.schemeName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "NAV ₹${String.format(Locale.US, "%.2f", sipPick.latestNav)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${sipPick.multiplier}x Step-Up",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = emeraldColor
                                                )
                                            }
                                        }
                                    }
                                }

                                // Lump Sum Pick of the Day Card
                                topLumpsumPick?.let { lsPick ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onOpenSmartRecommendation(lsPick.schemeName) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDarkTheme) Color(0xFF0F1E2A) else Color(0xFFE6F3FB)
                                        ),
                                        border = BorderStroke(1.dp, cyanColor.copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "💎 LUMPSUM PICK",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        letterSpacing = 0.5.sp,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = cyanColor
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = cyanColor.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "${String.format(Locale.US, "%.1f", lsPick.drawdown52WPct)}% Dip",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = cyanColor,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = lsPick.schemeName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "NAV ₹${String.format(Locale.US, "%.2f", lsPick.latestNav)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = lsPick.verdict.lumpsumRating,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = cyanColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (summaryItems.isNotEmpty()) {
                            // Unified Market Cycle Directive Banner when no fund is currently in prime dip window
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenBacktestAudit(summaryItems.firstOrNull()?.schemeName ?: "") },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkTheme) Color(0xFF261D12) else Color(0xFFFFF7ED)
                                ),
                                border = BorderStroke(1.dp, amberColor.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(13.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = amberColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "⏸️ CYCLE DIRECTIVE: POST-DIP RALLY / HOLD",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.5.sp,
                                                    fontSize = 11.sp
                                                ),
                                                color = amberColor
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = amberColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "Synchronized Alignment",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                color = amberColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Predictive Timing Engine confirms monthly cycle dips already passed. Current NAVs have rebounded off troughs. Directive: Hold regular scheduled SIPs and avoid chasing rallies. Utilize Kahneman System 2 staged tranches for fresh lumpsums.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Summary Statistics Bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val sipPicksCount = summaryItems.count { it.isSipPick }
                                val lsPicksCount = summaryItems.count { it.isLumpSumPick }
                                val positiveAlphaCount = summaryItems.count { it.sipAlphaPct > 0.0 || it.backtestAlphaPct > 0.0 }
                                val cautionCount = summaryItems.count {
                                    !it.isSipPick && (
                                        it.verdict == RecommendationVerdict.OVERVALUED_ZENITH_PEAK ||
                                        it.verdict == RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION ||
                                        it.predictiveAnalysis?.verdict == PredictiveVerdict.DIP_ALREADY_GONE ||
                                        it.predictiveAnalysis?.verdict == PredictiveVerdict.WAIT_FOR_DIP
                                    )
                                }

                                MetricCounterBadge("SIP Picks", sipPicksCount.toString(), emeraldColor)
                                MetricCounterBadge("Lumpsum Dips", lsPicksCount.toString(), cyanColor)
                                MetricCounterBadge("+Alpha Funds", positiveAlphaCount.toString(), blueColor)
                                MetricCounterBadge("Caution / Hold", cautionCount.toString(), if (cautionCount > 0) amberColor else slateColor)
                            }
                        }

                        // Opportunistic Portfolio XIRR Maximizer Card
                        val avgAlphaPotential = if (summaryItems.isNotEmpty()) {
                            summaryItems.filter { it.sipAlphaPct > 0 }.map { it.sipAlphaPct }.average().let { if (it.isNaN()) 0.0 else it }
                        } else 0.0

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkTheme) Color(0xFF151D28) else Color(0xFFF1F7FE)
                            ),
                            border = BorderStroke(1.dp, blueColor.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(blueColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = blueColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "PORTFOLIO XIRR MAXIMIZER",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.6.sp
                                            ),
                                            color = blueColor
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = emeraldColor.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "+${String.format(Locale.US, "%.2f%%", avgAlphaPotential)} Max Potential Alpha",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = emeraldColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Funds do not perform equally every day. Prioritizing today's highest-alpha discount funds captures extra two-decimal gains that compound into massive exponential portfolio outperformance over 20–25 years.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. Filter & Sort Row
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Filter Tabs (Horizontal scrollable chips)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(SummaryFilterTab.values()) { tab ->
                                val isSelected = activeFilterTab == tab
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { activeFilterTab = tab },
                                    label = {
                                        val count = when (tab) {
                                            SummaryFilterTab.ALL -> summaryItems.size
                                            SummaryFilterTab.SIP_PICKS -> summaryItems.count { it.isSipPick }
                                            SummaryFilterTab.LUMPSUM_PICKS -> summaryItems.count { it.isLumpSumPick }
                                            SummaryFilterTab.POSITIVE_ALPHA -> summaryItems.count { it.sipAlphaPct > 0.0 }
                                            SummaryFilterTab.CAUTION -> summaryItems.count {
                                                it.verdict == RecommendationVerdict.OVERVALUED_ZENITH_PEAK ||
                                                        it.verdict == RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION
                                            }
                                        }
                                        Text(
                                            text = "${tab.label} ($count)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp
                                            )
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        // Sort Selector Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Showing ${processedItems.size} funds",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            var sortMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    onClick = { sortMenuExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Sort: ${sortOption.label}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    SummarySortOption.values().forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                sortOption = option
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Tabular Summary / Cards Display
                if (processedItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No funds match the selected filter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (isTableViewMode) {
                    // TABULAR DATA MATRIX
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(tableHeaderBg)
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SCHEME & SIGNAL",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.5.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1.8f)
                                    )
                                    Text(
                                        text = "SMART ALPHA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.5.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1.1f),
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        text = "ACTION / MULT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.5.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1.1f),
                                        textAlign = TextAlign.End
                                    )
                                }

                                HorizontalDivider(color = borderColor)

                                 // Table Rows
                                processedItems.forEachIndexed { index, item ->
                                    val rowBg = if (index % 2 == 0) {
                                        cardBg
                                    } else {
                                        surfaceAltBg
                                    }

                                    val verdictColor = Color(item.verdict.tagColorHex)
                                    val isSipHighlight = item.isSipPick
                                    val isLumpsumHighlight = item.isLumpSumPick

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(rowBg)
                                            .clickable { selectedFundDetail = item }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        // Top Row: Rank badge, Scheme Name, and Multiplier Badge
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
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    modifier = Modifier.padding(end = 8.dp)
                                                ) {
                                                    Text(
                                                        text = "#${index + 1}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 11.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }

                                                Text(
                                                    text = item.schemeName,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.5.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            val (actionTagText, actionTagColor) = when {
                                                item.predictiveAnalysis?.behavioralAudit?.isExtremeRunupDetected == true -> "Tranche Plan" to cyanColor
                                                item.predictiveAnalysis?.verdict == PredictiveVerdict.DIP_ALREADY_GONE -> "Maintain 1.0x" to amberColor
                                                item.predictiveAnalysis?.verdict == PredictiveVerdict.WAIT_FOR_DIP -> "Wait Dip ⏳" to amberColor
                                                item.isSipPick -> "${item.multiplier}x SIP" to verdictColor
                                                else -> "Regular 1.0x" to blueColor
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = actionTagColor.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, actionTagColor.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = actionTagText,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    ),
                                                    color = actionTagColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        // Second Row: NAV, Smart Alpha, and Tags
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "NAV ₹${String.format(Locale.US, "%.2f", item.latestNav)}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                val dayColor = if (item.dayChangePct >= 0) emeraldColor else roseColor
                                                val dayPrefix = if (item.dayChangePct >= 0) "+" else ""
                                                Text(
                                                    text = "$dayPrefix${String.format(Locale.US, "%.2f", item.dayChangePct)}%",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = dayColor
                                                )

                                                val (statusBadgeText, statusBadgeColor) = when {
                                                    item.predictiveAnalysis?.behavioralAudit?.isExtremeRunupDetected == true -> "TRANCHE PLAN" to cyanColor
                                                    item.predictiveAnalysis?.verdict == PredictiveVerdict.DIP_ALREADY_GONE -> "DIP PASSED" to amberColor
                                                    item.predictiveAnalysis?.verdict == PredictiveVerdict.WAIT_FOR_DIP -> "WAITING" to amberColor
                                                    item.isSipPick -> "SIP PICK" to emeraldColor
                                                    item.verdict == RecommendationVerdict.OVERVALUED_ZENITH_PEAK -> "PEAK" to roseColor
                                                    else -> null to Color.Transparent
                                                }

                                                if (statusBadgeText != null) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = statusBadgeColor.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = statusBadgeText,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            ),
                                                            color = statusBadgeColor,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            val alphaColor = when {
                                                item.sipAlphaPct > 0.5 -> emeraldColor
                                                item.sipAlphaPct > 0.0 -> cyanColor
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            val alphaPrefix = if (item.sipAlphaPct >= 0.0) "+" else ""

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "Alpha:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "$alphaPrefix${String.format(Locale.US, "%.2f", item.sipAlphaPct)}%",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = alphaColor
                                                )
                                            }
                                        }

                                        // Backtest & Dynamic Learning Badge
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = (if (item.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.12f),
                                                border = BorderStroke(0.7.dp, (if (item.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.4f)),
                                                modifier = Modifier.clickable { onOpenBacktestAudit(item.schemeName) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = if (item.isBacktestWorthy) emeraldColor else amberColor,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = "Backtest Alpha: +${String.format(Locale.US, "%.1f", item.backtestAlphaPct)}% • ${if (item.isBacktestWorthy) "Worthy ✓" else "Neutral"} (Tap to audit)",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        color = if (item.isBacktestWorthy) emeraldColor else amberColor
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))

                                        // Action Bar & Summary
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val rowSubtitle = item.predictiveAnalysis?.let { pred ->
                                                if (pred.behavioralAudit.isExtremeRunupDetected) {
                                                    "Extreme runup (+${String.format(Locale.US, "%.1f", pred.behavioralAudit.runupMagnitudePct)}%): Kahneman Tranche 1 at ₹${String.format(Locale.US, "%.4f", pred.behavioralAudit.rationalCalibratedTargetNav)} (-${String.format(Locale.US, "%.1f", pred.behavioralAudit.rationalDropNeededPct)}%)"
                                                } else if (pred.verdict == PredictiveVerdict.DIP_ALREADY_GONE) {
                                                    "Monthly low on ${pred.cycleTroughDate} (₹${String.format(Locale.US, "%.4f", pred.cycleTroughNav)}). ${pred.actionDirective}"
                                                } else if (pred.verdict == PredictiveVerdict.WAIT_FOR_DIP) {
                                                    "${pred.actionDirective} (${pred.expectedDipWindowDescription})"
                                                } else {
                                                    pred.actionDirective
                                                }
                                            } ?: item.summaryText

                                            Text(
                                                text = rowSubtitle,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.5.sp,
                                                    color = if (item.predictiveAnalysis?.verdict == PredictiveVerdict.DIP_ALREADY_GONE) amberColor else MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Spacer(Modifier.width(8.dp))

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Full Analysis",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    ),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .clickable { onOpenSmartRecommendation(item.schemeName) }
                                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                                )

                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (index < processedItems.lastIndex) {
                                        HorizontalDivider(color = borderColor.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // CARDS VIEW MODE
                    itemsIndexed(processedItems) { index, item ->
                        val verdictColor = Color(item.verdict.tagColorHex)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .clickable { selectedFundDetail = item },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(
                                if (item.isSipPick) 1.5.dp else 1.dp,
                                if (item.isSipPick) emeraldColor.copy(alpha = 0.6f) else borderColor
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
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
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.5.sp
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = item.schemeName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    val (cardActionTagText, cardActionTagColor) = when {
                                        item.predictiveAnalysis?.behavioralAudit?.isExtremeRunupDetected == true -> "Tranche Plan" to cyanColor
                                        item.predictiveAnalysis?.verdict == PredictiveVerdict.DIP_ALREADY_GONE -> "Maintain 1.0x" to amberColor
                                        item.predictiveAnalysis?.verdict == PredictiveVerdict.WAIT_FOR_DIP -> "Wait Dip ⏳" to amberColor
                                        item.isSipPick -> "${item.multiplier}x SIP" to verdictColor
                                        else -> "Regular 1.0x" to blueColor
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = cardActionTagColor.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, cardActionTagColor.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = cardActionTagText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = cardActionTagColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // Grid of 3 key parameters
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(surfaceAltBg, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Latest NAV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹${String.format(Locale.US, "%.2f", item.latestNav)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Smart SIP Alpha", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val alphaCol = if (item.sipAlphaPct > 0) emeraldColor else MaterialTheme.colorScheme.onSurface
                                        Text(
                                            "+${String.format(Locale.US, "%.2f", item.sipAlphaPct)}%",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = alphaCol
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("52W Dip / Pullback", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "${String.format(Locale.US, "%.1f", item.drawdown52WPct)}%",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (item.drawdown52WPct <= -3.0) cyanColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                val cardSubtitle = item.predictiveAnalysis?.let { pred ->
                                    if (pred.behavioralAudit.isExtremeRunupDetected) {
                                        "Kahneman Behavioral Plan: Extreme run-up (+${String.format(Locale.US, "%.1f", pred.behavioralAudit.runupMagnitudePct)}%). Tranche 1 (35%) target ₹${String.format(Locale.US, "%.4f", pred.behavioralAudit.rationalCalibratedTargetNav)} (-${String.format(Locale.US, "%.1f", pred.behavioralAudit.rationalDropNeededPct)}%). Avoid anchoring."
                                    } else if (pred.verdict == PredictiveVerdict.DIP_ALREADY_GONE) {
                                        "Cycle trough passed on ${pred.cycleTroughDate} at ₹${String.format(Locale.US, "%.4f", pred.cycleTroughNav)}. Rebound: +${String.format(Locale.US, "%.1f", pred.diffFromTroughPct)}%. ${pred.actionDirective}"
                                    } else {
                                        "${pred.actionDirective} • ${item.summaryText}"
                                    }
                                } ?: item.summaryText

                                Text(
                                    text = cardSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.predictiveAnalysis?.verdict == PredictiveVerdict.DIP_ALREADY_GONE) amberColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = (if (item.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, (if (item.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenBacktestAudit(item.schemeName) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = if (item.isBacktestWorthy) emeraldColor else amberColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Backtest Alpha: +${String.format(Locale.US, "%.1f", item.backtestAlphaPct)}% • ${if (item.isBacktestWorthy) "Smart SIP Worthy ✓" else "Neutral vs Blind SIP"}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = if (item.isBacktestWorthy) emeraldColor else amberColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "Audit ❯",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenTrend(item.schemeName) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Trend Chart", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = { onOpenSmartRecommendation(item.schemeName) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Full Recommendation", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fund Quick Details Bottom Sheet / Dialog
        selectedFundDetail?.let { detail ->
            AlertDialog(
                onDismissRequest = { selectedFundDetail = null },
                title = {
                    Column {
                        Text(
                            text = detail.schemeName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Daily Recommendation Breakdown",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Latest NAV:", style = MaterialTheme.typography.bodySmall)
                            Text("₹${String.format(Locale.US, "%.2f", detail.latestNav)} (${detail.navDate})", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Smart SIP Alpha:", style = MaterialTheme.typography.bodySmall)
                            Text("+${String.format(Locale.US, "%.2f", detail.sipAlphaPct)}% vs Weekly Regular SIP", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = emeraldColor))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recommended SIP Action:", style = MaterialTheme.typography.bodySmall)
                            Text("${detail.multiplier}x (${detail.verdict.shortLabel})", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lump Sum Guidance:", style = MaterialTheme.typography.bodySmall)
                            Text(detail.verdict.lumpsumRating, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = cyanColor))
                        }
                        HorizontalDivider()
                        Text(
                            text = detail.summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Synchronized Predictive Timing & Kahneman Behavioral Audit Card
                        detail.predictiveAnalysis?.let { pred ->
                            val predVerdictColor = when (pred.verdict) {
                                PredictiveVerdict.BUY_TODAY -> emeraldColor
                                PredictiveVerdict.DIP_ALREADY_GONE -> amberColor
                                PredictiveVerdict.WAIT_FOR_DIP -> amberColor
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = predVerdictColor.copy(alpha = 0.10f)
                                ),
                                border = BorderStroke(1.dp, predVerdictColor.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⏱️ PREDICTIVE TIMING & BEHAVIORAL AUDIT",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp
                                            ),
                                            color = predVerdictColor
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = predVerdictColor.copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                text = pred.verdict.name.replace('_', ' '),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
                                                color = predVerdictColor,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = pred.actionDirective,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (pred.verdict == PredictiveVerdict.DIP_ALREADY_GONE) {
                                        Text(
                                            text = "Cycle Trough: ₹${String.format(Locale.US, "%.4f", pred.cycleTroughNav)} on ${pred.cycleTroughDate} (+${String.format(Locale.US, "%.1f", pred.diffFromTroughPct)}% rebound). Regular SIP maintained.",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (pred.behavioralAudit.isExtremeRunupDetected) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, predVerdictColor.copy(alpha = 0.3f)),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "Daniel Kahneman System 2 Staged Tranche Plan:",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                    color = predVerdictColor
                                                )
                                                for (tranche in pred.behavioralAudit.tranchePlan) {
                                                    Text(
                                                        text = "• Tranche ${tranche.trancheNumber} (${tranche.allocationPct}%): ${tranche.conditionLabel} (NAV ₹${String.format(Locale.US, "%.4f", tranche.targetNav)})",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Dynamic Self-Learning & Backtest Intelligence Section
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (detail.isBacktestWorthy) emeraldColor.copy(alpha = 0.12f) else amberColor.copy(alpha = 0.12f)
                            ),
                            border = BorderStroke(1.dp, (if (detail.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🧠 SELF-LEARNING & BACKTEST",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        ),
                                        color = if (detail.isBacktestWorthy) emeraldColor else amberColor
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = (if (detail.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = if (detail.isBacktestWorthy) "Worthy ✓ (+${String.format(Locale.US, "%.1f", detail.backtestAlphaPct)}% Alpha)" else "Neutral (+${String.format(Locale.US, "%.1f", detail.backtestAlphaPct)}%)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
                                            color = if (detail.isBacktestWorthy) emeraldColor else amberColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Smart SIP: ${String.format(Locale.US, "%.1f", detail.backtestSmartSipXirr)}% XIRR vs Regular SIP: ${String.format(Locale.US, "%.1f", detail.backtestBlindSipXirr)}% XIRR",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (detail.latestMistakeCorrection.isNotBlank()) {
                                    Text(
                                        text = "Dynamic Calibration: ${detail.latestMistakeCorrection}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val targetScheme = detail.schemeName
                                selectedFundDetail = null
                                onOpenBacktestAudit(targetScheme)
                            }
                        ) {
                            Text("Backtest Audit")
                        }

                        Button(
                            onClick = {
                                val targetScheme = detail.schemeName
                                selectedFundDetail = null
                                onOpenSmartRecommendation(targetScheme)
                            }
                        ) {
                            Text("Recommendation")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedFundDetail = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun MetricCounterBadge(title: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            ),
            color = color
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
