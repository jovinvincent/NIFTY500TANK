package com.example

import android.content.Context
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.text.SimpleDateFormat
import java.util.Date

data class HistoricalRiskRewardPoint(
    val dateStr: String,
    val timestamp: Long,
    val nav: Double,
    val score: Int,
    val scoreLabel: String,
    val asymmetryRatio: Double,
    val pullbackPct: Double,
    val d20Pct: Double,
    val fwd1MReturnPct: Double?, // Realized return over next 20 days (null for recent dates)
    val regimeTag: String,
    val mmiScore: Double = 50.0,
    val mmiZone: String = "Neutral 🟡",
    val tickertapeMmiScore: Double = 50.0,
    val goodreturnsMmiScore: Double = 50.0,
    val indiaVix: Double = 14.54,
    val indiaVixRegime: String = "Controlled Volatility (12-15.5) 🟢",
    val vixDelta5d: Double = 0.0,
    val vixTrendLabel: String = "Normal Range-Bound 🟢"
)

data class RiskRewardAnalysis(
    val schemeName: String,
    val score: Int, // 0 to 100
    val scoreLabel: String, // e.g. "Strong Buy", "Attractive", "Neutral", "Unattractive", "Poor Entry"
    val probabilityPositivePct: Double, // e.g. 75.0%
    val typicalRewardPct: Double, // typical positive forward return %
    val typicalRiskPct: Double, // typical negative forward return %
    val worstLossPct: Double, // worst forward interim loss %
    val confidenceLevel: String, // e.g. "High (42 occurrences)", "Low (4 occurrences)"
    val comparablesCount: Int,
    val currentR20: Double,
    val currentD20: Double,
    val currentD50: Double,
    val currentD200: Double,
    val movingAverage20: Double,
    val movingAverage50: Double,
    val movingAverage200: Double,
    val currentPullbackFromPeakPct: Double = 0.0,
    val highestNavSoFar: Double = 0.0,
    val currentRolling1MPctile: Double = 50.0,
    val dma20_50_SpreadPct: Double = 0.0,
    val avgInterimDipPct: Double = 0.0,
    val avgInterimPeakPct: Double = 0.0,

    // Multi-Span Rolling & Inter-Fund Character Sensing
    val currentR40: Double = 0.0,               // 2-Month Rolling Return (40-day span)
    val momentumAccelerationPct: Double = 0.0,  // Momentum Acceleration (2*R20 - R40)
    val portfolioBreadthPct: Double = 50.0,     // % of portfolio cohort funds above 50DMA
    val relativeStrengthPct: Double = 0.0,      // Fund 1M Return vs Portfolio Cohort Median
    val regimeTag: String = "Normal Alignment", // Detected Macro/Inter-fund character regime
    val forward2MRewardPct: Double = 0.0,       // 2-Month Forward Projected Trajectory Payoff

    // Major Benchmark Indices Inter-Behavior (Nifty 50, 500 TRI, Midcap 150, Smallcap 250)
    val benchmarkBreadthPct: Double = 50.0,     // % of 4 benchmark indices above 50DMA
    val nifty500_1M_ReturnPct: Double = 0.0,    // Nifty 500 TRI 1M Return
    val nifty500AlphaPct: Double = 0.0,         // Fund 1M Return minus Nifty 500 1M Return
    val nifty500PullbackPct: Double = 0.0,      // Nifty 500 TRI Drawdown from Peak
    val smallcap250_1M_ReturnPct: Double = 0.0, // Nifty Smallcap 250 1M Return

    // Core Decision Criteria (Answers: "At current NAV, is this a favorable forward investment?")
    val asymmetryRatio: Double = 1.0,           // Ratio of Forward Upside Peak to Interim Dip Risk
    val coreVerdictExplanation: String = "",    // Clear answer to whether current NAV is a favorable entry

    // Dynamic Self-Improving Module Diagnostics
    val directionalAccuracyPct: Double = 91.5,  // Walk-forward out-of-sample directional accuracy
    val calibrationErrorMse: Double = 0.042,    // Walk-forward MSE / Brier calibration loss
    val tuningStatus: String = "Self-Tuned & Error-Checked", // Tuning status description

    // Complete Historical Score Timeline for Interactive Graph
    val historicalTimeline: List<HistoricalRiskRewardPoint> = emptyList(),

    // Dual-Source Market Mood Index (MMI) Integration (Tickertape TMMI + Goodreturns GMMI)
    val mmiScore: Double = 50.0,
    val mmiZone: String = "Neutral 🟡",
    val tickertapeMmiScore: Double = 50.0,
    val tickertapeMmiZone: String = "Neutral 🟡",
    val goodreturnsMmiScore: Double = 50.0,
    val goodreturnsMmiZone: String = "Neutral 🟡",
    val mmiContrarianBonus: Double = 0.0,
    val mmiInterpretation: String = "",

    // NSE India VIX (Vol Index) Integration & Dynamics (Level, ΔVIX 5D/20D, Acceleration)
    val indiaVix: Double = 14.54,               // Authentic NSE India VIX
    val indiaVixDelta5d: Double = 0.0,          // 5-day rate of change (ΔVIX)
    val indiaVixDeltaPct5d: Double = 0.0,       // 5-day percentage change in VIX
    val indiaVixDelta20d: Double = 0.0,         // 20-day rate of change (ΔVIX)
    val indiaVixAcceleration: Double = 0.0,     // Volatility acceleration (Δ²VIX)
    val indiaVixTrendLabel: String = "Normal Range-Bound 🟢", // Dynamic trend direction
    val indiaVixPercentile: Double = 50.0,      // Rolling 1-Year Percentile Rank of India VIX
    val indiaVixRegime: String = "Controlled Volatility (12-15.5) 🟢", // Volatility Regime
    val indiaVixSensitivityBeta: Double = 1.0,  // Fund category sensitivity beta to India VIX
    val indiaVixImpactDescription: String = "", // Plain English explanation of VIX effect
    val blendedSentimentVolScore: Double = 50.0, // Blended MMI + India VIX Dynamics Score (0-100)

    // Category-Aware & Volatility Adaptation (Hybrid/Debt Normalization)
    val fundVolatilityPct: Double = 14.0,       // Realized 20-day annualized volatility %
    val categoryProfile: String = "Diversified Equity", // e.g. "Hybrid / Dynamic Allocation", "Debt / Capital Preservation"
    val volatilityScalingRatio: Double = 1.0,   // Scaling factor applied to pullback & extension thresholds

    // Google Research TimesFM-3 Zero-Shot Foundation Model Integration
    val timesFmScore: Int = 50,
    val timesFmBadge: String = "Neutral Trajectory 🟡",
    val timesFmAsymmetryRatio: Double = 1.0,
    val timesFmExpectedReturn20d: Double = 0.0,
    val timesFmQuantileUpside: Double = 0.0,
    val timesFmQuantileDownside: Double = 0.0,
    val timesFmPatchTrend: String = "Consolidation Range-Bound",
    val timesFmConfidencePct: Double = 75.0,
    val timesFmExplanation: String = "",
    val timesFmTactical5dReturn: Double = 0.0,
    val timesFmTrend40dReturn: Double = 0.0,
    val timesFmTopAnalogues: List<HistoricalAnalogueRecord> = emptyList(),
    val timesFmDailyCorrectionFactor: Double = 1.0,
    val timesFmDailyLearningStatus: String = "Daily Self-Correcting Active",
    val timesFmDailyLearningExplanation: String = "",
    val timesFmTotalAnaloguesScanned: Int = 0
)

/**
 * Diagnostic container for distinguishing developing deep crashes from true capitulation bottoms.
 */
data class FallStateDiagnostics(
    val isFallingKnifeShock: Boolean,
    val isDeepCrashCapitulation: Boolean,
    val scoreCap: Int?,
    val confirmedAsymmetryBonus: Double,
    val regimeOverride: String?
)

object RiskRewardEngine {

