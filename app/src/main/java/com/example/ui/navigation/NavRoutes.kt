package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
    object About : Screen("about")
    object Privacy : Screen("privacy")
    object Legal : Screen("legal")
    object Player : Screen("player/{downloadId}") {
        fun createRoute(downloadId: Long) = "player/$downloadId"
    }
}
