// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * Service to with utility functions to generate
 * style for Markdown preview from IntelliJ Color Theme
 */
internal object PreviewColorThemeStyles {
  /**
   * This method will generate stylesheet with color rules for markdown elements,
   * matching current IDE colors. Generated rules will override base rules from the
   * default.css, so the preview elements will have (almost*) correct colors.
   *
   * *There will be no dedicated color for code blocks, if current IDE color theme defines same
   * colors for UI panels and editor background (seems fine, though).
   *
   * @return String containing generated CSS rules.
   */
  @JvmStatic
  fun createStylesheet(): String {
    val panelBackground = UIUtil.getPanelBackground()
    with(EditorColorsManager.getInstance().globalScheme) {
      // For some reason background-color for ::-webkit-scrollbar-thumb
      // doesn't work with [0..255] alpha values. Fortunately it works fine with [0..1] values.
      // Default color from base stylesheets will be used, if the final value is null.
      // (Generated rule will be invalid)
      val scrollbarColor = getColor(EditorColors.SCROLLBAR_THUMB_COLOR)?.run {
        "rgba($red, $blue, $green, ${alpha / 255.0})"
      }
      val contrastedForeground = defaultForeground.contrast(0.1)
      val contrastedBackground = panelBackground.contrast(0.1)
      val linkColor = getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).foregroundColor
      // language=CSS
      return """
        body {
          background-color: ${defaultBackground.webRgba()};
          color: ${defaultForeground.webRgba()};
        }
        a {
          color: ${linkColor.webRgba()};
        }
        hr {
          background-color: ${panelBackground.webRgba()};
        }
        h6 {
          color: ${contrastedForeground.webRgba()};
        }
        pre {
          background-color: ${panelBackground.webRgba()};
        }
        pre > code {
          color: ${defaultForeground.webRgba()};
        }
        table tr {
          color: ${defaultForeground.webRgba()};
        }
        table th, table td, table tr {
          background-color: ${defaultBackground.webRgba()};
          border-color: ${defaultBackground.contrast(0.85).webRgba()};
        }
        table tr:nth-child(even) td {
          background-color: ${defaultBackground.contrast(0.93).webRgba()};
        }
        blockquote {
          border-left-color: ${contrastedBackground.webRgba()};
        }
        blockquote > p {
          color: ${contrastedForeground.webRgba()};
        }
        :checked + .radio-label {
          border-color: ${panelBackground.webRgba()};
        }
        ::-webkit-scrollbar-thumb {
          background-color: $scrollbarColor;
        }
      """.trimIndent()
    }
  }

  private fun Color.webRgba() = "rgba($red, $green, $blue, $alpha)"

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
