// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.java

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.convertOrReport
import org.jetbrains.uast.java.internal.JavaUElementWithComments

@ApiStatus.Internal
open class JavaUMethod(
  override val javaPsi: PsiMethod,
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UMethod, JavaUElementWithComments, UAnchorOwner, PsiMethod by javaPsi {

  @Suppress("OverridingDeprecatedMember")
  override val psi
    get() = javaPsi

  override val sourcePsi: PsiElement?
    get() =
      // hah, there is a Lombok and Enums and also Records, so we have fake PsiElements even in Java (IDEA-216248)
      javaPsi.takeIf { it !is LightElement }

  override val uastBody: UExpression? by lz {
    val body = sourcePsi.asSafely<PsiMethod>()?.body ?: return@lz null
    UastFacade.findPlugin(body)?.convertElement(body, this) as? UExpression
  }

  override val uAnnotations: List<UAnnotation> by lz { javaPsi.annotations.map { JavaUAnnotation(it, this) } }

  override val uastParameters: List<UParameter> by lz {
    javaPsi.parameterList.parameters.mapNotNull { convertOrReport(it, this) }
  }

  override fun getPsiParentForLazyConversion(): PsiElement? = super.getPsiParentForLazyConversion() ?: javaPsi.containingClass

  override val uastAnchor: UIdentifier?
    get() {
      val psiElement = (sourcePsi as? PsiNameIdentifierOwner)?.nameIdentifier // return elements of library sources, do not switch to binary
                       ?: (sourcePsi?.originalElement as? PsiNameIdentifierOwner)?.nameIdentifier
                       ?: sourcePsi.asSafely<PsiMethod>()?.nameIdentifier ?: return null
      return UIdentifier(psiElement, this)
    }

  override fun equals(other: Any?): Boolean = other is JavaUMethod && javaPsi == other.javaPsi
  override fun hashCode(): Int = javaPsi.hashCode()

  companion object {

    fun create(psiRecordHeader: PsiRecordHeader, containingElement: UElement?): JavaUMethod? {
      val lightCanonicalConstructor = psiRecordHeader.containingClass?.constructors
                                        ?.filterIsInstance<LightRecordCanonicalConstructor>()?.firstOrNull() ?: return null
      return JavaRecordConstructorUMethod(psiRecordHeader, lightCanonicalConstructor, containingElement)
    }

    fun create(psi: PsiMethod, languagePlugin: UastLanguagePlugin, containingElement: UElement?): JavaUMethod = when (psi) {
      is LightRecordCanonicalConstructor -> {
        val recordHeader = psi.containingClass.recordHeader
        if (recordHeader != null)
          JavaRecordConstructorUMethod(recordHeader, psi, containingElement)
        else JavaUMethod(psi, containingElement)
      }
      is PsiAnnotationMethod -> JavaUAnnotationMethod(psi, languagePlugin, containingElement)
      else -> JavaUMethod(psi, containingElement)
    }
  }

  override val returnTypeReference: UTypeReferenceExpression? by lz {
    javaPsi.returnTypeElement?.let { JavaUTypeReferenceExpression(it, this) }
  }

  override fun getOriginalElement(): PsiElement? = javaPsi.originalElement
}

private class JavaRecordConstructorUMethod(
  val psiRecordHeader: PsiRecordHeader,
  lightConstructor: LightRecordCanonicalConstructor,
  uastParent: UElement?) : JavaUMethod(lightConstructor, uastParent) {

  override val uastBody: UExpression? get() = null

  override val sourcePsi: PsiElement get() = psiRecordHeader

  override val uastAnchor: UIdentifier?
    get() = psiRecordHeader.containingClass?.nameIdentifier?.let { UIdentifier(it, this) }
}

@ApiStatus.Internal
class JavaUAnnotationMethod(
  override val javaPsi: PsiAnnotationMethod,
  languagePlugin: UastLanguagePlugin,
  containingElement: UElement?
) : JavaUMethod(javaPsi, containingElement), UAnnotationMethod, UDeclarationEx {

  @Suppress("OverridingDeprecatedMember")
  override val psi
    get() = javaPsi

  override val uastDefaultValue: UExpression? by lz {
    val defaultValue = javaPsi.defaultValue ?: return@lz null
    languagePlugin.convertElement(defaultValue, this, null) as? UExpression
  }
}
