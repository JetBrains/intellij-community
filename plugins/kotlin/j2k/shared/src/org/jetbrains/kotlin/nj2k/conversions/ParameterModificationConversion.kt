// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.blockStatement
import org.jetbrains.kotlin.nj2k.hasWritableUsages
import org.jetbrains.kotlin.nj2k.tree.JKBlockStatement
import org.jetbrains.kotlin.nj2k.tree.JKDeclarationStatement
import org.jetbrains.kotlin.nj2k.tree.JKFieldAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKForInStatement
import org.jetbrains.kotlin.nj2k.tree.JKLambdaExpression
import org.jetbrains.kotlin.nj2k.tree.JKLocalVariable
import org.jetbrains.kotlin.nj2k.tree.JKMethod
import org.jetbrains.kotlin.nj2k.tree.JKMutabilityModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKNameIdentifier
import org.jetbrains.kotlin.nj2k.tree.JKParameter
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.JKTypeElement
import org.jetbrains.kotlin.nj2k.tree.Mutability
import org.jetbrains.kotlin.nj2k.tree.copyTreeAndDetach
import org.jetbrains.kotlin.nj2k.types.determineType

/**
 * If a parameter is reassigned, we need to introduce a local mutable variable with the same name,
 * because in Kotlin parameters are not mutable.
 */
class ParameterModificationConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKMethod -> convertMethod(element)
            is JKLambdaExpression -> convertLambda(element)
            is JKForInStatement -> convertForInStatement(element)
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

    private fun convertForInStatement(element: JKForInStatement) {
        val newVariables = createVariables(listOf(element.parameter), element.body).ifEmpty { return }
        val declaration = JKDeclarationStatement(newVariables)
        when (val body = element.body) {
            is JKBlockStatement -> {
                body.block.statements = listOf(declaration) + body.block.statements
            }

            else -> {
                element.body = blockStatement(declaration, body.copyTreeAndDetach())
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
