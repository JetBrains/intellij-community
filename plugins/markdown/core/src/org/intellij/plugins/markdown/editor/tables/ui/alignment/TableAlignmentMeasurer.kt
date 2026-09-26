// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

/** Measures rendered rows while excluding alignment padding and retaining foreign inlays. */
@ApiStatus.Internal
fun measureRow(editor: Editor, borderOffsets: IntArray): RowSegments? {
  if (borderOffsets.size < 2) {
    return null
  }
  val inlays = editor.inlayModel.getInlineElementsInRange(
    borderOffsets.first(),
    borderOffsets.last(),
  )
  val xs = IntArray(borderOffsets.size)
  var rowY = 0
  var inlayIndex = 0
  var padWidthSoFar = 0
  for ((index, offset) in borderOffsets.withIndex()) {
    // offsetToXY() includes inlays to the left, but not at the queried offset.
    while (inlayIndex < inlays.size && inlays[inlayIndex].offset < offset) {
      val inlay = inlays[inlayIndex++]
      if (inlay.renderer is TablePadRenderer) {
        padWidthSoFar += inlay.widthInPixels
      }
    }
    val point = editor.offsetToXY(offset, false, false)
    if (index == 0) {
      rowY = point.y
    }
    else if (point.y != rowY) {
      return null
    }
    var foreignWidthAtOffset = 0
    var offsetInlayIndex = inlayIndex
    while (offsetInlayIndex < inlays.size && inlays[offsetInlayIndex].offset == offset) {
      val inlay = inlays[offsetInlayIndex++]
      if (inlay.renderer !is TablePadRenderer) {
        foreignWidthAtOffset += inlay.widthInPixels
      }
    }
    xs[index] = point.x + foreignWidthAtOffset - padWidthSoFar
  }
  return RowSegments(
    originX = xs.first(),
    segmentWidths = (0 until xs.size - 1).map { xs[it + 1] - xs[it] },
  )
}
