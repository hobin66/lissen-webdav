package org.grakovne.lissen.playback.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSynchronizationService
  @Inject
  constructor(
    private val exoPlayer: ExoPlayer,
    private val mediaChannel: LissenMediaProvider,
  ) {
    private var currentItem: DetailedItem? = null
    private val serviceScope = MainScope()
    private var snapshotJob: Job? = null
    private val snapshotMutex = Mutex()
    private var lastSnapshotAtMs: Long? = null

    init {
      exoPlayer.addListener(
        object : Player.Listener {
          override fun onEvents(
            player: Player,
            events: Player.Events,
          ) {
            if (syncEvents.any(events::contains)) {
              handleSyncEvent()
            }
          }
        },
      )
    }

    fun startPlaybackSynchronization(item: DetailedItem) {
      serviceScope.coroutineContext.cancelChildren()
      currentItem = item
      lastSnapshotAtMs = null
    }

    fun cancelSynchronization() {
      snapshotJob?.cancel()
    }

    private fun handleSyncEvent() {
      persistLocalSnapshot(
        force = true,
        trigger = PlaybackSnapshotTrigger.EVENT,
      )
      ensureSnapshotLoop()
    }

    private fun ensureSnapshotLoop() {
      if (snapshotJob?.isActive == true) return

      snapshotJob =
        serviceScope
          .launch {
            while (
              snapshotJob?.isActive == true &&
              exoPlayer.playWhenReady &&
              exoPlayer.playbackState != Player.STATE_ENDED
            ) {
              delay(LOCAL_SNAPSHOT_INTERVAL)
              persistLocalSnapshot(
                force = false,
                trigger = PlaybackSnapshotTrigger.PERIODIC,
              )
            }
          }.also { job ->
            job.invokeOnCompletion {
              snapshotJob = null
            }
          }
    }

    private fun persistLocalSnapshot(
      force: Boolean,
      trigger: PlaybackSnapshotTrigger,
    ) {
      val overallProgress = getProgress(exoPlayer) ?: return
      val currentItem = currentItem ?: return
      val currentMediaItemIndex = exoPlayer.currentMediaItemIndex

      if (overallProgress.currentTotalTime == 0.0) {
        return
      }

      serviceScope.launch(Dispatchers.IO) {
        if (snapshotMutex.tryLock().not()) {
          return@launch
        }

        try {
          persistLocalSnapshotInternal(
            item = currentItem,
            currentMediaItemIndex = currentMediaItemIndex,
            overallProgress = overallProgress,
            force = force,
            trigger = trigger,
          )
        } catch (e: Exception) {
          Timber.e(e, "Error during local snapshot persistence")
        } finally {
          snapshotMutex.unlock()
        }
      }
    }

    private suspend fun persistLocalSnapshotInternal(
      item: DetailedItem,
      currentMediaItemIndex: Int,
      overallProgress: PlaybackProgress,
      force: Boolean,
      trigger: PlaybackSnapshotTrigger,
    ) {
      val now = System.currentTimeMillis()
      if (!force && !shouldPersistPlaybackSnapshot(lastSnapshotAtMs, now, LOCAL_SNAPSHOT_INTERVAL)) {
        return
      }

      val chapterId =
        item
          .chapters
          .getOrNull(currentMediaItemIndex)
          ?.id
          ?: return

      mediaChannel.persistPlaybackSnapshot(
        detailedItem = item,
        chapterId = chapterId,
        progress = overallProgress,
        trigger = trigger,
      )
      lastSnapshotAtMs = now
    }

    private fun getProgress(exoPlayer: ExoPlayer): PlaybackProgress? =
      exoPlayer.currentMediaItem
        ?.mediaMetadata
        ?.extras
        ?.getLong(CHAPTER_START_MS, -1)
        ?.takeIf { it >= 0 }
        ?.let { currentChapterOffsetMs ->
          PlaybackProgress(
            currentTotalTime = (currentChapterOffsetMs + exoPlayer.currentPosition) / 1000.0,
            currentChapterTime = exoPlayer.currentPosition / 1000.0,
          )
        }

    companion object {
      private const val LOCAL_SNAPSHOT_INTERVAL = 15_000L

      private val syncEvents =
        listOf(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          Player.EVENT_POSITION_DISCONTINUITY,
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_IS_PLAYING_CHANGED,
        )
    }
  }
