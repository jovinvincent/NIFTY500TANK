package com.example

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Compounding power band categorization for mutual fund unit holdings.
 */
enum class UnitPowerBand(
    val title: String,
    val subtitle: String,
    val color: Color
) {
    SUPER_POWER(
        title = "Above Target",
        subtitle = "Alpha > +3% vs Target",
        color = Color(0xFF00E676)
    ),
    ON_TRACK(
        title = "On Target",
        subtitle = "Alpha -2% to +3% vs Target",
        color = Color(0xFF29B6F6)
    ),
    MODERATE_CATCHUP(
        title = "Near Target",
        subtitle = "Alpha -5% to -2% vs Target",
        color = Color(0xFFFFB300)
    ),
    UNDERPERFORMING_DISCOUNT(
        title = "Below Target",
        subtitle = "Alpha < -5% vs Target",
        color = Color(0xFFFF5252)
    )
}

/**
 * Action verdict indicating whether to accumulate additional units now or wait.
 */
enum class UnitsActionRecommendation(
    val badgeText: String,
    val headline: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    ADD_UNITS_NOW(
        badgeText = "ACCUMULATE UNITS NOW",
        headline = "Prime Unit Expansion Window",
        color = Color(0xFF00E676),
        icon = Icons.Default.AddCircle
    ),
    STEADY_SIP(
        badgeText = "STEADY SYSTEMATIC ACCUMULATION",
        headline = "Compounding on Track — Continue SIP",
        color = Color(0xFF29B6F6),
        icon = Icons.Default.CheckCircle
    ),
    WAIT_FOR_DIPS(
        badgeText = "HOLD PACE — WAIT FOR DIPS",
        headline = "Peak Velocity — Defer Aggressive Lumpsums",
        color = Color(0xFFFFB300),
        icon = Icons.Default.HourglassBottom
    )
}

/**
 * Evaluated lot of units with compounding speed and future value projection.
 */
data class UnitLotPowerItem(
    val units: Double,
    val costPerUnit: Double,
    val purchaseDate: Date,
    val daysHeld: Double,
    val yearsHeld: Double,
    val currentNav: Double,
    val lotCurrentValue: Double,
    val lotCost: Double,
    val absoluteReturnPct: Double,
    val annualizedCagrPct: Double,
    val compoundingAlphaPct: Double,
    val powerBand: UnitPowerBand,
    val projectedValueAtTarget: Double,
    val benchmarkTargetValue: Double
)

/**
 * Comprehensive Units Power evaluation for an individual mutual fund.
 */
