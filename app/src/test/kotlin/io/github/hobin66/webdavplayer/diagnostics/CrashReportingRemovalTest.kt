package io.github.hobin66.webdavplayer.diagnostics

import io.github.hobin66.webdavplayer.WebdavPlayerApplication
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.viewmodel.SettingsViewModel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CrashReportingRemovalTest {
  @Test
  fun `acra runtime is no longer available`() {
    assertThrows(ClassNotFoundException::class.java) {
      Class.forName("org.acra.ACRA")
    }
  }

  @Test
  fun `shared preferences no longer expose acra helpers`() {
    val methodNames =
      WebdavPlayerPreferences::class.java.declaredMethods
        .map { it.name }
        .toSet()

    assertFalse("getAcraEnabled" in methodNames)
    assertFalse("saveAcraEnabled" in methodNames)
  }

  @Test
  fun `settings view model no longer exposes crash reporting state`() {
    val methodNames =
      SettingsViewModel::class.java.declaredMethods
        .map { it.name }
        .toSet()

    assertFalse("preferCrashReporting" in methodNames)
  }

  @Test
  fun `application no longer contains crash reporting initializer`() {
    val methodNames =
      WebdavPlayerApplication::class.java.declaredMethods
        .map { it.name }
        .toSet()

    assertFalse("initCrashReporting" in methodNames)
  }
}
