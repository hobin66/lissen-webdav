package io.github.hobin66.webdavplayer.channel.webdav.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import io.github.hobin66.webdavplayer.lib.domain.MediaProgress
import io.github.hobin66.webdavplayer.playback.service.PlaybackSnapshotRecord

@Keep
@JsonClass(generateAdapter = true)
data class WebdavBookMetadata(
  val version: Int = 1,
  val id: String,
  val title: String,
  val author: String? = null,
  val description: String? = null,
  val cover: String?,
  val introSkipSeconds: Int = 0,
  val outroSkipSeconds: Int = 0,
  val progress: WebdavPlaybackProgress? = null,
) {
  fun coverOrDefault(): String = cover ?: "cover.jpg"

  fun authorOrNull(): String? = author?.trim()?.takeIf { it.isNotEmpty() }

  fun descriptionOrNull(): String? = description?.trim()?.takeIf { it.isNotEmpty() }

  fun introSkipSecondsOrDefault(): Int = introSkipSeconds.coerceIn(0, 60)

  fun outroSkipSecondsOrDefault(): Int = outroSkipSeconds.coerceIn(0, 60)
}

@Keep
@JsonClass(generateAdapter = true)
data class WebdavPlaybackProgress(
  val currentTime: Double,
  val isFinished: Boolean = false,
  val lastUpdate: Long,
  val chapterId: String? = null,
  val chapterTime: Double? = null,
) {
  fun toMediaProgress(): MediaProgress =
    MediaProgress(
      currentTime = currentTime,
      isFinished = isFinished,
      lastUpdate = lastUpdate,
    )

  fun toPlaybackSnapshot(bookId: String): PlaybackSnapshotRecord? {
    val safeChapterId = chapterId?.takeIf { it.isNotBlank() } ?: return null
    val safeChapterTime = chapterTime ?: return null

    return PlaybackSnapshotRecord(
      bookId = bookId,
      chapterId = safeChapterId,
      chapterPosition = safeChapterTime,
      totalPosition = currentTime,
      lastUpdated = lastUpdate,
    )
  }

  companion object {
    fun from(
      mediaProgress: MediaProgress?,
      snapshot: PlaybackSnapshotRecord?,
    ): WebdavPlaybackProgress? {
      val mediaLastUpdate = mediaProgress?.lastUpdate ?: 0L
      val snapshotLastUpdate = snapshot?.lastUpdated ?: 0L
      val useSnapshot = snapshot != null && snapshotLastUpdate >= mediaLastUpdate
      val currentTime =
        when {
          useSnapshot -> snapshot.totalPosition
          else -> mediaProgress?.currentTime
        } ?: return null
      val lastUpdate = maxOf(mediaLastUpdate, snapshotLastUpdate).takeIf { it > 0L } ?: return null

      return WebdavPlaybackProgress(
        currentTime = currentTime,
        isFinished = mediaProgress?.isFinished ?: false,
        lastUpdate = lastUpdate,
        chapterId = if (useSnapshot) snapshot.chapterId else null,
        chapterTime = if (useSnapshot) snapshot.chapterPosition else null,
      )
    }
  }
}
