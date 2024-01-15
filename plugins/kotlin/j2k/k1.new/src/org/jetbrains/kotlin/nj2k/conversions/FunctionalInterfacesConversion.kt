// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.annotationByFqName
import org.jetbrains.kotlin.nj2k.tree.*

private const val FUNCTIONAL_INTERFACE = "java.lang.FunctionalInterface"

internal class FunctionalInterfacesConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (!context.functionalInterfaceConversionEnabled) return recurse(element)
        if (element !is JKClass) return recurse(element)
        if (element.classKind != JKClass.ClassKind.INTERFACE) return recurse(element)
        if (element.inheritance.extends.isNotEmpty()) return recurse(element)

        val functionalInterfaceMarker = element.annotationList.annotationByFqName(FUNCTIONAL_INTERFACE) ?: return recurse(element)

        val samMethod = element.classBody.declarations.filterIsInstance<JKMethod>().singleOrNull { it.block is JKBodyStub }
        if (samMethod == null || samMethod.typeParameterList.typeParameters.isNotEmpty()) return recurse(element)

        element.otherModifierElements += JKOtherModifierElement(OtherModifier.FUN)
        element.annotationList.annotations -= functionalInterfaceMarker

        return recurse(element)
    }
}
