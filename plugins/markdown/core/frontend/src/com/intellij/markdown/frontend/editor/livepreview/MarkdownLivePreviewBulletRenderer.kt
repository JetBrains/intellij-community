// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

/** Hides a list marker and paints its bullet in an inlay after the marker. */
internal class MarkdownLivePreviewBulletRenderer(private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    val bullet = spec as MarkdownLivePreviewSpec.Bullet
    return listOf(MarkdownLivePreviewTextFold(bullet.range.toTextRange(), decoration = BulletDecoration(bullet.placeholderText)))
  }

  override fun documentChanged() = Unit
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) = Unit
  override fun dispose() = Unit

  private inner class BulletDecoration(val glyph: String) : MarkdownLivePreviewFoldDecoration {
    override fun create(region: FoldRegion): MarkdownLivePreviewMountedDecoration? {
      val renderer = MarkdownLivePreviewBulletInlayRenderer(editor, glyph)
      val inlay = editor.inlayModel.addInlineElement(region.endOffset, false, renderer)?.takeIf { it.isValid } ?: return null
      return object : MarkdownLivePreviewMountedDecoration {
        override fun update(decoration: MarkdownLivePreviewFoldDecoration): Boolean =
          decoration is BulletDecoration && decoration.glyph == glyph && inlay.isValid && region.isValid && inlay.offset == region.endOffset

        override fun dispose() = Disposer.dispose(inlay)
      }
    }
  }
}

/** Paints a list bullet in the list marker color of the editor scheme, or in the selection foreground color in a selection. */
@ApiStatus.Internal
class MarkdownLivePreviewBulletInlayRenderer internal constructor(
  private val editor: EditorEx,
  val glyph: String,
) : EditorCustomElementRenderer {
  private fun fontInfo(): FontInfo = EditorUtil.fontForChar(glyph[0], Font.PLAIN, editor)

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = fontInfo().fontMetrics().stringWidth(glyph).coerceAtLeast(1)

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val scheme = editor.colorsScheme
    val child = g.create() as? Graphics2D ?: return
    try {
      child.font = fontInfo().font
      child.color = selectionForeground(inlay)
                    ?: scheme.getAttributes(MarkdownHighlighterColors.LIST_MARKER)?.foregroundColor
                    ?: scheme.defaultForeground
      child.drawString(glyph, targetRegion.x, targetRegion.y + editor.ascent)
    }
    finally {
      child.dispose()
    }
  }

  private fun selectionForeground(inlay: Inlay<*>): Color? {
    val color = editor.colorsScheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR) ?: return null
    val offset = inlay.offset
    return color.takeIf { editor.caretModel.allCarets.any { it.hasSelection() && offset > it.selectionStart && offset < it.selectionEnd } }
  }
}
