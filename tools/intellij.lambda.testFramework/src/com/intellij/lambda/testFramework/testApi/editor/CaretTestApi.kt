package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.callActionByShortcut
import com.intellij.lambda.testFramework.testApi.editor.ArrowType.DOWN
import com.intellij.lambda.testFramework.testApi.editor.ArrowType.LEFT
import com.intellij.lambda.testFramework.testApi.editor.ArrowType.RIGHT
import com.intellij.lambda.testFramework.testApi.editor.ArrowType.UP
import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_UP
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.testFramework.fixtures.EditorMouseFixture
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


suspend fun EditorImpl.moveToOffset(offset: Int, check: Boolean = true, latency: Duration = defaultTestLatency) {
  frameworkLogger.info("Move to offset '$offset'")
  delay(latency)
  writeIntentReadAction {
    caretModel.moveToOffset(offset)
  }
  if (check) {
    waitCaretOffset(offset, "Move was successful", 10.seconds)
  }
}

/**
 * @param line one-based
 * @param column one-based
 */
suspend fun EditorImpl.moveTo(line: Int, column: Int, check: Boolean = true, latency: Duration = defaultTestLatency): Int {
  frameworkLogger.info("Moving to line: ${line - 1} col: ${column - 1}")
  delay(latency)
  val logicalPosition = LogicalPosition(line - 1, column - 1)
  //todo try read action here
  writeIntentReadAction {
    caretModel.moveToLogicalPosition(logicalPosition)
  }
  if (check) {
    waitCaretLogicalPosition(logicalPosition, "Move was successful")
  }
  return readAction { caretModel.offset }
}

suspend fun EditorImpl.waitCaretOffset(expectedOffset: Int, subjectOfWaiting: String? = null, timeout: Duration = 30.seconds) {
  waitSuspending("${if (subjectOfWaiting != null) "$subjectOfWaiting: " else ""}Caret offset is at $expectedOffset", timeout,
                 getter = { readAction { caretModel.offset } },
                 checker = { it == expectedOffset })
}

suspend fun EditorImpl.waitVisualPosition(timeout: Duration = 30.seconds, subjectOfWaiting: String? = null, checker: (VisualPosition) -> Boolean) {
  waitSuspending("${if (subjectOfWaiting != null) "$subjectOfWaiting: " else ""}Visual position passes the check", timeout,
                 getter = { readAction { caretModel.visualPosition } },
                 checker = checker)
}

suspend fun EditorImpl.waitVisualPositionLine(visualPositionLine: Int, subjectOfWaiting: String? = null, timeout: Duration = 30.seconds) {
  waitSuspending("${if (subjectOfWaiting != null) "$subjectOfWaiting: " else ""}Visual position line is $visualPositionLine", timeout,
                 getter = { readAction { caretModel.visualPosition } },
                 checker = { it.line == visualPositionLine })
}

suspend fun EditorImpl.waitCaretPosition(expectedLine: Int, expectedColumn: Int, subjectOfWaiting: String? = null, timeout: Duration = 30.seconds) =
  waitSuspending("${if (subjectOfWaiting != null) "$subjectOfWaiting: " else ""}Caret offset is at ($expectedLine, $expectedColumn)", timeout,
                 getter = { readAction { caretModel.logicalPosition } },
                 checker = { it.line + 1 == expectedLine && it.column + 1 == expectedColumn })

private suspend fun EditorImpl.waitCaretLogicalPosition(expectedLogicalPosition: LogicalPosition, subjectOfWaiting: String? = null, timeout: Duration = 30.seconds) =
  waitSuspending("${if (subjectOfWaiting != null) "$subjectOfWaiting: " else ""}Caret offset is at $expectedLogicalPosition", timeout,
                 getter = { readAction { caretModel.logicalPosition } },
                 checker = { it == expectedLogicalPosition })

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.pressDownArrow(
  repeat: Int = 1,
  latency: Duration = defaultTestLatency,
  check: Boolean = false,
  waitTimeout: Duration = 30.seconds,
) {
  pressArrow(arrow = DOWN, repeat = repeat, latency = latency, check = check, waitTimeout = waitTimeout)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.pressUpArrow(
  repeat: Int = 1,
  latency: Duration = defaultTestLatency,
  check: Boolean = false,
  waitTimeout: Duration = 30.seconds,
) {
  pressArrow(arrow = UP, repeat = repeat, latency = latency, check = check, waitTimeout = waitTimeout)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.pressLeftArrow(
  repeat: Int = 1,
  latency: Duration = defaultTestLatency,
  check: Boolean = false,
  waitTimeout: Duration = 30.seconds,
) {
  pressArrow(arrow = LEFT, repeat = repeat, latency = latency, check = check, waitTimeout = waitTimeout)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.pressRightArrow(
  repeat: Int = 1,
  latency: Duration = defaultTestLatency,
  check: Boolean = false,
  waitTimeout: Duration = 30.seconds,
) {
  pressArrow(arrow = RIGHT, repeat = repeat, latency = latency, check = check, waitTimeout = waitTimeout)
}

suspend fun EditorImpl.doubleClickAt(line: Int, column: Int, latency: Duration = defaultTestLatency) {
  frameworkLogger.info("Double click at line: ${line - 1} col: ${column - 1}")
  delay(latency)
  val logicalPosition = LogicalPosition(line - 1, column - 1)
  val visualPosition = logicalToVisualPosition(logicalPosition)
  writeIntentReadAction {
    EditorMouseFixture(this).doubleClickAt(visualPosition.line, visualPosition.column)
  }
}

private enum class ArrowType(val action: String) {
  DOWN(ACTION_EDITOR_MOVE_CARET_DOWN),
  UP(ACTION_EDITOR_MOVE_CARET_UP),
  RIGHT(ACTION_EDITOR_MOVE_CARET_RIGHT),
  LEFT(ACTION_EDITOR_MOVE_CARET_LEFT)
}

/**
 * Presses the specified arrow button the specified number of times.
 * Optionally checks that the carriage has moved correctly
 *
 * @param check if true checks the line and column after a button is pressed.
 * Note: this check does not take into account that:
 * - column may change position due to formatting when moved up or down
 * - line may be changed when right/left pressed at the end/start of the line
 * - etc
 * Use [waitCaretPosition] explicitly in this case
 */
context(lambdaIdeContext: LambdaIdeContext)
private suspend fun EditorImpl.pressArrow(
  arrow: ArrowType,
  repeat: Int,
  latency: Duration = defaultTestLatency,
  check: Boolean = false,
  waitTimeout: Duration = 30.seconds,
) {
  val current = caretModel.logicalPosition
  callActionByShortcut(arrow.action, repeat, latency)

  if (check) {
    val expected = when (arrow) {
      DOWN -> LogicalPosition(current.line + repeat, current.column)
      UP -> LogicalPosition(current.line - repeat, current.column)
      RIGHT -> LogicalPosition(current.line, current.column + repeat)
      LEFT -> LogicalPosition(current.line, current.column - repeat)
    }

    waitCaretLogicalPosition(expected, timeout = waitTimeout)
  }
}
