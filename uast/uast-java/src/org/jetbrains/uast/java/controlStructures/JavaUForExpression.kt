// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiForStatement
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUForExpression(
  override val sourcePsi: PsiForStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UForExpression {

  private val declarationPart = UastLazyPart<UExpression?>()
  private val conditionPart = UastLazyPart<UExpression?>()
  private val updatePart = UastLazyPart<UExpression?>()
  private val bodyPart = UastLazyPart<UExpression>()

  override val declaration: UExpression?
    get() = declarationPart.getOrBuild {
      sourcePsi.initialization?.let { JavaConverter.convertStatement(it, this, UExpression::class.java) }
    }

  override val condition: UExpression?
    get() = conditionPart.getOrBuild { sourcePsi.condition?.let { JavaConverter.convertExpression(it, this, UExpression::class.java) } }

  override val update: UExpression?
    get() = updatePart.getOrBuild { sourcePsi.update?.let { JavaConverter.convertStatement(it, this, UExpression::class.java) } }

  override val body: UExpression
    get() = bodyPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.body, this) }

  override val forIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.FOR_KEYWORD), this)
}
