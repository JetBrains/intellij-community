package com.github.firsttimeinforever.mermaid.lang.formatter.settings

import com.github.firsttimeinforever.mermaid.MermaidBundle
import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.psi.codeStyle.CodeStyleSettings

internal class MermaidCodeStyleConfigurable(settings: CodeStyleSettings, originalSettings: CodeStyleSettings)
  : CodeStyleAbstractConfigurable(settings, originalSettings, MermaidBundle.message("mermaid.settings.name")) {

  override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel {
    return MermaidCodeStyleSettingsPanel(currentSettings, settings)
  }
}
