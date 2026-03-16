// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
class JavaUTypeCastExpression(
  override val sourcePsi: PsiTypeCastExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBinaryExpressionWithType {
  private val operandPart = UastLazyPart<UExpression>()
  private val typeReferencePart = UastLazyPart<UTypeReferenceExpression?>()

  override val operand: UExpression
    get() = operandPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.operand, this) }

  override val type: PsiType
    get() = sourcePsi.castType?.type ?: UastErrorType

  override val typeReference: UTypeReferenceExpression?
    get() = typeReferencePart.getOrBuild { sourcePsi.castType?.let { JavaUTypeReferenceExpression(it, this) } }

  override val operationKind: UastBinaryExpressionWithTypeKind
    get() = UastBinaryExpressionWithTypeKind.TypeCast.INSTANCE
}
