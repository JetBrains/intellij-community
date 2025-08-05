// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseFieldSymbol
import org.jetbrains.kotlin.nj2k.tree.*

class MoveConstructorsAfterFieldsConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKClassBody) return recurse(element)
        if (element.declarations.none { it is JKInitDeclaration }) return recurse(element)

        moveInitBlocks(element, isStatic = false)
        moveInitBlocks(element, isStatic = true)

        return recurse(element)
    }

    private fun moveInitBlocks(element: JKClassBody, isStatic: Boolean) {
        val data = computeDeclarationsData(element, isStatic)
        element.declarations = collectNewDeclarations(element, data, isStatic)
    }

    private fun computeDeclarationsData(
        element: JKClassBody,
        isStatic: Boolean,
    ): DeclarationsData {
        val moveAfter = mutableMapOf<JKDeclaration, JKDeclaration>()
        val forwardlyReferencedFields = mutableSetOf<JKField>()
        val declarationsToAdd = mutableListOf<JKDeclaration>()
        val order = element.declarations.withIndex().associateTo(mutableMapOf()) { (index, value) -> value to index }
        for (declaration in element.declarations) {
            when {
                declaration is JKInitDeclaration && declaration.isStatic == isStatic -> {
                    declarationsToAdd += declaration
                    val fields = findAllUsagesOfFieldsIn(declaration, element) { it.hasOtherModifier(OtherModifier.STATIC) == isStatic }

                    moveAfter[declaration] = declaration
                    val lastDependentField = fields.maxByOrNull(order::getValue) ?: continue
                    val initDeclarationIndex = order.getValue(declaration)
                    if (order.getValue(lastDependentField) < initDeclarationIndex) continue
                    moveAfter[declaration] = lastDependentField

                    for (field in fields) {
                        if (order.getValue(field) > initDeclarationIndex) {
                            forwardlyReferencedFields += field
                        }
                    }
                }

                declaration is JKField && declaration.hasOtherModifier(OtherModifier.STATIC) == isStatic -> {
                    if (declaration.initializer !is JKStubExpression && declaration in forwardlyReferencedFields) {
                        val assignment = createFieldAssignmentInitDeclaration(declaration, isStatic)
                        moveAfter[assignment] = declarationsToAdd.last()
                        order[assignment] = order.getValue(declarationsToAdd.last())
                        declarationsToAdd += assignment
                    }
                }
            }
        }
        return DeclarationsData(order, moveAfter, declarationsToAdd)
    }

    private fun findAllUsagesOfFieldsIn(scope: JKTreeElement, owningClass: JKClassBody, filter: (JKField) -> Boolean): Collection<JKField> {
        val result = mutableSetOf<JKField>()
        scope.forEachDescendantOfType<JKExpression> { expression ->
            val symbol = expression.unboxFieldReference()?.identifier as? JKUniverseFieldSymbol ?: return@forEachDescendantOfType
            val field = symbol.target as? JKField ?: return@forEachDescendantOfType
            if (!filter(field)) return@forEachDescendantOfType
            if (field.parent != owningClass) return@forEachDescendantOfType
            result.add(field)
        }
        return result
    }

    private fun collectNewDeclarations(
        element: JKClassBody,
        data: DeclarationsData,
        isStatic: Boolean,
    ): List<JKDeclaration> {
        var index = 0

        val newDeclarations = buildList {
            for (declaration in element.declarations) {
                if (declaration !is JKInitDeclaration || declaration.isStatic != isStatic) {
                    add(declaration)
                }

                val declarationIndex = data.order.getValue(declaration)
                while (index <= data.declarationsToAdd.lastIndex) {
                    val initDeclaration = data.declarationsToAdd[index]
                    val moveAfterIndex = data.order.getValue(data.moveAfter.getValue(initDeclaration))
                    if (declarationIndex >= moveAfterIndex) {
                        add(initDeclaration)
                        index++
                    } else {
                        break
                    }
                }
            }
        }

        return newDeclarations
    }

    private data class DeclarationsData(
        val order: Map<JKDeclaration, Int>,
        val moveAfter: Map<JKDeclaration, JKDeclaration>,
        val declarationsToAdd: List<JKDeclaration>,
    )

    private fun createFieldAssignmentInitDeclaration(field: JKField, isStatic: Boolean): JKInitDeclaration {
        val initializer = field.initializer
        field.initializer = JKStubExpression()
        val block = JKBlockImpl(assignmentStatement(field, initializer, symbolProvider))
        return if (isStatic) JKJavaStaticInitDeclaration(block) else JKKtInitDeclaration(block)
    }
}