// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.java.expressions

import com.intellij.psi.PsiSynchronizedStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.java.JavaAbstractUExpression
import org.jetbrains.uast.java.JavaConverter
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
class JavaUSynchronizedExpression(
  override val sourcePsi: PsiSynchronizedStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBlockExpression {
  private val expressionsPart = UastLazyPart<List<UExpression>>()
  private val lockExpressionPart = UastLazyPart<UExpression>()

  override val expressions: List<UExpression>
    get() = expressionsPart.getOrBuild {
      sourcePsi.body?.statements?.map { JavaConverter.convertOrEmpty(it, this) } ?: listOf()
    }

  private val lockExpression: UExpression
    get() = lockExpressionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.lockExpression, this) }

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitBlockExpression(this)) return
    expressions.acceptList(visitor)
    lockExpression.accept(visitor)
    visitor.afterVisitBlockExpression(this)
  }
}
