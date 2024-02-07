// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.*

internal class RemoveWrongExtraModifiersConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKOtherModifiersOwner) return recurse(element)
        val objectClassParentExists = element.parents().none { parent -> parent is JKClass && parent.classKind == JKClass.ClassKind.OBJECT }
        val modifierToRemove = when {
            element.parentOfType<JKClass>() == null && element is JKMethod -> OtherModifier.STATIC  // single function
            element is JKClass && element.classKind == JKClass.ClassKind.OBJECT && objectClassParentExists -> OtherModifier.INNER  // Object not nested in another object
            else -> null
        }
        if (modifierToRemove != null) {
            element.elementByModifier(modifierToRemove)?.also { modifierElement ->
                element.otherModifierElements -= modifierElement
            }
        }
        return recurse(element)
    }
}
