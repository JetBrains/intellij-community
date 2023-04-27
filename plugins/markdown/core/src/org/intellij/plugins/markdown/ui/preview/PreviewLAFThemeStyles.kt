// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.namedColor
import com.intellij.ui.components.ScrollBarPainter
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color

internal object PreviewLAFThemeStyles {
  /**
   * This method will generate stylesheet with colors and other attributes matching current LAF settings of the IDE.
   * Generated CSS will override base rules from the default.css, so the preview elements will have correct colors.

   * @return String containing generated CSS rules.
   */
  @JvmStatic
  fun createStylesheet(): String {
    val scheme = obtainColorsScheme()
    val contrastedForeground = scheme.defaultForeground.contrast(0.1)

    val panelBackground = UIUtil.getPanelBackground()

    val linkActiveForeground = JBUI.CurrentTheme.Link.Foreground.ENABLED
    val separatorColor = namedColor("Group.separatorColor", panelBackground).webRgba()
    val infoForeground = namedColor("Component.infoForeground", contrastedForeground).webRgba()

    val markdownFenceBackground = JBColor(Color(212, 222, 231, 255 / 4), Color(212, 222, 231, 25))

    val fontSize = JBCefApp.normalizeScaledSize(EditorUtil.getEditorFont().size + 1)

    val scrollbarBackgroundColor = scheme.getScrollbarColor(ScrollBarPainter.BACKGROUND)
    val scrollbarTrackColor = scheme.getScrollbarColor(ScrollBarPainter.TRACK_OPAQUE_BACKGROUND)
    val scrollbarTrackColorHovered = scheme.getScrollbarColor(ScrollBarPainter.TRACK_OPAQUE_HOVERED_BACKGROUND)
    val scrollbarThumbColor = scheme.getScrollbarColor(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND)
    val scrollbarThumbColorHovered = scheme.getScrollbarColor(ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND)
    val scrollbarThumbBorder = scheme.getScrollbarColor(ScrollBarPainter.THUMB_OPAQUE_FOREGROUND)
    val scrollbarThumbBorderHovered = scheme.getScrollbarColor(ScrollBarPainter.THUMB_OPAQUE_HOVERED_FOREGROUND)

    val scale = UISettingsUtils.instance.currentIdeScale
    val scrollbarTrackSizePx = JBCefApp.normalizeScaledSize((if (SystemInfo.isMac) 14 else 10)) * scale
    val scrollbarThumbBorderSizePx = JBCefApp.normalizeScaledSize( (if (SystemInfo.isMac) 3 else 1)) * scale
    val scrollbarThumbRadiusPx = JBCefApp.normalizeScaledSize((if (SystemInfo.isMac) 14 else 0)) * scale
    UISettingsUtils.instance.currentIdeScale

    val backgroundColor = scheme.defaultBackground.webRgba()
    // language=CSS
    return """
    body {
        background-color: ${backgroundColor};
        font-size: ${fontSize}px !important;
    }
    
    body, p, blockquote, ul, ol, dl, table, pre, code, tr  {
        color: ${scheme.defaultForeground.webRgba()};
    }
    
    a {
        color: ${linkActiveForeground.webRgba()};
    }
    
    table td, table th {
      border: 1px solid $separatorColor;
    }
    
    hr {
      background-color: $separatorColor;
    }
    
    kbd, tr {
      border: 1px solid $separatorColor;
    }
    
    h6 {
        color: $infoForeground;
    }
    
    blockquote {
      border-left: 2px solid ${linkActiveForeground.webRgba(alpha = 0.4)};
    }
    
    blockquote, code, pre {
      background-color: ${markdownFenceBackground.webRgba(markdownFenceBackground.alpha / 255.0)};
    }
    
    ::-webkit-scrollbar {
      width: ${scrollbarTrackSizePx}px;
      height: ${scrollbarTrackSizePx}px;
      background-color: $scrollbarBackgroundColor;
    }
    
    ::-webkit-scrollbar-track {
      background-color:$scrollbarTrackColor;
    }
    
    ::-webkit-scrollbar-track:hover {
      background-color:$scrollbarTrackColorHovered;
    }
    
    ::-webkit-scrollbar-thumb {
      background-color:$scrollbarThumbColor;
      border-radius:${scrollbarThumbRadiusPx}px;
      border-width: ${scrollbarThumbBorderSizePx}px;
      border-style: solid;
      border-color: $scrollbarTrackColor;
      background-clip: padding-box;
      outline: 1px solid $scrollbarThumbBorder;
      outline-offset: -${scrollbarThumbBorderSizePx}px;
    }
    
    ::-webkit-scrollbar-thumb:hover {
      background-color:$scrollbarThumbColorHovered;
      border-radius:${scrollbarThumbRadiusPx}px;
      border-width: ${scrollbarThumbBorderSizePx}px;
      border-style: solid;
      border-color: $scrollbarTrackColor;
      background-clip: padding-box;
      outline: 1px solid $scrollbarThumbBorderHovered;
      outline-offset: -${scrollbarThumbBorderSizePx}px;
    }
    
    ::-webkit-scrollbar-button {
      display:none;
    }
    
    ::-webkit-scrollbar-corner {
      background-color: $scrollbarBackgroundColor;
    }
    """.trimIndent()
  }

  private fun obtainColorsScheme(): EditorColorsScheme {
    val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
    val activeScheme = manager.schemeManager.activeScheme
    if (activeScheme != null) {
      return activeScheme
    }
    return DefaultColorSchemesManager.getInstance().firstScheme
  }

  private fun Color.webRgba(alpha: Double = this.alpha.toDouble()): String {
    return "rgba($red, $green, $blue, $alpha)"
  }

  private fun EditorColorsScheme.getScrollbarColor(key: ColorKey): String {
    return (getColor(key) ?: key.defaultColor).let {
      "rgba(${it.red}, ${it.blue}, ${it.green}, ${getScrollbarAlpha(key) ?: (it.alpha / 255.0)})"
    }
  }

  private fun getScrollbarAlpha(colorKey: ColorKey): Int? {
    val contrastElementsKeys = listOf(
      ScrollBarPainter.THUMB_OPAQUE_FOREGROUND,
      ScrollBarPainter.THUMB_OPAQUE_BACKGROUND,
      ScrollBarPainter.THUMB_OPAQUE_HOVERED_FOREGROUND,
      ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND,
      ScrollBarPainter.THUMB_FOREGROUND,
      ScrollBarPainter.THUMB_BACKGROUND,
      ScrollBarPainter.THUMB_HOVERED_FOREGROUND,
      ScrollBarPainter.THUMB_HOVERED_BACKGROUND
    )

    if (!UISettings.shadowInstance.useContrastScrollbars || colorKey !in contrastElementsKeys) return null

    val lightAlpha = if (SystemInfo.isMac) 120 else 160
    val darkAlpha = if (SystemInfo.isMac) 255 else 180
    val alpha = Registry.intValue("contrast.scrollbars.alpha.level")
    return if (alpha > 0) {
      Integer.min(alpha, 255)
    }
    else {
      if (UIUtil.isUnderDarcula()) darkAlpha else lightAlpha
    }
  }

  /**
   * Simple linear contrast function.
   *
   * 0 < coefficient < 1 results in reduced contrast.
   * coefficient > 1 results in increased contrast.
   */
  private fun Color.contrast(coefficient: Double): Color {
    @Suppress("UseJBColor")
    return Color(
      (coefficient * (red - 128) + 128).toInt(),
      (coefficient * (green - 128) + 128).toInt(),
      (coefficient * (blue - 128) + 128).toInt(),
      alpha
    )
  }
}
