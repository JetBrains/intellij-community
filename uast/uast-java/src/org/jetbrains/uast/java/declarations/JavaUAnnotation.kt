// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private val attributeValuesPart = UastLazyPart<List<UNamedExpression>>()

  override val javaPsi: PsiAnnotation = sourcePsi

  override val qualifiedName: String?
    get() = sourcePsi.qualifiedName

  override val attributeValues: List<UNamedExpression>
    get() = attributeValuesPart.getOrBuild {
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
