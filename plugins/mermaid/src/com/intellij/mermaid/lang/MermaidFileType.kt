package com.intellij.mermaid.lang

import com.intellij.mermaid.icons.MermaidIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon


object MermaidFileType: LanguageFileType(MermaidLanguage) {
  @NlsSafe
  private val MERMAID_DESCRIPTION = "Mermaid"
  
  override fun getName(): String = MERMAID_DESCRIPTION

  override fun getDescription(): String = MERMAID_DESCRIPTION

  override fun getDefaultExtension(): String = "mermaid"

  override fun getIcon(): Icon = MermaidIcons.MermaidFileType
}
