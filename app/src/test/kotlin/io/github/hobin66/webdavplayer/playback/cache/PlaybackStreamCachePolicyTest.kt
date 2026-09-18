package io.github.hobin66.webdavplayer.playback.cache

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackStreamCachePolicyTest {
  @Test
  fun `account namespace isolates username host and root without exposing them`() {
    val first = playbackStreamAccountNamespace("alice", "https://dav.example", "/books")

    assertNotEquals(first, playbackStreamAccountNamespace("bob", "https://dav.example", "/books"))
    assertNotEquals(first, playbackStreamAccountNamespace("alice", "https://other.example", "/books"))
    assertNotEquals(first, playbackStreamAccountNamespace("alice", "https://dav.example", "/other"))
    assertFalse(first.contains("alice"))
    assertFalse(first.contains("dav.example"))
  }

  @Test
  fun `cache generation invalidates the same resource identity`() {
    val first = buildPlaybackStreamCacheKey("account", 1L, "book\u0000file")
    val second = buildPlaybackStreamCacheKey("account", 2L, "book\u0000file")

    assertNotEquals(first, second)
    assertTrue(first.startsWith("playback-stream-v1:account:1:"))
  }
}
