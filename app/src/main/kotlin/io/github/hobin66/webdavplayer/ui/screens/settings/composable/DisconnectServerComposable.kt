package io.github.hobin66.webdavplayer.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.ui.navigation.AppNavigationService
import io.github.hobin66.webdavplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisconnectServerComposable(
  navController: AppNavigationService,
  viewModel: SettingsViewModel,
  enabled: Boolean = true,
) {
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    viewModel.refreshConnectionInfo()
  }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.6f)
        .clickable(enabled = enabled) {
          scope.launch {
            viewModel.logout {
              navController.showLogin()
            }
          }
        }.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      Text(
        text = stringResource(R.string.disconnect_from_server_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = colorScheme.error,
      )
    }
  }
}
