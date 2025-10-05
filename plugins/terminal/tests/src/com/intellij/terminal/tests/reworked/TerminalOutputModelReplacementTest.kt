// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.replace
import com.intellij.terminal.tests.reworked.util.parseTextWithReplacement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.output.EmptyTextAttributesProvider
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalOutputModelReplacementTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `insert into empty - no highlighting`() =
    testReplace("<|12345>")

  @Test
  fun `insert into empty - with highlighting`() =
    testReplace("<|[12](1)345>")

  @Test
  fun `insert at end - no highlighting`() =
    testReplace("12345\n<|abcd\n>")

  @Test
  fun `insert at end - with highlighting`() =
    testReplace("12345\n<|[ab](1)cd\n>")

  @Test
  fun `insert at start - no highlighting`() =
    testReplace("<|abcd\n>12345\n")

  @Test
  fun `insert at start - with new highlighting`() =
    testReplace("<|[ab](1)cd\n>12345\n")

  @Test
  fun `insert at start - with existing highlighting right after`() =
    testReplace("<|abcd\n>[12](1)345\n")

  @Test
  fun `insert at start - with existing highlighting further away`() =
    testReplace("<|abcd\n>1[23](1)45\n")

  @Test
  fun `insert in the middle - no highlightings`() =
    testReplace("12345\n<|qwer>\nabcd\n")

  @Test
  fun `insert in the middle - with existing highlightings around`() =
    testReplace("[12345\n](1)<|qwer\n>[abcd\n](2)")

  @Test
  fun `insert in the middle - inside existing highlighting`() =
    testReplace("[12345\n<|qwer\n>abcd\n](1)")

  @Test
  fun `insert in the middle - inside existing highlighting with adjacent highlightings around`() =
    testReplace("[1234](1)[5\n<|qwer\n>abc](2)d\n")

  @Test
  fun `insert in the middle - inside existing highlighting with non-adjacent highlightings around`() =
    testReplace("[123](1)4[5\n<|qwer\n>ab](2)cd\n")

  @Test
  fun `delete at start - no highlightings`() =
    testReplace("<12345\n|>abcd\n")

  @Test
  fun `delete at start - whole style range`() =
    testReplace("<[12345\n](1)|>abcd\n")

  @Test
  fun `delete at start - end of the deleted region covered by a style range`() =
    testReplace("<1[2345\n](1)|>abcd\n")

  @Test
  fun `delete at start - start of the deleted region covered by a style range`() =
    testReplace("<[1234](1)5\n|>abcd\n")

  @Test
  fun `delete at start - style range extends beyond`() =
    testReplace("<[12345\n|>a](1)bcd\n")

  @Test
  fun `delete at end - whole style range`() =
    testReplace("12345\n<[abcd\n](1)|>")

  @Test
  fun `delete at end - end of the deleted region covered by a style range`() =
    testReplace("12345\n<a[bcd\n](1)|>")

  @Test
  fun `delete at end - start of the deleted region covered by a style range`() =
    testReplace("12345\n<[abc](1)d\n|>")

  @Test
  fun `delete at end - style range starts before the deleted region`() =
    testReplace("123[45\n<abc](1)d\n|>")

  @Test
  fun `delete in the middle - whole style range with adjacent highlightings`() =
    testReplace("[12345\n](1)<[abcd](2)\n|>[qwer\n](1)")

  @Test
  fun `delete in the middle - whole style range without adjacent highlightings`() =
    testReplace("[12](1)345\n<[abcd](2)\n|>q[wer\n](1)")

  @Test
  fun `delete in the middle - the end of a style range`() =
    testReplace("123[45\n<abcd\n](2)|>qwer\n")

  @Test
  fun `delete in the middle - the beginning of a style range`() =
    testReplace("12345\n<[abcd\n|>q](2)wer\n")

  @Test
  fun `replace at start - no highlighting`() =
    testReplace("<12|abc>345\n")

  @Test
  fun `replace at start - insert highlighting`() =
    testReplace("<12|[abc](1)>345\n")

  @Test
  fun `replace at start - replace highlighting`() =
    testReplace("<[12](1)|[abc](2)>345\n")

  @Test
  fun `replace at start - partially replace highlighting`() =
    testReplace("<[12|[abc](2)>3](1)45\n")

  @Test
  fun `replace at start - remove highlighting`() =
    testReplace("<[12](1)|abc>345\n")

  @Test
  fun `replace at end - no highlighting`() =
    testReplace("123<45\n|abc\n>")

  @Test
  fun `replace at end - insert highlighting`() =
    testReplace("123<45\n|[abc\n](1)>")

  @Test
  fun `replace at end - replace highlighting`() =
    testReplace("123<[45\n](1)|[abc](2)>")

  @Test
  fun `replace at end - partially replace highlighting`() =
    testReplace("12[3<45\n](1)|[abc](2)>")

  @Test
  fun `replace at end - remove highlighting`() =
    testReplace("123<[45\n](1)|abc>")

  @Test
  fun `replace in the middle - no highlighting`() =
    testReplace("12345\n<abcd\n|xyz\n>qwer\n")

  @Test
  fun `replace in the middle - insert highlighting`() =
    testReplace("12345\n<abcd\n|[xyz\n](1)>qwer\n")

  @Test
  fun `replace in the middle - replace highlighting`() =
    testReplace("12345\n<[abcd\n](1)|[xyz\n](2)>qwer\n")

  @Test
  fun `replace in the middle - replace the end of a highlighting region`() =
    testReplace("1234[5\n<abcd\n](1)|[xyz\n](2)>qwer\n")

  @Test
  fun `replace in the middle - replace the start of a highlighting region`() =
    testReplace("12345\n<[abcd\n|[xyz\n](2)>q](1)wer\n")

  @Test
  fun `replace in the middle - replace the middle of a highlighting region`() =
    testReplace("1234[5\n<abcd\n|[xyz\n](2)>q](1)wer\n")

  @Test
  fun `replace in the middle - replace the middle of a highlighting region with adjacent highlightings`() =
    testReplace("[1234](3)[5\n<abcd\n|[xyz\n](2)>q](1)[wer\n](4)")

  @Test
  fun `replace in the middle - replace the middle of a highlighting region with non-adjacent highlightings`() =
    testReplace("[123](3)4[5\n<abcd\n|[xyz\n](2)>q]w(1)[er\n](4)")
}

