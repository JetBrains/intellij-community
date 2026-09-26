// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.paint.LinePainter2D
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import java.awt.Graphics
import java.awt.Graphics2D

internal class MarkdownLivePreviewHorizontalRuleRenderer(private val editor: Editor) : MarkdownLivePreviewFoldDecoration, MarkdownLivePreviewElementRenderer {
  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> =
    listOf(MarkdownLivePreviewTextFold(spec.range.toTextRange(), decoration = this))

  override fun documentChanged() = Unit
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) = Unit
  override fun dispose() = Unit

  override fun create(region: FoldRegion): MarkdownLivePreviewMountedDecoration {
    val highlighter = editor.markupModel.addRangeHighlighter(
      MarkdownHighlighterColors.HRULE, region.startOffset, region.endOffset,
      HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE,
    )
    highlighter.customRenderer = MarkdownHorizontalRulePainter
    return object : MarkdownLivePreviewMountedDecoration {
      override fun update(decoration: MarkdownLivePreviewFoldDecoration): Boolean {
        return decoration === this@MarkdownLivePreviewHorizontalRuleRenderer && highlighter.isValid
      }

      override fun dispose() {
        highlighter.dispose()
      }
    }
  }
}

/** Paints a rule across the viewport without changing editor layout or input handling. */
private object MarkdownHorizontalRulePainter : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    if (editor.isDisposed || !highlighter.isValid) return
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.width <= 0) return
    val visualLine = editor.offsetToVisualPosition(highlighter.startOffset).line
    val y = editor.visualLineToY(visualLine) + editor.lineHeight / 2
    val scheme = editor.colorsScheme
    val color = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
      ?: highlighter.getTextAttributes(scheme)?.foregroundColor
      ?: scheme.defaultForeground
    val child = (graphics as? Graphics2D)?.create() as? Graphics2D ?: return
    try {
      child.color = color
      LinePainter2D.paint(
        child, visibleArea.x.toDouble(), y.toDouble(),
        (visibleArea.x + visibleArea.width).toDouble(), y.toDouble()
      )
    }
    finally {
      child.dispose()
    }
  }
}
