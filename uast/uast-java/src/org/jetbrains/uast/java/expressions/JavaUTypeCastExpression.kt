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

import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUTypeCastExpression(
  override val sourcePsi: PsiTypeCastExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBinaryExpressionWithType {
  override val operand: UExpression by lz { JavaConverter.convertOrEmpty(sourcePsi.operand, this) }

  override val type: PsiType
    get() = sourcePsi.castType?.type ?: UastErrorType

  override val typeReference: UTypeReferenceExpression? by lz { sourcePsi.castType?.let { JavaUTypeReferenceExpression(it, this) } }

  override val operationKind: UastBinaryExpressionWithTypeKind
    get() = UastBinaryExpressionWithTypeKind.TypeCast.INSTANCE
}