data class FundUnitsPowerReport(
    val fundItem: FundUiItem,
    val schemeName: String,
    val category: String,
    val totalUnits: Double,
    val averageNav: Double,
    val latestNav: Double,
    val investedAmount: Double,
    val currentValue: Double,
    val overallXirr: Double,
    val benchmarkXirr: Double,
    val targetDate: Date,
    val targetDateStr: String,
    val yearsToTarget: Double,
    val lots: List<UnitLotPowerItem>,
    val unitsPowerScore: Int,
    val superUnits: Double,
    val superUnitsPct: Double,
    val onTrackUnits: Double,
    val onTrackUnitsPct: Double,
    val moderateUnits: Double,
    val moderateUnitsPct: Double,
    val underperformingUnits: Double,
    val underperformingUnitsPct: Double,
    val outperformingTotalPct: Double,
    val underperformingTotalPct: Double,
    val recommendation: UnitsActionRecommendation,
    val recommendationReason: String,
    val projectedFutureValue: Double,
    val benchmarkFutureValue: Double,
    val projectedAlphaGain: Double,
    val navBandBelowUnits: Double,
    val navBandBelowPct: Double,
    val navBandAtBelow100Units: Double,
    val navBandAtBelow100Pct: Double,
    val navBandAtAbove100Units: Double,
    val navBandAtAbove100Pct: Double,
    val navBandAboveUnits: Double,
    val navBandAbovePct: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundUnitsPowerScreen(
    investedItems: List<FundUiItem>,
    allTxns: List<CasTransactionEntity>,
    initialSelectedSchemeName: String?,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPreferences = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val navPrefs = remember { context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE) }

    // Read Benchmark Target Parameters defined on Home Screen
    val benchmarkXirr = remember(sharedPreferences) {
        sharedPreferences.getString("benchmark_xirr", "12")?.toDoubleOrNull() ?: 12.0
    }

    val targetDateCal = remember(sharedPreferences, navPrefs) {
        val savedTime = sharedPreferences.getLong("future_val_time", -1L).let {
            if (it != -1L) it else navPrefs.getLong("future_val_time", -1L)
        }
        Calendar.getInstance().apply {
            if (savedTime != -1L) {
                timeInMillis = savedTime
            } else {
                set(2030, Calendar.JANUARY, 1, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    val targetDateStr = remember(targetDateCal) {
        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        sdf.format(targetDateCal.time)
    }

    val todayCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    val remainingYearsToTarget = remember(todayCal, targetDateCal) {
        val diffMillis = targetDateCal.timeInMillis - todayCal.timeInMillis
        max(0.2, diffMillis / (1000.0 * 60 * 60 * 24 * 365.25))
    }

    // Color Palette
    val greenColor = if (isDarkTheme) Color(0xFF00E676) else Color(0xFF1B5E20)
    val redColor = if (isDarkTheme) Color(0xFFFF5252) else Color(0xFFB71C1C)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val primaryColor = MaterialTheme.colorScheme.primary

    // Compute Units Power Reports for all funds
    val reports = remember(investedItems, allTxns, benchmarkXirr, targetDateCal) {
        computeAllFundReports(
            investedItems = investedItems,
            allTxns = allTxns,
            benchmarkXirr = benchmarkXirr,
            targetDate = targetDateCal.time,
            targetDateStr = targetDateStr,
            remainingYears = remainingYearsToTarget,
            today = todayCal.time
        )
    }

    // Total pages = 1 (Summary page) + reports.size (individual fund pages)
    val pageCount = reports.size + 1
    val initialPage = remember(reports, initialSelectedSchemeName) {
        if (initialSelectedSchemeName.isNullOrBlank()) {
            0
        } else {
            val idx = reports.indexOfFirst { it.schemeName.equals(initialSelectedSchemeName, ignoreCase = true) }
            if (idx >= 0) idx + 1 else 0
        }
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    var isGraphicalView by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (pagerState.currentPage == 0) "Portfolio Units Power" else "Fund Units Power",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Target ${String.format(Locale.US, "%.1f", benchmarkXirr)}% CAGR • Horizon $targetDateStr",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = onSurfaceVariantColor,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("units_power_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Portfolio Snapshot"
                        )
                    }
                },
                actions = {
                    // Toggle Graphical vs Detailed view
                    IconButton(
                        onClick = { isGraphicalView = !isGraphicalView },
                        modifier = Modifier.testTag("units_power_graphical_toggle")
                    ) {
                        Icon(
                            imageVector = if (isGraphicalView) Icons.Default.List else Icons.Default.BarChart,
                            contentDescription = "Toggle Graphical View",
                            tint = primaryColor
                        )
                    }

                    // Quick indicator showing active page out of total
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 0) "SUMMARY" else "${pagerState.currentPage}/${reports.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = primaryColor
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor,
                    titleContentColor = onSurfaceColor
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scrollable Tab / Header Row for Smooth Tap Navigation
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 12.dp,
                divider = { HorizontalDivider(color = outlineVariantColor) },
                containerColor = surfaceColor
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (pagerState.currentPage == 0) primaryColor else onSurfaceVariantColor
                            )
                            Text(
                                text = "All Funds Summary",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                )

                reports.forEachIndexed { index, report ->
                    val isSelected = pagerState.currentPage == (index + 1)
                    Tab(
                        selected = isSelected,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index + 1) }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(report.recommendation.color)
                                )
                                Text(
                                    text = report.schemeName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                }
            }

            // Prominent View Toggle Bar (Graphical vs Detailed)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, outlineVariantColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Graphical View Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isGraphicalView = true }
                            .testTag("toggle_graphical_view_btn"),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isGraphicalView) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                        border = if (isGraphicalView) BorderStroke(1.dp, primaryColor) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isGraphicalView) primaryColor else onSurfaceVariantColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Graphical (Default)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isGraphicalView) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isGraphicalView) primaryColor else onSurfaceVariantColor,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Detailed View Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isGraphicalView = false }
                            .testTag("toggle_detailed_view_btn"),
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isGraphicalView) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                        border = if (!isGraphicalView) BorderStroke(1.dp, primaryColor) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (!isGraphicalView) primaryColor else onSurfaceVariantColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detailed View",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (!isGraphicalView) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isGraphicalView) primaryColor else onSurfaceVariantColor,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Swipeable Horizontal Pager across Funds and Summary
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("units_power_pager")
            ) { page ->
                if (page == 0) {
                    if (isGraphicalView) {
                        GraphicalUnitsPowerSummaryPage(
                            reports = reports,
                            benchmarkXirr = benchmarkXirr,
                            targetDateStr = targetDateStr,
                            remainingYears = remainingYearsToTarget,
                            greenColor = greenColor,
                            redColor = redColor,
                            surfaceColor = surfaceColor,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = onSurfaceVariantColor,
                            outlineVariantColor = outlineVariantColor,
                            primaryColor = primaryColor,
                            onSelectFund = { schemeName ->
                                val targetIndex = reports.indexOfFirst { it.schemeName.equals(schemeName, ignoreCase = true) }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(targetIndex + 1) }
                                }
                            }
                        )
                    } else {
                        UnitsPowerSummaryPage(
                            reports = reports,
                            benchmarkXirr = benchmarkXirr,
                            targetDateStr = targetDateStr,
                            remainingYears = remainingYearsToTarget,
                            greenColor = greenColor,
                            redColor = redColor,
                            surfaceColor = surfaceColor,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = onSurfaceVariantColor,
                            outlineVariantColor = outlineVariantColor,
                            primaryColor = primaryColor,
                            onSelectFund = { schemeName ->
                                val targetIndex = reports.indexOfFirst { it.schemeName.equals(schemeName, ignoreCase = true) }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(targetIndex + 1) }
                                }
                            }
                        )
                    }
                } else {
                    val reportIndex = page - 1
                    if (reportIndex in reports.indices) {
                        if (isGraphicalView) {
                            GraphicalIndividualFundUnitsPowerPage(
                                report = reports[reportIndex],
                                greenColor = greenColor,
                                redColor = redColor,
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                onSurfaceVariantColor = onSurfaceVariantColor,
                                outlineVariantColor = outlineVariantColor,
                                primaryColor = primaryColor
                            )
                        } else {
                            IndividualFundUnitsPowerPage(
                                report = reports[reportIndex],
                                greenColor = greenColor,
                                redColor = redColor,
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                onSurfaceVariantColor = onSurfaceVariantColor,
                                outlineVariantColor = outlineVariantColor,
                                primaryColor = primaryColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Fund Units Power View: Detailed compounding telemetry, band distribution & actionable recommendations.
 */
@Composable
private fun IndividualFundUnitsPowerPage(
    report: FundUnitsPowerReport,
    greenColor: Color,
    redColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    outlineVariantColor: Color,
    primaryColor: Color
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

            // Fund Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, outlineVariantColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = report.category.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    color = onSurfaceVariantColor
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "${report.lots.size} Purchase Lots",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = onSurfaceVariantColor
                            )
                        )
                    }

                    Text(
                        text = report.schemeName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )

                    HorizontalDivider(color = outlineVariantColor.copy(alpha = 0.5f))

                    // Metrics Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "TOTAL UNITS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceVariantColor
                                )
                            )
                            Text(
                                text = formatIndianCommas(report.totalUnits, 3),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "LATEST NAV",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceVariantColor
                                )
                            )
                            Text(
                                text = "₹${formatIndianCommas(report.latestNav, 2)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "AVG NAV COST",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceVariantColor
                                )
                            )
                            Text(
                                text = "₹${formatIndianCommas(report.averageNav, 2)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Units Power Score Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, report.recommendation.color.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Visual Score Badge
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(report.recommendation.color.copy(alpha = 0.12f))
                            .border(2.dp, report.recommendation.color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${report.unitsPowerScore}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    color = report.recommendation.color
                                )
                            )
                            Text(
                                text = "POWER",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    color = report.recommendation.color
                                )
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UNITS COMPOUNDING POWER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = onSurfaceVariantColor,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = when {
                                report.unitsPowerScore >= 80 -> "Exceptional Compounding Engine"
                                report.unitsPowerScore >= 65 -> "Robust On-Track Compounding"
                                report.unitsPowerScore >= 50 -> "Steady Accumulation Phase"
                                else -> "Value Consolidation / Rebound Stage"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.1f%% of units beating target • %s%.1f%% alpha vs %.1f%% benchmark",
                                report.outperformingTotalPct,
                                if (report.overallXirr >= report.benchmarkXirr) "+" else "",
                                report.overallXirr - report.benchmarkXirr,
                                report.benchmarkXirr
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                color = onSurfaceVariantColor
                            )
                        )
                    }
                }
            }
        }

        // Action Decision Card: Whether to add more units now or wait
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = report.recommendation.color.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, report.recommendation.color.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = report.recommendation.icon,
                            contentDescription = null,
                            tint = report.recommendation.color,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = report.recommendation.badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp,
                                color = report.recommendation.color,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    Text(
                        text = report.recommendation.headline,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    )

                    Text(
                        text = report.recommendationReason,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = onSurfaceColor
                        )
                    )
                }
            }
        }

        // Compounding Power Bands Graphical Visualizer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, outlineVariantColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UNITS COMPOUNDING BANDS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = onSurfaceVariantColor,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = "Based on ${String.format(Locale.US, "%.1f", report.benchmarkXirr)}% Target",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                color = onSurfaceVariantColor
                            )
                        )
                    }

                    // Stacked Segmented Visual Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        if (report.superUnitsPct > 0.1) {
                            Box(
                                modifier = Modifier
                                    .weight(report.superUnitsPct.toFloat())
                                    .fillMaxHeight()
                                    .background(UnitPowerBand.SUPER_POWER.color)
                            )
                        }
                        if (report.onTrackUnitsPct > 0.1) {
                            Box(
                                modifier = Modifier
                                    .weight(report.onTrackUnitsPct.toFloat())
                                    .fillMaxHeight()
                                    .background(UnitPowerBand.ON_TRACK.color)
                            )
                        }
                        if (report.moderateUnitsPct > 0.1) {
                            Box(
                                modifier = Modifier
                                    .weight(report.moderateUnitsPct.toFloat())
                                    .fillMaxHeight()
                                    .background(UnitPowerBand.MODERATE_CATCHUP.color)
                            )
                        }
                        if (report.underperformingUnitsPct > 0.1) {
                            Box(
                                modifier = Modifier
                                    .weight(report.underperformingUnitsPct.toFloat())
                                    .fillMaxHeight()
                                    .background(UnitPowerBand.UNDERPERFORMING_DISCOUNT.color)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 4-Band Breakdown Details
                    BandDetailRow(
                        band = UnitPowerBand.SUPER_POWER,
                        units = report.superUnits,
                        pct = report.superUnitsPct,
                        onSurfaceVariantColor = onSurfaceVariantColor
                    )
                    HorizontalDivider(color = outlineVariantColor.copy(alpha = 0.35f))
                    BandDetailRow(
                        band = UnitPowerBand.ON_TRACK,
                        units = report.onTrackUnits,
                        pct = report.onTrackUnitsPct,
                        onSurfaceVariantColor = onSurfaceVariantColor
                    )
                    HorizontalDivider(color = outlineVariantColor.copy(alpha = 0.35f))
                    BandDetailRow(
                        band = UnitPowerBand.MODERATE_CATCHUP,
                        units = report.moderateUnits,
                        pct = report.moderateUnitsPct,
                        onSurfaceVariantColor = onSurfaceVariantColor
                    )
                    HorizontalDivider(color = outlineVariantColor.copy(alpha = 0.35f))
                    BandDetailRow(
                        band = UnitPowerBand.UNDERPERFORMING_DISCOUNT,
                        units = report.underperformingUnits,
                        pct = report.underperformingUnitsPct,
                        onSurfaceVariantColor = onSurfaceVariantColor
                    )
                }
            }
        }

        // Compounding Horizon Projection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, outlineVariantColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PROJECTED VALUE AT TARGET HORIZON",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = onSurfaceVariantColor,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Units Projected Value",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = onSurfaceVariantColor
                            )
                            Text(
                                text = "₹${formatIndianCommas(report.projectedFutureValue, 0)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (report.projectedAlphaGain >= 0) greenColor else redColor
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Benchmark Target (${String.format(Locale.US, "%.0f", report.benchmarkXirr)}%)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = onSurfaceVariantColor
                            )
                            Text(
                                text = "₹${formatIndianCommas(report.benchmarkFutureValue, 0)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }

                    val isSurplus = report.projectedAlphaGain >= 0.0
                    val surplusPrefix = if (isSurplus) "+" else ""
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (isSurplus) greenColor else redColor).copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Compounding Surplus vs Target: %s₹%s by %s",
                                surplusPrefix,
                                formatIndianCommas(report.projectedAlphaGain, 0),
                                report.targetDateStr
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = if (isSurplus) greenColor else redColor
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Purchase Batches (Lots) Breakdown
        if (report.lots.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    border = BorderStroke(1.dp, outlineVariantColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Purchase Batches (Lots) Grouped Breakdown
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "PURCHASE BATCHES SUMMARY (BY COST BANDS)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = onSurfaceVariantColor,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            NavBandDetailRow(
                                color = Color(0xFF00E676),
                                title = "Below Average NAV",
                                subtitle = "Cost < 95% of Avg",
                                units = report.navBandBelowUnits,
                                pct = report.navBandBelowPct,
                                onSurfaceVariantColor = onSurfaceVariantColor
                            )
                            NavBandDetailRow(
                                color = Color(0xFF29B6F6),
                                title = "At Average NAV (95-100%)",
                                subtitle = "Cost 95% - 100% of Avg",
                                units = report.navBandAtBelow100Units,
                                pct = report.navBandAtBelow100Pct,
                                onSurfaceVariantColor = onSurfaceVariantColor
                            )
                            NavBandDetailRow(
                                color = Color(0xFF29B6F6),
                                title = "At Average NAV (100-105%)",
                                subtitle = "Cost 100% - 105% of Avg",
                                units = report.navBandAtAbove100Units,
                                pct = report.navBandAtAbove100Pct,
                                onSurfaceVariantColor = onSurfaceVariantColor
                            )
                            NavBandDetailRow(
                                color = Color(0xFFFF5252),
                                title = "Above Average NAV",
                                subtitle = "Cost > 105% of Avg",
                                units = report.navBandAboveUnits,
                                pct = report.navBandAbovePct,
                                onSurfaceVariantColor = onSurfaceVariantColor
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Single Band summary row inside the distribution visualizer.
 */
@Composable
private fun BandDetailRow(
    band: UnitPowerBand,
    units: Double,
    pct: Double,
    onSurfaceVariantColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(band.color)
            )
            Column {
                Text(
                    text = band.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = band.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = onSurfaceVariantColor
                    )
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${String.format(Locale.US, "%.1f", pct)}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = band.color
                )
            )
            Text(
                text = "${formatIndianCommas(units, 2)} units",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = onSurfaceVariantColor
                )
            )
        }
    }
}

