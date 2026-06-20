package io.github.hobin66.webdavplayer.ui.screens.player.composable.common

import android.content.Context
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.lib.domain.LibraryType

fun provideNowPlayingTitle(
  libraryType: LibraryType,
  context: Context,
) = when (resolveNowPlayingTitleKey(libraryType)) {
  NowPlayingTitleKey.LIBRARY -> context.getString(R.string.player_screen_library_playing_title)
  NowPlayingTitleKey.GENERIC -> context.getString(R.string.player_screen_items_playing_title)
}
