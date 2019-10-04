// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiMethod
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

interface UUnaryExpression : UExpression {
  /**
   * Returns the expression operand.
   */
  val operand: UExpression

  /**
   * Returns the expression operator.
   */
  val operator: UastOperator

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

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitUnaryExpression(this)) return
    uAnnotations.acceptList(visitor)
    operand.accept(visitor)
    visitor.afterVisitUnaryExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitUnaryExpression(this, data)
}

interface UPrefixExpression : UUnaryExpression {
  override val operator: UastPrefixOperator

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitPrefixExpression(this)) return
    uAnnotations.acceptList(visitor)
    operand.accept(visitor)
    visitor.afterVisitPrefixExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitPrefixExpression(this, data)

  override fun asLogString(): String = log("operator = $operator")

  override fun asRenderString(): String = operator.text + operand.asRenderString()
}

interface UPostfixExpression : UUnaryExpression {
  override val operator: UastPostfixOperator

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitPostfixExpression(this)) return
    uAnnotations.acceptList(visitor)
    operand.accept(visitor)
    visitor.afterVisitPostfixExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitPostfixExpression(this, data)

  override fun asLogString(): String = log("operator = $operator")

  override fun asRenderString(): String = operand.asRenderString() + operator.text
}