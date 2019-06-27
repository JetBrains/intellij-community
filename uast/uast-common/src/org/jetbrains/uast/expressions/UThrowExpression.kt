// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a `throw` expression.
 */
interface UThrowExpression : UExpression {
  /**
   * Returns ths thrown expression.
   */
  val thrownExpression: UExpression

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitThrowExpression(this)) return
    uAnnotations.acceptList(visitor)
    thrownExpression.accept(visitor)
    visitor.afterVisitThrowExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitThrowExpression(this, data)

  override fun asRenderString(): String = "throw " + thrownExpression.asRenderString()

  override fun asLogString(): String = log()
}