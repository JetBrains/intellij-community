// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
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

class ImplicitInitializerConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKField) return recurse(element)
        if (element.initializer !is JKStubExpression) return recurse(element)

        val state = element.initializationState()
        if (state == INITIALIZED_IN_ALL_CONSTRUCTORS) return recurse(element)
        if (state == INITIALIZED_IN_SOME_CONSTRUCTORS && element.modality == FINAL) return recurse(element)

        generateNewInitializerFor(element)
        return recurse(element)
    }

    private fun JKField.initializationState(): InitializationState {
        val fieldSymbol = symbolProvider.provideUniverseSymbol(this)
        val containingClass = parentOfType<JKClass>() ?: return NON_INITIALIZED
        val symbolToConstructor = containingClass.declarationList
            .filterIsInstance<JKConstructor>()
            .map { symbolProvider.provideUniverseSymbol(it) to it }
            .toMap()

        fun JKMethodSymbol.parentConstructor(): JKMethodSymbol? =
            (symbolToConstructor[this]?.delegationCall as? JKDelegationConstructorCall)
                ?.identifier

        val constructors = containingClass.declarationList
            .filterIsInstance<JKConstructor>()
            .map { symbolProvider.provideUniverseSymbol(it) to false }
            .toMap()
            .toMutableMap()

        val declarationsWithInitializers = findUsages(containingClass, context).mapNotNull { usage ->
            val parent = usage.parent
            val assignmentStatement = when {
                parent is JKKtAssignmentStatement -> parent
                parent is JKQualifiedExpression && parent.receiver is JKThisExpression ->
                    parent.parent as? JKKtAssignmentStatement

                else -> null
            } ?: return@mapNotNull null

            val isInitializer = when (parent) {
                is JKKtAssignmentStatement -> (parent.field as? JKFieldAccessExpression)?.identifier == fieldSymbol
                is JKQualifiedExpression -> (parent.selector as? JKFieldAccessExpression)?.identifier == fieldSymbol
                else -> false
            }

            val containingDeclaration = (assignmentStatement.parent as? JKBlock)?.parent as? JKDeclaration ?: return@mapNotNull null
            if (isInitializer) containingDeclaration else null
        }

        val initBlocks = containingClass.declarationList.filterIsInstance<JKInitDeclaration>()
        if (initBlocks.isNotEmpty() && initBlocks.all { it in declarationsWithInitializers }) {
            // If the field is initialized in all init declarations it definitely doesn't need an explicit stub initializer
            return INITIALIZED_IN_ALL_CONSTRUCTORS
        }

        val constructorsWithInitializers = declarationsWithInitializers.filterIsInstance<JKConstructor>()
        for (constructor in constructorsWithInitializers) {
            constructors[symbolProvider.provideUniverseSymbol(constructor)] = true
        }

        val constructorsToInitialize = mutableListOf<JKMethodSymbol>()

        for ((constructor, initialized) in constructors) {
            if (initialized) continue
            val parentConstructors = generateSequence(constructor) { it.parentConstructor() }
            if (parentConstructors.any { constructors[it] == true }) {
                constructorsToInitialize += parentConstructors
            }
        }

        for (constructor in constructorsToInitialize) {
            constructors[constructor] = true
        }

        return when (constructors.values.count { it }) {
            0 -> NON_INITIALIZED
            constructors.size -> INITIALIZED_IN_ALL_CONSTRUCTORS
            else -> INITIALIZED_IN_SOME_CONSTRUCTORS
        }
    }

    private fun generateNewInitializerFor(field: JKField) {
        val initializer = when (val fieldType = field.type.type) {
            is JKClassType, is JKTypeParameterType -> JKLiteralExpression("null", LiteralType.NULL)
            is JKJavaPrimitiveType -> createPrimitiveTypeInitializer(fieldType)
            else -> null
        }
        if (initializer != null) {
            initializer.commentsAfter += field.name.commentsAfter
            field.name.commentsAfter.clear()
            field.initializer = initializer
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