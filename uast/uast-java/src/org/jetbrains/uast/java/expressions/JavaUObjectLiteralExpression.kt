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

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUObjectLiteralExpression(
  override val sourcePsi: PsiNewExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UObjectLiteralExpression, UCallExpression, UMultiResolvable {
  override val declaration: UClass by lz { JavaUClass.create(sourcePsi.anonymousClass!!, this) }

  override val classReference: UReferenceExpression? by lz {
    sourcePsi.classReference?.let { ref ->
      JavaConverter.convertReference(ref, this) as? UReferenceExpression
    }
  }

  override val valueArgumentCount: Int
    get() = sourcePsi.argumentList?.expressions?.size ?: 0

  override val valueArguments: List<UExpression> by lz {
    sourcePsi.argumentList?.expressions?.map { JavaConverter.convertOrEmpty(it, this) } ?: emptyList()
  }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val typeArgumentCount: Int by lz { sourcePsi.classReference?.typeParameters?.size ?: 0 }

  override val typeArguments: List<PsiType>
    get() = sourcePsi.classReference?.typeParameters?.toList() ?: emptyList()

  override fun resolve(): PsiMethod? = sourcePsi.resolveMethod()

  override fun multiResolve(): Iterable<ResolveResult> =
    sourcePsi.classReference?.multiResolve(false)?.asIterable() ?: emptyList()
}
