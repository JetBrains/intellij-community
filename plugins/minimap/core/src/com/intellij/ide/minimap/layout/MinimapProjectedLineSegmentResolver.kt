// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

import com.intellij.ide.minimap.model.MinimapLineProjection
import com.intellij.ide.minimap.model.MinimapSourceSoftWrap
import com.intellij.openapi.editor.Editor

internal class MinimapProjectedLineSegmentResolver(
  private val editor: Editor,
  private val lineProjection: MinimapLineProjection,
) {
  private val document = editor.document
  private val segmentsByLogicalLine = HashMap<Int, List<MinimapProjectedLineSegment>>()

  fun segmentFor(projectedLine: Int): MinimapProjectedLineSegment? {
    val logicalLine = lineProjection.projectedToLogicalLine(projectedLine) ?: return null
    val slotIndex = lineProjection.projectedLineSlotIndex(projectedLine) ?: return null
    return segmentsFor(logicalLine).getOrNull(slotIndex)
  }

  private fun segmentsFor(logicalLine: Int): List<MinimapProjectedLineSegment> {
    return segmentsByLogicalLine.getOrPut(logicalLine) {
      buildSegments(logicalLine)
    }
  }

  private fun buildSegments(logicalLine: Int): List<MinimapProjectedLineSegment> {
    if (logicalLine !in 0 until document.lineCount) return emptyList()

    val lineStartOffset = document.getLineStartOffset(logicalLine)
    val lineEndOffset = document.getLineEndOffset(logicalLine)
    if (lineEndOffset <= lineStartOffset) {
      return listOf(MinimapProjectedLineSegment(lineStartOffset, lineEndOffset, lineStartOffset, lineEndOffset, 0))
    }

    val softWraps = sourceSoftWraps(logicalLine, lineStartOffset, lineEndOffset)
    if (softWraps.isEmpty()) {
      return listOf(MinimapProjectedLineSegment(lineStartOffset, lineEndOffset, lineStartOffset, lineEndOffset, 0))
    }

    val result = ArrayList<MinimapProjectedLineSegment>(softWraps.size + 1)
    var segmentStartOffset = lineStartOffset
    var segmentStartColumn = 0
    for (softWrap in softWraps) {
      if (softWrap.startOffset > segmentStartOffset) {
        result += MinimapProjectedLineSegment(
          lineStartOffset = lineStartOffset,
          lineEndOffset = lineEndOffset,
          startOffset = segmentStartOffset,
          endOffset = softWrap.startOffset,
          startColumn = segmentStartColumn,
        )
      }
      segmentStartOffset = softWrap.startOffset
      segmentStartColumn = softWrap.indentColumns
    }

    if (segmentStartOffset < lineEndOffset || result.isEmpty()) {
      result += MinimapProjectedLineSegment(
        lineStartOffset = lineStartOffset,
        lineEndOffset = lineEndOffset,
        startOffset = segmentStartOffset,
        endOffset = lineEndOffset,
        startColumn = segmentStartColumn,
      )
    }
    return result
  }

  private fun sourceSoftWraps(logicalLine: Int, lineStartOffset: Int, lineEndOffset: Int): List<MinimapSourceSoftWrap> {
    val sourceSoftWraps = lineProjection.sourceSoftWraps(logicalLine)
    if (sourceSoftWraps != null) {
      return normalize(sourceSoftWraps, lineStartOffset, lineEndOffset)
    }

    val editorSoftWraps = editor.softWrapModel.getSoftWrapsForLine(logicalLine)
    if (editorSoftWraps.isEmpty()) return emptyList()

    return normalize(
      editorSoftWraps.map { MinimapSourceSoftWrap(it.start, it.indentInColumns) },
      lineStartOffset,
      lineEndOffset,
    )
  }

  private fun normalize(softWraps: List<MinimapSourceSoftWrap>,
                        lineStartOffset: Int,
                        lineEndOffset: Int): List<MinimapSourceSoftWrap> {
    return softWraps
      .asSequence()
      .filter { it.startOffset in (lineStartOffset + 1) until lineEndOffset }
      .sortedBy { it.startOffset }
      .distinctBy { it.startOffset }
      .map { MinimapSourceSoftWrap(it.startOffset, it.indentColumns.coerceAtLeast(0)) }
      .toList()
  }
}
