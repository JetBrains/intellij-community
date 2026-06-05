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
