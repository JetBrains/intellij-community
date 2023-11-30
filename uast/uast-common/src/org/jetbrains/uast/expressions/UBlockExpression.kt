// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents the code block expression: `{ /* code */ }`.
 */
interface UBlockExpression : UExpression {
  /**
   * Returns the list of block expressions.
   */
  val expressions: List<UExpression>

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitBlockExpression(this)) return
    uAnnotations.acceptList(visitor)
    expressions.acceptList(visitor)
    visitor.afterVisitBlockExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitBlockExpression(this, data)

  override fun asLogString(): String = log()

  override fun asRenderString(): String = buildString {
    appendLine("{")
    expressions.forEach { appendLine(it.asRenderString().withMargin) }
    append("}")
  }
}