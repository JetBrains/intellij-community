// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.tests.reworked.util.TerminalOutputPattern
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.assertMatches
import com.intellij.terminal.tests.reworked.util.outputPattern
import com.intellij.terminal.tests.reworked.util.replaceContent
import com.intellij.terminal.tests.reworked.util.updateContent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalOutputModelReplacementTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `insert into empty - no highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>"),
    replacementOffset = 0,
    replacementLength = 0,
    replacementPattern = outputPattern("12345"),
    expectedPattern = outputPattern("<cursor>12345"),
  )

  @Test
  fun `insert into empty - with highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>"),
    replacementOffset = 0,
    replacementLength = 0,
    replacementPattern = outputPattern("<s1>12</s1>345"),
    expectedPattern = outputPattern("<cursor><s1>12</s1>345"),
  )

  @Test
  fun `insert at end - no highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n"),
    replacementOffset = 6,
    replacementLength = 0,
    replacementPattern = outputPattern("abcd\n"),
    expectedPattern = outputPattern("<cursor>12345\nabcd\n"),
  )

  @Test
  fun `insert at end - with highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n"),
    replacementOffset = 6,
    replacementLength = 0,
    replacementPattern = outputPattern("<s1>ab</s1>cd\n"),
    expectedPattern = outputPattern("<cursor>12345\n<s1>ab</s1>cd\n"),
  )

  @Test
  fun `insert at start - no highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n"),
    replacementOffset = 0,
    replacementLength = 0,
    replacementPattern = outputPattern("abcd\n"),
    expectedPattern = outputPattern("<cursor>abcd\n12345\n"),
  )

  @Test
  fun `insert at start - with new highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n"),
    replacementOffset = 0,
    replacementLength = 0,
    replacementPattern = outputPattern("<s1>ab</s1>cd\n"),
    expectedPattern = outputPattern("<cursor><s1>ab</s1>cd\n12345\n"),
  )

  @Test
  fun `insert at start - with existing highlighting right after`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12</s1>345\n"),
    replacementOffset = 0,
    replacementLength = 0,
    replacementPattern = outputPattern("abcd\n"),
    expectedPattern = outputPattern("<cursor>abcd\n<s1>12</s1>345\n"),
  )

  @Test
  fun `insert at start - with existing highlighting further away`() = testReplace(
    patternBefore = outputPattern("<cursor>1<s1>23</s1>45\n"),
    replacementOffset = 0,
    replacementLength = 0,
    replacementPattern = outputPattern("abcd\n"),
    expectedPattern = outputPattern("<cursor>abcd\n1<s1>23</s1>45\n"),
  )

  @Test
  fun `insert in the middle - no highlightings`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n\nabcd\n"),
    replacementOffset = 6,
    replacementLength = 0,
    replacementPattern = outputPattern("qwer"),
    expectedPattern = outputPattern("<cursor>12345\nqwer\nabcd\n"),
  )

  @Test
  fun `insert in the middle - with existing highlightings around`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12345\n</s1><s2>abcd\n</s2>"),
    replacementOffset = 6,
    replacementLength = 0,
    replacementPattern = outputPattern("qwer\n"),
    expectedPattern = outputPattern("<cursor><s1>12345\n</s1>qwer\n<s2>abcd\n</s2>"),
  )

  @Test
  fun `insert in the middle - inside existing highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12345\nabcd\n</s1>"),
    replacementOffset = 6,
    replacementLength = 0,
    replacementPattern = outputPattern("qwer\n"),
    expectedPattern = outputPattern("<cursor><s1>12345\n</s1>qwer\n<s1>abcd\n</s1>"),
  )

  @Test
  fun `insert in the middle - inside existing highlighting with adjacent highlightings around`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>1234</s1><s2>5\nabc</s2>d\n"),
    replacementOffset = 6,
    replacementLength = 0,
    replacementPattern = outputPattern("qwer\n"),
    expectedPattern = outputPattern("<cursor><s1>1234</s1><s2>5\n</s2>qwer\n<s2>abc</s2>d\n"),
  )

  @Test
  fun `insert in the middle - inside existing highlighting with non-adjacent highlightings around`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>123</s1>4<s2>5\nab</s2>cd\n"),
    replacementOffset = 6,
    replacementLength = 0,
    replacementPattern = outputPattern("qwer\n"),
    expectedPattern = outputPattern("<cursor><s1>123</s1>4<s2>5\n</s2>qwer\n<s2>ab</s2>cd\n"),
  )

  @Test
  fun `delete at start - no highlightings`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\nabcd\n"),
    replacementOffset = 0,
    replacementLength = 6,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>abcd\n"),
  )

  @Test
  fun `delete at start - whole style range`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12345\n</s1>abcd\n"),
    replacementOffset = 0,
    replacementLength = 6,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>abcd\n"),
  )

  @Test
  fun `delete at start - end of the deleted region covered by a style range`() = testReplace(
    patternBefore = outputPattern("<cursor>1<s1>2345\n</s1>abcd\n"),
    replacementOffset = 0,
    replacementLength = 6,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>abcd\n"),
  )

  @Test
  fun `delete at start - start of the deleted region covered by a style range`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>1234</s1>5\nabcd\n"),
    replacementOffset = 0,
    replacementLength = 6,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>abcd\n"),
  )

  @Test
  fun `delete at start - style range extends beyond`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12345\na</s1>bcd\n"),
    replacementOffset = 0,
    replacementLength = 6,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor><s1>a</s1>bcd\n"),
  )

  @Test
  fun `delete at end - whole style range`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n<s1>abcd\n</s1>"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>12345\n"),
  )

  @Test
  fun `delete at end - end of the deleted region covered by a style range`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\na<s1>bcd\n</s1>"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>12345\n"),
  )

  @Test
  fun `delete at end - start of the deleted region covered by a style range`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n<s1>abc</s1>d\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>12345\n"),
  )

  @Test
  fun `delete at end - style range starts before the deleted region`() = testReplace(
    patternBefore = outputPattern("<cursor>123<s1>45\nabc</s1>d\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>123<s1>45\n</s1>"),
  )

  @Test
  fun `delete in the middle - whole style range with adjacent highlightings`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12345\n</s1><s2>abcd</s2>\n<s1>qwer\n</s1>"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor><s1>12345\n</s1><s1>qwer\n</s1>"),
  )

  @Test
  fun `delete in the middle - whole style range without adjacent highlightings`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12</s1>345\n<s2>abcd</s2>\nq<s1>wer\n</s1>"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor><s1>12</s1>345\nq<s1>wer\n</s1>"),
  )

  @Test
  fun `delete in the middle - the end of a style range`() = testReplace(
    patternBefore = outputPattern("<cursor>123<s2>45\nabcd\n</s2>qwer\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>123<s2>45\n</s2>qwer\n"),
  )

  @Test
  fun `delete in the middle - the beginning of a style range`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n<s2>abcd\nq</s2>wer\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern(""),
    expectedPattern = outputPattern("<cursor>12345\n<s2>q</s2>wer\n"),
  )

  @Test
  fun `replace at start - no highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n"),
    replacementOffset = 0,
    replacementLength = 2,
    replacementPattern = outputPattern("abc"),
    expectedPattern = outputPattern("<cursor>abc345\n"),
  )

  @Test
  fun `replace at start - insert highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n"),
    replacementOffset = 0,
    replacementLength = 2,
    replacementPattern = outputPattern("<s1>abc</s1>"),
    expectedPattern = outputPattern("<cursor><s1>abc</s1>345\n"),
  )

  @Test
  fun `replace at start - replace highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12</s1>345\n"),
    replacementOffset = 0,
    replacementLength = 2,
    replacementPattern = outputPattern("<s2>abc</s2>"),
    expectedPattern = outputPattern("<cursor><s2>abc</s2>345\n"),
  )

  @Test
  fun `replace at start - partially replace highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>123</s1>45\n"),
    replacementOffset = 0,
    replacementLength = 2,
    replacementPattern = outputPattern("<s2>abc</s2>"),
    expectedPattern = outputPattern("<cursor><s2>abc</s2><s1>3</s1>45\n"),
  )

  @Test
  fun `replace at start - remove highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor><s1>12</s1>345\n"),
    replacementOffset = 0,
    replacementLength = 2,
    replacementPattern = outputPattern("abc"),
    expectedPattern = outputPattern("<cursor>abc345\n"),
  )

  @Test
  fun `replace at end - no highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n"),
    replacementOffset = 3,
    replacementLength = 3,
    replacementPattern = outputPattern("abc\n"),
    expectedPattern = outputPattern("<cursor>123abc\n"),
  )

  @Test
  fun `replace at end - insert highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n"),
    replacementOffset = 3,
    replacementLength = 3,
    replacementPattern = outputPattern("<s1>abc\n</s1>"),
    expectedPattern = outputPattern("<cursor>123<s1>abc\n</s1>"),
  )

  @Test
  fun `replace at end - replace highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>123<s1>45\n</s1>"),
    replacementOffset = 3,
    replacementLength = 3,
    replacementPattern = outputPattern("<s2>abc</s2>"),
    expectedPattern = outputPattern("<cursor>123<s2>abc</s2>"),
  )

  @Test
  fun `replace at end - partially replace highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12<s1>345\n</s1>"),
    replacementOffset = 3,
    replacementLength = 3,
    replacementPattern = outputPattern("<s2>abc</s2>"),
    expectedPattern = outputPattern("<cursor>12<s1>3</s1><s2>abc</s2>"),
  )

  @Test
  fun `replace at end - remove highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>123<s1>45\n</s1>"),
    replacementOffset = 3,
    replacementLength = 3,
    replacementPattern = outputPattern("abc"),
    expectedPattern = outputPattern("<cursor>123abc"),
  )

  @Test
  fun `replace in the middle - no highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\nabcd\nqwer\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern("xyz\n"),
    expectedPattern = outputPattern("<cursor>12345\nxyz\nqwer\n"),
  )

  @Test
  fun `replace in the middle - insert highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\nabcd\nqwer\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern("<s1>xyz\n</s1>"),
    expectedPattern = outputPattern("<cursor>12345\n<s1>xyz\n</s1>qwer\n"),
  )

  @Test
  fun `replace in the middle - replace highlighting`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n<s1>abcd\n</s1>qwer\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern("<s2>xyz\n</s2>"),
    expectedPattern = outputPattern("<cursor>12345\n<s2>xyz\n</s2>qwer\n"),
  )

  @Test
  fun `replace in the middle - replace the end of a highlighting region`() = testReplace(
    patternBefore = outputPattern("<cursor>1234<s1>5\nabcd\n</s1>qwer\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern("<s2>xyz\n</s2>"),
    expectedPattern = outputPattern("<cursor>1234<s1>5\n</s1><s2>xyz\n</s2>qwer\n"),
  )

  @Test
  fun `replace in the middle - replace the start of a highlighting region`() = testReplace(
    patternBefore = outputPattern("<cursor>12345\n<s1>abcd\nq</s1>wer\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern("<s2>xyz\n</s2>"),
    expectedPattern = outputPattern("<cursor>12345\n<s2>xyz\n</s2><s1>q</s1>wer\n"),
  )

  @Test
  fun `replace in the middle - replace the middle of a highlighting region`() = testReplace(
    patternBefore = outputPattern("<cursor>1234<s1>5\nabcd\nq</s1>wer\n"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern("<s2>xyz\n</s2>"),
    expectedPattern = outputPattern("<cursor>1234<s1>5\n</s1><s2>xyz\n</s2><s1>q</s1>wer\n"),
  )

  @Test
  fun `replace in the middle - replace the middle of a highlighting region with adjacent highlightings`() = testReplace(
    patternBefore = outputPattern("<cursor><s3>1234</s3><s1>5\nabcd\nq</s1><s4>wer\n</s4>"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern("<s2>xyz\n</s2>"),
    expectedPattern = outputPattern("<cursor><s3>1234</s3><s1>5\n</s1><s2>xyz\n</s2><s1>q</s1><s4>wer\n</s4>"),
  )

  @Test
  fun `replace in the middle - replace the middle of a highlighting region with non-adjacent highlightings`() = testReplace(
    patternBefore = outputPattern("<cursor><s3>123</s3>4<s1>5\nabcd\nq</s1>w<s4>er\n</s4>"),
    replacementOffset = 6,
    replacementLength = 5,
    replacementPattern = outputPattern("<s2>xyz\n</s2>"),
    expectedPattern = outputPattern("<cursor><s3>123</s3>4<s1>5\n</s1><s2>xyz\n</s2><s1>q</s1>w<s4>er\n</s4>"),
  )

  /**
   * Establishes [patternBefore], then replaces [replacementLength] characters starting at
   * [replacementOffset] with [replacementPattern], and asserts the model matches [expectedPattern].
   */
  private fun testReplace(
    patternBefore: TerminalOutputPattern,
    replacementOffset: Int,
    replacementLength: Int,
    replacementPattern: TerminalOutputPattern,
    expectedPattern: TerminalOutputPattern,
  ) {
    runBlocking(Dispatchers.EDT) {
      val model = TerminalTestUtil.createOutputModel()
      model.updateContent(0, patternBefore)
      // We assert on both the initial content and changed content
      // because there was a bug when the first access caused the value to be cached,
      // and then the second call would return the (wrong) cached value.
      // Without the first access, the test would pass even though there's a serious bug.
      model.assertMatches(patternBefore)
      model.replaceContent(TerminalOffset.of(replacementOffset.toLong()), replacementLength, replacementPattern)
      model.assertMatches(expectedPattern)
    }
  }
}