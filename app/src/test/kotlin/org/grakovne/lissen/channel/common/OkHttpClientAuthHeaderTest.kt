package org.grakovne.lissen.channel.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class OkHttpClientAuthHeaderTest {
  @Test
  fun `prefers access token when present`() {
    val header =
      resolveAuthorizationHeader(
        accessToken = "access-token",
        token = "password",
        username = "alice",
        webdavRoot = "/dav",
      )

    assertEquals("Bearer access-token", header)
  }

  @Test
  fun `uses basic auth for webdav credentials when username token and root are present`() {
    val header =
      resolveAuthorizationHeader(
        accessToken = null,
        token = "secret",
        username = "alice",
        webdavRoot = "/dav",
      )

    val encoded = Base64.getEncoder().encodeToString("alice:secret".toByteArray(Charsets.UTF_8))
    assertEquals("Basic $encoded", header)
  }

  @Test
  fun `falls back to bearer token when webdav root is absent`() {
    val header =
      resolveAuthorizationHeader(
        accessToken = null,
        token = "secret",
        username = "alice",
        webdavRoot = null,
      )

    assertEquals("Bearer secret", header)
  }

  @Test
  fun `returns null when no credentials are available`() {
    val header =
      resolveAuthorizationHeader(
        accessToken = null,
        token = null,
        username = null,
        webdavRoot = null,
      )

    assertNull(header)
  }
}
