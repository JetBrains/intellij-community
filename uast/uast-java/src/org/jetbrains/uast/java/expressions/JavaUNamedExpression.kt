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
package org.jetbrains.uast.java.expressions

import com.intellij.psi.PsiNameValuePair
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.java.JavaAbstractUExpression
import org.jetbrains.uast.java.JavaConverter
import org.jetbrains.uast.java.lz

class JavaUNamedExpression(
  override val psi: PsiNameValuePair,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UNamedExpression {
  override fun evaluate(): Any? = expression.evaluate()

  override val name: String?
    get() = psi.name

  override val expression: UExpression by lz {
    psi.value?.let { value -> JavaConverter.convertPsiElement(value, this) } as? UExpression ?: UastEmptyExpression(this)
  }
}
