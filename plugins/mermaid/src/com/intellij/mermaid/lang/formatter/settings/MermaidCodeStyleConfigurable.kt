// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.formatter.settings

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.mermaid.MermaidBundle
import com.intellij.psi.codeStyle.CodeStyleSettings

internal class MermaidCodeStyleConfigurable(settings: CodeStyleSettings, originalSettings: CodeStyleSettings)
  : CodeStyleAbstractConfigurable(settings, originalSettings, MermaidBundle.message("mermaid.settings.name")) {

  override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel {
    return MermaidCodeStyleSettingsPanel(currentSettings, settings)
  }
}
