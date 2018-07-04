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
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

interface UTypeReferenceExpression : UExpression {
  /**
   * Returns the resolved type for this reference.
   */
  val type: PsiType

  /**
   * Returns the qualified name of the class type, or null if the [type] is not a class type.
   */
  fun getQualifiedName(): String? = PsiTypesUtil.getPsiClass(type)?.qualifiedName

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitTypeReferenceExpression(this)) return
    annotations.acceptList(visitor)
    visitor.afterVisitTypeReferenceExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitTypeReferenceExpression(this, data)

  override fun asLogString(): String = log("name = ${type.name}")

  override fun asRenderString(): String = type.name
}