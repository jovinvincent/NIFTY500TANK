package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekdayDistributionComponent(points: List<TimelineChartPoint>, isDark: Boolean) {
    if (points.size < 2) return

    var isExpanded by remember { mutableStateOf(false) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    
    val minAvailableDate = remember(points) { points.firstOrNull()?.let { parseTimelineDate(it.dateStr) } ?: 0L }
    val maxAvailableDate = remember(points) { points.lastOrNull()?.let { parseTimelineDate(it.dateStr) } ?: 0L }
    
    val data = remember(points, startDateMillis) {
        val validPoints = if (startDateMillis != null) {
            points.filter { parseTimelineDate(it.dateStr) >= startDateMillis!! }
        } else {
            points
        }
        val posCounts = IntArray(5)
        val negCounts = IntArray(5)
        var totalPos = 0
        var totalNeg = 0
        var sumPosReturn = 0.0
        var sumNegReturn = 0.0

        val cal = Calendar.getInstance()

        for (i in 1 until validPoints.size) {
            val prev = validPoints[i - 1].nav
            val curr = validPoints[i].nav
            if (prev == 0.0) continue
            val returnPct = (curr - prev) / prev

            val dateLong = parseTimelineDate(validPoints[i].dateStr)
            if (dateLong == 0L) continue

            cal.timeInMillis = dateLong
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            
            // Map Mon=0, Tue=1, Wed=2, Thu=3, Fri=4
            val dayIndex = when (dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                else -> -1
            }

            if (dayIndex != -1) {
                if (curr > prev) {
                    posCounts[dayIndex]++
                    totalPos++
                    sumPosReturn += returnPct
                } else if (curr < prev) {
                    negCounts[dayIndex]++
                    totalNeg++
                    sumNegReturn += returnPct
                }
            }
        }

        val posDist = DoubleArray(5)
        val negDist = DoubleArray(5)
        val posDayDist = DoubleArray(5)
        val negDayDist = DoubleArray(5)
        for (i in 0 until 5) {
            posDist[i] = if (totalPos > 0) posCounts[i].toDouble() / totalPos * 100 else 0.0
            negDist[i] = if (totalNeg > 0) negCounts[i].toDouble() / totalNeg * 100 else 0.0
            
            val dayTotal = posCounts[i] + negCounts[i]
            posDayDist[i] = if (dayTotal > 0) posCounts[i].toDouble() / dayTotal * 100 else 0.0
            negDayDist[i] = if (dayTotal > 0) negCounts[i].toDouble() / dayTotal * 100 else 0.0
        }
        val totalDays = totalPos + totalNeg
        val avgPosReturn = if (totalPos > 0) sumPosReturn / totalPos else 0.0
        val avgNegReturn = if (totalNeg > 0) sumNegReturn / totalNeg else 0.0
        val posRatio = if (totalDays > 0) totalPos.toDouble() / totalDays else 0.0
        val negRatio = if (totalDays > 0) totalNeg.toDouble() / totalDays else 0.0
        
        val expectedDailyReturn = (avgPosReturn * posRatio) + (avgNegReturn * negRatio)
        val annualizedAlpha = (Math.pow(1.0 + expectedDailyReturn, 252.0) - 1.0) * 100.0

        listOf(posDist, negDist, posDayDist, negDayDist, doubleArrayOf(avgPosReturn * 100, avgNegReturn * 100, posRatio * 100, negRatio * 100, annualizedAlpha))
    }
    
    val posDist = data[0] as DoubleArray
    val negDist = data[1] as DoubleArray
    val posDayDist = data[2] as DoubleArray
    val negDayDist = data[3] as DoubleArray
    val alphaStats = data[4] as DoubleArray
    
    val avgPosReturn = alphaStats[0]
    val avgNegReturn = alphaStats[1]
    val posRatio = alphaStats[2]
    val negRatio = alphaStats[3]
    val expectedAlpha = alphaStats[4]

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF1E2228) else Color(0xFFFFFFFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C3238) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(if (isExpanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "Expand", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Swing Data & Distribution",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.clickable { showDatePicker = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = "Change Start Date", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        val df = SimpleDateFormat("dd MMM yy", Locale.US)
                        Text(
                            text = startDateMillis?.let { df.format(Date(it)) } ?: "All Time",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = startDateMillis ?: minAvailableDate,
                    selectableDates = object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            return utcTimeMillis in minAvailableDate..maxAvailableDate
                        }
                    }
                )
                
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            startDateMillis = datePickerState.selectedDateMillis
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(
                        state = datePickerState,
                        showModeToggle = false,
                        title = null,
                        headline = null,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            ExpectedAlphaComponent(avgPosReturn, avgNegReturn, posRatio, negRatio, expectedAlpha, isDark)
            
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    ButterflyChart(posDayDist, negDayDist, isDark)
                    Spacer(modifier = Modifier.height(24.dp))
                    DualRingDonutChart(posDist, negDist, isDark)
                }
            }
        }
    }
}

