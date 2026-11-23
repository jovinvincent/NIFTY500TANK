package com.example.depthtank.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.depthtank.data.InstitutionalRegime
import com.example.depthtank.data.MarketRegime
import com.example.depthtank.data.NseMarketCalendar
import com.example.depthtank.data.TankIndexUniverse
import com.example.ui.theme.BreakoutAmber
import com.example.ui.theme.CapitulationPurple
import com.example.ui.theme.CoralOverheated
import com.example.ui.theme.CyanLiquid
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SubtleDivider
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = GlassCardSurface,
    borderColor: Color = HairlineBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

fun regimeColor(regime: MarketRegime): Color = when (regime) {
    MarketRegime.CAPITULATION -> CapitulationPurple
    MarketRegime.ACCUMULATION -> CyanLiquid
    MarketRegime.NEUTRAL -> BreakoutAmber
    MarketRegime.BULLISH_FLOW -> EmeraldBullish
    MarketRegime.OVERHEATED -> CoralOverheated
}

fun institutionalRegimeColor(regime: InstitutionalRegime): Color = when (regime) {
    InstitutionalRegime.DEEP_CAPITULATION -> CapitulationPurple
    InstitutionalRegime.ACCUMULATION -> CyanLiquid
    InstitutionalRegime.RECOVERY -> EmeraldBullish
    InstitutionalRegime.HEALTHY_BULL_FLOW -> EmeraldBullish
    InstitutionalRegime.ELEVATED_EUPHORIA -> BreakoutAmber
    InstitutionalRegime.EXTREME_EUPHORIA -> CoralOverheated
    InstitutionalRegime.MOMENTUM_EXHAUSTION -> CoralOverheated
    InstitutionalRegime.BREADTH_DETERIORATION -> BreakoutAmber
}

@Composable
fun RegimePill(
    regime: MarketRegime,
    modifier: Modifier = Modifier
) {
    PillBadge(
        label = regime.pillLabel,
        accent = regimeColor(regime),
        modifier = modifier
    )
}

@Composable
fun InstitutionalRegimePill(
    regime: InstitutionalRegime,
    modifier: Modifier = Modifier
) {
    PillBadge(
        label = regime.pillLabel,
        accent = institutionalRegimeColor(regime),
        modifier = modifier
    )
}

@Composable
private fun PillBadge(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.48f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun formatRupees(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "₹${formatter.format(value)}"
}

fun formatSignedPercent(value: Double): String {
    val sign = if (value > 0.0) "+" else if (value < 0.0) "-" else ""
    return String.format(Locale.US, "%s%.2f%%", sign, abs(value))
}

fun formatDisplayDate(dateStr: String): String {
    return NseMarketCalendar.formatDdMmmYyyy(dateStr)
}

@Composable
fun HydroDynamicInfoDialog(
    selectedUniverse: TankIndexUniverse = TankIndexUniverse.NIFTY_500,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElevatedCardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = null,
                        tint = CyanLiquid
                    )
                    Text(
                        text = "${selectedUniverse.shortLabel} Depth Tank",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("info_dialog_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close info modal",
                        tint = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "4-Pillar 52-Week Equal-Weight Intelligence (${selectedUniverse.targetCount} Base)",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanLiquid
                )
                Text(
                    text = "Each eligible ${selectedUniverse.shortLabel} constituent (${selectedUniverse.rangeLabel}) with valid rolling 52-Week High and 52-Week Low bounds receives an equal 0–100 tank above the permanent 50% drainage valve.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                HorizontalDivider(color = SubtleDivider)
                FormulaRow(
                    label = "1. Stock Score (52-Week Range)",
                    formula = "((Price - 52-Week Low) / (52-Week High - 52-Week Low)) × 100"
                )
                FormulaRow(
                    label = "2. Main Tank & Euphoria Pressure",
                    formula = "Tank = Sum(max(0, Score - 50)) / (Active × 50) • Euphoria = % > 85"
                )
                FormulaRow(
                    label = "3. Breakout Pulse & Capitulation",
                    formula = "Breakout = Today High > Prior 52-Week High • Capitulation = % < 15"
                )
                HorizontalDivider(color = SubtleDivider)
                Text(
                    text = "Overheated Main Tank levels (>75%) alone never trigger a top warning. Exhaustion requires corroborating 20-day Breakout Pulse contraction across the 1995–Present point-in-time NSE archive (with pre-2008 VIX & pre-2016 MMI shown honestly as N/A).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("info_dialog_confirm_button")
            ) {
                Text("Got It", color = CyanLiquid, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun FormulaRow(label: String, formula: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = GlassCardSurface,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = formula,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = CyanLiquid,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
