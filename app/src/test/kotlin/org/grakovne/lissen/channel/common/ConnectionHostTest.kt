package org.grakovne.lissen.channel.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectionHostTest {
  @Test
  fun `creates external host`() {
    val host = ConnectionHost.external("https://example.com")

    assertEquals("https://example.com", host.url)
    assertEquals(ConnectionType.EXTERNAL, host.type)
  }

  @Test
  fun `creates internal host`() {
    val host = ConnectionHost.internal("http://192.168.1.10:8080")

    assertEquals("http://192.168.1.10:8080", host.url)
    assertEquals(ConnectionType.INTERNAL, host.type)
  }
}
