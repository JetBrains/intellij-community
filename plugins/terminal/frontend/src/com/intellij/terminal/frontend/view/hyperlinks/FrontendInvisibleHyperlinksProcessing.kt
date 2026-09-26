// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.impl.EditorTextDecoration
import com.intellij.execution.impl.EditorTextDecorationApplier
import com.intellij.execution.impl.EditorTextDecorationId
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHoverLineRequest
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.session.toFilterResultInfo
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener

/**
 * Installs the computation of invisible hyperlinks for the output line under the mouse
 * pointer.
 *
 * Unlike the hyperlinks of `FrontendTerminalHyperlinksProcessing`, which are computed
 * for every output line, invisible hyperlinks are requested from the backend when the
 * pointer enters a line. The decorations of the last [HOVER_CACHED_LINES] hovered lines
 * are kept until the output changes, so moving between recently hovered lines needs no
 * new request, and the hint and the context menu keep a valid target after the pointer
 * leaves the text. Every request tells the backend which earlier results are still
 * shown, and the backend keeps exactly those.
 *
 * The pointer position comes from [EditorTextDecorationApplier.mouseHoveredTextOffset].
 *
 * @param processedHovers incremented every time a hover state has been processed,
 * for tests to await
 */
internal fun installInvisibleHyperlinksOnHover(
  outputModel: TerminalOutputModel,
  applier: EditorTextDecorationApplier,
  session: TerminalHyperlinksSession,
  onLinkClicked: (TerminalHyperlinkId, EditorMouseEvent) -> Unit,
  processedHovers: MutableStateFlow<Long>,
  coroutineScope: CoroutineScope,
) {
  val processing = InvisibleHyperlinksOnHover(outputModel, applier, session, onLinkClicked, processedHovers)
  outputModel.addListener(coroutineScope.asDisposable(), processing.contentListener)
  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement() + CoroutineName("Terminal invisible hyperlinks on hover")) {
    launch {
      applier.mouseHoveredTextOffset.collect {
        processing.mouseMoved(it)
      }
    }
    processing.hoverState.collectLatest {
      processing.process(it)
    }
  }
}

private class InvisibleHyperlinksOnHover(
  private val outputModel: TerminalOutputModel,
  private val applier: EditorTextDecorationApplier,
  private val session: TerminalHyperlinksSession,
  private val onLinkClicked: (TerminalHyperlinkId, EditorMouseEvent) -> Unit,
  private val processedHovers: MutableStateFlow<Long>,
) {
  /**
   * The hovered line and the modification count of the output.
   *
   * The count grows on every content change. That requests the hovered line again with
   * its current text and drops the reply to a request made before the change.
   */
  val hoverState = MutableStateFlow(HoverState(hoveredLine = null, modCount = 0L))

  // The fields below are accessed on the EDT only.
  private var modCount = 0L
  private var lastRequestId = 0L
  /** The decorations of recently hovered lines, the earliest hovered first. */
  private val cachedLines = LinkedHashMap<TerminalLineIndex, CachedLine>()

  val contentListener = object : TerminalOutputModelListener {
    override fun afterContentChanged(event: TerminalContentChangeEvent) {
      dropCachedLines()
      modCount++
      hoverState.update { it.copy(modCount = modCount) }
    }
  }

  /**
   * Updates the hovered line from the document offset under the pointer, or clears it
   * when [documentOffset] is `null`. An offset past the end of the output, left by
   * content that shrank since the mouse event, clears it too.
   */
  fun mouseMoved(documentOffset: Int?) {
    val line = documentOffset?.let { findLineByOffset(it) }
    hoverState.update { it.copy(hoveredLine = line) }
  }

  private fun findLineByOffset(documentOffset: Int): TerminalLineIndex? {
    val offset = outputModel.startOffset + documentOffset.toLong()
    return if (offset <= outputModel.endOffset) outputModel.getLineByOffset(offset) else null
  }

  /** Requests the hyperlinks of the hovered line unless they are cached already. */
  suspend fun process(state: HoverState) {
    // collectLatest cancels and joins the previous block before starting this one. If
    // the content changed during that wait, a newer state is already published, and a
    // request for this state would be cancelled at once.
    if (state.modCount != this.modCount) return
    val line = state.hoveredLine
    if (line != null && line !in cachedLines) {
      val requestId = ++lastRequestId
      val decorations = requestDecorations(line, requestId, state.modCount)
      if (decorations != null) {
        applier.addDecorations(decorations)
        cachedLines[line] = CachedLine(decorations.map { it.id }, requestId)
        dropEarliestCachedLines()
      }
    }
    processedHovers.update { it + 1 }
  }

  /**
   * Requests the invisible hyperlinks of [line] and returns their decorations, or `null`
   * if the line is gone, the request failed, or the output changed while the request
   * was in flight.
   */
  private suspend fun requestDecorations(line: TerminalLineIndex, requestId: Long, modCount: Long): List<EditorTextDecoration>? {
    if (line < outputModel.firstLineIndex || line > outputModel.lastLineIndex) return null
    val start = outputModel.getStartOfLine(line)
    val text = outputModel.getText(start, outputModel.getEndOfLine(line)).toString()
    val retainedIds = cachedLines.values.map { it.requestId }
    val results = try {
      session.findInvisibleHyperlinks(TerminalHoverLineRequest(text, start.toAbsolute(), requestId, retainedIds))
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to find invisible hyperlinks in the hovered line", e)
      return null
    }
    // Resumed on the UI thread. If the content changed during the request, a newer state
    // is published, but the collectLatest turn that cancels this block may be queued
    // behind this resumption. The reply is stale then.
    if (modCount != this.modCount) return null
    return results.mapNotNull {
      it.toFilterResultInfo().toEditorDecoration(outputModel, onLinkClicked)
    }
  }

  private fun dropEarliestCachedLines() {
    while (cachedLines.size > HOVER_CACHED_LINES) {
      val iterator = cachedLines.values.iterator()
      val cached = iterator.next()
      iterator.remove()
      applier.removeDecorations(cached.decorationIds)
    }
  }

  private fun dropCachedLines() {
    if (cachedLines.isNotEmpty()) {
      for (cached in cachedLines.values) {
        applier.removeDecorations(cached.decorationIds)
      }
      cachedLines.clear()
    }
  }
}

/** The hovered line, or `null` when the pointer is not over text, and the modification count of the output. */
private data class HoverState(val hoveredLine: TerminalLineIndex?, val modCount: Long)

/**
 * The decorations of a hovered line and the id of the request that found them,
 * see [TerminalHoverLineRequest.id].
 */
private class CachedLine(
  val decorationIds: List<EditorTextDecorationId>,
  val requestId: Long,
)

/**
 * The number of recently hovered lines whose invisible hyperlink decorations are kept.
 */
@ApiStatus.Internal
const val HOVER_CACHED_LINES: Int = 50

private val LOG = fileLogger()
