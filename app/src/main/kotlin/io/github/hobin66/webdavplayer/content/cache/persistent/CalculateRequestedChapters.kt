package io.github.hobin66.webdavplayer.content.cache.persistent

import io.github.hobin66.webdavplayer.lib.domain.AllItemsDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.CurrentItemDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.DownloadOption
import io.github.hobin66.webdavplayer.lib.domain.NumberItemDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import io.github.hobin66.webdavplayer.lib.domain.RemainingItemsDownloadOption
import io.github.hobin66.webdavplayer.playback.service.calculateChapterIndex

fun calculateRequestedChapters(
  book: DetailedItem,
  option: DownloadOption,
  currentTotalPosition: Double,
): List<PlayingChapter> {
  val chapterIndex = calculateChapterIndex(book, currentTotalPosition)

  return when (option) {
    AllItemsDownloadOption -> {
      book.chapters
    }

    CurrentItemDownloadOption -> {
      listOfNotNull(book.chapters.getOrNull(chapterIndex))
    }

    is NumberItemDownloadOption -> {
      book.chapters.subList(
        chapterIndex.coerceAtLeast(0),
        (chapterIndex + option.itemsNumber).coerceIn(chapterIndex..book.chapters.size),
      )
    }

    RemainingItemsDownloadOption -> {
      book.chapters.subList(
        chapterIndex.coerceIn(0, book.chapters.size),
        book.chapters.size,
      )
    }
  }
}
