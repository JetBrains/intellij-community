// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability.Nullable
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.conversions.InitializationState.*
import org.jetbrains.kotlin.nj2k.declarationList
import org.jetbrains.kotlin.nj2k.findUsages
import org.jetbrains.kotlin.nj2k.symbols.JKMethodSymbol
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression.LiteralType
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.types.JKClassType
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType
import org.jetbrains.kotlin.nj2k.types.JKTypeParameterType
import org.jetbrains.kotlin.nj2k.types.updateNullability

/**
 * Handles Java implicit initialization of fields.
 *
 * Identifies fields that are uninitialized in constructors or initializer blocks
 * and generates explicit initializers with default values (e.g., `null`, `0`, or `false`).
 */
class ImplicitInitializerConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKField) return recurse(element)
        if (element.initializer !is JKStubExpression) return recurse(element)

        val state = element.computeInitializationState()
        if (state == INITIALIZED_IN_ALL_CONSTRUCTORS) return recurse(element)
        if (state == INITIALIZED_IN_SOME_CONSTRUCTORS && element.modality == FINAL) return recurse(element)

        generateNewInitializerFor(element)
        return recurse(element)
    }

    context(_: KaSession)
    private fun JKField.computeInitializationState(): InitializationState {
        val containingClass = parentOfType<JKClass>() ?: return NON_INITIALIZED
        val constructors = containingClass.declarationList.filterIsInstance<JKConstructor>()
        val initBlocks = containingClass.declarationList.filterIsInstance<JKInitDeclaration>()
        val declarationsWithInitializers = containingClass.findDeclarationsWithInitializersFor(field = this)

        if (initBlocks.isNotEmpty() && initBlocks.all { it in declarationsWithInitializers }) {
            // If the field is initialized in all init blocks, then it definitely doesn't need an explicit stub initializer
            return INITIALIZED_IN_ALL_CONSTRUCTORS
        }

        val symbolToConstructor: Map<JKMethodSymbol, JKConstructor> =
            constructors.associateBy { symbolProvider.provideUniverseSymbol(it) }
        val symbolToInitialized: MutableMap<JKMethodSymbol, Boolean> =
            symbolToConstructor.entries.associate { it.key to false }.toMutableMap()

        val constructorsWithInitializers = declarationsWithInitializers.filterIsInstance<JKConstructor>()
        for (constructor in constructorsWithInitializers) {
            symbolToInitialized[symbolProvider.provideUniverseSymbol(constructor)] = true
        }

        val constructorsToInitialize = mutableListOf<JKMethodSymbol>()

        fun JKMethodSymbol.parentConstructor(): JKMethodSymbol? =
            (symbolToConstructor[this]?.delegationCall as? JKDelegationConstructorCall)?.identifier

        for ((constructor, initialized) in symbolToInitialized) {
            if (initialized) continue
            val parentConstructors = generateSequence(constructor) { it.parentConstructor() }
            if (parentConstructors.any { symbolToInitialized[it] == true }) {
                constructorsToInitialize += constructor
            }
        }

        for (constructor in constructorsToInitialize) {
            symbolToInitialized[constructor] = true
        }

        return when (symbolToInitialized.values.count { it }) {
            0 -> NON_INITIALIZED
            symbolToInitialized.size -> INITIALIZED_IN_ALL_CONSTRUCTORS
            else -> INITIALIZED_IN_SOME_CONSTRUCTORS
        }
    }

    context(_: KaSession)
    private fun JKClass.findDeclarationsWithInitializersFor(field: JKField): Set<JKDeclaration> {
        val fieldSymbol = symbolProvider.provideUniverseSymbol(field)
        val usages = field.findUsages(scope = this, context)
        val declarationsWithInitializers = mutableSetOf<JKDeclaration>()

        for (usage in usages) {
            val parent = usage.parent
            val assignmentStatement = when {
                parent is JKKtAssignmentStatement -> parent
                parent is JKQualifiedExpression && parent.receiver is JKThisExpression ->
                    parent.parent as? JKKtAssignmentStatement

                else -> null
            } ?: continue

            // If the field is final (i.e., a val) as determined by Java control-flow analysis on the JK tree building stage,
            // then it should be considered initialized from arbitrarily nested assignments within the constructor.
            // However, if the field is mutable, we only account for top-level assignment statements
            // because in this case it is not guaranteed that the field is definitely assigned from nested blocks.
            val containingDeclaration = if (field.modality == FINAL) {
                assignmentStatement.parentOfType<JKDeclaration>()
            } else {
                (assignmentStatement.parent as? JKBlock)?.parent as? JKDeclaration
            } ?: continue

            val isInitializer = when (parent) {
                is JKKtAssignmentStatement -> (parent.field as? JKFieldAccessExpression)?.identifier == fieldSymbol
                is JKQualifiedExpression -> (parent.selector as? JKFieldAccessExpression)?.identifier == fieldSymbol
                else -> false
            }

            if (isInitializer) {
                declarationsWithInitializers.add(containingDeclaration)
            }
        }

        return declarationsWithInitializers
    }

    private fun generateNewInitializerFor(field: JKField) {
        val fieldType = field.type.type
        val initializer = when (fieldType) {
            is JKClassType, is JKTypeParameterType -> JKLiteralExpression("null", LiteralType.NULL)
            is JKJavaPrimitiveType -> createPrimitiveTypeInitializer(fieldType)
            else -> return
        }

        initializer.commentsAfter += field.name.commentsAfter
        field.name.commentsAfter.clear()
        field.initializer = initializer

        if (initializer.type == LiteralType.NULL && fieldType.nullability != Nullable) {
            field.type.type = fieldType.updateNullability(Nullable)
        }
    }

    private fun createPrimitiveTypeInitializer(primitiveType: JKJavaPrimitiveType): JKLiteralExpression =
        if (primitiveType == JKJavaPrimitiveType.BOOLEAN) {
            JKLiteralExpression("false", LiteralType.BOOLEAN)
        } else {
            JKLiteralExpression("0", LiteralType.INT)
        }
}

private enum class InitializationState {
    INITIALIZED_IN_ALL_CONSTRUCTORS,
    INITIALIZED_IN_SOME_CONSTRUCTORS,
    NON_INITIALIZED
}