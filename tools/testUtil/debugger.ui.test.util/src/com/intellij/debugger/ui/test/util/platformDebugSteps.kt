package com.intellij.debugger.ui.test.util

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.tryToKillAllStartedProcesses
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.gutter
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.components.common.restartDebugButton
import com.intellij.driver.sdk.ui.components.common.toolwindows.debugToolWindow
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleTree
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.requestFocusFromIde
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.waitNotNull
import com.intellij.driver.sdk.withRetries
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfo.isMac
import java.awt.event.KeyEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.debugger get() = PlatformDebugSteps(this)

class PlatformDebugSteps(private val ideFrame: IdeaFrameUI) {

  fun startDebugFromMainToolbar(waitForIndicators: Boolean = true): PlatformDebugSteps {
    ideFrame.run {
      step("Start debug process from main toolbar") {
        waitForRunConfigurationWidget()
        mainToolbar.debugButton.waitFound()
        shouldBe("Debug button is not enabled") { mainToolbar.debugButton.isEnabled() }
        mainToolbar.debugButton.click()
        if (waitForIndicators) {
          driver.waitForIndicators(project!!, 5.minutes)
        }
      }
    }
    return this
  }

  /**
   * The main toolbar publishes the run and debug buttons only after the run-configuration widget resolves.
   * A read before that finds no button at all. Best effort: a toolbar without the widget falls through to
   * the debug button lookup unchanged.
   */
  private fun waitForRunConfigurationWidget(timeout: Duration = 30.seconds) {
    runCatching {
      waitFor(message = "Run configuration widget holds a configuration", timeout = timeout) {
        runCatching { ideFrame.mainToolbar.runWidget.text.isNotBlank() }.getOrDefault(false)
      }
    }
  }

  fun stopDebugFromMainToolbar() {
    ideFrame.run {
      step("Stop debug process from main toolbar") {
        mainToolbar.stopButton.waitFound()
        mainToolbar.stopButton.click()
      }
    }
  }

  fun stopDebugFromDebugToolWindow() {
    ideFrame.run {
      step("Stop debug process from debug tool window") {
        debugToolWindow().stopButton.waitFound()
        debugToolWindow().stopButton.click()
      }
    }
  }

  fun restartDebugFromDebugToolWindow() {
    ideFrame.run {
      step("Restart debug process from debug tool window") {
        debugToolWindow {
          val button = x { byAttribute("myicon", "restartDebug.svg") }
          waitFor(message = "Restart button is not enabled", timeout = 10.seconds) { button.isEnabled() }
          button.click()
        }
      }
    }
  }

  fun removeAllBreakpoints(): PlatformDebugSteps {
    ideFrame.run {
      codeEditor {
        driver.invokeAction("Debugger.RemoveAllBreakpoints")
      }
    }
    return this
  }

  fun clickStepButton(kindOfStep: String, timeout: Duration = 10.seconds) {
    step("Click $kindOfStep") {
      ideFrame.run {
        debugToolWindow {
          // Re-query the button on every poll: the debug toolbar recreates its ActionButtons when the
          // session state changes, so a reference captured once may stay disabled while the live button
          // is already enabled.
          val button = waitNotNull<UiComponent>(
            message = "$kindOfStep button did not become enabled",
            timeout = timeout,
            // Separate "the toolbar never published the button" from "the button is there and disabled".
            errorMessage = { _ ->
              if (xx { byAccessibleName(kindOfStep) }.list().isEmpty()) {
                "No '$kindOfStep' button in the debug toolbar after $timeout. The session never reached a suspended state."
              }
              else {
                "The '$kindOfStep' button stayed disabled for $timeout. The session is not suspended."
              }
            }) {
            xx { byAccessibleName(kindOfStep) }.list().lastOrNull { it.isEnabled() }
          }
          button.click()
        }
      }
    }
  }

