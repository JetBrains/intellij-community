// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a
 *
 * `while (condition) {
 *      // body
 *  }`
 *
 *  expression.
 */
interface UWhileExpression : ULoopExpression {
  /**
   * Returns the loop condition.
   */
  val condition: UExpression

  /**
   * Returns an identifier for the 'while' keyword.
   */
  val whileIdentifier: UIdentifier

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitWhileExpression(this)) return
    uAnnotations.acceptList(visitor)
    condition.accept(visitor)
    body.accept(visitor)
    visitor.afterVisitWhileExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitWhileExpression(this, data)

  override fun asRenderString(): String = buildString {
    append("while (${condition.asRenderString()}) ")
    append(body.asRenderString())
  }

  override fun asLogString(): String = log()
}
