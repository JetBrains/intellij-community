// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represent a
 *
 * `do {
 *      // body
 * } while (expr)`
 *
 * loop expression.
 */
interface UDoWhileExpression : ULoopExpression {
  /**
   * Returns the loop post-condition.
   */
  val condition: UExpression

  /**
   * Returns an identifier for the 'do' keyword.
   */
  val doIdentifier: UIdentifier

  /**
   * Returns an identifier for the 'while' keyword.
   */
  val whileIdentifier: UIdentifier

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitDoWhileExpression(this)) return
    uAnnotations.acceptList(visitor)
    condition.accept(visitor)
    body.accept(visitor)
    visitor.afterVisitDoWhileExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitDoWhileExpression(this, data)

  override fun asRenderString(): String = buildString {
    append("do ")
    append(body.asRenderString())
    appendLine("while (${condition.asRenderString()})")
  }

  override fun asLogString(): String = log()
}
