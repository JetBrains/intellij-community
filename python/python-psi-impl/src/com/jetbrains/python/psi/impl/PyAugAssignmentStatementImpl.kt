// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiPolyVariantReference
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.impl.references.PyOperatorReference
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyAugAssignmentStatementImpl(astNode: ASTNode) : PyElementImpl(astNode), PyAugAssignmentStatement {

  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPyAugAssignmentStatement(this)
  }

  /**
   * the type after the operation has been applied
   */
  override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType? {
    return PyCallExpressionHelper.getCallType(this, context, key)
  }

  override fun getReference(): PsiPolyVariantReference {
    return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())))
  }

  override fun getReference(context: PyResolveContext): PsiPolyVariantReference {
    return PyOperatorReference(this, context)
  }
}