  fun waitUntilDebuggerPausedAfterStart(): JTreeUiComponent {
    return ideFrame.run {
      debugToolWindow().variableTree.shouldBe(message = "Debugger toolwindow is opened",
                                              timeout = 3.minutes,
                                              condition = { present() })
        .apply {
          waitForNodesLoaded(3.minutes)
        }
        .also { setFocus() }
    }
  }

  fun setBreakpointAtLine(lineNumber: Int, editorUiComponent: JEditorUiComponent = ideFrame.editor()): PlatformDebugSteps {
    step("Set breakpoint at line $lineNumber") {
      ideFrame.run {
        editorUiComponent.apply {
          goToPosition(lineNumber, 1)
        }
        driver.invokeAction("ToggleLineBreakpoint")
      }
    }
    return this
  }

  /**
   * Sets a breakpoint at [lineNumber] of the editor of [fileName], and waits for the breakpoint icon in the file editor gutter.
   * Tries [times] times. The icon lookup excludes the gutter of a debug console, which a previous session can leave in the frame.
   */
  fun setBreakpointAtLineWithRetries(lineNumber: Int, fileName: String, times: Int = 3): PlatformDebugSteps {
    ideFrame.run {
      withRetries(message = "The breakpoint at line $lineNumber of $fileName is set", times = times) {
        driver.requestFocusFromIde(driver.singleProject())
        setBreakpointAtLine(lineNumber, codeEditorForFile(fileName))
        // GutterIcon.line is 0-based.
        gutter("//div[@class='EditorTabs']//div[@class='EditorGutterComponentImpl']")
          .icon(timeout = 10.seconds, errorMessage = "No breakpoint icon at line $lineNumber") { it.line == lineNumber - 1 }
      }
    }
    return this
  }

  /**
   * Ends every session that a previous test left: waits up to [timeout] until the main toolbar has no Stop button and shows
   * the Debug button, and kills all started processes every [interval] until then.
   */
  fun stopDebugSessionIfRunning(timeout: Duration = 1.minutes, interval: Duration = 5.seconds) {
    ideFrame.run {
      // A failed test can leave a suspended session that ignores Stop, or a session in another tab.
      waitFor(message = "The sessions of the previous test end", timeout = timeout, interval = interval) {
        val ended = runCatching { !mainToolbar.stopButton.present() && mainToolbar.debugButton.present() }.getOrDefault(false)
        if (!ended) driver.tryToKillAllStartedProcesses()
        ended
      }
    }
  }

  /** Copies the value of [variable] with the "Copy Value" item of the variables tree context menu. */
  fun copyValue(variable: String) {
    step("Copy the value of '$variable'") {
      ideFrame.run {
        debugToolWindow().variableTree.rightClickRow { it.contains("$variable = ") }
        popup().waitOneText(timeout = 10.seconds) { it.text == "Copy Value" }.click()
      }
    }
  }

  fun setBreakpointAtLineByShortcut(lineNumber: Int) {
    step("Set breakpoint at line $lineNumber ") {
      ideFrame.run {
        editor().goToPosition(lineNumber, 1)
        keyboard {
          if (isMac) {
            step("CMD + F8") {
              pressing(KeyEvent.VK_META) { key(KeyEvent.VK_F8) }
            }
          }
          else {
            step("CTRL + F8") {
              pressing(KeyEvent.VK_CONTROL) { key(KeyEvent.VK_F8) }
            }
          }
        }
      }
    }
  }

  fun checkFirstDebuggerFrame(vararg frameTexts: String, timeout: Duration = 15.seconds) {
    require(frameTexts.isNotEmpty()) { "At least one expected frame text must be provided" }
    step("Check debugger top frame") {
      ideFrame.run {
        debugToolWindow {
          waitFound(10.seconds)
          val xDebuggerFrames = list(xQuery { byClass("XDebuggerFramesList") })
          should(
            message = "Top frame does not contain any of ${frameTexts.toList()}",
            timeout = timeout,
            errorMessage = {
              val frames = xDebuggerFrames.items
              when {
                frames.isEmpty() -> "XDebuggerFramesList is empty"
                frames.any { it.contains("Loading") } -> "XDebuggerFramesList is still loading: $frames"
                else -> "Top frame does not contain any of ${frameTexts.toList()}: ${xDebuggerFrames.selectedItems.singleOrNull().orEmpty()}"
              }
            },
          ) {
            val frames = xDebuggerFrames.items
            val topFrame = xDebuggerFrames.selectedItems.singleOrNull()
            frames.isNotEmpty() &&
            frames.none { it.contains("Loading") } &&
            topFrame != null && frameTexts.any { topFrame.contains(it) }
          }
        }
      }
    }
  }

