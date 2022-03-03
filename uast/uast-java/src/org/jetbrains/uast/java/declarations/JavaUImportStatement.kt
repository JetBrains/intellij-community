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
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UMultiResolvable

@ApiStatus.Internal
class JavaUImportStatement(
  override val sourcePsi: PsiImportStatementBase,
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UImportStatement, UMultiResolvable {
  override val isOnDemand: Boolean
    get() = sourcePsi.isOnDemand
  override val importReference: UElement? by lz { sourcePsi.importReference?.let { JavaDumbUElement(it, this, it.qualifiedName) } }
  override fun resolve(): PsiElement? = sourcePsi.resolve()
  override fun multiResolve(): Iterable<ResolveResult> =
    sourcePsi.importReference?.multiResolve(false)?.asIterable() ?: emptyList()

  @Suppress("OverridingDeprecatedMember")
  override val psi get() = sourcePsi
}
