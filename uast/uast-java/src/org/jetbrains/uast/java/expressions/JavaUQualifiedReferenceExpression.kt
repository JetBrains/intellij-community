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
  override val receiver: UExpression by lz {
    sourcePsi.qualifier?.let { JavaConverter.convertPsiElement(it, this) as? UExpression } ?: UastEmptyExpression(this)
  }

  override val selector: USimpleNameReferenceExpression by lz {
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
