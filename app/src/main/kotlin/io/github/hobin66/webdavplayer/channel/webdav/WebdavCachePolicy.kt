package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavBookIndexEntry

enum class WebdavIndexSource {
  MEMORY,
  PERSISTED,
  EMPTY,
}

fun resolveWebdavIndexSource(
  hasInMemoryIndex: Boolean,
  hasPersistedIndex: Boolean,
): WebdavIndexSource =
  when {
    hasInMemoryIndex -> WebdavIndexSource.MEMORY
    hasPersistedIndex -> WebdavIndexSource.PERSISTED
    else -> WebdavIndexSource.EMPTY
  }

fun filterAddedBooks(entries: Collection<WebdavBookIndexEntry>): List<WebdavBookIndexEntry> = entries.filter { it.isAdded }

fun shouldSkipWebdavCoverLookup(entry: WebdavBookIndexEntry): Boolean = entry.isCoverMissing

fun buildWebdavCoverCandidates(
  preferredCoverName: String,
  resolvedCoverName: String?,
): List<String> =
  listOfNotNull(
    resolvedCoverName,
    preferredCoverName,
    "cover.jpg",
    "cover.jpeg",
    "cover.png",
    "cover.webp",
  ).distinct()

fun markMissingWebdavCover(entry: WebdavBookIndexEntry): WebdavBookIndexEntry =
  entry.copy(
    resolvedCoverName = null,
    isCoverMissing = true,
  )

fun markResolvedWebdavCover(
  entry: WebdavBookIndexEntry,
  resolvedCoverName: String,
): WebdavBookIndexEntry =
  entry.copy(
    resolvedCoverName = resolvedCoverName,
    isCoverMissing = false,
  )

fun resetWebdavCoverState(entry: WebdavBookIndexEntry): WebdavBookIndexEntry =
  entry.copy(
    resolvedCoverName = null,
    isCoverMissing = false,
  )

fun markBookAdded(entry: WebdavBookIndexEntry): WebdavBookIndexEntry = entry.copy(isAdded = true)

fun markBookRemoved(entry: WebdavBookIndexEntry): WebdavBookIndexEntry = entry.copy(isAdded = false)
