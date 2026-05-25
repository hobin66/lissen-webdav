package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.playback.BookSkipSettingsStore

internal fun DetailedItem.withRuntimeBookSkipSettings(): DetailedItem =
  BookSkipSettingsStore
    .get(id)
    ?.let { settings ->
      copy(
        introSkipSeconds = settings.normalizedIntroSkipSeconds,
        outroSkipSeconds = settings.normalizedOutroSkipSeconds,
      )
    } ?: this
