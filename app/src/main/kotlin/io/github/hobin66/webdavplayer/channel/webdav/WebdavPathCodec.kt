package io.github.hobin66.webdavplayer.channel.webdav

import java.util.Base64

object WebdavPathCodec {
  fun encode(path: String): String =
    Base64
      .getUrlEncoder()
      .withoutPadding()
      .encodeToString(path.toByteArray(Charsets.UTF_8))

  fun decode(value: String): String? =
    runCatching {
      Base64
        .getUrlDecoder()
        .decode(value)
        .toString(Charsets.UTF_8)
    }.getOrNull()
}
