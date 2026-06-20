package io.github.hobin66.webdavplayer.lib.domain

import androidx.annotation.Keep

@Keep
data class PlaybackProgress(
  val currentChapterTime: Double,
  val currentTotalTime: Double,
)
