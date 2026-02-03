// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiParenthesizedExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
class JavaUParenthesizedExpression(
  override val sourcePsi: PsiParenthesizedExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UParenthesizedExpression {
  private val expressionPart = UastLazyPart<UExpression>()

  override val expression: UExpression
    get() = expressionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.expression, this) }

  override fun evaluate(): Any? = expression.evaluate()
}
