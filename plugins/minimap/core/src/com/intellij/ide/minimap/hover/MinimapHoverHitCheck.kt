// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.geometry.MinimapLineGeometryUtil
import com.intellij.ide.minimap.layout.MinimapLayoutMetrics
import com.intellij.ide.minimap.layout.MinimapLayoutUtil.lineTop
import com.intellij.ide.minimap.render.MinimapRenderEntry
import com.intellij.ide.minimap.interaction.MinimapInteractionPolicy
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.ceil

class MinimapHoverHitCheck(private val editor: Editor) {
  private val structureMarkerPolicy = MinimapInteractionPolicy.forEditor(editor)

  @RequiresBackgroundThread
  @RequiresReadLock
  fun resolveHit(snapshot: MinimapSnapshot, point: Point?): MinimapHoverHitCheckResult? {
    if (point == null) return null
    val entries = snapshot.structureEntries
    if (entries.isEmpty()) return null

    val context = snapshot.context
    var bestEntry: MinimapRenderEntry? = null
    var bestRange: TextRange? = null
    var bestRect: Rectangle? = null
    var bestArea = Long.MAX_VALUE

    for (entry in entries) {
      val range = resolveRange(entry) ?: continue
      val rect = computeHoverRect(range, context) ?: continue
      if (!rect.contains(point)) continue

      val area = rect.width.toLong() * rect.height.toLong()
      if (area < bestArea) {
        bestArea = area
        bestEntry = entry
        bestRange = range
        bestRect = rect
      }
    }

    val entry = bestEntry ?: return null
    val range = bestRange ?: return null
    val rect = bestRect ?: return null

    val element = entry.element ?: return null
    val value = element.value ?: return null
    val presentation = element.presentation
    val text = getText(presentation, value) ?: return null
    val icon = presentation.getIcon(false)

    val declarationWidth = computeDeclarationWidth(range.startOffset, context, snapshot.layoutMetrics)
    return MinimapHoverHitCheckResult(entry, rect, text, icon, declarationWidth)
  }

  fun computeHoverRect(entry: MinimapRenderEntry, context: MinimapRenderContext): Rectangle? {
    val range = ReadAction.computeBlocking<TextRange?, RuntimeException> { resolveRange(entry) } ?: return null
    return computeHoverRect(range, context)
  }

  @RequiresReadLock
  private fun resolveRange(entry: MinimapRenderEntry): TextRange? {
    val element = entry.element ?: return null
    val value = element.value ?: return null
    if (!structureMarkerPolicy.isRelevantStructureElement(element, value)) return null

    return when (value) {
      is PsiElement -> value.textRange
      is TextRange -> value
      else -> null
    }
  }

  private fun computeHoverRect(range: TextRange, context: MinimapRenderContext): Rectangle? {
    val document = editor.document
    val lineProjection = context.lineProjection
    val lineCount = lineProjection.projectedLineCount
    val geometry = context.geometry

    if (lineCount <= 0 || geometry.minimapHeight <= 0 || context.panelWidth <= 0) return null

    val startLine = document.getLineNumber(range.startOffset.coerceAtLeast(0))
    val projectedStartLine = lineProjection.logicalToProjectedLine(startLine) ?: return null

    val endLine = document.getLineNumber(range.endOffset.coerceAtLeast(range.startOffset))
    val projectedEndLine = lineProjection.logicalToProjectedLine(endLine) ?: projectedStartLine
    val endLineExclusive = (projectedEndLine + 1)
      .coerceAtMost(lineCount)
      .coerceAtLeast(projectedStartLine + 1)

    val baseLineHeight = MinimapLineGeometryUtil.baseLineHeight(lineCount, geometry.minimapHeight)
    val lineGap = MinimapLineGeometryUtil.lineGap(baseLineHeight)

    val y1 = lineTop(projectedStartLine, baseLineHeight)
    val y2 = lineTop(endLineExclusive, baseLineHeight)

    val heightPx = ceil(y2 - y1 - lineGap).toInt().coerceAtLeast(1)
    val y = (y1 - geometry.areaStart + lineGap / 2.0).toInt()

    val width = context.panelWidth.coerceAtLeast(1)
    return Rectangle(0, y, width, heightPx)
  }

  fun computeDeclarationWidth(entry: MinimapRenderEntry, context: MinimapRenderContext, metrics: MinimapLayoutMetrics?): Int {
    val range = ReadAction.computeBlocking<TextRange?, RuntimeException> { resolveRange(entry) } ?: return context.panelWidth
    return computeDeclarationWidth(range.startOffset, context, metrics)
  }

  private fun computeDeclarationWidth(startOffset: Int, context: MinimapRenderContext, metrics: MinimapLayoutMetrics?): Int {
    if (metrics == null || metrics.pxPerColumn <= 0.0) return context.panelWidth
    val document = editor.document
    val startLine = document.getLineNumber(startOffset.coerceAtLeast(0))
    val lineStart = document.getLineStartOffset(startLine)
    val lineEnd = document.getLineEndOffset(startLine)
    val trimmedLength = document.charsSequence.subSequence(lineStart, lineEnd).trimEnd().length
    return (metrics.contentStartX + trimmedLength * metrics.pxPerColumn).toInt().coerceIn(1, context.panelWidth)
  }

  private fun getText(presentation: ItemPresentation, value: Any): String? {
    val presentableText = presentation.presentableText?.takeUnless { it.isBlank() }
    if (presentableText != null) return presentableText

    val owner = value as? PsiNameIdentifierOwner ?: return null
    return owner.name?.takeUnless { it.isBlank() }
  }

}
