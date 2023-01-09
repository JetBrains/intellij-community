// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter

@ApiStatus.Internal
class KotlinLocalFunctionULambdaExpression(
    override val sourcePsi: KtFunction,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULambdaExpression {
    override val functionalInterfaceType: PsiType? = null

    override fun getExpressionType(): PsiType? {
        return baseResolveProviderService.getFunctionType(sourcePsi, uastParent)
    }

    override val body by lz {
        sourcePsi.bodyExpression?.let { wrapExpressionBody(this, it) } ?: UastEmptyExpression(this)
    }

    override val valueParameters by lz {
        sourcePsi.valueParameters.mapIndexed { i, p ->
            KotlinUParameter(UastKotlinPsiParameter.create(p, sourcePsi, this, i), p, this)
        }
    }

    override fun asRenderString(): String {
        val renderedValueParameters = valueParameters.joinToString(
            prefix = "(",
            postfix = ")",
            transform = KotlinUParameter::asRenderString
        )
        val expressions = (body as? UBlockExpression)?.expressions?.joinToString("\n") {
            it.asRenderString().withMargin
        } ?: body.asRenderString()
        return "fun $renderedValueParameters {\n${expressions.withMargin}\n}"
    }
}
