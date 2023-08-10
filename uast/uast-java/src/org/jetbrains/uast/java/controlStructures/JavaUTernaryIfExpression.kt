// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiConditionalExpression
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UIfExpression

@ApiStatus.Internal
class JavaUTernaryIfExpression(
  override val sourcePsi: PsiConditionalExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UIfExpression {
  override val condition: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.condition, this) }
  override val thenExpression: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.thenExpression, this) }
  override val elseExpression: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.elseExpression, this) }

  override val isTernary: Boolean
    get() = true

  override val ifIdentifier: UIdentifier
    get() = UIdentifier(null, this)

  override val elseIdentifier: UIdentifier
    get() = UIdentifier(null, this)
}
