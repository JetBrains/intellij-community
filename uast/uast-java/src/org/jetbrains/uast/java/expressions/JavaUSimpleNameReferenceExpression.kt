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

class JavaUSimpleNameReferenceExpression(
  override val psi: PsiElement?,
  override val identifier: String,
  givenParent: UElement?,
  val reference: PsiReference? = null
) : JavaAbstractUExpression(givenParent), USimpleNameReferenceExpression, UMultiResolvable {

  override fun resolve(): PsiElement? = (reference ?: psi as? PsiReference)?.resolve()

  override fun multiResolve(): Iterable<ResolveResult> =
    (reference as? PsiPolyVariantReference ?: psi as? PsiPolyVariantReference)?.multiResolve(false)?.asIterable()
    ?: listOfNotNull(resolve()?.let { CandidateInfo(it, PsiSubstitutor.EMPTY) })

  override val resolvedName: String?
    get() = ((reference ?: psi as? PsiReference)?.resolve() as? PsiNamedElement)?.name

  override fun getPsiParentForLazyConversion(): PsiElement? {
    val parent = super.getPsiParentForLazyConversion()
    if (parent is PsiReferenceExpression && parent.parent is PsiMethodCallExpression) {
      return parent.parent
    }
    return parent
  }

  override fun convertParent(): UElement? = super.convertParent().let(this::unwrapCompositeQualifiedReference)


  override val referenceNameElement: UElement?
    get() = when (psi) {
      is PsiJavaCodeReferenceElement -> psi.referenceNameElement.toUElement()
      else -> this
    }

}

class JavaUTypeReferenceExpression(
  override val psi: PsiTypeElement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UTypeReferenceExpression {
  override val type: PsiType
    get() = psi.type
}

class LazyJavaUTypeReferenceExpression(
  override val psi: PsiElement,
  givenParent: UElement?,
  private val typeSupplier: () -> PsiType
) : JavaAbstractUExpression(givenParent), UTypeReferenceExpression {
  override val type: PsiType by lz { typeSupplier() }
}

@Deprecated("no known usages, to be removed in IDEA 2019.2")
@ApiStatus.ScheduledForRemoval(inVersion = "2019.2")
class JavaClassUSimpleNameReferenceExpression(
  override val identifier: String,
  val ref: PsiJavaReference,
  override val psi: PsiElement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), USimpleNameReferenceExpression, UMultiResolvable {
  override fun resolve(): PsiElement? = ref.resolve()
  override fun multiResolve(): Iterable<ResolveResult> = ref.multiResolve(false).asIterable()

  override val resolvedName: String?
    get() = (ref.resolve() as? PsiNamedElement)?.name
}