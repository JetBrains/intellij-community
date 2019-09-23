// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.java

import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments

open class JavaUMethod(
  override val sourcePsi: PsiMethod,
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UMethod, JavaUElementWithComments, UAnchorOwner, PsiMethod by sourcePsi {


  @Suppress("OverridingDeprecatedMember")
  override val psi get() = sourcePsi

  override val javaPsi: PsiMethod get() = sourcePsi

  override val uastBody: UExpression? by lz {
    val body = sourcePsi.body ?: return@lz null
    UastFacade.findPlugin(body)?.convertElement(body, this) as? UExpression
  }

  override val uAnnotations: List<JavaUAnnotation> by lz { sourcePsi.annotations.map { JavaUAnnotation(it, this) } }

  override val uastParameters: List<JavaUParameter> by lz {
    sourcePsi.parameterList.parameters.map { JavaUParameter(it, this) }
  }

  override val uastAnchor: UIdentifier?
    get() {
      val psiElement = (sourcePsi as? PsiNameIdentifierOwner)?.nameIdentifier // return elements of library sources, do not switch to binary
                       ?: (sourcePsi.originalElement as? PsiNameIdentifierOwner)?.nameIdentifier
                       ?: sourcePsi.nameIdentifier
      if (psiElement?.isPhysical != true) return null // hah there is a Lombok and we have fake PsiElements even in Java (IDEA-216248)
      return UIdentifier(psiElement, this)
    }

  override fun equals(other: Any?): Boolean = other is JavaUMethod && sourcePsi == other.sourcePsi
  override fun hashCode(): Int = sourcePsi.hashCode()

  companion object {
    fun create(psi: PsiMethod, languagePlugin: UastLanguagePlugin, containingElement: UElement?): JavaUMethod = when (psi) {
      is PsiAnnotationMethod -> JavaUAnnotationMethod(psi, languagePlugin, containingElement)
      else -> JavaUMethod(psi, containingElement)
    }
  }

  override val returnTypeReference: UTypeReferenceExpression? by lz {
    sourcePsi.returnTypeElement?.let { JavaUTypeReferenceExpression(it, this) }
  }

  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement
}

class JavaUAnnotationMethod(
  override val sourcePsi: PsiAnnotationMethod,
  languagePlugin: UastLanguagePlugin,
  containingElement: UElement?
) : JavaUMethod(sourcePsi, containingElement), UAnnotationMethod, UDeclarationEx {

  override val javaPsi: PsiAnnotationMethod
    get() = sourcePsi

  @Suppress("OverridingDeprecatedMember")
  override val psi
    get() = sourcePsi

  override val uastDefaultValue: UExpression? by lz {
    val defaultValue = sourcePsi.defaultValue ?: return@lz null
    languagePlugin.convertElement(defaultValue, this, null) as? UExpression
  }
}