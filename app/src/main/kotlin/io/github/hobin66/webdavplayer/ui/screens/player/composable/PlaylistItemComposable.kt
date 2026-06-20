package io.github.hobin66.webdavplayer.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter

@Composable
fun PlaylistItemComposable(
  track: PlayingChapter,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier,
  isCached: Boolean,
) {
  val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .padding(start = 6.dp)
        .padding(end = 4.dp)
        .padding(vertical = 2.dp)
        .clickable(
          onClick = onClick,
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
        ),
  ) {
    when (isSelected) {
      true -> {
        Icon(
          imageVector = Icons.Outlined.Audiotrack,
          contentDescription = stringResource(R.string.player_screen_library_playing_title),
          modifier = Modifier.size(16.dp),
        )
      }

      false -> {
        Spacer(modifier = Modifier.size(16.dp))
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    Text(
      text = track.title,
      style = MaterialTheme.typography.titleSmall,
      color =
        when (track.available) {
          true -> colorScheme.onBackground
          false -> colorScheme.onBackground.copy(alpha = 0.4f)
        },
      overflow = TextOverflow.Ellipsis,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      modifier =
        Modifier
          .weight(1f)
          .padding(end = 12.dp),
    )

    if (isCached) {
      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.available_offline_filled),
        contentDescription = stringResource(R.string.player_control_available_offline),
        modifier =
          Modifier
            .padding(horizontal = 6.dp * fontScale)
            .size(12.dp),
        tint =
          colorScheme.onBackground.copy(
            alpha = if (isSelected) 0.6f else 0.4f,
          ),
      )
    }
  }
}
