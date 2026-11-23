package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun RallyCyclesComponent(points: List<TimelineChartPoint>, isDark: Boolean) {
    val cycles = findRallyCycles(points, minRisePct = 0.0, topN = 3)
    val topOverallGains = getTopDailyMoves(points, isRise = true)

    CycleSection(
        isDark = isDark,
        title = "Rally / Bull Cycles",
        isBull = true,
        cycles = cycles.mapIndexed { index, cycle ->
            val statusLabel = if (cycle.isRetraced) "✓ Closed" else "⚠ Open · ${cycle.totalDays}d"
            val statusKind = if (cycle.isRetraced) "done" else "open"
            
            val days1 = cycle.riseDays.toInt()
            val days2 = cycle.retracementDays?.toInt() ?: (cycle.totalDays - cycle.riseDays).toInt()
            
            val topDatasetMove = topOverallGains.getOrNull(index)
            val topDataset = topDatasetMove?.changePct ?: 0.0
            val topDatasetDate = topDatasetMove?.dateStr ?: ""
            
            val topCycleMove = cycle.top3Gains.getOrNull(0)
            val topCycle = topCycleMove?.changePct ?: 0.0
            val topCycleDate = topCycleMove?.dateStr ?: ""

            val d1 = cycle.troughDateStr
            val d2 = cycle.peakDateStr
            val d3 = if (cycle.isRetraced) cycle.retracementDateStr ?: "" else points.last().dateStr

            CycleCardData(
                rank = index + 1,
                pct = cycle.maxRisePct,
                sign = "+",
                statusLabel = statusLabel,
                statusKind = statusKind,
                date1 = formatDateStrShort(d1),
                date2 = formatDateStrShort(d2),
                date3 = formatDateStrShort(d3),
                days1 = days1,
                days2 = days2,
                datasetPct = topDataset,
                datasetDate = formatDateStrShort(topDatasetDate),
                thisCyclePct = topCycle,
                thisCycleDate = formatDateStrShort(topCycleDate)
            )
        }
    )
}

@Composable
fun DrawdownCyclesComponent(points: List<TimelineChartPoint>, isDark: Boolean) {
    val cycles = findDrawdownCycles(points, minFallPct = 0.0, topN = 3)
    val topOverallLosses = getTopDailyMoves(points, isRise = false)

    CycleSection(
        isDark = isDark,
        title = "Drawdown Cycles",
        isBull = false,
        cycles = cycles.mapIndexed { index, cycle ->
            val statusLabel = if (cycle.isRecovered) "✓ Closed" else "⚠ Open · ${cycle.totalDays}d"
            val statusKind = if (cycle.isRecovered) "done" else "open"
            
            val days1 = cycle.fallDays.toInt()
            val days2 = cycle.recoveryDays?.toInt() ?: (cycle.totalDays - cycle.fallDays).toInt()
            
            val topDatasetMove = topOverallLosses.getOrNull(index)
            val topDataset = topDatasetMove?.changePct ?: 0.0
            val topDatasetDate = topDatasetMove?.dateStr ?: ""
            
            val topCycleMove = cycle.top3Losses.getOrNull(0)
            val topCycle = topCycleMove?.changePct ?: 0.0
            val topCycleDate = topCycleMove?.dateStr ?: ""

            val d1 = cycle.peakDateStr
            val d2 = cycle.troughDateStr
            val d3 = if (cycle.isRecovered) cycle.recoveryDateStr ?: "" else points.last().dateStr

            CycleCardData(
                rank = index + 1,
                pct = cycle.maxFallPct,
                sign = "-",
                statusLabel = statusLabel,
                statusKind = statusKind,
                date1 = formatDateStrShort(d1),
                date2 = formatDateStrShort(d2),
                date3 = formatDateStrShort(d3),
                days1 = days1,
                days2 = days2,
                datasetPct = topDataset,
                datasetDate = formatDateStrShort(topDatasetDate),
                thisCyclePct = topCycle,
                thisCycleDate = formatDateStrShort(topCycleDate)
            )
        }
    )
}

private fun formatDateStrShort(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    val parts = dateStr.split("-")
    if (parts.size == 3) {
        // If it starts with year like 2025-01-03 or 2025-Jan-03
        if (parts[0].length == 4) {
            val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val month = parts[1].toIntOrNull()
            if (month != null && month in 1..12) {
                return "${parts[2]}-${monthNames[month - 1]}-${parts[0]}"
            }
            return "${parts[2]}-${parts[1]}-${parts[0]}"
        }
        // If it's already DD-MMM-YYYY like 03-Jan-2025
        return dateStr
    }
    return dateStr
}

private fun getTopDailyMoves(points: List<TimelineChartPoint>, isRise: Boolean): List<DailyMove> {
    val overallMoves = mutableListOf<DailyMove>()
    for (i in 1 until points.size) {
        val prevNav = points[i-1].nav
        val currNav = points[i].nav
        if (prevNav > 0.0) {
            val pctChange = (currNav - prevNav) / prevNav * 100.0
            overallMoves.add(DailyMove(pctChange, points[i].dateStr))
        }
    }
    return if (isRise) {
        overallMoves.filter { it.changePct > 0 }.sortedByDescending { it.changePct }.take(3)
    } else {
        overallMoves.filter { it.changePct < 0 }.sortedBy { it.changePct }.take(3)
    }
}

