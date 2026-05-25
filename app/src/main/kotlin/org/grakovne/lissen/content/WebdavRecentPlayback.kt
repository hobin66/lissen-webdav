package org.grakovne.lissen.content

import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.playback.service.PlaybackSnapshotRecord

fun mergeRecentPlayback(
  existing: List<RecentBook>,
  latest: RecentBook,
  limit: Int,
): List<RecentBook> =
  (listOf(latest) + existing)
    .distinctBy { it.id }
    .sortedByDescending { it.listenedLastUpdate ?: 0L }
    .take(limit)

fun shouldTrimProgress(
  totalDuration: Double,
  progress: Double,
): Boolean =
  when {
    totalDuration <= 0.0 -> false
    progress <= 0.0 -> true
    progress >= totalDuration -> true
    else -> false
  }

fun fallbackRecentPlayback(
  recentBooks: List<RecentBook>,
  playingItem: DetailedItem?,
  snapshot: PlaybackSnapshotRecord?,
): List<RecentBook> {
  if (recentBooks.isNotEmpty()) {
    return recentBooks
  }

  val safePlayingItem = playingItem ?: return emptyList()
  val safeSnapshot = snapshot ?: return emptyList()
  if (safeSnapshot.bookId != safePlayingItem.id) {
    return emptyList()
  }

  return listOf(
    RecentBook(
      id = safePlayingItem.id,
      title = safePlayingItem.title,
      subtitle = safePlayingItem.subtitle,
      author = safePlayingItem.author,
      listenedPercentage = null,
      listenedLastUpdate = safeSnapshot.lastUpdated,
    ),
  )
}
