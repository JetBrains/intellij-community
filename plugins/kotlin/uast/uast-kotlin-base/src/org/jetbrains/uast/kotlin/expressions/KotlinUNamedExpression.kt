// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.uast.*

class KotlinUNamedExpression private constructor(
    override val name: String?,
    override val sourcePsi: PsiElement?,
    givenParent: UElement?,
    override val baseResolveProviderService: BaseKotlinUastResolveProviderService,
    expressionProducer: (UElement) -> UExpression
) : KotlinAbstractUElement(givenParent), UNamedExpression {

    override val expression: UExpression by lz { expressionProducer(this) }

    override val uAnnotations: List<UAnnotation> = emptyList()

    override val psi: PsiElement? = null

    override val javaPsi: PsiElement? = null

    companion object {
        fun create(
            name: String?,
            valueArgument: ValueArgument,
            uastParent: UElement?
        ): UNamedExpression {
            val expression = valueArgument.getArgumentExpression()
            val service = ServiceManager.getService(valueArgument.asElement().project, BaseKotlinUastResolveProviderService::class.java)
            return KotlinUNamedExpression(name, valueArgument.asElement(), uastParent, service) { expressionParent ->
                expression?.let { expressionParent.getLanguagePlugin().convertOpt(it, expressionParent) }
                    ?: UastEmptyExpression(expressionParent)
            }
        }

        fun create(
            name: String?,
            valueArguments: List<ValueArgument>,
            uastParent: UElement?,
            baseResolveProviderService: BaseKotlinUastResolveProviderService,
        ): UNamedExpression {
            return KotlinUNamedExpression(name, null, uastParent, baseResolveProviderService) { expressionParent ->
                KotlinUVarargExpression(valueArguments, expressionParent, baseResolveProviderService)
            }
        }
    }
}
