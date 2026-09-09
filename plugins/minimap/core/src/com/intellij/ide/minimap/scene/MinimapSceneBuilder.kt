// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.scene

import com.intellij.ide.minimap.breakpoints.MinimapBreakpointCollector
import com.intellij.ide.minimap.diagnostics.MinimapDiagnosticsCollector
import com.intellij.ide.minimap.folding.MinimapFoldMarkerCollector
import com.intellij.ide.minimap.geometry.MinimapGeometryCalculator
import com.intellij.ide.minimap.geometry.MinimapScaleData
import com.intellij.ide.minimap.layout.MinimapLayoutCalculator
import com.intellij.ide.minimap.layout.MinimapLayoutModeSelector
import com.intellij.ide.minimap.model.MinimapStructureMarker
import com.intellij.ide.minimap.model.MinimapStructureMarkerSnapshot
import com.intellij.ide.minimap.model.MinimapModel
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.annotations.RequiresReadLock

class MinimapSceneBuilder(
  private val editor: Editor,
  private val model: MinimapModel,
  private val layoutCalculator: MinimapLayoutCalculator,
  private val geometryCalculator: MinimapGeometryCalculator,
) {
  private val diagnosticsCollector = MinimapDiagnosticsCollector(editor)
  private val breakpointCollector = MinimapBreakpointCollector(editor)
  private val foldCollector = MinimapFoldMarkerCollector()
  @Volatile
  private var lastStructureMarkers: List<MinimapStructureMarker> = emptyList()

  fun buildSnapshot(panelWidth: Int,
                    panelHeight: Int,
                    scaleData: MinimapScaleData,
                    scaleMode: MinimapScaleMode,
                    areaStartOverride: Int? = null,
                    structureMarkers: List<MinimapStructureMarkerSnapshot> = captureStructureMarkers()): MinimapSnapshot {
    val lineProjection = model.getLineProjection()
    val geometry = geometryCalculator.compute(panelHeight, scaleData, scaleMode, lineProjection.projectedLineCount, areaStartOverride)
    val context = MinimapRenderContext(
      editor = editor,
      panelWidth = panelWidth,
      panelHeight = panelHeight,
      geometry = geometry,
      lineProjection = lineProjection,
    )

    val layoutMode = MinimapLayoutModeSelector.selectMode(context, scaleMode)
    val layout = layoutCalculator.buildLayout(context, structureMarkers, layoutMode)
    val diagnosticEntries = diagnosticsCollector.buildEntries(context, layout.metrics)
    val breakpointEntries = breakpointCollector.buildEntries(context, layout.metrics, layoutMode)
    val foldEntries = foldCollector.buildEntries(context, layout.metrics)

    return MinimapSnapshot(
      context = context,
      geometry = geometry,
      tokenEntries = layout.tokenEntries,
      structureEntries = layout.structureEntries,
      diagnosticEntries = diagnosticEntries,
      breakpointEntries = breakpointEntries,
      foldEntries = foldEntries,
      layoutMetrics = layout.metrics,
      layoutMode = layoutMode,
    )
  }

  @RequiresReadLock
  fun captureStructureMarkers(): List<MinimapStructureMarkerSnapshot> {
    val structureMarkers = if (model.isDocumentCommitted()) {
      model.getStructureMarkers().also { lastStructureMarkers = it }
    }
    else {
      lastStructureMarkers
    }
    return structureMarkers.mapNotNull { marker ->
      val rangeMarker = marker.rangeMarker ?: return@mapNotNull null
      if (!rangeMarker.isValid) return@mapNotNull null
      MinimapStructureMarkerSnapshot(marker.elementReference, rangeMarker.textRange)
    }
  }

  fun clear() {
    lastStructureMarkers = emptyList()
  }
}
