// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a list of declarations.
 * Example in Java: `int a = 4, b = 3`.
 */
interface UDeclarationsExpression : UExpression {
  /**
   * Returns the list of declarations inside this [UDeclarationsExpression].
   */
  val declarations: List<UDeclaration>

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitDeclarationsExpression(this)) return
    uAnnotations.acceptList(visitor)
    declarations.acceptList(visitor)
    visitor.afterVisitDeclarationsExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitDeclarationsExpression(this, data)

  override fun asRenderString(): String = declarations.joinToString(LINE_SEPARATOR) { it.asRenderString() }

  override fun asLogString(): String = log()
}