package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.sdk.ui.components.elements.JLabelUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.hasSuccessfulExecutionIcon
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.jupyter.ui.test.util.kernel.getExecutionTime
import kotlin.time.Duration

fun NotebookEditorUiComponent.assertCellExecutionTimeIsAtLeast(
  info: JLabelUiComponent,
  atLeastDuration: Duration,
) {
  shouldBe("Task should be running at least $atLeastDuration") {
    info.getExecutionTime() >= atLeastDuration
  }
}

fun cellExecuted(info: JLabelUiComponent): Boolean {
  return info.hasSuccessfulExecutionIcon()
}
