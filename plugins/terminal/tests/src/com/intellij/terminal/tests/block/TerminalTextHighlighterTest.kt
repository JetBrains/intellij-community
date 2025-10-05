// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.*
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TextAttributesProvider
import org.jetbrains.plugins.terminal.block.output.TextWithHighlightings
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.plainAttributesProvider
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
internal class TerminalTextHighlighterTest {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule: DisposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain = RuleChain(EdtRule(), projectRule, disposableRule)

  @Test
  fun `editor highlighter finds proper initial range`() {
    val outputManager = TestTerminalOutputManager(projectRule.project, disposableRule.disposable)
    outputManager.createBlock("echo foo", TextWithHighlightings("foo bar baz",
                                                            listOf(HighlightingInfo(1, 2, green()),
                                                                   HighlightingInfo(5, 6, yellow()))))
    checkHighlighter(outputManager, listOf(TextRange(0, 8),   // 'echo foo'
                                           TextRange(8, 10),  // '\nf'
                                           TextRange(10, 11), // 'o'
                                           TextRange(11, 14), // 'o b'
                                           TextRange(14, 15), // 'a'
                                           TextRange(15, outputManager.document.textLength)))  // 'r baz'
  }

  private fun checkHighlighter(outputManager: TestTerminalOutputManager, ranges: List<TextRange>) {
    for (documentOffset in 0 until outputManager.document.textLength) {
      val iterator = outputManager.terminalOutputHighlighter.createIterator(documentOffset)
      Assert.assertTrue(!iterator.atEnd())
      val expectedTextRange = ranges.find { it.contains(documentOffset) }
      Assert.assertNotNull(expectedTextRange)
      Assert.assertEquals(expectedTextRange, TextRange(iterator.start, iterator.end))
    }
  }

  companion object {
    fun green(): TextAttributesProvider = plainAttributesProvider(TerminalUiUtils.GREEN_COLOR_INDEX, BlockTerminalColorPalette())
    fun yellow(): TextAttributesProvider = plainAttributesProvider(TerminalUiUtils.YELLOW_COLOR_INDEX, BlockTerminalColorPalette())
  }
}
