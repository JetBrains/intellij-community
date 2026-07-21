// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.listeners

import com.intellij.ide.minimap.breakpoints.MinimapBreakpointUtil
import com.intellij.ide.minimap.caret.MinimapCaretController
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.ErrorStripeEvent
import com.intellij.openapi.editor.ex.ErrorStripeListener
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.SoftWrapModelEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class MinimapStateListeners(
  private val parentDisposable: Disposable,
  private val coroutineScope: CoroutineScope,
  private val editor: Editor,
  private val caretController: MinimapCaretController,
  private val scheduleStructureMarkersUpdate: () -> Unit,
  private val scheduleDiagnosticsUpdate: () -> Unit,
  private val scheduleBreakpointsUpdate: () -> Unit,
  private val scheduleFoldingUpdate: () -> Unit,
  private val invalidateLineProjection: () -> Unit,
  private val updateParameters: () -> Unit,
  private val updateVisibleArea: () -> Unit,
  private val repaint: () -> Unit,
) {
  private val documentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      invalidateLineProjection()
      scheduleStructureMarkersUpdate()
    }
  }

  private var disposed = false
  private val pendingSoftWrapRefreshSnapshot = AtomicBoolean(false)
  private var lastSoftWrapInvalidationFingerprint: Long? = null
  private var lastSoftWrapRefreshFingerprint: Long? = null
  private val softWrapProjectionUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val selectionListener = object : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      repaint()
    }
  }

  private val caretListener = object : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      if (event.caret != editor.caretModel.primaryCaret) return

      val newOffset = event.caret.offset
      ApplicationManager.getApplication().invokeLater {
        if (editor.isDisposed) return@invokeLater
        caretController.caretMoved(newOffset)
      }
    }
  }

  private val visibleAreaListener = object : VisibleAreaListener {
    private var visibleArea = Rectangle(0, 0, 0, 0)

    override fun visibleAreaChanged(e: VisibleAreaEvent) {
      val newArea = e.newRectangle
      if (visibleArea.y == newArea.y &&
          visibleArea.height == newArea.height &&
          visibleArea.width == newArea.width) {
        return
      }
      val sizeChanged = visibleArea.height != newArea.height || visibleArea.width != newArea.width
      visibleArea = newArea
      if (sizeChanged) {
        updateParameters()
      }
      else {
        updateVisibleArea()
      }
      repaint()
    }
  }

  private val errorStripeListener = object : ErrorStripeListener {
    override fun errorMarkerChanged(e: ErrorStripeEvent) {
      scheduleDiagnosticsUpdate()
    }
  }

  private val breakpointMarkupListener = object : MarkupModelListener {
    override fun afterAdded(highlighter: RangeHighlighterEx) {
      onHighlighterChanged(highlighter)
    }

    override fun afterRemoved(highlighter: RangeHighlighterEx) {
      onHighlighterChanged(highlighter)
    }

    override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleOrColorChanged: Boolean) {
      if (!renderersChanged && !fontStyleOrColorChanged) return
      onHighlighterChanged(highlighter)
    }

    private fun onHighlighterChanged(highlighter: RangeHighlighterEx) {
      if (!MinimapBreakpointUtil.isBreakpointHighlighter(highlighter)) return
      scheduleBreakpointsUpdate()
    }
  }

  private val foldingListener = object : FoldingListener {
    override fun onFoldProcessingEnd() {
      invalidateLineProjection()
      scheduleFoldingUpdate()
    }
  }

  private val softWrapListener = object : SoftWrapChangeListener {
    override fun softWrapsChanged() {
      scheduleSoftWrapProjectionUpdate(refreshSnapshot = false)
    }

    override fun recalculationEnds() {
      scheduleSoftWrapProjectionUpdate(refreshSnapshot = true)
    }
  }

  fun install() {
    Disposer.register(parentDisposable) {
      disposed = true
    }
    initSoftWrapProjectionFlow()
    editor.scrollingModel.addVisibleAreaListener(visibleAreaListener, parentDisposable)
    editor.selectionModel.addSelectionListener(selectionListener, parentDisposable)
    editor.caretModel.addCaretListener(caretListener, parentDisposable)
    editor.document.addDocumentListener(documentListener, parentDisposable)
    (editor.markupModel as? EditorMarkupModel)?.addErrorMarkerListener(errorStripeListener, parentDisposable)
    (editor.foldingModel as? FoldingModelEx)?.addListener(foldingListener, parentDisposable)
    MinimapSoftWrapDispatcher.getOrCreate(editor)?.subscribe(softWrapListener, parentDisposable)
    (editor as? EditorEx)?.let { editorEx ->
      editorEx.markupModel.addMarkupModelListener(parentDisposable, breakpointMarkupListener)
      editorEx.filteredDocumentMarkupModel.addMarkupModelListener(parentDisposable, breakpointMarkupListener)
    }
  }

  private fun scheduleSoftWrapProjectionUpdate(refreshSnapshot: Boolean) {
    if (disposed || editor.isDisposed) return
    if (refreshSnapshot) {
      pendingSoftWrapRefreshSnapshot.set(true)
    }
    softWrapProjectionUpdates.tryEmit(Unit)
  }

  private fun initSoftWrapProjectionFlow() {
    coroutineScope.launch {
      merge(
        softWrapProjectionUpdates.debounce(SOFT_WRAP_UPDATE_DEBOUNCE_MS.milliseconds),
        softWrapProjectionUpdates.sample(SOFT_WRAP_UPDATE_SAMPLE_MS.milliseconds),
      ).collect {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          processSoftWrapProjectionUpdate()
        }
      }
    }
  }

  private fun processSoftWrapProjectionUpdate() {
    if (disposed || editor.isDisposed) return
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.width <= 0 || visibleArea.height <= 0) return

    val refreshSnapshot = pendingSoftWrapRefreshSnapshot.getAndSet(false)
    val fingerprint = softWrapProjectionFingerprint()
    val shouldInvalidate = fingerprint != lastSoftWrapInvalidationFingerprint
    val shouldRefresh = refreshSnapshot && fingerprint != lastSoftWrapRefreshFingerprint
    if (!shouldInvalidate && !shouldRefresh) return

    if (shouldInvalidate) {
      invalidateLineProjection()
      lastSoftWrapInvalidationFingerprint = fingerprint
    }
    if (shouldRefresh) {
      updateParameters()
      repaint()
      lastSoftWrapRefreshFingerprint = fingerprint
    }
  }

  private fun softWrapProjectionFingerprint(): Long {
    val document = editor.document
    val visibleArea = editor.scrollingModel.visibleArea
    val softWrapModel = editor.softWrapModel
    var hash = document.modificationStamp
    hash = hash * FINGERPRINT_HASH_MULTIPLIER + document.lineCount.toLong()
    hash = hash * FINGERPRINT_HASH_MULTIPLIER + visibleArea.width.toLong()
    hash = hash * FINGERPRINT_HASH_MULTIPLIER + if (softWrapModel.isSoftWrappingEnabled) 1L else 0L
    hash = hash * FINGERPRINT_HASH_MULTIPLIER + EditorUtil.getPlainSpaceWidth(editor).toLong()
    hash = hash * FINGERPRINT_HASH_MULTIPLIER + EditorUtil.getTabSize(editor).toLong()
    hash = hash * FINGERPRINT_HASH_MULTIPLIER + registeredSoftWrapsHash()
    return hash
  }

  private fun registeredSoftWrapsHash(): Long {
    val softWrapModel = editor.softWrapModel as? SoftWrapModelEx ?: return 0
    val registeredSoftWraps = softWrapModel.registeredSoftWraps
    var hash = registeredSoftWraps.size.toLong()
    if (registeredSoftWraps.isNotEmpty()) {
      val first = registeredSoftWraps.first()
      val last = registeredSoftWraps.last()
      hash = FINGERPRINT_HASH_MULTIPLIER * hash + first.start.toLong()
      hash = FINGERPRINT_HASH_MULTIPLIER * hash + last.start.toLong()
      hash = FINGERPRINT_HASH_MULTIPLIER * hash + last.indentInColumns.toLong()
    }
    return hash
  }

  companion object {
    private const val SOFT_WRAP_UPDATE_DEBOUNCE_MS: Long = 100
    private const val SOFT_WRAP_UPDATE_SAMPLE_MS: Long = 100
    private const val FINGERPRINT_HASH_MULTIPLIER: Long = 31
  }
}
