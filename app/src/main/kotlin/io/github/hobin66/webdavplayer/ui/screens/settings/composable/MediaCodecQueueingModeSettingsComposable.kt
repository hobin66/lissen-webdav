package io.github.hobin66.webdavplayer.ui.screens.settings.composable

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.playback.MediaCodecQueueingMode
import io.github.hobin66.webdavplayer.viewmodel.SettingsViewModel

@Composable
fun MediaCodecQueueingModeSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }
  val queueingMode by viewModel.mediaCodecQueueingMode.observeAsState(MediaCodecQueueingMode.AUTOMATIC)

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { expanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.settings_screen_media_codec_queueing_mode_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = queueingMode.toItem(context),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (expanded) {
    CommonSettingsItemComposable(
      items = MediaCodecQueueingMode.entries.map { it.toSettingsItem(context) },
      selectedItem = queueingMode.toSettingsItem(context),
      onDismissRequest = { expanded = false },
      onItemSelected = { item ->
        MediaCodecQueueingMode
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferMediaCodecQueueingMode(it) }
      },
    )
  }
}

private fun MediaCodecQueueingMode.toSettingsItem(context: Context): CommonSettingsItem =
  CommonSettingsItem(this.name, this.toItem(context), null)

private fun MediaCodecQueueingMode.toItem(context: Context): String =
  when (this) {
    MediaCodecQueueingMode.AUTOMATIC -> context.getString(R.string.media_codec_queueing_mode_automatic)
    MediaCodecQueueingMode.FORCE_SYNCHRONOUS -> context.getString(R.string.media_codec_queueing_mode_force_synchronous)
    MediaCodecQueueingMode.FORCE_ASYNCHRONOUS -> context.getString(R.string.media_codec_queueing_mode_force_asynchronous)
  }
