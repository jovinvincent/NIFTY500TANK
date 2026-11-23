package com.example.depthtank.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.depthtank.data.DeadlyComboReport
import com.example.depthtank.data.InvestorActionStage
import com.example.depthtank.data.PathMilestone
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
internal fun InvestorActionPlaybookCard(report: DeadlyComboReport) {
    val directive = report.investorDirective
    val activeStage = directive.activeStage
    var showFullLadder by remember { mutableStateOf(true) }

    val stageAccent = when (activeStage) {
        InvestorActionStage.MASSIVE_LUMP_SUM -> CapitulationPurple
        InvestorActionStage.AGGRESSIVE_SIP_STEP_UP -> EmeraldBullish
        InvestorActionStage.HOLD_STANDARD_SIP -> CyanLiquid
        InvestorActionStage.REDUCE_SIP_PAUSE_LUMPSUM -> BreakoutAmber
        InvestorActionStage.START_PROFIT_BOOKING -> CoralOverheated
        InvestorActionStage.MASSIVE_PROFIT_BOOKING -> CoralOverheated
    }

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("investor_action_playbook_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(
                        imageVector = Icons.Rounded.AccountBalanceWallet,
                        contentDescription = "Investor playbook",
                        tint = stageAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "INVESTOR ACTION & EQUITY TAX PLAYBOOK",
                        style = MaterialTheme.typography.labelSmall,
                        color = stageAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = stageAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = activeStage.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = stageAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Active Directive Banner
            Surface(
                color = stageAccent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "WHAT YOU MUST DO RIGHT NOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = stageAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = directive.headlineDirective,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = directive.executiveRationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    val kVal = report.kinetics
                    val primaryPe = kVal.primaryAbsolutePe
                    if (kVal.peTankLevel != null || primaryPe != null) {
                        val absColor = when (kVal.absolutePeRegime) {
                            "Deep Value", "Attractive Value" -> EmeraldBullish
                            "Fair Value" -> CyanLiquid
                            "Moderately Elevated" -> BreakoutAmber
                            "Expensive / Frothy" -> CoralOverheated
                            else -> TextMuted
                        }
                        val ltMedStr = buildString {
                            kVal.medianPeLongTermMedian?.let { append(" • 20Y Med ${it}×") }
                            kVal.indexPeConsolidatedMedian?.let { append(" / Cons. ${it}×") }
                            if (kVal.indexPe != null && kVal.medianPe != null) {
                                append(" • Eq-Wt ${kVal.medianPe}×")
                            }
                        }
                        Surface(
                            color = ElevatedCardSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, absColor.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("playbook_dual_valuation_pill")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (kVal.isOfficialIndexPeBenchmark) {
                                        "DUAL VALUATION CHECK (52W VAL P/E + OFFICIAL NSE INDEX P/E)"
                                    } else {
                                        "DUAL VALUATION CHECK (12M NORMALIZED + 20Y ABSOLUTE P/E)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = absColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${kVal.dualValuationVerdict}$ltMedStr",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Three Concrete Execution Pillars: Lump Sum / SIP / Profit Booking
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExecutionRuleBox(
                    label = "LUMP SUM CAPITAL",
                    instruction = directive.lumpSumAction,
                    accentColor = CyanLiquid
                )
                ExecutionRuleBox(
                    label = "SIP ALLOCATION",
                    instruction = directive.sipAction,
                    accentColor = EmeraldBullish
                )
                ExecutionRuleBox(
                    label = "PROFIT BOOKING / EXIT",
                    instruction = directive.profitBookingAction,
                    accentColor = if (activeStage.stepIndex >= 5) CoralOverheated else BreakoutAmber
                )
            }

            // 6-Stage Investor Decision Ladder Toggle
            Surface(
                color = ElevatedCardSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFullLadder = !showFullLadder }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "6-STAGE CAPITAL DEPLOYMENT LADDER (STAGE ${activeStage.stepIndex}/6 ACTIVE)",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanLiquid,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (showFullLadder) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Toggle deployment ladder",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = showFullLadder) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            InvestorActionStage.entries.forEach { stage ->
                                val isCurrent = stage == activeStage
                                val rowColor = when (stage) {
                                    InvestorActionStage.MASSIVE_LUMP_SUM -> CapitulationPurple
                                    InvestorActionStage.AGGRESSIVE_SIP_STEP_UP -> EmeraldBullish
                                    InvestorActionStage.HOLD_STANDARD_SIP -> CyanLiquid
                                    InvestorActionStage.REDUCE_SIP_PAUSE_LUMPSUM -> BreakoutAmber
                                    InvestorActionStage.START_PROFIT_BOOKING,
                                    InvestorActionStage.MASSIVE_PROFIT_BOOKING -> CoralOverheated
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCurrent) rowColor.copy(alpha = 0.14f)
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = if (isCurrent) "Active stage" else "Inactive stage",
                                        tint = if (isCurrent) rowColor else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${stage.stepIndex}. ${stage.shortTitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCurrent) TextPrimary else TextSecondary,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = stage.phaseConditionSummary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    if (isCurrent) {
                                        Surface(
                                            color = rowColor.copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "NOW",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = rowColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Equity Tax-Saving Evaluation (Indian STCG 20% / LTCG 12.5% Shield)
            Surface(
                color = ElevatedCardSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BreakoutAmber.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("equity_tax_shield_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Gavel,
                            contentDescription = "Equity tax evaluation",
                            tint = BreakoutAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "EQUITY TAX-SAVING SHIELD (LTCG 12.5% • STCG 20%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = BreakoutAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = directive.taxStrategyHeadline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "• STCG (<12M @ 20%): ${directive.stcgGuidance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "• LTCG (>12M @ 12.5% above ₹1.25L): ${directive.ltcgGuidance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    HorizontalDivider(color = HairlineBorder)

                    Text(
                        text = directive.taxBreakEvenNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBullish,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutionRuleBox(
    label: String,
    instruction: String,
    accentColor: Color
) {
    Surface(
        color = ElevatedCardSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
internal fun TrajectoryPathForecastCard(report: DeadlyComboReport) {
    val k = report.kinetics
    val path = report.pathProjection

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trajectory_path_forecast_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = "Kinetic telemetry",
                    tint = CyanLiquid,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "VIX • MMI • VAL P/E • VELOCITY • ACCEL & PATH VERDICT",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanLiquid,
                    fontWeight = FontWeight.Bold
                )
            }

            // 6-Metric Live Macro & Kinetic Strip (2 rows x 3 columns)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val vixVal = k.indiaVix
                    val vixRatio = k.vixSurgeRatio15d
                    KineticTile(
                        label = "India VIX",
                        value = vixVal?.toString() ?: "N/A",
                        sub = if (vixVal != null && vixRatio != null) "${vixRatio}× 15D Min" else "Pre-2008 N/A",
                        valueColor = when {
                            vixVal == null -> TextMuted
                            vixVal >= 20.0 -> CoralOverheated
                            else -> EmeraldBullish
                        },
                        modifier = Modifier.weight(1f)
                    )
                    val mmiVal = k.mmiValue
                    KineticTile(
                        label = "Mood (MMI)",
                        value = mmiVal?.toString() ?: "N/A",
                        sub = when {
                            mmiVal == null -> "Pre-2016 N/A"
                            mmiVal >= 70.0 -> "Extreme Greed"
                            mmiVal >= 55.0 -> "Greed Zone"
                            mmiVal <= 30.0 -> "Extreme Fear"
                            mmiVal < 45.0 -> "Fear Zone"
                            else -> "Neutral"
                        },
                        valueColor = when {
                            mmiVal == null -> TextMuted
                            mmiVal >= 70.0 -> BreakoutAmber
                            mmiVal <= 30.0 -> CapitulationPurple
                            else -> CyanLiquid
                        },
                        modifier = Modifier.weight(1f)
                    )
                    val gapVal = k.mmiMinusTankGap
                    KineticTile(
                        label = "MMI−Tank Gap",
                        value = if (gapVal != null) "${if (gapVal >= 0) "+" else ""}$gapVal" else "N/A",
                        sub = when {
                            gapVal == null -> "MMI N/A"
                            gapVal >= 35.0 -> "Overhang!"
                            else -> "Aligned"
                        },
                        valueColor = when {
                            gapVal == null -> TextMuted
                            gapVal >= 35.0 -> BreakoutAmber
                            else -> EmeraldBullish
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KineticTile(
                        label = "A/D Ratio",
                        value = "${k.adPctDaily}%",
                        sub = "3D Avg ${k.adPct3dAvg}%",
                        valueColor = when {
                            k.adPctDaily >= 60.0 -> EmeraldBullish
                            k.adPctDaily <= 38.5 -> CoralOverheated
                            else -> CyanLiquid
                        },
                        modifier = Modifier.weight(1f)
                    )
                    KineticTile(
                        label = "10D Velocity",
                        value = "${if (k.velocity10d >= 0) "+" else ""}${k.velocity10d}%",
                        sub = "5D ${if (k.velocity5d >= 0) "+" else ""}${k.velocity5d}%",
                        valueColor = if (k.velocity10d >= 0) EmeraldBullish else CoralOverheated,
                        modifier = Modifier.weight(1f)
                    )
                    KineticTile(
                        label = "3D Accel",
                        value = "${if (k.momentumAccel3d >= 0) "+" else ""}${k.momentumAccel3d}",
                        sub = when {
                            k.momentumAccel3d >= 1.5 -> "Accelerating"
                            k.momentumAccel3d <= -1.5 -> "Decelerating"
                            else -> "Steady"
                        },
                        valueColor = if (k.momentumAccel3d < -1.5) BreakoutAmber else EmeraldBullish,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 3-Tile Valuation P/E Dimension Row (pink theme, strictly N/A pre-Jul 2007)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_valuation_pe_strip"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val peVal = k.peTankLevel
                    KineticTile(
                        label = "Val P/E Tank",
                        value = peVal?.let { "${it}%" } ?: "N/A",
                        sub = when {
                            peVal == null -> "Pre-2007 N/A"
                            peVal <= 15.0 -> "Deep Comp."
                            peVal < 25.0 -> "Compressed"
                            peVal >= 55.0 -> "Frothy P/E"
                            else -> "Balanced"
                        },
                        valueColor = when {
                            peVal == null -> TextMuted
                            peVal >= 55.0 -> CoralOverheated
                            else -> ValuationPePink
                        },
                        modifier = Modifier.weight(1f)
                    )
                    val primaryPe = k.primaryAbsolutePe
                    val medPct = k.medianPeHistoricalPercentile
                    val consPct = k.indexPeConsolidatedPercentile
                    val shortRegime = when (k.absolutePeRegime) {
                        "Deep Value" -> "Deep Val"
                        "Attractive Value" -> "Attractive"
                        "Fair Value" -> "Fair Val"
                        "Moderately Elevated" -> "Elevated"
                        "Expensive / Frothy" -> "Expensive"
                        else -> k.absolutePeRegime
                    }
                    val medSub = when {
                        primaryPe == null -> "Pre-1999 N/A"
                        medPct != null && consPct != null -> "${medPct}%/${consPct}%C • $shortRegime"
                        medPct != null -> "${medPct}th %ile • $shortRegime"
                        else -> shortRegime
                    }
                    val medColor = when (k.absolutePeRegime) {
                        "Deep Value", "Attractive Value" -> EmeraldBullish
                        "Fair Value" -> CyanLiquid
                        "Moderately Elevated" -> BreakoutAmber
                        "Expensive / Frothy" -> CoralOverheated
                        else -> TextMuted
                    }
                    KineticTile(
                        label = if (k.isOfficialIndexPeBenchmark) "Index P/E (NSE)" else "Median P/E",
                        value = primaryPe?.let { "${it}×" } ?: "N/A",
                        sub = medSub,
                        valueColor = medColor,
                        modifier = Modifier.weight(1f)
                    )
                    val peGap = k.priceMinusPeGap
                    KineticTile(
                        label = "Price−P/E",
                        value = if (peGap != null) "${if (peGap >= 0) "+" else ""}$peGap" else "N/A",
                        sub = when {
                            peGap == null -> "Val P/E N/A"
                            peGap >= 5.0 -> "Earnings-Led"
                            peGap <= -5.0 -> "Multiple-Led"
                            else -> "Aligned"
                        },
                        valueColor = when {
                            peGap == null -> TextMuted
                            peGap >= 5.0 -> EmeraldBullish
                            peGap <= -10.0 -> BreakoutAmber
                            else -> CyanLiquid
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Question 1: Will it definitely crash?
            ScenarioQuestionCard(
                question = "Q1: WILL IT DEFINITELY CRASH FROM HERE?",
                verdict = path.crashRiskHeadline,
                explanation = path.crashRiskExplanation,
                guardRail = path.crashInvalidationTrigger,
                icon = Icons.Rounded.Shield,
                accentColor = if (path.crashRiskHeadline.startsWith("HIGH")) CoralOverheated else EmeraldBullish
            )

            // Question 2: Straight to euphoria without intermediate drops?
            ScenarioQuestionCard(
                question = "Q2: HEADING STRAIGHT TO EUPHORIA WITHOUT INTERMEDIATE DROPS?",
                verdict = path.straightEuphoriaHeadline,
                explanation = path.straightEuphoriaExplanation,
                guardRail = null,
                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                accentColor = BreakoutAmber
            )

            // 4-Milestone Cycle Roadmap
            Surface(
                color = ElevatedCardSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "STEPPED CYCLE ROADMAP TO EUPHORIA",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanLiquid,
                        fontWeight = FontWeight.Bold
                    )
                    path.milestones.forEach { milestone ->
                        MilestoneRow(milestone = milestone)
                    }
                }
            }
        }
    }
}

@Composable
private fun KineticTile(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ElevatedCardSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMonoFamily,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ScenarioQuestionCard(
    question: String,
    verdict: String,
    explanation: String,
    guardRail: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Surface(
        color = ElevatedCardSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = question,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = question,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = verdict,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            if (!guardRail.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Invalidation level",
                        tint = BreakoutAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = guardRail,
                        style = MaterialTheme.typography.labelSmall,
                        color = BreakoutAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(milestone: PathMilestone) {
    val dotColor = when {
        milestone.isCurrentTarget -> CyanLiquid
        milestone.isCompleted -> EmeraldBullish
        else -> TextMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (milestone.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = milestone.statusLabel,
            tint = dotColor,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = milestone.stepLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (milestone.isCurrentTarget) TextPrimary else TextSecondary,
                    fontWeight = if (milestone.isCurrentTarget) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = milestone.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = dotColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = milestone.targetGate,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}
