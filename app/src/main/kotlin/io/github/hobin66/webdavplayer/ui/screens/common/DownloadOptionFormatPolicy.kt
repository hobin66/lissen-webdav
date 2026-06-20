package io.github.hobin66.webdavplayer.ui.screens.common

import io.github.hobin66.webdavplayer.lib.domain.AllItemsDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.CurrentItemDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.DownloadOption
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.lib.domain.NumberItemDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.RemainingItemsDownloadOption

enum class DownloadLabelKey {
  DISABLED,
  CURRENT_CHAPTER,
  CURRENT_ITEM,
  ENTIRE_BOOK,
  ENTIRE_ITEM,
  REMAINING_CHAPTERS,
  REMAINING_ITEMS,
  NEXT_CHAPTERS,
  NEXT_ITEMS,
}

data class DownloadLabelSpec(
  val key: DownloadLabelKey,
  val count: Int? = null,
)

fun resolveDownloadLabelSpec(
  option: DownloadOption?,
  libraryType: LibraryType,
): DownloadLabelSpec =
  when (option) {
    null -> DownloadLabelSpec(DownloadLabelKey.DISABLED)
    CurrentItemDownloadOption -> {
      when (libraryType) {
        LibraryType.UNKNOWN -> DownloadLabelSpec(DownloadLabelKey.CURRENT_ITEM)
        else -> DownloadLabelSpec(DownloadLabelKey.CURRENT_CHAPTER)
      }
    }

    AllItemsDownloadOption -> {
      when (libraryType) {
        LibraryType.UNKNOWN -> DownloadLabelSpec(DownloadLabelKey.ENTIRE_ITEM)
        else -> DownloadLabelSpec(DownloadLabelKey.ENTIRE_BOOK)
      }
    }

    RemainingItemsDownloadOption -> {
      when (libraryType) {
        LibraryType.UNKNOWN -> DownloadLabelSpec(DownloadLabelKey.REMAINING_ITEMS)
        else -> DownloadLabelSpec(DownloadLabelKey.REMAINING_CHAPTERS)
      }
    }

    is NumberItemDownloadOption -> {
      when (libraryType) {
        LibraryType.UNKNOWN -> DownloadLabelSpec(DownloadLabelKey.NEXT_ITEMS, option.itemsNumber)
        else -> DownloadLabelSpec(DownloadLabelKey.NEXT_CHAPTERS, option.itemsNumber)
      }
    }
  }
