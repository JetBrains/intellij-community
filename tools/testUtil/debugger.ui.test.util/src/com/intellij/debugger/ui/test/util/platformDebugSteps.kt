package com.intellij.debugger.ui.test.util

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.components.common.restartDebugButton
import com.intellij.driver.sdk.ui.components.common.toolwindows.debugToolWindow
import com.intellij.driver.sdk.ui.components.elements.accessibleTree
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfo.isMac
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.debugger get() = PlatformDebugSteps(this)

class PlatformDebugSteps(private val ideFrame: IdeaFrameUI) {

  fun startDebugFromMainToolbar(waitForIndicators: Boolean = true): PlatformDebugSteps {
    ideFrame.run {
      step("Start debug process from main toolbar") {
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

  fun clickStepButton(kindOfStep: String) {
    step("Click $kindOfStep") {
      ideFrame.run {
        debugToolWindow {
          val button = xx { byAccessibleName(kindOfStep) }.list().last()
          waitFor(message = "Step button is not enabled", timeout = 10.seconds) { button.isEnabled() }
          button.click()
        }
      }
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

  fun checkFirstDebuggerFrame(frameText: String) {
    step("Check debugger top frame") {
      ideFrame.run {
        debugToolWindow {
          waitFound(10.seconds)
          val xDebuggerFrames = list(xQuery { byClass("XDebuggerFramesList") })
          shouldBe("XDebuggerFramesList is Empty or Loading") {
            xDebuggerFrames.items.isNotEmpty() && (xDebuggerFrames.items.findLast { it.contains("Loading") } == null)
          }
          shouldBe(
            "Top frame does not contain '$frameText': ${xDebuggerFrames.selectedItems.single()}"
          ) {
            xDebuggerFrames.selectedItems.single().contains(frameText)
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
