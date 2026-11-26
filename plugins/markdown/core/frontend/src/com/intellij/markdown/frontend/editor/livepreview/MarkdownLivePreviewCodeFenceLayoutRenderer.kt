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
 * Lays out the code fences of the [MarkdownLivePreviewSpec.CodeFenceLayout] specs like the blocks of the HTML preview.
 * An invisible inline inlay at the start of the language and of each code line pads the text on the left.
 * A fence whose opening marker is not folded shows its source, so it gets no padding.
 *
 * The inlay of a code line belongs to the text before it, so a caret at the line start stays after the padding.
 * The inlay of the language belongs to the text after it, because the folded marker before it would hide the inlay.
 */
internal class MarkdownLivePreviewCodeFenceLayoutRenderer(private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  private val paddings = ArrayList<Inlay<MarkdownLivePreviewCodePaddingInlayRenderer>>()

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> = emptyList()

  override fun documentChanged() = Unit

  /** Runs after the reconciler has applied the fold changes, so the folds show which fences are revealed. */
  @RequiresEdt
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    // The value is true when the inlay belongs to the text before it.
    val wanted = LinkedHashMap<Int, Boolean>()
    for (element in presentation?.elements.orEmpty()) {
      val spec = element.spec as? MarkdownLivePreviewSpec.CodeFenceLayout ?: continue
      if (editor.foldingModel.getCollapsedRegionAtOffset(spec.markerStart)?.startOffset != spec.markerStart) continue
      spec.languageStart?.let { wanted[it] = false }
      spec.codeStarts.forEach { wanted[it] = true }
    }
    // An inlay with the wrong binding is obsolete. Its offset stays wanted, so the loop below adds the right inlay.
    val obsolete = paddings.filter { inlay ->
      val kept = inlay.isValid && wanted[inlay.offset] == inlay.isRelatedToPrecedingText
      if (kept) wanted.remove(inlay.offset)
      !kept
    }
    val changeCount = obsolete.size + wanted.size
    if (changeCount == 0) return
    editor.inlayModel.execute(changeCount > INLAY_BATCH_THRESHOLD) {
      obsolete.forEach(Disposer::dispose)
      paddings.removeAll(obsolete.toSet())
      for ([offset, relatesToPrecedingText] in wanted) {
        paddings += editor.inlayModel.addInlineElement(offset, relatesToPrecedingText, MarkdownLivePreviewCodePaddingInlayRenderer(editor)) ?: continue
      }
    }
  }

  override fun dispose() {
    paddings.forEach(Disposer::dispose)
    paddings.clear()
  }
}

/** Reserves the left padding of the language or of one code line of a code fence. */
@ApiStatus.Internal
class MarkdownLivePreviewCodePaddingInlayRenderer internal constructor(private val editor: Editor) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int = SPACES * EditorUtil.getPlainSpaceWidth(editor)

  private companion object {
    /** Four spaces of the editor font are about 1em, same as the padding of a code block in JCEF preview. */
    private const val SPACES = 4
  }
}
