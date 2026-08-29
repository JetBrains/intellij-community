// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import java.util.LinkedHashMap
import java.util.SequencedMap

/** Keeps the editor position stable while Markdown live preview regions change. */
internal class MarkdownLivePreviewPositionKeeper(private val editor: Editor) : DocRenderer.PositionKeeper {

  private var viewportShift = 0
  private var marker: RangeMarker? = null
  private var caretWasVisible = false
  private var imageLayout: SequencedMap<TextRange, Int> = LinkedHashMap()

  override fun save() {
    marker?.dispose()
    marker = null

    val visibleArea = editor.scrollingModel.visibleAreaOnScrollingFinished
    val caretY = editor.visualLineToY(editor.caretModel.visualPosition.line)
    val caretOutside = visibleArea.height > 0 && (caretY + editor.lineHeight <= visibleArea.y || caretY >= visibleArea.y + visibleArea.height)
    caretWasVisible = visibleArea.height > 0 && !caretOutside

    if (caretOutside) {
      val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(visibleArea.location))
      marker = editor.document.createRangeMarker(offset, offset)
      viewportShift = editor.offsetToXY(offset).y - visibleArea.y
    }
    else {
      viewportShift = caretY - visibleArea.y
    }

    imageLayout = editor.getAllCurrentImageLayout()
  }

  override fun restore() {
    val marker = this.marker
    if (marker != null && !marker.isValid) return

    val scrollingModel = editor.scrollingModel
    scrollingModel.disableAnimation()
    try {
      if (caretWasVisible && imageLayout != editor.getAllCurrentImageLayout()) {
        scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        return
      }

      val newY = if (marker == null) {
        editor.visualLineToY(editor.caretModel.visualPosition.line)
      }
      else {
        editor.offsetToXY(marker.startOffset).y
      }
      val targetArea = scrollingModel.visibleAreaOnScrollingFinished
      scrollingModel.scroll(targetArea.x, newY - viewportShift)
    }
    finally {
      scrollingModel.enableAnimation()
    }
  }

  override fun dispose() {
    marker?.dispose()
    marker = null
  }

  companion object {
    fun perform(editor: Editor, callback: () -> Unit) {
      val keeper = MarkdownLivePreviewPositionKeeper(editor)
      keeper.save()
      try {
        callback()
        keeper.restore()
      }
      finally {
        Disposer.dispose(keeper)
      }
    }
  }
}
