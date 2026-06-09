// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.settings

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ColorUtil

object MermaidThemeUtil {
  @JvmStatic
  fun determineTheme(): String {
    val mermaidSettings = MermaidSettings.getInstance()
    val theme = mermaidSettings.theme.value
    if (theme == "follow-ide") {
      val scheme = EditorColorsManager.getInstance().globalScheme
      return when {
        ColorUtil.isDark(scheme.defaultBackground) -> "dark"
        else -> "default"
      }
    }
    return theme
  }
}
