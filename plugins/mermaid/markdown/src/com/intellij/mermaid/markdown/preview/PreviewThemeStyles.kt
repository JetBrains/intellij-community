// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefScrollbarsHelper
import com.intellij.util.ui.JBUI
import java.awt.Color

@Suppress("UnstableApiUsage")
object PreviewThemeStyles {
  fun createStylesheet(): String {
    val scheme = obtainColorScheme()
    val linkActiveForeground = JBUI.CurrentTheme.Link.Foreground.ENABLED
    val fontSize = JBCefApp.normalizeScaledSize(EditorUtil.getEditorFont().size + 1)
    val backgroundColor = scheme.defaultBackground.webRgba()
    // language=CSS
    return """
    body {
      background-color: ${backgroundColor};
      font-size: ${fontSize}px !important;
    }

    body, p  {
      color: ${scheme.defaultForeground.webRgba()};
    }

    a {
      color: ${linkActiveForeground.webRgba()};
    }

    ${JBCefScrollbarsHelper.buildScrollbarsStyle()}
    """.trimIndent()
  }

  private fun obtainColorScheme(): EditorColorsScheme {
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
}
