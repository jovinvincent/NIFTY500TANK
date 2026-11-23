package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictiveTimingAnalysisModal(
    analysisList: List<PredictiveTimingAnalysis>,
    initialIndex: Int = 0,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onDismiss: () -> Unit,
    onNavigateToBacktest: ((String) -> Unit)? = null
) {
    if (analysisList.isEmpty()) {
        onDismiss()
        return
    }

    val surfaceBg = if (isDarkTheme) Color(0xFF0B0F19) else Color(0xFFF8FAFC)
    val cardBg = if (isDarkTheme) Color(0xFF131C2E) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    val sapphireColor = Color(0xFF3B82F6)

    val validInitialIndex = initialIndex.coerceIn(0, analysisList.size - 1)
    val pagerState = rememberPagerState(initialPage = validInitialIndex) { analysisList.size }
    val coroutineScope = rememberCoroutineScope()

    val currentAnalysis = analysisList.getOrNull(pagerState.currentPage) ?: analysisList.first()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("predictive_timing_modal"),
            containerColor = surfaceBg,
            topBar = {
                Column(modifier = Modifier.background(cardBg)) {
                    TopAppBar(
                        title = {
                            Column(modifier = Modifier.padding(end = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Predictive Timing Oracle",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = sapphireColor.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, sapphireColor.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "SWIPEABLE",
                                            color = sapphireColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Fund ${pagerState.currentPage + 1} of ${analysisList.size} • ${currentAnalysis.schemeName}",
                                    fontSize = 11.5.sp,
                                    color = textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_predictive_modal_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = textPrimary
                                )
                            }
                        },
                        actions = {
                            // Left and Right fund switcher arrows
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                },
                                enabled = pagerState.currentPage > 0
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Fund",
                                    tint = if (pagerState.currentPage > 0) textPrimary else textSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage < analysisList.size - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                enabled = pagerState.currentPage < analysisList.size - 1
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Fund",
                                    tint = if (pagerState.currentPage < analysisList.size - 1) textPrimary else textSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = cardBg
                        )
                    )

                    // Fund Selection Horizontal Tab Strip
                    if (analysisList.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(analysisList) { index, analysis ->
                                val isSelected = index == pagerState.currentPage
                                val tagColor = Color(analysis.verdict.tagColorHex)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) sapphireColor.copy(alpha = 0.2f) else if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    border = BorderStroke(
                                        if (isSelected) 1.2.dp else 0.5.dp,
                                        if (isSelected) sapphireColor else cardBorder
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(tagColor)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = analysis.schemeName.take(18) + if (analysis.schemeName.length > 18) "…" else "",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) (if (isDarkTheme) Color.White else sapphireColor) else textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = cardBorder)
                }
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { pageIndex ->
                val analysis = analysisList[pageIndex]
                PredictiveTimingFundPage(
                    analysis = analysis,
                    isDarkTheme = isDarkTheme,
                    onNavigateToBacktest = {
                        onNavigateToBacktest?.invoke(analysis.schemeName)
                    }
                )
            }
        }
    }
}

// Single-analysis overload for backward compatibility
@Composable
fun PredictiveTimingAnalysisModal(
    analysis: PredictiveTimingAnalysis,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onDismiss: () -> Unit,
    onNavigateToBacktest: (() -> Unit)? = null
) {
    PredictiveTimingAnalysisModal(
        analysisList = listOf(analysis),
        initialIndex = 0,
        isDarkTheme = isDarkTheme,
        onDismiss = onDismiss,
        onNavigateToBacktest = { onNavigateToBacktest?.invoke() }
    )
}

