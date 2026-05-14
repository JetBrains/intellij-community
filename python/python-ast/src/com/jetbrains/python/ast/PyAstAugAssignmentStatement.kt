// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonDialectsTokenSetProvider
import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import com.jetbrains.python.psi.PyElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstAugAssignmentStatement : PyAstStatement, PyAstQualifiedExpression, PyAstCallSiteOwner, PyAstReferenceOwner {
  val target: PyAstExpression
    get() {
      return childToPsi(PythonDialectsTokenSetProvider.getInstance().expressionTokens, 0)
        ?: throw RuntimeException("Target missing in augmented assignment statement")
    }

  val value: PyAstExpression?
    get() = childToPsi(PythonDialectsTokenSetProvider.getInstance().expressionTokens, 1)

  val operation: PsiElement?
    get() = PyPsiUtilsCore.getChildByFilter(this, PyTokenTypes.AUG_ASSIGN_OPERATIONS, 0)

  fun isRightOperator(resolvedCallee: PyAstCallable?): Boolean {
    return resolvedCallee != null && PyNames.isRightOperatorName(referencedName, resolvedCallee.getName())
  }

  fun isInplaceOperator(resolvedCallee: PyAstCallable?): Boolean {
    return resolvedCallee != null && PyNames.isInplaceOperatorName(referencedName, resolvedCallee.getName())
  }

  override fun getQualifier(): PyAstExpression? {
    return this.target
  }

  override fun asQualifiedName(): QualifiedName? {
    return PyPsiUtilsCore.asQualifiedName(this)
  }

  override fun isQualified(): Boolean {
    return qualifier != null
  }

  override fun getReferencedName(): String? {
    val t = this.operation?.node?.elementType as PyElementType
    return t.specialMethodName
  }

  override fun getNameElement(): ASTNode? {
    return operation?.getNode()
  }

  override fun getReceiver(resolvedCallee: PyAstCallable?): PyAstExpression? {
    return if (isRightOperator(resolvedCallee)) this.value else this.target
  }

  override fun getArguments(resolvedCallee: PyAstCallable?): List<PyAstExpression?> {
    return listOf(if (isRightOperator(resolvedCallee)) this.target else this.value)
  }

  override fun acceptPyVisitor(pyVisitor: PyAstElementVisitor) {
    pyVisitor.visitPyAugAssignmentStatement(this)
  }
}