@Composable
private fun NavBandDetailRow(
    color: Color,
    title: String,
    subtitle: String,
    units: Double,
    pct: Double,
    onSurfaceVariantColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = onSurfaceVariantColor
                    )
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${String.format(Locale.US, "%.1f", pct)}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = color
                )
            )
            Text(
                text = "${formatIndianCommas(units, 2)} units",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = onSurfaceVariantColor
                )
            )
        }
    }
}

/**
 * Special Summary Page (Page 0): Cross-fund comparison matrix, total portfolio units breakdown, and strategic insights.
 */
@Composable
private fun UnitsPowerSummaryPage(
    reports: List<FundUnitsPowerReport>,
    benchmarkXirr: Double,
    targetDateStr: String,
    remainingYears: Double,
    greenColor: Color,
    redColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    outlineVariantColor: Color,
    primaryColor: Color,
    onSelectFund: (String) -> Unit
) {
    val totalPortfolioUnits = remember(reports) { reports.sumOf { it.totalUnits } }
    val totalInvested = remember(reports) { reports.sumOf { it.investedAmount } }
    val totalCurrentVal = remember(reports) { reports.sumOf { it.currentValue } }
    val avgScore = remember(reports) {
        if (reports.isNotEmpty()) (reports.sumOf { it.unitsPowerScore }.toDouble() / reports.size).roundToInt() else 0
    }

    val totalSuperUnits = remember(reports) { reports.sumOf { it.superUnits } }
    val totalOnTrackUnits = remember(reports) { reports.sumOf { it.onTrackUnits } }
    val totalModerateUnits = remember(reports) { reports.sumOf { it.moderateUnits } }
    val totalUnderperformingUnits = remember(reports) { reports.sumOf { it.underperformingUnits } }

    val totalOutperformingPct = if (totalPortfolioUnits > 0.0) {
        ((totalSuperUnits + totalOnTrackUnits) / totalPortfolioUnits) * 100.0
    } else 0.0

    val totalUnderperformingPct = if (totalPortfolioUnits > 0.0) {
        ((totalModerateUnits + totalUnderperformingUnits) / totalPortfolioUnits) * 100.0
    } else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

            // Portfolio Overview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, outlineVariantColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PORTFOLIO UNITS POWER INDEX",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = onSurfaceVariantColor,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = primaryColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${reports.size} INVESTED FUNDS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    color = primaryColor
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.12f))
                                .border(2.dp, primaryColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$avgScore",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = primaryColor
                                    )
                                )
                                Text(
                                    text = "/ 100",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Accumulation: ${formatIndianCommas(totalPortfolioUnits, 2)} Units",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.1f%% of portfolio units are on or ahead of benchmark target (%.1f%% CAGR by %s)",
                                    totalOutperformingPct,
                                    benchmarkXirr,
                                    targetDateStr
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = onSurfaceVariantColor
                                )
                            )
                        }
                    }

                    // Portfolio Wide Multi-segment Stacked Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        if (totalSuperUnits > 0.1) {
                            Box(
                                modifier = Modifier
                                    .weight((totalSuperUnits / totalPortfolioUnits).toFloat())
                                    .fillMaxHeight()
                                    .background(UnitPowerBand.SUPER_POWER.color)
                            )
                        }
                        if (totalOnTrackUnits > 0.1) {
                            Box(
                                modifier = Modifier
                                    .weight((totalOnTrackUnits / totalPortfolioUnits).toFloat())
                                    .fillMaxHeight()
                                    .background(UnitPowerBand.ON_TRACK.color)
                            )
                        }
                        if (totalModerateUnits > 0.1) {
                            Box(
                                modifier = Modifier
                                    .weight((totalModerateUnits / totalPortfolioUnits).toFloat())
                                    .fillMaxHeight()
                                    .background(UnitPowerBand.MODERATE_CATCHUP.color)
                            )
                        }
                        if (totalUnderperformingUnits > 0.1) {
                            Box(
                                modifier = Modifier
                                    .weight((totalUnderperformingUnits / totalPortfolioUnits).toFloat())
                                    .fillMaxHeight()
                                    .background(UnitPowerBand.UNDERPERFORMING_DISCOUNT.color)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🟢 Performing Well: ${String.format(Locale.US, "%.1f", totalOutperformingPct)}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp,
                                color = UnitPowerBand.SUPER_POWER.color
                            )
                        )
                        Text(
                            text = "🟡 Underperforming: ${String.format(Locale.US, "%.1f", totalUnderperformingPct)}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp,
                                color = UnitPowerBand.MODERATE_CATCHUP.color
                            )
                        )
                    }
                }
            }
        }

        // Comparative Leaderboard
        item {
            Text(
                text = "FUND-WISE UNITS POWER COMPARISON",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = onSurfaceVariantColor,
                    letterSpacing = 0.5.sp
                )
            )
        }

        items(reports.sortedByDescending { it.unitsPowerScore }) { report ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectFund(report.schemeName) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, outlineVariantColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = report.schemeName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = report.recommendation.color.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = report.recommendation.badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.5.sp,
                                    color = report.recommendation.color
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatIndianCommas(report.totalUnits, 2)} units @ ₹${formatIndianCommas(report.averageNav, 2)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = onSurfaceVariantColor
                            )
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", report.outperformingTotalPct)}% Ahead",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = UnitPowerBand.SUPER_POWER.color
                                )
                            )

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${report.unitsPowerScore} pts",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Strategic Accumulation Playbook
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = primaryColor.copy(alpha = 0.06f)
                ),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "STRATEGIC UNIT ACCUMULATION PLAYBOOK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = primaryColor,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Text(
                        text = "• Expand Units Now: Funds with high underperforming bands (costing near or below average NAV) give the highest mathematical compounding leverage when funded today.\n" +
                                "• Continue Steady SIPs: Funds whose units are compounding securely on-track require no aggressive lumpsums; maintain disciplined systematic investing.\n" +
                                "• Defer Large Lumpsums: Funds running at extreme alpha velocity (>90% units ahead) should be allowed to run, but new bulk cash is best reserved for cyclical dips.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = onSurfaceColor
                        )
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Mathematical engine calculating compounding power for each mutual fund.
 */
