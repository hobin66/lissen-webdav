package io.github.hobin66.webdavplayer.ui.screens.library.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.ui.icons.Search

@Composable
fun DefaultActionComposable(
  onSearchRequested: () -> Unit,
  onPreferencesRequested: () -> Unit,
) {
  Row {
    IconButton(
      onClick = { onSearchRequested() },
    ) {
      Icon(
        imageVector = Search,
        contentDescription = null,
      )
    }
    IconButton(onClick = { onPreferencesRequested() }) {
      Icon(
        imageVector = Icons.Outlined.Settings,
        contentDescription = stringResource(R.string.common_menu),
      )
    }
  }
}
