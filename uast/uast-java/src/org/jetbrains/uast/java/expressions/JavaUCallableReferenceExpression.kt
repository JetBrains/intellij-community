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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMultiResolvable

@ApiStatus.Internal
class JavaUCallableReferenceExpression(
  override val sourcePsi: PsiMethodReferenceExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallableReferenceExpression, UMultiResolvable {
  override val qualifierExpression: UExpression? by lz { JavaConverter.convertOrNull(sourcePsi.qualifierExpression, this) }

  override val qualifierType: PsiType?
    get() = sourcePsi.qualifierType?.type

  override val callableName: String
    get() = sourcePsi.referenceName.orAnonymous()

  override fun resolve(): PsiElement? = sourcePsi.resolve()

  override fun multiResolve(): Iterable<ResolveResult> = sourcePsi.multiResolve(false).asIterable()

  override val resolvedName: String?
    get() = (sourcePsi.resolve() as? PsiNamedElement)?.name

  override val referenceNameElement: UElement? by lz {
    sourcePsi.referenceNameElement?.let { JavaUSimpleNameReferenceExpression(it, callableName, this, it.reference) }
  }

}
