// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiMethod
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a binary expression (value1 op value2), eg. `2 + "A"`.
 */
interface UBinaryExpression : UPolyadicExpression {
  /**
   * Returns the left operand.
   */
  val leftOperand: UExpression

  /**
   * Returns the right operand.
   */
  val rightOperand: UExpression

  /**
   * Returns the operator identifier.
   */
  val operatorIdentifier: UIdentifier?

  /**
   * Resolve the operator method.
   *
   * @return the resolved method, or null if the method can't be resolved, or if the expression is not a method call.
   */
  fun resolveOperator(): PsiMethod?

  override val operands: List<UExpression>
    get() = listOf(leftOperand, rightOperand)

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitBinaryExpression(this)) return
    uAnnotations.acceptList(visitor)
    leftOperand.accept(visitor)
    rightOperand.accept(visitor)
    visitor.afterVisitBinaryExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitBinaryExpression(this, data)

  override fun asLogString(): String = log("operator = $operator")
}