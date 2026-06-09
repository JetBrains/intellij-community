// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.formatter.settings

import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.psi.codeStyle.CodeStyleSettings

internal class MermaidCodeStyleSettingsPanel(
  currentSettings: CodeStyleSettings,
  settings: CodeStyleSettings
): TabbedLanguageCodeStylePanel(MermaidLanguage, currentSettings, settings) {
  override fun initTabs(settings: CodeStyleSettings) {
    addIndentOptionsTab(settings)
    addBlankLinesTab(settings)
    addSpacesTab(settings)
  }
}
