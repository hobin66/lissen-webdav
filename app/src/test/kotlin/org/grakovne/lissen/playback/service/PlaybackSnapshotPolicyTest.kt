package org.grakovne.lissen.playback.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackSnapshotPolicyTest {
  @Test
  fun `persists immediately when no previous snapshot exists`() {
    assertTrue(shouldPersistPlaybackSnapshot(lastPersistAtMs = null, nowMs = 5_000L, intervalMs = 5_000L))
  }

  @Test
  fun `persists when snapshot interval has elapsed`() {
    assertTrue(shouldPersistPlaybackSnapshot(lastPersistAtMs = 1_000L, nowMs = 6_100L, intervalMs = 5_000L))
  }

  @Test
  fun `skips when interval has not elapsed yet`() {
    assertFalse(shouldPersistPlaybackSnapshot(lastPersistAtMs = 1_000L, nowMs = 5_500L, intervalMs = 5_000L))
  }
}
