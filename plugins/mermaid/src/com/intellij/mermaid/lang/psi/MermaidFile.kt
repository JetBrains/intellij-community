// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.mermaid.lang.MermaidFileType
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class MermaidFile(viewProvider: FileViewProvider): PsiFileBase(viewProvider, MermaidLanguage), MermaidPsiElement {
  override fun getFileType(): FileType {
    return MermaidFileType
  }
}
