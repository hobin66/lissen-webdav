package io.github.hobin66.webdavplayer.ui.screens.player.composable.common

import io.github.hobin66.webdavplayer.lib.domain.LibraryType

enum class NowPlayingTitleKey {
  LIBRARY,
  GENERIC,
}

fun resolveNowPlayingTitleKey(libraryType: LibraryType): NowPlayingTitleKey =
  when (libraryType) {
    LibraryType.UNKNOWN -> NowPlayingTitleKey.GENERIC
    else -> NowPlayingTitleKey.LIBRARY
  }
