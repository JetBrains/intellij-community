// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast

import com.intellij.psi.PsiElement
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonDialectsTokenSetProvider
import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstAugAssignmentStatement : PyAstStatement {
  val target: PyAstExpression
    get() {
      return childToPsi(PythonDialectsTokenSetProvider.getInstance().expressionTokens, 0)
        ?: throw RuntimeException("Target missing in augmented assignment statement")
    }

  val value: PyAstExpression?
    get() = childToPsi(PythonDialectsTokenSetProvider.getInstance().expressionTokens, 1)

  val operation: PsiElement?
    get() = PyPsiUtilsCore.getChildByFilter(this, PyTokenTypes.AUG_ASSIGN_OPERATIONS, 0)

  override fun acceptPyVisitor(pyVisitor: PyAstElementVisitor) {
    pyVisitor.visitPyAugAssignmentStatement(this)
  }
}
