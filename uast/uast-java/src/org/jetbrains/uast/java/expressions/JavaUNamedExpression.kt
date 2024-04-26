// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java.expressions

import com.intellij.psi.PsiNameValuePair
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.JavaAbstractUExpression
import org.jetbrains.uast.java.JavaConverter

@ApiStatus.Internal
class JavaUNamedExpression(
  override val sourcePsi: PsiNameValuePair,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UNamedExpression {
  private val expressionPart = UastLazyPart<UExpression>()

  override fun evaluate(): Any? = expression.evaluate()

  override val name: String?
    get() = sourcePsi.name

  override val expression: UExpression
    get() = expressionPart.getOrBuild {
      sourcePsi.value
        ?.let { value -> JavaConverter.convertPsiElement(value, this, UElement::class.java) } as? UExpression
      ?: UastEmptyExpression(this)
    }
}
