package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.waitForNoOpenedDialogs
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.nio.file.Files

/**
 * Verifies that reused IDE recovery can start the next test after the previous test leaves a modal dialog open.
 *
 * The first test opens the Settings dialog and intentionally does not close it. The second test checks that the
 * same IDE process is still reused, the dialog was closed by recovery, and Driver calls continue to work.
 */
@TestMethodOrder(OrderAnnotation::class)
@ReusedRun
class ReusedModeModalDialogTest {
  companion object {
    @ReusedProject
    private val project: CachedProjectConfiguration =
      cachedProjectConfiguration(Files.createTempDirectory("reused-modal-dialog-project"))

    private lateinit var firstTestIdeId: String
  }

  @Test
  @Order(1)
  fun `first test leaves modal dialog open`(driver: Driver, bgRun: BackgroundRun) {
    assertTrue(bgRun.process.isAlive)
    firstTestIdeId = bgRun.process.id

    driver.ideFrame {
      openSettingsDialog()
      settingsDialog().waitFound()
    }
  }

  @Test
  @Order(2)
  fun `second test starts after modal dialog recovery`(driver: Driver, bgRun: BackgroundRun) {
    assertTrue(bgRun.process.isAlive)
    assertEquals(firstTestIdeId, bgRun.process.id)

    driver.ideFrame()
    driver.ui.waitForNoOpenedDialogs()
  }
}