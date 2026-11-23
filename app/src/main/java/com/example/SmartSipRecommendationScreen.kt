package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSipRecommendationScreen(
    baseSchemeName: String,
    allSchemes: List<String> = emptyList(),
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit,
    onNavigateToScheme: (String) -> Unit = {},
    onOpenDailySummary: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedScheme by remember(baseSchemeName) { mutableStateOf(baseSchemeName) }
    var report by remember { mutableStateOf<SmartSipRecommendationReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedHorizonTab by remember { mutableIntStateOf(0) } // 0: 1 Year, 1: 3 Years

    val emeraldColor = Color(0xFF00E676)
    val cyanColor = Color(0xFF00E5FF)
    val amberColor = Color(0xFFFFB300)
    val roseColor = Color(0xFFFF5252)

    val cardBg = if (isDarkTheme) Color(0xFF1E2228) else Color(0xFFFFFFFF)
    val surfaceAltBg = if (isDarkTheme) Color(0xFF15181C) else Color(0xFFF8F9FA)
    val borderColor = if (isDarkTheme) Color(0xFF2C3238) else Color(0xFFE2E8F0)

    // Load records & run Smart Recommendation analysis in background
    LaunchedEffect(selectedScheme) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            var records = NavCache.getRecords(selectedScheme).toMutableList()
            if (records.isEmpty()) {
                val dbRecords = db.fundDao().getHistoricalNavs(selectedScheme)
                if (dbRecords.isNotEmpty()) {
                    val converted = dbRecords.map { NavRecord(it.dateStr, it.navValue) }
                    NavCache.addAll(selectedScheme, converted)
                    records = converted.toMutableList()
                }
            }

            val funds = db.fundDao().getFundsDirect()
            val fundData = funds.find { it.schemeName == selectedScheme }
            if (fundData != null && fundData.navValue > 0.0 && fundData.navDate.isNotBlank()) {
                val latestRec = NavRecord(fundData.navDate, fundData.navValue)
                val recMap = records.associateBy { it.dateStr }.toMutableMap()
                recMap[fundData.navDate] = latestRec
                records = recMap.values.toList().toMutableList()
                NavCache.addAll(selectedScheme, listOf(latestRec))
            }

            // Look up actual portfolio XIRR for this scheme if held by user
            val inv = db.fundDao().getInvestmentByScheme(selectedScheme)
            val txns = db.fundDao().getTransactionsForSchemeDirect(selectedScheme)
            val actualPortfolioXirr: Double? = if (inv != null && inv.investedAmount > 0.0) {
                if (txns.isNotEmpty()) {
                    val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                    val navDate = try { dateFormat.parse(inv.highestNavDate) } catch (e: Exception) { Date() }
                    val currVal = if (records.isNotEmpty()) inv.unitsHeld * records.last().nav else inv.investedAmount
                    val calcXirr = ReturnsCalculations.calculateXirr(txns, currVal, inv.unitsHeld, inv.investedAmount, navDate)
                    if (calcXirr > 0.0 && !calcXirr.isNaN()) calcXirr else {
                        val cal = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1) }
                        val pXirr = calculatePortfolioXirr(inv.investedAmount, currVal, records.lastOrNull()?.dateStr ?: "", cal)
                        if (pXirr > 0.0 && !pXirr.isNaN()) pXirr else null
                    }
                } else {
                    val currVal = if (records.isNotEmpty()) inv.unitsHeld * records.last().nav else inv.investedAmount
                    val cal = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1) }
                    val pXirr = calculatePortfolioXirr(inv.investedAmount, currVal, records.lastOrNull()?.dateStr ?: "", cal)
                    if (pXirr > 0.0 && !pXirr.isNaN()) pXirr else null
                }
            } else null

            val computedReport = SmartSipRecommendationEngine.analyze(
                schemeName = selectedScheme,
                rawRecords = records,
                actualPortfolioXirr = actualPortfolioXirr
            )
            withContext(Dispatchers.Main) {
                report = computedReport
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "R",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Color(0xFF00E676)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Smart Alpha Recommendation",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenDailySummary,
                        modifier = Modifier.testTag("smart_rec_daily_summary_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "All Funds Summary",
                            tint = emeraldColor
                        )
                    }
                    if (allSchemes.size > 1) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Switch Fund")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                allSchemes.forEach { scheme ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                scheme,
                                                fontWeight = if (scheme == selectedScheme) FontWeight.Bold else FontWeight.Normal,
                                                color = if (scheme == selectedScheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedScheme = scheme
                                            expanded = false
                                            onNavigateToScheme(scheme)
                                        }
                                    )
                                }
                            }
                        }
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
                        text = "Analyzing historical alpha patterns...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Scanning full NAV history for smart SIP & lumpsum alpha opportunities",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else if (report == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = amberColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Insufficient Historical Data",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "A minimum of 60 trading days of historical NAV records is required to evaluate alpha potential.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val r = report!!
            val isOvervalued = r.verdict == RecommendationVerdict.OVERVALUED_ZENITH_PEAK
            val isElevated = r.verdict == RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION
            val hasExtraAlpha = (r.verdict == RecommendationVerdict.MODERATE_SIP_BOOST || r.verdict == RecommendationVerdict.AGGRESSIVE_ACCUMULATION) && r.sipAlphaPct >= 1.0

            val alphaHighlightColor = when {
                isOvervalued -> roseColor
                isElevated -> amberColor
                hasExtraAlpha -> emeraldColor
                else -> cyanColor
            }
            val verdictColor = Color(r.verdict.tagColorHex)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Professional Fund Header Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "SELECTED MUTUAL FUND",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    onClick = onOpenDailySummary,
                                    shape = RoundedCornerShape(6.dp),
                                    color = emeraldColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.4f)),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = "All Funds Summary",
                                            tint = emeraldColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "All Funds Summary",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = emeraldColor
                                        )
                                    }
                                }
                            }
                            Text(
                                text = r.schemeName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = borderColor.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "CURRENT NAV",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = String.format(Locale.US, "₹%.2f", r.currentNav),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "LAST NAV DATE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = r.analysisDateStr,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "NAV HISTORY",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${r.totalHistoricalTradingDaysAnalyzed} Days",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Core Alpha Verdict Banner
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            verdictColor.copy(alpha = 0.12f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                // Alpha Status Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(verdictColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    isOvervalued -> Icons.Default.Warning
                                                    hasExtraAlpha -> Icons.Default.Star
                                                    else -> Icons.Default.Check
                                                },
                                                contentDescription = null,
                                                tint = verdictColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "VALUATION & ALPHA SIGNAL",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    letterSpacing = 0.6.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                ),
                                                color = verdictColor
                                            )
                                            Text(
                                                text = when {
                                                    isOvervalued -> "Overextended Peak Valuation"
                                                    isElevated -> "Elevated / Steady Momentum"
                                                    hasExtraAlpha -> "Extra Alpha Opportunity"
                                                    else -> "Normal Compounding Zone"
                                                },
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    // Alpha Percentage Badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = alphaHighlightColor.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, alphaHighlightColor.copy(alpha = 0.5f))
                                    ) {
                                        val badgeText = when {
                                            isOvervalued -> "0.0% Extra Alpha"
                                            isElevated -> "0.0% Alpha"
                                            hasExtraAlpha -> "+${String.format(Locale.US, "%.1f%%", r.sipAlphaPct)} Alpha"
                                            else -> "Standard Base"
                                        }
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = alphaHighlightColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = borderColor.copy(alpha = 0.6f))
                                Spacer(Modifier.height(14.dp))

                                // Action Recommendations (Smart SIP & Lumpsum)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Smart SIP Action Box
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = surfaceAltBg,
                                        border = BorderStroke(1.dp, borderColor)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "SIP RECOMMENDATION",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = when (r.verdict) {
                                                    RecommendationVerdict.OVERVALUED_ZENITH_PEAK -> "Maintain Regular SIP (1.0x)"
                                                    RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION -> "Continue Regular SIP (1.0x)"
                                                    RecommendationVerdict.STANDARD_DISCIPLINED_SIP -> "Continue Regular SIP (1.0x)"
                                                    RecommendationVerdict.MODERATE_SIP_BOOST -> "Step-Up SIP (+25%)"
                                                    RecommendationVerdict.AGGRESSIVE_ACCUMULATION -> "Step-Up SIP (+50%)"
                                                },
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (hasExtraAlpha) emeraldColor else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Lumpsum Action Box
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = surfaceAltBg,
                                        border = BorderStroke(1.dp, borderColor)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "LUMPSUM GUIDANCE",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = when (r.verdict) {
                                                    RecommendationVerdict.OVERVALUED_ZENITH_PEAK -> "Avoid Lump-Sum / Use STP"
                                                    RecommendationVerdict.ELEVATED_MOMENTUM_CONSOLIDATION -> "Stagger over 3–6 Months"
                                                    RecommendationVerdict.STANDARD_DISCIPLINED_SIP -> "Stagger in 2–3 Tranches"
                                                    RecommendationVerdict.MODERATE_SIP_BOOST -> "Split in 2 Tranches"
                                                    RecommendationVerdict.AGGRESSIVE_ACCUMULATION -> "Favorable (Deploy Now)"
                                                },
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isOvervalued) roseColor else amberColor
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                // Plain-English Takeaway Explanation directly from engine
                                Text(
                                    text = r.plainEnglishSummary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 3. Quick Key Metrics Strip (3 Simple Numbers)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Expected 1Y Return
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Expected 1Y Return",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f%%", r.averageForward1YSipXirr)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = emeraldColor
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Annualized XIRR",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Win Rate vs Regular SIP
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Historical Win Rate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.0f%%", r.historicalSuccessRatePct)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = cyanColor
                                )
                                Spacer(Modifier.height(2.dp))
                                 Text(
                                    text = "Beat Weekly SIP",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Max Historical Dip
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Max Pullback Risk",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f%%", r.maxHistoricalPainPct)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = roseColor
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Worst Historical Dip",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 4. Simplified Strategy Backtest Comparison
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Strategy Comparison (vs Weekly SIP)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Horizon Tab Selector
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = selectedHorizonTab == 0,
                                    onClick = { selectedHorizonTab = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text("1-Year", fontSize = 11.sp)
                                }
                                SegmentedButton(
                                    selected = selectedHorizonTab == 1,
                                    onClick = { selectedHorizonTab = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text("3-Year", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Historical performance comparison against baseline regular weekly SIP:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 5. 20-25 Year Long-Term Compounding & Smart Alpha Impact (The 2-Decimal Power)
                item {
                    val compounding = r.longTermCompounding
                    var selectedMonthlyAmount by remember { mutableDoubleStateOf(10000.0) }
                    var selectedYearsTab by remember { mutableIntStateOf(3) } // 0: 10Y, 1: 15Y, 2: 20Y, 3: 25Y
                    val yearsList = listOf(10, 15, 20, 25)
                    val activeYears = yearsList[selectedYearsTab]

                    // Dynamic calculation for selected monthly amount
                    val baseRate = compounding.regularSipXirrPct
                    val smartRate = compounding.smartAlphaSipXirrPct
                    val r1 = (1.0 + baseRate / 100.0).pow(1.0 / 12.0) - 1.0
                    val r2 = (1.0 + smartRate / 100.0).pow(1.0 / 12.0) - 1.0
                    val nMonths = activeYears * 12

                    val totalInv = selectedMonthlyAmount * nMonths.toDouble()
                    val regCorpus = selectedMonthlyAmount * (( (1.0 + r1).pow(nMonths.toDouble()) - 1.0 ) / r1) * (1.0 + r1)
                    val smartCorpus = selectedMonthlyAmount * (( (1.0 + r2).pow(nMonths.toDouble()) - 1.0 ) / r2) * (1.0 + r2)
                    val extraWealth = smartCorpus - regCorpus
                    val outperfPct = if (regCorpus > 0.0) (extraWealth / regCorpus) * 100.0 else 0.0

                    fun formatCroresLakhs(amount: Double): String {
                        return if (amount >= 10000000.0) {
                            String.format(Locale.US, "₹%.2f Cr", amount / 10000000.0)
                        } else if (amount >= 100000.0) {
                            String.format(Locale.US, "₹%.2f L", amount / 100000.0)
                        } else {
                            String.format(Locale.US, "₹%.0f", amount)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF141920) else Color(0xFFF4FBF7)
                        ),
                        border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(emeraldColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            tint = emeraldColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "20–25 YEAR COMPOUNDING POWER",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = emeraldColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "+${String.format(Locale.US, "%.2f%%", compounding.alphaDiffPct)} Alpha",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = emeraldColor,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Monthly SIP Amount Quick Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MONTHLY BASE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "HORIZON",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(5000.0 to "₹5K", 10000.0 to "₹10K", 25000.0 to "₹25K", 50000.0 to "₹50K").forEach { (amt, label) ->
                                    val isSelected = selectedMonthlyAmount == amt
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) emeraldColor else surfaceAltBg,
                                        border = BorderStroke(1.dp, if (isSelected) emeraldColor else borderColor),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { selectedMonthlyAmount = amt }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Time Horizon Selector (10Y, 15Y, 20Y, 25Y)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                yearsList.forEachIndexed { idx, yr ->
                                    SegmentedButton(
                                        selected = selectedYearsTab == idx,
                                        onClick = { selectedYearsTab = idx },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = yearsList.size)
                                    ) {
                                        Text(
                                            text = "${yr}Y",
                                            fontWeight = if (selectedYearsTab == idx) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Side by side Comparison Result Cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Regular Blind SIP
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = surfaceAltBg,
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Regular SIP",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "@ ${String.format(Locale.US, "%.1f%%", baseRate)} XIRR",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = formatCroresLakhs(regCorpus),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Total Inv: ${formatCroresLakhs(totalInv)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Smart Alpha SIP
                                Surface(
                                    modifier = Modifier.weight(1.1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = emeraldColor.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Smart Alpha SIP",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = emeraldColor
                                            )
                                            Text(
                                                text = "+${String.format(Locale.US, "%.0f%%", outperfPct)}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.sp
                                                ),
                                                color = emeraldColor
                                            )
                                        }
                                        Text(
                                            text = "@ ${String.format(Locale.US, "%.1f%%", smartRate)} XIRR",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = emeraldColor.copy(alpha = 0.9f)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = formatCroresLakhs(smartCorpus),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = emeraldColor
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Extra Gain: +${formatCroresLakhs(extraWealth)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = emeraldColor
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = emeraldColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "In ${activeYears} Years: Extra +${formatCroresLakhs(extraWealth)} (+${String.format(Locale.US, "%.1f%%", outperfPct)} growth)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "Regular SIP Rate (${String.format(Locale.US, "%.1f%%", baseRate)}) = Min of Expected (${String.format(Locale.US, "%.1f%%", r.expectedXirr)}), Trailing (${String.format(Locale.US, "%.1f%%", r.annualisedTrailingXirr)})${if (r.actualPortfolioXirr != null) ", Portfolio (${String.format(Locale.US, "%.1f%%", r.actualPortfolioXirr)})" else ""}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Adaptive Self-Learning & Mistake-Correction Engine
                item {
                    val learn = r.selfLearningProfile
                    var isExpanded by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cyanColor.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(cyanColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = cyanColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "ADAPTIVE SELF-LEARNING ENGINE",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = cyanColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, cyanColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${learn.historicalAccuracyRatePct}% Accuracy",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = cyanColor,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Key Learning Metrics Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = surfaceAltBg,
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Evaluated", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "${learn.totalHistoricalSignalsEvaluated}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = surfaceAltBg,
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Alpha Hits", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "${learn.alphaHitsCount}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = emeraldColor
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = surfaceAltBg,
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Calibrated", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "${learn.mistakesCalibratedCount}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = amberColor
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Calibrated threshold description
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = surfaceAltBg,
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = learn.calibratedThresholdsDescription,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Historic Backtest Strategy Audit & Dynamic Recalibration Banner
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (if (r.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, (if (r.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "HISTORIC STRATEGY AUDIT (ALL NAV DAYS)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp
                                            ),
                                            color = if (r.isBacktestWorthy) emeraldColor else amberColor
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = (if (r.isBacktestWorthy) emeraldColor else amberColor).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (r.isBacktestWorthy) "Smart SIP Worthy ✓" else "Neutral vs Blind SIP",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
                                                color = if (r.isBacktestWorthy) emeraldColor else amberColor,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Smart SIP Alpha: +${String.format(Locale.US, "%.2f", r.backtestAlphaPct)}% (${String.format(Locale.US, "%.1f", r.backtestSmartSipXirr)}% XIRR vs ${String.format(Locale.US, "%.1f", r.backtestBlindSipXirr)}% Regular SIP). Drawdown reduced by ${String.format(Locale.US, "%.1f", r.backtestMaxDrawdownReductionPct)}%.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (r.latestMistakeCorrection.isNotBlank()) {
                                        Text(
                                            text = "Latest Dynamic Recalibration: ${r.latestMistakeCorrection}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Toggle to view Mistake Corrections
                            TextButton(
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = if (isExpanded) "Hide Learned Adjustments ▲" else "View Learned Mistake Adjustments (${learn.recentCorrections.size}) ▼",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cyanColor
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    learn.recentCorrections.forEach { correction ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = surfaceAltBg,
                                            border = BorderStroke(1.dp, borderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = correction.periodName,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = cyanColor
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "Mistake Identified: ${correction.identifiedMistake}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = roseColor
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = "Learned Calibration: ${correction.learnedAdjustment}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = emeraldColor
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = "Result: ${correction.accuracyImprovement}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
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
                }

                // 7. Historical Scenario Replay & Outcome Matrix (Has this happened before?)
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Historical Scenario Replay (${r.scenarioImpactRecords.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Past NAV Analogues",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Backtested realization of past identical market scenarios showing exact positive/negative impact:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(r.scenarioImpactRecords.take(8)) { record ->
                    val isPos = record.isPositiveAlpha
                    val tagBg = if (isPos) emeraldColor.copy(alpha = 0.15f) else roseColor.copy(alpha = 0.15f)
                    val tagColor = if (isPos) emeraldColor else roseColor

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isPos) Icons.Default.CheckCircle else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = tagColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = record.dateStr,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "(NAV: ₹${String.format(Locale.US, "%.2f", record.navAtEntry)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = tagBg,
                                    border = BorderStroke(1.dp, tagColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = if (record.alphaOverBaselinePct >= 0) "+${String.format(Locale.US, "%.1f%%", record.alphaOverBaselinePct)} Alpha" else "${String.format(Locale.US, "%.1f%%", record.alphaOverBaselinePct)} Alpha",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = tagColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = record.impactSummary,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1Y XIRR: ${String.format(Locale.US, "%.1f%%", record.realized1YXirr)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (record.realized1YXirr >= 12.0) emeraldColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Recovery: ${record.recoveryTradingDays} Days",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Interim Dip: ${String.format(Locale.US, "%.1f%%", record.maxPullbackAfterEntryPct)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = roseColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