  fun checkDebuggerFrames(frameText: String) {
    step("Check all debugger frames") {
      ideFrame.run {
        debugToolWindow {
          waitFound(10.seconds)
          val xDebuggerFrames = list(xQuery { byClass("XDebuggerFramesList") })
          shouldBe("XDebuggerFramesList is Empty or Loading") {
            xDebuggerFrames.items.isNotEmpty() && (xDebuggerFrames.items.findLast { it.contains("Loading") } == null)
          }
          shouldBe(
            "Debugger frames do not contain '$frameText': ${xDebuggerFrames.getAllTexts().asString()}"
          ) {
            xDebuggerFrames.getAllTexts().asString().contains(frameText)
          }
        }
      }
    }
  }

  fun checkDebuggerTree(variable: String) {
    step("Check debugger tree") {
      ideFrame.run {
        debugToolWindow {
          accessibleTree().waitContainsText(variable, "Debugger tree does not contain '$variable'", timeout = 10.seconds)
        }
      }
    }
  }

  fun evaluateExpression(expressionToEvaluate: String, setText: Boolean = true, actionToDoBeforeEvaluation: () -> Unit = {}) {
    step("Evaluate expression $expressionToEvaluate") {
      ideFrame.run {
        waitForDebugToolWindowsReady()
        debugToolWindow().editor(xQuery { byAccessibleName("Editor") }).apply {
          click()
          if (setText) {
            this.text = expressionToEvaluate
          }
          else {
            keyboard {
              typeText(expressionToEvaluate)
            }
          }
          run(actionToDoBeforeEvaluation)
          click()
          keyboard {
            hotKey(KeyEvent.VK_ENTER)
            hotKey(KeyEvent.VK_ENTER)
          }
        }
      }
    }
  }


  fun runToCursor() {
    step("Run to cursor") {
      ideFrame.run {
        keyboard {
          if (SystemInfo.isLinux)
            hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_9)
          else
            hotKey(KeyEvent.VK_ALT, KeyEvent.VK_F9)
        }
      }
    }
  }

  fun checkDebuggerSuccessfullyStops(consoleText: String) {
    step("Check debugger stops with text $consoleText") {
      ideFrame.run {
        debugToolWindow().waitFound(timeout = 30.seconds).apply {
          val console = xx { and(byClass("SimpleColoredComponent"), byAccessibleName("Console")) }.list().first()
          waitFor(timeout = 10.seconds, message = "Console tab is not clickable/showing yet") {
            runCatching {
              console.click()
            }.isSuccess
          }
          val consoleOutput = editor().text
          shouldBe("Editor does not contain $consoleText. Actual output\n:$consoleOutput") {
            consoleOutput.contains(consoleText)
          }
          threadsAndVariablesTab.waitFound().click()
          checkDebuggersFramesAreEmpty()
        }
      }
    }
  }

  fun checkDebuggersFramesAreEmpty() {
    ideFrame.run {
      debugToolWindow {
        threadsAndVariablesTab.click()
        val xDebuggerFrames = list(xQuery { byClass("XDebuggerFramesList") })
        shouldBe("Frames are not empty") {
          xDebuggerFrames.items.isEmpty()
        }
      }
    }
  }

  fun waitForDebugToolWindowsReady() {
    ideFrame.run {
      waitFor(timeout = 10.seconds) {
        runCatching {
          mainToolbar.restartDebugButton.present() && !debugToolWindow().tree().getAllTexts().asString().contains("Collecting")
        }.isSuccess
      }
    }
  }
}
