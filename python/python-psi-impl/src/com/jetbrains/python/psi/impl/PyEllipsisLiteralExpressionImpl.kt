// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyEllipsisLiteralExpression
import com.jetbrains.python.psi.PyInstantTypeProvider
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyEllipsisLiteralExpressionImpl(astNode: ASTNode?) : PyElementImpl(astNode), PyEllipsisLiteralExpression, PyInstantTypeProvider {
  override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType? {
    return PyBuiltinCache.getInstance(this).ellipsisType ?: PyAnyType.unknown
  }

  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPyEllipsisLiteralExpression(this)
  }
}
