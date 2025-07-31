// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.InputMethodInlayRenderer
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.output.TerminalOutputEditorInputMethodSupport
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.AWTEvent
import java.awt.event.InputMethodEvent
import java.awt.font.TextHitInfo
import java.text.AttributedString
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(JUnit4::class)
internal class TerminalInputMethodSupportTest : BasePlatformTestCase() {

  private lateinit var editor: EditorEx
  private lateinit var echoer: InputTextEchoer

  override fun runInDispatchThread(): Boolean = false

  private suspend fun init(coroutineScope: CoroutineScope) = withContext(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(TerminalUiUtils.getDefaultMaxOutputLength())
    editor = createEditor(model.document)
    echoer = InputTextEchoer(model, coroutineScope)
    TerminalOutputEditorInputMethodSupport(
      editor,
      coroutineScope,
      getCaretPosition = {
        val offset = model.cursorOffsetState.value
        editor.offsetToLogicalPosition(offset)
      },
      cursorOffsetFlow = model.cursorOffsetState,
      sendInputString = echoer::echo,
    )
  }

  private fun createEditor(document: Document): EditorEx {
    val editor = TerminalUiUtils.createOutputEditor(document, project, JBTerminalSystemSettingsProvider(), false)
    Disposer.register(testRootDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return editor
  }

  @Test
  fun `should handle repeated input of Korean characters`() = timeoutRunBlockingInBackground {
    for (i in 1..100) {
      val expectedDocumentText = "쇼ㅛ".repeat(i - 1) + "쇼"
      dispatchEventsAndAssert(
        listOfNotNull(
          // composed text from the previous step is converted to committed text
          if (i > 1) inputMethodTextChangedEvent("ㅛ", "") else null,
          inputMethodTextChangedEvent("", "ㅅ"),
          inputMethodTextChangedEvent("", "쇼"),
          inputMethodTextChangedEvent("쇼", ""),
          inputMethodTextChangedEvent("", "ㅛ")
        ),
        expectedDocumentText = expectedDocumentText,
        expectedComposedText = "ㅛ",
        expectedComposedTextInlayOffset = expectedDocumentText.length,
      )
    }
  }

  private suspend fun dispatchEventsAndAssert(
    events: List<AWTEvent>,
    expectedDocumentText: String,
    expectedComposedText: String,
    expectedComposedTextInlayOffset: Int,
  ) {
    withContext(Dispatchers.EDT) {
      for (event in events) {
        editor.contentComponent.dispatchEvent(event)
      }
    }
    echoer.await()
    // check on the next EDT tick to let `TerminalOutputEditorInputMethodSupport` process the cursor position changes
    withContext(Dispatchers.EDT) {
      assertEquals(expectedDocumentText, editor.document.text)
      val allInlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
      assertEquals(1, allInlays.size)
      val inlay = allInlays.first()
      assertEquals(expectedComposedTextInlayOffset, inlay.offset)
      val renderer = assertInstanceOf<InputMethodInlayRenderer>(inlay.renderer)
      assertEquals(expectedComposedText, renderer.text)
    }
  }

  private fun inputMethodTextChangedEvent(committedText: String, composedText: String): InputMethodEvent {
    return InputMethodEvent(
      editor.contentComponent,
      InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
      AttributedString(committedText + composedText).iterator,
      committedText.length,
      TextHitInfo.afterOffset(0),
      null
    )
  }

  private fun <T> timeoutRunBlockingInBackground(action: suspend CoroutineScope.() -> T) {
    timeoutRunBlocking(timeout = 30.seconds, context = Dispatchers.Default) {
      val coroutineScope = childScope("CoroutineScope for $qualifiedTestMethodName")
      init(coroutineScope)
      action()
      coroutineScope.cancel() // stop collecting the cursor position flow by `TerminalOutputEditorInputMethodSupport`
    }
  }

}

private class InputTextEchoer(val outputModel: TerminalOutputModelImpl, val coroutineScope: CoroutineScope) {

  private val lineBuffer: StringBuilder = StringBuilder()
  private val jobs: MutableList<Job> = CopyOnWriteArrayList()

  @RequiresEdt
  fun echo(textChunk: String) {
    check(!textChunk.contains('\n'))
    lineBuffer.append(textChunk)
    val line = lineBuffer.toString()
    val job = coroutineScope.launch(Dispatchers.EDT) {
      delay(10.milliseconds) // emulate delay of the round-trip
      outputModel.updateContent(0, line, emptyList())
      delay(10.milliseconds)
      outputModel.updateCursorPosition(0, line.length)
    }
    jobs.add(job)
  }

  suspend fun await() {
    for (job in jobs) {
      job.join()
      jobs.remove(job)
    }
  }
}
