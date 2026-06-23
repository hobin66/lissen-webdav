package io.github.hobin66.webdavplayer.ui.screens.settings.advanced

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.content.PlaybackProgressSyncDirection
import io.github.hobin66.webdavplayer.ui.navigation.AppNavigationService
import io.github.hobin66.webdavplayer.ui.screens.settings.composable.DisconnectServerComposable
import io.github.hobin66.webdavplayer.ui.screens.settings.composable.ServerInfoComposable
import io.github.hobin66.webdavplayer.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConnectionSettingsScreen(
  onBack: () -> Unit,
  navController: AppNavigationService,
) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val host by viewModel.host.observeAsState()
  val webdavRefreshInProgress by viewModel.webdavRefreshInProgress.observeAsState(false)
  val webdavRefreshProgress by viewModel.webdavRefreshProgress.observeAsState()
  val webdavRefreshMessageRes by viewModel.webdavRefreshMessageRes.observeAsState()

  val context = LocalContext.current
  var refreshConfirmationVisible by remember { mutableStateOf(false) }
  var syncDirectionSheetVisible by remember { mutableStateOf(false) }
  var pendingSyncDirection by remember { mutableStateOf<PlaybackProgressSyncDirection?>(null) }

  BackHandler(enabled = webdavRefreshInProgress) {}

  LaunchedEffect(webdavRefreshMessageRes) {
    val messageRes = webdavRefreshMessageRes ?: return@LaunchedEffect
    Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    viewModel.consumeWebdavRefreshMessage()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.connection_settings_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { onBack() },
            enabled = !webdavRefreshInProgress,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(R.string.common_back),
            )
          }
        },
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxHeight(),
  ) { innerPadding ->
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
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (host?.url?.isNotEmpty() == true) {
          ServerInfoComposable(viewModel = viewModel)
        }

        when (webdavRefreshInProgress) {
          true -> {
            WebdavRefreshProgressComposable(
              progressText =
                webdavRefreshProgress
                  ?.let { "${it.processedBooks}/${it.totalBooks}" }
                  ?: "0/0",
              progress = webdavRefreshProgress?.ratio ?: 0f,
            )
          }

          false -> {
            if (viewModel.canSyncPlaybackProgress()) {
              AdvancedSettingsSimpleItemComposable(
                title = stringResource(R.string.settings_sync_playback_progress_title),
                description = stringResource(R.string.settings_sync_playback_progress_description),
                onclick = { syncDirectionSheetVisible = true },
              )
            }

            AdvancedSettingsSimpleItemComposable(
              title = stringResource(R.string.settings_refresh_webdav_cache_title),
              description = stringResource(R.string.settings_refresh_webdav_cache_description),
              onclick = { refreshConfirmationVisible = true },
            )
          }
        }
      }

      DisconnectServerComposable(
        navController = navController,
        viewModel = viewModel,
        enabled = !webdavRefreshInProgress,
      )
    }
  }

  if (refreshConfirmationVisible) {
    AlertDialog(
      onDismissRequest = { refreshConfirmationVisible = false },
      title = { Text(text = stringResource(R.string.settings_refresh_webdav_cache_title)) },
      text = { Text(text = stringResource(R.string.settings_refresh_webdav_cache_confirm_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            refreshConfirmationVisible = false
            viewModel.refreshWebdavCache()
          },
        ) {
          Text(text = stringResource(android.R.string.ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { refreshConfirmationVisible = false }) {
          Text(text = stringResource(android.R.string.cancel))
        }
      },
    )
  }

  if (syncDirectionSheetVisible) {
    AlertDialog(
      onDismissRequest = { syncDirectionSheetVisible = false },
      title = { Text(text = stringResource(R.string.settings_sync_playback_progress_title)) },
      text = { Text(text = stringResource(R.string.settings_sync_playback_progress_choose_direction_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            syncDirectionSheetVisible = false
            pendingSyncDirection = PlaybackProgressSyncDirection.LOCAL_TO_REMOTE
          },
        ) {
          Text(text = stringResource(R.string.settings_sync_playback_progress_local_to_remote))
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            syncDirectionSheetVisible = false
            pendingSyncDirection = PlaybackProgressSyncDirection.REMOTE_TO_LOCAL
          },
        ) {
          Text(text = stringResource(R.string.settings_sync_playback_progress_remote_to_local))
        }
      },
    )
  }

  pendingSyncDirection?.let { direction ->
    AlertDialog(
      onDismissRequest = { pendingSyncDirection = null },
      title = {
        Text(
          text =
            when (direction) {
              PlaybackProgressSyncDirection.LOCAL_TO_REMOTE -> {
                stringResource(R.string.settings_sync_playback_progress_local_to_remote)
              }

              PlaybackProgressSyncDirection.REMOTE_TO_LOCAL -> {
                stringResource(R.string.settings_sync_playback_progress_remote_to_local)
              }
            },
        )
      },
      text = {
        Text(
          text =
            when (direction) {
              PlaybackProgressSyncDirection.LOCAL_TO_REMOTE -> {
                stringResource(R.string.settings_sync_playback_progress_local_to_remote_confirm_message)
              }

              PlaybackProgressSyncDirection.REMOTE_TO_LOCAL -> {
                stringResource(R.string.settings_sync_playback_progress_remote_to_local_confirm_message)
              }
            },
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            pendingSyncDirection = null
            viewModel.syncPlaybackProgress(direction)
          },
        ) {
          Text(text = stringResource(android.R.string.ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingSyncDirection = null }) {
          Text(text = stringResource(android.R.string.cancel))
        }
      },
    )
  }
}

@Composable
private fun WebdavRefreshProgressComposable(
  progressText: String,
  progress: Float,
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.Center,
    ) {
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
      )
      Text(
        text = progressText,
        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        textAlign = TextAlign.Center,
      )
    }
  }
}