/**
 * Runs a single text replacement test.
 *
 * Argument syntax:
 *
 * - Style regions are indicated with Markdown-like syntax:
 * ```
 * abcd[ef](2)gh
 * ```
 * meaning that the "ef" part will have some highlighting style. There are different styles numbered from 1 to 4.
 * - The (only) replacement region is indicated with `<>`, where the texts "before" and "after" are separated with `|`, for example:
 * ```
 * ab<cd|ef>gh
 * ```
 * meaning "replace cd with ef."
 * - Style regions, excluding the "after" part, which has separate styles, may not be nested, but they can cross the `<>` boundaries, for example:
 * ```
 * [ab<cd|ef>gh](2)
 * ```
 * meaning that "before" will be `[abcdgh](2)` and "after" will be `[ab](2)ef[gh](2)` (because the replacement has no highlighting here).
 * - Style regions in the "after" part can have their own highlighting regions, for example:
 * ```
 * ab<c[d|[gh](2)>ef](1)
 * ```
 * meaning, in `abc[def](1)` replace "cd" with `[gh](2)`, so the end result will be `ab[gh](2)[ef](1)`.
 */
private fun testReplace(
  textWithReplacement: String,
) {
  runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    val textWithReplacement = parseTextWithReplacement(textWithReplacement)
    model.replace(0, 0, textWithReplacement.originalText.text, textWithReplacement.originalText.styles)
    model.replace(
      textWithReplacement.removedIndex,
      textWithReplacement.removedLength,
      textWithReplacement.insertedText.text,
      textWithReplacement.insertedText.styles,
    )
    assertText(model, textWithReplacement.modifiedText.text)
    assertHighlightings(
      model,
      textWithReplacement.modifiedText.styles.map {
        highlighting(it.startOffset.toInt(), it.endOffset.toInt(), it.style)
      }
    )
  }
}

private fun assertText(model: TerminalOutputModelImpl, expectedText: String) {
  assertEquals(expectedText, model.document.text)
}

private fun assertHighlightings(model: TerminalOutputModelImpl, expectedHighlightings: List<HighlightingInfo>) {
  val actualHighlightings = model.getHighlightings()
  assertActualHighlightingsCoverTheDocument(model, actualHighlightings)
  assertActualHighlightingsMatchExpected(model, expectedHighlightings, actualHighlightings)
}

private fun assertActualHighlightingsCoverTheDocument(model: TerminalOutputModelImpl, actualHighlightings: TerminalOutputHighlightingsSnapshot) {
  var previousEnd = 0
  val validRange = 0..model.document.textLength
  for (i in 0 until actualHighlightings.size) {
    val highlighting = actualHighlightings[i]
    assertThat(highlighting.startOffset).`as`("highlightings[$i].startOffset")
      .isBetween(validRange.first, validRange.last)
      .isEqualTo(previousEnd)
    assertThat(highlighting.endOffset).`as`("highlightings[$i].endOffset")
      .isBetween(validRange.first, validRange.last)
      .isGreaterThanOrEqualTo(highlighting.startOffset)
    previousEnd = highlighting.endOffset
  }
  assertThat(previousEnd).`as`("last end index").isEqualTo(model.document.textLength)
}

private fun assertActualHighlightingsMatchExpected(
  model: TerminalOutputModelImpl,
  expectedHighlightings: List<HighlightingInfo>,
  actualHighlightings: TerminalOutputHighlightingsSnapshot,
) {
  for (offset in 0 until model.document.textLength) {
    val expectedHighlighting = expectedHighlightings.getHighlightingAtOffset(offset)?.textAttributesProvider
    val actualHighlighting = actualHighlightings.getHighlightingAtOffset(offset)?.textAttributesProvider
    assertThat(actualHighlighting).`as`("highlighting at offset %d", offset).isEqualTo(expectedHighlighting)
  }
}

private fun List<HighlightingInfo>.getHighlightingAtOffset(offset: Int): HighlightingInfo? =
  filter { offset in it.startOffset until it.endOffset }
    .let {
      when (it.size) {
        0 -> null
        1 -> it.single()
        else -> throw AssertionError("Highlightings overlap: $it")
      }
    }

private fun TerminalOutputHighlightingsSnapshot.getHighlightingAtOffset(offset: Int): HighlightingInfo? =
  findHighlightingIndex(offset)
    .let { index -> if (index in 0 until size) get(index) else null }
    ?.takeIf { offset in it.startOffset until it.endOffset && it.textAttributesProvider !is EmptyTextAttributesProvider}
