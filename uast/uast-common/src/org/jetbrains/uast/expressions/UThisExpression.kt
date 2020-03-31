// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a `this` expression.
 * TODO Qualified `this` is not supported at the moment.
 */
interface UThisExpression : UInstanceExpression {
  override fun asLogString(): String = log("label = $label")

  override fun asRenderString(): String = "this"

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitThisExpression(this)) return
    uAnnotations.acceptList(visitor)
    visitor.afterVisitThisExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitThisExpression(this, data)
}