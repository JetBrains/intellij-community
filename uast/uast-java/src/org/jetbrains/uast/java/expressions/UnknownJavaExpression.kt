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

import com.intellij.psi.PsiElement
import org.jetbrains.uast.JvmDeclarationUElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class UnknownJavaExpression(
  override val psi: PsiElement,
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UExpression, JvmDeclarationUElement {
  override fun asLogString(): String = "[!] " + UnknownJavaExpression::class.java.simpleName + " ($psi)"

  override val annotations: List<UAnnotation>
    get() = emptyList()
}