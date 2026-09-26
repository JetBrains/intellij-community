package com.intellij.jupyter.ui.test.util.utils

import com.intellij.debugger.ui.test.util.debugger
import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.DebugToolWindowUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.debugToolWindow
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.notebooks.CellSelector
import com.intellij.driver.sdk.ui.components.notebooks.FirstCell
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val Driver.debugPanel: UiComponent
  get() = ideFrame().x("//div[@class='JBRunnerTabs']")

fun Driver.debugPanel(action: UiComponent.() -> Unit = {}): UiComponent {
  return debugPanel.apply(action)
}

val Driver.debuggerTree: UiComponent
  get() = ideFrame().x("//div[@class='XDebuggerTree']")

val Driver.debugToolWindow: DebugToolWindowUi
  get() = ideFrame().debugToolWindow()

fun Driver.debugToolWindow(action: DebugToolWindowUi.() -> Unit = {}) {
  ideFrame().debugToolWindow(action)
}

val Driver.debugButton: UiComponent
  get() = ideFrame().x("//div[@accessiblename='Debug' and @class='SquareStripeButton']")

val IdeaFrameUI.debuggerModePopup
  get() = popup("//div[contains(@visible_text, 'debugpy')]")

val Driver.expressionAndWatchEditor: UiComponent
  get() = debugPanel().x { byAccessibleName("Editor") }

fun Driver.startDebugCell(cellSelector: CellSelector = FirstCell) {
  withNotebookEditor {
    clickOnCell(cellSelector)
    waitFor("Cell actions must be present") {
      cellActions.isNotEmpty()
    }
    cellActions.first().run {
      debugButton.strictClick()
    }
    waitFor(timeout = 1.minutes, message = "Debug should start") {
      notebookCellExecutionInfos.isNotEmpty() &&
      notebookCellExecutionInfos.last().getAllTexts { it.text.contains("Notebook debugging in progress") }.isNotEmpty() &&
      debuggerTree.present()
    }
    wait(5.seconds)
  }
  ideFrame {
    if (button("Got It").present()) {
      button("Got It").click()
    }
    keyboard { escape() } // needed to remove a message that blocks debug buttons
  }
}

fun Driver.debugTreeContains(name: String): Boolean {
  return debuggerTree.getAllTexts { it.text.contains(name) }.isNotEmpty()
}

fun Driver.resumeProgram() {
  debugToolWindow {
    val resumeButton = resumeButton
    waitFor("Resume button must be enabled", timeout = 15.seconds) {
      resumeButton.isEnabled()
    }
    resumeButton.click()
  }
}

fun Driver.debugInterrupt() {
  debugToolWindow {
    val stopButton = stopButton
    waitFor("Stop button must be enabled") {
      stopButton.isEnabled()
    }
    stopButton.click()
  }
}

fun Driver.stepInto() {
  debugPanel {
    x("//div[@myicon='stepInto.svg']").click()
  }
}

fun Driver.stepIntoMyCode() {
  debugPanel {
    x("//div[@myicon='stepIntoMyCode.svg']").click()
  }
}

fun Driver.stepOut() {
  debugPanel {
    x("//div[@myicon='stepOut.svg']").click()
  }
}

fun Driver.muteBreakPoints() {
  debugPanel {
    x("//div[@myicon='muteBreakpoints.svg']").click()
  }
}

fun Driver.debugEvaluateExpression(expression: String) {
  expressionAndWatchEditor.click()
  ideFrame {
    keyboard {
      ui.pasteText(expression)
      enter()
      enter()
    }
  }
}

fun Driver.debugAddNewWatchQuick(text: String) {
  expressionAndWatchEditor.click()
  ideFrame {
    keyboard {
      ui.pasteText(text)
    }
    x { byAccessibleName("Add to Watches") }.click()
  }
}

fun Driver.debugAddNewWatch(text: String) {
  debuggerTree.rightClick()
  ideFrame {
    popup().waitOneText("New Watch…").click()
    ui.pasteText(text)
    keyboard { enter() }
  }
}

fun Driver.removeAllBreakpoints() {
  ideFrame().debugger.removeAllBreakpoints()
}

fun IdeaFrameUI.placeBreakpointInFile(filePath: String, line: Int) {
  driver.openFile(filePath)
  debugger.setBreakpointAtLine(line)
}

/**
 * Options: pydevd, debugpy
 */
fun Driver.switchDebugMode(option: String) {
  invokeAction("Python.DebuggerBackendSwitcher")
  ideFrame {
    waitFor {
      debuggerModePopup.present()
    }
    debuggerModePopup.waitOneText(option).click()
  }
}

/**
 * Sets breakpoint in the cell at the given index and row (both starting from 1).
 */
fun NotebookEditorUiComponent.setBreakPoint(cellIndex: Int, row: Int) {
  val rowIndexes = cellIndexPanel.getAllTexts()
  var currentIndex = 0
  for (index in rowIndexes) {
    if (index.text == "1") {
      currentIndex++
    }
    if (currentIndex == cellIndex && index.text == row.toString()) {
      index.strictClick()
      return
    }
    if (currentIndex > cellIndex) {
      break
    }
  }
}
