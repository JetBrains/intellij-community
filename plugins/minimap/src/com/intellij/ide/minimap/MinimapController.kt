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
import com.intellij.ide.minimap.scene.MinimapSceneBuilder
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    // The snapshot pass resolves structure markers and reads the editor/document model in
    // MinimapSceneBuilder, which needs read access. This runs on EDT from many call sites
    // (resize, scroll, settings, LAF changes, …); wrap once here so every caller is protected.
    WriteIntentReadAction.run {
      val state = settings.state
      val panelHeight = max(if (panel.height > 0) panel.height else container.height, 0)
      val effectiveScaleMode = MinimapLayoutPolicy.getEffectiveScaleMode(editor, state.scaleMode)
      val scaleData = MinimapScaleUtil.computeScale(editor, panelHeight, state.width, effectiveScaleMode)
      if (!updatePanelVisibility(scaleData.width)) {
        return@run
      }
      if (panel.updatePreferredWidth(scaleData.width)) {
        panel.revalidate()
      }

      val panelWidth = max(panel.width, scaleData.width)
      val areaStartOverride = if (isIndependentScrollEnabled()) independentAreaStart else null
      val snapshot = sceneBuilder.buildSnapshot(
        panelWidth,
        panelHeight,
        scaleData,
        effectiveScaleMode,
        areaStartOverride,
      )
      panel.updateSnapshot(snapshot)
      if (isIndependentScrollEnabled()) {
        independentAreaStart = snapshot.geometry.areaStart
      }
    }
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
    val hiddenForNarrowEditor = shouldHideForNarrowEditor(minimapWidth)
    val shouldBeVisible = !hiddenForNarrowEditor
    if (panel.isVisible == shouldBeVisible) return shouldBeVisible
    panel.isVisible = shouldBeVisible
    container.revalidate()
    container.repaint()
    return shouldBeVisible
  }

  private fun shouldHideForNarrowEditor(minimapWidth: Int): Boolean {
    if (minimapWidth <= 0) return false
    val editorWidth = container.width
    if (editorWidth <= 0) return false
    return editorWidth.toLong() <= minimapWidth.toLong() * HIDE_MINIMAP_EDITOR_WIDTH_MULTIPLIER
  }

  fun updateStructureMarkersNow() {
    ReadAction.nonBlocking<Unit> { model.updateStructureMarkers() }
      .coalesceBy(this)
      .expireWith(this)
      .finishOnUiThread(ModalityState.any()) {
        refreshSnapshot()
        panel.repaint()
      }.submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun initStructureMarkersFlow() = scope.launch {
    structureUpdates.debounce(STRUCTURE_MARKERS_DEBOUNCE_MS.milliseconds).collect {
      updateStructureMarkersNow()
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

  companion object {
    private const val STRUCTURE_MARKERS_DEBOUNCE_MS: Long = 125
    private const val DIAGNOSTICS_DEBOUNCE_MS: Long = 125
    private const val HIDE_MINIMAP_EDITOR_WIDTH_MULTIPLIER: Long = 2
  }
}
