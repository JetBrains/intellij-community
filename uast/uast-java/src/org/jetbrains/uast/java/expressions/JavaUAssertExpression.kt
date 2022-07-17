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

import com.intellij.psi.PsiAssertStatement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUAssertExpression(
  override val sourcePsi: PsiAssertStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpression, UMultiResolvable {
  val condition: UExpression by lz { JavaConverter.convertOrEmpty(sourcePsi.assertCondition, this) }
  val message: UExpression? by lz { JavaConverter.convertOrNull(sourcePsi.assertDescription, this) }

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiAssertStatement
    get() = sourcePsi

  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference: UReferenceExpression?
    get() = null

  override val methodName: String
    get() = "assert"

  override val receiver: UExpression?
    get() = null

  override val receiverType: PsiType?
    get() = null

  override val valueArgumentCount: Int
    get() = if (message != null) 2 else 1

  override val valueArguments: List<UExpression> by lz {
    val message = this.message
    if (message != null) listOf(condition, message) else listOf(condition)
  }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val typeArgumentCount: Int
    get() = 0

  override val typeArguments: List<PsiType>
    get() = emptyList()

  override val returnType: PsiType
    get() = PsiType.VOID

  override val kind: UastCallKind
    get() = JavaUastCallKinds.ASSERT

  override fun resolve(): Nothing? = null

  override fun multiResolve(): Iterable<ResolveResult> = emptyList()
}
