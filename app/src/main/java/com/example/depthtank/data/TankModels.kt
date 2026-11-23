package com.example.depthtank.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class MarketRegime(
    val pillLabel: String,
    val title: String,
    val description: String
) {
    CAPITULATION(
        pillLabel = "CAPITULATION",
        title = "Dry Tank / Capitulation (0–15%)",
        description = "Broad market washout. Over 85% of momentum capacity is drained below the 50% hole—historically strong deep-value accumulation zones."
    ),
    ACCUMULATION(
        pillLabel = "ACCUMULATION",
        title = "Recharging / Accumulation (15–40%)",
        description = "Early breadth recovery. Leaders are crossing above the 50% midpoint valve while broader sentiment remains skeptical."
    ),
    NEUTRAL(
        pillLabel = "NEUTRAL",
        title = "Balanced Flow (40–55%)",
        description = "Selective market participation with healthy rotation across sectors around the equilibrium midpoint."
    ),
    BULLISH_FLOW(
        pillLabel = "BULLISH FLOW",
        title = "Healthy Bull Flow (55–75%)",
        description = "Strong equal-weight participation pouring smoothly into the Main Tank without euphoric overheating."
    ),
    OVERHEATED(
        pillLabel = "OVERHEATED",
        title = "Overheated / Caution (75–100%)",
        description = "Euphoric breadth extreme. Most constituents are pinned near 52-Week highs; risk-reward favors caution and disciplined trailing."
    )
}

enum class InstitutionalRegime(
    val pillLabel: String,
    val title: String,
    val description: String
) {
    DEEP_CAPITULATION(
        pillLabel = "DEEP CAPITULATION",
        title = "Deep Capitulation",
        description = "Systemic internal liquidation with broad constituents compressed into the lower 15% of their 52-Week range."
    ),
    ACCUMULATION(
        pillLabel = "ACCUMULATION",
        title = "Accumulation",
        description = "Quiet base-building below midpoint flow as forced selling subsides and early leaders stabilize."
    ),
    RECOVERY(
        pillLabel = "RECOVERY",
        title = "Recovery",
        description = "Expanding breadth impulse lifting off depressed levels with improving 52-Week breakout participation."
    ),
    HEALTHY_BULL_FLOW(
        pillLabel = "HEALTHY BULL FLOW",
        title = "Healthy Bull Flow",
        description = "Balanced equal-weight expansion above the 50% valve without overextended crowding or breadth divergence."
    ),
    ELEVATED_EUPHORIA(
        pillLabel = "ELEVATED EUPHORIA",
        title = "Elevated Euphoria",
        description = "High breadth above the 75% overheated line supported by constructive 52-Week breakout participation."
    ),
    EXTREME_EUPHORIA(
        pillLabel = "EXTREME EUPHORIA",
        title = "Extreme Euphoria",
        description = "Crowded upper-range concentration with widespread constituent scores above 85 and elevated plateau extension."
    ),
    MOMENTUM_EXHAUSTION(
        pillLabel = "MOMENTUM EXHAUSTION",
        title = "Momentum Exhaustion",
        description = "Extended overheated plateau accompanied by fading 52-Week breakout pulse (negative 20-day divergence)."
    ),
    BREADTH_DETERIORATION(
        pillLabel = "BREADTH DETERIORATION",
        title = "Breadth Deterioration",
        description = "Internal participation rolling over as fewer stocks sustain 52-Week highs and overflow capacity drains."
    );

    fun toLegacyRegime(tankLevel: Double): MarketRegime = when (this) {
        DEEP_CAPITULATION -> MarketRegime.CAPITULATION
        ACCUMULATION -> MarketRegime.ACCUMULATION
        RECOVERY -> if (tankLevel < 40.0) MarketRegime.ACCUMULATION else MarketRegime.NEUTRAL
        HEALTHY_BULL_FLOW -> MarketRegime.BULLISH_FLOW
        BREADTH_DETERIORATION -> when {
            tankLevel < 40.0 -> MarketRegime.ACCUMULATION
            tankLevel < 55.0 -> MarketRegime.NEUTRAL
            else -> MarketRegime.BULLISH_FLOW
        }
        ELEVATED_EUPHORIA, EXTREME_EUPHORIA, MOMENTUM_EXHAUSTION -> MarketRegime.OVERHEATED
    }
}

enum class DataQuality(val wireValue: String) {
    VERIFIED_EOD("VERIFIED_EOD"),
    ILLUSTRATIVE_FALLBACK("ILLUSTRATIVE_FALLBACK");

    companion object {
        fun fromWire(value: String?): DataQuality =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
                ?: ILLUSTRATIVE_FALLBACK
    }
}

data class DatasetIntegrityMetadata(
    val dataQuality: DataQuality = DataQuality.ILLUSTRATIVE_FALLBACK,
    val constituentCoverage: Double = 0.0,
    val isIllustrative: Boolean = true,
    val lastVerifiedTradingDate: String = "2026-11-13",
    val modelVersion: String = MODEL_VERSION,
    val calculationVersion: String = CALCULATION_VERSION,
    val excludedInsufficientHistoryCount: Int = 0
) {
    companion object {
        const val MODEL_VERSION = "2.0"
        const val CALCULATION_VERSION = "2.0.1"
        const val INDEX_TARGET_CONSTITUENTS = 500
    }

    fun isSchemaValid(): Boolean {
        return constituentCoverage in 0.0..1.0 &&
            lastVerifiedTradingDate.isNotBlank() &&
            modelVersion == MODEL_VERSION &&
            calculationVersion.isNotBlank()
    }
}

data class FourPillarSnapshot(
    val sessionDate: String = "",
    val mainTankLevelPct: Double = 0.0,
    val mainTankDailyDeltaPct: Double = 0.0,
    val isOverheatedThresholdBreached: Boolean = false,
    val plateauDays: Int = 0,
    val euphoriaPressurePct: Double = 0.0,
    val euphoriaPressureCount: Int = 0,
    val breakoutPulsePct: Double = 0.0,
    val breakoutPulseCount: Int = 0,
    val breakoutPulseDelta20: Double = 0.0,
    val capitulationDepthPct: Double = 0.0,
    val capitulationCount: Int = 0,
    val activeEligibleStocksCount: Int = 0,
    val excludedInsufficientHistoryCount: Int = 0,
    val regime: InstitutionalRegime = InstitutionalRegime.ACCUMULATION,
    val hasHiddenBearishDivergence: Boolean = false
)

