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

import com.intellij.psi.PsiSynchronizedStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.java.JavaAbstractUExpression
import org.jetbrains.uast.java.JavaConverter
import org.jetbrains.uast.java.lazyPub
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
class JavaUSynchronizedExpression(
  override val sourcePsi: PsiSynchronizedStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBlockExpression {
  override val expressions: List<UExpression> by lazyPub {
    sourcePsi.body?.statements?.map { JavaConverter.convertOrEmpty(it, this) } ?: listOf()
  }

  private val lockExpression: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.lockExpression, this) }

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitBlockExpression(this)) return
    expressions.acceptList(visitor)
    lockExpression.accept(visitor)
    visitor.afterVisitBlockExpression(this)
  }
}
