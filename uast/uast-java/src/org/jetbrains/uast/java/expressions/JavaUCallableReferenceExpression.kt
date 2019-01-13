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
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMultiResolvable

class JavaUCallableReferenceExpression(
  override val psi: PsiMethodReferenceExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallableReferenceExpression, UMultiResolvable {
  override val qualifierExpression: UExpression? by lz { JavaConverter.convertOrNull(psi.qualifierExpression, this) }

  override val qualifierType: PsiType?
    get() = psi.qualifierType?.type

  override val callableName: String
    get() = psi.referenceName.orAnonymous()

  override fun resolve(): PsiElement? = psi.resolve()

  override fun multiResolve(): Iterable<ResolveResult> = psi.multiResolve(false).asIterable()

  override val resolvedName: String? = (psi.resolve() as? PsiNamedElement)?.name

  override val referenceNameElement: UElement? by lz {
    psi.referenceNameElement?.let { JavaUSimpleNameReferenceExpression(it, callableName, this, it.reference) }
  }


}