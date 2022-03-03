package com.github.firsttimeinforever.mermaid.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object MermaidFileType: LanguageFileType(MermaidLanguage) {
  override fun getName(): String = "MyMermaid"

  override fun getDescription(): String = "Mermaid description"

  override fun getDefaultExtension(): String = "mymermaid"

  override fun getIcon(): Icon? = null
}
