// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D

internal class MarkdownLivePreviewBlockQuoteRenderer(private val editor: Editor) : MarkdownLivePreviewElementRenderer {
  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    val blockQuote = spec as MarkdownLivePreviewSpec.BlockQuote
    return listOf(MarkdownLivePreviewTextFold(
      range = blockQuote.markerRange.toTextRange(),
      placeholderText = blockQuote.placeholderText,
      decoration = BlockQuoteMarkerDecoration(editor, blockQuote.ruleRange.toTextRange()),
    ))
  }

  override fun documentChanged() = Unit
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) = Unit
  override fun dispose() = Unit

  private class BlockQuoteMarkerDecoration(
    private val editor: Editor,
    private val ruleRange: TextRange,
  ) : MarkdownLivePreviewFoldDecoration {
    override fun create(region: FoldRegion): MarkdownLivePreviewMountedDecoration {
      val highlighter = editor.markupModel.addRangeHighlighter(
        null,
        ruleRange.startOffset,
        ruleRange.endOffset,
        HighlighterLayer.ADDITIONAL_SYNTAX,
        HighlighterTargetArea.EXACT_RANGE,
      ).also { it.customRenderer = MarkdownBlockQuotePainter(region) }
      return object : MarkdownLivePreviewMountedDecoration {
        override fun update(decoration: MarkdownLivePreviewFoldDecoration): Boolean {
          return decoration is BlockQuoteMarkerDecoration && highlighter.isValid &&
                 highlighter.startOffset == decoration.ruleRange.startOffset &&
                 highlighter.endOffset == decoration.ruleRange.endOffset
        }

        override fun dispose() = highlighter.dispose()
      }
    }
  }
}

/**
 * Paints the vertical rule of one blockquote marker without changing editor layout or input handling.
 * The rule starts on the marker line at the x position of the marker.
 * A highlighter that ends at a line start stops the rule above that line.
 * The rule takes the foreground of [MarkdownHighlighterColors.BLOCK_QUOTE_MARKER].
 * A placeholder with [FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND] ignores highlighters and shows the editor background.
 * Thus the painter fills each such placeholder on the marker line, from the marker on, with the background of the quote lines.
 * This covers the marker itself and a list bullet in the quote.
 */
@ApiStatus.Internal
class MarkdownBlockQuotePainter internal constructor(private val markerRegion: FoldRegion) : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    if (editor.isDisposed || !highlighter.isValid || !markerRegion.isValid) return
    val markerOffset = markerRegion.startOffset
    val endOffset = highlighter.endOffset
    if (markerOffset >= endOffset) return

    val start = editor.visualLineToY(editor.offsetToVisualPosition(markerOffset).line)
    val end =
      if (DocumentUtil.isAtLineStart(endOffset, editor.document)) editor.visualLineToY(editor.offsetToVisualPosition(endOffset).line)
      else editor.visualLineToY(editor.offsetToVisualPosition(endOffset - 1).line) + editor.lineHeight
    if (start >= end) return

    val x = editor.offsetToXY(markerOffset).x
    val scheme = editor.colorsScheme
    val child = (graphics as? Graphics2D)?.create() as? Graphics2D ?: return
    try {
      scheme.getAttributes(MarkdownHighlighterColors.BLOCK_QUOTE)?.backgroundColor?.let {
        child.color = it
        fillPlaceholders(editor, markerOffset, child)
      }
      child.color = scheme.getAttributes(MarkdownHighlighterColors.BLOCK_QUOTE_MARKER)?.foregroundColor ?: scheme.defaultForeground
      child.fillRect(x, start, JBUI.scale(2), end - start)
    }
    finally {
      child.dispose()
    }
  }

  private fun fillPlaceholders(editor: Editor, markerOffset: Int, graphics: Graphics2D) {
    val foldingModel = editor.foldingModel as? FoldingModelEx ?: return
    val lineEnd = DocumentUtil.getLineEndOffset(markerOffset, editor.document)
    for (region in foldingModel.getRegionsOverlappingWith(markerOffset, lineEnd)) {
      if (region.isExpanded || region.startOffset < markerOffset || !FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND.isIn(region)) continue
      val placeholder = editor.offsetToXY(region.startOffset)
      graphics.fillRect(placeholder.x, placeholder.y, editor.offsetToXY(region.endOffset).x - placeholder.x, editor.lineHeight)
    }
  }
}
