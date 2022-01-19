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

import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiPostfixExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUPostfixExpression(
  override val sourcePsi: PsiPostfixExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPostfixExpression {
  override val operand: UExpression by lz { JavaConverter.convertOrEmpty(sourcePsi.operand, this) }

  override val operatorIdentifier: UIdentifier?
    get() = UIdentifier(sourcePsi.operationSign, this)

  override fun resolveOperator(): Nothing? = null

  override val operator: UastPostfixOperator = when (sourcePsi.operationTokenType) {
    JavaTokenType.PLUSPLUS -> UastPostfixOperator.INC
    JavaTokenType.MINUSMINUS -> UastPostfixOperator.DEC
    else -> UastPostfixOperator.UNKNOWN
  }
}
