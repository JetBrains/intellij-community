package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.notebooks.FirstCell
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.waitFor
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Shared smoke test utilities used across Kotlin and Python notebooks to avoid duplication
fun NotebookEditorUiComponent.checkRunCellsAndCleanUpOutputs(
  runAllCellsAndAwait: NotebookEditorUiComponent.(kotlin.time.Duration) -> Unit = { timeout -> runAllCellsAndWaitExecuted(timeout) },
) {
  step("Add one more code cell") {
    pasteToCell(FirstCell, "print(1)")
    addCodeCell("print(2)")
  }
  step("Run cells and check the outputs") {
    runAllCellsAndAwait(1.minutes)
    should("The cells should be executed", 30.seconds) {
      notebookCellOutputs.size == 2
    }
  }
  step("Clear outputs check") {
    clearAllOutputs()
    waitFor("The cells should be cleaned up", 15.seconds) {
      notebookCellOutputs.isEmpty()
      &&
      notebookCellExecutionInfos.isEmpty()
    }
  }
}

/**
 * Repeatedly runs all cells added in [createInitialCells] to reveal possible
 * race conditions and other issues in cell execution logic
 */
fun Driver.runAllCellsRepeatedly(
  times: Int = 50,
  createInitialCells: NotebookEditorUiComponent.() -> Unit = {},
) {
  withNotebookEditor {
    step("Add initial cells and run them") {
      createInitialCells()
    }
    step("Run cells over and over") {
      repeat(times) {
        runAllCellsAndWaitExecuted(10.seconds)
      }
    }
  }
}