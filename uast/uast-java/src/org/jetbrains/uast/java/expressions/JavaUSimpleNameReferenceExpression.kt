// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.*
import com.intellij.psi.infos.CandidateInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUSimpleNameReferenceExpression(
  override val sourcePsi: PsiElement?,
  override val identifier: String,
  givenParent: UElement?,
  val reference: PsiReference? = null
) : JavaAbstractUExpression(givenParent), USimpleNameReferenceExpression, UMultiResolvable {

  override fun resolve(): PsiElement? = (reference ?: sourcePsi as? PsiReference)?.resolve()

  override fun multiResolve(): Iterable<ResolveResult> =
    (reference as? PsiPolyVariantReference ?: sourcePsi as? PsiPolyVariantReference)?.multiResolve(false)?.asIterable()
    ?: listOfNotNull(resolve()?.let { CandidateInfo(it, PsiSubstitutor.EMPTY) })

  override val resolvedName: String?
    get() = ((reference ?: sourcePsi as? PsiReference)?.resolve() as? PsiNamedElement)?.name

  override fun getPsiParentForLazyConversion(): PsiElement? {
    val parent = super.getPsiParentForLazyConversion()
    if (parent is PsiReferenceExpression && parent.parent is PsiMethodCallExpression) {
      return parent.parent
    }
    else if (parent is PsiAnonymousClass) {
      return parent.parent
    }
    return parent
  }

  override fun convertParent(): UElement? = super.convertParent().let(this::unwrapCompositeQualifiedReference)


  override val referenceNameElement: UElement?
    get() = when (sourcePsi) {
      is PsiJavaCodeReferenceElement -> sourcePsi.referenceNameElement.toUElement()
      else -> this
    }

}

@ApiStatus.Internal
class JavaUTypeReferenceExpression(
  override val sourcePsi: PsiTypeElement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UTypeReferenceExpression {
  override val type: PsiType
    get() = sourcePsi.type
}

@ApiStatus.Internal
class LazyJavaUTypeReferenceExpression(
  override val sourcePsi: PsiElement,
  givenParent: UElement?,
  private val typeSupplier: () -> PsiType
) : JavaAbstractUExpression(givenParent), UTypeReferenceExpression {
  private val typePart = UastLazyPart<PsiType>()

  override val type: PsiType
    get() = typePart.getOrBuild(typeSupplier)
}
