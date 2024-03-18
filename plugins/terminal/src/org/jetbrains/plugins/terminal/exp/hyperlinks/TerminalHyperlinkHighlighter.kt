// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.hyperlinks

import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.execution.impl.ExpirableTokenProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.FilteringProcessor
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.exp.CommandBlock
import org.jetbrains.plugins.terminal.exp.TerminalOutputModel

internal class TerminalHyperlinkHighlighter(project: Project,
                                            private val outputModel: TerminalOutputModel,
                                            parentDisposable: Disposable) {

  private val filterWrapper: CompositeFilterWrapper = CompositeFilterWrapper(project, parentDisposable)
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
    for (i in 0 until outputModel.getBlocksSize()) {
      highlightHyperlinks(outputModel.getByIndex(i))
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

    clearHyperlinks(block.outputStartOffset, block.endOffset)

    val startLine = document.getLineNumber(block.outputStartOffset)
    val endLine = document.getLineNumber(block.endOffset)
    hyperlinkSupport.highlightHyperlinksLater(filter, startLine, endLine, expirableTokenProvider.createExpirable())
  }

  private fun clearHyperlinks(startOffset: Int, endOffset: Int) {
    for (highlighter in getHyperlinks(startOffset, endOffset)) {
      hyperlinkSupport.removeHyperlink(highlighter)
    }
  }

  private fun getHyperlinks(startOffset: Int, endOffset: Int): List<RangeHighlighter> {
    val result: MutableList<RangeHighlighter> = ArrayList()
    processHyperlinks(startOffset, endOffset, CollectProcessor(result))
    return result
  }

  private fun processHyperlinks(startOffset: Int,
                                endOffset: Int,
                                processor: Processor<in RangeHighlighter>) {
    editor.markupModel.processRangeHighlightersOverlappingWith(
      startOffset, endOffset, FilteringProcessor({ it.isValid && EditorHyperlinkSupport.getHyperlinkInfo(it) != null }, processor))
  }
}