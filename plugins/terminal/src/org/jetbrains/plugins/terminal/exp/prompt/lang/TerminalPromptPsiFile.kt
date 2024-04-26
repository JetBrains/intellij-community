// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.tree.IElementType

internal class TerminalPromptPsiFile(elementType: IElementType, viewProvider: FileViewProvider) : PsiFileImpl(elementType, elementType, viewProvider) {
  override fun accept(visitor: PsiElementVisitor) {
    visitor.visitFile(this)
  }

  override fun getFileType(): FileType = TerminalPromptFileType
}