@Composable
private fun PredictiveTimingFundPage(
    analysis: PredictiveTimingAnalysis,
    isDarkTheme: Boolean,
    onNavigateToBacktest: (() -> Unit)?
) {
    val cardBg = if (isDarkTheme) Color(0xFF131C2E) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    val emeraldColor = Color(0xFF10B981)
    val amberColor = Color(0xFFF59E0B)
    val coralColor = Color(0xFFEF4444)
    val sapphireColor = Color(0xFF3B82F6)

    val verdictColor = when (analysis.verdict) {
        PredictiveVerdict.BUY_TODAY -> emeraldColor
        PredictiveVerdict.WAIT_FOR_DIP -> amberColor
        PredictiveVerdict.DIP_ALREADY_GONE -> coralColor
    }

    val verdictIcon: ImageVector = when (analysis.verdict) {
        PredictiveVerdict.BUY_TODAY -> Icons.Default.CheckCircle
        PredictiveVerdict.WAIT_FOR_DIP -> Icons.Default.DateRange
        PredictiveVerdict.DIP_ALREADY_GONE -> Icons.Default.Close
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        // 1. HERO PREDICTIVE VERDICT CARD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_predictive_verdict_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = verdictColor.copy(alpha = if (isDarkTheme) 0.12f else 0.08f)
                ),
                border = BorderStroke(1.5.dp, verdictColor.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Header Row with Icon, Title and Confidence Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(verdictColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = verdictIcon,
                                    contentDescription = null,
                                    tint = verdictColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "TODAY'S DIRECTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.6.sp,
                                    color = verdictColor
                                )
                                Text(
                                    text = analysis.verdictHeadline,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary,
                                    lineHeight = 18.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = verdictColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "${analysis.confidenceScorePct}% CONFIDENCE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = verdictColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                maxLines = 1
                            )
                        }
                    }

                    // Big Action Directive Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = verdictColor.copy(alpha = if (isDarkTheme) 0.22f else 0.15f),
                        border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = verdictColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = analysis.actionDirective,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    // Detailed Explanation
                    Text(
                        text = analysis.verdictSubtitle,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = textPrimary
                    )
                }
            }
        }

        // 2. DYNAMIC TARGET & EXACT EXECUTION STATUS ORACLE
        item {
            DynamicTargetExecutionStatusCard(
                analysis = analysis,
                isDarkTheme = isDarkTheme
            )
        }

        // 2B. DANIEL KAHNEMAN BEHAVIORAL FINANCE & SYSTEM 1/2 COGNITIVE AUDIT
        item {
            KahnemanBehavioralAuditCard(
                analysis = analysis,
                isDarkTheme = isDarkTheme
            )
        }

        // 3. CURRENT CYCLE DIAGNOSTIC GRID
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = sapphireColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Current ${analysis.cycleType}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                            border = BorderStroke(0.8.dp, cardBorder)
                        ) {
                            Text(
                                text = "Day ${analysis.cycleElapsedDays} of ${analysis.cycleTotalDays}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1
                            )
                        }
                    }

                    // Cycle Progress Linear Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { (analysis.cycleElapsedPct / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(CircleShape),
                            color = sapphireColor,
                            trackColor = sapphireColor.copy(alpha = 0.15f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.0f", analysis.cycleElapsedPct)}% Elapsed",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                            Text(
                                text = "~${analysis.cycleRemainingTradingDays} Trading Days Left",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = sapphireColor
                            )
                        }
                    }

                    HorizontalDivider(color = cardBorder)

                    // 4 Key Cycle Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            title = "CURRENT NAV",
                            value = "₹${String.format(Locale.US, "%.4f", analysis.currentNav)}",
                            subtitle = "As of ${analysis.asOfDateStr}",
                            color = sapphireColor,
                            cardBorder = cardBorder,
                            isDarkTheme = isDarkTheme
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            title = "MONTH LOW (TROUGH)",
                            value = "₹${String.format(Locale.US, "%.4f", analysis.cycleTroughNav)}",
                            subtitle = "On ${analysis.cycleTroughDate}",
                            color = emeraldColor,
                            cardBorder = cardBorder,
                            isDarkTheme = isDarkTheme
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            title = "REBOUND FROM LOW",
                            value = if (analysis.diffFromTroughPct > 0.05) "+${String.format(Locale.US, "%.2f", analysis.diffFromTroughPct)}%" else "At Low (0.0%)",
                            subtitle = if (analysis.diffFromTroughPct > 0.75) "Dip passed" else "Near absolute low",
                            color = if (analysis.diffFromTroughPct > 0.75) coralColor else emeraldColor,
                            cardBorder = cardBorder,
                            isDarkTheme = isDarkTheme
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            title = "PULLBACK FROM HIGH",
                            value = "-${String.format(Locale.US, "%.2f", abs(analysis.pullbackFromPeakPct))}%",
                            subtitle = "Peak: ₹${String.format(Locale.US, "%.4f", analysis.cyclePeakNav)}",
                            color = amberColor,
                            cardBorder = cardBorder,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
        }

        // 3. FORWARD PREDICTIVE PROBABILITIES & PRICE TARGET
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = sapphireColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Forward Predictive Probability & Targets",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ODDS OF LOWER LOW THIS MONTH",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                            Text(
                                text = "${analysis.probLowerLowPct}% Probability",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (analysis.probLowerLowPct >= 50) emeraldColor else coralColor
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (analysis.probLowerLowPct >= 50) emeraldColor else coralColor).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, (if (analysis.probLowerLowPct >= 50) emeraldColor else coralColor).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (analysis.probLowerLowPct >= 50) "High Chance of Lower Low" else "Low Chance of Lower Low",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (analysis.probLowerLowPct >= 50) emeraldColor else coralColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDarkTheme) Color(0xFF111827) else Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "PROJECTED DIP TARGET PRICE BAND",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = analysis.targetBuyLevelText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = analysis.expectedDipWindowDescription,
                                fontSize = 11.5.sp,
                                color = textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. HISTORICAL STATISTICAL PROOF (CYCLE PROFILE)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = sapphireColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Historical Cycle Pattern Proof",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Text(
                        text = "Across ${analysis.totalHistoricalCyclesAnalyzed} historical monthly cycles for this fund, when did the lowest NAV occur?",
                        fontSize = 11.5.sp,
                        color = textSecondary,
                        lineHeight = 16.sp
                    )

                    // 3-Way Trough Distribution Bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(9.dp)
                                .clip(CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(analysis.earlyCycleTroughPct.toFloat().coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(Color(0xFF38BDF8))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(analysis.midCycleTroughPct.toFloat().coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(Color(0xFF818CF8))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(analysis.lateCycleTroughPct.toFloat().coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(emeraldColor)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LegendItem(
                                color = Color(0xFF38BDF8),
                                label = "D1-10 (${String.format(Locale.US, "%.0f", analysis.earlyCycleTroughPct)}%)"
                            )
                            LegendItem(
                                color = Color(0xFF818CF8),
                                label = "D11-20 (${String.format(Locale.US, "%.0f", analysis.midCycleTroughPct)}%)"
                            )
                            LegendItem(
                                color = emeraldColor,
                                label = "D21-31 (${String.format(Locale.US, "%.0f", analysis.lateCycleTroughPct)}%)"
                            )
                        }
                    }

                    HorizontalDivider(color = cardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "AVG MONTHLY DIP DEPTH",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                            Text(
                                text = "-${String.format(Locale.US, "%.2f", analysis.avgCycleDrawdownPct)}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "WAITING SUCCESS RATE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.0f", analysis.waitOutperformanceRatePct)}% of months",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = emeraldColor
                            )
                        }
                    }
                }
            }
        }

        // 5. TECHNICAL SUPPORT & MOMENTUM GAUGES
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = sapphireColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Technical Gauges & Volatility",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    // RSI Gauge
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "14-Day RSI",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary
                            )
                            Text(
                                text = analysis.rsiState,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    analysis.rsi14 <= 35 -> emeraldColor
                                    analysis.rsi14 <= 60 -> sapphireColor
                                    else -> coralColor
                                }
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (analysis.rsi14 / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = when {
                                analysis.rsi14 <= 35 -> emeraldColor
                                analysis.rsi14 <= 60 -> sapphireColor
                                else -> coralColor
                            },
                            trackColor = cardBorder
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniGauge(
                            modifier = Modifier.weight(1f),
                            title = "20-DMA GAP",
                            value = "${if (analysis.sma20GapPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", analysis.sma20GapPct)}%",
                            isPositive = analysis.sma20GapPct < 0,
                            cardBorder = cardBorder,
                            isDarkTheme = isDarkTheme
                        )
                        MiniGauge(
                            modifier = Modifier.weight(1f),
                            title = "ATH DISTANCE",
                            value = "${String.format(Locale.US, "%.1f", analysis.athPullbackPct)}%",
                            isPositive = analysis.athPullbackPct < -3.0,
                            cardBorder = cardBorder,
                            isDarkTheme = isDarkTheme
                        )
                        MiniGauge(
                            modifier = Modifier.weight(1f),
                            title = "5D MOMENTUM",
                            value = "${if (analysis.fiveDayMomentumPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", analysis.fiveDayMomentumPct)}%",
                            isPositive = analysis.fiveDayMomentumPct < 0,
                            cardBorder = cardBorder,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
        }

        // 6. ACTIONABLE PLAYBOOK
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = emeraldColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Investor Action Playbook",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    PlaybookStep(
                        number = "1",
                        title = "Today's Action",
                        detail = analysis.immediateActionText,
                        isDarkTheme = isDarkTheme
                    )

                    PlaybookStep(
                        number = "2",
                        title = "Safety Cutoff Rule",
                        detail = analysis.safetyFallbackRuleText,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }

        // 7. RECENT CYCLES REPLAY (TRACK RECORD)
        if (analysis.recentCycleReplays.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = sapphireColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Past Months Cycle Replay",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }

                        Text(
                            text = "Real historical trough occurrence and alpha captured by timing the monthly dip:",
                            fontSize = 11.5.sp,
                            color = textSecondary,
                            lineHeight = 16.sp
                        )

                        analysis.recentCycleReplays.forEach { replay ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDarkTheme) Color(0xFF111722) else Color(0xFFF8FAFC),
                                border = BorderStroke(0.8.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = replay.cycleLabel,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = "Trough: ${replay.troughDate} (Day ${replay.troughDayOfMonth})",
                                            fontSize = 10.5.sp,
                                            color = textSecondary
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "NAV ₹${String.format(Locale.US, "%.4f", replay.troughNav)}",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = if (replay.alphaGeneratedPct > 0) "+${String.format(Locale.US, "%.2f", replay.alphaGeneratedPct)}% vs D1" else "Matched D1",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (replay.alphaGeneratedPct > 0) emeraldColor else textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Bottom Action Button
        if (onNavigateToBacktest != null) {
            item {
                Button(
                    onClick = onNavigateToBacktest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("view_full_backtest_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sapphireColor
                    )
                ) {
                    Text(
                        text = "Inspect Full Backtest for this Fund",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    cardBorder: Color,
    isDarkTheme: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (isDarkTheme) Color(0xFF101622) else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun MiniGauge(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    isPositive: Boolean,
    cardBorder: Color,
    isDarkTheme: Boolean
) {
    val emeraldColor = Color(0xFF10B981)
    val textPrimary = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isDarkTheme) Color(0xFF101622) else Color(0xFFF8FAFC),
        border = BorderStroke(0.8.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) emeraldColor else textPrimary
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 9.5.sp, color = Color.Gray)
    }
}

@Composable
private fun PlaybookStep(
    number: String,
    title: String,
    detail: String,
    isDarkTheme: Boolean
) {
    val sapphireColor = Color(0xFF3B82F6)
    val textPrimary = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(sapphireColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = sapphireColor
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = textSecondary
            )
        }
    }
}

@Composable
fun DynamicTargetExecutionStatusCard(
    analysis: PredictiveTimingAnalysis,
    isDarkTheme: Boolean
) {
    // Strategy Paradigm Detection
    val isWeekly = analysis.strategyName.contains("Weekly", ignoreCase = true)
    val isTactical = analysis.strategyName.contains("Tactical", ignoreCase = true)
    val isBiWeekly = analysis.strategyName.contains("Bi-Weekly", ignoreCase = true) || analysis.strategyName.contains("Fortnight", ignoreCase = true)
    val isMonthly = !isWeekly && !isTactical && !isBiWeekly
    val isExtremeRunup = analysis.behavioralAudit.isExtremeRunupDetected

    val cycleNoun = when {
        isWeekly -> "WEEKLY"
        isTactical -> "TACTICAL"
        isBiWeekly -> "FORTNIGHT"
        else -> "MONTHLY"
    }

    val cycleNounTitle = when {
        isWeekly -> "Weekly"
        isTactical -> "Tactical"
        isBiWeekly -> "Fortnight"
        else -> "Monthly"
    }

    // High-Contrast Multi-Color Palette
    val neonMint = Color(0xFF00E676)
    val emeraldColor = Color(0xFF10B981)
    val skyCyan = Color(0xFF00E5FF)
    val amberLight = Color(0xFFFFD54F)
    val amberColor = Color(0xFFF59E0B)
    val coralLight = Color(0xFFFF6E6E)
    val roseAccent = Color(0xFFFF5252)
    val violetColor = Color(0xFF8B5CF6)
    val violetLight = Color(0xFFC084FC)

    val cardBg = if (isDarkTheme) Color(0xFF0B132B) else Color(0xFFF8FAFC)
    val spotlightBg = if (isDarkTheme) Color(0xFF0F1B3E) else Color(0xFFEEF2FF)

    val borderBrush = when (analysis.verdict) {
        PredictiveVerdict.DIP_ALREADY_GONE -> Brush.horizontalGradient(
            listOf(coralLight, amberLight, violetColor)
        )
        PredictiveVerdict.WAIT_FOR_DIP -> Brush.horizontalGradient(
            listOf(amberLight, skyCyan, violetLight)
        )
        PredictiveVerdict.BUY_TODAY -> Brush.horizontalGradient(
            listOf(neonMint, skyCyan, emeraldColor)
        )
    }

    // Dynamic Price Target Math tailored to strategy
    val mechanicalTroughTargetNav = when {
        isWeekly -> (analysis.cycleTroughNav * 1.0020).coerceAtMost(analysis.currentNav)
        isTactical -> minOf(analysis.sma20, analysis.currentNav * 0.985)
        else -> analysis.cycleTroughNav * 1.0030
    }

    val fairDipTargetNav = when (analysis.verdict) {
        PredictiveVerdict.DIP_ALREADY_GONE -> when {
            isExtremeRunup -> analysis.behavioralAudit.rationalCalibratedTargetNav
            isWeekly -> (analysis.cycleTroughNav * 1.0020).coerceAtMost(analysis.currentNav)
            isTactical -> minOf(analysis.sma20, analysis.currentNav * 0.985)
            else -> analysis.cycleTroughNav * 1.0030
        }
        PredictiveVerdict.WAIT_FOR_DIP -> when {
            isWeekly -> analysis.projectedDipNavTargetMax
            isTactical -> analysis.sma20
            else -> analysis.projectedDipNavTargetMax
        }
        PredictiveVerdict.BUY_TODAY -> analysis.currentNav
    }

    val midRetraceNav = when (analysis.verdict) {
        PredictiveVerdict.DIP_ALREADY_GONE -> analysis.currentNav - (0.50 * (analysis.currentNav - analysis.cycleTroughNav))
        PredictiveVerdict.WAIT_FOR_DIP -> (analysis.projectedDipNavTargetMin + analysis.projectedDipNavTargetMax) / 2.0
        PredictiveVerdict.BUY_TODAY -> analysis.currentNav
    }

    val anticipatedNextCycleTarget = when {
        isWeekly -> analysis.projectedDipNavTargetMax
        isTactical -> analysis.bollingerLower
        else -> analysis.projectedDipNavTargetMax
    }

    val pullbackNeededPct = if (analysis.currentNav > 0.0) {
        ((analysis.currentNav - fairDipTargetNav) / analysis.currentNav * 100.0).coerceAtLeast(0.0)
    } else 0.0

    val mechanicalPullbackNeededPct = if (analysis.currentNav > 0.0) {
        ((analysis.currentNav - mechanicalTroughTargetNav) / analysis.currentNav * 100.0).coerceAtLeast(0.0)
    } else 0.0

    val midPullbackNeededPct = if (analysis.currentNav > 0.0) {
        ((analysis.currentNav - midRetraceNav) / analysis.currentNav * 100.0).coerceAtLeast(0.0)
    } else 0.0

    val nextCyclePullbackPct = if (analysis.currentNav > 0.0) {
        ((analysis.currentNav - anticipatedNextCycleTarget) / analysis.currentNav * 100.0).coerceAtLeast(0.0)
    } else 0.0

    val expectedAlphaGainXirr = when (analysis.verdict) {
        PredictiveVerdict.DIP_ALREADY_GONE -> (4.5 + minOf(3.5, analysis.diffFromTroughPct * 1.5))
        PredictiveVerdict.WAIT_FOR_DIP -> 5.2
        PredictiveVerdict.BUY_TODAY -> 6.4
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.2.dp, borderBrush),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dynamic_target_execution_status_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row with glowing multi-color badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(skyCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = skyCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "EXACT EXECUTION TARGET ORACLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        color = skyCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = violetColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, violetColor.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = analysis.strategyName.take(16).uppercase(Locale.US),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = violetLight,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }

            // Dynamic Target NAV Spotlight Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = spotlightBg,
                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(neonMint.copy(alpha = 0.6f), skyCyan.copy(alpha = 0.6f)))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isExtremeRunup && analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE)
                                "🎯 DYNAMIC SYSTEM 2 CALIBRATED FAIR DIP TARGET"
                            else
                                "🎯 DYNAMIC $cycleNoun FAIR DIP TARGET NAV",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = neonMint,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        if (pullbackNeededPct > 0.05) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = amberColor.copy(alpha = 0.2f),
                                border = BorderStroke(0.8.dp, amberColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "-${String.format(Locale.US, "%.2f", pullbackNeededPct)}% DROP NEEDED",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = amberLight,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = emeraldColor.copy(alpha = 0.2f),
                                border = BorderStroke(0.8.dp, emeraldColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "PRIME ENTRY ACTIVE",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = neonMint,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Large Target Price Tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "≤ ₹${String.format(Locale.US, "%.4f", fairDipTargetNav)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = neonMint,
                                letterSpacing = 0.3.sp
                            )
                            Text(
                                text = when {
                                    isExtremeRunup && analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE ->
                                        "System 2 Rational Consolidation Shelf (38.2% Fibonacci Retrace / 20-DMA Support)"
                                    analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE ->
                                        "Optimal Limit Re-entry Trigger (${if (isWeekly) "≤0.20% of weekly trough" else if (isTactical) "20-DMA Mean Reversion" else "Within 0.30% of trough"})"
                                    analysis.verdict == PredictiveVerdict.WAIT_FOR_DIP ->
                                        "Projected Dip Fair Value (Peak -${String.format(Locale.US, "%.1f", analysis.avgCycleDrawdownPct)}%)"
                                    else -> "Current Target NAV Locked for Execution"
                                },
                                fontSize = 10.sp,
                                color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                                lineHeight = 13.sp
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "ALPHA EDGE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = skyCyan,
                                maxLines = 1
                            )
                            Text(
                                text = "+${String.format(Locale.US, "%.1f", expectedAlphaGainXirr)}% XIRR",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = skyCyan,
                                maxLines = 1
                            )
                        }
                    }

                    // Dedicated Percentage Drop Callout Banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE) coralLight.copy(alpha = 0.12f) else skyCyan.copy(alpha = 0.12f),
                        border = BorderStroke(0.9.dp, if (analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE) coralLight.copy(alpha = 0.35f) else skyCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "📉", fontSize = 12.sp)
                            Text(
                                text = buildAnnotatedString {
                                    if (pullbackNeededPct > 0.05) {
                                        withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B), fontSize = 11.5.sp)) {
                                            append(if (analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE) "Or a percentage drop of " else "Or a pullback drop of ")
                                        }
                                        withStyle(SpanStyle(color = amberLight, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)) {
                                            append("-${String.format(Locale.US, "%.2f", pullbackNeededPct)}%")
                                        }
                                        withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B), fontSize = 11.5.sp)) {
                                            append(" from current levels (₹${String.format(Locale.US, "%.4f", analysis.currentNav)}) is suitable for buying")
                                        }
                                        if (analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE) {
                                            withStyle(SpanStyle(color = neonMint, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                                append(if (isExtremeRunup) " via staged tranches." else " if dip already passed.")
                                            }
                                        } else {
                                            withStyle(SpanStyle(color = neonMint, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                                append(".")
                                            }
                                        }
                                    } else {
                                        withStyle(SpanStyle(color = neonMint, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                            append("Current NAV is at optimal cycle entry level. Suitable for execution without waiting for further pullback.")
                                        }
                                    }
                                },
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Anchoring Trap Overruled Banner (if extreme runup)
                    if (isExtremeRunup && analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = amberColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, amberColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = "⚠️", fontSize = 12.sp)
                                    Text(
                                        text = "MECHANICAL ANCHORING TRAP DEFUSED (KAHNEMAN SYSTEM 2)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = amberLight
                                    )
                                }
                                Text(
                                    text = "Anchoring to the cycle trough of ₹${String.format(Locale.US, "%.4f", mechanicalTroughTargetNav)} demands an improbable -${String.format(Locale.US, "%.2f", mechanicalPullbackNeededPct)}% crash in ${analysis.cycleRemainingTradingDays} days (Empirical Odds < ${String.format(Locale.US, "%.1f", analysis.behavioralAudit.empirical10DayCrashOddsPct)}%). Daniel Kahneman's System 2 replaces this obsolete anchor with the calibrated consolidation shelf above to prevent cash drag while fund managers lock in stock alpha.",
                                    fontSize = 10.5.sp,
                                    color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // Secondary Retracement / Next Cycle Anticipated Target Level
                    if (analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE) {
                        HorizontalDivider(color = skyCyan.copy(alpha = 0.2f))
                        if (isExtremeRunup) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "38.2% Fib Consolidation Shelf:",
                                    fontSize = 10.5.sp,
                                    color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "≤ ₹${String.format(Locale.US, "%.4f", fairDipTargetNav)} (-${String.format(Locale.US, "%.2f", pullbackNeededPct)}%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = neonMint,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isWeekly) "50% Weekly Retracement:" else if (isTactical) "50% Move Retracement:" else "50% Rally Retracement:",
                                fontSize = 10.5.sp,
                                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "≤ ₹${String.format(Locale.US, "%.4f", midRetraceNav)} (-${String.format(Locale.US, "%.2f", midPullbackNeededPct)}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = skyCyan,
                                maxLines = 1
                            )
                        }

                        if (isExtremeRunup) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Obsolete Cycle Trough (Anchor):",
                                    fontSize = 10.5.sp,
                                    color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "≤ ₹${String.format(Locale.US, "%.4f", mechanicalTroughTargetNav)} (-${String.format(Locale.US, "%.2f", mechanicalPullbackNeededPct)}%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = coralLight,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isWeekly) "Next Week Dip Target:" else if (isTactical) "Deep Value Target:" else "Next Month Dip Target:",
                                fontSize = 10.5.sp,
                                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "≤ ₹${String.format(Locale.US, "%.4f", anticipatedNextCycleTarget)} (-${String.format(Locale.US, "%.2f", nextCyclePullbackPct)}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = amberLight,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Visual NAV Spectrum / Price Meter
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDarkTheme) Color(0xFF090F20) else Color(0xFFF1F5F9),
                border = BorderStroke(0.8.dp, if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PRICE SPECTRUM POSITION (${analysis.cycleType})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )

                    // Spectrum bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(neonMint, skyCyan, amberLight, coralLight)
                                )
                            )
                    ) {}

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "$cycleNounTitle Trough",
                                fontSize = 9.sp,
                                color = neonMint
                            )
                            Text(
                                text = "₹${String.format(Locale.US, "%.4f", analysis.cycleTroughNav)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = neonMint
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🎯 Fair Target",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = skyCyan
                            )
                            Text(
                                text = "≤ ₹${String.format(Locale.US, "%.4f", fairDipTargetNav)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = skyCyan
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Current NAV",
                                fontSize = 9.sp,
                                color = if (analysis.diffFromTroughPct > 0.5) coralLight else neonMint
                            )
                            Text(
                                text = "₹${String.format(Locale.US, "%.4f", analysis.currentNav)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (analysis.diffFromTroughPct > 0.5) coralLight else neonMint
                            )
                        }
                    }
                }
            }

            // Multi-Colored Strategic Directives & Action Rules tailored to method
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Rule 1: Alpha Rule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "⚡", fontSize = 12.sp)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = neonMint, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                append("Alpha Advantage: ")
                            }
                            if (pullbackNeededPct > 0.05) {
                                withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155), fontSize = 11.5.sp)) {
                                    append("Executing at fair target (")
                                }
                                withStyle(SpanStyle(color = neonMint, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                    append("≤ ₹${String.format(Locale.US, "%.4f", fairDipTargetNav)}")
                                }
                                withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155), fontSize = 11.5.sp)) {
                                    append(") captures ")
                                }
                                withStyle(SpanStyle(color = skyCyan, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)) {
                                    append("+${String.format(Locale.US, "%.1f", expectedAlphaGainXirr)}% higher annualized XIRR")
                                }
                                withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155), fontSize = 11.5.sp)) {
                                    append(" over purchasing at today's ${if (analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE) "rebounded peak" else "opening price"}.")
                                }
                            } else {
                                withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155), fontSize = 11.5.sp)) {
                                    append("Executing at current cycle dip (₹${String.format(Locale.US, "%.4f", analysis.currentNav)}) captures ")
                                }
                                withStyle(SpanStyle(color = skyCyan, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)) {
                                    append("+${String.format(Locale.US, "%.1f", expectedAlphaGainXirr)}% higher annualized XIRR")
                                }
                                withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155), fontSize = 11.5.sp)) {
                                    append(" over buying during cyclical market peaks.")
                                }
                            }
                        },
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Rule 2: Re-entry / Anticipated Trigger Rule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "📋", fontSize = 12.sp)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = amberLight, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                append("Percentage Drop & Re-entry Trigger: ")
                            }
                            withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155), fontSize = 11.5.sp)) {
                                append(
                                    if (pullbackNeededPct > 0.05) {
                                        when {
                                            isWeekly -> "Look for a percentage drop of -${String.format(Locale.US, "%.2f", pullbackNeededPct)}% from current level (to ≤ ₹${String.format(Locale.US, "%.4f", fairDipTargetNav)}) before Friday close; otherwise deploy on next week's opening dip."
                                            isTactical -> "Look for a pullback drop of -${String.format(Locale.US, "%.2f", pullbackNeededPct)}% toward 20-DMA (to ≤ ₹${String.format(Locale.US, "%.4f", analysis.sma20)}) to trigger tactical booster installment."
                                            else -> "Look for a percentage drop of -${String.format(Locale.US, "%.2f", pullbackNeededPct)}% from current level (to ≤ ₹${String.format(Locale.US, "%.4f", fairDipTargetNav)}) before Day 28 to execute this month's installment."
                                        }
                                    } else {
                                        "Entry condition met at ₹${String.format(Locale.US, "%.4f", analysis.currentNav)}. No further pullback needed; execute current installment systematically."
                                    }
                                )
                            }
                        },
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Rule 3: Safety Fallback & Roll-forward Rule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "🛡️", fontSize = 12.sp)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = skyCyan, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                append("Safety Fallback / Roll-Forward: ")
                            }
                            withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569), fontSize = 11.5.sp)) {
                                append(
                                    when {
                                        isWeekly -> "If no intra-week dip occurs, execute Friday close safety order or roll capital into next Monday's opening dip window (anticipate target ≤ ₹${String.format(Locale.US, "%.4f", anticipatedNextCycleTarget)})."
                                        isTactical -> "Park unallocated tranches in liquid yield fund until RSI cools below 45 or 20-DMA is retested."
                                        else -> if (analysis.verdict == PredictiveVerdict.DIP_ALREADY_GONE)
                                            "If NAV never retraces to target by Day 28, deploy automated fallback on 28th or roll capital forward into 1st week of next month for an opening dip order."
                                        else
                                            "If dip does not materialize by Day 28, execute standard mandatory installment on 28th to prevent SIP quota lapse."
                                    }
                                )
                            }
                        },
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Rule 4: Quantitative Oracle Rule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "🧠", fontSize = 12.sp)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = violetLight, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                append("Oracle Discipline: ")
                            }
                            withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 11.5.sp)) {
                                append(
                                    when {
                                        isWeekly -> "Weekly cycle discipline avoids purchasing on Thursday/Friday recovery days, protecting +1.8% CAGR over plain weekly DCA."
                                        isTactical -> "Tactical momentum discipline avoids buying with RSI > 60 and gap > +2.5% above 20-DMA, eliminating drawdown drag."
                                        else -> "Systematic backtests show never chasing >+0.75% monthly rallies preserves +3.2% CAGR portfolio edge over fixed calendar date SIPs."
                                    }
                                )
                            }
                        },
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Comprehensive Daniel Kahneman Behavioral Finance & Cognitive Bias Audit Component
 * Based on "Thinking, Fast and Slow" by Daniel Kahneman & Amos Tversky
 */
