package io.github.hobin66.webdavplayer.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerStopMode
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import io.github.hobin66.webdavplayer.lib.domain.TimerOption
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.MediaRepository
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
class PlayerViewModel
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val preferences: WebdavPlayerPreferences,
  ) : ViewModel() {
    val book: LiveData<DetailedItem?> = mediaRepository.playingBook

    val currentChapterIndex: LiveData<Int> = mediaRepository.currentChapterIndex
    val currentChapterPosition: LiveData<Double> = mediaRepository.currentChapterPosition

    val currentChapterDuration: LiveData<Double> = mediaRepository.currentChapterDuration
    val totalPosition: LiveData<Double> = mediaRepository.totalPosition

    val timerOption: LiveData<TimerOption?> = mediaRepository.timerOption
    val timerRemaining: LiveData<Long?> = mediaRepository.timerRemaining

    private val _preferredSleepTimerStopMode =
      MutableLiveData(preferences.getPreferredSleepTimerStopMode())
    val preferredSleepTimerStopMode: LiveData<DurationTimerStopMode> = _preferredSleepTimerStopMode

    private val _playingQueueExpanded = MutableLiveData(false)
    val playingQueueExpanded: LiveData<Boolean> = _playingQueueExpanded

    val isPlaybackReady: LiveData<Boolean> = mediaRepository.isPlaybackReady
    val playbackSpeed: LiveData<Float> = mediaRepository.playbackSpeed
    val preparingError: LiveData<Boolean> = mediaRepository.mediaPreparingError

    private val _searchRequested = MutableLiveData(false)
    val searchRequested: LiveData<Boolean> = _searchRequested

    private val _searchToken = MutableLiveData(EMPTY_SEARCH)
    val searchToken: LiveData<String> = _searchToken

    private val _bookRefreshInProgress = MutableLiveData(false)
    val bookRefreshInProgress: LiveData<Boolean> = _bookRefreshInProgress

    private val _bookRefreshMessageRes = MutableLiveData<Int?>(null)
    val bookRefreshMessageRes: LiveData<Int?> = _bookRefreshMessageRes

    val bookSkipSaveMessageRes: LiveData<Int?> = mediaRepository.bookSkipSaveMessageRes

    val isPlaying: LiveData<Boolean> = mediaRepository.isPlaying

    val bookmarks = mediaRepository.bookmarks

    fun createBookmark() {
      viewModelScope.launch {
        mediaRepository.createBookmark()
      }
    }

    fun dropBookmark(bookmark: Bookmark) {
      viewModelScope.launch {
        mediaRepository.dropBookmark(bookmark = bookmark)
      }
    }

    fun playBookmark(bookmark: Bookmark) {
      mediaRepository.playBookmark(bookmark)
    }

    fun updateBookmarks() {
      viewModelScope.launch { mediaRepository.updateBookmarks() }
    }

    fun updatePlayingItem() {
      val playingItem = preferences.getPlayingItem()

      when (playingItem?.id) {
        null -> viewModelScope.launch { mediaRepository.clearPlayingBook() }
        else -> viewModelScope.launch { mediaRepository.preparePlayback(playingItem.id) }
      }
    }

    fun expandPlayingQueue() {
      _playingQueueExpanded.postValue(true)
    }

    fun setTimer(option: TimerOption?) {
      mediaRepository.updateTimer(option)
    }

    fun setPreferredSleepTimerStopMode(stopMode: DurationTimerStopMode) {
      preferences.savePreferredSleepTimerStopMode(stopMode)
      _preferredSleepTimerStopMode.postValue(stopMode)
    }

    fun collapsePlayingQueue() {
      _playingQueueExpanded.postValue(false)
    }

    fun togglePlayingQueue() {
      _playingQueueExpanded.postValue(!(_playingQueueExpanded.value ?: false))
    }

    fun requestSearch() {
      _searchRequested.postValue(true)
    }

    fun dismissSearch() {
      _searchRequested.postValue(false)
      _searchToken.postValue(EMPTY_SEARCH)
    }

    fun updateSearch(token: String) {
      _searchToken.postValue(token)
    }

    fun preparePlayback(bookId: String) {
      viewModelScope.launch {
        mediaRepository.clearPreparedItem()
        mediaRepository.preparePlayback(bookId, forceReload = false)
      }
    }

    fun refreshCurrentBook(bookId: String) {
      if (_bookRefreshInProgress.value == true) {
        return
      }

      viewModelScope.launch {
        _bookRefreshInProgress.postValue(true)

        when (mediaRepository.refreshCurrentBook(bookId)) {
          is OperationResult.Success<*> -> {
            Unit
          }

          is OperationResult.Error<*> -> {
            _bookRefreshMessageRes.postValue(R.string.settings_refresh_webdav_cache_failed)
          }
        }

        _bookRefreshInProgress.postValue(false)
      }
    }

    fun rewind() {
      mediaRepository.rewind()
    }

    fun forward() {
      mediaRepository.forward()
    }

    fun seekTo(chapterPosition: Double) {
      mediaRepository.setChapterPosition(chapterPosition)
    }

    fun setTotalPosition(totalPosition: Double) {
      mediaRepository.setTotalPosition(totalPosition)
    }

    fun setChapter(chapter: PlayingChapter) {
      if (chapter.available) {
        viewModelScope.launch {
          mediaRepository.setChapter(chapter)
        }
      }
    }

    fun clearPlayingBook() = mediaRepository.clearPlayingBook()

    fun consumeBookRefreshMessage() {
      _bookRefreshMessageRes.postValue(null)
    }

    fun saveBookSkipSettings(
      bookId: String,
      introSkipSeconds: Int,
      outroSkipSeconds: Int,
    ) {
      viewModelScope.launch {
        mediaRepository.saveBookSkipSettings(
          bookId = bookId,
          introSkipSeconds = introSkipSeconds,
          outroSkipSeconds = outroSkipSeconds,
        )
      }
    }

    fun consumeBookSkipSaveMessage() {
      mediaRepository.consumeBookSkipSaveMessage()
    }

    fun setPlaybackSpeed(factor: Float) = mediaRepository.setPlaybackSpeed(factor)

    fun nextTrack() = mediaRepository.nextTrack()

    fun previousTrack() = mediaRepository.previousTrack()

    fun togglePlayPause() = mediaRepository.togglePlayPause()

    fun prepareAndPlay() {
      val playingBook = preferences.getPlayingItem() ?: return
      mediaRepository.prepareAndPlay(playingBook)
    }

    companion object {
      private const val EMPTY_SEARCH = ""
    }
  }
