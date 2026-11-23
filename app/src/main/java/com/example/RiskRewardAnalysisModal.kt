package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RiskRewardAnalysisModal(
    analysisList: List<RiskRewardAnalysis>,
    initialIndex: Int,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    if (analysisList.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, analysisList.lastIndex)) { analysisList.size }
    var isFullscreenTimelineOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (isFullscreenTimelineOpen) {
                isFullscreenTimelineOpen = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(enabled = isFullscreenTimelineOpen) {
            isFullscreenTimelineOpen = false
        }

        Surface(
            modifier = if (isFullscreenTimelineOpen) Modifier.fillMaxSize() else Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = if (isFullscreenTimelineOpen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp),
            color = if (isDarkTheme) Color(0xFF161616) else Color.White,
            border = if (isFullscreenTimelineOpen) null else BorderStroke(1.dp, if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
        ) {
            if (isFullscreenTimelineOpen) {
                // FULLSCREEN RISK-REWARD TIMELINE MODE (Flawless fund-to-fund HorizontalPager swiping)
                FullscreenRiskRewardTimelinePager(
                    allAnalyses = analysisList,
                    pagerState = pagerState,
                    isDarkTheme = isDarkTheme,
                    onCloseFullscreen = { isFullscreenTimelineOpen = false }
                )
            } else {
                // NORMAL MODAL CONTENT
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Historical Risk–Reward Score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Swipe ← → to compare portfolio funds",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF616161),
                                    fontSize = 11.sp
                                )
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider(color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))

                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        RiskRewardContent(
                            analysisList = analysisList,
                            currentIndex = page,
                            isDarkTheme = isDarkTheme,
                            onOpenFullscreen = { isFullscreenTimelineOpen = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskRewardContent(
    analysisList: List<RiskRewardAnalysis>,
    currentIndex: Int,
    isDarkTheme: Boolean,
    onOpenFullscreen: () -> Unit = {}
) {
    val analysis = analysisList[currentIndex]
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF1A1C1E)
    val textSecondary = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF616161)
    val cardBg = if (isDarkTheme) Color(0xFF222222) else Color(0xFFF1F3F4)
    val outlineColor = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    val scoreColor = when {
        analysis.score >= 80 -> Color(0xFF10B981)
        analysis.score >= 65 -> Color(0xFF10B981)
        analysis.score >= 50 -> Color(0xFFF59E0B)
        analysis.score >= 35 -> Color(0xFFEF5350)
        else -> Color(0xFFEF4444)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Scheme Title
        Text(
            text = analysis.schemeName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = textPrimary)
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Score Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scoreColor.copy(alpha = 0.1f))
                .border(1.dp, scoreColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${analysis.score}",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = scoreColor,
                        fontFamily = FontFamily.Monospace
                    )
                )
                Text(text = "/ 100", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = scoreColor
                ) {
                    Text(
                        text = analysis.scoreLabel.uppercase(Locale.US),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Regime / Character Badge
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = textSecondary.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, textSecondary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = analysis.regimeTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CORE INVESTMENT VERDICT: "Is this fund a favourable investment at current NAV?"
        Card(
            colors = CardDefaults.cardColors(containerColor = scoreColor.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, scoreColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CURRENT NAV FORWARD DECISION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = scoreColor,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = scoreColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", analysis.asymmetryRatio)}x Asymmetry",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scoreColor,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = analysis.coreVerdictExplanation,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        lineHeight = 16.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // HISTORICAL RISK-REWARD TIMELINE GRAPHICAL VIEW
        HistoricalRiskRewardGraphComponent(
            allAnalyses = analysisList,
            currentIndex = currentIndex,
            isDarkTheme = isDarkTheme,
            onOpenFullscreen = onOpenFullscreen,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: GOOGLE RESEARCH TimesFM-3 FOUNDATION MODEL FORECAST
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GOOGLE RESEARCH TimesFM-3 FORECAST",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textSecondary)
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Zero-Shot Time-Series Foundation Model",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, outlineColor, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // TimesFM Score & Trajectory Badge
                val tfmScoreColor = when {
                    analysis.timesFmScore >= 70 -> Color(0xFF10B981)
                    analysis.timesFmScore >= 50 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF5350)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TimesFM-3 Calibrated Score",
                            style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "PatchTST 32d Context • RevIN Normalized",
                            style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${analysis.timesFmScore} / 100",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = tfmScoreColor
                            )
                        )
                        Text(
                            text = analysis.timesFmBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = tfmScoreColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))

                // Multi-Horizon Quantile Forecasts Grid (5d, 20d, 40d)
                Text(
                    text = "QUANTILE UNCERTAINTY ENVELOPES (Q10 · Q50 · Q90)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tactical 5D
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF8F9FA)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).border(0.5.dp, outlineColor, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Tactical (5D)", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(4.dp))
                            val tacCol = if (analysis.timesFmTactical5dReturn >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350)
                            Text(
                                text = String.format(Locale.US, "%+.1f%%", analysis.timesFmTactical5dReturn),
                                style = MaterialTheme.typography.bodySmall.copy(color = tacCol, fontWeight = FontWeight.Black)
                            )
                            Text(text = "Median Q50", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 8.5.sp))
                        }
                    }

                    // Swing 20D (Benchmark)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF8F9FA)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).border(0.5.dp, outlineColor, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Swing (20D)", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(4.dp))
                            val swCol = if (analysis.timesFmExpectedReturn20d >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350)
                            Text(
                                text = String.format(Locale.US, "%+.1f%%", analysis.timesFmExpectedReturn20d),
                                style = MaterialTheme.typography.bodySmall.copy(color = swCol, fontWeight = FontWeight.Black)
                            )
                            Text(text = "Median Q50", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 8.5.sp))
                        }
                    }

                    // Trend 40D
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF8F9FA)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).border(0.5.dp, outlineColor, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Trend (40D)", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(4.dp))
                            val trCol = if (analysis.timesFmTrend40dReturn >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350)
                            Text(
                                text = String.format(Locale.US, "%+.1f%%", analysis.timesFmTrend40dReturn),
                                style = MaterialTheme.typography.bodySmall.copy(color = trCol, fontWeight = FontWeight.Black)
                            )
                            Text(text = "Median Q50", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 8.5.sp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quantile Bounds & Payoff Asymmetry
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "20-Day Quantile Dispersion",
                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                    )
                    Text(
                        text = "Q10 ${String.format(Locale.US, "%.1f%%", analysis.timesFmQuantileDownside)}  •  Q90 ${String.format(Locale.US, "+%.1f%%", analysis.timesFmQuantileUpside)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = textPrimary, fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zero-Shot Payoff Asymmetry Ratio",
                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", analysis.timesFmAsymmetryRatio)}x",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (analysis.timesFmAsymmetryRatio >= 1.5) Color(0xFF10B981) else if (analysis.timesFmAsymmetryRatio >= 1.0) Color(0xFFF59E0B) else Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trajectory Shape Classification",
                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                    )
                    Text(
                        text = analysis.timesFmPatchTrend,
                        style = MaterialTheme.typography.bodySmall.copy(color = textPrimary, fontWeight = FontWeight.SemiBold)
                    )
                }

                // Dynamic Daily Learning & Self-Correction Banner
                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Self-Correcting Layer",
                            style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = analysis.timesFmDailyLearningStatus,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2fx Adaptive", analysis.timesFmDailyCorrectionFactor),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (analysis.timesFmDailyLearningExplanation.isNotBlank()) {
                    Text(
                        text = analysis.timesFmDailyLearningExplanation,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Top Analogous Historical Trajectories ("What Happened Next")
                if (analysis.timesFmTopAnalogues.isNotEmpty()) {
                    HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "ANALOGOUS HISTORICAL TRAJECTORIES (WHAT HAPPENED NEXT)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scanned ${analysis.timesFmTotalAnaloguesScanned} historical situations across inception to project forward payoffs.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    analysis.timesFmTopAnalogues.take(3).forEach { analogue ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5),
                            border = BorderStroke(0.5.dp, outlineColor),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = analogue.dateStr,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${String.format(Locale.US, "%.0f%%", analogue.similarityPct)} Trajectory Match",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val fwdCol = if (analogue.realizedFwd20dReturnPct >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350)
                                    Text(
                                        text = "Subsequent 20D Return:",
                                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontSize = 11.sp)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%+.2f%%", analogue.realizedFwd20dReturnPct),
                                        style = MaterialTheme.typography.bodySmall.copy(color = fwdCol, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Max Interim Rally / Dip:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = textSecondary, fontSize = 10.sp)
                                    )
                                    Text(
                                        text = "+${String.format(Locale.US, "%.1f%%", analogue.realizedMaxInterimRallyPct)} / ${String.format(Locale.US, "%.1f%%", analogue.realizedMaxInterimDipPct)}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (analysis.timesFmExplanation.isNotBlank()) {
                    HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        text = analysis.timesFmExplanation,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textPrimary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 1: INTER-FUND REGIME & MULTI-SPAN CHARACTER SENSING
        Text(
            text = "INTER-FUND REGIME & MULTI-SPAN CHARACTER",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textSecondary)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, outlineColor, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Portfolio Market Breadth
                val breadth = analysis.portfolioBreadthPct
                val breadthLabel = when {
                    breadth <= 25.0 -> "Bear Market Regime 🔴"
                    breadth >= 75.0 -> "Bull Expansion 🟢"
                    else -> "Mixed Rotation 🟡"
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Portfolio Market Breadth", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "Funds > 50DMA: ${String.format(Locale.US, "%.0f%%", breadth)}", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Text(text = breadthLabel, style = MaterialTheme.typography.bodySmall.copy(color = if (breadth <= 25.0) Color(0xFFEF5350) else if (breadth >= 75.0) Color(0xFF10B981) else Color(0xFFF59E0B), fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Relative Strength vs Portfolio Cohort
                val rs = analysis.relativeStrengthPct
                val rsColor = if (rs > 5.0 && breadth <= 25.0) Color(0xFFF59E0B) else if (rs >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Relative Strength vs Portfolio", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "Excess 1M return over cohort median", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Text(text = String.format(Locale.US, "%+.2f%%", rs), style = MaterialTheme.typography.bodySmall.copy(color = rsColor, fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // 2-Month Rolling Return (R40)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "2-Month Rolling Return (R40)", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary))
                    Text(text = String.format(Locale.US, "%+.2f%%", analysis.currentR40), style = MaterialTheme.typography.bodySmall.copy(color = if (analysis.currentR40 >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350), fontWeight = FontWeight.Bold))
                }

                // Momentum Acceleration (2*R20 - R40)
                val accel = analysis.momentumAccelerationPct
                val accelLabel = if (accel > 8.0) "Parabolic Burst ⚠️" else if (accel < -5.0) "Decelerating 🟡" else "Steady Trend 🟢"
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "Momentum Acceleration", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary))
                        Text(text = accelLabel, style = MaterialTheme.typography.labelSmall.copy(color = if (accel > 8.0) Color(0xFFF59E0B) else textSecondary.copy(alpha = 0.8f), fontSize = 10.sp))
                    }
                    Text(text = String.format(Locale.US, "%+.2f%%", accel), style = MaterialTheme.typography.bodySmall.copy(color = textPrimary, fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: DUAL-SOURCE MARKET MOOD INDEX (MMI) CONTRARIAN SENTIMENT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MARKET MOOD INDEX (MMI) — DUAL SOURCE",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textSecondary)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (analysis.mmiScore < 30.0) Color(0xFF10B981).copy(alpha = 0.15f) else if (analysis.mmiScore < 50.0) Color(0xFF34D399).copy(alpha = 0.15f) else if (analysis.mmiScore < 70.0) Color(0xFFF59E0B).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Composite: ${String.format(Locale.US, "%.1f", analysis.mmiScore)} - ${analysis.mmiZone}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (analysis.mmiScore < 30.0) Color(0xFF10B981) else if (analysis.mmiScore < 50.0) Color(0xFF34D399) else if (analysis.mmiScore < 70.0) Color(0xFFF59E0B) else Color(0xFFEF4444)
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Side-by-Side Dual MMI Gauges (TMMI and GMMI)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Gauge: Tickertape MMI (TMMI)
            val tColor = when {
                analysis.tickertapeMmiScore < 30.0 -> Color(0xFF10B981)
                analysis.tickertapeMmiScore < 50.0 -> Color(0xFF34D399)
                analysis.tickertapeMmiScore < 70.0 -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, outlineColor, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "TMMI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                        Text(
                            text = "Tickertape",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = String.format(Locale.US, "%.1f", analysis.tickertapeMmiScore),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = tColor
                        )
                    )

                    Text(
                        text = analysis.tickertapeMmiZone,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal Mini Gauge Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF10B981),
                                        Color(0xFF34D399),
                                        Color(0xFFF59E0B),
                                        Color(0xFFEF4444)
                                    )
                                )
                            )
                    ) {
                        val ptrPos = (analysis.tickertapeMmiScore / 100.0).coerceIn(0.0, 1.0).toFloat()
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(size.width * ptrPos, size.height / 2f)
                            )
                        }
                    }
                }
            }

            // Right Gauge: Goodreturns MMI (GMMI)
            val gColor = when {
                analysis.goodreturnsMmiScore < 30.0 -> Color(0xFF10B981)
                analysis.goodreturnsMmiScore < 50.0 -> Color(0xFF34D399)
                analysis.goodreturnsMmiScore < 70.0 -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, outlineColor, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "GMMI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                        Text(
                            text = "Goodreturns",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = String.format(Locale.US, "%.1f", analysis.goodreturnsMmiScore),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = gColor
                        )
                    )

                    Text(
                        text = analysis.goodreturnsMmiZone,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal Mini Gauge Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF10B981),
                                        Color(0xFF34D399),
                                        Color(0xFFF59E0B),
                                        Color(0xFFEF4444)
                                    )
                                )
                            )
                    ) {
                        val ptrPos = (analysis.goodreturnsMmiScore / 100.0).coerceIn(0.0, 1.0).toFloat()
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(size.width * ptrPos, size.height / 2f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Consensus & Contrarian Impact Card
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, outlineColor, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Composite Sentiment Impact (${String.format(Locale.US, "%.1f", analysis.mmiScore)})",
                            style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Equal blend of TMMI (${String.format(Locale.US, "%.1f", analysis.tickertapeMmiScore)}) & GMMI (${String.format(Locale.US, "%.1f", analysis.goodreturnsMmiScore)})",
                            style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp)
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%+.1f pts", analysis.mmiContrarianBonus),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (analysis.mmiContrarianBonus > 0.0) Color(0xFF10B981) else if (analysis.mmiContrarianBonus < 0.0) Color(0xFFEF4444) else textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = analysis.mmiInterpretation,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = textPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 2: BENCHMARK INDICES MACRO INTER-BEHAVIOR (NIFTY 50, 500, MID 150, SMALL 250)
        Text(
            text = "BENCHMARK INDICES MACRO INTER-BEHAVIOR",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textSecondary)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, outlineColor, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Benchmark Breadth
                val benchBreadth = analysis.benchmarkBreadthPct
                val benchBreadthLabel = when {
                    benchBreadth <= 25.0 -> "Broad Market Bear Regime 🔴"
                    benchBreadth >= 75.0 -> "Broad Market Bull Expansion 🟢"
                    else -> "Mixed Multi-Cap Divergence 🟡"
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Major Indices Breadth", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "Nifty 50, 500, Mid 150, Small 250 > 50DMA", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = String.format(Locale.US, "%.0f%%", benchBreadth), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = textPrimary))
                        Text(text = benchBreadthLabel, style = MaterialTheme.typography.labelSmall.copy(color = if (benchBreadth <= 25.0) Color(0xFFEF5350) else if (benchBreadth >= 75.0) Color(0xFF10B981) else Color(0xFFF59E0B), fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
                    }
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Alpha vs Nifty 500 TRI
                val n500Alpha = analysis.nifty500AlphaPct
                val n500AlphaColor = if (n500Alpha > 8.0 && benchBreadth <= 25.0) Color(0xFFF59E0B) else if (n500Alpha >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Fund Alpha vs Nifty 500 TRI", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "Excess 1M return over Indian broad market", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Text(text = String.format(Locale.US, "%+.2f%%", n500Alpha), style = MaterialTheme.typography.bodySmall.copy(color = n500AlphaColor, fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Nifty 500 TRI Drawdown from Peak
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Nifty 500 Drawdown from Peak", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary))
                    Text(text = String.format(Locale.US, "%+.2f%%", analysis.nifty500PullbackPct), style = MaterialTheme.typography.bodySmall.copy(color = if (analysis.nifty500PullbackPct <= -7.0) Color(0xFFEF5350) else textPrimary, fontWeight = FontWeight.Bold))
                }

                // Nifty Smallcap 250 1M Return
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Nifty Smallcap 250 1M Return", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary))
                    Text(text = String.format(Locale.US, "%+.2f%%", analysis.smallcap250_1M_ReturnPct), style = MaterialTheme.typography.bodySmall.copy(color = if (analysis.smallcap250_1M_ReturnPct >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350), fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 3: HISTORICAL PROBABILITIES & MULTI-HORIZON OUTCOMES
        Text(text = "HISTORICAL PROBABILITY & FORWARD OUTCOMES", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textSecondary))
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Gain Probability
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).border(1.dp, outlineColor, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "%.1f%%", analysis.probabilityPositivePct),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = if (analysis.probabilityPositivePct >= 60.0) Color(0xFF10B981) else Color(0xFFEF5350))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Gain Probability", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall.copy(color = textSecondary, fontWeight = FontWeight.Bold))
                }
            }

            // Comparable Matches
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).border(1.dp, outlineColor, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${analysis.comparablesCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = textPrimary))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Comparable Matches", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall.copy(color = textSecondary, fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stat details
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, outlineColor, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val statItems = listOf(
                    "Typical 1-Month Reward (Avg Gain)" to String.format(Locale.US, "+%.2f%%", analysis.typicalRewardPct) to Color(0xFF10B981),
                    "Typical 2-Month Trajectory Payoff" to String.format(Locale.US, "+%.2f%%", analysis.forward2MRewardPct) to Color(0xFF10B981),
                    "Typical Risk (Avg Loss)" to String.format(Locale.US, "%.2f%%", analysis.typicalRiskPct) to Color(0xFFEF5350),
                    "Worst Historic Drawdown" to String.format(Locale.US, "%.2f%%", analysis.worstLossPct) to Color(0xFFEF4444),
                    "Confidence Level" to analysis.confidenceLevel to textPrimary
                )
                statItems.forEach { (pair, color) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = pair.first, style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = pair.second, style = MaterialTheme.typography.bodySmall.copy(color = color, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 3: DYNAMIC SETUP (BACKWARD INTEGRATION)
        Text(text = "DYNAMIC SETUP (BACKWARD INTEGRATION)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textSecondary))
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, outlineColor, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Category-Aware Volatility Profile
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Asset Category Character", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "Adaptive Volatility Scaling: ${String.format(Locale.US, "%.2fx", analysis.volatilityScalingRatio)}", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Text(text = analysis.categoryProfile, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // NSE India VIX & Volatility Dynamics (Level, ΔVIX 5D, Acceleration)
                val vixCol = when {
                    analysis.indiaVix < 13.0 -> Color(0xFFF59E0B)
                    analysis.indiaVix < 16.0 -> Color(0xFF10B981)
                    analysis.indiaVix < 20.0 -> Color(0xFFFB923C)
                    else -> Color(0xFFEF5350)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "NSE India VIX & Dynamics (ΔVIX)", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "5D Δ: ${String.format(Locale.US, "%+.2f", analysis.indiaVixDelta5d)} (${String.format(Locale.US, "%+.1f%%", analysis.indiaVixDeltaPct5d)}) | β: ${String.format(Locale.US, "%.2fx", analysis.indiaVixSensitivityBeta)}", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = String.format(Locale.US, "%.2f", analysis.indiaVix), style = MaterialTheme.typography.bodySmall.copy(color = vixCol, fontWeight = FontWeight.Bold))
                        Text(text = analysis.indiaVixTrendLabel, style = MaterialTheme.typography.labelSmall.copy(color = vixCol, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
                    }
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Blended Market Sentiment (MMI + India VIX Dynamics)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Market Sentiment & Volatility Blend", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "Composite MMI: ${String.format(Locale.US, "%.1f", analysis.mmiScore)} (${analysis.mmiZone}) | TMMI: ${String.format(Locale.US, "%.1f", analysis.tickertapeMmiScore)}, GMMI: ${String.format(Locale.US, "%.1f", analysis.goodreturnsMmiScore)}", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "${String.format(Locale.US, "%.0f", analysis.blendedSentimentVolScore)} / 100", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))
                        Text(text = "Blended Contrarian Signal", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
                    }
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Movement from Highest NAV
                val pullback = analysis.currentPullbackFromPeakPct
                val pullbackLabel = when {
                    pullback >= -0.5 -> "At All-Time Peak ⚠️"
                    pullback >= -2.0 -> "Near Peak Consolidation 🟡"
                    pullback >= -6.0 -> "Healthy Dip Zone 🟢"
                    else -> "Deep Drawdown Discount 🔴"
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Movement from Highest NAV", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        if (analysis.highestNavSoFar > 0.0) Text(text = "Peak: ₹${String.format(Locale.US, "%.2f", analysis.highestNavSoFar)}", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = String.format(Locale.US, "%+.2f%%", pullback), style = MaterialTheme.typography.bodySmall.copy(color = if (pullback >= -0.5) Color(0xFFF59E0B) else if (pullback >= -6.0) Color(0xFF10B981) else Color(0xFFEF5350), fontWeight = FontWeight.Bold))
                        Text(text = pullbackLabel, style = MaterialTheme.typography.labelSmall.copy(color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
                    }
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // 1-Month Rolling Return & Percentile
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "1-Month Rolling Return (R20)", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary))
                        Text(text = "Historical Percentile: ${String.format(Locale.US, "%.0f", analysis.currentRolling1MPctile)}th", style = MaterialTheme.typography.labelSmall.copy(color = if (analysis.currentRolling1MPctile >= 80.0) Color(0xFFF59E0B) else textSecondary.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
                    }
                    Text(text = String.format(Locale.US, "%+.2f%%", analysis.currentR20), style = MaterialTheme.typography.bodySmall.copy(color = if (analysis.currentR20 >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350), fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Technical Distances
                listOf(
                    "Distance from 20DMA (D20)" to String.format(Locale.US, "%+.2f%%", analysis.currentD20) to (if (analysis.currentD20 in -2.5..1.5) Color(0xFF10B981) else if (analysis.currentD20 > 1.5) Color(0xFFF59E0B) else Color(0xFFEF5350)),
                    "Distance from 50DMA (D50)" to String.format(Locale.US, "%+.2f%%", analysis.currentD50) to (if (analysis.currentD50 >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350)),
                    "20/50 DMA Trend Alignment" to String.format(Locale.US, "%+.2f%%", analysis.dma20_50_SpreadPct) to (if (analysis.dma20_50_SpreadPct >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350)),
                    "Distance from 200DMA (D200)" to String.format(Locale.US, "%+.2f%%", analysis.currentD200) to (if (analysis.currentD200 >= 0.0) Color(0xFF10B981) else Color(0xFFEF5350))
                ).forEach { (pair, color) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = pair.first, style = MaterialTheme.typography.bodySmall.copy(color = textSecondary))
                        Text(text = pair.second, style = MaterialTheme.typography.bodySmall.copy(color = color, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 4: DYNAMIC SELF-IMPROVING ENGINE & ERROR CHECKING
        Text(text = "DYNAMIC SELF-IMPROVING ENGINE & ERROR CHECKING", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textSecondary))
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, outlineColor, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Auto-Tuning Status
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Calibration Engine Status", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                    Text(text = analysis.tuningStatus, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Walk-Forward Out-Of-Sample Accuracy
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Walk-Forward Directional Accuracy", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "Tested over rolling 120 trading sessions", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Text(text = String.format(Locale.US, "%.1f%%", analysis.directionalAccuracyPct), style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Calibration Error (MSE)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Calibration Loss (MSE)", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary))
                    Text(text = String.format(Locale.US, "%.3f (Optimized)", analysis.calibrationErrorMse), style = MaterialTheme.typography.bodySmall.copy(color = textPrimary, fontWeight = FontWeight.Bold))
                }

                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Similar-Case Analysis Depth
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "Similar-Case Search Horizon", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, fontWeight = FontWeight.Medium))
                        Text(text = "Matches across full multi-year history", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
                    }
                    Text(text = "Since Inception 🟢", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))
                }

                // Data Error Screening
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Built-in Data Error Screening", style = MaterialTheme.typography.bodySmall.copy(color = textSecondary))
                    Text(text = "Sanitized 🟢", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 5: METHODOLOGY & BIAS PROTECTION
        Card(
            colors = CardDefaults.cardColors(containerColor = scoreColor.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, scoreColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Info", tint = scoreColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Multi-Span & Benchmark Architecture", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textPrimary))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To eliminate blind spots (such as an isolated IPO surging in a bear market, or high-beta rebounds after market capitulation floors), this engine senses:\n\n" +
                                "1. Independent Benchmark Validation: Cross-validates against Nifty 50, 500 TRI, Midcap 150, and Smallcap 250. This prevents Fund-House Overlap Bias (where multiple funds from the same AMC co-move due to identical stock holdings).\n" +
                                "2. Real-Time NSE India VIX Blending: Blends actual historical and live India VIX data to dynamically scale forward downside risk envelopes and detect asymmetric fear discounts, with category-aware beta sensitivity (β = ${String.format(Locale.US, "%.2fx", analysis.indiaVixSensitivityBeta)}) for pure equity funds.\n" +
                                "3. Multi-Span Rolling (1M & 2M): Detects momentum acceleration and avoids buying unsustainable parabolic bursts.\n" +
                                "4. Daily Self-Improving Feedback Loop: Executes walk-forward error-checking and weight tuning every time new daily NAV data arrives, maximizing out-of-sample forward decision accuracy.",
                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary, lineHeight = 16.sp)
                    )
                    if (analysis.indiaVixImpactDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "India VIX Market Impact: ${analysis.indiaVixImpactDescription}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
