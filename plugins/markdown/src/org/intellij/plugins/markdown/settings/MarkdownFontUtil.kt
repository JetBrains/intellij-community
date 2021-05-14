// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

object MarkdownFontUtil {
  fun getFontSizeCss(fontSize: Int, fontFamily: String) =
    """
      div { 
        font-size: ${fontSize}px !important; 
        font-family: ${fontFamily} !important; 
      }
    """.trimIndent()
}
