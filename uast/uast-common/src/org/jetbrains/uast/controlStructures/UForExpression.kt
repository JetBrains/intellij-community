// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a
 *
 * `for (initDeclarations; loopCondition; update) {
 *      // body
 *  }`
 *
 *  loop expression.
 */
interface UForExpression : ULoopExpression {
  /**
   * Returns the [UExpression] containing variable declarations, or null if the are no variables declared.
   */
  val declaration: UExpression?

  /**
   * Returns the loop condition, or null if the condition is empty.
   */
  val condition: UExpression?

  /**
   * Returns the loop update expression(s).
   */
  val update: UExpression?

  /**
   * Returns the identifier for the 'for' keyword.
   */
  val forIdentifier: UIdentifier

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitForExpression(this)) return
    uAnnotations.acceptList(visitor)
    declaration?.accept(visitor)
    condition?.accept(visitor)
    update?.accept(visitor)
    body.accept(visitor)
    visitor.afterVisitForExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitForExpression(this, data)

  override fun asRenderString(): String = buildString {
    append("for (")
    declaration?.let { append(it.asRenderString()) }
    append("; ")
    condition?.let { append(it.asRenderString()) }
    append("; ")
    update?.let { append(it.asRenderString()) }
    append(") ")
    append(body.asRenderString())
  }

  override fun asLogString(): String = log()
}
