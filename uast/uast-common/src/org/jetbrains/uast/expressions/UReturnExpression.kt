// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a `return` expression.
 */
interface UReturnExpression : UJumpExpression {
  /**
   * Returns the `return` value.
   */
  val returnExpression: UExpression?

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitReturnExpression(this)) return
    uAnnotations.acceptList(visitor)
    returnExpression?.accept(visitor)
    visitor.afterVisitReturnExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitReturnExpression(this, data)

  override fun asRenderString(): String = returnExpression.let { if (it == null) "return" else "return " + it.asRenderString() }

  override fun asLogString(): String = log()

  override val label: String?
    get() = null

  override val jumpTarget: UElement? get() =
    generateSequence(uastParent) { it.uastParent }
      .find { it is ULambdaExpression || it is UMethod }
}