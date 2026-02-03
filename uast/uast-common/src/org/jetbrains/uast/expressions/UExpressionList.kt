// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a generic list of expressions.
 */
interface UExpressionList : UExpression {
  /**
   * Returns the list of expressions.
   */
  val expressions: List<UExpression>

  /**
   * Returns the list kind.
   */
  val kind: UastSpecialExpressionKind

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitExpressionList(this)) return
    uAnnotations.acceptList(visitor)
    expressions.acceptList(visitor)
    visitor.afterVisitExpressionList(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitExpressionList(this, data)

  fun firstOrNull(): UExpression? = expressions.firstOrNull()

  override fun asLogString(): String = log(kind.name)

  override fun asRenderString(): String = kind.name + " " + expressions.joinToString(" : ") { it.asRenderString() }
}