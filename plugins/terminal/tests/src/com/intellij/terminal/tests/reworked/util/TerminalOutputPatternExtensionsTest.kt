// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TerminalOutputPatternExtensionsTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `model matches plain text with cursor`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("hello<cursor> world"))
    model.assertMatches(outputPattern("hello<cursor> world"))
  }

  @Test
  fun `model matches text with styles and cursor`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("<s1>hello</s1> <s2>wor<cursor>ld</s2>"))
    model.assertMatches(outputPattern("<s1>hello</s1> <s2>wor<cursor>ld</s2>"))
  }

  @Test
  fun `model matches multiline with styles`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("<cursor><s1>aaa</s1>\n<s2>bbb</s2>"))
    model.assertMatches(outputPattern("<cursor><s1>aaa</s1>\n<s2>bbb</s2>"))
  }

  @Test
  fun `model does not match different text`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("<cursor>hello"))
    assertThat(model.matches(outputPattern("<cursor>world"))).isFalse()
  }

  @Test
  fun `model does not match different styles`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("<cursor><s1>hello</s1>"))
    assertThat(model.matches(outputPattern("<cursor><s2>hello</s2>"))).isFalse()
  }

  @Test
  fun `model does not match different cursor`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("ab<cursor>cde"))
    assertThat(model.matches(outputPattern("abc<cursor>de"))).isFalse()
  }

  @Test
  fun `model does not match when cursor not specified in pattern`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("<cursor>hello"))
    // Pattern without a cursor has cursorOffset=null. Model always has cursor -> mismatch
    assertThat(model.matches(outputPattern("hello"))).isFalse()
  }

  @Test
  fun `model toPattern round-trip`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    val expected = outputPattern("<s1>hello</s1> <s2>world</s2>\nfoo<cursor>bar")
    model.updateContent(0, expected)
    assertThat(model.toPattern()).isEqualTo(expected)
  }

  @Test
  fun `model matches text with link`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("hello <a href=\"https://example.com\">world</a><cursor>"))
    model.assertMatches(outputPattern("hello <a href=\"https://example.com\">world</a><cursor>"))
  }

  @Test
  fun `model does not match different link uri`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("<cursor><a href=\"https://a\">hello</a>"))
    assertThat(model.matches(outputPattern("<cursor><a href=\"https://b\">hello</a>"))).isFalse()
  }

  @Test
  fun `model toPattern round-trip with link`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    val expected = outputPattern("<s1>hi</s1> <a href=\"https://example.com\">there</a><cursor>!")
    model.updateContent(0, expected)
    assertThat(model.toPattern()).isEqualTo(expected)
  }

  @Test
  fun `replaceContent with a pattern containing a link throws`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("hello world"))
    assertThatThrownBy {
      model.replaceContent(model.startOffset, model.textLength, outputPattern("<a href=\"https://example.com\">goodbye</a>"))
    }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `model matches empty`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.assertMatches(outputPattern("<cursor>"))
  }

  @Test
  fun `model replaceContent with pattern`() = runOnEdt {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, outputPattern("hello<cursor> world"))
    model.replaceContent(model.startOffset, model.textLength, outputPattern("<s1>goodbye</s1>"))
    // cursor position is not updated by replaceContent
    model.assertMatches(outputPattern("<s1>goodb<cursor>ye</s1>"))
  }

  private fun runOnEdt(block: () -> Unit) {
    runBlocking(Dispatchers.EDT) { block() }
  }
}