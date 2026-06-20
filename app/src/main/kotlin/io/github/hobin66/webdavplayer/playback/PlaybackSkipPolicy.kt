package io.github.hobin66.webdavplayer.playback

import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.TimerOption

private const val ACTIVE_SKIP_CHECK_INTERVAL_MS = 250L
private const val IDLE_SKIP_CHECK_INTERVAL_MS = 2_000L
private const val SKIP_WINDOW_PADDING_SECONDS = 1.0

fun shouldApplyIntroSkip(
  settings: BookSkipSettings,
  chapterPositionSeconds: Double,
  currentMediaItemId: String?,
  lastAppliedMediaItemId: String?,
): Boolean {
  val introSkipSeconds = settings.normalizedIntroSkipSeconds
  if (introSkipSeconds == 0) {
    return false
  }

  if (!isFirstSkipForMediaItem(currentMediaItemId, lastAppliedMediaItemId)) {
    return false
  }

  val safeChapterPosition = chapterPositionSeconds.coerceAtLeast(0.0)
  return safeChapterPosition < introSkipSeconds.toDouble()
}

fun shouldEvaluateBookSkip(isPlaying: Boolean): Boolean = isPlaying

fun resolveSkipCheckIntervalMs(
  isPlaying: Boolean,
  chapterPositionSeconds: Double,
  chapterDurationSeconds: Double?,
  settings: BookSkipSettings,
  playbackSpeed: Float,
): Long? {
  if (!isPlaying) {
    return null
  }

  val safePlaybackSpeed = playbackSpeed.takeIf { it > 0f } ?: 1f

  val safePosition = chapterPositionSeconds.coerceAtLeast(0.0)
  val introWindowUpperBound = settings.normalizedIntroSkipSeconds + SKIP_WINDOW_PADDING_SECONDS
  if (settings.normalizedIntroSkipSeconds > 0 && safePosition <= introWindowUpperBound) {
    return ACTIVE_SKIP_CHECK_INTERVAL_MS
  }

  val safeDuration = chapterDurationSeconds?.takeIf { it > 0.0 }
  if (safeDuration != null && settings.normalizedOutroSkipSeconds > 0) {
    val remainingSeconds = (safeDuration - safePosition).coerceAtLeast(0.0)
    val outroWindowUpperBound = settings.normalizedOutroSkipSeconds + SKIP_WINDOW_PADDING_SECONDS
    if (remainingSeconds <= outroWindowUpperBound) {
      return ACTIVE_SKIP_CHECK_INTERVAL_MS
    }
  }

  return resolveAdaptiveIdleSkipCheckIntervalMs(
    chapterPositionSeconds = safePosition,
    chapterDurationSeconds = chapterDurationSeconds,
    settings = settings,
    playbackSpeed = safePlaybackSpeed,
  )
}

private fun resolveAdaptiveIdleSkipCheckIntervalMs(
  chapterPositionSeconds: Double,
  chapterDurationSeconds: Double?,
  settings: BookSkipSettings,
  playbackSpeed: Float,
): Long =
  buildList {
  val introThresholdSeconds = settings.normalizedIntroSkipSeconds.toDouble()
  if (introThresholdSeconds > 0.0 && chapterPositionSeconds < introThresholdSeconds) {
    val secondsUntilIntroBoundary = (introThresholdSeconds - chapterPositionSeconds).coerceAtLeast(0.0)
      add(
        adaptiveSkipIntervalMs(
          secondsUntilBoundary = secondsUntilIntroBoundary,
          skipWindowSeconds = introThresholdSeconds,
          playbackSpeed = playbackSpeed,
        ),
      )
  }

  val safeDuration = chapterDurationSeconds?.takeIf { it > 0.0 }
  val outroThresholdSeconds = settings.normalizedOutroSkipSeconds.toDouble()
  if (safeDuration != null && outroThresholdSeconds > 0.0) {
    val secondsUntilOutroBoundary = (safeDuration - outroThresholdSeconds - chapterPositionSeconds).coerceAtLeast(0.0)
      add(
        adaptiveSkipIntervalMs(
          secondsUntilBoundary = secondsUntilOutroBoundary,
          skipWindowSeconds = outroThresholdSeconds,
          playbackSpeed = playbackSpeed,
        ),
      )
  }
}.minOrNull() ?: IDLE_SKIP_CHECK_INTERVAL_MS

