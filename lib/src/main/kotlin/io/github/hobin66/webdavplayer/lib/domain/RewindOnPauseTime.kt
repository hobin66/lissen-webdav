package io.github.hobin66.webdavplayer.lib.domain

import androidx.annotation.Keep

@Keep
data class RewindOnPauseTime(
  val enabled: Boolean,
  val time: SeekTimeOption,
) {
  companion object {
    val Default =
      RewindOnPauseTime(
        enabled = false,
        time = SeekTimeOption.SEEK_5,
      )
  }
}
