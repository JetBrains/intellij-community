// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents
 *
 * `if (condition) {
 *     // do if true
 * } else {
 *     // do if false
 * }`
 *
 * and
 *
 * `condition : trueExpression ? falseExpression`
 *
 * condition expressions.
 */
interface UIfExpression : UExpression {
  /**
   * Returns the condition expression.
   */
  val condition: UExpression

  /**
   * Returns the expression which is executed if the condition is true, or null if the expression is empty.
   */
  val thenExpression: UExpression?

  /**
   * Returns the expression which is executed if the condition is false, or null if the expression is empty.
   */
  val elseExpression: UExpression?

  /**
   * Returns true if the expression is ternary (condition ? trueExpression : falseExpression).
   */
  val isTernary: Boolean

  /**
   * Returns an identifier for the 'if' keyword.
   */
  val ifIdentifier: UIdentifier

  /**
   * Returns an identifier for the 'else' keyword, or null if the conditional expression has not the 'else' part.
   */
  val elseIdentifier: UIdentifier?

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitIfExpression(this)) return
    uAnnotations.acceptList(visitor)
    condition.accept(visitor)
    thenExpression?.accept(visitor)
    elseExpression?.accept(visitor)
    visitor.afterVisitIfExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitIfExpression(this, data)

  override fun asLogString(): String = log()

  override fun asRenderString(): String = buildString {
    if (isTernary) {
      append("(" + condition.asRenderString() + ")")
      append(" ? ")
      append("(" + (thenExpression?.asRenderString() ?: "<noexpr>") + ")")
      append(" : ")
      append("(" + (elseExpression?.asRenderString() ?: "<noexpr>") + ")")
    }
    else {
      append("if (${condition.asRenderString()}) ")
      thenExpression?.let { append(it.asRenderString()) }
      val elseBranch = elseExpression
      if (elseBranch != null && elseBranch !is UastEmptyExpression) {
        if (thenExpression !is UBlockExpression) append(" ")
        append("else ")
        append(elseBranch.asRenderString())
      }
    }
  }
}
