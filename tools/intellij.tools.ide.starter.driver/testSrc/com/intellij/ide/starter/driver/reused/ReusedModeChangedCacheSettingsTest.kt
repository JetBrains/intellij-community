package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.saveSettings
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.nio.file.Files

/**
 * Verifies reused IDE recovery for an application component with the `$CACHE_FILE$` storage.
 *
 * The IDE keeps such a state in `app-internal-state.db`, which no file baseline shows. The first test hides a type in
 * the Search Everywhere filter and saves it. Live recovery must see the change, roll it back, and reload the component.
 */
@TestMethodOrder(OrderAnnotation::class)
@ReusedRun
class ReusedModeChangedCacheSettingsTest {
  companion object {
    @delegate:ReusedProject
    val project: CachedProjectConfiguration by lazy {
      ChangedCacheSettingsRunRegistry.ideProcessId = null
      cachedProjectConfiguration(Files.createTempDirectory("reused-cache-settings-project"))
    }
  }

  @Test
  @Order(1)
  fun `first test hides a type in the search everywhere filter`(driver: Driver, bgRun: BackgroundRun) {
    val filter = driver.service<SearchEverywhereConfigurationRemote>()
    assertTrue(filter.isVisible(FILTERED_TYPE))
    ChangedCacheSettingsRunRegistry.ideProcessId = bgRun.process.id

    filter.setVisible(FILTERED_TYPE, false)
    driver.saveSettings(forceSavingAllSettings = true)

    assertFalse(filter.isVisible(FILTERED_TYPE))
  }

  @Test
  @Order(2)
  fun `second test sees the default filter in the same IDE`(driver: Driver, bgRun: BackgroundRun) {
    assertEquals(ChangedCacheSettingsRunRegistry.ideProcessId, bgRun.process.id, "Recovery restarted the IDE")
    assertTrue(driver.service<SearchEverywhereConfigurationRemote>().isVisible(FILTERED_TYPE))
  }
}

@Remote("com.intellij.ide.util.gotoByName.SearchEverywhereConfiguration")
internal interface SearchEverywhereConfigurationRemote {
  fun isVisible(type: Any): Boolean
  fun setVisible(type: Any, value: Boolean)
}

private object ChangedCacheSettingsRunRegistry {
  var ideProcessId: String? = null
}

private const val FILTERED_TYPE = "Classes"
