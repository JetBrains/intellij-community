// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.resolve.BindingContext.FUNCTION
import org.jetbrains.kotlin.resolve.BindingContext.USED_AS_RESULT_OF_LAMBDA
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameterBase

class KotlinULambdaExpression(
    override val sourcePsi: KtLambdaExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULambdaExpression, KotlinUElementWithType {
    override val functionalInterfaceType: PsiType?
        get() = getFunctionalInterfaceType()

    override val body by lz {
        sourcePsi.bodyExpression?.let { Body(it, this) } ?: UastEmptyExpression(this)
    }

    class Body(bodyExpression: KtBlockExpression, parent: KotlinULambdaExpression) : KotlinUBlockExpression(bodyExpression, parent) {

        override val expressions: List<UExpression> by lz expressions@{
            val statements = sourcePsi.statements
            if (statements.isEmpty()) return@expressions emptyList<UExpression>()
            ArrayList<UExpression>(statements.size).also { result ->
                statements.subList(0, statements.size - 1).mapTo(result) { KotlinConverter.convertOrEmpty(it, this) }
                result.add(implicitReturn ?: KotlinConverter.convertOrEmpty(statements.last(), this))
            }
        }

        val implicitReturn: KotlinUImplicitReturnExpression? by lz {
            val lastExpression = sourcePsi.statements.lastOrNull() ?: return@lz null
            val context = lastExpression.analyze()
            if (context[USED_AS_RESULT_OF_LAMBDA, lastExpression] == true) {
                return@lz KotlinUImplicitReturnExpression(this).apply {
                    returnExpression = KotlinConverter.convertOrEmpty(lastExpression, this)
                }
            }
            return@lz null
        }

    }

    override val valueParameters by lz {
        getParameters { valueParameters }
    }

    override val parameters: List<UParameter> by lz {
        getParameters { explicitParameters }
    }

    private inline fun getParameters(parametersSelector: SimpleFunctionDescriptor.() -> List<ParameterDescriptor>): List<UParameter> {
        val explicitParameters = sourcePsi.valueParameters.mapIndexed { i, p ->
            KotlinUParameter(UastKotlinPsiParameter.create(baseResolveProviderService, p, sourcePsi, this, i), p, this)
        }
        if (explicitParameters.isNotEmpty()) return explicitParameters

        val functionDescriptor = sourcePsi.analyze()[FUNCTION, sourcePsi.functionLiteral] ?: return emptyList()
        return functionDescriptor.parametersSelector().mapIndexed { i, p ->
            KotlinUParameter(
                UastKotlinPsiParameterBase(
                    p.name.asString(),
                    p.type.toPsiType(this, sourcePsi, false),
                    sourcePsi, sourcePsi, sourcePsi.language, p.isVararg, null
                ),
                null, this
            )
        }
    }

    override fun asRenderString(): String {
        val renderedValueParameters = if (valueParameters.isEmpty())
            ""
        else
            valueParameters.joinToString { it.asRenderString() } + " ->\n"
        val expressions = (body as? UBlockExpression)?.expressions
            ?.joinToString("\n") { it.asRenderString().withMargin } ?: body.asRenderString()

        return "{ $renderedValueParameters\n$expressions\n}"
    }
}
