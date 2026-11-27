// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.Icon
import kotlin.math.roundToInt

/** Hides the `[!NOTE]` title of an alert and paints the icon and the label of its type in an inlay after it. */
internal class MarkdownLivePreviewAlertTitleRenderer(private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    (val range, val type) = spec as MarkdownLivePreviewSpec.AlertTitle
    return listOf(MarkdownLivePreviewTextFold(range.toTextRange(), decoration = AlertTitleDecoration(type)))
  }

  override fun documentChanged() = Unit
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) = Unit
  override fun dispose() = Unit

  private inner class AlertTitleDecoration(val type: MarkdownLivePreviewSpec.AlertType) : MarkdownLivePreviewFoldDecoration {
    override fun create(region: FoldRegion): MarkdownLivePreviewMountedDecoration? {
      val renderer = MarkdownLivePreviewAlertTitleInlayRenderer(editor, type)
      val inlay = editor.inlayModel.addInlineElement(region.endOffset, false, renderer)?.takeIf { it.isValid } ?: return null
      return object : MarkdownLivePreviewMountedDecoration {
        override fun update(decoration: MarkdownLivePreviewFoldDecoration): Boolean =
          decoration is AlertTitleDecoration && decoration.type == type &&
          inlay.isValid && region.isValid && inlay.offset == region.endOffset

        override fun dispose() = Disposer.dispose(inlay)
      }
    }
  }
}

/** Paints the icon and the bold label of an alert type in the color of the alert title, as the JCEF preview does. */
@ApiStatus.Internal
class MarkdownLivePreviewAlertTitleInlayRenderer internal constructor(
  private val editor: EditorEx,
  val type: MarkdownLivePreviewSpec.AlertType,
) : EditorCustomElementRenderer {
  private val icon: Icon = MarkdownAlertTitle.AlertType.valueOf(type.name).icon

  val label: @Nls String = MarkdownBundle.message("markdown.preview.alert.title.${type.name.lowercase()}")

  private fun font(): Font = editor.colorsScheme.getFont(EditorFontType.BOLD)

  private fun iconSize(): Int = (editor.lineHeight * ICON_LINE_HEIGHT_RATIO).roundToInt().coerceAtLeast(1)

  private fun gap(): Int = ICON_GAP_SPACES * EditorUtil.getPlainSpaceWidth(editor)

  override fun calcWidthInPixels(inlay: Inlay<*>): Int =
    iconSize() + gap() + editor.contentComponent.getFontMetrics(font()).stringWidth(label)

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val size = iconSize()
    val scale = size.toDouble() / maxOf(icon.iconWidth, icon.iconHeight)
    val iconGraphics = g.create() as? Graphics2D ?: return
    try {
      iconGraphics.translate(targetRegion.x.toDouble(), targetRegion.y + (targetRegion.height - icon.iconHeight * scale) / 2)
      iconGraphics.scale(scale, scale)
      icon.paintIcon(editor.contentComponent, iconGraphics, 0, 0)
    }
    finally {
      iconGraphics.dispose()
    }
    val scheme = editor.colorsScheme
    val textGraphics = g.create() as? Graphics2D ?: return
    try {
      textGraphics.font = font()
      textGraphics.color = scheme.getAttributes(ruleColorKey(type))?.foregroundColor ?: scheme.defaultForeground
      textGraphics.drawString(label, targetRegion.x + size + gap(), targetRegion.y + editor.ascent)
    }
    finally {
      textGraphics.dispose()
    }
  }

  private companion object {
    private const val ICON_LINE_HEIGHT_RATIO = 0.8
    private const val ICON_GAP_SPACES = 2
  }
}
