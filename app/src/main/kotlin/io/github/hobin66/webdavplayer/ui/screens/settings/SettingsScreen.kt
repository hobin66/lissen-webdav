package io.github.hobin66.webdavplayer.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.ui.navigation.AppNavigationService
import io.github.hobin66.webdavplayer.ui.screens.settings.advanced.AdvancedSettingsNavigationItemComposable
import io.github.hobin66.webdavplayer.ui.screens.settings.composable.ColorSchemeSettingsComposable
import io.github.hobin66.webdavplayer.ui.screens.settings.composable.GitHubLinkComposable
import io.github.hobin66.webdavplayer.ui.screens.settings.composable.LibraryOrderingSettingsComposable
import io.github.hobin66.webdavplayer.ui.screens.settings.composable.LicenseFooterComposable
import io.github.hobin66.webdavplayer.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
  onBack: () -> Unit,
  navController: AppNavigationService,
) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val host by viewModel.host.observeAsState()

  LaunchedEffect(Unit) {
    viewModel.refreshConnectionInfo()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_screen_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = colorScheme.onSurface,
              )
          }
        },
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxHeight(),
    content = { innerPadding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.connection_settings_title),
            description = stringResource(R.string.connection_settings_description),
            onclick = { navController.showConnectionSettings() },
          )

          ColorSchemeSettingsComposable(viewModel)

          LibraryOrderingSettingsComposable(viewModel)

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.download_settings_title),
            description = stringResource(R.string.download_settings_description),
            onclick = { navController.showCacheSettings() },
          )

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.settings_screen_advanced_preferences_title),
            description = stringResource(R.string.settings_screen_advanced_preferences_description),
            onclick = { navController.showAdvancedSettings() },
          )

          GitHubLinkComposable()
        }

        LicenseFooterComposable()
      }
    },
  )
}
