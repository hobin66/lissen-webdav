package io.github.hobin66.webdavplayer.ui.screens.library.paging

import androidx.paging.PagingState
import io.github.hobin66.webdavplayer.common.LibraryPagingSource
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.lib.domain.Book
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences

class LibrarySearchPagingSource(
  private val preferences: WebdavPlayerPreferences,
  private val mediaChannel: WebdavMediaProvider,
  private val searchToken: String,
  private val limit: Int,
  onTotalCountChanged: (Int) -> Unit,
) : LibraryPagingSource<Book>(onTotalCountChanged) {
  override fun getRefreshKey(state: PagingState<Int, Book>) = null

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
    val libraryId =
      preferences
        .getPreferredLibrary()
        ?.id
        ?: return LoadResult.Page(emptyList(), null, null)

    if (searchToken.isBlank()) {
      return LoadResult.Page(emptyList(), null, null)
    }

    return mediaChannel
      .searchBooks(libraryId, searchToken, limit)
      .fold(
        onSuccess = {
          onTotalCountChanged.invoke(it.size)
          LoadResult.Page(it, null, null)
        },
        onFailure = { LoadResult.Page(emptyList(), null, null) },
      )
  }
}
