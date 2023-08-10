// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost

@ApiStatus.Internal
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
            baseResolveProviderService.baseKotlinConverter.convertStringTemplateEntry(
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

    override fun getStringRoomExpression(): UExpression {
        val uParent = this.uastParent as? UExpression ?: return this
        val dotQualifiedExpression = uParent.sourcePsi as? KtDotQualifiedExpression
        if (dotQualifiedExpression != null) {
            val callExpression = dotQualifiedExpression.selectorExpression as? KtCallExpression ?: return this
            val resolvedFunctionName = baseResolveProviderService.resolvedFunctionName(callExpression)
            if (resolvedFunctionName == "trimIndent" || resolvedFunctionName == "trimMargin")
                return uParent
        }
        if (uParent is UPolyadicExpression && uParent.operator == UastBinaryOperator.PLUS)
            return uParent
        return super.getStringRoomExpression()
    }
}
