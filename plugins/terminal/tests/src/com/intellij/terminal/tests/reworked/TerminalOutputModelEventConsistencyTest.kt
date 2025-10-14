// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asDisposable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalOutputModelEventConsistencyTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `first content update`() = consistencyTest { sut ->
    sut.updateContent(0L, "text\n", emptyList())
  }

  @Test
  fun `several content updates`() = consistencyTest { sut ->
    sut.updateContent(0L, "text1\n", emptyList())
    sut.updateContent(1L, "text2\n", emptyList())
  }

  @Test
  fun `automatically adding empty lines on content update`() = consistencyTest { sut ->
    sut.updateContent(0L, "text1\n", emptyList())
    sut.updateContent(999L, "text2\n", emptyList())
  }

  @Test
  fun `automatically adding empty lines on cursor update`() = consistencyTest { sut ->
    sut.updateContent(0L, "text1\n", emptyList())
    sut.updateCursorPosition(999L, 0)
  }

  @Test
  fun `automatically adding spaces on cursor update`() = consistencyTest { sut ->
    sut.updateContent(0L, "text1\n", emptyList())
    sut.updateCursorPosition(999L, 100500)
  }

  private inline fun consistencyTest(crossinline block: (MutableTerminalOutputModel) -> Unit): Unit = timeoutRunBlocking(
    DEFAULT_TEST_TIMEOUT,
    "TerminalOutputModelEventConsistencyTest"
  ) {
    val sut = TerminalTestUtil.createOutputModel()
    val mirror = StringBuilder()
    var trimmed = 0L
    sut.addListener(asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (event.isTrimming) {
          mirror.delete(0, event.oldText.length)
          trimmed += event.oldText.length
        }
        else {
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
