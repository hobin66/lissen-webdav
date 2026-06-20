package io.github.hobin66.webdavplayer.ui.components.slider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkipSliderLabelsTest {
  @Test
  fun `skip slider supports zero through sixty seconds`() {
    assertEquals(0..60, SkipSliderRange)
  }

  @Test
  fun `skip slider labels major ticks every five seconds including off`() {
    assertEquals(
      listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60),
      SkipSliderLabeledIndexes,
    )
  }

  @Test
  fun `skip slider formats zero as off label`() {
    assertEquals("Off", formatSkipSecondsLabel(seconds = 0, offLabel = "Off"))
  }

  @Test
  fun `skip slider formats non-zero values as seconds`() {
    assertEquals("15 s", formatSkipSecondsLabel(seconds = 15, offLabel = "Off"))
  }

  @Test
  fun `skip slider clamps labels to supported range`() {
    assertEquals("Off", formatSkipSecondsLabel(seconds = -1, offLabel = "Off"))
    assertEquals("60 s", formatSkipSecondsLabel(seconds = 75, offLabel = "Off"))
  }
}
