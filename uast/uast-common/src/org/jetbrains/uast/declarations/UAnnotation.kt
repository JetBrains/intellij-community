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

import com.intellij.psi.PsiClass
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * An annotation wrapper to be used in [UastVisitor].
 */
interface UAnnotation : UElement, UResolvable {
  /**
   * Returns the annotation qualified name.
   */
  val qualifiedName: String?

  /**
   * Returns the annotation class, or null if the class reference was not resolved.
   */
  override fun resolve(): PsiClass?

  /**
   * Returns the annotation values.
   */
  val attributeValues: List<UNamedExpression>

  fun findAttributeValue(name: String?): UExpression?

  fun findDeclaredAttributeValue(name: String?): UExpression?

  override fun asRenderString() = buildString {
    append("@")
    append(qualifiedName)
    if (attributeValues.isNotEmpty()) {
      attributeValues.joinTo(
        buffer = this,
        prefix = "(",
        postfix = ")",
        transform = UNamedExpression::asRenderString)
    }
  }

  override fun asLogString() = log("fqName = $qualifiedName")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitAnnotation(this)) return
    attributeValues.acceptList(visitor)
    visitor.afterVisitAnnotation(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) =
    visitor.visitAnnotation(this, data)
}

interface UAnnotationEx : UAnnotation {
  val uastAnchor: UElement?
}