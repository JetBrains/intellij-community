// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastVisitor

interface UNamedExpression : UExpression {
  val name: String?
  val expression: UExpression

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitNamedExpression(this)) return
    uAnnotations.acceptList(visitor)
    expression.accept(visitor)
    visitor.afterVisitNamedExpression(this)
  }

  override fun asLogString(): String = log("name = $name")

  override fun asRenderString(): String = name + " = " + expression.asRenderString()

  override fun evaluate(): Any? = expression.evaluate()
}
