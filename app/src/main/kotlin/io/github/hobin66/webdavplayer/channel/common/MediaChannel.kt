package io.github.hobin66.webdavplayer.channel.common

import android.net.Uri
import okio.Buffer
import io.github.hobin66.webdavplayer.lib.domain.Book
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.CreateBookmarkRequest
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.Library
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.lib.domain.PagedItems
import io.github.hobin66.webdavplayer.lib.domain.RecentBook

interface MediaChannel {
  fun getLibraryType(): LibraryType

  fun provideFileUri(
    libraryItemId: String,
    fileId: String,
  ): Uri

  suspend fun fetchBookCover(
    bookId: String,
    width: Int? = null,
  ): OperationResult<Buffer>

  suspend fun fetchBooks(
    libraryId: String,
    pageSize: Int,
    pageNumber: Int,
  ): OperationResult<PagedItems<Book>>

  suspend fun searchBooks(
    libraryId: String,
    query: String,
    limit: Int,
  ): OperationResult<List<Book>>

  suspend fun fetchLibraries(): OperationResult<List<Library>>

  fun fetchConnectionHost(): OperationResult<ConnectionHost>

  suspend fun fetchConnectionInfo(): OperationResult<ConnectionInfo>

  suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>>

  suspend fun fetchBook(
    bookId: String,
    focusChapterId: String? = null,
  ): OperationResult<DetailedItem>

  suspend fun fetchBookmarks(libraryItemId: String): OperationResult<List<Bookmark>>

  suspend fun dropBookmark(bookmark: Bookmark): OperationResult<Unit>

  suspend fun createBookmark(request: CreateBookmarkRequest): OperationResult<Bookmark>
}
