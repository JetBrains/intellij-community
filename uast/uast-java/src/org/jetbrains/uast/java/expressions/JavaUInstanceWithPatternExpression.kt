// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java.expressions

import com.intellij.psi.PsiInstanceOfExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UBinaryExpressionWithPattern
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UPatternExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.java.JavaAbstractUExpression
import org.jetbrains.uast.java.JavaConverter

@ApiStatus.Internal
class JavaUInstanceWithPatternExpression(
  override val sourcePsi: PsiInstanceOfExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBinaryExpressionWithPattern {
  private val operandPart = UastLazyPart<UExpression>()

  private val patternPart = UastLazyPart<UPatternExpression?>()

  override val operand: UExpression get() = operandPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.operand, this) }

  override val patternExpression: UPatternExpression? get() = patternPart.getOrBuild {
    sourcePsi.pattern?.let { pattern ->
      JavaConverter.convertPsiElement(pattern, this, UPatternExpression::class.java) as? UPatternExpression
    }
  }

  override fun getExpressionType(): PsiType = PsiTypes.booleanType()
}