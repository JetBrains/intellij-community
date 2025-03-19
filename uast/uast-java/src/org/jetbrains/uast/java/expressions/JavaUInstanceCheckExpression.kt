// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiInstanceOfExpression
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUInstanceCheckExpression(
  override val sourcePsi: PsiInstanceOfExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBinaryExpressionWithType {

  private val operandPart = UastLazyPart<UExpression>()
  private val typeReferencePart = UastLazyPart<JavaUTypeReferenceExpression?>()

  override val operand: UExpression
    get() = operandPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.operand, this) }

  override val typeReference: JavaUTypeReferenceExpression?
    get() = typeReferencePart.getOrBuild {
      sourcePsi.checkType?.let { JavaUTypeReferenceExpression(it, this) }
    }

  override val type: PsiType
    get() = sourcePsi.checkType?.type ?: UastErrorType

  override val operationKind: UastBinaryExpressionWithTypeKind
    get() = UastBinaryExpressionWithTypeKind.InstanceCheck.INSTANCE
}
