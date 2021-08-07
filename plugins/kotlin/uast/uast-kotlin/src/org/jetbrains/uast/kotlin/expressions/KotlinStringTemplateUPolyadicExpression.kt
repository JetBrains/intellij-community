// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost

class KotlinStringTemplateUPolyadicExpression(
    override val sourcePsi: KtStringTemplateExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent),
    UPolyadicExpression,
    KotlinUElementWithType,
    KotlinEvaluatableUElement,
    UInjectionHost {
    override val operands: List<UExpression> by lz {
        sourcePsi.entries.map {
            KotlinConverter.convertEntry(
                it,
                this,
                DEFAULT_EXPRESSION_TYPES_LIST
            )!!
        }.takeIf { it.isNotEmpty() } ?: listOf(KotlinStringULiteralExpression(sourcePsi, this, ""))
    }
    override val operator = UastBinaryOperator.PLUS

    override val psiLanguageInjectionHost: PsiLanguageInjectionHost get() = sourcePsi
    override val isString: Boolean get() = true

    override fun asRenderString(): String = if (operands.isEmpty()) "\"\"" else super<UPolyadicExpression>.asRenderString()
    override fun asLogString(): String = if (operands.isEmpty()) "UPolyadicExpression (value = \"\")" else super.asLogString()
}