enum class TimeframeOption(val label: String, val tradingDays: Int) {
    M1("1M", 22),
    M6("6M", 126),
    Y1("1Y", 252),
    Y3("3Y", 756),
    Y5("5Y", 1260),
    Y10("10Y", 2520),
    Y20("20Y", 5040),
    MAX("MAX (1995)", Int.MAX_VALUE);

    val requiresPre2016Archive: Boolean
        get() = this == Y10 || this == Y20 || this == MAX
}

enum class ChartAggregation(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

enum class ArchiveHydrationState {
    NOT_LOADED,
    LOADING,
    READY,
    FAILED
}

data class ArchiveProvenanceSummary(
    val state: ArchiveHydrationState = ArchiveHydrationState.NOT_LOADED,
    val startDate: String = "2016-04-01",
    val endDate: String = "2016-03-31",
    val firstActive200dDate: String = "1995-08-25",
    val reconstructedPeriodEnd: String = "1998-07-31",
    val vixInceptionDate: String = "2008-03-03",
    val mmiInceptionDate: String = "2016-04-01",
    val pre2016SessionCount: Int = 0,
    val pre2016VixSessionCount: Int = 0,
    val loadedFromPersistentCache: Boolean = false,
    val sha256Verified: Boolean = false,
    val errorMessage: String? = null
) {
    val loadedFromCache: Boolean get() = loadedFromPersistentCache
    val checksumVerified: Boolean get() = sha256Verified
}

enum class FlowFilter(val label: String) {
    ALL("All"),
    FLOWING("Flowing (>50)"),
    HIGH_PRESSURE("High Pressure (>85)"),
    BELOW_HOLE("Below Hole (<50)");

    fun displayLabel(holeThreshold: Float): String {
        val hInt = round(holeThreshold).toInt()
        return when (this) {
            ALL -> "All"
            FLOWING -> "Flowing (>$hInt%)"
            HIGH_PRESSURE -> "High Pressure (>85%)"
            BELOW_HOLE -> "Below Hole (≤$hInt%)"
        }
    }
}

enum class TankIndexUniverse(
    val shortLabel: String,
    val dropdownLabel: String,
    val dashboardTitle: String,
    val rangeLabel: String,
    val targetCount: Int,
    val constituentKey: String,
    val subIndexHistoryKey: String
) {
    NIFTY_500(
        shortLabel = "Nifty 500",
        dropdownLabel = "Nifty 500 (Default • 1–500)",
        dashboardTitle = "Nifty 500 Depth Tank",
        rangeLabel = "1–500",
        targetCount = 500,
        constituentKey = "nifty500",
        subIndexHistoryKey = "nifty500"
    ),
    NIFTY_50(
        shortLabel = "Nifty 50",
        dropdownLabel = "Nifty 50 (0–50)",
        dashboardTitle = "Nifty 50 Depth Tank",
        rangeLabel = "0–50",
        targetCount = 50,
        constituentKey = "nifty50",
        subIndexHistoryKey = "nifty50"
    ),
    NEXT_50(
        shortLabel = "Next 50",
        dropdownLabel = "Next 50 (51–100)",
        dashboardTitle = "Next 50 Depth Tank",
        rangeLabel = "51–100",
        targetCount = 50,
        constituentKey = "niftyNext50",
        subIndexHistoryKey = "next50"
    ),
    MIDCAP_150(
        shortLabel = "Midcap 150",
        dropdownLabel = "Midcap 150 (101–250)",
        dashboardTitle = "Midcap 150 Depth Tank",
        rangeLabel = "101–250",
        targetCount = 150,
        constituentKey = "niftyMidcap150",
        subIndexHistoryKey = "midcap150"
    ),
    SMALLCAP_250(
        shortLabel = "Smallcap 250",
        dropdownLabel = "Smallcap 250 (251–500)",
        dashboardTitle = "Smallcap 250 Depth Tank",
        rangeLabel = "251–500",
        targetCount = 250,
        constituentKey = "niftySmallcap250",
        subIndexHistoryKey = "smallcap250"
    );
}

data class StockTank(
    val symbol: String,
    val name: String,
    val sector: String,
    val currentPrice: Double,
    val low52W: Double,
    val high52W: Double,
    val dailyChangePct: Double,
    val todayHigh: Double = currentPrice,
    val prior52WHigh: Double? = null,
    val rollingHistoryDays: Int = TankCalculator.FULL_52W_TRADING_DAYS,
    val peTtm: Double? = null,
    val peLow52W: Double? = null,
    val peHigh52W: Double? = null,
    val epsTtm: Double? = null
) {
    val hasValid52WeekWindow: Boolean
        get() = TankCalculator.hasValid52WeekHistory(currentPrice, low52W, high52W, rollingHistoryDays)

    val score: Double
        get() = TankCalculator.computeStockScore(currentPrice, low52W, high52W)

    val hasValidPeWindow: Boolean
        get() = TankCalculator.hasValid52WeekPeWindow(peTtm, peLow52W, peHigh52W)

    val peScore: Double?
        get() = if (hasValidPeWindow) {
            TankCalculator.computeStockScore(peTtm!!, peLow52W!!, peHigh52W!!)
        } else {
            null
        }

    val overflowVolume: Double
        get() = TankCalculator.computeOverflowVolume(score, 50.0)

    val isFlowing: Boolean
        get() = score > 50.0

    val isHighPressure: Boolean
        get() = score > TankCalculator.EUPHORIA_SCORE_THRESHOLD

    val isCapitulation: Boolean
        get() = score < TankCalculator.CAPITULATION_SCORE_THRESHOLD

    val isMakingFresh52WHigh: Boolean
        get() = TankCalculator.isGenuine52WeekBreakout(todayHigh, prior52WHigh)

    fun overflowVolumeAt(holeThreshold: Double): Double =
        TankCalculator.computeOverflowVolume(score, holeThreshold)

    fun isFlowingAt(holeThreshold: Double): Boolean =
        score > holeThreshold

    fun peOverflowVolumeAt(holeThreshold: Double): Double {
        val sc = peScore ?: return 0.0
        return TankCalculator.computeOverflowVolume(sc, holeThreshold)
    }

    fun isPeFlowingAt(holeThreshold: Double): Boolean {
        val sc = peScore ?: return false
        return sc > holeThreshold
    }

    fun contributionPct(activeStocksCount: Int, holeThreshold: Double = 50.0): Double {
        if (activeStocksCount <= 0) return 0.0
        val maxPerStock = 100.0 - holeThreshold.coerceIn(0.0, 100.0)
        if (maxPerStock <= 0.0001) return 0.0
        return (overflowVolumeAt(holeThreshold) / (activeStocksCount * maxPerStock)) * 100.0
    }
}

data class TankHistoryPoint(
    val date: String,
    val tankLevel: Double,
    val dailyDelta: Double,
    val flowingCount: Int,
    val activeCount: Int,
    val highPressureCount: Int,
    val avgScore: Double,
    val euphoriaPressurePct: Double = if (activeCount > 0) round((highPressureCount.toDouble() / activeCount) * 10000.0) / 100.0 else 0.0,
    val breakoutCount: Int = 0,
    val breakoutPulsePct: Double = 0.0,
    val breakoutPulseDelta20: Double = 0.0,
    val capitulationCount: Int = 0,
    val capitulationDepthPct: Double = 0.0,
    val plateauDays: Int = if (tankLevel > TankCalculator.OVERHEATED_TANK_THRESHOLD_PCT) 1 else 0,
    val scoreBins: List<Int> = emptyList(),
    val provenance: String? = null,
    val peTankLevel: Double? = null,
    val medianPe: Double? = null,
    val peActiveCount: Int = 0,
    val peScoreBins: List<Int> = emptyList(),
    val indexPe: Double? = null,
    val indexPb: Double? = null,
    val indexDivYield: Double? = null
) {
    val isIncompleteColdStart: Boolean
        get() = activeCount <= 0 || provenance == PROVENANCE_INCOMPLETE_COLD_START

    val isReconstructedHistorical: Boolean
        get() = provenance == PROVENANCE_RECONSTRUCTED_HISTORICAL

    val hasValuationPeData: Boolean
        get() = peTankLevel != null && peTankLevel.isFinite()

    val hasOfficialIndexPe: Boolean
        get() = indexPe != null && indexPe.isFinite() && indexPe > 0.0

    companion object {
        const val PROVENANCE_INCOMPLETE_COLD_START = "INCOMPLETE_COLD_START"
        const val PROVENANCE_RECONSTRUCTED_HISTORICAL = "RECONSTRUCTED_HISTORICAL"
        const val PROVENANCE_VERIFIED_EOD = "VERIFIED_EOD"
    }
}

data class SectorTankSummary(
    val sector: String,
    val stockCount: Int,
    val flowingCount: Int,
    val avgScore: Double,
    val totalOverflow: Double,
    val overflowSharePct: Double
)

data class SyncPipelineStatus(
    val isSyncing: Boolean = false,
    val lastSyncedDisplay: String = "Auto-Synced: EOD Vault (13-Nov-2026)",
    val lastTradeDate: String = "2026-11-13",
    val scheduleDisplay: String = "Live Auto-Refresh (9:15 AM–3:35 PM IST) & EOD @ 4:15 PM IST",
    val sourceDisplay: String = "NSE/BSE Live Constituent Feed",
    val trackedStocksCount: Int = 500,
    val isLiveHydrated: Boolean = false,
    val isLiveMarketActive: Boolean = false,
    val errorMessage: String? = null,
    val integrity: DatasetIntegrityMetadata = DatasetIntegrityMetadata(),
    val historyIntegrity: DatasetIntegrityMetadata = DatasetIntegrityMetadata()
)

object NseMarketCalendar {
    val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    val MARKET_OPEN_TIME: LocalTime = LocalTime.of(9, 15)
    val MARKET_CLOSE_TIME: LocalTime = LocalTime.of(15, 35)
    val EOD_SETTLEMENT_END_TIME: LocalTime = LocalTime.of(16, 30)
    private val DISPLAY_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US)

    private val NSE_HOLIDAYS: Set<LocalDate> = setOf(
        LocalDate.of(2025, 2, 26), LocalDate.of(2025, 3, 14), LocalDate.of(2025, 3, 31),
        LocalDate.of(2025, 4, 10), LocalDate.of(2025, 4, 14), LocalDate.of(2025, 4, 18),
        LocalDate.of(2025, 5, 1), LocalDate.of(2025, 8, 15), LocalDate.of(2025, 8, 27),
        LocalDate.of(2025, 10, 2), LocalDate.of(2025, 10, 22), LocalDate.of(2025, 11, 5),
        LocalDate.of(2025, 12, 25),
        LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 26), LocalDate.of(2026, 3, 3),
        LocalDate.of(2026, 3, 26), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 3),
        LocalDate.of(2026, 4, 14), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 28),
        LocalDate.of(2026, 6, 26), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 2),
        LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 24),
        LocalDate.of(2026, 12, 25),
        LocalDate.of(2027, 1, 26), LocalDate.of(2027, 3, 22), LocalDate.of(2027, 3, 26),
        LocalDate.of(2027, 4, 14), LocalDate.of(2027, 10, 20), LocalDate.of(2027, 11, 12)
    )

    fun isTradingDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY &&
            date.dayOfWeek != DayOfWeek.SUNDAY &&
            date !in NSE_HOLIDAYS

    fun isMarketOpen(nowIst: ZonedDateTime): Boolean {
        if (!isTradingDay(nowIst.toLocalDate())) return false
        val t = nowIst.toLocalTime()
        return !t.isBefore(MARKET_OPEN_TIME) && !t.isAfter(MARKET_CLOSE_TIME)
    }

    fun isPostCloseSettlementWindow(nowIst: ZonedDateTime): Boolean {
        if (!isTradingDay(nowIst.toLocalDate())) return false
        val t = nowIst.toLocalTime()
        return t.isAfter(MARKET_CLOSE_TIME) && !t.isAfter(EOD_SETTLEMENT_END_TIME)
    }

    fun isValidTradingIsoDate(isoDate: String): Boolean = try {
        isTradingDay(LocalDate.parse(isoDate))
    } catch (_: Exception) {
        false
    }

    fun latestMarketSessionDate(nowIst: ZonedDateTime): LocalDate {
        var date = nowIst.toLocalDate()
        if (isTradingDay(date) && nowIst.toLocalTime().isBefore(MARKET_OPEN_TIME)) {
            date = date.minusDays(1)
        }
        while (!isTradingDay(date)) {
            date = date.minusDays(1)
        }
        return date
    }

    fun formatDdMmmYyyy(isoDate: String): String = try {
        LocalDate.parse(isoDate).format(DISPLAY_DATE_FORMATTER)
    } catch (_: Exception) {
        isoDate
    }
}

