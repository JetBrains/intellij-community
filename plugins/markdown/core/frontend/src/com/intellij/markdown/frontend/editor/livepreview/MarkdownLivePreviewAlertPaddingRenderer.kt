// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.editor.fence.MarkdownAlertPaddingRenderer
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.jetbrains.annotations.ApiStatus

/**
 * Adds the space of each alert with inlays that no caret reveals, so a revealed marker or title does not move the text.
 * An inline inlay after each marker of an alert moves the text away from the rule.
 * Block inlays add space above the first line, below the title line and below the last line of the alert.
 */
internal class MarkdownLivePreviewAlertPaddingRenderer(private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  private val inlays = LinkedHashMap<Inlay<*>, Space>()

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> = emptyList()

  override fun documentChanged() = Unit

  @RequiresEdt
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    val wanted = LinkedHashSet<SpaceKey>()
    val document = editor.document
    for (element in presentation?.elements.orEmpty()) {
      val quote = element.spec as? MarkdownLivePreviewSpec.BlockQuote
      if (quote?.alertType != null) wanted += SpaceKey(quote.markerRange.endOffset, Space.MARKER)
      val padding = element.spec as? MarkdownLivePreviewSpec.AlertPadding ?: continue
      val first = padding.range.startOffset
      val last = padding.range.endOffset - 1
      wanted += SpaceKey(first, Space.TOP)
      wanted += SpaceKey(last, Space.BOTTOM)
      if (document.getLineNumber(first) != document.getLineNumber(last)) wanted += SpaceKey(first, Space.TITLE)
    }
    val obsolete = ArrayList<Inlay<*>>()
    for ([inlay, space] in inlays) {
      if (!inlay.isValid || !wanted.remove(SpaceKey(inlay.offset, space))) obsolete += inlay
    }
    val changeCount = obsolete.size + wanted.size
    if (changeCount == 0) return
    editor.inlayModel.execute(changeCount > INLAY_BATCH_THRESHOLD) {
      for (inlay in obsolete) {
        inlays.remove(inlay)
        Disposer.dispose(inlay)
      }
      for (key in wanted) {
        val inlay = create(key) ?: continue
        inlays[inlay] = key.space
      }
    }
  }

  override fun dispose() {
    inlays.keys.forEach(Disposer::dispose)
    inlays.clear()
  }

  private fun create(key: SpaceKey): Inlay<*>? = when (key.space) {
    Space.MARKER -> editor.inlayModel.addInlineElement(key.offset, true, MarkdownLivePreviewAlertMarkerInlayRenderer(editor))
    Space.TOP -> addBlock(key.offset, above = true, EDGE_LINE_HEIGHT_RATIO)
    Space.TITLE -> addBlock(key.offset, above = false, TITLE_LINE_HEIGHT_RATIO)
    Space.BOTTOM -> addBlock(key.offset, above = false, EDGE_LINE_HEIGHT_RATIO)
  }

  private fun addBlock(offset: Int, above: Boolean, lineHeightRatio: Double): Inlay<*>? =
    editor.inlayModel.addBlockElement(
      offset,
      InlayProperties().showAbove(above).priority(Int.MIN_VALUE),
      MarkdownAlertPaddingRenderer(editor, lineHeightRatio),
    )

  private data class SpaceKey(val offset: Int, val space: Space)

  private enum class Space { MARKER, TOP, TITLE, BOTTOM }
}

private const val EDGE_LINE_HEIGHT_RATIO = 0.5
private const val TITLE_LINE_HEIGHT_RATIO = 0.4

/** Reserves the space between the rule and the text of an alert line. */
@ApiStatus.Internal
class MarkdownLivePreviewAlertMarkerInlayRenderer internal constructor(private val editor: Editor) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int = MARKER_SPACES * EditorUtil.getPlainSpaceWidth(editor)

  private companion object {
    private const val MARKER_SPACES = 2
  }
}
