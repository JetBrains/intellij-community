// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUForEachExpression(
  override val sourcePsi: PsiForeachStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UForEachExpression {

  private val iteratedValuePart = UastLazyPart<UExpression>()
  private val bodyPart = UastLazyPart<UExpression>()

  @Deprecated("property may throw exception if foreach doesn't have variable", replaceWith = ReplaceWith("parameter"))
  override val variable: UParameter
    get() = JavaUParameter(sourcePsi.iterationParameter ?: error("Migrate code to $parameter"), this)

  override val parameter: UParameter?
    get() {
      val psiParameter = sourcePsi.iterationParameter ?: return null
      return JavaUParameter(psiParameter, this)
    }

  override val iteratedValue: UExpression
    get() = iteratedValuePart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.iteratedValue, this) }

  override val body: UExpression
    get() = bodyPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.body, this) }

  override val forIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.FOR_KEYWORD), this)
}
