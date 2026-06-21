package io.github.hobin66.webdavplayer.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerInfoComposable(viewModel: SettingsViewModel) {
  var connectionInfoExpanded by remember { mutableStateOf(false) }
  var showPassword by remember { mutableStateOf(false) }

  val host by viewModel.host.observeAsState()
  val username by viewModel.username.observeAsState()
  val rootPath by viewModel.rootPath.observeAsState()
  val password by viewModel.password.observeAsState()
  val serverVersion by viewModel.serverVersion.observeAsState()

  LaunchedEffect(Unit) {
    viewModel.refreshConnectionInfo()
  }

  LaunchedEffect(connectionInfoExpanded) {
    if (!connectionInfoExpanded) {
      showPassword = false
    }
  }

  androidx.compose.foundation.layout.Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { connectionInfoExpanded = true }
        .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.settings_screen_server_connection),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )

      host?.let {
        Text(
          text = it.url,
          style = typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    IconButton(
      onClick = {
        connectionInfoExpanded = true
        // navController.showLogin()
        // viewModel.logout()
      },
    ) {
      Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = null,
      )
    }
  }

  if (connectionInfoExpanded) {
    ModalBottomSheet(
      containerColor = MaterialTheme.colorScheme.background,
      onDismissRequest = { connectionInfoExpanded = false },
      content = {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
              .padding(horizontal = 16.dp),
        ) {
          host?.url?.takeIf { it.isNotBlank() }?.let {
            InfoRow(
              label = stringResource(R.string.hint_server_url_input),
              value = it,
            )
            HorizontalDivider()
          }

          rootPath?.takeIf { it.isNotBlank() }?.let {
            InfoRow(
              label = stringResource(R.string.login_screen_root_path_input),
              value = it,
            )

            HorizontalDivider()
          }

          username?.takeIf { it.isNotBlank() }?.let {
            InfoRow(
              label = stringResource(R.string.settings_screen_connected_as_title),
              value = it,
            )

            HorizontalDivider()
          }

          password?.takeIf { it.isNotEmpty() }?.let {
            InfoRow(
              label = stringResource(R.string.login_screen_password_input),
              value = if (showPassword) it else maskPassword(it),
              action = {
                IconButton(onClick = { showPassword = !showPassword }) {
                  Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription =
                      stringResource(
                        if (showPassword) {
                          R.string.settings_screen_hide_password_hint
                        } else {
                          R.string.login_screen_show_password_hint
                        },
                      ),
                  )
                }
              },
            )

            HorizontalDivider()
          }

          serverVersion?.takeIf { it.isNotBlank() }?.let {
            InfoRow(
              label = stringResource(R.string.settings_screen_server_version),
              value = it,
            )
          }
        }
      },
    )
  }
}

@Composable
fun InfoRow(
  label: String,
  value: String,
  action: (@Composable () -> Unit)? = null,
) {
  ListItem(
    headlineContent = {
      Column(
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = label,
          style = typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = value,
          style = typography.bodyLarge,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    },
    trailingContent = action,
  )
}

private fun maskPassword(password: String): String = "\u2022".repeat(password.length.coerceAtLeast(8))
