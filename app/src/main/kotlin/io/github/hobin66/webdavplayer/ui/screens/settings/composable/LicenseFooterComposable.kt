package io.github.hobin66.webdavplayer.ui.screens.settings.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.hobin66.webdavplayer.BuildConfig
import io.github.hobin66.webdavplayer.R

@Composable
fun LicenseFooterComposable() {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(16.dp),
  ) {
    HorizontalDivider(
      modifier = Modifier.padding(horizontal = 12.dp),
      color = colorScheme.onSurface.copy(alpha = 0.2f),
    )

    Text(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(top = 16.dp)
          .align(Alignment.CenterHorizontally),
      text = stringResource(R.string.settings_footer_version_label, BuildConfig.VERSION_NAME),
      style =
        TextStyle(
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center,
        ),
    )
    Text(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(top = 8.dp)
          .align(Alignment.CenterHorizontally),
      text = "© 2024-2026 Max Grakov. MIT License",
      style =
        TextStyle(
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center,
        ),
    )
  }
}
