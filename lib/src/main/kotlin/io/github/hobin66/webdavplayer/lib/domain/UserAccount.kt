package io.github.hobin66.webdavplayer.lib.domain

import androidx.annotation.Keep

@Keep
data class UserAccount(
  val username: String,
  val password: String,
)
