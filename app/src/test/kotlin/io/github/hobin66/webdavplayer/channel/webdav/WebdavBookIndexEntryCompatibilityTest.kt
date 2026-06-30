package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavBookIndexStore
import io.github.hobin66.webdavplayer.common.moshi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebdavBookIndexEntryCompatibilityTest {
  private val adapter = moshi.adapter(WebdavBookIndexStore::class.java)

  @Test
  fun `old index entries without isAdded remain visible`() {
    val store = adapter.fromJson(indexJson(extraFields = ""))!!

    assertTrue(store.items.single().isAdded)
  }

  @Test
  fun `explicit removed index entries stay removed`() {
    val store = adapter.fromJson(indexJson(extraFields = """, "isAdded": false"""))!!

    assertFalse(store.items.single().isAdded)
  }

  private fun indexJson(extraFields: String): String =
    """
    {
      "items": [
        {
          "bookId": "book-1",
          "directoryPath": "Books/Book 1",
          "directoryEtag": "\"dir\"",
          "directoryLastModified": "Mon, 01 Jan 2024 00:00:00 GMT",
          "title": "Book 1",
          "coverName": "cover.jpg",
          "metadataEtag": null,
          "metadataLastModified": null,
          "coverEtag": null,
          "coverLastModified": null
          $extraFields
        }
      ]
    }
    """.trimIndent()
}
