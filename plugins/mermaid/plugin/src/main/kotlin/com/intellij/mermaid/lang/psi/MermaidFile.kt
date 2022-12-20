package com.intellij.mermaid.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.mermaid.lang.MermaidFileType
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class MermaidFile(viewProvider: FileViewProvider): PsiFileBase(viewProvider, MermaidLanguage) {
  override fun getFileType(): FileType {
    return MermaidFileType
  }
}
