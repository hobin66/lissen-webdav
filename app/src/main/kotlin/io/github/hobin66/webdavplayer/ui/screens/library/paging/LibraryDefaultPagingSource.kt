package io.github.hobin66.webdavplayer.ui.screens.library.paging

import androidx.paging.PagingState
import io.github.hobin66.webdavplayer.common.LibraryPagingSource
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.lib.domain.Book
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences

class LibraryDefaultPagingSource(
  private val preferences: WebdavPlayerPreferences,
  private val mediaChannel: WebdavMediaProvider,
  onTotalCountChanged: (Int) -> Unit,
) : LibraryPagingSource<Book>(onTotalCountChanged) {
  override fun getRefreshKey(state: PagingState<Int, Book>) =
    state
      .anchorPosition
      ?.let { anchorPosition ->
        state
          .closestPageToPosition(anchorPosition)
          ?.prevKey
          ?.plus(1)
          ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
      }

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
    val libraryId =
      preferences
        .getPreferredLibrary()
        ?.id
        ?: return LoadResult.Page(emptyList(), null, null)

    return mediaChannel
      .fetchBooks(
        libraryId = libraryId,
        pageSize = params.loadSize,
        pageNumber = params.key ?: 0,
      ).fold(
        onSuccess = { result ->
          val nextPage = if (result.items.isEmpty()) null else result.currentPage + 1
          val prevKey = if (result.currentPage == 0) null else result.currentPage - 1

          onTotalCountChanged.invoke(result.totalItems)

          LoadResult.Page(
            data = result.items,
            prevKey = prevKey,
            nextKey = nextPage,
          )
        },
        onFailure = {
          LoadResult.Page(emptyList(), null, null)
        },
      )
  }
}