private fun adaptiveSkipIntervalMs(
  secondsUntilBoundary: Double,
  skipWindowSeconds: Double,
  playbackSpeed: Float,
): Long {
  val realTimeUntilBoundaryMs = ((secondsUntilBoundary / playbackSpeed.toDouble()) * 1000.0).toLong().coerceAtLeast(0L)
  val realTimeSkipWindowMs =
    ((skipWindowSeconds / playbackSpeed.toDouble()) * 1000.0).toLong().coerceAtLeast(ACTIVE_SKIP_CHECK_INTERVAL_MS)

  if (realTimeUntilBoundaryMs > IDLE_SKIP_CHECK_INTERVAL_MS + realTimeSkipWindowMs) {
    return IDLE_SKIP_CHECK_INTERVAL_MS
  }

  val distanceIntervalMs = maxOf(ACTIVE_SKIP_CHECK_INTERVAL_MS, realTimeUntilBoundaryMs / 2)
  val windowIntervalMs = maxOf(ACTIVE_SKIP_CHECK_INTERVAL_MS, realTimeSkipWindowMs / 2)

  return minOf(IDLE_SKIP_CHECK_INTERVAL_MS, distanceIntervalMs, windowIntervalMs)
}

fun shouldApplyOutroSkip(
  settings: BookSkipSettings,
  chapterPositionSeconds: Double,
  chapterDurationSeconds: Double,
  currentMediaItemId: String?,
  lastAppliedMediaItemId: String?,
): Boolean {
  val outroSkipSeconds = settings.normalizedOutroSkipSeconds
  if (outroSkipSeconds == 0) {
    return false
  }

  if (!isFirstSkipForMediaItem(currentMediaItemId, lastAppliedMediaItemId)) {
    return false
  }

  if (chapterDurationSeconds <= 0.0) {
    return false
  }

  val safeChapterPosition = chapterPositionSeconds.coerceIn(0.0, chapterDurationSeconds)
  val remainingDuration = chapterDurationSeconds - safeChapterPosition
  return remainingDuration <= outroSkipSeconds.toDouble()
}

fun resolveIntroSkipTargetTotalPosition(
  chapterStartSeconds: Double,
  settings: BookSkipSettings,
): Double = chapterStartSeconds.coerceAtLeast(0.0) + settings.normalizedIntroSkipSeconds

fun resolveIntroSkipTargetChapterPosition(settings: BookSkipSettings): Double = settings.normalizedIntroSkipSeconds.toDouble()

fun resolveOutroSkipTargetTotalPosition(
  currentChapterIndex: Int,
  mediaItemCount: Int,
  chapterStartsSeconds: List<Double>,
  currentChapterDurationSeconds: Double,
): Double? {
  if (currentChapterIndex < 0) {
    return null
  }

  val nextChapterIndex = currentChapterIndex + 1
  if (nextChapterIndex < mediaItemCount) {
    return chapterStartsSeconds.getOrNull(nextChapterIndex)
  }

  return chapterStartsSeconds
    .getOrNull(currentChapterIndex)
    ?.let { chapterStartSeconds ->
      chapterStartSeconds.coerceAtLeast(0.0) + currentChapterDurationSeconds.coerceAtLeast(0.0)
    }
}

sealed class DirectQueueOutroTarget {
  data class NextMediaItem(
    val mediaItemIndex: Int,
  ) : DirectQueueOutroTarget()

  data class FinalBoundary(
    val chapterPositionSeconds: Double,
  ) : DirectQueueOutroTarget()
}

fun resolveDirectQueueOutroTarget(
  currentMediaItemIndex: Int,
  mediaItemCount: Int,
  currentChapterDurationSeconds: Double,
): DirectQueueOutroTarget? {
  if (currentMediaItemIndex < 0 || mediaItemCount <= 0) {
    return null
  }

  val nextMediaItemIndex = currentMediaItemIndex + 1
  if (nextMediaItemIndex < mediaItemCount) {
    return DirectQueueOutroTarget.NextMediaItem(nextMediaItemIndex)
  }

  return DirectQueueOutroTarget.FinalBoundary(
    chapterPositionSeconds = currentChapterDurationSeconds.coerceAtLeast(0.0),
  )
}

data class PendingDirectIntroTimerReschedule(
  val mediaItemId: String,
  val chapterPositionSeconds: Double,
)

fun createPendingDirectIntroTimerReschedule(
  currentMediaItemId: String?,
  targetChapterPositionSeconds: Double,
  chapterDurationSeconds: Double?,
): PendingDirectIntroTimerReschedule? {
  val mediaItemId = currentMediaItemId ?: return null
  if (chapterDurationSeconds != null && chapterDurationSeconds > 0.0) {
    return null
  }

  return PendingDirectIntroTimerReschedule(
    mediaItemId = mediaItemId,
    chapterPositionSeconds = targetChapterPositionSeconds.coerceAtLeast(0.0),
  )
}

