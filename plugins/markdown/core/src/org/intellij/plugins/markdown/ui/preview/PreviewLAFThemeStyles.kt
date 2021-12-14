// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.namedColor
import com.intellij.ui.components.ScrollBarPainter
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * Service to with utility functions to generate
 * style for Markdown preview from IntelliJ LAF Settings
 */
internal object PreviewLAFThemeStyles {
  /**
   * This method will generate stylesheet with colors and other attributes matching current LAF settings of the IDE.
   * Generated CSS will override base rules from the default.css, so the preview elements will have correct colors.

   * @return String containing generated CSS rules.
   */
  @JvmStatic
  fun createStylesheet(): String {
    with(EditorColorsManager.getInstance().globalScheme) {
      val contrastedForeground = defaultForeground.contrast(0.1)

      val panelBackground = UIUtil.getPanelBackground()

      val linkActiveForeground = JBUI.CurrentTheme.Link.Foreground.ENABLED
      val separatorColor = namedColor("Group.separatorColor", panelBackground)
      val infoForeground = namedColor("Component.infoForeground", contrastedForeground)

      val markdownFenceBackground = JBColor(Color(212, 222, 231, 255 / 4), Color(212, 222, 231, 25))

      val fontSize = JBCefApp.normalizeScaledSize(EditorUtil.getEditorFont().size + 1)

      // For some reason background-color for ::-webkit-scrollbar-thumb
      // doesn't work with [0..255] alpha values. Fortunately it works fine with [0..1] values.
      // Default color from base stylesheets will be used, if the final value is null.
      // (Generated rule will be invalid)
      val scrollbarColor = getColor(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND)?.run {
        "rgba($red, $blue, $green, ${alpha / 255.0})"
      }
      // language=CSS
      val backgroundColor: String = defaultBackground.webRgba()//if (UIUtil.isUnderDarcula()) "rgb(49, 51, 53)" else defaultBackground.webRgba()
      return """
              body {
                  background-color: ${backgroundColor};
                  font-size: ${fontSize}px !important;
              }
              
              body, p, blockquote, ul, ol, dl, table, pre, code, tr  {
                  color: ${defaultForeground.webRgba()};
              }
              
              a {
                  color: ${linkActiveForeground.webRgba()};
              }
              
              table td, table th {
                border: 1px solid ${separatorColor.webRgba()};
              }
              
              hr {
                background-color: ${separatorColor.webRgba()};
              }
              
              kbd, tr {
                border: 1px solid ${separatorColor.webRgba()};
              }
              
              h6 {
                  color: ${infoForeground.webRgba()};
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
    }
  }

  private fun Color.webRgba(alpha: Double = this.alpha.toDouble()) = "rgba($red, $green, $blue, $alpha)"

  /**
   * Simple linear contrast function.
   *
   * 0 < coefficient < 1 results in reduced contrast.
   * coefficient > 1 results in increased contrast.
   */
  private fun Color.contrast(coefficient: Double) =
    Color(
      (coefficient * (red - 128) + 128).toInt(),
      (coefficient * (green - 128) + 128).toInt(),
      (coefficient * (blue - 128) + 128).toInt(),
      alpha
    )
}
