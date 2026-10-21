package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.saveSettings
import com.intellij.driver.sdk.ui.settings.getCurrentUiSettings
import com.intellij.driver.sdk.ui.settings.updateUiSettings
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Verifies reused IDE recovery for application-level settings changed through the Driver API.
 *
 * The first test changes a real persisted [com.intellij.ide.ui.UISettings] value, saves it, and checks that
 * the corresponding options file is written. The second test runs in the same IDE process and checks that
 * recovery removed the settings file and reset the runtime value back to the default.
 */
@TestMethodOrder(OrderAnnotation::class)
@ReusedRun
class ReusedModeChangedSettingsTest {
  companion object {
    @delegate:ReusedProject
    val project: CachedProjectConfiguration by lazy {
      ChangedSettingsRunRegistry.reset()
      cachedProjectConfiguration(Files.createTempDirectory("reused-settings-project"))
    }
  }

  @BeforeEach
  fun rememberActiveSettingsFile(driver: Driver) {
    val configDir = Path.of(driver.utility<PathManagerRemote>().getConfigPath())
    ChangedSettingsRunRegistry.uiSettingsFile = configDir.resolve("options/ui.lnf.xml")
  }

  @Test
  @Order(1)
  fun `first test changes setting through api`(driver: Driver, bgRun: BackgroundRun) {
    val settingsFile = checkNotNull(ChangedSettingsRunRegistry.uiSettingsFile)
    assertFalse(settingsFile.exists())
    assertEquals(DEFAULT_RECENT_FILES_LIMIT, driver.getCurrentUiSettings().recentFilesLimit)
    ChangedSettingsRunRegistry.ideProcessId = bgRun.process.id
    assertTrue(bgRun.process.isAlive)

    driver.updateUiSettings {
      recentFilesLimit = CHANGED_RECENT_FILES_LIMIT
    }
    driver.saveSettings()

    assertEquals(CHANGED_RECENT_FILES_LIMIT, driver.getCurrentUiSettings().recentFilesLimit)
    assertTrue(settingsFile.exists())
    val settingsText = settingsFile.readText()
    assertTrue(settingsText.contains("RECENT_FILES_LIMIT"))
    assertTrue(settingsText.contains("""value="$CHANGED_RECENT_FILES_LIMIT""""))
  }

  @Test
  @Order(2)
  fun `second test resets setting and removes settings file`(driver: Driver, bgRun: BackgroundRun) {
    val settingsFile = checkNotNull(ChangedSettingsRunRegistry.uiSettingsFile)
    assertEquals(ChangedSettingsRunRegistry.ideProcessId, bgRun.process.id)
    assertTrue(bgRun.process.isAlive)
    assertFalse(settingsFile.exists())
    assertEquals(DEFAULT_RECENT_FILES_LIMIT, driver.getCurrentUiSettings().recentFilesLimit)
  }
}

private object ChangedSettingsRunRegistry {
  var ideProcessId: String? = null
  var uiSettingsFile: Path? = null

  fun reset() {
    ideProcessId = null
    uiSettingsFile = null
  }
}

private const val DEFAULT_RECENT_FILES_LIMIT = 50
private const val CHANGED_RECENT_FILES_LIMIT = 7
