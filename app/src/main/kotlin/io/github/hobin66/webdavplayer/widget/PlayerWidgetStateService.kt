package io.github.hobin66.webdavplayer.widget

import android.content.Context
import androidx.annotation.OptIn
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.asFlow
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.common.RunningComponent
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.playback.MediaRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlayerWidgetStateService
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaProvider: WebdavMediaProvider,
  ) : RunningComponent {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
      scope.launch {
        combine(
          mediaRepository.playingBook.asFlow().distinctUntilChanged(),
          mediaRepository.isPlaying
            .asFlow()
            .filterNotNull()
            .distinctUntilChanged(),
          mediaRepository.currentChapterIndex.asFlow().distinctUntilChanged(),
        ) { playingItem: DetailedItem?, isPlaying, chapterIndex: Int? ->
          val chapterTitle = provideChapterTitle(playingItem, chapterIndex)

          val maybeCover =
            playingItem
              ?.id
              ?.let { mediaProvider.fetchBookCover(it) }
              ?.fold(
                onSuccess = { it },
                onFailure = { null },
              )

          PlayingItemState(
            id = playingItem?.id ?: "",
            title = playingItem?.title ?: "",
            chapterTitle = chapterTitle,
            isPlaying = isPlaying,
            coverFile = maybeCover,
          )
        }.collect { playingItemState ->
          updatePlayingItem(playingItemState)
        }
      }
    }

    private fun provideChapterTitle(
      item: DetailedItem?,
      chapterIndex: Int?,
    ): String? {
      if (null == item || null == chapterIndex) {
        return null
      }

      return when (chapterIndex in item.chapters.indices) {
        true -> item.chapters[chapterIndex].title
        false -> item.title
      }
    }

    private suspend fun updatePlayingItem(state: PlayingItemState) {
      val manager = GlanceAppWidgetManager(context)
      val glanceIds = manager.getGlanceIds(PlayerWidget::class.java)

      glanceIds
        .forEach { glanceId ->
          updateAppWidgetState(context, glanceId) { prefs ->
            prefs[PlayerWidget.bookId] = state.id
            prefs[PlayerWidget.coverPath] = state.coverFile?.absolutePath ?: ""
            prefs[PlayerWidget.title] = state.title
            prefs[PlayerWidget.chapterTitle] = state.chapterTitle ?: ""
            prefs[PlayerWidget.isPlaying] = state.isPlaying
          }
          PlayerWidget().update(context, glanceId)
        }
    }
  }

data class PlayingItemState(
  val id: String,
  val title: String,
  val chapterTitle: String?,
  val isPlaying: Boolean = false,
  val coverFile: File?,
)
