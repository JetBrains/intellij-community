package com.github.firsttimeinforever.mermaid.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.github.firsttimeinforever.mermaid.lang.MermaidFileType
import com.github.firsttimeinforever.mermaid.lang.MermaidLanguage

class MermaidFile(viewProvider: FileViewProvider): PsiFileBase(viewProvider, MermaidLanguage) {
  override fun getFileType(): FileType {
    return MermaidFileType
  }
}
