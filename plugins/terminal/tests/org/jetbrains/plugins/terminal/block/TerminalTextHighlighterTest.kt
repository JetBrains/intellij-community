// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.testFramework.*
import com.intellij.ui.ExperimentalUI
import org.jetbrains.plugins.terminal.exp.*
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TerminalTextHighlighterTest {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule: DisposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain = RuleChain(EdtRule(), projectRule, disposableRule)

  @Test
  fun `editor highlighter finds proper initial range`() {
    val editor = createEditor()
    Disposer.register(disposableRule.disposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    val outputModel = TerminalOutputModel(editor)
    editor.highlighter = TerminalTextHighlighter(outputModel)
    val block = outputModel.createBlock("echo foo", null)
    val output = TerminalOutputController.CommandOutput("foo bar baz",
                                                        listOf(HighlightingInfo(1, 2, green()),
                                                               HighlightingInfo(5, 6, red())))
    editor.document.insertString(0, output.text)
    outputModel.putHighlightings(block, output.highlightings)
    val textHighlighter = editor.highlighter as TerminalTextHighlighter
    checkHighlighter(textHighlighter, listOf(TextRange(0, 1),
                                             TextRange(1, 2),
                                             TextRange(2, 5),
                                             TextRange(5, 6)))
  }

  private fun checkHighlighter(highlighter: TerminalTextHighlighter, ranges: List<TextRange>) {
    for (documentOffset in 0 until ranges.last().endOffset) {
      val iterator = highlighter.createIterator(documentOffset)
      Assert.assertTrue(!iterator.atEnd())
      val expectedTextRange = ranges.find { it.startOffset <= documentOffset && documentOffset < it.endOffset }
      Assert.assertNotNull(expectedTextRange)
      Assert.assertEquals(expectedTextRange, TextRange(iterator.start, iterator.end))
    }
  }

  private fun createEditor(): EditorEx {
    ExperimentalUI.isNewUI() // `ExperimentalUI` runs `NewUiValue.initialize` in its static init
    val document = DocumentImpl("", true)
    return TerminalUiUtils.createOutputEditor(document, projectRule.project, JBTerminalSystemSettingsProviderBase())
  }

  companion object {
    fun green(): TextAttributes = ConsoleHighlighter.GREEN.defaultAttributes
    fun red(): TextAttributes = ConsoleHighlighter.RED.defaultAttributes
  }
}