/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.java

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaULambdaExpression(
  override val sourcePsi: PsiLambdaExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), ULambdaExpression {
  override val functionalInterfaceType: PsiType?
    get() = sourcePsi.functionalInterfaceType

  override val valueParameters: List<UParameter> by lz {
    sourcePsi.parameterList.parameters.map { JavaUParameter(it, this) }
  }

  override val body: UExpression by lz {
    val b = sourcePsi.body
    when (b) {
      is PsiCodeBlock -> JavaConverter.convertBlock(b, this)
      is PsiExpression -> wrapLambdaBody(this, b)
      else -> UastEmptyExpression(this)
    }
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
    val b = returnExpression != other.returnExpression
    if (b) return false

    return true
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
    if (expressions != other.expressions) return false
    return true
  }

  override fun hashCode(): Int = 31 + expressions.hashCode()

}
