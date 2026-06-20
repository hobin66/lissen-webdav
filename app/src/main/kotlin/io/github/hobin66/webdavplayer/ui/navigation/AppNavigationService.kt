package io.github.hobin66.webdavplayer.ui.navigation

import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

class AppNavigationService(
  private val host: NavHostController,
) {
  fun canGoBack(): Boolean = host.previousBackStackEntry != null

  fun goBack(): Boolean = host.popBackStack()

  fun showLibrary(clearHistory: Boolean = false) {
    host.navigate(ROUTE_LIBRARY) {
      val startId = host.graph.findStartDestination().id
      popUpTo(startId) { inclusive = clearHistory }

      launchSingleTop = true
    }
  }

  fun showPlayer(
    bookId: String,
    bookTitle: String,
    bookSubtitle: String?,
    startInstantly: Boolean = false,
  ) {
    val route =
      buildString {
        append("$ROUTE_PLAYER/$bookId")
        append("?bookTitle=${Uri.encode(bookTitle)}")
        append("&bookSubtitle=${Uri.encode(bookSubtitle ?: "")}")
        append("&startInstantly=$startInstantly")
      }
    host.navigate(route) { launchSingleTop = true }
  }

  fun showSettings() = host.navigate(ROUTE_SETTINGS)

  fun showConnectionSettings() = host.navigate("$ROUTE_SETTINGS/connection_settings")

  fun showSeekSettings() = host.navigate("$ROUTE_SETTINGS/seek_settings")

  fun showCachedItemsSettings() = host.navigate("$ROUTE_SETTINGS/cached_items")

  fun showLibraryManageSettings() = host.navigate("$ROUTE_SETTINGS/library_manage")

  fun showCacheSettings() = host.navigate("$ROUTE_SETTINGS/cache_settings")

  fun showAdvancedSettings() = host.navigate("$ROUTE_SETTINGS/advanced_settings")

  fun showLogin() {
    host.navigate(ROUTE_LOGIN) {
      val startId = host.graph.findStartDestination().id
      popUpTo(startId) { inclusive = true }

      launchSingleTop = true
    }
  }
}
