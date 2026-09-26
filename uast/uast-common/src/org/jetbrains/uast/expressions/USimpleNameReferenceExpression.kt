// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a simple reference expression (a non-qualified identifier).
 */
interface USimpleNameReferenceExpression : UReferenceExpression {
  /**
   * Returns the identifier name.
   */
  val identifier: String

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitSimpleNameReferenceExpression(this)) return
    uAnnotations.acceptList(visitor)
    visitor.afterVisitSimpleNameReferenceExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitSimpleNameReferenceExpression(this, data)

  override fun asLogString(): String = log("identifier = $identifier")

  override fun asRenderString(): String = identifier
}