fun shouldRetryPendingDirectIntroTimerReschedule(
  pending: PendingDirectIntroTimerReschedule?,
  currentMediaItemId: String?,
  chapterDurationSeconds: Double?,
): Boolean =
  pending != null &&
    pending.mediaItemId == currentMediaItemId &&
    chapterDurationSeconds != null &&
    chapterDurationSeconds > 0.0

data class DirectQueueSeekTarget(
  val mediaItemIndex: Int,
  val positionMs: Long,
)

fun resolveDirectQueueRelativeSeekTarget(
  currentMediaItemIndex: Int,
  mediaItemCount: Int,
  currentPositionMs: Long,
  currentDurationMs: Long,
  seekOffsetMs: Long,
): DirectQueueSeekTarget? {
  if (mediaItemCount <= 0 || currentMediaItemIndex !in 0 until mediaItemCount) {
    return null
  }

  val knownDurationMs = currentDurationMs.takeIf { it > 0L }
  val targetPositionMs = currentPositionMs.coerceAtLeast(0L) + seekOffsetMs
  val safePositionMs =
    when {
      targetPositionMs < 0L -> 0L
      knownDurationMs != null -> targetPositionMs.coerceAtMost(knownDurationMs)
      else -> targetPositionMs
    }

  return DirectQueueSeekTarget(
    mediaItemIndex = currentMediaItemIndex,
    positionMs = safePositionMs.coerceAtLeast(0L),
  )
}

fun resolveDirectQueueTotalPositionSeekTarget(
  chapterStartsSeconds: List<Double>,
  chapterEndsSeconds: List<Double>,
  totalPositionSeconds: Double,
): DirectQueueSeekTarget? {
  if (chapterStartsSeconds.isEmpty() || chapterStartsSeconds.size != chapterEndsSeconds.size) {
    return null
  }

  val safeTotalPosition = totalPositionSeconds.coerceAtLeast(0.0)
  val chapterIndex =
    chapterStartsSeconds.indices.firstOrNull { index ->
      val startSeconds = chapterStartsSeconds[index]
      val endSeconds = chapterEndsSeconds[index]

      startSeconds.isFinite() &&
        endSeconds.isFinite() &&
        endSeconds > startSeconds &&
        safeTotalPosition >= startSeconds &&
        (safeTotalPosition < endSeconds || (index == chapterStartsSeconds.lastIndex && safeTotalPosition <= endSeconds))
    } ?: return null

  val chapterPositionMs =
    ((safeTotalPosition - chapterStartsSeconds[chapterIndex]).coerceAtLeast(0.0) * 1000).toLong()

  return DirectQueueSeekTarget(
    mediaItemIndex = chapterIndex,
    positionMs = chapterPositionMs,
  )
}

fun usesDirectFileQueue(book: DetailedItem): Boolean =
  book.files.size == book.chapters.size &&
    book.files.zip(book.chapters).all { (file, chapter) -> file.id == chapter.id }

fun shouldResetAppliedSkipsAfterPositionDiscontinuity(
  oldMediaItemIndex: Int,
  newMediaItemIndex: Int,
  oldPositionMs: Long,
  newPositionMs: Long,
): Boolean = oldMediaItemIndex != newMediaItemIndex || newPositionMs < oldPositionMs

fun shouldCompleteTimerAtAutomaticOutroBoundary(
  timerOption: TimerOption?,
  stage: SleepTimerStage = initialSleepTimerStage(timerOption),
): Boolean = shouldAdjustCurrentItemSleepTimer(timerOption, stage)

fun resolveCurrentChapterTimerDelaySeconds(
  chapterDurationSeconds: Double,
  chapterPositionSeconds: Double,
  playbackSpeed: Float,
): Double? {
  val safeChapterDuration = chapterDurationSeconds.takeIf { it > 0.0 } ?: return null
  val safePlaybackSpeed = playbackSpeed.takeIf { it > 0.0f } ?: return null
  val safeChapterPosition = chapterPositionSeconds.coerceIn(0.0, safeChapterDuration)

  return (safeChapterDuration - safeChapterPosition) / safePlaybackSpeed.toDouble()
}

private fun isFirstSkipForMediaItem(
  currentMediaItemId: String?,
  lastAppliedMediaItemId: String?,
): Boolean = currentMediaItemId != null && currentMediaItemId != lastAppliedMediaItemId
