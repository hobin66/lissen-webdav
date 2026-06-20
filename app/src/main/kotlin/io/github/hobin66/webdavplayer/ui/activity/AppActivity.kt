package io.github.hobin66.webdavplayer.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import io.github.hobin66.webdavplayer.common.NetworkService
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.ui.navigation.AppLaunchAction
import io.github.hobin66.webdavplayer.ui.navigation.AppNavHost
import io.github.hobin66.webdavplayer.ui.navigation.AppNavigationService
import io.github.hobin66.webdavplayer.ui.navigation.CONTINUE_PLAYBACK
import io.github.hobin66.webdavplayer.ui.navigation.SHOW_DOWNLOADS
import io.github.hobin66.webdavplayer.ui.theme.WebdavPlayerTheme
import javax.inject.Inject

@AndroidEntryPoint
class AppActivity : ComponentActivity() {
  @Inject
  lateinit var preferences: WebdavPlayerPreferences

  @Inject
  lateinit var imageLoader: ImageLoader

  @Inject
  lateinit var networkService: NetworkService

  private lateinit var appNavigationService: AppNavigationService

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val colorScheme by preferences
        .colorSchemeFlow
        .collectAsState(initial = preferences.getColorScheme())

      val materialYou by preferences
        .materialYouFlow
        .collectAsState(initial = preferences.getMaterialYouColors())

      WebdavPlayerTheme(colorScheme, materialYou) {
        val navController = rememberNavController()
        appNavigationService = AppNavigationService(navController)

        AppNavHost(
          navController = navController,
          navigationService = appNavigationService,
          preferences = preferences,
          imageLoader = imageLoader,
          networkService = networkService,
          appLaunchAction = getLaunchAction(intent),
        )
      }
    }
  }

  private fun getLaunchAction(intent: Intent?) =
    when (intent?.action) {
      CONTINUE_PLAYBACK -> AppLaunchAction.CONTINUE_PLAYBACK
      SHOW_DOWNLOADS -> AppLaunchAction.MANAGE_DOWNLOADS
      else -> AppLaunchAction.DEFAULT
    }
}
