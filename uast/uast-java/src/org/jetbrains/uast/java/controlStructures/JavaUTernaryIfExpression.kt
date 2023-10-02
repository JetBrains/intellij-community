// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiConditionalExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUTernaryIfExpression(
  override val sourcePsi: PsiConditionalExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UIfExpression {

  private val conditionPart = UastLazyPart<UExpression>()
  private val thenExpressionPart = UastLazyPart<UExpression>()
  private val elseExpressionPart = UastLazyPart<UExpression>()

  override val condition: UExpression
    get() = conditionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.condition, this) }

  override val thenExpression: UExpression
    get() = thenExpressionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.thenExpression, this) }

  override val elseExpression: UExpression
    get() = elseExpressionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.elseExpression, this) }

  override val isTernary: Boolean
    get() = true

  override val ifIdentifier: UIdentifier
    get() = UIdentifier(null, this)

  override val elseIdentifier: UIdentifier
    get() = UIdentifier(null, this)
}
