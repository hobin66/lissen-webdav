package io.github.hobin66.webdavplayer.common

import io.github.hobin66.webdavplayer.ui.extensions.formatTime

fun buildBookmarkTitle(
  currentChapterTitle: String,
  currentChapterPosition: Double,
): String = "$currentChapterTitle - ${currentChapterPosition.toInt().formatTime()}"
