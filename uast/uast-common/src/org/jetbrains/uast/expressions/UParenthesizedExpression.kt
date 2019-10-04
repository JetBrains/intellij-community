// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a parenthesized expression, e.g. `(23 + 3)`.
 */
interface UParenthesizedExpression : UExpression {
  /**
   * Returns an expression inside the parenthesis.
   */
  val expression: UExpression

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitParenthesizedExpression(this)) return
    uAnnotations.acceptList(visitor)
    expression.accept(visitor)
    visitor.afterVisitParenthesizedExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitParenthesizedExpression(this, data)

  override fun evaluate(): Any? = expression.evaluate()

  override fun asLogString(): String = log()

  override fun asRenderString(): String = '(' + expression.asRenderString() + ')'
}