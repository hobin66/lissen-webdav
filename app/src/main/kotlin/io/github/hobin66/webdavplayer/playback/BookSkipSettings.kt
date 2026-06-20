package io.github.hobin66.webdavplayer.playback

data class BookSkipSettings(
  val introSkipSeconds: Int = 0,
  val outroSkipSeconds: Int = 0,
) {
  val normalizedIntroSkipSeconds: Int
    get() = introSkipSeconds.coerceIn(MIN_SKIP_SECONDS, MAX_SKIP_SECONDS)

  val normalizedOutroSkipSeconds: Int
    get() = outroSkipSeconds.coerceIn(MIN_SKIP_SECONDS, MAX_SKIP_SECONDS)

  companion object {
    const val MIN_SKIP_SECONDS = 0
    const val MAX_SKIP_SECONDS = 60
  }
}
