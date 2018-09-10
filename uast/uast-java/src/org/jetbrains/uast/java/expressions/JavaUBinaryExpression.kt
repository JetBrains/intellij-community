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

import com.intellij.psi.PsiBinaryExpression
import org.jetbrains.uast.*

class JavaUBinaryExpression(
  override val psi: PsiBinaryExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBinaryExpression {
  override val leftOperand: UExpression by lz { JavaConverter.convertOrEmpty(psi.lOperand, this) }
  override val rightOperand: UExpression by lz { JavaConverter.convertOrEmpty(psi.rOperand, this) }
  override val operator: UastBinaryOperator by lz { psi.operationTokenType.getOperatorType() }

  override val operatorIdentifier: UIdentifier
    get() = UIdentifier(psi.operationSign, this)

  override fun resolveOperator(): Nothing? = null
}