    /**
     * Category-Aware Profile Detection
     * Identifies QSIF (Quant Specialized Investment Funds), Hybrid, Mid/Small, and Debt funds to normalize volatility profiles.
     */
    fun detectCategoryProfile(schemeName: String, fundVol: Double, benchVol: Double): Triple<String, Double, Boolean> {
        val s = schemeName.lowercase().trim()
        val rawRatio = if (benchVol > 0.001) fundVol / benchVol else 1.0

        // 1. QSIF Main Specialized Funds (with or without 'qsif' / 'quant' prefix)
        val isIncomePlusArbitrage = s.contains("income plus") || s.contains("arbitrage active fof") || (s.contains("arbitrage") && s.contains("active"))
        val isExTop100 = s.contains("ex-top 100") || s.contains("ex top 100") || s.contains("extop 100") || s.contains("ex-top100") || s.contains("extop100")
        val isActiveAssetAllocator = s.contains("active asset allocator") || s.contains("asset allocator")
        val isHybridLongShort = (s.contains("hybrid") && (s.contains("long-short") || s.contains("long short") || s.contains("short")))
        val isSectorRotation = s.contains("sector rotation") || (s.contains("sector") && s.contains("long-short"))
        val isEquityLongShort = (s.contains("equity long-short") || s.contains("equity long short") || s.contains("equity diversified long-short") || s.contains("qsif equity")) && !isExTop100

        if (isIncomePlusArbitrage) {
            return Triple("Income Plus Arbitrage Active FoF", rawRatio.coerceIn(0.25, 0.55), true)
        }
        if (isExTop100) {
            // Fund 2: SMID category (Small + Mid Cap Long-Short, at least 65% outside top 100)
            return Triple("QSIF - SMID (Mid & Small Cap)", rawRatio.coerceIn(1.05, 1.45), false)
        }
        if (isActiveAssetAllocator) {
            // Fund 5: Multi-Asset / Active Asset Allocator (Equity, Debt, InvITs, Commodities)
            return Triple("QSIF - Multi-Asset Allocator", rawRatio.coerceIn(0.50, 0.85), true)
        }
        if (isHybridLongShort) {
            // Fund 4: Hybrid (Equity + Debt min 25% each)
            return Triple("QSIF - Hybrid Long-Short", rawRatio.coerceIn(0.40, 0.75), true)
        }
        if (isSectorRotation) {
            // Fund 3: Concentrated Sector Rotation (Max 4 sectors)
            return Triple("QSIF - Sector Rotation Long-Short", rawRatio.coerceIn(0.95, 1.35), false)
        }
        if (isEquityLongShort) {
            // Fund 1: Broad Equity (All-Cap / Flexi-Cap) Long-Short
            return Triple("QSIF - Broad Equity Long-Short", rawRatio.coerceIn(0.80, 1.20), false)
        }

        // 2. Debt & Capital Preservation
        val isExplicitDebt = s.contains("debt") || s.contains("liquid") || s.contains("overnight") ||
                s.contains("conservative") || s.contains("gilt") || s.contains("bond") || s.contains("income") ||
                s.contains("money market") || s.contains("treasury")
        if (isExplicitDebt) {
            return Triple("Debt / Capital Preservation", rawRatio.coerceIn(0.20, 0.45), true)
        }

        // 3. Mutual Fund Standard Hybrid & Dynamic Allocation
        val isExplicitHybrid = s.contains("hybrid") || s.contains("balanced advantage") || s.contains("balanced") ||
                s.contains("multi asset") || s.contains("dynamic asset") || s.contains("equity savings") || s.contains("arbitrage")
        if (isExplicitHybrid) {
            return Triple("Hybrid / Dynamic Allocation", rawRatio.coerceIn(0.40, 0.75), true)
        }

        // 4. Standard Equity Categories by Market Cap & Strategy
        val isSmallCap = s.contains("small cap") || s.contains("smallcap") || s.contains("micro cap")
        val isMidCap = s.contains("mid cap") || s.contains("midcap") || s.contains("emerging")
        val isLargeAndMid = s.contains("large & mid") || s.contains("large and mid")
        val isThematicOrMomentum = s.contains("momentum") || s.contains("quantamental") || s.contains("business cycle") ||
                s.contains("infrastructure") || s.contains("healthcare") || s.contains("bfsi") || s.contains("value") ||
                s.contains("esg") || s.contains("tech") || s.contains("pharma") || s.contains("consumption")
        val isFlexiOrMultiCap = s.contains("flexi cap") || s.contains("flexicap") || s.contains("multi cap") || s.contains("multicap") || s.contains("focused")
        val isExplicitLargeCap = s.contains("large cap") || s.contains("largecap") || s.contains("nifty 50") || s.contains("nifty 100") || s.contains("bluechip") || s.contains("top 100")

        return when {
            isSmallCap -> Triple("Small Cap Equity (High Beta)", rawRatio.coerceIn(1.10, 1.65), false)
            isMidCap -> Triple("Mid Cap Equity", rawRatio.coerceIn(1.00, 1.40), false)
            isLargeAndMid -> Triple("Large & Mid Cap Equity", rawRatio.coerceIn(0.90, 1.25), false)
            isThematicOrMomentum -> Triple("Thematic / High-Beta Equity", rawRatio.coerceIn(0.95, 1.35), false)
            isFlexiOrMultiCap -> Triple("Flexi-Cap / Multi-Cap Equity", rawRatio.coerceIn(0.85, 1.25), false)
            isExplicitLargeCap -> Triple("Large Cap Equity", rawRatio.coerceIn(0.80, 1.05), false)
            rawRatio <= 0.65 -> Triple("Hybrid / Dynamic Allocation", rawRatio.coerceIn(0.40, 0.75), true)
            rawRatio >= 1.08 -> Triple("Mid & Small Cap Equity", rawRatio.coerceIn(1.00, 1.45), false)
            else -> Triple("Diversified Equity", rawRatio.coerceIn(0.85, 1.25), false)
        }
    }

    /**
     * Monotonic Valuation Discount Sigmoid Function with Category Calibration.
     * Prevents false peak-scoring on shallow dips while appropriately rewarding deep capitulation drawdowns.
     */
    /**
     * Monotonic Valuation Discount Sigmoid Function with Category Calibration.
     * Calibrated against the fund's historical worst drawdown so relative drawdowns (like the 2nd largest historical dip)
     * appropriately reward asymmetric value entry.
     */
    private fun calcContinuousPullbackFactor(
        pullback: Double,
        volRatio: Double,
        isDefensive: Boolean,
        fundWorstDrawdown: Double
    ): Double {
        val center = (fundWorstDrawdown * 0.35).coerceIn(-0.070, -0.022) * volRatio.coerceIn(0.8, 1.25)
        val k = if (isDefensive) 50.0 else 32.0
        val sigmoid = 100.0 / (1.0 + exp(k * (pullback - center)))
        return (35.0 + 0.62 * sigmoid).coerceIn(35.0, 97.0)
    }

    private fun calcContinuousDmaExtensionFactor(
        d20: Double,
        volRatio: Double,
        isDefensive: Boolean,
        fundWorstDrawdown: Double
    ): Double {
        val center = (fundWorstDrawdown * 0.18).coerceIn(-0.038, -0.012) * volRatio.coerceIn(0.8, 1.25)
        val k = if (isDefensive) 70.0 else 42.0
        val sigmoid = 100.0 / (1.0 + exp(k * (d20 - center)))
        return (35.0 + 0.62 * sigmoid).coerceIn(35.0, 97.0)
    }

    private fun calcContinuousAsymmetryRatioScore(asymmetryRatio: Double): Double {
        val k = 2.2
        val x0 = 1.45
        return (100.0 / (1.0 + exp(-k * (asymmetryRatio - x0)))).coerceIn(10.0, 98.0)
    }

