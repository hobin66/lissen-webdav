package io.github.hobin66.webdavplayer.channel.webdav

data class WebdavRefreshProgress(
  val processedBooks: Int,
  val totalBooks: Int,
) {
  val ratio: Float
    get() =
      when {
        totalBooks <= 0 -> 0f
        else -> processedBooks.toFloat() / totalBooks.toFloat()
      }

  fun advance(): WebdavRefreshProgress =
    copy(
      processedBooks = (processedBooks + 1).coerceAtMost(totalBooks),
    )

  companion object {
    fun start(totalBooks: Int): WebdavRefreshProgress =
      WebdavRefreshProgress(
        processedBooks = 0,
        totalBooks = totalBooks,
      )
  }
}
