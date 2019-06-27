// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents `receiver[index0, ..., indexN]` expression.
 */
interface UArrayAccessExpression : UExpression {
  /**
   * Returns the receiver expression.
   */
  val receiver: UExpression

  /**
   * Returns the list of index expressions.
   */
  val indices: List<UExpression>

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitArrayAccessExpression(this)) return
    uAnnotations.acceptList(visitor)
    receiver.accept(visitor)
    indices.acceptList(visitor)
    visitor.afterVisitArrayAccessExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitArrayAccessExpression(this, data)

  override fun asLogString(): String = log()

  override fun asRenderString(): String = receiver.asRenderString() +
                                          indices.joinToString(prefix = "[", postfix = "]") { it.asRenderString() }
}