package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioMonthlyBreakdownScreen(
    viewModel: NavViewModel,
    isDarkTheme: Boolean,
    monthKey: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTxnsState = viewModel.getAllCasTransactions().collectAsStateWithLifecycle(
        initialValue = viewModel.getCachedTransactions()
    )
    val txns = allTxnsState.value

    val investedItems = remember(uiState) {
        when (uiState) {
            is UiState.Success -> (uiState as UiState.Success).items.filter { it.isInvested }
            else -> emptyList()
        }
    }

    // High-contrast clean color palette
    val backgroundColor = if (isDarkTheme) Color(0xFF0F1115) else Color(0xFFF8F9FA)
    val cardBackground = if (isDarkTheme) Color(0xFF181B20) else Color.White
    val innerSectionBg = if (isDarkTheme) Color(0xFF13161A) else Color(0xFFF1F3F5)
    val zebraBackground = if (isDarkTheme) Color(0xFF15181D) else Color(0xFFF8F9FA)
    val borderColor = if (isDarkTheme) Color(0xFF262B33) else Color(0xFFDEE2E6)

    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF1A1C1E)
    val textSecondary = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF616161)
    val textMuted = if (isDarkTheme) Color(0xFF757575) else Color(0xFF8D99AE)

    val investmentColor = if (isDarkTheme) Color(0xFF00E676) else Color(0xFF1B5E20)
    val redemptionColor = if (isDarkTheme) Color(0xFFFF5252) else Color(0xFFC62828)
    val netPositiveColor = if (isDarkTheme) Color(0xFF00E676) else Color(0xFF1B5E20)
    val netNegativeColor = if (isDarkTheme) Color(0xFFFF5252) else Color(0xFFC62828)

    // Compute monthly data with exact scheme-level FIFO matching
    val monthlyData = remember(txns, investedItems) {
        MonthlyCashflowCalculator.computeMonthlyCashFlow(txns, investedItems)
    }

    // Active selected month key
    var currentSelectedMonthKey by remember(monthKey, monthlyData) {
        mutableStateOf(
            if (monthKey.isNotBlank() && monthlyData.any { it.monthKey == monthKey }) {
                monthKey
            } else {
                monthlyData.firstOrNull()?.monthKey ?: ""
            }
        )
    }

    val activeMonthItem = remember(monthlyData, currentSelectedMonthKey) {
        monthlyData.firstOrNull { it.monthKey == currentSelectedMonthKey }
            ?: monthlyData.firstOrNull()
    }

    // Track which funds are expanded (schemeName -> Boolean)
    val expandedFunds = remember(currentSelectedMonthKey) {
        mutableStateMapOf<String, Boolean>()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .testTag("portfolio_monthly_breakdown_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${activeMonthItem?.monthLabel ?: "Monthly"} Breakdown",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        )
                        Text(
                            text = "Tap any fund to view its transactions",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("monthly_breakdown_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Monthly Cash Flow",
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month Selector Chips
            if (monthlyData.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        monthlyData.sortedByDescending { it.monthKey }.forEach { item ->
                            val isSelected = item.monthKey == currentSelectedMonthKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { currentSelectedMonthKey = item.monthKey },
                                label = {
                                    Text(
                                        text = item.monthLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("month_chip_${item.monthKey}")
                            )
                        }
                    }
                }
            }

            if (activeMonthItem == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transaction data found for this period",
                            style = MaterialTheme.typography.bodyMedium.copy(color = textSecondary)
                        )
                    }
                }
            } else {
                // Month Summary Overview Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${activeMonthItem.monthLabel} Total Activity",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 13.5.sp
                                    )
                                )
                                Text(
                                    text = "${activeMonthItem.buyCount + activeMonthItem.sellCount} Transactions",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = borderColor)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Monthly Investment
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = "INVESTMENT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val invDec = activeMonthItem.investmentAmount % 1.0 > 0.001
                                    Text(
                                        text = if (activeMonthItem.investmentAmount > 0) {
                                            "₹${formatIndianCommas(activeMonthItem.investmentAmount, if (invDec) 2 else 0)}"
                                        } else "₹0",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = investmentColor,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                    )
                                }

                                // Monthly Redemption (FIFO Cost)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "REDEMPTION (FIFO)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val redDec = activeMonthItem.redemptionAmount % 1.0 > 0.001
                                    Text(
                                        text = if (activeMonthItem.redemptionAmount > 0) {
                                            "-₹${formatIndianCommas(activeMonthItem.redemptionAmount, if (redDec) 2 else 0)}"
                                        } else "₹0",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (activeMonthItem.redemptionAmount > 0) redemptionColor else textMuted,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                    )
                                }

                                // Monthly Net Contribution
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "MONTHLY NET",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val netSign = if (activeMonthItem.netAmount > 0) "+" else if (activeMonthItem.netAmount < 0) "-" else ""
                                    val netDec = activeMonthItem.netAmount % 1.0 > 0.001
                                    Text(
                                        text = "$netSign₹${formatIndianCommas(abs(activeMonthItem.netAmount), if (netDec) 2 else 0)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (activeMonthItem.netAmount >= 0) netPositiveColor else netNegativeColor,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Header: Funds List with Expand/Collapse All toggle
                item {
                    val allExpanded = activeMonthItem.fundContributions.all { expandedFunds[it.schemeName] == true }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Funds (${activeMonthItem.fundContributions.size})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 13.5.sp
                            )
                        )

                        TextButton(
                            onClick = {
                                val newState = !allExpanded
                                activeMonthItem.fundContributions.forEach {
                                    expandedFunds[it.schemeName] = newState
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = if (allExpanded) "Collapse All" else "Expand All",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }

                // List of funds in clean expandable accordion cards
                itemsIndexed(activeMonthItem.fundContributions) { fundIndex, fundContribution ->
                    val isExpanded = expandedFunds[fundContribution.schemeName] == true
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        label = "arrow_rotation"
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("fund_breakdown_card_$fundIndex")
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Clickable Fund Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedFunds[fundContribution.schemeName] = !isExpanded
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = fundContribution.schemeName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 13.5.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Subtitle: Inflow / Outflow summary and Txn count
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${fundContribution.transactions.size} txn${if (fundContribution.transactions.size > 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = textSecondary,
                                                fontSize = 11.sp
                                            )
                                        )

                                        if (fundContribution.investmentAmount > 0 && fundContribution.fifoCostDeducted > 0) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.labelSmall.copy(color = textMuted)
                                            )
                                            Text(
                                                text = "+₹${formatIndianCommas(fundContribution.investmentAmount, 0)} / -₹${formatIndianCommas(fundContribution.fifoCostDeducted, 0)}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = textSecondary,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.5.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Fund Net Amount
                                    val fNetSign = if (fundContribution.netAmount > 0) "+" else if (fundContribution.netAmount < 0) "-" else ""
                                    val fNetDec = fundContribution.netAmount % 1.0 > 0.001
                                    Text(
                                        text = "$fNetSign₹${formatIndianCommas(abs(fundContribution.netAmount), if (fNetDec) 2 else 0)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (fundContribution.netAmount >= 0) netPositiveColor else netNegativeColor,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                    )

                                    // Dropdown Arrow Icon
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse transactions" else "Expand transactions",
                                        tint = textSecondary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(arrowRotation)
                                    )
                                }
                            }

                            // Expandable Transaction Details (Smooth Dropdown)
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(innerSectionBg)
                                ) {
                                    HorizontalDivider(thickness = 0.5.dp, color = borderColor)

                                    fundContribution.transactions.forEachIndexed { txnIdx, decTxn ->
                                        val txnBg = if (txnIdx % 2 == 1) zebraBackground else innerSectionBg

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(txnBg)
                                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                                .testTag("txn_row_${decTxn.txn.id}"),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            // Row 1: Date, Type, Amount
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = decTxn.txn.date,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = textPrimary,
                                                            fontSize = 11.5.sp
                                                        )
                                                    )

                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (decTxn.isBuy) investmentColor.copy(alpha = 0.15f) else redemptionColor.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = if (decTxn.isBuy) "BUY / SIP" else "REDEMPTION",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (decTxn.isBuy) investmentColor else redemptionColor,
                                                                fontSize = 9.sp
                                                            ),
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }

                                                if (decTxn.isBuy) {
                                                    val invDec = decTxn.investmentAmount % 1.0 > 0.001
                                                    Text(
                                                        text = "+₹${formatIndianCommas(decTxn.investmentAmount, if (invDec) 2 else 0)}",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = investmentColor,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 12.sp
                                                        )
                                                    )
                                                } else {
                                                    val redDec = decTxn.fifoCostDeducted % 1.0 > 0.001
                                                    Text(
                                                        text = "-₹${formatIndianCommas(decTxn.fifoCostDeducted, if (redDec) 2 else 0)}",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = redemptionColor,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 12.sp
                                                        )
                                                    )
                                                }
                                            }

                                            // Row 2: Units @ NAV & Description
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${String.format(Locale.US, "%,.3f", decTxn.units)} units @ ₹${String.format(Locale.US, "%.4f", decTxn.nav)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = textSecondary,
                                                        fontSize = 10.5.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                )

                                                Text(
                                                    text = decTxn.txn.description.ifBlank { decTxn.txn.type },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = textSecondary,
                                                        fontSize = 10.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // Row 3 for Redemptions: FIFO basis vs Proceeds
                                            if (decTxn.isSell && decTxn.redemptionProceeds > 0) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "FIFO Basis: ₹${formatIndianCommas(decTxn.fifoCostDeducted, 2)}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = textMuted,
                                                            fontSize = 9.5.sp
                                                        )
                                                    )
                                                    Text(
                                                        text = "Proceeds: ₹${formatIndianCommas(decTxn.redemptionProceeds, 2)}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = textSecondary,
                                                            fontSize = 9.5.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        if (txnIdx < fundContribution.transactions.lastIndex) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = borderColor.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
