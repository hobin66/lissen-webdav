package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.lib.domain.BookFile
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter

/** Display duration while a track has not been probed yet. */
const val UNRESOLVED_DISPLAY_DURATION_SECONDS = 0.0

/**
 * Timeline span used for unresolved tracks so chapter start offsets stay strictly increasing
 * until real durations are available.
 */
const val UNRESOLVED_TIMELINE_DURATION_SECONDS = 1.0

fun hasResolvedDuration(durationSeconds: Double): Boolean = durationSeconds > 0.0

fun needsChapterDurationResolution(item: DetailedItem): Boolean =
  item.files.any { !hasResolvedDuration(it.duration) } ||
    item.chapters.any { !hasResolvedDuration(it.duration) }

fun isDirectFileChapterQueue(
  files: List<BookFile>,
  chapters: List<PlayingChapter>,
): Boolean =
  files.size == chapters.size &&
    files.zip(chapters).all { (file, chapter) -> file.id == chapter.id }

/**
 * Applies probed file durations and rebuilds chapter start/end for direct 1:1 WebDAV queues.
 * Files/chapters without a resolved probe keep their previous values (or unresolved placeholders).
 */
fun applyResolvedFileDurations(
  item: DetailedItem,
  durationsByFileId: Map<String, Double>,
): DetailedItem {
  if (durationsByFileId.isEmpty()) {
    return item
  }

  val updatedFiles =
    item.files.map { file ->
      val resolved = durationsByFileId[file.id]?.takeIf(::hasResolvedDuration)
      if (resolved != null) {
        file.copy(duration = resolved)
      } else {
        file
      }
    }

  if (!isDirectFileChapterQueue(updatedFiles, item.chapters)) {
    return item.copy(files = updatedFiles)
  }

  var start = 0.0
  val updatedChapters =
    item.chapters.zip(updatedFiles).map { (chapter, file) ->
      val resolved = hasResolvedDuration(file.duration)
      val timelineDuration =
        if (resolved) {
          file.duration
        } else {
          UNRESOLVED_TIMELINE_DURATION_SECONDS
        }
      val displayDuration =
        if (resolved) {
          file.duration
        } else {
          UNRESOLVED_DISPLAY_DURATION_SECONDS
        }

      val updated =
        chapter.copy(
          duration = displayDuration,
          start = start,
          end = start + timelineDuration,
        )
      start += timelineDuration
      updated
    }

  return item.copy(
    files = updatedFiles,
    chapters = updatedChapters,
  )
}


/**
 * Total playback position for a 1:1 file/chapter queue.
 *
 * Uses resolved chapter durations for the prefix. A total position is not representable until
 * every preceding chapter is resolved; treating an unknown prefix as zero would silently move
 * progress backwards when playback crosses a chapter boundary.
 */
fun resolveDirectQueueTotalPositionSeconds(
  chapters: List<PlayingChapter>,
  currentIndex: Int,
  currentPositionSeconds: Double,
): Double? {
  if (currentIndex < 0 || currentIndex >= chapters.size) {
    return null
  }

  val prefix = chapters.take(currentIndex)
  if (prefix.any { chapter -> !hasResolvedDuration(chapter.duration) }) {
    return null
  }

  val prefixSeconds = prefix.sumOf { chapter -> chapter.duration }

  return prefixSeconds + currentPositionSeconds.coerceAtLeast(0.0)
}

fun mergeDurationMaps(
  existing: Map<String, Double>,
  incoming: Map<String, Double>,
): Map<String, Double> =
  buildMap {
    putAll(existing)
    incoming.forEach { (id, duration) ->
      if (hasResolvedDuration(duration)) {
        put(id, duration)
      }
    }
  }