private fun computeAllFundReports(
    investedItems: List<FundUiItem>,
    allTxns: List<CasTransactionEntity>,
    benchmarkXirr: Double,
    targetDate: Date,
    targetDateStr: String,
    remainingYears: Double,
    today: Date
): List<FundUnitsPowerReport> {
    val reports = mutableListOf<FundUnitsPowerReport>()

    for (item in investedItems) {
        val schemeTxns = ReturnsCalculations.filterTxnsForScheme(allTxns, item.schemeName)
        val rawLots = if (schemeTxns.isNotEmpty()) {
            ReturnsCalculations.runFifo(schemeTxns)
        } else {
            emptyList()
        }

        val totalUnits = item.unitsHeld
        val currentNav = item.fund.navValue
        val averageNav = if (totalUnits > 0.0) item.investedAmount / totalUnits else currentNav

        // Evaluate each lot
        val lots = if (rawLots.isNotEmpty()) {
            rawLots.map { lot ->
                val daysHeld = max(1.0, (today.time - lot.date.time) / (1000.0 * 60 * 60 * 24))
                val yearsHeld = daysHeld / 365.25
                val lotCost = lot.units * lot.costPerUnit
                val lotVal = lot.units * currentNav
                val absGain = lotVal - lotCost
                val absReturnPct = if (lotCost > 0.0) (absGain / lotCost) * 100.0 else 0.0

                val cagr = if (yearsHeld >= 0.5 && lot.costPerUnit > 0.0 && currentNav > 0.0) {
                    ((currentNav / lot.costPerUnit).pow(1.0 / yearsHeld) - 1.0) * 100.0
                } else {
                    absReturnPct
                }

                val alpha = cagr - benchmarkXirr

                val band = when {
                    alpha > 3.0 -> UnitPowerBand.SUPER_POWER
                    alpha >= -2.0 -> UnitPowerBand.ON_TRACK
                    cagr >= 0.0 -> UnitPowerBand.MODERATE_CATCHUP
                    else -> UnitPowerBand.UNDERPERFORMING_DISCOUNT
                }

                val projectedVal = lotVal * (1.0 + max(0.0, cagr / 100.0)).pow(remainingYears)
                val benchTargetVal = lotCost * (1.0 + benchmarkXirr / 100.0).pow(yearsHeld + remainingYears)

                UnitLotPowerItem(
                    units = lot.units,
                    costPerUnit = lot.costPerUnit,
                    purchaseDate = lot.date,
                    daysHeld = daysHeld,
                    yearsHeld = yearsHeld,
                    currentNav = currentNav,
                    lotCurrentValue = lotVal,
                    lotCost = lotCost,
                    absoluteReturnPct = absReturnPct,
                    annualizedCagrPct = cagr,
                    compoundingAlphaPct = alpha,
                    powerBand = band,
                    projectedValueAtTarget = projectedVal,
                    benchmarkTargetValue = benchTargetVal
                )
            }
        } else {
            // Synthesize holding if raw CAS transactions are unavailable
            val daysHeld = 365.0
            val yearsHeld = 1.0
            val absReturnPct = item.totalPlPercent
            val cagr = absReturnPct
            val alpha = cagr - benchmarkXirr
            val band = when {
                alpha > 3.0 -> UnitPowerBand.SUPER_POWER
                alpha >= -2.0 -> UnitPowerBand.ON_TRACK
                cagr >= 0.0 -> UnitPowerBand.MODERATE_CATCHUP
                else -> UnitPowerBand.UNDERPERFORMING_DISCOUNT
            }
            listOf(
                UnitLotPowerItem(
                    units = totalUnits,
                    costPerUnit = averageNav,
                    purchaseDate = today,
                    daysHeld = daysHeld,
                    yearsHeld = yearsHeld,
                    currentNav = currentNav,
                    lotCurrentValue = item.currentValue,
                    lotCost = item.investedAmount,
                    absoluteReturnPct = absReturnPct,
                    annualizedCagrPct = cagr,
                    compoundingAlphaPct = alpha,
                    powerBand = band,
                    projectedValueAtTarget = item.currentValue * (1.0 + max(0.0, cagr / 100.0)).pow(remainingYears),
                    benchmarkTargetValue = item.investedAmount * (1.0 + benchmarkXirr / 100.0).pow(yearsHeld + remainingYears)
                )
            )
        }

        // Aggregate units by band
        val superUnits = lots.filter { it.powerBand == UnitPowerBand.SUPER_POWER }.sumOf { it.units }
        val onTrackUnits = lots.filter { it.powerBand == UnitPowerBand.ON_TRACK }.sumOf { it.units }
        val moderateUnits = lots.filter { it.powerBand == UnitPowerBand.MODERATE_CATCHUP }.sumOf { it.units }
        val underperformingUnits = lots.filter { it.powerBand == UnitPowerBand.UNDERPERFORMING_DISCOUNT }.sumOf { it.units }

        val superUnitsPct = if (totalUnits > 0.0) (superUnits / totalUnits) * 100.0 else 0.0
        val onTrackUnitsPct = if (totalUnits > 0.0) (onTrackUnits / totalUnits) * 100.0 else 0.0
        val moderateUnitsPct = if (totalUnits > 0.0) (moderateUnits / totalUnits) * 100.0 else 0.0
        val underperformingUnitsPct = if (totalUnits > 0.0) (underperformingUnits / totalUnits) * 100.0 else 0.0

        val outperformingTotalPct = superUnitsPct + onTrackUnitsPct
        val underperformingTotalPct = moderateUnitsPct + underperformingUnitsPct

        val overallXirr = if (item.investedAmount > 0.0) item.totalPlPercent else 0.0

        // Score 0 to 100
        val outperformingScore = (outperformingTotalPct / 100.0) * 45.0
        val alphaVelocity = overallXirr - benchmarkXirr
        val alphaScore = ((alphaVelocity + 10.0) / 25.0).coerceIn(0.0, 1.0) * 30.0
        val navAdvantagePct = if (averageNav > 0.0) ((currentNav - averageNav) / averageNav) * 100.0 else 0.0
        val navScore = ((navAdvantagePct + 10.0) / 30.0).coerceIn(0.0, 1.0) * 15.0
        val unitsPowerScore = (outperformingScore + alphaScore + navScore + 10.0).roundToInt().coerceIn(18, 99)

        // Recommendation Determination
        val (rec, reason) = when {
            underperformingTotalPct >= 35.0 || currentNav <= averageNav * 1.04 -> {
                Pair(
                    UnitsActionRecommendation.ADD_UNITS_NOW,
                    String.format(
                        Locale.US,
                        "%.1f%% of units are consolidating below target benchmark. Current NAV (₹%s) offers an exceptional cost-averaging window near your average acquisition cost (₹%s). Expanding unit count now maximizes forward compounding velocity.",
                        underperformingTotalPct,
                        formatIndianCommas(currentNav, 2),
                        formatIndianCommas(averageNav, 2)
                    )
                )
            }
            outperformingTotalPct >= 85.0 && currentNav >= averageNav * 1.25 -> {
                Pair(
                    UnitsActionRecommendation.WAIT_FOR_DIPS,
                    String.format(
                        Locale.US,
                        "%.1f%% of your units are compounding at peak momentum (NAV is +%.1f%% above cost). Compounding power is outstanding, but fresh aggressive lumpsums should wait for mild cyclical pullbacks to protect margin of safety.",
                        outperformingTotalPct,
                        navAdvantagePct
                    )
                )
            }
            else -> {
                Pair(
                    UnitsActionRecommendation.STEADY_SIP,
                    String.format(
                        Locale.US,
                        "%.1f%% of units are securely compounding on track within the target corridor. Compounding runway is healthy. Continue systematic monthly SIP additions as scheduled.",
                        outperformingTotalPct
                    )
                )
            }
        }

        val projectedFutureValue = lots.sumOf { it.projectedValueAtTarget }
        val benchmarkFutureValue = lots.sumOf { it.benchmarkTargetValue }
        val projectedAlphaGain = projectedFutureValue - benchmarkFutureValue

        // Aggregate units by simplified Average NAV cost bands
        val navBandBelowUnits = lots.filter { it.costPerUnit < averageNav * 0.95 }.sumOf { it.units }
        val navBandAtBelow100Units = lots.filter { it.costPerUnit >= averageNav * 0.95 && it.costPerUnit < averageNav }.sumOf { it.units }
        val navBandAtAbove100Units = lots.filter { it.costPerUnit >= averageNav && it.costPerUnit <= averageNav * 1.05 }.sumOf { it.units }
        val navBandAboveUnits = lots.filter { it.costPerUnit > averageNav * 1.05 }.sumOf { it.units }

        val navBandBelowPct = if (totalUnits > 0.0) (navBandBelowUnits / totalUnits) * 100.0 else 0.0
        val navBandAtBelow100Pct = if (totalUnits > 0.0) (navBandAtBelow100Units / totalUnits) * 100.0 else 0.0
        val navBandAtAbove100Pct = if (totalUnits > 0.0) (navBandAtAbove100Units / totalUnits) * 100.0 else 0.0
        val navBandAbovePct = if (totalUnits > 0.0) (navBandAboveUnits / totalUnits) * 100.0 else 0.0

        reports.add(
            FundUnitsPowerReport(
                fundItem = item,
                schemeName = item.schemeName,
                category = detectFundCategory(item.schemeName),
                totalUnits = totalUnits,
                averageNav = averageNav,
                latestNav = currentNav,
                investedAmount = item.investedAmount,
                currentValue = item.currentValue,
                overallXirr = overallXirr,
                benchmarkXirr = benchmarkXirr,
                targetDate = targetDate,
                targetDateStr = targetDateStr,
                yearsToTarget = remainingYears,
                lots = lots,
                unitsPowerScore = unitsPowerScore,
                superUnits = superUnits,
                superUnitsPct = superUnitsPct,
                onTrackUnits = onTrackUnits,
                onTrackUnitsPct = onTrackUnitsPct,
                moderateUnits = moderateUnits,
                moderateUnitsPct = moderateUnitsPct,
                underperformingUnits = underperformingUnits,
                underperformingUnitsPct = underperformingUnitsPct,
                outperformingTotalPct = outperformingTotalPct,
                underperformingTotalPct = underperformingTotalPct,
                recommendation = rec,
                recommendationReason = reason,
                projectedFutureValue = projectedFutureValue,
                benchmarkFutureValue = benchmarkFutureValue,
                projectedAlphaGain = projectedAlphaGain,
                navBandBelowUnits = navBandBelowUnits,
                navBandBelowPct = navBandBelowPct,
                navBandAtBelow100Units = navBandAtBelow100Units,
                navBandAtBelow100Pct = navBandAtBelow100Pct,
                navBandAtAbove100Units = navBandAtAbove100Units,
                navBandAtAbove100Pct = navBandAtAbove100Pct,
                navBandAboveUnits = navBandAboveUnits,
                navBandAbovePct = navBandAbovePct
            )
        )
    }

    return reports
}

