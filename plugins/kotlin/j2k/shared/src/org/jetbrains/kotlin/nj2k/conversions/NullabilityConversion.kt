// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiTypeCastExpression
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.getExpressionDfaNullability
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.updateNullability

/**
 * Try to determine more precise nullability for some JK elements.
 * See also [org.jetbrains.kotlin.nj2k.JavaToJKTreeBuilder.collectNullabilityInfo]
 */
class NullabilityConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKTypeCastExpression -> element.updateNullability()
            is JKMethod -> {
                val parentClass = element.parentOfType<JKClass>()
                if (parentClass?.classKind == JKClass.ClassKind.ENUM && parentClass.name.value == element.name.value) {
                    element.updateNullabilityOfEnumConstructorParameters()
                }
            }
        }

        return recurse(element)
    }

    // Try to update nullability of the cast's type to not-null in certain cases
    // TODO migrate to J2KNullityInferrer
    // TODO support the case of argument for not-null parameter
    private fun JKTypeCastExpression.updateNullability() {
        var context = parent
        if (context is JKParenthesizedExpression) context = context.parent

        val psiCastedExpression = psi<PsiTypeCastExpression>()?.operand?.let {
            if (it is PsiParenthesizedExpression) it.expression else it
        }

        val isNotNullContext = when {
            // `((String o)).length()` is equivalent to Kotlin's unsafe cast
            context is JKQualifiedExpression -> true

            // `String s = (String obj);` when `s` is already inferred to be not-null
            context is JKLocalVariable -> {
                val variableType = context.type.type
                variableType.nullability == NotNull
            }

            // If Java DFA knows that the cast expression is not null, it is safe to cast to the not-null type
            psiCastedExpression != null -> {
                val dfaNullability = getExpressionDfaNullability(psiCastedExpression)
                dfaNullability == DfaNullability.NOT_NULL
            }

            else -> false
        }

        if (isNotNullContext) {
            type.type = type.type.updateNullability(NotNull)
        }
    }

    // TODO consider extending to all (private) methods
    private fun JKMethod.updateNullabilityOfEnumConstructorParameters() {
        val enumConstants = parentOfType<JKClassBody>()?.declarations?.filterIsInstance<JKEnumConstant>() ?: return

        for (i in parameters.indices) {
            if (parameters[i].type.type.nullability == NotNull) continue

            val allArgumentsForParameterAreNotNull = enumConstants.all { enumConstant ->
                val arguments = enumConstant.arguments.arguments
                val argument = arguments.getOrNull(i)
                when {
                    arguments.size != parameters.size -> false
                    argument == null -> false
                    argument.value.calculateType(typeFactory)?.nullability == NotNull -> true
                    else -> true
                }
            }

            if (allArgumentsForParameterAreNotNull) {
                parameters[i].type.type = parameters[i].type.type.updateNullability(NotNull)
            }
        }
    }
}