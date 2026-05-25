package org.grakovne.lissen.ui.screens.library

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryLoadingPolicyTest {
  @Test
  fun `shows placeholder during initial load before any content is shown`() {
    assertTrue(
      shouldShowLibraryPlaceholder(
        searchRequested = false,
        pullRefreshing = false,
        recentBookRefreshing = true,
        libraryRefreshing = true,
        hasDisplayedContent = false,
      ),
    )
  }

  @Test
  fun `keeps content visible during refresh after content has been displayed`() {
    assertFalse(
      shouldShowLibraryPlaceholder(
        searchRequested = false,
        pullRefreshing = false,
        recentBookRefreshing = true,
        libraryRefreshing = true,
        hasDisplayedContent = true,
      ),
    )
  }

  @Test
  fun `never shows placeholder while search is active`() {
    assertFalse(
      shouldShowLibraryPlaceholder(
        searchRequested = true,
        pullRefreshing = false,
        recentBookRefreshing = true,
        libraryRefreshing = true,
        hasDisplayedContent = false,
      ),
    )
  }
}
