package com.example.depthtank.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.depthtank.data.MarketCycleTurningPoint
import com.example.depthtank.data.TankCalculator
import com.example.depthtank.data.TankIndexUniverse
import com.example.ui.theme.BreakoutAmber
import com.example.ui.theme.CapitulationPurple
import com.example.ui.theme.CoralOverheated
import com.example.ui.theme.CyanLiquid
import com.example.ui.theme.DeepSlateWater
import com.example.ui.theme.DeepSlateWaterMuted
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

@Composable
fun PhilosophyGuideScreen(
    cycles: List<MarketCycleTurningPoint> = emptyList(),
    selectedUniverse: TankIndexUniverse = TankIndexUniverse.NIFTY_500,
    activeStocksCount: Int = selectedUniverse.targetCount,
    modifier: Modifier = Modifier
) {
    var simulatedScore by remember { mutableFloatStateOf(74f) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("philosophy_guide_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Philosophy & 4-Pillar Guide",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${selectedUniverse.shortLabel} ($activeStocksCount Base) Equal-Weight 52-Week Model (Jan 2, 1995 – Present)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // 1. Cap-Weighted Distortion vs Equal-Weight Truth
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "1. THE PROBLEM WITH HEADLINE INDICES",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Market-Cap Distortion vs. Equal-Weight 52-Week Truth",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Standard indices like Nifty 50 and Nifty 500 weight companies by free-float market cap. Just 10–15 mega-cap giants can mask a silent mid-cap and small-cap sell-off—or hide an early stealth bull market.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ComparisonBox(
                        title = "Cap-Weighted Nifty",
                        subtitle = "Top 15 stocks dictate ~45% of index direction.",
                        accent = CoralOverheated,
                        modifier = Modifier.weight(1f)
                    )
                    ComparisonBox(
                        title = "Depth Tank ($activeStocksCount Base)",
                        subtitle = "Every eligible ${selectedUniverse.shortLabel} stock gets an equal 52-Week High/Low tank & 50% valve.",
                        accent = EmeraldBullish,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Interactive Hydro-Dynamic Analogy + Why 50% Valve Creates Sharp Bottoms vs Plateaus
        item {
            val overflow = TankCalculator.computeOverflowVolume(simulatedScore.toDouble())
            val isFlowing = simulatedScore > 50f

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "2. THE 50% DRAINAGE VALVE PHYSICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Every stock's daily rolling 52-Week position is normalized 0 (52-Week Low) to 100 (52-Week High). Only overflow above the permanent 50% drainage hole pours into the Main Tank.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(72.dp)
                            .height(110.dp)
                    ) {
                        val corner = 14.dp.toPx()
                        val rect = Rect(0f, 0f, size.width, size.height)
                        val clip = Path().apply {
                            addRoundRect(RoundRect(rect, CornerRadius(corner, corner)))
                        }
                        val fillFrac = (simulatedScore / 100f).coerceIn(0f, 1f)
                        val waterTop = size.height * (1f - fillFrac)
                        val midY = size.height * 0.5f

                        drawPath(path = clip, color = ObsidianBackground)
                        clipPath(clip) {
                            if (fillFrac > 0.01f) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            if (isFlowing) CyanLiquid else DeepSlateWater,
                                            if (isFlowing) RoyalBlueLiquid else DeepSlateWaterMuted
                                        ),
                                        startY = waterTop,
                                        endY = size.height
                                    ),
                                    topLeft = Offset(0f, waterTop),
                                    size = Size(size.width, size.height - waterTop)
                                )
                            }
                            drawLine(
                                color = if (isFlowing) Color.White else TextMuted,
                                start = Offset(0f, midY),
                                end = Offset(size.width, midY),
                                strokeWidth = 1.6.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
                            )
                        }
                        drawRoundRect(
                            color = if (isFlowing) CyanLiquid else HairlineBorder,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(corner, corner),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Simulated Score: ${simulatedScore.toInt()} / 100",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = TextPrimary
                        )
                        Text(
                            text = if (isFlowing) {
                                "Overflow Volume: +${String.format(Locale.US, "%.1f", overflow)} pouring into Main Tank"
                            } else {
                                "Overflow Volume: 0.0 (Drained below 50% hole)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFlowing) EmeraldBullish else TextMuted
                        )
                        Slider(
                            value = simulatedScore,
                            onValueChange = { simulatedScore = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanLiquid,
                                activeTrackColor = CyanLiquid,
                                inactiveTrackColor = ElevatedCardSurface
                            ),
                            modifier = Modifier.testTag("hydro_analogy_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElevatedCardSurface,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Why V-Bottoms vs. Flat Plateaus? Because scores below 50 drain to zero overflow, severe crashes empty the Main Tank rapidly into sharp V-shaped capitulation troughs. Conversely, bull markets pin hundreds of constituents between 75–100, creating prolonged euphoric plateaus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 3. The Four Pillars & Breakout Exhaustion Detection
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "3. FOUR-PILLAR ARCHITECTURE & DIVERGENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                PlaybookTier(
                    range = "Pillar 1",
                    title = "Main Tank (Equal-Weight Breadth)",
                    detail = "Measures aggregated overflow above the 50% midpoint. Readings >75% mark Overheated Euphoria and track consecutive plateau days.",
                    color = CyanLiquid
                )
                HorizontalDivider(color = SubtleDivider, modifier = Modifier.padding(vertical = 10.dp))
                PlaybookTier(
                    range = "Pillar 2",
                    title = "Euphoria Pressure (>85)",
                    detail = "Percentage of active eligible stocks pinned in the top 15% of their 52-Week High/Low channel.",
                    color = CoralOverheated
                )
                HorizontalDivider(color = SubtleDivider, modifier = Modifier.padding(vertical = 10.dp))
                PlaybookTier(
                    range = "Pillar 3",
                    title = "Breakout Pulse (Fresh 52-Week Highs)",
                    detail = "Compares Today High > Prior 52-Week High. When Main Tank & Euphoria remain >75% while 20-day Breakout Pulse (Δ20D) falls sharply, hidden Momentum Exhaustion emerges.",
                    color = BreakoutAmber
                )
                HorizontalDivider(color = SubtleDivider, modifier = Modifier.padding(vertical = 10.dp))
                PlaybookTier(
                    range = "Pillar 4",
                    title = "Capitulation Depth (<15)",
                    detail = "Percentage of active stocks compressed near their 52-Week Low—historically identifying washed-out secular bottoms.",
                    color = CapitulationPurple
                )
            }
        }

        // 4. Eight-State Institutional Regimes
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "4. EIGHT-STATE INSTITUTIONAL REGIMES",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Deep Capitulation • 2. Accumulation • 3. Recovery • 4. Healthy Bull Flow • 5. Elevated Euphoria • 6. Extreme Euphoria • 7. Momentum Exhaustion • 8. Breadth Deterioration",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: Exceeding 75% in the Main Tank alone classifies as Elevated or Extreme Euphoria—never an automatic sell signal without causal 52-Week breakout contraction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // 5. Dynamic Historical Turning Cycles Since Jan 2, 1995
        item {
            val hasPre2016 = cycles.any { it.exactPeakDate < "2016-04-01" || it.exactBottomDate < "2016-04-01" }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "5. ${selectedUniverse.shortLabel.uppercase(Locale.US)} TURNING CYCLES (${if (hasPre2016) "1995 – PRESENT" else "APR 1, 2016 – PRESENT"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Point-in-time NSE Bhavcopies (including delisted/merged equities) power 1995–2016 alongside unmodified 2016–Present EOD records. Pre-2008 India VIX & pre-2016 MMI are marked N/A (never fabricated) and Cycle Sniper scores normalize strictly over genuinely available factors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (cycles.isEmpty()) {
                    MilestoneRow("Nov 2016", "Demonetization Shock", "Main tank cooled sharply into the 22% accumulation zone before the 2017 broad rally.")
                    Spacer(modifier = Modifier.height(10.dp))
                    MilestoneRow("2018–2019", "Mid & Small-Cap Bear Cycle", "Tank drained under 15% even while mega-caps propped up headline Nifty 50.")
                    Spacer(modifier = Modifier.height(10.dp))
                    MilestoneRow("Mar 2020", "COVID-19 Crash (~5%)", "Historic washout near 5% tank level—signaling peak capitulation before a secular bull run.")
                    Spacer(modifier = Modifier.height(10.dp))
                    MilestoneRow("2021 & Sep 2024", "Euphoric Peaks (>78%)", "Tank surged past the 75% caution line prior to multi-month market consolidations.")
                } else {
                    cycles.reversed().take(6).forEachIndexed { idx, cycle ->
                        if (idx > 0) Spacer(modifier = Modifier.height(10.dp))
                        val recText = cycle.recoveryDurationDays?.let { "Recovered in ${it}d (${formatDisplayDate(cycle.recoveryDate.orEmpty())})" }
                            ?: "Recovery unfolding"
                        MilestoneRow(
                            date = formatDisplayDate(cycle.exactPeakDate),
                            title = "Cycle #${cycle.cycleIndex}: ${String.format(Locale.US, "%.1f%%", cycle.peakMainTankPct)} → ${String.format(Locale.US, "%.1f%%", cycle.bottomMainTankPct)}",
                            note = "Bottomed ${formatDisplayDate(cycle.exactBottomDate)} (${cycle.daysPeakToBottom} trading days, Cap ${String.format(Locale.US, "%.1f%%", cycle.bottomCapitulationPct)}). $recText."
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ComparisonBox(
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ElevatedCardSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = accent)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun PlaybookTier(
    range: String,
    title: String,
    detail: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = color.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
        ) {
            Text(
                text = range,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun MilestoneRow(date: String, title: String, note: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.labelMedium,
            color = CyanLiquid,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(86.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
