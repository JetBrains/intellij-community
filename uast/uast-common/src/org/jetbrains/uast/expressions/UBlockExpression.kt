// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    appendln("{")
    expressions.forEach { appendln(it.asRenderString().withMargin) }
    append("}")
  }
}