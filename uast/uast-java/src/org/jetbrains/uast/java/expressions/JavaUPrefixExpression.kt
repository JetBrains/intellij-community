// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiPrefixExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUPrefixExpression(
  override val sourcePsi: PsiPrefixExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPrefixExpression {
  private val operandPart = UastLazyPart<UExpression>()

  override val operand: UExpression
    get() = operandPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.operand, this) }

  override val operatorIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.operationSign, this)

  override fun resolveOperator(): Nothing? = null

  override val operator: UastPrefixOperator = when (sourcePsi.operationTokenType) {
    JavaTokenType.PLUS -> UastPrefixOperator.UNARY_PLUS
    JavaTokenType.MINUS -> UastPrefixOperator.UNARY_MINUS
    JavaTokenType.PLUSPLUS -> UastPrefixOperator.INC
    JavaTokenType.MINUSMINUS -> UastPrefixOperator.DEC
    JavaTokenType.EXCL -> UastPrefixOperator.LOGICAL_NOT
    JavaTokenType.TILDE -> UastPrefixOperator.BITWISE_NOT
    else -> UastPrefixOperator.UNKNOWN
  }
}
