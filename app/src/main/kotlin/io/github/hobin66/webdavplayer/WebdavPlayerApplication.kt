package io.github.hobin66.webdavplayer

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import io.github.hobin66.webdavplayer.common.RunningComponent
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class WebdavPlayerApplication : Application() {
  @Inject
  lateinit var runningComponents: Set<@JvmSuppressWildcards RunningComponent>

  override fun onCreate() {
    super.onCreate()
    appContext = applicationContext

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }

    runningComponents.forEach {
      try {
        it.onCreate()
      } catch (ex: Exception) {
        Timber.e("Unable to register Running component due to: ${ex.message}")
      }
    }
  }

  companion object {
    lateinit var appContext: Context
      private set
  }
}
