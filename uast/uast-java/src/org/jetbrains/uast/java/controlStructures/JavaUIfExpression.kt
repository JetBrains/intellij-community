// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiIfStatement
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUIfExpression(
  override val sourcePsi: PsiIfStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UIfExpression {

  private val conditionPart = UastLazyPart<UExpression>()
  private val thenExpressionPart = UastLazyPart<UExpression>()
  private val elseExpressionPart = UastLazyPart<UExpression>()

  override val condition: UExpression
    get() = conditionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.condition, this) }

  override val thenExpression: UExpression
    get() = thenExpressionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.thenBranch, this) }

  override val elseExpression: UExpression
    get() = elseExpressionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.elseBranch, this) }

  override val isTernary: Boolean
    get() = false

  override val ifIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.IF_KEYWORD), this)

  override val elseIdentifier: UIdentifier?
    get() = sourcePsi.getChildByRole(ChildRole.ELSE_KEYWORD)?.let { UIdentifier(it, this) }
}
