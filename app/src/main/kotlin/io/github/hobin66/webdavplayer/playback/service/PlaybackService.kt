package io.github.hobin66.webdavplayer.playback.service

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import dagger.hilt.android.AndroidEntryPoint
import io.github.hobin66.webdavplayer.content.ExternalCoverProvider
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.lib.domain.BookFile
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import io.github.hobin66.webdavplayer.lib.domain.TimerOption
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.MediaLibrarySessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
  @Inject
  lateinit var exoPlayer: ExoPlayer

  @Inject
  lateinit var mediaLibrarySessionProvider: MediaLibrarySessionProvider

  @Inject
  lateinit var mediaProvider: WebdavMediaProvider

  @Inject
  lateinit var playbackSynchronizationService: PlaybackSynchronizationService

  @Inject
  lateinit var sharedPreferences: WebdavPlayerPreferences

  @Inject
  lateinit var playbackTimer: PlaybackTimer

  private var session: MediaLibrarySession? = null

  private val playerServiceScope = MainScope()

  override fun onCreate() {
    super.onCreate()

    session = getSession()
  }

  @Suppress("DEPRECATION")
  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    super.onStartCommand(intent, flags, startId)

    when (intent?.action) {
      ACTION_SET_TIMER -> {
        val delay = intent.getDoubleExtra(TIMER_VALUE_EXTRA, 0.0)
        val option = intent.getSerializableExtra(TIMER_OPTION_EXTRA) as? TimerOption

        if (delay > 0 && option != null) {
          setTimer(delay, option)
        }

        return START_NOT_STICKY
      }

      ACTION_CANCEL_TIMER -> {
        cancelTimer()
        return START_NOT_STICKY
      }

      ACTION_PLAY -> {
        playerServiceScope
          .launch {
            exoPlayer.prepare()
            exoPlayer.setPlaybackSpeed(sharedPreferences.getPlaybackSpeed())
            exoPlayer.playWhenReady = true
          }
        return START_STICKY
      }

      ACTION_PAUSE -> {
        pause()
        return START_NOT_STICKY
      }

      ACTION_STOP_SESSION -> {
        cancelTimer()
        sharedPreferences.clearPlayingItem()
        exoPlayer.clearMediaItems()
        pause()
        return START_NOT_STICKY
      }

      ACTION_SET_PLAYBACK -> {
        val book = sharedPreferences.getPlayingItem()

        book?.let {
          playerServiceScope
            .launch { preparePlayback(it) }
        }
        return START_NOT_STICKY
      }

      ACTION_SEEK_TO -> {
        val book = sharedPreferences.getPlayingItem()

        val position = intent.getDoubleExtra(POSITION, 0.0)
        book?.let { seek(it.chapters, position) }
        return START_NOT_STICKY
      }

      else -> {
        return START_NOT_STICKY
      }
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = getSession()

  private fun getSession(): MediaLibrarySession =
    when (val currentSession = session) {
      null -> mediaLibrarySessionProvider.provideMediaLibrarySession(this).also { session = it }
      else -> currentSession
    }

  override fun onDestroy() {
    playbackSynchronizationService.cancelSynchronization()
    playerServiceScope.cancel()

    exoPlayer.clearMediaItems()
    exoPlayer.release()

    session?.release()
    session = null

    super.onDestroy()
  }

  @OptIn(UnstableApi::class)
  private suspend fun preparePlayback(book: DetailedItem) {
    exoPlayer.playWhenReady = false

    withContext(Dispatchers.IO) {
      val prepareQueue =
        async {
          if (book.chapters.isEmpty()) {
            Timber.w("Can't build playing queue: book has no chapters (bookId=${book.id})")

            return@async
          }

          val itemsWithPosition =
            bookToChapterMediaItems(
              book = book,
              snapshot = sharedPreferences.getPlaybackSnapshot(book.id),
            )

          withContext(Dispatchers.Main) {
            exoPlayer.setMediaItems(itemsWithPosition.mediaItems)
            exoPlayer.prepare()
            exoPlayer.seekTo(itemsWithPosition.startIndex, itemsWithPosition.startPositionMs)
          }
        }

      val prepareSession =
        async {
          playbackSynchronizationService.startPlaybackSynchronization(book)
        }

      awaitAll(prepareSession, prepareQueue)

      PlaybackEvents.emit(PlaybackEvent.PlaybackReady)
    }
  }

  private fun setTimer(
    delay: Double,
    option: TimerOption,
  ) {
    playbackTimer.startTimer(delay, option)
    Timber.d("Timer started for ${delay * 1000} ms.")
  }

  private fun cancelTimer() {
    playbackTimer.stopTimer()
    Timber.d("Timer canceled.")
  }

  private fun pause() {
    playerServiceScope
      .launch {
        exoPlayer.playWhenReady = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
  }

  private fun seek(
    items: List<PlayingChapter>,
    position: Double?,
  ) {
    if (items.isEmpty()) {
      Timber.w("Tried to seek position $position in the empty book. Skipping")
      return
    }

    when (position) {
      null -> {
        exoPlayer.seekTo(0, 0)
      }

      else -> {
        val positionMs = (position * 1000).toLong()

        val durationsMs = items.map { (it.duration * 1000).toLong() }
        val cumulativeDurationsMs = durationsMs.runningFold(0L) { acc, duration -> acc + duration }

        val targetChapterIndex = cumulativeDurationsMs.indexOfFirst { it > positionMs }

        when (targetChapterIndex - 1 >= 0) {
          true -> {
            val chapterStartTimeMs = cumulativeDurationsMs[targetChapterIndex - 1]
            val chapterProgressMs = positionMs - chapterStartTimeMs
            exoPlayer.seekTo(targetChapterIndex - 1, chapterProgressMs)
          }

          false -> {
            val lastChapterIndex = items.size - 1
            val lastChapterDurationMs = durationsMs.last()
            exoPlayer.seekTo(lastChapterIndex, lastChapterDurationMs)
          }
        }
      }
    }
  }

  companion object {
    const val ACTION_PLAY = "io.github.hobin66.webdavplayer.player.service.PLAY"
    const val ACTION_PAUSE = "io.github.hobin66.webdavplayer.player.service.PAUSE"
    const val ACTION_STOP_SESSION = "io.github.hobin66.webdavplayer.player.service.STOP_SESSION"
    const val ACTION_SET_PLAYBACK = "io.github.hobin66.webdavplayer.player.service.SET_PLAYBACK"
    const val ACTION_SEEK_TO = "io.github.hobin66.webdavplayer.player.service.ACTION_SEEK_TO"
    const val ACTION_SET_TIMER = "io.github.hobin66.webdavplayer.player.service.ACTION_SET_TIMER"
    const val ACTION_CANCEL_TIMER = "io.github.hobin66.webdavplayer.player.service.CANCEL_TIMER"

    const val TIMER_VALUE_EXTRA = "io.github.hobin66.webdavplayer.player.service.TIMER_VALUE"
    const val TIMER_OPTION_EXTRA = "io.github.hobin66.webdavplayer.player.service.TIMER_OPTION"
    const val POSITION = "io.github.hobin66.webdavplayer.player.service.POSITION"

    const val FILE_SEGMENTS = "io.github.hobin66.webdavplayer.player.service.FILE_SEGMENTS"
    const val CHAPTER_START_MS = "io.github.hobin66.webdavplayer.player.service.CHAPTER_START_MS"

    internal fun resolveChapterToFiles(
      chapters: List<PlayingChapter>,
      files: List<BookFile>,
    ): List<ArrayList<FileClip>> = resolveChapterToFiles(chapters, files) { index, chapter, resolvedFiles -> resolvedFiles }

    internal fun <T> resolveChapterToFiles(
      chapters: List<PlayingChapter>,
      files: List<BookFile>,
      resolvedFilesConsumer: (Int, PlayingChapter, ArrayList<FileClip>) -> T,
    ): List<T> {
      if (files.isEmpty() || chapters.isEmpty()) return emptyList()

      val result = ArrayList<T>(chapters.size)

      val filesIterator = files.iterator()
      var currentFile = filesIterator.next()

      var allocatedFilesEnd = 0.0
      val epsilon = 0.01

      chapters.forEachIndexed { index, chapter ->
        val chapterClips = ArrayList<FileClip>(1)
        var outstandingPartStart = chapter.start

        while (outstandingPartStart < chapter.end - epsilon) {
          val currentFileEnd = allocatedFilesEnd + currentFile.duration
          val overlapEnd = minOf(chapter.end, currentFileEnd)

          if (epsilon < overlapEnd - outstandingPartStart) {
            chapterClips.add(
              FileClip(
                fileId = currentFile.id,
                clipStart = outstandingPartStart - allocatedFilesEnd,
                clipEnd = overlapEnd - allocatedFilesEnd,
              ),
            )
          }

          if (currentFileEnd < chapter.end && filesIterator.hasNext()) {
            allocatedFilesEnd += currentFile.duration
            currentFile = filesIterator.next()
          } else {
            break
          }

          outstandingPartStart = overlapEnd
        }
        result.add(resolvedFilesConsumer(index, chapter, chapterClips))
      }

      return result
    }

    @UnstableApi
    fun bookToChapterMediaItems(
      book: DetailedItem,
      snapshot: PlaybackSnapshotRecord? = null,
    ): MediaItemsWithStartPosition {
      val (chapterIndex, chapterOffset) = resolvePlaybackStartPosition(book = book, snapshot = snapshot)

      val chapterMediaItems =
        when (book.isDirectFileQueue()) {
          true -> {
            book.chapters.mapIndexed { index, chapter ->
              MediaItem
                .Builder()
                .setMediaId(WebdavPlayerMediaSourceFactory.MediaId(book.id, index).toString())
                .setUri(apply(book.id, chapter.id))
                .setMediaMetadata(
                  MediaMetadata
                    .Builder()
                    .setAlbumTitle(book.title)
                    .setTitle(chapter.title)
                    .setArtist(book.title)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkUri(ExternalCoverProvider.coverUri(book.id))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                    .setExtras(chapterStartExtras(chapter.start))
                    .build(),
                ).setTag(book)
                .build()
            }
          }

          false -> {
            resolveChapterToFiles(chapters = book.chapters, files = book.files) { index, chapter, resolvedFiles ->
              MediaItem
                .Builder()
                .setMediaId(WebdavPlayerMediaSourceFactory.MediaId(book.id, index).toString())
                .setRequestMetadata(
                  MediaItem.RequestMetadata
                    .Builder()
                    .setExtras(fileSegmentsExtras(resolvedFiles))
                    .build(),
                ).setMediaMetadata(
                  MediaMetadata
                    .Builder()
                    .setAlbumTitle(book.title)
                    .setTitle(chapter.title)
                    .setArtist(book.title)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkUri(ExternalCoverProvider.coverUri(book.id))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                    .setExtras(chapterStartExtras(chapter.start))
                    .build(),
                ).setTag(book)
                .build()
            }
          }
        }
      return MediaItemsWithStartPosition(chapterMediaItems, chapterIndex, (chapterOffset * 1000).toLong())
    }

    private fun chapterStartExtras(chapterStartSeconds: Double): Bundle =
      Bundle().apply {
        putLong(CHAPTER_START_MS, (chapterStartSeconds * 1000).toLong())
      }

    private fun fileSegmentsExtras(resolvedFiles: List<FileClip>): Bundle =
      Bundle().apply {
        putParcelableArrayList(FILE_SEGMENTS, ArrayList(resolvedFiles))
      }
  }
}
