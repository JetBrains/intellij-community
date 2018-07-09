/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    annotations.acceptList(visitor)
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
    annotations.acceptList(visitor)
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
    annotations.acceptList(visitor)
    operand.accept(visitor)
    visitor.afterVisitPostfixExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitPostfixExpression(this, data)

  override fun asLogString(): String = log("operator = $operator")

  override fun asRenderString(): String = operand.asRenderString() + operator.text
}