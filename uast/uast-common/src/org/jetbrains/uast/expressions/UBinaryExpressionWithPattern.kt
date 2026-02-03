// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

@Experimental
interface UBinaryExpressionWithPattern : UExpression {
  /**
   * Returns the operand expression.
   */
  val operand: UExpression

  /**
   * Returns the pattern of this expression.
   */
  val patternExpression: UPatternExpression?

  override fun asLogString(): String = log()

  override fun asRenderString(): String = "${operand.asRenderString()} is ${patternExpression?.asRenderString()}"

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitBinaryExpressionWithPattern(this)) return
    uAnnotations.acceptList(visitor)
    operand.accept(visitor)
    patternExpression?.accept(visitor)
    visitor.afterVisitBinaryExpressionWithPattern(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = visitor.visitBinaryExpressionWithPattern(this, data)
}