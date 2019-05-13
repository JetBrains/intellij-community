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
    annotations.acceptList(visitor)
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