object TankCalculator {

    const val FULL_52W_TRADING_DAYS = 252
    const val MIN_52W_REQUIRED_TRADING_DAYS = 200
    const val OVERHEATED_TANK_THRESHOLD_PCT = 75.0
    const val EUPHORIA_SCORE_THRESHOLD = 85.0
    const val CAPITULATION_SCORE_THRESHOLD = 15.0
    const val BREAKOUT_LOOKBACK_DAYS = 20
    const val SCORE_HISTOGRAM_BIN_COUNT = 20
    const val SCORE_HISTOGRAM_BIN_WIDTH = 5.0

    fun hasValid52WeekHistory(
        currentPrice: Double,
        low52W: Double,
        high52W: Double,
        rollingHistoryDays: Int,
        minRequiredDays: Int = MIN_52W_REQUIRED_TRADING_DAYS
    ): Boolean {
        if (rollingHistoryDays < minRequiredDays) return false
        if (currentPrice.isNaN() || low52W.isNaN() || high52W.isNaN()) return false
        if (currentPrice <= 0.0 || low52W <= 0.0) return false
        return (high52W - low52W) > 0.0001
    }

    fun filterEligibleConstituents(
        stocks: List<StockTank>,
        minRequiredDays: Int = MIN_52W_REQUIRED_TRADING_DAYS
    ): List<StockTank> {
        return stocks.filter {
            hasValid52WeekHistory(it.currentPrice, it.low52W, it.high52W, it.rollingHistoryDays, minRequiredDays)
        }
    }

