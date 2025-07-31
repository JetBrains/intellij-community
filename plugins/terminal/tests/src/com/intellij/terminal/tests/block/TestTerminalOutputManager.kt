// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.ExperimentalUI
import org.jetbrains.plugins.terminal.block.output.*
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.junit.Assert

internal class TestTerminalOutputManager(project: Project, parentDisposable: Disposable) {
  private val editor: EditorEx = createEditor(project, parentDisposable)
  private val outputModel: TerminalOutputModel = TerminalOutputModelImpl(editor)

  init {
    editor.highlighter = TerminalTextHighlighter(outputModel)
  }

  val terminalOutputHighlighter: TerminalTextHighlighter
    get() = editor.highlighter as TerminalTextHighlighter

  val document: DocumentEx
    get() = editor.document

  fun createBlock(command: String?, output: TextWithHighlightings): Pair<CommandBlock, TextWithHighlightings> {
    val lastBlockEndOffset = outputModel.blocks.lastOrNull()?.endOffset ?: 0
    Assert.assertEquals(lastBlockEndOffset, document.textLength)
    // Terminal width is important only when there is a right prompt
    val block = outputModel.createBlock(command, null, terminalWidth = 80)
    if (output.text.isNotEmpty()) {
      val promptAndCommandHighlightings = outputModel.getHighlightings(block)
      val outputHighlightings = output.highlightings.map {
        HighlightingInfo(it.startOffset + block.outputStartOffset, it.endOffset + block.outputStartOffset, it.textAttributesProvider)
      }
      val prefix = "\n".takeIf { block.withPrompt || block.withCommand }.orEmpty()
      outputModel.putHighlightings(block, promptAndCommandHighlightings + outputHighlightings)
      editor.document.replaceString(block.outputStartOffset - prefix.length, block.endOffset, prefix + output.text)
    }
    outputModel.trimOutput()
    outputModel.finalizeBlock(block)
    return block to TextWithHighlightings(document.getText(block.textRange), outputModel.getHighlightings(block))
  }

  companion object {
    private fun createEditor(project: Project, parentDisposable: Disposable): EditorEx {
      ExperimentalUI.isNewUI() // `ExperimentalUI` runs `NewUiValue.initialize` in its static init
      val document = DocumentImpl("", true)
      val editor = TerminalUiUtils.createOutputEditor(document, project, JBTerminalSystemSettingsProviderBase(), installContextMenu = true)
      Disposer.register(parentDisposable) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
      return editor
    }
  }
}
