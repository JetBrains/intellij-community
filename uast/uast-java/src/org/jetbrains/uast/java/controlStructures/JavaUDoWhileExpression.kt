// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiDoWhileStatement
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUDoWhileExpression(
  override val sourcePsi: PsiDoWhileStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UDoWhileExpression {

  private val bodyPart = UastLazyPart<UExpression>()
  private val conditionPart = UastLazyPart<UExpression>()

  override val condition: UExpression
    get() = conditionPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.condition, this) }

  override val body: UExpression
    get() = bodyPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.body, this) }

  override val doIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.DO_KEYWORD), this)

  override val whileIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.WHILE_KEYWORD), this)
}