data class CycleCardData(
    val rank: Int,
    val pct: Double,
    val sign: String,
    val statusLabel: String,
    val statusKind: String,
    val date1: String,
    val date2: String,
    val date3: String,
    val days1: Int,
    val days2: Int,
    val datasetPct: Double,
    val datasetDate: String,
    val thisCyclePct: Double,
    val thisCycleDate: String
)

@Composable
fun CycleSection(
    isDark: Boolean,
    title: String,
    isBull: Boolean,
    cycles: List<CycleCardData>
) {
    val bgColor = if (isDark) Color(0xFF141A1C) else Color(0xFFF0F4F5)
    val lineCol = if (isDark) Color(0xFF1F2A2D) else Color(0xFFD1D9E0)
    val textCol = if (isDark) Color(0xFFE7EEF0) else Color(0xFF101518)
    val dimCol = if (isDark) Color(0xFF5D6C70) else Color(0xFF8A9AA0)
    
    val green = Color(0xFF4ADE80)
    val greenDim = if (isDark) Color(0xFF1F3A2C) else Color(0xFFE6F4EA)
    val red = Color(0xFFF87171)
    val redDim = if (isDark) Color(0xFF3A1F1F) else Color(0xFFFCE8E8)
    
    val icoBg = if (isBull) greenDim else redDim
    val icoFg = if (isBull) green else red

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(bgColor, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(icoBg, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBull) {
                        TrendingUpIcon(modifier = Modifier.size(16.dp), color = icoFg)
                    } else {
                        TrendingDownIcon(modifier = Modifier.size(16.dp), color = icoFg)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(if (isDark) Color(0xFF1A2326) else Color.White, RoundedCornerShape(99.dp))
                        .border(1.dp, lineCol, RoundedCornerShape(99.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Top 3", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = dimCol)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cycles.forEach { cycle ->
                    Box(modifier = Modifier.weight(1f)) {
                        CycleCard(cycle, isBull, isDark)
                    }
                }
            }
        }
    }
}

@Composable
fun CycleCard(c: CycleCardData, isBull: Boolean, isDark: Boolean) {
    val card2 = if (isDark) Color(0xFF101518) else Color.White
    val lineCol = if (isDark) Color(0xFF1F2A2D) else Color(0xFFE2E8F0)
    val textCol = if (isDark) Color(0xFFE7EEF0) else Color(0xFF1E293B)
    val mutedCol = if (isDark) Color(0xFF8A9AA0) else Color(0xFF64748B)
    val dimCol = if (isDark) Color(0xFF5D6C70) else Color(0xFF94A3B8)
    val chipCol = if (isDark) Color(0xFF1A2326) else Color(0xFFF1F5F9)
    
    val green = Color(0xFF4ADE80)
    val greenDim = if (isDark) Color(0xFF1F3A2C) else Color(0xFFDCFCE7)
    val red = Color(0xFFF87171)
    val redDim = if (isDark) Color(0xFF3A1F1F) else Color(0xFFFEE2E2)
    val amber = Color(0xFFFBBF24)
    val amberDim = if (isDark) Color(0xFF3A2E1F) else Color(0xFFFEF3C7)

    val pctColor = if (isBull) green else red
    val pillBg = if (c.statusKind == "open") amberDim else greenDim
    val pillFg = if (c.statusKind == "open") amber else green

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(card2, RoundedCornerShape(12.dp))
            .border(1.dp, lineCol, RoundedCornerShape(12.dp))
            .padding(start = 9.dp, end = 9.dp, top = 10.dp, bottom = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            val pctStr = String.format(Locale.US, "%.2f", c.pct)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${c.sign}$pctStr",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = pctColor,
                    letterSpacing = (-0.1).sp
                )
                Text(
                    text = "%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = pctColor,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(chipCol, RoundedCornerShape(5.dp))
                    .border(1.dp, lineCol, RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("#${c.rank}", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = mutedCol)
            }
        }

        Box(
            modifier = Modifier
                .background(pillBg, RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(c.statusLabel, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = pillFg)
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Date rows
        Text(c.date1, fontSize = 11.sp, color = dimCol, modifier = Modifier.padding(bottom = 2.dp))
        Text(c.date2, fontSize = 11.sp, color = dimCol, modifier = Modifier.padding(bottom = 2.dp))
        Text(c.date3, fontSize = 11.sp, color = dimCol)

        Spacer(modifier = Modifier.height(8.dp))

        // Duration row
        Text("Duration", color = mutedCol, fontSize = 9.5.sp)
        val durTxt = if (c.days2 > 0) "${c.days1}+${c.days2}=${c.days1+c.days2} days" else "${c.days1} days"
        Text(durTxt, color = textCol, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

        Spacer(modifier = Modifier.height(8.dp))

        // Total
        Text("Total", color = mutedCol, fontSize = 9.5.sp)
        val dStr = String.format(Locale.US, "%.2f", c.datasetPct)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${if(c.datasetPct > 0) "+" else ""}$dStr", 
                color = if (c.datasetPct > 0) green else red, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("%", color = if (c.datasetPct > 0) green else red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text(c.datasetDate, fontSize = 11.sp, color = dimCol)

        Spacer(modifier = Modifier.height(8.dp))

        // This cycle
        Text("This cycle", color = mutedCol, fontSize = 9.5.sp)
        val cStr = String.format(Locale.US, "%.2f", c.thisCyclePct)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${if(c.thisCyclePct > 0) "+" else ""}$cStr", 
                color = if (c.thisCyclePct > 0) green else red, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("%", color = if (c.thisCyclePct > 0) green else red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text(c.thisCycleDate, fontSize = 11.sp, color = dimCol)
    }
}
