package io.github.hobin66.webdavplayer.playback.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebdavPlayerMediaSourceFactoryMediaIdTest {
  @Test
  fun `media id round trips custom book ids with separators`() {
    val mediaId = WebdavPlayerMediaSourceFactory.MediaId("books/fantasy:one", 7).toString()

    assertEquals("chapter:books%2Ffantasy%3Aone:7", mediaId)
    assertEquals(
      WebdavPlayerMediaSourceFactory.MediaId("books/fantasy:one", 7),
      WebdavPlayerMediaSourceFactory.MediaId.fromString(mediaId),
    )
  }
}
