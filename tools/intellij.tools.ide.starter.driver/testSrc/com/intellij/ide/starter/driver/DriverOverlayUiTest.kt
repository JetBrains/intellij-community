package com.intellij.ide.starter.driver

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.ShownOverlay
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.ui.dragAndDrop
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shownOverlays
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.driverInfoOverlaysEnabled
import com.intellij.ide.starter.driver.reused.ReusedProject
import com.intellij.ide.starter.driver.reused.ReusedRun
import com.intellij.ide.starter.driver.reused.cachedProjectConfiguration
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Point
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val SAMPLE_FILE = "sample.txt"

/** The minimum time of an outermost step overlay, and the time of a failure overlay, in the Driver SDK. */
private val OVERLAY_MIN_DURATION: Duration = 2.seconds

private val CHECK_TIMEOUT: Duration = 1.seconds

/** A nested step shows its overlay after 250 ms. */
private val LONGER_THAN_NESTED_STEP_DELAY: Duration = 1.seconds

/**
 * Checks the overlays of the Driver steps in a real IDE: the path and the time of the step, the failure overlays,
 * the mouse events under the overlay, and the overlay with a dialog.
 */
@ReusedRun
class DriverOverlayUiTest {
  companion object {
    @ReusedProject
    private val project: CachedProjectConfiguration = cachedProjectConfiguration(createProjectDir()) {
      applyVMOptionsPatch { disableGotItTooltips() }
    }
  }

  /** This test is outside the package of the UI tests, so it turns the overlays on. The setting resets after each test. */
  @BeforeEach
  fun enableOverlays() {
    ConfigurationStorage.driverInfoOverlaysEnabled(true)
  }

  @Test
  fun `the innermost step overlay shows, and a failure has priority over the step overlays`(driver: Driver) {
    with(driver) {
      waitForProjectOpen(1.minutes)
      val outer = ShownOverlay("Outer step", null)
      val inner = ShownOverlay("Outer step › Inner step", null)
      step(outer.title) {
        waitShown(outer, "the overlay shows")
        // A step overlay stays longer than the minimum time of the step overlays, and shows the time of the step.
        Thread.sleep((OVERLAY_MIN_DURATION + 1.seconds).inWholeMilliseconds)
        waitFor("the outer step overlay shows the time of the step", timeout = 2.seconds,
                getter = { shownOverlays() }, checker = { it == listOf(ShownOverlay("${outer.title} [3s]", null)) })

        step("Inner step") {
          waitShown(inner, "in a nested step")
          step("Level 3") {
            step("Level 4") {
              waitShown(ShownOverlay("Outer step › Inner step › Level 3 › Level 4", null), "in a deep step")
            }
          }
        }
        waitTitle(outer.title, "after the nested step")

        step("Short nested step") {}
        Thread.sleep(LONGER_THAN_NESTED_STEP_DELAY.inWholeMilliseconds)
        assertEquals(listOf(outer.title), shownTitles(), "a short nested step does not replace the overlay of its parent")
      }
      waitFor("the outer overlay goes after the step", timeout = 10.seconds, getter = { shownOverlays() }, checker = { it.isEmpty() })

      // invokeAction runs its step in withContext, through the delegate of the Driver of the test. The steps share one stack.
      val action = ShownOverlay("Action step", null)
      step(action.title) {
        invokeAction("SaveAll")
        waitShown(action, "after a step of the SDK in withContext")
      }

      step("Open the settings and check every option of the code style page") {
        waitTitle("Open the settings and check every option of the code style…", "a long step name ends at a word")
      }

      val short = ShownOverlay("Short step", null)
      val started = TimeSource.Monotonic.markNow()
      step(short.title) {}
      waitShown(short, "after the short step")
      waitFor("the short step overlay goes", timeout = 10.seconds, getter = { shownOverlays() }, checker = { it.isEmpty() })
      val goneAfter = started.elapsedNow()
      assertTrue(goneAfter >= OVERLAY_MIN_DURATION, "the short step overlay went after $goneAfter")

      val caught = ShownOverlay("Caught failure", null)
      step(caught.title) {
        assertThrows<WaitForException> { ideFrame().should(timeout = CHECK_TIMEOUT) { false } }
        waitShown(caught, "after a failure that the step catches")
      }

      val parent = ShownOverlay("Parent step", null)
      // A check without a message gives no second line.
      val failure = ShownOverlay("Step 'Failed step' failed", null)
      step(parent.title) {
        val checkStarted = TimeSource.Monotonic.markNow()
        assertThrows<WaitForException> { step("Failed step") { ideFrame().should(timeout = CHECK_TIMEOUT) { false } } }
        waitShown(failure, "after a failed check without a message")
        takeScreenshot("overlay_failure")
        step("Next step") {
          Thread.sleep(LONGER_THAN_NESTED_STEP_DELAY.inWholeMilliseconds)
          assertEquals(listOf(failure), shownOverlays(), "the failure has priority over a new step")
        }
        waitFor("the parent overlay comes back after the failure", timeout = 10.seconds,
                getter = { shownTitles() }, checker = { it == listOf(parent.title) })
        // The failure overlay shows after the timeout of the check, and stays for the minimum time.
        val failureGoneAfter = checkStarted.elapsedNow()
        assertTrue(failureGoneAfter >= CHECK_TIMEOUT + OVERLAY_MIN_DURATION, "the failure overlay went after $failureGoneAfter")
      }

      // The enclosing step sees the same failure, and does not show it again with its own name.
      assertThrows<WaitForException> {
        step("Outer failed step") { step("Failed step") { ideFrame().should(timeout = CHECK_TIMEOUT) { false } } }
      }
      waitShown(failure, "after a failure that leaves two steps")
    }
  }

