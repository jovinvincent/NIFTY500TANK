package com.example.depthtank.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CrisisAlert
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.depthtank.data.ExtremeTypeFilter
import com.example.depthtank.data.ExtremesAnalyzer
import com.example.depthtank.data.ExtremesSummary
import com.example.depthtank.data.ExtremesViewMode
import com.example.depthtank.data.MacroExtremeEpisode
import com.example.depthtank.data.MarketRegime
import com.example.depthtank.data.NseMarketCalendar
import com.example.depthtank.data.TankHistoryPoint
import com.example.depthtank.data.TankUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralOverheated
import com.example.ui.theme.CyanLiquid
import com.example.ui.theme.DeepSlateWater
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketExtremesScreen(
    uiState: TankUiState,
    modifier: Modifier = Modifier
) {
    var selectedTypeFilter by remember { mutableStateOf(ExtremeTypeFilter.ALL) }
    var selectedViewMode by remember { mutableStateOf(ExtremesViewMode.EPISODES) }
    var selectedYear by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedEpisodeForDetail by remember { mutableStateOf<MacroExtremeEpisode?>(null) }
    var selectedPointForDetail by remember { mutableStateOf<TankHistoryPoint?>(null) }

    androidx.compose.runtime.LaunchedEffect(uiState.selectedUniverse) {
        selectedEpisodeForDetail = null
        selectedPointForDetail = null
    }

    val history = uiState.fullHistory
    val summary = remember(history) { ExtremesAnalyzer.summarize(history) }
    val allEpisodes = remember(history) { ExtremesAnalyzer.buildEpisodes(history) }

    val filteredEpisodes = remember(allEpisodes, selectedTypeFilter, selectedYear, searchQuery) {
        val cleanQuery = searchQuery.trim().lowercase()
        allEpisodes.filter { ep ->
            val matchesType = when (selectedTypeFilter) {
                ExtremeTypeFilter.ALL -> true
                ExtremeTypeFilter.CAPITULATION -> ep.type == MarketRegime.CAPITULATION
                ExtremeTypeFilter.EUPHORIA -> ep.type == MarketRegime.OVERHEATED
            }
            if (!matchesType) return@filter false

            val matchesYear = selectedYear == "All" ||
                ep.startDate.startsWith(selectedYear) ||
                ep.endDate.startsWith(selectedYear)
            if (!matchesYear) return@filter false

            if (cleanQuery.isNotEmpty()) {
                val formattedStart = NseMarketCalendar.formatDdMmmYyyy(ep.startDate).lowercase()
                val formattedEnd = NseMarketCalendar.formatDdMmmYyyy(ep.endDate).lowercase()
                ep.title.lowercase().contains(cleanQuery) ||
                    ep.startDate.contains(cleanQuery) ||
                    ep.endDate.contains(cleanQuery) ||
                    formattedStart.contains(cleanQuery) ||
                    formattedEnd.contains(cleanQuery)
            } else {
                true
            }
        }
    }

    val filteredDailyPoints = remember(history, selectedTypeFilter, selectedYear, searchQuery) {
        ExtremesAnalyzer.filterDailyLog(history, selectedTypeFilter, selectedYear, searchQuery)
    }

    val years = remember(history) {
        val yList = history.mapNotNull {
            if (it.date.length >= 4) it.date.substring(0, 4) else null
        }.distinct().sortedDescending()
        listOf("All") + yList
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("market_extremes_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Market Breadth Extremes",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${uiState.selectedUniverse.shortLabel} (${uiState.activeStocksCount} Base) • Historical Capitulation (<15%) & Euphoria (≥75%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Summary Telemetry Dashboard
        item {
            ExtremesSummaryCard(summary = summary, currentLevel = uiState.currentTankLevel)
        }

        // Mode Selector: Macro Episodes vs Daily Session Log
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ElevatedCardSurface,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                TabRow(
                    selectedTabIndex = selectedViewMode.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = CyanLiquid,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedViewMode.ordinal]),
                            color = CyanLiquid,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    ExtremesViewMode.entries.forEach { mode ->
                        val selected = selectedViewMode == mode
                        val countLabel = if (mode == ExtremesViewMode.EPISODES) {
                            "${filteredEpisodes.size}"
                        } else {
                            "${filteredDailyPoints.size}"
                        }
                        Tab(
                            selected = selected,
                            onClick = { selectedViewMode = mode },
                            text = {
                                Text(
                                    text = "${mode.label} ($countLabel)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) CyanLiquid else TextSecondary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("extremes_view_mode_${mode.name.lowercase(Locale.US)}")
                        )
                    }
                }
            }
        }

        // Search & Filter Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search dates (e.g. Mar 2020, Oct 2026, 81%)...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search extreme dates",
                            tint = CyanLiquid
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("clear_extremes_search_button")
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
                        .fillMaxWidth()
                        .testTag("extremes_search_input")
                )

                // Type Filter Chips: All, Capitulation, Euphoria
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExtremeTypeFilter.entries.forEach { type ->
                        val isSelected = selectedTypeFilter == type
                        val accentColor = when (type) {
                            ExtremeTypeFilter.ALL -> CyanLiquid
                            ExtremeTypeFilter.CAPITULATION -> EmeraldBullish
                            ExtremeTypeFilter.EUPHORIA -> CoralOverheated
                        }
                        val count = when (type) {
                            ExtremeTypeFilter.ALL -> summary.totalExtremeDays
                            ExtremeTypeFilter.CAPITULATION -> summary.totalCapitulationDays
                            ExtremeTypeFilter.EUPHORIA -> summary.totalEuphoriaDays
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTypeFilter = type },
                            label = {
                                Text(
                                    text = "${type.label} ($count)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                selectedLabelColor = accentColor,
                                containerColor = ElevatedCardSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = HairlineBorder,
                                selectedBorderColor = accentColor.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier.testTag("extreme_type_chip_${type.name.lowercase(Locale.US)}")
                        )
                    }
                }

                // Year Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    years.forEach { year ->
                        val isSelected = selectedYear == year
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedYear = year },
                            label = {
                                Text(
                                    text = year,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                                selected = isSelected,
                                borderColor = HairlineBorder,
                                selectedBorderColor = CyanLiquid.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("extreme_year_chip_${year.lowercase(Locale.US)}")
                        )
                    }
                }
            }
        }

        // Render based on selectedViewMode
        if (selectedViewMode == ExtremesViewMode.EPISODES) {
            if (filteredEpisodes.isEmpty()) {
                item {
                    EmptyExtremesCard(message = "No matching macro episodes found.")
                }
            } else {
                items(
                    items = filteredEpisodes,
                    key = { it.id }
                ) { episode ->
                    MacroEpisodeCard(
                        episode = episode,
                        onClick = { selectedEpisodeForDetail = episode }
                    )
                }
            }
        } else {
            if (filteredDailyPoints.isEmpty()) {
                item {
                    EmptyExtremesCard(message = "No matching daily extreme sessions found.")
                }
            } else {
                items(
                    items = filteredDailyPoints,
                    key = { it.date }
                ) { point ->
                    DailyExtremePointCard(
                        point = point,
                        onClick = { selectedPointForDetail = point }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Modal Sheet for Episode Deep Dive
    val episodeDetail = selectedEpisodeForDetail
    if (episodeDetail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedEpisodeForDetail = null },
            sheetState = sheetState,
            containerColor = ElevatedCardSurface,
            contentColor = TextPrimary
        ) {
            EpisodeDetailSheetContent(
                episode = episodeDetail,
                onSelectPoint = { pt ->
                    selectedPointForDetail = pt
                }
            )
        }
    }

    // Modal Sheet for Single Day Deep Dive
    val pointDetail = selectedPointForDetail
    if (pointDetail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedPointForDetail = null },
            sheetState = sheetState,
            containerColor = ElevatedCardSurface,
            contentColor = TextPrimary
        ) {
            PointDetailSheetContent(point = pointDetail)
        }
    }
}

