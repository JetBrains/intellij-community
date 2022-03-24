// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.java

import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.internal.JavaUElementWithComments

@ApiStatus.Internal
class JavaUClassInitializer(
  override val sourcePsi: PsiClassInitializer,
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UClassInitializerEx, JavaUElementWithComments, UAnchorOwner, PsiClassInitializer by sourcePsi {

  @Suppress("OverridingDeprecatedMember")
  override val psi get() = sourcePsi

  override val javaPsi: PsiClassInitializer = sourcePsi

  override val uastAnchor: UIdentifier?
    get() = null

  override val uastBody: UExpression by lz {
    UastFacade.findPlugin(sourcePsi.body)?.convertElement(sourcePsi.body, this, null) as? UExpression ?: UastEmptyExpression(this)
  }

  override val uAnnotations: List<UAnnotation> by lz { sourcePsi.annotations.map { JavaUAnnotation(it, this) } }

  override fun equals(other: Any?): Boolean = this === other
  override fun hashCode(): Int = sourcePsi.hashCode()
  override fun getOriginalElement(): PsiElement? = sourcePsi.originalElement
}
