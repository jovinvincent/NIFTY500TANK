package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun AllFundsSummaryTableView(
    summaryList: List<FundSummaryRow>,
    isDarkTheme: Boolean,
    onSelectFund: (String) -> Unit
) {
    var activeModalIndex by remember { mutableStateOf<Int?>(null) }

    val emeraldColor = Color(0xFF10B981)
    val sapphireColor = Color(0xFF3B82F6)
    val goldColor = Color(0xFFF59E0B)
    val surfaceColor = if (isDarkTheme) Color(0xFF161B22) else Color(0xFFFFFFFF)
    val onSurfaceColor = if (isDarkTheme) Color(0xFFF0F6FC) else Color(0xFF1E293B)
    val cardBg = if (isDarkTheme) Color(0xFF111722) else Color(0xFFF8FAFC)
    val borderColor = if (isDarkTheme) Color(0xFF30363D) else Color(0xFFE2E8F0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(sapphireColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = sapphireColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cross-Fund Strategy & Timing Matrix",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = onSurfaceColor
                            )
                            Text(
                                text = "Tap any fund card or Timing Oracle to swipe through predictive analysis",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2. Individual Fund Cards (Clean, spacious, zero text overlap)
        if (summaryList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No backtest data available yet. Please sync funds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(summaryList, key = { _, item -> item.schemeName }) { index, row ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { activeModalIndex = index }
                        .testTag("fund_summary_card_${row.schemeName}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header: Scheme Name and Category Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = row.schemeName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                color = onSurfaceColor,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = sapphireColor.copy(alpha = 0.12f),
                                border = BorderStroke(0.5.dp, sapphireColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = row.category,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = sapphireColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                )
                            }
                        }

                        // Strategy & Live Timing Pills Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Best Strategy Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = emeraldColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "★ ${row.preferredMethod}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = emeraldColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Live Prediction Status Badge
                            row.predictiveAnalysis?.let { pa ->
                                val badgeColor = Color(pa.verdict.tagColorHex)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeColor.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = pa.verdict.shortBadge,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    }
                                }
                            }
                        }

                        // Performance Metrics Bar (Spacious 3-columns)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cardBg,
                            border = BorderStroke(0.8.dp, borderColor.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "TOP XIRR",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        text = "${String.format(Locale.US, "%.2f", row.preferredXirr)}%",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = emeraldColor
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1.2f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "ALPHA VS REGULAR",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        text = if (row.alphaMargin >= 0) "+${String.format(Locale.US, "%.2f", row.alphaMargin)}%" else "${String.format(Locale.US, "%.2f", row.alphaMargin)}%",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (row.alphaMargin >= 0) emeraldColor else goldColor
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1.4f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "BASELINE",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        text = row.baselineMethod,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = onSurfaceColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Key Advantage text
                        Text(
                            text = "• ${row.keyAdvantage}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Action Buttons Bar (Timing Oracle & Inspect Fund)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Timing Oracle Button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = sapphireColor.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, sapphireColor.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { activeModalIndex = index }
                                    .testTag("predictive_timing_btn_${row.schemeName}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = sapphireColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "Timing Oracle",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = sapphireColor
                                    )
                                }
                            }

                            // Inspect Fund Action
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSelectFund(row.schemeName) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Inspect Fund",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = sapphireColor
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = sapphireColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Simple Empirical Rules Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = sapphireColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "PORTFOLIO EXECUTION PLAYBOOK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = sapphireColor
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardBg, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(text = "1. ", fontWeight = FontWeight.Bold, color = emeraldColor, fontSize = 11.sp)
                            Text(
                                text = "Dip-Timed SIPs Beat Calendar Buying: Timing investments on intra-month dips systematically generates +2.5% to +10.0% annualized alpha across equity funds by eliminating peak-entry drag.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                color = onSurfaceColor
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text(text = "2. ", fontWeight = FontWeight.Bold, color = sapphireColor, fontSize = 11.sp)
                            Text(
                                text = "Tactical Boosters for Cash Reserves: Smart Tactical SIP is optimal when liquid cash reserves are available, deploying 2.0x–3.0x multiplier tranches during deep pullbacks.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                color = onSurfaceColor
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text(text = "3. ", fontWeight = FontWeight.Bold, color = goldColor, fontSize = 11.sp)
                            Text(
                                text = "Individual Fund Audits: Tap any fund card or the 'Timing Oracle' button to view complete 7-strategy backtest timelines, drawdowns, and actionable playbooks.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                color = onSurfaceColor
                            )
                        }
                    }
                }
            }
        }
    }

    // Predictive Timing Oracle Modal Dialog (Swipeable across all funds)
    val currentIdx = activeModalIndex
    val allAnalyses = summaryList.mapNotNull { it.predictiveAnalysis }
    if (currentIdx != null && allAnalyses.isNotEmpty()) {
        PredictiveTimingAnalysisModal(
            analysisList = allAnalyses,
            initialIndex = currentIdx.coerceIn(0, allAnalyses.size - 1),
            isDarkTheme = isDarkTheme,
            onDismiss = { activeModalIndex = null },
            onNavigateToBacktest = { schemeName ->
                activeModalIndex = null
                onSelectFund(schemeName)
            }
        )
    }
}
