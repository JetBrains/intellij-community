// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.java

import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments

open class JavaUMethod(
  psi: PsiMethod,
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UMethodTypeSpecific, JavaUElementWithComments, UAnchorOwner, PsiMethod by psi {
  override val psi: PsiMethod
    get() = javaPsi

  override val javaPsi: PsiMethod = unwrap<UMethod, PsiMethod>(psi)

  override val uastBody: UExpression? by lz {
    val body = psi.body ?: return@lz null
    getLanguagePlugin().convertElement(body, this) as? UExpression
  }

  override val annotations: List<JavaUAnnotation> by lz { psi.annotations.map { JavaUAnnotation(it, this) } }

  override val uastParameters: List<JavaUParameter> by lz {
    psi.parameterList.parameters.map { JavaUParameter(it, this) }
  }

  override val isOverride: Boolean
    get() = psi.modifierList.hasAnnotation("java.lang.Override")

  override val uastAnchor: UIdentifier
    get() = UIdentifier((psi.originalElement as? PsiNameIdentifierOwner)?.nameIdentifier ?: psi.nameIdentifier, this)

  override fun equals(other: Any?): Boolean = other is JavaUMethod && psi == other.psi
  override fun hashCode(): Int = psi.hashCode()

  companion object {
    fun create(psi: PsiMethod, languagePlugin: UastLanguagePlugin, containingElement: UElement?): JavaUMethod = when (psi) {
      is PsiAnnotationMethod -> JavaUAnnotationMethod(psi, languagePlugin, containingElement)
      else -> JavaUMethod(psi, containingElement)
    }
  }
}

class JavaUAnnotationMethod(
  override val psi: PsiAnnotationMethod,
  languagePlugin: UastLanguagePlugin,
  containingElement: UElement?
) : JavaUMethod(psi, containingElement), UAnnotationMethod {

  override val javaPsi: PsiAnnotationMethod
    get() = psi

  override val uastDefaultValue: UExpression? by lz {
    val defaultValue = psi.defaultValue ?: return@lz null
    languagePlugin.convertElement(defaultValue, this, null) as? UExpression
  }
}