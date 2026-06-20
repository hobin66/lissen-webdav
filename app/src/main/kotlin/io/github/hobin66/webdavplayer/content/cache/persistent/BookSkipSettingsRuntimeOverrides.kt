package io.github.hobin66.webdavplayer.content.cache.persistent

import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.playback.BookSkipSettingsStore

internal fun DetailedItem.withRuntimeBookSkipSettings(): DetailedItem =
  BookSkipSettingsStore
    .get(id)
    ?.let { settings ->
      copy(
        introSkipSeconds = settings.normalizedIntroSkipSeconds,
        outroSkipSeconds = settings.normalizedOutroSkipSeconds,
      )
    } ?: this
