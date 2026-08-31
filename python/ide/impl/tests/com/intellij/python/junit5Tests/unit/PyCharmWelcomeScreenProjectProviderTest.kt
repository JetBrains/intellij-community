// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.intellij.pycharm.community.ide.impl.welcomeScreen.PyCharmWelcomeScreenProjectProvider
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The two gates must agree. If the provider claims a file while the non-modal Welcome screen is off,
 * the file opening path finds no `NoProjectStateHandler` and the IDE fails with an internal error (PY-91932).
 */
@TestApplication
@SystemProperty(propertyKey = "idea.force.disable.non.modal.welcome.screen", propertyValue = "false")
internal class PyCharmWelcomeScreenProjectProviderTest {
  private val provider = PyCharmWelcomeScreenProjectProvider()

  @Test
  @RegistryKey(key = "welcome.screen.open.files", value = "true")
  fun `notebook is claimed when both settings are enabled`(@TempDir tempDir: Path) = withNonModalWelcomeScreen(true) {
    assertTrue(provider.canOpenFilesFromSystemFileManager(tempDir.resolve("notebook.ipynb")))
  }

  @Test
  @RegistryKey(key = "welcome.screen.open.files", value = "true")
  fun `notebook is not claimed when the non-modal welcome screen is disabled`(@TempDir tempDir: Path) = withNonModalWelcomeScreen(false) {
    assertFalse(provider.canOpenFilesFromSystemFileManager(tempDir.resolve("notebook.ipynb")))
  }

  @Test
  @RegistryKey(key = "welcome.screen.open.files", value = "false")
  fun `notebook is not claimed when the registry key is disabled`(@TempDir tempDir: Path) = withNonModalWelcomeScreen(true) {
    assertFalse(provider.canOpenFilesFromSystemFileManager(tempDir.resolve("notebook.ipynb")))
  }

  @Test
  @RegistryKey(key = "welcome.screen.open.files", value = "true")
  fun `a file that is not a notebook is not claimed`(@TempDir tempDir: Path) = withNonModalWelcomeScreen(true) {
    assertFalse(provider.canOpenFilesFromSystemFileManager(tempDir.resolve("script.py")))
    assertFalse(provider.canOpenFilesFromSystemFileManager(tempDir))
  }

  private fun withNonModalWelcomeScreen(value: Boolean, action: () -> Unit) {
    val previousValue = AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID)
    try {
      AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, value)
      action()
    }
    finally {
      AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, previousValue)
    }
  }
}
