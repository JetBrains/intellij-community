// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.*

internal class RemoveWrongExtraModifiersForSingleFunctionsConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKOtherModifiersOwner) return recurse(element)
        if (element.parentOfType<JKClass>() == null) {
            element.elementByModifier(OtherModifier.STATIC)?.also { modifierElement ->
                element.otherModifierElements -= modifierElement
            }
        }
        return recurse(element)
    }
}