    fun computeStockScore(currentPrice: Double, low52W: Double, high52W: Double): Double {
        val range = high52W - low52W
        if (range <= 0.0001 || currentPrice.isNaN() || low52W.isNaN() || high52W.isNaN()) {
            return 50.0
        }
        return (((currentPrice - low52W) / range) * 100.0).coerceIn(0.0, 100.0)
    }

    fun computeOverflowVolume(stockScore: Double, holeThreshold: Double = 50.0): Double {
        if (stockScore.isNaN()) return 0.0
        val h = holeThreshold.coerceIn(0.0, 100.0)
        return max(0.0, stockScore.coerceIn(0.0, 100.0) - h)
    }

    fun hasValid52WeekPeWindow(
        peTtm: Double?,
        peLow52W: Double?,
        peHigh52W: Double?
    ): Boolean {
        if (peTtm == null || peLow52W == null || peHigh52W == null) return false
        if (!peTtm.isFinite() || !peLow52W.isFinite() || !peHigh52W.isFinite()) return false
        if (peTtm <= 0.0 || peLow52W <= 0.0 || peHigh52W <= 0.0) return false
        return (peHigh52W - peLow52W) > 0.05
    }

    fun filterEligiblePeConstituents(stocks: List<StockTank>): List<StockTank> =
        stocks.filter { it.hasValidPeWindow }

    fun computeMainTankLevel(stocks: List<StockTank>, holeThreshold: Double = 50.0): Double {
        val eligible = filterEligibleConstituents(stocks)
        if (eligible.isEmpty()) return 0.0
        val h = holeThreshold.coerceIn(0.0, 100.0)
        val maxPossiblePerStock = 100.0 - h
        if (maxPossiblePerStock <= 0.0001) return 0.0
        var totalOverflow = 0.0
        for (stock in eligible) {
            totalOverflow += computeOverflowVolume(stock.score, h)
        }
        val maxPossibleTotal = eligible.size * maxPossiblePerStock
        return ((totalOverflow / maxPossibleTotal) * 100.0).coerceIn(0.0, 100.0)
    }

    fun computePeTankLevel(stocks: List<StockTank>, holeThreshold: Double = 50.0): Double? {
        val eligiblePe = filterEligiblePeConstituents(stocks)
        if (eligiblePe.isEmpty()) return null
        val h = holeThreshold.coerceIn(0.0, 100.0)
        val maxPossiblePerStock = 100.0 - h
        if (maxPossiblePerStock <= 0.0001) return 0.0
        var totalOverflow = 0.0
        for (stock in eligiblePe) {
            val sc = stock.peScore ?: continue
            totalOverflow += computeOverflowVolume(sc, h)
        }
        val maxPossibleTotal = eligiblePe.size * maxPossiblePerStock
        return roundPct(((totalOverflow / maxPossibleTotal) * 100.0).coerceIn(0.0, 100.0))
    }

