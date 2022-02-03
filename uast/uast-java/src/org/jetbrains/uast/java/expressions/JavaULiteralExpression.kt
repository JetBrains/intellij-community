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

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.expressions.UInjectionHost

@ApiStatus.Internal
class JavaULiteralExpression(
  override val sourcePsi: PsiLiteralExpressionImpl,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), ULiteralExpression, UInjectionHost {
  override fun evaluate(): Any? = sourcePsi.value
  override val value: Any? by lz { evaluate() }

  override val isString: Boolean
    get() = super<UInjectionHost>.isString

  override val psiLanguageInjectionHost: PsiLanguageInjectionHost
    get() = sourcePsi

}
