// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.CachingAdaptiveImageManagerService
import com.intellij.icons.AllIcons
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.svg.AdaptiveImageOrigin
import com.intellij.ui.svg.AdaptiveImageRendererEvent
import com.intellij.util.IconUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.SwingTextTrimmer
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.fence.paintBackground
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec.ImageFailure
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Paints an image as a block inlay below the line that holds the image source.
 * When the image can't be shown, the inlay paints a placeholder with the [inlayText].
 *
 * @param source null for a browser-owned image. Its inlay takes no space until the image engine loads the image.
 * @param origin where the image engine gets the pixels, or null when the destination is not a valid image source
 * @param failure the reason the image can't be shown
 */
@ApiStatus.Internal
class MarkdownLivePreviewImageInlayRenderer(
  private val editor: Editor,
  val destination: String,
  val source: MarkdownLivePreviewSpec.ImageSource?,
  origin: AdaptiveImageOrigin?,
  val failure: ImageFailure? = null,
) : EditorCustomElementRenderer {
  private var inlaySize: Dimension? = source?.let { Dimension(it.width, it.height) }
  private val image = CachingAdaptiveImageManagerService.getInstance().createRenderer(::onEvent)

  @ApiStatus.Internal
  var inlayText: @Nls String? = when {
    failure != null -> failureMessage(failure)
    origin == null -> loadFailedMessage()
    else -> null
  }
    private set

  init {
    if (editor.isProjectTrusted() && inlayText == null) {
      image.setOrigin(origin)
      if (source == null) image.getRenderedImage()
    }
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = getDimension()?.let { it.width + 2 * getHorizontalInset() } ?: 0

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = getDimension()?.let { it.height + 2 * getVerticalInset() } ?: 0

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val text = inlayText
    if (text != null) {
      paintPlaceholder(g, targetRegion, text)
      return
    }
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

  private fun paintPlaceholder(g: Graphics, targetRegion: Rectangle, text: @Nls String) {
    val child = g.create() as? Graphics2D ?: return
    try {
      val top = targetRegion.y + getVerticalInset()
      val bottom = targetRegion.y + targetRegion.height - getVerticalInset()
      val shape = paintBackground(editor, child, targetRegion.x, top, bottom) ?: return
      GraphicsUtil.setupRoundedBorderAntialiasing(child)
      child.color = PLACEHOLDER_BORDER_COLOR
      child.draw(shape)
      child.clip(shape)
      val box = shape.bounds

      UISettings.setupAntialiasing(child)
      child.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
      val fontSize = child.font.size2D
      val icon = IconUtil.scaleByFont(AllIcons.General.Warning, editor.contentComponent, fontSize)
      val gap = (JBUIScale.getFontScale(fontSize) * PLACEHOLDER_GAP).roundToInt()
      val metrics = child.fontMetrics
      val shownText = SwingTextTrimmer.ELLIPSIS_IN_CENTER.trim(text, metrics, box.width - icon.iconWidth - 3 * gap)
      val x = box.x + max(gap, (box.width - icon.iconWidth - gap - metrics.stringWidth(shownText)) / 2)
      val centerY = box.y + box.height / 2
      icon.paintIcon(editor.contentComponent, child, x, centerY - icon.iconHeight / 2)
      child.color = NamedColorUtil.getInactiveTextColor()
      child.drawString(shownText, x + icon.iconWidth + gap, centerY + (metrics.ascent - metrics.descent) / 2)
    }
    finally {
      child.dispose()
    }
  }

  private fun onEvent(event: AdaptiveImageRendererEvent) {
    if (!editor.isProjectTrusted()) return
    when (event) {
      is AdaptiveImageRendererEvent.Loaded -> {
        if (inlaySize == null) {
          (val width, val height) = event.dimensions.fallBack
          inlaySize = Dimension(max(1, width.roundToInt()), max(1, height.roundToInt()))
          updateInlay()
        }
        editor.contentComponent.repaint()
      }
      is AdaptiveImageRendererEvent.Error -> {
        image.setOrigin(null)
        inlayText = loadFailedMessage()
        updateInlay()
      }
      is AdaptiveImageRendererEvent.Rasterized, is AdaptiveImageRendererEvent.Unloaded -> editor.contentComponent.repaint()
    }
  }

  private fun updateInlay() {
    val inlay = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength, javaClass).find { it.renderer === this } ?: return
    EditorScrollingPositionKeeper.perform(editor, false) { inlay.update() }
  }

  /**
   * The drawn size: the intrinsic size, shrunk to the visible width and the maximum height. Null while the size is unknown.
   * A placeholder takes the visible width and a fixed height.
   */
  private fun getDimension(): Dimension? {
    val visibleWidth = editor.scrollingModel.visibleArea.width - 2 * getHorizontalInset()
    if (inlayText != null) return Dimension(max(1, visibleWidth), PLACEHOLDER_HEIGHT_IN_LINES * editor.lineHeight)
    if (!editor.isProjectTrusted()) return null
    val size = inlaySize ?: return null
    val maxHeight = MAX_HEIGHT_IN_LINES * editor.lineHeight
    val widthScale = if (visibleWidth > 0) visibleWidth.toDouble() / size.width else 1.0
    val heightScale = if (maxHeight > 0) maxHeight.toDouble() / size.height else 1.0
    val scale = minOf(1.0, widthScale, heightScale)
    return Dimension(max(1, (size.width * scale).roundToInt()), max(1, (size.height * scale).roundToInt()))
  }

  private fun failureMessage(failure: ImageFailure): @Nls String {
    return when (failure) {
      ImageFailure.NOT_FOUND -> MarkdownBundle.message("markdown.live.preview.image.not.found", destination)
      ImageFailure.OUTSIDE_PROJECT -> MarkdownBundle.message("markdown.live.preview.image.outside.project", destination)
      ImageFailure.UNREADABLE -> MarkdownBundle.message("markdown.live.preview.image.unreadable", destination)
      ImageFailure.TOO_LARGE -> MarkdownBundle.message("markdown.live.preview.image.too.large", destination)
      ImageFailure.UNTRUSTED -> MarkdownBundle.message("markdown.live.preview.image.untrusted")
    }
  }

  private fun loadFailedMessage(): @Nls String = MarkdownBundle.message("markdown.live.preview.image.load.failed", destination)

  private fun getHorizontalInset(): Int = JBUI.scale(HORIZONTAL_INSET)

  private fun getVerticalInset(): Int = JBUI.scale(VERTICAL_INSET)

  private fun Editor.isProjectTrusted(): Boolean {
    val project = this.project ?: return false
    return TrustedProjects.isProjectTrusted(project)
  }

  private companion object {
    val PLACEHOLDER_BORDER_COLOR = JBColor.namedColor("Component.borderColor", JBColor(0xC9CCD6, 0x4E5157))

    const val MAX_HEIGHT_IN_LINES = 20
    const val PLACEHOLDER_HEIGHT_IN_LINES = 3
    const val PLACEHOLDER_GAP = 6
    const val HORIZONTAL_INSET = 2
    const val VERTICAL_INSET = 2
  }
}
