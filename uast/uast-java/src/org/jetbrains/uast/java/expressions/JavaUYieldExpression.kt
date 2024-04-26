// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiYieldStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUYieldExpression(
  override val sourcePsi: PsiYieldStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UYieldExpression {
  private val expressionPart = UastLazyPart<UExpression?>()
  private val jumpTargetPart = UastLazyPart<UElement?>()

  override val expression: UExpression?
    get() = expressionPart.getOrBuild {
      JavaConverter.convertOrEmpty(sourcePsi.expression, this)
    }

  override val label: String?
    get() = null

  override val jumpTarget: UElement?
    get() = jumpTargetPart.getOrBuild {
      sourcePsi.findEnclosingExpression().takeIf { sourcePsi !== it }?.let {
        JavaConverter.convertExpression(it, null, UExpression::class.java)
      }
    }
}
