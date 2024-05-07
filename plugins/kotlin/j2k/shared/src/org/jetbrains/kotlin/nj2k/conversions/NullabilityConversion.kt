// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.updateNullability

/**
 * Try to determine more precise nullability for some JK elements.
 * See also [org.jetbrains.kotlin.nj2k.JavaToJKTreeBuilder.collectNullabilityInfo]
 */
class NullabilityConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
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

    private fun JKTypeCastExpression.updateNullability() {
        val qualifiedExpression = (parent as? JKParenthesizedExpression)?.parent as? JKQualifiedExpression
        if (qualifiedExpression != null) {
            // In code such as `((String o)).length()`, the cast's type can be considered not-null
            // (it is equivalent to Kotlin's unsafe cast)
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