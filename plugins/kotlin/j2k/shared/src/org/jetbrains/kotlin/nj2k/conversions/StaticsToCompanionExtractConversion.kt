// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.declarationList
import org.jetbrains.kotlin.nj2k.getOrCreateCompanionObject
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.COMPANION
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.OBJECT
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL

class StaticsToCompanionExtractConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKClass) return recurse(element)
        if (element.classKind == COMPANION || element.classKind == OBJECT) return element

        val staticDeclarations = element.declarationList.filter { declaration ->
            declaration is JKOtherModifiersOwner && declaration.hasOtherModifier(OtherModifier.STATIC)
                    || declaration is JKJavaStaticInitDeclaration
        }
        if (staticDeclarations.isEmpty()) return recurse(element)

        val companion = element.getOrCreateCompanionObject()
        element.classBody.declarations -= staticDeclarations

        val declarations = staticDeclarations.map { declaration ->
            when (declaration) {
                is JKJavaStaticInitDeclaration -> declaration.toKtInitDeclaration()
                else -> declaration
            }
        }

        for (declaration in declarations) {
            if (declaration is JKOtherModifiersOwner) {
                declaration.otherModifierElements -= declaration.elementByModifier(OtherModifier.STATIC)!!
            }
            if (declaration is JKModalityOwner) {
                declaration.modality = FINAL
            }
            context.externalCodeProcessor.getMember(declaration)?.let {
                it.isStatic = true
            }
        }

        companion.classBody.declarations += declarations
        return recurse(element)
    }

    private fun JKJavaStaticInitDeclaration.toKtInitDeclaration() =
        JKKtInitDeclaration(::block.detached()).withFormattingFrom(this)
}
