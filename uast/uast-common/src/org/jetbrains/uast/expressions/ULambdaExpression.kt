/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitLambdaExpression(this)) return
    annotations.acceptList(visitor)
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
