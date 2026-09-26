// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.caret.MinimapCaretController
import com.intellij.ide.minimap.geometry.MinimapGeometryCalculator
import com.intellij.ide.minimap.geometry.MinimapScaleUtil
import com.intellij.ide.minimap.layout.MinimapLayoutCalculator
import com.intellij.ide.minimap.listeners.MinimapStateListeners
import com.intellij.ide.minimap.listeners.MinimapUiListeners
import com.intellij.ide.minimap.interaction.MinimapInteractionPolicy
import com.intellij.ide.minimap.layout.MinimapLayoutPolicy
import com.intellij.ide.minimap.model.MinimapModel
import com.intellij.ide.minimap.model.MinimapStructureMarkerSnapshot
import com.intellij.ide.minimap.scene.MinimapSceneBuilder
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Graphics2D
import javax.swing.JPanel
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class MinimapController(
  coroutineScope: CoroutineScope,
  private val panel: MinimapPanel,
  private val container: JPanel,
): Disposable {
  @Volatile
  private var disposed = false

  private val scope = coroutineScope.childScope("MinimapController")
  private val snapshotUpdates = MutableSharedFlow<SnapshotRequest>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val structureUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val diagnosticsUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val settings = MinimapSettings.getInstance()
  private val editor: Editor = panel.editor

  private val model = MinimapModel(editor).also {
    Disposer.register(this, it)
  }

  private val layoutCalculator = MinimapLayoutCalculator(editor)
  private val geometryCalculator = MinimapGeometryCalculator(editor)
  private val sceneBuilder = MinimapSceneBuilder(editor, model, layoutCalculator, geometryCalculator)
  private val caretController = MinimapCaretController(editor, panel)
  private var independentAreaStart: Int? = null

  private val stateListeners = MinimapStateListeners(
    parentDisposable = this,
    coroutineScope = scope,
    editor = editor,
    caretController = caretController,
    scheduleStructureMarkersUpdate = ::scheduleStructureMarkersUpdate,
    scheduleDiagnosticsUpdate = { scheduleDiagnosticsUpdate() },
    scheduleBreakpointsUpdate = { scheduleDiagnosticsUpdate() },
    scheduleFoldingUpdate = { scheduleDiagnosticsUpdate() },
    invalidateLineProjection = model::invalidateLineProjection,
    updateParameters = ::refreshSnapshot,
    updateVisibleArea = ::refreshVisibleArea,
    repaint = panel::repaint,
  )

  private val uiListeners = MinimapUiListeners(
    parentDisposable = this,
    container = container,
    contentComponent = editor.contentComponent,
    updateParameters = ::refreshSnapshot,
    revalidate = panel::revalidate,
    repaint = panel::repaint
  )

  fun install() {
    stateListeners.install()
    uiListeners.install()
    initSnapshotFlow()
    refreshSnapshot()
    initStructureMarkersFlow()
    initDiagnosticsFlow()
  }

  override fun dispose() {
    disposed = true
    sceneBuilder.clear()
    scope.cancel()
  }

  fun isDocumentCommitted(): Boolean = model.isDocumentCommitted()

  fun paintCaret(graphics: Graphics2D): Unit = caretController.paint(graphics)

  fun scheduleStructureMarkersUpdate(): Boolean = structureUpdates.tryEmit(Unit)

  fun scheduleDiagnosticsUpdate(): Boolean = diagnosticsUpdates.tryEmit(Unit)

  fun refreshSnapshot() {
    if (disposed || editor.isDisposed) return

    val state = settings.state
    val independentScrollEnabled = isIndependentScrollEnabled()
    val request = SnapshotRequest(
      panelWidth = panel.width,
      panelHeight = max(if (panel.height > 0) panel.height else container.height, 0),
      editorWidth = container.width,
      fixedWidth = state.width,
      scaleMode = state.scaleMode,
      areaStartOverride = if (independentScrollEnabled) independentAreaStart else null,
      independentScrollEnabled = independentScrollEnabled,
    )
    snapshotUpdates.tryEmit(request)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun buildSnapshotUpdate(request: SnapshotRequest, structureMarkers: List<MinimapStructureMarkerSnapshot>): SnapshotUpdate {
    val effectiveScaleMode = MinimapLayoutPolicy.getEffectiveScaleMode(editor, request.scaleMode)
    val scaleData = MinimapScaleUtil.computeScale(editor, request.panelHeight, request.fixedWidth, effectiveScaleMode)
    val shouldBeVisible = !shouldHideForNarrowEditor(scaleData.width, request.editorWidth)
    val snapshot = if (shouldBeVisible) {
      sceneBuilder.buildSnapshot(
        panelWidth = max(request.panelWidth, scaleData.width),
        panelHeight = request.panelHeight,
        scaleData = scaleData,
        scaleMode = effectiveScaleMode,
        areaStartOverride = request.areaStartOverride,
        structureMarkers = structureMarkers,
      )
    }
    else {
      null
    }
    return SnapshotUpdate(scaleData.width, shouldBeVisible, request.independentScrollEnabled, snapshot)
  }

  private fun applySnapshotUpdate(update: SnapshotUpdate) {
    if (disposed || editor.isDisposed) return
    val isVisible = updatePanelVisibility(update.minimapWidth)
    if (isVisible != update.shouldBeVisible) {
      refreshSnapshot()
      return
    }
    if (!isVisible) return

    if (panel.updatePreferredWidth(update.minimapWidth)) {
      panel.revalidate()
    }
    val snapshot = update.snapshot ?: return
    panel.updateSnapshot(snapshot)
    if (update.independentScrollEnabled) {
      independentAreaStart = snapshot.geometry.areaStart
    }
    panel.repaint()
  }

  private fun refreshVisibleArea() {
    if (tryRefreshFitVisibleAreaGeometry()) return
    refreshSnapshot()
  }

  fun isIndependentScrollEnabled(): Boolean {
    return MinimapInteractionPolicy.useIndependentMinimapScroll(editor)
  }

  private fun tryRefreshFitVisibleAreaGeometry(): Boolean {
    if (isIndependentScrollEnabled()) return false

    val current = panel.currentSnapshot() ?: return false
    val state = settings.state
    val panelHeight = max(if (panel.height > 0) panel.height else container.height, 0)
    val effectiveScaleMode = MinimapLayoutPolicy.getEffectiveScaleMode(editor, state.scaleMode)
    if (effectiveScaleMode != MinimapScaleMode.FIT) return false

    val scaleData = MinimapScaleUtil.computeScale(editor, panelHeight, state.width, effectiveScaleMode)
    if (!scaleData.fitToHeight) return false

    val panelWidth = max(panel.width, scaleData.width)
    if (current.context.panelWidth != panelWidth || current.context.panelHeight != panelHeight) return false

    val lineProjection = model.getLineProjection()
    if (lineProjection !== current.context.lineProjection) return false

    val geometry = geometryCalculator.compute(panelHeight, scaleData, effectiveScaleMode, lineProjection.projectedLineCount)
    if (geometry.minimapHeight != current.geometry.minimapHeight ||
        geometry.areaStart != current.geometry.areaStart ||
        geometry.areaEnd != current.geometry.areaEnd) {
      return false
    }

    val context = current.context.copy(geometry = geometry)
    panel.updateSnapshot(current.copy(context = context, geometry = geometry))
    return true
  }

  fun scrollIndependentViewportBy(deltaPx: Int): Boolean {
    if (!isIndependentScrollEnabled()) return false
    if (deltaPx == 0) return false

    val current = panel.currentSnapshot() ?: return false
    val panelHeight = max(if (panel.height > 0) panel.height else container.height, 0)
    val maxAreaStart = (current.geometry.minimapHeight - panelHeight).coerceAtLeast(0)
    val targetAreaStart = (current.geometry.areaStart + deltaPx).coerceIn(0, maxAreaStart)
    if (targetAreaStart == current.geometry.areaStart) return false

    independentAreaStart = targetAreaStart
    refreshSnapshot()
    panel.repaint()
    return true
  }

  private fun updatePanelVisibility(minimapWidth: Int): Boolean {
    val hiddenForNarrowEditor = shouldHideForNarrowEditor(minimapWidth, container.width)
    val shouldBeVisible = !hiddenForNarrowEditor
    if (panel.isVisible == shouldBeVisible) return shouldBeVisible
    panel.isVisible = shouldBeVisible
    container.revalidate()
    container.repaint()
    return shouldBeVisible
  }

  private fun shouldHideForNarrowEditor(minimapWidth: Int, editorWidth: Int): Boolean {
    if (minimapWidth <= 0) return false
    if (editorWidth <= 0) return false
    return editorWidth.toLong() <= minimapWidth.toLong() * HIDE_MINIMAP_EDITOR_WIDTH_MULTIPLIER
  }

  fun updateStructureMarkersNow() {
    scope.launch {
      updateStructureMarkers()
    }
  }

  private suspend fun updateStructureMarkers() {
    readAction {
      model.updateStructureMarkers()
    }
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      if (!disposed) {
        refreshSnapshot()
        panel.repaint()
      }
    }
  }

  private fun initStructureMarkersFlow() = scope.launch {
    structureUpdates.debounce(STRUCTURE_MARKERS_DEBOUNCE_MS.milliseconds).collectLatest {
      updateStructureMarkers()
    }
  }

  private fun initDiagnosticsFlow() = scope.launch {
    diagnosticsUpdates.debounce(DIAGNOSTICS_DEBOUNCE_MS.milliseconds).collect {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        if (disposed) return@withContext
        refreshSnapshot()
        panel.repaint()
      }
    }
  }

  private fun initSnapshotFlow() = scope.launch {
    snapshotUpdates.collectLatest { request ->
      val structureMarkers = readAction {
        sceneBuilder.captureStructureMarkers()
      }
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val update = buildSnapshotUpdate(request, structureMarkers)
        applySnapshotUpdate(update)
      }
    }
  }

  private data class SnapshotRequest(
    val panelWidth: Int,
    val panelHeight: Int,
    val editorWidth: Int,
    val fixedWidth: Int,
    val scaleMode: MinimapScaleMode,
    val areaStartOverride: Int?,
    val independentScrollEnabled: Boolean,
  )

  private data class SnapshotUpdate(
    val minimapWidth: Int,
    val shouldBeVisible: Boolean,
    val independentScrollEnabled: Boolean,
    val snapshot: MinimapSnapshot?,
  )

  companion object {
    private const val STRUCTURE_MARKERS_DEBOUNCE_MS: Long = 125
    private const val DIAGNOSTICS_DEBOUNCE_MS: Long = 125
    private const val HIDE_MINIMAP_EDITOR_WIDTH_MULTIPLIER: Long = 2
  }
}
