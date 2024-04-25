// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.forEachDescendantOfType
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
                // Update parameters in enum constructor methods
                if (parentClass?.classKind == JKClass.ClassKind.ENUM && parentClass.name.value == element.name.value) {
                    element.updateNullabilityOfParameters()
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

    private fun JKMethod.updateNullabilityOfParameters() {
        val enumConstants = mutableListOf<JKEnumConstant>()
        parentOfType<JKClassBody>()?.forEachDescendantOfType<JKEnumConstant> {
            enumConstants.add(it)
        }
        for (i in parameters.indices) {
            val isNotNull = enumConstants.none {
                val arguments = it.arguments.arguments
                val argument = arguments.getOrNull(i)
                when {
                    arguments.size != parameters.size -> true
                    argument == null -> true
                    argument.value.calculateType(typeFactory)?.nullability == NotNull -> false
                    else -> true
                }
            }
            if (isNotNull) {
                parameters[i].type.type = parameters[i].type.type.updateNullability(NotNull)
            }
        }
    }
}