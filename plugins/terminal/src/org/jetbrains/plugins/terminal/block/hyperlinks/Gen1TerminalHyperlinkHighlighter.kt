// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.hyperlinks

import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.execution.impl.ExpirableTokenProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.block.output.CommandBlock
import org.jetbrains.plugins.terminal.block.output.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.output.withOutput
import org.jetbrains.plugins.terminal.util.terminalProjectScope

internal class Gen1TerminalHyperlinkHighlighter(project: Project,
                                                private val outputModel: TerminalOutputModel,
                                                parentDisposable: Disposable) {

  private val filterWrapper: CompositeFilterWrapper = CompositeFilterWrapper(project, createCoroutineScope(project, parentDisposable))

  private var lastUpdatedBlockInfo: Pair<CommandBlock, ExpirableTokenProvider>? = null

  private val editor: EditorEx
    get() = outputModel.editor

  private val document: DocumentEx
    get() = outputModel.editor.document

  private val hyperlinkSupport: EditorHyperlinkSupport
    get() = EditorHyperlinkSupport.get(editor)

  init {
    filterWrapper.addFiltersUpdatedListener { rehighlightAll() }
  }

  private fun rehighlightAll() {
    for (block in outputModel.blocks) {
      highlightHyperlinks(block)
    }
  }

  @RequiresEdt
  fun highlightHyperlinks(block: CommandBlock) {
    val filter = filterWrapper.getFilter() ?: return // if null, `rehighlightAll` will follow
    lastUpdatedBlockInfo?.let {
      if (it.first == block) {
        it.second.invalidateAll() // stop the previous highlighting of the same block
      }
    }
    val expirableTokenProvider = ExpirableTokenProvider()
    lastUpdatedBlockInfo = block to expirableTokenProvider

    hyperlinkSupport.clearHyperlinks(block.outputStartOffset, block.endOffset)

    if (block.withOutput) {
      val startLine = document.getLineNumber(block.outputStartOffset)
      val endLine = document.getLineNumber(block.endOffset)
      hyperlinkSupport.highlightHyperlinksLater(filter, startLine, endLine, expirableTokenProvider.createExpirable())
    }
  }

  companion object {
    private fun createCoroutineScope(project: Project, disposable: Disposable): CoroutineScope {
      return terminalProjectScope(project).childScope(Gen1TerminalHyperlinkHighlighter::class.java.simpleName).also {
        Disposer.register(disposable) {
          it.cancel()
        }
      }
    }
  }
}
