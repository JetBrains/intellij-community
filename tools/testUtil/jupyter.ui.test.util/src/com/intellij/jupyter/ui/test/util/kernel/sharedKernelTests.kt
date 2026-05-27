package com.intellij.jupyter.ui.test.util.kernel

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.elements.JLabelUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.hasFailedExecutionIcon
import com.intellij.driver.sdk.ui.components.notebooks.waitForHighlighting
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitFor
import com.intellij.jupyter.ui.test.util.utils.PostExecutionAwaitStrategy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun NotebookEditorUiComponent.waitCellsAreExecuted(
  timeout: Duration = 1.minutes,
  expectedExecutionCount: Int = notebookCellEditors.size,
  failIfAnyCellFailed: Boolean = true,
) {
  waitFor(timeout = timeout) {
    areAllExecutionsFinished(expectedExecutionCount)
  }
  if (failIfAnyCellFailed &&
      notebookCellExecutionInfos.any {
        it.hasFailedExecutionIcon()
      }) error("Some cells failed")

}

fun NotebookEditorUiComponent.runAllCellsAndWaitExecuted(
  timeout: Duration = 1.minutes,
  postExecutionStrategy: PostExecutionAwaitStrategy = PostExecutionAwaitStrategy.AwaitNone,
  expectedExecutionCount: Int = notebookCellEditors.size,
  failIfAnyCellFailed: Boolean = true,
) {
  step("Executing all cells") {
    runAllCells()
    waitCellsAreExecuted(expectedExecutionCount = expectedExecutionCount, timeout = timeout, failIfAnyCellFailed = failIfAnyCellFailed)
    awaitUpdated(postExecutionStrategy)
  }
}

/**
 * Helper function for different wait-targets in an IDE
 */
fun NotebookEditorUiComponent.awaitUpdated(
  awaitStrategy: PostExecutionAwaitStrategy = PostExecutionAwaitStrategy.AwaitNone
) {
  fun waitHighlighting() = step("Waiting for highlighting") {
    waitForHighlighting()
  }

  when (awaitStrategy) {
    PostExecutionAwaitStrategy.AwaitHighlighting -> waitHighlighting()
    PostExecutionAwaitStrategy.AwaitNone -> Unit
  }
}

fun NotebookEditorUiComponent.runCellAndWaitExecuted(
  timeout: Duration = 30.seconds,
  expectedFinalExecutionCount: Int = 1,
  postExecutionStrategy: PostExecutionAwaitStrategy = PostExecutionAwaitStrategy.AwaitNone,
  failIfExecutionFailed: Boolean = true
): Unit = step("Executing cell") {
  val currentCellIndex = selectedCellOrdinal ?: error("No cell selected")
  runCell()
  waitFor(timeout = timeout) {
    areAllExecutionsFinished(expectedFinalExecutionCount) && getCellExecutionState(currentCellIndex) != null
  }
  if (failIfExecutionFailed && getCellExecutionState(currentCellIndex) == NotebookEditorUiComponent.CellExecutionState.FAILED)
    error("Cell execution failed")
  awaitUpdated(awaitStrategy = postExecutionStrategy)
}

/*
  This functions should be removed when fixed:
  PY-84369
  PY-84374
 */
fun NotebookEditorUiComponent.softRunAllCellsAndWaitExecuted(timeout: Duration = 2.minutes): Unit = step("Executing all cells") {
  runAllCells()
  waitFor(timeout = timeout) {
    val infos = notebookCellExecutionInfos
    val timesBefore = infos.map { it.getExecutionTimeInMsSafe() }

    wait(250.milliseconds)

    val timesAfter = infos.map { it.getExecutionTimeInMsSafe() }

    infos.isNotEmpty()
    && timesAfter.all { it != null }
    && timesBefore == timesAfter
  }
}

fun JLabelUiComponent.getExecutionTimeInMsSafe(): Long? = step("Get cell execution time") {
  if (this.notPresent()) return@step null
  val text = this.getText()
  if (text.isEmpty()) return@step null

  val seconds = Regex("""(\d+)s""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
  val millis = Regex("""(\d+)ms""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

  seconds * 1_000 + millis
}

fun JLabelUiComponent.getExecutionTime(): Duration = step("Get cell execution time") {
  this.getText().run {
    val matchSeconds = Regex("\\d+s").find(this)?.value?.substringBefore("s")?.toLong() ?: 0
    val matchMs = Regex("\\d+ms").find(this)?.value?.substringBefore("ms")?.toLong() ?: 0

    matchSeconds.seconds + matchMs.milliseconds
  }
}
