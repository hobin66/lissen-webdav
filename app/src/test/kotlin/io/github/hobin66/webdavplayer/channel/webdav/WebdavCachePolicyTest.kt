package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.common.OperationError
import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavBookIndexEntry
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
  fun `prefers metadata cover then resolved identity and folder fallbacks`() {
    assertEquals(
      listOf(
        "cover.jpg",
        "cover.webp",
        "cover.jpeg",
        "cover.png",
        "folder.jpg",
        "folder.jpeg",
        "folder.png",
        "folder.webp",
      ),
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

  @Test
  fun `skips cover validation when directory etag is unchanged and cover was validated`() {
    val previous =
      sampleIndexEntry(
        directoryEtag = "\"dir-1\"",
        coverEtag = "\"cover-1\"",
        isCoverMissing = false,
      )

    assertFalse(
      shouldForceWebdavCoverValidation(
        previous = previous,
        preferredCoverName = "cover.jpg",
        directoryEtag = "\"dir-1\"",
        directoryLastModified = "ignored",
      ),
    )
  }

  @Test
  fun `forces cover validation when directory etag changes`() {
    val previous =
      sampleIndexEntry(
        directoryEtag = "\"dir-1\"",
        coverEtag = "\"cover-1\"",
      )

    assertTrue(
      shouldForceWebdavCoverValidation(
        previous = previous,
        preferredCoverName = "cover.jpg",
        directoryEtag = "\"dir-2\"",
        directoryLastModified = null,
      ),
    )
  }

  @Test
  fun `forces cover validation for first-time directory entries`() {
    assertTrue(
      shouldForceWebdavCoverValidation(
        previous = null,
        preferredCoverName = "cover.jpg",
        directoryEtag = "\"dir-1\"",
        directoryLastModified = null,
      ),
    )
  }

  @Test
  fun `resolveCoverIdentity marks missing covers`() {
    val previous = sampleIndexEntry(resolvedCoverName = "cover.jpg", isCoverMissing = false)
    assertEquals(
      null to true,
      resolveCoverIdentityAfterValidation(
        previous = previous,
        preferredCoverName = "cover.jpg",
        validatedCoverName = null,
        coverMissing = true,
      ),
    )
  }

  @Test
  fun `resolveCoverIdentity keeps resolved name when cover still exists`() {
    val previous = sampleIndexEntry(resolvedCoverName = "folder.png", isCoverMissing = false)
    assertEquals(
      "folder.png" to false,
      resolveCoverIdentityAfterValidation(
        previous = previous,
        preferredCoverName = "cover.jpg",
        validatedCoverName = "folder.png",
        coverMissing = false,
      ),
    )
  }

  private fun sampleIndexEntry(
    directoryEtag: String? = "\"dir\"",
    coverEtag: String? = null,
    resolvedCoverName: String? = null,
    isCoverMissing: Boolean = false,
  ) = WebdavBookIndexEntry(
    bookId = "book",
    directoryPath = "Book",
    directoryEtag = directoryEtag,
    directoryLastModified = null,
    title = "Book",
    coverName = "cover.jpg",
    metadataEtag = null,
    metadataLastModified = null,
    coverEtag = coverEtag,
    coverLastModified = null,
    resolvedCoverName = resolvedCoverName,
    isCoverMissing = isCoverMissing,
  )

  @Test
  fun `keeps resolved cover when preferred cover is absent`() {
    val previous = sampleIndexEntry(resolvedCoverName = "folder.jpg", isCoverMissing = false)
    // Preferred-only HEAD miss must not mark the cover missing.
    assertEquals(
      "folder.jpg" to false,
      resolveCoverIdentityAfterValidation(
        previous = previous,
        preferredCoverName = "cover.jpg",
        validatedCoverName = "folder.jpg",
        coverMissing = false,
      ),
    )
  }

  @Test
  fun `cover identity resets when metadata preferred name changes before validation`() {
    val previous = sampleIndexEntry(resolvedCoverName = "folder.jpg")

    assertEquals(
      null to false,
      resolveCoverIdentityAfterValidation(
        previous = previous,
        preferredCoverName = "artwork.jpg",
        validatedCoverName = null,
        coverMissing = false,
      ),
    )
  }

  @Test
  fun `definitive validation miss clears previous resolved identity without marking missing`() {
    val previous = sampleIndexEntry(resolvedCoverName = "folder.jpg")

    assertEquals(
      null to false,
      resolveCoverIdentityAfterValidation(
        previous = previous,
        preferredCoverName = "cover.jpg",
        validatedCoverName = null,
        coverMissing = false,
        preservePreviousIdentity = false,
      ),
    )
  }

  @Test
  fun `metadata cover name change forces validation even when directory is unchanged`() {
    val previous = sampleIndexEntry(directoryEtag = "\"dir-1\"", coverEtag = "\"cover-1\"")

    assertTrue(
      shouldForceWebdavCoverValidation(
        previous = previous,
        preferredCoverName = "artwork.jpg",
        directoryEtag = "\"dir-1\"",
        directoryLastModified = null,
      ),
    )
  }

  @Test
  fun `only definitive not found results mark a cover missing`() {
    assertTrue(
      shouldMarkWebdavCoverMissing(
        listOf(OperationError.NotFoundError, OperationError.NotFoundError),
      ),
    )
    assertFalse(
      shouldMarkWebdavCoverMissing(
        listOf(OperationError.NotFoundError, OperationError.NetworkError),
      ),
    )
    assertFalse(shouldMarkWebdavCoverMissing(emptyList()))
  }

}
