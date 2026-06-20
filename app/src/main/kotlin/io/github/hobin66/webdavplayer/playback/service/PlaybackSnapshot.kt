package io.github.hobin66.webdavplayer.playback.service

import com.squareup.moshi.JsonClass
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter

@JsonClass(generateAdapter = true)
data class PlaybackSnapshotRecord(
  val bookId: String,
  val chapterId: String,
  val chapterPosition: Double,
  val totalPosition: Double,
  val lastUpdated: Long,
)

data class PlaybackSnapshotStart(
  val chapterIndex: Int,
  val chapterPosition: Double,
)

enum class PlaybackSnapshotTrigger {
  PERIODIC,
  EVENT,
}

fun shouldPersistPlaybackSnapshot(
  lastPersistAtMs: Long?,
  nowMs: Long,
  intervalMs: Long,
): Boolean =
  when (lastPersistAtMs) {
    null -> true
    else -> nowMs - lastPersistAtMs >= intervalMs
  }

fun shouldUpdateRecentPlaybackSummary(trigger: PlaybackSnapshotTrigger): Boolean = trigger == PlaybackSnapshotTrigger.EVENT

fun resolvePlaybackSnapshotStart(
  chapters: List<PlayingChapter>,
  snapshot: PlaybackSnapshotRecord?,
): PlaybackSnapshotStart? {
  val safeSnapshot = snapshot ?: return null
  val index = chapters.indexOfFirst { it.id == safeSnapshot.chapterId }
  if (index < 0) {
    return null
  }

  return PlaybackSnapshotStart(
    chapterIndex = index,
    chapterPosition = safeSnapshot.chapterPosition,
  )
}

fun DetailedItem.isDirectFileQueue(): Boolean =
  files.size == chapters.size &&
    files.zip(chapters).all { (file, chapter) -> file.id == chapter.id }

fun DetailedItem.canRestoreFromOverallProgress(): Boolean =
  when {
    isDirectFileQueue().not() -> true
    chapters.any { it.duration > 0.0 } -> true
    else -> false
  }

fun resolvePlaybackStartPosition(
  book: DetailedItem,
  snapshot: PlaybackSnapshotRecord? = null,
): ChapterPosition {
  var (chapterIndex, chapterOffset) =
    when (book.isDirectFileQueue()) {
      true -> {
        resolvePlaybackSnapshotStart(
          chapters = book.chapters,
          snapshot = snapshot,
        )?.let { ChapterPosition(it.chapterIndex, it.chapterPosition) }
      }

      false -> {
        null
      }
    } ?: book
      .progress
      ?.currentTime
      ?.takeIf { book.canRestoreFromOverallProgress() }
      ?.let { calculateChapterIndexAndPosition(book, it) }
      ?: ChapterPosition(0, 0.0)

  val negativeChapter = chapterIndex < 0
  val lastMoments =
    book.chapters.lastOrNull()?.let { chapter ->
      chapterIndex == book.chapters.lastIndex && (chapter.end - 5) < chapterOffset
    } ?: false

  if (negativeChapter || lastMoments) {
    chapterIndex = 0
    chapterOffset = 0.0
  }

  return ChapterPosition(chapterIndex, chapterOffset)
}
