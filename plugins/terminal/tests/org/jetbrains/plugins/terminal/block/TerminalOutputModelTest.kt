// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.*
import org.jetbrains.plugins.terminal.block.TerminalTextHighlighterTest.Companion.green
import org.jetbrains.plugins.terminal.exp.*
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TerminalOutputModelTest {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule: DisposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain = RuleChain(EdtRule(), projectRule, disposableRule)

  @Test
  fun `editor highlighter finds proper initial range`() {
    val outputManager = TestTerminalOutputManager(projectRule.project, disposableRule.disposable)
    val maxCapacity = 100
    setMaxOutputEditorTextLength(maxCapacity)
    val firstBlock = outputManager.createBlock("echo foo", TestCommandOutput("foo".repeat(20),
                                                                             listOf(HighlightingInfo(1, 2, green()))))
    val secondBlockStartOffset = firstBlock.endOffset
    val secondBlockHighlighting = HighlightingInfo(secondBlockStartOffset + 1, secondBlockStartOffset + 2, green())
    outputManager.createBlock("echo bar", TestCommandOutput("bar".repeat(20),
                                                            listOf(secondBlockHighlighting)))
    val expectedOutput = ("foo".repeat(20) + "\n" + "bar".repeat(20)).takeLast(maxCapacity)
    Assert.assertEquals(expectedOutput, outputManager.document.text)
  }

  private fun setMaxOutputEditorTextLength(@Suppress("SameParameterValue") maxTextLength: Int) {
    val prevValue = AdvancedSettings.getInt(TERMINAL_EDITOR_MAX_TEXT_LENGTH)
    AdvancedSettings.setInt(TERMINAL_EDITOR_MAX_TEXT_LENGTH, maxTextLength)
    Disposer.register(disposableRule.disposable) {
      AdvancedSettings.setInt(TERMINAL_EDITOR_MAX_TEXT_LENGTH, prevValue)
    }
  }

}