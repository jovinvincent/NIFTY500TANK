package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun ForecastAndConsistencyComponent(
    points: List<TimelineChartPoint>,
    isDark: Boolean,
    onGraphClick: () -> Unit,
    onRankScoreClick: () -> Unit = {},
    onSmartRecommendationClick: () -> Unit = {}
) {
    if (points.isEmpty()) return

    var smoothedConsistencyScore by remember { mutableStateOf(0.0) }
    var smoothedExpectedXirr by remember { mutableStateOf(0.0) }
    var actualConsistencyScore by remember { mutableStateOf(0.0) }
    var actualExpectedXirr by remember { mutableStateOf(0.0) }
    var isCalculating by remember { mutableStateOf(true) }

    LaunchedEffect(points) {
        isCalculating = true
        withContext(Dispatchers.Default) {
            val mappedPoints = points.mapNotNull { pt ->
                val dateLong = parseTimelineDate(pt.dateStr)
                if (dateLong > 0) Pair(pt.copy(trend = 0.0, trendScore = 0.0), dateLong) else null
            }.sortedBy { it.second }
            
            if (mappedPoints.isNotEmpty()) {
                val minD = mappedPoints.first().second
                val maxD = mappedPoints.last().second
                
                val totalDays = ((maxD - minD) / (24L * 60 * 60 * 1000L)).coerceAtLeast(1)
                // Target around 120 points to completely eliminate nested calculation lags while maintaining high fidelity
                val stepDays = (totalDays / 120).coerceAtLeast(1)
                val stepMs = stepDays * 24L * 60 * 60 * 1000L

                val sharedDates = mutableListOf<Long>()
                var t = minD
                while (t <= maxD) {
                    sharedDates.add(t)
                    t += stepMs
                }
                if (sharedDates.isEmpty() || sharedDates.last() != maxD) {
                    sharedDates.add(maxD)
                }
                
                val dailyPoints = mutableListOf<Pair<Double, Double>>() // Pair(Score, Xirr)
                var k = 0
                val historyList = mutableListOf<Pair<TimelineChartPoint, Long>>()
                for (evalTime in sharedDates) {
                    while (k < mappedPoints.size && mappedPoints[k].second <= evalTime) {
                        historyList.add(mappedPoints[k])
                        k++
                    }
                    if (historyList.size >= 10) {
                        val (score, xirr) = calculateConsistencyAndForecastMapped(historyList)
                        dailyPoints.add(Pair(score, xirr))
                    }
                }
                
                if (dailyPoints.isNotEmpty()) {
                    val historySize = dailyPoints.size
                    val period = when {
                        historySize >= 200 -> 200
                        historySize >= 100 -> 100
                        historySize >= 50 -> 50
                        historySize >= 20 -> 20
                        historySize >= 15 -> 15
                        historySize >= 7 -> 7
                        historySize >= 5 -> historySize - 1
                        else -> 1
                    }
                    val alpha = 2.0 / (period.toDouble() + 1.0)
                    
                    var emaScore = 0.0
                    var emaXirr = 0.0
                    for (i in dailyPoints.indices) {
                        val (score, xirr) = dailyPoints[i]
                        if (i == 0) {
                            emaScore = score
                            emaXirr = xirr
                        } else {
                            emaScore = alpha * score + (1.0 - alpha) * emaScore
                            emaXirr = alpha * xirr + (1.0 - alpha) * emaXirr
                        }
                    }
                    
                    smoothedConsistencyScore = emaScore
                    smoothedExpectedXirr = emaXirr
                    actualConsistencyScore = dailyPoints.last().first
                    actualExpectedXirr = dailyPoints.last().second
                } else {
                    val (score, xirr) = calculateConsistencyAndForecast(points)
                    smoothedConsistencyScore = score
                    smoothedExpectedXirr = xirr
                    actualConsistencyScore = score
                    actualExpectedXirr = xirr
                }
            } else {
                smoothedConsistencyScore = 0.0
                smoothedExpectedXirr = 0.0
                actualConsistencyScore = 0.0
                actualExpectedXirr = 0.0
            }
        }
        isCalculating = false
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF1E2228) else Color(0xFFFFFFFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C3238) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "Forecast & Consistency",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!isCalculating) {
                        val compositeScore = scaleCompositeScore(
                            smoothedExpectedXirr,
                            actualExpectedXirr,
                            smoothedConsistencyScore,
                            actualConsistencyScore
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF22C55E).copy(alpha = 0.12f),
                            modifier = Modifier.clickable { onRankScoreClick() }
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "(%.2f)", compositeScore),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF22C55E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onSmartRecommendationClick) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF00E676).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "R",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                    IconButton(onClick = onGraphClick) {
                        Icon(Icons.Default.Add, contentDescription = "Forecast Analysis Graph", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isCalculating) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Consistency Score
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .background(if (isDark) Color(0xFF262A30) else Color(0xFFF8F9FA), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Consistency Score", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", smoothedConsistencyScore) + "/100",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = getConsistencyColor(smoothedConsistencyScore)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Smoothed",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // Expected XIRR
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .background(if (isDark) Color(0xFF262A30) else Color(0xFFF8F9FA), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Expected XIRR", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f%%", smoothedExpectedXirr),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = if (smoothedExpectedXirr >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Smoothed",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

fun getConsistencyColor(score: Double): Color {
    return when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> Color(0xFF8BC34A)
        score >= 40 -> Color(0xFFFFC107)
        score >= 20 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

fun calculateExpectedXirrForScheme(schemeName: String): Double {
    val records = NavCache.getRecords(schemeName)
    if (records.isEmpty()) return 0.0
    
    val points = records.map { rec ->
        TimelineChartPoint(rec.dateStr, rec.nav, null, null, 0.0, 0.0)
    }
    
    val mappedPoints = points.mapNotNull { pt ->
        val dateLong = parseTimelineDate(pt.dateStr)
        if (dateLong > 0) Pair(pt, dateLong) else null
    }.sortedBy { it.second }
    
    if (mappedPoints.isEmpty()) return 0.0
    
    val minD = mappedPoints.first().second
    val maxD = mappedPoints.last().second
    
    val totalDays = ((maxD - minD) / (24L * 60 * 60 * 1000L)).coerceAtLeast(1)
    val stepDays = (totalDays / 120).coerceAtLeast(1)
    val stepMs = stepDays * 24L * 60 * 60 * 1000L

    val sharedDates = mutableListOf<Long>()
    var t = minD
    while (t <= maxD) {
        sharedDates.add(t)
        t += stepMs
    }
    if (sharedDates.isEmpty() || sharedDates.last() != maxD) {
        sharedDates.add(maxD)
    }
    
    val dailyPoints = mutableListOf<Double>() // Only need Xirr
    var k = 0
    val historyList = mutableListOf<Pair<TimelineChartPoint, Long>>()
    for (evalTime in sharedDates) {
        while (k < mappedPoints.size && mappedPoints[k].second <= evalTime) {
            historyList.add(mappedPoints[k])
            k++
        }
        if (historyList.size >= 10) {
            val (_, xirr) = calculateConsistencyAndForecastMapped(historyList)
            dailyPoints.add(xirr)
        }
    }
    
    if (dailyPoints.isNotEmpty()) {
        val historySize = dailyPoints.size
        val period = when {
            historySize >= 200 -> 200
            historySize >= 100 -> 100
            historySize >= 50 -> 50
            historySize >= 20 -> 20
            historySize >= 15 -> 15
            historySize >= 7 -> 7
            historySize >= 5 -> historySize - 1
            else -> 1
        }
        val alpha = 2.0 / (period.toDouble() + 1.0)
        
        var emaXirr = 0.0
        for (i in dailyPoints.indices) {
            val xirr = dailyPoints[i]
            if (i == 0) {
                emaXirr = xirr
            } else {
                emaXirr = alpha * xirr + (1.0 - alpha) * emaXirr
            }
        }
        return emaXirr
    } else {
        val (_, xirr) = calculateConsistencyAndForecast(points)
        return xirr
    }
}

fun calculateConsistencyAndForecast(points: List<TimelineChartPoint>): Pair<Double, Double> {
    if (points.isEmpty()) return Pair(0.0, 0.0)
    val mappedPoints = points.mapNotNull { pt ->
        val dateLong = parseTimelineDate(pt.dateStr)
        if (dateLong > 0) Pair(pt.copy(trend = 0.0, trendScore = 0.0), dateLong) else null
    }.sortedBy { it.second }
    return calculateConsistencyAndForecastMapped(mappedPoints)
}

fun calculateConsistencyAndForecastMapped(mappedPoints: List<Pair<TimelineChartPoint, Long>>): Pair<Double, Double> {
    if (mappedPoints.isEmpty()) return Pair(0.0, 0.0)

    val maxDate = mappedPoints.last().second
    val minDate = mappedPoints.first().second
    val daysAvailable = (maxDate - minDate) / (24 * 60 * 60 * 1000).toDouble()
    val yearsAvailable = daysAvailable / 365.25
    
    if (daysAvailable < 7.0 || mappedPoints.size < 5) {
        return Pair(50.0, 10.0) // Fallback for extremely new funds
    }

    // --- 1. Extract Fundamental Time Series Data ---
    var sumDailyReturns = 0.0
    var dailyReturnsCount = 0
    for (i in 1 until mappedPoints.size) {
        val prevNav = mappedPoints[i-1].first.nav
        val currNav = mappedPoints[i].first.nav
        if (prevNav > 0) {
            val dailyRet = (currNav - prevNav) / prevNav
            sumDailyReturns += dailyRet
            dailyReturnsCount++
        }
    }
    val dailyMean = if (dailyReturnsCount > 0) sumDailyReturns / dailyReturnsCount else 0.0

    var maxDrawdown = 0.0
    var peakNav = mappedPoints.first().first.nav
    
    var upDaysCount = 0
    var downDaysCount = 0
    var sumUpReturns = 0.0
    var sumDownReturns = 0.0

    var currentSwing = 0.0
    var sumSwingSize = 0.0
    var swingCount = 0

    var varianceSum = 0.0
    var skewnessSum = 0.0
    var kurtosisSum = 0.0

    var downsideVarianceSum = 0.0
    var downsideCount = 0

    // For SIP Simulation
    var sipTotalInvested = 0.0
    var sipUnits = 0.0
    val sipInstallment = 1000.0
    val sipIntervalMs = 30L * 24 * 60 * 60 * 1000L
    var nextSipDate = minDate

    // For Worst-case Weekly SIP
    var currentWeekId = -1L
    var highestNavInWeek = -1.0
    var totalWorstWeeklyUnits = 0.0
    var totalWorstWeeklyInvested = 0.0
    val weeklyInstallment = 1000.0

    // For Worst-case Monthly SIP
    var currentMonthId = -1L
    var highestNavInMonth = -1.0
    var totalWorstMonthlyUnits = 0.0
    var totalWorstMonthlyInvested = 0.0
    val monthlyInstallment = 5000.0

    // For Rolling Weekly SIP (Block-based)
    fun computeAverageRollingWeeklySipXirrMapped(localMappedPoints: List<Pair<TimelineChartPoint, Long>>, blockDurationDays: Int): Double {
        val blockDurationMs = blockDurationDays * 24L * 60L * 60L * 1000L
        if (localMappedPoints.isEmpty()) return Double.NaN
        val maxHistoryDate = localMappedPoints.last().second
        if (maxHistoryDate - localMappedPoints.first().second < blockDurationMs) return Double.NaN
        
        var sumXirr = 0.0
        var totalBlocks = 0
        
        val stepMs = 60L * 24L * 60L * 60L * 1000L
        var startDate = localMappedPoints.first().second
        var startIndex = 0
        
        while (startDate + blockDurationMs <= maxHistoryDate) {
            val endDateTarget = startDate + blockDurationMs
            
            while (startIndex < localMappedPoints.size && localMappedPoints[startIndex].second < startDate) {
                startIndex++
            }
            
            var endIndex = startIndex
            while (endIndex < localMappedPoints.size && localMappedPoints[endIndex].second <= endDateTarget) {
                endIndex++
            }
            
            if (startIndex < endIndex) {
                var totalInvested = 0.0
                var totalUnits = 0.0
                val cashflows = mutableListOf<Pair<Long, Double>>()
                
                var nextSipDate = startDate
                for (i in startIndex until endIndex) {
                    val pt = localMappedPoints[i]
                    if (pt.second >= nextSipDate) {
                        val amount = 1000.0
                        totalInvested += amount
                        totalUnits += amount / pt.first.nav
                        cashflows.add(Pair(pt.second, -amount))
                        nextSipDate += 7L * 24L * 60L * 60L * 1000L // Weekly SIP
                    }
                }
                
                if (totalInvested > 0) {
                    val latestNav = localMappedPoints[endIndex - 1].first.nav
                    val latestDate = localMappedPoints[endIndex - 1].second
                    val currentValue = totalUnits * latestNav
                    cashflows.add(Pair(latestDate, currentValue))
                    val xirr = calculateXIRR(cashflows) * 100.0
                    if (!xirr.isNaN()) {
                        sumXirr += xirr
                        totalBlocks++
                    }
                }
            }
            startDate += stepMs
        }
        
        return if (totalBlocks > 0) sumXirr / totalBlocks else Double.NaN
    }

    for (i in 0 until mappedPoints.size) {
        val ptPair = mappedPoints[i]
        val dateMs = ptPair.second
        val nav = ptPair.first.nav

        // --- Standard Monthly SIP ---
        if (dateMs >= nextSipDate) {
            if (nav > 0.0) {
                sipUnits += sipInstallment / nav
                sipTotalInvested += sipInstallment
            }
            nextSipDate += sipIntervalMs
        }

        // --- Worst-case Weekly SIP ---
        val weekId = dateMs / (7L * 24 * 60 * 60 * 1000L)
        if (weekId != currentWeekId) {
            if (currentWeekId != -1L && highestNavInWeek > 0.0) {
                totalWorstWeeklyUnits += weeklyInstallment / highestNavInWeek
                totalWorstWeeklyInvested += weeklyInstallment
            }
            currentWeekId = weekId
            highestNavInWeek = nav
        } else {
            if (nav > highestNavInWeek) {
                highestNavInWeek = nav
            }
        }

        // --- Worst-case Monthly SIP ---
        val monthId = dateMs / (30L * 24 * 60 * 60 * 1000L)
        if (monthId != currentMonthId) {
            if (currentMonthId != -1L && highestNavInMonth > 0.0) {
                totalWorstMonthlyUnits += monthlyInstallment / highestNavInMonth
                totalWorstMonthlyInvested += monthlyInstallment
            }
            currentMonthId = monthId
            highestNavInMonth = nav
        } else {
            if (nav > highestNavInMonth) {
                highestNavInMonth = nav
            }
        }

        // --- Drawdown and Daily Return stats ---
        if (nav > peakNav) peakNav = nav
        if (peakNav > 0.0) {
            val dd = (peakNav - nav) / peakNav
            if (dd > maxDrawdown) maxDrawdown = dd
        }

        if (i > 0) {
            val prevNav = mappedPoints[i-1].first.nav
            if (prevNav > 0.0) {
                val dailyRet = (nav - prevNav) / prevNav

                // Variance, Skewness, Kurtosis sums
                val diff = dailyRet - dailyMean
                val diff2 = diff * diff
                varianceSum += diff2
                skewnessSum += diff2 * diff
                kurtosisSum += diff2 * diff2

                if (dailyRet > 0.0) {
                    upDaysCount++
                    sumUpReturns += dailyRet
                    if (currentSwing < 0.0) {
                        sumSwingSize += Math.abs(currentSwing)
                        swingCount++
                        currentSwing = 0.0
                    }
                    currentSwing += dailyRet
                } else if (dailyRet < 0.0) {
                    downsideVarianceSum += diff2
                    downsideCount++

                    downDaysCount++
                    sumDownReturns += dailyRet
                    if (currentSwing > 0.0) {
                        sumSwingSize += Math.abs(currentSwing)
                        swingCount++
                        currentSwing = 0.0
                    }
                    currentSwing += dailyRet
                }
            }
        }
    }

    // Flush any pending swings
    if (currentSwing != 0.0) {
        sumSwingSize += Math.abs(currentSwing)
        swingCount++
    }

    // Flush pending weekly worst SIP
    if (currentWeekId != -1L && highestNavInWeek > 0.0) {
        totalWorstWeeklyUnits += weeklyInstallment / highestNavInWeek
        totalWorstWeeklyInvested += weeklyInstallment
    }

    // Flush pending monthly worst SIP
    if (currentMonthId != -1L && highestNavInMonth > 0.0) {
        totalWorstMonthlyUnits += monthlyInstallment / highestNavInMonth
        totalWorstMonthlyInvested += monthlyInstallment
    }

    val n = dailyReturnsCount.toDouble()
    val dailyVariance = if (n > 1) varianceSum / (n - 1) else 0.0
    val dailyVol = Math.sqrt(dailyVariance)
    val annualizedVol = Math.max(dailyVol * Math.sqrt(252.0), 0.001)
    
    val skewness = if (n > 2 && dailyVol > 0) (n * skewnessSum) / ((n - 1) * (n - 2) * Math.pow(dailyVol, 3.0)) else 0.0
    val kurtosis = if (n > 3 && dailyVol > 0) (n * (n + 1) * kurtosisSum) / ((n - 1) * (n - 2) * (n - 3) * Math.pow(dailyVol, 4.0)) - (3.0 * Math.pow(n - 1, 2.0) / ((n - 2) * (n - 3))) else 0.0

    // --- 3. Typical Up/Down & Swing Behavior ---
    val avgUpReturn = if (upDaysCount > 0) sumUpReturns / upDaysCount else 0.0
    val avgDownReturn = if (downDaysCount > 0) sumDownReturns / downDaysCount else 0.0
    val upDownRatio = if (Math.abs(avgDownReturn) > 0.000001) avgUpReturn / Math.abs(avgDownReturn) else 1.0
    
    val avgSwingSize = if (swingCount > 0) sumSwingSize / swingCount else 0.0

    val downsideVariance = if (downsideCount > 1) downsideVarianceSum / (downsideCount - 1) else 0.0
    val downsideVol = Math.max(Math.sqrt(downsideVariance) * Math.sqrt(252.0), 0.001)

    // --- 4. Trailing Returns & SIP Simulation ---
    val firstNav = mappedPoints.first().first.nav
    val lastNav = mappedPoints.last().first.nav
    val absoluteReturn = if (firstNav > 0.0) (lastNav - firstNav) / firstNav else 0.0
    
    val pointToPointCagr = if (yearsAvailable > 0.05) {
        (Math.pow(1.0 + absoluteReturn, 1.0 / yearsAvailable) - 1.0) * 100.0
    } else {
        absoluteReturn * (365.25 / Math.max(daysAvailable, 1.0)) * 100.0
    }
    
    val sipCurrentValue = sipUnits * lastNav
    val sipAbsoluteReturn = if (sipTotalInvested > 0.0) (sipCurrentValue - sipTotalInvested) / sipTotalInvested else 0.0
    val avgSipDurationYears = yearsAvailable / 2.0
    val sipAnnualizedReturn = if (avgSipDurationYears > 0.05) {
        (Math.pow(1.0 + sipAbsoluteReturn, 1.0 / avgSipDurationYears) - 1.0) * 100.0
    } else {
        pointToPointCagr
    }

    val finalValueWorstWeekly = totalWorstWeeklyUnits * lastNav
    val worstWeeklySipAbsoluteReturn = if (totalWorstWeeklyInvested > 0.0) {
        (finalValueWorstWeekly - totalWorstWeeklyInvested) / totalWorstWeeklyInvested
    } else {
        0.0
    }
    val worstWeeklySipXirr = if (avgSipDurationYears > 0.05) {
        (Math.pow(Math.max(1.0 + worstWeeklySipAbsoluteReturn, 0.0001), 1.0 / avgSipDurationYears) - 1.0) * 100.0
    } else {
        pointToPointCagr
    }

    val finalValueWorstMonthly = totalWorstMonthlyUnits * lastNav
    val worstMonthlySipAbsoluteReturn = if (totalWorstMonthlyInvested > 0.0) {
        (finalValueWorstMonthly - totalWorstMonthlyInvested) / totalWorstMonthlyInvested
    } else {
        0.0
    }
    val worstMonthlySipXirr = if (avgSipDurationYears > 0.05) {
        (Math.pow(Math.max(1.0 + worstMonthlySipAbsoluteReturn, 0.0001), 1.0 / avgSipDurationYears) - 1.0) * 100.0
    } else {
        pointToPointCagr
    }

    // --- 5. Rolling Returns ---
    fun getRollingReturnCagr(days: Int): Double {
        val ms = days * 24L * 60 * 60 * 1000L
        if (maxDate - minDate < ms) return pointToPointCagr
        val startNavForRoll = mappedPoints.lastOrNull { it.second <= maxDate - ms }?.first?.nav ?: return pointToPointCagr
        if (startNavForRoll <= 0.0) return pointToPointCagr
        val ret = (lastNav - startNavForRoll) / startNavForRoll
        val rawAnn = (Math.pow(Math.max(1.0 + ret, 0.0001), 365.25 / days) - 1.0) * 100.0
        // Ground short-period annualized noise towards long-term inception CAGR:
        // A 30-day or 60-day annualized rate can otherwise yield unrealistic 40-50% numbers on short blips.
        val horizonConfidence = (days.toDouble() / 365.25).coerceIn(0.20, 1.0)
        return (rawAnn * horizonConfidence + pointToPointCagr * (1.0 - horizonConfidence)).coerceIn(-25.0, 35.0)
    }
    
    val roll1M = getRollingReturnCagr(30)
    val roll2M = getRollingReturnCagr(60)
    val roll3M = getRollingReturnCagr(90)
    val roll6M = getRollingReturnCagr(180)
    val roll1Y = getRollingReturnCagr(365)

    // Synthesize a combined observed return based on available history with continuous weights (no step jumps)
    // We increase the weightage of stable 2-month, 3-month, and 6-month rolling returns as requested,
    // blending them dynamically alongside the 1-month and 1-year returns.
    val blendedRoll = if (yearsAvailable <= 0.25) {
        val w = (yearsAvailable / 0.25).coerceIn(0.0, 1.0)
        val weight1M = 0.35 - 0.10 * w
        val weight2M = 0.35 - 0.05 * w
        val weight3M = 0.20 + 0.10 * w
        val weight6M = 0.10 + 0.05 * w
        val weight1Y = 0.0
        roll1M * weight1M + roll2M * weight2M + roll3M * weight3M + roll6M * weight6M + roll1Y * weight1Y
    } else if (yearsAvailable <= 0.5) {
        val w = ((yearsAvailable - 0.25) / 0.25).coerceIn(0.0, 1.0)
        val weight1M = 0.25 - 0.05 * w
        val weight2M = 0.30 - 0.05 * w
        val weight3M = 0.25 - 0.05 * w
        val weight6M = 0.20 + 0.10 * w
        val weight1Y = 0.05 * w
        roll1M * weight1M + roll2M * weight2M + roll3M * weight3M + roll6M * weight6M + roll1Y * weight1Y
    } else {
        val w = ((yearsAvailable - 0.5) / 1.5).coerceIn(0.0, 1.0)
        val weight1M = 0.20 - 0.05 * w
        val weight2M = 0.25 - 0.10 * w
        val weight3M = 0.20 - 0.05 * w
        val weight6M = 0.30 - 0.10 * w
        val weight1Y = 0.05 + 0.30 * w
        roll1M * weight1M + roll2M * weight2M + roll3M * weight3M + roll6M * weight6M + roll1Y * weight1Y
    }
    
    val weightP1 = 0.6 - 0.2 * Math.min(yearsAvailable, 1.0)
    val weightP2 = 0.2 + 0.1 * Math.min(yearsAvailable, 1.0)
    val weightP3 = 1.0 - weightP1 - weightP2
    
    // We blend the standard monthly SIP return, the worst-case weekly SIP return, and the worst-case monthly SIP return (XIRR)
    // to ensure highly realistic, robust, and conservative expected return estimates.
    // The worst-case monthly SIP has been given a prominent 40% weightage to model realistic, conservative investment scenarios.
    val blendedSipReturn = (sipAnnualizedReturn * 0.3) + (worstWeeklySipXirr * 0.3) + (worstMonthlySipXirr * 0.4)
    
    val blendedObservedReturn = (pointToPointCagr * weightP1) + (blendedSipReturn * weightP2) + (blendedRoll * weightP3)

    // --- 6. Momentum, Moving Average Data & Trend History ---
    val recentPoints = mappedPoints.takeLast(90) // Last ~3 months
    val trendScores = recentPoints.map { it.first.trendScore }.filter { !it.isNaN() && it != 0.0 }
    val avgTrendScore = if (trendScores.isNotEmpty()) trendScores.average() else 50.0
    
    // Trend stability: variance of trend score implies choppiness
    val trendVariance = if (trendScores.size > 1) trendScores.map { Math.pow(it - avgTrendScore, 2.0) }.sum() / (trendScores.size - 1) else 0.0
    val trendVol = Math.sqrt(trendVariance)
    
    // Robust Momentum Indicator (smoothly blended rolling returns based on available history to prevent jumps)
    val momentumRaw = if (yearsAvailable <= 0.25) {
        val w = (yearsAvailable / 0.25).coerceIn(0.0, 1.0)
        (roll1M - roll3M) * w
    } else if (yearsAvailable <= 0.5) {
        val w = ((yearsAvailable - 0.25) / 0.25).coerceIn(0.0, 1.0)
        (roll1M - roll3M) * (1.0 - w) + (roll3M - roll6M) * w
    } else {
        roll3M - roll6M
    }

    // --- 7. Bayesian Updating Model & Confidence/Time factor ---
    // Instead of a rigid 3-year baseline, we use a more realistic 1.5-year (18 months) threshold for 100% confidence.
    val rawTimeFactor = Math.min(yearsAvailable / 1.5, 1.0)
    
    // Drawdown and Recovery Recognition
    val currentDrawdown = if (peakNav > 0.0) (peakNav - lastNav) / peakNav else 0.0
    val recoveryRatio = if (maxDrawdown > 0.02) {
        (1.0 - currentDrawdown / maxDrawdown).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    
    // Smooth resilience boost without cliff jumps: scales smoothly with depth of drawdown and degree of recovery
    val cycleResilienceBoost = (maxDrawdown * recoveryRatio * 0.5).coerceIn(0.0, 0.15)
    val timeFactor = Math.min(rawTimeFactor + cycleResilienceBoost, 1.0)
    
    // Prior Volatility Regularization (Bayesian Shrinkage for Volatility of young funds)
    val priorVol = 0.15 // 15% standard annualized volatility for typical mutual funds
    val adjustedVol = annualizedVol * timeFactor + priorVol * (1.0 - timeFactor)
    val adjustedDownsideVol = downsideVol * timeFactor + priorVol * (1.0 - timeFactor)
    
    // Prior Beliefs: Anchor to the fund's actual lifetime CAGR as history grows,
    // blending with a standard market baseline (12%) for nascent funds without history
    val fundLifetimeAnchor = pointToPointCagr
    val marketBaseline = 12.0
    val priorMean = (fundLifetimeAnchor * timeFactor) + (marketBaseline * (1.0 - timeFactor))
    val priorMeanVariance = Math.pow(8.0, 2.0) // 8% standard deviation
    
    // Observed variance is modulated by time factor to reflect history confidence and regularized volatility
    val observedMeanVariance = Math.pow(adjustedVol * 100.0, 2.0) / Math.max(timeFactor, 0.05)
    
    val weightPrior = 1.0 / priorMeanVariance
    val weightObserved = 1.0 / observedMeanVariance
    
    val bayesianExpectedReturn = (priorMean * weightPrior + blendedObservedReturn * weightObserved) / (weightPrior + weightObserved)

    // --- 8. Differential Alpha ---
    // Approximating alpha as outperformance over expected CAPM return
    val assumedMarketVol = 0.15
    val betaProxy = (adjustedVol / assumedMarketVol).coerceIn(0.5, 2.0)
    val assumedMarketReturn = 12.0
    val expectedCAPMReturn = 6.0 + betaProxy * (assumedMarketReturn - 6.0) // Risk-free = 6%
    val differentialAlpha = bayesianExpectedReturn - expectedCAPMReturn

    // --- 9. Dynamic Expected XIRR Calculation & Real-World Reality Grounding ---
    // Adjust based on Differential Alpha, Trend, and Momentum (bounded within realistic economic limits)
    val alphaContribution = (differentialAlpha * 0.10 * timeFactor).coerceIn(-1.5, 1.5)
    val trendDrift = (((avgTrendScore - 50.0) / 50.0) * 1.0 * timeFactor).coerceIn(-1.0, 1.0)
    val momentumDrift = (momentumRaw * 0.03).coerceIn(-1.0, 1.0) * timeFactor
    
    // Shrink skewness towards 0 for young funds to prevent single-day outlier jumps
    val stableSkewness = skewness * timeFactor
    val stableKurtosis = kurtosis * timeFactor
    val skewDrift = (stableSkewness.coerceIn(-1.0, 1.0) * 0.5 * timeFactor).coerceIn(-0.5, 0.5)
    
    val dynamicModifier = alphaContribution + trendDrift + momentumDrift + skewDrift
    
    // Volatility drag: geometric return is lower than arithmetic return by approx Var/2
    val dragMitigation = (1.0 - 0.5 * recoveryRatio * (maxDrawdown / 0.10).coerceIn(0.0, 1.0)).coerceIn(0.5, 1.0)
    val volatilityDrag = (Math.pow(adjustedVol, 2.0) / 2.0 * 100.0) * timeFactor * dragMitigation
    
    val rawCalculatedXirr = (bayesianExpectedReturn + dynamicModifier - volatilityDrag)

    // Real-World Performance Anchor:
    // Blends the fund's actual point-to-point annualized inception return (pointToPointCagr)
    // and its actual simulated lifetime SIP performance (blendedSipReturn).
    // This represents the fund's demonstrated real-world performance track record.
    val rawRealWorldBaseline = (pointToPointCagr * 0.60) + (blendedSipReturn * 0.40)
    // For young funds (<1.5 years), blend smoothly with the market prior baseline (12%)
    // so noise in short-term data doesn't artificially pull expectations away from reality.
    val effectiveBaseline = (rawRealWorldBaseline * timeFactor) + (12.0 * (1.0 - timeFactor))

    // Apply real-world performance discipline (downgrade weighting):
    // If the unconstrained model expects significantly more return than the fund has actually
    // delivered since inception, apply a downgrade weight that scales with the fund's history.
    var finalExpectedXirr = if (rawCalculatedXirr > effectiveBaseline) {
        val excess = rawCalculatedXirr - effectiveBaseline
        // Downgrade weight: for funds with established track records, up to 80-85% of weight anchors to real performance,
        // allowing only a disciplined, realistic fraction of excess upside drift.
        val downgradeWeight = (0.40 + 0.45 * timeFactor).coerceIn(0.40, 0.85)
        effectiveBaseline + excess * (1.0 - downgradeWeight)
    } else {
        val deficit = effectiveBaseline - rawCalculatedXirr
        // Similarly ground downside drops during temporary market corrections
        val stabilizationWeight = (0.40 + 0.40 * timeFactor).coerceIn(0.40, 0.80)
        effectiveBaseline - deficit * (1.0 - stabilizationWeight)
    }

    // --- 10. Dynamic Consistency Score Calculation ---
    // Base Bayesian Win Rate (Rolling 1M)
    fun getRollingWinRate(days: Int): Pair<Int, Int> {
        val ms = days * 24L * 60 * 60 * 1000L
        var wins = 0; var total = 0
        for (i in mappedPoints.indices) {
            val startPt = mappedPoints[i]
            var j = i + 1
            while (j < mappedPoints.size && mappedPoints[j].second - startPt.second < ms) j++
            if (j < mappedPoints.size) {
                if (mappedPoints[j].first.nav > startPt.first.nav) wins++
                total++
            }
        }
        return Pair(wins, total)
    }

    val (wins1M, total1M) = getRollingWinRate(30)
    
    // Prior for 1M win rate is a fixed strong baseline (~60%) so new funds shrink gracefully
    val priorWins = 60.0; val priorTotal = 100.0
    val posteriorWinRate = (priorWins + wins1M) / (priorTotal + total1M)
    // Scale win rate score dynamically: Map 45%..65% range to 0..100
    val winRateScore = (((posteriorWinRate - 0.45) / 0.20) * 100.0).coerceIn(0.0, 100.0)

    // Up/Down Capture Profile Score: Map 0.8..1.2 range to 0..100
    val upDownScore = (((upDownRatio - 0.8) / 0.4) * 100.0).coerceIn(0.0, 100.0)
    
    // Risk-Adjusted Consistency (Sortino-like, using stable bayesian return)
    val riskMeasure = if (adjustedDownsideVol > 0.01) adjustedDownsideVol * 100.0 else adjustedVol * 100.0
    val excessRet = (bayesianExpectedReturn - 6.0)
    val sortinoRatio = excessRet / riskMeasure
    // Make the sigmoid steeper and shift midpoint to 0.4 for a highly dynamic, spread-out metric
    val riskAdjustedScore = 100.0 / (1.0 + Math.exp(-5.0 * (sortinoRatio - 0.4)))
    
    // Drawdown, Swing, and Kurtosis Penalties (Dampened by timeFactor to prevent short history distortion, mitigated by recovery)
    val ddMitigation = (1.0 - 0.4 * recoveryRatio).coerceIn(0.6, 1.0)
    val ddPenalty = (maxDrawdown * 100.0 * ddMitigation).coerceIn(0.0, 30.0) * timeFactor
    val swingPenalty = (avgSwingSize * 500.0).coerceIn(0.0, 15.0) * timeFactor
    val kurtosisPenalty = (stableKurtosis * 1.5).coerceIn(0.0, 10.0) * timeFactor
    
    // Trend Stability Score
    val trendStabilityScore = 100.0 - (trendVol.coerceIn(0.0, 50.0) * 1.5)
    
    // Final Composition (Weights adapt dynamically with timeFactor and sum to exactly 1.0)
    val weightWinRate = 0.2 + 0.3 * timeFactor  // 0.2 to 0.5
    val weightRiskAdj = 0.2 + 0.2 * timeFactor  // 0.2 to 0.4
    val weightUpDown = 0.4 - 0.3 * timeFactor   // 0.4 to 0.1
    val weightTrend = 0.2 - 0.2 * timeFactor    // 0.2 to 0.0
    
    var rawConsistency = (winRateScore * weightWinRate) + 
                         (riskAdjustedScore * weightRiskAdj) + 
                         (upDownScore * weightUpDown) + 
                         (trendStabilityScore * weightTrend)
                         
    rawConsistency -= (ddPenalty + swingPenalty + kurtosisPenalty)
    
    // Bayesian shrinkage for consistency (Prior is 50, shrinks young funds towards middle)
    val finalConsistencyScore = (rawConsistency * timeFactor) + (50.0 * (1.0 - timeFactor))

    return Pair(finalConsistencyScore.coerceIn(0.0, 100.0), finalExpectedXirr)
}

fun scaleCompositeScore(
    smoothedXirr: Double,
    latestXirr: Double,
    smoothedConsistency: Double,
    latestConsistency: Double
): Double {
    val raw = 0.40 * smoothedXirr + 0.25 * latestXirr + 0.20 * smoothedConsistency + 0.15 * latestConsistency
    return (raw * 2.2 + 10.0).coerceIn(0.0, 100.0)
}

fun calculateCompositeRankScore(schemeName: String): Double {
    val records = NavCache.getRecords(schemeName)
    if (records.isEmpty()) return 0.0
    
    val points = records.map { rec ->
        TimelineChartPoint(rec.dateStr, rec.nav, null, null, 0.0, 0.0)
    }
    
    val mappedPoints = points.mapNotNull { pt ->
        val dateLong = parseTimelineDate(pt.dateStr)
        if (dateLong > 0) Pair(pt, dateLong) else null
    }.sortedBy { it.second }
    
    if (mappedPoints.size < 5) return 0.0
    
    val (score, xirr) = calculateConsistencyAndForecastMapped(mappedPoints)
    return scaleCompositeScore(xirr, xirr, score, score)
}

object FundRankCache {
    var cachedRanks: Map<String, Int> = emptyMap()
    var lastNavSignature: String = ""

    @Synchronized
    fun getOrComputeRanks(items: List<FundUiItem>): Map<String, Int> {
        val signature = items.joinToString("|") { "${it.fund.schemeName}:${it.fund.navDate}:${it.fund.navValue}" }
        if (cachedRanks.isNotEmpty() && signature == lastNavSignature) {
            return cachedRanks
        }
        
        val scores = items.map { item ->
            item.fund.schemeName to calculateCompositeRankScore(item.fund.schemeName)
        }.sortedByDescending { it.second }
        
        val newRanks = mutableMapOf<String, Int>()
        scores.forEachIndexed { index, pair ->
            newRanks[pair.first] = index + 1
        }
        
        if (newRanks.isNotEmpty()) {
            cachedRanks = newRanks
            lastNavSignature = signature
        }
        return if (cachedRanks.isNotEmpty()) cachedRanks else newRanks
    }
}

