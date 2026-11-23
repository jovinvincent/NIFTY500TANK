package com.example.depthtank.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material3.TextButton
import com.example.depthtank.data.ArchiveHydrationState
import com.example.depthtank.data.DataQuality
import com.example.depthtank.data.SectorTankSummary
import com.example.depthtank.data.TankUiState
import com.example.ui.theme.BreakoutAmber
import com.example.ui.theme.CapitulationPurple
import com.example.ui.theme.CoralOverheated
import com.example.ui.theme.CyanLiquid
import com.example.ui.theme.DeepSlateWater
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.RoyalBlueLiquid
import com.example.ui.theme.SubtleDivider
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AnalyticsSyncScreen(
    uiState: TankUiState,
    onForceRefresh: () -> Unit,
    onRetryArchive: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_sync_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Market Breadth Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${uiState.selectedUniverse.shortLabel} (${uiState.activeStocksCount} Base) • 4-Pillar attribution & sector overflow",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        item {
            BreadthDistributionCard(uiState = uiState)
        }

        item {
            BreakoutPulseAttributionCard(uiState = uiState)
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sector_breakdown_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SECTOR TANK BREAKDOWN",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Avg 52W Score / Overflow Share",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.sectorSummaries.forEach { sector ->
                        SectorTankRow(summary = sector)
                    }
                }
            }
        }

        item {
            AutomatedPipelineCard(
                uiState = uiState,
                onForceRefresh = onForceRefresh,
                onRetryArchive = onRetryArchive
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BreadthDistributionCard(uiState: TankUiState) {
    val pillars = uiState.fourPillarSnapshot
    val total = uiState.activeStocksCount.coerceAtLeast(1)
    val euphoriaCount = pillars.euphoriaPressureCount
    val midFlowingCount = (uiState.flowingCount - euphoriaCount).coerceAtLeast(0)
    val capCount = pillars.capitulationCount
    val rechargingCount = (uiState.belowHoleCount - capCount).coerceAtLeast(0)

    val euphoriaFrac = euphoriaCount.toFloat() / total
    val midFrac = midFlowingCount.toFloat() / total
    val rechargingFrac = rechargingCount.toFloat() / total
    val capFrac = capCount.toFloat() / total

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("breadth_distribution_card")
    ) {
        Text(
            text = "FOUR-TIER 52-WEEK BREADTH DISTRIBUTION",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(ElevatedCardSurface)
        ) {
            if (euphoriaFrac > 0f) {
                Box(
                    modifier = Modifier
                        .weight(euphoriaFrac)
                        .height(14.dp)
                        .background(CoralOverheated)
                )
            }
            if (midFrac > 0f) {
                Box(
                    modifier = Modifier
                        .weight(midFrac)
                        .height(14.dp)
                        .background(CyanLiquid)
                )
            }
            if (rechargingFrac > 0f) {
                Box(
                    modifier = Modifier
                        .weight(rechargingFrac)
                        .height(14.dp)
                        .background(DeepSlateWater)
                )
            }
            if (capFrac > 0f) {
                Box(
                    modifier = Modifier
                        .weight(capFrac)
                        .height(14.dp)
                        .background(CapitulationPurple)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DistributionLegendItem("Euphoria >85", euphoriaCount, CoralOverheated)
            DistributionLegendItem("Flow 50–85", midFlowingCount, CyanLiquid)
            DistributionLegendItem("Recharge 15–50", rechargingCount, TextSecondary)
            DistributionLegendItem("Cap <15", capCount, CapitulationPurple)
        }
    }
}

@Composable
private fun BreakoutPulseAttributionCard(uiState: TankUiState) {
    val pillars = uiState.fourPillarSnapshot
    val deltaColor = if (pillars.breakoutPulseDelta20 >= 0.0) EmeraldBullish else CoralOverheated

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("breakout_pulse_attribution_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BREAKOUT PULSE & REGIME TELEMETRY",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
            InstitutionalRegimePill(regime = uiState.institutionalRegime)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AttributionStatBox(
                label = "52-Week Breakouts",
                value = "${pillars.breakoutPulseCount} (${String.format(Locale.US, "%.1f%%", pillars.breakoutPulsePct)})",
                subLabel = "Today High > Prior 52W High",
                accent = BreakoutAmber,
                modifier = Modifier.weight(1f)
            )
            AttributionStatBox(
                label = "20D Pulse Change",
                value = formatSignedPercent(pillars.breakoutPulseDelta20),
                subLabel = "Plateau: ${pillars.plateauDays}d >75%",
                accent = deltaColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AttributionStatBox(
    label: String,
    value: String,
    subLabel: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ElevatedCardSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(text = subLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun DistributionLegendItem(label: String, count: Int, color: Color) {
    Column {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun SectorTankRow(summary: SectorTankSummary) {
    val barColor = when {
        summary.avgScore >= 75.0 -> EmeraldBullish
        summary.avgScore >= 50.0 -> CyanLiquid
        else -> DeepSlateWater
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                    text = summary.sector,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = "${summary.flowingCount}/${summary.stockCount} flowing",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", summary.avgScore),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (summary.avgScore >= 50.0) CyanLiquid else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format(Locale.US, "%.1f%% share", summary.overflowSharePct),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(ElevatedCardSurface)
        ) {
            val fillFrac = (summary.avgScore / 100.0).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFrac)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(RoyalBlueLiquid, barColor)
                        )
                    )
            )
        }
    }
}

@Composable
private fun AutomatedPipelineCard(
    uiState: TankUiState,
    onForceRefresh: () -> Unit,
    onRetryArchive: () -> Unit = {}
) {
    val status = uiState.pipelineStatus
    val integrity = uiState.datasetIntegrity
    val historyIntegrity = uiState.historyIntegrity
    val archiveState = uiState.archiveHydrationState
    val archiveSummary = uiState.archiveSummary
    val isVerifiedLive = integrity.dataQuality == DataQuality.VERIFIED_EOD && !integrity.isIllustrative
    val badgeColor = if (isVerifiedLive) EmeraldBullish else BreakoutAmber

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("automated_pipeline_status_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DATA PROVENANCE & PIPELINE STATUS",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = badgeColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
            ) {
                Text(
                    text = if (isVerifiedLive) "VERIFIED EOD" else "ILLUSTRATIVE ARCHIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PipelineStatusLine(
            icon = Icons.Rounded.Schedule,
            label = "Daily EOD Schedule",
            value = status.scheduleDisplay
        )
        Spacer(modifier = Modifier.height(8.dp))
        PipelineStatusLine(
            icon = Icons.Rounded.CloudDone,
            label = "Last Verified Trading Date",
            value = "${formatDisplayDate(integrity.lastVerifiedTradingDate)} • ${uiState.activeStocksCount}/${uiState.selectedUniverse.targetCount} ${uiState.selectedUniverse.shortLabel} (${uiState.selectedUniverse.rangeLabel})"
        )
        Spacer(modifier = Modifier.height(8.dp))
        PipelineStatusLine(
            icon = Icons.Rounded.VerifiedUser,
            label = "Dataset Integrity & Coverage",
            value = "${uiState.selectedUniverse.shortLabel}: ${integrity.dataQuality.wireValue} (${(integrity.constituentCoverage * 100).roundToInt()}%) • History: ${historyIntegrity.dataQuality.wireValue} • v${integrity.modelVersion}/${integrity.calculationVersion}"
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pre2016_archive_status_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                val archiveDesc = when (archiveState) {
                    ArchiveHydrationState.READY -> {
                        val cacheTag = if (archiveSummary.loadedFromCache) "Local Cache Hit" else "Gzip Decompressed"
                        val shaTag = if (archiveSummary.checksumVerified) "SHA-256 Verified" else "Unverified"
                        "${formatDisplayDate(archiveSummary.startDate)} → ${formatDisplayDate(archiveSummary.endDate)} (${archiveSummary.pre2016SessionCount} sessions • $shaTag • $cacheTag)"
                    }
                    ArchiveHydrationState.LOADING -> "Decompressing & verifying SHA-256 for 1995–2016 NSE Bhavcopy archive..."
                    ArchiveHydrationState.FAILED -> "Archive hydration failed: ${archiveSummary.errorMessage ?: "Checksum mismatch"}"
                    ArchiveHydrationState.NOT_LOADED -> "Lazy .json.gz archive ready (loads automatically on 10Y / 20Y / MAX)"
                }
                PipelineStatusLine(
                    icon = Icons.Rounded.Archive,
                    label = "Pre-2016 NSE Archive (1995–2016)",
                    value = archiveDesc
                )
            }
            if (archiveState == ArchiveHydrationState.FAILED) {
                TextButton(
                    onClick = onRetryArchive,
                    modifier = Modifier.testTag("analytics_retry_archive_button")
                ) {
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanLiquid,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (status.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = CoralOverheated
            )
        }

        HorizontalDivider(
            color = SubtleDivider,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Button(
            onClick = onForceRefresh,
            enabled = !status.isSyncing,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanLiquid,
                contentColor = ObsidianBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("force_refresh_button")
        ) {
            if (status.isSyncing) {
                CircularProgressIndicator(
                    color = ObsidianBackground,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Syncing ${uiState.selectedUniverse.shortLabel} EOD Feed...", fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Force Refresh Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PipelineStatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyanLiquid,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(text = value, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }
    }
}
