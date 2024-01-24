// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.tree.*


internal class RemoveWrongExtraModifiersForSingleFunctionsConversion(context: NewJ2kConverterContext) :
    RecursiveApplicableConversionBase(context) {
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
