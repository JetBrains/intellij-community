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
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUCompositeQualifiedExpression(
  override val sourcePsi: PsiElement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UQualifiedReferenceExpression, UMultiResolvable {

  internal lateinit var receiverInitializer: () -> UExpression

  override val receiver: UExpression by lazy { receiverInitializer() }

  override lateinit var selector: UExpression
    internal set

  override val resolvedName: String?
    get() = (resolve() as? PsiNamedElement)?.name

  override fun resolve(): PsiElement? = (selector as? UResolvable)?.resolve()

  override fun multiResolve(): Iterable<ResolveResult> =
    (selector as? UMultiResolvable)?.multiResolve() ?: emptyList()

  override val accessType: UastQualifiedExpressionAccessType
    get() = UastQualifiedExpressionAccessType.SIMPLE

  override val referenceNameElement: UElement?
    get() =
      when (val selector = selector) {
        is UCallExpression -> selector.methodIdentifier
        else -> unwrapReferenceNameElement(selector)
      }
}
