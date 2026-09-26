package com.intellij.jupyter.ui.test.util.completion

import com.intellij.driver.sdk.ui.components.notebooks.LastCell
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import kotlin.time.Duration.Companion.milliseconds

fun NotebookEditorUiComponent.checkCompletion(codeToComplete: String, expectedCompletion: String) {
  typeInCell(LastCell, codeToComplete, 200.milliseconds)

  driver.checkCompletionVariantsWithReport(expectedCompletion)
  keyboard {
    enter() // submit the popup
  }
}
