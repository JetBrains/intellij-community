// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.declarationList
import org.jetbrains.kotlin.nj2k.tree.JKAnnotationList
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.tree.JKConstructor
import org.jetbrains.kotlin.nj2k.tree.JKKtPrimaryConstructor
import org.jetbrains.kotlin.nj2k.tree.JKModalityModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKNameIdentifier
import org.jetbrains.kotlin.nj2k.tree.JKStubExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.JKVisibilityModifierElement
import org.jetbrains.kotlin.nj2k.tree.Modality
import org.jetbrains.kotlin.nj2k.tree.Visibility

class InsertDefaultPrimaryConstructorConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKClass) return recurse(element)
        if (element.classKind != JKClass.ClassKind.CLASS) return recurse(element)
        if (element.declarationList.any { it is JKConstructor }) return recurse(element)

        val constructor = JKKtPrimaryConstructor(
            JKNameIdentifier(element.name.value),
            emptyList(),
            JKStubExpression(),
            JKAnnotationList(),
            emptyList(),
            JKVisibilityModifierElement(Visibility.PUBLIC),
            JKModalityModifierElement(Modality.FINAL)
        )

        element.classBody.declarations += constructor
        return recurse(element)
    }
}