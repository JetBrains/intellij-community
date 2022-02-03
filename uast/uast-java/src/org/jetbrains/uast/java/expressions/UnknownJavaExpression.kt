// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

@ApiStatus.Internal
class UnknownJavaExpression(
  override val sourcePsi: PsiElement,
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UExpression, UElement {
  override fun asLogString(): String = "[!] " + UnknownJavaExpression::class.java.simpleName + " ($sourcePsi)"

  override val uAnnotations: List<UAnnotation>
    get() = emptyList()
}
