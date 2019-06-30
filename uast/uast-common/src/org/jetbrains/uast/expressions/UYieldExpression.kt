// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UJumpExpression
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a `yield` expression.
 */
interface UYieldExpression : UJumpExpression {
  val expression: UExpression?

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitYieldExpression(this)) return
    uAnnotations.acceptList(visitor)
    expression?.accept(visitor)
    visitor.afterVisitYieldExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitYieldExpression(this, data)

  override fun asLogString(): String = log()

  override fun asRenderString(): String = buildString {
    append("yield")
    expression?.let { append(" ${it.asRenderString()}") }
  }
}