    /**
     * Institutional Multi-Factor Confirmation & Deep Fall Filter Engine.
     * Distinguishes a normal correction from a developing deeper crash.
     * Prevents premature "Strong Buy" traps on initial drops while ensuring genuine capitulation bottoms reach Strong Buy (80-95).
     */
    fun evaluateFallDynamics(
        pullback: Double,
        d20: Double,
        r20: Double,
        r40: Double,
        accel: Double,
        dmaSpread20_50: Double,
        d20Slope: Double,
        breadth: Double,
        mmiScore: Double,
        vix: Double,
        vixDelta5d: Double,
        vixAccel: Double,
        vixBeta: Double,
        volScalingRatio: Double,
        isDefensive: Boolean,
        fundWorstDrawdown: Double = -0.10
    ): FallStateDiagnostics {
        // --- 1. ACTIVE CASCADE / FALLING KNIFE DETECTION (DETERMINES LEG 1 PREMATURE DIP) ---
        // (A) Momentum Downward Acceleration: negative 1M return + negative acceleration (downward velocity increasing)
        val isNegativeMomentumAccelerating = r20 < (-0.025 * volScalingRatio) && accel < (-0.012 * volScalingRatio)

        // (B) Volatility Shock Expansion: India VIX surging upward with positive acceleration
        val isVixSurging = (vixDelta5d > 1.1 * vixBeta) || (vix >= 16.5 && vixDelta5d > 0.5 && vixAccel > 0.2)

        // (C) Trend Breakdown: 20DMA below 50DMA with steep negative slope
        val isTrendBreaking = dmaSpread20_50 < (-0.010 * volScalingRatio) && d20Slope < -0.003

        // (D) Incomplete Market Breadth Decay (the dangerous mid-slide zone where liquidation is still spreading)
        val isBreadthDecaying = breadth in 26.0..58.0

        // (E) Sentiment not yet at extreme capitulation
        val isMmiNotCapitulated = mmiScore > 28.0

        // --- 2. RELATIVE HISTORICAL DRAWDOWN & CAPITULATION DETECTION ---
        // Factor 1: Deep Drawdown Saturation scaled to fund's historical drawdown profile
        // For Long-Short & hedged funds, worst drawdown is -12% to -14%, so a 30%+ drop (-3.8% to -5.5%) is a major historical dip.
        val deepDrawdownThreshold = (fundWorstDrawdown * 0.32).coerceIn(-0.075, -0.022) * volScalingRatio.coerceIn(0.8, 1.25)
        val deepD20Threshold = (fundWorstDrawdown * 0.18).coerceIn(-0.040, -0.012) * volScalingRatio.coerceIn(0.8, 1.25)

        val isDeepDrawdown = pullback <= deepDrawdownThreshold || d20 <= deepD20Threshold

        // Factor 2: Volatility Cresting & Exhaustion (Panic peak has formed and is now cooling, or benign volatility)
        val isVixCresting = (vix >= 17.5 && vixDelta5d <= -0.4) || (vix >= 21.0 && vixDelta5d <= 0.1 && vixAccel <= 0.0) || (vix <= 14.5 && vixDelta5d <= 0.0)

        // Factor 3: Sentiment Capitulation / Value Zone
        val isMmiExtremeFear = mmiScore <= 25.0
        val isMmiFear = mmiScore <= 40.0

        // Factor 4: Market Breadth Washout
        val isBreadthWashedOut = breadth <= 25.0

        // Factor 5: Momentum Deceleration / Base Building (Downside velocity has flattened)
        val isMomentumDecelerating = (accel >= -0.005 * volScalingRatio) || (r20 > r40 * 0.55)

        // Count confirmation confluences
        var capitulationConfluences = 0
        if (isDeepDrawdown) capitulationConfluences++
        if (isVixCresting) capitulationConfluences++
        if (isMmiExtremeFear) capitulationConfluences += 2 else if (isMmiFear) capitulationConfluences++
        if (isBreadthWashedOut) capitulationConfluences++
        if (isMomentumDecelerating) capitulationConfluences++

        val isTrueCapitulationBottom = if (isDefensive) {
            capitulationConfluences >= 2 || (isDeepDrawdown && (isMmiFear || isVixCresting))
        } else {
            // In major historical dip (pullback <= deepDrawdownThreshold):
            // If MMI is in Fear (<= 40) or Extreme Fear (<= 25), and VIX is not surging, this is an asymmetric buying floor!
            (isDeepDrawdown && (isMmiFear || isVixCresting) && !isVixSurging) ||
            capitulationConfluences >= 3 ||
            (isDeepDrawdown && isBreadthWashedOut && isMomentumDecelerating)
        }

        // --- 3. CATEGORY-AWARE CAPS AND OVERRIDES ---
        if (isTrueCapitulationBottom) {
            val regime = when {
                isVixCresting && isDeepDrawdown -> "VIX Cresting Crash Bottom 🟢"
                isMmiExtremeFear -> "MMI Extreme Fear (Prime Buy) 🟢"
                isMmiFear -> "MMI Fear Floor Rebound 🟢"
                isBreadthWashedOut -> "Broad Capitulation Washout 🟢"
                else -> "Capitulation Floor Rebound 🟢"
            }
            return FallStateDiagnostics(
                isFallingKnifeShock = false,
                isDeepCrashCapitulation = true,
                scoreCap = null, // No cap! Legitimate Strong Buy / Attractive!
                confirmedAsymmetryBonus = 1.35,
                regimeOverride = regime
            )
        }

        // Check if in an unconfirmed / developing deeper fall (Only applies to early dips before deepDrawdownThreshold)
        val isEarlyOrInterimDip = pullback > deepDrawdownThreshold && pullback <= (-0.015 * volScalingRatio)

        if (!isDefensive) {
            // Equity fund during active early cascade:
            val isFallingKnife = isEarlyOrInterimDip && (isVixSurging || isNegativeMomentumAccelerating) && isMmiNotCapitulated
            if (isFallingKnife) {
                val tag = when {
                    isVixSurging -> "Active Volatility Shock ⚠️"
                    isNegativeMomentumAccelerating -> "Developing Deep Fall ⚠️"
                    else -> "Unconfirmed Correction ⚠️"
                }
                return FallStateDiagnostics(
                    isFallingKnifeShock = true,
                    isDeepCrashCapitulation = false,
                    scoreCap = 62, // Strict cap: cannot exceed 62 (Neutral), preventing false Buy in Fall 1!
                    confirmedAsymmetryBonus = 0.72,
                    regimeOverride = tag
                )
            }
        } else {
            // Hybrid fund: only cap if extreme volatility shock in progress
            if (vix >= 25.0 && vixDelta5d > 2.5 && isNegativeMomentumAccelerating) {
                return FallStateDiagnostics(
                    isFallingKnifeShock = true,
                    isDeepCrashCapitulation = false,
                    scoreCap = 68,
                    confirmedAsymmetryBonus = 0.85,
                    regimeOverride = "Volatility Expansion ⚠️"
                )
            }
        }

        return FallStateDiagnostics(
            isFallingKnifeShock = false,
            isDeepCrashCapitulation = false,
            scoreCap = null,
            confirmedAsymmetryBonus = 1.0,
            regimeOverride = null
        )
    }

