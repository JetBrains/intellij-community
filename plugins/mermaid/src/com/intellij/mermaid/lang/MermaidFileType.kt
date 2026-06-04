package com.intellij.mermaid.lang

import com.intellij.mermaid.MermaidIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object MermaidFileType: LanguageFileType(MermaidLanguage) {
  override fun getName(): String = "Mermaid"

  override fun getDescription(): String = "Mermaid"

  override fun getDefaultExtension(): String = "mermaid"

  override fun getIcon(): Icon = MermaidIcons.FileType
}
