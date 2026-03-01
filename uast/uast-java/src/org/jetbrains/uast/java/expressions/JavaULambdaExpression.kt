// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.getParentOfType

@ApiStatus.Internal
class JavaULambdaExpression(
  override val sourcePsi: PsiLambdaExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), ULambdaExpression {

  private val bodyPart = UastLazyPart<UExpression>()
  private val valueParametersPart = UastLazyPart<List<UParameter>>()

  override val functionalInterfaceType: PsiType?
    get() = sourcePsi.functionalInterfaceType

  override val valueParameters: List<UParameter>
    get() = valueParametersPart.getOrBuild {
      sourcePsi.parameterList.parameters.map { JavaUParameter(it, this) }
    }

  override val body: UExpression
    get() = bodyPart.getOrBuild {
      when (val b = sourcePsi.body) {
        is PsiCodeBlock -> JavaConverter.convertBlock(b, this)
        is PsiExpression -> wrapLambdaBody(this, b)
        else -> UastEmptyExpression(this)
      }
    }

  companion object {
    @Internal
    fun unwrapImplicitBody(uExpression: UExpression): PsiExpression? =
      uExpression
        .asSafely<JavaImplicitUBlockExpression>()
        ?.expressions
        ?.firstOrNull()
        ?.asSafely<JavaImplicitUReturnExpression>()
        ?.returnExpression
        ?.sourcePsi
        ?.asSafely<PsiExpression>()
  }
}

private fun wrapLambdaBody(parent: JavaULambdaExpression, b: PsiExpression): UBlockExpression =
  JavaImplicitUBlockExpression(parent).apply {
    expressions = listOf(JavaImplicitUReturnExpression(this).apply {
      returnExpression = JavaConverter.convertOrEmpty(b, this)
    })
  }

private class JavaImplicitUReturnExpression(givenParent: UElement?) : JavaAbstractUExpression(givenParent), UReturnExpression {
  override val label: String?
    get() = null

  override val jumpTarget: UElement? =
    getParentOfType(ULambdaExpression::class.java, strict = true)

  override val sourcePsi: PsiElement?
    get() = null
  override val psi: PsiElement?
    get() = null

  override lateinit var returnExpression: UExpression
    internal set

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as JavaImplicitUReturnExpression
    return returnExpression == other.returnExpression
  }

  override fun hashCode(): Int = 31 + returnExpression.hashCode()
}

private class JavaImplicitUBlockExpression(givenParent: UElement?) : JavaAbstractUExpression(givenParent), UBlockExpression {
  override val sourcePsi: PsiElement?
    get() = null

  override lateinit var expressions: List<UExpression>
    internal set

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as JavaImplicitUBlockExpression
    return expressions == other.expressions
  }

  override fun hashCode(): Int = 31 + expressions.hashCode()
}
