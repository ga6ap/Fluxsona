package com.example.fluxsona

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.example.fluxsona.ui.components.PlayerValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.graphics.graphicsLayer
import android.view.WindowManager
import com.example.fluxsona.ui.MusicViewModel
import com.example.fluxsona.ui.components.ExpandedPlayer
import com.example.fluxsona.ui.components.MiniPlayer
import com.example.fluxsona.ui.components.Screen
import com.example.fluxsona.ui.screens.HomeScreen
import com.example.fluxsona.ui.screens.LibraryScreen
import com.example.fluxsona.ui.screens.SearchScreen
import com.example.fluxsona.ui.screens.SettingsScreen
import com.example.fluxsona.ui.theme.FluxsonaTheme

class MainActivity : ComponentActivity() {
    private val intentData = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        
        intentData.value = intent?.data
        
        setContent {
            val context = LocalContext.current
            val appContainer = (context.applicationContext as FluxsonaApplication).container
            val musicViewModel: MusicViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return MusicViewModel(appContainer.repository) as T
                    }
                }
            )

            val darkTheme = when (musicViewModel.darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            FluxsonaTheme(darkTheme = darkTheme) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Handle permission result if needed
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    musicViewModel.initController(context)
                }

                LaunchedEffect(intentData.value) {
                    val uri = intentData.value ?: return@LaunchedEffect
                    if (uri.path?.endsWith(".flxn") == true || intent?.type == "application/octet-stream" || intent?.type == "application/x-fluxsona" || uri.toString().contains(".flxn")) {
                        musicViewModel.importData(context, uri)
                        intentData.value = null
                    }
                }

                MainScreen(musicViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentData.value = intent.data
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Search, Screen.Library, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val pagerState = rememberPagerState(pageCount = { screens.size })
    val scope = rememberCoroutineScope()

    // Sync Pager with NavController
    LaunchedEffect(currentDestination) {
        val index = screens.indexOfFirst { it.route == currentDestination?.route }
        if (index != -1 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    // Sync NavController with Pager
    LaunchedEffect(pagerState.currentPage) {
        val targetRoute = screens[pagerState.currentPage].route
        if (currentDestination?.route != targetRoute) {
            try {
                navController.graph
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            } catch (e: IllegalStateException) {}
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error messages/backups status in snackbar
    LaunchedEffect(viewModel.errorTrigger) {
        if (viewModel.errorTrigger > 0) {
            viewModel.errorMessage?.let {
                delay(50) // Small delay to allow previous snackbar to clear and re-trigger animation
                snackbarHostState.showSnackbar(it)
            }
        }
    }

    // Player draggable state
    val currentSong by viewModel.currentSong.collectAsState()
    val density = LocalDensity.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = LocalContext.current
    
    // Update Dialog
    viewModel.updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update Available: v${info.versionName}") },
            text = {
                Column {
                    if (info.releaseNotes != null) {
                        Text(info.releaseNotes)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Backup Recommended", 
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "It's always safe to back up your data before an update.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.exportData(context, includeFiles = false) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    enabled = !viewModel.isDownloadingUpdate
                                ) {
                                    Text("Metadata", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.exportData(context, includeFiles = true) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    enabled = !viewModel.isDownloadingUpdate
                                ) {
                                    Text("Full Sync", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Would you like to download and install the new update?")
                    if (viewModel.isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { viewModel.updateProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(viewModel.updateProgress * 100).toInt()}%",
                            modifier = Modifier.align(Alignment.End),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.downloadAndInstallUpdate(context) },
                    enabled = !viewModel.isDownloadingUpdate
                ) {
                    Text(if (viewModel.isDownloadingUpdate) "Downloading..." else "Update")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissUpdate() },
                    enabled = !viewModel.isDownloadingUpdate
                ) {
                    Text("Later")
                }
            }
        )
    }

    // Track the bottom navigation height to position the mini player correctly above it
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                        if (snackbarHostState.currentSnackbarData != null) {
                            snackbarHostState.currentSnackbarData?.dismiss()
                        }
                        // Also clear focus globally on any tap
                        focusManager.clearFocus()
                    }
                }
            }
        }
    ) {
        val fullHeight = maxHeight
        val miniPlayerHeight = 72.dp
        
        val expandedOffset = 0f
        // Position the mini player exactly above the bottom navigation bar
        val collapsedOffsetPx = with(density) { (fullHeight - bottomBarHeight - miniPlayerHeight).toPx() }

        val anchors = remember(collapsedOffsetPx) {
            DraggableAnchors {
                PlayerValue.Expanded at expandedOffset
                PlayerValue.Collapsed at collapsedOffsetPx
            }
        }

        val playerState = remember(anchors) {
            AnchoredDraggableState(
                initialValue = if (viewModel.isPlayerExpanded) PlayerValue.Expanded else PlayerValue.Collapsed,
                anchors = anchors,
                positionalThreshold = { distance: Float -> distance * 0.3f },
                velocityThreshold = { with(density) { 100.dp.toPx() } },
                snapAnimationSpec = spring(),
                decayAnimationSpec = exponentialDecay()
            )
        }

        LaunchedEffect(viewModel.isPlayerExpanded) {
            if (viewModel.isPlayerExpanded && playerState.currentValue == PlayerValue.Collapsed) {
                playerState.animateTo(PlayerValue.Expanded)
            } else if (!viewModel.isPlayerExpanded && playerState.currentValue == PlayerValue.Expanded) {
                playerState.animateTo(PlayerValue.Collapsed)
            }
        }

        LaunchedEffect(playerState.currentValue) {
            viewModel.isPlayerExpanded = playerState.currentValue == PlayerValue.Expanded
        }

        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.onGloballyPositioned { 
                        bottomBarHeight = with(density) { it.size.height.toDp() }
                    }
                ) {
                    screens.forEachIndexed { index, screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = stringResource(screen.labelRes)) },
                            label = { Text(stringResource(screen.labelRes)) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            val extraPadding = if (currentSong != null) 72.dp else 0.dp
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(
                    bottom = innerPadding.calculateBottomPadding() + extraPadding,
                    top = innerPadding.calculateTopPadding()
                ),
                beyondViewportPageCount = 3
            ) { page ->
                when (screens[page]) {
                    Screen.Home -> HomeScreen(viewModel)
                    Screen.Search -> SearchScreen(viewModel)
                    Screen.Library -> LibraryScreen(viewModel)
                    Screen.Settings -> SettingsScreen(viewModel)
                }
            }

            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.size(0.dp) 
            ) {
                composable(Screen.Home.route) {}
                composable(Screen.Search.route) {}
                composable(Screen.Library.route) {}
                composable(Screen.Settings.route) {}
            }
        }

        if (currentSong != null) {
            val offsetPx = if (playerState.offset.isNaN()) collapsedOffsetPx else playerState.offset
            val progress = (offsetPx / collapsedOffsetPx).coerceIn(0f, 1f)
            
            // Animate height to avoid covering bottom navigation buttons when collapsed
            val currentHeight = lerp(fullHeight, miniPlayerHeight, progress)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeight)
                    .offset { IntOffset(0, offsetPx.roundToInt()) }
                    .anchoredDraggable(playerState, Orientation.Vertical)
                    .zIndex(10f)
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - progress }
                ) {
                    if (progress < 0.99f) {
                        ExpandedPlayer(
                            viewModel = viewModel,
                            onCollapse = { scope.launch { playerState.animateTo(PlayerValue.Collapsed) } }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .graphicsLayer { alpha = progress }
                ) {
                    if (progress > 0.01f) {
                        MiniPlayer(
                            viewModel = viewModel,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onExpand = { scope.launch { playerState.animateTo(PlayerValue.Expanded) } },
                            onSkipNext = { viewModel.skipNext() },
                            onSkipPrevious = { viewModel.skipPrevious() }
                        )
                    }
                }
            }

            BackHandler(enabled = viewModel.isPlayerExpanded || viewModel.isQueueMaximized) {
                if (viewModel.isQueueMaximized) {
                    viewModel.isQueueMaximized = false
                } else if (viewModel.isPlayerExpanded) {
                    scope.launch { playerState.animateTo(PlayerValue.Collapsed) }
                }
            }
        }

        // Display Snackbar above the mini-player with a custom, slower dismissal animation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f) // Ensure it's above everything
        ) {
            val currentData = snackbarHostState.currentSnackbarData
            AnimatedContent(
                targetState = currentData,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith 
                    (fadeOut(animationSpec = tween(1200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(1200)))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (currentSong != null && !viewModel.isPlayerExpanded) miniPlayerHeight + bottomBarHeight + 8.dp else bottomBarHeight + 8.dp),
                label = "SnackbarAnimation"
            ) { data ->
                if (data != null) {
                    Snackbar(data)
                }
            }
        }
    }
}
