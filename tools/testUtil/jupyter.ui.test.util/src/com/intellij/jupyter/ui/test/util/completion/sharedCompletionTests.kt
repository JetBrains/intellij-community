package com.intellij.jupyter.ui.test.util.completion

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.editor.completionList
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.notebooks.notebookEditor
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// expects multiple completion options
fun Driver.testCompletion(
  codeToRun: String,
  codeToComplete: String,
  expectedCompletion: String,
) {
  withNotebookEditor {
    step("Create code cells") {
      if (codeToRun != "") addCodeCell(codeToRun)
      addCodeCell(codeToComplete)
      invokeAction("EditorLineEnd")
    }
  }
  ideFrame {
    waitFor(message = "Completion failed", timeout = 1.minutes, interval = 5.seconds) {
      runCatching {
        invokeAction("CodeCompletion")
        step("Select completion variant") {
          completionList().clickOnCompletionVariant(expectedCompletion)
          keyboard { enter() }
        }
        notebookEditor().text.contains(expectedCompletion)
      }.getOrDefault(false)
    }
  }
}