@Composable
private fun GraphicalUnitsPowerSummaryPage(
    reports: List<FundUnitsPowerReport>,
    benchmarkXirr: Double,
    targetDateStr: String,
    remainingYears: Double,
    greenColor: Color,
    redColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    outlineVariantColor: Color,
    primaryColor: Color,
    onSelectFund: (String) -> Unit
) {
    val totalPortfolioValue = reports.sumOf { it.currentValue }
    val totalProjected = reports.sumOf { it.projectedFutureValue }
    val totalBenchmark = reports.sumOf { it.benchmarkFutureValue }
    val avgScore = if (reports.isNotEmpty()) reports.map { it.unitsPowerScore }.average().toInt() else 0

    val totalPortfolioUnits = reports.sumOf { it.totalUnits }
    val totalSuperUnits = reports.sumOf { it.superUnits }
    val totalOnTrackUnits = reports.sumOf { it.onTrackUnits }
    val totalModerateUnits = reports.sumOf { it.moderateUnits }
    val totalUnderperformingUnits = reports.sumOf { it.underperformingUnits }

    val totalSuperPct = if (totalPortfolioUnits > 0.0) (totalSuperUnits / totalPortfolioUnits) * 100.0 else 0.0
    val totalOnTrackPct = if (totalPortfolioUnits > 0.0) (totalOnTrackUnits / totalPortfolioUnits) * 100.0 else 0.0
    val totalModeratePct = if (totalPortfolioUnits > 0.0) (totalModerateUnits / totalPortfolioUnits) * 100.0 else 0.0
    val totalUnderperformingPct = if (totalPortfolioUnits > 0.0) (totalUnderperformingUnits / totalPortfolioUnits) * 100.0 else 0.0

    val totalBelowUnits = reports.sumOf { it.navBandBelowUnits }
    val totalAtBelow100Units = reports.sumOf { it.navBandAtBelow100Units }
    val totalAtAbove100Units = reports.sumOf { it.navBandAtAbove100Units }
    val totalAboveUnits = reports.sumOf { it.navBandAboveUnits }

    val totalBelowPct = if (totalPortfolioUnits > 0.0) (totalBelowUnits / totalPortfolioUnits) * 100.0 else 0.0
    val totalAtBelow100Pct = if (totalPortfolioUnits > 0.0) (totalAtBelow100Units / totalPortfolioUnits) * 100.0 else 0.0
    val totalAtAbove100Pct = if (totalPortfolioUnits > 0.0) (totalAtAbove100Units / totalPortfolioUnits) * 100.0 else 0.0
    val totalAbovePct = if (totalPortfolioUnits > 0.0) (totalAboveUnits / totalPortfolioUnits) * 100.0 else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, outlineVariantColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PORTFOLIO UNITS POWER GRAPHIC",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = "Target Horizon: $targetDateStr",
                                style = MaterialTheme.typography.bodySmall.copy(color = onSurfaceVariantColor)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Score: $avgScore/100",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Donut Chart & Breakdown Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            UnitBandsDonutChart(
                                superPct = totalSuperPct.toFloat(),
                                onTrackPct = totalOnTrackPct.toFloat(),
                                moderatePct = totalModeratePct.toFloat(),
                                underperformingPct = totalUnderperformingPct.toFloat(),
                                modifier = Modifier.fillMaxSize()
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", totalSuperPct + totalOnTrackPct)}%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = greenColor
                                    )
                                )
                                Text(
                                    text = "On Track",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BandLegendItem(
                                color = UnitPowerBand.SUPER_POWER.color,
                                title = UnitPowerBand.SUPER_POWER.title,
                                subtitle = UnitPowerBand.SUPER_POWER.subtitle,
                                pct = totalSuperPct
                            )
                            BandLegendItem(
                                color = UnitPowerBand.ON_TRACK.color,
                                title = UnitPowerBand.ON_TRACK.title,
                                subtitle = UnitPowerBand.ON_TRACK.subtitle,
                                pct = totalOnTrackPct
                            )
                            BandLegendItem(
                                color = UnitPowerBand.MODERATE_CATCHUP.color,
                                title = UnitPowerBand.MODERATE_CATCHUP.title,
                                subtitle = UnitPowerBand.MODERATE_CATCHUP.subtitle,
                                pct = totalModeratePct
                            )
                            BandLegendItem(
                                color = UnitPowerBand.UNDERPERFORMING_DISCOUNT.color,
                                title = UnitPowerBand.UNDERPERFORMING_DISCOUNT.title,
                                subtitle = UnitPowerBand.UNDERPERFORMING_DISCOUNT.subtitle,
                                pct = totalUnderperformingPct
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = outlineVariantColor)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Current Value",
                                style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor)
                            )
                            Text(
                                text = "₹${formatIndianCommas(totalPortfolioValue, 0)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceColor
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Projected Value (Target)",
                                style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor)
                            )
                            Text(
                                text = "₹${formatIndianCommas(totalProjected, 0)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = greenColor
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val maxVal = max(totalProjected, totalBenchmark)
                    val projFraction = if (maxVal > 0.0) (totalProjected / maxVal).toFloat() else 0f
                    val benchFraction = if (maxVal > 0.0) (totalBenchmark / maxVal).toFloat() else 0f

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Compounding Projection", style = MaterialTheme.typography.labelSmall.copy(color = greenColor, fontWeight = FontWeight.Bold))
                            Text("₹${formatIndianCommas(totalProjected, 0)}", style = MaterialTheme.typography.labelSmall.copy(color = greenColor, fontWeight = FontWeight.Bold))
                        }
                        LinearProgressIndicator(
                            progress = { projFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = greenColor,
                            trackColor = outlineVariantColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Benchmark Target (${String.format(Locale.US, "%.1f", benchmarkXirr)}% CAGR)", style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor))
                            Text("₹${formatIndianCommas(totalBenchmark, 0)}", style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor))
                        }
                        LinearProgressIndicator(
                            progress = { benchFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = primaryColor,
                            trackColor = outlineVariantColor
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, outlineVariantColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UNITS BY AVERAGE NAV",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = "Unit distribution relative to average acquisition NAV",
                                style = MaterialTheme.typography.bodySmall.copy(color = onSurfaceVariantColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AverageNavBandsDonutChart(
                                belowPct = totalBelowPct.toFloat(),
                                atBelow100Pct = totalAtBelow100Pct.toFloat(),
                                atAbove100Pct = totalAtAbove100Pct.toFloat(),
                                abovePct = totalAbovePct.toFloat(),
                                modifier = Modifier.fillMaxSize()
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", totalBelowPct)}%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E676)
                                    )
                                )
                                Text(
                                    text = "Below Avg",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BandLegendItem(
                                color = Color(0xFF00E676),
                                title = "Below Average NAV",
                                subtitle = "Cost < 95% of Avg",
                                pct = totalBelowPct
                            )
                            BandLegendItem(
                                color = Color(0xFF29B6F6),
                                title = "At Average NAV (95-100%)",
                                subtitle = "Cost 95% - 100% of Avg",
                                pct = totalAtBelow100Pct
                            )
                            BandLegendItem(
                                color = Color(0xFF81D4FA),
                                title = "At Average NAV (100-105%)",
                                subtitle = "Cost 100% - 105% of Avg",
                                pct = totalAtAbove100Pct
                            )
                            BandLegendItem(
                                color = Color(0xFFFF5252),
                                title = "Above Average NAV",
                                subtitle = "Cost > 105% of Avg",
                                pct = totalAbovePct
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "FUND-WISE UNITS POWER GRAPHIC COMPARISON",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 0.5.sp
                )
            )
        }

        items(reports) { report ->
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, outlineVariantColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectFund(report.schemeName) }
                    .testTag("summary_graphical_fund_${report.schemeName}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.schemeName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceColor
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${report.category} • XIRR: ${String.format(Locale.US, "%.1f%%", report.overallXirr)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = onSurfaceVariantColor)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = report.recommendation.color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Score ${report.unitsPowerScore}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = report.recommendation.color
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(outlineVariantColor)
                    ) {
                        val sup = (report.superUnitsPct / 100f).toFloat().coerceIn(0f, 1f)
                        val trk = (report.onTrackUnitsPct / 100f).toFloat().coerceIn(0f, 1f)
                        val mod = (report.moderateUnitsPct / 100f).toFloat().coerceIn(0f, 1f)
                        val und = (report.underperformingUnitsPct / 100f).toFloat().coerceIn(0f, 1f)

                        if (sup > 0f) Box(modifier = Modifier.weight(sup).fillMaxHeight().background(UnitPowerBand.SUPER_POWER.color))
                        if (trk > 0f) Box(modifier = Modifier.weight(trk).fillMaxHeight().background(UnitPowerBand.ON_TRACK.color))
                        if (mod > 0f) Box(modifier = Modifier.weight(mod).fillMaxHeight().background(UnitPowerBand.MODERATE_CATCHUP.color))
                        if (und > 0f) Box(modifier = Modifier.weight(und).fillMaxHeight().background(UnitPowerBand.UNDERPERFORMING_DISCOUNT.color))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Outperforming: ${String.format(Locale.US, "%.1f%%", report.outperformingTotalPct)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = greenColor, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Trailing: ${String.format(Locale.US, "%.1f%%", report.underperformingTotalPct)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = redColor, fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = report.recommendation.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = report.recommendation.color
                        )
                        Text(
                            text = report.recommendation.badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = report.recommendation.color,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphicalIndividualFundUnitsPowerPage(
    report: FundUnitsPowerReport,
    greenColor: Color,
    redColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    outlineVariantColor: Color,
    primaryColor: Color
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, outlineVariantColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.schemeName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceColor
                                )
                            )
                            Text(
                                text = "${report.category} • Total Units: ${String.format(Locale.US, "%.2f", report.totalUnits)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = onSurfaceVariantColor)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = report.recommendation.color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Score ${report.unitsPowerScore}/100",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = report.recommendation.color
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = report.recommendation.color.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, report.recommendation.color.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = report.recommendation.icon,
                                contentDescription = null,
                                tint = report.recommendation.color,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = report.recommendation.badgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = report.recommendation.color
                                    )
                                )
                                Text(
                                    text = report.recommendationReason,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = onSurfaceColor,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "UNIT PERFORMANCE BANDS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceVariantColor,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            UnitBandsDonutChart(
                                superPct = report.superUnitsPct.toFloat(),
                                onTrackPct = report.onTrackUnitsPct.toFloat(),
                                moderatePct = report.moderateUnitsPct.toFloat(),
                                underperformingPct = report.underperformingUnitsPct.toFloat(),
                                modifier = Modifier.fillMaxSize()
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", report.outperformingTotalPct)}%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = greenColor
                                    )
                                )
                                Text(
                                    text = "Ahead",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BandLegendItem(
                                color = UnitPowerBand.SUPER_POWER.color,
                                title = UnitPowerBand.SUPER_POWER.title,
                                subtitle = UnitPowerBand.SUPER_POWER.subtitle,
                                pct = report.superUnitsPct
                            )
                            BandLegendItem(
                                color = UnitPowerBand.ON_TRACK.color,
                                title = UnitPowerBand.ON_TRACK.title,
                                subtitle = UnitPowerBand.ON_TRACK.subtitle,
                                pct = report.onTrackUnitsPct
                            )
                            BandLegendItem(
                                color = UnitPowerBand.MODERATE_CATCHUP.color,
                                title = UnitPowerBand.MODERATE_CATCHUP.title,
                                subtitle = UnitPowerBand.MODERATE_CATCHUP.subtitle,
                                pct = report.moderateUnitsPct
                            )
                            BandLegendItem(
                                color = UnitPowerBand.UNDERPERFORMING_DISCOUNT.color,
                                title = UnitPowerBand.UNDERPERFORMING_DISCOUNT.title,
                                subtitle = UnitPowerBand.UNDERPERFORMING_DISCOUNT.subtitle,
                                pct = report.underperformingUnitsPct
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, outlineVariantColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "UNITS BY AVERAGE NAV",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceVariantColor,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AverageNavBandsDonutChart(
                                belowPct = report.navBandBelowPct.toFloat(),
                                atBelow100Pct = report.navBandAtBelow100Pct.toFloat(),
                                atAbove100Pct = report.navBandAtAbove100Pct.toFloat(),
                                abovePct = report.navBandAbovePct.toFloat(),
                                modifier = Modifier.fillMaxSize()
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", report.navBandBelowPct)}%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E676)
                                    )
                                )
                                Text(
                                    text = "Below Avg",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = onSurfaceVariantColor
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BandLegendItem(
                                color = Color(0xFF00E676),
                                title = "Below Average NAV",
                                subtitle = "Cost < 95% of Avg",
                                pct = report.navBandBelowPct
                            )
                            BandLegendItem(
                                color = Color(0xFF29B6F6),
                                title = "At Average NAV (95-100%)",
                                subtitle = "Cost 95% - 100% of Avg",
                                pct = report.navBandAtBelow100Pct
                            )
                            BandLegendItem(
                                color = Color(0xFF81D4FA),
                                title = "At Average NAV (100-105%)",
                                subtitle = "Cost 100% - 105% of Avg",
                                pct = report.navBandAtAbove100Pct
                            )
                            BandLegendItem(
                                color = Color(0xFFFF5252),
                                title = "Above Average NAV",
                                subtitle = "Cost > 105% of Avg",
                                pct = report.navBandAbovePct
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, outlineVariantColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "COMPOUNDING PROJECTION VS BENCHMARK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Value", style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor))
                            Text("₹${formatIndianCommas(report.currentValue, 0)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = onSurfaceColor))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Projected Value", style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor))
                            Text("₹${formatIndianCommas(report.projectedFutureValue, 0)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = greenColor))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val maxVal = max(report.projectedFutureValue, report.benchmarkFutureValue)
                    val projFrac = if (maxVal > 0.0) (report.projectedFutureValue / maxVal).toFloat() else 0f
                    val benchFrac = if (maxVal > 0.0) (report.benchmarkFutureValue / maxVal).toFloat() else 0f

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fund Projection", style = MaterialTheme.typography.labelSmall.copy(color = greenColor, fontWeight = FontWeight.Bold))
                            Text("₹${formatIndianCommas(report.projectedFutureValue, 0)}", style = MaterialTheme.typography.labelSmall.copy(color = greenColor, fontWeight = FontWeight.Bold))
                        }
                        LinearProgressIndicator(
                            progress = { projFrac },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = greenColor,
                            trackColor = outlineVariantColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Benchmark (${String.format(Locale.US, "%.1f", report.benchmarkXirr)}% CAGR)", style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor))
                            Text("₹${formatIndianCommas(report.benchmarkFutureValue, 0)}", style = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor))
                        }
                        LinearProgressIndicator(
                            progress = { benchFrac },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = primaryColor,
                            trackColor = outlineVariantColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandLegendItem(
    color: Color,
    title: String,
    subtitle: String? = null,
    pct: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
        Text(
            text = "${String.format(Locale.US, "%.1f", pct)}%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = color
            )
        )
    }
}

