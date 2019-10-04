// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast


import com.intellij.psi.PsiType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents the class literal expression, e.g. `Clazz.class`.
 */
interface UClassLiteralExpression : UExpression {
  override fun asLogString(): String = log()

  override fun asRenderString(): String = (type?.name) ?: "(${expression?.asRenderString() ?: "<no expression>"})"+"::class"

  /**
   * Returns the type referenced by this class literal, or null if the type can't be determined in a compile-time.
   */
  val type: PsiType?

  /**
   * Returns an expression for this class literal expression.
   * Might be null if the [type] is specified.
   */
  val expression: UExpression?

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitClassLiteralExpression(this)) return
    uAnnotations.acceptList(visitor)
    expression?.accept(visitor)
    visitor.afterVisitClassLiteralExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitClassLiteralExpression(this, data)
}