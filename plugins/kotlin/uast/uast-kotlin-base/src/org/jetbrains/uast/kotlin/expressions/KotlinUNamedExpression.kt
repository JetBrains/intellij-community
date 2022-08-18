// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUNamedExpression private constructor(
    override val name: String?,
    override val sourcePsi: PsiElement?,
    givenParent: UElement?,
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
            return KotlinUNamedExpression(name, valueArgument.asElement(), uastParent) { expressionParent ->
                expression?.let { expressionParent.getLanguagePlugin().convertOpt(it, expressionParent) }
                    ?: UastEmptyExpression(expressionParent)
            }
        }

        fun create(
            name: String?,
            valueArguments: List<ValueArgument>,
            uastParent: UElement?,
        ): UNamedExpression {
            return KotlinUNamedExpression(name, null, uastParent) { expressionParent ->
                KotlinUVarargExpression(valueArguments, expressionParent)
            }
        }
    }
}
