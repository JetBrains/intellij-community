package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.sdk.DumbService
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getIdeFrame
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.isInModalContext
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.withoutPauseOnIndicators
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTimedValue

/**
 * Verifies that the pause on the indicators does not hang a Driver call in a modal dialog that holds dumb mode.
 *
 * The internal `DumbMode` action keeps a non-modal dumb task running until the action runs again.
 * The test stops the task while the Settings dialog is open.
 * The exit from dumb mode then waits for the dialog to close, and the status bar can keep the indicator of the task.
 */
@ReusedRun
class PauseOnIndicatorsInModalDialogTest {
  companion object {
    @ReusedProject
    private val project: CachedProjectConfiguration =
      cachedProjectConfiguration(Files.createTempDirectory("pause-on-indicators-modal-dialog-project")) {
        internalMode()
        // The Settings window is modeless by default, and the test needs a modal dialog.
        applyVMOptionsPatch { addSystemProperty("ide.ui.non.modal.settings.window", false) }
      }
  }

  @Test
  fun `a driver call in a modal dialog does not wait for the dumb mode that the dialog holds`(driver: Driver) {
    val openProject = driver.singleProject()
    withoutPauseOnIndicators {
      driver.toggleDumbMode(openProject)
      waitFor("The project is in dumb mode", timeout = 1.minutes) { driver.isDumb(openProject) }
      driver.ideFrame {
        openSettingsDialog()
        settingsDialog().waitFound()
      }
      driver.toggleDumbMode(openProject)
    }

    val call = measureTimedValue { driver.isDumb(openProject) }

    assertTrue(call.value, "The Settings dialog holds dumb mode")
    assertTrue(call.duration < 1.minutes, "The Driver call took ${call.duration}")

    driver.ideFrame { settingsDialog().closeDialog() }
    waitFor("The modal context ends", timeout = 1.minutes) { !driver.isInModalContext() }

    assertFalse(driver.isDumb(openProject), "The pause waits for smart mode after the dialog closes")
  }
}

private fun Driver.toggleDumbMode(project: Project) {
  invokeAction("DumbMode", component = getIdeFrame(project)?.getComponent())
}

private fun Driver.isDumb(project: Project): Boolean = service<DumbService>(project).isDumb()
