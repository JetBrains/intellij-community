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
package org.jetbrains.uast.java

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import org.jetbrains.uast.*
import org.jetbrains.uast.java.expressions.JavaUNamedExpression

class JavaUAnnotation(
  override val psi: PsiAnnotation,
  givenParent: UElement?
) : JavaAbstractUElement(givenParent), UAnnotationEx {
  override val qualifiedName: String?
    get() = psi.qualifiedName

  override val attributeValues: List<UNamedExpression> by lz {
    val attributes = psi.parameterList.attributes

    attributes.map { attribute -> JavaUNamedExpression(attribute, this) }
  }

  override val uastAnchor: UElement?
    get() = psi.nameReferenceElement?.referenceNameElement?.let { UIdentifier(it, this) }

  override fun resolve(): PsiClass? = psi.nameReferenceElement?.resolve() as? PsiClass

  override fun findAttributeValue(name: String?): UExpression? {
    val context = getUastContext()
    val attributeValue = psi.findAttributeValue(name) ?: return null
    return context.convertElement(attributeValue, this, null) as? UExpression ?: UastEmptyExpression(this)
  }

  override fun findDeclaredAttributeValue(name: String?): UExpression? {
    val context = getUastContext()
    val attributeValue = psi.findDeclaredAttributeValue(name) ?: return null
    return context.convertElement(attributeValue, this, null) as? UExpression ?: UastEmptyExpression(this)
  }

  companion object {
    @JvmStatic
    fun wrap(annotation: PsiAnnotation): UAnnotation = JavaUAnnotation(annotation, null)

    @JvmStatic
    fun wrap(annotations: List<PsiAnnotation>): List<UAnnotation> = annotations.map { JavaUAnnotation(it, null) }

    @JvmStatic
    fun wrap(annotations: Array<PsiAnnotation>): List<UAnnotation> = annotations.map { JavaUAnnotation(it, null) }
  }
}