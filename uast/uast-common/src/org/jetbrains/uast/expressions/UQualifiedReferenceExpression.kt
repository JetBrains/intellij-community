// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents the qualified expression (receiver.selector).
 */
interface UQualifiedReferenceExpression : UReferenceExpression {
  /**
   * Returns the expression receiver.
   */
  val receiver: UExpression

  /**
   * Returns the expression selector.
   */
  val selector: UExpression

  /**
   * Returns the access type (simple, safe access, etc.).
   */
  val accessType: UastQualifiedExpressionAccessType

  override fun asRenderString(): String = receiver.asRenderString() + accessType.name + selector.asRenderString()

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitQualifiedReferenceExpression(this)) return
    uAnnotations.acceptList(visitor)
    receiver.accept(visitor)
    selector.accept(visitor)
    visitor.afterVisitQualifiedReferenceExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitQualifiedReferenceExpression(this, data)

  override fun asLogString(): String = log()

  override val referenceNameElement: UElement?
    get() = unwrapReferenceNameElement(selector)

}