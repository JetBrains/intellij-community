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
@file:JvmName("UastLiteralUtils")

package org.jetbrains.uast

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.expressions.UInjectionHost

/**
 * Checks if the [UElement] is a null literal.
 *
 * @return true if the receiver is a null literal, false otherwise.
 */
fun UElement.isNullLiteral(): Boolean = this is ULiteralExpression && this.isNull

/**
 * Checks if the [UElement] is a boolean literal.
 *
 * @return true if the receiver is a boolean literal, false otherwise.
 */
fun UElement.isBooleanLiteral(): Boolean = this is ULiteralExpression && this.isBoolean

/**
 * Checks if the [UElement] is a `true` boolean literal.
 *
 * @return true if the receiver is a `true` boolean literal, false otherwise.
 */
fun UElement.isTrueLiteral(): Boolean = this is ULiteralExpression && this.isBoolean && this.value == true

/**
 * Checks if the [UElement] is a `false` boolean literal.
 *
 * @return true if the receiver is a `false` boolean literal, false otherwise.
 */
fun UElement.isFalseLiteral(): Boolean = this is ULiteralExpression && this.isBoolean && this.value == false

/**
 * Checks if the [UElement] is a [String] literal.
 *
 * @return true if the receiver is a [String] literal, false otherwise.
 */
@Deprecated("doesn't support UInjectionHost, most probably it is not what you want", ReplaceWith("isInjectionHost()"))
fun UElement.isStringLiteral(): Boolean = this is ULiteralExpression && this.isString

/**
 * Checks if the [UElement] is a [PsiLanguageInjectionHost] holder.
 *
 * NOTE: It is a transitional function until everything will migrate to [UInjectionHost]
 */
fun UElement?.isInjectionHost(): Boolean = this is UInjectionHost || (this is UExpression && this.sourceInjectionHost != null)

/**
 * Returns the [String] literal value.
 *
 * @return literal text if the receiver is a valid [String] literal, null otherwise.
 */
@Deprecated("doesn't support UInjectionHost, most probably it is not what you want", ReplaceWith("UExpression.evaluateString()"))
fun UElement.getValueIfStringLiteral(): String? =
  if (isStringLiteral()) (this as ULiteralExpression).value as String else null

/**
 * Checks if the [UElement] is a [Number] literal (Integer, Long, Float, Double, etc.).
 *
 * @return true if the receiver is a [Number] literal, false otherwise.
 */
fun UElement.isNumberLiteral(): Boolean = this is ULiteralExpression && this.value is Number

/**
 * Checks if the [UElement] is an integral literal (is an [Integer], [Long], [Short], [Char] or [Byte]).
 *
 * @return true if the receiver is an integral literal, false otherwise.
 */
fun UElement.isIntegralLiteral(): Boolean = this is ULiteralExpression && when (value) {
  is Int -> true
  is Long -> true
  is Short -> true
  is Char -> true
  is Byte -> true
  else -> false
}

/**
 * Returns the integral value of the literal.
 *
 * @return long representation of the literal expression value,
 *         0 if the receiver literal expression is not a integral one.
 */
fun ULiteralExpression.getLongValue(): Long = value.let {
  when (it) {
    is Long -> it
    is Int -> it.toLong()
    is Short -> it.toLong()
    is Char -> it.toLong()
    is Byte -> it.toLong()
    else -> 0
  }
}

/**
 * @return corresponding [PsiLanguageInjectionHost] for this [UExpression] if it exists.
 * Tries to not return same [PsiLanguageInjectionHost] for different UElement-s, thus returns `null` if host could be obtained from
 * another [UExpression].
 */
val UExpression.sourceInjectionHost: PsiLanguageInjectionHost?
  get() {
    (this.sourcePsi as? PsiLanguageInjectionHost)?.let { return it }
    // following is a handling of KT-27283
    if (this !is ULiteralExpression) return null
    val parent = this.uastParent
    if (parent is UPolyadicExpression && parent.sourcePsi is PsiLanguageInjectionHost) return null
    (this.sourcePsi?.parent as? PsiLanguageInjectionHost)?.let { return it }
    return null
  }

/**
 * @return a non-strict parent [PsiLanguageInjectionHost] for [sourcePsi] of given literal expression if it exists.
 *
 * NOTE: consider using [sourceInjectionHost] as more performant. Probably will be deprecated in future.
 */
val ULiteralExpression.psiLanguageInjectionHost: PsiLanguageInjectionHost?
  get() = this.psi?.let { PsiTreeUtil.getParentOfType(it, PsiLanguageInjectionHost::class.java, false) }

// Workaround until everything will migrate to `UInjectionHost` from `ULiteralExpression`, see KT-27283
@ApiStatus.Experimental
fun unwrapPolyadic(uElement: UExpression): UExpression {
  if (uElement is ULiteralExpression) {
    val parent = uElement.uastParent
    if (parent is UPolyadicExpression)
      return parent
  }
  return uElement
}

/**
 * @return all references injected into this [ULiteralExpression]
 *
 * Note: getting references simply from the `sourcePsi` will not work for Kotlin polyadic strings for instance
 */
val ULiteralExpression.injectedReferences: Iterable<PsiReference>
  get() {
    val element = this.psiLanguageInjectionHost ?: return emptyList()
    val references = element.references.asSequence()
    val innerReferences = element.children.asSequence().flatMap { e -> e.references.asSequence() }
    return (references + innerReferences).asIterable()
  }