    fun computeMedianPe(stocks: List<StockTank>): Double? {
        val values = stocks.mapNotNull { it.peTtm }
            .filter { it.isFinite() && it > 0.0 && it < 5000.0 }
            .sorted()
        if (values.isEmpty()) return null
        val mid = values.size / 2
        val raw = if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) * 0.5
        return roundPct(raw)
    }

    fun isGenuine52WeekBreakout(todayHigh: Double, prior52WHigh: Double?): Boolean {
        if (prior52WHigh == null || prior52WHigh.isNaN() || todayHigh.isNaN()) return false
        if (prior52WHigh <= 0.0 || todayHigh <= 0.0) return false
        return todayHigh > prior52WHigh
    }

    fun computeEuphoriaPressurePct(eligibleStocks: List<StockTank>): Double {
        if (eligibleStocks.isEmpty()) return 0.0
        val count = eligibleStocks.count { it.isHighPressure }
        return roundPct((count.toDouble() / eligibleStocks.size) * 100.0)
    }

    fun computeBreakoutPulsePct(eligibleStocks: List<StockTank>): Double {
        if (eligibleStocks.isEmpty()) return 0.0
        val count = eligibleStocks.count { it.isMakingFresh52WHigh }
        return roundPct((count.toDouble() / eligibleStocks.size) * 100.0)
    }

    fun computeCapitulationDepthPct(eligibleStocks: List<StockTank>): Double {
        if (eligibleStocks.isEmpty()) return 0.0
        val count = eligibleStocks.count { it.isCapitulation }
        return roundPct((count.toDouble() / eligibleStocks.size) * 100.0)
    }

    fun computePlateauDays(
        history: List<TankHistoryPoint>,
        threshold: Double = OVERHEATED_TANK_THRESHOLD_PCT
    ): Int {
        var streak = 0
        for (i in history.indices.reversed()) {
            if (history[i].tankLevel > threshold) streak++ else break
        }
        return streak
    }

    fun computeBreakoutPulseDelta20(
        history: List<TankHistoryPoint>,
        currentBreakoutPulsePct: Double? = null
    ): Double {
        if (history.isEmpty()) return 0.0
        val latestPct = currentBreakoutPulsePct ?: history.last().breakoutPulsePct
        val lookbackIdx = history.size - 1 - BREAKOUT_LOOKBACK_DAYS
        if (lookbackIdx < 0) return 0.0
        return roundPct(latestPct - history[lookbackIdx].breakoutPulsePct)
    }

    /**
     * Causal 8-State Multi-Pillar Regime Classifier.
     * Critically: Main Tank > 75% alone never emits a deterioration or exhaustion warning without
     * corroborating 52-Week breakout pulse contraction / hidden bearish divergence.
     */
    fun classifyInstitutionalRegime(
        tankLevel: Double,
        dailyDelta: Double,
        plateauDays: Int,
        euphoriaPressurePct: Double,
        breakoutPulsePct: Double,
        breakoutPulseDelta20: Double,
        capitulationDepthPct: Double
    ): InstitutionalRegime {
        if (tankLevel < 15.0 || (tankLevel < 22.0 && capitulationDepthPct >= 28.0)) {
            return InstitutionalRegime.DEEP_CAPITULATION
        }
        if (tankLevel >= OVERHEATED_TANK_THRESHOLD_PCT) {
            val hasConfirmedExhaustion = breakoutPulseDelta20 <= -2.5 &&
                breakoutPulsePct < 8.0 &&
                (plateauDays >= 10 || dailyDelta < -0.5)
            return when {
                hasConfirmedExhaustion -> InstitutionalRegime.MOMENTUM_EXHAUSTION
                euphoriaPressurePct >= 62.0 || tankLevel >= 82.0 -> InstitutionalRegime.EXTREME_EUPHORIA
                else -> InstitutionalRegime.ELEVATED_EUPHORIA
            }
        }
        if (tankLevel >= 55.0) {
            return when {
                breakoutPulseDelta20 <= -3.0 && dailyDelta < 0.0 -> InstitutionalRegime.BREADTH_DETERIORATION
                euphoriaPressurePct >= 50.0 && tankLevel >= 68.0 -> InstitutionalRegime.ELEVATED_EUPHORIA
                else -> InstitutionalRegime.HEALTHY_BULL_FLOW
            }
        }
        if (tankLevel >= 40.0) {
            return when {
                breakoutPulseDelta20 <= -2.0 && dailyDelta < -0.5 -> InstitutionalRegime.BREADTH_DETERIORATION
                dailyDelta > 0.25 || breakoutPulseDelta20 >= 1.0 -> InstitutionalRegime.RECOVERY
                tankLevel >= 48.0 && breakoutPulsePct >= 4.0 && capitulationDepthPct < 10.0 -> InstitutionalRegime.HEALTHY_BULL_FLOW
                else -> InstitutionalRegime.ACCUMULATION
            }
        }
        return when {
            breakoutPulseDelta20 <= -3.5 && dailyDelta < -1.0 -> InstitutionalRegime.BREADTH_DETERIORATION
            dailyDelta > 0.5 || breakoutPulseDelta20 >= 1.5 -> InstitutionalRegime.RECOVERY
            else -> InstitutionalRegime.ACCUMULATION
        }
    }

    fun computeFourPillarSnapshot(
        stocks: List<StockTank>,
        history: List<TankHistoryPoint>,
        sessionDate: String = history.lastOrNull()?.date.orEmpty()
    ): FourPillarSnapshot {
        val eligible = filterEligibleConstituents(stocks)
        val excludedCount = (stocks.size - eligible.size).coerceAtLeast(0)
        val latest = history.lastOrNull()

        val tankLevel = if (eligible.isNotEmpty()) {
            roundPct(computeMainTankLevel(eligible, 50.0))
        } else {
            latest?.tankLevel ?: 0.0
        }
        val prevTankLevel = if (history.size >= 2) history[history.size - 2].tankLevel else tankLevel
        val dailyDelta = if (eligible.isNotEmpty() && history.isNotEmpty()) {
            roundPct(tankLevel - prevTankLevel)
        } else {
            latest?.dailyDelta ?: 0.0
        }

        val eupCount = if (eligible.isNotEmpty()) eligible.count { it.isHighPressure } else (latest?.highPressureCount ?: 0)
        val eupPct = if (eligible.isNotEmpty()) computeEuphoriaPressurePct(eligible) else (latest?.euphoriaPressurePct ?: 0.0)

        val hasPriorHighData = eligible.any { it.prior52WHigh != null }
        val bpCount = when {
            eligible.isNotEmpty() && hasPriorHighData -> eligible.count { it.isMakingFresh52WHigh }
            else -> latest?.breakoutCount ?: 0
        }
        val bpPct = when {
            eligible.isNotEmpty() && hasPriorHighData -> computeBreakoutPulsePct(eligible)
            else -> latest?.breakoutPulsePct ?: 0.0
        }

        val capCount = if (eligible.isNotEmpty()) eligible.count { it.isCapitulation } else (latest?.capitulationCount ?: 0)
        val capPct = if (eligible.isNotEmpty()) computeCapitulationDepthPct(eligible) else (latest?.capitulationDepthPct ?: 0.0)

        val plateauDays = computePlateauDays(history)
        val deltaB20 = if (history.isNotEmpty() && latest?.date == sessionDate && latest.breakoutPulseDelta20 != 0.0 && !hasPriorHighData) {
            latest.breakoutPulseDelta20
        } else {
            computeBreakoutPulseDelta20(history, bpPct)
        }
        val regime = classifyInstitutionalRegime(
            tankLevel = tankLevel,
            dailyDelta = dailyDelta,
            plateauDays = plateauDays,
            euphoriaPressurePct = eupPct,
            breakoutPulsePct = bpPct,
            breakoutPulseDelta20 = deltaB20,
            capitulationDepthPct = capPct
        )
        val hiddenDivergence = tankLevel >= OVERHEATED_TANK_THRESHOLD_PCT &&
            plateauDays >= 5 &&
            deltaB20 <= -2.5

        return FourPillarSnapshot(
            sessionDate = sessionDate,
            mainTankLevelPct = tankLevel,
            mainTankDailyDeltaPct = dailyDelta,
            isOverheatedThresholdBreached = tankLevel > OVERHEATED_TANK_THRESHOLD_PCT,
            plateauDays = plateauDays,
            euphoriaPressurePct = eupPct,
            euphoriaPressureCount = eupCount,
            breakoutPulsePct = bpPct,
            breakoutPulseCount = bpCount,
            breakoutPulseDelta20 = deltaB20,
            capitulationDepthPct = capPct,
            capitulationCount = capCount,
            activeEligibleStocksCount = if (eligible.isNotEmpty()) eligible.size else (latest?.activeCount ?: 0),
            excludedInsufficientHistoryCount = excludedCount,
            regime = regime,
            hasHiddenBearishDivergence = hiddenDivergence
        )
    }

    fun classifyRegime(tankLevel: Double): MarketRegime = when {
        tankLevel < 15.0 -> MarketRegime.CAPITULATION
        tankLevel < 40.0 -> MarketRegime.ACCUMULATION
        tankLevel < 55.0 -> MarketRegime.NEUTRAL
        tankLevel < 75.0 -> MarketRegime.BULLISH_FLOW
        else -> MarketRegime.OVERHEATED
    }

    fun overheatedThresholdForHole(holeThreshold: Double): Double {
        val h = holeThreshold.coerceIn(0.0, 100.0)
        return when {
            abs(h - 50.0) < 0.5 -> OVERHEATED_TANK_THRESHOLD_PCT
            h > 50.0 -> {
                val t = ((h - 50.0) / 50.0).coerceIn(0.0, 1.0)
                roundPct((75.0 - 30.0 * t).coerceIn(35.0, 75.0))
            }
            else -> {
                val t = ((50.0 - h) / 50.0).coerceIn(0.0, 1.0)
                roundPct((75.0 + 13.0 * t).coerceIn(75.0, 88.0))
            }
        }
    }

    fun capitulationThresholdForHole(holeThreshold: Double): Double {
        val h = holeThreshold.coerceIn(0.0, 100.0)
        return when {
            abs(h - 50.0) < 0.5 -> 15.0
            h > 50.0 -> {
                val t = ((h - 50.0) / 50.0).coerceIn(0.0, 1.0)
                roundPct((15.0 - 11.0 * t).coerceIn(3.0, 15.0))
            }
            else -> {
                val t = ((50.0 - h) / 50.0).coerceIn(0.0, 1.0)
                roundPct((15.0 + 17.0 * t).coerceIn(15.0, 32.0))
            }
        }
    }

    /**
     * Builds an exact 20-bin constituent 52-Week score histogram ([0,5), [5,10), ..., [95,100])
     * across eligible constituents so any arbitrary Hole Position H in [0, 100] can be integrated
     * from actual constituent distribution rather than synthetic curve exponents.
     */
    fun buildScoreBins(stocks: List<StockTank>): List<Int> {
        val eligible = filterEligibleConstituents(stocks)
        if (eligible.isEmpty()) return emptyList()
        val bins = IntArray(SCORE_HISTOGRAM_BIN_COUNT)
        for (stock in eligible) {
            val sc = stock.score.coerceIn(0.0, 100.0)
            val idx = (sc / SCORE_HISTOGRAM_BIN_WIDTH).toInt().coerceIn(0, SCORE_HISTOGRAM_BIN_COUNT - 1)
            bins[idx]++
        }
        return bins.toList()
    }

    fun buildPeScoreBins(stocks: List<StockTank>): List<Int> {
        val eligiblePe = filterEligiblePeConstituents(stocks)
        if (eligiblePe.isEmpty()) return emptyList()
        val bins = IntArray(SCORE_HISTOGRAM_BIN_COUNT)
        for (stock in eligiblePe) {
            val sc = (stock.peScore ?: continue).coerceIn(0.0, 100.0)
            val idx = (sc / SCORE_HISTOGRAM_BIN_WIDTH).toInt().coerceIn(0, SCORE_HISTOGRAM_BIN_COUNT - 1)
            bins[idx]++
        }
        return bins.toList()
    }

    /**
     * Exact piecewise-uniform integration of Overflow(H) = max(0, StockScore - H)
     * normalized by (ActiveStocks * (100 - H)) from the 20-bin constituent score histogram.
     */
    fun computeTankLevelFromBins(bins: List<Int>, holeThreshold: Double): Double {
        if (bins.size != SCORE_HISTOGRAM_BIN_COUNT) return 0.0
        val totalActive = bins.sum()
        if (totalActive <= 0) return 0.0
        val h = holeThreshold.coerceIn(0.0, 100.0)
        val maxPerStock = 100.0 - h
        if (maxPerStock <= 0.0001) return 0.0

        var sumOverflow = 0.0
        for (b in 0 until SCORE_HISTOGRAM_BIN_COUNT) {
            val count = bins[b]
            if (count <= 0) continue
            val lo = b * SCORE_HISTOGRAM_BIN_WIDTH
            val hi = lo + SCORE_HISTOGRAM_BIN_WIDTH
            if (hi <= h) continue
            if (lo >= h) {
                val midScore = (lo + hi) * 0.5
                sumOverflow += count * (midScore - h)
            } else {
                val activeFrac = (hi - h) / SCORE_HISTOGRAM_BIN_WIDTH
                val meanAboveHole = (h + hi) * 0.5
                sumOverflow += count * activeFrac * (meanAboveHole - h)
            }
        }
        return roundPct(((sumOverflow / (totalActive * maxPerStock)) * 100.0).coerceIn(0.0, 100.0))
    }

    /**
     * Exact piecewise-uniform count of constituents strictly above Hole Position H from the 20-bin histogram.
     */
    fun computeFlowingCountFromBins(bins: List<Int>, holeThreshold: Double): Int {
        if (bins.size != SCORE_HISTOGRAM_BIN_COUNT) return 0
        val totalActive = bins.sum()
        if (totalActive <= 0) return 0
        val h = holeThreshold.coerceIn(0.0, 100.0)
        if (h <= 0.0) return totalActive
        if (h >= 100.0) return 0

        var flowing = 0.0
        for (b in 0 until SCORE_HISTOGRAM_BIN_COUNT) {
            val count = bins[b]
            if (count <= 0) continue
            val lo = b * SCORE_HISTOGRAM_BIN_WIDTH
            val hi = lo + SCORE_HISTOGRAM_BIN_WIDTH
            if (hi <= h) continue
            if (lo >= h) {
                flowing += count.toDouble()
            } else {
                flowing += count * ((hi - h) / SCORE_HISTOGRAM_BIN_WIDTH)
            }
        }
        return round(flowing).toInt().coerceIn(0, totalActive)
    }

    /**
     * Reconstructs a moment-matched 4-tier constituent density over [0,15), [15,50], (50,85], (85,100]
     * for legacy/remote history points that lack an explicit 20-bin histogram, ensuring dynamic hole
     * math still obeys Overflow(H) / (N * (100 - H)) conservation laws.
     */
    fun simulateTankLevel(
        avgScore: Double,
        tankLevelAt50: Double,
        highPressureCount: Int,
        activeCount: Int,
        h: Double,
        capitulationCount: Int = 0,
        flowingAt50: Int = -1
    ): Double {
        val clampedH = h.coerceIn(0.0, 100.0)
        if (clampedH <= 0.0) return roundPct(avgScore.coerceIn(0.0, 100.0))
        if (clampedH >= 100.0) return 0.0
        if (abs(clampedH - 50.0) < 0.001) return roundPct(tankLevelAt50.coerceIn(0.0, 100.0))

        val syntheticBins = reconstructBinsFromSummaryMoments(
            avgScore = avgScore,
            tankLevelAt50 = tankLevelAt50,
            highPressureCount = highPressureCount,
            capitulationCount = capitulationCount,
            flowingAt50 = flowingAt50,
            activeCount = activeCount
        )
        return computeTankLevelFromBins(syntheticBins, clampedH)
    }

    fun simulateFlowingCount(
        flowingAt50: Int,
        highPressureCount: Int,
        activeCount: Int,
        h: Double,
        avgScore: Double = 50.0,
        tankLevelAt50: Double = 50.0,
        capitulationCount: Int = 0
    ): Int {
        val clampedH = h.coerceIn(0.0, 100.0)
        val act = activeCount.coerceAtLeast(1)
        if (clampedH <= 0.0) return act
        if (clampedH >= 100.0) return 0
        if (abs(clampedH - 50.0) < 0.001) return flowingAt50.coerceIn(0, act)

        val syntheticBins = reconstructBinsFromSummaryMoments(
            avgScore = avgScore,
            tankLevelAt50 = tankLevelAt50,
            highPressureCount = highPressureCount,
            capitulationCount = capitulationCount,
            flowingAt50 = flowingAt50,
            activeCount = act
        )
        return computeFlowingCountFromBins(syntheticBins, clampedH)
    }

    private fun reconstructBinsFromSummaryMoments(
        avgScore: Double,
        tankLevelAt50: Double,
        highPressureCount: Int,
        capitulationCount: Int,
        flowingAt50: Int,
        activeCount: Int
    ): List<Int> {
        val n = activeCount.coerceAtLeast(1)
        val n85 = highPressureCount.coerceIn(0, n)
        val estFlow50 = if (flowingAt50 >= 0) {
            flowingAt50.coerceIn(n85, n)
        } else {
            round(n * (tankLevelAt50 / 100.0).coerceIn(0.0, 1.0) * 1.15).toInt().coerceIn(n85, n)
        }
        val n50to85 = (estFlow50 - n85).coerceAtLeast(0)
        val remBelow50 = (n - estFlow50).coerceAtLeast(0)
        val nCap15 = if (capitulationCount > 0) {
            capitulationCount.coerceIn(0, remBelow50)
        } else {
            val impliedLow = ((50.0 - avgScore.coerceIn(0.0, 100.0)) / 50.0).coerceIn(0.0, 1.0)
            round(remBelow50 * impliedLow * 0.55).toInt().coerceIn(0, remBelow50)
        }
        val n15to50 = (remBelow50 - nCap15).coerceAtLeast(0)

        val bins = IntArray(SCORE_HISTOGRAM_BIN_COUNT)
        distributeAcrossBins(bins, startBin = 0, endBinExclusive = 3, totalCount = nCap15)
        distributeAcrossBins(bins, startBin = 3, endBinExclusive = 10, totalCount = n15to50)
        distributeAcrossBins(bins, startBin = 10, endBinExclusive = 17, totalCount = n50to85)
        distributeAcrossBins(bins, startBin = 17, endBinExclusive = 20, totalCount = n85)
        return bins.toList()
    }

    private fun distributeAcrossBins(
        bins: IntArray,
        startBin: Int,
        endBinExclusive: Int,
        totalCount: Int
    ) {
        val span = (endBinExclusive - startBin).coerceAtLeast(1)
        if (totalCount <= 0) return
        val base = totalCount / span
        var rem = totalCount % span
        for (b in startBin until endBinExclusive) {
            bins[b] += base + if (rem > 0) {
                rem--
                1
            } else 0
        }
    }

    fun simulateHistory(
        history: List<TankHistoryPoint>,
        holeThreshold: Double,
        liveStocks: List<StockTank> = emptyList(),
        liveSessionDate: String? = null,
        targetConstituents: Int = DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS
    ): List<TankHistoryPoint> {
        val h = holeThreshold.coerceIn(0.0, 100.0)
        if (abs(h - 50.0) < 0.001 || history.isEmpty()) return history

        val eligibleLive = if (liveStocks.isNotEmpty()) filterEligibleConstituents(liveStocks) else emptyList()
        val minRequiredLive = max(1, (targetConstituents * 0.90).toInt())
        val hasFullLiveConstituents = eligibleLive.size >= minRequiredLive
        val overheatedLine = overheatedThresholdForHole(h)

        val out = ArrayList<TankHistoryPoint>(history.size)
        var prevLevel = 0.0
        var runningPlateau = 0

        for (i in history.indices) {
            val orig = history[i]
            if (orig.isIncompleteColdStart) {
                prevLevel = 0.0
                runningPlateau = 0
                out.add(orig.copy(tankLevel = 0.0, dailyDelta = 0.0, flowingCount = 0, plateauDays = 0))
                continue
            }
            val isLiveTerminalMatch = hasFullLiveConstituents &&
                ((liveSessionDate != null && orig.date == liveSessionDate) ||
                    (liveSessionDate == null && i == history.lastIndex))

            val simLevel: Double
            val simFlowing: Int
            if (isLiveTerminalMatch) {
                simLevel = roundPct(computeMainTankLevel(eligibleLive, h))
                simFlowing = eligibleLive.count { it.score > h }
            } else if (orig.scoreBins.size == SCORE_HISTOGRAM_BIN_COUNT && orig.scoreBins.sum() > 0) {
                simLevel = computeTankLevelFromBins(orig.scoreBins, h)
                simFlowing = computeFlowingCountFromBins(orig.scoreBins, h)
            } else {
                simLevel = simulateTankLevel(
                    avgScore = orig.avgScore,
                    tankLevelAt50 = orig.tankLevel,
                    highPressureCount = orig.highPressureCount,
                    activeCount = orig.activeCount,
                    h = h,
                    capitulationCount = orig.capitulationCount,
                    flowingAt50 = orig.flowingCount
                )
                simFlowing = simulateFlowingCount(
                    flowingAt50 = orig.flowingCount,
                    highPressureCount = orig.highPressureCount,
                    activeCount = orig.activeCount,
                    h = h,
                    avgScore = orig.avgScore,
                    tankLevelAt50 = orig.tankLevel,
                    capitulationCount = orig.capitulationCount
                )
            }

            val simPeLevel: Double? = when {
                isLiveTerminalMatch && filterEligiblePeConstituents(eligibleLive).isNotEmpty() ->
                    computePeTankLevel(eligibleLive, h)
                orig.peScoreBins.size == SCORE_HISTOGRAM_BIN_COUNT && orig.peScoreBins.sum() > 0 ->
                    computeTankLevelFromBins(orig.peScoreBins, h)
                else -> orig.peTankLevel
            }

            val delta = if (i == 0) 0.0 else roundPct(simLevel - prevLevel)
            prevLevel = simLevel
            runningPlateau = if (simLevel > overheatedLine) runningPlateau + 1 else 0

            out.add(
                orig.copy(
                    tankLevel = simLevel,
                    dailyDelta = delta,
                    flowingCount = simFlowing,
                    plateauDays = runningPlateau,
                    peTankLevel = simPeLevel
                )
            )
        }
        return out
    }

    fun buildSectorSummaries(stocks: List<StockTank>, holeThreshold: Double = 50.0): List<SectorTankSummary> {
        val eligible = filterEligibleConstituents(stocks)
        if (eligible.isEmpty()) return emptyList()
        val h = holeThreshold.coerceIn(0.0, 100.0)
        val totalMarketOverflow = eligible.sumOf { computeOverflowVolume(it.score, h) }
        return eligible.groupBy { it.sector }
            .map { (sector, list) ->
                val avg = list.map { it.score }.average()
                val flowing = list.count { it.score > h }
                val sectorOverflow = list.sumOf { computeOverflowVolume(it.score, h) }
                val share = if (totalMarketOverflow > 0.0) (sectorOverflow / totalMarketOverflow) * 100.0 else 0.0
                SectorTankSummary(
                    sector = sector,
                    stockCount = list.size,
                    flowingCount = flowing,
                    avgScore = avg,
                    totalOverflow = sectorOverflow,
                    overflowSharePct = share
                )
            }
            .sortedByDescending { it.avgScore }
    }

    private fun roundPct(value: Double): Double = round(value * 100.0) / 100.0
}

