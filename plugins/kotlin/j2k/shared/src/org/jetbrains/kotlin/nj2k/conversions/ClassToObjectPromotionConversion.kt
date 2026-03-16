// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.declarationList
import org.jetbrains.kotlin.nj2k.getCompanion
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.CLASS
import org.jetbrains.kotlin.nj2k.tree.JKClassBody
import org.jetbrains.kotlin.nj2k.tree.JKDeclarationStatement
import org.jetbrains.kotlin.nj2k.tree.JKKtInitDeclaration
import org.jetbrains.kotlin.nj2k.tree.JKKtPrimaryConstructor
import org.jetbrains.kotlin.nj2k.tree.JKModalityModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.JKVisibilityOwner
import org.jetbrains.kotlin.nj2k.tree.Modality
import org.jetbrains.kotlin.nj2k.tree.Visibility
import org.jetbrains.kotlin.nj2k.tree.detached
import org.jetbrains.kotlin.nj2k.tree.visibility
import org.jetbrains.kotlin.nj2k.tree.withFormattingFrom

class ClassToObjectPromotionConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKClass || element.classKind != CLASS) return recurse(element)
        val companion = element.getCompanion() ?: return recurse(element)

        val allDeclarationsMatch = element.declarationList.all {
            when (it) {
                is JKKtPrimaryConstructor -> it.parameters.isEmpty() && it.block.statements.isEmpty()
                is JKKtInitDeclaration ->
                    it.block.statements.all { statement ->
                        when (statement) {
                            is JKDeclarationStatement -> statement.declaredStatements.isEmpty()
                            else -> false
                        }
                    }

                is JKClass -> true
                else -> false
            }
        }

        if (allDeclarationsMatch && !element.hasInheritors()) {
            companion.invalidate()
            element.invalidate()
            return recurse(
                JKClass(
                    element.name,
                    element.inheritance,
                    JKClass.ClassKind.OBJECT,
                    element.typeParameterList,
                    companion.classBody.also { body ->
                        body.handleDeclarationsModifiers()
                        body.declarations += element.classBody.declarations.filter {
                            //TODO preserve order
                            it is JKClass && it.classKind != JKClass.ClassKind.COMPANION
                        }.map { it.detached(element.classBody) }
                    },
                    element.annotationList,
                    element.otherModifierElements,
                    element.visibilityElement,
                    JKModalityModifierElement(Modality.FINAL)
                ).withFormattingFrom(element)
            )
        }

        return recurse(element)
    }

    private fun JKClassBody.handleDeclarationsModifiers() {
        for (declaration in declarations) {
            if (declaration !is JKVisibilityOwner) continue
            if (declaration.visibility == Visibility.PROTECTED) {
                //in old j2k it is internal. should it be private instead?
                declaration.visibility = Visibility.INTERNAL
            }
        }
    }

    private fun JKClass.hasInheritors(): Boolean {
        val psi = psi<PsiClass>() ?: return false
        return context.converter.referenceSearcher.hasInheritors(psi)
    }
}