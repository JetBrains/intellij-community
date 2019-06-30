// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.expressions

import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UJumpExpression
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
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

  override fun asLogString(): String = log("value = ${expression?.asLogString() ?: "<unknown>"}")

  override fun asRenderString(): String = buildString {
    append("yield")
    expression?.let { append(" ${it.asRenderString()}") }
  }
}