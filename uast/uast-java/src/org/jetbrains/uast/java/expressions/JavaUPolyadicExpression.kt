// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiPolyadicExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.java.internal.PsiArrayToUElementListMappingView

@ApiStatus.Internal
class JavaUPolyadicExpression(
  override val sourcePsi: PsiPolyadicExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPolyadicExpression {
  private val operatorPart = UastLazyPart<UastBinaryOperator>()

  override val operands: List<UExpression> = PsiArrayToUElementListMappingView(sourcePsi.operands) {
    JavaConverter.convertOrEmpty(it, this)
  }

  override val operator: UastBinaryOperator
    get() = operatorPart.getOrBuild { sourcePsi.operationTokenType.getOperatorType() }
}
