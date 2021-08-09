// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter

class KotlinULambdaExpression(
    override val sourcePsi: KtLambdaExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULambdaExpression, KotlinUElementWithType {
    override val functionalInterfaceType: PsiType? by lz {
        baseResolveProviderService.getFunctionalInterfaceType(this)
    }

    override val body by lz {
        sourcePsi.bodyExpression?.let { Body(it, this) } ?: UastEmptyExpression(this)
    }

    class Body(bodyExpression: KtBlockExpression, parent: KotlinULambdaExpression) : KotlinUBlockExpression(bodyExpression, parent) {

        override val expressions: List<UExpression> by lz {
            val statements = sourcePsi.statements
            if (statements.isEmpty()) return@lz emptyList<UExpression>()
            ArrayList<UExpression>(statements.size).also { result ->
                statements.subList(0, statements.size - 1).mapTo(result) {
                    baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it, this)
                }
                result.add(implicitReturn ?: baseResolveProviderService.baseKotlinConverter.convertOrEmpty(statements.last(), this))
            }
        }

        val implicitReturn: KotlinUImplicitReturnExpression? by lz {
            baseResolveProviderService.getImplicitReturn(parent.sourcePsi, this)
        }
    }

    override val valueParameters by lz {
        getParameters(includeExplicitParameters = false)
    }

    override val parameters: List<UParameter> by lz {
        getParameters(includeExplicitParameters = true)
    }

    private fun getParameters(includeExplicitParameters: Boolean): List<UParameter> {
        val explicitParameters = sourcePsi.valueParameters.mapIndexed { i, p ->
            KotlinUParameter(UastKotlinPsiParameter.create(p, sourcePsi, this, i), p, this)
        }
        if (explicitParameters.isNotEmpty()) return explicitParameters

        return baseResolveProviderService.getImplicitParameters(sourcePsi, this, includeExplicitParameters)
    }

    override fun asRenderString(): String {
        val renderedValueParameters = if (valueParameters.isEmpty())
            ""
        else
            valueParameters.joinToString { it.asRenderString() } + " ->\n"
        val expressions =
            (body as? UBlockExpression)?.expressions?.joinToString("\n") { it.asRenderString().withMargin } ?: body.asRenderString()

        return "{ $renderedValueParameters\n$expressions\n}"
    }
}
