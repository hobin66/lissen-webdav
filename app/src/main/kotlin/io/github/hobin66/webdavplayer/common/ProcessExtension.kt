package io.github.hobin66.webdavplayer.common

import android.content.Context
import com.jakewharton.processphoenix.ProcessPhoenix
import timber.log.Timber

fun Context.restartApplication() {
  try {
    ProcessPhoenix.triggerRebirth(this)
  } catch (ex: Exception) {
    Timber.e(ex)
  }
}
