// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  override fun clone(): PsiFileImpl {
    // this logic was added to make the original file not null,
    // which is used for the pop-up completion
    var clone = super.clone()
    clone.setOriginalFile(this)
    return clone
  }
}