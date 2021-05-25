// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.uast.DEFAULT_EXPRESSION_TYPES_LIST
import org.jetbrains.uast.DEFAULT_TYPES_LIST
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.kotlin.internal.KotlinFakeUElement

fun expressionTypes(requiredType: Class<out UElement>?) =
    requiredType?.let { arrayOf(it) } ?: DEFAULT_EXPRESSION_TYPES_LIST

fun elementTypes(requiredType: Class<out UElement>?) =
    requiredType?.let { arrayOf(it) } ?: DEFAULT_TYPES_LIST

fun <T : UElement> Array<out Class<out T>>.nonEmptyOr(default: Array<out Class<out UElement>>) =
    takeIf { it.isNotEmpty() } ?: default

inline fun <reified ActualT : UElement> Array<out Class<out UElement>>.el(f: () -> UElement?): UElement? {
    return if (isAssignableFrom(ActualT::class.java)) f() else null
}

inline fun <reified ActualT : UElement> Array<out Class<out UElement>>.expr(f: () -> UExpression?): UExpression? {
    return if (isAssignableFrom(ActualT::class.java)) f() else null
}

fun Array<out Class<out UElement>>.isAssignableFrom(cls: Class<*>) = any { it.isAssignableFrom(cls) }

val identifiersTokens = setOf(
    KtTokens.IDENTIFIER, KtTokens.CONSTRUCTOR_KEYWORD, KtTokens.OBJECT_KEYWORD,
    KtTokens.THIS_KEYWORD, KtTokens.SUPER_KEYWORD,
    KtTokens.GET_KEYWORD, KtTokens.SET_KEYWORD
)

fun UElement.toSourcePsiFakeAware(): List<PsiElement> {
    if (this is KotlinFakeUElement) return this.unwrapToSourcePsi()
    return listOfNotNull(this.sourcePsi)
}
