package io.github.hobin66.webdavplayer.playback.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import io.github.hobin66.webdavplayer.channel.webdav.resolveDirectQueueTotalPositionSeconds
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.PlaybackProgress
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSynchronizationService
  @Inject
  constructor(
    private val exoPlayer: ExoPlayer,
    private val mediaChannel: WebdavMediaProvider,
    private val preferences: WebdavPlayerPreferences,
  ) {
    @Volatile
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
      snapshotJob = null
      currentItem = preferences.getPlayingItem()?.takeIf { it.id == item.id } ?: item
      lastSnapshotAtMs = null
    }

    fun updatePlaybackSynchronizationItem(item: DetailedItem) {
      currentItem = item
    }

    fun cancelSynchronization() {
      serviceScope.coroutineContext.cancelChildren()
      snapshotJob = null
      currentItem = null
      lastSnapshotAtMs = null
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
              if (snapshotJob === job) {
                snapshotJob = null
              }
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

      if (overallProgress.isTotalPositionReliable && overallProgress.currentTotalTime == 0.0) {
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
        } catch (error: CancellationException) {
          throw error
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

    private fun getProgress(exoPlayer: ExoPlayer): PlaybackProgress? {
      val chapterPositionSeconds = exoPlayer.currentPosition.coerceAtLeast(0L) / 1000.0
      val index = exoPlayer.currentMediaItemIndex
      val bookFromTag = exoPlayer.currentMediaItem?.localConfiguration?.tag as? DetailedItem
      val mediaBookId =
        exoPlayer.currentMediaItem
          ?.mediaId
          ?.let(WebdavPlayerMediaSourceFactory.MediaId::fromString)
          ?.bookId
      val book =
        currentItem?.takeIf { mediaBookId == null || it.id == mediaBookId }
          ?: bookFromTag?.takeIf { mediaBookId == null || it.id == mediaBookId }

      if (book != null && book.isDirectFileQueue() && index >= 0) {
        val total =
          resolveDirectQueueTotalPositionSeconds(
            chapters = book.chapters,
            currentIndex = index,
            currentPositionSeconds = chapterPositionSeconds,
          )
        return PlaybackProgress(
          currentTotalTime = total ?: 0.0,
          currentChapterTime = chapterPositionSeconds,
          isTotalPositionReliable = total != null,
        )
      }

      return exoPlayer.currentMediaItem
        ?.mediaMetadata
        ?.extras
        ?.getLong(CHAPTER_START_MS, -1)
        ?.takeIf { it >= 0 }
        ?.let { currentChapterOffsetMs ->
          PlaybackProgress(
            currentTotalTime = (currentChapterOffsetMs + exoPlayer.currentPosition) / 1000.0,
            currentChapterTime = chapterPositionSeconds,
          )
        }
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
