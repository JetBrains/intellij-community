// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a
 *
 * ` switch (expression) {
 *       case value1 -> expr1
 *       case value2 -> expr2
 *       ...
 *       else -> exprElse
 *   }
 *
 *   conditional expression.
 */
interface USwitchExpression : UExpression {
  /**
  Returns the expression on which the `switch` expression is performed.
   */
  val expression: UExpression?

  /**
  Returns the switch body.
  The body should contain [USwitchClauseExpression] expressions.
   */
  val body: UExpressionList

  /**
   * Returns an identifier for the 'switch' ('case', 'when', ...) keyword.
   */
  val switchIdentifier: UIdentifier

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitSwitchExpression(this)) return
    uAnnotations.acceptList(visitor)
    expression?.accept(visitor)
    body.accept(visitor)
    visitor.afterVisitSwitchExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitSwitchExpression(this, data)

  override fun asLogString(): String = log<USwitchExpression>()

  override fun asRenderString(): String = buildString {
    val expr = expression?.let { "(" + it.asRenderString() + ") " } ?: ""
    appendLine("switch $expr")
    appendLine(body.asRenderString())
  }
}

/**
 * Represents a [USwitchExpression] clause.
 * [USwitchClauseExpression] does not contain the clause body,
 *     and the actual body expression should be the next element in the parent expression list.
 */
interface USwitchClauseExpression : UExpression {
  /**
   * Returns the list of values for this clause, or null if there are no values for this close
   *     (for example, for the `else` clause).
   */
  val caseValues: List<UExpression>

  /**
   * Represents the guard expressions for this switch clause or null if there is no guard.
   *     (for example, expression after `when` in java `switch` statement).
   */
  val guard: UExpression?
    @Experimental
    get() = null

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitSwitchClauseExpression(this)) return
    uAnnotations.acceptList(visitor)
    caseValues.acceptList(visitor)
    guard?.accept(visitor)
    visitor.afterVisitSwitchClauseExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitSwitchClauseExpression(this, data)

  override fun asRenderString(): String = caseValues.joinToString { it.asRenderString() } + " -> "

  override fun asLogString(): String = "USwitchClauseExpression"
}

/**
 * Represents a [USwitchExpression] clause with the body.
 * [USwitchClauseExpressionWithBody], comparing with [USwitchClauseExpression], contains the body expression.
 *
 * Implementing this interface *is the right way* to support `switch` clauses in your language.
 */
interface USwitchClauseExpressionWithBody : USwitchClauseExpression {
  /**
   * Returns the body expression for this clause.
   */
  val body: UExpressionList

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitSwitchClauseExpression(this)) return
    uAnnotations.acceptList(visitor)
    caseValues.acceptList(visitor)
    guard?.accept(visitor)
    body.accept(visitor)
    visitor.afterVisitSwitchClauseExpression(this)
  }

  override fun asRenderString(): String = caseValues.joinToString { it.asRenderString() } + " -> " + body.asRenderString()

  override fun asLogString(): String = log()
}