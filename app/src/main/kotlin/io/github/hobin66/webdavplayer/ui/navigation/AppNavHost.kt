package io.github.hobin66.webdavplayer.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil3.ImageLoader
import io.github.hobin66.webdavplayer.common.NetworkService
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.ui.screens.library.LibraryScreen
import io.github.hobin66.webdavplayer.ui.screens.login.LoginScreen
import io.github.hobin66.webdavplayer.ui.screens.player.PlayerScreen
import io.github.hobin66.webdavplayer.ui.screens.settings.SettingsScreen
import io.github.hobin66.webdavplayer.ui.screens.settings.advanced.AdvancedSettingsComposable
import io.github.hobin66.webdavplayer.ui.screens.settings.advanced.ConnectionSettingsScreen
import io.github.hobin66.webdavplayer.ui.screens.settings.advanced.SeekSettingsScreen
import io.github.hobin66.webdavplayer.ui.screens.settings.advanced.cache.CacheSettingsScreen
import io.github.hobin66.webdavplayer.ui.screens.settings.advanced.cache.CachedItemsSettingsScreen
import io.github.hobin66.webdavplayer.ui.screens.settings.advanced.cache.LibraryManageScreen

@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun AppNavHost(
  navController: NavHostController,
  preferences: WebdavPlayerPreferences,
  networkService: NetworkService,
  navigationService: AppNavigationService,
  imageLoader: ImageLoader,
  appLaunchAction: AppLaunchAction,
) {
  val hasCredentials by remember {
    mutableStateOf(
      preferences.hasCredentials(),
    )
  }

  val book = preferences.getPlayingItem()

  val startDestination =
    when {
      appLaunchAction == AppLaunchAction.MANAGE_DOWNLOADS -> {
        "$ROUTE_SETTINGS/cached_items"
      }

      hasCredentials.not() -> {
        ROUTE_LOGIN
      }

      appLaunchAction == AppLaunchAction.CONTINUE_PLAYBACK && book != null -> {
        buildPlayerRoute(book.id, book.title, book.subtitle, startInstantly = true)
      }

      else -> {
        ROUTE_LIBRARY
      }
    }

  val enterTransition: EnterTransition =
    slideInHorizontally(
      initialOffsetX = { it },
      animationSpec = tween(),
    ) + fadeIn(animationSpec = tween())

  val exitTransition: ExitTransition =
    slideOutHorizontally(
      targetOffsetX = { -it },
      animationSpec = tween(),
    ) + fadeOut(animationSpec = tween())

  val popEnterTransition: EnterTransition =
    slideInHorizontally(
      initialOffsetX = { -it },
      animationSpec = tween(),
    ) + fadeIn(animationSpec = tween())

  val popExitTransition: ExitTransition =
    slideOutHorizontally(
      targetOffsetX = { it },
      animationSpec = tween(),
    ) + fadeOut(animationSpec = tween())

  Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
    NavHost(
      navController = navController,
      startDestination = startDestination,
    ) {
      composable(
        route = "settings_screen/cached_items",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        CachedItemsSettingsScreen(
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
          imageLoader = imageLoader,
        )
      }
      composable(
        route = "settings_screen/cache_settings",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        CacheSettingsScreen(
          navController = navigationService,
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
        )
      }
      composable(
        route = "settings_screen/library_manage",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        LibraryManageScreen(
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
          imageLoader = imageLoader,
        )
      }
      composable(
        route = "library_screen",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        LibraryScreen(
          navController = navigationService,
          imageLoader = imageLoader,
          networkService = networkService,
        )
      }

      composable(
        route = "player_screen/{bookId}?bookTitle={bookTitle}&bookSubtitle={bookSubtitle}&startInstantly={startInstantly}",
        arguments =
          listOf(
            navArgument("bookId") { type = NavType.StringType },
            navArgument("bookTitle") {
              type = NavType.StringType
              nullable = true
            },
            navArgument("bookSubtitle") {
              type = NavType.StringType
              nullable = true
            },
            navArgument("startInstantly") {
              type = NavType.BoolType
              nullable = false
            },
          ),
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) { navigationStack ->
        val bookId = navigationStack.arguments?.getString("bookId") ?: return@composable
        val bookTitle = navigationStack.arguments?.getString("bookTitle") ?: ""
        val bookSubtitle = navigationStack.arguments?.getString("bookSubtitle")
        val startInstantly = navigationStack.arguments?.getBoolean("startInstantly")

        PlayerScreen(
          navController = navigationService,
          imageLoader = imageLoader,
          bookId = bookId,
          bookTitle = bookTitle,
          bookSubtitle = bookSubtitle,
          playInstantly = startInstantly ?: false,
        )
      }

      composable(
        route = "login_screen",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        LoginScreen(navigationService)
      }

      composable(
        route = "settings_screen",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        SettingsScreen(
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
          navController = navigationService,
        )
      }

      composable(
        route = "settings_screen/connection_settings",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        ConnectionSettingsScreen(
          navController = navigationService,
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
        )
      }

      composable(
        route = "settings_screen/advanced_settings",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        AdvancedSettingsComposable(
          navController = navigationService,
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
        )
      }

      composable(
        route = "settings_screen/seek_settings",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        SeekSettingsScreen(
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
        )
      }
    }
  }
}
