// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast


import com.intellij.psi.PsiType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a callable reference expression, e.g. `Clazz::methodName`.
 */
interface UCallableReferenceExpression : UReferenceExpression {
  /**
   * Returns the qualifier expression.
   * Can be null if the [qualifierType] is known.
   */
  val qualifierExpression: UExpression?

  /**
   * Returns the qualifier type.
   * Can be null if the qualifier is an expression.
   */
  val qualifierType: PsiType?

  /**
   * Returns the callable name.
   */
  val callableName: String

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitCallableReferenceExpression(this)) return
    uAnnotations.acceptList(visitor)
    qualifierExpression?.accept(visitor)
    visitor.afterVisitCallableReferenceExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitCallableReferenceExpression(this, data)

  override fun asLogString(): String = log("name = $callableName")

  override fun asRenderString(): String = buildString {
    qualifierExpression?.let {
      append(it.asRenderString())
    } ?: qualifierType?.let {
      append(it.name)
    }
    append("::")
    append(callableName)
  }
}