// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import com.intellij.psi.PsiType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents the lambda expression.
 */
interface ULambdaExpression : UExpression {
  /**
   * Returns the list of lambda value parameters.
   */
  val valueParameters: List<UParameter>

  /**
   * Returns the lambda body expression.
   */
  val body: UExpression

  /**
   * Returns SAM type the lambda expression corresponds to or null when no SAM type could be found
   */
  val functionalInterfaceType: PsiType?

  /**
   * Returns the list of all lambda parameters including this, it and e.t.c.
   * It is more preferable than [valueParameters], because this parameters corresponds to parameters of functional interface method, which can be retrieved from [getExpressionType]
   */
  val parameters: List<UParameter>
    get() = valueParameters

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitLambdaExpression(this)) return
    uAnnotations.acceptList(visitor)
    valueParameters.acceptList(visitor)
    body.accept(visitor)
    visitor.afterVisitLambdaExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitLambdaExpression(this, data)

  override fun asLogString(): String = log()

  override fun asRenderString(): String {
    val renderedValueParameters = if (valueParameters.isEmpty())
      ""
    else
      valueParameters.joinToString { it.asRenderString() } + " ->" + LINE_SEPARATOR

    return "{ " + renderedValueParameters + body.asRenderString().withMargin + LINE_SEPARATOR + "}"
  }
}
