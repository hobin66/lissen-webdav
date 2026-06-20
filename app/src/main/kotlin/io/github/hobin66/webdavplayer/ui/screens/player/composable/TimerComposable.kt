package io.github.hobin66.webdavplayer.ui.screens.player.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.common.withHaptic
import io.github.hobin66.webdavplayer.lib.domain.CurrentItemTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerStopMode
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.lib.domain.LibraryType.LIBRARY
import io.github.hobin66.webdavplayer.lib.domain.TimerOption
import io.github.hobin66.webdavplayer.ui.components.slider.SleepTimerSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerComposable(
  currentOption: TimerOption?,
  libraryType: LibraryType,
  preferredStopMode: DurationTimerStopMode,
  onOptionSelected: (TimerOption?) -> Unit,
  onPreferredStopModeSelected: (DurationTimerStopMode) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val view = LocalView.current
  val context = LocalContext.current
  val durationStopMode = resolveDurationTimerStopMode(currentOption, preferredStopMode)

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.timer_title),
          style = typography.bodyLarge,
        )

        SleepTimerSlider(
          libraryType = libraryType,
          context = context,
          option = currentOption,
          durationStopMode = durationStopMode,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          onUpdate = {
            onOptionSelected(it)
          },
        )

        val currentDurationOption = currentOption as? DurationTimerOption
        if (currentDurationOption != null) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text =
                when (libraryType) {
                  LIBRARY -> stringResource(R.string.timer_option_finish_current_chapter_after_time)
                  else -> stringResource(R.string.timer_option_finish_current_item_after_time)
                },
              style = typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )

            Switch(
              checked = durationStopMode == DurationTimerStopMode.AFTER_CURRENT_ITEM,
              onCheckedChange = { enabled ->
                val nextStopMode =
                  if (enabled) {
                    DurationTimerStopMode.AFTER_CURRENT_ITEM
                  } else {
                    DurationTimerStopMode.IMMEDIATE
                  }

                onPreferredStopModeSelected(nextStopMode)
                onOptionSelected(currentDurationOption.copy(stopMode = nextStopMode))
              },
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          OptionPresetDurations.forEach { value ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  onOptionSelected(
                    value?.let { DurationTimerOption(duration = it, stopMode = durationStopMode) },
                  )
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (currentOption.isSameDuration(value)) {
                      colorScheme.primary
                    } else {
                      colorScheme.surfaceContainer
                    },
                  contentColor =
                    if (currentOption.isSameDuration(value)) {
                      colorScheme.onPrimary
                    } else {
                      colorScheme.onSurfaceVariant
                    },
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              if (value == null) {
                val fontSize = typography.labelMedium.fontSize
                val iconSize = with(LocalDensity.current) { fontSize.toDp() } * 1.5f

                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = null,
                  modifier = Modifier.size(iconSize),
                )
              } else {
                Text(
                  text = value.toString(),
                  style =
                    if (currentOption.isSameDuration(value)) {
                      typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                      typography.labelMedium
                    },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    },
  )
}

internal fun resolveDurationTimerStopMode(
  currentOption: TimerOption?,
  preferredStopMode: DurationTimerStopMode,
): DurationTimerStopMode = (currentOption as? DurationTimerOption)?.stopMode ?: preferredStopMode

private fun TimerOption?.isSameDuration(that: Int?) =
  when (this) {
    CurrentItemTimerOption -> false
    is DurationTimerOption -> that == this.duration
    null -> that == null
  }

private val OptionPresetDurations =
  listOf(
    null,
    10,
    15,
    30,
    60,
  )
