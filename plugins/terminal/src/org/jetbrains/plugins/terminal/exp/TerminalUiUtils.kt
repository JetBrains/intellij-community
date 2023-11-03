// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.MathUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.AwtTransformers
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.Color16.Companion.toColor16
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import javax.swing.JScrollPane
import kotlin.math.max

object TerminalUiUtils {
  fun createOutputEditor(document: Document, project: Project, settings: JBTerminalSystemSettingsProviderBase): EditorImpl {
    val editor = EditorFactory.getInstance().createEditor(document, project, EditorKind.CONSOLE) as EditorImpl
    editor.isScrollToCaret = false
    editor.isRendererMode = true
    editor.setCustomCursor(this, Cursor.getDefaultCursor())
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    editor.gutterComponentEx.isPaintBackground = false
    editor.gutterComponentEx.setRightFreePaintersAreaState(EditorGutterFreePainterAreaState.HIDE)

    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = 1.0f
    }

    editor.settings.apply {
      isShowingSpecialChars = false
      isLineNumbersShown = false
      setGutterIconsShown(false)
      isRightMarginShown = false
      isFoldingOutlineShown = false
      isCaretRowShown = false
      additionalLinesCount = 0
      additionalColumnsCount = 0
      isBlockCursor = true
    }

    editor.installPopupHandler(object:ContextMenuPopupHandler() {
      override fun getActionGroup(event: EditorMouseEvent) =
        ActionManager.getInstance().getAction("Terminal.PopupMenu") as ActionGroup})
    return editor
  }

  fun calculateTerminalSize(componentSize: Dimension, charSize: Dimension): TermSize {
    val width = componentSize.width / charSize.width
    val height = componentSize.height / charSize.height
    return ensureTermMinimumSize(TermSize(width, height))
  }

  private fun ensureTermMinimumSize(size: TermSize): TermSize {
    return TermSize(max(TerminalModel.MIN_WIDTH, size.columns), max(TerminalModel.MIN_HEIGHT, size.rows))
  }

  @RequiresEdt(generateAssertion = false)
  fun awaitComponentLayout(component: Component, parentDisposable: Disposable): CompletableFuture<Unit> {
    val size = component.size
    if (size.width > 0 || size.height > 0) {
      return CompletableFuture.completedFuture(Unit)
    }
    if (!UIUtil.isShowing(component, false)) {
      return CompletableFuture.failedFuture(IllegalStateException("component should be showing"))
    }
    val result = CompletableFuture<Unit>()

    val startNano = System.nanoTime()
    val resizeListener: ComponentAdapter = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        if (LOG.isDebugEnabled) {
          LOG.info("Terminal component layout took " + TimeoutUtil.getDurationMillis(startNano) + "ms")
        }
        result.complete(Unit)
      }
    }
    component.addComponentListener(resizeListener)

    val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    alarm.addRequest({
                       result.completeExceptionally(IllegalStateException("Terminal component layout is timed out (>${TIMEOUT}ms)"))
                     }, TIMEOUT, ModalityState.stateForComponent(component))

    Disposer.register(alarm) {
      if (!result.isDone) {
        ApplicationManager.getApplication().invokeLater({
                                                          result.completeExceptionally(IllegalStateException("parent disposed"))
                                                        }, ModalityState.stateForComponent(component))
      }
    }
    result.whenComplete { _, _ ->
      Disposer.dispose(alarm)
      component.removeComponentListener(resizeListener)
    }

    return result
  }

  fun toFloatAndScale(value: Int): Float = JBUIScale.scale(value.toFloat())

  internal fun TextStyle.toTextAttributes(palette: TerminalColorPalette): TextAttributes {
    return TextAttributes().also { attr ->
      backgroundForRun?.let {
        // [TerminalColorPalette.getDefaultBackground] is not applied to [TextAttributes].
        // It's passed to [EditorEx.setBackgroundColor] / [JComponent.setBackground] to
        // paint the background uniformly.
        attr.backgroundColor = AwtTransformers.toAwtColor(palette.getBackground(it))
      }
      attr.foregroundColor = getForegroundColor(this, palette)
      if (hasOption(TextStyle.Option.BOLD)) {
        attr.fontType = attr.fontType or Font.BOLD
      }
      if (hasOption(TextStyle.Option.ITALIC)) {
        attr.fontType = attr.fontType or Font.ITALIC
      }
      if (hasOption(TextStyle.Option.UNDERLINED)) {
        attr.withAdditionalEffect(EffectType.LINE_UNDERSCORE, attr.foregroundColor)
      }
    }
  }

  private fun getForegroundColor(style: TextStyle, palette: TerminalColorPalette): Color {
    val foreground = getEffectiveForegroundColor(style.foregroundForRun, palette)
    return if (style.hasOption(TextStyle.Option.DIM)) {
      val background = getEffectiveBackgroundColor(style.backgroundForRun, palette)
      @Suppress("UseJBColor")
      Color((foreground.red + background.red) / 2,
            (foreground.green + background.green) / 2,
            (foreground.blue + background.blue) / 2,
            foreground.alpha)
    }
    else foreground
  }

  private fun getEffectiveForegroundColor(color: TerminalColor?, palette: TerminalColorPalette): Color {
    return AwtTransformers.toAwtColor(color?.let { palette.getForeground(it) } ?: palette.defaultForeground)!!
  }

  private fun getEffectiveBackgroundColor(color: TerminalColor?, palette: TerminalColorPalette): Color {
    return AwtTransformers.toAwtColor(color?.let { palette.getBackground(it) } ?: palette.defaultBackground)!!
  }

  /**
   * Creates a horizontal gradient texture of the provided [width].
   * The returned gradient does not suffer from the banding problem because it is created using 16 bits for each channel (argb)
   * and then mapped to the 8 bits using Floyd–Steinberg dithering algorithm.
   *
   * The returned texture consists of a long smooth gradient, but the height of it is small - only 2px.
   * The real height depends on the [JBUIScale.sysScale], for example, with 2.0 scale, it will be 4px.
   * But even when the height is small, the calculation is still quite heavy, and it should not be calculated during each paint.
   * Better to calculate it once, store, and then recalculate only when required [width] is changed.
   */
  fun createHorizontalGradientTexture(graphics: Graphics,
                                      colorStart: Color,
                                      colorEnd: Color,
                                      width: Int): TexturePaint {
    val imgHeight = 2
    val image = ImageUtil.createImage(graphics, width, imgHeight, BufferedImage.TYPE_INT_ARGB)

    val pixels: Array<Array<Color16>> = Array(image.height) { Array(image.width) { Color16.TRANSPARENT } }

    val colorStart16 = colorStart.toColor16()
    val colorEnd16 = colorEnd.toColor16()
    val delta16 = colorEnd16 - colorStart16
    for (x in 0 until image.width) {
      val rel = x * 1.0 / (image.width - 1)
      val curColor = colorStart16 + delta16 * rel
      for (y in 0 until image.height) {
        pixels[y][x] = curColor
      }
    }

    val coefficients = doubleArrayOf(7.0 / 16, 3.0 / 16, 5.0 / 16, 1.0 / 16)
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val oldColor: Color16 = pixels[y][x]
        val newColor: Color = oldColor.toColor8()
        image.setRGB(x, y, newColor.rgb)

        val error: Color16 = oldColor - newColor.toColor16()
        if (x + 1 < image.width) {
          pixels[y][x + 1] = pixels[y][x + 1] + error * coefficients[0]
        }
        if (x - 1 >= 0 && y + 1 < image.height) {
          pixels[y + 1][x - 1] = pixels[y + 1][x - 1] + error * coefficients[1]
        }
        if (y + 1 < image.height) {
          pixels[y + 1][x] = pixels[y + 1][x] + error * coefficients[2]
        }
        if (x + 1 < image.width && y + 1 < image.height) {
          pixels[y + 1][x + 1] = pixels[y + 1][x + 1] + error * coefficients[3]
        }
      }
    }

    return TexturePaint(image, Rectangle(0, 0, width, imgHeight))
  }

  /**
   * Note that there are no checks for overflows in the operators' implementation. It is intended.
   */
  private data class Color16(val red: Int, val green: Int, val blue: Int, val alpha: Int) {
    @Suppress("UseJBColor")
    fun toColor8(): Color {
      return Color(to8bit(red), to8bit(green), to8bit(blue), to8bit(alpha))
    }

    private fun to8bit(value: Int): Int {
      val result = value / 256 + if (value % 256 >= 128) 1 else 0
      return MathUtil.clamp(result, 0, 255)
    }

    operator fun minus(other: Color16): Color16 {
      return Color16(red - other.red, green - other.green, blue - other.blue, alpha - other.alpha)
    }

    operator fun plus(other: Color16): Color16 {
      return Color16(red + other.red, green + other.green, blue + other.blue, alpha + other.alpha)
    }

    operator fun times(multiplier: Double): Color16 {
      return Color16((red * multiplier).toInt(), (green * multiplier).toInt(), (blue * multiplier).toInt(), (alpha * multiplier).toInt())
    }

    companion object {
      val TRANSPARENT: Color16 = Color16(0, 0, 0, 0)

      fun Color.toColor16(): Color16 {
        return Color16(red * 256, green * 256, blue * 256, alpha * 256)
      }
    }
  }

  private val LOG = logger<TerminalUiUtils>()
  private const val TIMEOUT = 2000
}