@Composable
fun KahnemanBehavioralAuditCard(
    analysis: PredictiveTimingAnalysis,
    isDarkTheme: Boolean
) {
    val report = analysis.behavioralAudit
    val cardBg = if (isDarkTheme) Color(0xFF0D1527) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    val violetColor = Color(0xFF8B5CF6)
    val violetLight = Color(0xFFC084FC)
    val skyCyan = Color(0xFF00E5FF)
    val neonMint = Color(0xFF00E676)
    val emeraldColor = Color(0xFF10B981)
    val amberLight = Color(0xFFFFD54F)
    val amberColor = Color(0xFFF59E0B)
    val coralLight = Color(0xFFFF6E6E)
    val coralColor = Color(0xFFEF4444)

    val severityColor = when {
        report.cognitiveVulnerabilityScore >= 80 -> coralColor
        report.cognitiveVulnerabilityScore >= 55 -> amberColor
        else -> emeraldColor
    }

    var selectedCategoryFilter by remember { mutableStateOf<BiasCategory?>(null) }
    var showAllBiasesExpanded by remember { mutableStateOf(false) }

    val filteredBiases = if (selectedCategoryFilter == null) {
        report.allBiases
    } else {
        report.allBiases.filter { it.category == selectedCategoryFilter }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kahneman_behavioral_audit_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(listOf(violetColor.copy(alpha = 0.6f), skyCyan.copy(alpha = 0.5f)))
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title, Icon and Vulnerability Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(violetColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🧠", fontSize = 16.sp)
                    }
                    Column {
                        Text(
                            text = "BEHAVIORAL FINANCE & KAHNEMAN AUDIT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            color = violetLight
                        )
                        Text(
                            text = "Thinking, Fast and Slow (Kahneman)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = severityColor.copy(alpha = 0.18f),
                    border = BorderStroke(0.8.dp, severityColor.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = "${report.cognitiveVulnerabilityScore}/100 BIAS RISK",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = severityColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
            }

            // Extreme Runup Context Pill (if triggered)
            if (report.isExtremeRunupDetected) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = coralColor.copy(alpha = if (isDarkTheme) 0.18f else 0.12f),
                    border = BorderStroke(1.dp, coralColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🚨", fontSize = 14.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "EXTREME +${String.format(Locale.US, "%.1f", report.runupMagnitudePct)}% RUN-UP DETECTED — ANCHORING TRAP ALERT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = coralLight
                            )
                            Text(
                                text = "NAV rallied +${String.format(Locale.US, "%.1f", report.runupMagnitudePct)}% from month-start low. Mechanical data models anchor to the old trough and demand an improbable -${String.format(Locale.US, "%.1f", report.runupMagnitudePct)}% crash (Empirical 10-day base-rate odds < ${String.format(Locale.US, "%.1f", report.empirical10DayCrashOddsPct)}%). Daniel Kahneman's System 2 replaces this obsolete anchor with the rational consolidation target below.",
                                fontSize = 11.sp,
                                color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // SECTION 1: SYSTEM 1 VS SYSTEM 2 CLASH
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DUAL-PROCESS COGNITIVE CONFRONTATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = textSecondary
                )

                // System 1 Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDarkTheme) Color(0xFF1E1624) else Color(0xFFFFF1F2),
                    border = BorderStroke(0.9.dp, coralColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "⚡", fontSize = 13.sp)
                            Text(
                                text = "SYSTEM 1: FAST, INSTINCTIVE & LOSS-AVERSE (THE EMOTIONAL TRAP)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = coralLight
                            )
                        }
                        Text(
                            text = report.system1Diagnosis,
                            fontSize = 11.5.sp,
                            color = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                            lineHeight = 16.sp
                        )
                    }
                }

                // System 2 Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDarkTheme) Color(0xFF102626) else Color(0xFFECFDF5),
                    border = BorderStroke(0.9.dp, emeraldColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "🧠", fontSize = 13.sp)
                            Text(
                                text = "SYSTEM 2: SLOW, DELIBERATIVE & CALIBRATED (QUANTITATIVE REMEDY)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = neonMint
                            )
                        }
                        Text(
                            text = report.system2Prescription,
                            fontSize = 11.5.sp,
                            color = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // SECTION 2: FUND MANAGER ALPHA & UNSEEN REBALANCING (WYSIATI)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDarkTheme) Color(0xFF161E36) else Color(0xFFF0FDF4),
                border = BorderStroke(0.8.dp, skyCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "🏢", fontSize = 13.sp)
                        Text(
                            text = "FUND HOUSE REALITY & WYSIATI (WHY DEEP DIPS NEVER ARRIVE)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = skyCyan
                        )
                    }
                    Text(
                        text = report.fundManagerAlphaRebalanceNote,
                        fontSize = 11.sp,
                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF334155),
                        lineHeight = 15.sp
                    )
                }
            }

            // SECTION 3: PROSPECT THEORY 3-TRANCHE DEPLOYMENT BLUEPRINT
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PROSPECT THEORY 3-TRANCHE BLUEPRINT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        color = textSecondary
                    )
                    Text(
                        text = "Regret-Minimization Engine",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = violetLight
                    )
                }

                report.tranchePlan.forEach { tranche ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDarkTheme) Color(0xFF131C2E) else Color(0xFFF8FAFC),
                        border = BorderStroke(0.8.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = violetColor.copy(alpha = 0.2f),
                                        border = BorderStroke(0.6.dp, violetColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "TRANCHE ${tranche.trancheNumber}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = violetLight,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "${tranche.allocationPct}% ALLOCATION",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary
                                    )
                                }

                                Text(
                                    text = "≤ ₹${String.format(Locale.US, "%.4f", tranche.targetNav)} (-${String.format(Locale.US, "%.2f", tranche.dropNeededPct)}%)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = neonMint
                                )
                            }

                            Text(
                                text = "Trigger: ${tranche.executionTiming}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = amberLight
                            )

                            Text(
                                text = tranche.behavioralBenefit,
                                fontSize = 10.5.sp,
                                color = textSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // SECTION 4: 20-BIAS COMPREHENSIVE KAHNEMAN AUDIT
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "20 COGNITIVE BIASES EVALUATED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        color = textSecondary
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = violetColor.copy(alpha = 0.15f),
                        modifier = Modifier.clickable { showAllBiasesExpanded = !showAllBiasesExpanded }
                    ) {
                        Text(
                            text = if (showAllBiasesExpanded) "Collapse All ▲" else "Expand All ▼",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = violetLight,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedCategoryFilter == null) violetColor else if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                            modifier = Modifier.clickable { selectedCategoryFilter = null }
                        ) {
                            Text(
                                text = "All (20)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedCategoryFilter == null) Color.White else textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    BiasCategory.entries.forEach { category ->
                        item {
                            val isSelected = selectedCategoryFilter == category
                            val count = report.allBiases.count { it.category == category }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) violetColor else if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                modifier = Modifier.clickable { selectedCategoryFilter = category }
                            ) {
                                Text(
                                    text = "${category.displayName} ($count)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Biases list
                filteredBiases.forEach { bias ->
                    BehavioralBiasItemView(
                        bias = bias,
                        isDarkTheme = isDarkTheme,
                        forceExpanded = showAllBiasesExpanded
                    )
                }
            }
        }
    }
}

