// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReturnExpression

class KotlinUReturnExpression(
    override val sourcePsi: KtReturnExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UReturnExpression, KotlinUElementWithType {
    override val returnExpression by lz {
        baseResolveProviderService.baseKotlinConverter.convertOrNull(sourcePsi.returnedExpression, this)
    }

    override val label: String?
        get() = sourcePsi.getTargetLabel()?.getReferencedName()

    override val jumpTarget: UElement?
        get() = generateSequence(uastParent) { it.uastParent }
            .find {
                it is ULabeledExpression && it.label == label ||
                        (it is UMethod || it is KotlinLocalFunctionULambdaExpression) && label == null ||
                        it is ULambdaExpression && it.uastParent.let { parent -> parent is UCallExpression && parent.methodName == label }
            }
}
