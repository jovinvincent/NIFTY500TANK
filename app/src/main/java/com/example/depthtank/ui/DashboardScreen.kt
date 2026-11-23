package com.example.depthtank.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.depthtank.data.ChartAggregation
import com.example.depthtank.data.DataQuality
import com.example.depthtank.data.DeadlyComboReport
import com.example.depthtank.data.FourPillarSnapshot
import com.example.depthtank.data.InstitutionalRegime
import com.example.depthtank.data.InvestorActionStage
import com.example.depthtank.data.TankCalculator
import com.example.depthtank.data.TankIndexUniverse
import com.example.depthtank.data.TankUiState
import com.example.depthtank.data.TimeframeOption
import com.example.ui.theme.BreakoutAmber
import com.example.ui.theme.CapitulationPurple
import com.example.ui.theme.CoralOverheated
import com.example.ui.theme.CyanLiquid
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ValuationPePink
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    uiState: TankUiState,
    onSelectUniverse: (TankIndexUniverse) -> Unit = {},
    onSelectTimeframe: (TimeframeOption) -> Unit,
    onSelectAggregation: (ChartAggregation) -> Unit = {},
    onRetryArchive: () -> Unit = {},
    onOpenInfoModal: () -> Unit,
    onHoleThresholdChange: (Float) -> Unit = {},
    onResetHoleThreshold: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pillars = uiState.fourPillarSnapshot
    val showBreadthWarning = pillars.hasHiddenBearishDivergence ||
        pillars.regime == InstitutionalRegime.MOMENTUM_EXHAUSTION ||
        pillars.regime == InstitutionalRegime.BREADTH_DETERIORATION ||
        (pillars.isOverheatedThresholdBreached &&
            pillars.euphoriaPressurePct > 75.0 &&
            pillars.breakoutPulseDelta20 <= -2.5)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DashboardHeader(
                uiState = uiState,
                onSelectUniverse = onSelectUniverse,
                onOpenInfoModal = onOpenInfoModal
            )
        }

        item {
            HeroMainTankCard(
                tankLevel = uiState.currentTankLevel,
                dailyDelta = uiState.dailyDelta,
                flowingCount = uiState.flowingCount,
                activeCount = uiState.activeStocksCount,
                regime = uiState.regime,
                holeThreshold = uiState.holeThreshold,
                euphoriaPressurePct = pillars.euphoriaPressurePct,
                euphoriaPressureCount = pillars.euphoriaPressureCount,
                institutionalRegime = uiState.institutionalRegime,
                plateauDays = pillars.plateauDays,
                peTankLevel = uiState.currentPeTankLevel,
                medianPe = uiState.currentIndexPe ?: uiState.currentMedianPe
            )
        }

        if (uiState.cycleReport.investorDirective.headlineDirective.isNotBlank()) {
            item {
                DailyDualValuationRecommendationCard(report = uiState.cycleReport)
            }
        }

        if (showBreadthWarning) {
            item {
                BreadthDeteriorationBanner(pillars = pillars)
            }
        }

        item {
            FourPillarIntelligenceGrid(
                uiState = uiState,
                pillars = pillars
            )
        }

        item {
            HistoricalDepthChartCard(
                history = uiState.filteredHistory,
                selectedTimeframe = uiState.selectedTimeframe,
                onSelectTimeframe = onSelectTimeframe,
                holeThreshold = uiState.holeThreshold,
                detectedCycles = uiState.detectedCycles,
                selectedAggregation = uiState.selectedAggregation,
                onSelectAggregation = onSelectAggregation,
                archiveHydrationState = uiState.archiveHydrationState,
                onRetryArchive = onRetryArchive
            )
        }

        item {
            HistoricalCyclesTableCard(
                cycles = uiState.detectedCycles,
                holeThreshold = uiState.holeThreshold
            )
        }

        item {
            HolePositionSliderCard(
                holeThreshold = uiState.holeThreshold,
                flowingCount = uiState.flowingCount,
                activeCount = uiState.activeStocksCount,
                currentTankLevel = uiState.currentTankLevel,
                currentPeTankLevel = uiState.currentPeTankLevel,
                currentMedianPe = uiState.currentIndexPe ?: uiState.currentMedianPe,
                onHoleThresholdChange = onHoleThresholdChange,
                onResetHoleThreshold = onResetHoleThreshold
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DailyDualValuationRecommendationCard(
    report: DeadlyComboReport,
    modifier: Modifier = Modifier
) {
    val directive = report.investorDirective
    val k = report.kinetics
    val stageAccent = when (directive.activeStage) {
        InvestorActionStage.MASSIVE_LUMP_SUM -> CapitulationPurple
        InvestorActionStage.AGGRESSIVE_SIP_STEP_UP -> EmeraldBullish
        InvestorActionStage.HOLD_STANDARD_SIP -> CyanLiquid
        InvestorActionStage.REDUCE_SIP_PAUSE_LUMPSUM -> BreakoutAmber
        InvestorActionStage.START_PROFIT_BOOKING,
        InvestorActionStage.MASSIVE_PROFIT_BOOKING -> CoralOverheated
    }
    val absAccent = when (k.absolutePeRegime) {
        "Deep Value", "Attractive Value" -> EmeraldBullish
        "Fair Value" -> CyanLiquid
        "Moderately Elevated" -> BreakoutAmber
        "Expensive / Frothy" -> CoralOverheated
        else -> TextMuted
    }
    val primaryPe = k.primaryAbsolutePe

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_daily_recommendation_card"),
        shape = RoundedCornerShape(18.dp),
        color = GlassCardSurface,
        border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.38f))
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
                Text(
                    text = "DAILY RECOMMENDATION • DUAL VALUATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = stageAccent,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = stageAccent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = directive.activeStage.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = stageAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = directive.headlineDirective,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            if (k.peTankLevel != null || primaryPe != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedCardSurface,
                    border = BorderStroke(1.dp, absAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = k.dualValuationVerdict,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val ltStr = k.medianPeLongTermMedian?.let { "20Y Med ${it}×" } ?: "20Y Band"
                            val pbStr = k.indexPb?.let { " • P/B ${it}×" } ?: ""
                            val eqWtStr = if (k.indexPe != null && k.medianPe != null) {
                                " • Eq-Wt ${k.medianPe}×"
                            } else if (k.peActiveCount > 0) {
                                " • ${k.peActiveCount}/${report.universe.targetCount} stocks"
                            } else ""
                            Text(
                                text = "$ltStr • ${k.absolutePeRegime}$pbStr$eqWtStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = absAccent
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            if (k.peTankLevel != null) {
                                Text(
                                    text = "${k.peTankLevel}%",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                    color = ValuationPePink,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (primaryPe != null) {
                                Text(
                                    text = if (k.isOfficialIndexPeBenchmark) "${primaryPe}× Idx" else "${primaryPe}× Med",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = absAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardHeader(
    uiState: TankUiState,
    onSelectUniverse: (TankIndexUniverse) -> Unit,
    onOpenInfoModal: () -> Unit
) {
    val integrity = uiState.datasetIntegrity
    val isVerifiedLive = integrity.dataQuality == DataQuality.VERIFIED_EOD && !integrity.isIllustrative
    val verifiedDateFormatted = formatDisplayDate(integrity.lastVerifiedTradingDate)
    val universe = uiState.selectedUniverse
    var universeMenuExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { universeMenuExpanded = true }
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                        .testTag("universe_dropdown_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = universe.dashboardTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Select index universe",
                            tint = CyanLiquid,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = "${universe.targetCount}-Stock Base (${universe.rangeLabel}) • Institutional 52-Week Breadth",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                DropdownMenu(
                    expanded = universeMenuExpanded,
                    onDismissRequest = { universeMenuExpanded = false },
                    containerColor = ElevatedCardSurface
                ) {
                    TankIndexUniverse.entries.forEach { option ->
                        val isSelected = option == universe
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.dropdownLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) CyanLiquid else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = "${option.targetCount}-stock denominator base",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected index",
                                            tint = CyanLiquid,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectUniverse(option)
                                universeMenuExpanded = false
                            },
                            modifier = Modifier.testTag("universe_option_${option.name.lowercase(Locale.US)}")
                        )
                    }
                }
            }
            IconButton(
                onClick = onOpenInfoModal,
                modifier = Modifier.testTag("info_modal_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Explain ${universe.dashboardTitle} model",
                    tint = CyanLiquid
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live market auto-refresh / EOD verified trading date sync badge
            val isLiveMarket = uiState.pipelineStatus.isLiveMarketActive
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isLiveMarket) EmeraldBullish.copy(alpha = 0.12f) else ElevatedCardSurface,
                border = BorderStroke(
                    1.dp,
                    if (isLiveMarket) EmeraldBullish.copy(alpha = 0.45f) else HairlineBorder
                ),
                modifier = Modifier.testTag("dashboard_sync_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        tint = if (isLiveMarket) EmeraldBullish else CyanLiquid,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = when {
                            uiState.pipelineStatus.isSyncing -> "Syncing NSE/BSE Feed..."
                            isLiveMarket && uiState.pipelineStatus.isLiveHydrated ->
                                "${uiState.pipelineStatus.lastSyncedDisplay} • Auto 30s"
                            isLiveMarket ->
                                "Market Open • Live Auto-Refresh ($verifiedDateFormatted)"
                            else ->
                                "EOD $verifiedDateFormatted • Mon–Fri @ 4:15 PM IST"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLiveMarket) EmeraldBullish else TextSecondary
                    )
                }
            }

            // DataQuality / isIllustrative transparency badge
            val qualityAccent = if (isVerifiedLive) EmeraldBullish else BreakoutAmber
            Surface(
                shape = RoundedCornerShape(50),
                color = qualityAccent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, qualityAccent.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("dashboard_data_quality_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = qualityAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isVerifiedLive) {
                            "VERIFIED EOD (${(integrity.constituentCoverage * 100).roundToInt()}%)"
                        } else {
                            "ILLUSTRATIVE MODEL"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = qualityAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            InstitutionalRegimePill(
                regime = uiState.institutionalRegime,
                modifier = Modifier.testTag("dashboard_regime_pill")
            )
        }
    }
}

@Composable
private fun BreadthDeteriorationBanner(pillars: FourPillarSnapshot) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CoralOverheated.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, CoralOverheated.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("breadth_deterioration_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = "Breadth deterioration divergence warning",
                tint = CoralOverheated,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "CAUSAL MULTI-PILLAR BREADTH DIVERGENCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = CoralOverheated,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Main Tank (${String.format(Locale.US, "%.1f%%", pillars.mainTankLevelPct)}) & Euphoria (${String.format(Locale.US, "%.1f%%", pillars.euphoriaPressurePct)}) remain elevated while 20D Breakout Pulse contracted (${formatSignedPercent(pillars.breakoutPulseDelta20)}). Fewer constituents are sustaining fresh 52-Week Highs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun FourPillarIntelligenceGrid(
    uiState: TankUiState,
    pillars: FourPillarSnapshot
) {
    val activeCount = pillars.activeEligibleStocksCount.coerceAtLeast(uiState.activeStocksCount)
    val holeInt = uiState.holeThreshold.roundToInt()
    val overheatedInt = TankCalculator.overheatedThresholdForHole(uiState.holeThreshold.toDouble()).roundToInt()
    val mainTankExhaustionNote = when {
        pillars.hasHiddenBearishDivergence -> "Exhaustion Alert • ${pillars.plateauDays}d >$overheatedInt%"
        pillars.plateauDays > 0 -> "Plateau: ${pillars.plateauDays}d above $overheatedInt% OB"
        else -> "${uiState.flowingCount}/$activeCount >$holeInt% • ${formatSignedPercent(uiState.dailyDelta)} 1D"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("four_pillar_grid"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PillarIntelligenceCard(
                title = "Pillar 1 • Main Tank",
                primaryValue = String.format(Locale.US, "%.1f%%", uiState.currentTankLevel),
                subtitle = mainTankExhaustionNote,
                badgeText = if (pillars.isOverheatedThresholdBreached) "OVERHEATED >$overheatedInt%" else "52-Week Overflow",
                accent = CyanLiquid,
                icon = Icons.Rounded.WaterDrop,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pillar_card_main_tank")
            )
            PillarIntelligenceCard(
                title = "Pillar 2 • Euphoria >85",
                primaryValue = String.format(Locale.US, "%.1f%%", pillars.euphoriaPressurePct),
                subtitle = "${pillars.euphoriaPressureCount} / $activeCount stocks",
                badgeText = "Near 52-Week High",
                accent = CoralOverheated,
                icon = Icons.Rounded.Bolt,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pillar_card_euphoria")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val deltaSignColor = if (pillars.breakoutPulseDelta20 >= 0.0) EmeraldBullish else CoralOverheated
            PillarIntelligenceCard(
                title = "Pillar 3 • Breakout Pulse",
                primaryValue = String.format(Locale.US, "%.1f%%", pillars.breakoutPulsePct),
                subtitle = "${pillars.breakoutPulseCount} fresh 52-Week Highs",
                badgeText = "Δ20D: ${formatSignedPercent(pillars.breakoutPulseDelta20)}",
                accent = BreakoutAmber,
                badgeColor = deltaSignColor,
                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pillar_card_breakout_pulse")
            )
            PillarIntelligenceCard(
                title = "Pillar 4 • Capitulation <15",
                primaryValue = String.format(Locale.US, "%.1f%%", pillars.capitulationDepthPct),
                subtitle = "${pillars.capitulationCount} / $activeCount washed out",
                badgeText = "Near 52-Week Low",
                accent = CapitulationPurple,
                icon = Icons.Rounded.Waves,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pillar_card_capitulation")
            )
        }
    }
}

@Composable
private fun PillarIntelligenceCard(
    title: String,
    primaryValue: String,
    subtitle: String,
    badgeText: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badgeColor: Color = accent
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = GlassCardSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = badgeColor.copy(alpha = 0.13f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun HolePositionSliderCard(
    holeThreshold: Float,
    flowingCount: Int,
    activeCount: Int,
    currentTankLevel: Double,
    currentPeTankLevel: Double? = null,
    currentMedianPe: Double? = null,
    onHoleThresholdChange: (Float) -> Unit,
    onResetHoleThreshold: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDefault = abs(holeThreshold - 50f) < 0.5f
    val intThreshold = holeThreshold.roundToInt()

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hole_position_slider_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DYNAMIC DRAINAGE HOLE SIMULATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Hole Position: $intThreshold%",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (isDefault) TextPrimary else CyanLiquid,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("hole_position_label")
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = (if (isDefault) CyanLiquid else BreakoutAmber).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, (if (isDefault) CyanLiquid else BreakoutAmber).copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = if (isDefault) "DEFAULT 50%" else "SIMULATING",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDefault) CyanLiquid else BreakoutAmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (!isDefault) {
                TextButton(
                    onClick = onResetHoleThreshold,
                    modifier = Modifier.testTag("reset_hole_threshold_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset hole threshold to default 50%",
                        tint = CyanLiquid,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset 50%",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanLiquid,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ElevatedCardSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = "Anchor: 50%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val dynamicExplanation = when {
            intThreshold == 50 -> "Permanent benchmark valve: Drains stocks in the lower 52-Week half (<50%); measures pure upward overflow into the Main Tank."
            intThreshold < 50 -> "Lower valve ($intThreshold%): Permissive simulation capturing early rebound momentum across $flowingCount/$activeCount eligible stocks."
            intThreshold > 75 -> "Overheated valve ($intThreshold%): High-pressure simulation isolating leaders pinned near their 52-Week High ($flowingCount stocks)."
            else -> "Higher valve ($intThreshold%): Strict 52-Week filter admitting only upper-tier stocks ($flowingCount/$activeCount flowing)."
        }
        Text(
            text = dynamicExplanation,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Slider(
            value = holeThreshold,
            onValueChange = onHoleThresholdChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = CyanLiquid,
                activeTrackColor = CyanLiquid,
                inactiveTrackColor = ElevatedCardSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hole_position_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                0f to "0% (Avg)",
                25f to "25% (Broad)",
                50f to "50% (Default)",
                75f to "75% (Caution)",
                85f to "85% (Euphoria)"
            )
            presets.forEach { (presetVal, label) ->
                val isSelected = intThreshold == presetVal.toInt()
                FilterChip(
                    selected = isSelected,
                    onClick = { onHoleThresholdChange(presetVal) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanLiquid.copy(alpha = 0.18f),
                        selectedLabelColor = CyanLiquid,
                        containerColor = ElevatedCardSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = HairlineBorder,
                        selectedBorderColor = CyanLiquid.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("hole_preset_${presetVal.toInt()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HoleTelemetryTile(
                    label = "Capacity Above Valve",
                    value = "${100 - intThreshold}%",
                    valueColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                HoleTelemetryTile(
                    label = "Flowing Stocks",
                    value = "$flowingCount / $activeCount",
                    valueColor = if (flowingCount > 0) CyanLiquid else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HoleTelemetryTile(
                    label = "Price Tank Depth",
                    value = String.format(Locale.US, "%.1f%%", currentTankLevel),
                    valueColor = CyanLiquid,
                    modifier = Modifier.weight(1f)
                )
                val peDisplay = if (currentPeTankLevel != null) {
                    val medSuffix = currentMedianPe?.let { String.format(Locale.US, " (%.1f×)", it) } ?: ""
                    String.format(Locale.US, "%.1f%%%s", currentPeTankLevel, medSuffix)
                } else {
                    "N/A"
                }
                HoleTelemetryTile(
                    label = "Val P/E Depth",
                    value = peDisplay,
                    valueColor = if (currentPeTankLevel != null) ValuationPePink else TextMuted,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hole_valuation_pe_tile")
                )
            }
        }
    }
}

@Composable
private fun HoleTelemetryTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ElevatedCardSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
