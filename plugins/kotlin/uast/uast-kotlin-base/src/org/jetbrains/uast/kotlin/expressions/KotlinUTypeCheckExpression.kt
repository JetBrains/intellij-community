// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUTypeCheckExpression(
    override val sourcePsi: KtIsExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpressionWithType, KotlinUElementWithType, KotlinEvaluatableUElement {

    private val operandPart = UastLazyPart<UExpression>()
    private val typePart = UastLazyPart<PsiType>()
    private val typeReferencePart = UastLazyPart<UTypeReferenceExpression?>()

    override val operand: UExpression
        get() = operandPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.leftHandSide, this)
        }

    override val type: PsiType
        get() = typePart.getOrBuild {
            sourcePsi.typeReference?.let {
                baseResolveProviderService.resolveToType(it, this, isBoxed = false)
            } ?: UastErrorType
        }

    override val typeReference: UTypeReferenceExpression?
        get() = typeReferencePart.getOrBuild {
            sourcePsi.typeReference?.let {
                KotlinUTypeReferenceExpression(it, this) { type }
            }
        }

    override val operationKind =
        if (sourcePsi.isNegated)
            KotlinBinaryExpressionWithTypeKinds.NEGATED_INSTANCE_CHECK
        else
            UastBinaryExpressionWithTypeKind.InstanceCheck.INSTANCE
}
