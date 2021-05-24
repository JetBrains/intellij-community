// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.expressions.KotlinLocalFunctionULambdaExpression
import org.jetbrains.uast.kotlin.internal.KotlinFakeUElement
import org.jetbrains.uast.kotlin.internal.toSourcePsiFakeAware

class KotlinUReturnExpression(
    override val sourcePsi: KtReturnExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UReturnExpression, KotlinUElementWithType {
    override val returnExpression by lz { KotlinConverter.convertOrNull(sourcePsi.returnedExpression, this) }

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

class KotlinUImplicitReturnExpression(
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UReturnExpression, KotlinUElementWithType, KotlinFakeUElement {
    override val psi: PsiElement?
        get() = null

    override lateinit var returnExpression: UExpression
        internal set

    // Due to the lack of [psi], (lazily) delegate to the one in [returnExpression]
    override val baseResolveProviderService: BaseKotlinUastResolveProviderService by lz {
        (returnExpression as KotlinAbstractUElement).baseResolveProviderService
    }

    override fun unwrapToSourcePsi(): List<PsiElement> {
        return returnExpression.toSourcePsiFakeAware()
    }

}
