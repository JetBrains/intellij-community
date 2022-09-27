package com.intellij.mermaid.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.mermaid.lang.MermaidFileType
import com.intellij.mermaid.lang.MermaidLanguage

class MermaidFile(viewProvider: FileViewProvider): PsiFileBase(viewProvider, MermaidLanguage) {
  override fun getFileType(): FileType {
    return MermaidFileType
  }
}
