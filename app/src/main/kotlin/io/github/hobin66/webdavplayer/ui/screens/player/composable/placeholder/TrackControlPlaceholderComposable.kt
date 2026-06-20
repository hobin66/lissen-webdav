package io.github.hobin66.webdavplayer.ui.screens.player.composable.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.lib.domain.SeekTime
import io.github.hobin66.webdavplayer.ui.extensions.formatTime
import io.github.hobin66.webdavplayer.ui.screens.player.composable.common.provideForwardIcon
import io.github.hobin66.webdavplayer.ui.screens.player.composable.common.provideReplayIcon
import io.github.hobin66.webdavplayer.viewmodel.SettingsViewModel

@Composable
fun TrackControlPlaceholderComposable(
  settingsViewModel: SettingsViewModel,
  modifier: Modifier = Modifier,
) {
  val seekTime by settingsViewModel.seekTime.observeAsState(SeekTime.Default)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      Slider(
        value = 0f,
        onValueChange = {},
        onValueChangeFinished = {},
        valueRange = 0f..1f,
        colors =
          SliderDefaults.colors(
            thumbColor = colorScheme.primary,
            activeTrackColor = colorScheme.primary,
          ),
        modifier = Modifier.fillMaxWidth(),
      )
    }

    Box(
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .offset(y = (-4).dp)
              .padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = 0.formatTime(true),
            style = typography.bodySmall,
            color = colorScheme.onBackground.copy(alpha = 0.6f),
          )
          Text(
            text = 0.formatTime(true),
            style = typography.bodySmall,
            color = colorScheme.onBackground.copy(alpha = 0.6f),
          )
        }
      }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .align(Alignment.BottomCenter),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = {},
          enabled = true,
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.player_control_previous_track),
            tint = colorScheme.onBackground,
            modifier = Modifier.size(36.dp),
          )
        }

        IconButton(onClick = {}) {
          Icon(
            imageVector = provideReplayIcon(seekTime),
            contentDescription = stringResource(R.string.player_control_rewind),
            tint = colorScheme.onBackground,
            modifier = Modifier.size(48.dp),
          )
        }

        IconButton(
          onClick = {},
          modifier = Modifier.size(72.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayCircleFilled,
            contentDescription = stringResource(R.string.player_control_play_pause),
            tint = colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
          )
        }

        IconButton(onClick = {}) {
          Icon(
            imageVector = provideForwardIcon(seekTime),
            contentDescription = stringResource(R.string.player_control_forward),
            tint = colorScheme.onBackground,
            modifier = Modifier.size(48.dp),
          )
        }

        IconButton(
          onClick = {},
          enabled = false,
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.player_control_next_track),
            tint = colorScheme.onBackground.copy(alpha = 0.3f),
            modifier = Modifier.size(36.dp),
          )
        }
      }
    }
  }
}
