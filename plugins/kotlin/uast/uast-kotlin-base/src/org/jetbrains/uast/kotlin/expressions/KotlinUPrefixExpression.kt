// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.getResolveResultVariants

@ApiStatus.Internal
class KotlinUPrefixExpression(
    override val sourcePsi: KtPrefixExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UPrefixExpression, KotlinUElementWithType, KotlinEvaluatableUElement,
    UMultiResolvable {

    private val operandPart = UastLazyPart<UExpression>()

    override val operand: UExpression
        get() = operandPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.baseExpression, this)
        }

    override val operatorIdentifier: UIdentifier
        get() = KotlinUIdentifier(sourcePsi.operationReference.getReferencedNameElement(), this)

    override fun resolveOperator(): PsiMethod? =
        baseResolveProviderService.resolveCall(sourcePsi)

    override val operator = when (sourcePsi.operationToken) {
        KtTokens.EXCL -> UastPrefixOperator.LOGICAL_NOT
        KtTokens.PLUS -> UastPrefixOperator.UNARY_PLUS
        KtTokens.MINUS -> UastPrefixOperator.UNARY_MINUS
        KtTokens.PLUSPLUS -> UastPrefixOperator.INC
        KtTokens.MINUSMINUS -> UastPrefixOperator.DEC
        else -> UastPrefixOperator.UNKNOWN
    }

    override fun multiResolve(): Iterable<ResolveResult> =
        getResolveResultVariants(baseResolveProviderService, sourcePsi)

    override fun getExpressionType(): PsiType? =
        super<KotlinUElementWithType>.getExpressionType()
        // For overloaded operator (from binary dependency), we may need call resolution.
            ?: resolveOperator()?.returnType
}
