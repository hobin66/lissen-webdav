package org.grakovne.lissen.channel.webdav

import org.grakovne.lissen.channel.webdav.cache.WebdavBookIndexEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebdavCachePolicyTest {
  @Test
  fun `prefers in memory index when available`() {
    assertEquals(
      WebdavIndexSource.MEMORY,
      resolveWebdavIndexSource(
        hasInMemoryIndex = true,
        hasPersistedIndex = true,
      ),
    )
  }

  @Test
  fun `uses persisted index when memory cache is empty`() {
    assertEquals(
      WebdavIndexSource.PERSISTED,
      resolveWebdavIndexSource(
        hasInMemoryIndex = false,
        hasPersistedIndex = true,
      ),
    )
  }

  @Test
  fun `returns empty source when no cache exists`() {
    assertEquals(
      WebdavIndexSource.EMPTY,
      resolveWebdavIndexSource(
        hasInMemoryIndex = false,
        hasPersistedIndex = false,
      ),
    )
  }

  @Test
  fun `filters library list to added books only`() {
    val books =
      listOf(
        indexEntry(bookId = "a", isAdded = true),
        indexEntry(bookId = "b", isAdded = false),
        indexEntry(bookId = "c", isAdded = true),
      )

    val filtered = filterAddedBooks(books)

    assertEquals(listOf("a", "c"), filtered.map { it.bookId })
  }

  @Test
  fun `marks book as added`() {
    val updated = markBookAdded(indexEntry(isAdded = false))

    assertTrue(updated.isAdded)
  }

  @Test
  fun `marks book as removed`() {
    val updated = markBookRemoved(indexEntry(isAdded = true))

    assertFalse(updated.isAdded)
  }

  @Test
  fun `skips cover lookup when missing state is known`() {
    assertTrue(shouldSkipWebdavCoverLookup(indexEntry(isCoverMissing = true)))
    assertFalse(shouldSkipWebdavCoverLookup(indexEntry(isCoverMissing = false)))
  }

  @Test
  fun `prefers resolved cover name before fallback candidates`() {
    assertEquals(
      listOf("cover.webp", "cover.jpg", "cover.jpeg", "cover.png"),
      buildWebdavCoverCandidates(
        preferredCoverName = "cover.jpg",
        resolvedCoverName = "cover.webp",
      ),
    )
  }

  @Test
  fun `marks missing cover state`() {
    val updated = markMissingWebdavCover(indexEntry())

    assertTrue(updated.isCoverMissing)
    assertEquals(null, updated.resolvedCoverName)
  }

  @Test
  fun `stores resolved cover name`() {
    val updated = markResolvedWebdavCover(indexEntry(), "cover.png")

    assertFalse(updated.isCoverMissing)
    assertEquals("cover.png", updated.resolvedCoverName)
  }

  @Test
  fun `resets cover state during manual refresh`() {
    val updated =
      resetWebdavCoverState(
        indexEntry(
          resolvedCoverName = "cover.png",
          isCoverMissing = true,
        ),
      )

    assertFalse(updated.isCoverMissing)
    assertEquals(null, updated.resolvedCoverName)
  }

  private fun indexEntry(
    bookId: String = "book",
    resolvedCoverName: String? = null,
    isCoverMissing: Boolean = false,
    isAdded: Boolean = false,
  ) = WebdavBookIndexEntry(
    bookId = bookId,
    directoryPath = "Book",
    directoryEtag = null,
    directoryLastModified = null,
    title = "Book",
    coverName = "cover.jpg",
    metadataEtag = null,
    metadataLastModified = null,
    coverEtag = null,
    coverLastModified = null,
    resolvedCoverName = resolvedCoverName,
    isCoverMissing = isCoverMissing,
    isAdded = isAdded,
  )
}
