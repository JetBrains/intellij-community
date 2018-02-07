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

import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.uast.*

class JavaUQualifiedReferenceExpression(
  override val psi: PsiJavaCodeReferenceElement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UQualifiedReferenceExpression {
  override val receiver by lz {
    psi.qualifier?.let { JavaConverter.convertPsiElement(it, this) as? UExpression } ?: UastEmptyExpression(this)
  }

  override val selector by lz {
    JavaUSimpleNameReferenceExpression(psi.referenceNameElement, psi.referenceName ?: "<error>", this, psi)
  }

  override fun convertParent(): UElement? = super.convertParent().let(this::unwrapCompositeQualifiedReference)

  override val accessType: UastQualifiedExpressionAccessType
    get() = UastQualifiedExpressionAccessType.SIMPLE

  override val resolvedName: String?
    get() = (psi.resolve() as? PsiNamedElement)?.name

  override fun resolve() = psi.resolve()
}

internal fun UElement.unwrapCompositeQualifiedReference(uParent: UElement?): UElement? = when (uParent) {
  is UQualifiedReferenceExpression -> {
    if (uParent.receiver == this || uParent.selector == this)
      uParent
    else {
      val selector = uParent.selector
      if (this is JavaUSimpleNameReferenceExpression || (selector is JavaUCallExpression && selector.valueArgumentCount > 0))
        selector
      else uParent.uastParent
    }
  }
  else -> uParent
}