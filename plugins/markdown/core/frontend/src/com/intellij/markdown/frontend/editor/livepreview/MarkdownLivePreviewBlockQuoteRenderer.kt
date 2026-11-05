// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.EditorUtil
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
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import kotlin.math.roundToInt

internal class MarkdownLivePreviewBlockQuoteRenderer(private val editor: Editor) : MarkdownLivePreviewElementRenderer {
  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    val blockQuote = spec as MarkdownLivePreviewSpec.BlockQuote
    return listOf(MarkdownLivePreviewTextFold(
      range = blockQuote.markerRange.toTextRange(),
      placeholderText = blockQuote.placeholderText,
      decoration = BlockQuoteMarkerDecoration(
        editor,
        blockQuote.ruleRange.toTextRange(),
        ruleColorKey(blockQuote.alertType),
      ),
    ))
  }

  private fun ruleColorKey(alertType: MarkdownLivePreviewSpec.AlertType?): TextAttributesKey = when (alertType) {
    MarkdownLivePreviewSpec.AlertType.NOTE -> MarkdownHighlighterColors.ALERT_TITLE_NOTE
    MarkdownLivePreviewSpec.AlertType.TIP -> MarkdownHighlighterColors.ALERT_TITLE_TIP
    MarkdownLivePreviewSpec.AlertType.IMPORTANT -> MarkdownHighlighterColors.ALERT_TITLE_IMPORTANT
    MarkdownLivePreviewSpec.AlertType.WARNING -> MarkdownHighlighterColors.ALERT_TITLE_WARNING
    MarkdownLivePreviewSpec.AlertType.CAUTION -> MarkdownHighlighterColors.ALERT_TITLE_CAUTION
    null -> MarkdownHighlighterColors.BLOCK_QUOTE_MARKER
  }

  override fun documentChanged() = Unit
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) = Unit
  override fun dispose() = Unit

  private class BlockQuoteMarkerDecoration(
    private val editor: Editor,
    private val ruleRange: TextRange,
    private val ruleColorKey: TextAttributesKey,
  ) : MarkdownLivePreviewFoldDecoration {
    override fun create(region: FoldRegion): MarkdownLivePreviewMountedDecoration {
      val highlighter = editor.markupModel.addRangeHighlighter(
        null,
        ruleRange.startOffset,
        ruleRange.endOffset,
        HighlighterLayer.ADDITIONAL_SYNTAX,
        HighlighterTargetArea.EXACT_RANGE,
      ).also { it.customRenderer = MarkdownBlockQuotePainter(region, ruleColorKey) }
      return object : MarkdownLivePreviewMountedDecoration {
        override fun update(decoration: MarkdownLivePreviewFoldDecoration): Boolean {
          return decoration is BlockQuoteMarkerDecoration && highlighter.isValid && decoration.ruleColorKey == ruleColorKey &&
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
 * The rule takes the foreground of [ruleColorKey].
 */
@ApiStatus.Internal
class MarkdownBlockQuotePainter internal constructor(
  private val markerRegion: FoldRegion,
  private val ruleColorKey: TextAttributesKey,
) : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    if (editor.isDisposed || !highlighter.isValid || !markerRegion.isValid) return
    val markerOffset = markerRegion.startOffset
    val endOffset = highlighter.endOffset
    if (markerOffset >= endOffset) return

    val start = editor.visualLineToY(editor.offsetToVisualPosition(markerOffset).line)
    val end =
      if (DocumentUtil.isAtLineStart(endOffset, editor.document)) editor.visualLineToY(editor.offsetToVisualPosition(endOffset).line)
      else editor.visualLineToYRange(editor.offsetToVisualPosition(endOffset - 1).line)[1]
    if (start >= end) return

    val offset = if (editor.foldingModel.getCollapsedRegionAtOffset(markerOffset) !is CustomFoldRegion) {
      0
    } else {
      getQuotePrefixWidth(editor, DocumentUtil.getLineStartOffset(markerOffset, editor.document), markerOffset)
    }
    val x = editor.offsetToXY(markerOffset).x + offset
    val scheme = editor.colorsScheme
    val child = (graphics as? Graphics2D)?.create() as? Graphics2D ?: return
    try {
      child.color = scheme.getAttributes(ruleColorKey)?.foregroundColor ?: scheme.defaultForeground
      child.fillRect(x, start, JBUI.scale(3), end - start)
    }
    finally {
      child.dispose()
    }
  }
}

internal fun getQuotePrefixWidth(editor: Editor, lineStart: Int, end: Int): Int {
  val text = editor.document.charsSequence
  val columns = (lineStart until end)
    .sumOf {
      if (text[it] == '>' && text.getOrNull(it + 1)?.let { next -> next in " \t" } != true) 2
      else 1
    }
  return (columns * EditorUtil.fontForChar(' ', Font.PLAIN, editor).charWidth2D(' '.code)).roundToInt()
}