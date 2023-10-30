// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
@Deprecated("doesn't support UInjectionHost, most likely it is not what you want", ReplaceWith("isInjectionHost()"))
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
@Deprecated("doesn't support UInjectionHost, most likely it is not what you want", ReplaceWith("UExpression.evaluateString()"))
@Suppress("DEPRECATION")
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

val UExpression.allPsiLanguageInjectionHosts: List<PsiLanguageInjectionHost>
  @ApiStatus.Experimental
  get() {
    sourceInjectionHost?.let { return listOf(it) }
    (this as? UPolyadicExpression)?.let { return this.operands.mapNotNull { it.sourceInjectionHost } }
    return emptyList()
  }

@ApiStatus.Experimental
fun isConcatenation(uExpression: UElement?): Boolean {
  if (uExpression !is UPolyadicExpression) return false

  return uExpression.operator == UastBinaryOperator.PLUS
}

/**
 * @return a non-strict parent [PsiLanguageInjectionHost] for [ULiteralExpression.sourcePsi] of given literal expression if it exists.
 *
 * NOTE: consider using [sourceInjectionHost] as more performant. Probably will be deprecated in future.
 */
val ULiteralExpression.psiLanguageInjectionHost: PsiLanguageInjectionHost?
  get() = this.sourcePsi?.let { PsiTreeUtil.getParentOfType(it, PsiLanguageInjectionHost::class.java, false) }

/**
 * @return if given [uElement] is an [ULiteralExpression] but not a [UInjectionHost]
 * (which could happen because of "KotlinULiteralExpression and PsiLanguageInjectionHost mismatch", see KT-27283 )
 * then tries to convert it to [UInjectionHost] and return it,
 * otherwise return [uElement] itself
 *
 * NOTE: when `kotlin.uast.force.uinjectionhost` flag is `true` this method is useless because there is no mismatch anymore
 */
@ApiStatus.Experimental
fun wrapULiteral(uElement: UExpression): UExpression {
  if (uElement is ULiteralExpression && uElement !is UInjectionHost) {
    uElement.sourceInjectionHost.toUElementOfType<UInjectionHost>()?.let { return it }
  }
  return uElement
}


val UInjectionHost.injectedReferences: Iterable<PsiReference>
  get() {
    return psiLanguageInjectionHost.injectedReferences
  }

/**
 * @return all references injected into this [ULiteralExpression]
 *
 * Note: getting references simply from the `sourcePsi` will not work for Kotlin polyadic strings for instance
 */
val ULiteralExpression.injectedReferences: Iterable<PsiReference>
  get() {
    return psiLanguageInjectionHost?.injectedReferences ?: return emptyList()
  }

private val PsiLanguageInjectionHost.injectedReferences: Iterable<PsiReference>
  get() {
    val references = references.asSequence()
    val innerReferences = children.asSequence().flatMap { e -> e.references.asSequence() }
    return (references + innerReferences).asIterable()
  }

@JvmOverloads
fun deepLiteralSearch(expression: UExpression, maxDepth: Int = 5): Sequence<ULiteralExpression> {
  val visited = HashSet<UExpression>()
  fun deepLiteralSearchInner(expression: UExpression, maxDepth: Int): Sequence<ULiteralExpression> {
    if (maxDepth <= 0 || !visited.add(expression)) return emptySequence()
    return when (expression) {
      is ULiteralExpression -> sequenceOf(expression)
      is UPolyadicExpression -> expression.operands.asSequence().flatMap { deepLiteralSearchInner(it, maxDepth - 1) }
      is UReferenceExpression -> expression.resolve()
        .toUElementOfType<UVariable>()
        ?.uastInitializer
        ?.let { deepLiteralSearchInner(it, maxDepth - 1) }.orEmpty()
      else -> emptySequence()
    }
  }
  return deepLiteralSearchInner(expression, maxDepth)
}