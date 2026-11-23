package com.example.depthtank.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.depthtank.data.ArchiveHydrationState
import com.example.depthtank.data.ChartAggregation
import com.example.depthtank.data.MarketCycleTurningPoint
import com.example.depthtank.data.TankCalculator
import com.example.depthtank.data.TankHistoryPoint
import com.example.depthtank.data.TimeframeOption
import com.example.ui.theme.BreakoutAmber
import com.example.ui.theme.CapitulationPurple
import com.example.ui.theme.CoralOverheated
import com.example.ui.theme.CyanLiquid
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
import com.example.ui.theme.ValuationPePink
import java.util.Locale
import java.util.TreeSet
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class TurningPointRole {
    PEAK,
    BOTTOM
}

private data class ChartTurningPointMatch(
    val cycle: MarketCycleTurningPoint,
    val role: TurningPointRole
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoricalDepthChartCard(
    history: List<TankHistoryPoint>,
    selectedTimeframe: TimeframeOption,
    onSelectTimeframe: (TimeframeOption) -> Unit,
    holeThreshold: Float = 50f,
    detectedCycles: List<MarketCycleTurningPoint> = emptyList(),
    selectedAggregation: ChartAggregation = ChartAggregation.DAILY,
    onSelectAggregation: (ChartAggregation) -> Unit = {},
    archiveHydrationState: ArchiveHydrationState = ArchiveHydrationState.READY,
    onRetryArchive: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panFraction by remember { mutableFloatStateOf(1f) }
    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }

    var showMainTank by remember { mutableStateOf(true) }
    var showValuationPe by remember { mutableStateOf(true) }
    var showEuphoria by remember { mutableStateOf(false) }
    var showBreakoutPulse by remember { mutableStateOf(false) }
    var showCapitulation by remember { mutableStateOf(false) }
    var displayEmaSmoothed by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTimeframe, selectedAggregation, history.size) {
        zoomScale = 1f
        panFraction = 1f
        scrubbedIndex = null
    }

    val visibleHistory = remember(history, zoomScale, panFraction) {
        if (history.size <= 2 || zoomScale <= 1.01f) {
            history
        } else {
            val total = history.size
            val windowSize = (total / zoomScale).roundToInt().coerceIn(12, total)
            val maxStart = (total - windowSize).coerceAtLeast(0)
            val start = (panFraction * maxStart).roundToInt().coerceIn(0, maxStart)
            history.subList(start, (start + windowSize).coerceAtMost(total))
        }
    }

    val turningPointByVisibleIndex = remember(visibleHistory, detectedCycles, selectedAggregation) {
        if (visibleHistory.isEmpty() || detectedCycles.isEmpty()) {
            emptyMap()
        } else {
            val dateToIdx = HashMap<String, Int>(visibleHistory.size * 2)
            visibleHistory.forEachIndexed { idx, pt -> dateToIdx[pt.date] = idx }
            val firstDate = visibleHistory.first().date
            val lastDate = visibleHistory.last().date

            fun findIndexForDate(targetDate: String): Int? {
                dateToIdx[targetDate]?.let { return it }
                if (selectedAggregation == ChartAggregation.DAILY) return null
                if (targetDate < firstDate || targetDate > lastDate) return null
                // Binary search for first aggregated bar on or after targetDate
                var lo = 0
                var hi = visibleHistory.lastIndex
                while (lo < hi) {
                    val mid = (lo + hi) ushr 1
                    if (visibleHistory[mid].date < targetDate) lo = mid + 1 else hi = mid
                }
                return lo
            }

            val map = LinkedHashMap<Int, ChartTurningPointMatch>()
            for (cycle in detectedCycles) {
                findIndexForDate(cycle.exactPeakDate)?.let { idx ->
                    map[idx] = ChartTurningPointMatch(cycle, TurningPointRole.PEAK)
                }
                findIndexForDate(cycle.exactBottomDate)?.let { idx ->
                    map[idx] = ChartTurningPointMatch(cycle, TurningPointRole.BOTTOM)
                }
            }
            map
        }
    }

    val hasColdStartPoints = remember(visibleHistory) { visibleHistory.any { it.isIncompleteColdStart } }
    val hasReconstructedPoints = remember(visibleHistory) { visibleHistory.any { it.isReconstructedHistorical } }
    val hasPre2007MissingPe = remember(visibleHistory) {
        visibleHistory.any { !it.isIncompleteColdStart && it.peTankLevel == null } &&
            visibleHistory.any { it.peTankLevel != null }
    }

    val sampledPoints = remember(visibleHistory, turningPointByVisibleIndex) {
        downsampleHistoryWithIndices(
            points = visibleHistory,
            maxPoints = 320,
            mandatoryIndices = turningPointByVisibleIndex.keys
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (zoomScale * zoomChange).coerceIn(1f, 8f)
        zoomScale = nextZoom
        if (nextZoom > 1.01f) {
            panFraction = (panFraction - (panChange.x / 600f) / nextZoom).coerceIn(0f, 1f)
        } else {
            panFraction = 1f
        }
    }

    val textMeasurer = rememberTextMeasurer()

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("historical_depth_chart_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MULTI-SERIES BREADTH CYCLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (abs(holeThreshold - 50f) > 0.5f) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = CyanLiquid.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, CyanLiquid.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "Hole: ${holeThreshold.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanLiquid,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (visibleHistory.isNotEmpty()) {
                        "${formatDisplayDate(visibleHistory.first().date)} → ${formatDisplayDate(visibleHistory.last().date)}"
                    } else {
                        "01-Apr-2016 → Present"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (displayEmaSmoothed) CyanLiquid.copy(alpha = 0.16f) else ElevatedCardSurface,
                    border = BorderStroke(
                        1.dp,
                        if (displayEmaSmoothed) CyanLiquid.copy(alpha = 0.6f) else HairlineBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { displayEmaSmoothed = !displayEmaSmoothed }
                        .testTag("chart_ema_smooth_toggle")
                ) {
                    Text(
                        text = if (displayEmaSmoothed) "EMA Smooth: ON" else "EMA Smooth",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (displayEmaSmoothed) CyanLiquid else TextSecondary,
                        fontWeight = if (displayEmaSmoothed) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (zoomScale > 1.05f) {
                    TextButton(
                        onClick = {
                            zoomScale = 1f
                            panFraction = 1f
                            scrubbedIndex = null
                        },
                        modifier = Modifier.testTag("reset_chart_zoom_button")
                    ) {
                        Text(
                            text = String.format(Locale.US, "Reset %.1fx", zoomScale),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanLiquid
                        )
                    }
                }
            }
        }

        if (displayEmaSmoothed) {
            Text(
                text = "EMA Visual Smoothing active (Display Only • Raw Daily Extrema & Turning Points Intact)",
                style = MaterialTheme.typography.labelSmall,
                color = CyanLiquid.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Interactive Timeframe Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeframeOption.entries.forEach { option ->
                val isSelected = option == selectedTimeframe
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectTimeframe(option) },
                    label = {
                        Text(
                            text = option.label,
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
                    modifier = Modifier.testTag("timeframe_chip_${option.name.lowercase(Locale.US)}")
                )
            }
        }

        // Daily / Weekly / Monthly Bar Aggregation Controls (derived strictly from daily bars)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "BARS",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
            ChartAggregation.entries.forEach { mode ->
                val isSelected = mode == selectedAggregation
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectAggregation(mode) },
                    label = {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldBullish.copy(alpha = 0.16f),
                        selectedLabelColor = EmeraldBullish,
                        containerColor = ElevatedCardSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = HairlineBorder,
                        selectedBorderColor = EmeraldBullish.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.testTag("aggregation_chip_${mode.name.lowercase(Locale.US)}")
                )
            }
        }

        // Lazy Pre-2016 Archive Hydration Banner
        if (selectedTimeframe.requiresPre2016Archive && archiveHydrationState == ArchiveHydrationState.LOADING) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CyanLiquid.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, CyanLiquid.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("archive_loading_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = CyanLiquid,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Decompressing verified 1995–2016 NSE Bhavcopy archive...",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanLiquid
                    )
                }
            }
        } else if (selectedTimeframe.requiresPre2016Archive && archiveHydrationState == ArchiveHydrationState.FAILED) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CoralOverheated.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, CoralOverheated.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("archive_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Archive error",
                            tint = CoralOverheated,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Pre-2016 archive unavailable — showing verified 2016–Present data.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoralOverheated
                        )
                    }
                    TextButton(
                        onClick = onRetryArchive,
                        modifier = Modifier.testTag("archive_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Retry pre-2016 archive",
                            tint = CyanLiquid,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanLiquid,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Honest Provenance Disclosure for Reconstructed, Cold-Start & Pre-2007 Valuation P/E Periods
        if (hasColdStartPoints || hasReconstructedPoints || (showValuationPe && hasPre2007MissingPe)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BreakoutAmber.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, BreakoutAmber.copy(alpha = 0.42f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("provenance_notice_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Historical provenance notice",
                        tint = BreakoutAmber,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = buildString {
                            if (hasColdStartPoints) {
                                append("Jan–Aug 1995: <200D NSE cold-start (no fabricated lookback). ")
                            }
                            if (hasReconstructedPoints) {
                                append("Aug 1995–Jul 1998: Top-500 liquidity-reconstructed from point-in-time NSE EQ Bhavcopies (including delisted/merged stocks) prior to NSE Nifty 500 launch. ")
                            }
                            if (showValuationPe && hasPre2007MissingPe) {
                                append("Valuation P/E normalized 0–100 history begins Jul 2007 (earlier dates shown as N/A).")
                            }
                        }.trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = BreakoutAmber
                    )
                }
            }
        }

        // Independent Multi-Series Visibility Toggles
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SeriesToggleChip(
                label = "Main Tank",
                color = CyanLiquid,
                selected = showMainTank,
                onClick = { showMainTank = !showMainTank },
                testTagName = "series_toggle_main_tank"
            )
            SeriesToggleChip(
                label = "Valuation P/E",
                color = ValuationPePink,
                selected = showValuationPe,
                onClick = { showValuationPe = !showValuationPe },
                testTagName = "series_toggle_valuation_pe"
            )
            SeriesToggleChip(
                label = "Euphoria >85",
                color = CoralOverheated,
                selected = showEuphoria,
                onClick = { showEuphoria = !showEuphoria },
                testTagName = "series_toggle_euphoria"
            )
            SeriesToggleChip(
                label = "Breakout Pulse",
                color = BreakoutAmber,
                selected = showBreakoutPulse,
                onClick = { showBreakoutPulse = !showBreakoutPulse },
                testTagName = "series_toggle_breakout"
            )
            SeriesToggleChip(
                label = "Capitulation <15",
                color = CapitulationPurple,
                selected = showCapitulation,
                onClick = { showCapitulation = !showCapitulation },
                testTagName = "series_toggle_capitulation"
            )
        }

        // Floating Tooltip Banner when scrubbing or legend hint
        val activePoint = scrubbedIndex?.let { idx -> visibleHistory.getOrNull(idx) }
        val activeCycleMatch = remember(scrubbedIndex, turningPointByVisibleIndex) {
            val idx = scrubbedIndex ?: return@remember null
            turningPointByVisibleIndex[idx] ?: (1..2).firstNotNullOfOrNull { delta ->
                turningPointByVisibleIndex[idx - delta] ?: turningPointByVisibleIndex[idx + delta]
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (activePoint != null) {
            ChartFloatingTooltip(
                point = activePoint,
                holeThreshold = holeThreshold,
                turningPoint = activeCycleMatch,
                selectedAggregation = selectedAggregation
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap or scrub to inspect • Pinch to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThresholdLegendDot("Peak", CoralOverheated)
                    ThresholdLegendDot("Bottom", EmeraldBullish)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(242.dp)
                .transformable(state = transformState)
                .pointerInput(visibleHistory) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoomScale = 1f
                            panFraction = 1f
                            scrubbedIndex = null
                        },
                        onTap = { offset ->
                            if (visibleHistory.isNotEmpty()) {
                                val leftPad = 36.dp.toPx()
                                val rightPad = 8.dp.toPx()
                                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                val frac = ((offset.x - leftPad) / plotW).coerceIn(0f, 1f)
                                scrubbedIndex = (frac * (visibleHistory.size - 1)).roundToInt()
                                    .coerceIn(0, visibleHistory.lastIndex)
                            }
                        }
                    )
                }
                .pointerInput(visibleHistory) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size == 1) {
                                val change = event.changes.first()
                                if (change.pressed && visibleHistory.isNotEmpty()) {
                                    val leftPad = 36.dp.toPx()
                                    val rightPad = 8.dp.toPx()
                                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                    val frac = ((change.position.x - leftPad) / plotW).coerceIn(0f, 1f)
                                    scrubbedIndex = (frac * (visibleHistory.size - 1)).roundToInt()
                                        .coerceIn(0, visibleHistory.lastIndex)
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (visibleHistory.isEmpty() || sampledPoints.isEmpty()) return@Canvas

                val leftPad = 36.dp.toPx()
                val rightPad = 8.dp.toPx()
                val topPad = 12.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val plotWidth = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                fun yForValue(pct: Double): Float {
                    val clamped = pct.coerceIn(0.0, 100.0).toFloat()
                    return topPad + plotHeight * (1f - clamped / 100f)
                }

                // Subtle horizontal Y-axis gridlines
                val yTicks = listOf(0, 25, 50, 75, 100)
                for (tick in yTicks) {
                    val y = yForValue(tick.toDouble())
                    drawLine(
                        color = HairlineBorder.copy(alpha = 0.55f),
                        start = Offset(leftPad, y),
                        end = Offset(leftPad + plotWidth, y),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    val label = textMeasurer.measure(
                        text = "$tick%",
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    )
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(0f, (y - label.size.height / 2f).coerceAtLeast(0f))
                    )
                }

                // Dynamic Hole-Aware Overheated & Capitulation threshold lines
                val overheatedPct = TankCalculator.overheatedThresholdForHole(holeThreshold.toDouble())
                val capitulationPct = TankCalculator.capitulationThresholdForHole(holeThreshold.toDouble())
                val yOverheated = yForValue(overheatedPct)
                drawLine(
                    color = CoralOverheated.copy(alpha = 0.65f),
                    start = Offset(leftPad, yOverheated),
                    end = Offset(leftPad + plotWidth, yOverheated),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
                val yCapitulation = yForValue(capitulationPct)
                drawLine(
                    color = EmeraldBullish.copy(alpha = 0.6f),
                    start = Offset(leftPad, yCapitulation),
                    end = Offset(leftPad + plotWidth, yCapitulation),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )

                val totalVisibleDenom = (visibleHistory.size - 1).coerceAtLeast(1).toFloat()
                fun xForOriginalIndex(origIdx: Int): Float =
                    leftPad + (origIdx.toFloat() / totalVisibleDenom) * plotWidth

                fun buildSeriesOffsets(selector: (TankHistoryPoint) -> Double): List<Offset> {
                    val rawSeries = sampledPoints.map { (_, pt) -> selector(pt) }
                    val displaySeries = if (displayEmaSmoothed) computeDisplayEma(rawSeries, period = 10) else rawSeries
                    return sampledPoints.mapIndexed { idx, (origIdx, _) ->
                        Offset(xForOriginalIndex(origIdx), yForValue(displaySeries[idx]))
                    }
                }

                fun buildMainTankOffsets(): List<Offset> {
                    val rawSeries = sampledPoints.map { (_, pt) -> pt.tankLevel }
                    val displaySeries = if (displayEmaSmoothed) {
                        computeDisplayEma(rawSeries, period = 10)
                    } else if (sampledPoints.size > 40) {
                        val passes = if (holeThreshold >= 65f || holeThreshold <= 30f) 2 else 1
                        conditionCurvePreservingExtrema(
                            sampledPoints = sampledPoints,
                            pinnedOriginalIndices = turningPointByVisibleIndex.keys,
                            capitulationFloor = capitulationPct + 6.0,
                            overheatedCeiling = (overheatedPct - 6.0).coerceAtLeast(50.0),
                            passes = passes
                        )
                    } else {
                        rawSeries
                    }
                    return sampledPoints.mapIndexed { idx, (origIdx, _) ->
                        Offset(xForOriginalIndex(origIdx), yForValue(displaySeries[idx]))
                    }
                }

                // Draw secondary series behind Main Tank
                if (showCapitulation) {
                    val capOffsets = buildSeriesOffsets { it.capitulationDepthPct }
                    val capPath = buildCubicBezierPath(capOffsets)
                    drawPath(
                        path = capPath,
                        color = CapitulationPurple.copy(alpha = 0.9f),
                        style = Stroke(
                            width = 1.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                if (showBreakoutPulse) {
                    val bpOffsets = buildSeriesOffsets { it.breakoutPulsePct }
                    val bpPath = buildCubicBezierPath(bpOffsets)
                    drawPath(
                        path = bpPath,
                        color = BreakoutAmber.copy(alpha = 0.92f),
                        style = Stroke(
                            width = 1.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                if (showEuphoria) {
                    val eupOffsets = buildSeriesOffsets { it.euphoriaPressurePct }
                    val eupPath = buildCubicBezierPath(eupOffsets)
                    drawPath(
                        path = eupPath,
                        color = CoralOverheated.copy(alpha = 0.9f),
                        style = Stroke(
                            width = 1.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Main Tank primary series with soft gradient fill
                if (showMainTank) {
                    val tankOffsets = buildMainTankOffsets()
                    val tankLinePath = buildCubicBezierPath(tankOffsets)
                    if (tankOffsets.isNotEmpty()) {
                        val fillPath = Path().apply {
                            addPath(tankLinePath)
                            lineTo(tankOffsets.last().x, topPad + plotHeight)
                            lineTo(tankOffsets.first().x, topPad + plotHeight)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    CyanLiquid.copy(alpha = 0.28f),
                                    RoyalBlueLiquid.copy(alpha = 0.10f),
                                    Color.Transparent
                                ),
                                startY = topPad,
                                endY = topPad + plotHeight
                            )
                        )
                    }
                    drawPath(
                        path = tankLinePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(RoyalBlueLiquid, CyanLiquid)
                        ),
                        style = Stroke(
                            width = 2.4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Normalized 0–100 Valuation P/E Tank series (drawn strictly across non-null segments; pre-2007 left blank, never zeroed)
                if (showValuationPe) {
                    val segments = mutableListOf<MutableList<Pair<Int, Double>>>()
                    var currentSeg: MutableList<Pair<Int, Double>>? = null
                    for ((origIdx, pt) in sampledPoints) {
                        val peVal = pt.peTankLevel
                        if (peVal != null && peVal.isFinite()) {
                            if (currentSeg == null) {
                                currentSeg = mutableListOf()
                                segments.add(currentSeg)
                            }
                            currentSeg.add(origIdx to peVal)
                        } else {
                            currentSeg = null
                        }
                    }
                    for (seg in segments) {
                        if (seg.size == 1) {
                            val (origIdx, peVal) = seg.first()
                            drawCircle(
                                color = ValuationPePink,
                                radius = 2.5.dp.toPx(),
                                center = Offset(xForOriginalIndex(origIdx), yForValue(peVal))
                            )
                        } else if (seg.size >= 2) {
                            val rawVals = seg.map { it.second }
                            val displayVals = if (displayEmaSmoothed) computeDisplayEma(rawVals, period = 10) else rawVals
                            val segOffsets = seg.mapIndexed { idx, (origIdx, _) ->
                                Offset(xForOriginalIndex(origIdx), yForValue(displayVals[idx]))
                            }
                            val pePath = buildCubicBezierPath(segOffsets)
                            drawPath(
                                path = pePath,
                                color = ValuationPePink.copy(alpha = 0.94f),
                                style = Stroke(
                                    width = 2.1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                // Confirmed retrospective raw turning-point markers (Red = Exact Peak, Green = Exact Bottom)
                for ((origIdx, match) in turningPointByVisibleIndex) {
                    val rawPt = visibleHistory[origIdx]
                    val mx = xForOriginalIndex(origIdx)
                    val my = yForValue(rawPt.tankLevel)
                    val markerColor = if (match.role == TurningPointRole.PEAK) CoralOverheated else EmeraldBullish

                    drawCircle(
                        color = markerColor.copy(alpha = 0.25f),
                        radius = 7.dp.toPx(),
                        center = Offset(mx, my)
                    )
                    drawCircle(
                        color = markerColor,
                        radius = 3.8.dp.toPx(),
                        center = Offset(mx, my)
                    )
                    drawCircle(
                        color = ObsidianBackground,
                        radius = 1.5.dp.toPx(),
                        center = Offset(mx, my)
                    )
                }

                // X-axis date labels (Start, Mid, End)
                val xIndices = listOf(0, visibleHistory.size / 2, visibleHistory.lastIndex).distinct()
                for (xi in xIndices) {
                    val xFrac = xi.toFloat() / totalVisibleDenom
                    val label = textMeasurer.measure(
                        text = formatDisplayDate(visibleHistory[xi].date),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    )
                    val lx = (leftPad + xFrac * plotWidth - label.size.width / 2f)
                        .coerceIn(leftPad, leftPad + plotWidth - label.size.width)
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(lx, topPad + plotHeight + 6.dp.toPx())
                    )
                }

                // Scrubbed vertical cursor & raw readout dots for visible series
                val activeIdx = scrubbedIndex
                if (activeIdx != null && activeIdx in visibleHistory.indices) {
                    val pt = visibleHistory[activeIdx]
                    val sx = xForOriginalIndex(activeIdx)

                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(sx, topPad),
                        end = Offset(sx, topPad + plotHeight),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    val dots = buildList {
                        if (showMainTank) add(pt.tankLevel to CyanLiquid)
                        if (showValuationPe && pt.peTankLevel != null) add(pt.peTankLevel to ValuationPePink)
                        if (showEuphoria) add(pt.euphoriaPressurePct to CoralOverheated)
                        if (showBreakoutPulse) add(pt.breakoutPulsePct to BreakoutAmber)
                        if (showCapitulation) add(pt.capitulationDepthPct to CapitulationPurple)
                    }
                    for ((valPct, dotColor) in dots) {
                        val sy = yForValue(valPct)
                        drawCircle(
                            color = dotColor.copy(alpha = 0.28f),
                            radius = 7.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = dotColor,
                            radius = 3.8.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = ObsidianBackground,
                            radius = 1.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }
            }
        }

        // Dedicated horizontal pan slider when zoomed in
        if (zoomScale > 1.05f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scroll Timeline (${String.format(Locale.US, "%.1fx", zoomScale)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "${(panFraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = CyanLiquid
                    )
                }
                Slider(
                    value = panFraction,
                    onValueChange = {
                        panFraction = it.coerceIn(0f, 1f)
                        scrubbedIndex = null
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanLiquid,
                        activeTrackColor = CyanLiquid,
                        inactiveTrackColor = ElevatedCardSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chart_pan_slider")
                )
            }
        }
    }
}

@Composable
private fun SeriesToggleChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) color.copy(alpha = 0.16f) else ElevatedCardSurface,
        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.65f) else HairlineBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (selected) color else TextMuted.copy(alpha = 0.4f))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else TextMuted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ChartFloatingTooltip(
    point: TankHistoryPoint,
    holeThreshold: Float = 50f,
    turningPoint: ChartTurningPointMatch? = null,
    selectedAggregation: ChartAggregation = ChartAggregation.DAILY
) {
    val deltaColor = if (point.dailyDelta >= 0.0) EmeraldBullish else CoralOverheated
    val provenanceSuffix = when {
        point.isIncompleteColdStart -> " • <200D Cold Start"
        point.isReconstructedHistorical -> " • Reconstructed 1995–1998"
        else -> ""
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ElevatedCardSurface,
        border = BorderStroke(1.dp, CyanLiquid.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chart_floating_tooltip")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatDisplayDate(point.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (abs(holeThreshold - 50f) > 0.5f) {
                            "${point.flowingCount}/${point.activeCount} > ${holeThreshold.toInt()}% • ${selectedAggregation.label}$provenanceSuffix"
                        } else {
                            "${point.flowingCount}/${point.activeCount} flowing • ${selectedAggregation.label}$provenanceSuffix"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (point.isReconstructedHistorical || point.isIncompleteColdStart) BreakoutAmber else TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, "%.2f%%", point.tankLevel),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = CyanLiquid,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatSignedPercent(point.dailyDelta),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = deltaColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 4-Pillar Raw Breakdown Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TooltipPillarStat(
                    label = "Main Tank",
                    value = String.format(Locale.US, "%.1f%%", point.tankLevel),
                    color = CyanLiquid
                )
                TooltipPillarStat(
                    label = "Euphoria >85",
                    value = String.format(Locale.US, "%.1f%%", point.euphoriaPressurePct),
                    color = CoralOverheated
                )
                TooltipPillarStat(
                    label = "52W Breakout",
                    value = String.format(Locale.US, "%.1f%%", point.breakoutPulsePct),
                    color = BreakoutAmber
                )
                TooltipPillarStat(
                    label = "Cap <15",
                    value = String.format(Locale.US, "%.1f%%", point.capitulationDepthPct),
                    color = CapitulationPurple
                )
            }

            // Normalized Valuation P/E + Official Point-in-Time NSE Index P/E Readout
            val peTank = point.peTankLevel
            val peMedian = point.medianPe
            val idxPe = point.indexPe
            val idxPb = point.indexPb
            val hasAnyPe = peTank != null || idxPe != null
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (hasAnyPe) ValuationPePink.copy(alpha = 0.10f) else ObsidianBackground.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (hasAnyPe) ValuationPePink.copy(alpha = 0.35f) else HairlineBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chart_tooltip_valuation_pe")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (idxPe != null && idxPe.isFinite() && idxPe > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Official Index P/E (NSE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanLiquid,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = buildString {
                                    append(String.format(Locale.US, "%.1f×", idxPe))
                                    if (idxPb != null && idxPb.isFinite() && idxPb > 0.0) {
                                        append(String.format(Locale.US, " • P/B %.2f×", idxPb))
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = CyanLiquid,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Val P/E (Hole ${holeThreshold.toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (peTank != null) ValuationPePink else TextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (peTank != null) {
                                val medText = peMedian?.let { String.format(Locale.US, " • Eq-Wt %.1f×", it) } ?: ""
                                val countText = if (point.peActiveCount > 0) " (${point.peActiveCount})" else ""
                                String.format(Locale.US, "%.1f%%%s%s", peTank, medText, countText)
                            } else {
                                "N/A (Pre-Mar 2007)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = if (peTank != null) ValuationPePink else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (turningPoint != null) {
                val isPeak = turningPoint.role == TurningPointRole.PEAK
                val badgeColor = if (isPeak) CoralOverheated else EmeraldBullish
                val c = turningPoint.cycle
                val badgeText = if (isPeak) {
                    "Cycle #${c.cycleIndex} Peak (${formatDisplayDate(c.exactPeakDate)}) • Euphoria ${String.format(Locale.US, "%.1f%%", c.peakEuphoriaPct)} • Drop -${String.format(Locale.US, "%.1f", c.drawdownPctPoints)} pts"
                } else {
                    val recText = c.recoveryDurationDays?.let { " • Recovery ${it}d" } ?: " • Recovering"
                    "Cycle #${c.cycleIndex} Bottom (${formatDisplayDate(c.exactBottomDate)}) • Cap ${String.format(Locale.US, "%.1f%%", c.bottomCapitulationPct)}$recText"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TooltipPillarStat(
    label: String,
    value: String,
    color: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HistoricalCyclesTableCard(
    cycles: List<MarketCycleTurningPoint>,
    holeThreshold: Float = 50f,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("historical_cycles_accordion")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp)
                .testTag("historical_cycles_toggle_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timeline,
                    contentDescription = null,
                    tint = CyanLiquid,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    val hasPre2016Cycles = cycles.any { it.exactPeakDate < "2016-04-01" || it.exactBottomDate < "2016-04-01" }
                    Text(
                        text = if (hasPre2016Cycles) {
                            "Historical Market Cycles (1995–Present)"
                        } else {
                            "Historical Market Cycles (2016–Present)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (abs(holeThreshold - 50f) > 0.5f) {
                            "${cycles.size} cycles @ ${holeThreshold.roundToInt()}% hole • Anchored strictly to raw extrema"
                        } else {
                            "${cycles.size} detected turning cycles • Anchored strictly to raw daily extrema"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse historical cycles" else "Expand historical cycles",
                tint = CyanLiquid
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (cycles.isEmpty()) {
                    Text(
                        text = "No full swing cycles detected in the current window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    cycles.reversed().forEach { cycle ->
                        CycleTableEntryCard(cycle = cycle)
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleTableEntryCard(cycle: MarketCycleTurningPoint) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ElevatedCardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cycle_entry_${cycle.cycleIndex}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cycle #${cycle.cycleIndex}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = (if (cycle.isRecovered) EmeraldBullish else BreakoutAmber).copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        (if (cycle.isRecovered) EmeraldBullish else BreakoutAmber).copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = if (cycle.isRecovered) {
                            "Recovered in ${cycle.recoveryDurationDays}d"
                        } else {
                            "Recovery In Progress"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cycle.isRecovered) EmeraldBullish else BreakoutAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CycleStatCell(
                    label = "Peak Date",
                    value = formatDisplayDate(cycle.exactPeakDate),
                    subValue = "Tank ${String.format(Locale.US, "%.1f%%", cycle.peakMainTankPct)} • Eup ${String.format(Locale.US, "%.1f%%", cycle.peakEuphoriaPct)}",
                    accent = CoralOverheated,
                    modifier = Modifier.weight(1f)
                )
                CycleStatCell(
                    label = "Bottom Date",
                    value = formatDisplayDate(cycle.exactBottomDate),
                    subValue = "Tank ${String.format(Locale.US, "%.1f%%", cycle.bottomMainTankPct)} • Cap ${String.format(Locale.US, "%.1f%%", cycle.bottomCapitulationPct)}",
                    accent = EmeraldBullish,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = SubtleDivider)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CycleCompactStat(
                    label = "Drawdown",
                    value = "-${String.format(Locale.US, "%.1f", cycle.drawdownPctPoints)} pts (${cycle.daysPeakToBottom}d)",
                    color = CoralOverheated
                )
                CycleCompactStat(
                    label = "Recovery Date",
                    value = cycle.recoveryDate?.let { formatDisplayDate(it) } ?: "Ongoing",
                    color = TextPrimary
                )
                CycleCompactStat(
                    label = "Rebound",
                    value = "+${String.format(Locale.US, "%.1f", cycle.recoveryFromBottomPctPoints)} pts",
                    color = EmeraldBullish
                )
            }
        }
    }
}

@Composable
private fun CycleStatCell(
    label: String,
    value: String,
    subValue: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun CycleCompactStat(
    label: String,
    value: String,
    color: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ThresholdLegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.padding(top = 1.dp)) {
            drawCircle(color = color, radius = 3.dp.toPx())
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * Extrema-preserving min/max bucket downsampler.
 * Guarantees every detected Peak & Bottom date, endpoints, and per-bucket local minima/maxima
 * remain in the rendered path so curves never clip V-bottoms or miss exact euphoric tops.
 */
private fun downsampleHistoryWithIndices(
    points: List<TankHistoryPoint>,
    maxPoints: Int,
    mandatoryIndices: Set<Int> = emptySet()
): List<Pair<Int, TankHistoryPoint>> {
    if (points.isEmpty()) return emptyList()
    if (points.size <= maxPoints) {
        return points.mapIndexed { idx, pt -> idx to pt }
    }
    val selectedIndices = TreeSet<Int>()
    selectedIndices.add(0)
    selectedIndices.add(points.lastIndex)
    for (idx in mandatoryIndices) {
        if (idx in points.indices) {
            selectedIndices.add(idx)
        }
    }

    val bucketCount = (maxPoints / 2).coerceAtLeast(16)
    val n = points.size
    for (b in 0 until bucketCount) {
        val start = (b.toLong() * n / bucketCount).toInt()
        val end = (((b + 1).toLong() * n / bucketCount).toInt()).coerceAtLeast(start + 1).coerceAtMost(n)
        var minIdx = start
        var maxIdx = start
        var minVal = points[start].tankLevel
        var maxVal = minVal
        for (k in (start + 1) until end) {
            val v = points[k].tankLevel
            if (v < minVal) {
                minVal = v
                minIdx = k
            }
            if (v > maxVal) {
                maxVal = v
                maxIdx = k
            }
        }
        selectedIndices.add(minIdx)
        selectedIndices.add(maxIdx)
        if (minIdx == maxIdx) {
            selectedIndices.add((start + end) / 2)
        }
    }

    return selectedIndices.map { idx -> idx to points[idx] }
}

/**
 * Zero-phase symmetric 3-point curve conditioner that strictly pins all confirmed
 * Peak/Bottom turning points, endpoints, sharp V-bottoms, and local dome crests to their
 * exact raw daily values while removing high-frequency pixel chatter along intermediate slopes.
 */
private fun conditionCurvePreservingExtrema(
    sampledPoints: List<Pair<Int, TankHistoryPoint>>,
    pinnedOriginalIndices: Set<Int>,
    capitulationFloor: Double,
    overheatedCeiling: Double,
    passes: Int
): List<Double> {
    var current = sampledPoints.map { (_, pt) -> pt.tankLevel }
    if (current.size <= 4 || passes <= 0) return current

    val pinnedSampleIndices = BooleanArray(sampledPoints.size)
    pinnedSampleIndices[0] = true
    pinnedSampleIndices[sampledPoints.lastIndex] = true
    for (i in 1 until sampledPoints.lastIndex) {
        val origIdx = sampledPoints[i].first
        val v = current[i]
        val prev = current[i - 1]
        val next = current[i + 1]
        val isSharpTrough = v <= capitulationFloor && v <= prev && v <= next
        val isLocalCrest = v >= overheatedCeiling && v >= prev && v >= next
        val isZeroFloor = v <= 0.05
        if (origIdx in pinnedOriginalIndices || isSharpTrough || isLocalCrest || isZeroFloor) {
            pinnedSampleIndices[i] = true
        }
    }

    repeat(passes) {
        val nextPass = DoubleArray(current.size)
        for (i in current.indices) {
            if (pinnedSampleIndices[i]) {
                nextPass[i] = current[i]
            } else {
                nextPass[i] = (0.22 * current[i - 1] + 0.56 * current[i] + 0.22 * current[i + 1])
                    .coerceIn(0.0, 100.0)
            }
        }
        current = nextPass.toList()
    }
    return current
}

/**
 * Display-only Exponential Moving Average smoothing.
 * Never mutates underlying TankHistoryPoint records or raw turning points.
 */
private fun computeDisplayEma(values: List<Double>, period: Int): List<Double> {
    if (values.size <= 2) return values
    val alpha = 2.0 / (period.coerceAtLeast(2) + 1.0)
    val out = ArrayList<Double>(values.size)
    var ema = values.first()
    out.add(ema)
    for (i in 1 until values.size) {
        ema = alpha * values[i] + (1.0 - alpha) * ema
        out.add(ema.coerceIn(0.0, 100.0))
    }
    return out
}

/**
 * Monotone-clamped cubic Bezier spline across chart coordinates.
 * Control points are strictly bounded within [minY, maxY] of each segment so curves
 * stay silky-smooth across euphoric domes while preserving crisp V-bottom vertices
 * without artificial spline overshoot.
 */
private fun buildCubicBezierPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until points.lastIndex) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]

        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f

        val minY = minOf(p1.y, p2.y)
        val maxY = maxOf(p1.y, p2.y)
        path.cubicTo(
            c1x,
            c1y.coerceIn(minY, maxY),
            c2x,
            c2y.coerceIn(minY, maxY),
            p2.x,
            p2.y
        )
    }
    return path
}
