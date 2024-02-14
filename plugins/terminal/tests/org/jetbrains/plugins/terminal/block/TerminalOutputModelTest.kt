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
  fun `trim top block output when editor max capacity reached`() {
    val outputManager = TestTerminalOutputManager(projectRule.project, disposableRule.disposable)
    val maxCapacityKB = 1
    setNewTerminalOutputCapacityKB(maxCapacityKB)
    val (_, firstBlockOutput) = outputManager.createBlock(
      "echo foo", TestCommandOutput("foo".repeat(200), listOf(HighlightingInfo(1, 2, green()))))
    val (_, secondBlockOutput) = outputManager.createBlock(
      "echo bar", TestCommandOutput("bar".repeat(200), listOf(HighlightingInfo(1, 2, green()))))
    val expectedOutput = (firstBlockOutput.text + "\n" + secondBlockOutput.text).takeLast(maxCapacityKB * 1024)
    Assert.assertEquals(expectedOutput, outputManager.document.text)
  }

  private fun setNewTerminalOutputCapacityKB(@Suppress("SameParameterValue") newTerminalOutputCapacityKB: Int) {
    val prevValue = AdvancedSettings.getInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB)
    AdvancedSettings.setInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB, newTerminalOutputCapacityKB)
    Disposer.register(disposableRule.disposable) {
      AdvancedSettings.setInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB, prevValue)
    }
  }
}