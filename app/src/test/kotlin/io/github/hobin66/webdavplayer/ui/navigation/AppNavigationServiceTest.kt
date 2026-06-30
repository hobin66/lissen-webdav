package io.github.hobin66.webdavplayer.ui.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppNavigationServiceTest {
  @Test
  fun `player route encodes path and query arguments`() {
    val route =
      buildPlayerRoute(
        bookId = "books/fantasy:one",
        bookTitle = "A&B / C?",
        bookSubtitle = "part=1&part=2",
        startInstantly = true,
      )

    assertEquals(
      "player_screen/books%2Ffantasy%3Aone?bookTitle=A%26B%20%2F%20C%3F&bookSubtitle=part%3D1%26part%3D2&startInstantly=true",
      route,
    )
  }
}
