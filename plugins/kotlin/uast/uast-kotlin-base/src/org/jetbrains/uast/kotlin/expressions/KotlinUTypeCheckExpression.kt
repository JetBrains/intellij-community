// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.jetbrains.uast.UastErrorType

@ApiStatus.Internal
class KotlinUTypeCheckExpression(
    override val sourcePsi: KtIsExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpressionWithType, KotlinUElementWithType, KotlinEvaluatableUElement {
    override val operand by lz {
        baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.leftHandSide, this)
    }

    override val type by lz {
        sourcePsi.typeReference?.let {
            baseResolveProviderService.resolveToType(it, this, boxed = false)
        } ?: UastErrorType
    }

    override val typeReference by lz {
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
