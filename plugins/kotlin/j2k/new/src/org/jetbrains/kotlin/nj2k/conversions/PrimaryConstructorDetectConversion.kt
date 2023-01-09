// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.declarationList
import org.jetbrains.kotlin.nj2k.mutate
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*

class PrimaryConstructorDetectConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKClass && (element.classKind == CLASS || element.classKind == ENUM || element.classKind == RECORD)) {
            processClass(element)
        }
        return recurse(element)
    }

    private fun processClass(element: JKClass) {
        val constructors = element.declarationList.filterIsInstance<JKConstructor>()
        if (constructors.any { it is JKKtPrimaryConstructor }) return
        val primaryConstructorCandidate = detectPrimaryConstructor(constructors) ?: return
        val delegationCall = primaryConstructorCandidate.delegationCall as? JKDelegationConstructorCall
        if (delegationCall?.expression is JKThisExpression) return

        primaryConstructorCandidate.invalidate()
        if (primaryConstructorCandidate.block.statements.isNotEmpty()) {
            val initDeclaration = JKKtInitDeclaration(primaryConstructorCandidate.block)
                .withFormattingFrom(primaryConstructorCandidate)
            primaryConstructorCandidate.clearFormatting()
            primaryConstructorCandidate.forEachModifier { modifierElement ->
                modifierElement.clearFormatting()
            }
            val lastInitBlockOrFieldIndex = element.classBody.declarations.indexOfLast { it is JKInitDeclaration || it is JKField }
            element.classBody.declarations =
                element.classBody.declarations.mutate {
                    val insertAfter = maxOf(lastInitBlockOrFieldIndex, indexOf(primaryConstructorCandidate))
                    add(insertAfter + 1, initDeclaration)
                    remove(primaryConstructorCandidate)
                }
        } else {
            element.classBody.declarations -= primaryConstructorCandidate
        }

        val primaryConstructor =
            JKKtPrimaryConstructor(
                primaryConstructorCandidate.name,
                primaryConstructorCandidate.parameters,
                primaryConstructorCandidate.delegationCall,
                primaryConstructorCandidate.annotationList,
                primaryConstructorCandidate.otherModifierElements,
                primaryConstructorCandidate.visibilityElement,
                primaryConstructorCandidate.modalityElement
            ).withFormattingFrom(primaryConstructorCandidate)

        symbolProvider.transferSymbol(primaryConstructor, primaryConstructorCandidate)

        element.classBody.declarations += primaryConstructor
    }

    private fun detectPrimaryConstructor(constructors: List<JKConstructor>): JKConstructor? {
        val constructorsWithoutOtherConstructorCall =
            constructors.filterNot { (it.delegationCall as? JKDelegationConstructorCall)?.expression is JKThisExpression }
        return constructorsWithoutOtherConstructorCall.singleOrNull()
    }
}