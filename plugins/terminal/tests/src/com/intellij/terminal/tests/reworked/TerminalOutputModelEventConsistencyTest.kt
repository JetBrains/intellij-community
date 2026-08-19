// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.outputPattern
import com.intellij.terminal.tests.reworked.util.updateContent
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asDisposable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalOutputModelEventConsistencyTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `first content update`() = consistencyTest { sut ->
    sut.updateContent(0L, outputPattern("text\n"))
  }

  @Test
  fun `several content updates`() = consistencyTest { sut ->
    sut.updateContent(0L, outputPattern("text1\n"))
    sut.updateContent(1L, outputPattern("text2\n"))
  }

  @Test
  fun `automatically adding empty lines on content update`() = consistencyTest { sut ->
    sut.updateContent(0L, outputPattern("text1\n"))
    sut.updateContent(999L, outputPattern("text2\n"))
  }

  @Test
  fun `automatically adding empty lines on cursor update`() = consistencyTest { sut ->
    sut.updateContent(0L, outputPattern("text1\n"))
    sut.updateCursorPosition(999L, 0)
  }

  @Test
  fun `automatically adding spaces on cursor update`() = consistencyTest { sut ->
    sut.updateContent(0L, outputPattern("text1\n"))
    sut.updateCursorPosition(999L, 100500)
  }

  @Test
  fun `trimming - no highlightings`() = consistencyTest(maxLength = 5) { sut ->
    sut.updateContent(0L, outputPattern("01234"))
    sut.updateContent(0L, outputPattern("0123456789"))
  }

  @Test
  fun `trimming - with highlightings`() = consistencyTest(maxLength = 5) { sut ->
    sut.updateContent(0L, outputPattern("<s1>01234</s1>"))
    sut.updateContent(0L, outputPattern("01234<s1>56789</s1>"))
    sut.updateContent(3L, outputPattern("<s1>a</s1>"))
  }

  @Test
  fun `clear - with highlightings`() = consistencyTest { sut ->
    sut.updateContent(0L, outputPattern("01"))
    sut.updateContent(1L, outputPattern("<s1>23</s1>"))
    sut.updateContent(0L, outputPattern(""))
  }

  @Test
  fun `trim then clear - with highlightings`() = consistencyTest(maxLength = 10) { sut ->
    sut.updateContent(0L, outputPattern("01"))
    sut.updateContent(1L, outputPattern("<s1>23</s1>"))
    sut.updateContent(2L, outputPattern("4<s1>5</s1>"))
    sut.updateContent(3L, outputPattern("<s1>6</s1>7"))
    sut.updateContent(4L, outputPattern("<s1>89</s1>"))
    sut.updateContent(0L, outputPattern(""))
  }

  private inline fun consistencyTest(maxLength: Int = 0, crossinline block: (MutableTerminalOutputModel) -> Unit): Unit = timeoutRunBlocking(
    DEFAULT_TEST_TIMEOUT,
    "TerminalOutputModelEventConsistencyTest"
  ) {
    val sut = TerminalTestUtil.createOutputModel(maxLength)
    val mirror = StringBuilder()
    var trimmed = 0L
    sut.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        assertEmptyHighlightings(sut.getHighlightings(), event.document.textLength)
      }
    }, asDisposable())
    sut.addListener(asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (event.isTrimming) {
          assertThat(event.offset).isLessThan(sut.startOffset)
          assertThat(sut.startOffset - event.offset).isEqualTo(event.oldText.length.toLong())
          mirror.delete(0, event.oldText.length)
          trimmed += event.oldText.length
        }
        else {
          assertThat(event.offset).isBetween(sut.startOffset, sut.endOffset)
          val affectedLine = sut.getLineByOffset(event.offset)
          assertThat(affectedLine).isBetween(sut.firstLineIndex, sut.lastLineIndex)
          if (event.offset == TerminalOffset.ZERO && sut.textLength == 0) { // clear
            trimmed = 0
          }
          val startRelativeIndex = (event.offset.toAbsolute() - trimmed).toInt()
          mirror.replace(startRelativeIndex, startRelativeIndex + event.oldText.length, event.newText.toString())
        }
        assertConsistency(sut)
      }
    })
    block(sut)
    assertThat(mirror.toString()).isEqualTo(sut.getText(sut.startOffset, sut.endOffset).toString())
  }
}

private fun assertConsistency(sut: TerminalOutputModel) {
  assertThat(sut.cursorOffset).isBetween(sut.startOffset, sut.endOffset)
  val highlightings = sut.getHighlightings()
  for (i in 0 until highlightings.size) {
    val highlighting = highlightings[i]
    assertThat(highlighting.startOffset).isBetween(0, sut.textLength)
    assertThat(highlighting.endOffset).isBetween(0, sut.textLength)
  }
}

private fun assertEmptyHighlightings(highlightings: TerminalOutputHighlightingsSnapshot, textLength: Int) {
  if (textLength == 0) {
    assertThat(highlightings.size).isZero()
    return
  }
  assertThat(highlightings.size).isOne()
  val highlighting = highlightings[0]
  assertThat(highlighting.startOffset).isEqualTo(0)
  assertThat(highlighting.endOffset).isEqualTo(textLength)
  assertThat(highlighting.textAttributesProvider.getTextAttributes()).isEqualTo(TextAttributes.ERASE_MARKER)
}
