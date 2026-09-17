// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.CachingAdaptiveImageManagerService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.svg.AdaptiveImageOrigin
import com.intellij.ui.svg.AdaptiveImageRenderer
import com.intellij.ui.svg.AdaptiveImageRendererEvent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Paints a local image as a block inlay below the line that holds the image source.
 *
 * The platform [AdaptiveImageRenderer] fetches and decodes the pixels behind [imageUrl], and the editor repaints when they arrive.
 */
@ApiStatus.Internal
class MarkdownLivePreviewImageInlayRenderer(
  private val editor: Editor,
  val destination: String,
  val source: MarkdownLivePreviewSpec.ImageSource,
  imageUrl: String,
) : EditorCustomElementRenderer {
  private val image = CachingAdaptiveImageManagerService.getInstance().createRenderer(::onEvent)

  init {
    image.setOrigin(AdaptiveImageOrigin.Url(imageUrl))
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = getDimension().width + 2 * getHorizontalInset()

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = getDimension().height + 2 * getVerticalInset()

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val size = getDimension()
    image.setRenderConfig(size.width.toFloat(), size.height.toFloat(), JBUIScale.sysScale(editor.contentComponent))
    val rendered = image.getRenderedImage() ?: return
    val child = g.create() as? Graphics2D ?: return
    try {
      child.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      child.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      val bounds = Rectangle(targetRegion.x + getHorizontalInset(), targetRegion.y + getVerticalInset(), size.width, size.height)
      StartupUiUtil.drawImage(child, rendered, bounds, null)
    }
    finally {
      child.dispose()
    }
  }

  private fun onEvent(event: AdaptiveImageRendererEvent) {
    if (event is AdaptiveImageRendererEvent.Error) image.setOrigin(null) else editor.contentComponent.repaint()
  }

  /** The drawn size: the intrinsic size, shrunk to the visible width and the maximum height. */
  private fun getDimension(): Dimension {
    val visibleWidth = editor.scrollingModel.visibleArea.width - 2 * getHorizontalInset()
    val maxHeight = MAX_HEIGHT_IN_LINES * editor.lineHeight
    val widthScale = if (visibleWidth > 0) visibleWidth.toDouble() / source.width else 1.0
    val heightScale = if (maxHeight > 0) maxHeight.toDouble() / source.height else 1.0
    val scale = minOf(1.0, widthScale, heightScale)
    return Dimension(max(1, (source.width * scale).roundToInt()), max(1, (source.height * scale).roundToInt()))
  }

  private fun getHorizontalInset(): Int = JBUI.scale(HORIZONTAL_INSET)

  private fun getVerticalInset(): Int = JBUI.scale(VERTICAL_INSET)

  private companion object {
    const val MAX_HEIGHT_IN_LINES = 20
    const val HORIZONTAL_INSET = 2
    const val VERTICAL_INSET = 2
  }
}
