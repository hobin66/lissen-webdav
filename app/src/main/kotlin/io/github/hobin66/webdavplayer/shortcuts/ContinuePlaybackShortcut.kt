package io.github.hobin66.webdavplayer.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.common.RunningComponent
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.ui.activity.AppActivity
import io.github.hobin66.webdavplayer.ui.navigation.CONTINUE_PLAYBACK
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinuePlaybackShortcut
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val sharedPreferences: WebdavPlayerPreferences,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
      Timber.d("ContinuePlaybackShortcut registered")

      scope.launch {
        sharedPreferences
          .playingItemFlow
          .collect { updateShortcut(it) }
      }
    }

    private fun updateShortcut(playingBook: DetailedItem?) {
      Timber.d("ContinuePlaybackShortcut is updating")

      val shortcutManager = context.getSystemService(ShortcutManager::class.java)

      if (playingBook == null) {
        shortcutManager.removeDynamicShortcuts(listOf(SHORTCUT_TAG))
        return
      }

      val shortcut =
        ShortcutInfo
          .Builder(context, SHORTCUT_TAG)
          .setShortLabel(context.getString(R.string.continue_playback_shortcut_title))
          .setLongLabel(context.getString(R.string.continue_playback_shortcut_description))
          .setIcon(Icon.createWithResource(context, R.drawable.ic_play))
          .setIntent(
            Intent(context, AppActivity::class.java).apply {
              action = CONTINUE_PLAYBACK
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
          ).build()

      shortcutManager.dynamicShortcuts = listOf(shortcut)
      shortcutManager.reportShortcutUsed(shortcut.id)
    }

    companion object {
      private const val SHORTCUT_TAG = "continue_playback_shortcut"
    }
  }
