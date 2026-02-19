// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiListLikeElement
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.ast.PyAstWithStatement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.PyWithItem
import com.jetbrains.python.psi.PyWithStatement
import com.jetbrains.python.psi.types.TypeEvalContext

class PyWithStatementImpl(astNode: ASTNode?) : PyElementImpl(astNode), PyWithStatement, PsiListLikeElement {
  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPyWithStatement(this)
  }

  fun getNamedElement(the_name: String): PsiNamedElement? {
    return PyUtil.IterHelper.findName(getNamedElements(), the_name)
  }

  override fun getWithItems(): Array<PyWithItem> {
    return childrenToPsi(PyAstWithStatement.WITH_ITEM, PyWithItem.EMPTY_ARRAY)
  }

  override fun getComponents(): List<PyWithItem> {
    return withItems.toList()
  }

  override fun isSuppressingExceptions(context: TypeEvalContext): Boolean {
    return withItems.any { it.isSuppressingExceptions(context) }
  }
}
