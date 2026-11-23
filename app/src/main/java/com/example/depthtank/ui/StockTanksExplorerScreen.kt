package com.example.depthtank.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.depthtank.data.FlowFilter
import com.example.depthtank.data.StockTank
import com.example.depthtank.data.TankUiState
import com.example.ui.theme.BreakoutAmber
import com.example.ui.theme.CapitulationPurple
import com.example.ui.theme.CoralOverheated
import com.example.ui.theme.CyanLiquid
import com.example.ui.theme.DeepSlateWater
import com.example.ui.theme.DeepSlateWaterMuted
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.RoyalBlueLiquid
import com.example.ui.theme.SubtleDivider
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ValuationPePink
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTanksExplorerScreen(
    uiState: TankUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectSector: (String) -> Unit,
    onSelectFlowFilter: (FlowFilter) -> Unit,
    onOpenStockDetail: (StockTank) -> Unit,
    onDismissStockDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sectorMenuExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "mini_droplet_pulse")
    val dropletAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "droplet_alpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("stock_tanks_explorer_screen")
    ) {
        val columns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(columns) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.selectedUniverse.shortLabel} Mini Tanks",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${uiState.filteredStocks.size} showing of ${uiState.activeStocksCount} tracked (${uiState.selectedUniverse.rangeLabel})",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    // Search + Sector Dropdown Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    text = "Ticker or company...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search stocks",
                                    tint = CyanLiquid
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchQueryChange("") },
                                        modifier = Modifier.testTag("clear_stock_search_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = TextSecondary
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = GlassCardSurface,
                                unfocusedContainerColor = GlassCardSurface,
                                focusedBorderColor = CyanLiquid,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stock_search_input")
                        )

                        Box {
                            OutlinedButton(
                                onClick = { sectorMenuExpanded = true },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, HairlineBorder),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                                modifier = Modifier.testTag("sector_filter_button")
                            ) {
                                Text(
                                    text = if (uiState.selectedSector == "All Sectors") "Sector" else uiState.selectedSector,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "Select sector",
                                    tint = CyanLiquid
                                )
                            }

                            DropdownMenu(
                                expanded = sectorMenuExpanded,
                                onDismissRequest = { sectorMenuExpanded = false },
                                containerColor = ElevatedCardSurface
                            ) {
                                uiState.availableSectors.forEach { sector ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sector,
                                                color = if (sector == uiState.selectedSector) CyanLiquid else TextPrimary,
                                                fontWeight = if (sector == uiState.selectedSector) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onSelectSector(sector)
                                            sectorMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Quick Flow Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlowFilter.entries.forEach { filter ->
                            val count = when (filter) {
                                FlowFilter.ALL -> uiState.activeStocksCount
                                FlowFilter.FLOWING -> uiState.flowingCount
                                FlowFilter.HIGH_PRESSURE -> uiState.highPressureCount
                                FlowFilter.BELOW_HOLE -> uiState.belowHoleCount
                            }
                            val selected = uiState.selectedFlowFilter == filter
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectFlowFilter(filter) },
                                label = {
                                    Text(
                                        text = "${filter.displayLabel(uiState.holeThreshold)} ($count)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanLiquid.copy(alpha = 0.18f),
                                    selectedLabelColor = CyanLiquid,
                                    containerColor = ElevatedCardSurface,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = HairlineBorder,
                                    selectedBorderColor = CyanLiquid.copy(alpha = 0.55f)
                                ),
                                modifier = Modifier.testTag("flow_filter_chip_${filter.name.lowercase(Locale.US)}")
                            )
                        }
                    }
                }
            }

            if (uiState.filteredStocks.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No matching ${uiState.selectedUniverse.shortLabel} stocks found.",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Clear the search query or switch back to All Sectors.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(
                    items = uiState.filteredStocks,
                    key = { it.symbol }
                ) { stock ->
                    MiniStockTankCard(
                        stock = stock,
                        holeThreshold = uiState.holeThreshold,
                        dropletPulseAlpha = dropletAlpha,
                        onClick = { onOpenStockDetail(stock) }
                    )
                }
            }
        }
    }

    val selected = uiState.selectedStockForDetail
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissStockDetail,
            sheetState = sheetState,
            containerColor = ElevatedCardSurface,
            contentColor = TextPrimary
        ) {
            StockTankDetailSheetContent(
                stock = selected,
                activeStocksCount = uiState.activeStocksCount,
                holeThreshold = uiState.holeThreshold
            )
        }
    }
}

