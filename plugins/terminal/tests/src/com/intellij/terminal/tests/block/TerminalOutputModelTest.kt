// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.tests.block.TerminalTextHighlighterTest.Companion.green
import com.intellij.testFramework.*
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TextWithHighlightings
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.NEW_TERMINAL_OUTPUT_CAPACITY_KB
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
internal class TerminalOutputModelTest {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule: DisposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain = RuleChain(EdtRule(), projectRule, disposableRule)

  @Test
  fun `trim top block output when editor max capacity reached`() {
    checkOutputTrimming(listOf(TextWithHighlightings("foo".repeat(200), listOf(HighlightingInfo(1, 2, green()))),
                               TextWithHighlightings("bar".repeat(200), listOf(HighlightingInfo(1, 2, green())))))
  }

  @Test
  fun `remove several top blocks when trimming output`() {
    val firstCharHighlighting = listOf(HighlightingInfo(0, 1, green()))
    val outputs = (0..1000).map {
      TextWithHighlightings(it.toString(), firstCharHighlighting)
    } + (0..100).map {
      TextWithHighlightings(it.toString().repeat(100), firstCharHighlighting)
    } + TextWithHighlightings("a".repeat(2000), firstCharHighlighting)

    checkOutputTrimming(outputs, 1)
  }

  private fun checkOutputTrimming(outputs: List<TextWithHighlightings>, outputCapacityKB: Int = 1) {
    val outputManager = TestTerminalOutputManager(projectRule.project, disposableRule.disposable)
    setNewTerminalOutputCapacityKB(outputCapacityKB)
    Assert.assertEquals("", outputManager.document.text)
    val addedOutputs = mutableListOf<TextWithHighlightings>()
    for (output in outputs) {
      outputManager.createBlock(null, output)
      addedOutputs.add(output)
      val expectedOutput = addedOutputs.joinToString("\n") { it.text }.takeLast(outputCapacityKB * 1024).removePrefix("\n")
      Assert.assertEquals(expectedOutput, outputManager.document.text)
    }
  }

  private fun setNewTerminalOutputCapacityKB(@Suppress("SameParameterValue") newTerminalOutputCapacityKB: Int) {
    val prevValue = AdvancedSettings.getInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB)
    AdvancedSettings.setInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB, newTerminalOutputCapacityKB)
    Disposer.register(disposableRule.disposable) {
      AdvancedSettings.setInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB, prevValue)
    }
  }
}
