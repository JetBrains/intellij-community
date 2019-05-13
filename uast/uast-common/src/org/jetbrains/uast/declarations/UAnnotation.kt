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

import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * An annotation wrapper to be used in [UastVisitor].
 */
interface UAnnotation : UElement, UResolvable {

  override val javaPsi: PsiAnnotation?
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

  override fun asRenderString(): String = buildString {
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

  override fun asLogString(): String = log("fqName = $qualifiedName")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitAnnotation(this)) return
    attributeValues.acceptList(visitor)
    visitor.afterVisitAnnotation(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitAnnotation(this, data)
}

/**
 * Handy method to get psiElement for reporting warnings and putting gutters
 */
val UAnnotation?.namePsiElement: PsiElement?
  get() {
    if (this is UAnnotationEx) return this.uastAnchor?.sourcePsi
    // A workaround for IDEA-184211
    val sourcePsiElement = this?.sourcePsiElement ?: return null
    val identifier = sourcePsiElement.navigationElement ?: return null
    val qualifiedName = this.qualifiedName
    if (qualifiedName != null) {
      val shortName = StringUtilRt.getShortName(qualifiedName)
      SyntaxTraverser.psiTraverser(sourcePsiElement)
        .filter { psi -> psi.references.isNotEmpty() && psi.text.contains(shortName) }
        .traverse()
        .first()
        ?.let {
          return PsiTreeUtil.getDeepestFirst(it)
        }
    }

    return PsiTreeUtil.getDeepestFirst(identifier)
  }


interface UAnnotationEx : UAnnotation, UAnchorOwner