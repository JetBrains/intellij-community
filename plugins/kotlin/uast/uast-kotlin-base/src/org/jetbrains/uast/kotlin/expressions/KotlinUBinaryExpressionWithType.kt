// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUBinaryExpressionWithType(
    override val sourcePsi: KtBinaryExpressionWithTypeRHS,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpressionWithType, KotlinUElementWithType, KotlinEvaluatableUElement {

    private val operandPart = UastLazyPart<UExpression>()
    private val typePart = UastLazyPart<PsiType>()
    private val typeReferencePart = UastLazyPart<UTypeReferenceExpression?>()

    override val operand: UExpression
        get() = operandPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.left, this)
        }

    override val type: PsiType
        get() = typePart.getOrBuild {
            sourcePsi.right?.let {
                baseResolveProviderService.resolveToType(it, this, isBoxed = false)
            } ?: UastErrorType
        }

    override val typeReference: UTypeReferenceExpression?
        get() = typeReferencePart.getOrBuild {
            sourcePsi.right?.let {
                KotlinUTypeReferenceExpression(it, this) { type }
            }
        }

    override val operationKind = when (sourcePsi.operationReference.getReferencedNameElementType()) {
        KtTokens.AS_KEYWORD -> UastBinaryExpressionWithTypeKind.TypeCast.INSTANCE
        KtTokens.AS_SAFE -> KotlinBinaryExpressionWithTypeKinds.SAFE_TYPE_CAST
        else -> UastBinaryExpressionWithTypeKind.UNKNOWN
    }
}