  /** The screenshot is for a person: it shows how the overlay wraps a long path of steps. */
  @Test
  fun `a long path of steps ends at a word and wraps`(driver: Driver) {
    with(driver) {
      waitForProjectOpen(1.minutes)
      step("Prepare the project") {
        step("Open the code styles") {
          step("Check every wrapping option of the Kotlin code style page in the settings") {
            val path = "Prepare the project › Open the code styles › Check every wrapping option of the Kotlin code style page…"
            val expected = ShownOverlay(path, null)
            waitShown(expected, "the overlay shows")
            takeScreenshot("overlay_long_text")
          }
        }
      }
    }
  }

  /** The screenshots are for a person: they show how the time of a long step changes. */
  @Test
  fun `a long step shows its time`(driver: Driver) {
    with(driver) {
      waitForProjectOpen(1.minutes)
      step("Long step") {
        waitShown(ShownOverlay("Long step", null), "at the start of the step")
        for (time in listOf("3s", "6s", "9s")) {
          waitFor("the step overlay shows $time", timeout = 5.seconds,
                  getter = { shownOverlays() }, checker = { it == listOf(ShownOverlay("Long step [$time]", null)) })
          takeScreenshot("overlay_step_time_$time")
        }
        step("Nested step") {
          waitShown(ShownOverlay("Long step › Nested step", null), "in a short nested step")
        }
        // The screenshots and the nested step can take the time past the next interval, so the check ignores the time.
        waitTitle("Long step", "the parent shows its own overlay again")
      }
    }
  }

  /** The screenshot is for a person: it shows the part of the overlay that a dialog over it paints. */
  @Test
  fun `a dialog that opens over the overlay paints its part of the overlay`(driver: Driver) {
    with(driver) {
      waitForProjectOpen(1.minutes)
      val frame = ideFrame().component
      val title = "Open the settings over the overlay"
      step(title) {
        invokeAction("ShowSettings", now = false)
        ideFrame().settingsDialog {
          waitFound()
          // The top of the dialog goes over the overlay at the top of the IDE frame.
          val frameLocation = frame.getLocationOnScreen()
          setBounds(frameLocation.x + 100, frameLocation.y + 30, frame.width - 200, frame.height - 100)
          waitTitle(title, "the overlay shows")
          takeScreenshot("overlay_under_dialog")
          closeDialog()
          waitFor("the dialog closes") { !present() }
        }
      }
    }
  }

