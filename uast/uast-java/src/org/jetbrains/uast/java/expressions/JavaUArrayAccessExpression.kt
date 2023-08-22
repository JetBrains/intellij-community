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

import com.intellij.psi.PsiArrayAccessExpression
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

@ApiStatus.Internal
class JavaUArrayAccessExpression(
  override val sourcePsi: PsiArrayAccessExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UArrayAccessExpression {
  override val receiver: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.arrayExpression, this) }
  override val indices: List<UExpression> by lazyPub { singletonListOrEmpty(JavaConverter.convertOrNull(sourcePsi.indexExpression, this)) }

  // No operator overloading in Java (yet?)
  override fun resolve(): PsiElement? = null
}
