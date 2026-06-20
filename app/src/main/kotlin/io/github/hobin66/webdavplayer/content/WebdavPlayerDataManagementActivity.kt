package io.github.hobin66.webdavplayer.content

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.hobin66.webdavplayer.ui.activity.AppActivity
import io.github.hobin66.webdavplayer.ui.navigation.SHOW_DOWNLOADS

@AndroidEntryPoint
class WebdavPlayerDataManagementActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val intent =
      Intent(this, AppActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TASK
        action = SHOW_DOWNLOADS
      }

    startActivity(intent)
    finish()
  }
}
