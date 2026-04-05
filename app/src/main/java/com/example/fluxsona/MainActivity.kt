package com.example.fluxsona

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxsonaTheme {
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

                MainScreen(musicViewModel)
            }
        }
    }
}

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    val currentSong by viewModel.currentSong.collectAsState()
                    if (currentSong != null) {
                        MiniPlayer(
                            song = currentSong!!,
                            isPlaying = viewModel.isPlaying,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onExpand = { viewModel.isPlayerExpanded = true },
                            onSkipNext = { viewModel.skipNext() },
                            onSkipPrevious = { viewModel.skipPrevious() }
                        )
                    }
                    NavigationBar {
                        screens.forEachIndexed { index, screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(innerPadding),
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

        AnimatedVisibility(
            visible = viewModel.isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            modifier = Modifier.zIndex(10f)
        ) {
            ExpandedPlayer(
                viewModel = viewModel,
                onCollapse = { viewModel.isPlayerExpanded = false }
            )
            BackHandler {
                viewModel.isPlayerExpanded = false
            }
        }
    }
}