@Composable
private fun ExtremesSummaryCard(
    summary: ExtremesSummary,
    currentLevel: Double
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("extremes_summary_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HISTORICAL BREADTH REGIMES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${summary.extremeDaysSharePct}% of all trading sessions reached extremes",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = GlassCardSurface,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (summary.isCurrentlyExtreme) CoralOverheated else CyanLiquid)
                    )
                    Text(
                        text = if (summary.isCurrentlyExtreme) "CURRENTLY EXTREME" else "CURRENT: ${String.format(Locale.US, "%.1f%%", currentLevel)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExtremeMetricTile(
                title = "Capitulation (<15%)",
                count = summary.totalCapitulationDays,
                subtext = "Deep value buy zones",
                accentColor = EmeraldBullish,
                modifier = Modifier.weight(1f)
            )
            ExtremeMetricTile(
                title = "Euphoria (≥75%)",
                count = summary.totalEuphoriaDays,
                subtext = "Overheated risk tops",
                accentColor = CoralOverheated,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ExtremeMetricTile(
    title: String,
    count: Int,
    subtext: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ElevatedCardSurface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                text = "$count Days",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Text(text = subtext, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun MacroEpisodeCard(
    episode: MacroExtremeEpisode,
    onClick: () -> Unit
) {
    val isCap = episode.type == MarketRegime.CAPITULATION
    val accent = if (isCap) EmeraldBullish else CoralOverheated
    val typeLabel = if (isCap) "CAPITULATION (<15%)" else "EUPHORIA (≥75%)"
    val extremeLabel = if (isCap) "Trough" else "Peak"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("macro_episode_card_${episode.id}"),
        shape = RoundedCornerShape(18.dp),
        color = GlassCardSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Type pill + Duration pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = ElevatedCardSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = "${episode.tradingDaysCount} Trading Sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            // Episode Title & Date Range
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${NseMarketCalendar.formatDdMmmYyyy(episode.startDate)} → ${NseMarketCalendar.formatDdMmmYyyy(episode.endDate)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = CyanLiquid
                )
            }

            // Telemetry Metric Tiles Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EpisodeStatTile(
                    label = "$extremeLabel Depth",
                    value = String.format(Locale.US, "%.2f%%", episode.extremeLevel),
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                EpisodeStatTile(
                    label = "Avg Depth",
                    value = String.format(Locale.US, "%.2f%%", episode.averageLevel),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                EpisodeStatTile(
                    label = "Avg Flowing",
                    value = "${episode.avgFlowingCount}/${episode.activeStocksCount}",
                    color = if (isCap) TextSecondary else CyanLiquid,
                    modifier = Modifier.weight(1f)
                )
            }

            // Visual Depth Fill Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ElevatedCardSurface)
                ) {
                    val fillFrac = (episode.extremeLevel / 100.0).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFrac)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(RoyalBlueLiquid, accent)
                                )
                            )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "0% Capitulation", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(text = "100% Euphoria", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }

            // Context Note
            Text(
                text = episode.contextNote,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EpisodeStatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ElevatedCardSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1)
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DailyExtremePointCard(
    point: TankHistoryPoint,
    onClick: () -> Unit
) {
    val isCap = point.tankLevel < 15.0
    val accent = if (isCap) EmeraldBullish else CoralOverheated
    val deltaPositive = point.dailyDelta >= 0.0
    val deltaColor = if (deltaPositive) EmeraldBullish else CoralOverheated

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("daily_extreme_point_${point.date}"),
        shape = RoundedCornerShape(16.dp),
        color = GlassCardSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Text(
                        text = NseMarketCalendar.formatDdMmmYyyy(point.date),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = if (isCap) "CAPITULATION" else "EUPHORIA",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = String.format(Locale.US, "%.2f%%", point.tankLevel),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = CyanLiquid,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${point.flowingCount}/${point.activeCount} flowing",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${point.highPressureCount} high pressure",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (point.highPressureCount > 50) EmeraldBullish else TextMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (deltaPositive) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                        contentDescription = null,
                        tint = deltaColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "%s%.2f%%", if (deltaPositive) "+" else "", point.dailyDelta),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = deltaColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeDetailSheetContent(
    episode: MacroExtremeEpisode,
    onSelectPoint: (TankHistoryPoint) -> Unit
) {
    val isCap = episode.type == MarketRegime.CAPITULATION
    val accent = if (isCap) EmeraldBullish else CoralOverheated

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .testTag("episode_detail_bottom_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "${NseMarketCalendar.formatDdMmmYyyy(episode.startDate)} → ${NseMarketCalendar.formatDdMmmYyyy(episode.endDate)} • ${episode.tradingDaysCount} Trading Days",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyanLiquid
                )
            }
        }

        Text(
            text = episode.contextNote,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        HorizontalDivider(color = SubtleDivider)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EpisodeStatTile("Extreme Depth", "${episode.extremeLevel}%", accent, Modifier.weight(1f))
            EpisodeStatTile("Average Depth", "${episode.averageLevel}%", TextPrimary, Modifier.weight(1f))
            EpisodeStatTile("Flowing Stocks", "${episode.avgFlowingCount}/${episode.activeStocksCount}", CyanLiquid, Modifier.weight(1f))
        }

        Text(
            text = "Trading Sessions in this Cycle (${episode.points.size})",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(episode.points.asReversed()) { pt ->
                DailyExtremePointCard(
                    point = pt,
                    onClick = { onSelectPoint(pt) }
                )
            }
        }
    }
}

