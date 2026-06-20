package io.github.hobin66.webdavplayer.playback

import java.util.concurrent.ConcurrentHashMap

object BookSkipSettingsStore {
  private val settingsByBookId = ConcurrentHashMap<String, BookSkipSettings>()

  fun get(bookId: String): BookSkipSettings? = settingsByBookId[bookId]

  fun put(
    bookId: String,
    settings: BookSkipSettings,
  ) {
    settingsByBookId[bookId] =
      BookSkipSettings(
        introSkipSeconds = settings.normalizedIntroSkipSeconds,
        outroSkipSeconds = settings.normalizedOutroSkipSeconds,
      )
  }

  fun remove(bookId: String) {
    settingsByBookId.remove(bookId)
  }

  fun clear() {
    settingsByBookId.clear()
  }
}
