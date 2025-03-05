// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.PsiFileImpl

internal class TerminalOutputPsiFile(
  viewProvider: FileViewProvider,
) : PsiFileImpl(TerminalOutputTokenTypes.FILE, TerminalOutputTokenTypes.FILE, viewProvider) {
  override fun getFileType(): FileType = TerminalOutputFileType

  override fun accept(visitor: PsiElementVisitor) {
    visitor.visitFile(this)
  }
}