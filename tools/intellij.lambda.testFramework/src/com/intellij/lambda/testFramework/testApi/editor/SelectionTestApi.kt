package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.callActionByShortcut
import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import kotlinx.coroutines.delay
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


suspend fun EditorImpl.selectedText(): String? =
  readAction { selectionModel.selectedText }

suspend fun EditorImpl.selectedTextOrThrow(): String =
  selectedText() ?: error("Expected to have some selection")

suspend fun Editor.setSelection(startOffset: Int, endOffset: Int, latency: Duration = defaultTestLatency) {
  frameworkLogger.info("Setting selection")
  delay(latency)
  writeIntentReadAction {
    selectionModel.setSelection(startOffset, endOffset)
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.selectCharacters(charCount: Int, expectedSelectionCharsNumber: Int = charCount, latency: Duration = defaultTestLatency) {
  for (i in 0 until charCount) {
    callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, latency = latency)
  }
  waitForNumberOfCharsSelection(expectedSelectionCharsNumber)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.moveCaretRightWithSelection(
  repeat: Int = 1,
  latency: Duration = defaultTestLatency,
  expectedSelectionCharsNumber: Int = repeat,
) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, repeat, latency)
  waitForNumberOfCharsSelection(expectedSelectionCharsNumber)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.moveCaretLeftWithSelection(
  repeat: Int = 1,
  latency: Duration = defaultTestLatency,
  expectedSelectionCharsNumber: Int = repeat,
) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, repeat, latency)
  waitForNumberOfCharsSelection(expectedSelectionCharsNumber)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.moveCaretDownWithSelection(
  repeat: Int = 1,
  latency: Duration = defaultTestLatency,
  expectedSelectionLinesNumber: Int = repeat,
) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION, repeat, latency)
  waitForNumberOfLinesSelection(expectedSelectionLinesNumber)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.moveCareUpWithSelection(
  repeat: Int = 1,
  latency: Duration = defaultTestLatency,
  expectedSelectionLinesNumber: Int = repeat + 1,
) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION, repeat, latency)
  waitForNumberOfLinesSelection(expectedSelectionLinesNumber)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.selectAll() {
  callActionByShortcut(IdeActions.ACTION_SELECT_ALL)
  waitForSelection { it.selectedText == document.text }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.selectLines(
  linesCount: Int,
  toEndOfLastLine: Boolean = true,
  latency: Duration = defaultTestLatency,
  expectedSelectionLinesNumber: Int = linesCount,
) {
  assertTrue { linesCount > 0 }
  callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION, linesCount - 1, latency)
  if (toEndOfLastLine) {
    callActionByShortcut(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, latency = latency)
  }
  waitForNumberOfLinesSelection(expectedSelectionLinesNumber, 5.seconds)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.selectWordAtCaret(latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET, latency = latency)
  waitForSelection()
  waitSuspending("Wait for no modality", 5.seconds) {
    ModalityState.current() == ModalityState.nonModal()
  }
}

suspend fun EditorImpl.waitForExactSelection(text: String, timeout: Duration = 20.seconds) {
  waitSuspending("Selection is '$text'", timeout,
                 getter = { selectedText() },
                 checker = { it == text }
  )
}

suspend fun EditorImpl.waitForNumberOfLinesSelection(linesCount: Int, timeout: Duration = 20.seconds) =
  waitForSelection("Waiting for selection to contain $linesCount lines", timeout) { it.getNumberOfLinesSelection() == linesCount }

fun SelectionModel.getNumberOfLinesSelection(): Int {
  val end = selectionEndPosition?.line
  val lead = leadSelectionPosition?.line
  if (lead != null && end != null)
    return end - lead + 1
  else
    return 0
}

suspend fun EditorImpl.waitForNumberOfCharsSelection(charsCount: Int, timeout: Duration = 20.seconds) =
  waitForSelection("Waiting for selection to contain $charsCount chars", timeout) { it.getNumberOfCharsSelection() == charsCount }

fun SelectionModel.getNumberOfCharsSelection(): Int =
  selectionEnd - selectionStart

suspend fun EditorImpl.waitForSelection(
  message: String = "Wait till selection appears",
  timeout: Duration = 20.seconds,
  checker: (SelectionModel) -> Boolean = { it.selectedText?.isNotEmpty() == true },
) =
  waitSuspending(
    message,
    timeout,
    getter = { selectionModel },
    checker = { it.selectedText != null && checker(it) },
    failMessageProducer = { "Actual: ${it?.selectedText.orEmpty()} [${it?.selectionStart}..${it?.selectionEnd}]" }
  )

suspend fun EditorImpl.waitForSelection(
  message: String = "Wait till selection appears",
  timeout: Duration = 20.seconds,
  expectedSelectText: String,
) =
  waitSuspending(
    message,
    timeout,
    getter = { selectionModel },
    checker = { it.selectedText == expectedSelectText },
    failMessageProducer = {
      "Expected: ${expectedSelectText}\n" +
      "Actual: ${it?.selectedText.orEmpty()} [${it?.selectionStart}..${it?.selectionEnd}]"
    }
  )


suspend fun EditorImpl.waitForNoSelection(timeout: Duration = 20.seconds) {
  waitSuspending("No text is selected", timeout) { selectedText() == null }
}
