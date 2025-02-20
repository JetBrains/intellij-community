// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.hyperlinks

import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.execution.impl.ExpirableTokenProvider
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.hyperlinks.CompositeFilterWrapper
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import kotlin.math.max

@ApiStatus.Internal
class TerminalHyperlinkHighlighter private constructor(
  project: Project,
  private val editor: Editor,
  coroutineScope: CoroutineScope,
) {

  private val filterWrapper: CompositeFilterWrapper = CompositeFilterWrapper(project, coroutineScope)
  private val tokenProvider: ExpirableTokenProvider = ExpirableTokenProvider()

  private val document: Document
    get() = editor.document

  private val hyperlinkSupport: EditorHyperlinkSupport
    get() = EditorHyperlinkSupport.get(editor, true)

  init {
    filterWrapper.addFiltersUpdatedListener {
      rehighlightAll()
    }
  }

  private fun rehighlightAll() {
    tokenProvider.invalidateAll()
    hyperlinkSupport.clearHyperlinks(0, document.textLength)
    highlightHyperlinks(0)
  }

  @RequiresEdt(generateAssertion = false)
  fun highlightHyperlinks(startOffset: Int) {
    val filter = filterWrapper.getFilter() ?: return // if null, `rehighlightAll` will follow
    if (filter.isEmpty) return
    if (document.textLength == 0) return
    val startLine = document.getLineNumber(startOffset)
    val endLine = max(0, document.lineCount - 1)

    // Clear the whole range to avoid multiple hyperlinks/inlays in the same positions.
    // It happens when the same area is updated in the editor several times, leading to
    // several `hyperlinkSupport.highlightHyperlinksLater` calls.
    // For example, it happens:
    //  * when moving the cursor in full-screen apps with alternate buffer enabled;
    //  * sometimes with large output from an app;
    //  * on Windows with ConPTY.
    // Even if multiple hyperlinks at the same position are visually identical,
    // the problem is that multiple inlays are visually multiplied.
    hyperlinkSupport.clearHyperlinks(startOffset, document.textLength)

    hyperlinkSupport.highlightHyperlinksLater(filter, startLine, endLine, tokenProvider.createExpirable())
  }

  @TestOnly
  internal suspend fun awaitInitialized() {
    filterWrapper.awaitFiltersComputed()
  }

  @TestOnly
  internal fun getHyperlinkSupport(): EditorHyperlinkSupport = hyperlinkSupport

  companion object {
    fun install(project: Project, model: TerminalOutputModel, editor: Editor, coroutineScope: CoroutineScope): TerminalHyperlinkHighlighter {
      val hyperlinkHighlighter = TerminalHyperlinkHighlighter(project, editor, coroutineScope)
      model.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
        override fun afterContentChanged(startOffset: Int) {
          hyperlinkHighlighter.highlightHyperlinks(startOffset)
        }
      })
      return hyperlinkHighlighter
    }
  }

}
