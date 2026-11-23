package com.example.depthtank.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.depthtank.data.ComboChecklistRule
import com.example.depthtank.data.ComboDangerLevel
import com.example.depthtank.data.DeadlyComboReport
import com.example.depthtank.data.HistoricalComboEpisode
import com.example.depthtank.data.TankIndexUniverse
import com.example.depthtank.data.TankUiState
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

@Composable
fun CycleReportScreen(
    uiState: TankUiState,
    onSelectUniverse: (TankIndexUniverse) -> Unit = {},
    onForceRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val report = uiState.cycleReport
    var selectedEpisodeFilter by remember { mutableStateOf("TOPS") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("cycle_report_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CycleReportHeader(
                uiState = uiState,
                report = report,
                onSelectUniverse = onSelectUniverse,
                onForceRefresh = onForceRefresh
            )
        }

        item {
            ExecutiveCompassCard(report = report)
        }

        item {
            InvestorActionPlaybookCard(report = report)
        }

        item {
            TrajectoryPathForecastCard(report = report)
        }

        item {
            MultiHolePositionRadarCard(report = report)
        }

        item {
            DeadlyPeakChecklistCard(report = report)
        }

        item {
            DeadlyBottomChecklistCard(report = report)
        }

        item {
            val hasPre2016Episodes = report.historicalPeakEpisodes.any { it.peakOrTroughDate < "2016-04-01" } ||
                report.historicalBottomEpisodes.any { it.peakOrTroughDate < "2016-04-01" }
            HistoricalLedgerHeader(
                selectedFilter = selectedEpisodeFilter,
                onFilterChange = { selectedEpisodeFilter = it },
                peakCount = report.historicalPeakEpisodes.size,
                bottomCount = report.historicalBottomEpisodes.size,
                hasPre2016Episodes = hasPre2016Episodes
            )
        }

        val episodes = if (selectedEpisodeFilter == "TOPS") {
            report.historicalPeakEpisodes
        } else {
            report.historicalBottomEpisodes
        }

        items(episodes, key = { it.episodeId }) { episode ->
            HistoricalComboEpisodeCard(episode = episode)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CycleReportHeader(
    uiState: TankUiState,
    report: DeadlyComboReport,
    onSelectUniverse: (TankIndexUniverse) -> Unit,
    onForceRefresh: () -> Unit
) {
    var universeMenuExpanded by remember { mutableStateOf(false) }
    val universe = uiState.selectedUniverse
    val pipeline = uiState.pipelineStatus

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { universeMenuExpanded = true }
                        .padding(vertical = 2.dp)
                        .testTag("report_universe_dropdown_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "9-Factor Cycle Sniper & Tax Report",
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "${universe.dropdownLabel} • Session ${report.sessionDate.ifBlank { pipeline.lastTradeDate }} • ${pipeline.lastSyncedDisplay}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
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
                                Column {
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
                            },
                            onClick = {
                                universeMenuExpanded = false
                                onSelectUniverse(option)
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = onForceRefresh,
                enabled = !pipeline.isSyncing,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("report_refresh_button")
            ) {
                if (pipeline.isSyncing) {
                    CircularProgressIndicator(
                        color = CyanLiquid,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.action_refresh_live_report),
                        tint = CyanLiquid
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutiveCompassCard(report: DeadlyComboReport) {
    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("executive_compass_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WHERE MARKET STANDS & WHERE IT IS HEADING",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = when (report.peakDangerLevel) {
                        ComboDangerLevel.SAFE -> EmeraldBullish.copy(alpha = 0.15f)
                        ComboDangerLevel.ELEVATED -> BreakoutAmber.copy(alpha = 0.15f)
                        ComboDangerLevel.CRITICAL_EXTREME -> CoralOverheated.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = report.peakDangerLevel.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (report.peakDangerLevel) {
                            ComboDangerLevel.SAFE -> EmeraldBullish
                            ComboDangerLevel.ELEVATED -> BreakoutAmber
                            ComboDangerLevel.CRITICAL_EXTREME -> CoralOverheated
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = report.currentMarketQuadrant,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            if (report.isPartialFactorScore) {
                Surface(
                    color = BreakoutAmber.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BreakoutAmber.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .semantics(mergeDescendants = true) {}
                        .testTag("partial_factor_coverage_badge")
                ) {
                    Text(
                        text = "${report.factorCoverageLabel} to 0–100 (Pre-Inception Factors Excluded, Never Zeroed)",
                        style = MaterialTheme.typography.labelSmall,
                        color = BreakoutAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Capitulation Floor (H25 ≤ 20%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CapitulationPurple
                    )
                    Text(
                        text = "Euphoric Climax (H75 ≥ 58.5%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralOverheated
                    )
                }

                val currentProgress = (report.currentReading.hole50 / 85.0).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (currentProgress > 0.7f) CoralOverheated else if (currentProgress < 0.2f) CapitulationPurple else CyanLiquid,
                    trackColor = ElevatedCardSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "+${report.bufferAboveCapitulationBottomPct}% Safe Buffer above bottom",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBullish
                    )
                    Text(
                        text = "+${report.distanceToEuphoricPeakPct}% H75 needed to peak",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            val peH50 = report.kinetics.peTankLevel
            val primaryPe = report.kinetics.primaryAbsolutePe
            val eqWtMedPe = report.kinetics.medianPe
            val medPeLt = report.kinetics.medianPeLongTermMedian
            val medPePctile = report.kinetics.medianPeHistoricalPercentile
            val consPctile = report.kinetics.indexPeConsolidatedPercentile
            val peActive = report.kinetics.peActiveCount
            val hasAnyVal = peH50 != null || primaryPe != null
            val absColor = when (report.kinetics.absolutePeRegime) {
                "Deep Value", "Attractive Value" -> EmeraldBullish
                "Fair Value" -> CyanLiquid
                "Moderately Elevated" -> BreakoutAmber
                "Expensive / Frothy" -> CoralOverheated
                else -> TextMuted
            }
            Surface(
                color = if (hasAnyVal) ValuationPePink.copy(alpha = 0.08f) else ElevatedCardSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (hasAnyVal) absColor.copy(alpha = 0.40f) else HairlineBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compass_dual_valuation_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (report.kinetics.isOfficialIndexPeBenchmark) {
                                "DUAL VALUATION (52W VAL P/E + OFFICIAL NSE INDEX P/E)"
                            } else {
                                "DUAL VALUATION (52W NORMALIZED + ABSOLUTE MEDIAN P/E)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasAnyVal) absColor else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (hasAnyVal) {
                                report.kinetics.dualValuationVerdict
                            } else {
                                "Pre-1999 N/A (No Synthetic Proxy)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        if (primaryPe != null) {
                            val ltPart = medPeLt?.let { "20Y Med ${it}×" } ?: "Long-Term Band"
                            val pctPart = when {
                                medPePctile != null && consPctile != null -> " • ${medPePctile}th (20Y) / ${consPctile}th (Cons.)"
                                medPePctile != null -> " • ${medPePctile}th %ile"
                                else -> ""
                            }
                            val pbDyPart = buildString {
                                report.kinetics.indexPb?.let { append(" • P/B ${it}×") }
                                report.kinetics.indexDivYield?.let { append(" • DY ${it}%") }
                            }
                            val eqWtPart = if (report.kinetics.indexPe != null && eqWtMedPe != null) {
                                val pitTag = if (report.kinetics.isPointInTimeConstituentPeTank) " PIT" else ""
                                " • Eq-Wt Med ${eqWtMedPe}× ($peActive/${report.universe.targetCount}$pitTag)"
                            } else if (peActive > 0) {
                                " • $peActive/${report.universe.targetCount} stocks"
                            } else ""
                            Text(
                                text = "$ltPart$pctPart$pbDyPart$eqWtPart",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = when {
                                peH50 != null && primaryPe != null -> "${peH50}% • ${primaryPe}×"
                                primaryPe != null -> "${primaryPe}×"
                                peH50 != null -> "${peH50}%"
                                else -> "N/A"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = if (hasAnyVal) ValuationPePink else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        if (hasAnyVal) {
                            Text(
                                text = report.kinetics.absolutePeRegime,
                                style = MaterialTheme.typography.labelSmall,
                                color = absColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Surface(
                color = ElevatedCardSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = "Forward trajectory",
                        tint = CyanLiquid,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Text(
                        text = report.forwardTrajectoryForecast,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiHolePositionRadarCard(report: DeadlyComboReport) {
    val cr = report.currentReading

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_hole_radar_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "MULTI-HOLE POSITION MATRIX (ALL VALVE LEVELS)",
                style = MaterialTheme.typography.labelSmall,
                color = CyanLiquid,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Comparing current constituent pressure across benchmark valves & extreme bands against historical cycle turning thresholds.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HoleRowItem(
                    holeName = "Hole 75% Valve (Master Top Sieve)",
                    currentVal = "${cr.hole75}%",
                    bottomRange = "≤ 1.5%",
                    peakRange = "≥ 58.5%",
                    status = if (cr.hole75 >= 58.5) "PEAK DANGER" else if (cr.hole75 <= 1.5) "WASHOUT" else "SAFE HEADROOM",
                    statusColor = if (cr.hole75 >= 58.5) CoralOverheated else if (cr.hole75 <= 1.5) CapitulationPurple else EmeraldBullish
                )
                HoleRowItem(
                    holeName = "Hole 50% Valve (Main Tank Equilibrium)",
                    currentVal = "${cr.hole50}%",
                    bottomRange = "≤ 7.5%",
                    peakRange = "≥ 73.8%",
                    status = if (cr.hole50 >= 73.8) "OVERHEATED" else if (cr.hole50 <= 7.5) "CAPITULATION" else "ACCUMULATING",
                    statusColor = if (cr.hole50 >= 73.8) CoralOverheated else if (cr.hole50 <= 7.5) CapitulationPurple else CyanLiquid
                )
                HoleRowItem(
                    holeName = "Hole 25% Valve (Master Bottom Sieve)",
                    currentVal = "${cr.hole25}%",
                    bottomRange = "≤ 20.0%",
                    peakRange = "≥ 81.4%",
                    status = if (cr.hole25 <= 20.0) "CAPITULATION FLOOR" else if (cr.hole25 >= 81.4) "EUPHORIC" else "RECOVERED",
                    statusColor = if (cr.hole25 <= 20.0) CapitulationPurple else if (cr.hole25 >= 81.4) CoralOverheated else EmeraldBullish
                )
                HoleRowItem(
                    holeName = "Hole 15% Valve (Deep Value Reservoir)",
                    currentVal = "${cr.hole15}%",
                    bottomRange = "≤ 26.0%",
                    peakRange = "≥ 84.5%",
                    status = if (cr.hole15 <= 26.0) "DEEP WASHOUT" else "SUPPORT HOLDING",
                    statusColor = if (cr.hole15 <= 26.0) CapitulationPurple else EmeraldBullish
                )
                HoleRowItem(
                    holeName = "Nifty 50 vs Smallcap 250 H75 Spread",
                    currentVal = "${if (report.largeSmallH75Divergence >= 0) "+" else ""}${report.largeSmallH75Divergence}%",
                    bottomRange = "≤ +5.0%",
                    peakRange = "≥ +22.0%",
                    status = if (report.largeSmallH75Divergence >= 22.0 && report.s250Hole75 <= 30.0) "STEALTH TRAP" else "SYNCHRONIZED",
                    statusColor = if (report.largeSmallH75Divergence >= 22.0 && report.s250Hole75 <= 30.0) CoralOverheated else EmeraldBullish
                )
                val peTank = report.kinetics.peTankLevel
                val primaryPe = report.kinetics.primaryAbsolutePe
                val eqWtMed = report.kinetics.medianPe
                val peP25 = report.kinetics.medianPeP25
                val peP75 = report.kinetics.medianPeP75
                val pePctile = report.kinetics.medianPeHistoricalPercentile
                val consPctile = report.kinetics.indexPeConsolidatedPercentile
                val absRegime = report.kinetics.absolutePeRegime
                HoleRowItem(
                    holeName = if (report.kinetics.isPointInTimeConstituentPeTank) {
                        "Valuation P/E Tank (52W Normalized Hole 50% • Point-in-Time)"
                    } else {
                        "Valuation P/E Tank (52W Normalized Hole 50%)"
                    },
                    currentVal = if (peTank != null) "${peTank}%" else "N/A",
                    bottomRange = "≤ 15.0%",
                    peakRange = "≥ 55.0%",
                    status = when {
                        peTank == null -> "PRE-2007 N/A"
                        peTank <= 15.0 -> "DEEP COMPRESSION"
                        peTank < 25.0 -> "12M COMPRESSED"
                        peTank >= 55.0 -> "FROTHY MULTIPLES"
                        else -> "BALANCED"
                    },
                    statusColor = when {
                        peTank == null -> TextMuted
                        peTank >= 55.0 -> CoralOverheated
                        else -> ValuationPePink
                    }
                )
                val absRowTitle = if (report.kinetics.isOfficialIndexPeBenchmark) {
                    val eqNote = if (eqWtMed != null) " • Eq-Wt Med ${eqWtMed}×" else ""
                    "Official NSE Index P/E (Zero-Bias Point-in-Time$eqNote)"
                } else {
                    "Absolute Median P/E (20Y Historical Band)"
                }
                HoleRowItem(
                    holeName = absRowTitle,
                    currentVal = if (primaryPe != null) {
                        val pctSuffix = when {
                            pePctile != null && consPctile != null -> " (${pePctile}%/${consPctile}%C)"
                            pePctile != null -> " (${pePctile}%ile)"
                            else -> ""
                        }
                        "${primaryPe}×$pctSuffix"
                    } else "N/A",
                    bottomRange = peP25?.let { "≤ ${it}×" } ?: "≤ 18.0×",
                    peakRange = peP75?.let { "≥ ${it}×" } ?: "≥ 29.0×",
                    status = if (primaryPe == null) "PRE-1999 N/A" else absRegime.uppercase(),
                    statusColor = when (absRegime) {
                        "Deep Value", "Attractive Value" -> EmeraldBullish
                        "Fair Value" -> CyanLiquid
                        "Moderately Elevated" -> BreakoutAmber
                        "Expensive / Frothy" -> CoralOverheated
                        else -> TextMuted
                    }
                )
            }
        }
    }
}

@Composable
private fun HoleRowItem(
    holeName: String,
    currentVal: String,
    bottomRange: String,
    peakRange: String,
    status: String,
    statusColor: Color
) {
    Surface(
        color = ElevatedCardSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holeName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Capitulation: $bottomRange • Peak Climax: $peakRange",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentVal,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeadlyPeakChecklistCard(report: DeadlyComboReport) {
    val activeCount = report.peakRulesChecklist.count { it.isDataAvailable && it.isTriggered }
    val availableCount = report.peakAvailableFactors.coerceAtLeast(1)
    val isPartial = availableCount < report.peakTotalFactors
    val criticalMin = when {
        availableCount >= 9 -> 7
        availableCount == 8 -> 6
        else -> 5
    }

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isPartial) BreakoutAmber.copy(alpha = 0.45f) else HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deadly_peak_checklist_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                        contentDescription = "9-Factor Peak Sniper",
                        tint = CoralOverheated,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isPartial) {
                            "${availableCount}/${report.peakTotalFactors}-FACTOR PEAK SNIPER"
                        } else {
                            "9-FACTOR SECULAR PEAK SNIPER"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralOverheated,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = if (activeCount >= criticalMin) CoralOverheated.copy(alpha = 0.18f)
                    else if (isPartial) BreakoutAmber.copy(alpha = 0.16f)
                    else EmeraldBullish.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isPartial) {
                            "$activeCount/$availableCount (${availableCount}/9 Avail)"
                        } else {
                            "$activeCount / $availableCount Active"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeCount >= criticalMin) CoralOverheated
                        else if (isPartial) BreakoutAmber
                        else EmeraldBullish,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = if (isPartial) {
                    "Partial-factor historical mode (${availableCount}/9 genuine factors available; pre-inception VIX/MMI marked N/A and excluded from denominator, normalized proportionally to 0–100)."
                } else {
                    "Requires Factor #1 + ≥8/9 total confluence. Pinpointed every secular cycle top within 2–4 sessions (Sept 2016, Jan 2018, Sept 2018, Oct 2021, Dec 2022, Sept/Oct 2024) while ignoring mid-bull shakeouts (Dec 2023 – Aug 2024)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.peakRulesChecklist.forEach { rule ->
                    ChecklistRuleItem(rule = rule, activeColor = CoralOverheated)
                }
            }
        }
    }
}

@Composable
private fun DeadlyBottomChecklistCard(report: DeadlyComboReport) {
    val activeCount = report.bottomRulesChecklist.count { it.isDataAvailable && it.isTriggered }
    val availableCount = report.bottomAvailableFactors.coerceAtLeast(1)
    val isPartial = availableCount < report.bottomTotalFactors
    val criticalMin = when {
        availableCount >= 9 -> 7
        availableCount == 8 -> 6
        else -> 5
    }

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isPartial) BreakoutAmber.copy(alpha = 0.45f) else HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deadly_bottom_checklist_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.TrendingDown,
                        contentDescription = "9-Factor Trough Sniper",
                        tint = CapitulationPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isPartial) {
                            "${availableCount}/${report.bottomTotalFactors}-FACTOR TROUGH SNIPER"
                        } else {
                            "9-FACTOR CAPITULATION TROUGH SNIPER"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = CapitulationPurple,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = if (activeCount >= criticalMin) CapitulationPurple.copy(alpha = 0.2f)
                    else if (isPartial) BreakoutAmber.copy(alpha = 0.16f)
                    else CyanLiquid.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when {
                            activeCount >= criticalMin -> "$activeCount/$availableCount SNIPER ON"
                            isPartial -> "$activeCount/$availableCount (${availableCount}/9 Avail)"
                            else -> "$activeCount/$availableCount • RECOVERING"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeCount >= criticalMin) CapitulationPurple
                        else if (isPartial) BreakoutAmber
                        else CyanLiquid,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = if (isPartial) {
                    "Partial-factor historical mode (${availableCount}/9 genuine factors available). Missing pre-inception VIX/MMI rules are shown as N/A and omitted from the normalized Sniper score."
                } else {
                    "Combines Hole 25%/50% washout with Fall Deceleration, ≥65% A/D Breadth Thrust, MMI Fear, and VIX Stabilization to pinpoint exact V-bottom inflection dates (27-Dec-2016, 29-Oct-2018, 25-Mar-2020, 23-Jun-2022, 29-Mar-2023, 04-Mar-2025, 26-Oct-2026)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.bottomRulesChecklist.forEach { rule ->
                    ChecklistRuleItem(rule = rule, activeColor = CapitulationPurple)
                }
            }
        }
    }
}

@Composable
private fun ChecklistRuleItem(rule: ComboChecklistRule, activeColor: Color) {
    val isAvailable = rule.isDataAvailable
    val isMet = isAvailable && rule.isTriggered
    Surface(
        color = ElevatedCardSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            when {
                !isAvailable -> HairlineBorder.copy(alpha = 0.55f)
                isMet -> activeColor.copy(alpha = 0.45f)
                else -> HairlineBorder
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isMet) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = when {
                        !isAvailable -> "Factor unavailable for date"
                        isMet -> "Rule met"
                        else -> "Rule not met"
                    },
                    tint = if (isMet) activeColor else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rule.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                !isAvailable -> TextMuted
                                isMet -> TextPrimary
                                else -> TextSecondary
                            },
                            fontWeight = if (isMet) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isAvailable) rule.currentValueFormatted else "N/A",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = when {
                                !isAvailable -> TextMuted
                                isMet -> activeColor
                                else -> CyanLiquid
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isAvailable) rule.conditionDesc else "${rule.conditionDesc} • Excluded (pre-inception)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            if (isAvailable) {
                LinearProgressIndicator(
                    progress = { (rule.proximityPct / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (isMet) activeColor else CyanLiquid.copy(alpha = 0.6f),
                    trackColor = GlassCardSurface
                )
            }
        }
    }
}

@Composable
private fun HistoricalLedgerHeader(
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    peakCount: Int,
    bottomCount: Int,
    hasPre2016Episodes: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (hasPre2016Episodes) {
                "EMPIRICAL 9-FACTOR SNIPER PROVENANCE LEDGER (1995–2026)"
            } else {
                "EMPIRICAL 9-FACTOR SNIPER PROVENANCE LEDGER (2016–2026)"
            },
            style = MaterialTheme.typography.labelSmall,
            color = CyanLiquid,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = selectedFilter == "TOPS",
                onClick = { onFilterChange("TOPS") },
                label = { Text("Secular Tops ($peakCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoralOverheated.copy(alpha = 0.16f),
                    selectedLabelColor = CoralOverheated
                )
            )
            FilterChip(
                selected = selectedFilter == "BOTTOMS",
                onClick = { onFilterChange("BOTTOMS") },
                label = { Text("Capitulation Troughs ($bottomCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CapitulationPurple.copy(alpha = 0.16f),
                    selectedLabelColor = CapitulationPurple
                )
            )
        }
    }
}

@Composable
private fun HistoricalComboEpisodeCard(episode: HistoricalComboEpisode) {
    val accentColor = if (episode.isPeak) CoralOverheated else CapitulationPurple
    val isPartialEpisode = episode.activeFactors < episode.totalFactors

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isPartialEpisode) BreakoutAmber.copy(alpha = 0.4f) else HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPartialEpisode) {
                        Surface(
                            color = BreakoutAmber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, BreakoutAmber.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "${episode.activeFactors}/${episode.totalFactors} Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = BreakoutAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = episode.typeDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                text = "Exact Cycle Extreme: ${episode.peakOrTroughDate} • Sniper Fired: ${episode.sniperTriggerDate.ifBlank { episode.peakOrTroughDate }} (${episode.sniperScoreText})",
                style = MaterialTheme.typography.labelSmall,
                color = CyanLiquid,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    label = if (episode.isPeak) "Hole 75%" else "Hole 25%",
                    value = if (episode.isPeak) "${episode.hole75Pct}%" else "${episode.hole25Pct}%",
                    color = accentColor
                )
                val epAbsPe = episode.displayAbsolutePe
                MetricColumn(
                    label = if (episode.indexPe != null) "Val / Idx P/E" else "Val / Med P/E",
                    value = when {
                        episode.peTankLevel != null && epAbsPe != null ->
                            "${episode.peTankLevel}% / ${epAbsPe}×"
                        epAbsPe != null -> "N/A / ${epAbsPe}×"
                        episode.peTankLevel != null -> "${episode.peTankLevel}%"
                        else -> "N/A"
                    },
                    color = if (episode.peTankLevel != null || epAbsPe != null) ValuationPePink else TextMuted
                )
                MetricColumn(
                    label = "VIX",
                    value = episode.indiaVix?.toString() ?: "N/A",
                    color = if (episode.indiaVix != null) TextPrimary else TextMuted
                )
                MetricColumn(
                    label = "MMI",
                    value = episode.mmiValue?.toString() ?: "N/A",
                    color = if (episode.mmiValue != null) BreakoutAmber else TextMuted
                )
                MetricColumn(
                    label = "A/D",
                    value = "${episode.adPct}%",
                    color = if (episode.adPct >= 60.0) EmeraldBullish else CoralOverheated
                )
                if (episode.forward6MReturnPct != null) {
                    MetricColumn(
                        label = "Fwd 6M",
                        value = "${if (episode.forward6MReturnPct > 0) "+" else ""}${episode.forward6MReturnPct}%",
                        color = if (episode.forward6MReturnPct > 0) EmeraldBullish else CoralOverheated
                    )
                }
            }

            Text(
                text = episode.contextNote,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetBrainsMonoFamily,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
