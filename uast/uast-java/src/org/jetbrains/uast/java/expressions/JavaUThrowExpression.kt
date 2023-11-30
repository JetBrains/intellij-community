// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.java

import com.intellij.psi.PsiThrowStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUThrowExpression(
  override val sourcePsi: PsiThrowStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UThrowExpression {
  private val thrownExpressionPart = UastLazyPart<UExpression>()

  override val thrownExpression: UExpression
    get() = thrownExpressionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.exception, this) }
}
