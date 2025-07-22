// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.EditorHyperlinkListener
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.execution.impl.ExpirableTokenProvider
import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.SystemProperties
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.hyperlinks.CompositeFilterWrapper
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class TerminalHyperlinkHighlighter private constructor(
  project: Project,
  private val editor: Editor,
  private val coroutineScope: CoroutineScope,
) {

  private val filterWrapper: CompositeFilterWrapper = CompositeFilterWrapper(project, coroutineScope)
  private val tokenProvider: ExpirableTokenProvider = ExpirableTokenProvider()

  // should be accessed in EDT
  private var actualStartOffset: Int = INFINITE

  // should be accessed in EDT
  private var delayedHighlightingJob: Job? = null

  private val document: Document
    get() = editor.document

  private val hyperlinkSupport: EditorHyperlinkSupport
    get() = EditorHyperlinkSupport.get(editor, true)

  init {
    filterWrapper.addFiltersUpdatedListener {
      rehighlightAll()
    }
    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        // There are only two possible types of document changes:
        // 1. Deletion from the document top (trimming).
        // 2. Replacement of the document bottom.
        if (event.getOffset() == 0 && event.getNewLength() == 0) {
          // This is a deletion from the document top.
          actualStartOffset = max(0, actualStartOffset - event.getOldLength())
        }
      }
    })
    // Some unnecessary defensive coding here: invokeOnCompletion may be invoked at a tricky moment,
    // therefore, we avoid calling the hyperlinkSupport getter from the completion handler just in case.
    // Also, it's very much likely that we don't even need to remove the listener,
    // as the lifetime of this entire thing matches the lifetime of the editor.
    // However, it's better to write unnecessarily correct code than to figure out later that it unexpectedly broke.
    val hyperlinkSupport = hyperlinkSupport
    val listener = EditorHyperlinkListener {
      ReworkedTerminalUsageCollector.logHyperlinkFollowed(it.javaClass)
    }
    hyperlinkSupport.addEditorHyperlinkListener(listener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      hyperlinkSupport.removeEditorHyperlinkListener(listener)
    }
  }

  internal fun addFilter(filter: Filter) {
    filterWrapper.addFilter(filter)
  }

  private fun rehighlightAll() {
    tokenProvider.invalidateAll()
    hyperlinkSupport.clearHyperlinks(0, document.textLength)
    highlightHyperlinks(0)
  }

  @RequiresEdt(generateAssertion = false)
  fun highlightHyperlinks(startOffset: Int) {
    actualStartOffset = min(actualStartOffset, startOffset)
    if (delayedHighlightingJob.isNotActive()) {
      delayedHighlightingJob = coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        delay(DELAY)
        doHyperlinkHighlighting()
      }
    }
  }

  private fun doHyperlinkHighlighting() {
    val filter = filterWrapper.getFilter() ?: return // if null, `rehighlightAll` will follow
    if (filter.isEmpty) return
    if (document.textLength == 0) return
    val startOffset = actualStartOffset
    actualStartOffset = INFINITE
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
  internal suspend fun awaitDelayedHighlightings() {
    delayedHighlightingJob?.join()
  }

  @TestOnly
  internal fun getHyperlinkSupport(): EditorHyperlinkSupport = hyperlinkSupport

  companion object {
    fun install(project: Project, model: TerminalOutputModel, editor: Editor, coroutineScope: CoroutineScope): TerminalHyperlinkHighlighter {
      val hyperlinkHighlighter = TerminalHyperlinkHighlighter(project, editor, coroutineScope)
      model.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
        override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
          hyperlinkHighlighter.highlightHyperlinks(startOffset)
        }
      })
      if (Registry.`is`("terminal.generic.hyperlinks", false) && AppModeAssertions.isMonolith()) {
        hyperlinkHighlighter.addFilter(GenericFileFilter(project, LocalFileSystem.getInstance()))
      }
      return hyperlinkHighlighter
    }

    /**
     * @see com.intellij.execution.impl.ConsoleViewImpl.DEFAULT_FLUSH_DELAY
     */
    private val DELAY: Duration = SystemProperties.getIntProperty("console.flush.delay.ms", 200).milliseconds

    /**
     * A value greater than possible document length.
     * A document length cannot be greater than 2048 MB, because [Document.getTextLength] return type is `Int`.
     */
    private const val INFINITE: Int = Integer.MAX_VALUE

    private fun Job?.isNotActive(): Boolean = this == null || !this.isActive
  }
}
