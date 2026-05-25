package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.lib.domain.DetailedItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlaybackSkipService
  @Inject
  constructor(
    private val exoPlayer: ExoPlayer,
    private val mediaRepository: MediaRepository,
  ) : RunningComponent {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var watchJob: Job? = null
    private var lastAppliedIntroMediaItemId: String? = null
    private var lastAppliedOutroMediaItemId: String? = null
    private var pendingDirectIntroTimerReschedule: PendingDirectIntroTimerReschedule? = null

    override fun onCreate() {
      exoPlayer.addListener(
        object : Player.Listener {
          override fun onTimelineChanged(
            timeline: androidx.media3.common.Timeline,
            reason: Int,
          ) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
              resetAppliedSkips()
            }
          }

          override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
          ) {
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
              resetAppliedSkips()
              return
            }

            if (
              shouldResetAppliedSkipsAfterPositionDiscontinuity(
                oldMediaItemIndex = oldPosition.mediaItemIndex,
                newMediaItemIndex = newPosition.mediaItemIndex,
                oldPositionMs = oldPosition.positionMs,
                newPositionMs = newPosition.positionMs,
              )
            ) {
              resetAppliedSkipsForMediaItem(exoPlayer.currentMediaItem?.mediaId)
            }
          }

          override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
          ) {
            resetAppliedSkips()
          }

          override fun onEvents(
            player: Player,
            events: Player.Events,
          ) {
            if (skipEvents.any(events::contains)) {
              updateWatchLoop()
              evaluateSkip()
            }
          }
        },
      )

      updateWatchLoop()
      evaluateSkip()
    }

    private fun updateWatchLoop() {
      if (exoPlayer.isPlaying) {
        if (watchJob?.isActive == true) {
          return
        }

        watchJob =
          serviceScope
            .launch {
              while (exoPlayer.isPlaying) {
                evaluateSkip()
                delay(SKIP_CHECK_INTERVAL_MS)
              }
            }.also { job ->
              job.invokeOnCompletion {
                if (watchJob === job) {
                  watchJob = null
                }
              }
            }
        return
      }

      watchJob?.cancel()
      watchJob = null
    }

    private fun evaluateSkip() {
      if (!shouldEvaluateBookSkip(exoPlayer.isPlaying)) {
        return
      }

      val currentMediaItem = exoPlayer.currentMediaItem ?: return
      val playbackBook = currentMediaItem.localConfiguration?.tag as? DetailedItem ?: return
      val currentBook =
        mediaRepository.playingBook.value
          ?.takeIf { it.id == playbackBook.id }
          ?: playbackBook
      val currentMediaItemId = currentMediaItem.mediaId
      val chapterPositionSeconds = exoPlayer.currentPosition.coerceAtLeast(0L) / 1000.0
      val playerChapterDurationSeconds = exoPlayer.duration.takeIf { it > 0L }?.div(1000.0)
      val settings = resolveSettings(currentBook)

      retryPendingDirectIntroTimerReschedule(
        currentMediaItemId = currentMediaItemId,
        chapterDurationSeconds = playerChapterDurationSeconds,
      )

      if (
        shouldApplyIntroSkip(
          settings = settings,
          chapterPositionSeconds = chapterPositionSeconds,
          currentMediaItemId = currentMediaItemId,
          lastAppliedMediaItemId = lastAppliedIntroMediaItemId,
        )
      ) {
        lastAppliedIntroMediaItemId = currentMediaItemId

        if (usesDirectFileQueue(currentBook)) {
          val targetChapterPositionSeconds = resolveIntroSkipTargetChapterPosition(settings)
          exoPlayer.seekTo(
            exoPlayer.currentMediaItemIndex,
            targetChapterPositionSeconds.secondsToMillis(),
          )
          if (playerChapterDurationSeconds != null) {
            mediaRepository.rescheduleCurrentChapterTimer(
              chapterDurationSeconds = playerChapterDurationSeconds,
              chapterPositionSeconds = targetChapterPositionSeconds,
            )
            pendingDirectIntroTimerReschedule = null
          } else {
            pendingDirectIntroTimerReschedule =
              createPendingDirectIntroTimerReschedule(
                currentMediaItemId = currentMediaItemId,
                targetChapterPositionSeconds = targetChapterPositionSeconds,
                chapterDurationSeconds = playerChapterDurationSeconds,
              )
          }
          return
        }

        val chapterStartSeconds =
          currentBook
            .chapters
            .getOrNull(exoPlayer.currentMediaItemIndex)
            ?.start
            ?: return

        mediaRepository.setTotalPosition(
          resolveIntroSkipTargetTotalPosition(
            chapterStartSeconds = chapterStartSeconds,
            settings = settings,
          ),
        )
        return
      }

      val chapterDurationSeconds =
        playerChapterDurationSeconds
          ?: currentBook
            .chapters
            .getOrNull(exoPlayer.currentMediaItemIndex)
            ?.duration
          ?: return

      if (
        shouldApplyOutroSkip(
          settings = settings,
          chapterPositionSeconds = chapterPositionSeconds,
          chapterDurationSeconds = chapterDurationSeconds,
          currentMediaItemId = currentMediaItemId,
          lastAppliedMediaItemId = lastAppliedOutroMediaItemId,
        )
      ) {
        val skipped =
          skipOutro(
            currentBook = currentBook,
            chapterDurationSeconds = chapterDurationSeconds,
          )

        if (skipped) {
          lastAppliedOutroMediaItemId = currentMediaItemId
        }
      }
    }

    private fun retryPendingDirectIntroTimerReschedule(
      currentMediaItemId: String?,
      chapterDurationSeconds: Double?,
    ) {
      val pending = pendingDirectIntroTimerReschedule
      if (
        !shouldRetryPendingDirectIntroTimerReschedule(
          pending = pending,
          currentMediaItemId = currentMediaItemId,
          chapterDurationSeconds = chapterDurationSeconds,
        )
      ) {
        return
      }

      mediaRepository.rescheduleCurrentChapterTimer(
        chapterDurationSeconds = requireNotNull(chapterDurationSeconds),
        chapterPositionSeconds = requireNotNull(pending).chapterPositionSeconds,
      )
      pendingDirectIntroTimerReschedule = null
    }

    private fun resolveSettings(currentBook: DetailedItem): BookSkipSettings {
      BookSkipSettingsStore.get(currentBook.id)?.let { return it }

      return BookSkipSettings(
        introSkipSeconds = currentBook.introSkipSeconds,
        outroSkipSeconds = currentBook.outroSkipSeconds,
      )
    }

    private fun resetAppliedSkips() {
      lastAppliedIntroMediaItemId = null
      lastAppliedOutroMediaItemId = null
      pendingDirectIntroTimerReschedule = null
    }

    private fun resetAppliedSkipsForMediaItem(mediaItemId: String?) {
      val targetMediaItemId = mediaItemId ?: return

      if (lastAppliedIntroMediaItemId == targetMediaItemId) {
        lastAppliedIntroMediaItemId = null
      }

      if (lastAppliedOutroMediaItemId == targetMediaItemId) {
        lastAppliedOutroMediaItemId = null
      }

      if (pendingDirectIntroTimerReschedule?.mediaItemId == targetMediaItemId) {
        pendingDirectIntroTimerReschedule = null
      }
    }

    private fun skipOutro(
      currentBook: DetailedItem,
      chapterDurationSeconds: Double,
    ): Boolean {
      if (usesDirectFileQueue(currentBook)) {
        val target =
          resolveDirectQueueOutroTarget(
            currentMediaItemIndex = exoPlayer.currentMediaItemIndex,
            mediaItemCount = exoPlayer.mediaItemCount,
            currentChapterDurationSeconds = chapterDurationSeconds,
          ) ?: return false

        if (mediaRepository.completeCurrentChapterTimerAtAutomaticOutroBoundary()) {
          return false
        }

        when (target) {
          is DirectQueueOutroTarget.NextMediaItem -> {
            exoPlayer.seekTo(target.mediaItemIndex, 0L)
          }

          is DirectQueueOutroTarget.FinalBoundary -> {
            exoPlayer.seekTo(
              exoPlayer.currentMediaItemIndex,
              target.chapterPositionSeconds.secondsToMillis(),
            )
          }
        }
        return true
      }

      val targetPosition =
        resolveOutroSkipTargetTotalPosition(
          currentChapterIndex = exoPlayer.currentMediaItemIndex,
          mediaItemCount = exoPlayer.mediaItemCount,
          chapterStartsSeconds = currentBook.chapters.map { it.start },
          currentChapterDurationSeconds = chapterDurationSeconds,
        ) ?: return false

      if (mediaRepository.completeCurrentChapterTimerAtAutomaticOutroBoundary()) {
        return false
      }

      mediaRepository.setTotalPosition(targetPosition)
      return true
    }

    private fun Double.secondsToMillis(): Long = (coerceAtLeast(0.0) * 1000).toLong()

    private companion object {
      private const val SKIP_CHECK_INTERVAL_MS = 250L

      private val skipEvents =
        listOf(
          Player.EVENT_IS_PLAYING_CHANGED,
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_POSITION_DISCONTINUITY,
        )
    }
  }