@Composable
private fun UnitBandsDonutChart(
    superPct: Float,
    onTrackPct: Float,
    moderatePct: Float,
    underperformingPct: Float,
    modifier: Modifier = Modifier
) {
    val superColor = UnitPowerBand.SUPER_POWER.color
    val onTrackColor = UnitPowerBand.ON_TRACK.color
    val moderateColor = UnitPowerBand.MODERATE_CATCHUP.color
    val underColor = UnitPowerBand.UNDERPERFORMING_DISCOUNT.color

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val total = (superPct + onTrackPct + moderatePct + underperformingPct).coerceAtLeast(0.001f)
        val sweepSuper = (superPct / total) * 360f
        val sweepOnTrack = (onTrackPct / total) * 360f
        val sweepModerate = (moderatePct / total) * 360f
        val sweepUnder = (underperformingPct / total) * 360f

        var startAngle = -90f
        val strokeWidth = size.minDimension * 0.28f
        val diameter = size.minDimension - strokeWidth
        val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

        if (sweepSuper > 0f) {
            drawArc(
                color = superColor,
                startAngle = startAngle,
                sweepAngle = sweepSuper,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            )
            startAngle += sweepSuper
        }
        if (sweepOnTrack > 0f) {
            drawArc(
                color = onTrackColor,
                startAngle = startAngle,
                sweepAngle = sweepOnTrack,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            )
            startAngle += sweepOnTrack
        }
        if (sweepModerate > 0f) {
            drawArc(
                color = moderateColor,
                startAngle = startAngle,
                sweepAngle = sweepModerate,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            )
            startAngle += sweepModerate
        }
        if (sweepUnder > 0f) {
            drawArc(
                color = underColor,
                startAngle = startAngle,
                sweepAngle = sweepUnder,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            )
        }
    }
}

