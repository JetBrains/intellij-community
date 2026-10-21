// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast.impl

import com.intellij.lang.Language
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.ast.PyAstElementVisitor
import com.jetbrains.python.ast.PyAstStatementList
import org.jetbrains.annotations.ApiStatus

/** The AST-only statement list. The node is lazy-parseable and is also the PSI element. */
@ApiStatus.Internal
class PyAstStatementListImpl(type: IElementType, buffer: CharSequence?) : LazyParseablePsiElement(type, buffer), PyAstStatementList {
  override fun accept(visitor: PsiElementVisitor) {
    if (visitor is PyAstElementVisitor) {
      visitor.visitPyStatementList(this)
    }
    else {
      super.accept(visitor)
    }
  }

  override fun getLanguage(): Language = PythonLanguage.INSTANCE

  override fun toString(): String = "PyStatementList"
}
