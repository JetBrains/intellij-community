// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.frontend.split.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.tree.IElementType

abstract class FrontendFileImpl(
  viewProvider: FileViewProvider,
  elementType: IElementType,
) : PsiFileImpl(elementType, elementType, viewProvider) {

  override fun getFileType(): FileType =
    viewProvider.fileType

  override fun accept(visitor: PsiElementVisitor) {
    visitor.visitFile(this)
  }
}
