// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.java

import com.intellij.psi.PsiReturnStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUReturnExpression(
  override val sourcePsi: PsiReturnStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UReturnExpression {
  private val returnExpressionPart = UastLazyPart<UExpression?>()

  override val label: String?
    get() = null

  override val returnExpression: UExpression?
    get() = returnExpressionPart.getOrBuild { JavaConverter.convertOrNull(sourcePsi.returnValue, this) }
}
