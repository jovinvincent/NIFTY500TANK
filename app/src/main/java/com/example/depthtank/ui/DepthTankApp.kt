package com.example.depthtank.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.depthtank.data.ChartAggregation
import com.example.depthtank.data.FlowFilter
import com.example.depthtank.data.StockTank
import com.example.depthtank.data.TankIndexUniverse
import com.example.depthtank.data.TankUiState
import com.example.depthtank.data.TankViewModel
import com.example.depthtank.data.TimeframeOption
import com.example.ui.theme.CyanLiquid
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

enum class DepthTankTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTagName: String
) {
    DASHBOARD("Dashboard", Icons.Rounded.WaterDrop, Icons.Outlined.WaterDrop, "nav_tab_dashboard"),
    EXTREMES("Extremes", Icons.Rounded.Whatshot, Icons.Outlined.Whatshot, "nav_tab_extremes"),
    TANKS("500 Tanks", Icons.Rounded.GridView, Icons.Outlined.GridView, "nav_tab_tanks"),
    ANALYTICS("Analytics", Icons.Rounded.Analytics, Icons.Outlined.Analytics, "nav_tab_analytics"),
    REPORT("Report", Icons.Rounded.Assessment, Icons.Outlined.Assessment, "nav_tab_report"),
    GUIDE("Guide", Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Outlined.MenuBook, "nav_tab_guide")
}

@Composable
fun DepthTankApp(
    viewModel: TankViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setForegroundActive(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setForegroundActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DepthTankRootContent(
        uiState = uiState,
        onSelectUniverse = viewModel::selectUniverse,
        onSelectTimeframe = viewModel::selectTimeframe,
        onSelectAggregation = viewModel::selectAggregation,
        onRetryArchive = viewModel::retryPre2016Archive,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSelectSector = viewModel::selectSector,
        onSelectFlowFilter = viewModel::selectFlowFilter,
        onOpenStockDetail = viewModel::openStockDetail,
        onDismissStockDetail = viewModel::closeStockDetail,
        onOpenInfoModal = { viewModel.setInfoModalVisible(true) },
        onDismissInfoModal = { viewModel.setInfoModalVisible(false) },
        onForceRefresh = viewModel::forceRefresh,
        onHoleThresholdChange = viewModel::updateHoleThreshold,
        onResetHoleThreshold = viewModel::resetHoleThreshold
    )
}

@Composable
fun DepthTankRootContent(
    uiState: TankUiState,
    onSelectUniverse: (TankIndexUniverse) -> Unit = {},
    onSelectTimeframe: (TimeframeOption) -> Unit,
    onSelectAggregation: (ChartAggregation) -> Unit = {},
    onRetryArchive: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onSelectSector: (String) -> Unit,
    onSelectFlowFilter: (FlowFilter) -> Unit,
    onOpenStockDetail: (StockTank) -> Unit,
    onDismissStockDetail: () -> Unit,
    onOpenInfoModal: () -> Unit,
    onDismissInfoModal: () -> Unit,
    onForceRefresh: () -> Unit,
    onHoleThresholdChange: (Float) -> Unit = {},
    onResetHoleThreshold: () -> Unit = {}
) {
    var activeTab by rememberSaveable { mutableStateOf(DepthTankTab.DASHBOARD) }

    BackHandler(enabled = activeTab != DepthTankTab.DASHBOARD) {
        activeTab = DepthTankTab.DASHBOARD
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 840.dp

        Scaffold(
            containerColor = ObsidianBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = GlassCardSurface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        DepthTankTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            val tabLabel = if (tab == DepthTankTab.TANKS) {
                                "${uiState.selectedUniverse.targetCount} Tanks"
                            } else {
                                tab.label
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tabLabel
                                    )
                                },
                                label = {
                                    Text(
                                        text = tabLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyanLiquid,
                                    selectedTextColor = CyanLiquid,
                                    indicatorColor = CyanLiquid.copy(alpha = 0.14f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTagName)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = GlassCardSurface
                    ) {
                        DepthTankTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            val tabLabel = if (tab == DepthTankTab.TANKS) {
                                "${uiState.selectedUniverse.targetCount} Tanks"
                            } else {
                                tab.label
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tabLabel
                                    )
                                },
                                label = {
                                    Text(
                                        text = tabLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CyanLiquid,
                                    selectedTextColor = CyanLiquid,
                                    indicatorColor = CyanLiquid.copy(alpha = 0.14f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTagName)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = if (useNavigationRail) {
                        Modifier.widthIn(max = 980.dp)
                    } else {
                        Modifier.widthIn(max = 680.dp)
                    }

                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CyanLiquid)
                        }
                    } else {
                        when (activeTab) {
                            DepthTankTab.DASHBOARD -> DashboardScreen(
                                uiState = uiState,
                                onSelectUniverse = onSelectUniverse,
                                onSelectTimeframe = onSelectTimeframe,
                                onSelectAggregation = onSelectAggregation,
                                onRetryArchive = onRetryArchive,
                                onOpenInfoModal = onOpenInfoModal,
                                onHoleThresholdChange = onHoleThresholdChange,
                                onResetHoleThreshold = onResetHoleThreshold,
                                modifier = contentModifier
                            )

                            DepthTankTab.EXTREMES -> MarketExtremesScreen(
                                uiState = uiState,
                                modifier = contentModifier
                            )

                            DepthTankTab.TANKS -> StockTanksExplorerScreen(
                                uiState = uiState,
                                onSearchQueryChange = onSearchQueryChange,
                                onSelectSector = onSelectSector,
                                onSelectFlowFilter = onSelectFlowFilter,
                                onOpenStockDetail = onOpenStockDetail,
                                onDismissStockDetail = onDismissStockDetail,
                                modifier = contentModifier
                            )

                            DepthTankTab.ANALYTICS -> AnalyticsSyncScreen(
                                uiState = uiState,
                                onForceRefresh = onForceRefresh,
                                onRetryArchive = onRetryArchive,
                                modifier = contentModifier
                            )

                            DepthTankTab.REPORT -> CycleReportScreen(
                                uiState = uiState,
                                onSelectUniverse = onSelectUniverse,
                                onForceRefresh = onForceRefresh,
                                modifier = contentModifier
                            )

                            DepthTankTab.GUIDE -> PhilosophyGuideScreen(
                                cycles = uiState.detectedCycles,
                                selectedUniverse = uiState.selectedUniverse,
                                activeStocksCount = uiState.activeStocksCount,
                                modifier = contentModifier
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isInfoModalVisible) {
        HydroDynamicInfoDialog(
            selectedUniverse = uiState.selectedUniverse,
            onDismiss = onDismissInfoModal
        )
    }
}
