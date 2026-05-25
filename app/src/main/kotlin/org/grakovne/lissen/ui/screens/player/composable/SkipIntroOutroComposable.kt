package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.ui.components.slider.SkipSecondsSlider
import org.grakovne.lissen.ui.components.slider.SkipSliderRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipIntroOutroComposable(
  book: DetailedItem,
  onSave: (introSeconds: Int, outroSeconds: Int) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val initialIntroSeconds = remember(book.id) { book.introSkipSeconds.coerceIn(SkipSliderRange) }
  val initialOutroSeconds = remember(book.id) { book.outroSkipSeconds.coerceIn(SkipSliderRange) }

  var draftIntroSeconds by rememberSaveable(book.id) { mutableIntStateOf(initialIntroSeconds) }
  var draftOutroSeconds by rememberSaveable(book.id) { mutableIntStateOf(initialOutroSeconds) }
  var dismissHandled by remember(book.id) { mutableStateOf(false) }

  fun dismiss() {
    if (dismissHandled) {
      return
    }

    dismissHandled = true

    if (draftIntroSeconds != initialIntroSeconds || draftOutroSeconds != initialOutroSeconds) {
      onSave(draftIntroSeconds, draftOutroSeconds)
    }

    onDismissRequest()
  }

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = ::dismiss,
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
          text = stringResource(R.string.player_skip_title),
          style = typography.bodyLarge,
        )

        SkipSettingSlider(
          label = stringResource(R.string.player_skip_intro_label),
          seconds = draftIntroSeconds,
          onUpdate = { draftIntroSeconds = it },
        )

        SkipSettingSlider(
          label = stringResource(R.string.player_skip_outro_label),
          seconds = draftOutroSeconds,
          onUpdate = { draftOutroSeconds = it },
        )
      }
    },
  )
}

@Composable
private fun SkipSettingSlider(
  label: String,
  seconds: Int,
  onUpdate: (Int) -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(top = 16.dp),
  ) {
    Text(
      text = label,
      style = typography.labelLarge,
    )

    SkipSecondsSlider(
      seconds = seconds,
      offLabel = stringResource(R.string.player_skip_off),
      modifier = Modifier.fillMaxWidth(),
      onUpdate = onUpdate,
    )
  }
}
