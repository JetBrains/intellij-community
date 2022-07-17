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
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.expressions.JavaUNamedExpression

@ApiStatus.Internal
class JavaUAnnotation(
  override val sourcePsi: PsiAnnotation,
  givenParent: UElement?
) : JavaAbstractUElement(givenParent), UAnnotationEx, UAnchorOwner, UMultiResolvable {

  override val javaPsi: PsiAnnotation = sourcePsi

  override val qualifiedName: String?
    get() = sourcePsi.qualifiedName

  override val attributeValues: List<UNamedExpression> by lz {
    val attributes = sourcePsi.parameterList.attributes

    attributes.map { attribute -> JavaUNamedExpression(attribute, this) }
  }

  override val uastAnchor: UIdentifier?
    get() = sourcePsi.nameReferenceElement?.referenceNameElement?.let { UIdentifier(it, this) }

  override fun resolve(): PsiClass? = sourcePsi.nameReferenceElement?.resolve() as? PsiClass

  override fun multiResolve(): Iterable<ResolveResult> =
    sourcePsi.nameReferenceElement?.multiResolve(false)?.asIterable() ?: emptyList()

  override fun findAttributeValue(name: String?): UExpression? {
    val attributeValue = sourcePsi.findAttributeValue(name) ?: return null
    return UastFacade.convertElement(attributeValue, this, null) as? UExpression ?: UastEmptyExpression(this)
  }

  override fun findDeclaredAttributeValue(name: String?): UExpression? {
    val attributeValue = sourcePsi.findDeclaredAttributeValue(name) ?: return null
    return UastFacade.convertElement(attributeValue, this, null) as? UExpression ?: UastEmptyExpression(this)
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
