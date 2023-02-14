// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.blockStatement
import org.jetbrains.kotlin.nj2k.hasWritableUsages
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.determineType

class ParameterModificationInMethodCallsConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKMethod -> convertMethod(element)
            is JKLambdaExpression -> convertLambda(element)
        }
        return recurse(element)
    }

    private fun convertMethod(element: JKMethod) {
        val newVariables = createVariables(element.parameters, element.block).ifEmpty { return }
        element.block.statements = listOf(JKDeclarationStatement(newVariables)) + element.block.statements
    }

    private fun convertLambda(element: JKLambdaExpression) {
        val newVariables = createVariables(element.parameters, element.statement).ifEmpty { return }
        val declaration = JKDeclarationStatement(newVariables)
        when (val statement = element.statement) {
            is JKBlockStatement -> {
                statement.block.statements = listOf(declaration) + statement.block.statements
            }

            else -> {
                element.statement = blockStatement(declaration, statement.copyTreeAndDetach())
            }
        }
    }

    private fun createVariables(parameters: List<JKParameter>, scope: JKTreeElement): List<JKLocalVariable> =
        parameters.mapNotNull { parameter ->
            if (parameter.hasWritableUsages(scope, context)) {
                JKLocalVariable(
                    JKTypeElement(parameter.determineType(symbolProvider)),
                    JKNameIdentifier(parameter.name.value),
                    JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(parameter)),
                    JKMutabilityModifierElement(Mutability.MUTABLE)
                )
            } else null
        }
}
