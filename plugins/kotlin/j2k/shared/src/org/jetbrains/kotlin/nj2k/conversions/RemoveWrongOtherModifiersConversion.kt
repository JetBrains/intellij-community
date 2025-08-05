// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.OBJECT
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.INNER
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.STATIC

class RemoveWrongOtherModifiersConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKOtherModifiersOwner) return recurse(element)

        val modifierToRemove = when {
            element is JKMethod -> STATIC
            element is JKClass && element.classKind == OBJECT -> INNER
            else -> null
        }

        if (modifierToRemove != null) {
            element.elementByModifier(modifierToRemove)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
            }
        }
        return recurse(element)
    }
}
