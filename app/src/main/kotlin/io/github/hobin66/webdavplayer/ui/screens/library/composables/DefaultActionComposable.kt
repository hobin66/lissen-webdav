package io.github.hobin66.webdavplayer.ui.screens.library.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
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
  onManageBooksRequested: () -> Unit,
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
    IconButton(onClick = { onManageBooksRequested() }) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
        contentDescription = stringResource(R.string.library_manage_books_quick_entry_title),
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
