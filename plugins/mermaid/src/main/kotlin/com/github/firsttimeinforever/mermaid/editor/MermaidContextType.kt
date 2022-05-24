package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.psi.MermaidFile
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiFile

class MermaidContextType: TemplateContextType("MERMAID", "Mermaid") {
  @Deprecated("Deprecated in Java",
    ReplaceWith("file is MermaidFile", "com.github.firsttimeinforever.mermaid.lang.psi.MermaidFile")
  )
  override fun isInContext(file: PsiFile, offset: Int): Boolean {
    return file is MermaidFile
  }
}
