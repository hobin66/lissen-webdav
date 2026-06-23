package io.github.hobin66.webdavplayer.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.channel.common.OperationError
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.content.PlaybackProgressSyncDirection
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.CurrentItemTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem.Companion.same
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerOption
import io.github.hobin66.webdavplayer.lib.domain.SeekTimeOption
import io.github.hobin66.webdavplayer.lib.domain.TimerOption
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.service.PlaybackService
import io.github.hobin66.webdavplayer.playback.service.PlaybackService.Companion.ACTION_SEEK_TO
import io.github.hobin66.webdavplayer.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import io.github.hobin66.webdavplayer.playback.service.PlaybackService.Companion.POSITION
import io.github.hobin66.webdavplayer.playback.service.PlaybackService.Companion.TIMER_OPTION_EXTRA
import io.github.hobin66.webdavplayer.playback.service.PlaybackService.Companion.TIMER_VALUE_EXTRA
import io.github.hobin66.webdavplayer.playback.service.PlaybackEvent
import io.github.hobin66.webdavplayer.playback.service.PlaybackEvents
import io.github.hobin66.webdavplayer.playback.service.calculateChapterIndex
import io.github.hobin66.webdavplayer.playback.service.calculateChapterIndexAndPosition
import io.github.hobin66.webdavplayer.playback.service.calculateChapterPosition
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MediaRepository
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: WebdavPlayerPreferences,
    private val mediaChannel: WebdavMediaProvider,
  ) {
    private lateinit var mediaController: MediaController

    private val token =
      SessionToken(
        context,
        ComponentName(context, PlaybackService::class.java),
      )

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _timerOption = MutableLiveData<TimerOption?>()
    val timerOption = _timerOption

    private val _timerRemaining = MutableLiveData<Long>()
    val timerRemaining = _timerRemaining

    private var sleepTimerStage = SleepTimerStage.IDLE

    private val _playAfterPrepare = MutableLiveData(false)
    private val _isPlaybackReady = MutableLiveData(false)
    val isPlaybackReady: LiveData<Boolean> = _isPlaybackReady

    private val _totalPosition = MutableLiveData<Double>()
    val totalPosition: LiveData<Double> = _totalPosition

    private val _playingBook = MutableLiveData<DetailedItem?>()
    val playingBook: LiveData<DetailedItem?> = _playingBook

    private val _mediaPreparingError = MutableLiveData<Boolean>()
    val mediaPreparingError: LiveData<Boolean> = _mediaPreparingError

    private val _bookSkipSaveMessageRes = MutableLiveData<Int?>(null)
    val bookSkipSaveMessageRes: LiveData<Int?> = _bookSkipSaveMessageRes

    private val _playbackSpeed = MutableLiveData(preferences.getPlaybackSpeed())
    val playbackSpeed: LiveData<Float> = _playbackSpeed

    private val bookSkipSaveMutex = Mutex()

    private val _currentChapterIndex =
      MediatorLiveData<Int>().apply {
        addSource(totalPosition) { updateCurrentTrackData() }
        addSource(playingBook) { updateCurrentTrackData() }
      }

    val currentChapterIndex: LiveData<Int> = _currentChapterIndex

    private val _currentChapterPosition =
      MediatorLiveData<Double>().apply {
        addSource(totalPosition) { updateCurrentTrackData() }
        addSource(playingBook) { updateCurrentTrackData() }
      }

    val currentChapterPosition: LiveData<Double> = _currentChapterPosition

    private val _currentChapterDuration =
      MediatorLiveData<Double>().apply {
        addSource(totalPosition) { updateCurrentTrackData() }
        addSource(playingBook) { updateCurrentTrackData() }
      }

    private val _bookmarks = MutableLiveData<List<Bookmark>>()
    val bookmarks: LiveData<List<Bookmark>> = _bookmarks

    val currentChapterDuration: LiveData<Double> = _currentChapterDuration

    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable =
      object : Runnable {
        override fun run() {
          val updateIntervalMs = resolvePlaybackProgressUpdateIntervalMs(_isPlaying.value == true) ?: return
          val detailedItem = _playingBook.value ?: return

          updateProgress(detailedItem)
          handler.postDelayed(this, updateIntervalMs)
        }
      }

    init {
      val controllerBuilder = MediaController.Builder(context, token)
      val futureController = controllerBuilder.buildAsync()

      Futures.addCallback(
        futureController,
        object : FutureCallback<MediaController> {
          override fun onSuccess(controller: MediaController) {
            mediaController = controller
            CoroutineScope(Dispatchers.Main.immediate).launch {
              PlaybackEvents.events.collectLatest { event ->
                when (event) {
                  PlaybackEvent.PlaybackReady -> handlePlaybackReady()
                  PlaybackEvent.TimerExpired -> handleTimerExpired()
                  is PlaybackEvent.TimerTick -> _timerRemaining.postValue(event.remainingSeconds)
                }
              }
            }

            mediaController.addListener(
              object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                  _isPlaying.value = isPlaying
                  updateProgressLoop(isPlaying)
                }

                override fun onMediaItemTransition(
                  mediaItem: androidx.media3.common.MediaItem?,
                  reason: Int,
                ) {
                  if (
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    shouldAdjustCurrentItemSleepTimer(_timerOption.value, sleepTimerStage)
                  ) {
                    updateTimer(timerOption = null)
                    pause()
                  }
                }

                override fun onPositionDiscontinuity(
                  oldPosition: Player.PositionInfo,
                  newPosition: Player.PositionInfo,
                  reason: Int,
                ) {
                  syncPlaybackProgressAfterPositionChange()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                  if (playbackState == Player.STATE_ENDED) {
                    updateProgressLoop(false)
                    if (shouldAdjustCurrentItemSleepTimer(_timerOption.value, sleepTimerStage)) {
                      updateTimer(timerOption = null)
                    }
                    mediaController.seekTo(0, 0)
                    mediaController.pause()
                  }
                }
              },
            )
          }

          override fun onFailure(t: Throwable) {
            Timber.e("Unable to add callback to player")
          }
        },
        MoreExecutors.directExecutor(),
      )
    }

    private fun handlePlaybackReady() {
      val book = preferences.getPlayingItem() ?: return

      CoroutineScope(Dispatchers.Main).launch {
        updateProgress(book)
        updateProgressLoop(mediaController.isPlaying)
        _isPlaybackReady.postValue(true)

        if (_playAfterPrepare.value == true) {
          _playAfterPrepare.postValue(false)
          play()
        }
      }
    }

    fun updateTimer(
      timerOption: TimerOption?,
      position: Double? = null,
    ) {
      _timerOption.postValue(timerOption)
      sleepTimerStage = initialSleepTimerStage(timerOption)

      when (timerOption) {
        is DurationTimerOption -> {
          scheduleServiceTimer(timerOption.duration * 60.0, timerOption)
        }

        is CurrentItemTimerOption -> {
          if (scheduleCurrentItemTimer(position).not()) {
            _timerOption.postValue(null)
            sleepTimerStage = SleepTimerStage.IDLE
          }
        }

        null -> {
          sleepTimerStage = SleepTimerStage.IDLE
          cancelServiceTimer()
        }
      }
    }

    fun rewind() {
      val book = playingBook.value ?: return
      if (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
        seekDirectQueueBy(-getSeekTime(preferences.getSeekTime().rewind))
        return
      }

      totalPosition
        .value
        ?.let { seekTo(it - getSeekTime(preferences.getSeekTime().rewind)) }
    }

    fun forward() {
      val book = playingBook.value ?: return
      if (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
        seekDirectQueueBy(getSeekTime(preferences.getSeekTime().forward))
        return
      }

      totalPosition
        .value
        ?.let { seekTo(it + getSeekTime(preferences.getSeekTime().forward)) }
    }

    fun setChapter(index: Int) {
      val book = playingBook.value ?: return
      if (index !in book.chapters.indices) {
        return
      }

      if (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
        mediaController.seekTo(index, 0)
        return
      }

      try {
        val chapterStartsAt =
          book
            .chapters[index]
            .start

        seekTo(chapterStartsAt)
      } catch (ex: Exception) {
        return
      }
    }

    suspend fun setChapter(chapter: io.github.hobin66.webdavplayer.lib.domain.PlayingChapter) {
      val currentBook = playingBook.value ?: return
      val index = currentBook.chapters.indexOfFirst { it.id == chapter.id }
      if (index >= 0) {
        setChapter(index)
      }
    }

    fun clearPlayingBook() {
      timerOption.value?.let { updateTimer(timerOption = null) }
      _playAfterPrepare.postValue(false)
      pause()

      (_playingBook.value ?: preferences.getPlayingItem())
        ?.id
        ?.let(BookSkipSettingsStore::remove)

      _isPlaybackReady.postValue(false)
      _mediaPreparingError.postValue(false)
      _isPlaying.postValue(false)
      _timerOption.postValue(null)
      _timerRemaining.postValue(0L)
      _totalPosition.postValue(0.0)
      _currentChapterIndex.postValue(0)
      _currentChapterPosition.postValue(0.0)
      _currentChapterDuration.postValue(0.0)
      _bookmarks.postValue(emptyList())
      _playingBook.postValue(null)
      preferences.clearPlayingItem()
    }

    fun setTotalPosition(totalPosition: Double) {
      seekTo(totalPosition)
    }

    fun setChapterPosition(chapterPosition: Double) {
      val book = playingBook.value ?: return
      val currentIndex =
        when (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
          true -> {
            mediaController.currentMediaItemIndex
          }

          false -> {
            val overallPosition = totalPosition.value ?: return
            calculateChapterIndex(book, overallPosition)
          }
        }

      if (currentIndex < 0) {
        return
      }

      if (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
        mediaController.seekTo(currentIndex, (chapterPosition * 1000).toLong())
        return
      }

      try {
        val absolutePosition =
          currentIndex
            .let { chapterIndex -> book.chapters[chapterIndex].start }
            .let { it + chapterPosition }

        seekTo(absolutePosition)
      } catch (ex: Exception) {
        return
      }
    }

    fun completeCurrentChapterTimerAtAutomaticOutroBoundary(): Boolean {
      if (!shouldCompleteTimerAtAutomaticOutroBoundary(_timerOption.value, sleepTimerStage)) {
        return false
      }

      updateTimer(timerOption = null)
      pause()
      return true
    }

    fun rescheduleCurrentChapterTimer(
      chapterDurationSeconds: Double,
      chapterPositionSeconds: Double,
    ): Boolean {
      if (!shouldAdjustCurrentItemSleepTimer(_timerOption.value, sleepTimerStage)) {
        return false
      }

      return scheduleCurrentChapterTimer(
        chapterDurationSeconds = chapterDurationSeconds,
        chapterPositionSeconds = chapterPositionSeconds,
      )
    }

    fun prepareAndPlay(book: DetailedItem) {
      when (isPlaybackReady.value) {
        true -> {
          play()
        }

        else -> {
          _playAfterPrepare.postValue(true)
          startPreparingPlayback(book, forceReload = false)
        }
      }
    }

    fun togglePlayPause() {
      if (currentChapterIndex.value == -1) {
        Timber.w("Tried to toggle play/pause in the empty book. Skipping")
        return
      }

      when (isPlaying.value) {
        true -> pause()
        else -> play()
      }
    }

    fun setPlaybackSpeed(factor: Float) {
      val speed =
        when {
          factor < 0.5f -> 0.5f
          factor > 3f -> 3f
          else -> factor
        }

      if (::mediaController.isInitialized) {
        mediaController.setPlaybackSpeed(speed)
      }

      _playbackSpeed.postValue(speed)
      preferences.savePlaybackSpeed(speed)

      _totalPosition.value?.let { adjustTimer(it) }
    }

    suspend fun preparePlayback(bookId: String) {
      preparePlayback(bookId = bookId, forceReload = false)
    }

    suspend fun syncCurrentBookProgress(
      bookId: String,
      direction: PlaybackProgressSyncDirection,
    ): OperationResult<Unit> = mediaChannel.syncPlaybackProgress(bookId, direction)

    suspend fun refreshCurrentBook(bookId: String): OperationResult<Unit> {
      _playAfterPrepare.postValue(false)
      pause()
      clearPreparedItem()

      val refreshResult = mediaChannel.refreshItemCache(bookId)
      if (refreshResult is OperationResult.Error) {
        return OperationResult.Error(refreshResult.code, refreshResult.message)
      }

      return preparePlayback(bookId = bookId, forceReload = true)
    }

    suspend fun preparePlayback(
      bookId: String,
      forceReload: Boolean,
    ): OperationResult<Unit> =
      coroutineScope {
        withContext(Dispatchers.IO) {
          mediaChannel
            .fetchBook(bookId)
            .foldAsync(
              onSuccess = {
                startPreparingPlayback(it, forceReload)
                OperationResult.Success(Unit)
              },
              onFailure = {
                _mediaPreparingError.postValue(true)
                OperationResult.Error(it.code, it.message)
              },
            )
        }
      }

    fun nextTrack() {
      val book = playingBook.value ?: return
      val currentIndex = currentChapterIndex.value ?: return

      val nextChapterIndex = currentIndex + 1
      setChapter(nextChapterIndex)
    }

    fun previousTrack(rewindRequired: Boolean = true) {
      val book = playingBook.value ?: return
      val currentIndex = currentChapterIndex.value ?: return
      val chapterPosition =
        when (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
          true -> {
            mediaController.currentPosition / 1000.0
          }

          false -> {
            val overallPosition = totalPosition.value ?: return
            calculateChapterIndexAndPosition(book, overallPosition).position
          }
        }

      val currentIndexReplay = (chapterPosition > CURRENT_TRACK_REPLAY_THRESHOLD || currentIndex == 0)

      when {
        currentIndexReplay && rewindRequired -> setChapter(currentIndex)
        currentIndex > 0 -> setChapter(currentIndex - 1)
      }
    }

    private fun scheduleServiceTimer(
      delay: Double,
      option: TimerOption,
    ) {
      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = PlaybackService.ACTION_SET_TIMER
          putExtra(TIMER_VALUE_EXTRA, delay)
          putExtra(TIMER_OPTION_EXTRA, option)
        }

      context.startService(intent)
    }

    private fun scheduleCurrentChapterTimer(
      chapterDurationSeconds: Double,
      chapterPositionSeconds: Double,
    ): Boolean {
      val delay =
        resolveCurrentChapterTimerDelaySeconds(
          chapterDurationSeconds = chapterDurationSeconds,
          chapterPositionSeconds = chapterPositionSeconds,
          playbackSpeed = preferences.getPlaybackSpeed(),
        ) ?: return false

      scheduleServiceTimer(
        delay = delay,
        option = CurrentItemTimerOption,
      )
      return true
    }

    private fun cancelServiceTimer() {
      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = PlaybackService.ACTION_CANCEL_TIMER
        }

      context.startService(intent)
    }

    fun clearPreparedItem() {
      timerOption
        .value
        ?.let { updateTimer(timerOption = null) }

      _mediaPreparingError.postValue(false)
      _isPlaybackReady.postValue(false)
    }

    private fun startPreparingPlayback(
      book: DetailedItem,
      forceReload: Boolean,
    ) {
      if (shouldPreparePlaybackBook(_playingBook.value, book, forceReload = forceReload)) {
        _totalPosition.postValue(0.0)
        _isPlaying.postValue(false)

        BookSkipSettingsStore.put(
          book.id,
          BookSkipSettings(
            introSkipSeconds = book.introSkipSeconds,
            outroSkipSeconds = book.outroSkipSeconds,
          ),
        )
        _playingBook.postValue(book)
        preferences.savePlayingItem(book)

        val intent =
          Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SET_PLAYBACK
          }

        when (inBackground()) {
          true -> context.startForegroundService(intent)
          false -> context.startService(intent)
        }
      }
    }

    private fun updateProgress(detailedItem: DetailedItem) {
      val nextTotalPosition = resolveCurrentTotalPositionSeconds(detailedItem) ?: return
      postIfChanged(_totalPosition, nextTotalPosition)
    }

    private fun updateProgressLoop(isPlaying: Boolean) {
      handler.removeCallbacks(progressUpdateRunnable)

      resolvePlaybackProgressUpdateIntervalMs(isPlaying)?.let { updateIntervalMs ->
        handler.postDelayed(progressUpdateRunnable, updateIntervalMs)
      }
    }

    private fun syncPlaybackProgressAfterPositionChange() {
      val detailedItem = _playingBook.value ?: return
      val nextTotalPosition = resolveCurrentTotalPositionSeconds(detailedItem) ?: return

      if (
        shouldRefreshPlaybackProgressOnPositionDiscontinuity(
          isPlaying = _isPlaying.value == true,
          previousTotalPositionSeconds = _totalPosition.value,
          currentTotalPositionSeconds = nextTotalPosition,
        )
      ) {
        postIfChanged(_totalPosition, nextTotalPosition)
      }
    }

    private fun resolveCurrentTotalPositionSeconds(detailedItem: DetailedItem): Double? {
      val currentTotalPosition =
        resolveTotalPositionSeconds(
          chapterStartOffsetMs =
            mediaController.currentMediaItem
              ?.mediaMetadata
              ?.extras
              ?.getLong(CHAPTER_START_MS, -1)
              ?.takeIf { it >= 0L },
          chapterPositionMs = mediaController.currentPosition,
        )

      return currentTotalPosition
        ?: run {
          val currentIndex = mediaController.currentMediaItemIndex
          if (currentIndex < 0) {
            return null
          }

          val accumulated =
            detailedItem.chapters
              .take(currentIndex)
              .sumOf { duration -> duration.duration.takeIf { it > 0.0 } ?: 0.0 }
          val currentFilePosition = mediaController.currentPosition / 1000.0

          accumulated + currentFilePosition
        }
    }

    private fun play() {
      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = PlaybackService.ACTION_PLAY
        }

      context.startForegroundService(intent)
    }

    private fun pause() {
      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = PlaybackService.ACTION_PAUSE
        }

      context.startService(intent)
    }

    private fun seekTo(position: Double) {
      val book = playingBook.value ?: return

      if (book.chapters.isEmpty()) {
        Timber.d("Tried to seek on the empty book")
        return
      }

      if (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
        seekDirectQueueToTotalPosition(book, position)
        return
      }

      val overallDuration =
        book
          .chapters
          .sumOf { it.duration }

      val current = totalPosition.value ?: 0.0

      val direction =
        when (current > maxOf(0.0, position)) {
          true -> ScrollingDirection.BACKWARD
          false -> ScrollingDirection.FORWARD
        }

      var safePosition = minOf(overallDuration, maxOf(0.0, position))

      while (book.chapters[calculateChapterIndex(book, safePosition)].available.not()) {
        val chapterIndex =
          when (direction) {
            ScrollingDirection.FORWARD -> calculateChapterIndex(book, safePosition) + 1
            ScrollingDirection.BACKWARD -> calculateChapterIndex(book, safePosition) - 1
          }

        safePosition =
          when {
            chapterIndex in 0..book.chapters.lastIndex -> book.chapters[chapterIndex].start
            else -> break
          }
      }

      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = ACTION_SEEK_TO
          putExtra(POSITION, safePosition)
        }

      context.startService(intent)
      adjustTimer(safePosition)
    }

    private fun seekDirectQueueBy(seekOffsetSeconds: Long) {
      resolveDirectQueueRelativeSeekTarget(
        currentMediaItemIndex = mediaController.currentMediaItemIndex,
        mediaItemCount = mediaController.mediaItemCount,
        currentPositionMs = mediaController.currentPosition,
        currentDurationMs = mediaController.duration,
        seekOffsetMs = seekOffsetSeconds * 1000L,
      )?.let(::seekToDirectQueueTarget)
    }

    private fun seekDirectQueueToTotalPosition(
      book: DetailedItem,
      totalPosition: Double,
    ) {
      resolveDirectQueueTotalPositionSeekTarget(
        chapterStartsSeconds = book.chapters.map { it.start },
        chapterEndsSeconds = book.chapters.map { it.end },
        totalPositionSeconds = totalPosition,
      )?.let(::seekToDirectQueueTarget)
    }

    private fun seekToDirectQueueTarget(target: DirectQueueSeekTarget) {
      val shouldRescheduleTimer = target.mediaItemIndex == mediaController.currentMediaItemIndex
      mediaController.seekTo(target.mediaItemIndex, target.positionMs)

      if (shouldRescheduleTimer) {
        rescheduleDirectQueueTimer(target.positionMs)
      }
    }

    private fun rescheduleDirectQueueTimer(chapterPositionMs: Long) {
      mediaController
        .duration
        .takeIf { it > 0L }
        ?.div(1000.0)
        ?.let { chapterDurationSeconds ->
          rescheduleCurrentChapterTimer(
            chapterDurationSeconds = chapterDurationSeconds,
            chapterPositionSeconds = chapterPositionMs.coerceAtLeast(0L) / 1000.0,
          )
        }
    }

    private fun adjustTimer(position: Double) {
      if (shouldAdjustCurrentItemSleepTimer(_timerOption.value, sleepTimerStage)) {
        scheduleCurrentItemTimer(position)
      }
    }

    private fun handleTimerExpired() {
      when (resolveSleepTimerExpiryAction(_timerOption.value, sleepTimerStage)) {
        SleepTimerExpiryAction.SWITCH_TO_CURRENT_ITEM_END -> {
          sleepTimerStage = SleepTimerStage.WAITING_FOR_CURRENT_ITEM_END

          if (scheduleCurrentItemTimer().not()) {
            Timber.w("Unable to schedule current-item timer after countdown expiry. Falling back to chapter-boundary stop.")
          }
        }

        SleepTimerExpiryAction.PAUSE_NOW -> {
          _timerOption.postValue(null)
          sleepTimerStage = SleepTimerStage.IDLE
          pause()
        }
      }
    }

    private fun scheduleCurrentItemTimer(position: Double? = null): Boolean {
      val snapshot = resolveCurrentItemTimerSnapshot(position = position) ?: return false
      return scheduleCurrentChapterTimer(
        chapterDurationSeconds = snapshot.chapterDurationSeconds,
        chapterPositionSeconds = snapshot.chapterPositionSeconds,
      )
    }

    private fun resolveCurrentItemTimerSnapshot(position: Double? = null): CurrentItemTimerSnapshot? {
      val currentBook = playingBook.value
      val currentPosition = position ?: totalPosition.value
      if (currentBook != null && currentPosition != null) {
        val (chapterIndex, chapterPosition) = calculateChapterIndexAndPosition(currentBook, currentPosition)
        val chapterDuration =
          currentBook
            .chapters
            .getOrNull(chapterIndex)
            ?.duration
            ?.takeIf { it > 0.0 }

        if (chapterDuration != null) {
          return CurrentItemTimerSnapshot(
            chapterDurationSeconds = chapterDuration,
            chapterPositionSeconds = chapterPosition,
          )
        }
      }

      val chapterDuration =
        currentChapterDuration.value?.takeIf { it > 0.0 }
          ?: when (::mediaController.isInitialized) {
            true -> mediaController.duration.takeIf { it > 0L }?.div(1000.0)
            false -> null
          }
          ?: return null

      val chapterPosition =
        currentChapterPosition.value?.takeIf { it >= 0.0 }
          ?: when (::mediaController.isInitialized) {
            true -> mediaController.currentPosition.coerceAtLeast(0L) / 1000.0
            false -> null
          }
          ?: return null

      return CurrentItemTimerSnapshot(
        chapterDurationSeconds = chapterDuration,
        chapterPositionSeconds = chapterPosition,
      )
    }

    private fun updateCurrentTrackData() {
      val book = playingBook.value ?: return
      val (trackIndex, trackPosition) =
        when (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
          true -> {
            val index = mediaController.currentMediaItemIndex.coerceAtLeast(0)
            index to (mediaController.currentPosition / 1000.0)
          }

          false -> {
            val totalPosition = totalPosition.value ?: return
            calculateChapterIndexAndPosition(book, totalPosition).let { it.index to it.position }
          }
        }

      val nextDuration =
        book
          .chapters
          .getOrNull(trackIndex)
          ?.duration
          ?.takeIf { it > 0.0 }
          ?: when (::mediaController.isInitialized) {
            true -> mediaController.duration.takeIf { it > 0 }?.div(1000.0)
            false -> null
          }
          ?: 0.0

      postIfChanged(_currentChapterIndex, trackIndex)
      postIfChanged(_currentChapterPosition, trackPosition)
      postIfChanged(_currentChapterDuration, nextDuration)
    }

    private fun usesDirectFileQueue(book: DetailedItem): Boolean =
      book.files.size == book.chapters.size &&
        book.files.zip(book.chapters).all { (file, chapter) -> file.id == chapter.id }

    suspend fun createBookmark() {
      val playingBook = _playingBook.value ?: return
      val chapterIndex = _currentChapterIndex.value ?: return
      val chapterPosition = _currentChapterPosition.value ?: return
      val totalPosition = _totalPosition.value ?: return
      val chapterId =
        playingBook
          .chapters
          .getOrNull(chapterIndex)
          ?.id
          ?: return

      mediaChannel
        .createBookmark(
          libraryItemId = playingBook.id,
          chapterId = chapterId,
          chapterPosition = chapterPosition,
          totalPosition = totalPosition,
        )

      _bookmarks.value = mediaChannel.provideBookmarks(playingBook.id)
    }

    suspend fun dropBookmark(bookmark: Bookmark) {
      mediaChannel.dropBookmark(bookmark = bookmark)

      _bookmarks.value = mediaChannel.provideBookmarks(bookmark.libraryItemId)
    }

    fun playBookmark(bookmark: Bookmark) {
      val book = playingBook.value ?: return

      if (usesDirectFileQueue(book) && ::mediaController.isInitialized) {
        val chapterId = bookmark.chapterId
        val chapterPosition = bookmark.chapterPosition

        if (chapterId != null && chapterPosition != null) {
          val target = resolveDirectQueueChapterSeekTarget(book.chapters, chapterId, chapterPosition)
          if (target != null) {
            seekToDirectQueueTarget(target)
            return
          }
        }
      }

      val absolutePosition =
        bookmark.chapterId
          ?.let { chapterId -> book.chapters.indexOfFirst { it.id == chapterId } }
          ?.takeIf { it >= 0 }
          ?.let { chapterIndex ->
            val chapterStart = book.chapters[chapterIndex].start
            chapterStart + (bookmark.chapterPosition ?: 0.0).coerceAtLeast(0.0)
          } ?: bookmark.totalPosition

      setTotalPosition(absolutePosition)
    }

    suspend fun updateBookmarks() {
      val book = playingBook.value ?: return
      val bookmarks = withContext(Dispatchers.IO) { mediaChannel.updateAndProvideBookmarks(book.id) }

      _bookmarks.value = bookmarks
    }

    suspend fun saveBookSkipSettings(
      bookId: String,
      introSkipSeconds: Int,
      outroSkipSeconds: Int,
    ): OperationResult<Unit> =
      bookSkipSaveMutex.withLock {
        val settings =
          BookSkipSettings(
            introSkipSeconds = introSkipSeconds,
            outroSkipSeconds = outroSkipSeconds,
          )

        val result =
          mediaChannel.updateBookSkipSettings(
            itemId = bookId,
            introSkipSeconds = settings.normalizedIntroSkipSeconds,
            outroSkipSeconds = settings.normalizedOutroSkipSeconds,
          )

        if (result is OperationResult.Success) {
          withContext(Dispatchers.Main.immediate) {
            val currentBook = _playingBook.value?.takeIf { it.id == bookId } ?: return@withContext
            val updatedBook =
              currentBook.copy(
                introSkipSeconds = settings.normalizedIntroSkipSeconds,
                outroSkipSeconds = settings.normalizedOutroSkipSeconds,
              )
            BookSkipSettingsStore.put(bookId, settings)
            _playingBook.value = updatedBook
            preferences.savePlayingItem(updatedBook)
          }
          return@withLock result
        }

        if (result is OperationResult.Error) {
          _bookSkipSaveMessageRes.postValue(result.toBookSkipSaveMessageRes())
        }
        result
      }

    fun consumeBookSkipSaveMessage() {
      _bookSkipSaveMessageRes.postValue(null)
    }

    private companion object {
      private const val CURRENT_TRACK_REPLAY_THRESHOLD = 5

      private fun getSeekTime(option: SeekTimeOption?): Long =
        when (option) {
          SeekTimeOption.SEEK_5 -> 5L
          SeekTimeOption.SEEK_10 -> 10L
          SeekTimeOption.SEEK_15 -> 15L
          SeekTimeOption.SEEK_30 -> 30L
          SeekTimeOption.SEEK_60 -> 60L
          else -> 30L
        }

      private fun inBackground(): Boolean =
        ProcessLifecycleOwner
          .get()
          .lifecycle
          .currentState
          .isAtMost(Lifecycle.State.STARTED)

      private fun Lifecycle.State.isAtMost(state: Lifecycle.State) = this <= state
    }

    private data class CurrentItemTimerSnapshot(
      val chapterDurationSeconds: Double,
      val chapterPositionSeconds: Double,
    )

    private fun OperationResult.Error<Unit>.toBookSkipSaveMessageRes(): Int =
      when (code) {
        OperationError.ConflictError -> R.string.login_error_metadata_conflict
        else -> R.string.player_skip_save_failed
      }

    private fun <T> postIfChanged(
      liveData: MutableLiveData<T>,
      value: T,
    ) {
      if (liveData.value != value) {
        liveData.postValue(value)
      }
    }
  }

enum class ScrollingDirection {
  FORWARD,
  BACKWARD,
}

fun shouldPreparePlaybackBook(
  currentBook: DetailedItem?,
  nextBook: DetailedItem,
  forceReload: Boolean,
): Boolean =
  when {
    forceReload -> true
    else -> !(currentBook?.same(nextBook) ?: false)
  }
