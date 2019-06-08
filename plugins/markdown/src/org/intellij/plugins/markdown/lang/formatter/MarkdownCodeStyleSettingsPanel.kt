// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownLanguage

class MarkdownCodeStyleSettingsPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
  : TabbedLanguageCodeStylePanel(MarkdownLanguage.INSTANCE, currentSettings, settings) {

  override fun initTabs(settings: CodeStyleSettings?) {
    addWrappingAndBracesTab(settings)
  }
}