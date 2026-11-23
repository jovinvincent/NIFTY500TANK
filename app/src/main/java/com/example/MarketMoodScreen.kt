package com.example

import android.content.Context
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class MarketIndicatorType {
    INDIA_VIX,
    TICKERTAPE_MMI,
    GOODRETURNS_MMI,
    COMPOSITE_MMI,
    ADVANCE_DECLINE_RATIO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketMoodScreen(
    isDark: Boolean,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // Live update listeners
    val mmiUpdateCount by MarketMoodIndexEngine.mmiUpdateState.collectAsStateWithLifecycle()
    val vixUpdateCount by IndiaVixManager.vixUpdateState.collectAsStateWithLifecycle()
    val adUpdateCount by AdvanceDeclineManager.adUpdateState.collectAsStateWithLifecycle()

    // Fetch data
    val mmiData = remember(mmiUpdateCount, vixUpdateCount, adUpdateCount) {
        MarketMoodIndexEngine.getLatestMMI(context)
    }
    val vixValue = remember(vixUpdateCount) {
        IndiaVixManager.getLatestIndiaVix(context)
    }
    val vixDynamics = remember(vixUpdateCount) {
        IndiaVixManager.getDynamics(System.currentTimeMillis(), "Diversified Equity", context)
    }

    // Trigger immediate refresh on first mount
    LaunchedEffect(Unit) {
        AdvanceDeclineManager.loadDataIfNeeded(context.applicationContext)
        AdvanceDeclineManager.triggerLiveAdvanceDeclineFetch(context.applicationContext)
        MarketMoodIndexEngine.triggerLiveMmiFetch(context.applicationContext)
        IndiaVixManager.triggerLiveIndiaVixFetch(context.applicationContext)
    }

    var selectedIndicatorForChart by remember { mutableStateOf<MarketIndicatorType?>(null) }

    val bgColor = if (isDark) Color(0xFF101214) else Color(0xFFF6F8FA)
    val cardBg = if (isDark) Color(0xFF181B1E) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1E2229)
    val textSecondary = if (isDark) Color(0xFF9E9E9E) else Color(0xFF6B7280)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .testTag("market_mood_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState()
        ) {
            // Top Navigation & Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onBackToDashboard() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Return to Dashboard",
                                tint = textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Dashboard",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                        }

                        // Swipe Page Indicator Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.20f else 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Market Mood • Live",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Refresh button
                        IconButton(
                            onClick = {
                                if (!isRefreshing) {
                                    isRefreshing = true
                                    coroutineScope.launch {
                                        AdvanceDeclineManager.loadDataIfNeeded(context.applicationContext)
                                        AdvanceDeclineManager.triggerLiveAdvanceDeclineFetch(context.applicationContext)
                                        MarketMoodIndexEngine.triggerLiveMmiFetch(context.applicationContext)
                                        IndiaVixManager.triggerLiveIndiaVixFetch(context.applicationContext)
                                        delay(1200)
                                        isRefreshing = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag("refresh_market_mood_btn")
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Market Feeds",
                                    tint = textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Market Mood & Volatility",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Real-time institutional sentiment & NSE India VIX indicators. Swipe left/right between Home & Mood.",
                        fontSize = 12.sp,
                        color = textSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Overall Composite Sentiment Banner
            item {
                CompositeSentimentBanner(
                    mmiData = mmiData,
                    vixDynamics = vixDynamics,
                    isDark = isDark,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { selectedIndicatorForChart = MarketIndicatorType.COMPOSITE_MMI }
                )
            }

            // Indicator 1: India VIX Card
            item {
                IndiaVixIndicatorCard(
                    vix = vixValue,
                    dynamics = vixDynamics,
                    isDark = isDark,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { selectedIndicatorForChart = MarketIndicatorType.INDIA_VIX }
                )
            }

            // Indicator 2: Tickertape MMI Card
            item {
                TickertapeMmiIndicatorCard(
                    mmiData = mmiData,
                    isDark = isDark,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { selectedIndicatorForChart = MarketIndicatorType.TICKERTAPE_MMI }
                )
            }

            // Indicator 3: Goodreturns Today's Market Mood Card
            item {
                GoodreturnsTodayMoodCard(
                    mmiData = mmiData,
                    isDark = isDark,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { selectedIndicatorForChart = MarketIndicatorType.GOODRETURNS_MMI }
                )
            }

            // Indicator 4: Authentic Goodreturns Historical Market Mood Index Chart Card
            item {
                GoodreturnsHistoricalChartCard(
                    isDark = isDark,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }

            // Indicator 5: Advance / Decline Ratio Card (BSE Sensex, Nifty 50, Nifty Next 50, Nifty Midcap 150, Nifty Smallcap 250, Nifty 500, Nifty 1000, All NSE Equity)
            item {
                AdvanceDeclineRatioCard(
                    isDark = isDark,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onOpenChart = { selectedIndicatorForChart = MarketIndicatorType.ADVANCE_DECLINE_RATIO }
                )
            }

            // Footer note
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Continuous Live Feeds • Fallback to Official NSE & Recorded History during Market Holidays",
                        fontSize = 10.sp,
                        color = textSecondary.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Full Interactive Historical Chart Modal
        selectedIndicatorForChart?.let { indicatorType ->
            MarketIndicatorChartModal(
                indicatorType = indicatorType,
                isDark = isDark,
                onDismiss = { selectedIndicatorForChart = null }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// MARKET CAPITULATION & EUPHORIA INDEX BANNER (0–100 TRIPLE-LOCK SYSTEM)
// -----------------------------------------------------------------------------
@Composable
private fun CompositeSentimentBanner(
    mmiData: MarketMoodIndexData,
    vixDynamics: IndiaVixDynamics,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val capData = mmiData.capitulationData ?: remember {
        MarketMoodIndexEngine.getLatestCapitulationIndex(context)
    }
    val score = capData.score

    val scoreColor = when {
        score >= 85.0 -> Color(0xFF10B981) // Blind Lumpsum / Capitulation
        score >= 68.0 -> Color(0xFF34D399) // Staggered Accumulation
        score >= 48.0 -> Color(0xFFF59E0B) // Temporary Correction / Wait
        score >= 27.0 -> Color(0xFF38BDF8) // Wealth Creation / Stay Invested
        score >= 16.0 -> Color(0xFFFB923C) // Extended Rally / Caution
        else -> Color(0xFFEF4444)          // Euphoric Top / Profit Booking
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
            .testTag("capitulation_euphoria_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, scoreColor.copy(alpha = if (isDark) 0.40f else 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: CYCLE-AWARE Tag + Title + Regime Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = scoreColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, scoreColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "CYCLE-AWARE OUTCOME SYSTEM",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scoreColor.copy(alpha = if (isDark) 0.22f else 0.14f),
                    border = BorderStroke(0.8.dp, scoreColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = capData.regimeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Market Capitulation & Euphoria Index (0–100)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Big Score Row + Gate & VIX Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", score),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary
                    )
                    Text(
                        text = " / 100",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (capData.isGateUnlocked) Color(0xFF10B981).copy(alpha = 0.15f)
                        else Color(0xFF6B7280).copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, if (capData.isGateUnlocked) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF6B7280).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (capData.isGateUnlocked) "Gate Unlocked 🔓" else "Gate Filtered 🔒",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (capData.isGateUnlocked) Color(0xFF10B981) else Color(0xFF9CA3AF),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF22262B) else Color(0xFFF1F3F5),
                        border = BorderStroke(0.6.dp, if (isDark) Color(0xFF333A42) else Color(0xFFCBD5E1))
                    ) {
                        Text(
                            text = "VIX: ${String.format(Locale.US, "%.1f", capData.rawVix)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Evolving Historical-Learning Investment Model • 8-Index Exhaustion & Macro Cycle",
                fontSize = 10.5.sp,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Triple-Lock Factor Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MceiFactorBox(
                    title = "Breadth Intensity",
                    value = "${String.format(Locale.US, "%.1f", capData.breadthIntensity)} / 40",
                    sub = "8 Indices Multi-Wk",
                    color = Color(0xFF10B981),
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                MceiFactorBox(
                    title = "Divergence Factor",
                    value = "${String.format(Locale.US, "%.1f", capData.divergenceFactor)} / 35",
                    sub = "${String.format(Locale.US, "%.1fx", capData.divergenceRatio)} Ratio",
                    color = Color(0xFF38BDF8),
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                MceiFactorBox(
                    title = "Panic / Volatility",
                    value = "${String.format(Locale.US, "%.1f", capData.panicFactor)} / 25",
                    sub = "MMI ${capData.rawMmi.roundToInt()}",
                    color = Color(0xFFF59E0B),
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }

            if (capData.closestHistoricalAnalog.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.12f else 0.08f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cycle Analog: ${capData.closestHistoricalAnalog}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${capData.learnedEpisodesCount} cycles learned",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            if (capData.isEuphoriaOverride) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Overbought Multi-Year Euphoria Active (MMI ${capData.rawMmi.roundToInt()}): Score ${String.format(Locale.US, "%.1f", capData.score)} (0–16 Profit Booking Zone).",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Directive Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = scoreColor.copy(alpha = if (isDark) 0.12f else 0.08f),
                border = BorderStroke(0.6.dp, scoreColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(26.dp)
                            .background(scoreColor, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = capData.actionDirective,
                        fontSize = 11.5.sp,
                        color = textPrimary,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tap hint button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(scoreColor.copy(alpha = if (isDark) 0.14f else 0.08f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = scoreColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Explore Historical MCEI Curve (01-04-2016 to Live)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scoreColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tap ➔",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun MceiFactorBox(
    title: String,
    value: String,
    sub: String,
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF22262B) else Color(0xFFF1F3F5),
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 8.5.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = sub, fontSize = 8.5.sp, color = Color.Gray)
        }
    }
}

// -----------------------------------------------------------------------------
// INDICATOR 1: INDIA VIX CARD
// -----------------------------------------------------------------------------
@Composable
private fun IndiaVixIndicatorCard(
    vix: Double,
    dynamics: IndiaVixDynamics,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val vixColor = when {
        vix < 12.0 -> Color(0xFFF59E0B)
        vix < 15.5 -> Color(0xFF10B981)
        vix < 19.0 -> Color(0xFFFB923C)
        vix < 24.0 -> Color(0xFFEF4444)
        else -> Color(0xFFA855F7)
    }

    val deltaIsGood = dynamics.delta5d <= 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
            .testTag("india_vix_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, vixColor.copy(alpha = if (isDark) 0.35f else 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Tag + Regime Pill, followed by full-width Title so "1. India VIX" never truncates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, Color(0xFF0284C7).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "NSE OFFICIAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = vixColor.copy(alpha = if (isDark) 0.22f else 0.12f),
                    border = BorderStroke(0.8.dp, vixColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = dynamics.regimeLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = vixColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "1. India VIX",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = String.format(Locale.US, "%.2f", vix),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary
                    )
                    Text(
                        text = "Volatility Index (NSE India)",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (deltaIsGood) Color(0xFF10B981) else Color(0xFFEF4444)).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "5D: %+.2f (%+.1f%%)", dynamics.delta5d, dynamics.deltaPct5d),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (deltaIsGood) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Trend: ${dynamics.trendLabel}",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-metrics row: Evenly balanced 4-column cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VixStatPill(title = "20D Δ", value = String.format(Locale.US, "%+.2f", dynamics.delta20d), isDark = isDark, modifier = Modifier.weight(1f))
                VixStatPill(title = "Percentile", value = "${dynamics.percentileRank.roundToInt()}th", isDark = isDark, modifier = Modifier.weight(1f))
                VixStatPill(title = "Beta", value = String.format(Locale.US, "%.2f", dynamics.categoryBeta), isDark = isDark, modifier = Modifier.weight(1f))
                val cleanStatus = when {
                    dynamics.acceleration > 0.5 -> "Surging 🔺"
                    dynamics.delta5d > 0.5 -> "Expanding ⚠️"
                    dynamics.delta5d < -0.5 -> "Cooling 🟢"
                    else -> "Calm 🟢"
                }
                VixStatPill(title = "Status", value = cleanStatus, isDark = isDark, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tap hint button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.12f else 0.08f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Explore historical chart (01-04-2016 to Live)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tap ➔",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun VixStatPill(title: String, value: String, isDark: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF22262B) else Color(0xFFF1F3F5),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// -----------------------------------------------------------------------------
// INDICATOR 2: TICKERTAPE MMI CARD
// -----------------------------------------------------------------------------
@Composable
private fun TickertapeMmiIndicatorCard(
    mmiData: MarketMoodIndexData,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val score = mmiData.tickertapeScore
    val scoreColor = when {
        score <= 25.0 -> Color(0xFF1EAA59)
        score <= 45.0 -> Color(0xFFE8AC11)
        score <= 55.0 -> Color(0xFF9E9E9E)
        score <= 75.0 -> Color(0xFFE8831D)
        else -> Color(0xFFDE3B32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
            .testTag("tickertape_mmi_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, scoreColor.copy(alpha = if (isDark) 0.35f else 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "TMMI",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scoreColor.copy(alpha = if (isDark) 0.22f else 0.12f),
                    border = BorderStroke(0.8.dp, scoreColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = mmiData.tickertapeZone,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "2. Tickertape MMI",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.2f", score),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = textPrimary
                        )
                        Text(
                            text = " / 100",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }
                    Text(
                        text = "Institutional 6-Factor Model",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF22262B) else Color(0xFFF1F3F5)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(text = "Zone", fontSize = 9.sp, color = Color.Gray)
                        Text(text = mmiData.tickertapeZone.replace("🟢", "").replace("🔴", "").replace("🟠", "").trim(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Range meter bar
            MmiHorizontalMeterBar(score = score)

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-factors breakdown
            Text(
                text = "Key Sub-Factors (0-100 scale)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MmiFactorMiniChip("Momentum", mmiData.momentumScore, isDark)
                MmiFactorMiniChip("Volatility", mmiData.volatilityScore, isDark)
                MmiFactorMiniChip("Breadth", mmiData.marketBreadthScore, isDark)
                MmiFactorMiniChip("Strength", mmiData.priceStrengthScore, isDark)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tap hint button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.12f else 0.08f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Explore historical chart (01-04-2016 to Live)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tap ➔",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun MmiHorizontalMeterBar(score: Double) {
    val clampedScore = score.coerceIn(0.0, 100.0).toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF10B981), // 0-30 Extreme Fear
                            Color(0xFF34D399), // 30-50 Fear
                            Color(0xFFF59E0B), // 50-70 Greed
                            Color(0xFFEF4444)  // 70-100 Extreme Greed
                        )
                    )
                )
        )
        // Indicator arrow/pointer position
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val fraction = clampedScore / 100f
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width * fraction
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(x, 5.dp.toPx())
                )
                drawCircle(
                    color = Color.Black,
                    radius = 2.dp.toPx(),
                    center = Offset(x, 5.dp.toPx())
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0 (Ext Fear)", fontSize = 9.sp, color = Color.Gray)
            Text("30", fontSize = 9.sp, color = Color.Gray)
            Text("50", fontSize = 9.sp, color = Color.Gray)
            Text("70", fontSize = 9.sp, color = Color.Gray)
            Text("100 (Ext Greed)", fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun MmiFactorMiniChip(label: String, score: Double, isDark: Boolean) {
    val factorColor = when {
        score < 30.0 -> Color(0xFF10B981)
        score < 50.0 -> Color(0xFF34D399)
        score < 70.0 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF22262B) else Color(0xFFF1F3F5),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 9.sp, color = Color.Gray)
            Text(
                text = String.format(Locale.US, "%.0f", score),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = factorColor
            )
        }
    }
}

// -----------------------------------------------------------------------------
// INDICATOR 3: GOODRETURNS TODAY'S MARKET MOOD (AUTHENTIC GAUGE & TABLE)
// -----------------------------------------------------------------------------
@Composable
private fun GoodreturnsTodayMoodCard(
    mmiData: MarketMoodIndexData,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val score = mmiData.goodreturnsScore
    val scoreColor = when {
        score <= 25.0 -> Color(0xFF1EAA59)
        score <= 45.0 -> Color(0xFFE8AC11)
        score <= 55.0 -> Color(0xFF9E9E9E)
        score <= 75.0 -> Color(0xFFE8831D)
        else -> Color(0xFFDE3B32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("goodreturns_today_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, scoreColor.copy(alpha = if (isDark) 0.35f else 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: GMMI Tag + Zone Pill, then full-width Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "GMMI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B82F6),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scoreColor.copy(alpha = if (isDark) 0.22f else 0.12f),
                        border = BorderStroke(0.8.dp, scoreColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = mmiData.goodreturnsZone,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Today's Market Mood",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Visual: Gauge on Left, Color Scale Table on Right
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 320.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoodreturnsSpeedometerGauge(
                            score = score,
                            isDark = isDark,
                            modifier = Modifier
                                .weight(1.15f)
                                .clickable { onClick() }
                        )

                        MarketMoodColorScaleTable(
                            isDark = isDark,
                            modifier = Modifier.weight(0.85f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GoodreturnsSpeedometerGauge(
                            score = score,
                            isDark = isDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClick() }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        MarketMoodColorScaleTable(
                            isDark = isDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))

            val zoneLabel = when {
                score <= 25.0 -> "Extreme Fear"
                score <= 45.0 -> "Fear"
                score <= 55.0 -> "Neutral"
                score <= 75.0 -> "Greed"
                else -> "Extreme Greed"
            }
            Text(
                text = buildAnnotatedString {
                    append("Market is in the ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scoreColor)) {
                        append(zoneLabel)
                    }
                    append(" mood indicating ")
                    append(
                        when (zoneLabel) {
                            "Extreme Fear" -> "investors are extremely pessimistic and may represent a buying opportunity."
                            "Fear" -> "investors are worried and cautious about market direction."
                            "Neutral" -> "the market is waiting for a stronger signal before moving."
                            "Greed" -> "investors are turning bullish and confident."
                            else -> "investors are excessively euphoric and market may be overbought."
                        }
                    )
                },
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                color = textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun GoodreturnsSpeedometerGauge(
    score: Double,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.coerceIn(0.0, 100.0).toFloat(),
        animationSpec = tween(durationMillis = 1000),
        label = "goodreturns_gauge_anim"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height - 10.dp.toPx()
                val radius = min(centerX - 10.dp.toPx(), centerY - 4.dp.toPx())

                val strokeWidth = 32.dp.toPx()
                val startAngle = 180f

                data class SegmentInfo(
                    val start: Float,
                    val sweep: Float,
                    val color: Color,
                    val line1: String,
                    val line2: String? = null
                )

                val segments = listOf(
                    SegmentInfo(180f, 45f, Color(0xFF1EAA59), "EXTREME", "FEAR"),
                    SegmentInfo(225f, 36f, Color(0xFFE8AC11), "FEAR"),
                    SegmentInfo(261f, 18f, Color(0xFFC2CBD5), "NEUTRAL"),
                    SegmentInfo(279f, 36f, Color(0xFFE8831D), "GREED"),
                    SegmentInfo(315f, 45f, Color(0xFFDE3B32), "EXTREME", "GREED")
                )

                val arcTopLeft = Offset(centerX - radius, centerY - radius)
                val arcSize = Size(radius * 2, radius * 2)

                // Draw arc segments
                for (seg in segments) {
                    drawArc(
                        color = seg.color,
                        startAngle = seg.start,
                        sweepAngle = seg.sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }

                // Draw segment dividers (white gaps between segments)
                val dividerAngles = listOf(225f, 261f, 279f, 315f)
                val rInner = radius - strokeWidth / 2f
                val rOuter = radius + strokeWidth / 2f
                for (angle in dividerAngles) {
                    val rad = Math.toRadians(angle.toDouble())
                    val x1 = centerX + rInner * cos(rad).toFloat()
                    val y1 = centerY + rInner * sin(rad).toFloat()
                    val x2 = centerX + rOuter * cos(rad).toFloat()
                    val y2 = centerY + rOuter * sin(rad).toFloat()
                    drawLine(
                        color = Color.White,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // Draw labels inside segments
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#151719")
                    textSize = 8.sp.toPx()
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                val labelRadius = radius
                drawContext.canvas.nativeCanvas.apply {
                    for (seg in segments) {
                        val midAngle = seg.start + seg.sweep / 2f
                        val midRad = Math.toRadians(midAngle.toDouble())
                        val lx = centerX + labelRadius * cos(midRad).toFloat()
                        val ly = centerY + labelRadius * sin(midRad).toFloat()

                        save()
                        translate(lx, ly)
                        val rot = when {
                            midAngle < 250f -> midAngle + 90f
                            midAngle in 250f..285f -> 0f
                            else -> midAngle - 90f
                        }
                        rotate(rot)
                        if (seg.line2 != null) {
                            drawText(seg.line1, 0f, -2.dp.toPx(), textPaint)
                            drawText(seg.line2, 0f, 7.dp.toPx(), textPaint)
                        } else {
                            drawText(seg.line1, 0f, 3.dp.toPx(), textPaint)
                        }
                        restore()
                    }
                }

                // Draw Needle
                val needleAngle = startAngle + (animatedScore / 100f * 180f)
                val needleRad = Math.toRadians(needleAngle.toDouble())
                val needleTipR = radius - strokeWidth / 2f - 2.dp.toPx()
                val tipX = centerX + needleTipR * cos(needleRad).toFloat()
                val tipY = centerY + needleTipR * sin(needleRad).toFloat()

                val perpRad = needleRad + Math.PI / 2
                val baseHalfW = 4.dp.toPx()
                val b1X = centerX + baseHalfW * cos(perpRad).toFloat()
                val b1Y = centerY + baseHalfW * sin(perpRad).toFloat()
                val b2X = centerX - baseHalfW * cos(perpRad).toFloat()
                val b2Y = centerY - baseHalfW * sin(perpRad).toFloat()

                val needlePath = Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(b1X, b1Y)
                    lineTo(b2X, b2Y)
                    close()
                }
                // Draw Needle with high contrast color (Pure White / Bright Accent) and clean outline/shadow for perfect visibility
                val needleColor = if (isDark) Color(0xFFFFFF00) else Color(0xFF000000)
                val pivotColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

                drawPath(needlePath, color = needleColor)
                drawPath(needlePath, color = if (isDark) Color.Black else Color.White, style = Stroke(width = 1.dp.toPx()))

                // Center pivot hub
                drawCircle(color = pivotColor, radius = 12.dp.toPx(), center = Offset(centerX, centerY))
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(centerX, centerY))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Updated on : ${MarketMoodIndexEngine.liveUpdatedDate}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
        )
    }
}

@Composable
private fun MarketMoodColorScaleTable(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isDark) Color(0xFF333A42) else Color(0xFFE2E8F0)
    val headerBg = if (isDark) Color(0xFF20242A) else Color(0xFFF8FAFC)
    val textHead = if (isDark) Color.White else Color(0xFF0F172A)
    val textBody = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
    ) {
        // Table Title
        Text(
            text = "Market Mood Color Scale",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = textHead,
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        HorizontalDivider(color = borderColor, thickness = 1.dp)

        // Header Row: Color | Zone | Range
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Color", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textHead, modifier = Modifier.weight(0.9f))
            Text("Zone", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textHead, modifier = Modifier.weight(1.8f))
            Text("Range", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textHead, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = borderColor, thickness = 0.8.dp)

        // Data Rows
        val rows = listOf(
            Triple(Color(0xFF1EAA59), "Extreme Fear", "0-25"),
            Triple(Color(0xFFE8AC11), "Fear", "26-45"),
            Triple(Color(0xFFC2CBD5), "Neutral", "46-55"),
            Triple(Color(0xFFE8831D), "Greed", "56-75"),
            Triple(Color(0xFFDE3B32), "Extreme Greed", "76-100")
        )

        rows.forEachIndexed { idx, (c, zone, range) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(0.9f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(c)
                    )
                }
                Text(zone, fontSize = 10.sp, color = textBody, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.8f))
                Text(range, fontSize = 10.sp, color = textBody, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
            }
            if (idx < rows.lastIndex) {
                HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// INDICATOR 4: AUTHENTIC GOODRETURNS HISTORICAL MARKET MOOD INDEX CHART CARD
// -----------------------------------------------------------------------------
@Composable
private fun GoodreturnsHistoricalChartCard(
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf("6M") }
    val seriesPoints = remember(selectedPeriod) {
        MarketMoodIndexEngine.getHistoricalGoodreturnsSeries(selectedPeriod, context)
    }
    val allYearPoints = remember {
        MarketMoodIndexEngine.getHistoricalGoodreturnsSeries("ALL", context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("goodreturns_historical_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, (if (isDark) Color(0xFF2E333B) else Color(0xFFE2E8F0)).copy(alpha = 0.5f))
    ) {
        GoodreturnsHistoricalMmiChart(
            points = seriesPoints,
            allPoints = allYearPoints,
            selectedPeriod = selectedPeriod,
            onPeriodSelected = { selectedPeriod = it },
            isDark = isDark,
            modifier = Modifier.padding(14.dp)
        )
    }
}


private data class IndicatorMeta(
    val title: String,
    val sourceTag: String,
    val unitSuffix: String,
    val defaultThreshold: Double
)

// -----------------------------------------------------------------------------
// INTERACTIVE HISTORICAL CHART MODAL (1M, 3M, 6M, 1Y, 2Y, 5Y, ALL)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketIndicatorChartModal(
    indicatorType: MarketIndicatorType,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val adUpdateCount by AdvanceDeclineManager.adUpdateState.collectAsStateWithLifecycle()
    val vixUpdateCount by IndiaVixManager.vixUpdateState.collectAsStateWithLifecycle()
    val mmiUpdateCount by MarketMoodIndexEngine.mmiUpdateState.collectAsStateWithLifecycle()
    var selectedPeriod by remember(indicatorType) {
        mutableStateOf(if (indicatorType == MarketIndicatorType.COMPOSITE_MMI) "1Y" else "6M")
    }
    val periods = listOf("1M", "3M", "6M", "1Y", "2Y", "5Y", "ALL")
    var selectedAdSegmentKey by remember { mutableStateOf("nifty50") }
    var adAsBreadthPercent by remember { mutableStateOf(false) }
    var highlightTargetTimestamp by remember { mutableStateOf<Long?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val (title, sourceTag, unitSuffix, defaultThreshold) = when (indicatorType) {
        MarketIndicatorType.INDIA_VIX -> IndicatorMeta("India VIX", "NSE Volatility", "", 15.5)
        MarketIndicatorType.TICKERTAPE_MMI -> IndicatorMeta("Tickertape MMI", "6-Factor Sentiment", "/ 100", 50.0)
        MarketIndicatorType.GOODRETURNS_MMI -> IndicatorMeta("Goodreturns MMI", "Authentic Mood History", "/ 100", 50.0)
        MarketIndicatorType.COMPOSITE_MMI -> IndicatorMeta("Capitulation & Euphoria Index", "Evolving MCEI", "/ 100", 85.0)
        MarketIndicatorType.ADVANCE_DECLINE_RATIO -> IndicatorMeta(
            if (adAsBreadthPercent) "Market Breadth %" else "Advance / Decline Ratio",
            "A/D Breadth",
            if (adAsBreadthPercent) "%" else "x",
            if (adAsBreadthPercent) 50.0 else 1.0
        )
    }

    // Load historical points based on period
    val seriesPoints: List<Pair<Long, Double>> = remember(
        indicatorType, selectedPeriod, selectedAdSegmentKey, adAsBreadthPercent,
        adUpdateCount, vixUpdateCount, mmiUpdateCount
    ) {
        when (indicatorType) {
            MarketIndicatorType.INDIA_VIX -> IndiaVixManager.getHistoricalSeries(selectedPeriod, context)
            MarketIndicatorType.TICKERTAPE_MMI -> MarketMoodIndexEngine.getHistoricalTickertapeSeries(selectedPeriod, context)
            MarketIndicatorType.GOODRETURNS_MMI -> MarketMoodIndexEngine.getHistoricalGoodreturnsSeries(selectedPeriod, context)
            MarketIndicatorType.COMPOSITE_MMI -> MarketMoodIndexEngine.getHistoricalCompositeSeries(selectedPeriod, context)
            MarketIndicatorType.ADVANCE_DECLINE_RATIO -> AdvanceDeclineManager.getHistoricalSeries(
                selectedAdSegmentKey,
                selectedPeriod,
                adAsBreadthPercent,
                context
            )
        }
    }

    val stats = remember(seriesPoints) {
        if (seriesPoints.isEmpty()) {
            Triple(0.0, 0.0, 0.0)
        } else {
            val vals = seriesPoints.map { it.second }
            val minV = vals.minOrNull() ?: 0.0
            val maxV = vals.maxOrNull() ?: 0.0
            val avgV = vals.average()
            Triple(minV, maxV, avgV)
        }
    }

    val netChange = remember(seriesPoints) {
        if (seriesPoints.size >= 2) {
            val first = seriesPoints.first().second
            val last = seriesPoints.last().second
            val delta = last - first
            val pct = if (first > 0.0) (delta / first) * 100.0 else 0.0
            Pair(delta, pct)
        } else {
            Pair(0.0, 0.0)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF14171A) else Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = sourceTag,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color.White else Color(0xFF1E2229)
                        )
                    }
                    Text(
                        text = if (indicatorType == MarketIndicatorType.COMPOSITE_MMI)
                            "Evolving Historical-Learning Investment Model (0-100) • 01-04-2016 to Live"
                        else
                            "Historical Interactive Chart & Range Analysis (01-04-2016 to Live)",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (indicatorType == MarketIndicatorType.GOODRETURNS_MMI) {
                val allYearPoints = remember {
                    MarketMoodIndexEngine.getHistoricalGoodreturnsSeries("ALL", context)
                }
                GoodreturnsHistoricalMmiChart(
                    points = seriesPoints,
                    allPoints = allYearPoints,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = { selectedPeriod = it },
                    isDark = isDark
                )
            } else {
                if (indicatorType == MarketIndicatorType.ADVANCE_DECLINE_RATIO) {
                    val adSegments = listOf(
                        "bseSensex" to "BSE Sensex",
                        "nifty50" to "Nifty 50",
                        "niftyNext50" to "Next 50",
                        "niftyMidcap150" to "Midcap 150",
                        "niftySmallcap250" to "Smallcap 250",
                        "nifty500" to "Nifty 500",
                        "nifty1000" to "Nifty 1000",
                        "allNseEquity" to "All NSE"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        adSegments.forEach { (key, label) ->
                            val isSelected = selectedAdSegmentKey == key
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAdSegmentKey = key },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !adAsBreadthPercent,
                            onClick = { adAsBreadthPercent = false },
                            label = { Text("A/D Ratio", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = adAsBreadthPercent,
                            onClick = { adAsBreadthPercent = true },
                            label = { Text("Breadth % (Adv %)", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Period Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    periods.forEach { period ->
                        val isSelected = selectedPeriod == period
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPeriod = period
                                highlightTargetTimestamp = null
                            },
                            label = {
                                Text(
                                    text = period,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // For Capitulation Index: Historical market cycle checkpoints (organic scores, zero forced dates)
                if (indicatorType == MarketIndicatorType.COMPOSITE_MMI) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Historical Cycle Checkpoints (01-04-2016 to Live • Tap to Inspect):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val benchmarkCheckpoints = remember(adUpdateCount, vixUpdateCount, mmiUpdateCount) {
                        val keySpecs = listOf(
                            "26 Dec '16" to "2016-12-26",
                            "23 Jan '18" to "2018-01-23",
                            "26 Oct '18" to "2018-10-26",
                            "22 Aug '19" to "2019-08-22",
                            "24 Mar '20" to "2020-03-24",
                            "18 Oct '21" to "2021-10-18",
                            "20 Jun '22" to "2022-06-20",
                            "02 Jun '23" to "2023-06-02",
                            "01 Dec '23" to "2023-12-01",
                            "26 Sep '24" to "2024-09-26",
                            "21 Nov '24" to "2024-11-21",
                            "03 Mar '25" to "2025-03-03",
                            "30 Mar '26" to "2026-03-30",
                            "23 Oct '26" to "2026-10-23"
                        )
                        keySpecs.mapNotNull { (shortName, isoDate) ->
                            val ts = IndexBenchmarkSimulator.parseAnyDateToMillis(isoDate)
                            if (ts != null) {
                                val sc = MarketMoodIndexEngine.getCapitulationIndexForDate(ts, context).score
                                Triple("$shortName (${String.format(Locale.US, "%.1f", sc)})", ts, sc)
                            } else null
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        benchmarkCheckpoints.forEach { (label, ts, sc) ->
                            val chipColor = when {
                                sc >= 68.0 -> Color(0xFF10B981)
                                sc >= 48.0 -> Color(0xFFF59E0B)
                                sc >= 27.0 -> Color(0xFF38BDF8)
                                else -> Color(0xFFEF4444)
                            }
                            Surface(
                                onClick = {
                                    selectedPeriod = "ALL"
                                    highlightTargetTimestamp = ts
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = chipColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                                border = BorderStroke(0.8.dp, chipColor.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = chipColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChartStatItem(label = "Period Min", value = String.format(Locale.US, "%.2f", stats.first), isDark = isDark)
                    ChartStatItem(label = "Period Avg", value = String.format(Locale.US, "%.2f", stats.third), isDark = isDark)
                    ChartStatItem(label = "Period Max", value = String.format(Locale.US, "%.2f", stats.second), isDark = isDark)
                    ChartStatItem(
                        label = "Net Change",
                        value = String.format(Locale.US, "%+.2f (%+.1f%%)", netChange.first, netChange.second),
                        isDark = isDark,
                        valueColor = if (netChange.first <= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Line Chart
                InteractiveHistoricalChartCanvas(
                    points = seriesPoints,
                    indicatorType = indicatorType,
                    isDark = isDark,
                    highlightTargetTimestamp = highlightTargetTimestamp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChartStatItem(
    label: String,
    value: String,
    isDark: Boolean,
    valueColor: Color? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color(0xFF1E2228) else Color(0xFFF1F3F5),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 9.sp, color = Color.Gray)
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: (if (isDark) Color.White else Color(0xFF1E2229))
            )
        }
    }
}

// -----------------------------------------------------------------------------
// INTERACTIVE CANVAS CHART WITH TOUCH SCRUBBING & CAPITULATION BANDS
// -----------------------------------------------------------------------------
@Composable
private fun InteractiveHistoricalChartCanvas(
    points: List<Pair<Long, Double>>,
    indicatorType: MarketIndicatorType,
    isDark: Boolean,
    highlightTargetTimestamp: Long? = null,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("Loading historical data...", color = Color.Gray)
        }
        return
    }

    val isCompositeMCEI = indicatorType == MarketIndicatorType.COMPOSITE_MMI
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(highlightTargetTimestamp, points) {
        if (highlightTargetTimestamp != null && points.isNotEmpty()) {
            val bestIdx = points.indices.minByOrNull { abs(points[it].first - highlightTargetTimestamp) }
            if (bestIdx != null && abs(points[bestIdx].first - highlightTargetTimestamp) <= 86400000L * 5) {
                selectedIndex = bestIdx
            }
        }
    }

    val primaryColor = when (indicatorType) {
        MarketIndicatorType.INDIA_VIX -> Color(0xFF38BDF8)
        MarketIndicatorType.TICKERTAPE_MMI -> Color(0xFF10B981)
        MarketIndicatorType.GOODRETURNS_MMI -> Color(0xFFF59E0B)
        MarketIndicatorType.COMPOSITE_MMI -> Color(0xFF06B6D4)
        MarketIndicatorType.ADVANCE_DECLINE_RATIO -> Color(0xFF10B981)
    }

    val values = points.map { it.second }
    val minVal = if (isCompositeMCEI) 0.0 else (values.minOrNull() ?: 0.0)
    val maxVal = if (isCompositeMCEI) 100.0 else (values.maxOrNull() ?: 100.0)
    val valRange = if (maxVal > minVal) (maxVal - minVal) else 1.0

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    Column(modifier = modifier) {
        // Scrubbing readout pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            contentAlignment = Alignment.Center
        ) {
            val activePoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
            if (activePoint != null) {
                val score = activePoint.second
                val statusText = if (isCompositeMCEI) {
                    val regime = when {
                        score >= 85.0 -> "Blind Lumpsum 🟢 (85-100)"
                        score >= 68.0 -> "Staggered Accum 🟢 (68-85)"
                        score >= 48.0 -> "Correction / Wait 🟡 (48-68)"
                        score >= 27.0 -> "Stay Invested 🔵 (27-48)"
                        score >= 16.0 -> "Caution 🟠 (16-27)"
                        else -> "Profit Booking 🔴 (0-16)"
                    }
                    "${dateFormat.format(Date(activePoint.first))}:  ${String.format(Locale.US, "%.1f", score)} / 100  •  $regime"
                } else {
                    "${dateFormat.format(Date(activePoint.first))}:  ${String.format(Locale.US, "%.2f", activePoint.second)}"
                }

                val pillBorderColor = if (isCompositeMCEI) {
                    when {
                        score >= 85.0 -> Color(0xFF10B981)
                        score >= 68.0 -> Color(0xFF34D399)
                        score >= 48.0 -> Color(0xFFF59E0B)
                        score >= 27.0 -> Color(0xFF38BDF8)
                        else -> Color(0xFFEF4444)
                    }
                } else primaryColor

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pillBorderColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, pillBorderColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Chart Area with Pointer Drag Detection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val n = points.size
                            if (n > 1) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (fraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val n = points.size
                            if (n > 1) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (fraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
                            }
                        },
                        onDragEnd = { /* keep point pinned */ },
                        onDragCancel = { /* keep point pinned */ }
                    )
                }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val n = points.size
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        selectedIndex = (fraction * (n - 1)).roundToInt()
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val n = points.size

                if (n < 2) return@Canvas

                val paddingY = 16.dp.toPx()
                val usableHeight = h - paddingY * 2

                fun getY(v: Double): Float {
                    val normalized = ((v - minVal) / valRange).toFloat()
                    return (h - paddingY) - (normalized * usableHeight)
                }

                fun getX(index: Int): Float {
                    return (index.toFloat() / (n - 1)) * w
                }

                // Reference guidelines and regime bands
                val gridColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

                if (isCompositeMCEI) {
                    val y100 = getY(100.0)
                    val y85 = getY(85.0)
                    val y16 = getY(16.0)
                    val y0 = getY(0.0)
                    val y48 = getY(48.0)

                    // Green Capitulation Band (85 to 100)
                    drawRect(
                        color = Color(0xFF10B981).copy(alpha = if (isDark) 0.18f else 0.12f),
                        topLeft = Offset(0f, y100),
                        size = Size(w, max(0f, y85 - y100))
                    )
                    // Red Euphoria Band (0 to 16)
                    drawRect(
                        color = Color(0xFFEF4444).copy(alpha = if (isDark) 0.18f else 0.12f),
                        topLeft = Offset(0f, y16),
                        size = Size(w, max(0f, y0 - y16))
                    )

                    // 85 Dashed Threshold
                    drawLine(
                        color = Color(0xFF10B981).copy(alpha = 0.8f),
                        start = Offset(0f, y85),
                        end = Offset(w, y85),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                    // 16 Dashed Threshold
                    drawLine(
                        color = Color(0xFFEF4444).copy(alpha = 0.8f),
                        start = Offset(0f, y16),
                        end = Offset(w, y16),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                    // 48 Bull/Correction Boundary Line
                    drawLine(
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.15f),
                        start = Offset(0f, y48),
                        end = Offset(w, y48),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )

                    // Text labels for zones
                    drawContext.canvas.nativeCanvas.apply {
                        val textPaintCap = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#10B981")
                            textSize = 8.5.sp.toPx()
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        val textPaintEuph = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#EF4444")
                            textSize = 8.5.sp.toPx()
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        val textPaintMid = android.graphics.Paint().apply {
                            color = if (isDark) android.graphics.Color.parseColor("#94A3B8") else android.graphics.Color.parseColor("#64748B")
                            textSize = 8.sp.toPx()
                            isAntiAlias = true
                        }
                        drawText("CAPITULATION ≥ 85 (Blind Lumpsum)", 8.dp.toPx(), y85 - 4.dp.toPx(), textPaintCap)
                        drawText("EUPHORIA ≤ 16 (Profit Booking)", 8.dp.toPx(), y16 + 10.dp.toPx(), textPaintEuph)
                        drawText("48 Stay Invested / Correction", 8.dp.toPx(), y48 - 3.dp.toPx(), textPaintMid)
                    }
                } else {
                    drawLine(gridColor, Offset(0f, getY(minVal)), Offset(w, getY(minVal)), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, getY((minVal + maxVal) / 2)), Offset(w, getY((minVal + maxVal) / 2)), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, getY(maxVal)), Offset(w, getY(maxVal)), strokeWidth = 1f)
                }

                // Build line path & area path
                val linePath = Path()
                val areaPath = Path()

                areaPath.moveTo(0f, h)
                linePath.moveTo(0f, getY(points[0].second))
                areaPath.lineTo(0f, getY(points[0].second))

                for (i in 1 until n) {
                    val prevX = getX(i - 1)
                    val prevY = getY(points[i - 1].second)
                    val currX = getX(i)
                    val currY = getY(points[i].second)

                    // Smooth bezier curve control points
                    val ctrlX1 = prevX + (currX - prevX) / 2f
                    val ctrlY1 = prevY
                    val ctrlX2 = prevX + (currX - prevX) / 2f
                    val ctrlY2 = currY

                    linePath.cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, currX, currY)
                    areaPath.cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, currX, currY)
                }

                areaPath.lineTo(w, h)
                areaPath.close()

                // Draw gradient fill
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isDark) 0.35f else 0.25f),
                            primaryColor.copy(alpha = 0.02f)
                        )
                    )
                )

                // Draw stroke line
                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw touch scrub crosshair if active
                selectedIndex?.let { idx ->
                    if (idx in 0 until n) {
                        val crossX = getX(idx)
                        val crossY = getY(points[idx].second)

                        // Vertical dashed guide line
                        drawLine(
                            color = primaryColor.copy(alpha = 0.7f),
                            start = Offset(crossX, 0f),
                            end = Offset(crossX, h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )

                        val pointColor = if (isCompositeMCEI) {
                            val sc = points[idx].second
                            when {
                                sc >= 85.0 -> Color(0xFF10B981)
                                sc >= 68.0 -> Color(0xFF34D399)
                                sc >= 48.0 -> Color(0xFFF59E0B)
                                sc <= 16.0 -> Color(0xFFEF4444)
                                else -> primaryColor
                            }
                        } else primaryColor

                        // Outer highlight glow
                        drawCircle(
                            color = pointColor.copy(alpha = 0.35f),
                            radius = 12.dp.toPx(),
                            center = Offset(crossX, crossY)
                        )
                        // Inner solid point
                        drawCircle(
                            color = pointColor,
                            radius = 5.5.dp.toPx(),
                            center = Offset(crossX, crossY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(crossX, crossY)
                        )
                    }
                }
            }
        }

        // X-axis date endpoints
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shortDate = SimpleDateFormat("dd MMM yy", Locale.US)
            if (points.isNotEmpty()) {
                Text(text = shortDate.format(Date(points.first().first)), fontSize = 10.sp, color = Color.Gray)
                if (points.size >= 4) {
                    Text(text = shortDate.format(Date(points[points.size / 2].first)), fontSize = 10.sp, color = Color.Gray)
                }
                Text(text = shortDate.format(Date(points.last().first)), fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// HISTORICAL MARKET MOOD INDEX CHART (AUTHENTIC TO GOODRETURNS.IN)
// -----------------------------------------------------------------------------
@Composable
fun GoodreturnsHistoricalMmiChart(
    points: List<Pair<Long, Double>>,
    allPoints: List<Pair<Long, Double>> = emptyList(),
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val periods = listOf("1M", "3M", "6M", "1Y", "2Y", "5Y", "ALL")
    val fromDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val toDateFormat = remember { SimpleDateFormat("dd MMM yy", Locale.US) }

    val fromDateStr = points.firstOrNull()?.let { fromDateFormat.format(Date(it.first)) } ?: "2016-04-01"
    val toDateStr = points.lastOrNull()?.let { toDateFormat.format(Date(it.first)) } ?: "13 Nov 26"

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val tooltipDateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.US) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Title matching GoodReturns: "Historical Market Mood Index"
        Text(
            text = "Historical Market Mood Index (01-04-2016 to Live)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color(0xFF1E293B)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Timeframe buttons (scrollable 1M..ALL) + Date Range Readout Row
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                periods.forEach { p ->
                    val isSelected = selectedPeriod.equals(p, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (isSelected) Color(0xFF279E74)
                                else (if (isDark) Color(0xFF242930) else Color(0xFFE2E8F0))
                            )
                            .clickable { onPeriodSelected(p) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = p,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "From",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0xFF242930) else Color.White,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF3B424C) else Color(0xFFCBD5E1))
                ) {
                    Text(
                        text = fromDateStr,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White else Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "To",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0xFF242930) else Color.White,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF3B424C) else Color(0xFFCBD5E1))
                ) {
                    Text(
                        text = toDateStr,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White else Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tooltip Banner when scrubbing or displaying active point
        val activePoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
        if (activePoint != null) {
            val aScore = activePoint.second
            val (moodName, moodColor) = when {
                aScore <= 25.0 -> "Extreme Fear" to Color(0xFF27AE60)
                aScore <= 45.0 -> "Fear" to Color(0xFFF1C40F)
                aScore <= 55.0 -> "Neutral" to Color(0xFFBDC3C7)
                aScore <= 75.0 -> "Greed" to Color(0xFFF39C12)
                else -> "Extreme Greed" to Color(0xFFE74C3C)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isDark) Color(0xFF22262C) else Color.White,
                border = BorderStroke(1.dp, moodColor.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tooltipDateFormat.format(Date(activePoint.first)),
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(moodColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Market Mood: $moodName (${String.format(Locale.US, "%.2f", aScore)})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = moodColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Graph Area with 5 Zone PlotBands and Highcharts Teal/Green Curve
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val n = points.size
                            if (n > 1) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (fraction * (n - 1)).toInt().coerceIn(0, n - 1)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val n = points.size
                            if (n > 1) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (fraction * (n - 1)).toInt().coerceIn(0, n - 1)
                            }
                        },
                        onDragEnd = { selectedIndex = null },
                        onDragCancel = { selectedIndex = null }
                    )
                }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val n = points.size
                        if (n > 1) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            selectedIndex = (fraction * (n - 1)).toInt().coerceIn(0, n - 1)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (points.size < 2) return@Canvas

                // 5 Horizontal Zone PlotBands:
                // 100 to 76: Extreme Greed (0 to 0.25h)
                // 75 to 56: Greed (0.25h to 0.45h)
                // 55 to 46: Neutral (0.45h to 0.55h)
                // 45 to 26: Fear (0.55h to 0.75h)
                // 25 to 0: Extreme Fear (0.75h to 1.0h)
                val y75 = h * 0.25f
                val y55 = h * 0.45f
                val y45 = h * 0.55f
                val y25 = h * 0.75f

                // Band 1: Extreme Greed (76 - 100)
                drawRect(
                    color = Color(0xFFE84C3D).copy(alpha = if (isDark) 0.35f else 0.42f),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, y75)
                )
                // Band 2: Greed (56 - 75)
                drawRect(
                    color = Color(0xFFF1970F).copy(alpha = if (isDark) 0.35f else 0.42f),
                    topLeft = Offset(0f, y75),
                    size = Size(w, y55 - y75)
                )
                // Band 3: Neutral (46 - 55)
                drawRect(
                    color = Color(0xFFC0C0C0).copy(alpha = if (isDark) 0.30f else 0.45f),
                    topLeft = Offset(0f, y55),
                    size = Size(w, y45 - y55)
                )
                // Band 4: Fear (26 - 45)
                drawRect(
                    color = Color(0xFFF1C40F).copy(alpha = if (isDark) 0.35f else 0.42f),
                    topLeft = Offset(0f, y45),
                    size = Size(w, y25 - y45)
                )
                // Band 5: Extreme Fear (0 - 25)
                drawRect(
                    color = Color(0xFF1EAA59).copy(alpha = if (isDark) 0.35f else 0.42f),
                    topLeft = Offset(0f, y25),
                    size = Size(w, h - y25)
                )

                // Divider gridlines across zone boundaries
                val gridColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.85f)
                val gridStroke = 1.dp.toPx()
                drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(0f, y75), Offset(w, y75), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(0f, y55), Offset(w, y55), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(0f, y45), Offset(w, y45), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(0f, y25), Offset(w, y25), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = gridStroke)

                // Draw the authentic Goodreturns Green MMI Curve (#279E74)
                val n = points.size
                fun getPointX(idx: Int): Float = (idx.toFloat() / (n - 1)) * w
                fun getPointY(score: Double): Float = h * (1f - (score.toFloat().coerceIn(0f, 100f) / 100f))

                val linePath = Path()
                linePath.moveTo(getPointX(0), getPointY(points[0].second))
                for (i in 1 until n) {
                    val prevX = getPointX(i - 1)
                    val prevY = getPointY(points[i - 1].second)
                    val currX = getPointX(i)
                    val currY = getPointY(points[i].second)
                    val ctrlX1 = prevX + (currX - prevX) / 2f
                    val ctrlY1 = prevY
                    val ctrlX2 = prevX + (currX - prevX) / 2f
                    val ctrlY2 = currY
                    linePath.cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, currX, currY)
                }

                drawPath(
                    path = linePath,
                    color = Color(0xFF279E74),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Interactive Scrubber Indicator
                val activeIdx = selectedIndex
                if (activeIdx != null && activeIdx in points.indices) {
                    val sX = getPointX(activeIdx)
                    val sY = getPointY(points[activeIdx].second)
                    drawLine(
                        color = Color(0xFF279E74).copy(alpha = 0.9f),
                        start = Offset(sX, 0f),
                        end = Offset(sX, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(sX, sY))
                    drawCircle(color = Color(0xFF279E74), radius = 3.5.dp.toPx(), center = Offset(sX, sY))
                }
            }
        }

        // X-Axis date labels along the bottom (adapts format with year for multi-year windows like 2Y, 5Y, ALL)
        val spanDays = if (points.size >= 2) (points.last().first - points.first().first) / 86400000L else 0L
        val axisDateFormat = remember(spanDays) {
            SimpleDateFormat(if (spanDays > 380L) "MMM yy" else "dd. MMM", Locale.US)
        }
        val tickCount = 6
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0 until tickCount) {
                val idx = if (points.size > 1 && tickCount > 1) {
                    ((i.toFloat() / (tickCount - 1)) * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                } else 0
                val label = points.getOrNull(idx)?.let { axisDateFormat.format(Date(it.first)) } ?: ""
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact 5-Zone Legend (matching Goodreturns web: cs_container)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val legendItems = listOf(
                Triple("Extreme Fear", "0-25", Color(0xFF27AE60)),
                Triple("Fear", "26-45", Color(0xFFF1C40F)),
                Triple("Neutral", "46-55", Color(0xFFBDC3C7)),
                Triple("Greed", "56-75", Color(0xFFF39C12)),
                Triple("Extreme Greed", "76-100", Color(0xFFE74C3C))
            )
            legendItems.forEach { (name, range, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
                        )
                    }
                    Text(
                        text = range,
                        fontSize = 8.5.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// INDICATOR 5: ADVANCE / DECLINE RATIO (ALL 8 BENCHMARKS, 01-04-2016 TO LIVE)
// -----------------------------------------------------------------------------
@Composable
fun AdvanceDeclineRatioCard(
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onOpenChart: () -> Unit
) {
    val context = LocalContext.current
    val adUpdateCount by AdvanceDeclineManager.adUpdateState.collectAsStateWithLifecycle()
    val allRecords = remember(adUpdateCount) {
        AdvanceDeclineManager.getAllHistoricalRecords(context)
    }

    var selectedSegmentKey by remember { mutableStateOf("nifty50") }
    var selectedIndex by remember(allRecords) {
        mutableStateOf(if (allRecords.isNotEmpty()) allRecords.size - 1 else 0)
    }

    LaunchedEffect(Unit) {
        AdvanceDeclineManager.triggerLiveAdvanceDeclineFetch(context.applicationContext)
    }

    val currentRecord = remember(allRecords, selectedIndex) {
        if (allRecords.isNotEmpty() && selectedIndex in allRecords.indices) {
            allRecords[selectedIndex]
        } else {
            AdvanceDeclineManager.getLatestRecord(context)
        }
    }

    val isLatest = allRecords.isNotEmpty() && selectedIndex == allRecords.size - 1
    val segment = currentRecord.getSegment(selectedSegmentKey)

    val ratioColor = when {
        segment.ratio >= 2.0 -> Color(0xFF10B981) // Strong Bullish
        segment.ratio >= 1.0 -> Color(0xFF34D399) // Bullish
        segment.ratio >= 0.7 -> Color(0xFFF59E0B) // Neutral / Caution
        segment.ratio >= 0.4 -> Color(0xFFFB923C) // Bearish
        else -> Color(0xFFEF4444)                 // Severe Selling
    }

    val segmentList = listOf(
        "bseSensex" to "BSE Sensex (30)",
        "nifty50" to "Nifty 50 (50)",
        "niftyNext50" to "Nifty Next 50 (50)",
        "niftyMidcap150" to "Nifty Midcap 150",
        "niftySmallcap250" to "Nifty Smallcap 250",
        "nifty500" to "Nifty 500 (500)",
        "nifty1000" to "Nifty 1000",
        "allNseEquity" to "All NSE Equity"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("advance_decline_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, ratioColor.copy(alpha = if (isDark) 0.35f else 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Tag + Pill, then full-width Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "MARKET BREADTH",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ratioColor.copy(alpha = if (isDark) 0.22f else 0.12f),
                    border = BorderStroke(0.8.dp, ratioColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = segment.sentimentLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ratioColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "5. Advance / Decline Ratio",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Authentic daily constituent breadth across all 8 major Indian benchmarks (01-04-2016 to Live)",
                fontSize = 11.sp,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Trading Date & Quick Jumps
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF22262B) else Color(0xFFF1F3F5),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Date: ",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                            Text(
                                text = currentRecord.dateStr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            if (isLatest) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        if (!isLatest && allRecords.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedIndex = allRecords.size - 1 }
                            ) {
                                Text(
                                    text = "Reset to Live",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (allRecords.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = selectedIndex.toFloat(),
                            onValueChange = { selectedIndex = it.roundToInt().coerceIn(0, allRecords.size - 1) },
                            valueRange = 0f..(allRecords.size - 1).toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFCBD5E1)
                            )
                        )

                        // Quick Jump Presets for famous market turning points
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            fun findDateIndex(dateStr: String): Int {
                                val idx = allRecords.indexOfFirst { it.dateStr == dateStr }
                                return if (idx != -1) idx else {
                                    allRecords.indexOfFirst { it.dateStr.startsWith(dateStr.take(7)) }.coerceAtLeast(0)
                                }
                            }

                            val presets = listOf(
                                "Live Latest" to (allRecords.size - 1),
                                "23 Oct '26" to findDateIndex("2026-10-23"),
                                "17 Jun '22" to findDateIndex("2022-06-17"),
                                "24 Mar '20" to findDateIndex("2020-03-24"),
                                "26 Oct '18" to findDateIndex("2018-10-26"),
                                "21 Nov '16" to findDateIndex("2016-11-21"),
                                "01 Apr '16" to 0
                            )

                            presets.forEach { (label, idx) ->
                                val active = selectedIndex == idx
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else (if (isDark) Color(0xFF1E2228) else Color.White),
                                    border = BorderStroke(
                                        0.6.dp,
                                        if (active) MaterialTheme.colorScheme.primary
                                        else (if (isDark) Color(0xFF333A42) else Color(0xFFCBD5E1))
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { selectedIndex = idx.coerceIn(0, allRecords.size - 1) }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) MaterialTheme.colorScheme.primary
                                        else (if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Segment Tabs (All 8 segments)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                segmentList.forEach { (key, label) ->
                    val isSelected = selectedSegmentKey == key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else (if (isDark) Color(0xFF22262B) else Color(0xFFE5E7EB)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .testTag("ad_segment_chip_$key")
                            .clickable { selectedSegmentKey = key }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Big Ratio & Breadth Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.2f", segment.ratio),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = textPrimary
                        )
                        Text(
                            text = " A/D Ratio",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }
                    val displayedTotal = max(1, segment.advances + segment.declines + segment.unchanged)
                    Text(
                        text = "${segment.displayName} ($displayedTotal constituents)",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, "%.1f%%", segment.breadthPercent),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ratioColor
                    )
                    Text(
                        text = if (segment.isBullish) "Bullish Breadth" else "Bearish Breadth",
                        fontSize = 10.sp,
                        color = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Advance / Decline Visual Stacked Bar
            val segmentSum = max(1, segment.advances + segment.declines + segment.unchanged)
            AdvanceDeclineStackedBar(
                advances = segment.advances,
                declines = segment.declines,
                unchanged = segment.unchanged,
                total = segmentSum
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Stat Breakdown Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AdStatBox(
                    label = "Advances",
                    count = segment.advances,
                    pct = (segment.advances.toDouble() / segmentSum) * 100.0,
                    color = Color(0xFF10B981),
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                AdStatBox(
                    label = "Declines",
                    count = segment.declines,
                    pct = (segment.declines.toDouble() / segmentSum) * 100.0,
                    color = Color(0xFFEF4444),
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                AdStatBox(
                    label = "Unchanged",
                    count = segment.unchanged,
                    pct = (segment.unchanged.toDouble() / segmentSum) * 100.0,
                    color = Color(0xFF94A3B8),
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                AdStatBox(
                    label = "Total",
                    count = segmentSum,
                    pct = 100.0,
                    color = MaterialTheme.colorScheme.primary,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Full 8 Segments Comparison Grid
            Text(
                text = "All 8 Market Benchmarks on ${currentRecord.dateStr}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1E2228) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2C323B) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Segment",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(1.65f)
                        )
                        Text(
                            text = "Adv / Dec",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(1.05f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "A/D Ratio",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(0.8f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Breadth %",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(0.8f),
                            textAlign = TextAlign.End
                        )
                    }

                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))

                    val allSegs = currentRecord.getAllSegments()
                    allSegs.forEachIndexed { idx, seg ->
                        val isSelected = seg.indexKey == selectedSegmentKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.20f else 0.12f)
                                     else Color.Transparent
                                )
                                .clickable { selectedSegmentKey = seg.indexKey }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = seg.displayName,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else textPrimary,
                                modifier = Modifier.weight(1.65f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.weight(1.05f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${seg.advances}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                Text(text = " / ", fontSize = 10.sp, color = textSecondary)
                                Text(
                                    text = "${seg.declines}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.2f", seg.ratio),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (seg.ratio >= 1.0) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.weight(0.8f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", seg.breadthPercent),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (seg.breadthPercent >= 50.0) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.weight(0.8f),
                                textAlign = TextAlign.End
                            )
                        }

                        if (idx < allSegs.size - 1) {
                            HorizontalDivider(
                                color = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Explore Historical A/D Trend Tap Button (Single clean line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.12f else 0.08f))
                    .clickable { onOpenChart() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Explore Historical A/D Trend (01-04-2016 to Live)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tap ➔",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun AdvanceDeclineStackedBar(
    advances: Int,
    declines: Int,
    unchanged: Int,
    total: Int
) {
    val actualSum = max(1, advances + declines + unchanged)
    val totalCount = max(total, actualSum)
    val advWeight = (advances.toFloat() / totalCount).coerceIn(0f, 1f)
    val decWeight = (declines.toFloat() / totalCount).coerceIn(0f, 1f - advWeight)
    val uncWeight = (unchanged.toFloat() / totalCount).coerceIn(0f, 1f - advWeight - decWeight)
    val advPctLabel = (advWeight * 100f).roundToInt()
    val uncPctLabel = if (unchanged > 0) (uncWeight * 100f).roundToInt() else 0
    val decPctLabel = (100 - advPctLabel - uncPctLabel).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF333A42))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val advWidth = w * advWeight
                val decWidth = w * decWeight
                val uncWidth = (w - advWidth - decWidth).coerceAtLeast(0f)

                // Advances segment (Green)
                if (advWidth > 0f) {
                    drawRect(
                        color = Color(0xFF10B981),
                        topLeft = Offset(0f, 0f),
                        size = Size(advWidth, h)
                    )
                }
                // Declines segment (Red)
                if (decWidth > 0f) {
                    drawRect(
                        color = Color(0xFFEF4444),
                        topLeft = Offset(advWidth, 0f),
                        size = Size(decWidth, h)
                    )
                }
                // Unchanged segment (Gray)
                if (uncWidth > 0f && unchanged > 0) {
                    drawRect(
                        color = Color(0xFF94A3B8),
                        topLeft = Offset(advWidth + decWidth, 0f),
                        size = Size(uncWidth, h)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Advances $advPctLabel%",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF10B981)
            )
            if (unchanged > 0) {
                Text(
                    text = "Unchanged $uncPctLabel%",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = "Declines $decPctLabel%",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFEF4444)
            )
        }
    }
}

@Composable
private fun AdStatBox(
    label: String,
    count: Int,
    pct: Double,
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF22262B) else Color(0xFFF1F3F5),
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 9.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$count",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = String.format(Locale.US, "%.0f%%", pct),
                fontSize = 8.5.sp,
                color = textSecondaryColor(isDark)
            )
        }
    }
}

private fun textSecondaryColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF9E9E9E) else Color(0xFF6B7280)