@Composable
fun ButterflyChart(posDist: DoubleArray, negDist: DoubleArray, isDark: Boolean) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val maxPct = maxOf((posDist.maxOrNull() ?: 0.0), (negDist.maxOrNull() ?: 0.0)).coerceAtLeast(1.0)
    
    val posColor = Color(0xFF4CAF50)
    val negColor = Color(0xFFF44336)
    val textColor = MaterialTheme.colorScheme.onSurface
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Negative Days (%)", color = negColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Weekday", color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Positive Days (%)", color = posColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        for (i in 0 until 5) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Negative bar (Right to Left)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val widthFraction = (negDist[i] / maxPct).coerceIn(0.001, 1.0).toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight((1f - widthFraction).coerceAtLeast(0.001f)))
                        if (negDist[i] > 0) {
                            Text(String.format(Locale.US, "%.0f%%", negDist[i]), fontSize = 12.sp, color = textColor, modifier = Modifier.padding(end = 4.dp))
                        }
                        Box(modifier = Modifier.weight(widthFraction).height(24.dp).background(negColor, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                    }
                }
                
                // Label
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    Text(days[i], fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                }
                
                // Positive bar (Left to Right)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val widthFraction = (posDist[i] / maxPct).coerceIn(0.001, 1.0).toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(widthFraction).height(24.dp).background(posColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                        if (posDist[i] > 0) {
                            Text(String.format(Locale.US, "%.0f%%", posDist[i]), fontSize = 12.sp, color = textColor, modifier = Modifier.padding(start = 4.dp))
                        }
                        Box(modifier = Modifier.weight((1f - widthFraction).coerceAtLeast(0.001f)))
                    }
                }
            }
        }
    }
}

