package io.github.hobin66.webdavplayer.ui.screens.common

import android.content.Context
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.lib.domain.AllItemsDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.CurrentItemDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.DownloadOption
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.lib.domain.NumberItemDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.RemainingItemsDownloadOption

fun DownloadOption?.makeText(
  context: Context,
  libraryType: LibraryType,
): String {
  val spec = resolveDownloadLabelSpec(this, libraryType)

  return when (spec.key) {
    DownloadLabelKey.DISABLED -> context.getString(R.string.downloads_menu_download_option_disable)
    DownloadLabelKey.CURRENT_CHAPTER -> context.getString(R.string.downloads_menu_download_option_current_chapter)
    DownloadLabelKey.CURRENT_ITEM -> context.getString(R.string.downloads_menu_download_option_current_item)
    DownloadLabelKey.ENTIRE_BOOK -> context.getString(R.string.downloads_menu_download_option_entire_book)
    DownloadLabelKey.ENTIRE_ITEM -> context.getString(R.string.downloads_menu_download_option_entire_item)
    DownloadLabelKey.REMAINING_CHAPTERS -> context.getString(R.string.downloads_menu_download_option_remaining_chapters)
    DownloadLabelKey.REMAINING_ITEMS -> context.getString(R.string.downloads_menu_download_option_remaining_items)
    DownloadLabelKey.NEXT_CHAPTERS -> context.getString(R.string.downloads_menu_download_option_next_chapters, spec.count ?: 0)
    DownloadLabelKey.NEXT_ITEMS -> context.getString(R.string.downloads_menu_download_option_next_items, spec.count ?: 0)
  }
}