  /** The screenshot is for a person: the test cannot see where the overlay is on the screen. */
  @Test
  fun `a step overlay shows over an open dialog`(driver: Driver) {
    with(driver) {
      waitForProjectOpen(1.minutes)
      invokeAction("ShowSettings", now = false)
      ideFrame().settingsDialog {
        waitFound()
        step("Over a dialog") {
          waitTitle("Over a dialog", "the overlay shows")
          takeScreenshot("overlay_over_dialog")
        }
        closeDialog()
        waitFor("the dialog closes") { !present() }
      }
    }
  }

  @Test
  fun `mouse events go through the overlay`(driver: Driver) {
    with(driver) {
      waitForProjectOpen(1.minutes)
      openFile(SAMPLE_FILE)
      ideFrame { ensureFocused() }
      val editor = ideFrame().codeEditor()
      // The overlay starts at the left edge of a 600 px column at the top centre of the IDE frame.
      // There it is over the first line of the editor.
      val frame = ideFrame().component
      val overlayLeft = frame.getLocationOnScreen().x + (frame.width - minOf(600, frame.width - 32)) / 2
      val underOverlay = overlayLeft + 80 - editor.component.getLocationOnScreen().x
      val firstLine = editor.calculatePositionPoint(1, 1).y

      // An enclosing step shows at most 30 characters of its name.
      val outer = "Mouse under overlay"
      step(outer) {
        editor.moveCaretToOffset(editor.text.length)
        assertEquals(40, editor.getCaretLine(), "the caret must be on the last line")

        step("Click in the editor") {
          editor.click(Point(underOverlay, firstLine))
          waitFor("the click moves the caret to the first line", getter = { editor.getCaretLine() }, checker = { it == 1 })
          waitTitle("$outer › Click in the editor", "after the click")
        }

        step("Drag in the editor") {
          editor.removeSelection()
          // The drag starts on the text, not on the line numbers at the left edge of the overlay.
          ui.dragAndDrop(editor, Point(underOverlay, firstLine), editor, Point(underOverlay + 100, firstLine))
          waitFor("the drag selects text", getter = { editor.getSelection().orEmpty() }, checker = { it.isNotBlank() })
          waitTitle("$outer › Drag in the editor", "after the drag")
        }
      }
    }
  }
}

/** The time at the end of the title of a step overlay, for example ` [6s]`. */
private val STEP_TIME = Regex(""" \[[^]]+]$""")

/** A step overlay shows the time of the step after a few seconds, so a check of a long step compares the titles without it. */
private fun Driver.shownTitles(): List<String> = shownOverlays().map { it.title.replace(STEP_TIME, "") }

/** The IDE paints an overlay a moment after the step, and a nested step shows its overlay after a delay. */
private fun Driver.waitShown(overlay: ShownOverlay, description: String) {
  waitFor(description, timeout = 5.seconds, getter = { shownOverlays() }, checker = { it == listOf(overlay) })
}

private fun Driver.waitTitle(title: String, description: String) {
  waitFor(description, timeout = 5.seconds, getter = { shownTitles() }, checker = { it == listOf(title) })
}

private fun createProjectDir(): Path {
  val projectDir = createTempDirectory("driver-overlay").resolve("project").createDirectories()
  // Long lines, so the first line has text under the whole width of the overlay.
  val words = "word ".repeat(100)
  projectDir.resolve(SAMPLE_FILE).writeText((1..40).joinToString("\n") { "$it $words" })
  return projectDir
}
