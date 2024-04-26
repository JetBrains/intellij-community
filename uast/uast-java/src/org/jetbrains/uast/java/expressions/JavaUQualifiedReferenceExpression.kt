// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUQualifiedReferenceExpression(
  override val sourcePsi: PsiJavaCodeReferenceElement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UQualifiedReferenceExpression, UMultiResolvable {
  private val receiverPart = UastLazyPart<UExpression>()
  private val selectorPart = UastLazyPart<USimpleNameReferenceExpression>()

  override val receiver: UExpression
    get() = receiverPart.getOrBuild {
      sourcePsi.qualifier
        ?.let { JavaConverter.convertPsiElement(it, this, UElement::class.java) as? UExpression }
      ?: UastEmptyExpression(this)
    }

  override val selector: USimpleNameReferenceExpression
    get() = selectorPart.getOrBuild {
      JavaUSimpleNameReferenceExpression(sourcePsi.referenceNameElement, sourcePsi.referenceName ?: "<error>", this, sourcePsi)
    }

  override val accessType: UastQualifiedExpressionAccessType
    get() = UastQualifiedExpressionAccessType.SIMPLE

  override val resolvedName: String?
    get() = (sourcePsi.resolve() as? PsiNamedElement)?.name

  override fun resolve(): PsiElement? = sourcePsi.resolve()

  override fun multiResolve(): Iterable<ResolveResult> = sourcePsi.multiResolve(false).asIterable()
}

internal fun UElement.unwrapCompositeQualifiedReference(uParent: UElement?): UElement? = when (uParent) {
  is UQualifiedReferenceExpression -> {
    if (uParent.receiver == this || uParent.selector == this)
      uParent
    else uParent.selector as? JavaUCallExpression ?: uParent.uastParent
  }
  else -> uParent
}
