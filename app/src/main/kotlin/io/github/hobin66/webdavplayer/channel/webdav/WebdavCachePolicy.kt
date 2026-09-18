package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.common.OperationError
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

fun shouldSkipWebdavCoverLookup(entry: WebdavBookIndexEntry): Boolean = entry.isCoverMissing

/**
 * Skip cover HEAD when the book directory is unchanged and we already know cover state.
 * Directory change (or first-time entry) forces revalidation.
 */
fun shouldForceWebdavCoverValidation(
  previous: WebdavBookIndexEntry?,
  preferredCoverName: String,
  directoryEtag: String?,
  directoryLastModified: String?,
): Boolean {
  if (previous == null) {
    return true
  }

  if (previous.coverName != preferredCoverName) {
    return true
  }

  if (isWebdavValidationTokenChanged(previous.directoryEtag, directoryEtag)) {
    return true
  }

  if (
    previous.directoryEtag.isNullOrBlank() &&
    directoryEtag.isNullOrBlank() &&
    isWebdavValidationTokenChanged(previous.directoryLastModified, directoryLastModified)
  ) {
    return true
  }

  // Directory appears unchanged. Only force when we never validated the cover before.
  val hasCoverValidation =
    previous.isCoverMissing ||
      !previous.coverEtag.isNullOrBlank() ||
      !previous.coverLastModified.isNullOrBlank()

  return !hasCoverValidation
}

/**
 * Preserve resolved cover identity across refreshes.
 *
 * Preferred-cover HEAD 404 is NOT proof that the book has no cover (candidates may still exist).
 * Only an explicit [coverMissing]=true (e.g. after full candidate download failure) should mark missing.
 */
fun resolveCoverIdentityAfterValidation(
  previous: WebdavBookIndexEntry?,
  preferredCoverName: String,
  validatedCoverName: String?,
  coverMissing: Boolean,
  preservePreviousIdentity: Boolean = true,
): Pair<String?, Boolean> =
  when {
    coverMissing -> null to true
    validatedCoverName != null -> validatedCoverName to false
    previous?.coverName != preferredCoverName -> null to false
    preservePreviousIdentity -> previous.resolvedCoverName to false
    else -> null to false
  }

fun isWebdavValidationTokenChanged(
  previous: String?,
  current: String?,
): Boolean {
  if (!previous.isNullOrBlank() && !current.isNullOrBlank()) {
    return previous != current
  }
  if (previous.isNullOrBlank() && current.isNullOrBlank()) {
    return false
  }
  // One side missing tokens: treat as changed only when both sides have values was handled above.
  // Prefer conservative revalidation when tokens cannot be compared.
  return !(previous.isNullOrBlank() && current.isNullOrBlank())
}

fun buildWebdavCoverCandidates(
  preferredCoverName: String,
  resolvedCoverName: String?,
): List<String> =
  listOfNotNull(
    preferredCoverName,
    resolvedCoverName,
    "cover.jpg",
    "cover.jpeg",
    "cover.png",
    "cover.webp",
    "folder.jpg",
    "folder.jpeg",
    "folder.png",
    "folder.webp",
  ).distinct()

fun shouldMarkWebdavCoverMissing(errors: List<OperationError>): Boolean =
  errors.isNotEmpty() && errors.all { it == OperationError.NotFoundError }

fun markMissingWebdavCover(entry: WebdavBookIndexEntry): WebdavBookIndexEntry =
  entry.copy(
    resolvedCoverName = null,
    coverEtag = null,
    coverLastModified = null,
    isCoverMissing = true,
  )

fun markResolvedWebdavCover(
  entry: WebdavBookIndexEntry,
  resolvedCoverName: String,
): WebdavBookIndexEntry =
  entry.copy(
    resolvedCoverName = resolvedCoverName,
    coverEtag = entry.coverEtag.takeIf { entry.resolvedCoverName == resolvedCoverName },
    coverLastModified = entry.coverLastModified.takeIf { entry.resolvedCoverName == resolvedCoverName },
    isCoverMissing = false,
  )

fun resetWebdavCoverState(entry: WebdavBookIndexEntry): WebdavBookIndexEntry =
  entry.copy(
    resolvedCoverName = null,
    coverEtag = null,
    coverLastModified = null,
    isCoverMissing = false,
  )

fun markBookAdded(entry: WebdavBookIndexEntry): WebdavBookIndexEntry = entry.copy(isAdded = true)

fun markBookRemoved(entry: WebdavBookIndexEntry): WebdavBookIndexEntry = entry.copy(isAdded = false)

/** Default max size for cover binary downloads (8 MiB). */
const val WEBDAV_COVER_MAX_BYTES = 8L * 1024L * 1024L
