// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiLabeledStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaULabeledExpression(
  override val sourcePsi: PsiLabeledStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), ULabeledExpression {

  private val expressionPart = UastLazyPart<UExpression>()

  override val label: String
    get() = sourcePsi.labelIdentifier.text

  override val labelIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.labelIdentifier, this)

  override val expression: UExpression
    get() = expressionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.statement, this) }

  override fun evaluate(): Any? = expression.evaluate()
}
