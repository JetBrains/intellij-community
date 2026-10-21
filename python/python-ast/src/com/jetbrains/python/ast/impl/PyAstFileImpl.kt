// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast.impl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.ast.PyAstElementVisitor
import com.jetbrains.python.ast.PyAstFile
import com.jetbrains.python.psi.FutureFeature
import org.jetbrains.annotations.ApiStatus

/**
 * The AST-only Python file: the PSI of a `.py` or `.pyi` file in a process without the stub-based Python PSI,
 * such as the JetBrains Client frontend or JetBrains Light before the smart mode.
 */
@ApiStatus.Internal
open class PyAstFileImpl(viewProvider: FileViewProvider, language: Language, private val fileType: FileType)
  : PsiFileBase(viewProvider, language), PyAstFile {

  override fun getFileType(): FileType = fileType

  override fun accept(visitor: PsiElementVisitor) {
    if (isAcceptedFor(visitor.javaClass)) {
      if (visitor is PyAstElementVisitor) {
        visitor.visitPyFile(this)
      }
      else {
        super.accept(visitor)
      }
    }
  }

  override fun hasImportFromFuture(feature: FutureFeature): Boolean = false

  override fun toString(): String = "PyAstFile:$name"
}
