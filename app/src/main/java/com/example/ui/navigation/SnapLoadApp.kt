package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.admin.AdminManager
import com.example.data.local.SettingsManager
import com.example.data.remote.AppAccessState
import com.example.data.remote.RemoteConfigManager
import com.example.data.repository.DownloadRepository
import com.example.downloader.DownloadEngine
import com.example.domain.model.DownloadStatus
import com.example.ui.about.AboutScreen
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.components.SalawatFloatingBanner
import com.example.ui.control.ForceUpdateScreen
import com.example.ui.control.MaintenanceScreen
import com.example.ui.downloads.DownloadsScreen
import com.example.ui.downloads.DownloadsViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.legal.LegalScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.player.MediaPlayerScreen
import com.example.ui.privacy.PrivacyScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.splash.SplashScreen
import kotlinx.coroutines.launch

@Composable
fun SnapLoadApp(
    settingsManager: SettingsManager,
    downloadRepository: DownloadRepository,
    downloadEngine: DownloadEngine,
    remoteConfigManager: RemoteConfigManager,
    initialSharedUrl: String? = null
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val firstLaunchDone by settingsManager.firstLaunchDoneFlow.collectAsState(initial = false)
    val liveProgressMap by downloadEngine.liveProgress.collectAsState()
    val activeDownloadsCount = liveProgressMap.values.count { it.status == DownloadStatus.DOWNLOADING }

    val accessState by remoteConfigManager.accessState.collectAsState()
    val isCheckingConfig by remoteConfigManager.isChecking.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val adminManager = remember { AdminManager(context, remoteConfigManager) }
    var isAdminBypassed by remember { mutableStateOf(adminManager.isAdminBypassed) }

    // Periodically poll server status every 25 seconds so any active user is blocked immediately!
    LaunchedEffect(Unit) {
        remoteConfigManager.recordAppLaunch()
        while (true) {
            kotlinx.coroutines.delay(25_000)
            try {
                remoteConfigManager.checkRemoteStatus()
            } catch (e: Exception) {
                // Ignore transient network errors
            }
        }
    }

    val homeViewModel = remember {
        HomeViewModel(downloadRepository, downloadEngine)
    }

    val downloadsViewModel = remember {
        DownloadsViewModel(downloadRepository, downloadEngine)
    }

    // Handle shared URL from external app
    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            homeViewModel.onUrlChanged(initialSharedUrl)
            homeViewModel.analyzeUrl(initialSharedUrl)
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Check remote access state (Maintenance or Force Update)
    if (!isAdminBypassed) {
        when (val state = accessState) {
            is AppAccessState.Maintenance -> {
                MaintenanceScreen(
                    title = state.title,
                    message = state.message,
                    isChecking = isCheckingConfig,
                    onRetry = {
                        scope.launch {
                            remoteConfigManager.checkRemoteStatus()
                        }
                    },
                    onAdminLogin = {
                        isAdminBypassed = true
                        adminManager.isAdminBypassed = true
                        navController.navigate(Screen.Admin.route)
                    }
                )
                return
            }
            is AppAccessState.ForceUpdate -> {
                ForceUpdateScreen(
                    title = state.title,
                    message = state.message,
                    updateUrl = state.updateUrl,
                    latestVersion = state.latestVersion,
                    isChecking = isCheckingConfig,
                    onRetry = {
                        scope.launch {
                            remoteConfigManager.checkRemoteStatus()
                        }
                    },
                    onAdminLogin = {
                        isAdminBypassed = true
                        adminManager.isAdminBypassed = true
                        navController.navigate(Screen.Admin.route)
                    }
                )
                return
            }
            is AppAccessState.Allowed -> {
                // Proceed to normal application flow
            }
        }
    }

    val bottomBarRoutes = listOf(Screen.Home.route, Screen.Downloads.route, Screen.Settings.route)
    val shouldShowBottomBar = currentRoute in bottomBarRoutes

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (isAdminBypassed && accessState !is AppAccessState.Allowed) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.Admin.route) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ وضع المدير مفعل: التطبيق متوقف عن المستخدمين",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "لوحة التحكم ⚙️",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (shouldShowBottomBar) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 8.dp
                    ) {
                        // Home
                        val isHomeSelected = currentRoute == Screen.Home.route
                        NavigationBarItem(
                            selected = isHomeSelected,
                            onClick = {
                                if (!isHomeSelected) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isHomeSelected) Icons.Default.Home else Icons.Outlined.Home,
                                    contentDescription = stringResource(R.string.nav_home)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_home)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )

                        // Downloads
                        val isDownloadsSelected = currentRoute == Screen.Downloads.route
                        NavigationBarItem(
                            selected = isDownloadsSelected,
                            onClick = {
                                if (!isDownloadsSelected) {
                                    navController.navigate(Screen.Downloads.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                BadgedBox(badge = {
                                    if (activeDownloadsCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(activeDownloadsCount.toString())
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isDownloadsSelected) Icons.Default.Download else Icons.Outlined.Download,
                                        contentDescription = stringResource(R.string.nav_downloads)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_downloads)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )

                        // Settings
                        val isSettingsSelected = currentRoute == Screen.Settings.route
                        NavigationBarItem(
                            selected = isSettingsSelected,
                            onClick = {
                                if (!isSettingsSelected) {
                                    navController.navigate(Screen.Settings.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSettingsSelected) Icons.Default.Settings else Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.nav_settings)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_settings)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Splash Screen
                composable(Screen.Splash.route) {
                    SplashScreen(
                        onSplashFinished = {
                            val destination = if (firstLaunchDone) Screen.Home.route else Screen.Onboarding.route
                            navController.navigate(destination) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }

                // Onboarding Screen
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        onGetStarted = {
                            scope.launch {
                                settingsManager.setFirstLaunchDone(true)
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                // Home Screen
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToDownloads = {
                            navController.navigate(Screen.Downloads.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenMedia = { downloadId ->
                            navController.navigate(Screen.Player.createRoute(downloadId))
                        }
                    )
                }

                // Downloads Screen
                composable(Screen.Downloads.route) {
                    DownloadsScreen(
                        viewModel = downloadsViewModel,
                        onNavigateToHome = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenMedia = { downloadId ->
                            navController.navigate(Screen.Player.createRoute(downloadId))
                        }
                    )
                }

                // Settings Screen
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        downloadRepository = downloadRepository,
                        remoteConfigManager = remoteConfigManager,
                        onNavigateToAbout = { navController.navigate(Screen.About.route) },
                        onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                        onNavigateToLegal = { navController.navigate(Screen.Legal.route) },
                        onNavigateToAdmin = { navController.navigate(Screen.Admin.route) }
                    )
                }

                // Admin Dashboard Screen
                composable(Screen.Admin.route) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val adminManager = remember { AdminManager(context, remoteConfigManager) }
                    BackHandler {
                        navController.popBackStack()
                    }
                    AdminDashboardScreen(
                        adminManager = adminManager,
                        remoteConfigManager = remoteConfigManager,
                        onBack = { navController.popBackStack() }
                    )
                }

                // Media Player Screen
                composable(
                    route = Screen.Player.route,
                    arguments = listOf(navArgument("downloadId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val downloadId = backStackEntry.arguments?.getLong("downloadId") ?: 0L
                    var downloadEntity by remember { mutableStateOf<com.example.data.local.DownloadEntity?>(null) }

                    LaunchedEffect(downloadId) {
                        downloadEntity = downloadRepository.getDownloadById(downloadId)
                    }

                    BackHandler {
                        navController.popBackStack()
                    }

                    MediaPlayerScreen(
                        download = downloadEntity,
                        onBack = { navController.popBackStack() }
                    )
                }

                // About Screen
                composable(Screen.About.route) {
                    BackHandler {
                        navController.popBackStack()
                    }
                    AboutScreen(onBack = { navController.popBackStack() })
                }

                // Privacy Screen
                composable(Screen.Privacy.route) {
                    BackHandler {
                        navController.popBackStack()
                    }
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }

                // Legal Screen
                composable(Screen.Legal.route) {
                    BackHandler {
                        navController.popBackStack()
                    }
                    LegalScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        // Salawat Floating Banner overlay on top
        SalawatFloatingBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
    }
}
