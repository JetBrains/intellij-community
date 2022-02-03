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
  override val type: PsiType by lz { typeSupplier() }
}
