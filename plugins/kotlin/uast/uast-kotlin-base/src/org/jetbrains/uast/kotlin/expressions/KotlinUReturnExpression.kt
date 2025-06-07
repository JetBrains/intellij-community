// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUReturnExpression(
    override val sourcePsi: KtReturnExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UReturnExpression, KotlinUElementWithType {

    private val returnExpressionPart = UastLazyPart<UExpression?>()

    override val returnExpression: UExpression?
        get() = returnExpressionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrNull(sourcePsi.returnedExpression, this)
        }

    override val label: String?
        get() = sourcePsi.getTargetLabel()?.getReferencedName()

    override val jumpTarget: UElement?
        get() = generateSequence(uastParent) { it.uastParent }
            .find {
                it is ULabeledExpression && it.label == label ||
                        it is UMethod && it.name == label ||
                        (it is UMethod || it is KotlinLocalFunctionULambdaExpression) && label == null ||
                        it is ULambdaExpression && it.uastParent.let { parent ->
                            parent is UCallExpression &&
                                    (
                                            // Regular function call
                                            parent.methodName == label ||
                                            // Constructor (whose `methodName` is `<init>`)
                                            parent.methodIdentifier?.name == label
                                    )
                        }
            }
}
