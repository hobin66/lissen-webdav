package io.github.hobin66.webdavplayer.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildMetadataTest {
  @Test
  fun `git hash uses stdout when command succeeds with a short hash`() {
    val result =
      GitCommandResult(
        exitCode = 0,
        stdout = "1a2b3c4\n",
        stderr = "",
      )

    assertEquals("1a2b3c4", resolveGitHash(result))
  }

  @Test
  fun `git hash falls back when command exits with failure`() {
    val result =
      GitCommandResult(
        exitCode = 128,
        stdout = "",
        stderr = "fatal: not a git repository",
      )

    assertEquals("stable", resolveGitHash(result))
  }

  @Test
  fun `git hash falls back when stdout is not a valid hash`() {
    val result =
      GitCommandResult(
        exitCode = 0,
        stdout = "fatal: not a git repository\nStopping at filesystem boundary\n",
        stderr = "",
      )

    assertEquals("stable", resolveGitHash(result))
  }

  @Test
  fun `missing required properties returns only blank and absent values`() {
    val missing =
      missingRequiredProperties(
        propertyNames = listOf("A", "B", "C"),
      ) { name ->
        when (name) {
          "A" -> "set"
          "B" -> ""
          else -> null
        }
      }

    assertEquals(listOf("B", "C"), missing)
  }

  @Test
  fun `release signing is required for release artifact tasks`() {
    assertTrue(requiresReleaseSigning(listOf(":app:assembleRelease")))
    assertTrue(requiresReleaseSigning(listOf("bundleRelease")))
  }

  @Test
  fun `release signing is not required for non artifact tasks`() {
    assertFalse(requiresReleaseSigning(emptyList()))
    assertFalse(requiresReleaseSigning(listOf("testDebugUnitTest")))
    assertFalse(requiresReleaseSigning(listOf("lintVitalRelease")))
  }

  @Test
  fun `release signing is required for validate signing release`() {
    assertTrue(requiresReleaseSigning(listOf("validateSigningRelease")))
  }
}
