// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.jetbrains.annotations.ApiStatus

/**
 * Widens the indent of each nested list line with an invisible inline inlay at the line start.
 * The inlay is not a fold, so a caret at the line start keeps the indent.
 * The soft-wrap engine counts an inline inlay on a leading whitespace character as indent of the original line,
 * so a wrapped continuation line starts after the inlay too.
 */
internal class MarkdownLivePreviewListIndentRenderer(private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  private val inlays = ArrayList<Inlay<MarkdownLivePreviewListIndentInlayRenderer>>()

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> = emptyList()

  override fun documentChanged() = Unit

  @RequiresEdt
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    val wanted = HashMap<Int, Int>()
    for (element in presentation?.elements.orEmpty()) {
      (val range, val depth) = element.spec as? MarkdownLivePreviewSpec.ListIndent ?: continue
      wanted[range.startOffset] = depth
    }
    val obsolete = ArrayList<Inlay<MarkdownLivePreviewListIndentInlayRenderer>>()
    for (inlay in inlays) {
      val depth = if (inlay.isValid) wanted.remove(inlay.offset) else null
      if (depth == null) {
        obsolete += inlay
        continue
      }
      val renderer = inlay.renderer
      renderer.depth = depth
      if (inlay.widthInPixels != renderer.calcWidthInPixels(inlay)) inlay.update()
    }
    val changeCount = obsolete.size + wanted.size
    if (changeCount == 0) return
    editor.inlayModel.execute(changeCount > 100) {
      obsolete.forEach(Disposer::dispose)
      inlays.removeAll(obsolete.toSet())
      for ((key, value) in wanted) {
        val inlay = editor.inlayModel.addInlineElement(
          key, true,
          MarkdownLivePreviewListIndentInlayRenderer(editor, value)
        ) ?: continue
        inlays.add(inlay)
      }
    }
  }

  override fun dispose() {
    inlays.forEach(Disposer::dispose)
    inlays.clear()
  }
}

/** Reserves the extra indent of one nested list line */
@ApiStatus.Internal
class MarkdownLivePreviewListIndentInlayRenderer internal constructor(
  private val editor: Editor,
  depth: Int,
) : EditorCustomElementRenderer {
  /** The nesting depth of the list item */
  var depth: Int = depth
    internal set

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = (depth - 1) * SPACES_PER_LEVEL * EditorUtil.getPlainSpaceWidth(editor)

  private companion object {
    /** Six spaces of the UI font are about 1.5em, so a two-space source level gets about 2em in all, same as JCEF preview. */
    private const val SPACES_PER_LEVEL = 6
  }
}
