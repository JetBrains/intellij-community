// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import java.awt.Graphics
import java.awt.Graphics2D

private const val BLOCK_QUOTE_PLACEHOLDER = "  "

internal class MarkdownLivePreviewBlockQuoteRenderer(private val editor: Editor) : MarkdownLivePreviewElementRenderer {
  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    val blockQuote = spec as MarkdownLivePreviewSpec.BlockQuote
    val range = blockQuote.range.toTextRange()
    return blockQuote.markerRanges.mapIndexed { index, markerRange ->
      MarkdownLivePreviewTextFold(
        range = markerRange.toTextRange(),
        placeholderText = BLOCK_QUOTE_PLACEHOLDER,
        decoration = if (index == 0) BlockQuoteMarkerDecoration(editor, range) else null,
      )
    }
  }

  override fun documentChanged() = Unit
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) = Unit
  override fun dispose() = Unit

  private class BlockQuoteMarkerDecoration(
    private val editor: Editor,
    private val blockQuoteRange: TextRange,
  ) : MarkdownLivePreviewFoldDecoration {
    override fun create(region: FoldRegion): MarkdownLivePreviewMountedDecoration {
      val highlighter = editor.markupModel.addRangeHighlighter(
        null,
        blockQuoteRange.startOffset,
        blockQuoteRange.endOffset,
        HighlighterLayer.ADDITIONAL_SYNTAX,
        HighlighterTargetArea.EXACT_RANGE,
      ).also { it.customRenderer = MarkdownBlockQuotePainter(region) }
      return object : MarkdownLivePreviewMountedDecoration {
        override fun update(decoration: MarkdownLivePreviewFoldDecoration): Boolean {
          return decoration is BlockQuoteMarkerDecoration && highlighter.isValid &&
                 highlighter.startOffset == decoration.blockQuoteRange.startOffset &&
                 highlighter.endOffset == decoration.blockQuoteRange.endOffset
        }

        override fun dispose() = highlighter.dispose()
      }
    }
  }
}

/** Paints a vertical rule without changing editor layout or input handling. */
private class MarkdownBlockQuotePainter(private val markerRegion: FoldRegion) : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    if (editor.isDisposed || !highlighter.isValid || !markerRegion.isValid) return
    val markerOffset = markerRegion.startOffset
    val endOffset = highlighter.endOffset
    if (markerOffset >= endOffset || endOffset <= 0) return

    val startLine = editor.offsetToVisualPosition(markerOffset).line
    val endLine = editor.offsetToVisualPosition((endOffset - 1).coerceAtLeast(markerOffset)).line
    val start = editor.visualLineToY(startLine)
    val end = editor.visualLineToY(endLine) + editor.lineHeight
    if (start >= end) return

    val x = editor.offsetToXY(markerOffset).x
    val color = editor.colorsScheme.getColor(DefaultLanguageHighlighterColors.DOC_COMMENT_GUIDE)
      ?: editor.colorsScheme.getColor(EditorColors.INDENT_GUIDE_COLOR)
      ?: editor.colorsScheme.defaultForeground
    val child = (graphics as? Graphics2D)?.create() as? Graphics2D ?: return
    try {
      child.color = color
      child.fillRect(x, start, JBUI.scale(2), end - start)
    }
    finally {
      child.dispose()
    }
  }
}
