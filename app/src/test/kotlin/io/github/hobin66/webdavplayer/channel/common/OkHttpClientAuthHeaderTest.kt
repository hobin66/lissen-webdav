package io.github.hobin66.webdavplayer.channel.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class OkHttpClientAuthHeaderTest {
  @Test
  fun `uses basic auth for webdav credentials when username password and root are present`() {
    val header =
      resolveAuthorizationHeader(
        username = "alice",
        password = "secret",
        webdavRoot = "/dav",
      )

    val encoded = Base64.getEncoder().encodeToString("alice:secret".toByteArray(Charsets.UTF_8))
    assertEquals("Basic $encoded", header)
  }

  @Test
  fun `returns null when webdav root is absent`() {
    val header =
      resolveAuthorizationHeader(
        username = "alice",
        password = "secret",
        webdavRoot = null,
      )

    assertNull(header)
  }

  @Test
  fun `returns null when no credentials are available`() {
    val header =
      resolveAuthorizationHeader(
        username = null,
        password = null,
        webdavRoot = null,
      )

    assertNull(header)
  }
}