@Composable
private fun PointDetailSheetContent(point: TankHistoryPoint) {
    val isCap = point.tankLevel < 15.0
    val accent = if (isCap) EmeraldBullish else CoralOverheated

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .testTag("point_detail_bottom_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = NseMarketCalendar.formatDdMmmYyyy(point.date),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (isCap) "Capitulation Washout Session" else "Euphoric Breadth Top Session",
                    style = MaterialTheme.typography.bodySmall,
                    color = accent
                )
            }
            Text(
                text = String.format(Locale.US, "%.2f%%", point.tankLevel),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = CyanLiquid,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(color = SubtleDivider)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EpisodeStatTile("Daily Delta", "${point.dailyDelta}%", if (point.dailyDelta >= 0) EmeraldBullish else CoralOverheated, Modifier.weight(1f))
            EpisodeStatTile("Flowing Stocks", "${point.flowingCount}/${point.activeCount}", CyanLiquid, Modifier.weight(1f))
            EpisodeStatTile("High Pressure", "${point.highPressureCount}", EmeraldBullish, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EpisodeStatTile("Avg Stock Score", "${point.avgScore} / 100", TextPrimary, Modifier.weight(1f))
            EpisodeStatTile("Below 50% Hole", "${point.activeCount - point.flowingCount}", TextSecondary, Modifier.weight(1f))
        }

        Text(
            text = if (isCap) {
                "Hydro-Dynamic Playbook: Extreme capitulation (<15%) signals peak broad-market despair. Historically, these moments provide the strongest multi-year risk-adjusted entry windows."
            } else {
                "Hydro-Dynamic Playbook: Extreme euphoria (≥75%) indicates overextended breadth where most constituents are near 52-Week Highs. Systematic investors tighten risk parameters and prepare for consolidation."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun EmptyExtremesCard(message: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(36.dp)
            )
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = "Adjust the type filter, select another year, or clear the search query.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