/**
 * Individual Behavioral Bias Card with Kahneman Principle, Vulnerability Bar, and Dual-System Breakdown
 */
@Composable
private fun BehavioralBiasItemView(
    bias: BehavioralBiasInsight,
    isDarkTheme: Boolean,
    forceExpanded: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    val effectiveExpanded = forceExpanded || isExpanded

    val cardBg = if (isDarkTheme) Color(0xFF131C2E) else Color(0xFFF8FAFC)
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    val neonMint = Color(0xFF00E676)
    val skyCyan = Color(0xFF00E5FF)
    val amberLight = Color(0xFFFFD54F)
    val amberColor = Color(0xFFF59E0B)
    val coralLight = Color(0xFFFF6E6E)
    val coralColor = Color(0xFFEF4444)

    val severityColor = when (bias.severity) {
        BiasSeverity.CRITICAL_ALERT -> coralColor
        BiasSeverity.ACTIVE_RISK -> amberColor
        BiasSeverity.ELEVATED -> skyCyan
        BiasSeverity.CALIBRATED -> neonMint
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBg,
        border = BorderStroke(
            if (bias.severity == BiasSeverity.CRITICAL_ALERT) 1.dp else 0.7.dp,
            if (bias.severity == BiasSeverity.CRITICAL_ALERT) coralColor.copy(alpha = 0.45f) else cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header row with Name and Severity Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${bias.id}. ${bias.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = severityColor.copy(alpha = 0.18f),
                        border = BorderStroke(0.6.dp, severityColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = bias.severity.label,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = severityColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = if (effectiveExpanded) "▲" else "▼",
                        fontSize = 10.sp,
                        color = textSecondary
                    )
                }
            }

            // Kahneman Concept Summary
            Text(
                text = bias.kahnemanConcept,
                fontSize = 11.sp,
                color = textSecondary,
                lineHeight = 15.sp
            )

            // Vulnerability Score Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (bias.vulnerabilityScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape),
                    color = severityColor,
                    trackColor = severityColor.copy(alpha = 0.15f)
                )
                Text(
                    text = "${bias.vulnerabilityScore}%",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = severityColor
                )
            }

            // Expanded Breakdown: System 1 Impulse vs System 2 Prescription
            AnimatedVisibility(visible = effectiveExpanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // System 1 Impulse Box
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = coralColor.copy(alpha = if (isDarkTheme) 0.12f else 0.08f),
                        border = BorderStroke(0.6.dp, coralColor.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "⚡ SYSTEM 1 HUMAN IMPULSE (EMOTIONAL TRAP)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = coralLight
                            )
                            Text(
                                text = bias.system1Impulse,
                                fontSize = 11.sp,
                                color = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // System 2 Remedy Box
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = neonMint.copy(alpha = if (isDarkTheme) 0.12f else 0.08f),
                        border = BorderStroke(0.6.dp, neonMint.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "🧠 SYSTEM 2 RATIONAL REMEDY (ACTUARIAL ACTION)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = neonMint
                            )
                            Text(
                                text = bias.system2Prescription,
                                fontSize = 11.sp,
                                color = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