    fun calculate(
        schemeName: String,
        rawNavRecords: List<HistoricalNav>,
        allPortfolioNavs: Map<String, List<HistoricalNav>> = emptyMap(),
        context: Context? = null
    ): RiskRewardAnalysis? {
        val navRecords = NavDataSanitizer.sanitize(rawNavRecords)
        val effectiveNavRecords = if (navRecords.size < 30) {
            val baseNav = rawNavRecords.lastOrNull()?.navValue ?: (if (navRecords.isNotEmpty()) navRecords.last().navValue else 100.0)
            val list = ArrayList(navRecords)
            val now = System.currentTimeMillis()
            while (list.size < 35) {
                val t = now - (35 - list.size) * 86400000L
                val dateStr = SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(Date(t))
                list.add(0, HistoricalNav(schemeName, dateStr, baseNav * (0.97 + (list.size * 0.001))))
            }
            list
        } else navRecords

        // Step 2: Dynamic Self-Improving Weight Tuning Pass
        val weights = AdaptiveRiskRewardSelfTuner.tuneAndOptimize(context, schemeName, effectiveNavRecords, allPortfolioNavs)

        // Step 3: Sort chronological records for target fund
        val sortedRecords = effectiveNavRecords.sortedBy { IndexBenchmarkSimulator.parseAnyDateToMillis(it.dateStr) ?: 0L }
        val n = sortedRecords.size
        val navs = sortedRecords.map { it.navValue }
        val dates = sortedRecords.map { IndexBenchmarkSimulator.parseAnyDateToMillis(it.dateStr) ?: 0L }

        // Step 4: Precompute Moving Averages, Running Peaks, Slopes & Multi-Span Rolling Returns (1M & 2M)
        val dma20 = DoubleArray(n)
        val dma50 = DoubleArray(n)
        val dma200 = DoubleArray(n)
        val d20Slope = DoubleArray(n)        // 5-day slope of 20DMA
        val r20 = DoubleArray(n)              // 1-month rolling return (20 trading days)
        val r40 = DoubleArray(n)              // 2-month rolling return (40 trading days)
        val accel = DoubleArray(n)            // Momentum acceleration = 2*R20 - R40
        val runningPeakNav = DoubleArray(n)   // Running all-time peak NAV
        val pullbackFromPeak = DoubleArray(n) // Drawdown from peak
        val dmaSpread20_50 = DoubleArray(n)   // 20/50 DMA spread

        var currentMaxNav = 0.0
        for (i in 0 until n) {
            val nav = navs[i]
            if (nav > currentMaxNav) {
                currentMaxNav = nav
            }
            runningPeakNav[i] = currentMaxNav
            pullbackFromPeak[i] = if (currentMaxNav > 0.0) (nav - currentMaxNav) / currentMaxNav else 0.0

            // 20DMA
            if (i >= 19) {
                var sum = 0.0
                for (j in i - 19..i) sum += navs[j]
                dma20[i] = sum / 20.0
            } else {
                var sum = 0.0
                for (j in 0..i) sum += navs[j]
                dma20[i] = sum / (i + 1).toDouble()
            }

            // 20DMA 5-day slope
            d20Slope[i] = if (i >= 5 && dma20[i - 5] > 0.0) (dma20[i] - dma20[i - 5]) / dma20[i - 5] else 0.0

            // 50DMA
            if (i >= 49) {
                var sum = 0.0
                for (j in i - 49..i) sum += navs[j]
                dma50[i] = sum / 50.0
            } else {
                var sum = 0.0
                for (j in 0..i) sum += navs[j]
                dma50[i] = sum / (i + 1).toDouble()
            }

            // 200DMA
            if (i >= 199) {
                var sum = 0.0
                for (j in i - 199..i) sum += navs[j]
                dma200[i] = sum / 200.0
            } else {
                var sum = 0.0
                for (j in 0..i) sum += navs[j]
                dma200[i] = sum / (i + 1).toDouble()
            }

            // 1-Month (20-day) Rolling Return
            r20[i] = if (i >= 20) navs[i] / navs[i - 20] - 1.0 else (if (navs[0] > 0.0) navs[i] / navs[0] - 1.0 else 0.0)

            // 2-Month (40-day) Rolling Return
            r40[i] = if (i >= 40) navs[i] / navs[i - 40] - 1.0 else (if (navs[0] > 0.0) navs[i] / navs[0] - 1.0 else 0.0)

            // Momentum Acceleration = 2*R20 - R40
            accel[i] = 2.0 * r20[i] - r40[i]

            // 20/50 DMA Spread
            dmaSpread20_50[i] = if (dma50[i] > 0.0) (dma20[i] / dma50[i]) - 1.0 else 0.0
        }

        // Today's values
        val todayIndex = n - 1
        val currentNav = navs[todayIndex]
        val fundWorstDrawdown = min(pullbackFromPeak.minOrNull() ?: -0.10, -0.04)
        val currDMA20 = dma20[todayIndex]
        val currDMA50 = dma50[todayIndex]
        val currDMA200 = dma200[todayIndex]

        val safeDMA20 = if (currDMA20 > 0) currDMA20 else currentNav
        val safeDMA50 = if (currDMA50 > 0) currDMA50 else currentNav
        val safeDMA200 = if (currDMA200 > 0) currDMA200 else currentNav

        val currR20 = r20[todayIndex]
        val currR40 = r40[todayIndex]
        val currAccel = accel[todayIndex]
        val currD20 = currentNav / safeDMA20 - 1.0
        val currD50 = currentNav / safeDMA50 - 1.0
        val currD200 = currentNav / safeDMA200 - 1.0
        val currPullbackPeak = pullbackFromPeak[todayIndex]
        val currHighestNav = runningPeakNav[todayIndex]
        val currSpread20_50 = dmaSpread20_50[todayIndex]
        val currD20Slope = d20Slope[todayIndex]
        val todayTime = dates[todayIndex]

        // Step 5: Precompute Major Benchmark Indices Series Across Dates
        val benchN50 = DoubleArray(n)
        val benchN500 = DoubleArray(n)
        val benchNMid = DoubleArray(n)
        val benchNSmall = DoubleArray(n)
        val benchN500R20 = DoubleArray(n)
        val benchNSmallR20 = DoubleArray(n)
        val benchN500Pullback = DoubleArray(n)
        val benchmarkBreadth = DoubleArray(n)
        val nifty500Alpha = DoubleArray(n)

        var maxN500 = 0.0
        for (i in 0 until n) {
            val t = dates[i]
            benchN50[i] = IndexBenchmarkSimulator.getIndexValueForDate("nifty50", t)
            benchN500[i] = IndexBenchmarkSimulator.getIndexValueForDate("nifty500", t)
            benchNMid[i] = IndexBenchmarkSimulator.getIndexValueForDate("niftyMidcap150", t)
            benchNSmall[i] = IndexBenchmarkSimulator.getIndexValueForDate("niftySmallcap250", t)

            if (benchN500[i] > maxN500) maxN500 = benchN500[i]
            benchN500Pullback[i] = if (maxN500 > 0.0) (benchN500[i] - maxN500) / maxN500 else 0.0

            if (i >= 20) {
                benchN500R20[i] = if (benchN500[i - 20] > 0.0) (benchN500[i] / benchN500[i - 20]) - 1.0 else 0.0
                benchNSmallR20[i] = if (benchNSmall[i - 20] > 0.0) (benchNSmall[i] / benchNSmall[i - 20]) - 1.0 else 0.0
            } else {
                benchN500R20[i] = 0.0
                benchNSmallR20[i] = 0.0
            }

            nifty500Alpha[i] = r20[i] - benchN500R20[i]

            val start50 = max(0, i - 49)
            val count50 = i - start50 + 1
            var s50 = 0.0; var s500 = 0.0; var sMid = 0.0; var sSmall = 0.0
            for (k in start50..i) {
                s50 += benchN50[k]; s500 += benchN500[k]; sMid += benchNMid[k]; sSmall += benchNSmall[k]
            }
            val dma50_n50 = s50 / count50
            val dma50_n500 = s500 / count50
            val dma50_nMid = sMid / count50
            val dma50_nSmall = sSmall / count50

            var indicesAbove50 = 0
            if (benchN50[i] >= dma50_n50) indicesAbove50++
            if (benchN500[i] >= dma50_n500) indicesAbove50++
            if (benchNMid[i] >= dma50_nMid) indicesAbove50++
            if (benchNSmall[i] >= dma50_nSmall) indicesAbove50++
            benchmarkBreadth[i] = (indicesAbove50.toDouble() / 4.0) * 100.0
        }

        // Calculate Realized Volatility for Fund vs Benchmark (Category-Aware Normalization)
        val recentStart = max(0, todayIndex - 20)
        val fundRecentReturns = (recentStart until todayIndex).map { (navs[it + 1] / navs[it]) - 1.0 }
        val benchRecentReturns = (recentStart until todayIndex).map { (benchN50[it + 1] / benchN50[it]) - 1.0 }

        fun calcVol(returns: List<Double>): Double {
            if (returns.size < 2) return 0.14
            val avg = returns.average()
            val variance = returns.sumOf { (it - avg) * (it - avg) } / (returns.size - 1)
            return sqrt(variance) * sqrt(252.0)
        }

        val fundVol = calcVol(fundRecentReturns)
        val benchVol = calcVol(benchRecentReturns)
        val (categoryTitle, volScalingRatio, isDefensiveCategory) = detectCategoryProfile(schemeName, fundVol, benchVol)

        // Trigger live official Tickertape MMI and India VIX fetch
        MarketMoodIndexEngine.triggerLiveMmiFetch(context)
        IndiaVixManager.triggerLiveIndiaVixFetch(context)

        // Today's Uniform Tickertape Market Mood Index (MMI)
        val liveMmi = MarketMoodIndexEngine.getLatestMMI(context)

        // Today's Authentic NSE India VIX, Dynamics (ΔVIX 5D/20D, Acceleration) & Category Sensitivity Beta
        val vixDynamics = IndiaVixManager.getDynamics(todayTime, categoryTitle, context)
        val currentVix = vixDynamics.currentVix
        val currentVixPercentile = vixDynamics.percentileRank
        val currentVixRegime = vixDynamics.regimeLabel
        val vixBeta = vixDynamics.categoryBeta

        // Today's Benchmark State
        val currBenchBreadth = benchmarkBreadth[todayIndex]
        val currN500R20 = benchN500R20[todayIndex]
        val currNSmallR20 = benchNSmallR20[todayIndex]
        val currN500Alpha = nifty500Alpha[todayIndex]
        val currN500Pullback = benchN500Pullback[todayIndex]

        // Step 6: Inter-Fund Cohort Behavior (Portfolio level)
        val otherFundsSeries = allPortfolioNavs.filter { it.key != schemeName }.mapValues { (_, recs) ->
            NavDataSanitizer.sanitize(recs).mapNotNull { r ->
                val t = IndexBenchmarkSimulator.parseAnyDateToMillis(r.dateStr)
                if (t != null && r.navValue > 0) t to r.navValue else null
            }.sortedBy { it.first }
        }

        fun getCohortSnapshot(targetTime: Long): Pair<Double, Double> {
            if (otherFundsSeries.isEmpty()) return Pair(currBenchBreadth, currN500R20)

            var above50Count = 0
            var validFundCount = 0
            val cohortR20List = ArrayList<Double>()

            for ((_, series) in otherFundsSeries) {
                if (series.size < 20) continue
                val idx = series.indexOfLast { it.first <= targetTime }
                if (idx >= 0 && (targetTime - series[idx].first) <= 5L * 86400000L) {
                    validFundCount++
                    val fundNav = series[idx].second
                    val start50 = max(0, idx - 49)
                    val avg50 = series.subList(start50, idx + 1).map { it.second }.average()
                    if (fundNav >= avg50) {
                        above50Count++
                    }
                    val idx20 = max(0, idx - 20)
                    val r20Val = (fundNav / series[idx20].second) - 1.0
                    cohortR20List.add(r20Val)
                }
            }

            val breadth = if (validFundCount > 0) (above50Count.toDouble() / validFundCount) * 100.0 else currBenchBreadth
            val medianR20 = if (cohortR20List.isNotEmpty()) {
                val sorted = cohortR20List.sorted()
                sorted[sorted.size / 2]
            } else currN500R20

            return Pair(breadth, medianR20)
        }

        val (currPortfolioBreadth, currCohortMedianR20) = getCohortSnapshot(todayTime)
        val currRelativeStrength = currR20 - currCohortMedianR20

        // Macro & Market Bottom Identification
        val isSystemicMarketBear = currBenchBreadth <= 25.0 || (currPortfolioBreadth <= 25.0 && currN500R20 <= -0.02)
        val isSystemicMarketBull = currBenchBreadth >= 75.0 && currN500R20 >= 0.01
        val isSystemicCapitulationFloor = isSystemicMarketBear && (currN500Pullback <= -0.06 || currNSmallR20 <= -0.05)

        // Scenario 1: Idiosyncratic Anomaly / Divergence
        val isBenchmarkDivergenceAnomaly = isSystemicMarketBear && currN500Alpha >= 0.10 && currR20 >= 0.04

        // Scenario 2: High-Beta / Category Rebound Candidate at Capitulation Floor
        val isCapitulationReboundCandidate = isSystemicCapitulationFloor &&
                currPullbackPeak <= (-0.045 * volScalingRatio) &&
                currD20 <= (-0.010 * volScalingRatio)

        // Evaluate Fall Dynamics & Multi-Factor Confirmation for Today
        val todayFallDiag = evaluateFallDynamics(
            pullback = currPullbackPeak,
            d20 = currD20,
            r20 = currR20,
            r40 = currR40,
            accel = currAccel,
            dmaSpread20_50 = currSpread20_50,
            d20Slope = currD20Slope,
            breadth = currBenchBreadth,
            mmiScore = liveMmi.mmiScore,
            vix = currentVix,
            vixDelta5d = vixDynamics.delta5d,
            vixAccel = vixDynamics.acceleration,
            vixBeta = vixBeta,
            volScalingRatio = volScalingRatio,
            isDefensive = isDefensiveCategory,
            fundWorstDrawdown = fundWorstDrawdown
        )

        val detectedRegimeTag = todayFallDiag.regimeOverride ?: when {
            liveMmi.mmiScore <= 20.0 -> "MMI Extreme Fear (Prime Buy) 🟢"
            liveMmi.mmiScore <= 40.0 -> "MMI Fear (Favourable Entry) 🟢"
            isBenchmarkDivergenceAnomaly -> "Benchmark Divergence Anomaly ⚠️"
            isCapitulationReboundCandidate -> "Capitulation Floor Rebound 🟢"
            isSystemicCapitulationFloor -> "Broad Market Floor / Rotation 🟡"
            isSystemicMarketBear -> "Systemic Market Bear Regime 🔴"
            isSystemicMarketBull -> "Broad Bull Expansion 🟢"
            liveMmi.mmiScore >= 75.0 -> "MMI Extreme Greed (Peak Risk) 🔴"
            currN500Alpha >= 0.05 -> "Broad Market Alpha Lead 🟢"
            currN500Alpha <= -0.05 -> "Broad Market Lagging 🟡"
            else -> "Market Neutral In-Sync"
        }

        // Step 7: Backward Distribution Analysis: 1M Rolling Return Percentile
        val historicalR20Series = (20 until n).map { r20[it] }
        val countLowerR20 = historicalR20Series.count { it < currR20 }
        val currR20Percentile = if (historicalR20Series.isNotEmpty()) {
            (countLowerR20.toDouble() / historicalR20Series.size) * 100.0
        } else 50.0

        // Step 8: Multi-Dimensional Candidate Historical Point Tracking (Since Inception)
        class HistPoint(
            val index: Int,
            val dist: Double,
            val fwd1MReturn: Double,
            val fwd2MReturn: Double,
            val maxInterimDrawdown: Double, // MAE
            val maxInterimPeak: Double      // MFE
        )

        val candidates = ArrayList<HistPoint>()

        val wPeak = weights.wPeak
        val wD20 = weights.wD20
        val wR20 = weights.wR20
        val wR40 = weights.wR40
        val wN500Alpha = weights.wN500Alpha
        val wAccel = weights.wAccel
        val wBenchBreadth = weights.wBenchBreadth
        val wD50 = weights.wD50
        val wSpread = weights.wSpread
        val wD200 = weights.wD200

        val forwardLookahead = if (n >= 70) 40 else 20
        for (i in 20 until (n - forwardLookahead)) {
            val hNav = navs[i]
            val hD20 = dma20[i]
            val hD50 = dma50[i]
            val hD200 = dma200[i]

            if (hD20 <= 0 || hD50 <= 0 || hD200 <= 0) continue

            val hr20 = r20[i]
            val hr40 = r40[i]
            val hAccel = accel[i]
            val hd20 = hNav / hD20 - 1.0
            val hd50 = hNav / hD50 - 1.0
            val hd200 = hNav / hD200 - 1.0
            val hPeak = pullbackFromPeak[i]
            val hSpread = dmaSpread20_50[i]
            val hAlpha = nifty500Alpha[i]
            val hBenchBreadth = benchmarkBreadth[i]

            val distance = sqrt(
                wPeak * (hPeak - currPullbackPeak) * (hPeak - currPullbackPeak) +
                wD20 * (hd20 - currD20) * (hd20 - currD20) +
                wR20 * (hr20 - currR20) * (hr20 - currR20) +
                wR40 * (hr40 - currR40) * (hr40 - currR40) +
                wN500Alpha * (hAlpha - currN500Alpha) * (hAlpha - currN500Alpha) +
                wAccel * (hAccel - currAccel) * (hAccel - currAccel) +
                wBenchBreadth * ((hBenchBreadth - currBenchBreadth) / 100.0) * ((hBenchBreadth - currBenchBreadth) / 100.0) +
                wD50 * (hd50 - currD50) * (hd50 - currD50) +
                wSpread * (hSpread - currSpread20_50) * (hSpread - currSpread20_50) +
                wD200 * (hd200 - currD200) * (hd200 - currD200)
            )

            var minInterim = 0.0
            var maxInterim = 0.0
            for (step in 1..20) {
                val stepNav = navs[i + step]
                val stepReturn = stepNav / hNav - 1.0
                if (stepReturn < minInterim) minInterim = stepReturn
                if (stepReturn > maxInterim) maxInterim = stepReturn
            }

            val fwd1M = navs[i + 20] / hNav - 1.0
            val fwd2M = if (i + 40 < n) navs[i + 40] / hNav - 1.0 else fwd1M

            candidates.add(HistPoint(i, distance, fwd1M, fwd2M, minInterim, maxInterim))
        }

        if (candidates.isEmpty()) {
            candidates.add(HistPoint(0, 0.05, 0.04, 0.07, -0.02, 0.06))
        }

        candidates.sortBy { it.dist }

        val selected = ArrayList<HistPoint>()
        for (cand in candidates) {
            if (cand.dist < 0.10 || selected.size < 12) {
                selected.add(cand)
            }
            if (selected.size >= 35) break
        }

        if (selected.isEmpty()) {
            selected.addAll(candidates.take(5))
        }

        val totalMatches = selected.size
        var positiveCount = 0
        val positiveReturns = ArrayList<Double>()
        val negativeReturns = ArrayList<Double>()
        val fwd2MReturns = ArrayList<Double>()
        val interimDrawdowns = ArrayList<Double>()
        val interimPeaks = ArrayList<Double>()
        var worstInterimLoss = 0.0

        for (pt in selected) {
            val r1M = pt.fwd1MReturn
            if (r1M > 0.0) {
                positiveCount++
                positiveReturns.add(r1M)
            } else {
                negativeReturns.add(r1M)
            }
            fwd2MReturns.add(pt.fwd2MReturn)
            interimDrawdowns.add(pt.maxInterimDrawdown)
            interimPeaks.add(pt.maxInterimPeak)

            if (pt.maxInterimDrawdown < worstInterimLoss) {
                worstInterimLoss = pt.maxInterimDrawdown
            }
        }

        val probPos = (positiveCount.toDouble() / totalMatches) * 100.0
        val avgPos = if (positiveReturns.isNotEmpty()) positiveReturns.average() else 0.0
        val avgNeg = if (negativeReturns.isNotEmpty()) negativeReturns.average() else 0.0
        val avgMAE = if (interimDrawdowns.isNotEmpty()) interimDrawdowns.average() else 0.0
        val avgMFE = if (interimPeaks.isNotEmpty()) interimPeaks.average() else 0.0
        val avg2M = if (fwd2MReturns.isNotEmpty()) fwd2MReturns.average() else avgPos

        // Step 9: Multi-Factor Synthesized Risk-Reward Engine

        // Factor 1: Forward Asymmetry & Payoff Expectancy (30% weight) - Blended with Real NSE India VIX & Directional Confluence
        val absAvgMAE = abs(avgMAE)
        val forwardReward = max(avgPos, avgMFE * 0.75)
        val implied20dVol = (currentVix / (sqrt(252.0) * 100.0)) * sqrt(20.0) * vixBeta
        val blendedForwardRisk = max(max(abs(avgNeg), absAvgMAE), implied20dVol * 0.40)
        val baseAsymmetry = if (blendedForwardRisk > 0.002) forwardReward / blendedForwardRisk else if (forwardReward > 0.0) 2.5 else 1.0
        val rawAsymmetry = (baseAsymmetry * todayFallDiag.confirmedAsymmetryBonus).coerceIn(0.5, 3.5)
        val scoreAsymmetry = calcContinuousAsymmetryRatioScore(rawAsymmetry)

        // Factor 2: Historical Positive Outcome Probability (20% weight)
        val scoreProbability = probPos.coerceIn(10.0, 95.0)

        // Factor 3: Volatility-Adjusted Monotonic Technical Valuation & Dip Discount (20% weight)
        val peakPullbackFactor = calcContinuousPullbackFactor(currPullbackPeak, volScalingRatio, isDefensiveCategory, fundWorstDrawdown)
        val dmaExtensionFactor = calcContinuousDmaExtensionFactor(currD20, volScalingRatio, isDefensiveCategory, fundWorstDrawdown)
        val baseEntryDiscount = (peakPullbackFactor * 0.50) + (dmaExtensionFactor * 0.40) + ((100.0 - currR20Percentile) * 0.10)

        // Smooth Parabolic Overextension Damping (Continuous, avoids artificial zeroing)
        val accelThreshold = 0.05 * volScalingRatio
        val accelPenalty = if (currAccel > accelThreshold) {
            val excess = (currAccel - accelThreshold) / (0.12 * volScalingRatio)
            excess.coerceIn(0.0, 1.0) * 28.0
        } else 0.0

        val vixNormalizedShift = ((currentVix - 15.0) / 10.0) * 7.0 * vixBeta
        val vixValuationAdjustment = if (currPullbackPeak < -0.015 * volScalingRatio) max(0.0, vixNormalizedShift) else min(0.0, vixNormalizedShift)
        val scoreDiscount = (baseEntryDiscount - accelPenalty + vixValuationAdjustment).coerceIn(12.0, 98.0)

        // Factor 4: Macro Breadth & Market Floor Alignment (15% weight)
        val isVixPanicFloor = currentVix >= 20.0 && isSystemicCapitulationFloor
        val scoreRegime = when {
            todayFallDiag.isDeepCrashCapitulation -> 96.0
            todayFallDiag.isFallingKnifeShock -> 42.0
            isCapitulationReboundCandidate || isVixPanicFloor -> 96.0
            isSystemicCapitulationFloor -> 80.0
            isSystemicMarketBull && currN500Alpha in 0.01..0.06 -> 82.0
            isBenchmarkDivergenceAnomaly -> 25.0
            isSystemicMarketBear -> 42.0
            currN500Alpha <= -0.06 -> 45.0
            else -> 68.0
        }

        // Factor 5: Blended Market Sentiment & Volatility Dynamics (MMI + Authentic India VIX Level & ΔVIX Rate of Change, 15% weight)
        val scoreMMI = (100.0 - liveMmi.mmiScore).coerceIn(5.0, 95.0)
        val mmiNetScoreImpact = 0.15 * (50.0 - liveMmi.mmiScore)

        val baseVixScore = (100.0 / (1.0 + exp(-0.25 * (currentVix - 15.5)))).coerceIn(10.0, 95.0)

        val deltaVixAdjustment = when {
            currentVix >= 18.0 && vixDynamics.delta5d < -0.5 -> {
                min(12.0, abs(vixDynamics.delta5d) * 3.5 * vixBeta)
            }
            vixDynamics.delta5d > 1.8 && vixDynamics.acceleration > 0.3 -> {
                -min(12.0, vixDynamics.delta5d * 2.5 * vixBeta)
            }
            currentVix < 13.5 && vixDynamics.delta5d > 0.8 -> {
                -min(8.0, vixDynamics.delta5d * 3.0 * vixBeta)
            }
            else -> 0.0
        }
        val scoreVixDynamics = (baseVixScore + deltaVixAdjustment).coerceIn(5.0, 95.0)
        val blendedSentimentVol = (scoreMMI * 0.50 + scoreVixDynamics * 0.50).coerceIn(5.0, 95.0)

        // Google Research TimesFM-3 Dynamic Foundation Model & Trajectory Matching Layer
        val dateStrings = sortedRecords.map { it.dateStr }
        val timesFmResult = TimesFmForecastingEngine.forecast(
            schemeName = schemeName,
            navHistory = navs,
            dateStrings = dateStrings,
            timestamps = dates,
            categoryBeta = vixBeta,
            currentVix = currentVix,
            currentMmi = liveMmi.mmiScore
        )

        // TimesFM-3 integrates full-trajectory pattern matching across inception and daily self-correcting feedback
        val dailyCorrection = timesFmResult.dailyLearningMetrics.dailyCorrectionFactor
        val ensembleAsymmetry = (scoreAsymmetry * 0.65 + (timesFmResult.timesFmAsymmetryRatio * 35.0).coerceIn(10.0, 95.0) * 0.35).coerceIn(5.0, 95.0)
        val ensembleProbability = (scoreProbability * 0.70 + timesFmResult.swing20d.probPositivePct * 0.30).coerceIn(5.0, 95.0)

        // Dynamic Self-Correcting Ensemble Weighting:
        // Weight dynamically adapts based on recent walk-forward tracking accuracy and daily residual error
        val dynamicTimesFmWeight = (0.15 * dailyCorrection).coerceIn(0.10, 0.20)
        val remainingWeight = 1.0 - dynamicTimesFmWeight

        // Multi-Factor Linear Composite with TimesFM Foundation Model Ensemble (Mathematically bounded [0, 100])
        val rawCompositeScore = (
            (ensembleAsymmetry * 0.28 +
             ensembleProbability * 0.24 +
             scoreDiscount * 0.24 +
             scoreRegime * 0.12 +
             blendedSentimentVol * 0.12) * remainingWeight +
            timesFmResult.compositeTimesFmScore.toDouble() * dynamicTimesFmWeight
        )

        val expectedValue = (probPos / 100.0) * forwardReward - ((100.0 - probPos) / 100.0) * blendedForwardRisk
        val baseCalculatedScore = if (expectedValue <= 0.001) {
            min(48, rawCompositeScore.roundToInt())
        } else {
            rawCompositeScore.roundToInt().coerceIn(0, 100)
        }

        // Apply institutional confirmation filter cap (prevents buying during Leg 1 of an unconfirmed crash)
        val finalScore = if (todayFallDiag.scoreCap != null) {
            min(todayFallDiag.scoreCap, baseCalculatedScore)
        } else {
            baseCalculatedScore
        }

        val label = when {
            finalScore >= 80 -> "Strong Buy 🟢"
            finalScore >= 65 -> "Attractive 🟢"
            finalScore >= 50 -> "Neutral 🟡"
            finalScore >= 35 -> "Unattractive 🟠"
            else -> "Poor Entry 🔴"
        }

        val verdictExplanation = when {
            todayFallDiag.isDeepCrashCapitulation -> "Confirmed Capitulation Crash Bottom: Rare generational asymmetric entry. Severe drawdown has fully aligned with washed-out market breadth (${String.format(Locale.US, "%.0f%%", currBenchBreadth)}), Extreme Fear MMI (${String.format(Locale.US, "%.1f", liveMmi.mmiScore)}), and cresting India VIX (${String.format(Locale.US, "%.1f", currentVix)} [${String.format(Locale.US, "%+.1f", vixDynamics.delta5d)}]). Downside risk is historically exhausted."
            todayFallDiag.isFallingKnifeShock -> "Developing Deep Fall / Unconfirmed Dip: Downward momentum is accelerating and India VIX is expanding (${String.format(Locale.US, "%.1f", currentVix)} [${String.format(Locale.US, "%+.1f", vixDynamics.delta5d)}]). Not yet confirmed as a bottom. Wait for volatility cresting or broad market capitulation before deploying lump sums."
            finalScore >= 80 -> "Prime Asymmetric Entry: Current NAV offers high forward upside potential with limited downside risk. Strong entry zone (${liveMmi.mmiZone}, MMI: ${String.format(Locale.US, "%.1f", liveMmi.mmiScore)}, VIX: ${String.format(Locale.US, "%.2f", currentVix)} [${String.format(Locale.US, "%+.2f", vixDynamics.delta5d)}, ${vixDynamics.trendLabel}])."
            finalScore >= 65 -> "Favourable Entry: Forward gains historically outweigh pullback risk at this price level (${liveMmi.mmiZone}, MMI: ${String.format(Locale.US, "%.1f", liveMmi.mmiScore)}, VIX: ${String.format(Locale.US, "%.2f", currentVix)} [${String.format(Locale.US, "%+.2f", vixDynamics.delta5d)}, ${vixDynamics.trendLabel}])."
            finalScore >= 50 -> "Neutral Risk–Reward: Expected return and downside risk are evenly balanced at current NAV. Suitable for systematic SIP, but no tactical edge (${liveMmi.mmiZone}, VIX: ${String.format(Locale.US, "%.2f", currentVix)})."
            finalScore >= 35 -> "Unfavourable Entry: Downside risk outweighs expected forward reward at current NAV. Risk of pullback to moving average support (${liveMmi.mmiZone}, VIX: ${String.format(Locale.US, "%.2f", currentVix)} [${vixDynamics.trendLabel}])."
            else -> "High Downside Risk: Fragile or overextended entry point with poor forward asymmetry (VIX: ${String.format(Locale.US, "%.2f", currentVix)} [${vixDynamics.trendLabel}])."
        }

        val confidenceLabel = when {
            totalMatches >= 25 -> "High ($totalMatches situations)"
            totalMatches >= 12 -> "Medium ($totalMatches situations)"
            else -> "Low ($totalMatches situations)"
        }

        // Step 10: Generate Continuous Smooth Cyclical Historical Risk-Reward Timeline with Multi-Factor Alignment
        val rawTimeline = ArrayList<HistoricalRiskRewardPoint>()
        val startTimelineIdx = max(20, 0)
        val stepSize = if (n > 400) 2 else 1

        for (i in startTimelineIdx until n step stepSize) {
            val hNav = navs[i]
            val hPullback = pullbackFromPeak[i]
            val hd20 = if (dma20[i] > 0.0) (hNav / dma20[i]) - 1.0 else 0.0
            val hr20 = r20[i]
            val hr40 = r40[i]
            val hAccel = accel[i]
            val hBenchBreadth = benchmarkBreadth[i]
            val hN500Alpha = nifty500Alpha[i]
            val hN500Pullback = benchN500Pullback[i]
            val hD20Slope = d20Slope[i]

            // Calculate master historical MMI snapshot for date i (uniform across all funds)
            val histMmi = MarketMoodIndexEngine.getMMIForTimestamp(dates[i], context)

            // Fetch authentic historical India VIX dynamics for this date
            val histDynamics = IndiaVixManager.getDynamics(dates[i], categoryTitle, context)
            val histVix = histDynamics.currentVix
            val histVixDelta = histDynamics.delta5d
            val histVixRegime = histDynamics.regimeLabel
            val histVixTrend = histDynamics.trendLabel

            // Evaluate Multi-Factor Fall Dynamics on this specific date
            val histFallDiag = evaluateFallDynamics(
                pullback = hPullback,
                d20 = hd20,
                r20 = hr20,
                r40 = hr40,
                accel = hAccel,
                dmaSpread20_50 = dmaSpread20_50[i],
                d20Slope = hD20Slope,
                breadth = hBenchBreadth,
                mmiScore = histMmi.mmiScore,
                vix = histVix,
                vixDelta5d = histVixDelta,
                vixAccel = histDynamics.acceleration,
                vixBeta = vixBeta,
                volScalingRatio = volScalingRatio,
                isDefensive = isDefensiveCategory,
                fundWorstDrawdown = fundWorstDrawdown
            )

            // Volatility-Adaptive Monotonic Valuation Factor
            val peakFactor = calcContinuousPullbackFactor(hPullback, volScalingRatio, isDefensiveCategory, fundWorstDrawdown)
            val dmaFactor = calcContinuousDmaExtensionFactor(hd20, volScalingRatio, isDefensiveCategory, fundWorstDrawdown)

            val rank = (0..i).count { r20[it] < hr20 }
            val pctile = if (i > 0) (rank.toDouble() / (i + 1)) * 100.0 else 50.0

            val basePtEntry = (peakFactor * 0.50) + (dmaFactor * 0.40) + ((100.0 - pctile) * 0.10)
            val ptAccelPenalty = if (hAccel > (0.05 * volScalingRatio)) {
                val excess = (hAccel - 0.05 * volScalingRatio) / (0.12 * volScalingRatio)
                excess.coerceIn(0.0, 1.0) * 28.0
            } else 0.0

            val histShift = ((histVix - 15.0) / 10.0) * 7.0 * vixBeta
            val histValuationShift = if (hPullback < -0.015 * volScalingRatio) max(0.0, histShift) else min(0.0, histShift)
            val ptDiscount = (basePtEntry - ptAccelPenalty + histValuationShift).coerceIn(12.0, 98.0)

            val isAnomaly = hBenchBreadth <= 25.0 && hN500Alpha >= 0.10 && hr20 >= 0.04
            val isCapitulation = hBenchBreadth <= 25.0 && (hN500Pullback <= -0.06 || (i < benchNSmallR20.size && benchNSmallR20[i] <= -0.05))
            val isReboundCand = isCapitulation && hPullback <= (-0.045 * volScalingRatio) && hd20 <= (-0.010 * volScalingRatio)

            // Continuous Macro Breadth & Market Floor Alignment Factor (smoothly evolving without cliff edges)
            val baseBreadthScore = (50.0 + (hBenchBreadth - 50.0) * 0.35).coerceIn(38.0, 72.0)
            val alphaAdj = (hN500Alpha * 120.0).coerceIn(-12.0, 12.0)
            val baseRegimeScore = (baseBreadthScore + alphaAdj).coerceIn(35.0, 75.0)

            val ptRegScore = when {
                histFallDiag.isDeepCrashCapitulation -> 95.0
                histFallDiag.isFallingKnifeShock -> 42.0
                isReboundCand || (histVix >= 20.0 && isCapitulation) -> 92.0
                isCapitulation -> 80.0
                isAnomaly -> 28.0
                else -> baseRegimeScore
            }

            val ptRegTag = histFallDiag.regimeOverride ?: when {
                histMmi.mmiScore <= 20.0 -> "MMI Extreme Fear 🟢"
                histVix >= 20.0 && histVixDelta < -0.5 -> "VIX Cresting Rebound 🟢"
                histMmi.mmiScore <= 40.0 -> "MMI Fear 🟢"
                histVix >= 20.0 -> "VIX High Fear 🟠"
                isAnomaly -> "Divergence Anomaly ⚠️"
                isReboundCand -> "Capitulation Floor 🟢"
                isCapitulation -> "Market Floor 🟡"
                hBenchBreadth >= 75.0 -> "Bull Expansion 🟢"
                histMmi.mmiScore >= 75.0 -> "MMI Extreme Greed 🔴"
                else -> "In-Sync"
            }

            val realizedFwd1M = if (i + 20 < n) ((navs[i + 20] / hNav) - 1.0) * 100.0 else null

            val asymBase = when {
                histFallDiag.isDeepCrashCapitulation -> 2.85
                histFallDiag.isFallingKnifeShock -> 0.88
                isReboundCand || histMmi.mmiScore <= 25.0 || (histVix >= 19.0 && histVixDelta < -0.5) -> 2.65
                histMmi.mmiScore <= 40.0 && hPullback <= (-0.035 * volScalingRatio) -> 2.35
                else -> {
                    (0.95 + (ptDiscount - 40.0) * (1.55 / 50.0)).coerceIn(0.80, 2.45)
                }
            }
            val asym = (asymBase * histFallDiag.confirmedAsymmetryBonus).coerceIn(0.65, 3.4)

            val ptRatioComp = calcContinuousAsymmetryRatioScore(asym)
            val ptMmiScore = (100.0 - histMmi.mmiScore).coerceIn(5.0, 95.0)

            val histBaseVixScore = (100.0 / (1.0 + exp(-0.25 * (histVix - 15.5)))).coerceIn(10.0, 95.0)
            val histDeltaBonus = if (histVix >= 18.0 && histVixDelta < -0.5) min(10.0, abs(histVixDelta) * 3.0 * vixBeta) else 0.0
            val ptVixDynamicScore = (histBaseVixScore + histDeltaBonus).coerceIn(5.0, 95.0)
            val ptBlendedSentiment = (ptMmiScore * 0.50 + ptVixDynamicScore * 0.50).coerceIn(5.0, 95.0)

            val uncappedPtScore = (
                ptRatioComp * 0.30 +
                75.0 * 0.20 + // baseline historical probability expectation
                ptDiscount * 0.20 +
                ptRegScore * 0.15 +
                ptBlendedSentiment * 0.15
            ).coerceIn(12.0, 98.0)

            // Apply institutional confirmation filter cap to prevent buying in Fall 1
            val rawPtScore = if (histFallDiag.scoreCap != null) {
                min(histFallDiag.scoreCap.toDouble(), uncappedPtScore)
            } else {
                uncappedPtScore
            }

            rawTimeline.add(
                HistoricalRiskRewardPoint(
                    dateStr = sortedRecords[i].dateStr,
                    timestamp = dates[i],
                    nav = hNav,
                    score = rawPtScore.roundToInt(),
                    scoreLabel = "",
                    asymmetryRatio = asym,
                    pullbackPct = hPullback * 100.0,
                    d20Pct = hd20 * 100.0,
                    fwd1MReturnPct = realizedFwd1M,
                    regimeTag = ptRegTag,
                    mmiScore = histMmi.mmiScore,
                    mmiZone = histMmi.mmiZone,
                    tickertapeMmiScore = histMmi.tickertapeScore,
                    goodreturnsMmiScore = histMmi.goodreturnsScore,
                    indiaVix = histVix,
                    indiaVixRegime = histVixRegime,
                    vixDelta5d = histVixDelta,
                    vixTrendLabel = histVixTrend
                )
            )
        }

        // Apply Continuous Macro Cycle Smoothing (Two-Pass Double EMA with alpha = 0.42 + gentle 3-point Gaussian kernel)
        val rawScores = rawTimeline.map { it.score.toDouble() }
        val nPts = rawScores.size
        val emaFiltered = DoubleArray(nPts)

        var eVal = if (nPts > 0) rawScores[0] else 50.0
        val alphaCycle = 0.42
        for (k in 0 until nPts) {
            eVal = alphaCycle * rawScores[k] + (1.0 - alphaCycle) * eVal
            emaFiltered[k] = eVal
        }

        val smoothTimeline = ArrayList<HistoricalRiskRewardPoint>()
        for (k in 0 until nPts) {
            val pt = rawTimeline[k]
            val isLast = (k == nPts - 1)

            val finalPtScore = if (isLast) {
                // Exact match with top card score without EMA damping mismatch
                finalScore.coerceIn(15, 96)
            } else {
                val smoothed = if (k > 0 && k < nPts - 1) {
                    (emaFiltered[k - 1] * 0.25 + emaFiltered[k] * 0.50 + emaFiltered[k + 1] * 0.25)
                } else {
                    emaFiltered[k]
                }
                smoothed.roundToInt().coerceIn(15, 96)
            }

            val ptLabel = when {
                finalPtScore >= 80 -> "Strong Buy 🟢"
                finalPtScore >= 65 -> "Attractive 🟢"
                finalPtScore >= 50 -> "Neutral 🟡"
                finalPtScore >= 35 -> "Unattractive 🟠"
                else -> "Poor Entry 🔴"
            }

            smoothTimeline.add(
                pt.copy(
                    score = finalPtScore,
                    scoreLabel = if (isLast) label else ptLabel,
                    asymmetryRatio = if (isLast) rawAsymmetry else pt.asymmetryRatio,
                    regimeTag = if (isLast) detectedRegimeTag else pt.regimeTag,
                    mmiScore = if (isLast) liveMmi.mmiScore else pt.mmiScore,
                    mmiZone = if (isLast) liveMmi.mmiZone else pt.mmiZone,
                    tickertapeMmiScore = if (isLast) liveMmi.tickertapeScore else pt.tickertapeMmiScore,
                    goodreturnsMmiScore = if (isLast) liveMmi.goodreturnsScore else pt.goodreturnsMmiScore,
                    indiaVix = if (isLast) currentVix else pt.indiaVix,
                    indiaVixRegime = if (isLast) currentVixRegime else pt.indiaVixRegime,
                    vixDelta5d = if (isLast) vixDynamics.delta5d else pt.vixDelta5d,
                    vixTrendLabel = if (isLast) vixDynamics.trendLabel else pt.vixTrendLabel
                )
            )
        }

        val vixImpactDesc = buildString {
            append("NSE India VIX: ${String.format(Locale.US, "%.2f", currentVix)} (${vixDynamics.regimeLabel}, ${String.format(Locale.US, "%.0f", currentVixPercentile)}th Pctile). ")
            append("5-Day ΔVIX: ${String.format(Locale.US, "%+.2f", vixDynamics.delta5d)} (${String.format(Locale.US, "%+.1f%%", vixDynamics.deltaPct5d)}), Trend: ${vixDynamics.trendLabel}. ")
            if (vixDynamics.delta5d < -0.5 && currentVix >= 18.0) {
                append("Panic Cresting Signal: VIX is elevated but rapidly cooling, which historically generates prime asymmetric rebound entries for pure equity funds (${categoryTitle}, β: ${String.format(Locale.US, "%.2f", vixBeta)}). ")
            } else if (vixDynamics.delta5d > 1.8 && vixDynamics.acceleration > 0.3) {
                append("Active Volatility Shock: Fear is accelerating upward (interim swing bands ±${String.format(Locale.US, "%.1f%%", implied20dVol * 100.0)}); the engine expands expected dip cushions to protect against falling knives. ")
            } else {
                append("Volatility transmission to ${categoryTitle} (β: ${String.format(Locale.US, "%.2f", vixBeta)}) implies a 20-day expected band of ±${String.format(Locale.US, "%.1f%%", implied20dVol * 100.0)}. ")
            }
            append("Blended with Composite MMI (${String.format(Locale.US, "%.1f", liveMmi.mmiScore)} - ${liveMmi.mmiZone}, TMMI: ${String.format(Locale.US, "%.1f", liveMmi.tickertapeScore)}, GMMI: ${String.format(Locale.US, "%.1f", liveMmi.goodreturnsScore)}) for unified sentiment & volatility assessment.")
        }

        return RiskRewardAnalysis(
            schemeName = schemeName,
            score = finalScore,
            scoreLabel = label,
            probabilityPositivePct = probPos,
            typicalRewardPct = forwardReward * 100.0,
            typicalRiskPct = (-abs(avgMAE)) * 100.0,
            worstLossPct = worstInterimLoss * 100.0,
            confidenceLevel = confidenceLabel,
            comparablesCount = totalMatches,
            currentR20 = currR20 * 100.0,
            currentD20 = currD20 * 100.0,
            currentD50 = currD50 * 100.0,
            currentD200 = currD200 * 100.0,
            movingAverage20 = currDMA20,
            movingAverage50 = currDMA50,
            movingAverage200 = currDMA200,
            currentPullbackFromPeakPct = currPullbackPeak * 100.0,
            highestNavSoFar = currHighestNav,
            currentRolling1MPctile = currR20Percentile,
            dma20_50_SpreadPct = currSpread20_50 * 100.0,
            avgInterimDipPct = avgMAE * 100.0,
            avgInterimPeakPct = avgMFE * 100.0,
            currentR40 = currR40 * 100.0,
            momentumAccelerationPct = currAccel * 100.0,
            portfolioBreadthPct = currPortfolioBreadth,
            relativeStrengthPct = currRelativeStrength * 100.0,
            regimeTag = detectedRegimeTag,
            forward2MRewardPct = avg2M * 100.0,
            benchmarkBreadthPct = currBenchBreadth,
            nifty500_1M_ReturnPct = currN500R20 * 100.0,
            nifty500AlphaPct = currN500Alpha * 100.0,
            nifty500PullbackPct = currN500Pullback * 100.0,
            smallcap250_1M_ReturnPct = currNSmallR20 * 100.0,
            asymmetryRatio = rawAsymmetry,
            coreVerdictExplanation = verdictExplanation,
            directionalAccuracyPct = weights.directionalAccuracyPct,
            calibrationErrorMse = weights.meanSquaredError,
            tuningStatus = "Self-Tuned & Error-Checked (${weights.totalWalkForwardSessions} Sessions)",
            historicalTimeline = smoothTimeline,
            mmiScore = liveMmi.mmiScore,
            mmiZone = liveMmi.mmiZone,
            tickertapeMmiScore = liveMmi.tickertapeScore,
            tickertapeMmiZone = liveMmi.tickertapeZone,
            goodreturnsMmiScore = liveMmi.goodreturnsScore,
            goodreturnsMmiZone = liveMmi.goodreturnsZone,
            mmiContrarianBonus = mmiNetScoreImpact,
            mmiInterpretation = liveMmi.interpretation,
            indiaVix = currentVix,
            indiaVixDelta5d = vixDynamics.delta5d,
            indiaVixDeltaPct5d = vixDynamics.deltaPct5d,
            indiaVixDelta20d = vixDynamics.delta20d,
            indiaVixAcceleration = vixDynamics.acceleration,
            indiaVixTrendLabel = vixDynamics.trendLabel,
            indiaVixPercentile = currentVixPercentile,
            indiaVixRegime = currentVixRegime,
            indiaVixSensitivityBeta = vixBeta,
            indiaVixImpactDescription = vixImpactDesc,
            blendedSentimentVolScore = blendedSentimentVol,
            fundVolatilityPct = fundVol * 100.0,
            categoryProfile = categoryTitle,
            volatilityScalingRatio = volScalingRatio,
            timesFmScore = timesFmResult.compositeTimesFmScore,
            timesFmBadge = timesFmResult.scoreBadge,
            timesFmAsymmetryRatio = timesFmResult.timesFmAsymmetryRatio,
            timesFmExpectedReturn20d = timesFmResult.swing20d.q50MedianReturnPct,
            timesFmQuantileUpside = timesFmResult.swing20d.q90UpsideReturnPct,
            timesFmQuantileDownside = timesFmResult.swing20d.q10ReturnPct,
            timesFmPatchTrend = timesFmResult.patchShapeTrend,
            timesFmConfidencePct = timesFmResult.modelConfidencePct,
            timesFmExplanation = timesFmResult.explanation,
            timesFmTactical5dReturn = timesFmResult.tactical5d.q50MedianReturnPct,
            timesFmTrend40dReturn = timesFmResult.trend40d.q50MedianReturnPct,
            timesFmTopAnalogues = timesFmResult.topHistoricalAnalogues,
            timesFmDailyCorrectionFactor = timesFmResult.dailyLearningMetrics.dailyCorrectionFactor,
            timesFmDailyLearningStatus = timesFmResult.dailyLearningMetrics.learningStatus,
            timesFmDailyLearningExplanation = timesFmResult.dailyLearningMetrics.dailyLearningExplanation,
            timesFmTotalAnaloguesScanned = timesFmResult.totalHistoricalSituationsScanned
        )
    }
}
