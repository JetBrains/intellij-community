// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter

@ApiStatus.Internal
class KotlinULambdaExpression(
    override val sourcePsi: KtLambdaExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULambdaExpression, KotlinUElementWithType {

    private val functionalInterfaceTypePart = UastLazyPart<PsiType?>()
    private val bodyPart = UastLazyPart<UExpression>()
    private val parametersPart = UastLazyPart<List<UParameter>>()
    private val valueParametersPart = UastLazyPart<List<UParameter>>()

    override val functionalInterfaceType: PsiType?
        get() = functionalInterfaceTypePart.getOrBuild {
            baseResolveProviderService.getFunctionalInterfaceType(this)
        }

    override val body: UExpression
        get() = bodyPart.getOrBuild {
            sourcePsi.bodyExpression?.let { Body(it, this) } ?: UastEmptyExpression(this)
        }

    @ApiStatus.Internal
    class Body(
        bodyExpression: KtBlockExpression,
        private val parent: KotlinULambdaExpression
    ) : KotlinUBlockExpression(bodyExpression, parent) {

        private val expressionsPart = UastLazyPart<List<UExpression>>()
        private val implicitReturnPart = UastLazyPart<KotlinUImplicitReturnExpression?>()

        override val expressions: List<UExpression>
            get() = expressionsPart.getOrBuild {
                val statements = sourcePsi.statements
                if (statements.isEmpty()) return@getOrBuild emptyList<UExpression>()
                ArrayList<UExpression>(statements.size).also { result ->
                    statements.subList(0, statements.size - 1).mapTo(result) {
                        baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it, this)
                    }
                    result.add(implicitReturn ?: baseResolveProviderService.baseKotlinConverter.convertOrEmpty(statements.last(), this))
                }
            }

        val implicitReturn: KotlinUImplicitReturnExpression?
            get() = implicitReturnPart.getOrBuild {
                baseResolveProviderService.getImplicitReturn(parent.sourcePsi, this)
            }
    }

    override val valueParameters: List<UParameter>
        get() = valueParametersPart.getOrBuild {
            getParameters(includeExplicitParameters = false)
        }

    override val parameters: List<UParameter>
        get() = parametersPart.getOrBuild {
            getParameters(includeExplicitParameters = true)
        }

    private fun getParameters(includeExplicitParameters: Boolean): List<UParameter> {
        // For [valueParameters] (includeExplicitParameters = false)
        // but only if there are value parameters indeed
        if (!includeExplicitParameters && sourcePsi.valueParameters.isNotEmpty()) {
            return sourcePsi.valueParameters.mapIndexed { i, p ->
                KotlinUParameter(UastKotlinPsiParameter.create(p, sourcePsi, this, i), p, this)
            }
        }

        // For [parameters] (includeExplicitParameters = true)
        // or for [valueParameters] without explicit value parameters
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
