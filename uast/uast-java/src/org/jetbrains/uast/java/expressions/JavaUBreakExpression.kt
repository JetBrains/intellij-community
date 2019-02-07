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

import com.intellij.psi.PsiBreakStatement
import com.intellij.psi.PsiExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UBreakWithValueExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class JavaUBreakExpression(
  override val psi: PsiBreakStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBreakExpression {
  override val label: String?
    get() = psi.labelIdentifier?.text
}

class JavaUBreakWithValueExpression(
  override val psi: PsiBreakStatement,
  val psiExpression: PsiExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBreakWithValueExpression {
  override val valueExpression: UExpression? by lazy {
    JavaConverter.convertExpression(psiExpression, this)
  }
  override val label: String?
    get() = psi.labelIdentifier?.text
}