// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast


import com.intellij.psi.PsiType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a binary expression with type (value op type), e.g. ("A" instanceof String).
 */
interface UBinaryExpressionWithType : UExpression {
  /**
   * Returns the operand expression.
   */
  val operand: UExpression

  /**
   * Returns the operation kind.
   */
  val operationKind: UastBinaryExpressionWithTypeKind

  /**
   * Returns the type reference of this expression.
   */
  val typeReference: UTypeReferenceExpression?

  /**
   * Returns the type.
   */
  val type: PsiType

  override fun asLogString(): String = log()

  override fun asRenderString(): String = "${operand.asRenderString()} ${operationKind.name} ${type.name}"

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitBinaryExpressionWithType(this)) return
    uAnnotations.acceptList(visitor)
    operand.accept(visitor)
    typeReference?.accept(visitor)
    visitor.afterVisitBinaryExpressionWithType(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitBinaryExpressionWithType(this, data)
}