@Composable
private fun MiniStockTankCard(
    stock: StockTank,
    holeThreshold: Float = 50f,
    dropletPulseAlpha: Float,
    onClick: () -> Unit
) {
    val isFlowing = stock.isFlowingAt(holeThreshold.toDouble())
    val overflowVol = stock.overflowVolumeAt(holeThreshold.toDouble())
    val borderAccent = when {
        stock.isMakingFresh52WHigh -> BreakoutAmber.copy(alpha = 0.72f)
        stock.isHighPressure -> CyanLiquid.copy(alpha = 0.65f)
        stock.isCapitulation -> CapitulationPurple.copy(alpha = 0.65f)
        isFlowing -> HairlineBorder
        else -> DeepSlateWater.copy(alpha = 0.5f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("stock_tank_card_${stock.symbol}"),
        shape = RoundedCornerShape(18.dp),
        color = GlassCardSurface,
        border = BorderStroke(1.dp, borderAccent)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Ticker + Breakout / Capitulation / Flowing indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stock.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stock.sector,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (stock.isMakingFresh52WHigh) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BreakoutAmber.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, BreakoutAmber.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "52W HIGH",
                                style = MaterialTheme.typography.labelSmall,
                                color = BreakoutAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (stock.isCapitulation) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = CapitulationPurple.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, CapitulationPurple.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "CAP <15",
                                style = MaterialTheme.typography.labelSmall,
                                color = CapitulationPurple,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isFlowing) {
                        Icon(
                            imageVector = Icons.Rounded.WaterDrop,
                            contentDescription = "Pouring into main tank",
                            tint = CyanLiquid,
                            modifier = Modifier
                                .size(16.dp)
                                .alpha(dropletPulseAlpha)
                        )
                    }
                }
            }

            // Mini Visual Tank + Score Readout side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniTankCylinder(
                    score = stock.score,
                    holeThreshold = holeThreshold,
                    isFlowing = isFlowing,
                    modifier = Modifier
                        .width(46.dp)
                        .height(74.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", stock.score),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (isFlowing) CyanLiquid else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isFlowing) {
                            "+${String.format(Locale.US, "%.1f", overflowVol)} flow"
                        } else {
                            "Below ${holeThreshold.toInt()}% hole"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFlowing) EmeraldBullish else TextMuted
                    )
                    Text(
                        text = formatRupees(stock.currentPrice),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                    val chgColor = if (stock.dailyChangePct >= 0.0) EmeraldBullish else CoralOverheated
                    Text(
                        text = formatSignedPercent(stock.dailyChangePct),
                        style = MaterialTheme.typography.labelSmall,
                        color = chgColor
                    )
                    val peSummary = when {
                        stock.peTtm != null && stock.peScore != null ->
                            String.format(Locale.US, "P/E %.1f× • V:%d", stock.peTtm, stock.peScore!!.roundToInt())
                        stock.peTtm != null ->
                            String.format(Locale.US, "P/E %.1f×", stock.peTtm)
                        else -> "P/E N/A"
                    }
                    Text(
                        text = peSummary,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (stock.peTtm != null) ValuationPePink else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 52W Range Bar
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ElevatedCardSurface)
                ) {
                    val fillW = (stock.score / 100.0).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillW)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isFlowing) CyanLiquid else DeepSlateWater)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "L:${stock.low52W.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "H:${stock.high52W.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTankCylinder(
    score: Double,
    holeThreshold: Float = 50f,
    isFlowing: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val corner = 10.dp.toPx()
        val rect = Rect(0f, 0f, size.width, size.height)
        val clip = Path().apply {
            addRoundRect(RoundRect(rect, CornerRadius(corner, corner)))
        }
        val fillFrac = (score / 100.0).toFloat().coerceIn(0f, 1f)
        val waterTopY = size.height * (1f - fillFrac)
        val holeFrac = (holeThreshold / 100f).coerceIn(0f, 1f)
        val holeY = size.height * (1f - holeFrac)

        drawPath(path = clip, color = ObsidianBackground)

        clipPath(clip) {
            if (fillFrac > 0.01f) {
                val topColor = if (isFlowing) CyanLiquid.copy(alpha = 0.85f) else DeepSlateWater
                val bottomColor = if (isFlowing) RoyalBlueLiquid.copy(alpha = 0.85f) else DeepSlateWaterMuted
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(topColor, bottomColor),
                        startY = waterTopY,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, waterTopY),
                    size = Size(size.width, size.height - waterTopY)
                )
            }
            // Dashed Hole line across cylinder
            drawLine(
                color = if (isFlowing) Color.White.copy(alpha = 0.6f) else TextMuted.copy(alpha = 0.7f),
                start = Offset(0f, holeY),
                end = Offset(size.width, holeY),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
            )
        }

        drawRoundRect(
            color = if (isFlowing) CyanLiquid.copy(alpha = 0.5f) else HairlineBorder,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

@Composable
private fun StockTankDetailSheetContent(
    stock: StockTank,
    activeStocksCount: Int,
    holeThreshold: Float = 50f
) {
    val h = holeThreshold.toDouble()
    val isFlowing = stock.isFlowingAt(h)
    val overflowVol = stock.overflowVolumeAt(h)
    val share = stock.contributionPct(activeStocksCount, h)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .testTag("stock_detail_bottom_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${stock.name} • ${stock.sector}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isFlowing) CyanLiquid.copy(alpha = 0.16f) else DeepSlateWater.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, if (isFlowing) CyanLiquid else HairlineBorder)
            ) {
                Text(
                    text = if (isFlowing) "FLOWING (> ${holeThreshold.toInt()}%)" else "BELOW ${holeThreshold.toInt()}% HOLE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isFlowing) CyanLiquid else TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        HorizontalDivider(color = SubtleDivider)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailStatTile("Current Price", formatRupees(stock.currentPrice), TextPrimary, Modifier.weight(1f))
            DetailStatTile("52-Week Low (0)", formatRupees(stock.low52W), CoralOverheated, Modifier.weight(1f))
            DetailStatTile("52-Week High (100)", formatRupees(stock.high52W), EmeraldBullish, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailStatTile(
                "Normalized Score",
                String.format(Locale.US, "%.2f / 100", stock.score),
                if (isFlowing) CyanLiquid else TextSecondary,
                Modifier.weight(1f)
            )
            DetailStatTile(
                "Overflow Volume",
                String.format(Locale.US, "%.2f pts", overflowVol),
                if (isFlowing) EmeraldBullish else TextMuted,
                Modifier.weight(1f)
            )
            DetailStatTile(
                "Main Tank Share",
                String.format(Locale.US, "+%.3f%%", share),
                CyanLiquid,
                Modifier.weight(1f)
            )
        }

        val peOverflowVol = stock.peOverflowVolumeAt(h)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stock_detail_valuation_pe_row"),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailStatTile(
                "TTM P/E",
                stock.peTtm?.let { String.format(Locale.US, "%.2f×", it) } ?: "N/A",
                if (stock.peTtm != null) ValuationPePink else TextMuted,
                Modifier.weight(1f)
            )
            DetailStatTile(
                "52W P/E Range",
                if (stock.peLow52W != null && stock.peHigh52W != null) {
                    String.format(Locale.US, "%.1f–%.1f×", stock.peLow52W, stock.peHigh52W)
                } else {
                    "N/A"
                },
                if (stock.hasValidPeWindow) TextPrimary else TextMuted,
                Modifier.weight(1f)
            )
            DetailStatTile(
                "Val P/E Score",
                stock.peScore?.let {
                    String.format(Locale.US, "%.1f (+%.1f)", it, peOverflowVol)
                } ?: "N/A",
                if (stock.isPeFlowingAt(h)) ValuationPePink else TextMuted,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DetailStatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = GlassCardSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
