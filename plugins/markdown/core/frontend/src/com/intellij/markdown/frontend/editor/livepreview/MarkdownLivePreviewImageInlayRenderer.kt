// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.CachingAdaptiveImageManagerService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.svg.AdaptiveImageOrigin
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
 * Paints an image as a block inlay below the line that holds the image source.
 *
 * @param source null for a browser-owned image. Its inlay takes no space until the image engine loads the image.
 * @param origin where the image engine gets the pixels, or null when the destination is not a valid image source
 */
@ApiStatus.Internal
class MarkdownLivePreviewImageInlayRenderer(
  private val editor: Editor,
  val destination: String,
  val source: MarkdownLivePreviewSpec.ImageSource?,
  origin: AdaptiveImageOrigin?,
) : EditorCustomElementRenderer {
  private var inlaySize: Dimension? = source?.let { Dimension(it.width, it.height) }
  private val image = CachingAdaptiveImageManagerService.getInstance().createRenderer(::onEvent)

  init {
    image.setOrigin(origin)
    if (source == null) image.getRenderedImage()
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = getDimension()?.let { it.width + 2 * getHorizontalInset() } ?: 0

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = getDimension()?.let { it.height + 2 * getVerticalInset() } ?: 0

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val size = getDimension() ?: return
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
    if (event is AdaptiveImageRendererEvent.Loaded && inlaySize == null) {
      val size = event.dimensions.fallBack
      inlaySize = Dimension(max(1, size.width.roundToInt()), max(1, size.height.roundToInt()))
      val inlay = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength, javaClass).find { it.renderer === this }
      if (inlay != null) EditorScrollingPositionKeeper.perform(editor, false) { inlay.update() }
    }
    if (event is AdaptiveImageRendererEvent.Error) image.setOrigin(null) else editor.contentComponent.repaint()
  }

  /** The drawn size: the intrinsic size, shrunk to the visible width and the maximum height. Null while the size is unknown. */
  private fun getDimension(): Dimension? {
    val size = inlaySize ?: return null
    val visibleWidth = editor.scrollingModel.visibleArea.width - 2 * getHorizontalInset()
    val maxHeight = MAX_HEIGHT_IN_LINES * editor.lineHeight
    val widthScale = if (visibleWidth > 0) visibleWidth.toDouble() / size.width else 1.0
    val heightScale = if (maxHeight > 0) maxHeight.toDouble() / size.height else 1.0
    val scale = minOf(1.0, widthScale, heightScale)
    return Dimension(max(1, (size.width * scale).roundToInt()), max(1, (size.height * scale).roundToInt()))
  }

  private fun getHorizontalInset(): Int = JBUI.scale(HORIZONTAL_INSET)

  private fun getVerticalInset(): Int = JBUI.scale(VERTICAL_INSET)

  private companion object {
    const val MAX_HEIGHT_IN_LINES = 20
    const val HORIZONTAL_INSET = 2
    const val VERTICAL_INSET = 2
  }
}