@Composable
private fun AverageNavBandsDonutChart(
    belowPct: Float,
    atBelow100Pct: Float,
    atAbove100Pct: Float,
    abovePct: Float,
    modifier: Modifier = Modifier
) {
    val colBelow = Color(0xFF00E676)
    val colAt1 = Color(0xFF29B6F6)
    val colAt2 = Color(0xFF81D4FA) // Lighter blue for 100-105%
    val colAbove = Color(0xFFFF5252)

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val total = (belowPct + atBelow100Pct + atAbove100Pct + abovePct).coerceAtLeast(0.001f)
        val sweep1 = (belowPct / total) * 360f
        val sweep2 = (atBelow100Pct / total) * 360f
        val sweep3 = (atAbove100Pct / total) * 360f
        val sweep4 = (abovePct / total) * 360f

        var startAngle = -90f
        val strokeWidth = size.minDimension * 0.28f
        val diameter = size.minDimension - strokeWidth
        val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

        if (sweep1 > 0f) {
            drawArc(color = colBelow, startAngle = startAngle, sweepAngle = sweep1, useCenter = false, topLeft = topLeft, size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt))
            startAngle += sweep1
        }
        if (sweep2 > 0f) {
            drawArc(color = colAt1, startAngle = startAngle, sweepAngle = sweep2, useCenter = false, topLeft = topLeft, size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt))
            startAngle += sweep2
        }
        if (sweep3 > 0f) {
            drawArc(color = colAt2, startAngle = startAngle, sweepAngle = sweep3, useCenter = false, topLeft = topLeft, size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt))
            startAngle += sweep3
        }
        if (sweep4 > 0f) {
            drawArc(color = colAbove, startAngle = startAngle, sweepAngle = sweep4, useCenter = false, topLeft = topLeft, size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt))
        }
    }
}