@Composable
fun DualRingDonutChart(posDist: DoubleArray, negDist: DoubleArray, isDark: Boolean) {
    val posColor = Color(0xFF4CAF50)
    val negColor = Color(0xFFF44336)
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val pctStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)

    Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val innerStroke = 24.dp.toPx()
            val outerStroke = 24.dp.toPx()
            val gap = 4.dp.toPx()
            
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = (size.width / 2) - 40.dp.toPx()
            val innerRadius = outerRadius - outerStroke - gap
            
            // Draw Positive (Inner Ring)
            var currentAngle = -90f
            for (i in 0 until 5) {
                val sweep = (posDist[i] / 100f * 360f).toFloat()
                if (sweep > 0) {
                    val arcTopLeft = Offset(center.x - innerRadius + innerStroke/2, center.y - innerRadius + innerStroke/2)
                    drawArc(
                        color = posColor.copy(alpha = 0.8f + (i * 0.05f)), // slight variation
                        startAngle = currentAngle + 1f,
                        sweepAngle = sweep - 2f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = Size(innerRadius * 2 - innerStroke, innerRadius * 2 - innerStroke),
                        style = Stroke(width = innerStroke, cap = StrokeCap.Butt)
                    )
                    
                    val angleRad = Math.toRadians((currentAngle + sweep / 2).toDouble())
                    val textR = innerRadius - innerStroke/2
                    val tx = center.x + (textR * Math.cos(angleRad)).toFloat()
                    val ty = center.y + (textR * Math.sin(angleRad)).toFloat()
                    

                }
                currentAngle += sweep
            }
            
            // Draw Negative (Outer Ring)
            currentAngle = -90f
            for (i in 0 until 5) {
                val sweep = (negDist[i] / 100f * 360f).toFloat()
                if (sweep > 0) {
                    val arcTopLeft = Offset(center.x - outerRadius + outerStroke/2, center.y - outerRadius + outerStroke/2)
                    drawArc(
                        color = negColor.copy(alpha = 0.8f + (i * 0.05f)),
                        startAngle = currentAngle + 1f,
                        sweepAngle = sweep - 2f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = Size(outerRadius * 2 - outerStroke, outerRadius * 2 - outerStroke),
                        style = Stroke(width = outerStroke, cap = StrokeCap.Butt)
                    )
                    
                    val angleRad = Math.toRadians((currentAngle + sweep / 2).toDouble())
                    val textR = outerRadius - outerStroke/2
                    val tx = center.x + (textR * Math.cos(angleRad)).toFloat()
                    val ty = center.y + (textR * Math.sin(angleRad)).toFloat()
                    

                    

                }
                currentAngle += sweep
            }
            // Day Labels outside
            var currentPosAngle = -90f
            var currentNegAngle = -90f
            for (i in 0 until 5) {
                val posSweep = (posDist[i] / 100f * 360f).toFloat()
                val negSweep = (negDist[i] / 100f * 360f).toFloat()
                
                var angleRad = 0.0
                if (negSweep > 0) {
                    angleRad = Math.toRadians((currentNegAngle + negSweep / 2).toDouble())
                } else if (posSweep > 0) {
                    angleRad = Math.toRadians((currentPosAngle + posSweep / 2).toDouble())
                }
                
                if (posSweep > 0 || negSweep > 0) {
                    val labelR = outerRadius + 22.dp.toPx()
                    val lx = center.x + (labelR * Math.cos(angleRad)).toFloat()
                    val ly = center.y + (labelR * Math.sin(angleRad)).toFloat()
                    
                    val dayLayout = textMeasurer.measure(days[i], style = textStyle)
                    val posLayout = textMeasurer.measure(String.format(Locale.US, "%.0f%%", posDist[i]), style = androidx.compose.ui.text.TextStyle(color = posColor, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    val negLayout = textMeasurer.measure(String.format(Locale.US, "%.0f%%", negDist[i]), style = androidx.compose.ui.text.TextStyle(color = negColor, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    
                    val totalHeight = dayLayout.size.height + posLayout.size.height + negLayout.size.height
                    var currentY = ly - totalHeight / 2
                    
                    drawText(dayLayout, topLeft = Offset(lx - dayLayout.size.width/2, currentY))
                    currentY += dayLayout.size.height
                    drawText(posLayout, topLeft = Offset(lx - posLayout.size.width/2, currentY))
                    currentY += posLayout.size.height
                    drawText(negLayout, topLeft = Offset(lx - negLayout.size.width/2, currentY))
                }
                currentPosAngle += posSweep
                currentNegAngle += negSweep
            }
        }
        
        // Center Legend
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(posColor, RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Positive", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(negColor, RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Negative", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun ExpectedAlphaComponent(
    avgPosReturn: Double,
    avgNegReturn: Double,
    posRatio: Double,
    negRatio: Double,
    expectedAlpha: Double,
    isDark: Boolean
) {
    val posColor = Color(0xFF4CAF50)
    val negColor = Color(0xFFF44336)
    val alphaColor = if (expectedAlpha >= 0) posColor else negColor
    val cardBg = if (isDark) Color(0xFF15171A) else Color(0xFFF8FAFC)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cardBg
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Differential Expected Alpha",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = String.format(Locale.US, "+%.2f%%", avgPosReturn),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = posColor
                    )
                    Text(
                        text = String.format(Locale.US, "Avg Up Day (%.0f%%)", posRatio),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("VS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, "%.2f%%", avgNegReturn),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = negColor
                    )
                    Text(
                        text = String.format(Locale.US, "Avg Down Day (%.0f%%)", negRatio),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Expected Annualized Return",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format(Locale.US, "%s%.2f%%", if(expectedAlpha > 0) "+" else "", expectedAlpha),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = alphaColor
                )
            }
        }
    }
}
