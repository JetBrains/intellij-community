// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.isLocalClass
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*
import org.jetbrains.kotlin.nj2k.tree.JKOtherModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.INNER
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.STATIC
import org.jetbrains.kotlin.nj2k.tree.elementByModifier

internal class InnerClassConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement =
        if (element is JKClass) recurseArmed(element, outerClass = element) else recurse(element)

    private fun recurseArmed(element: JKTreeElement, outerClass: JKClass): JKTreeElement =
        applyRecursive(element, outerClass) { elem, outer -> elem.applyArmed(outer) }

    private fun JKTreeElement.applyArmed(outerClass: JKClass): JKTreeElement {
        if (this !is JKClass || classKind == COMPANION || isLocalClass()) return recurseArmed(this, outerClass)
        val static = elementByModifier(STATIC)
        when {
            static != null ->
                otherModifierElements -= static

            outerClass.classKind != INTERFACE && classKind != INTERFACE && classKind != ENUM && classKind != RECORD ->
                otherModifierElements += JKOtherModifierElement(INNER)
        }
        return recurseArmed(this, outerClass = this)
    }
}
