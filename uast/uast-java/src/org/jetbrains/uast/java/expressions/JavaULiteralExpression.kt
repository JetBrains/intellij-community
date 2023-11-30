// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost

@ApiStatus.Internal
class JavaULiteralExpression(
  override val sourcePsi: PsiLiteralExpressionImpl,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), ULiteralExpression, UInjectionHost {
  private var lazyValue: Any? = UNINITIALIZED_UAST_PART

  override fun evaluate(): Any? = sourcePsi.value

  override val value: Any?
    get() {
      if (lazyValue == UNINITIALIZED_UAST_PART) {
        lazyValue = evaluate()
      }
      return lazyValue
    }

  override val isString: Boolean
    get() = super<UInjectionHost>.isString

  override val psiLanguageInjectionHost: PsiLanguageInjectionHost
    get() = sourcePsi

  override fun getStringRoomExpression(): UExpression {
    val uParent = this.uastParent ?: return this
    if (uParent is UPolyadicExpression && uParent.operator == UastBinaryOperator.PLUS) {
      return uParent
    }
    return super.getStringRoomExpression()
  }
}
