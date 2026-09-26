// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents an expression with the label specified.
 */
interface ULabeledExpression : UExpression, ULabeled {
  /**
   * Returns the expression label.
   */
  override val label: String

  /**
   * Returns the expression itself.
   */
  val expression: UExpression

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitLabeledExpression(this)) return
    uAnnotations.acceptList(visitor)
    expression.accept(visitor)
    visitor.afterVisitLabeledExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitLabeledExpression(this, data)

  override fun evaluate(): Any? = expression.evaluate()

  override fun asLogString(): String = log("label = $label")

  override fun asRenderString(): String = "$label@ ${expression.asRenderString()}"
}