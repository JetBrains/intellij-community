// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.UastPostfixOperator
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.internal.getResolveResultVariants
import org.jetbrains.uast.tryResolve

@ApiStatus.Internal
class KotlinUPostfixExpression(
    override val sourcePsi: KtPostfixExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UPostfixExpression, KotlinUElementWithType, KotlinEvaluatableUElement,
    UResolvable, UMultiResolvable {

    private val operandPart = UastLazyPart<UExpression>()

    override val operand: UExpression
        get() = operandPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.baseExpression, this)
        }

    override val operator: UastPostfixOperator = when (sourcePsi.operationToken) {
        KtTokens.PLUSPLUS -> UastPostfixOperator.INC
        KtTokens.MINUSMINUS -> UastPostfixOperator.DEC
        KtTokens.EXCLEXCL -> KotlinPostfixOperators.EXCLEXCL
        else -> UastPostfixOperator.UNKNOWN
    }

    override val operatorIdentifier: UIdentifier
        get() = KotlinUIdentifier(sourcePsi.operationReference.getReferencedNameElement(), this)

    override fun resolveOperator(): PsiMethod? =
        baseResolveProviderService.resolveCall(sourcePsi)

    override fun resolve(): PsiMethod? = when (sourcePsi.operationToken) {
        KtTokens.EXCLEXCL -> operand.tryResolve() as? PsiMethod
        else -> null
    }

    override fun multiResolve(): Iterable<ResolveResult> =
        getResolveResultVariants(baseResolveProviderService, sourcePsi)

    override fun getExpressionType(): PsiType? =
        super<KotlinUElementWithType>.getExpressionType()
        // For overloaded operator (from binary dependency), we may need call resolution.
            ?: resolveOperator()?.returnType
}
