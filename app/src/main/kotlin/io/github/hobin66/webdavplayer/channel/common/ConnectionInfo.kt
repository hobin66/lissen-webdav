package io.github.hobin66.webdavplayer.channel.common

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class ConnectionInfo(
  val username: String,
  val serverVersion: String?,
  val buildNumber: String?,
)
