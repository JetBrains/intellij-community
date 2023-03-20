// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.psi.PsiYieldStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UYieldExpression

@ApiStatus.Internal
class JavaUYieldExpression(
  override val sourcePsi: PsiYieldStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UYieldExpression {
  override val expression: UExpression? by lazyPub {
    JavaConverter.convertOrEmpty(sourcePsi.expression, this)
  }

  override val label: String?
    get() = null

  override val jumpTarget: UElement? by lazyPub {
    sourcePsi.findEnclosingExpression().takeIf { sourcePsi !== it }?.let { JavaConverter.convertExpression(it, null, UExpression::class.java) }
  }
}
