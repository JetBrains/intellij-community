// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.components.service
import com.intellij.ui.jcef.JBCefScrollbarsHelper
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import java.awt.Color

internal object PreviewLAFThemeStyles {
  @Suppress("ConstPropertyName", "CssInvalidHtmlTagReference")
  object Variables {
    const val FontSize = "--default-font-size"
    const val Scale = "--scale"
  }

  val defaultFontSize: Int
    get() = service<MarkdownPreviewSettings>().state.fontSize

  /**
   * This method will generate stylesheet with colors and other attributes matching current LAF settings of the IDE.
   * Generated CSS will override base rules from the default.css, so the preview elements will have correct colors.

   * @return String containing generated CSS rules.
   */
  fun createStylesheet(): String {
    val scheme = PreviewStyleScheme.fromCurrentTheme()

    val separatorColor = scheme.separatorColor.webRgba()
    val infoForeground = scheme.infoForegroundColor.webRgba()

    val backgroundColor = scheme.backgroundColor.webRgba()
    // language=CSS
    return """
    :root {
      ${Variables.FontSize}: ${scheme.fontSize}px;
      ${Variables.Scale}: ${scheme.scale};
    }

    body {
        background-color: ${backgroundColor};
        font-size: var(${Variables.FontSize}) !important;
        transform: scale(var(${Variables.Scale})) !important;
        transform-origin: 0 0;
    }
    
    body, p, blockquote, ul, ol, dl, table, pre, code, tr  {
        color: ${scheme.foregroundColor.webRgba()};
    }
    
    a {
        color: ${scheme.linkActiveForegroundColor.webRgba()};
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
      border-left: 2px solid ${scheme.linkActiveForegroundColor.webRgba(alpha = 0.4)};
    }
    
    blockquote, code, pre {
      background-color: ${scheme.fenceBackgroundColor.webRgba(scheme.fenceBackgroundColor.alpha / 255.0)};
    }
    
    ${JBCefScrollbarsHelper.buildScrollbarsStyle()}
    
    """.trimIndent()
  }

  private fun Color.webRgba(alpha: Double = this.alpha.toDouble()): String {
    return "rgba($red, $green, $blue, $alpha)"
  }
}
