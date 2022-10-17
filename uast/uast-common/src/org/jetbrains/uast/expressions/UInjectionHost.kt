// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.expressions

import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UExpression

/**
 * Represents a "string-like" elements in the host language (String literals, interpolated strings, comments)
 * where other languages could be injected by [com.intellij.psi.LanguageInjector]
 * or references contributed by [com.intellij.psi.PsiReferenceProvider]
 *
 * @see com.intellij.psi.UastReferenceProvider
 */
interface UInjectionHost : UExpression {

  val isString: Boolean
    get() = evaluateToString() != null

  fun evaluateToString(): String? = evaluate() as? String

  val psiLanguageInjectionHost: PsiLanguageInjectionHost

  /**
   * @return the whole "string-like" expression, which the current [UInjectionHost] participates in.
   * For instance for the `"abc"` participating in the expression
   * ```
   * val a = "abc" + v1 + "def"
   * ```
   * the `"abc" + v1 + "def"` will be returned.
   * 
   * Also, it will include string-processing postfix-methods like `trimIndent` and `trimMargin` if any of them is used.
   */
  @ApiStatus.Experimental
  fun getStringRoomExpression(): UExpression = this

}