// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastVisitor

interface UNamedExpression : UExpression {
  val name: String?
  val expression: UExpression

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitElement(this)) return
    uAnnotations.acceptList(visitor)
    expression.accept(visitor)
    visitor.afterVisitElement(this)
  }

  override fun asLogString(): String = log("name = $name")

  override fun asRenderString(): String = name + " = " + expression.asRenderString()

  override fun evaluate(): Any? = expression.evaluate()
}
