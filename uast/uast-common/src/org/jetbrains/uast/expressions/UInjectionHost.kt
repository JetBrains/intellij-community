// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
@ApiStatus.Experimental
interface UInjectionHost : UExpression {

  val isString: Boolean
    get() = evaluateToString() != null

  fun evaluateToString(): String? = evaluate() as? String

  val psiLanguageInjectionHost: PsiLanguageInjectionHost

}