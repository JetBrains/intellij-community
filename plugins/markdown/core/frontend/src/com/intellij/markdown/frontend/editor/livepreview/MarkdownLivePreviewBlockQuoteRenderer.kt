// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.ui.Gray
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.editor.fence.getAlertPaddingBottom
import org.intellij.plugins.markdown.editor.fence.getAlertPaddingTop
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import kotlin.math.roundToInt

/**
 * Hides the glyph of each blockquote marker that no caret touches and paints its rule.
 * The marker stays in the text and keeps its width.
 */
internal class MarkdownLivePreviewBlockQuoteRenderer(private val editor: Editor) : MarkdownLivePreviewElementRenderer {
  private var quotes = emptyList<ConcealedQuote>()

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> = emptyList()
  override fun documentChanged() = Unit

  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    val carets = editor.caretModel.allCarets
    val missing = LinkedHashSet<QuoteKey>()
    for (element in presentation?.elements.orEmpty()) {
      val spec = element.spec as? MarkdownLivePreviewSpec.BlockQuote ?: continue
      (val startOffset, val endOffset) = spec.range

      if (carets.any { it.selectionStart <= endOffset && startOffset <= it.selectionEnd }) continue
      missing.add(QuoteKey(spec.markerRange.toTextRange(), spec.ruleRange.toTextRange(), spec.alertType))
    }

    val kept = ArrayList<ConcealedQuote>()
    for (quote in quotes) {
      val key = quote.key()
      if (key != null && missing.remove(key)) kept.add(quote) else quote.dispose()
    }
    missing.mapTo(kept) { ConcealedQuote(it) }
    quotes = kept
  }

  override fun dispose() {
    quotes.forEach(ConcealedQuote::dispose)
    quotes = emptyList()
  }

  private data class QuoteKey(val marker: TextRange, val rule: TextRange, val alertType: MarkdownLivePreviewSpec.AlertType?)

  private inner class ConcealedQuote(key: QuoteKey) {
    private val alertType = key.alertType
    private val marker = editor.markupModel.addRangeHighlighter(
      key.marker.startOffset, key.marker.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX + 1, HIDDEN_GLYPH, HighlighterTargetArea.EXACT_RANGE,
    )
    private val rule = editor.markupModel.addRangeHighlighter(
      null, key.rule.startOffset, key.rule.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE,
    ).also { it.customRenderer = MarkdownBlockQuotePainter(alertType) }

    fun key(): QuoteKey? =
      if (marker.isValid && rule.isValid) QuoteKey(marker.textRange, rule.textRange, alertType) else null

    fun dispose() {
      marker.dispose()
      rule.dispose()
    }
  }
}

private val HIDDEN_GLYPH = TextAttributes(Gray.TRANSPARENT, null, null, null, Font.PLAIN)

internal fun ruleColorKey(alertType: MarkdownLivePreviewSpec.AlertType?): TextAttributesKey = when (alertType) {
  MarkdownLivePreviewSpec.AlertType.NOTE -> MarkdownHighlighterColors.ALERT_TITLE_NOTE
  MarkdownLivePreviewSpec.AlertType.TIP -> MarkdownHighlighterColors.ALERT_TITLE_TIP
  MarkdownLivePreviewSpec.AlertType.IMPORTANT -> MarkdownHighlighterColors.ALERT_TITLE_IMPORTANT
  MarkdownLivePreviewSpec.AlertType.WARNING -> MarkdownHighlighterColors.ALERT_TITLE_WARNING
  MarkdownLivePreviewSpec.AlertType.CAUTION -> MarkdownHighlighterColors.ALERT_TITLE_CAUTION
  null -> MarkdownHighlighterColors.BLOCK_QUOTE_MARKER
}

/**
 * Paints the vertical rule of one blockquote marker without changing editor layout or input handling.
 * The highlighter starts at the marker. The rule starts on the marker line at the x position of the marker.
 * A highlighter that ends at a line start stops the rule above that line.
 * The rule of an alert also covers the alert padding above and below the marker line.
 * The rule takes the foreground of [ruleColorKey].
 */
@ApiStatus.Internal
class MarkdownBlockQuotePainter internal constructor(
  private val alertType: MarkdownLivePreviewSpec.AlertType?,
) : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    if (editor.isDisposed || !highlighter.isValid) return
    val markerOffset = highlighter.startOffset
    val endOffset = highlighter.endOffset
    if (markerOffset >= endOffset) return

    val lineTop = editor.visualLineToY(editor.offsetToVisualPosition(markerOffset).line)
    val start = if (alertType == null) lineTop else getAlertPaddingTop(editor, markerOffset, markerOffset) ?: lineTop
    val end = if (DocumentUtil.isAtLineStart(endOffset, editor.document)) {
      editor.visualLineToY(editor.offsetToVisualPosition(endOffset).line)
    }
    else {
      val lineBottom = editor.visualLineToYRange(editor.offsetToVisualPosition(endOffset - 1).line)[1]
      if (alertType == null) lineBottom else getAlertPaddingBottom(editor, markerOffset, markerOffset) ?: lineBottom
    }
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
      child.color = scheme.getAttributes(ruleColorKey(alertType))?.foregroundColor ?: scheme.defaultForeground
      child.fillRect(x, start, JBUI.scale(3), end - start)
    }
    finally {
      child.dispose()
    }
  }
}

internal fun getQuotePrefixWidth(editor: Editor, lineStart: Int, end: Int): Int {
  val text = editor.document.charsSequence
  return (lineStart until end)
    .sumOf {
      val char = if (text[it] == '\t') ' ' else text[it]
      EditorUtil.fontForChar(char, Font.PLAIN, editor).charWidth2D(char.code).toDouble()
    }
    .roundToInt()
}