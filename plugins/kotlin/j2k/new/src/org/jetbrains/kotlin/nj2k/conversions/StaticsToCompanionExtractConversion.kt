// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.declarationList
import org.jetbrains.kotlin.nj2k.getOrCreateCompanionObject
import org.jetbrains.kotlin.nj2k.tree.*


class StaticsToCompanionExtractConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKClass) return recurse(element)
        if (element.classKind == JKClass.ClassKind.COMPANION || element.classKind == JKClass.ClassKind.OBJECT) return element
        val statics = element.declarationList.filter { declaration ->
            declaration is JKOtherModifiersOwner && declaration.hasOtherModifier(OtherModifier.STATIC)
                    || declaration is JKJavaStaticInitDeclaration
        }
        if (statics.isEmpty()) return recurse(element)
        val companion = element.getOrCreateCompanionObject()

        element.classBody.declarations -= statics
        companion.classBody.declarations += statics.map { declaration ->
            when (declaration) {
                is JKJavaStaticInitDeclaration -> declaration.toKtInitDeclaration()
                else -> declaration
            }
        }.onEach { declaration ->
            if (declaration is JKOtherModifiersOwner) {
                declaration.otherModifierElements -= declaration.elementByModifier(OtherModifier.STATIC)!!
            }
            context.externalCodeProcessor.getMember(declaration)?.let {
                it.isStatic = true
            }
        }
        return recurse(element)
    }

    private fun JKJavaStaticInitDeclaration.toKtInitDeclaration() =
        JKKtInitDeclaration(::block.detached()).withFormattingFrom(this)
}
