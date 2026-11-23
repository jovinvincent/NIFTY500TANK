package com.example.depthtank.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.depthtank.data.InstitutionalRegime
import com.example.depthtank.data.MarketRegime
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ValuationPePink
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private data class BubbleSeed(val xFrac: Float, val yOffset: Float, val radiusDp: Float, val speed: Float)

private val HERO_BUBBLES = listOf(
    BubbleSeed(0.14f, 0.15f, 3.2f, 1.0f),
    BubbleSeed(0.28f, 0.55f, 4.4f, 0.85f),
    BubbleSeed(0.41f, 0.30f, 2.8f, 1.25f),
    BubbleSeed(0.56f, 0.72f, 4.0f, 0.95f),
    BubbleSeed(0.69f, 0.22f, 3.5f, 1.1f),
    BubbleSeed(0.82f, 0.61f, 2.6f, 1.3f),
    BubbleSeed(0.35f, 0.88f, 3.0f, 0.9f),
    BubbleSeed(0.63f, 0.44f, 2.5f, 1.15f)
)

@Composable
fun HeroMainTankCard(
    tankLevel: Double,
    dailyDelta: Double,
    flowingCount: Int,
    activeCount: Int,
    regime: MarketRegime,
    holeThreshold: Float = 50f,
    euphoriaPressurePct: Double = 0.0,
    euphoriaPressureCount: Int = 0,
    institutionalRegime: InstitutionalRegime? = null,
    plateauDays: Int = 0,
    peTankLevel: Double? = null,
    medianPe: Double? = null,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = tankLevel.toFloat().coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 900),
        label = "tank_level_anim"
    )
    val animatedEuphoria by animateFloatAsState(
        targetValue = euphoriaPressurePct.toFloat().coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 900),
        label = "euphoria_level_anim"
    )
    val animatedPeLevel by animateFloatAsState(
        targetValue = (peTankLevel ?: 0.0).toFloat().coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 900),
        label = "valuation_pe_level_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "tank_wave_physics")
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_1"
    )
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_2"
    )
    val streamProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stream_flow"
    )
    val bubbleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble_rise"
    )

    val textMeasurer = rememberTextMeasurer()
    val flowingRatio = if (activeCount > 0) (flowingCount.toFloat() / activeCount.toFloat()).coerceIn(0f, 1f) else 0f

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_main_tank_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MAIN EQUAL-WEIGHT TANK (52-WEEK)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (kotlin.math.abs(holeThreshold - 50f) > 0.5f) {
                        "Simulated Overflow Above ${holeThreshold.toInt()}% Valve"
                    } else {
                        "Aggregated Overflow Above 50% Valve"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$flowingCount / $activeCount pouring",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanLiquid
                )
                if (plateauDays > 0) {
                    Text(
                        text = "Plateau: ${plateauDays}d >75%",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralOverheated,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(276.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Primary Animated Hydro-Dynamic Water Tank
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val leftMargin = 2.dp.toPx()
                    val rightRulerWidth = 46.dp.toPx()
                    val manifoldTop = 6.dp.toPx()
                    val pipeLength = 22.dp.toPx()
                    val tankTop = manifoldTop + pipeLength + 6.dp.toPx()
                    val tankBottom = size.height - 6.dp.toPx()
                    val tankLeft = leftMargin
                    val tankRight = size.width - rightRulerWidth
                    val tankWidth = (tankRight - tankLeft).coerceAtLeast(100f)
                    val tankHeight = (tankBottom - tankTop).coerceAtLeast(100f)

                    // Overhead collector pipe
                    drawLine(
                        color = HairlineBorder,
                        start = Offset(tankLeft + 14.dp.toPx(), manifoldTop + 5.dp.toPx()),
                        end = Offset(tankRight - 14.dp.toPx(), manifoldTop + 5.dp.toPx()),
                        strokeWidth = 5.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val totalPipes = 7
                    val activePipes = ((flowingRatio * totalPipes).toInt()).coerceIn(if (flowingCount > 0) 1 else 0, totalPipes)
                    val fillFraction = (animatedLevel / 100f).coerceIn(0f, 1f)
                    val waterSurfaceY = tankBottom - (tankHeight * fillFraction)

                    for (p in 0 until totalPipes) {
                        val xFrac = (p + 1).toFloat() / (totalPipes + 1).toFloat()
                        val pipeX = tankLeft + tankWidth * xFrac
                        val isPouring = p < activePipes

                        drawLine(
                            color = if (isPouring) CyanLiquid.copy(alpha = 0.65f) else HairlineBorder,
                            start = Offset(pipeX, manifoldTop + 5.dp.toPx()),
                            end = Offset(pipeX, manifoldTop + pipeLength),
                            strokeWidth = 3.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        if (isPouring) {
                            val streamTop = manifoldTop + pipeLength + 1.dp.toPx()
                            val streamBottom = waterSurfaceY.coerceAtLeast(streamTop + 8f)
                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        CyanLiquid.copy(alpha = 0.85f),
                                        RoyalBlueLiquid.copy(alpha = 0.5f),
                                        CyanLiquid.copy(alpha = 0.15f)
                                    ),
                                    startY = streamTop,
                                    endY = streamBottom
                                ),
                                start = Offset(pipeX, streamTop),
                                end = Offset(pipeX, streamBottom),
                                strokeWidth = 2.4.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    intervals = floatArrayOf(12f, 10f),
                                    phase = -streamProgress * 44f
                                )
                            )
                        }
                    }

                    val tankCorner = 20.dp.toPx()
                    val tankRect = Rect(tankLeft, tankTop, tankRight, tankBottom)
                    val tankClip = Path().apply {
                        addRoundRect(RoundRect(tankRect, CornerRadius(tankCorner, tankCorner)))
                    }

                    drawPath(
                        path = tankClip,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF0E1420), Color(0xFF0A0F18)),
                            startY = tankTop,
                            endY = tankBottom
                        )
                    )

                    clipPath(tankClip) {
                        val overboughtY = tankBottom - tankHeight * 0.75f
                        drawRect(
                            color = CoralOverheated.copy(alpha = 0.055f),
                            topLeft = Offset(tankLeft, tankTop),
                            size = Size(tankWidth, (overboughtY - tankTop).coerceAtLeast(0f))
                        )

                        if (fillFraction > 0.005f) {
                            val waveAmp1 = 5.5.dp.toPx()
                            val waveAmp2 = 3.5.dp.toPx()
                            val stepPx = 6f

                            val backWave = Path().apply {
                                moveTo(tankLeft, tankBottom)
                                var x = tankLeft
                                while (x <= tankRight + stepPx) {
                                    val nx = ((x - tankLeft) / tankWidth) * 2f * PI.toFloat()
                                    val y = waterSurfaceY + sin(nx * 1.4f - wavePhase2) * waveAmp2 - 2.dp.toPx()
                                    lineTo(x, y)
                                    x += stepPx
                                }
                                lineTo(tankRight, tankBottom)
                                close()
                            }
                            drawPath(
                                path = backWave,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        CyanLiquid.copy(alpha = 0.32f),
                                        RoyalBlueLiquid.copy(alpha = 0.25f)
                                    ),
                                    startY = waterSurfaceY - 12.dp.toPx(),
                                    endY = tankBottom
                                )
                            )

                            val frontWave = Path().apply {
                                moveTo(tankLeft, tankBottom)
                                var x = tankLeft
                                while (x <= tankRight + stepPx) {
                                    val nx = ((x - tankLeft) / tankWidth) * 2f * PI.toFloat()
                                    val y = waterSurfaceY +
                                        sin(nx * 1.8f + wavePhase1) * waveAmp1 +
                                        sin(nx * 0.9f - wavePhase2) * (waveAmp2 * 0.6f)
                                    lineTo(x, y)
                                    x += stepPx
                                }
                                lineTo(tankRight, tankBottom)
                                close()
                            }

                            val topLiquidColor = when {
                                animatedLevel >= 75f -> CoralOverheated.copy(alpha = 0.82f)
                                animatedLevel < 15f -> CapitulationPurple.copy(alpha = 0.78f)
                                else -> CyanLiquid.copy(alpha = 0.88f)
                            }
                            val midLiquidColor = when {
                                animatedLevel < 15f -> RoyalBlueLiquid.copy(alpha = 0.75f)
                                else -> RoyalBlueLiquid.copy(alpha = 0.84f)
                            }
                            drawPath(
                                path = frontWave,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        topLiquidColor,
                                        midLiquidColor,
                                        Color(0xFF002B7A).copy(alpha = 0.92f)
                                    ),
                                    startY = waterSurfaceY - 10.dp.toPx(),
                                    endY = tankBottom
                                )
                            )

                            val crestPath = Path().apply {
                                var x = tankLeft
                                var started = false
                                while (x <= tankRight + stepPx) {
                                    val nx = ((x - tankLeft) / tankWidth) * 2f * PI.toFloat()
                                    val y = waterSurfaceY +
                                        sin(nx * 1.8f + wavePhase1) * waveAmp1 +
                                        sin(nx * 0.9f - wavePhase2) * (waveAmp2 * 0.6f)
                                    if (!started) {
                                        moveTo(x, y)
                                        started = true
                                    } else {
                                        lineTo(x, y)
                                    }
                                    x += stepPx
                                }
                            }
                            drawPath(
                                path = crestPath,
                                color = Color.White.copy(alpha = 0.45f),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )

                            val liquidDepth = (tankBottom - waterSurfaceY).coerceAtLeast(0f)
                            if (liquidDepth > 24.dp.toPx()) {
                                for (bubble in HERO_BUBBLES) {
                                    val cycle = (bubble.yOffset + bubbleProgress * bubble.speed) % 1f
                                    val bx = tankLeft + tankWidth * bubble.xFrac +
                                        sin(cycle * 4f * PI.toFloat()) * 4.dp.toPx()
                                    val by = tankBottom - cycle * (liquidDepth - 10.dp.toPx())
                                    if (by > waterSurfaceY + 8.dp.toPx()) {
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.22f * (1f - cycle * 0.6f)),
                                            radius = bubble.radiusDp.dp.toPx(),
                                            center = Offset(bx, by)
                                        )
                                    }
                                }
                            }
                        }

                        val holeFrac = (holeThreshold / 100f).coerceIn(0f, 1f)
                        val yHole = tankBottom - tankHeight * holeFrac
                        val isCustomHole = kotlin.math.abs(holeThreshold - 50f) > 0.5f
                        drawLine(
                            color = if (isCustomHole) CyanLiquid.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.24f),
                            start = Offset(tankLeft, yHole),
                            end = Offset(tankRight, yHole),
                            strokeWidth = if (isCustomHole) 1.6.dp.toPx() else 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                        val y75 = tankBottom - tankHeight * 0.75f
                        drawLine(
                            color = CoralOverheated.copy(alpha = 0.58f),
                            start = Offset(tankLeft, y75),
                            end = Offset(tankRight, y75),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )

                        // Valuation P/E Tank Indicator (Pink Line on exact 0–100% Tank scale)
                        if (peTankLevel != null && peTankLevel.isFinite()) {
                            val peFrac = (animatedPeLevel / 100f).coerceIn(0f, 1f)
                            val yPe = (tankBottom - tankHeight * peFrac)
                                .coerceIn(tankTop + 3.dp.toPx(), tankBottom - 3.dp.toPx())
                            // Subtle dark contrast halo so pink line stands out crisply over bright cyan water
                            drawLine(
                                color = Color(0xFF070C15).copy(alpha = 0.78f),
                                start = Offset(tankLeft + 2.dp.toPx(), yPe),
                                end = Offset(tankRight - 2.dp.toPx(), yPe),
                                strokeWidth = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // Soft pink outer bloom
                            drawLine(
                                color = ValuationPePink.copy(alpha = 0.34f),
                                start = Offset(tankLeft + 2.dp.toPx(), yPe),
                                end = Offset(tankRight - 2.dp.toPx(), yPe),
                                strokeWidth = 4.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // Crisp primary Valuation P/E pink line
                            drawLine(
                                color = ValuationPePink,
                                start = Offset(tankLeft + 4.dp.toPx(), yPe),
                                end = Offset(tankRight - 4.dp.toPx(), yPe),
                                strokeWidth = 2.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // Left & right glowing anchor dots at glass inner edges
                            drawCircle(
                                color = Color(0xFF070C15),
                                radius = 4.5.dp.toPx(),
                                center = Offset(tankLeft + 6.dp.toPx(), yPe)
                            )
                            drawCircle(
                                color = ValuationPePink,
                                radius = 3.2.dp.toPx(),
                                center = Offset(tankLeft + 6.dp.toPx(), yPe)
                            )
                            drawCircle(
                                color = Color(0xFF070C15),
                                radius = 4.5.dp.toPx(),
                                center = Offset(tankRight - 6.dp.toPx(), yPe)
                            )
                            drawCircle(
                                color = ValuationPePink,
                                radius = 3.2.dp.toPx(),
                                center = Offset(tankRight - 6.dp.toPx(), yPe)
                            )
                        }
                    }

                    drawRoundRect(
                        color = HairlineBorder.copy(alpha = 0.95f),
                        topLeft = Offset(tankLeft, tankTop),
                        size = Size(tankWidth, tankHeight),
                        cornerRadius = CornerRadius(tankCorner, tankCorner),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(tankLeft + 10.dp.toPx(), tankTop + 18.dp.toPx()),
                        end = Offset(tankLeft + 10.dp.toPx(), tankBottom - 18.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    drawTankRulerLabels(
                        textMeasurer = textMeasurer,
                        tankRight = tankRight,
                        tankTop = tankTop,
                        tankHeight = tankHeight
                    )
                }

                // Center bold readout overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(end = 42.dp, top = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f%%", tankLevel),
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp),
                        color = TextPrimary,
                        modifier = Modifier.testTag("main_tank_level_text")
                    )

                    val deltaPositive = dailyDelta >= 0.0
                    val deltaColor = if (deltaPositive) EmeraldBullish else CoralOverheated
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = ObsidianBackground.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, deltaColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (deltaPositive) {
                                    Icons.AutoMirrored.Rounded.TrendingUp
                                } else {
                                    Icons.AutoMirrored.Rounded.TrendingDown
                                },
                                contentDescription = null,
                                tint = deltaColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${formatSignedPercent(dailyDelta)} today",
                                style = MaterialTheme.typography.labelMedium,
                                color = deltaColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = institutionalRegime?.title ?: regime.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    if (peTankLevel != null && peTankLevel.isFinite()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ObsidianBackground.copy(alpha = 0.84f),
                            border = BorderStroke(1.dp, ValuationPePink.copy(alpha = 0.55f)),
                            modifier = Modifier.testTag("hero_tank_valuation_pe_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Canvas(modifier = Modifier.size(width = 12.dp, height = 3.dp)) {
                                    drawLine(
                                        color = ValuationPePink,
                                        start = Offset(0f, size.height / 2f),
                                        end = Offset(size.width, size.height / 2f),
                                        strokeWidth = 2.4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                                Text(
                                    text = buildString {
                                        append(String.format(Locale.US, "Val P/E %.1f%%", peTankLevel))
                                        if (medianPe != null && medianPe.isFinite() && medianPe > 0.0) {
                                            append(String.format(Locale.US, " • %.1f×", medianPe))
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp
                                    ),
                                    color = ValuationPePink,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (kotlin.math.abs(holeThreshold - 50f) > 0.5f) {
                        Text(
                            text = "Simulating ${holeThreshold.toInt()}% Valve",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanLiquid,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right: Vertical Euphoria Pressure (>85) Gauge
            VerticalEuphoriaPressureGauge(
                euphoriaPct = euphoriaPressurePct,
                animatedEuphoria = animatedEuphoria,
                euphoriaCount = euphoriaPressureCount,
                activeCount = activeCount,
                wavePhase = wavePhase1,
                modifier = Modifier
                    .width(90.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun VerticalEuphoriaPressureGauge(
    euphoriaPct: Double,
    animatedEuphoria: Float,
    euphoriaCount: Int,
    activeCount: Int,
    wavePhase: Float,
    modifier: Modifier = Modifier
) {
    val activeAccent = when {
        euphoriaPct >= 62.0 -> CoralOverheated
        euphoriaPct >= 35.0 -> BreakoutAmber
        else -> CoralOverheated.copy(alpha = 0.85f)
    }

    Surface(
        modifier = modifier.testTag("euphoria_vertical_gauge"),
        shape = RoundedCornerShape(16.dp),
        color = ElevatedCardSurface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = CoralOverheated,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "EUPHORIA",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = CoralOverheated,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Score >85",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .width(34.dp)
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val corner = 10.dp.toPx()
                    val rect = Rect(0f, 0f, size.width, size.height)
                    val clip = Path().apply {
                        addRoundRect(RoundRect(rect, CornerRadius(corner, corner)))
                    }
                    val fillFrac = (animatedEuphoria / 100f).coerceIn(0f, 1f)
                    val topY = size.height * (1f - fillFrac)

                    drawPath(path = clip, color = ObsidianBackground)

                    clipPath(clip) {
                        if (fillFrac > 0.01f) {
                            val gaugeWave = Path().apply {
                                moveTo(0f, size.height)
                                val amp = 2.dp.toPx()
                                val step = 4f
                                var x = 0f
                                while (x <= size.width + step) {
                                    val nx = (x / size.width) * 2f * PI.toFloat()
                                    val y = topY + sin(nx + wavePhase) * amp
                                    lineTo(x, y)
                                    x += step
                                }
                                lineTo(size.width, size.height)
                                close()
                            }
                            drawPath(
                                path = gaugeWave,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        CoralOverheated,
                                        Color(0xFFD50000).copy(alpha = 0.88f),
                                        Color(0xFF4A0812)
                                    ),
                                    startY = topY,
                                    endY = size.height
                                )
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.45f),
                                start = Offset(2.dp.toPx(), topY),
                                end = Offset(size.width - 2.dp.toPx(), topY),
                                strokeWidth = 1.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // 25 / 50 / 75 scale lines
                        for (tick in listOf(0.25f, 0.50f, 0.75f)) {
                            val ty = size.height * (1f - tick)
                            drawLine(
                                color = if (tick == 0.75f) CoralOverheated.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
                                start = Offset(0f, ty),
                                end = Offset(size.width, ty),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    drawRoundRect(
                        color = activeAccent.copy(alpha = 0.55f),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.1f%%", euphoriaPct),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 14.sp
                    ),
                    color = CoralOverheated,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("euphoria_gauge_pct_text")
                )
                Text(
                    text = "$euphoriaCount/$activeCount",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.5.sp
                    ),
                    color = TextSecondary
                )
            }
        }
    }
}

private fun DrawScope.drawTankRulerLabels(
    textMeasurer: TextMeasurer,
    tankRight: Float,
    tankTop: Float,
    tankHeight: Float
) {
    val marks = listOf(
        1.0f to "100%",
        0.75f to "75% OB",
        0.50f to "50%",
        0.25f to "25%",
        0.0f to "0%"
    )
    for ((fraction, label) in marks) {
        val y = tankTop + tankHeight * (1f - fraction)
        val isOverbought = fraction == 0.75f
        val tickColor = when {
            isOverbought -> CoralOverheated
            fraction == 0.50f -> CyanLiquid.copy(alpha = 0.8f)
            else -> TextMuted
        }
        drawLine(
            color = tickColor,
            start = Offset(tankRight - 6.dp.toPx(), y),
            end = Offset(tankRight + 4.dp.toPx(), y),
            strokeWidth = 1.3.dp.toPx(),
            cap = StrokeCap.Round
        )
        val measured = textMeasurer.measure(
            text = label,
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 8.5.sp,
                fontWeight = if (isOverbought) FontWeight.Bold else FontWeight.Medium,
                color = tickColor
            )
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = tankRight + 6.dp.toPx(),
                y = (y - measured.size.height / 2f).coerceIn(tankTop - 4f, tankTop + tankHeight - measured.size.height + 4f)
            )
        )
    }
}