/**
 * Derives Weekly (ISO week-end) and Monthly (month-end) breadth bars strictly from the
 * underlying daily TankHistoryPoint series without mutating or fabricating daily data.
 */
object TankHistoryAggregator {
    private val ISO_WEEK = WeekFields.ISO

    fun aggregate(
        dailyPoints: List<TankHistoryPoint>,
        mode: ChartAggregation
    ): List<TankHistoryPoint> {
        if (mode == ChartAggregation.DAILY || dailyPoints.size <= 1) return dailyPoints
        val buckets = LinkedHashMap<String, MutableList<TankHistoryPoint>>()
        for (pt in dailyPoints) {
            val key = bucketKey(pt.date, mode)
            buckets.getOrPut(key) { ArrayList(5) }.add(pt)
        }

        val out = ArrayList<TankHistoryPoint>(buckets.size)
        var prevClose: Double? = null
        for ((_, bucket) in buckets) {
            val terminal = bucket.lastOrNull { !it.isIncompleteColdStart } ?: bucket.last()
            val periodDelta = if (prevClose != null && !terminal.isIncompleteColdStart) {
                round((terminal.tankLevel - prevClose) * 100.0) / 100.0
            } else {
                0.0
            }
            val maxPlateau = bucket.maxOf { it.plateauDays }
            val resolvedProvenance = when {
                bucket.all { it.isIncompleteColdStart } -> TankHistoryPoint.PROVENANCE_INCOMPLETE_COLD_START
                bucket.any { it.isReconstructedHistorical } -> TankHistoryPoint.PROVENANCE_RECONSTRUCTED_HISTORICAL
                else -> terminal.provenance
            }
            val peTerminal = bucket.lastOrNull { it.peTankLevel != null }
            val idxPeTerminal = bucket.lastOrNull { it.hasOfficialIndexPe }
            out.add(
                terminal.copy(
                    dailyDelta = periodDelta,
                    plateauDays = maxPlateau,
                    provenance = resolvedProvenance,
                    peTankLevel = peTerminal?.peTankLevel,
                    medianPe = peTerminal?.medianPe,
                    peActiveCount = peTerminal?.peActiveCount ?: terminal.peActiveCount,
                    peScoreBins = peTerminal?.peScoreBins ?: terminal.peScoreBins,
                    indexPe = idxPeTerminal?.indexPe ?: terminal.indexPe,
                    indexPb = idxPeTerminal?.indexPb ?: terminal.indexPb,
                    indexDivYield = idxPeTerminal?.indexDivYield ?: terminal.indexDivYield
                )
            )
            if (!terminal.isIncompleteColdStart) {
                prevClose = terminal.tankLevel
            }
        }
        return out
    }

    private fun bucketKey(isoDate: String, mode: ChartAggregation): String = when (mode) {
        ChartAggregation.DAILY -> isoDate
        ChartAggregation.MONTHLY -> if (isoDate.length >= 7) isoDate.substring(0, 7) else isoDate
        ChartAggregation.WEEKLY -> try {
            val d = LocalDate.parse(isoDate)
            val weekYear = d.get(ISO_WEEK.weekBasedYear())
            val weekNum = d.get(ISO_WEEK.weekOfWeekBasedYear())
            String.format(Locale.US, "%04d-W%02d", weekYear, weekNum)
        } catch (_: Exception) {
            isoDate
        }
    }
}
