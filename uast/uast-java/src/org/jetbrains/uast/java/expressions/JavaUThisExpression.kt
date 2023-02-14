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
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.UThisExpression

@ApiStatus.Internal
class JavaUThisExpression(
  override val sourcePsi: PsiThisExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UThisExpression, UMultiResolvable {
  override val label: String?
    get() = sourcePsi.qualifier?.qualifiedName

  override val labelIdentifier: UIdentifier?
    get() = sourcePsi.qualifier?.let { UIdentifier(it, this) }

  override fun resolve(): PsiElement? = sourcePsi.qualifier?.resolve()
  override fun multiResolve(): Iterable<ResolveResult> =
    sourcePsi.qualifier?.multiResolve(false)?.asIterable() ?: emptyList()
}
