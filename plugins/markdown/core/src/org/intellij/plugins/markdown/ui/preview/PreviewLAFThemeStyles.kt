// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.ex.util.EditorUtil
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

    // For some reason background-color for ::-webkit-scrollbar-thumb
    // doesn't work with [0..255] alpha values. Fortunately it works fine with [0..1] values.
    // Default color from base stylesheets will be used, if the final value is null.
    // (Generated rule will be invalid)
    val scrollbarColor = scheme.getColor(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND)?.apply {
      "rgba($red, $blue, $green, ${alpha / 255.0})"
    }
    // language=CSS
    val backgroundColor = scheme.defaultBackground.webRgba()
    // language=CSS
    val styles = """
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
    
    ::-webkit-scrollbar-thumb {
        background-color: $scrollbarColor;
    }
    
    blockquote, code, pre {
      background-color: ${markdownFenceBackground.webRgba(markdownFenceBackground.alpha / 255.0)};
    }
    """.trimIndent()
    return styles
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
