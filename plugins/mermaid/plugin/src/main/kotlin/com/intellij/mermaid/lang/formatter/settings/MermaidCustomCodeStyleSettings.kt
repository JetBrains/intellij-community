package com.intellij.mermaid.lang.formatter.settings

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class MermaidCustomCodeStyleSettings(settings: CodeStyleSettings) :
  CustomCodeStyleSettings(MermaidLanguage.id, settings) {
  //SPACES
  @JvmField
  var FORCE_ONE_SPACE_BETWEEN_WORDS: Boolean = true
}
