// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.config.ApiVersion.Companion.KOTLIN_1_9
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.*

class JavaModifiersConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKModalityOwner && element is JKAnnotationListOwner) {
            val overrideAnnotation = element.annotationList.annotationByFqName("java.lang.Override")
            if (overrideAnnotation != null) {
                element.annotationList.annotations -= overrideAnnotation
            }
        }

        if (element is JKOtherModifiersOwner && element is JKAnnotationListOwner) {
            element.elementByModifier(VOLATILE)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
                val annotationFqName = if (moduleApiVersion >= KOTLIN_1_9) "kotlin.concurrent.Volatile" else "kotlin.jvm.Volatile"
                element.annotationList.annotations +=
                    JKAnnotation(symbolProvider.provideClassSymbol(annotationFqName)).withFormattingFrom(modifierElement)
            }

            element.elementByModifier(TRANSIENT)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
                element.annotationList.annotations +=
                    jvmAnnotation("Transient", symbolProvider).withFormattingFrom(modifierElement)
            }

            element.elementByModifier(STRICTFP)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
                element.annotationList.annotations +=
                    jvmAnnotation("Strictfp", symbolProvider).withFormattingFrom(modifierElement)
            }

            element.elementByModifier(SYNCHRONIZED)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
                element.annotationList.annotations +=
                    jvmAnnotation("Synchronized", symbolProvider).withFormattingFrom(modifierElement)
            }

            element.elementByModifier(NATIVE)?.let { modifierElement ->
                modifierElement.otherModifier = EXTERNAL
            }
        }

        return recurse(element)
    }
}