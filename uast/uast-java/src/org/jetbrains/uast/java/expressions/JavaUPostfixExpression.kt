// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiPostfixExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUPostfixExpression(
  override val sourcePsi: PsiPostfixExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPostfixExpression {
  override val operand: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.operand, this) }

  override val operatorIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.operationSign, this)

  override fun resolveOperator(): Nothing? = null

  override val operator: UastPostfixOperator = when (sourcePsi.operationTokenType) {
    JavaTokenType.PLUSPLUS -> UastPostfixOperator.INC
    JavaTokenType.MINUSMINUS -> UastPostfixOperator.DEC
    else -> UastPostfixOperator.UNKNOWN
  }
}
